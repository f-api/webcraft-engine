package com.gameexpert.engine;

import java.util.Arrays;
import com.gameexpert.common.LongOpenHashMap;
import com.gameexpert.common.LongObjectOpenHashMap;
import com.gameexpert.engine.blocks.P26Rules;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.TerrainAccessor;

/**
 * 몹 스폰과 햇빛 연소가 같이 쓰는 실제 주변 광도 조회. 틱 스레드 전용이다.
 *
 * <p>주변 15블록만 역탐색하고 큐와 방문 버퍼를 재사용한다. 청크 스냅샷의
 * 객체 정체성을 버전으로 쓰므로 블록 변경 뒤에는 지표 가림 캐시도 자동으로 교체된다.</p>
 */
final class MobLightEngine {
    private static final int RADIUS = 15;
    private static final int SIDE = RADIUS * 2 + 1;
    private static final int VISITED_SIZE = SIDE * SIDE * SIDE;
    private static final int CACHE_CAP = 1_024;
    /** 랜덤틱 최대 표본 4,608개를 한 틱의 결과 메모가 수용하도록 여유를 둡니다. */
    private static final int LIGHT_MEMO_CAP = 8_192;
    private static final byte EMISSION_UNKNOWN = -1;

    private final TerrainAccessor accessor;
    private final LongObjectOpenHashMap<ColumnLightCache> columns =
            new LongObjectOpenHashMap<>(CACHE_CAP);
    /**
     * Revision of memo origins whose radius-15 search can observe a changed chunk. Keys are exact
     * signed chunk coordinates, so invalidation does not rely on a lossy packed-coordinate hash.
     */
    private final LongOpenHashMap originDependencyRevisions = new LongOpenHashMap(CACHE_CAP * 4);
    private final TerrainAccessor.SnapshotSource[] querySources = new TerrainAccessor.SnapshotSource[9];
    private final ColumnLightCache[] queryColumns = new ColumnLightCache[9];
    private final LightMemo lightMemo = new LightMemo(LIGHT_MEMO_CAP);
    private final int[] queue = new int[VISITED_SIZE];
    private final byte[] queueDistance = new byte[VISITED_SIZE];
    private final int[] visited = new int[VISITED_SIZE];
    private int visitGeneration = 1;
    private int minChunkX;
    private int minChunkZ;
    private int chunkWidth;
    private int chunkDepth;
    private long candidateLightQueries;
    private long memoHits;
    private long memoMisses;
    private long bfsVisitedCells;
    private int maxBfsVisitCount;
    private long nextDependencyRevision;

    MobLightEngine(TerrainAccessor accessor) {
        this.accessor = accessor;
    }

    /**
     * 광도는 immutable snapshot identity로 검증되고 모든 블록 변경이 memo를 무효화합니다. 따라서 틱
     * 경계에서 동일한 몹/블록 좌표 결과를 버리지 않습니다.
     */
    void beginTick() {
        // Deliberately retained across ticks; invalidateColumn/invalidateChunk clear it on every source change.
        resetTickCost();
    }

    /**
     * Returns the light-search work observed since the last tick boundary or drain, then resets the counters.
     * The hot query/BFS paths update primitive fields only; the immutable snapshot is allocated only when a
     * diagnostics consumer explicitly drains it.
     */
    TickCost drainTickCost() {
        TickCost result = new TickCost(candidateLightQueries, memoHits, memoMisses,
                bfsVisitedCells, maxBfsVisitCount);
        resetTickCost();
        return result;
    }

    private void resetTickCost() {
        candidateLightQueries = 0;
        memoHits = 0;
        memoMisses = 0;
        bfsVisitedCells = 0;
        maxBfsVisitCount = 0;
    }

    record TickCost(long candidateLightQueries, long memoHits, long memoMisses,
                    long bfsVisitedCells, int maxBfsVisitCount) {}

    long candidateLightQueries() { return candidateLightQueries; }

    long memoHits() { return memoHits; }

    long memoMisses() { return memoMisses; }

    long bfsVisitedCells() { return bfsVisitedCells; }

    int maxBfsVisitCount() { return maxBfsVisitCount; }

    /** 누적 실제 memo 슬롯 해제 수. 테스트·틱 진단은 8,192-cap 전체 clear 회귀를 검출한다. */
    long memoClearedSlots() {
        return lightMemo.clearedSlots;
    }

    /** 한 블록 변경은 해당 컬럼만 다시 계산하고, 전역 광도 결과만 버립니다. */
    void invalidateColumn(int x, int z) {
        int chunkX = Math.floorDiv(x, Blocks.CHUNK_X);
        int chunkZ = Math.floorDiv(z, Blocks.CHUNK_Z);
        ColumnLightCache cached = columns.get(chunkKey(chunkX, chunkZ));
        if (cached != null) {
            cached.invalidateColumn(Math.floorMod(x, Blocks.CHUNK_X),
                    Math.floorMod(z, Blocks.CHUNK_Z));
        }
        invalidateDependentOrigins(chunkX, chunkZ);
    }

    /** 청크 축출/자연 패치 교체 시 snapshot 강참조와 관련 컬럼을 함께 제거합니다. */
    void invalidateChunk(int chunkX, int chunkZ) {
        columns.remove(chunkKey(chunkX, chunkZ));
        invalidateDependentOrigins(chunkX, chunkZ);
        Arrays.fill(querySources, null);
        Arrays.fill(queryColumns, null);
        chunkWidth = 0;
        chunkDepth = 0;
    }

