/*
 * Adapted from Caelum `1.20.1-2.0.0.0` (`config.ClientConfig`, star option set) and from
 * TFCCaelum 1.2 `tfccaelum/Config`.
 * SPDX-License-Identifier: MIT AND LicenseRef-TFCCaelum-BSD
 * (see third_party/caelum/1.20.1-2.0.0.0/PROVENANCE.md and third_party/tfc-caelum/1.2/PROVENANCE.md).
 * Wildfires modifications: the retained option set moves to Forge 1.20.1 config with deprecated
 * compatibility keys kept readable, and the upstream keys that had no read point (sun blindness,
 * blend, orbit/distance factors) stay recorded without gaining behaviour.
 */
package first.wildfires.celestial;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/** Celestial gameplay and visual configuration kept separate from TFE climate settings. */
public final class CelestialConfig {

    public enum StarsMode { CUSTOM, VANILLA, NONE }
    public enum AuroraMode { POLAR_NIGHT, LEGACY_GLOBAL, DISABLED }
    public enum SkyMode { AUTO, WILDFIRES, SHADER_NATIVE }

    private static final ForgeConfigSpec SERVER_SPEC;
    private static final ForgeConfigSpec CLIENT_SPEC;

    private static final ForgeConfigSpec.DoubleValue SYNODIC_DAYS;
    private static final ForgeConfigSpec.DoubleValue ANOMALISTIC_DAYS;
    private static final ForgeConfigSpec.DoubleValue NODAL_YEARS;
    private static final ForgeConfigSpec.DoubleValue LUNAR_INCLINATION;
    private static final ForgeConfigSpec.BooleanValue BLOOD_MOON_SURFACE_MONSTERS;
    private static final ForgeConfigSpec.DoubleValue BLOOD_MOON_SPAWN_MULTIPLIER;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLOOD_MOON_SURFACE_MONSTER_IDS;
    private static final ForgeConfigSpec.BooleanValue DEPRECATED_SUN_BLINDNESS;
    private static final ForgeConfigSpec.EnumValue<CelestialRuntimeSettings.LunarPeriodPreset> LUNAR_PERIOD_PRESET;
    private static final Map<CelestialBodies, ConfiguredBody> CONFIGURABLE_BODIES;
    private static final ConfiguredEarth CONFIGURED_EARTH;

    private static final ForgeConfigSpec.EnumValue<StarsMode> STARS_MODE;
    private static final ForgeConfigSpec.EnumValue<SkyMode> SKY_MODE;
    private static final ForgeConfigSpec.DoubleValue MAX_MAGNITUDE;
    private static final ForgeConfigSpec.DoubleValue STAR_BRIGHTNESS;
    private static final ForgeConfigSpec.BooleanValue STAR_COLORS;
    private static final ForgeConfigSpec.DoubleValue STAR_SIZE;
    private static final ForgeConfigSpec.BooleanValue PLANETS;
    private static final ForgeConfigSpec.BooleanValue RAINBOW;
    private static final ForgeConfigSpec.EnumValue<AuroraMode> AURORA_MODE;
    private static final ForgeConfigSpec.IntValue AURORA_BANDS;
    private static final ForgeConfigSpec.DoubleValue SUN_SCALE;
    private static final ForgeConfigSpec.DoubleValue MOON_SCALE;
    private static final ForgeConfigSpec.DoubleValue PLANET_SCALE;
    private static volatile CelestialRuntimeSettings cachedServerSettings;
    private static volatile SurfaceMonsterIdFilter cachedSurfaceMonsterIdFilter;

