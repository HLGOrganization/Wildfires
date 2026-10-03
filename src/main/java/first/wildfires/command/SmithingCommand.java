package first.wildfires.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import first.wildfires.compat.moreattributes.SkillLevels;
import first.wildfires.network.PerfectForgeSyncPacket;
import first.wildfires.smithing.ForgeMemoryData;
import first.wildfires.smithing.PerfectForgeData;
import first.wildfires.smithing.PerfectForgeProgress;
import first.wildfires.smithing.PerfectForgeService;
import net.dries007.tfc.common.recipes.AnvilRecipe;
import net.dries007.tfc.common.recipes.TFCRecipeTypes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

/**
 * Administrative commands over one player's one-click forge unlocks.
 *
 * <p>Unlocking normally is a matter of forging a recipe until it has earned enough experience, at a chance
 * that rises with the player's smithing skill
 * ({@link first.wildfires.smithing.PerfectForgeService}). That is the right
 * pace for play and the wrong pace for anyone who needs a recipe unlocked <em>now</em>: to look at what the
 * anvil's one-click button does, to check the forging manual's layout against a full list, or to restore a
 * record that a bad save lost. These commands write the same record the anvil writes, so nothing downstream
 * can tell the difference - the only thing they skip is the forging itself.
 *
 * <p>Everything is per-player and self-targeted: the record lives on the server
 * ({@link PerfectForgeData}) and belongs to whoever ran the command. A changed record is pushed to that
 * player's client straight away, because the client's copy is only ever updated by a packet - without that
 * the manual and the anvil button would keep showing the old state until the next login.
 *
 * <p>The reset subcommands clear what a player has done on a recipe, which is two things: the progress
 * towards unlocking its one-click forge ({@link PerfectForgeData}) and the sequence they last worked it with
 * ({@link ForgeMemoryData}). Both go, because what a reset asks for is a recipe the player has never touched,
 * and the remembered sequence is exactly the shortcut that would still be there otherwise.
 *
 * <p>The messages switch is the one thing here that is not a record but a preference, and it lives in the
 * same file for the same reason: it is per player and it has to outlive death and a restart. It turns on
 * the forge's own reports - the running total towards an unlock, the odds of a shortcut unlock, and the
 * announcement of an unlock - for one player. They are off for everyone who has not asked, creative mode
 * included, so this command is the only way to hear them
 * ({@link first.wildfires.smithing.PerfectForgeService}).
 *
 * <p>The recipe argument is the recipe id TFC registers, {@code tfc:anvil/...}, offered by tab completion
 * from the server's own recipe manager. Ids are validated against that same manager, so a typo is refused
 * rather than written into the save as a record that can never match anything.
 */
public final class SmithingCommand {

    /** Recipes listed by {@code list} before it starts summarising. A full unlock is 278 of them. */
    private static final int LIST_LIMIT = 24;

    private static final int LIST_PER_LINE = 4;

