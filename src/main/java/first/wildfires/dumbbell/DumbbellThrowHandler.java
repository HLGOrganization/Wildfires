package first.wildfires.dumbbell;

import first.wildfires.Wildfires;
import first.wildfires.entity.DumbbellProjectile;
import first.wildfires.register.EntityRegister;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Turns the released right-click charge of a dumbbell into a throw. */
@Mod.EventBusSubscriber(modid = Wildfires.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DumbbellThrowHandler {

    private DumbbellThrowHandler() {
    }

    @SubscribeEvent
    public static void onStopUsing(LivingEntityUseItemEvent.Stop event) {
        ItemStack stack = event.getItem();
        if (!Dumbbells.isDumbbell(stack)) {
            return;
        }

        LivingEntity thrower = event.getEntity();
        Level level = thrower.level();
        if (level.isClientSide()) {
            return;
        }

        int chargeTicks = Math.max(0, stack.getUseDuration() - event.getDuration());
        float velocity = Dumbbells.throwVelocity(chargeTicks);

        DumbbellProjectile projectile = new DumbbellProjectile(EntityRegister.DUMBBELL.get(), thrower, level);
        projectile.setItem(stack.copyWithCount(1));
        projectile.setSpinRate(Dumbbells.spinRate(velocity));
        projectile.shootFromRotation(thrower, thrower.getXRot(), thrower.getYRot(), 0.0F,
                velocity, Dumbbells.THROW_INACCURACY);
        level.addFreshEntity(projectile);

        level.playSound(null, thrower.getX(), thrower.getEyeY(), thrower.getZ(),
                SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.8F, 0.8F + level.random.nextFloat() * 0.3F);

        // One dumbbell per throw, unless the thrower is in creative mode.
        if (!(thrower instanceof Player player) || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

}
