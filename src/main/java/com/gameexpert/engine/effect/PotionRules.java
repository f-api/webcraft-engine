package com.gameexpert.engine.effect;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.mob.ProjectileEffect;

/**
 * [POTION] 플레이어 물약의 효과·지속 정본(순수 함수). 마시는 물약과 투척 물약이 같은 표를
 * 쓰고, 투척은 바닐라와 같이 지속의 3/4 만 준다.
 *
 * <p>지속시간은 전부 10 TPS 서버 틱이다(바닐라 초 × 10). 부여 자체는 마녀 물약과 완전히 같은
 * {@link ProjectileEffect} · {@code MobEffectRules.splashFactor} 경로를 타므로 감쇠·적용 규칙에
 * 사본이 생기지 않는다.
 *
 * <p>양판(Java·정적판)이 같은 표를 쓰므로 값을 바꿀 때 정적판 {@code StandalonePotions.ts} 도
 * 함께 갱신해야 한다({@code StandalonePotionParity.test.ts} 가 두 표를 대조한다).
 */
public final class PotionRules {

    private PotionRules() {
    }

    /** 신속의 물약 3:00(바닐라 speed base). */
    public static final int SWIFTNESS_TICKS = 1_800;
    /** 독 물약 0:45. */
    public static final int POISON_TICKS = 450;
    /** 감속의 물약 1:30. */
    public static final int SLOWNESS_TICKS = 900;
    /** 나약함의 물약 1:30. */
    public static final int WEAKNESS_TICKS = 900;

