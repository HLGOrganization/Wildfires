package first.wildfires.mixin.tfc;

import first.wildfires.compat.create.FanBellowsBoost;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 鼓风机吹着 TFC 热源时，热源对其<b>内部物品</b>的<b>加热速度</b>也按同样倍率加速。
 *
 * <h2>覆盖哪些方块</h2>
 * 反编译扫过 TFC 全部 {@code *BlockEntity}，调用 {@code HeatCapability.addTemp} 的只有五个：
 * <pre>
 *   CrucibleBlockEntity          addTemp(heat, temp, temp * 0.0025f)   ← 由 CrucibleFanBoostMixin 处理
 *   FirepitBlockEntity           addTemp(heat, temp)                   ← 本 mixin
 *   CharcoalForgeBlockEntity     addTemp(heat, temp)                   ← 本 mixin
 *   BlastFurnaceBlockEntity      addTemp(heat, temp)                   ← 本 mixin
 *   GrillBlockEntity             addTemp(heat, temp)                   ← 本 mixin
 * </pre>
 * 单参版 {@code addTemp(heat, target)} 内部就是 {@code addTemp(heat, target, 3.0f)}，
 * 系数写死 3.0。要让倍率生效，得绕过它、直接调三参版并传入 {@code 3.0 * factor}。
 *
 * <h2>为什么用 @Redirect 而不是 @ModifyConstant</h2>
 * 这五个类里 {@code 3.0f} 这个字面量可能出现多次（别的方法也用到），
 * 改常量有误伤风险。{@code @Redirect} 精确拦「对这个方法的调用」，
 * 只影响加热这一处（设备自身的升温由 TFC 的 adjustDeviceTemp 管，不走这里），其他逻辑不动。
 *
 * <p>★ handler 的签名要和方法原文一致：{@code addTemp(IHeat, float)}，
 * 多出来的 {@code level/pos/state} 由 Mixin 按调用点的局部变量表补上。
 * 这几个类里 {@code handleCooking()} 都是实例方法，所以第一个参数是 {@code this}。
 */
@Mixin(value = {
        net.dries007.tfc.common.blockentities.FirepitBlockEntity.class,
        net.dries007.tfc.common.blockentities.CharcoalForgeBlockEntity.class,
        net.dries007.tfc.common.blockentities.BlastFurnaceBlockEntity.class,
        net.dries007.tfc.common.blockentities.GrillBlockEntity.class
}, remap = false)
public abstract class DeviceInnerItemHeatBoostMixin
{
    /** 单参 addTemp 内部使用的固定系数（反编译确认：float 3.0f） */
    private static final float TFC_BASE_AMOUNT = 3.0F;

    @Redirect(
            method = "handleCooking",
            at = @At(value = "INVOKE",
                    target = "Lnet/dries007/tfc/common/capabilities/heat/HeatCapability;addTemp(Lnet/dries007/tfc/common/capabilities/heat/IHeat;F)V"),
            remap = false,
            require = 0
    )
    private void wildfires$boostDeviceInnerHeat(
            net.dries007.tfc.common.capabilities.heat.IHeat heat,
            float targetTemp)
    {
        final BlockEntity self = (BlockEntity) (Object) this;
        final Level level = self.getLevel();
        float factor = 1.0F;
        if (level != null && !level.isClientSide())
        {
            factor = FanBellowsBoost.factorAt(level, self.getBlockPos(), level.getGameTime());
        }
        net.dries007.tfc.common.capabilities.heat.HeatCapability.addTemp(
                heat, targetTemp, TFC_BASE_AMOUNT * factor);
    }
}