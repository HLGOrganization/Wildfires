package first.wildfires.compat.create;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * <p>Relocated on 2026-10-01: the two values moved into woodencog_wildfire's own
 * {@code woodencog-common.toml} under {@code [woodencog.fan]} (see
 * {@code net.chauvedev.woodencog.config.WoodenCogCommonConfigs}), and Wildfires no longer registers
 * this file, so {@code wildfires-fan-heat.toml} is no longer created or read. The class is kept -
 * with its fallbacks, which is what the accessors return while nothing is registered - so that the
 * mixin and the guard beside it still compile if they are ever enabled again.
 *
 * <p>The destruction ceilings Wildfires added on top of Wooden Cog's fan heating.
 *
 * <p>Wooden Cog discards an item only once a TFC heating recipe melts it. An item that has no such
 * recipe is therefore heated without limit and never destroyed, however hot it gets. Two ceilings
 * close that gap, because TFC has no "maximum temperature" field to reuse: a heat definition
 * carries only {@code heat_capacity}, {@code forging_temperature} and {@code welding_temperature}.
 *
 * <p>An item with a welding temperature is destroyed above {@code welding_temperature * multiplier}.
 * The multiplier cannot go below {@code 1.25}: every TFC metal melts at least {@code 1.2497 *} its
 * welding temperature, so anything lower destroys the metal before it can melt. That is why the
 * accepted range starts at {@code 1.25} rather than {@code 1.0}.
 */
public final class FanHeatConfig {

    private static final float FALLBACK_MULTIPLIER = 1.3F;
    private static final float FALLBACK_NO_RECIPE_CEILING = 1600.0F;

    /** Lowest multiplier that still lets every TFC metal melt, measured from the shipped recipes. */
    private static final double MIN_SAFE_MULTIPLIER = 1.25D;

    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.DoubleValue OVERHEAT_MULTIPLIER;
    private static final ForgeConfigSpec.DoubleValue NO_RECIPE_CEILING;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Fan heating destruction ceilings.").push("fanHeat");
        OVERHEAT_MULTIPLIER = builder
                .comment("An item with a welding temperature is destroyed once it passes",
                        "welding_temperature * this value.",
                        "Do not go below 1.25: the coldest TFC metal melt is 1.2497 * its welding",
                        "temperature, so a smaller value would destroy the metal before it can melt.",
                        "A metal that does have a heating recipe can never be destroyed early by any",
                        "value at or above that ratio, because the recipe is checked every tick.")
                .defineInRange("overheatMultiplier", 1.3D, 1.25D, 10.0D);
        NO_RECIPE_CEILING = builder
                .comment("An item that defines no welding temperature and has no heating recipe is destroyed",
                        "once it passes this temperature. Such an item would otherwise heat forever.",
                        "The highest TFC heating recipe needs 1540 C, so cooking and melting still happen",
                        "first. Set to 0 to let these items heat without limit.")
                .defineInRange("noRecipeCeiling", 1600.0D, 0.0D, 100000.0D);
        builder.pop();
        SPEC = builder.build();
    }

    private FanHeatConfig() {
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "wildfires-fan-heat.toml");
    }

    public static double minSafeMultiplier() {
        return MIN_SAFE_MULTIPLIER;
    }

    /** Read every tick, so only the config object is cached, never its value. */
    public static float overheatMultiplier() {
        try {
            return OVERHEAT_MULTIPLIER.get().floatValue();
        } catch (IllegalStateException notLoadedYet) {
            return FALLBACK_MULTIPLIER;
        }
    }

    public static float noRecipeCeiling() {
        try {
            return NO_RECIPE_CEILING.get().floatValue();
        } catch (IllegalStateException notLoadedYet) {
            return FALLBACK_NO_RECIPE_CEILING;
        }
    }
}