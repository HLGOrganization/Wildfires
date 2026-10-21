package first.wildfires.mixin.tfc;

import first.wildfires.utils.ArmorWear;
import net.dries007.tfc.util.PhysicalDamageType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * TFC 物理抗性的两处改造（老大 2026-10-18 定）。
 *
 * <h2>一、抗性从「指数」改成「线性百分比」</h2>
 * TFC 原本：{@code PhysicalDamageType.calculateMultiplier} 的最后一步是
 * <pre>return (float) Math.pow(Math.E, -0.01 * R);   // R = 实体抗性 + 四件护甲抗性之和</pre>
 * 即 {@code 伤害 × e^(-R/100)}：R = 8 只减 7.7%，R = 100 也只减 63%。本 mixin 改成
 * <pre>伤害 × (1 - R/100)          // R = 8 ⇒ 减 8%，R = 100 ⇒ 全免，下限 0</pre>
 * 改法：重定向那次 {@code Math.pow(E, -0.01R)}。它就在 return 前，参数只有 E 与 {@code -0.01R}，
 * 反解 {@code R = -exponent * 100} 即可，不必重算 TFC 那段「实体抗性 + 遍历护甲槽」的累加逻辑。
 *
 * <h2>二、抗性跟着护甲耐久阶段一起衰减</h2>
 * TFC 的护甲抗性来自 {@code getResistanceForItem(ItemStack)}（数据包 {@code item_damage_resistances}
 * 或护甲材料的 {@code Multiplier} 实现）。这里重定向它，把每件护甲自己的抗性乘上该件的耐久阶段系数
 * （{@link ArmorWear}：剩 ≤50%/≤25%/≤10% 时 ×0.85/×0.70/×0.55），与护甲值、韧性同步衰减。
 * ★ 只影响"护甲件"这一路：生物自身的 {@code EntityDamageResistance} 走另一个分支，不受影响。
 *
 * <p>注意：{@code tfc:bypasses_damage_resistance} 标签里的伤害仍不吃抗性（走 type == null 分支，
 * 根本到不了 pow 这一步）。
 */
@Mixin(value = PhysicalDamageType.class, remap = false)
public class PhysicalDamageTypeMixin {

    /**
     * 线性抗性：TFC 用 {@code Math.pow(E, -0.01R)}，这里换成 {@code 1 - R/100}（下限 0）。
     */
    @Redirect(method = "calculateMultiplier",
              at = @At(value = "INVOKE", target = "Ljava/lang/Math;pow(DD)D"),
              remap = false)
    private static double wildfires$linearResistance(double base, double exponent) {
        // TFC 传进来的是 E 和 -0.01 * R
        double resistance = -exponent * 100.0;
        double multiplier = 1.0 - resistance / 100.0;
        // R >= 100 视为全免；负抗性（如苦力怕斩击 -25）会放大伤害，不设上限
        return Math.max(0.0, multiplier);
    }

    /**
     * 抗性按护甲耐久阶段衰减：把 {@code calculateMultiplier} 里逐件取抗性的那次调用接过来，
     * 返回一个按该件阶段系数缩放的包装。护甲件之外的来源（生物抗性）不经过这里。
     */
    @Redirect(method = "calculateMultiplier",
              at = @At(value = "INVOKE",
                       target = "Lnet/dries007/tfc/util/PhysicalDamageType;getResistanceForItem(Lnet/minecraft/world/item/ItemStack;)Lnet/dries007/tfc/util/PhysicalDamageType$Multiplier;"),
              remap = false)
    private static PhysicalDamageType.Multiplier wildfires$degradeResistanceByWear(ItemStack stack) {
        PhysicalDamageType.Multiplier original = PhysicalDamageType.getResistanceForItem(stack);
        if (original == null) {
            return null;
        }
        float factor = ArmorWear.factor(stack);
        if (factor >= 1.0F) {
            return original;
        }
        return new ScaledMultiplier(original, factor);
    }

    /** 把一份抗性按系数缩小；三段默认方法 value(...) 会走这些 getter，所以只包这三个就够 */
    private static final class ScaledMultiplier implements PhysicalDamageType.Multiplier {

        private final PhysicalDamageType.Multiplier delegate;
        private final float factor;

        private ScaledMultiplier(PhysicalDamageType.Multiplier delegate, float factor) {
            this.delegate = delegate;
            this.factor = factor;
        }

        @Override
        public float crushing() {
            return delegate.crushing() * factor;
        }

        @Override
        public float piercing() {
            return delegate.piercing() * factor;
        }

        @Override
        public float slashing() {
            return delegate.slashing() * factor;
        }
    }
}
