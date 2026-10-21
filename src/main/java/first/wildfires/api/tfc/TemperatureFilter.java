package first.wildfires.api.tfc;

import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * One entry of the attribute filter's temperature condition list.
 *
 * <p>Two flavours share the same NBT entry (the {@code TemperatureFilters} string list):
 * <ul>
 *   <li>numeric expressions, e.g. {@code ">=800"} written by hand in the text box;</li>
 *   <li>preset tokens, {@code "workable"} / {@code "weldable"} / {@code "any"}, which delegate to
 *       TFC's own {@link IHeat#canWork()} / {@link IHeat#canWeld()} instead of comparing numbers,
 *       so filtering agrees with TFC's tooltips and other mods by construction.</li>
 * </ul>
 */
public class TemperatureFilter {

    /** Temperature is at or above the item's own forging (working) temperature. */
    public static final String WORKABLE = "workable";
    /** Temperature is at or above the item's own welding temperature. */
    public static final String WELDABLE = "weldable";
    /** Ignores temperature entirely: always true. */
    public static final String ANY = "any";

    private final String operator;
    private final float value;
    private final Predicate<ItemStack> preset;

    private TemperatureFilter(String operator, float value, Predicate<ItemStack> preset) {
        this.operator = operator;
        this.value = value;
        this.preset = preset;
    }

    public TemperatureFilter(String operator, float value) {
        this(operator, value, null);
    }

    /**
     * @return a filter for one of the preset tokens, or {@code null} when {@code token} is not a preset.
     *         {@code null} means "unknown token" and callers are expected to ignore the entry, which keeps
     *         saves written by other/newer versions loadable.
     */
    public static TemperatureFilter preset(String token) {
        if (token == null) {
            return null;
        }
        return switch (token) {
            // canWork()/canWeld() are IHeat defaults; an item without TFC heat has no IHeat at all and
            // is therefore rejected here.
            case WORKABLE -> new TemperatureFilter(null, 0f, item -> {
                IHeat heat = HeatCapability.get(item);
                return heat != null && heat.canWork();
            });
            case WELDABLE -> new TemperatureFilter(null, 0f, item -> {
                IHeat heat = HeatCapability.get(item);
                return heat != null && heat.canWeld();
            });
            case ANY -> new TemperatureFilter(null, 0f, item -> true);
            default -> null;
        };
    }

    public boolean matches(ItemStack stack) {
        if (preset != null) {
            return preset.test(stack);
        }
        // Numeric expressions keep reading the plain temperature, exactly as before.
        float temp = HeatCapability.getTemperature(stack);
        return switch (operator) {
            case ">=" -> temp >= value;
            case ">" -> temp > value;
            case "<=" -> temp <= value;
            case "<" -> temp < value;
            case "==", "=" -> Math.abs(temp - value) < 0.001f;
            default -> false;
        };
    }
}
