package com.gameexpert.engine.mob;

import com.gameexpert.terrain.Blocks;

/**
 * Available-block projection of the vanilla {@code #minecraft:piglin_guarded} tag. Breaking one of
 * these, or opening it as a container, angers every idle piglin within 16 blocks of the player.
 * The vanilla tag also lists barrels, ender/trapped chests, shulker boxes and gilded blackstone;
 * none of those blocks exist in WebCraft, so the projection carries only the present members.
 */
public final class PiglinGuardedBlocks {
    private PiglinGuardedBlocks() {}

    public static boolean isGuarded(int blockType) {
        return blockType == Blocks.GOLD_ORE
                || blockType == Blocks.DEEPSLATE_GOLD_ORE
                || blockType == Blocks.NETHER_GOLD_ORE
                || blockType == Blocks.GOLD_BLOCK
                || blockType == Blocks.RAW_GOLD_BLOCK
                // [CHEST-FAMILY] 피글린은 상자 형상군 전체를 지킨다.
                || Blocks.isChestShaped(blockType);
    }
}
