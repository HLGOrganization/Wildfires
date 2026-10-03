package first.wildfires.compat.sacombat;

/**
 * Geometry and artwork constants for the vertical backpack panel drawn by {@code BackpackScreen}.
 *
 * <h2>What this replaces</h2>
 *
 * <p>Right-clicking a backpack opens {@code BackpackScreen}, which draws vanilla's {@code generic_54}
 * sheet - a nine-wide grid baked into the texture. That is why a 15-slot satchel showed 18 cells: the
 * drawing code never consulted the slot count, it blitted a fixed grid, and the surplus cells had no
 * slots behind them. This class supplies the numbers that let the screen draw the modpack's own
 * artwork instead: backpack panel above, player inventory below.
 *
 * <h2>The backpack artwork</h2>
 *
 * <p>The panel occupies {@code sa_combat:textures/gui/backpack_ui.png}, region {@code (19, 52)} sized
 * {@code 131 x 101}. Reading that region row by row shows three parts:
 *
 * <pre>
 *   v =  52 ..  72   21px   header, carries the panel title
 *   v =  73 .. 144   72px   four slot rows of 18px
 *   v = 145 .. 152    8px   bottom edge
 * </pre>
 *
 * <p>The middle band is exactly 18px periodic - every row from {@code v = 73} through {@code v = 122}
 * is identical to the row 18px below it. That is what lets a taller panel replay the band instead of
 * stretching the art, so a 42-slot military backpack shows all seven rows with no scrollbar.
 *
 * <p>The panel is tinted per item, matching how the inventory overlay colours it, so a backpack looks
 * the same however it is opened.
 *
 * <h2>The player-inventory artwork</h2>
 *
 * <p>Vanilla's {@code inventory.png} is {@code 176 x 166} and spends its top 83px on armour slots and an
 * empty crafting area. That block made the window look far taller than the backpack it belonged to, so
 * the mod ships its own {@code 176 x 90} sheet holding only what this window needs:
 *
 * <pre>
 *   y =  8, 26, 44   main inventory, three rows of nine
 *   y = 66           hotbar
 * </pre>
 *
 * <p>The grid is vanilla's, unchanged: the same nine columns at {@code x = 8, 26 ... 152} on the same
 * 18px pitch, with the hotbar 58px below the last main row - exactly the spacing vanilla uses. The sheet
 * is therefore vanilla's shifted up by {@link #PLAYER_INV_DRAW_OFFSET_Y}, which is the one number that
 * ties it to the menu's slot coordinates.
 *
 * <h2>Slot alignment</h2>
 *
 * <p>The panel art puts its first slot at {@code (8, 21)} from the panel corner, while
 * {@code BackpackMenu} positions its slots at {@code panelX + 7, panelY + 17}. Drawing the art one
 * pixel left and four up lands its baked slots exactly on the menu's slots.
 *
 * <p>For the player inventory, the menu places its main rows {@code 84px} below the inventory origin and
 * the hotbar {@code 142px} below. Drawing the sheet {@code 76px} below that origin puts its rows at
 * {@code 76 + 8 = 84} and {@code 76 + 66 = 142}, so the two agree with no per-slot adjustment.
 */
public final class BackpackPanelLayout {

    /** Width of the panel artwork, and of the backpack column. */
    public static final int PANEL_WIDTH = 131;

    /** Slots per row, matching the artwork's baked grid. */
    public static final int COLUMNS = 6;

    /** Vertical pitch of the slot grid. */
    public static final int ROW_HEIGHT = 18;

    /** Where {@code BackpackMenu} positions its first slot inside the panel. */
    public static final int SLOT_INSET_X = 7;
    public static final int SLOT_INSET_Y = 17;

    /**
     * How far the backpack's title is raised above where {@code BackpackScreen} would put it.
     *
     * <p>That class draws the title at {@code panelY + 6} and lays the label relative to the artwork it
     * was written for. The panel used here has a taller header, so the text is lifted to sit centred in
     * it rather than low against the first slot row.
     */
    public static final int TITLE_SHIFT_Y = -4;

    // --- Source coordinates inside sa_combat:textures/gui/backpack_ui.png ---

    public static final int ART_U = 19;
    public static final int ART_V = 52;

    /** Height of the header above the first slot row. */
    public static final int HEADER_HEIGHT = 21;

    /** Top of the repeatable slot band. */
    public static final int BAND_V = ART_V + HEADER_HEIGHT;

    /** Rows the artwork carries before the band has to repeat. */
    public static final int BAND_ROWS = 4;

    /** Top of the bottom edge, below the band. */
    public static final int FOOTER_V = ART_V + 101 - 8;

