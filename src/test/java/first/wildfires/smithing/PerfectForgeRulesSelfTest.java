package first.wildfires.smithing;

/**
 * Plain-Java checks for the two rules that decide when a one-click forge unlocks.
 *
 * <p>Both are read off the smith's "技巧" level in More Attributes, and both were tuned by hand, so what is
 * pinned here is the shape rather than the numbers: a forge pays for the quality TFC graded the result at
 * and for nothing else, the bar that experience fills is longer for a smith below
 * {@link PerfectForgeService#SHORTCUT_SKILL_LEVEL} by {@link PerfectForgeService#XP_PER_MISSING_SKILL_LEVEL}
 * a level, the odds of being handed the recipe outright are nothing at that level and climb by
 * {@link PerfectForgeService#UNLOCK_CHANCE_PER_SKILL_LEVEL} above it, and the level one rule stops at is the
 * level the other starts at.
 *
 * <p>Both rules take a skill level and nothing else, and are answered over a plain quality tier rather than
 * TFC's own enum - so this test runs in any JVM, and a change to either rule is visible here rather than
 * only as a slower or faster bar in game.
 */
public final class PerfectForgeRulesSelfTest {

    /** Mirrors {@code PerfectForgeService.XP_TO_UNLOCK}: the shortest bar there is. */
    private static final int XP_TO_UNLOCK = 10;

    /** The worst graded result TFC can stamp: one point, or {@code ForgingBonus.NONE.ordinal() + 1}. */
    private static final int WORST_QUALITY = 1;

    /** The best graded result TFC stamps today, {@code ForgingBonus.PERFECTLY_FORGED.ordinal() + 1}. */
    private static final int BEST_QUALITY = 5;

    private PerfectForgeRulesSelfTest() {
    }

    public static void main(String[] args) {
        aForgeIsWorthTheQualityOfItsResult();
        aRecipeWithoutQualityPaysAFlatPoint();
        theBarIsLongerForEveryLevelBelowTheMark();
        theBarStopsGrowingAtTheMark();
        aBeginnerForgesAgainstTheLongestBar();
        theOddsStartAtTheMarkAndClimbByFour();
        theOddsOnlyEverRiseWithSkill();
        theLongBarEndsWhereTheOddsStart();
        noSingleForgeFillsEvenTheShortestBar();
        System.out.println("PerfectForgeRulesSelfTest: all checks passed");
    }

    private static void aForgeIsWorthTheQualityOfItsResult() {
        for (int quality = WORST_QUALITY; quality <= BEST_QUALITY; quality++) {
            assertEquals(quality, PerfectForgeService.xpFor(quality), "a forge is worth its tier");
        }
    }

    private static void aRecipeWithoutQualityPaysAFlatPoint() {
        assertEquals(1, PerfectForgeService.xpFor(0), "a result with no quality to grade pays one point");
        assertEquals(1, PerfectForgeService.xpFor(-3), "and a nonsense tier cannot take points away");
    }

    private static void theBarIsLongerForEveryLevelBelowTheMark() {
        int mark = PerfectForgeService.SHORTCUT_SKILL_LEVEL;
        int perLevel = PerfectForgeService.XP_PER_MISSING_SKILL_LEVEL;
        for (int skill = 0; skill < mark; skill++) {
            assertEquals(XP_TO_UNLOCK + (mark - skill) * perLevel, PerfectForgeService.xpToUnlock(skill),
                    "the bar at skill " + skill + " is longer by every level short of " + mark);
        }

        // The figures that were asked for, spelled out: no skill at all costs thirty points, half way costs
        // twenty, and one level short costs a single step more than the shortest bar.
        assertEquals(30, PerfectForgeService.xpToUnlock(0), "a smith with no skill at all needs thirty points");
        assertEquals(20, PerfectForgeService.xpToUnlock(mark / 2), "half way to the mark needs twenty");
        assertEquals(XP_TO_UNLOCK + perLevel, PerfectForgeService.xpToUnlock(mark - 1),
                "one level short needs the shortest bar plus one step");
    }

    private static void theBarStopsGrowingAtTheMark() {
        int mark = PerfectForgeService.SHORTCUT_SKILL_LEVEL;
        for (int skill = mark; skill <= mark + 40; skill++) {
            assertEquals(XP_TO_UNLOCK, PerfectForgeService.xpToUnlock(skill),
                    "a smith at or above skill " + skill + " forges against the shortest bar");
        }
    }

