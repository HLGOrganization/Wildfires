package first.wildfires.textile.loot;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.providers.number.LootNumberProviderType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Textile 的战利品数据注册。
 *
 * <p>★ 命名空间必须是 <b>textile</b>：
 * {@code data/textile/loot_tables/blocks/crop/*.json} 里的 count type 写的是
 * {@code textile:crop_yield_uniform}。
 *
 * <p>原模组 {@code TextileLoot} 还声明了 CONDITIONS / LOOT_FUNCTIONS 两个 DeferredRegister，
 * 但字节码里只实际注册了 {@code crop_yield_uniform} 一个，所以这里只搬这个。
 */
public final class TextileLoot
{
    private TextileLoot()
    {
    }

    public static final DeferredRegister<LootNumberProviderType> NUMBER_PROVIDERS =
            DeferredRegister.create(Registries.LOOT_NUMBER_PROVIDER_TYPE, "textile");

    public static final RegistryObject<LootNumberProviderType> CROP_YIELD =
            NUMBER_PROVIDERS.register("crop_yield_uniform",
                    () -> new LootNumberProviderType(TextileCropYieldProvider.SERIALIZER));

    public static void register(IEventBus eventBus)
    {
        NUMBER_PROVIDERS.register(eventBus);
    }
}