package first.wildfires.jei;

import first.wildfires.Wildfires;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/** Re-parses the food trait item list whenever its client config loads or reloads. */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FoodTraitDisplayConfigEvents {

    private FoodTraitDisplayConfigEvents() {
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) {
        if (FoodTraitDisplayConfig.isSpec(event.getConfig())) {
            FoodTraitDisplayConfig.refresh();
        }
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        if (FoodTraitDisplayConfig.isSpec(event.getConfig())) {
            FoodTraitDisplayConfig.refresh();
        }
    }
}