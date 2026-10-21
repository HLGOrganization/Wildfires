package first.wildfires.compat.tfcieaddon;

import first.wildfires.mixin.minecraft.CauldronDripAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * TFC-IE-Crossover 的四个石英芽：滴水 + 有条件生长（老大 2026-10-20 定）。
 *
 * <h2>只认这四个方块</h2>
 * 每次动作前都先按注册 id 白名单判定，原版紫水晶（{@code minecraft:amethyst_cluster} /
 * {@code minecraft:budding_amethyst}）一个都不在名单里，所以本类永远不会改到原版紫水晶：
 * <pre>
 * tfc_ie_addon:mineral/small_quartz_bud   → medium
 * tfc_ie_addon:mineral/medium_quartz_bud  → large
 * tfc_ie_addon:mineral/large_quartz_bud   → quartz_cluster
 * tfc_ie_addon:mineral/quartz_cluster     → 不再生长，只滴水
 * </pre>
 * 母岩是 {@code tfc_ie_addon:mineral/quartz_block}。
 *
 * <h2>滴水（原版 PointedDripstoneBlock 的语义）</h2>
 * 原版水是从「石锥贴附的那个方块的上方」来的，不是从锥尖那一格的上方：{@code maybeTransferFluid} →
 * {@code getFluidAboveStalactite} → {@code findRootBlock}（沿锥链往上找到第一个非石锥方块）→
 * {@code .above()}。所以经典水车结构是「水 → 方块 → 倒挂的石锥 → 炼药锅」，水在贴附方块上面。
 * 石英芽照这个语义实现：
 * <ul>
 *   <li>芽自己的正上方是水（含水方块 / 贴附方块本身 waterlogged）——对应"方块上方有水源"；</li>
 *   <li>芽朝下的贴附面（{@code FACING == DOWN}，即倒挂在方块底下）时，再看贴附方块再上方的那一格。</li>
 * </ul>
 * 水用 {@code FluidTags.WATER} 判定：TFC 把 {@code tfc:river_water}/{@code tfc:spring_water}/
 * {@code tfc:salt_water} 都塞进了 {@code minecraft:water} 这个 fluid tag（jar 内
 * {@code data/minecraft/tags/fluids/water.json}），所以 TFC 的水和原版水、以及任何 waterlogged
 * 方块（{@code getFluidState} 返回水）都会算。
 *
 * <h2>炼药锅</h2>
 * 填充走原版自己的虚接口 {@code AbstractCauldronBlock#canReceiveStalactiteDrip} /
 * {@code #receiveStalactiteDrip}（{@link CauldronDripAccess} 暴露的 {@code @Invoker}），因此：
 * 空炼药锅 → 水炼药锅；水炼药锅 → 水位 +1；满锅 / 细雪锅 / 岩浆锅 / TFC 自己的
 * {@code FluidCauldronBlock}（没覆写这两个方法，默认 false）不会被灌。
 * 原版是 {@code scheduleTick(50 + 距离)} 之后由 {@code AbstractCauldronBlock#tick} 再去上面找石锥，
 * 但那条路只认 {@code minecraft:pointed_dripstone}，石英芽永远找不到；所以这里直接调用
 * {@code receiveStalactiteDrip}，把 50+ 刻的延迟省掉（只是时间差，结果与水车一致）。
 *
 * <h2>生长</h2>
 * 随机刻时若「正上方是 {@code quartz_block}」且「再上方含 {@code FluidTags.WATER} 的水」，按
 * {@code nextInt(15) < 4}（4/15）升一级。母岩 {@code BuddingQuartzBlock#randomTick} 是
 * {@code nextInt(15) == 0}（1/15），4/15 正好是它的 4 倍。
 */
public final class QuartzBudDrip {

    /** 原版 {@code WATER_TRANSFER_PROBABILITY_PER_RANDOM_TICK}，水车灌锅每随机刻的概率。 */
    private static final float DRIP_TRANSFER_CHANCE = 0.17578125F;
    /** 原版 {@code animateTick} 的滴水粒子概率（水在锥上方时 0.12）。 */
    private static final float DRIP_PARTICLE_CHANCE = 0.12F;
    /** 原版 {@code MAX_SEARCH_LENGTH_BETWEEN_STALACTITE_TIP_AND_CAULDRON} 的循环：i = 1..10。 */
    private static final int MAX_CAULDRON_DISTANCE = 10;
    /** 每级生长的判定：nextInt(15) < 4 ⇒ 4/15。 */
    private static final int GROWTH_ROLL = 15;
    private static final int GROWTH_SUCCESS = 2;
    /** 原版 {@code REQUIRED_SPACE_TO_DRIP_THROUGH_NON_SOLID_BLOCK}，判断能否往下一路滴到锅。 */
    private static final VoxelShape DRIP_GAP = Block.box(6.0D, 0.0D, 6.0D, 10.0D, 16.0D, 10.0D);

    private static final ResourceLocation QUARTZ_BLOCK_ID = id("mineral/quartz_block");
    private static final ResourceLocation SMALL_QUARTZ_BUD_ID = id("mineral/small_quartz_bud");
    private static final ResourceLocation MEDIUM_QUARTZ_BUD_ID = id("mineral/medium_quartz_bud");
    private static final ResourceLocation LARGE_QUARTZ_BUD_ID = id("mineral/large_quartz_bud");
    private static final ResourceLocation QUARTZ_CLUSTER_ID = id("mineral/quartz_cluster");

    /** 顺序即等级：small → medium → large → cluster。注册表冻结后才会被第一次随机刻读到。 */
    private static volatile Block[] buds;
    private static volatile Block quartzBlock;

    private QuartzBudDrip() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("tfc_ie_addon", path);
    }

    private static Block block(ResourceLocation id) {
        // tfc_ie_addon 缺席时 get() 给的是 AIR（DefaultedRegistry），下面每个入口都会因此静默跳过。
        return BuiltInRegistries.BLOCK.get(id);
    }

    private static Block[] buds() {
        Block[] value = buds;
        if (value == null) {
            value = new Block[]{
                    block(SMALL_QUARTZ_BUD_ID),
                    block(MEDIUM_QUARTZ_BUD_ID),
                    block(LARGE_QUARTZ_BUD_ID),
                    block(QUARTZ_CLUSTER_ID)
            };
            buds = value;
        }
        return value;
    }

    private static Block quartzBlock() {
        Block value = quartzBlock;
        if (value == null) {
            value = block(QUARTZ_BLOCK_ID);
            quartzBlock = value;
        }
        return value;
    }

    /** 等级：0 = small、1 = medium、2 = large、3 = cluster，其它方块（含原版紫水晶）一律 -1。 */
    private static int tierOf(Block block) {
        if (block == Blocks.AIR || !(block instanceof AmethystClusterBlock)) {
            return -1;
        }
        Block[] value = buds();
        for (int i = 0; i < value.length; i++) {
            if (value[i] == block) {
                return i;
            }
        }
        return -1;
    }

    /** 只对那四个 id 返回 true；原版紫水晶（哪怕同样是 AmethystClusterBlock）返回 false。 */
    public static boolean isQuartzBud(Block block) {
        return tierOf(block) >= 0;
    }

    /**
     * 服务端随机刻：滴水灌锅 + 有条件生长。挂在 {@code BlockBehaviour#randomTick} 的 HEAD 上，
     * 所以对每个随机刻的方块都会走一次，第一道就是上面那个 id 白名单，原版紫水晶在这里直接返回。
     */
    public static void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int tier = tierOf(state.getBlock());
        if (tier < 0) {
            return;
        }
        if (canDrip(state, level, pos) && random.nextFloat() < DRIP_TRANSFER_CHANCE) {
            fillCauldronBelow(level, pos);
        }
        if (tier < 3 && canGrow(level, pos) && random.nextInt(GROWTH_ROLL) < GROWTH_SUCCESS) {
            grow(level, pos, state, tier);
        }
    }

    /**
     * 客户端 {@code animateTick}：原版 {@code PointedDripstoneBlock#animateTick} 的粒子那一半。
     * 概率同为 0.12，位置照 {@code spawnDripParticle}（{@code (y+1) - 0.6875 - 0.0625 = y + 0.25}）。
     */
    public static void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!level.isClientSide || tierOf(state.getBlock()) < 0) {
            return;
        }
        if (!canDrip(state, level, pos) || random.nextFloat() > DRIP_PARTICLE_CHANCE) {
            return;
        }
        level.addParticle(ParticleTypes.DRIPPING_DRIPSTONE_WATER,
                pos.getX() + 0.5D, dripY(pos), pos.getZ() + 0.5D, 0.0D, 0.0D, 0.0D);
    }

    /** 原版石锥尖端的滴水高度：{@code (y + 1) - 0.6875 - 0.0625}。 */
    private static double dripY(BlockPos pos) {
        return (double) (pos.getY() + 1) - 0.6875D - 0.0625D;
    }

    /**
     * 是否具备"能滴水"的条件：没被水泡（原版 {@code canDrip} 就要求 {@code !WATERLOGGED}），且
     * 正上方是水；倒挂（{@code FACING == DOWN}）时再接受贴附方块再上方那格水（原版
     * {@code findRootBlock().above()} 的语义）。
     */
    private static boolean canDrip(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getValue(AmethystClusterBlock.WATERLOGGED)) {
            return false;
        }
        if (level.getFluidState(pos.above()).is(FluidTags.WATER)) {
            return true;
        }
        return state.getValue(AmethystClusterBlock.FACING) == Direction.DOWN
                && level.getFluidState(pos.above(2)).is(FluidTags.WATER);
    }

    /** 「正上方是石英母岩，再上方是水」——生长条件，逐字按老大的要求。 */
    private static boolean canGrow(ServerLevel level, BlockPos pos) {
        Block mother = quartzBlock();
        return mother != Blocks.AIR
                && level.getBlockState(pos.above()).is(mother)
                && level.getFluidState(pos.above(2)).is(FluidTags.WATER);
    }

    /**
     * 沿正下方找炼药锅：中间只允许原版 {@code canDripThrough} 那种"能滴过去"的方块（空气、细雪等），
     * 最多 10 格，和原版 {@code findFillableCauldronBelowStalactiteTip} 的搜索一致。
     */
    private static void fillCauldronBelow(ServerLevel level, BlockPos from) {
        for (int i = 1; i <= MAX_CAULDRON_DISTANCE; i++) {
            BlockPos target = from.below(i);
            BlockState state = level.getBlockState(target);
            if (state.getBlock() instanceof AbstractCauldronBlock cauldron) {
                if (cauldron.isFull(state)) {
                    return;
                }
                CauldronDripAccess access = (CauldronDripAccess) cauldron;
                if (!access.wildfires$canReceiveStalactiteDrip(Fluids.WATER)) {
                    return;
                }
                access.wildfires$receiveStalactiteDrip(state, level, target, Fluids.WATER);
                // 原版在锅上方 play 1504 让客户端补一颗滴水粒子；1504 的实现只认 pointed_dripstone，
                // 这里直接给半径内玩家发同一颗粒子。
                level.sendParticles(ParticleTypes.DRIPPING_DRIPSTONE_WATER,
                        from.getX() + 0.5D, dripY(from), from.getZ() + 0.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                return;
            }
            if (!canDripThrough(level, target, state)) {
                return;
            }
        }
    }

    /** 原版 {@code PointedDripstoneBlock#canDripThrough} 的逐行复制。 */
    private static boolean canDripThrough(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (state.isSolidRender(level, pos)) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        return !Shapes.joinIsNotEmpty(DRIP_GAP, shape, BooleanOp.AND);
    }

    /** 升一级：换成下一个 id 的方块，FACING / WATERLOGGED 等能对上的属性原样保留。 */
    private static void grow(ServerLevel level, BlockPos pos, BlockState state, int tier) {
        Block nextBlock = buds()[tier + 1];
        if (nextBlock == Blocks.AIR || nextBlock == state.getBlock()) {
            return;
        }
        BlockState nextState = nextBlock.defaultBlockState();
        for (Property<?> property : state.getProperties()) {
            if (nextState.hasProperty(property)) {
                nextState = copyProperty(nextState, state, property);
            }
        }
        level.setBlockAndUpdate(pos, nextState);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(nextState));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState copyProperty(BlockState target, BlockState source, Property<?> property) {
        return target.setValue((Property) property, (Comparable) source.getValue(property));
    }
}
