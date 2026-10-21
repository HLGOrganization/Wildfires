package first.wildfires.soldering;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.Arrays;

/**
 * Server-authoritative state of one 8x8 perfboard.
 *
 * <p><b>Storage.</b> The board is a flat array of 64 cell codes plus a small array of wires.
 * A component is stored once, at its anchor cell; every other cell of its footprint is derived from
 * the anchor plus the component type, so a 2x3 chip costs one cell, not six. The cell code is
 * {@code typeId | (vertical ? 8 : 0) | (flipped ? 16 : 0)}; codes therefore stay below 32 and fit
 * in one base-32 character in the page bridge string.
 *
 * <p><b>Wires.</b> A wire is exactly the pair of holes the player clicked. The path is not stored:
 * the renderer and the server both use the same rule — same row/column is a straight segment,
 * otherwise an L with the horizontal leg first ({@code (x1,y1) -> (x2,y1) -> (x2,y2)}). Its cost is
 * the Manhattan distance. A duplicate (either direction) is rejected; at most
 * {@link #MAX_WIRES} wires are kept. Deleting a wire does not refund copper.
 *
 * <p>An endpoint is just the clicked hole. It may lie on a component cell: that cell is treated as
 * the component's pin, and the page draws a visible pin above the component body. A component may
 * also be placed over an existing wire endpoint; it becomes a pin. A component still cannot overlap
 * another component.
 *
 * <p><b>Sync.</b> The menu's {@code ContainerData} packs two cells per data slot and one wire per
 * data slot. The client reuses this same class: {@code ContainerData#set} writes the decoded values
 * into it, and the page reads {@link #stateString()}. Nothing in the client ever mutates the board
 * through the placement methods.
 *
 * <p><b>No block entity.</b> Opening the soldering iron creates a menu, and the board lives exactly
 * as long as that menu. Closing the screen discards the in-progress board. The completed item keeps
 * a copy in its NBT ({@link #TAG}) so the composition survives.
 */
final class CircuitBoard {

    static final int SIZE = 8;
    static final int CELLS = SIZE * SIZE;
    static final int MAX_WIRES = 64;

    /** NBT key written onto the completed {@code soldered_circuit}. */
    static final String TAG = "WildfiresCircuit";

    private static final int VERTICAL_BIT = 8;
    private static final int FLIPPED_BIT = 16;

    private final int[] cells = new int[CELLS];
    private final Wire[] wireSlots = new Wire[MAX_WIRES];
    private int wireCount;
    private int revision = 1;

    // ------------------------------------------------------------------
    // Read-only views (also used by the client sync decoder)
    // ------------------------------------------------------------------

    int revision() {
        return this.revision & 0xFFFF;
    }

    int cellAt(int index) {
        return index >= 0 && index < CELLS ? this.cells[index] & 0xFF : 0;
    }

    int cell(int x, int y) {
        return inBounds(x, y) ? this.cells[index(x, y)] & 0xFF : 0;
    }

    int wireCount() {
        return this.wireCount;
    }

    Wire wire(int index) {
        return index >= 0 && index < this.wireCount ? this.wireSlots[index] : null;
    }

    // ------------------------------------------------------------------
    // Client sync writes
    // ------------------------------------------------------------------

    void setRevision(int value) {
        this.revision = value & 0xFFFF;
    }

    void setCell(int index, int code) {
        if (index >= 0 && index < CELLS) {
            this.cells[index] = code & 0xFF;
        }
    }

    void setWireCount(int count) {
        this.wireCount = Math.max(0, Math.min(MAX_WIRES, count));
        for (int i = this.wireCount; i < MAX_WIRES; i++) {
            this.wireSlots[i] = null;
        }
    }

    void setWire(int index, int packed) {
        if (index < 0 || index >= MAX_WIRES) {
            return;
        }
        Wire wire = Wire.unpack(packed);
        this.wireSlots[index] = wire.isDegenerate() ? null : wire;
    }

    // ------------------------------------------------------------------
    // Server-side edits
    // ------------------------------------------------------------------

