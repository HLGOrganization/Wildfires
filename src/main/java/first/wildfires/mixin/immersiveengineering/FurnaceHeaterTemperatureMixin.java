package first.wildfires.mixin.immersiveengineering;

import first.wildfires.compat.create.WoodenCogBurnerTemperatures;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Gives an Immersive Engineering furnace heater a gradual TFC heat output. */
@Pseudo
@Mixin(targets = "blusunrize.immersiveengineering.common.blocks.metal.FurnaceHeaterBlockEntity", remap = false)
public abstract class FurnaceHeaterTemperatureMixin {

    private static final float WILDFIRES_MAX_TEMPERATURE = 900.0F;
    private static final int WILDFIRES_ENERGY_PER_TICK = 8;

    @Inject(method = "tickServer", at = @At("TAIL"), remap = false)
    private void wildfires$provideTfcHeat(CallbackInfo callback) {
        BlockEntity blockEntity = (BlockEntity) (Object) this;
        Level level = blockEntity.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }

        BlockPos position = blockEntity.getBlockPos();
        boolean hasTfcTarget = false;
        for (Direction direction : Direction.values()) {
            BlockEntity target = level.getBlockEntity(position.relative(direction));
            if (target != null && target.getCapability(HeatCapability.BLOCK_CAPABILITY).isPresent()) {
                hasTfcTarget = true;
                break;
            }
        }
        if (!hasTfcTarget || !wildfires$consumeEnergy(blockEntity, WILDFIRES_ENERGY_PER_TICK)) {
            WoodenCogBurnerTemperatures.graduallyApproach(blockEntity, 0.0F);
            return;
        }

        float temperature = WoodenCogBurnerTemperatures.graduallyApproach(
                blockEntity, WILDFIRES_MAX_TEMPERATURE);
        if (temperature <= 0.0F) {
            return;
        }

        for (Direction direction : Direction.values()) {
            HeatCapability.provideHeatTo(level, position.relative(direction), temperature);
        }
    }

    /**
     * IE keeps this field public, but the mod is optional at compile time. Reflection keeps this
     * mixin safe when an instance of the target class is not available in a development pack.
     */
    private static boolean wildfires$consumeEnergy(BlockEntity blockEntity, int amount) {
        try {
            Field field = blockEntity.getClass().getField("energyStorage");
            Object storage = field.get(blockEntity);
            Method getter = storage.getClass().getMethod("getEnergyStored");
            Object stored = getter.invoke(storage);
            if (!(stored instanceof Number number) || number.intValue() < amount) {
                return false;
            }
            Method extractor = storage.getClass().getMethod("extractEnergy", int.class, boolean.class);
            Object extracted = extractor.invoke(storage, amount, false);
            return extracted instanceof Number extractedNumber && extractedNumber.intValue() >= amount;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }
}
