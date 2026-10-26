package first.wildfires.utils;

import net.dries007.tfc.util.PhysicalDamageType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;

/**
 * 护甲「属性抗性」的统一取数口（老大 2026-10-25 定）。
 *
 * <h2>三条规则</h2>
 * <ol>
 *   <li><b>抗性先减免</b>（已有）：TFC 在 {@code LivingHurtEvent} 里把传入伤害乘
 *       {@code (1 - 抗性)}。这一步早于护甲减免。</li>
 *   <li><b>耐久损耗也乘抗性</b>：某件护甲这次要掉 {@code N} 点耐久，
 *       若它有 {@code r} 的抗性，实掉 {@code N × (1 - r)}。</li>
 *   <li><b>穿甲改成 {@code 50% × (1 - 抗性)}</b>，再减护甲韧性抵扣：
 *       原口径是固定 20% − 韧性×1%，现在抗性越高穿甲越低。</li>
 * </ol>
 *
 * <h2>为什么单独抽一个类</h2>
 * 「某件护甲的某类抗性」要在两处用到（耐久损耗、穿甲），
 * 且都必须<b>包含耐久阶段衰减</b>（{@link ArmorWear}：剩 ≤50%/≤25%/≤10% ⇒ ×0.85/0.70/0.55），
 * 否则两处算出来的数会对不上。集中在这里，改一处等于两处一起改。
 *
 * <p>★ 全免疫伤害（{@code tfc:bypasses_damage_resistance} 标签）的抗性恒为 0，
 * 这与 TFC 自己 {@code Multiplier.value} 的行为一致。
 */
public final class ArmorResistance {

    private ArmorResistance() {
    }

    /**
     * 取一件护甲对某次伤害的<b>有效抗性</b>，已含耐久阶段衰减。
     *
     * @return 0.0 ~ 1.0（TFC 的实际值域；负抗性会返回负数，代表"更疼"，由调用方决定怎么用）
     */
    public static float of(ItemStack stack, DamageSource source) {
        if (stack == null || stack.isEmpty() || source == null) {
            // ★ source == null 必须提前返回：Multiplier.value 第一行就调 source.is(...)，
            //   传 null 进去会 NPE（反编译确认，2026-10-25）
            return 0.0F;
        }
        // 不享受抗性的伤害类型（TFC 标签），直接 0
        if (source.is(PhysicalDamageType.BYPASSES_DAMAGE_RESISTANCES)) {
            return 0.0F;
        }
        PhysicalDamageType.Multiplier multiplier = PhysicalDamageType.getResistanceForItem(stack);
        if (multiplier == null) {
            return 0.0F;
        }
        // type 可能是 null（伤害来源没落进任何 IS_* 标签）—— 那时 TFC 的 value() 取三者最大值，
        // 是它本来的保守语义，照用即可
        PhysicalDamageType type = PhysicalDamageType.getTypeForSource(source);
        float raw = multiplier.value(type, source);
        // 与 TFC 原逻辑一致：按该件的耐久阶段衰减
        return raw * ArmorWear.factor(stack);
    }

    /**
     * 耐久损耗系数：{@code 1 - 抗性}，夹到 {@code [0, 2]}。
     *
     * <p>大于 1 的情况来自<b>负抗性</b>（如苦力怕斩击 −25% ⇒ 系数 1.25，更容易掉耐久）。
     * 上限 2 是防止数据包写出离谱的负数抗性把耐久瞬间打光。
     */
    public static float wearFactor(float resistance) {
        return Math.max(0.0F, Math.min(2.0F, 1.0F - resistance));
    }

    /**
     * 穿甲比例：{@code 50% × (1 - 抗性) − 韧性×1%}，下限 0。
     *
     * <p>原口径 {@code 20% − 韧性×1%} 的对照（老大给的例子）：
     * <pre>
     * 抗性 0%   ⇒ 50% − 韧性%      （原来 20%）
     * 抗性 10%  ⇒ 45% − 韧性%      （老大原话："造成伤害的 45% 是穿甲"）
     * 抗性 50%  ⇒ 25% − 韧性%
     * 抗性 100% ⇒ 0%
     * </pre>
     */
    public static float penetration(float resistance, float armorToughness) {
        return Math.max(0.0F, 0.5F * (1.0F - resistance) - armorToughness * 0.01F);
    }
}
