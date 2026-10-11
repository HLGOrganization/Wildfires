package first.wildfires.mixin.tfc.client;

import first.wildfires.client.smithing.AnvilMemoryStrip;
import first.wildfires.client.smithing.PerfectForgeOverlay;
import net.dries007.tfc.client.screen.AnvilScreen;
import net.dries007.tfc.common.container.AnvilContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws the anvil's one-click forge button and the memory strip above its panel.
 *
 * <p>Hangs off {@code AbstractContainerScreen} rather than off {@code AnvilScreen} for a plain reason:
 * neither {@code render} nor {@code mouseClicked} is declared by TFC's anvil screen, so there is nothing
 * there to inject into. Both are declared here, and each handler returns immediately unless the screen is
 * an anvil - so every other container screen pays only an {@code instanceof}.
 *
 * <p>Rendering is at the tail of {@code render}, which is after the slots, the labels and the vanilla
 * tooltips, so the button ends up on top of the panel rather than underneath it. The memory strip goes
 * through the same hook because it sits above the panel rather than in it, and because TFC's screen may
 * take over {@code renderBg} and {@code renderLabels} without calling back into them. The strip is drawn
 * before the button so that the button's hover text, which appears where the pointer is, is not covered.
 *
 * <p>These are Minecraft methods, so they are named as the development mappings do and remapped through
 * the refmap, rather than written as SRG names - the same treatment the accessors in
 * {@code minecraft} get.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class PerfectForgeOverlayMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void wildfires$renderPerfectForge(GuiGraphics graphics, int mouseX, int mouseY,
                                              float partialTick, CallbackInfo ci) {
        if (!(((Object) this) instanceof AnvilScreen screen)) {
            return;
        }
        // The strip first: it sits above the panel and the button's hover tooltip is drawn where the
        // pointer already is, so the tooltip ends up on top of everything rather than under the strip.
        AnvilMemoryStrip.render(graphics, screen.getMenu(), screen.getGuiLeft(), screen.getGuiTop());
        PerfectForgeOverlay.render(graphics, screen.getMenu(),
                screen.getGuiLeft(), screen.getGuiTop(), mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void wildfires$clickPerfectForge(double mouseX, double mouseY, int button,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 || !(((Object) this) instanceof AnvilScreen screen)) {
            return;
        }
        if (PerfectForgeOverlay.click(screen.getGuiLeft(), screen.getGuiTop(), mouseX, mouseY)) {
            cir.setReturnValue(true);
        }
    }
}
