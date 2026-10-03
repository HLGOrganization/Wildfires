package first.wildfires.compat.sacombat;

import net.minecraft.resources.ResourceLocation;

/**
 * Panel artwork and the tint applied to it, for the backpack screen.
 *
 * <p>The panel sheet is the same one the inventory overlay uses, so a backpack looks the same whether
 * it is opened by right-clicking or by the panel key.
 *
 * <p>The tint reproduces the overlay's per-item colouring. {@code BackpackGuiTintMixin} already hooks
 * that logic for the overlay; this class covers the screen, so both paths can agree. For the Wildfires
 * leather packs the value comes from {@link LeatherBackpackGuiTint}, which matches the colour
 * Survivors Arsenal gives its own leather backpack.
 */
public final class BackpackPanelArt {

    /** The same sheet the inventory overlay draws. */
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("sa_combat", "textures/gui/backpack_ui.png");

    /** Untinted, meaning "draw the artwork as authored". */
    public static final int NO_TINT = -1;

    private BackpackPanelArt() {
    }
}
