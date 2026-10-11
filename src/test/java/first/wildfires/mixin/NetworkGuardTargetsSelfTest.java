package first.wildfires.mixin;

import first.wildfires.compat.net.NetworkGuardTargets;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plain-Java checks for the table that decides whether a network guard is applied at all.
 *
 * <p>The table fails open on purpose - a guard it does not recognise is applied as before - so nothing at
 * run time can report a missing row: the guard would simply still be there, or, for a mod that is not
 * installed, still be dropped by the guard's own {@code @Pseudo}. That is the reason this file compares the
 * two lists against each other instead: the guards named in {@code wildfires.mixins.json} and the rows of
 * the table have to match in both directions, and every mod id is written out here a second time, exactly
 * as it was read from the {@code META-INF/mods.toml} of the installed jar. A typo in an id would otherwise
 * disable a guard for a mod that is installed, silently, and this check is the only thing that can see it.
 *
 * <p>What is left to the in-game pass is the plugin itself: Mixin has to load it, and the skip has to
 * happen before the target is resolved. Both are visible in the start-up log, which prints the number of
 * armed guards and the ones the plugin dropped.
 */
public final class NetworkGuardTargetsSelfTest {

    /**
     * The twelve guards and the mods they patch, copied from {@code META-INF/mods.toml}. The order is the
     * one the table declares, so a diff here reads like a diff of the table.
     */
    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();

    private static final Pattern CONFIG_PACKAGE = Pattern.compile("\"package\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CONFIG_PLUGIN = Pattern.compile("\"plugin\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CONFIG_GUARD = Pattern.compile("\"network\\.([A-Za-z0-9_$]+)\"");
    private static final Pattern MOD_ID = Pattern.compile("[a-z][a-z0-9_]*");

    private static final String PLUGIN_CLASS = "first.wildfires.mixin.WildfiresMixinPlugin";

    static {
        EXPECTED.put("AdAstraStationsMixin", "ad_astra");
        EXPECTED.put("AlexsCavesConflictsMixin", "alexscaves");
        EXPECTED.put("CitadelPatreonMixin", "citadel");
        EXPECTED.put("ExposureSupportersMixin", "exposure");
        EXPECTED.put("FancyToastsCreditsMixin", "fancytoasts");
        EXPECTED.put("ImmersiveEngineeringContributorsMixin", "immersiveengineering");
        EXPECTED.put("KiwiContributorsMixin", "kiwi");
        EXPECTED.put("KubeJsUpdateMixin", "kubejs");
        EXPECTED.put("PlaceboTrailsMixin", "placebo");
        EXPECTED.put("PlaceboWingsMixin", "placebo");
        EXPECTED.put("SteamRailsDevCapeMixin", "railways");
        EXPECTED.put("SupplementariesCreditsMixin", "supplementaries");
    }

    private NetworkGuardTargetsSelfTest() {
    }

    public static void main(String[] args) throws IOException {
        String config = stripComments(readConfig());
        theTableIsTheOneReadFromTheInstalledJars();
        theModIdsLookLikeForgeModIds();
        theConfigListsExactlyTheGuardedMixins(config);
        theConfigNamesThePluginAndThePackageItAssumes(config);
        theGuardsAndThePluginAreInTheJar();
        onlyTheGuardsInTheGuardPackageAreGated();
        aDroppedGuardIsRecordedByItsSimpleName();
        System.out.println("NetworkGuardTargetsSelfTest: all checks passed");
    }

    private static void theTableIsTheOneReadFromTheInstalledJars() {
        assertEquals(EXPECTED.size(), NetworkGuardTargets.all().size(), "the table has a row per guard");
        for (Map.Entry<String, String> expected : EXPECTED.entrySet()) {
            assertEquals(expected.getValue(), NetworkGuardTargets.all().get(expected.getKey()),
                    "the mod id of " + expected.getKey());
        }
        assertEquals(new TreeSet<>(EXPECTED.keySet()), new TreeSet<>(NetworkGuardTargets.all().keySet()),
                "the table has no guard the expected list does not have");
    }

    private static void theModIdsLookLikeForgeModIds() {
        for (Map.Entry<String, String> guard : NetworkGuardTargets.all().entrySet()) {
            assertTrue(MOD_ID.matcher(guard.getValue()).matches(),
                    guard.getKey() + " carries a mod id Forge would accept: " + guard.getValue());
        }
    }

