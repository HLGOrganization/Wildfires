package first.wildfires.client.screen;

import net.minecraft.resources.ResourceLocation;

/**
 * Everything about the temperature row's state icon in one place: the sheet it is painted on, the state
 * order the icon cycles through, and the sub-rectangle for each state.
 *
 * <p>The sheet is 21x16 - three 7x16 frames side by side, in the same order as {@link Mode}:
 * <pre>
 *     +-------+-------+-------+
 *     |  any  | work  | weld  |      u = 0..6    7..13   14..20
 *     +-------+-------+-------+      v = 0..15
 * </pre>
 * All three frames are fully opaque on a #C6C6C6 base, so each one paints over the row completely. That
 * matters because the panel texture no longer carries a thermometer of its own: the state that used to be
 * drawn into the background is frame 0 now, so {@link Mode#ANY} has to be blitted like the other two.
 *
 * <p>Keeping the geometry here means the artwork can be redrawn without touching rendering code: only
 * {@link #SHEET}, {@link #FRAME_WIDTH} / {@link #FRAME_HEIGHT} and each state's frame column change.
 */
public final class TemperatureModeIcons {

    /** The icon sheet: three 7x16 frames, left to right. */
    public static final ResourceLocation SHEET =
            ResourceLocation.fromNamespaceAndPath("wildfires", "textures/gui/temp_mode_switch.png");

    /** The whole sheet, needed by {@code blit} so the frames are not stretched over a 256x256 atlas. */
    public static final int SHEET_WIDTH = 21;
    public static final int SHEET_HEIGHT = 16;

    /** One frame: 7 wide, 16 tall. */
    public static final int FRAME_WIDTH = 7;
    public static final int FRAME_HEIGHT = 16;

    /**
     * Where the icon sits, relative to the panel's top-left.
     *
     * <p>x=77 is what keeps the tube aligned with the row it replaces: the old thermometer's tube walls were
     * at sheet x=78 and x=82, and this sheet's walls are at frame x=1 and x=5, so 77+1=78 and 77+5=82.
     */
    public static final int ICON_OFFSET_X = 77;
    public static final int ICON_OFFSET_Y = 62;

    /**
     * The click target, which is now the icon's own rectangle: {@link #FRAME_WIDTH} x {@link #FRAME_HEIGHT}
     * at the icon's corner, so the button and the pixels it stands for are one and the same thing.
     *
     * <p>老大 2026-10-15: it used to be 22x20 at y-2 - a target larger than the frame on every side - which
     * made the whole right-hand end of the row feel like one big button. Size now follows the frame constants
     * instead of repeating 7 and 16, so a redrawn sheet cannot desynchronise them.
     *
     * <p>The widget's position <em>is</em> its click target - {@code AbstractSimiWidget} (catnip, bundled in
     * {@code Ponder-Forge}) draws nothing at all, its {@code doRender} being an empty {@code return}, and the
     * frame is blitted by the screen instead - so the two must agree or the player aims at something he
     * cannot click. The left edge has always been the icon's own ({@link #HITBOX_OFFSET_X} is
     * {@link #ICON_OFFSET_X}); it was 83 for a version, which put the icon's visible 77..84 almost entirely
     * outside its own button: only x=83 was both drawn and clickable.
     *
     * <p>Nothing overlaps on either side, measured against the panel origin:
     * <ul>
     *   <li>Left: the text box is {@code INPUT_OFFSET_X}=42, {@code INPUT_WIDTH}=24, so its clickable
     *       rectangle is 42..66 - and {@code EditBox.isMouseOver} in 1.20.1 is that rectangle exactly, with
     *       no margin for a borderless box. A target starting at 77 clears it by 11px.</li>
     *   <li>Right: this target ends at 77+7=84, and the first of Create's three whitelist buttons is moved
     *       to 122 by {@code EXISTING_BUTTONS_SHIFT}=84 - a 38px gap, so a click meant for this button cannot
     *       land on Create's.</li>
     *   <li>Over the icon: 77..84 is exactly the 7px frame that is drawn there, so every clickable pixel is a
     *       drawn pixel and nothing outside the frame is clickable.</li>
     * </ul>
     */
    public static final int HITBOX_WIDTH = FRAME_WIDTH;
    public static final int HITBOX_HEIGHT = FRAME_HEIGHT;
    public static final int HITBOX_OFFSET_Y = 0;
    public static final int HITBOX_OFFSET_X = ICON_OFFSET_X;

    private TemperatureModeIcons() {
    }

    /**
     * The three states, in the order the icon cycles through them and in the order their frames appear on
     * the sheet.
     *
     * <p>{@code ANY} is first so that a filter with nothing staged shows the unconditional state, which is
     * also what an old save with only hand-written expressions gets.
     */
    public enum Mode {

        /** Unconditional: the text box is visible and takes numeric expressions. Sheet frame 0. */
        ANY("any", "any", 0),

        /** Workable (TFC forging temperature reached): the text box shows this state's name. Frame 1. */
        WORKABLE("workable", "workable", 1),

        /** Weldable (TFC welding temperature reached): the text box shows this state's name. Frame 2. */
        WELDABLE("weldable", "weldable", 2);

        private final String token;
        private final String langSuffix;
        private final int frameColumn;

        Mode(String token, String langSuffix, int frameColumn) {
            this.token = token;
            this.langSuffix = langSuffix;
            this.frameColumn = frameColumn;
        }

        /** The string stored in the {@code TemperatureFilters} NBT list. Never a new key. */
        public String token() {
            return token;
        }

        /** Suffix of {@code wildfires.attribute_filter.temperature.<suffix>}. */
        public String langSuffix() {
            return langSuffix;
        }

        /** True while {@code ANY}: hand-written numeric expressions are allowed and the text box shows. */
        public boolean usesTextBox() {
            return this == ANY;
        }

        /** u of this state's frame inside {@link #SHEET}. */
        public int frameU() {
            return frameColumn * FRAME_WIDTH;
        }

        /** v of this state's frame inside {@link #SHEET}; every frame shares the top edge. */
        public int frameV() {
            return 0;
        }

        /** The next state in the cycle: any -> workable -> weldable -> any. */
        public Mode next() {
            Mode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        /**
         * @return the state for a stored token, or {@code ANY} when the token is not one this version knows.
         *         Unknown tokens must not break a save, and an old save holds no preset token at all.
         */
        public static Mode byToken(String token) {
            for (Mode mode : values()) {
                if (mode.token.equals(token)) {
                    return mode;
                }
            }
            return ANY;
        }
    }
}
