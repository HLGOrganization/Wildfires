package first.wildfires.client.sacombat;

import com.ogaba.sa_survival.item.backpack.BackpackItem;
import com.ogaba.sa_survival.menu.BackpackInventoryMenuExtender;
import first.wildfires.Wildfires;
import first.wildfires.network.QuickStoreBackpackPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Ctrl in the inventory screen puts items into the backpack. A left click sends the stack under the
 * mouse, or the one on the cursor; keeping the left button down and dragging across items sends those
 * too, so a whole row can be swept in without clicking each one.
 *
 * <p>The click runs before the screen handles it and cancels it, so the stack is not also picked up -
 * which is what leaves the cursor empty for the drag that follows. The drag itself is watched on each
 * frame, the only place the mouse position is known while it is holding still. Neither gesture
 * predicts anything locally: the move happens on the server and the changed slots are broadcast back.
 *
 * <p>The drag remembers the slot and stack it last sent and waits for that slot to change before
 * sending again. That keeps it to one packet per item, still lets a stack that only partly fit
 * continue into the backpack over the following round trips, and stops entirely on a slot that stops
 * changing - so a full backpack produces no traffic at all, however long the mouse rests on it.
 *
 * <p>A stack already on the cursor means the player is carrying one around, and dragging that is
 * vanilla's quick craft; the drag is left alone entirely in that case.
 *
 * <p>Only the inventory screen counts, which is where Survivors Arsenal draws its backpack panel and
 * where the mod's own Ctrl + right click quick swap already lives. Only slots belonging to the player
 * are offered, so the crafting result - a preview the server rebuilds from the grid - is left alone,
 * and so are the backpack panel's own slots, which the server would refuse in any case.
 */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BackpackQuickStoreHandler {

    /** The slot and stack the drag last sent, so one item produces one packet. */
    private static int dragSlot = -1;
    private static ItemStack dragStack = ItemStack.EMPTY;

    private BackpackQuickStoreHandler() {}

    /** Ctrl + left click: the stack on the cursor, or the one on the slot under the mouse. */
    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0 || !Screen.hasControlDown()
                || !(event.getScreen() instanceof InventoryScreen screen)) {
            return;
        }

        AbstractContainerMenu menu = screen.getMenu();
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            new QuickStoreBackpackPacket(-1, true, carried.copy()).sendToServer();
            event.setCanceled(true);
            return;
        }

        Slot slot = storeableSlot(screen, event.getMouseX(), event.getMouseY());
        if (slot == null) {
            return;
        }
        int index = menu.slots.indexOf(slot);
        remember(index, slot.getItem());
        new QuickStoreBackpackPacket(index, false, slot.getItem().copy()).sendToServer();
        event.setCanceled(true);
    }

    /** Ctrl held with the left button down: every item the mouse is dragged across is sent as well. */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen) || !Screen.hasControlDown()
                || !isLeftButtonDown()) {
            forget();
            return;
        }

        AbstractContainerMenu menu = screen.getMenu();
        if (!menu.getCarried().isEmpty()) {
            forget();
            return;
        }

        Slot slot = storeableSlot(screen, event.getMouseX(), event.getMouseY());
        if (slot == null) {
            forget();
            return;
        }

        int index = menu.slots.indexOf(slot);
        ItemStack hovered = slot.getItem();
        if (index == dragSlot && ItemStack.matches(hovered, dragStack)) {
            // This very stack is already on its way; wait for the server's answer for it.
            return;
        }

        remember(index, hovered);
        new QuickStoreBackpackPacket(index, false, hovered.copy()).sendToServer();
    }

    /**
     * The slot under the mouse when it may be sent to the backpack, or {@code null} for every slot that
     * must be left alone: the backpack panel's own slots, anything not backed by the player's
     * inventory, empty slots, and backpacks themselves - a bag may not be put inside a bag.
     */
    private static @Nullable Slot storeableSlot(InventoryScreen screen, double mouseX, double mouseY) {
        AbstractContainerMenu menu = screen.getMenu();
        Slot slot = slotUnderMouse(screen, menu, mouseX, mouseY);
        if (slot == null || BackpackInventoryMenuExtender.isBackpackSlot(slot)
                || !(slot.container instanceof Inventory)) {
            return null;
        }
        ItemStack stack = slot.getItem();
        if (stack.isEmpty() || stack.getItem() instanceof BackpackItem) {
            return null;
        }
        return slot;
    }

    private static void remember(int index, ItemStack stack) {
        dragSlot = index;
        dragStack = stack.copy();
    }

    /**
     * Whether the left button is physically down, asked of the window rather than of the game.
     *
     * <p>{@code MouseHandler.isLeftPressed()} cannot be used here: that field is only written while no
     * screen is open - {@code onPress} skips the write as soon as {@code minecraft.screen} is set - so
     * inside the inventory it stays false no matter what the player is holding.
     */
    private static boolean isLeftButtonDown() {
        Minecraft minecraft = Minecraft.getInstance();
        return GLFW.glfwGetMouseButton(minecraft.getWindow()
                .getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    private static void forget() {
        dragSlot = -1;
        dragStack = ItemStack.EMPTY;
    }

    /**
     * The slot whose 16x16 cell holds the mouse, placed the way vanilla places its own slots: the
     * screen's origin plus the slot's menu-relative coordinates.
     */
    private static Slot slotUnderMouse(InventoryScreen screen, AbstractContainerMenu menu, double mouseX, double mouseY) {
        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            if (mouseX >= left + slot.x && mouseX < left + slot.x + 16
                    && mouseY >= top + slot.y && mouseY < top + slot.y + 16) {
                return slot;
            }
        }
        return null;
    }
}