    // ── [POTION-UPGRADE] 강화 물약(MC Java 1.21.4 `PotionBrewing` · `Potions`). 강화는 둘뿐이고
    // 한 병에 같이 걸 수 없다: 레드스톤 = 연장, 발광석 가루 = II 등급. 바닐라 표에서
    // **나약함은 연장만**, **고통은 II 만** 있고 신속·독·감속만 둘 다 있다.
    //
    // 아래 숫자는 전부 바닐라 등록 지속(20 TPS 틱)의 절반이다 — 이 저장소는 10 TPS 서버 틱을
    // 쓰므로 `바닐라 틱 / 2 = 바닐라 초 × 10` 이 기존 다섯 종과 같은 환산 규약이다
    // (SWIFTNESS 3600/2=1800 · POISON 900/2=450 · SLOWNESS 1800/2=900 · WEAKNESS 1800/2=900). ──
    /** 신속의 물약 (연장) 8:00 — 바닐라 LONG_SWIFTNESS 9600 틱. */
    public static final int SWIFTNESS_LONG_TICKS = 4_800;
    /** 독 물약 (연장) 1:30 — 바닐라 LONG_POISON 1800 틱. */
    public static final int POISON_LONG_TICKS = 900;
    /** 감속의 물약 (연장) 4:00 — 바닐라 LONG_SLOWNESS 4800 틱. */
    public static final int SLOWNESS_LONG_TICKS = 2_400;
    /** 나약함의 물약 (연장) 4:00 — 바닐라 LONG_WEAKNESS 4800 틱. */
    public static final int WEAKNESS_LONG_TICKS = 2_400;
    // ── [BRIMSTONE] 화염 저항. 바닐라 `Potions.FIRE_RESISTANCE` 3:00(3600 틱) ·
    // `LONG_FIRE_RESISTANCE` 8:00(9600 틱)이고 II 등급은 바닐라에도 없다(지속만 강화된다).
    // 위 다섯 종과 같은 환산 규약(바닐라 틱 / 2 = 바닐라 초 × 10)을 그대로 쓴다. ──
    /** 화염 저항 물약 3:00 — 바닐라 FIRE_RESISTANCE 3600 틱. */
    public static final int FIRE_RESISTANCE_TICKS = 1_800;
    /** 화염 저항 물약 (연장) 8:00 — 바닐라 LONG_FIRE_RESISTANCE 9600 틱. */
    public static final int FIRE_RESISTANCE_LONG_TICKS = 4_800;
    // ── [POTION-GAP] 힘 · 수중 호흡 · 도약 · 야간 투시. 바닐라 `Potions` 등록부의 지속을
    // 위와 같은 환산 규약(바닐라 틱 / 2 = 바닐라 초 × 10)으로 옮긴 값이다. 힘·도약은 II 가
    // 있고(발광석 가루) 수중 호흡·야간 투시는 바닐라에도 II 가 없어 지속만 강화된다. ──
    /** 힘의 물약 3:00 — 바닐라 STRENGTH 3600 틱. */
    public static final int STRENGTH_TICKS = 1_800;
    /** 힘의 물약 (연장) 8:00 — 바닐라 LONG_STRENGTH 9600 틱. */
    public static final int STRENGTH_LONG_TICKS = 4_800;
    /** 힘의 물약 II 1:30 — 바닐라 STRONG_STRENGTH 1800 틱. */
    public static final int STRENGTH_II_TICKS = 900;
    /** 수중 호흡 물약 3:00 — 바닐라 WATER_BREATHING 3600 틱. */
    public static final int WATER_BREATHING_TICKS = 1_800;
    /** 수중 호흡 물약 (연장) 8:00 — 바닐라 LONG_WATER_BREATHING 9600 틱. */
    public static final int WATER_BREATHING_LONG_TICKS = 4_800;
    /** 도약의 물약 3:00 — 바닐라 LEAPING 3600 틱. */
    public static final int LEAPING_TICKS = 1_800;
    /** 도약의 물약 (연장) 8:00 — 바닐라 LONG_LEAPING 9600 틱. */
    public static final int LEAPING_LONG_TICKS = 4_800;
    /** 도약의 물약 II 1:30 — 바닐라 STRONG_LEAPING 1800 틱. */
    public static final int LEAPING_II_TICKS = 900;
    /** 야간 투시 물약 3:00 — 바닐라 NIGHT_VISION 3600 틱. */
    public static final int NIGHT_VISION_TICKS = 1_800;
    /** 야간 투시 물약 (연장) 8:00 — 바닐라 LONG_NIGHT_VISION 9600 틱. */
    public static final int NIGHT_VISION_LONG_TICKS = 4_800;
    /** 신속의 물약 II 1:30 — 바닐라 STRONG_SWIFTNESS 1800 틱. */
    public static final int SWIFTNESS_II_TICKS = 900;
    /** 독 물약 II 0:21 — 바닐라 STRONG_POISON 432 틱. */
    public static final int POISON_II_TICKS = 216;
    /** 감속의 물약 II 0:20 — 바닐라 STRONG_SLOWNESS 400 틱. */
    public static final int SLOWNESS_II_TICKS = 200;
    /**
     * II 등급의 증폭. 바닐라 {@code registerStrong} 은 증폭 1 을 주지만
     * STRONG_SLOWNESS 만 예외로 증폭 3(감속 IV)이다.
     */
    public static final int II_AMPLIFIER = 1;
    /** 감속 II 물약의 증폭. 바닐라 STRONG_SLOWNESS 는 감속 IV 라 3 이다. */
    public static final int SLOWNESS_II_AMPLIFIER = 3;

