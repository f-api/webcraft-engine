package com.gameexpert.engine.dragon;

import com.gameexpert.terrain.Blocks;

/**
 * [DRAGON] 드래곤 몸통의 블록 판정({@code EnderDragon.checkWalls}): 핀 26.3 태그
 * {@code data/minecraft/tags/block/dragon_transparent.json}(light · #fire) 과 {@code dragon_immune.json}(barrier ·
 * bedrock · end_portal · end_portal_frame · end_gateway · 명령/구조/직소 블록 · moving_piston · obsidian ·
 * crying_obsidian · end_stone · iron_bars · respawn_anchor · reinforced_deepslate · test 블록). 이 저장소에 있는
 * 블록만 옮긴다(나머지는 등록되지 않았다). 정적판 {@code DragonBlockRules.ts} 와 같은 목록이다.
 */
public final class DragonBlockRules {
    private DragonBlockRules() {
    }

    /** {@code BlockTags.DRAGON_TRANSPARENT}: 몸통이 지나가도 막히지도 부수지도 않는다. */
    public static boolean dragonTransparent(int block) {
        return block == Blocks.FIRE;
    }

    /** {@code BlockTags.DRAGON_IMMUNE}: 몸통을 막고 부서지지 않는다. */
    public static boolean dragonImmune(int block) {
        return block == Blocks.BARRIER || block == Blocks.BEDROCK || block == Blocks.END_PORTAL
                || block == Blocks.END_PORTAL_FRAME || block == Blocks.END_GATEWAY || block == Blocks.OBSIDIAN
                || block == Blocks.CRYING_OBSIDIAN || block == Blocks.END_STONE || block == Blocks.IRON_BARS;
    }
}
