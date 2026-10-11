package first.wildfires.smithing;

import net.dries007.tfc.common.capabilities.forge.ForgeStep;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Plain-Java checks for the memory strip laid out above the anvil's panel.
 *
 * <p>The strip is five cells read from the player's own remembered blows: the middle one is the blow about to
 * be struck, and the two on either side fade outwards. What is pinned here is the window arithmetic - which
 * remembered blow lands in which cell as the piece is worked through - because an off-by-one would put the
 * wrong icon under the player's eye at the one moment they are reading it, and because the three-blows-deep
 * context on each side is the whole reason the strip exists next to a record of the full sequence.
 *
 * <p>The fade and the artwork order are pinned too, for the same class of reason: an icon drawn at the wrong
 * offset from the sheet shows a different tool than the one remembered, and that is a lie the player cannot
 * see through.
 */
public final class ForgeMemoryStripSelfTest {

    /** A remembered sequence of the usual kind: more blows than the strip has cells. */
    private static final List<ForgeStep> MEMORY = List.of(ForgeStep.SHRINK, ForgeStep.UPSET, ForgeStep.SHRINK,
            ForgeStep.HIT_LIGHT, ForgeStep.HIT_MEDIUM, ForgeStep.HIT_HARD, ForgeStep.DRAW);

    private ForgeMemoryStripSelfTest() {
    }

    public static void main(String[] args) {
        try {
            theStripIsAlwaysFiveCellsWide();
            theMiddleCellIsTheBlowAboutToBeStruck();
            theFadeRunsFromTheMiddleOutwards();
            theBlowsAlreadyStruckStayOnTheLeft();
            aFinishedPieceLeavesTheMiddleCellEmpty();
            aPieceWorkedPastTheRecordStopsAtItsEnd();
            deviationIsJudgedAgainstTheSamePlaceInTheRecord();
            theWorkIsWhatTheBlowsAddUpTo();
            theArtworkOrderCoversEveryStepOnce();
            System.out.println("ForgeMemoryStripSelfTest: all checks passed");
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            System.out.println("ForgeMemoryStripSelfTest: skipped, Minecraft classes are unavailable (" + e + ")");
        }
    }

    private static void theStripIsAlwaysFiveCellsWide() {
        for (int struck = -1; struck <= MEMORY.size() + 2; struck++) {
            List<ForgeMemoryStrip.Cell> cells = ForgeMemoryStrip.of(MEMORY, struck);
            assertEquals(ForgeMemoryStrip.CELLS, cells.size(),
                    "a strip struck " + struck + " blows in still has " + ForgeMemoryStrip.CELLS + " cells");
            assertEquals(5, ForgeMemoryStrip.CELLS, "the strip is the middle cell and two either side");
            for (int i = 0; i < cells.size(); i++) {
                assertEquals(i - ForgeMemoryStrip.RADIUS, cells.get(i).offset(),
                        "cell " + i + " sits at its own offset from the middle");
            }
            assertTrue(cell(cells, 0).current(), "the middle cell is the one marked as next");
            assertTrue(!cell(cells, -1).current() && !cell(cells, 1).current(),
                    "and only the middle one is");
        }
    }

    private static void theMiddleCellIsTheBlowAboutToBeStruck() {
        for (int struck = 0; struck < MEMORY.size(); struck++) {
            assertEquals(MEMORY.get(struck), cell(ForgeMemoryStrip.of(MEMORY, struck), 0).step(),
                    "after " + struck + " blows the middle cell is blow " + struck + " of the record");
        }
        assertTrue(cell(ForgeMemoryStrip.of(List.of(), 0), 0).isEmpty(),
                "a record with no blows has nothing in the middle");
    }

    private static void theFadeRunsFromTheMiddleOutwards() {
        List<ForgeMemoryStrip.Cell> cells = ForgeMemoryStrip.of(MEMORY, 3);
        float[] expected = {0.40F, 0.60F, 1.0F, 0.60F, 0.40F};
        for (int i = 0; i < cells.size(); i++) {
            assertEquals(expected[i], cells.get(i).alpha(),
                    "cell " + i + " fades by its distance from the middle");
        }
        assertEquals(1.0F, cell(cells, 0).alpha(), "the blow being struck is fully opaque");
        assertEquals(cell(cells, -1).alpha(), cell(cells, 1).alpha(), "and the fade is the same both ways");
        assertEquals(cell(cells, -2).alpha(), cell(cells, 2).alpha(), "even at the shoulders");
    }

    private static void theBlowsAlreadyStruckStayOnTheLeft() {
        int struck = 4;
        List<ForgeMemoryStrip.Cell> cells = ForgeMemoryStrip.of(MEMORY, struck);
        assertEquals(MEMORY.get(struck - 2), cell(cells, -2).step(), "two blows back is on the far left");
        assertEquals(MEMORY.get(struck - 1), cell(cells, -1).step(), "the last blow struck is just left");
        assertEquals(MEMORY.get(struck), cell(cells, 0).step(), "and the next one is in the middle");
        assertEquals(MEMORY.get(struck + 1), cell(cells, 1).step(), "the blow after that is just right");
        assertEquals(MEMORY.get(struck + 2), cell(cells, 2).step(), "and the one after that on the far right");

        List<ForgeMemoryStrip.Cell> fresh = ForgeMemoryStrip.of(MEMORY, 0);
        assertTrue(cell(fresh, -1).isEmpty() && cell(fresh, -2).isEmpty(),
                "nothing has been struck yet, so there is nothing behind the middle");
    }

