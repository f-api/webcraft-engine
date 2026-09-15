package com.gameexpert.engine.mob;

/**
 * [EC-MOBS] 아이템 액자(mob 105 {@code item_frame}, 아이템 2303)의 <b>상태 없는</b> 수치 정본.
 *
 * <p>근거는 핀 26.3-snapshot-7 jar 의 바이트코드다.
 * <ul>
 *   <li>{@code ItemFrame}: DEPTH 0.0625 · WIDTH 0.75 · HEIGHT 0.75 · NUM_ROTATIONS 8 ·
 *       {@code createBoundingBox} = {@code AABB.ofSize(Vec3.atCenterOf(pos).relative(dir, -0.46875),
 *       …)} (축 방향 두께 0.0625, 나머지 두 축 0.75) · {@code survives}(지지 칸 {@code isSolid} 또는
 *       수평일 때 다이오드, 그리고 {@code canCoexist(true)}) · {@code interact} · {@code hurtServer} ·
 *       {@code dropItem} · {@code getAnalogOutput} · {@code getVisualRotationYInDegrees}.</li>
 *   <li>{@code HangingEntity}: {@code recalculateBoundingBox} 가 개체 위치를 상자 <b>중심</b>에
 *       둔다({@code setPosRaw(center)}) · {@code canCoexist} 람다(자기 아닌 같은 종 또는 같은 방향)
 *       · {@code spawnAtLocation} 이 방향 수평 성분 × 0.15 만큼 앞에서 떨군다.</li>
 *   <li>{@code BlockAttachedEntity}: {@code CHECK_INTERVAL = 100}, {@code ticksSinceLastCheck++ >= 100}
 *       이면 0 으로 되감고 {@code survives()} 가 거짓이면 스스로 떨어진다.</li>
 *   <li>{@code HangingEntityItem.useOn} / {@code ItemFrameItem.mayPlace}: 클릭한 칸의 클릭한 면 바깥
 *       칸에 {@code new ItemFrame(level, pos.relative(face), face)} 를 세우고, {@code survives()} 일
 *       때만 설치음과 함께 추가하고 스택을 하나 줄인다(창작 모드는 줄지 않는다).</li>
 * </ul>
 *
 * <p><b>방향 부호</b>는 바닐라 {@code Direction.get3DDataValue} 다: DOWN 0 · UP 1 · NORTH 2 ·
 * SOUTH 3 · WEST 4 · EAST 5. 액자는 이 방향을 <b>바라본다</b> — 지지 블록은 반대편
 * ({@code pos.relative(dir.getOpposite())})이다.
 *
 * <p><b>위치 계약</b>: 권위 몹의 {@code x,y,z} 는 다른 몹처럼 발밑이 아니라 바닐라 그대로 상자
 * 중심이다. 칸 좌표는 {@code floor(center + step·0.46875)} 로 정확히 되돌아온다(0.46875 = 15/32 는
 * 이진 표현이 정확하다). 정적판 짝은 {@code StandaloneItemFrameRules.ts} 이며 두 권위의 값은
 * 테스트가 대조한다.
 */
public final class ItemFrameRules {

    private ItemFrameRules() {
    }

    public static final int DOWN = 0;
    public static final int UP = 1;
    public static final int NORTH = 2;
    public static final int SOUTH = 3;
    public static final int WEST = 4;
    public static final int EAST = 5;

    private static final int[] STEP_X = {0, 0, 0, 0, -1, 1};
    private static final int[] STEP_Y = {-1, 1, 0, 0, 0, 0};
    private static final int[] STEP_Z = {0, 0, -1, 1, 0, 0};
    /** {@code Direction.get2DDataValue}: SOUTH 0 · WEST 1 · NORTH 2 · EAST 3, 수직은 -1. */
    private static final int[] DATA_2D = {-1, -1, 2, 0, 1, 3};

    /** {@code ItemFrame.DEPTH}. */
    public static final double DEPTH = 0.0625;
    /** {@code ItemFrame.WIDTH}/{@code HEIGHT}(지도가 없을 때). */
    public static final double SIZE = 0.75;
    /** {@code createBoundingBox} 의 {@code Vec3.relative(dir, -0.46875)}. */
    public static final double CENTER_OFFSET = 0.46875;
    /** {@code ItemFrame.NUM_ROTATIONS}. */
    public static final int NUM_ROTATIONS = 8;
    /** {@code BlockAttachedEntity.CHECK_INTERVAL}(MC 틱). */
    public static final int CHECK_INTERVAL_MC_TICKS = 100;
    /** {@code HangingEntity.spawnAtLocation} 의 {@code getStepX() * 0.15F}(float). */
    public static final double DROP_FORWARD_OFFSET = 0.15f;