    // ── [TRIAL-GAP] 재생의 물약과 잔류형 물약 · 효과 화살(26.3-snapshot-7 javap). 지속은 전부
    // MC 틱으로 적고 서버 틱으로 옮길 때만 2 로 나눈다(위 표와 같은 환산 규약).
    //   Potions.<clinit>: regeneration = REGENERATION 900 · slow_falling = SLOW_FALLING 1800 ·
    //   wind_charged/weaving/oozing/infested = 각 효과 3600 · strength = STRENGTH 3600 ·
    //   swiftness = SPEED 3600 · poison = POISON 900 · strong_slowness = SLOWNESS 400 앰프 3.
    //   Items.<clinit>: lingering_potion 의 potion_duration_scale 0.25 · tipped_arrow 0.125.
    //   MobEffectInstance.withScaledDuration: max(1, floor(지속 × 배율)). ──
    /** 재생의 물약 0:45 — 바닐라 Potions.REGENERATION 900 MC 틱. */
    public static final int REGENERATION_MC_TICKS = 900;
    public static final int REGENERATION_TICKS = REGENERATION_MC_TICKS / 2;
    public static final int SLOW_FALLING_MC_TICKS = 1_800;
    /** 돌풍 충전 · 거미줄 · 점액 · 벌레 먹음 물약의 공통 지속(바닐라 3600 MC 틱). */
    public static final int TRIAL_EFFECT_POTION_MC_TICKS = 3_600;
    public static final int STRENGTH_MC_TICKS = 3_600;
    public static final int SWIFTNESS_MC_TICKS = 3_600;
    public static final int POISON_MC_TICKS = 900;
    public static final int STRONG_SLOWNESS_MC_TICKS = 400;
    /** 잔류형 물약 {@code potion_duration_scale}. */
    public static final float LINGERING_DURATION_SCALE = 0.25f;
    /** 효과 화살 {@code potion_duration_scale}. */
    public static final float TIPPED_ARROW_DURATION_SCALE = 0.125f;

    /** 바닐라 {@code withScaledDuration}: {@code max(1, floor(mcTicks × scale))} MC 틱. */
    public static int scaledMcTicks(int mcTicks, float scale) {
        return Math.max(1, (int) Math.floor(mcTicks * scale));
    }

    /** MC 틱 지속을 10 TPS 서버 틱 효과로 옮긴다. 이 표의 값은 전부 짝수 MC 틱이다. */
    private static ProjectileEffect mcEffect(StatusEffect effect, int amplifier, int mcTicks) {
        return new ProjectileEffect(effect, amplifier, Math.max(1, mcTicks / 2));
    }

    /**
     * 잔류형 물약 한 병이 효과 구름에 싣는 효과(구름의 {@code potion_duration_scale} 0.25 를
     * 이미 곱한 값). 잔류형 물약이 아니면 null.
     */
    public static ProjectileEffect lingeringEffect(short itemType) {
        int unscaled;
        StatusEffect effect;
        if (itemType == PlayerInventory.LINGERING_POTION_WIND_CHARGED) {
            effect = StatusEffect.WIND_CHARGED; unscaled = TRIAL_EFFECT_POTION_MC_TICKS;
        } else if (itemType == PlayerInventory.LINGERING_POTION_OOZING) {
            effect = StatusEffect.OOZING; unscaled = TRIAL_EFFECT_POTION_MC_TICKS;
        } else if (itemType == PlayerInventory.LINGERING_POTION_WEAVING) {
            effect = StatusEffect.WEAVING; unscaled = TRIAL_EFFECT_POTION_MC_TICKS;
        } else if (itemType == PlayerInventory.LINGERING_POTION_INFESTED) {
            effect = StatusEffect.INFESTED; unscaled = TRIAL_EFFECT_POTION_MC_TICKS;
        } else if (itemType == PlayerInventory.LINGERING_POTION_STRENGTH) {
            effect = StatusEffect.STRENGTH; unscaled = STRENGTH_MC_TICKS;
        } else if (itemType == PlayerInventory.LINGERING_POTION_SWIFTNESS) {
            effect = StatusEffect.SPEED; unscaled = SWIFTNESS_MC_TICKS;
        } else if (itemType == PlayerInventory.LINGERING_POTION_SLOW_FALLING) {
            effect = StatusEffect.SLOW_FALLING; unscaled = SLOW_FALLING_MC_TICKS;
        } else {
            return null;
        }
        return mcEffect(effect, 0, scaledMcTicks(unscaled, LINGERING_DURATION_SCALE));
    }

    /** [GLOWING] {@code SpectralArrow.duration} 기본값(javap sipush 200): 명중한 개체의 발광 MC 틱. */
    public static final int SPECTRAL_ARROW_GLOWING_MC_TICKS = 200;

