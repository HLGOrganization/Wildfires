package first.wildfires.smithing;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Plain-Java checks for the per-player forging record.
 *
 * <p>This store is the only thing standing between a player's forging history and losing it, and the
 * administrative reset commands are the only thing that ever removes from it, so the checks here cover the
 * two ways it can go wrong: a reset that removes too little (a recipe that stays unlocked, or experience
 * that survives) and one that removes too much (another recipe, or another player).
 *
 * <p>The save file is pinned to its on-disk shape, including both older ones - a nested record holding a
 * perfect-forge count, and a bare number from before that - because a save written by an earlier version is
 * still a real save someone may open, and both have to land on the same experience rule as a current one.
 * The forge tally the manual shows joined the record later still, so a record without it has to read as
 * never forged rather than as the experience it happens to hold.
 *
 * <p>The reports switch is the one preference kept here rather than a record, and it is checked for the
 * same reasons: it is per player, it has to outlive a restart, and it is deliberately not something a
 * reset clears.
 */
public final class PerfectForgeDataSelfTest {

    private static final UUID ALICE = UUID.fromString("6f8a2e0e-6b2e-4a1c-9d6b-3b0f2f5c1a10");
    private static final UUID BOB = UUID.fromString("1c9c2a80-3f0f-4d1a-8c4e-2a1b7d9e5f30");

    /** The keys {@code PerfectForgeData} reads and writes. Pinned: renaming one orphans saves. */
    private static final String PLAYERS_KEY = "Players";
    private static final String XP_KEY = "Xp";
    private static final String UNLOCKED_KEY = "Unlocked";
    private static final String LEGACY_COUNT_KEY = "Count";
    private static final String FORGED_KEY = "Forged";

    /** The preference list: the players who asked the forge to report what their forges earn. */
    private static final String REPORTS_KEY = "Reports";

    /** Mirrors {@code PerfectForgeService.XP_TO_UNLOCK}: the shortest bar any smith forges against. */
    private static final int XP_TO_UNLOCK = 10;

    /** Mirrors {@code PerfectForgeService.SHORTCUT_SKILL_LEVEL} and what it asks per missing level. */
    private static final int SHORTCUT_SKILL_LEVEL = 10;
    private static final int XP_PER_MISSING_SKILL_LEVEL = 2;

    /** The longest bar there is: the one a smith with no skill at all forges against. */
    private static final int LONGEST_BAR = XP_TO_UNLOCK + SHORTCUT_SKILL_LEVEL * XP_PER_MISSING_SKILL_LEVEL;

    /** Mirrors {@code PerfectForgeData.PERFECT_XP}: what one perfectly forged item is worth. */
    private static final int PERFECT_XP = 5;

    private PerfectForgeDataSelfTest() {
    }

    public static void main(String[] args) {
        try {
            aFreshRecordIsEmpty();
            experienceAddsUpAndStopsAtTheCap();
            aLongerBarHoldsMoreThanTheShortestOne();
            forgesAreTalliedPastTheCap();
            xpAndUnlocksAreSeparate();
            clearingOneRecipeForgetsBoth();
            clearingEverythingForgetsThePlayer();
            aResetSurvivesAReload();
            legacyBareCountsReadAsExperience();
            legacyNestedCountsConvertToExperience();
            experienceAloneDoesNotReadAsAnUnlock();
            unreadableEntriesAreSkipped();
            theReportsSwitchOutlivesTheRecordAndTheReset();
            System.out.println("PerfectForgeDataSelfTest: all checks passed");
        } catch (NoClassDefFoundError | ExceptionInInitializerError e) {
            System.out.println("PerfectForgeDataSelfTest: skipped, Minecraft classes are unavailable (" + e + ")");
        }
    }

    private static void aFreshRecordIsEmpty() {
        PerfectForgeData data = fresh();
        assertEquals(0, data.snapshot(ALICE).size(), "a record for an unknown player holds nothing");
        assertEquals(0, data.progress(ALICE, bars()).xp(), "an unrecorded recipe has no experience");
        assertTrue(!data.isUnlocked(ALICE, bars()), "an unrecorded recipe is locked");
    }

