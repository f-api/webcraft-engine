package com.gameexpert.engine.mob;

import com.gameexpert.engine.Difficulty;
import com.gameexpert.engine.effect.StatusEffect;
import com.gameexpert.engine.effect.StatusEffects;
import com.gameexpert.engine.inventory.PlayerInventory;

/**
 * 몹이 부여하는 상태이상의 수치·조건(순수 함수). 지속시간은 전부 10 TPS 서버 틱입니다.
 *
 * <p>양판(Java·정적판)이 같은 표를 쓰므로 값을 바꿀 때 정적판
 * {@code StandaloneMobEffects.ts} 도 함께 갱신해야 합니다.
 */
public final class MobEffectRules {

    private MobEffectRules() {
    }

    /** 동굴거미 근접 독(normal 기준 7초). 난이도 분기는 {@link Difficulty#caveSpiderPoisonTicks()}. */
    public static final int CAVE_SPIDER_POISON_TICKS = 70;
    /** 스트레이 화살 감속 30초(normal 기준). */
    public static final int STRAY_SLOWNESS_TICKS = 300;
    /** 보그드 화살 독 5초(normal 기준). */
    public static final int BOGGED_POISON_TICKS = 50;
    /**
     * [PARCHED-FAMILY] 파치드 화살 나약함 30초(normal 기준). 값의 정본은
     * {@link ParchedRules#WEAKNESS_TICKS} 이고 여기는 스트레이·보그드와 같은 자리에 두는
     * 사본이다(두 상수가 갈리면 {@code ParchedRulesTest} 가 잡는다).
     */
    public static final int PARCHED_WEAKNESS_TICKS = ParchedRules.WEAKNESS_TICKS;
    /** 마녀 감속 물약 15초. */
    public static final int WITCH_SLOWNESS_TICKS = 150;
    /** 마녀 독 물약 12초. */
    public static final int WITCH_POISON_TICKS = 120;
    /** 마녀 나약 물약 15초. */
    public static final int WITCH_WEAKNESS_TICKS = 150;
    /** 마녀 나약 물약 선택 확률(≤8블록). */
    public static final double WITCH_WEAKNESS_CHANCE = 0.25;
    /** 마녀 물약 선택 거리 경계(블록). */
    public static final double WITCH_POTION_RANGE = 8.0;
    /** 마녀 독 물약 선택 체력 하한. */
    public static final int WITCH_POISON_HEALTH = 8;
    /** 마녀가 받는 마법(물약) 피해 저항률. */
    public static final double WITCH_MAGIC_RESISTANCE = 0.85;
    /** 스플래시 물약 효과 반경(블록). */
    public static final double SPLASH_RADIUS = 4.0;
    /** WebCraft 그물사의 근접 그물: 짧은 감속 II로 진형을 갈라놓는다. */
    public static final int WEB_TRAPPER_SLOWNESS_TICKS = 40;

    // ── 환술사(Illusioner) ────────────────────────────────────────────
    // 바닐라 IllusionerBlindnessSpellGoal / IllusionerMirrorSpellGoal 의 MC 틱 값을 그대로 쓴다.
    // 지속 필드는 MC 틱(20 TPS)으로 저장하고 ProjectileEffect 만 10 TPS 서버 틱으로 환산한다.

    /** 실명 지속 400 MC 틱(20초). */
    public static final int ILLUSIONER_BLINDNESS_MC_TICKS = 400;
    /** 실명 시전 간격 180 MC 틱. 같은 표적을 다시 실명시키지 않는 바닐라 lastTargetId 규칙과 함께 쓴다. */
    public static final int ILLUSIONER_BLINDNESS_INTERVAL_MC_TICKS = 180;
    /** 자기 투명화 지속 1,200 MC 틱(60초). */
    public static final int ILLUSIONER_INVISIBILITY_MC_TICKS = 1_200;
    /** 거울상 주문 시전 간격 340 MC 틱. */
    public static final int ILLUSIONER_MIRROR_INTERVAL_MC_TICKS = 340;
    /** 주문 시전 20 MC 틱. 이 동안 활 공격이 눌린다(바닐라 goal 우선순위 4 &lt; 6). */
    public static final int ILLUSIONER_CAST_MC_TICKS = 20;
    /** WebCraft 계약: 바닐라 실명 주문을 기존 팁 화살 경로에 실어 나른다. */
    public static final ProjectileEffect ILLUSIONER_BLINDNESS_ARROW = new ProjectileEffect(
            StatusEffect.BLINDNESS, 0,
            ILLUSIONER_BLINDNESS_MC_TICKS / StatusEffects.MC_TICKS_PER_SERVER_TICK);

