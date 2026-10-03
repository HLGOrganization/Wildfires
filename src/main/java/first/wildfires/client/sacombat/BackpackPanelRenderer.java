package first.wildfires.client.sacombat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.ogaba.sa_survival.menu.BackpackMenu;
import first.wildfires.compat.sacombat.BackpackPanelArt;
import first.wildfires.compat.sacombat.BackpackPanelLayout;
import first.wildfires.mixin.sacombat.BackpackGuiTintInvoker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the vertical backpack panel: the modpack's panel artwork on top, the player inventory below.
 *
 * <h2>What this replaces</h2>
 *
 * <p>Called from the screen's background pass in place of the original body, so the whole background
 * is painted here - panel sheet, slot sprites, then vanilla's inventory sheet. Items and tooltips are
 * still drawn afterwards by the container screen, so interaction is unchanged.
 *
 * <h2>Panel assembly</h2>
 *
 * <p>The artwork region {@code (19, 52)} sized {@code 131 x 101} is a header, four slot rows and a
 * bottom edge. Rows come from a repeating band: every row from {@code v = 73} to {@code v = 122} is
 * identical to the row 18px below it, so a panel needing more than four rows replays the band instead
 * of stretching the art. A 42-slot military backpack shows seven rows and needs no scrollbar.
 *
 * <p>Everything is drawn at {@code panelX - 1, panelY - 4} so the slots baked into the artwork land on
 * the slots {@code BackpackMenu} actually created. See {@link BackpackPanelLayout}.
 *
 * <h2>Tinting</h2>
 *
 * <p>The panel is tinted with the same colour Survivors Arsenal gives it in the inventory overlay, read
 * through an invoker on that class, so a backpack looks identical however it is opened. A tint of
 * {@code -1} means "draw as authored".
 */
public final class BackpackPanelRenderer {

    /** The mod's own player-inventory sheet: three main rows and a hotbar, no armour or crafting. */
    private static final ResourceLocation PLAYER_INVENTORY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("wildfires",
                    "textures/gui/backpack_player_inventory.png");

    private BackpackPanelRenderer() {
    }

    /**
     * Paints the background for a backpack menu.
     *
     * @return true when painted; false when this menu is not one we rebuilt, so the caller should run
     *     the original rendering instead
     */
    public static boolean render(GuiGraphics graphics, BackpackMenu menu, ItemStack stack,
                                 int guiLeft, int guiTop) {
        int slots = menu.getBackpackSlots();
        if (menu.isGenericLayout()
                || slots <= 0
                || menu.getBackpackColumns() != BackpackPanelLayout.COLUMNS) {
            // Not a panel we built - leave the original path alone rather than guess at geometry.
            return false;
        }

        int rows = menu.getBackpackRows();
        int panelX = guiLeft + menu.getBackpackPanelX();
        int panelY = guiTop + menu.getBackpackPanelY();

        float[] tint = unpack(wildfires$tintFor(stack));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        RenderSystem.setShaderColor(tint[0], tint[1], tint[2], 1.0F);
        drawPanel(graphics, panelX, panelY, rows);
        drawScrollbar(graphics, panelX, panelY, rows);
        drawSlots(graphics, panelX, panelY, rows, slots);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // Player inventory, from the mod's own sheet: three main rows and the hotbar, with no armour or
        // crafting area to crop around. It is drawn PLAYER_INV_DRAW_OFFSET_Y below the origin the menu
        // measured its slots from, which is what puts its grid lines on the slots.
        //
        // The sized form of blit is used because the sheet is 176x90. The shorter form assumes a 256x256
        // texture, so passing this sheet to it would sample only the top-left 69% x 35% and stretch that
        // over the whole area - the grid appearing zoomed in. Giving the real dimensions keeps it at
        // true scale.
        graphics.blit(PLAYER_INVENTORY_TEXTURE,
                guiLeft + menu.getVanillaInventoryX(),
                guiTop + menu.getVanillaInventoryY() + BackpackPanelLayout.PLAYER_INV_DRAW_OFFSET_Y,
                BackpackPanelLayout.PLAYER_INV_WIDTH,
                BackpackPanelLayout.PLAYER_INV_HEIGHT,
                0.0F, 0.0F,
                BackpackPanelLayout.PLAYER_INV_WIDTH,
                BackpackPanelLayout.PLAYER_INV_HEIGHT,
                BackpackPanelLayout.PLAYER_INV_TEXTURE_WIDTH,
                BackpackPanelLayout.PLAYER_INV_TEXTURE_HEIGHT);

        RenderSystem.disableBlend();
        return true;
    }

