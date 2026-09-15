package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gameexpert.terrain.Blocks;

/** 월드 틱 스레드가 소유하는 좌표별 화로 상태와 dirty/ticking 인덱스입니다. */
public final class FurnaceStorage {

    private final Map<Long, FurnaceInventory> furnaces = new HashMap<>();
    private final Map<Long, Set<Long>> furnacesByChunk = new HashMap<>();
    private final Set<Long> dirty = new HashSet<>();
    private final Set<Long> ticking = new HashSet<>();

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

    /**
     * 좌표의 화로 상태. 없으면 그 변형의 빈 화로를 만듭니다.
     *
     * <p>변형은 좌표에 실제로 놓인 블록 ID 에서 옵니다. 이미 다른 변형으로 저장돼 있다면
     * (블록이 바뀌었는데 블록 엔티티가 남은 경우) 옛 상태를 재사용하지 않고 새 변형의 빈
     * 화로로 갈아 끼웁니다 — 용광로 자리에 음식이 든 훈연기 상태가 살아남으면
     * {@link FurnaceInventory#restore} 가 다음 로드에서 월드를 통째로 거부하기 때문입니다.
     */
    public FurnaceInventory openAt(int x, int y, int z, FurnaceVariant variant) {
        long key = key(x, y, z);
        FurnaceInventory furnace = furnaces.get(key);
        if (furnace != null && furnace.variant() == variant) return furnace;
        furnace = new FurnaceInventory(variant);
        furnaces.put(key, furnace);
        index(key, x, z);
        return furnace;
    }

    /** 변형을 모르는 호출부용 화로 기본값. */
    public FurnaceInventory openAt(int x, int y, int z) {
        return openAt(x, y, z, FurnaceVariant.FURNACE);
    }

    public FurnaceInventory peekAt(int x, int y, int z) {
        return furnaces.get(key(x, y, z));
    }

    public void load(int x, int y, int z, FurnaceInventory furnace) {
        long key = key(x, y, z);
        furnaces.put(key, furnace);
        index(key, x, z);
    }

    public List<FurnaceInventory.StoredStack> removeAt(int x, int y, int z) {
        long key = key(x, y, z);
        FurnaceInventory removed = furnaces.remove(key);
        ticking.remove(key);
        unindex(key, x, z);
        markDirty(x, y, z);
        return removed == null ? List.of() : removed.drainAll();
    }

    public void activate(int x, int y, int z) {
        ticking.add(key(x, y, z));
    }

    public void deactivate(long key) {
        ticking.remove(key);
    }

    /** 상주하게 된 청크의 저장 화로만 10Hz 진행 집합에 되돌립니다. */
    public void activateChunk(int chunkX, int chunkZ) {
        Set<Long> indexed = furnacesByChunk.get(chunkKey(chunkX, chunkZ));
        if (indexed != null) ticking.addAll(indexed);
    }

    /** 비상주 청크의 화로는 상태를 보존한 채 매 틱 진행 집합에서만 내립니다. */
    public void deactivateChunk(int chunkX, int chunkZ) {
        Set<Long> indexed = furnacesByChunk.get(chunkKey(chunkX, chunkZ));
        if (indexed != null) ticking.removeAll(indexed);
    }

    /** 열어 보기만 한 미변경 화로만 제거하며, 저장된 빈 화로의 CAS 기준선은 보존합니다. */
    public void discardIfEmpty(long key) {
        FurnaceInventory furnace = furnaces.get(key);
        if (furnace == null || !furnace.isEmpty() || furnace.persistenceRevision() != 0L) return;
        furnaces.remove(key);
        int[] pos = unkey(key);
        unindex(key, pos[0], pos[2]);
    }

    public List<Long> tickingKeys() {
        return new ArrayList<>(ticking);
    }

    public int size() {
        return furnaces.size();
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

    public synchronized void restoreDirty(List<int[]> positions) {
        for (int[] pos : positions) dirty.add(key(pos[0], pos[1], pos[2]));
    }

    public synchronized boolean hasDirty() {
        return !dirty.isEmpty();
    }

    private void index(long furnaceKey, int x, int z) {
        furnacesByChunk.computeIfAbsent(
                chunkKey(Math.floorDiv(x, Blocks.CHUNK_X), Math.floorDiv(z, Blocks.CHUNK_Z)),
                unused -> new HashSet<>()).add(furnaceKey);
    }

    private void unindex(long furnaceKey, int x, int z) {
        long chunkKey = chunkKey(Math.floorDiv(x, Blocks.CHUNK_X), Math.floorDiv(z, Blocks.CHUNK_Z));
        Set<Long> indexed = furnacesByChunk.get(chunkKey);
        if (indexed == null) return;
        indexed.remove(furnaceKey);
        if (indexed.isEmpty()) furnacesByChunk.remove(chunkKey);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | Integer.toUnsignedLong(chunkZ);
    }
}
