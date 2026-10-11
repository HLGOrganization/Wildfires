package first.wildfires.vintageimprovements;

import first.wildfires.compat.vintageimprovements.CurvingPressRules;
import first.wildfires.compat.vintageimprovements.CurvingPressRules.ReportBudget;
import first.wildfires.compat.vintageimprovements.CurvingPressRules.Slot;

import java.util.function.IntFunction;

/**
 * Plain-Java checks for the rule that keeps the curving press from throwing on an ingredient that
 * lists no items, and for the budget that decides how often the guard is allowed to say so.
 *
 * <p>Three things are pinned here. The first is which slot the rule watches, because that is the whole
 * fix: Vintage Improvements reads {@code getItems()[0]} of the slot it pre-checked - slot 0 in mode 1
 * and slot 1 in mode 2 - and only that slot. If this rule watched a different slot it would refuse
 * recipes the press handles perfectly well, and if it watched no slot the crash would still be there.
 *
 * <p>The second is that nothing else can cause a refusal. Every broken slot, every mode and every
 * recipe length is walked below, and a refusal is only ever allowed for the watched slot of modes 1
 * and 2, or for a recipe too short to hold that slot - which is the same crash one instruction
 * earlier, at {@code canCurve}'s own {@code ings.get(mode - 1)}. Modes 3 and 4 check the array length
 * themselves, and an empty watched slot is answered false by {@code canCurve} before it reaches the
 * item list, so neither is ours to touch.
 *
 * <p>The third is the log budget. The refused call is a predicate that runs once per recipe per
 * lookup, so a repeated line is the difference between one diagnosable line and a log nobody can read;
 * the checks walk the first offer, the repeat, the limit and the announcement that follows it.
 *
 * <p>What is left to the in-game pass is the wiring: that the mixin applies to a real Vintage
 * Improvements install, and that the line names the recipe the player cannot find in JEI.
 */
public final class CurvingPressRulesSelfTest {

    private static final Slot HEALTHY = new Slot(false, 3);

    private static final Slot EMPTY = new Slot(true, 0);

    private static final Slot NO_ITEMS = new Slot(false, 0);

    private static final Slot UNREADABLE = new Slot(false, Slot.UNREADABLE);

    private CurvingPressRulesSelfTest() {
    }

    public static void main(String[] args) {
        theWatchedSlotFollowsTheMode();
        aWatchedSlotWithNoItemsIsRefused();
        anEmptyWatchedSlotIsTheMethodSOwnAnswer();
        aBrokenSlotElsewhereIsLeftAlone();
        aRecipeTooShortForTheSlotIsRefused();
        theModesThatCheckTheirOwnLengthAreLeftAlone();
        theBudgetNamesEachRecipeOnce();
        theBudgetAnnouncesTheLimitOnce();
        theBudgetTakesWhateverItIsGiven();
        System.out.println("CurvingPressRulesSelfTest: all checks passed");
    }

    private static void theWatchedSlotFollowsTheMode() {
        assertEquals(0, CurvingPressRules.watchedSlot(1),
                "mode 1 reads the even slots and starts at the pre-checked slot 0");
        assertEquals(1, CurvingPressRules.watchedSlot(2),
                "mode 2 reads the odd slots and starts at the pre-checked slot 1");
        assertEquals(CurvingPressRules.NO_SLOT, CurvingPressRules.watchedSlot(0),
                "mode 0 never reaches an ingredient");
        assertEquals(CurvingPressRules.NO_SLOT, CurvingPressRules.watchedSlot(3),
                "mode 3 checks the array length before it reads it");
        assertEquals(CurvingPressRules.NO_SLOT, CurvingPressRules.watchedSlot(4),
                "and so does mode 4");
        assertEquals(CurvingPressRules.NO_SLOT, CurvingPressRules.watchedSlot(-1),
                "and a mode that is not one of the four is answered false without an ingredient read");
    }

    private static void aWatchedSlotWithNoItemsIsRefused() {
        assertEquals(true, refuses(1, 9, only(0, NO_ITEMS)),
                "a mode 1 recipe whose first slot lists no items is the ArrayIndexOutOfBoundsException");
        assertEquals(true, refuses(2, 9, only(1, NO_ITEMS)),
                "and a mode 2 recipe whose second slot lists no items is the same read one slot over");
        assertEquals(true, refuses(1, 9, only(0, UNREADABLE)),
                "a watched slot that throws when it is asked for its items is refused too, since that"
                        + " throw would happen inside canCurve");
    }