    public static final int FOOTER_HEIGHT = 8;

    /** A cell the backpack really has. */
    public static final int SLOT_ACTIVE_U = 168;
    public static final int SLOT_ACTIVE_V = 51;

    /** A cell past the backpack's slot count, marked the way the inventory overlay marks them. */
    public static final int SLOT_LOCKED_U = 168;
    public static final int SLOT_LOCKED_V = 34;

    public static final int SLOT_ART_SIZE = 16;

    // --- Scrollbar ---

    /**
     * Where the scrollbar column sits, as an offset from the panel's own corner.
     *
     * <p>Stored panel-relative rather than as raw artwork coordinates, because the artwork is drawn with
     * a shift applied for slot alignment: an artwork coordinate {@code u} lands at
     * {@code panelX + ART_OFFSET_X + (u - ART_U)}. Keeping the panel-relative numbers here means the
     * drawing code cannot forget that adjustment.
     */
    public static final int TRACK_OFFSET_X = 116;

    /** Level with the artwork's own groove, which starts one header below the panel's top edge. */
    public static final int TRACK_OFFSET_Y = SLOT_INSET_Y;

    /**
     * The uniform part of the groove, tiled to whatever height the panel needs.
     *
     * <p>The artwork bakes in a scrollbar groove down its right edge, but it is sized for exactly four
     * slot rows: the groove is solid from {@code v = 73} to {@code v = 140}, and the four pixels below
     * that are an end cap. Replaying that band for a taller panel - which is how this screen shows
     * backpacks larger than 24 slots - therefore drags the end cap up into the middle of the panel and
     * the track reads as broken. Drawing the groove explicitly on top avoids the whole problem, because
     * the groove's own pixels are a single flat colour and tile seamlessly at any height.
     */
    public static final int GROOVE_U = 136;
    public static final int GROOVE_V = 73;
    public static final int GROOVE_WIDTH = 8;
    public static final int GROOVE_TILE_HEIGHT = 68;

    /** A small cap that closes the bottom of the groove, matching the inventory overlay's scrollbar. */
    public static final int END_CAP_U = 217;
    public static final int END_CAP_V = 84;
    public static final int END_CAP_WIDTH = 6;
    public static final int END_CAP_HEIGHT = 5;

    /**
     * The thumb.
     *
     * <p>Decoration only. The panel grows to fit however many rows the backpack has, so the track never
     * has to scroll and the thumb stays at the top.
     */
    public static final int SCROLLBAR_U = 216;
    public static final int SCROLLBAR_V = 68;
    public static final int SCROLLBAR_WIDTH = 8;
    public static final int SCROLLBAR_HEIGHT = 15;


    // --- Alignment correction, see the class note above ---

    /** Shift applied to the artwork's x so its slots line up with the menu's slot positions. */
    public static final int ART_OFFSET_X = -1;

    /** Shift applied to the artwork's y for the same reason. */
    public static final int ART_OFFSET_Y = -4;

    // --- Player inventory ---

    /** The mod's own sheet, sized {@code 176 x 90}. */
    public static final int PLAYER_INV_WIDTH = 176;
    public static final int PLAYER_INV_HEIGHT = 90;

    /**
     * Dimensions of the sheet file, which drawing code must pass explicitly.
     *
     * <p>Minecraft's short {@code blit} form assumes a 256x256 texture - it divides the source
     * coordinates by 256 rather than by the real size. This sheet is 176x90, so using that form samples
     * only the top-left {@code 176/256} by {@code 90/256} of the image and stretches it across the whole
     * area, which reads as the inventory grid being zoomed in. Every vanilla GUI sheet happens to be
     * 256x256, so the assumption usually holds and the mistake is easy to miss. The sized form of
     * {@code blit} takes these values and renders the sheet at its true scale.
     */
    public static final int PLAYER_INV_TEXTURE_WIDTH = 176;
    public static final int PLAYER_INV_TEXTURE_HEIGHT = 90;

    /**
     * Where the sheet's top edge goes, measured from the inventory origin the menu uses.
     *
     * <p>Vanilla's sheet carries its first inventory row 84px below that origin; this one carries it 8px
     * below its own top. The sheet is therefore drawn at {@code 84 - 8 = 76}, which is what makes the
     * menu's existing slot coordinates land on the right cells.
     */
    public static final int PLAYER_INV_DRAW_OFFSET_Y = 76;

    /**
     * How far the "Inventory" label is raised.
     *
     * <p>{@code BackpackScreen} puts it 72px below the inventory origin, sized by vanilla to clear a row
     * of armour slots this sheet no longer has. The sheet's own top edge is at 76, so the label has
     * {@link #GAP} pixels to live in; lifting it by 8 centres it in that band.
     */
    public static final int INVENTORY_LABEL_SHIFT_Y = -8;

