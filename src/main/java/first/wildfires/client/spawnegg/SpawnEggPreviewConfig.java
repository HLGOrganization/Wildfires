package first.wildfires.client.spawnegg;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * <h2>What this does</h2>
 * Tuning for the spawn-egg model preview that {@code SpawnEggItemRendererMixin} draws. The values
 * are the same five the standalone {@code entityspawnegg} mod exposed, kept under their original key
 * names so an existing {@code entityspawnegg.toml} reads across without an edit.
 * <h2>Why the file name changed</h2>
 * The standalone mod registered {@code entityspawnegg.toml}. This port registers
 * {@code wildfires-spawn-egg.toml} instead, because every other Wildfires config file is named after
 * Wildfires and a file called {@code entityspawnegg.toml} inside this jar would misrepresent which
 * mod owns it. The keys inside are unchanged.
 * <h2>Why the values are read through {@link #read}</h2>
 * The renderer runs on the client render thread from the first frame the inventory draws. A config
 * is not guaranteed to be loaded that early - a self-test or a resource-reload path can reach the
 * renderer first - and a renderer that threw over its own setting would leave a blank screen where
 * an item should be. Falling back to the shipped default keeps the egg visible.
 */
public final class SpawnEggPreviewConfig {

    private static final ForgeConfigSpec CLIENT_SPEC;
    private static final ForgeConfigSpec.DoubleValue MAX_ZOOM_RATE;
    private static final ForgeConfigSpec.DoubleValue MIN_ZOOM_RATE;
    private static final ForgeConfigSpec.DoubleValue TOTAL_ZOOM_RATE;
    private static final ForgeConfigSpec.DoubleValue ICON_ZOOM_RATE;
    private static final ForgeConfigSpec.DoubleValue ROTATION_ANGLE;
    private static final ForgeConfigSpec.DoubleValue ITEM_ZOOM_RATE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Draws the mob a spawn egg belongs to in place of the flat egg icon.",
                        "Applies to the inventory, the hotbar and every other slot the egg is drawn in.")
                .push("spawnEggPreview");

        MAX_ZOOM_RATE = builder.comment("Largest scale the fitted mob may be drawn at. A very small mob",
                        "is clamped here so it does not fill the whole slot.")
                .defineInRange("maxZoomRate", 0.38D, 0.0D, 2147483647.0D);

        MIN_ZOOM_RATE = builder.comment("Smallest scale the fitted mob may be drawn at. A very large mob is",
                        "clamped here so it does not shrink past recognition.")
                .defineInRange("minZoomRate", 0.1D, 0.0D, 2147483647.0D);

        TOTAL_ZOOM_RATE = builder.comment("Extra multiplier over the fitted scale, applied in every context.")
                .defineInRange("totalZoomRate", 1.0D, 0.0D, 2147483647.0D);

        ICON_ZOOM_RATE = builder.comment("Additional multiplier for the inventory icon context only.")
                .defineInRange("iconZoomRate", 1.0D, 0.0D, 2147483647.0D);

        ROTATION_ANGLE = builder.comment("Y rotation, in degrees, applied to the mob in the inventory icon",
                        "context so it is not seen dead-on.")
                .defineInRange("rotationAngle", -45.0D, 0.0D, 2147483647.0D);

        ITEM_ZOOM_RATE = builder.comment("Additional multiplier for the held and dropped contexts, which do",
                        "not receive the rotation above.")
                .defineInRange("itemZoomRate", 1.0D, 0.0D, 2147483647.0D);

        builder.pop();
        CLIENT_SPEC = builder.build();
    }

    private SpawnEggPreviewConfig() {
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC,
                "wildfires-spawn-egg.toml");
    }

    public static float maxZoomRate() {
        return read(MAX_ZOOM_RATE, 0.38D).floatValue();
    }

    public static float minZoomRate() {
        return read(MIN_ZOOM_RATE, 0.1D).floatValue();
    }

    public static float totalZoomRate() {
        return read(TOTAL_ZOOM_RATE, 1.0D).floatValue();
    }

    public static float iconZoomRate() {
        return read(ICON_ZOOM_RATE, 1.0D).floatValue();
    }

    public static float rotationAngle() {
        return read(ROTATION_ANGLE, -45.0D).floatValue();
    }

    public static float itemZoomRate() {
        return read(ITEM_ZOOM_RATE, 1.0D).floatValue();
    }

    /**
     * Reads a value, falling back to its default while the config is not loaded yet. A client config
     * is absent from the dedicated server entirely, and the renderer can be reached before the file
     * is read on the client, so the fallback is the normal path rather than an error path.
     */
    private static Double read(ForgeConfigSpec.ConfigValue<Double> value, double fallback) {
        try {
            Double loaded = value.get();
            return loaded == null ? fallback : loaded;
        } catch (IllegalStateException notLoadedYet) {
            return fallback;
        }
    }
}
