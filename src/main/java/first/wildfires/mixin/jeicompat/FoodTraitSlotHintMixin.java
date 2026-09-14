package first.wildfires.mixin.jeicompat;

import first.wildfires.jei.FoodTraitDisplayConfig;
import first.wildfires.jei.FoodTraitItemRenderer;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.library.gui.recipes.layout.builder.RecipeSlotBuilder;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.food.IFood;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps TFC food traits on JEI input slots for the items a pack cares about.
 *
 * <p>JEI normalises every ingredient to {@code Item + tag} and drops the Forge capabilities food
 * traits live in, which hides both the trait a recipe demands and the model override that shows a
 * dried food's texture. TFC's delegate ingredients attach the trait to the display stacks while
 * building them, and those stacks still carry their capabilities when they arrive here, so this is
 * the last point where the data can still be kept.
 *
 * <p>What to cover is configured; see {@link FoodTraitDisplayConfig}. Every slot is rejected by an
 * id or tag check first, and an ingredient that carries no trait at all is left untouched, so a plain
 * food keeps JEI's own rendering and never gains a spoilage line it would not otherwise show.
 */
@Mixin(value = RecipeSlotBuilder.class, remap = false)
public abstract class FoodTraitSlotHintMixin {

    /**
     * The covered stacks of this slot, keyed by item.
     *
     * <p>A builder instance belongs to one slot. An ingredient that spans a tag contributes one stack
     * per item and JEI cycles through all of them, and a slot may be filled by more than one call, so
     * the entries are accumulated and the renderer is rebuilt from the merged map.
     */
    @Unique
    private Map<Item, ItemStack> wildfires$traitSources;

    @Inject(
            method = "addIngredients(Lmezz/jei/api/ingredients/IIngredientType;Ljava/util/List;)"
                    + "Lmezz/jei/api/gui/builder/IRecipeSlotBuilder;",
            at = @At("RETURN"),
            remap = false
    )
    private void wildfires$keepFoodTraits(
            IIngredientType<?> ingredientType,
            List<?> ingredients,
            CallbackInfoReturnable<IRecipeSlotBuilder> cir
    ) {
        wildfires$apply(ingredientType, cir.getReturnValue(), ingredients);
    }

    @Inject(
            method = "addIngredient(Lmezz/jei/api/ingredients/IIngredientType;Ljava/lang/Object;)"
                    + "Lmezz/jei/api/gui/builder/IRecipeSlotBuilder;",
            at = @At("RETURN"),
            remap = false
    )
    private void wildfires$keepFoodTrait(
            IIngredientType<?> ingredientType,
            Object ingredient,
            CallbackInfoReturnable<IRecipeSlotBuilder> cir
    ) {
        wildfires$apply(ingredientType, cir.getReturnValue(), Collections.singletonList(ingredient));
    }

    /** Records the covered stacks of this call and installs the renderer that keeps their traits. */
    @Unique
    private void wildfires$apply(IIngredientType<?> ingredientType, IRecipeSlotBuilder slot, Collection<?> ingredients) {
        if (slot == null || ingredients == null || ingredients.isEmpty()) {
            return;
        }
        // Fluid slots carry their own ingredient type and never show a food model.
        if (ingredientType != VanillaTypes.ITEM_STACK) {
            return;
        }

        boolean changed = false;
        for (Object value : ingredients) {
            if (!(value instanceof ItemStack stack) || stack.isEmpty()) {
                continue;
            }
            // The configured list is checked first, so an unlisted item costs one map lookup.
            if (!FoodTraitDisplayConfig.covers(stack)) {
                continue;
            }
            // Only a stack that actually carries a trait has something to show. An ingredient with
            // no trait is left exactly as JEI renders it, so no plain food gains a spoilage line.
            IFood food = FoodCapability.get(stack);
            if (food == null || food.getTraits().isEmpty()) {
                continue;
            }
            if (wildfires$traitSources == null) {
                wildfires$traitSources = new LinkedHashMap<>();
            }
            // A copy is kept because JEI may reuse or mutate the stack it was handed.
            if (wildfires$traitSources.putIfAbsent(stack.getItem(), stack.copy()) == null) {
                changed = true;
            }
        }

        if (changed) {
            slot.setCustomRenderer(VanillaTypes.ITEM_STACK,
                    new FoodTraitItemRenderer(Map.copyOf(wildfires$traitSources)));
        }
    }
}