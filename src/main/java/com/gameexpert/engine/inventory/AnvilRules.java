package com.gameexpert.engine.inventory;

import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.enchant.WideEnchantments;

/** Pure, non-mutating anvil planner. Runtime/UI may commit a returned plan atomically. */
public final class AnvilRules {
    public static final int TOO_EXPENSIVE_COST = 40;
    public static final int MATERIAL_REPAIR_DIVISOR = 4;

    private AnvilRules() {}

    public static final class Plan {
        private final PlayerInventory.StackSnapshot result;
        private final int levelCost;
        private final int rightConsumed;
        private final boolean tooExpensive;
        private final String customName;
        private final ItemComponentData resultComponents;

        private Plan(PlayerInventory.StackSnapshot result, int levelCost, int rightConsumed,
                boolean tooExpensive, String customName, ItemComponentData resultComponents) {
            this.result = result;
            this.levelCost = levelCost;
            this.rightConsumed = rightConsumed;
            this.tooExpensive = tooExpensive;
            this.customName = customName;
            this.resultComponents = resultComponents;
        }

        public PlayerInventory.StackSnapshot result() { return result; }
        public int levelCost() { return levelCost; }
        public int rightConsumed() { return rightConsumed; }
        public boolean tooExpensive() { return tooExpensive; }
        public String customName() { return customName; }
        public ItemComponentData resultComponents() { return resultComponents; }
    }

    /**
     * Plans the component-preserving same-item repair/enchantment combine operation. An empty plan
     * is represented by {@code null}; callers must not consume either input in that case.
     */
    public static Plan plan(PlayerInventory.StackSnapshot left, ItemComponentData leftComponents,
            PlayerInventory.StackSnapshot right, ItemComponentData rightComponents,
            String requestedName, boolean creative) {
        if (left == null || left.isEmpty()
                || leftComponents == null || rightComponents == null
                || !validName(requestedName)) {
            return null;
        }
        boolean rename = requestedName != null;
        boolean hasRight = right != null && !right.isEmpty();
        if (!hasRight && !rename) return null;
        if (hasRight && left.count() != 1) return null;
        int max = PlayerInventory.initialDurability(left.itemType());
        int repaired = left.durability();
        WideEnchantments before = leftComponents.enchantments(left.enchantments());
        WideEnchantments combined = before;
        int rightConsumed = hasRight ? 1 : 0;
        // 바닐라 AnvilMenu.createResult: 같은 아이템 수리가 실제로 내구를 올렸을 때만 +2(바이트코드 431
        // iinc 2,2), 재료 수리는 재료 하나마다 +1. 마법책·안 닳은 같은 아이템 합성에는 붙지 않는다.
        int operationCost = 0;
        int enchantmentCost = 0;
        boolean fromBook = false;
        if (hasRight) {
            boolean sameDurable = right.itemType() == left.itemType()
                    && PlayerInventory.isDurable(left.itemType())
                    && right.count() == 1 && left.mapId() == right.mapId()
                    && left.shulkerId() == right.shulkerId()
                    && java.util.Objects.equals(left.bucketMobData(), right.bucketMobData());
            WideEnchantments incoming = rightComponents.enchantments(right.enchantments());
            boolean enchantedBook = right.itemType() == PlayerInventory.ENCHANTED_BOOK
                    && right.count() == 1 && !incoming.isEmpty();
            fromBook = enchantedBook;
            boolean repairMaterial = isRepairMaterial(left.itemType(), right.itemType());
            if (!sameDurable && !enchantedBook && !repairMaterial) return null;
            if (sameDurable) {
                repaired = Math.min(max,
                        left.durability() + right.durability() + max * 12 / 100);
                if (repaired > left.durability()) operationCost = 2;
            } else if (repairMaterial) {
                int used = 0;
                int perItem = Math.max(1, max / MATERIAL_REPAIR_DIVISOR);
                while (used < right.count() && repaired < max) {
                    repaired = Math.min(max, repaired + perItem);
                    used++;
                }
                rightConsumed = used;
                operationCost = used;
            }
            if (!repairMaterial) {
                Combination combination = combine(left.itemType(), before, incoming, fromBook,
                        creative);
                // 바닐라 AnvilMenu: 기증자의 인챈트가 모두 호환 불가이고 하나도 붙지 않으면 결과가 없다.
                if (combination.onlyIncompatible()) return null;
                combined = combination.result();
                enchantmentCost = combination.cost();
            }
        }
        if (!rename && repaired == left.durability() && combined.equals(before)) return null;
        long rawCost = (long) enchantmentCost
                + operationCost + (rename ? 1 : 0)
                + leftComponents.priorWorkPenalty()
                + (hasRight ? rightComponents.priorWorkPenalty() : 0);
        int cost = rawCost > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rawCost;
        boolean tooExpensive = !creative && cost >= TOO_EXPENSIVE_COST;
        PlayerInventory.StackSnapshot output = new PlayerInventory.StackSnapshot(left.itemType(),
                hasRight ? 1 : left.count(),
                repaired, combined.word0(), left.mapId(), left.shulkerId(), left.bucketMobData(),
                ItemComponentCodec.withEnchantments(left.itemType(), left.itemComponentData(),
                        combined));
        ItemComponentData resultComponents = (rename
                ? leftComponents.withCustomName(requestedName) : leftComponents).afterAnvilUse()
                .withEnchantments(combined);
        return new Plan(output, cost, rightConsumed, tooExpensive, requestedName, resultComponents);
    }