    private static void theConfigListsExactlyTheGuardedMixins(String config) {
        List<String> inConfig = new ArrayList<>();
        Matcher matcher = CONFIG_GUARD.matcher(config);
        while (matcher.find()) {
            inConfig.add(matcher.group(1));
        }
        assertEquals(EXPECTED.size(), inConfig.size(), "the config lists one entry per guard");
        assertEquals(EXPECTED.size(), new LinkedHashSet<>(inConfig).size(), "the config lists no guard twice");
        assertEquals(new TreeSet<>(EXPECTED.keySet()), new TreeSet<>(inConfig),
                "the config lists exactly the guards the table knows");
    }

    private static void theConfigNamesThePluginAndThePackageItAssumes(String config) {
        Matcher plugin = CONFIG_PLUGIN.matcher(config);
        assertTrue(plugin.find(), "wildfires.mixins.json names the mod-presence plugin");
        assertEquals(PLUGIN_CLASS, plugin.group(1), "the plugin the config names");
        Matcher mixinPackage = CONFIG_PACKAGE.matcher(config);
        assertTrue(mixinPackage.find(), "wildfires.mixins.json has a mixin package");
        assertEquals(NetworkGuardTargets.GUARD_PACKAGE, mixinPackage.group(1) + ".network",
                "the guard package the table assumes");
    }

    private static void theGuardsAndThePluginAreInTheJar() {
        assertTrue(resourceExists("/" + PLUGIN_CLASS.replace('.', '/') + ".class"),
                "the class the config names as its plugin is in the jar");
        for (String guard : EXPECTED.keySet()) {
            String path = "/" + NetworkGuardTargets.GUARD_PACKAGE.replace('.', '/') + "/" + guard + ".class";
            assertTrue(resourceExists(path), "the guard " + guard + " is in the jar at " + path);
        }
    }

    private static void onlyTheGuardsInTheGuardPackageAreGated() {
        assertEquals("citadel",
                NetworkGuardTargets.requiredMod(NetworkGuardTargets.GUARD_PACKAGE + ".CitadelPatreonMixin"),
                "a guard in the guard package is gated on its mod");
        assertEquals(null, NetworkGuardTargets.requiredMod(null), "a null name is not a guard");
        assertEquals(null, NetworkGuardTargets.requiredMod(NetworkGuardTargets.GUARD_PACKAGE),
                "the package itself is not a guard");
        assertEquals(null, NetworkGuardTargets.requiredMod(NetworkGuardTargets.GUARD_PACKAGE + ".UnknownMixin"),
                "a mixin without a row is applied, not dropped");
        assertEquals(null, NetworkGuardTargets.requiredMod("first.wildfires.mixin.tfc.AbstractFirepitBlockEntityMixin"),
                "a mixin of another package is applied");
        assertEquals(null, NetworkGuardTargets.requiredMod("first.wildfires.mixin.networkish.CitadelPatreonMixin"),
                "a package that only starts with the guard package name is not the guard package");
    }

    private static void aDroppedGuardIsRecordedByItsSimpleName() {
        NetworkGuardTargets.recordDropped(NetworkGuardTargets.GUARD_PACKAGE + ".FancyToastsCreditsMixin", "fancytoasts");
        NetworkGuardTargets.recordDropped(NetworkGuardTargets.GUARD_PACKAGE + ".AdAstraStationsMixin", "ad_astra");
        Map<String, String> dropped = NetworkGuardTargets.dropped();
        assertEquals(2, dropped.size(), "both dropped guards are recorded");
        assertEquals("ad_astra", dropped.get("AdAstraStationsMixin"), "a dropped guard is recorded by simple name");
        assertEquals(new TreeSet<>(List.of("AdAstraStationsMixin", "FancyToastsCreditsMixin")),
                new TreeSet<>(dropped.keySet()), "the dropped guards are the ones that were reported");
    }

    private static String readConfig() throws IOException {
        try (InputStream stream = NetworkGuardTargetsSelfTest.class.getResourceAsStream("/wildfires.mixins.json")) {
            assertTrue(stream != null, "wildfires.mixins.json is on the classpath");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Both comment forms Mixin's reader accepts, so the scan above only ever sees real entries. */
    private static String stripComments(String json) {
        StringBuilder out = new StringBuilder();
        for (String line : json.replaceAll("(?s)/\\*.*?\\*/", "").split("\n", -1)) {
            if (!line.trim().startsWith("//")) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private static boolean resourceExists(String path) {
        try (InputStream stream = NetworkGuardTargetsSelfTest.class.getResourceAsStream(path)) {
            return stream != null;
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static void assertTrue(boolean holds, String what) {
        if (!holds) {
            throw new AssertionError(what);
        }
    }

    private static void assertEquals(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }
}
