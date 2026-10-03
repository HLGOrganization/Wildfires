package first.wildfires.mixin.minecraft;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the text and font a screen draws its title with.
 *
 * <p>Both live on {@code Screen} rather than on the container screens that inherited them, which is why
 * {@link ContainerScreenOriginAccessor} - which targets {@code AbstractContainerScreen} - cannot see
 * them. Accessors have to name the class that actually declares the field.
 *
 * <p>The backpack screen redraws its title in the inventory overlay's colour, and needs both the text
 * to draw and the font to draw it with.
 */
@Mixin(Screen.class)
public interface ScreenTextAccessor {

    @Accessor("font")
    Font wildfires$getFont();

    /** The screen's title - for a backpack, the item's own name. */
    @Accessor("title")
    Component wildfires$getTitle();
}
