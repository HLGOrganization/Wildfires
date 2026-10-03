package first.wildfires.dumbbell;

import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import first.wildfires.Wildfires;
import first.wildfires.WildfiresTags;
import first.wildfires.utils.WildfiresUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.Collection;

/**
 * Shared data and helpers for the throwable dumbbells.
 *
 * <p>The items themselves are registered by this mod ({@code wildfires:stone_dumbbell},
 * {@code wildfires:iron_dumbbell}); everything here is driven by the {@code wildfires:dumbbells} item tag, so a
 * pack can give any other item the exact same handling by tagging it.</p>
 *
 * <p>Being in {@code wildfires:dumbbells} <em>is</em> the dumbbell attack style: attack speed forced to
 * {@link #ATTACK_SPEED}, a three second right click charge, the thrown projectile and crushing damage typing.
 * The damage stays the item's own main hand attack damage, so every item keeps the value it declares; only items
 * that declare none fall back to {@link #DEFAULT_ATTACK_DAMAGE}.</p>
 */
public final class Dumbbells {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Never completes on its own, exactly like a bow. */
    public static final int USE_DURATION = 72000;

    /** Ticks of right-click charge for a full power throw. */
    public static final int FULL_CHARGE_TICKS = 60;

    /** Full charge throw speed in blocks per tick: one third of a fully charged arrow (3.0). */
    public static final float FULL_CHARGE_VELOCITY = 1.0F;

    /** A release without any charge still throws, just very weakly. */
    public static final float MIN_CHARGE_POWER = 0.1F;

    /** Thrown spread, tighter than a bow because a dumbbell is heavy. */
    public static final float THROW_INACCURACY = 0.5F;

    /** Block bounces keep this fraction of the incoming speed. */
    public static final float BOUNCE_FACTOR = 0.2F;

    /** Hitting a living entity slows the flight down to this fraction and it keeps flying. */
    public static final float ENTITY_HIT_FACTOR = 0.5F;

    /** Landing impact radius in blocks, measured horizontally from the impact point. */
    public static final double IMPACT_RADIUS = 1.0D;

    /** How far the landing impact reaches upwards, so a victim standing one block above is hit too. */
    public static final double IMPACT_HEIGHT = 2.0D;

    /** Impact damage per block/tick of landing speed. */
    public static final double IMPACT_SPEED_SCALE = 5.0D;
    public static final float IMPACT_MIN_DAMAGE = 1.0F;
    public static final float IMPACT_MAX_DAMAGE = 5.0F;

    /** Safety net so a projectile wedged in geometry can never live forever. */
    public static final int MAX_LIFETIME_TICKS = 600;

    /** Player base values, so the modifiers below land on the exact requested totals. */
    public static final float PLAYER_BASE_ATTACK_DAMAGE = 1.0F;
    private static final float PLAYER_BASE_ATTACK_SPEED = 4.0F;

    /** Main hand attack speed for every dumbbell. */
    public static final float ATTACK_SPEED = 0.5F;

    /** Fallback damage for a tagged item that declares no main hand attack damage of its own. */
    public static final float DEFAULT_ATTACK_DAMAGE = 5.0F;

