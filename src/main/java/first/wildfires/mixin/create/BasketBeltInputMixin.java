package first.wildfires.mixin.create;

import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import first.wildfires.compat.farmersdelight.BasketBeltInput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets Create find a {@link DirectBeltInputBehaviour} on Farmer's Delight's baskets.
 *
 * <p>Every target lookup funnels through this one static method - the belt's end insertion
 * ({@code BeltInventory}), the basin's spout side ({@code BasinBlock.canOutputTo}), brass tunnels,
 * ejectors and chutes all ask for the behaviour at a position this way - and it returns nothing unless
 * the block entity there is a Create {@code SmartBlockEntity}. A basket never is, so
 * {@link BasketBeltInput} is handed back in its place.
 *
 * <p>Returning the stand-in early only happens when the requested type is the belt input behaviour, so
 * the one-in-a-hundred other queries passing through here are untouched. The handler is static because
 * the target is.
 */
@Mixin(targets = "com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour", remap = false)
public abstract class BasketBeltInputMixin {

    @Inject(
            method = "get(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lcom/simibubi/create/foundation/blockEntity/behaviour/BehaviourType;)Lcom/simibubi/create/foundation/blockEntity/behaviour/BlockEntityBehaviour;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void wildfires$basketBeltInput(BlockGetter reader, BlockPos pos, BehaviourType<?> type,
                                                  CallbackInfoReturnable<BlockEntityBehaviour> cir) {
        if (type != DirectBeltInputBehaviour.TYPE) {
            return;
        }
        BasketBeltInput behaviour = BasketBeltInput.of(reader, pos);
        if (behaviour != null) {
            cir.setReturnValue(behaviour);
        }
    }
}
