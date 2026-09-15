package com.gameexpert.furnace.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.gameexpert.state.service.inventory.InventoryMutationTarget;
import com.gameexpert.state.service.inventory.StaleInventoryMutationException;

import com.gameexpert.engine.FurnaceInventory;
import com.gameexpert.engine.FurnaceStorage;
import com.gameexpert.engine.FurnaceVariant;
import com.gameexpert.engine.PersistenceExecutor;
import com.gameexpert.furnace.entity.WorldFurnace;
import com.gameexpert.furnace.entity.WorldFurnaceXpCarry;
import com.gameexpert.furnace.repository.WorldFurnaceRepository;
import com.gameexpert.furnace.repository.WorldFurnaceXpCarryRepository;


/** 화로 런타임 캐시와 좌표별 JPA 스냅샷 사이의 dirty 배치 배선입니다. */
@Service
public class FurnacePersistenceService {

    public enum GeneratedInstallOutcome { COMMITTED, IDEMPOTENT }

    /** Furnace slots are componentless today, but the witness spells out the complete item identity. */
    public record ExactSlot(short itemType, int count, int durability, long enchantments,
            int mapId, int shulkerId, String bucketMobData, String itemComponentData) {
        public ExactSlot {
            new com.gameexpert.engine.inventory.PlayerInventory.StackSnapshot(itemType, count,
                    durability, enchantments, mapId, shulkerId,
                    bucketMobData, itemComponentData);
        }

        public static ExactSlot componentless(short itemType, int count) {
            return new ExactSlot(itemType, count,
                    com.gameexpert.engine.inventory.PlayerInventory.initialDurability(itemType),
                    0L, 0, 0, null, null);
        }

        private boolean isComponentless() {
            return durability == com.gameexpert.engine.inventory.PlayerInventory.initialDurability(itemType)
                    && enchantments == 0L && mapId == 0 && shulkerId == 0
                    && bucketMobData == null && itemComponentData == null;
        }
    }

    /** Detached row-incarnation and complete current-schema witness captured on the owner thread. */
    public record ExactRetirementTarget(long rowId, int x, int y, int z,
            long expectedRevision, int variantCode, int burnTicks, int burnTotalTicks,
            int cookTicks, int xpMilli, List<ExactSlot> slots) {
        public ExactRetirementTarget {
            if (rowId <= 0L || expectedRevision < 0L || expectedRevision == Long.MAX_VALUE
                    || slots == null || slots.size() != FurnaceInventory.SLOTS
                    || slots.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("invalid exact furnace retirement witness");
            }
            slots = List.copyOf(slots);
        }
    }

    private record FurnacePosition(long worldId, int x, int y, int z) { }
    private record RowBinding(long rowId, long revision) { }

    private static final Logger log = LoggerFactory.getLogger(FurnacePersistenceService.class);

    private final WorldFurnaceRepository repository;
    private final WorldFurnaceXpCarryRepository carryRepository;
    private final PersistenceExecutor persistenceExecutor;
    private final TransactionTemplate transactionTemplate;
    private final Map<FurnacePosition, RowBinding> rowBindings = new ConcurrentHashMap<>();

    public record CarryState(int amount, long revision) {
        public CarryState {
            if (amount < 0 || amount >= 1000 || revision < 0 || revision == Long.MAX_VALUE) {
                throw new IllegalArgumentException("invalid world furnace XP carry state");
            }
        }
    }

    @Autowired
    public FurnacePersistenceService(WorldFurnaceRepository repository,
            WorldFurnaceXpCarryRepository carryRepository,
            PersistenceExecutor persistenceExecutor, TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.carryRepository = carryRepository;
        this.persistenceExecutor = persistenceExecutor;
        this.transactionTemplate = transactionTemplate;
    }

