package com.gameexpert.enchanting.service;

import com.gameexpert.chest.entity.ChestItem;
import com.gameexpert.engine.EnchantingInventory;
import com.gameexpert.engine.EnchantingStorage;
import com.gameexpert.engine.PersistenceExecutor;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.enchanting.entity.WorldEnchantingTable;
import com.gameexpert.enchanting.repository.WorldEnchantingTableRepository;
import com.gameexpert.state.service.inventory.StaleInventoryMutationException;
import java.util.ArrayList;
import java.util.Comparator;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable, caller-transaction-owned operations for enchanting-table contents. */
@Service
@RequiredArgsConstructor
public class EnchantingPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(EnchantingPersistenceService.class);

    /** Complete coordinate, generation, and item-identity witness for one table. */
    public record ExpectedTable(long rowId, int x, int y, int z, long revision,
            List<ChestItem> expectedItems) {
        public ExpectedTable {
            if (rowId <= 0L || revision < 0L) {
                throw new IllegalArgumentException("enchanting-table revision must be non-negative");
            }
            expectedItems = copyItems(expectedItems);
        }
    }

    private record Position(int x, int y, int z) { }
    private record WorldPosition(long worldId, int x, int y, int z) { }
    private record RowBinding(long rowId, long incarnation, long revision,
            List<ChestItem> expectedItems) {
        private RowBinding {
            if (rowId <= 0L || incarnation <= 0L || revision < 0L) {
                throw new IllegalArgumentException("invalid enchanting-table row binding");
            }
            expectedItems = copyItems(expectedItems);
        }
    }
    private record DirtySnapshot(EnchantingStorage.PersistenceSnapshot state,
            boolean deleted, RowBinding capturedBinding) { }
    private record BindingEffect(WorldPosition position, RowBinding before,
            RowBinding after, boolean remove) { }

    private static final Comparator<ExpectedTable> LOCK_ORDER = Comparator
            .comparingInt(ExpectedTable::x)
            .thenComparingInt(ExpectedTable::y)
            .thenComparingInt(ExpectedTable::z);

    private final WorldEnchantingTableRepository repository;
    private final PersistenceExecutor persistenceExecutor;
    private final TransactionTemplate transactionTemplate;
    private final Map<WorldPosition, RowBinding> rowBindings = new ConcurrentHashMap<>();

    /**
     * Retires all witnessed enchanting-table rows inside the caller-owned transaction.
     * Every row is locked and validated before the first delete, so a stale later target cannot
     * partially retire an earlier target. Missing rows fail closed, including empty revision-zero
     * witnesses, because an explosion must prove the exact durable row it is consuming.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void retireExactJoiningTransaction(Long worldId, List<ExpectedTable> targets) {
        requireWorldId(worldId);
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("exact enchanting-table targets required");
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "exact enchanting-table retirement requires transaction synchronization");
        }

        List<ExpectedTable> ordered = new ArrayList<>(targets);
        Set<Position> positions = new HashSet<>();
        for (ExpectedTable target : ordered) {
            if (target == null) {
                throw new IllegalArgumentException("exact enchanting-table target required");
            }
            if (!positions.add(new Position(target.x(), target.y(), target.z()))) {
                throw new IllegalArgumentException("duplicate exact enchanting-table target");
            }
        }
        ordered.sort(LOCK_ORDER);

        List<WorldEnchantingTable> locked = new ArrayList<>(ordered.size());
        for (ExpectedTable target : ordered) {
            WorldEnchantingTable entity = repository.findLockedAt(
                    worldId, target.x(), target.y(), target.z()).orElse(null);
            if (entity == null || !Objects.equals(entity.getId(), target.rowId())
                    || !entity.matches(target.expectedItems(), target.revision())) {
                throw new StaleInventoryMutationException(
                        "stale exact enchanting-table retirement at " + target.x() + ":"
                                + target.y() + ":" + target.z());
            }
            locked.add(entity);
        }

        repository.deleteAll(locked);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                for (ExpectedTable target : ordered) {
                    WorldPosition position = new WorldPosition(
                            worldId, target.x(), target.y(), target.z());
                    rowBindings.computeIfPresent(position, (ignored, binding) ->
                            binding.rowId() == target.rowId()
                                    && binding.revision() == target.revision()
                                    && binding.expectedItems().equals(target.expectedItems())
                                            ? null : binding);
                }
            }
        });
    }

    /** Captures a complete exact-row witness from a resident inventory on its owner thread. */
    public ExpectedTable captureExactRetirementTarget(Long worldId,
            int x, int y, int z, EnchantingInventory resident) {
        requireWorldId(worldId);
        if (resident == null) {
            throw new StaleInventoryMutationException(
                    "missing resident enchanting table at " + x + ":" + y + ":" + z);
        }
        WorldPosition position = new WorldPosition(worldId, x, y, z);
        RowBinding binding = rowBindings.get(position);
        List<ChestItem> captured = items(List.of(resident.persistenceSnapshot().stacks()));
        if (binding == null || binding.revision() != resident.persistenceRevision()
                || !binding.expectedItems().equals(captured)) {
            throw new StaleInventoryMutationException(
                    "unbound or stale resident enchanting table at " + x + ":" + y + ":" + z);
        }
        return new ExpectedTable(binding.rowId(), x, y, z, binding.revision(), captured);
    }

    /** Restores complete item values and binds each resident table to its durable row incarnation. */
    public void loadWorld(Long worldId, EnchantingStorage storage) {
        requireWorldId(worldId);
        Objects.requireNonNull(storage, "enchanting storage required");
        rowBindings.keySet().removeIf(position -> position.worldId() == worldId.longValue());
        for (WorldEnchantingTable entity : repository.findAllByWorldId(worldId)) {
            EnchantingInventory inventory = new EnchantingInventory();
            inventory.restorePersistenceSnapshot(
                    stacks(entity.snapshotItems()).toArray(PlayerInventory.StackSnapshot[]::new),
                    entity.getPersistenceRevision());
            storage.load(entity.getPosX(), entity.getPosY(), entity.getPosZ(), inventory,
                    entity.getPersistenceRevision());
            EnchantingStorage.PersistenceSnapshot resident = storage.persistenceSnapshotAt(
                    entity.getPosX(), entity.getPosY(), entity.getPosZ());
            rowBindings.put(position(worldId, resident), binding(entity, resident.incarnation()));
        }
    }

    /** Captures dirty coordinates on the owner thread and writes them on the serial executor. */
    public void flushDirty(Long worldId, EnchantingStorage storage) {
        requireWorldId(worldId);
        Objects.requireNonNull(storage, "enchanting storage required");
        List<DirtySnapshot> snapshots = new ArrayList<>();
        for (int[] coordinate : storage.drainDirty()) {
            WorldPosition position = new WorldPosition(worldId,
                    coordinate[0], coordinate[1], coordinate[2]);
            EnchantingStorage.PersistenceSnapshot state = storage.persistenceSnapshotAt(
                    coordinate[0], coordinate[1], coordinate[2]);
            if (state == null) {
                RowBinding bound = rowBindings.get(position);
                snapshots.add(new DirtySnapshot(new EnchantingStorage.PersistenceSnapshot(
                        coordinate[0], coordinate[1], coordinate[2],
                        bound == null ? 1L : bound.incarnation(),
                        bound == null ? 0L : bound.revision(), emptyStacks()), true, bound));
            } else {
                RowBinding bound = rowBindings.get(position);
                snapshots.add(new DirtySnapshot(state, false,
                        bound != null && bound.incarnation() == state.incarnation() ? bound : null));
            }
        }
        if (snapshots.isEmpty()) return;
        boolean accepted = persistenceExecutor.trySubmit(() -> {
            try {
                List<BindingEffect> effects = new ArrayList<>();
                transactionTemplate.executeWithoutResult(ignored -> persist(worldId, snapshots, effects));
            } catch (RuntimeException failure) {
                restoreDirty(storage, snapshots);
                log.warn("world {} enchanting-table flush failed; restored {} dirty coordinates",
                        worldId, snapshots.size(), failure);
            }
        });
        if (!accepted) {
            restoreDirty(storage, snapshots);
            log.warn("world {} enchanting-table flush rejected; restored {} dirty coordinates",
                    worldId, snapshots.size());
        }
    }

    private void persist(Long worldId, List<DirtySnapshot> snapshots,
            List<BindingEffect> effects) {
        for (DirtySnapshot snapshot : snapshots) {
            EnchantingStorage.PersistenceSnapshot state = snapshot.state();
            WorldPosition position = position(worldId, state);
            RowBinding current = snapshot.capturedBinding();
            RowBinding advanced = rowBindings.get(position);
            if (advanced != null && advanced.incarnation() == state.incarnation()
                    && (current == null || current.rowId() == advanced.rowId()
                            && advanced.revision() >= current.revision())) {
                current = advanced;
            }
            WorldEnchantingTable entity = repository.findLockedAt(
                    worldId, state.x(), state.y(), state.z()).orElse(null);
            if (current != null && !sameBoundRow(entity, worldId, position, current)) continue;
            if (current == null && entity != null) continue;

            if (snapshot.deleted()) {
                if (entity != null) {
                    repository.delete(entity);
                    effects.add(new BindingEffect(position, current, null, true));
                }
                continue;
            }

            List<ChestItem> items = items(state.stacks());
            boolean created = entity == null;
            if (created) entity = new WorldEnchantingTable(worldId, state.x(), state.y(), state.z());
            if (created && state.revision() == 0L) {
                if (!items.isEmpty()) {
                    throw new IllegalStateException("revision-zero enchanting table cannot contain items");
                }
            } else if (!entity.replaceIfNewer(items, state.revision())) {
                continue;
            }
            entity = repository.save(entity);
            if (entity.getId() == null) repository.flush();
            RowBinding next = binding(entity, state.incarnation());
            effects.add(new BindingEffect(position, current, next, false));
        }
        if (!effects.isEmpty() && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { applyEffects(effects); }
            });
        }
    }

    private void applyEffects(List<BindingEffect> effects) {
        for (BindingEffect effect : effects) {
            if (effect.remove()) {
                if (effect.before() != null) rowBindings.remove(effect.position(), effect.before());
            } else if (effect.before() == null) {
                rowBindings.compute(effect.position(), (ignored, existing) ->
                        existing == null || existing.incarnation() == effect.after().incarnation()
                                ? effect.after() : existing);
            } else {
                rowBindings.replace(effect.position(), effect.before(), effect.after());
            }
        }
    }

    private static void restoreDirty(EnchantingStorage storage, List<DirtySnapshot> snapshots) {
        storage.restoreDirty(snapshots.stream().map(snapshot -> new int[] {
                snapshot.state().x(), snapshot.state().y(), snapshot.state().z() }).toList());
    }

    private static RowBinding binding(WorldEnchantingTable entity, long incarnation) {
        return new RowBinding(entity.getId(), incarnation, entity.getPersistenceRevision(),
                entity.snapshotItems());
    }

    private static boolean sameBoundRow(WorldEnchantingTable entity, Long worldId,
            WorldPosition position, RowBinding binding) {
        return entity != null && Objects.equals(entity.getId(), binding.rowId())
                && Objects.equals(entity.getWorldId(), worldId)
                && entity.getPosX() == position.x() && entity.getPosY() == position.y()
                && entity.getPosZ() == position.z()
                && entity.matches(binding.expectedItems(), binding.revision());
    }

    private static WorldPosition position(Long worldId,
            EnchantingStorage.PersistenceSnapshot snapshot) {
        return new WorldPosition(worldId, snapshot.x(), snapshot.y(), snapshot.z());
    }

    private static List<PlayerInventory.StackSnapshot> stacks(List<ChestItem> items) {
        PlayerInventory.StackSnapshot[] stacks = {
                PlayerInventory.StackSnapshot.EMPTY, PlayerInventory.StackSnapshot.EMPTY };
        for (ChestItem item : items) {
            short type = item.getItemType();
            stacks[item.getSlot()] = new PlayerInventory.StackSnapshot(type, item.getItemCount(),
                    item.getDurability() == null ? 0 : item.getDurability(),
                    item.enchantmentsOrZero(), item.getMapId() == null ? 0 : item.getMapId(),
                    item.shulkerIdOrZero(), item.getBucketMobData(), item.getItemComponentData());
        }
        return List.of(stacks);
    }

    private static List<PlayerInventory.StackSnapshot> emptyStacks() {
        return List.of(PlayerInventory.StackSnapshot.EMPTY, PlayerInventory.StackSnapshot.EMPTY);
    }

    private static List<ChestItem> items(List<PlayerInventory.StackSnapshot> stacks) {
        List<ChestItem> items = new ArrayList<>();
        for (int slot = 0; slot < stacks.size(); slot++) {
            PlayerInventory.StackSnapshot stack = stacks.get(slot);
            if (stack.isEmpty()) continue;
            items.add(new ChestItem(slot, stack.itemType(), stack.count(),
                    PlayerInventory.isDurable(stack.itemType()) ? stack.durability() : null,
                    stack.enchantments() == 0L ? null : stack.enchantments(),
                    stack.mapId() == 0 ? null : stack.mapId(),
                    stack.shulkerId() == 0 ? null : stack.shulkerId(),
                    stack.bucketMobData(), stack.itemComponentData()));
        }
        return List.copyOf(items);
    }

    private static List<ChestItem> copyItems(List<ChestItem> items) {
        if (items == null) throw new IllegalArgumentException("enchanting items required");
        return items.stream().map(item -> new ChestItem(item.getSlot(), item.getItemType(),
                item.getItemCount(), item.getDurability(),
                item.enchantmentsOrZero() == 0L ? null : item.enchantmentsOrZero(), item.getMapId(),
                item.getShulkerId(), item.getBucketMobData(), item.getItemComponentData())).toList();
    }

    private static void requireWorldId(Long worldId) {
        if (worldId == null || worldId <= 0L) {
            throw new IllegalArgumentException("positive world ID required");
        }
    }
}
