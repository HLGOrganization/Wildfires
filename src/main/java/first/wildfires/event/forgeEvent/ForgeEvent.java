package first.wildfires.event.forgeEvent;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.latvian.mods.kubejs.item.ItemClickedEventJS;
import first.wildfires.Wildfires;
import first.wildfires.diagnostics.StartupDiagnostics;
import first.wildfires.api.KineticData;
import first.wildfires.api.MobPoopData;
import first.wildfires.api.customEvent.*;
import first.wildfires.register.SoundRegister;
import first.wildfires.register.AttributeRegister;
import first.wildfires.register.BlockRegister;
import first.wildfires.register.ItemRegister;
import first.wildfires.structure.AndesiteCasingFrameDetector;
import first.wildfires.utils.CuriosUtil;
import first.wildfires.utils.WildfiresUtil;
import net.dries007.tfc.common.blockentities.CharcoalForgeBlockEntity;
import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blocks.plant.fruit.FruitTreeBranchBlock;
import net.dries007.tfc.common.blocks.plant.fruit.FruitTreeLeavesBlock;
import net.dries007.tfc.common.blocks.plant.fruit.Lifecycle;
import net.dries007.tfc.common.capabilities.food.FoodCapability;
import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.dries007.tfc.common.entities.livestock.TFCAnimalProperties;
import net.dries007.tfc.common.items.IngotItem;
import net.dries007.tfc.common.items.TFCItems;
import net.dries007.tfc.util.Metal;
import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.events.StartFireEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;
import sfiomn.legendarysurvivaloverhaul.api.temperature.TemperatureEnum;
import sfiomn.legendarysurvivaloverhaul.util.CapabilityUtil;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

import java.util.*;
import java.util.concurrent.CompletableFuture;


