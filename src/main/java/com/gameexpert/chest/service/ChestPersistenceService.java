package com.gameexpert.chest.service;

import com.gameexpert.chest.entity.ChestItem;
import com.gameexpert.chest.entity.WorldChest;
import com.gameexpert.chest.repository.WorldChestRepository;
import com.gameexpert.engine.ChestInventory;
import com.gameexpert.engine.ChestStorage;
import com.gameexpert.engine.PersistenceExecutor;
import com.gameexpert.engine.ShulkerContentsStorage;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootAggregateMutation;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootAggregatePersistence;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootStoredResolution;
import com.gameexpert.dispenser.service.DispenserPersistenceService;
import com.gameexpert.shulker.service.ShulkerContentsPersistenceService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.gameexpert.state.service.inventory.InventoryMutationTarget;
import com.gameexpert.state.service.inventory.StaleInventoryMutationException;

/** 상자 런타임 캐시와 JPA 엔티티 사이의 얇은 스냅샷 배선. */
@Service
@RequiredArgsConstructor
public class ChestPersistenceService {

    public enum GeneratedInstallOutcome { COMMITTED, IDEMPOTENT }

    /**
     * A detached, exact chest-row witness captured before an aggregate settlement starts.
     * The row identity prevents a delete/recreate at the same coordinate from being retired,
     * while the revision and complete item values prevent retiring contents that changed in place.
     */
    public record ExactRetirementTarget(long rowId, int x, int y, int z,
            long expectedRevision, int containerSize, List<ChestItem> expectedItems) {
        public ExactRetirementTarget {
            if (rowId <= 0L || expectedRevision < 0L || containerSize <= 0) {
                throw new IllegalArgumentException("invalid exact chest retirement identity");
            }
            expectedItems = copyItems(expectedItems);
            if (!WorldChest.sameCanonicalItems(containerSize, expectedItems, expectedItems)) {
                throw new IllegalArgumentException("invalid exact chest retirement contents");
            }
        }
    }

    private static final Logger log = LoggerFactory.getLogger(ChestPersistenceService.class);

    private final WorldChestRepository repository;
    private final PersistenceExecutor persistenceExecutor;
    private final TransactionTemplate transactionTemplate;
    /** Binds a resident snapshot to one database row incarnation and its last known revision. */
    private final Map<ChestPosition, RowBinding> rowBindings = new ConcurrentHashMap<>();
    /**
     * [SHULKER-CONTENTS] 27칸 참조 lane. {@code null} 이면 참조 저장소 없이 도는 배선이다
     * (좌표 상자만 쓰는 단위 테스트). 생성자를 늘리지 않고 선택 주입하는 이유는, 이 lane 이
     * 상자 lane 의 <b>트랜잭션에 얹히는</b> 종속이지 상자 저장의 전제가 아니기 때문이다.
     */
    private ShulkerContentsPersistenceService shulkerPersistence;
    private DispenserPersistenceService dispenserPersistence;

    private record ChestPosition(long worldId, int x, int y, int z) { }

    private record RowBinding(long id, long revision, int containerSize,
            List<ChestItem> expectedItems) {
        private RowBinding {
            if (id <= 0L || revision < 0L || containerSize <= 0) {
                throw new IllegalArgumentException("invalid chest row binding");
            }
            expectedItems = copyItems(expectedItems);
        }
    }

    private record PendingSave(ChestPosition position, WorldChest entity) { }

    private record BindingEffect(ChestPosition position, RowBinding binding, boolean remove) { }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setShulkerPersistence(ShulkerContentsPersistenceService shulkerPersistence) {
        this.shulkerPersistence = shulkerPersistence;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDispenserPersistence(DispenserPersistenceService dispenserPersistence) {
        this.dispenserPersistence = dispenserPersistence;
    }

