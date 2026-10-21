package first.wildfires.compat.tfc;

import net.dries007.tfc.config.TFCConfig;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Turns "this device heats M times faster" into the rate parameter TFC's
 * {@code HeatCapability.addTemp} actually takes.
 *
 * <h2>Why the rate parameter cannot simply be multiplied</h2>
 *
 * <p>The increment TFC adds each tick is
 * <pre>
 *     increment = ((itemCoolingModifier - 1) + b * itemHeatingModifier) / heatCapacity
 * </pre>
 * and the new temperature is {@code min(temperature + increment, fuelTemperature)}. The parameter that
 * belongs to a device is {@code b} - the charcoal forge passes 3.0 through the two-argument overload, the
 * crucible passes {@code 2 + 0.0025 * temperature} - while the cooling term does not scale with it at all.
 * Writing {@code b * M} would therefore raise the increment by something less than M times, by exactly the
 * cooling term that was left unscaled.
 *
 * <p>So the increment is scaled instead, and {@code b} is solved back out of it:
 * <pre>
 *     bNew = (M * ((cooling - 1) + b * heating) - (cooling - 1)) / heating
 * </pre>
 * With the shipped modifiers {@code cooling = 0.2} and {@code heating = 1.0} (so {@code k = -0.8}) this gives
 * the three shipped factors exactly:
 * <pre>
 *     forge      b = 3.0            M = 1.5  ->  1.5 * 2.2 - (-0.8) =  4.1
 *     crucible   b = 2 + 0.0025T    M = 2.0  ->  2.0 * (1.2 + 0.0025T) + 0.8 = 3.2 + 0.005T
 *     corundum   b = 2 + 0.0025T    M = 2.5  ->  2.5 * (1.2 + 0.0025T) + 0.8 = 3.8 + 0.00625T
 * </pre>
 * Both factors are read live from TFC's own config, so a pack that changes them still gets exactly
 * M times the increment rather than a number that was only right for the defaults.
 *
 * <h2>Why the temperature ceiling is untouched</h2>
 *
 * <p>{@code addTemp} also clamps the result to its first argument, the temperature the device is handing
 * over. Scaling {@code b} leaves that argument alone on both call sites, so nothing here can raise a cap:
 * an item still stops exactly at the forge's or the crucible's own temperature.
 */
public final class DeviceHeatRate {

    /**
     * The rate parameter the two-argument {@code HeatCapability.addTemp(IHeat, float)} uses internally:
     * its body is {@code addTemp(heat, temperature, 3.0f)}. The forge reaches this class through that
     * overload, so the number has to come from here rather than from the call site's bytecode.
     */
    public static final double TWO_ARGUMENT_RATE = 3.0D;

    /** Mirrors the shipped default of {@code TFCConfig.SERVER.itemHeatingModifier}. */
    private static final double FALLBACK_HEATING_MODIFIER = 1.0D;

    /** Mirrors the shipped default of {@code TFCConfig.SERVER.itemCoolingModifier}. */
    private static final double FALLBACK_COOLING_MODIFIER = 0.2D;

    private DeviceHeatRate() {
    }

    /**
     * Scales one device's rate parameter so the temperature increment comes out M times as large.
     *
     * @param b       the rate parameter this tick's call would have used.
     * @param heating {@code TFCConfig.SERVER.itemHeatingModifier}.
     * @param cooling {@code TFCConfig.SERVER.itemCoolingModifier}.
     * @param factor  how much faster the device should reach its ceiling: 1.5 is +50%.
     * @return the replacement rate parameter, or {@code b} itself when the arithmetic cannot be trusted.
     */
    public static float scale(double b, double heating, double cooling, double factor) {
        double k = cooling - 1.0D;
        double scaled = factor * (k + b * heating) - k;
        double result = scaled / heating;

        // A pack is free to set the heating modifier to zero or to something that makes the expression
        // non-finite. Falling back to the untouched parameter keeps TFC's own behaviour instead of feeding
        // setTemperatureIfWarmer a NaN that would poison the item's heat for good.
        if (heating <= 0.0D || !Double.isFinite(result) || result < 0.0D) {
            return (float) b;
        }
        return (float) result;
    }

    /** {@code TFCConfig.SERVER.itemHeatingModifier}, or its shipped default while the config is not loaded. */
    public static double heatingModifier() {
        Double value = readConfigValue(() -> TFCConfig.SERVER.itemHeatingModifier);
        return value == null || !Double.isFinite(value) ? FALLBACK_HEATING_MODIFIER : value;
    }

    /** {@code TFCConfig.SERVER.itemCoolingModifier}, or its shipped default while the config is not loaded. */
    public static double coolingModifier() {
        Double value = readConfigValue(() -> TFCConfig.SERVER.itemCoolingModifier);
        return value == null || !Double.isFinite(value) ? FALLBACK_COOLING_MODIFIER : value;
    }

    /**
     * Reads one of TFC's server config values without letting an unloaded config take the tick down.
     * {@code ForgeConfigSpec.DoubleValue#get()} answers null while the config file has not been read yet,
     * and TFC's own {@code isServerConfigLoaded} guard is used as well so a
     * {@code NullPointerException} cannot escape from inside a block entity tick.
     */
    private static Double readConfigValue(java.util.function.Supplier<ForgeConfigSpec.DoubleValue> field) {
        try {
            if (!TFCConfig.isServerConfigLoaded()) {
                return null;
            }
            return field.get().get();
        } catch (RuntimeException notLoadedYet) {
            return null;
        }
    }
}
