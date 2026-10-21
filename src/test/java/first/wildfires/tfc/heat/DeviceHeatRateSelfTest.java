package first.wildfires.tfc.heat;

import first.wildfires.compat.tfc.DeviceHeatRate;

/**
 * Plain-Java checks for the arithmetic that turns "M times faster" into TFC's rate parameter.
 *
 * <p>What is pinned here is the property the whole feature rests on: the increment TFC adds per tick must
 * come out exactly M times the increment it would have added, for the forge's constant rate parameter and
 * for the crucible's temperature dependent one alike, and it must do so at TFC's shipped modifiers and at
 * any other pair a pack might configure. The failure this rules out is the obvious implementation -
 * multiplying the rate parameter by M - which looks right and is short by exactly the cooling term.
 *
 * <p>The numbers are TFC's own, from {@code HeatCapability.addTemp(IHeat, float, float)} at 3.2.20:
 * {@code increment = ((cooling - 1) + b * heating) / heatCapacity}, clamped to the temperature the device
 * hands over. The rate parameters are the ones the two call sites produce: the charcoal forge reaches the
 * three-argument overload through {@code addTemp(IHeat, float)}, whose own body is
 * {@code addTemp(heat, temperature, 3.0f)} - so {@code b = 3.0} lives inside the overload, not at the call
 * site - and the crucible passes {@code b = 2 + 0.0025 * temperature} directly.
 * {@code heatCapacity} cancels out of the comparison, so it is 1 here.
 */
public final class DeviceHeatRateSelfTest {

    /** Mirrors the shipped default of {@code TFCConfig.SERVER.itemHeatingModifier}. */
    private static final double HEATING = 1.0D;

    /** Mirrors the shipped default of {@code TFCConfig.SERVER.itemCoolingModifier}. */
    private static final double COOLING = 0.2D;

    /** {@code HeatCapability.addTemp(IHeat, float)} passes this on to the three-argument overload. */
    private static final double FORGE_BASE_RATE = DeviceHeatRate.TWO_ARGUMENT_RATE;

    /** The charcoal stove's factor from {@code wildfires-tfc-heat.toml}. */
    private static final double FORGE_FACTOR = 1.5D;

    /** TFC's crucible factor. */
    private static final double CRUCIBLE_FACTOR = 2.0D;

    /** The corundum crucible's factor. */
    private static final double CORUNDUM_CRUCIBLE_FACTOR = 2.5D;

    /** A temperature the crucible can actually sit at, in degrees. */
    private static final double CRUCIBLE_TEMPERATURE = 1000.0D;

    private static final double TOLERANCE = 1e-4D;

    private DeviceHeatRateSelfTest() {
    }

    public static void main(String[] args) {
        theForgeGainsExactlyHalfAgainAsMuch();
        theCrucibleGainsTwiceAsMuch();
        theCorundumCrucibleGainsTwoAndAHalfTimesAsMuch();
        theCrucibleRateIsRewrittenAtEveryTemperature();
        aFactorOfOneChangesNothing();
        theAnswerDoesNotDependOnTfcsConfiguredModifiers();
        aBrokenHeatingModifierLeavesTfcsOwnBehaviourAlone();
        System.out.println("DeviceHeatRateSelfTest: all checks passed");
    }

    private static void theForgeGainsExactlyHalfAgainAsMuch() {
        double scaled = DeviceHeatRate.scale(FORGE_BASE_RATE, HEATING, COOLING, FORGE_FACTOR);
        assertEquals(4.1D, scaled, "the forge's 3.0 becomes 4.1 at the shipped modifiers");
        assertEquals(FORGE_FACTOR * increment(FORGE_BASE_RATE, HEATING, COOLING),
                increment(scaled, HEATING, COOLING),
                "so the forge's increment per tick is exactly 1.5 times TFC's");
    }

    private static void theCrucibleGainsTwiceAsMuch() {
        double base = crucibleRate(CRUCIBLE_TEMPERATURE);
        double scaled = DeviceHeatRate.scale(base, HEATING, COOLING, CRUCIBLE_FACTOR);
        assertEquals(3.2D + 0.005D * CRUCIBLE_TEMPERATURE, scaled,
                "the crucible's 2 + 0.0025T becomes 3.2 + 0.005T at 1000 degrees");
        assertEquals(CRUCIBLE_FACTOR * increment(base, HEATING, COOLING),
                increment(scaled, HEATING, COOLING),
                "so the crucible's increment per tick is exactly twice TFC's");
    }