    /** Locked component-complete first-open aggregate boundary used by the canonical LOOT lane. */
    @Transactional(propagation = Propagation.MANDATORY)
    public CanonicalLootAggregatePersistence.Outcome installCanonicalLootJoiningTransaction(
            CanonicalLootAggregateMutation mutation) {
        if (mutation == null) throw new IllegalArgumentException("canonical LOOT mutation required");
        boolean dispenser = mutation.containerKind()
                == com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootContainerKind.DISPENSER;
        WorldChest entity = dispenser ? null : repository.findLockedAt(
                mutation.worldId(), mutation.x(), mutation.y(), mutation.z()).orElse(null);
        if (!dispenser && entity == null && !mutation.firstInstallation()) {
            return CanonicalLootAggregatePersistence.Outcome.REJECTED;
        }
        List<ChestItem> fixedItems;
        try {
            int version = dispenser || entity == null ? WorldChest.CURRENT_MATERIALIZER_VERSION
                    : entity.materializerVersionForInstallation();
            fixedItems = WorldChest.projectCanonicalLootItems(mutation.resolution(), version);
        } catch (IllegalArgumentException | IllegalStateException unsupported) {
            return CanonicalLootAggregatePersistence.Outcome.REJECTED;
        }
        if (mutation.containerKind()
                == com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootContainerKind
                        .DISPENSER) {
            if (dispenserPersistence == null) {
                return CanonicalLootAggregatePersistence.Outcome.REJECTED;
            }
            return dispenserPersistence.installCanonicalLootJoiningTransaction(mutation, fixedItems);
        }
        try {
            if (entity == null) {
                // Only the locked UNOPENED assignment grants this transient authority.
                // Keep the new row detached until exact projection and installation both succeed.
                entity = new WorldChest(mutation.worldId(), mutation.x(), mutation.y(),
                        mutation.z(), mutation.containerKind().slots());
                entity.claimGeneratedInstallation(mutation.laneInstallationIdentity(),
                        mutation.installationFingerprint(), mutation.containerKind().slots());
            }
            boolean committed = entity.installCanonicalLoot(
                    mutation.laneInstallationIdentity(), mutation.installationFingerprint(),
                    mutation.definitionFingerprint(), mutation.resultFingerprint(),
                    mutation.resolution().encode(), fixedItems);
            repository.save(entity);
            rememberBindingAfterCommit(
                    mutation.worldId(), mutation.x(), mutation.y(), mutation.z(), entity);
            return committed ? CanonicalLootAggregatePersistence.Outcome.COMMITTED
                    : CanonicalLootAggregatePersistence.Outcome.ALREADY_COMMITTED;
        } catch (IllegalArgumentException | IllegalStateException conflict) {
            return CanonicalLootAggregatePersistence.Outcome.REJECTED;
        }
    }

    /** Builds the exact resident inventory only after its aggregate transaction committed. */
    public ChestInventory materializeCanonicalLoot(CanonicalLootStoredResolution resolution) {
        return inventoryFromItems(projectCanonicalLootItems(resolution), resolution.slots().size(), 1L);
    }

    /** Owner-thread replay recovery from a committed row witness; no DB I/O or loot re-projection. */
    public ChestInventory restoreCommittedCanonicalLoot(Long worldId, int x, int y, int z) {
        RowBinding binding = bindingFor(worldId, x, y, z);
        if (binding == null || binding.revision() <= 0L) {
            throw new IllegalStateException("committed canonical chest row is unavailable");
        }
        return inventoryFromItems(binding.expectedItems(), binding.containerSize(), binding.revision());
    }

    private static ChestInventory inventoryFromItems(List<ChestItem> items, int size, long revision) {
        ChestInventory inventory = new ChestInventory(size);
        for (ChestItem item : items) {
            inventory.restoreSlot(item.getSlot(), item.getItemType(), item.getItemCount(),
                    item.getDurability(), item.getEnchantments(), item.getMapId(),
                    item.getShulkerId(), item.getBucketMobData(), item.getItemComponentData());
        }
        inventory.restorePersistenceRevision(revision);
        return inventory;
    }

    /** Single exact symbolic-loot to gameplay-cargo projection shared by every container kind. */
    public static List<ChestItem> projectCanonicalLootItems(
            CanonicalLootStoredResolution resolution) {
        return WorldChest.projectCanonicalLootItems(resolution);
    }

