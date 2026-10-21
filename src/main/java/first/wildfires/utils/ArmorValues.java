package first.wildfires.utils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 护甲部位平均化（老大 2026-10-18 定）。
 *
 * <p>问题：TFC 原版一件胸甲就拿走大半护甲值，身上部件差距太大。
 *
 * <p>规则：给出每类的<b>均值（基准值）</b>，四件都接近这个均值；单件允许 ±0.5 的微调，
 * 且<b>四件平均必须等于基准值</b>（所以是"胸甲 +0.25/+0.5、靴子 −0.25/−0.5、头腿持平"的配法）。
 *
 * <h2>金属甲</h2>
 * <table>
 *   <caption>数值（头 / 胸 / 腿 / 靴）</caption>
 *   <tr><td>铜</td><td>3.0</td><td>3.5</td><td>3.0</td><td>2.5</td><td>均值 3.0</td></tr>
 *   <tr><td>铜合金（铋铜/黑铜/青铜）</td><td>3.5</td><td>4.0</td><td>3.5</td><td>3.0</td><td>均值 3.5</td></tr>
 *   <tr><td>锻铁 / 钢</td><td>4.0</td><td>4.5</td><td>4.0</td><td>3.5</td><td>均值 4.0</td></tr>
 *   <tr><td>高锰钢（black_steel）</td><td>4.5</td><td>5.0</td><td>4.5</td><td>4.0</td><td>均值 4.5</td></tr>
 *   <tr><td>秘银（blue_steel）/ 精金（red_steel）</td><td>5.0</td><td>5.5</td><td>5.0</td><td>4.5</td><td>均值 5.0</td></tr>
 * </table>
 *
 * <h2>皮甲</h2>
 * 均值 <b>1.0</b>（头 1.0 / 胸 1.25 / 腿 1.0 / 靴 0.75）。
 * ★ <b>死名单</b>（老大 2026-10-18：「就按照死名单就行，后面添加新装备会自己填的」）：
 * textile 的全部保暖衣物 + 原版皮革套 + Firmalife 养蜂人套；锁链甲与茅草不在内。
 * 新装备直接往下面四个数组里加 id 即可。
 */
public final class ArmorValues {

    /** 每行：金属 id、头、胸、腿、靴 */
    private static final String[][] METAL_TABLE = {
            {"copper", "3.0", "3.5", "3.0", "2.5"},
            {"bronze", "3.5", "4.0", "3.5", "3.0"},
            {"bismuth_bronze", "3.5", "4.0", "3.5", "3.0"},
            {"black_bronze", "3.5", "4.0", "3.5", "3.0"},
            {"wrought_iron", "4.0", "4.5", "4.0", "3.5"},
            {"steel", "4.0", "4.5", "4.0", "3.5"},
            {"black_steel", "4.5", "5.0", "4.5", "4.0"},
            {"blue_steel", "5.0", "5.5", "5.0", "4.5"},
            {"red_steel", "5.0", "5.5", "5.0", "4.5"},
    };

    /** tfc:metal/<部件>/<金属> 里的部件名，顺序与 METAL_TABLE 的列一一对应 */
    private static final String[] METAL_PARTS = {"helmet", "chestplate", "greaves", "boots"};

    /** 皮甲四件（头 / 胸 / 腿 / 靴），均值 1.0 */
    private static final float LEATHER_HEAD = 1.0F;
    private static final float LEATHER_CHEST = 1.25F;
    private static final float LEATHER_LEGS = 1.0F;
    private static final float LEATHER_FEET = 0.75F;

    // ---- 皮甲死名单（新装备直接往这里加）----
    private static final String[] LEATHER_HELMETS = {
            "textile:black_bear_hat",
            "textile:burlap_hat",
            "textile:caribou_hat",
            "textile:cotton_hat",
            "textile:cougar_hat",
            "textile:crocodile_hat",
            "textile:direwolf_hat",
            "textile:grizzly_bear_hat",
            "textile:linen_hat",
            "textile:lion_hat",
            "textile:panther_hat",
            "textile:polar_bear_hat",
            "textile:raw_hat",
            "textile:sabertooth_hat",
            "textile:silk_hat",
            "textile:tiger_hat",
            "textile:wool_hat",
            "minecraft:leather_helmet",
            "firmalife:beekeeper_helmet"
    };

    private static final String[] LEATHER_CHESTPLATES = {
            "textile:black_bear_shirt",
            "textile:burlap_shirt",
            "textile:caribou_shirt",
            "textile:cotton_shirt",
            "textile:cougar_shirt",
            "textile:crocodile_shirt",
            "textile:direwolf_shirt",
            "textile:grizzly_bear_shirt",
            "textile:linen_shirt",
            "textile:lion_shirt",
            "textile:panther_shirt",
            "textile:polar_bear_shirt",
            "textile:raw_shirt",
            "textile:sabertooth_shirt",
            "textile:silk_shirt",
            "textile:tiger_shirt",
            "textile:wool_shirt",
            "minecraft:leather_chestplate",
            "firmalife:beekeeper_chestplate"
    };

