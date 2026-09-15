package com.gameexpert.brewing.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.gameexpert.brewing.entity.WorldBrewingStand;
import com.gameexpert.brewing.repository.WorldBrewingStandRepository;
import com.gameexpert.engine.BrewingInventory;
import com.gameexpert.engine.BrewingStorage;
import com.gameexpert.engine.PersistenceExecutor;
import com.gameexpert.state.service.inventory.InventoryMutationTarget;
import com.gameexpert.state.service.inventory.StaleInventoryMutationException;
import com.gameexpert.terrain.Blocks;

import lombok.RequiredArgsConstructor;

/** Persistence boundary for coordinate-owned brewing stands. */
@Service
@RequiredArgsConstructor
public class BrewingPersistenceService {

    public enum Outcome { COMMITTED, IDEMPOTENT, STALE }
    public enum GeneratedInstallOutcome { COMMITTED, IDEMPOTENT }

    /**
     * Detached witness for one exact persisted brewing-stand row.
     * The row ID prevents a delete/recreate at the same coordinate from being retired, while the
     * revision and every serialized brewing field prevent retiring contents changed in place.
     */
    public record ExactRetirementTarget(long rowId, int x, int y, int z,
            long expectedRevision, short[] expectedItemTypes, int[] expectedCounts,
            int expectedFuel, int expectedBrewTicks, short expectedBrewingIngredient) {
        public ExactRetirementTarget {
            if (rowId <= 0L || expectedRevision < 0L
                    || expectedItemTypes == null
                    || expectedItemTypes.length != BrewingInventory.SLOTS
                    || expectedCounts == null || expectedCounts.length != BrewingInventory.SLOTS) {
                throw new IllegalArgumentException("invalid exact brewing retirement identity");
            }
            expectedItemTypes = expectedItemTypes.clone();
            expectedCounts = expectedCounts.clone();
        }

        @Override public short[] expectedItemTypes() { return expectedItemTypes.clone(); }
        @Override public int[] expectedCounts() { return expectedCounts.clone(); }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ExactRetirementTarget target)) return false;
            return rowId == target.rowId && x == target.x && y == target.y && z == target.z
                    && expectedRevision == target.expectedRevision
                    && expectedFuel == target.expectedFuel
                    && expectedBrewTicks == target.expectedBrewTicks
                    && expectedBrewingIngredient == target.expectedBrewingIngredient
                    && Arrays.equals(expectedItemTypes, target.expectedItemTypes)
                    && Arrays.equals(expectedCounts, target.expectedCounts);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(rowId, x, y, z, expectedRevision, expectedFuel,
                    expectedBrewTicks, expectedBrewingIngredient);
            result = 31 * result + Arrays.hashCode(expectedItemTypes);
            return 31 * result + Arrays.hashCode(expectedCounts);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(BrewingPersistenceService.class);
    private final WorldBrewingStandRepository repository;
    private final PersistenceExecutor persistenceExecutor;
    private final TransactionTemplate transactionTemplate;
    /** Binds a resident snapshot to one durable row incarnation and its complete last-known state. */
    private final Map<BrewingPosition, RowBinding> rowBindings = new ConcurrentHashMap<>();
    /** Keeps a loaded or flushed resident object tied to the row binding it represents. */
    private final Map<BrewingPosition, BrewingInventory> residentBindings =
            new ConcurrentHashMap<>();

    private record BrewingPosition(long worldId, int x, int y, int z) { }

    private record RowBinding(long id, long revision, short[] itemTypes, int[] counts,
            int fuel, int brewTicks, short brewingIngredient) {
        private RowBinding {
            if (id <= 0L || revision < 0L || itemTypes == null
                    || itemTypes.length != BrewingInventory.SLOTS
                    || counts == null || counts.length != BrewingInventory.SLOTS) {
                throw new IllegalArgumentException("invalid brewing row binding");
            }
            itemTypes = itemTypes.clone();
            counts = counts.clone();
        }

        @Override public short[] itemTypes() { return itemTypes.clone(); }
        @Override public int[] counts() { return counts.clone(); }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RowBinding binding)) return false;
            return id == binding.id && revision == binding.revision
                    && fuel == binding.fuel && brewTicks == binding.brewTicks
                    && brewingIngredient == binding.brewingIngredient
                    && Arrays.equals(itemTypes, binding.itemTypes)
                    && Arrays.equals(counts, binding.counts);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(id, revision, fuel, brewTicks, brewingIngredient);
            result = 31 * result + Arrays.hashCode(itemTypes);
            return 31 * result + Arrays.hashCode(counts);
        }
    }

    private record PendingSave(BrewingPosition position, WorldBrewingStand entity,
            BrewingInventory resident) { }

    private record BindingEffect(BrewingPosition position, RowBinding binding,
            BrewingInventory resident, boolean remove) { }

    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceExactSnapshotJoiningTransaction(Long worldId,
            InventoryMutationTarget.Brewing snapshot) {
        InventoryMutationTarget.Position pos = snapshot.position();
        WorldBrewingStand entity = repository
                .findLockedByWorldIdAndPosXAndPosYAndPosZ(worldId, pos.x(), pos.y(), pos.z())
                .orElseGet(() -> new WorldBrewingStand(worldId, pos.x(), pos.y(), pos.z()));
        if (!entity.replaceIfNewer(snapshot.itemTypes(), snapshot.counts(), snapshot.components(),
                snapshot.fuel(), snapshot.brewTicks(), snapshot.brewingIngredient(),
                snapshot.revision())) {
            throw new StaleInventoryMutationException("brewing stand " + pos);
        }
        repository.save(entity);
        rememberBindingAfterCommit(worldId, pos.x(), pos.y(), pos.z(), entity);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public GeneratedInstallOutcome installGeneratedWeaknessStandJoiningTransaction(Long worldId,
            int x, int y, int z, String installationId,
            String installationFingerprint) {
        WorldBrewingStand entity = repository
                .findLockedByWorldIdAndPosXAndPosYAndPosZ(worldId, x, y, z)
                .orElse(null);
        if (entity != null) {
            entity.requireSameGeneratedInstallation(installationId, installationFingerprint);
            rememberBindingAfterCommit(worldId, x, y, z, entity);
            return GeneratedInstallOutcome.IDEMPOTENT;
        }
        entity = new WorldBrewingStand(worldId, x, y, z);
        entity.claimGeneratedInstallation(
                installationId, installationFingerprint, (short) Blocks.SPLASH_POTION_WEAKNESS);
        repository.save(entity);
        rememberBindingAfterCommit(worldId, x, y, z, entity);
        return GeneratedInstallOutcome.COMMITTED;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean replaceExactSnapshotAtExpectedRevisionJoiningTransaction(Long worldId,
            InventoryMutationTarget.Brewing snapshot, long expectedRevision) {
        InventoryMutationTarget.Position pos = snapshot.position();
        WorldBrewingStand entity = repository
                .findLockedByWorldIdAndPosXAndPosYAndPosZ(worldId, pos.x(), pos.y(), pos.z())
                .orElse(null);
        long persistedRevision = entity == null ? 0 : entity.getPersistenceRevision();
        if (persistedRevision != expectedRevision) return false;
        requireNextRevision(snapshot.revision(), expectedRevision);
        if (entity == null) entity = new WorldBrewingStand(worldId, pos.x(), pos.y(), pos.z());
        if (!entity.replaceIfNewer(snapshot.itemTypes(), snapshot.counts(), snapshot.components(),
                snapshot.fuel(), snapshot.brewTicks(), snapshot.brewingIngredient(),
                snapshot.revision())) {
            throw new IllegalStateException("validated brewing revision changed in transaction");
        }
        repository.save(entity);
        rememberBindingAfterCommit(worldId, pos.x(), pos.y(), pos.z(), entity);
        return true;
    }

    /**
     * Retires one exact brewing-stand row in the caller's transaction.
     * The coordinate query takes the existing pessimistic row lock, including for revision-zero
     * rows, and every identity/state field is checked before the delete is issued.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void retireExactJoiningTransaction(Long worldId, ExactRetirementTarget target) {
        requireWorldId(worldId);
        if (target == null) throw new IllegalArgumentException("exact brewing target required");
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "exact brewing retirement requires transaction synchronization");
        }

        WorldBrewingStand entity = repository
                .findLockedByWorldIdAndPosXAndPosYAndPosZ(
                        worldId, target.x(), target.y(), target.z())
                .orElse(null);
        if (!matchesExact(entity, worldId, target)) {
            throw new StaleInventoryMutationException(
                    "stale exact brewing retirement at " + target.x() + ":"
                            + target.y() + ":" + target.z());
        }

        repository.delete(entity);
        RowBinding binding = new RowBinding(target.rowId(), target.expectedRevision(),
                target.expectedItemTypes(), target.expectedCounts(), target.expectedFuel(),
                target.expectedBrewTicks(), target.expectedBrewingIngredient());
        BrewingPosition position = new BrewingPosition(worldId, target.x(), target.y(), target.z());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                applyBindingEffect(new BindingEffect(position, binding, null, true));
            }
        });
    }

    /** Persists one tick result synchronously; callers publish/install only after COMMITTED/IDEMPOTENT. */
    public Outcome persistTick(Long worldId, long expectedRevision,
            InventoryMutationTarget.Brewing snapshot) {
        return transactionTemplate.execute(ignored -> persistTickTransaction(
                worldId, expectedRevision, snapshot));
    }

    private Outcome persistTickTransaction(Long worldId, long expectedRevision,
            InventoryMutationTarget.Brewing snapshot) {
        InventoryMutationTarget.Position pos = snapshot.position();
        WorldBrewingStand entity = repository
                .findLockedByWorldIdAndPosXAndPosYAndPosZ(worldId, pos.x(), pos.y(), pos.z())
                .orElse(null);
        long persistedRevision = entity == null ? 0 : entity.getPersistenceRevision();
        if (persistedRevision == snapshot.revision()) {
            if (entity == null || !entity.matches(snapshot.itemTypes(), snapshot.counts(),
                    snapshot.components(), snapshot.fuel(), snapshot.brewTicks(),
                    snapshot.brewingIngredient(), snapshot.revision())) return Outcome.STALE;
            rememberBindingAfterCommit(worldId, pos.x(), pos.y(), pos.z(), entity);
            return Outcome.IDEMPOTENT;
        }
        if (persistedRevision != expectedRevision) return Outcome.STALE;
        requireNextRevision(snapshot.revision(), expectedRevision);
        if (entity == null) entity = new WorldBrewingStand(worldId, pos.x(), pos.y(), pos.z());
        if (!entity.replaceIfNewer(snapshot.itemTypes(), snapshot.counts(), snapshot.components(),
                snapshot.fuel(), snapshot.brewTicks(), snapshot.brewingIngredient(),
                snapshot.revision())) {
            return Outcome.STALE;
        }
        repository.save(entity);
        rememberBindingAfterCommit(worldId, pos.x(), pos.y(), pos.z(), entity);
        return Outcome.COMMITTED;
    }

    public void loadWorld(Long worldId, BrewingStorage storage) {
        requireWorldId(worldId);
        List<BindingEffect> loaded = new ArrayList<>();
        for (WorldBrewingStand entity : repository.findAllByWorldId(worldId)) {
            BrewingInventory stand = new BrewingInventory();
            stand.restore(entity.itemTypes(), entity.counts(), entity.components(),
                    entity.getFuel(), entity.getBrewTicks(), entity.getBrewingIngredient());
            stand.restorePersistenceRevision(entity.getPersistenceRevision());
            storage.load(entity.getPosX(), entity.getPosY(), entity.getPosZ(), stand);
            RowBinding binding = bindingFrom(entity);
            if (binding != null) {
                loaded.add(new BindingEffect(
                        new BrewingPosition(worldId, entity.getPosX(), entity.getPosY(), entity.getPosZ()),
                        binding, stand, false));
            }
        }
        publishLoadedBindings(worldId, loaded);
    }

    /**
     * Captures a detached exact-row witness from the resident inventory on its owner thread.
     * This method deliberately performs no repository access and accepts only a component-complete
     * state that still matches the binding established by a committed load/save.
     */
    public ExactRetirementTarget captureExactRetirementTarget(Long worldId,
            int x, int y, int z, BrewingInventory resident) {
        requireWorldId(worldId);
        if (resident == null) {
            throw new StaleInventoryMutationException(
                    "missing resident brewing stand at " + x + ":" + y + ":" + z);
        }
        RowBinding binding = bindingFor(worldId, x, y, z);
        if (binding == null) {
            throw new StaleInventoryMutationException(
                    "unbound resident brewing stand at " + x + ":" + y + ":" + z);
        }
        BrewingPosition position = new BrewingPosition(worldId, x, y, z);
        if (residentBindings.get(position) != resident) {
            throw new StaleInventoryMutationException(
                    "replaced resident brewing stand at " + x + ":" + y + ":" + z);
        }
        BrewingInventory.Snapshot captured = resident.snapshot();
        if (!matchesBinding(binding, captured)) {
            throw new StaleInventoryMutationException(
                    "stale resident brewing stand at " + x + ":" + y + ":" + z);
        }
        return new ExactRetirementTarget(binding.id(), x, y, z, binding.revision(),
                captured.itemTypes(), captured.counts(), captured.fuel(), captured.brewTicks(),
                captured.brewingIngredient());
    }

    public void flushDirty(Long worldId, BrewingStorage storage) {
        List<Snapshot> snapshots = new ArrayList<>();
        for (int[] pos : storage.drainDirty()) {
            BrewingInventory stand = storage.peekAt(pos[0], pos[1], pos[2]);
            RowBinding binding = bindingFor(worldId, pos[0], pos[1], pos[2]);
            snapshots.add(stand == null ? Snapshot.deleted(pos[0], pos[1], pos[2], binding)
                    : Snapshot.present(pos[0], pos[1], pos[2], stand.snapshot(), binding, stand));
        }
        if (snapshots.isEmpty()) return;
        boolean accepted = persistenceExecutor.trySubmit(() -> {
            try {
                List<BindingEffect> effects = new ArrayList<>();
                transactionTemplate.executeWithoutResult(ignored -> persist(worldId, snapshots, effects));
            } catch (RuntimeException failed) {
                restoreDirty(storage, snapshots);
                log.warn("world {} brewing stand flush failed; restored {} dirty coordinates",
                        worldId, snapshots.size(), failed);
            }
        });
        if (!accepted) {
            restoreDirty(storage, snapshots);
            log.warn("world {} brewing stand flush rejected; restored {} dirty coordinates",
                    worldId, snapshots.size());
        }
    }

    @Transactional
    public void deleteWorld(Long worldId) {
        repository.deleteAllByWorldId(worldId);
        forgetBindingsAfterCommit(worldId);
    }

    private void persist(Long worldId, List<Snapshot> snapshots, List<BindingEffect> effects) {
        requireWorldId(worldId);
        List<int[]> positions = snapshots.stream()
                .map(snapshot -> new int[] { snapshot.x, snapshot.y, snapshot.z }).toList();
        Map<String, WorldBrewingStand> existing = new HashMap<>();
        for (WorldBrewingStand entity : repository.findDirtyByWorldId(worldId, positions)) {
            existing.put(positionKey(entity.getPosX(), entity.getPosY(), entity.getPosZ()), entity);
        }
        List<WorldBrewingStand> changed = new ArrayList<>();
        List<WorldBrewingStand> deleted = new ArrayList<>();
        List<PendingSave> pendingSaves = new ArrayList<>();
        for (Snapshot snapshot : snapshots) {
            WorldBrewingStand entity = existing.get(positionKey(snapshot.x, snapshot.y, snapshot.z));
            if (snapshot.binding != null && !matchesBinding(entity, worldId, snapshot.x,
                    snapshot.y, snapshot.z, snapshot.binding)) {
                throw new StaleInventoryMutationException(
                        "stale brewing flush at " + snapshot.x + ":"
                                + snapshot.y + ":" + snapshot.z);
            }
            if (snapshot.deleted) {
                if (entity != null) {
                    deleted.add(entity);
                    effects.add(new BindingEffect(
                            new BrewingPosition(worldId, snapshot.x, snapshot.y, snapshot.z),
                            bindingFrom(entity), null, true));
                }
                continue;
            }
            boolean created = entity == null;
            if (created) entity = new WorldBrewingStand(worldId, snapshot.x, snapshot.y, snapshot.z);
            if (entity.replaceIfNewer(snapshot.itemTypes, snapshot.counts, snapshot.components,
                    snapshot.fuel, snapshot.brewTicks, snapshot.brewingIngredient,
                    snapshot.revision)) {
                changed.add(entity);
                pendingSaves.add(new PendingSave(
                        new BrewingPosition(worldId, snapshot.x, snapshot.y, snapshot.z), entity,
                        snapshot.resident));
            }
        }
        if (!deleted.isEmpty()) repository.deleteAll(deleted);
        if (!changed.isEmpty()) repository.saveAll(changed);
        for (PendingSave pending : pendingSaves) {
            RowBinding binding = bindingFrom(pending.entity());
            if (binding != null) {
                effects.add(new BindingEffect(pending.position(), binding, pending.resident(), false));
            }
        }
        registerBindingEffectsAfterCommit(effects);
    }

    private static void restoreDirty(BrewingStorage storage, List<Snapshot> snapshots) {
        storage.restoreDirty(snapshots.stream()
                .map(snapshot -> new int[] { snapshot.x, snapshot.y, snapshot.z }).toList());
    }

    private void rememberBindingAfterCommit(Long worldId, int x, int y, int z,
            WorldBrewingStand entity) {
        if (entity == null || entity.getId() == null) return;
        BrewingPosition position = new BrewingPosition(worldId, x, y, z);
        RowBinding binding = bindingFrom(entity);
        Runnable remember = () -> applyBindingEffect(new BindingEffect(position, binding, null, false));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    remember.run();
                }
            });
        } else {
            remember.run();
        }
    }

    private void publishLoadedBindings(Long worldId, List<BindingEffect> loaded) {
        Runnable publish = () -> {
            rowBindings.keySet().removeIf(position -> position.worldId() == worldId.longValue());
            residentBindings.keySet().removeIf(position -> position.worldId() == worldId.longValue());
            loaded.forEach(this::applyBindingEffect);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }

    private void forgetBindingsAfterCommit(Long worldId) {
        Runnable forget = () -> rowBindings.keySet()
                .removeIf(position -> position.worldId() == worldId.longValue());
        Runnable forgetResidents = () -> residentBindings.keySet()
                .removeIf(position -> position.worldId() == worldId.longValue());
        Runnable forgetAll = () -> {
            forget.run();
            forgetResidents.run();
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    forgetAll.run();
                }
            });
        } else {
            forgetAll.run();
        }
    }

    private static RowBinding bindingFrom(WorldBrewingStand entity) {
        if (entity == null || entity.getId() == null) return null;
        return new RowBinding(entity.getId(), entity.getPersistenceRevision(), entity.itemTypes(),
                entity.counts(), entity.getFuel(), entity.getBrewTicks(),
                entity.getBrewingIngredient());
    }

    private RowBinding bindingFor(Long worldId, int x, int y, int z) {
        requireWorldId(worldId);
        return rowBindings.get(new BrewingPosition(worldId, x, y, z));
    }

    private static boolean matchesBinding(RowBinding binding, BrewingInventory.Snapshot snapshot) {
        return binding != null && snapshot != null
                && binding.revision() == snapshot.revision()
                && Arrays.equals(binding.itemTypes(), snapshot.itemTypes())
                && Arrays.equals(binding.counts(), snapshot.counts())
                && binding.fuel() == snapshot.fuel()
                && binding.brewTicks() == snapshot.brewTicks()
                && binding.brewingIngredient() == snapshot.brewingIngredient();
    }

    private static boolean matchesBinding(WorldBrewingStand entity, Long worldId,
            int x, int y, int z, RowBinding binding) {
        return entity != null && binding != null
                && Objects.equals(entity.getId(), binding.id())
                && Objects.equals(entity.getWorldId(), worldId)
                && entity.getPosX() == x && entity.getPosY() == y && entity.getPosZ() == z
                && entity.matches(binding.itemTypes(), binding.counts(), binding.fuel(),
                        binding.brewTicks(), binding.brewingIngredient(), binding.revision());
    }

    private static boolean sameBinding(RowBinding left, RowBinding right) {
        return left != null && right != null
                && left.id() == right.id()
                && left.revision() == right.revision()
                && Arrays.equals(left.itemTypes(), right.itemTypes())
                && Arrays.equals(left.counts(), right.counts())
                && left.fuel() == right.fuel()
                && left.brewTicks() == right.brewTicks()
                && left.brewingIngredient() == right.brewingIngredient();
    }

    private void registerBindingEffectsAfterCommit(List<BindingEffect> effects) {
        if (effects.isEmpty()) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    effects.forEach(BrewingPersistenceService.this::applyBindingEffect);
                }
            });
        } else {
            effects.forEach(this::applyBindingEffect);
        }
    }

    private void applyBindingEffect(BindingEffect effect) {
        if (effect.remove()) {
            rowBindings.compute(effect.position(), (key, current) -> {
                if (!sameBinding(current, effect.binding())) return current;
                residentBindings.remove(key);
                return null;
            });
        } else if (effect.binding() != null) {
            RowBinding previous = rowBindings.put(effect.position(), effect.binding());
            if (effect.resident() != null) {
                residentBindings.put(effect.position(), effect.resident());
            } else if (previous == null || previous.id() != effect.binding().id()) {
                residentBindings.remove(effect.position());
            }
        }
    }

    private static void requireNextRevision(long revision, long expectedRevision) {
        if (expectedRevision == Long.MAX_VALUE || revision != expectedRevision + 1) {
            throw new IllegalArgumentException("brewing revision must advance expected revision once");
        }
    }

    private static void requireWorldId(Long worldId) {
        if (worldId == null || worldId <= 0L) {
            throw new IllegalArgumentException("positive world ID required");
        }
    }

    private static boolean matchesExact(WorldBrewingStand entity, Long worldId,
            ExactRetirementTarget target) {
        return entity != null
                && Objects.equals(entity.getId(), target.rowId())
                && Objects.equals(entity.getWorldId(), worldId)
                && entity.getPosX() == target.x()
                && entity.getPosY() == target.y()
                && entity.getPosZ() == target.z()
                && entity.matches(target.expectedItemTypes(), target.expectedCounts(),
                        target.expectedFuel(), target.expectedBrewTicks(),
                        target.expectedBrewingIngredient(), target.expectedRevision());
    }

    private static String positionKey(int x, int y, int z) { return x + ":" + y + ":" + z; }

    private record Snapshot(int x, int y, int z, short[] itemTypes, int[] counts,
            String[] components, int fuel,
            int brewTicks, short brewingIngredient, long revision, boolean deleted,
            RowBinding binding, BrewingInventory resident) {
        private Snapshot {
            itemTypes = itemTypes.clone();
            counts = counts.clone();
            components = components == null ? new String[BrewingInventory.SLOTS] : components.clone();
        }
        static Snapshot present(int x, int y, int z, BrewingInventory.Snapshot snapshot,
                RowBinding binding, BrewingInventory resident) {
            return new Snapshot(x, y, z, snapshot.itemTypes(), snapshot.counts(),
                    snapshot.components(), snapshot.fuel(),
                    snapshot.brewTicks(), snapshot.brewingIngredient(), snapshot.revision(), false,
                    binding, resident);
        }
        static Snapshot deleted(int x, int y, int z, RowBinding binding) {
            return new Snapshot(x, y, z, new short[BrewingInventory.SLOTS],
                    new int[BrewingInventory.SLOTS], null, 0, 0, (short) 0, 0, true, binding, null);
        }
    }
}
