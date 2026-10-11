package first.wildfires.jei;

import first.wildfires.mixin.tfc.PotRecipeAccessor;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.category.extensions.IRecipeCategoryDecorator;
import net.dries007.tfc.common.recipes.PotRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Writes the temperature a pot recipe needs in the corner of its JEI page.
 *
 * <p>A pot recipe only cooks while the pot is above its own temperature, and the pack uses several:
 * boiling ore slurry into purified dust wants 100, the pack's wood chip and hot water recipes want
 * 200, cooking and jam want 300, and turning wood ash into lye wants 600. JEI drew all of them
 * identically, so the page could not tell you which pot you needed to build - the number is in the
 * recipe JSON and nowhere on screen.
 *
 * <p>The line goes in the bottom corner that both of TFC's pot layouts leave empty: the 110x100 item
 * and fluid pot page keeps its slots above y=84, and the 175x50 soup and jam pages keep theirs above
 * y=44, so {@code height - 10} clears the slots of both. It is drawn right aligned so a long line grows
 * towards the middle of the page instead of off it.
 *
 * <p>Presentation only: nothing here changes what a pot does, and a recipe without a temperature (0,
 * which no shipped recipe uses) draws nothing at all.
 */
public class PotTemperatureDecorator implements IRecipeCategoryDecorator<PotRecipe> {

    /** JEI's own body text colour, so the line reads as part of the page rather than as an overlay. */
    private static final int COLOUR = 0xFF404040;
    private static final int MARGIN = 3;
    private static final int BOTTOM_INSET = 10;

    @Override
    public void draw(@NotNull PotRecipe recipe, @NotNull IRecipeCategory<PotRecipe> category, @NotNull IRecipeSlotsView recipeSlots, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        final float minTemp = ((PotRecipeAccessor) recipe).wildfires$getMinTemp();
        if (minTemp <= 0f) {
            return; // No requirement to show.
        }

        final Font font = Minecraft.getInstance().font;
        final Component text = Component.translatable("wildfires.jei.pot.temperature", format(minTemp));
        final IDrawable background = category.getBackground();
        final int x = Math.max(MARGIN, background.getWidth() - MARGIN - font.width(text));
        final int y = Math.max(MARGIN, background.getHeight() - BOTTOM_INSET);
        graphics.drawString(font, text, x, y, COLOUR, false);
    }

    /** The number as the recipe writes it: no trailing ".0", but nothing lost if one ever has decimals. */
    private static String format(float temperature) {
        return temperature == Math.floor(temperature)
            ? Integer.toString((int) temperature)
            : String.format(Locale.ROOT, "%.1f", temperature);
    }
}
