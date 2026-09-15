package com.gameexpert.engine.mob.origin;

import com.gameexpert.engine.mob.MobType;

/** Pure village-cat eligibility and local-cap facts. */
public final class CatVillageOriginRules {

    public static final int MIN_OCCUPIED_BEDS = 5;
    public static final int LOCAL_CAT_CAP = 5;
    public static final int LOCAL_HORIZONTAL_RADIUS = 48;
    public static final int LOCAL_VERTICAL_RADIUS = 8;
    public static final int ATTEMPT_INTERVAL_TICKS = 1_200;

    private CatVillageOriginRules() {
    }

    public static boolean attemptDue(long tickNo) {
        return Math.floorMod(tickNo, ATTEMPT_INTERVAL_TICKS) == 0;
    }

    /**
     * The accepted-village fact is mandatory: POI counts near a rejected/candidate site cannot
     * create a cat. Cat spawning is separate from biome natural-spawn category caps.
     */
    public static SpawnPlan plan(boolean acceptedVillage, int occupiedBeds,
            int nearbyCats, boolean validSpawnPosition, int x, int y, int z) {
        if (!acceptedVillage || occupiedBeds < MIN_OCCUPIED_BEDS
                || nearbyCats < 0 || nearbyCats >= LOCAL_CAT_CAP || !validSpawnPosition) {
            return null;
        }
        return new SpawnPlan(x, y, z, LOCAL_CAT_CAP - nearbyCats);
    }

    public static final class SpawnPlan {
        private final int x;
        private final int y;
        private final int z;
        private final int remainingCapacity;

        private SpawnPlan(int x, int y, int z, int remainingCapacity) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.remainingCapacity = remainingCapacity;
        }

        public MobType mobType() { return MobType.CAT; }
        public int count() { return 1; }
        public int remainingCapacity() { return remainingCapacity; }
        public double spawnX() { return x + 0.5; }
        public double spawnY() { return y; }
        public double spawnZ() { return z + 0.5; }
    }
}
