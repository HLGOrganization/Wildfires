package first.wildfires.compat.tfc;

/**
 * The rule behind "one exchange takes one compass": the water source that recharges a compass and the decay
 * tick that dries one out both exchange a single item for a single item, so a stack has to be left with
 * everything except that one item.
 *
 * <h2>Why a rule is needed now that both compasses are unstackable</h2>
 *
 * <p>{@code wildfires:simple_compass} and {@code wildfires:damaged_compass} are registered with
 * {@code durability(60)} - which already forces a maximum stack size of one, so a {@code stacksTo} call must
 * never be chained onto it - and {@code stacksTo(1)} respectively, so a stack of two can no longer be built
 * and the ordinary path - replace the held
 * item where it is - is already exactly one item. What this rule covers is the stacks that already exist:
 * an item's maximum stack size is not applied retroactively, so a world saved before that change can still
 * hold a stack of eight dehydrated compasses, and a player can still hold those eight in one hand.
 * Exchanging one of them is what keeps the other seven from disappearing with the click.
 *
 * <h2>Why the answer is not simply "always take one out"</h2>
 *
 * <p>Replacing the stack where it is, is what makes the recharged compass appear in the hand that used it
 * and costs no inventory operation, and it is the branch every ordinary exchange takes. Taking one item out
 * is the exception that exists for those leftovers, so the rule names the exception: a stack leaves the rest
 * behind when it holds more than the one item being exchanged.
 */
public final class CompassStackRules {

    private CompassStackRules() {
    }

    /**
     * @param stackCount how many compasses the stack that is being exchanged holds.
     * @return whether one item has to be taken out of that stack and the rest left where they are, instead of
     *         the whole stack being replaced by the exchange.
     */
    public static boolean leavesTheRestBehind(int stackCount) {
        return stackCount > 1;
    }
}
