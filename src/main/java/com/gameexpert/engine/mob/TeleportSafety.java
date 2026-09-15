package com.gameexpert.engine.mob;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.engine.inventory.CauldronRules;
import com.gameexpert.engine.validation.MovementLimits;

/** Snapshot 9 teleport destination tags, resolved to this runtime's block/state IDs. */
public final class TeleportSafety {
    private TeleportSafety() {}
    public static boolean withinBorder(int x, int z) {
        double limit = MovementLimits.MAX_HORIZONTAL_COORDINATE;
        return x >= -limit && x + 1 <= limit && z >= -limit && z + 1 <= limit;
    }
    public static boolean dangerous(int block, int state) {
        return block == Blocks.FIRE || block == Blocks.CAMPFIRE || block == Blocks.CACTUS
                || block == Blocks.MAGMA || block == Blocks.SWEET_BERRY_BUSH
                || block == Blocks.POINTED_DRIPSTONE || block == Blocks.POWDER_SNOW
                || block == Blocks.CAULDRON && CauldronRules.kind(state) == CauldronRules.LAVA;
    }
    public static boolean endermanAvoids(MobWorldView world, int x, int y, int z) {
        int block = world.getBlock(x, y, z) & 0xffff;
        return block == Blocks.BEDROCK || world.waterAt(x, y, z)
                || dangerous(block, world.blockState(x, y, z, block));
    }
}
