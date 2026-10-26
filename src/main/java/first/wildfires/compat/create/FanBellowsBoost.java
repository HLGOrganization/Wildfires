package first.wildfires.compat.create;

import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 鼓风机吹到 TFC 热源时，把速度倍率记下来，供两类加速共用。
 *
 * <h2>老大要的效果</h2>
 * 鼓风机对着火坑 ⇒ 火坑自身的【升温】加速；
 * <b>火坑正上方</b>的坩埚（等容器）对其内部物品的【加热】也按同样倍率加速。
 *
 * <h2>为什么需要这张表</h2>
 * TFC 的设备完全不知道鼓风机在哪，也拿不到转速。
 * 所以由鼓风机每 tick 写一份「这个位置现在有风，倍率是多少」，
 * 设备自己升温 / 加热内部物品时来查。表用 {@link WeakHashMap} 按 Level 分，
 * 值里再按 BlockPos 存；每 20 tick 整体过期一次，避免鼓风机拆了倍率还留着。
 *
 * <h2>倍率公式（和 {@link FanBellowsCompat#airForRpm} 同一套）</h2>
 * <pre>
 *   steps  = max(0, (|rpm| - 32) / 32)   // 32 RPM 以下为 0，不减
 *   factor = 1.0 + 0.10 * steps          // 32→1.0, 64→1.1, 96→1.2 …
 * </pre>
 */
public final class FanBellowsBoost
{
    private FanBellowsBoost()
    {
    }

    /** 基础转速：32 RPM */
    public static final float BASE_RPM = 32.0F;

    /** 每高出多少转速 +10% */
    public static final float RPM_STEP = 32.0F;

    /** 每高一个 step 的加成 */
    public static final float BONUS_PER_STEP = 0.10F;

    /** 标记的有效期（tick）。鼓风机每秒刷一次，留一点余量。 */
    public static final int LIFETIME_TICKS = 40;

    /** 一条记录：倍率 + 记录时的游戏时间 */
    private record Boost(float factor, long stamp)
    {
    }

    private static final Map<Level, Map<BlockPos, Boost>> BOOSTS = new WeakHashMap<>();

    /**
     * 按转速算倍率。
     *
     * @param rpm 鼓风机转速（可负，取绝对值）
     * @return 倍率，最低 1.0
     */
    public static float factorForRpm(float rpm)
    {
        final float abs = Math.abs(rpm);
        final float steps = Math.max(0.0F, (abs - BASE_RPM) / RPM_STEP);
        return 1.0F + BONUS_PER_STEP * steps;
    }

    /**
     * 记下「这个位置被鼓风机吹着，倍率是 factor」。
     *
     * <p>同时向<b>正下方</b>传导一层：老大要的是「鼓风机吹火坑，火坑上方的坩埚也加速」。
     * 火坑在下、坩埚在上，所以真正需要往下传一格给热源本身，
     * 热源自身的升温由 TFC 处理；坩埚那层通过 {@link #factorBelow} 往下找一格解决。
     */
    public static void mark(Level level, BlockPos pos, float factor, long gameTime)
    {
        if (level == null || level.isClientSide())
        {
            return;
        }
        BOOSTS.computeIfAbsent(level, k -> new HashMap<>()).put(pos.immutable(), new Boost(factor, gameTime));
    }

    /**
     * 查某个位置当前的倍率。
     *
     * @return 没有风时返回 1.0
     */
    public static float factorAt(Level level, BlockPos pos, long gameTime)
    {
        if (level == null)
        {
            return 1.0F;
        }
        final Map<BlockPos, Boost> map = BOOSTS.get(level);
        if (map == null)
        {
            return 1.0F;
        }
        final Boost b = map.get(pos);
        if (b == null || gameTime - b.stamp() > LIFETIME_TICKS)
        {
            return 1.0F;
        }
        return b.factor();
    }

    /**
     * 查「这个方块所在柱子的下方一格」有没有被吹。
     *
     * <p>用于坩埚：坩埚本身没被直接吹到，但它下面的火坑被吹到了，
     * 所以坩埚要往下找一格。
     */
    public static float factorBelow(Level level, BlockPos pos, long gameTime)
    {
        return factorAt(level, pos.below(), gameTime);
    }

    /** 清掉某个 Level 的全部记录（换维度/卸载时用） */
    public static void clearLevel(Level level)
    {
        if (level != null)
        {
            BOOSTS.remove(level);
        }
    }
}