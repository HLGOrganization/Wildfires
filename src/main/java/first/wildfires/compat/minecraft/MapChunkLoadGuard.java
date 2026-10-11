package first.wildfires.compat.minecraft;

import com.mojang.logging.LogUtils;
import first.wildfires.compat.minecraft.MapChunkLoadRules.Chunk;
import first.wildfires.compat.minecraft.MapChunkLoadRules.ChunkProbe;
import first.wildfires.compat.minecraft.MapChunkLoadRules.ReportBudget;
import first.wildfires.compat.minecraft.MapChunkLoadRules.Update;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/**
 * <h2>What this guards</h2>
 * {@code MapItem.update} walks the chunks of the area a held map draws and reads every one of them.
 * Called from {@code inventoryTick} on the server, that read is {@code Level#getChunk} and a chunk
 * that is only scheduled - which is the normal state at a low simulation distance, or right after a
 * teleport - turns it into a wait on the server thread. The wait ends when the chunk arrives, but the
 * tick it belongs to has not finished, and one that runs past {@code max-tick-time} is reported as a
 * hang and the server is stopped. This class answers one question for the mixin at the head of
 * {@code update}: are the chunks this tick would read all here? If not, the update is deferred to a
 * later tick, which costs the map one sixteenth of a refresh and costs the server nothing.
 *
 * <h2>Why deferring is the right repair</h2>
 * A map update is already incremental: each pass redraws one sixteenth of the sheet's columns, and the
 * phase rolls around by itself, so a pass that is skipped is redrawn sixteen ticks later. Nothing is
 * lost by waiting, and the alternative - letting the read through - is the hang. The deferral is not
 * "wait until every chunk of the visible area is loaded": only the chunks of the window this tick
 * would actually touch are required, which is the difference between an atlas that keeps working near
 * a loading border and one that stops refreshing.
 *
 * <h2>Why the phase has to be advanced here</h2>
 * The phase lives in the map's {@code HoldingPlayer} and vanilla increments it at the top of the pass.
 * A pass cancelled from here never reaches that, so this method increments it itself. Without that,
 * the guard would ask about the same pixel column on every tick and a map held over one unloaded chunk
 * would never refresh again - a freeze that looks much worse than the rare wait it prevents.
 *
 * <h2>What counts as "here"</h2>
 * The question asked here is not "is this chunk loaded" but "would reading it wait". {@code getChunkNow}
 * answers the first question from a cache without starting a load, and a {@code null} from it means
 * the read would wait - but in part because vanilla's own read is what starts the load. Map Atlases
 * wraps that read and turns every chunk it has no ticket for into an empty chunk, so with that mod
 * installed such a chunk is read without waiting and does not have to hold the update back. A chunk
 * that <em>has</em> a ticket but has not arrived yet waits in vanilla and in Map Atlases alike, and
 * that is the case the reported hang came from: it is always deferred.
 *
 * <h2>When it stays out of the way</h2>
 * Only a server-side update on the server thread is inspected, because only there does the read wait
 * and only there can a chunk be asked about without waiting for it ({@code getChunkNow} answers
 * {@code null} from another thread, which would look like "not loaded" for every chunk). Updates in
 * which vanilla would do nothing anyway - a map of another dimension, or an entity that is not a
 * player - are left alone, and anything that throws falls back to vanilla behaviour. A guard that
 * takes the server down over its own bookkeeping would be worse than the bug it fixes.
 */
public final class MapChunkLoadGuard {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "[Wildfires map] ";

    /** Names each map once, so a map that stays unloaded cannot write a line per tick. */
    private static final ReportBudget DEFERRALS = new ReportBudget();

    /** Set the first time a map is updated; {@code null} means the question has not been asked yet. */
    private static volatile Boolean STUBS_UNTICKETED_CHUNKS;

    /** Names a failure of the guard itself a few times, then stops. */
    private static final ReportBudget FAILURES = new ReportBudget(3);

    private MapChunkLoadGuard() {
    }

