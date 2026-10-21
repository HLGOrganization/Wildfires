package first.wildfires.mixin.minecraft;

import first.wildfires.compat.tfcieaddon.QuartzBudDrip;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 石英芽的滴水粒子入口。
 *
 * <p>原版 {@code PointedDripstoneBlock} 在 {@code animateTick} 里撒
 * {@code ParticleTypes.DRIPPING_DRIPSTONE_WATER}；四个石英芽都是 {@code AmethystBlock} 的子类、
 * 一路继承到 {@code Block#animateTick}（空方法），自己也没有覆写，所以挂在这里。
 *
 * <p>客户端每 tick 只对玩家附近的一小批方块调 {@code animateTick}，这里第一道仍是
 * {@link QuartzBudDrip} 的 id 白名单，非石英芽（含原版紫水晶）立刻返回。
 */
@Mixin(Block.class)
public abstract class BlockAnimateTickMixin {

    @Inject(method = "animateTick", at = @At("HEAD"))
    private void wildfires$quartzBudDripParticles(BlockState state, Level level, BlockPos pos, RandomSource random,
                                                  CallbackInfo ci) {
        QuartzBudDrip.animateTick(state, level, pos, random);
    }
}
