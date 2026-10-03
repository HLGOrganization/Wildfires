package first.wildfires.entity;

import com.mojang.logging.LogUtils;
import first.wildfires.dumbbell.Dumbbells;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.List;

/**
 * Thrown dumbbell: a blunt projectile that bounces off walls at a fifth of its speed, lands on the ground
 * and then deals a crushing impact to everything within one block, one block upwards included.
 */
public class DumbbellProjectile extends ThrowableItemProjectile {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Set as soon as the throw has landed so the impact can never run twice. */
    private boolean landed;

    /** Tumble speed in degrees per tick, synced so both sides render the same spin. */
    private static final EntityDataAccessor<Integer> DATA_SPIN_RATE =
            SynchedEntityData.defineId(DumbbellProjectile.class, EntityDataSerializers.INT);

    public DumbbellProjectile(EntityType<? extends DumbbellProjectile> type, Level level) {
        super(type, level);
    }

    public DumbbellProjectile(EntityType<? extends DumbbellProjectile> type, LivingEntity shooter, Level level) {
        super(type, shooter, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SPIN_RATE, Dumbbells.DEFAULT_SPIN_RATE);
    }

    /** Called right after the throw, before the entity is added to the level. */
    public void setSpinRate(int degreesPerTick) {
        this.entityData.set(DATA_SPIN_RATE, degreesPerTick);
    }

    /** Tumble speed in degrees per tick used by {@code DumbbellRenderer}. */
    public float getSpinRate() {
        return this.entityData.get(DATA_SPIN_RATE);
    }

    @Override
    protected Item getDefaultItem() {
        return Dumbbells.defaultItem();
    }

    /** The thrower can never be hit by their own dumbbell, not even after a bounce. */
    @Override
    protected boolean canHitEntity(Entity target) {
        return target != this.getOwner() && super.canHitEntity(target);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.landed || this.level().isClientSide()) {
            return;
        }
        if (this.tickCount > Dumbbells.MAX_LIFETIME_TICKS || this.getY() < this.level().getMinBuildHeight() - 8.0D) {
            this.impact(this.position());
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (this.landed) {
            return;
        }

        Entity target = hit.getEntity();
        Vec3 motion = this.getDeltaMovement();
        if (!this.level().isClientSide()) {
            DamageSource source = Dumbbells.impactSource(this.level(), this, this.getOwner());
            if (target.hurt(source, Dumbbells.attackDamage(this.getItem()))) {
                this.knockback(target, motion, 0.5D);
            }
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 0.9F);
        }

        // The dumbbell keeps flying, just slower.
        this.setDeltaMovement(motion.scale(Dumbbells.ENTITY_HIT_FACTOR));
        this.hasImpulse = true;
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (this.landed) {
            return;
        }

        Direction face = hit.getDirection();
        Vec3 motion = this.getDeltaMovement();
        Vec3 contact = hit.getLocation();
        if (!this.level().isClientSide()) {
            LOGGER.info("[dumbbell] block hit face={} at ({}, {}, {}) motion=({}, {}, {})",
                    face, contact.x, contact.y, contact.z, motion.x, motion.y, motion.z);
        }

        // A downward hit on the top face of a block is the ground: stop bouncing and detonate.
        if (face == Direction.UP && motion.y <= 0.0D) {
            // Blast from the collision point, not from the position the projectile still had this tick.
            this.impact(contact);
            return;
        }

        // Otherwise bounce: reflect on the hit normal and keep a fifth of the speed.
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 bounced = motion.subtract(normal.scale(2.0D * motion.dot(normal))).scale(Dumbbells.BOUNCE_FACTOR);
        this.setDeltaMovement(bounced);
        this.hasImpulse = true;
        // Nudge out of the block so the next tick does not start inside it.
        this.setPos(this.position().add(normal.scale(0.05D)));

