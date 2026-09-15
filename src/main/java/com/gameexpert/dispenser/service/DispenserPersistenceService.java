package com.gameexpert.dispenser.service;

import com.gameexpert.dispenser.entity.WorldDispenser;
import com.gameexpert.dispenser.repository.WorldDispenserRepository;
import com.gameexpert.chest.entity.ChestItem;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootAggregateMutation;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootAggregatePersistence;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.state.service.inventory.StaleInventoryMutationException;
import com.gameexpert.tnt.dto.ExplosionSettlementCommand;
import com.gameexpert.tnt.service.PrimedTntPersistenceService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Transaction-joining generated-dispenser installation boundary. */
@Service
@RequiredArgsConstructor
public class DispenserPersistenceService
        implements PrimedTntPersistenceService.ContainerRetirementAdapter {
    public enum GeneratedInstallOutcome { COMMITTED, IDEMPOTENT }

    private final WorldDispenserRepository repository;

    private record DispenserPosition(long worldId, int x, int y, int z) { }

    private static final class PreflightState {
        private final Long worldId;
        private final String canonicalHash;
        private final PrimedTntPersistenceService.ContainerRetirementWitness witness;
        private final boolean alreadyRetired;
        private boolean retired;

        private PreflightState(Long worldId, String canonicalHash,
                PrimedTntPersistenceService.ContainerRetirementWitness witness,
                boolean alreadyRetired) {
            this.worldId = worldId;
            this.canonicalHash = canonicalHash;
            this.witness = witness;
            this.alreadyRetired = alreadyRetired;
        }

        private boolean matches(Long candidateWorldId, String candidateHash) {
            return Objects.equals(worldId, candidateWorldId)
                    && canonicalHash.equals(candidateHash);
        }
    }

    private record RetirementScan(List<WorldDispenser> locked, boolean allAbsent) { }

    @Override
    public ExplosionSettlementCommand.ContainerKind kind() {
        return ExplosionSettlementCommand.ContainerKind.DISPENSER;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public GeneratedInstallOutcome installGeneratedJoiningTransaction(Long worldId,
            int x, int y, int z, String installationId, String installationFingerprint) {
        WorldDispenser existing = repository
                .findLockedByWorldIdAndPosXAndPosYAndPosZ(worldId, x, y, z)
                .orElse(null);
        if (existing != null) {
            existing.requireSameGeneratedInstallation(installationId, installationFingerprint);
            return GeneratedInstallOutcome.IDEMPOTENT;
        }
        repository.save(new WorldDispenser(worldId, x, y, z,
                installationId, installationFingerprint));
        return GeneratedInstallOutcome.COMMITTED;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CanonicalLootAggregatePersistence.Outcome installCanonicalLootJoiningTransaction(
            CanonicalLootAggregateMutation mutation, List<ChestItem> fixedItems) {
        WorldDispenser entity = repository
                .findLockedByWorldIdAndPosXAndPosYAndPosZ(
                        mutation.worldId(), mutation.x(), mutation.y(), mutation.z())
                .orElse(null);
        if (entity == null) return CanonicalLootAggregatePersistence.Outcome.REJECTED;
        try {
            boolean committed = entity.installCanonicalLoot(
                    mutation.laneInstallationIdentity(), mutation.installationFingerprint(),
                    mutation.definitionFingerprint(), mutation.resultFingerprint(),
                    mutation.resolution().encode(), fixedItems);
            repository.save(entity);
            return committed ? CanonicalLootAggregatePersistence.Outcome.COMMITTED
                    : CanonicalLootAggregatePersistence.Outcome.ALREADY_COMMITTED;
        } catch (IllegalArgumentException | IllegalStateException conflict) {
            return CanonicalLootAggregatePersistence.Outcome.REJECTED;
        }
    }

    /**
     * Locks and validates every exact dispenser row before any explosion authority may write.
     * The returned value is only the public digest; the usable capability is transaction-bound.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PrimedTntPersistenceService.ContainerRetirementWitness preflightExact(
            Long worldId, List<ExplosionSettlementCommand.ContainerRetirement> retirements) {
        requireTransactionSynchronization();
        requireWorldId(worldId);
        List<ExplosionSettlementCommand.ContainerRetirement> exact =
                validateRetirements(worldId, retirements);
        String canonicalHash = PrimedTntPersistenceService.canonicalContainerRetirementHash(
                kind(), exact);
        PreflightState prior = currentPreflight();
        if (prior != null) {
            if (!prior.matches(worldId, canonicalHash)) {
                throw new IllegalStateException(
                        "multiple dispenser preflights in one transaction are not allowed");
            }
            return prior.witness;
        }

        RetirementScan scan = lockAndValidate(worldId, exact);
        PrimedTntPersistenceService.ContainerRetirementWitness witness =
                new PrimedTntPersistenceService.ContainerRetirementWitness(kind(), canonicalHash);
        bindPreflight(new PreflightState(worldId, canonicalHash, witness, scan.allAbsent()));
        return witness;
    }

    /**
     * Re-locks and revalidates the same transaction-bound preflight, then retires exactly once.
     * A value-equal witness created by a caller is rejected because only the bound instance is
     * accepted; a committed absent-row replay is a no-op after its own exact preflight.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PrimedTntPersistenceService.ContainerRetirementWitness retireExact(
            Long worldId, List<ExplosionSettlementCommand.ContainerRetirement> retirements,
            PrimedTntPersistenceService.ContainerRetirementWitness preflight) {
        requireTransactionSynchronization();
        requireWorldId(worldId);
        PreflightState state = currentPreflight();
        if (state == null || preflight == null || preflight != state.witness) {
            throw new IllegalStateException(
                    "dispenser retirement requires its bound preflight witness");
        }
        List<ExplosionSettlementCommand.ContainerRetirement> exact =
                validateRetirements(worldId, retirements);
        String canonicalHash = PrimedTntPersistenceService.canonicalContainerRetirementHash(
                kind(), exact);
        if (!state.matches(worldId, canonicalHash)) {
            throw new StaleInventoryMutationException("tampered exact dispenser retirement");
        }
        if (state.retired) return state.witness;

        RetirementScan scan = lockAndValidate(worldId, exact);
        if (scan.allAbsent()) {
            if (!state.alreadyRetired) {
                throw staleRetirement(exact.getFirst());
            }
            state.retired = true;
            return state.witness;
        }
        if (state.alreadyRetired) {
            throw staleRetirement(exact.getFirst());
        }
        if (scan.locked().size() != exact.size()) {
            throw staleRetirement(exact.getFirst());
        }
        repository.deleteAll(scan.locked());
        state.retired = true;
        return state.witness;
    }

    private RetirementScan lockAndValidate(Long worldId,
            List<ExplosionSettlementCommand.ContainerRetirement> retirements) {
        List<WorldDispenser> locked = new ArrayList<>(retirements.size());
        boolean allAbsent = true;
        for (ExplosionSettlementCommand.ContainerRetirement retirement : sorted(retirements)) {
            List<ChestItem> expectedItems = expectedItems(retirement);
            WorldDispenser entity = repository
                    .findLockedByWorldIdAndPosXAndPosYAndPosZ(
                            worldId, retirement.x(), retirement.y(), retirement.z())
                    .orElse(null);
            if (entity == null) {
                WorldDispenser rowAtAnotherIdentity = repository.findLockedById(retirement.rowId())
                        .orElse(null);
                if (rowAtAnotherIdentity != null) {
                    throw staleRetirement(retirement);
                }
                continue;
            }
            allAbsent = false;
            if (!entity.matchesExact(worldId, retirement.x(), retirement.y(), retirement.z(),
                    retirement.rowId(), retirement.expectedRevision(), retirement.aggregateId(),
                    expectedItems)) {
                throw staleRetirement(retirement);
            }
            locked.add(entity);
        }
        if (!allAbsent && locked.size() != retirements.size()) {
            throw staleRetirement(sorted(retirements).getFirst());
        }
        return new RetirementScan(List.copyOf(locked), allAbsent);
    }

    private List<ExplosionSettlementCommand.ContainerRetirement> validateRetirements(
            Long worldId, List<ExplosionSettlementCommand.ContainerRetirement> retirements) {
        if (retirements == null || retirements.isEmpty()) {
            throw new IllegalArgumentException("exact dispenser retirements are required");
        }
        Set<Long> rowIds = new HashSet<>();
        Set<DispenserPosition> positions = new HashSet<>();
        List<ExplosionSettlementCommand.ContainerRetirement> exact = new ArrayList<>(
                retirements.size());
        for (ExplosionSettlementCommand.ContainerRetirement retirement : retirements) {
            if (retirement == null || retirement.kind() != kind()
                    || retirement.pairedChest()) {
                throw new IllegalArgumentException("invalid exact dispenser retirement");
            }
            if (!rowIds.add(retirement.rowId())
                    || !positions.add(new DispenserPosition(
                            worldId, retirement.x(), retirement.y(),
                            retirement.z()))) {
                throw new IllegalArgumentException("duplicate exact dispenser retirement");
            }
            expectedItems(retirement);
            exact.add(retirement);
        }
        return List.copyOf(exact);
    }

    private static List<ExplosionSettlementCommand.ContainerRetirement> sorted(
            List<ExplosionSettlementCommand.ContainerRetirement> retirements) {
        ArrayList<ExplosionSettlementCommand.ContainerRetirement> ordered =
                new ArrayList<>(retirements);
        ordered.sort(Comparator.comparingInt(ExplosionSettlementCommand.ContainerRetirement::x)
                .thenComparingInt(ExplosionSettlementCommand.ContainerRetirement::y)
                .thenComparingInt(ExplosionSettlementCommand.ContainerRetirement::z)
                .thenComparingLong(ExplosionSettlementCommand.ContainerRetirement::rowId));
        return ordered;
    }

    private static List<ChestItem> expectedItems(
            ExplosionSettlementCommand.ContainerRetirement retirement) {
        if (retirement.drops() == null
                || retirement.drops().size() != WorldDispenser.CONTAINER_SIZE) {
            throw new IllegalArgumentException("dispenser retirement must cover nine slots");
        }
        List<ChestItem> expected = new ArrayList<>();
        for (int slot = 0; slot < WorldDispenser.CONTAINER_SIZE; slot++) {
            ExplosionSettlementCommand.ContainerDrop drop = retirement.drops().get(slot);
            if (drop == null || drop.source() == null || drop.source().slot() != slot
                    || drop.source().progress() != 0) {
                throw new IllegalArgumentException("dispenser retirement slots are not canonical");
            }
            ExplosionSettlementCommand.ContainerStack source = drop.source();
            if (source.isEmpty()) continue;
            expected.add(new ChestItem(slot, source.itemType(), source.count(),
                    PlayerInventory.isDurable(source.itemType()) ? source.durability() : null,
                    source.enchantments() == 0L ? null : source.enchantments(),
                    source.mapId() == 0 ? null : source.mapId(),
                    source.shulkerId() == 0 ? null : source.shulkerId(),
                    source.bucketMobData(), source.itemComponentData()));
        }
        return List.copyOf(expected);
    }

    private PreflightState currentPreflight() {
        Object resource = TransactionSynchronizationManager.getResource(this);
        return resource instanceof PreflightState state ? state : null;
    }

    private void bindPreflight(PreflightState state) {
        TransactionSynchronizationManager.bindResource(this, state);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (TransactionSynchronizationManager.hasResource(DispenserPersistenceService.this)) {
                    TransactionSynchronizationManager.unbindResource(
                            DispenserPersistenceService.this);
                }
            }
        });
    }

    private static void requireTransactionSynchronization() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "exact dispenser retirement requires one active transaction");
        }
    }

    private static void requireWorldId(Long worldId) {
        if (worldId == null || worldId <= 0L || worldId == Long.MAX_VALUE) {
            throw new IllegalArgumentException("positive world ID required");
        }
    }

    private static StaleInventoryMutationException staleRetirement(
            ExplosionSettlementCommand.ContainerRetirement retirement) {
        return new StaleInventoryMutationException("stale exact dispenser retirement at "
                + retirement.x() + ":" + retirement.y() + ":" + retirement.z());
    }
}
