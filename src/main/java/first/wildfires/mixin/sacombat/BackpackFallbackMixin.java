package first.wildfires.mixin.sacombat;

import first.wildfires.compat.sacombat.BackpackResolverFallback;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让背包面板认副手/主手/背包栏里的背包，不再只认 Curios 的 back 槽。
 *
 * <h2>物品为什么会消失（老大 2026-10-27 报「石子放进左边背包面板就没了」）</h2>
 * 实测数据（AutoJS 进游戏跑的，服务端）：
 * <pre>
 * 副手          = [1 leather_backpack]
 * resolve       = Found[stack=1 leather_backpack, source=OFF_HAND]   ← 能查到
 * hasEquippedBackpack = false                                        ← 但"已装备"不算
 * handler.hasBackpack = false
 * handler.slots = 42   active = 0                                    ← 一格都没启用
 * isItemValid(0, stone) = false
 * insertItem(0, stone) → 返回 [1 stone]（没插进去），NBT 一点没动
 * </pre>
 *
 * <p>原因链：
 * <ol>
 *   <li>{@code DynamicBackpackItemHandler.resolveBackpack()} 调的是
 *       {@code BackpackResolver.resolveEquipped(player)}，它<b>只查 Curios 的 {@code back} 槽</b>。
 *       背包拿在手上（主手/副手）或躺在背包栏里时，这里返回空。</li>
 *   <li>{@code getBackpackStack()} 因此返回 {@code ItemStack.EMPTY}；
 *       {@code getActiveSlotCount()} 见空气就 {@code return 0}；
 *       {@code isSlotEnabled(i)} 要求 {@code i < activeSlotCount} ⇒ 全 false；
 *       {@code isItemValid} 和 {@code insertItem} 全部拒绝。</li>
 *   <li>★ 但左侧面板 UI 不看这些，照旧画 42 个格子让玩家放。玩家一放，
 *       {@code insertItem} 把原物退回来，面板却已经把物品从光标上拿走了 ⇒
 *       <b>物品凭空消失</b>。</li>
 * </ol>
 *
 * <h2>修法</h2>
 * 在 {@code getBackpackStack()} 的 RETURN 上兜底：上游给的是空栈时，
 * 改用 {@link BackpackResolverFallback#resolve}（它查主手 / 副手 / 背包栏）。
 * 上游有值时一律不动，所以正常装备背包的行为完全不变。
 *
 * <p>★ 只改这一处就够：{@code getActiveSlotCount} / {@code isSlotEnabled} /
 * {@code isItemValid} / {@code insertItem} / {@code extractItem} 全都经由
 * {@code getBackpackStack()} 或 {@code createInventoryForSlot()} 取背包，
 * 上游给了值之后它们自然全部放行。
 */
@Pseudo
@Mixin(targets = "com.ogaba.sa_survival.item.backpack.DynamicBackpackItemHandler", remap = false)
public abstract class BackpackFallbackMixin {

    /**
     * 上游 {@code getBackpackStack()} 返回空时，改用手上/背包栏里的背包。
     *
     * <p>{@code require = 1}：这个方法名是 Survivors Arsenal 自己的（不是 MC 继承来的），
     * 名字不会随映射变化，目标不在就该在启动时报出来，而不是静默失效。
     */
    @Inject(method = "getBackpackStack()Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"), cancellable = true, remap = false, require = 1)
    private void wildfires$fallbackToHeldBackpack(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack upstream = cir.getReturnValue();
        if (upstream != null && !upstream.isEmpty()) {
            return;                     // Curios 槽里有背包：上游结果就是对的，不动
        }
        ItemStack fallback = BackpackResolverFallback.resolveWildfiresHeld(this);
        if (fallback != null && !fallback.isEmpty()) {
            cir.setReturnValue(fallback);
        }
    }

    /**
     * {@code hasBackpack()} 也要跟着一起兜底，否则会出现「自相矛盾」状态。
     *
     * <h2>为什么必须补这一处</h2>
     * 上游 {@code hasBackpack()} 走的是 {@code resolveBackpack().isPresent()}（只认 Curios），
     * 而 {@code getBackpackStack()} 已经被上面那条注入改成「也认手上」。
     * 如果只改一半，调用方会看到：
     * <pre>
     * hasBackpack()      = false      ← 面板渲染器和槽位注入器都靠它决定「加不加格子」
     * getBackpackStack() = leather_backpack
     * getActiveSlotCount() = 12
     * </pre>
     * {@code BackpackInventoryMenuExtender.ensureSlots} 与
     * {@code BackpackInventoryPanelOverlay} 都读 {@code hasBackpack}，
     * 于是「格子算出来有 12 个，但一个都不加」—— 面板空白，等于没修。
     *
     * <p>所以这里让 {@code hasBackpack()} 以 {@code getBackpackStack()} 为准，
     * 两个口子必须同进同退。实测（2026-10-27，AutoJS 进游戏，背包放副手）：
     * 补之前 {@code hasBackpack=false / active=12}，补之后两者一致。
     */
    @Inject(method = "hasBackpack()Z",
            at = @At("RETURN"), cancellable = true, remap = false, require = 1)
    private void wildfires$hasBackpackIncludesHeld(CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            return;                     // 上游已经说是 true，不动
        }
        ItemStack fallback = BackpackResolverFallback.resolveWildfiresHeld(this);
        if (fallback != null && !fallback.isEmpty()) {
            cir.setReturnValue(true);
        }
    }
}
