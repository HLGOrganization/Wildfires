package first.wildfires.client.smithing;

import com.mojang.blaze3d.vertex.PoseStack;
import first.wildfires.Wildfires;
import first.wildfires.smithing.ForgePath;
import first.wildfires.smithing.ForgePathFinder;
import first.wildfires.smithing.ForgeTargets;
import net.dries007.tfc.common.capabilities.forge.ForgeRule;
import net.dries007.tfc.common.capabilities.forge.ForgeStep;
import net.dries007.tfc.common.recipes.AnvilRecipe;
import net.dries007.tfc.common.recipes.TFCRecipeTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;

/**
 * The forging manual: the anvil recipes whose one-click forge this player has unlocked, each with the
 * shortest way to forge it by hand.
 *
 * <h2>What it lists</h2>
 * Only unlocked recipes. A recipe still being worked towards is deliberately left out - showing the ones
 * that have not happened yet would turn a record of what a player can do into a list of what they cannot.
 *
 * <h2>What each entry shows</h2>
 * The recipe's output in the slot, then its shortest sequence of strikes as icons, one per run of the same
 * strike, with the run's length in the corner of the icon, and how many times the recipe has been forged at
 * the right. Rules are not spelled out: they are what the sequence is, and the anvil only judges the last
 * three strikes, so a player following the icons is following the rules without having to hold them in
 * mind. The item's name, its forging experience, the work value its sequence ends on and the exact path
 * length are in the hover tooltip, which is also where an unavailable path is reported.
 *
 * <h2>Where the contents come from</h2>
 * The recipe list is read from the client's own recipe manager, so it always matches the recipes the player
 * is actually able to make - including anything a datapack adds - with no server round trip and no list to
 * keep in sync. Which of them are unlocked, and their counts, are the local player's, pushed by the server
 * (see {@link PerfectForgeClientData}). The world seed behind every target is pushed the same way
 * (see {@link ForgeSeedClientData}); without it there are no targets, and the manual says so.
 *
 * <p>Entries are ordered by how often they have been forged, so the most-practised are on the first page.
 */
public class ForgingManualScreen extends Screen {

    private static final ResourceLocation BACKGROUND =
            Wildfires.rl("textures/gui/forging_manual_background.png");
    private static final ResourceLocation SLOTS =
            Wildfires.rl("textures/gui/forging_manual_slots.png");
    private static final ResourceLocation ICONS =
            Wildfires.rl("textures/gui/forging_step_icons.png");

    /** The book art, and the geometry measured off it. */
    private static final int BOOK_WIDTH = 293;
    private static final int BOOK_HEIGHT = 178;
    /** Slot frames are 18 wide, 16 apart per row; the right page is exactly one page over. */
    private static final int PAGE_WIDTH = 137;
    private static final int LEFT_SLOT_X = 23;
    private static final int FIRST_ROW_Y = 13;
    private static final int ROW_PITCH = 26;
    private static final int ROWS = 6;
    private static final int PER_SPREAD = ROWS * 2;

    /** Where the icons and the forge count go, right of a slot. */
    private static final int ICON_X = 41;
    private static final int ICON_SIZE = 16;
    private static final int ICON_RIGHT = 140;
    private static final int ICON_SPACE = ICON_RIGHT - ICON_X;
    private static final int NUMBER_GAP = 2;
    /** Widest first, so a row only tightens up when it has to. */
    private static final int[] PITCHES = {16, 15, 14, 13, 12, 11};
    /** The run count is small, but it still has to be legible: the sheet leaves the corner empty for it. */
    private static final float COUNT_SCALE = 0.75F;
    /** The outer margin of each page, which turns it. */
    private static final int TURN_STRIP = 20;

    private static final int COLOR_INK = 0xFF4B2E21;
    private static final int COLOR_INK_SOFT = 0xFF673D2A;
    private static final int COLOR_ROW_HOVER = 0x22C08A3E;

