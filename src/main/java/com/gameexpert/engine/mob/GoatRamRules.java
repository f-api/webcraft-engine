package com.gameexpert.engine.mob;

import com.gameexpert.engine.BlockFamilies;
import com.gameexpert.terrain.Blocks;

/** Minecraft Java 1.21.4 Goat brain constants, expressed on the 20 TPS MC clock. */
public final class GoatRamRules {
    private GoatRamRules() { }

    public static final int MIN_COOLDOWN_MC_TICKS = 600;
    public static final int MAX_COOLDOWN_MC_TICKS = 6_000;
    public static final int PREPARE_MC_TICKS = 20;
    public static final int MC_TICKS_PER_AUTHORITY_TICK = 2;
    public static final double TARGET_RANGE = 16.0;
    public static final int MIN_RUN_UP_BLOCKS = 4;
    public static final int MAX_RUN_UP_BLOCKS = 7;
    public static final double PREPARE_SPEED_MODIFIER = 1.25;
    public static final double RAM_SPEED_MODIFIER = 3.0;
    public static final double RUN_UP_REACHED_DISTANCE = 0.5;
    public static final double TARGET_MOVE_TOLERANCE = 0.25;
    public static final int IMPACT_DAMAGE = 2;
    public static final double IMPACT_KNOCKBACK = 2.5;

    public enum Phase { IDLE, PREPARING, CHARGING, IMPACT_PENDING }

    public static int sampleCooldown(MobRandom random) {
        return MIN_COOLDOWN_MC_TICKS
                + random.nextInt(MAX_COOLDOWN_MC_TICKS - MIN_COOLDOWN_MC_TICKS + 1);
    }

    public static int advanceMcCursor(int cursor) {
        return Math.max(0, cursor - MC_TICKS_PER_AUTHORITY_TICK);
    }

    /** Vanilla #snaps_goat_horn tag in the registered Overworld content set. */
    public static boolean snapsGoatHorn(int blockId) {
        return blockId == Blocks.STONE || blockId == Blocks.PACKED_ICE
                || blockId == Blocks.COAL_ORE || blockId == Blocks.IRON_ORE || blockId == Blocks.COPPER_ORE
                || blockId == Blocks.EMERALD_ORE || BlockFamilies.isWoodLog(blockId);
    }
}
