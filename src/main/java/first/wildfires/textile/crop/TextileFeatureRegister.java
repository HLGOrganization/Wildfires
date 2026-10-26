package first.wildfires.textile.crop;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Textile 的世界生成 feature 注册。
 *
 * <p>★ ID 必须是 {@code textile:tall_wild_crop}（不是 wildfires 命名空间），
 * 因为 kubejs/data/textile/worldgen/configured_feature/plant/wild_crop/*.json 里
 * 的 {@code "type"} 写的就是 {@code textile:tall_wild_crop}。
 *
 * <p>2026-10-23 实测：不注册这个 feature，进世界会失败：
 * <pre>
 * Caused by: java.lang.RuntimeException: Unknown registry key in
 *   ResourceKey[minecraft:root / minecraft:worldgen/feature]: textile:tall_wild_crop
 * Caused by: java.lang.IllegalStateException: Failed to load registries due to above errors
 * </pre>
 */
public final class TextileFeatureRegister
{
    private TextileFeatureRegister()
    {
    }

    public static final DeferredRegister<Feature<?>> Register =
            DeferredRegister.create(Registries.FEATURE, "textile");

    public static final RegistryObject<Feature<?>> TALL_WILD_CROP =
            Register.register("tall_wild_crop",
                    () -> new TextileTallWildCropFeature(TextileTallWildCropFeature.CODEC));

    public static void register(IEventBus eventBus)
    {
        Register.register(eventBus);
    }
}
