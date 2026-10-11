package first.wildfires.tfc.interaction;

import first.wildfires.compat.tfc.AnvilInsertRules;
import first.wildfires.compat.tfc.AnvilInsertRules.Decision;

/**
 * Plain-Java checks for the rule that keeps the tongs out of an anvil.
 *
 * <p>Two things are pinned here. The first is the shape of the answer. There are three of them rather than
 * two because the refusal has to say which hand the click is left to, and the difference between the two
 * refusals is the difference between a fixed accident and the same accident moved one hand over: a refusal
 * that always let the click fall through would, with an empty other hand, land on TFC's empty-handed take
 * and put a hot workpiece into a bare hand. So the pair of them is written out twice, once with something
 * in the other hand and once without.
 *
 * <p>The second is that the rule stays out of everything else. Sneaking with anything but tongs, clicking
 * without sneaking, clicking tongs at a block that is not an anvil, and a hammer that happens to also
 * carry the tongs tag all have to come back as {@code ALLOW} - that is the weld, the take-out, and the
 * anvil screen, and every one of them is a gesture the player still needs. The truth table at the end
 * walks all thirty-two combinations of the five booleans rather than the handful of interesting ones, so
 * that a later change to the rule has to argue with the whole table.
 *
 * <p>What is left to the in-game pass is the wiring: which block is an anvil on this side, which stack
 * carries the pack's tongs tag, and which hand the click was made with. Those are five plain booleans
 * here.
 */
public final class AnvilInsertRulesSelfTest {

    private AnvilInsertRulesSelfTest() {
    }

    public static void main(String[] args) {
        aPlainClickIsLeftAlone();
        aClickThatCarriesSomethingElseIsLeftAlone();
        theTongsStepAsideForTheOtherHand();
        theTongsAreNeverPutInWhenNothingElseCanBe();
        aHammerKeepsItsWeld();
        theReportedAccidentStopsAtTheTongs();
        everyConditionIsNeeded();
        System.out.println("AnvilInsertRulesSelfTest: all checks passed");
    }

    private static void aPlainClickIsLeftAlone() {
        assertEquals(Decision.ALLOW, decide(false, true, true, false, true),
                "a plain click on an anvil opens the anvil screen, and TFC inserts nothing by it");
        assertEquals(Decision.ALLOW, decide(false, true, true, false, false),
                "and it is left alone with tongs in either hand and a full other hand alike");
    }

    private static void aClickThatCarriesSomethingElseIsLeftAlone() {
        assertEquals(Decision.ALLOW, decide(true, true, false, false, true),
                "a workpiece in the clicking hand is inserted exactly as TFC has always inserted it");
        assertEquals(Decision.ALLOW, decide(true, true, false, false, false),
                "and a workpiece is still inserted when the other hand holds the tongs, since it is the "
                        + "hand that came first that is asked first");
        assertEquals(Decision.ALLOW, decide(true, false, true, false, true),
                "tongs clicked at anything that is not an anvil are nobody's business but TFC's");
    }

    private static void theTongsStepAsideForTheOtherHand() {
        assertEquals(Decision.TRY_OTHER_HAND, decide(true, true, true, false, false),
                "tongs with something in the other hand are refused in a way that asks the other hand");
    }

    private static void theTongsAreNeverPutInWhenNothingElseCanBe() {
        assertEquals(Decision.REFUSE, decide(true, true, true, false, true),
                "tongs with nothing in the other hand are refused outright, because the click falling "
                        + "through to an empty hand is TFC's take-out and would land on bare skin");
    }

    private static void aHammerKeepsItsWeld() {
        assertEquals(Decision.ALLOW, decide(true, true, true, true, true),
                "a stack that carries both tags still welds, because TFC welds before it would insert");
        assertEquals(Decision.ALLOW, decide(true, true, true, true, false),
                "and that does not change with the other hand either");
    }

    /**
     * The reported accident, in the order the two hands are actually asked.
     *
     * <p>The hammer is in the main hand and the tongs in the off hand, and the anvil is empty after a weld
     * has just been finished. The main hand weld returns PASS, so vanilla asks the off hand - the tongs -
     * and before this rule the tongs were inserted. Now the off hand is the one that is refused, and since
     * the main hand is holding the hammer the refusal is {@code TRY_OTHER_HAND}: the answer the tongs give
     * is the same PASS the weld gave, both hands have been asked, and the click ends with nothing inserted.
     */
    private static void theReportedAccidentStopsAtTheTongs() {
        final boolean sneaking = true;
        final boolean anvil = true;
        final boolean tongsInClickedHand = true;
        final boolean hammerInClickedHand = false;
        final boolean emptyOtherHand = false;

        assertEquals(Decision.TRY_OTHER_HAND,
                decide(sneaking, anvil, tongsInClickedHand, hammerInClickedHand, emptyOtherHand),
                "the off hand holding the tongs is refused, and the tongs stay in the hand");

        assertEquals(Decision.REFUSE, decide(true, true, true, false, true),
                "and with the other hand empty the same click would have reached the take-out, which is "
                        + "why that case spends the click instead of handing it on");
    }

    /**
     * Every one of the thirty-two combinations, against the rule read straight off its own prose: the rule
     * is about a sneak click on an anvil made with tongs that are not also a hammer, and the answer for it
     * depends only on whether the other hand holds anything.
     */
    private static void everyConditionIsNeeded() {
        for (int bits = 0; bits < 32; bits++) {
            final boolean sneaking = (bits & 1) != 0;
            final boolean anvil = (bits & 2) != 0;
            final boolean tongs = (bits & 4) != 0;
            final boolean hammer = (bits & 8) != 0;
            final boolean emptyOtherHand = (bits & 16) != 0;

            final boolean ours = sneaking && anvil && tongs && !hammer;
            final Decision expected = !ours ? Decision.ALLOW
                    : emptyOtherHand ? Decision.REFUSE : Decision.TRY_OTHER_HAND;

            assertEquals(expected, decide(sneaking, anvil, tongs, hammer, emptyOtherHand),
                    "combination " + bits + ": sneaking=" + sneaking + " anvil=" + anvil + " tongs=" + tongs
                            + " hammer=" + hammer + " emptyOtherHand=" + emptyOtherHand);
        }
    }

    private static Decision decide(boolean sneaking, boolean anvil, boolean tongs, boolean hammer,
            boolean emptyOtherHand) {
        return AnvilInsertRules.decide(sneaking, anvil, tongs, hammer, emptyOtherHand);
    }

    private static void assertEquals(Decision expected, Decision actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }
}
