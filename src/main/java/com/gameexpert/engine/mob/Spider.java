package com.gameexpert.engine.mob;

/** 거미: 밝기 기반 적대, LeapAtTargetGoal, 벽 등반. */
public class Spider extends MeleeMob {
    /** Overworld ambient-light 0 maps the official magic-light 0.5 boundary to raw light 12. */
    private static final int BRIGHTNESS_HOSTILE_THRESHOLD = 12;
    private static final int VIRTUAL_GAME_TICKS = 2;
    private static final double LEAP_MIN_DISTANCE_SQUARED = 4.0;
    private static final double LEAP_MAX_DISTANCE_SQUARED = 16.0;
    private static final double LEAP_HORIZONTAL = 0.4;
    private static final double LEAP_VERTICAL = 0.4;
    private boolean leaping;
    private double leapX;
    private double leapZ;

    public Spider(long id, double x, double y, double z) {
        this(id, MobType.SPIDER, x, y, z);
    }

    protected Spider(long id, MobType type, double x, double y, double z) {
        super(id, type, x, y, z);
    }

    @Override protected double detectRange() { return 16.0; }
    @Override protected double attackRange() { return 1.5; }
    @Override protected int attackDamage() { return 2; }
    @Override protected int attackCooldownTicks() { return 10; }
    @Override protected boolean climbWalls() { return true; }
    @Override protected boolean hostile(MobWorldView world) {
        return localBrightness(world) < BRIGHTNESS_HOSTILE_THRESHOLD;
    }

    @Override protected boolean abandonTrackedTarget(MobWorldView world, MobRandom rng) {
        if (localBrightness(world) < BRIGHTNESS_HOSTILE_THRESHOLD) return false;
        for (int gameTick = 0; gameTick < VIRTUAL_GAME_TICKS; gameTick++) {
            if (rng.nextInt(100) == 0) return true;
        }
        return false;
    }

    @Override protected double[] chaseMovement(MobWorldView world, PlayerSnapshot target,
                                                MobRandom rng) {
        if (leaping) {
            if (!onGround) {
                double[] movement = towardHoriz(x + leapX, z + leapZ, Math.hypot(leapX, leapZ));
                movement[0] = leapX;
                movement[1] = leapZ;
                return movement;
            }
            leaping = false;
        }

        double dx = target.x() - x;
        double dy = target.y() - y;
        double dz = target.z() - z;
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (onGround && distanceSquared >= LEAP_MIN_DISTANCE_SQUARED
                && distanceSquared <= LEAP_MAX_DISTANCE_SQUARED
                && succeedsVirtualLeapRoll(rng)) {
            double length = Math.hypot(dx, dz);
            double directionX = length > 1e-9 ? dx / length : 0;
            double directionZ = length > 1e-9 ? dz / length : 0;
            leapX = directionX * LEAP_HORIZONTAL + horizontalVx * 0.2;
            leapZ = directionZ * LEAP_HORIZONTAL + horizontalVz * 0.2;
            // MobPhysics applies gravity before movement; add it here so this tick moves up by 0.4.
            vy = LEAP_VERTICAL + MobPhysics.GRAVITY;
            leaping = true;
            double[] movement = towardHoriz(x + leapX, z + leapZ, Math.hypot(leapX, leapZ));
            movement[0] = leapX;
            movement[1] = leapZ;
            return movement;
        }
        return super.chaseMovement(world, target, rng);
    }

    private boolean succeedsVirtualLeapRoll(MobRandom rng) {
        for (int gameTick = 0; gameTick < VIRTUAL_GAME_TICKS; gameTick++) {
            if (rng.nextInt(5) == 0) return true;
        }
        return false;
    }

    private int localBrightness(MobWorldView world) {
        return world.lightLevel((int) Math.floor(x), (int) Math.floor(y + eyeHeight()),
                (int) Math.floor(z));
    }
}
