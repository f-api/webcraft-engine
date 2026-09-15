package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.gameexpert.engine.inventory.PlayerInventory;

import com.gameexpert.terrain.Blocks;

/**
 * 월드 틱 스레드가 소유하는 음식이 올라간 모닥불 상태입니다.
 *
 * <p>빈 자연/구조물 모닥불은 맵에 넣지 않아 런타임 객체와 영속 행을 만들지 않습니다.
 */
public final class CampfireStorage {

    public static final long PROGRESS_UPDATE_INTERVAL_TICKS = 10L;

    private static final AtomicLong NEXT_INCARNATION = new AtomicLong(1L);

    private static final class Entry {
        private final CampfireInventory inventory;
        private final long incarnation;

        private Entry(CampfireInventory inventory, long incarnation) {
            this.inventory = inventory;
            this.incarnation = incarnation;
        }
    }

    /** 좌표와 resident incarnation에 묶인 불변 영속 스냅샷입니다. */
    public record PersistenceSnapshot(int x, int y, int z, long incarnation, long revision,
            short[] itemTypes, int[] cookTicks) {
        public PersistenceSnapshot {
            if (incarnation <= 0L || revision < 0L || revision == Long.MAX_VALUE) {
                throw new IllegalArgumentException("invalid campfire persistence identity");
            }
            CampfireStorage.validateSnapshot(itemTypes, cookTicks);
            itemTypes = itemTypes.clone();
            cookTicks = cookTicks.clone();
        }

        @Override public short[] itemTypes() { return itemTypes.clone(); }
        @Override public int[] cookTicks() { return cookTicks.clone(); }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PersistenceSnapshot snapshot)) return false;
            return x == snapshot.x && y == snapshot.y && z == snapshot.z
                    && incarnation == snapshot.incarnation && revision == snapshot.revision
                    && java.util.Arrays.equals(itemTypes, snapshot.itemTypes)
                    && java.util.Arrays.equals(cookTicks, snapshot.cookTicks);
        }

        @Override
        public int hashCode() {
            int result = java.util.Objects.hash(x, y, z, incarnation, revision);
            result = 31 * result + java.util.Arrays.hashCode(itemTypes);
            return 31 * result + java.util.Arrays.hashCode(cookTicks);
        }
    }

    private final Map<Long, Entry> campfires = new HashMap<>();
    private final Map<CampfireInventory, Long> owners = new IdentityHashMap<>();
    private final Map<Long, Long> lastRemovedIncarnations = new HashMap<>();
    private final Map<Long, Set<Long>> campfiresByChunk = new HashMap<>();
    private final Set<Long> dirty = new HashSet<>();
    private final Set<Long> ticking = new HashSet<>();
    private final Map<Long, Long> scheduledUpdateTicks = new HashMap<>();
    private final PriorityQueue<ScheduledUpdate> scheduledUpdates =
            new PriorityQueue<>((left, right) -> Long.compare(left.tick, right.tick));

    private static final class ScheduledUpdate {
        private final long key;
        private final long tick;

        private ScheduledUpdate(long key, long tick) {
            this.key = key;
            this.tick = tick;
        }
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 30)
                | ((long) (z & 0x1FFFFF) << 9)
                | ((y - Blocks.MIN_Y) & 0x1FFL);
    }

    static int[] unkey(long key) {
        int x = (int) (key >> 30) & 0x1FFFFF;
        int z = (int) (key >> 9) & 0x1FFFFF;
        int y = (int) (key & 0x1FF) + Blocks.MIN_Y;
        if (x >= 0x100000) x -= 0x200000;
        if (z >= 0x100000) z -= 0x200000;
        return new int[] { x, y, z };
    }

    /** 실제 유효 음식 삽입에서만 좌표 상태를 만들고 첫 빈 슬롯을 반환합니다. */
    public synchronized int insertAt(int x, int y, int z, short itemType) {
        if (!CampfireRules.isCookable(itemType)) return -1;
        long key = key(x, y, z);
        Entry existingEntry = campfires.get(key);
        CampfireInventory existing = existingEntry == null ? null : existingEntry.inventory;
        if (existing != null && existing.firstFreeSlot() < 0) return -1;
        CampfireInventory campfire = existing;
        if (campfire == null) {
            campfire = new CampfireInventory();
            bind(key, campfire, allocateIncarnation());
            index(key, x, z);
        }
        int slot = campfire.addFirst(itemType);
        if (slot >= 0) ticking.add(key);
        return slot;
    }

    public synchronized CampfireInventory peekAt(int x, int y, int z) {
        Entry entry = campfires.get(key(x, y, z));
        return entry == null ? null : entry.inventory;
    }

    public synchronized void load(int x, int y, int z, CampfireInventory campfire) {
        if (campfire == null || !campfire.occupied()) {
            throw new IllegalArgumentException("빈 모닥불 상태는 로드할 수 없습니다.");
        }
        validateSnapshot(itemTypes(campfire), cookTicks(campfire));
        long key = key(x, y, z);
        Long owner = owners.get(campfire);
        if (owner != null && owner.longValue() != key) {
            throw new IllegalArgumentException("one campfire inventory cannot alias coordinates");
        }
        Entry existing = campfires.get(key);
        if (existing != null && existing.inventory != campfire) {
            owners.remove(existing.inventory);
        }
        long incarnation = existing == null ? allocateIncarnation() : existing.incarnation;
        campfires.put(key, new Entry(campfire, incarnation));
        owners.put(campfire, key);
        lastRemovedIncarnations.remove(key);
        index(key, x, z);
    }

    public synchronized void activate(int x, int y, int z) {
        long key = key(x, y, z);
        if (campfires.containsKey(key)) ticking.add(key);
    }

    public synchronized void deactivate(long key) {
        ticking.remove(key);
    }

    /** 상주하게 된 청크의 점유 모닥불만 진행·복구 대상으로 되돌립니다. */
    public synchronized void activateChunk(int chunkX, int chunkZ) {
        Set<Long> indexed = campfiresByChunk.get(chunkKey(chunkX, chunkZ));
        if (indexed == null) return;
        for (long key : indexed) {
            scheduledUpdateTicks.remove(key);
            ticking.add(key);
        }
    }

    /** 비상주 청크 상태는 보존하되 틱과 1Hz 복구 예약에서 제외합니다. */
    public synchronized void deactivateChunk(int chunkX, int chunkZ) {
        Set<Long> indexed = campfiresByChunk.get(chunkKey(chunkX, chunkZ));
        if (indexed == null) return;
        ticking.removeAll(indexed);
        for (long key : indexed) scheduledUpdateTicks.remove(key);
    }

    /** 비상주 판정이 틱과 엇갈린 단일 좌표를 다음 청크 활성화까지 내립니다. */
    public synchronized void park(long key) {
        ticking.remove(key);
        scheduledUpdateTicks.remove(key);
    }

    public synchronized List<Long> tickingKeys() {
        return new ArrayList<>(ticking);
    }

    /**
     * 파괴/교체된 좌표의 원재료를 제거합니다. 상태가 없으면 dirty도 만들지 않아 빈 구조물 비용을 피합니다.
     */
    public synchronized List<CampfireInventory.StoredStack> removeAt(int x, int y, int z) {
        long key = key(x, y, z);
        Entry removedEntry = campfires.remove(key);
        if (removedEntry == null) return List.of();
        CampfireInventory removed = removedEntry.inventory;
        owners.remove(removed);
        lastRemovedIncarnations.put(key, removedEntry.incarnation);
        ticking.remove(key);
        scheduledUpdateTicks.remove(key);
        unindex(key, x, z);
        markDirty(x, y, z);
        return removed.drainAll();
    }

    /** 마지막 음식이 완성된 좌표를 영속 삭제 대상으로 전환합니다. */
    public synchronized boolean removeIfEmpty(int x, int y, int z) {
        long key = key(x, y, z);
        Entry entry = campfires.get(key);
        CampfireInventory campfire = entry == null ? null : entry.inventory;
        if (campfire == null || campfire.occupied()) return false;
        campfires.remove(key);
        owners.remove(campfire);
        lastRemovedIncarnations.put(key, entry.incarnation);
        ticking.remove(key);
        scheduledUpdateTicks.remove(key);
        unindex(key, x, z);
        markDirty(x, y, z);
        return true;
    }

    public synchronized int size() {
        return campfires.size();
    }

    public synchronized void recordUpdate(int x, int y, int z, long tickNo) {
        long key = key(x, y, z);
        if (!campfires.containsKey(key)) return;
        long due = tickNo + PROGRESS_UPDATE_INTERVAL_TICKS;
        scheduledUpdateTicks.put(key, due);
        scheduledUpdates.add(new ScheduledUpdate(key, due));
    }

    /** 로드된 좌표가 아직 한 번도 현재 런타임에서 전송되지 않았는지 확인합니다. */
    public synchronized boolean needsInitialUpdate(long key) {
        return campfires.containsKey(key) && !scheduledUpdateTicks.containsKey(key);
    }

    /** 1초 주기가 도래한 점유 좌표만 꺼냅니다. 오래된 재예약 노드는 값 일치로 무시합니다. */
    public synchronized List<Long> drainDueUpdates(long tickNo) {
        if (scheduledUpdates.isEmpty() || scheduledUpdates.peek().tick > tickNo) return List.of();
        List<Long> due = new ArrayList<>();
        while (!scheduledUpdates.isEmpty() && scheduledUpdates.peek().tick <= tickNo) {
            ScheduledUpdate update = scheduledUpdates.remove();
            Long current = scheduledUpdateTicks.get(update.key);
            if (current == null || current != update.tick || !campfires.containsKey(update.key)) continue;
            scheduledUpdateTicks.remove(update.key);
            due.add(update.key);
        }
        return due;
    }

    public synchronized void markDirty(int x, int y, int z) {
        dirty.add(key(x, y, z));
    }

    public synchronized List<int[]> drainDirty() {
        if (dirty.isEmpty()) return List.of();
        List<int[]> out = new ArrayList<>(dirty.size());
        for (long key : dirty) out.add(unkey(key));
        dirty.clear();
        return out;
    }

    /** 정산할 좌표만 꺼내 다른 모닥불의 진행 상태와 저장 성공 여부를 묶지 않습니다. */
    public synchronized List<int[]> drainDirtyAt(int x, int y, int z) {
        return dirty.remove(key(x, y, z)) ? List.of(new int[] {x, y, z}) : List.of();
    }

    public synchronized void restoreDirty(List<int[]> positions) {
        for (int[] pos : positions) dirty.add(key(pos[0], pos[1], pos[2]));
    }

    public synchronized boolean hasDirty() {
        return !dirty.isEmpty();
    }

    /** 현재 resident의 incarnation과 전체 상태를 한 번에 복사합니다. */
    public synchronized PersistenceSnapshot persistenceSnapshotAt(int x, int y, int z) {
        long key = key(x, y, z);
        Entry entry = campfires.get(key);
        return entry == null ? null : snapshot(x, y, z, entry);
    }

    /** 제거된 직후의 dirty 삭제 스냅샷이 사용할 마지막 resident incarnation입니다. */
    public synchronized long removedIncarnationAt(int x, int y, int z) {
        return lastRemovedIncarnations.getOrDefault(key(x, y, z), 0L);
    }

    private void index(long campfireKey, int x, int z) {
        campfiresByChunk.computeIfAbsent(
                chunkKey(Math.floorDiv(x, Blocks.CHUNK_X), Math.floorDiv(z, Blocks.CHUNK_Z)),
                unused -> new HashSet<>()).add(campfireKey);
    }

    private void unindex(long campfireKey, int x, int z) {
        long chunkKey = chunkKey(Math.floorDiv(x, Blocks.CHUNK_X), Math.floorDiv(z, Blocks.CHUNK_Z));
        Set<Long> indexed = campfiresByChunk.get(chunkKey);
        if (indexed == null) return;
        indexed.remove(campfireKey);
        if (indexed.isEmpty()) campfiresByChunk.remove(chunkKey);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | Integer.toUnsignedLong(chunkZ);
    }

    private void bind(long campfireKey, CampfireInventory campfire, long incarnation) {
        Long owner = owners.get(campfire);
        if (owner != null && owner.longValue() != campfireKey) {
            throw new IllegalArgumentException("one campfire inventory cannot alias coordinates");
        }
        campfires.put(campfireKey, new Entry(campfire, incarnation));
        owners.put(campfire, campfireKey);
        lastRemovedIncarnations.remove(campfireKey);
    }

    private static PersistenceSnapshot snapshot(int x, int y, int z, Entry entry) {
        return new PersistenceSnapshot(x, y, z, entry.incarnation,
                entry.inventory.persistenceRevision(), itemTypes(entry.inventory),
                cookTicks(entry.inventory));
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

    private static void validateSnapshot(short[] itemTypes, int[] cookTicks) {
        if (itemTypes == null || cookTicks == null
                || itemTypes.length != CampfireInventory.SLOTS
                || cookTicks.length != CampfireInventory.SLOTS) {
            throw new IllegalArgumentException("campfire snapshot must contain four slots");
        }
        boolean occupied = false;
        for (int slot = 0; slot < CampfireInventory.SLOTS; slot++) {
            short type = itemTypes[slot];
            int progress = cookTicks[slot];
            if (type == PlayerInventory.EMPTY) {
                if (progress != 0) {
                    throw new IllegalArgumentException(
                            "empty campfire slot must have zero cook ticks: " + slot);
                }
                continue;
            }
            if (!CampfireRules.isCookable(type)
                    || progress < 0 || progress >= CampfireInventory.COOK_TOTAL_TICKS) {
                throw new IllegalArgumentException("invalid canonical campfire slot: " + slot);
            }
            occupied = true;
        }
        if (!occupied) throw new IllegalArgumentException("empty campfire cannot be persisted");
    }

    private static long allocateIncarnation() {
        long incarnation = NEXT_INCARNATION.getAndIncrement();
        if (incarnation <= 0L || incarnation == Long.MAX_VALUE) {
            throw new IllegalStateException("campfire incarnation space exhausted");
        }
        return incarnation;
    }
}
