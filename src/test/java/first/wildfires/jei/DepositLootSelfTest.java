package first.wildfires.jei;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Plain-Java checks for the deposit loot-table maths behind the JEI sluice and panning categories.
 *
 * <p>The numbers matter and are easy to get wrong: {@code alternatives} is first-match, so a child's
 * real chance is its own condition multiplied by the odds that every earlier child failed, and the raw
 * values in TFC's files are not the percentages a player should be shown.
 */
public final class DepositLootSelfTest {

    private static final float EPSILON = 1e-5F;

    private DepositLootSelfTest() {
    }

    public static void main(String[] args) {
        tfcGoldDepositMatchesItsPublishedOdds();
        repeatedItemChancesAreSummed();
        nestedTableInheritsTheChanceOfItsParent();
        unknownConditionIsListedButFlagged();
        multiRollPoolIsFlagged();
        poolLevelConditionScalesEveryEntry();
        weightedPoolEntriesShareTheRoll();
        itemCountComesFromSetCountOnly();
        missingTableYieldsNothing();
        looseDataFolderIsReadFromDisk();
        deepestChildNeverGetsMoreThanItsParent();
        System.out.println("DepositLootSelfTest: all checks passed");
    }

    /** The real shape and values of {@code data/tfc/loot_tables/panning/deposits/native_gold_granite.json}. */
    private static void tfcGoldDepositMatchesItsPublishedOdds() {
        List<DepositDrop> drops = parse("tfc:panning/deposits/native_gold_granite", """
                {"type":"minecraft:fishing","pools":[{"name":"loot_pool","rolls":1,"entries":[
                  {"type":"minecraft:alternatives","children":[
                    {"type":"minecraft:item","name":"tfc:ore/small_native_gold",
                     "conditions":[{"condition":"minecraft:random_chance","chance":0.5}]},
                    {"type":"minecraft:item","name":"tfc:rock/loose/granite",
                     "conditions":[{"condition":"minecraft:random_chance","chance":0.5}]},
                    {"type":"minecraft:item","name":"tfc:rock/loose/granite",
                     "conditions":[{"condition":"minecraft:random_chance","chance":0.25}]},
                    {"type":"minecraft:item","name":"tfc:ore/topaz",
                     "conditions":[{"condition":"minecraft:random_chance","chance":0.0533}]}
                  ]}]}]}
                """);

        assertEquals(3, drops.size(), "distinct drops after the two granite entries are merged");
        assertChance(0.5F, drops, "tfc:ore/small_native_gold", "the first child keeps its own chance");
        assertChance(0.3125F, drops, "tfc:rock/loose/granite", "0.5*0.5 + 0.5*0.5*0.25");
        assertChance(0.00999375F, drops, "tfc:ore/topaz", "0.5*0.5*0.75*0.0533");
        assertTrue(drops.get(0).chance() >= drops.get(1).chance(), "drops are sorted by chance");
        assertTrue(drops.get(0).exact() && drops.get(2).exact(), "random_chance is evaluated exactly");
    }

    private static void repeatedItemChancesAreSummed() {
        List<DepositDrop> drops = parse("tfc:test/repeats", """
                {"pools":[{"rolls":1,"entries":[{"type":"minecraft:alternatives","children":[
                  {"type":"minecraft:item","name":"minecraft:stone",
                   "conditions":[{"condition":"minecraft:random_chance","chance":0.25}]},
                  {"type":"minecraft:item","name":"minecraft:stone",
                   "conditions":[{"condition":"minecraft:random_chance","chance":0.5}]}
                ]}]}]}
                """);

        assertEquals(1, drops.size(), "the same item twice is one row");
        assertChance(0.625F, drops, "minecraft:stone", "0.25 + 0.75*0.5");
    }

    private static void nestedTableInheritsTheChanceOfItsParent() {
        Map<String, String> tables = new HashMap<>();
        tables.put("tfc:test/outer", """
                {"pools":[{"rolls":1,"entries":[{"type":"minecraft:loot_table","value":"tfc:test/inner",
                  "conditions":[{"condition":"minecraft:random_chance","chance":0.5}]}]}]}
                """);
        tables.put("tfc:test/inner", """
                {"pools":[{"rolls":1,"entries":[
                  {"type":"minecraft:item","name":"minecraft:gold_ingot",
                   "conditions":[{"condition":"minecraft:random_chance","chance":0.5}]}]}]}
                """);

        List<DepositDrop> drops = parse("tfc:test/outer", tables);
        assertChance(0.25F, drops, "minecraft:gold_ingot", "0.5 from the reference times 0.5 inside");
    }