    static {
        ForgeConfigSpec.Builder server = new ForgeConfigSpec.Builder();
        server.comment("Server-authoritative celestial periods and blood moon rules.").push("celestial");
        LUNAR_PERIOD_PRESET = server.comment("UNIFIED_16_13 is the new model; LEGACY_TFCCAELUM reproduces the old mixed period semantics; CUSTOM reads the numeric values below.")
                .defineEnum("lunarPeriodPreset", CelestialRuntimeSettings.LunarPeriodPreset.UNIFIED_16_13);
        SYNODIC_DAYS = server.comment("Full-to-full moon cycle in TFC calendar days.")
                .defineInRange("synodicDays", CelestialMath.SYNODIC_DAYS, 1.0D, 10000.0D);
        ANOMALISTIC_DAYS = server.comment("Perigee-to-perigee cycle in TFC calendar days.")
                .defineInRange("anomalisticDays", CelestialMath.ANOMALISTIC_DAYS, 1.0D, 10000.0D);
        NODAL_YEARS = server.comment("Lunar node precession in TFC calendar years.")
                .defineInRange("nodalYears", CelestialMath.NODAL_YEARS, 0.1D, 10000.0D);
        LUNAR_INCLINATION = server.comment("Lunar orbit inclination relative to the ecliptic, in degrees.")
                .defineInRange("lunarInclinationDegrees", 5.14D, 0.0D, 90.0D);
        SUN_SCALE = server.comment("Authoritative visual scale of the Sun and its square eclipse pixel body.")
                .defineInRange("sunScale", CelestialDiscGeometry.DEFAULT_SUN_SCALE, 0.01D, 100.0D);
        MOON_SCALE = server.comment("Authoritative visual scale of the Moon and its square eclipse pixel body.")
                .defineInRange("moonScale", CelestialDiscGeometry.DEFAULT_MOON_SCALE, 0.01D, 100.0D);
        BLOOD_MOON_SURFACE_MONSTERS = server
                .comment("Allow TFC-tagged vanilla monsters on the surface while a visible blood moon is active.")
                .define("bloodMoonSurfaceMonsters", true);
        BLOOD_MOON_SPAWN_MULTIPLIER = server.comment("Local mob cap multiplier at maximum blood moon intensity.")
                .defineInRange("bloodMoonSpawnMultiplier", 3.0D, 1.0D, 100.0D);
        BLOOD_MOON_SURFACE_MONSTER_IDS = server
                .comment("Optional entity ids allowed by the blood-moon surface exception. An empty list keeps the full TFC VANILLA_MONSTERS tag range.")
                .defineListAllowEmpty("bloodMoonSurfaceMonsterIds", List.of(),
                        value -> value instanceof String id && ResourceLocation.tryParse(id) != null);
        DEPRECATED_SUN_BLINDNESS = server
                .comment("Deprecated compatibility key. TFCCaelum 1.2 never read this value and Wildfires does not add blindness.")
                .define("enableSunBlindness", false);
        CONFIGURABLE_BODIES = new EnumMap<>(CelestialBodies.class);
        server.comment("Unified physical parameters for Earth and Mercury through Pluto. Untouched legacy defaults are corrected at load time.")
                .push("planets");
        server.push("earth");
        CONFIGURED_EARTH = new ConfiguredEarth(
                server.defineInRange("diameterKm", CelestialBodies.EARTH_DIAMETER_KM, 1.0D, 1.0E9D),
                server.defineInRange("orbitalDays", CelestialBodies.EARTH_ORBITAL_DAYS, 1.0E-3D, 1.0E9D),
                server.defineInRange("semiMajorMillionKm", CelestialBodies.EARTH_SEMI_MAJOR_AXIS,
                        1.0E-6D, 1.0E12D));
        server.pop();
        CelestialBodies[] bodies = CelestialBodies.values();
        for (int index = 0; index < CelestialPlanetSettings.CONFIGURABLE_BODY_COUNT; index++) {
            CelestialBodies body = bodies[index];
            CelestialBodyParameters defaults = body.defaultParameters();
            server.push(body.name().toLowerCase(Locale.ROOT));
            ForgeConfigSpec.DoubleValue diameter = server.defineInRange("diameterKm", defaults.diameterKm(),
                    1.0D, 1.0E9D);
            ForgeConfigSpec.DoubleValue orbitalDays = server.defineInRange("orbitalDays", defaults.orbitalDays(),
                    1.0E-3D, 1.0E9D);
            ForgeConfigSpec.DoubleValue semiMajor = server.defineInRange("semiMajorMillionKm",
                    defaults.semiMajorMillionKm(), 1.0E-6D, 1.0E12D);
            ForgeConfigSpec.DoubleValue synodicDays = server.defineInRange("synodicDays", defaults.synodicDays(),
                    1.0E-3D, 1.0E9D);
            ForgeConfigSpec.DoubleValue inclination = server.defineInRange("inclinationDegrees",
                    Math.toDegrees(defaults.inclinationRadians()), 0.0D, 180.0D);
            CONFIGURABLE_BODIES.put(body,
                    new ConfiguredBody(diameter, orbitalDays, semiMajor, synodicDays, inclination));
            server.pop();
        }
        server.pop();
        server.pop();
        SERVER_SPEC = server.build();

        ForgeConfigSpec.Builder client = new ForgeConfigSpec.Builder();
        client.push("celestial");
        SKY_MODE = client.comment("AUTO uses Wildfires without an active shader pack and returns the overworld sky to unknown shader packs. WILDFIRES and SHADER_NATIVE force either owner.")
                .defineEnum("sky.mode", SkyMode.AUTO);
        STARS_MODE = client.defineEnum("stars.mode", StarsMode.CUSTOM);
        MAX_MAGNITUDE = client.defineInRange("stars.maxMagnitude", 5.0D, -30.0D, 30.0D);
        STAR_BRIGHTNESS = client.defineInRange("stars.brightness", 2.0D, 0.0D, 100.0D);
        STAR_COLORS = client.define("stars.colors", true);
        STAR_SIZE = client.defineInRange("stars.size", 1.0D, 0.0D, 100.0D);
        PLANETS = client.define("enablePlanets", true);
        RAINBOW = client.define("enableRainbow", true);
        AURORA_MODE = client.defineEnum("aurora.mode", AuroraMode.POLAR_NIGHT);
        AURORA_BANDS = client.defineInRange("aurora.maxBands", 3, 0, 3);
        PLANET_SCALE = client.defineInRange("planetScale", 1.0D, 0.01D, 100.0D);
        client.pop();
        CLIENT_SPEC = client.build();
    }

