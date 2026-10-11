package first.wildfires.compat.vintageimprovements;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

/**
 * <h2>What this is</h2>
 * The rule behind the guard that keeps Vintage Improvements' curving press from throwing on a recipe
 * whose ingredient has no item list, plus the budget that stops the guard from filling the log while
 * it names the recipes that hit it. The wiring half - reading the slots, writing the line - is
 * {@link CurvingPressGuard}, which is called from the head of
 * {@code com.negodya1.vintageimprovements.content.kinetics.curving_press.CurvingPressBlockEntity.canCurve}.
 *
 * <h2>What actually throws</h2>
 * {@code canCurve(Recipe, int)} handles four modes. Modes 3 and 4 ask an ingredient for its items,
 * check that the array they got is not empty, and only then read {@code [0]} of it. Modes 1 and 2 do
 * not: after a pre-check that the slot {@code mode - 1} is not empty, the ingredient loop takes the
 * <em>first</em> candidate slot and does {@code reference = ingredient.getItems()[0]} without asking
 * how long that array is. That first candidate is exactly the slot the pre-check looked at, because
 * mode 1 walks the even slots and starts at 0 while mode 2 walks the odd ones and starts at 1 - so
 * the whole crash comes down to one question about one slot: is it not empty while its item list is
 * empty? Every other slot is only ever compared against the reference stack that came out of it.
 *
 * <h2>Why an ingredient can be not empty and still list nothing</h2>
 * Forge's {@code Ingredient.isEmpty()} is {@code values.length == 0}, but {@code getItems()} is a
 * second, independent trip through those same values: each one is resolved to item stacks and the
 * entries that fail to resolve are dropped. TFC's delegate and filter ingredients
 * ({@code DelegateIngredient} and its {@code TraitIngredient}, {@code HeatableIngredient},
 * {@code NotRottenIngredient} subclasses, woodencog's {@code HeatedIngredient} and
 * {@code FoodIngredient}) resolve through TFC item and heat data, so a tag whose items carry none of
 * that data lands on a barrier or on air, gets filtered out, and leaves an ingredient that is not
 * empty with an item list of length zero. The list is baked when recipes are read, so this is a
 * property of that read and not of the press - which is why a server join, where the client replaces
 * its recipe set with the server's, can leave a handful of recipes broken for the rest of the
 * session, and why a restart brings them back.
 *
 * <h2>What the guard does about it</h2>
 * It refuses the recipe rather than repairing it. The recipe data, not the code, is what is wrong,
 * and an ingredient that cannot list a single item could not be matched by the press anyway. The
 * refusal answers {@code false}, which is what {@code canCurve} itself answers for every recipe it
 * cannot process, so no recipe that Vintage Improvements would have accepted is lost - and the caller
 * that used to die here keeps the rest of its work. That caller is the expensive one: JEI registers
 * curving recipes by iterating every crafting recipe through this predicate, abandons the whole
 * plugin callback on the first throw, and never retries, which is what takes the turning and
 * laser-cutting categories out of JEI for the session while the press itself keeps working.
 */
public final class CurvingPressRules {

    /** The answer for a mode that never reads an ingredient's item list, so the guard never refuses. */
    public static final int NO_SLOT = -1;

    private CurvingPressRules() {
    }

    /**
     * The slot whose item list a mode's ingredient loop reads without checking its length, or
     * {@link #NO_SLOT} for the modes that check it themselves.
     */
    public static int watchedSlot(int mode) {
        if (mode == 1) {
            return 0;
        }
        if (mode == 2) {
            return 1;
        }
        return NO_SLOT;
    }

    /**
     * Whether the guard should answer {@code false} in place of the crash.
     *
     * @param mode  the mode {@code canCurve} was called with
     * @param size  the number of ingredient slots the recipe has
     * @param slotAt a view of one slot; only the watched slot is read, and it is read once
     */
    public static boolean refuses(int mode, int size, IntFunction<Slot> slotAt) {
        int slot = watchedSlot(mode);
        if (slot == NO_SLOT) {
            return false;
        }
        if (slot >= size) {
            // canCurve's own ings.get(mode - 1) is the first thing that reads the slot, so a recipe
            // too short to hold it throws before any ingredient is looked at.
            return true;
        }
        Slot view = slotAt.apply(slot);
        if (view == null || view.empty()) {
            // An empty slot makes canCurve answer false before it ever reaches the item list.
            return false;
        }
        // Zero items is the ArrayIndexOutOfBoundsException; -1 is the same read throwing outright.
        return view.itemCount() <= 0;
    }

    /**
     * One ingredient slot as the guard sees it.
     *
     * @param empty     {@code Ingredient.isEmpty()}
     * @param itemCount the length of {@code Ingredient.getItems()}, or {@link #UNREADABLE}
     */
    public record Slot(boolean empty, int itemCount) {

        /** {@code itemCount} for a slot that threw while it was being read. */
        public static final int UNREADABLE = -1;
    }

    /**
     * <h2>What this is</h2>
     * The bookkeeping that keeps a broken recipe from being logged once per recipe lookup: the first
     * offer of a key is accepted, a repeat is dropped without a word, and only {@code limit} distinct
     * keys are ever accepted.
     *
     * <h2>Why the repeats have to be dropped</h2>
     * The refused call is a predicate. JEI walks every crafting recipe through it during
     * registration, and the press walks the recipes it can see whenever it looks for a recipe, so a
     * single broken recipe would otherwise write the same line to the log for the rest of the
     * session - which is precisely the noise this guard is meant to replace with one line per recipe.
     * The limit is the second half of that: a pack that ships a broken tag used by dozens of recipes
     * should say so a dozen times, not hundreds.
     *
     * <h2>Thread safety</h2>
     * The guard is not called from one thread only - recipe registration runs on the client thread
     * while the press is reachable from the server thread, and this pack has at least one mod that
     * reaches block entities from its own worker. The set and both counters are concurrent, and the
     * limit announcement is a one-shot compare-and-set.
     */
    public static final class ReportBudget {

        /** How many distinct recipes are named before the guard goes quiet. */
        public static final int DEFAULT_LIMIT = 12;

        private final int limit;
        private final Set<String> offered = ConcurrentHashMap.newKeySet();
        private final AtomicInteger suppressed = new AtomicInteger();
        private final AtomicBoolean limitAnnounced = new AtomicBoolean();

        public ReportBudget() {
            this(DEFAULT_LIMIT);
        }

        public ReportBudget(int limit) {
            this.limit = Math.max(1, limit);
        }

        /**
         * Whether {@code key} should be logged now. False means the key was already taken, or that it
         * arrived after the limit was full; {@link #suppressed()} counts the second case only.
         */
        public boolean offer(String key) {
            String normalized = key == null ? "<unknown>" : key;
            if (!offered.add(normalized)) {
                return false;
            }
            if (offered.size() > limit) {
                suppressed.incrementAndGet();
                return false;
            }
            return true;
        }

        /**
         * Whether the limit has just swallowed its first key. Answers true once, so the caller can say
         * that it has stopped naming recipes without saying it again for every recipe after that.
         */
        public boolean limitJustReached() {
            return suppressed.get() > 0 && limitAnnounced.compareAndSet(false, true);
        }

        /** How many distinct keys were accepted, which is the limit once it is full. */
        public int reported() {
            return Math.min(offered.size(), limit);
        }

        /** How many distinct keys arrived after the limit was full. */
        public int suppressed() {
            return suppressed.get();
        }

        public int limit() {
            return limit;
        }
    }
}
