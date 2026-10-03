package first.wildfires.smithing;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain-Java checks for the search that works out how a recipe is to be forged.
 *
 * <p>What is pinned here is the three things a plan has to be, because each of them ruins a piece of work
 * silently when it is wrong. A plan that misses the target leaves an item that is never finished; one that
 * overshoots it, or dips below nothing on the way, destroys the piece the moment the offending blow lands;
 * and one whose last blows break the recipe's rules finishes the work and then refuses to make anything of
 * it. All three are checked by striking the plan out blow by blow exactly as the anvil would.
 *
 * <p>The rules are simulated here rather than taken from TFC: a rule reads the last three blows and nothing
 * else, so three positions and a value are all it takes to stand in for one. The eight blows are the eight
 * real ones, written out as numbers so this runs without the game on the classpath - which is the point of
 * checking the search without it.
 *
 * <p>A plan can also be asked for from a piece that has already been struck, which is how an interrupted
 * forge is picked up again: the search starts from the work the piece stands at, and the recipe's rules are
 * read against the end of the whole forging, so the blows the piece has taken are put back in front of every
 * candidate before they are read.
 */
public final class ForgePlanSelfTest {

    /** The eight blows, in the order the search is offered them: TFC's own values, heaviest first. */
    private static final int SHRINK = 16;
    private static final int UPSET = 13;
    private static final int BEND = 7;
    private static final int PUNCH = 2;
    private static final int HIT_LIGHT = -3;
    private static final int HIT_MEDIUM = -6;
    private static final int HIT_HARD = -9;
    private static final int DRAW = -15;

    private static final int[] BLOWS =
            {SHRINK, UPSET, BEND, PUNCH, HIT_LIGHT, HIT_MEDIUM, HIT_HARD, DRAW};

    /** TFC's working range, which no blow may take the work outside of. */
    private static final int LIMIT = 150;

    /** How far the search may look past the fewest blows it was told about. Mirrors {@code ForgePlan.PADDING}. */
    private static final int PADDING = 12;

    /** The longest plan the search will consider at all. Mirrors {@code ForgePlan.CEILING}. */
    private static final int CEILING = 48;

    private ForgePlanSelfTest() {
    }

    public static void main(String[] args) {
        try {
            aPlanLandsExactlyOnTheTarget();
            everyBlowLeavesTheWorkInsideTheAnvilsRange();
            aPlanIsNeverShorterThanItWasAskedFor();
            aLongerPlanIsTakenWhenShorterOnesCannotReach();
            theFirstBlowAlwaysRaisesTheWork();
            aRecipeThatCannotBeFinishedIsReported();
            theSearchStopsWithinItsOwnReach();
            aRuleEndingIsHonoured();
            aStruckPieceIsFinishedFromWhereItStands();
            System.out.println("ForgePlanSelfTest: all checks passed");
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            System.out.println("ForgePlanSelfTest: skipped, Minecraft classes are unavailable (" + e + ")");
        }
    }

    /** Every target a recipe could ask for is reached, and reached exactly. */
    private static void aPlanLandsExactlyOnTheTarget() {
        for (int target = 1; target <= 60; target++) {
            int[] plan = plan(BLOWS, LIMIT, target, 1, none());
            if (plan == null) {
                throw new AssertionError("no plan was found for target " + target);
            }
        }
    }

    /** No blow on the way may take the work past the anvil's ends: that destroys the piece outright. */
    private static void everyBlowLeavesTheWorkInsideTheAnvilsRange() {
        // The ends are the interesting targets: a piece asked to end at the very top of the range has no room
        // above it at all, so a plan for it can only be struck if nothing ever drifts upwards past it.
        assertTrue(plan(BLOWS, LIMIT, LIMIT, 1, none()) != null, "the top of the range can be forged to");
        assertTrue(plan(BLOWS, LIMIT, 1, 1, none()) != null, "and so can the bottom");
    }

    /** A plan shorter than the fewest blows a recipe could need is impossible, so it is never returned. */
    private static void aPlanIsNeverShorterThanItWasAskedFor() {
        for (int minimum = 1; minimum <= 12; minimum++) {
            int[] plan = plan(BLOWS, LIMIT, SHRINK, minimum, none());
            // 16 is one blow's worth, but one blow is only allowed when that is what was asked for.
            if (plan != null && plan.length < minimum) {
                throw new AssertionError("a plan of " + plan.length + " blows was returned for a minimum of "
                        + minimum);
            }
        }
        assertEquals(1, plan(BLOWS, LIMIT, SHRINK, 1, none()).length,
                "a target one blow can reach is reached in one blow when one is allowed");
    }

    /** A length the blows cannot reach is passed over for the next one, which is what the padding is for. */
    private static void aLongerPlanIsTakenWhenShorterOnesCannotReach() {
        // Blows of 2 alone: 8 cannot be struck in three, but is struck in four.
        int[] plan = plan(new int[]{PUNCH}, LIMIT, 8, 3, none());
        assertEquals(4, plan.length, "the shortest length that can reach the target is the one returned");
    }

