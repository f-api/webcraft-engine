package com.gameexpert.engine;

import com.gameexpert.engine.inventory.PlayerInventory;

/**
 * 근접 전투(플레이어→몹)의 순수 규칙. 상태 없음.
 *
 * 피해와 attack speed 는 MC-REFERENCE §7의 20포인트 스케일을 사용한다.
 * 클라이언트는 3.0블록 시선 레이로 대상을 고르고, 서버는 바닐라와 같이 눈에서 대상 AABB까지의
 * 거리와 패킷 허용 오차를 검증한다.
 * 쿨다운 중 공격도 성립하며, 마지막 공격 이후 경과 틱에 따라 MC 충전 배율을 적용한다.
 */
public final class CombatRules {

    private CombatRules() {
    }

    // ID 는 PlayerInventory 가 단일 출처다. 여기서 값을 복제하면 재배정 때 조용히 갈린다
    // (2026-07-20 아이템 ID 를 256+ 로 분리할 때 실제로 12개가 옛 값으로 남았다).
    public static final short SWORD_ITEM = PlayerInventory.SWORD_ITEM;        // 나무 검
    public static final short STONE_SWORD = PlayerInventory.STONE_SWORD;      // 돌 검(§10.2-S2b)
    public static final short IRON_SWORD = PlayerInventory.IRON_SWORD;        // 철 검(§10.2-S2b)
    public static final short WOOD_PICKAXE = PlayerInventory.PICKAXE;
    public static final short WOOD_AXE = PlayerInventory.AXE;
    public static final short WOOD_SHOVEL = PlayerInventory.SHOVEL;
    public static final short STONE_PICKAXE = PlayerInventory.STONE_PICKAXE;
    public static final short STONE_AXE = PlayerInventory.STONE_AXE;
    public static final short STONE_SHOVEL = PlayerInventory.STONE_SHOVEL;
    public static final short IRON_PICKAXE = PlayerInventory.IRON_PICKAXE;
    public static final short IRON_AXE = PlayerInventory.IRON_AXE;
    public static final short IRON_SHOVEL = PlayerInventory.IRON_SHOVEL;
    public static final short GOLD_PICKAXE = PlayerInventory.GOLD_PICKAXE;
    public static final short GOLD_AXE = PlayerInventory.GOLD_AXE;
    public static final short GOLD_SHOVEL = PlayerInventory.GOLD_SHOVEL;
    public static final short GOLD_SWORD = PlayerInventory.GOLD_SWORD;
    public static final short DIAMOND_PICKAXE = PlayerInventory.DIAMOND_PICKAXE;
    public static final short DIAMOND_AXE = PlayerInventory.DIAMOND_AXE;
    public static final short DIAMOND_SHOVEL = PlayerInventory.DIAMOND_SHOVEL;
    public static final short DIAMOND_SWORD = PlayerInventory.DIAMOND_SWORD;
    public static final short COPPER_PICKAXE = PlayerInventory.COPPER_PICKAXE;
    public static final short COPPER_AXE = PlayerInventory.COPPER_AXE;
    public static final short COPPER_SHOVEL = PlayerInventory.COPPER_SHOVEL;
    public static final short COPPER_SWORD = PlayerInventory.COPPER_SWORD;
    public static final short NETHERITE_PICKAXE = PlayerInventory.NETHERITE_PICKAXE;
    public static final short NETHERITE_AXE = PlayerInventory.NETHERITE_AXE;
    public static final short NETHERITE_SHOVEL = PlayerInventory.NETHERITE_SHOVEL;
    public static final short NETHERITE_SWORD = PlayerInventory.NETHERITE_SWORD;
    /** [TRIDENT] 삼지창. 근접 무기표가 읽는 정본 ID 다. */
    public static final short TRIDENT = PlayerInventory.TRIDENT;
    /** [MACE] 철퇴. 근접 무기표가 읽는 정본 ID 다. */
    public static final short MACE = PlayerInventory.MACE;
    /**
     * [MACE][A] 철퇴 근접 피해 6. 26.3-snapshot-7 {@code MaceItem.createAttributes} 의 attack_damage
     * 수정자 +5.0(ADD_VALUE) 에 플레이어 기본 1 을 더한 값이다. 낙하 강타 보너스는
     * {@link MaceRules#smashBonusDamage} 가 따로 더한다.
     */
    public static final double MACE_DAMAGE = 6.0;
    /**
     * [MACE][A] 철퇴 attack speed. {@code MaceItem} 의 attack_speed 수정자는 float 상수
     * {@code DEFAULT_ATTACK_SPEED = -3.4f} 를 double 로 넓힌 {@code -3.4000000953674316} 이고,
     * 속성 기본값 4.0 에 더해 0.5999999046325684 가 된다(바닐라 double 산술 그대로).
     */
    public static final double MACE_ATTACK_SPEED = 4.0 + (double) -3.4f;

