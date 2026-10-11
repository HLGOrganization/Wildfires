package first.wildfires.network;

import first.wildfires.client.smithing.PerfectForgeClientData;
import first.wildfires.compat.moreattributes.SkillLevels;
import first.wildfires.network.base.ICustomPacketPayload;
import first.wildfires.register.NetworkPacketRegister;
import first.wildfires.smithing.ForgeMemory;
import first.wildfires.smithing.PerfectForgeProgress;
import first.wildfires.smithing.PerfectForgeService;
import net.dries007.tfc.common.capabilities.forge.ForgeStep;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Sends one player their own forging record, the length of the bar it is measured against, and the blows of
 * every sequence they have struck out.
 *
 * <p>The anvil's one-click button, the memory strip above the anvil and the forging manual all draw from
 * this, so it goes out on join, again after every completed forge - which is the only thing that changes
 * it - and again whenever an anvil is opened ({@code PerfectForgeSyncEvents#onAnvilOpened}). It carries
 * only that player's entries - never anyone else's - so one player never learns another's progress or
 * another's way of forging.
 *
 * <p>The bar comes along because it depends on the receiving player's skill level
 * ({@link PerfectForgeService#xpToUnlock}) and the client has no way to work it out for itself: the manual
 * shows progress as a fraction of it, and the requirement is only ever read where the level is known.
 *
 * <p>The sequences come along because the client draws them: the strip shows the five blows around the one
 * being struck, and the manual lists a whole run as icons. They are the receiving player's own blows, and
 * the server only remembers a run that built the item, so what travels is a record of work already done
 * rather than anything that would let a client make an item by itself.
 */
public record PerfectForgeSyncPacket(Map<ResourceLocation, PerfectForgeProgress> progress, int xpToUnlock,
                                     Map<ResourceLocation, List<ForgeStep>> memories)
        implements ICustomPacketPayload {

    public PerfectForgeSyncPacket(FriendlyByteBuf buffer) {
        this(readProgress(buffer), buffer.readVarInt(), readMemories(buffer));
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
        buffer.writeVarInt(memories.size());
        for (Map.Entry<ResourceLocation, List<ForgeStep>> entry : memories.entrySet()) {
            buffer.writeResourceLocation(entry.getKey());
            buffer.writeVarInt(entry.getValue().size());
            for (ForgeStep step : entry.getValue()) {
                buffer.writeByte(indexOf(step));
            }
        }
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        PerfectForgeClientData.accept(progress, xpToUnlock, memories);
    }

    public static void sendTo(ServerPlayer player, Map<ResourceLocation, PerfectForgeProgress> progress) {
        NetworkPacketRegister.Instance.send(PacketDistributor.PLAYER.with(() -> player),
                new PerfectForgeSyncPacket(progress, PerfectForgeService.xpToUnlock(SkillLevels.of(player)),
                        memories(player)));
    }

    /** Every sequence this player has, or none if there is no server to ask. */
    private static Map<ResourceLocation, List<ForgeStep>> memories(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server == null ? Map.of() : ForgeMemory.rememberedAll(server, player.getUUID());
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

    private static Map<ResourceLocation, List<ForgeStep>> readMemories(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        Map<ResourceLocation, List<ForgeStep>> memories = new HashMap<>(Math.max(4, size));
        for (int i = 0; i < size; i++) {
            ResourceLocation recipe = buffer.readResourceLocation();
            int length = buffer.readVarInt();
            List<ForgeStep> steps = new ArrayList<>(length);
            for (int step = 0; step < length; step++) {
                int index = buffer.readUnsignedByte();
                if (index < ForgeStep.VALUES.length) {
                    steps.add(ForgeStep.VALUES[index]);
                }
            }
            if (!steps.isEmpty()) {
                memories.put(recipe, List.copyOf(steps));
            }
        }
        return memories;
    }

    /**
     * Where a step sits in {@link ForgeStep#VALUES}, which is what goes on the wire: a byte per blow, read
     * back against the same array. TFC's own order rather than the enum's ordinal, so that the two sides
     * agree even if TFC ever publishes those steps in an order of its own.
     */
    private static int indexOf(ForgeStep step) {
        for (int index = 0; index < ForgeStep.VALUES.length; index++) {
            if (ForgeStep.VALUES[index] == step) {
                return index;
            }
        }
        return 0;
    }
}
