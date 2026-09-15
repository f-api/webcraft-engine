package com.gameexpert.terrain;

import com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState;
import com.gameexpert.terrain.mc.feature.Mc263CanonicalFeaturesProducerSkeleton;
import com.gameexpert.terrain.mc.feature.Mc263CanonicalGenerationProduct;
import com.gameexpert.terrain.mc.feature.Mc263FeaturesRegion;
import com.gameexpert.terrain.mc.feature.Mc263CanonicalStructureExecutors;
import com.gameexpert.terrain.mc.feature.Mc263WorldGenRegionRandom;
import com.gameexpert.terrain.mc.feature.Mc263PostCarversFeaturesRegionBuilder;
import com.gameexpert.terrain.mc.feature.Mc263PostprocessResolver;
import com.gameexpert.terrain.mc.loot.Mc263ProductionContextCatalog;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import com.gameexpert.terrain.mc.structure.Mc263Beardifier;
import com.gameexpert.terrain.mc.Mc263PostCarversAccumulator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bridge from the canonical post-CARVERS cut to the writable FEATURES region.
 *
 * <p>The direct product method is current-only and requires a complete STR carrier and POST
 * authority context. The legacy region helper remains useful for focused leaf tests.</p>
 */
public final class Mc263FeaturesRegionBridge {

    /**
     * Resident post-CARVERS chunk inputs in a {@link RegionMemo} by default.
     *
     * <p>A 3x3 activation wall consumes the 7x7 = 49 distinct chunks around it; 64 keeps that wall
     * plus its immediate advance resident.</p>
     */
    public static final int DEFAULT_MEMO_CHUNKS = 64;

    /**
     * Independent post-CARVERS input builders.
     *
     * <p>One input chunk is built by a freshly constructed {@link ChunkGenerator} over the seed,
     * the chunk coordinates and — when a carrier is bound — that chunk's own beardifier carrier.
     * The build shares no mutable state with any other input chunk, so building the missing inputs
     * of one FEATURES target concurrently cannot change a byte: the assembled region input is
     * still filled in the pinned chunk-X-major, chunk-Z-minor order and the memo still receives
     * exactly the same misses in that order. A warm target needs five fresh inputs and a cold one
     * twenty-five, so a small bounded pool is enough; it stays below the world owner's priority so
     * production can never outrank the authority tick.</p>
     */
    private static final int INPUT_BUILDER_THREADS = Math.max(1,
            Math.min(5, Runtime.getRuntime().availableProcessors() / 2));
    private static final int INPUT_BUILDER_QUEUE_CAPACITY = 256;
    private static final long INPUT_CONTEXT_CLOSE_TIMEOUT_NANOS =
            TimeUnit.SECONDS.toNanos(2);
    private static final AtomicInteger INPUT_CONTEXT_SEQUENCE = new AtomicInteger();
    private static final AtomicLong PARALLEL_INPUT_BUILDS = new AtomicLong();
    private static final AtomicLong INLINE_INPUT_BUILDS = new AtomicLong();

    /** Creates one explicitly owned input-builder context for an exporter worker. */
    public static InputBuilderContext newInputBuilderContext() {
        return InputBuilderContext.create();
    }

    /** Creates a context with a bounded worker count, primarily for deterministic focused tests. */
    public static InputBuilderContext newInputBuilderContext(int threadCount) {
        return InputBuilderContext.create(threadCount);
    }

    /** Creates a context around an already selected worker-local evidence context. */
    public static InputBuilderContext newInputBuilderContext(
            ChunkGenerator.PostCarversEvidenceContext evidenceContext) {
        return InputBuilderContext.create(evidenceContext);
    }

    /** Named factory alias for callers that prefer the resource's type in the method name. */
    public static InputBuilderContext createInputBuilderContext() {
        return newInputBuilderContext();
    }

    /** Evidence-context overload for callers that construct worker state before the executor. */
    public static InputBuilderContext createInputBuilderContext(
            ChunkGenerator.PostCarversEvidenceContext evidenceContext) {
        return newInputBuilderContext(evidenceContext);
    }

    /** Named factory alias retained for concise try-with-resources call sites. */
    public static InputBuilderContext inputBuilderContext() {
        return newInputBuilderContext();
    }

    /** Post-CARVERS input chunks built on worker-owned bounded contexts. */
    public static long parallelInputBuilds() {
        return PARALLEL_INPUT_BUILDS.get();
    }

    /** Post-CARVERS input chunks built on the calling thread. */
    public static long inlineInputBuilds() {
        return INLINE_INPUT_BUILDS.get();
    }

    /** A worker-owned bounded executor and its worker-local post-CARVERS evidence context. */
    public static final class InputBuilderContext implements AutoCloseable {
        private final Object lifecycle = new Object();
        private final ThreadPoolExecutor executor;
        private final ChunkGenerator.PostCarversEvidenceContext postCarversEvidenceContext;
        private final Set<TrackedFuture> trackedFutures = ConcurrentHashMap.newKeySet();
        private final ThreadLocal<Boolean> onBuilderThread =
                ThreadLocal.withInitial(() -> false);
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicLong submittedTasks = new AtomicLong();
        private final AtomicLong startedTasks = new AtomicLong();
        private final AtomicLong completedTasks = new AtomicLong();
        private final AtomicLong cancelledTasks = new AtomicLong();
        private final AtomicLong failedTasks = new AtomicLong();
        private final AtomicLong rejectedTasks = new AtomicLong();
        private final AtomicLong parallelBuilds = new AtomicLong();
        private final AtomicLong inlineBuilds = new AtomicLong();

