package first.wildfires.mixin.vintageimprovements;

import first.wildfires.compat.vintageimprovements.HelveHammerAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 把 helve hammer（锁链锤）的加工节奏改成「32 RPM 下 2 秒一次」，
 * 其他转速沿用原版整数处理速度，周期向上取整到完整 tick。
 *
 * <p>历史：2026-10-23 第一版做成 5 秒（timer=200），同日老大要求改成 2 秒（timer=80）。
 *
 * <h2>原版机制（反编译 HelveBlockEntity 得到）</h2>
 * <pre>
 * // tickServer()
 * int speed = getProcessingSpeed(getSpeed());   // = Mth.clamp((int)(|RPM| / 16), 1, 512)
 * if (timer > 0) {
 *     timer -= speed;                  // 每 tick 扣 speed
 *     if (timer <= 0) {
 *         hammerBlows--;
 *         if (hammerBlows <= 0) process();
 *         else timer = 500;            // ← 每次挥锤重置成 500
 *     }
 * }
 * // 配方匹配时也是 timer = 500
 * </pre>
 * 原版：32 RPM 时 speed = 32/16 = 2，单次挥锤 500/2 = <b>250 tick = 12.5 秒</b>。
 *
 * <h2>老大要的效果（2026-10-23 第二次调整：5 秒 → 2 秒）</h2>
 * 32 RPM 下 2 秒一次 ⇒ 需要 40 tick 走完 ⇒ timer 应为 2 × 40 = <b>80</b>。
 * 每 tick 扣整数处理速度，改成 80 之后，周期是 ceil(80 / speed)：
 * <pre>
 *    8 RPM → 80/1  =  80   tick = 4    秒
 *   16 RPM → 80/1  =  80   tick = 4    秒   ← speed 有下限 1
 *   32 RPM → 80/2  =  40   tick = 2    秒   ← 基准
 *   64 RPM → 80/4  =  20   tick = 1    秒
 *   96 RPM → ceil(80/6) = 14 tick = 0.7 秒
 *  128 RPM → 80/8  =  10   tick = 0.5  秒
 *  256 RPM → 80/16 =   5   tick = 0.25 秒
 * </pre>
 *
 * <p>★ 8 RPM 和 16 RPM 都是 4 秒：{@code getProcessingSpeed} 是
 * {@code Mth.clamp((int)(|RPM| / 16), 1, 512)}，低转速被 clamp 到 1，所以不再区分。
 * 这是模组原本的限制，不是本次改动引入的。
 *
 * <p>服务端四处、客户端一处的 500 必须同时替换；否则客户端连续敲击回到旧周期。
 *
 * <p>★ Vintage Improvements 不是本模组的编译依赖 ⇒ {@code @Pseudo} + 字符串 targets。
 */
@Pseudo
@Mixin(targets = "com.negodya1.vintageimprovements.content.kinetics.helve_hammer.HelveBlockEntity", remap = false)
public abstract class HelveBlockEntitySpeedMixin
{
    /** 32 RPM 下 2 秒 = 40 tick，速度系数 2 ⇒ timer 初值 80 */
    private static final int WILDFIRES_TIMER = HelveHammerAnimation.PERIOD;

    @ModifyConstant(method = {"tickServer", "tickClient"}, constant = @Constant(intValue = 500), remap = false, require = 5)
    private int wildfires$replaceTimer(int original)
    {
        return WILDFIRES_TIMER;
    }
}
