package com.gameexpert.engine.mob;

/**
 * [EC-MOBS] 셜커(mob 104 {@code shulker})의 <b>상태 없는</b> 수치·기하 정본.
 *
 * <p>근거는 핀 26.3-snapshot-7 jar 의 {@code net.minecraft.world.entity.monster.Shulker} 와 내부
 * 목표들, 그리고 {@code EntityTypes.SHULKER}({@code MONSTER · fireImmune · sized(1, 1) ·
 * eyeHeight(0.5)}) 바이트코드다.
 * <ul>
 *   <li>{@code createAttributes}: MAX_HEALTH 30. {@code xpReward = 5}.</li>
 *   <li>{@code COVERED_ARMOR_MODIFIER}: 닫혀 있는 동안(raw peek 0) ARMOR +20(ADD_VALUE).</li>
 *   <li>{@code PEEK_PER_TICK 0.05f} · {@code getPhysicalPeek(p) = 0.5 - sin((0.5 + p)·π)·0.5}
 *       ({@code Mth.sin} 표).</li>
 *   <li>{@code getProgressDeltaAabb}/{@code getProgressAabb}: 단위 상자를 부착면 반대 방향으로
 *       {@code max(a, b)} 만큼 늘리고 {@code 1 + min(a, b)} 만큼 줄인다.</li>
 *   <li>{@code canStayAt}: 칸이 공기이고, 부착면 쪽 이웃 블록이 그 면에서 엔티티가 설 수 있고
 *       ({@code loadedAndEntityCanStandOnFace}), 완전히 열린 상자가 충돌하지 않는다.</li>
 *   <li>{@code findAttachableSurface}: {@code Direction.values()} 순서(DOWN, UP, NORTH, SOUTH, WEST,
 *       EAST)로 처음 머물 수 있는 면.</li>
 *   <li>{@code teleportSomewhere}: 5회, 각 축 {@code randomBetweenInclusive(-8, 8)}(x, y, z 순),
 *       {@code y > minY}·빈 칸·경계 안·단위 칸 무충돌·부착면 존재. 성공하면 peek 0, 대상 해제.</li>
 *   <li>{@code hitByShulkerBullet}: 열린 셜커만, 순간이동에 성공해야 하며, 옛 상자를
 *       8 부풀린 범위의 셜커 수 n 에 대해 {@code nextFloat() < (n-1)/5} 이면 복제하지 않는다.</li>
 * </ul>
 *
 * <p>방향 부호는 {@link ItemFrameRules} 와 같은 {@code Direction.get3DDataValue} 다. 정적판 짝은
 * {@code StandaloneShulkerRules.ts} 이며 두 권위는 같은 난수 소비 순서를 테스트로 대조한다.
 */
public final class ShulkerRules {

    private ShulkerRules() {
    }

    public static final double WIDTH = 1.0;
    public static final double HEIGHT = 1.0;
    public static final double EYE_HEIGHT = 0.5;
    public static final int MAX_HEALTH = 30;
    /** {@code COVERED_ARMOR_MODIFIER} 20.0 ADD_VALUE. */
    public static final double COVERED_ARMOR = 20.0;
    /** {@code PEEK_PER_TICK}(MC 틱당). */
    public static final float PEEK_PER_MC_TICK = 0.05f;
    /** {@code ShulkerPeekGoal.start} 의 {@code setRawPeekAmount(30)}. */
    public static final int PEEK_GOAL_RAW = 30;
    /** {@code ShulkerAttackGoal.start} 의 {@code setRawPeekAmount(100)}. */
    public static final int ATTACK_RAW = 100;
    public static final int MAX_TELEPORT_DISTANCE = 8;
    public static final int TELEPORT_ATTEMPTS = 5;
    public static final double OTHER_SHULKER_SCAN_RADIUS = 8.0;
    /** {@code ShulkerAttackGoal.tick}: {@code distanceToSqr < 400} 이면 쏘고, 아니면 대상을 놓는다. */
    public static final double ATTACK_RANGE_SQR = 400.0;
    /** {@code ShulkerAttackGoal.start} 의 {@code attackTime = 20}(MC 틱, 매 틱 갱신 목표). */
    public static final int ATTACK_START_DELAY_MC_TICKS = 20;
    /** {@code Monster}/{@code Mob.createMobAttributes} FOLLOW_RANGE 기본 16. */
    public static final double FOLLOW_RANGE = 16.0;
    /** {@code AbstractGolem.getAmbientSoundInterval}. */
    public static final int AMBIENT_SOUND_INTERVAL_MC_TICKS = 120;
    /** {@code Shulker.DEFAULT_ATTACH_FACE}. */
    public static final int DEFAULT_ATTACH_FACE = ItemFrameRules.DOWN;

    /** {@code ShulkerAttackGoal.tick}: {@code 20 + nextInt(10)·20/2}(MC 틱). */
    public static int nextAttackDelayMcTicks(MobRandom rng) {
        return 20 + rng.nextInt(10) * 20 / 2;
    }

