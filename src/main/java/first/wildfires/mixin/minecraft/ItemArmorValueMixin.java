package first.wildfires.mixin.minecraft;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import first.wildfires.utils.ArmorValues;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 把护甲值写进物品属性（老大 2026-10-18 定）。
 *
 * <p>挂在 {@code ArmorItem#getDefaultAttributeModifiers(EquipmentSlot)} 上 —— 2026-10-18 用日志验证过：
 * 这个方法确实会被调用（`Item` 上的同名方法反而不走盔甲），而且参数里带着槽位。
 *
 * <p>三个要点：
 * <ol>
 *   <li>只处理四个护甲槽，且<b>只认这件物品自己的槽位</b>（{@link ArmorValues#slotOf}）——
 *       否则胸甲会在脚/腿/头上也显示护甲值；</li>
 *   <li>移除原有 ARMOR 修正后换成 {@link ArmorValues} 表里的值（金属 9 种 + 皮甲死名单）；</li>
 *   <li>★ 每件物品用<b>自己的 UUID</b>（由物品 id 派生）—— 属性修饰符按 UUID 去重，
 *       共用同一个 UUID 会让后穿的那件顶掉先穿的，护甲值就不相加了。</li>
 * </ol>
 */
@Mixin(ArmorItem.class)
public class ItemArmorValueMixin {

    @Inject(method = "getDefaultAttributeModifiers", at = @At("RETURN"), cancellable = true, remap = true, require = 0)
    private void wildfires$replaceArmorValue(EquipmentSlot slot, CallbackInfoReturnable<Multimap<Attribute, AttributeModifier>> cir) {
        if (slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST
                && slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET) {
            return;
        }
        ArmorItem self = (ArmorItem) (Object) this;
        float armor = ArmorValues.armorOf(self);
        if (armor < 0.0F) {
            return;
        }
        if (ArmorValues.slotOf(self) != slot) {
            return;
        }
        ListMultimap<Attribute, AttributeModifier> replaced = ArrayListMultimap.create();
        for (Attribute attribute : cir.getReturnValue().keySet()) {
            if (attribute == Attributes.ARMOR) {
                continue;
            }
            replaced.putAll(attribute, cir.getReturnValue().get(attribute));
        }
        UUID itemUuid = UUID.nameUUIDFromBytes(
                ("wildfires_armor:" + BuiltInRegistries.ITEM.getKey(self)).getBytes(StandardCharsets.UTF_8));
        replaced.put(Attributes.ARMOR,
                new AttributeModifier(itemUuid, "wildfires_armor_value", armor, AttributeModifier.Operation.ADDITION));
        cir.setReturnValue(replaced);
    }
}
