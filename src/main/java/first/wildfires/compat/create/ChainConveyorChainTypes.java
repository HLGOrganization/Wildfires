package first.wildfires.compat.create;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * 锁链传动轮「每条连接用的是哪种锁链」的存取工具。
 *
 * <p>★ 为什么不把逻辑写在 mixin 里（2026-10-23 踩过的坑）：
 * mixin 标了 {@code remap = false} 之后，里面<b>直接调用 Minecraft 的方法</b>不会做名称映射，
 * 于是运行期报：
 * <pre>
 * MixinTransformerError: Error resolving INVOKEVIRTUAL for
 *   ChainConveyorBlockEntityChainTypeMixin.wildfires$write...
 * </pre>
 * 那个 {@code ItemStack.save(CompoundTag)} / {@code NbtUtils.writeBlockPos} 在运行期是混淆名，
 * 编译期名字对不上。把逻辑搬到普通类里，普通类走正常的 remap 流程，就没有这个问题。
 * mixin 只留一个 {@code @Unique} 字段和一层薄转发。
 */
public final class ChainConveyorChainTypes
{
    private ChainConveyorChainTypes()
    {
    }

    public static final String TAG_KEY = "WildfiresChainTypes";

    /** 新建一个空的记录表 */
    public static Map<BlockPos, ItemStack> create()
    {
        return new HashMap<>();
    }

    /** 记下一条连接用的锁链 */
    public static void put(Map<BlockPos, ItemStack> map, BlockPos target, @Nullable ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            map.remove(target);
        }
        else
        {
            map.put(target.immutable(), stack.copyWithCount(1));
        }
    }

    /**
     * 取一条连接用的锁链，<b>没记录就返回 {@link ItemStack#EMPTY}</b>。
     *
     * <h2>★ 2026-10-24：这里曾经返回原版锁链，把坐标修正整个废掉了</h2>
     * 原来写的是「没记录就退回原版锁链」，看起来贴心，实际埋了个大坑：
     * <pre>
     * // ChainConveyorWrenchHelper#getRecordedChain
     * ItemStack recorded = self.wildfires$getChainType(target);
     * if (recorded != null &amp;&amp; !recorded.isEmpty()) {
     *     return recorded;        // ← 永远命中！因为"没记录"被填成了原版锁链
     * }
     * recorded = self.wildfires$getChainType(be.getBlockPos().offset(target));  // ← 永远走不到
     * </pre>
     * 「相对坐标 ⇒ 绝对坐标」的第二次查询<b>一次都没执行过</b>。
     *
     * <p>所以这里改成返回 {@code EMPTY}：<b>查不到就是查不到</b>，
     * 默认值（原版锁链）交给真正需要它的地方（如 {@code ChainConveyorChains#chainToReturn}）去填。
     */
    public static ItemStack get(Map<BlockPos, ItemStack> map, BlockPos target)
    {
        final ItemStack recorded = map.get(target);
        return recorded == null || recorded.isEmpty() ? ItemStack.EMPTY : recorded.copy();
    }

    /**
     * 从 NBT 读出记录。
     *
     * <h2>★ 2026-10-24：这里原本无条件 {@code map.clear()}，把刚写进去的记录冲掉了</h2>
     * 原写法：
     * <pre>
     * map.clear();
     * if (!tag.contains(TAG_KEY, ...)) return;   // 没有 tag 就直接返回 —— 但已经清空了
     * </pre>
     * {@code read} 在方块实体反序列化、客户端同步、区块重载时都会调用。
     * 只要某次 tag 里没有我们这份记录（比如刚连完还没 save、或者客户端包不含自定义字段），
     * 刚写进去的映射就被<b>清空</b>了。实测表现：
     * <pre>
     * [-1324,2057] = 1 metal/chain/red_steel   ← 时序侥幸，活着
     * [-1336,2043] = 0 air                      ← read() 刚好把它清了
     * </pre>
     *
     * <p>修法：<b>tag 里没有我们的键就什么都不做</b>，保留内存里已有的记录。
     * 只有真的读到数据时才用读到的内容替换。
     */
    public static void read(Map<BlockPos, ItemStack> map, CompoundTag tag)
    {
        if (!tag.contains(TAG_KEY, Tag.TAG_LIST))
        {
            // 没有这份记录：不动内存里的映射（可能是刚写入还没来得及存的）
            return;
        }

        map.clear();
        final ListTag list = tag.getList(TAG_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            final CompoundTag entry = list.getCompound(i);
            if (!entry.contains("Pos", Tag.TAG_COMPOUND) || !entry.contains("Item", Tag.TAG_COMPOUND))
            {
                continue;
            }
            final BlockPos pos = NbtUtils.readBlockPos(entry.getCompound("Pos"));
            final ItemStack stack = ItemStack.of(entry.getCompound("Item"));
            if (stack != null && !stack.isEmpty())
            {
                map.put(pos, stack);
            }
        }
    }

    /** 写进 NBT */
    public static void write(Map<BlockPos, ItemStack> map, CompoundTag tag)
    {
        if (map.isEmpty())
        {
            return;
        }
        final ListTag list = new ListTag();
        for (final Map.Entry<BlockPos, ItemStack> entry : map.entrySet())
        {
            final CompoundTag out = new CompoundTag();
            out.put("Pos", NbtUtils.writeBlockPos(entry.getKey()));
            out.put("Item", entry.getValue().save(new CompoundTag()));
            list.add(out);
        }
        tag.put(TAG_KEY, list);
    }
}