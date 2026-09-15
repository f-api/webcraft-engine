package com.gameexpert.engine.mob;

import com.gameexpert.terrain.Blocks;

/** Exact committed result of an ordinary Frog consuming a small Sulfur Cube. */
public final class FrogSulfurCubeRules {
    public static final double CONSUME_RANGE_SQUARED = 1.5 * 1.5;

    private FrogSulfurCubeRules() { }

    public static int froglightForVariant(String frogVariant) {
        if ("cold".equals(frogVariant)) return Blocks.VERDANT_FROGLIGHT;
        if ("warm".equals(frogVariant)) return Blocks.PEARLESCENT_FROGLIGHT;
        return Blocks.OCHRE_FROGLIGHT;
    }

    /**
     * {@code Frog#canEat} 는 {@code AbstractCubeMob#getSize() == 1} 인 큐브만 먹는다 —
     * 새끼 판정이 아니라 <b>크기</b> 판정이다(유황 큐브는 크기 1 ⟺ 새끼).
     */
    public static boolean canConsume(Mob frog, Mob cube) {
        if (frog == null || !(cube instanceof SulfurCube sulfurCube)
                || frog.type != MobType.FROG
                || sulfurCube.size() != SulfurCubeRules.MIN_SIZE
                || frog.isBaby() || frog.isDead() || frog.removed
                || cube.isDead() || cube.removed) return false;
        double dx = frog.x - cube.x;
        double dy = frog.y - cube.y;
        double dz = frog.z - cube.z;
        return dx * dx + dy * dy + dz * dz <= CONSUME_RANGE_SQUARED;
    }
}
