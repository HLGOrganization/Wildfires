package first.wildfires.compat.farmersdelight;

import first.wildfires.Wildfires;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import vectorwing.farmersdelight.common.block.entity.BasketBlockEntity;

/**
 * Gives Farmer's Delight's baskets the item handler capability they were missing.
 *
 * <p>Create moves every item through {@code IItemHandler}, never through vanilla's {@code Container},
 * and {@code RandomizableContainerBlockEntity} does not expose one - a chest overrides
 * {@code getCapability} itself, a basket does not. Without this the baskets are invisible to funnels,
 * chutes, mechanical arms, chained basins and the basin's own output lookup.
 *
 * <p>Only the baskets are touched: {@code farmersdelight:wooden_basket} and
 * {@code farmersdelight:bamboo_basket} are the mod's only two, and both share the block entity type
 * this attaches to. The wrapper is handed the basket itself as a {@code Container}, so the TFC chest
 * filter {@code BasketBlockEntityMixin} installs on {@code canPlaceItem} keeps out the same items it
 * already keeps out of the basket window.
 */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BasketItemHandler implements ICapabilityProvider {

    private static final ResourceLocation ID = Wildfires.rl("basket_item_handler");

    private final LazyOptional<IItemHandler> handler;

    private BasketItemHandler(BasketBlockEntity basket) {
        // InvWrapper asks the basket, so every vanilla Container rule - including canPlaceItem -
        // applies to anything a machine inserts.
        this.handler = LazyOptional.of(() -> new InvWrapper(basket));
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        if (!(event.getObject() instanceof BasketBlockEntity basket)) {
            return;
        }
        event.addCapability(ID, new BasketItemHandler(basket));
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.ITEM_HANDLER ? handler.cast() : LazyOptional.empty();
    }
}