    private static void experienceAddsUpAndStopsAtTheCap() {
        PerfectForgeData data = fresh();
        assertEquals(1, addXp(data, ALICE, bars(), 1), "a forge with no quality pays one point");
        assertEquals(4, addXp(data, ALICE, bars(), 3), "experience adds up");
        assertEquals(4, data.progress(ALICE, bars()).xp(), "the total is readable back");
        assertEquals(9, addXp(data, ALICE, bars(), PERFECT_XP), "a perfect forge pays five");
        assertEquals(XP_TO_UNLOCK, addXp(data, ALICE, bars(), PERFECT_XP),
                "the total stops at the amount that unlocks, however much more is added");
        assertEquals(XP_TO_UNLOCK, addXp(data, ALICE, bars(), PERFECT_XP), "and stays there");
        assertTrue(!data.isUnlocked(ALICE, bars()),
                "experience alone does not unlock: filling the bar and unlocking are separate steps");
    }

    /**
     * The bar is the smith's own, so the store cannot cap at one fixed amount: a beginner forges against
     * three times the shortest bar, and progress past ten is real progress for them. Only the caller knows
     * which bar applies, which is why it is handed in - and why a figure above every bar is a hand edit.
     */
    private static void aLongerBarHoldsMoreThanTheShortestOne() {
        PerfectForgeData data = fresh();
        assertEquals(XP_TO_UNLOCK, data.addXp(ALICE, bars(), XP_TO_UNLOCK, LONGEST_BAR),
                "a smith on the longest bar is not stopped at the shortest one");
        assertEquals(XP_TO_UNLOCK + PERFECT_XP, data.addXp(ALICE, bars(), PERFECT_XP, LONGEST_BAR),
                "and goes on earning past it");
        assertEquals(LONGEST_BAR, data.addXp(ALICE, bars(), LONGEST_BAR, LONGEST_BAR),
                "up to the bar they are actually forging against");
        assertEquals(LONGEST_BAR, reload(data).progress(ALICE, bars()).xp(), "and it survives a reload");
        assertTrue(!data.isUnlocked(ALICE, bars()),
                "a full bar is still not an unlock on its own: the forge unlocks, not the total");

        CompoundTag above = new CompoundTag();
        above.putInt(XP_KEY, 5 * LONGEST_BAR);
        CompoundTag recipes = new CompoundTag();
        recipes.put("tfc:anvil/bismuth_bronze_bars", above);
        assertEquals(LONGEST_BAR,
                PerfectForgeData.load(legacyTag(ALICE.toString(), recipes)).progress(ALICE, bars()).xp(),
                "a figure above every bar reads as the longest bar");
    }

    /**
     * The tally the manual writes on each row.
     *
     * <p>Kept apart from the experience on purpose: the experience reaches its cap and stops, so a recipe
     * forged twenty times would otherwise read as the same figure as one forged twice, and the whole point
     * of the number is to show which recipes a smith actually practises.
     */
    private static void forgesAreTalliedPastTheCap() {
        PerfectForgeData data = fresh();
        assertEquals(0, data.progress(ALICE, bars()).forged(), "an unrecorded recipe has never been forged");

        for (int i = 1; i <= 4; i++) {
            addXp(data, ALICE, bars(), PERFECT_XP);
            assertEquals(i, data.addForge(ALICE, bars()), "each forge counts, and the total is returned");
        }

        assertEquals(XP_TO_UNLOCK, data.progress(ALICE, bars()).xp(), "the experience is full");
        assertEquals(4, data.progress(ALICE, bars()).forged(), "the tally is not capped with it");
        assertEquals(0, data.progress(ALICE, shield()).forged(),
                "a forge of one recipe does not count towards another");
        assertEquals(4, reload(data).progress(ALICE, bars()).forged(), "the tally round-trips");
    }

    private static void xpAndUnlocksAreSeparate() {
        PerfectForgeData data = fresh();
        addXp(data, ALICE, bars(), 2);
        addXp(data, ALICE, bars(), 3);

        assertTrue(data.unlock(ALICE, bars()), "the first unlock changes the record");
        assertTrue(!data.unlock(ALICE, bars()), "unlocking an unlocked recipe changes nothing");
        assertEquals(5, data.progress(ALICE, bars()).xp(), "unlocking keeps the experience");
        assertTrue(data.isUnlocked(ALICE, bars()), "the unlock sticks");
        assertTrue(!data.isUnlocked(BOB, bars()), "one player's unlock is not another's");
    }

