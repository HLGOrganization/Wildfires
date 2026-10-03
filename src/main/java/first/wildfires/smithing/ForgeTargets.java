package first.wildfires.smithing;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * The work value a recipe wants, worked out the same way the anvil works it out.
 *
 * <p>TFC picks a recipe's target from the world seed and the recipe's own id
 * ({@code AnvilRecipe#computeTarget}), which makes it stable for a given world and different between
 * worlds. That method only exists on the server - it needs TFC's own inventory wrapper to read the seed
 * from - so this repeats the same arithmetic from the seed alone. The client learns the seed from the
 * server (see {@code ForgeSeedSyncPacket}); without it there is no target to show.
 *
 * <p>Kept in step with TFC by {@code ForgePathFinderSelfTest}, which checks the range and the spread.
 */
public final class ForgeTargets {

    /** Targets run from 40 up to 40 + 74 - 1, matching {@code 40 + nextInt(74)}. */
    public static final int MIN = 40;
    public static final int RANGE = 74;
    public static final int MAX = MIN + RANGE - 1;

    private ForgeTargets() {
    }

    public static int compute(long seed, ResourceLocation recipe) {
        return MIN + new XoroshiroRandomSource(seed).forkPositional().fromHashOf(recipe).nextInt(RANGE);
    }
}