    /** 워드 0 마스크끼리의 합성(옛 호출자·테스트용). 확장 인챈트가 없으면 {@link #combine} 과 같다. */
    static long combineEnchantments(short itemType, long base, long addition, boolean fromBook) {
        return combine(itemType, WideEnchantments.legacy(base), WideEnchantments.legacy(addition),
                fromBook, false).result().word0();
    }

    /** 합성 결과·인챈트 비용·"호환 불가만 있었는가". */
    record Combination(WideEnchantments result, int cost, boolean onlyIncompatible) {}

    /**
     * [ENCHANT-WIDE] 바닐라 {@code AnvilMenu.createResult} 의 인챈트 합성 루프(26.3 바이트코드 기준):
     * 기증자의 인챈트마다 {@code level = cur == in ? in + 1 : max(in, cur)}, 대상이 받을 수 있는지
     * ({@code canEnchant}, 왼쪽이 마법책이면 언제나 참)와 이미 붙은 인챈트와의 호환을 본다. 호환되지
     * 않는 기존 인챈트 하나마다 비용 +1, 적용된 인챈트마다 {@code anvil_cost × level}(책 재료면
     * {@code max(1, anvil_cost / 2)}) 을 더한다. 레벨은 최대 레벨로 자른다.
     *
     * <p>[CURSE] 같은 아이템 기증자의 저주 전이는 이 저장소의 사용자 승인 결정대로 막는다(책은 허용).
     */
    static Combination combine(short itemType, WideEnchantments base, WideEnchantments addition,
            boolean fromBook, boolean creative) {
        WideEnchantments result = base;
        int cost = 0;
        boolean anyApplied = false;
        boolean anyIncompatible = false;
        for (int id = 0; id < EnchantmentRules.ENCHANTMENT_COUNT; id++) {
            int incoming = addition.level(id);
            if (incoming == 0) continue;
            if (EnchantmentRules.isCurse(id) && !fromBook) continue;
            int current = result.level(id);
            int level = incoming == current ? incoming + 1 : Math.max(incoming, current);
            boolean canEnchant = creative || EnchantmentRules.appliesTo(id, itemType)
                    || EnchantmentRules.isEnchantedBookItem(itemType);
            for (int existing = 0; existing < EnchantmentRules.ENCHANTMENT_COUNT; existing++) {
                if (existing != id && result.level(existing) > 0
                        && !EnchantmentRules.compatible(existing, id)) {
                    canEnchant = false;
                    cost++;
                }
            }
            if (!canEnchant) {
                anyIncompatible = true;
                continue;
            }
            anyApplied = true;
            level = Math.min(level, EnchantmentRules.maxLevel(id));
            result = result.with(id, level);
            int anvilCost = EnchantmentRules.anvilCost(id);
            if (fromBook) anvilCost = Math.max(1, anvilCost / 2);
            cost += anvilCost * level;
        }
        return new Combination(result, cost, anyIncompatible && !anyApplied);
    }

