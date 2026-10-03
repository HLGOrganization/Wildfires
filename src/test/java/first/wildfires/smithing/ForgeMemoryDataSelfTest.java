package first.wildfires.smithing;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Plain-Java checks for the remembered forging sequences.
 *
 * <p>What is pinned here is the promise the memory forge rests on: the sequence handed back for a recipe is
 * byte-for-byte the sequence that was completed on it, in the order it was struck - a sequence that comes
 * back shorter, reordered, or with someone else's blows in it would be played back as that smith's work and
 * work the item differently. The save shape is pinned with it, because a renamed key does not fail loudly:
 * it silently orphans every sequence already on disk.
 *
 * <p>The rule that nothing but a completed forge is written is enforced by the caller rather than here, so
 * what is checked instead is the guard that keeps a hand-edited file from being replayed: a blank or
 * oversized sequence is dropped whole rather than half-read.
 */
public final class ForgeMemoryDataSelfTest {

    private static final UUID ALICE = UUID.fromString("6f8a2e0e-6b2e-4a1c-9d6b-3b0f2f5c1a10");
    private static final UUID BOB = UUID.fromString("1c9c2a80-3f0f-4d1a-8c4e-2a1b7d9e5f30");
    private static final UUID NOBODY = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    /** The key {@code ForgeMemoryData} writes under. Pinned: renaming it orphans every save. */
    private static final String PLAYERS_KEY = "Players";

    /** Mirrors {@code ForgeMemoryData.MAX_STEPS}. */
    private static final int MAX_STEPS = 150;

    private ForgeMemoryDataSelfTest() {
    }

    public static void main(String[] args) {
        try {
            aFreshRecordRemembersNothing();
            aSequenceRoundTripsInOrder();
            rememberingAgainReplacesTheSequence();
            onePlayersSequenceIsNotAnothers();
            aSequenceThatCouldNotHaveBeenForgedIsRefused();
            forgettingTakesOneRecipeAndSaysSo();
            forgettingEverythingTakesTheWholePlayer();
            theSaveShapeIsPinned();
            unreadableEntriesAreSkipped();
            System.out.println("ForgeMemoryDataSelfTest: all checks passed");
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            System.out.println("ForgeMemoryDataSelfTest: skipped, Minecraft classes are unavailable (" + e + ")");
        }
    }

    private static void aFreshRecordRemembersNothing() {
        ForgeMemoryData data = fresh();
        assertEquals(0, data.steps(ALICE, bars()).size(), "an unknown player remembers nothing");
        assertTrue(!data.forget(ALICE, bars()), "and there is nothing to forget");
    }

    private static void aSequenceRoundTripsInOrder() {
        ForgeMemoryData data = fresh();
        List<String> steps = List.of("HIT_LIGHT", "DRAW", "BEND", "HIT_HARD");
        data.remember(ALICE, bars(), steps);

        assertEquals(steps, data.steps(ALICE, bars()), "the sequence is readable back as it was struck");
        assertEquals(steps, reload(data).steps(ALICE, bars()), "and survives a save and a load");
    }

    private static void rememberingAgainReplacesTheSequence() {
        ForgeMemoryData data = fresh();
        data.remember(ALICE, bars(), List.of("HIT_LIGHT", "DRAW"));
        data.remember(ALICE, bars(), List.of("UPSET", "SHRINK", "PUNCH"));

        assertEquals(List.of("UPSET", "SHRINK", "PUNCH"), data.steps(ALICE, bars()),
                "the newer sequence is the one remembered, not both and not the older one");
        assertEquals(List.of("UPSET", "SHRINK", "PUNCH"), reload(data).steps(ALICE, bars()),
                "a reread holds that one sequence, not a growing history");
    }

