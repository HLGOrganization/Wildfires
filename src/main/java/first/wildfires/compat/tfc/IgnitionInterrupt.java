package first.wildfires.compat.tfc;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.blocks.devices.PotBlock;
import net.dries007.tfc.common.items.FirestarterItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * Refuses the click that a burning out ignition item leaves behind, so the pot it just lit is not taken off
 * the fire by the same held button. The rule, and why it is what it is, lives in
 * {@link IgnitionInterruptRules}.
 *
 * <h2>How the moment is recognised</h2>
 *
 * <p>Nothing announces that a firestarter burned out: TFC damages the stack in the middle of its own item
 * tick and the player simply finds an empty hand. What both sides can watch is the hand itself, so at the
 * end of every player tick the main hand and the off hand are compared with the tick before, and an empty
 * hand that was holding an ignition item opens the window. On the server that is the tick the firestarter
 * broke, because the break happens in the player's own tick and this runs at the very end of it. On the
 * client it is the tick the emptied hand is synced in, one network trip later, which is the first tick the
 * client could click with an empty hand at all: the click it makes on that same tick still goes out with the
 * hand it had before the sync reached it.
 *
 * <h2>Which side actually protects the pot</h2>
 *
 * <p>The server takes the pot, so the server's window is the one that decides whether it is taken. The
 * client's copy is there to keep its own prediction of the click in step with the server and to hold the
 * window open while the button is still down; a client that refused a click the server allows would only
 * show a pot that is still there, and a client that allowed a click the server refuses shows nothing at
 * all, because the pickup never happened on this side to begin with.
 *
 * <h2>Why the flags live in the player's own data</h2>
 *
 * <p>The state is three values per player, and it is kept in the player's persistent data rather than in a
 * table of our own so that it is saved, carried across dimensions and dropped together with the player
 * without any bookkeeping on our side.
 */
public final class IgnitionInterrupt {

    /** The tick the window ends on, as an absolute game time, or zero while it has never been opened. */
    private static final String WINDOW_END = "wildfires_ignition_interrupt_until";

    /** Whether the main hand held an ignition item on the previous tick. */
    private static final String IGNITION_WAS_IN_MAIN_HAND = "wildfires_ignition_interrupt_main_hand";

    /** Whether the off hand held an ignition item on the previous tick. */
    private static final String IGNITION_WAS_IN_OFF_HAND = "wildfires_ignition_interrupt_off_hand";

    private IgnitionInterrupt() {
    }

    /**
     * Watches the player's hands and opens the window when one of them has just lost an ignition item.
     * Called at the end of every player tick, on both sides.
     */
    public static void track(Player player) {
        Level level = player.level();
        long now = level.getGameTime();
        CompoundTag data = player.getPersistentData();
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        final boolean mainHandEmptied = data.getBoolean(IGNITION_WAS_IN_MAIN_HAND) && mainHand.isEmpty();
        final boolean offHandEmptied = data.getBoolean(IGNITION_WAS_IN_OFF_HAND) && offHand.isEmpty();
        if (mainHandEmptied || offHandEmptied) {
            data.putLong(WINDOW_END, IgnitionInterruptRules.arm(data.getLong(WINDOW_END), now));
        }

        data.putBoolean(IGNITION_WAS_IN_MAIN_HAND, isIgnition(mainHand));
        data.putBoolean(IGNITION_WAS_IN_OFF_HAND, isIgnition(offHand));
    }

    /**
     * @return whether this click is the bare-handed sneak click on a pot that the window is there to refuse.
     *         A click that carries it out again while the button is held keeps the window open.
     */
    public static boolean interrupt(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        long now = level.getGameTime();
        CompoundTag data = player.getPersistentData();
        if (!IgnitionInterruptRules.shouldInterrupt(
                player.isShiftKeyDown(),
                player.getItemInHand(event.getHand()).isEmpty(),
                level.getBlockState(event.getPos()).getBlock() instanceof PotBlock,
                IgnitionInterruptRules.isActive(data.getLong(WINDOW_END), now))) {
            return false;
        }

        data.putLong(WINDOW_END, IgnitionInterruptRules.arm(data.getLong(WINDOW_END), now));
        return true;
    }

    /**
     * @return whether this stack is used to light a device. TFC's own firestarter handles its own hold and
     *         is in neither tag, so it is named here as well. The two tags are the items TFC lights devices
     *         with from the item use path - the flint and steel, which is damaged, and the fire charge,
     *         which is consumed - and both of them leave the same empty hand behind when they run out.
     */
    private static boolean isIgnition(ItemStack stack) {
        return stack.getItem() instanceof FirestarterItem
                || stack.is(TFCTags.Items.STARTS_FIRES_WITH_DURABILITY)
                || stack.is(TFCTags.Items.STARTS_FIRES_WITH_ITEMS);
    }
}
