package com.gameexpert.engine.mob;

import com.gameexpert.engine.Difficulty;

/** Deterministic authority rules for WebCraft's original poison dart frog. */
public final class PoisonDartFrogRules {
    public static final double WIDTH = 0.45;
    public static final double HEIGHT = 0.45;
    public static final int MAX_HEALTH = 8;
    public static final double BASE_SPEED = 1.1;
    public static final int HOST_SEARCH_RADIUS = 12;
    public static final double HOST_REACH_SQUARED = 0.85 * 0.85;
    public static final int EAT_ACTION_TICKS = 12;
    public static final int POISON_ACTION_TICKS = 8;
    public static final int DEFENSIVE_WINDOW_TICKS = 20;
    public static final int CONTACT_COOLDOWN_TICKS = 20;
    public static final int FEED_COOLDOWN_MC_TICKS = 800;
    public static final String[] VARIANTS = {"azure", "golden", "strawberry", "mint"};

    private PoisonDartFrogRules() { }

    public static final class PoisonPlan {
        private final double damage;
        private final int amplifier;
        private final int durationTicks;

        public PoisonPlan(double damage, int amplifier, int durationTicks) {
            this.damage = damage;
            this.amplifier = amplifier;
            this.durationTicks = durationTicks;
        }

        public double damage() { return damage; }
        public int amplifier() { return amplifier; }
        public int durationTicks() { return durationTicks; }
    }

    public static boolean spawnBiome(int biome) {
        return biome == 6 || biome == 184;
    }

    public static boolean validSpawnHabitat(int biome, boolean solidGround,
            boolean bodyClear, boolean adjacentWater) {
        return spawnBiome(biome) && solidGround && bodyClear && adjacentWater;
    }

    /** Coordinate hash only: adding the species never shifts the shared spawn RNG stream. */
    public static String variant(int worldSeed, long mobId, double x, double z) {
        long mixed = Integer.toUnsignedLong(worldSeed) * 0x9e3779b97f4a7c15L
                ^ mobId * 0x632be59bd9b4e019L
                ^ (long) Math.floor(x) * 0x94d049bb133111ebL
                ^ (long) Math.floor(z) * 0xbf58476d1ce4e5b9L;
        mixed ^= mixed >>> 30;
        mixed *= 0xbf58476d1ce4e5b9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94d049bb133111ebL;
        mixed ^= mixed >>> 31;
        return VARIANTS[(int) Math.floorMod(mixed, VARIANTS.length)];
    }

    public static PoisonPlan defensiveContact(Difficulty difficulty,
            boolean defensiveWindow, boolean touching, boolean cooldownReady) {
        if (difficulty == null || !defensiveWindow || !touching || !cooldownReady) return null;
        return switch (difficulty) {
            case EASY -> new PoisonPlan(1.0, 0, 60);
            case NORMAL -> new PoisonPlan(1.0, 0, 100);
            case HARD -> new PoisonPlan(2.0, 1, 160);
        };
    }

    /** Stable nearest-host ordering: squared distance, then x, y, z. */
    public static int compareHost(double frogX, double frogY, double frogZ,
            int ax, int ay, int az, int bx, int by, int bz) {
        double ad = distanceSquared(frogX, frogY, frogZ, ax, ay, az);
        double bd = distanceSquared(frogX, frogY, frogZ, bx, by, bz);
        int distance = Double.compare(ad, bd);
        if (distance != 0) return distance;
        int x = Integer.compare(ax, bx);
        if (x != 0) return x;
        int y = Integer.compare(ay, by);
        return y != 0 ? y : Integer.compare(az, bz);
    }

    public static boolean hostReached(double frogX, double frogY, double frogZ,
            int hostX, int hostY, int hostZ) {
        return distanceSquared(frogX, frogY, frogZ, hostX, hostY, hostZ)
                <= HOST_REACH_SQUARED;
    }

    private static double distanceSquared(double frogX, double frogY, double frogZ,
            int hostX, int hostY, int hostZ) {
        double dx = frogX - (hostX + 0.5);
        double dy = frogY - hostY;
        double dz = frogZ - (hostZ + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }
}
