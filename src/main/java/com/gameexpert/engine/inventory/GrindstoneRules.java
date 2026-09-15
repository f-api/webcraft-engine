package com.gameexpert.engine.inventory;

import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.enchant.WideEnchantments;

/**
 * Pure grindstone planner.
 *
 * <p>[CURSE] Vanilla grindstones strip ordinary enchantments and <b>keep curses</b>, and the XP
 * they refund covers only what was actually stripped. Both halves live in {@link #plan}: the
 * result carries {@link EnchantmentRules#curseEnchantments} forward and the XP sum is computed
 * from {@link EnchantmentRules#nonCurseEnchantments} alone.
 */
public final class GrindstoneRules {
    private GrindstoneRules() {}

    public static final class Plan {
        private final PlayerInventory.StackSnapshot result;
        private final int enchantmentCostSum;
        private Plan(PlayerInventory.StackSnapshot result, int enchantmentCostSum) {
            this.result = result;
            this.enchantmentCostSum = enchantmentCostSum;
        }
        public PlayerInventory.StackSnapshot result() { return result; }
        public int minimumXp() { return (enchantmentCostSum + 1) / 2; }
        public int maximumXp() { return enchantmentCostSum; }
        public int xp(int randomOffset) {
            int half = minimumXp();
            if (randomOffset < 0 || randomOffset >= half) {
                throw new IllegalArgumentException("grindstone XP roll out of range");
            }
            return half + randomOffset;
        }
    }

    public static Plan plan(PlayerInventory.StackSnapshot first,
            PlayerInventory.StackSnapshot second) {
        if (first == null || first.isEmpty() || first.count() != 1) return null;
        boolean hasSecond = second != null && !second.isEmpty();
        if (hasSecond && (second.count() != 1 || second.itemType() != first.itemType()
                || !PlayerInventory.isDurable(first.itemType()))) return null;
        int durability = first.durability();
        WideEnchantments firstEnchantments = first.wideEnchantments();
        WideEnchantments secondEnchantments = hasSecond
                ? second.wideEnchantments() : WideEnchantments.EMPTY;
        if (hasSecond) {
            int max = PlayerInventory.initialDurability(first.itemType());
            durability = Math.min(max,
                    first.durability() + second.durability() + max * 5 / 100);
        }
        // [CURSE] 저주는 숫돌을 통과해 결과에 그대로 남고 XP 도 주지 않는다. 바닐라
        // mergeEnchantsFrom 은 저주를 합치고(레벨 1 이라 합집합과 같다) removeNonCursesFrom 이 나머지를 벗긴다.
        WideEnchantments kept = EnchantmentRules.curseEnchantments(firstEnchantments)
                .union(EnchantmentRules.curseEnchantments(secondEnchantments));
        // 저주만 걸린 아이템 하나로는 바뀌는 게 없어 결과 자체가 없다.
        if (!hasSecond && EnchantmentRules.nonCurseEnchantments(firstEnchantments).isEmpty()) {
            return null;
        }
        PlayerInventory.StackSnapshot output = new PlayerInventory.StackSnapshot(first.itemType(), 1,
                durability, kept.word0(), first.mapId(), first.shulkerId(),
                first.bucketMobData(), ItemComponentCodec.withEnchantments(
                        first.itemType(), first.itemComponentData(), kept));
        // 바닐라 GrindstoneMenu 결과 칸 getExperienceAmount: 두 입력의 비저주 min_cost 를 각각 더한다.
        return new Plan(output, removedEnchantmentCost(firstEnchantments)
                + removedEnchantmentCost(secondEnchantments));
    }

    static int removedEnchantmentCost(long mask) {
        return removedEnchantmentCost(WideEnchantments.legacy(mask));
    }

    /** 비저주 인챈트의 바닐라 {@code Enchantment.getMinCost(level)} 합. */
    static int removedEnchantmentCost(WideEnchantments enchantments) {
        int sum = 0;
        for (int id = 0; id < EnchantmentRules.ENCHANTMENT_COUNT; id++) {
            if (EnchantmentRules.isCurse(id)) continue;
            int level = enchantments.level(id);
            if (level > 0) sum += EnchantmentRules.minCost(id, level);
        }
        return sum;
    }
}
