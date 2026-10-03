package first.wildfires.jei;

import com.mojang.logging.LogUtils;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.dries007.tfc.util.Pannable;
import net.dries007.tfc.util.Sluiceable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITag;
import net.minecraftforge.registries.tags.ITagManager;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Collects the sluice and gold pan deposits to show in JEI.
 *
 * <p>TFC has no JEI category for either device, and there is no recipe to show even if it did: a
 * {@code Sluiceable} or {@code Pannable} only points at a loot table, which Minecraft never sends to
 * the client and offers no way to inspect. The tables are therefore read straight out of the loaded
 * data packs, which is why this opens a {@link PackType#SERVER_DATA} resource manager - the client's
 * own one only covers {@code assets/} and would not find {@code data/} at all.
 *
 * <p>Reading the client's packs rather than asking the server means a loot table that only lives in a
 * server-side data pack would not be reflected here, and that is the normal case for this pack: every
 * deposit table it uses is defined in {@code kubejs/data}, which KubeJS gives to the server alone. That
 * folder is therefore read off the disk as well, and it takes precedence over the packs - the server
 * loads it after them, so its tables are the ones that really drop.
 */
public final class DepositRecipes {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation SLUICE_ICON = ResourceLocation.fromNamespaceAndPath("tfc", "wood/sluice/oak");
    private static final ResourceLocation PAN_ICON = ResourceLocation.fromNamespaceAndPath("tfc", "pan/filled");
    private static final ResourceLocation SLUICES_TAG = ResourceLocation.fromNamespaceAndPath("tfc", "sluices");
    private static final ResourceLocation PAN_FILLED = ResourceLocation.fromNamespaceAndPath("tfc", "pan/filled");
    private static final ResourceLocation PAN_EMPTY = ResourceLocation.fromNamespaceAndPath("tfc", "pan/empty");

    private static List<DepositLootRecipe> sluicing = List.of();
    private static List<DepositLootRecipe> panning = List.of();

    private DepositRecipes() {
    }

    public static void registerCategories(IRecipeCategoryRegistration registration, IGuiHelper guiHelper) {
        collect();
        registration.addRecipeCategories(
                new DepositRecipeCategory(DepositRecipeCategory.SLUICING,
                        Component.translatable("wildfires.jei.category.sluicing"),
                        icon(SLUICE_ICON), guiHelper, DepositRecipeCategory.rowsFor(sluicing)),
                new DepositRecipeCategory(DepositRecipeCategory.PANNING,
                        Component.translatable("wildfires.jei.category.panning"),
                        icon(PAN_ICON), guiHelper, DepositRecipeCategory.rowsFor(panning)));
    }

    public static void registerRecipes(IRecipeRegistration registration) {
        if (sluicing.isEmpty() && panning.isEmpty()) {
            // Normally already collected while registering the categories, which have to be sized from
            // the recipes first. This only covers recipes being registered on their own.
            collect();
        }
        registration.addRecipes(DepositRecipeCategory.SLUICING, sluicing);
        registration.addRecipes(DepositRecipeCategory.PANNING, panning);
    }

    public static void registerCatalysts(IRecipeCatalystRegistration registration) {
        List<ItemLike> sluices = tagItems(SLUICES_TAG);
        if (!sluices.isEmpty()) {
            registration.addRecipeCatalysts(DepositRecipeCategory.SLUICING, sluices.toArray(ItemLike[]::new));
        }
        addCatalyst(registration, PAN_FILLED, DepositRecipeCategory.PANNING);
        addCatalyst(registration, PAN_EMPTY, DepositRecipeCategory.PANNING);
    }

    private static void collect() {
        try {
            List<PackResources> packs = Minecraft.getInstance().getResourcePackRepository().openAllSelected();
            try (MultiPackResourceManager manager = new MultiPackResourceManager(PackType.SERVER_DATA, packs)) {
                sluicing = build(manager, true);
                panning = build(manager, false);
            }
        } catch (Exception e) {
            LOGGER.error("[Wildfires] could not read the sluice and panning loot tables", e);
            sluicing = List.of();
            panning = List.of();
        }
        if (sluicing.isEmpty() && panning.isEmpty()) {
            LOGGER.warn("[Wildfires] no sluice or panning deposits were registered with JEI. TFC syncs these to"
                    + " the client on login, so this normally means JEI registered before that arrived.");
        } else {
            LOGGER.info("[Wildfires] JEI shows {} sluicing and {} panning deposits",
                    sluicing.size(), panning.size());
        }
    }

    private static List<DepositLootRecipe> build(ResourceManager manager, boolean sluicing) {
        List<DepositLootRecipe> recipes = new ArrayList<>();
        if (sluicing) {
            for (Sluiceable definition : Sluiceable.MANAGER.getValues()) {
                List<ItemStack> inputs = new ArrayList<>();
                for (Item item : definition.getValidItems()) {
                    inputs.add(new ItemStack(item));
                }
                add(recipes, manager, definition.getId(), inputs, definition.getLootTable());
            }
        } else {
            for (Pannable definition : Pannable.MANAGER.getValues()) {
                List<ItemStack> inputs = new ArrayList<>();
                for (Block block : definition.getIngredient().blocks()) {
                    if (block.asItem() != Items.AIR) {
                        inputs.add(new ItemStack(block.asItem()));
                    }
                }
                add(recipes, manager, definition.getId(), inputs, definition.getLootTable());
            }
        }
        recipes.sort(Comparator.comparing(recipe -> recipe.id().toString()));
        return recipes;
    }

    private static void add(List<DepositLootRecipe> recipes, ResourceManager manager, ResourceLocation id,
                            List<ItemStack> inputs, ResourceLocation lootTable) {
        if (inputs.isEmpty()) {
            LOGGER.warn("[Wildfires] deposit {} has no items to show as its input", id);
            return;
        }
        recipes.add(new DepositLootRecipe(id, inputs,
                toDisplay(DepositLoot.resolve(manager, lootTable, unpackedData()), lootTable)));
    }

    /**
     * KubeJS keeps its data pack in {@code kubejs/data} and registers it with the server alone, so the
     * client's packs never contain it - yet this pack defines every deposit table there.
     */
    private static Path unpackedData() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("kubejs").resolve("data");
    }

    /** Turns the parsed drops into stacks JEI can draw, dropping any item the registry does not know. */
    private static List<LootDrop> toDisplay(List<DepositDrop> drops, ResourceLocation lootTable) {
        List<LootDrop> displayed = new ArrayList<>(drops.size());
        for (DepositDrop drop : drops) {
            Item item = ForgeRegistries.ITEMS.getValue(drop.item());
            if (item == null || item == Items.AIR) {
                LOGGER.warn("[Wildfires] loot table {} names unknown item {}", lootTable, drop.item());
                continue;
            }
            displayed.add(new LootDrop(new ItemStack(item, drop.count()), drop.chance(), drop.exact()));
        }
        return displayed;
    }

    private static void addCatalyst(IRecipeCatalystRegistration registration, ResourceLocation itemId,
                                    RecipeType<DepositLootRecipe> type) {
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null || item == Items.AIR) {
            LOGGER.warn("[Wildfires] JEI catalyst {} is missing, so it will not be listed for this category", itemId);
            return;
        }
        registration.addRecipeCatalyst(item, type);
    }

    private static List<ItemLike> tagItems(ResourceLocation tagId) {
        ITagManager<Item> tags = ForgeRegistries.ITEMS.tags();
        if (tags == null) {
            return List.of();
        }
        ITag<Item> tag = tags.getTag(ItemTags.create(tagId));
        List<ItemLike> items = new ArrayList<>();
        if (tag != null) {
            for (Item item : tag) {
                items.add(item);
            }
        }
        if (items.isEmpty()) {
            LOGGER.warn("[Wildfires] tag {} is empty or missing, so this category has no JEI catalyst", tagId);
        }
        return items;
    }

    private static ItemStack icon(ResourceLocation itemId) {
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }
}
