package first.wildfires.smithing;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The steps of the last sequence each player finished each anvil recipe with.
 *
 * <p>TFC keeps only the last three strikes of a forging ({@code ForgeSteps} is a rolling window), which is
 * enough for the anvil itself but not for repeating a smith's own work, so the full sequence is kept here.
 * It is written when a recipe is completed and only then, which is what makes a remembered sequence worth
 * playing back: it is one that ended in an item, not an attempt that was abandoned halfway.
 *
 * <p>Per player per recipe, because the useful thing to remember is how <em>that</em> smith works
 * <em>that</em> recipe, and recipes differ enough - a different order, or a different number of blows -
 * that one sequence per recipe is the only honest granularity. A newer sequence replaces the older one,
 * as it is by definition the more recent answer to the same question.
 *
 * <p>The steps are stored as the names of TFC's {@code ForgeStep} constants rather than as ordinals or as
 * the enum itself: a name survives a reordering of that enum and a version that drops a step is readable,
 * and nothing in this file has to know a TFC class. {@link ForgeMemory} does the translation, and refuses
 * a sequence it cannot translate in full - a replay with a hole in it would not be the smith's own work.
 *
 * <p>Kept in the overworld's data storage, like the unlock progress, so it survives death and needs no
 * capability. Only recipes a player has actually completed are written.
 */
public final class ForgeMemoryData extends SavedData {

    public static final String FILE_ID = "wildfires_forge_memory";

    private static final String KEY_PLAYERS = "Players";

    /**
     * The longest sequence worth keeping: TFC ruins an item it has struck more than 150 times, so nothing
     * a smith could have completed can be longer than that. A longer list can only come from a hand-edited
     * file, and replaying one would be a hundred minutes of hammering.
     */
    public static final int MAX_STEPS = 150;

    /** Player UUID to (recipe id to the step names of the sequence last completed there). */
    private final Map<UUID, Map<ResourceLocation, List<String>>> memories = new HashMap<>();

    public static ForgeMemoryData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage()
                .computeIfAbsent(ForgeMemoryData::load, ForgeMemoryData::new, FILE_ID);
    }

    /**
     * The steps of the last sequence completed on this recipe, in strike order.
     *
     * @return the step names, empty when this player has never completed this recipe by hand
     */
    public List<String> steps(UUID player, ResourceLocation recipe) {
        List<String> steps = memories.getOrDefault(player, Map.of()).get(recipe);
        return steps == null ? List.of() : steps;
    }

    /** Remembers a completed sequence, replacing whatever was remembered for that recipe before. */
    public void remember(UUID player, ResourceLocation recipe, List<String> steps) {
        if (steps.isEmpty() || steps.size() > MAX_STEPS) {
            return;
        }
        memories.computeIfAbsent(player, id -> new HashMap<>()).put(recipe, List.copyOf(steps));
        setDirty();
    }

    /**
     * Forgets one recipe's sequence, for a player whose progress there has been reset.
     *
     * @return whether there was anything to forget
     */
    public boolean forget(UUID player, ResourceLocation recipe) {
        Map<ResourceLocation, List<String>> forPlayer = memories.get(player);
        if (forPlayer == null || forPlayer.remove(recipe) == null) {
            return false;
        }
        if (forPlayer.isEmpty()) {
            memories.remove(player);
        }
        setDirty();
        return true;
    }

    /** Which recipes this player has a sequence remembered for, for a reset that has to name them. */
    public Set<ResourceLocation> recipes(UUID player) {
        return Set.copyOf(memories.getOrDefault(player, Map.of()).keySet());
    }

    /**
     * Forgets every sequence a player has, for a reset of everything they have done on the anvil.
     *
     * @return how many sequences were dropped
     */
    public int forgetAll(UUID player) {
        Map<ResourceLocation, List<String>> forPlayer = memories.remove(player);
        if (forPlayer == null || forPlayer.isEmpty()) {
            return 0;
        }
        setDirty();
        return forPlayer.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, Map<ResourceLocation, List<String>>> entry : memories.entrySet()) {
            CompoundTag recipes = new CompoundTag();
            for (Map.Entry<ResourceLocation, List<String>> memory : entry.getValue().entrySet()) {
                ListTag steps = new ListTag();
                for (String step : memory.getValue()) {
                    steps.add(StringTag.valueOf(step));
                }
                recipes.put(memory.getKey().toString(), steps);
            }
            players.put(entry.getKey().toString(), recipes);
        }
        tag.put(KEY_PLAYERS, players);
        return tag;
    }

    public static ForgeMemoryData load(CompoundTag tag) {
        ForgeMemoryData data = new ForgeMemoryData();
        CompoundTag players = tag.getCompound(KEY_PLAYERS);
        for (String rawPlayer : players.getAllKeys()) {
            UUID player;
            try {
                player = UUID.fromString(rawPlayer);
            } catch (IllegalArgumentException malformed) {
                // A hand-edited or corrupted file should cost at most that one entry.
                continue;
            }
            Map<ResourceLocation, List<String>> forPlayer = readPlayer(players.getCompound(rawPlayer));
            if (!forPlayer.isEmpty()) {
                data.memories.put(player, forPlayer);
            }
        }
        return data;
    }

    private static Map<ResourceLocation, List<String>> readPlayer(CompoundTag recipes) {
        Map<ResourceLocation, List<String>> forPlayer = new HashMap<>();
        for (String rawRecipe : recipes.getAllKeys()) {
            ResourceLocation recipe = ResourceLocation.tryParse(rawRecipe);
            if (recipe == null) {
                continue;
            }
            List<String> steps = readSteps(recipes.getList(rawRecipe, Tag.TAG_STRING));
            if (!steps.isEmpty()) {
                forPlayer.put(recipe, steps);
            }
        }
        return forPlayer;
    }

    /**
     * The steps one stored list holds, dropping anything that could not have been written by a forge.
     *
     * <p>A blank name or a sequence longer than {@link #MAX_STEPS} means the file has been edited by hand,
     * and a half-read sequence is worse than none: it would be played back as if the smith had worked that
     * way. Whether a name names a real step is not decided here - that needs TFC - so an unknown name is
     * kept and {@link ForgeMemory} rejects the whole sequence when it translates it.
     */
    private static List<String> readSteps(ListTag list) {
        if (list.isEmpty() || list.size() > MAX_STEPS) {
            return List.of();
        }
        List<String> steps = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            String step = list.getString(i);
            if (step.isBlank()) {
                return List.of();
            }
            steps.add(step);
        }
        return List.copyOf(steps);
    }
}