    private static void unknownConditionIsListedButFlagged() {
        List<DepositDrop> drops = parse("tfc:test/unknown", """
                {"pools":[{"rolls":1,"entries":[
                  {"type":"minecraft:item","name":"minecraft:diamond","conditions":[
                    {"condition":"minecraft:random_chance","chance":0.4},
                    {"condition":"minecraft:survives_explosion"}]}]}]}
                """);

        assertEquals(1, drops.size(), "an unevaluatable condition does not hide the item");
        assertChance(0.4F, drops, "minecraft:diamond", "the known condition is still applied");
        assertTrue(!drops.get(0).exact(), "an unknown condition must not read as an exact chance");
    }

    private static void multiRollPoolIsFlagged() {
        List<DepositDrop> drops = parse("tfc:test/rolls", """
                {"pools":[{"rolls":2,"entries":[
                  {"type":"minecraft:item","name":"minecraft:coal",
                   "conditions":[{"condition":"minecraft:random_chance","chance":0.3}]}]}]}
                """);

        assertChance(0.3F, drops, "minecraft:coal", "the per-roll chance is still reported");
        assertTrue(!drops.get(0).exact(), "a pool rolled twice is not a single-roll chance");
    }

    private static void poolLevelConditionScalesEveryEntry() {
        List<DepositDrop> drops = parse("tfc:test/pool_condition", """
                {"pools":[{"rolls":1,"conditions":[{"condition":"minecraft:random_chance","chance":0.5}],
                  "entries":[{"type":"minecraft:item","name":"minecraft:iron_nugget",
                    "conditions":[{"condition":"minecraft:random_chance","chance":0.5}]}]}]}
                """);

        assertChance(0.25F, drops, "minecraft:iron_nugget", "pool and entry chances multiply");
    }

    private static void weightedPoolEntriesShareTheRoll() {
        List<DepositDrop> drops = parse("tfc:test/weighted", """
                {"pools":[{"rolls":1,"entries":[
                  {"type":"minecraft:item","name":"minecraft:copper_ingot","weight":3},
                  {"type":"minecraft:item","name":"minecraft:tin_ingot","weight":1}]}]}
                """);

        assertEquals(2, drops.size(), "both weighted entries are listed");
        assertChance(0.75F, drops, "minecraft:copper_ingot", "weight 3 of 4");
        assertChance(0.25F, drops, "minecraft:tin_ingot", "weight 1 of 4");
    }

    private static void itemCountComesFromSetCountOnly() {
        List<DepositDrop> drops = parse("tfc:test/counts", """
                {"pools":[{"rolls":1,"entries":[
                  {"type":"minecraft:item","name":"minecraft:flint","count":2},
                  {"type":"minecraft:item","name":"minecraft:clay_ball",
                   "functions":[{"function":"minecraft:set_count","count":5}]}]}]}
                """);

        // Minecraft's LootItem builds `new ItemStack(item)` and only functions resize it, so a bare
        // `count` field changes nothing in game and must not be shown here either.
        assertEquals(1, countOf(drops, "minecraft:flint"), "a bare count field is ignored, as the game ignores it");
        assertEquals(5, countOf(drops, "minecraft:clay_ball"), "set_count is honoured");
    }

    private static void missingTableYieldsNothing() {
        List<DepositDrop> drops = DepositLoot.parse(id("tfc:test/absent"), key -> null);
        assertEquals(0, drops.size(), "an unreadable table produces no rows");
    }

