package first.wildfires.mixin.vintageimprovements;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把 Vintage Improvements 的 helve hammer（锁链锤）输入区改成<b>只有 1 格、每格 1 个</b>，
 * 并<b>禁用槽位盖板</b>。
 *
 * <h2>模组原本的槽位（反编译确认）</h2>
 * <pre>
 *   inputInv   SmartInventory(3)   ← 输入
 *   bufInv     SmartInventory(3)   ← 序列组装缓冲
 *   outputInv  SmartInventory(3)   ← 输出
 * </pre>
 *
 * <h2>老大 2026-10-23 的要求（演进过程）</h2>
 * <ol>
 *   <li>最初：3 格，各堆 64 个（模组原版）</li>
 *   <li>第一版改动：3 格，<b>每格只放 1 个</b></li>
 *   <li><b>当前</b>：<b>只留 1 格</b>，并禁用盖板</li>
 * </ol>
 * 为什么要禁用盖板：模组的盖板（{@code addBlockedSlots}）每次锁掉一个输入槽，
 * 1 格再被锁一下就变成 0 格，机器彻底不能用，只能拆了重放。
 *
 * <h2>怎么把 3 格改成 1 格</h2>
 * 模组没开配置项，所以用<b>反射重建</b>：构造完成后把 {@code inputInv} 整个换成
 * 新造的 {@code SmartInventory(1, this)}。
 *
 * <p><b>为什么不改构造函数的 {@code iconst_3}</b>：
 * 那处字节码同时被 {@code bufInv} 和 {@code outputInv} 复用（三处都是 {@code iconst_3}），
 * 按偏移打会误伤；按序号打又依赖编译顺序，模组一更新就断。
 * 反射重建对版本变化更耐受。
 *
 * <h2>为什么不需要动 capability（自动化那路）</h2>
 * 模组构造里是 {@code LazyOptional.of(() -> new HelveInventoryHandler(inputInv, outputInv))}。
 * Forge 的 {@code LazyOptional} <b>首次 resolve 时才执行 supplier</b>，之后才缓存。
 * 本 mixin 在 {@code <init>} 的 TAIL 执行，此时方块实体刚构造完、还没被任何管道访问过，
 * LazyOptional 尚未 resolve ⇒ 替换字段后首次 resolve 读到的就是新的 1 格 inventory。
 * <b>所以不要画蛇添足去重置 capability</b>（本鲸试过，反而要用一堆反射拼内部类，容易出错）。
 *
 * <h2>为什么用 withMaxStackSize(1) 而不是改 getSlotLimit</h2>
 * {@code SmartInventory#getSlotLimit(slot)} 是所有 Create 物品栏共用的
 * （箱子、漏斗、各类机器），改它会误伤一大片。
 * {@code withMaxStackSize(n)} 是实例级的，反编译确认它同时设置
 * {@code this.stackSize} 和内部 {@code wrapped.stackSize}，
 * 之后 {@code getSlotLimit} 返回 {@code Math.min(原上限, stackSize)}，
 * 所以界面、管道、漏斗、机械臂全部受约束，且只影响这一台设备。
 *
 * <p>★ 通过反射拿字段，不 import 该模组的类 —— Vintage Improvements 不是本模组的编译依赖，
 * 必须 {@code @Pseudo} + 字符串 targets，且 handler 签名里不能出现它的类型。
 */
@Pseudo
@Mixin(targets = "com.negodya1.vintageimprovements.content.kinetics.helve_hammer.HelveBlockEntity", remap = false)
public abstract class HelveBlockEntityMixin
{
    /** 输入区保留的格数 */
    private static final int WILDFIRES_INPUT_SLOTS = 1;

    /** 构造完之后：重建 1 格输入栏 + 每格限 1 个 */
    @Inject(method = "<init>", at = @At("TAIL"), remap = false, require = 1)
    private void wildfires$shrinkInputInv(BlockEntityType<?> type, BlockPos pos, BlockState state, CallbackInfo ci)
    {
        wildfires$rebuildInputInventory();
    }

    /**
     * 禁用盖板：让 {@code addBlockedSlots()} 直接返回 false。
     *
     * <p>返回 false 时模组自己的 {@code use()} 走 {@code ifeq} 分支，
     * 既不消耗盖板物品、也不重建 inventory。
     * 用 {@code cancellable = true} + setReturnValue 最干净，比改方法体字节码耐久。
     */
    @Inject(method = "addBlockedSlots", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void wildfires$disableSlotCover(CallbackInfoReturnable<Boolean> cir)
    {
        cir.setReturnValue(Boolean.FALSE);
    }

    /**
     * 把 inputInv 换成 1 格的新实例，每格限 1 个，并补回模组原本挂的回调。
     *
     * <p>模组构造里给 inputInv 挂了 {@code whenContentsChanged(contents -> recipesDirty = true)}，
     * 反编译 {@code lambda$new$0} 确认它就干这一件事。重建后必须补回去，
     * 否则玩家往输入槽塞东西时模组不会重算配方，机器可能不认新放进去的料。
     *
     * <p>失败时安静跳过（保持模组原生 3 格），绝不因此崩掉方块实体。
     */
    private void wildfires$rebuildInputInventory()
    {
        try
        {
            final Class<?> self = this.getClass();

            final Class<?> smartInvClass = Class.forName(
                    "com.simibubi.create.foundation.item.SmartInventory");
            final Class<?> syncedClass = Class.forName(
                    "com.simibubi.create.foundation.blockEntity.SyncedBlockEntity");

            final Object fresh = smartInvClass
                    .getConstructor(int.class, syncedClass)
                    .newInstance(WILDFIRES_INPUT_SLOTS, this);

            // 每格只放 1 个
            smartInvClass.getMethod("withMaxStackSize", int.class).invoke(fresh, 1);

            // 补回 recipesDirty 回调（recipesDirty 是私有字段，只能反射写）
            final java.lang.reflect.Field dirtyField = self.getDeclaredField("recipesDirty");
            dirtyField.setAccessible(true);
            final java.util.function.Consumer<Integer> onChanged = slot ->
            {
                try
                {
                    dirtyField.setBoolean(this, true);
                }
                catch (IllegalAccessException ignored)
                {
                    // 已 setAccessible，理论上到不了这里
                }
            };
            smartInvClass
                    .getMethod("whenContentsChanged", java.util.function.Consumer.class)
                    .invoke(fresh, onChanged);

            self.getField("inputInv").set(this, fresh);
        }
        catch (ReflectiveOperationException ex)
        {
            // ★ 2026-10-23：一开始这里是静默 catch，结果"改了半天没生效"根本查不出原因。
            //   改成打日志——失败必须留下痕迹，否则只能靠猜。
            com.mojang.logging.LogUtils.getLogger().error(
                    "[Wildfires] helve hammer 输入栏缩成 1 格失败，保持模组原生 3 格：{}", ex.toString());
        }
    }
}