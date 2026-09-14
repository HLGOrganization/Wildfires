package first.wildfires.mixin.firmalife;

import com.eerussianguy.firmalife.common.blockentities.DryingMatBlockEntity;
import first.wildfires.mixin.tfc.InventoryBlockEntityAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps solar-drier progress when rain or snow starts. */
@Mixin(value = DryingMatBlockEntity.class, remap = false)
public abstract class DryingMatBlockEntityMixin {

    private static final ResourceLocation SOLAR_DRIER_ID =
            new ResourceLocation("firmalife", "solar_drier");

    @Inject(method = "<init>", at = @At("RETURN"))
    private void wildfires$enableSolarDrierAutomation(CallbackInfo ci) {
        DryingMatBlockEntity drier = (DryingMatBlockEntity) (Object) this;
        if (!BuiltInRegistries.BLOCK.getKey(drier.getBlockState().getBlock()).equals(SOLAR_DRIER_ID)) {
            return;
        }

        InventoryBlockEntityAccessor<ItemStackHandler> accessor =
                (InventoryBlockEntityAccessor<ItemStackHandler>) (Object) this;
        IItemHandler automationHandler = new SolarDrierItemHandler(drier, accessor.getInventory());
        accessor.getSidedInventory().on(automationHandler, direction -> true);
    }

    @Redirect(
            method = "serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lcom/eerussianguy/firmalife/common/blockentities/DryingMatBlockEntity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/eerussianguy/firmalife/common/blockentities/"
                            + "DryingMatBlockEntity;resetCounter()V",
                    ordinal = 0
            ),
            remap = false
    )
    private static void wildfires$skipSolarWeatherReset(DryingMatBlockEntity instance) {
        if (!BuiltInRegistries.BLOCK.getKey(instance.getBlockState().getBlock()).equals(SOLAR_DRIER_ID)) {
            instance.resetCounter();
        }
    }

    /**
     * Starts processing after automated insertion and keeps unfinished inputs
     * inside the one-slot drier. Once the stack no longer matches a drying
     * recipe, adjacent inventories may extract the finished product.
     */
    private record SolarDrierItemHandler(DryingMatBlockEntity drier, ItemStackHandler inventory)
            implements IItemHandler {

        @Override
        public int getSlots() {
            return inventory.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            ItemStack remainder = inventory.insertItem(slot, stack, simulate);
            if (!simulate && remainder.getCount() < stack.getCount()) {
                drier.start();
            }
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack stored = inventory.getStackInSlot(slot);
            if (stored.isEmpty() || drier.getLevel() == null
                    || drier.isItemValid(slot, stored) && drier.getTicksLeft() > 0L) {
                return ItemStack.EMPTY;
            }
            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return inventory.isItemValid(slot, stack);
        }
    }
}
