package first.wildfires.smithing;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * One player's record for one anvil recipe: the forging experience built up on it, whether its one-click
 * forge has unlocked, and how many times the recipe has been forged.
 *
 * <p>Experience is what unlocks. Every completed forge of the recipe pays some, depending on the quality
 * TFC gave the result - a perfectly forged item pays the most, an unqualified one the least - and as much
 * of it as the smith's skill level asks for unlocks the one-click forge outright
 * ({@link PerfectForgeService#xpToUnlock}). So this is a progress bar towards the unlock rather than a tally
 * of anything the player did.
 *
 * <p>The unlocked flag is stored rather than derived from the experience, because a forge can also unlock
 * the recipe outright on a lucky roll, long before the bar is full.
 *
 * <p>The forge count is the one figure here that no rule reads: it is how much the recipe has actually been
 * practised, and the forging manual is the only thing that shows it. It keeps climbing where the
 * experience has long since stopped.
 */
public record PerfectForgeProgress(int xp, boolean unlocked, int forged) {

    public static final PerfectForgeProgress NONE = new PerfectForgeProgress(0, false, 0);

    /** Entries are collected into maps keyed by recipe, so a lookup miss reads as no progress at all. */
    public static PerfectForgeProgress of(Map<ResourceLocation, PerfectForgeProgress> map, ResourceLocation recipe) {
        PerfectForgeProgress progress = map.get(recipe);
        return progress == null ? NONE : progress;
    }
}
