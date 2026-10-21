package first.wildfires.mixin.minecraft;

import first.wildfires.compat.tfcieaddon.QuartzBudDrip;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 石英芽的随机刻入口。
 *
 * <p>{@code AmethystClusterBlock}（四个石英芽的类）自己没有覆写 {@code randomTick}，继承的是
 * {@code BlockBehaviour#randomTick}（它只转调空的 {@code tick}），所以只能挂在这里。挂 HEAD 而不是
 * 挂 {@code AmethystClusterBlock} 是因为那个类里根本没有这个方法可注入。
 *
 * <p>对每个随机刻的方块都会走一次；第一道判定是 {@link QuartzBudDrip} 里的注册 id 白名单，
 * 原版紫水晶、草、作物等全部在第一行就返回，行为与没装这个 mixin 时一致。
 */
@Mixin(BlockBehaviour.class)
public abstract class BlockRandomTickMixin {

    @Inject(method = "randomTick", at = @At("HEAD"))
    private void wildfires$quartzBudRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random,
                                               CallbackInfo ci) {
        QuartzBudDrip.randomTick(state, level, pos, random);
    }
}
