package com.gameexpert.tnt.service;

import com.gameexpert.block.persistence.BlockDiffBuffer;
import com.gameexpert.block.persistence.BlockDiffFlusher;
import com.gameexpert.block.entity.WorldBlockDiff;
import com.gameexpert.block.repository.WorldBlockDiffRepository;
import com.gameexpert.engine.BlockPos;
import com.gameexpert.ground.dto.GroundMutationCommand;
import com.gameexpert.ground.service.GroundMutationOutcome;
import com.gameexpert.ground.service.GroundMutationSettlementService;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.tnt.dto.ExplosionSettlementCommand;
import com.gameexpert.tnt.dto.PrimedTntSnapshot;
import com.gameexpert.tnt.entity.WorldExplosionSettlement;
import com.gameexpert.tnt.entity.WorldPrimedTnt;
import com.gameexpert.tnt.repository.WorldExplosionSettlementRepository;
import com.gameexpert.tnt.repository.WorldPrimedTntRepository;
import com.gameexpert.api.persistence.WorldAccess;
import com.gameexpert.api.persistence.WorldStore;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Transactional delta store for the per-world PrimedTnt entity set. */
@Service
public class PrimedTntPersistenceService {
    public enum ExplosionSettlementOutcome { COMMITTED, IDEMPOTENT }

    /** Required production authority for canonical base cells absent from the sparse overlay. */
    public interface CanonicalBaseBlockAuthority {
        String canonicalProductIdentity(WorldAccess world);

        BaseBlockWitness preflightExact(WorldAccess world,
                ExplosionSettlementCommand.BlockTransition transition);
    }

    /** Exact committed-carrier witness returned by {@link CanonicalBaseBlockAuthority}. */
    public record BaseBlockWitness(Long worldId, int x, int y, int z,
            short blockType, short blockState, String canonicalProductIdentity,
            String canonicalReceiptIdentity, long canonicalRevision) {
        public BaseBlockWitness {
            if (worldId == null || worldId <= 0 || worldId == Long.MAX_VALUE
                    || !ExplosionSettlementCommand.isSupportedProductIdentity(canonicalProductIdentity)
                    || canonicalReceiptIdentity == null
                    || !canonicalReceiptIdentity.matches("[0-9a-f]{64}")
                    || canonicalReceiptIdentity.equals("0".repeat(64))
                    || canonicalRevision < 0 || canonicalRevision == Long.MAX_VALUE) {
                throw new IllegalArgumentException("canonical base block witness is invalid");
            }
        }
    }

    /** Existing adapter surface retained for independently owned implementations. */
    public interface ContainerRetirementAdapter {
        ExplosionSettlementCommand.ContainerKind kind();

        default ContainerRetirementWitness preflightExact(Long worldId,
                List<ExplosionSettlementCommand.ContainerRetirement> retirements) {
            throw new IllegalStateException("split container retirement is not supported");
        }

        default ContainerRetirementWitness retireExact(Long worldId,
                List<ExplosionSettlementCommand.ContainerRetirement> retirements,
                ContainerRetirementWitness preflight) {
            throw new IllegalStateException("split container retirement is not supported");
        }
    }

    /**
     * Public digest carried by older independently owned adapters. Explosion settlement never
     * treats this value as authority; only {@link ContainerRetirementPlan} is accepted there.
     */
    public record ContainerRetirementWitness(
            ExplosionSettlementCommand.ContainerKind kind, String canonicalHash) {
        public ContainerRetirementWitness {
            if (kind == null || canonicalHash == null
                    || !canonicalHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("container retirement witness is invalid");
            }
        }
    }

    /**
     * Transaction-bound adapter base. The public method issues an opaque capability only after the
     * production authority has locked, validated, and retired the exact rows in the current
     * transaction. Callers cannot manufacture or replay that capability in another transaction.
     */
    public abstract static class TransactionalContainerRetirementAdapter
            implements ContainerRetirementAdapter {
        private final ExplosionSettlementCommand.ContainerKind kind;

        protected TransactionalContainerRetirementAdapter(
                ExplosionSettlementCommand.ContainerKind kind) {
            this.kind = Objects.requireNonNull(kind, "container retirement kind");
        }

        @Override
        public final ExplosionSettlementCommand.ContainerKind kind() { return kind; }

        public final ContainerRetirementPlan preflightAndRetireExact(Long worldId,
                List<ExplosionSettlementCommand.ContainerRetirement> retirements) {
            requireActiveRetirementTransaction();
            if (worldId == null || worldId <= 0 || worldId == Long.MAX_VALUE
                    || retirements == null || retirements.isEmpty()
                    || retirements.stream().anyMatch(value -> value == null
                            || value.kind() != kind)) {
                throw new IllegalArgumentException("exact container retirement batch is invalid");
            }
            List<ExplosionSettlementCommand.ContainerRetirement> exact = List.copyOf(retirements);
            String canonicalHash = canonicalContainerRetirementHash(kind, exact);
            Object transactionCapability = currentRetirementTransactionCapability();
            performExactRetirement(worldId, exact);
            if (TransactionSynchronizationManager.getResource(
                    CONTAINER_RETIREMENT_TRANSACTION_KEY) != transactionCapability) {
                throw new IllegalStateException("container retirement left its owning transaction");
            }
            return new ContainerRetirementPlan(
                    this, transactionCapability, worldId, kind, canonicalHash);
        }

        protected abstract void performExactRetirement(Long worldId,
                List<ExplosionSettlementCommand.ContainerRetirement> retirements);
    }

