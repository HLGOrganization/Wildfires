package first.wildfires.mixin.minecraft;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import first.wildfires.utils.ArmorResistance;
import first.wildfires.utils.ArmorWear;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 护甲减免 + 穿甲 + 耐久阶段衰减（老大 2026-10-18 定，2026-10-25 改口径）。
 *
 * <p><b>护甲值</b>：已由 {@code ArmorValueEvents} 写进物品属性（tooltip / 护甲条 / 伤害计算三处一致），
 * 这里直接读该槽位的 ARMOR 修正之和。
 *
 * <p><b>护甲减免</b>：每点有效护甲减 5%，线性、无上限（超过 100% 时最终伤害夹 0）。
 * 有效护甲 = 护甲值 × 该件耐久阶段系数（{@link ArmorWear}：剩 ≤50%/≤25%/≤10% ⇒ 0.85/0.70/0.55）。
 *
 * <h2>★ 穿甲口径（2026-10-25 老大改）</h2>
 * <pre>
 * 穿甲比例 = 50% × (1 - 抗性) - 韧性×1%     下限 0
 * </pre>
 * 旧口径 {@code 20% - 韧性×1%} 已废弃。对照：
 * <pre>
 * 抗性 0%   ⇒ 50% - 韧性%
 * 抗性 10%  ⇒ 45% - 韧性%     ← 老大原话："造成伤害的 45% 是穿甲"
 * 抗性 50%  ⇒ 25% - 韧性%
 * 抗性 100% ⇒ 0%
 * </pre>
 * ★ 这里的"伤害"是<b>已过抗性</b>的（TFC 在 {@code LivingHurtEvent} 先乘 {@code 1-抗性}），
 * 而 {@code getDamageAfterArmorAbsorb} 收到的正是那之后的值 —— 时序天然对上。
 *
 * <h2>★ 耐久损耗口径（2026-10-25 新增）</h2>
 * 某件护甲这次要掉 {@code N} 点耐久，若<b>它自己</b>有 {@code r} 的抗性，实掉 {@code N × (1-r)}。
 * 例：头盔分到 10 点斩击损耗，头盔有 20% 斩击抗性 ⇒ 只掉 8 点。
 *
 * <p><b>磨损分摊</b>：基数 = 护甲吸收的伤害，按各件护甲值比例分摊，再各自乘自己的抗性系数。
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @ModifyExpressionValue(
            method = "getDamageAfterArmorAbsorb",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(FFF)F"
            )
    )
    private float getDamageAfterArmorAbsorb(float original, @Local(argsOnly = true) DamageSource damageSource, @Local(argsOnly = true) float damageAmount) {
        LivingEntity target = (LivingEntity) (Object) this;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

        float[] armorPerSlot = new float[slots.length];
        float[] resistPerSlot = new float[slots.length];
        float effectiveArmor = 0.0F;
        float factorSum = 0.0F;
        int worn = 0;
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = target.getItemBySlot(slots[i]);
            float pieceArmor = armorOf(stack, slots[i]);
            armorPerSlot[i] = pieceArmor;
            // 抗性按件取（含耐久阶段衰减），供穿甲和耐久损耗两处共用
            resistPerSlot[i] = ArmorResistance.of(stack, damageSource);
            if (pieceArmor <= 0.0F) {
                continue;
            }
            float factor = ArmorWear.factor(stack);
            effectiveArmor += pieceArmor * factor;
            factorSum += factor;
            worn++;
        }

        float setFactor = worn > 0 ? factorSum / worn : 1.0F;
        float armorToughness = (target.getAttribute(Attributes.ARMOR_TOUGHNESS) != null
                ? (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS)
                : 0.0F) * setFactor;

        // 整套的有效抗性：按护甲值加权平均（没穿甲的部位不参与）
        float armorSumAll = 0.0F;
        float resistWeighted = 0.0F;
        for (int i = 0; i < slots.length; i++) {
            if (armorPerSlot[i] <= 0.0F) {
                continue;
            }
            armorSumAll += armorPerSlot[i];
            resistWeighted += resistPerSlot[i] * armorPerSlot[i];
        }
        float setResistance = armorSumAll > 0.0F ? resistWeighted / armorSumAll : 0.0F;

        // 穿甲：50% × (1 - 抗性)，再减韧性抵扣
        float penetration = ArmorResistance.penetration(setResistance, armorToughness);
        // 护甲减免：每点有效护甲 5%，然后整体按比例被穿甲削掉一部分
        float reduction = Math.max(0.0F, effectiveArmor * 0.05F) * (1.0F - penetration);
        float removed = Math.min(damageAmount, damageAmount * reduction);

        if (removed > 0) {
            float armorSum = 0.0F;
            for (float pieceArmor : armorPerSlot) {
                armorSum += pieceArmor;
            }
            if (armorSum > 0.0F) {
                for (int i = 0; i < slots.length; i++) {
                    if (armorPerSlot[i] <= 0.0F) {
                        continue;
                    }
                    // 分到这件头上的损耗，再乘该件自己的抗性系数
                    float share = removed * armorPerSlot[i] / armorSum;
                    int loss = Math.round(share * ArmorResistance.wearFactor(resistPerSlot[i]));
                    if (loss > 0) {
                        target.getItemBySlot(slots[i]).hurtAndBreak(loss, target, living -> {
                        });
                    }
                }
            }
        }
        return damageAmount - removed;
    }

    /** 该槽位的护甲值（ArmorValueEvents 已经把 TFC 原值换成了我们表里的值） */
    private static float armorOf(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) {
            return 0.0F;
        }
        return (float) stack.getAttributeModifiers(slot).get(Attributes.ARMOR).stream()
                .filter(modifier -> modifier.getOperation() == AttributeModifier.Operation.ADDITION)
                .mapToDouble(AttributeModifier::getAmount)
                .sum();
    }
}
