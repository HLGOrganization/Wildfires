package first.wildfires.mixin.tfc;

import first.wildfires.compat.create.FanBellowsBoost;
import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 鼓风机吹着火坑时，火坑<b>正上方</b>的坩埚对内部物品的<b>加热速度</b>也按同样倍率加速。
 *
 * <h2>TFC 坩埚原本怎么给内部物品加热（反编译 CrucibleBlockEntity.serverTick）</h2>
 * <pre>
 * IHeat heat = HeatCapability.get(stack);
 * if (heat != null) {
 *     HeatCapability.addTemp(heat,
 *         crucible.temperature,                 // 目标温度
 *         crucible.temperature * 0.0025f);      // ← 每次加热的幅度
 * }
 * </pre>
 * {@code addTemp(heat, target, amount)} 内部是：
 * <pre>
 *   newTemp = heat.temp + amount * itemHeatingModifier / heat.heatCapacity;
 *   if (newTemp > target) newTemp = target;      // 不会超过热源温度
 * </pre>
 * 所以<b>放大 amount 就是放大加热速度</b>，且仍然受热源温度封顶，
 * 符合「加速加热」而不是「凭空生热」。
 *
 * <h2>为什么改常量 0.0025f 是安全的</h2>
 * 反编译确认：<b>整个 CrucibleBlockEntity 里 0.0025f 只出现一次</b>
 * （扫过 TFC 全部 *BlockEntity，也只有这一个类用到它）。
 *
 * <h2>拿不到实例怎么办</h2>
 * {@code serverTick} 是 <b>static</b> 方法，handler 里没有 {@code this}。
 * 但它的第三个参数就是那个 {@code CrucibleBlockEntity}（偏移 0 处 {@code aload_3} 之后一直在用），
 * 所以给 handler 声明完整的参数表，Mixin 会按签名把它们都传进来。
 */
@Mixin(value = CrucibleBlockEntity.class, remap = false)
public abstract class CrucibleFanBoostMixin
{
    @ModifyConstant(
            method = "serverTick",
            constant = @Constant(floatValue = 0.0025F),
            remap = false
    )
    private static float wildfires$boostInnerItemHeat(
            float original,
            Level level,
            net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state,
            CrucibleBlockEntity crucible)
    {
        if (level == null || level.isClientSide())
        {
            return original;
        }
        // 鼓风机吹的是坩埚下面那一格（火坑）。没有风时 factorBelow 返回 1.0。
        final float factor = FanBellowsBoost.factorBelow(level, crucible.getBlockPos(), level.getGameTime());
        return original * factor;
    }
}