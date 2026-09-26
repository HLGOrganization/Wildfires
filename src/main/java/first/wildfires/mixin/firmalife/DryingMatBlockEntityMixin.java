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
     * inside the one-slot drier. Only a stack that no longer matches a drying
     * recipe may be extracted, so adjacent inventories receive the finished
     * product and never the raw input - including during the window between the
     * timer expiring and the conversion actually running.
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
            if (stored.isEmpty() || drier.getLevel() == null) {
                return ItemStack.EMPTY;
            }
            // Automation may only take a stack that is not a drying input, which is exactly the
            // finished product.
            //
            // The conversion gap is why this tests the recipe rather than the timer. serverTick()
            // calls finish() only every twentieth tick, while getTicksLeft() is computed on the
            // fly and reaches zero the instant the duration elapses. For up to a second the raw
            // input therefore sits in the slot, unchanged and no longer counting down, and the old
            // "isItemValid && ticksLeft > 0" test returned false for it - so a hopper, which polls
            // every eight ticks, pulled the unfinished item out and cancelled the conversion.
            // A recipe match is true across that whole window and false once finish() has replaced
            // the stack with the result, so it is the condition that actually covers the gap.
            //
            // Holding every matching stack for its whole life would deadlock a recipe chain whose
            // output is another recipe's input, because finish() re-runs updateCache() and the new
            // stack would match again immediately. That cannot happen here: firmalife:drying is
            // the only type this block runs, and of its fourteen recipes the closest to a cycle is
            // drying_fruit, which maps fruit onto the same fruit carrying the firmalife:dried
            // trait. Its ingredient requires lacks_trait firmalife:dried, so the finished stack
            // stops matching the moment the trait is added and is released normally. Every other
            // recipe returns a different item than it consumes. Verified against the pack's data,
            // so no stack can stay matched forever.
            if (drier.isItemValid(slot, stored)) {
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
