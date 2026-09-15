package com.gameexpert.engine.structure;

import com.gameexpert.engine.BlockFamilies;
import com.gameexpert.engine.Fluids;
import com.gameexpert.terrain.Blocks;

/** Shared surface witness rules for runtime structures. */
public final class StructureTerrainRules {

    private StructureTerrainRules() {
    }

    public static boolean isStableGround(int block) {
        return block != Blocks.AIR && !Fluids.isFluid(block) && !isVegetation(block);
    }

    /** Soft natural overlays may be cleared; trunks and canopies reject the complete structure site. */
    public static boolean isReplaceableByStructure(int block) {
        return block == Blocks.AIR || isVegetation(block) && !isTreeBlock(block);
    }

    public static boolean isVegetation(int block) {
        return Fluids.isDecoration(block)
                // Kelp stems and both tall-seagrass halves contain water, never a foundation.
                || Fluids.isSubmergedDecoration(block)
                || block == Blocks.CACTUS || block == Blocks.LILY_PAD
                || block == Blocks.KELP || block == Blocks.SEAGRASS
                || block == Blocks.CORAL || block == Blocks.SEA_PICKLE
                || block == Blocks.BAMBOO || block == Blocks.VINE
                || block == Blocks.FERN || block == Blocks.BUSH
                || block == Blocks.MOSS_CARPET || block == Blocks.GLOW_LICHEN
                || block == Blocks.HANGING_ROOTS || block == Blocks.SPORE_BLOSSOM
                || block == Blocks.SMALL_DRIPLEAF || block == Blocks.BIG_DRIPLEAF
                || block == Blocks.AZALEA || block == Blocks.FLOWERING_AZALEA
                || block == Blocks.CARROT_CROP || block == Blocks.POTATO_CROP
                || block == Blocks.BEETROOT_CROP || block == Blocks.PUMPKIN_STEM
                || block == Blocks.OAK_SAPLING || block == Blocks.BIRCH_SAPLING
                || block == Blocks.PUMPKIN || block == Blocks.CARVED_PUMPKIN
                || block == Blocks.JACK_O_LANTERN || block == Blocks.COBWEB
                || block == Blocks.SNOW || block == Blocks.POWDER_SNOW
                || isTreeBlock(block);
    }

    public static boolean isTreeBlock(int block) {
        return BlockFamilies.isWoodLog(block) || block == Blocks.MUSHROOM_STEM
                || block == Blocks.BROWN_MUSHROOM_BLOCK
                || block == Blocks.RED_MUSHROOM_BLOCK || BlockFamilies.isLeaves(block);
    }

    public static boolean isLeaves(int block) {
        return BlockFamilies.isLeaves(block);
    }
}
