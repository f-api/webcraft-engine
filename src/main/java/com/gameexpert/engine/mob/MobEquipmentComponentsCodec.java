package com.gameexpert.engine.mob;

import com.gameexpert.engine.enchant.EnchantmentRules;

/**
 * [MOB-EQUIP] 몹 장비 칸별 성분의 영속 문자열(열 {@code world_mob.equipment_components}, 정적판은 같은
 * 의미의 객체 필드). 한 줄 형식:
 *
 * <pre>MEC1;&lt;보장 드랍 비트&gt;;&lt;드랍 없음 비트&gt;;&lt;주손 워드0&gt;;&lt;주손 성분&gt;;&lt;투구 워드0&gt;;&lt;투구 성분&gt;;…;&lt;부츠 성분&gt;</pre>
 *
 * 비트는 10진 {@code 0..31}(주손 1, 방어구 {@code 1 << (ordinal+1)}), 워드0 은 10진 48비트 인챈트
 * 마스크, 성분은 {@code ItemComponentCodec} 문자열 그대로(없으면 {@code ~}). 성분 문자열에는 {@code ;} 가
 * 나오지 않는다(WCIC 는 {@code |}·{@code ,}·{@code :}·Base64url 만 쓴다). 칸 순서는 주손 → HELMET →
 * CHESTPLATE → LEGGINGS → BOOTS 다. 옛 행은 열이 null 이다.
 */
public final class MobEquipmentComponentsCodec {
    private MobEquipmentComponentsCodec() {}

    public static final String PREFIX = "MEC1";
    public static final int SLOT_COUNT = 5;
    private static final String NULL = "~";

    public record Decoded(int guaranteedDropSlots, int noDropSlots, long[] words,
            String[] componentStrings) {
        public long enchantments(int slot) { return words[slot]; }
        public String components(int slot) { return componentStrings[slot]; }
    }

    public static String encode(int guaranteedDropSlots, int noDropSlots, long[] words,
            String[] components) {
        if (words.length != SLOT_COUNT || components.length != SLOT_COUNT
                || guaranteedDropSlots < 0 || guaranteedDropSlots > MobEquipmentRules.ALL_SLOTS
                || noDropSlots < 0 || noDropSlots > MobEquipmentRules.ALL_SLOTS) {
            throw new IllegalArgumentException("invalid mob equipment components");
        }
        StringBuilder out = new StringBuilder(PREFIX).append(';').append(guaranteedDropSlots)
                .append(';').append(noDropSlots);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            String component = components[slot];
            if (!EnchantmentRules.isValidEnchantmentMask(words[slot])
                    || component != null && (component.isEmpty() || component.indexOf(';') >= 0
                            || NULL.equals(component))) {
                throw new IllegalArgumentException("invalid mob equipment slot");
            }
            out.append(';').append(words[slot]).append(';').append(component == null ? NULL : component);
        }
        return out.toString();
    }

    public static Decoded decode(String encoded) {
        String[] fields = encoded.split(";", -1);
        if (fields.length != 3 + 2 * SLOT_COUNT || !PREFIX.equals(fields[0])) {
            throw new IllegalStateException("invalid persisted mob equipment components");
        }
        try {
            int guaranteed = canonicalInt(fields[1]);
            int noDrop = canonicalInt(fields[2]);
            long[] words = new long[SLOT_COUNT];
            String[] components = new String[SLOT_COUNT];
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                String word = fields[3 + 2 * slot];
                if (word.isEmpty() || word.length() > 1 && word.charAt(0) == '0'
                        || !word.chars().allMatch(Character::isDigit)) {
                    throw new IllegalStateException("non-canonical enchantment word");
                }
                words[slot] = Long.parseLong(word);
                if (!EnchantmentRules.isValidEnchantmentMask(words[slot])) {
                    throw new IllegalStateException("enchantment word out of range");
                }
                String component = fields[4 + 2 * slot];
                if (component.isEmpty()) throw new IllegalStateException("empty component field");
                components[slot] = NULL.equals(component) ? null : component;
            }
            if (guaranteed > MobEquipmentRules.ALL_SLOTS || noDrop > MobEquipmentRules.ALL_SLOTS) {
                throw new IllegalStateException("drop slot mask out of range");
            }
            return new Decoded(guaranteed, noDrop, words, components);
        } catch (NumberFormatException invalid) {
            throw new IllegalStateException("invalid persisted mob equipment components", invalid);
        }
    }

    private static int canonicalInt(String field) {
        if (field.isEmpty() || field.length() > 2 || field.length() > 1 && field.charAt(0) == '0'
                || !field.chars().allMatch(Character::isDigit)) {
            throw new IllegalStateException("non-canonical drop slot mask");
        }
        return Integer.parseInt(field);
    }
}
