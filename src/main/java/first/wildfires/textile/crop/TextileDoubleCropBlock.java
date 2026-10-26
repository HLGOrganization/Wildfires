package first.wildfires.textile.crop;

import net.dries007.tfc.common.blockentities.FarmlandBlockEntity.NutrientType;
import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.util.climate.ClimateRange;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * Textile 的双高作物方块（棉花 / 亚麻都占上下两格）。
 *
 * <p>从 textile-2.2.3 的 {@code TextileDoubleCropBlock} 移植。
 *
 * <p>★ 与原模组的关键差异（因为不复用它的私有辅助类）：
 * <ul>
 *   <li>原模组的 {@code create()} 返回一个匿名子类 {@code TextileDoubleCropBlock$1}，
 *       只为把 age 属性塞进父类构造；这里改成显式的 ageProperty 参数，语义等价。</li>
 *   <li>原模组 {@code diesInto()} 判断"上方是不是同类方块"用 {@code getBlock() == this}；
 *       这里同样。</li>
 * </ul>
 *
 * <p>★ 两格规则（字节码实测）：
 * <ul>
 *   <li>{@link #growthTick} 只在下半格推进（上半格完全不动）</li>
 *   <li>{@link #postGrowthTick} 写下方 age；若 age &gt; maxSingleAge，再把头顶那格升成 TOP</li>
 *   <li>{@link #playerWillDestroy} 破坏任意一格时，把另一格也清掉（防止留半截）</li>
 * </ul>
 */
public class TextileDoubleCropBlock extends TextileCropBlock
{
    /** 上/下半格标记。 */
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    // ★ 关键：原版 CropBlock 的构造函数会调用 getAgeProperty()，
    //   而那时子类字段还没初始化（Java 的初始化顺序：super() 先于字段赋值）。
    //   所以 ageProperty 必须先在一个"构造前就能取到"的地方放好。
    //   做法：create() 里先写静态 pendingAgeProperty，构造期间 getAgeProperty() 读它，
    //   构造完成后清空。方块构造是单线程（注册期），这样是安全的。
    private static IntegerProperty PENDING_AGE_PROPERTY = null;

    private final IntegerProperty ageProperty;
    private final int maxSingleAge;
    private final float maxSingleGrowth;

    protected TextileDoubleCropBlock(ExtendedProperties properties, int maxSingleAge, int maxAge,
                                     Supplier<? extends Block> dead,
                                     Supplier<? extends Item> seeds,
                                     NutrientType primaryNutrient,
                                     Supplier<ClimateRange> climateRange,
                                     IntegerProperty ageProperty)
    {
        super(properties, maxAge, dead, seeds, primaryNutrient, climateRange);
        this.ageProperty = ageProperty;
        this.maxSingleAge = maxSingleAge;
        this.maxSingleGrowth = (float) maxSingleAge / (float) maxAge;
        // super() 返回后 stateDefinition 已建好（含 PART，因为 createBlockStateDefinition 被调过），
        // 此时才能安全设默认值
        this.registerDefaultState(this.defaultBlockState().setValue(PART, Part.BOTTOM));
    }

    /**
     * 建一个双高作物方块。
     *
     * <p>对照原模组 {@code create(props, maxSingleAge, maxAge, crop)}：
     * 它先算 {@code ageProperty = IntegerProperty.create("age", 0, maxSingleAge + maxAge - 1)}，
     * 然后传给构造函数 {@code (maxSingleAge - 1, maxSingleAge + maxAge - 1)}。
     * 这两个 -1 是原模组的写法（它的 age 从 0 起算并留一格给 TOP），照抄。
     */
    public static TextileDoubleCropBlock create(ExtendedProperties properties, int maxSingleAge, int maxAge, TextileCrop crop)
    {
        final IntegerProperty ageProperty = TextileBlockStateProperties.getAgeProperty(maxSingleAge + maxAge - 1);
        PENDING_AGE_PROPERTY = ageProperty;
        try
        {
            return new TextileDoubleCropBlock(
                    properties,
                    maxSingleAge - 1,
                    maxSingleAge + maxAge - 1,
                    () -> TextileBlocks.getDeadCrop(crop).get(),
                    () -> TextileBlocks.SeedLookup.getSeed(crop),
                    crop.getPrimaryNutrient(),
                    TextileClimateRanges.supplier(crop),
                    ageProperty
            );
        }
        finally
        {
            PENDING_AGE_PROPERTY = null;
        }
    }

    @Override
    public IntegerProperty getAgeProperty()
    {
        // super() 期间实例字段还是 null，回退到构造前预设的那个
        return ageProperty != null ? ageProperty : PENDING_AGE_PROPERTY;
    }

        @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return state.getValue(PART) == Part.TOP ? FULL_SHAPE : HALF_SHAPE;
    }

    /** 下方必须是耕地（下半格）或自己（上半格）。 */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        if (state.getValue(PART) == Part.TOP)
        {
            final BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(PART) == Part.BOTTOM;
        }
        return super.canSurvive(state, level, pos);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState,
                                  LevelAccessor level, BlockPos pos, BlockPos facingPos)
    {
        if (state.getValue(PART) == Part.TOP && facing == Direction.DOWN && !facingState.is(this))
        {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, facing, facingState, level, pos, facingPos);
    }

    /** 只在下半格推进生长。 */
    @Override
    public void growthTick(Level level, BlockPos pos, BlockState state, TextileCropBlockEntity crop)
    {
        if (state.getValue(PART) == Part.BOTTOM)
        {
            super.growthTick(level, pos, state, crop);
        }
    }

    /**
     * 生长后刷新两格。
     *
     * <p>字节码实测流程：
     * <pre>
     * above = pos.above(); aboveState = level.getBlockState(above);
     * age = clamp((int)(growth * getMaxAge()), 0, getMaxAge());
     * level.setBlock(pos, state.setValue(age, age), 2);
     * if (age > maxSingleAge && (aboveState.isAir() || aboveState.getBlock() == this))
     *     level.setBlock(above, state.setValue(age).setValue(PART, TOP), 3);
     * </pre>
     */
    @Override
    protected void postGrowthTick(Level level, BlockPos pos, BlockState state, TextileCropBlockEntity crop)
    {
        final BlockPos above = pos.above();
        final BlockState aboveState = level.getBlockState(above);

        final int age = Math.max(0, Math.min((int) (crop.getGrowth() * this.getMaxAge()), this.getMaxAge()));
        level.setBlock(pos, state.setValue(getAgeProperty(), age), 2);

        if (age > maxSingleAge && (aboveState.isAir() || aboveState.getBlock() == this))
        {
            level.setBlock(above, state.setValue(getAgeProperty(), age).setValue(PART, Part.TOP), 3);
        }
    }

    /** 生长上限：下半格单独能长到 maxSingleAge，再往上就是 TOP 格的事了。 */
    @Override
    public float getGrowthLimit(Level level, BlockPos pos, BlockState state)
    {
        return maxSingleGrowth;
    }

    /** 破坏任意一格都把另一格一起清掉。 */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)
    {
        final Part part = state.getValue(PART);
        final BlockPos otherPos = part == Part.BOTTOM ? pos.above() : pos.below();
        final BlockState otherState = level.getBlockState(otherPos);

        if (otherState.is(this) && otherState.getValue(PART) != part)
        {
            level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
            level.levelEvent(player, 2001, otherPos, Block.getId(otherState));
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /** 用来判断"这一格是不是同类方块的上/下半格"。 */
    protected boolean isSameOrAir(BlockState state)
    {
        return state.isAir() || state.is(this);
    }

    /** 上/下半格。 */
    public enum Part implements StringRepresentable
    {
        BOTTOM("bottom"),
        TOP("top");

        private final String serializedName;

        Part(String serializedName)
        {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName()
        {
            return serializedName;
        }
    }
}