    public static final double SWORD_DAMAGE = 4.0;          // 나무 검 피해
    public static final double STONE_SWORD_DAMAGE = 5.0;    // 돌 검 피해
    public static final double IRON_SWORD_DAMAGE = 6.0;     // 철 검 피해
    public static final double GOLD_SWORD_DAMAGE = 4.0;
    public static final double DIAMOND_SWORD_DAMAGE = 7.0;
    public static final double HAND_DAMAGE = 1.0;
    /**
     * [TRIDENT][A] 삼지창 근접 피해 9. 바닐라 {@code attack_damage} 수정자 +8 에 플레이어
     * 기본 피해 1 을 더한 값이다(같은 산술로 철 검은 +5 → 6 이 나온다).
     */
    public static final double TRIDENT_DAMAGE = 9.0;
    /** [TRIDENT][A] 삼지창 attack speed 1.1. 바닐라 수정자 -2.9 에 기본 4.0 을 더한 값이다. */
    public static final double TRIDENT_ATTACK_SPEED = 1.1;
    /**
     * [TRIDENT][B] 던진 삼지창의 피해 8. 바닐라 {@code ThrownTrident} 의 기본 피해이며 데이터가
     * 아니라 Java 코드 상수라 1.21.4 성분 덤프에는 없다(근거 등급 B,
     * {@code docs/research/mc-trident-1214.md} §1.3). <b>드라운드 투척과 플레이어 투척이 같은
     * 값</b>이라 두 경로가 이 상수 하나만 읽는다 — 한쪽만 바꾸면 그 자체가 divergence 다.
     */
    public static final int TRIDENT_PROJECTILE_DAMAGE = 8;

    /** MC Java attack speed(초당 공격 횟수). WebCraft 는 10TPS 이므로 풀 충전 틱 = 10/speed. */
    public static final double SWORD_ATTACK_SPEED = 1.6;
    public static final double HAND_ATTACK_SPEED = 4.0;

