package first.wildfires.client.spawnegg;

/**
 * Plain-Java checks for the two arithmetic rules behind the spawn-egg preview.
 *
 * <p>What is pinned here is what a screenshot cannot pin: where the fit clamp takes over, and which
 * of the two display contexts receives the icon boost. Both were single-expression decisions in the
 * standalone {@code entityspawnegg} mod this is ported from, and both are the kind of expression that
 * survives a rewrite while quietly changing meaning - a swapped pair of multipliers, or a clamp
 * applied in the other order, still renders a mob and still looks plausible.
 *
 * <p>The rules take numbers and return numbers, so this runs in any JVM. Whether a live mob reports
 * the right bounding box, and whether the dispatcher draws it, is wiring - and wiring is what the
 * in-game pass is for.
 */
public final class SpawnEggPreviewRulesSelfTest {

    // The shipped clamps, so a boundary case is checked against the values that will actually run.
    private static final float MIN = 0.1F;
    private static final float MAX = 0.38F;

    private SpawnEggPreviewRulesSelfTest() {
    }

    public static void main(String[] args) {
        anOrdinaryMobIsFittedToTheTargetExtent();
        aTinyMobIsClampedDownToTheMaximum();
        aHugeMobIsClampedUpToTheMinimum();
        theWiderOfTwoDimensionsDecidesTheFit();
        aMobWithNoMeasurableBoxDoesNotDivideByZero();
        theIconContextIsTheOnlyOneBoosted();
        theIconFrameIsTheOnlyOneTilted();
        System.out.println("SpawnEggPreviewRulesSelfTest: all checks passed");
    }

    /**
     * The untouched path: a mob whose widest dimension lands between the two clamps is normalised
     * against the target extent and passes through.
     *
     * <p>Note how narrow that band is with the shipped clamps. The target extent normalises a mob of
     * {@code 0.45 / 0.38 = 1.18} blocks and wider, so a plain 1-block-wide mob is already over the
     * maximum and gets clamped. The unclamped path therefore only covers mobs between about 1.18 and
     * 4.5 blocks wide, which is why the case below uses 2.0.
     */
    private static void anOrdinaryMobIsFittedToTheTargetExtent() {
        // 2 blocks wide fits at 0.225: below the maximum, above the minimum, so it is exact.
        assertClose(0.225F, SpawnEggPreviewRules.fitScale(2.0D, 1.0D, MIN, MAX),
                "a 2-block mob is fitted in proportion");
        // 1.5 wide fits at 0.30, still inside the band.
        assertClose(0.30F, SpawnEggPreviewRules.fitScale(1.5D, 1.0D, MIN, MAX),
                "a wider mob is scaled down in proportion");
    }

    /**
     * A mob smaller than the target would scale above the maximum, and is held there. This is what
     * stops a chicken from being blown up to fill the slot.
     */
    private static void aTinyMobIsClampedDownToTheMaximum() {
        // 0.2 blocks would fit at 2.25, far above the maximum.
        assertClose(MAX, SpawnEggPreviewRules.fitScale(0.2D, 0.2D, MIN, MAX),
                "a tiny mob is clamped to the maximum scale");
        // The boundary itself: exactly at the extent where the raw fit equals the maximum.
        assertClose(MAX, SpawnEggPreviewRules.fitScale(0.45D / MAX, 1.0D, MIN, MAX),
                "the boundary mob sits exactly on the maximum");
        // One step past it, the raw fit is below the maximum and must be exact again.
        float justPast = SpawnEggPreviewRules.fitScale(0.45F / MAX + 0.01F, 1.0D, MIN, MAX);
        assertTrue(justPast < MAX, "past the boundary the raw fit is exact, not clamped");
    }