    int lightLevel(int x, int y, int z, long worldTime) {
        return propagatedLevel(x, y, z, true, true, skyDarken(worldTime));
    }

    int rawSkyLight(int x, int y, int z) {
        return propagatedLevel(x, y, z, true, false, 0);
    }

    int blockLight(int x, int y, int z) {
        return propagatedLevel(x, y, z, false, true, 0);
    }

    /** 식물 랜덤틱용 raw brightness. 하늘광과 블록광을 한 번의 BFS에서 합칩니다. */
    int rawLightLevel(int x, int y, int z) {
        return propagatedLevel(x, y, z, true, true, 0);
    }

    int worldSurfaceHeight(int x, int z) {
        int chunkX = Math.floorDiv(x, Blocks.CHUNK_X);
        int chunkZ = Math.floorDiv(z, Blocks.CHUNK_Z);
        TerrainAccessor.SnapshotSource source = accessor.snapshotSource(chunkX, chunkZ);
        if (source == null) return Blocks.MAX_Y;
        ColumnLightCache cached = columnCache(chunkX, chunkZ, source);
        int localX = Math.floorMod(x, Blocks.CHUNK_X);
        int localZ = Math.floorMod(z, Blocks.CHUNK_Z);
        int column = localX + localZ * Blocks.CHUNK_X;
        int top = cached.occupiedTop[column];
        if (top == Short.MIN_VALUE) {
            top = Blocks.MIN_Y - 1;
            for (int y = Blocks.MAX_Y; y >= Blocks.MIN_Y; y--) {
                if (cached.blockTypeAt(Blocks.blockIndex(localX, y, localZ)) != 0) {
                    top = y;
                    break;
                }
            }
            cached.occupiedTop[column] = (short) top;
        }
        return top;
    }

    /** 바닐라 MOTION_BLOCKING_NO_LEAVES 조건을 만족하는 가장 높은 셀. */
    int motionBlockingNoLeavesHeight(int x, int z) {
        int chunkX = Math.floorDiv(x, Blocks.CHUNK_X);
        int chunkZ = Math.floorDiv(z, Blocks.CHUNK_Z);
        TerrainAccessor.SnapshotSource source = accessor.snapshotSource(chunkX, chunkZ);
        if (source == null) return Blocks.MAX_Y;
        ColumnLightCache cached = columnCache(chunkX, chunkZ, source);
        int localX = Math.floorMod(x, Blocks.CHUNK_X);
        int localZ = Math.floorMod(z, Blocks.CHUNK_Z);
        int column = localX + localZ * Blocks.CHUNK_X;
        int top = cached.motionBlockingNoLeavesTop[column];
        if (top == Short.MIN_VALUE) {
            top = Blocks.MIN_Y - 1;
            for (int y = Blocks.MAX_Y; y >= Blocks.MIN_Y; y--) {
                int index = Blocks.blockIndex(localX, y, localZ);
                int id = cached.blockTypeAt(index);
                if (!BlockFamilies.isLeaves(id) && (BuildingBlockRules.blocksMotion(
                        id, cached.blockStateAt(index))
                        || Fluids.isWaterMedium(id) || Fluids.isLava(id))) {
                    top = y;
                    break;
                }
            }
            cached.motionBlockingNoLeavesTop[column] = (short) top;
        }
        return top;
    }

    int sunlightLevel(int x, int y, int z, long worldTime) {
        if (worldTime >= 6_000) return 0;
        return Math.max(0, rawSkyLight(x, y, z) - skyDarken(worldTime));
    }

    private static int skyDarken(long worldTime) {
        return worldTime >= 6_000 ? 11 : 0;
    }

