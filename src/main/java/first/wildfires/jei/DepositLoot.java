package first.wildfires.jei;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITag;
import net.minecraftforge.registries.tags.ITagManager;
import org.slf4j.Logger;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Works out what a deposit's loot table can drop, and how likely each drop is.
 *
 * <p>Minecraft has no API for asking a loot table what it <em>could</em> produce - the only way to get
 * anything out of one is to roll it and see. So the table is read as JSON instead and the handful of
 * entry and condition types that matter here are evaluated directly. TFC's own deposit tables are exact
 * that way: a single pool holding one {@code minecraft:alternatives} entry whose children are
 * {@code minecraft:item}s gated by {@code minecraft:random_chance}. A pack is free to replace them with
 * something else - this one rolls each pool several times and nests {@code minecraft:group}s inside - and
 * a table like that is only ever reported as approximate.
 *
 * <p>{@code alternatives} takes the first child whose conditions pass, so the children are walked in
 * order and each one only gets the probability left over after its predecessors failed. A child's
 * marginal chance is therefore not its own condition value except for the first child, which is why the
 * raw numbers in the file cannot simply be shown as percentages.
 *
 * <p>The table JSON is supplied through a lookup function rather than read from a {@link ResourceManager}
 * directly, so {@link #parse} can be driven from a map in tests.
 */
public final class DepositLoot {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    /** Enough for TFC's four-entry tables several times over, while keeping the category a sane size. */
    private static final int MAX_DROPS = 12;
    private static final int MAX_DEPTH = 4;

    private DepositLoot() {
    }

    /**
     * Reads one deposit's table, preferring {@code looseDataRoot} over the loaded data packs.
     *
     * <p>That folder exists because KubeJS keeps its data pack in {@code kubejs/data} and hands it to the
     * server alone, so it is missing from the client's pack list even though this pack defines, and
     * overrides, every deposit table there. A table found in it therefore wins: the server loads it after
     * the mod jars, so its contents are the ones that actually drop.
     *
     * @param looseDataRoot an unpacked data folder to read first, or {@code null} to use the packs alone
     */
    public static List<DepositDrop> resolve(ResourceManager manager, ResourceLocation tableId, Path looseDataRoot) {
        return parse(tableId, id -> readTable(manager, id, looseDataRoot));
    }

    /**
     * Walks the table named by {@code tableId}, taking each table's JSON from {@code tables}. A lookup
     * returning {@code null} means the table could not be read, which flags the drops it feeds as
     * approximate rather than silently dropping them.
     */
    public static List<DepositDrop> parse(ResourceLocation tableId, Function<ResourceLocation, JsonObject> tables) {
        Accumulator accumulator = new Accumulator();
        walk(tableId, tables, 1.0F, true, accumulator, 0);

        List<DepositDrop> drops = accumulator.toDrops();
        if (drops.size() > MAX_DROPS) {
            LOGGER.info("[Wildfires] loot table {} has {} distinct drops, showing the {} likeliest",
                    tableId, drops.size(), MAX_DROPS);
            drops = drops.subList(0, MAX_DROPS);
        }
        return drops;
    }

    private static JsonObject readTable(ResourceManager manager, ResourceLocation tableId, Path looseDataRoot) {
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(tableId.getNamespace(),
                "loot_tables/" + tableId.getPath() + ".json");
        Path loose = loosePath(file, looseDataRoot);

        if (loose != null) {
            try (Reader reader = Files.newBufferedReader(loose, StandardCharsets.UTF_8)) {
                return GSON.fromJson(reader, JsonObject.class);
            } catch (Exception e) {
                LOGGER.warn("[Wildfires] could not read loot table {} from {}", tableId, loose, e);
                return null;
            }
        }

        Optional<Resource> resource = manager.getResource(file);
        if (resource.isEmpty()) {
            LOGGER.warn("[Wildfires] no loot table for {} (looked for {} and {})", tableId, file, looseDataRoot);
            return null;
        }
        try (Reader reader = resource.get().openAsReader()) {
            return GSON.fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            LOGGER.warn("[Wildfires] could not read loot table {}", tableId, e);
            return null;
        }
    }

    /**
     * The unpacked location of {@code file}, or {@code null} if it is not in the folder.
     *
     * <p>An id is allowed to contain "..", so anything that climbs out of the folder is refused.
     */
    private static Path loosePath(ResourceLocation file, Path looseDataRoot) {
        if (looseDataRoot == null) {
            return null;
        }
        Path root = looseDataRoot.toAbsolutePath().normalize();
        // `file` already carries its extension: it is the same id handed to the resource manager.
        Path path = root.resolve(file.getNamespace()).resolve(file.getPath()).normalize();
        return path.startsWith(root) && Files.isRegularFile(path) ? path : null;
    }

    private static void walk(ResourceLocation tableId, Function<ResourceLocation, JsonObject> tables, float reach,
                             boolean exact, Accumulator accumulator, int depth) {
        if (depth > MAX_DEPTH) {
            LOGGER.warn("[Wildfires] loot table {} nests deeper than {} levels, stopping there", tableId, MAX_DEPTH);
            accumulator.markApproximate();
            return;
        }

        JsonObject table = tables.apply(tableId);
        if (table == null || !table.has("pools")) {
            accumulator.markApproximate();
            return;
        }

        for (JsonElement poolElement : table.getAsJsonArray("pools")) {
            JsonObject pool = poolElement.getAsJsonObject();
            Condition poolCondition = condition(pool);
            float poolReach = reach * poolCondition.chance();
            // A pool rolled more than once can hand out its entry repeatedly, so the per-roll chance no
            // longer describes what a player ends up with. Such a table is flagged rather than shown as
            // though it were a single roll.
            boolean poolExact = exact && poolCondition.exact() && rollCount(pool) == 1;

            JsonArray entries = pool.getAsJsonArray("entries");
            if (entries == null) {
                continue;
            }

            // Entries sitting directly in a pool compete with each other by weight; only one is taken
            // per roll.
            float totalWeight = 0.0F;
            for (JsonElement entry : entries) {
                totalWeight += weight(entry.getAsJsonObject());
            }
            for (JsonElement entry : entries) {
                JsonObject object = entry.getAsJsonObject();
                float share = totalWeight <= 0.0F ? 0.0F : weight(object) / totalWeight;
                emit(object, poolReach * share, poolExact, tables, accumulator, depth);
            }
        }
    }

    private static void emit(JsonObject entry, float chance, boolean exact,
                             Function<ResourceLocation, JsonObject> tables, Accumulator accumulator, int depth) {
        String type = string(entry, "type", "minecraft:item");
        Condition condition = condition(entry);
        float reached = chance * condition.chance();
        boolean reachedExactly = exact && condition.exact();

        switch (type) {
            case "minecraft:empty" -> {
                // Deliberately yields nothing.
            }
            case "minecraft:item" -> {
                ResourceLocation id = resource(entry, "name");
                if (id == null) {
                    LOGGER.warn("[Wildfires] a deposit loot table has an item entry without a name");
                    accumulator.markApproximate();
                    return;
                }
                accumulator.add(id, count(entry), reached, reachedExactly);
            }
            case "minecraft:alternatives" -> {
                JsonArray children = entry.getAsJsonArray("children");
                if (children == null) {
                    return;
                }
                // First child whose conditions pass wins, so a child is only reachable if every child
                // before it failed. `emit` applies the child's own conditions, so `remaining` must carry
                // only the odds of getting this far - multiplying by the child's chance here as well
                // would count it twice.
                float remaining = reached;
                for (JsonElement childElement : children) {
                    JsonObject child = childElement.getAsJsonObject();
                    Condition childCondition = condition(child);
                    emit(child, remaining, reachedExactly, tables, accumulator, depth);
                    remaining *= 1.0F - childCondition.chance();
                }
            }
            case "minecraft:group", "minecraft:sequence" -> {
                JsonArray children = entry.getAsJsonArray("children");
                if (children == null) {
                    return;
                }
                // Both of these yield every child, unlike a pool or an alternatives list.
                for (JsonElement childElement : children) {
                    emit(childElement.getAsJsonObject(), reached, reachedExactly, tables, accumulator, depth);
                }
            }
            case "minecraft:tag" -> {
                List<ResourceLocation> items = tagItems(string(entry, "name", null));
                if (items.isEmpty()) {
                    accumulator.markApproximate();
                    return;
                }
                int count = count(entry);
                for (ResourceLocation item : items) {
                    accumulator.add(item, count, reached, reachedExactly);
                }
            }
            case "minecraft:loot_table" -> {
                ResourceLocation nested = resource(entry, "value");
                if (nested == null) {
                    nested = resource(entry, "name");
                }
                if (nested == null) {
                    accumulator.markApproximate();
                    return;
                }
                walk(nested, tables, reached, reachedExactly, accumulator, depth + 1);
            }
            default -> {
                LOGGER.warn("[Wildfires] unsupported loot table entry type {} in a deposit table", type);
                accumulator.markApproximate();
            }
        }
    }

    private static Condition condition(JsonObject owner) {
        float chance = 1.0F;
        boolean exact = true;
        JsonArray conditions = owner.getAsJsonArray("conditions");
        if (conditions != null) {
            for (JsonElement element : conditions) {
                JsonObject condition = element.getAsJsonObject();
                if ("minecraft:random_chance".equals(string(condition, "condition", ""))) {
                    chance *= number(condition, "chance", 0.0F);
                } else {
                    // Anything else cannot be turned into a number. The item still belongs in the list,
                    // but its chance is not something this code can vouch for.
                    exact = false;
                }
            }
        }
        return new Condition(chance, exact);
    }

    /** Expands an item tag. The only part of this class that needs the item registry. */
    private static List<ResourceLocation> tagItems(String name) {
        if (name == null) {
            return List.of();
        }
        ResourceLocation id = ResourceLocation.tryParse(name.startsWith("#") ? name.substring(1) : name);
        if (id == null) {
            return List.of();
        }
        ITagManager<Item> tags = ForgeRegistries.ITEMS.tags();
        if (tags == null) {
            return List.of();
        }
        ITag<Item> tag = tags.getTag(ItemTags.create(id));
        if (tag == null) {
            return List.of();
        }
        List<ResourceLocation> items = new ArrayList<>();
        for (Item item : tag) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
            if (key != null) {
                items.add(key);
            }
        }
        return items;
    }

    /**
     * Only {@code set_count} is read. A bare {@code count} field is deliberately ignored: Minecraft's
     * loot item builds {@code new ItemStack(item)} and lets functions resize it, so an entry carrying
     * {@code count} still drops a single item and showing more would be wrong.
     */
    private static int count(JsonObject entry) {
        int count = 1;
        JsonArray functions = entry.getAsJsonArray("functions");
        if (functions != null) {
            for (JsonElement element : functions) {
                JsonObject function = element.getAsJsonObject();
                if ("minecraft:set_count".equals(string(function, "function", ""))) {
                    count = (int) number(function, "count", count);
                }
            }
        }
        return Math.max(1, count);
    }

    private static int rollCount(JsonObject owner) {
        return Math.max(1, (int) number(owner, "rolls", 1.0F));
    }

    private static float weight(JsonObject entry) {
        return Math.max(0.0F, number(entry, "weight", 1.0F));
    }

    private static String string(JsonObject owner, String key, String fallback) {
        JsonElement element = owner.get(key);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsString();
    }

    private static float number(JsonObject owner, String key, float fallback) {
        JsonElement element = owner.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsFloat();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static ResourceLocation resource(JsonObject owner, String key) {
        String value = string(owner, key, null);
        return value == null ? null : ResourceLocation.tryParse(value);
    }

    private record Condition(float chance, boolean exact) {
    }

    /**
     * Sums the chances of items that appear more than once. TFC does this on purpose - a loose rock can
     * be rolled for twice at different odds - and two identical JEI entries would only be confusing.
     */
    private static final class Accumulator {

        private final Map<Key, Chance> byItem = new LinkedHashMap<>();

        void add(ResourceLocation item, int count, float chance, boolean exact) {
            if (item == null || chance <= 0.0F) {
                return;
            }
            byItem.computeIfAbsent(new Key(item, count), key -> new Chance()).merge(chance, exact);
        }

        void markApproximate() {
            for (Chance chance : byItem.values()) {
                chance.exact = false;
            }
        }

        List<DepositDrop> toDrops() {
            List<DepositDrop> drops = new ArrayList<>(byItem.size());
            for (Map.Entry<Key, Chance> entry : byItem.entrySet()) {
                drops.add(new DepositDrop(entry.getKey().item(), entry.getKey().count(),
                        Math.min(1.0F, entry.getValue().chance), entry.getValue().exact));
            }
            drops.sort(Comparator.comparingDouble((DepositDrop drop) -> drop.chance()).reversed()
                    .thenComparing(drop -> drop.item().toString()));
            return drops;
        }
    }

    private record Key(ResourceLocation item, int count) {
    }

    private static final class Chance {
        private float chance;
        private boolean exact = true;

        void merge(float amount, boolean isExact) {
            chance += amount;
            exact &= isExact;
        }
    }
}
