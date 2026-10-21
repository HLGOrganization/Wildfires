package first.wildfires.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.filter.AttributeFilterMenu;
import com.simibubi.create.content.logistics.filter.AttributeFilterScreen;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.utility.CreateLang;
import first.wildfires.client.screen.TemperatureModeIcons.Mode;
import first.wildfires.mixin.create.AttributeFilterMenuAccessor;
import first.wildfires.mixin.create.AttributeFilterScreenAccessor;
import first.wildfires.network.TemperatureFilterSyncPacket;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds Create's attribute filter a temperature condition row, and to that row a small click-through icon
 * that switches the condition between three independent states:
 *
 * <ul>
 *   <li><b>unconditional</b> ({@code any}) - the text box is visible and takes a hand-written numeric
 *       expression such as {@code >800}. This is what the row has always been, and it is also what an older
 *       save with only numeric expressions loads into, so nothing already saved changes meaning.</li>
 *   <li><b>workable</b> / <b>weldable</b> - the text box is hidden and its place shows the state's localized
 *       name; the condition is TFC's own {@code IHeat.canWork()} / {@code canWeld()}.</li>
 * </ul>
 *
 * <p>The three are equal and not nested: the state decides what the one condition is. A state that owns the
 * row cannot apply at the same time as a numeric expression - there is no text box to type one into - but the
 * expressions are still <b>kept</b> in the NBT, so switching away to workable/weldable and back, or closing
 * and reopening the screen, restores what the player had typed. Which of the two kinds actually decides a
 * match is settled in {@code AttributeFilterItemStackMixin}, not here.
 *
 * <p><b>Storage is unchanged.</b> Everything still goes through the {@code TemperatureFilters} string list in
 * the filter item's NBT - the key is not renamed and no new key is introduced. workable/weldable append their
 * own token to that same list; the unconditional state appends nothing, so a save that never used the icon
 * keeps exactly its original entries.
 *
 * <p>The icon itself lives entirely in {@link TemperatureModeIcons}; this class only knows the state machine
 * and where to put the button.
 */
public class TemperatureAttributeFilterScreen extends AttributeFilterScreen {

    /** The NBT key, unchanged since the row was introduced. Old saves must keep working. */
    private static final String TEMPERATURE_FILTERS_KEY = "TemperatureFilters";

    private static final Pattern TEMPERATURE_PATTERN = Pattern.compile("^([><=]=?)\\s*(-?\\d+(?:\\.\\d+)?)$");

    private static final String LANG_ROOT = "wildfires.attribute_filter.temperature.";

    /** Where the text box sits inside the panel. Unchanged from the original row. */
    private static final int INPUT_OFFSET_X = 42;
    private static final int INPUT_OFFSET_Y = 66;
    private static final int INPUT_WIDTH = 24;
    private static final int INPUT_HEIGHT = 14;

    /**
     * How far the row is lifted above the panel: one unit of GUI z, the smallest lift that still puts every
     * pixel of it in front of the panel the base class blits.
     *
     * <p>The base class's {@code renderBg} ends with a blit of the whole opaque 241x85 panel, so the row has
     * to be in front of it rather than merely drawn after it - see {@link #renderBg}. Lifting is what
     * {@code renderForeground} already does for the item count (150), and the panel is at 0, so anything
     * above 0 is enough.
     */
    private static final float ROW_Z = 1.0F;