    private int propagatedLevel(int originX, int originY, int originZ,
                                boolean includeSky, boolean includeBlocks, int skyDarken) {
        candidateLightQueries++;
        prepareSources(originX, originZ);
        int centerSourceIndex = sourceIndex(originX, originZ);
        TerrainAccessor.SnapshotSource centerSource = centerSourceIndex < 0
                ? null : querySources[centerSourceIndex];
        int memoMode = (includeSky ? 1 : 0) | (includeBlocks ? 2 : 0) | (skyDarken << 2);
        long originRevision = originDependencyRevision(originX, originZ);
        int memoized = lightMemo.get(originX, originY, originZ, memoMode,
                centerSource, originRevision);
        if (memoized != LightMemo.MISS) {
            memoHits++;
            return memoized;
        }
        memoMisses++;
        if (originY < Blocks.MIN_Y || originY > Blocks.MAX_Y) {
            lightMemo.put(originX, originY, originZ, memoMode, centerSource, originRevision, 0);
            return 0;
        }
        if (blockAt(originX, originY, originZ) < 0
                || blocksLight(originX, originY, originZ)) {
            lightMemo.put(originX, originY, originZ, memoMode, centerSource, originRevision, 0);
            return 0;
        }
        // 블록 광만 요청하는 스폰 후보는 대부분 광원이 없다. 청크별 음수 결과를 재사용해 BFS를 건너뛴다.
        if (!includeSky && includeBlocks && !hasBlockEmissionInQuery(originY)) {
            lightMemo.put(originX, originY, originZ, memoMode, centerSource, originRevision, 0);
            return 0;
        }

        int generation = nextGeneration();
        int head = 0;
        int tail = 0;
        int center = encode(0, 0, 0);
        queue[tail++] = center;
        queueDistance[0] = 0;
        visited[center] = generation;
        int bestSky = 0;
        int bestBlock = 0;

        while (head < tail) {
            int queueIndex = head++;
            int packed = queue[queueIndex];
            int dx = packed % SIDE - RADIUS;
            int rest = packed / SIDE;
            int dz = rest % SIDE - RADIUS;
            int dy = rest / SIDE - RADIUS;
            int distance = Byte.toUnsignedInt(queueDistance[queueIndex]);
            if (distance > RADIUS) continue;
            int x = originX + dx;
            int y = originY + dy;
            int z = originZ + dz;
            if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) continue;
            int id = blockAt(x, y, z);
            if (id < 0) continue;
            int state = stateAt(x, y, z);

            if (includeBlocks) {
                bestBlock = Math.max(bestBlock, emission(id, state) - distance);
            }
            if (blocksLight(id, state)) continue;
            if (includeSky && openToSky(x, y, z)) {
                bestSky = Math.max(bestSky, 15 - distance);
            }
            int best = Math.max(bestBlock, Math.max(0, bestSky - skyDarken));
            if (best >= 15 || distance == RADIUS) continue;

            int nextDistance = distance + 1;
            if (!faceOccludes(id, state, blockAt(x + 1, y, z),
                    stateAt(x + 1, y, z), 1)) tail = enqueue(dx + 1, dy, dz, nextDistance, generation, tail);
            if (!faceOccludes(id, state, blockAt(x - 1, y, z),
                    stateAt(x - 1, y, z), 0)) tail = enqueue(dx - 1, dy, dz, nextDistance, generation, tail);
            if (!faceOccludes(id, state, blockAt(x, y + 1, z),
                    stateAt(x, y + 1, z), 3)) tail = enqueue(dx, dy + 1, dz, nextDistance, generation, tail);
            if (!faceOccludes(id, state, blockAt(x, y - 1, z),
                    stateAt(x, y - 1, z), 2)) tail = enqueue(dx, dy - 1, dz, nextDistance, generation, tail);
            if (!faceOccludes(id, state, blockAt(x, y, z + 1),
                    stateAt(x, y, z + 1), 5)) tail = enqueue(dx, dy, dz + 1, nextDistance, generation, tail);
            if (!faceOccludes(id, state, blockAt(x, y, z - 1),
                    stateAt(x, y, z - 1), 4)) tail = enqueue(dx, dy, dz - 1, nextDistance, generation, tail);
        }
        bfsVisitedCells += head;
        maxBfsVisitCount = Math.max(maxBfsVisitCount, head);
        int result = Math.max(bestBlock, Math.max(0, bestSky - skyDarken));
        lightMemo.put(originX, originY, originZ, memoMode, centerSource, originRevision, result);
        return result;
    }

    private int enqueue(int dx, int dy, int dz, int distance, int generation, int tail) {
        if (distance > RADIUS) return tail;
        int encoded = encode(dx, dy, dz);
        if (visited[encoded] == generation) return tail;
        visited[encoded] = generation;
        queue[tail] = encoded;
        queueDistance[tail] = (byte) distance;
        return tail + 1;
    }

    private static int encode(int dx, int dy, int dz) {
        return dx + RADIUS + (dz + RADIUS) * SIDE + (dy + RADIUS) * SIDE * SIDE;
    }

    private int nextGeneration() {
        if (++visitGeneration == 0) {
            Arrays.fill(visited, 0);
            visitGeneration = 1;
        }
        return visitGeneration;
    }

    private void prepareSources(int x, int z) {
        minChunkX = Math.floorDiv(x - RADIUS, Blocks.CHUNK_X);
        minChunkZ = Math.floorDiv(z - RADIUS, Blocks.CHUNK_Z);
        int maxChunkX = Math.floorDiv(x + RADIUS, Blocks.CHUNK_X);
        int maxChunkZ = Math.floorDiv(z + RADIUS, Blocks.CHUNK_Z);
        chunkWidth = maxChunkX - minChunkX + 1;
        chunkDepth = maxChunkZ - minChunkZ + 1;
        int index = 0;
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                TerrainAccessor.SnapshotSource source = accessor.snapshotSource(chunkX, chunkZ);
                querySources[index] = source;
                queryColumns[index] = source == null ? null : columnCache(chunkX, chunkZ, source);
                index++;
            }
        }
        Arrays.fill(querySources, index, querySources.length, null);
        Arrays.fill(queryColumns, index, queryColumns.length, null);
    }

    private ColumnLightCache columnCache(int chunkX, int chunkZ,
                                         TerrainAccessor.SnapshotSource source) {
        long key = chunkKey(chunkX, chunkZ);
        ColumnLightCache cached = columns.get(key);
        if (cached != null && cached.source == source) {
            // Tests and same-tick mutation publication may preserve wrapper identity. The explicit
            // invalidation bit still owns freshness and must refresh the touched columns.
            if (cached.sourceMayChange) cached.rebind(source);
            return cached;
        }
        if (cached != null && cached.sourceMayChange) {
            cached.rebind(source);
            return cached;
        }
        if (cached == null && columns.size() >= CACHE_CAP) {
            columns.clear();
        } else if (cached != null) {
            // A replacement published without the ordinary explicit invalidation is still observable.
            // Invalidate only origins whose radius can touch this source chunk before the memo lookup.
            invalidateDependentOrigins(chunkX, chunkZ);
        }
        ColumnLightCache replacement = new ColumnLightCache(source);
        columns.put(key, replacement);
        return replacement;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffff_ffffL);
    }

    private long originDependencyRevision(int originX, int originZ) {
        long key = chunkKey(Math.floorDiv(originX, Blocks.CHUNK_X),
                Math.floorDiv(originZ, Blocks.CHUNK_Z));
        return originDependencyRevisions.get(key, 0L);
    }

    /** A radius-15 query can touch only its own chunk or one of the eight adjacent chunks. */
    private void invalidateDependentOrigins(int changedChunkX, int changedChunkZ) {
        long revision = ++nextDependencyRevision;
        if (revision == 0) {
            // Practically unreachable overflow; clearing both structures keeps equality safe.
            originDependencyRevisions.clear();
            lightMemo.clear();
            revision = ++nextDependencyRevision;
        }
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                originDependencyRevisions.put(
                        chunkKey(changedChunkX + dx, changedChunkZ + dz), revision);
            }
        }
    }

    private boolean hasBlockEmissionInQuery(int originY) {
        int count = chunkWidth * chunkDepth;
        for (int index = 0; index < count; index++) {
            ColumnLightCache cached = queryColumns[index];
            if (cached != null && cached.hasEmission(
                    originY - RADIUS, originY + RADIUS)) return true;
        }
        return false;
    }

    private int sourceIndex(int x, int z) {
        int cx = Math.floorDiv(x, Blocks.CHUNK_X) - minChunkX;
        int cz = Math.floorDiv(z, Blocks.CHUNK_Z) - minChunkZ;
        if (cx < 0 || cz < 0 || cx >= chunkWidth || cz >= chunkDepth) return -1;
        int index = cx + cz * chunkWidth;
        return index < querySources.length ? index : -1;
    }

    private int blockAt(int x, int y, int z) {
        if (y < Blocks.MIN_Y) return Blocks.BEDROCK;
        if (y > Blocks.MAX_Y) return Blocks.AIR;
        int sourceIndex = sourceIndex(x, z);
        ColumnLightCache cached = sourceIndex < 0 ? null : queryColumns[sourceIndex];
        if (cached == null) return -1;
        return cached.blockTypeAt(Blocks.blockIndex(
                Math.floorMod(x, Blocks.CHUNK_X), y, Math.floorMod(z, Blocks.CHUNK_Z)));
    }

    private int stateAt(int x, int y, int z) {
        int sourceIndex = sourceIndex(x, z);
        ColumnLightCache cached = sourceIndex < 0 ? null : queryColumns[sourceIndex];
        if (cached == null || y < Blocks.MIN_Y || y > Blocks.MAX_Y) return 0;
        return cached.blockStateAt(Blocks.blockIndex(
                Math.floorMod(x, Blocks.CHUNK_X), y, Math.floorMod(z, Blocks.CHUNK_Z)));
    }

    private boolean openToSky(int x, int y, int z) {
        int sourceIndex = sourceIndex(x, z);
        ColumnLightCache cached = sourceIndex < 0 ? null : queryColumns[sourceIndex];
        if (cached == null) return false;
        int localX = Math.floorMod(x, Blocks.CHUNK_X);
        int localZ = Math.floorMod(z, Blocks.CHUNK_Z);
        int column = localX + localZ * Blocks.CHUNK_X;
        int top = cached.top[column];
        if (top == Short.MIN_VALUE) {
            top = Blocks.MIN_Y - 1;
            int aboveId = Blocks.AIR, aboveState = 0;
            for (int scanY = Blocks.MAX_Y; scanY >= Blocks.MIN_Y; scanY--) {
                int index = Blocks.blockIndex(localX, scanY, localZ);
                int id = cached.blockTypeAt(index);
                int state = cached.blockStateAt(index);
                if (attenuatesDirectSky(id, state) || faceOccludes(aboveId, aboveState, id, state, 2)) {
                    top = scanY;
                    break;
                }
                aboveId = id;
                aboveState = state;
            }
            cached.top[column] = (short) top;
        }
        return y > top;
    }

    /** Only pinned slab/stair light-occlusion shapes, never arbitrary collision boxes. */
    private static int lightShapeOctants(int id, int state) {
        if (BuildingBlockRules.isSlab(id)) {
            int half = state & ~0x80;
            return half == 2 ? 0 : half == 1 ? 240 : 15;
        }
        if (!BuildingBlockRules.isStairs(id)) return 0;
        int q = BuildingBlockRules.stairQuarterMask(state);
        int linear = (q & 3) | ((q & 4) << 1) | ((q & 8) >> 1);
        return (state & 4) != 0 ? 240 | linear : 15 | (linear << 4);
    }

    /** -X,+X,-Y,+Y,-Z,+Z, in the same world-axis orientation on opposite faces. */
    private static int lightFaceMask(int id, int state, int face) {
        int axis = face >> 1, side = face & 1, mask = 0;
        if (id == Blocks.FARMLAND || id == Blocks.DIRT_PATH || id == Blocks.SNOW) {
            int height = id == Blocks.SNOW ? Math.max(1, Math.min(8, state)) * 2 : 15;
            if (axis == 1) return side == 0 || height == 16 ? -1 : 0;
            int column = (1 << height) - 1;
            return column | (column << 16);
        }
        int octants = lightShapeOctants(id, state);
        if (octants == 0) return 0;
        for (int v = 0; v < 2; v++) for (int u = 0; u < 2; u++) {
            int x = axis == 0 ? side : u;
            int y = axis == 1 ? side : v;
            int z = axis == 2 ? side : axis == 0 ? u : v;
            if ((octants & (1 << (x + z * 2 + y * 4))) != 0) {
                mask |= 255 << (axis == 1 ? (u + v * 2) * 8 : u * 16 + v * 8);
            }
        }
        return mask;
    }

    private static boolean faceOccludes(int a, int aState, int b, int bState, int face) {
        return (lightFaceMask(a, aState, face) | lightFaceMask(b, bState, face ^ 1)) == -1;
    }

    private boolean blocksLight(int x, int y, int z) {
        int id = blockAt(x, y, z);
        return id < 0 || blocksLight(id, stateAt(x, y, z));
    }

    private static boolean attenuatesDirectSky(int id, int state) {
        return blocksLight(id, state) || Fluids.isWaterMedium(id)
                || id == Blocks.ICE || id == Blocks.MANGROVE_ROOTS
                // [FROST-SOUL] 살얼음도 얼음과 같은 noOcclusion 반투명 풀 셀이다.
                || id == Blocks.FROSTED_ICE
                || (id >= Blocks.SPAWNER_BASE && id <= Blocks.SPAWNER_BASE + 2)
                // [TRIAL] 트라이얼 스포너·금고도 몬스터 스포너처럼 풀 셀 noOcclusion 이다.
                || Blocks.isTrialChamberFixture(id)
                || BlockFamilies.isLeaves(id);
    }

    /** [BEACON] 바닐라 {@code getLightDampening() >= 15} 의 이 저장소 판정(신호기 빔 차단이 같은 표를 쓴다). */
    static boolean blocksLight(int id, int state) {
        if (!Fluids.isSolid(id)) return false;
        if (id == Blocks.TINTED_GLASS) return true;
        if (Blocks.isGlassBlock(id) || id == Blocks.ICE || id == Blocks.POWDER_SNOW
                || id == Blocks.FROSTED_ICE
                // [OPENABLE-METAL] 구리 창살도 철창과 같은 IronBarsBlock 이라 빛을 막지 않는다.
                || Blocks.isGlassPane(id) || id == Blocks.IRON_BARS || Blocks.isCopperBars(id)
                || Blocks.isDoor(id) || Blocks.isBed(id)
                // [CHEST-FAMILY] 상자 형상군 전체가 14/16 인셋 박스라 빛을 막지 않는다.
                || Blocks.isChestShaped(id)
                || id == Blocks.FARMLAND || id == Blocks.DIRT_PATH || Blocks.isTrapdoor(id)
                || id == Blocks.CAMPFIRE || id == Blocks.SNOW
                // 랜턴은 바닐라 LanternBlock 이 noOcclusion 이라 매달린 칸의 빛을 막지 않는다.
                // 구리 랜턴도 같은 물성을 그대로 쓴다(Blocks.isCopperBuildingBlock 주석).
                || id == Blocks.LANTERN || id == Blocks.COPPER_LANTERN
                || id >= Blocks.EXPOSED_COPPER_LANTERN
                        && id <= Blocks.WAXED_OXIDIZED_COPPER_LANTERN
                || id == Blocks.MOSS_CARPET || id == Blocks.BIG_DRIPLEAF
                || id == Blocks.FIREFLY_BUSH || id == Blocks.RAFFLESIA
                || id == Blocks.MANGROVE_ROOTS
                || Blocks.isFence(id) || Blocks.isFenceGate(id)
                || BuildingBlockRules.isWall(id)
                || BuildingBlockRules.isStairs(id)
                // [END-PORTAL] 틀은 13/16 몸체(+눈 기둥)라 solidRender 가 아니고 propagatesSkylightDown
                // 이 참이어서 바닐라 getLightDampening 이 0 이다(클라 opaque=false 와 같은 결론).
                || id == Blocks.END_PORTAL_FRAME
                // [BLOCK-SHAPES] 모델 블록 열 종도 풀 셀 형상이 아니라 propagatesSkylightDown 이
                // 참이고 getLightDampening 이 0 이다(고정 서버 전 state 확인).
                || BlockModelShapes.has(id)
                || (id >= Blocks.SPAWNER_BASE && id <= Blocks.SPAWNER_BASE + 2)
                // [TRIAL] 트라이얼 스포너·금고(noOcclusion 컷아웃 풀 셀)와 [DEEP-DARK] 말린
                // 가스트(noOcclusion · box(3,0,3,13,10,13))는 바닐라에서 빛을 막지 않는다.
                || Blocks.isTrialChamberFixture(id) || id == Blocks.DRIED_GHAST
                // [TRIAL-GAP] 무거운 핵은 box(4,0,4,12,8,12) 라 solidRender 가 아니고 물에 잠길 수 있는
                // 비-풀 형상이라 propagatesSkylightDown 이 참이다 — 바닐라 getLightDampening 0.
                || id == Blocks.HEAVY_CORE
                // [DRAGON] 드래곤 알은 noOcclusion · box(1,0,1,15,16,15) 라 solidRender 가 아니다.
                || id == Blocks.DRAGON_EGG
                // [END-CITY] 드래곤 머리(부분 모양)와 벽 현수막(비고체)은 빛을 막지 않는다.
                || id == Blocks.DRAGON_HEAD || id == Blocks.MAGENTA_WALL_BANNER
                || BlockFamilies.isLeaves(id)) return false;
        // 반 블록은 double(state 2)일 때만 빛을 막는다. 종별 목재 반 블록도 같은 규칙이라
        // 판정을 형상군 정본 하나로 모은다.
        if (BuildingBlockRules.isSlab(id)) {
            return state == BuildingBlockRules.SLAB_DOUBLE;
        }
        return true;
    }

    /**
     * 블록광 방출표. <b>클라 {@code BLOCK_LIGHT_EMISSION} 과 값이 같아야 한다</b> —
     * 한쪽에만 광원이 있으면 같은 자리에서 서버는 몹을 스폰하고 클라는 밝게 그린다.
     * 그 대조는 {@code MobLightEmissionParityTest} 와 정적판
     * {@code StandaloneBlockLightEmission.parity.test.ts} 가 공유 골든
     * {@code block-light-emission-golden.json} 으로 못박는다.
     */
    static int emission(int id, int state) {
        if (id == Blocks.FLESH_FAT_LAMP || id == Blocks.FLESH_PULSE_LAMP) return 15;
        if (id == Blocks.TORCH || (id >= Blocks.WALL_TORCH_N && id <= Blocks.WALL_TORCH_W)
                || id >= Blocks.COPPER_TORCH && id <= Blocks.COPPER_WALL_TORCH_W) return 14;
        if (id >= Blocks.LAVA_SOURCE && id <= Blocks.LAVA_SOURCE + 3) return 15;
        // [FURNACE-VARIANT] 바닐라 furnace/blast_furnace/smoker 는 lit=true 에서 모두 광원 13이다.
        if (id == Blocks.FURNACE_LIT
                || id == Blocks.BLAST_FURNACE_LIT || id == Blocks.SMOKER_LIT) {
            return 13;
        }
        if (id == Blocks.NETHER_PORTAL) return 11;
        // [IGLOO] 레드스톤 횃불은 신호·반전·연소가 없는 장식 광원(AGENTS 40)이지만 광량은
        // 바닐라 RedstoneTorchBlock 의 lit=true 값 7 그대로다. 클라 p38-igloo lightEmit 과 같다.
        if (id == Blocks.REDSTONE_TORCH || id == Blocks.REDSTONE_WALL_TORCH) return 7;
        if (id == Blocks.REDSTONE_LAMP_LIT) return 15;
        // [STRONGHOLD] 엔드 차원문 블록은 바닐라 lightLevel 15 다(클라 BlockRegistry 와 같은 값).
        if (id == Blocks.END_PORTAL) return 15;
        // [VOID-END] 엔드 막대는 바닐라 lightLevel 14 다(방향과 무관, 클라 lightEmit 과 같은 값).
        if (id == Blocks.END_ROD) return 14;
        // [END-PORTAL] 엔드 차원문 틀은 바닐라 lightLevel(state -> 1) 이다(javap Blocks.lambda$static$191).
        if (id == Blocks.END_PORTAL_FRAME) return 1;
        // [VOID-END] 엔드 관문은 바닐라 lightLevel 15 다(클라 BlockRegistry lightEmit 과 같은 값).
        if (id == Blocks.END_GATEWAY) return 15;
        // [BLOCK-SHAPES] 양조대는 바닐라 lightLevel(state -> 1) 이다(Blocks.BREWING_STAND, 모든 state).
        if (id == Blocks.BREWING_STAND) return 1;
        // [BLOCK-SHAPES] 바닐라 Blocks.LAVA_CAULDRON 은 lightLevel(state -> 15) 이다. 엔진 가마솥은 한 ID 가
        // CauldronRules kind(비트 2..3)로 빈·물·용암·가루눈을 겸하므로 용암일 때만 15 다.
        if (id == Blocks.CAULDRON) {
            return com.gameexpert.engine.inventory.CauldronRules.kind(state)
                    == com.gameexpert.engine.inventory.CauldronRules.LAVA ? 15 : 0;
        }
        // [DRAGON] 드래곤 알은 바닐라 lightLevel(state -> 1) 이다.
        if (id == Blocks.DRAGON_EGG) return 1;
        // [UTILITY] 신호기는 바닐라 lightLevel(state -> 15) 다(활성 여부와 무관, 클라 lightEmit 과 같은 값).
        if (id == Blocks.BEACON) return 15;
        if (id == Blocks.CAMPFIRE) return (state & 4) != 0 ? 15 : 0;
        // [TRIAL] 바닐라 Blocks.lambda$static$421/422: 트라이얼 스포너는 TrialSpawnerState.lightLevel
        // (0·4·8·8·8·0), 금고는 VaultState.LightLevel(비활성 6 · 나머지 12)이다.
        if (id == Blocks.TRIAL_SPAWNER) {
            return com.gameexpert.engine.trial.TrialSpawnerContract.lightLevel(state);
        }
        if (id == Blocks.VAULT) return com.gameexpert.engine.trial.TrialVaultContract.lightLevel(state);
        if (id == Blocks.GLOW_LICHEN) return 7;
        if (id == Blocks.CAVE_VINES || id == Blocks.CAVE_VINES_PLANT) return (state & 1) != 0 ? 14 : 0;
        if (id == Blocks.JACK_O_LANTERN) return 15;
        // [PRISMARINE] 바다 랜턴도 바닐라 광량 15 다(클라 BLOCK_LIGHT_EMISSION 과 같은 값).
        if (id == Blocks.SEA_LANTERN) return 15;
        // [CONDUIT] 콘딧도 광량 15 다. [A] ConduitBlock 의 lightLevel 은 **상태를 보지
        // 않으므로** 활성 여부와 무관하게 언제나 15 다 — 활성이 파생값이라 상태 비트가
        // 없다는 이 트랙의 계약과도 맞는다(클라 BLOCK_LIGHT_EMISSION 과 같은 값).
        if (id == Blocks.CONDUIT) return 15;
        // [BRIMSTONE] 바닐라 마그마 블록은 3, 프로젝트 트로피는 유황 군집과 같은 5다.
        if (id == Blocks.MAGMA) return 3;
        if (id == Blocks.BRIMSTONE_CARAPACE_TROPHY) return 5;
        if (id >= Blocks.OCHRE_FROGLIGHT && id <= Blocks.PEARLESCENT_FROGLIGHT) return 15;
        if (id == Blocks.GLOWSTONE) return 15;
        if (id == Blocks.SMALL_AMETHYST_BUD) return 1;
        if (id == Blocks.MEDIUM_AMETHYST_BUD) return 2;
        if (id == Blocks.LARGE_AMETHYST_BUD) return 4;
        if (id == Blocks.AMETHYST_CLUSTER) return 5;
        // [SPRING-TO-LIFE] 반딧불 수풀의 은은한 광량 2. 값 정본은 P26Rules 한 곳이라
        // 클라 BlockMeta(p26-spring)와 이 표가 갈릴 수 없다.
        if (id == Blocks.FIREFLY_BUSH) return P26Rules.FIREFLY_BUSH_LIGHT;
        if (id == Blocks.ENCHANTING_TABLE) return 7; // 바닐라 인챈트 테이블 광량 7.
        // [ENDER-SHULKER] 엔더 상자도 바닐라 lightLevel(7) 이다 — 인챈트 테이블과 같은 값이라
        // 한 줄 아래 붙인다. 클라 p30-ender-shulker 의 lightEmit 과 같은 값이어야 한다.
        if (id == Blocks.ENDER_CHEST) return 7;
        // [COPPER] 점등 구리 전구는 산화 단계마다 15/12/8/4 다(바닐라 CopperBulbBlock 의
        // lightLevel). 밀랍 쌍둥이도 같은 값이라 판정 정본 하나(copperBulbLightLevel)만 읽는다 —
        // 소등 전구·비전구는 그 함수가 0 을 준다.
        if (Blocks.isCopperBulb(id)) return Blocks.copperBulbLightLevel(id);
        // 구리 랜턴은 바닐라 lantern 물성을 따르는 추가 블록이라 광량도 lantern 과 같은 15 다.
        if (id == Blocks.COPPER_LANTERN || id == Blocks.LANTERN
                || id >= Blocks.EXPOSED_COPPER_LANTERN
                        && id <= Blocks.WAXED_OXIDIZED_COPPER_LANTERN) return 15;
        if (id == Blocks.FIRE) return 15; // 바닐라 FireBlock: lightLevel(state -> 15).
        if (id == Blocks.SCULK_CATALYST) return 6; // 바닐라 sculk_catalyst 광량 6.
        if (id == Blocks.SCULK_SENSOR) return 1;   // 바닐라 sculk_sensor 광량 1.
        return 0;
    }


    private static final class ColumnLightCache {
        private TerrainAccessor.SnapshotSource source;
        private final short[] blockTypes = new short[Blocks.CHUNK_BLOCKS];
        private final byte[] blockStates = new byte[Blocks.CHUNK_BLOCKS];
        private final short[] top = new short[Blocks.CHUNK_X * Blocks.CHUNK_Z];
        private final short[] occupiedTop = new short[Blocks.CHUNK_X * Blocks.CHUNK_Z];
        private final short[] motionBlockingNoLeavesTop =
                new short[Blocks.CHUNK_X * Blocks.CHUNK_Z];
        private final boolean[] dirtyColumns = new boolean[Blocks.CHUNK_X * Blocks.CHUNK_Z];
        private final boolean[] loadedSections = new boolean[Blocks.CHUNK_Y / 16];
        private int knownEmissionSections;
        private int presentEmissionSections;
        private boolean sourceMayChange;

        private ColumnLightCache(TerrainAccessor.SnapshotSource source) {
            this.source = source;
            Arrays.fill(top, Short.MIN_VALUE);
            Arrays.fill(occupiedTop, Short.MIN_VALUE);
            Arrays.fill(motionBlockingNoLeavesTop, Short.MIN_VALUE);
        }

        private boolean hasEmission(int minY, int maxY) {
            int firstSection = Math.max(0, (minY - Blocks.MIN_Y) >> 4);
            int lastSection = Math.min(loadedSections.length - 1,
                    (maxY - Blocks.MIN_Y) >> 4);
            if (firstSection > lastSection) return false;
            for (int section = firstSection; section <= lastSection; section++) {
                int bit = 1 << section;
                if ((knownEmissionSections & bit) == 0) {
                    ensureSection(section);
                    int start = section * 16 * Blocks.CHUNK_X * Blocks.CHUNK_Z;
                    int end = start + 16 * Blocks.CHUNK_X * Blocks.CHUNK_Z;
                    boolean present = false;
                    for (int index = start; index < end; index++) {
                        int id = Short.toUnsignedInt(blockTypes[index]);
                        int state = id == Blocks.CAMPFIRE
                                || id == Blocks.CAVE_VINES || id == Blocks.CAVE_VINES_PLANT
                                || id == Blocks.TRIAL_SPAWNER || id == Blocks.VAULT
                                || id == Blocks.CAULDRON
                                ? Byte.toUnsignedInt(blockStates[index]) : 0;
                        if (emission(id, state) > 0) {
                            present = true;
                            break;
                        }
                    }
                    knownEmissionSections |= bit;
                    if (present) presentEmissionSections |= bit;
                }
                if ((presentEmissionSections & bit) != 0) return true;
            }
            return false;
        }

        private void invalidateColumn(int localX, int localZ) {
            int column = localX + localZ * Blocks.CHUNK_X;
            top[column] = Short.MIN_VALUE;
            occupiedTop[column] = Short.MIN_VALUE;
            motionBlockingNoLeavesTop[column] = Short.MIN_VALUE;
            dirtyColumns[column] = true;
            knownEmissionSections = 0;
            presentEmissionSections = 0;
            sourceMayChange = true;
        }

        private void rebind(TerrainAccessor.SnapshotSource replacement) {
            source = replacement;
            // Ordinary block mutations publish a replacement snapshot but affect only the invalidated columns.
            // 이미 읽은 16높이 섹션의 해당 컬럼만 갱신한다. 아직 읽지 않은 섹션은 첫 실제 조회가
            // 채우므로 cold start에서 98,304셀 전체 복사를 다시 만들지 않는다.
            for (int column = 0; column < dirtyColumns.length; column++) {
                if (!dirtyColumns[column]) continue;
                int localX = column % Blocks.CHUNK_X;
                int localZ = column / Blocks.CHUNK_X;
                for (int y = Blocks.MIN_Y; y <= Blocks.MAX_Y; y++) {
                    int section = (y - Blocks.MIN_Y) >> 4;
                    if (!loadedSections[section]) continue;
                    int index = Blocks.blockIndex(localX, y, localZ);
                    blockTypes[index] = (short) replacement.blockTypeAt(index);
                    blockStates[index] = (byte) replacement.blockStateAt(index);
                }
                dirtyColumns[column] = false;
            }
            sourceMayChange = false;
        }

        private int blockTypeAt(int index) {
            ensureSection(index >>> 12);
            return Short.toUnsignedInt(blockTypes[index]);
        }

        private int blockStateAt(int index) {
            ensureSection(index >>> 12);
            return Byte.toUnsignedInt(blockStates[index]);
        }

        private void ensureSection(int section) {
            if (loadedSections[section]) return;
            source.copySectionTo(section, blockTypes, blockStates);
            loadedSections[section] = true;
        }
    }

    /** 틱 스코프 좌표별 결과 메모. source identity를 함께 비교해 immutable snapshot 계약을 유지합니다. */
    private static final class LightMemo {
        private static final int MISS = Integer.MIN_VALUE;
        private final int[] xs;
        private final int[] ys;
        private final int[] zs;
        private final int[] modes;
        private final int[] values;
        private final int[] stamps;
        private final long[] dependencyRevisions;
        private final TerrainAccessor.SnapshotSource[] sources;
        private final int[] occupiedSlots;
        private int generation = 1;
        private int size;
        private long clearedSlots;

        private LightMemo(int capacity) {
            xs = new int[capacity];
            ys = new int[capacity];
            zs = new int[capacity];
            modes = new int[capacity];
            values = new int[capacity];
            stamps = new int[capacity];
            dependencyRevisions = new long[capacity];
            sources = new TerrainAccessor.SnapshotSource[capacity];
            occupiedSlots = new int[capacity];
        }

        private int get(int x, int y, int z, int mode,
                        TerrainAccessor.SnapshotSource source, long dependencyRevision) {
            int slot = slot(x, y, z, mode);
            while (stamps[slot] == generation) {
                if (xs[slot] == x && ys[slot] == y && zs[slot] == z
                        && modes[slot] == mode) {
                    return sources[slot] == source
                            && dependencyRevisions[slot] == dependencyRevision
                            ? values[slot] : MISS;
                }
                slot = (slot + 1) & (stamps.length - 1);
            }
            return MISS;
        }

        private void put(int x, int y, int z, int mode,
                         TerrainAccessor.SnapshotSource source, long dependencyRevision,
                         int value) {
            if (size >= stamps.length * 3 / 4) clear();
            int slot = slot(x, y, z, mode);
            while (stamps[slot] == generation) {
                if (xs[slot] == x && ys[slot] == y && zs[slot] == z
                        && modes[slot] == mode) {
                    sources[slot] = source;
                    dependencyRevisions[slot] = dependencyRevision;
                    values[slot] = value;
                    return;
                }
                slot = (slot + 1) & (stamps.length - 1);
            }
            stamps[slot] = generation;
            occupiedSlots[size] = slot;
            xs[slot] = x;
            ys[slot] = y;
            zs[slot] = z;
            modes[slot] = mode;
            sources[slot] = source;
            dependencyRevisions[slot] = dependencyRevision;
            values[slot] = value;
            size++;
        }

        private void clear() {
            if (++generation == 0) {
                Arrays.fill(stamps, 0);
                generation = 1;
            }
            for (int index = 0; index < size; index++) {
                sources[occupiedSlots[index]] = null;
            }
            clearedSlots += size;
            size = 0;
        }

        private int slot(int x, int y, int z, int mode) {
            int hash = x * 0x9e3779b9;
            hash = (hash ^ y) * 0x85ebca6b;
            hash = (hash ^ z) * 0xc2b2ae35;
            return ((hash ^ mode * 0x27d4eb2d) & 0x7fffffff) & (stamps.length - 1);
        }
    }
}
