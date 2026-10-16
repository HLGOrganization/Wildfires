/*
 * Adapted from Caelum `1.20.1-2.0.0.0` (`client.SkyUtils` and `client.data.StarDataManager`
 * star-appearance math) and from TFCCaelum 1.2 `tfccaelum/Helpers` and `tfccaelum/util/Planets`.
 * SPDX-License-Identifier: MIT AND LicenseRef-TFCCaelum-BSD
 * (see third_party/caelum/1.20.1-2.0.0.0/PROVENANCE.md and third_party/tfc-caelum/1.2/PROVENANCE.md).
 * Wildfires modifications: restores the exact upstream small-angle apparent-diameter scale
 * (206265 arcseconds per radian), the visual-only x50 satellite separation and the 5000-tick
 * rainbow curve, keeps Caelum's logarithmic star-magnitude mapping with its once-per-buffer
 * brightness, and applies all of it to the unified three-dimensional state.
 */
package first.wildfires.client.celestial;

import first.wildfires.api.celestial.CelestialVector;
import first.wildfires.api.celestial.LunarEclipseState;
import first.wildfires.celestial.CelestialDiscGeometry;
import first.wildfires.celestial.CelestialEventRules;
import first.wildfires.celestial.CelestialMath;

/** Pure client-visual decisions shared by renderers and deterministic acceptance tests. */
final class CelestialVisualRules {

    private static final double DEG_TO_RAD = Math.PI / 180.0D;
    private static final double TFCCAELUM_ARCSECONDS_PER_RADIAN = 206265.0D;
    private static final double SKY_SPHERE_RADIUS = 100.0D;
    private static final double[] TWILIGHT_COSINES = circleValues(24, true);
    private static final double[] TWILIGHT_SINES = circleValues(24, false);
    private static final double[] DISC_COSINES = circleValues(48, true);
    private static final double[] DISC_SINES = circleValues(48, false);
    private static final ThreadLocal<VisibilityProducts> VISIBILITY_PRODUCTS =
            ThreadLocal.withInitial(VisibilityProducts::new);
    static final double SATELLITE_ORBIT_RENDER_SCALE = 50.0D;
    static final long RAINBOW_DURATION_TICKS = 5000L;
    private static final double LEGACY_RAINBOW_CURVE = 0.0003544658838845707D;

    private CelestialVisualRules() {
    }

    static double twilightCosine(int index) {
        return TWILIGHT_COSINES[index];
    }

    static double twilightSine(int index) {
        return TWILIGHT_SINES[index];
    }

    static double discCosine(int index) {
        return DISC_COSINES[index];
    }

    static double discSine(int index) {
        return DISC_SINES[index];
    }

    private static double[] circleValues(int segments, boolean cosine) {
        double[] values = new double[segments + 1];
        for (int index = 0; index <= segments; index++) {
            double angle = CelestialMath.TAU * index / segments;
            values[index] = cosine ? Math.cos(angle) : Math.sin(angle);
        }
        return values;
    }

    static double auroraProbability(double absoluteLatitudeDegrees) {
        return CelestialEventRules.auroraProbability(absoluteLatitudeDegrees);
    }

    static boolean auroraVisible(boolean legacyGlobal, boolean disabled, int bands,
                                 double latitudeRadians, double sunAltitudeRadians, double deterministicRoll) {
        return CelestialEventRules.auroraVisible(legacyGlobal, disabled, bands, latitudeRadians,
                sunAltitudeRadians, deterministicRoll);
    }

    static double auroraNightFactor(double sunAltitudeRadians) {
        return smoothstep(-6.0D * DEG_TO_RAD, -18.0D * DEG_TO_RAD, sunAltitudeRadians);
    }