    public static final ResourceKey<DamageType> IMPACT_DAMAGE_TYPE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            Wildfires.rl("dumbbell_impact")
    );

    private static boolean missingDamageTypeLogged;

    private Dumbbells() {
    }

    public static boolean isDumbbell(ItemStack stack) {
        return !stack.isEmpty() && stack.is(WildfiresTags.DUMBBELLS);
    }

    /** Charge power in 0..1, using the vanilla bow curve stretched over {@link #FULL_CHARGE_TICKS}. */
    public static float chargePower(int chargeTicks) {
        float f = Mth.clamp((float) chargeTicks / FULL_CHARGE_TICKS, 0.0F, 1.0F);
        return (f * f + 2.0F * f) / 3.0F;
    }

    /** Throw velocity in blocks/tick for the given charge, always strong enough to leave the hand. */
    public static float throwVelocity(int chargeTicks) {
        return Math.max(MIN_CHARGE_POWER, chargePower(chargeTicks)) * FULL_CHARGE_VELOCITY;
    }

    /** Degrees per tick of tumble per block/tick of throw speed. */
    private static final double SPIN_DEGREES_PER_SPEED = 40.0D;
    private static final int MIN_SPIN_RATE = 20;
    private static final int MAX_SPIN_RATE = 60;

    /** Tumble speed used when nothing else was set, matching a full power throw. */
    public static final int DEFAULT_SPIN_RATE = 40;

    /** How fast the thrown dumbbell tumbles: a hard throw spins visibly faster than a weak toss. */
    public static int spinRate(float velocity) {
        return Mth.clamp((int) Math.round(velocity * SPIN_DEGREES_PER_SPEED), MIN_SPIN_RATE, MAX_SPIN_RATE);
    }

    /** Main hand attack damage of a specific dumbbell stack, using the item's own damage when it declares any. */
    public static float attackDamage(ItemStack stack) {
        Item item = stack.getItem();
        return declaresOwnDamage(item) ? applyAttackDamageModifiers(
                item.getDefaultAttributeModifiers(EquipmentSlot.MAINHAND)) : DEFAULT_ATTACK_DAMAGE;
    }

    /** True when the item brings its own main hand attack damage instead of relying on the fallback. */
    private static boolean declaresOwnDamage(Item item) {
        return applyAttackDamageModifiers(item.getDefaultAttributeModifiers(EquipmentSlot.MAINHAND))
                > PLAYER_BASE_ATTACK_DAMAGE + 1.0E-4F;
    }

    /**
     * Reproduces the vanilla attribute order (flat, then percent of base, then percent of total) on the bare hand
     * baseline, which is what the player ends up with while holding the item.
     */
    private static float applyAttackDamageModifiers(Multimap<Attribute, AttributeModifier> modifiers) {
        Collection<AttributeModifier> damage = modifiers.get(Attributes.ATTACK_DAMAGE);
        double value = PLAYER_BASE_ATTACK_DAMAGE;
        for (AttributeModifier modifier : damage) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                value += modifier.getAmount();
            }
        }
        for (AttributeModifier modifier : damage) {
            if (modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_BASE) {
                value *= 1.0D + modifier.getAmount();
            }
        }
        for (AttributeModifier modifier : damage) {
            if (modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL) {
                value *= 1.0D + modifier.getAmount();
            }
        }
        return (float) value;
    }

    /** Adds the melee modifiers of a dumbbell to the main hand of the player holding it. */
    public static void addAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (!isDumbbell(stack)) {
            return;
        }

        // An item that declares its own damage already gets it from the vanilla modifiers, so only the attack
        // speed (the actual "dumbbell style") is enforced for it.
        float declared = applyAttackDamageModifiers(stack.getItem().getDefaultAttributeModifiers(EquipmentSlot.MAINHAND));
        float target = declaresOwnDamage(stack.getItem()) ? declared : DEFAULT_ATTACK_DAMAGE;
        float damageModifier = target - declared;
        if (damageModifier != 0.0F) {
            event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                    WildfiresUtil.getUUID("dumbbell_attack_damage"),
                    "dumbbell_attack_damage",
                    damageModifier,
                    AttributeModifier.Operation.ADDITION
            ));
        }
        event.addModifier(Attributes.ATTACK_SPEED, new AttributeModifier(
                WildfiresUtil.getUUID("dumbbell_attack_speed"),
                "dumbbell_attack_speed",
                ATTACK_SPEED - PLAYER_BASE_ATTACK_SPEED,
                AttributeModifier.Operation.ADDITION
        ));
    }

    /**
     * Blunt damage source for both the direct hit and the landing impact.
     *
     * <p>The type lives in {@code tfc:is_crushing}, which is what makes TFC treat the hit as crushing damage.
     * The thrown entity is the direct source and the thrower the causing entity, so player kill credit and the
     * {@code .player} death message both work.</p>
     */
    public static DamageSource impactSource(Level level, Entity direct, Entity owner) {
        try {
            return new DamageSource(
                    level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(IMPACT_DAMAGE_TYPE),
                    direct,
                    owner
            );
        } catch (RuntimeException exception) {
            if (!missingDamageTypeLogged) {
                missingDamageTypeLogged = true;
                LOGGER.error("Missing damage type {}, falling back to minecraft:thrown", IMPACT_DAMAGE_TYPE.location(), exception);
            }
            return level.damageSources().thrown(direct, owner);
        }
    }

    /** Fallback item used by the projectile when its synced stack is still empty. */
    public static Item defaultItem() {
        Item item = ForgeRegistries.ITEMS.getValue(Wildfires.rl("stone_dumbbell"));
        return item == null ? Items.STONE : item;
    }

}
