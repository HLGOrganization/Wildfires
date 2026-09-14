package first.wildfires.mixin.create;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import first.wildfires.compat.create.WoodenCogBurnerTemperatures;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives a Create Diesel Generators burner the same TFC heat behavior as Wooden Cog burners. */
@Pseudo
@Mixin(targets = "com.jesz.createdieselgenerators.content.burner.BurnerBlockEntity", remap = false)
public abstract class DieselGeneratorBurnerTemperatureMixin {

    @Inject(method = "tick()V", at = @At("TAIL"), remap = false)
    private void wildfires$provideTfcHeat(CallbackInfo callback) {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        Level level = blockEntity.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }

        BlockState state = blockEntity.getBlockState();
        BlazeBurnerBlock.HeatLevel heatLevel = BlazeBurnerBlock.getHeatLevelOf(state);
        float targetTemperature = WoodenCogBurnerTemperatures.targetForHeatLevel(heatLevel);
        float temperature = WoodenCogBurnerTemperatures.graduallyApproach(blockEntity, targetTemperature);
        if (temperature > 0.0F) {
            BlockPos receiver = blockEntity.getBlockPos().above();
            HeatCapability.provideHeatTo(level, receiver, temperature);
        }
    }
}
