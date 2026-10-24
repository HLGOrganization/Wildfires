package first.wildfires.mixin.create;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import first.wildfires.compat.create.ChainConveyorChains;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让「扣锁链」也认 TFC 金属链。
 *
 * <p>Create 原本（反编译 {@code getChainsFromInventory} 偏移 120-129）：
 * <pre>
 * if (!stack.is(template.getItem())) continue;   // 只认同一个 Item 的堆叠
 * </pre>
 * {@code template} 是玩家手里那种锁链。拿 TFC 链时，背包里的 TFC 链
 * 因为<b>不是原版锁链</b>（Create 的模板判定已放宽，但这里比较的是具体 Item）
 * 实际上问题在于：模板是 TFC 链时，{}<code>template.getItem()</code> 就是 TFC 链物品，
 * 理论上能匹配 —— 但 <b>Create 在这一步之前已经把非原版锁链过滤掉了</b>
 * （见 {@link ChainConveyorIsChainMixin}），所以走到这里时模板几乎总是原版锁链。
 *
 * <p>本 mixin 做两件事：
 * <ol>
 *   <li>模板是锁链时，允许匹配「同一种锁链」的堆叠（红钢链只吃红钢链，不串味）</li>
 *   <li>额外接受 {@code c:chains} 标签里的等价物</li>
 * </ol>
 *
 * <p>★ {@code @Redirect} 的 handler 签名必须和<b>被拦截的那个方法调用</b>一致：
 * 目标是 {@code ItemStack#is(Item)}，所以前两个参数是 {@code (ItemStack receiver, Item arg)}，
 * 后面的额外参数才是外层方法的局部变量（Mixin 会按类型从局部变量表补）。
 */
@Mixin(value = ChainConveyorBlockEntity.class, remap = false)
public abstract class ChainConveyorGetChainsMixin
{
    @Redirect(
            method = "getChainsFromInventory",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"),
            remap = false,
            require = 0
    )
    private static boolean wildfires$matchAnyChain(ItemStack stack, Item item,
                                                   Player player, ItemStack template,
                                                   int needed, boolean simulate)
    {
        // 原本的判定优先
        if (stack.is(item))
        {
            return true;
        }
        // 新增：模板和当前堆叠都是锁链，且是同一个物品（防止红钢链被钢链顶掉）
        return ChainConveyorChains.isChain(template)
                && ChainConveyorChains.isChain(stack)
                && stack.getItem() == template.getItem();
    }
}