    private static void clearingOneRecipeForgetsBoth() {
        PerfectForgeData data = fresh();
        addXp(data, ALICE, bars(), 5);
        addXp(data, ALICE, shield(), 5);
        data.unlock(ALICE, bars());

        assertTrue(data.clear(ALICE, bars()), "clearing a recorded recipe changes something");
        assertTrue(!data.isUnlocked(ALICE, bars()), "the reset takes the unlock with it");
        assertEquals(0, data.progress(ALICE, bars()).xp(), "the reset takes the experience with it");
        assertEquals(1, data.snapshot(ALICE).size(), "the player's other recipe is untouched");

        assertTrue(!data.clear(ALICE, bars()), "clearing it again changes nothing");
        assertTrue(!data.clear(BOB, bars()), "clearing for a player with no record changes nothing");
    }

    private static void clearingEverythingForgetsThePlayer() {
        PerfectForgeData data = fresh();
        addXp(data, ALICE, bars(), 5);
        addXp(data, ALICE, shield(), 5);
        data.unlock(ALICE, shield());
        addXp(data, BOB, bars(), 5);

        assertEquals(2, data.clearAll(ALICE), "clearing everything reports how many recipes it removed");
        assertEquals(0, data.snapshot(ALICE).size(), "nothing is left for that player");
        assertTrue(!data.isUnlocked(ALICE, shield()), "unlocks are gone too");
        assertEquals(1, data.snapshot(BOB).size(), "another player keeps their record");
        assertEquals(0, data.clearAll(ALICE), "clearing an already empty record reports nothing");
        assertEquals(1, data.clearAll(BOB), "the other player can be cleared as well");
    }

    private static void aResetSurvivesAReload() {
        PerfectForgeData data = fresh();
        addXp(data, ALICE, bars(), 5);
        addXp(data, ALICE, bars(), 3);
        data.addForge(ALICE, bars());
        data.addForge(ALICE, bars());
        data.addForge(ALICE, bars());
        data.unlock(ALICE, bars());
        addXp(data, ALICE, shield(), 1);
        data.addForge(ALICE, shield());
        addXp(data, BOB, bars(), 2);
        data.addForge(BOB, bars());

        PerfectForgeData before = reload(data);
        assertEquals(8, before.progress(ALICE, bars()).xp(), "the experience round-trips");
        assertTrue(before.isUnlocked(ALICE, bars()), "the unlock round-trips");
        assertEquals(3, before.progress(ALICE, bars()).forged(), "the tally round-trips");

        data.clear(ALICE, bars());
        data.clearAll(BOB);

        PerfectForgeData after = reload(data);
        assertEquals(1, after.snapshot(ALICE).size(), "only the untouched recipe is written back");
        assertTrue(!after.isUnlocked(ALICE, bars()), "a cleared recipe does not come back unlocked");
        assertEquals(0, after.progress(ALICE, bars()).xp(), "cleared experience does not come back");
        assertEquals(0, after.progress(ALICE, bars()).forged(), "a cleared tally does not come back either");
        assertEquals(1, after.progress(ALICE, shield()).forged(),
                "the player's other tally is untouched by the reset");
        assertEquals(0, after.snapshot(BOB).size(), "a fully cleared player is not written at all");
    }

    private static void legacyBareCountsReadAsExperience() {
        CompoundTag recipes = new CompoundTag();
        recipes.putInt("tfc:anvil/bismuth_bronze_bars", 1);
        recipes.putInt("tfc:anvil/bismuth_bronze_shield", 2);
        PerfectForgeData loaded = PerfectForgeData.load(legacyTag(ALICE.toString(), recipes));

        assertEquals(PERFECT_XP, loaded.progress(ALICE, bars()).xp(),
                "an old count reads as the experience those forges would have earned");
        assertTrue(!loaded.isUnlocked(ALICE, bars()), "one old perfect forge is not yet enough");
        assertEquals(XP_TO_UNLOCK, loaded.progress(ALICE, shield()).xp(),
                "two old perfect forges fill the bar");
        assertTrue(loaded.isUnlocked(ALICE, shield()),
                "and that count was written against the bar it fills, so it stays unlocked");
        assertEquals(2, loaded.progress(ALICE, shield()).forged(),
                "an old count was a count of forges, so it still is one");
    }