    private static final String[] LEATHER_LEGGINGS = {
            "textile:black_bear_pants",
            "textile:burlap_pants",
            "textile:caribou_pants",
            "textile:cotton_pants",
            "textile:cougar_pants",
            "textile:crocodile_pants",
            "textile:direwolf_pants",
            "textile:grizzly_bear_pants",
            "textile:linen_pants",
            "textile:lion_pants",
            "textile:panther_pants",
            "textile:polar_bear_pants",
            "textile:raw_pants",
            "textile:sabertooth_pants",
            "textile:silk_pants",
            "textile:tiger_pants",
            "textile:wool_pants",
            "minecraft:leather_leggings",
            "firmalife:beekeeper_leggings"
    };

    private static final String[] LEATHER_BOOTS = {
            "textile:black_bear_boots",
            "textile:caribou_boots",
            "textile:cougar_boots",
            "textile:crocodile_boots",
            "textile:direwolf_boots",
            "textile:grizzly_bear_boots",
            "textile:lion_boots",
            "textile:panther_boots",
            "textile:polar_bear_boots",
            "textile:sabertooth_boots",
            "textile:tiger_boots",
            "minecraft:leather_boots",
            "firmalife:beekeeper_boots"
    };

    private ArmorValues() {
    }

    /**
     * @return 该件的护甲值；<b>不在表里返回 -1</b>（调用方回退到物品自身的属性修正）
     */
    public static float armorOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return -1.0F;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return -1.0F;
        }
        return armorOfId(id);
    }

    private static float armorOfId(ResourceLocation id) {
        String key = id.getNamespace() + ":" + id.getPath();

        // ---- 皮甲死名单 ----
        if (contains(LEATHER_HELMETS, key)) {
            return LEATHER_HEAD;
        }
        if (contains(LEATHER_CHESTPLATES, key)) {
            return LEATHER_CHEST;
        }
        if (contains(LEATHER_LEGGINGS, key)) {
            return LEATHER_LEGS;
        }
        if (contains(LEATHER_BOOTS, key)) {
            return LEATHER_FEET;
        }

        // ---- 金属甲：tfc:metal/<部件>/<金属> ----
        if ("tfc".equals(id.getNamespace())) {
            String[] pieces = id.getPath().split("/");
            if (pieces.length == 3 && "metal".equals(pieces[0])) {
                int partIndex = indexOf(METAL_PARTS, pieces[1]);
                if (partIndex < 0) {
                    return -1.0F;
                }
                for (String[] row : METAL_TABLE) {
                    if (row[0].equals(pieces[2])) {
                        return Float.parseFloat(row[partIndex + 1]);
                    }
                }
            }
        }
        return -1.0F;
    }

    /** 按物品本体查（mixin 里拿不到 ItemStack 时用） */
    public static float armorOf(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) {
            return -1.0F;
        }
        return armorOfId(id);
    }


    /**
     * 这件盔甲自己的装备槽（从注册名推，不依赖任何未验证的 API）。
     *
     * @return 自己的槽位；不是我们认得的护甲则返回 null
     */
    public static EquipmentSlot slotOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return null;
        }
        String path = id.getPath();
        // tfc:metal/<部件>/<金属>
        if ("tfc".equals(id.getNamespace()) && path.startsWith("metal/")) {
            String[] pieces = path.split("/");
            if (pieces.length == 3) {
                return slotForName(pieces[1]);
            }
        }
        return slotForName(path);
    }


    /** 按物品本体查它自己的装备槽（mixin 里拿不到 ItemStack 时用） */
    public static EquipmentSlot slotOf(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) {
            return null;
        }
        if ("tfc".equals(id.getNamespace()) && id.getPath().startsWith("metal/")) {
            String[] pieces = id.getPath().split("/");
            if (pieces.length == 3) {
                return slotForName(pieces[1]);
            }
        }
        return slotForName(id.getPath());
    }

    /** 按名字判断部位：头 / 胸 / 腿 / 靴（顺序与 LEATHER_HELMETS 等四个数组一致） */
    private static final String[][] LEATHER_PART_NAMES = {
            {"helmet", "hat", "hood", "cap"},
            {"chestplate", "shirt", "coat", "jacket", "robe"},
            {"leggings", "greaves", "pants", "trousers"},
            {"boots", "shoes", "sandals"},
    };

    private static EquipmentSlot slotForName(String name) {
        for (String part : LEATHER_PART_NAMES[0]) {
            if (name.contains(part)) {
                return EquipmentSlot.HEAD;
            }
        }
        for (String part : LEATHER_PART_NAMES[1]) {
            if (name.contains(part)) {
                return EquipmentSlot.CHEST;
            }
        }
        for (String part : LEATHER_PART_NAMES[2]) {
            if (name.contains(part)) {
                return EquipmentSlot.LEGS;
            }
        }
        for (String part : LEATHER_PART_NAMES[3]) {
            if (name.contains(part)) {
                return EquipmentSlot.FEET;
            }
        }
        return null;
    }

    private static boolean contains(String[] array, String value) {
        for (String candidate : array) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static int indexOf(String[] array, String value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(value)) {
                return i;
            }
        }
        return -1;
    }
}
