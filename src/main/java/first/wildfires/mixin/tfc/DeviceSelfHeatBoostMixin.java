package first.wildfires.mixin.tfc;

import first.wildfires.compat.create.FanBellowsBoost;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 鼓风机吹着 TFC 热源时，热源<b>自身</b>的【升温】速度也按鼓风机倍率放大。
 *
 * <h2>和 DeviceInnerItemHeatBoostMixin 的分工</h2>
 * <table border="1">
 *   <tr><th>本 mixin</th><td>改设备<b>自身</b>升温 —— {@code adjustDeviceTemp} 那条线</td></tr>
 *   <tr><th>DeviceInnerItemHeatBoostMixin</th><td>改设备对<b>内部物品</b>的加热 —— {@code addTemp} 那条线</td></tr>
 * </table>
 * 老大 2026-10-23 定：「升温指的是自身的升温速度，加热指的是容器给物品提供的加热速度」。
 *
 * <h2>TFC 原本的升温（HeatCapability.adjustDeviceTemp）</h2>
 * <pre>
 * float target = targetDeviceTemp(burn, airTicks, raining);
 * if (current != target) {
 *     float speed = 1.0f, range = 1.0f;
 *     if (airTicks > 0) { speed = 2.0f; range = 0.5f; }   // ← 有风就只有这两档
 *     return adjustTempTowards(current, target, speed, range);
 * }
 * </pre>
 * {@code speed} 是<b>硬编码</b>的 1.0 / 2.0，所以光靠灌空气只能拿到「有风=2倍」这一档，
 * 转速再高也不会更快。老大要的是「升温也按倍率走」，所以这里直接改 speed。
 *
 * <h2>怎么改</h2>
 * 拦 {@code adjustDeviceTemp} 这一次调用，拿它的 4 个参数自己重算一遍：
 * <pre>
 *   speed2 = speed × factor                  // factor 来自鼓风机转速
 *   return adjustTempTowards(current, target, speed2, range);
 * </pre>
 * 为什么不在 {@code adjustDeviceTemp} 内部改：那是 <b>static</b> 方法，
 * 拿不到 Level/BlockPos，查不了"这里有没有风"。
 * 而调用它的三个 {@code serverTick} 都带 {@code (Level, BlockPos, ...)}，
 * 所以把 redirect 放在调用方，靠 Mixin 从局部变量表补 level/pos 进来。
 *
 * <h2>覆盖范围</h2>
 * 反编译扫过 TFC 全部 {@code *BlockEntity}，调用 {@code adjustDeviceTemp} 的只有三个：
 * <pre>
 *   AbstractFirepitBlockEntity   ← 篝火 / 烤架 / 陶锅（都继承它）
 *   CharcoalForgeBlockEntity     ← 木炭炉
 *   BlastFurnaceBlockEntity      ← 高炉
 * </pre>
 * 坩埚<b>不</b>调这个 —— 坩埚自身不升温，它的温度来自下方热源，所以坩埚只走
 * {@link CrucibleFanBoostMixin}（加热那条线）。
 *
 * <h2>★ 注意 deviceHeatingModifier 会被乘两次</h2>
 * {@code adjustTempTowards(current, target, speed, range)} 内部还会乘一次
 * {@code TFCConfig.SERVER.deviceHeatingModifier}。当前配置是 1.0，所以无影响；
 * 若以后把这个配置调到 2.0，本 mixin 的 factor 仍然是在它之上再乘 —— 这是预期行为。
 */
@Mixin(value = {
        net.dries007.tfc.common.blockentities.AbstractFirepitBlockEntity.class,
        net.dries007.tfc.common.blockentities.CharcoalForgeBlockEntity.class,
        net.dries007.tfc.common.blockentities.BlastFurnaceBlockEntity.class
}, remap = false)
public abstract class DeviceSelfHeatBoostMixin
{
    @Redirect(
            method = "serverTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/dries007/tfc/common/capabilities/heat/HeatCapability;adjustDeviceTemp(FFIZ)F"),
            remap = false,
            require = 1
    )
    private static float wildfires$boostDeviceSelfHeat(
            float currentTemp,
            float burnTemp,
            int airTicks,
            boolean raining,
            Level level,
            BlockPos pos)
    {
        final float target = net.dries007.tfc.common.capabilities.heat.HeatCapability
                .targetDeviceTemp(burnTemp, airTicks, raining);

        if (currentTemp == target)
        {
            return target;
        }

        float speed = 1.0F;
        float range = 1.0F;
        if (airTicks > 0)
        {
            speed = 2.0F;
            range = 0.5F;
        }

        // ★ 升温也按倍率走（老大 2026-10-23 要求）
        if (level != null && !level.isClientSide())
        {
            speed *= FanBellowsBoost.factorAt(level, pos, level.getGameTime());
        }

        return net.dries007.tfc.common.capabilities.heat.HeatCapability
                .adjustTempTowards(currentTemp, target, speed, range);
    }
}