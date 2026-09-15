package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gameexpert.terrain.Blocks;

/**
 * 월드별 피뢰침 원장. 바닐라 {@code ServerLevel#findLightningRod} 가 읽는
 * {@code PoiManager} 의 {@code minecraft:lightning_rod} 인덱스에 해당한다.
 *
 * <pre>
 * public Optional&lt;BlockPos&gt; findLightningRod(BlockPos pos) {
 *     Optional&lt;BlockPos&gt; optional = this.getPoiManager().findClosest(
 *             holder -&gt; holder.is(PoiTypes.LIGHTNING_ROD),
 *             p -&gt; p.getY() == this.getHeight(Heightmap.Types.WORLD_SURFACE, p.getX(), p.getZ()) - 1,
 *             pos, 128, PoiManager.Occupancy.ANY);
 *     return optional.map(p -&gt; p.above(1));
 * }
 * </pre>
 * 세 가지가 규칙의 전부다 — (1) 반경 128블록, (2) 피뢰침이 그 컬럼의 <b>가장 높은 블록</b>일 것,
 * (3) 타격점은 피뢰침 <b>한 칸 위</b>.
 *
 * <h2>원장 설계 — 왜 새 테이블이 없나</h2>
 * 모닥불({@code CampfireStorage})·상자·화로는 <b>블록만으로는 복원할 수 없는 상태</b>(내용물)를
 * 들고 있어 전용 테이블이 필요하다. 피뢰침은 상태가 없다 — 원장이 담는 것은 "어디에 있나" 뿐이고
 * 그 정보의 정본은 이미 {@code world_block_diffs}(=플레이어 편집)다. 그래서 이 원장은
 * {@link SpawnerIndex} 와 같은 <b>파생 캐시</b>이고, 영속·언로드 복구는 다음 두 경로로 닫힌다.
 * <ul>
 *   <li><b>영속</b>: 설치·파괴가 {@code WorldRuntime#setIndexedOverlay} 단일 깔때기를 지나므로
 *       그 자리에서 {@link #record}. 같은 깔때기가 diff 를 DB 에 적는다.</li>
 *   <li><b>언로드 복구</b>: 청크 축출은 {@link #forgetChunk}, 재활성화는 그 청크의 persisted diff
 *       목록을 {@link #recover} 로 되먹인다. 피뢰침은 지형 생성이 놓지 않는 블록(제작 전용)이라
 *       diff 목록만 보면 그 청크의 피뢰침이 전부 나온다 — 컬럼 전수 스캔이 필요 없다.</li>
 * </ul>
 * 원장에 없는 청크(=시뮬레이션 밖)의 피뢰침은 유인하지 않는다. 바닐라도 낙뢰는 로드된 청크에서만
 * 치므로 같은 경계다.
 *
 * <p>틱 스레드 전용이다.
 */
final class LightningRodIndex {

    /** 바닐라 {@code findLightningRod} 의 POI 탐색 반경(블록). */
    static final int SEARCH_RADIUS = 128;

    /** 컬럼의 가장 높은 비-공기 블록 y. 바닐라 {@code Heightmap.WORLD_SURFACE} − 1 이다. */
    @FunctionalInterface
    interface ColumnTop {
        int topOccupiedY(int x, int z);
    }

    /** 청크키 → 그 청크의 피뢰침 좌표들. 삽입 순서를 유지해 동점 정렬 전 순회가 재현된다. */
    private final Map<Long, Set<BlockPos>> rodsByChunk = new HashMap<>();

    /** 한 칸의 최종 블록을 원장에 반영한다. 피뢰침이면 등록, 아니면 (있었다면) 해제. */
    void record(int x, int y, int z, int blockType) {
        long key = chunkKey(Math.floorDiv(x, Blocks.CHUNK_X), Math.floorDiv(z, Blocks.CHUNK_Z));
        BlockPos pos = new BlockPos(x, y, z);
        if (blockType == Blocks.LIGHTNING_ROD) {
            rodsByChunk.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(pos);
            return;
        }
        Set<BlockPos> rods = rodsByChunk.get(key);
        if (rods == null || !rods.remove(pos)) return;
        if (rods.isEmpty()) rodsByChunk.remove(key);
    }