    /**
     * [GLOWING] 활·석궁이 쏜 화살이 명중에 싣는 효과: 효과 화살은 {@link #tippedArrowEffect}, 분광 화살은
     * {@code SpectralArrow#doPostHurtEffects} 의 발광 200 MC 틱(앰프 0). 그 밖의 화살은 null.
     */
    public static ProjectileEffect ammoEffect(short itemType) {
        if (itemType == PlayerInventory.SPECTRAL_ARROW) {
            return mcEffect(StatusEffect.GLOWING, 0, SPECTRAL_ARROW_GLOWING_MC_TICKS);
        }
        return tippedArrowEffect(itemType);
    }

    /** 효과 화살이 명중에 싣는 효과(배율 0.125 적용). 효과 화살이 아니면 null. */
    public static ProjectileEffect tippedArrowEffect(short itemType) {
        if (itemType == PlayerInventory.TIPPED_ARROW_POISON) {
            return mcEffect(StatusEffect.POISON, 0,
                    scaledMcTicks(POISON_MC_TICKS, TIPPED_ARROW_DURATION_SCALE));
        }
        if (itemType == PlayerInventory.TIPPED_ARROW_STRONG_SLOWNESS) {
            return mcEffect(StatusEffect.SLOWNESS, SLOWNESS_II_AMPLIFIER,
                    scaledMcTicks(STRONG_SLOWNESS_MC_TICKS, TIPPED_ARROW_DURATION_SCALE));
        }
        return null;
    }

    /** 바닐라 {@code PotionContents.BASE_POTION_COLOR} -13083194(0xFF385DC6) — 효과 없는 물약. */
    public static final int BASE_POTION_COLOR = 0x385DC6;

    /**
     * 바닐라 {@code PotionContents.getColor}: 효과 하나짜리 물약의 색은 그 효과의 색이다
     * ({@code MobEffects.<clinit>}). 마시는 물약 · 투척 물약(대응하는 마시는 물약의 효과) · 잔류형 물약 ·
     * 효과 화살을 모두 다루고, 효과가 없으면 {@link #BASE_POTION_COLOR} 다. 클라이언트 아이콘
     * {@code potionTintColor} · standalone {@code standalonePotionColor} 와 같은 판정이다.
     */
    public static int potionColor(short itemType) {
        ProjectileEffect effect = drinkEffect(itemType);
        if (effect == null) effect = drinkEffect(drinkableForSplash(itemType));
        if (effect == null) effect = lingeringEffect(itemType);
        if (effect == null) effect = tippedArrowEffect(itemType);
        if (effect == null) return BASE_POTION_COLOR;
        return effectColor(effect.effect());
    }

