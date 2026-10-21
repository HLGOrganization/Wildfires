package first.wildfires.soldering;

import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * The five components that can be installed on the 8x8 perfboard.
 *
 * <p>The enum is the single source of truth for footprints: {@link #baseWidth()} and
 * {@link #baseHeight()} describe the component in its <em>horizontal</em> orientation, and
 * {@link #width(boolean)} / {@link #height(boolean)} rotate that footprint for the vertical
 * orientation. Only {@link #directional()} components can be rotated; the capacitor is square.
 *
 * <p>{@link #polarized()} marks the diode. It has a {@code flipped} bit in the board cell code so
 * the player can choose which way the triangle/bar points, in addition to rotating it.
 *
 * <p>The item supplier is lazy on purpose: the enum may be class-initialised while
 * {@link SolderingRegister} is still registering its {@code RegistryObject}s, so calling
 * {@code get()} during enum construction would be premature.
 */
public enum CircuitComponent {

    RESISTOR(1, "resistor", 2, 1, true, false, () -> SolderingRegister.RESISTOR.get()),
    CAPACITOR(2, "capacitor", 2, 2, false, false, () -> SolderingRegister.CAPACITOR.get()),
    SWITCH(3, "switch", 3, 1, true, false, () -> SolderingRegister.SWITCH.get()),
    DIODE(4, "diode", 2, 1, true, true, () -> SolderingRegister.DIODE.get()),
    IC_CHIP(5, "ic_chip", 3, 2, true, false, () -> SolderingRegister.IC_CHIP.get());

    private static final CircuitComponent[] BY_ID = new CircuitComponent[6];

    static {
        for (CircuitComponent component : values()) {
            BY_ID[component.id] = component;
        }
    }

    private final int id;
    private final String itemId;
    private final int baseWidth;
    private final int baseHeight;
    private final boolean directional;
    private final boolean polarized;
    private final Supplier<Item> item;

    CircuitComponent(int id, String itemId, int baseWidth, int baseHeight,
                     boolean directional, boolean polarized, Supplier<Item> item) {
        this.id = id;
        this.itemId = itemId;
        this.baseWidth = baseWidth;
        this.baseHeight = baseHeight;
        this.directional = directional;
        this.polarized = polarized;
        this.item = item;
    }

    public int id() {
        return this.id;
    }

    public String itemId() {
        return this.itemId;
    }

    /** Horizontal footprint width, in board cells. */
    public int baseWidth() {
        return this.baseWidth;
    }

    /** Horizontal footprint height, in board cells. */
    public int baseHeight() {
        return this.baseHeight;
    }

    public boolean directional() {
        return this.directional;
    }

    public boolean polarized() {
        return this.polarized;
    }

    public Item item() {
        return this.item.get();
    }

    /** Footprint width for the requested orientation (vertical rotates directional parts). */
    public int width(boolean vertical) {
        return vertical && this.directional ? this.baseHeight : this.baseWidth;
    }

    /** Footprint height for the requested orientation (vertical rotates directional parts). */
    public int height(boolean vertical) {
        return vertical && this.directional ? this.baseWidth : this.baseHeight;
    }

    public static CircuitComponent byId(int id) {
        return id > 0 && id < BY_ID.length ? BY_ID[id] : null;
    }
}