    public static final ProjectileEffect CAVE_SPIDER_POISON =
            new ProjectileEffect(StatusEffect.POISON, 0, CAVE_SPIDER_POISON_TICKS);
    public static final ProjectileEffect STRAY_ARROW =
            new ProjectileEffect(StatusEffect.SLOWNESS, 0, STRAY_SLOWNESS_TICKS);
    public static final ProjectileEffect BOGGED_ARROW =
            new ProjectileEffect(StatusEffect.POISON, 0, BOGGED_POISON_TICKS);
    /** [PARCHED-FAMILY] 파치드의 나약함 화살. 스트레이·보그드와 같은 팁 화살 경로를 탄다. */
    public static final ProjectileEffect PARCHED_ARROW =
            new ProjectileEffect(StatusEffect.WEAKNESS, 0, PARCHED_WEAKNESS_TICKS);
    public static final ProjectileEffect WITCH_SLOWNESS =
            new ProjectileEffect(StatusEffect.SLOWNESS, 0, WITCH_SLOWNESS_TICKS);
    public static final ProjectileEffect WITCH_POISON =
            new ProjectileEffect(StatusEffect.POISON, 0, WITCH_POISON_TICKS);
    public static final ProjectileEffect WITCH_WEAKNESS =
            new ProjectileEffect(StatusEffect.WEAKNESS, 0, WITCH_WEAKNESS_TICKS);
    /** 고통 물약. 앰프 0 = 6점(바닐라 6 &lt;&lt; amplifier). */
    public static final ProjectileEffect WITCH_HARMING =
            new ProjectileEffect(StatusEffect.INSTANT_DAMAGE, 0, 0);
    public static final ProjectileEffect WEB_TRAPPER_SLOWNESS =
            new ProjectileEffect(StatusEffect.SLOWNESS, 1, WEB_TRAPPER_SLOWNESS_TICKS);

    /**
     * 난이도별 동굴거미 근접 독. easy 는 독을 부여하지 않으므로 null 이다.
     * 바닐라 {@code CaveSpider#doHurtTarget}(easy 없음/normal 7초/hard 15초).
     */
    public static ProjectileEffect caveSpiderPoison(Difficulty difficulty) {
        int ticks = difficulty.caveSpiderPoisonTicks();
        if (ticks <= 0) return null;
        if (ticks == CAVE_SPIDER_POISON_TICKS) return CAVE_SPIDER_POISON;
        return new ProjectileEffect(StatusEffect.POISON, 0, ticks);
    }

    /** 난이도별 스트레이 화살 감속. */
    public static ProjectileEffect strayArrow(Difficulty difficulty) {
        int ticks = difficulty.strayArrowSlownessTicks();
        return ticks == STRAY_SLOWNESS_TICKS
                ? STRAY_ARROW
                : new ProjectileEffect(StatusEffect.SLOWNESS, 0, ticks);
    }

    /** 난이도별 보그드 화살 독. */
    public static ProjectileEffect boggedArrow(Difficulty difficulty) {
        int ticks = difficulty.boggedArrowPoisonTicks();
        return ticks == BOGGED_POISON_TICKS
                ? BOGGED_ARROW
                : new ProjectileEffect(StatusEffect.POISON, 0, ticks);
    }

    /** [PARCHED-FAMILY] 난이도별 파치드 화살 나약함. */
    public static ProjectileEffect parchedArrow(Difficulty difficulty) {
        int ticks = difficulty.parchedWeaknessArrowTicks();
        return ticks == PARCHED_WEAKNESS_TICKS
                ? PARCHED_ARROW
                : new ProjectileEffect(StatusEffect.WEAKNESS, 0, ticks);
    }

    /**
     * 마녀의 투척 물약 선택(바닐라 WitchAttackGoal 순서).
     * 8블록 초과이며 감속이 없으면 감속, 표적 HP가 8 이상이며 독이 없으면 독,
     * 8블록 이하이며 나약이 없고 25% 확률이면 나약, 그 외 고통.
     *
     * @param distance   마녀→표적 3D 거리
     * @param targetHealth 표적의 현재 체력
     * @param roll       0~1 난수(나약 확률 판정)
     */
    public static ProjectileEffect selectWitchPotion(double distance, int targetHealth,
            boolean targetHasSlowness, boolean targetHasPoison, boolean targetHasWeakness,
            double roll) {
        if (!targetHasSlowness && distance > WITCH_POTION_RANGE) return WITCH_SLOWNESS;
        if (targetHealth >= WITCH_POISON_HEALTH && !targetHasPoison) return WITCH_POISON;
        if (distance <= WITCH_POTION_RANGE && !targetHasWeakness && roll < WITCH_WEAKNESS_CHANCE) {
            return WITCH_WEAKNESS;
        }
        return WITCH_HARMING;
    }

    /**
     * [POTION-COLOR] 마녀가 던지는 물약의 투척 물약 아이템(감속·독·나약·고통 기본형). 투사체가 이
     * 아이템을 실어야 클라이언트가 바닐라처럼 물약 색으로 병을 칠한다. 그 밖의 효과는 0.
     * standalone {@code WITCH_SPLASH_ITEM_BY_EFFECT} 와 같은 표다.
     */
    public static short witchSplashItem(ProjectileEffect potion) {
        if (potion == null) return 0;
        return switch (potion.effect()) {
            case SLOWNESS -> PlayerInventory.SPLASH_POTION_SLOWNESS;
            case POISON -> PlayerInventory.SPLASH_POTION_POISON;
            case WEAKNESS -> PlayerInventory.SPLASH_POTION_WEAKNESS;
            case INSTANT_DAMAGE -> PlayerInventory.SPLASH_POTION_HARMING;
            default -> (short) 0;
        };
    }

    /**
     * 착탄점에서 거리 d 만큼 떨어진 대상의 스플래시 감쇠 계수(1.0 = 착탄점, 0 = 반경 밖).
     * 바닐라 ThrownPotion 과 같은 선형 감쇠입니다.
     */
    public static double splashFactor(double distance) {
        if (distance >= SPLASH_RADIUS) return 0.0;
        return 1.0 - distance / SPLASH_RADIUS;
    }
}