    /**
     * How far right Create's three whitelist buttons are moved so that the temperature row fits beside the
     * filter slot.
     *
     * <p>Create puts them on the row directly under the slot - panel x 38 / 56 / 74, y 61, each 18x18, the
     * three of them added together at the end of its own {@code init()} - and under {@code setWindowOffset
     * (-11, 7)} the same panel origin the temperature row uses. The temperature row needs that band: the
     * input box is at {@link #INPUT_OFFSET_X},66 and the state icon at {@link TemperatureModeIcons#ICON_OFFSET_X},
     * {@link TemperatureModeIcons#ICON_OFFSET_Y}. With both present the first two buttons land on the text box
     * and the third on the icon, which is exactly what a player sees as "the temperature controls are hidden
     * behind buttons".
     *
     * <p>The row cannot be the thing that moves: to its left are the slot and the count drawn over it, and
     * further left the panel edge; to its right Create's own add buttons. So the three are pushed sideways,
     * which is also what an earlier version of this screen did: its {@code EXISTING_BUTTONS_SHIFT} was
     * {@code INPUT_OFFSET_X + PRESET_BUTTON_STEP * 3 + 2 - 38} = {@code 42 + 78 + 2 - 38} = 84, for a row of
     * three 24-wide presets ending at panel x 116 with a 2px margin. Those presets are gone - the row is one
     * 24-wide box and one icon now - so the number that matters is the one that clears what is actually there:
     * the icon's click target is the icon's own frame and ends at
     * {@code TemperatureModeIcons.HITBOX_OFFSET_X + HITBOX_WIDTH} = 84, and 84 puts the first button at
     * 38+84 = 122, a 38px gap.
     *
     * <p>84 rather than a value recomputed from today's narrower row, so that the panel comes back to the
     * layout a player accepted before rather than to a new one: the three buttons keep their 18px step, sit
     * at x 122 / 140 / 158, and their right edge is 176 - exactly the width of the player inventory graphic
     * the window is sized against ({@code AbstractFilterScreen.init} takes the max of the two), so nothing
     * leaves the panel. They keep their y and their own tooltips and callbacks untouched.
     */
    private static final int EXISTING_BUTTONS_SHIFT = 84;

    /**
     * The panel-relative x Create gives its three whitelist buttons, in the order
     * {@link #moveCreateWhitelistButtons()} reads them: {@code whitelistDis}, {@code whitelistCon},
     * {@code blacklist}. These are the {@code bipush 38 / 56 / 74} offsets of its own {@code init()}, kept
     * here so the new position can be worked out absolutely instead of by a delta that would accumulate.
     */
    private static final int[] CREATE_BUTTON_X = {38, 56, 74};

    /** Staging list, committed to NBT when the screen closes. Holds numeric expressions only. */
    private final List<String> pendingTemperatureFilters = new ArrayList<>();

    /** The one state the row is currently in. */
    private Mode temperatureMode = Mode.ANY;

    private EditBox temperatureInput;
    private ModeIconButton temperatureModeButton;

    public TemperatureAttributeFilterScreen(AttributeFilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();

        this.temperatureInput = new EditBox(this.font,
                this.leftPos + INPUT_OFFSET_X, this.topPos + INPUT_OFFSET_Y,
                INPUT_WIDTH, INPUT_HEIGHT, Component.literal(""));
        this.temperatureInput.setMaxLength(12);
        this.temperatureInput.setHint(Component.literal(">=0")
                .withStyle(style -> style.withColor(ChatFormatting.GRAY)));
        this.temperatureInput.setCanLoseFocus(true);
        this.temperatureInput.setBordered(false);
        this.addRenderableWidget(this.temperatureInput);

        this.temperatureModeButton = new ModeIconButton(
                this.leftPos + TemperatureModeIcons.HITBOX_OFFSET_X,
                this.topPos + TemperatureModeIcons.ICON_OFFSET_Y + TemperatureModeIcons.HITBOX_OFFSET_Y);
        this.temperatureModeButton.withCallback(this::cycleTemperatureMode);
        this.addRenderableWidget(this.temperatureModeButton);

        moveCreateWhitelistButtons();

        loadExistingFilters();
        applyModeToWidgets();
    }