    /** Non-forgeable proof that one exact adapter batch retired inside this transaction. */
    public static final class ContainerRetirementPlan {
        private final TransactionalContainerRetirementAdapter owner;
        private final Object transactionCapability;
        private final Long worldId;
        private final ExplosionSettlementCommand.ContainerKind kind;
        private final String canonicalHash;
        private boolean consumed;

        private ContainerRetirementPlan(TransactionalContainerRetirementAdapter owner,
                Object transactionCapability, Long worldId,
                ExplosionSettlementCommand.ContainerKind kind, String canonicalHash) {
            this.owner = owner;
            this.transactionCapability = transactionCapability;
            this.worldId = worldId;
            this.kind = kind;
            this.canonicalHash = canonicalHash;
        }

        private void consume(TransactionalContainerRetirementAdapter expectedOwner,
                Long expectedWorldId,
                ExplosionSettlementCommand.ContainerKind expectedKind,
                List<ExplosionSettlementCommand.ContainerRetirement> retirements) {
            requireActiveRetirementTransaction();
            if (consumed || owner != expectedOwner || !Objects.equals(worldId, expectedWorldId)
                    || kind != expectedKind
                    || TransactionSynchronizationManager.getResource(
                            CONTAINER_RETIREMENT_TRANSACTION_KEY) != transactionCapability
                    || !canonicalHash.equals(
                            canonicalContainerRetirementHash(expectedKind, retirements))) {
                throw new IllegalStateException("container retirement capability is invalid");
            }
            consumed = true;
        }
    }

    private static final Object CONTAINER_RETIREMENT_TRANSACTION_KEY = new Object();

    private final WorldPrimedTntRepository repository;
    private final WorldBlockDiffRepository blockDiffRepository;
    private final BlockDiffFlusher blockDiffFlusher;
    private final WorldExplosionSettlementRepository explosionSettlements;
    private final GroundMutationSettlementService groundSettlements;
    private final WorldStore worlds;
    private final List<ContainerRetirementAdapter> containerRetirementAdapters;
    private final CanonicalBaseBlockAuthority canonicalBaseBlockAuthority;

    public PrimedTntPersistenceService(WorldPrimedTntRepository repository,
            WorldBlockDiffRepository blockDiffRepository) {
        this(repository, blockDiffRepository, null, null, null, null, List.of(), null);
    }

    public PrimedTntPersistenceService(WorldPrimedTntRepository repository,
            WorldBlockDiffRepository blockDiffRepository, BlockDiffFlusher blockDiffFlusher) {
        this(repository, blockDiffRepository, blockDiffFlusher,
                null, null, null, List.of(), null);
    }

    public PrimedTntPersistenceService(WorldPrimedTntRepository repository,
            WorldBlockDiffRepository blockDiffRepository, BlockDiffFlusher blockDiffFlusher,
            WorldExplosionSettlementRepository explosionSettlements,
            GroundMutationSettlementService groundSettlements, WorldStore worlds,
            List<ContainerRetirementAdapter> containerRetirementAdapters) {
        this(repository, blockDiffRepository, blockDiffFlusher, explosionSettlements,
                groundSettlements, worlds, containerRetirementAdapters, null);
    }

    @Autowired
    public PrimedTntPersistenceService(WorldPrimedTntRepository repository,
            WorldBlockDiffRepository blockDiffRepository, BlockDiffFlusher blockDiffFlusher,
            WorldExplosionSettlementRepository explosionSettlements,
            GroundMutationSettlementService groundSettlements, WorldStore worlds,
            List<ContainerRetirementAdapter> containerRetirementAdapters,
            CanonicalBaseBlockAuthority canonicalBaseBlockAuthority) {
        this.repository = repository;
        this.blockDiffRepository = blockDiffRepository;
        this.blockDiffFlusher = blockDiffFlusher;
        this.explosionSettlements = explosionSettlements;
        this.groundSettlements = groundSettlements;
        this.worlds = worlds;
        this.containerRetirementAdapters = containerRetirementAdapters == null
                ? List.of() : List.copyOf(containerRetirementAdapters);
        this.canonicalBaseBlockAuthority = canonicalBaseBlockAuthority;
    }

