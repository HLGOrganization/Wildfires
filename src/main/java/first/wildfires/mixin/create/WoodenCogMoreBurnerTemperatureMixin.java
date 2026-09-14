package first.wildfires.mixin.create;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import first.wildfires.compat.create.WoodenCogBurnerTemperatures;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replaces Wooden Cog's linear MoreBurners temperature formula with its heat-level values. */
@Pseudo
@Mixin(targets = "net.chauvedev.woodencog.compat.createmoreburners.CMBIntegrationImpl", remap = false)
public abstract class WoodenCogMoreBurnerTemperatureMixin {

    private static final ResourceLocation ELECTRIC_BURNER_ID =
            ResourceLocation.fromNamespaceAndPath("moreburners", "electric_burner");

    @Inject(method = "getTFCTemperatureOf", at = @At("HEAD"), cancellable = true, remap = false)
    private void wildfires$useHeatLevelTemperature(
            BlockEntity blockEntity,
            CallbackInfoReturnable<Float> callback
    ) {
        if (blockEntity == null) {
            // Ponder scenes can tick a basin before the heat source block
            // entity exists; Wooden Cog's own instanceof check handles that.
            return;
        }

        BlockState state = blockEntity.getBlockState();
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(ELECTRIC_BURNER_ID)) {
            return;
        }

        BlazeBurnerBlock.HeatLevel heatLevel = BlazeBurnerBlock.getHeatLevelOf(state);
        float targetTemperature = WoodenCogBurnerTemperatures.targetForHeatLevel(heatLevel);
        callback.setReturnValue(
                WoodenCogBurnerTemperatures.graduallyApproach(blockEntity, targetTemperature)
        );
    }
}
