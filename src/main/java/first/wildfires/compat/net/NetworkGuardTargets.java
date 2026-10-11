package first.wildfires.compat.net;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <h2>What this is</h2>
 * The table that maps every network guard in {@code first.wildfires.mixin.network} to the mod it
 * patches. {@code first.wildfires.mixin.WildfiresMixinPlugin} reads it while Mixin prepares
 * {@code wildfires.mixins.json}: a guard whose mod is not installed is dropped before Mixin resolves
 * its target, so an absent mod can never produce a "target was not found" message.
 * <h2>Why the mod ids are written out instead of being derived from the target class</h2>
 * Deciding whether to apply a mixin may not load the target class - Mixin resolves the declared
 * targets while it prepares the config, long before any mod is constructed, and loading a target
 * that early is itself an error. The mod id therefore has to be a constant, and every id below was
 * read from the {@code META-INF/mods.toml} of the jar that owns the target, never guessed from a
 * file name.
 * <h2>Why the guards are also {@code @Pseudo}</h2>
 * This table only answers "is the mod installed". A guard whose target class is missing (a mod
 * update that renamed it, or a client-only target on a dedicated server) is covered by the
 * {@code @Pseudo} on each guard, which turns a missing target into a debug-level skip. The two
 * mechanisms are independent on purpose: {@code @Pseudo} is also what lets the guards compile at
 * all, because the annotation processor would otherwise reject a target that is not on the compile
 * classpath.
 * <h2>Why the table is keyed by simple name</h2>
 * Mixin hands {@code shouldApplyMixin} the fully qualified mixin name, so only names below
 * {@link #GUARD_PACKAGE} are looked up; a mixin of the same simple name elsewhere under
 * {@code first.wildfires.mixin} can never match.
 * <h2>Fail open</h2>
 * {@link #requiredMod(String)} answers {@code null} for anything it does not recognise, and the
 * plugin then applies the mixin as it always did. That direction matters: a miss here means a guard
 * stays switched off for a mod that is installed (the network access survives), while the opposite
 * default could apply a guard to a mod that is absent. A missing row is therefore a silent hole,
 * which is why {@code NetworkGuardTargetsSelfTest} fails the build when the guards listed in
 * {@code wildfires.mixins.json} and this table disagree in either direction.
 */
public final class NetworkGuardTargets {

    /** The package the network guards live in; names outside it are never treated as guards. */
    public static final String GUARD_PACKAGE = "first.wildfires.mixin.network";

    private static final Map<String, String> MOD_BY_GUARD;

    /** Guards the plugin dropped, filled while Mixin prepares the config; read once for the log. */
    private static final Map<String, String> DROPPED = new ConcurrentHashMap<>();

    static {
        Map<String, String> guards = new LinkedHashMap<>();
        guards.put("AdAstraStationsMixin", "ad_astra");
        guards.put("AlexsCavesConflictsMixin", "alexscaves");
        guards.put("CitadelPatreonMixin", "citadel");
        guards.put("ExposureSupportersMixin", "exposure");
        guards.put("FancyToastsCreditsMixin", "fancytoasts");
        guards.put("ImmersiveEngineeringContributorsMixin", "immersiveengineering");
        guards.put("KiwiContributorsMixin", "kiwi");
        guards.put("KubeJsUpdateMixin", "kubejs");
        guards.put("PlaceboTrailsMixin", "placebo");
        guards.put("PlaceboWingsMixin", "placebo");
        guards.put("SteamRailsDevCapeMixin", "railways");
        guards.put("SupplementariesCreditsMixin", "supplementaries");
        MOD_BY_GUARD = Collections.unmodifiableMap(guards);
    }

    private NetworkGuardTargets() {
    }

    /**
     * The mod id that must be installed for {@code mixinClassName} to be applied, or {@code null}
     * when the class is not a known network guard.
     */
    public static String requiredMod(String mixinClassName) {
        if (mixinClassName == null || !mixinClassName.startsWith(GUARD_PACKAGE + ".")) {
            return null;
        }
        return MOD_BY_GUARD.get(simpleName(mixinClassName));
    }

    /** Every guard and the mod it patches, in declaration order. */
    public static Map<String, String> all() {
        return MOD_BY_GUARD;
    }

    /** Records a guard the plugin dropped, so the start-up log can name it. */
    public static void recordDropped(String mixinClassName, String modId) {
        DROPPED.put(simpleName(mixinClassName), modId);
    }

    /** The guards dropped so far, keyed by simple name and sorted for a stable log line. */
    public static Map<String, String> dropped() {
        return new TreeMap<>(DROPPED);
    }

    private static String simpleName(String mixinClassName) {
        int lastDot = mixinClassName.lastIndexOf('.');
        return lastDot < 0 ? mixinClassName : mixinClassName.substring(lastDot + 1);
    }
}