    private static void onePlayersSequenceIsNotAnothers() {
        ForgeMemoryData data = fresh();
        data.remember(ALICE, bars(), List.of("HIT_LIGHT"));
        data.remember(BOB, bars(), List.of("HIT_HARD"));
        data.remember(ALICE, shield(), List.of("DRAW", "BEND"));

        assertEquals(List.of("HIT_LIGHT"), data.steps(ALICE, bars()), "a smith's own work is theirs");
        assertEquals(List.of("HIT_HARD"), data.steps(BOB, bars()), "another smith's is not");
        assertEquals(List.of("DRAW", "BEND"), data.steps(ALICE, shield()),
                "and one recipe does not answer for another");
        assertEquals(0, data.steps(ALICE, id("tfc:anvil/never_forged")).size(),
                "a recipe that was never completed has nothing remembered");

        ForgeMemoryData loaded = reload(data);
        assertEquals(List.of("HIT_LIGHT"), loaded.steps(ALICE, bars()), "all three survive a reload");
        assertEquals(List.of("HIT_HARD"), loaded.steps(BOB, bars()), "all three survive a reload");
        assertEquals(List.of("DRAW", "BEND"), loaded.steps(ALICE, shield()), "all three survive a reload");
    }

    private static void aSequenceThatCouldNotHaveBeenForgedIsRefused() {
        ForgeMemoryData data = fresh();
        data.remember(ALICE, bars(), List.of("HIT_LIGHT"));

        data.remember(ALICE, bars(), List.of());
        assertEquals(List.of("HIT_LIGHT"), data.steps(ALICE, bars()),
                "an empty sequence is not a forge, and does not erase one that was");

        data.remember(ALICE, shield(), oversized());
        assertEquals(0, data.steps(ALICE, shield()).size(),
                "a sequence longer than an anvil could survive is refused whole");

        data.remember(ALICE, id("tfc:anvil/long"), fits());
        assertEquals(MAX_STEPS, data.steps(ALICE, id("tfc:anvil/long")).size(),
                "the longest sequence a forge can end with is kept, at exactly the limit");
    }

    private static void forgettingTakesOneRecipeAndSaysSo() {
        ForgeMemoryData data = fresh();
        data.remember(ALICE, bars(), List.of("HIT_LIGHT"));
        data.remember(ALICE, shield(), List.of("DRAW"));
        data.remember(BOB, bars(), List.of("HIT_HARD"));

        assertTrue(data.forget(ALICE, bars()), "forgetting a remembered recipe reports it");
        assertEquals(0, data.steps(ALICE, bars()).size(), "and takes that sequence with it");
        assertEquals(List.of("DRAW"), data.steps(ALICE, shield()), "another recipe is untouched");
        assertEquals(List.of("HIT_HARD"), data.steps(BOB, bars()), "another player is untouched");
        assertTrue(!data.forget(ALICE, bars()), "forgetting it again reports nothing to forget");

        // The forgotten recipe is gone from the file too, rather than written back as an empty list.
        CompoundTag saved = data.save(new CompoundTag());
        assertTrue(!saved.getCompound(PLAYERS_KEY).getCompound(ALICE.toString()).contains(bars().toString()),
                "a forgotten sequence is not written back to the file");
        assertTrue(saved.getCompound(PLAYERS_KEY).getCompound(ALICE.toString()).contains(shield().toString()),
                "while the recipe that was not forgotten still is");
        assertTrue(saved.getCompound(PLAYERS_KEY).contains(BOB.toString()),
                "and a player who still has a sequence is written");
    }

    private static void forgettingEverythingTakesTheWholePlayer() {
        ForgeMemoryData data = fresh();
        data.remember(ALICE, bars(), List.of("HIT_LIGHT"));
        data.remember(ALICE, shield(), List.of("DRAW"));
        data.remember(BOB, bars(), List.of("HIT_HARD"));

        assertEquals(0, data.recipes(NOBODY).size(), "an unknown player has no recipes remembered");
        assertEquals(2, data.recipes(ALICE).size(), "both of a player's recipes are known to a reset");
        assertEquals(2, data.forgetAll(ALICE), "a full reset reports every sequence it drops");
        assertEquals(0, data.recipes(ALICE).size(), "and leaves none behind");
        assertEquals(0, data.steps(ALICE, bars()).size(), "neither of them");
        assertEquals(0, data.steps(ALICE, shield()).size(), "neither of them");
        assertEquals(List.of("HIT_HARD"), data.steps(BOB, bars()), "another player is not reset with them");
        assertEquals(0, data.forgetAll(ALICE), "a second reset has nothing left to drop");

        CompoundTag saved = data.save(new CompoundTag());
        assertTrue(!saved.getCompound(PLAYERS_KEY).contains(ALICE.toString()),
                "a fully reset player is not written back to the file");
        assertEquals(0, ForgeMemoryData.load(saved).steps(ALICE, bars()).size(),
                "and comes back empty");
    }

