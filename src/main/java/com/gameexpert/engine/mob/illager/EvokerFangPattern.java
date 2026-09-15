package com.gameexpert.engine.mob.illager;

/**
 * Pure Minecraft 1.21.4 evoker-fang placement geometry.
 *
 * <p>The returned array is ordered by the stable zero-based ordinal carried by
 * each fang. This seam only computes geometry; it does not inspect or mutate
 * any world state.
 */
public final class EvokerFangPattern {

    private static final double CLOSE_DISTANCE_SQUARED = 9.0;
    private static final int CLOSE_INNER_COUNT = 5;
    private static final int CLOSE_OUTER_COUNT = 8;
    private static final double INNER_RADIUS = 1.5;
    private static final double OUTER_RADIUS = 2.5;
    private static final double FAR_RADIUS_STEP = 1.25;
    private static final double OUTER_ANGLE_OFFSET = 1.2566371;

    private EvokerFangPattern() {
    }

    /**
     * Computes the ordered fangs for one evoker target position.
     *
     * @param evokerX evoker horizontal X coordinate
     * @param evokerZ evoker horizontal Z coordinate
     * @param targetX target horizontal X coordinate
     * @param targetZ target horizontal Z coordinate
     * @return a bounded array of 13 close-range or 16 far-range immutable fangs
     */
    public static Fang[] generate(double evokerX, double evokerZ,
                                  double targetX, double targetZ) {
        double dx = targetX - evokerX;
        double dz = targetZ - evokerZ;
        double baseAngle = Math.atan2(dz, dx);
        double distanceSquared = dx * dx + dz * dz;

        if (distanceSquared < CLOSE_DISTANCE_SQUARED) {
            Fang[] fangs = new Fang[CLOSE_INNER_COUNT + CLOSE_OUTER_COUNT];
            int ordinal = 0;
            for (int index = 0; index < CLOSE_INNER_COUNT; index++) {
                double angle = baseAngle + index * Math.PI * 0.4;
                fangs[ordinal] = fang(evokerX, evokerZ, INNER_RADIUS, angle, 0, ordinal);
                ordinal++;
            }
            for (int index = 0; index < CLOSE_OUTER_COUNT; index++) {
                double angle = baseAngle + index * 2.0 * Math.PI / 8.0 + OUTER_ANGLE_OFFSET;
                fangs[ordinal] = fang(evokerX, evokerZ, OUTER_RADIUS, angle, 3, ordinal);
                ordinal++;
            }
            return fangs;
        }

        Fang[] fangs = new Fang[16];
        for (int index = 0; index < fangs.length; index++) {
            double angle = baseAngle;
            double radius = FAR_RADIUS_STEP * (index + 1);
            fangs[index] = fang(evokerX, evokerZ, radius, angle, index, index);
        }
        return fangs;
    }

    private static Fang fang(double evokerX, double evokerZ, double radius,
                             double angle, int warmup, int ordinal) {
        return new Fang(
                evokerX + radius * Math.cos(angle),
                evokerZ + radius * Math.sin(angle),
                angle,
                warmup,
                ordinal);
    }

    /** Immutable geometry and timing facts for one fang. */
    public static final class Fang {
        private final double x;
        private final double z;
        private final double angle;
        private final int warmup;
        private final int ordinal;

        private Fang(double x, double z, double angle, int warmup, int ordinal) {
            this.x = x;
            this.z = z;
            this.angle = angle;
            this.warmup = warmup;
            this.ordinal = ordinal;
        }

        public double getX() {
            return x;
        }

        public double getZ() {
            return z;
        }

        public double getAngle() {
            return angle;
        }

        public int getWarmup() {
            return warmup;
        }

        public int getOrdinal() {
            return ordinal;
        }
    }
}
