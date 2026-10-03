package first.wildfires.smithing;

import net.dries007.tfc.common.capabilities.forge.ForgeRule;
import net.dries007.tfc.common.capabilities.forge.ForgeStep;
import net.dries007.tfc.common.capabilities.forge.ForgeSteps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Works out the shortest sequence of strikes that forges a recipe's target and follows its rules.
 *
 * <h2>Why a search is needed</h2>
 * The target alone would be easy - TFC can work out the fewest strikes to reach a work value. The rules
 * are what makes it awkward: each one is about the last three strikes ({@code ForgeRule#matches}), so a
 * sequence that lands exactly on the target can still be rejected, and a sequence that satisfies every
 * rule can miss the target. Both at once, in as few strikes as possible, is the search below.
 *
 * <h2>How it searches</h2>
 * A strike always adds one step, so the reachable states fall into layers by how many strikes were used:
 * every state in layer {@code L} comes from layer {@code L - 1} and nothing else. That makes this a plain
 * shortest-path pass over a layered graph, with no queue to reconsider - one sweep in layer order settles
 * every state.
 *
 * <p>A state is the anvil's whole situation: the current work value, plus the last three strikes, which is
 * all a rule can see. Work is kept within {@code 0 .. 149} because the anvil refuses to go outside that
 * range, so no path offered here can be struck for real. States are ranked first by strike count, then by
 * how many icons the sequence needs, which is why the result is the shortest path in the sense the player
 * cares about: fewest strikes, and among those, fewest separate instructions.
 *
 * <p>The table does not depend on the recipe - the same strike always changes work by the same amount and
 * always pushes the same step into the window - so it is built once and every recipe is then a scan over
 * the states that land on its target.
 */
public final class ForgePathFinder {

    /** The anvil's work range: {@code ForgeStep.LIMIT} is 150, and work may not leave {@code [0, 150)}. */
    private static final int WORK_LIMIT = 150;

    /** One slot per recent strike, plus a zero meaning "no strike yet". */
    private static final int SLOTS = ForgeStep.VALUES.length + 1;
    private static final int WINDOWS = SLOTS * SLOTS * SLOTS;

    private static final int STATES = WORK_LIMIT * WINDOWS;
    private static final int UNREACHED = Integer.MAX_VALUE;

    /** Built once on first use: about a million array writes, then every lookup is a scan. */
    private static final ForgePathFinder INSTANCE = new ForgePathFinder();

    private final int[] lengths = new int[STATES];
    private final int[] runCounts = new int[STATES];
    private final int[] parents = new int[STATES];
    private final byte[] parentSteps = new byte[STATES];

    /** Reachable states in layer order, each listed exactly once. */
    private final int[] queue = new int[STATES];

    private ForgePathFinder() {
        Arrays.fill(lengths, UNREACHED);
        Arrays.fill(runCounts, UNREACHED);
        Arrays.fill(parents, -1);

        // The start: no work done, no strikes made.
        lengths[0] = 0;
        runCounts[0] = 0;

        int head = 0;
        int tail = 1;
        queue[0] = 0;
        int layerStart = 0;
        int layerEnd = 1;

        while (layerStart < layerEnd) {
            int nextLayerStart = layerEnd;
            int used = lengths[queue[layerStart]];

            for (int i = layerStart; i < layerEnd; i++) {
                int state = queue[i];
                int work = state / WINDOWS;
                int window = state % WINDOWS;
                int recent = window % SLOTS;
                int runs = runCounts[state];

                for (int index = 0; index < ForgeStep.VALUES.length; index++) {
                    int nextWork = work + ForgeStep.VALUES[index].step();
                    if (nextWork < 0 || nextWork >= WORK_LIMIT) {
                        continue;
                    }

                    // Push the strike in: the oldest slot falls off, everything shifts up by one.
                    int nextWindow = (window % (SLOTS * SLOTS)) * SLOTS + index + 1;
                    int nextState = nextWork * WINDOWS + nextWindow;
                    // Repeating the strike the player just made continues the same icon.
                    int nextRuns = runs + (recent == index + 1 ? 0 : 1);

                    if (lengths[nextState] == UNREACHED) {
                        lengths[nextState] = used + 1;
                        runCounts[nextState] = nextRuns;
                        parents[nextState] = state;
                        parentSteps[nextState] = (byte) index;
                        queue[tail++] = nextState;
                    } else if (lengths[nextState] == used + 1 && nextRuns < runCounts[nextState]) {
                        // Same number of strikes, fewer icons: a better way to get here.
                        runCounts[nextState] = nextRuns;
                        parents[nextState] = state;
                        parentSteps[nextState] = (byte) index;
                    }
                }
            }

            layerStart = nextLayerStart;
            layerEnd = tail;
        }
    }

    public static ForgePathFinder instance() {
        return INSTANCE;
    }

    /**
     * The shortest sequence that ends on {@code target} and satisfies every rule.
     *
     * @return the sequence, or an empty path if the target is out of range or nothing can satisfy the
     *         rules - which the manual has to be able to draw either way
     */
    public ForgePath find(int target, ForgeRule[] rules) {
        ForgeRule[] wanted = rules == null ? new ForgeRule[0] : rules;
        if (target < 0 || target >= WORK_LIMIT) {
            return ForgePath.empty(target);
        }

        int base = target * WINDOWS;
        int bestState = -1;
        int bestLength = UNREACHED;
        int bestRuns = UNREACHED;

        for (int window = 0; window < WINDOWS; window++) {
            int state = base + window;
            int length = lengths[state];
            if (length == UNREACHED || length > bestLength) {
                continue;
            }
            if (!matches(window, wanted)) {
                continue;
            }
            int runs = runCounts[state];
            if (length < bestLength || runs < bestRuns) {
                bestState = state;
                bestLength = length;
                bestRuns = runs;
            }
        }

        if (bestState < 0) {
            return ForgePath.empty(target);
        }

        List<ForgeStep> steps = new ArrayList<>(bestLength);
        for (int state = bestState; parents[state] >= 0; state = parents[state]) {
            steps.add(ForgeStep.VALUES[parentSteps[state]]);
        }
        Collections.reverse(steps);

        // Belt and braces: the manual would rather show nothing than a sequence that cannot be forged.
        if (!isExecutable(steps, target, wanted)) {
            return ForgePath.empty(target);
        }
        return ForgePath.of(target, steps);
    }

    /** Whether the last three slots of a window satisfy every rule, using TFC's own check. */
    private static boolean matches(int window, ForgeRule[] rules) {
        if (rules.length == 0) {
            return true;
        }
        ForgeSteps steps = new ForgeSteps();
        // Oldest first: ForgeSteps#addStep pushes each strike to the front, so the last one added has to be
        // the newest strike for last()/secondLast()/thirdLast() to mean what the rules expect.
        for (int slot = 2; slot >= 0; slot--) {
            int value = (window / pow(slot)) % SLOTS;
            if (value > 0) {
                steps.addStep(ForgeStep.VALUES[value - 1]);
            }
        }
        for (ForgeRule rule : rules) {
            if (!rule.matches(steps)) {
                return false;
            }
        }
        return true;
    }

    /** Re-walks a finished sequence as the anvil would, target and rules included. */
    private static boolean isExecutable(List<ForgeStep> steps, int target, ForgeRule[] rules) {
        int work = 0;
        ForgeSteps history = new ForgeSteps();
        for (ForgeStep step : steps) {
            work += step.step();
            if (work < 0 || work >= WORK_LIMIT) {
                return false;
            }
            history.addStep(step);
        }
        if (work != target || history.total() != steps.size()) {
            return false;
        }
        for (ForgeRule rule : rules) {
            if (!rule.matches(history)) {
                return false;
            }
        }
        return true;
    }

    private static int pow(int slot) {
        int value = 1;
        for (int i = 0; i < slot; i++) {
            value *= SLOTS;
        }
        return value;
    }
}
