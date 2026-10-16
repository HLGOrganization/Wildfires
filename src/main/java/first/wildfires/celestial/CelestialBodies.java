/*
 * Adapted from TFCCaelum 1.2 `tfccaelum/util/Planets`.
 * SPDX-License-Identifier: LicenseRef-TFCCaelum-BSD (see third_party/tfc-caelum/1.2/PROVENANCE.md).
 * Wildfires modifications: keeps the retained planet/satellite parameter set but drives real radii,
 * ecliptic inclinations, nodes and parent-body relations on three-dimensional orbits instead of the
 * legacy two-dimensional sky projection.
 */
package first.wildfires.celestial;

import first.wildfires.api.celestial.CelestialBodyState;
import first.wildfires.api.celestial.CelestialVector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Three-dimensional planets and satellites retained from TFCCaelum. */
public enum CelestialBodies {
    // radius/period values and J2000 ecliptic inclinations/nodes; spin obliquity is physical.
    MERCURY(4879, 87.9691, 57.909, 115.88, 7.005, 48.331, 318.235, 7.037, 0.1,
            false, null, OrbitReferenceFrame.ECLIPTIC, 0.0, 90.0),
    VENUS(12104, 224.701, 108.210, 583.92, 3.3946, 76.680, 30.187, 1.239, 0.1,
            false, null, OrbitReferenceFrame.ECLIPTIC, 0.0, 90.0),
    MARS(6792, 686.980, 227.956, 779.94, 1.850, 49.558, 352.908, 26.718, 0.4,
            false, null, OrbitReferenceFrame.ECLIPTIC, 0.0, 90.0),
    JUPITER(142984, 4332.59, 778.479, 398.88, 1.303, 100.464, 247.818, 2.217, 0.125,
            false, null, OrbitReferenceFrame.ECLIPTIC, 0.0, 90.0),
    SATURN(120536, 10759.22, 1432.041, 378.09, 2.485, 113.665, 79.528, 28.052, 0.15,
            false, null, OrbitReferenceFrame.ECLIPTIC, 0.0, 90.0),
    URANUS(51118, 30688.5, 2867.043, 369.66, 0.773, 74.006, 257.647, 82.278, 0.4,
            false, null, OrbitReferenceFrame.ECLIPTIC, 0.0, 90.0),
    NEPTUNE(49528, 60182.0, 4514.953, 367.49, 1.770, 131.784, 319.235, 28.026, 0.4,
            false, null, OrbitReferenceFrame.ECLIPTIC, 0.0, 90.0),
    PLUTO(2376, 90560.0, 5869.656, 366.73, 17.160, 110.299, 137.351, 112.816, 5.0,
            false, null, OrbitReferenceFrame.ECLIPTIC, 0.0, 90.0),
    PERSEPHONE(50968, 5_478_630.0, 73302.956643, 365.242, 16.0, 0.0, 0.0, 0.0, 5.0,
            false, null, OrbitReferenceFrame.ECLIPTIC, 0.0, 90.0),
    NEMESIS(571936, 4_054_186.2, 14_190_792.6, 365.242, 10.0, 0.0, 0.0, 0.0, 20.0,
            false, null, OrbitReferenceFrame.ECLIPTIC, 0.0, 90.0),
    // JPL mean elements use each satellite's stated ecliptic/Laplace/equatorial reference plane.
    GANYMEDE(5268.2, 7.15455296, 1.0704, 4330.595, 0.20, 58.5, 0.0, 0.20, 1.0,
            false, JUPITER, OrbitReferenceFrame.J2000_EQUATORIAL_POLE, 268.2, 64.6),
    CALLISTO(4820.6, 16.6890184, 1.8827, 4330.595, 0.30, 309.1, 0.0, 0.30, 1.0,
            false, JUPITER, OrbitReferenceFrame.J2000_EQUATORIAL_POLE, 268.7, 64.8),
    IO(3643.2, 1.769137786, 0.4217, 4330.595, 0.0, 0.0, 0.0, 0.0, 1.0,
            false, JUPITER, OrbitReferenceFrame.J2000_EQUATORIAL_POLE, 268.1, 64.5),
    EUROPA(3121.6, 3.551181, 0.669151, 4330.595, 0.50, 184.0, 0.0, 0.50, 1.0,
            false, JUPITER, OrbitReferenceFrame.J2000_EQUATORIAL_POLE, 268.1, 64.5),
    TITAN(5149.46, 15.945, 1.22187, 378.09, 0.30, 78.6, 0.0, 0.30, 1.0,
            false, SATURN, OrbitReferenceFrame.J2000_EQUATORIAL_POLE, 36.4, 84.0),
    TRITON(2706.8, 5.876854, 0.354759, 367.49, 157.3, 178.1, 0.0, 157.3, 1.0,
            false, NEPTUNE, OrbitReferenceFrame.J2000_EQUATORIAL_POLE, 299.8, 43.1),
    CHARON(1212.5, 6.3872304, 0.019596, 366.73, 0.0, 0.0, 0.0, 0.0, 12.0,
            false, PLUTO, OrbitReferenceFrame.PARENT_EQUATOR, 0.0, 90.0);

