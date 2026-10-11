package first.wildfires.smithing;

import net.dries007.tfc.common.blockentities.AnvilBlockEntity;
import net.dries007.tfc.common.capabilities.forge.ForgeStep;
import net.dries007.tfc.common.capabilities.forge.ForgeSteps;
import net.dries007.tfc.common.capabilities.forge.Forging;
import net.dries007.tfc.common.recipes.AnvilRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers the last sequence a smith struck out on a recipe, and plays it back.
 *
 * <p>This is what the one-click forge strikes: the work the player did by hand the last time they finished
 * that recipe, one blow at a time, rather than the item being made outright. It is struck at
 * {@link PerfectForgeSequence#MEMORY_TICKS} ticks a blow, and only for a recipe whose forge this player has
 * unlocked - until then there is nothing to press for, and the blows are what fills the bar that unlocks it.
 *
 * <p>A replay is started on whatever the anvil holds, so a piece that already carries the first blows of the
 * sequence - left there by a replay that was interrupted, or struck by the smith's own hand - is finished by
 * the rest of it rather than refused. A piece that is not that sequence's own work is refused.
 *
 * <p>A sequence is only ever remembered from a forge that <em>ended in an item</em>. TFC keeps just the
 * last three strikes, so the sequence is collected here as the blows happen: every strike the anvil accepts
 * is written to a draft at the position it went in at, and the draft is only handed to
 * {@link ForgeMemoryData} when the recipe completes. An attempt that was abandoned half way, or that
 * ruined the item, therefore teaches nothing - playing it back would not produce anything either.
 *
 * <p>The position a strike goes in at is its strike number, which makes a draft correct across everything
 * a smith can do to a forging: a restart - a different recipe chosen, or a fresh item put on the anvil -
 * begins striking from one again and so overwrites the draft from its first blow rather than extending a
 * sequence that no longer describes anything.
 *
 * <p>All of this is server-side, and the sequences themselves are the one thing sent out: the anvil's
 * memory strip and the manual both draw the player's own blows, and both of those are client-side. Nothing
 * else about a forging leaves the server, and a player is only ever sent their own sequences.
 */
public final class ForgeMemory {

    /** The blows of the forging each player is in the middle of: player, then recipe, then strike number. */
    private static final Map<UUID, Map<ResourceLocation, Map<Integer, ForgeStep>>> DRAFTS = new HashMap<>();

    private ForgeMemory() {
    }

    /**
     * Notes one strike the anvil accepted.
     *
     * <p>Called from TFC's own work method, so it sees every blow struck by hand and every blow this mod
     * plays back - a replay is a strike the anvil accepted too, which is what keeps a draft true to the
     * item even when a smith takes over from a replay halfway through.
     */
    public static void record(ServerPlayer player, AnvilBlockEntity anvil, ForgeStep step) {
        Forging forging = anvil.getMainInputForging();
        ResourceLocation recipeId = recipeId(anvil, forging);
        if (recipeId == null) {
            return;
        }
        if (forging.getSteps().total() <= 0) {
            // Not on a forging that has been struck, which cannot be true of an accepted blow.
            return;
        }
        DRAFTS.computeIfAbsent(player.getUUID(), id -> new HashMap<>())
                .computeIfAbsent(recipeId, id -> new HashMap<>())
                .put(forging.getSteps().total(), step);
    }

    /**
     * Remembers the sequence a forge that has just completed was struck out with.
     *
     * <p>Only the strikes of that forging are taken, and only if every one of them was seen: a sequence
     * with a gap in it is not what the smith did, and guessing the missing blow would replay as a
     * different forging - which could ruin the item, since too much work is worse than too little.
     */
    public static void commit(ServerPlayer player, AnvilBlockEntity anvil) {
        MinecraftServer server = player.getServer();
        Forging forging = anvil.getMainInputForging();
        ResourceLocation recipeId = recipeId(anvil, forging);
        if (server == null || recipeId == null) {
            return;
        }

        Map<ResourceLocation, Map<Integer, ForgeStep>> forPlayer = DRAFTS.get(player.getUUID());
        Map<Integer, ForgeStep> draft = forPlayer == null ? null : forPlayer.get(recipeId);
        if (draft == null) {
            // The blows were not seen - a forge driven by something other than the anvil, or a restart of
            // the server - so this forge has nothing to say about how the recipe is worked.
            return;
        }
        forPlayer.remove(recipeId);
        if (forPlayer.isEmpty()) {
            DRAFTS.remove(player.getUUID());
        }

        int total = forging.getSteps().total();
        List<String> steps = new ArrayList<>(total);
        for (int strike = 1; strike <= total; strike++) {
            ForgeStep step = draft.get(strike);
            if (step == null) {
                return;
            }
            steps.add(step.name());
        }
        ForgeMemoryData.get(server).remember(player.getUUID(), recipeId, steps);
    }

    /**
     * The sequence to play back for this recipe, in strike order.
     *
     * @return the steps, empty when this player has no sequence for the recipe or when the stored one
     *         cannot be read as a sequence of real steps
     */
    public static List<ForgeStep> remembered(MinecraftServer server, UUID player, ResourceLocation recipe) {
        return steps(ForgeMemoryData.get(server).steps(player, recipe));
    }

    /**
     * Every sequence this player has, by recipe. What the anvil's strip and the manual read from the
     * server's copy, one entry per recipe the player has finished by hand at least once.
     */
    public static Map<ResourceLocation, List<ForgeStep>> rememberedAll(MinecraftServer server, UUID player) {
        Map<ResourceLocation, List<ForgeStep>> all = new HashMap<>();
        for (Map.Entry<ResourceLocation, List<String>> entry
                : ForgeMemoryData.get(server).sequences(player).entrySet()) {
            List<ForgeStep> steps = steps(entry.getValue());
            if (!steps.isEmpty()) {
                all.put(entry.getKey(), steps);
            }
        }
        return all;
    }

    /** Reads stored step names back as steps, or nothing at all when one of them is not a real step. */
    private static List<ForgeStep> steps(List<String> names) {
        if (names.isEmpty()) {
            return List.of();
        }
        List<ForgeStep> steps = new ArrayList<>(names.size());
        for (String name : names) {
            ForgeStep step = step(name);
            if (step == null) {
                // A step this version of TFC does not have: the whole sequence is unusable, because a
                // replay missing a blow would work the item differently from the sequence it came from.
                return List.of();
            }
            steps.add(step);
        }
        return List.copyOf(steps);
    }

    /** Drops the part-finished drafts. A forging belongs to the world that saw it, draft included. */
    public static void clearRuntime() {
        DRAFTS.clear();
    }

    /**
     * The blows of a remembered sequence this forging has not been struck by yet.
     *
     * <p>An interrupted replay leaves a piece that is the first blows of the remembered sequence and nothing
     * else, so the rest of the sequence finishes it exactly as the original forge finished it - which is what
     * makes a replay resumable by clicking the button again. Anything else on the anvil is refused rather than
     * guessed at: working a piece with blows its smith never struck on it is how a replay ruins one.
     *
     * <p>What the piece is read against is what TFC reads back from it - its work, and the last three blows it
     * has seen - because those are the whole of what decides how the rest of the forging works out. A piece
     * that agrees with the sequence on both is finished the same way, whichever blows it was struck by: a
     * smith who struck the first blows of the sequence by hand has done the same work as a replay that got
     * that far.
     *
     * @return the blows still to strike, or {@code null} when this piece is not that sequence's own work, or
     *         the sequence has nothing left to strike
     */
    public static List<ForgeStep> remaining(List<ForgeStep> remembered, Forging forging) {
        ForgeSteps steps = forging.getSteps();
        return remaining(remembered, steps.total(), forging.getWork(), window(steps));
    }

    /**
     * The check above, apart from the forging so that it can be exercised without one.
     *
     * @param struck the blows already counted against the forging
     * @param work   the work those blows left it at
     * @param window its last blows, oldest first: three of them, or as many as it has been struck
     */
    static List<ForgeStep> remaining(List<ForgeStep> remembered, int struck, int work, List<ForgeStep> window) {
        if (remembered.isEmpty() || struck < 0 || struck >= remembered.size()
                || window.size() != Math.min(3, struck)) {
            return null;
        }
        int sum = 0;
        for (int strike = 0; strike < struck; strike++) {
            sum += remembered.get(strike).step();
        }
        if (sum != work) {
            return null;
        }
        for (int i = 0; i < window.size(); i++) {
            if (remembered.get(struck - window.size() + i) != window.get(i)) {
                return null;
            }
        }
        return List.copyOf(remembered.subList(struck, remembered.size()));
    }

    /**
     * The last blows a forging has seen, oldest first. TFC keeps the newest three and no more, so this is
     * every blow of a forging struck fewer than three times.
     */
    private static List<ForgeStep> window(ForgeSteps steps) {
        List<ForgeStep> window = new ArrayList<>(3);
        for (ForgeStep step : new ForgeStep[]{steps.thirdLast(), steps.secondLast(), steps.last()}) {
            if (step != null) {
                window.add(step);
            }
        }
        return window;
    }

    private static ForgeStep step(String name) {
        for (ForgeStep step : ForgeStep.VALUES) {
            if (step.name().equals(name)) {
                return step;
            }
        }
        return null;
    }

    private static ResourceLocation recipeId(AnvilBlockEntity anvil, Forging forging) {
        Level level = anvil.getLevel();
        if (forging == null || level == null) {
            return null;
        }
        AnvilRecipe recipe = forging.getRecipe(level);
        return recipe == null ? null : recipe.getId();
    }
}
