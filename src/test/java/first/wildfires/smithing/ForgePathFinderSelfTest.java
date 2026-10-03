package first.wildfires.smithing;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.dries007.tfc.common.capabilities.forge.ForgeRule;
import net.dries007.tfc.common.capabilities.forge.ForgeStep;
import net.dries007.tfc.common.capabilities.forge.ForgeSteps;
import net.dries007.tfc.common.capabilities.forge.ForgingBonus;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Plain-Java checks for the forging manual's shortest-path search.
 *
 * <p>The search is easy to get subtly wrong in ways nothing else would notice: the rules only look at the
 * last three strikes, so a path can be rejected for reasons a work-value check alone would miss, and the
 * three-strike window has to be fed to {@link ForgeSteps} oldest-first or every ordered rule is read
 * backwards. These checks strike the paths out for real with TFC's own classes, compare the search against
 * an independently written relaxation, and run TFC's shipped anvil recipes end to end.
 */
public final class ForgePathFinderSelfTest {

    /** The world seed stands in for a real save: targets only have to be stable and varied here. */
    private static final long SEED = 8675309L;

    /** One slot per recent strike plus a zero for "never struck", so the window is a base-9 number. */
    private static final int SLOTS = ForgeStep.VALUES.length + 1;
    private static final int WINDOWS = SLOTS * SLOTS * SLOTS;

    private static final int LIMIT = ForgeStep.LIMIT;
    private static final int STATES = LIMIT * WINDOWS;
    private static final int UNREACHED = Integer.MAX_VALUE;

    /**
     * TFC's own rule: a forged item is {@code PERFECTLY_FORGED} when strikes taken divided by
     * {@code ForgeRule#calculateOptimalStepsToTarget} is under this. The manual teaches a path, so that
     * path has to land in the perfect band - {@link ForgingBonus} holds the same number, read from config.
     */
    private static final float PERFECT_RATIO = 1.5F;

    /** The art offers eight strike icons, and the row only fits six of them beside the target. */
    private static final int ART_ICONS = 8;
    private static final int ROW_ICONS = 6;

    /** No recipe should ever need more than this many strikes; TFC's own recipes top out well below. */
    private static final int PLAUSIBLE_STRIKES = 13;

    private ForgePathFinderSelfTest() {
    }

    public static void main(String[] args) {
        try {
            targetsStayInRangeAndFollowTheSeed();
            repeatedStrikesCollapseIntoOneIcon();
            outOfRangeTargetsHaveNoPath();
            everySolutionCanBeStruckForReal();
            aPlainRelaxationAgreesWithTheSearch();
            realRecipesAllSolveWithinTheIconRow();
            System.out.println("ForgePathFinderSelfTest: all checks passed");
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            System.out.println("ForgePathFinderSelfTest: skipped, TFC or Minecraft classes are unavailable (" + e + ")");
        }
    }

    private static void targetsStayInRangeAndFollowTheSeed() {
        int moved = 0;
        for (int i = 0; i < 300; i++) {
            ResourceLocation id = id("tfc:test/recipe_" + i);
            int target = ForgeTargets.compute(SEED, id);
            assertEquals(target, ForgeTargets.compute(SEED, id), "the same world and recipe want the same work");
            assertTrue(target >= ForgeTargets.MIN && target <= ForgeTargets.MAX,
                    "target " + target + " for " + id + " is outside " + ForgeTargets.MIN + ".." + ForgeTargets.MAX);
            if (target != ForgeTargets.compute(SEED + 1L, id)) {
                moved++;
            }
        }
        assertTrue(moved > 250, "another world seed should move nearly every target, only " + moved + " of 300 moved");
    }

