package first.wildfires.compat.create;

import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import first.wildfires.Wildfires;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

/** Registers Wildfires' Create mechanical-arm interaction points. */
public final class CreateMechanicalArmCompat {

    private static final ResourceLocation SOLAR_DRIER_ID = Wildfires.rl("solar_drier");

    private CreateMechanicalArmCompat() {
    }

    public static void register() {
        Registry<ArmInteractionPointType> registry = CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE;
        if (registry.containsKey(SOLAR_DRIER_ID)) {
            return;
        }

        try {
            registerSolarDrier(registry);
        } catch (IllegalStateException alreadyFrozen) {
            // The registries are frozen during bootstrap in the dev environment,
            // which happens before mod construction. Reopen the registry for this
            // one entry and freeze it again, so Create rebuilds its sorted list.
            if (!(registry instanceof MappedRegistry<?> mapped)) {
                throw alreadyFrozen;
            }
            mapped.unfreeze();
            registerSolarDrier(registry);
            mapped.freeze();
        }
    }

    private static void registerSolarDrier(Registry<ArmInteractionPointType> registry) {
        Registry.register(registry, SOLAR_DRIER_ID, new SolarDrierArmInteractionPointType());
    }
}
