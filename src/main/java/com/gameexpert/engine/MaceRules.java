package com.gameexpert.engine;

import com.gameexpert.engine.inventory.PlayerInventory;

/**
 * [MACE] 철퇴 낙하 강타(smash attack)의 순수 규칙. 정본은 핀 26.3-snapshot-7 jar 의
 * {@code net.minecraft.world.item.MaceItem}(javap) 과 {@code data/minecraft/enchantment/}
 * {@code density.json} · {@code breach.json} · {@code wind_burst.json} 이다. client
 * {@code StandaloneMaceRules.ts} 가 같은 식·같은 벡터를 가진 사본이다(파리티 테스트 양쪽).
 *
 * <p>순서(바닐라 {@code Player.attack} → {@code itemAttackInteraction}):
 * <ol>
 *   <li>기본 피해 × 충전 배율에 {@link #smashBonusDamage} 를 더하고, 그 합에 치명타 ×1.5 를 건다
 *       ({@code getAttackDamageBonus} 는 치명타 곱 앞에서 더해진다).</li>
 *   <li>피해가 받아들여지면 {@code MaceItem.hurtEnemy}: 공격자 수직 속도 0.01 블록/틱, 소리
 *       ({@link #smashSound}), 반경 3.5 넉백({@link #knockbackPower}).</li>
 *   <li>{@code doPostAttackEffects}: 돌풍(wind_burst) 폭발 — 요건은 낙하 거리 ≥ 1.5 이고 비행 중이
 *       아님({@link #windBurstApplies}).</li>
 *   <li>{@code postHurtEnemy}: 강타가 성립했으면 낙하 거리를 0 으로 되돌린다(그 낙하 피해 면제).</li>
 * </ol>
 */
public final class MaceRules {

    private MaceRules() {}

    /** {@code MaceItem.SMASH_ATTACK_FALL_THRESHOLD = 1.5f}(초과여야 강타). */
    public static final double SMASH_ATTACK_FALL_THRESHOLD = 1.5;
    /** {@code MaceItem.SMASH_ATTACK_HEAVY_THRESHOLD = 5.0f}(초과면 무거운 소리·넉백 2배). */
    public static final double SMASH_ATTACK_HEAVY_THRESHOLD = 5.0;
    /** {@code MaceItem.SMASH_ATTACK_KNOCKBACK_RADIUS = 3.5f}. */
    public static final double SMASH_ATTACK_KNOCKBACK_RADIUS = 3.5;
    /** {@code MaceItem.SMASH_ATTACK_KNOCKBACK_POWER = 0.7f} 를 double 로 넓힌 값(0.699999988079071). */
    public static final double SMASH_ATTACK_KNOCKBACK_POWER = (double) 0.7f;
    /** {@code MaceItem.hurtEnemy}: 공격자 수직 속도를 0.01 블록/MC틱으로 둔다({@code Vec3.with(Y, 0.01)}). */
    public static final double ATTACKER_VERTICAL_VELOCITY_PER_MC_TICK = (double) 0.01f;
    /** {@code ServerLevel.levelEvent(2013, target.getOnPos(), 750)} — 강타 입자(바닐라 LevelEvent). */
    public static final int SMASH_LEVEL_EVENT = 2013;
    /** 위 levelEvent 의 data(입자 수 750). */
    public static final int SMASH_LEVEL_EVENT_DATA = 750;
    /** wind_burst.json {@code radius 3.5}: {@code ServerLevel.explode} 의 폭발 세기(반경은 ×2). */
    public static final double WIND_BURST_POWER = (double) 3.5f;
    /** wind_burst.json 요건 {@code movement.fall_distance.min 1.5}(이상). */
    public static final double WIND_BURST_MIN_FALL_DISTANCE = 1.5;

    /** 철퇴를 쥐었는가. */
    public static boolean isMace(short weapon) {
        return weapon == PlayerInventory.MACE;
    }

    /**
     * {@code MaceItem.canSmashAttack}: {@code fallDistance > 1.5 && !isFallFlying()}. 강타 여부는 철퇴
     * 아이템의 {@code getAttackDamageBonus}·{@code hurtEnemy}·{@code postHurtEnemy} 가 부르므로 무기가
     * 철퇴일 때만 의미가 있다.
     */
    public static boolean canSmashAttack(short weapon, double fallDistance, boolean gliding) {
        return isMace(weapon) && fallDistance > SMASH_ATTACK_FALL_THRESHOLD && !gliding;
    }

    /**
     * 밀도(density) 한 블록당 추가 피해. density.json
     * {@code smash_damage_per_fallen_block: add linear(base 0.5, per_level_above_first 0.5)} —
     * {@code LevelBasedValue.Linear} 의 float 산술 그대로다.
     */
    public static float densityDamagePerBlock(int densityLevel) {
        if (densityLevel <= 0) return 0.0f;
        return 0.5f + 0.5f * (densityLevel - 1);
    }

    /**
     * {@code MaceItem.getAttackDamageBonus}(강타가 성립할 때만 호출): 낙하 거리 fd 에 대해
     * {@code fd ≤ 3 → 4·fd}, {@code fd ≤ 8 → 12 + 2·(fd − 3)}, 그 밖 {@code 22 + fd − 8} 에
     * {@code EnchantmentHelper.modifyFallBasedDamage(…, 0) · fd}(밀도)를 더해 float 로 자른다.
     */
    public static double smashBonusDamage(double fallDistance, int densityLevel) {
        double base;
        if (fallDistance <= 3.0) {
            base = 4.0 * fallDistance;
        } else if (fallDistance <= 8.0) {
            base = 12.0 + 2.0 * (fallDistance - 3.0);
        } else {
            base = 22.0 + fallDistance - 8.0;
        }
        return (float) (base + (double) densityDamagePerBlock(densityLevel) * fallDistance);
    }

