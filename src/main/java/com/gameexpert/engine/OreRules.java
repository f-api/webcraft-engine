package com.gameexpert.engine;

import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.inventory.InventoryRules;
import com.gameexpert.terrain.Blocks;

/** 신규 광석의 MC 무마법 채굴 수량. 난수는 실제 ItemEntitySystem 채굴 lane에서 한 번 주입된다. */
public final class OreRules {
    private OreRules() {
    }

    public static int minedDropCount(int blockType, double roll) {
        double normalized = Math.max(0.0, Math.min(Math.nextDown(1.0), roll));
        if (blockType == Blocks.LAPIS_ORE) return 4 + (int) (normalized * 6);     // 4..9
        if (blockType == Blocks.REDSTONE_ORE) return 4 + (int) (normalized * 2); // 4..5
        // [PRISMARINE] 바다 랜턴은 바닐라 sea_lantern loot table 의 uniform 2..3 이다.
        if (blockType == Blocks.SEA_LANTERN) return 2 + (int) (normalized * 2);  // 2..3
        if (blockType == Blocks.GLOWSTONE) return 2 + (int) (normalized * 3);   // 2..4
        // [UTILITY] 수박은 바닐라 blocks/melon.json 의 uniform 3..7 수박 조각이다(상한 9 는 아래).
        if (blockType == Blocks.MELON) return 3 + (int) (normalized * 5);       // 3..7
        // [ARCHAEOLOGY] 금 가지 않은 장식 항아리는 자신 하나다. 금 간 항아리의 재료 넷은
        // ItemEntitySystem.spawnMinedBlockDrop 이 ArchaeologyRules.decoratedPotDrop 으로 따로 낸다.
        return 1;
    }

    /**
     * [PRISMARINE] 행운 배율까지 곱한 뒤 적용하는 바닐라 {@code limit_count} 상한.
     * 바다 랜턴만 최대 5개다(sea_lantern.json: uniform 2~3 → ore_drops 보너스 → limit 5).
     * 상한이 없는 블록은 {@link Integer#MAX_VALUE} 를 돌려주어 곱셈 결과를 그대로 통과시킨다.
     */
    public static int dropCountLimit(int blockType) {
        if (blockType == Blocks.SEA_LANTERN) return 5;
        // [UTILITY] blocks/melon.json limit_count max 9.
        if (blockType == Blocks.MELON) return 9;
        return blockType == Blocks.GLOWSTONE ? 4 : Integer.MAX_VALUE;
    }

    /**
     * [SURV-X] 기본 수량은 마스크와 무관하다. 행운 배율은 {@link #fortuneMultiplier} 로 따로 곱한다.
     */
    public static int minedDropCount(int blockType, long enchantments, double roll) {
        return minedDropCount(blockType, roll);
    }

    /**
     * [SURV-X] 광석류 드랍 개수에 곱할 바닐라 ordinary 행운 배율.
     * 섬세한 손길이 함께 있으면 섬세한 손길이 이기므로 배율은 1이다.
     */
    public static int fortuneMultiplier(int blockType, long enchantments, int roll) {
        if (EnchantmentRules.enchantLevel(enchantments, EnchantmentRules.SILK_TOUCH) > 0) return 1;
        int level = EnchantmentRules.enchantLevel(enchantments, EnchantmentRules.FORTUNE);
        if (level <= 0 || !fortuneApplies(blockType)) return 1;
        return EnchantmentRules.fortuneOrdinaryMultiplier(level, roll);
    }

    /** 행운 배율이 붙는 드랍(§3): 석탄·다이아·에메랄드·청금석·레드스톤·구리·네더 금·자수정. */
    public static boolean fortuneApplies(int blockType) {
        if (blockType == Blocks.AMETHYST_CLUSTER) return true;
        // [PRISMARINE] 바다 랜턴도 바닐라에서 apply_bonus ore_drops 를 쓴다(상한 5 는 별도).
        if (blockType == Blocks.SEA_LANTERN) return true;
        if (blockType == Blocks.GLOWSTONE) return true;
        // [UTILITY] 수박도 행운 보너스를 받는다(발광석과 같은 이 저장소의 행운 배율 경로).
        if (blockType == Blocks.MELON) return true;
        // 철·금 광석은 원석을 떨어뜨리므로 바닐라에서도 행운이 붙지 않는다.
        if (blockType == Blocks.IRON_ORE || blockType == Blocks.DEEPSLATE_IRON_ORE
                || blockType == Blocks.GOLD_ORE || blockType == Blocks.DEEPSLATE_GOLD_ORE) {
            return false;
        }
        return InventoryRules.isOreBlock(blockType);
    }
}
