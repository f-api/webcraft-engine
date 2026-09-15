package com.gameexpert.engine;

/** Minecraft Java 1.21.4 플레이어 눈높이와 블록·엔티티 상호작용 거리 정본. */
public final class PlayerInteractionRules {

    public static final double STANDING_EYE_HEIGHT = 1.62;
    public static final double CROUCHING_EYE_HEIGHT = 1.27;
    public static final double BLOCK_REACH = 4.5;
    public static final double BLOCK_AUTHORITY_PADDING = 1.0;
    public static final double CONTAINER_AUTHORITY_PADDING = 4.0;
    public static final double ENTITY_REACH = 3.0;
    public static final double ENTITY_AUTHORITY_PADDING = 3.0;

    private PlayerInteractionRules() {
    }

    public static double eyeHeight(boolean crouching) {
        return crouching ? CROUCHING_EYE_HEIGHT : STANDING_EYE_HEIGHT;
    }

    /** 서버가 블록 위치 패킷을 검증할 때 사용하는 바닐라 +1.0 허용 오차. */
    public static boolean canInteractWithBlock(double px, double py, double pz,
            boolean crouching, int x, int y, int z) {
        return canReachBlockAabb(
                px, py, pz, crouching, x, y, z, BLOCK_AUTHORITY_PADDING);
    }

    /** 열린 컨테이너의 {@code stillValid}가 사용하는 바닐라 +4.0 허용 오차. */
    public static boolean canUseContainer(double px, double py, double pz,
            boolean crouching, int x, int y, int z) {
        return canReachBlockAabb(
                px, py, pz, crouching, x, y, z, CONTAINER_AUTHORITY_PADDING);
    }

    private static boolean canReachBlockAabb(double px, double py, double pz,
            boolean crouching, int x, int y, int z, double padding) {
        double reach = BLOCK_REACH + padding;
        return distanceSquaredToAabb(px, py + eyeHeight(crouching), pz,
                x, y, z, x + 1.0, y + 1.0, z + 1.0) < reach * reach;
    }

    /** 서버가 엔티티 상호작용 패킷을 검증할 때 사용하는 바닐라 +3.0 허용 오차. */
    public static boolean canInteractWithEntity(double px, double py, double pz,
            boolean crouching, double x, double y, double z, double width, double height) {
        double halfWidth = width / 2.0;
        double reach = ENTITY_REACH + ENTITY_AUTHORITY_PADDING;
        return distanceSquaredToAabb(px, py + eyeHeight(crouching), pz,
                x - halfWidth, y, z - halfWidth,
                x + halfWidth, y + height, z + halfWidth) < reach * reach;
    }

    static double distanceSquaredToAabb(double px, double py, double pz,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        double dx = px < minX ? minX - px : px > maxX ? px - maxX : 0.0;
        double dy = py < minY ? minY - py : py > maxY ? py - maxY : 0.0;
        double dz = pz < minZ ? minZ - pz : pz > maxZ ? pz - maxZ : 0.0;
        return dx * dx + dy * dy + dz * dz;
    }
}
