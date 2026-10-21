package first.wildfires.mixin.apricityui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Makes the item carried on the cursor match the enlarged soldering slots.
 *
 * <h2>What AUI does</h2>
 * {@code ApricityContainerScreen#captureFloatingItem} calls
 * {@code RenderNode$ItemNode.positioned(Supplier, x, y, scale, z, decorations, overlay, decorationOffsetY, ghost)}
 * with a hard-coded {@code 1.0} for {@code scale} (the fourth argument, index 3; bytecode
 * {@code dconst_1}). Slot items go through the same {@code ItemNode} but read
 * {@code --aui-slot-icon-scale} through {@code Slot.resolveIconScale}; the floating item has no
 * slot ancestor, so it stays 16x16 even when the slots use {@code 2.6667}.
 *
 * <h2>What this changes</h2>
 * Only the {@code scale} argument of that {@code positioned} call is rewritten, and only while the
 * current screen is the soldering screen. Other AUI container screens keep scale 1.0. The value
 * matches the 48px player backpack slot in {@code apricity/screens/soldering.css}:
 * {@code 48 * 16/18 / 16 = 2.6667}. The 40px machine slots use 2.2222; a carried item is always
 * drawn at the backpack ratio, which is the size the player asked for while moving items.
 *
 * <h2>Optional-mod safety</h2>
 * ApricityUI is not a compile dependency. The mixin is {@code @Pseudo}, names its target as a
 * string, uses {@code remap = false}, and has {@code require = 0}. If AUI is absent, the class or
 * method is renamed, or the {@code positioned} descriptor changes, Mixin skips this injection
 * silently: the game starts, the floating item simply stays at 1.0, and no AUI type is needed to
 * load this class. That is the deliberate failure mode for an optional visual patch.
 */
@Pseudo
@Mixin(targets = "com.sighs.apricityui.screen.ApricityContainerScreen", remap = false)
public abstract class ApricityFloatingItemScaleMixin {

    /** Same ratio as .player-slot (48px slot, 16px base icon, vanilla 16/18 icon-to-slot ratio). */
    private static final double SOLDERING_FLOATING_ITEM_SCALE = 2.6667D;

    /** Compared by name so this mixin never loads SolderingScreen (which needs AUI) at startup. */
    private static final String SOLDERING_SCREEN_CLASS = "first.wildfires.soldering.SolderingScreen";

    @ModifyArg(
            method = "captureFloatingItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/sighs/apricityui/render/RenderNode$ItemNode;positioned(Ljava/util/function/Supplier;DDDIZLjava/lang/String;DZ)Lcom/sighs/apricityui/render/RenderNode$ItemNode;",
                    remap = false
            ),
            index = 3,
            remap = false,
            require = 0
    )
    private double wildfires$scaleFloatingItem(double originalScale) {
        // AUI 1.2.8 passes the hard-coded 1.0 here. If a later AUI already supplies a real scale,
        // leave it alone instead of overwriting it (and avoid corrupting a reordered x/y argument).
        if (originalScale != 1.0D) {
            return originalScale;
        }
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null && SOLDERING_SCREEN_CLASS.equals(screen.getClass().getName())) {
            return SOLDERING_FLOATING_ITEM_SCALE;
        }
        return originalScale;
    }
}