    /** 검의 10TPS 풀 충전 시간(12.5 MC틱 = 6.25 서버틱). */
    public static final double SWORD_COOLDOWN_TICKS = 10.0 / SWORD_ATTACK_SPEED;
    /** MC 피격 무적 10틱@20TPS = WebCraft 5틱. */
    public static final int HURT_COOLDOWN_TICKS = 5;
    /** MC 기본 넓백 0.4 block/20TPS tick = 8 blocks/s. */
    public static final double KNOCKBACK_HORIZONTAL_BPS = 8.0;
    /** MC 지상 수직 넓백 0.4 block/20TPS tick = 8 blocks/s. */
    public static final double KNOCKBACK_VERTICAL_BPS = 8.0;
    /** Sprint/Knockback +1의 별도 0.5 block/20TPS tick = 10 blocks/s. */
    public static final double KNOCKBACK_BONUS_BPS = 10.0;
    /** 선택 슬롯 아이템 종류에 따른 MC 20포인트 스케일 기본 피해량. */
    public static double meleeDamage(short selectedItemType) {
        return switch (selectedItemType) {
            case SWORD_ITEM -> SWORD_DAMAGE;
            case STONE_SWORD -> STONE_SWORD_DAMAGE;
            case IRON_SWORD -> IRON_SWORD_DAMAGE;
            case GOLD_SWORD -> GOLD_SWORD_DAMAGE;
            case DIAMOND_SWORD -> DIAMOND_SWORD_DAMAGE;
            case COPPER_SWORD -> 5.0;
            case NETHERITE_SWORD -> 8.0;
            case WOOD_AXE -> 7.0;
            case STONE_AXE, IRON_AXE -> 9.0;
            case GOLD_AXE -> 7.0;
            case DIAMOND_AXE -> 9.0;
            case COPPER_AXE -> 8.0;
            case NETHERITE_AXE -> 10.0;
            case WOOD_PICKAXE -> 2.0;
            case STONE_PICKAXE -> 3.0;
            case IRON_PICKAXE -> 4.0;
            case GOLD_PICKAXE -> 2.0;
            case DIAMOND_PICKAXE -> 5.0;
            case COPPER_PICKAXE -> 3.0;
            case NETHERITE_PICKAXE -> 6.0;
            case WOOD_SHOVEL -> 2.5;
            case STONE_SHOVEL -> 3.5;
            case IRON_SHOVEL -> 4.5;
            case GOLD_SHOVEL -> 2.5;
            case DIAMOND_SHOVEL -> 5.5;
            case COPPER_SHOVEL -> 3.5;
            case NETHERITE_SHOVEL -> 6.5;
            // [TRIDENT][A] 바닐라 attack_damage 수정자 +8 에 플레이어 기본 1 을 더해 9 다.
            case TRIDENT -> TRIDENT_DAMAGE;
            case MACE -> MACE_DAMAGE;
            // [SPEAR][B] 창 여섯 티어의 잽 피해는 SpearRules.JAB_DAMAGE 표가 정본이다.
            // switch 라벨은 상수식이어야 하는데 창은 표 색인이라 default 앞에서 갈라낸다.
            default -> PlayerInventory.isSpear(selectedItemType)
                    ? SpearRules.jabDamage(selectedItemType) : HAND_DAMAGE;
        };
    }

    /** 선택 무기의 attack speed. 검 1.6, 도끼 0.8~0.9, 곡괭이 1.2, 삽 1.0, 맨손 4.0. */
    public static double attackSpeed(short selectedItemType) {
        return switch (selectedItemType) {
            case SWORD_ITEM, STONE_SWORD, IRON_SWORD, GOLD_SWORD, DIAMOND_SWORD,
                    COPPER_SWORD, NETHERITE_SWORD -> SWORD_ATTACK_SPEED;
            case WOOD_AXE, STONE_AXE, GOLD_AXE -> 0.8;
            case IRON_AXE -> 0.9;
            case DIAMOND_AXE -> 1.0;
            case COPPER_AXE -> 0.9;
            case NETHERITE_AXE -> 1.0;
            case WOOD_PICKAXE, STONE_PICKAXE, IRON_PICKAXE, GOLD_PICKAXE, DIAMOND_PICKAXE,
                    COPPER_PICKAXE, NETHERITE_PICKAXE -> 1.2;
            case WOOD_SHOVEL, STONE_SHOVEL, IRON_SHOVEL, GOLD_SHOVEL, DIAMOND_SHOVEL,
                    COPPER_SHOVEL, NETHERITE_SHOVEL -> 1.0;
            // [TRIDENT][A] 바닐라 attack_speed 수정자 -2.9 에 기본 4 를 더해 1.1 이다.
            case TRIDENT -> TRIDENT_ATTACK_SPEED;
            case MACE -> MACE_ATTACK_SPEED;
            // [SPEAR][B] 창 여섯 티어의 공속은 SpearRules.ATTACK_SPEED 표가 정본이다.
            default -> PlayerInventory.isSpear(selectedItemType)
                    ? SpearRules.attackSpeed(selectedItemType) : HAND_ATTACK_SPEED;
        };
    }

