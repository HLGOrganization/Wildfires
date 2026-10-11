package first.wildfires.compat.tfc;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blocks.devices.AnvilBlock;
import net.dries007.tfc.common.blocks.rock.RockAnvilBlock;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * Reads the two hands of an anvil click and answers with the {@link AnvilInsertRules.Decision} that the
 * rules class asks for. The rule itself, and why it is what it is, lives in {@link AnvilInsertRules}.
 *
 * <h2>Why the tongs are recognised by a tag</h2>
 *
 * <p>The tongs are a pack item: they are registered per metal by a KubeJS startup script, so the mod has
 * no item of its own to compare against and no TFC tag to borrow. The pack's own {@code kubejs:tongs} tag
 * is what names them, and it is read off the stack the same way the mod's other pack-side hooks read the
 * tags they watch.
 *
 * <h2>Why the anvil is recognised by its class</h2>
 *
 * <p>TFC's own anvil tag has no field in {@code TFCTags}, and the two anvil blocks are the only blocks in
 * the whole of TFC that reach {@code AnvilBlock.interactWithAnvil} - the put-in, the take-out and the weld
 * all live in that one method - so the block test is by class and follows the code path rather than a tag
 * that a pack could add to without the mod following it anywhere. A rock anvil is the same gesture in a
 * different block, so both are named.
 */
public final class AnvilInsertGuard {

    /** The pack's tag for every metal tong, as registered by the KubeJS startup scripts. */
    private static final String TONGS_TAG = "kubejs:tongs";

    private AnvilInsertGuard() {
    }

    /**
     * @return whether this click should be refused, and how. Always {@link AnvilInsertRules.Decision#ALLOW}
     *         unless it is a sneak click on an anvil made with tongs in the clicking hand.
     */
    public static AnvilInsertRules.Decision decide(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());
        ItemStack clicked = player.getItemInHand(event.getHand());
        ItemStack other = player.getItemInHand(otherHand(event.getHand()));

        return AnvilInsertRules.decide(
                player.isShiftKeyDown(),
                isAnvil(state),
                hasTag(clicked, TONGS_TAG),
                clicked.is(TFCTags.Items.HAMMERS),
                other.isEmpty());
    }

    private static InteractionHand otherHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private static boolean isAnvil(BlockState state) {
        return state.getBlock() instanceof AnvilBlock || state.getBlock() instanceof RockAnvilBlock;
    }

    private static boolean hasTag(ItemStack stack, String tag) {
        return !stack.isEmpty() && stack.getTags().anyMatch(key -> key.location().toString().equals(tag));
    }
}
