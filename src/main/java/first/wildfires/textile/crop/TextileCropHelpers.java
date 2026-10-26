package first.wildfires.textile.crop;

import net.dries007.tfc.common.blockentities.FarmlandBlockEntity.NutrientType;
import net.dries007.tfc.common.blockentities.IFarmland;
import net.dries007.tfc.common.blocks.soil.FarmlandBlock;
import net.dries007.tfc.config.TFCConfig;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.calendar.ICalendar;
import net.dries007.tfc.util.climate.Climate;
import net.dries007.tfc.util.climate.ClimateRange;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Textile 作物的生长逻辑。
 *
 * <p>从 textile-2.2.3 的 {@code TextileCropHelpers} 还原。<b>算法是 2026-10-22 从字节码
 * 逐条抄下来的</b>（见记忆 D84），常量用 {@code javap -constants} 读的真值。
 *
 * <p>★ 常量（真值，不要改）：
 * <pre>
 * UPDATE_INTERVAL        = 48000L
 * GROWTH_FACTOR          = 1.7361111E-6f   (= 1/576000)
 * NUTRIENT_CONSUMPTION   = 3.4722223E-6f   (= 2/576000)
 * NUTRIENT_GROWTH_FACTOR = 0.5f
 * GROWTH_LIMIT           = 1.0f
 * EXPIRY_LIMIT           = 2.0f
 * YIELD_MIN              = 0.2f
 * YIELD_LIMIT            = 1.0f
 * </pre>
 */
public final class TextileCropHelpers
{
    private TextileCropHelpers()
    {
    }

    public static final long UPDATE_INTERVAL = 48000L;
    public static final float GROWTH_FACTOR = 1.7361111E-6f;
    public static final float NUTRIENT_CONSUMPTION = 3.4722223E-6f;
    public static final float NUTRIENT_GROWTH_FACTOR = 0.5f;
    public static final float GROWTH_LIMIT = 1.0f;
    public static final float EXPIRY_LIMIT = 2.0f;
    public static final float YIELD_MIN = 0.2f;
    public static final float YIELD_LIMIT = 1.0f;

    /** 光照够不够长（≥ 9，和 TFC 作物一致）。 */
    public static boolean lightValid(Level level, BlockPos pos)
    {
        return level.getRawBrightness(pos, 0) >= 9;
    }

    /**
     * 入口：检查是否到了更新间隔，到了就调 {@link #growthTickStep}。
     *
     * @return true 表示这次真的推进了生长（调用方要据此刷新方块状态）
     */
    public static boolean growthTick(Level level, BlockPos pos, BlockState state, TextileCropBlockEntity crop)
    {
        final long now = Calendars.SERVER.getTicks();
        final long last = crop.getLastGrowthTick();

        // 第一次 tick（last == 0）只记时间，不推进
        if (last == 0L)
        {
            crop.setLastGrowthTick(now);
            return false;
        }
        if (now - last < UPDATE_INTERVAL)
        {
            return false;
        }
        final long oldTick = last;
        final RandomSource random = level.getRandom();
        final boolean grown = growthTickStep(level, pos, state, random, oldTick, now, crop);
        // 无论是否 grown，都要把 lastGrowthTick 推进（growthTickStep 内部已设，这里兜底）
        if (!grown)
        {
            crop.setLastGrowthTick(now);
        }
        return grown;
    }

