package first.wildfires.mixin.create;

import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import first.wildfires.compat.create.FanBellowsCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让鼓风机正对 TFC 设备时，等效于一台一直工作的风箱。
 *
 * <p>逻辑在 {@link FanBellowsCompat}（普通类）里，这里只负责每 tick 转一下。
 *
 * <p>★ 为什么挂在 {@code tick()} 的 TAIL：Create 自己在这个方法里算 {@code airCurrent}
 * （含 {@code getAirFlowDirection()} 的朝向判定）。放在 HEAD 会读到上一 tick 的朝向，
 * 玩家刚转向那一帧会漏吹；放 TAIL 拿到的永远是这一 tick 的最新朝向。
 */
@Mixin(value = EncasedFanBlockEntity.class, remap = false)
public abstract class EncasedFanBellowsMixin
{
    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void wildfires$blowIntoBellowsConsumer(CallbackInfo ci)
    {
        final EncasedFanBlockEntity self = (EncasedFanBlockEntity) (Object) this;
        if (self.getLevel() != null)
        {
            FanBellowsCompat.tick(self, self.getLevel().getGameTime());
        }
    }
}