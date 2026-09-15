package com.gameexpert.engine.inventory;

import java.util.List;

/**
 * [UTILITY] 갑옷 장식 성분({@code minecraft:trim})의 어휘. 고정 26.3-snapshot-7 jar
 * {@code data/minecraft/trim_pattern/*.json}(18) · {@code trim_material/*.json}(11)과
 * {@code tags/item/trimmable_armor}(head/chest/leg/foot armor 태그) 그대로다. 정적판
 * {@code client/src/world/armorTrimKeys.ts} 가 같은 목록이다.
 */
public final class ArmorTrimKeys {

    /** 바닐라 trim_pattern 레지스트리 경로(네임스페이스 없이), 파일 이름순. */
    public static final List<String> PATTERNS = List.of(
            "bolt", "coast", "dune", "eye", "flow", "host", "raiser", "rib", "sentry", "shaper",
            "silence", "snout", "spire", "tide", "vex", "ward", "wayfinder", "wild");

    /** 바닐라 trim_material 레지스트리 경로, 파일 이름순. */
    public static final List<String> MATERIALS = List.of(
            "amethyst", "copper", "diamond", "emerald", "gold", "iron", "lapis", "netherite",
            "quartz", "redstone", "resin");

    private ArmorTrimKeys() {
    }

    public static boolean isPattern(String key) {
        return key != null && PATTERNS.contains(key);
    }

    public static boolean isMaterial(String key) {
        return key != null && MATERIALS.contains(key);
    }

    /**
     * {@code #minecraft:trimmable_armor}: 가죽·구리·사슬·금·철·다이아·네더라이트의 투구·흉갑·각반·장화와
     * 거북 등껍질. 썩은 가죽 방어구는 바닐라 아이템이 아니라 태그에 없다.
     */
    public static boolean isTrimmableArmor(short itemType) {
        return itemType >= PlayerInventory.LEATHER_HELMET && itemType <= PlayerInventory.LEATHER_BOOTS
                || itemType >= PlayerInventory.IRON_HELMET && itemType <= PlayerInventory.IRON_BOOTS
                || itemType >= PlayerInventory.GOLD_HELMET && itemType <= PlayerInventory.GOLD_BOOTS
                || itemType >= PlayerInventory.DIAMOND_HELMET && itemType <= PlayerInventory.DIAMOND_BOOTS
                || itemType >= PlayerInventory.CHAINMAIL_HELMET
                        && itemType <= PlayerInventory.CHAINMAIL_BOOTS
                || itemType >= PlayerInventory.COPPER_HELMET && itemType <= PlayerInventory.COPPER_BOOTS
                || itemType >= PlayerInventory.NETHERITE_HELMET
                        && itemType <= PlayerInventory.NETHERITE_BOOTS
                || itemType == PlayerInventory.TURTLE_SHELL;
    }
}
