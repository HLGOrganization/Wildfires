package first.wildfires.compat.create;

import net.dries007.tfc.common.capabilities.heat.HeatCapability;
import net.dries007.tfc.common.capabilities.heat.IHeat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Wooden Cog heats the stack inside a dropped item in place and never marks the
 * entity data dirty, so clients keep displaying the unheated copy until the item
 * is picked up. Re-sending the stack once its temperature actually changed fixes
 * the display, and the packet is only built when a player is close enough to see it.
 */
public final class FanItemHeatSync {

    private static final int CHECK_INTERVAL_TICKS = 4;
    private static final double SYNC_RADIUS = 5.0D;
    private static final float SYNC_THRESHOLD_CELSIUS = 5.0F;

    private static final Map<ItemEntity, Float> LAST_SENT_TEMPERATURE = new WeakHashMap<>();

    private FanItemHeatSync() {
    }

    /** Runs for every item entity that just ticked on the server. */
    public static void tick(ItemEntity itemEntity) {
        Level level = itemEntity.level();
        if (level.isClientSide() || itemEntity.tickCount % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        if (level.getNearestPlayer(itemEntity, SYNC_RADIUS) == null) {
            return;
        }

        IHeat heat = itemEntity.getItem().getCapability(HeatCapability.CAPABILITY)
                .resolve()
                .orElse(null);
        if (heat == null) {
            LAST_SENT_TEMPERATURE.remove(itemEntity);
            return;
        }

        float temperature = heat.getTemperature();
        Float lastSent = LAST_SENT_TEMPERATURE.get(itemEntity);
        if (lastSent == null) {
            // Clients receive the stack when the entity starts being tracked, so
            // the first sighting only needs to be remembered.
            LAST_SENT_TEMPERATURE.put(itemEntity, temperature);
            return;
        }
        if (Math.abs(temperature - lastSent) < SYNC_THRESHOLD_CELSIUS) {
            return;
        }

        LAST_SENT_TEMPERATURE.put(itemEntity, temperature);
        ItemStack stack = itemEntity.getItem();
        ItemStack payload = stack.copy();
        // Temperature lives inside the stack, so re-sending it is what makes the
        // client render the new heat. SynchedEntityData compares values, and the
        // entity already holds the mutated stack, so writing the payload twice
        // through a different first value is what marks the data dirty.
        ItemStack placeholder = payload.copy();
        placeholder.setCount(payload.getCount() == 1 ? 2 : 1);
        itemEntity.setItem(placeholder);
        itemEntity.setItem(payload);
    }
}