    /**
     * 재활성화된 청크의 persisted diff 로 그 청크 원장을 되살린다. 멱등하며 <b>더하기만</b> 한다.
     *
     * <p>통째 교체가 아닌 이유: 활성화 경로는 이미 활성인 청크에도 다시 들어올 수 있고, 그때 아직
     * DB 로 flush 되지 않은 이번 세션의 설치를 지워 버린다. 파괴는 축출 시
     * {@link #forgetChunk} 가 청크째 비우고 그 뒤 diff(AIR)는 여기서 걸러지므로 유령이 남지 않는다.
     */
    void recover(int chunkX, int chunkZ, Iterable<int[]> diffs) {
        long key = chunkKey(chunkX, chunkZ);
        for (int[] diff : diffs) {
            if (diff[3] != Blocks.LIGHTNING_ROD) continue;
            rodsByChunk.computeIfAbsent(key, ignored -> new LinkedHashSet<>())
                    .add(new BlockPos(diff[0], diff[1], diff[2]));
        }
    }

    /** 청크가 시뮬레이션에서 빠지면 그 청크의 피뢰침도 유인 후보에서 빠진다. */
    void forgetChunk(int chunkX, int chunkZ) {
        rodsByChunk.remove(chunkKey(chunkX, chunkZ));
    }

    /** 원장에 등록된 피뢰침 수(테스트·진단용). */
    int size() {
        int total = 0;
        for (Set<BlockPos> rods : rodsByChunk.values()) total += rods.size();
        return total;
    }

    /**
     * 후보 타격점에서 가장 가까운 유효 피뢰침. 없으면 {@code null}.
     *
     * <p>바닐라 {@code PoiManager.findClosest} 는 {@code distSqr} 최소값을 고르고, 후보 자격은
     * "그 컬럼의 최상단 블록"이다. 동점은 바닐라에서 청크 순회 순서로 갈리지만 여기서는
     * 좌표 사전순으로 고정해 양 권위가 같은 답을 낸다.
     *
     * @return {@code {x, y, z}} 피뢰침 블록 좌표. 타격점은 호출부가 한 칸 위로 올린다.
     */
    int[] nearest(int x, int y, int z, ColumnTop columnTop) {
        if (rodsByChunk.isEmpty()) return null;
        int chunkRadius = Math.floorDiv(SEARCH_RADIUS, Blocks.CHUNK_X) + 1;
        int centerChunkX = Math.floorDiv(x, Blocks.CHUNK_X);
        int centerChunkZ = Math.floorDiv(z, Blocks.CHUNK_Z);
        long limit = (long) SEARCH_RADIUS * SEARCH_RADIUS;
        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                Set<BlockPos> rods = rodsByChunk.get(chunkKey(centerChunkX + dx, centerChunkZ + dz));
                if (rods != null) candidates.addAll(rods);
            }
        }
        BlockPos best = null;
        long bestDistance = Long.MAX_VALUE;
        for (BlockPos rod : candidates) {
            long distance = distanceSquared(rod, x, y, z);
            if (distance > limit) continue;
            if (best != null && (distance > bestDistance
                    || distance == bestDistance && comparePositions(rod, best) >= 0)) {
                continue;
            }
            // 컬럼 최상단 확인은 가장 비싼 판정이라 거리 후보로 좁힌 뒤에만 한다.
            if (columnTop.topOccupiedY(rod.x(), rod.z()) != rod.y()) continue;
            best = rod;
            bestDistance = distance;
        }
        return best == null ? null : new int[] { best.x(), best.y(), best.z() };
    }

    private static long distanceSquared(BlockPos rod, int x, int y, int z) {
        long dx = (long) rod.x() - x;
        long dy = (long) rod.y() - y;
        long dz = (long) rod.z() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static int comparePositions(BlockPos left, BlockPos right) {
        int x = Integer.compare(left.x(), right.x());
        if (x != 0) return x;
        int y = Integer.compare(left.y(), right.y());
        return y != 0 ? y : Integer.compare(left.z(), right.z());
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
