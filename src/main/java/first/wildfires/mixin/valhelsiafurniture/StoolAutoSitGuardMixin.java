package first.wildfires.mixin.valhelsiafurniture;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 凳子那一半：{@link ChairAutoSitGuardMixin} 的同款处理。
 *
 * <p>凳子（{@code StoolBlock}）自己实现了一份 {@code Block#entityInside}（SRG {@code m_7892_}），所以椅子
 * 那个 mixin 盖不到它，需要单独一份。取消的只是"碰到就坐"，右键坐下（{@code m_6227_}）照旧。
 */
@Pseudo
@Mixin(targets = "net.valhelsia.valhelsia_furniture.common.block.StoolBlock", remap = false)
public abstract class StoolAutoSitGuardMixin {

    @Inject(method = "m_7892_", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void wildfires$doNotSitEntitiesAutomatically(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        ci.cancel();
    }
}
