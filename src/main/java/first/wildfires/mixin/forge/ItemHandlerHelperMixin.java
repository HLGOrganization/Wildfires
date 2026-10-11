package first.wildfires.mixin.forge;

import first.wildfires.utils.FoodStacking;
import first.wildfires.utils.HeatStacking;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <h2>Forge 这一半的"能不能叠"</h2>
 * 物品栏、物品实体、合成栏走的是原版 {@code ItemStack.isSameItemSameTags}（见
 * {@code first.wildfires.mixin.minecraft.ItemStackMixin}），而所有的机器、管道、物品处理器走的是
 * {@link ItemHandlerHelper#canItemStacksStack}。两者都要放宽，否则热锭在箱子里叠不起来、在机器里却
 * 叠得起来，或者反过来。
 * <p>
 * 这里比原版多一层：Forge 的判定除了物品和 NBT 还额外要求 {@code areCapsCompatible}，
 * 群峦设备的合并入口 {@code FoodCapability.areStacksStackableExceptCreationDate} 正是通过它来判定的。
 */
@Mixin(value = ItemHandlerHelper.class, remap = false)
public class ItemHandlerHelperMixin {

    @Inject(method = "canItemStacksStack", at = @At("RETURN"), cancellable = true)
    private static void wildfires$allowCloseTemperaturesToStack(ItemStack a, ItemStack b, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && (HeatStacking.tolerateSameStack(a, b) || FoodStacking.tolerateSameFood(a, b))) {
            cir.setReturnValue(true);
        }
    }
}