    private CelestialConfig() {
    }

    public static void register() {
        ModLoadingContext context = ModLoadingContext.get();
        context.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC, "wildfires-celestial-server.toml");
        context.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, "wildfires-celestial-client.toml");
    }

    public static CelestialRuntimeSettings serverSettings() {
        CelestialRuntimeSettings cached = cachedServerSettings;
        if (cached != null) {
            return cached;
        }
        cached = readServerSettings();
        cachedServerSettings = cached;
        return cached;
    }

    static void refreshServerSettings() {
        cachedServerSettings = readServerSettings();
        cachedSurfaceMonsterIdFilter = SurfaceMonsterIdFilter.from(
                BLOOD_MOON_SURFACE_MONSTER_IDS.get());
    }

    private static CelestialRuntimeSettings readServerSettings() {
        List<CelestialBodyParameters> planets = new ArrayList<>(CelestialPlanetSettings.CONFIGURABLE_BODY_COUNT);
        CelestialBodies[] bodies = CelestialBodies.values();
        for (int index = 0; index < CelestialPlanetSettings.CONFIGURABLE_BODY_COUNT; index++) {
            CelestialBodies body = bodies[index];
            planets.add(body.migrateLegacyDefaults(CONFIGURABLE_BODIES.get(body).parameters()));
        }
        return new CelestialRuntimeSettings(SYNODIC_DAYS.get(), ANOMALISTIC_DAYS.get(), NODAL_YEARS.get(),
                Math.toRadians(LUNAR_INCLINATION.get()), BLOOD_MOON_SURFACE_MONSTERS.get(),
                BLOOD_MOON_SPAWN_MULTIPLIER.get(), SUN_SCALE.get(), MOON_SCALE.get(),
                LUNAR_PERIOD_PRESET.get(),
                new CelestialPlanetSettings(planets, CONFIGURED_EARTH.diameterKm().get(),
                        CONFIGURED_EARTH.orbitalDays().get(), CONFIGURED_EARTH.semiMajorMillionKm().get()));
    }

    public static List<? extends String> bloodMoonSurfaceMonsterIds() {
        return BLOOD_MOON_SURFACE_MONSTER_IDS.get();
    }

    static SurfaceMonsterIdFilter surfaceMonsterIdFilter() {
        SurfaceMonsterIdFilter cached = cachedSurfaceMonsterIdFilter;
        if (cached == null) {
            cached = SurfaceMonsterIdFilter.from(BLOOD_MOON_SURFACE_MONSTER_IDS.get());
            cachedSurfaceMonsterIdFilter = cached;
        }
        return cached;
    }

    static boolean isServerSpec(net.minecraftforge.fml.config.ModConfig config) {
        return config.getSpec() == SERVER_SPEC;
    }

    public static StarsMode starsMode() { return STARS_MODE.get(); }
    public static SkyMode skyMode() { return SKY_MODE.get(); }
    public static double maxMagnitude() { return MAX_MAGNITUDE.get(); }
    public static double starBrightness() { return STAR_BRIGHTNESS.get(); }
    public static boolean starColors() { return STAR_COLORS.get(); }
    public static double starSize() { return STAR_SIZE.get(); }
    public static boolean planets() { return PLANETS.get(); }
    public static boolean rainbow() { return RAINBOW.get(); }
    public static AuroraMode auroraMode() { return AURORA_MODE.get(); }
    public static int auroraBands() { return AURORA_BANDS.get(); }
    public static double planetScale() { return PLANET_SCALE.get(); }

    private record ConfiguredBody(ForgeConfigSpec.DoubleValue diameterKm,
                                  ForgeConfigSpec.DoubleValue orbitalDays,
                                  ForgeConfigSpec.DoubleValue semiMajorMillionKm,
                                  ForgeConfigSpec.DoubleValue synodicDays,
                                  ForgeConfigSpec.DoubleValue inclinationDegrees) {
        private CelestialBodyParameters parameters() {
            return new CelestialBodyParameters(diameterKm.get(), orbitalDays.get(), semiMajorMillionKm.get(),
                    synodicDays.get(), Math.toRadians(inclinationDegrees.get()));
        }
    }

    record SurfaceMonsterIdFilter(boolean unrestricted, Set<ResourceLocation> canonicalIds) {
        SurfaceMonsterIdFilter {
            canonicalIds = Set.copyOf(canonicalIds);
        }

        static SurfaceMonsterIdFilter from(List<? extends String> configured) {
            Objects.requireNonNull(configured, "configured");
            if (configured.isEmpty()) {
                return new SurfaceMonsterIdFilter(true, Set.of());
            }
            Set<ResourceLocation> canonical = new LinkedHashSet<>();
            for (String text : configured) {
                if (text == null) {
                    continue;
                }
                ResourceLocation parsed = ResourceLocation.tryParse(text);
                if (parsed != null && parsed.toString().equals(text)) {
                    canonical.add(parsed);
                }
            }
            return new SurfaceMonsterIdFilter(false, canonical);
        }

        boolean allows(ResourceLocation id) {
            return unrestricted || canonicalIds.contains(Objects.requireNonNull(id, "id"));
        }
    }

    private record ConfiguredEarth(ForgeConfigSpec.DoubleValue diameterKm,
                                   ForgeConfigSpec.DoubleValue orbitalDays,
                                   ForgeConfigSpec.DoubleValue semiMajorMillionKm) {
    }
}