    /** 이미 열린 플레이어 조작 트랜잭션에 한쪽 또는 양쪽 상자 스냅샷을 합류시킨다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceExactSnapshotsJoiningTransaction(Long worldId,
            List<InventoryMutationTarget.ChestHalf> halves) {
        for (InventoryMutationTarget.ChestHalf half : halves) {
            InventoryMutationTarget.Position pos = half.position();
            WorldChest entity = repository.findLockedAt(worldId, pos.x(), pos.y(), pos.z())
                    .orElseGet(() -> new WorldChest(worldId, pos.x(), pos.y(), pos.z(),
                            half.contents().itemTypes().length));
            entity.requireContainerSize(half.contents().itemTypes().length);
            List<ChestItem> items = items(half.contents());
            if (!entity.replaceItemsIfNewer(items, half.revision())) {
                throw new StaleInventoryMutationException("chest " + pos);
            }
            repository.save(entity);
            rememberBindingAfterCommit(worldId, pos.x(), pos.y(), pos.z(), entity);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public GeneratedInstallOutcome installGeneratedContainerJoiningTransaction(Long worldId,
            int x, int y, int z, int containerSize, String installationId,
            String installationFingerprint) {
        WorldChest entity = repository.findLockedAt(worldId, x, y, z).orElse(null);
        if (entity != null) {
            entity.requireSameGeneratedInstallation(
                    installationId, installationFingerprint, containerSize);
            rememberBindingAfterCommit(worldId, x, y, z, entity);
            return GeneratedInstallOutcome.IDEMPOTENT;
        }
        entity = new WorldChest(worldId, x, y, z, containerSize);
        entity.claimGeneratedInstallation(installationId, installationFingerprint, containerSize);
        repository.save(entity);
        rememberBindingAfterCommit(worldId, x, y, z, entity);
        return GeneratedInstallOutcome.COMMITTED;
    }

    /**
     * 안정 정산 명령이 읽었던 세대와 DB 세대가 정확히 같을 때만 모든 half를 교체한다.
     * 두 half를 모두 잠그고 검증한 뒤 쓰므로 두 번째 half가 stale이어도 첫 half가 변하지 않는다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean replaceExactSnapshotsAtExpectedRevisionsJoiningTransaction(Long worldId,
            List<InventoryMutationTarget.ChestHalf> halves, List<Long> expectedRevisions) {
        if (halves.size() != expectedRevisions.size()) {
            throw new IllegalArgumentException("each chest half needs an expected revision");
        }
        List<WorldChest> entities = new ArrayList<>(halves.size());
        for (int index = 0; index < halves.size(); index++) {
            InventoryMutationTarget.Position pos = halves.get(index).position();
            WorldChest entity = repository.findLockedAt(worldId, pos.x(), pos.y(), pos.z())
                    .orElse(null);
            long persistedRevision = entity == null ? 0 : entity.getPersistenceRevision();
            if (persistedRevision != expectedRevisions.get(index)) return false;
            entities.add(entity == null
                    ? new WorldChest(worldId, pos.x(), pos.y(), pos.z(),
                            halves.get(index).contents().itemTypes().length) : entity);
            entities.get(index).requireContainerSize(
                    halves.get(index).contents().itemTypes().length);
        }
        for (int index = 0; index < halves.size(); index++) {
            InventoryMutationTarget.ChestHalf half = halves.get(index);
            InventoryMutationTarget.Position pos = half.position();
            long expected = expectedRevisions.get(index);
            if (expected < 0 || expected == Long.MAX_VALUE
                    || half.revision() != expected + 1) {
                throw new IllegalArgumentException("chest revision must advance expected revision once");
            }
            WorldChest entity = entities.get(index);
            if (!entity.replaceItemsIfNewer(items(half.contents()), half.revision())) {
                throw new IllegalStateException("validated chest revision changed in transaction");
            }
            repository.save(entity);
            rememberBindingAfterCommit(worldId, pos.x(), pos.y(), pos.z(), entity);
        }
        return true;
    }

    /**
     * Retires every witnessed chest row as one caller-owned transaction.
     *
     * <p>All rows are pessimistically locked and all exact witnesses are checked before the first
     * delete is issued. A missing, recreated, moved, resized, revised, or content-divergent row
     * aborts the enclosing settlement without partially retiring another half. Cache bindings are
     * removed only after the caller's transaction commits.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void retireExactJoiningTransaction(Long worldId,
            List<ExactRetirementTarget> targets) {
        requireWorldId(worldId);
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("exact chest retirement targets required");
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("exact chest retirement requires transaction synchronization");
        }

        List<ExactRetirementTarget> exactTargets = new ArrayList<>(targets);
        Set<Long> rowIds = new HashSet<>();
        Set<ChestPosition> positions = new HashSet<>();
        for (ExactRetirementTarget target : exactTargets) {
            if (target == null) throw new IllegalArgumentException("exact chest target required");
            ChestPosition position = new ChestPosition(worldId, target.x(), target.y(), target.z());
            if (!rowIds.add(target.rowId()) || !positions.add(position)) {
                throw new IllegalArgumentException("duplicate exact chest retirement target");
            }
        }
        exactTargets.sort(Comparator.comparingLong(ExactRetirementTarget::rowId));

        List<WorldChest> locked = new ArrayList<>(exactTargets.size());
        List<BindingEffect> effects = new ArrayList<>(exactTargets.size());
        for (ExactRetirementTarget target : exactTargets) {
            WorldChest entity = repository.findLockedById(target.rowId()).orElse(null);
            if (entity == null
                    || !Objects.equals(entity.getWorldId(), worldId)
                    || entity.getPosX() != target.x()
                    || entity.getPosY() != target.y()
                    || entity.getPosZ() != target.z()
                    || entity.getPersistenceRevision() != target.expectedRevision()
                    || entity.getContainerSize() != target.containerSize()
                    || !WorldChest.sameCanonicalItems(target.containerSize(), entity.getItems(),
                            target.expectedItems())) {
                throw new StaleInventoryMutationException(
                        "stale exact chest retirement at " + target.x() + ":"
                                + target.y() + ":" + target.z());
            }
            locked.add(entity);
            effects.add(new BindingEffect(
                    new ChestPosition(worldId, target.x(), target.y(), target.z()),
                    new RowBinding(target.rowId(), target.expectedRevision(),
                            target.containerSize(), target.expectedItems()), true));
        }

        repository.deleteAll(locked);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                effects.forEach(ChestPersistenceService.this::applyBindingEffect);
            }
        });
    }

    /**
     * Captures a detached exact-row witness from the resident chest on its owner thread.
     *
     * <p>This method deliberately performs no repository access. It accepts a resident snapshot
     * only when its row incarnation, revision, size, and every persisted item component still
     * match the binding established by a committed load/save. Callers must capture before
     * mutating or detaching the resident inventory.
     */
    public ExactRetirementTarget captureExactRetirementTarget(Long worldId,
            int x, int y, int z, ChestInventory resident) {
        requireWorldId(worldId);
        if (resident == null) {
            throw new StaleInventoryMutationException(
                    "missing resident chest at " + x + ":" + y + ":" + z);
        }
        RowBinding binding = bindingFor(worldId, x, y, z);
        if (binding == null) {
            throw new StaleInventoryMutationException(
                    "unbound resident chest at " + x + ":" + y + ":" + z);
        }
        ChestInventory.PersistenceSnapshot captured = resident.persistenceSnapshot();
        List<ChestItem> capturedItems = items(captured);
        if (captured.revision() != binding.revision()
                || captured.slots() != binding.containerSize()
                || !WorldChest.sameCanonicalItems(binding.containerSize(),
                        binding.expectedItems(), capturedItems)) {
            throw new StaleInventoryMutationException(
                    "stale resident chest at " + x + ":" + y + ":" + z);
        }
        return new ExactRetirementTarget(binding.id(), x, y, z, binding.revision(),
                binding.containerSize(), capturedItems);
    }

