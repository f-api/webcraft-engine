package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import com.gameexpert.terrain.Blocks;

/**
 * [BLOCK-SHAPES] 바닐라 면별 지지 판정 {@code BlockState#isFaceSturdy(level, pos, face, type)}.
 * 클라 정본 {@code client/src/world/BlockFaceSturdiness.ts} 와 같은 식이고, 두 권위는 고정 서버가
 * 낸 판정({@code original/block-shape-golden.json} 의 sturdy 열)으로 함께 못박힌다.
 *
 * <p>바닐라 26.3 {@code SupportType}(javap):
 * <ul>
 *   <li>FULL: {@code Block.isFaceFull(getBlockSupportShape(), face)}</li>
 *   <li>CENTER: 면 형상이 {@code CENTER_SUPPORT_SHAPE = column(2, 0, 10)} 의 면 투영을 덮는다
 *       (위·아래 면은 2×2 중앙, 옆면은 폭 2 · 높이 0..10 띠).</li>
 *   <li>RIGID: 면 형상이 {@code RIGID_SUPPORT_SHAPE = join(block(), column(12, 0, 16), ONLY_FIRST)}
 *       의 면 투영을 덮는다(위·아래 면은 2px 테두리, 옆면은 면 전체).</li>
 * </ul>
 * {@code VoxelShape#getFaceShape} 는 면 안쪽 1e-7 평면을 품은 복셀 조각을 남기므로 박스가 그
 * 평면에 걸치면 그 면에 들어간다(셀보다 높은 울타리 기둥도 윗면이 있다). 지지 형상은 충돌
 * 형상이고, getBlockSupportShape 를 재정의하는 잎(빈 형상)·눈 층(층 높이 전체)·진흙(풀 큐브)만
 * 따로 적는다.
 *
 * <h2>면·종류 번호</h2>
 * 면은 바닐라 Direction 3D 값 0 down · 1 up · 2 north · 3 south · 4 west · 5 east, 종류는
 * 0 FULL · 1 CENTER · 2 RIGID 다.
 */
public final class BlockFaceSturdiness {

    public static final int DOWN = 0;
    public static final int UP = 1;
    public static final int NORTH = 2;
    public static final int SOUTH = 3;
    public static final int WEST = 4;
    public static final int EAST = 5;

    public static final int FULL = 0;
    public static final int CENTER = 1;
    public static final int RIGID = 2;

    private static final double PLANE_EPS = 1e-7;
    private static final ConcurrentHashMap<Integer, Integer> CACHE = new ConcurrentHashMap<>();

    private BlockFaceSturdiness() {
    }

    /** {@code BlockState#isFaceSturdy(level, pos, face, type)}. */
    public static boolean isFaceSturdy(int id, int state, int face, int type) {
        return (mask(id, state) & (1 << (type * 6 + face))) != 0;
    }

    /** {@code Block.canSupportCenter(level, pos, face)}. */
    public static boolean canSupportCenter(int id, int state, int face) {
        return isFaceSturdy(id, state, face, CENTER);
    }

    /** {@code Block.canSupportRigidBlock(level, pos)}: 윗면 RIGID. */
    public static boolean canSupportRigidBlock(int id, int state) {
        return isFaceSturdy(id, state, UP, RIGID);
    }

    /** 18 개 판정의 비트 마스크: 비트 {@code type * 6 + face}. */
    public static int mask(int id, int state) {
        int key = id * 256 + (state & 0xff);
        Integer cached = CACHE.get(key);
        if (cached != null) return cached;
        List<double[]> boxes = supportBoxes(id, state);
        int mask = 0;
        for (int type = FULL; type <= RIGID; type++) {
            for (int face = DOWN; face <= EAST; face++) {
                if (boxesFaceSturdy(boxes, face, type)) mask |= 1 << (type * 6 + face);
            }
        }
        CACHE.put(key, mask);
        return mask;
    }

