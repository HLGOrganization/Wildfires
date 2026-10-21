package first.wildfires.mixin.tfc;

import first.wildfires.compat.tfc.CollapseTicker;
import net.dries007.tfc.util.tracker.Collapse;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

/**
 * Requirements two and three of 2026-10-20, at the two places {@code WorldTracker} owns the collapse state:
 * the tick that drains the queue, and the funnel that creates a collapse.
 *
 * <h2>Requirement two - at most N positions per invocation</h2>
 *
 * <p>{@code WorldTracker.tick()} is the only consumer of {@code Collapse.nextPositions}. It is reached from
 * {@code ForgeEventHandler.onWorldTick} on {@code TickEvent.LevelTickEvent} of phase {@code START}, for
 * {@code ServerLevel}s only, and its collapse half runs when {@code random.nextInt(10) == 0}. Each such run
 * walks the <em>whole</em> queue of every collapse in progress, then clears it and writes back the survivors
 * (offset 208 of TFC 3.2.20's {@code tick()V} is the single {@code List.clear()} call in that method).
 *
 * <p>Three hooks, none of which rewrites the method body and none of which depends on an ordinal or on a line
 * number - a TFC update that renames these methods fails at start-up instead of silently changing behaviour:
 *
 * <ul>
 *   <li>{@code HEAD}: start the merge window for this level, then take every collapse's positions beyond
 *       {@link CollapseTicker#MAX_QUEUED_POSITIONS_PER_TICK} out of its queue and keep them aside together
 *       with the radius the collapse had before this invocation;</li>
 *   <li>{@code List.clear()} inside {@code tick}: TFC ran and is about to write the survivors of the slice it
 *       was given, so the kept tail goes back in <em>front</em> of them. The tail is then already in the queue
 *       when TFC's {@code removeIf} looks at it, which is why a collapse with a tail can never be removed
 *       here and why there is no "put the collapse back" case;</li>
 *   <li>{@code RETURN}: for a collapse whose {@code clear()} never ran (the one-in-ten gate did not pass, so
 *       the queue was never touched), put the tail back at the end unchanged; in every case restore the
 *       pre-invocation radius, so the positions still waiting are judged against the radius they were queued
 *       with and the single {@code * 0.8} of the unsliced run lands on the invocation that drains the tail.</li>
 * </ul>
 *
 * <p>Nothing is processed twice: the tail is a range removed from the queue at {@code HEAD} and re-inserted
 * exactly once, and progress is guaranteed because the front of the queue is always work TFC has not seen yet
 * (the survivors end up behind the tail, so a {@code copy_input} recipe that collapses the same block again
 * cannot starve it).
 *
 * <p>What this cannot preserve is the exact random sequence: TFC's {@code random} is the {@code WorldTracker}
 * field, shared with {@code addCollapsePositions} and the rain maths, so processing a queue in slices consumes
 * {@code nextFloat()} in a different rhythm than one big loop would. The same is true of any cap - the number
 * of positions per invocation is the thing being changed - and it is recorded here rather than glossed over.
 *
 * <h2>Requirement three - one collapse per region per tick</h2>
 *
 * <p>{@code addCollapseData(Collapse)} is the single funnel for a new collapse: it appends to
 * {@code collapsesInProgress} and posts the {@code CollapseEvent}. {@code CollapseRecipe.startCollapse}
 * creates one collapse per unsupported block position it found, so a single {@code tryTriggerCollapse} call
 * can create several collapses in the same few blocks; the explosion path
 * ({@code WorldTracker.addCollapsePositions}) creates one per explosion.
 *
 * <p>This injection cancels the add when {@link CollapseTicker#merge} folded the incoming collapse into the
 * one this tick already created in the same 32 block cell - the incoming positions are appended to that
 * collapse (de-duplicated) and its radius is widened to keep them inside it. Cancelling means no second entry
 * in {@code collapsesInProgress} and no second {@code CollapseEvent}, which is the whole point; the surviving
 * collapse keeps the centre of whichever one was created first in the cell.
 */
@Pseudo
@Mixin(targets = "net.dries007.tfc.util.tracker.WorldTracker", remap = false)
public abstract class WorldTrackerCollapseMixin {

    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    private List<Collapse> collapsesInProgress;

    /** Per-invocation state, keyed by the queue list so TFC's own {@code clear()} can find its entry. */
    @Unique
    private Map<List<BlockPos>, CollapseTicker.Deferred> wildfires$deferred;

    @Inject(method = "tick", at = @At("HEAD"), require = 1)
    private void wildfires$deferQueueTail(CallbackInfo ci) {
        CollapseTicker.beginTick(level);

        if (wildfires$deferred == null) {
            wildfires$deferred = CollapseTicker.newDeferrals();
        } else {
            // Normally already empty: the RETURN hook below drained it. It is only non-empty here when the
            // previous invocation threw before reaching its return, and then this finishes that invocation's
            // bookkeeping (tails back into the queue, radii back) before a new one starts.
            CollapseTicker.finish(wildfires$deferred);
        }
        for (Collapse collapse : collapsesInProgress) {
            CollapseTicker.defer(collapse, wildfires$deferred);
        }
    }

    /**
     * The single {@code collapse.nextPositions.clear()} in {@code tick()}: when this queue has a deferred tail,
     * the tail is re-inserted here so TFC's survivors are appended behind it. Any other {@code clear()} call
     * (there is none in 3.2.20) keeps its plain behaviour.
     */
    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = "Ljava/util/List;clear()V", remap = false),
            require = 1
    )
    private void wildfires$placeDeferredTail(List<BlockPos> queue) {
        if (wildfires$deferred == null || !CollapseTicker.placeTail(queue, wildfires$deferred)) {
            queue.clear();
        }
    }

    @Inject(method = "tick", at = @At("RETURN"), require = 1)
    private void wildfires$finishDeferredQueues(CallbackInfo ci) {
        if (wildfires$deferred != null) {
            CollapseTicker.finish(wildfires$deferred);
        }
    }

    @Inject(method = "addCollapseData", at = @At("HEAD"), cancellable = true, require = 1)
    private void wildfires$mergeIntoSameTickCollapse(Collapse collapse, CallbackInfo ci) {
        if (CollapseTicker.merge(level, collapse)) {
            ci.cancel();
        }
    }
}
