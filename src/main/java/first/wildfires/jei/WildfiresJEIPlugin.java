package first.wildfires.jei;

import com.mojang.logging.LogUtils;
import first.wildfires.Wildfires;
import first.wildfires.kinetic.loom.LoomBlockItem;
import first.wildfires.kinetic.loom.recipe.WeavingRecipe;
import first.wildfires.kinetic.loom.recipe.WeavingRecipeType;
import first.wildfires.register.BlockRegister;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.dries007.tfc.common.recipes.PotRecipe;
import net.dries007.tfc.compat.jei.JEIIntegration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.List;

@JeiPlugin
public class WildfiresJEIPlugin implements IModPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final PotTemperatureDecorator POT_TEMPERATURE = new PotTemperatureDecorator();

    /**
     * Pot pages other mods build on top of TFC's pot. Each is its own JEI recipe type, so each needs its
     * own registration, and the classes holding those recipe types belong to mods this one does not
     * compile against - hence the reflective lookup. Both of those pages use TFC's own pot layouts
     * (110x100 and 175x50), so the temperature line lands in the same free corner it does on TFC's pages.
     *
     * <p>A missing, renamed or retyped entry costs that one page its line and nothing else.
     */
    private static final String[][] ADDON_POT_TYPES = {
        {"net.mrhitech.artisanal.compat.jei.JEIIntegration", "SCALABLE_POT"},
        {"com.eerussianguy.firmalife.compat.jei.FLJEIPlugin", "BOWL_POT"},
        {"com.eerussianguy.firmalife.compat.jei.FLJEIPlugin", "STINKY_SOUP"},
    };

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return Wildfires.rl("jei_plugin");
    }

    /**
     * Writes each pot recipe's own temperature onto its JEI page.
     *
     * <p>One decorator per TFC pot recipe type covers every pot recipe behind that type, including the
     * ones the pack adds through KubeJS and the ones other addons put on the same TFC pot: the number
     * comes from the recipe itself, not from the mod that registered it.
     */
    @Override
    public void registerAdvanced(@NotNull IAdvancedRegistration registration) {
        registration.addRecipeCategoryDecorator(JEIIntegration.SIMPLE_POT, POT_TEMPERATURE);
        registration.addRecipeCategoryDecorator(JEIIntegration.SOUP_POT, POT_TEMPERATURE);
        registration.addRecipeCategoryDecorator(JEIIntegration.JAM_POT, POT_TEMPERATURE);
        registerAddonPotDecorators(registration);
    }

    /** Reads the pot recipe types of the addons that put their own pages on TFC's pot. */
    @SuppressWarnings("unchecked")
    private static void registerAddonPotDecorators(IAdvancedRegistration registration) {
        for (String[] entry : ADDON_POT_TYPES) {
            final String holder = entry[0];
            final String field = entry[1];
            try {
                final Object value = Class.forName(holder).getField(field).get(null);
                if (value instanceof RecipeType<?> type && PotRecipe.class.isAssignableFrom(type.getRecipeClass())) {
                    registration.addRecipeCategoryDecorator((RecipeType<PotRecipe>) type, POT_TEMPERATURE);
                } else {
                    LOGGER.warn("[Wildfires JEI] {}#{} no longer holds a pot recipe type, so that pot page will not show a temperature", holder, field);
                }
            } catch (ClassNotFoundException e) {
                LOGGER.debug("[Wildfires JEI] {} is not installed, so its pot page keeps JEI's own layout", holder);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                LOGGER.warn("[Wildfires JEI] could not read the pot recipe type {}#{}, so that pot page will not show a temperature", holder, field, e);
            }
        }
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new WeavingRecipeCategory(guiHelper));
        DepositRecipes.registerCategories(registration, guiHelper);
    }

    @Override
    public void registerRecipeCatalysts(@NotNull IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(BlockRegister.LoomControlBlock.get().asItem(), WeavingRecipeCategory.RECIPE_TYPE);
        DepositRecipes.registerCatalysts(registration);
    }

    @Override
    public void registerRecipes(@NotNull IRecipeRegistration registration) {
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            List<WeavingRecipe> recipes = level.getRecipeManager().getAllRecipesFor(WeavingRecipeType.INSTANCE);
            registration.addRecipes(WeavingRecipeCategory.RECIPE_TYPE, recipes);
        }
        // Not tied to the level: the deposits come from TFC's synced registries and the loaded data packs.
        DepositRecipes.registerRecipes(registration);
    }
}