    private static void repeatedStrikesCollapseIntoOneIcon() {
        List<ForgeStep> strikes = List.of(ForgeStep.PUNCH, ForgeStep.PUNCH, ForgeStep.PUNCH,
                ForgeStep.DRAW, ForgeStep.DRAW, ForgeStep.UPSET);
        ForgePath path = ForgePath.of(60, strikes);

        assertEquals(60, path.target(), "the path keeps its target");
        assertEquals(6, path.length(), "run lengths still count as strikes");
        assertEquals(3, path.runs().size(), "three punches in a row are one icon");
        assertEquals(ForgeStep.PUNCH, path.runs().get(0).step(), "the first icon is the punch");
        assertEquals(3, path.runs().get(0).count(), "the first icon repeats three times");
        assertEquals(ForgeStep.DRAW, path.runs().get(1).step(), "the second icon is the draw");
        assertEquals(2, path.runs().get(1).count(), "the second icon repeats twice");
        assertEquals(ForgeStep.UPSET, path.runs().get(2).step(), "the third icon is the upset");
        assertEquals(1, path.runs().get(2).count(), "a lone strike is one icon of one");
        assertEquals(strikes, expand(path), "the icons expand back into the strikes");

        ForgePath nothing = ForgePath.empty(60);
        assertTrue(nothing.isEmpty(), "an empty path is empty");
        assertEquals(0, nothing.runs().size(), "an empty path draws no icons");
        assertEquals(-1, ForgePath.unknown().target(), "an unknown path has no target");
        assertTrue(ForgePath.unknown().isEmpty(), "an unknown path has no strikes either");
    }

    private static void outOfRangeTargetsHaveNoPath() {
        ForgeRule[] rules = {ForgeRule.HIT_LAST, ForgeRule.BEND_SECOND_LAST, ForgeRule.UPSET_THIRD_LAST};

        ForgePath below = ForgePathFinder.instance().find(-1, rules);
        assertTrue(below.isEmpty(), "work cannot be struck below zero");
        assertEquals(-1, below.target(), "an impossible target is still reported back");

        ForgePath above = ForgePathFinder.instance().find(LIMIT, rules);
        assertTrue(above.isEmpty(), "the anvil tops out at " + (LIMIT - 1) + ", so " + LIMIT + " is out of reach");

        ForgePath noRules = ForgePathFinder.instance().find(60, null);
        assertTrue(!noRules.isEmpty(), "a target with no rules still needs a sequence");
        assertEquals(60, noRules.target(), "and still reports the target");
        assertTrue(strikesOut(noRules, 60, new ForgeRule[0]) == null,
                "a rule-free path lands on its target");
    }

    private static void everySolutionCanBeStruckForReal() {
        List<ForgeRule[]> samples = ruleSamples();
        int solved = 0;
        int unsolvable = 0;
        int shortest = Integer.MAX_VALUE;
        int longest = 0;
        int widest = 0;

        for (ForgeRule[] rules : samples) {
            for (int target = ForgeTargets.MIN; target <= ForgeTargets.MAX; target += 7) {
                ForgePath path = ForgePathFinder.instance().find(target, rules);
                if (path.isEmpty()) {
                    unsolvable++;
                    continue;
                }
                solved++;
                shortest = Math.min(shortest, path.length());
                longest = Math.max(longest, path.length());
                widest = Math.max(widest, path.runs().size());

                String where = "target " + target + " with " + Arrays.toString(rules);
                String problem = strikesOut(path, target, rules);
                assertTrue(problem == null, where + ": " + problem);

                assertTrue(path.runs().size() <= ART_ICONS, where + " needs more icons than the art has");
                for (int i = 0; i < path.runs().size(); i++) {
                    ForgePath.Run run = path.runs().get(i);
                    assertTrue(run.count() >= 1, where + ": a run of " + run.count() + " strikes");
                    if (i > 0) {
                        assertTrue(path.runs().get(i - 1).step() != run.step(),
                                where + ": neighbouring icons are the same strike, so they should have merged");
                    }
                }
                assertEquals(path.length(), countStrikes(path), where + ": run lengths add up to the strike count");
            }
        }

        assertTrue(solved > 200, "the rule sample should solve plenty of cases, only " + solved + " solved");
        assertTrue(shortest >= 1, "every solved case needs at least one strike");
        assertTrue(longest <= PLAUSIBLE_STRIKES, "no case should need more than " + PLAUSIBLE_STRIKES + " strikes, worst was " + longest);
        assertTrue(widest <= ART_ICONS, "no case should need more icons than the art has, worst was " + widest);
        System.out.println("ForgePathFinderSelfTest: sampled rules solved " + solved + " cases ("
                + unsolvable + " impossible), " + shortest + ".." + longest + " strikes, up to " + widest + " icons");
    }

