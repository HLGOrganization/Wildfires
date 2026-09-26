package first.wildfires.mixin.sacombat.client;

import first.wildfires.compat.sacombat.PlainBackpackFamilies;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the Survivors Arsenal renderer treat the plain Wildfires backpacks as a known family.
 *
 * <p>{@code BackpackCurioRenderer} answers "which family is this stack?" by comparing it against
 * the exact items it ships, one hard-coded {@code ItemStack#is} chain per family. A new item is
 * therefore drawn with the fallback path and does not appear on a player's back.
 *
 * <p>Every family check is answered here for the plain variants. The checks are read-only, so the
 * inventory, the menu and the Curios slot are untouched; only the worn appearance changes. Each
 * injection cancels with its own answer, which leaves the original chain unused for plain packs.
 *
 * <p>The class is client-only and optional, so {@code require = 0} keeps a Survivors Arsenal update
 * from turning a missing method into a crash.
 */
@Pseudo
@Mixin(targets = "com.ogaba.sa_survival.client.renderer.BackpackCurioRenderer", remap = false)
public abstract class BackpackCurioRendererMixin {

    @Inject(method = "isSatchel(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void wildfires$plainSatchel(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        wildfires$answer(stack, "satchel_", cir);
    }

    @Inject(method = "isLeatherBackpack(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void wildfires$plainLeatherBackpack(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        wildfires$answer(stack, "leather_backpack", cir);
    }

    @Inject(method = "isSmallBackpack(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void wildfires$plainSmallBackpack(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        wildfires$answer(stack, "small_backpack_", cir);
    }

    @Inject(method = "isDuffelBag(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void wildfires$plainDuffelBag(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        wildfires$answer(stack, "duffel_bag_", cir);
    }

    @Inject(method = "isHikingBackpack(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void wildfires$plainHikingBackpack(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        wildfires$answer(stack, "hiking_backpack_", cir);
    }

    @Inject(method = "isMilitaryBackpack(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void wildfires$plainMilitaryBackpack(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        wildfires$answer(stack, "military_backpack_", cir);
    }

    /** Confirms a plain pack of this family, and stays silent for every other stack. */
    private static void wildfires$answer(ItemStack stack, String familyPrefix, CallbackInfoReturnable<Boolean> cir) {
        if (PlainBackpackFamilies.belongsToFamilyPrefix(stack, familyPrefix)) {
            cir.setReturnValue(true);
        }
    }
}