    public static boolean validDirection(int direction) {
        return direction >= DOWN && direction <= EAST;
    }

    public static int stepX(int direction) { return STEP_X[direction]; }
    public static int stepY(int direction) { return STEP_Y[direction]; }
    public static int stepZ(int direction) { return STEP_Z[direction]; }

    /** {@code Direction.getOpposite}: 짝(0↔1, 2↔3, 4↔5)이다. */
    public static int opposite(int direction) { return direction ^ 1; }

    public static boolean horizontal(int direction) { return direction >= NORTH; }

    /** 상자 중심 x. {@code Vec3.atCenterOf(pos).relative(dir, -0.46875)}. */
    public static double centerX(int blockX, int direction) {
        return blockX + 0.5 + STEP_X[direction] * -CENTER_OFFSET;
    }

    public static double centerY(int blockY, int direction) {
        return blockY + 0.5 + STEP_Y[direction] * -CENTER_OFFSET;
    }

    public static double centerZ(int blockZ, int direction) {
        return blockZ + 0.5 + STEP_Z[direction] * -CENTER_OFFSET;
    }

    /** 중심 좌표에서 액자가 걸린 칸을 되돌린다(정확한 역함수). */
    public static int blockX(double centerX, int direction) {
        return (int) Math.floor(centerX + STEP_X[direction] * CENTER_OFFSET);
    }

    public static int blockY(double centerY, int direction) {
        return (int) Math.floor(centerY + STEP_Y[direction] * CENTER_OFFSET);
    }

    public static int blockZ(double centerZ, int direction) {
        return (int) Math.floor(centerZ + STEP_Z[direction] * CENTER_OFFSET);
    }

    /**
     * {@code ItemFrame.createBoundingBox(pos, dir, false)} = {@code AABB.ofSize(center, sx, sy, sz)}.
     * 반환은 {@code {minX, minY, minZ, maxX, maxY, maxZ}} 이다.
     */
    public static double[] boundingBox(int blockX, int blockY, int blockZ, int direction) {
        double cx = centerX(blockX, direction);
        double cy = centerY(blockY, direction);
        double cz = centerZ(blockZ, direction);
        double sx = STEP_X[direction] != 0 ? DEPTH : SIZE;
        double sy = STEP_Y[direction] != 0 ? DEPTH : SIZE;
        double sz = STEP_Z[direction] != 0 ? DEPTH : SIZE;
        return new double[] {
            cx - sx / 2.0, cy - sy / 2.0, cz - sz / 2.0,
            cx + sx / 2.0, cy + sy / 2.0, cz + sz / 2.0,
        };
    }

    /** {@code AABB.intersects}: 모든 축에서 열린 구간이 겹친다. */
    public static boolean intersects(double[] a, double[] b) {
        return a[0] < b[3] && a[3] > b[0] && a[1] < b[4] && a[4] > b[1]
                && a[2] < b[5] && a[5] > b[2];
    }

    /** 설치·생존이 보는 권위 월드. 좌표는 모두 블록 칸이다. */
    public interface SupportWorld {
        /** {@code hasLevelCollision(popBox)}: 상자와 겹치는 블록 충돌 형상이 있는가. */
        boolean blockCollision(double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ);

        /** 지지 칸 블록이 바닐라 {@code BlockState.isSolid} 인가. */
        boolean solid(int x, int y, int z);

        /** 지지 칸 블록이 {@code DiodeBlock}(중계기·비교기)인가. */
        boolean diode(int x, int y, int z);
    }

    /** 이미 걸린 액자 하나. {@code canCoexist} 가 읽는 전부다. */
    public record Hanging(long id, int blockX, int blockY, int blockZ, int direction) { }