    static double twilightAlpha(double sunAltitudeRadians, double weatherVisibility) {
        if (!Double.isFinite(sunAltitudeRadians) || !Double.isFinite(weatherVisibility)) {
            return 0.0D;
        }
        double rising = smoothstep(-18.0D * DEG_TO_RAD, -3.0D * DEG_TO_RAD, sunAltitudeRadians);
        double falling = 1.0D - smoothstep(-1.0D * DEG_TO_RAD, 12.0D * DEG_TO_RAD,
                sunAltitudeRadians);
        double alpha = rising * falling;
        return clamp(alpha, 0.0D, 1.0D) * clamp(weatherVisibility, 0.0D, 1.0D);
    }

    /** Keeps the TFC sunrise fan on the world horizon while following the Sun's current azimuth. */
    static HorizonFrame horizonFrame(CelestialVector sunDirection) {
        if (sunDirection == null || !Double.isFinite(sunDirection.x())
                || !Double.isFinite(sunDirection.z())) {
            return new HorizonFrame(new CelestialVector(0.0D, 0.0D, 1.0D),
                    new CelestialVector(1.0D, 0.0D, 0.0D),
                    new CelestialVector(0.0D, 1.0D, 0.0D));
        }
        double horizontalLength = Math.hypot(sunDirection.x(), sunDirection.z());
        CelestialVector horizon = horizontalLength > 1.0E-9D
                ? new CelestialVector(sunDirection.x() / horizontalLength, 0.0D,
                sunDirection.z() / horizontalLength)
                : new CelestialVector(0.0D, 0.0D, 1.0D);
        CelestialVector right = new CelestialVector(horizon.z(), 0.0D, -horizon.x());
        return new HorizonFrame(horizon, right, new CelestialVector(0.0D, 1.0D, 0.0D));
    }

    /** Tints the complete TFC-style solar texture without changing its glow-to-body scale. */
    static SunAppearance sunAppearance(double sunAltitudeRadians) {
        if (!Double.isFinite(sunAltitudeRadians)) {
            return new SunAppearance(1.0D, 0.65D, 0.30D);
        }
        double warmth = 1.0D - smoothstep(-4.0D * DEG_TO_RAD, 12.0D * DEG_TO_RAD,
                sunAltitudeRadians);
        return new SunAppearance(1.0D, 1.0D - 0.35D * warmth,
                1.0D - 0.70D * warmth);
    }

    /** Matches ClientLevel.getStarBrightness for a local apparent vanilla day time. */
    static double vanillaStarAlpha(double apparentDayTime) {
        if (!Double.isFinite(apparentDayTime)) {
            return 0.0D;
        }
        double celestialAngle = CelestialClientTime.vanillaCelestialAngle(apparentDayTime, Float.NaN);
        if (!Double.isFinite(celestialAngle)) {
            return 0.0D;
        }
        double alpha = 1.0D - (Math.cos(celestialAngle * Math.PI * 2.0D) * 2.0D + 0.25D);
        alpha = clamp(alpha, 0.0D, 1.0D);
        return alpha * alpha * 0.5D;
    }

    /**
     * Prepares the shared vanilla night curve once for every consumer in one rendered sky frame.
     * The mutable holder is render-thread local and its fields preserve the exact expressions used
     * by the individual legacy helpers below.
     */
    static VisibilityProducts prepareVisibility(double apparentDayTime,
                                                 double weatherVisibility) {
        double starAlpha = vanillaStarAlpha(apparentDayTime);
        double weather = Double.isFinite(weatherVisibility)
                ? clamp(weatherVisibility, 0.0D, 1.0D) : 0.0D;
        double starVisibility = Double.isFinite(weatherVisibility)
                ? starAlpha * weather : 0.0D;
        double moonVisibility = Double.isFinite(apparentDayTime)
                && Double.isFinite(weatherVisibility)
                ? (0.2D + 0.8D * starAlpha) * weather : 0.0D;
        double moonNeutralWeight = 0.25D
                + 0.75D * clamp(starAlpha * 2.0D, 0.0D, 1.0D);
        VisibilityProducts products = VISIBILITY_PRODUCTS.get();
        products.set(starAlpha, weather, Double.isFinite(weatherVisibility), starVisibility,
                moonVisibility, moonNeutralWeight);
        return products;
    }

