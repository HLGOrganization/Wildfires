package first.wildfires.jei;

import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Draws a covered JEI slot from the stacks that still carry their TFC food traits.
 *
 * <p>JEI reduces every ingredient to {@code Item + tag} and drops the Forge capabilities that food
 * traits live in. That breaks two things at once: the model override never fires, so Firmalife's
 * {@code firmalife:dry} property keeps a dried food's fresh texture, and TFC's tooltip handler finds
 * no food capability, so the trait line disappears and the icon quietly contradicts the recipe.
 * Drawing the original stack fixes both, because icon and tooltip then come from the same data.
 *
 * <p>A tag ingredient such as {@code tfc:foods/fruits} produces one trait-carrying stack per item,
 * and JEI cycles through all of them, so one original is kept per item and the substitution is
 * looked up by item type.
 *
 * <p>TFC's JEI category code already marks those stacks non-decaying, so the tooltip gains the trait
 * line without any spoilage line.
 */
public final class FoodTraitItemRenderer implements IIngredientRenderer<ItemStack> {

    /** TFC's display stacks for this slot, keyed by item: each still carries its food traits. */
    private final Map<Item, ItemStack> sources;

    public FoodTraitItemRenderer(Map<Item, ItemStack> sources) {
        this.sources = Map.copyOf(sources);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, @NotNull ItemStack ingredient) {
        render(guiGraphics, ingredient, 0, 0);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, @NotNull ItemStack ingredient, int posX, int posY) {
        ItemStack display = wildfires$displayStack(ingredient);
        if (display.isEmpty()) {
            return;
        }

        RenderSystem.enableDepthTest();
        Font font = Minecraft.getInstance().font;
        guiGraphics.renderFakeItem(display, posX, posY);
        guiGraphics.renderItemDecorations(font, display, posX, posY);
        RenderSystem.disableBlend();
    }

    @Override
    public @NotNull List<Component> getTooltip(@NotNull ItemStack ingredient, @NotNull TooltipFlag tooltipFlag) {
        Player player = Minecraft.getInstance().player;
        return wildfires$displayStack(ingredient).getTooltipLines(player, tooltipFlag);
    }

    @Override
    public void getTooltip(@NotNull ITooltipBuilder tooltip, @NotNull ItemStack ingredient, @NotNull TooltipFlag tooltipFlag) {
        tooltip.addAll(getTooltip(ingredient, tooltipFlag));
    }

    @Override
    public int getWidth() {
        return 16;
    }

    @Override
    public int getHeight() {
        return 16;
    }

    /**
     * Substitutes TFC's stack for the requested item.
     *
     * <p>Anything without a recorded original is drawn exactly as JEI would draw it, so an
     * unexpected ingredient can never render as something else.
     */
    @NotNull
    private ItemStack wildfires$displayStack(@NotNull ItemStack ingredient) {
        if (ingredient.isEmpty()) {
            return ingredient;
        }

        ItemStack source = sources.get(ingredient.getItem());
        return source == null || source.isEmpty() ? ingredient : source;
    }
}