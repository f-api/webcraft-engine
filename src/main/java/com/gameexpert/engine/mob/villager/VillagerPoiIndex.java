package com.gameexpert.engine.mob.villager;

import com.gameexpert.terrain.Blocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resident-only HOME/job-site POI index shared by the villager society and profession lanes.
 *
 * <p>The index never changes the established WebCraft POI choice. Every stored candidate is
 * assigned the exact zero-based ordinal it would have in
 * {@link VillagerSocietyRules#nearestMatchingCell}; the smallest eligible ordinal below the
 * existing block budget wins. A result is used only when every resident chunk conservatively
 * covering that bounded walk has the current immutable snapshot identity. Missing coverage is
 * reported explicitly so callers can retain the old block-by-block fallback.</p>
 *
 * <p>Chunk snapshots are scanned only when their immutable identity changes. Beds and all thirteen
 * job-site types (including lit furnace variants through
 * {@link VillagerJobSitePolicy#stationCode}) are retained; AIR and ordinary blocks do not allocate
 * index entries.</p>
 */
public final class VillagerPoiIndex {

    /** Resident immutable chunk source. A null snapshot means coverage is unknown. */
    public interface ChunkSource {
        Object snapshot(int chunkX, int chunkZ);

        int blockTypeAt(Object snapshot, int blockIndex);
    }

    /** Candidate vacancy predicate; HOME passes stationCode 0. */
    @FunctionalInterface
    public interface FreeCell {
        boolean isFree(int stationCode, int x, int y, int z);
    }

    /**
     * Search result. {@code covered=false} requires the caller to use the legacy resident probe;
     * {@code covered=true, position=null} is a conclusive indexed miss.
     */
    public record SearchResult(boolean covered, int[] position) {
        private static final SearchResult UNKNOWN = new SearchResult(false, null);
        private static final SearchResult NONE = new SearchResult(true, null);

        static SearchResult found(int... position) {
            return new SearchResult(true, position);
        }
    }

    private record Cell(int x, int y, int z, boolean home, int stationCode) {}

    private record ChunkEntry(Object snapshot, int minY, int maxY, List<Cell> cells) {}

    private final ChunkSource source;
    private final Map<Long, ChunkEntry> chunks = new HashMap<>();

    public VillagerPoiIndex(ChunkSource source) {
        if (source == null) throw new IllegalArgumentException("villager POI source is required");
        this.source = source;
    }

    /** Resident lifecycle invalidation. The next covered query adopts the newest snapshot. */
    public synchronized void invalidateChunk(int chunkX, int chunkZ) {
        chunks.remove(chunkKey(chunkX, chunkZ));
    }

    public synchronized SearchResult nearestVacantHome(int originX, int originY, int originZ,
            FreeCell free) {
        return nearest(originX, originY, originZ,
                VillagerSocietyRules.HOME_SEARCH_RADIUS_BLOCKS,
                VillagerSocietyRules.HOME_SEARCH_VERTICAL_REACH,
                VillagerSocietyRules.HOME_SCAN_BLOCK_BUDGET, true, free);
    }

    public synchronized SearchResult nearestFreeJobSite(int originX, int originY, int originZ,
            FreeCell free) {
        return nearest(originX, originY, originZ,
                VillagerBrainRules.POI_SEARCH_RADIUS_BLOCKS,
                VillagerJobSitePolicy.POI_SEARCH_VERTICAL_REACH,
                VillagerJobSitePolicy.POI_SCAN_BLOCK_BUDGET, false, free);
    }

    /**
     * 바닐라 {@code PoiManager.getInRange(HOME, pos, radius, ANY)} 자리: 원점 블록과의
     * {@code distSqr <= radius²} 인 HOME(침대) 칸 전부를 {@link #candidateOrdinal}(수직 반창 = radius)
     * 오름차순으로 돌려준다. 상주 범위를 모르면 {@code null} 이다. 활동 원장의 은신처 찾기
     * ({@code LocateHidingPlace})가 쓴다.
     */
    public synchronized List<int[]> homesWithin(int originX, int originY, int originZ,
            int radius) {
        if (radius < 0) return List.of();
        int minChunkX = Math.floorDiv(originX - radius, Blocks.CHUNK_X);
        int maxChunkX = Math.floorDiv(originX + radius, Blocks.CHUNK_X);
        int minChunkZ = Math.floorDiv(originZ - radius, Blocks.CHUNK_Z);
        int maxChunkZ = Math.floorDiv(originZ + radius, Blocks.CHUNK_Z);
        int minY = Math.max(Blocks.MIN_Y, originY - radius);
        int maxY = Math.min(Blocks.MAX_Y, originY + radius);
        if (minY > maxY) return List.of();
        if (!refreshCoverage(minChunkX, maxChunkX, minChunkZ, maxChunkZ, minY, maxY)) return null;
        long limit = (long) radius * radius;
        List<long[]> ranked = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkEntry entry = chunks.get(chunkKey(chunkX, chunkZ));
                if (entry == null) return null;
                for (Cell cell : entry.cells) {
                    if (!cell.home) continue;
                    long dx = cell.x - (long) originX;
                    long dy = cell.y - (long) originY;
                    long dz = cell.z - (long) originZ;
                    if (dx * dx + dy * dy + dz * dz > limit) continue;
                    int ordinal = candidateOrdinal(originX, originY, originZ,
                            cell.x, cell.y, cell.z, radius, radius);
                    if (ordinal < 0) continue;
                    ranked.add(new long[] { ordinal, cell.x, cell.y, cell.z });
                }
            }
        }
        ranked.sort((left, right) -> Long.compare(left[0], right[0]));
        List<int[]> homes = new ArrayList<>(ranked.size());
        for (long[] row : ranked) homes.add(new int[] { (int) row[1], (int) row[2], (int) row[3] });
        return homes;
    }

    private SearchResult nearest(int originX, int originY, int originZ,
            int radius, int verticalReach, int blockBudget, boolean home, FreeCell free) {
        int maxRing = maxVisitedRing(originY, radius, verticalReach, blockBudget);
        if (maxRing < 0) return SearchResult.NONE;
        int minChunkX = Math.floorDiv(originX - maxRing, Blocks.CHUNK_X);
        int maxChunkX = Math.floorDiv(originX + maxRing, Blocks.CHUNK_X);
        int minChunkZ = Math.floorDiv(originZ - maxRing, Blocks.CHUNK_Z);
        int maxChunkZ = Math.floorDiv(originZ + maxRing, Blocks.CHUNK_Z);
        int minY = Math.max(Blocks.MIN_Y, originY - verticalReach);
        int maxY = Math.min(Blocks.MAX_Y, originY + verticalReach);
        if (!refreshCoverage(minChunkX, maxChunkX, minChunkZ, maxChunkZ, minY, maxY)) {
            return SearchResult.UNKNOWN;
        }

        Cell best = null;
        int bestOrdinal = Integer.MAX_VALUE;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkEntry entry = chunks.get(chunkKey(chunkX, chunkZ));
                if (entry == null) return SearchResult.UNKNOWN;
                for (Cell cell : entry.cells) {
                    int stationCode = cell.stationCode;
                    if (home ? !cell.home : stationCode == 0) continue;
                    int ordinal = candidateOrdinal(originX, originY, originZ,
                            cell.x, cell.y, cell.z, radius, verticalReach);
                    if (ordinal < 0 || ordinal >= blockBudget || ordinal >= bestOrdinal) continue;
                    if (!free.isFree(home ? 0 : stationCode, cell.x, cell.y, cell.z)) continue;
                    best = cell;
                    bestOrdinal = ordinal;
                }
            }
        }
        if (best == null) return SearchResult.NONE;
        return home
                ? SearchResult.found(best.x, best.y, best.z)
                : SearchResult.found(best.stationCode, best.x, best.y, best.z);
    }

    private boolean refreshCoverage(int minChunkX, int maxChunkX,
            int minChunkZ, int maxChunkZ, int minY, int maxY) {
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                long key = chunkKey(chunkX, chunkZ);
                Object snapshot = source.snapshot(chunkX, chunkZ);
                if (snapshot == null) {
                    chunks.remove(key);
                    return false;
                }
                ChunkEntry current = chunks.get(key);
                if (current != null && current.snapshot == snapshot
                        && current.minY <= minY && current.maxY >= maxY) continue;
                int scanMinY = current != null && current.snapshot == snapshot
                        ? Math.min(current.minY, minY) : minY;
                int scanMaxY = current != null && current.snapshot == snapshot
                        ? Math.max(current.maxY, maxY) : maxY;
                chunks.put(key, new ChunkEntry(snapshot, scanMinY, scanMaxY,
                        scanChunk(chunkX, chunkZ, snapshot, scanMinY, scanMaxY)));
            }
        }
        return true;
    }

    private List<Cell> scanChunk(int chunkX, int chunkZ, Object snapshot,
            int minY, int maxY) {
        List<Cell> found = null;
        int baseX = chunkX * Blocks.CHUNK_X;
        int baseZ = chunkZ * Blocks.CHUNK_Z;
        int layerSize = Blocks.CHUNK_X * Blocks.CHUNK_Z;
        int fromIndex = (minY - Blocks.MIN_Y) * layerSize;
        int toIndex = (maxY - Blocks.MIN_Y + 1) * layerSize;
        for (int index = fromIndex; index < toIndex; index++) {
            int blockId = source.blockTypeAt(snapshot, index);
            boolean home = Blocks.isBed(blockId);
            int stationCode = VillagerJobSitePolicy.stationCode(blockId);
            if (!home && stationCode == 0) continue;
            if (found == null) found = new ArrayList<>();
            int horizontal = index & 0xff;
            found.add(new Cell(baseX + (horizontal & 15),
                    Blocks.MIN_Y + (index >>> 8), baseZ + (horizontal >>> 4),
                    home, stationCode));
        }
        return found == null ? List.of() : found;
    }

    /**
     * Exact ordinal of a coordinate in the established Chebyshev/dy/dx/dz perimeter walk.
     * Invalid-Y candidates are absent rather than consuming budget, matching the legacy loop.
     */
    public static int candidateOrdinal(int originX, int originY, int originZ,
            int candidateX, int candidateY, int candidateZ,
            int radius, int verticalReach) {
        long dxLong = (long) candidateX - originX;
        long dzLong = (long) candidateZ - originZ;
        long ringLong = Math.max(Math.abs(dxLong), Math.abs(dzLong));
        if (ringLong > radius || ringLong > Integer.MAX_VALUE
                || candidateY < Blocks.MIN_Y || candidateY > Blocks.MAX_Y) return -1;
        int ring = (int) ringLong;
        int dx = (int) dxLong;
        int dz = (int) dzLong;
        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) return -1;

        int minY = Math.max(Blocks.MIN_Y, originY - verticalReach);
        int maxY = Math.min(Blocks.MAX_Y, originY + verticalReach);
        if (candidateY < minY || candidateY > maxY) return -1;
        int validYCount = maxY - minY + 1;
        long precedingRings = ring == 0 ? 0L : (long) (2 * ring - 1) * (2 * ring - 1);
        long ordinal = precedingRings * validYCount
                + (long) (candidateY - minY) * perimeterSize(ring)
                + perimeterOrdinal(ring, dx, dz);
        return ordinal > Integer.MAX_VALUE ? -1 : (int) ordinal;
    }

    /** Largest horizontal ring that can consume at least one cell before the fixed budget ends. */
    public static int maxVisitedRing(int originY, int radius,
            int verticalReach, int blockBudget) {
        if (radius < 0 || verticalReach < 0 || blockBudget <= 0) return -1;
        int minY = Math.max(Blocks.MIN_Y, originY - verticalReach);
        int maxY = Math.min(Blocks.MAX_Y, originY + verticalReach);
        if (minY > maxY) return -1;
        int validYCount = maxY - minY + 1;
        long consumed = 0;
        for (int ring = 0; ring <= radius; ring++) {
            long next = consumed + (long) validYCount * perimeterSize(ring);
            if (consumed < blockBudget && next >= blockBudget) return ring;
            consumed = next;
        }
        return radius;
    }

    private static int perimeterSize(int ring) {
        return ring == 0 ? 1 : ring * 8;
    }

    private static int perimeterOrdinal(int ring, int dx, int dz) {
        if (ring == 0) return dx == 0 && dz == 0 ? 0 : -1;
        if (dx == -ring) return dz + ring;
        int afterNegativeXEdge = 2 * ring + 1;
        if (dx < ring) {
            int beforeColumn = afterNegativeXEdge + (dx + ring - 1) * 2;
            if (dz == -ring) return beforeColumn;
            return dz == ring ? beforeColumn + 1 : -1;
        }
        int beforePositiveXEdge = afterNegativeXEdge + (2 * ring - 1) * 2;
        return beforePositiveXEdge + dz + ring;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffff_ffffL);
    }
}
