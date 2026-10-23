package first.wildfires.textile.crop;

import net.dries007.tfc.common.blockentities.FarmlandBlockEntity.NutrientType;
import net.dries007.tfc.util.climate.ClimateRange;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 本模组作物的契约。
 *
 * <p>从 textile-2.2.3 的 {@code net.atobaazul.textile.crop.ITextileCropBlock} 移植，
 * 方法签名与默认实现逐条对应（2026-10-23 字节码核对）。
 */
public interface ITextileCropBlock
{
    /** 日历跳变时推进生长。 */
    void growthTick(Level level, BlockPos pos, BlockState state, TextileCropBlockEntity crop);

    /**
     * 本次能长到多高。
     *
     * <p>默认实现（字节码实测）：光照合格给 1.0，否则 0.0。
     * 双高作物会覆盖成 {@code maxSingleGrowth}。
     */
    default float getGrowthLimit(Level level, BlockPos pos, BlockState state)
    {
        return TextileCropHelpers.lightValid(level, pos) ? 1.0f : 0.0f;
    }

    /** 枯死。 */
    void die(Level level, BlockPos pos, BlockState state, boolean fullyGrown);

    ClimateRange getClimateRange();

    NutrientType getPrimaryNutrient();
}