    /**
     * {@code MobEffects.<clinit>} 의 효과 색(RGB). 핀 jar 에서 생성한 클라이언트
     * {@code itemIconLayouts.VANILLA_EFFECT_COLORS} 와 같은 값이다(클라이언트 테스트가 이 표를 대조한다).
     */
    public static int effectColor(StatusEffect effect) {
        return switch (effect) {
            case POISON -> 0x87A363;
            case SLOWNESS -> 0x8BAFE0;
            case WEAKNESS -> 0x484D48;
            case BAD_OMEN -> 0x0B6138;
            case RAID_OMEN -> 0xDE4058;
            case INSTANT_DAMAGE -> 0xA9656A;
            case BLINDNESS -> 0x1F1F23;
            case SPEED -> 0x33EBFF;
            case MINING_FATIGUE -> 0x4A4217;
            case DARKNESS -> 0x292721;
            case NAUSEA -> 0x551D4A;
            case FIRE_RESISTANCE -> 0xFF9900;
            case NIGHT_VISION -> 0xC2FF66;
            case SATURATION -> 0xF82423;
            case STRENGTH -> 0xFFC700;
            case WATER_BREATHING -> 0x98DAC0;
            case BREATH_OF_THE_NAUTILUS -> 0x00FFEE;
            case JUMP_BOOST -> 0xFDFF84;
            case RESISTANCE -> 0x9146F0;
            case CONDUIT_POWER -> 0x1DC2D1;
            case HUNGER -> 0x587653;
            case TRIAL_OMEN -> 0x16A6A6;
            case REGENERATION -> 0xCD5CAB;
            case SLOW_FALLING -> 0xF3CFB9;
            case WIND_CHARGED -> 0xBDC9FF;
            case WEAVING -> 0x78695A;
            case OOZING -> 0x99FFA3;
            case INFESTED -> 0x8C9B8C;
            // [GLOWING] MobEffects.GLOWING 색 9740385.
            case GLOWING -> 0x94A061;
            // [END-CITY] MobEffects.LEVITATION 색 13565951 · INSTANT_HEALTH 색 -1080797(0xF82423).
            case LEVITATION -> 0xCEFFFF;
            case INSTANT_HEALTH -> 0xF82423;
            // [RAID-OMEN] MobEffects.HERO_OF_THE_VILLAGE 색 4521796.
            case HERO_OF_THE_VILLAGE -> 0x44FF44;
            // [UTILITY] 신호기·양조가 여는 효과(MobEffects.<clinit> 색).
            case HASTE -> 0xD9C043;
            case INVISIBILITY -> 0xF6F6F6;
            case LUCK -> 0x59C106;
        };
    }

    /** 투척 물약은 바닐라와 같이 마시는 지속의 3/4 을 준다. */
    public static int splashTicks(int drinkTicks) {
        return drinkTicks * 3 / 4;
    }