        if (!this.level().isClientSide()) {
            BlockPos pos = hit.getBlockPos();
            float volume = (float) Mth.clamp(0.3D + motion.length() * 0.5D, 0.3D, 1.0D);
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, volume, 1.1F - this.random.nextFloat() * 0.2F);
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        pos.getX() + 0.5D + normal.x * 0.55D,
                        pos.getY() + 0.5D + normal.y * 0.55D,
                        pos.getZ() + 0.5D + normal.z * 0.55D,
                        3, 0.15D, 0.15D, 0.15D, 0.05D);
            }
        }
    }

    /**
     * Landing: area damage scaled with the remaining speed, then the dumbbell becomes a pickable drop.
     * {@code center} is the collision point, so the blast covers {@link Dumbbells#IMPACT_RADIUS} blocks
     * around it horizontally plus one block upwards ({@link Dumbbells#IMPACT_HEIGHT}).
     */
    private void impact(Vec3 center) {
        if (this.landed) {
            return;
        }
        this.landed = true;

        if (!this.level().isClientSide()) {
            double speed = this.getDeltaMovement().length();
            float damage = (float) Mth.clamp(speed * Dumbbells.IMPACT_SPEED_SCALE,
                    Dumbbells.IMPACT_MIN_DAMAGE, Dumbbells.IMPACT_MAX_DAMAGE);

            DamageSource source = Dumbbells.impactSource(this.level(), this, this.getOwner());
            AABB area = new AABB(
                    center.x - Dumbbells.IMPACT_RADIUS, center.y - Dumbbells.IMPACT_RADIUS, center.z - Dumbbells.IMPACT_RADIUS,
                    center.x + Dumbbells.IMPACT_RADIUS, center.y + Dumbbells.IMPACT_HEIGHT, center.z + Dumbbells.IMPACT_RADIUS);
            List<LivingEntity> victims = this.level().getEntitiesOfClass(LivingEntity.class, area,
                    victim -> victim != this.getOwner() && victim.isAlive() && !victim.isSpectator());
            for (LivingEntity victim : victims) {
                victim.hurt(source, damage);
                this.knockback(victim, null, 0.4D);
            }
            LOGGER.info("[dumbbell] impact at ({}, {}, {}) speed={} damage={} victims={}",
                    center.x, center.y, center.z, speed, damage, victims.size());

            this.impactEffects(speed);
            this.dropDumbbell();
        }

        this.setDeltaMovement(Vec3.ZERO);
        this.discard();
    }

    /** Pushes a victim away from the flight direction (direct hit) or from the impact point (area hit). */
    private void knockback(Entity victim, Vec3 flight, double strength) {
        Vec3 push = flight;
        if (push == null || push.lengthSqr() < 1.0E-6D) {
            push = victim.position().subtract(this.position());
        }
        if (push.lengthSqr() < 1.0E-6D) {
            push = new Vec3(0.0D, 1.0D, 0.0D);
        }
        Vec3 direction = push.normalize().scale(strength);
        victim.push(direction.x, 0.25D, direction.z);
        victim.hurtMarked = true;
    }

    private void impactEffects(double speed) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        float volume = (float) Mth.clamp(0.4D + speed * 0.4D, 0.4D, 1.2D);
        serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, volume, 0.8F + this.random.nextFloat() * 0.2F);
        int count = 6 + (int) Math.round(Math.min(speed, 3.0D) * 4.0D);
        serverLevel.sendParticles(ParticleTypes.CRIT,
                this.getX(), this.getY() + 0.1D, this.getZ(),
                count, 0.35D, 0.15D, 0.35D, 0.15D);
        serverLevel.sendParticles(ParticleTypes.SMOKE,
                this.getX(), this.getY() + 0.05D, this.getZ(),
                5, 0.3D, 0.05D, 0.3D, 0.01D);
    }

    /** The thrown dumbbell itself, dropped where it landed. */
    private void dropDumbbell() {
        ItemStack stack = this.getItem();
        if (stack.isEmpty()) {
            return;
        }
        ItemEntity drop = new ItemEntity(this.level(), this.getX(), this.getY() + 0.05D, this.getZ(), stack.copyWithCount(1));
        drop.setDefaultPickUpDelay();
        drop.setDeltaMovement(this.getDeltaMovement().scale(0.3D).add(0.0D, 0.1D, 0.0D));
        this.level().addFreshEntity(drop);
    }

}
