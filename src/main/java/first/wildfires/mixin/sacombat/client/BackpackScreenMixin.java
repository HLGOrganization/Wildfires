package first.wildfires.mixin.sacombat.client;

import com.ogaba.sa_survival.client.screen.BackpackScreen;
import com.ogaba.sa_survival.menu.BackpackMenu;
import first.wildfires.client.sacombat.BackpackPanelRenderer;
import first.wildfires.compat.sacombat.BackpackPanelLayout;
import first.wildfires.mixin.minecraft.ContainerScreenOriginAccessor;
import first.wildfires.mixin.minecraft.ScreenTextAccessor;
import first.wildfires.mixin.sacombat.BackpackMenuGeometryAccessor;
import first.wildfires.mixin.sacombat.BackpackTitleColorInvoker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the vertical panel background for a backpack opened by right-clicking.
 *
 * <h2>What changes</h2>
 *
 * <p>{@code BackpackScreen#renderBg} paints the container background. On the generic layout it blits
 * vanilla's {@code generic_54} sheet, whose nine-wide grid is baked into the texture - the reason a
 * 15-slot satchel appeared to have 18 slots. This injection replaces that background with the modpack's
 * own panel artwork, arranged with the backpack above the player inventory.
 *
 * <p>{@link BackpackPanelRenderer} does the drawing and decides whether a menu is one we rebuilt. When
 * it declines, the original body runs untouched, so no other screen is affected.
 *
 * <h2>Why two injectors</h2>
 *
 * <p>{@code renderBg} is declared by Minecraft and merely overridden, so its physical name depends on
 * the environment: the shipped jar uses the SRG name {@code m_7286_} while a development workspace
 * would see {@code renderBg}. This mixin targets a mod class by name, so no refmap entry can bridge the
 * two. Both are injected with {@code require = 0}; exactly one matches per environment and the other is
 * skipped, matching how the other Survivors Arsenal mixins in this mod handle the same problem.
 *
 * <h2>Slot coordinates</h2>
 *
 * <p>Only the background is replaced. The slots are real {@code Slot} objects placed by
 * {@code BackpackPanelMenuMixin}, and the container screen draws items from those coordinates, so
 * items, tooltips, drag-and-drop and shift-click keep working untouched.
 */
@Mixin(value = BackpackScreen.class, remap = false)
public abstract class BackpackScreenMixin {

    /**
     * The colour vanilla gives both container labels, {@code 0x404040}.
     *
     * <p>Kept here because this mixin redraws the labels itself: the backpack's name takes the overlay's
     * cream, while the "Inventory" label stays exactly as the game would have drawn it.
     */
    private static final int VANILLA_LABEL_COLOR = 4210752;

    /**
     * Nudges both labels onto the artwork drawn here.
     *
     * <p>{@code BackpackScreen} places the title at {@code panelY + 6} and the "Inventory" label 72px
     * below the inventory origin - both measured for the layout it was written for, whose panel had a
     * shorter header and whose player inventory began with a row of armour slots. Neither matches the
     * artwork used here, so the title is lifted by {@link BackpackPanelLayout#TITLE_SHIFT_Y} onto the
     * taller header, and the inventory label by
     * {@link BackpackPanelLayout#INVENTORY_LABEL_SHIFT_Y} into the gap above the sheet.
     *
     * <p>Only the labels' y values are touched; every other coordinate the constructor computed is left
     * alone.
     */
    @Inject(
            method = "<init>(Lcom/ogaba/sa_survival/menu/BackpackMenu;"
                    + "Lnet/minecraft/world/entity/player/Inventory;"
                    + "Lnet/minecraft/network/chat/Component;)V",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private void wildfires$shiftLabels(CallbackInfo ci) {
        ContainerScreenOriginAccessor labels = (ContainerScreenOriginAccessor) this;
        labels.wildfires$setTitleLabelY(
                labels.wildfires$getTitleLabelY() + BackpackPanelLayout.TITLE_SHIFT_Y);
        labels.wildfires$setInventoryLabelY(
                labels.wildfires$getInventoryLabelY() + BackpackPanelLayout.INVENTORY_LABEL_SHIFT_Y);
    }

    /** Production name, matching the SRG-mapped runtime. */
    @Inject(method = "m_280003_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void wildfires$colouredLabels(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        wildfires$drawLabels(graphics, ci);
    }

    /** Development name, matching the Mojang-mapped client. */
    @Inject(method = "renderLabels", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void wildfires$colouredLabelsDev(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        wildfires$drawLabels(graphics, ci);
    }

    /**
     * Redraws both labels, giving the backpack's name the colour the inventory overlay uses for it.
     *
     * <p>Vanilla draws both labels in {@code 0x404040}, a dark grey chosen for the pale container
     * backgrounds it was written against. The panel used here is dark, so the name all but disappears on
     * it. The overlay that draws the same backpack when it is opened with the panel key uses a light
     * cream instead, together with a coloured shadow laid at a one-pixel offset rather than the font's
     * own drop shadow. Both colours are read back from that overlay through
     * {@link BackpackTitleColorInvoker}, so the two screens cannot drift apart, and the shadow is drawn
     * the same way, so the text looks identical rather than merely similar.
     *
     * <p>The "Inventory" label keeps vanilla's colour; only the backpack's name was asked to change.
     *
     * <p>If the backpack stack cannot be read there is nothing to ask the overlay about, so the method
     * returns without cancelling and vanilla's own labels are drawn.
     */
    private void wildfires$drawLabels(GuiGraphics graphics, CallbackInfo ci) {
        if (!(((Object) this) instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        if (!(screen.getMenu() instanceof BackpackMenu menu)) {
            return;
        }

        ItemStack stack = ((BackpackMenuGeometryAccessor) menu).wildfires$getBackpackStack();
        if (stack == null || stack.isEmpty()) {
            return;
        }

        ContainerScreenOriginAccessor labels = (ContainerScreenOriginAccessor) screen;
        ScreenTextAccessor text = (ScreenTextAccessor) screen;
        Font font = text.wildfires$getFont();
        int color = BackpackTitleColorInvoker.wildfires$getTitleColor(stack);
        int shadow = BackpackTitleColorInvoker.wildfires$getTextShadowColor(stack);

        // Shadow first, one pixel down and right, exactly as the overlay's own label helper does.
        graphics.drawString(font, text.wildfires$getTitle(),
                labels.wildfires$getTitleLabelX() + 1, labels.wildfires$getTitleLabelY() + 1,
                shadow, false);
        graphics.drawString(font, text.wildfires$getTitle(),
                labels.wildfires$getTitleLabelX(), labels.wildfires$getTitleLabelY(),
                color, false);

        graphics.drawString(font, labels.wildfires$getPlayerInventoryTitle(),
                labels.wildfires$getInventoryLabelX(), labels.wildfires$getInventoryLabelY(),
                VANILLA_LABEL_COLOR, false);

        ci.cancel();
    }

    /** Production name, matching the SRG-mapped runtime. */
    @Inject(method = "m_7286_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void wildfires$verticalPanelBackground(GuiGraphics graphics, float partialTick,
                                                   int mouseX, int mouseY, CallbackInfo ci) {
        wildfires$renderPanel(graphics, ci);
    }

    /** Development name, matching the Mojang-mapped client. */
    @Inject(method = "renderBg", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void wildfires$verticalPanelBackgroundDev(GuiGraphics graphics, float partialTick,
                                                      int mouseX, int mouseY, CallbackInfo ci) {
        wildfires$renderPanel(graphics, ci);
    }

    private void wildfires$renderPanel(GuiGraphics graphics, CallbackInfo ci) {
        // `this` is the mixin type, so the target screen is reached through an unchecked cast.
        if (!(((Object) this) instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        if (!(screen.getMenu() instanceof BackpackMenu menu)) {
            return;
        }

        // Read the origin through the accessor, since the fields are protected and this mixin targets
        // a mod class by name, where @Shadow cannot be remapped reliably.
        ContainerScreenOriginAccessor origin = (ContainerScreenOriginAccessor) screen;

        ItemStack stack = ((BackpackMenuGeometryAccessor) menu).wildfires$getBackpackStack();
        if (BackpackPanelRenderer.render(graphics, menu, stack,
                origin.wildfires$getLeftPos(), origin.wildfires$getTopPos())) {
            // Background handled here; the original method must not also draw its own.
            ci.cancel();
        }
    }
}