    static double starVisibility(double apparentDayTime, double weatherVisibility) {
        if (!Double.isFinite(weatherVisibility)) {
            return 0.0D;
        }
        return vanillaStarAlpha(apparentDayTime) * clamp(weatherVisibility, 0.0D, 1.0D);
    }

    /** Caelum's logarithmic magnitude intent, made finite at negative catalog magnitudes. */
    static StarAppearance starAppearance(double magnitude, double catalogMinMagnitude,
                                         double catalogMaxMagnitude, double globalScale) {
        if (!Double.isFinite(magnitude) || !Double.isFinite(catalogMinMagnitude)
                || !Double.isFinite(catalogMaxMagnitude) || catalogMaxMagnitude < catalogMinMagnitude
                || !Double.isFinite(globalScale) || globalScale <= 0.0D) {
            return StarAppearance.HIDDEN;
        }
        double boundedMagnitude = clamp(magnitude, catalogMinMagnitude, catalogMaxMagnitude);
        double shift = catalogMinMagnitude <= 0.0D ? 1.0D - catalogMinMagnitude : 0.0D;
        double shiftedMin = catalogMinMagnitude + shift;
        double shiftedMax = catalogMaxMagnitude + shift;
        double shiftedMagnitude = boundedMagnitude + shift;
        double logMin = Math.log10(shiftedMin);
        double logMax = Math.log10(shiftedMax);
        double normalized = logMax > logMin
                ? clamp((Math.log10(shiftedMagnitude) - logMin) / (logMax - logMin), 0.0D, 1.0D)
                : 0.0D;
        return new StarAppearance((0.6D - normalized * 0.5D) * globalScale, 1.0D - normalized);
    }

    /** Caelum applies configured brightness once to the whole star buffer, after local night and weather. */
    static double starShaderBrightness(double apparentDayTime, double weatherVisibility,
                                       double configuredBrightness) {
        if (!Double.isFinite(configuredBrightness) || configuredBrightness <= 0.0D) {
            return 0.0D;
        }
        return starVisibility(apparentDayTime, weatherVisibility) * configuredBrightness;
    }

    static double starShaderBrightness(VisibilityProducts visibility,
                                       double configuredBrightness) {
        if (!Double.isFinite(configuredBrightness) || configuredBrightness <= 0.0D) {
            return 0.0D;
        }
        return visibility.starVisibility() * configuredBrightness;
    }

    /** TFC 1.21 keeps the daytime moon at 20% prominence and restores it continuously at night. */
    static double moonVisibility(double apparentDayTime, double weatherVisibility) {
        if (!Double.isFinite(apparentDayTime) || !Double.isFinite(weatherVisibility)) {
            return 0.0D;
        }
        return (0.2D + 0.8D * vanillaStarAlpha(apparentDayTime))
                * clamp(weatherVisibility, 0.0D, 1.0D);
    }

    /** TFC 1.21 samples every vanilla atlas cell so the new moon retains its dark face and faint rim. */
    static boolean moonTextureVisible(int moonPhase) {
        return moonPhase >= 0 && moonPhase < 8;
    }

    /** Visible stars require an opaque solar-body pass before the additive sun texture. */
    static boolean sunSkyCoverVisible(double apparentDayTime, double weatherVisibility) {
        return starVisibility(apparentDayTime, weatherVisibility) > 0.001D;
    }

    static boolean sunSkyCoverVisible(VisibilityProducts visibility) {
        return visibility.starVisibility() > 0.001D;
    }

    /**
     * Every valid lunar phase owns the nearest celestial body layer. Daylight and weather may fade
     * its texture, but cannot move the physical Moon behind the Sun or planets.
     */
    static boolean moonSkyCoverVisible(int moonPhase, double apparentDayTime, double weatherVisibility) {
        return moonPhase >= 0 && moonPhase < 8;
    }

    /** Vanilla's solid lunar pixels occupy the centered 8x8 area (12..19) of each 32x32 phase cell. */
    static double moonAtlasBodyHalfSize(double moonQuadRadius) {
        return CelestialDiscGeometry.atlasBodyHalfSize(moonQuadRadius);
    }