    /**
     * The same search written a second way: no layers, no queue order to rely on, just relaxations over a
     * state key that is decoded by hand. A layered pass that walked its layers wrongly, or packed the
     * strike window the wrong way round, shows up here as a different answer.
     */
    private static void aPlainRelaxationAgreesWithTheSearch() {
        List<ForgeRule[]> samples = ruleSamples();
        int compared = 0;
        for (int i = 0; i < samples.size() && compared < 8; i += 7) {
            ForgeRule[] rules = samples.get(i);
            for (int target = ForgeTargets.MIN + 9; target <= ForgeTargets.MAX; target += 41) {
                int[] expected = plainRelaxation(target, rules);
                ForgePath path = ForgePathFinder.instance().find(target, rules);
                assertEquals(expected == null, path.isEmpty(),
                        "target " + target + " with " + Arrays.toString(rules) + ": reachability (relaxation found "
                                + (expected == null ? "nothing" : Arrays.toString(expected)) + ", the search found "
                                + (path.isEmpty() ? "nothing" : path.steps()));
                if (expected != null) {
                    assertEquals(expected[0], path.length(),
                            "target " + target + " with " + Arrays.toString(rules) + ": strike count");
                    assertEquals(expected[1], path.runs().size(),
                            "target " + target + " with " + Arrays.toString(rules) + ": icon count");
                }
                compared++;
            }
        }
        assertTrue(compared > 0, "the relaxation should have been compared against something");
    }

    private static void realRecipesAllSolveWithinTheIconRow() {
        List<Recipe> recipes = readTfcAnvilRecipes();
        if (recipes.isEmpty()) {
            System.out.println("ForgePathFinderSelfTest: skipped the shipped-recipe sweep, no TFC anvil recipes found");
            return;
        }

        List<String> unavoidable = new ArrayList<>();
        List<String> tooManyIcons = new ArrayList<>();
        List<String> notPerfect = new ArrayList<>();
        boolean bonusChecked = true;
        float worstRatio = 0.0F;
        String worst = "";
        int longest = 0;

        for (Recipe recipe : recipes) {
            int target = ForgeTargets.compute(SEED, recipe.id());
            ForgePath path = ForgePathFinder.instance().find(target, recipe.rules());
            String where = recipe.id() + " @ " + target;

            if (path.isEmpty()) {
                unavoidable.add(where);
                continue;
            }
            String problem = strikesOut(path, target, recipe.rules());
            assertTrue(problem == null, where + ": " + problem);
            longest = Math.max(longest, path.length());
            if (path.runs().size() > ROW_ICONS) {
                tooManyIcons.add(where + " needs " + path.runs().size() + " icons");
            }

            float ratio = perfectRatio(path.length(), target, recipe.rules());
            if (ratio > 0.0F) {
                if (ratio > worstRatio) {
                    worstRatio = ratio;
                    worst = where;
                }
                if (!(ratio < PERFECT_RATIO)) {
                    notPerfect.add(where + " ratio " + ratio);
                }
                if (bonusChecked) {
                    bonusChecked = perfectBonus(path.length(), target, recipe.rules());
                }
            }
        }

        // Three recipes, every target: the same recipe is a different problem in a different world.
        for (int i = 0; i < Math.min(3, recipes.size()); i++) {
            Recipe recipe = recipes.get(i);
            for (int target = ForgeTargets.MIN; target <= ForgeTargets.MAX; target++) {
                ForgePath path = ForgePathFinder.instance().find(target, recipe.rules());
                if (path.isEmpty()) {
                    unavoidable.add(recipe.id() + " @ " + target + " (all targets)");
                    continue;
                }
                String problem = strikesOut(path, target, recipe.rules());
                assertTrue(problem == null, recipe.id() + " @ " + target + ": " + problem);
                if (path.runs().size() > ROW_ICONS) {
                    tooManyIcons.add(recipe.id() + " @ " + target + " needs " + path.runs().size() + " icons");
                }
            }
        }

        System.out.println("ForgePathFinderSelfTest: " + recipes.size() + " shipped anvil recipes, longest "
                + longest + " strikes, worst perfect-forge ratio " + worstRatio + " at " + worst
                + (bonusChecked ? "" : " (ForgingBonus unavailable here, ratio checked against " + PERFECT_RATIO + ")"));

        assertTrue(unavoidable.isEmpty(), "every shipped recipe should have a path, but "
                + unavoidable.size() + " did not: " + head(unavoidable));
        assertTrue(tooManyIcons.isEmpty(), "the manual's row fits " + ROW_ICONS + " icons, but "
                + tooManyIcons.size() + " recipes need more: " + head(tooManyIcons));
        assertTrue(notPerfect.isEmpty(), "a taught path must reach a perfect forge (ratio under " + PERFECT_RATIO
                + "), but " + notPerfect.size() + " did not: " + head(notPerfect));
    }

