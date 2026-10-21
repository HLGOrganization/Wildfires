package first.wildfires.compat.tfc;

import first.wildfires.mixin.tfc.CollapseAccess;
import net.dries007.tfc.util.tracker.Collapse;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The two collapse-queue policies requested on 2026-10-20: a per-collapse cap on how many queued positions
 * one tick processes, and merging of the collapses that the same tick creates inside the same region.
 *
 * <h2>Where the queue is consumed</h2>
 *
 * <p>{@code WorldTracker.tick()} is the only consumer of {@code Collapse.nextPositions}. It is called from
 * {@code ForgeEventHandler.onWorldTick} on {@code TickEvent.LevelTickEvent} of phase {@code START} for every
 * {@code ServerLevel}, and its collapse half runs only when {@code random.nextInt(10) == 0} - so the queue is
 * drained on roughly one tick in ten, <em>all of it at once</em>, once per collapse:
 *
 * <pre>
 *   for (Collapse collapse : collapsesInProgress)
 *       for (BlockPos pos : collapse.nextPositions)          // every queued position
 *           if (can collapse &amp;&amp; distSqr &lt; radiusSquared &amp;&amp; random &lt; chance)
 *               if (CollapseRecipe.collapseBlock(level, pos, state)) survivors.add(pos);
 *   collapse.nextPositions.clear();                          // the whole queue
 *   if (!survivors.isEmpty()) { sound; collapse.nextPositions.addAll(survivors); collapse.radiusSquared *= 0.8; }
 *   collapsesInProgress.removeIf(c -&gt; c.nextPositions.isEmpty());
 * </pre>
 *
 * <p>The peak to flatten is therefore "one collapse, its entire queue, one invocation". The mixin
 * {@code WorldTrackerCollapseMixin} cuts that queue down to {@link #MAX_QUEUED_POSITIONS_PER_TICK} entries at
 * the {@code HEAD} of that method, keeps the rest aside, and puts it back - see
 * {@link #placeTail(List, Map)} and {@link #finish(Map)} for where and in which order.
 *
 * <h2>Why the tail goes back in front of the survivors, and why the radius is held</h2>
 *
 * <p>Two things in TFC's loop are per <em>invocation</em> rather than per position, and both would be wrong if
 * a cap simply dropped the tail back at the end of the queue:
 *
 * <ul>
 *   <li>the loop writes the survivors - the positions that collapsed - back into the queue, so a tail
 *       appended after them is only reached once those survivors have been processed. A position whose
 *       collapse result is itself collapsible (TFC's {@code copy_input} spike recipes are exactly that)
 *       would keep refilling the front of the queue and the tail would never advance. Putting the tail in
 *       front of the survivors makes every invocation consume N <em>not yet processed</em> positions, so the
 *       tail shrinks by N per invocation no matter what the survivors do;</li>
 *   <li>{@code radiusSquared *= 0.8} happens once per invocation, and it gates every position
 *       ({@code distSqr &lt; radiusSquared}). A deferred position judged in a later invocation would be
 *       judged against a radius the unsliced run would not have shrunk yet - and a position outside it is
 *       dropped, i.e. never collapses at all. So the radius the collapse had before the invocation is held
 *       while a tail is pending, and the single decay the unsliced run would have applied lands on the
 *       invocation that drains the last of the tail (that invocation defers nothing, so nothing is held).</li>
 * </ul>
 *
 * <p>Holding the tail back at the <em>end</em> is what the vanilla loop would see, but it cannot make
 * progress; holding it at the front keeps every queued position's fate identical to the unsliced run - it is
 * examined once, against the radius it was queued with, with one roll of the chance. Positions are never
 * processed twice: the tail is removed from the queue at {@code HEAD} and re-inserted exactly once, either by
 * the {@code clear()} hook (TFC ran and is about to write its survivors) or by {@link #finish(Map)} (TFC did
 * not run this tick, so the queue was never touched and the tail simply goes back at the end).
 *
 * <p>Because the tail is already in the queue when TFC's {@code removeIf(c -&gt; c.nextPositions.isEmpty())}
 * runs, a collapse that still has a tail can never be dropped by it - the "TFC removed it, put it back" case
 * does not exist in this design.
 *
 * <h2>Merging</h2>
 *
 * <p>A collapse is created by {@code CollapseRecipe.startCollapse} (one per unsupported position found by
 * {@code tryTriggerCollapse}, which is called from {@code ForgeEventHandler.onBlockBroken} and from
 * {@code ChiselItem}) and by {@code WorldTracker.addCollapsePositions} (the explosion path). Both funnel
 * through {@code WorldTracker.addCollapseData}, which appends to {@code collapsesInProgress} and posts a
 * {@code CollapseEvent}. This class remembers, per level and per {@link #MERGE_CELL_BLOCKS} block cell, the
 * collapse created during the current tick; a second one in the same cell is folded into it instead: its
 * positions are appended without duplicates and the radius is widened to cover them, so no position is lost.
 * The event is not posted a second time, which is the point of merging.
 */
public final class CollapseTicker {

    /**
     * How many queued positions one collapse may process per {@code WorldTracker.tick()} invocation.
     *
     * <p><b>This is the number to change</b> (default 32). It is a compile-time constant on purpose: the
     * request was for a fixed cap, and a config option would have to be read on a path that runs for every
     * collapse of every world. Raising it trades peak work for fewer deferred invocations; lowering it
     * flattens the peak further but spreads one collapse over more invocations.
     *
     * <p>A typical TFC collapse queues one position per successfully collapsed block, which for the shipped
     * {@code collapseMinRadius} plus {@code collapseRadiusVariance} is a few dozen - so 32 is about one
     * invocation's worth for a normal collapse, and only genuinely large ones (or the ones this class merges)
     * are spread over several.
     */
    public static final int MAX_QUEUED_POSITIONS_PER_TICK = 32;

    /**
     * Width, in blocks, of the region a collapse is merged into. 32 blocks is two chunks on a side, which
     * covers the whole unsupported-position box one {@code tryTriggerCollapse} scans (half-extent 2..4
     * blocks) plus the column radius {@code startCollapse} works on, while keeping collapses a few dozen
     * blocks apart in separate cells.
     */
    public static final int MERGE_CELL_BLOCKS = 32;

    /** {@code blockX >> 5} is {@code (blockX >> 4) >> 1} for negative coordinates too. */
    private static final int MERGE_CELL_SHIFT = 5;

    private static final Map<ResourceKey<Level>, Map<Long, Collapse>> CREATED_THIS_TICK = new ConcurrentHashMap<>();

    private CollapseTicker() {
    }

    /** One empty set of deferrals, keyed by the queue list itself so TFC's own {@code clear()} can find it. */
    public static Map<List<BlockPos>, Deferred> newDeferrals() {
        return new IdentityHashMap<>();
    }

    /**
     * Starts a new merge window for this level. Called from the {@code HEAD} of {@code WorldTracker.tick},
     * which runs at {@code LevelTickEvent.START} - before the block updates that create collapses - so a
     * window holds exactly the collapses created during one tick of that level.
     */
    public static void beginTick(Level level) {
        Map<Long, Collapse> created = CREATED_THIS_TICK.get(level.dimension());
        if (created != null) {
            created.clear();
        }
    }

    /**
     * Takes the positions beyond {@link #MAX_QUEUED_POSITIONS_PER_TICK} out of one collapse's queue, leaving
     * TFC exactly the N positions it may process. A queue that already fits is not touched at all.
     */
    public static void defer(Collapse collapse, Map<List<BlockPos>, Deferred> deferrals) {
        CollapseAccess access = (CollapseAccess) collapse;
        List<BlockPos> queue = access.wildfires$getNextPositions();
        int size = queue.size();
        if (size <= MAX_QUEUED_POSITIONS_PER_TICK) {
            return;
        }

        List<BlockPos> tail = new ArrayList<>(queue.subList(MAX_QUEUED_POSITIONS_PER_TICK, size));
        queue.subList(MAX_QUEUED_POSITIONS_PER_TICK, size).clear();
        deferrals.put(queue, new Deferred(access, queue, tail, access.wildfires$getRadiusSquared()));
    }

    /**
     * The {@code nextPositions.clear()} hook: TFC ran and is about to write the survivors of the slice it was
     * given, so the deferred tail is put back <em>first</em> and the survivors land behind it.
     *
     * @return {@code true} when this list was one of ours and the call was handled; {@code false} when the
     *         caller must fall through to the plain {@code clear()}.
     */
    public static boolean placeTail(List<BlockPos> queue, Map<List<BlockPos>, Deferred> deferrals) {
        if (deferrals.isEmpty()) {
            return false;
        }
        Deferred deferred = deferrals.get(queue);
        if (deferred == null) {
            return false;
        }

        queue.clear();
        queue.addAll(deferred.tail);
        deferred.placed = true;
        return true;
    }

    /**
     * Ends the invocation: a tail whose {@code clear()} hook never ran (TFC's one-in-ten gate did not pass, so
     * the queue was never touched) simply goes back at the end, in order; and every deferred collapse gets the
     * radius it had before this invocation back, so the deferred positions are judged against the radius they
     * were queued with.
     */
    public static void finish(Map<List<BlockPos>, Deferred> deferrals) {
        if (deferrals.isEmpty()) {
            return;
        }

        for (Deferred deferred : deferrals.values()) {
            if (!deferred.placed) {
                deferred.queue.addAll(deferred.tail);
            }
            deferred.collapse.wildfires$setRadiusSquared(deferred.radiusSquared);
        }
        deferrals.clear();
    }

    /**
     * Folds a collapse that is being created into the one this tick already created in the same cell.
     *
     * @return {@code true} when it was merged and the caller must cancel TFC's add (and its event),
     *         {@code false} when this is the first collapse of the cell this tick and TFC should add it.
     */
    public static boolean merge(Level level, Collapse incoming) {
        BlockPos center = ((CollapseAccess) incoming).wildfires$getCenterPos();
        long cell = ChunkPos.asLong(center.getX() >> MERGE_CELL_SHIFT, center.getZ() >> MERGE_CELL_SHIFT);

        Map<Long, Collapse> created = CREATED_THIS_TICK.computeIfAbsent(level.dimension(),
                ignored -> new HashMap<>());
        Collapse existing = created.get(cell);
        if (existing == null) {
            created.put(cell, incoming);
            return false;
        }
        if (existing == incoming) {
            return false;
        }

        CollapseAccess target = (CollapseAccess) existing;
        CollapseAccess source = (CollapseAccess) incoming;
        List<BlockPos> targetPositions = target.wildfires$getNextPositions();
        BlockPos targetCenter = target.wildfires$getCenterPos();
        double radius = target.wildfires$getRadiusSquared();

        Set<BlockPos> present = new HashSet<>(targetPositions);
        for (BlockPos pos : source.wildfires$getNextPositions()) {
            if (!present.add(pos)) {
                continue;
            }
            targetPositions.add(pos);
            // TFC tests distSqr(center, pos) < radiusSquared strictly, so a position sitting exactly on the
            // radius would be skipped; the half block of slack keeps every merged position inside it.
            radius = Math.max(radius, pos.distSqr(targetCenter) + 0.5D);
        }
        target.wildfires$setRadiusSquared(radius);
        return true;
    }

    /** The state one deferred tail needs in order to be put back exactly where it came from. */
    public static final class Deferred {

        private final CollapseAccess collapse;
        private final List<BlockPos> queue;
        private final List<BlockPos> tail;
        private final double radiusSquared;
        private boolean placed;

        private Deferred(CollapseAccess collapse, List<BlockPos> queue, List<BlockPos> tail,
                double radiusSquared) {
            this.collapse = collapse;
            this.queue = queue;
            this.tail = tail;
            this.radiusSquared = radiusSquared;
        }
    }
}
