package first.wildfires.register;

import com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockModel;
import com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem;
import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.SharedProperties;
import com.simibubi.create.api.stress.BlockStressValues;
import com.tterrag.registrate.util.entry.BlockEntry;
import first.wildfires.Wildfires;
import first.wildfires.block.*;
import first.wildfires.kinetic.loom.LoomBlockItem;
import first.wildfires.kinetic.loom.LoomAuxiliaryBlock;
import first.wildfires.kinetic.loom.LoomControlBlock;
import first.wildfires.kinetic.loom.LoomStructureBlock;
import net.dries007.tfc.common.blockentities.CharcoalForgeBlockEntity;
import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.dries007.tfc.common.blockentities.TFCBlockEntities;
import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.common.blocks.devices.CharcoalForgeBlock;
import net.dries007.tfc.common.blocks.devices.CrucibleBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.DoubleSupplier;

import static com.simibubi.create.foundation.data.ModelGen.customItemModel;
import static com.simibubi.create.foundation.data.TagGen.axeOrPickaxe;
import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;
import static net.minecraft.world.level.block.Block.box;

public class BlockRegister {

    private static final CreateRegistrate Registrate = Wildfires.Registrate;

    public static final BlockEntry<LoomControlBlock> LoomControlBlock =
            Registrate.block("loom_control_block", LoomControlBlock::new)
                    .initialProperties(SharedProperties::wooden)
                    .properties(p -> p.mapColor(MapColor.METAL))
                    .transform(pickaxeOnly())
                    .blockstate((c, p) -> p.simpleBlock(c.getEntry(), AssetLookup.partialBaseModel(c, p)))
                    .item(LoomBlockItem::new)
                    .build()
                    .register();

    public static final BlockEntry<LoomStructureBlock> LoomStructureBlock =
            Registrate.block("loom_structure_block", LoomStructureBlock::new)
                    .initialProperties(SharedProperties::wooden)
                    .properties(p -> p.mapColor(MapColor.COLOR_BLACK).noOcclusion())
                    .transform(pickaxeOnly())
                    .register();

    public static final BlockEntry<LoomAuxiliaryBlock> LoomAuxiliaryBlock =
            Registrate.block("loom_auxiliary_block", LoomAuxiliaryBlock::new)
                    .initialProperties(SharedProperties::wooden)
                    .properties(p -> p.mapColor(MapColor.COLOR_BLACK).noOcclusion())
                    .transform(pickaxeOnly())
                    .register();

