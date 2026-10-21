package first.wildfires.mixin.minecraft;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
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
 * 护甲减免 + 穿甲 + 耐久阶段衰减（老大 2026-10-18 定）。
 *
 * <p><b>护甲值</b>：已由 {@code ArmorValueEvents} 写进物品属性（tooltip / 护甲条 / 伤害计算三处一致），
 * 这里直接读该槽位的 ARMOR 修正之和。
 *
 * <p><b>护甲减免</b>：每点有效护甲减 5%，线性、无上限（超过 100% 时最终伤害夹 0）。
 * 有效护甲 = 护甲值 × 该件耐久阶段系数（{@link ArmorWear}：剩 ≤50%/≤25%/≤10% ⇒ 0.85/0.70/0.55）。
 *
 * <p><b>穿甲（按比例，2026-10-18 修订）</b>：穿甲比例 = 20% − 韧性×1%（韧性 ≥20 则 0），
 * 然后<b>护甲减免整体 ×(1 − 穿甲比例)</b>。
 * 例：100 伤害、护甲减 70% ⇒ 实际 70%×0.8 = <b>56%</b> ⇒ 吃 44；皮甲 20% ⇒ 16% ⇒ 吃 84。
 * （旧口径是"减 20 个百分点"，那会让 4 点甲的皮甲直接归零，老大已改为按比例。）
 *
 * <p><b>磨损</b>：基数 = 护甲吸收的伤害，按各件护甲值比例分摊。
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
        float effectiveArmor = 0.0F;
        float factorSum = 0.0F;
        int worn = 0;
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = target.getItemBySlot(slots[i]);
            float pieceArmor = armorOf(stack, slots[i]);
            armorPerSlot[i] = pieceArmor;
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

        // 穿甲比例：20%，每点韧性抵 1 个百分点
        float penetration = Math.max(0.0F, 0.20F - armorToughness * 0.01F);
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
                    int loss = Math.round(removed * armorPerSlot[i] / armorSum);
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
