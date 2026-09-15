package com.gameexpert.engine.inventory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 네더라이트 대장장이 변환의 슬롯 순서와 손실 없는 결과를 계산하는 순수 규칙입니다. */
public final class SmithingTransformRules {

    private static final Map<Short, Short> TRANSFORMS = createTransforms();
    private static final String FINGERPRINT = TRANSFORMS.entrySet().stream()
            .map(entry -> Short.toUnsignedInt(entry.getKey()) + ">"
                    + Short.toUnsignedInt(entry.getValue()))
            .reduce((left, right) -> left + "," + right)
            .orElse("");

    private SmithingTransformRules() {
    }

    private static Map<Short, Short> createTransforms() {
        Map<Short, Short> transforms = new LinkedHashMap<>();
        transforms.put(PlayerInventory.DIAMOND_PICKAXE, PlayerInventory.NETHERITE_PICKAXE);
        transforms.put(PlayerInventory.DIAMOND_AXE, PlayerInventory.NETHERITE_AXE);
        transforms.put(PlayerInventory.DIAMOND_SHOVEL, PlayerInventory.NETHERITE_SHOVEL);
        transforms.put(PlayerInventory.DIAMOND_SWORD, PlayerInventory.NETHERITE_SWORD);
        transforms.put(PlayerInventory.DIAMOND_HOE, PlayerInventory.NETHERITE_HOE);
        transforms.put(PlayerInventory.DIAMOND_HELMET, PlayerInventory.NETHERITE_HELMET);
        transforms.put(PlayerInventory.DIAMOND_CHESTPLATE, PlayerInventory.NETHERITE_CHESTPLATE);
        transforms.put(PlayerInventory.DIAMOND_LEGGINGS, PlayerInventory.NETHERITE_LEGGINGS);
        transforms.put(PlayerInventory.DIAMOND_BOOTS, PlayerInventory.NETHERITE_BOOTS);
        transforms.put(PlayerInventory.DIAMOND_SPEAR, PlayerInventory.NETHERITE_SPEAR);
        transforms.put(PlayerInventory.DIAMOND_HORSE_ARMOR, PlayerInventory.NETHERITE_HORSE_ARMOR);
        transforms.put(PlayerInventory.DIAMOND_NAUTILUS_ARMOR,
                PlayerInventory.NETHERITE_NAUTILUS_ARMOR);
        return Collections.unmodifiableMap(transforms);
    }

    /** Java/standalone 표가 같은 순서와 값을 갖는지 고정하는 안정 문자열입니다. */
    public static String fingerprint() {
        return FINGERPRINT;
    }

    public static boolean isSupportedBase(short itemType) {
        return TRANSFORMS.containsKey(itemType);
    }

    public static boolean isSupportedResult(short itemType) {
        return TRANSFORMS.containsValue(itemType);
    }

    /**
     * template/base/addition 슬롯을 그 순서 그대로 검증하고 한 번의 원자적 변환 계획을 냅니다.
     * 입력 스냅샷은 변경하지 않으며, 같은 입력으로 반복 호출하면 같은 계획을 돌려줍니다.
     */
    public static Plan plan(PlayerInventory.StackSnapshot template,
            PlayerInventory.StackSnapshot base, PlayerInventory.StackSnapshot addition) {
        if (template == null || base == null || addition == null
                || template.itemType() != PlayerInventory.NETHERITE_UPGRADE_SMITHING_TEMPLATE
                || addition.itemType() != PlayerInventory.NETHERITE_INGOT
                || template.count() < 1 || addition.count() < 1 || base.count() != 1) {
            return null;
        }
        Short resultType = TRANSFORMS.get(base.itemType());
        if (resultType == null) return null;

        int oldMaximum = PlayerInventory.initialDurability(base.itemType());
        int newMaximum = PlayerInventory.initialDurability(resultType);
        if ((oldMaximum == 0) != (base.durability() == 0)
                || (newMaximum == 0) != (oldMaximum == 0)) {
            return null;
        }
        int resultDurability = oldMaximum == 0 ? 0
                : Math.max(1, newMaximum - (oldMaximum - base.durability()));
        PlayerInventory.StackSnapshot result = new PlayerInventory.StackSnapshot(
                resultType, 1, resultDurability, base.enchantments(), base.mapId(), base.shulkerId(),
                base.bucketMobData(), base.itemComponentData());
        return new Plan(result, decrement(template), PlayerInventory.StackSnapshot.EMPTY,
                decrement(addition));
    }

    private static PlayerInventory.StackSnapshot decrement(PlayerInventory.StackSnapshot stack) {
        if (stack.count() == 1) return PlayerInventory.StackSnapshot.EMPTY;
        return new PlayerInventory.StackSnapshot(stack.itemType(), stack.count() - 1,
                stack.durability(), stack.enchantments(), stack.mapId(), stack.shulkerId(),
                stack.bucketMobData(), stack.itemComponentData());
    }

    /** 호출자가 세 입력 소비와 결과 지급을 한 번에 반영할 수 있는 불변 계획입니다. */
    public static final class Plan {
        private final PlayerInventory.StackSnapshot result;
        private final PlayerInventory.StackSnapshot templateAfter;
        private final PlayerInventory.StackSnapshot baseAfter;
        private final PlayerInventory.StackSnapshot additionAfter;

        /** [ARMOR-TRIM] 같은 패키지의 {@link SmithingTrimRules} 도 같은 계획 모양을 낸다. */
        Plan(PlayerInventory.StackSnapshot result,
                PlayerInventory.StackSnapshot templateAfter,
                PlayerInventory.StackSnapshot baseAfter,
                PlayerInventory.StackSnapshot additionAfter) {
            this.result = result;
            this.templateAfter = templateAfter;
            this.baseAfter = baseAfter;
            this.additionAfter = additionAfter;
        }

        public PlayerInventory.StackSnapshot result() { return result; }
        public PlayerInventory.StackSnapshot templateAfter() { return templateAfter; }
        public PlayerInventory.StackSnapshot baseAfter() { return baseAfter; }
        public PlayerInventory.StackSnapshot additionAfter() { return additionAfter; }

        /** 클라이언트가 제시한 결과 슬롯이 이 계획과 구성요소까지 완전히 같은지 확인합니다. */
        public boolean matchesResult(PlayerInventory.StackSnapshot candidate) {
            return result.equals(candidate);
        }
    }
}
