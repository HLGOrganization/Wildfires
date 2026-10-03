package first.wildfires.mixin.minecraft;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the backpack menu move its slots onto a new grid.
 *
 * <p>{@code Slot.x} and {@code Slot.y} are final, but the backpack screen has to reposition the slots
 * when the panel geometry changes. Targeting vanilla's {@code Slot} rather than the mod's own slot
 * class means the annotation processor can emit a refmap for the fields, which is what makes this work
 * in the obfuscated runtime as well as a development workspace.
 */
@Mixin(Slot.class)
public interface SlotPositionAccessor {

    @Mutable
    @Accessor("x")
    void wildfires$setX(int x);

    @Mutable
    @Accessor("y")
    void wildfires$setY(int y);
}
