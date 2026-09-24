package com.gameexpert.engine.persistence.tick;

import com.gameexpert.engine.persistence.finalcarrier.FinalCarrierConsumedTick;
import com.gameexpert.engine.persistence.finalcarrier.FinalCarrierDurableStateException;
import com.gameexpert.ground.dto.GroundItemSnapshot;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.authority.versioned.NeutralFinalChunk;
import com.gameexpert.authority.versioned.ProducerAuthorities;
import com.gameexpert.world.WorldGenerationProfiles;
import java.util.function.IntFunction;
import com.gameexpert.authority.versioned.NeutralFinalChunk.BlockTick;
import com.gameexpert.authority.versioned.NeutralFinalChunk.FluidTick;
import com.gameexpert.authority.versioned.NeutralFinalChunk.TickPriority;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongSupplier;

/**
 * Exact, bounded runtime core for final-carrier BTIK and FTIK lanes.
 *
 * <p>The world owner supplies an {@link AtomicPersistence} boundary that atomically persists lane
 * admission with its carrier ACK, and settles each due tick with every resulting durable mutation.
 * Live publication and its exact ACK finish before another tick is planned.</p>
 */
public final class FinalCarrierTickScheduler {

    public static final int MAX_PENDING_TICKS = 65_536;
    public static final int MAX_MUTATIONS_PER_TICK = MAX_PENDING_TICKS;
    private static final FinalCarrierTickPublicationCodec.Limits PUBLICATION_LIMITS =
            new FinalCarrierTickPublicationCodec.Limits(
                    32 * 1024 * 1024, MAX_MUTATIONS_PER_TICK, MAX_MUTATIONS_PER_TICK,
                    160, 1 << 20);

    public enum Lane { BLOCK, FLUID }

    public enum AdmissionStatus { ADMITTED, ALREADY_ACKNOWLEDGED, CAPACITY_REJECTED }

    public enum DueDisposition { EXECUTE, LIVE_TYPE_NO_OP }

    public enum Settlement { COMMITTED, ALREADY_COMMITTED, RETRY }

    public enum DrainStatus { DRAINED, RETRY, DEADLINE, UNAVAILABLE, REENTRANT }

    public record DrainReport(int processed, DrainStatus status) {
        public DrainReport {
            if (processed < 0) throw new IllegalArgumentException("negative processed count");
            Objects.requireNonNull(status, "status");
        }
    }

    /** Stable source + exact lane-payload identity; activation/delivery time is deliberately absent. */
    public record CarrierReceipt(long worldId, int chunkX, int chunkZ, String sourceFingerprint,
            String lanePayloadFingerprint, Lane lane) {
        public CarrierReceipt {
            Objects.requireNonNull(lane, "lane");
            requireSha256(sourceFingerprint, "carrier source fingerprint");
            requireSha256(lanePayloadFingerprint, "lane payload fingerprint");
        }
    }