    /**
     * Commits one explosion plan as a single caller-visible durable transaction. The permanent
     * explosion receipt is deliberately flushed last; every earlier stale/collision outcome throws
     * and rolls the transaction back.
     */
    @Transactional
    public ExplosionSettlementOutcome settleExplosion(ExplosionSettlementCommand command) {
        if (command == null) throw new IllegalArgumentException("explosion command is required");
        requireExplosionWiring();

        Long worldId = command.worldId();
        var world = worlds.findByIdForShare(worldId)
                .orElseThrow(() -> new IllegalStateException("explosion world is absent"));
        if (!world.generationProfile().getBaselineId().equals(
                canonicalBaseBlockAuthority.canonicalProductIdentity(world))) {
            throw new IllegalStateException("explosion authority does not match world profile");
        }
        var prior = explosionSettlements.findByWorldIdAndExplosionId(
                worldId, command.explosionId());
        if (prior.isPresent()) {
            if (!prior.get().matches(command)) {
                throw new IllegalStateException("explosion settlement identity collision");
            }
            return ExplosionSettlementOutcome.IDEMPOTENT;
        }

        if (command.sourceKind() != ExplosionSettlementCommand.SourceKind.PRIMED_TNT) {
            throw new IllegalStateException(
                    "exact explosive mob source retirement authority is unavailable");
        }

        long receiptTntHighWater = receiptTntHighWater(worldId);
        long liveTntHighWater = liveTntHighWater(worldId);
        long reservedTntHighWater = Math.max(receiptTntHighWater, liveTntHighWater);

        EnumMap<ExplosionSettlementCommand.ContainerKind,
                TransactionalContainerRetirementAdapter> adapters =
                        resolveContainerAdapters(command.containerRetirements());
        List<BlockWrite> blockWrites = new ArrayList<>(command.blockTransitions().size());
        for (var transition : command.blockTransitions()) {
            var locked = blockDiffRepository.findLockedAt(worldId,
                    transition.x(), transition.y(), transition.z());
            WorldBlockDiff stored = locked == null ? null : locked.orElse(null);
            blockWrites.add(preflightBlock(world, transition, stored));
        }

        List<TntWrite> tntWrites = new ArrayList<>();
        for (var mutation : command.tntMutations()) {
            requireUsableTntId(mutation.tntId());
            var locked = repository.findLockedByWorldIdAndTntId(
                    worldId, mutation.tntId());
            WorldPrimedTnt stored = locked == null ? null : locked.orElse(null);
            if (mutation.expected() == null ? stored != null
                    : stored == null || !stored.matches(mutation.expected())) {
                throw new IllegalStateException("stale primed TNT " + mutation.tntId());
            }
            // An existing row may legitimately have been allocated by an earlier explosion
            // receipt and is now being consumed. Only a new row may not reuse either high-water.
            if (mutation.expected() == null && mutation.tntId() <= reservedTntHighWater) {
                throw new IllegalStateException("primed TNT identity was already reserved");
            }
            tntWrites.add(new TntWrite(stored, mutation));
        }

        GroundMutationSettlementService.SettlementPlan groundPlan = null;
        if (command.groundMutation() != null) {
            long reservedGroundHighWater = groundSettlements.highestReservedEntityId(worldId);
            if (reservedGroundHighWater < 0
                    || reservedGroundHighWater > GroundMutationCommand.MAX_GROUND_ENTITY_ID) {
                throw new IllegalStateException("stored ground entity high-water is invalid");
            }
            GroundMutationSettlementService.AuthenticatedExternalReservedHighWater external =
                    GroundMutationSettlementService.externalReservationFor(
                            command.groundMutation(), reservedGroundHighWater);
            groundPlan = groundSettlements.preflightJoiningTransaction(
                    command.groundMutation(), external);
            if (groundPlan == null) {
                throw new IllegalStateException("ground mutation preflight returned no plan");
            }
            if (groundPlan.outcome() != GroundMutationOutcome.COMMITTED) {
                throw new IllegalStateException(
                        "explosion ground mutation was not a fresh atomic plan");
            }
        }

        retireContainers(worldId, command.containerRetirements(), adapters);

        for (BlockWrite write : blockWrites) {
            var transition = write.transition();
            if (write.stored() == null) {
                blockDiffRepository.saveAndFlush(new WorldBlockDiff(world,
                        transition.x(), transition.y(), transition.z(),
                        transition.committedBlockType(), transition.committedBlockState()));
                continue;
            }
            int changed = blockDiffRepository.compareAndSetExact(worldId,
                    transition.x(), transition.y(), transition.z(),
                    transition.expectedBlockType(), transition.expectedBlockState(),
                    transition.committedBlockType(), transition.committedBlockState());
            if (changed != 1) {
                throw new IllegalStateException("explosion block CAS lost at "
                        + transition.x() + ":" + transition.y() + ":" + transition.z());
            }
        }

        for (TntWrite write : tntWrites) {
            if (write.mutation().committed() == null) {
                repository.delete(write.stored());
            } else if (write.stored() == null) {
                repository.saveAndFlush(new WorldPrimedTnt(worldId, write.mutation().committed()));
            } else {
                write.stored().apply(write.mutation().committed());
                repository.save(write.stored());
            }
        }
        repository.flush();

        if (groundPlan != null) {
            GroundMutationOutcome groundOutcome = groundSettlements.applyJoiningTransaction(groundPlan);
            if (groundOutcome != GroundMutationOutcome.COMMITTED) {
                throw new IllegalStateException("explosion ground mutation was not committed");
            }
        }

        explosionSettlements.saveAndFlush(new WorldExplosionSettlement(worldId,
                command.explosionId(), command.commandHash(),
                command.highestReservedGroundEntityId(), command.highestTntId()));
        return ExplosionSettlementOutcome.COMMITTED;
    }