    private static void legacyNestedCountsConvertToExperience() {
        CompoundTag record = new CompoundTag();
        record.putInt(LEGACY_COUNT_KEY, 3);

        CompoundTag recipes = new CompoundTag();
        recipes.put("tfc:anvil/bismuth_bronze_bars", record);

        CompoundTag shieldRecord = new CompoundTag();
        shieldRecord.putInt(LEGACY_COUNT_KEY, 1);
        shieldRecord.putBoolean(UNLOCKED_KEY, true);
        recipes.put("tfc:anvil/bismuth_bronze_shield", shieldRecord);

        PerfectForgeData loaded = PerfectForgeData.load(legacyTag(ALICE.toString(), recipes));
        assertEquals(3 * PERFECT_XP, loaded.progress(ALICE, bars()).xp(),
                "a nested count converts the same way, at five points per forge");
        assertTrue(loaded.isUnlocked(ALICE, bars()),
                "an entry that converts past the bar it was written against reads as unlocked");
        assertTrue(loaded.isUnlocked(ALICE, shield()),
                "an unlock recorded by hand survives a count that would not have earned it");

        // The current shape wins outright: a record that has both keys is not converted from the count.
        CompoundTag both = new CompoundTag();
        both.putInt(XP_KEY, 1);
        both.putInt(LEGACY_COUNT_KEY, 5);
        CompoundTag onlyOne = new CompoundTag();
        onlyOne.put("tfc:anvil/bismuth_bronze_bars", both);
        assertEquals(1, PerfectForgeData.load(legacyTag(ALICE.toString(), onlyOne)).progress(ALICE, bars()).xp(),
                "experience is read as-is when the record already has it");
        assertEquals(0, PerfectForgeData.load(legacyTag(ALICE.toString(), onlyOne))
                        .progress(ALICE, bars()).forged(),
                "a record written before the tally existed reads as never forged, not as its count");

        // A record written since then carries the tally, and it survives alongside the experience.
        CompoundTag current = new CompoundTag();
        current.putInt(XP_KEY, 2);
        current.putBoolean(UNLOCKED_KEY, true);
        current.putInt(FORGED_KEY, 7);
        CompoundTag carriesTally = new CompoundTag();
        carriesTally.put("tfc:anvil/bismuth_bronze_bars", current);
        assertEquals(7, PerfectForgeData.load(legacyTag(ALICE.toString(), carriesTally))
                        .progress(ALICE, bars()).forged(),
                "a tally on disk is read back as written");
    }

    /**
     * Experience alone cannot say whether a recipe is unlocked, because the bar it is measured against
     * depends on the smith: ten points is a full bar for one at {@code SHORTCUT_SKILL_LEVEL} and a third of
     * one for a beginner. So a current record carries the answer with it, and only a count converted from
     * the rule every smith once shared is allowed to read an unlock out of what it converts to.
     */
    private static void experienceAloneDoesNotReadAsAnUnlock() {
        CompoundTag record = new CompoundTag();
        record.putInt(XP_KEY, XP_TO_UNLOCK);
        CompoundTag recipes = new CompoundTag();
        recipes.put("tfc:anvil/bismuth_bronze_bars", record);

        PerfectForgeData loaded = PerfectForgeData.load(legacyTag(ALICE.toString(), recipes));
        assertEquals(XP_TO_UNLOCK, loaded.progress(ALICE, bars()).xp(), "the figure is read as written");
        assertTrue(!loaded.isUnlocked(ALICE, bars()),
                "a figure that would have filled the shortest bar is not an unlock");

        CompoundTag marked = new CompoundTag();
        marked.putInt(XP_KEY, XP_TO_UNLOCK);
        marked.putBoolean(UNLOCKED_KEY, true);
        CompoundTag markedRecipes = new CompoundTag();
        markedRecipes.put("tfc:anvil/bismuth_bronze_bars", marked);
        assertTrue(PerfectForgeData.load(legacyTag(ALICE.toString(), markedRecipes)).isUnlocked(ALICE, bars()),
                "the unlock the record actually carries is the one that counts");
    }

