package first.wildfires.mixin.sacombat;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Lets the backpack screen reuse the inventory overlay's own title colours.
 *
 * <p>When a backpack is opened with the panel key, the overlay draws its heading through
 * {@code drawPanelLabel}, which takes a main colour and a shadow colour. Both come from the two methods
 * below. Calling through to them - rather than copying the constants - is what makes the name in the
 * right-click screen the same colour as the one in the overlay, including for any item the overlay
 * treats differently.
 *
 * <p>Both are private statics, which an invoker reaches without the class needing to be widened.
 */
@Pseudo
@Mixin(targets = "com.ogaba.sa_survival.client.BackpackInventoryPanelOverlay", remap = false)
public interface BackpackTitleColorInvoker {

    @Invoker(value = "getBackpackTitleColor", remap = false)
    static int wildfires$getTitleColor(ItemStack stack) {
        throw new AssertionError("Mixin invoker was not applied");
    }

    /**
     * The colour the overlay lays behind its heading, drawn at a one-pixel offset rather than as the
     * font's own drop shadow.
     */
    @Invoker(value = "getBackpackTextShadowColor", remap = false)
    static int wildfires$getTextShadowColor(ItemStack stack) {
        throw new AssertionError("Mixin invoker was not applied");
    }
}
