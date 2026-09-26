package first.wildfires.mixin.sacombat;

import first.wildfires.compat.sacombat.SatchelSizeRules;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the TFC item-size limit to the storage panel of Survivors Arsenal clothing.
 *
 * <p>Clothing such as {@code sa_combat:blue_jeans} and the jackets gains its slots from the
 * {@code [external_equipment_storage]} config list, and every one of those slots is served by this
 * handler. Only {@code isItemValid} is injected: the handler's own {@code insertItem} consults it
 * first, and {@code InventoryClothingSlot#mayPlace} delegates to it as well, so a single injection
 * point covers the GUI, shift-click and every automation path.
 *
 * <p>One handler merges the slots of every equipped provider of one storage area, so a panel can
 * mix the slots of a limited jacket with the unlimited slots of a vest. The limit is therefore
 * resolved per slot through {@link SatchelSizeRules#ownerAt}, which reads the {@code sourceStacks}
 * array the handler already builds during its layout pass. Reflection is used instead of
 * {@code @Shadow} because the field is {@code private final} in a class that is not a compile
 * dependency, and a missing field simply fails open.
 */
@Pseudo
@Mixin(targets = "com.ogaba.sa_survival.item.clothing.DynamicClothingItemHandler", remap = false)
public abstract class DynamicClothingItemHandlerMixin {

    @Inject(method = "isItemValid(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void wildfires$tfcClothingSizeFilter(int slot, ItemStack stack,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ItemStack owner = SatchelSizeRules.ownerAt(this, slot);
        if (SatchelSizeRules.blocks(owner, stack)) {
            cir.setReturnValue(false);
        }
    }
}