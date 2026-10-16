/*
 * Adapted from TFCCaelum 1.2 `tfccaelum/Config` and `tfccaelum/util/Planets`.
 * SPDX-License-Identifier: LicenseRef-TFCCaelum-BSD (see third_party/tfc-caelum/1.2/PROVENANCE.md).
 * Wildfires modifications: the retained Earth and Mercury-through-Pluto parameter set becomes
 * server-authoritative and synchronized rather than static configuration fields.
 */
package first.wildfires.celestial;

import java.util.ArrayList;
import java.util.List;

/** Server-authoritative Earth and Mercury-through-Pluto parameters retained from TFCCaelum. */
public record CelestialPlanetSettings(List<CelestialBodyParameters> configurableBodies,
                                      double earthDiameterKm,
                                      double earthOrbitalDays,
                                      double earthSemiMajorMillionKm) {

    public static final int CONFIGURABLE_BODY_COUNT = 8;
    public static final CelestialPlanetSettings DEFAULT = defaultsFromDefinitions();

    public CelestialPlanetSettings {
        configurableBodies = List.copyOf(configurableBodies);
        if (configurableBodies.size() != CONFIGURABLE_BODY_COUNT) {
            throw new IllegalArgumentException("Expected " + CONFIGURABLE_BODY_COUNT
                    + " configurable planet definitions, got " + configurableBodies.size());
        }
        if (!(earthDiameterKm > 0.0D) || !(earthOrbitalDays > 0.0D) || !(earthSemiMajorMillionKm > 0.0D)
                || !Double.isFinite(earthDiameterKm) || !Double.isFinite(earthOrbitalDays)
                || !Double.isFinite(earthSemiMajorMillionKm)) {
            throw new IllegalArgumentException("Earth parameters must be finite and positive");
        }
    }

    public CelestialBodyParameters parameters(CelestialBodies body) {
        if (body.ordinal() < CONFIGURABLE_BODY_COUNT) {
            return configurableBodies.get(body.ordinal());
        }
        if (body == CelestialBodies.PERSEPHONE) {
            CelestialBodyParameters defaults = body.defaultParameters();
            return new CelestialBodyParameters(earthDiameterKm * 4.0D, earthOrbitalDays * 15000.0D,
                    defaults.semiMajorMillionKm(), earthOrbitalDays, defaults.inclinationRadians());
        }
        if (body == CelestialBodies.NEMESIS) {
            CelestialBodyParameters defaults = body.defaultParameters();
            return new CelestialBodyParameters(defaults.diameterKm(), earthOrbitalDays * 11100.0D,
                    defaults.semiMajorMillionKm(), earthOrbitalDays, defaults.inclinationRadians());
        }
        return body.defaultParameters();
    }

    public CelestialPlanetSettings with(CelestialBodies body, CelestialBodyParameters parameters) {
        if (body.ordinal() >= CONFIGURABLE_BODY_COUNT) {
            throw new IllegalArgumentException(body + " is not configurable in TFCCaelum");
        }
        List<CelestialBodyParameters> changed = new ArrayList<>(configurableBodies);
        changed.set(body.ordinal(), parameters);
        return new CelestialPlanetSettings(changed, earthDiameterKm, earthOrbitalDays, earthSemiMajorMillionKm);
    }

    public CelestialPlanetSettings withEarth(double diameterKm, double orbitalDays, double semiMajorMillionKm) {
        return new CelestialPlanetSettings(configurableBodies, diameterKm, orbitalDays, semiMajorMillionKm);
    }

    private static CelestialPlanetSettings defaultsFromDefinitions() {
        CelestialBodies[] bodies = CelestialBodies.values();
        List<CelestialBodyParameters> defaults = new ArrayList<>(CONFIGURABLE_BODY_COUNT);
        for (int index = 0; index < CONFIGURABLE_BODY_COUNT; index++) {
            defaults.add(bodies[index].defaultParameters());
        }
        return new CelestialPlanetSettings(defaults, CelestialBodies.EARTH_DIAMETER_KM,
                CelestialBodies.EARTH_ORBITAL_DAYS, CelestialBodies.EARTH_SEMI_MAJOR_AXIS);
    }
}