    /**
     * Moves Create's three whitelist buttons out from under the temperature row.
     *
     * <p>{@code super.init()} has already created and added them - they are that screen's own fields, set up
     * on the row below the filter slot - so this is the one place left to say where they go. The accessors are
     * the same ones the tooltip code uses, and the buttons are still Create's: this only changes their
     * position, not what they do or say. {@link #EXISTING_BUTTONS_SHIFT} has the number and why it is that.
     *
     * <p>The new x is worked out from the positions Create itself uses - {@code leftPos + 38 / 56 / 74}, the
     * three literal offsets in its {@code init()} - rather than from each button's current x plus the delta.
     * The delta form is what an earlier version of this screen used, and it is wrong: {@code init()} runs
     * again on every window resize, and a button that is moved relative to where it already is ends up 84px
     * further right every time the player resizes the window. Absolute means the second run is a no-op.
     *
     * <p>{@code leftPos} is read here for the same reason: Create has already applied its own window offset,
     * so panel-relative plus {@code leftPos} is the on-screen position it would have used itself.
     */
    private void moveCreateWhitelistButtons() {
        AttributeFilterScreenAccessor accessor = (AttributeFilterScreenAccessor) this;
        IconButton[] buttons = {
                accessor.wildfires$getWhitelistDis(),
                accessor.wildfires$getWhitelistCon(),
                accessor.wildfires$getBlacklist()
        };
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i] == null) {
                continue;
            }
            buttons[i].setX(this.leftPos + CREATE_BUTTON_X[i] + EXISTING_BUTTONS_SHIFT);
        }
    }

    /**
     * Reads the stored list. A state token sets the row's state; numeric expressions are staged no matter
     * what, even when a state token is present, so a value typed under the unconditional state survives a
     * detour through workable/weldable <b>and</b> a screen reopen. An unknown token leaves the row in the
     * unconditional state rather than failing, and a list holding more than one state token resolves by the
     * order they were written - the states are mutually exclusive now, so there is nothing to reconcile.
     */
    private void loadExistingFilters() {
        pendingTemperatureFilters.clear();
        temperatureMode = Mode.ANY;

        ItemStack filterStack = this.menu.contentHolder;
        if (filterStack.isEmpty()) {
            return;
        }
        CompoundTag tag = filterStack.getTag();
        if (tag == null || !tag.contains(TEMPERATURE_FILTERS_KEY)) {
            return;
        }
        ListTag filters = tag.getList(TEMPERATURE_FILTERS_KEY, Tag.TAG_STRING);
        for (int i = 0; i < filters.size(); i++) {
            String entry = filters.getString(i);
            Mode preset = presetOrNull(entry);
            if (preset != null) {
                temperatureMode = preset;
            } else if (TEMPERATURE_PATTERN.matcher(entry).matches()) {
                pendingTemperatureFilters.add(entry);
            }
            // Anything else is an unknown token from another version: ignored, and dropped the moment the
            // screen writes its own state back.
        }
    }

    /** @return the state a token names, or null when the token is not a state this version knows. */
    private static Mode presetOrNull(String entry) {
        for (Mode mode : Mode.values()) {
            if (mode != Mode.ANY && mode.token().equals(entry)) {
                return mode;
            }
        }
        return null;
    }

    /**
     * Advances unconditional -> workable -> weldable -> unconditional. A state is a state, not a flag: the
     * previous state's token is dropped when the next one is written, so the stored list never describes two
     * states at once.
     *
     * <p>The staged numeric expressions are deliberately left alone. They do not apply while workable/weldable
     * is showing - the mixin ignores numbers whenever a state token is present - but they are still there to
     * come back to, including after the screen is closed.
     *
     * <p><b>A switch only changes local state; it deliberately does not talk to the server.</b> Create's
     * {@code AbstractFilterScreen.containerTick()} evaluates
     * {@code !menu.player.getMainHandItem().equals(menu.contentHolder, false)} on every tick and calls
     * {@code menu.player.closeContainer()} the moment it fails. The {@code equals} deciding that is Forge's
     * {@code IForgeItemStack.equals(ItemStack, boolean)}, which compares item, count and
     * {@code Objects.equals(getTag(), getTag())} - the NBT <i>is</i> part of the test even with
     * {@code matchNBT = false}. So the instant a sync packet rewrites the held filter's NBT, the screen closes
     * itself on the next tick, silently. Committing here did exactly that on every single click. The write is
     * left to {@link #removed()}, which already runs when the screen closes anyway.
     */
    private void cycleTemperatureMode() {
        temperatureMode = temperatureMode.next();
        applyModeToWidgets();

        if (temperatureMode.usesTextBox()) {
            this.temperatureInput.setFocused(true);
        }
        this.playUiSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0F, 1.0F);
    }

    /** Shows or hides the text box for the current state and lays the icon out to match. */
    private void applyModeToWidgets() {
        boolean textBox = temperatureMode.usesTextBox();
        this.temperatureInput.setVisible(textBox);
        this.temperatureInput.setFocused(textBox);
        if (this.temperatureModeButton != null) {
            this.temperatureModeButton.setMode(this.temperatureMode);
        }
    }

    /**
     * The list that gets written: every staged numeric expression, plus the state's token when that state
     * needs one. The unconditional state appends nothing - a filter that never used the icon therefore keeps
     * exactly its original entries, and reading it back gives the unconditional state again.
     *
     * <p>This is the <b>stored</b> list and it is deliberately not what the player reads. Use
     * {@link #buildActiveTemperatureFilterEntries()} for the count and the tooltip.
     */
    private List<String> buildTemperatureFilterEntries() {
        List<String> entries = new ArrayList<>(pendingTemperatureFilters);
        if (!temperatureMode.usesTextBox()) {
            entries.add(temperatureMode.token());
        }
        return entries;
    }

    /**
     * The entries that actually decide a match, and the only ones the count and the tooltip show. A state
     * token makes the numeric expressions inert - the mixin short-circuits on the token, see
     * {@code AttributeFilterItemStackMixin} - so in workable/weldable this is exactly the token and nothing
     * else; with no token it is the numeric expressions, which is the original behaviour. Switching back to
     * the unconditional state brings the numbers back into this list on its own.
     *
     * <p>This is the <b>displayed</b> view, deliberately not the stored list. Keeping the numbers in
     * {@link #buildTemperatureFilterEntries()} is what makes a typed expression survive a detour through
     * workable/weldable and a screen reopen; but a number that a state token has made inert is a memory, not
     * a condition, so it must not be counted or listed next to the condition that is in force.
     */
    private List<String> buildActiveTemperatureFilterEntries() {
        if (!temperatureMode.usesTextBox()) {
            return List.of(temperatureMode.token());
        }
        return new ArrayList<>(pendingTemperatureFilters);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (temperatureMode.usesTextBox() && this.temperatureInput.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                String text = this.temperatureInput.getValue().trim();
                if (!text.isEmpty()) {
                    if (stageTemperatureFilter(text)) {
                        this.temperatureInput.setValue("");
                    }
                }
                return true;
            }
            if (this.temperatureInput.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (temperatureMode.usesTextBox() && this.temperatureInput.isFocused()) {
            if (this.temperatureInput.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.temperatureInput.isFocused() && !this.temperatureInput.isMouseOver(mouseX, mouseY)) {
            this.temperatureInput.setFocused(false);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean stageTemperatureFilter(String input) {
        Matcher matcher = TEMPERATURE_PATTERN.matcher(input);
        if (!matcher.matches()) {
            return false;
        }

        String filterValue = matcher.group(1) + matcher.group(2);

        // Check for duplicates - silent handling
        if (pendingTemperatureFilters.contains(filterValue)) {
            return false;
        }

        pendingTemperatureFilters.add(filterValue);
        return true;
    }

    /**
     * Writes the staged state back into the held filter's NBT. Only ever called when the screen is going away
     * ({@link #removed()}) - see {@link #cycleTemperatureMode()} for why calling it mid-session is not an
     * option: the resulting NBT change reaches the client and Create's filter screen closes itself over it.
     */
    private void commitTemperatureFilters() {
        TemperatureFilterSyncPacket.fromFilterList(buildTemperatureFilterEntries()).sendToServer();
    }

    @Override
    public void removed() {
        // The only place this is allowed to run: the screen is already on its way out, so the NBT change this
        // sends cannot close anything that is still open.
        commitTemperatureFilters();
        super.removed();
    }

    /**
     * Include temperature filters in the total count displayed on the filter slot.
     */
    @Override
    public void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        ItemStack stack = this.menu.ghostInventory.getStackInSlot(1);
        int attributeCount = ((AttributeFilterMenuAccessor) this.menu).wildfires$getSelectedAttributes().size();
        int totalCount = attributeCount + buildActiveTemperatureFilterEntries().size();

        PoseStack matrixStack = graphics.pose();
        matrixStack.pushPose();
        matrixStack.translate(0.0F, 0.0F, 150.0F);
        graphics.renderItemDecorations(this.font, stack, this.leftPos + 16, this.topPos + 62, String.valueOf(totalCount));
        matrixStack.popPose();


        this.renderTooltip(graphics, mouseX, mouseY);

        for(Renderable widget : this.renderables) {
            if (widget instanceof AbstractSimiWidget simiWidget) {
                if (simiWidget.isMouseOver(mouseX, mouseY)) {
                    List<Component> tooltip = simiWidget.getToolTip();
                    if (!tooltip.isEmpty()) {
                        int ttx = simiWidget.lockedTooltipX == -1 ? mouseX : simiWidget.lockedTooltipX + simiWidget.getX();
                        int tty = simiWidget.lockedTooltipY == -1 ? mouseY : simiWidget.lockedTooltipY + simiWidget.getY();
                        graphics.renderComponentTooltip(this.font, tooltip, ttx, tty);
                    }
                }
            }
        }
    }

    /**
     * Everything the temperature row shows is drawn here, <b>after</b> {@code super} has painted the panel:
     * the state's name (which takes the text box's place while workable/weldable is showing) and the state's
     * icon.
     *
     * <p><b>The order is the whole point, and it used to be the other way round.</b>
     * {@code AbstractFilterScreen.renderBg} ends with {@code background.render(graphics, leftPos, topPos)} -
     * one blit of the whole {@code ATTRIBUTE_FILTER} panel, 241x85 at this screen's own corner - and every
     * pixel of the row's two rectangles is plain opaque panel colour in the texture that blit reads
     * ({@code wildfires:textures/gui/filters.png}, the location {@code AllGuiTexturesMixin} swaps in): the
     * icon's own 7x16 box at 77,62 is (198,198,198) with alpha 255 on all 112 of its pixels, and the whole
     * 21x16 sheet area and the 22x20 click target are that same flat colour. The text box's slot at 42..66
     * is opaque too. Calling {@code super} last therefore painted the panel straight over this row,
     * which is why neither the icon nor the state's name could be seen. The row is drawn after the panel now,
     * and lifted by {@link #ROW_Z} as well, so it stays in front whichever way two draws at the same place
     * order themselves.
     *
     * <p><b>This was proven in game, not just reasoned.</b> For one build this method also painted the same
     * two flat rectangles as probes - magenta before the panel, cyan after it - and only the cyan one could
     * be seen (老大 2026-10-15 screenshot), even though both sit on the same opaque panel pixels. So what is
     * painted before {@code super} really is painted over: the probes are gone, the order stays.
     *
     * <p>All three states are blitted, including the unconditional one. The panel texture no longer contains
     * a thermometer - that artwork moved into the icon sheet's first frame - so relying on the background
     * would leave the unconditional state with no icon at all.
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTicks, mouseX, mouseY);

        // 这一行抬到面板之上一层（和 renderForeground 给物品数量抬 150 是同一招），面板再画也盖不住它。
        PoseStack row = graphics.pose();
        row.pushPose();
        row.translate(0.0F, 0.0F, ROW_Z);

        if (!temperatureMode.usesTextBox()) {
            // 老大 2026-10-15: 字要贴输入框上沿，不要居中偏低 3px；白字必须带投影，198 的底上纯白只差 57。
            Component name = Component.translatable(LANG_ROOT + temperatureMode.langSuffix());
            graphics.drawString(this.font, name,
                    this.leftPos + INPUT_OFFSET_X + INPUT_WIDTH / 2 - this.font.width(name) / 2,
                    this.topPos + INPUT_OFFSET_Y,
                    0xFFFFFF, true);
        }

        graphics.blit(TemperatureModeIcons.SHEET,
                this.leftPos + TemperatureModeIcons.ICON_OFFSET_X,
                this.topPos + TemperatureModeIcons.ICON_OFFSET_Y,
                temperatureMode.frameU(), temperatureMode.frameV(),
                TemperatureModeIcons.FRAME_WIDTH, TemperatureModeIcons.FRAME_HEIGHT,
                TemperatureModeIcons.SHEET_WIDTH, TemperatureModeIcons.SHEET_HEIGHT);

        row.popPose();
    }

    /**
     * When the clear/reset button is pressed, also drop the temperature row: expressions and state both.
     */
    @Override
    protected void contentsCleared() {
        super.contentsCleared();
        pendingTemperatureFilters.clear();
        temperatureMode = Mode.ANY;
        this.temperatureInput.setValue("");
        applyModeToWidgets();
    }

    /**
     * Renders one stored entry for the tooltip. A number goes through
     * {@code wildfires.attribute_filter.temperature.expression} so the wording is translatable; a state token
     * goes through its own key, and a token no language file knows is shown verbatim instead of vanishing.
     */
    private Component describeTemperatureFilter(String filter) {
        Matcher matcher = TEMPERATURE_PATTERN.matcher(filter);
        if (matcher.matches()) {
            String operatorKey = LANG_ROOT + "operator." + matcher.group(1);
            return Component.translatable(LANG_ROOT + "expression",
                    Component.translatable(operatorKey), matcher.group(2));
        }

        String tokenKey = LANG_ROOT + filter;
        String translated = Component.translatable(tokenKey).getString();
        return translated.equals(tokenKey) ? Component.literal(filter) : Component.translatable(tokenKey);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.menu.getCarried().isEmpty() && this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            if (this.hoveredSlot.index == 37) {
                List<Component> tooltipLines = new ArrayList<>();

                // Header
                boolean hasContent = !buildActiveTemperatureFilterEntries().isEmpty()
                        || !((AttributeFilterMenuAccessor) this.menu).wildfires$getSelectedAttributes().isEmpty();
                if (hasContent) {
                    tooltipLines.add(CreateLang.translateDirect("gui.attribute_filter.selected_attributes")
                                             .withStyle(ChatFormatting.YELLOW));
                }

                // Only the conditions in force: a state token makes the numeric expressions inert, so they
                // are stored (as memory) but not listed.
                for (String filter : buildActiveTemperatureFilterEntries()) {
                    tooltipLines.add(Component.literal("- ")
                                             .append(describeTemperatureFilter(filter))
                                             .withStyle(ChatFormatting.GRAY));
                }

                // Original selected attributes
                List<Pair<ItemAttribute, Boolean>> selectedAttrs = ((AttributeFilterMenuAccessor) this.menu).wildfires$getSelectedAttributes();
                for (Pair<ItemAttribute, Boolean> attr : selectedAttrs) {
                    tooltipLines.add(Component.literal("- ")
                                             .append(attr.getFirst()
                                                             .format(attr.getSecond()))
                                             .withStyle(ChatFormatting.GRAY));
                }

                if (!tooltipLines.isEmpty()) {
                    graphics.renderComponentTooltip(this.font, tooltipLines, mouseX, mouseY);
                    return;
                }
            }

            graphics.renderTooltip(this.font, this.hoveredSlot.getItem(), mouseX, mouseY);
            return;
        }

        super.renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * The click target over the temperature row's icon. It paints nothing of its own: the frame is drawn by
     * {@link #renderBg} so that it lands under the widgets, and the base class draws nothing either
     * ({@code AbstractSimiWidget.doRender} is an empty {@code return}). Its only job is to be clickable.
     *
     * <p>Its rectangle is the icon's own - {@code HITBOX_WIDTH} / {@code HITBOX_HEIGHT} are the frame's
     * 7x16 and {@code HITBOX_OFFSET_X} / {@code HITBOX_OFFSET_Y} are its corner - so the drawn icon is
     * exactly what the player clicks. A target that starts elsewhere is invisible: with the target 6px to
     * the right, the drawn icon is no longer the thing the player clicks. At 77..84 the target is clear of
     * the text box (42..66) on the left and of Create's first moved button (122) on the right. 老大
     * 2026-10-15 shrank it to this: the old 22x20 at y-2 was a button much bigger than the thing it stood
     * for.
     *
     * <p>老大 2026-10-15: no tooltip at all. Hovering used to pop the state's name in a box over the icon it
     * named, which hid the very thing that answers the question. The name belongs to the row itself (drawn
     * white in {@link #renderBg} while workable/weldable is on), so the widget no longer sets
     * {@code toolTip}, and the {@code lockedTooltipX/Y} offsets that pinned that box above the button are
     * gone with it.
     */
    private class ModeIconButton extends AbstractSimiWidget {

        private Mode mode;

        ModeIconButton(int x, int y) {
            super(x, y, TemperatureModeIcons.HITBOX_WIDTH, TemperatureModeIcons.HITBOX_HEIGHT);
            this.mode = Mode.ANY;
        }

        void setMode(Mode mode) {
            this.mode = mode;
        }
    }
}
