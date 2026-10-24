package first.wildfires.compat.create;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 拆链时把 Create 硬编码返还的原版锁链换成当初那种。
 *
 * <p>数量怎么确定：<b>不自己算</b> {@code getChainCost}（它按方块间距离算，
 * 而 Create 的返还按玩家与目标的偏移算，容易对不上）。
 * 改成对比 {@code applySettings} 前后玩家背包里 {@code minecraft:chain} 的存量差，
 * 差多少就是 Create 这次返还了多少。
 */
public final class ChainConveyorWrenchHelper
{
    private ChainConveyorWrenchHelper()
    {
    }

    /** 数玩家背包里指定物品的总数 */
    public static int countInInventory(ServerPlayer player, Item item)
    {
        int total = 0;
        final var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++)
        {
            final ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(item))
            {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** 从玩家背包按物品精确扣除，返回实际扣掉的个数 */
    public static int removeFromInventory(ServerPlayer player, Item item, int amount)
    {
        int remaining = amount;
        final var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++)
        {
            final ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !stack.is(item))
            {
                continue;
            }
            final int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        return amount - remaining;
    }

    /**
     * 把 Create 刚返还的原版锁链换成 {@code wanted}。
     *
     * @param gained Create 这次实际返还的原版锁链数量
     */
    public static void swapReturnedChains(ServerPlayer player, int gained, ItemStack wanted)
    {
        if (player == null || player.isCreative() || gained <= 0)
        {
            return;
        }
        if (wanted == null || wanted.isEmpty() || wanted.is(Items.CHAIN))
        {
            return;
        }
        removeFromInventory(player, Items.CHAIN, gained);
        final ItemStack replacement = wanted.copyWithCount(gained);
        if (!player.getInventory().add(replacement))
        {
            player.drop(replacement, false);
        }
    }

    /** 记下连接：两端都记 */
    public static void rememberConnection(ChainConveyorBlockEntity be, BlockPos target, ItemStack chain)
    {
        if (be == null || target == null || chain == null || chain.isEmpty())
        {
            return;
        }
        ChainConveyorReflect.setChainType(be, target, chain);
        be.setChanged();
        be.sendData();

        if (be.getLevel() != null
                && be.getLevel().getBlockEntity(target) instanceof ChainConveyorBlockEntity other)
        {
            ChainConveyorReflect.setChainType(other, be.getBlockPos(), chain);
            other.setChanged();
            other.sendData();
        }
    }

    /** 清掉连接记录：两端都清 */
    public static void forgetConnection(ChainConveyorBlockEntity be, BlockPos target)
    {
        if (be == null || target == null)
        {
            return;
        }
        ChainConveyorReflect.clearChainType(be, target);

        if (be.getLevel() != null
                && be.getLevel().getBlockEntity(target) instanceof ChainConveyorBlockEntity other)
        {
            ChainConveyorReflect.clearChainType(other, be.getBlockPos());
        }
    }

    /**
     * 取连接记录的锁链。
     *
     * <h2>★ 2026-10-24：查不到记录的真正原因</h2>
     * 老大实测拆链掉的还是原版锁链，本鲸加了日志才看出来：
     * <pre>
     * 记录写入时 targetPos = BlockPos{x=-1322, y=71, z=2051}   ← 绝对坐标
     * Create 的 connections 集合 = {BlockPos{x=-2, y=0, z=6}}   ← 相对坐标！
     * </pre>
     * {@code ChainConveyorBlockEntity#chainDestroyed(BlockPos target, ...)} 收到的
     * {@code target} 是 {@code connections} 里的值，也就是**相对坐标**
     * （Create 用 {@code RelativeBlockPos} 存连接，相对于传动轮自身）。
     * 拿它去查用绝对坐标写进去的表，永远查不到 ⇒ 一直退回原版锁链。
     *
     * <p>所以这里先做个「相对 ⇒ 绝对」的归一化：
     * <ul>
     *   <li>先在表里按原样查（兼容调用方本来就传绝对坐标的情况，比如
     *       {@code ChainConveyorConnectionPacketMixin} 里用的是数据包的绝对 targetPos）</li>
     *   <li>查不到再用 {@code be.getBlockPos().offset(target)} 查一次</li>
     * </ul>
     */
    public static ItemStack getRecordedChain(ChainConveyorBlockEntity be, BlockPos target)
    {
        if (be == null || target == null)
        {
            return ItemStack.EMPTY;
        }

        // 1) 按原样查（绝对坐标路径）
        ItemStack recorded = ChainConveyorReflect.getChainType(be, target);
        if (recorded != null && !recorded.isEmpty())
        {
            return recorded;
        }

        // 2) 当成相对坐标，换算成绝对坐标再查（chainDestroyed 走这条）
        recorded = ChainConveyorReflect.getChainType(be, be.getBlockPos().offset(target));
        if (recorded != null && !recorded.isEmpty()) return recorded;

        recorded = ChainConveyorReflect.getNativeChainType(be, target.subtract(be.getBlockPos()));
        if (!recorded.isEmpty()) return recorded;
        return ChainConveyorReflect.getNativeChainType(be, target);
    }
}
