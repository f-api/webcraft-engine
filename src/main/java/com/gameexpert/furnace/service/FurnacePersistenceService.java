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
import com.gameexpert.engine.inventory.PlayerInventory.StackSnapshot;
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

    /** Exact retirement witnesses carry the same full identity as persisted furnace slots. */
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

        public StackSnapshot stack() {
            return new StackSnapshot(itemType, count, durability, enchantments, mapId,
                    shulkerId, bucketMobData, itemComponentData);
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
        replaceExactSnapshotJoiningTransaction(worldId, snapshot, snapshot.xpMilli());
    }

    /** Persists a furnace snapshot with the exact pending smelt XP value. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceExactSnapshotJoiningTransaction(Long worldId,
            InventoryMutationTarget.Furnace snapshot, int xpMilli) {
        InventoryMutationTarget.Position pos = snapshot.position();
        WorldFurnace entity = repository
                .findLockedByWorldIdAndPosXAndPosYAndPosZ(worldId, pos.x(), pos.y(), pos.z())
                .orElseGet(() -> new WorldFurnace(worldId, pos.x(), pos.y(), pos.z()));
        if (!entity.replaceIfNewer(contents(snapshot, xpMilli))) {
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
        return replaceExactSnapshotAtExpectedRevisionJoiningTransaction(
                worldId, snapshot, snapshot.xpMilli(), expectedRevision);
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
        if (!entity.replaceIfNewer(contents(snapshot, xpMilli))) {
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
            StackSnapshot value = snapshot.stacks()[slot];
            slots.add(new ExactSlot(value.itemType(), value.count(), value.durability(),
                    value.enchantments(), value.mapId(), value.shulkerId(),
                    value.bucketMobData(), value.itemComponentData()));
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
            StackSnapshot[] stacks = new StackSnapshot[FurnaceInventory.SLOTS];
            for (int slot = 0; slot < FurnaceInventory.SLOTS; slot++) {
                ExactSlot expected = target.slots().get(slot);
                types[slot] = expected.itemType();
                counts[slot] = expected.count();
                stacks[slot] = expected.stack();
            }
            if (entity == null
                    || !Objects.equals(entity.getId(), target.rowId())
                    || !Objects.equals(entity.getWorldId(), worldId)
                    || !entity.matchesExact(new FurnaceInventory.Snapshot(types, counts, target.burnTicks(),
                            target.burnTotalTicks(), target.cookTicks(), FurnaceVariant.fromCode(target.variantCode()),
                            target.expectedRevision(), target.xpMilli(), stacks))) {
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
            furnace.restore(entity.snapshot());
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
        DirtyCheckpoint checkpoint = captureDirty(worldId, storage, carryMilli, carryRevision,
                carryDirty, carryAcknowledgement);
        if (checkpoint.isEmpty()) return;
        try {
            boolean accepted = persistenceExecutor.trySubmit(() -> {
                try {
                    transactionTemplate.executeWithoutResult(status -> checkpoint.persist(false));
                    checkpoint.committed();
                } catch (RuntimeException | Error failure) {
                    checkpoint.restoreDirty();
                    log.warn("월드 {} 화로 flush 실패 — dirty 복원", worldId, failure);
                }
            });
            if (!accepted) checkpoint.restoreDirty();
        } catch (RuntimeException | Error failure) {
            checkpoint.restoreDirty();
            throw failure;
        }
    }

    /** Captured on the owner thread; may join a chest checkpoint's transaction. */
    public DirtyCheckpoint captureDirty(Long worldId, FurnaceStorage storage,
            int carryMilli, long carryRevision, boolean carryDirty,
            LongConsumer carryAcknowledgement) {
        requireWorldId(worldId);
        CarryState carry = new CarryState(carryMilli, carryRevision);
        if (carryDirty && carryRepository == null) {
            throw new IllegalStateException("world furnace XP carry repository is required");
        }
        Objects.requireNonNull(carryAcknowledgement, "carry acknowledgement is required");
        List<int[]> drained = storage.drainDirty();
        try {
            List<FurnaceSnapshot> snapshots = new ArrayList<>();
            for (int[] pos : drained) {
                FurnaceInventory furnace = storage.peekAt(pos[0], pos[1], pos[2]);
                snapshots.add(furnace == null
                        ? FurnaceSnapshot.deleted(pos[0], pos[1], pos[2])
                        : new FurnaceSnapshot(pos[0], pos[1], pos[2], furnace.snapshot(), false));
            }
            return new DirtyCheckpoint(worldId, storage, snapshots, carry, carryDirty,
                    carryAcknowledgement);
        } catch (RuntimeException | Error failure) {
            storage.restoreDirty(drained);
            throw failure;
        }
    }

    public final class DirtyCheckpoint {
        private final Long worldId;
        private final FurnaceStorage storage;
        private final List<FurnaceSnapshot> snapshots;
        private final CarryState carry;
        private final boolean carryDirty;
        private final LongConsumer carryAcknowledgement;

        private DirtyCheckpoint(Long worldId, FurnaceStorage storage,
                List<FurnaceSnapshot> snapshots, CarryState carry, boolean carryDirty,
                LongConsumer carryAcknowledgement) {
            this.worldId = worldId;
            this.storage = storage;
            this.snapshots = List.copyOf(snapshots);
            this.carry = carry;
            this.carryDirty = carryDirty;
            this.carryAcknowledgement = carryAcknowledgement;
        }

        public boolean isEmpty() { return snapshots.isEmpty() && !carryDirty; }

        public void persist(boolean requireExact) {
            FurnacePersistenceService.this.persist(worldId, snapshots, requireExact);
            if (carryDirty) persistCarry(worldId, carry);
        }

        public void committed() {
            if (carryDirty) carryAcknowledgement.accept(carry.revision());
        }

        public void restoreDirty() {
            FurnacePersistenceService.restoreDirty(storage, snapshots);
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

    private void persist(Long worldId, List<FurnaceSnapshot> snapshots, boolean requireExact) {
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
            if (entity.replaceIfNewer(snapshot.state)) changed.add(entity);
            else if (requireExact && !entity.matchesExact(snapshot.state)) {
                throw new StaleInventoryMutationException("stale joint furnace checkpoint");
            }
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

    private static FurnaceInventory.Snapshot contents(InventoryMutationTarget.Furnace snapshot, int xpMilli) {
        return new FurnaceInventory.Snapshot(snapshot.itemTypes(), snapshot.counts(),
                snapshot.burnTicks(), snapshot.burnTotalTicks(), snapshot.cookTicks(),
                FurnaceVariant.fromCode(snapshot.variantCode()), snapshot.revision(), xpMilli, snapshot.stacks());
    }

    private record FurnaceSnapshot(int x, int y, int z, FurnaceInventory.Snapshot state, boolean deleted) {
        private static FurnaceSnapshot deleted(int x, int y, int z) {
            return new FurnaceSnapshot(x, y, z, null, true);
        }
    }
}
