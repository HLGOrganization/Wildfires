package first.wildfires.textile.crop;

import first.wildfires.Wildfires;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Textile 作物的方块实体注册。
 *
 * <p>★ 2026-10-23 实测崩溃与修法：
 * <pre>
 * java.lang.NullPointerException: Registry Object not present: textile:crop/cotton
 *     at TextileBlockEntities.lambda$static$0(TextileBlockEntities.java:28)
 * </pre>
 * 原因：方块注册在 <b>另一个</b> DeferredRegister（{@code BlockRegister.TextileRegister}）里，
 * 而两个 DeferredRegister 的 {@code RegisterEvent} 监听器触发顺序不可控。
 * 方块实体的 lambda 执行时，方块可能还没绑上 RegistryObject，{@code .get()} 就抛了。
 *
 * <p>修法：<b>不在</b> lambda 里调 {@code .get()}，改成等
 * {@code BlockEntityType} 真正被创建时（那时所有方块一定已注册）再按 ID 查方块。
 * 用 {@link ForgeRegistries#BLOCKS#getValue} 直接查注册表，绕开 RegistryObject 的绑定状态。
 */
public final class TextileBlockEntities
{
    private TextileBlockEntities()
    {
    }

    public static final DeferredRegister<BlockEntityType<?>> Register =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Wildfires.MODID);

    public static final RegistryObject<BlockEntityType<TextileCropBlockEntity>> CROP =
            Register.register("textile_crop", () -> {
                // 直接查注册表，不用 RegistryObject.get()（见类注释里的崩溃原因）
                final Block cotton = ForgeRegistries.BLOCKS.getValue(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("textile", "crop/cotton"));
                final Block flax = ForgeRegistries.BLOCKS.getValue(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("textile", "crop/flax"));
                if (cotton == null || flax == null)
                {
                    throw new IllegalStateException(
                            "Textile crop blocks are not registered yet (cotton=" + cotton + ", flax=" + flax + ")");
                }
                return BlockEntityType.Builder
                        .of(TextileCropBlockEntity::new, cotton, flax)
                        .build(null);
            });

    public static void register(IEventBus eventBus)
    {
        Register.register(eventBus);
    }
}