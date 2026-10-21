package first.wildfires.soldering;

import com.mojang.logging.LogUtils;
import com.sighs.apricityui.container.PlayerInventorySlotOrder;
import com.sighs.apricityui.container.SlotLayout;
import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.screen.ApricityContainerMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * The perfboard editor menu, opened by right-clicking a soldering iron.
 *
 * <p><b>No machine slots.</b> The 8x8 board is drawn by the AUI page on a canvas, not by menu slots.
 * The menu therefore only declares the 36 player-inventory slots, so every vanilla click on the
 * backpack still goes through {@code AbstractContainerScreen} exactly like a furnace screen. The
 * page's board clicks go through the page bridge and {@link #clickMenuButton(Player, int)}.
 *
 * <p><b>State ownership.</b> The {@link CircuitBoard} lives in this menu. There is no block entity
 * and nothing in the world to tick. Closing the menu throws the in-progress board away; the
 * completed item keeps a snapshot in NBT.
 *
 * <p><b>Sync.</b> A single {@link ContainerData} carries the board to the client. Values are
 * {@code short} on the wire, so the code packs two 8-bit cell codes per data slot and one 12-bit
 * wire per slot. Revision is slot 0; the page uses it to redraw only when something changed.
 *
 * <p><b>Authority.</b> Place/remove/connect/delete/complete all arrive as vanilla container buttons
 * and are validated here on the server. The client-side bridge never mutates the board directly.
 */
public final class SolderingMenu extends ApricityContainerMenu {

    public static final String PLAYER_CONTAINER_ID = "player";
    public static final int PLAYER_SLOTS = 36;
    public static final String PAGE = "screens/soldering.html";

    /** First ContainerData slot: board revision. */
    static final int DATA_REVISION = 0;
    /** Second: number of wires. */
    static final int DATA_WIRE_COUNT = 1;
    /** 32 slots of two 8-bit cell codes each (64 cells total). */
    static final int DATA_CELLS = 2;
    static final int CELL_SHORTS = CircuitBoard.CELLS / 2;
    /** 64 slots, one 12-bit wire each. */
    static final int DATA_WIRES = DATA_CELLS + CELL_SHORTS;
    static final int DATA_COUNT = DATA_WIRES + CircuitBoard.MAX_WIRES;

    private static final Component TITLE = Component.translatable("container.wildfires.soldering");
    private static final Logger LOGGER = LogUtils.getLogger();

    private final CircuitBoard board;
    private final CircuitData data;

    /**
     * Accumulator for {@link SolderingActions}' multi-click protocol. A vanilla container button
     * only carries one byte, so a coordinate action arrives as a control byte plus one or two data
     * bytes; this state belongs to the menu and is reset by every control byte.
     */
    private int pendingAction = -1;
    private int pendingPayload;
    private int pendingBits;