    private static void theCorundumCrucibleGainsTwoAndAHalfTimesAsMuch() {
        double base = crucibleRate(CRUCIBLE_TEMPERATURE);
        double scaled = DeviceHeatRate.scale(base, HEATING, COOLING, CORUNDUM_CRUCIBLE_FACTOR);
        assertEquals(3.8D + 0.00625D * CRUCIBLE_TEMPERATURE, scaled,
                "the corundum crucible's 2 + 0.0025T becomes 3.8 + 0.00625T at 1000 degrees");
        // The constant term is 3.8, not 4.5: at zero degrees the crucible's own rate parameter is 2.0, and
        // 2.5 * (1.2 + 0.0025 * 0) + 0.8 = 3.8. The 1000 degree value above is the same number with the
        // temperature term added, which is what makes the pair of assertions a real cross-check.
        assertEquals(3.8D, DeviceHeatRate.scale(crucibleRate(0.0D), HEATING, COOLING, CORUNDUM_CRUCIBLE_FACTOR),
                "and its constant term is 3.8 at zero degrees");
        assertEquals(3.8D + 0.00625D * CRUCIBLE_TEMPERATURE, scaled, "which the 1000 degree value agrees with");
        assertEquals(CORUNDUM_CRUCIBLE_FACTOR * increment(base, HEATING, COOLING),
                increment(scaled, HEATING, COOLING),
                "so the corundum crucible's increment per tick is exactly 2.5 times TFC's");
    }

    /** The crucible's rate parameter is a function of its temperature, so the property must hold at all of them. */
    private static void theCrucibleRateIsRewrittenAtEveryTemperature() {
        for (double temperature : new double[] {0.0D, 1.0D, 250.0D, 1500.0D, 2300.0D}) {
            double base = crucibleRate(temperature);
            double scaled = DeviceHeatRate.scale(base, HEATING, COOLING, CRUCIBLE_FACTOR);
            assertEquals(CRUCIBLE_FACTOR * increment(base, HEATING, COOLING),
                    increment(scaled, HEATING, COOLING),
                    "twice the increment at " + temperature + " degrees");
        }
    }

    private static void aFactorOfOneChangesNothing() {
        double scaled = DeviceHeatRate.scale(FORGE_BASE_RATE, HEATING, COOLING, 1.0D);
        assertEquals(FORGE_BASE_RATE, scaled, "a factor of 1.0 leaves the forge's rate parameter alone");
    }

    /**
     * The point of reading TFC's modifiers instead of hard-coding 0.2 and 1.0: a pack that changes them still
     * gets exactly M times the increment, rather than a number that was only right for the defaults.
     */
    private static void theAnswerDoesNotDependOnTfcsConfiguredModifiers() {
        double[][] configurations = {{1.0D, 0.2D}, {2.0D, 0.3D}, {0.5D, 1.0D}, {1.5D, 0.0D}};
        for (double[] configuration : configurations) {
            double heating = configuration[0];
            double cooling = configuration[1];
            for (double factor : new double[] {FORGE_FACTOR, CRUCIBLE_FACTOR, CORUNDUM_CRUCIBLE_FACTOR}) {
                double base = FORGE_BASE_RATE;
                double scaled = DeviceHeatRate.scale(base, heating, cooling, factor);
                assertEquals(factor * increment(base, heating, cooling),
                        increment(scaled, heating, cooling),
                        "heating=" + heating + " cooling=" + cooling + " factor=" + factor);
            }
        }
    }

    /**
     * A pack may write a heating modifier that makes the formula meaningless. The rate parameter is then left
     * untouched - TFC's own behaviour - because a NaN would be stored on the item's heat and never wash out.
     */
    private static void aBrokenHeatingModifierLeavesTfcsOwnBehaviourAlone() {
        assertEquals(FORGE_BASE_RATE, DeviceHeatRate.scale(FORGE_BASE_RATE, 0.0D, COOLING, FORGE_FACTOR),
                "a heating modifier of zero falls back to TFC's own rate parameter");
        assertEquals(FORGE_BASE_RATE, DeviceHeatRate.scale(FORGE_BASE_RATE, Double.NaN, COOLING, FORGE_FACTOR),
                "and a NaN heating modifier does too");
    }

    /**
     * The rate parameter the crucible computes three instructions before the call, in degrees. This is the
     * shape of the third argument TFC hands to {@code addTemp(IHeat, float, float)} from
     * {@code CrucibleBlockEntity.serverTick}, and it is the whole reason the crucible's factor cannot be a
     * plain multiplier on a constant.
     */
    private static double crucibleRate(double temperature) {
        return 2.0D + 0.0025D * temperature;
    }

    /** TFC's own arithmetic: {@code ((cooling - 1) + b * heating) / heatCapacity}, with heatCapacity 1. */
    private static double increment(double b, double heating, double cooling) {
        return (cooling - 1.0D + b * heating) / 1.0D;
    }

    private static void assertEquals(double expected, double actual, String what) {
        if (Math.abs(expected - actual) > TOLERANCE) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }
}
