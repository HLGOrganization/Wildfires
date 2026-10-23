package first.wildfires.textile.crop;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/**
 * Textile 作物相关方块的三张注册表。
 *
 * <p>实际的 {@code Register.register(...)} 调用在 {@code register.BlockRegister} 里
 * （那里才有 {@code DeferredRegister<Block>}），这里只负责按作物种类存索引，
 * 方便 {@link TextileCrop} 反查自己的三个方块。
 *
 * <p>★ 6 个方块的 ID（沿用原模组，带斜杠）：
 * <pre>
 * textile:crop/cotton            textile:crop/flax
 * textile:dead_crop/cotton       textile:dead_crop/flax
 * textile:wild_crop/cotton       textile:wild_crop/flax
 * </pre>
 */
public final class TextileBlocks
{
    private TextileBlocks()
    {
    }

    public static final Map<TextileCrop, RegistryObject<Block>> CROPS = new EnumMap<>(TextileCrop.class);
    public static final Map<TextileCrop, RegistryObject<Block>> DEAD_CROPS = new EnumMap<>(TextileCrop.class);
    public static final Map<TextileCrop, RegistryObject<Block>> WILD_CROPS = new EnumMap<>(TextileCrop.class);

    public static RegistryObject<Block> getCrop(TextileCrop crop)
    {
        return CROPS.get(crop);
    }

    public static RegistryObject<Block> getDeadCrop(TextileCrop crop)
    {
        return DEAD_CROPS.get(crop);
    }

    public static RegistryObject<Block> getWildCrop(TextileCrop crop)
    {
        return WILD_CROPS.get(crop);
    }

    /**
     * 种子解析：种子物品由 KubeJS 注册，野火核心在方块注册阶段拿不到 RegistryObject，
     * 只能运行时按 ID 查。
     *
     * <p>★ ID 是 2026-10-22 进游戏实测的：<b>{@code textile:seeds/cotton}（带斜杠）</b>，
     * 不是 {@code textile:cotton_seeds}。用错的话作物根本种不下去。
     *
     * <p>查不到时返回 {@link Items#AIR} 而不是抛异常——方块注册早于物品注册，
     * 过早抛异常会让启动挂掉；真正取种子只在玩家右键/掉落时发生，那时物品已就绪。
     */
    public static final class SeedLookup
    {
        private SeedLookup()
        {
        }

        public static Item getSeed(TextileCrop crop)
        {
            final ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    "textile", "seeds/" + crop.getSerializedName());
            final Item found = ForgeRegistries.ITEMS.getValue(id);
            return found != null ? found : Items.AIR;
        }
    }
}