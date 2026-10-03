package first.wildfires.mixin.minecraft;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import first.wildfires.compat.sacombat.ClothingArmorOverrides;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.UUID;

/**
 * Rewrites the protection of the Leon S. Kennedy jacket.
 *
 * <h2>The problem</h2>
 *
 * <p>Survivors Arsenal builds that jacket as {@code new GeckoClothingStorageItem(ArmorMaterials.DIAMOND,
 * Type.CHESTPLATE, ...)}, so {@code ArmorItem}'s constructor copies the diamond chestplate's stats and
 * bakes them into an attribute map: <b>8 armour</b> and <b>2.0 toughness</b>. In this modpack that is
 * too strong for a piece of clothing that can be worn alongside real armour. Nothing exposes those
 * numbers to configuration - the mod's item list is {@code item_id|area|slots|tint} - and they are
 * computed inside a constructor, so they are corrected at the point they are handed out instead.
 *
 * <h2>Why the target is ArmorItem and not the jacket's own class</h2>
 *
 * <p>{@code ClothingStorageItem} does not override {@code getDefaultAttributeModifiers}; its Curios
 * method {@code getAttributeModifiers} calls into {@code ArmorItem}'s implementation directly with
 * {@code invokespecial} and copies the result out. Injecting into the clothing class would therefore
 * find nothing to modify. Targeting {@code ArmorItem} catches every path at once:
 *
 * <ul>
 *   <li>worn as armour,</li>
 *   <li>worn through Curios,</li>
 *   <li>the item tooltip, which is built from the same map.</li>
 * </ul>
 *
 * <p>Because the mixin is on vanilla's class, every armour item passes through it, so the jacket is
 * identified by registry name and everything else is returned untouched. The check holds no reference
 * to any Survivors Arsenal field, so it survives that mod reshuffling its registry class.
 *
 * <h2>Why all three methods</h2>
 *
 * <p>The attribute map is what actually applies the protection, but {@code getDefense()} and
 * {@code getToughness()} report the same stats separately and are read by other mods and by screens
 * that print them directly. All three are overridden together so the numbers can never disagree.
 */
@Mixin(ArmorItem.class)
public abstract class LeonJacketArmorMixin {

    /** True when this armour item is the jacket we are changing. */
    private static boolean wildfires$isTargetJacket(Object item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey((Item) item);
        return ClothingArmorOverrides.LEON_JACKET.equals(id);
    }

    /**
     * Rebuilds the attribute map with the corrected armour and no toughness.
     *
     * <p>Built from scratch rather than patched because toughness has to disappear entirely: leaving an
     * {@code AttributeModifier} of {@code +0} in place would still produce a toughness line in the
     * tooltip.
     */
    @ModifyReturnValue(method = "getDefaultAttributeModifiers", at = @At("RETURN"))
    private Multimap<Attribute, AttributeModifier> wildfires$overrideJacketArmor(
            Multimap<Attribute, AttributeModifier> original, EquipmentSlot slot) {
        if (!wildfires$isTargetJacket(this)) {
            return original;
        }

        // The armour modifier's UUID is fixed per equipment slot by the original constructor. Reusing
        // it keeps the entry recognisable as this item's armour bonus and stable across saves, rather
        // than inventing a new identity for a stat that already existed.
        UUID armorId = null;
        for (Map.Entry<Attribute, AttributeModifier> entry : original.entries()) {
            if (entry.getKey() == Attributes.ARMOR) {
                armorId = entry.getValue().getId();
                break;
            }
        }

        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        if (armorId != null) {
            builder.put(Attributes.ARMOR, new AttributeModifier(
                    armorId,
                    "Armor modifier",
                    ClothingArmorOverrides.JACKET_ARMOR,
                    AttributeModifier.Operation.ADDITION));
        }
        // Toughness is intentionally absent; see the class note.
        return builder.build();
    }

    /** Keeps the reported armour points in step with the map above. */
    @ModifyReturnValue(method = "getDefense", at = @At("RETURN"))
    private int wildfires$overrideJacketDefense(int original) {
        return wildfires$isTargetJacket(this)
                ? (int) ClothingArmorOverrides.JACKET_ARMOR
                : original;
    }

    /** Keeps the reported toughness in step with the map above, which no longer carries any. */
    @ModifyReturnValue(method = "getToughness", at = @At("RETURN"))
    private float wildfires$overrideJacketToughness(float original) {
        return wildfires$isTargetJacket(this)
                ? (float) ClothingArmorOverrides.JACKET_TOUGHNESS
                : original;
    }
}
