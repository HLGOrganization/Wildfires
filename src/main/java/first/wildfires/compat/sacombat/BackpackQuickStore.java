package first.wildfires.compat.sacombat;

import com.ogaba.sa_survival.item.backpack.DynamicBackpackItemHandler;
import com.ogaba.sa_survival.menu.BackpackInventoryMenuExtender;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Ctrl + left click in the player's inventory screen: the stack on the cursor, or the stack on the
 * clicked slot, goes straight into the equipped backpack.
 *
 * <p>The click is decided on the client, because that is the only side that knows the Ctrl key is
 * down - a container click packet carries no modifier state - and it arrives here as a packet naming
 * a menu slot. Everything that decides the outcome is therefore read again on the server: the slot is
 * looked up in the menu the player actually has open, and whatever is found there has to match the
 * stack the client clicked. A slot that moved between the click and the packet is refused instead of
 * transferring the wrong item.
 *
 * <p>Only slots backed by the player's own inventory are accepted. The inventory menu also holds the
 * crafting result, whose slot is a preview rebuilt from the grid; moving that preview would hand out
 * items that were never crafted.
 *
 * <p>The move itself goes through {@link DynamicBackpackItemHandler}, the handler the backpack's own
 * menu slots are built from, so the TFC item-size limit this pack applies to that handler holds here
 * too - and the handler refuses a backpack outright, so a bag can never be put inside a bag.
 */
public final class BackpackQuickStore {

    /** Survivors Arsenal's own "no backpack" message, so the wording stays the pack's. */
    private static final String NO_BACKPACK = "message.sa_combat.backpack.none";

    private BackpackQuickStore() {}

    /**
     * Moves the clicked stack into the backpack, leaving whatever does not fit where it was.
     *
     * @param slotIndex the clicked slot's position in the open menu, ignored when {@code carried}
     * @param carried   whether the client clicked with a stack on the cursor instead of on a slot
     * @param expected  the stack the client clicked, checked against the server's copy of it
     */
    public static void store(ServerPlayer player, int slotIndex, boolean carried, ItemStack expected) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) {
            return;
        }

        Slot slot = null;
        ItemStack source;
        if (carried) {
            source = menu.getCarried();
        } else {
            if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
                return;
            }
            slot = menu.getSlot(slotIndex);
            // The backpack's own panel slots are part of the inventory menu; storing from one of them
            // into the backpack it belongs to would only move an item onto itself.
            if (!slot.isActive() || BackpackInventoryMenuExtender.isBackpackSlot(slot)
                    || !(slot.container instanceof Inventory)) {
                return;
            }
            source = slot.getItem();
        }

        if (source.isEmpty() || !ItemStack.matches(source, expected)) {
            return;
        }

        DynamicBackpackItemHandler backpack = new DynamicBackpackItemHandler(player);
        if (!backpack.hasBackpack()) {
            player.displayClientMessage(Component.translatable(NO_BACKPACK), true);
            return;
        }

        ItemStack remainder = source.copy();
        for (int i = 0; i < backpack.getSlots() && !remainder.isEmpty(); i++) {
            if (!backpack.isSlotEnabled(i)) {
                continue;
            }
            remainder = backpack.insertItem(i, remainder, false);
        }
        if (remainder.getCount() == source.getCount()) {
            // Nothing fit: leave the stack untouched rather than reporting a move that did not happen.
            return;
        }

        if (carried) {
            menu.setCarried(remainder);
        } else {
            slot.set(remainder);
        }
        menu.broadcastChanges();
        // The backpack's slots are added to the player's inventory menu and the client draws its panel
        // from that menu, so it is refreshed even when the open menu is a different one.
        player.inventoryMenu.broadcastChanges();
    }
}
