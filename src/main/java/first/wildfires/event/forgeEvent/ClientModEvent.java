package first.wildfires.event.forgeEvent;

import first.wildfires.Wildfires;
import first.wildfires.client.renderer.entity.ReplacedBearRenderer;
import first.wildfires.client.spell.GalaxyHymnStarlinkParticle;
import first.wildfires.client.spell.GalaxyHymnBlackWorldShader;
import first.wildfires.client.spell.GalaxyHymnFieldRenderer;
import first.wildfires.client.spell.GalaxyHymnHomingStarRenderer;
import first.wildfires.client.spell.GalaxyHymnGpuStarParticle;
import first.wildfires.client.spell.GalaxyHymnNebulaShader;
import first.wildfires.client.spell.GalaxyHymnProjectileRenderer;
import first.wildfires.client.spell.GalaxyHymnSpaceWindowShader;
import first.wildfires.compats.irons_spellbooks.GalaxyHymnRegister;
import first.wildfires.register.ItemRegister;
import net.dries007.tfc.common.entities.TFCEntities;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only item model properties. Never loaded by a dedicated server. */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModEvent {

    private ClientModEvent() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                ItemRegister.SimpleCompass.get(),
                ResourceLocation.fromNamespaceAndPath("minecraft", "angle"),
                (ClampedItemPropertyFunction) ItemProperties.getProperty(Items.COMPASS,
                        ResourceLocation.fromNamespaceAndPath("minecraft", "angle"))
        ));
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TFCEntities.BLACK_BEAR.get(), ReplacedBearRenderer::BlackBear);
        event.registerEntityRenderer(TFCEntities.GRIZZLY_BEAR.get(), ReplacedBearRenderer::GrizzlyBear);
        event.registerEntityRenderer(TFCEntities.POLAR_BEAR.get(), ReplacedBearRenderer::PolarBear);
        event.registerEntityRenderer(GalaxyHymnRegister.GALAXY_HYMN_CORE.get(),
                GalaxyHymnProjectileRenderer::new);
        event.registerEntityRenderer(GalaxyHymnRegister.GALAXY_HYMN_FIELD.get(),
                GalaxyHymnFieldRenderer::new);
        event.registerEntityRenderer(GalaxyHymnRegister.GALAXY_HYMN_HOMING_STAR.get(),
                GalaxyHymnHomingStarRenderer::new);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(GalaxyHymnRegister.GALAXY_HYMN_STARLINK.get(),
                GalaxyHymnStarlinkParticle.Provider::new);
        event.registerSpriteSet(GalaxyHymnRegister.GALAXY_HYMN_IMPACT_STARLINK.get(),
                GalaxyHymnStarlinkParticle.ImpactProvider::new);
        event.registerSpriteSet(GalaxyHymnRegister.GALAXY_HYMN_SPARK.get(),
                GalaxyHymnStarlinkParticle.SparkProvider::new);
        event.registerSpriteSet(GalaxyHymnRegister.GALAXY_HYMN_GPU_STAR.get(),
                GalaxyHymnGpuStarParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws java.io.IOException {
        GalaxyHymnBlackWorldShader.register(event);
        GalaxyHymnSpaceWindowShader.register(event);
        GalaxyHymnNebulaShader.register(event);
    }

}
