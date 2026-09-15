package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gameexpert.terrain.Blocks;

/** World-tick-owned coordinate index for durable brewing stands and exclusive open leases. */
public final class BrewingStorage {

    private final Map<Long, BrewingInventory> stands = new HashMap<>();
    private final Map<Long, Set<Long>> standsByChunk = new HashMap<>();
    private final Map<Long, String> leases = new HashMap<>();
    private final Set<Long> dirty = new HashSet<>();
    private final Set<Long> ticking = new HashSet<>();

    public static long key(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 30)
                | ((long) (z & 0x1FFFFF) << 9)
                | ((y - Blocks.MIN_Y) & 0x1FFL);
    }

    public static int[] unkey(long key) {
        int x = (int) (key >> 30) & 0x1FFFFF;
        int z = (int) (key >> 9) & 0x1FFFFF;
        int y = (int) (key & 0x1FF) + Blocks.MIN_Y;
        if (x >= 0x100000) x -= 0x200000;
        if (z >= 0x100000) z -= 0x200000;
        return new int[] { x, y, z };
    }

    public BrewingInventory openAt(int x, int y, int z) {
        long key = key(x, y, z);
        BrewingInventory stand = stands.get(key);
        if (stand != null) return stand;
        stand = new BrewingInventory();
        stands.put(key, stand);
        index(key, x, z);
        return stand;
    }

    public BrewingInventory peekAt(int x, int y, int z) {
        return stands.get(key(x, y, z));
    }

    public void load(int x, int y, int z, BrewingInventory stand) {
        if (stand == null) throw new IllegalArgumentException("brewing stand is required");
        long key = key(x, y, z);
        stands.put(key, stand);
        index(key, x, z);
    }

    public List<BrewingInventory.StoredStack> removeAt(int x, int y, int z) {
        long key = key(x, y, z);
        BrewingInventory removed = stands.remove(key);
        ticking.remove(key);
        leases.remove(key);
        unindex(key, x, z);
        markDirty(x, y, z);
        return removed == null ? List.of() : removed.drainAll();
    }

    public void activate(int x, int y, int z) { ticking.add(key(x, y, z)); }
    public void deactivate(long key) { ticking.remove(key); }

    public void activateChunk(int chunkX, int chunkZ) {
        Set<Long> indexed = standsByChunk.get(chunkKey(chunkX, chunkZ));
        if (indexed != null) ticking.addAll(indexed);
    }

    /** [BLOCK-SHAPES] 이 청크에 저장된 양조대 좌표 키(없으면 빈 목록). */
    public List<Long> keysInChunk(int chunkX, int chunkZ) {
        Set<Long> indexed = standsByChunk.get(chunkKey(chunkX, chunkZ));
        return indexed == null ? List.of() : new ArrayList<>(indexed);
    }

    /** [BLOCK-SHAPES] 저장된 모든 양조대 좌표 키. */
    public List<Long> keys() {
        return new ArrayList<>(stands.keySet());
    }

    public void deactivateChunk(int chunkX, int chunkZ) {
        Set<Long> indexed = standsByChunk.get(chunkKey(chunkX, chunkZ));
        if (indexed != null) ticking.removeAll(indexed);
    }

    public boolean acquireLease(int x, int y, int z, String owner) {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("lease owner required");
        long key = key(x, y, z);
        String current = leases.get(key);
        if (current != null && !current.equals(owner)) return false;
        leases.put(key, owner);
        return true;
    }

    public boolean ownsLease(int x, int y, int z, String owner) {
        return owner != null && owner.equals(leases.get(key(x, y, z)));
    }

    public String leaseOwner(int x, int y, int z) {
        return leases.get(key(x, y, z));
    }

    public boolean releaseLease(int x, int y, int z, String owner) {
        return owner != null && leases.remove(key(x, y, z), owner);
    }

    public void discardIfEmpty(long key) {
        BrewingInventory stand = stands.get(key);
        if (stand == null || !stand.isEmpty() || leases.containsKey(key)) return;
        stands.remove(key);
        int[] pos = unkey(key);
        unindex(key, pos[0], pos[2]);
    }

    public List<Long> tickingKeys() { return new ArrayList<>(ticking); }
    public int size() { return stands.size(); }

    public synchronized void markDirty(int x, int y, int z) { dirty.add(key(x, y, z)); }

    public synchronized List<int[]> drainDirty() {
        if (dirty.isEmpty()) return List.of();
        List<int[]> positions = new ArrayList<>(dirty.size());
        for (long key : dirty) positions.add(unkey(key));
        dirty.clear();
        return positions;
    }

    public synchronized void restoreDirty(List<int[]> positions) {
        for (int[] pos : positions) dirty.add(key(pos[0], pos[1], pos[2]));
    }

    public synchronized boolean hasDirty() { return !dirty.isEmpty(); }

    private void index(long standKey, int x, int z) {
        standsByChunk.computeIfAbsent(
                chunkKey(Math.floorDiv(x, Blocks.CHUNK_X), Math.floorDiv(z, Blocks.CHUNK_Z)),
                unused -> new HashSet<>()).add(standKey);
    }

    private void unindex(long standKey, int x, int z) {
        long chunkKey = chunkKey(Math.floorDiv(x, Blocks.CHUNK_X), Math.floorDiv(z, Blocks.CHUNK_Z));
        Set<Long> indexed = standsByChunk.get(chunkKey);
        if (indexed == null) return;
        indexed.remove(standKey);
        if (indexed.isEmpty()) standsByChunk.remove(chunkKey);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | Integer.toUnsignedLong(chunkZ);
    }
}
