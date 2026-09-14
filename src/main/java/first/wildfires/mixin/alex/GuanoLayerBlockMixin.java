package first.wildfires.mixin.alex;

import com.github.alexmodguy.alexscaves.server.block.GuanoLayerBlock;
import net.dries007.tfc.common.blocks.ISlowEntities;
import net.dries007.tfc.config.TFCConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuanoLayerBlock.class)
public class GuanoLayerBlockMixin implements ISlowEntities {

    @Inject(
            method = "getCollisionShape",
            at = @At("RETURN"),
            cancellable = true
    )
    private void getCollisionShape(BlockState state, BlockGetter level, BlockPos blockPos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        cir.setReturnValue(Shapes.empty());
    }

    /**
     * Use the same movement modifier as TFC leaves. TFC applies this through
     * Helpers.slowEntityInsideBlocks(), so the empty collision shape remains
     * compatible with walking through the pile.
     */
    @Override
    public float slowEntityFactor(BlockState state) {
        return TFCConfig.SERVER.leavesMovementModifier.get().floatValue();
    }

}
