package first.wildfires.compat.create;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 「什么样的物品算锁链传动轮能用的锁链」—— 把 Create 只认原版锁链的判定放宽到 TFC 金属链。
 *
 * <h2>Create 原本的判定（反编译确认）</h2>
 * <pre>
 * // ChainConveyorConnectionHandler.isChain(ItemStack)
 * return stack.is(Items.CHAIN);
 *
 * // ChainConveyorBlockEntity.getChainsFromInventory(Player, ItemStack template, int needed, boolean simulate)
 * if (!stack.is(template.getItem())) continue;      // 只扣和模板同一个 Item
 *
 * // ChainConveyorConnectionPacket.applySettings —— 断开时
 * player.getInventory().add(new ItemStack(Items.CHAIN, Math.min(cost, 64)));
 * </pre>
 * 三处都<b>硬编码 `Items.CHAIN`</b>，所以：
 * <ul>
 *   <li>手持 TFC 红钢链时客户端根本不启动连接流程</li>
 *   <li>就算连上了，也扣不到物品、掉落时只会给原版锁链</li>
 *   <li>贴图按原版锁链画</li>
 * </ul>
 *
 * <h2>TFC 的锁链有哪些</h2>
 * <pre>
 * tfc:metal/chain/{bismuth_bronze, black_bronze, black_steel, blue_steel,
 *                  bronze, copper, red_steel, steel, wrought_iron}
 * </pre>
 * 共 9 种（反编译 + assets/models 确认）。
 *
 * <h2>这个类的定位</h2>
 * 只做「判定」和「换算」，不碰方块状态。所有 mixin 都调这里，
 * 保证三处逻辑一致（改一处等于三处一起改）。
 */
public final class ChainConveyorChains
{
    private ChainConveyorChains()
    {
    }

    /** TFC 锁链的命名空间 */
    private static final String TFC_NAMESPACE = "tfc";

    /** TFC 锁链的物品路径前缀 */
    private static final String TFC_CHAIN_PREFIX = "metal/chain/";

    /** 通用锁链 tag（若别的模组加了更通用的 tag，这里也能兜住） */
    private static final TagKey<Item> C_CHAINS = ItemTags.create(
            new ResourceLocation("c", "chains"));

    /**
     * 这个物品是不是锁链传动轮能用的「锁链」。
     *
     * <p>认三种：原版锁链、TFC 金属链、以及 {@code c:chains} 标签里的任何东西。
     */
    public static boolean isChain(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return false;
        }
        if (stack.is(Items.CHAIN))
        {
            return true;
        }
        if (stack.is(C_CHAINS))
        {
            return true;
        }
        return isTfcChain(stack.getItem());
    }

    /**
     * 这个物品是不是 TFC 的金属锁链（{@code tfc:metal/chain/<metal>}）。
     */
    public static boolean isTfcChain(Item item)
    {
        if (item == null)
        {
            return false;
        }
        final ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null || !TFC_NAMESPACE.equals(id.getNamespace()))
        {
            return false;
        }
        // 前缀匹配再加一层"后面还有非空材料名"的判断，避免 tfc:metal/chain/ 这种畸形 id
        final String path = id.getPath();
        return path.startsWith(TFC_CHAIN_PREFIX) && path.length() > TFC_CHAIN_PREFIX.length();
    }

    /**
     * 断开连接时应该返还什么锁链。
     *
     * <p>优先用记下来的那种（玩家当初连的时候用的），
     * 没有记录（老存档 / 记录丢失）就退回原版锁链 —— 和 Create 原行为一致。
     */
    public static ItemStack chainToReturn(ItemStack recorded)
    {
        if (recorded != null && !recorded.isEmpty() && isChain(recorded))
        {
            return recorded.copy();
        }
        return new ItemStack(Items.CHAIN);
    }
}