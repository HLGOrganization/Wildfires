package first.wildfires.smithing;

import first.wildfires.compat.moreattributes.SkillLevels;
import first.wildfires.mixin.tfc.InventoryBlockEntityAccessor;
import first.wildfires.network.PerfectForgeSyncPacket;
import net.dries007.tfc.common.blockentities.AnvilBlockEntity;
import net.dries007.tfc.common.capabilities.forge.ForgeStep;
import net.dries007.tfc.common.capabilities.forge.Forging;
import net.dries007.tfc.common.capabilities.forge.ForgingBonus;
import net.dries007.tfc.common.capabilities.forge.ForgingCapability;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.dries007.tfc.common.recipes.AnvilRecipe;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.util.Helpers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.List;
import java.util.UUID;

/**
 * The rules behind the anvil's one-click forge: how it unlocks, and what it does.
 *
 * <h2>Unlocking</h2>
 * Every completed forge of a recipe earns experience on that recipe alone, and enough of it unlocks its
 * one-click forge for good. How much a forge is worth comes from the quality TFC gave the result
 * ({@link #xpFor}): a perfectly forged item is worth the most, a merely forged one the least, and a recipe
 * that carries no quality at all pays a flat point.
 *
 * <p>How much is enough depends on the smith's "技巧" level in More Attributes (see {@link SkillLevels}),
 * because the roll that shortcuts the bar is closed to a smith below {@value #SHORTCUT_SKILL_LEVEL}, and
 * those levels have to work for it ({@link #xpToUnlock}):
 *
 * <pre>
 *     bar = {@value #XP_TO_UNLOCK} +
 *           {@value #XP_PER_MISSING_SKILL_LEVEL} x levels below {@value #SHORTCUT_SKILL_LEVEL}
 * </pre>
 *
 * <p>So a smith at {@value #SHORTCUT_SKILL_LEVEL} or above needs {@value #XP_TO_UNLOCK} - two perfectly
 * forged items, or ten of anything less - while one starting out at level zero needs thirty, and every
 * level of skill earned on the way up shortens the bar by two. Experience only ever goes up, so a recipe is
 * never further from unlocking than it was.
 *
 * <p>Each forge also rolls to unlock the recipe outright, which is how a smith skips the rest of the bar.
 * The odds come from the same level:
 *
 * <pre>
 *     chance = (skillLevel - {@value #SHORTCUT_SKILL_LEVEL}) x {@value #UNLOCK_CHANCE_PER_SKILL_LEVEL}
 * </pre>
 *
 * <p>So the level the long bar ends at is the level the odds start at: below it the only way to an unlocked
 * recipe is working the bar, above it every forge is a chance to be handed the recipe outright.
 * The odds are said out loud along with the running total, for the players who asked to hear them
 * ({@link #reportsProgress}).
 *
 * <h2>One-click forge</h2>
 * Pressing the button on an unlocked recipe strikes {@link PerfectForgePlan the recipe's own sequence} on
 * the anvil - the fewest blows that finish it and satisfy its rules - one blow every
 * {@link PerfectForgeSequence#PERFECT_TICKS} ticks. Nothing is handed over and nothing is stamped: the blows
 * are handed to the anvil, which builds the item when the work is finished and grades it by how many blows
 * that took. Because the sequence is never shorter than TFC's own estimate of the fewest needed, the grade
 * comes out perfect by TFC's own reckoning.
 *
 * <p>A plan belongs to the piece that is on the anvil rather than to the recipe, so a run that was
 * interrupted is picked up where it stopped: the blows the piece has already taken are counted against the
 * sequence, and only the work that is left is asked for ({@link PerfectForgePlan}). A fresh piece is that
 * same case with nothing taken off the front.
 *
 * <p>The cost is one hammer durability per blow, the anvil's own price, and every gate the anvil itself
 * applies is kept: there must be a recipe, the metal must be hot enough to work, and a hammer must be
 * present and last the whole run. Because the blows are real anvil strikes they earn what a smith's blows
 * earn - experience towards unlocking, the unlock roll, and TFC's perfectly-forged advancement - and they
 * replace the recipe's memory with the sequence they struck.
 *
 * <h2>Memory forge</h2>
 * Before a recipe is unlocked, the same button repeats the sequence its smith last finished there: one blow
 * every {@link PerfectForgeSequence#MEMORY_TICKS} ticks, struck through the anvil's own work method, so
 * every rule the anvil applies is still applied and the item is built by those blows as it would be by hand
 * ({@link ForgeMemory}).
 *
 * <p>Unlocking replaces it rather than adding to it, as the recipe's own sequence is at least as good as
 * anything a smith struck by hand - there is one button, and this is what it does at each stage.
 */