    private SmithingCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> createWildfiresBranch() {
        return Commands.literal("smithing")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("unlock")
                        .then(Commands.literal("all").executes(SmithingCommand::unlockAll))
                        .then(Commands.argument("recipe", StringArgumentType.string())
                                .suggests(SmithingCommand::suggestRecipes)
                                .executes(SmithingCommand::unlockOne)))
                .then(Commands.literal("reset")
                        .then(Commands.literal("all").executes(SmithingCommand::resetAll))
                        .then(Commands.argument("recipe", StringArgumentType.string())
                                .suggests(SmithingCommand::suggestRecipes)
                                .executes(SmithingCommand::resetOne)))
                .then(Commands.literal("list").executes(SmithingCommand::list))
                .then(Commands.literal("messages")
                        .executes(SmithingCommand::showMessages)
                        .then(Commands.literal("on").executes(context -> setMessages(context, true)))
                        .then(Commands.literal("off").executes(context -> setMessages(context, false))));
    }

    private static int unlockOne(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return 0;
        }
        ResourceLocation recipe = validate(context, server);
        if (recipe == null) {
            return 0;
        }
        PerfectForgeData data = PerfectForgeData.get(server);
        if (!data.unlock(player.getUUID(), recipe)) {
            // Already unlocked is a success with nothing to do, not an error: the caller asked for a state
            // the world is already in.
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.wildfires.smithing.already", recipe.toString()), false);
            return Command.SINGLE_SUCCESS;
        }
        sync(player, data);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.wildfires.smithing.unlocked", recipe.toString()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int unlockAll(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        MinecraftServer server = player.getServer();
        Set<ResourceLocation> recipes = anvilRecipes(server);
        PerfectForgeData data = PerfectForgeData.get(server);
        int unlocked = 0;
        for (ResourceLocation recipe : recipes) {
            if (data.unlock(player.getUUID(), recipe)) {
                unlocked++;
            }
        }
        sync(player, data);
        int changed = unlocked;
        int total = recipes.size();
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.wildfires.smithing.unlocked_all", changed, total), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int resetOne(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        ResourceLocation recipe = player == null ? null : validate(context, player.getServer());
        if (recipe == null) {
            return 0;
        }
        MinecraftServer server = player.getServer();
        PerfectForgeData data = PerfectForgeData.get(server);
        boolean cleared = data.clear(player.getUUID(), recipe);
        boolean forgotten = ForgeMemoryData.get(server).forget(player.getUUID(), recipe);
        if (!cleared && !forgotten) {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.wildfires.smithing.nothing", recipe.toString()), false);
            return 0;
        }
        sync(player, data);
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.wildfires.smithing.cleared", recipe.toString()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int resetAll(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        MinecraftServer server = player.getServer();
        PerfectForgeData data = PerfectForgeData.get(server);
        ForgeMemoryData memories = ForgeMemoryData.get(server);
        // Counted before the reset, and as a union: a recipe that had either progress or a remembered
        // sequence is one the player can no longer find anything on.
        Set<ResourceLocation> touched = new TreeSet<>(data.snapshot(player.getUUID()).keySet());
        touched.addAll(memories.recipes(player.getUUID()));
        int cleared = data.clearAll(player.getUUID());
        int forgotten = memories.forgetAll(player.getUUID());
        if (cleared == 0 && forgotten == 0) {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.wildfires.smithing.nothing_at_all"), false);
            return 0;
        }
        sync(player, data);
        int count = touched.size();
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.wildfires.smithing.cleared_all", count), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        MinecraftServer server = player.getServer();
        int total = anvilRecipes(server).size();

        // The snapshot is already a copy, so the walk below cannot see a change made mid-print.
        Map<ResourceLocation, PerfectForgeProgress> progress = PerfectForgeData.get(server)
                .snapshot(player.getUUID());
        List<Map.Entry<ResourceLocation, PerfectForgeProgress>> unlocked = new ArrayList<>();
        for (Map.Entry<ResourceLocation, PerfectForgeProgress> entry : progress.entrySet()) {
            if (entry.getValue().unlocked()) {
                unlocked.add(entry);
            }
        }
        unlocked.sort(Map.Entry.comparingByKey());

        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.wildfires.smithing.list_header", unlocked.size(), total), false);

        StringBuilder line = new StringBuilder();
        // The bar these entries were filled against, which is this player's own.
        int bar = PerfectForgeService.xpToUnlock(SkillLevels.of(player));
        for (int index = 0; index < unlocked.size() && index < LIST_LIMIT; index++) {
            if (index > 0 && index % LIST_PER_LINE == 0) {
                context.getSource().sendSuccess(() -> Component.literal(line.toString()), false);
                line.setLength(0);
            } else if (line.length() > 0) {
                line.append(", ");
            }
            Map.Entry<ResourceLocation, PerfectForgeProgress> entry = unlocked.get(index);
            line.append(Component.translatable("commands.wildfires.smithing.list_entry",
                    entry.getKey().toString(), entry.getValue().xp(), bar).getString());
        }
        if (line.length() > 0) {
            context.getSource().sendSuccess(() -> Component.literal(line.toString()), false);
        }
        if (unlocked.size() > LIST_LIMIT) {
            int remaining = unlocked.size() - LIST_LIMIT;
            context.getSource().sendSuccess(() -> Component.translatable(
                    "commands.wildfires.smithing.list_more", remaining), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Turns the forge's reports on or off for the player who ran it.
     *
     * <p>Self-targeted like everything else here: what it gates is that player's own figures - their
     * running total, their odds - and it is their chat it would fill.
     */
    private static int setMessages(CommandContext<CommandSourceStack> context, boolean on) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return 0;
        }
        boolean changed = PerfectForgeData.get(server).setReports(player.getUUID(), on);
        context.getSource().sendSuccess(() -> Component.translatable(messagesAnswer(on, changed)), true);
        return Command.SINGLE_SUCCESS;
    }

    /** Says whether the forge's reports are on for this player, for a bare {@code messages}. */
    private static int showMessages(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return 0;
        }
        boolean on = PerfectForgeData.get(server).reports(player.getUUID());
        context.getSource().sendSuccess(() -> Component.translatable(on
                ? "commands.wildfires.smithing.messages_state_on"
                : "commands.wildfires.smithing.messages_state_off"), false);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * The answer to a switch that has just been asked for: what it is now, or that it already was.
     *
     * <p>Both are successes - the caller asked for a state and the world is in it - so the difference is
     * worth a word rather than an error. Each answer is written out whole rather than assembled from a
     * state and a sentence around it, because "already on" and "turned on" are not the same sentence in
     * every language.
     */
    private static String messagesAnswer(boolean on, boolean changed) {
        if (!changed) {
            return on ? "commands.wildfires.smithing.messages_was_on"
                    : "commands.wildfires.smithing.messages_was_off";
        }
        return on ? "commands.wildfires.smithing.messages_on"
                : "commands.wildfires.smithing.messages_off";
    }

    /** The command's executor, or a failure message when it came from a command block or the console. */
    private static ServerPlayer player(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.wildfires.smithing.players_only"));
        }
        return player;
    }

    /** The recipe argument, checked against the recipe manager so an unknown id is never written. */
    private static ResourceLocation validate(CommandContext<CommandSourceStack> context,
                                             MinecraftServer server) {
        String raw = StringArgumentType.getString(context, "recipe");
        ResourceLocation recipe = ResourceLocation.tryParse(raw);
        if (recipe == null) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.wildfires.smithing.bad_id", raw));
            return null;
        }
        if (!anvilRecipes(server).contains(recipe)) {
            // Unlocking something the manual cannot list would be a record that never shows up anywhere,
            // and resetting it would look like the command did nothing.
            context.getSource().sendFailure(Component.translatable(
                    "commands.wildfires.smithing.unknown_recipe", recipe.toString()));
            return null;
        }
        return recipe;
    }

    /** Every anvil recipe id, the same set the forging manual reads, in a stable order for output. */
    private static Set<ResourceLocation> anvilRecipes(MinecraftServer server) {
        Set<ResourceLocation> recipes = new TreeSet<>();
        for (AnvilRecipe recipe : server.getRecipeManager()
                .getAllRecipesFor(TFCRecipeTypes.ANVIL.get())) {
            recipes.add(recipe.getId());
        }
        return recipes;
    }

    private static CompletableFuture<Suggestions> suggestRecipes(CommandContext<CommandSourceStack> context,
                                                                SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                anvilRecipes(context.getSource().getServer()).stream().map(ResourceLocation::toString),
                builder);
    }

    private static void sync(ServerPlayer player, PerfectForgeData data) {
        PerfectForgeSyncPacket.sendTo(player, data.snapshot(player.getUUID()));
    }
}