    private static void theSaveShapeIsPinned() {
        ForgeMemoryData data = fresh();
        data.remember(ALICE, bars(), List.of("HIT_LIGHT", "DRAW"));

        CompoundTag saved = data.save(new CompoundTag());
        ListTag steps = saved.getCompound(PLAYERS_KEY).getCompound(ALICE.toString())
                .getList(bars().toString(), Tag.TAG_STRING);
        assertEquals(2, steps.size(), "the sequence is saved as a list, under the recipe id, under the player");
        assertTrue("HIT_LIGHT".equals(steps.getString(0)) && "DRAW".equals(steps.getString(1)),
                "each blow is saved as the step's own name, in strike order");
    }

    private static void unreadableEntriesAreSkipped() {
        CompoundTag recipes = new CompoundTag();
        recipes.put("tfc:anvil/Bad", stringList("HIT_LIGHT"));
        recipes.put(bars().toString(), stringList("HIT_LIGHT"));
        recipes.put(shield().toString(), stringList("HIT_LIGHT", " "));
        recipes.put("tfc:anvil/long", oversizedList());
        recipes.put("tfc:anvil/not_a_list", new CompoundTag());

        CompoundTag players = new CompoundTag();
        players.put(ALICE.toString(), recipes);
        players.put("not-a-uuid", recipes.copy());
        CompoundTag tag = new CompoundTag();
        tag.put(PLAYERS_KEY, players);

        ForgeMemoryData loaded = ForgeMemoryData.load(tag);
        assertEquals(1, loaded.steps(ALICE, bars()).size(),
                "a readable sequence survives the broken entries beside it");
        assertEquals(0, loaded.steps(ALICE, shield()).size(),
                "a blank step drops that whole sequence rather than leaving a hole in it");
        assertEquals(1, loaded.recipes(ALICE).size(),
                "a recipe id that cannot be parsed is skipped, and one that reads as no sequence with it");
        assertTrue(loaded.recipes(ALICE).contains(bars()),
                "the only entry that survives is the one that could be read in full");
        assertEquals(0, loaded.steps(ALICE, id("tfc:anvil/long")).size(),
                "an oversized stored sequence is refused");
        assertEquals(0, loaded.steps(ALICE, id("tfc:anvil/not_a_list")).size(),
                "a recipe whose value is not a list of steps is skipped");
        assertEquals(1, loaded.save(new CompoundTag()).getCompound(PLAYERS_KEY).getAllKeys().size(),
                "a player id that cannot be parsed is skipped, and its neighbour is not lost");
    }

    /** A record built the way the game builds one, without a server: an empty tag is an empty record. */
    private static ForgeMemoryData fresh() {
        return ForgeMemoryData.load(new CompoundTag());
    }

    private static ForgeMemoryData reload(ForgeMemoryData data) {
        return ForgeMemoryData.load(data.save(new CompoundTag()));
    }

    private static List<String> fits() {
        List<String> steps = new ArrayList<>(MAX_STEPS);
        for (int i = 0; i < MAX_STEPS; i++) {
            steps.add(i % 2 == 0 ? "HIT_LIGHT" : "HIT_HARD");
        }
        return steps;
    }

    private static List<String> oversized() {
        List<String> steps = new ArrayList<>(fits());
        steps.add("DRAW");
        return steps;
    }

    private static ListTag stringList(String... steps) {
        ListTag list = new ListTag();
        for (String step : steps) {
            list.add(StringTag.valueOf(step));
        }
        return list;
    }

    private static ListTag oversizedList() {
        ListTag list = new ListTag();
        for (int i = 0; i <= MAX_STEPS; i++) {
            list.add(StringTag.valueOf("HIT_LIGHT"));
        }
        return list;
    }

    private static ResourceLocation bars() {
        return id("tfc:anvil/bismuth_bronze_bars");
    }

    private static ResourceLocation shield() {
        return id("tfc:anvil/bismuth_bronze_shield");
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new AssertionError("bad test id " + value);
        }
        return id;
    }

    private static void assertEquals(int expected, int actual, String what) {
        if (expected != actual) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertEquals(List<String> expected, List<String> actual, String what) {
        if (!expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean value, String what) {
        if (!value) {
            throw new AssertionError(what);
        }
    }
}
