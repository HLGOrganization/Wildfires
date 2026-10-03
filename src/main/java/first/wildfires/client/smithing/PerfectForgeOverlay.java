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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The anvil's one-click forge button: where it sits, how it looks, and what a click on it does.
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
 * One texture holds the button's three frames side by side. The first is a grey book, for a recipe with
 * nothing to offer: not unlocked, and nothing worked out on it by hand either. The second is the same book
 * in brown with a yellow star, which is the one-click perfect forge. The third is the book in brown with no
 * star: the memory forge, where the recipe itself is still locked but this player has finished it by hand
 * once, so the button can strike the blows they struck. The frame is chosen from the state the client can
 * see; see {@link PerfectForgeClientData}.
 *
 * <p>The button is never drawn dimmed and a click always reaches the server, whichever frame is showing.
 * The frames repeat what the client has been told, which is only ever enough to choose a picture - that a
 * recipe is unlocked, and that a sequence is remembered for it - and never the sequence itself. What the
 * button can then do with them, namely whether the piece on the anvil stands where that sequence expects
 * to pick up, is the server's business alone, so a click is never withheld on the strength of a frame. The
 * server's answer is what the player needs, and its refusals are written for the player rather than
 * swallowed here.
 *
 * <h2>Reading the current recipe</h2>
 * The recipe comes from the input slot <em>as the client sees it</em> - that is, from the menu - rather
 * than from the block entity. Only the menu's slots are synced to a client, so the block entity's own
 * inventory is not a reliable place to look for the item being worked. The item's {@code Forging}
 * capability travels with the stack and names the recipe the player selected.
 *
 * <p>Everything is recomputed on each call rather than cached, because a perfect forge while this screen
 * is open can unlock the recipe underneath the player.
 */
public final class PerfectForgeOverlay {

    /** Panel-relative position, measured against TFC's anvil artwork. */
    public static final int OFFSET_X = 134;
    public static final int OFFSET_Y = 39;
    public static final int SIZE = 20;

    /** The button's three frames, side by side: locked first, then the perfect forge, then the memory. */
    private static final ResourceLocation TEXTURE =
            Wildfires.rl("textures/gui/perfect_forge_button.png");
    private static final int TEXTURE_WIDTH = 3 * SIZE;
    private static final int LOCKED_U = 0;
    private static final int UNLOCKED_U = SIZE;
    private static final int MEMORY_U = 2 * SIZE;

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
        // The star is the perfect forge, and it is the better of the two the button can offer, so an
        // unlocked recipe never shows the plain book even while a remembered sequence is still there.
        boolean remembered = !unlocked && recipe != null && PerfectForgeClientData.isRemembered(recipe);

        int left = guiLeft + OFFSET_X;
        int top = guiTop + OFFSET_Y;

        graphics.blit(TEXTURE, left, top, unlocked ? UNLOCKED_U : remembered ? MEMORY_U : LOCKED_U, 0,
                SIZE, SIZE, TEXTURE_WIDTH, SIZE);

        if (isInside(guiLeft, guiTop, mouseX, mouseY)) {
            graphics.fill(left + HOVER_INSET, top + HOVER_INSET,
                    left + SIZE - HOVER_INSET, top + SIZE - HOVER_INSET, HOVER_WASH);
        }
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

    /** The recipe loaded on the anvil right now, or {@code null} if none is selected. */
    private static ResourceLocation currentRecipe(AnvilContainer menu) {
        Level level = Minecraft.getInstance().level;
        ItemStack input = menu.getSlot(AnvilBlockEntity.SLOT_INPUT_MAIN).getItem();
        Forging forging = ForgingCapability.get(input);
        if (level == null || forging == null) {
            return null;
        }
        AnvilRecipe recipe = forging.getRecipe(level);
        return recipe == null ? null : recipe.getId();
    }
}
