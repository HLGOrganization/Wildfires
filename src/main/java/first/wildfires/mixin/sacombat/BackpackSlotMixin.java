package first.wildfires.mixin.sacombat;

import first.wildfires.compat.sacombat.SatchelSizeRules;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the TFC item-size limit to the Survivors Arsenal backpack menu slots.
 *
 * <p>{@code BackpackSlot#mayPlace} only rejects nested backpacks, so the size check is injected at
 * its head. Shift-click is covered by the same point, because
 * {@code AbstractContainerMenu#moveItemStackTo} consults {@code Slot#mayPlace} before every
 * transfer. A slot exposes no backpack field, only the {@code IItemHandler} it was built with, so
 * the owning backpack is resolved through {@link SatchelSizeRules#containerOf}.
 *
 * <h2>Why there are two injectors</h2>
 *
 * <p>{@code mayPlace} is declared by Minecraft's {@code Slot} and merely overridden by Survivors
 * Arsenal. That makes the physical name depend on the environment: development uses the Mojang
 * name {@code mayPlace}, while the published jar uses the SRG name {@code m_5857_}. A refmap entry
 * cannot bridge the two here, because Survivors Arsenal is not a compile dependency, so the
 * annotation processor has no target class to resolve and cannot emit the mapping.
 *
 * <p>Both names are therefore injected, each with {@code require = 0}. Exactly one of them matches
 * in any given environment and the other is skipped with a warning, which keeps the filter working
 * in the development client and in the shipped mod without making a version change fatal.
 */
@Pseudo
@Mixin(targets = "com.ogaba.sa_survival.menu.BackpackSlot", remap = false)
public abstract class BackpackSlotMixin {

    /** Development name. Matched against the Mojang-mapped client. */
    @Inject(method = "mayPlace(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"),
            cancellable = true, remap = false, require = 0)
    private void wildfires$tfcBackpackSizeFilterDev(ItemStack stack,
                                                    CallbackInfoReturnable<Boolean> cir) {
        wildfires$applyBackpackSizeFilter(stack, cir);
    }

    /** Production name. Matched against the SRG-mapped runtime. */
    @Inject(method = "m_5857_(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"),
            cancellable = true, remap = false, require = 0)
    private void wildfires$tfcBackpackSizeFilterProduction(ItemStack stack,
                                                           CallbackInfoReturnable<Boolean> cir) {
        wildfires$applyBackpackSizeFilter(stack, cir);
    }

    private void wildfires$applyBackpackSizeFilter(ItemStack stack,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (SatchelSizeRules.blocks(SatchelSizeRules.containerOf(this), stack)) {
            cir.setReturnValue(false);
        }
    }
}