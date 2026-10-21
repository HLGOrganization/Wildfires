package first.wildfires.soldering;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

/**
 * The soldering iron is a non-consuming tool: right-clicking opens the soldering station.
 *
 * <p>The station itself is drawn by ApricityUI. On a client without AUI there is nothing to draw,
 * so the server explains that instead of opening a menu with no screen.
 */
public final class SolderingIronItem extends Item {

    public SolderingIronItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!ModList.get().isLoaded("apricityui")) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.wildfires.soldering.requires_aui"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            SolderingMenu.open(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