    /** The TFC 1.21 solar body cover is 7.5 for a 30-radius full sun texture. */
    static double sunAtlasBodyHalfSize(double sunQuadRadius) {
        return CelestialDiscGeometry.atlasBodyHalfSize(sunQuadRadius);
    }

    /** The low-brightness circular gradient occupies pixels 5..26 around the centered lunar body. */
    static double moonAtlasGlowRadius(double moonQuadRadius) {
        return Double.isFinite(moonQuadRadius) && moonQuadRadius > 0.0D
                ? moonQuadRadius * 11.0D / 16.0D
                : 0.0D;
    }

    /** Phase-dependent visual halo that attenuates nearby stars without changing the star catalog. */
    static MoonHalo moonHalo(double illuminatedFraction, double weatherVisibility) {
        if (!Double.isFinite(illuminatedFraction) || !Double.isFinite(weatherVisibility)) {
            return MoonHalo.NONE;
        }
        return moonHaloPrepared(illuminatedFraction,
                clamp(weatherVisibility, 0.0D, 1.0D));
    }

    static MoonHalo moonHalo(double illuminatedFraction, VisibilityProducts visibility) {
        if (!Double.isFinite(illuminatedFraction) || !visibility.weatherFinite()) {
            return MoonHalo.NONE;
        }
        return moonHaloPrepared(illuminatedFraction, visibility.weatherVisibility());
    }

    private static MoonHalo moonHaloPrepared(double illuminatedFraction,
                                             double weatherVisibility) {
        double phase = clamp(illuminatedFraction, 0.0D, 1.0D);
        if (phase <= 0.0D || weatherVisibility <= 0.0D) {
            return MoonHalo.NONE;
        }
        double strength = Math.pow(phase, 0.75D) * weatherVisibility;
        return new MoonHalo(1.5D + 2.0D * Math.sqrt(phase), 0.34D * strength);
    }

    /** The terrestrial shadow removes the full Moon's white veil while leaving the red body visible. */
    static double lunarEclipseMoonlight(double illuminatedFraction, double eclipseCoverage) {
        if (!Double.isFinite(illuminatedFraction)) {
            return 0.0D;
        }
        double phase = clamp(illuminatedFraction, 0.0D, 1.0D);
        double eclipse = clamp(Double.isFinite(eclipseCoverage) ? eclipseCoverage : 0.0D, 0.0D, 1.0D);
        return phase * (1.0D - 0.88D * eclipse);
    }

    static double moonHaloAlpha(MoonHalo halo, double normalizedRadius) {
        if (halo == null || !Double.isFinite(normalizedRadius)) {
            return 0.0D;
        }
        return halo.centerAlpha() * clamp(1.0D - normalizedRadius, 0.0D, 1.0D);
    }

    /** Rendering eligibility is geometric validity only; altitude never deletes a celestial body. */
    static boolean celestialDiscRenderable(CelestialVector direction) {
        return direction != null && Double.isFinite(direction.x()) && Double.isFinite(direction.y())
                && Double.isFinite(direction.z()) && direction.lengthSquared() > 1.0E-12D;
    }

    static double planetVisibility(double apparentDayTime, double weatherVisibility) {
        return starVisibility(apparentDayTime, weatherVisibility);
    }

    static double planetVisibility(VisibilityProducts visibility) {
        return visibility.starVisibility();
    }

    /**
     * Restores TFCCaelum's exact small-angle apparent-diameter quad scale. The unified state stores
     * atan2(diameter / 2, distance), so twice its tangent reconstructs the source diameter/distance
     * ratio before applying TFCCaelum's fixed 206265 arcseconds-per-radian approximation. Every
     * planet and satellite therefore retains the source ratio and its own scale factor; the global
     * setting remains a single linear multiplier for the whole system.
     */
    static double planetRenderRadius(double angularRadiusRadians, double bodyScale, double globalScale) {
        if (!Double.isFinite(angularRadiusRadians) || angularRadiusRadians <= 0.0D
                || !Double.isFinite(bodyScale) || bodyScale <= 0.0D
                || !Double.isFinite(globalScale) || globalScale <= 0.0D) {
            return 0.0D;
        }
        double apparentDiameterArcSeconds = 2.0D * Math.tan(angularRadiusRadians)
                * TFCCAELUM_ARCSECONDS_PER_RADIAN;
        double radius = apparentDiameterArcSeconds * bodyScale * globalScale;
        return Double.isFinite(radius) ? radius : 0.0D;
    }

