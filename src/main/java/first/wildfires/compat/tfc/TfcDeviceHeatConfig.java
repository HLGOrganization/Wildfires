package first.wildfires.compat.tfc;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * How much faster Wildfires' own TFC heat devices reach the temperature they were already able to reach.
 *
 * <p>Each value is the factor on the temperature <em>increment</em> per tick, not on any stored rate: 1.5
 * means "+50% faster to the top". {@link DeviceHeatRate} converts the factor into the rate parameter TFC's
 * {@code HeatCapability.addTemp} takes, because the increment and that parameter are not proportional while
 * the cooling term is part of the sum.
 *
 * <p>No ceiling is configured here on purpose. TFC decides how hot a device may get - the forge hands its
 * own temperature to the items inside it and the crucible does the same - so a factor only changes when an
 * item arrives, never how far.
 *
 * <p>This file is new, so no existing key is renamed or moved: {@code wildfires-tfc-heat.toml} appears with
 * its three defaults on the first launch of this version.
 */
public final class TfcDeviceHeatConfig {

    private static final double FALLBACK_CHARCOAL_FORGE = 1.5D;
    private static final double FALLBACK_CRUCIBLE = 2.0D;
    private static final double FALLBACK_CORUNDUM_CRUCIBLE = 2.5D;

    /** The multiplier cannot be negative: a device that cools its contents is not what was asked for. */
    private static final double MIN_MULTIPLIER = 0.0D;
    private static final double MAX_MULTIPLIER = 100.0D;

    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.DoubleValue CHARCOAL_FORGE;
    private static final ForgeConfigSpec.DoubleValue CRUCIBLE;
    private static final ForgeConfigSpec.DoubleValue CORUNDUM_CRUCIBLE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Heat-up speed of Wildfires' TFC devices.",
                        "Each value multiplies the temperature gain per tick of that one block.",
                        "1.0 leaves TFC's own speed, 1.5 is +50%, 2.0 is +100%.",
                        "No temperature ceiling is changed: the forge and the crucible still stop at their own temperature.")
                .push("tfcDeviceHeatSpeed");

        CHARCOAL_FORGE = builder
                .comment("wildfires:unrestricted_charcoal_forge - the standalone charcoal stove.",
                         "TFC's own tfc:charcoal_forge is not affected by this value.")
                .defineInRange("unrestrictedCharcoalForgeMultiplier", FALLBACK_CHARCOAL_FORGE, MIN_MULTIPLIER, MAX_MULTIPLIER);

        CRUCIBLE = builder
                .comment("tfc:crucible - TFC's own crucible. The industrial crucible of Create Metallurgy is not affected.")
                .defineInRange("crucibleMultiplier", FALLBACK_CRUCIBLE, MIN_MULTIPLIER, MAX_MULTIPLIER);

        CORUNDUM_CRUCIBLE = builder
                .comment("wildfires:crucible - the corundum crucible.")
                .defineInRange("corundumCrucibleMultiplier", FALLBACK_CORUNDUM_CRUCIBLE, MIN_MULTIPLIER, MAX_MULTIPLIER);

        builder.pop();
        SPEC = builder.build();
    }

    private TfcDeviceHeatConfig() {
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC, "wildfires-tfc-heat.toml");
    }

    /** Read every tick, so only the config object is cached, never its value. */
    public static double unrestrictedCharcoalForgeMultiplier() {
        return value(CHARCOAL_FORGE, FALLBACK_CHARCOAL_FORGE);
    }

    public static double crucibleMultiplier() {
        return value(CRUCIBLE, FALLBACK_CRUCIBLE);
    }

    public static double corundumCrucibleMultiplier() {
        return value(CORUNDUM_CRUCIBLE, FALLBACK_CORUNDUM_CRUCIBLE);
    }

    private static double value(ForgeConfigSpec.DoubleValue configValue, double fallback) {
        try {
            return configValue.get();
        } catch (IllegalStateException | NullPointerException notLoadedYet) {
            return fallback;
        }
    }
}
