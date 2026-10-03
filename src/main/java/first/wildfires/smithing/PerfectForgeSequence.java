package first.wildfires.smithing;

import first.wildfires.Wildfires;
import first.wildfires.mixin.tfc.InventoryBlockEntityAccessor;
import net.dries007.tfc.common.blockentities.AnvilBlockEntity;
import net.dries007.tfc.common.capabilities.forge.ForgeStep;
import net.dries007.tfc.common.capabilities.forge.Forging;
import net.dries007.tfc.common.capabilities.forge.ForgingCapability;
import net.dries007.tfc.common.recipes.AnvilRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The hammering behind the anvil's one-click forge: both things the button can set going are a run of real
 * blows on the anvil, and this owns the run.
 *
 * <h2>Two runs, one mechanism</h2>
 * A recipe whose one-click forge is unlocked is struck through the sequence
 * {@link PerfectForgePlan} reads off the recipe - the fewest blows that finish it and satisfy its rules -
 * which is the run a flawless smith would have made, so TFC grades it perfect on its own terms. A recipe
 * that is not unlocked yet instead has the sequence its smith last finished there struck again, one blow at
 * a time, which is the button doing the smith's own work rather than handing over the result.
 *
 * <p>Both are the same thing to this class: a list of blows, a pause between them, and a check before each
 * one that the anvil still holds the work the run was started for. A perfect run strikes every
 * {@value #PERFECT_TICKS} ticks and a remembered one every {@value #MEMORY_TICKS}, and neither plays its own
 * sound or applies its own rules - the blow is handed to the anvil, which plays the strike, sparks, wears
 * the hammer, applies the recipe's rules and builds the item when the work is finished. Repeating TFC's
 * smithing here would be a second set of rules to keep in step with the first.
 *
 * <h2>Why one run per anvil</h2>
 * Two players at the same anvil, or one player clicking twice, would otherwise each strike their own blows
 * into the same piece - and two runs at once would interleave into a sequence neither smith ever struck.
 * The first click wins; the second is told the anvil is busy.
 *
 * <h2>Why every blow is checked</h2>
 * The item can be taken off the anvil, swapped for another, or struck by hand between two blows. A run is
 * therefore abandoned the moment the piece is not the one it was started on - the recipe or the strike count
 * no longer matching - rather than carrying on and ruining whatever is there now. A blow the anvil refuses
 * ends the run too, since the next one would be refused for the same reason.
 *
 * <h2>Why finishing says nothing</h2>
 * The last blow is the anvil's own blow, and it finishes the item exactly as a hand-struck one would: the
 * result appearing in the input slot is the whole answer, and it is already in front of the player. So
 * neither kind of run announces itself. A perfect run would only be narrating a button the player has just
 * pressed, and what a forge earned is the forge's business, said to whoever asked to hear it.
 *
 * <h2>Why a run may start on a struck piece</h2>
 * A run that is abandoned leaves its work where it stopped, and clicking the button again picks it up: the
 * blows it has already taken are counted against the sequence, which was worked out for exactly that piece.
 * The count a run compares the anvil against is therefore the one it found there, not nothing.
 */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PerfectForgeSequence {

    /** How long a perfect forge leaves between two blows: 3 ticks. */
    public static final int PERFECT_TICKS = 3;

    /** How long a memory forge leaves between two of the smith's blows: 6 ticks. */
    public static final int MEMORY_TICKS = 6;

    /** The runs in flight, at most one per anvil. */
    private static final Map<AnvilKey, Pending> PENDING = new HashMap<>();

    private PerfectForgeSequence() {
    }

    /**
     * Starts a run of blows on one anvil, unless it is already striking.
     *
     * <p>Everything the first blow needs has been checked by the caller, and every later blow is checked
     * again here as it is struck.
     *
     * @param alreadyStruck how many blows the piece on the anvil has already taken
     * @param strikes       the blows to strike, in order; must not be empty
     * @param perfect       whether this is the recipe's own sequence rather than the smith's remembered one
     * @return whether the run was started; {@code false} means this anvil is already busy
     */
    static boolean start(ServerPlayer player, AnvilBlockEntity anvil, AnvilRecipe recipe, int alreadyStruck,
                         List<ForgeStep> strikes, boolean perfect) {
        if (!(anvil.getLevel() instanceof ServerLevel level) || strikes.isEmpty()) {
            return false;
        }
        AnvilKey key = new AnvilKey(level.dimension(), anvil.getBlockPos().immutable());
        if (PENDING.containsKey(key)) {
            return false;
        }
        PENDING.put(key, new Pending(player.getUUID(), recipe.getId(), alreadyStruck, strikes, perfect));
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        List<AnvilKey> finished = new ArrayList<>();
        for (Map.Entry<AnvilKey, Pending> entry : PENDING.entrySet()) {
            if (entry.getValue().tick(server, entry.getKey())) {
                finished.add(entry.getKey());
            }
        }
        // Removed after the loop: a run sends messages and writes to the anvil as it ends, which must not
        // happen while the map is being walked.
        finished.forEach(PENDING::remove);
    }

    /** No run outlives the server that started it: its anvil and its smith belong to that world. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        // Part-finished memory drafts belong to that world's forges for the same reason.
        ForgeMemory.clearRuntime();
    }

    /** The forging on an anvil's main input, or {@code null} when nothing there is being worked. */
    private static Forging inputForging(AnvilBlockEntity anvil) {
        if (!(anvil instanceof InventoryBlockEntityAccessor<?> accessor)
                || !(accessor.getInventory() instanceof AnvilBlockEntity.AnvilInventory inventory)) {
            return null;
        }
        return ForgingCapability.get(inventory.getStackInSlot(AnvilBlockEntity.SLOT_INPUT_MAIN));
    }

    /** Tells a smith their run stopped short, since a run that stops silently reads as a click that was lost. */
    private static void interrupt(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }

    /** One anvil's run: who started it, what it was started for, and how far along it is. */
    private static final class Pending {

        private final UUID playerId;
        private final ResourceLocation recipeId;
        /** How many blows the piece had already taken when this run was started. */
        private final int before;
        /** The blows still to come, in order. */
        private final List<ForgeStep> strikes;
        /** Whether this is the recipe's own sequence rather than a remembered one. */
        private final boolean perfect;
        private int untilBlow;
        /** How many blows this run has struck: also the index of the next one. */
        private int struck;

        private Pending(UUID playerId, ResourceLocation recipeId, int before, List<ForgeStep> strikes,
                        boolean perfect) {
            this.playerId = playerId;
            this.recipeId = recipeId;
            this.before = before;
            this.strikes = List.copyOf(strikes);
            this.perfect = perfect;
        }

        /** The pause between two blows, which is the only thing the two kinds of run do differently. */
        private int interval() {
            return perfect ? PERFECT_TICKS : MEMORY_TICKS;
        }

        /** What to say when the anvil stops holding the work this run was started on. */
        private String interrupted() {
            return perfect ? "wildfires.smithing.perfect.interrupted" : "wildfires.smithing.memory.interrupted";
        }

        /**
         * Advances one tick: strikes the next blow if one is due, and reports the run as over when it is.
         *
         * @return whether the run is over, either way, so the ticker can forget it
         */
        private boolean tick(MinecraftServer server, AnvilKey key) {
            ServerLevel level = server.getLevel(key.dimension());
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (level == null || player == null) {
                // The world or the smith is gone; there is nobody left to forge for.
                return true;
            }
            if (!level.isLoaded(key.pos())) {
                return true;
            }
            if (!(level.getBlockEntity(key.pos()) instanceof AnvilBlockEntity anvil)) {
                // Broken, replaced, or simply no longer an anvil.
                return true;
            }
            if (untilBlow > 0) {
                untilBlow--;
                if (untilBlow > 0) {
                    return false;
                }
            }
            return strike(level, player, anvil);
        }

        /**
         * Strikes the next blow, through the anvil's own work method - so the strike sounds, sparks, wears
         * the hammer, applies the recipe's rules and finishes the item exactly as a smith's blow would.
         *
         * @return whether the run is over
         */
        private boolean strike(ServerLevel level, ServerPlayer player, AnvilBlockEntity anvil) {
            Forging forging = inputForging(anvil);
            AnvilRecipe recipe = forging == null ? null : forging.getRecipe(level);
            if (recipe == null || !recipe.getId().equals(recipeId)
                    || forging.getSteps().total() != before + struck) {
                // Taken off the anvil, swapped for another item, or struck by hand since the last blow: the
                // blows left were worked out for a piece that is no longer there. Clicking again starts a run
                // on whatever is there now, worked out from where it stands.
                interrupt(player, interrupted());
                return true;
            }
            if (anvil.work(player, strikes.get(struck)) != InteractionResult.SUCCESS) {
                // TFC refused the blow and has already said why - the metal has cooled, the hammer is gone,
                // the tier is too low - and would refuse the next one for the same reason.
                return true;
            }
            if (++struck >= strikes.size()) {
                // The last blow is the one that finished the recipe, so the anvil has built the item by now
                // and there is nothing left to hand over - and nothing to say about it either, since the
                // result is already in the input slot. The perfect run is quiet here for the same reason as
                // the remembered one: this is an ordinary forge that the player asked for, not a discovery.
                return true;
            }
            untilBlow = interval();
            return false;
        }
    }

    /** Which anvil a run belongs to: its position alone would collide across dimensions. */
    private record AnvilKey(ResourceKey<Level> dimension, BlockPos pos) {
    }
}