public final class PerfectForgeService {

    /**
     * Forging experience a recipe needs before its one-click forge unlocks, for a smith at
     * {@value #SHORTCUT_SKILL_LEVEL} or above.
     *
     * <p>Sized against the experience a forge pays ({@link #xpFor}): two perfectly forged items are enough,
     * a merely forged one takes ten, and so does a recipe with no quality at all, which can only ever pay
     * one point. A smith below that level forges against a longer bar for as long as they are
     * ({@link #xpToUnlock}).
     */
    public static final int XP_TO_UNLOCK = 10;

    /** The skill level both rules are measured from: below it the bar is longer, at it the odds are zero. */
    public static final int SHORTCUT_SKILL_LEVEL = 10;

    /** How much each skill level above {@link #SHORTCUT_SKILL_LEVEL} adds to the unlock probability. */
    public static final double UNLOCK_CHANCE_PER_SKILL_LEVEL = 0.04D;

    /** How much longer the bar is for each skill level a smith is below {@link #SHORTCUT_SKILL_LEVEL}. */
    public static final int XP_PER_MISSING_SKILL_LEVEL = 2;

    private PerfectForgeService() {
    }

    /** The per-forge unlock probability at a given skill level. Zero at {@value #SHORTCUT_SKILL_LEVEL}. */
    public static double unlockChance(int skillLevel) {
        return (skillLevel - SHORTCUT_SKILL_LEVEL) * UNLOCK_CHANCE_PER_SKILL_LEVEL;
    }

    /**
     * The forging experience a recipe needs before its one-click forge unlocks for a smith of this skill
     * level: {@value #XP_TO_UNLOCK}, plus {@value #XP_PER_MISSING_SKILL_LEVEL} for every level short of
     * {@value #SHORTCUT_SKILL_LEVEL}.
     *
     * <p>Below that level the roll for the recipe is closed ({@link #unlockChance}), so the bar is the only
     * way to one and it is that much longer: thirty points for a smith who has never forged before, which is
     * six perfectly forged items or thirty forges of a recipe with no quality to grade. Every level of skill
     * closes two points of the gap, and at {@value #SHORTCUT_SKILL_LEVEL} the bar is back to its shortest.
     */
    public static int xpToUnlock(int skillLevel) {
        return XP_TO_UNLOCK + Math.max(0, SHORTCUT_SKILL_LEVEL - skillLevel) * XP_PER_MISSING_SKILL_LEVEL;
    }

    /**
     * The forging experience one completed forge is worth, whatever the smith's skill level.
     *
     * <p>It follows the quality TFC stamped on the result - the better the work, the more it pays - which
     * the enum happens to order from worst to best, so its ordinal is the tier. A recipe that carries no
     * quality at all has nothing to grade and pays a flat point, the same as the worst graded result.
     *
     * @param quality {@code ForgingBonus.ordinal() + 1}, or zero for a result with no quality to grade
     */
    public static int xpFor(int quality) {
        return Math.max(1, quality);
    }

    /** {@link #xpFor(int)} for the quality TFC graded a result at, where {@code null} means ungraded. */
    public static int xpFor(ForgingBonus bonus) {
        return xpFor(bonus == null ? 0 : bonus.ordinal() + 1);
    }

