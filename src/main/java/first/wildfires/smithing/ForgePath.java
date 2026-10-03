package first.wildfires.smithing;

import net.dries007.tfc.common.capabilities.forge.ForgeStep;

import java.util.ArrayList;
import java.util.List;

/**
 * The shortest way to forge one recipe: the target work value, the steps to strike in order, and those
 * steps collapsed into runs of the same strike.
 *
 * <p>The anvil only ever looks at the last three steps when it checks a recipe's rules, so a long stretch
 * of the same strike in the middle of a sequence is one instruction to the player. Runs are what the
 * manual draws - one icon per run, with the run's length beside it.
 *
 * @param target the work value the sequence ends on, or -1 when the world seed is not known yet
 */
public record ForgePath(int target, List<ForgeStep> steps, List<Run> runs) {

    /** One strike repeated {@code count} times in a row. */
    public record Run(ForgeStep step, int count) {
    }

    public ForgePath {
        steps = List.copyOf(steps);
        runs = List.copyOf(runs);
    }

    /** No target known yet: the world seed has not arrived, so nothing can be worked out. */
    public static ForgePath unknown() {
        return new ForgePath(-1, List.of(), List.of());
    }

    /** A known target with no path to it. Not expected in practice, but the manual has to draw something. */
    public static ForgePath empty(int target) {
        return new ForgePath(target, List.of(), List.of());
    }

    /** Counts the runs out of a full sequence. */
    public static ForgePath of(int target, List<ForgeStep> steps) {
        List<Run> runs = new ArrayList<>(steps.size());
        for (ForgeStep step : steps) {
            int last = runs.size() - 1;
            if (last >= 0 && runs.get(last).step() == step) {
                runs.set(last, new Run(step, runs.get(last).count() + 1));
            } else {
                runs.add(new Run(step, 1));
            }
        }
        return new ForgePath(target, steps, runs);
    }

    /** How many strikes the sequence has, run lengths included. */
    public int length() {
        return steps.size();
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }
}
