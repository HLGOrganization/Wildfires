package first.wildfires.event.forgeEvent;

import first.wildfires.Wildfires;
import first.wildfires.command.SmithingCommand;
import first.wildfires.network.ForgeSeedSyncPacket;
import first.wildfires.network.PerfectForgeSyncPacket;
import first.wildfires.smithing.PerfectForgeData;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Gives a joining player their forging experience and the world seed.
 *
 * <p>The record is sent on every login because the client keeps no copy of its own across sessions, and a
 * player who reconnects needs the anvil's button and the forging manual to show the right thing straight
 * away. The seed is sent for the same reason: a recipe's target is derived from it, so the manual cannot
 * show a target until the client has it, and a client's own level reports zero.
 *
 * <p>Later changes are pushed by {@code PerfectForgeService} as they happen. The seed is re-sent on a
 * dimension change, since the target is read from whichever level the player is standing in.
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
            MinecraftServer server = player.getServer();
            if (server == null) {
                return;
            }
            PerfectForgeData data = PerfectForgeData.get(server);
            PerfectForgeSyncPacket.sendTo(player, data.snapshot(player.getUUID()));
            sendSeed(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendSeed(player);
        }
    }

    private static void sendSeed(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            ForgeSeedSyncPacket.sendTo(player, level.getSeed());
        }
    }
}
