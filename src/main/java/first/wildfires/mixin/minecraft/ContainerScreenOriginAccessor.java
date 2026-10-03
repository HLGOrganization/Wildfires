package first.wildfires.mixin.minecraft;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads and adjusts a container screen's origin and its two labels.
 *
 * <p>These are all protected, and the backpack screen mixin targets a mod class by name, where
 * {@code @Shadow} declarations could not be remapped reliably. Targeting
 * {@code AbstractContainerScreen} instead - an ordinary Minecraft class - lets the annotation processor
 * emit the normal mappings, which the refmap confirms.
 *
 * <p>The label shown as "Inventory" is held in {@code playerInventoryTitle}. The font and the title text
 * itself live one class up, in {@code Screen}, so {@link ScreenTextAccessor} covers those.
 *
 * <p>The backpack screen redraws both labels, to give the backpack's own name the same colour the
 * inventory overlay uses for it.
 */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenOriginAccessor {

    @Accessor("leftPos")
    int wildfires$getLeftPos();

    @Accessor("topPos")
    int wildfires$getTopPos();

    /** The second label, shown as "Inventory". */
    @Accessor("playerInventoryTitle")
    Component wildfires$getPlayerInventoryTitle();

    @Accessor("titleLabelX")
    int wildfires$getTitleLabelX();

    /** The title's y, set by the screen's constructor and read back to shift it. */
    @Accessor("titleLabelY")
    int wildfires$getTitleLabelY();

    @Mutable
    @Accessor("titleLabelY")
    void wildfires$setTitleLabelY(int y);

    @Accessor("inventoryLabelX")
    int wildfires$getInventoryLabelX();

    /** The "Inventory" label's y, handled the same way as the title's. */
    @Accessor("inventoryLabelY")
    int wildfires$getInventoryLabelY();

    @Mutable
    @Accessor("inventoryLabelY")
    void wildfires$setInventoryLabelY(int y);
}