    /** The icons in the sheet, in the sheet's order: the lang file's step names, in TFC's own words. */
    private static final List<ForgeStep> ART_ORDER = List.of(
            ForgeStep.PUNCH, ForgeStep.BEND, ForgeStep.UPSET, ForgeStep.SHRINK,
            ForgeStep.HIT_LIGHT, ForgeStep.HIT_MEDIUM, ForgeStep.HIT_HARD, ForgeStep.DRAW);

    private final List<Entry> entries = new ArrayList<>();
    private int spread;

    public ForgingManualScreen() {
        super(Component.translatable("item.wildfires.forging_manual"));
    }

    /** One listed recipe, with the shortest path worked out when the manual is opened. */
    private record Entry(ResourceLocation id, ItemStack icon, Component name, ForgePath path) {
    }

    @Override
    protected void init() {
        entries.clear();

        Minecraft minecraft = Minecraft.getInstance();
        OptionalLong seed = worldSeed(minecraft);

        if (minecraft.level != null) {
            RegistryAccess access = minecraft.level.registryAccess();
            for (AnvilRecipe recipe : minecraft.level.getRecipeManager()
                    .getAllRecipesFor(TFCRecipeTypes.ANVIL.get())) {
                ResourceLocation id = recipe.getId();
                if (!PerfectForgeClientData.isUnlocked(id)) {
                    // The manual records what has been unlocked. A recipe still being worked towards is not
                    // its business - that progress belongs to the anvil that will unlock it.
                    continue;
                }
                ItemStack icon = iconFor(recipe, access);
                entries.add(new Entry(id, icon,
                        icon.isEmpty() ? Component.literal(id.getPath()) : icon.getHoverName(),
                        pathFor(seed, id, recipe)));
            }
        }

        // Most-practised first, which is also the number written on each row.
        entries.sort(Comparator
                .comparingInt((Entry entry) -> -PerfectForgeClientData.forged(entry.id()))
                .thenComparing(entry -> entry.id().toString()));

        spread = Mth.clamp(spread, 0, spreads() - 1);
    }

    /**
     * The world seed, from the server if it has told us, or from the local world in single player.
     *
     * <p>A client on a server has no other way to know it, and only the server can derive it; empty means
     * no target can be worked out yet.
     */
    private static OptionalLong worldSeed(Minecraft minecraft) {
        OptionalLong known = ForgeSeedClientData.seed();
        if (known.isPresent()) {
            return known;
        }
        MinecraftServer server = minecraft.getSingleplayerServer();
        return server == null ? OptionalLong.empty() : OptionalLong.of(server.overworld().getSeed());
    }

    /** The shortest sequence that forges this recipe, or an unknown path when the seed is not known yet. */
    private static ForgePath pathFor(OptionalLong seed, ResourceLocation id, AnvilRecipe recipe) {
        if (seed.isEmpty()) {
            return ForgePath.unknown();
        }
        ForgeRule[] rules = recipe.getRules();
        return ForgePathFinder.instance().find(ForgeTargets.compute(seed.getAsLong(), id), rules);
    }

