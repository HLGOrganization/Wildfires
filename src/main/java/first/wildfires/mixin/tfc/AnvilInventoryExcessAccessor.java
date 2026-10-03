package first.wildfires.mixin.tfc;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Reaches the anvil's overflow list.
 *
 * <p>An anvil can hold more than one item in its input slot, but a weld produces a single result, so when
 * the input stack survives the weld the result cannot go back into that slot. TFC's own weld pushes it
 * here instead, and the book-to-manual weld does the same so that welding one book out of a stack of them
 * behaves like every other weld.
 *
 * <p>The field is private, and it is a plain list rather than anything TFC exposes.
 */
@Pseudo
@Mixin(targets = "net.dries007.tfc.common.blockentities.AnvilBlockEntity$AnvilInventory", remap = false)
public interface AnvilInventoryExcessAccessor {

    @Accessor("excess")
    List<ItemStack> wildfires$getExcess();
}
