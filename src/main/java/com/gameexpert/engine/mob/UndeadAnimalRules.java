package com.gameexpert.engine.mob;

/**
 * 좀비 동물 계열이 <b>공유</b>하는 상태 없는 계약 두 가지 — (1) 좀비 보정의 정의,
 * (2) 뿔·엄니 돌진의 정의.
 *
 * <p>MC-REFERENCE: 바닐라에 대응물이 <b>없는</b> WebCraft 자체 계약이다(근거 등급 C).
 * 서술 정본은 {@code docs/MC-REFERENCE.md} 「WebCraft 좀비 동물」 절이고, 정적판 사본은
 * {@code client/src/backend/standalone/StandaloneMobRules.ts} 의 {@code UNDEAD_ANIMAL_*} ·
 * {@code CARRION_CHARGE_*} 상수다.
 *
 * <h2>좀비 보정</h2>
 * 좀비화 종(stableId 76~81)은 원본 동물의 <b>골격을 그대로</b> 물려받는다 — AABB 와 눈높이는
 * 살아 있는 개체와 글자 그대로 같다. 갈라지는 축은 부패가 설명할 수 있는 것뿐이며, 그 축은
 * 좀비곰({@link ZombieBearRules})·좀비 늑대({@link ZombieWolfRules})가 이미 정한 세 가지다:
 * <ul>
 *   <li><b>더 질기다</b> — 최대 체력 = 원본 + {@value #HEALTH_BONUS}(부패로 굳은 근막).
 *       좀비곰 30→34, 좀비 늑대 8→12 가 이 규칙의 선례다.</li>
 *   <li><b>더 굼뜨다</b> — 배회 속도 = 원본 × {@value #IDLE_SPEED_SCALE}(소수 둘째 자리 반올림).
 *       좀비 늑대 0.3→0.24 가 이 규칙의 선례다.</li>
 *   <li><b>더 끈질기다</b> — 감지 반경은 종을 가리지 않고 {@value #DETECT_RANGE} 블록으로
 *       통일한다(좀비 계열 전체와 같다). 살아 있는 동물은 도망치거나 무시하지만 부패한
 *       개체는 좀비처럼 멀리서 붙는다.</li>
 * </ul>
 * 방어도는 좀비 계열과 같은 {@value #ARMOR}(썩은 가죽)이고 방어구 관통 저항은 없다.
 *
 * <p><b>주간 소각은 하지 않는다.</b> 좀비곰·좀비 늑대와 같은 결론이며
 * ({@code Mob#tickFireEnvironment} 의 {@code burnsInSun} 명단 밖), 바닐라 낙타 husk 가
 * "Unlike most undead mobs, camel husks do not burn in sunlight" 로 같은 결론을 갖는다는
 * 원문 근거가 {@code docs/research/mc-camel-husk-1-21-11.md} 에 있다.
 *
 * <h2>돌진(뿔·엄니)</h2>
 * 좀비 염소({@link ZombieGoatRules})·무덤 사슴({@link CarrionStagRules})·부패 멧돼지
 * ({@link CarrionBoarRules}) 셋이 <b>같은</b> 돌진을 쓴다 — 종마다 사본을 두지 않는다는 것이
 * 이 클래스가 존재하는 이유다. 돌진은 <b>텔레그래프 없는 짧은 가속</b>이다:
 * <ol>
 *   <li>표적이 {@value #CHARGE_MIN_DISTANCE}~{@value #CHARGE_MAX_DISTANCE} 블록에 있고
 *       쿨다운이 0 이면 돌진이 시작된다(난수를 소비하지 않는 결정적 판정이라 양 권위의
 *       결정 트레이스가 갈리지 않는다).</li>
 *   <li>{@value #CHARGE_TICKS} 틱 동안 추격 속도가 {@value #CHARGE_SPEED_SCALE} 배가 된다.</li>
 *   <li>돌진 중에 사거리에 닿아 때리면 피해가 {@value #CHARGE_DAMAGE_BONUS} 만큼 늘어난다.</li>
 *   <li>끝나면 {@value #CHARGE_COOLDOWN_TICKS} 틱 쿨다운이 걸린다.</li>
 * </ol>
 * 무덤 사슴만 <b>표적 하나당 {@value CarrionStagRules#CHARGE_LIMIT} 회</b>로 더 조인다 —
 * "도망가다 딱 한 번 뿔로 받는다"가 그 종의 정체성이기 때문이다.
 */
public final class UndeadAnimalRules {

    private UndeadAnimalRules() {
    }

    // ── 좀비 보정 ────────────────────────────────────────────────────
    /** 최대 체력 보정. 원본 동물 체력에 이만큼 더한다(부패로 굳은 근막). */
    public static final int HEALTH_BONUS = 4;
    /** 배회 속도 배율. 원본 동물 속도에 곱하고 소수 둘째 자리에서 반올림한다. */
    public static final double IDLE_SPEED_SCALE = 0.8;
    /** 추적 감지 반경(블록). 좀비 계열 전체와 같다. */
    public static final double DETECT_RANGE = 35.0;
    /** 방어도. 좀비 계열과 같은 썩은 가죽 2 다. */
    public static final double ARMOR = 2.0;
    /** 방어구 관통 저항. 좀비 계열과 같이 없다. */
    public static final double TOUGHNESS = 0.0;
    /** 썩은 살점 굴림 상한(배타). {@code nextInt(3)} → 0~2. 좀비 계열과 같은 형태다. */
    public static final int ROTTEN_FLESH_ROLL = 3;

    // ── 돌진 ─────────────────────────────────────────────────────────
    /** 돌진 개시 최소 거리(블록). 이보다 가까우면 이미 붙어 있어 가속할 여지가 없다. */
    public static final double CHARGE_MIN_DISTANCE = 5.0;
    /** 돌진 개시 최대 거리(블록). 이보다 멀면 도착 전에 돌진이 끝난다. */
    public static final double CHARGE_MAX_DISTANCE = 12.0;
    /** 돌진 지속(10TPS 틱) = 0.8초. */
    public static final int CHARGE_TICKS = 8;
    /** 돌진 쿨다운(10TPS 틱) = 6초. 돌진이 끝난 틱부터 센다. */
    public static final int CHARGE_COOLDOWN_TICKS = 60;
    /** 돌진 중 추격 속도 배율. */
    public static final double CHARGE_SPEED_SCALE = 2.0;
    /** 돌진 중에 닿은 타격의 추가 피해. */
    public static final int CHARGE_DAMAGE_BONUS = 2;

    /** 좀비 보정을 거친 최대 체력. */
    public static int zombifiedHealth(int livingMaxHealth) {
        return livingMaxHealth + HEALTH_BONUS;
    }

    /** 좀비 보정을 거친 배회 속도(소수 둘째 자리 반올림). */
    public static double zombifiedIdleSpeed(double livingSpeed) {
        return Math.round(livingSpeed * IDLE_SPEED_SCALE * 100.0) / 100.0;
    }

    /**
     * 이 거리에서 돌진을 시작할 수 있는가. 난수를 소비하지 않는 결정적 판정이다.
     *
     * @param distance 표적까지의 3차원 거리(블록)
     * @param cooldownTicks 남은 쿨다운(0 이면 준비 완료)
     */
    public static boolean chargeReady(double distance, int cooldownTicks) {
        return cooldownTicks <= 0
                && distance >= CHARGE_MIN_DISTANCE && distance <= CHARGE_MAX_DISTANCE;
    }
}
