package first.wildfires.smithing;

import java.util.ArrayList;
import java.util.List;

/**
 * Works out which blows, in which order, carry a piece of work to exactly the value a recipe asks for -
 * from nothing, for a piece that has not been struck yet, or from wherever an already-struck piece stands.
 *
 * <h2>What a plan has to satisfy</h2>
 * Three things, and the last is the awkward one.
 *
 * <ul>
 *   <li>The work ends on the recipe's target - not near it, on it. An overshot forging is not a worse
 *       forging, it is a destroyed one.</li>
 *   <li>The work never leaves the anvil's working range while it is being struck. TFC destroys the piece
 *       the moment it passes either end, so an intermediate value is as fatal as the final one.</li>
 *   <li>The recipe's rules are satisfied by the blows that were struck. TFC reads those rules against the
 *       last three blows alone, so the end of a plan is where the rules are won or lost.</li>
 * </ul>
 *
 * <h2>Why a plan has a minimum length</h2>
 * TFC grades the finished item by how many blows it took against its own estimate of the fewest that could
 * have been needed, so a plan longer than that estimate is graded down and a plan shorter than it cannot
 * exist. The caller passes the estimate as {@code minimum}, and this never returns a plan shorter than it.
 * A longer one is only taken when every length below it has no answer at all.
 *
 * <h2>How it searches</h2>
 * The rules touch the last three blows, so the search is done in two parts: an exhaustive walk of every
 * possible ending finds the three-blow tails the recipe accepts, then a reachability sweep over the blows
 * in front of them says whether the work can be brought to the start of such a tail without leaving the
 * working range. That sweep is enough in place of a full search because a blow always moves the work by a
 * fixed amount and the work is bounded, so a position is fully described by how many blows have been struck
 * and where the work sits. Lengths are tried shortest first, and the first length with an answer is the one
 * returned.
 *
 * <p>Blows are passed in and returned as indexes into the caller's array, so this knows nothing about
 * smithing beyond "this blow moves the work by this much".
 *
 * <h2>Where a plan starts</h2>
 * A piece that has already been struck is planned from where it stands: its work is the starting point of the
 * search rather than nothing, and the blows it has already taken are the caller's business, not this
 * search's. Only the rule check needs them, because TFC reads a recipe's rules against the last three blows
 * of the whole forging - so a candidate ending shorter than three blows is one whose window still holds the
 * piece's own earlier blows, and the caller's {@link Tail} is what puts them back in front of it.
 */
public final class ForgePlan {

    /** How much longer than the caller's estimate a plan may be before the search gives up on it. */
    public static final int PADDING = 12;

    /** The longest plan this will ever consider, so a nonsensical estimate cannot spin here forever. */
    public static final int CEILING = 48;

    /**
     * The rule check for a candidate ending: given the last blows of a plan, in the order they are struck,
     * does the recipe accept it? Three blows is a complete tail; fewer means the plan itself is that short,
     * so what the rules see before it is whatever the caller puts there - nothing for a fresh piece, and the
     * blows the piece has already taken for one that has been struck.
     */
    @FunctionalInterface
    public interface Tail {
        boolean accepts(int[] blows);
    }

    private ForgePlan() {
    }

    /**
     * Looks for the shortest plan of at least {@code minimum} blows that finishes exactly on target.
     *
     * @param values  what each blow does to the work, indexed by the id a plan is written in
     * @param limit   the highest and lowest the work may ever sit at while it is struck; the work starts at
     *                {@code start} and may not go below nothing
     * @param target  the work the finished piece must sit at
     * @param minimum the fewest blows the plan may use
     * @param start   the work the piece already stands at: nothing for a fresh one
     * @param tail    whether a candidate's last blows satisfy the recipe
     * @return the blow ids in the order they are to be struck, or {@code null} when there is no plan
     */
    public static int[] find(int[] values, int limit, int target, int minimum, int start, Tail tail) {
        if (values.length == 0 || limit < 1 || target < 0 || target > limit || minimum < 1
                || start < 0 || start > limit) {
            return null;
        }
        int longest = Math.min(CEILING, minimum + PADDING);
        for (int length = minimum; length <= longest; length++) {
            int[] plan = ofLength(values, limit, target, length, start, tail);
            if (plan != null) {
                return plan;
            }
        }
        return null;
    }

