package first.wildfires.jei;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Chooses which items JEI keeps TFC food-trait data for.
 *
 * <p>JEI drops the Forge capabilities that food traits live in, so an ingredient written as
 * {@code tfc:has_trait} loses both the trait line a recipe depends on and the model override that
 * switches a dried food's texture. Firmalife's pineapple fibre is the usual example: JEI draws a
 * plain pineapple, hides the "Dried" line and keeps the fresh texture.
 *
 * <p>Entries are item ids ({@code tfc:food/banana}) or item tags ({@code #tfc:foods/fruits}), so a
 * pack can cover a whole food group with one line. The defaults list what Firmalife can dry, because
 * those change texture as well. Add any other item whose traits appear in a recipe: even without a
 * texture change, an icon that hides the terms a recipe demands would mislead.
 *
 * <p>Only listed items are touched. Everything else is rejected by an id or tag check before any
 * capability is read, which keeps the cost proportional to the configured list.
 */
public final class FoodTraitDisplayConfig {

    /**
     * Every TFC and Firmalife food.
     *
     * <p>{@code tfc:foods} is the widest group available: TFC fills it with its own foods and
     * Firmalife extends it with its own, fruits and grapes included. Covering the whole tag means no
     * food can hide the traits a recipe depends on, and it also covers the dryable subset whose
     * texture changes.
     */
    private static final List<String> DEFAULT_TRAIT_ITEMS = List.of(
            "#tfc:foods"
    );

    private static final ForgeConfigSpec CLIENT_SPEC;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> TRAIT_ITEMS;

    private static final Object PARSE_LOCK = new Object();
    private static volatile boolean parsed;
    private static volatile Set<ResourceLocation> itemIds = Set.of();
    private static volatile Set<ResourceLocation> tagIds = Set.of();

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("JEI display of TFC food traits, such as the dried variant of a fruit.",
                        "Entries are item ids (tfc:food/banana) or item tags (#tfc:foods).")
                .push("foodTraits");
        TRAIT_ITEMS = builder
                .comment("Items whose JEI input slots keep their food trait data.",
                        "The default covers every TFC and Firmalife food, including the dryable ones",
                        "whose texture changes and any food whose traits appear in a recipe.",
                        "Narrow it to a smaller tag or a few ids if the cost ever matters.",
                        "Leave empty to disable this feature entirely.")
                .defineListAllowEmpty("traitItems", DEFAULT_TRAIT_ITEMS, FoodTraitDisplayConfig::isValidEntry);
        builder.pop();
        CLIENT_SPEC = builder.build();
    }

    private FoodTraitDisplayConfig() {
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC,
                "wildfires-food-traits.toml");
    }

    /** True when the given config is this feature's client spec. */
    public static boolean isSpec(ModConfig config) {
        return config.getSpec() == CLIENT_SPEC;
    }

    /** Re-reads the configured entries, for example after the config was loaded or reloaded. */
    public static void refresh() {
        synchronized (PARSE_LOCK) {
            parsed = false;
            ensureParsed();
        }
    }

    /**
     * True when this stack should keep its food trait data in JEI.
     *
     * <p>Deliberately cheap: a listed item is found by id, and tags are only walked when the config
     * actually names a tag.
     */
    public static boolean covers(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ensureParsed();

        Item item = stack.getItem();
        Set<ResourceLocation> ids = itemIds;
        if (!ids.isEmpty() && ids.contains(ForgeRegistries.ITEMS.getKey(item))) {
            return true;
        }

        Set<ResourceLocation> tags = tagIds;
        if (tags.isEmpty()) {
            return false;
        }
        return stack.getTags().map(TagKey::location).anyMatch(tags::contains);
    }

    /**
     * Parses the entries once, on first use.
     *
     * <p>Before the config file is read, {@code get} answers with the defaults, so an early call
     * still yields a usable list; the config event re-reads it afterwards.
     */
    private static void ensureParsed() {
        if (parsed) {
            return;
        }
        synchronized (PARSE_LOCK) {
            if (parsed) {
                return;
            }

            Set<ResourceLocation> items = new HashSet<>();
            Set<ResourceLocation> tags = new HashSet<>();
            for (String entry : TRAIT_ITEMS.get()) {
                if (entry == null || entry.isEmpty()) {
                    continue;
                }
                boolean tag = entry.charAt(0) == '#';
                ResourceLocation id = ResourceLocation.tryParse(tag ? entry.substring(1) : entry);
                if (id == null) {
                    continue;
                }
                (tag ? tags : items).add(id);
            }
            itemIds = Set.copyOf(items);
            tagIds = Set.copyOf(tags);
            parsed = true;
        }
    }

    /** Accepts an item id or a {@code #namespace:path} tag id, so a typo cannot be read as an item. */
    private static boolean isValidEntry(Object value) {
        if (!(value instanceof String entry) || entry.isEmpty()) {
            return false;
        }
        String id = entry.charAt(0) == '#' ? entry.substring(1) : entry;
        return ResourceLocation.tryParse(id) != null;
    }
}