package first.wildfires.mixin.tfc;

import first.wildfires.block.UnrestrictedCharcoalForgeBlock;
import net.dries007.tfc.common.blockentities.CharcoalForgeBlockEntity;
import net.dries007.tfc.common.blocks.devices.CharcoalForgeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a true eighth heat tier to every TFC charcoal forge at 2300 degrees. */
@Mixin(value = CharcoalForgeBlockEntity.class, remap = false)
public abstract class CharcoalForgeBlockEntityMixin {

    private static final int WILDFIRES_MAX_TFC_HEAT_LEVEL = 7;
    private static final int WILDFIRES_OVERHEATED_HEAT_LEVEL = 8;
    private static final float WILDFIRES_OVERHEATED_TEMPERATURE = 2300.0F;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void wildfires$enableAllSidedAutomation(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!(state.getBlock() instanceof UnrestrictedCharcoalForgeBlock)) {
            return;
        }

        InventoryBlockEntityAccessor<ItemStackHandler> accessor =
                (InventoryBlockEntityAccessor<ItemStackHandler>) (Object) this;
        accessor.getSidedInventory().on(accessor.getInventory(), direction -> true);
    }

    @ModifyVariable(
            method = "serverTick",
            at = @At("HEAD"),
            argsOnly = true,
            index = 2
    )
    private static BlockState wildfires$presentEighthTierAsTfcMaximum(BlockState state) {
        if (state.hasProperty(CharcoalForgeBlock.HEAT)
                && state.getValue(CharcoalForgeBlock.HEAT) == WILDFIRES_OVERHEATED_HEAT_LEVEL) {
            return state.setValue(CharcoalForgeBlock.HEAT, WILDFIRES_MAX_TFC_HEAT_LEVEL);
        }
        return state;
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private static void wildfires$applyEighthTier(Level level, BlockPos pos, BlockState state,
                                                  CharcoalForgeBlockEntity blockEntity, CallbackInfo ci) {
        BlockState currentState = level.getBlockState(pos);
        if (!(currentState.getBlock() instanceof CharcoalForgeBlock)
                || !currentState.hasProperty(CharcoalForgeBlock.HEAT)) {
            return;
        }

        int currentHeatLevel = currentState.getValue(CharcoalForgeBlock.HEAT);
        int targetHeatLevel = currentHeatLevel > 0
                && blockEntity.getTemperature() >= WILDFIRES_OVERHEATED_TEMPERATURE
                ? WILDFIRES_OVERHEATED_HEAT_LEVEL
                : Math.min(currentHeatLevel, WILDFIRES_MAX_TFC_HEAT_LEVEL);
        if (currentHeatLevel != targetHeatLevel) {
            level.setBlockAndUpdate(pos, currentState.setValue(CharcoalForgeBlock.HEAT, targetHeatLevel));
        }
    }
}
