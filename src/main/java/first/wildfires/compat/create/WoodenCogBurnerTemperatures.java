package first.wildfires.compat.create;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The TFC temperatures shared by Wooden Cog, MoreBurners and the diesel
 * generator burner.
 *
 * Wooden Cog keeps these values in its common config. Reflection is used here
 * deliberately so Wildfires can still compile and load when Wooden Cog is not
 * present; the fallback values are the values shipped by the integration pack.
 */
public final class WoodenCogBurnerTemperatures {

    private static final String CONFIG_CLASS =
            "net.chauvedev.woodencog.config.WoodenCogCommonConfigs";
    private static final Map<BlockEntity, Float> CURRENT_TEMPERATURES = new WeakHashMap<>();

    private static final float NONE = read("BLAZE_BURNER_NONE", 0.0F);
    private static final float SMOULDERING = read("BLAZE_BURNER_SMOULDERING", 80.0F);
    private static final float FADING = read("BLAZE_BURNER_FADING", 750.0F);
    private static final float KINDLED = read("BLAZE_BURNER_KINDLED", 1250.0F);
    private static final float SEETHING = read("BLAZE_BURNER_SEETHING", 2300.0F);

    private WoodenCogBurnerTemperatures() {
    }

    /**
     * Returns the TFC target temperature for the two useful burner modes.
     * Lower Create states are not active heating modes for these burners.
     */
    public static float targetForHeatLevel(BlazeBurnerBlock.HeatLevel heatLevel) {
        return switch (heatLevel) {
            case NONE -> NONE;
            case SMOULDERING, FADING -> NONE;
            case KINDLED -> KINDLED;
            case SEETHING -> SEETHING;
        };
    }

    /**
     * Advances a burner temperature using the same device-temperature
     * adjustment used by a TFC charcoal forge without bellows.
     */
    public static synchronized float graduallyApproach(BlockEntity blockEntity, float targetTemperature) {
        float currentTemperature = CURRENT_TEMPERATURES.getOrDefault(blockEntity, 0.0F);
        float nextTemperature = HeatCapability.adjustDeviceTemp(
                currentTemperature,
                targetTemperature,
                0,
                false
        );
        if (nextTemperature <= 0.0F) {
            CURRENT_TEMPERATURES.remove(blockEntity);
            return 0.0F;
        }
        CURRENT_TEMPERATURES.put(blockEntity, nextTemperature);
        return nextTemperature;
    }

    private static float read(String fieldName, float fallback) {
        try {
            Class<?> configClass = Class.forName(CONFIG_CLASS);
            Field field = configClass.getField(fieldName);
            Object configValue = field.get(null);
            Method get = configValue.getClass().getMethod("get");
            Object value = get.invoke(configValue);
            if (value instanceof Number number && Float.isFinite(number.floatValue())) {
                return number.floatValue();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Wooden Cog is optional in development and on servers that only
            // use the gas burner integration. Use the pack defaults there.
        }
        return fallback;
    }
}