    /** The Minecraft uniqueness key: first admission for one lane, position and type wins. */
    public record TickKey(Lane lane, int x, int y, int z, String typeKey) {
        public TickKey {
            Objects.requireNonNull(lane, "lane");
            requireCanonicalKey(typeKey);
            if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) {
                throw new IllegalArgumentException("scheduled tick Y outside world: " + y);
            }
        }
    }

    /** Durable queue row. {@code expectedBlockId} is -1 for fluid ticks. */
    public record ScheduledTick(TickKey key, CarrierReceipt receipt, int expectedBlockId,
            long dueTick, TickPriority priority, long subTickOrder, long durableOrder) {
        public ScheduledTick {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(receipt, "receipt");
            Objects.requireNonNull(priority, "priority");
            if (dueTick < 0) throw new IllegalArgumentException("negative absolute due tick");
            if (durableOrder < -1) throw new IllegalArgumentException("invalid durable order");
            if (key.lane() != receipt.lane()) {
                throw new IllegalArgumentException("tick lane does not match carrier receipt");
            }
            if (Math.floorDiv(key.x(), Blocks.CHUNK_X) != receipt.chunkX()
                    || Math.floorDiv(key.z(), Blocks.CHUNK_Z) != receipt.chunkZ()) {
                throw new IllegalArgumentException("tick position is outside receipt chunk");
            }
            if (key.lane() == Lane.BLOCK) {
                if (expectedBlockId < 0 || expectedBlockId > 0xffff) {
                    throw new IllegalArgumentException("block tick ID outside u16: "
                            + expectedBlockId);
                }
            } else if (expectedBlockId != -1) {
                throw new IllegalArgumentException("fluid tick must not carry a block ID");
            }
        }

        public Lane lane() { return key.lane(); }
        public int x() { return key.x(); }
        public int y() { return key.y(); }
        public int z() { return key.z(); }
        public String typeKey() { return key.typeKey(); }
    }

    /** Immutable authoritative block-diff plan persisted with tick consumption. */
    public record BlockMutation(int x, int y, int z, int beforeBlockId, int beforeBlockState,
            int blockId, int blockState) {
        /** Compatibility constructor for callers that only have the after-state. */
        public BlockMutation(int x, int y, int z, int blockId, int blockState) {
            this(x, y, z, -1, -1, blockId, blockState);
        }

        public BlockMutation {
            if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) {
                throw new IllegalArgumentException("mutation Y outside world: " + y);
            }
            if (blockId < 0 || blockId > 0xffff || blockState < 0 || blockState > 0xffff) {
                throw new IllegalArgumentException("mutation after block fields must be u16");
            }
            if ((beforeBlockId == -1) != (beforeBlockState == -1)
                    || beforeBlockId < -1 || beforeBlockId > 0xffff
                    || beforeBlockState < -1 || beforeBlockState > 0xffff) {
                throw new IllegalArgumentException(
                        "mutation before block fields must be both absent or u16");
            }
        }

        public boolean hasBeforeState() {
            return beforeBlockId >= 0;
        }
    }

    public record TickMutation(List<BlockMutation> blocks, List<GroundItemSnapshot> drops) {
        public static final TickMutation NONE = new TickMutation(List.of());

        public TickMutation(List<BlockMutation> blocks) {
            this(blocks, List.of());
        }

        public TickMutation {
            Objects.requireNonNull(blocks, "blocks");
            Objects.requireNonNull(drops, "drops");
            blocks = blocks.stream().map(Objects::requireNonNull)
                    .sorted(Comparator.comparingInt(BlockMutation::x)
                            .thenComparingInt(BlockMutation::y)
                            .thenComparingInt(BlockMutation::z))
                    .toList();
            drops = drops.stream().map(Objects::requireNonNull)
                    .sorted(Comparator.comparingLong(GroundItemSnapshot::entityId))
                    .toList();
            HashSet<MutationPosition> positions = new HashSet<>();
            for (BlockMutation block : blocks) {
                if (!positions.add(new MutationPosition(block.x(), block.y(), block.z()))) {
                    throw new IllegalArgumentException(
                            "tick mutation contains duplicate block position");
                }
            }
            HashSet<Long> dropIds = new HashSet<>();
            for (GroundItemSnapshot drop : drops) {
                if (!dropIds.add(drop.entityId())) {
                    throw new IllegalArgumentException(
                            "tick mutation contains duplicate ground item entity ID");
                }
            }
            if ((long) blocks.size() + drops.size() > MAX_MUTATIONS_PER_TICK) {
                throw new IllegalArgumentException(
                        "tick mutation exceeds bounded cardinality: "
                                + (blocks.size() + drops.size()));
            }
        }
    }

    private record MutationPosition(int x, int y, int z) { }

    public record Admission(AdmissionStatus status, List<ScheduledTick> durableTicks,
            List<ScheduledTick> consumedTicks) {
        public Admission(AdmissionStatus status, List<ScheduledTick> durableTicks) {
            this(status, durableTicks, List.of());
        }

        public Admission {
            Objects.requireNonNull(status, "status");
            durableTicks = List.copyOf(durableTicks);
            consumedTicks = List.copyOf(consumedTicks);
            if ((long) durableTicks.size() + consumedTicks.size() > MAX_PENDING_TICKS) {
                throw new IllegalArgumentException(
                        "admission durable disposition exceeds bounded capacity");
            }
            if (status == AdmissionStatus.CAPACITY_REJECTED
                    && (!durableTicks.isEmpty() || !consumedTicks.isEmpty())) {
                throw new IllegalArgumentException(
                        "rejected admission cannot expose durable accounting");
            }
        }
    }

    /** Exact durable outcome, including the plan recovered from the consumed row. */
    public record SettlementResult(Settlement status, TickMutation mutation,
            String publicationKey, String mutationDigest, byte[] mutationBody) {
        public SettlementResult {
            Objects.requireNonNull(status, "settlement status");
            Objects.requireNonNull(mutation, "settlement mutation");
            Objects.requireNonNull(publicationKey, "publication key");
            Objects.requireNonNull(mutationDigest, "mutation digest");
            Objects.requireNonNull(mutationBody, "mutation body");
            mutationBody = mutationBody.clone();
            if (mutationBody.length == 0
                    || !mutationDigest.equals(FinalCarrierTickPublicationCodec
                            .publicationDigest(mutationBody))) {
                throw new FinalCarrierDurableStateException(
                        "settlement result body is not authenticated");
            }
        }

        @Override
        public byte[] mutationBody() {
            return mutationBody.clone();
        }

        static SettlementResult forDefault(ScheduledTick tick, DueDisposition disposition,
                Settlement status, TickMutation mutation) {
            byte[] body = encodePublicationBody(tick, disposition, mutation);
            return new SettlementResult(status, mutation,
                    FinalCarrierTickPublicationCodec.publicationKey(tick, disposition),
                    FinalCarrierTickPublicationCodec.publicationDigest(body), body);
        }
    }

    /** Durable publication identity and its exact, already-decoded mutation. */
    public record DurablePublication(ScheduledTick tick, DueDisposition disposition,
            TickMutation mutation, String publicationKey, String mutationDigest,
            byte[] mutationBody, FinalCarrierConsumedTick.PublicationState publicationState) {
        public DurablePublication {
            Objects.requireNonNull(tick, "publication tick");
            Objects.requireNonNull(disposition, "publication disposition");
            Objects.requireNonNull(mutation, "publication mutation");
            Objects.requireNonNull(publicationKey, "publication key");
            Objects.requireNonNull(mutationDigest, "mutation digest");
            Objects.requireNonNull(mutationBody, "mutation body");
            Objects.requireNonNull(publicationState, "publication state");
            mutationBody = mutationBody.clone();
            if (tick.durableOrder() <= 0L
                    || !publicationKey.equals(FinalCarrierTickPublicationCodec.publicationKey(
                            tick, disposition))
                    || mutationBody.length == 0
                    || !mutationDigest.equals(FinalCarrierTickPublicationCodec
                            .publicationDigest(mutationBody))
                    || !MessageDigest.isEqual(mutationBody,
                            encodePublicationBody(tick, disposition, mutation))) {
                throw new FinalCarrierDurableStateException(
                        "durable publication identity is not canonical");
            }
            if (disposition == DueDisposition.LIVE_TYPE_NO_OP
                    && (!mutation.blocks().isEmpty() || !mutation.drops().isEmpty())) {
                throw new FinalCarrierDurableStateException(
                        "no-op durable publication contains a mutation");
            }
        }

        @Override
        public byte[] mutationBody() {
            return mutationBody.clone();
        }

        static DurablePublication fromSettlement(ScheduledTick tick, DueDisposition disposition,
                SettlementResult result, FinalCarrierConsumedTick.PublicationState state) {
            return new DurablePublication(tick, disposition, result.mutation(),
                    result.publicationKey(), result.mutationDigest(), result.mutationBody(), state);
        }
    }

    /**
     * Immutable admission input prepared without touching scheduler state. Persistence may commit it on a
     * worker; the world owner later installs the returned durable rows through {@link #acceptAdmission}.
     */
    public static final class PreparedAdmission {
        private final CarrierReceipt receipt;
        private final long admissionBaseMcTick;
        private final List<ScheduledTick> candidates;

        private PreparedAdmission(CarrierReceipt receipt, long admissionBaseMcTick,
                List<ScheduledTick> candidates) {
            this.receipt = Objects.requireNonNull(receipt, "receipt");
            requireNow(admissionBaseMcTick);
            Objects.requireNonNull(candidates, "candidates");
            if (candidates.size() > MAX_PENDING_TICKS) {
                throw new IllegalArgumentException(
                        "prepared admission candidate set exceeds bounded capacity");
            }
            for (ScheduledTick candidate : candidates) {
                Objects.requireNonNull(candidate, "candidate");
                if (candidate.durableOrder() != -1L) {
                    throw new IllegalArgumentException(
                            "prepared admission candidates must use durable order -1");
                }
            }
            this.admissionBaseMcTick = admissionBaseMcTick;
            this.candidates = List.copyOf(candidates);
        }

        public CarrierReceipt receipt() {
            return receipt;
        }

        public long admissionBaseMcTick() {
            return admissionBaseMcTick;
        }

        public List<ScheduledTick> candidates() {
            return candidates;
        }
    }

    /**
     * Detached, authenticated recovery rows and their requested coordinate. This value contains
     * no resident state or persistence entities. Its identity does not establish freshness:
     * callers must keep recovery and installation synchronous until they provide lifecycle fences.
     */
    public static final class PreparedChunkRecovery {
        private final long worldId;
        private final int chunkX;
        private final int chunkZ;
        private final List<ScheduledTick> ticks;

        private PreparedChunkRecovery(long worldId, int chunkX, int chunkZ,
                List<ScheduledTick> ticks) {
            this.worldId = worldId;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.ticks = List.copyOf(ticks);
        }

        public long worldId() { return worldId; }
        public int chunkX() { return chunkX; }
        public int chunkZ() { return chunkZ; }
        public List<ScheduledTick> ticks() { return ticks; }
    }

    /**
     * Required production transaction boundary.
     *
     * <p>{@link #admitAndAcknowledge} inserts only first-winner queue rows and the carrier-lane
     * receipt in one transaction, assigning a monotonic {@code durableOrder} within the lane to
     * every first-winner row. {@link #settleAtomically} inserts the consumed receipt, applies
     * every mutation to durable world diffs, and removes the queue row in one transaction. A retry
     * after an unknown commit result returns {@link Settlement#ALREADY_COMMITTED}.</p>
     */
    public interface AtomicPersistence {
        Admission admitAndAcknowledge(CarrierReceipt receipt, List<ScheduledTick> candidates,
                long admissionBaseMcTick, int capacity);

        List<ScheduledTick> loadWorld(long worldId);

        List<ScheduledTick> loadChunk(long worldId, int chunkX, int chunkZ);

        /** Production boundaries override this to read queue and outbox in one transaction. */
        default FinalCarrierTickRecoverySnapshot loadRecovery(long worldId, int limit) {
            return new FinalCarrierTickRecoverySnapshot(loadWorld(worldId),
                    loadDurablePublications(worldId, limit));
        }

        Settlement settleAtomically(ScheduledTick tick, DueDisposition disposition,
                TickMutation mutation);

        /** Compatibility bridge; durable implementations return the stored plan on ALREADY. */
        default SettlementResult settleAtomicallyWithPlan(ScheduledTick tick,
                DueDisposition disposition, TickMutation mutation) {
            Settlement status = settleAtomically(tick, disposition, mutation);
            return SettlementResult.forDefault(tick, disposition, status, mutation);
        }

        /** Puts a committed receipt into the explicit outcome-unknown publication state. */
        default DurablePublication beginOutcomeUnknown(ScheduledTick tick,
                DueDisposition disposition, SettlementResult result) {
            return DurablePublication.fromSettlement(tick, disposition, result,
                    FinalCarrierConsumedTick.PublicationState.OUTCOME_UNKNOWN);
        }

        /** Exact ACK; the default keeps focused in-memory boundaries source-compatible. */
        default void acknowledgePublication(DurablePublication publication) { }

        /** Exact rejection; the default keeps focused in-memory boundaries source-compatible. */
        default void rejectPublication(DurablePublication publication) { }

        /** Bounded durable outbox source lane. */
        default List<DurablePublication> loadDurablePublications(long worldId, int limit) {
            return List.of();
        }
    }

    /**
     * Runs one durable settlement step somewhere other than the calling thread.
     *
     * <p>Every step below takes the world row lock, and a world row is the process-wide write
     * mutex a dozen unrelated services already contend for. Taking it on the world owner thread
     * is what forced this seam to use a no-wait lock, which then failed thousands of times per
     * session and turned each failure into a database round trip, an exception and a pair of
     * Hibernate warnings. Handing the step to a worker lets it take the ordinary waiting lock and
     * succeed the first time, while the owner thread only waits for the result inside the
     * wall-clock slice the drain already owns.</p>
     *
     * <p>An implementation returns {@code false} when it cannot accept the step; the drain then
     * leaves the row at the head and retries it on the next turn. It must never run the step on
     * the calling thread.</p>
     */
    @FunctionalInterface
    public interface SettlementExecutor {
        boolean submit(Runnable step);
    }

    /** The durable steps of one settlement, each its own transaction and its own world lock. */
    private enum SettlementStage { SETTLE, BEGIN, ACKNOWLEDGE }

    /** One step handed to the settlement executor, and the single result it will produce. */
    private static final class OffThreadStep {
        private volatile Object value;
        private volatile RuntimeException failure;
        private volatile Error fatal;
        private volatile boolean done;
    }

    private record StepKey(TickKey key, SettlementStage stage) { }

    @FunctionalInterface
    public interface LiveTypes {
        String canonicalTypeAt(Lane lane, int x, int y, int z);
    }

    @FunctionalInterface
    public interface TickSemantics {
        TickMutation plan(ScheduledTick tick, LiveTypes world);
    }

    /**
     * Publishes the exact mutation after durable settlement. The scheduler calls this for both a
     * fresh {@link Settlement#COMMITTED} result and an idempotent
     * {@link Settlement#ALREADY_COMMITTED} recovery, and retains the scheduler queue row until
     * this call returns normally.
     */
    @FunctionalInterface
    public interface CommittedMutationSink {
        void publish(ScheduledTick tick, TickMutation mutation);
    }

    /** Poll interval while the owner waits, inside its own slice, for a handed-off step. */
    private static final long SETTLEMENT_POLL_NANOS = 50_000L;

    private static final Comparator<ScheduledTick> TICK_ORDER =
            Comparator.comparingLong(ScheduledTick::dueTick)
                    .thenComparingInt(tick -> tick.priority().value())
                    .thenComparingLong(ScheduledTick::subTickOrder)
                    .thenComparingLong(ScheduledTick::durableOrder);

    private final long worldId;
    private final IntFunction<NeutralFinalChunk.StateOverride> defaultStateProvider;
    private final AtomicPersistence persistence;
    private final int capacity;
    private final LongSupplier nanoTime;
    private final Map<TickKey, ScheduledTick> pendingByKey = new HashMap<>();
    /** Pending keys per chunk, so evicting one chunk does not walk every pending tick in the world. */
    private final Map<Long, Set<TickKey>> pendingKeysByChunk = new HashMap<>();
    /**
     * Per-chunk count of resident queue changes (install, settlement progress, removal). A caller
     * that prepares a chunk recovery off the owner thread records the stamp first and accepts the
     * prepared rows only if the stamp is unchanged: a row settled or admitted in between would
     * otherwise be judged against a stale durable read.
     */
    private final Map<Long, Long> residentStamps = new HashMap<>();

    /** Current resident-change stamp of one chunk; see {@link #residentStamps}. */
    public long residentStamp(int chunkX, int chunkZ) {
        return residentStamps.getOrDefault(chunkStampKey(chunkX, chunkZ), 0L);
    }

    private static long chunkStampKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private void touchResident(ScheduledTick tick) {
        residentStamps.merge(chunkStampKey(Math.floorDiv(tick.x(), Blocks.CHUNK_X),
                Math.floorDiv(tick.z(), Blocks.CHUNK_Z)), 1L, Long::sum);
    }
    private final Map<String, String> payloadBySourceLane = new HashMap<>();
    /**
     * Exact plans whose durable settlement or live publication has not completed. Keeping the
     * plan, rather than recomputing it on a retry, prevents a changed live overlay from changing
     * the mutation authenticated by an unknown or already-committed result.
     */
    private final Map<TickKey, PendingSettlement> pendingSettlements = new LinkedHashMap<>();
    private final Map<TickKey, SettlementResult> completedSettlements = new HashMap<>();
    private final Map<TickKey, DurablePublication> begunPublications = new HashMap<>();
    /** A cold prefix is visited once per pass, even when scanning spans several owner turns. */
    private final Map<Lane, LaneScan> laneScans = new java.util.EnumMap<>(Lane.class);

    private static final class LaneScan {
        private final long dueAt;
        private ScheduledTick last;
        private ScheduledTick current;
        private boolean unavailable;

        private LaneScan(long dueAt) { this.dueAt = dueAt; }
    }
    private final TreeSet<ScheduledTick> blockQueue = new TreeSet<>(TICK_ORDER);
    private final TreeSet<ScheduledTick> fluidQueue = new TreeSet<>(TICK_ORDER);
    private Thread drainOwner;
    private boolean draining;
    /**
     * Durable steps handed to the executor and not yet collected. A step outlives the drain turn
     * that started it: the owner waits only inside its own slice, and whatever is still running
     * when that slice ends is collected by a later turn instead of being started again.
     */
    private final Map<StepKey, OffThreadStep> offThreadSteps = new ConcurrentHashMap<>();
    /**
     * Publications already applied to the live world and waiting only for their durable ACK.
     * A drain turn that ends between the publication and its ACK must resume at the ACK, never
     * republish: the mutation has already been applied to the world by then.
     */
    private final Map<TickKey, DurablePublication> awaitingAcknowledgement = new HashMap<>();
    private volatile SettlementExecutor settlementExecutor;

    /** Legacy-only compatibility entry point; versioned worlds must pass their bound provider. */
    public FinalCarrierTickScheduler(long worldId, AtomicPersistence persistence) {
        this(worldId, persistence, MAX_PENDING_TICKS, System::nanoTime);
    }

    public FinalCarrierTickScheduler(long worldId, AtomicPersistence persistence,
            IntFunction<NeutralFinalChunk.StateOverride> defaultStateProvider) {
        this(worldId, persistence, MAX_PENDING_TICKS, System::nanoTime, defaultStateProvider);
    }

    FinalCarrierTickScheduler(long worldId, AtomicPersistence persistence, int capacity) {
        this(worldId, persistence, capacity, System::nanoTime);
    }

    FinalCarrierTickScheduler(long worldId, AtomicPersistence persistence, int capacity,
            LongSupplier nanoTime) {
        this(worldId, persistence, capacity, nanoTime, id -> ProducerAuthorities.defaultState(
                WorldGenerationProfiles.newWorldProfile(), id));
    }

    private FinalCarrierTickScheduler(long worldId, AtomicPersistence persistence, int capacity,
            LongSupplier nanoTime, IntFunction<NeutralFinalChunk.StateOverride> defaultStateProvider) {
        this.worldId = worldId;
        this.defaultStateProvider = Objects.requireNonNull(defaultStateProvider, "default state provider");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        if (capacity < 1 || capacity > MAX_PENDING_TICKS) {
            throw new IllegalArgumentException("capacity outside 1..65536: " + capacity);
        }
        this.capacity = capacity;
    }

    public AdmissionStatus admitBlockLane(CarrierReceipt receipt, long nowMcTick,
            boolean structureReady, List<BlockTick> ticks) {
        PreparedAdmission prepared = prepareBlockLane(receipt, nowMcTick, structureReady, ticks);
        Admission admission = persistAdmission(prepared);
        acceptAdmission(prepared, admission);
        return admission.status();
    }

    public PreparedAdmission prepareBlockLane(CarrierReceipt receipt, long nowMcTick,
            boolean structureReady, List<BlockTick> ticks) {
        requireReceipt(receipt, Lane.BLOCK, structureReady);
        Objects.requireNonNull(ticks, "ticks");
        requireNow(nowMcTick);
        if (!receipt.lanePayloadFingerprint().equals(blockPayloadFingerprint(ticks))) {
            throw new IllegalArgumentException("carrier source/payload receipt mismatch");
        }
        List<ScheduledTick> candidates = new ArrayList<>(ticks.size());
        for (BlockTick tick : ticks) {
            requireSupportedBlockTick(tick);
            candidates.add(scheduled(receipt, tick.packed(), tick.key(), tick.blockId(),
                    Math.addExact(nowMcTick, tick.delay()), tick.priority(),
                    tick.subTickOrder()));
        }
        return new PreparedAdmission(receipt, nowMcTick, firstCandidates(candidates));
    }

    public AdmissionStatus admitFluidLane(CarrierReceipt receipt, long nowMcTick,
            boolean structureReady, List<FluidTick> ticks) {
        PreparedAdmission prepared = prepareFluidLane(receipt, nowMcTick, structureReady, ticks);
        Admission admission = persistAdmission(prepared);
        acceptAdmission(prepared, admission);
        return admission.status();
    }

    public PreparedAdmission prepareFluidLane(CarrierReceipt receipt, long nowMcTick,
            boolean structureReady, List<FluidTick> ticks) {
        requireReceipt(receipt, Lane.FLUID, structureReady);
        Objects.requireNonNull(ticks, "ticks");
        requireNow(nowMcTick);
        if (!receipt.lanePayloadFingerprint().equals(fluidPayloadFingerprint(ticks))) {
            throw new IllegalArgumentException("carrier source/payload receipt mismatch");
        }
        List<ScheduledTick> candidates = new ArrayList<>(ticks.size());
        for (FluidTick tick : ticks) {
            requireSupportedFluidTick(tick.key());
            candidates.add(scheduled(receipt, tick.packed(), tick.key(), -1,
                    Math.addExact(nowMcTick, tick.delay()), tick.priority(),
                    tick.subTickOrder()));
        }
        return new PreparedAdmission(receipt, nowMcTick, firstCandidates(candidates));
    }

    public Admission persistAdmission(PreparedAdmission prepared) {
        Objects.requireNonNull(prepared, "prepared admission");
        return Objects.requireNonNull(persistence.admitAndAcknowledge(
                prepared.receipt(), prepared.candidates(), prepared.admissionBaseMcTick(),
                capacity), "admission");
    }

    public void acceptAdmission(PreparedAdmission prepared, Admission admission) {
        Objects.requireNonNull(prepared, "prepared admission");
        Objects.requireNonNull(admission, "admission");
        if (admission.status() == AdmissionStatus.CAPACITY_REJECTED) {
            throw new IllegalStateException("final-carrier 65536-capacity contract rejected admission"
                    + " (configured capacity " + capacity + ")");
        }
        if (admission.status() == AdmissionStatus.ADMITTED) {
            Map<TickKey, ScheduledTick> expected = new HashMap<>();
            for (ScheduledTick candidate : prepared.candidates()) {
                if (expected.put(candidate.key(), candidate) != null) {
                    throw new IllegalStateException(
                            "prepared admission contains duplicate tick keys");
                }
            }
            Set<TickKey> returned = new HashSet<>();
            for (ScheduledTick durable : admission.durableTicks()) {
                ScheduledTick candidate = expected.get(durable.key());
                if (candidate == null || !returned.add(durable.key())
                        || durable.durableOrder() <= 0L
                        || !sameCandidateIdentity(durable, candidate)) {
                    throw new IllegalStateException(
                            "admission returned a tick outside the prepared candidate identity");
                }
            }
            if (returned.size() != expected.size()) {
                throw new IllegalStateException(
                        "admission did not return a durable disposition for every candidate");
            }
            if (!admission.consumedTicks().isEmpty()) {
                throw new IllegalStateException(
                        "fresh admission cannot expose consumed candidate accounting");
            }
        } else {
            Map<TickKey, ScheduledTick> expected = new HashMap<>();
            for (ScheduledTick candidate : prepared.candidates()) {
                if (expected.put(candidate.key(), candidate) != null) {
                    throw new IllegalStateException(
                            "prepared admission contains duplicate tick keys");
                }
            }
            Set<TickKey> returned = new HashSet<>();
            for (ScheduledTick durable : admission.durableTicks()) {
                ScheduledTick candidate = expected.get(durable.key());
                if (!durable.receipt().equals(prepared.receipt())
                        || durable.durableOrder() <= 0L
                        || candidate == null
                        || !returned.add(durable.key())
                        || !sameAlreadyAcknowledgedIdentity(durable, candidate)) {
                    throw new IllegalStateException(
                        "acknowledged admission returned an unauthenticated durable body");
                }
            }
            for (ScheduledTick durable : admission.consumedTicks()) {
                ScheduledTick candidate = expected.get(durable.key());
                if (!durable.receipt().equals(prepared.receipt())
                        || durable.durableOrder() <= 0L
                        || candidate == null
                        || !returned.add(durable.key())
                        || !sameAlreadyAcknowledgedIdentity(durable, candidate)) {
                    throw new IllegalStateException(
                            "acknowledged admission returned an unauthenticated consumed body");
                }
            }
            if (returned.size() != expected.size()) {
                throw new IllegalStateException(
                        "acknowledged admission did not account for every candidate");
            }
        }
        install(admission.durableTicks());
    }

    /** Loads durable rows after runtime construction without altering their absolute due times. */
    public void restoreWorld() {
        FinalCarrierTickRecoverySnapshot snapshot = Objects.requireNonNull(
                persistence.loadRecovery(worldId, MAX_PENDING_TICKS), "recovery snapshot");
        List<ScheduledTick> worldRows = snapshot.scheduled();
        List<DurablePublication> publications = snapshot.publications();
        if (publications.size() > MAX_PENDING_TICKS) {
            throw new FinalCarrierDurableStateException(
                    "durable publication batch exceeds bounded capacity");
        }
        Map<TickKey, DurablePublication> activePublications = new LinkedHashMap<>();
        Set<TickKey> residentKeys = new HashSet<>();
        Set<TickKey> publicationKeys = new HashSet<>();
        for (ScheduledTick row : worldRows) {
            Objects.requireNonNull(row, "world row");
            residentKeys.add(row.key());
        }
        for (DurablePublication publication : publications) {
            Objects.requireNonNull(publication, "durable publication");
            if (publication.tick().receipt().worldId() != worldId) {
                throw new FinalCarrierDurableStateException(
                        "durable publication belongs to another world");
            }
            if (!publicationKeys.add(publication.tick().key())) {
                throw new FinalCarrierDurableStateException(
                        "duplicate durable publication key in restore batch");
            }
            FinalCarrierConsumedTick.PublicationState state = publication.publicationState();
            if (state == FinalCarrierConsumedTick.PublicationState.REJECTED
                    || state == FinalCarrierConsumedTick.PublicationState.ACKNOWLEDGED) {
                if (residentKeys.contains(publication.tick().key())) {
                    throw new FinalCarrierDurableStateException(
                            "terminal durable publication overlaps a scheduled tick");
                }
                continue;
            }
            if (state != FinalCarrierConsumedTick.PublicationState.UNACKNOWLEDGED
                    && state != FinalCarrierConsumedTick.PublicationState.OUTCOME_UNKNOWN) {
                throw new FinalCarrierDurableStateException(
                        "durable publication has an invalid recovery state");
            }
            if (residentKeys.contains(publication.tick().key())) {
                throw new FinalCarrierDurableStateException(
                        "durable publication overlaps a scheduled tick");
            }
            activePublications.put(publication.tick().key(), publication);
            residentKeys.add(publication.tick().key());
        }
        List<ScheduledTick> installRows = new ArrayList<>(worldRows);
        installRows.addAll(activePublications.values().stream()
                .map(DurablePublication::tick).toList());
        Map<TickKey, PendingSettlement> stagedPending = new LinkedHashMap<>(pendingSettlements);
        for (DurablePublication publication : activePublications.values().stream()
                .sorted(Comparator.comparing(DurablePublication::tick,
                        Comparator.comparing(ScheduledTick::lane).thenComparing(TICK_ORDER))).toList()) {
            PendingSettlement pending = new PendingSettlement(
                    publication.tick(), publication.disposition(), publication.mutation());
            PendingSettlement previous = stagedPending.put(publication.tick().key(), pending);
            if (previous != null && !previous.equals(pending)) {
                throw new FinalCarrierDurableStateException(
                        "durable publication recovery plan conflicts with resident plan");
            }
        }
        install(installRows);
        for (PendingSettlement pending : stagedPending.values()) touchResident(pending.tick());
        pendingSettlements.putAll(stagedPending);
    }

    /** Reinstalls only one evicted chunk; overdue entries remain due immediately. */
    public void activateChunk(int chunkX, int chunkZ) {
        acceptChunkRecovery(prepareChunkRecovery(chunkX, chunkZ));
    }

    /**
     * Loads through the existing authenticated transaction boundary, then validates detached rows
     * without consulting or changing resident scheduler state. This adds no transaction boundary
     * or worker submission; joined callers retain the service's existing flush and lock behavior.
     */
    public PreparedChunkRecovery prepareChunkRecovery(int chunkX, int chunkZ) {
        List<ScheduledTick> ticks = validateRecoveredTicks(
                persistence.loadChunk(worldId, chunkX, chunkZ), worldId, chunkX, chunkZ, capacity);
        return new PreparedChunkRecovery(worldId, chunkX, chunkZ, ticks);
    }

    /** Owner-side installation; resident conflicts and capacity are checked before any mutation. */
    public void acceptChunkRecovery(PreparedChunkRecovery prepared) {
        Objects.requireNonNull(prepared, "prepared recovery");
        if (prepared.worldId() != worldId) {
            throw new IllegalStateException("prepared tick recovery belongs to another world");
        }
        installValidated(prepared.ticks());
    }

    /** A planned mutation is a causal barrier; keep its origin and destinations resident until ACK. */
    public Set<Long> pendingPublicationChunks() {
        Set<Long> chunks = new HashSet<>();
        for (PendingSettlement pending : pendingSettlements.values()) {
            ScheduledTick tick = pending.tick();
            chunks.add(chunkStampKey(Math.floorDiv(tick.x(), Blocks.CHUNK_X),
                    Math.floorDiv(tick.z(), Blocks.CHUNK_Z)));
            for (BlockMutation block : pending.mutation().blocks()) {
                chunks.add(chunkStampKey(Math.floorDiv(block.x(), Blocks.CHUNK_X),
                        Math.floorDiv(block.z(), Blocks.CHUNK_Z)));
            }
        }
        return Set.copyOf(chunks);
    }

    /** Removes only resident queue state. Durable rows and their original due times remain intact. */
    public void evictChunk(int chunkX, int chunkZ) {
        Set<TickKey> keys = pendingKeysByChunk.get(chunkStampKey(chunkX, chunkZ));
        if (keys == null || keys.isEmpty()) return;
        List<ScheduledTick> evicted = new ArrayList<>();
        for (TickKey key : List.copyOf(keys)) {
            ScheduledTick tick = pendingByKey.get(key);
            if (tick != null && !pendingSettlements.containsKey(key)) evicted.add(tick);
        }
        for (ScheduledTick tick : evicted) remove(tick);
    }

    /** Exact-cell guard used to suppress the legacy generated-fluid wakeup for an admitted FTIK. */
    public boolean suppressesLegacyFluidAt(int x, int y, int z) {
        for (TickKey key : pendingByKey.keySet()) {
            if (key.lane() == Lane.FLUID && key.x() == x && key.y() == y && key.z() == z) {
                return true;
            }
        }
        return false;
    }

    /**
     * Drains BLOCK before FLUID. A persistence retry leaves the head and its due time untouched and
     * stops this drain, preserving ordering across retry.
     */
    public int drainDue(long nowMcTick, LiveTypes liveTypes, TickSemantics semantics,
            CommittedMutationSink committedSink, int budget) {
        return drainDueReport(nowMcTick, liveTypes, semantics, committedSink, budget,
                Long.MAX_VALUE).processed();
    }

    /**
     * Wall-clock bounded drain. Every settled row costs one durable transaction, so a first-generation
     * neighbourhood can make tens of thousands of rows due in the same owner turn; without a deadline the
     * owner turn is unbounded. Each lane retains TICK_ORDER; the BLOCK slice precedes the reserved
     * FLUID slice. Unavailable rows retain their exact due time while a bounded, resumable scan
     * visits the rest of that lane. The first available due row may settle before the deadline; a turn with only
     * unavailable heads may settle zero rows.
     */
    public int drainDue(long nowMcTick, LiveTypes liveTypes, TickSemantics semantics,
            CommittedMutationSink committedSink, int budget, long deadlineNanos) {
        return drainDueReport(nowMcTick, liveTypes, semantics, committedSink, budget,
                deadlineNanos).processed();
    }

    public DrainReport drainDueReport(long nowMcTick, LiveTypes liveTypes, TickSemantics semantics,
            CommittedMutationSink committedSink, int budget) {
        return drainDueReport(nowMcTick, liveTypes, semantics, committedSink, budget,
                Long.MAX_VALUE);
    }

    public DrainReport drainDueReport(long nowMcTick, LiveTypes liveTypes, TickSemantics semantics,
            CommittedMutationSink committedSink, int budget, long deadlineNanos) {
        Objects.requireNonNull(liveTypes, "liveTypes");
        Objects.requireNonNull(semantics, "semantics");
        Objects.requireNonNull(committedSink, "committedSink");
        if (budget < 0) throw new IllegalArgumentException("negative tick budget");
        requireNow(nowMcTick);
        if (!beginDrain()) return new DrainReport(0, DrainStatus.REENTRANT);
        try {
            // The half-slice limits starting new BLOCK work, not completing an already planned
            // mutation. A retained plan/publication (including FLUID from a prior turn) is a
            // causal barrier: finish it before planning either lane against the live overlay.
            long nowNanos = nanoTime.getAsLong();
            long blockDeadline = deadlineNanos == Long.MAX_VALUE ? Long.MAX_VALUE
                    : nowNanos + Math.max(0L, deadlineNanos - nowNanos) / 2;
            int blockCount = 0;
            int fluidCount = 0;
            while (!pendingSettlements.isEmpty()) {
                Lane lane = pendingSettlements.values().iterator().next().tick().lane();
                if ((lane == Lane.BLOCK ? blockCount : fluidCount) >= budget) {
                    return new DrainReport(blockCount + fluidCount, DrainStatus.DRAINED);
                }
                LaneDrainResult resumed = drainLane(lane, nowMcTick, liveTypes, semantics,
                        committedSink, 1, deadlineNanos, deadlineNanos, true);
                if (lane == Lane.BLOCK) blockCount += resumed.processed();
                else fluidCount += resumed.processed();
                if (resumed.status() != DrainStatus.DRAINED) {
                    return new DrainReport(blockCount + fluidCount, resumed.status());
                }
            }
            LaneDrainResult blocks = drainLane(Lane.BLOCK, nowMcTick, liveTypes, semantics,
                    committedSink, budget - blockCount, blockDeadline, deadlineNanos, false);
            blockCount += blocks.processed();
            if (blocks.status() == DrainStatus.RETRY || !pendingSettlements.isEmpty()) {
                return new DrainReport(blockCount + fluidCount, blocks.status());
            }
            if (deadlineReached(deadlineNanos)) {
                return new DrainReport(blockCount + fluidCount, DrainStatus.DEADLINE);
            }
            LaneDrainResult fluids = drainLane(Lane.FLUID, nowMcTick, liveTypes, semantics,
                    committedSink, budget - fluidCount, deadlineNanos, deadlineNanos, false);
            DrainStatus status = fluids.status() == DrainStatus.DRAINED
                    ? blocks.status() : fluids.status();
            return new DrainReport(blockCount + fluidCount + fluids.processed(), status);
        } finally {
            endDrain();
        }
    }

    private LaneDrainResult drainLane(Lane lane, long nowMcTick, LiveTypes liveTypes,
            TickSemantics semantics, CommittedMutationSink committedSink, int budget,
            long startDeadlineNanos, long deadlineNanos, boolean resumeOnly) {
        TreeSet<ScheduledTick> queue = queue(lane);
        int processed = 0;
        while (processed < budget && !queue.isEmpty()) {
            if (deadlineReached(deadlineNanos)) {
                return new LaneDrainResult(processed, DrainStatus.DEADLINE);
            }
            PendingSettlement pending = resumeOnly ? pendingSettlements.values().stream()
                    .filter(value -> value.tick().lane() == lane).findFirst().orElse(null) : null;
            ScheduledTick tick;
            if (pending != null) {
                tick = pending.tick();
            } else {
                if (resumeOnly) break;
                if (deadlineReached(startDeadlineNanos)) {
                    return new LaneDrainResult(processed, DrainStatus.DEADLINE);
                }
                LaneScan scan = laneScans.computeIfAbsent(lane, ignored -> new LaneScan(nowMcTick));
                tick = scan.last == null ? queue.first() : queue.higher(scan.last);
                if (tick == null || tick.dueTick() > scan.dueAt) {
                    laneScans.remove(lane);
                    return new LaneDrainResult(processed,
                            scan.unavailable ? DrainStatus.UNAVAILABLE : DrainStatus.DRAINED);
                }
                scan.current = tick;
                pending = pendingSettlements.get(tick.key());
            }
            if (pending == null) {
                DueDisposition disposition;
                TickMutation mutation;
                try {
                    String liveType = requireCanonicalKey(liveTypes.canonicalTypeAt(
                            tick.lane(), tick.x(), tick.y(), tick.z()));
                    if (!liveType.equals(tick.typeKey())) {
                        disposition = DueDisposition.LIVE_TYPE_NO_OP;
                        mutation = TickMutation.NONE;
                    } else {
                        if (deadlineReached(deadlineNanos)) {
                            return new LaneDrainResult(processed, DrainStatus.DEADLINE);
                        }
                        disposition = DueDisposition.EXECUTE;
                        mutation = Objects.requireNonNull(
                                semantics.plan(tick, liveTypes), "mutation");
                    }
                } catch (UnavailableNeighborhood unavailable) {
                    // No plan or durable work exists for this row. Keep its absolute due time,
                    // but let ready rows behind it run; resume the scan after it next turn.
                    LaneScan scan = laneScans.get(lane);
                    scan.last = tick;
                    scan.current = null;
                    scan.unavailable = true;
                    continue;
                }
                pending = new PendingSettlement(tick, disposition, mutation);
                pendingSettlements.put(tick.key(), pending);
                touchResident(tick);
            } else if (!pending.tick().equals(tick)) {
                throw new IllegalStateException("pending settlement identity drift for " + tick.key());
            }
            if (deadlineReached(deadlineNanos)) {
                return new LaneDrainResult(processed, DrainStatus.DEADLINE);
            }
            final PendingSettlement plan = pending;
            PendingSettlement exactPending = null;
            // A turn that ended between the live publication and its durable ACK resumes at the
            // ACK. The world already carries this mutation; settling and publishing it again
            // would apply it twice for no durable gain.
            DurablePublication publication = awaitingAcknowledgement.get(tick.key());
            if (publication == null) {
                SettlementResult settled = completedSettlements.get(tick.key());
                if (settled == null) {
                    settled = settlementStep(tick, SettlementStage.SETTLE,
                            deadlineNanos, () -> persistence.settleAtomicallyWithPlan(
                                    tick, plan.disposition(), plan.mutation()));
                    if (settled != null && settled.status() != Settlement.RETRY) {
                        completedSettlements.put(tick.key(), settled);
                    }
                }
                if (settled == null) {
                    return new LaneDrainResult(processed, pendingStepStatus(deadlineNanos));
                }
                if (settled.status() == Settlement.RETRY) {
                    return new LaneDrainResult(processed, DrainStatus.RETRY);
                }
                if (settled.status() == Settlement.COMMITTED
                        && !settled.mutation().equals(plan.mutation())) {
                    throw new FinalCarrierDurableStateException(
                            "fresh settlement returned a different mutation body");
                }
                exactPending = new PendingSettlement(
                        tick, plan.disposition(), settled.mutation());
                pendingSettlements.put(tick.key(), exactPending);
                touchResident(tick);
                final SettlementResult settlement = settled;
                publication = begunPublications.get(tick.key());
                if (publication == null) {
                    publication = settlementStep(tick, SettlementStage.BEGIN, deadlineNanos,
                            () -> persistence.beginOutcomeUnknown(
                                    tick, plan.disposition(), settlement));
                    if (publication != null) begunPublications.put(tick.key(), publication);
                }
                if (publication == null) {
                    return new LaneDrainResult(processed, pendingStepStatus(deadlineNanos));
                }
                validatePublication(publication, settled, tick, plan.disposition());
                if (deadlineReached(deadlineNanos)) {
                    return new LaneDrainResult(processed, DrainStatus.DEADLINE);
                }
                // Both statuses identify a durable outcome. The cached plan is the exact value to
                // recover after an unknown result; never replace it with a fresh live replan.
                if (plan.disposition() == DueDisposition.EXECUTE) {
                    if (deadlineReached(deadlineNanos)) {
                        return new LaneDrainResult(processed, DrainStatus.DEADLINE);
                    }
                    try {
                        // Availability only: a committed mutation must never be replanned or
                        // discarded because the now-live block differs from its original type.
                        liveTypes.canonicalTypeAt(tick.lane(), tick.x(), tick.y(), tick.z());
                    } catch (UnavailableNeighborhood unavailable) {
                        return new LaneDrainResult(processed, DrainStatus.UNAVAILABLE);
                    }
                    committedSink.publish(tick, publication.mutation());
                }
                awaitingAcknowledgement.put(tick.key(), publication);
            } else {
                exactPending = pendingSettlements.get(tick.key());
            }
            final DurablePublication acknowledged = publication;
            if (settlementStep(tick, SettlementStage.ACKNOWLEDGE, deadlineNanos, () -> {
                persistence.acknowledgePublication(acknowledged);
                return Boolean.TRUE;
            }) == null) {
                return new LaneDrainResult(processed, pendingStepStatus(deadlineNanos));
            }
            // Publication returned normally. Only now may the scheduler queue row be released;
            // a sink exception leaves both maps untouched for deterministic retry.
            awaitingAcknowledgement.remove(tick.key());
            if (exactPending != null) pendingSettlements.remove(tick.key(), exactPending);
            LaneScan scan = laneScans.get(lane);
            if (scan != null && tick.equals(scan.current)) {
                scan.last = tick;
                scan.current = null;
            }
            remove(tick);
            processed++;
        }
        if (queue.isEmpty()) laneScans.remove(lane);
        return new LaneDrainResult(processed, DrainStatus.DRAINED);
    }

    private record PendingSettlement(ScheduledTick tick, DueDisposition disposition,
            TickMutation mutation) {
        private PendingSettlement {
            Objects.requireNonNull(tick, "pending tick");
            Objects.requireNonNull(disposition, "pending disposition");
            Objects.requireNonNull(mutation, "pending mutation");
            if (disposition == DueDisposition.LIVE_TYPE_NO_OP
                    && (!mutation.blocks().isEmpty() || !mutation.drops().isEmpty())) {
                throw new IllegalArgumentException("no-op pending settlement must not mutate");
            }
        }
    }

    private static void validatePublication(DurablePublication publication,
            SettlementResult settlement, ScheduledTick tick, DueDisposition disposition) {
        if (!publication.tick().equals(tick) || publication.disposition() != disposition
                || !publication.mutation().equals(settlement.mutation())
                || !publication.publicationKey().equals(settlement.publicationKey())
                || !publication.mutationDigest().equals(settlement.mutationDigest())
                || !MessageDigest.isEqual(publication.mutationBody(), settlement.mutationBody())
                || (publication.publicationState()
                        != FinalCarrierConsumedTick.PublicationState.OUTCOME_UNKNOWN
                        && publication.publicationState()
                        != FinalCarrierConsumedTick.PublicationState.ACKNOWLEDGED)) {
            throw new FinalCarrierDurableStateException(
                    "durable publication does not match settlement result");
        }
    }

    /**
     * A semantic read that left the resident neighbourhood. It is the world owner's signal that this
     * due row cannot be planned yet - never that the immutable carrier lane is malformed.
     */
    public static final class UnavailableNeighborhood extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        public UnavailableNeighborhood(String message) {
            super(message);
        }
    }

    private record LaneDrainResult(int processed, DrainStatus status) { }

    public int pendingCount() {
        return pendingByKey.size();
    }

    public List<ScheduledTick> pendingTicks() {
        List<ScheduledTick> ticks = new ArrayList<>(pendingByKey.values());
        ticks.sort(Comparator.comparing(ScheduledTick::lane).thenComparing(TICK_ORDER));
        return List.copyOf(ticks);
    }

    private void install(Collection<ScheduledTick> ticks) {
        installValidated(validateRecoveredTicks(ticks, worldId, null, null, capacity));
    }

    /** Validates only the detached batch; safe independently of the resident scheduler maps. */
    private List<ScheduledTick> validateRecoveredTicks(Collection<ScheduledTick> ticks,
            long worldId, Integer expectedChunkX, Integer expectedChunkZ, int capacity) {
        Objects.requireNonNull(ticks, "ticks");
        List<ScheduledTick> incoming = List.copyOf(ticks);
        Set<TickKey> incomingKeys = new HashSet<>();
        Map<String, String> incomingPayloads = new HashMap<>();
        TreeSet<ScheduledTick> incomingBlocks = new TreeSet<>(TICK_ORDER);
        TreeSet<ScheduledTick> incomingFluids = new TreeSet<>(TICK_ORDER);
        for (ScheduledTick tick : incoming) {
            if (!incomingKeys.add(tick.key())) {
                throw new IllegalStateException("duplicate durable tick key in install batch");
            }
            if (tick.receipt().worldId() != worldId) {
                throw new IllegalStateException("durable scheduled tick belongs to another world");
            }
            if (expectedChunkX != null && (tick.receipt().chunkX() != expectedChunkX
                    || tick.receipt().chunkZ() != expectedChunkZ
                    || Math.floorDiv(tick.x(), Blocks.CHUNK_X) != expectedChunkX
                    || Math.floorDiv(tick.z(), Blocks.CHUNK_Z) != expectedChunkZ)) {
                throw new IllegalStateException("durable scheduled tick belongs to another chunk");
            }
            validateDurableTick(tick);
            String previousPayload = incomingPayloads.putIfAbsent(
                    sourceLane(tick.receipt()), tick.receipt().lanePayloadFingerprint());
            if (previousPayload != null
                    && !previousPayload.equals(tick.receipt().lanePayloadFingerprint())) {
                throw new IllegalStateException("durable carrier source has conflicting lane payloads");
            }
            TreeSet<ScheduledTick> incomingQueue = tick.lane() == Lane.BLOCK
                    ? incomingBlocks : incomingFluids;
            if (!incomingQueue.add(tick)) {
                throw new IllegalStateException("duplicate durable admission order in tick lane");
            }
        }
        if (incomingBlocks.size() > capacity || incomingFluids.size() > capacity) {
            throw new IllegalStateException("durable final-carrier lane exceeds 65536 capacity");
        }
        return incoming;
    }

    private void installValidated(List<ScheduledTick> incoming) {
        // 들어온 것만 따로 모아 검증하고, 다 통과하면 반영한다. 예전에는 대기 중인 틱 전체(최대 65536×2)를 매번
        // 복사해 그 사본에 넣어 보며 검증해서, 설치 한 번이 대기량에 비례했다(월드 틱 스레드의 약 18%).
        Map<TickKey, ScheduledTick> newByKey = new HashMap<>();
        Map<String, String> newPayloads = new HashMap<>();
        TreeSet<ScheduledTick> newBlocks = new TreeSet<>(TICK_ORDER);
        TreeSet<ScheduledTick> newFluids = new TreeSet<>(TICK_ORDER);
        List<ScheduledTick> additions = new ArrayList<>();
        for (ScheduledTick tick : incoming) {
            String sourceLane = sourceLane(tick.receipt());
            String previousPayload = payloadBySourceLane.get(sourceLane);
            if (previousPayload == null) {
                previousPayload = newPayloads.putIfAbsent(sourceLane, tick.receipt().lanePayloadFingerprint());
            }
            if (previousPayload != null
                    && !previousPayload.equals(tick.receipt().lanePayloadFingerprint())) {
                throw new IllegalStateException("durable carrier source has conflicting lane payloads");
            }
            ScheduledTick existing = newByKey.get(tick.key());
            if (existing == null) existing = pendingByKey.get(tick.key());
            if (existing != null) {
                if (!existing.equals(tick)) {
                    throw new IllegalStateException("durable first-admission conflict for " + tick.key());
                }
                continue;
            }
            newByKey.put(tick.key(), tick);
            boolean block = tick.lane() == Lane.BLOCK;
            TreeSet<ScheduledTick> liveQueue = block ? blockQueue : fluidQueue;
            TreeSet<ScheduledTick> newQueue = block ? newBlocks : newFluids;
            if (liveQueue.contains(tick) || !newQueue.add(tick)) {
                throw new IllegalStateException("duplicate durable admission order in tick lane");
            }
            additions.add(tick);
        }
        if (blockQueue.size() + newBlocks.size() > capacity || fluidQueue.size() + newFluids.size() > capacity) {
            throw new IllegalStateException("durable final-carrier lane exceeds 65536 capacity");
        }
        Map<String, String> stagedPayloads = newPayloads;
        for (Map.Entry<String, String> entry : stagedPayloads.entrySet()) {
            if (!payloadBySourceLane.containsKey(entry.getKey())) {
                payloadBySourceLane.put(entry.getKey(), entry.getValue());
            }
        }
        for (ScheduledTick tick : additions) {
            touchResident(tick);
            pendingByKey.put(tick.key(), tick);
            pendingKeysByChunk.computeIfAbsent(chunkStampKey(Math.floorDiv(tick.x(), Blocks.CHUNK_X),
                    Math.floorDiv(tick.z(), Blocks.CHUNK_Z)), ignored -> new LinkedHashSet<>()).add(tick.key());
            if (!queue(tick.lane()).add(tick)) {
                throw new IllegalStateException("resident durable tick install changed during validation");
            }
        }
    }

    /**
     * Attaches the worker that runs durable settlement steps off this drain's thread.
     *
     * <p>Without one, every step runs inline exactly as before, which is what a focused in-memory
     * fixture wants. With one, the world owner never takes a database row lock: it hands each
     * step over and waits for the answer inside the wall-clock slice the drain already owns.</p>
     */
    public void installSettlementExecutor(SettlementExecutor executor) {
        this.settlementExecutor = executor;
    }

    /**
     * Runs one durable settlement step, off this thread when an executor is installed.
     *
     * <p>Returns {@code null} when the step has not produced its result yet — either the executor
     * would not take it, or the drain's slice ended while it was still running. The step is then
     * left in flight and collected by a later turn rather than started again, so a handed-off
     * transaction is never duplicated by the wait giving up on it.</p>
     */
    @SuppressWarnings("unchecked")
    private <T> T settlementStep(ScheduledTick tick, SettlementStage stage, long deadlineNanos,
            java.util.function.Supplier<T> durableStep) {
        SettlementExecutor executor = settlementExecutor;
        if (executor == null) return deadlineReached(deadlineNanos) ? null : durableStep.get();
        StepKey stepKey = new StepKey(tick.key(), stage);
        OffThreadStep step = offThreadSteps.get(stepKey);
        if (step == null) {
            if (deadlineReached(deadlineNanos)) return null;
            OffThreadStep started = new OffThreadStep();
            offThreadSteps.put(stepKey, started);
            boolean accepted;
            try {
                accepted = executor.submit(() -> {
                    try {
                        started.value = durableStep.get();
                    } catch (RuntimeException failure) {
                        started.failure = failure;
                    } catch (Error fatal) {
                        started.fatal = fatal;
                    } finally {
                        started.done = true;
                    }
                });
            } catch (RuntimeException | Error rejected) {
                offThreadSteps.remove(stepKey);
                throw rejected;
            }
            if (!accepted) {
                offThreadSteps.remove(stepKey);
                return null;
            }
            step = started;
        }
        while (!step.done) {
            if (deadlineReached(deadlineNanos)) return null;
            LockSupport.parkNanos(SETTLEMENT_POLL_NANOS);
        }
        offThreadSteps.remove(stepKey);
        if (step.fatal != null) throw step.fatal;
        if (step.failure != null) throw step.failure;
        return (T) step.value;
    }

    /** The status a lane reports when a handed-off step has not answered yet. */
    private DrainStatus pendingStepStatus(long deadlineNanos) {
        return deadlineReached(deadlineNanos) ? DrainStatus.DEADLINE : DrainStatus.RETRY;
    }

    private boolean beginDrain() {
        synchronized (this) {
            Thread current = Thread.currentThread();
            if (drainOwner == null) drainOwner = current;
            if (drainOwner != current) {
                throw new IllegalStateException("final-carrier drain is owner-thread-only");
            }
            if (draining) return false;
            draining = true;
            return true;
        }
    }

    private void endDrain() {
        synchronized (this) {
            draining = false;
        }
    }

    private boolean deadlineReached(long deadlineNanos) {
        return deadlineNanos != Long.MAX_VALUE && nanoTime.getAsLong() >= deadlineNanos;
    }

    private void remove(ScheduledTick tick) {
        touchResident(tick);
        awaitingAcknowledgement.remove(tick.key());
        completedSettlements.remove(tick.key());
        begunPublications.remove(tick.key());
        if (pendingByKey.remove(tick.key(), tick)) {
            queue(tick.lane()).remove(tick);
            long chunk = chunkStampKey(Math.floorDiv(tick.x(), Blocks.CHUNK_X),
                    Math.floorDiv(tick.z(), Blocks.CHUNK_Z));
            Set<TickKey> keys = pendingKeysByChunk.get(chunk);
            if (keys != null && keys.remove(tick.key()) && keys.isEmpty()) pendingKeysByChunk.remove(chunk);
        }
    }

    private TreeSet<ScheduledTick> queue(Lane lane) {
        return lane == Lane.BLOCK ? blockQueue : fluidQueue;
    }

    private void requireReceipt(CarrierReceipt receipt, Lane lane, boolean structureReady) {
        Objects.requireNonNull(receipt, "receipt");
        if (!structureReady) {
            throw new IllegalStateException("final-carrier tick admission requires structure-ready");
        }
        if (receipt.worldId() != worldId || receipt.lane() != lane) {
            throw new IllegalArgumentException("carrier receipt does not match scheduler world/lane");
        }
    }

    private void requireSupportedBlockTick(BlockTick tick) {
        Objects.requireNonNull(tick, "tick");
        NeutralFinalChunk.StateOverride state;
        try {
            state = defaultStateProvider.apply(tick.blockId());
        } catch (IllegalStateException unsupported) {
            throw new IllegalArgumentException("unsupported final-carrier block tick: "
                    + tick.key(), unsupported);
        }
        if (state == null || state.blockId() != tick.blockId()) {
            throw new IllegalArgumentException("default state provider returned another block ID");
        }
        boolean keyMatchesId = state.blockKey().equals(tick.key())
                || (tick.blockId() == Blocks.AIR && tick.key().equals("minecraft:cave_air"));
        boolean supported = keyMatchesId
                && (tick.key().equals("minecraft:cave_air")
                || tick.key().equals("minecraft:pale_hanging_moss")
                || tick.key().equals("minecraft:sugar_cane")
                || tick.key().equals("minecraft:sulfur_spike")
                || tick.key().equals("minecraft:pointed_dripstone")
                || state.isLeavesTag());
        if (!supported) {
            throw new IllegalArgumentException("unsupported final-carrier block tick: "
                    + tick.key());
        }
    }

    private static ScheduledTick scheduled(CarrierReceipt receipt, int packed, String typeKey,
            int expectedBlockId, long dueTick, TickPriority priority, long subTickOrder) {
        int localX = packed % Blocks.CHUNK_X;
        int yz = packed / Blocks.CHUNK_X;
        int localZ = yz % Blocks.CHUNK_Z;
        int localY = yz / Blocks.CHUNK_Z;
        TickKey key = new TickKey(receipt.lane(),
                receipt.chunkX() * Blocks.CHUNK_X + localX,
                Blocks.MIN_Y + localY,
                receipt.chunkZ() * Blocks.CHUNK_Z + localZ,
                typeKey);
        return new ScheduledTick(key, receipt, expectedBlockId, dueTick, priority, subTickOrder, -1);
    }

    private static List<ScheduledTick> firstCandidates(List<ScheduledTick> candidates) {
        Map<TickKey, ScheduledTick> first = new LinkedHashMap<>();
        for (ScheduledTick tick : candidates) first.putIfAbsent(tick.key(), tick);
        return List.copyOf(first.values());
    }

    private static boolean sameCandidateIdentity(ScheduledTick durable,
            ScheduledTick candidate) {
        return durable.key().equals(candidate.key())
                && durable.receipt().equals(candidate.receipt())
                && durable.expectedBlockId() == candidate.expectedBlockId()
                && durable.dueTick() == candidate.dueTick()
                && durable.priority() == candidate.priority()
                && durable.subTickOrder() == candidate.subTickOrder();
    }

    private static boolean sameAlreadyAcknowledgedIdentity(ScheduledTick durable,
            ScheduledTick candidate) {
        // Replay is allowed to rebuild the prepared due time from a later owner turn; the
        // durable consumed row is the authority for the original absolute due time.
        return durable.key().equals(candidate.key())
                && durable.receipt().equals(candidate.receipt())
                && durable.expectedBlockId() == candidate.expectedBlockId()
                && durable.priority() == candidate.priority()
                && durable.subTickOrder() == candidate.subTickOrder();
    }

    /**
     * 같은 술어를 미리 컴파일해 둔다. {@code String.matches}는 호출마다 패턴을 새로 컴파일하며,
     * 저장 월드 접속은 내구 틱 행마다 영수증을 만들면서 이 검사를 두 번씩 돈다. 접속 스레드
     * jstack 표본에서 상위에 잡힌 프레임이라 컴파일 비용만 걷어낸다. 판정은 그대로다.
     */
    private static final Pattern SHA256_LOWERCASE = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern CANONICAL_KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private static String requireCanonicalKey(String key) {
        Objects.requireNonNull(key, "typeKey");
        if (!CANONICAL_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("canonical ASCII type key required: " + key);
        }
        return key;
    }

    private static void requireNow(long nowMcTick) {
        if (nowMcTick < 0) throw new IllegalArgumentException("negative absolute Minecraft time");
    }

    private static byte[] encodePublicationBody(ScheduledTick tick,
            DueDisposition disposition, TickMutation mutation) {
        return FinalCarrierTickPublicationCodec.encode(
                new FinalCarrierTickPublication(tick, disposition, mutation), PUBLICATION_LIMITS);
    }

    private void validateDurableTick(ScheduledTick tick) {
        if (tick.durableOrder() <= 0) {
            throw new IllegalStateException("durable scheduled tick is missing admission order");
        }
        if (tick.key().lane() == Lane.BLOCK) {
            requireSupportedBlockTick(new BlockTick(0, tick.expectedBlockId(), tick.typeKey(), 0,
                    tick.priority(), tick.subTickOrder()));
        } else {
            try {
                requireSupportedFluidTick(tick.typeKey());
            } catch (IllegalArgumentException unsupported) {
                throw new IllegalStateException(
                        "unsupported durable fluid tick: " + tick.typeKey(), unsupported);
            }
        }
    }

    private static void requireSupportedFluidTick(String typeKey) {
        if (!List.of("minecraft:water", "minecraft:flowing_water",
                "minecraft:lava", "minecraft:flowing_lava").contains(typeKey)) {
            throw new IllegalArgumentException("unsupported final-carrier fluid tick: " + typeKey);
        }
    }

    public static CarrierReceipt blockReceipt(long worldId, int chunkX, int chunkZ,
            String sourceFingerprint, List<BlockTick> ticks) {
        Objects.requireNonNull(ticks, "ticks");
        return new CarrierReceipt(worldId, chunkX, chunkZ, sourceFingerprint,
                blockPayloadFingerprint(ticks), Lane.BLOCK);
    }

    public static CarrierReceipt fluidReceipt(long worldId, int chunkX, int chunkZ,
            String sourceFingerprint, List<FluidTick> ticks) {
        Objects.requireNonNull(ticks, "ticks");
        return new CarrierReceipt(worldId, chunkX, chunkZ, sourceFingerprint,
                fluidPayloadFingerprint(ticks), Lane.FLUID);
    }

    private static String blockPayloadFingerprint(List<BlockTick> ticks) {
        MessageDigest digest = sha256();
        putInt(digest, ticks.size());
        for (BlockTick tick : ticks) {
            putInt(digest, tick.packed()); putInt(digest, tick.blockId());
            putString(digest, tick.key()); putInt(digest, tick.delay());
            putInt(digest, tick.priority().value()); putLong(digest, tick.subTickOrder());
        }
        return hex(digest.digest());
    }

    private static String fluidPayloadFingerprint(List<FluidTick> ticks) {
        MessageDigest digest = sha256();
        putInt(digest, ticks.size());
        for (FluidTick tick : ticks) {
            putInt(digest, tick.packed()); putString(digest, tick.key());
            putInt(digest, tick.delay()); putInt(digest, tick.priority().value());
            putLong(digest, tick.subTickOrder());
        }
        return hex(digest.digest());
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static void putInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    private static void putLong(MessageDigest digest, long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }

    private static void putString(MessageDigest digest, String value) {
        byte[] bytes = requireCanonicalKey(value).getBytes(StandardCharsets.US_ASCII);
        putInt(digest, bytes.length); digest.update(bytes);
    }

    private static String hex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }

    private static String requireSha256(String value, String description) {
        Objects.requireNonNull(value, description);
        if (!SHA256_LOWERCASE.matcher(value).matches()) {
            throw new IllegalArgumentException(description + " must be lowercase SHA-256");
        }
        return value;
    }

    private static String sourceLane(CarrierReceipt receipt) {
        return receipt.worldId() + ":" + receipt.chunkX() + ":" + receipt.chunkZ() + ":"
                + receipt.sourceFingerprint() + ":" + receipt.lane();
    }
}