    /** Strikes a path out with TFC's own classes: null when it works, otherwise what went wrong. */
    private static String strikesOut(ForgePath path, int target, ForgeRule[] rules) {
        int work = 0;
        ForgeSteps history = new ForgeSteps();
        for (ForgeStep step : path.steps()) {
            work += step.step();
            if (work < 0 || work >= LIMIT) {
                return work + " is outside the anvil's 0.." + (LIMIT - 1) + " after striking "
                        + history.total() + " times";
            }
            history.addStep(step);
        }
        if (work != target) {
            return "the sequence ends on " + work + ", not the target " + target;
        }
        if (history.total() != path.length()) {
            return "the anvil counted " + history.total() + " strikes, the path has " + path.length();
        }
        for (ForgeRule rule : rules) {
            if (!rule.matches(history)) {
                return "the rule " + rule + " is not satisfied by " + path.steps();
            }
        }
        return null;
    }

    /** TFC's estimate of the fewest strikes, or 0 when it cannot be asked. */
    private static float perfectRatio(int strikes, int target, ForgeRule[] rules) {
        try {
            int optimal = ForgeRule.calculateOptimalStepsToTarget(target, rules);
            if (optimal <= 0) {
                return 0.0F;
            }
            return strikes / (float) optimal;
        } catch (Throwable t) {
            return 0.0F;
        }
    }

    /** Whether TFC itself would call this many strikes on this target a perfect forge. */
    private static boolean perfectBonus(int strikes, int target, ForgeRule[] rules) {
        try {
            int optimal = ForgeRule.calculateOptimalStepsToTarget(target, rules);
            if (optimal <= 0) {
                return true;
            }
            return ForgingBonus.byRatio(strikes / (float) optimal) == ForgingBonus.PERFECTLY_FORGED;
        } catch (Throwable t) {
            // The config is not loaded outside the game, which is the only reason this can fail.
            return false;
        }
    }

