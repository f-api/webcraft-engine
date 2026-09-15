package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.validation.MovementLimits;
import com.gameexpert.terrain.Blocks;

/** 월드 틱 스레드가 소유하는 좌표별 인챈트 테이블 입력 칸입니다. */
public final class EnchantingStorage {
    private record Coordinate(int x, int y, int z) { }

    private static final class Entry {
        private final EnchantingInventory inventory;
        private final long incarnation;
        private Entry(EnchantingInventory inventory, long incarnation) {
            this.inventory = inventory;
            this.incarnation = incarnation;
        }
    }

    /** 좌표와 incarnation에 묶인, component-complete 불변 저장 스냅샷입니다. */
    public record PersistenceSnapshot(int x, int y, int z, long incarnation, long revision,
            List<PlayerInventory.StackSnapshot> stacks) {
        public PersistenceSnapshot {
            coordinate(x, y, z);
            if (incarnation <= 0) throw new IllegalArgumentException("invalid enchanting incarnation");
            if (revision < 0) throw new IllegalArgumentException("invalid enchanting revision");
            if (stacks == null || stacks.size() != EnchantingInventory.SLOTS) {
                throw new IllegalArgumentException("enchanting snapshot must contain two slots");
            }
            stacks = List.copyOf(stacks);
            if (stacks.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("enchanting snapshot stack is required");
            }
        }
        @Override public List<PlayerInventory.StackSnapshot> stacks() { return List.copyOf(stacks); }
    }

    /** prepare와 commit/rollback 사이의 관찰 전용 배타 토큰입니다. */
    public static final class RetirementLease {
        private enum State { ACTIVE, COMMITTED, ROLLED_BACK }
        private final EnchantingStorage owner;
        private final Coordinate coordinate;
        private final PersistenceSnapshot snapshot;
        private final long token;
        private State state = State.ACTIVE;
        private RetirementLease(EnchantingStorage owner, Coordinate coordinate,
                PersistenceSnapshot snapshot, long token) {
            this.owner = owner;
            this.coordinate = coordinate;
            this.snapshot = snapshot;
            this.token = token;
        }
        public PersistenceSnapshot snapshot() { return snapshot; }
        public boolean active() { return state == State.ACTIVE; }
    }

    private final Map<Coordinate, Entry> tables = new HashMap<>();
    private final Map<EnchantingInventory, Coordinate> owners = new IdentityHashMap<>();
    private final Map<Coordinate, RetirementLease> retirementLeases = new HashMap<>();
    private final Set<Coordinate> dirty = new HashSet<>();
    private long nextIncarnation = 1L;
    private long nextLeaseToken = 1L;