    private static void anEmptyWatchedSlotIsTheMethodSOwnAnswer() {
        assertEquals(false, refuses(1, 9, only(0, EMPTY)),
                "an empty first slot makes canCurve answer false before it reads any item list");
        assertEquals(false, refuses(2, 9, only(1, EMPTY)),
                "and the same holds for the second slot in mode 2");
    }

    /**
     * Every mode, every recipe length from nothing to a full grid, and every single broken slot in
     * turn: the rule may only answer true for the watched slot of modes 1 and 2, or for a recipe too
     * short to hold it.
     */
    private static void aBrokenSlotElsewhereIsLeftAlone() {
        for (int mode = 0; mode <= 5; mode++) {
            final int watched = CurvingPressRules.watchedSlot(mode);
            for (int size = 0; size <= 9; size++) {
                for (int broken = 0; broken < 9; broken++) {
                    final boolean expected = watched != CurvingPressRules.NO_SLOT
                            && (watched >= size || broken == watched);
                    assertEquals(expected, refuses(mode, size, only(broken, NO_ITEMS)),
                            "mode " + mode + ", size " + size + ", slot " + broken + " without items");
                }
            }
        }
    }

    private static void aRecipeTooShortForTheSlotIsRefused() {
        assertEquals(true, refuses(1, 0, all(HEALTHY)),
                "canCurve asks for slot 0 before anything else, so a recipe with no ingredients throws"
                        + " there whatever they would have been");
        assertEquals(true, refuses(2, 1, all(HEALTHY)),
                "and mode 2 asks for slot 1, which a one-ingredient recipe does not have");
        assertEquals(false, refuses(2, 2, all(HEALTHY)),
                "while the shortest recipe that can hold the watched slot is none of our business");
    }

    private static void theModesThatCheckTheirOwnLengthAreLeftAlone() {
        assertEquals(false, refuses(3, 9, all(NO_ITEMS)),
                "mode 3 asks how long the item list is", "before it reads it");
        assertEquals(false, refuses(4, 9, all(NO_ITEMS)),
                "and mode 4 does the same, so a broken ingredient there is already survivable");
    }

    private static void theBudgetNamesEachRecipeOnce() {
        ReportBudget budget = new ReportBudget(3);
        assertEquals(true, budget.offer("a"), "the first offer of a recipe is logged");
        assertEquals(false, budget.offer("a"), "and the second is not, which is the point of the budget");
        assertEquals(true, budget.offer("b"), "a second recipe still gets its line");
        assertEquals(true, budget.offer("c"), "and so does a third, up to the limit");
        assertEquals(false, budget.offer("d"), "the fourth distinct recipe is past the limit of three");
        assertEquals(1, budget.suppressed(), "and is counted rather than logged");
        assertEquals(false, budget.offer("d"), "offering it again is still not a line");
        assertEquals(1, budget.suppressed(), "and is not counted twice, because the key is remembered");
        assertEquals(3, budget.reported(), "the budget reports the limit it spent");
    }

    private static void theBudgetAnnouncesTheLimitOnce() {
        ReportBudget budget = new ReportBudget(1);
        assertEquals(false, budget.limitJustReached(),
                "nothing has been dropped yet, so there is nothing to announce");
        budget.offer("a");
        assertEquals(false, budget.limitJustReached(),
                "and a budget that is still under its limit says nothing");
        budget.offer("b");
        assertEquals(true, budget.limitJustReached(),
                "the first recipe past the limit is the moment to say the guard has gone quiet");
        assertEquals(false, budget.limitJustReached(),
                "which is said once, not once per recipe");
        budget.offer("c");
        assertEquals(2, budget.suppressed(), "while every recipe after it is still counted");
    }

    private static void theBudgetTakesWhateverItIsGiven() {
        ReportBudget budget = new ReportBudget(0);
        assertEquals(true, budget.offer("a"), "a limit of zero is raised to one rather than silencing the guard");
        assertEquals(false, budget.offer("b"), "and then behaves like a limit of one");
        assertEquals(true, new ReportBudget(2).offer(null),
                "a recipe with no id of its own falls back to its class, so a null key is still a key");
    }

    private static boolean refuses(int mode, int size, IntFunction<Slot> slotAt) {
        return CurvingPressRules.refuses(mode, size, slotAt);
    }

    private static IntFunction<Slot> all(Slot view) {
        return slot -> view;
    }

    private static IntFunction<Slot> only(int broken, Slot view) {
        return slot -> slot == broken ? view : HEALTHY;
    }

    private static void assertEquals(boolean expected, boolean actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(boolean expected, boolean actual, String what, String and) {
        assertEquals(expected, actual, what + " " + and);
    }

    private static void assertEquals(int expected, int actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }
}