    private static List<ChestItem> items(ChestInventory.Snapshot snapshot) {
        short[] itemTypes = snapshot.itemTypes();
        int[] counts = snapshot.counts();
        int[] durabilities = snapshot.durabilities();
        long[] enchantments = snapshot.enchantments();
        int[] mapIds = snapshot.mapIds();
        int[] shulkerIds = snapshot.shulkerIds();
        String[] bucketMobData = snapshot.bucketMobData();
        String[] itemComponentData = snapshot.itemComponentData();
        List<ChestItem> items = new ArrayList<>();
        for (int slot = 0; slot < itemTypes.length; slot++) {
            short type = itemTypes[slot];
            int count = counts[slot];
            if (type == PlayerInventory.EMPTY || count <= 0) continue;
            items.add(new ChestItem(slot, type, count,
                    PlayerInventory.isDurable(type) ? durabilities[slot] : null,
                    enchantments[slot] == 0 ? null : enchantments[slot],
                    mapIds[slot] == 0 ? null : mapIds[slot],
                    // [SHULKER-CONTENTS] 이 칸에 든 셜커가 물고 있는 27칸 참조도 함께 굳힌다.
                    shulkerIds[slot] == 0 ? null : shulkerIds[slot],
                    bucketMobData[slot], itemComponentData[slot]));
        }
        return items;
    }

