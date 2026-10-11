package first.wildfires.compat.vintageimprovements;

import com.mojang.logging.LogUtils;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * <h2>What this is</h2>
 * The wiring half of {@link CurvingPressRules}: it reads the watched ingredient slot of a recipe,
 * decides with the rule whether Vintage Improvements would throw on it, and writes the one line that
 * names the recipe when it would. The mixin half is
 * {@code first.wildfires.mixin.vintageimprovements.CurvingPressGuardMixin}, which calls
 * {@link #refuses(Recipe, int)} at the head of
 * {@code com.negodya1.vintageimprovements.content.kinetics.curving_press.CurvingPressBlockEntity.canCurve}
 * and turns a true answer into {@code false}.
 *
 * <h2>Why it lives here and not in the mixin</h2>
 * The mixin is {@code @Pseudo} and names its target as a string, so that Vintage Improvements stays
 * out of the compile classpath and out of the pack's mod list when it is absent. Nothing in a
 * {@code @Pseudo} mixin may therefore mention a type from the target mod - but this half needs none:
 * a recipe, its ingredients and the vanilla recipe-type registry are all the guard looks at, and the
 * ingame behaviour it has to replace is one array read.
 *
 * <h2>What the line is for</h2>
 * The refusal is the fix, but the cause is data: some ingredient in the recipe resolves to no items
 * at all, which is a tag, a food trait or a heatable item that was missing when the recipes were
 * read. That is a pack-side problem to correct, so the line names the recipe, the ingredient slot
 * and every other slot in the same recipe with the same defect, once per recipe per session
 * ({@link CurvingPressRules.ReportBudget}).
 */
public final class CurvingPressGuard {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String PREFIX = "[Wildfires VI] ";

    private static final CurvingPressRules.ReportBudget BUDGET = new CurvingPressRules.ReportBudget();

    private CurvingPressGuard() {
    }

    /**
     * Whether the caller must answer {@code false} instead of running the rest of {@code canCurve}.
     *
     * @param recipe the recipe {@code canCurve} was called with
     * @param mode   the mode {@code canCurve} was called with
     */
    public static boolean refuses(Recipe<?> recipe, int mode) {
        if (!(recipe instanceof CraftingRecipe)) {
            // canCurve answers false for these before it looks at any ingredient.
            return false;
        }
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        if (!CurvingPressRules.refuses(mode, ingredients.size(), slotAt(ingredients))) {
            return false;
        }
        report(recipe, mode, ingredients);
        return true;
    }

    /**
     * A view of the slots that never throws: a slot that cannot answer is reported as unreadable and
     * the rule treats that as a refusal, because the same read inside {@code canCurve} would throw.
     */
    private static IntFunction<CurvingPressRules.Slot> slotAt(List<Ingredient> ingredients) {
        return slot -> {
            Ingredient ingredient = ingredients.get(slot);
            boolean empty;
            try {
                empty = ingredient.isEmpty();
            } catch (RuntimeException e) {
                return new CurvingPressRules.Slot(false, CurvingPressRules.Slot.UNREADABLE);
            }
            if (empty) {
                return new CurvingPressRules.Slot(true, 0);
            }
            try {
                return new CurvingPressRules.Slot(false, ingredient.getItems().length);
            } catch (RuntimeException e) {
                return new CurvingPressRules.Slot(false, CurvingPressRules.Slot.UNREADABLE);
            }
        };
    }

    private static void report(Recipe<?> recipe, int mode, List<Ingredient> ingredients) {
        String key = recipeKey(recipe) + '|' + mode;
        if (!BUDGET.offer(key)) {
            if (BUDGET.limitJustReached()) {
                LOGGER.warn(PREFIX + "Refusing further curving recipes without a line each: {} distinct"
                                + " recipes have been named, anything after them is only counted.",
                        BUDGET.reported());
            }
            return;
        }
        int slot = CurvingPressRules.watchedSlot(mode);
        LOGGER.warn(PREFIX + "Refused curving recipe {} (mode {}, slot {}): {}{}. Vintage Improvements reads"
                        + " getItems()[0] of that slot without checking the length, which is the"
                        + " ArrayIndexOutOfBoundsException this guard turns into a plain false instead; the"
                        + " press and JEI skip the recipe from here on.",
                describe(recipe), mode, slot, defect(ingredients, slot), otherDefects(ingredients, slot));
    }

    /** What is wrong with the watched slot, as the tail of a sentence that ends in a full stop. */
    private static String defect(List<Ingredient> ingredients, int slot) {
        if (slot >= ingredients.size()) {
            return "the recipe has no ingredient in that slot, it has " + ingredients.size();
        }
        CurvingPressRules.Slot view = slotAt(ingredients).apply(slot);
        if (view.itemCount() == CurvingPressRules.Slot.UNREADABLE) {
            return "asking the ingredient for its items threw";
        }
        return "the ingredient is not empty but its item list is empty";
    }

    /**
     * The other slots of the same recipe with the same defect, so that one line says whether the
     * recipe is a one-off or one of a family that shares a broken tag. Empty when there are none.
     */
    private static String otherDefects(List<Ingredient> ingredients, int slot) {
        IntFunction<CurvingPressRules.Slot> views = slotAt(ingredients);
        List<String> broken = new ArrayList<>();
        for (int index = 0; index < ingredients.size(); index++) {
            if (index == slot) {
                continue;
            }
            CurvingPressRules.Slot view = views.apply(index);
            if (!view.empty() && view.itemCount() <= 0) {
                broken.add(String.valueOf(index));
            }
        }
        if (broken.isEmpty()) {
            return "";
        }
        if (broken.size() > 4) {
            int rest = broken.size() - 4;
            broken = broken.subList(0, 4);
            return "; other slots with the same defect: " + String.join(", ", broken) + " and " + rest + " more";
        }
        return "; other slots with the same defect: " + String.join(", ", broken);
    }

    private static String describe(Recipe<?> recipe) {
        ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        return recipeKey(recipe) + " (type " + (type == null ? String.valueOf(recipe.getType()) : type)
                + ", class " + recipe.getClass().getName() + ')';
    }

    /**
     * The identity the log budget is keyed on. Recipes that carry no id of their own - the special
     * crafting recipes do not - fall back to their class, which is what tells them apart.
     */
    private static String recipeKey(Recipe<?> recipe) {
        ResourceLocation id = recipe.getId();
        return id == null ? recipe.getClass().getName() : id.toString();
    }
}
