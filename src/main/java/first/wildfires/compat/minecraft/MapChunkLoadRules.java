package first.wildfires.compat.minecraft;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <h2>What this decides</h2>
 * Reproduces, out of the few numbers one map update already carries, the exact chunks that
 * {@code MapItem.update} is about to read - so a guard in front of that method can ask whether all of
 * them are loaded before vanilla is allowed to ask for them. A map that owns a chunk which is not
 * loaded reaches {@code Level#getChunk}, and on the server thread that call parks the tick inside
 * {@code ServerChunkCache} on {@code managedBlock} until the chunk arrives. One such wait that
 * outlasts {@code max-tick-time} is all the watchdog needs to kill the server, which is what happened
 * to this pack: a held atlas over an area whose chunks were scheduled but not yet delivered.
 *
 * <h2>Why the window is reproduced instead of approximated</h2>
 * The obvious guard - "are the chunks of the map's whole visible area loaded?" - is wrong in both
 * directions. It would hold back a map that has one unloaded corner <em>pixel</em> for as long as
 * that corner stays unloaded, which reads in game as a map that quietly stops refreshing; and a
 * sampler coarse enough to avoid that can still miss the one chunk vanilla reads, which is the hang
 * this exists to prevent. So this class mirrors the pass itself: the same pixel window, the same
 * 1-in-16 decimation, the same pixel to block to chunk arithmetic. What it does not reproduce is the
 * colour work, which cannot load anything.
 *
 * <h2>The pass it mirrors</h2>
 * {@code update} walks pixel columns {@code i} from {@code pixelX - radius + 1} to
 * {@code pixelX + radius - 1} and inside each of them pixel rows {@code j} from
 * {@code pixelZ - radius - 1} to {@code pixelZ + radius - 1}, where {@code radius} is
 * {@code 128 / (1 << scale)} and is halved again in a dimension with a ceiling. Pixels outside the
 * 128x128 sheet are skipped, and the row range deliberately starts one below the sheet - row -1 is a
 * real chunk read. Within a sampled column every row is sampled, so that column's chunks form one
 * contiguous range in Z.
 *
 * <h2>The decimation, and why the phase has to move</h2>
 * A pixel column is only taken when its low four bits match the low four bits of the tick's phase, so
 * one tick in sixteen walks each column and the sheet rolls around every sixteen ticks. That phase is
 * the {@code step} counter of the map's {@code HoldingPlayer}, and vanilla increments it at the top
 * of the pass. A cancelled pass never gets there, so a guard that cancelled without touching
 * {@code step} would offer the server the same column on every tick and could sit on one unloaded
 * chunk forever. {@link #nextPhase} is what the caller needs for that; the caller is responsible for
 * writing the incremented value back.
 *
 * <h2>What it looks at</h2>
 * Nothing but primitives. This class has no Minecraft imports, so the rule is checkable by a plain
 * JVM test that never starts a game: the caller supplies the level-derived numbers - the player's
 * position, the map's centre and scale, and whether the dimension has a ceiling - and a probe for the
 * chunks. An empty window answers "nothing missing" without asking the probe at all, which is also
 * what vanilla does with a player too far from the drawn area.
 */
public final class MapChunkLoadRules {

    /** Side length of a map's colour sheet; vanilla walks 0..127 on both axes. */
    public static final int SHEET_SIZE = 128;

    /** Index of the sheet's centre column and row, and the offset the pass is written around. */
    public static final int SHEET_CENTER = 64;

    /** Ticks per full sweep of the sheet: the number of distinct phases. */
    public static final int PHASE_MODULUS = 16;

    private static final int PHASE_MASK = PHASE_MODULUS - 1;

    /** Radius in pixels of the sampled window before the scale divides it. */
    private static final int BASE_RADIUS = 128;

    /**
     * Lowest pixel row a pass reads. Vanilla skips a row below -1 and keeps -1 itself, so the first
     * usable row is this and not 0.
     */
    private static final int LOWEST_ROW = -1;

    private MapChunkLoadRules() {
    }

    /**
     * Answers whether one chunk can be read without waiting for it.
     *
     * <p>The guard supplies a probe that returns immediately - a load is never started by asking - and
     * the answer means "reading this chunk now would not wait", which is "the chunk is here now" plus any
     * chunk the reader itself refuses to wait for.
     */
    @FunctionalInterface
    public interface ChunkProbe {

        boolean isLoaded(int chunkX, int chunkZ);
    }

    /**
     * The geometry of one map update tick, described in the terms the pass itself uses.
     *
     * @param scale     the map's scale shift, so one pixel covers {@code 1 << scale} blocks
     * @param centerX   the map's centre block X
     * @param centerZ   the map's centre block Z
     * @param pixelX    the sheet column the player stands on
     * @param pixelZ    the sheet row the player stands on
     * @param radius    pixels from the player to the edge of the sampled window, after the ceiling
     * @param phase     the decimation phase this tick will use, already advanced
     */
    public record Update(int scale, int centerX, int centerZ, int pixelX, int pixelZ, int radius, int phase) {

        /** Blocks per pixel. The pass calls this {@code s}. */
        public int blockStep() {
            return 1 << this.scale;
        }

        /** First pixel column of the window, before clipping to the sheet. */
        public int firstColumn() {
            return this.pixelX - this.radius + 1;
        }

        /** Last pixel column of the window, before clipping to the sheet. */
        public int lastColumn() {
            return this.pixelX + this.radius - 1;
        }

        /** First pixel row of the window, before clipping. One below the sheet on purpose. */
        public int firstRow() {
            return this.pixelZ - this.radius - 1;
        }

        /** Last pixel row of the window, before clipping to the sheet. */
        public int lastRow() {
            return this.pixelZ + this.radius - 1;
        }

        /** Whether pixel column {@code i} falls inside the sheet. */
        public boolean columnOnSheet(int i) {
            return i >= 0 && i < SHEET_SIZE;
        }

        /** Whether pixel row {@code j} is one the pass will read. */
        public boolean rowReadable(int j) {
            return j >= LOWEST_ROW && j < SHEET_SIZE;
        }

        /**
         * Whether this tick's decimation selects pixel column {@code i}. Only every sixteenth column
         * is taken, which is what keeps one tick's cost bounded.
         */
        public boolean selected(int i) {
            return (i & PHASE_MASK) == this.phase;
        }

        /**
         * Chunk column of pixel column {@code i}. This is vanilla's
         * {@code (centerX / s + i - 64) * s} followed by {@code SectionPos.blockToSectionCoord},
         * whose division by sixteen rounds towards negative infinity - hence the shift, not a divide.
         */
        public int chunkXOf(int i) {
            return ((this.centerX / this.blockStep() + i - SHEET_CENTER) * this.blockStep()) >> 4;
        }

        /** Chunk row of pixel row {@code j}, by the same arithmetic as {@link #chunkXOf(int)}. */
        public int chunkZOf(int j) {
            return ((this.centerZ / this.blockStep() + j - SHEET_CENTER) * this.blockStep()) >> 4;
        }
    }

    /** A chunk coordinate, as reported by {@link #firstMissingChunk}. */
    public record Chunk(int x, int z) {
    }

    /**
     * Describes the pass the next tick would run.
     *
     * @param playerX    the holder's X
     * @param playerZ    the holder's Z
     * @param centerX    {@code MapItemSavedData#centerX}
     * @param centerZ    {@code MapItemSavedData#centerZ}
     * @param scale      {@code MapItemSavedData#scale}, the shift rather than the block size
     * @param hasCeiling whether the dimension type has a ceiling, which halves the radius
     * @param step       the holding player's current phase, before this tick advances it
     */
    public static Update update(double playerX, double playerZ, int centerX, int centerZ, int scale,
            boolean hasCeiling, int step) {
        int blockStep = 1 << scale;
        int radius = BASE_RADIUS / blockStep;
        if (hasCeiling) {
            radius /= 2;
        }
        int pixelX = floor(playerX - centerX) / blockStep + SHEET_CENTER;
        int pixelZ = floor(playerZ - centerZ) / blockStep + SHEET_CENTER;
        return new Update(scale, centerX, centerZ, pixelX, pixelZ, radius, nextPhase(step));
    }

    /**
     * The phase the pass about to run will use. Vanilla advances {@code step} before it sweeps, so a
     * caller reading the counter gets the phase of the <em>next</em> tick and not of the last one.
     */
    public static int nextPhase(int step) {
        return (step + 1) & PHASE_MASK;
    }

    /**
     * The first chunk in the window that is not loaded, or {@code null} when every chunk of the window
     * is here. The probe is asked in the order the pass reaches the chunks - column by column, rows
     * first within a column - so the walk stops at the first miss and the whole window is never
     * examined when one chunk is enough to answer.
     */
    public static Chunk firstMissingChunk(Update update, ChunkProbe probe) {
        int firstRow = Math.max(update.firstRow(), LOWEST_ROW);
        int lastRow = Math.min(update.lastRow(), SHEET_SIZE - 1);
        if (firstRow > lastRow) {
            return null;
        }
        int firstColumn = update.firstColumn();
        int lastColumn = update.lastColumn();
        if (lastColumn < 0 || firstColumn >= SHEET_SIZE) {
            return null;
        }
        int firstChunkZ = update.chunkZOf(firstRow);
        int lastChunkZ = update.chunkZOf(lastRow);
        for (int i = firstColumn; i <= lastColumn; i++) {
            if (!update.columnOnSheet(i) || !update.selected(i)) {
                continue;
            }
            int chunkX = update.chunkXOf(i);
            for (int chunkZ = firstChunkZ; chunkZ <= lastChunkZ; chunkZ++) {
                if (!probe.isLoaded(chunkX, chunkZ)) {
                    return new Chunk(chunkX, chunkZ);
                }
            }
        }
        return null;
    }

    /** Whether the pass would read a chunk that is not loaded, which is the case worth stopping. */
    public static boolean anyChunkMissing(Update update, ChunkProbe probe) {
        return firstMissingChunk(update, probe) != null;
    }

    /**
     * Floor of a double as an int, which is what {@code Mth.floor} does: truncation drops towards
     * zero, so a negative fraction needs the extra step.
     */
    private static int floor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    /**
     * <h2>What this is</h2>
     * The bookkeeping that keeps a deferred map from writing a line per tick: the first offer of a key
     * is accepted, a repeat is dropped without a word, and only {@code limit} distinct keys are ever
     * accepted.
     *
     * <h2>Why the repeats have to be dropped</h2>
     * The guard runs once per map per tick, on the server thread, for as long as the condition lasts -
     * a map held over a stretch of unloaded chunks would otherwise fill the log for minutes. One line
     * per map is enough to identify the map, which is why the key is the map and not the chunk over
     * which it failed.
     *
     * <h2>Thread safety</h2>
     * Map updates run on the server thread, but a map item can also be updated from a client level
     * and this guard is reachable from both. The set and both counters are concurrent, and the limit
     * announcement is a one-shot compare-and-set.
     */
    public static final class ReportBudget {

        /** How many distinct maps are named before the guard goes quiet. */
        public static final int DEFAULT_LIMIT = 8;

        private final int limit;
        private final Set<String> offered = ConcurrentHashMap.newKeySet();
        private final AtomicInteger suppressed = new AtomicInteger();
        private final AtomicBoolean limitAnnounced = new AtomicBoolean();

        public ReportBudget() {
            this(DEFAULT_LIMIT);
        }

        public ReportBudget(int limit) {
            this.limit = Math.max(1, limit);
        }

        /**
         * Whether {@code key} has already been taken, without taking it. Useful when the caller has a
         * second reason to stay quiet - a cooldown, say - and must not spend the key on a line it is
         * not going to write.
         */
        public boolean contains(String key) {
            return this.offered.contains(key == null ? "<unknown>" : key);
        }

        /**
         * Whether {@code key} should be logged now. False means the key was already taken, or that it
         * arrived after the limit was full; {@link #suppressed()} counts the second case only.
         */
        public boolean offer(String key) {
            String normalized = key == null ? "<unknown>" : key;
            if (!offered.add(normalized)) {
                return false;
            }
            if (offered.size() > this.limit) {
                this.suppressed.incrementAndGet();
                return false;
            }
            return true;
        }

        /**
         * Whether the limit has just swallowed its first key. Answers true once, so the caller can say
         * that it has stopped naming maps without saying it again for every map after that.
         */
        public boolean limitJustReached() {
            return this.suppressed.get() > 0 && this.limitAnnounced.compareAndSet(false, true);
        }

        /** How many distinct keys were accepted, which is the limit once it is full. */
        public int reported() {
            return Math.min(this.offered.size(), this.limit);
        }

        /** How many distinct keys arrived after the limit was full. */
        public int suppressed() {
            return this.suppressed.get();
        }

        public int limit() {
            return this.limit;
        }
    }
}