    private static List<ChestItem> items(ChestInventory.PersistenceSnapshot snapshot) {
        return items(snapshot.snapshot());
    }

    /** 첫 월드 런타임 생성 시 저장된 27칸을 좌표 캐시에 복원한다. */
    public void loadWorld(Long worldId, ChestStorage storage) {
        requireWorldId(worldId);
        rowBindings.keySet().removeIf(position -> position.worldId() == worldId.longValue());
        for (WorldChest entity : repository.findAllByWorldId(worldId)) {
            ChestInventory chest = new ChestInventory(entity.getContainerSize());
            for (ChestItem item : entity.getItems()) {
                chest.restoreSlot(item.getSlot(), item.getItemType(), item.getItemCount(),
                        item.getDurability(), item.getEnchantments(), item.getMapId(),
                        item.getShulkerId(), item.getBucketMobData(),
                        item.getItemComponentData());
            }
            chest.restorePotItemComponents(entity.getPotItemComponents());
            chest.restorePersistenceRevision(entity.getPersistenceRevision());
            storage.load(entity.getPosX(), entity.getPosY(), entity.getPosZ(), chest,
                    entity.getHopperTransferCooldown());
            rememberBinding(worldId, entity.getPosX(), entity.getPosY(), entity.getPosZ(), entity);
        }
    }

    /**
     * [SHULKER-CONTENTS] 좌표 lane 과 참조 lane 을 <b>함께</b> 복원한다. 두 저장소는 27칸의
     * 소유를 주고받으므로 한쪽만 복원하면 재기동 직후의 설치/채굴이 없는 행을 가리킨다.
     */
    public void loadWorld(
            Long worldId, ChestStorage storage, ShulkerContentsStorage shulkerStorage) {
        loadWorld(worldId, storage);
        if (shulkerPersistence != null && shulkerStorage != null) {
            shulkerPersistence.loadWorld(worldId, shulkerStorage);
        }
    }

    /** 틱 스레드에서 dirty 좌표와 내용물을 복사하고, 쓰기는 공용 persistence executor에 맡긴다. */
    public void flushDirty(Long worldId, ChestStorage storage) {
        flushDirty(worldId, storage, null);
    }

    /**
     * [SHULKER-CONTENTS] 좌표 블록 엔티티 lane 과 27칸 참조 lane 을 <b>한 트랜잭션</b>으로
     * 커밋한다. 셜커 설치·채굴은 27칸의 소유를 두 저장소 사이에서 옮기므로, 한쪽만 커밋되면
     * 27칸이 복제되거나(설치 후 재기동) 사라진다(채굴 후 재기동). 실패하면 두 lane 의 dirty 를
     * 함께 되돌려 다음 주기가 같은 쌍을 다시 시도한다.
     */
    public void flushDirty(
            Long worldId, ChestStorage storage, ShulkerContentsStorage shulkerStorage) {
        ShulkerContentsStorage.Batch shulkerBatch = shulkerStorage == null
                ? ShulkerContentsStorage.Batch.EMPTY : shulkerStorage.drainDirty();
        ShulkerContentsPersistenceService.Snapshot shulkerSnapshot =
                shulkerPersistence == null || shulkerStorage == null
                        ? ShulkerContentsPersistenceService.Snapshot.EMPTY
                        : shulkerPersistence.snapshot(shulkerBatch, shulkerStorage);
        List<ChestSnapshot> snapshots = new ArrayList<>();
        for (int[] pos : storage.drainDirty()) {
            ChestInventory chest = storage.peekAt(pos[0], pos[1], pos[2]);
            ChestInventory.PersistenceSnapshot captured = chest == null
                    ? null : chest.persistenceSnapshot();
            if (captured != null && !WorldChest.isSupportedContainerSize(captured.slots())) {
                // One unpersistable row must never roll back (and re-dirty) the whole batch every
                // cycle: that would wedge every other container in this world's save queue.
                log.error("월드 {} 상자 ({},{},{}) 칸 수 {} 는 저장할 수 없음 — 이 행만 제외",
                        worldId, pos[0], pos[1], pos[2], captured.slots());
                continue;
            }
            List<ChestItem> items = captured == null ? List.of() : items(captured);
            snapshots.add(new ChestSnapshot(pos[0], pos[1], pos[2], items, chest == null,
                    captured == null ? 0 : captured.revision(),
                    captured == null ? ChestInventory.SLOTS : captured.slots(),
                    bindingFor(worldId, pos[0], pos[1], pos[2]),
                    storage.hopperCooldownAt(pos[0], pos[1], pos[2]),
                    captured == null ? null : captured.potItemComponents()));
        }
        if (snapshots.isEmpty() && shulkerSnapshot.isEmpty()) return;
        boolean accepted = persistenceExecutor.trySubmit(() -> {
            try {
                List<BindingEffect> effects = new ArrayList<>();
                transactionTemplate.executeWithoutResult(status -> {
                    persist(worldId, snapshots, effects);
                    // [SHULKER-CONTENTS] 같은 트랜잭션 — 27칸의 소유가 두 lane 사이를 옮긴다.
                    if (shulkerPersistence != null) {
                        shulkerPersistence.persist(worldId, shulkerSnapshot);
                    }
                });
                for (BindingEffect effect : effects) applyBindingEffect(effect);
            } catch (RuntimeException exception) {
                restoreDirty(storage, snapshots);
                if (shulkerStorage != null) shulkerStorage.restoreDirty(shulkerBatch);
                log.warn("월드 {} 상자 {}개 flush 실패 — dirty 좌표 복원", worldId, snapshots.size(), exception);
            }
        });
        if (!accepted) {
            restoreDirty(storage, snapshots);
            if (shulkerStorage != null) shulkerStorage.restoreDirty(shulkerBatch);
            log.warn("월드 {} 상자 flush 제출 거부 — dirty 좌표 {} 개 복원",
                    worldId, snapshots.size());
        }
    }

