package first.wildfires.minecraft;

import first.wildfires.compat.minecraft.MapChunkLoadRules;
import first.wildfires.compat.minecraft.MapChunkLoadRules.Chunk;
import first.wildfires.compat.minecraft.MapChunkLoadRules.ReportBudget;
import first.wildfires.compat.minecraft.MapChunkLoadRules.Update;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plain-Java checks for the rule that decides whether a map update would have to wait for a chunk,
 * and for the budget behind its log line.
 *
 * <p>Everything here rests on one claim: the chunks this rule asks about are the chunks
 * {@code MapItem.update} would read - no more than those, and above all no fewer. Asking about a
 * chunk vanilla never touches costs a lookup and, at worst, one deferred tick, while missing one that
 * vanilla does touch leaves the hang this exists to prevent, so both directions are pinned below.
 *
 * <p>The oracle is a second, independent walk of the pass: the test loops the pixel window itself,
 * with vanilla's own clipping and 1-in-16 decimation, and collects the chunks it reaches. The rule's
 * probed set is compared against it - as an equality for the scales at which one row step cannot skip
 * a chunk, and as a containment for the coarser scales, where vanilla jumps over chunks and asking
 * about the chunks in between is the conservative answer.
 *
 * <p>What is left to the in-game pass is the wiring: that the mixin applies at the head of
 * {@code MapItem#update} on a server, and that a map held over a loading border keeps refreshing
 * instead of stopping or hanging.
 */
public final class MapChunkLoadRulesSelfTest {

    /** Every phase, in the order {@code step} walks them: the phase is the counter plus one. */
    private static final int PHASES = MapChunkLoadRules.PHASE_MODULUS;

    private MapChunkLoadRulesSelfTest() {
    }

    public static void main(String[] args) {
        theWindowIsWhereVanillaLooks();
        theCeilingDimensionHalvesTheWindow();
        theScaleWidensWhatOnePixelCovers();
        thePhaseAdvancesLikeVanilla();
        aFarAwayMapReadsNothing();
        theWalkAsksAboutTheChunksVanillaTouches();
        theWalkNeverMissesAChunkVanillaTouches();
        theWalkStopsAtTheFirstMissingChunk();
        oneSweepCoversEveryColumn();
        theBudgetNamesEachMapOnce();
        theBudgetAnnouncesTheLimitOnce();
        theBudgetTakesWhateverItIsGiven();
        System.out.println("MapChunkLoadRulesSelfTest: all checks passed");
    }

    /**
     * The geometry of the pass, at the one place it can be counted by hand: a player at the centre of
     * a scale-0 map. One pixel is one block, the window is 128 pixels to each side, and the sheet is
     * 128 wide - so the window starts 63 pixels to the left of the centre and ends 63 to the right of
     * it, with the row range one lower than the column range because vanilla starts a row below.
     */
    private static void theWindowIsWhereVanillaLooks() {
        Update update = MapChunkLoadRules.update(0.5D, 0.5D, 0, 0, 0, false, 0);
        assertEquals(1, update.blockStep(), "a scale of zero covers one block per pixel");
        assertEquals(128, update.radius(), "and reaches 128 pixels in every direction");
        assertEquals(64, update.pixelX(), "the player stands on the sheet's centre column");
        assertEquals(64, update.pixelZ(), "and on its centre row");
        assertEquals(-63, update.firstColumn(), "the window starts 63 columns to the left");
        assertEquals(191, update.lastColumn(), "and ends 63 to the right");
        assertEquals(-65, update.firstRow(), "the row window starts one row lower still");
        assertEquals(191, update.lastRow(), "and ends 63 rows below the centre");
        assertEquals(MapChunkLoadRules.nextPhase(0), update.phase(),
                "the phase is the one this tick will use, not the one the counter still holds");

        assertEquals(-4, update.chunkXOf(1), "pixel column 1 stands for block -63, in chunk -4");
        assertEquals(3, update.chunkXOf(113), "and pixel column 113 stands for block 49, in chunk 3");
        assertEquals(-5, update.chunkZOf(-1),
                "the row below the sheet is a real read, in the chunk below the sheet");
        assertEquals(3, update.chunkZOf(127), "while the sheet's last row is its last chunk");
    }

    private static void theCeilingDimensionHalvesTheWindow() {
        assertEquals(64, MapChunkLoadRules.update(0.5D, 0.5D, 0, 0, 0, true, 0).radius(),
                "a dimension with a ceiling reads half the radius");
        assertEquals(16, MapChunkLoadRules.update(0.5D, 0.5D, 0, 0, 2, true, 0).radius(),
                "and halves it after the scale has already divided it");
    }

    /**
     * The scale divides the window and widens what a pixel stands for, and a player's offset from the
     * map's centre is floored as a block offset and only then divided - the one place where getting
     * the rounding wrong would put the window a pixel off for a player standing below or west of the
     * centre.
     */
    private static void theScaleWidensWhatOnePixelCovers() {
        Update update = MapChunkLoadRules.update(1064.0D, -521.0D, 1024, -512, 2, false, 0);
        assertEquals(4, update.blockStep(), "a scale of two covers four blocks per pixel");
        assertEquals(32, update.radius(), "and reaches 32 pixels in every direction");
        assertEquals(74, update.pixelX(), "40 blocks east of the centre is 10 pixels east of it");
        assertEquals(62, update.pixelZ(),
                "and 9 blocks west of the centre is 2 pixels to its left, the block offset being floored "
                        + "and the division truncating");
        assertEquals(63, MapChunkLoadRules.update(0.5D, -0.5D, 0, 0, 0, false, 0).pixelZ(),
                "a player half a block north of the centre is on the row below it, not on the centre row");
    }

    private static void thePhaseAdvancesLikeVanilla() {
        assertEquals(1, MapChunkLoadRules.nextPhase(0), "vanilla advances the counter before it sweeps");
        assertEquals(15, MapChunkLoadRules.nextPhase(14), "so the last phase of a sweep is 15");
        assertEquals(0, MapChunkLoadRules.nextPhase(15), "and the sweep starts over");
        assertEquals(1, MapChunkLoadRules.nextPhase(16), "however far the counter has run");
        assertEquals(1, MapChunkLoadRules.nextPhase(32), "a counter that is a multiple of the sweep wraps");
    }

    /**
     * A player far enough from the drawn area leaves the window entirely off the sheet, and vanilla
     * then reads no chunk at all - so the rule must answer "nothing missing" without asking the probe
     * a single question, whatever the probe would have said. Each direction of "off the sheet" is
     * walked separately, including the row clip, which fires on its own when the columns are still on
     * the sheet.
     */
    private static void aFarAwayMapReadsNothing() {
        assertReadsNothing(MapChunkLoadRules.update(1200.0D, 0.5D, 0, 0, 4, false, 0), "east of the sheet");
        assertReadsNothing(MapChunkLoadRules.update(-1200.0D, 0.5D, 0, 0, 4, false, 0), "west of the sheet");
        assertReadsNothing(MapChunkLoadRules.update(0.5D, 1200.0D, 0, 0, 4, false, 0), "south of the sheet");
        assertReadsNothing(MapChunkLoadRules.update(0.5D, -1200.0D, 0, 0, 4, false, 0), "north of the sheet");

        Update onSheet = MapChunkLoadRules.update(0.5D, 0.5D, 0, 0, 0, false, 0);
        assertEquals(true, MapChunkLoadRules.anyChunkMissing(onSheet, (x, z) -> false),
                "while a player on the sheet does read chunks, so an empty world defers the update");
    }

    private static void assertReadsNothing(Update update, String where) {
        AtomicInteger probes = new AtomicInteger();
        Chunk missing = MapChunkLoadRules.firstMissingChunk(update, (x, z) -> {
            probes.incrementAndGet();
            return false;
        });
        assertEquals(null, missing, "a window entirely " + where + " has no chunk to be missing");
        assertEquals(0, probes.get(), "and it is answered without asking about any chunk " + where);
    }

    /**
     * The heart of the rule: for the scales at which one row step is at most a chunk, the probed set
     * is exactly the set of chunks vanilla's own walk reaches. The oracle below walks the pass
     * independently - the pixel window, the clipping, the decimation, the pixel to block to chunk
     * arithmetic - so a mistake in the rule has to be repeated in the oracle to survive.
     */
    private static void theWalkAsksAboutTheChunksVanillaTouches() {
        int nonEmpty = 0;
        for (int scale = 0; scale <= 4; scale++) {
            for (boolean hasCeiling : new boolean[] {false, true}) {
                for (double offset : new double[] {0.5D, 7.0D, -7.0D, 64.0D, -64.0D}) {
                    Update update = MapChunkLoadRules.update(offset, -offset, 128, -64, scale, hasCeiling, 5);
                    Set<String> probed = probedChunks(update);
                    Set<String> touched = touchedChunks(update);
                    assertEquals(touched, probed, "scale " + scale + ", ceiling " + hasCeiling + ", offset "
                            + offset + ": the rule asks about exactly the chunks the pass reads");
                    if (!touched.isEmpty()) {
                        nonEmpty++;
                    }
                }
            }
        }
        assertEquals(true, nonEmpty > 0, "and at least some of those geometries read a chunk at all");
    }

    /**
     * Above the scale at which a row step is exactly one chunk, vanilla's own walk skips chunks: a
     * scale-6 pixel is 64 blocks wide, so two neighbouring rows stand four chunks apart and the chunks
     * between them are never read. The rule asks about them anyway. That is the direction that
     * matters - a false "not loaded" costs one deferred tick, a missed chunk costs the server - and it
     * is asserted here as containment.
     */
    private static void theWalkNeverMissesAChunkVanillaTouches() {
        for (int scale = 0; scale <= 6; scale++) {
            for (boolean hasCeiling : new boolean[] {false, true}) {
                for (double offset : new double[] {0.5D, 33.0D, -33.0D}) {
                    Update update = MapChunkLoadRules.update(offset, 0.5D, -4096, 4096, scale, hasCeiling, 11);
                    Set<String> probed = probedChunks(update);
                    Set<String> missed = new LinkedHashSet<>(touchedChunks(update));
                    missed.removeAll(probed);
                    assertEquals(new LinkedHashSet<String>(), missed,
                            "scale " + scale + ", ceiling " + hasCeiling + ", offset " + offset
                                    + ": every chunk the pass reads is one the rule asks about");
                }
            }
        }
    }

    /**
     * The probe is asked in the order the pass reaches chunks, columns first, and the walk stops at
     * the first chunk that is not loaded - so one unloaded chunk near the start of the window costs
     * one column of questions, not the whole window.
     */
    private static void theWalkStopsAtTheFirstMissingChunk() {
        Update update = MapChunkLoadRules.update(0.5D, 0.5D, 0, 0, 0, false, 0);
        AtomicInteger probes = new AtomicInteger();
        Chunk missing = MapChunkLoadRules.firstMissingChunk(update, (x, z) -> {
            probes.incrementAndGet();
            return !(x == -3 && z == -5);
        });
        assertEquals(new Chunk(-3, -5), missing, "the walk reports the chunk it could not read");
        assertEquals(10, probes.get(),
                "after the whole first column and one row of the second, not the whole window");
        assertEquals(true, MapChunkLoadRules.anyChunkMissing(update, (x, z) -> !(x == -3 && z == -5)),
                "and the boolean form agrees with it");
        assertEquals(72, probedChunks(update).size(),
                "while a window that is completely here costs eight columns of nine rows");
        assertEquals(null, MapChunkLoadRules.firstMissingChunk(update, (x, z) -> true),
                "a window whose chunks are all here asks for no deferral");
    }

    /**
     * The decimation is not a loss: over the sixteen phases of one sweep, every column of the window
     * is walked exactly once. The check uses a scale at which one pixel is a whole chunk, so a probed
     * chunk identifies the column that asked for it - at finer scales sixteen columns share a chunk.
     */
    private static void oneSweepCoversEveryColumn() {
        Set<Integer> covered = new HashSet<>();
        int probedColumns = 0;
        int widestPhase = 0;
        for (int step = 0; step < PHASES; step++) {
            Update update = MapChunkLoadRules.update(0.5D, 0.5D, 0, 0, 4, false, step);
            assertEquals(57, update.firstColumn(), "the scale-4 window is 15 columns wide");
            assertEquals(71, update.lastColumn(), "and centred on the player's column");
            Set<Integer> columns = probedColumns(update);
            widestPhase = Math.max(widestPhase, columns.size());
            probedColumns += columns.size();
            covered.addAll(columns);
        }
        assertEquals(15, probedColumns,
                "each of the window's 15 columns is walked by one phase of the sweep");
        assertEquals(15, covered.size(), "and no column is walked twice");
        assertEquals(1, widestPhase, "so no phase has more than its share of the window to read");
    }

    private static void theBudgetNamesEachMapOnce() {
        ReportBudget budget = new ReportBudget(2);
        assertEquals(true, budget.offer("overworld@0,0"), "the first map that is deferred is named");
        assertEquals(false, budget.contains("overworld@128,0"), "a map that has not been named is unknown");
        assertEquals(false, budget.offer("overworld@0,0"), "the same map is not named again");
        assertEquals(true, budget.contains("overworld@0,0"), "and a name that was taken is recognisable");
        assertEquals(0, budget.suppressed(), "and a repeat is not counted as a suppressed map");
        assertEquals(true, budget.offer("overworld@128,0"), "a second map is named");
        assertEquals(false, budget.offer("overworld@256,0"), "a third is not");
        assertEquals(1, budget.suppressed(), "but it is counted");
        assertEquals(2, budget.reported(), "and the budget reports as many maps as it named");
        assertEquals(2, budget.limit(), "with the limit it was built with");
    }

    private static void theBudgetAnnouncesTheLimitOnce() {
        ReportBudget budget = new ReportBudget(1);
        assertEquals(false, budget.limitJustReached(), "nothing has been dropped yet");
        budget.offer("a");
        assertEquals(false, budget.limitJustReached(), "and a budget under its limit says nothing");
        budget.offer("b");
        assertEquals(true, budget.limitJustReached(), "the first map past the limit is the moment to say so");
        assertEquals(false, budget.limitJustReached(), "which is said once, not once per map");
        budget.offer("c");
        assertEquals(2, budget.suppressed(), "while every map after it is still counted");
    }

    private static void theBudgetTakesWhateverItIsGiven() {
        ReportBudget budget = new ReportBudget(0);
        assertEquals(true, budget.offer("a"), "a limit of zero is raised to one rather than silencing the guard");
        assertEquals(false, budget.offer("b"), "and then behaves like a limit of one");

        ReportBudget nullKeyed = new ReportBudget(2);
        assertEquals(false, nullKeyed.contains(null), "a key that was never offered is not recognised");
        assertEquals(true, nullKeyed.offer(null),
                "a map with no dimension of its own still has a key to offer");
        assertEquals(true, nullKeyed.contains(null), "and afterwards that key is recognised");
    }

    /** The chunks the rule asks about, for a probe that reports every chunk as loaded. */
    private static Set<String> probedChunks(Update update) {
        Set<String> probed = new LinkedHashSet<>();
        assertEquals(null, MapChunkLoadRules.firstMissingChunk(update, (x, z) -> {
            probed.add(x + "," + z);
            return true;
        }), "a probe that answers yes to everything leaves nothing missing");
        return probed;
    }

    /** The chunk columns the rule asks about, at a scale where one column is a whole chunk. */
    private static Set<Integer> probedColumns(Update update) {
        Set<Integer> columns = new HashSet<>();
        MapChunkLoadRules.firstMissingChunk(update, (x, z) -> {
            columns.add(x);
            return true;
        });
        return columns;
    }

    /**
     * An independent walk of {@code MapItem#update}'s own loops over the same geometry, collecting the
     * chunks it reaches. Written from the pass rather than from the rule, on purpose.
     */
    private static Set<String> touchedChunks(Update update) {
        Set<String> touched = new LinkedHashSet<>();
        for (int i = update.pixelX() - update.radius() + 1; i < update.pixelX() + update.radius(); i++) {
            if ((i & (PHASES - 1)) != update.phase()) {
                continue;
            }
            if (i < 0 || i >= MapChunkLoadRules.SHEET_SIZE) {
                continue;
            }
            for (int j = update.pixelZ() - update.radius() - 1; j < update.pixelZ() + update.radius(); j++) {
                if (j < -1 || j >= MapChunkLoadRules.SHEET_SIZE) {
                    continue;
                }
                touched.add(update.chunkXOf(i) + "," + update.chunkZOf(j));
            }
        }
        return touched;
    }

    private static void assertEquals(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(boolean expected, boolean actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }
}