    /**
     * {@code ItemFrame.survives()}(fixed 아님): 상자에 블록 충돌이 없고, 지지 칸이 고체이거나
     * (수평 방향일 때) 다이오드이며, {@code canCoexist(true)} — 같은 방향으로 걸린 다른 액자의
     * 상자와 겹치지 않는다.
     */
    public static boolean survives(SupportWorld world, long selfId, int blockX, int blockY,
            int blockZ, int direction, Iterable<Hanging> others) {
        if (!validDirection(direction)) return false;
        double[] box = boundingBox(blockX, blockY, blockZ, direction);
        if (world.blockCollision(box[0], box[1], box[2], box[3], box[4], box[5])) return false;
        int sx = blockX - STEP_X[direction];
        int sy = blockY - STEP_Y[direction];
        int sz = blockZ - STEP_Z[direction];
        if (!world.solid(sx, sy, sz) && !(horizontal(direction) && world.diode(sx, sy, sz))) {
            return false;
        }
        for (Hanging other : others) {
            if (other.id() == selfId || other.direction() != direction) continue;
            if (intersects(box, boundingBox(other.blockX(), other.blockY(), other.blockZ(),
                    other.direction()))) {
                return false;
            }
        }
        return true;
    }

    /** {@code setRotation(r)} 의 {@code r % 8}. 우클릭 회전은 {@code getRotation() + 1} 이다. */
    public static int nextRotation(int rotation) {
        return (rotation + 1) % NUM_ROTATIONS;
    }

    /** {@code ItemFrame.getAnalogOutput}: 비었으면 0, 아니면 {@code rotation % 8 + 1}. */
    public static int analogOutput(boolean empty, int rotation) {
        return empty ? 0 : rotation % NUM_ROTATIONS + 1;
    }

    /** {@code ItemFrame.interact} 의 서버 갈래. */
    public enum Interaction { PASS, INSERT, ROTATE }

    /**
     * 채워진 액자는 손과 무관하게 회전하고, 빈 액자는 빈 손이 아니면 한 개를 넣는다.
     * {@code fixed} 액자는 모두 PASS 다(구조물 액자는 fixed 가 아니다).
     */
    public static Interaction interaction(boolean fixed, boolean frameHasItem, boolean handHasItem) {
        if (fixed) return Interaction.PASS;
        if (frameHasItem) return Interaction.ROTATE;
        return handHasItem ? Interaction.INSERT : Interaction.PASS;
    }

    /** {@code ItemFrame.hurtServer} 의 두 갈래. */
    public enum Hurt { POP_ITEM, BREAK }

    /**
     * 폭발이 아니고 넣은 아이템이 있으면 아이템만 떨구고(액자는 남는다), 그 밖에는 액자째 부서진다
     * ({@code shouldDamageDropItem}).
     */
    public static Hurt hurt(boolean explosion, boolean frameHasItem) {
        return !explosion && frameHasItem ? Hurt.POP_ITEM : Hurt.BREAK;
    }

    /**
     * {@code ItemFrame.dropItem(level, entity, withFrame)} 의 드랍 관문: 창작(무한 재료) 플레이어가
     * 원인이면 액자도 아이템도 떨구지 않는다. {@code dropChance} 는 기본 1.0 이라 아이템은 늘 떨어진다.
     */
    public static boolean drops(boolean creativeCause) {
        return !creativeCause;
    }

    /** {@code HangingEntity.spawnAtLocation} 의 드랍 x. */
    public static double dropX(double centerX, int direction) {
        return centerX + STEP_X[direction] * DROP_FORWARD_OFFSET;
    }

    public static double dropZ(double centerZ, int direction) {
        return centerZ + STEP_Z[direction] * DROP_FORWARD_OFFSET;
    }

    /**
     * {@code ItemFrame.getVisualRotationYInDegrees}:
     * {@code wrapDegrees(180 + get2DDataValue·90 + rotation·45 + (수직이면 90·축부호))}.
     */
    public static int visualRotationYDegrees(int direction, int rotation) {
        int vertical = horizontal(direction) ? 0 : 90 * (direction == UP ? 1 : -1);
        return wrapDegrees(180 + DATA_2D[direction] * 90 + rotation * 45 + vertical);
    }

    /** {@code Mth.wrapDegrees(int)}. */
    static int wrapDegrees(int degrees) {
        int wrapped = degrees % 360;
        if (wrapped >= 180) wrapped -= 360;
        if (wrapped < -180) wrapped += 360;
        return wrapped;
    }

    /** 방향 3 비트 + 회전 3 비트. 프로토콜 {@code MOB_VISUAL_ITEM_FRAME_*} 과 같은 배치다. */
    public static int visualFlags(int direction, int rotation) {
        return (direction & 0x7) | (rotation & 0x7) << 3;
    }
}
