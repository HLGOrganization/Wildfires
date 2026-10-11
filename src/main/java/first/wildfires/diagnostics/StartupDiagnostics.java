package first.wildfires.diagnostics;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/** Records a compact startup timeline in the normal game log. */
public final class StartupDiagnostics {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MOD_LOAD_STARTED_AT = System.nanoTime();

    private StartupDiagnostics() {
    }

    public static void commonMark(String stage) {
        mark("common", stage);
    }

    public static void clientMark(String stage) {
        mark("client", stage);
    }

    public static void serverMark(String stage) {
        mark("server", stage);
    }

    public static void clientCompleted(String stage, long startedAt) {
        completed("client", stage, startedAt);
    }

    public static void serverCompleted(String stage, long startedAt) {
        completed("server", stage, startedAt);
    }

    private static void mark(String side, String stage) {
        LOGGER.info("[Wildfires startup/{}] {} at {} ms since Wildfires loaded", side, stage, elapsedMillis(MOD_LOAD_STARTED_AT));
    }

    public static long now() {
        return System.nanoTime();
    }

    /**
     * Reports how many network guards are switched on. The mixin plugin has already dropped the
     * guards whose mod is not installed, so without this line a guard that does nothing looks
     * exactly like a guard that works.
     *
     * @param modByGuard    every guard and the mod it patches
     * @param droppedByPlugin guards the plugin dropped while the mixin config was prepared
     * @param modPresent    whether a mod id is installed
     */
    public static void networkGuards(Map<String, String> modByGuard, Map<String, String> droppedByPlugin,
                                     Predicate<String> modPresent) {
        Map<String, String> guards = new TreeMap<>(modByGuard);
        List<String> absent = new ArrayList<>();
        for (Map.Entry<String, String> guard : guards.entrySet()) {
            if (!modPresent.test(guard.getValue())) {
                absent.add(guard.getKey() + " -> " + guard.getValue());
            }
        }
        LOGGER.info("[Wildfires network] {}/{} network guards armed, {} not applied because the mod is missing",
                guards.size() - absent.size(), guards.size(), absent.size());
        if (!absent.isEmpty()) {
            LOGGER.info("[Wildfires network] mod not installed: {}", String.join(", ", absent));
        }
        if (!droppedByPlugin.isEmpty()) {
            LOGGER.info("[Wildfires network] dropped by the mixin plugin before target lookup: {}",
                    String.join(", ", new TreeMap<>(droppedByPlugin).keySet()));
        }
    }

    private static void completed(String side, String stage, long startedAt) {
        LOGGER.info("[Wildfires startup/{}] {} completed in {} ms", side, stage, elapsedMillis(startedAt));
    }

    public static String elapsedSeconds(long startedAt) {
        return String.format(Locale.ROOT, "%.3f", (System.nanoTime() - startedAt) / 1_000_000_000D);
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
