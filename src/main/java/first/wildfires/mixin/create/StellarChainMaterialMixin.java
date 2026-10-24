package first.wildfires.mixin.create;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import first.wildfires.compat.create.ChainConveyorWrenchHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 优化器固定使用原版贴图；自定义材质交回 Create/木齿轮渲染。 */
@Pseudo
@Mixin(targets = "org.stellarvan.stellarcreateoptimization.client.chainconveyor.ChainConveyorOptimizedRenderer", remap = false)
public abstract class StellarChainMaterialMixin
{
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void wildfires$preserveChainMaterial(ChainConveyorBlockEntity be, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay, CallbackInfoReturnable<Boolean> cir)
    {
        for (BlockPos relative : be.connections)
        {
            ItemStack chain = ChainConveyorWrenchHelper.getRecordedChain(be, relative);
            if (!chain.isEmpty() && !chain.is(Items.CHAIN))
            {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
