package first.wildfires.textile.crop;

import net.dries007.tfc.common.blockentities.FarmlandBlockEntity.NutrientType;
import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.common.blocks.IForgeBlockExtension;
import net.dries007.tfc.common.blocks.soil.HoeOverlayBlock;
import net.dries007.tfc.util.climate.ClimateRange;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * Textile 作物方块基类。
 *
 * <p>★ 2026-10-23 实测纠正：它继承的是<b>原版</b> {@code net.minecraft.world.level.block.CropBlock}，
 * <b>不是</b> TFC 的 {@code tfc...blocks.crop.CropBlock}。
 * 昨天误判成继承 TFC 的，导致 {@code growthTick} 参数类型对不上
 * （TFC 版要求 TFC 的 {@code CropBlockEntity}，而 Textile 用<b>自己的</b>
 * {@link TextileCropBlockEntity}）。
 *
 * <p>真正继承 TFC CropBlock 的只有单格的 {@code TextileDefaultCropBlock}；
 * 双高作物 {@link TextileDoubleCropBlock} 走的是这条原版继承线。
 *
 * <p>因此生长逻辑必须自己搭：原版 CropBlock 的 {@code randomTick} 只做默认生长，
 * 这里的生长由 {@link TextileCropHelpers} 按 TFC 日历推进（见 {@link #growthTick}）。
 *
 * <p>实现的 TFC 接口：
 * <ul>
 *   <li>{@link ITextileCropBlock} —— 本模组自己的作物契约</li>
 *   <li>{@link HoeOverlayBlock} —— 锄头指上去显示养分/气候提示</li>
 *   <li>{@link IForgeBlockExtension} —— 提供 {@link #getExtendedProperties()}</li>
 * </ul>
 */
public abstract class TextileCropBlock extends CropBlock implements HoeOverlayBlock, ITextileCropBlock, IForgeBlockExtension
{
    /** 枯死方块用来记"是否成熟"，和 TFC 的 DeadCropBlock.MATURE 同名同义。 */
    public static final BooleanProperty MATURE = BooleanProperty.create("mature");

    public static final VoxelShape QUARTER_SHAPE = box(2, 0, 2, 14, 4, 14);
    public static final VoxelShape HALF_SHAPE = box(2, 0, 2, 14, 8, 14);
    public static final VoxelShape FULL_SHAPE = box(2, 0, 2, 14, 16, 14);

    protected final NutrientType primaryNutrient;
    protected final Supplier<? extends Block> dead;
    protected final Supplier<? extends Item> seeds;
    protected final Supplier<ClimateRange> climateRange;
    protected final int maxAge;

    private final ExtendedProperties extendedProperties;

    protected TextileCropBlock(ExtendedProperties properties, int maxAge,
                               Supplier<? extends Block> dead,
                               Supplier<? extends Item> seeds,
                               NutrientType primaryNutrient,
                               Supplier<ClimateRange> climateRange)
    {
        super(properties.properties());
        this.extendedProperties = properties;
        this.maxAge = maxAge;
        this.dead = dead;
        this.seeds = seeds;
        this.primaryNutrient = primaryNutrient;
        this.climateRange = climateRange;
    }

    @Override
    public ExtendedProperties getExtendedProperties()
    {
        return extendedProperties;
    }

    /** 把 age 属性登记进方块状态（原版 CropBlock 不会自动加，必须子类补）。 */
    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(getAgeProperty());
    }

    @Override
    protected abstract IntegerProperty getAgeProperty();

    @Override
    public int getMaxAge()
    {
        return maxAge;
    }

    @Override
    public abstract VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context);

    /** 原版 CropBlock 的随机 tick：这里只做"没有方块实体时的兜底"，真正的生长走日历。 */
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        // 生长由 TextileCropBlockEntity 的日历回调驱动；这里不重复推进，避免双重生长。
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        return level.getBlockState(pos.below()).getBlock() instanceof net.dries007.tfc.common.blocks.soil.ISoilBlock
                || level.getBlockState(pos.below()).is(net.minecraft.tags.BlockTags.DIRT)
                || level.getBlockState(pos.below()).is(Blocks.FARMLAND);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState,
                                  LevelAccessor level, BlockPos pos, BlockPos facingPos)
    {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    /** 掉落：种子（按 TFC 的收成规则，这里简化为给 1 个种子）。 */
    @Override
    protected net.minecraft.world.level.ItemLike getBaseSeedId()
    {
        return seeds.get();
    }

    // ---------------------------------------------------------------- 生长

    /**
     * 日历推进时被 {@link TextileCropBlockEntity#onCalendarUpdate} 调用。
     *
     * <p>字节码实测的原模组实现：
     * <pre>
     * if (!level.isClientSide() &amp;&amp; TextileCropHelpers.growthTick(level, pos, state, crop))
     *     postGrowthTick(level, pos, state, crop);
     * </pre>
     */
    @Override
    public void growthTick(Level level, BlockPos pos, BlockState state, TextileCropBlockEntity crop)
    {
        if (!level.isClientSide() && TextileCropHelpers.growthTick(level, pos, state, crop))
        {
            postGrowthTick(level, pos, state, crop);
        }
    }

    /** 生长后刷新方块状态（双高作物覆盖它来同步另一格）。 */
    protected abstract void postGrowthTick(Level level, BlockPos pos, BlockState state, TextileCropBlockEntity crop);

    /** 气候区间。 */
    @Override
    public ClimateRange getClimateRange()
    {
        return climateRange.get();
    }

    /** 主养分。 */
    @Override
    public NutrientType getPrimaryNutrient()
    {
        return primaryNutrient;
    }

    /** 枯死：替换成枯死方块。 */
    @Override
    public void die(Level level, BlockPos pos, BlockState state, boolean fullyGrown)
    {
        final BlockState deadState = dead.get().defaultBlockState();
        level.setBlockAndUpdate(pos, deadState.hasProperty(MATURE)
                ? deadState.setValue(MATURE, fullyGrown)
                : deadState);
    }

    /** 锄头提示（气候/养分）。原模组有完整实现，这里给最小可用版本。 */
    @Override
    public void addHoeOverlayInfo(Level level, BlockPos pos, BlockState state, java.util.List<net.minecraft.network.chat.Component> text, boolean isDebug)
    {
        text.add(net.minecraft.network.chat.Component.literal("Nutrient: " + primaryNutrient.name()));
    }
}