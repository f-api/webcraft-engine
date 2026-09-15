package com.gameexpert.campfire.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.gameexpert.campfire.entity.WorldCampfire;
import com.gameexpert.campfire.repository.WorldCampfireRepository;
import com.gameexpert.engine.CampfireInventory;
import com.gameexpert.engine.CampfireStorage;
import com.gameexpert.engine.PersistenceExecutor;
import com.gameexpert.state.service.inventory.InventoryMutationTarget;
import com.gameexpert.state.service.inventory.StaleInventoryMutationException;

import lombok.RequiredArgsConstructor;

/** 모닥불 런타임 캐시와 좌표별 JPA 스냅샷 사이의 비동기 dirty 배치 배선입니다. */
@Service
@RequiredArgsConstructor
public class CampfirePersistenceService {

    /** 정산 시작 전에 캡처한 모닥불 행 증인입니다. revision 0 구형 행도 전체 직렬화 상태로 비교합니다. */
    public record ExactRetirementTarget(long rowId, int x, int y, int z,
            long expectedRevision, short[] expectedItemTypes, int[] expectedCookTicks) {
        public ExactRetirementTarget {
            if (rowId <= 0L || expectedRevision < 0L || expectedRevision == Long.MAX_VALUE) {
                throw new IllegalArgumentException("invalid exact campfire retirement identity");
            }
            WorldCampfire.validateSnapshot(expectedItemTypes, expectedCookTicks);
            expectedItemTypes = expectedItemTypes.clone();
            expectedCookTicks = expectedCookTicks.clone();
        }

        @Override public short[] expectedItemTypes() { return expectedItemTypes.clone(); }
        @Override public int[] expectedCookTicks() { return expectedCookTicks.clone(); }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ExactRetirementTarget target)) return false;
            return rowId == target.rowId && x == target.x && y == target.y && z == target.z
                    && expectedRevision == target.expectedRevision
                    && Arrays.equals(expectedItemTypes, target.expectedItemTypes)
                    && Arrays.equals(expectedCookTicks, target.expectedCookTicks);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(rowId, x, y, z, expectedRevision);
            result = 31 * result + Arrays.hashCode(expectedItemTypes);
            return 31 * result + Arrays.hashCode(expectedCookTicks);
        }
    }

    private record CampfirePosition(long worldId, int x, int y, int z) { }

    /** DB row incarnation, resident incarnation, and complete serialized campfire state. */
    private record RowBinding(long rowId, long residentIncarnation, long revision,
            short[] expectedItemTypes, int[] expectedCookTicks) {
        private RowBinding {
            if (rowId <= 0L || residentIncarnation < 0L || revision < 0L
                    || revision == Long.MAX_VALUE) {
                throw new IllegalArgumentException("invalid campfire row binding");
            }
            WorldCampfire.validateSnapshot(expectedItemTypes, expectedCookTicks);
            expectedItemTypes = expectedItemTypes.clone();
            expectedCookTicks = expectedCookTicks.clone();
        }

        @Override public short[] expectedItemTypes() { return expectedItemTypes.clone(); }
        @Override public int[] expectedCookTicks() { return expectedCookTicks.clone(); }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RowBinding binding)) return false;
            return rowId == binding.rowId && residentIncarnation == binding.residentIncarnation
                    && revision == binding.revision
                    && Arrays.equals(expectedItemTypes, binding.expectedItemTypes)
                    && Arrays.equals(expectedCookTicks, binding.expectedCookTicks);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(rowId, residentIncarnation, revision);
            result = 31 * result + Arrays.hashCode(expectedItemTypes);
            return 31 * result + Arrays.hashCode(expectedCookTicks);
        }
    }

    private record PendingSave(CampfirePosition position, WorldCampfire entity,
            CampfireInventory resident, long residentIncarnation) { }

    private record BindingEffect(CampfirePosition position, RowBinding binding,
            CampfireInventory resident, boolean remove) { }

    private record CampfireSnapshot(int x, int y, int z,
            CampfireStorage.PersistenceSnapshot state, boolean deleted, RowBinding binding,
            long runtimeIncarnation, CampfireInventory resident) {
        private CampfireSnapshot {
            if (deleted) {
                if (state != null || resident != null || runtimeIncarnation < 0L) {
                    throw new IllegalArgumentException("invalid deleted campfire snapshot");
                }
            } else if (state == null || resident == null
                    || runtimeIncarnation != state.incarnation()) {
                throw new IllegalArgumentException("invalid resident campfire snapshot");
            }
        }

        private static CampfireSnapshot deleted(int x, int y, int z, RowBinding binding,
                long runtimeIncarnation) {
            return new CampfireSnapshot(x, y, z, null, true, binding, runtimeIncarnation, null);
        }
    }

    private static final Comparator<CampfireSnapshot> POSITION_ORDER = Comparator
            .comparingInt(CampfireSnapshot::x)
            .thenComparingInt(CampfireSnapshot::y)
            .thenComparingInt(CampfireSnapshot::z);

    private static final Logger log = LoggerFactory.getLogger(CampfirePersistenceService.class);

    private final WorldCampfireRepository repository;
    private final PersistenceExecutor persistenceExecutor;
    private final TransactionTemplate transactionTemplate;
    private final Map<CampfirePosition, RowBinding> rowBindings = new ConcurrentHashMap<>();
    /** 커밋된 행 binding에 대응하는 현재 resident object identity입니다. */
    private final Map<CampfirePosition, CampfireInventory> residentBindings =
            new ConcurrentHashMap<>();
    private final Object bindingPublicationLock = new Object();

    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceExactSnapshotJoiningTransaction(Long worldId,
            InventoryMutationTarget.Campfire snapshot) {
        requireWorldId(worldId);
        validateMutationSnapshot(snapshot);
        InventoryMutationTarget.Position pos = snapshot.position();
        WorldCampfire entity = lockedAt(worldId, pos.x(), pos.y(), pos.z());
        RowBinding binding = bindingFor(worldId, pos.x(), pos.y(), pos.z());
        if (entity == null) {
            if (binding != null || snapshot.revision() != 1L) {
                throw stale("campfire row identity is not available at " + pos);
            }
            entity = new WorldCampfire(worldId, pos.x(), pos.y(), pos.z());
        } else if (!sameBoundRow(entity, worldId, pos.x(), pos.y(), pos.z(), binding)) {
            throw stale("campfire row identity changed at " + pos);
        }
        if (!entity.replaceIfNewer(snapshot.itemTypes(), snapshot.cookTicks(), snapshot.revision())) {
            throw stale("campfire " + pos);
        }
        repository.save(entity);
        rememberBindingAfterCommit(worldId, pos.x(), pos.y(), pos.z(), entity);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean replaceExactSnapshotAtExpectedRevisionJoiningTransaction(Long worldId,
            InventoryMutationTarget.Campfire snapshot, long expectedRevision) {
        requireWorldId(worldId);
        validateMutationSnapshot(snapshot);
        requireRevision(expectedRevision);
        InventoryMutationTarget.Position pos = snapshot.position();
        WorldCampfire entity = lockedAt(worldId, pos.x(), pos.y(), pos.z());
        RowBinding binding = bindingFor(worldId, pos.x(), pos.y(), pos.z());
        if (entity == null) {
            if (binding != null || expectedRevision != 0L) return false;
        } else {
            if (!sameBoundRow(entity, worldId, pos.x(), pos.y(), pos.z(), binding)) return false;
            if (entity.getPersistenceRevision() != expectedRevision) return false;
        }
        requireNextRevision(snapshot.revision(), expectedRevision);
        if (entity == null) entity = new WorldCampfire(worldId, pos.x(), pos.y(), pos.z());
        if (!entity.replaceIfNewer(snapshot.itemTypes(), snapshot.cookTicks(), snapshot.revision())) {
            throw new IllegalStateException("validated campfire revision changed in transaction");
        }
        repository.save(entity);
        rememberBindingAfterCommit(worldId, pos.x(), pos.y(), pos.z(), entity);
        return true;
    }

    /**
     * 호출자 트랜잭션 없이 resident만 읽어 완전한 exact-row witness를 캡처합니다.
     * committed binding이 row identity를, resident가 현재 incarnation과 전체 상태를 증명합니다.
     */
    public ExactRetirementTarget captureExactRetirementTarget(Long worldId,
            int x, int y, int z, CampfireInventory current) {
        requireWorldId(worldId);
        if (current == null) {
            throw stale("missing resident campfire at " + x + ":" + y + ":" + z);
        }

        CampfirePosition position = new CampfirePosition(worldId, x, y, z);
        RowBinding binding;
        CampfireInventory boundResident;
        synchronized (bindingPublicationLock) {
            binding = rowBindings.get(position);
            boundResident = residentBindings.get(position);
        }
        if (binding == null) throw stale("unbound resident campfire at " + x + ":" + y + ":" + z);

        short[] itemTypes = itemTypes(current);
        int[] cookTicks = cookTicks(current);
        WorldCampfire.validateSnapshot(itemTypes, cookTicks);
        if ((boundResident != null && boundResident != current)
                || current.persistenceRevision() != binding.revision()
                || !Arrays.equals(itemTypes, binding.expectedItemTypes())
                || !Arrays.equals(cookTicks, binding.expectedCookTicks())) {
            throw stale("stale resident campfire at " + x + ":" + y + ":" + z);
        }
        return new ExactRetirementTarget(binding.rowId(), x, y, z, binding.revision(),
                itemTypes, cookTicks);
    }

    /** 호출자 트랜잭션 안에서만 좌표·행·revision·직렬화 상태가 모두 같은 모닥불 행을 삭제합니다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void retireExactJoiningTransaction(Long worldId, ExactRetirementTarget target) {
        requireWorldId(worldId);
        if (target == null) throw new IllegalArgumentException("exact campfire target required");
        WorldCampfire.validateSnapshot(target.expectedItemTypes(), target.expectedCookTicks());
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "exact campfire retirement requires transaction synchronization");
        }

        WorldCampfire entity = lockedAt(worldId, target.x(), target.y(), target.z());
        if (!matchesExact(entity, worldId, target)) {
            throw stale("stale exact campfire retirement at " + target.x() + ":"
                    + target.y() + ":" + target.z());
        }
        CampfirePosition position = new CampfirePosition(worldId, target.x(), target.y(), target.z());
        RowBinding cached = bindingFor(worldId, target.x(), target.y(), target.z());
        if (cached != null && !sameDurableBinding(cached, bindingFrom(entity, cached.residentIncarnation()))) {
            throw stale("stale cached campfire retirement at " + target.x() + ":"
                    + target.y() + ":" + target.z());
        }
        repository.delete(entity);
        RowBinding removedBinding = cached == null
                ? bindingFrom(entity, 0L) : cached;
        registerBindingEffectsAfterCommit(List.of(
                new BindingEffect(position, removedBinding, null, true)));
    }

    /** 월드 삭제 aggregate에서 호출하는 모닥불 행 및 resident cache 정리입니다. */
    @Transactional
    public void deleteWorld(Long worldId) {
        requireWorldId(worldId);
        repository.deleteAllByWorldId(worldId);
        publishAfterCommit(() -> forgetWorldCaches(worldId));
    }

    public void loadWorld(Long worldId, CampfireStorage storage) {
        requireWorldId(worldId);
        if (storage == null) throw new IllegalArgumentException("campfire storage is required");
        List<BindingEffect> loaded = new ArrayList<>();
        List<WorldCampfire> rows = repository.findAllByWorldId(worldId);
        if (rows == null) rows = List.of();
        for (WorldCampfire entity : rows) {
            short[] itemTypes = entity.itemTypes();
            int[] cookTicks = entity.cookTicks();
            WorldCampfire.validateSnapshot(itemTypes, cookTicks);
            CampfireInventory campfire = new CampfireInventory();
            campfire.restore(itemTypes, cookTicks);
            campfire.restorePersistenceRevision(entity.getPersistenceRevision());
            storage.load(entity.getPosX(), entity.getPosY(), entity.getPosZ(), campfire);
            if (entity.getId() != null) {
                CampfireStorage.PersistenceSnapshot resident = storage.persistenceSnapshotAt(
                        entity.getPosX(), entity.getPosY(), entity.getPosZ());
                loaded.add(new BindingEffect(
                        new CampfirePosition(worldId, entity.getPosX(), entity.getPosY(), entity.getPosZ()),
                        bindingFrom(entity, resident.incarnation()), campfire, false));
            }
        }
        publishLoadedBindings(loaded);
    }

    /** 틱 스레드에서는 값만 복사하고 DB 쓰기는 공용 직렬 writer에 맡깁니다. */
    public void flushDirty(Long worldId, CampfireStorage storage) {
        requireWorldId(worldId);
        if (storage == null) throw new IllegalArgumentException("campfire storage is required");
        flushDirtyPositions(worldId, storage, storage.drainDirty());
    }

    /** 조리를 멈춘 한 좌표의 현재 revision을 뒤따르는 음식 투입 CAS보다 먼저 저장합니다. */
    public boolean flushDirtyAt(Long worldId, CampfireStorage storage, int x, int y, int z) {
        requireWorldId(worldId);
        if (storage == null) throw new IllegalArgumentException("campfire storage is required");
        return flushDirtyPositions(worldId, storage, storage.drainDirtyAt(x, y, z));
    }

    private boolean flushDirtyPositions(Long worldId, CampfireStorage storage, List<int[]> drained) {
        if (drained.isEmpty()) return true;

        List<CampfireSnapshot> snapshots = new ArrayList<>(drained.size());
        try {
            for (int[] pos : drained) {
                CampfireStorage.PersistenceSnapshot state = storage.persistenceSnapshotAt(
                        pos[0], pos[1], pos[2]);
                RowBinding binding = bindingFor(worldId, pos[0], pos[1], pos[2]);
                if (state == null) {
                    long incarnation = storage.removedIncarnationAt(pos[0], pos[1], pos[2]);
                    if (incarnation == 0L && binding != null) {
                        incarnation = binding.residentIncarnation();
                    }
                    snapshots.add(CampfireSnapshot.deleted(
                            pos[0], pos[1], pos[2], binding, incarnation));
                    continue;
                }
                CampfireInventory resident = storage.peekAt(pos[0], pos[1], pos[2]);
                if (resident == null) throw stale("missing resident campfire snapshot");
                validateResidentCapture(binding, resident, state,
                        new CampfirePosition(worldId, pos[0], pos[1], pos[2]));
                snapshots.add(new CampfireSnapshot(pos[0], pos[1], pos[2], state, false,
                        binding, state.incarnation(), resident));
            }
        } catch (RuntimeException exception) {
            storage.restoreDirty(drained);
            throw exception;
        }

        boolean accepted = persistenceExecutor.trySubmit(() -> {
            try {
                transactionTemplate.executeWithoutResult(status -> persist(worldId, storage, snapshots));
            } catch (RuntimeException exception) {
                restoreDirty(storage, snapshots);
                log.warn("월드 {} 모닥불 {}개 flush 실패 — dirty 좌표 복원",
                        worldId, snapshots.size(), exception);
            }
        });
        if (!accepted) {
            restoreDirty(storage, snapshots);
            log.warn("월드 {} 모닥불 flush 제출 거부 — dirty 좌표 {} 개 복원",
                    worldId, snapshots.size());
        }
        return accepted;
    }

    private void persist(Long worldId, CampfireStorage storage,
            List<CampfireSnapshot> snapshots) {
        requireWorldId(worldId);
        List<CampfireSnapshot> ordered = new ArrayList<>(snapshots);
        ordered.sort(POSITION_ORDER);
        Map<String, WorldCampfire> hintedRows = new HashMap<>();
        List<int[]> unboundPositions = new ArrayList<>();
        for (CampfireSnapshot snapshot : ordered) {
            requireRuntimeSnapshot(storage, snapshot);
            if (snapshot.binding() == null) {
                unboundPositions.add(new int[] { snapshot.x(), snapshot.y(), snapshot.z() });
            }
        }
        if (!unboundPositions.isEmpty()) {
            List<WorldCampfire> rows = repository.findDirtyByWorldId(worldId, unboundPositions);
            if (rows == null) throw new IllegalStateException("campfire dirty-row query returned null");
            for (WorldCampfire row : rows) {
                hintedRows.put(positionKey(row.getPosX(), row.getPosY(), row.getPosZ()), row);
            }
        }

        List<WorldCampfire> changed = new ArrayList<>();
        List<PendingSave> pendingSaves = new ArrayList<>();
        List<WorldCampfire> deleted = new ArrayList<>();
        List<BindingEffect> effects = new ArrayList<>();
        for (CampfireSnapshot snapshot : ordered) {
            CampfirePosition position = new CampfirePosition(
                    worldId, snapshot.x(), snapshot.y(), snapshot.z());
            String key = positionKey(snapshot.x(), snapshot.y(), snapshot.z());
            WorldCampfire entity = lockedAt(worldId, snapshot.x(), snapshot.y(), snapshot.z());
            if (snapshot.binding() != null) {
                if (!sameBoundRow(entity, worldId, snapshot.x(), snapshot.y(), snapshot.z(),
                        snapshot.binding())) {
                    throw stale("campfire dirty row identity changed at " + key);
                }
            } else if (hintedRows.containsKey(key) || entity != null) {
                throw stale("unbound campfire dirty row cannot select an existing row at " + key);
            }

            if (snapshot.deleted()) {
                if (entity == null) {
                    if (snapshot.binding() != null) {
                        throw stale("campfire deleted row disappeared at " + key);
                    }
                    continue;
                }
                deleted.add(entity);
                effects.add(new BindingEffect(position, snapshot.binding(), null, true));
                continue;
            }

            CampfireStorage.PersistenceSnapshot state = snapshot.state();
            if (entity == null) {
                entity = new WorldCampfire(worldId, snapshot.x(), snapshot.y(), snapshot.z());
            } else if (entity.getPersistenceRevision() >= state.revision()) {
                if (!sameState(entity, state)) {
                    throw stale("out-of-order campfire dirty state at " + key);
                }
                continue;
            }
            if (!entity.replaceIfNewer(state.itemTypes(), state.cookTicks(), state.revision())) {
                throw stale("campfire dirty revision changed at " + key);
            }
            changed.add(entity);
            pendingSaves.add(new PendingSave(position, entity, snapshot.resident(),
                    snapshot.runtimeIncarnation()));
        }
        if (!deleted.isEmpty()) repository.deleteAll(deleted);
        if (!changed.isEmpty()) repository.saveAll(changed);
        for (PendingSave pending : pendingSaves) {
            if (pending.entity().getId() != null) {
                effects.add(new BindingEffect(pending.position(),
                        bindingFrom(pending.entity(), pending.residentIncarnation()),
                        pending.resident(), false));
            }
        }
        registerBindingEffectsAfterCommit(effects);
    }

    private void validateResidentCapture(RowBinding binding, CampfireInventory resident,
            CampfireStorage.PersistenceSnapshot state, CampfirePosition position) {
        if (binding != null && binding.residentIncarnation() > 0L
                && binding.residentIncarnation() != state.incarnation()) {
            throw stale("campfire resident incarnation changed");
        }
        synchronized (bindingPublicationLock) {
            CampfireInventory boundResident = residentBindings.get(position);
            if (boundResident != null && boundResident != resident) {
                throw stale("campfire resident object changed");
            }
        }
    }

    private void requireRuntimeSnapshot(CampfireStorage storage, CampfireSnapshot snapshot) {
        CampfireStorage.PersistenceSnapshot current = storage.persistenceSnapshotAt(
                snapshot.x(), snapshot.y(), snapshot.z());
        if (snapshot.deleted()) {
            if (current != null) throw stale("campfire was recreated before dirty delete");
            if (snapshot.runtimeIncarnation() > 0L
                    && storage.removedIncarnationAt(snapshot.x(), snapshot.y(), snapshot.z())
                            != snapshot.runtimeIncarnation()) {
                throw stale("campfire deletion incarnation changed");
            }
            return;
        }
        if (current == null || !current.equals(snapshot.state())
                || storage.peekAt(snapshot.x(), snapshot.y(), snapshot.z()) != snapshot.resident()) {
            throw stale("campfire resident changed before dirty flush");
        }
    }

    private void publishLoadedBindings(List<BindingEffect> loaded) {
        if (loaded.isEmpty()) return;
        publishAfterCommit(() -> loaded.forEach(this::applyBindingEffect));
    }

    private void rememberBindingAfterCommit(Long worldId, int x, int y, int z,
            WorldCampfire entity) {
        if (entity == null || entity.getId() == null) return;
        CampfirePosition position = new CampfirePosition(worldId, x, y, z);
        RowBinding previous = bindingFor(worldId, x, y, z);
        long residentIncarnation = previous != null && previous.rowId() == entity.getId()
                ? previous.residentIncarnation() : 0L;
        registerBindingEffectsAfterCommit(List.of(new BindingEffect(
                position, bindingFrom(entity, residentIncarnation), null, false)));
    }

    private void registerBindingEffectsAfterCommit(List<BindingEffect> effects) {
        if (effects.isEmpty()) return;
        publishAfterCommit(() -> effects.forEach(this::applyBindingEffect));
    }

    /** synchronization이 없는 실제 transaction에서는 rollback 전 cache mutation을 금지합니다. */
    private void publishAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        } else if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
        }
    }

    private void applyBindingEffect(BindingEffect effect) {
        if (effect == null || effect.binding() == null) return;
        synchronized (bindingPublicationLock) {
            RowBinding current = rowBindings.get(effect.position());
            if (effect.remove()) {
                if (sameBinding(current, effect.binding())) {
                    rowBindings.remove(effect.position(), current);
                    residentBindings.remove(effect.position());
                }
                return;
            }

            boolean durableAdvanced = current == null
                    || compareDurable(effect.binding(), current) > 0;
            boolean residentAdvanced = current == null
                    || effect.binding().residentIncarnation() > current.residentIncarnation();
            if (current != null) {
                int durableOrder = compareDurable(effect.binding(), current);
                if (durableOrder < 0
                        || (durableOrder == 0 && !sameDurableBinding(effect.binding(), current))
                        || (durableOrder == 0
                                && effect.binding().residentIncarnation()
                                        < current.residentIncarnation())) {
                    return;
                }
            }
            rowBindings.put(effect.position(), effect.binding());
            if (effect.resident() != null && effect.binding().residentIncarnation() > 0L) {
                CampfireInventory previousResident = residentBindings.get(effect.position());
                if (previousResident == null || durableAdvanced || residentAdvanced
                        || previousResident == effect.resident()) {
                    residentBindings.put(effect.position(), effect.resident());
                }
            } else if (durableAdvanced || residentAdvanced) {
                residentBindings.remove(effect.position());
            }
        }
    }

    private void forgetWorldCaches(Long worldId) {
        synchronized (bindingPublicationLock) {
            rowBindings.keySet().removeIf(position -> position.worldId() == worldId.longValue());
            residentBindings.keySet().removeIf(position -> position.worldId() == worldId.longValue());
        }
    }

    private RowBinding bindingFor(Long worldId, int x, int y, int z) {
        requireWorldId(worldId);
        synchronized (bindingPublicationLock) {
            return rowBindings.get(new CampfirePosition(worldId, x, y, z));
        }
    }

    private static WorldCampfire lockedAt(WorldCampfireRepository repository, Long worldId,
            int x, int y, int z) {
        Optional<WorldCampfire> locked = repository
                .findLockedByWorldIdAndPosXAndPosYAndPosZ(worldId, x, y, z);
        return locked == null ? null : locked.orElse(null);
    }

    private WorldCampfire lockedAt(Long worldId, int x, int y, int z) {
        return lockedAt(repository, worldId, x, y, z);
    }

    private static RowBinding bindingFrom(WorldCampfire entity, long residentIncarnation) {
        return new RowBinding(entity.getId(), residentIncarnation,
                entity.getPersistenceRevision(), entity.itemTypes(), entity.cookTicks());
    }

    private static boolean sameBoundRow(WorldCampfire entity, Long worldId,
            int x, int y, int z, RowBinding binding) {
        return entity != null && binding != null
                && Objects.equals(entity.getId(), binding.rowId())
                && Objects.equals(entity.getWorldId(), worldId)
                && entity.getPosX() == x && entity.getPosY() == y && entity.getPosZ() == z
                && entity.getPersistenceRevision() == binding.revision()
                && Arrays.equals(entity.itemTypes(), binding.expectedItemTypes())
                && Arrays.equals(entity.cookTicks(), binding.expectedCookTicks());
    }

    private static boolean sameState(WorldCampfire entity,
            CampfireStorage.PersistenceSnapshot state) {
        return entity != null
                && entity.getPersistenceRevision() == state.revision()
                && Arrays.equals(entity.itemTypes(), state.itemTypes())
                && Arrays.equals(entity.cookTicks(), state.cookTicks());
    }

    private static boolean sameDurableBinding(RowBinding left, RowBinding right) {
        return left != null && right != null
                && left.rowId() == right.rowId()
                && left.revision() == right.revision()
                && Arrays.equals(left.expectedItemTypes(), right.expectedItemTypes())
                && Arrays.equals(left.expectedCookTicks(), right.expectedCookTicks());
    }

    private static boolean sameBinding(RowBinding left, RowBinding right) {
        return sameDurableBinding(left, right)
                && left.residentIncarnation() == right.residentIncarnation();
    }

    private static int compareDurable(RowBinding left, RowBinding right) {
        int rowOrder = Long.compare(left.rowId(), right.rowId());
        return rowOrder != 0 ? rowOrder : Long.compare(left.revision(), right.revision());
    }

    private static short[] itemTypes(CampfireInventory campfire) {
        short[] itemTypes = new short[CampfireInventory.SLOTS];
        for (int slot = 0; slot < CampfireInventory.SLOTS; slot++) {
            itemTypes[slot] = campfire.itemType(slot);
        }
        return itemTypes;
    }

    private static int[] cookTicks(CampfireInventory campfire) {
        int[] cookTicks = new int[CampfireInventory.SLOTS];
        for (int slot = 0; slot < CampfireInventory.SLOTS; slot++) {
            cookTicks[slot] = campfire.cookTicks(slot);
        }
        return cookTicks;
    }

    private static String positionKey(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private static boolean matchesExact(WorldCampfire entity, Long worldId,
            ExactRetirementTarget target) {
        return entity != null
                && Objects.equals(entity.getId(), target.rowId())
                && Objects.equals(entity.getWorldId(), worldId)
                && entity.getPosX() == target.x()
                && entity.getPosY() == target.y()
                && entity.getPosZ() == target.z()
                && entity.getPersistenceRevision() == target.expectedRevision()
                && Arrays.equals(entity.itemTypes(), target.expectedItemTypes())
                && Arrays.equals(entity.cookTicks(), target.expectedCookTicks());
    }

    private static void validateMutationSnapshot(InventoryMutationTarget.Campfire snapshot) {
        if (snapshot == null || snapshot.position() == null) {
            throw new IllegalArgumentException("campfire snapshot and position are required");
        }
        WorldCampfire.validateSnapshot(snapshot.itemTypes(), snapshot.cookTicks());
    }

    private static void requireNextRevision(long revision, long expectedRevision) {
        requireRevision(expectedRevision);
        if (revision != expectedRevision + 1L) {
            throw new IllegalArgumentException("campfire revision must advance expected revision once");
        }
    }

    private static void requireRevision(long revision) {
        if (revision < 0L || revision == Long.MAX_VALUE) {
            throw new IllegalArgumentException("campfire revision must be non-negative and finite");
        }
    }

    private static StaleInventoryMutationException stale(String message) {
        return new StaleInventoryMutationException(message);
    }

    private static void restoreDirty(CampfireStorage storage, List<CampfireSnapshot> snapshots) {
        List<int[]> positions = new ArrayList<>(snapshots.size());
        for (CampfireSnapshot snapshot : snapshots) {
            positions.add(new int[] { snapshot.x(), snapshot.y(), snapshot.z() });
        }
        storage.restoreDirty(positions);
    }

    private static void requireWorldId(Long worldId) {
        if (worldId == null || worldId <= 0L) {
            throw new IllegalArgumentException("positive world ID required");
        }
    }
}
