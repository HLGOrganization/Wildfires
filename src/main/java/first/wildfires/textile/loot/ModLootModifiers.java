package first.wildfires.textile.loot;

import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Textile 的全局战利品修饰器注册。
 *
 * <p>★ 命名空间必须是 <b>textile</b>（不是 wildfires），
 * 因为 {@code data/textile/loot_modifiers/*.json} 里的 {@code "type"} 写的是
 * {@code textile:add_item} / {@code textile:remove_item}。
 *
 * <p>2026-10-23 实测漏注册的后果（不崩，但静默失效）：
 * <pre>
 * Could not decode GlobalLootModifier with json id textile:crocodile_fur_add
 * Could not decode GlobalLootModifier with json id textile:lion_remove_medium_hide
 * ... 共 34 个
 * </pre>
 * 后果是打死狮子不掉狮子皮、也不移除 TFC 的中号生皮——战利品改动全部失效。
 */
public final class ModLootModifiers
{
    private ModLootModifiers()
    {
    }

    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIER_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, "textile");

    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> ADD_ITEM =
            LOOT_MODIFIER_SERIALIZERS.register("add_item", () -> AddItemModifier.CODEC);

    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> REMOVE_ITEM =
            LOOT_MODIFIER_SERIALIZERS.register("remove_item", () -> RemoveItemModifier.CODEC);

    public static void register(IEventBus eventBus)
    {
        LOOT_MODIFIER_SERIALIZERS.register(eventBus);
    }
}