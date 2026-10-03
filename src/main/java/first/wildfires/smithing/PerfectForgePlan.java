package first.wildfires.smithing;

import net.dries007.tfc.common.blockentities.AnvilBlockEntity;
import net.dries007.tfc.common.capabilities.forge.ForgeRule;
import net.dries007.tfc.common.capabilities.forge.ForgeStep;
import net.dries007.tfc.common.capabilities.forge.ForgeSteps;
import net.dries007.tfc.common.capabilities.forge.Forging;
import net.dries007.tfc.common.recipes.AnvilRecipe;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The blows a perfect one-click forge strikes, read off the recipe itself.
 *
 * <h2>Where the blows come from</h2>
 * TFC grades a finished forging against {@link ForgeRule#calculateOptimalStepsToTarget} - its own estimate of
 * the fewest blows a recipe could be finished in. That estimate is a number of blows and nothing more: TFC
 * never builds the sequence it stands for, because it never needs one, so the sequence has to be searched
 * for. {@link ForgePlan} does the searching; this hands it the recipe's target, the work each kind of blow
 * does, and a rule check built from the recipe's own rules, so the ending it accepts is exactly the ending
 * TFC would have accepted from a smith's hands.
 *
 * <p>The result is a run of that many real blows, struck on the anvil one at a time. Because the count is
 * never below TFC's estimate, the ratio TFC grades the result by is at least one, which is a perfect
 * forging - so the item is not stamped with a perfect bonus, it earns one.
 *
 * <h2>Picking up a piece that has been struck</h2>
 * A piece may already have been struck when this is asked - by hand, or by a run of either kind that was cut
 * short - and the run planned for it finishes it from where it stands rather than from nothing. Its blows
 * still count against TFC's estimate, so the run is the estimate less the blows already taken: the finished
 * item is graded exactly as if the whole sequence had been struck in one go.
 *
 * <p>A piece struck past that estimate cannot be perfect any more - the ratio TFC grades by is below one and
 * nothing brings it back. Such a piece is still finished rather than refused, because a refusal would leave
 * it half-worked on the anvil with no way forward, which is the state this exists to get out of.
 *
 * <h2>Blow order</h2>
 * Blows are offered to the search heaviest first, so a plan tends to shape the metal up to the target and
 * then settle it with the lighter blows a recipe's rules usually ask for, rather than the other way round.
 * The search is otherwise indifferent to which of two plans of the same length it returns.
 */
final class PerfectForgePlan {

    /**
     * The longest estimate worth chasing. TFC's own estimate runs away to {@link Integer#MAX_VALUE} when a
     * recipe's target is one its path table does not cover, and a plan of dozens of blows is not something a
     * smith could have struck anyway - so past this the recipe is reported as one the anvil cannot plan.
     */
    private static final int MAX_OPTIMAL = ForgePlan.CEILING;

    private PerfectForgePlan() {
    }

    /**
     * The blows that finish this recipe from the piece that is on the anvil, or an empty list when there is no
     * such run within reach. A piece that has been struck already is finished from where it stands.
     */
    static List<ForgeStep> of(AnvilRecipe recipe, AnvilBlockEntity.AnvilInventory inventory, Forging forging) {
        int target = recipe.computeTarget(inventory);
        ForgeRule[] rules = recipe.getRules();
        int optimal = ForgeRule.calculateOptimalStepsToTarget(target, rules);
        if (optimal < 1 || optimal > MAX_OPTIMAL) {
            return List.of();
        }

        ForgeStep[] order = ForgeStep.VALUES.clone();
        Arrays.sort(order, Comparator.comparingInt(ForgeStep::step).reversed());
        int[] values = new int[order.length];
        for (int id = 0; id < order.length; id++) {
            values[id] = order[id].step();
        }

        // The blows the piece has already taken are behind the plan, so they are the search's starting point
        // and they count against the estimate it has to be at least as long as. Never less than one blow: a
        // piece the estimate says is already finished still has to be brought back onto its target.
        ForgeSteps before = copy(forging.getSteps());
        int minimum = Math.max(1, optimal - before.total());
        int[] plan = ForgePlan.find(values, ForgeStep.LIMIT, target, minimum, forging.getWork(),
                blows -> accepted(rules, order, before, blows));
        if (plan == null) {
            return List.of();
        }
        List<ForgeStep> strikes = new ArrayList<>(plan.length);
        for (int id : plan) {
            strikes.add(order[id]);
        }
        return strikes;
    }

    /**
     * Whether the recipe's rules are all satisfied by these blows, which are the last ones the recipe's work
     * will have seen. TFC reads a rule against the last three blows and nothing else, so the piece's own blows
     * are put back in front of the candidate: a run of three here pushes them out of the window, exactly as
     * the finished forging would, while a run of one or two leaves them inside it where TFC would see them.
     */
    private static boolean accepted(ForgeRule[] rules, ForgeStep[] order, ForgeSteps before, int[] blows) {
        if (rules.length == 0) {
            return true;
        }
        ForgeSteps steps = copy(before);
        for (int id : blows) {
            steps.addStep(order[id]);
        }
        for (ForgeRule rule : rules) {
            if (!rule.matches(steps)) {
                return false;
            }
        }
        return true;
    }

    /** The blows a piece has already taken, as a copy the search may add its own candidates to. */
    private static ForgeSteps copy(ForgeSteps steps) {
        return new ForgeSteps().read(steps.write(new CompoundTag()));
    }
}
