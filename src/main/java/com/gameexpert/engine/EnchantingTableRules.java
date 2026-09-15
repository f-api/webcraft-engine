package com.gameexpert.engine;

import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.terrain.Blocks;

/**
 * [SURV-X] 인챈트 테이블 주변 책장 세기와 제안 3줄 구성의 순수 규칙입니다.
 * 마법 수식 자체는 {@link EnchantmentRules} 가 정본이고, 여기서는 월드 스캔 기하만 담당합니다.
 */
public final class EnchantingTableRules {

    /** 좌표 하나의 블록 ID를 돌려주는 조회기(테스트 주입 가능). */
    public interface BlockLookup {
        int blockAt(int x, int y, int z);
    }

    private EnchantingTableRules() {
    }

    /**
     * 바닐라 {@code EnchantmentMenu} 와 같은 기하로 주변 책장을 셉니다.
     * 8방향 각각에 대해 테이블 바로 옆 두 칸(발치·눈높이)이 모두 비어 있을 때만 두 칸 바깥을 봅니다.
     * 대각선 방향은 사이 칸까지 함께 세어 최대 32칸을 후보로 삼고, 값은 15로 잘립니다.
     */
    public static int countBookshelves(BlockLookup world, int x, int y, int z) {
        int power = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;
                if (!isAir(world, x + dx, y, z + dz) || !isAir(world, x + dx, y + 1, z + dz)) {
                    continue;
                }
                power += bookshelfAt(world, x + dx * 2, y, z + dz * 2);
                power += bookshelfAt(world, x + dx * 2, y + 1, z + dz * 2);
                if (dx != 0 && dz != 0) {
                    power += bookshelfAt(world, x + dx * 2, y, z + dz);
                    power += bookshelfAt(world, x + dx * 2, y + 1, z + dz);
                    power += bookshelfAt(world, x + dx, y, z + dz * 2);
                    power += bookshelfAt(world, x + dx, y + 1, z + dz * 2);
                }
            }
        }
        return EnchantmentRules.bookshelfPower(power);
    }

    private static int bookshelfAt(BlockLookup world, int x, int y, int z) {
        return world.blockAt(x, y, z) == Blocks.BOOKSHELF ? 1 : 0;
    }

    private static boolean isAir(BlockLookup world, int x, int y, int z) {
        return world.blockAt(x, y, z) == Blocks.AIR;
    }

    /** 제안 한 줄의 확정 결과(레벨 요구치·청금석 비용·마스크·구매 가능 여부). */
    public record Offer(int levelCost, int lapisCost,
            com.gameexpert.engine.enchant.WideEnchantments enchantments, boolean affordable) {
    }

    /**
     * 제안 3줄을 한 번에 만듭니다. 대상 아이템이 없거나 인챈트 대상이 아니면 전부 0/불가입니다.
     * 비용 기준값(base)은 세 줄이 공유하는 한 번의 추첨 결과입니다(§4).
     */
    public static Offer[] buildOffers(short itemType, int lapisCount, int bookshelves,
            int playerLevel, int enchantSeed) {
        Offer[] offers = new Offer[EnchantmentRules.ENCHANT_OFFER_COUNT];
        int power = EnchantmentRules.bookshelfPower(bookshelves);
        boolean enchantable = EnchantmentRules.isTableEnchantable(itemType);
        int base = EnchantmentRules.enchantCostBase(enchantSeed, power);
        for (int slot = 0; slot < offers.length; slot++) {
            if (!enchantable) {
                // 줄 수는 언제나 ENCHANT_OFFER_COUNT 다. 빈 줄은 요구 레벨 0 으로만 표시한다.
                offers[slot] = new Offer(EnchantmentRules.EMPTY_OFFER_LEVEL_COST,
                        EnchantmentRules.offerLapisCost(slot),
                        com.gameexpert.engine.enchant.WideEnchantments.EMPTY, false);
                continue;
            }
            int levelCost = EnchantmentRules.offerLevelCost(slot, base, power);
            int lapisCost = EnchantmentRules.offerLapisCost(slot);
            com.gameexpert.engine.enchant.WideEnchantments mask =
                    EnchantmentRules.rollEnchantments(itemType, levelCost, enchantSeed, slot);
            boolean affordable = EnchantmentRules.offerSelectable(
                    playerLevel, lapisCount, slot, levelCost, mask);
            offers[slot] = new Offer(levelCost, lapisCost, mask, affordable);
        }
        return offers;
    }
}