    /**
     * A recipe's representative output, or an empty stack if it has none.
     *
     * <p>Anvil recipes may copy their input into the result, and those have no output to show without one;
     * that is not an error, so it falls back to the recipe's own name rather than failing to list.
     */
    private static ItemStack iconFor(AnvilRecipe recipe, RegistryAccess access) {
        try {
            ItemStack result = recipe.getResultItem(access);
            return result == null ? ItemStack.EMPTY : result;
        } catch (RuntimeException needsInput) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int left = bookLeft();
        int top = bookTop();
        graphics.blit(BACKGROUND, left, top, 0, 0, BOOK_WIDTH, BOOK_HEIGHT, BOOK_WIDTH, BOOK_HEIGHT);

        int hovered = hoveredIndex(mouseX, mouseY);
        int base = spread * PER_SPREAD;

        // The wash goes under the slot frames so they stay crisp.
        for (int i = 0; i < visibleEntries(); i++) {
            renderHover(graphics, i, i == hovered);
        }
        graphics.blit(SLOTS, left, top, 0, 0, BOOK_WIDTH, BOOK_HEIGHT, BOOK_WIDTH, BOOK_HEIGHT);

        for (int i = 0; i < visibleEntries(); i++) {
            renderEntry(graphics, entries.get(base + i), i / ROWS, i % ROWS);
        }
        renderEmptyPage(graphics);
        renderTurnArrows(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (hovered >= 0) {
            graphics.renderComponentTooltip(font, tooltipFor(entries.get(base + hovered)), mouseX, mouseY);
        }
    }

    /** One entry: its output in the slot, its strikes beside it, and the times it has been forged. */
    private void renderEntry(GuiGraphics graphics, Entry entry, int column, int row) {
        int slotX = slotX(column);
        int slotY = slotY(row);
        int pageX = bookLeft() + columnX(column);

        if (!entry.icon().isEmpty()) {
            graphics.renderItem(entry.icon(), slotX, slotY);
        }

        int forged = PerfectForgeClientData.forged(entry.id());
        int forgedWidth = font.width(Integer.toString(forged));

        List<ForgePath.Run> runs = entry.path().runs();
        if (!runs.isEmpty()) {
            int pitch = iconPitch(runs.size(), forgedWidth);

            int x = pageX + ICON_X;
            for (ForgePath.Run run : runs) {
                graphics.blit(ICONS, x, slotY, ART_ORDER.indexOf(run.step()) * ICON_SIZE, 0,
                        ICON_SIZE, ICON_SIZE, ICON_SIZE * ART_ORDER.size(), ICON_SIZE);
                if (run.count() > 1) {
                    drawRepeatCount(graphics, x, slotY, run.count());
                }
                x += pitch;
            }
        }

        // The tally is the one figure on the row that owes nothing to the path, so it is written whether or
        // not the strikes could be worked out.
        graphics.drawString(font, Component.literal(Integer.toString(forged)),
                pageX + ICON_RIGHT - forgedWidth, slotY + 4, COLOR_INK, false);
    }

    /**
     * How far apart to space the icons so the row and its forge count still fit the page.
     *
     * <p>Widest spacing first: a row of four gets the full 16, and only a row with more strikes than that
     * tightens up, and never onto the number.
     */
    private static int iconPitch(int icons, int numberWidth) {
        for (int pitch : PITCHES) {
            int width = (icons - 1) * pitch + ICON_SIZE;
            if (width + NUMBER_GAP + numberWidth <= ICON_SPACE) {
                return pitch;
            }
        }
        return PITCHES[PITCHES.length - 1];
    }

    /** The run's length, tucked into the icon's bottom-right corner so the icons stay on the page. */
    private void drawRepeatCount(GuiGraphics graphics, int x, int y, int count) {
        Component text = Component.literal(Integer.toString(count));
        float width = font.width(text) * COUNT_SCALE;
        drawScaledText(graphics, text, x + ICON_SIZE - width - 1.0F, y + 11.0F, COUNT_SCALE, COLOR_INK);
    }

    private void renderHover(GuiGraphics graphics, int visibleIndex, boolean hovered) {
        if (!hovered) {
            return;
        }
        int column = visibleIndex / ROWS;
        int row = visibleIndex % ROWS;
        graphics.fill(slotX(column) - 1, slotY(row) - 1,
                bookLeft() + columnX(column) + ICON_RIGHT + 1, slotY(row) + ICON_SIZE + 1, COLOR_ROW_HOVER);
    }

    /**
     * The arrow on each side that says a page is there to turn to, and turns it when clicked.
     *
     * <p>Both are the same mark the artwork's margins leave room for; a spread with nothing beyond it gets
     * no arrow, so the pair also says how far into the book the reader is.
     */
    private void renderTurnArrows(GuiGraphics graphics) {
        if (spread > 0) {
            graphics.drawString(font, "<", bookLeft() + 7, bookTop() + 84, COLOR_INK_SOFT, false);
        }
        if (spread < spreads() - 1) {
            graphics.drawString(font, ">", bookLeft() + BOOK_WIDTH - 13, bookTop() + 84, COLOR_INK_SOFT, false);
        }
    }

    /** The left page carries the message when there is nothing unlocked yet. */
    private void renderEmptyPage(GuiGraphics graphics) {
        if (!entries.isEmpty()) {
            return;
        }
        Component text = Component.translatable("wildfires.manual.empty");
        float scale = 0.75F;
        float width = font.width(text) * scale;
        drawScaledText(graphics, text, bookLeft() + 80 - width / 2.0F, bookTop() + 76, scale, COLOR_INK);
    }

    private void drawScaledText(GuiGraphics graphics, Component text, float x, float y, float scale, int color) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        pose.popPose();
    }

