package com.gameexpert.engine;

import com.gameexpert.terrain.CanonicalOriginChunkProductSource;
import com.gameexpert.terrain.ChunkProductSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Produces canonical chunk products ahead of the world that will ask for them.
 *
 * <p>Producing one canonical chunk costs hundreds of milliseconds; replaying a chunk this world
 * already committed costs about a millisecond. Nothing about the owner-turn budget can absorb the
 * former, so this scheduler moves it off every demanding thread: one dedicated producer walks a
 * bounded spiral around the currently demanded chunk and commits those products before anybody
 * needs them. Every consumer — the world owner, snapshot preparation, activation planning — then
 * finds the product already on record and takes the lock-free replay path.</p>
 *
 * <p>The scheduler is a pure accelerator. It changes no byte: {@link
 * CanonicalOriginChunkProductSource} remains the single production seam, the same bounded memos
 * amortize the same inputs, and a coordinate this scheduler never reaches is still produced by
 * its demanding caller exactly as before. Deleting it would cost latency, not identity.</p>
 *
 * <p><b>Ordering</b>: the spiral is deliberate. Neighbouring FEATURES targets share twenty of
 * their twenty-five post-CARVERS input chunks, so advancing one chunk at a time keeps the bounded
 * region memo hot; a scattered order would evict the window it is about to need.</p>
 */
public final class CanonicalChunkPrefetchScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CanonicalChunkPrefetchScheduler.class);

    /**
     * Spiral radius in chunks.
     *
     * <p>Only the client's 9x9 visible field (81 final products) is produced speculatively.
     * Noise-based spawn selection no longer produces search arcs. Required activation neighbours
     * outside this field retain the ordinary demand path; generation inputs and final bytes are
     * unchanged.</p>
     */
    public static final int DEFAULT_RADIUS = 4;

    /**
     * Everything one sweep needs from the product source.
     *
     * <p>Production binds this to the real {@link CanonicalOriginChunkProductSource}, and nothing
     * about the shipped path changes. It exists because the behaviours that decide whether this
     * scheduler is correct — a re-centre landing mid-sweep, a coordinate whose production is
     * cancelled, a close arriving while workers are producing — are all about <i>when</i> the
     * sweep calls the seam, not about terrain bytes, and each real chunk costs hundreds of
     * milliseconds. A focused test binds a deterministic stand-in and drives those orderings
     * exactly.</p>
     */
    interface CourseSource {
        int productionSlots();

        boolean isCommitted(int chunkX, int chunkZ);

        boolean prefetch(int worldSeed, int chunkX, int chunkZ, int nextChunkX, int nextChunkZ,
                int slotIndex);

        void installCourse(CanonicalOriginChunkProductSource.ChunkDemandListener listener,
                CanonicalOriginChunkProductSource.ProductionCourse course);

        void clearCourse(CanonicalOriginChunkProductSource.ChunkDemandListener listener);

        CanonicalOriginChunkProductSource.ProductionTelemetry telemetry();

        CanonicalOriginChunkProductSource.MemoTelemetry memoTelemetry();
    }

    /** Binds the seam to the one production source; every call is the source's own. */
    private static CourseSource boundCourse(CanonicalOriginChunkProductSource source) {
        Objects.requireNonNull(source, "canonical chunk product source");
        return new CourseSource() {
            @Override public int productionSlots() { return source.productionSlots(); }

            @Override public boolean isCommitted(int chunkX, int chunkZ) {
                return source.isCommitted(chunkX, chunkZ);
            }

            @Override public boolean prefetch(int worldSeed, int chunkX, int chunkZ,
                    int nextChunkX, int nextChunkZ, int slotIndex) {
                return source.prefetch(worldSeed, chunkX, chunkZ, nextChunkX, nextChunkZ,
                        slotIndex);
            }

            @Override public void installCourse(
                    CanonicalOriginChunkProductSource.ChunkDemandListener listener,
                    CanonicalOriginChunkProductSource.ProductionCourse course) {
                source.setDemandListener(listener, course);
            }

            @Override public void clearCourse(
                    CanonicalOriginChunkProductSource.ChunkDemandListener listener) {
                source.clearDemandListener(listener);
            }

            @Override public CanonicalOriginChunkProductSource.ProductionTelemetry telemetry() {
                return source.telemetry();
            }

            @Override public CanonicalOriginChunkProductSource.MemoTelemetry memoTelemetry() {
                return source.memoTelemetry();
            }
        };
    }

    /**
     * Re-centre only after the demand leaves the field this spiral already covers.
     *
     * <p>The hysteresis distance is the spiral's own radius, not a constant: a demand the current
     * sweep is already going to reach must never restart that sweep. A fixed inner-ring distance
     * looks like hysteresis but is not one during the only window this scheduler exists for. A
     * first-generation near field is demanded in client request order across the whole 9x9 field,
     * so demands land three and four chunks from the spawn centre continuously; with a distance of
     * two, every one of them bumped the generation, {@link #sweep} aborted on its next step, and
     * the producer restarted at a new centre — measured at eighty-eight re-centres and <b>zero</b>
     * completed sweeps across one entry, with eight of a hundred and twenty-eight committed chunks
     * coming from this course. Binding the distance to the radius makes the covered field the unit
     * of hysteresis, so the sweep that entry is waiting for runs to its outer edge, and a player
     * who really leaves that field still re-centres on the step that leaves it.</p>
     */
    private final int recentreDistance;

    /** Backoff before retrying a coordinate that yielded to a waiting consumer. */
    private static final long DEFERRAL_PARK_NANOS = 2_000_000L;

    /** Committed chunks between production progress reports. */
    private static final int PROGRESS_LOG_CHUNKS = 32;

    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();

    /** Null when a focused test bound the seam directly; only {@link #boundSource} reads it. */
    private final CanonicalOriginChunkProductSource source;
    private final CourseSource production;
    private final CanonicalOriginChunkProductSource.ChunkDemandListener listener =
            this::onCanonicalChunkDemand;
    private final CanonicalOriginChunkProductSource.ProductionCourse course =
            this::coversActiveCourse;
    private final long worldId;
    private final int worldSeed;
    private final int radius;
    private final int[] spiral;
    private final Thread[] producers;
    private final Object signal = new Object();

    /** Packed centre chunk (high 32 bits X, low 32 bits Z) and the generation that owns it. */
    private final AtomicLong centre = new AtomicLong();
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong recentres = new AtomicLong();
    private final AtomicLong completedSweeps = new AtomicLong();
    /** The generation whose sweep is finished; the producers will commit nothing more for it. */
    private final AtomicLong sweptGeneration = new AtomicLong(-1L);
    /**
     * Per worker, the generation whose arcs that worker has finished.
     *
     * <p>This is what {@link #coversActiveCourse} answers from, and it must be per worker rather
     * than per sweep. A coordinate belongs to exactly one worker's arc, so the question "is the
     * course still going to reach it" is answered by that worker alone. Waiting for every worker
     * instead would keep the whole field covered until the slowest one finished, and a consumer
     * inside it would keep waiting for a coordinate whose own producer had already walked past.
     * That is a permanent wait, not a slow one: the other workers keep committing, so the
     * no-progress fallback never fires either.</p>
     */
    private final java.util.concurrent.atomic.AtomicLongArray workerSweptGeneration;
    /** Offset (dx,dz) inside the field to its step in the ring order; {@code -1} outside it. */
    private final int[] stepByOffset;
    private volatile boolean centred;
    private volatile boolean closed;
    /** Search demand advances bounded candidate arcs instead of moving the entry field. */
    private volatile boolean searchingSpawn;
    /** One contiguous arc per producer, replaced only once its candidates were consumed. */
    private final SpawnBatch[] spawnBatches;
    /** Spawn-course coordinates whose producer failed; guarded by {@link #signal}. */
    private final Set<Long> failedSpawnCourseChunks = new HashSet<>();
    /** Successful course attempts wake the one spawn-search handoff waiter; guarded by signal. */
    private long spawnCourseProgress;

    private static final class SpawnBatch {
        private final long generation;
        private final int firstCandidate;
        private final int[] chunks;
        /** Guarded by signal, like publication of the arc itself. */
        private boolean completed;

        private SpawnBatch(long generation, int firstCandidate, int count) {
            this.generation = generation;
            this.firstCandidate = firstCandidate;
            this.chunks = new int[count * 2];
            for (int index = 0; index < count; index++) {
                long chunk = WorldSpawn.candidateChunk(firstCandidate + index);
                chunks[index * 2] = (int) (chunk >> 32);
                chunks[index * 2 + 1] = (int) chunk;
            }
        }

        private int step(int chunkX, int chunkZ) {
            for (int index = 0; index < chunks.length; index += 2) {
                if (chunks[index] == chunkX && chunks[index + 1] == chunkZ) return index / 2;
            }
            return -1;
        }
    }
    /** Committed-chunk count at the last progress report; guarded by {@link #progressLock}. */
    private long reportedProducedChunks;
    private final Object progressLock = new Object();
    /** The generation whose per-worker sweep completions are being counted; guarded by this. */
    private long sweepCountingGeneration = Long.MIN_VALUE;
    private int sweepCompletedWorkers;

    public CanonicalChunkPrefetchScheduler(CanonicalOriginChunkProductSource source, long worldId,
            int worldSeed) {
        this(source, worldId, worldSeed, DEFAULT_RADIUS);
    }

    public CanonicalChunkPrefetchScheduler(CanonicalOriginChunkProductSource source, long worldId,
            int worldSeed, int radius) {
        this(source, boundCourse(source), worldId, worldSeed, radius);
    }

    /** Focused-test entry point: the sweep drives a caller-supplied seam. */
    CanonicalChunkPrefetchScheduler(CourseSource production, long worldId, int worldSeed,
            int radius) {
        this(null, Objects.requireNonNull(production, "course source"), worldId, worldSeed,
                radius);
    }

    private CanonicalChunkPrefetchScheduler(CanonicalOriginChunkProductSource source,
            CourseSource production, long worldId, int worldSeed, int radius) {
        if (radius < 0) throw new IllegalArgumentException("negative prefetch radius");
        this.source = source;
        this.production = production;
        this.worldId = worldId;
        this.worldSeed = worldSeed;
        this.radius = radius;
        this.spiral = ringOrderedSpiral(radius);
        // Edge activation asks for the one-chunk halo without the player leaving this field.
        this.recentreDistance = radius + 1;
        int span = radius * 2 + 1;
        this.stepByOffset = new int[span * span];
        java.util.Arrays.fill(this.stepByOffset, -1);
        for (int index = 0; index < this.spiral.length; index += 2) {
            int dx = this.spiral[index];
            int dz = this.spiral[index + 1];
            this.stepByOffset[(dx + radius) * span + (dz + radius)] = index / 2;
        }
        int workers = production.productionSlots();
        this.workerSweptGeneration = new java.util.concurrent.atomic.AtomicLongArray(workers);
        for (int worker = 0; worker < workers; worker++) {
            this.workerSweptGeneration.set(worker, -1L);
        }
        this.producers = new Thread[workers];
        this.spawnBatches = new SpawnBatch[workers];
        int sequence = THREAD_SEQUENCE.incrementAndGet();
        for (int worker = 0; worker < workers; worker++) {
            int ordinal = worker;
            Thread thread = new Thread(() -> produce(ordinal),
                    "canonical-chunk-prefetch-" + worldId + "-" + sequence + "-" + ordinal);
            thread.setDaemon(true);
            // Strictly below the world owner: producing ahead of need may never outrank the tick
            // that the production is meant to protect.
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            this.producers[worker] = thread;
        }
        production.installCourse(listener, course);
        for (Thread thread : this.producers) thread.start();
    }

    /**
     * Contiguous spiral steps one worker takes before the next worker's arc begins.
     *
     * <p>The spiral is not divided by {@code index % workers}. Neighbouring FEATURES targets share
     * twenty of their twenty-five post-CARVERS input chunks, and that sharing is the only reason a
     * chunk costs five input builds instead of twenty-five — targets four steps apart along a ring
     * share five. Striding the spiral one step per worker would therefore hand every worker a cold
     * input window on every target and spend the parallelism paying for it. Handing each worker a
     * contiguous run keeps its own successive targets adjacent, so only the first target of a run
     * pays a cold window, while rotating the runs keeps every worker inside the same rings at the
     * same time and preserves the centre-outward order entry actually waits on.</p>
     *
     * <p>The length is set by the memo window, not picked round. A run of {@code n} adjacent
     * targets costs {@code 25 + 5(n-1)} input builds, so the per-target cost falls towards five as
     * the run grows: eight targets pay 7.5 each, twenty-four pay 5.8. Eight was measured at 733
     * misses and 460 evictions over one entry with the mean production at 627 ms against the 270
     * to 340 ms the single-producer course held.</p>
     */
    static final int COURSE_ARC_CHUNKS = 10;

    private static final int MAX_SPAWN_BATCH_CHUNKS = 96;

    /**
     * A first cold course target can legitimately take seconds. During spawn search alone, lend
     * that target to its scheduled producer before calling the source, rather than immediately
     * entering the source's short generic consumer-fallback window. This remains bounded so a
     * stuck producer retains the source's established direct-production escape path.
     */
    private static final long SPAWN_COURSE_HANDOFF_NANOS = 5_000_000_000L;

    /** The worker that owns one spiral step, by contiguous rotating arcs. */
    private int arcOwner(int stepIndex) {
        return (stepIndex / COURSE_ARC_CHUNKS) % producers.length;
    }

    /** The product source this scheduler produces for; a rebound world needs a new scheduler. */
    public CanonicalOriginChunkProductSource boundSource() {
        return source;
    }

    /** Centres the spiral on the world spawn before the first session enters. */
    public void prewarm(int chunkX, int chunkZ) {
        recentre(chunkX, chunkZ);
    }

    /**
     * Searches the unchanged dry-land candidates using a rolling window of contiguous arcs.
     * The search consumes candidates in order; producers may only run ahead inside that window.
     * No entry-centred sweeps are started until the search has returned (or failed).
     */
    public int[] findSpawn(ChunkProductSource products) {
        synchronized (signal) {
            if (searchingSpawn) throw new IllegalStateException("spawn search already active");
            searchingSpawn = true;
            centred = false;
            generation.incrementAndGet();
            failedSpawnCourseChunks.clear();
            java.util.Arrays.fill(spawnBatches, null);
        }
        try {
            if (Boolean.getBoolean("webcraft.spawnStageTrace")) {
                return WorldSpawn.findWithProductionTelemetry(worldSeed, products,
                        this::prepareSpawnCandidate, production::telemetry);
            }
            return WorldSpawn.find(worldSeed, products, this::prepareSpawnCandidate);
        } finally {
            synchronized (signal) {
                centred = false;
                generation.incrementAndGet();
                java.util.Arrays.fill(spawnBatches, null);
                failedSpawnCourseChunks.clear();
                searchingSpawn = false;
                signal.notifyAll();
            }
        }
    }

    private void prepareSpawnCandidate(int candidate) {
        synchronized (signal) {
            if (closed) return;
            int firstArc = candidate / COURSE_ARC_CHUNKS * COURSE_ARC_CHUNKS;
            int workers = spawnCourseWorkers();
            int limit = Math.min(WorldSpawn.candidateCount(), firstArc + workers * COURSE_ARC_CHUNKS);
            boolean published = false;
            for (int first = firstArc; first < limit; first += COURSE_ARC_CHUNKS) {
                int worker = (first / COURSE_ARC_CHUNKS) % workers;
                SpawnBatch prior = spawnBatches[worker];
                if (prior == null || prior.firstCandidate < first) {
                    SpawnBatch next = new SpawnBatch(generation.get(), first,
                            Math.min(COURSE_ARC_CHUNKS, WorldSpawn.candidateCount() - first));
                    spawnBatches[worker] = next;
                    published = true;
                    if (Boolean.getBoolean("webcraft.spawnStageTrace")) {
                        log.info("SPAWN_COURSE_ARC world={} generation={} worker={} first={} count={} atNanos={}",
                                worldId, next.generation, worker, first, next.chunks.length / 2,
                                System.nanoTime());
                    }
                }
            }
            if (published) {
                failedSpawnCourseChunks.removeIf(chunk -> spawnBatchAt((int) (chunk >> 32),
                        (int) (long) chunk) == null);
                centre.set(0L);
                centred = true;
                signal.notifyAll();
            }
        }
        awaitSpawnCourseCandidate(candidate);
    }

    private int spawnCourseWorkers() {
        return Math.min(producers.length, MAX_SPAWN_BATCH_CHUNKS / COURSE_ARC_CHUNKS);
    }

    /** Caller holds signal; the window contains at most MAX_SPAWN_BATCH_CHUNKS coordinates. */
    private SpawnBatch spawnBatchAt(int chunkX, int chunkZ) {
        for (SpawnBatch batch : spawnBatches) {
            if (batch != null && batch.step(chunkX, chunkZ) >= 0) return batch;
        }
        return null;
    }

    /**
     * Lets the batch owner publish this exact spawn candidate before its ordinary source call.
     * Source failures, close, a changed generation, or the deadline all return to the unchanged
     * source fallback immediately.
     */
    private void awaitSpawnCourseCandidate(int candidate) {
        long packedCandidate = WorldSpawn.candidateChunk(candidate);
        int chunkX = (int) (packedCandidate >> 32);
        int chunkZ = (int) packedCandidate;
        long deadline = System.nanoTime() + SPAWN_COURSE_HANDOFF_NANOS;
        while (true) {
            long observedProgress;
            synchronized (signal) {
                if (!spawnCourseCanStillCommit(candidate, packedCandidate)) return;
                observedProgress = spawnCourseProgress;
            }
            if (production.isCommitted(chunkX, chunkZ)) return;
            synchronized (signal) {
                if (!spawnCourseCanStillCommit(candidate, packedCandidate)
                        || spawnCourseProgress != observedProgress) {
                    continue;
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) return;
                try {
                    signal.wait(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** Caller holds {@link #signal}. */
    private boolean spawnCourseCanStillCommit(int candidate, long packedCandidate) {
        SpawnBatch batch = spawnBatches[(candidate / COURSE_ARC_CHUNKS) % spawnCourseWorkers()];
        if (closed || !searchingSpawn || batch == null
                || candidate < batch.firstCandidate
                || candidate >= batch.firstCandidate + batch.chunks.length / 2
                || failedSpawnCourseChunks.contains(packedCandidate)) {
            return false;
        }
        return generation.get() == batch.generation && !batch.completed;
    }

    /** Chunks this scheduler committed before a consumer asked for them. */
    public long prefetchedChunks() {
        return production.telemetry().prefetchedChunks();
    }

    public long recentres() {
        return recentres.get();
    }

    public long completedSweeps() {
        return completedSweeps.get();
    }

    /** Blocks until the spiral around the current centre is fully committed, or the wait expires. */
    public boolean awaitSweep(long timeoutMillis) {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        long target = generation.get();
        while (System.nanoTime() < deadline) {
            if (closed) return false;
            if (completedSweeps.get() > 0 && generation.get() == target && sweepComplete(target)) {
                return true;
            }
            LockSupport.parkNanos(1_000_000L);
        }
        return false;
    }

    private boolean sweepComplete(long target) {
        long packed = centre.get();
        int centreX = (int) (packed >> 32);
        int centreZ = (int) packed;
        for (int index = 0; index < spiral.length; index += 2) {
            if (generation.get() != target) return false;
            if (!production.isCommitted(centreX + spiral[index],
                    centreZ + spiral[index + 1])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        production.clearCourse(listener);
        synchronized (signal) {
            signal.notifyAll();
        }
        for (Thread thread : producers) thread.interrupt();
    }

    /**
     * Consumer demand callback. It runs on the demanding thread — the world owner included — so it
     * does no work beyond one comparison and, at most, one publish plus notify.
     */
    private void onCanonicalChunkDemand(int chunkX, int chunkZ) {
        if (closed || searchingSpawn) return;
        if (centred) {
            long packed = centre.get();
            int centreX = (int) (packed >> 32);
            int centreZ = (int) packed;
            if (Math.max(Math.abs(chunkX - centreX), Math.abs(chunkZ - centreZ))
                    <= recentreDistance) {
                return;
            }
        }
        recentre(chunkX, chunkZ);
    }

    /**
     * Whether the sweep now running will commit this coordinate on its own.
     *
     * <p>The seam asks this before a consumer produces an uncommitted chunk itself: inside the
     * active sweep, the consumer waits for this producer instead of taking the production lock,
     * which keeps first generation on the ring-ordered, lookahead-overlapped course the bounded
     * memos and the throughput gate are sized for. It must therefore be true only while this
     * producer is genuinely still going to reach the coordinate — not when the scheduler is
     * closed, not before a centre exists, not outside the covered field, and not after this
     * generation's sweep already finished (a coordinate the sweep failed to produce is then
     * handed straight back to its demanding caller, which fails closed with the same
     * diagnostic).</p>
     */
    private boolean coversActiveCourse(int chunkX, int chunkZ) {
        if (closed || !centred) return false;
        long current = generation.get();
        if (sweptGeneration.get() == current) return false;
        if (searchingSpawn) {
            synchronized (signal) {
                SpawnBatch batch = spawnBatchAt(chunkX, chunkZ);
                long packed = ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
                if (batch == null || batch.generation != current
                        || failedSpawnCourseChunks.contains(packed)) {
                    return false;
                }
                return !batch.completed
                        && generation.get() == current && !closed;
            }
        }
        long packed = centre.get();
        int centreX = (int) (packed >> 32);
        int centreZ = (int) packed;
        int dx = chunkX - centreX;
        int dz = chunkZ - centreZ;
        if (Math.max(Math.abs(dx), Math.abs(dz)) > radius) return false;
        int step = stepByOffset[(dx + radius) * (radius * 2 + 1) + (dz + radius)];
        if (step < 0) return false;
        // Only the worker that owns this coordinate's arc can still commit it.
        return workerSweptGeneration.get(arcOwner(step)) != current;
    }

    private void recentre(int chunkX, int chunkZ) {
        synchronized (signal) {
            if (closed || searchingSpawn) return;
            centre.set(((long) chunkX << 32) | (chunkZ & 0xffffffffL));
            centred = true;
            recentres.incrementAndGet();
            generation.incrementAndGet();
            signal.notifyAll();
        }
    }

    private void produce(int worker) {
        long served = -1L;
        while (!closed) {
            long current;
            long packed;
            int[] offsets;
            SpawnBatch spawnArc;
            synchronized (signal) {
                current = generation.get();
                spawnArc = searchingSpawn ? spawnBatches[worker] : null;
                if (!centred || (searchingSpawn ? spawnArc == null || spawnArc.completed
                        : current == served)) {
                    if (!closed) {
                        try {
                            signal.wait(1_000L);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    continue;
                }
                packed = centre.get();
                offsets = spawnArc == null ? spiral : spawnArc.chunks;
            }
            if (spawnArc != null) {
                if (sweepSpawnArc(spawnArc, worker)) {
                    synchronized (signal) {
                        spawnArc.completed = true;
                        signal.notifyAll();
                    }
                    if (Boolean.getBoolean("webcraft.spawnStageTrace")) {
                        log.info("SPAWN_COURSE_ARC_DONE world={} generation={} worker={} first={} atNanos={}",
                                worldId, spawnArc.generation, worker, spawnArc.firstCandidate,
                                System.nanoTime());
                    }
                }
                continue;
            }
            int centreX = (int) (packed >> 32);
            int centreZ = (int) packed;
            if (sweep(current, centreX, centreZ, worker, offsets)) {
                served = current;
                // Publish this worker's completion before the sweep tally, so a consumer released
                // by the tally can never observe an arc as still covered.
                workerSweptGeneration.set(worker, current);
                noteWorkerSwept(current);
                notifySpawnCourseWaiter();
            }
        }
    }

    private boolean ownsSpawnArc(SpawnBatch batch, int worker) {
        synchronized (signal) {
            return !closed && searchingSpawn && generation.get() == batch.generation
                    && spawnBatches[worker] == batch;
        }
    }

    /** Each producer keeps its slot and adjacent lookahead; other arcs need not finish first. */
    private boolean sweepSpawnArc(SpawnBatch batch, int worker) {
        for (int index = 0; index < batch.chunks.length; index += 2) {
            int x = batch.chunks[index];
            int z = batch.chunks[index + 1];
            int next = index + 2 < batch.chunks.length ? index + 2 : index;
            while (ownsSpawnArc(batch, worker)) {
                try {
                    if (production.prefetch(worldSeed, x, z, batch.chunks[next],
                            batch.chunks[next + 1], worker)) {
                        noteSpawnCourseProgress();
                        break;
                    }
                } catch (RuntimeException failure) {
                    if (isExpectedCancellation(failure)) {
                        log.debug("정본 청크 선행 생성 취소: chunk={},{} ({})", x, z, failure.getMessage());
                    } else {
                        log.warn("정본 청크 선행 생성 실패: chunk={},{}", x, z, failure);
                    }
                    noteSpawnCourseFailure(batch.generation, x, z);
                    break;
                }
                LockSupport.parkNanos(DEFERRAL_PARK_NANOS);
            }
            reportProductionProgress();
            if (!ownsSpawnArc(batch, worker)) return false;
        }
        return true;
    }

    /**
     * Records that one worker finished its arcs of a generation's spiral.
     *
     * <p>A sweep is only complete, and the course only stops covering its field, once <i>every</i>
     * worker has finished. Declaring completion on the first worker to finish would hand the
     * coordinates the other workers are still producing back to their demanding callers, which is
     * exactly the admission rule {@link #coversActiveCourse} exists to prevent.</p>
     */
    private synchronized void noteWorkerSwept(long owningGeneration) {
        if (generation.get() != owningGeneration || closed) return;
        if (sweepCountingGeneration != owningGeneration) {
            sweepCountingGeneration = owningGeneration;
            sweepCompletedWorkers = 0;
        }
        if (++sweepCompletedWorkers == producers.length) {
            sweptGeneration.set(owningGeneration);
            completedSweeps.incrementAndGet();
        }
    }

    /**
     * 선행 생성이 <b>취소</b>되어 끝났는지 판별합니다.
     *
     * <p>퇴장·재중심·스케줄러 종료는 진행 중인 생성 작업을 인터럽트로 끊습니다. 그 결과는
     * {@link java.util.concurrent.CancellationException} 이나 {@link InterruptedException} 으로
     * 올라오는데, 앞의 것은 {@code IllegalStateException} 의 자식이라 일반 실행 예외와 함께
     * 잡힙니다. 원인 사슬 전체를 훑는 이유는 생성 시임이 이 둘을 자기 예외로 감싸 던지기
     * 때문입니다.
     *
     * <p>인터럽트 상태 자체는 판별에 쓰지 않습니다. 예외가 이 스레드까지 올라오는 동안 상태가
     * 이미 소비돼 사라질 수 있어, 같은 사건이 어떤 때는 취소로 어떤 때는 실패로 보이게 됩니다.
     */
    static boolean isExpectedCancellation(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.util.concurrent.CancellationException
                    || cause instanceof InterruptedException
                    || cause instanceof java.nio.channels.ClosedByInterruptException) {
                return true;
            }
            if (cause.getCause() == cause) break;
        }
        return false;
    }

    /**
     * Runs one worker's arcs of the spiral. Returns true when they were all committed without the
     * centre moving underneath them.
     */
    private boolean sweep(long owningGeneration, int centreX, int centreZ, int worker,
            int[] offsets) {
        for (int index = 0; index < offsets.length; index += 2) {
            int step = index / 2;
            if (arcOwner(step) != worker) continue;
            int chunkX = centreX + offsets[index];
            int chunkZ = centreZ + offsets[index + 1];
            // The spiral is this scheduler's own order, so the coordinate it will ask for next is
            // known before this one is produced. Handing it to the seam lets the next target's
            // post-CARVERS inputs be built while this target's FEATURES pass runs; the last step
            // of a sweep names itself and stays serial.
            int nextIndex = index + 2;
            boolean ownsNext = nextIndex < offsets.length && arcOwner(nextIndex / 2) == worker;
            int nextChunkX = ownsNext ? centreX + offsets[nextIndex] : chunkX;
            int nextChunkZ = ownsNext ? centreZ + offsets[nextIndex + 1] : chunkZ;
            while (!closed && generation.get() == owningGeneration) {
                try {
                    if (production.prefetch(worldSeed, chunkX, chunkZ, nextChunkX,
                            nextChunkZ, worker)) {
                        noteSpawnCourseProgress();
                        break;
                    }
                } catch (RuntimeException productionFailure) {
                    // A coordinate that cannot be produced ahead of need is not a scheduler
                    // failure: its demanding caller still produces it on the existing seam and
                    // fails closed there with the same diagnostic.
                    if (isExpectedCancellation(productionFailure)) {
                        // 플레이어가 나가면 이 월드의 선행 생성은 곧바로 취소된다. 그건 정상
                        // 종료 경로이지 결함이 아니므로 스택을 남기지 않는다. 스택을 찍으면
                        // 평범한 접속 종료마다 ERROR 처럼 보이는 덩어리가 로그에 남아, 진짜
                        // 생성 실패를 가린다.
                        log.debug("정본 청크 선행 생성 취소: chunk={},{} ({})",
                                chunkX, chunkZ, productionFailure.getMessage());
                    } else {
                        log.warn("정본 청크 선행 생성 실패: chunk={},{}", chunkX, chunkZ,
                                productionFailure);
                    }
                    noteSpawnCourseFailure(owningGeneration, chunkX, chunkZ);
                    break;
                }
                LockSupport.parkNanos(DEFERRAL_PARK_NANOS);
            }
            reportProductionProgress();
            if (closed || generation.get() != owningGeneration) return false;
        }
        return true;
    }

    private void noteSpawnCourseProgress() {
        if (!searchingSpawn) return;
        synchronized (signal) {
            if (!searchingSpawn) return;
            spawnCourseProgress++;
            signal.notifyAll();
        }
    }

    private void noteSpawnCourseFailure(long owningGeneration, int chunkX, int chunkZ) {
        if (!searchingSpawn) return;
        synchronized (signal) {
            SpawnBatch batch = spawnBatchAt(chunkX, chunkZ);
            if (searchingSpawn && batch != null && batch.generation == owningGeneration
                    && generation.get() == owningGeneration) {
                failedSpawnCourseChunks.add(((long) chunkX << 32) | (chunkZ & 0xffffffffL));
            }
            signal.notifyAll();
        }
    }

    private void notifySpawnCourseWaiter() {
        if (!searchingSpawn) return;
        synchronized (signal) {
            if (searchingSpawn) signal.notifyAll();
        }
    }

    /**
     * Reports how this world's canonical production is actually running, every {@link
     * #PROGRESS_LOG_CHUNKS} committed chunks.
     *
     * <p>A first-generation near field is the one window where production rate decides how long a
     * player waits, and the numbers that explain that rate are not derivable from outside the
     * seam: how many committed chunks came from this scheduler's ring-ordered lookahead course
     * rather than a consumer thread producing in client request order, how long consumers spent
     * queued behind the single production lock, and whether the bounded start-decision and
     * post-CARVERS memos were being hit or thrashed while they ran. Once the field is on record
     * the counters stop advancing and this goes quiet, so it costs a handful of lines per world
     * and nothing at all afterwards.</p>
     */
    private void reportProductionProgress() {
        CanonicalOriginChunkProductSource.ProductionTelemetry telemetry =
                production.telemetry();
        long produced = telemetry.producedChunks();
        synchronized (progressLock) {
            if (produced - reportedProducedChunks < PROGRESS_LOG_CHUNKS) return;
            reportedProducedChunks = produced;
        }
        CanonicalOriginChunkProductSource.MemoTelemetry memos = production.memoTelemetry();
        log.info("정본 청크 생산 진행: world={} 생산={} 평균={}ms 최대={}ms 선행={} 소비자={} 유예={} "
                        + "무잠금재생={} 소비자대기={}ms 최대대기={}ms 코스대기={} 평균코스대기={}ms "
                        + "코스포기={} 재중심={} 스윕={} "
                        + "시작메모 적중={} 실패={} 축출={} 상주지역={} 입력메모 적중={} 실패={} 축출={}",
                worldId, produced,
                String.format("%.1f", telemetry.meanProductionMillis()),
                telemetry.maxProductionNanos() / 1_000_000L,
                telemetry.prefetchedChunks(), produced - telemetry.prefetchedChunks(),
                telemetry.deferredPrefetches(), telemetry.replayedWithoutLock(),
                telemetry.consumerLockWaitNanos() / 1_000_000L,
                telemetry.maxConsumerLockWaitNanos() / 1_000_000L,
                telemetry.courseAwaitedChunks(),
                String.format("%.1f", telemetry.meanCourseAwaitMillis()),
                telemetry.courseAwaitAbandoned(),
                recentres.get(), completedSweeps.get(),
                memos.startHits(), memos.startMisses(),
                memos.startEvictions(), memos.residentRegions(),
                memos.inputHits(), memos.inputMisses(),
                memos.inputEvictions());
    }

    /**
     * Centre-outward ring order, deterministic within each ring.
     *
     * <p>Public because it is the shipped demand order: {@code
     * CanonicalOriginThroughputGateTest} derives its course from this very method so the
     * throughput gate measures the order production emits, not a hand-written lookalike
     * (AGENTS 10l/10m).</p>
     */
    public static int[] ringOrderedSpiral(int radius) {
        List<int[]> offsets = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
        for (int ring = 0; ring <= radius; ring++) {
            for (int dz = -ring; dz <= ring; dz++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) offsets.add(new int[] {dx, dz});
                }
            }
        }
        int[] packed = new int[offsets.size() * 2];
        for (int index = 0; index < offsets.size(); index++) {
            packed[index * 2] = offsets.get(index)[0];
            packed[index * 2 + 1] = offsets.get(index)[1];
        }
        return packed;
    }
}
