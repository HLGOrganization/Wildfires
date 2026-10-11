package first.wildfires.client.spawnegg;

/**
 * <h2>What this does</h2>
 * The two arithmetic decisions behind the spawn-egg preview, kept out of the mixin so they can be
 * checked in a plain JVM. The mixin draws; this decides how big and in which frame.
 *
 * <h2>Why it is separate</h2>
 * Both rules are pure functions of numbers a mob reports, and both had a real failure mode in the
 * standalone {@code entityspawnegg} mod they are ported from. Pulling them out means the boundaries -
 * where a clamp takes over, and where the icon branch stops applying the tilt - are pinned by a test
 * rather than by looking at a screenshot.
 */
public final class SpawnEggPreviewRules {

    /**
     * The size, in blocks, a mob's widest dimension is normalised against before the clamps apply.
     * Unchanged from the original: it is what makes a chicken and a ravager both fill their slot.
     */
    static final double TARGET_EXTENT = 0.45D;

    /**
     * The extra multiplier the inventory icon context applies over the held and dropped contexts.
     * Unchanged from the original.
     */
    static final float GUI_BOOST = 1.6F;

    private SpawnEggPreviewRules() {
    }

    /**
     * The scale that fits a mob in a slot: the larger of its width and height is normalised against
     * {@link #TARGET_EXTENT}, then clamped between {@code min} and {@code max}.
     *
     * <p>The original built a three-element vector {@code (width, height, width)} and took the maximum,
     * which is just the larger of the two distinct values. Only the two are passed here.
     *
     * <p>An extent that is zero or negative - a mob that reports no box at all - would divide to
     * infinity. The original let that through and relied on the {@code max} clamp catching it, which
     * works but throws away the ordering; it is rejected explicitly here so the clamp keeps meaning
     * what it says.
     *
     * @param width  the mob's bounding-box width, in blocks
     * @param height the mob's bounding-box height, in blocks
     * @param min    the smallest permitted scale
     * @param max    the largest permitted scale
     */
    public static float fitScale(double width, double height, float min, float max) {
        double extent = Math.max(width, height);
        if (!(extent > 0.0D) || !Double.isFinite(extent)) {
            // A mob with no measurable box cannot be fitted; the smallest scale is the honest answer,
            // and it keeps the result inside the range the caller asked for.
            return Math.max(min, Math.min(min, max));
        }
        float fitted = (float) (TARGET_EXTENT / extent);
        return Math.max(min, Math.min(fitted, max));
    }

    /**
     * The scale finally pushed onto the pose stack for a resolved context.
     *
     * @param fittedScale the value from {@link #fitScale}
     * @param inGui       true for the inventory icon context, false for held and dropped
     * @param iconRate    the icon-only multiplier
     * @param itemRate    the multiplier for every non-icon context
     */
    public static float contextScale(float fittedScale, boolean inGui, float iconRate, float itemRate) {
        // Only the icon branch has a boost. Reading both in one expression is what made the original's
        // two assignments easy to swap; they are separate branches here, and the test pins which one
        // the boost belongs to.
        return inGui
                ? fittedScale * GUI_BOOST * iconRate
                : fittedScale * itemRate;
    }

    /**
     * Whether the display context receives the tilt and the vertical lift.
     *
     * <p>Both belong to the inventory icon alone: the slot is drawn on a different origin from the
     * world, so an untilted mob reads as lying down. A held or dropped egg is already in world
     * orientation and must not be lifted, which is why this is a question and not a constant.
     */
    public static boolean usesIconFrame(boolean inGui) {
        return inGui;
    }
}