    /** 10TPS 서버에서 선택 무기가 완전히 충전되는 틱 수. */
    public static double fullCooldownTicks(short selectedItemType) {
        if (selectedItemType == SWORD_ITEM || selectedItemType == STONE_SWORD
                || selectedItemType == IRON_SWORD || selectedItemType == GOLD_SWORD
                || selectedItemType == DIAMOND_SWORD || selectedItemType == COPPER_SWORD
                || selectedItemType == NETHERITE_SWORD) {
            return SWORD_COOLDOWN_TICKS;
        }
        return 10.0 / attackSpeed(selectedItemType);
    }

    /**
     * MC 미완충 피해 배율: 0.2 + ((t+0.25)/T)^2*0.8, [0.2,1] 클램프.
     * 첫 공격은 호출부가 {@code firstAttack=true} 로 전달해 완충(1.0) 처리한다.
     */
    public static double cooldownMultiplier(long elapsedTicks, double fullCooldownTicks,
                                            boolean firstAttack) {
        if (firstAttack) return 1.0;
        double t = Math.max(0L, elapsedTicks);
        double ratio = (t + 0.25) / Math.max(0.000001, fullCooldownTicks);
        double multiplier = 0.2 + ratio * ratio * 0.8;
        return Math.max(0.2, Math.min(1.0, multiplier));
    }

    /** 선택 아이템과 경과 틱으로 계산한 실제(방어도 적용 전) 근접 피해. */
    /**
     * [ENCHANT-WIDE] 나약·힘을 반영한 충전 근접 피해. 바닐라 26.3 {@code Player.attack} 은
     * {@code ATTACK_DAMAGE} 속성값(무기 + 나약 −4/힘 +3 ADD_VALUE, 속성 하한 0)에 공격 세기 배율
     * {@code 0.2 + s²·0.8} 을 곱한다 — 효과 보정은 충전 배율 <b>앞</b>이다. {@code penalty} 는
     * {@link com.gameexpert.engine.effect.StatusEffects#meleeDamagePenalty()}(양수면 감소)다.
     * 정적판 짝은 {@code standaloneEffectiveChargedMeleeDamage}.
     */
    public static double effectiveChargedMeleeDamage(short selectedItemType, long elapsedTicks,
                                                     boolean firstAttack, double penalty) {
        return Math.max(0.0, meleeDamage(selectedItemType) - penalty)
                * cooldownMultiplier(elapsedTicks, fullCooldownTicks(selectedItemType), firstAttack);
    }

    public static double chargedMeleeDamage(short selectedItemType, long elapsedTicks,
                                            boolean firstAttack) {
        return chargedMeleeDamage(selectedItemType, elapsedTicks, firstAttack,
                com.gameexpert.engine.enchant.EnchantmentRules.EMPTY_ENCHANTMENTS);
    }

    /**
     * [SURV-X] 날카로움(Sharpness)까지 반영한 근접 피해. 바닐라대로 충전 배율 뒤·치명타 배율 앞에서
     * 고정 피해를 더한다. 마스크 없이 호출하는 기존 경로는 위의 오버로드가 그대로 처리한다.
     */
    public static double chargedMeleeDamage(short selectedItemType, long elapsedTicks,
                                            boolean firstAttack, long enchantments) {
        double charged = meleeDamage(selectedItemType)
                * cooldownMultiplier(elapsedTicks, fullCooldownTicks(selectedItemType), firstAttack);
        int sharpness = com.gameexpert.engine.enchant.EnchantmentRules.enchantLevel(
                enchantments, com.gameexpert.engine.enchant.EnchantmentRules.SHARPNESS);
        // 적용 대상(검·도끼)이 아닌 아이템에 마스크가 실려 와도 피해를 올리지 않는다(정적판과 동일).
        if (sharpness <= 0 || !com.gameexpert.engine.enchant.EnchantmentRules.heldEffectApplies(
                com.gameexpert.engine.enchant.EnchantmentRules.SHARPNESS, selectedItemType)) {
            return charged;
        }
        return charged
                + com.gameexpert.engine.enchant.EnchantmentRules.sharpnessBonusDamageMilli(sharpness)
                        / (double) com.gameexpert.engine.enchant.EnchantmentRules.MILLI;
    }