    /**
     * Restores TFCCaelum's visual-only x50 satellite separation on the unified three-dimensional
     * tangent direction. The physical API position, distance and angular size stay untouched.
     */
    static CelestialVector satelliteRenderDirection(CelestialVector parentDirection,
                                                     CelestialVector satelliteDirection) {
        CelestialVector parent = parentDirection.normalized();
        CelestialVector satellite = satelliteDirection.normalized();
        if (parent == CelestialVector.ZERO || satellite == CelestialVector.ZERO) {
            return satellite;
        }
        double dot = clamp(parent.dot(satellite), -1.0D, 1.0D);
        CelestialVector tangent = satellite.subtract(parent.scale(dot));
        double tangentLength = tangent.length();
        if (tangentLength < 1.0E-12D) {
            return satellite;
        }
        double physicalAngle = Math.atan2(tangentLength, dot);
        double visualAngle = Math.min(Math.PI * 0.5D, physicalAngle * SATELLITE_ORBIT_RENDER_SCALE);
        CelestialVector tangentDirection = tangent.scale(1.0D / tangentLength);
        return parent.scale(Math.cos(visualAngle))
                .add(tangentDirection.scale(Math.sin(visualAngle))).normalized();
    }

    /**
     * Builds a celestial-north-locked tangent frame for textured sky discs. A world-up billboard
     * has an unavoidable hard reference-axis switch around the zenith; projecting celestial north
     * keeps the Sun, Moon and planet textures continuous along the ecliptic instead.
     */
    static CelestialDiscGeometry.Basis stableDiscBasis(CelestialVector bodyDirection,
                                                       CelestialVector celestialNorth) {
        return CelestialDiscGeometry.stableBasis(bodyDirection, celestialNorth);
    }

    static boolean startsRainbow(float rainBefore, float currentRain, float rainAfter, double apparentDayTime,
                                 double sunAltitudeRadians) {
        return CelestialEventRules.startsRainbow(rainBefore, currentRain, rainAfter,
                apparentDayTime, sunAltitudeRadians);
    }

    static boolean rainbowVisible(long remainingTicks, double apparentDayTime, double sunAltitudeRadians) {
        return remainingTicks > 0L
                && apparentDayTime < 13000.0D / 24000.0D
                && sunAltitudeRadians > 0.0D;
    }

    /** Unit direction opposite the Sun with a stable fallback at the solar zenith/nadir singularity. */
    static RainbowDirection rainbowDirection(double sunX, double sunZ) {
        double horizontalLength = Math.sqrt(sunX * sunX + sunZ * sunZ);
        double x;
        double z;
        if (!Double.isFinite(horizontalLength) || horizontalLength < 1.0E-9D) {
            x = 0.0D;
            z = 1.0D;
        } else {
            x = -sunX / horizontalLength;
            z = -sunZ / horizontalLength;
        }
        double length = Math.sqrt(x * x + 0.25D * 0.25D + z * z);
        return new RainbowDirection(x / length, 0.25D / length, z / length);
    }

    /** TFCCaelum's 5000-tick rainbow curve, with its empirical eclipse term replaced by disc coverage. */
    static double rainbowAlpha(long remainingTicks, double solarEclipseCoverage) {
        double timer = clamp(remainingTicks, 0L, RAINBOW_DURATION_TICKS);
        double curve = Math.sin(Math.pow(timer * LEGACY_RAINBOW_CURVE, 2.0D));
        double eclipseVisibility = 1.0D - CelestialClientTime.eclipseVisualIntensity(solarEclipseCoverage);
        return clamp(curve * eclipseVisibility, 0.0D, 1.0D);
    }

