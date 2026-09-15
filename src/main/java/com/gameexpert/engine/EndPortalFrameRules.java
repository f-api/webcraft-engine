package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;

/**
 * 엔드 차원문 틀의 state 어휘와 바닐라 26.3 {@code EndPortalFrameBlock#getOrCreatePortalShape}
 * 판정. 순수 로직이며 정적판 {@code StandaloneEndPortalRules.ts} 가 같은 식의 사본이다.
 *
 * <h2>state 바이트</h2>
 * FACING[0..1](0=N, 1=E, 2=S, 3=W — 엔진 공통 수평 어휘) + EYE[2]. carrier 투영
 * ({@code CarrierStateProjection})과 편집 정규화({@link BuildingBlockRules#normalizeState})가
 * 이 두 필드만 남긴다.
 *
 * <h2>틀 모양 판정</h2>
 * 바닐라 {@code BlockPatternBuilder.start().aisle("?vvv?", ">???<", ">???<", ">???<", "?^^^?")}
 * 이며 문자 뜻은 javap 로 확인한 where 절 그대로다: {@code '?'} = 아무 블록(비적재 칸 포함),
 * {@code '^'} = eye=true·facing=south 틀, {@code '>'} = eye=true·facing=west,
 * {@code 'v'} = eye=true·facing=north, {@code '<'} = eye=true·facing=east.
 * 탐색은 {@code BlockPattern#find} 그대로 클릭 칸부터 +4 까지의 정육면체({@code betweenClosed}
 * 순서: x 가장 안쪽, 그다음 y, z)를 원점으로 두고 {@code Direction.values()} 순서의 forwards ×
 * up 조합(같은 축 제외)을 모두 시험해 처음 맞는 것을 쓴다. 가운데 3×3 은 {@code '?'} 라 비어
 * 있을 필요가 없다 — 바닐라는 그 칸을 {@code destroyBlock(pos, true)} 로 부수고 채운다.
 *
 * <p>{@code EndPortalFrameBlock} 에는 {@code neighborChanged}/{@code updateShape} 재정의가 없고
 * {@code EndPortalBlock} 도 이웃 변화로 스스로 사라지지 않는다. 그래서 틀이 바뀌어도 이미 열린
 * 차원문은 꺼지지 않는다(이 클래스에 소거 판정이 없는 이유).
 */
public final class EndPortalFrameRules {
    public static final int FACING_MASK = BuildingBlockRules.FACING_MASK;
    /** {@code EndPortalFrameBlock.HAS_EYE}. */
    public static final int EYE = 0x04;
    public static final int NORTH = 0;
    public static final int EAST = 1;
    public static final int SOUTH = 2;
    public static final int WEST = 3;

    /**
     * 바닐라 26.3 {@code EndPortalFrameBlock} 형상(javap): {@code SHAPE_EMPTY = Block.column(16, 0, 13)}
     * = box(0,0,0,16,13,16), {@code SHAPE_FULL = Shapes.or(SHAPE_EMPTY, Block.column(8, 13, 16))} 로
     * box(4,13,4,12,16,12) 눈 기둥이 더해진다. {@code getShape} 가 HAS_EYE 로 둘 중 하나를 고르고
     * {@code getCollisionShape} 재정의가 없어 충돌도 같은 형상이다. 각 행은
     * minX,minY,minZ,maxX,maxY,maxZ(px/16)이고 클라 정본 {@code BlockRegistry}
     * {@code END_PORTAL_FRAME_BODY_BOX_16} · {@code END_PORTAL_FRAME_EYE_BOX_16} 과 같은 값이다.
     */
    private static final double[][] EMPTY_SHAPE_BOXES_16 = {{0, 0, 0, 16, 13, 16}};
    private static final double[][] FILLED_SHAPE_BOXES_16 = {
        {0, 0, 0, 16, 13, 16}, {4, 13, 4, 12, 16, 12}
    };