    /**
     * A mob larger than the target would scale below the minimum, and is held there. This is what
     * stops a ravager from shrinking past recognition.
     */
    private static void aHugeMobIsClampedUpToTheMinimum() {
        // 10 blocks would fit at 0.045, far below the minimum.
        assertClose(MIN, SpawnEggPreviewRules.fitScale(10.0D, 10.0D, MIN, MAX),
                "a huge mob is clamped to the minimum scale");
        assertClose(MIN, SpawnEggPreviewRules.fitScale(0.45D / MIN + 1.0D, 1.0D, MIN, MAX),
                "the boundary mob sits exactly on the minimum");
    }

    /**
     * The original built {@code (width, height, width)} and took the maximum. Only the two distinct
     * values matter, and the larger must win in both directions.
     */
    private static void theWiderOfTwoDimensionsDecidesTheFit() {
        float tall = SpawnEggPreviewRules.fitScale(0.5D, 2.0D, MIN, MAX);
        float wide = SpawnEggPreviewRules.fitScale(2.0D, 0.5D, MIN, MAX);
        assertClose(tall, wide, "the fit depends on the larger dimension, not on which axis it is");
        assertClose(0.45F / 2.0F, tall, "and the larger dimension is the one normalised");
    }

    /**
     * A mob that reports no box cannot be fitted. The original divided by the extent and leaned on the
     * clamp to absorb the infinity; this pins that the result is still inside the requested range and
     * does not come back as NaN or infinity.
     */
    private static void aMobWithNoMeasurableBoxDoesNotDivideByZero() {
        for (double[] box : new double[][]{{0.0D, 0.0D}, {0.0D, 1.0D}, {-1.0D, -1.0D}, {1.0D, 0.0D}}) {
            float scale = SpawnEggPreviewRules.fitScale(box[0], box[1], MIN, MAX);
            assertTrue(Float.isFinite(scale), "a box of " + box[0] + "x" + box[1] + " yields a finite scale");
            assertTrue(scale >= MIN && scale <= MAX,
                    "a box of " + box[0] + "x" + box[1] + " stays inside the configured range");
        }
        assertClose(MIN, SpawnEggPreviewRules.fitScale(Double.NaN, Double.NaN, MIN, MAX),
                "a NaN box falls back to the minimum rather than propagating NaN");
    }

    /**
     * The icon context carries an extra 1.6x that held and dropped contexts do not. Reading the wrong
     * multiplier here is invisible in a still frame, so both sides are pinned against each other.
     */
    private static void theIconContextIsTheOnlyOneBoosted() {
        float fitted = 0.2F;
        float icon = SpawnEggPreviewRules.contextScale(fitted, true, 1.0F, 1.0F);
        float item = SpawnEggPreviewRules.contextScale(fitted, false, 1.0F, 1.0F);
        assertClose(fitted * 1.6F, icon, "the icon context receives the boost");
        assertClose(fitted, item, "the held and dropped contexts do not");
        assertTrue(icon > item, "the icon is drawn larger than the same mob held in hand");

        // Each rate reaches only its own branch.
        assertClose(fitted * SpawnEggPreviewRules.GUI_BOOST * 2.0F,
                SpawnEggPreviewRules.contextScale(fitted, true, 2.0F, 1.0F),
                "the icon rate applies to the icon context");
        assertClose(fitted * 3.0F,
                SpawnEggPreviewRules.contextScale(fitted, false, 1.0F, 3.0F),
                "the item rate applies to the other contexts");
        assertClose(fitted * 3.0F,
                SpawnEggPreviewRules.contextScale(fitted, false, 9.0F, 3.0F),
                "and the icon rate does not leak into the other contexts");
    }

    /** The tilt and the lift belong to the slot, not to the world. */
    private static void theIconFrameIsTheOnlyOneTilted() {
        assertTrue(SpawnEggPreviewRules.usesIconFrame(true), "the inventory icon is tilted and lifted");
        assertFalse(SpawnEggPreviewRules.usesIconFrame(false), "a held or dropped egg is not");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertClose(float expected, float actual, String message) {
        if (Math.abs(expected - actual) > 1.0E-5F) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }
}