    static long advanceRainbowTimer(long remainingTicks, long elapsedTicks, boolean visibleWindow) {
        long remaining = Math.max(0L, Math.min(RAINBOW_DURATION_TICKS, remainingTicks));
        if (!visibleWindow || elapsedTicks <= 0L) {
            return remaining;
        }
        return elapsedTicks >= remaining ? 0L : remaining - elapsedTicks;
    }

    /** Fixed new-moon texture brightness; unlike the old eight frames this cannot jump discretely. */
    static double solarOccultorTextureAlpha(double coverage) {
        double obscured = clamp(Double.isFinite(coverage) ? coverage : 0.0D, 0.0D, 1.0D);
        return 1.0D - obscured;
    }

    /** Uses one ordinary lunar texture before, during and after a solar eclipse. */
    static double solarOccultorMoonAlpha(double apparentDayTime, double weatherVisibility,
                                         double coverage) {
        return moonVisibility(apparentDayTime, weatherVisibility)
                * solarOccultorTextureAlpha(coverage);
    }

    static double solarOccultorMoonAlpha(VisibilityProducts visibility, double coverage) {
        return visibility.moonVisibility() * solarOccultorTextureAlpha(coverage);
    }

    /** Daylight washes the Moon into the local sky; night continuously restores its neutral texture. */
    static MoonTint moonSkyTint(double skyRed, double skyGreen, double skyBlue,
                                double apparentDayTime) {
        double red = clamp(Double.isFinite(skyRed) ? skyRed : 0.0D, 0.0D, 1.0D);
        double green = clamp(Double.isFinite(skyGreen) ? skyGreen : 0.0D, 0.0D, 1.0D);
        double blue = clamp(Double.isFinite(skyBlue) ? skyBlue : 0.0D, 0.0D, 1.0D);
        double night = clamp(vanillaStarAlpha(apparentDayTime) * 2.0D, 0.0D, 1.0D);
        double neutralWeight = 0.25D + 0.75D * night;
        return new MoonTint(red + (1.0D - red) * neutralWeight,
                green + (1.0D - green) * neutralWeight,
                blue + (1.0D - blue) * neutralWeight);
    }

    static MoonTint moonSkyTint(double skyRed, double skyGreen, double skyBlue,
                                VisibilityProducts visibility) {
        double red = clamp(Double.isFinite(skyRed) ? skyRed : 0.0D, 0.0D, 1.0D);
        double green = clamp(Double.isFinite(skyGreen) ? skyGreen : 0.0D, 0.0D, 1.0D);
        double blue = clamp(Double.isFinite(skyBlue) ? skyBlue : 0.0D, 0.0D, 1.0D);
        double neutralWeight = visibility.moonNeutralWeight();
        return new MoonTint(red + (1.0D - red) * neutralWeight,
                green + (1.0D - green) * neutralWeight,
                blue + (1.0D - blue) * neutralWeight);
    }

    /**
     * Local-night blue-moon strength. Sunset and sunrise stay white, midnight reaches the
     * configured supermoon strength, weather attenuates the tint, and the shared lunar-eclipse
     * transition coverage continuously removes blue before its red shadow layers take over.
     */
    static double supermoonBlueIntensity(double apparentDayTime, double weatherVisibility,
                                         double supermoonStrength, double lunarEclipseCoverage,
                                         double sunAltitudeRadians, double moonAltitudeRadians) {
        if (!Double.isFinite(weatherVisibility)) {
            return 0.0D;
        }
        return supermoonBlueIntensityPrepared(apparentDayTime,
                clamp(weatherVisibility, 0.0D, 1.0D), supermoonStrength,
                lunarEclipseCoverage, sunAltitudeRadians, moonAltitudeRadians);
    }

