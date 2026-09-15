package com.gameexpert.engine;

import com.gameexpert.terrain.Blocks;

/** Snapshot 9 Cushion survival uses suffocation, not arbitrary collision overlap. */
final class CushionSuffocationRules {
    private CushionSuffocationRules() {}

    static boolean suffocates(int block, int state) {
        // Vanilla explicit ALWAYS overrides also cover these partial collision shapes.
        if (block == Blocks.FARMLAND || block == Blocks.DIRT_PATH
                || block == Blocks.SOUL_SAND || block == Blocks.MUD) return true;
        if (BlockFamilies.isLeaves(block) || Blocks.isGlassBlock(block)
                || block == Blocks.TINTED_GLASS || block == Blocks.MANGROVE_ROOTS
                || Blocks.isShulkerBox(block)
                || block >= Blocks.COPPER_GRATE && block <= Blocks.OXIDIZED_COPPER_GRATE
                || block >= Blocks.WAXED_COPPER_GRATE && block <= Blocks.WAXED_OXIDIZED_COPPER_GRATE) return false;
        return BuildingBlockRules.isFullCollisionShape(block, state);
    }
}
