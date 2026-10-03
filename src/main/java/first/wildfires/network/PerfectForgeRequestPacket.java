package first.wildfires.network;

import first.wildfires.network.base.ICustomPacketPayload;
import first.wildfires.smithing.PerfectForgeService;
import net.dries007.tfc.common.container.AnvilContainer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Asks the server to forge the currently open anvil's recipe perfectly.
 *
 * <p>Carries nothing. The server works from the container the player actually has open, reached through
 * {@code player.containerMenu}, so a modified client cannot name an anvil it is not standing at - and
 * cannot invent a recipe, a count, or a hammer, since all of those are read server-side.
 */
public record PerfectForgeRequestPacket() implements ICustomPacketPayload {

    public PerfectForgeRequestPacket(FriendlyByteBuf buffer) {
        this();
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        // Nothing to write: the open container is the whole request.
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null || !(player.containerMenu instanceof AnvilContainer container)) {
            return;
        }
        PerfectForgeService.tryPerfectForge(player, container.getBlockEntity());
    }
}
