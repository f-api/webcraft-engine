package com.gameexpert.engine.mob;

import com.gameexpert.engine.CombatRules;
import com.gameexpert.engine.enchant.EnchantmentRules;
import com.gameexpert.engine.enchant.WideEnchantments;
import com.gameexpert.engine.inventory.ItemComponentCodec;
import com.gameexpert.engine.inventory.PlayerInventory;

/**
 * [MOB-EQUIP] 몹 장비 한 칸의 스택 성분 규칙. 표현은 플레이어 스택과 같다 — 워드 0 인챈트
 * {@code long} 과 성분 문자열({@link ItemComponentCodec}, 확장 인챈트·사용자 이름·가죽 색 등). 성분
 * 문자열은 코덱이 검증만 하고 그대로 옮기므로 코덱이 새 성분(갑옷 장식 등)을 얻으면 몹 장비도 그대로
 * 보존한다.
 *
 * <p>교체 판정은 핀 26.3 {@code net.minecraft.world.entity.Mob}(javap)의 {@code canReplaceCurrentItem}
 * → {@code compareArmor} / {@code compareWeapons} → {@code canReplaceEqualItem} 을 그대로 옮긴다.
 * 정적판 사본은 {@code StandaloneMobEquipment.ts} 다.</p>
 */
public final class MobEquipmentRules {
    private MobEquipmentRules() {}

    /** 칸 비트: 주손 0, 방어구 {@code ArmorSlot.ordinal() + 1}. */
    public static final int MAIN_HAND_BIT = 1;
    public static final int ALL_SLOTS = 0x1F;

    public static int armorBit(int armorOrdinal) {
        return 1 << (armorOrdinal + 1);
    }

    /** 몹 칸 하나의 스택. {@code itemType == 0} 이면 빈 칸(성분 없음). */
    public record Stack(short itemType, int durability, long enchantments, String components) {
        public static final Stack EMPTY = new Stack(PlayerInventory.EMPTY, 0, 0L, null);

        public boolean isEmpty() { return itemType == PlayerInventory.EMPTY; }

        public WideEnchantments wide() {
            return PlayerInventory.wideEnchantmentsOf(enchantments, components);
        }

        /** 바닐라 {@code getDamageValue()}: 최대 내구 − 남은 내구(내구 없는 아이템은 0). */
        int damageValue() {
            int maximum = PlayerInventory.initialDurability(itemType);
            return maximum <= 0 ? 0 : maximum - durability;
        }

        boolean hasCustomName() {
            return components != null
                    && ItemComponentCodec.decode(itemType, components).customName() != null;
        }
    }

