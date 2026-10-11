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
 * 关掉 Valhelsia 家具椅子的"自动吸附"。
 *
 * <p>椅子实现了 {@code SeatableBlock}，并在 {@code Block#entityInside}（SRG {@code m_7892_}）里把任何碰到
 * 碰撞箱的实体建一个 {@code SeatEntity} 直接按上去，所以人一走进椅子范围就被吸住。这里把那个回调整个取消。
 *
 * <p>只去掉"碰一下就坐"：右键坐下走的是另一个方法（{@code Block#use}，SRG {@code m_6227_}），不受影响。
 * 若要连右键也一并禁掉，说一声，在同一个类里再插一手即可。
 *
 * <p>{@code UpholsteredChairBlock} 继承本类且没有重写这个方法，所以这一个 mixin 也覆盖它。目标写 SRG 名字 +
 * {@code remap = false}，和仓库里其它第三方守卫一致；{@code @Pseudo} 让这个模组缺席时安静跳过，
 * {@code require = 1} 让它在模组换了实现时当场报错，而不是悄悄失效。
 */
@Pseudo
@Mixin(targets = "net.valhelsia.valhelsia_furniture.common.block.ChairBlock", remap = false)
public abstract class ChairAutoSitGuardMixin {

    @Inject(method = "m_7892_", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void wildfires$doNotSitEntitiesAutomatically(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        ci.cancel();
    }
}
