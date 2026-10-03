package first.wildfires.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 铺在地面上的筋腱（同时用于风干的筋腱；湿筋腱见 {@link FreshSinewBlock}）。
 *
 * <p>放置规则：下方方块的上表面必须完整，用的是原版红石粉那条 {@code isFaceSturdy(UP)}；
 * 自己这一格或六个相邻格里有水时放不下去，放下之后水过来了会被冲掉并掉落本体。
 * 掉落逻辑交给原版：{@code updateShape} 返回空气时引擎走 {@code Block#updateOrDestroy}
 * → {@code destroyBlock(pos, true)}，不需要自己 popItem。
 */
public class SinewBlock extends Block {
    /** 1 像素高的碰撞箱/描边，与原 KubeJS 版本的 box(0,0,0,16,1,16) 一致。 */
    private static final VoxelShape SHAPE = box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);

    /** TFC 把河水/泉水/盐水/无限水都收进这个流体标签，用来补齐 #minecraft:water 之外的变种。 */
    private static final TagKey<Fluid> TFC_ANY_WATER =
            TagKey.create(Registries.FLUID, new ResourceLocation("tfc", "any_water"));

    public SinewBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (hasWaterAround(level, pos)) {
            return false;
        }
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.canSurvive(level, pos)
                ? super.updateShape(state, direction, neighborState, level, pos, neighborPos)
                : Blocks.AIR.defaultBlockState();
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        // updateShape 覆盖不到的边角情况：水从正上方灌进来、或水在旁边生成但没触发形状更新
        if (!level.isClientSide && !state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    public boolean canBeReplaced(BlockState state, Fluid fluid) {
        // 让水流进自己这一格：FlowingFluid#beforeDestroyingBlock 会先掉落本体，而不是无声消失
        return isWater(fluid) || super.canBeReplaced(state, fluid);
    }

    /** 自己这一格以及上下前后左右六格是否有水。 */
    protected static boolean hasWaterAround(LevelReader level, BlockPos pos) {
        if (isWater(level.getFluidState(pos))) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            if (isWater(level.getFluidState(pos.relative(direction)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 水判定：原版 {@code #minecraft:water}（TFC 已经把河水/泉水/盐水并了进去）+ TFC 的
     * {@code #tfc:any_water}，最后再加一层流体 id 兜底，标签缺失或加载顺序异常时也不会漏判。
     */
    protected static boolean isWater(FluidState fluid) {
        if (fluid.isEmpty()) {
            return false;
        }
        if (fluid.is(FluidTags.WATER) || fluid.is(TFC_ANY_WATER)) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid.getType());
        return id != null && id.getPath().endsWith("water");
    }

    protected static boolean isWater(Fluid fluid) {
        return isWater(fluid.defaultFluidState());
    }
}