    static double supermoonBlueIntensity(double apparentDayTime, VisibilityProducts visibility,
                                         double supermoonStrength, double lunarEclipseCoverage,
                                         double sunAltitudeRadians, double moonAltitudeRadians) {
        if (!visibility.weatherFinite()) {
            return 0.0D;
        }
        return supermoonBlueIntensityPrepared(apparentDayTime, visibility.weatherVisibility(),
                supermoonStrength, lunarEclipseCoverage,
                sunAltitudeRadians, moonAltitudeRadians);
    }

    private static double supermoonBlueIntensityPrepared(
            double apparentDayTime, double weatherVisibility,
            double supermoonStrength, double lunarEclipseCoverage,
            double sunAltitudeRadians, double moonAltitudeRadians) {
        if (!Double.isFinite(apparentDayTime)
                || !Double.isFinite(supermoonStrength) || !Double.isFinite(lunarEclipseCoverage)
                || !Double.isFinite(sunAltitudeRadians) || !Double.isFinite(moonAltitudeRadians)
                || sunAltitudeRadians > 0.0D || moonAltitudeRadians <= 0.0D) {
            return 0.0D;
        }
        double distanceFromMidnight = Math.abs(apparentDayTime - 0.75D) % 1.0D;
        distanceFromMidnight = Math.min(distanceFromMidnight, 1.0D - distanceFromMidnight);
        double nightProgress = smoothstep(0.0D, 1.0D,
                clamp(1.0D - distanceFromMidnight / 0.25D, 0.0D, 1.0D));
        return nightProgress * weatherVisibility
                * clamp(supermoonStrength, 0.0D, 1.0D)
                * (1.0D - clamp(lunarEclipseCoverage, 0.0D, 1.0D));
    }

    /**
     * Uses the same first penumbral contact that activates the red lunar-eclipse pass. Umbra is
     * retained as a defensive maximum so malformed third-party states cannot restore blue inside
     * a darker shadow.
     */
    static double lunarEclipseTintCoverage(LunarEclipseState eclipse) {
        if (eclipse == null) {
            return 0.0D;
        }
        return Math.max(clamp(eclipse.penumbraCoverage(), 0.0D, 1.0D),
                clamp(eclipse.umbraCoverage(), 0.0D, 1.0D));
    }

    static MoonTint supermoonTint(MoonTint ordinary, double blueIntensity) {
        if (ordinary == null) {
            return new MoonTint(1.0D, 1.0D, 1.0D);
        }
        double blue = clamp(Double.isFinite(blueIntensity) ? blueIntensity : 0.0D, 0.0D, 1.0D);
        return new MoonTint(ordinary.red() * (1.0D - 0.55D * blue),
                ordinary.green() * (1.0D - 0.28D * blue),
                ordinary.blue() + (1.0D - ordinary.blue()) * blue);
    }

    /** Keeps the eclipsed corona warm while the geometric Moon removes the covered solar body. */
    static SunTint solarEclipseSunTint(double coverage) {
        double obscured = CelestialClientTime.eclipseVisualIntensity(coverage);
        return new SunTint(1.0D, 1.0D - 0.28D * obscured,
                1.0D - 0.82D * obscured);
    }

    /** Preserves opaque occultation while the visible Moon darkens continuously with covered area. */
    static SolarOccultorTint solarOccultorTint(double skyRed, double skyGreen, double skyBlue,
                                               double coverage) {
        double value = clamp(Double.isFinite(coverage) ? coverage : 0.0D, 0.0D, 1.0D);
        double red = clamp(Double.isFinite(skyRed) ? skyRed : 0.0D, 0.0D, 1.0D);
        double green = clamp(Double.isFinite(skyGreen) ? skyGreen : 0.0D, 0.0D, 1.0D);
        double blue = clamp(Double.isFinite(skyBlue) ? skyBlue : 0.0D, 0.0D, 1.0D);
        return new SolarOccultorTint(red + (0.008D - red) * value,
                green + (0.012D - green) * value,
                blue + (0.022D - blue) * value);
    }

