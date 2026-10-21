package first.wildfires.soldering;

import com.sighs.apricityui.registry.annotation.KJSBindings;
import first.wildfires.Wildfires;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Page-script bridge for {@code apricity/screens/soldering.html}.
 *
 * <p>Registered as the page global {@code WildfiresSoldering} by AUI's KubeJS scan. Every mutating
 * method builds one integer with {@link SolderingActions} and sends it through
 * {@code ClientPacketListener#handleInventoryButtonClick} — vanilla's container-button packet. The
 * server validates and applies it in {@link SolderingMenu#clickMenuButton}. There is no custom
 * network channel.
 *
 * <p>Read-only helpers ({@link #state()}, {@link #stock(int)}, {@link #copperWire()},
 * {@link #boards()}, {@link #text(String)}) run on the client and are polled by the page every
 * 100 ms. The board state itself is authoritative on the server; the client copy only exists so
 * the canvas can be drawn.
 */
@KJSBindings(value = "WildfiresSoldering", modId = Wildfires.MODID, isClient = true)
public final class SolderingAuiBindings {

    private SolderingAuiBindings() {
    }

    // ------------------------------------------------------------------
    // Board state
    // ------------------------------------------------------------------

    /** {@code R<revision>|C<64 base-32 cells>|W<count>|<3 hex digits per wire>} or "" if closed. */
    public static String state() {
        SolderingMenu menu = menu();
        return menu == null ? "" : menu.stateString();
    }

    public static int revision() {
        SolderingMenu menu = menu();
        return menu == null ? 0 : menu.boardRevision();
    }

    // ------------------------------------------------------------------
    // Actions (vanilla container buttons)
    // ------------------------------------------------------------------

    public static boolean place(int type, int x, int y, int vertical, int flipped) {
        return send(SolderingActions.place(type, x, y, vertical, flipped));
    }

    public static boolean removeComponent(int x, int y) {
        return send(SolderingActions.remove(x, y));
    }

    public static boolean connect(int x1, int y1, int x2, int y2) {
        return send(SolderingActions.wire(x1, y1, x2, y2));
    }

    public static boolean disconnect(int x1, int y1, int x2, int y2) {
        return send(SolderingActions.deleteWire(x1, y1, x2, y2));
    }

    public static boolean complete() {
        return send(SolderingActions.complete());
    }

    public static boolean clear() {
        return send(SolderingActions.clear());
    }

    // ------------------------------------------------------------------
    // Client-side helpers for the page
    // ------------------------------------------------------------------

    /** Manhattan distance, matching the server's copper cost. */
    public static int wireCost(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    /** Creative players see unlimited parts/copper; the server skips the same checks. */
    public static boolean creative() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.isCreative();
    }

    /** How many of the given component item the player is carrying (main inventory + hotbar). */
    public static int stock(int type) {
        CircuitComponent component = CircuitComponent.byId(type);
        return component == null ? 0 : countItem(component.item());
    }

    public static int copperWire() {
        return countItem(SolderingRegister.COPPER_WIRE.get());
    }

    public static int boards() {
        return countItem(SolderingRegister.CIRCUIT_BOARD.get());
    }

    /** Localised text for dynamic page status lines. */
    public static String text(String key) {
        return Component.translatable(key).getString();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * Sends one logical operation as the ordered control+data byte sequence from
     * {@link SolderingActions}. Each byte is an ordinary vanilla container button click.
     */
    private static boolean send(int[] sequence) {
        SolderingMenu menu = menu();
        Minecraft minecraft = Minecraft.getInstance();
        if (menu == null || minecraft.gameMode == null || sequence.length == 0) {
            return false;
        }
        for (int button : sequence) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
        return true;
    }

    private static SolderingMenu menu() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return null;
        }
        return minecraft.player.containerMenu instanceof SolderingMenu menu ? menu : null;
    }

    private static int countItem(Item item) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return 0;
        }
        Inventory inventory = minecraft.player.getInventory();
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