    /** ChestStorage와 같은 실제 월드 경계를 검증합니다. */
    private static Coordinate coordinate(int x, int y, int z) {
        if (x < -MovementLimits.MAX_HORIZONTAL_COORDINATE
                || x > MovementLimits.MAX_HORIZONTAL_COORDINATE
                || z < -MovementLimits.MAX_HORIZONTAL_COORDINATE
                || z > MovementLimits.MAX_HORIZONTAL_COORDINATE) {
            throw new IllegalArgumentException("enchanting coordinate exceeds the world boundary");
        }
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) {
            throw new IllegalArgumentException("enchanting y coordinate is outside the world");
        }
        return new Coordinate(x, y, z);
    }

    static long key(int x, int y, int z) {
        Coordinate position = coordinate(x, y, z);
        return ChestStorage.key(position.x(), position.y(), position.z());
    }

    static int[] unkey(long key) { return ChestStorage.unkey(key); }

    public synchronized EnchantingInventory openAt(int x, int y, int z) {
        Coordinate position = coordinate(x, y, z);
        Entry existing = tables.get(position);
        if (existing != null) return existing.inventory;
        ensureNoLease(position);
        EnchantingInventory created = new EnchantingInventory();
        bind(position, created, allocateIncarnation());
        return created;
    }

    public synchronized EnchantingInventory peekAt(int x, int y, int z) {
        Entry entry = tables.get(coordinate(x, y, z));
        return entry == null ? null : entry.inventory;
    }

    /** Legacy destruction path. Exact durable settlement should use a RetirementLease. */
    public synchronized List<EnchantingInventory.StoredStack> removeAt(int x, int y, int z) {
        Coordinate position = coordinate(x, y, z);
        ensureNoLease(position);
        Entry removed = tables.get(position);
        if (removed == null) {
            dirty.add(position);
            return List.of();
        }
        List<EnchantingInventory.StoredStack> drained = removed.inventory.drainAll();
        if (!removed.inventory.isEmpty()) {
            throw new IllegalStateException("enchanting inventory drain did not commit");
        }
        tables.remove(position);
        owners.remove(removed.inventory);
        dirty.add(position);
        return drained;
    }

    public synchronized void discardIfEmpty(int x, int y, int z) {
        Coordinate position = coordinate(x, y, z);
        ensureNoLease(position);
        Entry entry = tables.get(position);
        if (entry != null && entry.inventory.isEmpty()) {
            tables.remove(position);
            owners.remove(entry.inventory);
            dirty.add(position);
        }
    }

    public synchronized PersistenceSnapshot persistenceSnapshotAt(int x, int y, int z) {
        Coordinate position = coordinate(x, y, z);
        Entry entry = tables.get(position);
        return entry == null ? null : snapshot(position, entry);
    }

    /** 내용, 좌표, incarnation, revision을 관찰만 하고 해당 좌표의 유일한 lease를 잡습니다. */
    public synchronized RetirementLease prepareExactRetirement(int x, int y, int z) {
        Coordinate position = coordinate(x, y, z);
        if (retirementLeases.containsKey(position)) {
            throw new IllegalStateException("enchanting retirement already prepared");
        }
        Entry entry = tables.get(position);
        if (entry == null) throw new IllegalStateException("enchanting table does not exist");
        PersistenceSnapshot captured = snapshot(position, entry);
        if (captured.revision() >= EnchantingInventory.TERMINAL_REVISION) {
            throw new IllegalStateException("terminal enchanting revision cannot be retired");
        }
        RetirementLease lease = new RetirementLease(this, position, captured, allocateLeaseToken());
        retirementLeases.put(position, lease);
        return lease;
    }

    /** 같은 coordinate/incarnation/revision/content일 때만 drain과 제거를 한 번 게시합니다. */
    public synchronized PersistenceSnapshot commitRetirement(RetirementLease lease) {
        RetirementLease active = requireActiveLease(lease);
        Entry entry = tables.get(active.coordinate);
        if (entry == null || entry.incarnation != active.snapshot.incarnation()) {
            throw new IllegalStateException("stale enchanting retirement incarnation");
        }
        PersistenceSnapshot current = snapshot(active.coordinate, entry);
        if (!current.equals(active.snapshot)) {
            throw new IllegalStateException("stale enchanting retirement snapshot");
        }
        if (current.revision() >= EnchantingInventory.TERMINAL_REVISION) {
            throw new IllegalStateException("terminal enchanting revision cannot be retired");
        }
        if (!entry.inventory.isEmpty()) {
            EnchantingInventory.CommandResult drained = entry.inventory
                    .stage(EnchantingInventory.LogicalCommand.drain(current.revision())).commit();
            if (!drained.committed()) {
                throw new IllegalStateException("enchanting retirement drain was rejected: "
                        + drained.reason());
            }
        }
        tables.remove(active.coordinate);
        owners.remove(entry.inventory);
        dirty.add(active.coordinate);
        retirementLeases.remove(active.coordinate);
        active.state = RetirementLease.State.COMMITTED;
        return active.snapshot;
    }

    /** lease만 해제하며 테이블이나 dirty 상태를 변경하지 않습니다. */
    public synchronized void rollbackRetirement(RetirementLease lease) {
        RetirementLease active = requireActiveLease(lease);
        retirementLeases.remove(active.coordinate);
        active.state = RetirementLease.State.ROLLED_BACK;
    }

    public synchronized void markDirty(int x, int y, int z) {
        Coordinate position = coordinate(x, y, z);
        if (!tables.containsKey(position)) {
            throw new IllegalStateException("cannot mark a missing enchanting table dirty");
        }
        dirty.add(position);
    }

    public synchronized List<int[]> drainDirty() {
        if (dirty.isEmpty()) return List.of();
        List<int[]> result = new ArrayList<>(dirty.size());
        for (Coordinate position : dirty) result.add(new int[] { position.x(), position.y(), position.z() });
        dirty.clear();
        return List.copyOf(result);
    }

    public synchronized void restoreDirty(List<int[]> positions) {
        if (positions == null) throw new IllegalArgumentException("dirty positions are required");
        for (int[] position : positions) {
            if (position == null || position.length != 3) {
                throw new IllegalArgumentException("invalid dirty position");
            }
            dirty.add(coordinate(position[0], position[1], position[2]));
        }
    }

    public synchronized boolean hasDirty() { return !dirty.isEmpty(); }

    /** 이미 정확한 내용을 가진 inventory를 persisted revision에 결합합니다. */
    public synchronized void load(int x, int y, int z, EnchantingInventory inventory,
            long persistedRevision) {
        if (inventory == null) throw new IllegalArgumentException("enchanting inventory is required");
        if (persistedRevision < 0) throw new IllegalArgumentException("invalid enchanting revision");
        Coordinate position = coordinate(x, y, z);
        ensureNoLease(position);
        if (tables.containsKey(position) || owners.containsKey(inventory)) {
            throw new IllegalStateException("enchanting load cannot replace a live binding");
        }
        long currentRevision = inventory.persistenceRevision();
        if (currentRevision == 0L && persistedRevision != 0L) {
            inventory.restorePersistenceRevision(persistedRevision);
        } else if (currentRevision != persistedRevision) {
            throw new IllegalArgumentException("enchanting inventory revision does not match load");
        }
        bind(position, inventory, allocateIncarnation());
    }

    synchronized int size() { return tables.size(); }

    private void bind(Coordinate position, EnchantingInventory inventory, long incarnation) {
        Coordinate previous = owners.get(inventory);
        if (previous != null && !previous.equals(position)) {
            throw new IllegalArgumentException("one enchanting inventory cannot alias coordinates");
        }
        Entry replaced = tables.get(position);
        if (replaced != null && replaced.inventory != inventory) owners.remove(replaced.inventory);
        tables.put(position, new Entry(inventory, incarnation));
        owners.put(inventory, position);
    }

    private PersistenceSnapshot snapshot(Coordinate position, Entry entry) {
        EnchantingInventory.Snapshot captured = entry.inventory.persistenceSnapshot();
        return new PersistenceSnapshot(position.x(), position.y(), position.z(), entry.incarnation,
                captured.revision(), List.of(captured.stacks()));
    }

    private RetirementLease requireActiveLease(RetirementLease lease) {
        if (lease == null || lease.owner != this || lease.state != RetirementLease.State.ACTIVE) {
            throw new IllegalStateException("foreign, terminal, or replayed enchanting lease");
        }
        RetirementLease registered = retirementLeases.get(lease.coordinate);
        if (registered != lease || registered.token != lease.token) {
            throw new IllegalStateException("stale enchanting retirement lease");
        }
        return lease;
    }

    private void ensureNoLease(Coordinate position) {
        if (retirementLeases.containsKey(position)) {
            throw new IllegalStateException("enchanting coordinate has an active retirement lease");
        }
    }

    private long allocateIncarnation() {
        if (nextIncarnation <= 0 || nextIncarnation == Long.MAX_VALUE) {
            throw new IllegalStateException("enchanting incarnation space exhausted");
        }
        return nextIncarnation++;
    }

    private long allocateLeaseToken() {
        if (nextLeaseToken <= 0 || nextLeaseToken == Long.MAX_VALUE) {
            throw new IllegalStateException("enchanting retirement token space exhausted");
        }
        return nextLeaseToken++;
    }
}
