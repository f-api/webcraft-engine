package com.gameexpert.engine.mob;

import com.gameexpert.terrain.Blocks;

/** Pure authority rules for the irreversible Rafflesia-colony frog conversion. */
public final class FrogPoisonConversionRules {
    public static final int FIREFLY_HORIZONTAL_RADIUS = 12;
    public static final int FIREFLY_VERTICAL_RADIUS = 3;
    public static final int RAFFLESIA_HORIZONTAL_RADIUS = 4;
    public static final int RAFFLESIA_VERTICAL_RADIUS = 2;
    public static final int MAX_LIGHT = 7;
    public static final long NIGHT_START_MC_TICK = 12_600L;
    public static final long NIGHT_END_MC_TICK = 23_400L;
    public static final long DAY_MC_TICKS = 24_000L;
    public static final long COLONY_COOLDOWN_MC_TICKS = 240_000L;
    public static final double REACH_SQUARED = 0.85 * 0.85;

    private FrogPoisonConversionRules() { }

    public static boolean conversionNight(long authorityWorldTime) {
        long mcTime = Math.floorMod(authorityWorldTime * 2L, DAY_MC_TICKS);
        return mcTime >= NIGHT_START_MC_TICK && mcTime <= NIGHT_END_MC_TICK;
    }

    public static boolean validPair(int rafflesiaBlock, int bushBlock, int bushLight,
            int rx, int ry, int rz, int bx, int by, int bz) {
        return rafflesiaBlock == Blocks.RAFFLESIA && bushBlock == Blocks.FIREFLY_BUSH
                && bushLight >= 0 && bushLight <= MAX_LIGHT
                && Math.abs(rx - bx) <= RAFFLESIA_HORIZONTAL_RADIUS
                && Math.abs(rz - bz) <= RAFFLESIA_HORIZONTAL_RADIUS
                && Math.abs(ry - by) <= RAFFLESIA_VERTICAL_RADIUS;
    }

    public static boolean reached(double frogX, double frogY, double frogZ,
            int bushX, int bushY, int bushZ) {
        return distanceSquared(frogX, frogY, frogZ, bushX, bushY, bushZ) <= REACH_SQUARED;
    }

    /** Stable ordering: distance to host, Rafflesia xyz, then Firefly Bush xyz. */
    public static int compare(double frogX, double frogY, double frogZ,
            int[] candidate, int[] current) {
        return compare(frogX, frogY, frogZ,
                candidate[0], candidate[1], candidate[2], candidate[3], candidate[4], candidate[5],
                current[0], current[1], current[2], current[3], current[4], current[5]);
    }

    public static int compare(double frogX, double frogY, double frogZ,
            int arx, int ary, int arz, int abx, int aby, int abz,
            int brx, int bry, int brz, int bbx, int bby, int bbz) {
        int distance = Double.compare(distanceSquared(frogX, frogY, frogZ,
                abx, aby, abz), distanceSquared(frogX, frogY, frogZ, bbx, bby, bbz));
        if (distance != 0) return distance;
        int coordinate = Integer.compare(arx, brx);
        if (coordinate != 0) return coordinate;
        coordinate = Integer.compare(ary, bry);
        if (coordinate != 0) return coordinate;
        coordinate = Integer.compare(arz, brz);
        if (coordinate != 0) return coordinate;
        coordinate = Integer.compare(abx, bbx);
        if (coordinate != 0) return coordinate;
        coordinate = Integer.compare(aby, bby);
        return coordinate != 0 ? coordinate : Integer.compare(abz, bbz);
    }

    private static double distanceSquared(double x, double y, double z,
            int bx, int by, int bz) {
        double dx = x - (bx + 0.5);
        double dy = y < by ? y - by : y > by + 1.0 ? y - (by + 1.0) : 0.0;
        double dz = z - (bz + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }
}
