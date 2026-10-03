package first.wildfires.smithing;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * What each player has done at each anvil recipe, and which recipes have unlocked their one-click forge.
 *
 * <p>Per recipe this holds the forging experience the player has built up there, whether the one-click
 * forge has unlocked, and how many times the recipe has been forged. What a forge pays is TFC's business -
 * the quality it stamped on the result, or a flat point for a recipe that carries no quality at all - and
 * what that experience unlocks is decided elsewhere ({@link PerfectForgeService}), because it also depends
 * on the player's skill level and on chance. So the experience and the unlocked flag are stored separately.
 *
 * <p>Kept in the overworld's data storage rather than on the player, so the record survives death and
 * needs no capability and no clone handling. Only recipes a player has actually forged are written, which
 * keeps the file small even though a modpack can define hundreds of recipes.
 *
 * <p>Also held here, though it is a preference rather than a record, is whether the forge should say what
 * a forge earned ({@link #reports}): kept beside the record so that it survives death and a restart the
 * same way, in a file whose per-player half is already keyed by UUID.
 *
 * <p>Everything here is server-side. The client sees a copy through {@code PerfectForgeSyncPacket}.
 */
public final class PerfectForgeData extends SavedData {

    public static final String FILE_ID = "wildfires_perfect_forge";

    private static final String KEY_PLAYERS = "Players";
    private static final String KEY_XP = "Xp";
    private static final String KEY_UNLOCKED = "Unlocked";

    /**
     * How many times the recipe has been forged, for the manual to show.
     *
     * <p>Written since the manual started reporting practice. A record that predates the key simply has no
     * tally, and reads as none: the experience it holds cannot be turned back into one, because that is
     * capped and this is not.
     */
    private static final String KEY_FORGED = "Forged";

    /** Written by the perfect-count rule and only ever read now: that rule's units are experience. */
    private static final String KEY_LEGACY_COUNT = "Count";

    /**
     * Players who have asked the forge to report what their forges earn them.
     *
     * <p>A list of the players who have it on rather than a flag per player, so that a save that has never
     * seen the switch - every save written before it existed - reads as off for everyone, and turning it on
     * for one player writes nothing at all for the others.
     */
    private static final String KEY_REPORTS = "Reports";

    /**
     * What one perfectly forged item is worth under the experience rule.
     *
     * <p>Mirrors {@code ForgingBonus.PERFECTLY_FORGED.ordinal() + 1} in {@link PerfectForgeService}, and
     * pinned here rather than read from that enum so that reading an old save never needs a TFC class.
     * Only used to convert a save written by the perfect-count rule, where each unit was one perfectly
     * forged item: two of them are exactly the two perfect forges the current rule asks for.
     */
    private static final int PERFECT_XP = 5;

    /** Player UUID to (recipe id to progress). */
    private final Map<UUID, Map<ResourceLocation, PerfectForgeProgress>> progress = new HashMap<>();

    /** Players who asked to be told what their forges earn. Empty until the switch is turned on. */
    private final Set<UUID> reports = new HashSet<>();

    public static PerfectForgeData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage()
                .computeIfAbsent(PerfectForgeData::load, PerfectForgeData::new, FILE_ID);
    }

    public PerfectForgeProgress progress(UUID player, ResourceLocation recipe) {
        return PerfectForgeProgress.of(entries(player), recipe);
    }

    public boolean isUnlocked(UUID player, ResourceLocation recipe) {
        return progress(player, recipe).unlocked();
    }

    /**
     * Adds forging experience and returns the player's new total for that recipe.
     *
     * <p>Capped at {@code bar}, which is the amount that unlocks the recipe for this player
     * ({@link PerfectForgeService#xpToUnlock}) - it depends on their skill level, so the caller passes it.
     * The figure therefore climbs to the point where it has bought everything it can and then stays there.
     * That is true of an unlocked recipe as well, so what a player sees never goes backwards.
     *
     * @param bar how much experience unlocks this recipe for this player
     */
    public int addXp(UUID player, ResourceLocation recipe, int xp, int bar) {
        Map<ResourceLocation, PerfectForgeProgress> forPlayer = mutableEntries(player);
        PerfectForgeProgress current = PerfectForgeProgress.of(forPlayer, recipe);
        int next = Math.min(Math.max(0, bar), Math.max(0, current.xp() + xp));
        forPlayer.put(recipe, new PerfectForgeProgress(next, current.unlocked(), current.forged()));
        setDirty();
        return next;
    }

    /**
     * Counts one completed forge and returns the player's new total for that recipe.
     *
     * <p>A tally rather than a rule: no progression reads it, and the forging manual is the only thing that
     * shows it. Unlike the experience it is never capped and never converted, so a smith who has forged a
     * recipe a hundred times still sees a hundred after the bar has long been full.
     */
    public int addForge(UUID player, ResourceLocation recipe) {
        Map<ResourceLocation, PerfectForgeProgress> forPlayer = mutableEntries(player);
        PerfectForgeProgress current = PerfectForgeProgress.of(forPlayer, recipe);
        int next = current.forged() + 1;
        forPlayer.put(recipe, new PerfectForgeProgress(current.xp(), current.unlocked(), next));
        setDirty();
        return next;
    }

    /**
     * Marks a recipe's one-click forge as unlocked.
     *
     * @return whether this changed anything, so the caller only announces a genuinely new unlock
     */
    public boolean unlock(UUID player, ResourceLocation recipe) {
        Map<ResourceLocation, PerfectForgeProgress> forPlayer = mutableEntries(player);
        PerfectForgeProgress current = PerfectForgeProgress.of(forPlayer, recipe);
        if (current.unlocked()) {
            return false;
        }
        forPlayer.put(recipe, new PerfectForgeProgress(current.xp(), true, current.forged()));
        setDirty();
        return true;
    }

    /**
     * Forgets one recipe's record, both the experience and any unlock.
     *
     * <p>This is the only way a record ever goes away: forging only ever adds to one. It exists for the
     * administrative reset command, so a progression that was handed out can be taken back and the recipe
     * tried again from nothing. Removing the last record drops the player's entry entirely, which keeps a
     * fully reset player out of the save file.
     *
     * @return whether there was a record to remove
     */
    public boolean clear(UUID player, ResourceLocation recipe) {
        Map<ResourceLocation, PerfectForgeProgress> forPlayer = progress.get(player);
        if (forPlayer == null || forPlayer.remove(recipe) == null) {
            return false;
        }
        if (forPlayer.isEmpty()) {
            progress.remove(player);
        }
        setDirty();
        return true;
    }

    /**
     * Forgets everything recorded for one player.
     *
     * @return how many recipes had a record, so the caller can tell a real reset from a no-op
     */
    public int clearAll(UUID player) {
        Map<ResourceLocation, PerfectForgeProgress> forPlayer = progress.remove(player);
        if (forPlayer == null || forPlayer.isEmpty()) {
            return 0;
        }
        setDirty();
        return forPlayer.size();
    }

    /** An immutable copy of one player's record, for syncing to that player's client. */
    public Map<ResourceLocation, PerfectForgeProgress> snapshot(UUID player) {
        Map<ResourceLocation, PerfectForgeProgress> forPlayer = progress.get(player);
        return forPlayer == null ? Map.of() : Map.copyOf(forPlayer);
    }

    /**
     * Whether the forge should say what this player's forges earn them.
     *
     * <p>Off for everyone who has never said otherwise, which is everyone: the running total towards an
     * unlock, the odds of a shortcut unlock, and the announcement of an unlock are a smith's bookkeeping
     * rather than part of forging, and they are wanted by the smith who is counting and by nobody else. The
     * switch is the whole of the gate, so a creative player hears nothing either unless they ask.
     */
    public boolean reports(UUID player) {
        return reports.contains(player);
    }

    /**
     * Turns the forge's reports on or off for one player.
     *
     * <p>A preference rather than a record, so the administrative reset leaves it alone: it says what the
     * player wants to hear, not what they have done.
     *
     * @return whether this changed anything, so the caller can tell a real change from a repeat
     */
    public boolean setReports(UUID player, boolean on) {
        boolean changed = on ? reports.add(player) : reports.remove(player);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    private Map<ResourceLocation, PerfectForgeProgress> entries(UUID player) {
        Map<ResourceLocation, PerfectForgeProgress> forPlayer = progress.get(player);
        return forPlayer == null ? Map.of() : forPlayer;
    }

    private Map<ResourceLocation, PerfectForgeProgress> mutableEntries(UUID player) {
        return progress.computeIfAbsent(player, key -> new HashMap<>());
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, Map<ResourceLocation, PerfectForgeProgress>> entry : progress.entrySet()) {
            CompoundTag recipes = new CompoundTag();
            for (Map.Entry<ResourceLocation, PerfectForgeProgress> recipe : entry.getValue().entrySet()) {
                PerfectForgeProgress value = recipe.getValue();
                CompoundTag record = new CompoundTag();
                record.putInt(KEY_XP, value.xp());
                record.putBoolean(KEY_UNLOCKED, value.unlocked());
                record.putInt(KEY_FORGED, value.forged());
                recipes.put(recipe.getKey().toString(), record);
            }
            players.put(entry.getKey().toString(), recipes);
        }
        tag.put(KEY_PLAYERS, players);
        if (!reports.isEmpty()) {
            ListTag on = new ListTag();
            for (UUID player : reports) {
                on.add(StringTag.valueOf(player.toString()));
            }
            tag.put(KEY_REPORTS, on);
        }
        return tag;
    }

    public static PerfectForgeData load(CompoundTag tag) {
        PerfectForgeData data = new PerfectForgeData();
        CompoundTag players = tag.getCompound(KEY_PLAYERS);
        for (String rawPlayer : players.getAllKeys()) {
            UUID player;
            try {
                player = UUID.fromString(rawPlayer);
            } catch (IllegalArgumentException malformed) {
                // A hand-edited or corrupted file should cost at most that one entry.
                continue;
            }
            Map<ResourceLocation, PerfectForgeProgress> forPlayer =
                    readPlayer(players.getCompound(rawPlayer));
            if (!forPlayer.isEmpty()) {
                data.progress.put(player, forPlayer);
            }
        }
        // A save written before the switch existed has no list at all, and reads as off for everyone.
        ListTag on = tag.getList(KEY_REPORTS, Tag.TAG_STRING);
        for (int i = 0; i < on.size(); i++) {
            try {
                data.reports.add(UUID.fromString(on.getString(i)));
            } catch (IllegalArgumentException malformed) {
                // Same again: a hand-edited file costs at most the one entry.
            }
        }
        return data;
    }

    private static Map<ResourceLocation, PerfectForgeProgress> readPlayer(CompoundTag recipes) {
        Map<ResourceLocation, PerfectForgeProgress> forPlayer = new HashMap<>();
        for (String rawRecipe : recipes.getAllKeys()) {
            ResourceLocation recipe = ResourceLocation.tryParse(rawRecipe);
            if (recipe == null) {
                continue;
            }
            if (recipes.contains(rawRecipe, Tag.TAG_COMPOUND)) {
                // A current record carries what was decided at the forge, and that stands whatever the
                // experience in it is worth today: the bar a smith forges against depends on their skill
                // level, so no amount of experience reads as an unlock on its own. The flag is always
                // written alongside the experience, so a record holding an experience figure and no flag is
                // simply a locked recipe - and the two shapes that were written without it carry a count
                // instead, which is the one thing that still converts.
                CompoundTag record = recipes.getCompound(rawRecipe);
                boolean unlocked = record.getBoolean(KEY_UNLOCKED);
                if (record.contains(KEY_XP, Tag.TAG_ANY_NUMERIC)) {
                    forPlayer.put(recipe, new PerfectForgeProgress(clampXp(record.getInt(KEY_XP)), unlocked,
                            Math.max(0, record.getInt(KEY_FORGED))));
                } else {
                    forPlayer.put(recipe, fromCount(record.getInt(KEY_LEGACY_COUNT), unlocked));
                }
            } else {
                forPlayer.put(recipe, fromCount(recipes.getInt(rawRecipe), false));
            }
        }
        return forPlayer;
    }

    /**
     * One entry as read from an older save, where a record was the count of perfectly forged items it had
     * seen rather than the experience they were worth.
     *
     * <p>Under that rule a count meant one perfectly forged item per unit, and those are worth
     * {@link #PERFECT_XP} apiece, so an old record reads as the progress those same forges would have
     * earned today. It also reads as unlocked when that reaches {@link PerfectForgeService#XP_TO_UNLOCK},
     * which is exactly when the rule it was written under unlocked it - the bar was that length for every
     * smith then, and a converted old save has no skill level attached to check it against.
     *
     * <p>The count is the tally the manual shows as well, and unlike the experience it was never capped:
     * these were forges, so they count where an unrecorded count would not.
     */
    private static PerfectForgeProgress fromCount(int count, boolean unlocked) {
        int forges = Math.max(0, count);
        int xp = clampXp(forges * PERFECT_XP);
        return new PerfectForgeProgress(xp, unlocked || xp >= PerfectForgeService.XP_TO_UNLOCK, forges);
    }

    /**
     * Caps any converted or hand-edited figure at the longest bar any smith forges against, which is the one
     * asked of a smith with no skill at all ({@link PerfectForgeService#xpToUnlock}). A shorter bar is
     * applied as it is forged against, so a value above the longest one could only come from a hand edit.
     */
    private static int clampXp(int xp) {
        return Math.min(PerfectForgeService.xpToUnlock(0), Math.max(0, xp));
    }
}
