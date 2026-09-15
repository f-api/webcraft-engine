package com.gameexpert.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.gameexpert.terrain.Blocks;

/**
 * [PRISMARINE] 마른 스펀지의 물 흡수(MC Java 1.21.4 {@code SpongeBlock.removeWaterBreadthFirstSearch}).
 *
 * <p>바닐라 알고리즘 그대로다.
 * <ul>
 *   <li>스펀지 자리에서 **너비 우선**으로 6방향 이웃을 훑는다(스펀지 셀 자신은 검사하지 않는다).
 *   <li>큐에 넣을 때의 깊이 상한은 {@code depth &lt; 6} 이라 스펀지에서 <b>택시 거리 7칸</b>까지 닿는다.
 *   <li>흡수 개수가 <b>65칸</b>에 닿으면 그 자리에서 멈춘다.
 *   <li>한 칸이라도 빨아들이면 스펀지는 젖은 스펀지가 된다.
 * </ul>
 *
 * <p>divergence 둘.
 * <ol>
 *   <li>바닐라는 물에 잠긴 식물(다시마·해초·산호·바다 수세미)도 함께 빨아들이고 그 전리품을
 *       떨군다. 이 저장소는 단일 ID 월드 포맷이라 그 셀이 곧 물 소스이며, 식물 전리품 방출
 *       경로를 흡수 경로에 끌어들이지 않으려고 <b>물 셀({@link Blocks#WATER_SOURCE}~+7)만</b>
 *       흡수한다. 잠긴 식물은 탐색을 막는 벽이 된다.
 *   <li>바닐라는 흡수 뒤 젖은 스펀지가 네더에서 즉시 마르지만 이 저장소에는 네더가 없다.
 * </ol>
 *
 * <p>순수 함수라 서버 권위와 정적판 권위가 같은 판정을 공유한다 — 두 사본이 갈리면
 * 물이 한쪽에서만 사라지는 데스싱크가 된다.
 */
public final class SpongeAbsorption {
    /** 흡수 개수 상한(바닐라 65칸). 이 수에 닿는 즉시 탐색을 끝낸다. */
    public static final int MAX_ABSORBED = 65;
    /** 큐 확장 깊이 상한. 스펀지에서 닿는 최대 거리는 이 값 + 1 = 7칸이다. */
    public static final int MAX_DEPTH = 6;

    private static final int[][] NEIGHBORS = {
        {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0},
    };

    /** 좌표 → 블록 ID. 청크가 없으면 {@link #UNAVAILABLE} 를 돌려주어 탐색을 막는다. */
    public interface BlockLookup {
        int blockAt(int x, int y, int z);
    }

    /** 조회할 수 없는 좌표(미로드 청크). 물이 아닌 것으로 취급해 탐색이 넘어가지 않는다. */
    public static final int UNAVAILABLE = -1;

    private SpongeAbsorption() {
    }

    /** 물 셀인가. 잠긴 식물은 포함하지 않는다(위 divergence ①). */
    public static boolean isAbsorbableWater(int blockId) {
        return blockId >= Blocks.WATER_SOURCE && blockId <= Blocks.WATER_SOURCE + 7;
    }

    /**
     * {@code (x,y,z)} 에 마른 스펀지가 놓였을 때 비워야 할 물 셀 좌표를 바닐라 순서대로 돌려준다.
     * 비어 있으면 흡수가 일어나지 않은 것이므로 스펀지는 마른 상태로 남는다.
     */
    public static List<BlockPos> absorb(int x, int y, int z, BlockLookup lookup) {
        List<BlockPos> absorbed = new ArrayList<>();
        Set<BlockPos> removed = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{x, y, z, 0});
        while (!queue.isEmpty()) {
            int[] node = queue.poll();
            for (int[] step : NEIGHBORS) {
                int nx = node[0] + step[0];
                int ny = node[1] + step[1];
                int nz = node[2] + step[2];
                if (ny < Blocks.MIN_Y || ny > Blocks.MAX_Y) continue;
                BlockPos pos = new BlockPos(nx, ny, nz);
                if (removed.contains(pos)) continue;
                int block = lookup.blockAt(nx, ny, nz);
                if (block == UNAVAILABLE || !isAbsorbableWater(block)) continue;
                removed.add(pos);
                absorbed.add(pos);
                // 상한은 노드 경계가 아니라 흡수 시점에 건다 — 그래야 결과가 정확히 65칸에서 멎는다.
                if (absorbed.size() >= MAX_ABSORBED) return absorbed;
                if (node[3] < MAX_DEPTH) queue.add(new int[]{nx, ny, nz, node[3] + 1});
            }
        }
        return absorbed;
    }
}