    /**
     * Space between the panel and the player inventory.
     *
     * <p>Wide enough for the "Inventory" label above the sheet, which is 9px tall. A tighter gap would
     * push the text onto either the panel or the inventory frame.
     */
    public static final int GAP = 14;

    /** Panel y, chosen so the artwork's top edge lands at zero once its offset is applied. */
    public static final int PANEL_Y = -ART_OFFSET_Y;

    /** Where a slot that must stay out of the way is parked, as the inventory overlay also does. */
    public static final int HIDDEN_SLOT_POS = -2000;

    private BackpackPanelLayout() {
    }

    /** Rows needed to show {@code slotCount} slots. */
    public static int rowsFor(int slotCount) {
        return Math.max(1, (slotCount + COLUMNS - 1) / COLUMNS);
    }

    /** Height of the panel for {@code slotCount} slots. */
    public static int panelHeightFor(int slotCount) {
        return HEADER_HEIGHT + rowsFor(slotCount) * ROW_HEIGHT + FOOTER_HEIGHT;
    }

    /**
     * Height of the scrollbar groove for a panel of {@code rows} slot rows, excluding the end cap.
     *
     * <p>The track runs from the top of the first slot row down to where the bottom edge begins, minus
     * the cap that closes it. Deriving it from the row count rather than from the artwork's baked groove
     * is what keeps it correct for backpacks of every size.
     */
    public static int grooveHeightFor(int rows) {
        int trackHeight = HEADER_HEIGHT + rows * ROW_HEIGHT - TRACK_OFFSET_Y;
        return Math.max(0, trackHeight - END_CAP_HEIGHT);
    }

    /** Panel x, centring the panel within the player inventory's width. */
    public static int panelX() {
        return (imageWidth() - PANEL_WIDTH) / 2;
    }

    /** Player inventory x, centring it in the image. */
    public static int vanillaInventoryX() {
        return (imageWidth() - PLAYER_INV_WIDTH) / 2;
    }

    /**
     * Player-inventory origin y, i.e. the reference point every player slot is expressed against.
     *
     * <p>Pulled up by {@link #PLAYER_INV_DRAW_OFFSET_Y} so that the sheet, drawn that far below this
     * origin, begins right under the panel. Slot coordinates are unaffected - they are all relative to
     * this origin, so moving it moves the whole inventory as a unit.
     */
    public static int vanillaInventoryY(int slotCount) {
        return PANEL_Y + panelHeightFor(slotCount) + GAP - PLAYER_INV_DRAW_OFFSET_Y;
    }

    /** Where the sheet's top edge is actually drawn. */
    public static int playerInventoryDrawY(int slotCount) {
        return vanillaInventoryY(slotCount) + PLAYER_INV_DRAW_OFFSET_Y;
    }

    /** Container width, wide enough for the wider of the two blocks. */
    public static int imageWidth() {
        return Math.max(PANEL_WIDTH, PLAYER_INV_WIDTH);
    }

    /** Container height: panel, gap, then the player-inventory sheet. */
    public static int imageHeightFor(int slotCount) {
        return PANEL_Y + panelHeightFor(slotCount) + GAP + PLAYER_INV_HEIGHT;
    }

    /**
     * True for a slot that falls above the drawn inventory sheet.
     *
     * <p>Those are the armour and offhand slots. They are positioned relative to the inventory origin,
     * which has been pulled up so the sheet sits under the panel, so they now land over the panel
     * itself. They cannot be removed - the server sent them and both sides match slots by index - so
     * they are parked off-screen instead. They remain usable from the ordinary inventory screen.
     *
     * @param offsetY the slot's y relative to the inventory origin
     */
    public static boolean isHiddenSlot(int offsetY) {
        return offsetY < PLAYER_INV_DRAW_OFFSET_Y;
    }

    /**
     * X of the cell at visual {@code index}, using the same arithmetic {@code BackpackMenu} applies to
     * its slots so the two cannot drift apart.
     */
    public static int slotX(int panelX, int index) {
        return panelX + SLOT_INSET_X + (index % COLUMNS) * ROW_HEIGHT;
    }

    /** Y of the cell at visual {@code index}, mirroring {@link #slotX}. */
    public static int slotY(int panelY, int index) {
        return panelY + SLOT_INSET_Y + (index / COLUMNS) * ROW_HEIGHT;
    }

    /** True when the cell has a real slot behind it. */
    public static boolean isRealSlot(int index, int slotCount) {
        return index >= 0 && index < slotCount;
    }
}
