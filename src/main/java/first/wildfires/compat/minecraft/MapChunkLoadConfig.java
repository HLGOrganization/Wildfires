package first.wildfires.compat.minecraft;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Configuration for the two pieces of protection against a synchronous chunk load stalling the server
 * thread: the map update guard, and the watcher that names the wait when something else still causes
 * one.
 *
 * <p>The keys are deliberately few. The guard has no tuning knob that could make it read a chunk
 * itself, and the watcher only reports - it never changes what the game does. The abort threshold a
 * first draft of this protection wanted is not here, and cannot be: refusing to wait for a chunk
 * future on the server thread leaves the caller with a future it still has to join, and throwing out
 * of the wait would take down the caller instead of the tick.
 */
public final class MapChunkLoadConfig {

    private static final ForgeConfigSpec COMMON_SPEC;
    private static final ForgeConfigSpec.BooleanValue GUARD;
    private static final ForgeConfigSpec.BooleanValue GUARD_LOG;
    private static final ForgeConfigSpec.BooleanValue WATCHER;
    private static final ForgeConfigSpec.IntValue WATCHER_WARN_MILLIS;
    private static final ForgeConfigSpec.IntValue WATCHER_COOLDOWN_MILLIS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Keeps a held map from making the server thread wait for a chunk.",
                        "A map update reads the chunks of the area it draws. On the server thread that read",
                        "waits for chunks that are only scheduled, which is what the watchdog kills the server",
                        "over. The guard defers the update to the next tick instead - the map still refreshes,",
                        "just not while a chunk of the window is missing.")
                .push("mapChunkLoadGuard");
        GUARD = builder.comment("Defer a map update whose chunks are not all loaded. Disable only to reproduce")
                .define("mapChunkLoadGuard", true);
        GUARD_LOG = builder.comment("Log each map that is deferred, once per map, to diagnose a map that will not",
                        "refresh. Off by default: the deferral is normal and needs no line.")
                .define("mapChunkLoadGuardLog", false);
        builder.pop();

        builder.comment("Reports - never prevents - a wait for a chunk on the server thread.",
                        "Any other mod, or vanilla, can still ask for a chunk that is not loaded; this only",
                        "says which chunk took how long, so the cause is visible in the log.")
                .push("chunkLoadWatcher");
        WATCHER = builder.comment("Watch synchronous chunk loads on the server thread. Reports only.")
                .define("syncChunkLoadWatcher", true);
        WATCHER_WARN_MILLIS = builder.comment("A wait at least this long is reported.")
                .defineInRange("syncChunkLoadWarnMs", 1000, 1, 600000);
        WATCHER_COOLDOWN_MILLIS = builder.comment("Shortest interval between two reports.")
                .defineInRange("syncChunkLoadWarnCooldownMs", 5000, 0, 3600000);
        builder.pop();

        COMMON_SPEC = builder.build();
    }

    private MapChunkLoadConfig() {
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC,
                "wildfires-map-chunk-load.toml");
    }

    public static boolean guardEnabled() {
        return read(GUARD, true);
    }

    public static boolean guardLogEnabled() {
        return read(GUARD_LOG, false);
    }

    public static boolean watcherEnabled() {
        return read(WATCHER, true);
    }

    public static int watcherWarnMillis() {
        return read(WATCHER_WARN_MILLIS, 1000);
    }

    public static int watcherCooldownMillis() {
        return read(WATCHER_COOLDOWN_MILLIS, 5000);
    }

    /**
     * Reads a value, falling back to its default while the config is not loaded yet. The guard is
     * reachable from a world that can exist before a common config is read - a self-test or a
     * structure line - and a guard that threw over its own setting would be worse than one that
     * assumed its default.
     */
    private static <T> T read(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            T loaded = value.get();
            return loaded == null ? fallback : loaded;
        } catch (IllegalStateException notLoadedYet) {
            return fallback;
        }
    }
}