    /**
     * Whether the map update that is about to run has to be postponed.
     *
     * <p>Called from the head of {@code MapItem.update}. A {@code true} answer means the caller must
     * cancel the pass; the update will be offered again on the next tick, on the next phase.
     *
     * @param level  the level the map is updated in
     * @param entity the holder
     * @param data   the map's saved data
     */
    public static boolean defers(Level level, Entity entity, MapItemSavedData data) {
        try {
            if (!MapChunkLoadConfig.guardEnabled()) {
                return false;
            }
            if (!(level instanceof ServerLevel serverLevel)) {
                return false;
            }
            if (level.dimension() != data.dimension) {
                return false;
            }
            if (!(entity instanceof Player player)) {
                return false;
            }
            if (!serverLevel.getServer().isSameThread()) {
                return false;
            }
            MapItemSavedData.HoldingPlayer holding = data.getHoldingPlayer(player);
            Update update = MapChunkLoadRules.update(player.getX(), player.getZ(), data.centerX, data.centerZ,
                    data.scale, level.dimensionType().hasCeiling(), holding.step);
            Chunk missing = MapChunkLoadRules.firstMissingChunk(update, probe(serverLevel));
            if (missing == null) {
                return false;
            }
            holding.step = MapChunkLoadRules.nextPhase(holding.step);
            report(data, missing, update);
            return true;
        } catch (Throwable failure) {
            reportFailure(failure);
            return false;
        }
    }

    /**
     * Asks the chunk source whether a chunk can be read from this thread without waiting.
     *
     * <p>{@code getChunkNow} is a cache lookup that never starts a load, so {@code null} from it already
     * means "vanilla would wait here". {@code hasChunk} is the weaker "this chunk has a ticket", and Map
     * Atlases' own guard reads it before every chunk: without a ticket its read is replaced by an empty
     * chunk, so the wait cannot happen and the update does not have to be deferred. The second answer is
     * only consulted when the first one says the chunk is not here, so the common case stays one lookup.
     *
     * <p>A chunk that a load has already picked up ({@code ChunkHolder.currentlyLoading}) is reported as
     * here by {@code getChunkNow} even though its status is not yet full. Reading it can still wait, but
     * only for a load that is already running, which is bounded by that load and not by chunk generation.
     */
    private static ChunkProbe probe(ServerLevel level) {
        ServerChunkCache chunks = level.getChunkSource();
        boolean stubbed = stubsUnticketedChunks();
        return (chunkX, chunkZ) -> chunks.getChunkNow(chunkX, chunkZ) != null
                || (stubbed && !chunks.hasChunk(chunkX, chunkZ));
    }

    /**
     * Whether chunks without a ticket are handed out empty instead of being waited for, which is what
     * Map Atlases does inside {@code MapItem.update}. Asked once and remembered; a mod list that does not
     * exist yet is treated as "not installed" for that call rather than remembered as such.
     */
    private static boolean stubsUnticketedChunks() {
        Boolean remembered = STUBS_UNTICKETED_CHUNKS;
        if (remembered != null) {
            return remembered;
        }
        ModList mods = ModList.get();
        if (mods == null) {
            return false;
        }
        boolean stubbed = mods.isLoaded("map_atlases");
        if (!stubbed) {
            LOGGER.info("{}Map Atlases is not installed, so every chunk of a map's window has to be loaded",
                    PREFIX);
        }
        STUBS_UNTICKETED_CHUNKS = stubbed;
        return stubbed;
    }

    private static void report(MapItemSavedData data, Chunk missing, Update update) {
        if (!MapChunkLoadConfig.guardLogEnabled()) {
            return;
        }
        String key = data.dimension.location() + "@" + data.centerX + "," + data.centerZ;
        if (!DEFERRALS.offer(key)) {
            if (DEFERRALS.limitJustReached()) {
                LOGGER.info("{}further maps are not named; {} maps deferred so far", PREFIX,
                        DEFERRALS.reported() + DEFERRALS.suppressed());
            }
            return;
        }
        LOGGER.info("{}map at {},{} in {} deferred its update: chunk {},{} is not loaded (phase {})", PREFIX,
                data.centerX, data.centerZ, data.dimension.location(), missing.x(), missing.z(), update.phase());
    }

    private static void reportFailure(Throwable failure) {
        String key = failure.getClass().getName();
        if (!FAILURES.offer(key)) {
            return;
        }
        LOGGER.warn("{}map chunk load guard failed and let the update through: {}", PREFIX,
                failure.toString(), failure);
    }
}
