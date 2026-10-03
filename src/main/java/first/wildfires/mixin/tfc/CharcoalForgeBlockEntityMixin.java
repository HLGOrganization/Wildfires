package first.wildfires.mixin.tfc;

import first.wildfires.block.UnrestrictedCharcoalForgeBlock;
import net.dries007.tfc.common.blockentities.CharcoalForgeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the earthen oven's inventory reachable from every side.
 *
 * <p>The eighth heat tier of the charcoal forge is no longer implemented here. It used to be a
 * {@code @ModifyVariable} that masked tier 8 down to TFC's maximum of 7 plus a {@code @Inject} at
 * return that promoted the forge back to tier 8 at 2300 degrees. That work now lives natively in
 * the rebuilt Wooden Cog: Wildfire mod, because the {@code heat_level} property can only have one
 * owner - two mods extending the same property install two redirects on the same
 * {@code IntegerProperty.create} call site, and two writers of the same block state make the model
 * flicker. See {@code net.chauvedev.woodencog.mixin.heat.MixinCharcoalForgeBlockEntity} and
 * {@code net.chauvedev.woodencog.mixin.heat.MixinTFCBlockStateProperties}.
 *
 * <p>Consequence: the {@code heat_level=8} variants in
 * {@code assets/wildfires/blockstates/unrestricted_charcoal_forge.json} are resolved against the
 * property that Wooden Cog: Wildfire extends. Running Wildfires without that mod therefore leaves
 * the earthen oven without a usable block state definition. If Wildfires ever has to work
 * standalone again, re-enable {@code first.wildfires.mixin.tfc.TFCBlockStatePropertiesMixin} in
 * {@code wildfires.mixins.json} (the class is kept for exactly that reason) and move the eighth
 * tier handling back into this file.
 */
@Mixin(value = CharcoalForgeBlockEntity.class, remap = false)
public abstract class CharcoalForgeBlockEntityMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void wildfires$enableAllSidedAutomation(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!(state.getBlock() instanceof UnrestrictedCharcoalForgeBlock)) {
            return;
        }

        InventoryBlockEntityAccessor<ItemStackHandler> accessor =
                (InventoryBlockEntityAccessor<ItemStackHandler>) (Object) this;
        accessor.getSidedInventory().on(accessor.getInventory(), direction -> true);
    }
}