    /** Client factory: reads the layout written by {@link #open(ServerPlayer)}. */
    public SolderingMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, SlotLayout.read(buffer), new CircuitBoard(), null);
        // AUI builds client slots from the buffer. If that ever arrives empty, the page would have
        // no addressable backpack at all, so rebuild the 36 player slots as a safety net.
        this.ensureClientSlots();
        LOGGER.info("[Soldering] client menu slots={} uiOnly={}",
                this.slots.size(), this.rawLayout().isUiOnly());
    }

    /** Server factory: builds the real authoritative board. */
    public SolderingMenu(int id, Inventory inventory, ServerPlayer owner) {
        this(id, inventory, createLayout(), new CircuitBoard(), owner);
        LOGGER.info("[Soldering] server menu slots={} uiOnly={}",
                this.slots.size(), this.rawLayout().isUiOnly());
    }

    private SolderingMenu(int id, Inventory inventory, SlotLayout layout, CircuitBoard board,
                          @Nullable ServerPlayer owner) {
        super(id, inventory, layout, Map.of(), Map.of(), owner);
        this.board = board;
        this.data = new CircuitData(board);
        this.addDataSlots(this.data);
    }

    /**
     * The page only has a player container. {@code primary = true} makes
     * {@link SlotLayout#primaryContainerId()} resolve to "player" for AUI's binder.
     */
    private static SlotLayout createLayout() {
        return new SlotLayout(PAGE, List.of(
                new SlotLayout.ContainerEntry(PLAYER_CONTAINER_ID, ContainerBindType.PLAYER,
                        0, PLAYER_SLOTS, true)
        ));
    }

    /** Opens the editor. The layout is written as menu extra data for the client factory. */
    public static void open(ServerPlayer player) {
        SlotLayout layout = createLayout();
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (id, inventory, p) -> new SolderingMenu(id, inventory, (ServerPlayer) p), TITLE),
                buffer -> layout.write(buffer));
    }

    private void ensureClientSlots() {
        if (!this.slots.isEmpty()) {
            return;
        }
        Inventory inventory = this.getPlayerInventory();
        for (int menuIndex = 0; menuIndex < PLAYER_SLOTS; menuIndex++) {
            int inventoryIndex =
                    PlayerInventorySlotOrder.menuRelativeIndexToPlayerInventoryIndex(menuIndex, PLAYER_SLOTS);
            this.addSlot(new ApricityContainerMenu.UiSlot(inventory, inventoryIndex, 0, 0));
        }
        LOGGER.warn("[Soldering] client layout produced no slots; rebuilt {} player UI slots",
                this.slots.size());
    }

    // ------------------------------------------------------------------
    // Server-authoritative actions
    // ------------------------------------------------------------------

    /**
     * Receives the multi-click sequence described on {@link SolderingActions}. A control byte
     * starts a new action; subsequent 7-bit data bytes accumulate its payload. The action runs once
     * the required number of bits has arrived. Extra bytes after a completed action are ignored.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!this.stillValid(player)) {
            return false;
        }
        int button = id & 0xFF;
        if (SolderingActions.isControl(button)) {
            int action = SolderingActions.actionOf(button);
            int bits = SolderingActions.requiredBits(action);
            if (bits < 0) {
                this.resetPending();
                return false;
            }
            this.pendingAction = action;
            this.pendingPayload = 0;
            this.pendingBits = 0;
            if (bits == 0) {
                this.resetPending();
                return this.execute(player, action, 0);
            }
            return true;
        }

        if (this.pendingAction < 0) {
            return false;
        }
        this.pendingPayload |= (button & 0x7F) << this.pendingBits;
        this.pendingBits += 7;
        if (this.pendingBits < SolderingActions.requiredBits(this.pendingAction)) {
            return true;
        }
        int action = this.pendingAction;
        int payload = this.pendingPayload;
        this.resetPending();
        return this.execute(player, action, payload);
    }

    private void resetPending() {
        this.pendingAction = -1;
        this.pendingPayload = 0;
        this.pendingBits = 0;
    }

    private boolean execute(Player player, int action, int payload) {
        boolean changed = switch (action) {
            case SolderingActions.PLACE -> this.placeComponent(player, payload);
            case SolderingActions.REMOVE -> this.removeComponent(player, payload);
            case SolderingActions.WIRE -> this.connectWire(player, payload);
            case SolderingActions.DELETE_WIRE -> this.deleteWire(player, payload);
            case SolderingActions.COMPLETE -> this.completeBoard(player);
            case SolderingActions.CLEAR -> this.clearBoard(player);
            default -> false;
        };
        if (changed) {
            // ContainerData is short-based; broadcastChanges sends every changed slot.
            this.broadcastChanges();
        }
        return changed;
    }

    private boolean placeComponent(Player player, int payload) {
        CircuitComponent type = CircuitComponent.byId(payload & 7);
        boolean vertical = ((payload >> 3) & 1) != 0;
        boolean flipped = ((payload >> 4) & 1) != 0;
        int x = (payload >> 5) & 7;
        int y = (payload >> 8) & 7;
        String typeName = type == null ? "null" : type.itemId();
        LOGGER.info("[Soldering] place request type={} x={} y={} v={} f={} creative={}",
                typeName, x, y, vertical, flipped, player.isCreative());
        if (type == null || (flipped && !type.polarized())) {
            LOGGER.info("[Soldering] place rejected reason=invalid type={} v={} f={}",
                    typeName, vertical, flipped);
            return false;
        }
        int width = type.width(vertical);
        int height = type.height(vertical);
        if (x + width > CircuitBoard.SIZE || y + height > CircuitBoard.SIZE) {
            LOGGER.info("[Soldering] place rejected reason=out_of_bounds type={} x={} y={} w={} h={}",
                    typeName, x, y, width, height);
            error(player, "out_of_bounds");
            return false;
        }
        if (!this.board.canPlace(type, x, y, vertical, flipped)) {
            LOGGER.info("[Soldering] place rejected reason=overlap type={} x={} y={} v={} f={} at={}",
                    typeName, x, y, vertical, flipped, this.board.findComponentAt(x, y));
            error(player, "occupied");
            return false;
        }
        // Creative players have unlimited parts; survival players must carry the token, but it is
        // deliberately not consumed (one item can mark several installed components).
        if (!player.isCreative() && countItem(player, type.item()) <= 0) {
            LOGGER.info("[Soldering] place rejected reason=no_stock type={} x={} y={}",
                    typeName, x, y);
            error(player, "not_in_inventory");
            return false;
        }
        if (!this.board.place(type, x, y, vertical, flipped)) {
            LOGGER.info("[Soldering] place rejected reason=board_place_false type={} x={} y={}",
                    typeName, x, y);
            error(player, "occupied");
            return false;
        }
        LOGGER.info("[Soldering] place accepted type={} x={} y={} v={} f={}",
                typeName, x, y, vertical, flipped);
        return true;
    }

    private boolean removeComponent(Player player, int payload) {
        int x = payload & 7;
        int y = (payload >> 3) & 7;
        if (!this.board.removeAt(x, y)) {
            error(player, "nothing_there");
            return false;
        }
        return true;
    }

    private boolean connectWire(Player player, int payload) {
        int x1 = payload & 7;
        int y1 = (payload >> 3) & 7;
        int x2 = (payload >> 6) & 7;
        int y2 = (payload >> 9) & 7;
        if (x1 == x2 && y1 == y2) {
            error(player, "same_hole");
            return false;
        }
        if (this.board.hasWire(x1, y1, x2, y2)) {
            error(player, "duplicate_wire");
            return false;
        }
        if (this.board.wireCount() >= CircuitBoard.MAX_WIRES) {
            error(player, "too_many_wires");
            return false;
        }
        if (!this.board.canAddWire(x1, y1, x2, y2)) {
            error(player, "invalid_wire");
            return false;
        }
        int cost = Math.abs(x1 - x2) + Math.abs(y1 - y2);
        if (!player.isCreative()) {
            if (countItem(player, SolderingRegister.COPPER_WIRE.get()) < cost) {
                error(player, "not_enough_wire");
                return false;
            }
            if (!consumeItem(player, SolderingRegister.COPPER_WIRE.get(), cost)) {
                error(player, "not_enough_wire");
                return false;
            }
        }
        this.board.addWire(x1, y1, x2, y2);
        return true;
    }

    private boolean deleteWire(Player player, int payload) {
        int x1 = payload & 7;
        int y1 = (payload >> 3) & 7;
        int x2 = (payload >> 6) & 7;
        int y2 = (payload >> 9) & 7;
        int index = this.board.findWire(x1, y1, x2, y2);
        if (index < 0 || !this.board.removeWire(index)) {
            error(player, "nothing_there");
            return false;
        }
        // Deliberate: deleting a trace does not refund the copper it cost.
        return true;
    }

    private boolean completeBoard(Player player) {
        if (!consumeItem(player, SolderingRegister.CIRCUIT_BOARD.get(), 1)) {
            error(player, "no_board");
            return false;
        }
        ItemStack result = new ItemStack(SolderingRegister.SOLDERED_CIRCUIT.get());
        CompoundTag tag = result.getOrCreateTag();
        tag.put(CircuitBoard.TAG, this.board.toTag());
        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }
        this.board.clear();
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.wildfires.soldering.completed"), true);
        }
        return true;
    }

    private boolean clearBoard(Player player) {
        this.board.clear();
        return true;
    }

    // ------------------------------------------------------------------
    // Vanilla slot plumbing (player inventory only)
    // ------------------------------------------------------------------

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        // Global 0..26 are the 27 main inventory slots, 27..35 are the hotbar.
        int start = index < 27 ? 27 : 0;
        int end = index < 27 ? 36 : 27;
        if (!this.moveItemStackTo(stack, start, end, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.resetPending();
    }

    /** The client needs the real menu type, not the one AUI's superclass constructor installs. */
    @Override
    public MenuType<?> getType() {
        return SolderingRegister.SOLDERING_MENU.get();
    }

    /**
     * AUI resolves page slot indices from the layout. With a player-only layout the global index is
     * exactly the menu-relative inventory index; this fallback keeps older/empty layouts usable.
     */
    @Override
    public Integer resolveGlobalSlotIndex(String containerId, int localIndex) {
        Integer resolved = super.resolveGlobalSlotIndex(containerId, localIndex);
        if (resolved != null) {
            return resolved;
        }
        if (PLAYER_CONTAINER_ID.equals(containerId) && localIndex >= 0 && localIndex < PLAYER_SLOTS) {
            int menuRelative =
                    PlayerInventorySlotOrder.playerInventoryIndexToMenuRelativeIndex(localIndex, PLAYER_SLOTS);
            return menuRelative < 0 ? null : menuRelative;
        }
        return null;
    }

    /** If the client received a UI-only layout, expose the real player declaration to AUI's binder. */
    @Override
    public SlotLayout getLayout() {
        SlotLayout layout = super.getLayout();
        return layout != null && layout.isUiOnly() ? createLayout() : layout;
    }

    private SlotLayout rawLayout() {
        return super.getLayout();
    }

    // ------------------------------------------------------------------
    // Page-facing read-only state
    // ------------------------------------------------------------------

    /** Compact board state consumed by {@code WildfiresSoldering.state()} in the page script. */
    public String stateString() {
        return this.board.stateString();
    }

    public int boardRevision() {
        return this.board.revision();
    }

    CircuitBoard board() {
        return this.board;
    }

    // ------------------------------------------------------------------
    // Inventory helpers
    // ------------------------------------------------------------------

    private static int countItem(Player player, Item item) {
        Inventory inventory = player.getInventory();
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean consumeItem(Player player, Item item, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (countItem(player, item) < amount) {
            return false;
        }
        Inventory inventory = player.getInventory();
        int remaining = amount;
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(item)) {
                continue;
            }
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
        }
        inventory.setChanged();
        return remaining == 0;
    }

    private static void error(Player player, String key) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.wildfires.soldering.error." + key), true);
        }
    }

    // ------------------------------------------------------------------
    // ContainerData bridge
    // ------------------------------------------------------------------

    /**
     * Bidirectional bridge: on the server {@code get} reads the authoritative board; on the client
     * {@code set} writes the decoded values straight into the client-side {@link CircuitBoard}.
     * Vanilla sends each value as a short, hence the masks.
     */
    private static final class CircuitData implements ContainerData {

        private final CircuitBoard board;

        private CircuitData(CircuitBoard board) {
            this.board = board;
        }

        @Override
        public int get(int index) {
            if (index == DATA_REVISION) {
                return this.board.revision();
            }
            if (index == DATA_WIRE_COUNT) {
                return this.board.wireCount();
            }
            if (index >= DATA_CELLS && index < DATA_CELLS + CELL_SHORTS) {
                int cell = (index - DATA_CELLS) * 2;
                return this.board.cellAt(cell) | (this.board.cellAt(cell + 1) << 8);
            }
            if (index >= DATA_WIRES && index < DATA_WIRES + CircuitBoard.MAX_WIRES) {
                CircuitBoard.Wire wire = this.board.wire(index - DATA_WIRES);
                return wire == null ? 0 : wire.packed();
            }
            return 0;
        }

        @Override
        public void set(int index, int value) {
            if (index == DATA_REVISION) {
                this.board.setRevision(value);
                return;
            }
            if (index == DATA_WIRE_COUNT) {
                this.board.setWireCount(value & 0xFFFF);
                return;
            }
            if (index >= DATA_CELLS && index < DATA_CELLS + CELL_SHORTS) {
                int cell = (index - DATA_CELLS) * 2;
                this.board.setCell(cell, value & 0xFF);
                this.board.setCell(cell + 1, (value >> 8) & 0xFF);
                return;
            }
            if (index >= DATA_WIRES && index < DATA_WIRES + CircuitBoard.MAX_WIRES) {
                this.board.setWire(index - DATA_WIRES, value & 0xFFFF);
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }
}