    private static void aFinishedPieceLeavesTheMiddleCellEmpty() {
        List<ForgeMemoryStrip.Cell> cells = ForgeMemoryStrip.of(MEMORY, MEMORY.size());
        assertTrue(cell(cells, 0).isEmpty(), "with the whole record struck the middle cell is empty");
        assertEquals(MEMORY.get(MEMORY.size() - 1), cell(cells, -1).step(), "the last blow is behind it");
        assertEquals(MEMORY.get(MEMORY.size() - 2), cell(cells, -2).step(), "and the one before that");
        assertTrue(cell(cells, 1).isEmpty() && cell(cells, 2).isEmpty(),
                "with nothing left ahead of it either");
    }

    private static void aPieceWorkedPastTheRecordStopsAtItsEnd() {
        for (int struck : new int[] {MEMORY.size() + 1, MEMORY.size() + 20}) {
            List<ForgeMemoryStrip.Cell> cells = ForgeMemoryStrip.of(MEMORY, struck);
            assertTrue(cell(cells, 0).isEmpty(),
                    "a piece struck " + struck + " blows holds the window at the end of the record");
            assertEquals(MEMORY.get(MEMORY.size() - 1), cell(cells, -1).step(),
                    "so the last remembered blows are still readable");
        }
        // Fewer than no blows cannot happen, but the window still has to land somewhere sane rather than
        // walk off the front of the record.
        List<ForgeMemoryStrip.Cell> cells = ForgeMemoryStrip.of(MEMORY, -3);
        assertEquals(MEMORY.get(0), cell(cells, 0).step(), "a negative count reads as nothing struck yet");
    }

    private static void deviationIsJudgedAgainstTheSamePlaceInTheRecord() {
        int struck = 3;
        assertTrue(!ForgeMemoryStrip.deviated(MEMORY, struck, MEMORY.get(struck - 1)),
                "the blow the record expects is not a deviation");
        ForgeStep other = MEMORY.get(struck - 1) == ForgeStep.SHRINK ? ForgeStep.PUNCH : ForgeStep.SHRINK;
        assertTrue(ForgeMemoryStrip.deviated(MEMORY, struck, other),
                "a different blow struck in its place is");

        assertTrue(!ForgeMemoryStrip.deviated(MEMORY, 0, null), "nothing struck, nothing to deviate from");
        assertTrue(!ForgeMemoryStrip.deviated(MEMORY, 0, ForgeStep.PUNCH),
                "and a piece struck no blows cannot have deviated");
        assertTrue(ForgeMemoryStrip.deviated(MEMORY, MEMORY.size() + 1, MEMORY.get(0)),
                "a piece worked further than the record describes has left it behind");
        assertTrue(!ForgeMemoryStrip.deviated(List.of(), 0, ForgeStep.PUNCH),
                "a record with no blows cannot be deviated from by a piece not yet struck");
    }

    private static void theWorkIsWhatTheBlowsAddUpTo() {
        assertEquals(0, ForgeMemoryStrip.work(List.of()), "no blows leave the work where it started");
        // TFC's own values: light -3, medium -6, hard -9, draw -15, punch +2, bend +7, upset +13, shrink +16.
        assertEquals(-18, ForgeMemoryStrip.work(List.of(ForgeStep.HIT_LIGHT, ForgeStep.HIT_MEDIUM,
                ForgeStep.HIT_HARD)), "the three hits add up to eighteen of work");
        assertEquals(38, ForgeMemoryStrip.work(List.of(ForgeStep.PUNCH, ForgeStep.BEND, ForgeStep.UPSET,
                ForgeStep.SHRINK)), "the four shaping blows add up to thirty-eight");
        assertEquals(23, ForgeMemoryStrip.work(List.of(ForgeStep.PUNCH, ForgeStep.BEND, ForgeStep.UPSET,
                ForgeStep.SHRINK, ForgeStep.DRAW)), "and a draw takes fifteen of that back off");
        assertEquals(ForgeStep.PUNCH.step(), ForgeMemoryStrip.work(List.of(ForgeStep.PUNCH)),
                "one blow is worth exactly what TFC says it is");
    }

    private static void theArtworkOrderCoversEveryStepOnce() {
        assertEquals(ForgeStep.VALUES.length, ForgeMemoryStrip.ART_ORDER.size(),
                "the sheet has a frame for every step");
        assertEquals(new HashSet<>(List.of(ForgeStep.VALUES)), new HashSet<>(ForgeMemoryStrip.ART_ORDER),
                "and lists each of them exactly once");

        Set<Integer> columns = new HashSet<>();
        for (ForgeStep step : ForgeStep.VALUES) {
            int u = ForgeMemoryStrip.iconU(step);
            assertEquals(ForgeMemoryStrip.ART_ORDER.indexOf(step) * ForgeMemoryStrip.ICON_SIZE, u,
                    step + " is drawn at its own frame of the sheet");
            assertTrue(u >= 0 && u + ForgeMemoryStrip.ICON_SIZE
                            <= ForgeMemoryStrip.ICON_SIZE * ForgeMemoryStrip.ART_ORDER.size(),
                    step + " is drawn inside the sheet");
            assertTrue(columns.add(u), step + " does not share a frame with another step");
        }
    }

    /** The cell at this offset from the middle. */
    private static ForgeMemoryStrip.Cell cell(List<ForgeMemoryStrip.Cell> cells, int offset) {
        for (ForgeMemoryStrip.Cell cell : cells) {
            if (cell.offset() == offset) {
                return cell;
            }
        }
        throw new AssertionError("no cell at offset " + offset);
    }

    private static void assertEquals(Object expected, Object actual, String what) {
        if (!expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean value, String what) {
        if (!value) {
            throw new AssertionError(what);
        }
    }
}
