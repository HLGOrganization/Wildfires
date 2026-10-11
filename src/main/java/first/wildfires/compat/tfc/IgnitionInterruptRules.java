package first.wildfires.compat.tfc;

/**
 * The rule behind the pause that follows an ignition item burning out: for a moment after one leaves the
 * player's hand, a bare-handed sneak click on a cooking pot is refused instead of being carried out.
 *
 * <h2>The click that takes the pot</h2>
 *
 * <p>TFC's firestarter is used by holding the button down for seventy-two ticks and it loses its last point
 * of durability on the final tick of that hold, before the fire is started. A firestarter at one durability
 * therefore breaks in the same tick in which it lights the device, and the player is left holding nothing
 * with the button still down. Vanilla re-runs the click every four ticks for as long as the button is held,
 * so the next click arrives with an empty hand. The player is also sneaking, because that is what lets the
 * firestarter past the device's own use in the first place - and an empty hand plus sneak on a pot is
 * exactly TFC's gesture for picking the pot up, which burns the player for taking a pot off a lit fire. One
 * firestarter at one durability costs one pot, and if that single point of damage kills the player the pot
 * goes with them.
 *
 * <h2>Why the click is refused rather than the pot being guarded</h2>
 *
 * <p>Refusing the bare-handed take for as long as the pot is lit would refuse the ordinary way a player
 * picks up a pot they have finished with, and TFC has no tongs item to require instead. Refusing the click
 * for a moment after an ignition item disappears fixes the accident without touching the gesture itself,
 * and it costs the player almost nothing: the window ends by itself, so picking a pot up on purpose only
 * takes a pause of a second, and a device can still be lit with a fresh firestarter the instant the old one
 * breaks, because that click is made with an item in hand.
 *
 * <h2>Why the window is held open by the clicks it refuses</h2>
 *
 * <p>A held button keeps re-clicking every four ticks, which is well inside {@link #WINDOW_TICKS}, so a
 * window that did not move would be outlived by the accident it exists to stop. Every refused click holds
 * the window open again, so it ends one second after the last refused click rather than one second after
 * the firestarter broke. The price of that is a player who keeps clicking a pot they really do want to pick
 * up: they are refused until they stop for a second.
 *
 * <h2>Why the window only ever moves forward</h2>
 *
 * <p>The window is opened whenever an ignition item leaves either hand, on the client and on the server,
 * and the two sides see that moment a network round trip apart. Taking the later of the two deadlines
 * rather than the most recent one keeps a tick that arrives late on one side from cutting short a window
 * the other side has already extended past it.
 */
public final class IgnitionInterruptRules {

    /**
     * How long, in ticks, a click is refused after an ignition item has left the player's hand.
     *
     * <p>One second: several times the four tick click repeat, comfortably longer than the client and
     * server can disagree about the hand by, and short enough that a player who wants the pot back only has
     * to stop clicking for a moment.
     */
    public static final int WINDOW_TICKS = 20;

    private IgnitionInterruptRules() {
    }

    /**
     * Holds the window open from {@code now}, and never lets it end earlier than it already would have.
     *
     * @param armedUntil the tick the window currently ends on, or anything in the past if it is not open.
     * @param now        the current game time.
     * @return the tick the window should end on.
     */
    public static long arm(long armedUntil, long now) {
        return Math.max(armedUntil, now + WINDOW_TICKS);
    }

    /**
     * @param armedUntil the tick the window ends on, as returned by {@link #arm}.
     * @param now        the current game time.
     * @return whether the window still covers {@code now}. A window armed from tick zero covers ticks zero
     *         to nineteen, so it is exactly {@link #WINDOW_TICKS} ticks long.
     */
    public static boolean isActive(long armedUntil, long now) {
        return now < armedUntil;
    }

    /**
     * @param sneaking     whether the player is sneaking, which is what TFC's pot take is bound to.
     * @param emptyHand    whether the hand that clicked is empty, which is what lets the click reach the
     *                     block at all: with an item in hand vanilla skips the block's own use.
     * @param potTarget    whether the clicked block is a cooking pot.
     * @param windowActive whether an ignition item has left the player's hand within the last
     *                     {@link #WINDOW_TICKS} ticks.
     * @return whether this click should be refused.
     */
    public static boolean shouldInterrupt(boolean sneaking, boolean emptyHand, boolean potTarget, boolean windowActive) {
        return sneaking && emptyHand && potTarget && windowActive;
    }
}
