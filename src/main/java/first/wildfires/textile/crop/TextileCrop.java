package first.wildfires.textile.crop;

import net.dries007.tfc.common.blockentities.FarmlandBlockEntity.NutrientType;
import net.dries007.tfc.common.blocks.ExtendedProperties;
import net.dries007.tfc.util.climate.ClimateRange;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Textile 的两种作物（棉花 / 亚麻）。
 *
 * <p>从 textile-2.2.3 的 {@code TextileCrop} 枚举<b>逐字段</b>移植。
 *
 * <p>★ 数值全部来自 {@code javap -c} 读的 static 初始化块（2026-10-23），不是猜的：
 * <pre>
 * COTTON = new TextileCrop("COTTON", 0, NutrientType.NITROGEN, 2, 4)
 * FLAX   = new TextileCrop("FLAX",   1, NutrientType.NITROGEN, 2, 3)
 *                                   签名: (name, ordinal, nutrient, maxSingleAge, maxAge)
 * </pre>
 * ⇒ 两种作物的主养分<b>都是 NITROGEN</b>（昨天误写成 COTTON=PHOSPHOROUS / FLAX=POTASSIUM）。
 *
 * <p>★ 两个 age 的含义（双高作物）：
 * <ul>
 *   <li>{@code maxSingleAge} —— 下半格单独能长到的最大 age（2）</li>
 *   <li>{@code maxAge} —— 上下两格加起来的总 age 上限（棉花 4 / 亚麻 3）</li>
 *   <li>方块 age 属性区间 = {@code 0 .. maxSingleAge + maxAge - 1}</li>
 *   <li>构造里还派生 {@code maxSingleGrowth = maxSingleAge / maxAge}</li>
 * </ul>
 */
public enum TextileCrop implements StringRepresentable
{
    COTTON(NutrientType.NITROGEN, 2, 4),
    FLAX(NutrientType.NITROGEN, 2, 3);

    private static final TextileCrop[] VALUES = values();

    /** 本模组作物自己的气候区间表，由 {@link TextileClimateRanges} 填充。 */
    public static final Map<TextileCrop, Supplier<ClimateRange>> CLIMATE_RANGES = new ConcurrentHashMap<>();

    private final NutrientType primaryNutrient;
    private final int maxSingleAge;
    private final int maxAge;
    private final String serializedName;

    TextileCrop(NutrientType primaryNutrient, int maxSingleAge, int maxAge)
    {
        this.primaryNutrient = primaryNutrient;
        this.maxSingleAge = maxSingleAge;
        this.maxAge = maxAge;
        this.serializedName = name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName()
    {
        return serializedName;
    }

    /** 下半格单独能长到的最大 age。 */
    public int getMaxSingleAge()
    {
        return maxSingleAge;
    }

    /** 上下两格合计的 age 上限。 */
    public int getMaxAge()
    {
        return maxAge;
    }

    /** 下半格占总生长的比例 = maxSingleAge / maxAge（对应原模组 maxSingleGrowth）。 */
    public float getMaxSingleGrowth()
    {
        return (float) maxSingleAge / (float) maxAge;
    }

    /** 方块 age 属性的上界 = maxSingleAge + maxAge - 1。 */
    public int getBlockAgeMax()
    {
        return maxSingleAge + maxAge - 1;
    }

    public NutrientType getPrimaryNutrient()
    {
        return primaryNutrient;
    }

    public Supplier<ClimateRange> getClimateRange()
    {
        final Supplier<ClimateRange> supplier = CLIMATE_RANGES.get(this);
        if (supplier == null)
        {
            throw new IllegalStateException("Climate range for textile crop " + serializedName
                    + " is not registered. Did TextileClimateRanges.init() run?");
        }
        return supplier;
    }

    public Block create()
    {
        return TextileBlocks.getCrop(this).get();
    }

    public Block createDead()
    {
        return TextileBlocks.getDeadCrop(this).get();
    }

    public Block createWild()
    {
        return TextileBlocks.getWildCrop(this).get();
    }

    public static TextileCrop[] allValues()
    {
        return VALUES;
    }

    /** 基础属性模板（原模组 {@code dead()}，所有方块都从它派生）。 */
    public static ExtendedProperties dead()
    {
        return ExtendedProperties.of(net.minecraft.world.level.material.MapColor.PLANT)
                .noCollission()
                .randomTicks()
                .strength(0.4f)
                .sound(net.minecraft.world.level.block.SoundType.CROP)
                .flammable(60, 30);
    }

    /**
     * 双高作物方块的属性模板（原模组 {@code doubleCrop()}）。
     *
     * <p>字节码实测：{@code dead().blockEntity(TextileBlockEntities.CROP)
     * .serverTicks(TextileCropBlockEntity::serverTickBottomPartOnly)}
     * —— 双高作物<b>只让下半格</b>跑 tick。
     */
    public static ExtendedProperties doubleCrop()
    {
        return dead()
                .blockEntity(() -> TextileBlockEntities.CROP.get())
                .serverTicks(TextileCropBlockEntity::serverTickBottomPartOnly);
    }

    /** 单格作物用的属性模板（原模组 {@code crop()}，tick 用普通 serverTick）。 */
    public static ExtendedProperties crop()
    {
        return dead()
                .blockEntity(() -> TextileBlockEntities.CROP.get())
                .serverTicks(TextileCropBlockEntity::serverTick);
    }

    /** 野生方块属性模板（原模组 {@code wild()}，不挂方块实体）。 */
    public static ExtendedProperties wild()
    {
        return dead();
    }
}