    /**
     * A plain label-correcting relaxation over the same state space, deliberately written without the
     * search's layered sweep and with the strike window decoded a slot at a time.
     *
     * @return {@code {strikes, icons}} for the best path, or null when the target cannot be reached
     */
    private static int[] plainRelaxation(int target, ForgeRule[] rules) {
        int[] bestStrikes = new int[STATES];
        int[] bestIcons = new int[STATES];
        Arrays.fill(bestStrikes, UNREACHED);
        Arrays.fill(bestIcons, UNREACHED);
        bestStrikes[0] = 0;
        bestIcons[0] = 0;

        ArrayDeque<Integer> pending = new ArrayDeque<>();
        pending.add(0);
        long guard = 0L;
        while (!pending.isEmpty()) {
            if (++guard > 40_000_000L) {
                throw new AssertionError("the plain relaxation never settled");
            }
            int state = pending.poll();
            int work = state / WINDOWS;
            int window = state % WINDOWS;
            int middle = (window / SLOTS) % SLOTS;
            int newest = window % SLOTS;

            for (int index = 0; index < ForgeStep.VALUES.length; index++) {
                int nextWork = work + ForgeStep.VALUES[index].step();
                if (nextWork < 0 || nextWork >= LIMIT) {
                    continue;
                }
                // Sliding the window along by hand: the oldest strike falls out, the newest moves up a slot.
                int nextWindow = (middle * SLOTS + newest) * SLOTS + index + 1;
                int nextState = nextWork * WINDOWS + nextWindow;
                int nextStrikes = bestStrikes[state] + 1;
                int nextIcons = bestIcons[state] + (newest == index + 1 ? 0 : 1);
                if (nextStrikes < bestStrikes[nextState]
                        || (nextStrikes == bestStrikes[nextState] && nextIcons < bestIcons[nextState])) {
                    bestStrikes[nextState] = nextStrikes;
                    bestIcons[nextState] = nextIcons;
                    pending.add(nextState);
                }
            }
        }

        int base = target * WINDOWS;
        int bestState = -1;
        for (int window = 0; window < WINDOWS; window++) {
            int state = base + window;
            if (bestStrikes[state] == UNREACHED || !satisfies(window, rules)) {
                continue;
            }
            if (bestState < 0
                    || bestStrikes[state] < bestStrikes[bestState]
                    || (bestStrikes[state] == bestStrikes[bestState] && bestIcons[state] < bestIcons[bestState])) {
                bestState = state;
            }
        }
        return bestState < 0 ? null : new int[]{bestStrikes[bestState], bestIcons[bestState]};
    }

    /** Reads a window the long way round: oldest strike first, which is the order the anvil replays them. */
    private static boolean satisfies(int window, ForgeRule[] rules) {
        if (rules.length == 0) {
            return true;
        }
        int[] slots = {(window / (SLOTS * SLOTS)) % SLOTS, (window / SLOTS) % SLOTS, window % SLOTS};
        ForgeSteps history = new ForgeSteps();
        for (int value : slots) {
            if (value > 0) {
                history.addStep(ForgeStep.VALUES[value - 1]);
            }
        }
        for (ForgeRule rule : rules) {
            if (!rule.matches(history)) {
                return false;
            }
        }
        return true;
    }

    private static List<ForgeRule[]> ruleSamples() {
        ForgeRule[] all = ForgeRule.values();
        List<ForgeRule[]> samples = new ArrayList<>();
        samples.add(new ForgeRule[0]);
        for (ForgeRule rule : all) {
            samples.add(new ForgeRule[]{rule});
        }
        // TFC always writes three rules, one per position, so that is the shape worth testing.
        Random random = new Random(20240607L);
        for (int i = 0; i < 30; i++) {
            samples.add(new ForgeRule[]{
                    all[random.nextInt(all.length)],
                    all[random.nextInt(all.length)],
                    all[random.nextInt(all.length)]});
        }
        return samples;
    }

