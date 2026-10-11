package first.wildfires.mixin.tfc;

import first.wildfires.compat.tfc.Smoldering;
import net.dries007.tfc.common.blockentities.AbstractFirepitBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a campfire, pot or grill that has gone out but is still hot catch again from fuel put into it.
 *
 * <p>All three devices share one ticker, {@code AbstractFirepitBlockEntity::serverTick} - the registration
 * in TFC's block list points at the abstract class for every one of them - so a single injection at the end
 * of that tick covers the whole family. Nothing is exempted here: unlike a charcoal forge, a firepit has no
 * structure to keep up, and TFC lets one burn anywhere.
 *
 * <p>The work itself, including why the device is lit through {@code light(state)} rather than by writing
 * the block state, is in {@link Smoldering}.
 */
@Mixin(value = AbstractFirepitBlockEntity.class, remap = false)
public abstract class AbstractFirepitBlockEntityMixin {

    @Inject(method = "serverTick", at = @At("TAIL"))
    private static void wildfires$relightWhileHot(Level level, BlockPos pos, BlockState state,
                                                 AbstractFirepitBlockEntity<?> firepit, CallbackInfo ci) {
        Smoldering.firepit(level, state, firepit);
    }
}
