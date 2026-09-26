package first.wildfires.mixin.sacombat.client;

import first.wildfires.compat.sacombat.LeatherBackpackGuiTint;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives the Wildfires leather backpacks the same panel colour as the mod's own leather backpack.
 *
 * <p>{@code BackpackInventoryPanelOverlay#getBackpackGuiTint} walks a long {@code ItemStack#is}
 * chain over the items Survivors Arsenal ships and returns {@code -1} for anything else, where
 * {@code -1} means "leave the panel art untinted". The Wildfires packs are not in that chain, so
 * without this they would draw the plain grey panel while every stock backpack draws a coloured
 * one.
 *
 * <p>Injecting at the head and cancelling keeps the upstream chain untouched for its own items -
 * this only ever supplies a value for the five Wildfires packs and defers in every other case.
 *
 * <p>The class is client-only and optional, so {@code require = 0} keeps a Survivors Arsenal update
 * from turning a missing method into a crash.
 */
@Pseudo
@Mixin(targets = "com.ogaba.sa_survival.client.BackpackInventoryPanelOverlay", remap = false)
public abstract class BackpackGuiTintMixin {

    @Inject(method = "getBackpackGuiTint(Lnet/minecraft/world/item/ItemStack;)I",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void wildfires$leatherBackpackTint(ItemStack stack,
                                                      CallbackInfoReturnable<Integer> cir) {
        int tint = LeatherBackpackGuiTint.tintFor(stack);
        if (tint != -1) {
            cir.setReturnValue(tint);
        }
    }
}