@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEvent {
	private static final int SIMPLE_COMPASS_DECAY_INTERVAL_TICKS = 20;
	private static final int SIMPLE_COMPASS_RAIN_REPAIR_INTERVAL_TICKS = 20 * 10;
	private static final int ITEM_BODY_TEMPERATURE_INTERVAL_TICKS = 20;
	private static final int RAIN_GEAR_DAMAGE_INTERVAL_TICKS = 20 * 10;
	private static final float HOT_WATER_BOTTLE_BODY_TEMPERATURE_SCALE = 0.01f;
	private static final float OTHER_HEATED_ITEM_BODY_TEMPERATURE_SCALE = 0.0001f;

	@SubscribeEvent
	public static void checkAndesiteCasingFramePlaced(BlockEvent.EntityPlaceEvent event) {
		if (event.getLevel() instanceof Level level) {
			AndesiteCasingFrameDetector.checkAfterPlacement(level, event.getPos());
		}
	}

	@SubscribeEvent
	public static void checkAndesiteCasingFrameBroken(BlockEvent.BreakEvent event) {
		if (event.getLevel() instanceof Level level) {
			AndesiteCasingFrameDetector.checkAfterBreak(level, event.getPos());
		}
	}

	private static final Map<String, Integer> RAIN_PROTECTION = Map.of(
			"wildfires:umbrella_hat", 80,
			"wildfires:wide_straw_hat", 35,
			"wildfires:conical_hat", 45,
			"wildfires:straw_rain_cape", 60,
			"wildfires:leather_windbreaker", 70,
			"wildfires:raincoat", 100,
			"wildfires:rubber_diving_chestplate", 50,
			"wildfires:rubber_diving_leggings", 50
	);
	private static final Map<String, Integer> WATER_PROTECTION = Map.of(
			"wildfires:rubber_diving_chestplate", 50,
			"wildfires:rubber_diving_leggings", 50
	);
	private static final Map<String, EquipmentSlot> WETNESS_PROTECTION_SLOTS = Map.of(
			"wildfires:umbrella_hat", EquipmentSlot.HEAD,
			"wildfires:wide_straw_hat", EquipmentSlot.HEAD,
			"wildfires:conical_hat", EquipmentSlot.HEAD,
			"wildfires:straw_rain_cape", EquipmentSlot.CHEST,
			"wildfires:leather_windbreaker", EquipmentSlot.CHEST,
			"wildfires:raincoat", EquipmentSlot.CHEST,
			"wildfires:rubber_diving_chestplate", EquipmentSlot.CHEST,
			"wildfires:rubber_diving_leggings", EquipmentSlot.LEGS
	);

	@SubscribeEvent
	public static void place(PlayerInteractEvent.RightClickItem event) {
		if (event.getItemStack().getItem() instanceof IngotItem) {
			event.setCancellationResult(InteractionResult.PASS);
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void lightUnrestrictedCharcoalForge(StartFireEvent event) {
		if (!event.isStrong() || !event.getState().is(BlockRegister.UnrestrictedCharcoalForge.get())) {
			return;
		}

		if (event.getLevel().getBlockEntity(event.getPos()) instanceof CharcoalForgeBlockEntity forge
				&& forge.light(event.getState())) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onCurioChange(CurioChangeEvent event) {
		CuriosUtil.invalidate(event.getEntity());
	}

	@SubscribeEvent
	public static void reload(AddReloadListenerEvent event) {
		event.addListener((preparationBarrier, resourceManager, profilerFiller, profilerFiller2, executor1, executor2) -> {
			long startedAt = StartupDiagnostics.now();
			StartupDiagnostics.serverMark("data reload listener started");
			return CompletableFuture.completedFuture(null).thenCompose(preparationBarrier::wait).thenAcceptAsync(v -> {
			KineticDataModifyEvent kineticDataModifyEvent = new KineticDataModifyEvent();
			WildfiresUtil.post(kineticDataModifyEvent);
			WildfiresUtil.kineticDataList.clear();
			WildfiresUtil.kineticDataList.addAll(kineticDataModifyEvent.getKineticData());
			MobPoopDataModifyEvent mobPoopDataModifyEvent = new MobPoopDataModifyEvent();
			WildfiresUtil.post(mobPoopDataModifyEvent);
			WildfiresUtil.mobPoopDataList.clear();
			List<MobPoopData> list = mobPoopDataModifyEvent.getMobPoopDataList();
			WildfiresUtil.mobPoopDataList.addAll(list);
			WildfiresUtil.PoopList.clear();
			for (MobPoopData data : list) {
				WildfiresUtil.PoopList.add(data.type());
			}
			WildfiresUtil.StructureStageMap.clear();
			StructureStageModifyEvent structureStageModifyEvent = new StructureStageModifyEvent();
			WildfiresUtil.post(structureStageModifyEvent);
			WildfiresUtil.StructureStageMap.putAll(structureStageModifyEvent.getStructureStageMap());
			StartupDiagnostics.serverCompleted("data reload listener", startedAt);
		}, executor2);
		});
	}

	@SubscribeEvent
	public static void onServerStarting(ServerStartingEvent event) {
		StartupDiagnostics.serverMark("starting");
	}

	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		StartupDiagnostics.serverMark("started");
	}

	@SubscribeEvent
	public static void right(PlayerInteractEvent.RightClickBlock event) {
		BlockPos pos = event.getPos();
		Player player = event.getEntity();
		Level level = player.level();
		if (!level.isClientSide()) {
			Block block = level.getBlockState(pos).getBlock();
			if (block instanceof FruitTreeBranchBlock) {
				BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
				for (int y = pos.getY() + 1; y < level.getMaxBuildHeight(); y++) {
					mutablePos.set(pos.getX(), y, pos.getZ());
					BlockState state = level.getBlockState(mutablePos);
					if (!(state.getBlock() instanceof FruitTreeBranchBlock) && !(state.getBlock() instanceof FruitTreeLeavesBlock)) {
						break;
					}
					if (state.getBlock() instanceof FruitTreeLeavesBlock) {
						Set<BlockPos> leaves = new HashSet<>();
						Queue<BlockPos> queue = new LinkedList<>();
						Set<BlockPos> visited = new HashSet<>();
						queue.offer(mutablePos.immutable());
						visited.add(mutablePos.immutable());
						while (!queue.isEmpty()) {
							BlockPos current = queue.poll();
							BlockState currentState = level.getBlockState(current);
							if (currentState.getBlock() instanceof FruitTreeLeavesBlock && currentState.hasProperty(FruitTreeLeavesBlock.LIFECYCLE) && !currentState.getValue(FruitTreeLeavesBlock.PERSISTENT)) {
								leaves.add(current);
							}
							for (Direction dir : Direction.values()) {
								BlockPos neighbor = current.relative(dir);
								if (!visited.contains(neighbor)) {
									visited.add(neighbor);
									BlockState neighborState = level.getBlockState(neighbor);
									Block neighborBlock = neighborState.getBlock();
									if (neighborBlock instanceof FruitTreeBranchBlock || neighborBlock instanceof FruitTreeLeavesBlock) {
										queue.offer(neighbor);
									}
								}
							}
						}
						for (BlockPos leafPos : leaves) {
							BlockState leafState = level.getBlockState(leafPos);
							if (leafState.getBlock() instanceof FruitTreeLeavesBlock fruitTreeLeavesBlock) {
								if (level.getRandom().nextDouble() < 0.25 && leafState.getValue(FruitTreeLeavesBlock.LIFECYCLE) == Lifecycle.FRUITING) {
									ItemStack item = fruitTreeLeavesBlock.getProductItem(level.getRandom());
									level.addFreshEntity(new ItemEntity(level, leafPos.getX() + 0.5, leafPos.getY() + 0.5, leafPos.getZ() + 0.5, item));
									level.setBlockAndUpdate(leafPos, leafState.setValue(FruitTreeLeavesBlock.LIFECYCLE, Lifecycle.HEALTHY));
								}
							}
						}
						break;
					}
				}
			}
		}
	}

	@SubscribeEvent
	public static void entity(MobSpawnEvent.FinalizeSpawn event) {
		Mob mob = event.getEntity();
		Level level = mob.level();
		if (!level.isClientSide()) {
			if (event.getSpawnType() == MobSpawnType.BREEDING) {
				List<Mob> mobList = level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(32), living -> living.distanceTo(mob) < 32 && living.getType() == mob.getType());
				if (mobList.size() > 16) {
					event.setSpawnCancelled(true);
				}
			}
		}
	}

	@SubscribeEvent
	public static void livingTick(LivingEvent.LivingTickEvent event){
		LivingEntity living = event.getEntity();
		Level level = living.level();
		if (!level.isClientSide()) {
			EntityType<?> type = living.getType();
			if (WildfiresUtil.PoopList.contains(type)) {
				for (MobPoopData data : WildfiresUtil.mobPoopDataList) {
					if (data.type() == type) {
						int ticks = data.ticks();
						CompoundTag tag = living.getPersistentData();
						int poopTicks = tag.getInt("MobPoopTicks");
						if (poopTicks >= ticks) {
							//如果动物没吃东西就取消拉屎
							if (Wildfires.TFCLoaded && living instanceof TFCAnimalProperties tfcAnimal && tfcAnimal.isHungry()) {
								break;
							}
							ItemStack itemStack = data.itemStack();
							if (itemStack!= null) {
								living.spawnAtLocation(itemStack.copy());
							}
							Block block = data.block();
							if (block != null) {
								BlockPos pos = living.blockPosition();
								BlockState blockState = level.getBlockState(pos);
								boolean pooped = false;
								if (blockState.isAir()) {
									level.setBlockAndUpdate(pos, block.defaultBlockState());
									pooped = true;
								} else if (blockState.getBlock() instanceof SnowLayerBlock && !blockState.is(block)) {
									// Snow layers also expose LAYERS, but animal droppings must replace
									// the snow instead of being mistaken for another poop layer.
									level.setBlockAndUpdate(pos, block.defaultBlockState());
									pooped = true;
								} else if (blockState.is(block) && blockState.hasProperty(BlockStateProperties.LAYERS)) {
									int value = blockState.getValue(BlockStateProperties.LAYERS);
									if (value < 8) {
										level.setBlock(pos, blockState.setValue(BlockStateProperties.LAYERS, value + 1), 2);
										pooped = true;
									}
								}
								if (pooped) {
									tag.remove("MobPoopTicks");
								}
							}
						} else {
							tag.putInt("MobPoopTicks", poopTicks + 1);
						}
						break;
					}
				}
			}
		}
	}

	/**
	 * Directly changes current LSO body temperature once per second. TFC remains
	 * the source of truth for item heat, so the contribution naturally follows
	 * the item's real cooling curve instead of accumulating in item NBT.
	 */
	@SubscribeEvent
	public static void applyCarriedItemBodyTemperature(TickEvent.PlayerTickEvent event) {
		if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()
				|| event.player.tickCount % ITEM_BODY_TEMPERATURE_INTERVAL_TICKS != 0) {
			return;
		}

		Player player = event.player;
		float hotWaterBottleDelta = 0f;
		float otherItemDelta = 0f;
		for (ItemStack stack : player.getInventory().items) {
			if (isHotWaterBottle(stack)) hotWaterBottleDelta += carriedItemBodyTemperatureDelta(stack);
			else otherItemDelta += carriedItemBodyTemperatureDelta(stack);
		}
		if (isHotWaterBottle(player.getOffhandItem())) hotWaterBottleDelta += carriedItemBodyTemperatureDelta(player.getOffhandItem());
		else otherItemDelta += carriedItemBodyTemperatureDelta(player.getOffhandItem());

		if (Wildfires.CurioLoaded) {
			IItemHandlerModifiable equippedCurios = CuriosApi.getCuriosInventory(player)
					.resolve()
					.map(handler -> handler.getEquippedCurios())
					.orElse(null);
			if (equippedCurios != null) {
				for (int slot = 0; slot < equippedCurios.getSlots(); slot++) {
					ItemStack stack = equippedCurios.getStackInSlot(slot);
					if (isHotWaterBottle(stack)) hotWaterBottleDelta += carriedItemBodyTemperatureDelta(stack);
					else otherItemDelta += carriedItemBodyTemperatureDelta(stack);
				}
			}
		}

		if (hotWaterBottleDelta != 0f || otherItemDelta != 0f) {
			var temperatureCapability = CapabilityUtil.getTempCapability(player);
			// A hot bag may warm a player into the HOT band, but never into HEAT_STROKE.
			float heatStrokeBoundary = TemperatureEnum.HEAT_STROKE.getLowerBound() - 0.001f;
			float allowedHotWaterBottleDelta = Math.max(0f, heatStrokeBoundary - (temperatureCapability.getTemperatureLevel() + otherItemDelta));
			if (hotWaterBottleDelta > 0f) hotWaterBottleDelta = Math.min(hotWaterBottleDelta, allowedHotWaterBottleDelta);
			temperatureCapability.addTemperatureLevel(otherItemDelta + hotWaterBottleDelta);
		}
	}

	private static float carriedItemBodyTemperatureDelta(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0f;
		}
		float scale = isHotWaterBottle(stack)
				? HOT_WATER_BOTTLE_BODY_TEMPERATURE_SCALE
				: OTHER_HEATED_ITEM_BODY_TEMPERATURE_SCALE;
		return HeatCapability.getTemperature(stack) * scale;
	}

	private static boolean isHotWaterBottle(ItemStack stack) {
		return !stack.isEmpty() && stack.getTags().anyMatch(key -> key.location().toString().equals("kubejs:hot_water_bottle"));
	}

	/**
	 * Rainproof attributes can reduce rain wetness to zero, so this deliberately
	 * tests physical rain exposure instead of relying on the wetness increment.
	 */
	@SubscribeEvent
	public static void damageRainGearInRain(TickEvent.PlayerTickEvent event) {
		if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()
				|| event.player.tickCount % RAIN_GEAR_DAMAGE_INTERVAL_TICKS != 0
				|| !event.player.level().isRainingAt(event.player.blockPosition())) {
			return;
		}
		damageRainGear(event.player, EquipmentSlot.HEAD, ItemRegister.ConicalHat.get());
		damageRainGear(event.player, EquipmentSlot.CHEST, ItemRegister.StrawRainCape.get());
	}

	private static void damageRainGear(Player player, EquipmentSlot slot, Item gear) {
		ItemStack stack = player.getItemBySlot(slot);
		if (stack.is(gear)) {
			stack.hurtAndBreak(1, player, entity -> entity.broadcastBreakEvent(slot));
		}
	}

	@SubscribeEvent
	public static void kineticBlockEntityTick(KineticBlockEntityTickEvent.Post event) {
		KineticBlockEntity kinetic = event.getKineticBlockEntity();
		Level level = kinetic.getLevel();
		if (level != null && !level.isClientSide()) {
			KineticNetwork network = kinetic.getOrCreateNetwork();
			if (network != null) {
				for (KineticData kineticData : WildfiresUtil.kineticDataList) {
					if (kineticData.block().equals(kinetic.getBlockState().getBlock())) {
						boolean c1 = kineticData.maxNetworkStress() != null && kineticData.maxNetworkStress() < network.calculateStress();
						boolean c2 = kineticData.maxSpeed() != null && Math.abs(kineticData.maxSpeed()) < Math.abs(kinetic.getSpeed());
						if (c1 || c2) {
							List<ItemStack> list = kineticData.list();
							level.destroyBlock(kinetic.getBlockPos(), list == null);
							if (list != null) {
                                for (ItemStack itemStack : list) {
                                    Vec3 center = kinetic.getBlockPos().getCenter();
                                    level.addFreshEntity(new ItemEntity(level, center.x(), center.y(), center.z(), itemStack.copy()));
                                }
                            }
                        }
						break;
					}
				}
			}
		}
	}

	@SubscribeEvent
	public static void itemEntityTick(ItemEntityTickEvent.Post event) {
		ItemEntity itemEntity = event.getItemEntity();
		ItemStack itemStack = itemEntity.getItem();
		IHeat iHeat = HeatCapability.get(itemStack);
		if (!itemEntity.level().isClientSide() && itemEntity.isInWaterRainOrBubble() && iHeat != null && (iHeat.getTemperature() >= iHeat.getWorkingTemperature() * 0.6) && itemStack.getTags().anyMatch(itemTagKey -> itemTagKey.location().toString().equals("kubejs:tool_head"))) {
			CompoundTag tag = itemStack.getOrCreateTag();
			tag.putInt("Quenching", tag.getInt("Quenching") + 1);
			tag.putInt("Polish", tag.getInt("Polish") + 1);
			if (iHeat.canWeld()) {
				tag.putBoolean("Broken", true);
			}
		}
	}

	@SubscribeEvent
	public static void metalPipeSound(ItemEntityTickEvent.Post event) {
		ItemEntity itemEntity = event.getItemEntity();
		Level level = itemEntity.level();
		ItemStack item = itemEntity.getItem();
		if (!level.isClientSide() && item.is(TFCItems.METAL_ITEMS.get(Metal.Default.STEEL).get(Metal.ItemType.ROD).get())) {
			CompoundTag tag = itemEntity.getPersistentData();
			boolean onGround = itemEntity.onGround();
			boolean lastOnGround = tag.getBoolean("lastOnGround");
			if (onGround && !lastOnGround) {
				WildfiresUtil.playSound(
						level,
						itemEntity.blockPosition(),
						SoundRegister.MetalPipe.get(),
						SoundSource.BLOCKS
				);
			}
			if (onGround != lastOnGround) {
				tag.putBoolean("lastOnGround", onGround);
			}
		}
	}

	@SubscribeEvent
	public static void hurt(LivingHurtEvent event) {
		if (event.getSource().getEntity() instanceof LivingEntity attacker) {
			ItemStack item = attacker.getMainHandItem();
			if (item.getTags().anyMatch(itemTagKey -> itemTagKey.location().toString().equals("kubejs:spear"))) {
				LivingEntity target = event.getEntity();
				AABB aabb = target.getBoundingBox();
				Vec3 center = attacker.getBoundingBox().getCenter();
				double dx = Math.max(aabb.minX - center.x, Math.max(0, center.x - aabb.maxX));
				double dy = Math.max(aabb.minY - center.y, Math.max(0, center.y - aabb.maxY));
				double dz = Math.max(aabb.minZ - center.z, Math.max(0, center.z - aabb.maxZ));
				float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
				if (distance <= 4) {
					event.setAmount(event.getAmount() * (0.5f + (distance * 0.125f)));
				}
			}
		}
	}

	@SubscribeEvent
	public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
		CompoundTag tag = event.getEntity().getMainHandItem().getTag();
		if (tag != null && event.getEntity().getMainHandItem().getTags().anyMatch(itemTagKey -> itemTagKey.location().toString().equals("kubejs:polish"))) {
			int polish = tag.getInt("Polish");
			if (polish > 0) {
				event.setNewSpeed(event.getNewSpeed() * Math.min(2, (1 + polish * 0.001f)));
			}
		}
		if (tag != null) {
			int quenching = tag.getInt("Quenching");
			if (quenching > 0) {
				event.setNewSpeed(event.getNewSpeed() * (1 + (float) (1 - 1.0 / (1 + quenching / 900.0))));
			}
		}
	}

	@SubscribeEvent
	public static void itemModify(ItemAttributeModifierEvent event) {
		ItemStack itemStack = event.getItemStack();
		CompoundTag tag = itemStack.getTag();
		addWetnessProtectionModifiers(event, itemStack);
		if (tag != null && event.getSlotType() == EquipmentSlot.MAINHAND && itemStack.getTags().anyMatch(itemTagKey -> itemTagKey.location().toString().equals("kubejs:polish"))) {
			int polish = tag.getInt("Polish");
			if (polish > 0) {
				String name = "Polish";
				AttributeModifier modifier = new AttributeModifier(
						WildfiresUtil.getUUID(name),
						name,
						Math.min(0.15, polish * 0.001),
						AttributeModifier.Operation.MULTIPLY_TOTAL
				);
				event.addModifier(Attributes.ATTACK_DAMAGE, modifier);
			}
		}
	}

	/** Applies equipment rainproof percentage only to wetness caused by rain. */
	@SubscribeEvent
	public static void reduceRainWetness(PlayerWetnessEvent.RainIncrease event) {
		// The attribute is displayed at one tenth of its effective percentage.
		double rainproofPercent = event.getPlayer().getAttributeValue(AttributeRegister.Rainproof.get()) * 10d;
		if (rainproofPercent <= 0d) {
			return;
		}

		float wetnessChance = (float) Math.max(0d, 1d - rainproofPercent / 100d);
		int remainingWetness = 0;
		for (int point = 0; point < event.getWetness(); point++) {
			if (event.getPlayer().getRandom().nextFloat() < wetnessChance) {
				remainingWetness++;
			}
		}
		event.setWetness(remainingWetness);
	}

	private static void addWetnessProtectionModifiers(ItemAttributeModifierEvent event, ItemStack itemStack) {
		String itemId = ForgeRegistries.ITEMS.getKey(itemStack.getItem()).toString();
		if (event.getSlotType() != WETNESS_PROTECTION_SLOTS.get(itemId)) {
			return;
		}

		int waterproof = WATER_PROTECTION.getOrDefault(itemId, 0);
		if (waterproof > 0) {
			event.addModifier(AttributeRegister.Waterproof.get(), new AttributeModifier(
					WildfiresUtil.getUUID("waterproof_" + itemId),
					"Waterproof",
					waterproof * 0.1d,
					AttributeModifier.Operation.ADDITION
			));
		}

		int extraRainproof = Math.max(0, RAIN_PROTECTION.getOrDefault(itemId, 0) - waterproof);
		if (extraRainproof > 0) {
			event.addModifier(AttributeRegister.Rainproof.get(), new AttributeModifier(
					WildfiresUtil.getUUID("rainproof_" + itemId),
					"Rainproof",
					extraRainproof * 0.1d,
					AttributeModifier.Operation.ADDITION
			));
		}
	}

	@SubscribeEvent
	public static void PlayerTick(TickEvent.PlayerTickEvent event) {
		Player player = event.player;
		Level level = player.level();
		if (Wildfires.TFCLoaded && !level.isClientSide()) {
			AbstractContainerMenu containerMenu = player.containerMenu;
			for (Slot slot : containerMenu.slots) {
				ItemStack item = slot.getItem();
				if (FoodCapability.isRotten(item)) {
					FoodRottenEvent rottenEvent = new FoodRottenEvent(item);
					WildfiresUtil.post(rottenEvent);
					ItemStack newItemStack = rottenEvent.getItemStack();
					if (rottenEvent.getItemStack() != item) {
						slot.set(newItemStack);
					}
				}
			}
		}
	}

	/**
	 * Runs before TFC's drinking handler. A damaged compass is restored by a
	 * water source; a fully repaired compass still allows the normal drink action.
	 */
	@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
	public static void repairCompassInWater(PlayerInteractEvent.RightClickBlock event) {
		ItemStack heldItem = event.getItemStack();
		FluidState fluid = event.getLevel().getFluidState(event.getPos());
		if (!repairCompassInWater(event.getEntity(), event.getHand(), heldItem, event.getLevel(), fluid)) {
			return;
		}

		event.setCancellationResult(InteractionResult.SUCCESS);
		event.setCanceled(true);
	}

	/**
	 * TFC can handle drinking through the item-use path when the target is a fluid
	 * source, so also catch that path and resolve the source with TFC's ray trace.
	 */
	@SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
	public static void repairCompassInWater(PlayerInteractEvent.RightClickItem event) {
		ItemStack heldItem = event.getItemStack();
		BlockHitResult hit = Helpers.rayTracePlayer(event.getLevel(), event.getEntity(), ClipContext.Fluid.SOURCE_ONLY);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}

		FluidState fluid = event.getLevel().getFluidState(hit.getBlockPos());
		if (!repairCompassInWater(event.getEntity(), event.getHand(), heldItem, event.getLevel(), fluid)) {
			return;
		}

		event.setCancellationResult(InteractionResult.SUCCESS);
		event.setCanceled(true);
	}

	private static boolean repairCompassInWater(Player player, InteractionHand hand, ItemStack heldItem, Level level, FluidState fluid) {
		boolean isDamagedCompass = heldItem.is(ItemRegister.DamagedCompass.get());
		boolean isWornSimpleCompass = heldItem.is(ItemRegister.SimpleCompass.get())
				&& heldItem.getDamageValue() > 0;
		if ((!isDamagedCompass && !isWornSimpleCompass) || !isWaterSource(fluid)) {
			return false;
		}

		if (!level.isClientSide()) {
			if (isDamagedCompass) {
				player.setItemInHand(hand, new ItemStack(ItemRegister.SimpleCompass.get()));
			} else {
				heldItem.setDamageValue(0);
			}
		}

		return true;
	}

	private static boolean isWaterSource(FluidState fluid) {
		return fluid.isSource()
				&& (fluid.getType().is(FluidTags.WATER)
				|| fluid.getType().is(TFCTags.Fluids.ANY_FRESH_WATER)
				|| fluid.getType().is(TFCTags.Fluids.ANY_WATER));
	}

	/**
	 * A simple compass loses durability while carried. A compass held in either
	 * hand is protected from that loss in rain and slowly repairs instead.
	 */
	@SubscribeEvent
	public static void decaySimpleCompass(TickEvent.PlayerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}

		Player player = event.player;
		if (player.level().isClientSide() || player.tickCount % SIMPLE_COMPASS_DECAY_INTERVAL_TICKS != 0) {
			return;
		}

		boolean raining = player.level().isRainingAt(player.blockPosition());
		ItemStack mainHand = player.getMainHandItem();
		ItemStack offhand = player.getOffhandItem();
		if (raining && player.tickCount % SIMPLE_COMPASS_RAIN_REPAIR_INTERVAL_TICKS == 0) {
			repairRainSoakedCompass(mainHand);
			repairRainSoakedCompass(offhand);
		}

		for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
			decaySimpleCompass(player.getInventory().items, slot, raining ? mainHand : null, raining ? offhand : null);
		}
		for (int slot = 0; slot < player.getInventory().offhand.size(); slot++) {
			decaySimpleCompass(player.getInventory().offhand, slot, raining ? mainHand : null, raining ? offhand : null);
		}
	}

	private static void repairRainSoakedCompass(ItemStack stack) {
		if (stack.is(ItemRegister.SimpleCompass.get()) && stack.getDamageValue() > 0) {
			stack.setDamageValue(stack.getDamageValue() - 1);
		}
	}

	private static void decaySimpleCompass(java.util.List<ItemStack> inventory, int slot, ItemStack protectedMainHand, ItemStack protectedOffhand) {
		ItemStack stack = inventory.get(slot);
		if (!stack.is(ItemRegister.SimpleCompass.get()) || stack == protectedMainHand || stack == protectedOffhand) {
			return;
		}

		if (stack.getDamageValue() + 1 >= stack.getMaxDamage()) {
			inventory.set(slot, new ItemStack(ItemRegister.DamagedCompass.get()));
		} else {
			stack.setDamageValue(stack.getDamageValue() + 1);
		}
	}

}