        private InputBuilderContext(ChunkGenerator.PostCarversEvidenceContext evidenceContext,
                int threadCount) {
            postCarversEvidenceContext = Objects.requireNonNull(
                    evidenceContext, "post-CARVERS evidence context");
            if (threadCount <= 0 || threadCount > INPUT_BUILDER_THREADS) {
                throw new IllegalArgumentException("input-builder thread count outside 1.."
                        + INPUT_BUILDER_THREADS + ": " + threadCount);
            }
            int contextId = INPUT_CONTEXT_SEQUENCE.incrementAndGet();
            ThreadFactory threadFactory = new ThreadFactory() {
                private final AtomicInteger sequence = new AtomicInteger();

                @Override
                public Thread newThread(Runnable task) {
                    Thread thread = new Thread(task,
                            "canonical-post-carvers-input-context-" + contextId + "-"
                                    + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    thread.setPriority(Thread.NORM_PRIORITY - 1);
                    return thread;
                }
            };
            executor = new ThreadPoolExecutor(threadCount, threadCount, 30L, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(INPUT_BUILDER_QUEUE_CAPACITY), threadFactory,
                    new ThreadPoolExecutor.AbortPolicy());
            executor.allowCoreThreadTimeOut(true);
        }

        public static InputBuilderContext create() {
            return create(INPUT_BUILDER_THREADS);
        }

        public static InputBuilderContext create(int threadCount) {
            return new InputBuilderContext(new ChunkGenerator.PostCarversEvidenceContext(),
                    threadCount);
        }

        public static InputBuilderContext create(
                ChunkGenerator.PostCarversEvidenceContext evidenceContext) {
            return new InputBuilderContext(evidenceContext, INPUT_BUILDER_THREADS);
        }

        public static InputBuilderContext open() {
            return create();
        }

        public ChunkGenerator.PostCarversEvidenceContext postCarversEvidenceContext() {
            return postCarversEvidenceContext;
        }

        public ChunkGenerator.PostCarversEvidenceContext evidenceContext() {
            return postCarversEvidenceContext;
        }

        public boolean isClosed() {
            return closed.get();
        }

        public boolean closed() {
            return isClosed();
        }

        public boolean isTerminated() {
            return executor.isTerminated();
        }

        public boolean hasLiveThreads() {
            return executor.getPoolSize() != 0;
        }

        public int trackedFutureCount() {
            return trackedFutures.size();
        }

        public InputBuilderTelemetry telemetry() {
            return new InputBuilderTelemetry(submittedTasks.get(), startedTasks.get(),
                    completedTasks.get(), cancelledTasks.get(), failedTasks.get(),
                    rejectedTasks.get(), parallelBuilds.get(), inlineBuilds.get(),
                    trackedFutures.size(), executor.getActiveCount(), executor.getQueue().size(),
                    closed.get(), executor.isTerminated());
        }

        private Future<?> submit(Runnable action) {
            Objects.requireNonNull(action, "input-builder action");
            synchronized (lifecycle) {
                if (closed.get()) {
                    throw new IllegalStateException("input-builder context is closed");
                }
                TrackedFuture future = new TrackedFuture(action);
                trackedFutures.add(future);
                submittedTasks.incrementAndGet();
                try {
                    executor.execute(future);
                    return future;
                } catch (RuntimeException failure) {
                    submittedTasks.decrementAndGet();
                    trackedFutures.remove(future);
                    rejectedTasks.incrementAndGet();
                    throw failure;
                }
            }
        }

        private boolean onBuilderThread() {
            return Boolean.TRUE.equals(onBuilderThread.get());
        }

        private void runOnBuilderThread(Runnable action) {
            boolean nested = onBuilderThread();
            if (!nested) onBuilderThread.set(true);
            try {
                action.run();
            } finally {
                if (!nested) onBuilderThread.remove();
            }
        }

        private void recordParallelBuild() {
            parallelBuilds.incrementAndGet();
            PARALLEL_INPUT_BUILDS.incrementAndGet();
        }

        private void recordInlineBuild() {
            inlineBuilds.incrementAndGet();
            INLINE_INPUT_BUILDS.incrementAndGet();
        }

        private void cancelTrackedFutures() {
            for (TrackedFuture future : trackedFutures) future.cancel(true);
        }

        @Override
        public void close() {
            synchronized (lifecycle) {
                if (!closed.getAndSet(true)) postCarversEvidenceContext.cancel();
                cancelTrackedFutures();
                executor.shutdownNow();
            }
            boolean interrupted = false;
            long deadline = System.nanoTime() + INPUT_CONTEXT_CLOSE_TIMEOUT_NANOS;
            while (!executor.isTerminated()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                try {
                    if (executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) break;
                } catch (InterruptedException interruption) {
                    interrupted = true;
                    synchronized (lifecycle) {
                        cancelTrackedFutures();
                        executor.shutdownNow();
                    }
                    break;
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
            if (!executor.isTerminated()) {
                throw new IllegalStateException("input-builder workers survived close");
            }
        }

        private final class TrackedFuture extends FutureTask<Void> {
            private TrackedFuture(Runnable action) {
                super(() -> {
                    startedTasks.incrementAndGet();
                    try {
                        action.run();
                    } catch (RuntimeException | Error failure) {
                        failedTasks.incrementAndGet();
                        throw failure;
                    }
                }, null);
            }

            @Override
            protected void done() {
                trackedFutures.remove(this);
                completedTasks.incrementAndGet();
                if (isCancelled()) cancelledTasks.incrementAndGet();
            }
        }
    }

    /** Immutable counters and lifecycle state for one input-builder context. */
    public record InputBuilderTelemetry(long submittedTasks, long startedTasks,
            long completedTasks, long cancelledTasks, long failedTasks, long rejectedTasks,
            long parallelBuilds, long inlineBuilds, int trackedFutures, int activeThreads,
            int queuedTasks, boolean closed, boolean terminated) {

        public long submitted() { return submittedTasks; }
        public long started() { return startedTasks; }
        public long completed() { return completedTasks; }
        public long cancelled() { return cancelledTasks; }
        public int active() { return activeThreads; }
        public int queued() { return queuedTasks; }
        public int tracked() { return trackedFutures; }
    }

    private Mc263FeaturesRegionBridge() {
    }

    /**
     * Bounded per-{@code (worldSeed, chunkX, chunkZ)} LRU over finished post-CARVERS chunk inputs.
     *
     * <p>Neighbouring FEATURES targets overlap in 20 of their 25 input chunks, and one chunk's
     * post-CARVERS state is a pure function of the seed and that chunk's coordinates: the
     * beardifier comes from the carrier's reference chunk for exactly that chunk, which every
     * carrier whose window covers it decides identically. {@link
     * Mc263PostCarversFeaturesRegionBuilder.ChunkInput} is deeply immutable (its snapshot, biome
     * keys and tick lists are copied in and out), so sharing one instance between region inputs
     * cannot change the produced bytes.</p>
     */
    public static final class RegionMemo {
        private final int capacity;
        private final LinkedHashMap<Key, Mc263PostCarversFeaturesRegionBuilder.ChunkInput> chunks;
        private long hits;
        private long misses;
        private long evictions;

        private RegionMemo(int capacity) {
            if (capacity <= 0) throw new IllegalArgumentException("non-positive memo capacity");
            this.capacity = capacity;
            this.chunks = new LinkedHashMap<>(16, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Key,
                        Mc263PostCarversFeaturesRegionBuilder.ChunkInput> eldest) {
                    boolean evict = size() > RegionMemo.this.capacity;
                    if (evict) evictions++;
                    return evict;
                }
            };
        }

        public static RegionMemo bounded(int capacity) { return new RegionMemo(capacity); }

        public int residentChunks() { return chunks.size(); }
        public long hits() { return hits; }
        public long misses() { return misses; }
        public long evictions() { return evictions; }

        private Mc263PostCarversFeaturesRegionBuilder.ChunkInput cached(
                int worldSeed, int chunkX, int chunkZ) {
            Mc263PostCarversFeaturesRegionBuilder.ChunkInput input =
                    chunks.get(new Key(worldSeed, chunkX, chunkZ));
            if (input == null) misses++; else hits++;
            return input;
        }

        private void store(int worldSeed, int chunkX, int chunkZ,
                Mc263PostCarversFeaturesRegionBuilder.ChunkInput input) {
            chunks.put(new Key(worldSeed, chunkX, chunkZ), input);
        }

        private record Key(int worldSeed, int chunkX, int chunkZ) { }
    }

    /** Builds target +/-2 in deterministic chunk-X-major, chunk-Z-minor order. */
    public static Mc263FeaturesRegion buildPostCarversRegion(
            int worldSeed, int targetChunkX, int targetChunkZ) {
        requireRepresentableTarget(targetChunkX, "X");
        requireRepresentableTarget(targetChunkZ, "Z");

        List<Mc263FeaturesRegion.CarversChunk> chunks =
                new ArrayList<>(Mc263FeaturesRegion.INPUT_CHUNK_COUNT);
        for (int chunkX = targetChunkX - Mc263FeaturesRegion.INPUT_RADIUS;
                chunkX <= targetChunkX + Mc263FeaturesRegion.INPUT_RADIUS; chunkX++) {
            for (int chunkZ = targetChunkZ - Mc263FeaturesRegion.INPUT_RADIUS;
                    chunkZ <= targetChunkZ + Mc263FeaturesRegion.INPUT_RADIUS; chunkZ++) {
                ChunkGenerator.PostCarversChunkData data =
                        ChunkGenerator.generatePostCarversChunkData(worldSeed, chunkX, chunkZ);
                chunks.add(new Mc263FeaturesRegion.CarversChunk(
                        data.chunkX(), data.chunkZ(), data.blockIds(), data.biomeKeys(),
                        data.stateOverrides(), data.postprocessMarks(), data.scheduledBlockTicks(),
                        data.scheduledFluidTicks()));
            }
        }
        return new Mc263FeaturesRegion(targetChunkX, targetChunkZ, chunks,
                Mc263FeaturesRegionBridge::worldSurface,
                Mc263FeaturesRegionBridge::oceanFloor,
                Mc263FeaturesRegionBridge::motionBlocking);
    }

    /** Builds the immutable 25-chunk input consumed by the canonical FEATURES product. */
    public static Mc263PostCarversFeaturesRegionBuilder.RegionInput buildPostCarversInput(
            int worldSeed, int targetChunkX, int targetChunkZ) {
        return buildPostCarversInput(worldSeed, targetChunkX, targetChunkZ, null, null);
    }

    /**
     * Builds the 25-chunk input under a complete STR carrier, optionally reusing already built
     * chunks from a {@link RegionMemo}.
     *
     * @param memo bounded chunk cache, or {@code null} to build every input chunk fresh
     */
    public static Mc263PostCarversFeaturesRegionBuilder.RegionInput buildPostCarversInput(
            int worldSeed, int targetChunkX, int targetChunkZ,
            Mc263StructureCarrier structureCarrier, RegionMemo memo) {
        return startPostCarversInput(worldSeed, targetChunkX, targetChunkZ, structureCarrier, memo)
                .finish();
    }

    /** Builds input with a caller-owned worker context. */
    public static Mc263PostCarversFeaturesRegionBuilder.RegionInput buildPostCarversInput(
            InputBuilderContext inputBuilderContext, int worldSeed, int targetChunkX,
            int targetChunkZ) {
        return buildPostCarversInput(inputBuilderContext, worldSeed, targetChunkX, targetChunkZ,
                null, null);
    }

    /** Builds input with a caller-owned worker context, carrier and memo. */
    public static Mc263PostCarversFeaturesRegionBuilder.RegionInput buildPostCarversInput(
            InputBuilderContext inputBuilderContext, int worldSeed, int targetChunkX,
            int targetChunkZ, Mc263StructureCarrier structureCarrier, RegionMemo memo) {
        return startPostCarversInput(inputBuilderContext, worldSeed, targetChunkX, targetChunkZ,
                structureCarrier, memo).finish();
    }

    /** Context-last compatibility overload for existing argument-building call sites. */
    public static Mc263PostCarversFeaturesRegionBuilder.RegionInput buildPostCarversInput(
            int worldSeed, int targetChunkX, int targetChunkZ,
            Mc263StructureCarrier structureCarrier, RegionMemo memo,
            InputBuilderContext inputBuilderContext) {
        return buildPostCarversInput(inputBuilderContext, worldSeed, targetChunkX, targetChunkZ,
                structureCarrier, memo);
    }

    /**
     * Starts the 25-chunk input build and returns before its missing chunks are finished.
     *
     * <p>AGENTS rule 10l: a streaming wall pays one target's FEATURES on its producer thread while
     * the worker-owned input context runs, and the next target's five new column chunks are already
     * decided work. Starting them here and joining them in {@link PendingRegionInput#finish()}
     * overlaps that context with the producer's FEATURES pass. Every memo lookup and every memo
     * store still happens on the caller's thread, in the same pinned order, so the memo is neither
     * shared across threads nor reordered; only the pure per-chunk builds move.</p>
     */
    public static PendingRegionInput startPostCarversInput(int worldSeed, int targetChunkX,
            int targetChunkZ, Mc263StructureCarrier structureCarrier, RegionMemo memo) {
        InputBuilderContext inputBuilderContext = newInputBuilderContext();
        try {
            return startPostCarversInput(inputBuilderContext, worldSeed, targetChunkX, targetChunkZ,
                    structureCarrier, memo, true);
        } catch (RuntimeException failure) {
            closeAfterStartFailure(inputBuilderContext, failure);
            throw failure;
        }
    }

    /** Starts input builds on the explicitly supplied worker-owned context. */
    public static PendingRegionInput startPostCarversInput(
            InputBuilderContext inputBuilderContext, int worldSeed, int targetChunkX,
            int targetChunkZ, Mc263StructureCarrier structureCarrier, RegionMemo memo) {
        return startPostCarversInput(inputBuilderContext, worldSeed, targetChunkX, targetChunkZ,
                structureCarrier, memo, false);
    }

    /** Context-last compatibility overload for existing argument-building call sites. */
    public static PendingRegionInput startPostCarversInput(int worldSeed, int targetChunkX,
            int targetChunkZ, Mc263StructureCarrier structureCarrier, RegionMemo memo,
            InputBuilderContext inputBuilderContext) {
        return startPostCarversInput(inputBuilderContext, worldSeed, targetChunkX, targetChunkZ,
                structureCarrier, memo);
    }

    private static PendingRegionInput startPostCarversInput(
            InputBuilderContext inputBuilderContext, int worldSeed, int targetChunkX,
            int targetChunkZ, Mc263StructureCarrier structureCarrier, RegionMemo memo,
            boolean ownsContext) {
        Objects.requireNonNull(inputBuilderContext, "input-builder context");
        requireRepresentableTarget(targetChunkX, "X");
        requireRepresentableTarget(targetChunkZ, "Z");
        int span = Mc263FeaturesRegion.INPUT_RADIUS * 2 + 1;
        int slots = span * span;
        int[] coordinates = new int[slots * 2];
        Mc263PostCarversFeaturesRegionBuilder.ChunkInput[] inputs =
                new Mc263PostCarversFeaturesRegionBuilder.ChunkInput[slots];
        List<Integer> missingSlots = new ArrayList<>(slots);
        int slot = 0;
        // Pass 1 keeps the pinned chunk-X-major, chunk-Z-minor lookup order, so the memo observes
        // exactly the hits and misses it observed when every input was built inline.
        for (int chunkX = targetChunkX - Mc263FeaturesRegion.INPUT_RADIUS;
                chunkX <= targetChunkX + Mc263FeaturesRegion.INPUT_RADIUS; chunkX++) {
            for (int chunkZ = targetChunkZ - Mc263FeaturesRegion.INPUT_RADIUS;
                    chunkZ <= targetChunkZ + Mc263FeaturesRegion.INPUT_RADIUS; chunkZ++) {
                coordinates[slot * 2] = chunkX;
                coordinates[slot * 2 + 1] = chunkZ;
                Mc263PostCarversFeaturesRegionBuilder.ChunkInput reused =
                        memo == null ? null : memo.cached(worldSeed, chunkX, chunkZ);
                if (reused != null) inputs[slot] = reused;
                else missingSlots.add(slot);
                slot++;
            }
        }

        // Pass 2 builds the missing inputs. Each build owns a fresh generator over pure inputs, so
        // running them concurrently cannot change one produced byte.
        List<Future<?>> pending = startMissingInputs(inputBuilderContext, worldSeed,
                structureCarrier, coordinates, inputs, missingSlots);
        return new PendingRegionInput(worldSeed, targetChunkX, targetChunkZ, memo, coordinates,
                inputs, missingSlots, pending, inputBuilderContext, ownsContext);
    }

    /** A started 25-chunk input build; {@link #finish()} joins it on the starting thread. */
    public static final class PendingRegionInput {
        private final int worldSeed;
        private final int targetChunkX;
        private final int targetChunkZ;
        private final RegionMemo memo;
        private final int[] coordinates;
        private final Mc263PostCarversFeaturesRegionBuilder.ChunkInput[] inputs;
        private final List<Integer> missingSlots;
        private final List<Future<?>> pending;
        private final InputBuilderContext inputBuilderContext;
        private final boolean ownsContext;
        private final AtomicBoolean terminal = new AtomicBoolean();
        private final AtomicBoolean cancellationRequested = new AtomicBoolean();
        private final AtomicBoolean completed = new AtomicBoolean();

        private PendingRegionInput(int worldSeed, int targetChunkX, int targetChunkZ,
                RegionMemo memo, int[] coordinates,
                Mc263PostCarversFeaturesRegionBuilder.ChunkInput[] inputs,
                List<Integer> missingSlots, List<Future<?>> pending,
                InputBuilderContext inputBuilderContext, boolean ownsContext) {
            this.worldSeed = worldSeed;
            this.targetChunkX = targetChunkX;
            this.targetChunkZ = targetChunkZ;
            this.memo = memo;
            this.coordinates = coordinates.clone();
            this.inputs = inputs;
            this.missingSlots = List.copyOf(missingSlots);
            this.pending = List.copyOf(pending);
            this.inputBuilderContext = inputBuilderContext;
            this.ownsContext = ownsContext;
        }

        public int targetChunkX() { return targetChunkX; }

        public int targetChunkZ() { return targetChunkZ; }

        /** Cancels every input future and prevents a partial input from entering the memo. */
        public void cancel() {
            cancellationRequested.set(true);
            if (!terminal.compareAndSet(false, true)) {
                if (!completed.get()) cancelInputBuilds(pending);
                return;
            }
            try {
                cancelInputBuilds(pending);
                if (ownsContext) inputBuilderContext.close();
            } finally {
                completed.set(true);
            }
        }

        /** Joins the started builds and publishes the misses, in the pinned order. */
        public Mc263PostCarversFeaturesRegionBuilder.RegionInput finish() {
            if (!terminal.compareAndSet(false, true)) {
                throw new IllegalStateException("post-CARVERS input already finished");
            }
            RuntimeException failure = null;
            Mc263PostCarversFeaturesRegionBuilder.RegionInput result = null;
            boolean restoreInterrupt = false;
            try {
                awaitInputBuilds(pending);
                if (cancellationRequested.get() || inputBuilderContext.isClosed()) {
                    throw new CancellationException("input-builder context is closed");
                }
                for (int slot = 0; slot < inputs.length; slot++) {
                    if (inputs[slot] == null) {
                        throw new IllegalStateException("post-CARVERS input build produced no chunk "
                                + slot);
                    }
                }
                // Pass 3 publishes the misses to the memo in the same pinned order the inline
                // build used. It is deliberately after every Future.get succeeds.
                if (memo != null) {
                    for (int missing : missingSlots) {
                        memo.store(worldSeed, coordinates[missing * 2],
                                coordinates[missing * 2 + 1], inputs[missing]);
                    }
                }
                List<Mc263PostCarversFeaturesRegionBuilder.ChunkInput> chunks =
                        new ArrayList<>(inputs.length);
                for (Mc263PostCarversFeaturesRegionBuilder.ChunkInput input : inputs) {
                    chunks.add(input);
                }
                result = new Mc263PostCarversFeaturesRegionBuilder.RegionInput(
                        new Mc263PostCarversFeaturesRegionBuilder.TargetMetadata(
                                worldSeed, targetChunkX, targetChunkZ), chunks);
            } catch (InterruptedException interruption) {
                cancelInputBuilds(pending);
                CancellationException cancellation = new CancellationException(
                        "post-CARVERS input build interrupted");
                cancellation.initCause(interruption);
                failure = cancellation;
                restoreInterrupt = true;
            } catch (RuntimeException problem) {
                cancelInputBuilds(pending);
                failure = problem;
            } finally {
                if (ownsContext) {
                    try {
                        inputBuilderContext.close();
                    } catch (RuntimeException closeFailure) {
                        if (failure == null) failure = closeFailure;
                        else failure.addSuppressed(closeFailure);
                    }
                }
                if (restoreInterrupt) Thread.currentThread().interrupt();
                completed.set(true);
            }
            if (failure != null) throw failure;
            return result;
        }
    }

    private static List<Future<?>> startMissingInputs(InputBuilderContext inputBuilderContext,
            int worldSeed,
            Mc263StructureCarrier structureCarrier,
            int[] coordinates, Mc263PostCarversFeaturesRegionBuilder.ChunkInput[] inputs,
            List<Integer> missingSlots) {
        if (missingSlots.isEmpty()) return List.of();
        // Only a nested build stays inline. A single missing input is still a whole post-CARVERS
        // chunk — aquifer, density and noise for 16 columns — and it is exactly the case a warm
        // streaming target hits most often, so building it on the producer thread cancels the
        // lookahead overlap the seam exists for (AGENTS 10l). Handing it to the pool costs one
        // task dispatch and lets it run against this target's FEATURES pass.
        if (inputBuilderContext.onBuilderThread()) {
            for (int missing : missingSlots) {
                inputBuilderContext.recordInlineBuild();
                inputs[missing] = buildInputChunk(
                        inputBuilderContext.postCarversEvidenceContext(), worldSeed,
                        structureCarrier, coordinates[missing * 2], coordinates[missing * 2 + 1]);
            }
            return List.of();
        }
        List<Future<?>> pending = new ArrayList<>(missingSlots.size());
        try {
            for (int missing : missingSlots) {
                int chunkX = coordinates[missing * 2];
                int chunkZ = coordinates[missing * 2 + 1];
                int target = missing;
                pending.add(inputBuilderContext.submit(() ->
                        inputBuilderContext.runOnBuilderThread(() -> {
                            inputBuilderContext.recordParallelBuild();
                            inputs[target] = buildInputChunk(
                                    inputBuilderContext.postCarversEvidenceContext(),
                                    worldSeed, structureCarrier, chunkX, chunkZ);
                        })));
            }
        } catch (RuntimeException | Error failure) {
            cancelInputBuilds(pending);
            throw failure;
        }
        return pending;
    }

    private static void awaitInputBuilds(List<Future<?>> pending) throws InterruptedException {
        // Report the first failure in pinned coordinate order so a broken carrier reports the same
        // chunk it reported when the inputs were built one at a time.
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        RuntimeException failure = null;
        for (int index = 0; index < pending.size(); index++) {
            try {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                pending.get(index).get();
            } catch (CancellationException cancellation) {
                throw cancellation;
            } catch (ExecutionException execution) {
                RuntimeException cause = inputFailure(execution.getCause());
                if (failure == null) failure = cause;
            }
        }
        if (failure != null) throw failure;
    }

    private static RuntimeException inputFailure(Throwable failure) {
        if (failure instanceof Error error) throw error;
        if (failure instanceof CancellationException cancellation) return cancellation;
        if (failure instanceof RuntimeException runtime) return runtime;
        return new IllegalStateException("post-CARVERS input build failed", failure);
    }

    private static void cancelInputBuilds(List<? extends Future<?>> pending) {
        for (Future<?> future : pending) future.cancel(true);
    }

    private static void closeAfterStartFailure(InputBuilderContext inputBuilderContext,
            RuntimeException failure) {
        try {
            inputBuilderContext.close();
        } catch (RuntimeException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static Mc263PostCarversFeaturesRegionBuilder.ChunkInput buildInputChunk(
            ChunkGenerator.PostCarversEvidenceContext postCarversEvidenceContext,
            int worldSeed, Mc263StructureCarrier structureCarrier, int chunkX, int chunkZ) {
        Objects.requireNonNull(postCarversEvidenceContext, "post-CARVERS evidence context");
        ChunkGenerator.PostCarversChunkData data;
        if (structureCarrier == null) {
            data = ChunkGenerator.generatePostCarversChunkData(
                    postCarversEvidenceContext, worldSeed, chunkX, chunkZ);
        } else {
            Mc263StructureCarrier.ChunkReferences references = structureCarrier
                    .referenceChunk(chunkX, chunkZ).orElse(null);
            if (references == null) {
                throw new IllegalArgumentException(
                        "missing canonical structure references for chunk "
                                + chunkX + "," + chunkZ);
            }
            Mc263Beardifier beardifier = Mc263Beardifier.fromCarrier(
                    structureCarrier.beardifierCarrier(references));
            data = ChunkGenerator.generatePostCarversChunkData(
                    postCarversEvidenceContext, worldSeed, chunkX, chunkZ, beardifier);
        }
        Mc263PostCarversAccumulator accumulator = new Mc263PostCarversAccumulator(
                chunkX, chunkZ, data.blockIds());
        for (Mc263FeaturesRegion.StateOverride override : data.stateOverrides()) {
            if (!accumulator.setExactState(override.localX(), override.blockY(),
                    override.localZ(), override.state())) {
                throw new IllegalStateException("post-CARVERS state override rejected");
            }
        }
        for (Mc263FeaturesRegion.PostprocessMark mark : data.postprocessMarks()) {
            if (!accumulator.markPosForPostprocessing(mark.localX(), mark.blockY(),
                    mark.localZ())) {
                throw new IllegalStateException("post-CARVERS mark rejected");
            }
        }
        return new Mc263PostCarversFeaturesRegionBuilder.ChunkInput(
                accumulator.snapshot(), data.biomeKeys(),
                data.scheduledBlockTicks(), data.scheduledFluidTicks(),
                Mc263PostCarversFeaturesRegionBuilder.LightInput.absent());
    }

    /** Produces the non-null canonical final carrier and STR-bound commit product. */
    public static Mc263CanonicalGenerationProduct generateCanonicalProduct(int worldSeed,
            int targetChunkX, int targetChunkZ, Mc263StructureCarrier structureCarrier,
            Mc263PostprocessResolver.ActivationContext activation) {
        throw missingProductionContextAuthority();
    }

    /**
     * Legacy production signature. It cannot build or mutate a region without context authority.
     */
    public static Mc263CanonicalGenerationProduct generateCanonicalProduct(int worldSeed,
            int targetChunkX, int targetChunkZ, Mc263StructureCarrier structureCarrier,
            Mc263PostprocessResolver.ActivationContext activation, RegionMemo memo) {
        throw missingProductionContextAuthority();
    }

    /** Produces one product with a fresh authority from the pinned immutable catalog provider. */
    public static Mc263CanonicalGenerationProduct generateCanonicalProduct(int worldSeed,
            int targetChunkX, int targetChunkZ, Mc263StructureCarrier structureCarrier,
            Mc263ProductionContextCatalog.Provider productionContextProvider,
            Mc263PostprocessResolver.ActivationContext activation) {
        return generateCanonicalProduct(worldSeed, targetChunkX, targetChunkZ, structureCarrier,
                productionContextProvider, activation, null);
    }

    /** Produces one product with a provider and an optional bounded post-CARVERS memo. */
    public static Mc263CanonicalGenerationProduct generateCanonicalProduct(int worldSeed,
            int targetChunkX, int targetChunkZ, Mc263StructureCarrier structureCarrier,
            Mc263ProductionContextCatalog.Provider productionContextProvider,
            Mc263PostprocessResolver.ActivationContext activation, RegionMemo memo) {
        Mc263CanonicalFeaturesProducerSkeleton.ProductionContextAuthority authority =
                requireAuthority(productionContextProvider, worldSeed, targetChunkX,
                        targetChunkZ);
        return generateCanonicalProduct(worldSeed, targetChunkX, targetChunkZ, structureCarrier,
                authority, activation, memo);
    }

    /**
     * Explicit authenticated-authority seam for focused fixtures. Production callers use the
     * provider overload so every upstream product receives a newly minted authority.
     */
    public static Mc263CanonicalGenerationProduct generateCanonicalProduct(int worldSeed,
            int targetChunkX, int targetChunkZ, Mc263StructureCarrier structureCarrier,
            Mc263CanonicalFeaturesProducerSkeleton.ProductionContextAuthority authority,
            Mc263PostprocessResolver.ActivationContext activation) {
        return generateCanonicalProduct(worldSeed, targetChunkX, targetChunkZ, structureCarrier,
                authority, activation, null);
    }

    /** Authenticated fixture seam with an optional bounded post-CARVERS memo. */
    public static Mc263CanonicalGenerationProduct generateCanonicalProduct(int worldSeed,
            int targetChunkX, int targetChunkZ, Mc263StructureCarrier structureCarrier,
            Mc263CanonicalFeaturesProducerSkeleton.ProductionContextAuthority authority,
            Mc263PostprocessResolver.ActivationContext activation, RegionMemo memo) {
        Objects.requireNonNull(authority, "production context authority");
        Objects.requireNonNull(structureCarrier, "structure carrier");
        Mc263PostCarversFeaturesRegionBuilder.RegionInput input = buildPostCarversInput(
                worldSeed, targetChunkX, targetChunkZ, structureCarrier, memo);
        return generateCanonicalProductFromInput(worldSeed, targetChunkX, targetChunkZ,
                structureCarrier, authority, activation, input);
    }

    /**
     * Legacy prebuilt-input signature. Target identity is still checked, then missing authority
     * fails before an upstream product or writable FEATURES region can be created.
     */
    public static Mc263CanonicalGenerationProduct generateCanonicalProductFromInput(int worldSeed,
            int targetChunkX, int targetChunkZ, Mc263StructureCarrier structureCarrier,
            Mc263PostprocessResolver.ActivationContext activation,
            Mc263PostCarversFeaturesRegionBuilder.RegionInput input) {
        requireInputTarget(worldSeed, targetChunkX, targetChunkZ, input);
        throw missingProductionContextAuthority();
    }

    /** Produces over a prebuilt input with a fresh exact-target catalog authority. */
    public static Mc263CanonicalGenerationProduct generateCanonicalProductFromInput(int worldSeed,
            int targetChunkX, int targetChunkZ, Mc263StructureCarrier structureCarrier,
            Mc263ProductionContextCatalog.Provider productionContextProvider,
            Mc263PostprocessResolver.ActivationContext activation,
            Mc263PostCarversFeaturesRegionBuilder.RegionInput input) {
        requireInputTarget(worldSeed, targetChunkX, targetChunkZ, input);
        Mc263CanonicalFeaturesProducerSkeleton.ProductionContextAuthority authority =
                requireAuthority(productionContextProvider, worldSeed, targetChunkX,
                        targetChunkZ);
        return generateCanonicalProductFromInput(worldSeed, targetChunkX, targetChunkZ,
                structureCarrier, authority, activation, input);
    }

    /** Explicit authenticated-authority seam for prebuilt focused fixtures. */
    public static Mc263CanonicalGenerationProduct generateCanonicalProductFromInput(int worldSeed,
            int targetChunkX, int targetChunkZ, Mc263StructureCarrier structureCarrier,
            Mc263CanonicalFeaturesProducerSkeleton.ProductionContextAuthority authority,
            Mc263PostprocessResolver.ActivationContext activation,
            Mc263PostCarversFeaturesRegionBuilder.RegionInput input) {
        requireInputTarget(worldSeed, targetChunkX, targetChunkZ, input);
        Objects.requireNonNull(authority, "production context authority");
        Objects.requireNonNull(structureCarrier, "structure carrier");
        Mc263CanonicalFeaturesProducerSkeleton.UpstreamProduct upstream =
                Mc263CanonicalFeaturesProducerSkeleton.UpstreamProduct.bind(input,
                        structureCarrier,
                        Mc263WorldGenRegionRandom.atCenterChunk(worldSeed,
                                targetChunkX, targetChunkZ).snapshot(),
                        authority);
        return Mc263CanonicalFeaturesProducerSkeleton.produce(upstream, defaultHeightmaps(),
                activation, Mc263CanonicalStructureExecutors.completeRegistry());
    }

    private static void requireInputTarget(int worldSeed, int targetChunkX, int targetChunkZ,
            Mc263PostCarversFeaturesRegionBuilder.RegionInput input) {
        Objects.requireNonNull(input, "post-CARVERS region input");
        if (input.target().worldSeed() != worldSeed
                || input.target().chunkX() != targetChunkX
                || input.target().chunkZ() != targetChunkZ) {
            throw new IllegalArgumentException(
                    "FEATURES RegionInput target identity does not match explicit arguments");
        }
    }

    private static Mc263CanonicalFeaturesProducerSkeleton.ProductionContextAuthority
            requireAuthority(Mc263ProductionContextCatalog.Provider provider, int worldSeed,
                    int targetChunkX, int targetChunkZ) {
        Objects.requireNonNull(provider, "production context catalog provider");
        return Objects.requireNonNull(provider.authorityFor(
                worldSeed, targetChunkX, targetChunkZ),
                "production context catalog provider returned no authority");
    }

    private static IllegalStateException missingProductionContextAuthority() {
        return new IllegalStateException(
                "canonical FEATURES production requires an authenticated production context "
                        + "catalog provider or explicit test authority");
    }

    private static boolean worldSurface(int blockId) {
        return !Mc263FeatureBlockState.defaultForId(blockId).isAir();
    }

    private static boolean oceanFloor(int blockId) {
        return Mc263FeatureBlockState.defaultForId(blockId).isSolid();
    }

    private static boolean motionBlocking(int blockId) {
        return Mc263FeatureBlockState.defaultForId(blockId).isSolid();
    }

    private static Mc263PostCarversFeaturesRegionBuilder.HeightmapPredicates defaultHeightmaps() {
        return new Mc263PostCarversFeaturesRegionBuilder.HeightmapPredicates(
                Mc263FeaturesRegionBridge::worldSurface,
                Mc263FeaturesRegionBridge::oceanFloor,
                Mc263FeaturesRegionBridge::motionBlocking);
    }

    private static void requireRepresentableTarget(int coordinate, String axis) {
        long minimumChunk = (long) coordinate - Mc263FeaturesRegion.INPUT_RADIUS;
        long maximumChunk = (long) coordinate + Mc263FeaturesRegion.INPUT_RADIUS;
        long minimumBlock = minimumChunk * Blocks.CHUNK_X;
        long maximumBlock = maximumChunk * Blocks.CHUNK_X + Blocks.CHUNK_X - 1L;
        if (minimumChunk < Integer.MIN_VALUE || maximumChunk > Integer.MAX_VALUE
                || minimumBlock < Integer.MIN_VALUE || maximumBlock > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("FEATURES target chunk " + axis
                    + " cannot be represented as block coordinates: " + coordinate);
        }
    }
}
