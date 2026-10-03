package first.wildfires.mixin.sacombat;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Lets the backpack screen reuse the inventory overlay's own panel colour.
 *
 * <p>{@code BackpackInventoryPanelOverlay#getBackpackGuiTint} walks a chain comparing the stack against
 * every item Survivors Arsenal ships, returning {@code -1} for anything else. Rather than duplicate
 * that table - and have the two drift apart - the screen calls through to it, so a backpack is coloured
 * identically whether it is opened by right-clicking or by the panel key.
 *
 * <p>{@link BackpackGuiTintMixin} already hooks this method for the Wildfires leather packs, so an
 * invoked call runs that hook and then the original chain.
 */
@Pseudo
@Mixin(targets = "com.ogaba.sa_survival.client.BackpackInventoryPanelOverlay", remap = false)
public interface BackpackGuiTintInvoker {

    @Invoker(value = "getBackpackGuiTint", remap = false)
    static int wildfires$getBackpackGuiTint(ItemStack stack) {
        throw new AssertionError("Mixin invoker was not applied");
    }
}
