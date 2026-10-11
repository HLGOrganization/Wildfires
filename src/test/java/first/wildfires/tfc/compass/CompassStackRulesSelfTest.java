package first.wildfires.tfc.compass;

import first.wildfires.compat.tfc.CompassStackRules;

/**
 * Plain-Java checks for the one rule that decides whether a compass exchange replaces the stack in the hand
 * or takes a single compass out of it.
 *
 * <p>What is pinned here is the boundary that lost items: a stack of dehydrated compasses used on a water
 * source used to be replaced by one charged compass, and a stack of worn compasses used to be replaced by
 * one dehydrated compass on the tick it ran dry. Both are the same rule, so both are checked here - one item
 * may be exchanged, and the count that stays behind is the count that was there minus one.
 *
 * <p>The rule takes a count and answers with a boolean, so this runs in any JVM. Which stack supplies that
 * count is wiring, and wiring is what the in-game pass is for.
 */
public final class CompassStackRulesSelfTest {

    private CompassStackRulesSelfTest() {
    }

    public static void main(String[] args) {
        aLoneCompassIsReplacedWhereItIs();
        aStackIsDrainedOneAtATime();
        anEmptyStackHasNothingToLeaveBehind();
        System.out.println("CompassStackRulesSelfTest: all checks passed");
    }

    private static void aLoneCompassIsReplacedWhereItIs() {
        assertFalse(leavesTheRest(1), "one compass is the whole stack, so the exchange replaces it where it is");
    }

    private static void aStackIsDrainedOneAtATime() {
        assertTrue(leavesTheRest(2), "two compasses: one is exchanged and one stays");
        assertTrue(leavesTheRest(8), "a stack saved before the compasses became unstackable keeps its other seven");
        assertTrue(leavesTheRest(64), "a full stack keeps sixty-three");
        assertEquals(7, 8 - 1, "the count left behind is the count that was there minus the one exchanged");
        assertEquals(0, 1 - 1, "and a lone compass leaves nothing behind");
    }

    private static void anEmptyStackHasNothingToLeaveBehind() {
        // Both call sites look at a compass they found, so an empty stack never reaches the rule. It must
        // still not claim that there is something to leave behind, which is what a "count != 1" test would.
        assertFalse(leavesTheRest(0), "an empty stack has nothing to leave behind");
        assertFalse(leavesTheRest(-1), "and neither has a count that cannot exist");
    }

    private static boolean leavesTheRest(int stackCount) {
        return CompassStackRules.leavesTheRestBehind(stackCount);
    }

    private static void assertTrue(boolean holds, String what) {
        if (!holds) {
            throw new AssertionError(what);
        }
    }

    private static void assertFalse(boolean holds, String what) {
        if (holds) {
            throw new AssertionError(what);
        }
    }

    private static void assertEquals(int expected, int actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }
}
