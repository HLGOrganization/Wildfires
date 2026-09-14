package first.wildfires.mixin.minecraft;

import first.wildfires.WildfiresTags;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives TFC knives the same block speed as shears for balloon blocks. */
@Mixin(DiggerItem.class)
public abstract class DiggerItemMixin {
    @Inject(method = "getDestroySpeed", at = @At("HEAD"), cancellable = true)
    private void wildfires$getDestroySpeed(ItemStack stack, BlockState state, CallbackInfoReturnable<Float> cir) {
        if (stack.is(WildfiresTags.TFC_KNIVES) && state.is(WildfiresTags.MINEABLE_WITH_SHEARS)) {
            cir.setReturnValue(5.0F);
        }
    }
}
