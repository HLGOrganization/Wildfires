package first.wildfires;

import com.simibubi.create.foundation.data.CreateRegistrate;
import first.wildfires.api.celestial.CelestialApi;
import first.wildfires.celestial.CelestialConfig;
import first.wildfires.celestial.CelestialBodies;
import first.wildfires.celestial.LegacyCelestialModGuard;
import first.wildfires.celestial.OverworldCelestialProvider;
import first.wildfires.compats.irons_spellbooks.GalaxyHymnRegister;
import first.wildfires.compat.create.CreateMechanicalArmCompat;
import first.wildfires.compat.sophisticateditemactions.SophisticatedItemActionsConfig;
import first.wildfires.jei.FoodTraitDisplayConfig;
import first.wildfires.diagnostics.StartupDiagnostics;
import first.wildfires.register.*;
import first.wildfires.space.SpaceBootstrap;
import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.celestial.StationCelestialProvider;
import first.wildfires.thermal.ThermalConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Wildfires.MODID)
public class Wildfires {

	public static final String MODID = "wildfires";

	public static final CreateRegistrate Registrate = CreateRegistrate.create(MODID);
	public static boolean TFCLoaded;
	public static boolean LSOLoaded;
	public static boolean CurioLoaded;

	public Wildfires(FMLJavaModLoadingContext context) {
		StartupDiagnostics.commonMark("Wildfires constructor");
		LegacyCelestialModGuard.rejectLoaded(ModList.get()::isLoaded);
		CelestialConfig.register();
		CelestialBodies.validateDefinitions();
		CelestialApi.register(net.minecraft.world.level.Level.OVERWORLD, OverworldCelestialProvider.INSTANCE);
		CelestialApi.register(SpaceDimensions.ORBIT, StationCelestialProvider.INSTANCE);
		ThermalConfig.register();
		SophisticatedItemActionsConfig.register();
		FoodTraitDisplayConfig.register();
		CreateMechanicalArmCompat.register();
		IEventBus eventBus = context.getModEventBus();
		GalaxyHymnRegister.register(eventBus);
		SpaceBootstrap.register(eventBus);
		Registrate.registerEventListeners(eventBus);
		AttributeRegister.register(eventBus);
		BlockEntityRegister.register();
		BlockRegister.register(eventBus);
		NetworkPacketRegister.register();
		CreativeModeTabRegister.register(eventBus);
		ItemRegister.register(eventBus);
		SoundRegister.register(eventBus);
		WeavingRecipeRegister.register(eventBus);
	}

	public static ResourceLocation rl(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}

}
