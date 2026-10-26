package first.wildfires.compat.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import net.dries007.tfc.common.blockentities.BellowsBlockEntity;
import net.dries007.tfc.common.blocks.devices.IBellowsConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 让 Create 的鼓风机（encased fan）正对 TFC 设备时，等效于一台一直工作的 TFC 风箱。
 *
 * <h2>TFC 风箱原本怎么给热源加温（反编译得到）</h2>
 * <pre>
 * // BellowsBlockEntity.doPush()
 * consumer.intakeAir(level, pos, state, 200);        // BELLOWS_AIR = 200
 *
 * // CharcoalForgeBlockEntity.intakeAir(int amount)
 * airTicks = Math.min(airTicks + amount, 600);       // MAX_DEVICE_AIR_TICKS = 600
 *
 * // HeatCapability.adjustDeviceTemp(current, burn, airTicks, raining)
 * float speed = 1.0f, range = 1.0f;
 * if (airTicks > 0) { speed = 2.0f; range = 0.5f; }  // ← 设备自身的升温速度翻倍（注意：这是"升温"，不是"加热"）
 * return adjustTempTowards(current, target, speed, range);
 * </pre>
 * 所以「风箱在吹」这件事在 TFC 里等价于「{@code airTicks > 0}」，
 * 也就是只把空气灌进去就行，加温逻辑完全走 TFC 自己那套。
 *
 * <h2>鼓风机按转速给多少</h2>
 * 老大要求：<b>32 转速为基础，每高 32 转速 +10% 速度，低于 32 不减</b>。
 *
 * <p>TFC 里设备【升温】速度是一个 1.0 / 2.0 的档位（有风 vs 无风），
 * 直接改它会动到 TFC 的全局逻辑。这里改成<b>多灌空气</b>来实现等效效果：
 * 每 tick 灌入的空气量按转速放大，空气越多，
 * {@code adjustDeviceTemp} 被喂到的 {@code airTicks} 越稳定地保持在满值，
 * 同时我们用「额外空气量」表示那 +10% 的部分。
 *
 * <p>具体公式（{@code rpm} 为鼓风机绝对转速）：
 * <pre>
 *   steps   = max(0, (rpm - 32) / 32)     // 32 RPM 以下为 0，不减
 *   factor  = 1.0 + 0.10 * steps          // 32→1.0, 64→1.1, 96→1.2 …
 *   air     = round(200 * factor)         // 每秒灌一次（见 tick() 的节流）
 * </pre>
 * 灌入间隔固定为 1 秒（20 tick），和 TFC 风箱一次鼓风的节奏一致。
 */
public final class FanBellowsCompat
{
    private FanBellowsCompat()
    {
    }

    /** TFC 风箱一次鼓风的空气量（BellowsBlockEntity.BELLOWS_AIR） */
    public static final int BELLOWS_AIR = BellowsBlockEntity.BELLOWS_AIR;

    /** 基础转速：32 RPM */
    public static final float BASE_RPM = 32.0F;

    /** 每高出多少转速 +10% */
    public static final float RPM_STEP = 32.0F;

    /** 每高一个 step 的加成 */
    public static final float BONUS_PER_STEP = 0.10F;

    /** 灌空气的间隔（tick）。20 = 1 秒，和 TFC 风箱鼓风节奏一致。 */
    public static final int INTERVAL_TICKS = 20;

    /**
     * 按转速算一次该灌多少空气。
     *
     * @param rpm 鼓风机的绝对转速
     * @return 空气量；转速为 0 时返回 0
     */
    public static int airForRpm(float rpm)
    {
        final float abs = Math.abs(rpm);
        if (abs < 1.0F)
        {
            return 0;
        }
        final float steps = Math.max(0.0F, (abs - BASE_RPM) / RPM_STEP);
        final float factor = 1.0F + BONUS_PER_STEP * steps;
        return Math.round(BELLOWS_AIR * factor);
    }

    /**
     * 找鼓风机正对着的那个 TFC 风箱消费者。
     *
     * <p>鼓风机的气流方向 = {@code getAirFlowDirection()}（它自己算好的、考虑朝向与旋转的那一侧）；
     * 只检查<b>紧邻</b>的那一格——TFC 风箱也是紧邻设备才能吹到的。
     *
     * @return 目标方块坐标；找不到返回 null
     */
    public static BlockPos findTarget(EncasedFanBlockEntity fan)
    {
        final Level level = fan.getLevel();
        if (level == null)
        {
            return null;
        }
        final Direction flow = fan.getAirFlowDirection();
        if (flow == null)
        {
            return null;
        }
        final BlockPos target = fan.getBlockPos().relative(flow);
        final BlockState state = level.getBlockState(target);
        if (state.getBlock() instanceof IBellowsConsumer consumer
                && consumer.canAcceptAir(level, target, state))
        {
            return target;
        }
        return null;
    }

    /**
     * 每 tick 由 mixin 调用：按 1 秒的节奏给正对的 TFC 设备灌空气。
     *
     * @return true 表示这次真的灌了（供调试/统计用）
     */
    public static boolean tick(EncasedFanBlockEntity fan, long gameTime)
    {
        if (fan.getLevel() == null || fan.getLevel().isClientSide())
        {
            return false;
        }
        // 只按游戏时间的秒数取模，保证同一台鼓风机每秒只灌一次
        if (gameTime % INTERVAL_TICKS != 0)
        {
            return false;
        }

        final float rpm = ((KineticBlockEntity) fan).getSpeed();
        final int air = airForRpm(rpm);
        if (air <= 0)
        {
            return false;
        }

        final BlockPos target = findTarget(fan);
        if (target == null)
        {
            return false;
        }

        final Level level = fan.getLevel();
        final BlockState state = level.getBlockState(target);
        if (state.getBlock() instanceof IBellowsConsumer consumer)
        {
            consumer.intakeAir(level, target, state, air);
            // 2026-10-23 新增：记下这个热源被吹着、倍率多少。
            // 火坑自己升温用它；火坑正上方的坩埚通过 factorBelow() 查到同一份值（用的是同一倍率）。
            FanBellowsBoost.mark(level, target, FanBellowsBoost.factorForRpm(rpm), gameTime);
            return true;
        }
        return false;
    }
}