    /** {@code BlockPattern} 의 aisle 한 장(깊이 1). 행이 height, 열이 width 다. */
    private static final String[] PATTERN = {"?vvv?", ">???<", ">???<", ">???<", "?^^^?"};
    private static final int SIZE = 5;
    /** {@code Direction.values()} 순서: DOWN, UP, NORTH, SOUTH, WEST, EAST. */
    private static final int[][] DIRECTIONS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0},
    };

    private EndPortalFrameRules() {
    }

    /** state 의 HAS_EYE 가 고르는 형상 박스(px/16). 호출자는 배열을 바꾸지 않는다. */
    static double[][] shapeBoxes16(int state) {
        return (state & EYE) != 0 ? FILLED_SHAPE_BOXES_16 : EMPTY_SHAPE_BOXES_16;
    }

    /** 형상의 윗면 높이(블록 단위): 빈 틀 13/16, 눈이 박힌 틀 1. */
    static double shapeTop(int state) {
        double top = 0;
        for (double[] box : shapeBoxes16(state)) top = Math.max(top, box[4] / 16.0);
        return top;
    }

    /** 틀 칸을 읽는 창. 비적재 칸은 {@code blockType} 이 음수다(바닐라 BlockInWorld null state). */
    public interface BlockView {
        int blockType(int x, int y, int z);

        int state(int x, int y, int z);
    }

    /**
     * 맞은 틀의 {@code BlockPatternMatch#getFrontTopLeft}. 바닐라 {@code EnderEyeItem#useOn} 은
     * 여기서 (-3, 0, -3) 을 더한 칸부터 x·z 로 3×3 을 채운다.
     */
    public record PortalMatch(int frontTopLeftX, int frontTopLeftY, int frontTopLeftZ) {
        public int interiorMinX() { return frontTopLeftX - 3; }
        public int interiorY() { return frontTopLeftY; }
        public int interiorMinZ() { return frontTopLeftZ - 3; }
        /** {@code globalLevelEvent(1038, interiorStart.offset(1, 0, 1), 0)} 의 좌표. */
        public int soundX() { return interiorMinX() + 1; }
        public int soundZ() { return interiorMinZ() + 1; }
    }

    public static boolean hasEye(int state) {
        return (state & EYE) != 0;
    }

    public static int facing(int state) {
        return state & FACING_MASK;
    }

    /** 바닐라 {@code getOrCreatePortalShape().find(level, pos)}. 틀이 없으면 null. */
    public static PortalMatch findPortal(BlockView view, int x, int y, int z) {
        for (int dz = 0; dz < SIZE; dz++) {
            for (int dy = 0; dy < SIZE; dy++) {
                for (int dx = 0; dx < SIZE; dx++) {
                    int ox = x + dx, oy = y + dy, oz = z + dz;
                    for (int[] forwards : DIRECTIONS) {
                        for (int[] up : DIRECTIONS) {
                            if (sameAxis(forwards, up)) continue;
                            if (matches(view, ox, oy, oz, forwards, up)) {
                                return new PortalMatch(ox, oy, oz);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean sameAxis(int[] a, int[] b) {
        return a[0] == b[0] && a[1] == b[1] && a[2] == b[2]
                || a[0] == -b[0] && a[1] == -b[1] && a[2] == -b[2];
    }

    /** {@code BlockPattern#matches} + {@code translateAndRotate}: origin + up·(-row) + (f×up)·column. */
    private static boolean matches(BlockView view, int ox, int oy, int oz, int[] f, int[] up) {
        int cx = f[1] * up[2] - f[2] * up[1];
        int cy = f[2] * up[0] - f[0] * up[2];
        int cz = f[0] * up[1] - f[1] * up[0];
        for (int column = 0; column < SIZE; column++) {
            for (int row = 0; row < SIZE; row++) {
                char symbol = PATTERN[row].charAt(column);
                if (symbol == '?') continue;
                int px = ox - up[0] * row + cx * column;
                int py = oy - up[1] * row + cy * column;
                int pz = oz - up[2] * row + cz * column;
                if (view.blockType(px, py, pz) != Blocks.END_PORTAL_FRAME) return false;
                int state = view.state(px, py, pz);
                if (!hasEye(state) || facing(state) != requiredFacing(symbol)) return false;
            }
        }
        return true;
    }

    private static int requiredFacing(char symbol) {
        return switch (symbol) {
            case '^' -> SOUTH;
            case '>' -> WEST;
            case 'v' -> NORTH;
            case '<' -> EAST;
            default -> throw new IllegalStateException("unknown portal symbol " + symbol);
        };
    }
}