    /**
     * 파괴(breach)의 방어 효율 가산치. breach.json
     * {@code armor_effectiveness: add linear(base -0.15, per_level_above_first -0.15)}. 음수다.
     */
    public static float breachArmorEffectivenessDelta(int breachLevel) {
        if (breachLevel <= 0) return 0.0f;
        return -0.15f + -0.15f * (breachLevel - 1);
    }

    /**
     * 강타 소리 kind. {@code hurtEnemy}: 대상이 땅 위면 낙하 거리 &gt; 5 일 때
     * {@code MACE_SMASH_GROUND_HEAVY}, 아니면 {@code MACE_SMASH_GROUND}; 공중이면 {@code MACE_SMASH_AIR}.
     * volume 1 · pitch 1, 공격자 위치.
     */
    public static String smashSound(boolean targetOnGround, double fallDistance) {
        if (!targetOnGround) return "mace_smash_air";
        return fallDistance > SMASH_ATTACK_HEAVY_THRESHOLD ? "mace_smash_ground_heavy"
                : "mace_smash_ground";
    }

    /**
     * {@code MaceItem.getKnockbackPower}: {@code (3.5 − |d|) · 0.7 · (공격자 낙하 > 5 ? 2 : 1)
     * · (1 − knockback_resistance)}. d 는 대상 발 위치에서 주변 개체 발 위치까지의 3차원 벡터다.
     * 0 이하면 밀지 않는다.
     */
    public static double knockbackPower(double distance, double attackerFallDistance,
            double knockbackResistance) {
        return (SMASH_ATTACK_KNOCKBACK_RADIUS - distance) * SMASH_ATTACK_KNOCKBACK_POWER
                * (attackerFallDistance > SMASH_ATTACK_HEAVY_THRESHOLD ? 2 : 1)
                * (1.0 - knockbackResistance);
    }

    /**
     * {@code MaceItem.knockbackPredicate} 의 거리 조건: {@code target.distanceToSqr(e) ≤ 3.5²}.
     * (나머지 조건 — 관전자·공격자·대상 제외, 공격자의 동맹(공격자가 주인인 길든 늑대·고양이·앵무새·
     * 노틸러스) 제외, 대상이 주인인 길든 동물 제외, 갑옷 거치대 마커 제외, 창작 비행 플레이어 제외 — 는
     * 호출부가 개체 종류로 판정한다.)
     */
    public static boolean withinKnockbackRange(double distanceSquared) {
        return distanceSquared <= Math.pow(SMASH_ATTACK_KNOCKBACK_RADIUS, 2.0);
    }

    /**
     * 플레이어의 {@code knockback_resistance}. 이 저장소 플레이어 방어구 중 속성을 가진 것은 네더라이트
     * 방어구({@code ArmorMaterials.NETHERITE} knockbackResistance 0.1f, 조각마다 ADD_VALUE)뿐이다.
     * 속성 값은 [0, 1] 로 잘린다.
     */
    public static double playerKnockbackResistance(short helmet, short chestplate, short leggings,
            short boots) {
        double total = 0.0;
        for (short piece : new short[] {helmet, chestplate, leggings, boots}) {
            if (piece >= PlayerInventory.NETHERITE_HELMET && piece <= PlayerInventory.NETHERITE_BOOTS) {
                total += (double) 0.1f;
            }
        }
        return Math.max(0.0, Math.min(1.0, total));
    }

    /**
     * 강타 넉백을 받지 않는 공격자 동맹인가: 바닐라 {@code TamableAnimal.considersEntityAsAlly} 는 길든
     * 개체의 주인을 동맹으로 보고, {@code Entity.isAlliedTo} 는 양방향이다. 이 저장소의
     * {@code TamableAnimal} 계열은 늑대·고양이·앵무새·노틸러스·좀비 노틸러스다.
     */
    public static boolean isTamableAnimal(com.gameexpert.engine.mob.MobType type) {
        return switch (type) {
            case WOLF, CAT, PARROT, NAUTILUS, ZOMBIE_NAUTILUS -> true;
            default -> false;
        };
    }

    /**
     * 돌풍(wind_burst) 효과가 도는가: 레벨 &gt; 0, 직접 가해자의 낙하 거리 ≥ 1.5, 비행({@code is_flying})
     * 이 아님. 이 저장소 권위에는 창작 비행 상태가 없어 비행 조건은 언제나 거짓이다. 활공은 요건에 없다.
     */
    public static boolean windBurstApplies(int windBurstLevel, double fallDistance) {
        return windBurstLevel > 0 && fallDistance >= WIND_BURST_MIN_FALL_DISTANCE;
    }

    /**
     * 돌풍의 넉백 배율. wind_burst.json {@code knockback_multiplier: lookup [1.2, 1.75, 2.2],
     * fallback linear(base 1.5, per_level_above_first 0.35)} — {@code LevelBasedValue.Lookup} 은
     * 레벨 1..n 을 표에서, 그 밖은 fallback 을 float 로 계산한다.
     */
    public static float windBurstKnockbackMultiplier(int windBurstLevel) {
        return switch (windBurstLevel) {
            case 1 -> 1.2f;
            case 2 -> 1.75f;
            case 3 -> 2.2f;
            default -> 1.5f + 0.35f * (windBurstLevel - 1);
        };
    }
}