    private static boolean validName(String value) {
        if (value == null) return true;
        if (value.isBlank() || value.length() > NameTagRules.MAX_NAME_UTF16_UNITS) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) return false;
            } else if (Character.isLowSurrogate(c)) return false;
        }
        return true;
    }

    static boolean isRepairMaterial(short item, short material) {
        int id = Short.toUnsignedInt(item);
        int ingredient = Short.toUnsignedInt(material);
        if (id >= PlayerInventory.SWORD_ITEM && id <= PlayerInventory.SHOVEL
                || item == PlayerInventory.WOODEN_HOE) {
            return com.gameexpert.terrain.Blocks.isPlankBlock(ingredient);
        }
        if (id >= PlayerInventory.STONE_TIER_MIN && id <= PlayerInventory.STONE_TIER_MAX) {
            return ingredient == com.gameexpert.terrain.Blocks.COBBLE;
        }
        if (id >= PlayerInventory.IRON_TIER_MIN && id <= PlayerInventory.IRON_TIER_MAX
                || id >= PlayerInventory.IRON_HELMET && id <= PlayerInventory.IRON_BOOTS
                || item == PlayerInventory.CHAINMAIL_HELMET
                || item == PlayerInventory.CHAINMAIL_CHESTPLATE
                || item == PlayerInventory.CHAINMAIL_LEGGINGS
                || item == PlayerInventory.CHAINMAIL_BOOTS) {
            return material == PlayerInventory.IRON_INGOT;
        }
        if (id >= PlayerInventory.GOLD_TIER_MIN && id <= PlayerInventory.GOLD_TIER_MAX
                || id >= PlayerInventory.GOLD_ARMOR_MIN && id <= PlayerInventory.GOLD_ARMOR_MAX) {
            return material == PlayerInventory.GOLD_INGOT;
        }
        if (id >= PlayerInventory.DIAMOND_TIER_MIN && id <= PlayerInventory.DIAMOND_TIER_MAX
                || item == PlayerInventory.DIAMOND_HOE) return material == PlayerInventory.DIAMOND;
        if (id >= PlayerInventory.COPPER_PICKAXE && id <= PlayerInventory.COPPER_BOOTS) {
            return material == PlayerInventory.COPPER_INGOT;
        }
        if (id >= PlayerInventory.NETHERITE_PICKAXE && id <= PlayerInventory.NETHERITE_SPEAR) {
            return material == PlayerInventory.NETHERITE_INGOT;
        }
        if (id >= PlayerInventory.LEATHER_HELMET && id <= PlayerInventory.LEATHER_BOOTS) {
            return material == PlayerInventory.LEATHER;
        }
        if (item == PlayerInventory.ELYTRA) return material == PlayerInventory.PHANTOM_MEMBRANE;
        if (item == PlayerInventory.TURTLE_SHELL) return material == PlayerInventory.TURTLE_SCUTE;
        if (item == PlayerInventory.WOLF_ARMOR) return material == PlayerInventory.ARMADILLO_SCUTE;
        // [MACE] 바닐라 Items.MACE 의 repairable(BREEZE_ROD).
        if (item == PlayerInventory.MACE) return material == PlayerInventory.BREEZE_ROD;
        return false;
    }

    /** Vanilla anvil degradation: 12% after a successful non-creative operation. */
    public static int degradedBlock(int currentBlock, int roll0To99, boolean creative) {
        if (creative || roll0To99 < 0 || roll0To99 >= 100 || roll0To99 >= 12) return currentBlock;
        if (currentBlock == com.gameexpert.terrain.Blocks.ANVIL)
            return com.gameexpert.terrain.Blocks.CHIPPED_ANVIL;
        if (currentBlock == com.gameexpert.terrain.Blocks.CHIPPED_ANVIL)
            return com.gameexpert.terrain.Blocks.DAMAGED_ANVIL;
        if (currentBlock == com.gameexpert.terrain.Blocks.DAMAGED_ANVIL)
            return com.gameexpert.terrain.Blocks.AIR;
        throw new IllegalArgumentException("not an anvil block");
    }
}
