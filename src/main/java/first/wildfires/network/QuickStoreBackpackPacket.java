package first.wildfires.network;

import first.wildfires.compat.sacombat.BackpackQuickStore;
import first.wildfires.network.base.ICustomPacketPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Asks the server to put a clicked stack into the player's backpack (Ctrl + left click).
 *
 * <p>Sent instead of the vanilla container click, because only the client knows Ctrl is held. The
 * slot number and the stack the client saw both travel with it and are checked against the server's
 * own menu before anything moves; see {@link BackpackQuickStore}.
 *
 * @param slotIndex the clicked slot in the open menu, or -1 when the click was on the cursor stack
 * @param carried   whether the stack to store is the one on the cursor
 * @param expected  the stack the client clicked, used to detect a slot that changed in between
 */
public record QuickStoreBackpackPacket(int slotIndex, boolean carried, ItemStack expected) implements ICustomPacketPayload {

    public QuickStoreBackpackPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readBoolean(), buffer.readItem());
    }

    @Override
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(slotIndex);
        buffer.writeBoolean(carried);
        buffer.writeItem(expected);
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null) {
            return;
        }
        BackpackQuickStore.store(player, slotIndex, carried, expected);
    }
}
