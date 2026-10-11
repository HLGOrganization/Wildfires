package first.wildfires.tfc.heat;

import first.wildfires.compat.tfc.SmolderingRules;

/**
 * Plain-Java checks for the one rule that decides when a hot but extinguished device catches again.
 *
 * <p>What is pinned here is the boundary, because the boundary is the whole feature: a device that has gone
 * cold must stay cold until somebody lights it, and a device that has one degree left must catch. The two
 * spellings this rules out are the ones that would be easy to write by accident - {@code >= 0f}, which would
 * leave every device in the world relighting itself forever once it had cooled, and a threshold above the
 * first degree, which would silently shorten the window nobody has a number for.
 *
 * <p>The length of the window is pinned as well, against TFC's cooling arithmetic rather than against a
 * guess: {@code HeatCapability.adjustTempTowards} moves a device towards its target by
 * {@code TFCConfig.SERVER.deviceHeatingModifier} per tick and stops exactly on it, and the target of a
 * device with no fuel burning is zero. At the shipped modifier of 1.0 that makes the window exactly as many
 * ticks long as the temperature it starts at - half a minute for a firepit that went out at 600 degrees,
 * two minutes for a forge that went out at 2300 - and twice that while a bellows is working the device,
 * because air halves the cooling rate.
 *
 * <p>The rule takes three plain values and answers with a boolean, so this runs in any JVM. Which device
 * supplies which flags is wiring, and wiring is what the in-game pass is for.
 */
public final class SmolderingRulesSelfTest {

    /** Mirrors the shipped default of {@code TFCConfig.SERVER.deviceHeatingModifier}. */
    private static final float COOLING_PER_TICK = 1f;

    /** The cooling rate while air is being pushed into the device: {@code deltaNegative} with air ticks. */
    private static final float COOLING_PER_TICK_WITH_BELLOWS = 0.5f;

    /** What a firepit that burned down to nothing is left at, in degrees. */
    private static final float FIREPIT_AFTER_EXTINGUISH = 600f;

    /** What a charcoal forge that burned down to nothing is left at, in degrees. */
    private static final float FORGE_AFTER_EXTINGUISH = 2300f;

    private SmolderingRulesSelfTest() {
    }

    public static void main(String[] args) {
        aColdDeviceStaysCold();
        exactlyZeroIsColdButTheFirstDegreeIsNot();
        aHotDeviceThatWentOutCatchesAgain();
        aDeviceThatIsAlreadyBurningIsLeftAlone();
        aDeviceThatCannotStayLitIsLeftAlone();
        theWindowIsAsLongAsTheTemperature();
        aBellowsDoublesTheWindow();
        System.out.println("SmolderingRulesSelfTest: all checks passed");
    }

    private static void aColdDeviceStaysCold() {
        assertFalse(relights(false, 0f, true), "a device at zero degrees is cold, not smoldering");
        assertFalse(relights(false, -1f, true), "and nothing below zero is heat either");
        assertFalse(relights(false, -300f, true), "not even a device left out in the rain below freezing");
    }

    private static void exactlyZeroIsColdButTheFirstDegreeIsNot() {
        assertFalse(relights(false, 0f, true), "the rule is before zero, not at zero");
        assertTrue(relights(false, 1f, true), "one degree left is still a device that can catch");
        assertTrue(relights(false, 0.5f, true), "even half a degree, which a bellows can leave behind");
        assertTrue(relights(false, Float.MIN_VALUE, true), "and any heat at all above zero counts");
    }

    private static void aHotDeviceThatWentOutCatchesAgain() {
        assertTrue(relights(false, FIREPIT_AFTER_EXTINGUISH, true), "a firepit that just went out catches");
        assertTrue(relights(false, FORGE_AFTER_EXTINGUISH, true), "so does a forge that just went out");
    }

    private static void aDeviceThatIsAlreadyBurningIsLeftAlone() {
        assertFalse(relights(true, FIREPIT_AFTER_EXTINGUISH, true), "a burning firepit needs nothing");
        assertFalse(relights(true, FORGE_AFTER_EXTINGUISH, true), "and neither does a burning forge");
        assertFalse(relights(true, 0f, true), "a device that is lit while cold is TFC's business, not ours");
    }

    private static void aDeviceThatCannotStayLitIsLeftAlone() {
        assertFalse(relights(false, FIREPIT_AFTER_EXTINGUISH, false),
                "a forge that no longer passes its multiblock check must not be fed fuel");
        assertFalse(relights(false, 0f, false), "and a cold one of those is doubly out of the question");
    }

    private static void theWindowIsAsLongAsTheTemperature() {
        assertEquals((int) FIREPIT_AFTER_EXTINGUISH, ticksOfSmoldering(FIREPIT_AFTER_EXTINGUISH, COOLING_PER_TICK),
                "a firepit that went out at 600 degrees smolders for 600 ticks");
        assertEquals((int) FORGE_AFTER_EXTINGUISH, ticksOfSmoldering(FORGE_AFTER_EXTINGUISH, COOLING_PER_TICK),
                "a forge that went out at 2300 degrees smolders for 2300 ticks");

        // Spelled out in seconds, which is what a player actually experiences.
        assertEquals(30, ticksOfSmoldering(FIREPIT_AFTER_EXTINGUISH, COOLING_PER_TICK) / 20,
                "half a minute for a firepit");
        assertEquals(115, ticksOfSmoldering(FORGE_AFTER_EXTINGUISH, COOLING_PER_TICK) / 20,
                "and just under two minutes for a forge");

        // A cold device has nothing left to give: the window is finite and it ends.
        assertEquals(0, ticksOfSmoldering(0f, COOLING_PER_TICK), "a device at zero degrees has no window left");
        assertEquals(1, ticksOfSmoldering(1f, COOLING_PER_TICK), "the last degree is the last tick");
    }

    private static void aBellowsDoublesTheWindow() {
        assertEquals(2 * ticksOfSmoldering(FIREPIT_AFTER_EXTINGUISH, COOLING_PER_TICK),
                ticksOfSmoldering(FIREPIT_AFTER_EXTINGUISH, COOLING_PER_TICK_WITH_BELLOWS),
                "air halves the cooling rate, so it doubles the time a device stays catchable");
    }

    /**
     * TFC's cooling, tick by tick: step towards a target of zero and land on it exactly. Counts how many
     * ticks a device at {@code temperature} is still able to catch again.
     */
    private static int ticksOfSmoldering(float temperature, float coolingPerTick) {
        int ticks = 0;
        float current = temperature;
        while (relights(false, current, true)) {
            current = Math.max(current - coolingPerTick, 0f);
            ticks++;
        }
        return ticks;
    }

    private static boolean relights(boolean lit, float temperature, boolean deviceCanStayLit) {
        return SmolderingRules.shouldRelight(lit, temperature, deviceCanStayLit);
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