    /**
     * [CRIT-FX] 이 타격이 바닐라 {@code magicCrit}(ENCHANTED_HIT 파티클) 조건을 만족하는가.
     *
     * [A] {@code Player#attack} 은 인챈트가 얹은 **추가 피해가 0보다 클 때** 치명타와 무관하게
     * {@code magicCrit(target)} 을 부른다. 이 저장소의 근접 인챈트 보너스는 날카로움 하나이므로
     * {@link #chargedMeleeDamage} 가 보너스를 실제로 더하는 조건과 **같은 판정**을 쓴다 —
     * 피해 계산과 파티클이 갈라지지 않게 하려는 것이다(비무기에 실린 마스크는 둘 다 무시).
     */
    public static boolean magicMeleeBonus(short selectedItemType, long enchantments) {
        int sharpness = com.gameexpert.engine.enchant.EnchantmentRules.enchantLevel(
                enchantments, com.gameexpert.engine.enchant.EnchantmentRules.SHARPNESS);
        if (sharpness <= 0 || !com.gameexpert.engine.enchant.EnchantmentRules.heldEffectApplies(
                com.gameexpert.engine.enchant.EnchantmentRules.SHARPNESS, selectedItemType)) {
            return false;
        }
        return com.gameexpert.engine.enchant.EnchantmentRules.sharpnessBonusDamageMilli(sharpness) > 0;
    }

    /**
     * [PIGLIN-LOOTING] 처치 무기에서 읽는 약탈 레벨. 바닐라
     * {@code EnchantmentHelper.getMobLooting} 은 주손 아이템의 약탈만 본다. 대상(검)이 아닌
     * 아이템에 마스크가 실려 와도 0으로 읽어 정적판과 같은 결정을 낸다.
     */
    public static int lootingLevel(short selectedItemType, long enchantments) {
        if (!com.gameexpert.engine.enchant.EnchantmentRules.heldEffectApplies(
                com.gameexpert.engine.enchant.EnchantmentRules.LOOTING, selectedItemType)) {
            return 0;
        }
        return com.gameexpert.engine.enchant.EnchantmentRules.enchantLevel(
                enchantments, com.gameexpert.engine.enchant.EnchantmentRules.LOOTING);
    }

    /**
     * [ENCHANT-WIDE] 바닐라 {@code Player.getAttackStrengthScale(0.5)}: 마지막 공격 이후 경과를 선택 무기
     * 풀 충전 시간으로 나눈 비율을 [0,1] 로 자른 값. 첫 공격은 1 이다. {@link #cooldownMultiplier} 의
     * {@code 0.2 + ratio^2 * 0.8} 과 같은 비율을 쓴다.
     */
    public static double attackStrengthScale(short selectedItemType, long elapsedTicks,
                                             boolean firstAttack) {
        if (firstAttack) return 1.0;
        double t = Math.max(0L, elapsedTicks);
        double ratio = (t + 0.25) / Math.max(0.000001, fullCooldownTicks(selectedItemType));
        return Math.max(0.0, Math.min(1.0, ratio));
    }

    /**
     * [ENCHANT-WIDE] 근접 인챈트 추가 피해. 26.3 {@code Player.attack} 바이트코드:
     * {@code bonus = strength × (getEnchantedDamage(target, base) - base)} 이고 치명타 배율은 기본 피해에만
     * 곱한 뒤 이 값을 더한다. 날카로움·강타(#undead)·살충(#arthropod)·찌르기(#aquatic)가 여기 모인다.
     */
    public static double enchantmentMeleeBonus(short selectedItemType,
            com.gameexpert.engine.enchant.WideEnchantments enchantments,
            com.gameexpert.engine.mob.MobType target, long elapsedTicks, boolean firstAttack) {
        int milli = com.gameexpert.engine.enchant.EnchantmentRules.meleeEnchantmentBonusMilli(
                selectedItemType, enchantments,
                com.gameexpert.engine.mob.UndeadNeutralityRules.isUndead(target),
                com.gameexpert.engine.enchant.EnchantmentRules.isArthropod(target),
                com.gameexpert.engine.enchant.EnchantmentRules.isAquatic(target));
        if (milli <= 0) return 0.0;
        return attackStrengthScale(selectedItemType, elapsedTicks, firstAttack)
                * milli / com.gameexpert.engine.enchant.EnchantmentRules.MILLI;
    }

