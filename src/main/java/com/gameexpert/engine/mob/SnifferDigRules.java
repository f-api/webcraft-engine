package com.gameexpert.engine.mob;

import com.gameexpert.terrain.Blocks;

/** Minecraft Java 1.21.4 Sniffer dig activity facts on the 20 TPS MC clock. */
public final class SnifferDigRules {
    private SnifferDigRules() { }

    public enum Phase { IDLE, SCENTING, SNIFFING, SEARCHING, DIGGING, RISING }

    public static final int COOLDOWN_MC_TICKS = 9_600;
    public static final int SCENTING_MIN_MC_TICKS = 40;
    public static final int SCENTING_MAX_MC_TICKS = 80;
    public static final int SNIFFING_MIN_MC_TICKS = 40;
    public static final int SNIFFING_MAX_MC_TICKS = 80;
    public static final int SEARCH_MAX_MC_TICKS = 600;
    public static final int DIGGING_MIN_MC_TICKS = 160;
    public static final int DIGGING_MAX_MC_TICKS = 180;
    /** Sniffer.onDiggingStart schedules its loot-table roll at game tick +120. */
    public static final int DIG_DROP_DELAY_MC_TICKS = 120;
    public static final int RISING_MC_TICKS = 40;
    public static final int MC_TICKS_PER_AUTHORITY_TICK = 2;
    public static final int SEARCH_BASE_RADIUS = 10;
    public static final int SEARCH_VERTICAL_RADIUS = 3;
    public static final int SEARCH_ATTEMPTS = 5;
    public static final double SEARCH_SPEED_MODIFIER = 1.25;
    public static final double TARGET_REACHED_DISTANCE = 1.5;

    public static boolean diggable(int blockId) {
        return blockId == Blocks.DIRT || blockId == Blocks.GRASS || blockId == Blocks.PODZOL
                || blockId == Blocks.COARSE_DIRT || blockId == Blocks.ROOTED_DIRT
                || blockId == Blocks.MOSS_BLOCK || blockId == Blocks.PALE_MOSS_BLOCK
                || blockId == Blocks.MUD
                || blockId == Blocks.MUDDY_MANGROVE_ROOTS;
    }

    public static int advance(int mcTicks) {
        return Math.max(0, mcTicks - MC_TICKS_PER_AUTHORITY_TICK);
    }

    public static int sampleInclusive(MobRandom rng, int minimum, int maximum) {
        return minimum + rng.nextInt(maximum - minimum + 1);
    }

    /** Coordinate-stable candidate lane; failed searches consume no loot RNG. */
    public static int offset(long mobId, long searchEpoch, int attempt, int salt, int radius) {
        long mixed = mobId * 0x9E3779B97F4A7C15L ^ searchEpoch * 0xC2B2AE3D27D4EB4FL
                ^ (long) attempt * 0x165667B19E3779F9L ^ salt * 0x85EBCA77C2B2AE63L;
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        return Math.floorMod((int) (mixed ^ mixed >>> 32), radius * 2 + 1) - radius;
    }
}