    /** {@code Mth.sin(double)}: 65536 칸 표, 색인 {@code (long)(x·10430.378350470453) & 65535}. */
    public static float mthSin(double value) {
        int index = (int) ((long) (value * 10430.378350470453D) & 65535L);
        return (float) Math.sin(index * Math.PI * 2.0D / 65536.0D);
    }

    /** {@code Shulker.getPhysicalPeek}(float 연산 그대로). */
    public static float physicalPeek(float peek) {
        return 0.5f - mthSin((double) ((0.5f + peek) * 3.1415927f)) * 0.5f;
    }

    /**
     * {@code updatePeekAmount} 한 MC 틱: {@code current} 를 목표 {@code raw·0.01} 쪽으로 0.05 씩
     * 옮기고 목표를 넘지 않게 자른다({@code Mth.clamp}).
     */
    public static float stepPeek(float current, int rawPeek) {
        float target = rawPeek * 0.01f;
        if (current == target) return current;
        if (current > target) return clamp(current - PEEK_PER_MC_TICK, target, 1.0f);
        return clamp(current + PEEK_PER_MC_TICK, 0.0f, target);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    /**
     * {@code getProgressDeltaAabb(scale=1, dir, a, b, pos)}. {@code dir} 은 상자가 늘어나는 방향
     * (부착면의 반대)이고 {@code (x, y, z)} 는 발밑 중심이다. 반환은 {@code {minX..maxZ}}.
     */
    public static double[] progressDeltaAabb(int dir, float a, float b,
            double x, double y, double z) {
        double minX = -0.5, minY = 0.0, minZ = -0.5, maxX = 0.5, maxY = 1.0, maxZ = 0.5;
        double max = Math.max(a, b);
        double min = Math.min(a, b);
        double ex = ItemFrameRules.stepX(dir) * max;
        double ey = ItemFrameRules.stepY(dir) * max;
        double ez = ItemFrameRules.stepZ(dir) * max;
        // AABB.expandTowards: 음수는 min 을, 양수는 max 를 민다.
        if (ex < 0) minX += ex; else if (ex > 0) maxX += ex;
        if (ey < 0) minY += ey; else if (ey > 0) maxY += ey;
        if (ez < 0) minZ += ez; else if (ez > 0) maxZ += ez;
        double cx = -ItemFrameRules.stepX(dir) * (1.0 + min);
        double cy = -ItemFrameRules.stepY(dir) * (1.0 + min);
        double cz = -ItemFrameRules.stepZ(dir) * (1.0 + min);
        // AABB.contract: 음수는 min 을 당기고, 양수는 max 를 당긴다.
        if (cx < 0) minX -= cx; else if (cx > 0) maxX -= cx;
        if (cy < 0) minY -= cy; else if (cy > 0) maxY -= cy;
        if (cz < 0) minZ -= cz; else if (cz > 0) maxZ -= cz;
        return new double[] {minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z};
    }

    /** {@code getProgressAabb(1, dir, peek, pos)} = {@code getProgressDeltaAabb(1, dir, -1, peek, pos)}. */
    public static double[] progressAabb(int dir, float peek, double x, double y, double z) {
        return progressDeltaAabb(dir, -1.0f, peek, x, y, z);
    }

    /** {@code makeBoundingBox}: 부착면 반대로 {@code getPhysicalPeek(currentPeekAmount)} 만큼. */
    public static double[] boundingBox(int attachFace, float currentPeek,
            double x, double y, double z) {
        return progressAabb(ItemFrameRules.opposite(attachFace), physicalPeek(currentPeek), x, y, z);
    }

    private static double[] deflate(double[] box, double amount) {
        return new double[] {box[0] + amount, box[1] + amount, box[2] + amount,
                box[3] - amount, box[4] - amount, box[5] - amount};
    }

    /** 부착·순간이동 판정이 보는 권위 월드. */
    public interface AttachWorld {
        /** {@code Level.getMinY()}. */
        int minY();

        /** 칸이 공기인가({@code isAir} · {@code isEmptyBlock}). */
        boolean air(int x, int y, int z);

        /**
         * {@code loadedAndEntityCanStandOnFace(pos, entity, face)}: {@code (x, y, z)} 블록이 로드돼
         * 있고 그 블록의 {@code face} 면이 엔티티가 설 수 있는 온전한 면인가.
         */
        boolean sturdyFace(int x, int y, int z, int face);

        /** 월드 경계 안인가. */
        boolean withinBorder(int x, int z);

        default boolean teleportSurfaceAllowed(int x, int y, int z) { return true; }

        /**
         * {@code Level.noCollision(entity, box)} 의 부정: 블록 충돌 형상 또는 단단한 엔티티
         * (다른 살아 있는 셜커)와 겹치는가. {@code selfMobId} 는 제외한다.
         */
        boolean collides(long selfMobId, double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ);
    }

    /** {@code Shulker.canStayAt(pos, face)}. */
    public static boolean canStayAt(AttachWorld world, long selfMobId,
            int x, int y, int z, int face) {
        if (!world.air(x, y, z)) return false;
        int opposite = ItemFrameRules.opposite(face);
        if (!world.sturdyFace(x + ItemFrameRules.stepX(face), y + ItemFrameRules.stepY(face),
                z + ItemFrameRules.stepZ(face), opposite)) {
            return false;
        }
        double[] box = deflate(progressAabb(opposite, 1.0f, x + 0.5, y, z + 0.5), 1.0E-6);
        return !world.collides(selfMobId, box[0], box[1], box[2], box[3], box[4], box[5]);
    }

    /** {@code findAttachableSurface}: 없으면 -1. */
    public static int findAttachableSurface(AttachWorld world, long selfMobId, int x, int y, int z) {
        for (int face = ItemFrameRules.DOWN; face <= ItemFrameRules.EAST; face++) {
            if (canStayAt(world, selfMobId, x, y, z, face)) return face;
        }
        return -1;
    }

    /** 순간이동 결과. 실패면 {@code null}. */
    public record Teleport(int x, int y, int z, int attachFace) { }

    /** {@code Mth.randomBetweenInclusive(r, -8, 8)}. */
    private static int randomOffset(MobRandom rng) {
        return rng.nextInt(MAX_TELEPORT_DISTANCE * 2 + 1) - MAX_TELEPORT_DISTANCE;
    }

    /**
     * {@code teleportSomewhere} 의 탐색부. 호출자는 살아 있고 AI 가 켜진 셜커에서만 부른다.
     * 난수는 시도마다 x, y, z 세 번을 소비한다.
     */
    public static Teleport teleportSearch(AttachWorld world, long selfMobId,
            int blockX, int blockY, int blockZ, MobRandom rng) {
        for (int attempt = 0; attempt < TELEPORT_ATTEMPTS; attempt++) {
            int tx = blockX + randomOffset(rng);
            int ty = blockY + randomOffset(rng);
            int tz = blockZ + randomOffset(rng);
            if (ty <= world.minY() || !world.air(tx, ty, tz) || !world.withinBorder(tx, tz)) {
                continue;
            }
            if (world.collides(selfMobId, tx + 1.0E-6, ty + 1.0E-6, tz + 1.0E-6,
                    tx + 1.0 - 1.0E-6, ty + 1.0 - 1.0E-6, tz + 1.0 - 1.0E-6)) {
                continue;
            }
            int face = findAttachableSurface(world, selfMobId, tx, ty, tz);
            if (face >= 0 && world.teleportSurfaceAllowed(tx + ItemFrameRules.stepX(face),
                    ty + ItemFrameRules.stepY(face), tz + ItemFrameRules.stepZ(face))) {
                return new Teleport(tx, ty, tz, face);
            }
        }
        return null;
    }

    /**
     * {@code hitByShulkerBullet} 의 복제 굴림. {@code nearbyShulkers} 는 옛 상자를 8 부풀린 범위에
     * 있는 살아 있는 셜커 수(순간이동한 자신 포함)다. 복제하면 true.
     */
    public static boolean duplicates(int nearbyShulkers, MobRandom rng) {
        float chance = (nearbyShulkers - 1) / 5.0f;
        return !(rng.nextFloat() < chance);
    }

    /** 닫힘(raw peek 0) 동안의 방어도 추가분. */
    public static double coveredArmor(int rawPeek) {
        return rawPeek == 0 ? COVERED_ARMOR : 0.0;
    }

    /**
     * {@code ShulkerLookControl.getYRotD}: 부착면 반대 축을 위로 삼는 평면 안에서 목표를 향하는
     * 머리 각도(도). 목표가 축 위에 있으면 NaN(갱신 없음). 기저는 {@code Direction.getRotation} 이
     * {@code FORWARD = SOUTH} 를 옮긴 정수 벡터다(float 회전 오차 1e-7 은 버린다).
     */
    public static double lookYawDegrees(int attachFace, double dx, double dy, double dz) {
        int up = ItemFrameRules.opposite(attachFace);
        // forward = getRotation(up)·(0,0,1)
        int fx, fy, fz;
        switch (up) {
            case ItemFrameRules.DOWN -> { fx = 0; fy = 0; fz = -1; }
            case ItemFrameRules.UP -> { fx = 0; fy = 0; fz = 1; }
            default -> { fx = 0; fy = -1; fz = 0; }
        }
        int ux = ItemFrameRules.stepX(up), uy = ItemFrameRules.stepY(up), uz = ItemFrameRules.stepZ(up);
        // side = up × forward
        int sx = uy * fz - uz * fy;
        int sy = uz * fx - ux * fz;
        int sz = ux * fy - uy * fx;
        float side = (float) (sx * (float) dx + sy * (float) dy + sz * (float) dz);
        float forward = (float) (fx * (float) dx + fy * (float) dy + fz * (float) dz);
        if (Math.abs(side) <= 1.0E-5f && Math.abs(forward) <= 1.0E-5f) return Double.NaN;
        return (float) (Math.atan2(-side, forward) * 57.2957763671875D);
    }

    /** 부착면 3 비트 + raw peek 7 비트(0..100). 프로토콜 {@code MOB_VISUAL_SHULKER_*} 배치. */
    public static int visualFlags(int attachFace, int rawPeek) {
        return (attachFace & 0x7) | (Math.max(0, Math.min(100, rawPeek)) & 0x7f) << 3;
    }
}
