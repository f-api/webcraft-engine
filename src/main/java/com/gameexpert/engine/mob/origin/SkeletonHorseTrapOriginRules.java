package com.gameexpert.engine.mob.origin;

import com.gameexpert.engine.mob.MobType;
import com.gameexpert.engine.mob.SkeletonHorse;

/** Pure lightning-origin gate for skeleton-horse traps. */
public final class SkeletonHorseTrapOriginRules {

    private static final int CHANCE_BOUND = 100;
    private static final long SALT = 0x534b454c54524150L;

    private SkeletonHorseTrapOriginRules() {
    }

    /**
     * Returns a spawn plan only for an actual natural lightning strike during a thunderstorm.
     * Conducted, command/debug, visual-only, obstructed, and duplicate-origin events are rejected.
     */
    public static TrapPlan plan(int worldSeed, int x, int y, int z,
            boolean thundering, boolean naturalLightning, boolean skyVisible,
            boolean originAlreadyClaimed) {
        if (!thundering || !naturalLightning || !skyVisible || originAlreadyClaimed) return null;
        long originKey = originKey(worldSeed, x, y, z);
        if (Math.floorMod(mix64(originKey), CHANCE_BOUND) >= SkeletonHorse.TRAP_CHANCE_PERCENT) {
            return null;
        }
        return new TrapPlan(originKey, x, y, z);
    }

    public static long originKey(int worldSeed, int x, int y, int z) {
        long hash = mix64(((long) worldSeed << 32) ^ SALT);
        hash = mix64(hash ^ x);
        hash = mix64(hash ^ y);
        return mix64(hash ^ z);
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ value >>> 31;
    }

    public static final class TrapPlan {
        private final long originKey;
        private final int x;
        private final int y;
        private final int z;

        private TrapPlan(long originKey, int x, int y, int z) {
            this.originKey = originKey;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public long originKey() { return originKey; }
        public MobType mobType() { return MobType.SKELETON_HORSE; }
        public double spawnX() { return x + 0.5; }
        public double spawnY() { return y; }
        public double spawnZ() { return z + 0.5; }
        public int triggerRange() { return (int) SkeletonHorse.TRAP_TRIGGER_RANGE; }
        public int despawnTicks() { return SkeletonHorse.TRAP_DESPAWN_TICKS; }
        public int riderCount() { return SkeletonHorse.TRAP_RIDER_COUNT; }
    }
}
