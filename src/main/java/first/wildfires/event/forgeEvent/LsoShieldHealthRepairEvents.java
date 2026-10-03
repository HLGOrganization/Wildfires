package first.wildfires.event.forgeEvent;

import first.wildfires.Wildfires;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import sfiomn.legendarysurvivaloverhaul.common.capabilities.health.HealthCapability;
import sfiomn.legendarysurvivaloverhaul.util.CapabilityUtil;

/**
 * Repairs an invalid (NaN) Legendary Survival Overhaul shield value.
 * <p>
 * A NaN shield is unrecoverable in game: {@code HealthUtil.hurtPlayer} returns {@code Math.max(damage - shield, 0)},
 * which stays NaN for every later hit, and vanilla writes that through {@code setHealth(getHealth() - NaN)} where
 * {@code Mth.clamp(NaN, 0, maxHealth)} is still NaN. Such a player is then neither alive nor dying - no death, no
 * respawn screen, no regeneration - and {@code addShieldHealth} can never clear it because {@code NaN + x} is NaN.
 * {@code setShieldHealth} cannot sanitise it either, since its own clamp passes NaN through.
 * <p>
 * Wildfires also hides LSO's health HUD ({@code RenderHealthGuiMixin}), so the player never sees the invalid value.
 * Both hooks below therefore write a plain {@code 0} instead of waiting for LSO to recover on its own.
 */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LsoShieldHealthRepairEvents {

    private LsoShieldHealthRepairEvents() {
    }

    /**
     * Death is the pack's reset point: whatever shield the player was carrying is meaningless once they die, so a NaN
     * is cleared there even when the death did not come from the damage path (a direct {@code die()} call, as used by
     * PlayerRevive, never passes through LSO's damage handler).
     */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            repairShieldHealth(player);
        }
    }

    /**
     * A player whose shield is already NaN cannot die from damage at all, so the death hook can never fire for them and
     * the corruption has to be cleared when the save is loaded. This also repairs saves that were written before this
     * fix existed, without any offline NBT surgery.
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            repairShieldHealth(player);
        }
    }

    private static void repairShieldHealth(ServerPlayer player) {
        if (!Wildfires.LSOLoaded) {
            return;
        }
        HealthCapability health = CapabilityUtil.getHealthCapability(player);
        if (health != null && Float.isNaN(health.getShieldHealth())) {
            health.setShieldHealth(0.0F);
        }
    }
}
