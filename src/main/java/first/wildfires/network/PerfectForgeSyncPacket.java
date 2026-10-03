package first.wildfires.network;

import first.wildfires.client.smithing.PerfectForgeClientData;
import first.wildfires.compat.moreattributes.SkillLevels;
import first.wildfires.network.base.ICustomPacketPayload;
import first.wildfires.register.NetworkPacketRegister;
import first.wildfires.smithing.ForgeMemoryData;
import first.wildfires.smithing.PerfectForgeProgress;
import first.wildfires.smithing.PerfectForgeService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Sends one player their own forging record, the length of the bar it is measured against, and the recipes
 * they can have replayed from memory.
 *
 * <p>The anvil's one-click button and the forging manual both draw from this, so it goes out on join and
 * again after every completed forge, which is the only thing that changes it. It carries only that
 * player's entries - never anyone else's - so one player never learns another's progress.
 *
 * <p>The bar comes along because it depends on the receiving player's skill level
 * ({@link PerfectForgeService#xpToUnlock}) and the client has no way to work it out for itself: the manual
 * shows progress as a fraction of it, and the requirement is only ever read where the level is known.
 *
 * <p>The remembered recipes come along for the button's third frame - a recipe that is still locked but
 * that this player has finished by hand, so the button can strike those blows again
 * ({@link first.wildfires.smithing.ForgeMemory}). Only the fact that a sequence exists is sent, never the
 * sequence: it is enough to choose a picture, and nothing that would let a client forge the item itself.
 * That set is a subset of the recipes already listed here, since a sequence is only ever written by
 * finishing one, so this cannot grow the packet into a new size class.
 */
public record PerfectForgeSyncPacket(Map<ResourceLocation, PerfectForgeProgress> progress, int xpToUnlock,
                                     Set<ResourceLocation> remembered) implements ICustomPacketPayload {

    public PerfectForgeSyncPacket(FriendlyByteBuf buffer) {
        this(readProgress(buffer), buffer.readVarInt(), readRemembered(buffer));
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(progress.size());
        for (Map.Entry<ResourceLocation, PerfectForgeProgress> entry : progress.entrySet()) {
            buffer.writeResourceLocation(entry.getKey());
            buffer.writeVarInt(entry.getValue().xp());
            buffer.writeBoolean(entry.getValue().unlocked());
            buffer.writeVarInt(entry.getValue().forged());
        }
        buffer.writeVarInt(xpToUnlock);
        buffer.writeVarInt(remembered.size());
        for (ResourceLocation recipe : remembered) {
            buffer.writeResourceLocation(recipe);
        }
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        PerfectForgeClientData.accept(progress, xpToUnlock, remembered);
    }

    public static void sendTo(ServerPlayer player, Map<ResourceLocation, PerfectForgeProgress> progress) {
        NetworkPacketRegister.Instance.send(PacketDistributor.PLAYER.with(() -> player),
                new PerfectForgeSyncPacket(progress, PerfectForgeService.xpToUnlock(SkillLevels.of(player)),
                        remembered(player)));
    }

    /** The recipes this player has a remembered sequence for, or none if there is no server to ask. */
    private static Set<ResourceLocation> remembered(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server == null ? Set.of() : ForgeMemoryData.get(server).recipes(player.getUUID());
    }

    private static Map<ResourceLocation, PerfectForgeProgress> readProgress(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        Map<ResourceLocation, PerfectForgeProgress> progress = new HashMap<>(Math.max(4, size));
        for (int i = 0; i < size; i++) {
            ResourceLocation recipe = buffer.readResourceLocation();
            progress.put(recipe, new PerfectForgeProgress(buffer.readVarInt(), buffer.readBoolean(),
                    buffer.readVarInt()));
        }
        return progress;
    }

    private static Set<ResourceLocation> readRemembered(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        Set<ResourceLocation> remembered = new HashSet<>(Math.max(4, size));
        for (int i = 0; i < size; i++) {
            remembered.add(buffer.readResourceLocation());
        }
        return remembered;
    }
}
