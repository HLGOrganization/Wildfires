package first.wildfires.register;

import com.github.alexthe666.citadel.server.item.CustomArmorMaterial;
import first.wildfires.Wildfires;
import first.wildfires.item.GeckoSimpleArmorItem;
import first.wildfires.item.HeatResistantArmorItem;
import first.wildfires.item.DrainedPulpScoopItem;
import first.wildfires.item.FilledPulpScoopItem;
import first.wildfires.item.PlanetariumItem;
import first.wildfires.item.PulpScoopItem;
import net.dries007.tfc.util.Metal;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ItemRegister {

    private static final DeferredRegister<Item> Register = DeferredRegister.create(Registries.ITEM, Wildfires.MODID);

    public static final RegistryObject<Item> DragonFruitItem =
            Register.register("dragon_fruit", () -> new Item(new Item.Properties()
                    .food(new FoodProperties.Builder()
                            .nutrition(3)
                            .saturationMod(0.7f)
                            .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200), 0.2f)
                            .build())
            ));
//时装



    /** A vanilla-style compass that is consumed gradually while carried. */
    public static final RegistryObject<CompassItem> SimpleCompass =
            Register.register("simple_compass", () -> new CompassItem(new Item.Properties().durability(60)));

    /** The result left when a simple compass has used all six durability points. */
    public static final RegistryObject<Item> DamagedCompass =
            Register.register("damaged_compass", () -> new Item(new Item.Properties()));

    /** Empty container returned when Create's super glue runs out of durability. */
    public static final RegistryObject<Item> EmptySuperGlue =
            Register.register("empty_super_glue", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> PulpScoop =
            Register.register("pulp_scoop", () -> new PulpScoopItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> FilledPulpScoop =
            Register.register("filled_pulp_scoop", () -> new FilledPulpScoopItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> DrainedPulpScoop =
            Register.register("drained_pulp_scoop", () -> new DrainedPulpScoopItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> Planetarium =
            Register.register("planetarium", () -> new PlanetariumItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final CustomArmorMaterial RainGearArmorMaterial = new CustomArmorMaterial(
            "minecraft:leather",
            60,
            new int[]{0, 0, 0, 0},
            0,
            SoundEvents.ARMOR_EQUIP_LEATHER,
            0.0F,
            0.0F
    );
    public static final CustomArmorMaterial BambooHatAndCoirRaincoatArmorMaterial = rainGearArmorMaterial("wildfires:bamboo_hat_and_coir_raincoat", 240);
    public static final CustomArmorMaterial RubberDivingSuitArmorMaterial = rainGearArmorMaterial("wildfires:rubber_diving_suit");
    public static final CustomArmorMaterial ForgingApronArmorMaterial = rainGearArmorMaterial("wildfires:forging_apron", 210);
    public static final ArmorMaterial ForgedIronGuardArmorMaterial = Metal.Default.WROUGHT_IRON.armorTier();
    public static final ArmorMaterial ChainGuardArmorMaterial = ArmorMaterials.CHAIN;

    public static final RegistryObject<GeckoSimpleArmorItem> ConicalHat = Register.register("conical_hat", () -> new GeckoSimpleArmorItem(
            BambooHatAndCoirRaincoatArmorMaterial, ArmorItem.Type.HELMET, new Item.Properties(), "bamboo_hat", "bamboo_hat_and_coir_raincoat", 3.5D));
    public static final RegistryObject<GeckoSimpleArmorItem> StrawRainCape = Register.register("straw_rain_cape", () -> new GeckoSimpleArmorItem(
            BambooHatAndCoirRaincoatArmorMaterial, ArmorItem.Type.CHESTPLATE, new Item.Properties(), "coir_raincoat", "bamboo_hat_and_coir_raincoat", 0D));
    public static final RegistryObject<ArmorItem> Raincoat = rainGear("raincoat", ArmorItem.Type.CHESTPLATE);
    public static final RegistryObject<ArmorItem> RubberDivingChestplate = rubberDivingGear("rubber_diving_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final RegistryObject<ArmorItem> RubberDivingLeggings = rubberDivingGear("rubber_diving_leggings", ArmorItem.Type.LEGGINGS);
    public static final RegistryObject<HeatResistantArmorItem> ForgingApron = Register.register("forging_apron", () -> new HeatResistantArmorItem(
            ForgingApronArmorMaterial, ArmorItem.Type.CHESTPLATE, new Item.Properties()));
    public static final RegistryObject<GeckoSimpleArmorItem> ForgedIronGuardClothHelmet =
            Register.register("wrought_iron_guard_cloth_covered_knight_helmet", () -> new GeckoSimpleArmorItem(
                    ForgedIronGuardArmorMaterial, ArmorItem.Type.HELMET, new Item.Properties(),
                    "wrought_iron_guard_cloth_helmet", "wrought_iron_guard", 0D));
    public static final RegistryObject<GeckoSimpleArmorItem> ForgedIronGuardHeavyArmor =
            Register.register("wrought_iron_guard_knight_heavy_helmet", () -> new GeckoSimpleArmorItem(
                    ForgedIronGuardArmorMaterial, ArmorItem.Type.CHESTPLATE, new Item.Properties(),
                    "wrought_iron_guard_expanded", "wrought_iron_guard", 0D));
    public static final RegistryObject<GeckoSimpleArmorItem> ForgedIronGuardKneeGuards =
            Register.register("wrought_iron_guard_knight_knee_guard", () -> new GeckoSimpleArmorItem(
                    ForgedIronGuardArmorMaterial, ArmorItem.Type.LEGGINGS, new Item.Properties(),
                    "wrought_iron_guard_expanded", "wrought_iron_guard", 0D));
    public static final RegistryObject<GeckoSimpleArmorItem> ForgedIronGuardFootArmor =
            Register.register("wrought_iron_guard_knight_foot_armor", () -> new GeckoSimpleArmorItem(
                    ForgedIronGuardArmorMaterial, ArmorItem.Type.BOOTS, new Item.Properties(),
                    "wrought_iron_guard_expanded", "wrought_iron_guard", 0D));
    public static final RegistryObject<GeckoSimpleArmorItem> ForgedIronGuardSquareHelmet =
            Register.register("wrought_iron_guard_square_helmet", () -> new GeckoSimpleArmorItem(
                    ForgedIronGuardArmorMaterial, ArmorItem.Type.HELMET, new Item.Properties(),
                    "wrought_iron_guard_square_helmet", "wrought_iron_guard_square_helmet", 0D));
    public static final RegistryObject<GeckoSimpleArmorItem> ChainGuardHelmet =
            Register.register("chain_guard_helmet", () -> new GeckoSimpleArmorItem(
                    ChainGuardArmorMaterial, ArmorItem.Type.HELMET, new Item.Properties(),
                    "wrought_iron_guard_chain_helmet", "chain_guard", 0D));
    public static final RegistryObject<GeckoSimpleArmorItem> ChainGuardChestplate =
            Register.register("chain_guard_chestplate", () -> new GeckoSimpleArmorItem(
                    ChainGuardArmorMaterial, ArmorItem.Type.CHESTPLATE, new Item.Properties(),
                    "wrought_iron_guard_expanded", "chain_guard", 0D));
    public static final RegistryObject<GeckoSimpleArmorItem> ChainGuardLeggings =
            Register.register("chain_guard_leggings", () -> new GeckoSimpleArmorItem(
                    ChainGuardArmorMaterial, ArmorItem.Type.LEGGINGS, new Item.Properties(),
                    "wrought_iron_guard_expanded", "chain_guard", 0D));
    public static final RegistryObject<GeckoSimpleArmorItem> ChainGuardBoots =
            Register.register("chain_guard_boots", () -> new GeckoSimpleArmorItem(
                    ChainGuardArmorMaterial, ArmorItem.Type.BOOTS, new Item.Properties(),
                    "wrought_iron_guard_expanded", "chain_guard", 0D));
    public static final RegistryObject<GeckoSimpleArmorItem> BucketGuardHelmet =
            Register.register("wrought_iron_guard_bucket_helmet", () -> new GeckoSimpleArmorItem(
                    ForgedIronGuardArmorMaterial, ArmorItem.Type.HELMET, new Item.Properties(),
                    "wrought_iron_guard_bucket_helmet", "wrought_iron_guard_plain", 0D));
    public static final RegistryObject<GeckoSimpleArmorItem> BucketGuardChestplate =
            Register.register("wrought_iron_guard_bucket_chestplate", () -> new GeckoSimpleArmorItem(
                    ForgedIronGuardArmorMaterial, ArmorItem.Type.CHESTPLATE, new Item.Properties(),
                    "wrought_iron_guard_expanded", "wrought_iron_guard_plain", 0D));
    public static final RegistryObject<GeckoSimpleArmorItem> BucketGuardLeggings =
            Register.register("wrought_iron_guard_bucket_leggings", () -> new GeckoSimpleArmorItem(
                    ForgedIronGuardArmorMaterial, ArmorItem.Type.LEGGINGS, new Item.Properties(),
                    "wrought_iron_guard_expanded", "wrought_iron_guard_plain", 0D));
    public static final RegistryObject<GeckoSimpleArmorItem> BucketGuardBoots =
            Register.register("wrought_iron_guard_bucket_boots", () -> new GeckoSimpleArmorItem(
                    ForgedIronGuardArmorMaterial, ArmorItem.Type.BOOTS, new Item.Properties(),
                    "wrought_iron_guard_expanded", "wrought_iron_guard_plain", 0D));
    public static final RegistryObject<GeckoSimpleArmorItem> SquareGuardChestplate =
            Register.register("wrought_iron_guard_square_chestplate", () -> new GeckoSimpleArmorItem(
                    ForgedIronGuardArmorMaterial, ArmorItem.Type.CHESTPLATE, new Item.Properties(),
                    "wrought_iron_guard_expanded", "wrought_iron_guard_square_armor", 0D));
    public static final RegistryObject<GeckoSimpleArmorItem> SquareGuardLeggings =
            Register.register("wrought_iron_guard_square_leggings", () -> new GeckoSimpleArmorItem(
                    ForgedIronGuardArmorMaterial, ArmorItem.Type.LEGGINGS, new Item.Properties(),
                    "wrought_iron_guard_expanded", "wrought_iron_guard_square_armor", 0D));
    public static final RegistryObject<GeckoSimpleArmorItem> SquareGuardBoots =
            Register.register("wrought_iron_guard_square_boots", () -> new GeckoSimpleArmorItem(
                    ForgedIronGuardArmorMaterial, ArmorItem.Type.BOOTS, new Item.Properties(),
                    "wrought_iron_guard_expanded", "wrought_iron_guard_square_armor", 0D));

    private static RegistryObject<ArmorItem> rainGear(String name, ArmorItem.Type type) {
        return Register.register(name, () -> new ArmorItem(RainGearArmorMaterial, type, new Item.Properties()));
    }

    private static RegistryObject<ArmorItem> rubberDivingGear(String name, ArmorItem.Type type) {
        return Register.register(name, () -> new ArmorItem(RubberDivingSuitArmorMaterial, type, new Item.Properties()));
    }

    private static CustomArmorMaterial rainGearArmorMaterial(String name) {
        return rainGearArmorMaterial(name, 60);
    }

    private static CustomArmorMaterial rainGearArmorMaterial(String name, int durability) {
        return new CustomArmorMaterial(
                name,
                durability,
                new int[]{0, 0, 0, 0},
                0,
                SoundEvents.ARMOR_EQUIP_LEATHER,
                0.0F,
                0.0F
        );
    }


    public static final RegistryObject<Item> GrassSlab = Register.register("grass_slab", () -> new BlockItem(BlockRegister.GrassSlab.get(), new Item.Properties()));

    public static final RegistryObject<Item> WildBrownMushroom = Register.register(
            "wild_brown_mushroom", () -> new BlockItem(BlockRegister.WildBrownMushroom.get(), new Item.Properties()));
    public static final RegistryObject<Item> WildRedMushroom = Register.register(
            "wild_red_mushroom", () -> new BlockItem(BlockRegister.WildRedMushroom.get(), new Item.Properties()));
    public static final RegistryObject<Item> WildFluorescystShroom = Register.register(
            "wild_fluorescyst_shroom", () -> new BlockItem(BlockRegister.WildFluorescystShroom.get(), new Item.Properties()));

    public static final RegistryObject<Item> UnrestrictedCharcoalForge =
            Register.register("unrestricted_charcoal_forge", () -> new BlockItem(BlockRegister.UnrestrictedCharcoalForge.get(), new Item.Properties()));

    public static final RegistryObject<Item> Crucible =
            Register.register("crucible", () -> new BlockItem(BlockRegister.Crucible.get(), new Item.Properties()));

    public static final RegistryObject<Item> DecorativeCrucible =
            Register.register("decorative_crucible", () -> new BlockItem(BlockRegister.DecorativeCrucible.get(), new Item.Properties()));

    public static final RegistryObject<Item> UnderwaterTurbine =
            Register.register("underwater_turbine", () -> new BlockItem(BlockRegister.UnderwaterTurbine.get(), new Item.Properties()));

    public static final RegistryObject<Item> SubmarineCore =
            Register.register("submarine_core", () -> new BlockItem(BlockRegister.SubmarineCore.get(), new Item.Properties()));

    public static final RegistryObject<Item> SimpleAirCushion =
            Register.register("simple_air_cushion", () -> new BlockItem(BlockRegister.SimpleAirCushion.get(), new Item.Properties()));

    public static final RegistryObject<Item> AirCushion =
            Register.register("air_cushion", () -> new BlockItem(BlockRegister.AirCushion.get(), new Item.Properties()));

    public static final RegistryObject<Item> DoubleWing =
            Register.register("double_wing", () -> new BlockItem(BlockRegister.DoubleWing.get(), new Item.Properties()));

    public static final RegistryObject<Item> AirshipSlats =
            Register.register("airship_slats", () -> new BlockItem(BlockRegister.AirshipSlats.get(), new Item.Properties()));

    public static final RegistryObject<Item> GyrodynePropeller =
            Register.register("gyrodyne_propeller", () -> new BlockItem(BlockRegister.GyrodynePropeller.get(), new Item.Properties()));

    public static final RegistryObject<Item> BiplaneEngine =
            Register.register("biplane_engine", () -> new BlockItem(BlockRegister.BiplaneEngine.get(), new Item.Properties()));

    public static final RegistryObject<Item> LargeAirshipEngine =
            Register.register("large_airship_engine", () -> new BlockItem(BlockRegister.LargeAirshipEngine.get(), new Item.Properties()));

    public static final RegistryObject<Item> SmallEngine =
            Register.register("small_engine", () -> new BlockItem(BlockRegister.SmallEngine.get(), new Item.Properties()));

    public static final RegistryObject<Item> SmallSideEngine =
            Register.register("small_side_engine", () -> new BlockItem(BlockRegister.SmallSideEngine.get(), new Item.Properties()));

    public static final RegistryObject<Item> RuggedSmallEngine =
            Register.register("rugged_small_engine", () -> new BlockItem(BlockRegister.RuggedSmallEngine.get(), new Item.Properties()));

    public static final RegistryObject<Item> LargePropeller =
            Register.register("large_propeller", () -> new BlockItem(BlockRegister.LargePropeller.get(), new Item.Properties()));

    public static final RegistryObject<Item> LargeTwinPropeller =
            Register.register("large_twin_propeller", () -> new BlockItem(BlockRegister.LargeTwinPropeller.get(), new Item.Properties()));

    public static final RegistryObject<Item> MediumPropeller =
            Register.register("medium_propeller", () -> new BlockItem(BlockRegister.MediumPropeller.get(), new Item.Properties()));

    public static final RegistryObject<Item> SmallPropeller =
            Register.register("small_propeller", () -> new BlockItem(BlockRegister.SmallPropeller.get(), new Item.Properties()));

    public static final CustomArmorMaterial CosmeticArmorTunmou = new CustomArmorMaterial(
            "wildfires:tunmou_armor",//护甲材料id
            -1, //耐久
            new int[]{0, 0, 0, 0}, //护甲值, 头盔, 胸甲, 腿甲, 靴子
            0, //附魔值
            SoundEvents.ARMOR_EQUIP_GENERIC, //装备音效
            0.0F, // 韧性
            0.0F // 击退抗性
    );
    public static final CustomArmorMaterial CosmeticArmoryuyu = new CustomArmorMaterial(
            "wildfires:yuyu_armor",//护甲材料id
            -1, //耐久
            new int[]{0, 0, 0, 0}, //护甲值, 头盔, 胸甲, 腿甲, 靴子
            0, //附魔值
            SoundEvents.ARMOR_EQUIP_GENERIC, //装备音效
            0.0F, // 韧性
            0.0F // 击退抗性
    );
    public static final CustomArmorMaterial CosmeticArmorbeizi = new CustomArmorMaterial(
            "wildfires:beizi_armor",//护甲材料id
            -1, //耐久
            new int[]{0, 0, 0, 0}, //护甲值, 头盔, 胸甲, 腿甲, 靴子
            0, //附魔值
            SoundEvents.ARMOR_EQUIP_GENERIC, //装备音效
            0.0F, // 韧性
            0.0F // 击退抗性
    );
    public static final CustomArmorMaterial CosmeticArmorhuacao = new CustomArmorMaterial(
            "wildfires:huacao_armor",//护甲材料id
            -1, //耐久
            new int[]{0, 0, 0, 0}, //护甲值, 头盔, 胸甲, 腿甲, 靴子
            0, //附魔值
            SoundEvents.ARMOR_EQUIP_GENERIC, //装备音效
            0.0F, // 韧性
            0.0F // 击退抗性
    );

    public static final CustomArmorMaterial CosmeticArmormushroom = new CustomArmorMaterial(
            "wildfires:mushroom_armor",//护甲材料id
            -1, //耐久
            new int[]{0, 0, 0, 0}, //护甲值, 头盔, 胸甲, 腿甲, 靴子
            0, //附魔值
            SoundEvents.ARMOR_EQUIP_GENERIC, //装备音效
            0.0F, // 韧性
            0.0F // 击退抗性

    );
    public static final CustomArmorMaterial CosmeticArmorxk = new CustomArmorMaterial(
            "wildfires:xk_armor",//护甲材料id
            -1, //耐久
            new int[]{0, 0, 0, 0}, //护甲值, 头盔, 胸甲, 腿甲, 靴子
            0, //附魔值
            SoundEvents.ARMOR_EQUIP_GENERIC, //装备音效
            0.0F, // 韧性
            0.0F // 击退抗性

    );


    public static final RegistryObject<GeckoSimpleArmorItem> CosmeticmushroomHelmet =
            Register.register("decoration/mushroom_helmet", () -> new GeckoSimpleArmorItem(
                    CosmeticArmormushroom,//护甲材料
                    ArmorItem.Type.HELMET,//头盔
                    new Item.Properties()//属性
            ));
    public static final RegistryObject<GeckoSimpleArmorItem> CosmeticmushroomChestplat =
            Register.register("decoration/mushroom_armor_chestplate", () -> new GeckoSimpleArmorItem(
                    CosmeticArmormushroom,//护甲材料
                    ArmorItem.Type.CHESTPLATE,//头盔
                    new Item.Properties()//属性
            ));




    public static final RegistryObject<GeckoSimpleArmorItem> CosmetichuacaoHelmet =
            Register.register("decoration/huacao_helmet", () -> new GeckoSimpleArmorItem(
                    CosmeticArmorhuacao,//护甲材料
                    ArmorItem.Type.HELMET,//头盔
                    new Item.Properties()//属性
            ));
    public static final RegistryObject<GeckoSimpleArmorItem> CosmeticyuyuHelmet =
            Register.register("decoration/yuyu_helmet", () -> new GeckoSimpleArmorItem(
                    CosmeticArmoryuyu,//护甲材料
                    ArmorItem.Type.HELMET,//头盔
                    new Item.Properties()//属性
            ));
    public static final RegistryObject<GeckoSimpleArmorItem> CosmeticbeiziHelmet =
            Register.register("decoration/beizi_helmet", () -> new GeckoSimpleArmorItem(
                    CosmeticArmorbeizi,//护甲材料
                    ArmorItem.Type.HELMET,//头盔
                    new Item.Properties()//属性
            ));
    public static final RegistryObject<GeckoSimpleArmorItem> CosmeticbeiziChestplat =
            Register.register("decoration/beizi_armor_chestplate", () -> new GeckoSimpleArmorItem(
                    CosmeticArmorbeizi,//护甲材料
                    ArmorItem.Type.CHESTPLATE,//头盔
                    new Item.Properties()//属性
            ));

    public static final RegistryObject<GeckoSimpleArmorItem> CosmeticTunmouHelmet =
            Register.register("decoration/tunmou_armor_helmet", () -> new GeckoSimpleArmorItem(
                    CosmeticArmorTunmou,//护甲材料
                    ArmorItem.Type.HELMET,//头盔
                    new Item.Properties()//属性
            ));

    public static final RegistryObject<GeckoSimpleArmorItem> CosmeticTunmouChestplat =
            Register.register("decoration/tunmou_armor_chestplate", () -> new GeckoSimpleArmorItem(
                    CosmeticArmorTunmou,//护甲材料
                    ArmorItem.Type.CHESTPLATE,//头盔
                    new Item.Properties()//属性
            ));
    public static final RegistryObject<GeckoSimpleArmorItem> CosmeticTunmouLeggings =
            Register.register("decoration/tunmou_armor_leggings", () -> new GeckoSimpleArmorItem(
                    CosmeticArmorTunmou,//护甲材料
                    ArmorItem.Type.LEGGINGS,//头盔
                    new Item.Properties()//属性
            ));

    public static final RegistryObject<GeckoSimpleArmorItem> CosmeticTunmouBoots =
            Register.register("decoration/tunmou_armor_boots", () -> new GeckoSimpleArmorItem(
                    CosmeticArmorTunmou,//护甲材料
                    ArmorItem.Type.BOOTS,//头盔
                    new Item.Properties()//属性
            ));


    public static final RegistryObject<GeckoSimpleArmorItem> CosmeticxkHelmet =
            Register.register("decoration/xk_helmet", () -> new GeckoSimpleArmorItem(
                    CosmeticArmorxk,//护甲材料
                    ArmorItem.Type.HELMET,//头盔
                    new Item.Properties()//属性
            ));


    public static void register(IEventBus eventBus) {
        Register.register(eventBus);
    }

}
