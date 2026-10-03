package first.wildfires.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import first.wildfires.dumbbell.Dumbbells;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A training weight that doubles as a slow, heavy melee weapon and can be charged up and thrown.
 *
 * <p>Only the main hand attack damage is declared here, because every item in the
 * {@code wildfires:dumbbells} tag brings its own damage. The rest of the dumbbell style - the 0.5 attack
 * speed, the three second right click charge, the thrown projectile and the crushing damage typing - is
 * applied by {@link Dumbbells} from the tag, so a pack can give any item the same handling by tagging it.</p>
 *
 * <p>The damage passed to the constructor is the total the player ends up with while holding it, bare hand
 * baseline included, exactly like a vanilla sword material.</p>
 */
public class DumbbellItem extends Item {

    /** Total main hand attack damage this item reaches on a player, bare hand baseline included. */
    private final float attackDamage;

    private final Multimap<Attribute, AttributeModifier> mainHandModifiers;

    public DumbbellItem(Item.Properties properties, float attackDamage) {
        super(properties);
        this.attackDamage = attackDamage;
        this.mainHandModifiers = ImmutableMultimap.<Attribute, AttributeModifier>builder()
                .put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                        BASE_ATTACK_DAMAGE_UUID,
                        "Weapon modifier",
                        attackDamage - Dumbbells.PLAYER_BASE_ATTACK_DAMAGE,
                        AttributeModifier.Operation.ADDITION
                ))
                .build();
    }

    /** Total main hand damage of this weight, used by the tooltip and by the thrown impact. */
    public float getAttackDamage() {
        return this.attackDamage;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? this.mainHandModifiers : super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        String key = this.getDescriptionId();
        tooltip.add(Component.translatable(key + ".tooltip.weight").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(key + ".tooltip.exp").withStyle(ChatFormatting.GRAY));
    }

}