    private static void restoreDirty(ChestStorage storage, List<ChestSnapshot> snapshots) {
        List<int[]> positions = new ArrayList<>(snapshots.size());
        for (ChestSnapshot snapshot : snapshots) {
            positions.add(new int[] { snapshot.x, snapshot.y, snapshot.z });
        }
        storage.restoreDirty(positions);
    }

    private void persist(Long worldId, List<ChestSnapshot> snapshots,
            List<BindingEffect> effects) {
        requireWorldId(worldId);
        List<int[]> positions = new ArrayList<>(snapshots.size());
        for (ChestSnapshot snapshot : snapshots) {
            if (snapshot.binding == null) {
                positions.add(new int[] { snapshot.x, snapshot.y, snapshot.z });
            }
        }
        Map<String, WorldChest> existingByPosition = new HashMap<>();
        if (!positions.isEmpty()) {
            List<WorldChest> existing = repository.findDirtyByWorldId(worldId, positions);
            if (existing != null) {
                for (WorldChest entity : existing) {
                    existingByPosition.put(
                            positionKey(entity.getPosX(), entity.getPosY(), entity.getPosZ()), entity);
                }
            }
        }
        List<WorldChest> changed = new ArrayList<>();
        List<WorldChest> deleted = new ArrayList<>();
        List<PendingSave> pendingSaves = new ArrayList<>();
        for (ChestSnapshot snapshot : snapshots) {
            String key = positionKey(snapshot.x, snapshot.y, snapshot.z);
            ChestPosition position = new ChestPosition(worldId, snapshot.x, snapshot.y, snapshot.z);
            WorldChest entity;
            if (snapshot.binding != null) {
                java.util.Optional<WorldChest> locked = repository.findLockedById(
                        snapshot.binding.id());
                entity = locked == null ? null : locked.orElse(null);
                if (entity == null || !sameRow(entity, snapshot.binding,
                        worldId, snapshot.x, snapshot.y, snapshot.z)
                        || entity.getPersistenceRevision() != snapshot.binding.revision()) {
                    // The row was deleted/recreated or advanced after this resident snapshot.
                    // A stale task must never target the replacement row at this coordinate.
                    continue;
                }
            } else {
                entity = existingByPosition.get(key);
                if (entity != null) {
                    // An unbound cache cannot prove which incarnation it observed. Preserve the
                    // durable row, including canonical metadata, until a fresh load binds it.
                    continue;
                }
            }
            if (snapshot.deleted) {
                if (entity == null) continue;
                deleted.add(entity);
                effects.add(new BindingEffect(position,
                        bindingFrom(entity), true));
                continue;
            }
            boolean created = entity == null;
            if (created) {
                entity = new WorldChest(worldId, snapshot.x, snapshot.y, snapshot.z,
                        snapshot.containerSize);
            }
            entity.requireContainerSize(snapshot.containerSize);
            // A missing row may be a first runtime write at revision 0 or a normal gameplay
            // write at a later revision. Once a row exists, only a strictly newer bound snapshot
            // can replace its contents.
            if (created) {
                entity.replaceItems(snapshot.items);
                if (snapshot.revision > 0) {
                    entity.replaceItemsIfNewer(snapshot.items, snapshot.revision);
                }
                entity.setHopperTransferCooldown(snapshot.hopperTransferCooldown);
                entity.setPotItemComponents(snapshot.potItemComponents);
                changed.add(entity);
                pendingSaves.add(new PendingSave(position, entity));
            } else if (entity.replaceItemsIfNewer(snapshot.items, snapshot.revision)) {
                entity.setHopperTransferCooldown(snapshot.hopperTransferCooldown);
                entity.setPotItemComponents(snapshot.potItemComponents);
                changed.add(entity);
                pendingSaves.add(new PendingSave(position, entity));
            }
        }
        if (!deleted.isEmpty()) repository.deleteAll(deleted);
        if (!changed.isEmpty()) repository.saveAll(changed);
        for (PendingSave pending : pendingSaves) {
            if (pending.entity().getId() != null) {
                effects.add(new BindingEffect(pending.position(),
                        bindingFrom(pending.entity()), false));
            }
        }
    }

