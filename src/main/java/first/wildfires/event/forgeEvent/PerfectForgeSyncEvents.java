package first.wildfires.event.forgeEvent;

import first.wildfires.Wildfires;
import first.wildfires.command.SmithingCommand;
import first.wildfires.network.PerfectForgeSyncPacket;
import first.wildfires.smithing.PerfectForgeData;
import net.dries007.tfc.common.container.AnvilContainer;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Gives a player their forging record: the recipes they have unlocked and the sequences they have
 * remembered.
 *
 * <p>The record is sent on every login because the client keeps no copy of its own across sessions, and a
 * player who reconnects needs the anvil's button and the forging manual to show the right thing straight
 * away - whether a recipe's forge is unlocked and how far along the bar towards it is, and the blows of the
 * sequence each finished recipe remembers.
 *
 * <p>Later changes are pushed by {@code PerfectForgeService} as they happen, and the anvil asks for the
 * record again every time it is opened ({@link #onAnvilOpened}), so the one screen that draws all of this
 * from the copy never starts out stale.
 *
 * <p>The administrative smithing commands hang off this class too, because they change the same record and
 * have to push it the same way: unlocking by command has to reach the client exactly like unlocking by
 * forging does ({@link SmithingCommand}).
 */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PerfectForgeSyncEvents {

    private PerfectForgeSyncEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wildfires")
                .then(SmithingCommand.createWildfiresBranch()));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            send(player);
        }
    }

    /**
     * Hands a player their record again whenever they open an anvil.
     *
     * <p>Anvil is the one screen whose contents are drawn entirely from this record: the strip above the
     * panel and the button in its corner both read the client's copy, and nothing on the screen can tell
     * whether that copy is current. Every change to the record is pushed as it happens, so this is the
     * safety net rather than the main path - it covers a copy that was built before the record existed,
     * a push that never made it, and a record changed while the screen was closed. With it, opening the
     * anvil always starts from the truth and the player never has to close and reopen it to catch up.
     *
     * <p>Only the anvil is worth the packet: it is a few kilobytes, and every other container on the
     * server would be paying for a screen that never reads any of it.
     */
    @SubscribeEvent
    public static void onAnvilOpened(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getContainer() instanceof AnvilContainer) {
            send(player);
        }
    }

    /**
     * Pushes this player's record as it stands. The client keeps no copy of its own across sessions, and
     * every part of the record it shows - the unlock, the experience towards it, the remembered sequences -
     * only ever arrives this way.
     */
    private static void send(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        PerfectForgeData data = PerfectForgeData.get(server);
        PerfectForgeSyncPacket.sendTo(player, data.snapshot(player.getUUID()));
    }
}
