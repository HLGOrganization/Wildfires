package first.wildfires.mixin.tfc;

import first.wildfires.compat.tfc.Quenching;
import net.dries007.tfc.common.blockentities.BarrelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <h2>What this adds</h2>
 * A barrel holding water quenches the hot tool heads in its inventory, once per tick, exactly as a lake
 * quenches a dropped one.
 *
 * <h2>Why the barrel's own tick</h2>
 * A head put in a barrel is an item in a block entity's inventory: it is never an item entity in the world,
 * so the dropped-item path cannot see it and nothing about it is in water as far as the game is concerned.
 * The barrel's ticker is the one place those items exist while the barrel is loaded, and it already runs
 * every tick for a loaded barrel, so the quench rate matches the lake's.
 *
 * <p>The injection is at HEAD rather than TAIL because {@code serverTick} leaves early on several paths
 * (a barrel that is not formed, one with nothing to do); a TAIL would put the quench on the tail of a branch
 * that a working barrel rarely reaches.
 */
@Mixin(BarrelBlockEntity.class)
public abstract class BarrelBlockEntityMixin {

    @Inject(method = "serverTick", at = @At("HEAD"), remap = false, require = 1)
    private static void wildfires$quenchContentsInWater(Level level, BlockPos pos, BlockState state,
                                                       BarrelBlockEntity barrel, CallbackInfo ci) {
        Quenching.quenchBarrelContents(barrel);
    }
}
