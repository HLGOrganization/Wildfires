package first.wildfires.mixin.create;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import first.wildfires.compat.create.ChainConveyorChains;
import first.wildfires.compat.create.ChainConveyorWrenchHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 拆掉锁链时掉落<b>当初那种</b>锁链 —— 真正管用的那一处。
 *
 * <h2>★ 前面两版改错了地方，记下来别再踩</h2>
 * 本鲸先后改过：
 * <ol>
 *   <li>{@code ChainConveyorConnectionPacket#applySettings} 里的
 *       {@code new ItemStack(Items.CHAIN, ...)} —— <b>不是这条路</b>。
 *       实测加日志后确认：玩家拆锁链时 {@code applySettings} <b>压根没被调用</b>，
 *       日志里"连接"有 4 条、"断开" <b>0 条</b>。</li>
 *   <li>{@code ChainConveyorConnectionHandler#validateAndConnect} —— 也不对。
 *       反编译确认它偏移 343-359 只会发 {@code connect = true} 的包，
 *       <b>断开根本不走这个入口</b>。</li>
 * </ol>
 *
 * <h2>真正的掉落点（反编译 {@code ChainConveyorBlockEntity#chainDestroyed}）</h2>
 * <pre>
 * public void chainDestroyed(BlockPos target, boolean dropItems, boolean sendEffect) {
 *     int cost = getChainCost(target);
 *     if (sendEffect) { chainDestroyedEffectToSend = target; sendData(); }
 *     if (!dropItems) return;
 *     while (cost > 0) {
 *         Block.popResource(level, worldPosition,
 *                 new ItemStack(<b>Blocks.CHAIN.asItem()</b>, Math.min(cost, 64)));  // ← 就是这里
 *         cost -= 64;
 *     }
 * }
 * </pre>
 * 注意是 {@code Blocks.CHAIN.asItem()}，<b>不是</b> {@code Items.CHAIN}，
 * 所以按 {@code Items.CHAIN} 去找是找不到的 —— 这也是前两版判断失误的原因之一。
 *
 * <h2>本 mixin 怎么做</h2>
 * 拦 {@code new ItemStack(ItemLike, int)} 这次构造：
 * <ol>
 *   <li>查这条连接记录过的锁链（{@link ChainConveyorWrenchHelper#getRecordedChain}）</li>
 *   <li>有记录 ⇒ 用它；没记录 ⇒ 保持原版锁链</li>
 * </ol>
 * 数量仍然用 Create 算好的 {@code Math.min(cost, 64)}，不干预。
 */
@Mixin(value = ChainConveyorBlockEntity.class, remap = false)
public abstract class ChainConveyorChainDestroyedMixin
{
    @Redirect(
            method = "chainDestroyed",
            at = @At(value = "NEW",
                    target = "(Lnet/minecraft/world/level/ItemLike;I)Lnet/minecraft/world/item/ItemStack;",
                    ordinal = 0),
            remap = false,
            require = 1
    )
    private ItemStack wildfires$dropRecordedChain(ItemLike original, int count, BlockPos target)
    {
        final ItemStack recorded = ChainConveyorWrenchHelper.getRecordedChain(
                (ChainConveyorBlockEntity) (Object) this, target);

        if (recorded != null && !recorded.isEmpty() && ChainConveyorChains.isChain(recorded))
        {
            final ItemStack result = recorded.copy();
            result.setCount(count);
            return result;
        }

        return new ItemStack(original, count);
    }
}