    /**
     * 一步生长（算法逐条对应原模组字节码，偏移量见记忆 D84）。
     *
     * @param oldTick 上次生长时刻
     * @param newTick 当前时刻
     */
    public static boolean growthTickStep(Level level, BlockPos pos, BlockState state, RandomSource random,
                                         long oldTick, long newTick, TextileCropBlockEntity crop)
    {
        final ICalendar calendar = Calendars.get(level);
        final BlockPos below = pos.below();
        final int hydration = FarmlandBlock.getHydration(level, below);

        final float tempOld = Climate.getTemperature(level, pos, calendar, Calendars.SERVER.ticksToCalendarTicks(oldTick));
        final float tempNew = Climate.getTemperature(level, pos, calendar, Calendars.SERVER.ticksToCalendarTicks(newTick));
        final long deltaTick = newTick - oldTick;

        final ITextileCropBlock block = (ITextileCropBlock) state.getBlock();
        final ClimateRange range = block.getClimateRange();

        // 气候：old 或 new 任一时刻通过即可
        final boolean climateOk = checkClimate(range, hydration, tempOld, tempNew, false)
                || checkClimate(range, hydration, tempOld, tempNew, true);

        final NutrientType nutrient = block.getPrimaryNutrient();
        float nutrientAvailable = 0f;
        final float nutrientConsumptionToDo = NUTRIENT_CONSUMPTION * (float) deltaTick;
        float nutrientConsumed = 0f;

        final BlockEntity be = level.getBlockEntity(below);
        if (be instanceof IFarmland farmland)
        {
            nutrientAvailable = farmland.getNutrient(nutrient);
            nutrientConsumed = farmland.consumeNutrientAndResupplyOthers(nutrient, nutrientConsumptionToDo);
        }

        final float growthModifier = TFCConfig.SERVER.cropGrowthModifier.get().floatValue();
        final float expiryModifier = TFCConfig.SERVER.cropExpiryModifier.get().floatValue();
        final float expiryUnit = 2f * expiryModifier * (1f / growthModifier);

        // 本步应增长的量（含 ±10% 随机）
        final float growthToDo = (1f / growthModifier)
                * uniform(random, 0.9f, 1.1f)
                * (float) deltaTick
                * GROWTH_FACTOR
                + nutrientConsumed * NUTRIENT_GROWTH_FACTOR;

        final float growthOld = crop.getGrowth();
        float growthRemaining = growthToDo;
        float growthNew = growthOld;
        float expiry = crop.getExpiry();
        float yield = crop.getYield();

        // 先按半步推进老化
        expiry *= expiryUnit / 2f;

        final float growthLimit = block.getGrowthLimit(level, pos, state);

        // ① 先长到上限
        if (growthRemaining > 0 && climateOk && growthNew < growthLimit)
        {
            final float step = Math.min(growthRemaining, growthLimit - growthNew);
            growthNew += step;
            growthRemaining -= step;
        }

        // ② 剩下的转成老化
        if (growthRemaining > 0)
        {
            final float step = Math.min(growthRemaining, expiryUnit - expiry);
            expiry += step;
        }

        // ③ 产量
        final float gained = growthNew - growthOld;
        final float efficiency;
        if (gained <= 0 || nutrientConsumptionToDo <= 0)
        {
            efficiency = 1f;
        }
        else
        {
            efficiency = Math.min(1f, (growthToDo / gained) * (nutrientConsumptionToDo / nutrientAvailable));
        }
        yield += gained * lerp(efficiency, YIELD_MIN, YIELD_LIMIT);

        // ④ 老化到头 或 气候不通过 ⇒ 枯死
        if (expiry >= expiryUnit || !climateOk)
        {
            block.die(level, pos, state, growthNew >= 1f);
            return false;
        }

        // 还原成 0..1 归一
        expiry *= 2f / expiryUnit;

        crop.setGrowth(growthNew);
        crop.setYield(yield);
        crop.setExpiry(expiry);
        crop.setLastGrowthTick(calendar.getTicks());
        return true;
    }

    /** 气候双向判定：strict 决定用哪一套 wiggle 范围。 */
    private static boolean checkClimate(ClimateRange range, int hydration, float tempOld, float tempNew, boolean strict)
    {
        return range.checkBoth(hydration, tempOld, strict) && range.checkBoth(hydration, tempNew, strict);
    }

    /** 线性插值。 */
    public static float lerp(float t, float min, float max)
    {
        return min + t * (max - min);
    }

    /** [min, max] 均匀随机。 */
    public static float uniform(RandomSource random, float min, float max)
    {
        return min + random.nextFloat() * (max - min);
    }
}