    /** 마시는 물약 하나의 효과. 물약이 아니거나 어색한 물약이면 null. */
    public static ProjectileEffect drinkEffect(short itemType) {
        if (itemType == PlayerInventory.POTION_SWIFTNESS) {
            return new ProjectileEffect(StatusEffect.SPEED, 0, SWIFTNESS_TICKS);
        }
        if (itemType == PlayerInventory.POTION_POISON) {
            return new ProjectileEffect(StatusEffect.POISON, 0, POISON_TICKS);
        }
        if (itemType == PlayerInventory.POTION_SLOWNESS) {
            return new ProjectileEffect(StatusEffect.SLOWNESS, 0, SLOWNESS_TICKS);
        }
        if (itemType == PlayerInventory.POTION_WEAKNESS) {
            return new ProjectileEffect(StatusEffect.WEAKNESS, 0, WEAKNESS_TICKS);
        }
        if (itemType == PlayerInventory.POTION_HARMING) {
            return new ProjectileEffect(StatusEffect.INSTANT_DAMAGE, 0, 0);
        }
        // [POTION-UPGRADE] 연장(레드스톤) 네 종. 효과·증폭은 기본형과 같고 지속만 늘어난다.
        if (itemType == PlayerInventory.POTION_SWIFTNESS_LONG) {
            return new ProjectileEffect(StatusEffect.SPEED, 0, SWIFTNESS_LONG_TICKS);
        }
        if (itemType == PlayerInventory.POTION_POISON_LONG) {
            return new ProjectileEffect(StatusEffect.POISON, 0, POISON_LONG_TICKS);
        }
        if (itemType == PlayerInventory.POTION_SLOWNESS_LONG) {
            return new ProjectileEffect(StatusEffect.SLOWNESS, 0, SLOWNESS_LONG_TICKS);
        }
        if (itemType == PlayerInventory.POTION_WEAKNESS_LONG) {
            return new ProjectileEffect(StatusEffect.WEAKNESS, 0, WEAKNESS_LONG_TICKS);
        }
        // [POTION-UPGRADE] II 등급(발광석 가루) 네 종. 증폭이 오르고 지속은 짧아진다.
        if (itemType == PlayerInventory.POTION_SWIFTNESS_II) {
            return new ProjectileEffect(StatusEffect.SPEED, II_AMPLIFIER, SWIFTNESS_II_TICKS);
        }
        if (itemType == PlayerInventory.POTION_POISON_II) {
            return new ProjectileEffect(StatusEffect.POISON, II_AMPLIFIER, POISON_II_TICKS);
        }
        if (itemType == PlayerInventory.POTION_SLOWNESS_II) {
            return new ProjectileEffect(
                    StatusEffect.SLOWNESS, SLOWNESS_II_AMPLIFIER, SLOWNESS_II_TICKS);
        }
        if (itemType == PlayerInventory.POTION_HARMING_II) {
            return new ProjectileEffect(StatusEffect.INSTANT_DAMAGE, II_AMPLIFIER, 0);
        }
        // [BRIMSTONE] 화염 저항 두 종. 바닐라에 II 등급이 없어 증폭은 언제나 0 이다.
        if (itemType == PlayerInventory.POTION_FIRE_RESISTANCE) {
            return new ProjectileEffect(StatusEffect.FIRE_RESISTANCE, 0, FIRE_RESISTANCE_TICKS);
        }
        if (itemType == PlayerInventory.POTION_FIRE_RESISTANCE_LONG) {
            return new ProjectileEffect(
                    StatusEffect.FIRE_RESISTANCE, 0, FIRE_RESISTANCE_LONG_TICKS);
        }
        // [POTION-GAP] 힘 세 종. II 등급의 증폭 규약은 위 강화 물약과 같은 II_AMPLIFIER 다.
        if (itemType == PlayerInventory.POTION_STRENGTH) {
            return new ProjectileEffect(StatusEffect.STRENGTH, 0, STRENGTH_TICKS);
        }
        if (itemType == PlayerInventory.POTION_STRENGTH_LONG) {
            return new ProjectileEffect(StatusEffect.STRENGTH, 0, STRENGTH_LONG_TICKS);
        }
        if (itemType == PlayerInventory.POTION_STRENGTH_II) {
            return new ProjectileEffect(StatusEffect.STRENGTH, II_AMPLIFIER, STRENGTH_II_TICKS);
        }
        // [POTION-GAP] 수중 호흡 두 종. 바닐라에 II 등급이 없어 증폭은 언제나 0 이다.
        if (itemType == PlayerInventory.POTION_WATER_BREATHING) {
            return new ProjectileEffect(StatusEffect.WATER_BREATHING, 0, WATER_BREATHING_TICKS);
        }
        if (itemType == PlayerInventory.POTION_WATER_BREATHING_LONG) {
            return new ProjectileEffect(
                    StatusEffect.WATER_BREATHING, 0, WATER_BREATHING_LONG_TICKS);
        }
        // [POTION-GAP] 도약 세 종.
        if (itemType == PlayerInventory.POTION_LEAPING) {
            return new ProjectileEffect(StatusEffect.JUMP_BOOST, 0, LEAPING_TICKS);
        }
        if (itemType == PlayerInventory.POTION_LEAPING_LONG) {
            return new ProjectileEffect(StatusEffect.JUMP_BOOST, 0, LEAPING_LONG_TICKS);
        }
        if (itemType == PlayerInventory.POTION_LEAPING_II) {
            return new ProjectileEffect(StatusEffect.JUMP_BOOST, II_AMPLIFIER, LEAPING_II_TICKS);
        }
        // [POTION-GAP] 야간 투시 두 종. 바닐라에 II 등급이 없어 증폭은 언제나 0 이다.
        if (itemType == PlayerInventory.POTION_NIGHT_VISION) {
            return new ProjectileEffect(StatusEffect.NIGHT_VISION, 0, NIGHT_VISION_TICKS);
        }
        if (itemType == PlayerInventory.POTION_NIGHT_VISION_LONG) {
            return new ProjectileEffect(StatusEffect.NIGHT_VISION, 0, NIGHT_VISION_LONG_TICKS);
        }
        // [TRIAL-GAP] 재생의 물약. 바닐라 Potions.REGENERATION = 재생 I 900 MC 틱.
        if (itemType == PlayerInventory.POTION_REGENERATION) {
            return new ProjectileEffect(StatusEffect.REGENERATION, 0, REGENERATION_TICKS);
        }
        // [END-CITY] 강한 치유의 물약. 바닐라 Potions.STRONG_HEALING = 즉시 회복 II(증폭 1).
        if (itemType == PlayerInventory.POTION_STRONG_HEALING) {
            return new ProjectileEffect(StatusEffect.INSTANT_HEALTH, II_AMPLIFIER, 0);
        }
        return null;
    }