    /** A plan of exactly this many blows, or {@code null} when none of that length reaches the target. */
    private static int[] ofLength(int[] values, int limit, int target, int length, int start, Tail tail) {
        if (length < 3) {
            // Too short for a tail or a sweep: the whole plan is the ending, and there are at most 64 of
            // them, so they are simply all tried.
            int[] plan = new int[length];
            return walkEndings(values, limit, target, length, 0, start, plan, tail) ? plan : null;
        }

        List<int[]> endings = endings(values, tail);
        if (endings.isEmpty()) {
            return null;
        }
        int front = length - 3;
        boolean[] canFinish = new boolean[limit + 1];
        for (int work = 0; work <= limit; work++) {
            canFinish[work] = fitsAny(values, endings, work, target, limit);
        }

        boolean[][] reachable = new boolean[front + 1][limit + 1];
        reachable[front] = canFinish;
        for (int struck = front - 1; struck >= 0; struck--) {
            for (int work = 0; work <= limit; work++) {
                for (int value : values) {
                    int next = work + value;
                    if (next >= 0 && next <= limit && reachable[struck + 1][next]) {
                        reachable[struck][work] = true;
                        break;
                    }
                }
            }
        }
        // The search walks forward from where the piece stands, which is the whole of what has to be known
        // about its past: a blow moves the work by a fixed amount, so the same place strikes the same way.
        if (!reachable[0][start]) {
            return null;
        }

        int[] plan = new int[length];
        int work = start;
        for (int struck = 0; struck < front; struck++) {
            for (int id = 0; id < values.length; id++) {
                int next = work + values[id];
                if (next >= 0 && next <= limit && reachable[struck + 1][next]) {
                    plan[struck] = id;
                    work = next;
                    break;
                }
            }
        }
        for (int[] ending : endings) {
            if (fits(values, ending, work, target, limit)) {
                System.arraycopy(ending, 0, plan, front, 3);
                return plan;
            }
        }
        // Unreachable: the sweep only ever stepped onto a work value an ending can finish from.
        return null;
    }

    /** Every three-blow ending the recipe accepts, as ids, in the order they were found. */
    private static List<int[]> endings(int[] values, Tail tail) {
        List<int[]> endings = new ArrayList<>();
        int[] candidate = new int[3];
        for (int first = 0; first < values.length; first++) {
            for (int second = 0; second < values.length; second++) {
                for (int third = 0; third < values.length; third++) {
                    candidate[0] = first;
                    candidate[1] = second;
                    candidate[2] = third;
                    if (tail.accepts(candidate)) {
                        endings.add(candidate.clone());
                    }
                }
            }
        }
        return endings;
    }

    /** The short case: every plan of the given length, tried in turn. */
    private static boolean walkEndings(int[] values, int limit, int target, int length, int struck, int work,
                                       int[] plan, Tail tail) {
        if (struck == length) {
            return work == target && tail.accepts(plan);
        }
        for (int id = 0; id < values.length; id++) {
            int next = work + values[id];
            if (next < 0 || next > limit) {
                continue;
            }
            plan[struck] = id;
            if (walkEndings(values, limit, target, length, struck + 1, next, plan, tail)) {
                return true;
            }
        }
        return false;
    }

    private static boolean fitsAny(int[] values, List<int[]> endings, int work, int target, int limit) {
        for (int[] ending : endings) {
            if (fits(values, ending, work, target, limit)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether this ending takes the work from where it stands to the target without leaving the working
     * range on the way.
     */
    private static boolean fits(int[] values, int[] ending, int work, int target, int limit) {
        int current = work;
        for (int id : ending) {
            current += values[id];
            if (current < 0 || current > limit) {
                return false;
            }
        }
        return current == target;
    }
}
