package first.wildfires.mixin.vintageimprovements;

import first.wildfires.compat.vintageimprovements.HelveHammerAnimation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 客户端逐 tick 保存运动区间，渲染时插值相位，跨周期与最后一锤均落回零位。 */
@Pseudo
@Mixin(targets = "com.negodya1.vintageimprovements.content.kinetics.helve_hammer.HelveBlockEntity", remap = false)
public abstract class HelveAnimationMixin
{
    @Shadow private int timer;
    @Shadow private int hammerBlows;
    @Shadow private int operatingMode;
    @Shadow public abstract float getSpeed();
    @Shadow public abstract int getProcessingSpeed(float speed);

    @Unique private float wildfires$animationStart;
    @Unique private float wildfires$animationEnd;
    @Unique private boolean wildfires$animationReady;
    @Unique private boolean wildfires$returnToRest;

    @Inject(method = "tickClient", at = @At("HEAD"), remap = false, require = 1)
    private void wildfires$captureAnimationStep(CallbackInfo ci)
    {
        float speed = getSpeed();
        boolean running = speed != 0 && timer > 0
                && (operatingMode == 2 || operatingMode == 1 && hammerBlows > 0);
        // 在扣除/重置 timer 前保存。周期末尾仅插到零，不倒着跨整圈插值。
        wildfires$animationStart = running ? Math.min(HelveHammerAnimation.PERIOD, timer)
                : wildfires$animationReady ? wildfires$animationEnd : 0;
        wildfires$animationEnd = running
                ? Math.max(0, wildfires$animationStart - getProcessingSpeed(speed)) : 0;
        // 停转/取消时只收回当前角度，不能把剩余抬落动作压进一 tick 补敲。
        wildfires$returnToRest = !running && (speed == 0 || operatingMode != 1 && operatingMode != 2
                || wildfires$animationStart > getProcessingSpeed(speed));
        wildfires$animationReady = true;
    }

    @Unique
    public float wildfires$getInterpolatedHammerAngle(float partialTick)
    {
        if (!wildfires$animationReady)
            return HelveHammerAnimation.angle((operatingMode == 2
                    || operatingMode == 1 && hammerBlows > 0) ? timer : 0);
        if (wildfires$returnToRest)
            return HelveHammerAnimation.angle(wildfires$animationStart)
                    * (1 - Math.max(0, Math.min(1, partialTick)));
        return HelveHammerAnimation.interpolate(wildfires$animationStart, wildfires$animationEnd, partialTick);
    }

    @Inject(method = "getHammerAngle", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void wildfires$renderFullCycle(CallbackInfoReturnable<Float> cir)
    {
        cir.setReturnValue(wildfires$getInterpolatedHammerAngle(Minecraft.getInstance().getFrameTime()));
    }
}
