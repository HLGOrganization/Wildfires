package first.wildfires.block;

import first.wildfires.register.BlockRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 湿筋腱：连续干燥 20 分钟（24000 刻，与原 KubeJS 版本的 {@code entity.tick >= 24000} 一致）之后
 * 自动风干成 {@code wildfires:dried_sinew}；中途下雨会把风干进度清零、从头计时。
 *
 * <p>用方块计划刻推进，没有方块实体：不占内存、不注册 ticker。进度记在 {@link #AGE} 上
 * （每格 1 分钟），所以进度和计划刻都会随区块存档一起保存，重启服务器不会丢。
 *
 * <p>下雨判定用原版 {@code Level#isRainingAt}，它自带「能看到天空 + 头顶无遮挡」判断，
 * 因此搭棚盖住的筋腱不会因为下雨而重置，只有露天淋到的才会。
 */
public class FreshSinewBlock extends SinewBlock {
    /** 风干总时长：连续 24000 刻没淋到雨。 */
    public static final int DRY_TICKS = 24000;

    /** 天气检查间隔，同时也是进度推进的步长：1 分钟。 */
    public static final int CHECK_INTERVAL = 1200;

    /** 干燥进度满格值：从 0 涨到 19 正好经过 20 次检查，也就是 24000 刻。 */
    public static final int DRY_STAGE = DRY_TICKS / CHECK_INTERVAL - 1;

    /** 已连续干燥的分钟数，下雨时归零。 */
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, DRY_STAGE);

    public FreshSinewBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        // 同一格重复放置（例如 /setblock）时不要重复排期
        if (!level.isClientSide && !oldState.is(this)) {
            level.scheduleTick(pos, this, CHECK_INTERVAL);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            // 风干期间水漫过来/支撑没了：补一次掉落，避免物品凭空消失
            level.destroyBlock(pos, true);
            return;
        }

        if (level.isRainingAt(pos)) {
            // 淋湿了：进度清零，重新计时。只在进度非零时写方块，省掉每分钟一次的无效方块更新
            if (state.getValue(AGE) != 0) {
                level.setBlock(pos, state.setValue(AGE, 0), Block.UPDATE_CLIENTS);
            }
            level.scheduleTick(pos, this, CHECK_INTERVAL);
            return;
        }

        int age = state.getValue(AGE);
        if (age >= DRY_STAGE) {
            // 连续干燥满 24000 刻
            level.setBlockAndUpdate(pos, BlockRegister.DriedSinew.get().defaultBlockState());
            return;
        }

        // 只改 age、不改形状也不影响邻居，所以只同步客户端
        level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, CHECK_INTERVAL);
    }
}
