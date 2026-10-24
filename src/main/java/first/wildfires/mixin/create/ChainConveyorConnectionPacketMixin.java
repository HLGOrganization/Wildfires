package first.wildfires.mixin.create;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorConnectionPacket;
import first.wildfires.compat.create.ChainConveyorWrenchHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 记录「这条连接当初用的是哪种锁链」，供断开时返还。
 *
 * <h2>职责（2026-10-23 简化过）</h2>
 * 这个 mixin <b>只负责记账</b>：
 * <ul>
 *   <li>连上时：{@code rememberConnection} 把玩家手里那种锁链记进 BE</li>
 *   <li>断开时：{@code forgetConnection} 清掉记录</li>
 * </ul>
 * <b>返还什么锁链已经交给 {@link ChainConveyorReturnChainMixin} 处理</b>
 * （它直接拦 {@code new ItemStack(...)}，比在这里事后替换干净得多）。
 *
 * <h2>为什么不再在这里替换</h2>
 * 旧版在这里用「前后统计原版锁链数量差」来推算拿了多少，然后 swap 成记录的类型。
 * 问题：{@code countInInventory(player, Items.CHAIN)} 只认原版锁链，
 * 玩家用的是 TFC 红钢链时前后都是 0，gained = 0，<b>替换逻辑整个空转</b>。
 * 现在改成在构造点直接给对，统计这一步就没必要了。
 */
@Mixin(value = ChainConveyorConnectionPacket.class, remap = false)
public abstract class ChainConveyorConnectionPacketMixin
{
    @Shadow(remap = false)
    private ItemStack chain;

    @Shadow(remap = false)
    private BlockPos targetPos;

    @Shadow(remap = false)
    private boolean connect;

    @Inject(method = "applySettings(Lnet/minecraft/server/level/ServerPlayer;"
            + "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;)V",
            at = @At("TAIL"), remap = false)
    private void wildfires$rememberChainType(ServerPlayer player, ChainConveyorBlockEntity be, CallbackInfo ci)
    {
        if (be == null || targetPos == null)
        {
            return;
        }

        if (connect)
        {
            // ★ 2026-10-24 临时诊断
            com.mojang.logging.LogUtils.getLogger().info(
                    "[Wildfires/chain] 连接: chain={} targetPos={}",
                    chain == null ? "null" : chain.toString(), targetPos);
            ChainConveyorWrenchHelper.rememberConnection(be, targetPos, chain);
        }
        else
        {
            ChainConveyorWrenchHelper.forgetConnection(be, targetPos);
        }
    }
}