    /**
     * Drives {@link DepositLoot#resolve} against a real folder on disk, because that is the only way to
     * test the shape of the path it looks for there: {@code <root>/<namespace>/loot_tables/<path>.json},
     * the extension appearing exactly once.
     *
     * <p>Feeding {@link DepositLoot#parse} a map cannot catch a mistake in that path, and the cost of one
     * is invisible in game - a table missing from the packs is simply reported as having no drops. This
     * test exists because a doubled ".json" did exactly that: every table that only lived in the pack's
     * {@code kubejs/data} folder was found by nothing, so 127 JEI entries listed an input and no output.
     */
    private static void looseDataFolderIsReadFromDisk() {
        Path root;
        try {
            root = Files.createTempDirectory("wildfires-deposit-loot");
        } catch (Exception e) {
            throw new AssertionError("could not create a temp folder for the loose data folder test", e);
        }

        try {
            Path table = root.resolve("tfc").resolve("loot_tables").resolve("sluicing").resolve("deposits")
                    .resolve("test_gold.json");
            Files.createDirectories(table.getParent());
            Files.writeString(table, """
                    {"pools":[{"rolls":1,"entries":[
                      {"type":"minecraft:item","name":"tfc:ore/small_native_gold",
                       "conditions":[{"condition":"minecraft:random_chance","chance":0.5}]}]}]}
                    """, StandardCharsets.UTF_8);

            try (MultiPackResourceManager manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of())) {
                List<DepositDrop> drops = DepositLoot.resolve(manager, id("tfc:sluicing/deposits/test_gold"), root);
                assertEquals(1, drops.size(), "the table in the loose data folder is the one that gets read");
                assertChance(0.5F, drops, "tfc:ore/small_native_gold", "and is parsed like any other table");

                List<DepositDrop> absent = DepositLoot.resolve(manager, id("tfc:sluicing/deposits/test_absent"), root);
                assertEquals(0, absent.size(), "a table in neither the folder nor the packs still yields nothing");
            }
        } catch (Exception e) {
            throw new AssertionError("the loose data folder test could not read or write its temp files", e);
        } finally {
            deleteRecursively(root);
        }
    }

    private static void deleteRecursively(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (Exception e) {
            // A leftover temp folder is not worth failing an otherwise passing test over.
        }
    }

    /** Guards the ordering rule: no child may end up likelier than the branch that contains it. */
    private static void deepestChildNeverGetsMoreThanItsParent() {
        List<DepositDrop> drops = parse("tfc:test/ordering", """
                {"pools":[{"rolls":1,"entries":[{"type":"minecraft:alternatives","children":[
                  {"type":"minecraft:item","name":"minecraft:apple",
                   "conditions":[{"condition":"minecraft:random_chance","chance":0.9}]},
                  {"type":"minecraft:item","name":"minecraft:stick",
                   "conditions":[{"condition":"minecraft:random_chance","chance":0.9}]},
                  {"type":"minecraft:item","name":"minecraft:bone",
                   "conditions":[{"condition":"minecraft:random_chance","chance":0.9}]}
                ]}]}]}
                """);

        float previous = Float.MAX_VALUE;
        for (DepositDrop drop : drops) {
            assertTrue(drop.chance() <= previous, "later alternatives can never overtake earlier ones");
            previous = drop.chance();
        }
        float total = 0.0F;
        for (DepositDrop drop : drops) {
            total += drop.chance();
        }
        assertTrue(total <= 1.0F + EPSILON, "the chances cannot sum past certainty, was " + total);
    }

    private static List<DepositDrop> parse(String table, String json) {
        Map<String, String> single = new HashMap<>();
        single.put(table, json);
        return parse(table, single);
    }

    private static List<DepositDrop> parse(String table, Map<String, String> json) {
        return DepositLoot.parse(id(table), asTables(json));
    }

    private static Function<ResourceLocation, JsonObject> asTables(Map<String, String> json) {
        return key -> {
            String text = json.get(key.toString());
            return text == null ? null : JsonParser.parseString(text).getAsJsonObject();
        };
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new AssertionError("bad test id " + value);
        }
        return id;
    }

    private static void assertChance(float expected, List<DepositDrop> drops, String item, String what) {
        float actual = chanceOf(drops, item);
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(what + ": " + item + " expected " + expected + " but was " + actual);
        }
    }

    private static float chanceOf(List<DepositDrop> drops, String item) {
        for (DepositDrop drop : drops) {
            if (drop.item().toString().equals(item)) {
                return drop.chance();
            }
        }
        throw new AssertionError("no drop listed for " + item);
    }

    private static int countOf(List<DepositDrop> drops, String item) {
        for (DepositDrop drop : drops) {
            if (drop.item().toString().equals(item)) {
                return drop.count();
            }
        }
        throw new AssertionError("no drop listed for " + item);
    }

    private static void assertEquals(int expected, int actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean value, String what) {
        if (!value) {
            throw new AssertionError(what);
        }
    }
}
