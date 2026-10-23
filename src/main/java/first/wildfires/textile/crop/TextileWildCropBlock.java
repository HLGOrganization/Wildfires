package first.wildfires.textile.crop;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.common.blocks.ISpecialPile;
import net.dries007.tfc.common.blocks.plant.TFCBushBlock;
import net.dries007.tfc.util.calendar.Calendars;
import net.dries007.tfc.util.calendar.Month;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 单格野生作物。
 *
 * <p>从 textile-2.2.3 的 {@code TextileWildCropBlock} 逐方法移植。
 *
 * <p>★ 核心特性：<b>成熟与否取决于 TFC 日历的月份</b> ——
 * {@link #isMature} 在 <b>JUNE ~ OCTOBER</b>（6~10 月）之间返回 true。
 * 这是字节码里读出来的（`Month.JUNE.ordinal()` 到 `Month.OCTOBER.ordinal()`）。
 */
public class TextileWildCropBlock extends TFCBushBlock implements ISpecialPile
{
    public static final BooleanProperty MATURE = BooleanProperty.create("mature");

    public TextileWildCropBlock(ExtendedProperties properties)
    {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(MATURE, false));
    }

    /** 按 TFC 日历判断是否成熟：6 月到 10 月之间为成熟。 */
    public static boolean isMature(LevelAccessor level)
    {
        final int month = Calendars.get(level).getCalendarMonthOfYear().ordinal();
        return month >= Month.JUNE.ordinal() && month <= Month.OCTOBER.ordinal();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return this.defaultBlockState().setValue(MATURE, isMature(context.getLevel()));
    }

    /** 每次随机 tick 检查月份，跨月时更新 MATURE。 */
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (state.getValue(MATURE) != isMature(level))
        {
            level.setBlockAndUpdate(pos, state.cycle(MATURE));
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return TextileCropBlock.FULL_SHAPE;
    }

    @Override
    public BlockState getHiddenState(BlockState state, boolean isTop)
    {
        return state.setValue(MATURE, false);
    }

    @Override
    public BlockState getHiddenStateAbove(BlockState state, boolean isTop)
    {
        return state != null && state.hasProperty(MATURE) ? state.setValue(MATURE, false) : state;
    }

    /** 只长在 {@code tfc:grass_plantable_on} 标签的方块上。 */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos)
    {
        return state.is(TFCTags.Blocks.GRASS_PLANTABLE_ON);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(MATURE);
    }
}