    public static final double EARTH_ORBITAL_DAYS = 365.242D;
    public static final double EARTH_DIAMETER_KM = 12742.0D;
    public static final double EARTH_SEMI_MAJOR_AXIS = 149.598D;
    private static final CelestialBodies[] ORDERED = values();
    private static final PrimaryOrbitBasis EARTH_ORBIT_BASIS =
            PrimaryOrbitBasis.create(0.0D, 0.0D);
    private static final ThreadLocal<BodyCalculationScratch> CALCULATION_SCRATCH =
            ThreadLocal.withInitial(BodyCalculationScratch::new);
    static {
        for (CelestialBodies body : ORDERED) {
            body.initializeFixedOrbitGeometry();
        }
    }
    private static final Map<ResourceLocation, CelestialBodies> BY_ID = createIdIndex();

    private final double diameterKm;
    private final double orbitalDays;
    private final double semiMajorMillionKm;
    private final double synodicDays;
    private final double inclinationRadians;
    private final double ascendingNodeRadians;
    private final double spinPoleLongitudeRadians;
    private final double axialTiltRadians;
    private final double scaleFactor;
    private final boolean retrograde;
    private final CelestialBodies parent;
    private final OrbitReferenceFrame orbitReferenceFrame;
    private final double referencePoleRightAscensionRadians;
    private final double referencePoleDeclinationRadians;
    private final ResourceLocation id;
    private final ResourceLocation renderTexture;
    private final CelestialBodyParameters defaultParameters;
    private CelestialVector orbitalReferenceNormalEcliptic;
    private CelestialMath.SatelliteOrbitBasis satelliteOrbitBasis;

    CelestialBodies(double diameterKm, double orbitalDays, double semiMajorMillionKm,
                    double synodicDays, double inclinationDegrees, double ascendingNodeDegrees,
                    double spinPoleLongitudeDegrees, double axialTiltDegrees, double scaleFactor,
                    boolean retrograde, CelestialBodies parent,
                    OrbitReferenceFrame orbitReferenceFrame,
                    double referencePoleRightAscensionDegrees,
                    double referencePoleDeclinationDegrees) {
        this.diameterKm = diameterKm;
        this.orbitalDays = orbitalDays;
        this.semiMajorMillionKm = semiMajorMillionKm;
        this.synodicDays = synodicDays;
        this.inclinationRadians = Math.toRadians(inclinationDegrees);
        this.ascendingNodeRadians = Math.toRadians(ascendingNodeDegrees);
        this.spinPoleLongitudeRadians = Math.toRadians(spinPoleLongitudeDegrees);
        this.axialTiltRadians = Math.toRadians(axialTiltDegrees);
        this.scaleFactor = scaleFactor;
        this.retrograde = retrograde;
        this.parent = parent;
        this.orbitReferenceFrame = orbitReferenceFrame;
        this.referencePoleRightAscensionRadians = Math.toRadians(referencePoleRightAscensionDegrees);
        this.referencePoleDeclinationRadians = Math.toRadians(referencePoleDeclinationDegrees);
        this.id = ResourceLocation.fromNamespaceAndPath("wildfires", name().toLowerCase());
        this.renderTexture = ResourceLocation.fromNamespaceAndPath("wildfires",
                "textures/sky/planets/" + name().toLowerCase() + ".png");
        this.defaultParameters = new CelestialBodyParameters(diameterKm, orbitalDays, semiMajorMillionKm,
                synodicDays, this.inclinationRadians);
    }

