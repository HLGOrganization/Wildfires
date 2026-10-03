package first.wildfires.jei;

import first.wildfires.Wildfires;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Draws one deposit and everything it can yield, for either the sluice or the gold pan.
 *
 * <p>The two devices get their own {@link RecipeType} and title but share this class, because the shape
 * of the recipe is identical - one deposit in, a handful of drops out with the odds attached. The only
 * per-category difference the layout cares about is how many rows of drops to leave room for, which is
 * measured from the resolved recipes before the category is built.
 *
 * <p>The layout reads left to right: the deposit goes into a slot, the device it goes into is drawn
 * underneath that slot, the vanilla furnace arrow carries the eye across, and the drops sit in a grid
 * of cells. A drop that always comes out sits on the plain slot and one that only sometimes does sits on
 * the checkered slot, which is the pairing Create uses for its own outputs, so a glance is enough to
 * tell the two apart. Both cells keep the same eighteen pixel footprint, and the odds are left to the
 * tooltip exactly as Create leaves its own.
 */
public class DepositRecipeCategory extends AbstractRecipeCategory<DepositLootRecipe> {

    public static final RecipeType<DepositLootRecipe> SLUICING =
            RecipeType.create(Wildfires.MODID, "sluicing", DepositLootRecipe.class);
    public static final RecipeType<DepositLootRecipe> PANNING =
            RecipeType.create(Wildfires.MODID, "panning", DepositLootRecipe.class);

    /** The slot artwork is eighteen pixels square and frames a sixteen pixel item. */
    private static final int SLOT = 18;

    /** JEI hangs a slot background one pixel outside the cell, which is how the art frames the item. */
    private static final int SLOT_OVERHANG = 1;

    /**
     * The even margin the page keeps around the artwork. Every other coordinate is measured from it, so
     * the arrangement stays centred however the pitch and the number of columns are tuned.
     */
    private static final int PAGE_MARGIN = 6;

    /** Where the deposit goes in. Kept at the top so the device can sit under it. */
    private static final int INPUT_X = PAGE_MARGIN + SLOT_OVERHANG;
    private static final int INPUT_Y = PAGE_MARGIN + SLOT_OVERHANG;

    /** The sluice or the pan, drawn as an item icon directly beneath the input slot, centred on it. */
    private static final int DEVICE_X = INPUT_X;
    private static final int DEVICE_Y = INPUT_Y + SLOT + 3;
    private static final int DEVICE_SIZE = 16;

    private static final int ARROW_X = INPUT_X + SLOT + 6;
    /** Centred on the input slot: the arrow's ink is fifteen pixels tall in a sixteen pixel frame. */
    private static final int ARROW_Y = INPUT_Y + 2;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 16;

    private static final int OUTPUT_X = ARROW_X + ARROW_WIDTH + 6;
    private static final int OUTPUT_Y = INPUT_Y;
    /** The gutter the drop cells keep between each other, in both directions. */
    private static final int CELL_GUTTER = 2;
    /** The drop grid's pitch: one cell plus that gutter, so the cells never touch. */
    private static final int COLUMN_PITCH = SLOT + CELL_GUTTER;
    private static final int ROW_PITCH = SLOT + CELL_GUTTER;
    private static final int COLUMNS = 4;
    private static final int ROWS = 3;
    private static final int MAX_DROPS = COLUMNS * ROWS;