    /** Uses the server/client-common lunar projection without recomputing a raw anti-solar shadow. */
    static LunarShadow lunarShadow(LunarEclipseState state) {
        if (state == null || !state.active() || !Double.isFinite(state.shadowCenterX())
                || !Double.isFinite(state.shadowCenterY())
                || !Double.isFinite(state.shadowRadius()) || state.shadowRadius() <= 0.0D) {
            return LunarShadow.NONE;
        }
        return new LunarShadow(state.shadowCenterX(), state.shadowCenterY(),
                state.shadowRadius(), true);
    }

    /** Coverage of the Moon by the umbra plus its one-pixel visual penumbra. */
    static double lunarPenumbraCoverage(LunarShadow shadow) {
        if (shadow == null || !shadow.visible() || !Double.isFinite(shadow.centerX())
                || !Double.isFinite(shadow.centerY()) || !Double.isFinite(shadow.radius())) {
            return 0.0D;
        }
        double expandedRadius = shadow.radius() + CelestialDiscGeometry.LUNAR_PENUMBRA_NORMALIZED_WIDTH;
        double xOverlap = Math.max(0.0D, Math.min(1.0D, shadow.centerX() + expandedRadius)
                - Math.max(-1.0D, shadow.centerX() - expandedRadius));
        double yOverlap = Math.max(0.0D, Math.min(1.0D, shadow.centerY() + expandedRadius)
                - Math.max(-1.0D, shadow.centerY() - expandedRadius));
        return clamp(xOverlap * yOverlap / 4.0D, 0.0D, 1.0D);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double x = clamp((value - edge0) / (edge1 - edge0), 0.0D, 1.0D);
        return x * x * (3.0D - 2.0D * x);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static CelestialVector finiteUnit(CelestialVector vector, CelestialVector fallback) {
        if (vector != null && Double.isFinite(vector.x()) && Double.isFinite(vector.y())
                && Double.isFinite(vector.z()) && vector.lengthSquared() > 1.0E-12D) {
            return vector.normalized();
        }
        return fallback;
    }

    private static CelestialVector cross(CelestialVector first, CelestialVector second) {
        return new CelestialVector(first.y() * second.z() - first.z() * second.y(),
                first.z() * second.x() - first.x() * second.z(),
                first.x() * second.y() - first.y() * second.x());
    }

    static final class VisibilityProducts {
        private double starAlpha;
        private double weatherVisibility;
        private boolean weatherFinite;
        private double starVisibility;
        private double moonVisibility;
        private double moonNeutralWeight;

        private void set(double starAlpha, double weatherVisibility, boolean weatherFinite,
                         double starVisibility, double moonVisibility, double moonNeutralWeight) {
            this.starAlpha = starAlpha;
            this.weatherVisibility = weatherVisibility;
            this.weatherFinite = weatherFinite;
            this.starVisibility = starVisibility;
            this.moonVisibility = moonVisibility;
            this.moonNeutralWeight = moonNeutralWeight;
        }

        double starAlpha() {
            return starAlpha;
        }

        double weatherVisibility() {
            return weatherVisibility;
        }

        boolean weatherFinite() {
            return weatherFinite;
        }

        double starVisibility() {
            return starVisibility;
        }

        double moonVisibility() {
            return moonVisibility;
        }

        double moonNeutralWeight() {
            return moonNeutralWeight;
        }
    }

    record SunTint(double red, double green, double blue) {}

    record SolarOccultorTint(double red, double green, double blue) {}

    record MoonTint(double red, double green, double blue) {}

    record SunAppearance(double red, double green, double blue) {}

    record HorizonFrame(CelestialVector horizon, CelestialVector right, CelestialVector up) {}

    record RainbowDirection(double x, double y, double z) {}

    record MoonHalo(double radiusMultiplier, double centerAlpha) {
        private static final MoonHalo NONE = new MoonHalo(0.0D, 0.0D);
    }

    record LunarShadow(double centerX, double centerY, double radius, boolean visible) {
        private static final LunarShadow NONE = new LunarShadow(0.0D, 0.0D, 0.0D, false);
    }

    record StarAppearance(double radius, double alpha) {
        private static final StarAppearance HIDDEN = new StarAppearance(0.0D, 0.0D);
    }
}
