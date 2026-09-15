package com.gameexpert.engine.effect;

/**
 * 서버 권위 상태이상 종류(FX A1 1차 도입 4종).
 *
 * <p>지속시간·주기는 전부 <b>MC 틱(20 TPS)</b> 단위로 다루고, 10 TPS 서버 틱 1회가 MC 2틱을 누적합니다.
 * 이미 황금사과 재생 II 가 같은 규약(REGEN_II_INTERVAL_MC_TICKS=25)을 쓰므로 반올림 없이 바닐라 주기를 재현합니다.
 */
public enum StatusEffect {

    /** 주기 피해. 방어구를 무시하고 HP 1 미만으로는 내리지 않는다. */
    POISON("poison", false),
    /** 이동속도 감소(레벨당 -15%). 플레이어는 클라 물리, 몹은 서버 이동량에 곱한다. */
    SLOWNESS("slowness", false),
    /** 근접 피해 감소(레벨당 -4점). 플레이어·몹 공통. */
    WEAKNESS("weakness", false),
    /** 레이드 대장 보상으로 획득하는 지속 효과. 마을 진입 전까지 보존한다. */
    BAD_OMEN("bad_omen", false),
    /** 유효한 마을 진입 뒤 레이드 시작까지 남는 600 MC tick 준비 효과. */
    RAID_OMEN("raid_omen", false),
    /** 즉시 피해(6 &lt;&lt; 앰프). 지속이 없어 목록에 남지 않는다. */
    INSTANT_DAMAGE("instant_damage", true),
    /** 실명. 시야만 좁히는 표현 전용 효과로 권위 판정에는 관여하지 않는다. */
    BLINDNESS("blindness", false),
    /**
     * [POTION] 이동속도 증가(레벨당 +20%). 감속과 정확히 같은 자리에 곱해지는 반대 부호 효과라
     * 플레이어는 클라 물리가, 몹은 서버 이동량이 같은 배율 함수를 쓴다.
     */
    SPEED("speed", false),
    /**
     * [GUARDIAN] 채굴 피로(바닐라 {@code minecraft:mining_fatigue}, 옛 이름 DIG_SLOWDOWN).
     * 채굴 속도만 낮추는 표현/조작 효과라 권위 전투 판정에는 관여하지 않는다 — 서버 채굴
     * 검증은 "최소 소요 시간"의 상한만 보므로 더 느려지는 방향은 항상 통과한다.
     * 배율은 바닐라 {@code Player.getDestroySpeed} 표(앰프 0/1/2/3+ → 0.3 / 0.09 / 0.0027 / 0.00081)다.
     */
    MINING_FATIGUE("mining_fatigue", false),
    /**
     * [DEEP-DARK] 어둠(바닐라 {@code minecraft:darkness}). 실명({@link #BLINDNESS})과 **별개**다:
     * 실명은 시야를 상수로 좁히지만 어둠은 시야 반경을 <b>맥동</b>시킨다(바닐라도 1초 주기로
     * 어두워졌다 밝아진다). 시야만 건드리는 표현 전용 효과라 권위 판정에는 관여하지 않는다.
     *
     * <p>부여 주체는 스컬크 비명체다 — 비명이 끝나면 반경 40 안의 플레이어에게 12초를 준다.
     * [B] minecraft.wiki «Sculk Shrieker» Java 판.
     */
    DARKNESS("darkness", false),
    /** [SULFUR] 강한 유황 위 얕은 원천수의 유독 가스가 갱신하는 표현 전용 메스꺼움. */
    NAUSEA("nausea", false),
    /**
     * [BRIMSTONE] 화염 저항(바닐라 {@code minecraft:fire_resistance}). 화염·용암·간헐천 계열
     * 피해를 <b>무효</b>로 만든다 — 이 저장소의 화염 피해 사인은 {@code on_fire} ·
     * {@code in_fire} · {@code lava} 셋뿐이라 게이트도 그 셋 위에 선다.
     *
     * <p>이 효과는 <b>새 판정 축을 만들지 않는다</b>: 불사의 토템이 이미
     * {@code PlayerTickState.fireResistanceTicksRemaining} 으로 같은 게이트를 열고 있었고,
     * 이 효과는 그 게이트를 상태이상 목록에서도 열 수 있게 합류시킨 것뿐이다. 그래서 토템과
     * 물약이 겹쳐도 판정이 두 벌로 갈리지 않는다.
     */
    FIRE_RESISTANCE("fire_resistance", false),
    /**
     * [COOKING] 야간 투시(바닐라 {@code minecraft:night_vision}). 실명·어둠과 같은
     * <b>표현 전용</b> 효과라 권위 판정에는 관여하지 않는다 — 화면 밝기만 올린다.
     * 부여 주체는 수상한 스튜(양귀비) 취식이고, 바닐라대로 남은 시간이 200 MC 틱 미만이면
     * 클라가 깜빡인다(그 연출은 클라 소유다).
     */
    NIGHT_VISION("night_vision", false),
    /**
     * [COOKING] 포만감(바닐라 {@code minecraft:saturation}). <b>지속 효과지만 매 MC 틱마다
     * 허기를 채우는</b> 유일한 효과다 — 바닐라 {@code SaturationMobEffect} 가 MC 틱마다
     * {@code foodData.eat(level + 1, 1.0F)} 를 부른다(허기 +레벨+1, 포화 +(레벨+1)×2.0).
     * 부여 주체는 수상한 스튜(민들레) 취식이고 지속이 7 MC 틱뿐이라 사실상 즉발처럼 보이지만,
     * 바닐라가 지속 효과로 다루므로 여기서도 목록에 담는다(그래야 우유가 지운다).
     */
    SATURATION("saturation", false),
    /**
     * [POTION-GAP] 힘(바닐라 {@code minecraft:strength}). <b>나약함과 정확히 같은 자리에</b>
     * 반대 부호로 더해지는 근접 피해 보정이라 새 판정 축이 아니다 — 신속/감속이 이동속도 배율
     * 한 자리를 나눠 쓰는 것과 같은 꼴이고, 둘이 겹치면 합산돼 상쇄된다. 레벨당 +3 점이다
     * (바닐라 1.21 {@code MobEffects.DAMAGE_BOOST} 의 {@code ATTACK_DAMAGE} ADD_VALUE 3.0).
     */
    STRENGTH("strength", false),
    /**
     * [POTION-GAP] 수중 호흡(바닐라 {@code minecraft:water_breathing}). 익사 피해를
     * <b>무효</b>로 만든다. 화염 저항과 <b>같은 꼴의 게이트</b>라 새 판정 축을 만들지 않는다 —
     * 이미 있는 익사 누적({@code drownAccum})을 막는 조건이 하나 늘 뿐이다.
     */
    WATER_BREATHING("water_breathing", false),
    /**
     * Minecraft 26.2 Nautilus mount aura. This is deliberately distinct from water breathing:
     * it has its own 60-MC-tick refresh cadence and restores air while preventing drowning,
     * without granting the unrelated potion's presentation identity.
     */
    BREATH_OF_THE_NAUTILUS("breath_of_the_nautilus", false),
    /**
     * [POTION-GAP] 도약(바닐라 {@code minecraft:jump_boost}). 점프 초속을 레벨당 +0.1 하고
     * 낙하 피해 계산에서 레벨당 1 블록을 빼 준다(바닐라 {@code LivingEntity.causeFallDamage}
     * 의 {@code jumpBoostPower} 보정). 이동은 설계상 클라 권위라 점프 초속은 클라 물리가
     * 쓰고, 낙하 피해 보정은 양 권위가 같은 함수를 쓴다.
     */
    JUMP_BOOST("jump_boost", false),
    /**
     * [GOLD-FOOD] 저항(바닐라 {@code minecraft:resistance}). 받는 피해를 레벨당 20% 줄인다 —
     * 바닐라 {@code LivingEntity#getDamageAfterMagicAbsorb} 의
     * {@code damage * (25 - (amplifier + 1) * 5) / 25} 를 그대로 옮긴 것이다.
     *
     * <p>적용 지점은 <b>방어도 감산 뒤 · 보호 인챈트 앞</b>이며(바닐라 순서), 방어도가 통하지
     * 않는 피해(낙하·기아·마법)에도 걸리는 것이 바닐라와 같으므로 {@code armorReducible}
     * 게이트 밖에 둔다. 방어구 무시 주기 피해인 독({@code PlayerTickState#damagePoison})만
     * 예외로 남긴다 — 그 경로는 체력을 1 미만으로 내리지 않는 별도 계약이라 감산을 더하면
     * "1 로 수렴"이라는 성질만 흐려진다(문서화된 divergence).
     *
     * <p>부여 주체는 현재 <b>마법이 부여된 황금 사과</b> 하나뿐이다(저항 I · 5:00).
     */
    RESISTANCE("resistance", false),
    /**
     * [CONDUIT] 콘딧 파워(바닐라 {@code minecraft:conduit_power}). <b>새 판정 축을 하나도
     * 만들지 않는 합성 효과</b>다 — 세 갈래 전부 이미 있는 자리에 합류한다.
     *
     * <p>[A] 1.21.4 {@code MobEffects.CONDUIT_POWER} 원문이 거는 것은
     * {@code Attributes.SUBMERGED_MINING_SPEED} ADD_MULTIPLIED_TOTAL 4.0 <b>하나뿐</b>이고
     * 나머지 둘은 코드가 효과 보유 여부를 직접 묻는다:
     * <ul>
     *   <li><b>수중 호흡</b> — {@code MobEffectUtil.hasWaterBreathing} 이
     *       {@code WATER_BREATHING} 또는 {@code CONDUIT_POWER} 중 하나만 있어도 참인
     *       <b>불리언 게이트</b>다. 그래서 {@link #WATER_BREATHING} 이 이미 여는 익사 게이트에
     *       조건이 하나 느는 것뿐이고, 화염 저항에 토템과 물약이 합류한 것과 같은 꼴이다.</li>
     *   <li><b>야간 투시(수중판)</b> — 물속 시야만 밝히는 <b>표현 전용</b>이라 권위 판정에
     *       관여하지 않는다({@link #NIGHT_VISION} 과 같은 자리이고, 소유자는 클라다).</li>
     *   <li><b>채굴 속도</b> — 바닐라의 수중 채굴 페널티(잠수 채굴 속도 기본 0.2)를
     *       0.2 × (1 + 4.0) = <b>1.0</b> 으로 되돌린다. 즉 <b>빨라지는 효과가 아니라
     *       페널티를 정확히 상쇄</b>하는 효과다 — 물 밖에서는 아무 일도 하지 않는다.
     *       채굴 속도는 {@link #MINING_FATIGUE} 와 같은 자리라 서버 채굴 검증은
     *       "최소 소요 시간"의 상한만 보고, 이 방향은 빨라지므로 클라 소유 값이 아니라
     *       <b>양 권위가 같은 함수</b>를 쓴다.</li>
     * </ul>
     *
     * <p>부여 주체는 활성 콘딧 하나뿐이다(활성 판정은 {@link com.gameexpert.engine.ConduitRules}).
     * 지속 260 MC 틱 · 앰프 0 고정이며 프레임 수가 세기를 바꾸지 않는다 — 프레임 수가 바꾸는
     * 것은 <b>범위</b>뿐이다.
     */
    CONDUIT_POWER("conduit_power", false),
    /** 허기. 플레이어 exhaustion을 매 MC 틱마다 0.005 × (앰프 + 1) 더한다. */
    HUNGER("hunger", false),
    /**
     * [TRIAL] 시련의 징조(바닐라 {@code minecraft:trial_omen}, NEUTRAL · 색 0x16A6A6). 불길한
     * 징조를 지닌 플레이어가 트라이얼 스포너 감지 범위에 들어오면 바닐라
     * {@code TrialSpawnerStateData.transformBadOmenIntoTrialOmen} 이 불길한 징조를 지우고
     * {@code 18000 * (앰프 + 1)} MC 틱의 이 효과(앰프 0)를 준다. 이 효과를 지닌 플레이어를
     * 감지한 스포너는 불길해진다. 효과 자체는 판정을 바꾸지 않는 표식이다.
     */
    TRIAL_OMEN("trial_omen", false),
    /**
     * [TRIAL-GAP] 재생(바닐라 {@code minecraft:regeneration}, {@code RegenerationMobEffect}).
     * {@code shouldApplyEffectTickThisTick}: 주기 {@code 50 >> 앰프} MC 틱, 남은 지속이 그 주기의
     * 배수인 MC 틱마다 체력 &lt; 최대면 1 회복한다. 부여 주체는 재생의 물약(900 MC 틱 · 앰프 0)이다.
     * 황금 사과·토템의 재생 II 는 이미 {@code PlayerTickState} 의 별도 시계가 소유하며, 그 시계가
     * 켜진 동안 이 효과는 바닐라 hiddenEffect 처럼 시간만 흐르고 회복하지 않는다.
     */
    REGENERATION("regeneration", false),
    /**
     * [TRIAL-GAP] 느린 낙하(바닐라 {@code minecraft:slow_falling}). {@code LivingEntity
     * .getEffectiveGravity} 가 하강 중 중력을 0.01 로 낮추고 {@code causeFallDamage} 이전에
     * {@code fallDistance} 를 0 으로 되돌린다 — 낙하 피해가 없다.
     */
    SLOW_FALLING("slow_falling", false),
    /**
     * [TRIAL-GAP] 돌풍 충전(바닐라 {@code minecraft:wind_charged}, {@code WindChargedMobEffect}).
     * 지닌 채 죽으면({@code RemovalReason.KILLED}) 몸 중앙(발 + 키/2)에서 반경 3 + nextFloat×2
     * 의 돌풍 폭발(바람 탄환과 같은 계산기)이 터진다.
     */
    WIND_CHARGED("wind_charged", false),
    /**
     * [TRIAL-GAP] 거미줄(바닐라 {@code minecraft:weaving}, {@code WeavingMobEffect}). 지닌 채
     * 죽으면 발 위치 주변 15 칸 정육면체에서 무작위로 교체 가능한 칸(아래가 윗면 단단)을 골라
     * 거미줄을 최대 {@code 2 + nextInt(2)} 개(바닐라 {@code randomBetweenInclusive(2, 3)}) 친다.
     */
    WEAVING("weaving", false),
    /**
     * [TRIAL-GAP] 점액(바닐라 {@code minecraft:oozing}, {@code OozingMobEffect}). 지닌 채 죽으면
     * 크기 2 슬라임 2 마리({@code 2}, 반경 3 안 기존 슬라임을 뺀 몹 상한 24 까지)가 나온다.
     */
    OOZING("oozing", false),
    /**
     * [TRIAL-GAP] 벌레 먹음(바닐라 {@code minecraft:infested}, {@code InfestedMobEffect}).
     * 피해를 받을 때마다 10% 확률로 좀벌레 {@code randomBetweenInclusive(1, 2)} 마리가 나온다.
     */
    INFESTED("infested", false),
    /**
     * [GLOWING] 발광(바닐라 {@code minecraft:glowing}, NEUTRAL · 색 9740385 = 0x94A061, 속성 수정자
     * 없는 평범한 {@code MobEffect}). 판정은 바꾸지 않는 <b>표현 전용</b> 효과다 — 바닐라
     * {@code LivingEntity#updateGlowingStatus} 가 이 효과의 보유 여부를 공유 플래그 6(발광)으로
     * 옮기고, 클라가 그 엔티티를 벽 너머로 윤곽선(entity_outline 후처리)으로 그린다. 여기서는
     * 몹의 효과 목록이 {@code MobSpawnDto/MobUpdateDto.effects} 로 복제되어 같은 일을 한다.
     *
     * <p>부여 주체는 공명한 종({@code BellBlockEntity#makeRaidersGlow}: 48 블록 안 습격자에게
     * 60 MC 틱)이다. 분광 화살({@code SpectralArrow}, 200 MC 틱)은 이 저장소에 아이템이 없다.
     */
    GLOWING("glowing", false),
    /**
     * [END-CITY] 공중 부양(바닐라 {@code minecraft:levitation}). 셜커 탄환이 {@code 200} MC 틱 준다.
     * 수직 속도를 {@code (0.05·(amp+1) − vy)·0.2} 씩 끌어올린다(LivingEntity#travel). 플레이어는 클라 물리가,
     * 몹은 서버 이동이 적용한다.
     */
    LEVITATION("levitation", false),
    /**
     * [RAID-OMEN] 마을의 영웅(바닐라 {@code minecraft:hero_of_the_village}). 레이드 승리 때 영웅(레이더를 죽인
     * 플레이어)에게 48000 MC 틱, 증폭 {@code raidOmenLevel − 1} 로 준다. 주민 거래가
     * {@code 0.3 + 0.0625·amplifier} 만큼 싸진다.
     */
    HERO_OF_THE_VILLAGE("hero_of_the_village", false),
    /**
     * [END-CITY] 즉시 회복(바닐라 {@code minecraft:instant_health}, {@code HealOrHarmMobEffect(false)}): 살아 있는
     * 개체는 {@code 4 << amp} 회복, 언데드는 {@code 6 << amp} 피해(즉시 피해와 반대). 목록에 남지 않는다.
     * [BREWING-26.3] 치유의 물약도 같은 효과다(투척은 근접도 배율 {@code (int)(근접도 × (4 << amp) + 0.5)}).
     */
    INSTANT_HEALTH("instant_health", true),
    /**
     * [BEACON] 성급함(바닐라 {@code minecraft:haste}, BENEFICIAL · 색 0xD9C043). 두 축에 걸린다:
     * <ul>
     *   <li><b>채굴 속도</b> — 26.3 {@code Player.getDestroySpeed} 의
     *       {@code if (MobEffectUtil.hasDigSpeed(this)) f *= 1 + (getDigSpeedAmplification + 1) * 0.2F}.
     *       {@code hasDigSpeed} 는 성급함 <b>또는 콘딧 파워</b>이고 앰프는 둘 중 큰 값이다
     *       ({@code MobEffectUtil}). 채굴은 클라 조작이라 클라가 곱하고, 서버 채굴 검증은 같은 배율로
     *       최소 소요 시간을 줄인다({@link com.gameexpert.engine.effect.StatusEffects#digSpeedMultiplier}).</li>
     *   <li><b>공격 속도</b> — {@code MobEffects.HASTE} 의 {@code ATTACK_SPEED} ADD_MULTIPLIED_TOTAL
     *       0.1 × 레벨.</li>
     * </ul>
     * 부여 주체는 신호기(1단계 효과)다.
     */
    HASTE("haste", false),
    /**
     * [BREWING-26.3] 투명화(바닐라 {@code minecraft:invisibility}). 권위 판정은 몹의 <b>표적 획득</b>
     * 한 자리뿐이다 — 바닐라 {@code LivingEntity.getVisibilityPercent}: 투명하면 가시도에
     * {@code 0.7 × max(0.1, 착용 방어구 칸 / 4)} 를 곱하고, {@code TargetingConditions} 가
     * 획득 반경을 {@code max(추적 반경 × 가시도, 2)} 로 줄인다. 이미 잡은 표적의 유지
     * ({@code TargetGoal.canContinueToUse})에는 가시도가 없다. 몸을 그리지 않는 것은 표현이다.
     */
    INVISIBILITY("invisibility", false),
    /**
     * [BREWING-26.3] 행운(바닐라 {@code minecraft:luck}, 속성 {@code LUCK} +1/레벨). 행운의 물약은
     * 바닐라에서도 양조할 수 없고(26.3 {@code recipe/brewing} 에 없다) 이 저장소의 전리품 표는
     * 플레이어 행운을 읽지 않으므로 표현 전용 효과로 목록에만 남는다.
     */
    LUCK("luck", false);

    private final String protocolName;
    private final boolean instantaneous;

    StatusEffect(String protocolName, boolean instantaneous) {
        this.protocolName = protocolName;
        this.instantaneous = instantaneous;
    }

    /** 프로토콜(effectUpdate)에 실리는 이름. 양판·클라가 같은 문자열을 씁니다. */
    public String protocolName() {
        return protocolName;
    }

    /** 즉발 효과인가. 즉발은 {@link StatusEffects} 목록에 저장하지 않고 그 자리에서 소진합니다. */
    public boolean instantaneous() {
        return instantaneous;
    }

    /** 프로토콜 이름으로 역조회. 알 수 없으면 null. */
    public static StatusEffect byProtocolName(String name) {
        if (name == null) return null;
        for (StatusEffect effect : values()) {
            if (effect.protocolName.equals(name)) return effect;
        }
        return null;
    }
}