    /** Compatibility constructor for focused furnace tests that do not exercise carry persistence. */
    public FurnacePersistenceService(WorldFurnaceRepository repository,
            PersistenceExecutor persistenceExecutor, TransactionTemplate transactionTemplate) {
        this(repository, null, persistenceExecutor, transactionTemplate);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceExactSnapshotJoiningTransaction(Long worldId,
            InventoryMutationTarget.Furnace snapshot) {
        // InventoryMutationTarget.Furnace predates the persisted XP field. Keep this entry point
        // source-compatible until its caller carries xpMilli, but make the exact overload below
        // the only path that can persist a non-zero pending XP value.
        replaceExactSnapshotJoiningTransaction(worldId, snapshot, 0);
    }

    /** Persists a furnace snapshot with the exact pending smelt XP value. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceExactSnapshotJoiningTransaction(Long worldId,
            InventoryMutationTarget.Furnace snapshot, int xpMilli) {
        InventoryMutationTarget.Position pos = snapshot.position();
        WorldFurnace entity = repository
                .findLockedByWorldIdAndPosXAndPosYAndPosZ(worldId, pos.x(), pos.y(), pos.z())
                .orElseGet(() -> new WorldFurnace(worldId, pos.x(), pos.y(), pos.z()));
        if (!entity.replaceIfNewer(snapshot.itemTypes(), snapshot.counts(), snapshot.burnTicks(),
                snapshot.burnTotalTicks(), snapshot.cookTicks(), snapshot.variantCode(),
                xpMilli, snapshot.revision())) {
            throw new StaleInventoryMutationException("furnace " + pos);
        }
        repository.save(entity);
        rememberBindingAfterCommit(worldId, pos.x(), pos.y(), pos.z(), entity);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public GeneratedInstallOutcome installGeneratedJoiningTransaction(Long worldId,
            int x, int y, int z, FurnaceVariant variant, String installationId,
            String installationFingerprint) {
        if (variant == null) throw new IllegalArgumentException("furnace variant is required");
        WorldFurnace entity = repository
                .findLockedByWorldIdAndPosXAndPosYAndPosZ(worldId, x, y, z)
                .orElse(null);
        if (entity != null) {
            entity.requireSameGeneratedInstallation(
                    installationId, installationFingerprint, variant.code());
            rememberBindingAfterCommit(worldId, x, y, z, entity);
            return GeneratedInstallOutcome.IDEMPOTENT;
        }
        entity = new WorldFurnace(worldId, x, y, z);
        entity.claimGeneratedInstallation(installationId, installationFingerprint, variant.code());
        repository.save(entity);
        rememberBindingAfterCommit(worldId, x, y, z, entity);
        return GeneratedInstallOutcome.COMMITTED;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean replaceExactSnapshotAtExpectedRevisionJoiningTransaction(Long worldId,
            InventoryMutationTarget.Furnace snapshot, long expectedRevision) {
        // See replaceExactSnapshotJoiningTransaction(Long, Furnace): callers must migrate to the
        // overload carrying xpMilli before a non-zero XP remainder can enter this target path.
        return replaceExactSnapshotAtExpectedRevisionJoiningTransaction(
                worldId, snapshot, 0, expectedRevision);
    }

    /** CAS variant that compares and applies slots, progress, revision and pending XP together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean replaceExactSnapshotAtExpectedRevisionJoiningTransaction(Long worldId,
            InventoryMutationTarget.Furnace snapshot, int xpMilli, long expectedRevision) {
        InventoryMutationTarget.Position pos = snapshot.position();
        WorldFurnace entity = repository
                .findLockedByWorldIdAndPosXAndPosYAndPosZ(worldId, pos.x(), pos.y(), pos.z())
                .orElse(null);
        long persistedRevision = entity == null ? 0 : entity.getPersistenceRevision();
        if (persistedRevision != expectedRevision) return false;
        if (expectedRevision == Long.MAX_VALUE || snapshot.revision() != expectedRevision + 1) {
            throw new IllegalArgumentException("furnace revision must advance expected revision once");
        }
        if (entity == null) entity = new WorldFurnace(worldId, pos.x(), pos.y(), pos.z());
        if (!entity.replaceIfNewer(snapshot.itemTypes(), snapshot.counts(), snapshot.burnTicks(),
                snapshot.burnTotalTicks(), snapshot.cookTicks(), snapshot.variantCode(),
                xpMilli, snapshot.revision())) {
            throw new IllegalStateException("validated furnace revision changed in transaction");
        }
        repository.save(entity);
        rememberBindingAfterCommit(worldId, pos.x(), pos.y(), pos.z(), entity);
        return true;
    }

    /**
     * Captures an immutable witness without touching the database. The owner thread supplies the
     * resident inventory; its revision must still name the exact row incarnation bound by load/save.
     */
    public ExactRetirementTarget captureExactRetirementTarget(Long worldId,
            int x, int y, int z, FurnaceInventory inventory) {
        requireWorldId(worldId);
        if (inventory == null) throw new IllegalArgumentException("resident furnace is required");
        FurnaceInventory.Snapshot snapshot = inventory.snapshot();
        RowBinding binding = rowBindings.get(new FurnacePosition(worldId, x, y, z));
        if (binding == null || binding.revision() != snapshot.revision()) {
            throw new StaleInventoryMutationException(
                    "furnace row incarnation is not bound at " + x + ":" + y + ":" + z);
        }
        short[] types = snapshot.itemTypes();
        int[] counts = snapshot.counts();
        List<ExactSlot> slots = new ArrayList<>(FurnaceInventory.SLOTS);
        for (int slot = 0; slot < FurnaceInventory.SLOTS; slot++) {
            slots.add(ExactSlot.componentless(types[slot], counts[slot]));
        }
        return new ExactRetirementTarget(binding.rowId(), x, y, z, binding.revision(),
                snapshot.variant().code(), snapshot.burnTicks(), snapshot.burnTotalTicks(),
                snapshot.cookTicks(), snapshot.xpMilli(), slots);
    }

    /** Locks, prevalidates, and retires one exact furnace batch in the caller-owned transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void retireExactJoiningTransaction(Long worldId,
            List<ExactRetirementTarget> targets) {
        requireWorldId(worldId);
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("exact furnace retirement targets required");
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "exact furnace retirement requires transaction synchronization");
        }
        List<ExactRetirementTarget> ordered = new ArrayList<>(targets);
        Set<Long> rowIds = new HashSet<>();
        Set<FurnacePosition> positions = new HashSet<>();
        for (ExactRetirementTarget target : ordered) {
            if (target == null || !rowIds.add(target.rowId())
                    || !positions.add(new FurnacePosition(
                            worldId, target.x(), target.y(), target.z()))) {
                throw new IllegalArgumentException("duplicate or null exact furnace target");
            }
        }
        ordered.sort(Comparator.comparingInt(ExactRetirementTarget::x)
                .thenComparingInt(ExactRetirementTarget::y)
                .thenComparingInt(ExactRetirementTarget::z)
                .thenComparingLong(ExactRetirementTarget::rowId));

        List<WorldFurnace> locked = new ArrayList<>(ordered.size());
        List<Map.Entry<FurnacePosition, RowBinding>> retiredBindings = new ArrayList<>();
        for (ExactRetirementTarget target : ordered) {
            WorldFurnace entity = repository.findLockedByWorldIdAndPosXAndPosYAndPosZ(
                    worldId, target.x(), target.y(), target.z()).orElse(null);
            short[] types = new short[FurnaceInventory.SLOTS];
            int[] counts = new int[FurnaceInventory.SLOTS];
            boolean componentComplete = true;
            for (int slot = 0; slot < FurnaceInventory.SLOTS; slot++) {
                ExactSlot expected = target.slots().get(slot);
                types[slot] = expected.itemType();
                counts[slot] = expected.count();
                componentComplete &= expected.isComponentless();
            }
            if (!componentComplete || entity == null
                    || !Objects.equals(entity.getId(), target.rowId())
                    || !Objects.equals(entity.getWorldId(), worldId)
                    || !entity.matchesExact(types, counts, target.burnTicks(),
                            target.burnTotalTicks(), target.cookTicks(), target.variantCode(),
                            target.xpMilli(), target.expectedRevision())) {
                throw new StaleInventoryMutationException(
                        "stale exact furnace retirement at " + target.x() + ":"
                                + target.y() + ":" + target.z());
            }
            locked.add(entity);
            retiredBindings.add(Map.entry(
                    new FurnacePosition(worldId, target.x(), target.y(), target.z()),
                    new RowBinding(target.rowId(), target.expectedRevision())));
        }
        repository.deleteAll(locked);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                retiredBindings.forEach(entry ->
                        rowBindings.remove(entry.getKey(), entry.getValue()));
            }
        });
    }

    public CarryState loadWorld(Long worldId, FurnaceStorage storage) {
        requireWorldId(worldId);
        rowBindings.keySet().removeIf(position -> position.worldId() == worldId.longValue());
        for (WorldFurnace entity : repository.findAllByWorldId(worldId)) {
            // [FURNACE-VARIANT] 변형 열이 없던 시절 행은 0 → 화로로 읽힌다(WorldFurnace 참조).
            FurnaceInventory furnace =
                    new FurnaceInventory(FurnaceVariant.fromCode(entity.getVariantCode()));
            furnace.restore(
                    new short[] { entity.getInputType(), entity.getFuelType(), entity.getOutputType() },
                    new int[] { entity.getInputCount(), entity.getFuelCount(), entity.getOutputCount() },
                    entity.getBurnTicks(), entity.getBurnTotalTicks(), entity.getCookTicks(),
                    entity.getVariantCode(), entity.getPersistenceRevision(), entity.getXpMilli());
            storage.load(entity.getPosX(), entity.getPosY(), entity.getPosZ(), furnace);
            rememberBinding(worldId, entity.getPosX(), entity.getPosY(), entity.getPosZ(), entity);
        }
        if (carryRepository == null) return new CarryState(0, 0L);
        return carryRepository.findById(worldId)
                .map(entity -> new CarryState(
                        entity.getCarryMilli(), entity.getPersistenceRevision()))
                .orElseGet(() -> new CarryState(0, 0L));
    }

    /** 틱 스레드에서는 값만 복사하고 DB 쓰기는 공용 직렬 writer에 맡깁니다. */
    public void flushDirty(Long worldId, FurnaceStorage storage) {
        flushDirty(worldId, storage, 0, 0L, false, ignored -> { });
    }

    /** Checkpoints furnace rows and the world carry in the same serial transaction boundary. */
    public void flushDirty(Long worldId, FurnaceStorage storage,
            int carryMilli, long carryRevision, boolean carryDirty,
            LongConsumer carryAcknowledgement) {
        requireWorldId(worldId);
        CarryState carry = new CarryState(carryMilli, carryRevision);
        if (carryDirty && carryRepository == null) {
            throw new IllegalStateException("world furnace XP carry repository is required");
        }
        if (carryAcknowledgement == null) {
            throw new IllegalArgumentException("carry acknowledgement is required");
        }
        List<FurnaceSnapshot> snapshots = new ArrayList<>();
        for (int[] pos : storage.drainDirty()) {
            FurnaceInventory furnace = storage.peekAt(pos[0], pos[1], pos[2]);
            if (furnace == null) {
                snapshots.add(FurnaceSnapshot.deleted(pos[0], pos[1], pos[2]));
                continue;
            }
            short[] types = new short[FurnaceInventory.SLOTS];
            int[] counts = new int[FurnaceInventory.SLOTS];
            for (int slot = 0; slot < FurnaceInventory.SLOTS; slot++) {
                types[slot] = furnace.itemType(slot);
                counts[slot] = furnace.count(slot);
            }
            snapshots.add(new FurnaceSnapshot(pos[0], pos[1], pos[2], types, counts,
                    furnace.burnTicks(), furnace.burnTotalTicks(), furnace.cookTicks(),
                    furnace.variant().code(), furnace.xpMilli(), false, furnace.persistenceRevision()));
        }
        if (snapshots.isEmpty() && !carryDirty) return;
        boolean accepted = persistenceExecutor.trySubmit(() -> {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    persist(worldId, snapshots);
                    if (carryDirty) persistCarry(worldId, carry);
                });
                if (carryDirty) carryAcknowledgement.accept(carry.revision());
            } catch (RuntimeException exception) {
                restoreDirty(storage, snapshots);
                log.warn("월드 {} 화로 {}개 flush 실패 — dirty 좌표 복원",
                        worldId, snapshots.size(), exception);
            }
        });
        if (!accepted) {
            restoreDirty(storage, snapshots);
            log.warn("월드 {} 화로 flush 제출 거부 — dirty 좌표 {} 개 복원",
                    worldId, snapshots.size());
        }
    }

    private void persistCarry(Long worldId, CarryState snapshot) {
        WorldFurnaceXpCarry entity = carryRepository.findLockedByWorldId(worldId).orElse(null);
        if (entity == null) {
            if (snapshot.revision() == 0L) {
                if (snapshot.amount() != 0) {
                    throw new IllegalStateException("nonzero furnace XP carry has no generation");
                }
                return;
            }
            entity = new WorldFurnaceXpCarry(worldId);
        } else if (entity.matches(snapshot.amount(), snapshot.revision())) {
            return;
        } else if (entity.getPersistenceRevision() >= snapshot.revision()) {
            throw new StaleInventoryMutationException(
                    "stale world furnace XP carry for world " + worldId);
        }
        if (!entity.replaceIfNewer(snapshot.amount(), snapshot.revision())) {
            throw new StaleInventoryMutationException(
                    "world furnace XP carry CAS failed for world " + worldId);
        }
        carryRepository.save(entity);
    }

    private static void restoreDirty(FurnaceStorage storage, List<FurnaceSnapshot> snapshots) {
        List<int[]> positions = new ArrayList<>(snapshots.size());
        for (FurnaceSnapshot snapshot : snapshots) {
            positions.add(new int[] { snapshot.x, snapshot.y, snapshot.z });
        }
        storage.restoreDirty(positions);
    }

    private void persist(Long worldId, List<FurnaceSnapshot> snapshots) {
        if (snapshots.isEmpty()) return;
        List<int[]> positions = new ArrayList<>(snapshots.size());
        for (FurnaceSnapshot snapshot : snapshots) {
            positions.add(new int[] { snapshot.x, snapshot.y, snapshot.z });
        }
        Map<String, WorldFurnace> existing = new HashMap<>();
        for (WorldFurnace entity : repository.findDirtyByWorldId(worldId, positions)) {
            existing.put(positionKey(entity.getPosX(), entity.getPosY(), entity.getPosZ()), entity);
        }
        List<WorldFurnace> changed = new ArrayList<>();
        List<WorldFurnace> deleted = new ArrayList<>();
        for (FurnaceSnapshot snapshot : snapshots) {
            String key = positionKey(snapshot.x, snapshot.y, snapshot.z);
            WorldFurnace entity = existing.get(key);
            if (snapshot.deleted) {
                if (entity != null) deleted.add(entity);
                continue;
            }
            if (entity == null) {
                entity = new WorldFurnace(worldId, snapshot.x, snapshot.y, snapshot.z);
            }
            if (entity.replaceIfNewer(snapshot.itemTypes, snapshot.counts,
                    snapshot.burnTicks, snapshot.burnTotalTicks, snapshot.cookTicks,
                    snapshot.variantCode, snapshot.xpMilli, snapshot.revision)) changed.add(entity);
        }
        if (!deleted.isEmpty()) repository.deleteAll(deleted);
        if (!changed.isEmpty()) repository.saveAll(changed);
        if (!deleted.isEmpty() || !changed.isEmpty()) {
            List<Map.Entry<FurnacePosition, RowBinding>> removed = new ArrayList<>();
            for (WorldFurnace entity : deleted) {
                if (entity.getId() != null) removed.add(Map.entry(
                        new FurnacePosition(worldId, entity.getPosX(), entity.getPosY(), entity.getPosZ()),
                        new RowBinding(entity.getId(), entity.getPersistenceRevision())));
            }
            List<Map.Entry<FurnacePosition, RowBinding>> saved = new ArrayList<>();
            for (WorldFurnace entity : changed) {
                if (entity.getId() != null) saved.add(Map.entry(
                        new FurnacePosition(worldId, entity.getPosX(), entity.getPosY(), entity.getPosZ()),
                        new RowBinding(entity.getId(), entity.getPersistenceRevision())));
            }
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() {
                        removed.forEach(entry -> rowBindings.remove(entry.getKey(), entry.getValue()));
                        saved.forEach(entry -> rowBindings.put(entry.getKey(), entry.getValue()));
                    }
                });
            }
        }
    }

    private void rememberBindingAfterCommit(Long worldId, int x, int y, int z,
            WorldFurnace entity) {
        if (entity == null || entity.getId() == null) return;
        FurnacePosition position = new FurnacePosition(worldId, x, y, z);
        RowBinding binding = new RowBinding(entity.getId(), entity.getPersistenceRevision());
        Runnable remember = () -> rowBindings.put(position, binding);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { remember.run(); }
            });
        } else {
            remember.run();
        }
    }

    private void rememberBinding(Long worldId, int x, int y, int z, WorldFurnace entity) {
        if (entity != null && entity.getId() != null) {
            rowBindings.put(new FurnacePosition(worldId, x, y, z),
                    new RowBinding(entity.getId(), entity.getPersistenceRevision()));
        }
    }

    private RowBinding bindingFor(Long worldId, int x, int y, int z) {
        return rowBindings.get(new FurnacePosition(worldId, x, y, z));
    }

    private static void requireWorldId(Long worldId) {
        if (worldId == null || worldId <= 0L) {
            throw new IllegalArgumentException("positive world ID required");
        }
    }

    private static String positionKey(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private static final class FurnaceSnapshot {
        private final int x;
        private final int y;
        private final int z;
        private final short[] itemTypes;
        private final int[] counts;
        private final int burnTicks;
        private final int burnTotalTicks;
        private final int cookTicks;
        private final int variantCode;
        private final int xpMilli;
        private final boolean deleted;
        private final long revision;

        private FurnaceSnapshot(int x, int y, int z, short[] itemTypes, int[] counts,
                int burnTicks, int burnTotalTicks, int cookTicks, int variantCode,
                int xpMilli, boolean deleted, long revision) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.itemTypes = itemTypes.clone();
            this.counts = counts.clone();
            this.burnTicks = burnTicks;
            this.burnTotalTicks = burnTotalTicks;
            this.cookTicks = cookTicks;
            this.variantCode = variantCode;
            this.xpMilli = xpMilli;
            this.deleted = deleted;
            this.revision = revision;
        }

        private static FurnaceSnapshot deleted(int x, int y, int z) {
            return new FurnaceSnapshot(
                    x, y, z, new short[3], new int[3], 0, 0, 0,
                    FurnaceVariant.FURNACE.code(), 0, true, 0);
        }
    }
}
