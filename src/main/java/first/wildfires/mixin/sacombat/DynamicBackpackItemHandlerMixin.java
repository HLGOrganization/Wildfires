package first.wildfires.mixin.sacombat;

import first.wildfires.compat.sacombat.SatchelSizeRules;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the TFC item-size limit to the Survivors Arsenal dynamic backpack handler.
 *
 * <p>This handler backs the inventory baggage panel, the quick-swap packet and every automation
 * path. Only the two methods declared by Survivors Arsenal are injected; the inherited
 * {@code ItemStackHandler} behaviour is never touched, so no other mod is affected.
 *
 * <p>{@code insertItem} needs its own injection because this handler does not consult its
 * {@code isItemValid} before inserting, unlike the clothing handler.
 */
@Pseudo
@Mixin(targets = "com.ogaba.sa_survival.item.backpack.DynamicBackpackItemHandler", remap = false)
public abstract class DynamicBackpackItemHandlerMixin {

    @Inject(method = "isItemValid(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void wildfires$tfcBackpackSizeFilterValid(int slot, ItemStack stack,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (SatchelSizeRules.blocks(SatchelSizeRules.containerOf(this), stack)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "insertItem(ILnet/minecraft/world/item/ItemStack;Z)"
                    + "Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void wildfires$tfcBackpackSizeFilterInsert(int slot, ItemStack stack, boolean simulate,
                                                       CallbackInfoReturnable<ItemStack> cir) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (SatchelSizeRules.blocks(SatchelSizeRules.containerOf(this), stack)) {
            // The upstream contract rejects a stack by returning it unchanged.
            cir.setReturnValue(stack);
        }
    }
}