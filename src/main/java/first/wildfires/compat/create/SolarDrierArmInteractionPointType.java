package first.wildfires.compat.create;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Makes Firmalife's solar drier discoverable by Create mechanical arms. */
public final class SolarDrierArmInteractionPointType extends ArmInteractionPointType {

    private static final ResourceLocation SOLAR_DRIER_ID =
            ResourceLocation.fromNamespaceAndPath("firmalife", "solar_drier");

    @Override
    public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(SOLAR_DRIER_ID);
    }

    @Override
    public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
        return new ArmInteractionPoint(this, level, pos, state);
    }
}
