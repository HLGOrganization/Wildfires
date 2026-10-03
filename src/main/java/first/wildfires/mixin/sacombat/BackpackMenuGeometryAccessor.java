package first.wildfires.mixin.sacombat;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@code BackpackMenu}'s layout fields so {@link BackpackPanelMenuMixin} can rebuild the
 * geometry as a vertical arrangement.
 *
 * <p>Every geometry field is final in the original class, because the constructor was the only place
 * meant to set them. The layout has to change between the geometry being computed and the slots being
 * placed, which is still inside the constructor, so the fields must be writable.
 */
@Pseudo
@Mixin(targets = "com.ogaba.sa_survival.menu.BackpackMenu", remap = false)
public interface BackpackMenuGeometryAccessor {

    @Accessor(value = "backpackStack", remap = false)
    ItemStack wildfires$getBackpackStack();

    @Accessor(value = "backpackSlots", remap = false)
    int wildfires$getBackpackSlots();

    @Mutable
    @Accessor(value = "genericLayout", remap = false)
    void wildfires$setGenericLayout(boolean genericLayout);

    @Mutable
    @Accessor(value = "backpackColumns", remap = false)
    void wildfires$setBackpackColumns(int columns);

    @Mutable
    @Accessor(value = "backpackRows", remap = false)
    void wildfires$setBackpackRows(int rows);

    @Mutable
    @Accessor(value = "backpackPanelWidth", remap = false)
    void wildfires$setBackpackPanelWidth(int width);

    @Mutable
    @Accessor(value = "backpackPanelHeight", remap = false)
    void wildfires$setBackpackPanelHeight(int height);

    @Mutable
    @Accessor(value = "backpackPanelX", remap = false)
    void wildfires$setBackpackPanelX(int x);

    @Mutable
    @Accessor(value = "backpackPanelY", remap = false)
    void wildfires$setBackpackPanelY(int y);

    @Mutable
    @Accessor(value = "vanillaInventoryX", remap = false)
    void wildfires$setVanillaInventoryX(int x);

    @Mutable
    @Accessor(value = "vanillaInventoryY", remap = false)
    void wildfires$setVanillaInventoryY(int y);

    @Mutable
    @Accessor(value = "imageWidth", remap = false)
    void wildfires$setImageWidth(int width);

    @Mutable
    @Accessor(value = "imageHeight", remap = false)
    void wildfires$setImageHeight(int height);
}
