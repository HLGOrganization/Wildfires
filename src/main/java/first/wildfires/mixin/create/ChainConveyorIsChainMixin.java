package first.wildfires.mixin.create;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorConnectionHandler;
import first.wildfires.compat.create.ChainConveyorChains;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 放宽「手里拿的是不是锁链」的判定，让 TFC 金属链也能用来连传动轮。
 *
 * <p>Create 原本（反编译）：
 * <pre>
 * private static boolean isChain(ItemStack stack) {
 *     return stack.is(Items.CHAIN);
 * }
 * </pre>
 * 只有原版锁链能通过，拿 TFC 红钢链点方块时客户端<b>根本不会启动连接流程</b>，
 * 连选点都进不去。所以这一处必须改，否则后面两处（扣物品、掉落）改了也用不上。
 *
 * <p>只改 {@code isChain}，不碰它的其它逻辑 —— 选点、距离、提示全部照旧。
 */
@Mixin(value = ChainConveyorConnectionHandler.class, remap = false)
public abstract class ChainConveyorIsChainMixin
{
    @Inject(method = "isChain", at = @At("HEAD"), cancellable = true, remap = false)
    private static void wildfires$acceptTfcChains(ItemStack stack, CallbackInfoReturnable<Boolean> cir)
    {
        // 已经是原版锁链就交给原方法（等价于 true），只处理原版不认的那些
        if (!stack.is(net.minecraft.world.item.Items.CHAIN) && ChainConveyorChains.isChain(stack))
        {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}