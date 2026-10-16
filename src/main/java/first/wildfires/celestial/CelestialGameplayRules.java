/*
 * Adapted from TFCCaelum 1.2 `tfccaelum/Helpers`, `tfccaelum/mixin/LocalMobCapCalculatorMixin`
 * and `tfccaelum/mixin/MonsterMixin`.
 * SPDX-License-Identifier: LicenseRef-TFCCaelum-BSD (see third_party/tfc-caelum/1.2/PROVENANCE.md).
 * Wildfires modifications: the retained blood-moon gameplay effects (Unluck amplifier, finite
 * intensity-scaled local mob cap, visibility gating) are collected into pure deterministic rules
 * driven by the unified celestial state.
 */
package first.wildfires.celestial;

import first.wildfires.api.celestial.CelestialState;

/** Pure, deterministic rules shared by blood-moon gameplay and regression tests. */
public final class CelestialGameplayRules {

    /** Blood-moon gameplay requires strictly more than eighty percent square-disc coverage. */
    public static final double ACTIVE_THRESHOLD = CelestialState.BLOOD_MOON_COVERAGE_THRESHOLD;

    private CelestialGameplayRules() {
    }

    public static double visibleBloodMoon(double rawIntensity, double moonElevationRadians,
                                          double sunElevationRadians) {
        if (!Double.isFinite(rawIntensity) || !Double.isFinite(moonElevationRadians)
                || !Double.isFinite(sunElevationRadians) || moonElevationRadians <= 0.0D
                || sunElevationRadians > 0.0D || rawIntensity <= ACTIVE_THRESHOLD) {
            return 0.0D;
        }
        return clamp(rawIntensity, 0.0D, 1.0D);
    }

    public static int localMobCapLimit(int vanillaLimit, double intensity, double configuredMultiplier) {
        if (vanillaLimit <= 0) {
            return vanillaLimit;
        }
        double safeIntensity = clamp(intensity, 0.0D, 1.0D);
        double safeMultiplier = Math.max(1.0D, configuredMultiplier);
        double scale = 1.0D + safeIntensity * (safeMultiplier - 1.0D);
        return Math.max(vanillaLimit, (int) Math.ceil(vanillaLimit * scale));
    }

    public static int unluckAmplifier(double intensity) {
        return Math.max(0, (int) Math.round(2.0D * clamp(intensity, 0.0D, 1.0D)));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