    /**
     * MC 방어도/강인도 감소 공식. armor/toughness 는 음수가 될 수 없고, 피해가 0 이하면 0을 반환한다.
     */
    public static double damageAfterArmor(double damage, double armor, double toughness) {
        if (damage <= 0.0) return 0.0;
        double safeArmor = Math.max(0.0, armor);
        double safeToughness = Math.max(0.0, toughness);
        double reductionPoints = Math.min(20.0,
                Math.max(safeArmor / 5.0,
                        safeArmor - 4.0 * damage / (safeToughness + 8.0)));
        return damage * (1.0 - reductionPoints / 25.0);
    }

    /**
     * [MACE] 무기의 {@code armor_effectiveness} 가산치를 반영한 방어 흡수(바닐라
     * {@code CombatRules.getDamageAfterAbsorb}: 효율 {@code points/25} 에 가산치를 더해 [0,1] 로 자른다).
     * 가산치 0 이면 {@link #damageAfterArmor(double, double, double)} 와 같은 값이다.
     */
    public static double damageAfterArmor(double damage, double armor, double toughness,
            double armorEffectivenessDelta) {
        if (armorEffectivenessDelta == 0.0) return damageAfterArmor(damage, armor, toughness);
        if (damage <= 0.0) return 0.0;
        double safeArmor = Math.max(0.0, armor);
        double safeToughness = Math.max(0.0, toughness);
        double reductionPoints = Math.min(20.0,
                Math.max(safeArmor / 5.0,
                        safeArmor - 4.0 * damage / (safeToughness + 8.0)));
        double effectiveness = Math.max(0.0, Math.min(1.0,
                reductionPoints / 25.0 + armorEffectivenessDelta));
        return damage * (1.0 - effectiveness);
    }

    /**
     * [SURV-X] 플레이어 활 화살의 서버 권위 피해. 기본 피해는 당김 정도에 비례하고,
     * 힘(Power)은 바닐라대로 고정량을 더한다. 최소 피해는 1이다.
     */
    public static int arrowDamage(double pull, long enchantments) {
        int power = com.gameexpert.engine.enchant.EnchantmentRules.enchantLevel(
                enchantments, com.gameexpert.engine.enchant.EnchantmentRules.POWER);
        double base = 9.0 * pull;
        if (power > 0) {
            base += com.gameexpert.engine.enchant.EnchantmentRules.powerBonusDamageMilli(power)
                    / (double) com.gameexpert.engine.enchant.EnchantmentRules.MILLI;
        }
        return Math.max(1, (int) Math.round(base));
    }

    /**
     * [MOB-EQUIP] 몹 활 화살의 피해. 해골 계열 고유 활의 힘(Power)이 {@link #arrowDamage} 와 같은
     * {@code 0.5·n + 0.5} 를 종별 기본 화살 피해에 더하고 반올림한다(최소 1). 힘이 없으면 기본값 그대로.
     */
    public static int mobArrowDamage(int baseDamage,
            com.gameexpert.engine.enchant.WideEnchantments bow) {
        int power = bow.level(com.gameexpert.engine.enchant.EnchantmentRules.POWER);
        if (power <= 0) return baseDamage;
        return Math.max(1, (int) Math.round(baseDamage
                + com.gameexpert.engine.enchant.EnchantmentRules.powerBonusDamageMilli(power)
                        / (double) com.gameexpert.engine.enchant.EnchantmentRules.MILLI));
    }

    public static boolean withinAuthorityReach(double px, double py, double pz,
            boolean crouching, double mx, double my, double mz, double width, double height) {
        return PlayerInteractionRules.canInteractWithEntity(
                px, py, pz, crouching, mx, my, mz, width, height);
    }

}
