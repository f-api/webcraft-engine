package com.gameexpert.terrain;

import com.gameexpert.terrain.mc.feature.Mc263CanonicalGenerationProduct;
import com.gameexpert.terrain.mc.feature.Mc263CanonicalFeaturesProducerSkeleton;
import com.gameexpert.terrain.mc.feature.Mc263PostCarversFeaturesRegionBuilder;
import com.gameexpert.terrain.mc.feature.Mc263PostprocessResolver;
import com.gameexpert.terrain.mc.loot.Mc263ProductionContextCatalog;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrierOrigin;
import com.gameexpert.terrain.persistence.CanonicalWorldgenStore;
import com.gameexpert.world.WorldBaseline;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The one production chunk seam: replay a committed canonical product, otherwise assemble the
 * structure carrier, produce the canonical FEATURES product, commit it, and serve it.
 *
 * <p>The canonical product is the sole output. There is no legacy raster fallback: a missing or
 * uncarriable structure carrier, a foreign world identity, and a coordinate mismatch all fail
 * closed. The bounded {@link Mc263StructureCarrierOrigin.RegionMemo} is retained per source so the
 * 21x21 start window of consecutive chunk demands amortizes across one activation wall, and the
 * bounded {@link Mc263FeaturesRegionBridge.RegionMemo} likewise amortizes the 25 post-CARVERS
 * input chunks each FEATURES target consumes.</p>
 *
 * <p><b>Threading</b>: one instance serves one world, and production runs on a fixed set of
 * {@linkplain #productionSlots() production slots}. A slot owns everything a chunk production
 * mutates — the bounded start-decision memo, the bounded post-CARVERS input memo, the carrier
 * {@code WorldContext}, and the lookahead slot — so holding one slot's lock is what keeps that
 * state single-threaded. Two slots may therefore produce two different coordinates at once.
 * Replaying an <i>already committed</i> chunk takes no slot at all: the committed product is
 * durable and immutable, so a caller that only needs bytes already on record is never queued
 * behind a chunk someone else is producing. That distinction is the whole point of producing
 * ahead of need — see {@link #prefetch}.</p>
 *
 * <p><b>Determinism</b>: a canonical product is a pure function of the seed and the coordinate,
 * so which slot produced it cannot change a byte — the memos only cache decided work, and a cold
 * memo costs time, never identity. Commits are serialized behind one lock and the store verifies
 * the fingerprint and the payload of an already-present coordinate, so even a coordinate produced
 * twice by two slots is committed once and proven identical.</p>
 */
public final class CanonicalOriginChunkProductSource implements ChunkProductSource {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(CanonicalOriginChunkProductSource.class);

    /** Regions are 16 chunks square; 9 resident regions cover a 3x3 activation wall and its halo. */
    public static final int DEFAULT_MEMO_REGIONS = 9;

    /**
     * Concurrent production slots per world.
     *
     * <p>Producing one canonical chunk is a few hundred milliseconds of pure CPU, and a first
     * generation near field is eighty-one of them, so the single production lock made entry cost
     * the sum rather than the maximum. Every slot is a full copy of the mutable production state,
     * so half the available cores (at least eight producers) share the bounded input-builder
     * pools. One additional slot stays reserved for demand. CPU slots are released before the
     * single settlement worker performs a bounded store transaction.</p>
     */
    public static final int DEFAULT_PRODUCTION_SLOTS = 1 + Math.max(8,
            Runtime.getRuntime().availableProcessors() / 2);

    /** Poll interval while a consumer waits for the production course to reach its coordinate. */
    private static final long COURSE_POLL_NANOS = 500_000L;

    /** Poll interval while a consumer waits for any production slot to free. */
    private static final long SLOT_POLL_NANOS = 200_000L;

    /**
     * A consumer stops waiting for the course when the whole source has committed nothing for
     * this long. The course is an accelerator, never an authority over liveness: a producer that
     * died, threw on this coordinate, or was closed must never strand the thread a player is
     * waiting on, so the consumer falls back to producing the chunk itself exactly as before.
     */
    private static final long COURSE_STALL_NANOS = 5_000_000_000L;

    /**
     * The hard ceiling on how long one consumer lends its coordinate to the course.
     *
     * <p>{@link #COURSE_STALL_NANOS} was the whole liveness rule when one producer swept the whole
     * spiral: if that producer was alive it was working towards this coordinate, and if it had
     * stopped committing anything the consumer took over. Several producers break that inference.
     * Each owns a different part of the course, so the others keep committing — and keep resetting
     * the stall deadline — while the producer that owns <i>this</i> coordinate is arbitrarily far
     * from it. The consumer then waits forever on a course that is making progress everywhere
     * except where it is waiting, which is exactly how a snapshot preparer stops delivering.
     *
     * <p>So the wait is also bounded outright. Handing the course a quarter second is still worth
     * it — that is most of a production, and the course produces on the amortized ring order — but
     * past it the consumer produces its own chunk exactly as it would with no course at all.</p>
     */
    private static final long COURSE_AWAIT_BUDGET_NANOS = 250_000_000L;

    /** Notified on every consumer chunk demand; prefetch production never fires it. */
    @FunctionalInterface
    public interface ChunkDemandListener {
        /**
         * Runs on the demanding thread — including the world owner thread — so an implementation
         * must be non-blocking and allocation-free.
         */
        void onCanonicalChunkDemand(int chunkX, int chunkZ);
    }

    /**
     * The production course currently sweeping this world, installed by the prefetch scheduler.
     *
     * <p>Answers one question for a consumer that finds its chunk uncommitted: <i>is the producer
     * course going to reach this coordinate on its own?</i> When it is, the consumer must not
     * produce the chunk itself. See {@link #generate}.</p>
     */
    @FunctionalInterface
    public interface ProductionCourse {
        /**
         * True while the active sweep will commit this coordinate without being asked. Runs on
         * demanding threads, so it must be non-blocking and allocation-free.
         */
        boolean coversActiveCourse(int chunkX, int chunkZ);
    }

    /** Immutable measurement of what this source produced, replayed, and waited for. */
    public record ProductionTelemetry(long producedChunks, long producedNanos,
            long maxProductionNanos, long replayedWithoutLock, long consumerLockWaitNanos,
            long maxConsumerLockWaitNanos, long prefetchedChunks, long deferredPrefetches,
            long courseAwaitedChunks, long courseAwaitNanos, long courseAwaitAbandoned) {

        public double meanProductionMillis() {
            return producedChunks == 0 ? 0.0 : producedNanos / 1e6 / producedChunks;
        }

        /** Mean wall time a consumer spent letting the course produce its chunk. */
        public double meanCourseAwaitMillis() {
            return courseAwaitedChunks == 0 ? 0.0 : courseAwaitNanos / 1e6 / courseAwaitedChunks;
        }
    }

    private final CanonicalStoreChunkProductSource replay;
    private final CanonicalWorldgenStore store;
    private final long worldId;
    private final com.gameexpert.authority.versioned.IsolatedChunkProductSource isolated;
    private final com.gameexpert.world.WorldGenerationProfile generationProfile;

    @Override public com.gameexpert.world.WorldGenerationProfile generationProfile() {
        return generationProfile;
    }
    private final Mc263PostprocessResolver.ActivationContext activation;
    private final Mc263ProductionContextCatalog.Provider productionContextProvider;
    private final Integer detachedSeed;
    private final Mc263FeaturesRegionBridge.InputBuilderContext inputBuilderContext;
    /**
     * The bounded input-builder pools this source owns when the caller supplied none.
     *
     * <p>Without it, {@link Mc263FeaturesRegionBridge#startPostCarversInput(int, int, int,
     * com.gameexpert.terrain.mc.structure.Mc263StructureCarrier,
     * Mc263FeaturesRegionBridge.RegionMemo)} builds a fresh pool for every single chunk. One
     * producer made that invisible — five input threads, one at a time. With several slots
     * producing at once it multiplies: four concurrent productions meant four pools and twenty
     * input threads against a dozen cores, and the oversubscription doubled the mean production
     * this parallelism exists to cut. The source instead shares at most eight input threads,
     * bounded to half the available cores, regardless of its production-slot count. Two existing
     * bounded contexts are needed above five threads: changing the bridge's compiled factory
     * would change persisted container-loot producer identities. Its factory, queue limits and
     * the caller-owned exporter context remain untouched.</p>
     *
     * <p>Its threads are daemon and time out when idle, so a source that is dropped without a
     * close leaves nothing running.</p>
     */
    private volatile Mc263FeaturesRegionBridge.InputBuilderContext[] ownedInputBuilderContexts;
    /**
     * The production slots of this world, indexed as the prefetch course addresses them.
     *
     * <p>Slot 0 is the one every legacy entry point uses, so a caller that does not know about
     * slots behaves exactly as it did when there was a single production lock.</p>
     */
    private final ProductionSlot[] slots;
    /**
     * Serializes store publication off the CPU slots. Completed callers see only durable bytes.
     */
    private final CanonicalChunkCommitBatcher settlements;
    /** One complete compute/publication attempt per coordinate, shared by all demanders. */
    private final ConcurrentHashMap<Long, CompletableFuture<Void>> inFlight = new ConcurrentHashMap<>();
    /**
     * Consumers currently blocked because every slot was busy.
     *
     * <p>It is deliberately one counter for the whole source rather than one per slot. A consumer
     * here is usually the world owner, whose turn is budgeted in tens of milliseconds, and the
     * only thing that gets it a slot quickly is every producer standing down. A per-slot counter
     * looks equivalent and is not: the waiter is pinned to whichever slot it announced on, and the
     * other producers keep taking fresh coordinates, so the wait becomes the tail of one chunk
     * plus everything the other slots choose to start. Measured on entry that ran the owner's wait
     * from zero to six seconds and stopped snapshot delivery outright, because a turn spent
     * queueing for a production slot is a turn that sends nothing.</p>
     */
    private final AtomicInteger waitingConsumers = new AtomicInteger();
    private final AtomicLong producedChunks = new AtomicLong();
    private final AtomicLong producedNanos = new AtomicLong();
    private final AtomicLong maxProductionNanos = new AtomicLong();
    private final AtomicLong replayedWithoutLock = new AtomicLong();
    private final AtomicLong consumerLockWaitNanos = new AtomicLong();
    private final AtomicLong maxConsumerLockWaitNanos = new AtomicLong();
    private final AtomicLong prefetchedChunks = new AtomicLong();
    private final AtomicLong deferredPrefetches = new AtomicLong();
    private final AtomicLong courseAwaitedChunks = new AtomicLong();
    private final AtomicLong courseAwaitNanos = new AtomicLong();
    private final AtomicLong courseAwaitAbandoned = new AtomicLong();
    static final int KNOWN_COMMIT_HINT_CAPACITY = 1024;
    /** Readers never take the writers' FIFO lock or consult the persistent store. */
    private final java.util.Set<Long> knownCommitHints =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.ArrayDeque<Long> knownCommitHintOrder = new java.util.ArrayDeque<>();
    private volatile ChunkDemandListener demandListener;
    private volatile ProductionCourse productionCourse;
    private volatile Integer boundWorldSeed;

    public CanonicalOriginChunkProductSource(CanonicalWorldgenStore store, long worldId,
            Mc263PostprocessResolver.ActivationContext activation) {
        this(store, worldId, null, activation, DEFAULT_MEMO_REGIONS, null, null);
    }

    public CanonicalOriginChunkProductSource(CanonicalWorldgenStore store, long worldId,
            Mc263PostprocessResolver.ActivationContext activation, int memoRegions) {
        this(store, worldId, null, activation, memoRegions, null, null);
    }

    /** Creates a production source with the mandatory immutable context-catalog provider. */
    public CanonicalOriginChunkProductSource(CanonicalWorldgenStore store, long worldId,
            Mc263PostprocessResolver.ActivationContext activation,
            Mc263ProductionContextCatalog.Provider productionContextProvider) {
        this(store, worldId, null, activation, DEFAULT_MEMO_REGIONS, null,
                productionContextProvider);
    }

    /** Provider-backed source with an explicit structure memo capacity. */
    public CanonicalOriginChunkProductSource(CanonicalWorldgenStore store, long worldId,
            Mc263PostprocessResolver.ActivationContext activation, int memoRegions,
            Mc263ProductionContextCatalog.Provider productionContextProvider) {
        this(store, worldId, null, activation, memoRegions, null,
                productionContextProvider);
    }

    /** Creates a seeded source that reuses the caller-owned input-builder context. */
    public CanonicalOriginChunkProductSource(CanonicalWorldgenStore store, long worldId,
            int detachedSeed, Mc263PostprocessResolver.ActivationContext activation,
            Mc263FeaturesRegionBridge.InputBuilderContext inputBuilderContext) {
        this(store, worldId, detachedSeed, activation, DEFAULT_MEMO_REGIONS,
                inputBuilderContext, null);
    }

    /** Seeded source with a caller-owned input context and mandatory catalog provider. */
    public CanonicalOriginChunkProductSource(CanonicalWorldgenStore store, long worldId,
            int detachedSeed, Mc263PostprocessResolver.ActivationContext activation,
            Mc263FeaturesRegionBridge.InputBuilderContext inputBuilderContext,
            Mc263ProductionContextCatalog.Provider productionContextProvider) {
        this(store, worldId, detachedSeed, activation, DEFAULT_MEMO_REGIONS,
                inputBuilderContext, productionContextProvider);
    }

    /** Creates a source with an injected input-builder context and late seed binding. */
    public CanonicalOriginChunkProductSource(CanonicalWorldgenStore store, long worldId,
            Mc263PostprocessResolver.ActivationContext activation,
            Mc263FeaturesRegionBridge.InputBuilderContext inputBuilderContext) {
        this(store, worldId, null, activation, DEFAULT_MEMO_REGIONS, inputBuilderContext,
                null);
    }

    /** Late-seed source with caller-owned input context and mandatory catalog provider. */
    public CanonicalOriginChunkProductSource(CanonicalWorldgenStore store, long worldId,
            Mc263PostprocessResolver.ActivationContext activation,
            Mc263FeaturesRegionBridge.InputBuilderContext inputBuilderContext,
            Mc263ProductionContextCatalog.Provider productionContextProvider) {
        this(store, worldId, null, activation, DEFAULT_MEMO_REGIONS, inputBuilderContext,
                productionContextProvider);
    }

    /** Context-backed source with an explicit structure memo capacity. */
    public CanonicalOriginChunkProductSource(CanonicalWorldgenStore store, long worldId,
            Mc263PostprocessResolver.ActivationContext activation, int memoRegions,
            Mc263FeaturesRegionBridge.InputBuilderContext inputBuilderContext) {
        this(store, worldId, null, activation, memoRegions, inputBuilderContext, null);
    }

    /** Context-backed source with explicit memo capacity and mandatory catalog provider. */
    public CanonicalOriginChunkProductSource(CanonicalWorldgenStore store, long worldId,
            Mc263PostprocessResolver.ActivationContext activation, int memoRegions,
            Mc263FeaturesRegionBridge.InputBuilderContext inputBuilderContext,
            Mc263ProductionContextCatalog.Provider productionContextProvider) {
        this(store, worldId, null, activation, memoRegions, inputBuilderContext,
                productionContextProvider);
    }

    /** Seeded context-backed source with an explicit structure memo capacity. */
    public CanonicalOriginChunkProductSource(CanonicalWorldgenStore store, long worldId,
            int detachedSeed, Mc263PostprocessResolver.ActivationContext activation,
            int memoRegions, Mc263FeaturesRegionBridge.InputBuilderContext inputBuilderContext) {
        this(store, worldId, Integer.valueOf(detachedSeed), activation, memoRegions,
                inputBuilderContext, null);
    }

    /** Fully specified seeded production source. */
    public CanonicalOriginChunkProductSource(CanonicalWorldgenStore store, long worldId,
            int detachedSeed, Mc263PostprocessResolver.ActivationContext activation,
            int memoRegions, Mc263FeaturesRegionBridge.InputBuilderContext inputBuilderContext,
            Mc263ProductionContextCatalog.Provider productionContextProvider) {
        this(store, worldId, Integer.valueOf(detachedSeed), activation, memoRegions,
                inputBuilderContext, productionContextProvider);
    }

    public CanonicalOriginChunkProductSource(CanonicalWorldgenStore store, long worldId,
            int seed, com.gameexpert.world.WorldGenerationProfile profile, long gameTimeMcTicks) {
        this(store, worldId, Integer.valueOf(seed), null, DEFAULT_MEMO_REGIONS, null, null,
                com.gameexpert.world.WorldGenerationProfiles.requireSupported(profile), gameTimeMcTicks);
    }

    private CanonicalOriginChunkProductSource(CanonicalWorldgenStore store, long worldId,
            Integer detachedSeed, Mc263PostprocessResolver.ActivationContext activation,
            int memoRegions, Mc263FeaturesRegionBridge.InputBuilderContext inputBuilderContext,
            Mc263ProductionContextCatalog.Provider productionContextProvider) {
        this(store, worldId, detachedSeed, activation, memoRegions, inputBuilderContext,
                productionContextProvider, null, 0L);
    }

    private CanonicalOriginChunkProductSource(CanonicalWorldgenStore store, long worldId,
            Integer detachedSeed, Mc263PostprocessResolver.ActivationContext activation,
            int memoRegions, Mc263FeaturesRegionBridge.InputBuilderContext inputBuilderContext,
            Mc263ProductionContextCatalog.Provider productionContextProvider,
            com.gameexpert.world.WorldGenerationProfile profile, long gameTimeMcTicks) {
        this.productionContextProvider = profile == null ? Objects.requireNonNull(productionContextProvider,
                "production context catalog provider") : null;
        this.generationProfile = profile == null
                ? com.gameexpert.world.WorldGenerationProfiles.CURRENT : profile;
        this.isolated = profile == null ? null : new com.gameexpert.authority.versioned.IsolatedChunkProductSource(
                store, worldId, Objects.requireNonNull(detachedSeed), profile, gameTimeMcTicks);
        this.store = Objects.requireNonNull(store, "canonical worldgen store");
        this.settlements = new CanonicalChunkCommitBatcher(store, worldId);
        this.replay = new CanonicalStoreChunkProductSource(store, worldId, generationProfile);
        this.worldId = worldId;
        this.activation = profile == null ? Objects.requireNonNull(activation, "POST activation context") : null;
        this.detachedSeed = detachedSeed;
        this.inputBuilderContext = inputBuilderContext;
        this.slots = new ProductionSlot[DEFAULT_PRODUCTION_SLOTS];
        for (int index = 0; index < this.slots.length; index++) {
            this.slots[index] = new ProductionSlot(index, memoRegions);
        }
    }

    /**
     * Slots the prefetch course may sweep on, which is one fewer than this source owns.
     *
     * <p>The held-back slot is what keeps a consumer off the queue. A consumer here is usually the
     * world owner, and a canonical chunk is hundreds of milliseconds — sometimes three seconds on
     * a cold sampler — so a demand that has to wait for a prefetch production to end costs the
     * owner turn its whole budget many times over. Standing the producers down is not enough:
     * whichever chunk is already in flight still has to finish. Measured on entry, that put the
     * owner's wait at 2.4 seconds even with every producer yielding. One slot no course worker
     * ever takes makes the demand path wait for nothing.</p>
     */
    public int productionSlots() {
        return isolated == null ? slots.length - 1 : 1;
    }

    /** The first slot's bounded start-decision memo; the slot a single-threaded caller uses. */
    public Mc263StructureCarrierOrigin.RegionMemo regionMemo() {
        return slots[0].memo;
    }

    /** The first slot's bounded post-CARVERS input memo. */
    public Mc263FeaturesRegionBridge.RegionMemo postCarversMemo() {
        return slots[0].postCarversMemo;
    }

    /** Summed memo counters across every production slot. Diagnostics only. */
    public record MemoTelemetry(long startHits, long startMisses, long startEvictions,
            int residentRegions, long inputHits, long inputMisses, long inputEvictions) { }

    /**
     * Adds up what every slot's memos did. The counters are plain fields mutated by the slot that
     * owns them, so a reader outside that slot sees a recent value rather than an exact one; they
     * exist to say whether the window was hot or thrashing, which a race cannot change.
     */
    public MemoTelemetry memoTelemetry() {
        long startHits = 0;
        long startMisses = 0;
        long startEvictions = 0;
        int residentRegions = 0;
        long inputHits = 0;
        long inputMisses = 0;
        long inputEvictions = 0;
        for (ProductionSlot slot : slots) {
            startHits += slot.memo.hits();
            startMisses += slot.memo.misses();
            startEvictions += slot.memo.evictions();
            residentRegions += slot.memo.residentRegions();
            inputHits += slot.postCarversMemo.hits();
            inputMisses += slot.postCarversMemo.misses();
            inputEvictions += slot.postCarversMemo.evictions();
        }
        return new MemoTelemetry(startHits, startMisses, startEvictions, residentRegions,
                inputHits, inputMisses, inputEvictions);
    }

    /** Installs the single prefetch scheduler that follows consumer demand on this world. */
    public synchronized void setDemandListener(ChunkDemandListener listener) {
        setDemandListener(listener, null);
    }

    /**
     * Installs the prefetch scheduler together with the course it sweeps, so a consumer demanding
     * a coordinate that course already owns hands it over instead of racing it. A {@code null}
     * course keeps the pre-course behaviour: every consumer produces its own uncommitted chunk.
     */
    public synchronized void setDemandListener(ChunkDemandListener listener,
            ProductionCourse course) {
        this.demandListener = listener;
        this.productionCourse = course;
    }

    /**
     * Detaches the listener only while it is still the installed one, so a scheduler closing after
     * its replacement has already attached cannot silently unhook the live one.
     */
    public synchronized void clearDemandListener(ChunkDemandListener expected) {
        if (demandListener == expected) {
            demandListener = null;
            productionCourse = null;
        }
    }

    /**
     * True once this world has a committed canonical product for the chunk.
     *
     * <p>Asks the store for the bit, not the row. Every prefetch tests this before producing, and
     * loading the row's three longblob carriers to answer it dominated the production workers on
     * a saved world's join.</p>
     */
    public boolean isCommitted(int chunkX, int chunkZ) {
        boolean committed = store.isCommitted(worldId, chunkX, chunkZ);
        if (committed) rememberCommittedChunk(chunkX, chunkZ);
        return committed;
    }

    @Override
    public boolean hasKnownCommittedChunk(int chunkX, int chunkZ) {
        return knownCommitHints.contains(((long) chunkX << 32) | (chunkZ & 0xffffffffL));
    }

    private void rememberCommittedChunk(int chunkX, int chunkZ) {
        long key = ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
        synchronized (knownCommitHintOrder) {
            if (knownCommitHints.contains(key)) return;
            if (knownCommitHintOrder.size() == KNOWN_COMMIT_HINT_CAPACITY) {
                knownCommitHints.remove(knownCommitHintOrder.removeFirst());
            }
            knownCommitHintOrder.addLast(key);
            knownCommitHints.add(key);
        }
    }

    public ProductionTelemetry telemetry() {
        return new ProductionTelemetry(producedChunks.get(), producedNanos.get(),
                maxProductionNanos.get(), replayedWithoutLock.get(), consumerLockWaitNanos.get(),
                maxConsumerLockWaitNanos.get(), prefetchedChunks.get(), deferredPrefetches.get(),
                courseAwaitedChunks.get(), courseAwaitNanos.get(), courseAwaitAbandoned.get());
    }

    /**
     * Produces the chunk ahead of need when nothing is currently demanding production.
     *
     * <p>Returns {@code true} when the chunk is committed on return. A deferral is not a failure:
     * the caller retries the same coordinate once the consumer it yielded to is served.</p>
     */
    public boolean prefetch(int worldSeed, int chunkX, int chunkZ) {
        return prefetch(worldSeed, chunkX, chunkZ, chunkX, chunkZ);
    }

    /**
     * Produces one chunk ahead of need and, while its FEATURES pass runs, starts the post-CARVERS
     * inputs of the coordinate this caller will ask for next.
     *
     * <p>AGENTS rule 10l: neighbouring targets share twenty of their twenty-five post-CARVERS
     * input chunks, so a streaming wall's marginal input cost is five chunk builds — and this seam
     * used to pay them as dead wall time, with the input pool idle for the whole FEATURES pass and
     * the producer thread idle for the whole input pass. The lookahead coordinate lets the two
     * overlap. It changes nothing a consumer can observe: the same seam produces the same bytes in
     * the same order, every memo lookup and store still happens on this thread, and a lookahead
     * that is never produced is dropped after its builds are joined. A caller that does not know
     * its next coordinate passes this one and gets exactly the old serial behaviour, which is what
     * {@link #generate} and the throughput gate do.</p>
     */
    public boolean prefetch(int worldSeed, int chunkX, int chunkZ, int nextChunkX,
            int nextChunkZ) {
        return prefetch(worldSeed, chunkX, chunkZ, nextChunkX, nextChunkZ, 0);
    }

    /**
     * Produces one chunk ahead of need on a named production slot.
     *
     * <p>Each course worker owns one slot for the whole sweep, which is what lets the bounded
     * memos stay hot: the worker's own targets are the only ones that ever land in them. A slot
     * index outside the slot count wraps, so a caller may pass its worker ordinal directly.</p>
     */
    public boolean prefetch(int worldSeed, int chunkX, int chunkZ, int nextChunkX,
            int nextChunkZ, int slotIndex) {
        if (isolated != null) {
            boolean committed = isolated.prefetch(worldSeed, chunkX, chunkZ);
            if (committed) { rememberCommittedChunk(chunkX, chunkZ); prefetchedChunks.incrementAndGet(); }
            else deferredPrefetches.incrementAndGet();
            return committed;
        }
        bindWorldSeed(worldSeed);
        if (isCommitted(chunkX, chunkZ)) return true;
        ProductionSlot slot = slots[Math.floorMod(slotIndex, productionSlots())];
        if (waitingConsumers.get() > 0 || !slot.lock.tryLock()) {
            deferredPrefetches.incrementAndGet();
            return false;
        }
        if (waitingConsumers.get() > 0) {
            slot.lock.unlock();
            deferredPrefetches.incrementAndGet();
            return false;
        }
        long key = ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
        CompletableFuture<Void> attempt = new CompletableFuture<>();
        if (inFlight.putIfAbsent(key, attempt) != null) {
            slot.lock.unlock();
            deferredPrefetches.incrementAndGet();
            return false;
        }
        Mc263CanonicalGenerationProduct product;
        try {
            try {
                boolean lookahead = nextChunkX != chunkX || nextChunkZ != chunkZ;
                product = produceIfAbsent(slot, worldSeed, chunkX, chunkZ, lookahead, nextChunkX,
                        nextChunkZ);
            } finally {
                slot.lock.unlock();
            }
            if (product != null) {
                commitProduct(product);
                rememberCommittedChunk(chunkX, chunkZ);
                prefetchedChunks.incrementAndGet();
            }
            attempt.complete(null);
            return true;
        } catch (RuntimeException | Error failure) {
            attempt.completeExceptionally(failure);
            throw failure;
        } finally {
            inFlight.remove(key, attempt);
        }
    }

    @Override
    public ChunkGenerator.GeneratedChunk generate(int worldSeed, int chunkX, int chunkZ) {
        bindWorldSeed(worldSeed);
        ChunkDemandListener listener = demandListener;
        if (listener != null) listener.onCanonicalChunkDemand(chunkX, chunkZ);
        if (isolated != null) {
            var result = isolated.generate(worldSeed, chunkX, chunkZ);
            rememberCommittedChunk(chunkX, chunkZ);
            return result;
        }

        // A committed product is durable and immutable, so serving it needs no production lock.
        ChunkGenerator.GeneratedChunk committed = replay.replayIfCommitted(chunkX, chunkZ);
        if (committed != null) {
            rememberCommittedChunk(chunkX, chunkZ);
            replayedWithoutLock.incrementAndGet();
            return committed;
        }

        // The coordinate is not on record yet, but the production course may already own it.
        ChunkGenerator.GeneratedChunk awaited = awaitCourseCommit(chunkX, chunkZ);
        if (awaited != null) return awaited;

        long key = ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
        CompletableFuture<Void> attempt = new CompletableFuture<>();
        CompletableFuture<Void> existing = inFlight.putIfAbsent(key, attempt);
        if (existing != null) {
            awaitPublication(existing);
        } else {
            try {
                long waitStarted = System.nanoTime();
                ProductionSlot slot = acquireSlot();
                long waited = System.nanoTime() - waitStarted;
                consumerLockWaitNanos.addAndGet(waited);
                maxConsumerLockWaitNanos.accumulateAndGet(waited, Math::max);
                Mc263CanonicalGenerationProduct product;
                try {
                    product = produceIfAbsent(slot, worldSeed, chunkX, chunkZ, false, chunkX, chunkZ);
                } finally {
                    slot.lock.unlock();
                }
                if (product != null) commitProduct(product);
                attempt.complete(null);
            } catch (RuntimeException | Error failure) {
                attempt.completeExceptionally(failure);
                throw failure;
            } finally {
                inFlight.remove(key, attempt);
            }
        }
        ChunkGenerator.GeneratedChunk served = replay.replayIfCommitted(chunkX, chunkZ);
        if (served == null) {
            throw new IllegalStateException("canonical commit did not publish chunk "
                    + chunkX + "," + chunkZ);
        }
        rememberCommittedChunk(chunkX, chunkZ);
        return served;
    }

    private static void awaitPublication(CompletableFuture<Void> attempt) {
        try {
            attempt.join();
        } catch (CompletionException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw failure;
        }
    }

    /**
     * Lets the production course commit a coordinate it already owns, instead of racing it.
     *
     * <p>This is the admission rule for first generation. The course produces in ring order with
     * the next target's post-CARVERS inputs overlapped onto this target's FEATURES pass, which is
     * the only order the bounded memos are sized for and the only rate the throughput gate
     * measures. A consumer that takes the production lock for a coordinate the sweep is about to
     * reach does not get its chunk sooner — production is serialized by that one lock either way —
     * it merely moves the work off the amortized course and onto client request order, where the
     * post-CARVERS memo thrashes and every chunk costs several times what it costs on the course.
     * Worse, while such a consumer is queued, {@link #prefetch} defers <i>every</i> coordinate to
     * it, so during entry the course is starved by exactly the demands it exists to serve.</p>
     *
     * <p>So a consumer inside the sweep waits here rather than in the production lock queue: same
     * seam, same producer thread, same bytes, same memo state transitions — only the thread that
     * pays for them changes, and {@code waitingConsumers} stays at zero so the course keeps
     * running. A consumer outside the sweep is unchanged and still produces its own chunk, which
     * is the correct rule for background and steady-state demand.</p>
     *
     * <p>Returns the committed product, or {@code null} when the caller must produce it itself:
     * no course installed, the coordinate outside it, the sweep having moved or closed, an
     * interrupt, or the whole source committing nothing for {@link #COURSE_STALL_NANOS}.</p>
     */
    private ChunkGenerator.GeneratedChunk awaitCourseCommit(int chunkX, int chunkZ) {
        ProductionCourse course = productionCourse;
        if (course == null || !course.coversActiveCourse(chunkX, chunkZ)) return null;
        long started = System.nanoTime();
        long lastCommitted = producedChunks.get();
        long stallDeadline = started + COURSE_STALL_NANOS;
        while (true) {
            LockSupport.parkNanos(COURSE_POLL_NANOS);
            ChunkGenerator.GeneratedChunk served = replay.replayIfCommitted(chunkX, chunkZ);
            if (served != null) {
                rememberCommittedChunk(chunkX, chunkZ);
                courseAwaitedChunks.incrementAndGet();
                courseAwaitNanos.addAndGet(System.nanoTime() - started);
                return served;
            }
            if (Thread.currentThread().isInterrupted()
                    || !course.coversActiveCourse(chunkX, chunkZ)) {
                courseAwaitAbandoned.incrementAndGet();
                return null;
            }
            long produced = producedChunks.get();
            long now = System.nanoTime();
            if (now - started >= COURSE_AWAIT_BUDGET_NANOS) {
                courseAwaitAbandoned.incrementAndGet();
                return null;
            }
            if (produced != lastCommitted) {
                lastCommitted = produced;
                stallDeadline = now + COURSE_STALL_NANOS;
            } else if (now >= stallDeadline) {
                courseAwaitAbandoned.incrementAndGet();
                return null;
            }
        }
    }

    /**
     * Takes a production slot for a consumer, returning it locked.
     *
     * <p>A free slot is taken without ever registering as a waiter, so the course keeps producing:
     * {@link #prefetch} only stands down for a consumer that is genuinely queued behind its own
     * slot. Only when every slot is busy does the consumer pick one, announce itself on it, and
     * block — which is the case the single-lock seam always had.</p>
     */
    private ProductionSlot acquireSlot() {
        // The reserved slot first: no course worker ever takes it, so it is free unless another
        // consumer is already producing on it.
        ProductionSlot reserved = slots[slots.length - 1];
        if (reserved.lock.tryLock()) return reserved;
        for (ProductionSlot candidate : slots) {
            if (candidate.lock.tryLock()) return candidate;
        }
        // Every slot is busy. Announce the wait so no producer starts another coordinate, then
        // take the first slot that frees — which is the one whose in-flight chunk finishes first,
        // never a slot chosen in advance.
        waitingConsumers.incrementAndGet();
        try {
            while (true) {
                for (ProductionSlot candidate : slots) {
                    if (candidate.lock.tryLock()) return candidate;
                }
                LockSupport.parkNanos(SLOT_POLL_NANOS);
            }
        } finally {
            waitingConsumers.decrementAndGet();
        }
    }

    private void bindWorldSeed(int worldSeed) {
        if (detachedSeed != null && detachedSeed != worldSeed) {
            throw new IllegalStateException(
                    "detached canonical source world seed does not match request");
        }
        Integer bound = boundWorldSeed;
        if (bound == null) {
            synchronized (this) {
                if (boundWorldSeed == null) boundWorldSeed = worldSeed;
                else if (boundWorldSeed != worldSeed) {
                    throw new IllegalStateException(
                            "canonical source world seed does not match request");
                }
            }
        } else if (bound != worldSeed) {
            throw new IllegalStateException("canonical source world seed does not match request");
        }
    }

    /**
     * Assembles and produces one chunk on a slot the caller already holds. Returns
     * {@code null} when already committed; publication happens only after releasing the slot.
     */
    private Mc263CanonicalGenerationProduct produceIfAbsent(ProductionSlot slot, int worldSeed, int chunkX, int chunkZ,
            boolean lookahead, int nextChunkX, int nextChunkZ) {
        if (isCommitted(chunkX, chunkZ)) return null;
        long started = System.nanoTime();
        Prepared candidate = slot.prepared;
        Mc263CanonicalFeaturesProducerSkeleton.ProductionContextAuthority contextAuthority =
                candidate != null && candidate.chunkX == chunkX && candidate.chunkZ == chunkZ
                        ? candidate.contextAuthority
                        : productionContextAuthority(worldSeed, chunkX, chunkZ);
        Prepared prepared = takePrepared(slot, chunkX, chunkZ);
        Mc263StructureCarrierOrigin.Assembly assembly = prepared != null ? prepared.assembly
                : Mc263StructureCarrierOrigin.assemble(
                        slot.carrierWorld(worldSeed), chunkX, chunkZ, slot.memo);
        if (assembly.targetChunkX() != chunkX || assembly.targetChunkZ() != chunkZ) {
            throw new IllegalStateException("assembled carrier target does not match request");
        }
        Mc263FeaturesRegionBridge.PendingRegionInput pending = prepared != null ? prepared.input
                : startPostCarversInput(slot, worldSeed, chunkX, chunkZ, assembly.carrier());
        Mc263PostCarversFeaturesRegionBuilder.RegionInput input = pending.finish();
        // The next target's inputs are decided work; start them before this target's FEATURES pass
        // so the input context's workers run while this thread does not need them.
        if (lookahead && !isCommitted(nextChunkX, nextChunkZ)) {
            prepare(slot, worldSeed, nextChunkX, nextChunkZ);
        }
        Mc263CanonicalGenerationProduct product = Mc263FeaturesRegionBridge.generateCanonicalProductFromInput(
                worldSeed, chunkX, chunkZ, assembly.carrier(), contextAuthority, activation, input);
        long elapsed = System.nanoTime() - started;
        producedNanos.addAndGet(elapsed);
        maxProductionNanos.accumulateAndGet(elapsed, Math::max);
        return product;
    }

    /**
     * Publishes one produced product, serialized against every other slot.
     *
     * <p>The store already collapses a coordinate committed twice — it inserts if absent and then
     * proves the stored fingerprint and payload against this one — so a coordinate two slots both
     * produced is committed once and verified identical rather than conflicting. This lock is what
     * keeps that collapse a single decision instead of a race between two inserts, and it keeps
     * the observable commit order of concurrently produced coordinates one total order. The
     * caller waits without a CPU slot, and returns only after the store transaction finishes.</p>
     */
    private void commitProduct(Mc263CanonicalGenerationProduct product) {
        byte[] successor = product.structureCarrier().receiptBytes();
        settlements.commit(new CanonicalWorldgenStore.ChunkCommit(worldId, WorldBaseline.ID,
                product.finalChunk().chunkX(), product.finalChunk().chunkZ(),
                product.finalCarrier(), successor, successor, product.commitFingerprint()));
        producedChunks.incrementAndGet();
    }

    /**
     * One world's production state, owned by whoever holds {@link ProductionSlot#lock}.
     *
     * <p>Everything a production mutates lives here — both bounded memos, the prepared carrier
     * world, and the lookahead slot — so two slots produce two coordinates without sharing a
     * single mutable field. That duplication is the cost of the parallelism: the memos are
     * bounded per slot, and the carrier world is rebuilt once per slot per seed.</p>
     */
    private final class ProductionSlot {
        private final int index;
        private final ReentrantLock lock = new ReentrantLock();
        private final Mc263StructureCarrierOrigin.RegionMemo memo;
        private final Mc263FeaturesRegionBridge.RegionMemo postCarversMemo =
                Mc263FeaturesRegionBridge.RegionMemo.bounded(
                        Mc263FeaturesRegionBridge.DEFAULT_MEMO_CHUNKS);
        private long baseHeightSeed;
        private Mc263BaseHeightSampler baseHeight;
        private Mc263StructureCarrierOrigin.WorldContext carrierWorld;
        /** Lookahead slot; written and read only under {@link #lock}. */
        private Prepared prepared;

        private ProductionSlot(int index, int memoRegions) {
            this.index = index;
            this.memo = Mc263StructureCarrierOrigin.RegionMemo.bounded(memoRegions);
        }

        /**
         * The pinned base-height lane for this seed. It carries only bounded pure column caches,
         * so one instance per slot amortizes its ~200 ms construction over every chunk this slot
         * produces.
         */
        private Mc263BaseHeightSampler baseHeight(int worldSeed) {
            if (baseHeight == null || baseHeightSeed != worldSeed) {
                baseHeight = Mc263BaseHeightSampler.overworld(worldSeed);
                baseHeightSeed = worldSeed;
                carrierWorld = Mc263StructureCarrierOrigin.prepare(worldSeed, baseHeight);
            }
            return baseHeight;
        }

        private Mc263StructureCarrierOrigin.WorldContext carrierWorld(int worldSeed) {
            baseHeight(worldSeed);
            return carrierWorld;
        }
    }

    /** One target assembled and started ahead of the producer thread reaching it. */
    private static final class Prepared {
        private final int chunkX;
        private final int chunkZ;
        private final Mc263StructureCarrierOrigin.Assembly assembly;
        private final Mc263FeaturesRegionBridge.PendingRegionInput input;
        private final Mc263CanonicalFeaturesProducerSkeleton.ProductionContextAuthority
                contextAuthority;

        private Prepared(int chunkX, int chunkZ, Mc263StructureCarrierOrigin.Assembly assembly,
                Mc263FeaturesRegionBridge.PendingRegionInput input,
                Mc263CanonicalFeaturesProducerSkeleton.ProductionContextAuthority
                        contextAuthority) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.assembly = assembly;
            this.input = input;
            this.contextAuthority = contextAuthority;
        }
    }

    /** Assembles the lookahead target and starts its input builds. Slot holder only. */
    private void prepare(ProductionSlot slot, int worldSeed, int chunkX, int chunkZ) {
        Mc263CanonicalFeaturesProducerSkeleton.ProductionContextAuthority contextAuthority =
                productionContextAuthority(worldSeed, chunkX, chunkZ);
        discardPrepared(slot);
        Mc263StructureCarrierOrigin.Assembly assembly = Mc263StructureCarrierOrigin.assemble(
                slot.carrierWorld(worldSeed), chunkX, chunkZ, slot.memo);
        slot.prepared = new Prepared(chunkX, chunkZ, assembly,
                startPostCarversInput(slot, worldSeed, chunkX, chunkZ, assembly.carrier()),
                contextAuthority);
    }

    private Mc263CanonicalFeaturesProducerSkeleton.ProductionContextAuthority
            productionContextAuthority(int worldSeed, int chunkX, int chunkZ) {
        return Objects.requireNonNull(productionContextProvider.authorityFor(
                worldSeed, chunkX, chunkZ),
                "production context catalog provider returned no authority");
    }

    private Mc263FeaturesRegionBridge.PendingRegionInput startPostCarversInput(
            ProductionSlot slot, int worldSeed, int chunkX, int chunkZ,
            Mc263StructureCarrier carrier) {
        return Mc263FeaturesRegionBridge.startPostCarversInput(
                inputBuilderContext(slot), worldSeed, chunkX, chunkZ, carrier, slot.postCarversMemo);
    }

    /** The caller's input pool, or this slot's stable share of the source-owned input budget. */
    private Mc263FeaturesRegionBridge.InputBuilderContext inputBuilderContext(ProductionSlot slot) {
        if (inputBuilderContext != null) return inputBuilderContext;
        Mc263FeaturesRegionBridge.InputBuilderContext[] owned = ownedInputBuilderContexts;
        if (owned != null) return owned[slot.index % owned.length];
        synchronized (this) {
            if (ownedInputBuilderContexts == null) {
                int[] counts = runtimeInputBuilderThreadCounts(
                        Runtime.getRuntime().availableProcessors());
                owned = new Mc263FeaturesRegionBridge.InputBuilderContext[counts.length];
                for (int index = 0; index < counts.length; index++) {
                    owned[index] = Mc263FeaturesRegionBridge.newInputBuilderContext(counts[index]);
                }
                ownedInputBuilderContexts = owned;
                log.info("정본 입력 풀: world={} 작업자={} 풀={} 풀별={} 생산슬롯={}",
                        worldId, java.util.Arrays.stream(counts).sum(), counts.length,
                        java.util.Arrays.toString(counts), productionSlots());
            }
            owned = ownedInputBuilderContexts;
            return owned[slot.index % owned.length];
        }
    }

    /** Existing contexts accept at most five workers; never multiply the budget by slot count. */
    static int[] runtimeInputBuilderThreadCounts(int processors) {
        if (processors < 1) throw new IllegalArgumentException("positive processor count required");
        int threads = Math.max(1, Math.min(8, processors / 2));
        if (threads <= 5) return new int[] {threads};
        return new int[] {(threads + 1) / 2, threads / 2};
    }

    /** The prepared slot for this coordinate, or {@code null}; any other slot is dropped. */
    private Prepared takePrepared(ProductionSlot slot, int chunkX, int chunkZ) {
        Prepared candidate = slot.prepared;
        if (candidate == null) return null;
        if (candidate.chunkX != chunkX || candidate.chunkZ != chunkZ) {
            discardPrepared(slot);
            return null;
        }
        slot.prepared = null;
        return candidate;
    }

    /**
     * Drops a lookahead the producer will not reach. The started builds are joined first, because
     * a dropped slot must not leave a build running against a carrier nobody holds; a failure
     * there is not this coordinate's failure and is raised again when that coordinate is really
     * produced.
     */
    private void discardPrepared(ProductionSlot slot) {
        Prepared candidate = slot.prepared;
        if (candidate == null) return;
        slot.prepared = null;
        try {
            candidate.input.finish();
        } catch (RuntimeException dropped) {
            // Deliberately ignored: see above.
        }
    }
}