    private static void unreadableEntriesAreSkipped() {
        CompoundTag recipes = new CompoundTag();
        recipes.putInt("tfc:anvil/Bad", 9);
        recipes.putInt("tfc:anvil/ok", 2);
        PerfectForgeData loaded = PerfectForgeData.load(legacyTag(ALICE.toString(), recipes));

        assertEquals(1, loaded.snapshot(ALICE).size(), "a recipe id that cannot be parsed is skipped");
        assertEquals(2 * PERFECT_XP, loaded.progress(ALICE, id("tfc:anvil/ok")).xp(),
                "the readable entry survives its broken neighbour");

        CompoundTag players = new CompoundTag();
        players.put("not-a-uuid", recipes.copy());
        CompoundTag tag = new CompoundTag();
        tag.put(PLAYERS_KEY, players);
        assertEquals(0, PerfectForgeData.load(tag).snapshot(ALICE).size(),
                "a player id that cannot be parsed is skipped");
    }

    /**
     * The reports switch is a preference rather than a record, and it is stored like one all the same.
     *
     * <p>It decides whether the forge says what a forge earned, which is something a player asked for and
     * expects to still be true tomorrow, so it has to survive a restart exactly as the record does. What it
     * does not have to survive is a reset: that asks for a recipe the player has never touched, not for a
     * player who has stopped listening. A save written before the switch existed has no list at all, and a
     * missing list reads as off for everyone rather than as on for anyone.
     */
    private static void theReportsSwitchOutlivesTheRecordAndTheReset() {
        PerfectForgeData data = fresh();
        assertTrue(!data.reports(ALICE), "nothing is reported to a player who has never asked");

        assertTrue(data.setReports(ALICE, true), "asking for reports changes something");
        assertTrue(data.reports(ALICE), "and that player is reported to afterwards");
        assertTrue(!data.reports(BOB), "one player asking does not turn it on for another");
        assertTrue(!data.setReports(ALICE, true), "asking twice for the same thing changes nothing");
        assertTrue(!data.setReports(BOB, false), "turning off what was never on changes nothing");

        PerfectForgeData reloaded = reload(data);
        assertTrue(reloaded.reports(ALICE), "the switch round-trips through the save file");
        assertTrue(!reloaded.reports(BOB), "and takes nobody else with it");
        assertEquals(1, data.save(new CompoundTag()).getList(REPORTS_KEY, Tag.TAG_STRING).size(),
                "the switch is written as a list of the players who asked");

        addXp(data, ALICE, bars(), PERFECT_XP);
        assertEquals(1, data.clearAll(ALICE), "the record is cleared");
        assertTrue(reload(data).reports(ALICE), "but clearing a record leaves the switch alone");
        assertTrue(!reload(data).progress(ALICE, bars()).unlocked(),
                "and nothing of the record it was cleared with comes back");

        assertTrue(!PerfectForgeData.load(legacyTag(ALICE.toString(), new CompoundTag())).reports(ALICE),
                "a save written before the switch existed reports to nobody");
    }

    /** A record built the way the game builds one, without a server: an empty tag is an empty record. */
    private static PerfectForgeData fresh() {
        return PerfectForgeData.load(new CompoundTag());
    }

    /**
     * {@code addXp} against the shortest bar, which is what a smith at {@code SHORTCUT_SKILL_LEVEL} or above
     * forges against. That is where the store's own rules are pinned; the longer bars are their own check.
     */
    private static int addXp(PerfectForgeData data, UUID player, ResourceLocation recipe, int xp) {
        return data.addXp(player, recipe, xp, XP_TO_UNLOCK);
    }

    private static PerfectForgeData reload(PerfectForgeData data) {
        return PerfectForgeData.load(data.save(new CompoundTag()));
    }

    /** The oldest on-disk shape: a player table of recipe ids to bare counts, with no nested record. */
    private static CompoundTag legacyTag(String player, CompoundTag recipes) {
        CompoundTag players = new CompoundTag();
        players.put(player, recipes);
        CompoundTag tag = new CompoundTag();
        tag.put(PLAYERS_KEY, players);
        return tag;
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

    private static void assertTrue(boolean value, String what) {
        if (!value) {
            throw new AssertionError(what);
        }
    }
}