    private static void aBeginnerForgesAgainstTheLongestBar() {
        // What a longer bar means in forges, since that is what a player actually feels: twice the work of
        // the shortest bar at the very bottom and no more, measured by the best grade a forge can be paid.
        assertEquals(2, forgesToFill(PerfectForgeService.xpToUnlock(PerfectForgeService.SHORTCUT_SKILL_LEVEL)),
                "the shortest bar is two perfect forges");
        assertEquals(6, forgesToFill(PerfectForgeService.xpToUnlock(0)),
                "the longest bar is six perfect forges");
        assertEquals(30, PerfectForgeService.xpToUnlock(0) / PerfectForgeService.xpFor(0),
                "or thirty forges of a recipe with no quality to grade");
        assertEquals(10, PerfectForgeService.xpToUnlock(PerfectForgeService.SHORTCUT_SKILL_LEVEL)
                        / PerfectForgeService.xpFor(0),
                "where the shortest bar is ten of them");
    }

    private static void theOddsStartAtTheMarkAndClimbByFour() {
        int mark = PerfectForgeService.SHORTCUT_SKILL_LEVEL;
        double perLevel = PerfectForgeService.UNLOCK_CHANCE_PER_SKILL_LEVEL;
        assertEquals(0D, PerfectForgeService.unlockChance(mark), "the odds are nothing at the mark");
        for (int skill = 0; skill < mark; skill++) {
            assertTrue(PerfectForgeService.unlockChance(skill) <= 0D,
                    "a smith below " + mark + " cannot be handed a recipe (skill " + skill + ")");
        }
        assertEquals(4 * perLevel, PerfectForgeService.unlockChance(mark + 4),
                "four levels above the mark is four steps of odds");
        assertEquals(1D, PerfectForgeService.unlockChance(mark + 25),
                "a smith far enough above the mark is certain to be handed the recipe");
    }

    private static void theOddsOnlyEverRiseWithSkill() {
        int mark = PerfectForgeService.SHORTCUT_SKILL_LEVEL;
        double perLevel = PerfectForgeService.UNLOCK_CHANCE_PER_SKILL_LEVEL;
        for (int skill = -5; skill < mark + 40; skill++) {
            assertEquals(PerfectForgeService.unlockChance(skill) + perLevel,
                    PerfectForgeService.unlockChance(skill + 1),
                    "one skill level is one step of odds (skill " + skill + ")");
        }
    }

    private static void theLongBarEndsWhereTheOddsStart() {
        int mark = PerfectForgeService.SHORTCUT_SKILL_LEVEL;
        assertEquals(PerfectForgeService.xpToUnlock(mark), PerfectForgeService.xpToUnlock(mark + 1),
                "the mark is where the bar stops growing");
        assertEquals(XP_TO_UNLOCK + PerfectForgeService.XP_PER_MISSING_SKILL_LEVEL,
                PerfectForgeService.xpToUnlock(mark - 1),
                "and the level before it is the last one asked for a longer bar");
        assertTrue(PerfectForgeService.unlockChance(mark) == 0D
                        && PerfectForgeService.unlockChance(mark + 1) > 0D,
                "which is the level the odds start at");
    }

    private static void noSingleForgeFillsEvenTheShortestBar() {
        // The shape the longer bar bought. A level-zero smith used to be paid more for one forge than the
        // whole bar cost, so the very first thing they forged had its one-click forge unlocked; the bar is
        // longer rather than the pay now, so there is no level at which a single forge is enough.
        int mark = PerfectForgeService.SHORTCUT_SKILL_LEVEL;
        for (int skill = 0; skill <= mark + 40; skill++) {
            assertTrue(PerfectForgeService.xpFor(BEST_QUALITY) < PerfectForgeService.xpToUnlock(skill),
                    "one forge never fills the bar on its own (skill " + skill + ")");
        }
    }

    /** How many forges of the best grade it takes to reach a bar. */
    private static int forgesToFill(int bar) {
        return (bar + BEST_QUALITY - 1) / BEST_QUALITY;
    }

    private static void assertEquals(int expected, int actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(double expected, double actual, String what) {
        if (Math.abs(expected - actual) > 1.0E-9D) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertTrue(boolean holds, String what) {
        if (!holds) {
            throw new AssertionError(what);
        }
    }
}