    /**
     * The furnace's progress arrow, lifted out of the vanilla GUI into a texture of our own.
     *
     * <p>The arrow is the one thing players already read as "turns into", so the recipe borrows it from
     * the furnace instead of using JEI's own. It has to be a copy rather than a slice of
     * {@code minecraft:textures/gui/container/furnace.png}, because in that file the arrow is painted on
     * the furnace's grey panel, and drawn here the panel would come with it as a grey brick laid over
     * JEI's page. The copy has the panel keyed out, so only the arrow itself is drawn.
     */
    private static final ResourceLocation ARROW_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Wildfires.MODID, "textures/gui/furnace_arrow.png");

    /**
     * The two slot backgrounds, taken from Create's JEI sheet: the plain slot marks a drop that is
     * certain and the checkered one a drop that can fail, which is the same pairing Create gives its own
     * outputs and is already what a checkerboard reads as. They are shipped as copies rather than drawn
     * from {@code create:textures/...} so these pages still render without Create installed, and the
     * checkerboard's light squares, which are the page's own colour, are keyed out so whatever is behind
     * the cell shows through them.
     */
    private static final ResourceLocation SLOT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Wildfires.MODID, "textures/gui/jei_slot.png");
    private static final ResourceLocation CHANCE_SLOT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Wildfires.MODID, "textures/gui/jei_chance_slot.png");

    private final IDrawable background;
    private final IDrawable device;
    private final IDrawable arrow;
    private final IDrawable slotArt;
    private final IDrawable chanceSlotArt;

    public DepositRecipeCategory(RecipeType<DepositLootRecipe> type, Component title, ItemStack icon,
                                 IGuiHelper guiHelper, int rows) {
        super(type, title, guiHelper.createDrawableItemStack(icon), width(), height(rows));
        this.background = guiHelper.createBlankDrawable(getWidth(), getHeight());
        // The category icon is the sluice for the sluicing category and the pan for the panning one, so
        // the same stack is what belongs under the input slot.
        this.device = icon.isEmpty() ? null : guiHelper.createDrawableItemStack(icon);
        this.arrow = guiHelper.createDrawable(ARROW_TEXTURE, 0, 0, ARROW_WIDTH, ARROW_HEIGHT);
        this.slotArt = guiHelper.createDrawable(SLOT_TEXTURE, 0, 0, SLOT, SLOT);
        this.chanceSlotArt = guiHelper.createDrawable(CHANCE_SLOT_TEXTURE, 0, 0, SLOT, SLOT);
    }

    /** How many rows of drops the widest recipe of this kind needs, capped at the layout's maximum. */
    public static int rowsFor(List<DepositLootRecipe> recipes) {
        int most = 0;
        for (DepositLootRecipe recipe : recipes) {
            most = Math.max(most, recipe.drops().size());
        }
        return Math.max(1, Math.min(ROWS, (most + COLUMNS - 1) / COLUMNS));
    }

    /**
     * The page is the content plus the same margin at both ends, which is what centres the arrangement:
     * the artwork hangs one pixel outside the first and last cell, so the right hand end stops one pixel
     * short of that last cell's edge to match the room the left hand cell is given.
     */
    public static int width() {
        int columnsRight = OUTPUT_X + (COLUMNS - 1) * COLUMN_PITCH + SLOT - SLOT_OVERHANG;
        return columnsRight + PAGE_MARGIN;
    }

    /** Tall enough for the drops, but never shorter than the input slot plus the device under it. */
    public static int height(int rows) {
        int clamped = Math.max(1, Math.min(ROWS, rows));
        int dropsBottom = OUTPUT_Y + (clamped - 1) * ROW_PITCH + SLOT - SLOT_OVERHANG;
        int deviceBottom = DEVICE_Y + DEVICE_SIZE;
        return Math.max(deviceBottom, dropsBottom) + PAGE_MARGIN;
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder, DepositLootRecipe recipe, @NotNull IFocusGroup focuses) {
        builder.addInputSlot(INPUT_X, INPUT_Y)
                .setBackground(slotArt, -SLOT_OVERHANG, -SLOT_OVERHANG)
                .addItemStacks(recipe.inputs());

        List<LootDrop> drops = recipe.drops();
        for (int i = 0; i < shownCount(drops); i++) {
            LootDrop drop = drops.get(i);
            // A certain drop gets the plain slot, the same one the deposit went into; one that can fail
            // gets the checkerboard, which is Create's own mark for a chance output. Either way the cell
            // is eighteen pixels square, so the grid keeps its even pitch.
            IRecipeSlotBuilder cell = builder.addOutputSlot(outputX(i), outputY(i))
                    .addItemStack(drop.stack())
                    .setBackground(alwaysDrops(drop) ? slotArt : chanceSlotArt, -SLOT_OVERHANG, -SLOT_OVERHANG);
            if (!alwaysDrops(drop)) {
                // The odds are left to the tooltip, the way Create leaves its own. The checkerboard
                // already says the cell is a chance, and a number on the page would only be a second
                // thing to read in a grid that is meant to be scanned at a glance.
                cell.addRichTooltipCallback((view, tooltip) -> tooltip.add(chanceLine(drop)));
            }
        }
    }

    @Override
    public void createRecipeExtras(@NotNull IRecipeExtrasBuilder extras, DepositLootRecipe recipe,
                                   @NotNull IFocusGroup focuses) {
        // getBackground() is deprecated in this JEI version and marked for removal, so the background
        // and everything drawn on it are declared here instead.
        extras.addDrawable(background, 0, 0);
        if (device != null) {
            extras.addDrawable(device, DEVICE_X, DEVICE_Y);
        }
        extras.addDrawable(arrow, ARROW_X, ARROW_Y);
    }

    /** A drop is certain when nothing can roll it away, whatever the table it came from. */
    private static boolean alwaysDrops(LootDrop drop) {
        return drop.chance() >= 1.0F;
    }

    private static int shownCount(List<LootDrop> drops) {
        return Math.min(drops.size(), MAX_DROPS);
    }

    private static int outputX(int index) {
        return OUTPUT_X + (index % COLUMNS) * COLUMN_PITCH;
    }

    private static int outputY(int index) {
        return OUTPUT_Y + (index / COLUMNS) * ROW_PITCH;
    }

    /** The odds in words for the tooltip, where the translated line already says whether they are exact. */
    private static Component chanceLine(LootDrop drop) {
        String key = drop.exact() ? "wildfires.jei.chance" : "wildfires.jei.chance_approx";
        return Component.translatable(key, percentText(drop) + "%");
    }

    /**
     * The number itself, before it is marked up. A chance below a tenth of a percent is shown as such
     * rather than rounded away to nothing, and anything that rounds up to a hundred is held at 99: a
     * drop that could actually fail is never written as certain.
     */
    private static String percentText(LootDrop drop) {
        float percent = drop.chance() * 100.0F;
        if (percent < 0.05F) {
            return "<0.1";
        }
        if (percent < 10.0F) {
            String value = String.valueOf(Math.round(percent * 10.0F) / 10.0F);
            return value.endsWith(".0") ? value.substring(0, value.length() - 2) : value;
        }
        return String.valueOf(Math.min(99, Math.round(percent)));
    }
}