    private record BlockWrite(ExplosionSettlementCommand.BlockTransition transition,
            WorldBlockDiff stored, BaseBlockWitness baseWitness) { }

    private record TntWrite(WorldPrimedTnt stored,
            ExplosionSettlementCommand.TntMutation mutation) { }

    private record TntReplacement(WorldPrimedTnt stored, PrimedTntSnapshot snapshot) { }

    private void requireExplosionWiring() {
        if (explosionSettlements == null || groundSettlements == null || worlds == null
                || canonicalBaseBlockAuthority == null) {
            throw new IllegalStateException("explosion settlement persistence is unavailable");
        }
    }

    private EnumMap<ExplosionSettlementCommand.ContainerKind,
            TransactionalContainerRetirementAdapter>
            resolveContainerAdapters(
                    List<ExplosionSettlementCommand.ContainerRetirement> retirements) {
        EnumMap<ExplosionSettlementCommand.ContainerKind,
                TransactionalContainerRetirementAdapter> resolved =
                new EnumMap<>(ExplosionSettlementCommand.ContainerKind.class);
        EnumSet<ExplosionSettlementCommand.ContainerKind> required =
                EnumSet.noneOf(ExplosionSettlementCommand.ContainerKind.class);
        for (var retirement : retirements) required.add(retirement.kind());
        for (ContainerRetirementAdapter candidate : containerRetirementAdapters) {
            if (candidate == null || candidate.kind() == null) {
                throw new IllegalStateException("container retirement adapter kind is required");
            }
            if (!(candidate instanceof TransactionalContainerRetirementAdapter adapter)) continue;
            if (resolved.put(adapter.kind(), adapter) != null) {
                throw new IllegalStateException("duplicate exact container retirement adapter "
                        + adapter.kind());
            }
        }
        for (var kind : required) {
            if (!resolved.containsKey(kind)) {
                throw new IllegalStateException("missing exact container retirement adapter " + kind);
            }
        }
        return resolved;
    }

    private void retireContainers(Long worldId,
            List<ExplosionSettlementCommand.ContainerRetirement> retirements,
            EnumMap<ExplosionSettlementCommand.ContainerKind,
                    TransactionalContainerRetirementAdapter> adapters) {
        EnumMap<ExplosionSettlementCommand.ContainerKind,
                List<ExplosionSettlementCommand.ContainerRetirement>> grouped =
                new EnumMap<>(ExplosionSettlementCommand.ContainerKind.class);
        for (var retirement : retirements) {
            grouped.computeIfAbsent(retirement.kind(), ignored -> new ArrayList<>()).add(retirement);
        }
        for (var kind : ExplosionSettlementCommand.ContainerKind.values()) {
            List<ExplosionSettlementCommand.ContainerRetirement> values = grouped.get(kind);
            if (values == null) continue;
            values.sort(Comparator.comparingInt(ExplosionSettlementCommand.ContainerRetirement::x)
                    .thenComparingInt(ExplosionSettlementCommand.ContainerRetirement::y)
                    .thenComparingInt(ExplosionSettlementCommand.ContainerRetirement::z)
                    .thenComparingLong(ExplosionSettlementCommand.ContainerRetirement::rowId)
                    .thenComparingLong(ExplosionSettlementCommand.ContainerRetirement::expectedRevision)
                    .thenComparing(ExplosionSettlementCommand.ContainerRetirement::aggregateId));
            List<ExplosionSettlementCommand.ContainerRetirement> ordered = List.copyOf(values);
            TransactionalContainerRetirementAdapter adapter = adapters.get(kind);
            ContainerRetirementPlan plan = adapter.preflightAndRetireExact(worldId, ordered);
            plan.consume(adapter, worldId, kind, ordered);
        }
    }