    /*  { in: "granite", out: "granite" },
      { in: "diorite", out: "diorite" },
      { in: "gabbro", out: "tuff" },
      { in: "rhyolite", out: "dripstone" },
      { in: "basalt", out: "deepslate" },
      { in: "dacite", out: "limestone" }*/
    // Granite（花岗岩）磨石 - 绑定 GraniteMillingWheel 模型
    public static final BlockEntry<CustomMillstoneBlock> StoneMillgranite = Registrate
            .block("granite_millstone",
                    properties -> new CustomMillstoneBlock(PartialModelRegister.GraniteMillingWheel, properties))
            .initialProperties(SharedProperties::stone)
            .properties(p -> p.mapColor(MapColor.METAL))
            .transform(pickaxeOnly())
            .onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> 4.0D))
            .blockstate((c, p) -> p.simpleBlock(c.getEntry(), AssetLookup.partialBaseModel(c, p)))
            .item()
            .transform(customItemModel())
            .register();

    // Diorite（闪长岩）磨石 - 绑定 DioriteMillingWheel 模型
    public static final BlockEntry<CustomMillstoneBlock> StoneMilldiorite = Registrate
            .block("diorite_millstone",
                    properties -> new CustomMillstoneBlock(PartialModelRegister.DioriteMillingWheel, properties))
            .initialProperties(SharedProperties::stone)
            .properties(p -> p.mapColor(MapColor.METAL))
            .transform(pickaxeOnly())
            .onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> 4.0D))
            .blockstate((c, p) -> p.simpleBlock(c.getEntry(), AssetLookup.partialBaseModel(c, p)))
            .item()
            .transform(customItemModel())
            .register();

    // Gabbro（辉长岩）磨石 - 绑定 GabbroMillingWheel 模型
    public static final BlockEntry<CustomMillstoneBlock> StoneMillgabbro = Registrate
            .block("gabbro_millstone",
                    properties -> new CustomMillstoneBlock(PartialModelRegister.GabbroMillingWheel, properties))
            .initialProperties(SharedProperties::stone)
            .properties(p -> p.mapColor(MapColor.METAL))
            .transform(pickaxeOnly())
            .onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> 4.0D))
            .blockstate((c, p) -> p.simpleBlock(c.getEntry(), AssetLookup.partialBaseModel(c, p)))
            .item()
            .transform(customItemModel())
            .register();

    // Rhyolite（流纹岩）磨石 - 绑定 RhyoliteMillingWheel 模型
    public static final BlockEntry<CustomMillstoneBlock> StoneMillrhyolite = Registrate
            .block("rhyolite_millstone",
                    properties -> new CustomMillstoneBlock(PartialModelRegister.RhyoliteMillingWheel, properties))
            .initialProperties(SharedProperties::stone)
            .properties(p -> p.mapColor(MapColor.METAL))
            .transform(pickaxeOnly())
            .onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> 4.0D))
            .blockstate((c, p) -> p.simpleBlock(c.getEntry(), AssetLookup.partialBaseModel(c, p)))
            .item()
            .transform(customItemModel())
            .register();

    // Basalt（玄武岩）磨石 - 绑定 BasaltMillingWheel 模型
    public static final BlockEntry<CustomMillstoneBlock> StoneMillbasalt = Registrate
            .block("basalt_millstone",
                    properties -> new CustomMillstoneBlock(PartialModelRegister.BasaltMillingWheel, properties))
            .initialProperties(SharedProperties::stone)
            .properties(p -> p.mapColor(MapColor.METAL))
            .transform(pickaxeOnly())
            .onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> 4.0D))
            .blockstate((c, p) -> p.simpleBlock(c.getEntry(), AssetLookup.partialBaseModel(c, p)))
            .item()
            .transform(customItemModel())
            .register();

    // Dacite（英安岩）磨石 - 绑定 DaciteMillingWheel 模型
    public static final BlockEntry<CustomMillstoneBlock> StoneMilldacite = Registrate
            .block("dacite_millstone",
                    properties -> new CustomMillstoneBlock(PartialModelRegister.DaciteMillingWheel, properties))
            .initialProperties(SharedProperties::stone)
            .properties(p -> p.mapColor(MapColor.METAL))
            .transform(pickaxeOnly())
            .onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> 4.0D))
            .blockstate((c, p) -> p.simpleBlock(c.getEntry(), AssetLookup.partialBaseModel(c, p)))
            .item()
            .transform(customItemModel())
            .register();
    // 基础 granite 版本（保留原有正确的 GraniteCrushingWheel 引用）
    public static final BlockEntry<CustomCrushingWheelBlock> StoneCrushingWheelGranite = Registrate
            .block("granite_crushing_wheel",
                    properties -> new CustomCrushingWheelBlock(PartialModelRegister.GraniteCrushingWheel, properties))
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_BLACK).sound(SoundType.NETHERITE_BLOCK))
            .transform(pickaxeOnly())
            .onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> 8.0D))
            .onRegister(CreateRegistrate.blockModel(() -> BracketedKineticBlockModel::new))
            .item()
            .transform(customItemModel())
            .register();

    // 补充 diorite 对应的破碎轮（修正为 DioriteCrushingWheel）
    public static final BlockEntry<CustomCrushingWheelBlock> StoneCrushingWheelDiorite = Registrate
            .block("diorite_crushing_wheel",
                    properties -> new CustomCrushingWheelBlock(PartialModelRegister.DioriteCrushingWheel, properties))
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_BLACK).sound(SoundType.NETHERITE_BLOCK))
            .transform(pickaxeOnly())
            .onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> 8.0D))
            .onRegister(CreateRegistrate.blockModel(() -> BracketedKineticBlockModel::new))
            .item()
            .transform(customItemModel())
            .register();

    // 补充 gabbro 对应的破碎轮（修正为 GabbroCrushingWheel）
    public static final BlockEntry<CustomCrushingWheelBlock> StoneCrushingWheelGabbro = Registrate
            .block("gabbro_crushing_wheel",
                    properties -> new CustomCrushingWheelBlock(PartialModelRegister.GabbroCrushingWheel, properties))
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_BLACK).sound(SoundType.NETHERITE_BLOCK))
            .transform(pickaxeOnly())
            .onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> 8.0D))
            .onRegister(CreateRegistrate.blockModel(() -> BracketedKineticBlockModel::new))
            .item()
            .transform(customItemModel())
            .register();

    // 补充 rhyolite 对应的破碎轮（修正为 RhyoliteCrushingWheel）
    public static final BlockEntry<CustomCrushingWheelBlock> StoneCrushingWheelRhyolite = Registrate
            .block("rhyolite_crushing_wheel",
                    properties -> new CustomCrushingWheelBlock(PartialModelRegister.RhyoliteCrushingWheel, properties))
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_BLACK).sound(SoundType.NETHERITE_BLOCK))
            .transform(pickaxeOnly())
            .onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> 8.0D))
            .onRegister(CreateRegistrate.blockModel(() -> BracketedKineticBlockModel::new))
            .item()
            .transform(customItemModel())
            .register();

    // 补充 basalt 对应的破碎轮（修正为 BasaltCrushingWheel）
    public static final BlockEntry<CustomCrushingWheelBlock> StoneCrushingWheelBasalt = Registrate
            .block("basalt_crushing_wheel",
                    properties -> new CustomCrushingWheelBlock(PartialModelRegister.BasaltCrushingWheel, properties))
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_BLACK).sound(SoundType.NETHERITE_BLOCK))
            .transform(pickaxeOnly())
            .onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> 8.0D))
            .onRegister(CreateRegistrate.blockModel(() -> BracketedKineticBlockModel::new))
            .item()
            .transform(customItemModel())
            .register();

    // 补充 dacite 对应的破碎轮（修正为 DaciteCrushingWheel）
    public static final BlockEntry<CustomCrushingWheelBlock> StoneCrushingWheelDacite = Registrate
            .block("dacite_crushing_wheel",
                    properties -> new CustomCrushingWheelBlock(PartialModelRegister.DaciteCrushingWheel, properties))
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_BLACK).sound(SoundType.NETHERITE_BLOCK))
            .transform(pickaxeOnly())
            .onRegister(block -> BlockStressValues.IMPACTS.register(block, () -> 8.0D))
            .onRegister(CreateRegistrate.blockModel(() -> BracketedKineticBlockModel::new))
            .item()
            .transform(customItemModel())
            .register();

    public static final BlockEntry<CustomCogWheelBlock> StoneCogWheel = Registrate
            .block("stone_cogwheel",
                    properties -> new CustomCogWheelBlock(PartialModelRegister.StoneCogWheel, false, properties))
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_BLACK).sound(SoundType.NETHERITE_BLOCK))
            .transform(axeOrPickaxe())
            .onRegister(CreateRegistrate.blockModel(() -> BracketedKineticBlockModel::new))
            .item(CogwheelBlockItem::new)
            .transform(customItemModel())
            .register();

    public static final BlockEntry<CustomCogWheelBlock> StoneLargeCogWheel = Registrate
            .block("stone_large_cogwheel",
                    properties -> new CustomCogWheelBlock(PartialModelRegister.StoneLargeCogWheel, true, properties))
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_BLACK).sound(SoundType.NETHERITE_BLOCK))
            .transform(axeOrPickaxe())
            .onRegister(CreateRegistrate.blockModel(() -> BracketedKineticBlockModel::new))
            .item(CogwheelBlockItem::new)
            .transform(customItemModel())
            .register();

    public static final DeferredRegister<Block> Register = DeferredRegister.create(Registries.BLOCK, Wildfires.MODID);

    public static final RegistryObject<UnrestrictedCharcoalForgeBlock> UnrestrictedCharcoalForge =
            Register.register("unrestricted_charcoal_forge", () -> new UnrestrictedCharcoalForgeBlock(
                    ExtendedProperties.of()
                            .sound(SoundType.STONE)
                            .strength(3.5F)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()
                            .lightLevel(state -> state.getValue(CharcoalForgeBlock.HEAT))
                            .blockEntity(TFCBlockEntities.CHARCOAL_FORGE)
                            .serverTicks(UnrestrictedCharcoalForgeBlock::serverTick)
            ));

    public static final RegistryObject<CrucibleBlock> Crucible = Register.register("crucible", () -> new CrucibleBlock(
            ExtendedProperties.of(MapColor.METAL)
                    .strength(3.0F)
                    .sound(SoundType.METAL)
                    .blockEntity(TFCBlockEntities.CRUCIBLE)
                    .serverTicks(CrucibleBlockEntity::serverTick)
    ));

    public static final RegistryObject<DecorativeCrucibleBlock> DecorativeCrucible = Register.register(
            "decorative_crucible",
            () -> new DecorativeCrucibleBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F)
                    .sound(SoundType.METAL))
    );

    public static final RegistryObject<SlabBlock> GrassSlab = Register.register("grass_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(Blocks.GRASS_BLOCK)));

    public static final RegistryObject<WildMushroomBlock> WildBrownMushroom = Register.register(
            "wild_brown_mushroom", () -> new WildMushroomBlock(BlockBehaviour.Properties.copy(Blocks.BROWN_MUSHROOM)));

    public static final RegistryObject<WildMushroomBlock> WildRedMushroom = Register.register(
            "wild_red_mushroom", () -> new WildMushroomBlock(BlockBehaviour.Properties.copy(Blocks.RED_MUSHROOM)));

    public static final RegistryObject<WildMushroomBlock> WildFluorescystShroom = Register.register(
            "wild_fluorescyst_shroom", () -> new WildMushroomBlock(
                    BlockBehaviour.Properties.copy(Blocks.BROWN_MUSHROOM).lightLevel(state -> 5)));

    // 筋腱：贴地摆放，湿的过 20 分钟风干；两者遇水都会被冲走，失去下方支撑就掉落
    public static final RegistryObject<FreshSinewBlock> Sinew = Register.register("sinew",
            () -> new FreshSinewBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(0.5F)
                    .sound(SoundType.WOOL)
                    .noCollission()));

    public static final RegistryObject<SinewBlock> DriedSinew = Register.register("dried_sinew",
            () -> new SinewBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(0.5F)
                    .sound(SoundType.WOOL)
                    .noCollission()));

    // 水下涡轮
    public static final RegistryObject<Block> UnderwaterTurbine = Register.register("underwater_turbine",
            () -> {
                BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                        .sound(SoundType.NETHERITE_BLOCK)
                        .strength(1.5f)
                        .requiresCorrectToolForDrops()
                        .noOcclusion();
                VoxelShape north = Shapes.block();
                VoxelShape south = Shapes.block();
                VoxelShape west = Shapes.block();
                VoxelShape east = Shapes.block();
                return new WrenchableDirectionalBlock(properties, north, east, south, west);
            });

    // 潜艇核心
    public static final RegistryObject<Block> SubmarineCore = Register.register("submarine_core", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.COPPER)
                .strength(1.5f)
                .requiresCorrectToolForDrops()
                .noOcclusion();
        VoxelShape north = Shapes.block();
        VoxelShape south = Shapes.block();
        VoxelShape west = Shapes.block();
        VoxelShape east = Shapes.block();
        return new WrenchableDirectionalBlock(properties, north, east, south, west);
    });

    // 简易气球
    public static final RegistryObject<Block> SimpleAirCushion = Register.register("simple_air_cushion", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.WOOL)
                .strength(1.5f)
                .noOcclusion();
        VoxelShape north = Shapes.block();
        VoxelShape south = Shapes.block();
        VoxelShape west = Shapes.block();
        VoxelShape east = Shapes.block();
        return new CustomDirectionalBlock(properties, north, east, south, west);
    });

    // 气球
    public static final RegistryObject<Block> AirCushion = Register.register("air_cushion", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.WOOL)
                .strength(1.5f)
                .noOcclusion();
        VoxelShape north = Shapes.block();
        VoxelShape south = Shapes.block();
        VoxelShape west = Shapes.block();
        VoxelShape east = Shapes.block();
        return new CustomDirectionalBlock(properties, north, east, south, west);
    });

    // 双翼
    public static final RegistryObject<Block> DoubleWing = Register.register("double_wing", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.SCAFFOLDING)
                .strength(1.5f)
                .requiresCorrectToolForDrops()
                .noOcclusion();

        VoxelShape north = box(0, 3, 6, 16, 14, 26).optimize();
        VoxelShape south = box(0, 3, -10, 16, 14, 10).optimize();
        VoxelShape west = box(6, 3, 0, 26, 14, 16).optimize();
        VoxelShape east = box(-10, 3, 0, 10, 14, 16).optimize();
        return new WrenchableDirectionalBlock(properties, north, east, south, west);
    });

    // 飞艇侧板
    public static final RegistryObject<Block> AirshipSlats = Register.register("airship_slats", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.SCAFFOLDING)
                .strength(1.5f)
                .requiresCorrectToolForDrops()
                .noOcclusion();
        VoxelShape north = box(0, 4, 14, 16, 13, 16);
        VoxelShape south = box(0, 4, 0, 16, 13, 2);
        VoxelShape west = box(14, 4, 0, 16, 13, 16);
        VoxelShape east = box(0, 4, 0, 2, 13, 16);
        return new WrenchableDirectionalBlock(properties, north, east, south, west);
    });

    // 人力飞机扇叶
    public static final RegistryObject<Block> GyrodynePropeller = Register.register("gyrodyne_propeller",
            () -> {
                BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                        .sound(SoundType.SCAFFOLDING)
                        .strength(1.5f)
                        .requiresCorrectToolForDrops()
                        .noOcclusion();
                return new WrenchableShapeBlock(properties, box(0, 4, 0, 2, 13, 16));
            });


    // 双翼机引擎
    public static final RegistryObject<Block> BiplaneEngine = Register.register("biplane_engine", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.NETHERITE_BLOCK)
                .strength(1.5f)
                .requiresCorrectToolForDrops()
                .noOcclusion();
        VoxelShape north = box(0, 0, 0, 16, 16, 16);
        VoxelShape east = box(0, 0, 0, 16, 16, 16);
        VoxelShape south = box(0, 0, 0, 16, 16, 16);
        VoxelShape west = box(0, 0, 0, 16, 16, 16);
        return new WrenchableDirectionalBlock(properties, north, east, south, west);
    });

    // 大型飞艇引擎
    public static final RegistryObject<Block> LargeAirshipEngine = Register.register("large_airship_engine",
            () -> {
                BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                        .sound(SoundType.NETHERITE_BLOCK)
                        .strength(1.5f)
                        .requiresCorrectToolForDrops()
                        .noOcclusion();
                VoxelShape north = box(-1, 0, -2, 17, 18, 16);
                VoxelShape south = box(-1, 0, -2, 17, 18, 16);
                VoxelShape west = box(-1, 0, -2, 17, 18, 16);
                VoxelShape east = box(-1, 0, -2, 17, 18, 16);
                return new WrenchableDirectionalBlock(properties, north, east, south, west);
            });

    // 小型引擎
    public static final RegistryObject<Block> SmallEngine = Register.register("small_engine", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.NETHERITE_BLOCK)
                .strength(1.5f)
                .requiresCorrectToolForDrops()
                .noOcclusion();
        VoxelShape north = box(1, 0, 0, 15, 16, 16);
        VoxelShape south = box(1, 0, 0, 15, 16, 16);
        VoxelShape west = box(0, 0, 1, 16, 16, 15);
        VoxelShape east = box(0, 0, 1, 16, 16, 15);
        return new WrenchableDirectionalBlock(properties, north, east, south, west);
    });

    // 小型侧引擎
    public static final RegistryObject<Block> SmallSideEngine = Register.register("small_side_engine", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.NETHERITE_BLOCK)
                .strength(1.5f)
                .requiresCorrectToolForDrops()
                .noOcclusion();
        VoxelShape north = box(2, 2, 0, 14, 14, 16);
        VoxelShape south = box(2, 2, 0, 14, 14, 16);
        VoxelShape west = box(0, 2, 2, 16, 14, 14);
        VoxelShape east = box(0, 2, 2, 16, 14, 14);
        return new WrenchableDirectionalBlock(properties, north, east, south, west);
    });

    // 坚固小型引擎
    public static final RegistryObject<Block> RuggedSmallEngine = Register.register("rugged_small_engine",
            () -> {
                BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                        .sound(SoundType.NETHERITE_BLOCK)
                        .strength(1.5f)
                        .requiresCorrectToolForDrops()
                        .noOcclusion();
                VoxelShape north = box(1, 0, 0, 15, 16, 16);
                VoxelShape south = box(1, 0, 0, 15, 16, 16);
                VoxelShape west = box(0, 0, 1, 16, 16, 15);
                VoxelShape east = box(0, 0, 1, 16, 16, 15);
                return new WrenchableDirectionalBlock(properties, north, east, south, west);
            });

    // 大型螺旋桨
    public static final RegistryObject<Block> LargePropeller = Register.register("large_propeller", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.NETHERITE_BLOCK)
                .strength(1.5f)
                .requiresCorrectToolForDrops()
                .noOcclusion();
        VoxelShape north = box(5, 6, 12.5, 11, 12, 16.5);
        VoxelShape east = box(-0.5, 6, 5, 3.5, 12, 11); // 旋转90°（顺时针）
        VoxelShape south = box(5, 6, -0.5, 11, 12, 3.5); // 旋转180°
        VoxelShape west = box(12.5, 6, 5, 16.5, 12, 11); // 旋转-90°（逆时针）
        return new WrenchableDirectionalBlock(properties, north, east, south, west);
    });

    // 大型双螺旋桨
    public static final RegistryObject<Block> LargeTwinPropeller = Register.register("large_twin_propeller",
            () -> {
                BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                        .sound(SoundType.NETHERITE_BLOCK)
                        .strength(1.5f)
                        .requiresCorrectToolForDrops()
                        .noOcclusion();
                VoxelShape north = box(5, 5, 12.5, 11, 11, 16.5);
                VoxelShape east  = box(-0.5, 5, 5, 3.5, 11, 11);
                VoxelShape south = box(5, 5, -0.5, 11, 11, 3.5);
                VoxelShape west  = box(12.5, 5, 5, 16.5, 11, 11);
                return new WrenchableDirectionalBlock(properties, north, east, south, west);
            });

    // 中型螺旋桨
    public static final RegistryObject<Block> MediumPropeller = Register.register("medium_propeller", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.NETHERITE_BLOCK)
                .strength(20.0f)
                .requiresCorrectToolForDrops()
                .noOcclusion();
        VoxelShape north = box(5, 5, 12.5, 11, 11, 16.5);
        VoxelShape east  = box(-0.5, 5, 5, 3.5, 11, 11);
        VoxelShape south = box(5, 5, -0.5, 11, 11, 3.5);
        VoxelShape west  = box(12.5, 5, 5, 16.5, 11, 11);
        return new WrenchableDirectionalBlock(properties, north, east, south, west);
    });

    // 小型螺旋桨
    public static final RegistryObject<Block> SmallPropeller = Register.register("small_propeller", () -> {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .sound(SoundType.NETHERITE_BLOCK)
                .strength(1.5f)
                .requiresCorrectToolForDrops()
                .noOcclusion();
        VoxelShape north = box(5, 5, 12.5, 11, 11, 16.5);
        VoxelShape east  = box(-0.5, 5, 5, 3.5, 11, 11);
        VoxelShape south = box(5, 5, -0.5, 11, 11, 3.5);
        VoxelShape west  = box(12.5, 5, 5, 16.5, 11, 11);
        return new WrenchableDirectionalBlock(properties, north, east, south, west);
    });

    public static void register(IEventBus eventBus) {
        Register.register(eventBus);
    }

}