    /** 투척 물약 하나가 착탄점에서 뿌리는 효과. 투척 물약이 아니면 null. */
    public static ProjectileEffect splashEffect(short itemType) {
        ProjectileEffect drink = drinkEffect(drinkableForSplash(itemType));
        if (drink == null) return null;
        if (drink.effect().instantaneous()) return drink;
        return new ProjectileEffect(drink.effect(), drink.amplifier(),
                splashTicks(drink.durationTicks()));
    }

    /**
     * 투척 물약에 대응하는 마시는 물약. 투척 물약이 아니면 {@link PlayerInventory#EMPTY}.
     *
     * <p>[POTION-UPGRADE] 두 구간을 각자의 상수 오프셋으로 옮긴다 — 기본 다섯 종은 809~813 →
     * 804~808(오프셋 5), 강화 여덟 종은 825~832 → 817~824(오프셋 8). 두 구간 모두 마시는 쪽과
     * 투척 쪽의 나열 순서가 같으므로 산술 규약은 그대로다. 이 매핑의 단일 소유자는 이 클래스다.
     */
    public static short drinkableForSplash(short splashType) {
        if (splashType >= PlayerInventory.SPLASH_POTION_SWIFTNESS
                && splashType <= PlayerInventory.SPLASH_POTION_HARMING) {
            return (short) (splashType
                    - PlayerInventory.SPLASH_POTION_SWIFTNESS + PlayerInventory.POTION_SWIFTNESS);
        }
        if (splashType >= PlayerInventory.SPLASH_POTION_SWIFTNESS_LONG
                && splashType <= PlayerInventory.SPLASH_POTION_HARMING_II) {
            return (short) (splashType - PlayerInventory.SPLASH_POTION_SWIFTNESS_LONG
                    + PlayerInventory.POTION_SWIFTNESS_LONG);
        }
        // [BRIMSTONE] 화염 저항 두 종은 1463~1466 안에서 마시는 둘 → 투척 둘이 같은 순서로
        // 늘어서므로 오프셋이 2 다(기본 다섯 종의 5 · 강화 여덟 종의 8 과 같은 꼴).
        if (splashType >= PlayerInventory.SPLASH_POTION_FIRE_RESISTANCE
                && splashType <= PlayerInventory.SPLASH_POTION_FIRE_RESISTANCE_LONG) {
            return (short) (splashType - PlayerInventory.SPLASH_POTION_FIRE_RESISTANCE
                    + PlayerInventory.POTION_FIRE_RESISTANCE);
        }
        // [POTION-GAP] 네 사슬 모두 같은 산술 규약이다 — 사슬 안에서 마시는 쪽과 투척 쪽의
        // 나열 순서가 같으므로 오프셋은 그 사슬의 마시는 종 수(힘·도약 3 · 수중 호흡·야간 투시 2)다.
        if (splashType >= PlayerInventory.SPLASH_POTION_STRENGTH
                && splashType <= PlayerInventory.SPLASH_POTION_STRENGTH_II) {
            return (short) (splashType - PlayerInventory.SPLASH_POTION_STRENGTH
                    + PlayerInventory.POTION_STRENGTH);
        }
        if (splashType >= PlayerInventory.SPLASH_POTION_WATER_BREATHING
                && splashType <= PlayerInventory.SPLASH_POTION_WATER_BREATHING_LONG) {
            return (short) (splashType - PlayerInventory.SPLASH_POTION_WATER_BREATHING
                    + PlayerInventory.POTION_WATER_BREATHING);
        }
        if (splashType >= PlayerInventory.SPLASH_POTION_LEAPING
                && splashType <= PlayerInventory.SPLASH_POTION_LEAPING_II) {
            return (short) (splashType - PlayerInventory.SPLASH_POTION_LEAPING
                    + PlayerInventory.POTION_LEAPING);
        }
        if (splashType >= PlayerInventory.SPLASH_POTION_NIGHT_VISION
                && splashType <= PlayerInventory.SPLASH_POTION_NIGHT_VISION_LONG) {
            return (short) (splashType - PlayerInventory.SPLASH_POTION_NIGHT_VISION
                    + PlayerInventory.POTION_NIGHT_VISION);
        }
        return PlayerInventory.EMPTY;
    }