    private List<Component> tooltipFor(Entry entry) {
        List<Component> lines = new ArrayList<>(5);
        lines.add(entry.name().copy().withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("wildfires.manual.forged",
                PerfectForgeClientData.xp(entry.id()), PerfectForgeClientData.xpToUnlock())
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("wildfires.manual.count", PerfectForgeClientData.forged(entry.id()))
                .withStyle(ChatFormatting.GRAY));

        ForgePath path = entry.path();
        if (path.target() < 0) {
            lines.add(Component.translatable("wildfires.manual.noseed").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.add(Component.translatable("wildfires.manual.target", path.target())
                    .withStyle(ChatFormatting.GOLD));
            if (path.isEmpty()) {
                lines.add(Component.translatable("wildfires.manual.nopath").withStyle(ChatFormatting.GRAY));
            } else {
                lines.add(Component.translatable("wildfires.manual.path", path.length())
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        return lines;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (inTurnStrip(mouseX, mouseY, true) && spread > 0) {
                turn(-1);
                return true;
            }
            if (inTurnStrip(mouseX, mouseY, false) && spread < spreads() - 1) {
                turn(1);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** The outer margin of either page: outside the artwork's content, still on the book. */
    private boolean inTurnStrip(double mouseX, double mouseY, boolean left) {
        if (mouseY < bookTop() || mouseY > bookTop() + BOOK_HEIGHT) {
            return false;
        }
        if (left) {
            return mouseX >= bookLeft() && mouseX <= bookLeft() + TURN_STRIP;
        }
        return mouseX >= bookLeft() + BOOK_WIDTH - TURN_STRIP && mouseX <= bookLeft() + BOOK_WIDTH;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0.0D && spreads() > 1) {
            turn(delta > 0.0D ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            turn(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            turn(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void turn(int delta) {
        spread = Mth.clamp(spread + delta, 0, spreads() - 1);
    }

    /** A book is read while playing, so it does not stop the world. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** How many entries are on this spread. */
    private int visibleEntries() {
        return Math.min(PER_SPREAD, Math.max(0, entries.size() - spread * PER_SPREAD));
    }

    private int spreads() {
        return Math.max(1, (entries.size() + PER_SPREAD - 1) / PER_SPREAD);
    }

    /** Which visible entry the mouse is over, if any. */
    private int hoveredIndex(int mouseX, int mouseY) {
        for (int i = 0; i < visibleEntries(); i++) {
            int column = i / ROWS;
            int row = i % ROWS;
            int slotX = slotX(column);
            int slotY = slotY(row);
            if (mouseX >= slotX - 1 && mouseX < bookLeft() + columnX(column) + ICON_RIGHT + 1
                    && mouseY >= slotY - 1 && mouseY < slotY + ICON_SIZE + 1) {
                return i;
            }
        }
        return -1;
    }

    private int bookLeft() {
        return (width - BOOK_WIDTH) / 2;
    }

    private int bookTop() {
        return (height - BOOK_HEIGHT) / 2;
    }

    private static int columnX(int column) {
        return column * PAGE_WIDTH;
    }

    private int slotX(int column) {
        return bookLeft() + LEFT_SLOT_X + columnX(column);
    }

    private int slotY(int row) {
        return bookTop() + FIRST_ROW_Y + row * ROW_PITCH;
    }
}