    /**
     * Records one completed forge: adds its experience, counts it, rolls its unlock, and tells the client.
     *
     * <p>Called from the anvil, for every recipe that reaches an output - whatever quality came out of it.
     * The bookkeeping happens for everyone; nothing is said out loud unless the player asked for these
     * reports, which are off until they ask ({@link #reportsProgress}). The count is the manual's
     * business rather than a rule's, and goes up by one whatever quality the forge came out at.
     *
     * @param bonus the quality of the forged result, or {@code null} for a recipe without one
     */
    public static void recordForge(ServerPlayer player, AnvilBlockEntity anvil, ForgingBonus bonus) {
        MinecraftServer server = player.getServer();
        Level level = anvil.getLevel();
        if (server == null || level == null) {
            return;
        }

        Forging forging = anvil.getMainInputForging();
        if (forging == null) {
            return;
        }
        AnvilRecipe recipe = forging.getRecipe(level);
        if (recipe == null) {
            return;
        }

        UUID playerId = player.getUUID();
        ResourceLocation recipeId = recipe.getId();
        PerfectForgeData data = PerfectForgeData.get(server);
        int skillLevel = SkillLevels.of(player);
        int bar = xpToUnlock(skillLevel);
        int earned = xpFor(bonus);
        int xp = data.addXp(playerId, recipeId, earned, bar);
        data.addForge(playerId, recipeId);

        boolean reports = reportsProgress(player);

        if (!data.isUnlocked(playerId, recipeId)) {
            // The bar filling is what usually unlocks a recipe; the roll is the shortcut that can hand it
            // over early, which is why the odds are nothing right up to the level the long bar ends at - a
            // smith working the longest bar is a smith who cannot be handed the recipe.
            if (xp >= bar || rolledUnlock(skillLevel, player.getRandom())) {
                data.unlock(playerId, recipeId);
                if (reports) {
                    player.displayClientMessage(Component.translatable("wildfires.smithing.unlocked",
                            recipe.getResultItem(level.registryAccess()).getHoverName()), false);
                }
            } else if (reports) {
                // Otherwise say what the forge earned, for the smith who asked to hear it: the odds are
                // worked out here and nowhere else, so this message is the only place they are ever said.
                player.displayClientMessage(Component.translatable("wildfires.smithing.xp",
                        earned, xp, bar,
                        (int) Math.round(Math.max(0D, unlockChance(skillLevel)) * 100D)), true);
            }
        }

        PerfectForgeSyncPacket.sendTo(player, data.snapshot(playerId));
    }

    /**
     * Whether the forge should say anything to this player about what a forge earned them.
     *
     * <p>The running total towards unlocking a recipe, the odds the level hands over, and the announcement
     * that a recipe has just unlocked are a smith's bookkeeping rather than part of forging, and out in the
     * world they are noise on every finished item. So they are off for everyone - creative mode included,
     * which is a way of building rather than a way of hearing more - until
     * {@code /wildfires smithing messages on} asks for them ({@link PerfectForgeData#reports}).
     *
     * <p>Refusals are not this: a click that could not be carried out says why, whoever the player is, so
     * that a button which does nothing is never indistinguishable from one that is broken.
     */
    private static boolean reportsProgress(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server != null && PerfectForgeData.get(server).reports(player.getUUID());
    }

    private static boolean rolledUnlock(int skillLevel, RandomSource random) {
        double chance = unlockChance(skillLevel);
        // A non-positive chance cannot be rolled, however the random comes out.
        return chance > 0 && random.nextDouble() < chance;
    }