    private void initializeFixedOrbitGeometry() {
        orbitalReferenceNormalEcliptic = computeOrbitalReferenceNormalEcliptic();
        satelliteOrbitBasis = parent == null ? null
                : CelestialMath.satelliteOrbitBasis(orbitalReferenceNormalEcliptic,
                inclinationRadians, ascendingNodeRadians);
    }

    public ResourceLocation id() {
        return id;
    }

    public ResourceLocation texture() {
        return ResourceLocation.fromNamespaceAndPath("wildfires",
                "textures/sky/planets/" + name().toLowerCase() + ".png");
    }

    /** Stable immutable texture identity for the per-frame renderer hot path. */
    public ResourceLocation renderTexture() {
        return renderTexture;
    }

    public double diameterKm() { return diameterKm; }
    public double orbitalDays() { return orbitalDays; }
    public double semiMajorMillionKm() { return semiMajorMillionKm; }
    public double synodicDays() { return synodicDays; }
    public double inclinationRadians() { return inclinationRadians; }
    public double ascendingNodeRadians() { return ascendingNodeRadians; }
    public double spinPoleLongitudeRadians() { return spinPoleLongitudeRadians; }
    public double axialTiltRadians() { return axialTiltRadians; }
    /** Inclination of the resulting physical orbit plane against the common ecliptic. */
    public double orbitalPlaneInclinationRadians() {
        return orbitalPlaneInclinationRadians(CelestialPlanetSettings.DEFAULT);
    }
    public CelestialVector orbitalPlaneNormalEcliptic() {
        return orbitalNormal(CelestialPlanetSettings.DEFAULT);
    }
    /** Orbit-plane normal after applying the synchronized per-body inclination settings. */
    public CelestialVector orbitalPlaneNormalEcliptic(CelestialPlanetSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("Celestial planet settings cannot be null");
        }
        return orbitalNormal(settings);
    }
    public CelestialVector spinAxisEcliptic() {
        if (parent != null) {
            return orbitalNormal(CelestialPlanetSettings.DEFAULT);
        }
        double longitude = spinPoleLongitudeRadians;
        return new CelestialVector(Math.sin(axialTiltRadians) * Math.cos(longitude),
                Math.sin(axialTiltRadians) * Math.sin(longitude),
                Math.cos(axialTiltRadians)).normalized();
    }
    public double scaleFactor() { return scaleFactor; }
    public boolean retrograde() { return retrograde; }
    public CelestialBodies parent() { return parent; }
    public OrbitReferenceFrame orbitReferenceFrame() { return orbitReferenceFrame; }

    /** Normal of the source element reference plane, expressed in the common J2000 ecliptic frame. */
    public CelestialVector orbitalReferenceNormalEcliptic() {
        return orbitalReferenceNormalEcliptic;
    }

    private CelestialVector computeOrbitalReferenceNormalEcliptic() {
        return switch (orbitReferenceFrame) {
            case ECLIPTIC -> new CelestialVector(0.0D, 0.0D, 1.0D);
            case PARENT_EQUATOR -> parent.spinAxisEcliptic();
            case J2000_EQUATORIAL_POLE -> equatorialPoleToEcliptic(
                    referencePoleRightAscensionRadians, referencePoleDeclinationRadians);
        };
    }

    public static CelestialBodies byId(ResourceLocation id) {
        return BY_ID.get(id);
    }

    public CelestialBodyParameters defaultParameters() {
        return defaultParameters;
    }

    /** Upgrades untouched values emitted by the former TFCCaelum-derived defaults. */
    public CelestialBodyParameters migrateLegacyDefaults(CelestialBodyParameters configured) {
        CelestialBodyParameters legacy = legacyDefaultParameters();
        if (legacy == null) {
            return configured;
        }
        return new CelestialBodyParameters(
                migrate(configured.diameterKm(), legacy.diameterKm(), defaultParameters.diameterKm()),
                migrate(configured.orbitalDays(), legacy.orbitalDays(), defaultParameters.orbitalDays()),
                migrate(configured.semiMajorMillionKm(), legacy.semiMajorMillionKm(),
                        defaultParameters.semiMajorMillionKm()),
                migrate(configured.synodicDays(), legacy.synodicDays(), defaultParameters.synodicDays()),
                migrate(configured.inclinationRadians(), legacy.inclinationRadians(),
                        defaultParameters.inclinationRadians()));
    }

    private CelestialBodyParameters legacyDefaultParameters() {
        return switch (this) {
            case MERCURY -> legacy(4879, 87.968, 57.909, 115.88, 7.004);
            case VENUS -> legacy(12104, 224.695, 108.210, 583.92, 3.395);
            case MARS -> legacy(6792, 779.94, 227.956, 779.94, 1.848);
            case JUPITER -> legacy(142984, 4330.595, 778.479, 4330.595, 1.304);
            case SATURN -> legacy(120536, 10746.94, 1432.041, 378.09, 2.486);
            case URANUS -> legacy(51118, 30588.74, 2867.043, 369.66, 0.770);
            case NEPTUNE -> legacy(49528, 59799.9, 4514.953, 367.49, 1.770);
            case PLUTO -> legacy(2376, 90560.0, 5869.656, 366.73, 17.160);
            case CALLISTO -> legacy(4820.6, 16.6890184, 1.8827, 4330.595, 0.192);
            case IO -> legacy(3643.2, 1.769137786, 0.4217, 4330.595, 0.05);
            case EUROPA -> legacy(3121.6, 3.551181, 0.669151, 4330.595, 0.47);
            case TITAN -> legacy(5149.46, 15.945, 1.22187, 378.09, 0.348);
            case TRITON -> legacy(2706.8, 5.876854, 0.354759, 367.49, 23.115);
            case CHARON -> legacy(1212.5, 6.3872304, 0.019596, 366.73, 0.08);
            default -> null;
        };
    }

    private static CelestialBodyParameters legacy(double diameter, double period, double axis,
                                                   double synodic, double inclinationDegrees) {
        return new CelestialBodyParameters(diameter, period, axis, synodic,
                Math.toRadians(inclinationDegrees));
    }

    private static double migrate(double configured, double legacy, double corrected) {
        return Double.doubleToLongBits(configured) == Double.doubleToLongBits(legacy)
                ? corrected : configured;
    }

    public static List<CelestialBodyState> calculate(CelestialMath.Result frame, double calendarYears) {
        return calculate(frame, calendarYears, CelestialPlanetSettings.DEFAULT);
    }

    public static List<CelestialBodyState> calculate(CelestialMath.Result frame, double calendarYears,
                                                      CelestialPlanetSettings settings) {
        return calculate(frame, calendarYears, settings, CelestialOrbitalPhases.ZERO);
    }

    public static List<CelestialBodyState> calculate(CelestialMath.Result frame, double calendarYears,
                                                       CelestialPlanetSettings settings,
                                                       CelestialOrbitalPhases phases) {
        return calculate(frame, calendarYears, settings, phases,
                Math.sin(frame.latitude()), Math.cos(frame.latitude()),
                Math.sin(frame.localSiderealAngle()), Math.cos(frame.localSiderealAngle()));
    }

    /** Package path reusing the exact horizon products already evaluated by the full frame. */
    static List<CelestialBodyState> calculate(CelestialMath.Result frame, double calendarYears,
                                               CelestialPlanetSettings settings,
                                               CelestialOrbitalPhases phases,
                                               double sineLatitude, double cosineLatitude,
                                               double sineSidereal, double cosineSidereal) {
        BodyCalculationScratch scratch = CALCULATION_SCRATCH.get();
        scratch.prepare(settings, phases);
        double[] positionX = scratch.positionX;
        double[] positionY = scratch.positionY;
        double[] positionZ = scratch.positionZ;
        double earthOrbitalDays = settings.earthOrbitalDays();
        double astronomicalDays = calendarYears * earthOrbitalDays
                + (284.0D / 365.0D + 0.5D) * earthOrbitalDays;
        EclipticRotation forwardRotation = scratch.forwardRotation;
        EclipticRotation inverseRotation = scratch.inverseRotation;
        CelestialVector earth = rotateEcliptic(primaryOrbitalPosition(
                settings.earthSemiMajorMillionKm(), earthOrbitalDays, false,
                astronomicalDays, 0.0D, EARTH_ORBIT_BASIS), forwardRotation);
        List<CelestialBodyState> states = new ArrayList<>(ORDERED.length);
        for (CelestialBodies body : ORDERED) {
            int bodyIndex = body.ordinal();
            CelestialBodyParameters parameters = scratch.parameters[bodyIndex];
            int parentIndex = body.parent == null ? -1 : body.parent.ordinal();
            double originX = parentIndex < 0 ? CelestialVector.ZERO.x() : positionX[parentIndex];
            double originY = parentIndex < 0 ? CelestialVector.ZERO.y() : positionY[parentIndex];
            double originZ = parentIndex < 0 ? CelestialVector.ZERO.z() : positionZ[parentIndex];
            CelestialVector unrotatedRelative = body.parent == null
                    ? primaryOrbitalPosition(parameters.semiMajorMillionKm(),
                            parameters.orbitalDays(), body.retrograde, astronomicalDays,
                            phases.turns(body), scratch.primaryOrbitBases[bodyIndex])
                    : CelestialMath.satelliteOrbitalPosition(parameters.semiMajorMillionKm(),
                            parameters.orbitalDays(), body.satelliteOrbitBasis,
                            body.retrograde, astronomicalDays, phases.turns(body));
            double relativeX = unrotatedRelative.x() * forwardRotation.cosine()
                    - unrotatedRelative.y() * forwardRotation.sine();
            double relativeY = unrotatedRelative.x() * forwardRotation.sine()
                    + unrotatedRelative.y() * forwardRotation.cosine();
            double relativeZ = unrotatedRelative.z();
            // Preserve the exact ZERO.add(relative) and parent.add(relative) component order
            // without retaining an otherwise short-lived heliocentric vector in thread state.
            double heliocentricX = originX + relativeX;
            double heliocentricY = originY + relativeY;
            double heliocentricZ = originZ + relativeZ;
            positionX[bodyIndex] = heliocentricX;
            positionY[bodyIndex] = heliocentricY;
            positionZ[bodyIndex] = heliocentricZ;
            // The world phase rotates the inertial reference frame, not TFC's seasonal axes.
            // Undo that common rotation before exposing Earth-local directions and station visuals.
            double relativeToEarthX = heliocentricX - earth.x();
            double relativeToEarthY = heliocentricY - earth.y();
            double relativeToEarthZ = heliocentricZ - earth.z();
            CelestialVector geocentric = new CelestialVector(
                    relativeToEarthX * inverseRotation.cosine()
                            - relativeToEarthY * inverseRotation.sine(),
                    relativeToEarthX * inverseRotation.sine()
                            + relativeToEarthY * inverseRotation.cosine(),
                    relativeToEarthZ);
            double geocentricLength = geocentric.length();
            double normalizedX;
            double normalizedY;
            double normalizedZ;
            if (geocentricLength > 1.0E-12D) {
                double inverse = 1.0D / geocentricLength;
                normalizedX = geocentric.x() * inverse;
                normalizedY = geocentric.y() * inverse;
                normalizedZ = geocentric.z() * inverse;
            } else {
                normalizedX = 0.0D;
                normalizedY = 0.0D;
                normalizedZ = 0.0D;
            }
            double rotatedX = normalizedX;
            double rotatedY = normalizedY * CelestialMath.AXIAL_TILT_COS
                    - normalizedZ * CelestialMath.AXIAL_TILT_SIN;
            double rotatedZ = normalizedY * CelestialMath.AXIAL_TILT_SIN
                    + normalizedZ * CelestialMath.AXIAL_TILT_COS;
            double rotatedLengthSquared = rotatedX * rotatedX + rotatedY * rotatedY
                    + rotatedZ * rotatedZ;
            double rotatedLength = Math.sqrt(rotatedLengthSquared);
            double equatorialX;
            double equatorialY;
            double equatorialZ;
            if (rotatedLength > 1.0E-12D) {
                double inverse = 1.0D / rotatedLength;
                equatorialX = rotatedX * inverse;
                equatorialY = rotatedY * inverse;
                equatorialZ = rotatedZ * inverse;
            } else {
                equatorialX = 0.0D;
                equatorialY = 0.0D;
                equatorialZ = 0.0D;
            }
            CelestialVector direction = CelestialMath.equatorialToHorizon(
                    equatorialX, equatorialY, equatorialZ, sineLatitude, cosineLatitude,
                    sineSidereal, cosineSidereal);
            double distance = Math.max(1.0E-6D, geocentricLength);
            double angularRadius = Math.atan2(parameters.diameterKm() * 0.5D, distance * 1_000_000.0D);
            double altitude = Math.asin(Math.max(-1.0D, Math.min(1.0D, direction.y())));
            states.add(new CelestialBodyState(body.id,
                    body.parent == null ? null : body.parent.id,
                    geocentric, direction, distance, angularRadius, altitude,
                    Math.min(1.0D, 1.0D / Math.sqrt(distance)), 1.0D, 0.0D));
        }
        return states;
    }

    /** Same operation order as {@link CelestialMath#orbitalPosition}; only fixed trig is prepared. */
    private static CelestialVector primaryOrbitalPosition(double radius, double orbitalDays,
                                                           boolean retrograde,
                                                           double calendarDays, double phaseTurns,
                                                           PrimaryOrbitBasis basis) {
        double sign = retrograde ? -1.0D : 1.0D;
        double angle = sign * CelestialMath.TAU * calendarDays / orbitalDays
                + CelestialMath.TAU * phaseTurns;
        double nodeScale = radius * Math.cos(angle);
        double transverseScale = radius * Math.sin(angle);
        return new CelestialVector(basis.nodeCosine * nodeScale
                + basis.transverseX * transverseScale,
                basis.nodeSine * nodeScale + basis.transverseY * transverseScale,
                0.0D * nodeScale + basis.transverseZ * transverseScale);
    }

    private double orbitalPlaneInclinationRadians(CelestialPlanetSettings settings) {
        return Math.acos(Math.max(-1.0D, Math.min(1.0D, orbitalNormal(settings).z())));
    }

    private CelestialVector orbitalNormal(CelestialPlanetSettings settings) {
        double inclination = settings.parameters(this).inclinationRadians();
        if (parent == null) {
            return new CelestialVector(Math.sin(inclination) * Math.sin(ascendingNodeRadians),
                    -Math.sin(inclination) * Math.cos(ascendingNodeRadians),
                    Math.cos(inclination)).normalized();
        }
        CelestialMath.SatelliteOrbitBasis basis = inclination == inclinationRadians
                ? satelliteOrbitBasis
                : CelestialMath.satelliteOrbitBasis(orbitalReferenceNormalEcliptic,
                inclination, ascendingNodeRadians);
        CelestialVector atNode = CelestialMath.satelliteOrbitalPosition(1.0D, 4.0D,
                basis, retrograde, 0.0D, 0.0D);
        CelestialVector quarter = CelestialMath.satelliteOrbitalPosition(1.0D, 4.0D,
                basis, retrograde, 1.0D, 0.0D);
        return cross(atNode, quarter).normalized();
    }

    private static CelestialVector cross(CelestialVector first, CelestialVector second) {
        return new CelestialVector(first.y() * second.z() - first.z() * second.y(),
                first.z() * second.x() - first.x() * second.z(),
                first.x() * second.y() - first.y() * second.x());
    }

    public static void validateDefinitions() {
        if (values().length != 17) {
            throw new IllegalStateException("Expected all 17 TFCCaelum orbiting bodies, found " + values().length);
        }
        for (CelestialBodies body : values()) {
            if (!(body.diameterKm > 0.0D) || !(body.orbitalDays > 0.0D) || !(body.semiMajorMillionKm > 0.0D)
                    || !(body.synodicDays > 0.0D) || !(body.scaleFactor > 0.0D)
                    || !Double.isFinite(body.inclinationRadians)
                    || !Double.isFinite(body.ascendingNodeRadians)
                    || !Double.isFinite(body.axialTiltRadians) || (body.parent != null
                    && body.parent.ordinal() >= body.ordinal())
                    || body.orbitReferenceFrame == OrbitReferenceFrame.PARENT_EQUATOR
                    && body.parent == null) {
                throw new IllegalStateException("Invalid celestial body definition: " + body.name());
            }
            CelestialVector first = CelestialMath.orbitalPosition(body.semiMajorMillionKm, body.orbitalDays,
                    body.inclinationRadians, body.retrograde, 12345.678D);
            CelestialVector second = CelestialMath.orbitalPosition(body.semiMajorMillionKm, body.orbitalDays,
                    body.inclinationRadians, body.retrograde, 12345.678D);
            if (!first.equals(second)) {
                throw new IllegalStateException("Non-deterministic orbit definition: " + body.name());
            }
        }
    }

    private static CelestialVector rotateEcliptic(CelestialVector vector,
                                                   EclipticRotation rotation) {
        return new CelestialVector(vector.x() * rotation.cosine()
                - vector.y() * rotation.sine(),
                vector.x() * rotation.sine() + vector.y() * rotation.cosine(), vector.z());
    }

    private static CelestialVector equatorialPoleToEcliptic(double rightAscension, double declination) {
        double cosDeclination = Math.cos(declination);
        double x = cosDeclination * Math.cos(rightAscension);
        double equatorialY = cosDeclination * Math.sin(rightAscension);
        double equatorialZ = Math.sin(declination);
        double cosObliquity = CelestialMath.AXIAL_TILT_COS;
        double sinObliquity = CelestialMath.AXIAL_TILT_SIN;
        return new CelestialVector(x,
                equatorialY * cosObliquity + equatorialZ * sinObliquity,
                -equatorialY * sinObliquity + equatorialZ * cosObliquity).normalized();
    }

    public enum OrbitReferenceFrame {
        ECLIPTIC,
        PARENT_EQUATOR,
        J2000_EQUATORIAL_POLE
    }

    private static final class BodyCalculationScratch {
        private final double[] positionX = new double[ORDERED.length];
        private final double[] positionY = new double[ORDERED.length];
        private final double[] positionZ = new double[ORDERED.length];
        private final CelestialBodyParameters[] parameters =
                new CelestialBodyParameters[ORDERED.length];
        private final PrimaryOrbitBasis[] primaryOrbitBases =
                new PrimaryOrbitBasis[ORDERED.length];
        private CelestialPlanetSettings settingsIdentity;
        private CelestialOrbitalPhases phasesIdentity;
        private EclipticRotation forwardRotation;
        private EclipticRotation inverseRotation;

        private void prepare(CelestialPlanetSettings settings, CelestialOrbitalPhases phases) {
            if (settingsIdentity != settings) {
                for (CelestialBodies body : ORDERED) {
                    int index = body.ordinal();
                    CelestialBodyParameters parameters = settings.parameters(body);
                    this.parameters[index] = parameters;
                    primaryOrbitBases[index] = body.parent == null
                            ? PrimaryOrbitBasis.create(parameters.inclinationRadians(),
                            body.ascendingNodeRadians)
                            : null;
                }
                settingsIdentity = settings;
            }
            if (phasesIdentity != phases) {
                double turns = phases.earthTurns();
                forwardRotation = EclipticRotation.forTurns(turns);
                inverseRotation = EclipticRotation.forTurns(-turns);
                phasesIdentity = phases;
            }
        }
    }

    private static final class PrimaryOrbitBasis {
        private final double nodeCosine;
        private final double nodeSine;
        private final double transverseX;
        private final double transverseY;
        private final double transverseZ;

        private PrimaryOrbitBasis(double nodeCosine, double nodeSine,
                                  double transverseX, double transverseY,
                                  double transverseZ) {
            this.nodeCosine = nodeCosine;
            this.nodeSine = nodeSine;
            this.transverseX = transverseX;
            this.transverseY = transverseY;
            this.transverseZ = transverseZ;
        }

        private static PrimaryOrbitBasis create(double inclination, double ascendingNode) {
            double nodeCosine = Math.cos(ascendingNode);
            double nodeSine = Math.sin(ascendingNode);
            double cosineInclination = Math.cos(inclination);
            return new PrimaryOrbitBasis(nodeCosine, nodeSine,
                    -nodeSine * cosineInclination,
                    nodeCosine * cosineInclination, Math.sin(inclination));
        }
    }

    private record EclipticRotation(double cosine, double sine) {
        static EclipticRotation forTurns(double turns) {
            double angle = CelestialMath.TAU * turns;
            return new EclipticRotation(Math.cos(angle), Math.sin(angle));
        }
    }

    private static Map<ResourceLocation, CelestialBodies> createIdIndex() {
        Map<ResourceLocation, CelestialBodies> index = new HashMap<>();
        for (CelestialBodies body : ORDERED) {
            index.put(body.id, body);
        }
        return Map.copyOf(index);
    }
}
