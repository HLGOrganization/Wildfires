package first.wildfires.mixin.firmalife;

import net.dries007.tfc.common.recipes.SimpleItemRecipe;
import net.dries007.tfc.compat.jei.category.SimpleItemRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds both configured drying times below Firmalife's drying arrow.
 *
 * Firmalife uses the same recipe class and JEI category for its drying mat and solar
 * drier. The category does not expose the selected catalyst, so both times are shown
 * on the shared drying recipe page.
 */
@Mixin(value = SimpleItemRecipeCategory.class, remap = false)
public abstract class SimpleItemRecipeCategoryMixin {

    private static final String FIRMALIFE_DRYING_RECIPE =
            "com.eerussianguy.firmalife.common.recipes.DryingRecipe";

    @Inject(
            method = "draw(Lnet/dries007/tfc/common/recipes/SimpleItemRecipe;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/client/gui/GuiGraphics;DD)V",
            at = @At("RETURN"),
            remap = false
    )
    private void wildfires$drawDryingTimes(
            SimpleItemRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics guiGraphics,
            double mouseX,
            double mouseY,
            CallbackInfo ci
    ) {
        if (!recipe.getClass().getName().equals(FIRMALIFE_DRYING_RECIPE)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        String text = Component.translatable("wildfires.jei.drying_times").getString();

        int x = 49 - minecraft.font.width(text) / 2;
        guiGraphics.drawString(minecraft.font, text, x, 17, 0x404040, false);
    }

}
