package first.wildfires.client.smithing;

import first.wildfires.smithing.PerfectForgeProgress;
import first.wildfires.smithing.PerfectForgeService;
import net.dries007.tfc.common.capabilities.forge.ForgeStep;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * The local player's forging record, as last sent by the server.
 *
 * <p>Both the anvil's one-click button and the forging manual need it, and neither can read the world
 * save. The server sends this player's record on join and whenever a completed forge changes it, and
 * everything client-side reads it from here.
 *
 * <p>Replaced wholesale rather than merged: the server's copy is the only truth, so a stale local entry
 * can never outlive the update that removed it.
 *
 * <p>The steps themselves ride along with the same update: the button draws its memory frame from them,
 * and so does the strip above the anvil and every page of the manual - see {@link #sequence}.
 */
public final class PerfectForgeClientData {

    private static volatile Map<ResourceLocation, PerfectForgeProgress> progress = Map.of();

    /** The bar those entries are measured against, which the server sends with them. */
    private static volatile int xpToUnlock = PerfectForgeService.XP_TO_UNLOCK;

    /** The last sequence this player struck out per recipe, in strike order. */
    private static volatile Map<ResourceLocation, List<ForgeStep>> sequences = Map.of();

    private PerfectForgeClientData() {
    }

    public static void accept(Map<ResourceLocation, PerfectForgeProgress> incoming, int bar,
                              Map<ResourceLocation, List<ForgeStep>> remembered) {
        progress = Map.copyOf(incoming);
        xpToUnlock = Math.max(1, bar);
        sequences = Map.copyOf(remembered);
    }

    /**
     * How much experience unlocks a recipe for the local player.
     *
     * <p>Their own figure rather than a rule of the mod: it is longer for a smith below
     * {@link PerfectForgeService#SHORTCUT_SKILL_LEVEL}, and only the server knows the level. Until the
     * first record arrives, the shortest bar stands in for it.
     */
    public static int xpToUnlock() {
        return xpToUnlock;
    }

    /** A recipe with no entry anywhere reads as never forged and not unlocked. */
    public static PerfectForgeProgress progress(ResourceLocation recipe) {
        return PerfectForgeProgress.of(progress, recipe);
    }

    public static int xp(ResourceLocation recipe) {
        return progress(recipe).xp();
    }

    /** How many times the local player has forged this recipe, for the manual's right-hand column. */
    public static int forged(ResourceLocation recipe) {
        return progress(recipe).forged();
    }

    public static boolean isUnlocked(ResourceLocation recipe) {
        return progress(recipe).unlocked();
    }

    /**
     * The sequence this player last struck out on a recipe, in strike order, or an empty list when the
     * server holds none.
     *
     * <p>A whole sequence, not a fact about one: it is what the anvil's memory strip draws and what the
     * manual lists, so it has to arrive here rather than be worked out. It is the player's own work, and
     * the server only ever remembers a run that ended in an item.
     */
    public static List<ForgeStep> sequence(ResourceLocation recipe) {
        return sequences.getOrDefault(recipe, List.of());
    }

    /**
     * Whether the server holds a sequence for this recipe that this player can have struck again.
     *
     * <p>Says nothing about whether it can be struck right now: a remembered sequence is only picked up
     * from the point the piece on the anvil has reached, and that is judged on the server when the button
     * is pressed. This is the fact the button draws its third frame from, and nothing more.
     */
    public static boolean isRemembered(ResourceLocation recipe) {
        return !sequence(recipe).isEmpty();
    }
}
