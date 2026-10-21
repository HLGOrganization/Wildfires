package first.wildfires.mixin.minecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 把原版炼药锅接滴水的两个 protected 方法暴露出来，供
 * {@link first.wildfires.compat.tfcieaddon.QuartzBudDrip} 调用。
 *
 * <p>这是原版 {@code AbstractCauldronBlock#tick} 自己用的那对口子：{@code canReceiveStalactiteDrip}
 * 决定这口锅收不收水（空锅 / 水锅 true，细雪锅 / 岩浆锅 / 满锅 / TFC 的 FluidCauldronBlock false），
 * {@code receiveStalactiteDrip} 负责真正加一格水并发 1047 音效。用它而不是直接改 blockstate，
 * 是为了让所有继承 {@code AbstractCauldronBlock} 的锅按它们自己的实现决定收不收。
 *
 * <p>只加方法、不改任何现有行为；没有滴水的锅完全不受影响。
 */
@Mixin(AbstractCauldronBlock.class)
public interface CauldronDripAccess {

    @Invoker("canReceiveStalactiteDrip")
    boolean wildfires$canReceiveStalactiteDrip(Fluid fluid);

    @Invoker("receiveStalactiteDrip")
    void wildfires$receiveStalactiteDrip(BlockState state, Level level, BlockPos pos, Fluid fluid);
}