    /** 이 블록 state 의 바닐라 지지 형상(블록 단위 AABB). */
    public static List<double[]> supportBoxes(int id, int state) {
        List<double[]> boxes = new ArrayList<>();
        if (BlockFamilies.isLeaves(id)) return boxes;
        if (id == Blocks.MUD) {
            boxes.add(new double[] {0, 0, 0, 1, 1, 1});
            return boxes;
        }
        if (id == Blocks.SNOW) {
            int layers = Math.max(1, Math.min(8, state));
            boxes.add(new double[] {0, 0, 0, 1, layers * 2 / 16.0, 1});
            return boxes;
        }
        if (id == Blocks.HONEY_BLOCK) {
            boxes.add(new double[] {1 / 16.0, 0, 1 / 16.0, 15 / 16.0, 15 / 16.0, 15 / 16.0});
            return boxes;
        }
        BuildingBlockRules.forCollisionBoxes(id, state,
                (x0, y0, z0, x1, y1, z1) -> boxes.add(new double[] {x0, y0, z0, x1, y1, z1}));
        return boxes;
    }

    /** 임의 박스 목록(블록 단위) 한 면의 판정. */
    public static boolean boxesFaceSturdy(List<double[]> boxes, int face, int type) {
        int axis = face <= UP ? 1 : face <= SOUTH ? 2 : 0;
        double plane = (face & 1) == 1 ? 1 - PLANE_EPS : PLANE_EPS;
        int u = axis == 0 ? 2 : 0;
        int v = axis == 1 ? 2 : 1;
        List<double[]> rects = new ArrayList<>();
        for (double[] box : boxes) {
            if (box[axis] > plane || box[axis + 3] < plane) continue;
            double u0 = Math.max(0, box[u]), u1 = Math.min(1, box[u + 3]);
            double v0 = Math.max(0, box[v]), v1 = Math.min(1, box[v + 3]);
            if (u1 > u0 && v1 > v0) rects.add(new double[] {u0, v0, u1, v1});
        }
        if (rects.isEmpty()) return false;
        boolean vertical = face <= UP;
        if (type == CENTER) {
            // CENTER_SUPPORT_SHAPE = column(2, 0, 10): 위·아래 면에는 x/z 7..9, 옆면에는 폭 2 · 높이 0..10 띠.
            return vertical
                    ? covers(rects, 7 / 16.0, 9 / 16.0, 7 / 16.0, 9 / 16.0, 0, 0)
                    : covers(rects, 7 / 16.0, 9 / 16.0, 0, 10 / 16.0, 0, 0);
        }
        // RIGID_SUPPORT_SHAPE 의 2px 테두리는 옆면으로 투영하면 면 전체라 옆면에서는 FULL 과 같다.
        if (type == RIGID && vertical) return covers(rects, 0, 1, 0, 1, 2 / 16.0, 14 / 16.0);
        return covers(rects, 0, 1, 0, 1, 0, 0);
    }

    /** 사각형 합집합이 [uLo, uHi]×[vLo, vHi] 에서 열린 구멍 (holeLo, holeHi)² 을 뺀 영역을 덮는가. */
    private static boolean covers(List<double[]> rects, double uLo, double uHi, double vLo, double vHi,
            double holeLo, double holeHi) {
        TreeSet<Double> us = new TreeSet<>(List.of(uLo, uHi, holeLo, holeHi));
        TreeSet<Double> vs = new TreeSet<>(List.of(vLo, vHi, holeLo, holeHi));
        for (double[] r : rects) {
            us.add(r[0]);
            us.add(r[2]);
            vs.add(r[1]);
            vs.add(r[3]);
        }
        Double[] uCuts = us.subSet(uLo, true, uHi, true).toArray(new Double[0]);
        Double[] vCuts = vs.subSet(vLo, true, vHi, true).toArray(new Double[0]);
        for (int i = 0; i + 1 < uCuts.length; i++) {
            double cu = (uCuts[i] + uCuts[i + 1]) / 2;
            for (int j = 0; j + 1 < vCuts.length; j++) {
                double cv = (vCuts[j] + vCuts[j + 1]) / 2;
                if (cu > holeLo && cu < holeHi && cv > holeLo && cv < holeHi) continue;
                boolean covered = false;
                for (double[] r : rects) {
                    if (cu > r[0] && cu < r[2] && cv > r[1] && cv < r[3]) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) return false;
            }
        }
        return true;
    }
}
