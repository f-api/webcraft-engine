package com.gameexpert.engine.inventory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * [ARMOR-TRIM] 바닐라 26.3 {@code SmithingTrimRecipe} 의 순수 규칙(대장장이 격자 0/1/2 =
 * template/base/addition). 정적판 {@code StandaloneSmithingTrimRules.ts} +
 * {@code world/armorTrimRecipes.ts} 와 같은 표·같은 판정이며 {@link #fingerprint()} 가 그 동일성을
 * 고정한다.
 *
 * <ul>
 *   <li>형판 → 무늬: {@code recipe/<pattern>_armor_trim_smithing_template_smithing_trim.json}
 *       18종(파일 이름 순서).</li>
 *   <li>기본 장비: {@code #minecraft:trimmable_armor}(= head/chest/leg/foot_armor 태그 —
 *       가죽·구리·사슬·금·철·다이아·네더라이트 네 부위 + 거북 등껍질) — {@link ArmorTrimKeys}.</li>
 *   <li>재료 아이템 → 재료: {@code #minecraft:trim_materials} 11종(태그 선언 순서)과 각 아이템의
 *       {@code provides_trim_material}.</li>
 *   <li>결과({@code SmithingTrimRecipe#applyTrim}): 기본 장비를 {@code copyWithCount(1)} 해
 *       {@code minecraft:trim} 만 바꾼다. 이미 <b>같은</b> 트림이면 {@code ItemStack.EMPTY}.</li>
 *   <li>가져가면 {@code SmithingMenu#onTake} 가 세 칸을 하나씩 줄인다(형판도 소모).</li>
 * </ul>
 */
public final class SmithingTrimRules {

    private static final Map<Short, String> PATTERN_BY_TEMPLATE = createPatterns();
    private static final Map<Short, String> MATERIAL_BY_ITEM = createMaterials();
    private static final String FINGERPRINT = createFingerprint();

    private SmithingTrimRules() {
    }

    private static Map<Short, String> createPatterns() {
        Map<Short, String> patterns = new LinkedHashMap<>();
        patterns.put(PlayerInventory.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, "bolt");
        patterns.put(PlayerInventory.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, "coast");
        patterns.put(PlayerInventory.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, "dune");
        patterns.put(PlayerInventory.EYE_ARMOR_TRIM_SMITHING_TEMPLATE, "eye");
        patterns.put(PlayerInventory.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, "flow");
        patterns.put(PlayerInventory.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, "host");
        patterns.put(PlayerInventory.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, "raiser");
        patterns.put(PlayerInventory.RIB_ARMOR_TRIM_SMITHING_TEMPLATE, "rib");
        patterns.put(PlayerInventory.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, "sentry");
        patterns.put(PlayerInventory.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, "shaper");
        patterns.put(PlayerInventory.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, "silence");
        patterns.put(PlayerInventory.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, "snout");
        patterns.put(PlayerInventory.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, "spire");
        patterns.put(PlayerInventory.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, "tide");
        patterns.put(PlayerInventory.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, "vex");
        patterns.put(PlayerInventory.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, "ward");
        patterns.put(PlayerInventory.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, "wayfinder");
        patterns.put(PlayerInventory.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, "wild");
        return Collections.unmodifiableMap(patterns);
    }

    private static Map<Short, String> createMaterials() {
        Map<Short, String> materials = new LinkedHashMap<>();
        materials.put(PlayerInventory.AMETHYST_SHARD, "amethyst");
        materials.put(PlayerInventory.COPPER_INGOT, "copper");
        materials.put(PlayerInventory.DIAMOND, "diamond");
        materials.put(PlayerInventory.EMERALD, "emerald");
        materials.put(PlayerInventory.GOLD_INGOT, "gold");
        materials.put(PlayerInventory.IRON_INGOT, "iron");
        materials.put(PlayerInventory.LAPIS_LAZULI, "lapis");
        materials.put(PlayerInventory.NETHERITE_INGOT, "netherite");
        materials.put(PlayerInventory.QUARTZ, "quartz");
        materials.put(PlayerInventory.REDSTONE_DUST, "redstone");
        materials.put(PlayerInventory.RESIN_BRICK, "resin");
        return Collections.unmodifiableMap(materials);
    }

    private static String createFingerprint() {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<Short, String> entry : PATTERN_BY_TEMPLATE.entrySet()) {
            if (!text.isEmpty()) text.append(',');
            text.append(Short.toUnsignedInt(entry.getKey())).append('>').append(entry.getValue());
        }
        for (Map.Entry<Short, String> entry : MATERIAL_BY_ITEM.entrySet()) {
            text.append(',').append(Short.toUnsignedInt(entry.getKey())).append('=')
                    .append(entry.getValue());
        }
        return text.toString();
    }

    /** 형판 → 무늬 · 재료 아이템 → 재료 표의 안정 문자열(정적판 {@code armorTrimRecipes.ts} 와 같은 순서). */
    public static String fingerprint() {
        return FINGERPRINT;
    }

    /** 형판 아이템의 바닐라 무늬 키(네임스페이스 없음). 형판이 아니면 null. */
    public static String patternForTemplate(short itemType) {
        return PATTERN_BY_TEMPLATE.get(itemType);
    }

    /** {@code #trim_materials} 아이템의 재료 키. 재료가 아니면 null. */
    public static String materialForItem(short itemType) {
        return MATERIAL_BY_ITEM.get(itemType);
    }

    public static boolean isTrimTemplate(short itemType) {
        return PATTERN_BY_TEMPLATE.containsKey(itemType);
    }

    /**
     * [ARMOR-TRIM] {@code SmithingMenu} 입력 칸 판정({@code smithing_template} · {@code smithing_base} ·
     * {@code smithing_addition} 레시피 속성 집합): 이 저장소의 대장장이 레시피는 갑옷 장식과 네더라이트
     * 강화 둘이다. 정적판 {@code StandaloneCrafting.isSmithing*Item} 과 같은 표다.
     */
    public static boolean isSmithingTemplateItem(short itemType) {
        return isTrimTemplate(itemType) || itemType == PlayerInventory.NETHERITE_UPGRADE_SMITHING_TEMPLATE;
    }

    public static boolean isSmithingBaseItem(short itemType) {
        return ArmorTrimKeys.isTrimmableArmor(itemType) || SmithingTransformRules.isSupportedBase(itemType);
    }

    public static boolean isSmithingAdditionItem(short itemType) {
        return materialForItem(itemType) != null || itemType == PlayerInventory.NETHERITE_INGOT;
    }

    /**
     * template/base/addition 을 그 순서대로 검증해 원자적 트림 계획을 낸다. 입력 스냅샷은
     * 변경하지 않으며 같은 입력이면 같은 계획이다. 성립하지 않으면 null.
     */
    public static SmithingTransformRules.Plan plan(PlayerInventory.StackSnapshot template,
            PlayerInventory.StackSnapshot base, PlayerInventory.StackSnapshot addition) {
        if (template == null || base == null || addition == null
                || template.count() < 1 || base.count() < 1 || addition.count() < 1) {
            return null;
        }
        String pattern = PATTERN_BY_TEMPLATE.get(template.itemType());
        String material = MATERIAL_BY_ITEM.get(addition.itemType());
        if (pattern == null || material == null
                || !ArmorTrimKeys.isTrimmableArmor(base.itemType())) {
            return null;
        }
        ItemComponentData components = ItemComponentCodec.decode(
                base.itemType(), base.itemComponentData());
        ItemComponentData.ArmorTrim next = new ItemComponentData.ArmorTrim(pattern, material);
        if (Objects.equals(components.trim(), next)) return null;
        String encoded = ItemComponentCodec.encode(base.itemType(), components.withTrim(next));
        PlayerInventory.StackSnapshot result = new PlayerInventory.StackSnapshot(
                base.itemType(), 1, base.durability(), base.enchantments(), base.mapId(),
                base.shulkerId(), base.bucketMobData(), encoded);
        return new SmithingTransformRules.Plan(result, decrement(template), decrement(base),
                decrement(addition));
    }

    private static PlayerInventory.StackSnapshot decrement(PlayerInventory.StackSnapshot stack) {
        if (stack.count() == 1) return PlayerInventory.StackSnapshot.EMPTY;
        return new PlayerInventory.StackSnapshot(stack.itemType(), stack.count() - 1,
                stack.durability(), stack.enchantments(), stack.mapId(), stack.shulkerId(),
                stack.bucketMobData(), stack.itemComponentData());
    }
}
