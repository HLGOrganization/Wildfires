package first.wildfires.soldering;

import first.wildfires.Wildfires;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Registers the AUI container screen once the menu type exists. */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SolderingClientSetup {

    private SolderingClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("apricityui")) {
            event.enqueueWork(SolderingAuiClient::registerScreen);
        }
    }
}
