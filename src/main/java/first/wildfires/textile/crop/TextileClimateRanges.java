package first.wildfires.textile.crop;

import net.dries007.tfc.util.climate.ClimateRange;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/**
 * 本模组作物的气候区间登记。
 *
 * <p>★ 为什么不能像原模组那样直接读 {@code ClimateRanges.CROPS}：
 * 那张表的 key 是 TFC 自己的 {@code Crop} 枚举（小麦/大麦…），棉花和亚麻不在其中。
 *
 * <p>★ 为什么不能硬编码数值：{@link ClimateRange} 的构造函数<b>全是 private</b>，
 * 只能由 TFC 的 {@code ClimateRange.MANAGER} 从数据包 JSON 反序列化出来。
 * 所以必须走 {@code MANAGER.register(id)} 拿一个 {@code Supplier}，JSON 放在：
 * <pre>data/wildfires/tfc/climate_ranges/crop/cotton.json
 * data/wildfires/tfc/climate_ranges/crop/flax.json</pre>
 * （搬迁时也从原模组的 {@code data/textile/tfc/climate_ranges/crop/*.json} 抄了过来，
 *   数值一致：棉花 min_hydration=45/max=100/min_temp=12/max_temp=38，
 *   亚麻 min_hydration=70/max=100/min_temp=19/max_temp=37。）
 *
 * <p>调用时机：在 {@code FMLCommonSetupEvent} 或更早的静态初始化里执行 {@link #init()}。
 * 必须在任何作物方块被真正使用之前完成，否则 {@link TextileCrop#getClimateRange()} 会抛异常。
 */
public final class TextileClimateRanges
{
    private TextileClimateRanges()
    {
    }

    private static volatile boolean initialized = false;

    /** 把两种作物的气候区间登记进 {@link TextileCrop#CLIMATE_RANGES}。可重复调用。 */
    public static synchronized void init()
    {
        if (initialized)
        {
            return;
        }
        for (TextileCrop crop : TextileCrop.values())
        {
            // ResourceLocation(String, String) 在 1.20.1 已弃用，用 fromNamespaceAndPath
            final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("textile", "crop/" + crop.getSerializedName());
            final Supplier<ClimateRange> supplier = ClimateRange.MANAGER.register(id);
            TextileCrop.CLIMATE_RANGES.put(crop, supplier);
        }
        initialized = true;
    }

    /** 给方块注册用的显式 Supplier（避免方块持有枚举里那个会抛异常的 getter）。 */
    public static Supplier<ClimateRange> of(TextileCrop crop)
    {
        init();
        return TextileCrop.CLIMATE_RANGES.get(crop);
    }

    /** 兼容：某些注册器要求 {@code RegistryObject} 风格的写法，这里返回普通 Supplier。 */
    public static Supplier<ClimateRange> supplier(TextileCrop crop)
    {
        return of(crop);
    }
}
