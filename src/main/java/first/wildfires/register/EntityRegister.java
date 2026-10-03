package first.wildfires.register;

import first.wildfires.Wildfires;
import first.wildfires.entity.DumbbellProjectile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class EntityRegister {

    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Wildfires.MODID);

    public static final RegistryObject<EntityType<DumbbellProjectile>> DUMBBELL = ENTITIES.register("dumbbell",
            () -> EntityType.Builder.<DumbbellProjectile>of(DumbbellProjectile::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(4)
                    .updateInterval(4)
                    .build(Wildfires.rl("dumbbell").toString()));

    private EntityRegister() {
    }

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }

}
