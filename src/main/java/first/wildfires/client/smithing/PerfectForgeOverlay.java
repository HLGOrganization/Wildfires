package first.wildfires.client.smithing;

import first.wildfires.Wildfires;
import first.wildfires.network.PerfectForgeRequestPacket;
import net.dries007.tfc.common.blockentities.AnvilBlockEntity;
import net.dries007.tfc.common.capabilities.forge.Forging;
import net.dries007.tfc.common.capabilities.forge.ForgingCapability;
import net.dries007.tfc.common.container.AnvilContainer;
import net.dries007.tfc.common.recipes.AnvilRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The anvil's memory forge button: where it sits, how it looks, and what a click on it does.
 *
 * <p>Drawn and hit-tested by hand rather than registered as a screen widget. TFC's anvil screen builds
 * its own layout and offers no supported way for another mod to add to it, and the vanilla route -
 * {@code addRenderableWidget} - is protected, so it cannot be called on a screen from a mixin that merely
 * targets that screen's class.
 *
 * <p>The button sits in the empty square at the top right of TFC's panel, to the right of the top row of
 * step buttons. Those form a four by two grid of 16x16 buttons at x = 53, 71, 89, 107 and y = 50, 68. The
 * 20x20 area at (134, 39) is plain panel grey in the artwork, clear of every drawn element with a whole
 * pixel of margin on each of its four sides - so the position is measured against the panel artwork rather
 * than guessed.
 *
 * <h2>The artwork</h2>
 * One texture holds the button's two frames side by side: the same hammer, dim for a recipe whose forge this
 * player has not unlocked and bright for one they have. The face is a strike button rather than a book, so
 * what it shows is availability, and availability is the unlock - the click is refused until then, with the
 * bar that unlocks it ({@link first.wildfires.smithing.PerfectForgeService#tryPerfectForge}). The frame is
 * chosen from the state the client can see; see {@link PerfectForgeClientData}.
 *
 * <p>Whether a record exists at all is not on the face. A recipe can be unlocked by a forge whose blows were
 * not recorded in full, so an unlocked recipe with nothing remembered is possible, and the hover text is
 * where that is said rather than the picture.
 *
 * <p>The button is never drawn dimmed and a click always reaches the server, whichever frame is showing.
 * The frames repeat what the client has been told, which is only ever enough to choose a picture - whether
 * a recipe is unlocked, and whether a sequence is remembered for it - and never the sequence itself. What
 * the button can then do with them, namely whether the piece on the anvil stands where that sequence expects
 * to pick up, is the server's business alone, so a click is never withheld on the strength of a frame. The
 * server's answer is what the player needs, and its refusals are written for the player rather than
 * swallowed here.
 *
 * <h2>Reading the current recipe</h2>
 * The recipe comes from the input slot <em>as the client sees it</em> - that is, from the menu - rather
 * than from the block entity. Only the menu's slots are synced to a client, so the block entity's own
 * inventory is not a reliable place to look for the item being worked. The item's {@code Forging}
 * capability travels with the stack and names the recipe the player selected. The memory strip drawn above
 * the panel reads the same two things the same way ({@link AnvilMemoryStrip}).
 *
 * <p>Everything is recomputed on each call rather than cached, because a forge running while this screen is
 * open can unlock the recipe underneath the player.
 */
public final class PerfectForgeOverlay {

    /** Panel-relative position, measured against TFC's anvil artwork. */
    public static final int OFFSET_X = 134;
    public static final int OFFSET_Y = 39;
    public static final int SIZE = 20;

    /** The button's two frames, side by side: dim while the recipe is locked, bright once it is unlocked. */
    private static final ResourceLocation TEXTURE =
            Wildfires.rl("textures/gui/memory_forge_button.png");
    /** The face itself, 18x18, centred in the 20x20 slot so it keeps a pixel of margin on every side. */
    private static final int FACE = 18;
    private static final int FACE_INSET = (SIZE - FACE) / 2;
    private static final int TEXTURE_WIDTH = 2 * FACE;
    private static final int LOCKED_U = 0;
    private static final int UNLOCKED_U = FACE;

    /**
     * A light wash over the face on hover, so the button answers the pointer.
     *
     * <p>Inset past the rounded corners of the artwork, so it can only ever tint the button's own pixels
     * and cannot square off the corners or spill onto the panel behind it.
     */
    private static final int HOVER_WASH = 0x33FFFFFF;
    private static final int HOVER_INSET = 2;

    private PerfectForgeOverlay() {
    }

    public static void render(GuiGraphics graphics, AnvilContainer menu,
                              int guiLeft, int guiTop, int mouseX, int mouseY) {
        ResourceLocation recipe = currentRecipe(menu);
        boolean unlocked = recipe != null && PerfectForgeClientData.isUnlocked(recipe);
        // The face is the unlock, so a locked recipe shows the dim hammer whether or not there is a record
        // behind it: a record only changes the hover text, and the click still forges at the slower pace.
        boolean remembered = !unlocked && recipe != null && PerfectForgeClientData.isRemembered(recipe);

        int left = guiLeft + OFFSET_X;
        int top = guiTop + OFFSET_Y;

        graphics.blit(TEXTURE, left + FACE_INSET, top + FACE_INSET, unlocked ? UNLOCKED_U : LOCKED_U, 0,
                FACE, FACE, TEXTURE_WIDTH, FACE);

        if (isInside(guiLeft, guiTop, mouseX, mouseY)) {
            graphics.fill(left + FACE_INSET + HOVER_INSET, top + FACE_INSET + HOVER_INSET,
                    left + FACE_INSET + FACE - HOVER_INSET, top + FACE_INSET + FACE - HOVER_INSET, HOVER_WASH);
            graphics.renderComponentTooltip(Minecraft.getInstance().font,
                    hoverText(recipe, unlocked, remembered), mouseX, mouseY);
        }
    }

    /**
     * What the button says when the pointer rests on it.
     *
     * <p>One answer per state the button can be in: nothing selected on the anvil, a recipe whose forge is
     * unlocked - which is mastery, and is said so even when the record behind it is empty, because the
     * unlock is the thing the player earned - a recipe that has been forged by hand and remembers its blows
     * but is not unlocked yet, together with the bar towards that, and a recipe with no record at all.
     *
     * <p>The record is only mentioned as the second line of an unlocked recipe: a memory forge only works
     * from a record, so a mastered recipe without one says nothing about the click rather than promising a
     * run it cannot make. The bar is the one number a smith cannot read anywhere else without opening the
     * manual.
     */
    private static List<Component> hoverText(ResourceLocation recipe, boolean unlocked, boolean remembered) {
        if (recipe == null) {
            return List.of(Component.translatable("wildfires.smithing.forge.no_recipe"));
        }
        if (unlocked) {
            return remembered
                    ? List.of(Component.translatable("wildfires.smithing.button.mastered"),
                            Component.translatable("wildfires.smithing.button.hint"))
                    : List.of(Component.translatable("wildfires.smithing.button.mastered"));
        }
        if (remembered) {
            return List.of(Component.translatable("wildfires.smithing.button.remembered"),
                    Component.translatable("wildfires.smithing.button.locked_hint",
                            PerfectForgeClientData.xp(recipe), PerfectForgeClientData.xpToUnlock()));
        }
        return List.of(Component.translatable("wildfires.smithing.button.none"),
                Component.translatable("wildfires.smithing.memory.none"));
    }

    /**
     * Sends the request if the click landed on the button.
     *
     * <p>Always sent, whatever the button currently looks like and whether or not anything is selected:
     * what the button does depends on state only the server holds - which recipe is loaded, whether it is
     * unlocked, and which sequence this player last finished there - so it is the server's answer that is
     * worth having, and its refusals are written for the player rather than swallowed here.
     *
     * @return whether the click was consumed, so the screen does not also treat it as a slot click
     */
    public static boolean click(int guiLeft, int guiTop, double mouseX, double mouseY) {
        if (!isInside(guiLeft, guiTop, mouseX, mouseY)) {
            return false;
        }
        new PerfectForgeRequestPacket().sendToServer();
        return true;
    }

    private static boolean isInside(int guiLeft, int guiTop, double mouseX, double mouseY) {
        double left = guiLeft + OFFSET_X;
        double top = guiTop + OFFSET_Y;
        return mouseX >= left && mouseX < left + SIZE && mouseY >= top && mouseY < top + SIZE;
    }

    /**
     * The forging carried by the anvil's input slot as the client sees it, or {@code null} if there is
     * none.
     *
     * <p>Read from the menu rather than the block entity: only the menu's slots are synced to a client, so
     * the block entity's own inventory is not a reliable place to look.
     */
    static Forging forging(AnvilContainer menu) {
        return ForgingCapability.get(menu.getSlot(AnvilBlockEntity.SLOT_INPUT_MAIN).getItem());
    }

    /** The recipe loaded on the anvil right now, or {@code null} if none is selected. */
    static ResourceLocation currentRecipe(AnvilContainer menu) {
        Forging forging = forging(menu);
        return forging == null ? null : currentRecipe(forging);
    }

    /** The recipe this piece of forging names, or {@code null} if it names none. */
    static ResourceLocation currentRecipe(Forging forging) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        AnvilRecipe recipe = forging.getRecipe(level);
        return recipe == null ? null : recipe.getId();
    }
}
