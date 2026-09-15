package com.gameexpert.engine;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 리스폰 좌표 결정 규칙(S2a, 순수 함수). 개인 침대 지점이 설정돼 있고 그 위치의 블록이 여전히 침대(32)면
 * 침대 주변의 안전한 두 칸 높이 공간을 리스폰 좌표로 쓰고, 없거나 침대가 파괴·막혔으면
 * 월드 스폰으로 폴백한다.
 */
public final class RespawnRules {

    /** 머리 셀의 인접 8칸. 그중 발 셀은 안전 공간 검사에서 자연히 제외되어 7칸이 남습니다. */
    private static final int[][] HEAD_NEIGHBORS = {
            { 0, 0, -1 }, { 1, 0, 0 }, { 0, 0, 1 }, { -1, 0, 0 },
            { 1, 0, -1 }, { 1, 0, 1 }, { -1, 0, 1 }, { -1, 0, -1 },
    };

    private RespawnRules() {
    }

    @FunctionalInterface
    public interface SafeSpace {
        boolean test(int feetX, int feetY, int feetZ);
    }

    /** 리스폰 좌표(발밑 중심). */
    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    public static class Spawn {
        private final double x;
        private final double y;
        private final double z;
        private final boolean bed;
    }

    /**
     * @param hasBed          개인 침대 지점 설정 여부
     * @param bedBlockPresent 침대 지점의 현재 블록이 침대(32)인지(파괴 폴백 판정)
     * @param bx,by,bz        침대 머리 블록 좌표
     * @param facing          발에서 머리를 향하는 수평 방향(0=N,1=E,2=S,3=W)
     * @param worldX,worldY,worldZ 월드 스폰 좌표(폴백)
     * @param safeSpace       발밑 지지와 발·머리 공간을 현재 권위 월드에서 확인하는 함수
     */
    public static Spawn resolve(boolean hasBed, boolean bedBlockPresent,
            int bx, int by, int bz, int facing,
            double worldX, double worldY, double worldZ,
            SafeSpace safeSpace) {
        if (hasBed && bedBlockPresent) {
            for (int[] offset : HEAD_NEIGHBORS) {
                int x = bx + offset[0];
                int y = by + offset[1];
                int z = bz + offset[2];
                if (safeSpace.test(x, y, z)) {
                    return new Spawn(x + 0.5, y, z + 0.5, true);
                }
            }
            // 머리 주변에서 발 셀이 차지한 방향 너머의 세 칸도 검사한다. 이 세 칸을 빼면
            // 좁은 침실에서 발끝 뒤만 비어 있을 때 유효한 침대를 잘못 무효화한다.
            int dx = switch (facing & 3) {
                case 1 -> 1;
                case 3 -> -1;
                default -> 0;
            };
            int dz = switch (facing & 3) {
                case 2 -> 1;
                case 0 -> -1;
                default -> 0;
            };
            int rearX = bx - dx * 2;
            int rearZ = bz - dz * 2;
            int sideX = -dz;
            int sideZ = dx;
            for (int side = 0; side < 3; side++) {
                int lateral = side - 1;
                int x = rearX + sideX * lateral;
                int z = rearZ + sideZ * lateral;
                if (safeSpace.test(x, by, z)) {
                    return new Spawn(x + 0.5, by, z + 0.5, true);
                }
            }
        }
        return new Spawn(worldX, worldY, worldZ, false);
    }
}
