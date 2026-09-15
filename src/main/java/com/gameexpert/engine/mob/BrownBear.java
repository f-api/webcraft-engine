package com.gameexpert.engine.mob;

/**
 * WebCraft territorial brown bear. It can be escaped at the canonical player
 * sprint speed while remaining much faster than ordinary hostile mobs.
 */
public final class BrownBear extends MeleeMob {
    /** 5.2 blocks/second at the authority's 10 TPS, below the player's 5.6 sprint. */
    public static final double PURSUIT_BLOCKS_PER_TICK = 0.52;
    public static final double IDLE_BLOCKS_PER_TICK = 0.12;

    public BrownBear(long id, double x, double y, double z) {
        super(id, MobType.BROWN_BEAR, x, y, z);
    }

    @Override protected double detectRange() { return 20.0; }
    @Override protected double attackRange() { return 2.2; }
    @Override protected int attackDamage() { return 6; }
    @Override protected int attackCooldownTicks() { return 12; }
    @Override protected boolean climbWalls() { return false; }
    @Override protected double moveSpeed() { return IDLE_BLOCKS_PER_TICK; }

    @Override
    protected double[] chaseMovement(MobWorldView world, PlayerSnapshot target, MobRandom rng) {
        return towardHoriz(target.x(), target.z(), PURSUIT_BLOCKS_PER_TICK);
    }
}
