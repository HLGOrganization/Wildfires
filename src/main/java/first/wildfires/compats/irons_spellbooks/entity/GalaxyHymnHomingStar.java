package first.wildfires.compats.irons_spellbooks.entity;

/*
 * Adapted from ArcaneVortex 0.6.8 SkyRipperArrow under the user's project-specific visual
 * authorization. Copyright ErChien.
 * Wildfires additions: the impact-frame burst, silent hover, delayed tracking start,
 * seed-distributed stop points, distance-weighted target sampling, untargeted expiry and the
 * independent 20 damage are Wildfires-owned. No upstream attack-damage mechanism is included.
 * Evidence: third_party/arcanevortex/0.6.8/PROVENANCE.md
 */
import first.wildfires.compats.irons_spellbooks.GalaxyHymnRegister;
import first.wildfires.compats.irons_spellbooks.GalaxyHymnStarlinkSpawner;
import first.wildfires.compats.irons_spellbooks.GalaxyHymnVisualMath;
import first.wildfires.compats.irons_spellbooks.spell.GalaxyHymnSpell;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/** A real impact-frame star: eased burst, silent hover, then delayed curved homing. */
public final class GalaxyHymnHomingStar extends Projectile {

    public enum VisualPhase {
        BURST_OUT,
        HOVER,
        HOMING
    }

    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(
            GalaxyHymnHomingStar.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> INDEX = SynchedEntityData.defineId(
            GalaxyHymnHomingStar.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TRACKING_START_AGE = SynchedEntityData.defineId(
            GalaxyHymnHomingStar.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(
            GalaxyHymnHomingStar.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> BURST_X = SynchedEntityData.defineId(
            GalaxyHymnHomingStar.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BURST_Y = SynchedEntityData.defineId(
            GalaxyHymnHomingStar.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BURST_Z = SynchedEntityData.defineId(
            GalaxyHymnHomingStar.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> STOP_X = SynchedEntityData.defineId(
            GalaxyHymnHomingStar.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> STOP_Y = SynchedEntityData.defineId(
            GalaxyHymnHomingStar.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> STOP_Z = SynchedEntityData.defineId(
            GalaxyHymnHomingStar.class, EntityDataSerializers.FLOAT);

    public static final int MAX_LIFETIME = 210;
    public static final int CLIENT_TRAIL_TICKS = 16;
    private static final double MAX_SPEED = 1.65D;
    private static final double MIN_HOMING_SPEED = 0.34D;
    private static final double HOMING_ACCELERATION = 0.085D;
    private static final double DIRECT_INTERCEPT_DISTANCE = 3.0D;

    @Nullable
    private UUID targetUuid;
    private final Deque<TrailPoint> clientTrail = new ArrayDeque<>();

    public GalaxyHymnHomingStar(EntityType<? extends GalaxyHymnHomingStar> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(SEED, 0);
        entityData.define(INDEX, 0);
        entityData.define(TRACKING_START_AGE, GalaxyHymnVisualMath.TRACKING_START_TICK);
        entityData.define(TARGET_ID, -1);
        entityData.define(BURST_X, 0.0F);
        entityData.define(BURST_Y, 0.0F);
        entityData.define(BURST_Z, 0.0F);
        entityData.define(STOP_X, 0.0F);
        entityData.define(STOP_Y, 0.0F);
        entityData.define(STOP_Z, 0.0F);
    }

    public int getSeed() {
        return entityData.get(SEED);
    }

    public void setSeed(int seed) {
        entityData.set(SEED, seed);
    }

    public int getStarIndex() {
        return entityData.get(INDEX);
    }

    public int getTrackingStartAge() {
        return entityData.get(TRACKING_START_AGE);
    }

    public void setBurst(Vec3 center, Vec3 stopOffset, int index) {
        entityData.set(INDEX, index);
        entityData.set(TRACKING_START_AGE, GalaxyHymnVisualMath.trackingStartAge(getSeed(), index));
        entityData.set(BURST_X, (float) center.x);
        entityData.set(BURST_Y, (float) center.y);
        entityData.set(BURST_Z, (float) center.z);
        entityData.set(STOP_X, (float) stopOffset.x);
        entityData.set(STOP_Y, (float) stopOffset.y);
        entityData.set(STOP_Z, (float) stopOffset.z);
    }

    public Vec3 getBurstCenter() {
        return new Vec3(entityData.get(BURST_X), entityData.get(BURST_Y), entityData.get(BURST_Z));
    }

    public Vec3 getStopOffset() {
        return new Vec3(entityData.get(STOP_X), entityData.get(STOP_Y), entityData.get(STOP_Z));
    }

    public VisualPhase getVisualPhase(float age) {
        if (age < GalaxyHymnVisualMath.BURST_TRAVEL_TICKS) {
            return VisualPhase.BURST_OUT;
        }
        return age < getTrackingStartAge() || entityData.get(TARGET_ID) < 0
                ? VisualPhase.HOVER : VisualPhase.HOMING;
    }

    public float getBurstRotation(float partialTick) {
        return GalaxyHymnVisualMath.burstRotationDegrees(getSeed(), getStarIndex(), tickCount + partialTick);
    }

    public List<Vec3> getClientTrailPositions() {
        List<Vec3> result = new ArrayList<>(clientTrail.size());
        for (TrailPoint point : clientTrail) {
            result.add(point.position());
        }
        return List.copyOf(result);
    }

    public void setTarget(LivingEntity target) {
        targetUuid = target.getUUID();
        entityData.set(TARGET_ID, target.getId());
    }

    @Nullable
    public LivingEntity getTarget() {
        Entity target = level().getEntity(entityData.get(TARGET_ID));
        if (target instanceof LivingEntity living && living.isAlive()) {
            return living;
        }
        if (targetUuid != null && level() instanceof ServerLevel serverLevel) {
            Entity byUuid = serverLevel.getEntity(targetUuid);
            if (byUuid instanceof LivingEntity living && living.isAlive()) {
                entityData.set(TARGET_ID, living.getId());
                return living;
            }
        }
        clearTarget();
        return null;
    }

    private void clearTarget() {
        targetUuid = null;
        entityData.set(TARGET_ID, -1);
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return getVisualPhase(tickCount) == VisualPhase.HOMING
                && target instanceof LivingEntity && target != getOwner()
                && (getOwner() == null || !DamageSources.isFriendlyFireBetween(getOwner(), target))
                && super.canHitEntity(target);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 currentPosition = position();
        if (tickCount < getTrackingStartAge()) {
            VisualPhase phase = getVisualPhase(tickCount);
            Vec3 nextPosition = burstPosition(tickCount);
            setDeltaMovement(nextPosition.subtract(currentPosition));
            if (!level().isClientSide && shatterOnBlockImpact()) {
                return;
            }
            setPos(nextPosition);
            if (level().isClientSide) {
                updateClientVisuals(currentPosition, nextPosition, phase);
            }
            ProjectileUtil.rotateTowardsMovement(this, 1.0F);
            return;
        }

        if (!level().isClientSide) {
            LivingEntity target = getTarget();
            if (!isValidTrackingTarget(target)) {
                clearTarget();
                target = reacquireTarget();
            }
            if (target == null) {
                setDeltaMovement(Vec3.ZERO);
                if (GalaxyHymnVisualMath.shouldExpireUntargeted(tickCount, false)) {
                    spawnSparkBurst();
                    discard();
                    return;
                }
            } else {
                steerTowardsCollisionBox(target);

                HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
                if (hit.getType() != HitResult.Type.MISS && !ForgeEventFactory.onProjectileImpact(this, hit)) {
                    setPos(hit.getLocation());
                    if (hit instanceof EntityHitResult entityHit) {
                        DamageSources.applyDamage(entityHit.getEntity(), GalaxyHymnSpell.HOMING_STAR_DAMAGE,
                                GalaxyHymnRegister.GALAXY_HYMN.get().getDamageSource(
                                        this, getOwner() == null ? this : getOwner()));
                    }
                    spawnSparkBurst();
                    discard();
                    return;
                }
            }
            if (tickCount >= MAX_LIFETIME) {
                spawnSparkBurst();
                discard();
                return;
            }
        }

        VisualPhase phase = getVisualPhase(tickCount);
        Vec3 nextPosition = phase == VisualPhase.HOMING
                ? currentPosition.add(getDeltaMovement()) : currentPosition;
        if (phase == VisualPhase.HOVER) {
            setDeltaMovement(Vec3.ZERO);
        }
        setPos(nextPosition);
        if (level().isClientSide) {
            updateClientVisuals(currentPosition, nextPosition, phase);
        }
        ProjectileUtil.rotateTowardsMovement(this, 1.0F);
    }

    private Vec3 burstPosition(float age) {
        return getBurstCenter().add(getStopOffset().scale(GalaxyHymnVisualMath.burstTravelFraction(age)));
    }

    /** Burst-out stars are still real projectiles and must not pass through terrain. */
    private boolean shatterOnBlockImpact() {
        if (getDeltaMovement().lengthSqr() < 1.0E-8D) {
            return false;
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.BLOCK || ForgeEventFactory.onProjectileImpact(this, hit)) {
            return false;
        }
        setPos(hit.getLocation());
        spawnSparkBurst();
        discard();
        return true;
    }

    private void updateClientVisuals(Vec3 from, Vec3 to, VisualPhase phase) {
        while (!clientTrail.isEmpty() && clientTrail.peekFirst().age() < tickCount - CLIENT_TRAIL_TICKS) {
            clientTrail.removeFirst();
        }
        if (phase != VisualPhase.HOVER && from.distanceToSqr(to) > 1.0E-5D) {
            if (clientTrail.isEmpty() || clientTrail.peekLast().position().distanceToSqr(from) > 1.0E-5D) {
                clientTrail.addLast(new TrailPoint(from, tickCount));
            }
            clientTrail.addLast(new TrailPoint(to, tickCount));
        }
        Vec3 previousVisualPosition = from;
        Vec3 visualPosition = to;
        if (phase == VisualPhase.HOVER) {
            previousVisualPosition = from.add(hoverNoise(tickCount - 1));
            visualPosition = to.add(hoverNoise(tickCount));
        }
        Vec3 visualSegment = visualPosition.subtract(previousVisualPosition);
        level().addParticle(GalaxyHymnRegister.GALAXY_HYMN_GPU_STAR.get(),
                visualPosition.x, visualPosition.y, visualPosition.z,
                visualSegment.x, visualSegment.y, visualSegment.z);
        if (phase == VisualPhase.HOMING) {
            spawnHomingSparkTrail(from, to);
        }
        if (phase == VisualPhase.HOVER && tickCount % 4 == Math.floorMod(getSeed(), 4)) {
            double x = visualPosition.x + (random.nextDouble() - 0.5D) * 0.55D;
            double y = visualPosition.y + (random.nextDouble() - 0.5D) * 0.55D;
            double z = visualPosition.z + (random.nextDouble() - 0.5D) * 0.55D;
            // Hover shedding deliberately uses the exact same GPU-batched blue mote as impacts.
            level().addParticle(GalaxyHymnRegister.GALAXY_HYMN_SPARK.get(), x, y, z,
                    (random.nextDouble() - 0.5D) * 0.025D,
                    (random.nextDouble() - 0.5D) * 0.025D,
                    (random.nextDouble() - 0.5D) * 0.025D);
        }
    }

    private Vec3 hoverNoise(int visualTick) {
        double seedPhase = Math.floorMod(getSeed(), 8192) * (Math.PI * 2.0D / 8192.0D);
        return new Vec3(
                Math.sin(visualTick * 0.31D + seedPhase) * 0.055D,
                Math.sin(visualTick * 0.23D + seedPhase * 1.7D) * 0.048D,
                Math.cos(visualTick * 0.27D + seedPhase * 0.73D) * 0.055D);
    }

    /** Dense shrinking impact motes sampled strictly along this tick's real homing segment. */
    private void spawnHomingSparkTrail(Vec3 from, Vec3 to) {
        Vec3 segment = to.subtract(from);
        int samples = Mth.clamp((int) Math.ceil(segment.length() * 4.0D), 4, 8);
        for (int sample = 0; sample < samples; sample++) {
            double fraction = (sample + 1.0D) / samples;
            Vec3 position = from.add(segment.scale(fraction));
            level().addParticle(GalaxyHymnRegister.GALAXY_HYMN_SPARK.get(),
                    position.x, position.y, position.z, 0.0D, 0.0D, 0.0D);
        }
    }

    @Nullable
    private LivingEntity reacquireTarget() {
        Entity owner = getOwner();
        AABB area = getBoundingBox().inflate(GalaxyHymnSpell.TARGET_RANGE);
        List<LivingEntity> candidates = level().getEntitiesOfClass(LivingEntity.class, area,
                        candidate -> candidate.isAlive()
                                && distanceToSqr(candidate) <= GalaxyHymnSpell.TARGET_RANGE * GalaxyHymnSpell.TARGET_RANGE
                                && candidate != owner
                                && (owner == null || !DamageSources.isFriendlyFireBetween(owner, candidate)));
        if (candidates.isEmpty()) {
            return null;
        }
        LivingEntity selected = null;
        double totalWeight = 0.0D;
        for (LivingEntity candidate : candidates) {
            double weight = GalaxyHymnVisualMath.proximityTargetWeight(distanceToSqr(candidate));
            totalWeight += weight;
            if (random.nextDouble() * totalWeight < weight) {
                selected = candidate;
            }
        }
        if (selected != null) {
            setTarget(selected);
        }
        return selected;
    }

    private boolean isValidTrackingTarget(@Nullable LivingEntity target) {
        Entity owner = getOwner();
        return target != null && target.isAlive()
                && distanceToSqr(target) <= GalaxyHymnSpell.TARGET_RANGE * GalaxyHymnSpell.TARGET_RANGE
                && target != owner
                && (owner == null || !DamageSources.isFriendlyFireBetween(owner, target));
    }

    private void steerTowardsCollisionBox(LivingEntity target) {
        Vec3 boxCenter = target.getBoundingBox().getCenter();
        Vec3 direct = boxCenter.subtract(position());
        double distance = direct.length();
        if (distance < 1.0E-4D) {
            return;
        }

        double leadTicks = Math.min(3.0D, distance / Math.max(0.65D, MAX_SPEED));
        double leadScale = distance <= DIRECT_INTERCEPT_DISTANCE ? 0.0D : leadTicks * 0.35D;
        Vec3 aimPoint = boxCenter.add(target.getDeltaMovement().scale(leadScale));
        Vec3 toAim = aimPoint.subtract(position());
        Vec3 desiredDirection = toAim.normalize();
        if (distance > DIRECT_INTERCEPT_DISTANCE) {
            Vec3 referenceAxis = (getSeed() & 1) == 0
                    ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
            Vec3 curveAxis = desiredDirection.cross(referenceAxis);
            if (curveAxis.lengthSqr() < 1.0E-6D) {
                curveAxis = desiredDirection.cross(new Vec3(0.0D, 0.0D, 1.0D));
            }
            double approachEnvelope = Math.min(1.0D,
                    (distance - DIRECT_INTERCEPT_DISTANCE) / 9.0D);
            double trackingAge = Math.max(0.0D, tickCount - getTrackingStartAge());
            double seedPhase = Math.floorMod(getSeed(), 4096) * (Math.PI * 2.0D / 4096.0D);
            double arcWave = 0.72D + 0.28D * Math.sin(seedPhase + trackingAge * 0.11D);
            double curveStrength = Math.min(0.72D, 0.24D + distance * 0.032D)
                    * approachEnvelope * arcWave;
            if ((getSeed() & 2) != 0) {
                curveStrength = -curveStrength;
            }
            Vec3 curvedAim = aimPoint.add(curveAxis.normalize().scale(curveStrength));
            desiredDirection = curvedAim.subtract(position()).normalize();
        }

        Vec3 current = getDeltaMovement();
        Vec3 currentDirection = current.lengthSqr() > 1.0E-6D
                ? current.normalize() : desiredDirection;
        double turnResponse = distance <= DIRECT_INTERCEPT_DISTANCE ? 0.42D
                : 0.11D + 0.10D * Math.max(0.0D,
                1.0D - distance / GalaxyHymnSpell.TARGET_RANGE);
        Vec3 nextDirection = currentDirection.scale(1.0D - turnResponse)
                .add(desiredDirection.scale(turnResponse));
        if (nextDirection.lengthSqr() < 1.0E-6D) {
            nextDirection = desiredDirection;
        }
        double speed = Math.min(MAX_SPEED,
                Math.max(MIN_HOMING_SPEED, current.length() + HOMING_ACCELERATION));
        setDeltaMovement(nextDirection.normalize().scale(speed));
    }

    private void spawnSparkBurst() {
        if (level() instanceof ServerLevel serverLevel) {
            GalaxyHymnStarlinkSpawner.sendHitSparks(serverLevel, position(), getSeed());
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Seed", getSeed());
        tag.putInt("StarIndex", getStarIndex());
        tag.putInt("TrackingStartAge", getTrackingStartAge());
        Vec3 center = getBurstCenter();
        Vec3 stop = getStopOffset();
        tag.putDouble("BurstX", center.x);
        tag.putDouble("BurstY", center.y);
        tag.putDouble("BurstZ", center.z);
        tag.putDouble("StopX", stop.x);
        tag.putDouble("StopY", stop.y);
        tag.putDouble("StopZ", stop.z);
        if (targetUuid != null) {
            tag.putUUID("Target", targetUuid);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSeed(tag.getInt("Seed"));
        setBurst(new Vec3(tag.getDouble("BurstX"), tag.getDouble("BurstY"), tag.getDouble("BurstZ")),
                new Vec3(tag.getDouble("StopX"), tag.getDouble("StopY"), tag.getDouble("StopZ")),
                tag.getInt("StarIndex"));
        if (tag.contains("TrackingStartAge")) {
            entityData.set(TRACKING_START_AGE, tag.getInt("TrackingStartAge"));
        }
        targetUuid = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    private record TrailPoint(Vec3 position, int age) {
    }
}