    /**
     * 칸 성분이 이 아이템에 유효한가. 빈 칸은 성분이 없어야 하고, 인챈트는 레이아웃·대상·최대 레벨
     * ({@link EnchantmentRules#isValidEnchantmentsForItem}, 배타 조합은 스택에 허용), 성분 문자열은 코덱
     * 검증을 통과해야 한다.
     */
    public static boolean validComponents(short itemType, long enchantments, String components) {
        if (itemType == PlayerInventory.EMPTY) return enchantments == 0L && components == null;
        if (!EnchantmentRules.isValidEnchantmentMask(enchantments)) return false;
        try {
            var decoded = ItemComponentCodec.decode(itemType, components);
            return EnchantmentRules.isValidEnchantmentsForItem(itemType,
                    decoded.enchantments(enchantments));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /**
     * 바닐라 {@code Mob.canReplaceCurrentItem(newItem, current, slot)}. {@code current} 가 비었으면 참,
     * 방어구 칸이면 {@code compareArmor}, 주손이면 {@code compareWeapons}.
     *
     * @param preferredWeapon 바닐라 {@code getPreferredWeaponType()} 태그의 유일한 아이템(해골 계열 활,
     *                        드라운드 삼지창), 없으면 0
     */
    public static boolean canReplaceCurrentItem(Stack candidate, Stack current, boolean armorSlot,
            short preferredWeapon) {
        if (current.isEmpty()) return true;
        return armorSlot ? compareArmor(candidate, current)
                : compareWeapons(candidate, current, preferredWeapon);
    }

    /**
     * 바닐라 {@code compareArmor}: 현재 칸에 결속의 저주({@code prevent_armor_change})가 있으면 거짓,
     * 방어도가 다르면 큰 쪽, 같으면 방어 강도가 다르면 큰 쪽, 둘 다 같으면 {@code canReplaceEqualItem}.
     */
    static boolean compareArmor(Stack candidate, Stack current) {
        if (current.wide().level(EnchantmentRules.BINDING_CURSE) > 0) return false;
        int candidateArmor = PlayerInventory.armorPoints(candidate.itemType());
        int currentArmor = PlayerInventory.armorPoints(current.itemType());
        if (candidateArmor != currentArmor) return candidateArmor > currentArmor;
        int candidateToughness = PlayerInventory.armorToughness(candidate.itemType());
        int currentToughness = PlayerInventory.armorToughness(current.itemType());
        if (candidateToughness != currentToughness) return candidateToughness > currentToughness;
        return canReplaceEqualItem(candidate, current);
    }

    /**
     * 바닐라 {@code compareWeapons}: 선호 무기 태그가 있으면 새 것만 선호면 참·현재 것만 선호면 거짓,
     * 그다음 공격 피해 속성(기본값 공통이라 아이템 피해로 비교), 같으면 {@code canReplaceEqualItem}.
     */
    static boolean compareWeapons(Stack candidate, Stack current, short preferredWeapon) {
        if (preferredWeapon != PlayerInventory.EMPTY) {
            boolean candidatePreferred = candidate.itemType() == preferredWeapon;
            boolean currentPreferred = current.itemType() == preferredWeapon;
            if (currentPreferred && !candidatePreferred) return false;
            if (candidatePreferred && !currentPreferred) return true;
        }
        double candidateDamage = CombatRules.meleeDamage(candidate.itemType());
        double currentDamage = CombatRules.meleeDamage(current.itemType());
        if (candidateDamage != currentDamage) return candidateDamage > currentDamage;
        return canReplaceEqualItem(candidate, current);
    }

    /**
     * 바닐라 {@code canReplaceEqualItem}: 인챈트 개수(레벨 무관)가 다르면 많은 쪽, 같으면 손상값이 작은
     * 쪽, 그것도 같으면 새 것에만 사용자 이름이 있을 때 참.
     */
    static boolean canReplaceEqualItem(Stack candidate, Stack current) {
        int candidateCount = candidate.wide().count();
        int currentCount = current.wide().count();
        if (candidateCount != currentCount) return candidateCount > currentCount;
        int candidateDamage = candidate.damageValue();
        int currentDamage = current.damageValue();
        if (candidateDamage != currentDamage) return candidateDamage < currentDamage;
        return candidate.hasCustomName() && !current.hasCustomName();
    }

    /**
     * 바닐라 {@code Mob.equipItemIfPossible} 의 교체 장비 드랍: {@code max(nextFloat - 0.1, 0) < chance}.
     * 자연 장비 8.5%, 주운 장비(보장 2.0)는 언제나, 트라이얼 장비(0.0)는 결코 떨어지지 않는다.
     */
    public static boolean replacedItemDrops(float roll, float dropChance) {
        return Math.max(roll - 0.1F, 0.0F) < dropChance;
    }

    /** 바닐라 {@code Mob} 기본 칸 드랍 확률 0.085F. */
    public static final float NATURAL_DROP_CHANCE = 0.085F;
    /** 바닐라 {@code setGuaranteedDrop} 가 쓰는 2.0F. */
    public static final float GUARANTEED_DROP_CHANCE = 2.0F;

    /** 소실의 저주({@code prevent_equipment_drop})가 걸린 장비는 사망해도 떨어지지 않는다. */
    public static boolean preventsEquipmentDrop(Stack stack) {
        return stack.wide().level(EnchantmentRules.VANISHING_CURSE) > 0;
    }
}
