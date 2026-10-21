package first.wildfires.soldering;

import com.sighs.apricityui.screen.ApricityContainerMenu;
import first.wildfires.Wildfires;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Items and the menu type for the perfboard editor.
 *
 * <p>The menu type is a Forge {@link IForgeMenuType}: the server writes the AUI slot layout into the
 * open-screen packet, the client reads it before constructing the menu. AUI's screen then binds the
 * page's {@code <container bind="player">} slots to this exact layout.
 *
 * <p>The old {@code solder_wire} and {@code electronic_component} items are kept registered for
 * backwards compatibility with existing saves and pack scripts, even though the new editor uses
 * {@code copper_wire} and the five dedicated component items instead.
 */
public final class SolderingRegister {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, Wildfires.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Wildfires.MODID);

    /** Non-consuming tool that opens the editor. */
    public static final RegistryObject<Item> SOLDERING_IRON = ITEMS.register(
            "soldering_iron", () -> new SolderingIronItem(new Item.Properties().stacksTo(1)));

    /** Blank board: one is consumed when the player presses Complete. */
    public static final RegistryObject<Item> CIRCUIT_BOARD = ITEMS.register(
            "circuit_board", () -> new Item(new Item.Properties()));

    /** Legacy weld wire; kept for old saves. */
    public static final RegistryObject<Item> SOLDER_WIRE = ITEMS.register(
            "solder_wire", () -> new Item(new Item.Properties()));

    /** Legacy generic component; kept for old saves. */
    public static final RegistryObject<Item> ELECTRONIC_COMPONENT = ITEMS.register(
            "electronic_component", () -> new Item(new Item.Properties()));

    /** Result of pressing Complete. Carries the board composition in its NBT. */
    public static final RegistryObject<Item> SOLDERED_CIRCUIT = ITEMS.register(
            "soldered_circuit", () -> new SolderedCircuitItem(new Item.Properties()));

    // ------------------------------------------------------------------
    // 8x8 perfboard parts
    // ------------------------------------------------------------------

    /** 1x2, horizontal or vertical. */
    public static final RegistryObject<Item> RESISTOR = ITEMS.register(
            "resistor", () -> new CircuitComponentItem(new Item.Properties(), CircuitComponent.RESISTOR));

    /** 2x2, no orientation. */
    public static final RegistryObject<Item> CAPACITOR = ITEMS.register(
            "capacitor", () -> new CircuitComponentItem(new Item.Properties(), CircuitComponent.CAPACITOR));

    /** 1x3, horizontal or vertical. */
    public static final RegistryObject<Item> SWITCH = ITEMS.register(
            "switch", () -> new CircuitComponentItem(new Item.Properties(), CircuitComponent.SWITCH));

    /** 1x2, horizontal or vertical, plus a polarity flip. */
    public static final RegistryObject<Item> DIODE = ITEMS.register(
            "diode", () -> new CircuitComponentItem(new Item.Properties(), CircuitComponent.DIODE));

    /** 2x3 (3x2 horizontally, 2x3 vertically). */
    public static final RegistryObject<Item> IC_CHIP = ITEMS.register(
            "ic_chip", () -> new CircuitComponentItem(new Item.Properties(), CircuitComponent.IC_CHIP));

    /** Trace material: one item per Manhattan-distance step of a new wire. */
    public static final RegistryObject<Item> COPPER_WIRE = ITEMS.register(
            "copper_wire", () -> new Item(new Item.Properties()));

    /**
     * Declared as {@code MenuType<ApricityContainerMenu>} so AUI's own container screen can be
     * registered for it unchanged; the factory returns a {@link SolderingMenu}, which is a subtype.
     */
    public static final RegistryObject<MenuType<ApricityContainerMenu>> SOLDERING_MENU = MENUS.register(
            "soldering", () -> IForgeMenuType.create(
                    (id, inventory, buffer) -> new SolderingMenu(id, inventory, buffer)));

    private SolderingRegister() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        MENUS.register(eventBus);
    }
}