    boolean canPlace(CircuitComponent type, int x, int y, boolean vertical, boolean flipped) {
        if (type == null || (flipped && !type.polarized())) {
            return false;
        }
        int width = type.width(vertical);
        int height = type.height(vertical);
        if (x < 0 || y < 0 || x + width > SIZE || y + height > SIZE) {
            return false;
        }
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                int cx = x + dx;
                int cy = y + dy;
                if (findComponentAt(cx, cy) != null) {
                    return false;
                }
                // A wire endpoint may become a pin under the component; that is legal now.
            }
        }
        return true;
    }

    boolean place(CircuitComponent type, int x, int y, boolean vertical, boolean flipped) {
        if (!canPlace(type, x, y, vertical, flipped)) {
            return false;
        }
        this.cells[index(x, y)] = encode(type, vertical, flipped);
        this.revision++;
        return true;
    }

    boolean removeAt(int x, int y) {
        PlacedComponent placed = findComponentAt(x, y);
        if (placed == null) {
            return false;
        }
        this.cells[index(placed.x(), placed.y())] = 0;
        this.revision++;
        return true;
    }

    boolean canAddWire(int x1, int y1, int x2, int y2) {
        if (!inBounds(x1, y1) || !inBounds(x2, y2)) {
            return false;
        }
        if (x1 == x2 && y1 == y2) {
            return false;
        }
        if (this.wireCount >= MAX_WIRES) {
            return false;
        }
        // Endpoints may sit on a component cell: that cell is the pin. The wire still stores only
        // the hole coordinate, so the page can show the pin and the server can find the component.
        Wire candidate = new Wire(x1, y1, x2, y2);
        for (int i = 0; i < this.wireCount; i++) {
            Wire existing = this.wireSlots[i];
            if (existing != null && existing.sameEndpoints(candidate)) {
                return false;
            }
        }
        return true;
    }

    boolean addWire(int x1, int y1, int x2, int y2) {
        if (!canAddWire(x1, y1, x2, y2)) {
            return false;
        }
        this.wireSlots[this.wireCount++] = new Wire(x1, y1, x2, y2);
        this.revision++;
        return true;
    }

    boolean removeWire(int index) {
        if (index < 0 || index >= this.wireCount) {
            return false;
        }
        for (int i = index; i < this.wireCount - 1; i++) {
            this.wireSlots[i] = this.wireSlots[i + 1];
        }
        this.wireSlots[--this.wireCount] = null;
        this.revision++;
        return true;
    }

    void clear() {
        Arrays.fill(this.cells, 0);
        Arrays.fill(this.wireSlots, null);
        this.wireCount = 0;
        this.revision++;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /** Returns the component whose footprint covers the cell, or {@code null}. */
    PlacedComponent findComponentAt(int x, int y) {
        if (!inBounds(x, y)) {
            return null;
        }
        for (int ay = 0; ay < SIZE; ay++) {
            for (int ax = 0; ax < SIZE; ax++) {
                int code = this.cells[index(ax, ay)];
                if (code == 0) {
                    continue;
                }
                CircuitComponent type = typeOf(code);
                if (type == null) {
                    continue;
                }
                boolean vertical = isVertical(code);
                boolean flipped = isFlipped(code);
                int width = type.width(vertical);
                int height = type.height(vertical);
                if (x >= ax && x < ax + width && y >= ay && y < ay + height) {
                    return new PlacedComponent(type, ax, ay, vertical, flipped);
                }
            }
        }
        return null;
    }

    /** Index of the wire between these holes (either direction), or -1. */
    int findWire(int x1, int y1, int x2, int y2) {
        Wire candidate = new Wire(x1, y1, x2, y2);
        for (int i = 0; i < this.wireCount; i++) {
            Wire wire = this.wireSlots[i];
            if (wire != null && wire.sameEndpoints(candidate)) {
                return i;
            }
        }
        return -1;
    }

    /** True when this exact pair of holes is already connected (either direction). */
    boolean hasWire(int x1, int y1, int x2, int y2) {
        return findWire(x1, y1, x2, y2) >= 0;
    }

    static int encode(CircuitComponent type, boolean vertical, boolean flipped) {
        return (type.id() & 7)
                | (vertical ? VERTICAL_BIT : 0)
                | (flipped ? FLIPPED_BIT : 0);
    }

    static CircuitComponent typeOf(int code) {
        return CircuitComponent.byId(code & 7);
    }

    static boolean isVertical(int code) {
        return (code & VERTICAL_BIT) != 0;
    }

    static boolean isFlipped(int code) {
        return (code & FLIPPED_BIT) != 0;
    }

    // ------------------------------------------------------------------
    // Page bridge / completed-item NBT
    // ------------------------------------------------------------------

    /**
     * Compact page-bridge string:
     * {@code R<revision>|C<64 base-32 cells>|W<wire count>|<three hex digits per wire>}.
     * The page parses it with {@code split("|")}; no JSON parser is required in the AUI runtime.
     */
    String stateString() {
        StringBuilder out = new StringBuilder(160);
        out.append('R').append(this.revision & 0xFFFF).append("|C");
        for (int code : this.cells) {
            out.append(Character.forDigit(code & 31, 32));
        }
        out.append("|W").append(this.wireCount).append('|');
        for (int i = 0; i < this.wireCount; i++) {
            Wire wire = this.wireSlots[i];
            int packed = wire == null ? 0 : wire.packed();
            String hex = Integer.toHexString(packed);
            for (int pad = hex.length(); pad < 3; pad++) {
                out.append('0');
            }
            out.append(hex);
        }
        return out.toString();
    }

    /** Snapshot for the completed item, so the composition is not lost when the menu closes. */
    CompoundTag toTag() {
        CompoundTag root = new CompoundTag();
        ListTag components = new ListTag();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int code = this.cells[index(x, y)];
                CircuitComponent type = typeOf(code);
                if (code == 0 || type == null) {
                    continue;
                }
                CompoundTag component = new CompoundTag();
                component.putString("Type", type.itemId());
                component.putInt("X", x);
                component.putInt("Y", y);
                component.putBoolean("Vertical", isVertical(code));
                component.putBoolean("Flipped", isFlipped(code));
                components.add(component);
            }
        }
        root.put("Components", components);
        int[] packedWires = new int[this.wireCount];
        for (int i = 0; i < this.wireCount; i++) {
            Wire wire = this.wireSlots[i];
            packedWires[i] = wire == null ? 0 : wire.packed();
        }
        root.putIntArray("Wires", packedWires);
        return root;
    }

    private static int index(int x, int y) {
        return y * SIZE + x;
    }

    private static boolean inBounds(int x, int y) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE;
    }

    // ------------------------------------------------------------------
    // Value types
    // ------------------------------------------------------------------

    record PlacedComponent(CircuitComponent type, int x, int y, boolean vertical, boolean flipped) {

        int width() {
            return this.type.width(this.vertical);
        }

        int height() {
            return this.type.height(this.vertical);
        }
    }

    /**
     * Two clicked holes. {@link #packed()} is the 12-bit value carried in the page bridge and in the
     * completed item's NBT: x1 bits 0..2, y1 bits 3..5, x2 bits 6..8, y2 bits 9..11.
     */
    record Wire(int x1, int y1, int x2, int y2) {

        static Wire unpack(int packed) {
            return new Wire(
                    packed & 7,
                    (packed >> 3) & 7,
                    (packed >> 6) & 7,
                    (packed >> 9) & 7);
        }

        int packed() {
            return (this.x1 & 7)
                    | ((this.y1 & 7) << 3)
                    | ((this.x2 & 7) << 6)
                    | ((this.y2 & 7) << 9);
        }

        int cost() {
            return Math.abs(this.x1 - this.x2) + Math.abs(this.y1 - this.y2);
        }

        boolean isDegenerate() {
            return this.x1 == this.x2 && this.y1 == this.y2;
        }

        boolean sameEndpoints(Wire other) {
            return (this.x1 == other.x1 && this.y1 == other.y1
                    && this.x2 == other.x2 && this.y2 == other.y2)
                    || (this.x1 == other.x2 && this.y1 == other.y2
                    && this.x2 == other.x1 && this.y2 == other.y1);
        }
    }
}
