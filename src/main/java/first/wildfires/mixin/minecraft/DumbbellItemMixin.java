package first.wildfires.mixin.minecraft;

import first.wildfires.dumbbell.Dumbbells;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Turns a right click with a dumbbell into a bow style charge. The mod registers the two dumbbells itself, but the
 * behaviour is driven purely by the {@code wildfires:dumbbells} tag, so any tagged item charges the same way.
 */
@Mixin(Item.class)
public abstract class DumbbellItemMixin {

    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void wildfires$getUseDuration(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (Dumbbells.isDumbbell(stack)) {
            cir.setReturnValue(Dumbbells.USE_DURATION);
        }
    }

    @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
    private void wildfires$getUseAnimation(ItemStack stack, CallbackInfoReturnable<UseAnim> cir) {
        if (Dumbbells.isDumbbell(stack)) {
            cir.setReturnValue(UseAnim.SPEAR);
        }
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void wildfires$use(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack stack = player.getItemInHand(hand);
        if (!Dumbbells.isDumbbell(stack)) {
            return;
        }
        // Both sides have to start using the item, otherwise the release hook never fires on the server.
        player.startUsingItem(hand);
        cir.setReturnValue(InteractionResultHolder.consume(stack));
    }

}