    private void rememberBindingAfterCommit(Long worldId, int x, int y, int z,
            WorldChest entity) {
        if (entity == null || entity.getId() == null) return;
        ChestPosition position = new ChestPosition(worldId, x, y, z);
        RowBinding binding = bindingFrom(entity);
        Runnable remember = () -> rowBindings.put(position, binding);
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

    private void rememberBinding(Long worldId, int x, int y, int z, WorldChest entity) {
        if (entity == null || entity.getId() == null) return;
        rowBindings.put(new ChestPosition(worldId, x, y, z),
                bindingFrom(entity));
    }

    private static RowBinding bindingFrom(WorldChest entity) {
        return new RowBinding(entity.getId(), entity.getPersistenceRevision(),
                entity.getContainerSize(), entity.getItems());
    }

    private static List<ChestItem> copyItems(List<ChestItem> source) {
        Objects.requireNonNull(source, "expected chest items required");
        List<ChestItem> copies = new ArrayList<>(source.size());
        for (ChestItem item : source) {
            if (item == null) throw new IllegalArgumentException("null chest item");
            copies.add(new ChestItem(item.getSlot(), item.getItemType(), item.getItemCount(),
                    item.getDurability(), item.getEnchantments(), item.getMapId(),
                    item.getShulkerId(), item.getBucketMobData(), item.getItemComponentData()));
        }
        return List.copyOf(copies);
    }

    private void applyBindingEffect(BindingEffect effect) {
        if (effect.remove()) {
            rowBindings.remove(effect.position(), effect.binding());
        } else {
            rowBindings.put(effect.position(), effect.binding());
        }
    }

    private RowBinding bindingFor(Long worldId, int x, int y, int z) {
        requireWorldId(worldId);
        return rowBindings.get(new ChestPosition(worldId, x, y, z));
    }

    private static boolean sameRow(WorldChest entity, RowBinding binding, Long worldId,
            int x, int y, int z) {
        return entity != null && Objects.equals(entity.getId(), binding.id())
                && Objects.equals(entity.getWorldId(), worldId)
                && entity.getPosX() == x && entity.getPosY() == y && entity.getPosZ() == z;
    }

    private static void requireWorldId(Long worldId) {
        if (worldId == null || worldId <= 0L) {
            throw new IllegalArgumentException("positive world ID required");
        }
    }

    private static String positionKey(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private static final class ChestSnapshot {
        private final int x;
        private final int y;
        private final int z;
        private final List<ChestItem> items;
        private final boolean deleted;
        private final long revision;
        private final int containerSize;
        private final RowBinding binding;
        private final Integer hopperTransferCooldown;
        private final String potItemComponents;

        private ChestSnapshot(int x, int y, int z, List<ChestItem> items, boolean deleted,
                long revision, int containerSize, RowBinding binding, Integer hopperTransferCooldown,
                String potItemComponents) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.items = List.copyOf(items);
            this.deleted = deleted;
            this.revision = revision;
            this.containerSize = containerSize;
            this.binding = binding;
            this.hopperTransferCooldown = hopperTransferCooldown;
            this.potItemComponents = potItemComponents;
        }
    }
}
