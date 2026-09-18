package com.gameexpert.engine.persistence.finalcarrier;

import com.gameexpert.engine.TickSafetyTelemetry;

import com.gameexpert.block.entity.WorldBlockDiff;
import com.gameexpert.block.repository.WorldBlockDiffRepository;
import com.gameexpert.brewing.service.BrewingPersistenceService;
import com.gameexpert.chest.service.ChestPersistenceService;
import com.gameexpert.dispenser.service.DispenserPersistenceService;
import com.gameexpert.engine.FurnaceVariant;
import com.gameexpert.engine.WorldRuntime;
import com.gameexpert.engine.persistence.finalcarrier.archaeology.ArchaeologyBrushableAggregate;
import com.gameexpert.engine.persistence.finalcarrier.archaeology.ArchaeologyLootResolver;
import com.gameexpert.engine.persistence.finalcarrier.archaeology.WorldArchaeologyBrushable;
import com.gameexpert.engine.persistence.finalcarrier.archaeology.WorldArchaeologyBrushableRepository;
import com.gameexpert.engine.persistence.finalcarrier.blockentity.FinalCarrierBlockEntityPlan;
import com.gameexpert.engine.persistence.finalcarrier.bees.CanonicalBeePayload;
import com.gameexpert.engine.persistence.finalcarrier.bees.WorldCanonicalBeeInstallation;
import com.gameexpert.engine.persistence.finalcarrier.bees.WorldCanonicalBeeInstallationRepository;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootAssignmentPlan;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootAggregateMutation;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootAggregatePersistence;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootContainerKind;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootFirstOpenResult;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootStoredResolution;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootMapMaterializationService;
import com.gameexpert.engine.persistence.finalcarrier.loot.WorldCanonicalLootAssignment;
import com.gameexpert.engine.persistence.finalcarrier.loot.WorldCanonicalLootAssignmentRepository;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.StructureEntityAggregate;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.GeneratedStructureEntityFacts;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityState;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldGeneratedStructureEntityStateRepository;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldStructureEntity;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldStructureEntityRepository;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldStructureEntityIdSequence;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldStructureEntityIdSequenceRepository;
import com.gameexpert.engine.persistence.finalcarrier.spawner.SpawnerAggregate;
import com.gameexpert.furnace.service.FurnacePersistenceService;
import com.gameexpert.ground.dto.GroundItemSnapshot;
import com.gameexpert.ground.dto.GroundMutationCommand;
import com.gameexpert.ground.service.GroundEntityPersistenceService;
import com.gameexpert.engine.mob.MobRuntime;
import com.gameexpert.mob.dto.MobPersistenceSnapshot;
import com.gameexpert.mob.entity.WorldMob;
import com.gameexpert.mob.repository.WorldMobRepository;
import com.gameexpert.sign.service.SignBlockPersistenceService;
import com.gameexpert.engine.persistence.tick.FinalCarrierTickScheduler;
import com.gameexpert.engine.persistence.tick.FinalCarrierTickPublication;
import com.gameexpert.engine.persistence.tick.FinalCarrierTickPublicationCodec;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.TerrainAccessor;
import com.gameexpert.authority.versioned.NeutralFinalChunk;
import com.gameexpert.terrain.persistence.CanonicalWorldgenStore;
import com.gameexpert.api.persistence.WorldStore;
import com.gameexpert.engine.persistence.finalcarrier.reference.LateCanonicalLootPreparationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Spring transaction owner for all durable final-carrier lanes. */
@Service
public class FinalCarrierPersistenceService
        implements FinalCarrierTickScheduler.AtomicPersistence,
        WorldRuntime.FinalCarrierDurablePayloadInstaller {
    /** Terminal authenticated-payload conflict; callers must retire the exact canonical claim. */
    public static final class FinalCarrierLaneConflict extends RuntimeException {
        public FinalCarrierLaneConflict(String message) {
            super(message);
        }

        public FinalCarrierLaneConflict(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Retryable infrastructure failure; the surrounding transaction must roll back the claim. */
    public static final class FinalCarrierLaneRetryable extends RuntimeException {
        private final boolean worldLockContention;

        public FinalCarrierLaneRetryable(String message) {
            this(message, null, false);
        }

        public FinalCarrierLaneRetryable(String message, Throwable cause) {
            this(message, cause, false);
        }

        private FinalCarrierLaneRetryable(String message, Throwable cause,
                boolean worldLockContention) {
            super(message, cause);
            this.worldLockContention = worldLockContention;
        }

        public static FinalCarrierLaneRetryable worldLockContention(String message,
                Throwable cause) {
            return new FinalCarrierLaneRetryable(message, cause, true);
        }

        public boolean isWorldLockContention() { return worldLockContention; }
    }

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(FinalCarrierPersistenceService.class);
    private static final long OWNER_LOCK_CONTENTION_LOG_INTERVAL_NANOS = 60_000_000_000L;
    private static final java.util.concurrent.atomic.AtomicLong OWNER_LOCK_CONTENTION_COUNT =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong OWNER_LOCK_CONTENTION_LOG_NANOS =
            new java.util.concurrent.atomic.AtomicLong();
    private static final FinalCarrierTickPublicationCodec.Limits TICK_PUBLICATION_LIMITS =
            FinalCarrierTickPublicationCodec.Limits.DEFAULT;
    private static final List<FinalCarrierConsumedTick.PublicationState> ALL_PUBLICATION_STATES =
            List.of(FinalCarrierConsumedTick.PublicationState.UNACKNOWLEDGED,
                    FinalCarrierConsumedTick.PublicationState.OUTCOME_UNKNOWN,
                    FinalCarrierConsumedTick.PublicationState.ACKNOWLEDGED,
                    FinalCarrierConsumedTick.PublicationState.REJECTED);
    private static final List<FinalCarrierConsumedTick.PublicationState> RECOVERY_STATES =
            List.of(FinalCarrierConsumedTick.PublicationState.UNACKNOWLEDGED,
                    FinalCarrierConsumedTick.PublicationState.OUTCOME_UNKNOWN);
    private static final Comparator<TickPartitionKey> TICK_PARTITION_ORDER =
            Comparator.comparingLong(TickPartitionKey::worldId)
                    .thenComparingInt(TickPartitionKey::chunkX)
                    .thenComparingInt(TickPartitionKey::chunkZ)
                    .thenComparing(TickPartitionKey::lane)
                    .thenComparing(TickPartitionKey::sourceFingerprint);
    private static final Comparator<FinalCarrierTickScheduler.ScheduledTick> TICK_ORDER =
            Comparator.comparing(FinalCarrierTickScheduler.ScheduledTick::lane)
                    .thenComparingLong(FinalCarrierTickScheduler.ScheduledTick::durableOrder)
                    .thenComparingInt(FinalCarrierTickScheduler.ScheduledTick::x)
                    .thenComparingInt(FinalCarrierTickScheduler.ScheduledTick::y)
                    .thenComparingInt(FinalCarrierTickScheduler.ScheduledTick::z)
                    .thenComparing(FinalCarrierTickScheduler.ScheduledTick::typeKey);

    private final CanonicalWorldgenStore canonical;
    private final FinalCarrierLaneMutationRepository lanes;
    private final FinalCarrierScheduledTickRepository scheduled;
    private final FinalCarrierConsumedTickRepository consumed;
    private final WorldArchaeologyBrushableRepository archaeology;
    private final WorldStructureEntityRepository structureEntities;
    private final WorldGeneratedStructureEntityStateRepository generatedEntityStates;
    private final WorldStructureEntityIdSequenceRepository entityIdSequences;
    private final WorldMobRepository mobs;
    private final WorldBlockDiffRepository diffs;
    private final WorldStore worlds;
    private final GroundEntityPersistenceService groundEntities;
    private final ChestPersistenceService chests;
    private final FurnacePersistenceService furnaces;
    private final BrewingPersistenceService brewing;
    private final SignBlockPersistenceService signs;
    private final DispenserPersistenceService dispensers;
    private final WorldCanonicalLootAssignmentRepository lootAssignments;
    private final WorldCanonicalBeeInstallationRepository beeInstallations;
    private CanonicalLootMapMaterializationService lootMapMaterialization;
    private LateCanonicalLootPreparationService lateLootPreparation;
    private EntityManager tickRecoveryEntityManager;
    private final Map<Long, CommittedEntityActivationPublisher> entityPublishers =
            new ConcurrentHashMap<>();

    @Autowired
    public FinalCarrierPersistenceService(CanonicalWorldgenStore canonical,
            FinalCarrierLaneMutationRepository lanes,
            FinalCarrierScheduledTickRepository scheduled,
            FinalCarrierConsumedTickRepository consumed,
            WorldArchaeologyBrushableRepository archaeology,
            WorldStructureEntityRepository structureEntities,
            WorldGeneratedStructureEntityStateRepository generatedEntityStates,
            WorldStructureEntityIdSequenceRepository entityIdSequences,
            WorldMobRepository mobs, WorldBlockDiffRepository diffs, WorldStore worlds,
            GroundEntityPersistenceService groundEntities, ChestPersistenceService chests,
            FurnacePersistenceService furnaces, BrewingPersistenceService brewing,
            SignBlockPersistenceService signs, DispenserPersistenceService dispensers,
            WorldCanonicalLootAssignmentRepository lootAssignments,
            WorldCanonicalBeeInstallationRepository beeInstallations,
            EntityManager entityManager) {
        this(canonical, lanes, scheduled, consumed, archaeology, structureEntities,
                generatedEntityStates, entityIdSequences, mobs, diffs, worlds, groundEntities,
                chests, furnaces, brewing, signs, dispensers, lootAssignments, beeInstallations);
        this.tickRecoveryEntityManager = Objects.requireNonNull(entityManager, "entity manager");
    }

    public FinalCarrierPersistenceService(CanonicalWorldgenStore canonical,
            FinalCarrierLaneMutationRepository lanes,
            FinalCarrierScheduledTickRepository scheduled,
            FinalCarrierConsumedTickRepository consumed,
            WorldArchaeologyBrushableRepository archaeology,
            WorldStructureEntityRepository structureEntities,
            WorldGeneratedStructureEntityStateRepository generatedEntityStates,
            WorldStructureEntityIdSequenceRepository entityIdSequences,
            WorldMobRepository mobs, WorldBlockDiffRepository diffs, WorldStore worlds,
            GroundEntityPersistenceService groundEntities, ChestPersistenceService chests,
            FurnacePersistenceService furnaces, BrewingPersistenceService brewing,
            SignBlockPersistenceService signs, DispenserPersistenceService dispensers,
            WorldCanonicalLootAssignmentRepository lootAssignments,
            WorldCanonicalBeeInstallationRepository beeInstallations) {
        this(canonical, lanes, scheduled, consumed, archaeology, structureEntities,
                generatedEntityStates,
                entityIdSequences, mobs, diffs, worlds, groundEntities, chests, furnaces, brewing,
                signs, dispensers, lootAssignments, beeInstallations, true);
    }

    public FinalCarrierPersistenceService(CanonicalWorldgenStore canonical,
            FinalCarrierLaneMutationRepository lanes,
            FinalCarrierScheduledTickRepository scheduled,
            FinalCarrierConsumedTickRepository consumed,
            WorldArchaeologyBrushableRepository archaeology,
            WorldStructureEntityRepository structureEntities,
            WorldGeneratedStructureEntityStateRepository generatedEntityStates,
            WorldStructureEntityIdSequenceRepository entityIdSequences,
            WorldMobRepository mobs, WorldBlockDiffRepository diffs, WorldStore worlds,
            GroundEntityPersistenceService groundEntities, ChestPersistenceService chests,
            FurnacePersistenceService furnaces, BrewingPersistenceService brewing,
            SignBlockPersistenceService signs, DispenserPersistenceService dispensers,
            WorldCanonicalLootAssignmentRepository... lootAssignments) {
        this(canonical, lanes, scheduled, consumed, archaeology, structureEntities,
                generatedEntityStates,
                entityIdSequences, mobs, diffs, worlds, groundEntities, chests, furnaces, brewing,
                signs, dispensers,
                lootAssignments.length == 0 ? null : lootAssignments[0], null, false);
        if (lootAssignments.length > 1) {
            throw new IllegalArgumentException("exactly one canonical LOOT repository is supported");
        }
    }

    private FinalCarrierPersistenceService(CanonicalWorldgenStore canonical,
            FinalCarrierLaneMutationRepository lanes,
            FinalCarrierScheduledTickRepository scheduled,
            FinalCarrierConsumedTickRepository consumed,
            WorldArchaeologyBrushableRepository archaeology,
            WorldStructureEntityRepository structureEntities,
            WorldGeneratedStructureEntityStateRepository generatedEntityStates,
            WorldStructureEntityIdSequenceRepository entityIdSequences,
            WorldMobRepository mobs, WorldBlockDiffRepository diffs, WorldStore worlds,
            GroundEntityPersistenceService groundEntities, ChestPersistenceService chests,
            FurnacePersistenceService furnaces, BrewingPersistenceService brewing,
            SignBlockPersistenceService signs, DispenserPersistenceService dispensers,
            WorldCanonicalLootAssignmentRepository lootAssignments,
            WorldCanonicalBeeInstallationRepository beeInstallations,
            boolean productionRepositoriesRequired) {
        this.canonical = Objects.requireNonNull(canonical, "canonical worldgen store");
        this.lanes = Objects.requireNonNull(lanes, "final-carrier lane repository");
        this.scheduled = Objects.requireNonNull(scheduled, "scheduled tick repository");
        this.consumed = Objects.requireNonNull(consumed, "consumed tick repository");
        this.archaeology = Objects.requireNonNull(archaeology, "archaeology repository");
        this.structureEntities = Objects.requireNonNull(
                structureEntities, "structure entity repository");
        this.generatedEntityStates = Objects.requireNonNull(
                generatedEntityStates, "generated structure entity state repository");
        this.entityIdSequences = Objects.requireNonNull(entityIdSequences, "ENTS ID repository");
        this.mobs = Objects.requireNonNull(mobs, "mob repository");
        this.diffs = Objects.requireNonNull(diffs, "world block diff repository");
        this.worlds = Objects.requireNonNull(worlds, "world repository");
        this.groundEntities = Objects.requireNonNull(
                groundEntities, "ground entity persistence");
        this.chests = Objects.requireNonNull(chests, "generated chest persistence");
        this.furnaces = Objects.requireNonNull(furnaces, "generated furnace persistence");
        this.brewing = Objects.requireNonNull(brewing, "generated brewing persistence");
        this.signs = Objects.requireNonNull(signs, "generated sign persistence");
        this.dispensers = Objects.requireNonNull(dispensers, "generated dispenser persistence");
        this.lootAssignments = productionRepositoriesRequired
                ? Objects.requireNonNull(lootAssignments, "canonical LOOT repository")
                : lootAssignments;
        this.beeInstallations = productionRepositoriesRequired
                ? Objects.requireNonNull(beeInstallations, "canonical BEES repository")
                : beeInstallations;
    }

    public enum CanonicalBeeInstallResult {
        ACKNOWLEDGED, ALREADY_ACKNOWLEDGED, REJECTED, RETRY
    }

    @Autowired(required = false)
    void bindCanonicalLootMapMaterialization(
            CanonicalLootMapMaterializationService materialization) {
        this.lootMapMaterialization = Objects.requireNonNull(
                materialization, "canonical LOOT map materialization");
    }

    @Autowired
    public void configureLateLootPreparation(LateCanonicalLootPreparationService preparation) {
        lateLootPreparation=Objects.requireNonNull(preparation,"late loot preparation");
    }
    public LateCanonicalLootPreparationService.Prepared prepareLateCanonicalLoot(long worldId,
            int x,int y,int z,CanonicalLootContainerKind kind,boolean playerOverride,
            com.gameexpert.terrain.ChunkProductSource generation) {
        // Legacy focused constructors omit optional runtime wiring; the new-profile guard below is strict.
        if(lateLootPreparation==null)return null;
        return lateLootPreparation.prepare(worldId,x,y,z,kind,playerOverride,generation);
    }
    /** Loads and validates the complete persisted generation identity for runtime production. */
    public com.gameexpert.world.WorldGenerationProfile generationProfileFor(long worldId) {
        return worlds.findById(worldId).orElseThrow(() ->
                new IllegalStateException("canonical world profile is missing")).generationProfile();
    }

    /** Exact immutable data needed to restore one acknowledged nest population once. */
    public record CanonicalBeeRecovery(long worldId, int chunkX, int chunkZ,
            String installationIdentity, List<NeutralFinalChunk.BeeNest> nests) {
        public CanonicalBeeRecovery {
            if (worldId <= 0L) throw new IllegalArgumentException("positive world ID required");
            Objects.requireNonNull(installationIdentity, "BEES installation identity");
            nests = List.copyOf(nests);
        }
    }

    /** Atomically stores exact occupants and their canonical claim outcome. */
    @Transactional
    public CanonicalBeeInstallResult installCanonicalBees(long worldId, int chunkX, int chunkZ,
            String sourceFingerprint, NeutralFinalChunk.Sidecars exactPayload) {
        WorldCanonicalBeeInstallationRepository repository = requireBeeRepository();
        Objects.requireNonNull(exactPayload, "exact BEES payload");
        byte[] occupantPayload = CanonicalBeePayload.encode(exactPayload.bees());
        String payloadFingerprint = sha256(occupantPayload);
        String identity = beeInstallationIdentity(worldId, chunkX, chunkZ,
                sourceFingerprint, payloadFingerprint);
        WorldCanonicalBeeInstallation existing = repository
                .findByWorldIdAndChunkXAndChunkZAndInstallationIdentity(
                        worldId, chunkX, chunkZ, identity).orElse(null);
        if (existing != null && !existing.samePayload(
                sourceFingerprint, payloadFingerprint, occupantPayload)) {
            throw new IllegalStateException("canonical BEES installation identity collision");
        }

        CanonicalWorldgenStore.Lane lane = CanonicalWorldgenStore.Lane.BEES;
        CanonicalWorldgenStore.LaneReceipt claim = canonical.claim(worldId, chunkX, chunkZ, lane);
        if (claim == null) {
            // The unclaimed turn answers from lane bookkeeping alone; only the acknowledged row
            // check needs the carrier, so the retry and rejection turns skip the blob read.
            CanonicalWorldgenStore.LaneMasks masks = canonical.laneMasks(worldId, chunkX, chunkZ);
            if (masks == null) return CanonicalBeeInstallResult.RETRY;
            if ((masks.rejected() & lane.mask()) != 0) {
                return existing != null && existing.getStatus()
                        == WorldCanonicalBeeInstallation.Status.REJECTED
                        ? CanonicalBeeInstallResult.REJECTED : CanonicalBeeInstallResult.RETRY;
            }
            if ((masks.ack() & lane.mask()) == 0 || existing == null
                    || existing.getStatus()
                    != WorldCanonicalBeeInstallation.Status.ACKNOWLEDGED) {
                return CanonicalBeeInstallResult.RETRY;
            }
            CanonicalWorldgenStore.CanonicalChunkSnapshot snapshot = canonical.find(
                    worldId, chunkX, chunkZ);
            if (snapshot == null) return CanonicalBeeInstallResult.RETRY;
            validateAcknowledgedBeeRow(existing, snapshot);
            return CanonicalBeeInstallResult.ALREADY_ACKNOWLEDGED;
        }

        NeutralFinalChunk source = validateClaim(
                claim, chunkX, chunkZ, sourceFingerprint);
        boolean exact;
        try {
            requireExactProjection(lane, source.sidecars(), exactPayload);
            exact = true;
        } catch (IllegalArgumentException conflict) {
            exact = false;
        }
        if (existing != null) {
            if (existing.getStatus() == WorldCanonicalBeeInstallation.Status.ACKNOWLEDGED
                    && exact) {
                canonical.acknowledge(worldId, chunkX, chunkZ, lane, claim.receipt());
                return CanonicalBeeInstallResult.ALREADY_ACKNOWLEDGED;
            }
            canonical.reject(worldId, chunkX, chunkZ, lane, claim.receipt());
            return CanonicalBeeInstallResult.REJECTED;
        }
        boolean conflictingPayload = repository
                .findAllByWorldIdAndChunkXAndChunkZOrderById(worldId, chunkX, chunkZ).stream()
                .anyMatch(row -> row.getSourceFingerprint().equals(sourceFingerprint));
        if (conflictingPayload) {
            repository.save(new WorldCanonicalBeeInstallation(worldId, chunkX, chunkZ, identity,
                    sourceFingerprint, payloadFingerprint,
                    WorldCanonicalBeeInstallation.Status.REJECTED,
                    claim.receipt(), occupantPayload));
            canonical.reject(worldId, chunkX, chunkZ, lane, claim.receipt());
            return CanonicalBeeInstallResult.REJECTED;
        }
        WorldCanonicalBeeInstallation.Status status = exact
                ? WorldCanonicalBeeInstallation.Status.ACKNOWLEDGED
                : WorldCanonicalBeeInstallation.Status.REJECTED;
        repository.save(new WorldCanonicalBeeInstallation(worldId, chunkX, chunkZ, identity,
                sourceFingerprint, payloadFingerprint, status, claim.receipt(), occupantPayload));
        if (exact) {
            canonical.acknowledge(worldId, chunkX, chunkZ, lane, claim.receipt());
            return CanonicalBeeInstallResult.ACKNOWLEDGED;
        }
        canonical.reject(worldId, chunkX, chunkZ, lane, claim.receipt());
        return CanonicalBeeInstallResult.REJECTED;
    }

    @Transactional(readOnly = true)
    public List<CanonicalBeeRecovery> recoverCanonicalBees(long worldId) {
        return recoverCanonicalBeeRows(requireBeeRepository().findAllByWorldIdOrderById(worldId));
    }

    @Transactional(readOnly = true)
    public List<CanonicalBeeRecovery> recoverCanonicalBees(long worldId, int chunkX, int chunkZ) {
        return recoverCanonicalBeeRows(requireBeeRepository()
                .findAllByWorldIdAndChunkXAndChunkZOrderById(worldId, chunkX, chunkZ));
    }

    private List<CanonicalBeeRecovery> recoverCanonicalBeeRows(
            List<WorldCanonicalBeeInstallation> rows) {
        ArrayList<CanonicalBeeRecovery> recovered = new ArrayList<>();
        for (WorldCanonicalBeeInstallation row : rows) {
            if (row.getStatus() != WorldCanonicalBeeInstallation.Status.ACKNOWLEDGED) continue;
            CanonicalWorldgenStore.CanonicalChunkSnapshot snapshot = canonical.find(
                    row.getWorldId(), row.getChunkX(), row.getChunkZ());
            validateAcknowledgedBeeRow(row, snapshot);
            recovered.add(new CanonicalBeeRecovery(row.getWorldId(), row.getChunkX(),
                    row.getChunkZ(), row.getInstallationIdentity(), row.exactNests()));
        }
        return List.copyOf(recovered);
    }

    private void validateAcknowledgedBeeRow(WorldCanonicalBeeInstallation row,
            CanonicalWorldgenStore.CanonicalChunkSnapshot snapshot) {
        CanonicalWorldgenStore.Lane lane = CanonicalWorldgenStore.Lane.BEES;
        if (snapshot == null || (snapshot.laneAckMask() & lane.mask()) == 0
                || (snapshot.laneRejectedMask() & lane.mask()) != 0) {
            throw new IllegalStateException(
                    "durable BEES installation has no acknowledged canonical receipt");
        }
        NeutralFinalChunk source = committedSource(snapshot.commit());
        if (source.chunkX() != row.getChunkX() || source.chunkZ() != row.getChunkZ()) {
            throw new IllegalStateException("durable BEES receipt coordinate mismatch");
        }
        requireSource(source, row.getSourceFingerprint());
        List<NeutralFinalChunk.BeeNest> nests = row.exactNests();
        if (!isEncounterOrderedProjection(source.sidecars().bees(), nests)
                || !row.samePayload(row.getSourceFingerprint(), row.getPayloadFingerprint(),
                        CanonicalBeePayload.encode(nests))) {
            throw new IllegalStateException("durable BEES receipt payload mismatch");
        }
    }

    private WorldCanonicalBeeInstallationRepository requireBeeRepository() {
        if (beeInstallations == null) {
            throw new IllegalStateException("canonical BEES repository is unavailable");
        }
        return beeInstallations;
    }

    private static String beeInstallationIdentity(long worldId, int chunkX, int chunkZ,
            String sourceFingerprint, String payloadFingerprint) {
        return worldId + ":" + chunkX + ":" + chunkZ + ":BEES:"
                + sourceFingerprint + ":" + payloadFingerprint;
    }

    /** Locks and materializes one assignment once; later opens replay the stored fixed slots. */
    @Transactional
    public CanonicalLootFirstOpenResult resolveCanonicalLootFirstOpen(long worldId,
            int x, int y, int z, CanonicalLootContainerKind containerKind,
            boolean playerOverride, CanonicalLootAggregatePersistence aggregatePersistence) {
        return resolveCanonicalLootFirstOpen(worldId,x,y,z,containerKind,playerOverride,aggregatePersistence,null);
    }
    @Transactional
    public CanonicalLootFirstOpenResult resolveCanonicalLootFirstOpen(long worldId,
            int x,int y,int z,CanonicalLootContainerKind containerKind,boolean playerOverride,
            CanonicalLootAggregatePersistence aggregatePersistence,
            LateCanonicalLootPreparationService.Prepared prepared) {
        Objects.requireNonNull(aggregatePersistence, "canonical LOOT aggregate persistence");
        if(prepared!=null)Objects.requireNonNull(lateLootPreparation,"late loot preparation")
                .lockWorldJoiningTransaction(worldId,prepared);
        WorldCanonicalLootAssignmentRepository repository = requireLootRepository();
        WorldCanonicalLootAssignment assignment = repository
                .findLockedByWorldIdAndPosXAndPosYAndPosZ(worldId, x, y, z)
                .orElse(null);
        if (assignment == null) return null;
        if (assignment.getStatus() == WorldCanonicalLootAssignment.Status.REJECTED) {
            return CanonicalLootFirstOpenResult.rejected(assignment);
        }
        if (playerOverride || assignment.getContainerKind()
                != Objects.requireNonNull(containerKind, "container kind")) {
            assignment.reject();
            repository.save(assignment);
            return CanonicalLootFirstOpenResult.rejected(assignment);
        }
        var producerSnapshot = canonical.find(worldId, assignment.getChunkX(), assignment.getChunkZ());
        if (producerSnapshot == null) {
            throw new IllegalStateException("canonical LOOT producer source is missing");
        }
        NeutralFinalChunk producerSource = committedSource(producerSnapshot.commit());
        assignment.verifyProducer(producerSource);
        if(prepared!=null)lateLootPreparation.validateJoiningTransaction(assignment,prepared,producerSource);
        else if(!assignment.getLocatedProductionContext().targets().isEmpty())
            throw new IllegalStateException("new-profile map opening requires prepared producer outcome");
        if (assignment.getStatus() == WorldCanonicalLootAssignment.Status.RESOLVED) {
            CanonicalLootStoredResolution stored = assignment.requireStoredResolution();
            CanonicalLootMapMaterializationService.Materialization maps =
                    replayMaterializedMaps(assignment, stored);
            CanonicalLootAggregatePersistence.Outcome replay = Objects.requireNonNull(
                    aggregatePersistence.installResolvedJoiningTransaction(
                            CanonicalLootAggregateMutation.from(assignment, stored)),
                    "canonical LOOT aggregate outcome");
            if (replay != CanonicalLootAggregatePersistence.Outcome.ALREADY_COMMITTED) {
                throw new IllegalStateException(
                        "resolved canonical LOOT does not exactly replay its container aggregate");
            }
            return CanonicalLootFirstOpenResult.resolved(
                    assignment, stored, maps.maps());
        }
        CanonicalLootStoredResolution resolution;
        try {
            resolution = prepared==null ? assignment.resolveCandidate(producerSource)
                    : CanonicalLootStoredResolution.decode(prepared.outcome().resolution());
        } catch (IllegalArgumentException | IllegalStateException conflict) {
            assignment.reject();
            repository.save(assignment);
            return CanonicalLootFirstOpenResult.rejected(assignment);
        }
        CanonicalLootMapMaterializationService.Materialization maps =
                materializeMaps(assignment, resolution);
        resolution = maps.resolution();
        CanonicalLootAggregatePersistence.Outcome aggregate = Objects.requireNonNull(
                aggregatePersistence.installResolvedJoiningTransaction(
                        CanonicalLootAggregateMutation.forFirstInstallation(assignment, resolution)),
                "canonical LOOT aggregate outcome");
        if (aggregate == CanonicalLootAggregatePersistence.Outcome.REJECTED) {
            if (!maps.maps().isEmpty()) {
                throw new IllegalStateException(
                        "map-bearing canonical LOOT aggregate rejected after allocation");
            }
            assignment.reject();
            repository.save(assignment);
            return CanonicalLootFirstOpenResult.rejected(assignment);
        }
        if(prepared!=null)lateLootPreparation.stageJoiningTransaction(assignment,prepared);
        assignment.commitResolution(resolution, maps.payload(), maps.receipt());
        repository.save(assignment);
        return CanonicalLootFirstOpenResult.resolved(assignment, resolution, maps.maps());
    }

    private CanonicalLootMapMaterializationService.Materialization materializeMaps(
            WorldCanonicalLootAssignment assignment, CanonicalLootStoredResolution candidate) {
        if (candidate.pendingMapReferences().isEmpty()) {
            return new CanonicalLootMapMaterializationService.Materialization(
                    candidate, List.of(), null, null);
        }
        if (lootMapMaterialization == null) {
            throw new IllegalStateException("canonical LOOT map materialization is unavailable");
        }
        return lootMapMaterialization.materializeJoiningTransaction(assignment, candidate);
    }

    private CanonicalLootMapMaterializationService.Materialization replayMaterializedMaps(
            WorldCanonicalLootAssignment assignment, CanonicalLootStoredResolution stored) {
        if (assignment.getMapMaterializationReceipt() == null
                && assignment.getMapMaterializationPayload() == null) {
            return new CanonicalLootMapMaterializationService.Materialization(
                    stored, List.of(), null, null);
        }
        if (lootMapMaterialization == null) {
            throw new IllegalStateException("canonical LOOT map replay authority is unavailable");
        }
        return lootMapMaterialization.replayJoiningTransaction(assignment, stored,
                assignment.getMapMaterializationPayload(),
                assignment.getMapMaterializationReceipt());
    }

    @FunctionalInterface
    public interface CommittedEntityActivationPublisher {
        void publish(CommittedEntityActivation activation);

        /** Non-consuming next identity available to the live runtime; readable on any thread. */
        default long currentLiveEntityIdFloor() { return 1L; }

        /**
         * Atomically moves the live allocator past {@code through}, unless it has already handed out
         * {@code minimum} or later. A reservation made off the owner thread is only safe once this
         * returns true: every earlier live id is then below the range and every later one above it.
         */
        default boolean claimLiveEntityIds(long minimum, long through) { return true; }
    }

    public static final class CommittedEntityActivation {
        private final long worldId;
        private final int chunkX;
        private final int chunkZ;
        private final String installationIdentity;
        private final StructureEntityAggregate aggregate;
        private final List<MobPersistenceSnapshot> snapshots;
        private final List<WorldGeneratedStructureEntityState.RuntimeSnapshot>
                generatedSnapshots;

        private CommittedEntityActivation(FinalCarrierLaneMutation mutation,
                StructureEntityAggregate aggregate, List<MobPersistenceSnapshot> snapshots,
                List<WorldGeneratedStructureEntityState.RuntimeSnapshot> generatedSnapshots) {
            this.worldId = mutation.getWorldId();
            this.chunkX = mutation.getChunkX();
            this.chunkZ = mutation.getChunkZ();
            this.installationIdentity = mutation.getInstallationIdentity();
            this.aggregate = Objects.requireNonNull(aggregate, "committed ENTS aggregate");
            if (!aggregate.installed()
                    || !installationIdentity.equals(aggregate.installationIdentity())) {
                throw new IllegalStateException("published ENTS aggregate is not committed");
            }
            this.snapshots = detachMobSnapshots(worldId, snapshots);
            this.generatedSnapshots = List.copyOf(generatedSnapshots);
        }

        public long worldId() { return worldId; }
        public int chunkX() { return chunkX; }
        public int chunkZ() { return chunkZ; }
        public String installationIdentity() { return installationIdentity; }
        public StructureEntityAggregate aggregate() { return aggregate; }
        public List<StructureEntityAggregate.PlannedEntity> plannedEntities() {
            return aggregate.plannedEntities();
        }
        public List<MobPersistenceSnapshot> snapshots() {
            return detachMobSnapshots(worldId, snapshots);
        }
        public List<WorldGeneratedStructureEntityState.RuntimeSnapshot> generatedSnapshots() {
            return generatedSnapshots;
        }

        private static List<MobPersistenceSnapshot> detachMobSnapshots(long worldId,
                List<MobPersistenceSnapshot> values) {
            return values.stream()
                    .map(value -> new WorldMob(worldId,
                            Objects.requireNonNull(value, "committed ENTS mob snapshot"))
                            .toSnapshot())
                    .toList();
        }
    }

    public void registerWorldEntityPublisher(long worldId,
            CommittedEntityActivationPublisher publisher) {
        if (worldId <= 0L) throw new IllegalArgumentException("positive world ID required");
        entityPublishers.put(worldId, Objects.requireNonNull(publisher, "ENTS publisher"));
    }

    public void unregisterWorldEntityPublisher(long worldId,
            CommittedEntityActivationPublisher publisher) {
        entityPublishers.remove(worldId, publisher);
    }

    public void unregisterWorldEntityPublisher(long worldId) {
        entityPublishers.remove(worldId);
    }

    public enum StructureEntityDeathResult {
        COMMITTED,
        NOT_FOUND_RETRYABLE,
        UNSUPPORTED_TERMINAL
    }

    @Transactional
    public StructureEntityDeathResult markStructureEntityDead(long worldId, long mobId) {
        WorldStructureEntity binding = structureEntities
                .findByWorldIdAndAuthoritativeEntityId(worldId, mobId).orElse(null);
        if (binding == null) return StructureEntityDeathResult.NOT_FOUND_RETRYABLE;
        GeneratedStructureEntityFacts.DecodeResult decoded =
                GeneratedStructureEntityFacts.decode(binding);
        if (decoded instanceof GeneratedStructureEntityFacts.Decoded generatedFacts
                && (generatedFacts.facts().kind()
                        == GeneratedStructureEntityFacts.Kind.ARMOR_STAND
                    || generatedFacts.facts().kind()
                        == GeneratedStructureEntityFacts.Kind.CHEST_MINECART)) {
            // Their destruction owns inventory/equipment drops that must join the tombstone in a
            // dedicated transaction. The generic no-drop tombstone would silently destroy items.
            return StructureEntityDeathResult.UNSUPPORTED_TERMINAL;
        }
        if (decoded instanceof GeneratedStructureEntityFacts.Decoded) {
            List<FinalCarrierLaneMutation> matching = lanes.findInstalledEntityMutation(
                    worldId, binding.getChunkX(), binding.getChunkZ(),
                    binding.getLaneInstallationIdentity(), FinalCarrierLaneMutation.ActivationStatus.INSTALLED, PageRequest.of(0, 2));
            if (matching.size() != 1) throw new IllegalStateException(
                    "generated entity must have exactly one installed ENTS aggregate");
            FinalCarrierLaneMutation mutation = matching.getFirst();
            NeutralFinalChunk.Sidecars exact = decodeStoredProjection(
                    mutation, CanonicalWorldgenStore.Lane.ENTITIES);
            StructureEntityActivation activation = prepareStructureEntityActivation(
                    mutation, CanonicalWorldgenStore.Lane.ENTITIES, exact, true,
                    preflightEntities(mutation, CanonicalWorldgenStore.Lane.ENTITIES,
                            exact.entities()));
            WorldStructureEntity exactSource = activation.allRows.stream()
                    .filter(row -> row.getAuthoritativeEntityId() == mobId)
                    .findFirst().orElseThrow(StructureEntityConflict::new);
            WorldGeneratedStructureEntityState generated = activation.allGeneratedRows.stream()
                    .filter(row -> row.getAuthoritativeEntityId() == mobId)
                    .findFirst().orElseThrow(StructureEntityConflict::new);
            WorldGeneratedStructureEntityState.RuntimeSnapshot snapshot =
                    generated.runtimeSnapshot();
            if (snapshot.lifecycle()
                    == WorldGeneratedStructureEntityState.Lifecycle.DEAD) {
                return StructureEntityDeathResult.COMMITTED;
            }
            generated.markDead(snapshot.revision(), Math.addExact(snapshot.revision(), 1L),
                    worldId, binding.getChunkX(), binding.getChunkZ(),
                    mutation.getInstallationIdentity(), exactSource, activation.aggregate);
            structureEntities.save(exactSource);
            generatedEntityStates.save(generated);
            return StructureEntityDeathResult.COMMITTED;
        }
        if (binding.markDead()) structureEntities.save(binding);
        mobs.findLockedByWorldIdAndMobId(worldId, mobId).ifPresent(mobs::delete);
        return StructureEntityDeathResult.COMMITTED;
    }

    public record ArchaeologyResultSettlement(
            ArchaeologyBrushableAggregate.ConsumeOutcome outcome,
            GroundItemSnapshot groundItem,
            long committedGroundRevision) {
        public ArchaeologyResultSettlement {
            Objects.requireNonNull(outcome, "ARCH consume outcome");
            Objects.requireNonNull(groundItem, "ARCH ground item");
            if (committedGroundRevision < 0L || committedGroundRevision == Long.MAX_VALUE) {
                throw new IllegalArgumentException("ARCH committed ground revision is invalid");
            }
        }

    }

    /** Atomically records the exact deterministic ARCH result and ground row before delivery. */
    @Transactional
    public ArchaeologyResultSettlement consumeArchaeologyResult(
            long worldId, int x, int y, int z, String installationIdentity,
            String candidateExactBlockState, long candidateTargetRevision,
            String candidateResultIdentity, long proposedEntityId) {
        WorldArchaeologyBrushable row = archaeology
                .findLockedByWorldIdAndTargetXAndTargetYAndTargetZ(worldId, x, y, z)
                .orElse(null);
        if (row == null || !row.getInstallationIdentity().equals(installationIdentity)) {
            return null;
        }
        ArchaeologyLootResolver.ResolvedLoot expected;
        try {
            expected = ArchaeologyLootResolver.pinned().resolve(
                    row.getExactTableKey(), row.getRawSeed());
        } catch (IllegalArgumentException unsupported) {
            return null;
        }
        if (!expected.resultIdentity().equals(candidateResultIdentity)) return null;
        // Validate all candidate provenance before mutating the JPA row. If the joining ground
        // transaction rejects, the in-memory entity remains an unconsumed replay candidate.
        ArchaeologyBrushableAggregate candidate = row.toAggregate();
        ArchaeologyBrushableAggregate.ConsumeOutcome outcome = candidate.consume(
                candidateResultIdentity, candidateExactBlockState, candidateTargetRevision);
        if (proposedEntityId <= 0L
                || proposedEntityId > GroundMutationCommand.MAX_GROUND_ENTITY_ID) {
            throw new IllegalArgumentException("stable archaeology ground entity ID is required");
        }
        long entityId = row.getResultEntityId() == null
                ? proposedEntityId : row.getResultEntityId();
        if (entityId <= 0L || entityId > GroundMutationCommand.MAX_GROUND_ENTITY_ID) {
            throw new IllegalStateException("stored archaeology ground entity ID is invalid");
        }
        var stack = expected.stack();
        GroundItemSnapshot groundItem = new GroundItemSnapshot(entityId, stack.itemType(),
                stack.count(), stack.durability(), stack.enchantments(), stack.mapId(),
                stack.shulkerId(), stack.bucketMobData(), stack.itemComponentData(),
                x + 0.5, y + 0.5, z + 0.5, 0, 0.2, 0, false, 0, 5, 0);
        long committedGroundRevision = groundEntities.insertStableItemJoiningTransaction(
                worldId, groundItem);
        if (committedGroundRevision < 0L || committedGroundRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("ARCH ground transaction returned invalid revision");
        }
        ArchaeologyBrushableAggregate.ConsumeOutcome persistedOutcome = row.consume(
                candidateResultIdentity, candidateExactBlockState,
                candidateTargetRevision, proposedEntityId);
        if (persistedOutcome != outcome) {
            throw new IllegalStateException("ARCH result outcome changed while locked");
        }
        archaeology.save(row);
        return new ArchaeologyResultSettlement(outcome, groundItem, committedGroundRevision);
    }

    @Transactional
    public boolean advanceArchaeologyTarget(long worldId, int x, int y, int z,
            String installationIdentity, String expectedExactBlockState,
            long expectedRevision, String nextExactBlockState) {
        WorldArchaeologyBrushable row = archaeology
                .findLockedByWorldIdAndTargetXAndTargetYAndTargetZ(worldId, x, y, z)
                .orElse(null);
        if (row == null || !row.getInstallationIdentity().equals(installationIdentity)) {
            return false;
        }
        row.advanceTarget(expectedExactBlockState, expectedRevision, nextExactBlockState);
        archaeology.save(row);
        return true;
    }

    @Transactional
    public boolean revokeArchaeologyTarget(long worldId, int x, int y, int z,
            String installationIdentity, String expectedExactBlockState,
            long expectedRevision) {
        WorldArchaeologyBrushable row = archaeology
                .findLockedByWorldIdAndTargetXAndTargetYAndTargetZ(worldId, x, y, z)
                .orElse(null);
        if (row == null || !row.getInstallationIdentity().equals(installationIdentity)) {
            return false;
        }
        row.revoke(expectedExactBlockState, expectedRevision);
        archaeology.save(row);
        return true;
    }

    @Override
    public boolean supportsDurablePayload(TerrainAccessor.FinalLiveCarrierLane lane) {
        return lane == TerrainAccessor.FinalLiveCarrierLane.OWNERS
                || lane == TerrainAccessor.FinalLiveCarrierLane.LOOT && lootAssignments != null;
    }

    /**
     * 비어 있는 SPAWNERS·ENTITIES·ARCHAEOLOGY 레인도 durable-only다. 활성화할 스포너·개체·솔질
     * 블록이 없으므로 런타임 설치는 결과가 정해진 무연산이고, 남는 일은 영수증 기록뿐이다.
     */
    @Override
    public boolean supportsDurablePayload(TerrainAccessor.FinalLiveCarrierLane lane,
            NeutralFinalChunk.Sidecars exactPayload) {
        // ENTS는 비어 있지 않아도 이 트랜잭션 안에서 런타임을 부르지 않는다. 몹은 커밋 뒤 게시자가
        // owner 완료 큐로 넘기고, id 예약은 claimLiveEntityIds로 살아 있는 발급기와 원자적으로 겹침을 막는다.
        return supportsDurablePayload(lane) || emptyGameplayPayload(lane, exactPayload)
                || lane == TerrainAccessor.FinalLiveCarrierLane.ENTITIES;
    }

    /**
     * 스포너가 있는 SPAWNERS, 솔질 블록이 있는 ARCHAEOLOGY, 그리고 BLOCK_ENTITIES 레인. 영속
     * 부분(BENT의 상자·화로·양조대·표지판·발사기 행 포함)은 워커가, 상주 설치(스포너 칸·솔질
     * 집계·장식 항아리 투영)는 owner가 커밋 뒤에 맡는다. BENT 집계 서비스들은 요청마다 자기
     * 트랜잭션 안의 잠금 행만 다루고, 공유 상태는 커밋 뒤에 갱신되는 ConcurrentHashMap 행 결속뿐이라
     * 영속 실행기와 이미 동시에 불린다.
     */
    @Override
    public boolean supportsDetachedGameplayPayload(TerrainAccessor.FinalLiveCarrierLane lane,
            NeutralFinalChunk.Sidecars exactPayload) {
        return switch (lane) {
            case SPAWNERS -> !exactPayload.spawners().isEmpty();
            case ARCHAEOLOGY -> !exactPayload.archaeology().isEmpty();
            case BLOCK_ENTITIES -> true;
            default -> false;
        };
    }

    /**
     * 워커 트랜잭션. {@code verdict}는 owner가 만든 스냅샷으로 owner 설치와 같은 판정을 내린다.
     * 성공 판정만 영속 설치로 커밋하고, 그 밖의 판정은 {@link OwnerActivationRequired}로 전부
     * 되돌린다. 거절 여부를 워커가 확정하는 일은 없으므로 런타임 충돌이 영속 상태에 묻히지 않는다.
     */
    @Override
    @Transactional
    public WorldRuntime.FinalCarrierInstallResult installDetachedGameplayAndCommit(long worldId,
            int chunkX, int chunkZ, TerrainAccessor.FinalLiveCarrierLane lane,
            String sourceFingerprint, NeutralFinalChunk.Sidecars exactPayload,
            WorldRuntime.FinalCarrierGameplayInstaller verdict) {
        Objects.requireNonNull(verdict, "detached gameplay verdict");
        if (TickSafetyTelemetry.isTickThread()) {
            throw new IllegalStateException("detached gameplay transaction cannot run on the owner tick");
        }
        if (!supportsDetachedGameplayPayload(lane, exactPayload)) {
            throw new IllegalArgumentException("not a detached gameplay payload lane: " + lane);
        }
        requireWorldLock(worldId, "detached gameplay payload world is missing");
        return installAndCommit(worldId, chunkX, chunkZ, lane, sourceFingerprint, exactPayload,
                provenVerdictOnly(verdict, activatedLane -> activatedLane == lane, false));
    }

    /**
     * Gate between a worker transaction and an owner-snapshot verdict. Only COMMITTED and
     * ALREADY_COMMITTED pass; every other verdict, and any activation outside {@code lanes}, throws
     * {@link OwnerActivationRequired} so the whole transaction rolls back and the owner decides.
     * A worker therefore never settles a runtime rejection into durable state. With
     * {@code emptyIsSettled}, an activation with nothing to install answers ALREADY_COMMITTED
     * without consulting the verdict, which is what the owner's installer answers for it.
     */
    private static WorldRuntime.FinalCarrierGameplayInstaller provenVerdictOnly(
            WorldRuntime.FinalCarrierGameplayInstaller verdict,
            java.util.function.Predicate<TerrainAccessor.FinalLiveCarrierLane> lanes,
            boolean emptyIsSettled) {
        return new WorldRuntime.FinalCarrierGameplayInstaller() {
            @Override
            public WorldRuntime.FinalCarrierInstallResult install(String identity,
                    TerrainAccessor.FinalLiveCarrierLane lane, NeutralFinalChunk.Sidecars payload,
                    List<ArchaeologyBrushableAggregate> archaeology) {
                return installPrepared(identity, lane, payload, archaeology, List.of());
            }

            @Override
            public WorldRuntime.FinalCarrierInstallResult installPrepared(String identity,
                    TerrainAccessor.FinalLiveCarrierLane lane, NeutralFinalChunk.Sidecars payload,
                    List<ArchaeologyBrushableAggregate> archaeology,
                    List<SpawnerAggregate> spawners) {
                if (emptyIsSettled && archaeology.isEmpty() && spawners.isEmpty()
                        && emptyGameplayPayload(lane, payload)) {
                    return WorldRuntime.FinalCarrierInstallResult.ALREADY_COMMITTED;
                }
                if (!lanes.test(lane)) throw new OwnerActivationRequired();
                return proven(verdict.installPrepared(identity, lane, payload, archaeology,
                        spawners));
            }

            @Override
            public WorldRuntime.FinalCarrierInstallResult installBlockEntityPlan(
                    FinalCarrierBlockEntityPlan plan) {
                if (!lanes.test(TerrainAccessor.FinalLiveCarrierLane.BLOCK_ENTITIES)) {
                    throw new OwnerActivationRequired();
                }
                return proven(verdict.installBlockEntityPlan(plan));
            }
        };
    }

    private static WorldRuntime.FinalCarrierInstallResult proven(
            WorldRuntime.FinalCarrierInstallResult result) {
        if (result == WorldRuntime.FinalCarrierInstallResult.COMMITTED
                || result == WorldRuntime.FinalCarrierInstallResult.ALREADY_COMMITTED) {
            return result;
        }
        throw new OwnerActivationRequired();
    }

    private static boolean emptyGameplayPayload(TerrainAccessor.FinalLiveCarrierLane lane,
            NeutralFinalChunk.Sidecars exactPayload) {
        return switch (lane) {
            case SPAWNERS -> exactPayload.spawners().isEmpty();
            case ENTITIES -> exactPayload.entities().isEmpty();
            case ARCHAEOLOGY -> exactPayload.archaeology().isEmpty();
            default -> false;
        };
    }

    /** Atomic durable-only transaction. A worker can never invoke a resident gameplay callback. */
    @Override
    @Transactional
    public WorldRuntime.FinalCarrierInstallResult installDurablePayloadAndCommit(long worldId,
            int chunkX, int chunkZ, TerrainAccessor.FinalLiveCarrierLane lane,
            String sourceFingerprint, NeutralFinalChunk.Sidecars exactPayload) {
        if (TickSafetyTelemetry.isTickThread()) {
            throw new IllegalStateException("durable payload transaction cannot run on the owner tick");
        }
        if (!supportsDurablePayload(lane, exactPayload)) {
            throw new IllegalArgumentException("not a durable-only payload lane: " + lane);
        }
        // Protect against world deletion, without taking an exclusive world-row lock.
        requireWorldLock(worldId, "durable payload world is missing");
        return installAndCommit(worldId, chunkX, chunkZ, lane, sourceFingerprint, exactPayload,
                (identity, activatedLane, payload, archaeology) -> {
                    // 빈 레인의 런타임 설치와 같은 결과다. 그 밖의 활성화는 워커에서 금지한다.
                    if (activatedLane == lane && archaeology.isEmpty()
                            && emptyGameplayPayload(activatedLane, payload)) {
                        return WorldRuntime.FinalCarrierInstallResult.ALREADY_COMMITTED;
                    }
                    throw new IllegalStateException("durable-only lane attempted runtime activation");
                });
    }

    @Override
    @Transactional
    public WorldRuntime.FinalCarrierInstallResult installAndCommit(long worldId, int chunkX,
            int chunkZ, TerrainAccessor.FinalLiveCarrierLane lane, String sourceFingerprint,
            NeutralFinalChunk.Sidecars exactPayload,
            WorldRuntime.FinalCarrierGameplayInstaller gameplayInstaller) {
        Objects.requireNonNull(gameplayInstaller, "gameplay installer");
        CanonicalWorldgenStore.Lane canonicalLane = canonicalLane(lane);
        if (canonicalLane == CanonicalWorldgenStore.Lane.BLOCK_TICKS
                || canonicalLane == CanonicalWorldgenStore.Lane.FLUID_TICKS
                || canonicalLane == CanonicalWorldgenStore.Lane.BEES) {
            throw new IllegalArgumentException("not a durable payload lane: " + lane);
        }
        if (canonicalLane == CanonicalWorldgenStore.Lane.ENTITIES
                && !canPublishEntities(worldId)) {
            return WorldRuntime.FinalCarrierInstallResult.RETRY;
        }
        CanonicalWorldgenStore.LaneReceipt claim = canonical.claim(
                worldId, chunkX, chunkZ, canonicalLane);
        if (claim == null) {
            return terminalPayloadResult(worldId, chunkX, chunkZ, canonicalLane,
                    sourceFingerprint, exactPayload, gameplayInstaller);
        }
        NeutralFinalChunk source = validateClaim(
                claim, chunkX, chunkZ, sourceFingerprint);
        requireExactProjection(canonicalLane, source.sidecars(), exactPayload);
        byte[] typedPayload = encodeProjection(source, exactPayload);
        String payloadFingerprint = sha256(typedPayload);
        FinalCarrierLaneMutation existing = lanes
                .findByWorldIdAndChunkXAndChunkZAndLaneAndSourceFingerprint(
                        worldId, chunkX, chunkZ, canonicalLane.name(), sourceFingerprint)
                .orElse(null);
        if (existing != null && !existing.matches(payloadFingerprint, typedPayload)) {
            canonical.reject(worldId, chunkX, chunkZ, canonicalLane, claim.receipt());
            return WorldRuntime.FinalCarrierInstallResult.REJECTED;
        }
        boolean newMutation = existing == null;
        if (newMutation) {
            existing = new FinalCarrierLaneMutation(worldId, chunkX, chunkZ,
                    canonicalLane.name(), sourceFingerprint, payloadFingerprint, typedPayload);
        }
        EntityPreflight entityPreflight;
        try {
            entityPreflight = preflightEntities(existing, canonicalLane,
                    exactPayload.entities());
        } catch (StructureEntityConflict malformedAuthority) {
            canonical.reject(worldId, chunkX, chunkZ, canonicalLane, claim.receipt());
            return WorldRuntime.FinalCarrierInstallResult.REJECTED;
        }
        if (newMutation) {
            lanes.save(existing);
        }
        return activateAndSettle(existing, lane, exactPayload, source, claim, gameplayInstaller,
                entityPreflight);
    }

    private WorldRuntime.FinalCarrierInstallResult terminalPayloadResult(long worldId, int chunkX,
            int chunkZ, CanonicalWorldgenStore.Lane lane, String sourceFingerprint,
            NeutralFinalChunk.Sidecars exactPayload,
            WorldRuntime.FinalCarrierGameplayInstaller gameplayInstaller) {
        // Lane bookkeeping decides RETRY/REJECTED on its own, and both answers are common while a
        // chunk is still settling. Reading the masks first keeps those turns off the carrier blobs.
        CanonicalWorldgenStore.LaneMasks masks = canonical.laneMasks(worldId, chunkX, chunkZ);
        if (masks == null || (masks.rejected() & lane.mask()) != 0) {
            return masks == null ? WorldRuntime.FinalCarrierInstallResult.RETRY
                    : WorldRuntime.FinalCarrierInstallResult.REJECTED;
        }
        if ((masks.ack() & lane.mask()) == 0) {
            return WorldRuntime.FinalCarrierInstallResult.RETRY;
        }
        CanonicalWorldgenStore.CanonicalChunkSnapshot snapshot =
                canonical.find(worldId, chunkX, chunkZ);
        if (snapshot == null || (snapshot.laneRejectedMask() & lane.mask()) != 0) {
            return snapshot == null ? WorldRuntime.FinalCarrierInstallResult.RETRY
                    : WorldRuntime.FinalCarrierInstallResult.REJECTED;
        }
        if ((snapshot.laneAckMask() & lane.mask()) == 0) {
            return WorldRuntime.FinalCarrierInstallResult.RETRY;
        }
        NeutralFinalChunk source = committedSource(snapshot.commit());
        if (source.chunkX() != chunkX || source.chunkZ() != chunkZ) {
            return WorldRuntime.FinalCarrierInstallResult.REJECTED;
        }
        requireSource(source, sourceFingerprint);
        requireExactProjection(lane, source.sidecars(), exactPayload);
        byte[] typedPayload = encodeProjection(source, exactPayload);
        String payloadFingerprint = sha256(typedPayload);
        FinalCarrierLaneMutation mutation = lanes
                .findByWorldIdAndChunkXAndChunkZAndLaneAndSourceFingerprint(
                        worldId, chunkX, chunkZ, lane.name(), sourceFingerprint)
                .orElse(null);
        if (mutation == null) return WorldRuntime.FinalCarrierInstallResult.RETRY;
        if (!mutation.matches(payloadFingerprint, typedPayload)) {
            return WorldRuntime.FinalCarrierInstallResult.REJECTED;
        }
        if (mutation.getActivationStatus()
                == FinalCarrierLaneMutation.ActivationStatus.REJECTED) {
            return WorldRuntime.FinalCarrierInstallResult.REJECTED;
        }
        if (mutation.getActivationStatus()
                != FinalCarrierLaneMutation.ActivationStatus.INSTALLED) {
            return WorldRuntime.FinalCarrierInstallResult.REJECTED;
        }
        if (lane == CanonicalWorldgenStore.Lane.OWNERS) {
            return WorldRuntime.FinalCarrierInstallResult.ALREADY_COMMITTED;
        }
        if (lane == CanonicalWorldgenStore.Lane.BLOCK_ENTITIES) {
            FinalCarrierBlockEntityPlan plan;
            try {
                plan = prepareBlockEntityPlan(mutation, exactPayload, source);
                installBlockEntityAggregates(mutation.getWorldId(), plan);
            } catch (BlockEntityConflict invalidPlan) {
                return WorldRuntime.FinalCarrierInstallResult.REJECTED;
            }
            WorldRuntime.FinalCarrierInstallResult activation = activateBlockEntityRuntime(
                    gameplayInstaller, plan);
            return activation == WorldRuntime.FinalCarrierInstallResult.COMMITTED
                    || activation == WorldRuntime.FinalCarrierInstallResult.ALREADY_COMMITTED
                    ? WorldRuntime.FinalCarrierInstallResult.ALREADY_COMMITTED : activation;
        }
        if (lane == CanonicalWorldgenStore.Lane.LOOT && lootAssignments != null) {
            try {
                NeutralFinalChunk installed = requireInstalledCarrier(
                        mutation, lane, exactPayload);
                prepareCanonicalLootActivation(mutation, lane, exactPayload, installed, true);
            } catch (CanonicalLootConflict conflict) {
                return WorldRuntime.FinalCarrierInstallResult.REJECTED;
            }
            return WorldRuntime.FinalCarrierInstallResult.ALREADY_COMMITTED;
        }
        ArchaeologyActivation archaeologyActivation;
        StructureEntityActivation structureEntityActivation;
        SpawnerActivation spawnerActivation;
        try {
            archaeologyActivation = prepareArchaeologyActivation(
                    mutation, lane, exactPayload, null, true);
            structureEntityActivation = prepareStructureEntityActivation(
                    mutation, lane, exactPayload, true,
                    preflightEntities(mutation, lane, exactPayload.entities()));
            spawnerActivation = prepareSpawnerActivation(mutation, lane, exactPayload);
        } catch (ArchaeologyConflict | StructureEntityConflict | SpawnerConflict conflict) {
            return WorldRuntime.FinalCarrierInstallResult.REJECTED;
        }
        WorldRuntime.FinalCarrierInstallResult activation;
        if (lane == CanonicalWorldgenStore.Lane.ENTITIES) {
            if (!canPublishEntities(worldId)) return WorldRuntime.FinalCarrierInstallResult.RETRY;
            publishEntitiesAfterCommit(mutation, structureEntityActivation);
            activation = WorldRuntime.FinalCarrierInstallResult.ALREADY_COMMITTED;
        } else {
            activation = activateGameplay(mutation, canonicalLaneRuntime(lane), exactPayload,
                    archaeologyActivation.aggregates, spawnerActivation.aggregates,
                    gameplayInstaller);
        }
        if (activation == WorldRuntime.FinalCarrierInstallResult.COMMITTED
                || activation == WorldRuntime.FinalCarrierInstallResult.ALREADY_COMMITTED) {
            if (mutation.getActivationStatus()
                    != FinalCarrierLaneMutation.ActivationStatus.INSTALLED) {
                mutation.markInstalled();
                lanes.save(mutation);
            }
            return WorldRuntime.FinalCarrierInstallResult.ALREADY_COMMITTED;
        }
        return activation;
    }

    /** Replays every durable activation into the current runtime before its owner starts. */
    @Override
    @Transactional
    public void recoverWorld(long worldId,
            WorldRuntime.FinalCarrierGameplayInstaller gameplayInstaller) {
        Objects.requireNonNull(gameplayInstaller, "gameplay installer");
        requireWorldLock(worldId, "final-carrier recovery world is missing");
        forEachHistoryPage(worldId,
                (after, page) -> lanes.findAllByWorldIdAndIdGreaterThanOrderById(worldId, after, page),
                rows -> recoverRows(rows, gameplayInstaller), true);
    }

    /** Replays durable state when an acknowledged chunk becomes resident again. */
    @Override
    @Transactional
    public void recoverChunk(long worldId, int chunkX, int chunkZ,
            WorldRuntime.FinalCarrierGameplayInstaller gameplayInstaller) {
        Objects.requireNonNull(gameplayInstaller, "gameplay installer");
        recoverChunkRows(worldId, chunkX, chunkZ, gameplayInstaller);
    }

    @Override
    public boolean supportsDetachedChunkRecovery() {
        return true;
    }

    /**
     * 워커에서 도는 청크 복구. 대부분의 청크는 되살릴 상주 상태가 없어 복구가 DB 검증과 멱등
     * 재기록뿐이다. 상주 활성화가 하나라도 필요하면 트랜잭션을 되돌리고
     * {@link OwnerActivationRequired}로 알린다. 호출자는 그때 틱 스레드에서 다시 복구한다.
     */
    @Override
    @Transactional
    public void recoverChunkDetached(long worldId, int chunkX, int chunkZ) {
        recoverChunkDetachedRows(worldId, chunkX, chunkZ, NO_RESIDENT_VERDICT);
    }

    /** Proves nothing: every resident activation goes back to the owner. */
    private static final WorldRuntime.FinalCarrierGameplayInstaller NO_RESIDENT_VERDICT =
            new WorldRuntime.FinalCarrierGameplayInstaller() {
                @Override
                public WorldRuntime.FinalCarrierInstallResult install(String identity,
                        TerrainAccessor.FinalLiveCarrierLane lane, NeutralFinalChunk.Sidecars payload,
                        List<ArchaeologyBrushableAggregate> archaeology) {
                    return WorldRuntime.FinalCarrierInstallResult.REJECTED;
                }

                @Override
                public WorldRuntime.FinalCarrierInstallResult installBlockEntityPlan(
                        FinalCarrierBlockEntityPlan plan) {
                    return WorldRuntime.FinalCarrierInstallResult.REJECTED;
                }
            };

    /**
     * 같은 워커 복구이되, 상주 활성화가 필요한 행은 owner가 만든 청크 스냅샷 {@code verdict}로
     * 판정한다. 성공 판정만 통과하고 나머지는 트랜잭션째 되돌려 owner 복구로 넘긴다. 호출자는 커밋
     * 뒤 verdict가 승인한 활성화를 owner에서 그대로 설치한다.
     */
    @Override
    @Transactional
    public void recoverChunkDetached(long worldId, int chunkX, int chunkZ,
            WorldRuntime.FinalCarrierGameplayInstaller verdict) {
        recoverChunkDetachedRows(worldId, chunkX, chunkZ,
                Objects.requireNonNull(verdict, "detached recovery verdict"));
    }

    private void recoverChunkDetachedRows(long worldId, int chunkX, int chunkZ,
            WorldRuntime.FinalCarrierGameplayInstaller verdict) {
        if (TickSafetyTelemetry.isTickThread()) {
            throw new IllegalStateException("detached chunk recovery cannot run on the owner tick");
        }
        recoverChunkRows(worldId, chunkX, chunkZ, provenVerdictOnly(verdict, lane -> true, true));
    }

    /** 분리 복구가 상주 활성화를 만났다. 틱 스레드 복구로 되돌아가라는 신호다. */
    public static final class OwnerActivationRequired extends RuntimeException {
        public OwnerActivationRequired() {
            super("chunk recovery needs owner-side gameplay activation", null, false, false);
        }
    }

    private void recoverChunkRows(long worldId, int chunkX, int chunkZ,
            WorldRuntime.FinalCarrierGameplayInstaller gameplayInstaller) {
        requireWorldLock(worldId, "final-carrier recovery world is missing");
        forEachHistoryPage(worldId,
                (after, page) -> lanes.findAllByWorldIdAndChunkXAndChunkZAndIdGreaterThanOrderById(
                        worldId, chunkX, chunkZ, after, page),
                rows -> {
                    rows.forEach(row -> requireHistoryCoordinate(row, chunkX, chunkZ));
                    recoverRows(rows, gameplayInstaller);
                }, true);
    }

    private static final int HISTORY_PAGE_SIZE = 1024;

    private void forEachHistoryPage(long worldId,
            java.util.function.BiFunction<Long, Pageable, List<FinalCarrierLaneMutation>> fetch,
            java.util.function.Consumer<List<FinalCarrierLaneMutation>> consume, boolean writes) {
        long after = 0L;
        Pageable pageRequest = PageRequest.of(0, HISTORY_PAGE_SIZE);
        while (true) {
            List<FinalCarrierLaneMutation> page = fetch.apply(after, pageRequest);
            if (page.size() > HISTORY_PAGE_SIZE) throw durableState("history page exceeds requested bound", null);
            if (page.isEmpty()) return;
            for (FinalCarrierLaneMutation row : page) {
                if (row == null || row.getId() == null || row.getId() <= after || row.getWorldId() != worldId) {
                    throw durableState("history page has invalid ordering or world identity", null);
                }
                after = row.getId();
            }
            consume.accept(page);
            if (tickRecoveryEntityManager != null) {
                if (writes) tickRecoveryEntityManager.flush();
                page.forEach(tickRecoveryEntityManager::detach);
            }
            if (page.size() < HISTORY_PAGE_SIZE) return;
        }
    }

    private static void requireHistoryCoordinate(FinalCarrierLaneMutation row, int chunkX, int chunkZ) {
        if (row.getChunkX() != chunkX || row.getChunkZ() != chunkZ) {
            throw durableState("history query returned a foreign coordinate", null);
        }
    }

    private void recoverRows(List<FinalCarrierLaneMutation> mutations,
            WorldRuntime.FinalCarrierGameplayInstaller gameplayInstaller) {
        Map<Long, CanonicalWorldgenStore.CanonicalChunkSnapshot> snapshots = new HashMap<>();
        for (FinalCarrierLaneMutation mutation : mutations) {
            CanonicalWorldgenStore.Lane lane = CanonicalWorldgenStore.Lane.valueOf(
                    mutation.getLane());
            if (lane == CanonicalWorldgenStore.Lane.BLOCK_TICKS
                    || lane == CanonicalWorldgenStore.Lane.FLUID_TICKS
                    || lane == CanonicalWorldgenStore.Lane.BEES) continue;
            TerrainAccessor.FinalLiveCarrierLane runtimeLane = canonicalLaneRuntime(lane);
            NeutralFinalChunk.Sidecars exact = decodeStoredProjection(mutation, lane);
            if (mutation.getActivationStatus()
                    == FinalCarrierLaneMutation.ActivationStatus.REJECTED) continue;
            if (mutation.getActivationStatus()
                    == FinalCarrierLaneMutation.ActivationStatus.INSTALLED) {
                if (lane == CanonicalWorldgenStore.Lane.OWNERS) {
                    requireInstalledProvenanceReceipt(mutation, exact, snapshots);
                    continue;
                }
                if (lane == CanonicalWorldgenStore.Lane.BLOCK_ENTITIES) {
                    NeutralFinalChunk source = requireInstalledCarrier(
                            mutation, lane, exact, snapshots);
                    FinalCarrierBlockEntityPlan plan = prepareBlockEntityPlan(mutation, exact, source);
                    installBlockEntityAggregates(mutation.getWorldId(), plan);
                    WorldRuntime.FinalCarrierInstallResult restored = activateBlockEntityRuntime(
                            gameplayInstaller, plan);
                    if (restored != WorldRuntime.FinalCarrierInstallResult.COMMITTED
                            && restored != WorldRuntime.FinalCarrierInstallResult.ALREADY_COMMITTED) {
                        throw new IllegalStateException(
                                "durable BENT installation was not restored into runtime");
                    }
                    continue;
                }
                if (lane == CanonicalWorldgenStore.Lane.LOOT && lootAssignments != null) {
                    NeutralFinalChunk source = requireInstalledCarrier(
                            mutation, lane, exact, snapshots);
                    try {
                        prepareCanonicalLootActivation(mutation, lane, exact, source, true);
                    } catch (CanonicalLootConflict conflict) {
                        throw new IllegalStateException(
                                "durable canonical LOOT installation is incomplete or conflicting",
                                conflict);
                    }
                    continue;
                }
                // An installed projection is not itself source authority.  In particular ENTS
                // rows contain a detached projection that can remain internally consistent after
                // the canonical carrier has been replaced or corrupted.  Rebind every replay to
                // the acknowledged canonical carrier before deriving live entities from it.
                if (lane == CanonicalWorldgenStore.Lane.ENTITIES) {
                    requireInstalledCarrier(mutation, lane, exact, snapshots);
                }
                try {
                    ArchaeologyActivation archaeologyActivation = prepareArchaeologyActivation(
                            mutation, lane, exact, null, true);
                    StructureEntityActivation entityActivation = prepareStructureEntityActivation(
                            mutation, lane, exact, true,
                            preflightEntities(mutation, lane, exact.entities()));
                    SpawnerActivation spawnerActivation = prepareSpawnerActivation(
                            mutation, lane, exact);
                    if (lane == CanonicalWorldgenStore.Lane.ENTITIES) {
                        if (canPublishEntities(mutation.getWorldId())) {
                            publishEntitiesAfterCommit(mutation, entityActivation);
                        }
                    } else {
                        WorldRuntime.FinalCarrierInstallResult restored = activateGameplay(
                                mutation, runtimeLane, exact, archaeologyActivation.aggregates,
                                spawnerActivation.aggregates, gameplayInstaller);
                        if (restored == WorldRuntime.FinalCarrierInstallResult.REJECTED) {
                            throw new IllegalStateException(
                                    "durable final-carrier installation conflicts with runtime");
                        }
                        if (restored != WorldRuntime.FinalCarrierInstallResult.COMMITTED
                                && restored
                                != WorldRuntime.FinalCarrierInstallResult.ALREADY_COMMITTED) {
                            throw new IllegalStateException(
                                    "durable final-carrier installation was not restored");
                        }
                    }
                } catch (ArchaeologyConflict | StructureEntityConflict | SpawnerConflict conflict) {
                    throw new IllegalStateException(
                            "durable final-carrier installation is incomplete or conflicting",
                            conflict);
                }
                continue;
            }
            CanonicalWorldgenStore.LaneReceipt claim = canonical.claim(mutation.getWorldId(),
                    mutation.getChunkX(), mutation.getChunkZ(), lane);
            if (claim == null) {
                // A pending row without a live claim is stale once canonical state is terminal.
                // It must never be promoted by replay after ACK or rejection.
                continue;
            }
            NeutralFinalChunk source = validateClaim(claim, mutation.getChunkX(),
                    mutation.getChunkZ(), mutation.getSourceFingerprint());
            requireExactProjection(lane, source.sidecars(), exact);
            activateAndSettle(mutation, runtimeLane, exact, source, claim, gameplayInstaller,
                    preflightEntities(mutation, lane, exact.entities()));
        }
    }

    private WorldRuntime.FinalCarrierInstallResult activateAndSettle(
            FinalCarrierLaneMutation mutation, TerrainAccessor.FinalLiveCarrierLane lane,
            NeutralFinalChunk.Sidecars exactPayload, NeutralFinalChunk source,
            CanonicalWorldgenStore.LaneReceipt claim,
            WorldRuntime.FinalCarrierGameplayInstaller gameplayInstaller,
            EntityPreflight entityPreflight) {
        CanonicalWorldgenStore.Lane canonicalLane = canonicalLane(lane);
        if (canonicalLane == CanonicalWorldgenStore.Lane.ENTITIES
                && !canPublishEntities(mutation.getWorldId())) {
            return WorldRuntime.FinalCarrierInstallResult.RETRY;
        }
        FinalCarrierBlockEntityPlan blockEntityPlan;
        CanonicalLootActivation canonicalLootActivation;
        ArchaeologyActivation archaeologyActivation;
        StructureEntityActivation structureEntityActivation;
        SpawnerActivation spawnerActivation;
        try {
            blockEntityPlan = prepareBlockEntityPlan(
                    mutation, canonicalLane, exactPayload, source);
            canonicalLootActivation = prepareCanonicalLootActivation(
                    mutation, canonicalLane, exactPayload, source, false);
            archaeologyActivation = prepareArchaeologyActivation(
                    mutation, canonicalLane, exactPayload, claim.receipt(), false);
            structureEntityActivation = prepareStructureEntityActivation(
                    mutation, canonicalLane, exactPayload, false, entityPreflight);
            spawnerActivation = prepareSpawnerActivation(mutation, canonicalLane, exactPayload);
        } catch (BlockEntityConflict | CanonicalLootConflict | ArchaeologyConflict
                | StructureEntityConflict | SpawnerConflict conflict) {
            mutation.markRejected();
            lanes.save(mutation);
            canonical.reject(mutation.getWorldId(), mutation.getChunkX(), mutation.getChunkZ(),
                    canonicalLane, claim.receipt());
            return WorldRuntime.FinalCarrierInstallResult.REJECTED;
        }
        WorldRuntime.FinalCarrierInstallResult activation;
        if (canonicalLane == CanonicalWorldgenStore.Lane.OWNERS) {
            // The exact durable mutation is the complete provenance aggregate. OWNR is never
            // activated into gameplay ownership, permissions, or structure state.
            activation = WorldRuntime.FinalCarrierInstallResult.COMMITTED;
        } else if (canonicalLane == CanonicalWorldgenStore.Lane.LOOT
                && lootAssignments != null) {
            activation = WorldRuntime.FinalCarrierInstallResult.COMMITTED;
        } else if (canonicalLane == CanonicalWorldgenStore.Lane.BLOCK_ENTITIES) {
            installBlockEntityAggregates(mutation.getWorldId(), blockEntityPlan);
            activation = activateBlockEntityRuntime(gameplayInstaller, blockEntityPlan);
        } else if (canonicalLane == CanonicalWorldgenStore.Lane.ENTITIES) {
            activation = WorldRuntime.FinalCarrierInstallResult.COMMITTED;
        } else {
            activation = activateGameplay(mutation, lane, exactPayload,
                    archaeologyActivation.aggregates, spawnerActivation.aggregates,
                    gameplayInstaller);
        }
        if (activation == WorldRuntime.FinalCarrierInstallResult.RETRY) return activation;
        if (activation == WorldRuntime.FinalCarrierInstallResult.REJECTED) {
            mutation.markRejected();
            lanes.save(mutation);
            canonical.reject(mutation.getWorldId(), mutation.getChunkX(), mutation.getChunkZ(),
                    canonicalLane, claim.receipt());
            return activation;
        }
        if (!archaeologyActivation.newRows.isEmpty()) {
            archaeology.saveAll(archaeologyActivation.newRows);
        }
        if (!canonicalLootActivation.newRows.isEmpty()) {
            requireLootRepository().saveAll(canonicalLootActivation.newRows);
        }
        if (!structureEntityActivation.newRows.isEmpty()) {
            structureEntityActivation.aggregate.commit(
                    structureEntityActivation.aggregate.expectedReceipts());
            structureEntities.saveAll(structureEntityActivation.newRows);
            if (!structureEntityActivation.newGeneratedRows.isEmpty()) {
                generatedEntityStates.saveAll(structureEntityActivation.newGeneratedRows);
            }
            mobs.saveAll(structureEntityActivation.newMobRows);
        }
        mutation.markInstalled();
        lanes.save(mutation);
        canonical.acknowledge(mutation.getWorldId(), mutation.getChunkX(), mutation.getChunkZ(),
                canonicalLane, claim.receipt());
        if (canonicalLane == CanonicalWorldgenStore.Lane.ENTITIES) {
            publishEntitiesAfterCommit(mutation, structureEntityActivation);
        }
        return activation;
    }

    private void requireInstalledProvenanceReceipt(FinalCarrierLaneMutation mutation,
            NeutralFinalChunk.Sidecars exactPayload,
            Map<Long, CanonicalWorldgenStore.CanonicalChunkSnapshot> snapshots) {
        CanonicalWorldgenStore.CanonicalChunkSnapshot snapshot = recoveryCanonicalSnapshot(
                mutation.getWorldId(), mutation.getChunkX(), mutation.getChunkZ(), snapshots);
        CanonicalWorldgenStore.Lane lane = CanonicalWorldgenStore.Lane.OWNERS;
        if (snapshot == null || (snapshot.laneAckMask() & lane.mask()) == 0
                || (snapshot.laneRejectedMask() & lane.mask()) != 0) {
            throw new IllegalStateException(
                    "durable OWNR installation has no acknowledged canonical receipt");
        }
        NeutralFinalChunk source = committedSource(snapshot.commit());
        if (source.chunkX() != mutation.getChunkX()
                || source.chunkZ() != mutation.getChunkZ()) {
            throw new IllegalStateException("durable OWNR receipt coordinate mismatch");
        }
        requireSource(source, mutation.getSourceFingerprint());
        requireExactProjection(lane, source.sidecars(), exactPayload);
        byte[] expected = encodeProjection(source, exactPayload);
        if (!mutation.matches(sha256(expected), expected)) {
            throw new IllegalStateException("durable OWNR receipt payload mismatch");
        }
    }

    /**
     * The committed carrier's decoded view. AGENTS rule 10l: the commit decoded exactly these
     * immutable stored bytes when it was built, so replay reuses that proven view instead of
     * cloning and decoding the identical payload. Every accepted commit must retain its
     * selected producer's bound view; an absent view fails closed rather than using another codec.
     */
    private static NeutralFinalChunk committedSource(
            CanonicalWorldgenStore.ChunkCommit commit) {
        NeutralFinalChunk source = commit.semanticFinalChunk();
        if (source == null) throw new IllegalStateException("canonical carrier has no producer-verified semantic view");
        return source;
    }

    /**
     * One recovery page observes one immutable canonical snapshot per chunk. A saved world replays
     * OWNR/LOOT/BENT/ENTS rows that share chunks, and each miss costs a full carrier blob read.
     * The pass runs inside one transaction and reuses that transaction's consistent read view for
     * a chunk instead of re-querying it, so caching does not change what the pass observes; the
     * cache is discarded after each bounded page rather than retaining all world carriers.
     */
    private CanonicalWorldgenStore.CanonicalChunkSnapshot recoveryCanonicalSnapshot(long worldId,
            int chunkX, int chunkZ,
            Map<Long, CanonicalWorldgenStore.CanonicalChunkSnapshot> snapshots) {
        if (snapshots == null) return canonical.find(worldId, chunkX, chunkZ);
        long chunkKey = ((long) chunkX << 32) ^ (chunkZ & 0xffff_ffffL);
        return snapshots.computeIfAbsent(chunkKey,
                ignored -> canonical.find(worldId, chunkX, chunkZ));
    }

    private NeutralFinalChunk requireInstalledCarrier(
            FinalCarrierLaneMutation mutation, CanonicalWorldgenStore.Lane lane,
            NeutralFinalChunk.Sidecars exactPayload) {
        return requireInstalledCarrier(mutation, lane, exactPayload, null);
    }

    private NeutralFinalChunk requireInstalledCarrier(
            FinalCarrierLaneMutation mutation, CanonicalWorldgenStore.Lane lane,
            NeutralFinalChunk.Sidecars exactPayload,
            Map<Long, CanonicalWorldgenStore.CanonicalChunkSnapshot> snapshots) {
        CanonicalWorldgenStore.CanonicalChunkSnapshot snapshot = recoveryCanonicalSnapshot(
                mutation.getWorldId(), mutation.getChunkX(), mutation.getChunkZ(), snapshots);
        if (snapshot == null || (snapshot.laneAckMask() & lane.mask()) == 0
                || (snapshot.laneRejectedMask() & lane.mask()) != 0) {
            throw new IllegalStateException(
                    "durable " + lane + " installation has no acknowledged canonical receipt");
        }
        NeutralFinalChunk source = committedSource(snapshot.commit());
        if (source.chunkX() != mutation.getChunkX()
                || source.chunkZ() != mutation.getChunkZ()) {
            throw new IllegalStateException("durable " + lane + " receipt coordinate mismatch");
        }
        requireSource(source, mutation.getSourceFingerprint());
        requireExactProjection(lane, source.sidecars(), exactPayload);
        byte[] expected = encodeProjection(source, exactPayload);
        if (!mutation.matches(sha256(expected), expected)) {
            throw new IllegalStateException("durable " + lane + " receipt payload mismatch");
        }
        return source;
    }

    private FinalCarrierBlockEntityPlan prepareBlockEntityPlan(
            FinalCarrierLaneMutation mutation, NeutralFinalChunk.Sidecars exactPayload,
            NeutralFinalChunk source) {
        return prepareBlockEntityPlan(mutation, CanonicalWorldgenStore.Lane.BLOCK_ENTITIES,
                exactPayload, source);
    }

    private FinalCarrierBlockEntityPlan prepareBlockEntityPlan(
            FinalCarrierLaneMutation mutation, CanonicalWorldgenStore.Lane lane,
            NeutralFinalChunk.Sidecars exactPayload, NeutralFinalChunk source) {
        if (lane != CanonicalWorldgenStore.Lane.BLOCK_ENTITIES) return null;
        try {
            NeutralFinalChunk typed = decodeStoredCarrier(mutation, mutation.getTypedPayload());
            if (!typed.sidecars().blockEntities().equals(exactPayload.blockEntities())) {
                throw new IllegalArgumentException("stored BENT projection differs from delivery");
            }
            java.util.Set<Integer> retainedPositions = exactPayload.blockEntities().stream()
                    .map(NeutralFinalChunk.BlockEntity::packed)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            List<NeutralFinalChunk.Loot> lootEvidence = source.sidecars().loot().stream()
                    .filter(value -> retainedPositions.contains(value.packed())).toList();
            NeutralFinalChunk.Sidecars planningSidecars = new NeutralFinalChunk.Sidecars(
                    List.of(), List.of(), lootEvidence, List.of(), List.of(), List.of(),
                    List.of(), exactPayload.blockEntities(), List.of());
            NeutralFinalChunk planningCarrier =
                    source.withSidecars(planningSidecars);
            return FinalCarrierBlockEntityPlan.prepare(
                    mutation.getInstallationIdentity(), planningCarrier);
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw new BlockEntityConflict();
        }
    }

    private CanonicalLootActivation prepareCanonicalLootActivation(
            FinalCarrierLaneMutation mutation, CanonicalWorldgenStore.Lane lane,
            NeutralFinalChunk.Sidecars exactPayload, NeutralFinalChunk source,
            boolean requireDurableRows) {
        if (lane != CanonicalWorldgenStore.Lane.LOOT) return CanonicalLootActivation.EMPTY;
        if (lootAssignments == null) return CanonicalLootActivation.EMPTY;
        try {
            WorldCanonicalLootAssignmentRepository repository = requireLootRepository();
            NeutralFinalChunk typed = decodeStoredCarrier(mutation, mutation.getTypedPayload());
            if (!typed.sidecars().loot().equals(exactPayload.loot())) {
                throw new IllegalArgumentException("stored LOOT projection differs from delivery");
            }
            long worldSeed = worlds.findById(mutation.getWorldId())
                    .orElseThrow(() -> new IllegalStateException("canonical LOOT world is missing"))
                    .getSeed();
            CanonicalLootAssignmentPlan plan = CanonicalLootAssignmentPlan.prepare(
                    worldSeed, mutation, source, exactPayload);
            List<WorldCanonicalLootAssignment> stored = repository
                    .findAllByLaneInstallationIdentityOrderByPacked(
                            mutation.getInstallationIdentity());
            Map<Integer, WorldCanonicalLootAssignment> storedByPacked = new HashMap<>();
            for (WorldCanonicalLootAssignment row : stored) {
                if (row.getWorldId() != mutation.getWorldId()
                        || row.getChunkX() != mutation.getChunkX()
                        || row.getChunkZ() != mutation.getChunkZ()
                        || storedByPacked.put(row.getPacked(), row) != null) {
                    throw new IllegalStateException("conflicting persisted canonical LOOT rows");
                }
            }
            ArrayList<WorldCanonicalLootAssignment> newRows = new ArrayList<>();
            for (WorldCanonicalLootAssignment candidate : plan.assignments()) {
                WorldCanonicalLootAssignment existing = storedByPacked.remove(
                        candidate.getPacked());
                if (existing != null) {
                    if (!existing.sameDefinition(candidate)) {
                        throw new IllegalStateException("canonical LOOT replay definition conflict");
                    }
                    continue;
                }
                if (requireDurableRows) {
                    throw new IllegalStateException("acknowledged canonical LOOT row is missing");
                }
                WorldCanonicalLootAssignment occupied = repository
                        .findByWorldIdAndPosXAndPosYAndPosZ(candidate.getWorldId(),
                                candidate.getPosX(), candidate.getPosY(), candidate.getPosZ())
                        .orElse(null);
                if (occupied != null && !occupied.sameDefinition(candidate)) {
                    throw new IllegalStateException("canonical LOOT position is already assigned");
                }
                if (occupied == null) newRows.add(candidate);
            }
            if (!storedByPacked.isEmpty()) {
                throw new IllegalStateException("persisted canonical LOOT rows exceed lane payload");
            }
            return new CanonicalLootActivation(newRows);
        } catch (IllegalArgumentException | IllegalStateException conflict) {
            throw new CanonicalLootConflict();
        }
    }

    private WorldCanonicalLootAssignmentRepository requireLootRepository() {
        if (lootAssignments == null) {
            throw new IllegalStateException("canonical LOOT repository is unavailable");
        }
        return lootAssignments;
    }

    private static final class CanonicalLootActivation {
        private static final CanonicalLootActivation EMPTY =
                new CanonicalLootActivation(List.of());
        private final List<WorldCanonicalLootAssignment> newRows;

        private CanonicalLootActivation(List<WorldCanonicalLootAssignment> newRows) {
            this.newRows = List.copyOf(newRows);
        }
    }

    private static final class CanonicalLootConflict extends RuntimeException {
    }

    private void installBlockEntityAggregates(long worldId,
            FinalCarrierBlockEntityPlan plan) {
        for (FinalCarrierBlockEntityPlan.PlannedBlockEntity entry : plan.entries()) {
            String aggregateIdentity = plan.installationIdentity() + ":" + entry.packed();
            String fingerprint = plan.sourceFingerprint();
            switch (entry.entityType()) {
                case "minecraft:chest" -> Objects.requireNonNull(
                        chests.installGeneratedContainerJoiningTransaction(worldId,
                                entry.x(), entry.y(), entry.z(), 27, aggregateIdentity,
                                fingerprint), "generated chest aggregate outcome");
                case "minecraft:decorated_pot" -> Objects.requireNonNull(
                        chests.installGeneratedContainerJoiningTransaction(worldId,
                                entry.x(), entry.y(), entry.z(), 1, aggregateIdentity,
                                fingerprint), "generated decorated-pot aggregate outcome");
                case "minecraft:furnace" -> Objects.requireNonNull(
                        furnaces.installGeneratedJoiningTransaction(worldId,
                                entry.x(), entry.y(), entry.z(), FurnaceVariant.FURNACE,
                                aggregateIdentity, fingerprint),
                        "generated furnace aggregate outcome");
                case "minecraft:brewing_stand" -> Objects.requireNonNull(
                        brewing.installGeneratedWeaknessStandJoiningTransaction(worldId,
                                entry.x(), entry.y(), entry.z(),
                                aggregateIdentity, fingerprint),
                        "generated brewing aggregate outcome");
                case "minecraft:sign" -> Objects.requireNonNull(
                        signs.installGeneratedJoiningTransaction(worldId,
                                entry.x(), entry.y(), entry.z(),
                                List.of("", "<----", "---->", ""),
                                aggregateIdentity, fingerprint),
                        "generated sign aggregate outcome");
                case "minecraft:dispenser" -> Objects.requireNonNull(
                        dispensers.installGeneratedJoiningTransaction(worldId,
                                entry.x(), entry.y(), entry.z(), aggregateIdentity, fingerprint),
                        "generated dispenser aggregate outcome");
                default -> throw new IllegalStateException(
                        "preflighted BENT aggregate type is unsupported");
            }
        }
    }

    private static final class BlockEntityConflict extends RuntimeException {
    }

    private static WorldRuntime.FinalCarrierInstallResult activateGameplay(
            FinalCarrierLaneMutation mutation, TerrainAccessor.FinalLiveCarrierLane lane,
            NeutralFinalChunk.Sidecars exactPayload,
            List<ArchaeologyBrushableAggregate> archaeology,
            List<SpawnerAggregate> spawners,
            WorldRuntime.FinalCarrierGameplayInstaller gameplayInstaller) {
        return Objects.requireNonNull(gameplayInstaller.installPrepared(
                mutation.getInstallationIdentity(), lane, exactPayload, archaeology, spawners),
                "gameplay activation result");
    }

    private static WorldRuntime.FinalCarrierInstallResult activateBlockEntityRuntime(
            WorldRuntime.FinalCarrierGameplayInstaller gameplayInstaller,
            FinalCarrierBlockEntityPlan plan) {
        if (plan.entries().stream().noneMatch(entry -> entry.decoratedPot().isPresent())) {
            return WorldRuntime.FinalCarrierInstallResult.COMMITTED;
        }
        return Objects.requireNonNull(gameplayInstaller.installBlockEntityPlan(plan),
                "block-entity gameplay activation result");
    }

    private SpawnerActivation prepareSpawnerActivation(
            FinalCarrierLaneMutation mutation, CanonicalWorldgenStore.Lane lane,
            NeutralFinalChunk.Sidecars exactPayload) {
        if (lane != CanonicalWorldgenStore.Lane.SPAWNERS) return SpawnerActivation.EMPTY;
        try {
            NeutralFinalChunk typed = decodeStoredCarrier(mutation, mutation.getTypedPayload());
            if (!typed.sidecars().spawners().equals(exactPayload.spawners())) {
                throw new SpawnerConflict();
            }
            if (exactPayload.spawners().isEmpty()) return SpawnerActivation.EMPTY;
            return new SpawnerActivation(List.of(SpawnerAggregate.prepare(
                    mutation.getInstallationIdentity(), typed)));
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw new SpawnerConflict();
        }
    }

    private static final class SpawnerActivation {
        private static final SpawnerActivation EMPTY = new SpawnerActivation(List.of());
        private final List<SpawnerAggregate> aggregates;

        private SpawnerActivation(List<SpawnerAggregate> aggregates) {
            this.aggregates = List.copyOf(aggregates);
        }
    }

    private static final class SpawnerConflict extends RuntimeException {
    }

    private ArchaeologyActivation prepareArchaeologyActivation(
            FinalCarrierLaneMutation mutation, CanonicalWorldgenStore.Lane lane,
            NeutralFinalChunk.Sidecars exactPayload, byte[] claimedReceipt,
            boolean requireDurableRows) {
        if (lane != CanonicalWorldgenStore.Lane.ARCHAEOLOGY) {
            return ArchaeologyActivation.EMPTY;
        }
        List<WorldArchaeologyBrushable> stored =
                archaeology.findAllByLaneInstallationIdentityOrderByPacked(
                        mutation.getInstallationIdentity());
        Map<Integer, WorldArchaeologyBrushable> storedByPacked = new HashMap<>();
        for (WorldArchaeologyBrushable row : stored) {
            if (row.getWorldId() != mutation.getWorldId()
                    || row.getChunkX() != mutation.getChunkX()
                    || row.getChunkZ() != mutation.getChunkZ()
                    || !row.getLaneInstallationIdentity().equals(
                            mutation.getInstallationIdentity())
                    || storedByPacked.put(row.getPacked(), row) != null) {
                throw new ArchaeologyConflict();
            }
        }
        if (stored.size() > exactPayload.archaeology().size()) {
            throw new ArchaeologyConflict();
        }
        NeutralFinalChunk source = decodeStoredCarrier(mutation, mutation.getTypedPayload());
        short[] blockIds = source.blockIds();
        Map<Integer, NeutralFinalChunk.StateOverride> stateOverrides = source.stateOverrides();
        List<ArchaeologyBrushableAggregate> aggregates = new ArrayList<>();
        List<WorldArchaeologyBrushable> newRows = new ArrayList<>();
        for (NeutralFinalChunk.Archaeology value : exactPayload.archaeology()) {
            WorldArchaeologyBrushable existing = storedByPacked.remove(value.packed());
            if (existing == null && (requireDurableRows || claimedReceipt == null)) {
                throw new ArchaeologyConflict();
            }
            byte[] receipt = claimedReceipt != null
                    ? claimedReceipt : existing.getCanonicalReceiptBytes();
            NeutralFinalChunk.StateOverride state = stateOverrides.get(value.packed());
            if (state == null) {
                state = source.defaultState(Short.toUnsignedInt(blockIds[value.packed()]));
            }
            int localX = value.packed() % Blocks.CHUNK_X;
            int yz = value.packed() / Blocks.CHUNK_X;
            int localZ = yz % Blocks.CHUNK_Z;
            int y = Blocks.MIN_Y + yz / Blocks.CHUNK_Z;
            String identity = mutation.getInstallationIdentity() + ":" + value.packed();
            int targetX = mutation.getChunkX() * Blocks.CHUNK_X + localX;
            int targetZ = mutation.getChunkZ() * Blocks.CHUNK_Z + localZ;
            if (existing == null && (archaeology.findByInstallationIdentity(identity).isPresent()
                    || archaeology.findByWorldIdAndTargetXAndTargetYAndTargetZ(
                            mutation.getWorldId(), targetX, y, targetZ).isPresent())) {
                throw new ArchaeologyConflict();
            }
            ArchaeologyBrushableAggregate candidate;
            try {
                candidate = ArchaeologyBrushableAggregate.install(
                        new ArchaeologyBrushableAggregate.Installation(identity, value.table(),
                                value.seed(), new ArchaeologyBrushableAggregate.TargetBlock(
                                        targetX, y, targetZ, state.exactState()), receipt));
            } catch (IllegalArgumentException unsupportedTableOrResult) {
                throw new ArchaeologyConflict();
            }
            if (existing != null) {
                try {
                    ArchaeologyBrushableAggregate restored = existing.toAggregate();
                    restored.replayInstall(installation(candidate));
                    aggregates.add(restored);
                } catch (IllegalStateException conflict) {
                    throw new ArchaeologyConflict();
                }
            } else {
                newRows.add(new WorldArchaeologyBrushable(
                        mutation.getWorldId(), mutation.getChunkX(), mutation.getChunkZ(),
                        value.packed(), mutation.getInstallationIdentity(), candidate));
                aggregates.add(candidate);
            }
        }
        if (!storedByPacked.isEmpty()) throw new ArchaeologyConflict();
        return new ArchaeologyActivation(List.copyOf(aggregates), List.copyOf(newRows));
    }

    private static ArchaeologyBrushableAggregate.Installation installation(
            ArchaeologyBrushableAggregate aggregate) {
        return new ArchaeologyBrushableAggregate.Installation(
                aggregate.installationIdentity(), aggregate.exactTableKey(), aggregate.rawSeed(),
                aggregate.target(), aggregate.canonicalReceiptBytes());
    }

    private static final class ArchaeologyActivation {
        private static final ArchaeologyActivation EMPTY =
                new ArchaeologyActivation(List.of(), List.of());
        private final List<ArchaeologyBrushableAggregate> aggregates;
        private final List<WorldArchaeologyBrushable> newRows;

        private ArchaeologyActivation(List<ArchaeologyBrushableAggregate> aggregates,
                List<WorldArchaeologyBrushable> newRows) {
            this.aggregates = aggregates;
            this.newRows = newRows;
        }
    }

    private static final class ArchaeologyConflict extends RuntimeException {
    }

    private StructureEntityActivation prepareStructureEntityActivation(
            FinalCarrierLaneMutation mutation, CanonicalWorldgenStore.Lane lane,
            NeutralFinalChunk.Sidecars exactPayload, boolean requireDurableRows,
            EntityPreflight preflight) {
        if (lane != CanonicalWorldgenStore.Lane.ENTITIES || exactPayload.entities().isEmpty()) {
            return StructureEntityActivation.EMPTY;
        }
        preflight.requireMatches(mutation, exactPayload.entities().size());
        List<WorldStructureEntity> stored = structureEntities
                .findAllByLaneInstallationIdentityOrderByEncounterOrdinal(
                        mutation.getInstallationIdentity());
        if (stored.isEmpty()) {
            if (requireDurableRows) throw new StructureEntityConflict();
            List<Long> ids = reserveEntityIds(mutation.getWorldId(), exactPayload.entities().size());
            StructureEntityAggregate.Installation installation =
                    new StructureEntityAggregate.Installation(
                            mutation.getInstallationIdentity(), exactPayload.entities(), ids);
            StructureEntityAggregate aggregate = StructureEntityAggregate.prepare(installation);
            for (StructureEntityAggregate.PlannedEntity planned : aggregate.plannedEntities()) {
                // world_structure_entities도 같은 world 안에서 authoritative id가 유일하다.
                // 이 표를 확인하지 않으면 다른 설치가 이미 쓴 id를 예약했을 때 중복키로 터지고
                // 레인 전체가 롤백된다. 세 표를 모두 보고 정상 conflict 재시도로 돌린다.
                if (mobs.findLockedByWorldIdAndMobId(mutation.getWorldId(),
                        planned.authoritativeEntityId()).isPresent()
                        || generatedEntityStates.findLockedByWorldIdAndAuthoritativeEntityId(
                                mutation.getWorldId(), planned.authoritativeEntityId()).isPresent()
                        || structureEntities.findByWorldIdAndAuthoritativeEntityId(
                                mutation.getWorldId(), planned.authoritativeEntityId())
                                .isPresent()) {
                    throw new StructureEntityConflict();
                }
            }
            List<WorldStructureEntity> newRows = aggregate.plannedEntities().stream()
                    .map(entity -> new WorldStructureEntity(mutation.getWorldId(),
                            mutation.getChunkX(), mutation.getChunkZ(),
                            mutation.getInstallationIdentity(), aggregate.sourceFingerprint(),
                            entity))
                    .toList();
            aggregate.commit(aggregate.expectedReceipts());
            List<MobPersistenceSnapshot> snapshots = mobSnapshots(aggregate, preflight);
            List<WorldMob> mobRows = snapshots.stream()
                    .map(snapshot -> new WorldMob(mutation.getWorldId(), snapshot)).toList();
            ArrayList<WorldGeneratedStructureEntityState> generatedRows = new ArrayList<>();
            for (int index = 0; index < newRows.size(); index++) {
                if (preflight.rows().get(index).classification()
                        != EntityClassification.VALID_GENERATED) continue;
                generatedRows.add(WorldGeneratedStructureEntityState.install(
                        mutation.getWorldId(), mutation.getChunkX(), mutation.getChunkZ(),
                        mutation.getInstallationIdentity(), newRows.get(index), aggregate));
            }
            List<WorldGeneratedStructureEntityState.RuntimeSnapshot> generatedSnapshots =
                    generatedRows.stream().map(WorldGeneratedStructureEntityState::runtimeSnapshot)
                            .toList();
            return new StructureEntityActivation(aggregate, newRows, generatedRows, mobRows,
                    snapshots, generatedSnapshots, newRows, generatedRows);
        }
        if (stored.size() != exactPayload.entities().size()) {
            throw new StructureEntityConflict();
        }
        List<Long> ids = stored.stream().map(WorldStructureEntity::getAuthoritativeEntityId).toList();
        StructureEntityAggregate.Installation installation =
                new StructureEntityAggregate.Installation(
                        mutation.getInstallationIdentity(), exactPayload.entities(), ids);
        for (int index = 0; index < stored.size(); index++) {
            if (!stored.get(index).matches(mutation.getWorldId(), mutation.getChunkX(),
                    mutation.getChunkZ(), mutation.getInstallationIdentity(),
                    installation.sourceFingerprint(), installation.plannedEntities().get(index))) {
                throw new StructureEntityConflict();
            }
        }
        StructureEntityAggregate aggregate;
        try {
            aggregate = StructureEntityAggregate.restore(installation,
                    new StructureEntityAggregate.TerminalState(
                            mutation.getInstallationIdentity(), installation.sourceFingerprint(),
                            stored.stream().map(WorldStructureEntity::toReceipt).toList()));
        } catch (IllegalStateException conflict) {
            throw new StructureEntityConflict();
        }
        List<WorldGeneratedStructureEntityState> durableGenerated = generatedEntityStates
                .findLockedByLaneInstallationIdentityOrderByEncounterOrdinal(
                        mutation.getInstallationIdentity());
        ArrayList<WorldGeneratedStructureEntityState.RuntimeSnapshot> generatedSnapshots =
                new ArrayList<>();
        int generatedIndex = 0;
        ArrayList<MobPersistenceSnapshot> liveSnapshots = new ArrayList<>();
        for (int index = 0; index < aggregate.plannedEntities().size(); index++) {
            StructureEntityAggregate.PlannedEntity planned =
                    aggregate.plannedEntities().get(index);
            WorldMob row = mobs.findLockedByWorldIdAndMobId(
                    mutation.getWorldId(), planned.authoritativeEntityId()).orElse(null);
            EntityClassification classification = preflight.rows().get(index).classification();
            if (classification == EntityClassification.VALID_GENERATED) {
                if (row != null) throw new StructureEntityConflict();
                if (generatedIndex >= durableGenerated.size()) throw new StructureEntityConflict();
                WorldGeneratedStructureEntityState generated =
                        durableGenerated.get(generatedIndex++);
                try {
                    generated.requireSameBinding(mutation.getWorldId(), mutation.getChunkX(),
                            mutation.getChunkZ(), mutation.getInstallationIdentity(),
                            stored.get(index), aggregate);
                } catch (IllegalArgumentException | IllegalStateException conflict) {
                    throw new StructureEntityConflict();
                }
                generatedSnapshots.add(generated.runtimeSnapshot());
                continue;
            }
            if (classification == EntityClassification.UNOWNED) {
                if (row != null) throw new StructureEntityConflict();
                continue;
            }
            if (stored.get(index).bindingStatus() == WorldStructureEntity.BindingStatus.DEAD) {
                if (row != null) throw new StructureEntityConflict();
                continue;
            }
            if (row == null) {
                // 살아있다고 기록된 결속인데 몹 행이 없다면 그 몹은 죽어 사라진 것이다. 죽음
                // 처리가 결속을 DEAD로 못 바꾼 채 끝났을 뿐이며, 이걸 충돌로 보면 그 청크는
                // 영원히 승인되지 않는다. 지금 묘비를 찍고 소비된 것으로 넘어간다.
                WorldStructureEntity binding = stored.get(index);
                if (binding.markDead()) structureEntities.save(binding);
                continue;
            }
            MobPersistenceSnapshot current = row.toSnapshot();
            MobPersistenceSnapshot canonicalBinding = mobSnapshot(planned, preflight.mobSeed());
            if (!MobRuntime.sameFinalCarrierBinding(canonicalBinding, current)) {
                throw new StructureEntityConflict();
            }
            liveSnapshots.add(current);
        }
        if (generatedIndex != durableGenerated.size()) throw new StructureEntityConflict();
        return new StructureEntityActivation(aggregate, List.of(), List.of(), List.of(),
                List.copyOf(liveSnapshots), List.copyOf(generatedSnapshots), stored,
                durableGenerated);
    }

    private EntityPreflight preflightEntities(FinalCarrierLaneMutation mutation,
            CanonicalWorldgenStore.Lane lane,
            List<NeutralFinalChunk.StructureEntity> entities) {
        if (lane != CanonicalWorldgenStore.Lane.ENTITIES || entities.isEmpty()) {
            return EntityPreflight.EMPTY;
        }
        try {
            StructureEntityAggregate preflight = StructureEntityAggregate.prepare(
                    new StructureEntityAggregate.Installation(
                            mutation.getInstallationIdentity(), entities));
            ArrayList<EntityRowPreflight> rows =
                    new ArrayList<>(entities.size());
            boolean requiresMobSeed = false;
            NeutralFinalChunk producerSource = null;
            int entityOrdinal = 0;
            for (StructureEntityAggregate.PlannedEntity planned : preflight.plannedEntities()) {
                NeutralFinalChunk.StructureEntity originalEntity = entities.get(entityOrdinal++);
                var generated = WorldGeneratedStructureEntityState.classifyPlannedEntity(planned);
                if (generated.isPresent()) {
                    rows.add(new EntityRowPreflight(EntityClassification.VALID_GENERATED,
                            generated.get()));
                } else if (isKnownMobKey(planned.entityKey())) {
                    if (producerSource == null) {
                        CanonicalWorldgenStore.CanonicalChunkSnapshot snapshot = canonical.find(
                                mutation.getWorldId(), mutation.getChunkX(), mutation.getChunkZ());
                        if (snapshot == null) throw new StructureEntityConflict();
                        producerSource = committedSource(snapshot.commit());
                    }
                    if (!producerSource.matchesCanonicalMob(originalEntity)) throw new StructureEntityConflict();
                    rows.add(new EntityRowPreflight(EntityClassification.VALID_MOB, null));
                    requiresMobSeed = true;
                } else {
                    rows.add(new EntityRowPreflight(EntityClassification.UNOWNED, null));
                }
            }
            int mobSeed = 0;
            if (requiresMobSeed) {
                mobSeed = Math.toIntExact(worlds.findById(mutation.getWorldId())
                        .orElseThrow(StructureEntityConflict::new).getSeed());
            }
            return new EntityPreflight(mutation.getInstallationIdentity(), rows, mobSeed);
        } catch (IllegalArgumentException | IllegalStateException malformedKnownType) {
            throw new StructureEntityConflict();
        }
    }

    private enum EntityClassification { VALID_MOB, VALID_GENERATED, UNOWNED }

    private record EntityRowPreflight(EntityClassification classification,
            GeneratedStructureEntityFacts.Kind generatedKind) {
        private EntityRowPreflight {
            Objects.requireNonNull(classification, "ENTS row classification");
            if ((classification == EntityClassification.VALID_GENERATED)
                    != (generatedKind != null)) {
                throw new IllegalArgumentException("invalid generated ENTS classification");
            }
        }
    }

    private record EntityPreflight(String installationIdentity,
            List<EntityRowPreflight> rows, int mobSeed) {
        private static final EntityPreflight EMPTY =
                new EntityPreflight(null, List.of(), 0);

        private EntityPreflight {
            rows = List.copyOf(rows);
        }

        private void requireMatches(FinalCarrierLaneMutation mutation, int rowCount) {
            if (!Objects.equals(installationIdentity, mutation.getInstallationIdentity())
                    || rows.size() != rowCount) {
                throw new StructureEntityConflict();
            }
        }
    }

    private static final class StructureEntityActivation {
        private static final StructureEntityActivation EMPTY =
                new StructureEntityActivation(null, List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of());
        private final StructureEntityAggregate aggregate;
        private final List<WorldStructureEntity> newRows;
        private final List<WorldGeneratedStructureEntityState> newGeneratedRows;
        private final List<WorldMob> newMobRows;
        private final List<MobPersistenceSnapshot> snapshots;
        private final List<WorldGeneratedStructureEntityState.RuntimeSnapshot>
                generatedSnapshots;
        private final List<WorldStructureEntity> allRows;
        private final List<WorldGeneratedStructureEntityState> allGeneratedRows;

        private StructureEntityActivation(StructureEntityAggregate aggregate,
                List<WorldStructureEntity> newRows,
                List<WorldGeneratedStructureEntityState> newGeneratedRows,
                List<WorldMob> newMobRows, List<MobPersistenceSnapshot> snapshots,
                List<WorldGeneratedStructureEntityState.RuntimeSnapshot> generatedSnapshots,
                List<WorldStructureEntity> allRows,
                List<WorldGeneratedStructureEntityState> allGeneratedRows) {
            this.aggregate = aggregate;
            this.newRows = List.copyOf(newRows);
            this.newGeneratedRows = List.copyOf(newGeneratedRows);
            this.newMobRows = List.copyOf(newMobRows);
            this.snapshots = List.copyOf(snapshots);
            this.generatedSnapshots = List.copyOf(generatedSnapshots);
            this.allRows = List.copyOf(allRows);
            this.allGeneratedRows = List.copyOf(allGeneratedRows);
        }
    }

    private static final class StructureEntityConflict extends RuntimeException {
    }

    private boolean canPublishEntities(long worldId) {
        return entityPublishers.containsKey(worldId)
                && TransactionSynchronizationManager.isSynchronizationActive();
    }

    private void publishEntitiesAfterCommit(FinalCarrierLaneMutation mutation,
            StructureEntityActivation activation) {
        if (activation.aggregate == null) return;
        long worldId = mutation.getWorldId();
        CommittedEntityActivationPublisher publisher = entityPublishers.get(worldId);
        if (publisher == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("ENTS live publisher is not transaction-bound");
        }
        CommittedEntityActivation committed = new CommittedEntityActivation(
                mutation, activation.aggregate, activation.snapshots,
                activation.generatedSnapshots);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                // A runtime can detach while this transaction is committing.  Never publish to a
                // stale runtime owner: the replacement recovers the committed row through its own
                // transaction-bound registration.
                if (entityPublishers.get(worldId) == publisher) publisher.publish(committed);
            }
        });
    }

    /** 이미 쓰인 id를 건너뛰는 한도. 이보다 조밀하게 막히면 예약이 아니라 상태가 깨진 것이다. */
    private static final int MAX_ENTITY_ID_SKIPS = 4096;

    /** 워커 예약 도중 소유 스레드가 계속 몹 id를 발급해 구간을 앞지를 때의 재시도 한도. */
    private static final int MAX_LIVE_ENTITY_ID_CLAIMS = 16;

    private List<Long> reserveEntityIds(long worldId, int count) {
        CommittedEntityActivationPublisher publisher = entityPublishers.get(worldId);
        if (publisher == null) throw new StructureEntityConflict();
        Long mobMaximum = mobs.findMaximumMobIdByWorldId(worldId);
        long persistedHighWater = mobMaximum == null ? 0L : mobMaximum;
        Long sourceMaximum = structureEntities.findMaximumAuthoritativeEntityIdByWorldId(worldId);
        Long generatedMaximum =
                generatedEntityStates.findMaximumAuthoritativeEntityIdByWorldId(worldId);
        persistedHighWater = Math.max(persistedHighWater,
                sourceMaximum == null ? 0L : sourceMaximum);
        persistedHighWater = Math.max(persistedHighWater,
                generatedMaximum == null ? 0L : generatedMaximum);
        WorldStructureEntityIdSequence sequence = lockedEntityIdSequence(worldId);
        int skipped = 0;
        for (int attempt = 0; attempt < MAX_LIVE_ENTITY_ID_CLAIMS; attempt++) {
            // 이 트랜잭션은 워커에서도 돈다. 바닥 읽기와 예약 사이에 소유 스레드가 새 몹을 만들면
            // 그 id가 예약 구간에 들어갈 수 있다. 구간을 원자적으로 차지한 뒤에만 쓰고, 앞질렸으면
            // 새 바닥 위에서 다시 예약한다. 버린 id는 시퀀스가 이미 넘었으므로 재사용되지 않는다.
            long liveFloor = publisher.currentLiveEntityIdFloor();
            if (liveFloor <= 0L) throw new StructureEntityConflict();
            sequence.advanceHighWater(Math.max(persistedHighWater, liveFloor - 1L));
            ArrayList<Long> ids = new ArrayList<>(count);
            while (ids.size() < count) {
                long candidate = sequence.reserve(1);
                // 같은 월드에서 이미 쓰인 authoritative id는 건너뛴다. 최고수위만 보고 예약하면
                // 다른 설치가 남긴 행과 겹칠 수 있고, 그 순간 중복키로 레인 전체가 롤백된다.
                if (structureEntities.findByWorldIdAndAuthoritativeEntityId(worldId, candidate)
                        .isPresent()) {
                    if (++skipped > MAX_ENTITY_ID_SKIPS) throw new StructureEntityConflict();
                    continue;
                }
                ids.add(candidate);
            }
            if (!publisher.claimLiveEntityIds(ids.getFirst(), ids.getLast())) continue;
            entityIdSequences.saveAndFlush(sequence);
            return List.copyOf(ids);
        }
        throw new FinalCarrierLaneRetryable(
                "live mob identities kept overtaking the ENTS identity reservation");
    }

    /**
     * 아직 시퀀스 행이 없는 월드에서는 잠글 대상이 없어 동시 설치 둘이 같은 id를 예약한다.
     * 행을 먼저 만들어 flush한 뒤 다시 잠가, 예약을 항상 행 잠금으로 직렬화한다. 같은 순간
     * 다른 트랜잭션이 행을 만들었으면 PK 중복이므로 레인 conflict로 되돌려 재시도하게 한다.
     */
    private WorldStructureEntityIdSequence lockedEntityIdSequence(long worldId) {
        Optional<WorldStructureEntityIdSequence> locked =
                entityIdSequences.findLockedByWorldId(worldId);
        if (locked.isPresent()) return locked.get();
        try {
            entityIdSequences.saveAndFlush(new WorldStructureEntityIdSequence(worldId, 0L));
        } catch (DataIntegrityViolationException | PersistenceException race) {
            throw new StructureEntityConflict();
        }
        return entityIdSequences.findLockedByWorldId(worldId)
                .orElseThrow(StructureEntityConflict::new);
    }

    private List<MobPersistenceSnapshot> mobSnapshots(StructureEntityAggregate aggregate,
            EntityPreflight preflight) {
        ArrayList<MobPersistenceSnapshot> snapshots = new ArrayList<>();
        for (int index = 0; index < aggregate.plannedEntities().size(); index++) {
            if (preflight.rows().get(index).classification()
                    == EntityClassification.VALID_MOB) {
                snapshots.add(mobSnapshot(
                        aggregate.plannedEntities().get(index), preflight.mobSeed()));
            }
        }
        return List.copyOf(snapshots);
    }

    private MobPersistenceSnapshot mobSnapshot(
            StructureEntityAggregate.PlannedEntity planned, int seed) {
        String variant = planned.entityKey().equals("minecraft:cat") ? "all_black" : null;
        boolean villagerData = planned.entityKey().equals("minecraft:villager")
                || planned.entityKey().equals("minecraft:zombie_villager");
        return MobRuntime.finalCarrierSnapshot(planned, seed, variant,
                villagerData ? "snow" : null, villagerData ? "CLERIC" : null,
                villagerData ? 1 : 0);
    }

    private static boolean isKnownMobKey(String entityKey) {
        return switch (entityKey) {
            case "minecraft:witch", "minecraft:cat", "minecraft:villager",
                    "minecraft:zombie_villager", "minecraft:evoker",
                    "minecraft:vindicator", "minecraft:allay" -> true;
            default -> false;
        };
    }

    /** Revalidate durable lane bytes through the canonical source's exact producer/profile. */
    private NeutralFinalChunk decodeStoredCarrier(FinalCarrierLaneMutation mutation, byte[] payload) {
        CanonicalWorldgenStore.CanonicalChunkSnapshot snapshot = canonical.find(
                mutation.getWorldId(), mutation.getChunkX(), mutation.getChunkZ());
        if (snapshot == null) throw new IllegalStateException("durable lane has no canonical producer source");
        return committedSource(snapshot.commit()).verifyCarrier(payload);
    }

    private NeutralFinalChunk.Sidecars decodeStoredProjection(
            FinalCarrierLaneMutation mutation, CanonicalWorldgenStore.Lane lane) {
        byte[] typedPayload = mutation.getTypedPayload();
        if (!mutation.matches(sha256(typedPayload), typedPayload)) {
            throw new IllegalStateException("final-carrier durable payload fingerprint mismatch");
        }
        NeutralFinalChunk decoded = decodeStoredCarrier(mutation, typedPayload);
        if (decoded.chunkX() != mutation.getChunkX() || decoded.chunkZ() != mutation.getChunkZ()) {
            throw new IllegalStateException("final-carrier durable payload coordinate mismatch");
        }
        if (!only(decoded.sidecars(), lane)) {
            throw new IllegalStateException("final-carrier durable payload lane mismatch");
        }
        return decoded.sidecars();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED,
            noRollbackFor = FinalCarrierLaneConflict.class)
    public FinalCarrierTickScheduler.Admission admitAndAcknowledge(
            FinalCarrierTickScheduler.CarrierReceipt receipt,
            List<FinalCarrierTickScheduler.ScheduledTick> candidates,
            long admissionBaseMcTick, int capacity) {
        try {
            return admitAndAcknowledgeInternal(receipt, candidates, admissionBaseMcTick, capacity);
        } catch (FinalCarrierLaneConflict | FinalCarrierLaneRetryable
                | FinalCarrierDurableStateException expected) {
            throw expected;
        } catch (DataAccessException | PersistenceException | TransactionException infrastructure) {
            throw new FinalCarrierLaneRetryable(
                    "final-carrier admission storage failure is retryable", infrastructure);
        }
    }

    private FinalCarrierTickScheduler.Admission admitAndAcknowledgeInternal(
            FinalCarrierTickScheduler.CarrierReceipt receipt,
            List<FinalCarrierTickScheduler.ScheduledTick> candidates,
            long admissionBaseMcTick, int capacity) {
        Objects.requireNonNull(receipt, "carrier receipt");
        Objects.requireNonNull(candidates, "tick candidates");
        requireAdmissionInput(receipt, candidates, admissionBaseMcTick, capacity);
        requireWorldLock(receipt.worldId(), "scheduled tick world is missing");
        probeIncompleteLegacyRows(receipt.worldId());
        CanonicalWorldgenStore.Lane lane = tickCanonicalLane(receipt.lane());
        CanonicalWorldgenStore.LaneReceipt claim = canonical.claim(
                receipt.worldId(), receipt.chunkX(), receipt.chunkZ(), lane);
        if (claim == null) {
            return replayAcknowledgedAdmission(receipt, lane);
        }

        NeutralFinalChunk source;
        try {
            source = validateClaim(claim, receipt.chunkX(), receipt.chunkZ(),
                    receipt.sourceFingerprint());
            requireExactTickAdmission(source, receipt, candidates, admissionBaseMcTick);
        } catch (FinalCarrierDurableStateException invalidDurable) {
            throw invalidDurable;
        } catch (RuntimeException invalidAuthority) {
            canonical.reject(receipt.worldId(), receipt.chunkX(), receipt.chunkZ(), lane,
                    claim.receipt());
            throw new FinalCarrierLaneConflict(
                    "canonical final-carrier tick authority conflict: " + lane + ": "
                            + invalidAuthority.getMessage(), invalidAuthority);
        }

        byte[] typedPayload = tickPayload(receipt, admissionBaseMcTick, candidates);
        FinalCarrierLaneMutation mutation = lanes
                .findByWorldIdAndChunkXAndChunkZAndLaneAndSourceFingerprint(receipt.worldId(),
                        receipt.chunkX(), receipt.chunkZ(), lane.name(), receipt.sourceFingerprint())
                .orElse(null);
        if (mutation != null) {
            validateTickMutationRow(mutation, receipt.worldId(), receipt.chunkX(), receipt.chunkZ(),
                    lane.name());
            if (!mutation.matches(receipt.lanePayloadFingerprint(), typedPayload)) {
                canonical.reject(receipt.worldId(), receipt.chunkX(), receipt.chunkZ(), lane,
                        claim.receipt());
                throw new FinalCarrierLaneConflict(
                        "durable final-carrier tick lane fingerprint conflict: " + lane);
            }
            if (mutation.getActivationStatus()
                    == FinalCarrierLaneMutation.ActivationStatus.REJECTED) {
                canonical.reject(receipt.worldId(), receipt.chunkX(), receipt.chunkZ(), lane,
                        claim.receipt());
                throw new FinalCarrierLaneConflict(
                        "durable final-carrier tick lane is terminally rejected: " + lane);
            }
        }

        TickLedger ledger = readTickLedger(receipt, candidates.size());
        validatePresentTickLedger(receipt, candidates, ledger);
        if (!ledger.consumedRows().isEmpty()) {
            throw durableState(
                    "non-terminal admission already contains consumed candidate rows", null);
        }
        Set<FinalCarrierTickScheduler.TickKey> accounted = new HashSet<>();
        ledger.scheduledRows().forEach(row -> accounted.add(durableScheduledTick(row).key()));
        ledger.consumedRows().forEach(row -> accounted.add(durableConsumedTick(row).key()));
        List<FinalCarrierTickScheduler.ScheduledTick> missing = candidates.stream()
                .filter(candidate -> !accounted.contains(candidate.key())).toList();

        long existingCount = scheduled.countByWorldIdAndLane(receipt.worldId(),
                receipt.lane().name());
        if (existingCount < 0L) {
            throw durableState("scheduled tick count is negative", null);
        }
        if (existingCount > (long) capacity
                || missing.size() > (long) capacity - existingCount) {
            // Capacity is transient backpressure. Leave the canonical claim open for retry.
            return new FinalCarrierTickScheduler.Admission(
                    FinalCarrierTickScheduler.AdmissionStatus.CAPACITY_REJECTED, List.of(), List.of());
        }

        long nextOrder = nextDurableOrder(receipt, ledger, capacity);
        List<FinalCarrierScheduledTick> additions = new ArrayList<>(missing.size());
        for (FinalCarrierTickScheduler.ScheduledTick candidate : missing) {
            additions.add(new FinalCarrierScheduledTick(candidate, nextOrder));
            nextOrder = positiveNextOrder(nextOrder);
        }
        if (mutation == null) {
            mutation = new FinalCarrierLaneMutation(receipt.worldId(), receipt.chunkX(),
                    receipt.chunkZ(), lane.name(), receipt.sourceFingerprint(),
                    receipt.lanePayloadFingerprint(), typedPayload);
        }
        if (mutation.getActivationStatus() == FinalCarrierLaneMutation.ActivationStatus.PENDING) {
            mutation.markInstalled();
            lanes.saveAndFlush(mutation);
        } else if (mutation.getActivationStatus()
                != FinalCarrierLaneMutation.ActivationStatus.INSTALLED) {
            throw durableState("durable tick lane has an invalid activation state", null);
        }

        List<FinalCarrierScheduledTick> saved = additions.isEmpty()
                ? List.of() : scheduled.saveAllAndFlush(additions);
        if (saved.size() != additions.size()) {
            throw durableState("scheduled tick save omitted an admission candidate", null);
        }
        ArrayList<FinalCarrierScheduledTick> postSaveScheduled = new ArrayList<>(
                ledger.scheduledRows().size() + saved.size());
        postSaveScheduled.addAll(ledger.scheduledRows());
        postSaveScheduled.addAll(saved);
        TickLedger postSaveLedger = new TickLedger(postSaveScheduled, ledger.consumedRows());
        validatePresentTickLedger(receipt, candidates, postSaveLedger);
        Set<FinalCarrierTickScheduler.TickKey> postSaveKeys = new HashSet<>();
        postSaveKeys.addAll(postSaveScheduled.stream().map(FinalCarrierPersistenceService::durableScheduledTick)
                .map(FinalCarrierTickScheduler.ScheduledTick::key).toList());
        if (postSaveKeys.size() != candidates.size()) {
            throw durableState("scheduled tick save omitted an admission candidate", null);
        }
        List<FinalCarrierTickScheduler.ScheduledTick> durable = new ArrayList<>(
                postSaveScheduled.stream().map(FinalCarrierPersistenceService::durableScheduledTick)
                        .toList());
        durable.sort(Comparator.comparingLong(FinalCarrierTickScheduler.ScheduledTick::durableOrder));
        canonical.acknowledge(receipt.worldId(), receipt.chunkX(), receipt.chunkZ(), lane,
                claim.receipt());
        return new FinalCarrierTickScheduler.Admission(
                FinalCarrierTickScheduler.AdmissionStatus.ADMITTED, durable, List.of());
    }

    /**
     * 어떤 필드가 어긋났는지 남겨야 다음 진단에서 원인을 좁힐 수 있다. 지문은 앞 12자만 남긴다.
     */
    private static String receiptConflictDetail(
            FinalCarrierTickScheduler.CarrierReceipt presented,
            FinalCarrierTickScheduler.CarrierReceipt canonical) {
        StringBuilder detail = new StringBuilder();
        if (presented.lane() != canonical.lane()) {
            detail.append(" lane=").append(presented.lane())
                    .append("/").append(canonical.lane());
        }
        if (presented.chunkX() != canonical.chunkX() || presented.chunkZ() != canonical.chunkZ()) {
            detail.append(" chunk=").append(presented.chunkX()).append(",")
                    .append(presented.chunkZ()).append("/").append(canonical.chunkX())
                    .append(",").append(canonical.chunkZ());
        }
        if (!presented.sourceFingerprint().equals(canonical.sourceFingerprint())) {
            detail.append(" source=").append(shortFingerprint(presented.sourceFingerprint()))
                    .append("/").append(shortFingerprint(canonical.sourceFingerprint()));
        }
        if (!presented.lanePayloadFingerprint().equals(canonical.lanePayloadFingerprint())) {
            detail.append(" lanePayload=")
                    .append(shortFingerprint(presented.lanePayloadFingerprint()))
                    .append("/").append(shortFingerprint(canonical.lanePayloadFingerprint()));
        }
        return detail.isEmpty() ? "" : ":" + detail;
    }

    private static String shortFingerprint(String fingerprint) {
        return fingerprint.length() <= 12 ? fingerprint : fingerprint.substring(0, 12);
    }

    private FinalCarrierTickScheduler.Admission replayAcknowledgedAdmission(
            FinalCarrierTickScheduler.CarrierReceipt receipt, CanonicalWorldgenStore.Lane lane) {
        try {
            CanonicalWorldgenStore.CanonicalChunkSnapshot snapshot = canonical.find(
                    receipt.worldId(), receipt.chunkX(), receipt.chunkZ());
            if (snapshot == null) {
                throw new FinalCarrierLaneConflict("canonical tick lane source is missing");
            }
            if ((snapshot.laneRejectedMask() & lane.mask()) != 0) {
                throw new FinalCarrierLaneConflict(
                        "canonical tick lane is terminally rejected: " + lane);
            }
            if ((snapshot.laneAckMask() & lane.mask()) == 0) {
                throw new FinalCarrierLaneConflict(
                        "canonical tick lane has no claimable receipt: " + lane);
            }
            NeutralFinalChunk source = snapshot.commit().semanticFinalChunk();
            if (source == null || source.chunkX() != receipt.chunkX()
                    || source.chunkZ() != receipt.chunkZ()) {
                throw durableState("acknowledged canonical tick lane coordinate is malformed", null);
            }
            try {
                requireSource(source, receipt.sourceFingerprint());
            } catch (RuntimeException staleReceipt) {
                throw new FinalCarrierLaneConflict(
                        "acknowledged canonical tick lane source fingerprint conflict: " + lane,
                        staleReceipt);
            }
            FinalCarrierTickScheduler.CarrierReceipt canonicalReceipt =
                    canonicalTickReceipt(receipt, source);
            if (!canonicalReceipt.equals(receipt)) {
                throw new FinalCarrierLaneConflict(
                        "acknowledged canonical tick lane receipt conflict"
                                + receiptConflictDetail(receipt, canonicalReceipt), null);
            }
            FinalCarrierLaneMutation mutation = lanes
                    .findByWorldIdAndChunkXAndChunkZAndLaneAndSourceFingerprint(
                            receipt.worldId(), receipt.chunkX(), receipt.chunkZ(), lane.name(),
                            receipt.sourceFingerprint())
                    .orElseThrow(() -> durableState(
                            "acknowledged canonical tick lane has no durable mutation", null));
            validateTickMutationRow(mutation, receipt.worldId(), receipt.chunkX(), receipt.chunkZ(),
                    lane.name());
            if (mutation.getActivationStatus()
                    != FinalCarrierLaneMutation.ActivationStatus.INSTALLED) {
                throw durableState("acknowledged tick lane mutation is not INSTALLED", null);
            }
            long admissionBaseMcTick = tickAdmissionBase(mutation, canonicalReceipt);
            List<FinalCarrierTickScheduler.ScheduledTick> exact = canonicalTickCandidates(
                    source, canonicalReceipt, admissionBaseMcTick);
            if (!mutation.matches(receipt.lanePayloadFingerprint(),
                    tickPayload(canonicalReceipt, admissionBaseMcTick, exact))) {
                throw durableState("acknowledged tick lane mutation payload mismatch", null);
            }
            TickLedger ledger = readTickLedger(receipt, exact.size());
            authenticateDurableTickSubset(receipt, exact, ledger.scheduledRows(),
                    ledger.consumedRows());
            validatePublicationRows(ledger.consumedRows());
            return new FinalCarrierTickScheduler.Admission(
                    FinalCarrierTickScheduler.AdmissionStatus.ALREADY_ACKNOWLEDGED,
                    ledger.scheduledRows().stream().map(FinalCarrierPersistenceService::durableScheduledTick)
                            .toList(),
                    ledger.consumedRows().stream().map(FinalCarrierPersistenceService::durableConsumedTick)
                            .toList());
        } catch (FinalCarrierDurableStateException invalid) {
            throw invalid;
        } catch (FinalCarrierLaneConflict conflict) {
            throw conflict;
        } catch (DataAccessException | PersistenceException | TransactionException infrastructure) {
            throw infrastructure;
        } catch (RuntimeException invalidDurableIdentity) {
            throw durableState(
                    "acknowledged canonical tick lane durable state is malformed: " + lane,
                    invalidDurableIdentity);
        }
    }

    private static void requireAdmissionInput(FinalCarrierTickScheduler.CarrierReceipt receipt,
            List<FinalCarrierTickScheduler.ScheduledTick> candidates,
            long admissionBaseMcTick, int capacity) {
        if (admissionBaseMcTick < 0L) {
            throw new FinalCarrierLaneConflict("negative final-carrier tick admission base");
        }
        if (capacity < 1 || capacity > FinalCarrierTickScheduler.MAX_PENDING_TICKS) {
            throw new FinalCarrierLaneConflict("final-carrier capacity is outside bounds");
        }
        if (candidates.size() > FinalCarrierTickScheduler.MAX_PENDING_TICKS) {
            throw new FinalCarrierLaneConflict("final-carrier candidate set exceeds bounded capacity");
        }
        Set<FinalCarrierTickScheduler.TickKey> keys = new HashSet<>();
        for (FinalCarrierTickScheduler.ScheduledTick candidate : candidates) {
            if (candidate == null || candidate.durableOrder() != -1L
                    || !candidate.receipt().equals(receipt) || !keys.add(candidate.key())) {
                throw new FinalCarrierLaneConflict(
                        "final-carrier admission contains an invalid or duplicate candidate");
            }
        }
    }

    private record TickLedger(List<FinalCarrierScheduledTick> scheduledRows,
            List<FinalCarrierConsumedTick> consumedRows) { }

    /** Reads one current-hash partition with the authenticated candidate bound plus one sentinel. */
    private TickLedger readTickLedger(FinalCarrierTickScheduler.CarrierReceipt receipt,
            int candidateCount) {
        probeIncompleteLegacyRows(receipt.worldId());
        Pageable limit = PageRequest.of(0, boundedRowLimit(candidateCount));
        List<FinalCarrierScheduledTick> scheduledRows = scheduled
                .findAllByWorldIdAndChunkXAndChunkZAndLaneAndSourceFingerprintOrderByDurableOrderAscIdAsc(
                        receipt.worldId(), receipt.chunkX(), receipt.chunkZ(),
                        receipt.lane().name(), receipt.sourceFingerprint(), limit);
        List<FinalCarrierConsumedTick> consumedRows = consumed
                .findAllByWorldIdAndChunkXAndChunkZAndLaneAndSourceFingerprintAndPublicationStateInOrderByDurableOrderAscIdAsc(
                        receipt.worldId(), receipt.chunkX(), receipt.chunkZ(), receipt.lane().name(),
                        receipt.sourceFingerprint(), ALL_PUBLICATION_STATES, limit);
        if (scheduledRows.size() > candidateCount || consumedRows.size() > candidateCount) {
            throw durableState("durable tick partition exceeds canonical candidate count", null);
        }
        return new TickLedger(List.copyOf(scheduledRows), List.copyOf(consumedRows));
    }

    private void validatePresentTickLedger(FinalCarrierTickScheduler.CarrierReceipt receipt,
            List<FinalCarrierTickScheduler.ScheduledTick> candidates, TickLedger ledger) {
        Map<FinalCarrierTickScheduler.TickKey, FinalCarrierTickScheduler.ScheduledTick> expected =
                new HashMap<>();
        for (FinalCarrierTickScheduler.ScheduledTick candidate : candidates) {
            if (expected.put(candidate.key(), candidate) != null) {
                throw durableState("canonical first-winner set contains duplicate keys", null);
            }
        }
        Set<FinalCarrierTickScheduler.TickKey> present = new HashSet<>();
        Set<Long> durableOrders = new HashSet<>();
        for (FinalCarrierScheduledTick row : ledger.scheduledRows()) {
            FinalCarrierTickScheduler.ScheduledTick durable = durableScheduledTick(row);
            FinalCarrierTickScheduler.ScheduledTick candidate = expected.get(durable.key());
            if (candidate == null || !present.add(durable.key()) || !durableOrders.add(
                    durable.durableOrder()) || !matchesExactScheduledCandidate(row, candidate)) {
                throw durableState("durable scheduled tick differs from canonical admission", null);
            }
        }
        Set<FinalCarrierTickScheduler.TickKey> consumedKeys = new HashSet<>();
        for (FinalCarrierConsumedTick row : ledger.consumedRows()) {
            FinalCarrierTickScheduler.ScheduledTick durable = durableConsumedTick(row);
            FinalCarrierTickScheduler.ScheduledTick candidate = expected.get(durable.key());
            if (candidate == null || !consumedKeys.add(durable.key())
                    || !durableOrders.add(durable.durableOrder())
                    || !row.matchesCandidate(candidate)) {
                throw durableState("consumed tick differs from canonical admission", null);
            }
            if (present.contains(durable.key())) {
                throw durableState("scheduled and consumed tick identities overlap", null);
            }
            validatePublicationRow(row);
        }
        for (FinalCarrierTickScheduler.TickKey key : present) {
            if (consumedKeys.contains(key)) {
                throw durableState("scheduled and consumed tick identities overlap", null);
            }
        }
        if (!receipt.equals(candidates.isEmpty() ? receipt : candidates.getFirst().receipt())) {
            throw durableState("admission candidate receipt drift", null);
        }
    }

    private long nextDurableOrder(FinalCarrierTickScheduler.CarrierReceipt receipt,
            TickLedger ledger, int capacity) {
        probeIncompleteLegacyRows(receipt.worldId());
        long maximum = 0L;
        FinalCarrierScheduledTick latestScheduled = scheduled
                .findFirstByWorldIdAndLaneOrderByDurableOrderDescIdDesc(
                        receipt.worldId(), receipt.lane().name()).orElse(null);
        if (latestScheduled != null) {
            maximum = Math.max(maximum, durableScheduledTick(latestScheduled).durableOrder());
        }
        FinalCarrierConsumedTick latestConsumed = consumed
                .findFirstByWorldIdAndLaneAndPublicationStateInOrderByDurableOrderDescIdDesc(
                        receipt.worldId(), receipt.lane().name(), ALL_PUBLICATION_STATES)
                .orElse(null);
        if (latestConsumed != null) {
            maximum = Math.max(maximum, durableConsumedTick(latestConsumed).durableOrder());
        }
        for (FinalCarrierConsumedTick row : ledger.consumedRows()) {
            maximum = Math.max(maximum, durableConsumedTick(row).durableOrder());
        }
        return positiveNextOrder(maximum);
    }

    private static long positiveNextOrder(long current) {
        if (current < 0L || current == Long.MAX_VALUE) {
            throw durableState("durable admission order overflow", null);
        }
        return current + 1L;
    }

    /**
     * A writer can consume a scheduled row between two separate recovery calls. Pin both reads
     * to one repeatable database snapshot so that row cannot appear in both queue and outbox.
     * The public proxy owns this transaction; internal calls intentionally join it.
     */
    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public com.gameexpert.engine.persistence.tick.FinalCarrierTickRecoverySnapshot loadRecovery(
            long worldId, int limit) {
        return new com.gameexpert.engine.persistence.tick.FinalCarrierTickRecoverySnapshot(
                loadWorld(worldId), loadDurablePublications(worldId, limit));
    }

    @Override
    @Transactional
    public List<FinalCarrierTickScheduler.ScheduledTick> loadWorld(long worldId) {
        try {
            return loadTickRecovery(worldId, null, null);
        } catch (FinalCarrierLaneRetryable | FinalCarrierDurableStateException expected) {
            throw expected;
        } catch (DataAccessException | PersistenceException | TransactionException infrastructure) {
            throw new FinalCarrierLaneRetryable(
                    "final-carrier world tick load storage failure is retryable", infrastructure);
        }
    }

    @Override
    @Transactional
    public List<FinalCarrierTickScheduler.ScheduledTick> loadChunk(
            long worldId, int chunkX, int chunkZ) {
        try {
            return loadTickRecovery(worldId, chunkX, chunkZ);
        } catch (FinalCarrierLaneRetryable | FinalCarrierDurableStateException expected) {
            throw expected;
        } catch (DataAccessException | PersistenceException | TransactionException infrastructure) {
            throw new FinalCarrierLaneRetryable(
                    "final-carrier chunk tick load storage failure is retryable", infrastructure);
        }
    }

    @Override
    @Transactional
    public FinalCarrierTickScheduler.Settlement settleAtomically(
            FinalCarrierTickScheduler.ScheduledTick tick,
            FinalCarrierTickScheduler.DueDisposition disposition,
            FinalCarrierTickScheduler.TickMutation mutation) {
        return settleAtomicallyWithPlan(tick, disposition, mutation).status();
    }

    @Override
    @Transactional
    public FinalCarrierTickScheduler.SettlementResult settleAtomicallyWithPlan(
            FinalCarrierTickScheduler.ScheduledTick tick,
            FinalCarrierTickScheduler.DueDisposition disposition,
            FinalCarrierTickScheduler.TickMutation mutation) {
        try {
            return settleAtomicallyWithPlanInternal(tick, disposition, mutation);
        } catch (FinalCarrierLaneRetryable | FinalCarrierDurableStateException expected) {
            throw expected;
        } catch (DataAccessException | PersistenceException | TransactionException infrastructure) {
            throw new FinalCarrierLaneRetryable(
                    "final-carrier settlement storage failure is retryable", infrastructure);
        }
    }

    private FinalCarrierTickScheduler.SettlementResult settleAtomicallyWithPlanInternal(
            FinalCarrierTickScheduler.ScheduledTick tick,
            FinalCarrierTickScheduler.DueDisposition disposition,
            FinalCarrierTickScheduler.TickMutation mutation) {
        Objects.requireNonNull(tick, "scheduled tick");
        Objects.requireNonNull(disposition, "tick disposition");
        Objects.requireNonNull(mutation, "tick mutation");
        if (tick.durableOrder() <= 0L) {
            throw durableState("settlement requires a durable admission order", null);
        }
        requireSettlementWorldLock(tick.receipt().worldId(), "settlement world is missing");
        probeIncompleteLegacyRows(tick.receipt().worldId());
        authenticateSettlementTick(tick);
        FinalCarrierConsumedTick settled = lockedConsumedTick(tick);
        if (settled != null) {
            FinalCarrierTickPublication stored = durablePublicationValue(settled);
            if (!settled.matchesExact(tick, disposition)) {
                throw durableState("consumed tick conflicts with exact settlement identity", null);
            }
            if (!stored.mutation().equals(mutation)) {
                throw durableState("consumed tick conflicts with exact settlement mutation", null);
            }
            if (settled.getState() == FinalCarrierConsumedTick.PublicationState.REJECTED) {
                throw durableState("rejected settlement cannot be replayed", null);
            }
            return new FinalCarrierTickScheduler.SettlementResult(
                    FinalCarrierTickScheduler.Settlement.ALREADY_COMMITTED, stored.mutation(),
                    settled.getPublicationKey(), settled.getMutationDigest(),
                    settled.getPublicationBody());
        }
        FinalCarrierScheduledTick row = scheduled
                .findLockedByWorldIdAndChunkXAndChunkZAndLaneAndXAndYAndZAndTypeKeyAndSourceFingerprint(
                        tick.receipt().worldId(), tick.receipt().chunkX(), tick.receipt().chunkZ(),
                        tick.lane().name(), tick.x(), tick.y(), tick.z(), tick.typeKey(),
                        tick.receipt().sourceFingerprint())
                .orElse(null);
        if (row == null) {
            return settlementResult(FinalCarrierTickScheduler.Settlement.RETRY, tick, disposition,
                    FinalCarrierTickScheduler.TickMutation.NONE);
        }
        FinalCarrierTickScheduler.ScheduledTick durable = durableScheduledTick(row);
        if (!durable.equals(tick)) {
            throw durableState("scheduled tick differs from exact settlement identity", null);
        }
        FinalCarrierTickPublication planned = new FinalCarrierTickPublication(tick, disposition,
                mutation, FinalCarrierConsumedTick.PublicationState.UNACKNOWLEDGED);
        applyWorldMutations(tick.receipt().worldId(), planned.mutation());
        for (var falling : com.gameexpert.engine.persistence.tick.SpeleothemFallIntents.from(tick, mutation)) {
            if (tickRecoveryEntityManager == null) throw durableState("falling entity persistence is unavailable", null);
            tickRecoveryEntityManager.persist(new com.gameexpert.falling.entity.WorldFallingSpeleothem(
                    worlds.getReferenceById(tick.receipt().worldId()), falling));
        }
        FinalCarrierConsumedTick consumedRow = new FinalCarrierConsumedTick(planned,
                TICK_PUBLICATION_LIMITS);
        consumed.saveAndFlush(consumedRow);
        scheduled.delete(row);
        scheduled.flush();
        return settlementResult(FinalCarrierTickScheduler.Settlement.COMMITTED, tick, disposition,
                planned.mutation());
    }

    /** Re-authenticates a runtime tick against the current canonical hash before any mutation. */
    private void authenticateSettlementTick(FinalCarrierTickScheduler.ScheduledTick tick) {
        try {
            CanonicalWorldgenStore.Lane lane = tickCanonicalLane(tick.lane());
            CanonicalWorldgenStore.CanonicalChunkSnapshot snapshot = canonical.find(
                    tick.receipt().worldId(), tick.receipt().chunkX(), tick.receipt().chunkZ());
            if (snapshot == null) {
                throw durableState("settlement has no current canonical snapshot", null);
            }
            if ((snapshot.laneAckMask() & lane.mask()) == 0) {
                throw durableState("settlement lane is not acknowledged", null);
            }
            if ((snapshot.laneRejectedMask() & lane.mask()) != 0) {
                throw durableState("settlement lane is rejected", null);
            }
            NeutralFinalChunk source = snapshot.commit().semanticFinalChunk();
            if (source == null || source.chunkX() != tick.receipt().chunkX()
                    || source.chunkZ() != tick.receipt().chunkZ()) {
                throw durableState("settlement canonical snapshot coordinate mismatch", null);
            }
            requireSource(source, tick.receipt().sourceFingerprint());
            FinalCarrierTickScheduler.CarrierReceipt canonicalReceipt = canonicalTickReceipt(
                    tick.receipt(), source);
            if (!canonicalReceipt.equals(tick.receipt())) {
                throw durableState("settlement receipt is not the current canonical hash", null);
            }
            FinalCarrierLaneMutation authority = lanes
                    .findByWorldIdAndChunkXAndChunkZAndLaneAndSourceFingerprint(
                            tick.receipt().worldId(), tick.receipt().chunkX(),
                            tick.receipt().chunkZ(), lane.name(),
                            tick.receipt().sourceFingerprint())
                    .orElseThrow(() -> durableState(
                            "settlement has no durable lane mutation", null));
            validateTickMutationRow(authority, tick.receipt().worldId(), tick.receipt().chunkX(),
                    tick.receipt().chunkZ(), lane.name());
            if (authority.getActivationStatus()
                    != FinalCarrierLaneMutation.ActivationStatus.INSTALLED) {
                throw durableState("settlement lane mutation is not INSTALLED", null);
            }
            long admissionBaseMcTick = tickAdmissionBase(authority, canonicalReceipt);
            List<FinalCarrierTickScheduler.ScheduledTick> exact = canonicalTickCandidates(
                    source, canonicalReceipt, admissionBaseMcTick);
            if (!authority.matches(canonicalReceipt.lanePayloadFingerprint(),
                    tickPayload(canonicalReceipt, admissionBaseMcTick, exact))) {
                throw durableState("settlement lane mutation payload mismatch", null);
            }
            if (exact.stream().noneMatch(candidate -> sameCandidateIdentity(candidate, tick))) {
                throw durableState("settlement tick is not a canonical first winner", null);
            }
        } catch (FinalCarrierDurableStateException invalid) {
            throw invalid;
        } catch (DataAccessException | PersistenceException | TransactionException infrastructure) {
            throw infrastructure;
        } catch (RuntimeException invalidAuthority) {
            throw durableState("settlement canonical authority is malformed", invalidAuthority);
        }
    }

    private void applyWorldMutations(long worldId, FinalCarrierTickScheduler.TickMutation mutation) {
        Objects.requireNonNull(mutation, "tick mutation");
        if (mutation.blocks().isEmpty() && mutation.drops().isEmpty()) return;
        var world = worlds.findById(worldId)
                .orElseThrow(() -> new FinalCarrierLaneRetryable(
                        "settlement world disappeared during world lock"));
        try {
            for (FinalCarrierTickScheduler.BlockMutation block : mutation.blocks()) {
                WorldBlockDiff diff = diffs.findByWorldIdAndXAndYAndZ(
                        worldId, block.x(), block.y(), block.z()).orElse(null);
                if (diff == null) {
                    diffs.save(new WorldBlockDiff(world, block.x(), block.y(), block.z(),
                            (short) block.blockId(), (short) block.blockState()));
                    continue;
                }
                boolean alreadyApplied = diff.getBlockType() == (short) block.blockId()
                        && diff.getBlockState() == (short) block.blockState();
                if (alreadyApplied) continue;
                if (block.hasBeforeState()
                        && (diff.getBlockType() != (short) block.beforeBlockId()
                        || diff.getBlockState() != (short) block.beforeBlockState())) {
                    throw durableState("settlement block before-state conflicts with durable diff",
                            null);
                }
                diff.replace((short) block.blockId(), (short) block.blockState(), null);
                diffs.save(diff);
            }
            for (GroundItemSnapshot drop : mutation.drops()) {
                groundEntities.insertStableItemJoiningTransaction(worldId, drop);
            }
        } catch (FinalCarrierDurableStateException invalid) {
            throw invalid;
        } catch (FinalCarrierLaneRetryable | DataAccessException | PersistenceException
                | TransactionException retryable) {
            throw retryable;
        } catch (RuntimeException invalidMutation) {
            throw durableState("settlement ground or block mutation is malformed",
                    invalidMutation);
        }
    }

    private record TickPartitionCoordinate(long worldId, int chunkX, int chunkZ,
            FinalCarrierTickScheduler.Lane lane) { }

    /** A tick partition is source-specific; old-source rows must never join current rows. */
    private record TickPartitionKey(long worldId, int chunkX, int chunkZ,
            FinalCarrierTickScheduler.Lane lane, String sourceFingerprint) { }

    private static TickPartitionKey partitionKey(
            FinalCarrierTickScheduler.ScheduledTick tick) {
        return new TickPartitionKey(tick.receipt().worldId(), tick.receipt().chunkX(),
                tick.receipt().chunkZ(), tick.lane(), tick.receipt().sourceFingerprint());
    }

    private static TickPartitionCoordinate partitionCoordinate(TickPartitionKey key) {
        return new TickPartitionCoordinate(key.worldId(), key.chunkX(), key.chunkZ(), key.lane());
    }

    private static TickPartitionKey mutationPartitionKey(long worldId,
            FinalCarrierLaneMutation mutation) {
        if (mutation.getWorldId() != worldId || mutation.getLane() == null
                || mutation.getSourceFingerprint() == null
                || !mutation.getSourceFingerprint().matches("[0-9a-f]{64}")) {
            throw durableState("tick lane mutation identity is malformed", null);
        }
        FinalCarrierTickScheduler.Lane lane;
        try {
            lane = switch (CanonicalWorldgenStore.Lane.valueOf(mutation.getLane())) {
                case BLOCK_TICKS -> FinalCarrierTickScheduler.Lane.BLOCK;
                case FLUID_TICKS -> FinalCarrierTickScheduler.Lane.FLUID;
                default -> throw durableState("non-tick lane reached tick recovery", null);
            };
        } catch (FinalCarrierDurableStateException invalid) {
            throw invalid;
        } catch (RuntimeException invalidLane) {
            throw durableState("tick lane mutation has an invalid lane", invalidLane);
        }
        return new TickPartitionKey(worldId, mutation.getChunkX(), mutation.getChunkZ(), lane,
                mutation.getSourceFingerprint());
    }

    private static void requireRequestedChunk(FinalCarrierTickScheduler.ScheduledTick tick,
            long worldId, int chunkX, int chunkZ) {
        if (tick.receipt().worldId() != worldId || tick.receipt().chunkX() != chunkX
                || tick.receipt().chunkZ() != chunkZ
                || Math.floorDiv(tick.x(), Blocks.CHUNK_X) != chunkX
                || Math.floorDiv(tick.z(), Blocks.CHUNK_Z) != chunkZ) {
            throw durableState("tick recovery returned a foreign requested coordinate", null);
        }
    }

    private List<FinalCarrierTickScheduler.ScheduledTick> loadTickRecovery(long worldId,
            Integer requestedChunkX, Integer requestedChunkZ) {
        requireWorldLock(worldId, "scheduled tick world is missing");
        if (tickRecoveryEntityManager == null) {
            return readLockedTickRecovery(worldId, requestedChunkX, requestedChunkZ);
        }
        // Recovery performs no writes. Keep the write-capable transaction and pessimistic lock,
        // but avoid Hibernate traversing the growing managed carrier graph before every SELECT.
        // Flush joined caller changes once so recovery still observes them, and restore the
        // caller's policy even when an authenticated durable-state check rejects the restore.
        FlushModeType previous = tickRecoveryEntityManager.getFlushMode();
        tickRecoveryEntityManager.flush();
        try {
            tickRecoveryEntityManager.setFlushMode(FlushModeType.COMMIT);
            return readLockedTickRecovery(worldId, requestedChunkX, requestedChunkZ);
        } finally {
            tickRecoveryEntityManager.setFlushMode(previous);
        }
    }

    private List<FinalCarrierTickScheduler.ScheduledTick> readLockedTickRecovery(long worldId,
            Integer requestedChunkX, Integer requestedChunkZ) {
        probeIncompleteLegacyRows(worldId);
        Pageable discoveryLimit = PageRequest.of(0,
                boundedRowLimit(FinalCarrierTickScheduler.MAX_PENDING_TICKS));
        Set<TickPartitionCoordinate> coordinates = new HashSet<>();
        Set<TickPartitionKey> discovered = new HashSet<>();
        for (FinalCarrierTickScheduler.Lane lane : FinalCarrierTickScheduler.Lane.values()) {
            String laneName = lane.name();
            List<FinalCarrierScheduledTick> scheduledRows = requestedChunkX == null
                    ? scheduled.findAllByWorldIdAndLaneOrderByDurableOrderAscIdAsc(
                            worldId, laneName, discoveryLimit)
                    : scheduled.findAllByWorldIdAndChunkXAndChunkZAndLaneOrderByDurableOrderAscIdAsc(
                            worldId, requestedChunkX, requestedChunkZ, laneName, discoveryLimit);
            List<FinalCarrierConsumedTick> consumedRows = requestedChunkX == null
                    ? consumed.findAllByWorldIdAndLaneAndPublicationStateInOrderByDurableOrderAscIdAsc(
                            worldId, laneName, RECOVERY_STATES, discoveryLimit)
                    : consumed.findAllByWorldIdAndChunkXAndChunkZAndLaneAndPublicationStateInOrderByDurableOrderAscIdAsc(
                            worldId, requestedChunkX, requestedChunkZ, laneName,
                            RECOVERY_STATES, discoveryLimit);
            if (scheduledRows.size() > FinalCarrierTickScheduler.MAX_PENDING_TICKS
                    || consumedRows.size() > FinalCarrierTickScheduler.MAX_PENDING_TICKS) {
                throw durableState("tick recovery discovery exceeds bounded history", null);
            }
            for (FinalCarrierScheduledTick row : scheduledRows) {
                FinalCarrierTickScheduler.ScheduledTick tick = durableScheduledTick(row);
                if (requestedChunkX == null) {
                    if (tick.receipt().worldId() != worldId) {
                        throw durableState("scheduled tick belongs to another world", null);
                    }
                } else {
                    requireRequestedChunk(tick, worldId, requestedChunkX, requestedChunkZ);
                }
                TickPartitionKey key = partitionKey(tick);
                discovered.add(key);
                coordinates.add(partitionCoordinate(key));
            }
            for (FinalCarrierConsumedTick row : consumedRows) {
                if (!RECOVERY_STATES.contains(row.getState())) continue;
                FinalCarrierTickScheduler.ScheduledTick tick = durableConsumedTick(row);
                validatePublicationRow(row);
                if (requestedChunkX == null) {
                    if (tick.receipt().worldId() != worldId) {
                        throw durableState("consumed tick belongs to another world", null);
                    }
                } else {
                    requireRequestedChunk(tick, worldId, requestedChunkX, requestedChunkZ);
                }
                TickPartitionKey key = partitionKey(tick);
                discovered.add(key);
                coordinates.add(partitionCoordinate(key));
            }
        }
        for (String laneName : List.of(CanonicalWorldgenStore.Lane.BLOCK_TICKS.name(),
                CanonicalWorldgenStore.Lane.FLUID_TICKS.name())) {
            forEachHistoryPage(worldId,
                    (after, page) -> requestedChunkX == null
                            ? lanes.findAllByWorldIdAndLaneAndIdGreaterThanOrderById(worldId, laneName, after, page)
                            : lanes.findAllByWorldIdAndChunkXAndChunkZAndLaneAndIdGreaterThanOrderById(
                                    worldId, requestedChunkX, requestedChunkZ, laneName, after, page),
                    mutations -> mutations.forEach(mutation -> {
                        if (!laneName.equals(mutation.getLane())) throw durableState("tick history query returned a foreign lane", null);
                        TickPartitionKey key = mutationPartitionKey(worldId, mutation);
                        if (requestedChunkX != null) requireHistoryCoordinate(mutation, requestedChunkX, requestedChunkZ);
                        discovered.add(key);
                        coordinates.add(partitionCoordinate(key));
                    }), false);
        }

        ArrayList<FinalCarrierTickScheduler.ScheduledTick> result = new ArrayList<>();
        // Keep only the current coordinate's carrier while validating discovered partitions.
        Map<Long, CanonicalWorldgenStore.CanonicalChunkSnapshot> snapshots = new HashMap<>();
        Comparator<TickPartitionCoordinate> coordinateOrder =
                Comparator.comparingLong(TickPartitionCoordinate::worldId)
                        .thenComparingInt(TickPartitionCoordinate::chunkX)
                        .thenComparingInt(TickPartitionCoordinate::chunkZ)
                        .thenComparing(TickPartitionCoordinate::lane);
        for (TickPartitionCoordinate coordinate : coordinates.stream().sorted(coordinateOrder)
                .toList()) {
            snapshots.clear();
            TickPartitionKey current = currentTickPartitionKey(coordinate, snapshots);
            if (!discovered.contains(current)) continue;
            long chunkKey = ((long) coordinate.chunkX() << 32)
                    ^ (coordinate.chunkZ() & 0xffff_ffffL);
            result.addAll(loadTickPartition(current, snapshots.get(chunkKey)));
        }
        result.sort(TICK_ORDER);
        return List.copyOf(result);
    }

    private TickPartitionKey currentTickPartitionKey(TickPartitionCoordinate coordinate) {
        return currentTickPartitionKey(coordinate, null);
    }

    private TickPartitionKey currentTickPartitionKey(TickPartitionCoordinate coordinate,
            Map<Long, CanonicalWorldgenStore.CanonicalChunkSnapshot> snapshots) {
        try {
            long chunkKey = ((long) coordinate.chunkX() << 32)
                    ^ (coordinate.chunkZ() & 0xffff_ffffL);
            CanonicalWorldgenStore.CanonicalChunkSnapshot snapshot = snapshots == null
                    ? canonical.find(coordinate.worldId(), coordinate.chunkX(), coordinate.chunkZ())
                    : snapshots.computeIfAbsent(chunkKey, ignored -> canonical.find(
                            coordinate.worldId(), coordinate.chunkX(), coordinate.chunkZ()));
            if (snapshot == null || snapshot.commit() == null) {
                throw durableState("durable tick partition has no current canonical snapshot", null);
            }
            NeutralFinalChunk source = snapshot.commit().semanticFinalChunk();
            if (source == null || source.chunkX() != coordinate.chunkX()
                    || source.chunkZ() != coordinate.chunkZ()) {
                throw durableState("canonical tick partition coordinate is malformed", null);
            }
            return new TickPartitionKey(coordinate.worldId(), coordinate.chunkX(),
                    coordinate.chunkZ(), coordinate.lane(), sourceFingerprint(source));
        } catch (FinalCarrierDurableStateException invalid) {
            throw invalid;
        } catch (DataAccessException | PersistenceException | TransactionException infrastructure) {
            throw infrastructure;
        } catch (RuntimeException malformed) {
            throw durableState("canonical tick partition source is malformed", malformed);
        }
    }

    private TickLedger readActiveTickLedger(TickPartitionKey key, Pageable limit) {
        probeIncompleteLegacyRows(key.worldId());
        List<FinalCarrierScheduledTick> scheduledRows = scheduled
                .findAllByWorldIdAndChunkXAndChunkZAndLaneAndSourceFingerprintOrderByDurableOrderAscIdAsc(
                        key.worldId(), key.chunkX(), key.chunkZ(), key.lane().name(),
                        key.sourceFingerprint(), limit);
        List<FinalCarrierConsumedTick> consumedRows = consumed
                .findAllByWorldIdAndChunkXAndChunkZAndLaneAndSourceFingerprintAndPublicationStateInOrderByDurableOrderAscIdAsc(
                        key.worldId(), key.chunkX(), key.chunkZ(), key.lane().name(),
                        key.sourceFingerprint(), RECOVERY_STATES, limit);
        if (scheduledRows.size() > FinalCarrierTickScheduler.MAX_PENDING_TICKS
                || consumedRows.size() > FinalCarrierTickScheduler.MAX_PENDING_TICKS) {
            throw durableState("active tick partition exceeds bounded history", null);
        }
        for (FinalCarrierScheduledTick row : scheduledRows) {
            if (!partitionKey(durableScheduledTick(row)).equals(key)) {
                throw durableState("active scheduled tick is bound to another source", null);
            }
        }
        for (FinalCarrierConsumedTick row : consumedRows) {
            if (!RECOVERY_STATES.contains(row.getState())
                    || !partitionKey(durableConsumedTick(row)).equals(key)) {
                throw durableState("active consumed tick is bound to another source", null);
            }
            validatePublicationRow(row);
        }
        return new TickLedger(List.copyOf(scheduledRows), List.copyOf(consumedRows));
    }

    private List<FinalCarrierTickScheduler.ScheduledTick> loadTickPartition(
            TickPartitionKey key) {
        return loadTickPartition(key, null);
    }

    private List<FinalCarrierTickScheduler.ScheduledTick> loadTickPartition(
            TickPartitionKey key, CanonicalWorldgenStore.CanonicalChunkSnapshot restoredSnapshot) {
        try {
            CanonicalWorldgenStore.CanonicalChunkSnapshot snapshot = restoredSnapshot == null
                    ? canonical.find(key.worldId(), key.chunkX(), key.chunkZ()) : restoredSnapshot;
            if (snapshot == null) {
                throw durableState("durable tick partition has no current canonical snapshot", null);
            }
            NeutralFinalChunk source = snapshot.commit().semanticFinalChunk();
            if (source == null || source.chunkX() != key.chunkX() || source.chunkZ() != key.chunkZ()
                    || !sourceFingerprint(source).equals(key.sourceFingerprint())) {
                throw durableState("tick partition source fingerprint is not current", null);
            }
            CanonicalWorldgenStore.Lane canonicalLane = tickCanonicalLane(key.lane());
            boolean acknowledged = (snapshot.laneAckMask() & canonicalLane.mask()) != 0;
            boolean rejected = (snapshot.laneRejectedMask() & canonicalLane.mask()) != 0;
            probeIncompleteLegacyRows(key.worldId());
            Pageable mutationLimit = PageRequest.of(0, 2);
            List<FinalCarrierLaneMutation> currentMutations = lanes
                    .findAllByWorldIdAndChunkXAndChunkZAndLaneAndSourceFingerprintOrderById(
                            key.worldId(), key.chunkX(), key.chunkZ(), canonicalLane.name(),
                            key.sourceFingerprint(), mutationLimit);
            if (currentMutations.size() > 1) {
                throw durableState("multiple current-source tick mutations share a partition", null);
            }
            FinalCarrierLaneMutation mutation = currentMutations.isEmpty()
                    ? null : currentMutations.getFirst();
            if (mutation != null) {
                validateTickMutationRow(mutation, key.worldId(), key.chunkX(), key.chunkZ(),
                        canonicalLane.name());
            }
            TickLedger active = null;
            if (rejected) {
                if (mutation != null && mutation.getActivationStatus()
                        != FinalCarrierLaneMutation.ActivationStatus.REJECTED) {
                    throw durableState("rejected canonical tick lane has a live mutation", null);
                }
                active = readActiveTickLedger(key, PageRequest.of(0,
                        boundedRowLimit(FinalCarrierTickScheduler.MAX_PENDING_TICKS)));
                if (!active.scheduledRows().isEmpty() || !active.consumedRows().isEmpty()) {
                    throw durableState("rejected canonical tick lane has durable tick rows", null);
                }
                return List.of();
            }
            if (!acknowledged) {
                active = readActiveTickLedger(key, PageRequest.of(0,
                        boundedRowLimit(FinalCarrierTickScheduler.MAX_PENDING_TICKS)));
                if (mutation != null || !active.scheduledRows().isEmpty()
                        || !active.consumedRows().isEmpty()) {
                    throw durableState("durable tick rows are not paired with an ACK", null);
                }
                return List.of();
            }
            if (mutation == null) {
                throw durableState("ACK-only tick partition has no durable mutation", null);
            }
            if (mutation.getActivationStatus()
                    != FinalCarrierLaneMutation.ActivationStatus.INSTALLED) {
                throw durableState("acknowledged tick mutation is not INSTALLED", null);
            }
            requireSource(source, mutation.getSourceFingerprint());
            FinalCarrierTickScheduler.CarrierReceipt seedReceipt =
                    new FinalCarrierTickScheduler.CarrierReceipt(key.worldId(), key.chunkX(),
                            key.chunkZ(), mutation.getSourceFingerprint(),
                            mutation.getPayloadFingerprint(), key.lane());
            FinalCarrierTickScheduler.CarrierReceipt receipt = canonicalTickReceipt(seedReceipt,
                    source);
            long admissionBaseMcTick = tickAdmissionBase(mutation, receipt);
            List<FinalCarrierTickScheduler.ScheduledTick> exact = canonicalTickCandidates(
                    source, receipt, admissionBaseMcTick);
            byte[] expectedPayload = tickPayload(receipt, admissionBaseMcTick, exact);
            if (!mutation.matches(receipt.lanePayloadFingerprint(), expectedPayload)) {
                throw durableState("acknowledged tick mutation payload mismatch", null);
            }
            Pageable exactLimit = PageRequest.of(0, boundedRowLimit(exact.size()));
            List<FinalCarrierScheduledTick> scheduledRows = scheduled
                    .findAllByWorldIdAndChunkXAndChunkZAndLaneAndSourceFingerprintOrderByDurableOrderAscIdAsc(
                            key.worldId(), key.chunkX(), key.chunkZ(), key.lane().name(),
                            key.sourceFingerprint(), exactLimit);
            List<FinalCarrierConsumedTick> consumedRows = consumed
                    .findAllByWorldIdAndChunkXAndChunkZAndLaneAndSourceFingerprintAndPublicationStateInOrderByDurableOrderAscIdAsc(
                            key.worldId(), key.chunkX(), key.chunkZ(), key.lane().name(),
                            key.sourceFingerprint(), RECOVERY_STATES, exactLimit);
            if (scheduledRows.size() > exact.size() || consumedRows.size() > exact.size()) {
                throw durableState("durable tick partition exceeds canonical candidate count", null);
            }
            authenticateDurableTickSubset(receipt, exact, scheduledRows, consumedRows, false);
            return scheduledRows.stream().map(FinalCarrierPersistenceService::durableScheduledTick)
                    .toList();
        } catch (FinalCarrierLaneRetryable | FinalCarrierDurableStateException expected) {
            throw expected;
        } catch (DataAccessException | PersistenceException | TransactionException infrastructure) {
            throw infrastructure;
        } catch (RuntimeException invalidPartition) {
            throw durableState("durable tick partition is malformed", invalidPartition);
        }
    }

    @Override
    @Transactional
    public FinalCarrierTickScheduler.DurablePublication beginOutcomeUnknown(
            FinalCarrierTickScheduler.ScheduledTick tick,
            FinalCarrierTickScheduler.DueDisposition disposition,
            FinalCarrierTickScheduler.SettlementResult result) {
        try {
            Objects.requireNonNull(tick, "scheduled tick");
            Objects.requireNonNull(disposition, "tick disposition");
            Objects.requireNonNull(result, "settlement result");
            requireSettlementWorldLock(tick.receipt().worldId(), "settlement world is missing");
            probeIncompleteLegacyRows(tick.receipt().worldId());
            FinalCarrierConsumedTick row = lockedConsumedTick(tick);
            if (row == null) {
                throw new FinalCarrierLaneRetryable(
                        "durable settlement publication is missing; retry settlement");
            }
            FinalCarrierTickPublication stored = durablePublicationValue(row);
            if (!row.matchesExact(tick, disposition)) {
                throw durableState("durable publication differs from settlement identity", null);
            }
            // An ALREADY result is intentionally ignored as an input plan. The row is the only
            // source of bytes after a replay or an unknown transaction outcome.
            if (result.status() == FinalCarrierTickScheduler.Settlement.COMMITTED
                    || result.status()
                            == FinalCarrierTickScheduler.Settlement.ALREADY_COMMITTED) {
                requireStoredSettlementResult(stored, result);
            } else {
                throw durableState("outcome-unknown cannot begin from a RETRY result", null);
            }
            if (row.getState() == FinalCarrierConsumedTick.PublicationState.REJECTED) {
                throw durableState("rejected settlement cannot enter outcome-unknown state", null);
            }
            if (row.getState() == FinalCarrierConsumedTick.PublicationState.UNACKNOWLEDGED) {
                requireSingleCasUpdate(markOutcomeUnknown(row),
                        "UNACKNOWLEDGED -> OUTCOME_UNKNOWN");
                return durablePublication(row,
                        FinalCarrierConsumedTick.PublicationState.OUTCOME_UNKNOWN);
            }
            return durablePublication(row);
        } catch (FinalCarrierLaneRetryable | FinalCarrierDurableStateException expected) {
            throw expected;
        } catch (DataAccessException | PersistenceException | TransactionException infrastructure) {
            throw new FinalCarrierLaneRetryable(
                    "final-carrier publication state storage failure is retryable", infrastructure);
        } catch (RuntimeException malformed) {
            throw durableState("durable publication state is malformed", malformed);
        }
    }

    @Override
    @Transactional
    public void acknowledgePublication(FinalCarrierTickScheduler.DurablePublication publication) {
        try {
            Objects.requireNonNull(publication, "durable publication");
            requireAcknowledgeSuppliedState(publication);
            requireSettlementWorldLock(publication.tick().receipt().worldId(),
                    "publication world is missing");
            probeIncompleteLegacyRows(publication.tick().receipt().worldId());
            FinalCarrierConsumedTick row = lockedConsumedTick(publication.tick());
            if (row == null) {
                throw new FinalCarrierLaneRetryable(
                        "durable publication disappeared before ACK; retry");
            }
            FinalCarrierTickPublication stored = durablePublicationValue(row);
            requireExactPublication(publication, stored);
            if (row.getState() == FinalCarrierConsumedTick.PublicationState.REJECTED) {
                throw durableState("rejected publication cannot be acknowledged", null);
            }
            if (row.getState() == FinalCarrierConsumedTick.PublicationState.ACKNOWLEDGED) {
                return;
            }
            if (row.getState() != FinalCarrierConsumedTick.PublicationState.OUTCOME_UNKNOWN) {
                throw durableState("publication must be outcome-unknown before acknowledgement",
                        null);
            }
            requireSingleCasUpdate(acknowledgeConsumedRow(row),
                    "OUTCOME_UNKNOWN -> ACKNOWLEDGED");
        } catch (FinalCarrierLaneRetryable | FinalCarrierDurableStateException expected) {
            throw expected;
        } catch (DataAccessException | PersistenceException | TransactionException infrastructure) {
            throw new FinalCarrierLaneRetryable(
                    "final-carrier publication ACK storage failure is retryable", infrastructure);
        } catch (RuntimeException malformed) {
            throw durableState("durable publication ACK identity is malformed", malformed);
        }
    }

    @Override
    @Transactional
    public void rejectPublication(FinalCarrierTickScheduler.DurablePublication publication) {
        try {
            Objects.requireNonNull(publication, "durable publication");
            requireRejectSuppliedState(publication);
            requireSettlementWorldLock(publication.tick().receipt().worldId(),
                    "publication world is missing");
            probeIncompleteLegacyRows(publication.tick().receipt().worldId());
            FinalCarrierConsumedTick row = lockedConsumedTick(publication.tick());
            if (row == null) {
                throw new FinalCarrierLaneRetryable(
                        "durable publication disappeared before rejection; retry");
            }
            FinalCarrierTickPublication stored = durablePublicationValue(row);
            requireExactPublication(publication, stored);
            if (row.getState() == FinalCarrierConsumedTick.PublicationState.ACKNOWLEDGED) {
                throw durableState("acknowledged publication cannot be rejected", null);
            }
            if (row.getState() == FinalCarrierConsumedTick.PublicationState.REJECTED) {
                return;
            }
            if (row.getState() != FinalCarrierConsumedTick.PublicationState.UNACKNOWLEDGED
                    && row.getState() != FinalCarrierConsumedTick.PublicationState.OUTCOME_UNKNOWN) {
                throw durableState("publication has an invalid rejection state", null);
            }
            requireSingleCasUpdate(rejectConsumedRow(row),
                    row.getState() + " -> REJECTED");
        } catch (FinalCarrierLaneRetryable | FinalCarrierDurableStateException expected) {
            throw expected;
        } catch (DataAccessException | PersistenceException | TransactionException infrastructure) {
            throw new FinalCarrierLaneRetryable(
                    "final-carrier publication rejection storage failure is retryable", infrastructure);
        } catch (RuntimeException malformed) {
            throw durableState("durable publication rejection identity is malformed", malformed);
        }
    }

    @Override
    @Transactional
    public List<FinalCarrierTickScheduler.DurablePublication> loadDurablePublications(
            long worldId, int limit) {
        try {
            if (limit < 1 || limit > FinalCarrierTickScheduler.MAX_PENDING_TICKS) {
                throw new IllegalArgumentException("durable publication limit is outside bounds");
            }
            requireSettlementWorldLock(worldId, "publication world is missing");
            probeIncompleteLegacyRows(worldId);
            Pageable bounded = PageRequest.of(0, boundedRowLimit(limit));
            ArrayList<FinalCarrierTickScheduler.DurablePublication> result = new ArrayList<>();
            Set<TickPartitionKey> validatedPartitions = new HashSet<>();
            for (FinalCarrierTickScheduler.Lane lane : FinalCarrierTickScheduler.Lane.values()) {
                List<FinalCarrierConsumedTick> rows = consumed
                        .findAllByWorldIdAndLaneAndPublicationStateInOrderByDurableOrderAscIdAsc(
                                worldId, lane.name(), RECOVERY_STATES, bounded);
                if (rows.size() > limit) {
                    throw durableState("durable publication recovery exceeds requested bound", null);
                }
                for (FinalCarrierConsumedTick row : rows) {
                    if (!RECOVERY_STATES.contains(row.getState())) continue;
                    FinalCarrierTickScheduler.DurablePublication publication = durablePublication(row);
                    FinalCarrierTickScheduler.ScheduledTick tick = publication.tick();
                    if (tick.receipt().worldId() != worldId) {
                        throw durableState("durable publication belongs to another world", null);
                    }
                    TickPartitionKey key = partitionKey(tick);
                    TickPartitionKey current = currentTickPartitionKey(partitionCoordinate(key));
                    if (!key.equals(current)) continue;
                    if (validatedPartitions.add(current)) loadTickPartition(current);
                    result.add(publication);
                }
            }
            if (result.size() > limit) {
                throw durableState("durable publication recovery exceeds requested bound", null);
            }
            result.sort(Comparator.comparing(FinalCarrierTickScheduler.DurablePublication::tick,
                    TICK_ORDER));
            Set<FinalCarrierTickScheduler.TickKey> keys = new HashSet<>();
            for (FinalCarrierTickScheduler.DurablePublication publication : result) {
                if (!keys.add(publication.tick().key())) {
                    throw durableState("duplicate durable publication identity", null);
                }
            }
            return List.copyOf(result);
        } catch (FinalCarrierDurableStateException expected) {
            throw expected;
        } catch (DataAccessException | PersistenceException | TransactionException infrastructure) {
            throw new FinalCarrierLaneRetryable(
                    "final-carrier publication recovery storage failure is retryable", infrastructure);
        }
    }

    private static void requireStoredSettlementResult(FinalCarrierTickPublication stored,
            FinalCarrierTickScheduler.SettlementResult result) {
        byte[] storedBody = FinalCarrierTickPublicationCodec.encode(stored,
                TICK_PUBLICATION_LIMITS);
        if (!stored.mutation().equals(result.mutation())
                || !FinalCarrierTickPublicationCodec.publicationKey(stored.scheduledTick(),
                        stored.disposition(), TICK_PUBLICATION_LIMITS)
                        .equals(result.publicationKey())
                || !FinalCarrierTickPublicationCodec.publicationDigest(storedBody)
                        .equals(result.mutationDigest())
                || !MessageDigest.isEqual(storedBody, result.mutationBody())) {
            throw durableState("settlement result differs from stored publication", null);
        }
    }

    private static void requireExactPublication(
            FinalCarrierTickScheduler.DurablePublication supplied,
            FinalCarrierTickPublication stored) {
        if (!FinalCarrierConsumedTick.isLegalPublicationTransition(
                supplied.publicationState(), stored.publicationState())) {
            throw durableState("publication supplied state is not a legal stored transition", null);
        }
        byte[] storedBody = FinalCarrierTickPublicationCodec.encode(stored,
                TICK_PUBLICATION_LIMITS);
        String storedKey = FinalCarrierTickPublicationCodec.publicationKey(stored.scheduledTick(),
                stored.disposition(), TICK_PUBLICATION_LIMITS);
        String storedDigest = FinalCarrierTickPublicationCodec.publicationDigest(storedBody);
        if (!supplied.tick().equals(stored.scheduledTick())
                || !supplied.tick().receipt().sourceFingerprint().equals(
                        stored.scheduledTick().receipt().sourceFingerprint())
                || supplied.disposition() != stored.disposition()
                || !supplied.mutation().equals(stored.mutation())
                || !supplied.publicationKey().equals(storedKey)
                || !supplied.mutationDigest().equals(storedDigest)
                || !MessageDigest.isEqual(supplied.mutationBody(), storedBody)) {
            throw durableState("publication operation is not bound to stored bytes", null);
        }
    }

    private static void requireAcknowledgeSuppliedState(
            FinalCarrierTickScheduler.DurablePublication publication) {
        FinalCarrierConsumedTick.PublicationState state = publication.publicationState();
        if (state != FinalCarrierConsumedTick.PublicationState.OUTCOME_UNKNOWN
                && state != FinalCarrierConsumedTick.PublicationState.ACKNOWLEDGED) {
            throw durableState("ACK publication supplied state is not legal", null);
        }
    }

    private static void requireRejectSuppliedState(
            FinalCarrierTickScheduler.DurablePublication publication) {
        FinalCarrierConsumedTick.PublicationState state = publication.publicationState();
        if (state != FinalCarrierConsumedTick.PublicationState.UNACKNOWLEDGED
                && state != FinalCarrierConsumedTick.PublicationState.OUTCOME_UNKNOWN
                && state != FinalCarrierConsumedTick.PublicationState.REJECTED) {
            throw durableState("reject publication supplied state is not legal", null);
        }
    }

    private FinalCarrierTickScheduler.DurablePublication durablePublication(
            FinalCarrierConsumedTick row) {
        FinalCarrierTickPublication publication = durablePublicationValue(row);
        return new FinalCarrierTickScheduler.DurablePublication(publication.scheduledTick(),
                publication.disposition(), publication.mutation(), row.getPublicationKey(),
                row.getMutationDigest(), row.getPublicationBody(), row.getState());
    }

    private static FinalCarrierTickScheduler.DurablePublication durablePublication(
            FinalCarrierConsumedTick row, FinalCarrierConsumedTick.PublicationState state) {
        FinalCarrierTickPublication publication = durablePublicationValue(row);
        return new FinalCarrierTickScheduler.DurablePublication(publication.scheduledTick(),
                publication.disposition(), publication.mutation(), row.getPublicationKey(),
                row.getMutationDigest(), row.getPublicationBody(), state);
    }

    private static FinalCarrierTickPublication durablePublicationValue(
            FinalCarrierConsumedTick row) {
        try {
            return row.toPublication(TICK_PUBLICATION_LIMITS);
        } catch (FinalCarrierDurableStateException invalid) {
            throw invalid;
        } catch (RuntimeException malformed) {
            // 원인 문구를 보존해야 운영 로그만으로 어떤 컬럼이 깨졌는지 판별할 수 있다.
            throw durableState("consumed publication row is malformed: "
                    + malformed.getMessage(), malformed);
        }
    }

    private static void validatePublicationRow(FinalCarrierConsumedTick row) {
        durablePublicationValue(row);
    }

    private static void validatePublicationRows(List<FinalCarrierConsumedTick> rows) {
        for (FinalCarrierConsumedTick row : rows) validatePublicationRow(row);
    }

    private FinalCarrierConsumedTick lockedConsumedTick(
            FinalCarrierTickScheduler.ScheduledTick tick) {
        return consumed
                .findLockedByWorldIdAndChunkXAndChunkZAndLaneAndXAndYAndZAndTypeKeyAndSourceFingerprint(
                tick.receipt().worldId(), tick.receipt().chunkX(), tick.receipt().chunkZ(),
                tick.lane().name(), tick.x(), tick.y(), tick.z(), tick.typeKey(),
                tick.receipt().sourceFingerprint()).orElse(null);
    }

    private void probeIncompleteLegacyRows(long worldId) {
        Pageable probeLimit = PageRequest.of(0, 1);
        List<FinalCarrierScheduledTickRepository.LegacyScheduledTickIdProjection> scheduledLegacy =
                scheduled.findIncompleteLegacyRowsByWorldId(worldId, probeLimit);
        if (scheduledLegacy == null) {
            throw durableState("scheduled tick legacy probe returned no result", null);
        }
        if (!scheduledLegacy.isEmpty()) {
            Long id = scheduledLegacy.getFirst().getId();
            throw durableState("scheduled tick row has incomplete legacy identity: " + id, null);
        }
        List<FinalCarrierConsumedTickRepository.LegacyConsumedTickIdProjection> consumedLegacy =
                consumed.findIncompleteLegacyRowsByWorldId(worldId, probeLimit);
        if (consumedLegacy == null) {
            throw durableState("consumed tick legacy probe returned no result", null);
        }
        if (!consumedLegacy.isEmpty()) {
            Long id = consumedLegacy.getFirst().getId();
            throw durableState("consumed tick row has incomplete legacy identity: " + id, null);
        }
    }

    private int markOutcomeUnknown(FinalCarrierConsumedTick row) {
        return consumed.markOutcomeUnknown(row.getWorldId(), row.getChunkX(), row.getChunkZ(),
                row.getLane(), row.getX(), row.getY(), row.getZ(), row.getTypeKey(),
                row.getDisposition(), row.getDurableOrder(), row.getSourceFingerprint(),
                row.getPayloadFingerprint(), row.getPublicationKey(), row.getPublicationDigest());
    }

    private int acknowledgeConsumedRow(FinalCarrierConsumedTick row) {
        return consumed.acknowledgePublication(row.getWorldId(), row.getChunkX(), row.getChunkZ(),
                row.getLane(), row.getX(), row.getY(), row.getZ(), row.getTypeKey(),
                row.getDisposition(), row.getDurableOrder(), row.getSourceFingerprint(),
                row.getPayloadFingerprint(), row.getPublicationKey(), row.getPublicationDigest());
    }

    private int rejectConsumedRow(FinalCarrierConsumedTick row) {
        return consumed.rejectPublication(row.getWorldId(), row.getChunkX(), row.getChunkZ(),
                row.getLane(), row.getX(), row.getY(), row.getZ(), row.getTypeKey(),
                row.getDisposition(), row.getDurableOrder(), row.getSourceFingerprint(),
                row.getPayloadFingerprint(), row.getPublicationKey(), row.getPublicationDigest());
    }

    private static void requireSingleCasUpdate(int updated, String transition) {
        if (updated != 1) {
            throw durableState("exact publication CAS " + transition + " affected " + updated
                    + " rows", null);
        }
    }

    /**
     * Takes this world's row lock, shared.
     *
     * <p>Used by canonical admission, settlement, recovery and chunk activation. None of them
     * changes the world row; they need only the guarantee that it is not deleted underneath the
     * transaction, so they take {@code FOR SHARE} and are compatible with one another. Taking it
     * exclusively is what starved the owner: production and admission hold the row per chunk, so
     * during a production burst the owner's activation and recovery found it locked on nearly
     * every attempt and the world stopped applying block edits.</p>
     *
     * <p>The owner thread additionally never waits. Even a shared lock can queue behind a world
     * deletion, and waiting there stops the whole world for up to
     * {@code innodb_lock_wait_timeout}, so on the owner this fails immediately into the caller's
     * retry path.</p>
     */
    private void requireWorldLock(long worldId, String missingMessage) {
        if (TickSafetyTelemetry.isTickThread()) {
            requireWorldLockWithoutWaiting(worldId, missingMessage);
            return;
        }
        if (worlds.findByIdForShare(worldId).isEmpty()) {
            throw new FinalCarrierLaneRetryable(missingMessage);
        }
    }

    /**
     * The owner-thread variant: no wait, immediate retryable failure, and a rate-limited summary
     * instead of a pair of warnings per failure. Nothing is thrown away on failure because the
     * lock is the first step of every one of these paths; the caller retries on a later turn.
     */
    private void requireWorldLockWithoutWaiting(long worldId, String missingMessage) {
        boolean missing;
        try {
            missing = worlds.findByIdForShareNoWait(worldId).isEmpty();
        } catch (PessimisticLockingFailureException | LockTimeoutException
                | PessimisticLockException busy) {
            noteOwnerLockContention(worldId);
            throw FinalCarrierLaneRetryable.worldLockContention(
                    "final-carrier world row lock is held by another transaction", busy);
        }
        if (missing) throw new FinalCarrierLaneRetryable(missingMessage);
    }

    /** 60초에 한 줄만 남기고, 그 사이 무대기 실패 건수를 누적해 함께 보고한다. */
    private void noteOwnerLockContention(long worldId) {
        OWNER_LOCK_CONTENTION_COUNT.incrementAndGet();
        long now = System.nanoTime();
        long last = OWNER_LOCK_CONTENTION_LOG_NANOS.get();
        if (last != 0L && now - last < OWNER_LOCK_CONTENTION_LOG_INTERVAL_NANOS) return;
        if (!OWNER_LOCK_CONTENTION_LOG_NANOS.compareAndSet(last, now)) return;
        log.info("월드 행 공유 잠금을 즉시 잡지 못해 틱 스레드가 물러났습니다:"
                + " world={} 지난 60초 {}건, 다음 턴에 재시도합니다",
                worldId, OWNER_LOCK_CONTENTION_COUNT.getAndSet(0L));
    }

    /**
     * The settlement and publication variant, which the world owner may never reach.
     *
     * <p>These four steps run on the settlement workers. The guard is what keeps that true: a
     * settlement that found its way back onto the owner thread would take a database round trip
     * inside the owner turn for work that has a worker of its own.</p>
     */
    private void requireSettlementWorldLock(long worldId, String missingMessage) {
        if (TickSafetyTelemetry.isTickThread()) {
            throw new IllegalStateException(
                    "final-carrier settlement world row lock attempted on the world owner thread");
        }
        requireWorldLock(worldId, missingMessage);
    }

    private static FinalCarrierTickScheduler.SettlementResult settlementResult(
            FinalCarrierTickScheduler.Settlement status,
            FinalCarrierTickScheduler.ScheduledTick tick,
            FinalCarrierTickScheduler.DueDisposition disposition,
            FinalCarrierTickScheduler.TickMutation mutation) {
        FinalCarrierTickPublication publication = new FinalCarrierTickPublication(tick, disposition,
                mutation, FinalCarrierConsumedTick.PublicationState.UNACKNOWLEDGED);
        byte[] body = FinalCarrierTickPublicationCodec.encode(publication, TICK_PUBLICATION_LIMITS);
        return new FinalCarrierTickScheduler.SettlementResult(status, publication.mutation(),
                FinalCarrierTickPublicationCodec.publicationKey(tick, disposition,
                        TICK_PUBLICATION_LIMITS), FinalCarrierTickPublicationCodec.publicationDigest(body),
                body);
    }

    private static FinalCarrierTickScheduler.ScheduledTick durableScheduledTick(
            FinalCarrierScheduledTick row) {
        try {
            return row.toScheduledTick();
        } catch (FinalCarrierDurableStateException invalid) {
            throw invalid;
        } catch (RuntimeException malformed) {
            throw durableState("scheduled tick row is malformed", malformed);
        }
    }

    private static FinalCarrierTickScheduler.ScheduledTick durableConsumedTick(
            FinalCarrierConsumedTick row) {
        try {
            return row.toScheduledTick();
        } catch (FinalCarrierDurableStateException invalid) {
            throw invalid;
        } catch (RuntimeException malformed) {
            throw durableState("consumed tick identity is malformed", malformed);
        }
    }

    private static void validateTickMutationRow(FinalCarrierLaneMutation mutation,
            long worldId, int chunkX, int chunkZ, String lane) {
        try {
            if (mutation.getWorldId() != worldId || mutation.getChunkX() != chunkX
                    || mutation.getChunkZ() != chunkZ || !lane.equals(mutation.getLane())
                    || mutation.getSourceFingerprint() == null
                    || mutation.getPayloadFingerprint() == null
                    || mutation.getInstallationIdentity() == null
                    || mutation.getActivationStatus() == null
                    || mutation.getTypedPayload() == null
                    || mutation.getTypedPayload().length == 0
                    || !mutation.getSourceFingerprint().matches("[0-9a-f]{64}")
                    || !mutation.getPayloadFingerprint().matches("[0-9a-f]{64}")
                    || !mutation.getInstallationIdentity().equals(worldId + ":" + chunkX + ":"
                            + chunkZ + ":" + lane + ":" + mutation.getSourceFingerprint() + ":"
                            + mutation.getPayloadFingerprint())) {
                throw durableState("tick lane mutation has malformed current-hash identity", null);
            }
            CanonicalWorldgenStore.Lane.valueOf(lane);
        } catch (FinalCarrierDurableStateException invalid) {
            throw invalid;
        } catch (RuntimeException malformed) {
            throw durableState("tick lane mutation is malformed", malformed);
        }
    }

    private void authenticateDurableTickSubset(FinalCarrierTickScheduler.CarrierReceipt receipt,
            List<FinalCarrierTickScheduler.ScheduledTick> exact,
            List<FinalCarrierScheduledTick> rows, List<FinalCarrierConsumedTick> settledRows) {
        authenticateDurableTickSubset(receipt, exact, rows, settledRows, true);
    }

    private void authenticateDurableTickSubset(FinalCarrierTickScheduler.CarrierReceipt receipt,
            List<FinalCarrierTickScheduler.ScheduledTick> exact,
            List<FinalCarrierScheduledTick> rows, List<FinalCarrierConsumedTick> settledRows,
            boolean requireComplete) {
        if (rows.size() > exact.size() || settledRows.size() > exact.size()) {
            throw durableState("durable tick partition exceeds canonical candidate count", null);
        }
        Map<FinalCarrierTickScheduler.TickKey, FinalCarrierTickScheduler.ScheduledTick> expected =
                new HashMap<>();
        for (FinalCarrierTickScheduler.ScheduledTick candidate : exact) {
            if (expected.put(candidate.key(), candidate) != null) {
                throw durableState("canonical first-winner set contains duplicate keys", null);
            }
        }
        Set<FinalCarrierTickScheduler.TickKey> present = new HashSet<>();
        Set<Long> durableOrders = new HashSet<>();
        for (FinalCarrierScheduledTick row : rows) {
            FinalCarrierTickScheduler.ScheduledTick durable = durableScheduledTick(row);
            if (!durable.receipt().equals(receipt) || durable.durableOrder() <= 0L
                    || !present.add(durable.key()) || !durableOrders.add(durable.durableOrder())) {
                throw durableState("duplicate or foreign durable scheduled tick", null);
            }
            FinalCarrierTickScheduler.ScheduledTick candidate = expected.get(durable.key());
            if (candidate == null || !matchesExactScheduledCandidate(row, candidate)) {
                throw durableState("durable scheduled tick differs from canonical authority", null);
            }
        }
        Set<FinalCarrierTickScheduler.TickKey> settled = new HashSet<>();
        for (FinalCarrierConsumedTick row : settledRows) {
            FinalCarrierTickScheduler.ScheduledTick durable = durableConsumedTick(row);
            FinalCarrierTickScheduler.ScheduledTick candidate = expected.get(durable.key());
            boolean duplicateKey = !settled.add(durable.key());
            boolean duplicateOrder = !durableOrders.add(durable.durableOrder());
            if (!durable.receipt().equals(receipt) || candidate == null
                    || durable.durableOrder() <= 0L || duplicateKey || duplicateOrder
                    || !row.matchesCandidate(candidate)) {
                throw durableState("consumed tick differs from canonical authority: "
                        + "world=" + receipt.worldId() + " chunk=" + receipt.chunkX() + "," + receipt.chunkZ()
                        + " key=" + durable.key() + " order=" + durable.durableOrder()
                        + " duplicateKey=" + duplicateKey + " duplicateOrder=" + duplicateOrder
                        + " candidate=" + (candidate == null ? "missing" : "present")
                        + " due=" + durable.dueTick() + "/" + (candidate == null ? "-" : candidate.dueTick())
                        + " block=" + durable.expectedBlockId() + "/" + (candidate == null ? "-" : candidate.expectedBlockId())
                        + " priority=" + durable.priority() + "/" + (candidate == null ? "-" : candidate.priority())
                        + " subTick=" + durable.subTickOrder() + "/" + (candidate == null ? "-" : candidate.subTickOrder())
                        + receiptConflictDetail(durable.receipt(), receipt), null);
            }
            validatePublicationRow(row);
            if (present.contains(durable.key())) {
                throw durableState("scheduled and consumed tick identities overlap", null);
            }
        }
        if (requireComplete) {
            for (FinalCarrierTickScheduler.ScheduledTick candidate : exact) {
                if (!present.contains(candidate.key())
                        && !settled.contains(candidate.key())) {
                    throw durableState("canonical durable tick identity is incomplete", null);
                }
            }
        }
    }

    private static int boundedRowLimit(int canonicalCandidateCount) {
        if (canonicalCandidateCount < 0 || canonicalCandidateCount == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("invalid canonical tick candidate count");
        }
        return Math.max(1, canonicalCandidateCount + 1);
    }

    private static FinalCarrierTickScheduler.CarrierReceipt canonicalTickReceipt(
            FinalCarrierTickScheduler.CarrierReceipt delivered,
            NeutralFinalChunk source) {
        return delivered.lane() == FinalCarrierTickScheduler.Lane.BLOCK
                ? FinalCarrierTickScheduler.blockReceipt(delivered.worldId(), source.chunkX(),
                        source.chunkZ(), delivered.sourceFingerprint(),
                        source.sidecars().blockTicks())
                : FinalCarrierTickScheduler.fluidReceipt(delivered.worldId(), source.chunkX(),
                        source.chunkZ(), delivered.sourceFingerprint(),
                        source.sidecars().fluidTicks());
    }

    private static NeutralFinalChunk validateClaim(
            CanonicalWorldgenStore.LaneReceipt claim, int chunkX, int chunkZ,
            String sourceFingerprint) {
        NeutralFinalChunk source;
        try {
            source = claim.semanticActivation();
        } catch (FinalCarrierDurableStateException invalid) {
            throw invalid;
        } catch (RuntimeException malformed) {
            throw durableState("canonical lane claim activation is malformed", malformed);
        }
        if (source.chunkX() != chunkX || source.chunkZ() != chunkZ) {
            throw new IllegalStateException("canonical lane claim coordinate mismatch");
        }
        requireSource(source, sourceFingerprint);
        return source;
    }

    private static void requireSource(NeutralFinalChunk source,
            String sourceFingerprint) {
        if (!sourceFingerprint(source).equals(sourceFingerprint)) {
            throw new IllegalStateException("runtime carrier source differs from canonical claim");
        }
    }

    private static String sourceFingerprint(NeutralFinalChunk source) {
        return sha256(source.withSidecars(NeutralFinalChunk.Sidecars.EMPTY).encodedCarrier());
    }

    private static void requireExactProjection(CanonicalWorldgenStore.Lane lane,
            NeutralFinalChunk.Sidecars canonicalPayload, NeutralFinalChunk.Sidecars exact) {
        Objects.requireNonNull(exact, "exact lane payload");
        boolean valid = switch (lane) {
            case LOOT -> only(exact, lane)
                    && isMultiplicityPreservingProjection(canonicalPayload.loot(), exact.loot());
            case SPAWNERS -> only(exact, lane)
                    && isMultiplicityPreservingProjection(
                            canonicalPayload.spawners(), exact.spawners());
            case OWNERS -> only(exact, lane)
                    && isMultiplicityPreservingProjection(
                            canonicalPayload.owners(), exact.owners());
            case ARCHAEOLOGY -> only(exact, lane)
                    && isMultiplicityPreservingProjection(
                            canonicalPayload.archaeology(), exact.archaeology());
            case BEES -> only(exact, lane)
                    && isEncounterOrderedProjection(canonicalPayload.bees(), exact.bees());
            case BLOCK_ENTITIES -> only(exact, lane)
                    && isMultiplicityPreservingProjection(
                            canonicalPayload.blockEntities(), exact.blockEntities());
            case ENTITIES -> only(exact, lane)
                    && isEncounterOrderedProjection(
                            canonicalPayload.entities(), exact.entities());
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("payload is not an override-filtered " + lane
                + " projection of the canonical carrier");
    }

    private static <T> boolean isMultiplicityPreservingProjection(
            List<T> source, List<T> projection) {
        Map<T, Integer> remaining = new HashMap<>();
        for (T value : source) remaining.merge(value, 1, Integer::sum);
        for (T value : projection) {
            Integer count = remaining.get(value);
            if (count == null) return false;
            if (count == 1) remaining.remove(value);
            else remaining.put(value, count - 1);
        }
        return true;
    }

    private static <T> boolean isEncounterOrderedProjection(List<T> source, List<T> projection) {
        int sourceIndex = 0;
        for (T expected : projection) {
            while (sourceIndex < source.size()
                    && !source.get(sourceIndex).equals(expected)) sourceIndex++;
            if (sourceIndex == source.size()) return false;
            sourceIndex++;
        }
        return true;
    }

    private static boolean only(NeutralFinalChunk.Sidecars value, CanonicalWorldgenStore.Lane lane) {
        return (lane == CanonicalWorldgenStore.Lane.LOOT || value.loot().isEmpty())
                && (lane == CanonicalWorldgenStore.Lane.SPAWNERS || value.spawners().isEmpty())
                && (lane == CanonicalWorldgenStore.Lane.OWNERS || value.owners().isEmpty())
                && (lane == CanonicalWorldgenStore.Lane.ARCHAEOLOGY || value.archaeology().isEmpty())
                && (lane == CanonicalWorldgenStore.Lane.BLOCK_ENTITIES
                        || value.blockEntities().isEmpty())
                && (lane == CanonicalWorldgenStore.Lane.ENTITIES || value.entities().isEmpty())
                && value.blockTicks().isEmpty() && value.fluidTicks().isEmpty()
                && (lane == CanonicalWorldgenStore.Lane.BEES || value.bees().isEmpty());
    }

    private void requireExactTickAdmission(NeutralFinalChunk source,
            FinalCarrierTickScheduler.CarrierReceipt receipt,
            List<FinalCarrierTickScheduler.ScheduledTick> candidates,
            long admissionBaseMcTick) {
        FinalCarrierTickScheduler.CarrierReceipt canonicalReceipt =
                canonicalTickReceipt(receipt, source);
        if (!canonicalReceipt.equals(receipt)) {
            throw new IllegalArgumentException(
                    "tick receipt is not the exact canonical lane projection");
        }
        Set<FinalCarrierTickScheduler.TickKey> candidateKeys = new HashSet<>();
        for (FinalCarrierTickScheduler.ScheduledTick candidate : candidates) {
            if (candidate == null) {
                throw new IllegalArgumentException("null final-carrier tick candidate");
            }
            if (!candidateKeys.add(candidate.key())) {
                throw new IllegalArgumentException(
                        "duplicate final-carrier tick candidate key: " + candidate.key());
            }
        }
        FinalCarrierTickScheduler scheduler = new FinalCarrierTickScheduler(
                receipt.worldId(), this);
        FinalCarrierTickScheduler.PreparedAdmission exact;
        try {
            exact = receipt.lane() == FinalCarrierTickScheduler.Lane.BLOCK
                    ? scheduler.prepareBlockLane(canonicalReceipt, admissionBaseMcTick, true,
                            source.sidecars().blockTicks())
                    : scheduler.prepareFluidLane(canonicalReceipt, admissionBaseMcTick, true,
                            source.sidecars().fluidTicks());
        } catch (FinalCarrierDurableStateException invalid) {
            throw invalid;
        } catch (RuntimeException malformedCanonicalTicks) {
            throw durableState("canonical tick authority is malformed", malformedCanonicalTicks);
        }
        if (candidates.size() != exact.candidates().size()) {
            throw new IllegalArgumentException(
                    "tick candidates are not the complete canonical first-winner projection");
        }
        if (!exact.candidates().equals(candidates)) {
            throw new IllegalArgumentException(
                    "tick candidates differ from canonical timing or first-winner projection");
        }
    }

    private static byte[] encodeProjection(NeutralFinalChunk source,
            NeutralFinalChunk.Sidecars exact) {
        return source.withSidecars(exact).encodedCarrier();
    }

    private List<FinalCarrierTickScheduler.ScheduledTick> canonicalTickCandidates(
            NeutralFinalChunk source,
            FinalCarrierTickScheduler.CarrierReceipt receipt, long admissionBaseMcTick) {
        FinalCarrierTickScheduler scheduler = new FinalCarrierTickScheduler(receipt.worldId(), this);
        return receipt.lane() == FinalCarrierTickScheduler.Lane.BLOCK
                ? scheduler.prepareBlockLane(receipt, admissionBaseMcTick, true,
                        source.sidecars().blockTicks()).candidates()
                : scheduler.prepareFluidLane(receipt, admissionBaseMcTick, true,
                        source.sidecars().fluidTicks()).candidates();
    }

    private static long tickAdmissionBase(FinalCarrierLaneMutation mutation,
            FinalCarrierTickScheduler.CarrierReceipt receipt) {
        String payload = new String(mutation.getTypedPayload(), StandardCharsets.US_ASCII);
        String[] fields = payload.split("\\n", -1);
        if (fields.length != 5 || !fields[0].equals("FINAL_CARRIER_TICK_AUTHORITY_V1")
                || !fields[1].equals(receipt.lane().name())
                || !fields[2].equals(receipt.lanePayloadFingerprint())
                || !fields[4].matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("durable tick authority payload is malformed");
        }
        try {
            long value = Long.parseLong(fields[3]);
            if (value < 0) throw new NumberFormatException("negative");
            return value;
        } catch (NumberFormatException invalid) {
            throw new IllegalStateException("durable tick admission base is malformed", invalid);
        }
    }

    private static byte[] tickPayload(FinalCarrierTickScheduler.CarrierReceipt receipt,
            long admissionBaseMcTick,
            List<FinalCarrierTickScheduler.ScheduledTick> candidates) {
        StringBuilder rows = new StringBuilder();
        for (FinalCarrierTickScheduler.ScheduledTick candidate : candidates) {
            rows.append(candidate.lane().name()).append('\u0000')
                    .append(candidate.x()).append('\u0000').append(candidate.y()).append('\u0000')
                    .append(candidate.z()).append('\u0000').append(candidate.typeKey()).append('\u0000')
                    .append(candidate.expectedBlockId()).append('\u0000')
                    .append(candidate.dueTick()).append('\u0000')
                    .append(candidate.priority().value()).append('\u0000')
                    .append(candidate.subTickOrder()).append('\n');
        }
        String identity = "FINAL_CARRIER_TICK_AUTHORITY_V1\n" + receipt.lane().name() + "\n"
                + receipt.lanePayloadFingerprint() + "\n" + admissionBaseMcTick + "\n"
                + sha256(rows.toString().getBytes(StandardCharsets.US_ASCII));
        return identity.getBytes(StandardCharsets.US_ASCII);
    }

    private static boolean matchesExactScheduledCandidate(FinalCarrierScheduledTick row,
            FinalCarrierTickScheduler.ScheduledTick candidate) {
        FinalCarrierTickScheduler.CarrierReceipt receipt = candidate.receipt();
        return row.getWorldId() == receipt.worldId()
                && row.getChunkX() == receipt.chunkX() && row.getChunkZ() == receipt.chunkZ()
                && row.getLane().equals(candidate.lane().name())
                && row.getX() == candidate.x() && row.getY() == candidate.y()
                && row.getZ() == candidate.z() && row.getTypeKey().equals(candidate.typeKey())
                && row.getExpectedBlockId() == candidate.expectedBlockId()
                && row.getDueTick() == candidate.dueTick()
                && row.getPriority() == candidate.priority().value()
                && row.getSubTickOrder() == candidate.subTickOrder()
                && row.getSourceFingerprint().equals(receipt.sourceFingerprint())
                && row.getPayloadFingerprint().equals(receipt.lanePayloadFingerprint());
    }

    private static boolean sameCandidateIdentity(
            FinalCarrierTickScheduler.ScheduledTick left,
            FinalCarrierTickScheduler.ScheduledTick right) {
        return left.key().equals(right.key())
                && left.receipt().equals(right.receipt())
                && left.expectedBlockId() == right.expectedBlockId()
                && left.dueTick() == right.dueTick()
                && left.priority() == right.priority()
                && left.subTickOrder() == right.subTickOrder();
    }

    private static CanonicalWorldgenStore.Lane tickCanonicalLane(
            FinalCarrierTickScheduler.Lane lane) {
        return lane == FinalCarrierTickScheduler.Lane.BLOCK
                ? CanonicalWorldgenStore.Lane.BLOCK_TICKS
                : CanonicalWorldgenStore.Lane.FLUID_TICKS;
    }

    private static CanonicalWorldgenStore.Lane canonicalLane(
            TerrainAccessor.FinalLiveCarrierLane lane) {
        return CanonicalWorldgenStore.Lane.valueOf(lane.name());
    }

    private static TerrainAccessor.FinalLiveCarrierLane canonicalLaneRuntime(
            CanonicalWorldgenStore.Lane lane) {
        return TerrainAccessor.FinalLiveCarrierLane.valueOf(lane.name());
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static FinalCarrierDurableStateException durableState(String message,
            Throwable cause) {
        return cause == null ? new FinalCarrierDurableStateException(message)
                : new FinalCarrierDurableStateException(message, cause);
    }

    @Transactional(readOnly = true)
    public List<com.gameexpert.falling.dto.FallingSpeleothemState> loadFallingSpeleothems(long worldId) {
        if (tickRecoveryEntityManager == null) return List.of();
        return tickRecoveryEntityManager.createQuery(
                "select f from WorldFallingSpeleothem f where f.world.id = :worldId order by f.id",
                com.gameexpert.falling.entity.WorldFallingSpeleothem.class)
                .setParameter("worldId", worldId).getResultList().stream()
                .map(com.gameexpert.falling.entity.WorldFallingSpeleothem::snapshot).toList();
    }

    @Transactional
    public void checkpointFallingSpeleothems(long worldId,
            List<com.gameexpert.falling.dto.FallingSpeleothemState> expected,
            List<com.gameexpert.falling.dto.FallingSpeleothemState> updates,
            Runnable settleGround) {
        requireSettlementWorldLock(worldId, "falling speleothem checkpoint");
        if (expected.size() != updates.size()) throw new IllegalArgumentException("falling checkpoint cardinality");
        for (int i = 0; i < updates.size(); i++) {
            var state = updates.get(i);
            var row = tickRecoveryEntityManager.createQuery(
                    "select f from WorldFallingSpeleothem f where f.world.id = :worldId and f.entityKey = :key",
                    com.gameexpert.falling.entity.WorldFallingSpeleothem.class)
                    .setParameter("worldId", worldId).setParameter("key", state.id())
                    .setLockMode(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE).getSingleResult();
            if (!row.snapshot().equals(expected.get(i)) && !row.snapshot().equals(state)) {
                throw new IllegalStateException("falling speleothem checkpoint is stale");
            }
            row.apply(state);
        }
        if (settleGround != null) settleGround.run();
        tickRecoveryEntityManager.flush();
    }
}