    private BlockWrite preflightBlock(WorldAccess world,
            ExplosionSettlementCommand.BlockTransition transition, WorldBlockDiff stored) {
        var provenance = transition.provenance();
        if (!world.generationProfile().getBaselineId().equals(provenance.canonicalProductIdentity())) {
            throw new IllegalStateException("explosion provenance belongs to another world profile");
        }
        if (stored != null) {
            if (stored.getBlockType() != transition.expectedBlockType()
                    || stored.getBlockState() != transition.expectedBlockState()
                    || provenance.overlayState()
                            != ExplosionSettlementCommand.OverlayState.DURABLE_OVERLAY
                    || !durableOverlayProvenance(world, stored).equals(provenance)) {
                throw staleBlock(transition, "stale explosion block overlay at ");
            }
            return new BlockWrite(transition, stored, null);
        }
        if (provenance.overlayState()
                != ExplosionSettlementCommand.OverlayState.CANONICAL_BASE) {
            throw staleBlock(transition, "missing explosion block overlay at ");
        }
        BaseBlockWitness witness = canonicalBaseBlockAuthority.preflightExact(world, transition);
        validateBaseBlockWitness(world.getId(), transition, witness);
        return new BlockWrite(transition, null, witness);
    }

    private static IllegalStateException staleBlock(
            ExplosionSettlementCommand.BlockTransition transition, String prefix) {
        return new IllegalStateException(prefix
                + transition.x() + ":" + transition.y() + ":" + transition.z());
    }

    private static void validateBaseBlockWitness(Long worldId,
            ExplosionSettlementCommand.BlockTransition transition, BaseBlockWitness witness) {
        var provenance = transition.provenance();
        if (witness == null || !Objects.equals(witness.worldId(), worldId)
                || witness.x() != transition.x() || witness.y() != transition.y()
                || witness.z() != transition.z()
                || witness.blockType() != transition.expectedBlockType()
                || witness.blockState() != transition.expectedBlockState()
                || !provenance.canonicalProductIdentity()
                        .equals(witness.canonicalProductIdentity())
                || !provenance.canonicalReceiptIdentity()
                        .equals(witness.canonicalReceiptIdentity())
                || provenance.canonicalRevision() != witness.canonicalRevision()
                || provenance.overlayState()
                        != ExplosionSettlementCommand.OverlayState.CANONICAL_BASE) {
            throw new IllegalStateException("canonical base block witness mismatch");
        }
    }

    /** Builds the only accepted provenance for a currently locked durable overlay row. */
    public static ExplosionSettlementCommand.BlockProvenance durableOverlayProvenance(
            WorldAccess world, WorldBlockDiff row) {
        Long worldId = world == null ? null : world.getId();
        if (worldId == null || worldId <= 0 || worldId == Long.MAX_VALUE || row == null
                || row.getId() == null || row.getId() <= 0 || row.getId() == Long.MAX_VALUE
                || row.getUpdatedAt() == null) {
            throw new IllegalArgumentException("persisted overlay authority is incomplete");
        }
        return new ExplosionSettlementCommand.BlockProvenance(worldId,
                row.getX(), row.getY(), row.getZ(), world.generationProfile().getBaselineId(),
                durableOverlayReceiptIdentity(worldId, row), row.getId(),
                ExplosionSettlementCommand.OverlayState.DURABLE_OVERLAY);
    }