    /**
     * Runs the anvil's one-click forge button against whatever recipe the anvil is set to.
     *
     * <p>Two things can come of a click, and the recipe alone decides which. A recipe whose one-click forge
     * is unlocked has its own sequence struck on the anvil - the blows that finish it and satisfy its rules,
     * worked out from the recipe (see {@link PerfectForgePlan}). A recipe that is not unlocked yet instead
     * has the sequence its smith last finished there struck again. Either way the button does the striking
     * rather than handing over the result, and pays the anvil's usual single point of durability per blow.
     *
     * <p>Neither produces anything here, and every gate is answered at the click rather than during the
     * run: that is what makes a click that cannot forge fail immediately with a reason instead of after a
     * run of noise. Every failure says why in chat, because the button is one click and a silent no-op is
     * indistinguishable from a broken one.
     */
    public static void tryPerfectForge(ServerPlayer player, AnvilBlockEntity anvil) {
        Level level = anvil.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        if (!(anvil instanceof InventoryBlockEntityAccessor<?> accessor)
                || !(accessor.getInventory() instanceof AnvilBlockEntity.AnvilInventory inventory)) {
            return;
        }

        ItemStack input = inventory.getStackInSlot(AnvilBlockEntity.SLOT_INPUT_MAIN);
        Forging forging = ForgingCapability.get(input);
        AnvilRecipe recipe = forging == null ? null : forging.getRecipe(level);
        if (recipe == null) {
            fail(player, "wildfires.smithing.perfect.no_recipe");
            return;
        }

        UUID playerId = player.getUUID();
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        PerfectForgeData data = PerfectForgeData.get(server);
        boolean unlocked = data.isUnlocked(playerId, recipe.getId());
        // A run works on whatever piece the anvil is holding, from wherever that piece stands: its blows count
        // against what is left to strike, and its work is where the run begins. A piece left part worked by an
        // interrupted run - or by the smith's own hand - is therefore finished by clicking again, and only a
        // piece that neither kind of run can make anything of is refused.
        List<ForgeStep> strikes;
        if (unlocked) {
            strikes = PerfectForgePlan.of(recipe, inventory, forging);
            if (strikes.isEmpty()) {
                // Either TFC's own estimate of the fewest blows is runaway or out of reach of anything the
                // anvil can strike, or this piece cannot be brought onto the recipe's target from where it
                // stands - struck past what the recipe needs, or worked somewhere the blows cannot come back
                // from. A fresh piece has only the first of those to blame, and says so differently.
                fail(player, forging.getSteps().total() > 0
                        ? "wildfires.smithing.perfect.stranded"
                        : "wildfires.smithing.perfect.unsolvable");
                return;
            }
        } else {
            // The sequence is only wanted while the recipe is locked - an unlocked one strikes its own - so
            // it is not even read once the button is going to do the recipe's own sequence.
            List<ForgeStep> remembered = ForgeMemory.remembered(server, playerId, recipe.getId());
            if (remembered.isEmpty()) {
                // Nothing has been finished here by hand yet, so there is nothing to repeat. Unlocking is
                // what this click was really asking for, so the recipe's running total comes with the
                // refusal - but only for a player who asked for the forge's reports, like every other total.
                Component refusal = Component.translatable("wildfires.smithing.memory.none");
                if (reportsProgress(player)) {
                    refusal = refusal.copy().append(Component.translatable("wildfires.smithing.memory.none_xp",
                            data.progress(playerId, recipe.getId()).xp(), xpToUnlock(SkillLevels.of(player))));
                }
                fail(player, refusal);
                return;
            }
            // Only the blows still to come: a piece that is the first blows of the remembered sequence has
            // already done the rest of it, and is finished here from where it stopped.
            strikes = ForgeMemory.remaining(remembered, forging);
            if (strikes == null) {
                fail(player, "wildfires.smithing.memory.dirty");
                return;
            }
        }
        // Read before the first blow lands: it is the strike count the anvil is expected to be at while the
        // run is under way, which is what tells a piece that has been taken off the anvil from one that has
        // simply been struck by this run.
        int alreadyStruck = forging.getSteps().total();

        // TFC only rejects a cold input when a heat capability is actually present - a null result from
        // HeatCapability.get skips the check entirely in AnvilBlockEntity#work, so an input that carries
        // no heat at all must still be forgeable here.
        IHeat heat = HeatCapability.get(input);
        if (heat != null && !heat.canWork()) {
            fail(player, "wildfires.smithing.perfect.cold");
            return;
        }

        ItemStack hammer = findHammer(player, inventory);
        if (hammer == null) {
            fail(player, "wildfires.smithing.perfect.hammer");
            return;
        }
        // Checked before any wear is applied, so a hammer that cannot take the whole run is left alone
        // rather than broken part way through a forge that then fails. One point per blow, which is what
        // TFC charges a smith striking by hand.
        int cost = strikes.size();
        if (hammer.getMaxDamage() - hammer.getDamageValue() < cost) {
            fail(player, "wildfires.smithing.perfect.durability", cost);
            return;
        }

        // End of the gates: the hammering takes over from here and builds the item when it is done.
        if (!PerfectForgeSequence.start(player, anvil, recipe, alreadyStruck, strikes, unlocked)) {
            fail(player, "wildfires.smithing.perfect.busy");
        }
    }

    /**
     * The anvil's own hammer slot, or else whichever hand is holding one - the same order TFC uses, so a
     * hammer that works for a normal strike also works here.
     */
    private static ItemStack findHammer(ServerPlayer player, IItemHandlerModifiable inventory) {
        ItemStack hammer = inventory.getStackInSlot(AnvilBlockEntity.SLOT_HAMMER);
        if (hammer.isEmpty()) {
            hammer = player.getMainHandItem();
        }
        if (hammer.isEmpty()) {
            hammer = player.getOffhandItem();
        }
        if (hammer.isEmpty() || !Helpers.isItem(hammer, TFCTags.Items.HAMMERS)) {
            return null;
        }
        return hammer;
    }

    private static void fail(ServerPlayer player, String key, Object... args) {
        fail(player, Component.translatable(key, args));
    }

    private static void fail(ServerPlayer player, Component message) {
        player.displayClientMessage(message, true);
    }
}
