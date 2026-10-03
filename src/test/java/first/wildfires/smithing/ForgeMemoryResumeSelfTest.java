package first.wildfires.smithing;

import net.dries007.tfc.common.capabilities.forge.ForgeStep;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain-Java checks for picking an interrupted memory forge up where it stopped.
 *
 * <p>A replay that is interrupted leaves its piece part worked, and clicking the button again is supposed to
 * finish that piece rather than refuse it. What is pinned here is the check that decides whether the piece on
 * the anvil is the work the remembered sequence had got to: the work it stands at, and the last three blows
 * it has taken. TFC reads a recipe's target against the first and its rules against the second, so a piece
 * that agrees with the sequence on both is finished the same way by the rest of it - whichever hand struck
 * the blows that came before.
 *
 * <p>Everything a piece could be instead is refused, because a replay that guessed would be working the item
 * with blows its smith never struck: a piece at the wrong work, a piece whose last blows are someone else's,
 * and a piece from a recipe with no sequence at all.
 */
public final class ForgeMemoryResumeSelfTest {

    /** A sequence of the kind a forge leaves behind: five blows, more than the three TFC reads back. */
    private static final List<ForgeStep> SEQUENCE = List.of(ForgeStep.SHRINK, ForgeStep.UPSET, ForgeStep.SHRINK,
            ForgeStep.HIT_LIGHT, ForgeStep.HIT_MEDIUM);

    private ForgeMemoryResumeSelfTest() {
    }

    public static void main(String[] args) {
        try {
            aFreshPieceIsStruckThroughTheWholeSequence();
            aStruckPieceCarriesOnFromTheNextBlow();
            aPieceThatIsNotThatSequencesWorkIsRefused();
            aWindowOfTheWrongShapeIsRefused();
            thereIsNothingLeftOnceTheSequenceIsSpent();
            theRemainderHandedBackIsItsOwn();
            System.out.println("ForgeMemoryResumeSelfTest: all checks passed");
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            System.out.println("ForgeMemoryResumeSelfTest: skipped, Minecraft classes are unavailable (" + e + ")");
        }
    }

    private static void aFreshPieceIsStruckThroughTheWholeSequence() {
        assertEquals(SEQUENCE, ForgeMemory.remaining(SEQUENCE, 0, 0, List.of()),
                "an untouched piece is struck through the whole sequence");
    }

    private static void aStruckPieceCarriesOnFromTheNextBlow() {
        for (int struck = 1; struck < SEQUENCE.size(); struck++) {
            List<ForgeStep> left = ForgeMemory.remaining(SEQUENCE, struck, work(struck), window(struck));
            assertEquals(SEQUENCE.subList(struck, SEQUENCE.size()), left,
                    "a piece struck " + struck + " blows in carries on from the next one");
        }
    }

    private static void aPieceThatIsNotThatSequencesWorkIsRefused() {
        int struck = 3;
        assertTrue(ForgeMemory.remaining(SEQUENCE, struck, work(struck) + 1, window(struck)) == null,
                "a piece left at the wrong work is refused");
        assertTrue(ForgeMemory.remaining(SEQUENCE, struck, work(struck) - 1, window(struck)) == null,
                "and so is one left at less work than the same blows would have done");
        assertTrue(ForgeMemory.remaining(List.of(), 0, 0, List.of()) == null,
                "a recipe with no sequence remembered has nothing to strike");
        assertTrue(ForgeMemory.remaining(SEQUENCE, -1, 0, List.of()) == null,
                "a piece cannot have been struck fewer than no blows");
    }

    private static void aWindowOfTheWrongShapeIsRefused() {
        // The window has to be exactly as long as the piece has been struck: TFC reads the newest three blows
        // back, so a piece struck three times and read back with one would be matched against the wrong end
        // of the sequence.
        assertTrue(ForgeMemory.remaining(SEQUENCE, 3, work(3), List.of(ForgeStep.SHRINK)) == null,
                "a piece struck three blows in is read back as three");
        assertTrue(ForgeMemory.remaining(SEQUENCE, 1, work(1),
                List.of(ForgeStep.UPSET, ForgeStep.SHRINK)) == null,
                "a piece struck once is read back as one");
        // And the blows it is read back with have to be the sequence's own, newest first.
        assertTrue(ForgeMemory.remaining(SEQUENCE, 3, work(3),
                List.of(ForgeStep.SHRINK, ForgeStep.HIT_LIGHT, ForgeStep.SHRINK)) == null,
                "a piece whose last blows are not the sequence's own is refused");
    }

    private static void thereIsNothingLeftOnceTheSequenceIsSpent() {
        assertTrue(ForgeMemory.remaining(SEQUENCE, SEQUENCE.size(), work(SEQUENCE.size()),
                window(SEQUENCE.size())) == null,
                "a piece struck through the whole sequence has nothing left to strike");
    }

    private static void theRemainderHandedBackIsItsOwn() {
        List<ForgeStep> left = ForgeMemory.remaining(SEQUENCE, 2, work(2), window(2));
        boolean refused = false;
        try {
            left.add(ForgeStep.DRAW);
        } catch (UnsupportedOperationException e) {
            refused = true;
        }
        assertTrue(refused, "the blows handed back cannot be added to");
        assertEquals(SEQUENCE.size(), left.size() + 2, "and what is left of the sequence is still the point");
    }

    /** What the first this many blows of the sequence leave the work at. */
    private static int work(int struck) {
        int sum = 0;
        for (int i = 0; i < struck; i++) {
            sum += SEQUENCE.get(i).step();
        }
        return sum;
    }

    /** The blows TFC would read back from a piece this many blows in: the newest three, oldest first. */
    private static List<ForgeStep> window(int struck) {
        List<ForgeStep> window = new ArrayList<>(Math.min(3, struck));
        for (int i = Math.max(0, struck - 3); i < struck; i++) {
            window.add(SEQUENCE.get(i));
        }
        return window;
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
