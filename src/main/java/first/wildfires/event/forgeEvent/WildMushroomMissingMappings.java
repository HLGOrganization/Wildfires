package first.wildfires.event.forgeEvent;

import first.wildfires.Wildfires;
import first.wildfires.register.BlockRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.MissingMappingsEvent;

/** Keeps existing worlds valid after the KubeJS mushroom blocks moved to Wildfires. */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WildMushroomMissingMappings {
    private WildMushroomMissingMappings() {
    }

    @SubscribeEvent
    public static void remapBlocks(MissingMappingsEvent event) {
        event.getAllMappings(Registries.BLOCK).forEach(mapping -> {
            ResourceLocation key = mapping.getKey();
            if (!"kubejs".equals(key.getNamespace())) {
                return;
            }

            switch (key.getPath()) {
                case "wild_brown_mushroom" -> mapping.remap(BlockRegister.WildBrownMushroom.get());
                case "wild_red_mushroom" -> mapping.remap(BlockRegister.WildRedMushroom.get());
                case "wild_fluorescyst_shroom" -> mapping.remap(BlockRegister.WildFluorescystShroom.get());
                default -> {
                }
            }
        });
    }
}