    /**
     * 마시는 물약에 화약을 더해 만드는 투척 물약. 효과가 없는 어색한 물약은 투척형이 없어
     * {@link PlayerInventory#EMPTY} 다(바닐라는 만들 수 있지만 아무 일도 하지 않는다).
     */
    public static short splashForDrinkable(short drinkType) {
        if (drinkEffect(drinkType) == null) return PlayerInventory.EMPTY;
        // [TRIAL-GAP] 재생의 물약에는 이 저장소에 투척형 아이템이 없다(아래 산술이 엉뚱한 ID 를 낸다).
        if (drinkType == PlayerInventory.POTION_REGENERATION) return PlayerInventory.EMPTY;
        // [END-CITY] 강한 치유의 물약에도 이 저장소에 투척형 아이템이 없다.
        if (drinkType == PlayerInventory.POTION_STRONG_HEALING) return PlayerInventory.EMPTY;
        // [POTION-GAP] 위 drinkableForSplash 의 역방향. 네 사슬을 각자의 오프셋으로 되돌린다.
        if (drinkType >= PlayerInventory.POTION_STRENGTH
                && drinkType <= PlayerInventory.POTION_STRENGTH_II) {
            return (short) (drinkType - PlayerInventory.POTION_STRENGTH
                    + PlayerInventory.SPLASH_POTION_STRENGTH);
        }
        if (drinkType >= PlayerInventory.POTION_WATER_BREATHING
                && drinkType <= PlayerInventory.POTION_WATER_BREATHING_LONG) {
            return (short) (drinkType - PlayerInventory.POTION_WATER_BREATHING
                    + PlayerInventory.SPLASH_POTION_WATER_BREATHING);
        }
        if (drinkType >= PlayerInventory.POTION_LEAPING
                && drinkType <= PlayerInventory.POTION_LEAPING_II) {
            return (short) (drinkType - PlayerInventory.POTION_LEAPING
                    + PlayerInventory.SPLASH_POTION_LEAPING);
        }
        if (drinkType >= PlayerInventory.POTION_NIGHT_VISION
                && drinkType <= PlayerInventory.POTION_NIGHT_VISION_LONG) {
            return (short) (drinkType - PlayerInventory.POTION_NIGHT_VISION
                    + PlayerInventory.SPLASH_POTION_NIGHT_VISION);
        }
        if (drinkType >= PlayerInventory.POTION_FIRE_RESISTANCE
                && drinkType <= PlayerInventory.POTION_FIRE_RESISTANCE_LONG) {
            return (short) (drinkType - PlayerInventory.POTION_FIRE_RESISTANCE
                    + PlayerInventory.SPLASH_POTION_FIRE_RESISTANCE);
        }
        if (drinkType >= PlayerInventory.POTION_SWIFTNESS_LONG
                && drinkType <= PlayerInventory.POTION_HARMING_II) {
            return (short) (drinkType - PlayerInventory.POTION_SWIFTNESS_LONG
                    + PlayerInventory.SPLASH_POTION_SWIFTNESS_LONG);
        }
        return (short) (drinkType
                - PlayerInventory.POTION_SWIFTNESS + PlayerInventory.SPLASH_POTION_SWIFTNESS);
    }
}