    private static void drawPanel(GuiGraphics graphics, int panelX, int panelY, int rows) {
        ResourceLocation tex = BackpackPanelArt.TEXTURE;

        // The artwork is shifted up/left so its baked slots align with the menu's slots.
        int x = panelX + BackpackPanelLayout.ART_OFFSET_X;
        int y = panelY + BackpackPanelLayout.ART_OFFSET_Y;

        graphics.blit(tex, x, y, BackpackPanelLayout.ART_U, BackpackPanelLayout.ART_V,
                BackpackPanelLayout.PANEL_WIDTH, BackpackPanelLayout.HEADER_HEIGHT);

        for (int row = 0; row < rows; row++) {
            // Replay the band once the artwork's four rows are used up.
            int v = BackpackPanelLayout.BAND_V
                    + (row % BackpackPanelLayout.BAND_ROWS) * BackpackPanelLayout.ROW_HEIGHT;
            graphics.blit(tex,
                    x, y + BackpackPanelLayout.HEADER_HEIGHT + row * BackpackPanelLayout.ROW_HEIGHT,
                    BackpackPanelLayout.ART_U, v,
                    BackpackPanelLayout.PANEL_WIDTH, BackpackPanelLayout.ROW_HEIGHT);
        }

        graphics.blit(tex,
                x, y + BackpackPanelLayout.HEADER_HEIGHT + rows * BackpackPanelLayout.ROW_HEIGHT,
                BackpackPanelLayout.ART_U, BackpackPanelLayout.FOOTER_V,
                BackpackPanelLayout.PANEL_WIDTH, BackpackPanelLayout.FOOTER_HEIGHT);
    }

    private static void drawSlots(GuiGraphics graphics, int panelX, int panelY, int rows, int slots) {
        ResourceLocation tex = BackpackPanelArt.TEXTURE;
        int cells = rows * BackpackPanelLayout.COLUMNS;

        for (int index = 0; index < cells; index++) {
            boolean real = BackpackPanelLayout.isRealSlot(index, slots);
            graphics.blit(tex,
                    BackpackPanelLayout.slotX(panelX, index),
                    BackpackPanelLayout.slotY(panelY, index),
                    real ? BackpackPanelLayout.SLOT_ACTIVE_U : BackpackPanelLayout.SLOT_LOCKED_U,
                    real ? BackpackPanelLayout.SLOT_ACTIVE_V : BackpackPanelLayout.SLOT_LOCKED_V,
                    BackpackPanelLayout.SLOT_ART_SIZE, BackpackPanelLayout.SLOT_ART_SIZE);
        }
    }

    /**
     * Draws the scrollbar: a continuous groove, a cap closing its bottom, and a thumb at the top.
     *
     * <p>The artwork already carries a groove down the panel's right edge, but it is drawn for exactly
     * four slot rows - the four pixels below its solid part are an end cap. This screen replays the
     * artwork's row band for backpacks taller than four rows, which would drag that cap up into the
     * middle of the panel and leave the track looking broken. Laying the groove down here instead keeps
     * it continuous at any size, and covers whatever the artwork baked in.
     *
     * <p>Tiling is seamless because the groove's pixels are one flat colour across the whole of
     * {@link BackpackPanelLayout#GROOVE_TILE_HEIGHT}.
     *
     * <p>The thumb is decoration only. The panel grows to fit however many rows the backpack has, so
     * there is never anything to scroll and the thumb stays at the top of the track.
     */
    private static void drawScrollbar(GuiGraphics graphics, int panelX, int panelY, int rows) {
        ResourceLocation tex = BackpackPanelArt.TEXTURE;
        int x = panelX + BackpackPanelLayout.TRACK_OFFSET_X;
        int y = panelY + BackpackPanelLayout.TRACK_OFFSET_Y;
        int grooveHeight = BackpackPanelLayout.grooveHeightFor(rows);

        for (int drawn = 0; drawn < grooveHeight; ) {
            int slice = Math.min(BackpackPanelLayout.GROOVE_TILE_HEIGHT, grooveHeight - drawn);
            graphics.blit(tex, x, y + drawn,
                    BackpackPanelLayout.GROOVE_U, BackpackPanelLayout.GROOVE_V,
                    BackpackPanelLayout.GROOVE_WIDTH, slice);
            drawn += slice;
        }

        // Cap the bottom, centred in the eight-pixel groove.
        graphics.blit(tex, x + 1, y + grooveHeight,
                BackpackPanelLayout.END_CAP_U, BackpackPanelLayout.END_CAP_V,
                BackpackPanelLayout.END_CAP_WIDTH, BackpackPanelLayout.END_CAP_HEIGHT);

        graphics.blit(tex, x, y,
                BackpackPanelLayout.SCROLLBAR_U, BackpackPanelLayout.SCROLLBAR_V,
                BackpackPanelLayout.SCROLLBAR_WIDTH, BackpackPanelLayout.SCROLLBAR_HEIGHT);
    }

    /** The overlay's own colour for this backpack, so both paths agree. */
    private static int wildfires$tintFor(ItemStack stack) {
        try {
            return BackpackGuiTintInvoker.wildfires$getBackpackGuiTint(stack);
        } catch (Throwable ignored) {
            // The overlay may be absent or reshaped; an untinted panel still draws correctly.
            return BackpackPanelArt.NO_TINT;
        }
    }

    /** ARGB to the RGB triplet the shader wants; {@code -1} becomes plain white. */
    private static float[] unpack(int argb) {
        if (argb == BackpackPanelArt.NO_TINT) {
            return new float[] {1.0F, 1.0F, 1.0F};
        }
        return new float[] {
                ((argb >> 16) & 0xFF) / 255.0F,
                ((argb >> 8) & 0xFF) / 255.0F,
                (argb & 0xFF) / 255.0F
        };
    }
}
