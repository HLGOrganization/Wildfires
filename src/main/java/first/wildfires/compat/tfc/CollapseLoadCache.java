package first.wildfires.compat.tfc;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Answers TFC's {@code isAreaLoaded(pos, 32)} guard in {@code CollapseRecipe.tryTriggerCollapse} from a
 * cache that lives for exactly one level tick.
 *
 * <h2>What the uncached check costs</h2>
 *
 * <p>{@code LevelReader.isAreaLoaded(BlockPos, int)} is a default method: it allocates two offset
 * {@link BlockPos}es and hands the box to {@code hasChunksAt(BlockPos, BlockPos)}, which turns it into
 * chunk coordinates and loops {@code hasChunk(chunkX, chunkZ)} over the 5x5 chunks the 32 block radius
 * covers. Each of those 25 calls is {@code ServerChunkCache.hasChunk}, which allocates a
 * {@link ChunkPos} (bytecode verified, see the report), hashes it, looks the holder up in the chunk
 * map and compares the holder's level against {@code ChunkLevel.byStatus(FULL)}. So one uncached call is
 * 27 allocations and 25 chunk-map probes, and it runs on <em>every</em> block break or chisel use that
 * reaches {@code tryTriggerCollapse} - before the trigger chance is even rolled, so it is paid whether
 * or not a collapse is going to happen.
 *
 * <h2>Why the answer is cached per chunk and not per larger cell</h2>
 *
 * <p>The result of {@code isAreaLoaded} belongs to one centre position: there is no cell wider than a
 * single chunk that gives every position inside it the same answer, because moving one chunk moves the
 * 5x5 window with it. A coarser key (for example {@code chunkX >> 5}, suggested when this was specified)
 * would answer a query from the <em>other</em> side of a 512 block cell with the answer computed for the
 * first side, and a stale {@code true} is the one answer that must never be wrong here: requirement one
 * is that a collapse is never triggered against an unloaded area. Keying on the centre chunk keeps the
 * cached answer identical to the uncached answer for every query inside one tick, which is the only
 * guarantee the cache has to make.
 *
 * <p>That still removes almost all of the cost: a burst of block breaks in one area asks about the same
 * handful of centre chunks, so the 25 probe scan runs once per chunk per tick instead of once per break.
 *
 * <h2>How the cache ends</h2>
 *
 * <p>There is no scheduler and no cleanup hook. Each bucket remembers the {@code getGameTime()} it was
 * filled for; the first query of a later tick clears it before answering. Chunk load state cannot change
 * within one level tick - chunk loading and unloading run in their own phase of the level tick, not in the
 * middle of the block/entity phase that breaks blocks - so a value cached in tick N is never consulted in
 * tick N+1.
 *
 * <p>Buckets are keyed by dimension rather than by {@link Level} instance, so an unloaded level leaves
 * behind one empty bucket per dimension instead of pinning the level object. The maps are touched only
 * from the thread that ticks that dimension; separate dimensions never share a bucket.
 */
public final class CollapseLoadCache {

    /** The radius TFC passes; only this exact call is cached, any other radius goes straight through. */
    public static final int TFC_RANGE = 32;

    private static final byte UNKNOWN = 0;
    private static final byte NOT_LOADED = 1;
    private static final byte LOADED = 2;

    private static final Map<ResourceKey<Level>, Bucket> BUCKETS = new ConcurrentHashMap<>();

    private CollapseLoadCache() {
    }

    /**
     * The replacement for {@code level.isAreaLoaded(pos, range)} at TFC's call site.
     *
     * <p>Order of work: cache, then the position's own chunk, then - only if both say "maybe" - the real
     * 5x5 scan. The centre chunk is checked before the scan because it is the cheapest possible "no": a
     * chunk that is not loaded at all cannot be part of a loaded 5x5 window.
     */
    public static boolean isAreaLoaded(Level level, BlockPos pos, int range) {
        if (range != TFC_RANGE) {
            // A radius this class never saw: cache nothing rather than answer from a foreign key.
            return level.isAreaLoaded(pos, range);
        }

        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        long tick = level.getGameTime();

        Bucket bucket = BUCKETS.computeIfAbsent(level.dimension(), ignored -> new Bucket());
        if (bucket.tick != tick) {
            bucket.tick = tick;
            bucket.answers.clear();
        }

        long key = ChunkPos.asLong(chunkX, chunkZ);
        byte answer = bucket.answers.get(key);
        if (answer != UNKNOWN) {
            return answer == LOADED;
        }

        // Own chunk first: LevelAccessor's default hasChunk is getChunkSource().hasChunk, and
        // ServerChunkCache's override only looks a holder up - it never loads a chunk.
        if (!level.hasChunk(chunkX, chunkZ)) {
            bucket.answers.put(key, NOT_LOADED);
            return false;
        }

        answer = level.isAreaLoaded(pos, range) ? LOADED : NOT_LOADED;
        bucket.answers.put(key, answer);
        return answer == LOADED;
    }

    /** One dimension's answers for one tick. Not shared between dimensions, so not synchronised. */
    private static final class Bucket {

        long tick = Long.MIN_VALUE;

        /** 0 = not asked yet, 1 = not loaded, 2 = loaded; keyed by {@link ChunkPos#asLong}. */
        final Long2ByteOpenHashMap answers = new Long2ByteOpenHashMap();
    }
}