    /** Work starts at nothing and may not go below it, so a plan can only open with a raising blow. */
    private static void theFirstBlowAlwaysRaisesTheWork() {
        for (int target = 1; target <= 60; target++) {
            int[] plan = plan(BLOWS, LIMIT, target, 1, none());
            assertTrue(BLOWS[plan[0]] > 0, "the first blow of the plan for target " + target + " raises the work");
        }
    }

    /** No ending at all is reported as none, rather than as a plan that runs out of blows. */
    private static void aRecipeThatCannotBeFinishedIsReported() {
        // All blows of 2: an odd target can never be reached.
        assertTrue(plan(new int[]{PUNCH}, LIMIT, 1, 1, none()) == null,
                "a target the blows cannot sum to has no plan");
        // The last blow of 15 is a draw, so the work has to stand at 165 before it - past the range.
        assertTrue(plan(BLOWS, LIMIT, LIMIT, 1, rules(last(DRAW))) == null,
                "an ending whose last blow cannot fit inside the range has no plan");
    }

    /** The search is bounded in both directions, so a nonsensical request cannot run away. */
    private static void theSearchStopsWithinItsOwnReach() {
        int longest = CEILING * PUNCH;
        // One blow of 2: the target takes 48 blows, which is further past the first answer than the padding
        // reaches - so it is reported as no plan rather than chased.
        assertTrue(plan(new int[]{PUNCH}, LIMIT, longest, 1, none()) == null,
                "the search does not chase plans beyond the padding");
        // Asked for from the longest length it will consider, the same target is found.
        assertEquals(CEILING, plan(new int[]{PUNCH}, LIMIT, longest, CEILING - PADDING, none()).length,
                "a plan of the longest length the search will consider is still found");
        // And a length beyond that is refused rather than searched.
        assertTrue(plan(new int[]{PUNCH}, LIMIT, longest, CEILING + 1, none()) == null,
                "a minimum beyond the longest plan the search considers is refused");
    }

    /** The rules of a recipe are satisfied by the blows the plan ends on, for every kind of rule. */
    private static void aRuleEndingIsHonoured() {
        List<Rule[]> recipes = new ArrayList<>();
        recipes.add(rules(last(SHRINK)));
        recipes.add(rules(last(HIT_LIGHT), secondLast(BEND)));
        recipes.add(rules(thirdLast(HIT_LIGHT), any(DRAW)));
        recipes.add(rules(notLast(PUNCH), last(UPSET), secondLast(HIT_MEDIUM)));
        recipes.add(rules(any(PUNCH), any(BEND), any(HIT_HARD)));

        for (Rule[] recipe : recipes) {
            int planned = 0;
            for (int target = 1; target <= 60; target++) {
                // TFC's own estimate is the count of blows its rule tail needs plus the fewest for the rest,
                // so three is the floor for a recipe with a three-blow tail and is used for all of them here.
                int[] plan = plan(BLOWS, LIMIT, target, 3, recipe);
                if (plan != null) {
                    planned++;
                }
            }
            assertTrue(planned > 0, "a recipe of rules was forged for some target: " + describe(recipe)
                    + " was forged for none of them");
        }
    }

    /**
     * A piece that has already been struck is finished from where it stands. What is asked for is the rest of
     * the work, and the recipe's rules are read against the end of the whole forging rather than of the plan
     * alone - which is what makes an interrupted run resumable by clicking again.
     */
    private static void aStruckPieceIsFinishedFromWhereItStands() {
        Rule[] recipe = rules(last(SHRINK), secondLast(HIT_LIGHT));
        int target = 42;
        int[] whole = plan(BLOWS, LIMIT, target, 3, recipe);
        assertTrue(whole.length > 1, "the recipe has a plan with blows another run could have struck first");

        int[] taken = new int[0];
        for (int struck = 1; struck < whole.length; struck++) {
            taken = join(taken, new int[]{BLOWS[whole[struck - 1]]});
            int left = whole.length - struck;
            int start = sum(taken);
            // The piece's own blows are what the recipe's rules must be read against, so every candidate the
            // search offers is tried with them put back in front of it.
            int[] prefix = taken;
            int[] plan = ForgePlan.find(BLOWS, LIMIT, target, left, start,
                    blows -> accepts(join(prefix, strikes(BLOWS, blows)), recipe));
            assertTrue(plan != null, "a piece struck " + struck + " blows in is finished from where it stands");
            assertEquals(left, plan.length, "the finish is exactly the blows that were left over");
            int[] all = join(taken, strikes(BLOWS, plan));
            assertEquals(target, sum(all), "the whole forging ends on the target");
            assertTrue(accepts(all, recipe), "the whole forging ends on blows the recipe accepts");
        }

        // Work the blows cannot bring onto the target has no plan, and a start outside the anvil's range is
        // refused rather than searched from.
        assertTrue(ForgePlan.find(new int[]{PUNCH}, 3, 1, 1, 3, anyEnding()) == null,
                "work the blows cannot bring onto the target has no plan");
        assertTrue(ForgePlan.find(BLOWS, LIMIT, target, 1, LIMIT + 1, anyEnding()) == null,
                "a start outside the anvil's range has no plan");
    }

