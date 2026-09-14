package first.wildfires.mixin.tfc;

import net.dries007.tfc.common.blockentities.InventoryBlockEntity;
import net.dries007.tfc.common.capabilities.SidedHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = InventoryBlockEntity.class, remap = false)
public interface InventoryBlockEntityAccessor<C extends IItemHandlerModifiable & INBTSerializable<CompoundTag>> {

    @Accessor
    C getInventory();

    @Accessor
    SidedHandler.Builder<IItemHandler> getSidedInventory();

}
