/*
 * Adapted from TFCCaelum 1.2 `tfccaelum/Config` and `tfccaelum/Helpers`.
 * SPDX-License-Identifier: LicenseRef-TFCCaelum-BSD (see third_party/tfc-caelum/1.2/PROVENANCE.md).
 * Wildfires modifications: the `LEGACY_TFCCAELUM` preset reproduces the upstream mixed
 * orbit/supermoon period semantics, while the default preset uses the unified synodic,
 * anomalistic and nodal model.
 */
package first.wildfires.celestial;

/** Server-authoritative parameters used by the common celestial model. */
public record CelestialRuntimeSettings(double synodicDays,
                                       double anomalisticDays,
                                       double nodalYears,
                                       double lunarInclinationRadians,
                                       boolean bloodMoonSurfaceMonsters,
                                       double bloodMoonSpawnMultiplier,
                                       double sunScale,
                                       double moonScale,
                                       LunarPeriodPreset lunarPeriodPreset,
                                       CelestialPlanetSettings planetSettings,
                                       CelestialOrbitalPhases orbitalPhases) {

    private static final ThreadLocal<PreparedPeriodsCache> PREPARED_PERIODS =
            ThreadLocal.withInitial(PreparedPeriodsCache::new);

    public CelestialRuntimeSettings {
        if (lunarPeriodPreset == null || planetSettings == null || orbitalPhases == null) {
            throw new IllegalArgumentException("Celestial runtime settings cannot contain null values");
        }
        if (!Double.isFinite(sunScale) || sunScale <= 0.0D
                || !Double.isFinite(moonScale) || moonScale <= 0.0D) {
            throw new IllegalArgumentException("Authoritative Sun and Moon scales must be finite and positive");
        }
    }

    public CelestialRuntimeSettings(double synodicDays, double anomalisticDays, double nodalYears,
                                    double lunarInclinationRadians, boolean bloodMoonSurfaceMonsters,
                                    double bloodMoonSpawnMultiplier, double sunScale, double moonScale,
                                    LunarPeriodPreset lunarPeriodPreset,
                                    CelestialPlanetSettings planetSettings) {
        this(synodicDays, anomalisticDays, nodalYears, lunarInclinationRadians,
                bloodMoonSurfaceMonsters, bloodMoonSpawnMultiplier, sunScale, moonScale,
                lunarPeriodPreset, planetSettings, CelestialOrbitalPhases.ZERO);
    }

    public CelestialRuntimeSettings withOrbitalPhases(CelestialOrbitalPhases phases) {
        return new CelestialRuntimeSettings(synodicDays, anomalisticDays, nodalYears,
                lunarInclinationRadians, bloodMoonSurfaceMonsters, bloodMoonSpawnMultiplier,
                sunScale, moonScale, lunarPeriodPreset, planetSettings, phases);
    }

    public enum LunarPeriodPreset {
        /** The Wildfires model selected for new worlds. */
        UNIFIED_16_13,
        /** Reproduces TFCCaelum's mixed orbit/supermoon period semantics. */
        LEGACY_TFCCAELUM,
        /** Uses the explicit numeric period fields. */
        CUSTOM
    }

    public static final CelestialRuntimeSettings DEFAULT = new CelestialRuntimeSettings(
            CelestialMath.SYNODIC_DAYS,
            CelestialMath.ANOMALISTIC_DAYS,
            CelestialMath.NODAL_YEARS,
            CelestialMath.LUNAR_INCLINATION,
            true,
            3.0D,
            CelestialDiscGeometry.DEFAULT_SUN_SCALE,
            CelestialDiscGeometry.DEFAULT_MOON_SCALE,
            LunarPeriodPreset.UNIFIED_16_13,
            CelestialPlanetSettings.DEFAULT,
            CelestialOrbitalPhases.ZERO
    );

    public double resolvedSynodicDays(int calendarDaysInMonth) {
        return switch (lunarPeriodPreset) {
            case UNIFIED_16_13 -> CelestialMath.SYNODIC_DAYS;
            case LEGACY_TFCCAELUM -> Math.max(1, calendarDaysInMonth) * 29.530588D / 30.436875D;
            case CUSTOM -> synodicDays;
        };
    }

    public double resolvedAnomalisticDays(int calendarDaysInMonth) {
        return switch (lunarPeriodPreset) {
            case UNIFIED_16_13 -> CelestialMath.ANOMALISTIC_DAYS;
            case LEGACY_TFCCAELUM -> 29.530588D;
            case CUSTOM -> anomalisticDays;
        };
    }

    /** Package-only, thread-confined reuse for inputs derived solely from this immutable record. */
    PreparedPeriods preparedPeriods(int calendarDaysInMonth) {
        return PREPARED_PERIODS.get().get(this, calendarDaysInMonth);
    }

    record PreparedPeriods(double synodicDays, double anomalisticDays,
                           double sineLunarInclination) {
    }

    private static final class PreparedPeriodsCache {
        private CelestialRuntimeSettings settingsIdentity;
        private int daysInMonth;
        private PreparedPeriods value;

        private PreparedPeriods get(CelestialRuntimeSettings settings, int daysInMonth) {
            if (settingsIdentity != settings || this.daysInMonth != daysInMonth) {
                PreparedPeriods prepared = new PreparedPeriods(
                        settings.resolvedSynodicDays(daysInMonth),
                        settings.resolvedAnomalisticDays(daysInMonth),
                        Math.sin(settings.lunarInclinationRadians()));
                settingsIdentity = settings;
                this.daysInMonth = daysInMonth;
                value = prepared;
            }
            return value;
        }
    }
}
