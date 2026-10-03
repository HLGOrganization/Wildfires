package first.wildfires.item;

import first.wildfires.client.smithing.ClientForgingManual;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Opens the forging manual, which lists every anvil recipe with how close each one is to its one-click
 * forge.
 *
 * <p>Opening a screen is a client-only act, so it is reached through {@link DistExecutor} rather than by
 * referencing the screen directly - otherwise a dedicated server would try to load a client class while
 * merely holding this item.
 */
public class ForgingManualItem extends Item {

    public ForgingManualItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientForgingManual::open);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
