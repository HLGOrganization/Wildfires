package first.wildfires.compat.net;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

/**
 * <h2>Why this exists</h2>
 * Several mods in this pack contact a remote host during start-up for data that is either purely
 * cosmetic (supporter lists, credits, contributor capes, update notices) or that already ships
 * inside the mod's own jar (Citadel's patreon list, Alex's Caves' world generation conflict list).
 * None of it has to leave the machine, and every one of those requests is a start-up delay, a
 * privacy leak and a failure mode on an offline network.
 * <p>
 * Mods that expose a config switch are switched off there instead (see the pack's config files).
 * The ones that do not are cut at the call site by {@code first.wildfires.mixin.network.*}.
 * <h2>Why the mixins use string targets</h2>
 * The guards are written as {@code @Pseudo @Mixin(targets = "<class name>", remap = false)} so that
 * they need no compile-time dependency on the target mod and no target type ever appears in a
 * handler signature. {@code @Pseudo} is what makes the missing class acceptable to the annotation
 * processor and makes Mixin skip the guard when the mod is not installed at all. That keeps every
 * guard inside the single shared {@code wildfires.mixins.json} and means a mod that is missing from
 * the development environment cannot break the build.
 * <h2>Never worse than being offline</h2>
 * Wherever a mod falls back to a resource bundled in its own jar, the guard hands back that
 * resource, so the result is identical to the mod's own "network unreachable" path. Where the mod
 * cannot fall back, the guard returns exactly what its own failure path returns (usually
 * {@code null}), never a value it does not already expect.
 */
public final class NetworkGuard {

    /** Message attached to every denied request, so its origin is obvious in other mods' logs. */
    public static final String REASON = "wildfires: network access disabled";

    private NetworkGuard() {
    }

    /**
     * Builds the exception that a blocked request throws. It is deliberately an
     * {@link UnknownHostException}: that is what a machine without a working route produces, so
     * the caller's existing offline handling runs unchanged.
     */
    public static UnknownHostException blocked(String url) {
        return new UnknownHostException(REASON + " (" + url + ")");
    }

    /**
     * Opens a resource that ships inside a mod jar, used to preserve bundled fallback data when the
     * remote copy is denied. Returns {@code null} when the resource is absent, which is the same
     * value the guarded callers already handle.
     */
    public static BufferedReader localResource(String path) {
        if (path == null) {
            return null;
        }
        ClassLoader loader = NetworkGuard.class.getClassLoader();
        InputStream stream = loader == null ? null : loader.getResourceAsStream(path);
        if (stream == null) {
            ClassLoader context = Thread.currentThread().getContextClassLoader();
            if (context != null && context != loader) {
                stream = context.getResourceAsStream(path);
            }
        }
        return stream == null ? null
                : new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
