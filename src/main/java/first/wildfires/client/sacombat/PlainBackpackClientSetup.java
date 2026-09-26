package first.wildfires.client.sacombat;

import com.ogaba.sa_survival.client.renderer.BackpackCurioRenderer;
import first.wildfires.Wildfires;
import first.wildfires.register.ItemRegister;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegistryObject;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

import java.util.List;

/**
 * Makes the plain Wildfires backpacks appear on a player's back.
 *
 * <p>Survivors Arsenal registers its worn-backpack renderer per item, and gates the worn model on
 * an {@code sa_combat:satchel_render} item property it also registers per item. Neither list
 * includes a new item, so a plain pack would be visible only in the inventory.
 *
 * <p>Both registrations are repeated here for the plain variants, through the public Curios and
 * vanilla APIs. The shared {@link BackpackCurioRenderer} is reused as-is; the companion mixin
 * teaches its internal family checks about the plain variants, so no renderer is reimplemented.
 *
 * <p>Everything happens inside the client setup work queue, which is where Curios expects
 * renderers to be registered.
 */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PlainBackpackClientSetup {

    /** The predicate Survivors Arsenal reads to swap in the worn model. */
    private static final ResourceLocation SATCHEL_RENDER_PROPERTY =
            ResourceLocation.fromNamespaceAndPath("sa_combat", "satchel_render");

    /** Mirrors the NBT key the renderer writes, so the property is set exactly while worn. */
    private static final String RENDER_TAG = "SatchelCurioRender";

    /**
     * Every plain pack, so the shared renderer and the worn-model predicate cover all five.
     *
     * <p>The renderer itself picks the correct worn pose per family, so one registration call per
     * item is enough; only the satchel family swaps in a separate worn model through the property.
     */
    private static final List<RegistryObject<? extends Item>> PLAIN_PACKS = List.of(
            ItemRegister.SatchelLeather,
            ItemRegister.SmallBackpackLeather,
            ItemRegister.DuffelBagLeather,
            ItemRegister.HikingBackpackLeather,
            ItemRegister.MilitaryBackpackLeather
    );

    private PlainBackpackClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (RegistryObject<? extends Item> holder : PLAIN_PACKS) {
                Item item = holder.get();
                if (item == null) {
                    continue;
                }
                CuriosRendererRegistry.register(item, BackpackCurioRenderer::new);
                ItemProperties.register(item, SATCHEL_RENDER_PROPERTY,
                        (stack, level, entity, seed) -> {
                            if (stack == null || !stack.hasTag()) {
                                return 0.0F;
                            }
                            return stack.getTag().getBoolean(RENDER_TAG) ? 1.0F : 0.0F;
                        });
            }
        });
    }
}