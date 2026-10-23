package first.wildfires;

import com.simibubi.create.foundation.data.CreateRegistrate;
import first.wildfires.api.celestial.CelestialApi;
import first.wildfires.celestial.CelestialConfig;
import first.wildfires.celestial.CelestialBodies;
import first.wildfires.celestial.LegacyCelestialModGuard;
import first.wildfires.celestial.OverworldCelestialProvider;
import first.wildfires.compats.irons_spellbooks.GalaxyHymnRegister;
import first.wildfires.compat.create.CreateMechanicalArmCompat;
import first.wildfires.compat.create.FanHeatConfig;
import first.wildfires.compat.minecraft.MapChunkLoadConfig;
import first.wildfires.compat.net.NetworkGuardTargets;
import first.wildfires.compat.sophisticateditemactions.SophisticatedItemActionsConfig;
import first.wildfires.compat.tfc.TfcDeviceHeatConfig;
import first.wildfires.jei.FoodTraitDisplayConfig;
import first.wildfires.client.spawnegg.SpawnEggPreviewConfig;
import first.wildfires.diagnostics.StartupDiagnostics;
import first.wildfires.register.*;
import first.wildfires.soldering.SolderingAuiClient;
import first.wildfires.soldering.SolderingRegister;
import first.wildfires.space.SpaceBootstrap;
import first.wildfires.space.SpaceDimensions;
import first.wildfires.space.celestial.StationCelestialProvider;
import first.wildfires.thermal.ThermalConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
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
		StartupDiagnostics.networkGuards(NetworkGuardTargets.all(), NetworkGuardTargets.dropped(), ModList.get()::isLoaded);
		CelestialConfig.register();
		CelestialBodies.validateDefinitions();
		CelestialApi.register(net.minecraft.world.level.Level.OVERWORLD, OverworldCelestialProvider.INSTANCE);
		CelestialApi.register(SpaceDimensions.ORBIT, StationCelestialProvider.INSTANCE);
		ThermalConfig.register();
		TfcDeviceHeatConfig.register();
		// FanHeatConfig.register(); // Moved into woodencog_wildfire: the fan ceilings now live in woodencog-common.toml under [woodencog.fan]
		SophisticatedItemActionsConfig.register();
		MapChunkLoadConfig.register();
		FoodTraitDisplayConfig.register();
		// Ported from the standalone entityspawnegg 1.0.0 mod: draws a spawn egg's mob in place of
		// the flat egg icon. Registered unconditionally like FoodTraitDisplayConfig above; Forge
		// ignores a CLIENT config on a dedicated server.
		SpawnEggPreviewConfig.register();
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
		SolderingRegister.register(eventBus);
		// The soldering station's menu and page bridge only make sense with ApricityUI installed.
		// Guarding the binding scan here also keeps the AUI menu class off a pack without it.
		if (ModList.get().isLoaded("apricityui")) {
			// Page scripts need KubeJS; the screen itself works without it, so only skip the binding scan.
			if (ModList.get().isLoaded("kubejs")) {
				DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> SolderingAuiClient::registerBindings);
			}
		}
		SoundRegister.register(eventBus);
		EntityRegister.register(eventBus);
		WeavingRecipeRegister.register(eventBus);
		// Textile 作物方块实体（棉花/亚麻用）。放在 BlockRegister 之后，
		// 因为它的 Builder.of(...) 需要引用已经注册好的作物方块。
		first.wildfires.textile.crop.TextileBlockEntities.register(eventBus);
		// Textile 野生作物的世界生成 feature（ID 是 textile:tall_wild_crop）
		first.wildfires.textile.crop.TextileFeatureRegister.register(eventBus);
		// Textile 的全局战利品修饰器（textile:add_item / textile:remove_item，34 个 json 依赖）
		first.wildfires.textile.loot.ModLootModifiers.register(eventBus);
		// Textile 的战利品数字提供器（textile:crop_yield_uniform，作物产量按 yield 插值）
		first.wildfires.textile.loot.TextileLoot.register(eventBus);
	}

	public static ResourceLocation rl(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}

}