    private static String durableOverlayReceiptIdentity(Long worldId, WorldBlockDiff row) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            putString(digest, "game-expert/durable-block-overlay/v1");
            putLong(digest, worldId); putLong(digest, row.getId());
            putInt(digest, row.getX()); putInt(digest, row.getY()); putInt(digest, row.getZ());
            putShort(digest, row.getBlockType()); putShort(digest, row.getBlockState());
            putNullableString(digest, row.getMobMutationKey());
            putString(digest, row.getUpdatedAt().toString());
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void requireActiveRetirementTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "exact container retirement requires one active transaction");
        }
    }

    private static Object currentRetirementTransactionCapability() {
        Object existing = TransactionSynchronizationManager.getResource(
                CONTAINER_RETIREMENT_TRANSACTION_KEY);
        if (existing != null) return existing;
        Object capability = new Object();
        TransactionSynchronizationManager.bindResource(
                CONTAINER_RETIREMENT_TRANSACTION_KEY, capability);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                Object current = TransactionSynchronizationManager.getResource(
                        CONTAINER_RETIREMENT_TRANSACTION_KEY);
                if (current == capability) {
                    TransactionSynchronizationManager.unbindResource(
                            CONTAINER_RETIREMENT_TRANSACTION_KEY);
                }
            }
        });
        return capability;
    }

    /** Computes the deterministic hash an exact container adapter must return. */
    public static String canonicalContainerRetirementHash(
            ExplosionSettlementCommand.ContainerKind kind,
            List<ExplosionSettlementCommand.ContainerRetirement> retirements) {
        if (kind == null || retirements == null || retirements.isEmpty()
                || retirements.stream().anyMatch(value -> value == null || value.kind() != kind)) {
            throw new IllegalArgumentException("container retirement hash input is invalid");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            putString(digest, "game-expert/container-retirement/v1");
            putString(digest, kind.name());
            putInt(digest, retirements.size());
            for (var retirement : retirements) {
                putInt(digest, retirement.x());
                putInt(digest, retirement.y());
                putInt(digest, retirement.z());
                putLong(digest, retirement.rowId());
                putLong(digest, retirement.expectedRevision());
                putString(digest, retirement.aggregateId());
                digest.update((byte) (retirement.pairedChest() ? 1 : 0));
                updateContainerStateDigest(digest, retirement.state());
                putInt(digest, retirement.drops().size());
                for (var drop : retirement.drops()) {
                    var source = drop.source();
                    putInt(digest, source.slot());
                    putShort(digest, source.itemType());
                    putInt(digest, source.count());
                    putInt(digest, source.durability());
                    putLong(digest, source.enchantments());
                    putInt(digest, source.mapId());
                    putInt(digest, source.shulkerId());
                    putNullableString(digest, source.bucketMobData());
                    putNullableString(digest, source.itemComponentData());
                    putInt(digest, source.progress());
                    putLong(digest, drop.outputGroundEntityId());
                }
                putInt(digest, retirement.expectedExperience());
                putInt(digest, retirement.outputGroundXpEntityIds().size());
                for (Long entityId : retirement.outputGroundXpEntityIds()) putLong(digest, entityId);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void updateContainerStateDigest(
            MessageDigest digest, ExplosionSettlementCommand.ContainerState state) {
        putString(digest, "game-expert/explosion-container-state/v3");
        if (state instanceof ExplosionSettlementCommand.ChestState chest) {
            putString(digest, "ChestState");
            putString(digest, "containerSize");
            putInt(digest, chest.containerSize());
        } else if (state instanceof ExplosionSettlementCommand.FurnaceState furnace) {
            putString(digest, "FurnaceState");
            putString(digest, "variantCode");
            putInt(digest, furnace.variantCode());
            putString(digest, "burnTicks");
            putInt(digest, furnace.burnTicks());
            putString(digest, "burnTotalTicks");
            putInt(digest, furnace.burnTotalTicks());
            putString(digest, "cookTicks");
            putInt(digest, furnace.cookTicks());
            putString(digest, "xpMilli");
            putInt(digest, furnace.xpMilli());
        } else if (state instanceof ExplosionSettlementCommand.BrewingState brewing) {
            putString(digest, "BrewingState");
            putString(digest, "fuel");
            putInt(digest, brewing.fuel());
            putString(digest, "brewTicks");
            putInt(digest, brewing.brewTicks());
            putString(digest, "brewingIngredient");
            putShort(digest, brewing.brewingIngredient());
        } else if (state instanceof ExplosionSettlementCommand.CampfireState) {
            putString(digest, "CampfireState");
        } else if (state instanceof ExplosionSettlementCommand.EnchantingTableState) {
            putString(digest, "EnchantingTableState");
        } else if (state instanceof ExplosionSettlementCommand.LecternState lectern) {
            putString(digest, "LecternState");
            putString(digest, "page");
            putInt(digest, lectern.page());
        } else {
            throw new IllegalStateException("unsupported exhaustive container state");
        }
    }

    private static void putString(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        putInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void putNullableString(MessageDigest digest, String value) {
        digest.update((byte) (value == null ? 0 : 1));
        if (value != null) putString(digest, value);
    }

    private static void putLong(MessageDigest digest, long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }

    private static void putInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    private static void putShort(MessageDigest digest, short value) {
        digest.update(ByteBuffer.allocate(Short.BYTES).putShort(value).array());
    }

    private long receiptTntHighWater(Long worldId) {
        if (explosionSettlements == null) return 0L;
        Long highest = explosionSettlements.findMaximumTntIdByWorldId(worldId);
        if (highest == null) return 0L;
        if (highest < 0 || highest >= Long.MAX_VALUE) {
            throw new IllegalStateException("stored TNT high-water is invalid");
        }
        return highest;
    }

    private long liveTntHighWater(Long worldId) {
        long highest = repository.findValidatedHighWaterByWorldId(worldId);
        if (highest < 0 || highest >= Long.MAX_VALUE) {
            throw new IllegalStateException("stored TNT identity is invalid");
        }
        return highest;
    }

    private long reservedTntHighWater(Long worldId) {
        return Math.max(receiptTntHighWater(worldId), liveTntHighWater(worldId));
    }

    @Transactional
    public long highestReservedTntId(Long worldId) {
        if (worldId == null || worldId <= 0 || worldId == Long.MAX_VALUE) {
            throw new IllegalArgumentException("world id must be positive");
        }
        if (worlds != null) {
            worlds.findByIdForShare(worldId)
                    .orElseThrow(() -> new IllegalStateException("TNT world is absent"));
        }
        return reservedTntHighWater(worldId);
    }

    private static void requireUsableTntId(long tntId) {
        if (tntId <= 0 || tntId >= Long.MAX_VALUE) {
            throw new IllegalStateException("TNT identity is invalid or exhausted");
        }
    }

    private static long checkedTntSuccessor(long highWater) {
        if (highWater < 0 || highWater >= Long.MAX_VALUE) {
            throw new IllegalStateException("TNT high-water is invalid or exhausted");
        }
        long next = Math.addExact(highWater, 1L);
        if (next >= Long.MAX_VALUE) {
            throw new IllegalStateException("TNT identity allocator is exhausted");
        }
        return next;
    }

    /**
     * Converts retired coordinate-bound PRIMED_TNT diffs before WorldRuntime reads its block overlay.
     * Their old fuse/motion was never durable, so the least destructive recovery is a fresh stationary
     * entity with a full fuse; the reserved block ID is replaced with AIR in the same transaction.
     */
    @Transactional
    public void migrateLegacyPrimedBlocks(Long worldId) {
        var legacy = blockDiffRepository.findByWorldIdAndBlockType(
                worldId, (short) Blocks.PRIMED_TNT);
        if (legacy.isEmpty()) return;
        long nextId = checkedTntSuccessor(reservedTntHighWater(worldId));
        long afterBatch = Math.addExact(nextId, legacy.size());
        if (afterBatch >= Long.MAX_VALUE) {
            throw new IllegalStateException("TNT identity allocator is exhausted");
        }
        legacy = legacy.stream().sorted(Comparator.comparingInt(WorldBlockDiff::getX)
                .thenComparingInt(WorldBlockDiff::getY)
                .thenComparingInt(WorldBlockDiff::getZ)).toList();
        List<WorldPrimedTnt> migrated = new ArrayList<>(legacy.size());
        for (var diff : legacy) {
            requireUsableTntId(nextId);
            migrated.add(new WorldPrimedTnt(worldId, new PrimedTntSnapshot(nextId++,
                    diff.getX() + 0.5, diff.getY(), diff.getZ() + 0.5,
                    0.0, 0.0, 0.0, 80)));
        }
        repository.saveAll(migrated);
        blockDiffRepository.replaceBlockType(worldId, (short) Blocks.PRIMED_TNT, (short) 0);
    }

    @Transactional(readOnly = true)
    public List<PrimedTntSnapshot> loadWorld(Long worldId) {
        return repository.findAllByWorldId(worldId).stream()
                .map(WorldPrimedTnt::toSnapshot)
                .sorted(Comparator.comparingLong(PrimedTntSnapshot::tntId))
                .toList();
    }

    @Transactional
    public void replaceWorld(Long worldId, Collection<PrimedTntSnapshot> snapshots) {
        List<WorldPrimedTnt> storedRows = repository.findAllByWorldId(worldId);
        long receiptHighWater = receiptTntHighWater(worldId);
        Map<Long, WorldPrimedTnt> storedByTntId = new HashMap<>();
        for (WorldPrimedTnt stored : storedRows) {
            requireUsableTntId(stored.getTntId());
            storedByTntId.put(stored.getTntId(), stored);
        }
        Set<Long> live = new HashSet<>();
        List<TntReplacement> changed = new ArrayList<>();
        for (PrimedTntSnapshot snapshot : snapshots) {
            requireUsableTntId(snapshot.tntId());
            if (!live.add(snapshot.tntId())) {
                throw new IllegalArgumentException("duplicate primed TNT id " + snapshot.tntId());
            }
            WorldPrimedTnt stored = storedByTntId.get(snapshot.tntId());
            if (stored == null) {
                if (snapshot.tntId() <= receiptHighWater) {
                    throw new IllegalStateException("primed TNT identity was already reserved");
                }
                changed.add(new TntReplacement(null, snapshot));
            } else if (!stored.matches(snapshot)) {
                changed.add(new TntReplacement(stored, snapshot));
            }
        }
        for (TntReplacement replacement : changed) {
            if (replacement.stored() != null) replacement.stored().apply(replacement.snapshot());
        }
        if (!changed.isEmpty()) {
            repository.saveAll(changed.stream().map(replacement -> replacement.stored() == null
                    ? new WorldPrimedTnt(worldId, replacement.snapshot()) : replacement.stored())
                    .toList());
        }
        List<WorldPrimedTnt> removed = storedRows.stream()
                .filter(stored -> !live.contains(stored.getTntId()))
                .toList();
        if (!removed.isEmpty()) {
            repository.deleteAll(removed);
        }
    }

    /**
     * 한 owner-thread 경계에서 떼어낸 블록 diff와 PrimedTnt delta를 한 DB 트랜잭션으로
     * 반영합니다. 체크포인트 경로에서는 전체 TNT 테이블을 읽거나 교체하지 않습니다.
     */
    @Transactional
    public void applyCheckpoint(Long worldId,
            Map<BlockPos, BlockDiffBuffer.Change> blockChanges,
            Collection<PrimedTntSnapshot> changedSnapshots,
            Collection<Long> removedTntIds) {
        long receiptHighWater = receiptTntHighWater(worldId);
        long liveHighWater = liveTntHighWater(worldId);
        Set<Long> preflightChangedIds = new HashSet<>();
        for (PrimedTntSnapshot snapshot : changedSnapshots) {
            requireUsableTntId(snapshot.tntId());
            if (!preflightChangedIds.add(snapshot.tntId())) {
                throw new IllegalArgumentException("duplicate primed TNT id " + snapshot.tntId());
            }
        }
        Map<Long, WorldPrimedTnt> existingChanged = preflightChangedIds.isEmpty() ? Map.of()
                : repository.findAllByWorldIdAndTntIdIn(worldId, preflightChangedIds).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                WorldPrimedTnt::getTntId, value -> value));
        for (PrimedTntSnapshot snapshot : changedSnapshots) {
            if (!existingChanged.containsKey(snapshot.tntId())
                    && snapshot.tntId() <= Math.max(receiptHighWater, liveHighWater)) {
                throw new IllegalStateException("primed TNT identity was already reserved");
            }
        }

        List<Long> removed = new ArrayList<>(removedTntIds.size());
        Set<Long> removedSet = new HashSet<>();
        for (Long tntId : removedTntIds) {
            if (tntId == null) throw new IllegalArgumentException("TNT identity is required");
            requireUsableTntId(tntId);
            if (!removedSet.add(tntId)) {
                throw new IllegalArgumentException("duplicate removed primed TNT id " + tntId);
            }
            removed.add(tntId);
        }
        for (Long tntId : preflightChangedIds) {
            if (removedSet.contains(tntId)) {
                throw new IllegalArgumentException("primed TNT cannot be changed and removed together: "
                        + tntId);
            }
        }
        removed.sort(Long::compareTo);
        if (!blockChanges.isEmpty()) {
            if (blockDiffFlusher == null) {
                throw new IllegalStateException("block diff checkpoint writer is unavailable");
            }
            blockDiffFlusher.writeDetached(worldId, blockChanges);
        }

        if (!removed.isEmpty()) {
            repository.deleteAllByWorldIdAndTntIdIn(worldId, removed);
        }

        List<PrimedTntSnapshot> changed = changedSnapshots.stream()
                .sorted(Comparator.comparingLong(PrimedTntSnapshot::tntId))
                .toList();
        if (changed.isEmpty()) return;
        Set<Long> changedIds = new HashSet<>(preflightChangedIds);
        Map<Long, WorldPrimedTnt> storedById = new HashMap<>();
        for (WorldPrimedTnt stored : repository.findAllByWorldIdAndTntIdIn(worldId, changedIds)) {
            storedById.put(stored.getTntId(), stored);
        }
        List<WorldPrimedTnt> writes = new ArrayList<>(changed.size());
        for (PrimedTntSnapshot snapshot : changed) {
            WorldPrimedTnt stored = storedById.get(snapshot.tntId());
            if (stored == null) {
                writes.add(new WorldPrimedTnt(worldId, snapshot));
            } else if (!stored.matches(snapshot)) {
                stored.apply(snapshot);
                writes.add(stored);
            }
        }
        if (!writes.isEmpty()) repository.saveAll(writes);
    }
}
