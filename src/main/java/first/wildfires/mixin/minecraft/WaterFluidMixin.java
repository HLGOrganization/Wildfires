package first.wildfires.mixin.minecraft;

import com.github.alexmodguy.alexscaves.server.block.GuanoLayerBlock;
import com.github.alexmodguy.alexscaves.server.item.ACItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Makes water carry the configured guano-layer block drop when it washes it away. */
@Mixin(FlowingFluid.class)
public abstract class WaterFluidMixin {

    @Inject(
            method = "spreadTo",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/material/FlowingFluid;beforeDestroyingBlock(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"
            ),
            cancellable = true
    )
    private void wildfires$dropGuanoLayer(LevelAccessor level, BlockPos pos, BlockState state,
                                          net.minecraft.core.Direction direction, FluidState fluidState,
                                          CallbackInfo ci) {
        if (state.getBlock() instanceof GuanoLayerBlock && level instanceof Level actualLevel
                && fluidState.getType().is(FluidTags.WATER)
                && !actualLevel.isClientSide()) {
            int layers = state.hasProperty(SnowLayerBlock.LAYERS)
                    ? state.getValue(SnowLayerBlock.LAYERS)
                    : 1;
            Block.popResource(actualLevel, pos, new ItemStack(ACItemRegistry.GUANO.get(), layers));
            level.setBlock(pos, fluidState.createLegacyBlock(), 3);
            ci.cancel();
        }
    }
}