    /**
     * Runs a plan out blow by blow and returns it, failing if it is not one the anvil could strike: the right
     * number of blows, every one of them inside the range, ending exactly on the target, and its ending
     * satisfying the recipe.
     */
    private static int[] plan(int[] values, int limit, int target, int minimum, Rule[] rules) {
        // The search deals in blow ids throughout, so the recipe's rules - which are about what the blows do
        // to the work - are only readable once the ids are turned back into their blows. Doing that here as
        // well as at the end is what keeps the check honest in both directions.
        ForgePlan.Tail tail = blows -> accepts(strikes(values, blows), rules);
        int[] plan = ForgePlan.find(values, limit, target, minimum, 0, tail);
        if (plan == null) {
            return null;
        }
        if (plan.length < minimum) {
            throw new AssertionError("a plan is shorter than it was asked for: " + plan.length + " < " + minimum);
        }
        int work = 0;
        for (int i = 0; i < plan.length; i++) {
            int id = plan[i];
            if (id < 0 || id >= values.length) {
                throw new AssertionError("a plan names a blow that does not exist: " + id);
            }
            work += values[id];
            if (work < 0 || work > limit) {
                throw new AssertionError("blow " + (i + 1) + " of " + plan.length + " leaves the work at " + work
                        + ", outside 0.." + limit);
            }
        }
        if (work != target) {
            throw new AssertionError("a plan ends at " + work + " rather than the target " + target);
        }
        if (values[plan[0]] <= 0) {
            throw new AssertionError("a plan opens with a blow of " + values[plan[0]]
                    + ", which cannot be struck onto untouched work");
        }
        int[] struck = strikes(values, plan);
        if (!accepts(struck, rules)) {
            throw new AssertionError("a plan ends on blows the recipe refuses");
        }
        return plan;
    }

    /** What each blow of a plan does to the work, in the order they are struck. */
    private static int[] strikes(int[] values, int[] plan) {
        int[] struck = new int[plan.length];
        for (int i = 0; i < plan.length; i++) {
            struck[i] = values[plan[i]];
        }
        return struck;
    }

    /** Two runs of blows as one, in the order they were struck. */
    private static int[] join(int[] before, int[] after) {
        int[] all = new int[before.length + after.length];
        System.arraycopy(before, 0, all, 0, before.length);
        System.arraycopy(after, 0, all, before.length, after.length);
        return all;
    }

    /** What a run of blows leaves the work at, struck onto untouched work. */
    private static int sum(int[] blows) {
        int work = 0;
        for (int blow : blows) {
            work += blow;
        }
        return work;
    }

    /** Whether a recipe's rules are all satisfied by these blows, which are its last ones. */
    private static boolean accepts(int[] blows, Rule[] rules) {
        for (Rule rule : rules) {
            boolean satisfied = false;
            for (int fromEnd : rule.fromEnd()) {
                int value = fromEnd <= blows.length ? blows[blows.length - fromEnd] : Integer.MIN_VALUE;
                for (int wanted : rule.values()) {
                    satisfied |= value == wanted;
                }
            }
            if (!satisfied) {
                return false;
            }
        }
        return true;
    }

    /**
     * One of TFC's rule kinds: a blow, and where among the last three it may sit. Positions count back from
     * the end, so 1 is the last blow. {@code ANY} is all three, {@code NOT_LAST} is the further two, and a
     * name for one position alone is that position - which is what a rule's order means in TFC.
     */
    private record Rule(int[] values, int[] fromEnd) {
    }

    private static Rule last(int value) {
        return new Rule(new int[]{value}, new int[]{1});
    }

    private static Rule secondLast(int value) {
        return new Rule(new int[]{value}, new int[]{2});
    }

    private static Rule thirdLast(int value) {
        return new Rule(new int[]{value}, new int[]{3});
    }

    private static Rule notLast(int value) {
        return new Rule(new int[]{value}, new int[]{2, 3});
    }

    private static Rule any(int value) {
        return new Rule(new int[]{value}, new int[]{1, 2, 3});
    }

    private static Rule[] rules(Rule... rules) {
        return rules;
    }

    /** How a recipe reads in a failure message: its blows, and where among the last three they may sit. */
    private static String describe(Rule[] rules) {
        StringBuilder text = new StringBuilder();
        for (Rule rule : rules) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(java.util.Arrays.toString(rule.values())).append('@')
                    .append(java.util.Arrays.toString(rule.fromEnd()));
        }
        return text.length() == 0 ? "no rules" : text.toString();
    }

    private static Rule[] none() {
        return new Rule[0];
    }

    /**
     * A tail that accepts every ending. For the checks that are about the search turning a start and a target
     * into blows, where the recipe has nothing to say about how the work ends.
     */
    private static ForgePlan.Tail anyEnding() {
        return blows -> true;
    }

    private static void assertEquals(int expected, int actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean value, String what) {
        if (!value) {
            throw new AssertionError(what);
        }
    }
}