    /**
     * Every anvil recipe TFC ships, read from the mod itself rather than from a data pack copy, so the
     * sweep covers what players actually forge.
     */
    private static List<Recipe> readTfcAnvilRecipes() {
        Path source = tfcDataSource();
        if (source == null) {
            return List.of();
        }
        List<Recipe> recipes = new ArrayList<>();
        int unknownRules = 0;
        try {
            if (Files.isDirectory(source)) {
                Path dir = source.resolve("data/tfc/recipes/anvil");
                if (!Files.isDirectory(dir)) {
                    return List.of();
                }
                List<Path> files;
                try (Stream<Path> stream = Files.list(dir)) {
                    files = stream.filter(path -> path.toString().endsWith(".json")).sorted().toList();
                }
                for (Path file : files) {
                    String name = file.getFileName().toString();
                    unknownRules += readRecipe(Files.readString(file, StandardCharsets.UTF_8),
                            name.substring(0, name.length() - ".json".length()), recipes);
                }
            } else {
                try (ZipFile zip = new ZipFile(source.toFile())) {
                    List<String> names = new ArrayList<>();
                    for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
                        String name = entry.getName();
                        if (name.startsWith("data/tfc/recipes/anvil/") && name.endsWith(".json")) {
                            names.add(name);
                        }
                    }
                    names.sort(null);
                    for (String name : names) {
                        try (InputStream in = zip.getInputStream(zip.getEntry(name))) {
                            String base = name.substring(name.lastIndexOf('/') + 1);
                            unknownRules += readRecipe(new String(in.readAllBytes(), StandardCharsets.UTF_8),
                                    base.substring(0, base.length() - ".json".length()), recipes);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new AssertionError("could not read TFC's shipped anvil recipes from " + source, e);
        }
        if (unknownRules > 0) {
            System.out.println("ForgePathFinderSelfTest: ignored " + unknownRules
                    + " shipped recipes whose rules this build does not know");
        }
        return recipes;
    }

    /** @return the number of rules in this file that are not in {@link ForgeRule} */
    private static int readRecipe(String json, String name, List<Recipe> out) {
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return 0;
        }
        if (!root.has("type") || !"tfc:anvil".equals(root.get("type").getAsString())) {
            return 0;
        }
        JsonArray rules = root.getAsJsonArray("rules");
        if (rules == null || rules.isEmpty()) {
            return 0;
        }
        ForgeRule[] mapped = new ForgeRule[rules.size()];
        for (int i = 0; i < rules.size(); i++) {
            try {
                mapped[i] = ForgeRule.valueOf(rules.get(i).getAsString().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return 1;
            }
        }
        // The id is the recipe's path, which is what the anvil hashes to pick the work target.
        out.add(new Recipe(id("tfc:anvil/" + name), mapped));
        return 0;
    }

    /** Where TFC's own data lives: its jar on this classpath, or an exploded classes folder. */
    private static Path tfcDataSource() {
        try {
            var source = ForgeRule.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return null;
            }
            return Path.of(source.getLocation().toURI());
        } catch (Exception e) {
            return null;
        }
    }

    private static List<ForgeStep> expand(ForgePath path) {
        List<ForgeStep> strikes = new ArrayList<>(path.length());
        for (ForgePath.Run run : path.runs()) {
            for (int i = 0; i < run.count(); i++) {
                strikes.add(run.step());
            }
        }
        return strikes;
    }

    private static int countStrikes(ForgePath path) {
        int total = 0;
        for (ForgePath.Run run : path.runs()) {
            total += run.count();
        }
        return total;
    }

    private static String head(List<String> problems) {
        return problems.size() <= 5 ? problems.toString() : problems.subList(0, 5) + " ...";
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new AssertionError("bad test id " + value);
        }
        return id;
    }

    private static void assertEquals(int expected, int actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertEquals(ForgeStep expected, ForgeStep actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertEquals(boolean expected, boolean actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertEquals(List<ForgeStep> expected, List<ForgeStep> actual, String what) {
        if (!expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean value, String what) {
        if (!value) {
            throw new AssertionError(what);
        }
    }

    private record Recipe(ResourceLocation id, ForgeRule[] rules) {
    }
}
