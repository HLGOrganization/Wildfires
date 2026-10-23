package first.wildfires.textile.crop;

import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 双高野生作物（野生棉花 / 野生亚麻）。
 *
 * <p>从 textile-2.2.3 的 {@code TextileWildDoubleCropBlock} 逐方法移植。
 * 继承 {@link TextileWildCropBlock}，多一个 {@link #PART} 属性区分上下半格。
 *
 * <p>★ 放置入口 {@link #placeTwoHalves} 是给世界生成 feature 用的：
 * 在 pos 放 BOTTOM、pos.above() 放 TOP，两格都带当前月份的 {@code MATURE 值。
 */
public class TextileWildDoubleCropBlock extends TextileWildCropBlock
{
    public static final EnumProperty<TextileDoubleCropBlock.Part> PART =
            EnumProperty.create("part", TextileDoubleCropBlock.Part.class);

    public TextileWildDoubleCropBlock(ExtendedProperties properties)
    {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(PART, TextileDoubleCropBlock.Part.BOTTOM));
    }

    /** 世界生成用：一次性放下上下两格。 */
    public void placeTwoHalves(LevelAccessor level, BlockPos pos, int flags)
    {
        final boolean mature = isMature(level);
        level.setBlock(pos, this.defaultBlockState()
                .setValue(PART, TextileDoubleCropBlock.Part.BOTTOM)
                .setValue(MATURE, mature), flags);
        level.setBlock(pos.above(), this.defaultBlockState()
                .setValue(PART, TextileDoubleCropBlock.Part.TOP)
                .setValue(MATURE, mature), flags);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        // 只有下半格负责跨月更新，上下两格一起改
        if (state.getValue(PART) == TextileDoubleCropBlock.Part.BOTTOM)
        {
            final boolean m = isMature(level);
            if (state.getValue(MATURE) != m)
            {
                level.setBlockAndUpdate(pos, state.setValue(MATURE, m));
                final BlockPos above = pos.above();
                final BlockState aboveState = level.getBlockState(above);
                if (aboveState.is(this))
                {
                    level.setBlockAndUpdate(above, aboveState.setValue(MATURE, m));
                }
            }
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return state.getValue(PART) == TextileDoubleCropBlock.Part.TOP
                ? TextileCropBlock.HALF_SHAPE
                : TextileCropBlock.FULL_SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return this.defaultBlockState()
                .setValue(PART, TextileDoubleCropBlock.Part.BOTTOM)
                .setValue(MATURE, isMature(context.getLevel()));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack)
    {
        level.setBlock(pos.above(), this.defaultBlockState()
                .setValue(PART, TextileDoubleCropBlock.Part.TOP)
                .setValue(MATURE, state.getValue(MATURE)), 3);
    }

    /** 上格要求下方是自己；下格沿用父类规则。 */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        if (state.getValue(PART) == TextileDoubleCropBlock.Part.TOP)
        {
            final BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(PART) == TextileDoubleCropBlock.Part.BOTTOM;
        }
        return super.canSurvive(state, level, pos);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState,
                                  LevelAccessor level, BlockPos pos, BlockPos facingPos)
    {
        if (state.getValue(PART) == TextileDoubleCropBlock.Part.TOP
                && facing == Direction.DOWN && !facingState.is(this))
        {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, facing, facingState, level, pos, facingPos);
    }

    /** 破坏任意一格都把另一半清掉。 */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)
    {
        final TextileDoubleCropBlock.Part part = state.getValue(PART);
        final BlockPos otherPos = part == TextileDoubleCropBlock.Part.BOTTOM ? pos.above() : pos.below();
        final BlockState otherState = level.getBlockState(otherPos);
        if (otherState.is(this) && otherState.getValue(PART) != part)
        {
            level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
            level.levelEvent(player, 2001, otherPos, Block.getId(otherState));
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }
}