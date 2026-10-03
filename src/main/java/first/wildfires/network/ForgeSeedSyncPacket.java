package first.wildfires.network;

import first.wildfires.client.smithing.ForgeSeedClientData;
import first.wildfires.network.base.ICustomPacketPayload;
import first.wildfires.register.NetworkPacketRegister;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Tells one player the world seed, so their forging manual can work out anvil targets.
 *
 * <p>TFC picks a recipe's target from the seed, and a client on a server cannot read it - the client's own
 * level reports zero. Without the real seed the manual could not show a target, let alone a path to it.
 * The seed only changes when the world does, so this goes out on join, and again on a dimension change in
 * case a world's dimensions ever report different seeds.
 *
 * <p>A new packet rather than a field on {@code PerfectForgeSyncPacket}: that one carries progress and is
 * re-sent every time a recipe is forged perfectly, and the seed has no business travelling with it.
 */
public record ForgeSeedSyncPacket(long seed) implements ICustomPacketPayload {

    public ForgeSeedSyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readLong());
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeLong(seed);
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        ForgeSeedClientData.accept(seed);
    }

    public static void sendTo(ServerPlayer player, long seed) {
        NetworkPacketRegister.Instance.send(PacketDistributor.PLAYER.with(() -> player),
                new ForgeSeedSyncPacket(seed));
    }
}
