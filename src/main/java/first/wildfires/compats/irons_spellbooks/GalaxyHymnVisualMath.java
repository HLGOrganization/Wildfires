package first.wildfires.compats.irons_spellbooks;

/*
 * Constants shared with the Galaxy Hymn impact. The source-style first-stage Starlink shell,
 * the tracking start and the untargeted expiry follow ArcaneVortex 0.6.8 SkyRipperArrow under
 * the user's project-specific visual authorization; the sixty-four-star volley, its stop-radius
 * band and the burst travel ticks are Wildfires-owned values.
 * Copyright ErChien. No upstream attack-damage mechanism is included.
 * Evidence: third_party/arcanevortex/0.6.8/PROVENANCE.md
 */
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** Pure common-side math shared with the Galaxy Hymn shader's visual contracts. */
public final class GalaxyHymnVisualMath {

    public static final int HOMING_STAR_COUNT = 64;
    public static final int VOLLEY_COUNT = 1;
    public static final int VOLLEY_SIZE = HOMING_STAR_COUNT;
    public static final int VOLLEY_RELEASE_TICK = 0;
    public static final int TRACKING_START_TICK = 40;
    public static final int UNTARGETED_EXPIRY_TICK = 120;
    public static final int BURST_TRAVEL_TICKS = 36;
    public static final double STAR_STOP_MIN_RADIUS = 5.5D;
    public static final double STAR_STOP_MAX_RADIUS = 12.0D;

    private GalaxyHymnVisualMath() {
    }

    /** Deterministic stop point for one of the sixty-four cross-shaped stars emitted at impact. */
    public static Vec3 homingReleaseOffset(int seed, int index) {
        if (index < 0 || index >= HOMING_STAR_COUNT) {
            throw new IllegalArgumentException("Homing star index must be in [0, " + HOMING_STAR_COUNT + ")");
        }
        Random random = new Random(seed);
        Vec3 offset = Vec3.ZERO;
        for (int current = 0; current <= index; current++) {
            double radius = STAR_STOP_MIN_RADIUS
                    + random.nextDouble() * (STAR_STOP_MAX_RADIUS - STAR_STOP_MIN_RADIUS);
            double theta = random.nextDouble() * Math.PI * 2.0D;
            double phi = Math.acos(2.0D * random.nextDouble() - 1.0D);
            offset = new Vec3(radius * Math.sin(phi) * Math.cos(theta),
                    radius * Math.cos(phi), radius * Math.sin(phi) * Math.sin(theta));
        }
        return offset;
    }

    /** Cubic ease-out shared by the server position and the client trail/rotation presentation. */
    public static double burstTravelFraction(float age) {
        double u = Math.max(0.0D, Math.min(1.0D, age / BURST_TRAVEL_TICKS));
        double remaining = 1.0D - u;
        return 1.0D - remaining * remaining * remaining;
    }

    /** The derivative reaches exactly zero at the hover point. */
    public static double burstSpeedFraction(float age) {
        if (age < 0.0F || age >= BURST_TRAVEL_TICKS) {
            return 0.0D;
        }
        double remaining = 1.0D - age / BURST_TRAVEL_TICKS;
        return 3.0D * remaining * remaining / BURST_TRAVEL_TICKS;
    }

    /**
     * Local entity age at which every impact-frame star becomes eligible to acquire a target.
     */
    public static int trackingStartAge(int seed, int index) {
        if (index < 0 || index >= HOMING_STAR_COUNT) {
            throw new IllegalArgumentException("Homing star index must be in [0, " + HOMING_STAR_COUNT + ")");
        }
        return TRACKING_START_TICK;
    }

    /** All sixty-four stars belong to one impact-frame volley. */
    public static int volleyIndex(int starIndex) {
        if (starIndex < 0 || starIndex >= HOMING_STAR_COUNT) {
            throw new IllegalArgumentException("Homing star index must be in [0, " + HOMING_STAR_COUNT + ")");
        }
        return 0;
    }

    public static int volleyAge(int volleyIndex) {
        if (volleyIndex < 0 || volleyIndex >= VOLLEY_COUNT) {
            throw new IllegalArgumentException("Volley index must be in [0, " + VOLLEY_COUNT + ")");
        }
        return VOLLEY_RELEASE_TICK;
    }

    public static int volleySize(int volleyIndex) {
        if (volleyIndex < 0 || volleyIndex >= VOLLEY_COUNT) {
            throw new IllegalArgumentException("Volley index must be in [0, " + VOLLEY_COUNT + ")");
        }
        return VOLLEY_SIZE;
    }

    /** Monotonic distance weight: every in-range candidate remains possible, nearer ones are likelier. */
    public static double proximityTargetWeight(double distanceSquared) {
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0D) {
            throw new IllegalArgumentException("Target distance squared must be finite and non-negative");
        }
        return 1.0D / (1.0D + Math.sqrt(distanceSquared));
    }

    /** Untargeted stars remain available from the second-second gate through the sixth second. */
    public static boolean shouldExpireUntargeted(int age, boolean hasTarget) {
        return !hasTarget && age >= UNTARGETED_EXPIRY_TICK;
    }

    /** Total burst rotation also follows ease-out, so angular velocity reaches zero at the stop point. */
    public static float burstRotationDegrees(int seed, int index, float age) {
        int mixed = mix(seed ^ index * 0x45d9f3b);
        float turns = 2.25F + Math.floorMod(mixed, 176) / 100.0F;
        float direction = (mixed & 1) == 0 ? 1.0F : -1.0F;
        return direction * turns * 360.0F * (float) burstTravelFraction(age);
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        value *= 0x846ca68b;
        return value ^ value >>> 16;
    }

}
