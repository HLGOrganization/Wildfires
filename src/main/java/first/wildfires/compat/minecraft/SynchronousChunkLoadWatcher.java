package first.wildfires.compat.minecraft;

import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.slf4j.Logger;

/**
 * <h2>What this reports</h2>
 * A wait for a chunk on the server thread, and how long it lasted. It is the second half of the
 * protection against map-driven stalls: the guard in {@link MapChunkLoadGuard} stops the one caller
 * this pack controls, and this watcher only names what still gets through - from another mod, or from
 * vanilla - so a stall that is reported in game can be traced to the chunk that caused it instead of
 * being guessed at from a watchdog crash report.
 *
 * <h2>Why it only reports</h2>
 * The wait it sits in front of is {@code BlockableEventLoop#managedBlock}, and the caller after it
 * still needs the chunk: it goes on to join the same future. Refusing to wait would move the block to
 * that join, where no task can be pumped and nothing can time out, and throwing would take down the
 * caller rather than the wait. The honest repair is therefore to leave the wait alone - the guard
 * exists so that this pack never creates one - and to make the wait visible when something else does.
 *
 * <h2>What it costs</h2>
 * Nothing on the common path. The caller checks the completed flag before it calls in, so only a wait
 * that really had to run is timed, and the line is only assembled when the wait was long enough to be
 * worth one. The stack trace that comes with the line is the useful part: it names whoever asked for
 * the chunk synchronously.
 */
public final class SynchronousChunkLoadWatcher {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PREFIX = "[Wildfires chunk] ";

    /** Earliest a second line may be written, in {@link System#nanoTime()} terms. */
    private static final AtomicLong NEXT_REPORT = new AtomicLong();

    /** Waits that the cooldown swallowed, so the summary is not silent about them. */
    private static final AtomicLong SWALLOWED = new AtomicLong();

    /** Names each chunk once, so a chunk that is asked for repeatedly cannot fill the log. */
    private static final MapChunkLoadRules.ReportBudget REPORTED = new MapChunkLoadRules.ReportBudget(12);

    private SynchronousChunkLoadWatcher() {
    }

    /**
     * Records one synchronous chunk load that has already finished.
     *
     * <p>Called after {@code managedBlock} returned, so the wait is over and nothing about the timing
     * can extend it.
     *
     * @param chunkX       requested chunk X
     * @param chunkZ       requested chunk Z
     * @param status       the status that was requested
     * @param requireChunk whether the caller asked to be given a chunk rather than a future
     * @param elapsedNanos how long the wait took
     */
    public static void report(int chunkX, int chunkZ, ChunkStatus status, boolean requireChunk,
            long elapsedNanos) {
        try {
            long millis = elapsedNanos / 1_000_000L;
            if (millis < MapChunkLoadConfig.watcherWarnMillis()) {
                return;
            }
            String name = status == null ? "null" : status.toString();
            String key = chunkX + "," + chunkZ + "," + name;
            if (REPORTED.contains(key)) {
                return;
            }
            if (!cooledDown()) {
                // The cooldown drops the line, not the name: this chunk is still unnamed and will be
                // named the next time it is waited for once the cooldown has passed.
                SWALLOWED.incrementAndGet();
                return;
            }
            if (!REPORTED.offer(key)) {
                reportLimitJustReached();
                return;
            }
            LOGGER.warn("{}the server thread waited {} ms for chunk {},{} (status {}, requireChunk {}) - "
                            + "a chunk was requested synchronously instead of being waited for off the tick",
                    PREFIX, millis, chunkX, chunkZ, name, requireChunk,
                    new Throwable("synchronous chunk load on the server thread"));
            reportLimitJustReached();
        } catch (Throwable failure) {
            // A watcher that throws would break the chunk load it is watching; it has nothing to lose
            // by giving up quietly.
        }
    }

    /** Whether the cooldown has passed, and moves it forward when it has. */
    private static boolean cooledDown() {
        long now = System.nanoTime();
        long allowFrom = NEXT_REPORT.get();
        if (now < allowFrom) {
            return false;
        }
        long next = now + MapChunkLoadConfig.watcherCooldownMillis() * 1_000_000L;
        return NEXT_REPORT.compareAndSet(allowFrom, next);
    }

    private static void reportLimitJustReached() {
        if (REPORTED.limitJustReached()) {
            LOGGER.info("{}further chunks are not named; {} chunks reported, {} waits inside the cooldown",
                    PREFIX, REPORTED.reported(), SWALLOWED.get());
        }
    }
}
