package first.wildfires.mixin.tfc;

import net.dries007.tfc.common.recipes.PotRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the temperature a pot recipe needs.
 *
 * <p>Every pot recipe carries one - it is the {@code temperature} field of the recipe JSON, parsed into
 * {@code PotRecipe#minTemp}, and {@code isHotEnough(float)} is the only thing TFC does with it. That
 * method answers "is this pot hot enough right now", which says nothing about <em>how</em> hot the
 * recipe wants the pot to be, so a JEI page that shows the number has to read the field.
 *
 * <p>The field is protected with no getter, and it lives on the base class, so one accessor covers the
 * fluid, item, soup and jam pot recipes alike.
 */
@Mixin(value = PotRecipe.class, remap = false)
public interface PotRecipeAccessor {

    @Accessor("minTemp")
    float wildfires$getMinTemp();
}
