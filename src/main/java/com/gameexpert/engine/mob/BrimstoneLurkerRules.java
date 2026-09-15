package com.gameexpert.engine.mob;

/**
 * 황린 잠복자(brimstone_lurker, stableId 98)의 <b>상태 없는</b> 수치 정본.
 *
 * <p><b>이 종은 바닐라에 없다.</b> 유황 동굴 지대(Track SULFUR)가 만든 WebCraft 고유 지형에
 * 사는 고유 엘리트 몹이고, 서술 정본은 {@code docs/MC-REFERENCE.md} 「황린 잠복자」 절이다.
 * 바닐라 대응이 없으므로 아래 값은 전부 <b>자체 계약([C])</b> 이며, 각 값의 근거는 "이미
 * 저장소에 있는 어떤 바닐라 계약을 재사용했는가" 로 적는다 — 새 문법을 만들지 않는 것이
 * 이 종의 설계 원칙이다.
 *
 * <p><b>재사용한 기존 계약</b>
 * <ol>
 *   <li>돌진: 좀비 염소·무덤 사슴이 쓰는 {@link ChargingUndeadAnimal} 상태 기계와 같은 꼴이다
 *       (텔레그래프 → 돌진 → 쿨다운, 난수 0 소비).</li>
 *   <li>유황 침: 라마 침({@link ProjectileSim.Kind#LLAMA_SPIT})의 <b>투척 물리</b>를 그대로
 *       쓴다 — 중력 0.03 · 공기 0.99 · 수중 0.8. 새 물리를 만들지 않는다.</li>
 *   <li>착탄 장판·접촉 점화 피해: 유황 간헐천({@code SulfurGeyserRules})이 이미 모닥불에서
 *       물려받은 계약 그대로다 — 0.5초당 1 point, 사인 {@code "in_fire"}, 접촉 중 점화.</li>
 *   <li>과열 발광 텍스처 스왑: 구리 전구 {@code lit} 쌍둥이가 낸 선례(상태를 비트가 아니라
 *       표현 스왑으로 다루는 문법)를 몹 재질에 적용한다.</li>
 *   <li>재출현 에포크: 레이드 쿨다운이 쓰는 "월드 틱 / 주기" 에포크 계약을 재사용한다.</li>
 * </ol>
 *
 * <p><b>난이도</b>. 근접 피해 8 은 기획서가 "hard 기준 난이도 표" 로 적은 값이지만, 이
 * 저장소의 월드는 <b>언제나 normal</b> 로 만들어지고 난이도 선택기가 없다(AGENTS 39u).
 * 그래서 hard 열은 실제로는 절대 나타나지 않으므로 8 을 <b>기본(=normal) 공격력 attribute</b>
 * 로 등록하고, 난이도 배율은 다른 모든 몹과 정확히 같은 자리
 * ({@code Difficulty.scaleContactDamage})에서 곱한다. 이것이 이 종의 divergence 1 이다.
 *
 * <p>정적판 사본은 {@code StandaloneMobRules.ts} 의 {@code BRIMSTONE_LURKER_*} 상수이며 두
 * 권위는 손 사본이다(값이 갈리면 {@code StandaloneMobRules.species.test.ts} 가 잡는다).
 */
public final class BrimstoneLurkerRules {

    private BrimstoneLurkerRules() {
    }

    // ── 개체 치수 ────────────────────────────────────────────────────────────────────
    /**
     * 히트박스 폭. 낮고 넓은 사족이라 폭이 높이보다 크다 — 거미({@code 1.4 × 0.9})가 이미
     * 낸 "넓고 낮은 상자" 선례와 같은 폭을 쓴다.
     */
    public static final double WIDTH = 1.4;
    /** 히트박스 높이. 거미보다 조금 높은 등껍질 실루엣이다. */
    public static final double HEIGHT = 1.1;
    /**
     * 눈높이. 원문이 없는 종이라 지어내지 않고 {@code EntityDimensions} 기본값
     * {@code height × 0.85} 를 쓴다(유황 큐브·가디언과 같은 자리).
     */
    public static final double EYE_HEIGHT = HEIGHT * 0.85;

    // ── 전투 수치 ────────────────────────────────────────────────────────────────────
    /** 최대 체력 40(20하트). 엘리트라 일반 적대 몹(20)의 두 배다. */
    public static final int MAX_HEALTH = 40;
    /**
     * 방어도 8. 유황 수정 갑각의 값이며, <b>과열 중</b>({@link #OVERHEAT_TICKS})과
     * <b>물 접촉 중</b>에는 {@link #ARMOR_SOFTENED} 로 떨어진다 — 이 종의 유일한 공략 창이다.
     */
    public static final double ARMOR = 8.0;
    /** 갑각 경화가 풀린 동안의 방어도. 0 이라 어떤 무기든 피해가 온전히 들어간다. */
    public static final double ARMOR_SOFTENED = 0.0;
    /** 방어구 관통 저항. 갑각은 두껍기만 하고 질기지는 않다는 계약이라 0 이다. */
    public static final double TOUGHNESS = 0.0;
    /** 기본 근접 피해(=normal). 난이도 배율은 공통 파이프라인이 곱한다(divergence 1). */
    public static final int MELEE_DAMAGE = 8;
    /** 근접 공격 간격(10 TPS 권위 틱). 20틱 = 2초로 느리고 무겁다. */
    public static final int ATTACK_COOLDOWN_TICKS = 20;
    /** 근접 사거리(블록). 넓은 상자만큼 앞이 길다. */
    public static final double ATTACK_RANGE = 2.2;

    // ── 이동 ─────────────────────────────────────────────────────────────────────────
    /** 배회·추격 속도(블록/10 TPS 틱). 무겁게 느껴지도록 느리다. */
    public static final double IDLE_BLOCKS_PER_TICK = 0.20;
    /** 돌진 속도(블록/10 TPS 틱). 배회의 2.2 배라 회피 판단 시간이 생긴다. */
    public static final double CHARGE_BLOCKS_PER_TICK = 0.44;
    /** 물에 닿은 동안의 이동 배율. 갑각이 식으면서 −50% 다. */
    public static final double WATER_SPEED_SCALE = 0.5;

    // ── 환경 면역 ────────────────────────────────────────────────────────────────────
    /** 화염 접촉 면역. 자기 장판·자기 점화·간헐천에 스스로 죽지 않는다. */
    public static final boolean FIRE_IMMUNE = true;
    /** 용암 면역. 간헐천 하부 마그마 위에 서 있는 것이 이 종의 기본 자세다. */
    public static final boolean LAVA_IMMUNE = true;

    // ── ① 잠복 ───────────────────────────────────────────────────────────────────────
    /**
     * 잠복 활성 반경(블록). 플레이어가 이 안에 들어오면 등장 절차가 시작된다. 12 는
     * 유황 동굴 최소 규모(반경 12)의 반지름과 같아서 "방에 들어서면 깨어난다" 가 된다.
     */
    public static final double LURK_ACTIVATION_RADIUS = 12.0;
    /** 잠복 중에는 이동하지 않는다. 반매몰 표현과 연기 파티클만 나간다. */
    public static final double LURK_BLOCKS_PER_TICK = 0.0;

    // ── ② 등장 ───────────────────────────────────────────────────────────────────────
    /**
     * 간헐천 연동 사거리(블록). 이 안에 분출구가 있으면 <b>다음 분출과 함께</b> 상방 발사로
     * 등장한다. 위상은 {@code SulfurGeyserRules} 의 순수 함수라 양 권위가 같은 틱을 고른다.
     */
    public static final double GEYSER_LINK_RADIUS = 16.0;
    /**
     * 간헐천이 멀 때의 연기 예고 길이(10 TPS 틱). 30틱 = 3초. 예고가 끝나면 제자리에서
     * 솟는다 — 등장 연출이 없는 순간 이동이 되지 않게 하는 하한이다.
     */
    public static final int EMERGE_WARNING_TICKS = 30;
    /** 상방 발사 등장의 초기 수직 속도(블록/틱). 분출 기둥 높이 3 을 넘기는 값이다. */
    public static final double ERUPTION_LAUNCH_SPEED = 0.62;

    // ── ③ 갑각 돌진 ──────────────────────────────────────────────────────────────────
    /** 돌진 텔레그래프 길이(10 TPS 틱). 30틱 = 3초. 이 동안 제자리에서 자세를 잡는다. */
    public static final int CHARGE_TELEGRAPH_TICKS = 30;
    /** 돌진 지속(10 TPS 틱). 20틱 = 2초. */
    public static final int CHARGE_TICKS = 20;
    /** 돌진 종료 뒤 쿨다운(10 TPS 틱). 60틱 = 6초. */
    public static final int CHARGE_COOLDOWN_TICKS = 60;
    /** 돌진을 시작할 수 있는 최소 거리(블록). 붙어 있을 때는 평범한 근접만 쓴다. */
    public static final double CHARGE_MIN_DISTANCE = 4.0;
    /** 돌진을 시작할 수 있는 최대 거리(블록). */
    public static final double CHARGE_MAX_DISTANCE = 14.0;
    /** 돌진 명중 피해. 근접과 같은 8 이고 난이도 배율도 같은 자리에서 곱한다. */
    public static final int CHARGE_HIT_DAMAGE = MELEE_DAMAGE;
    /** 돌진 명중 시 붙이는 화염 지속(10 TPS 틱). 30틱 = 3초. */
    public static final int CHARGE_IGNITE_TICKS = 30;
    /** 벽에 박았을 때의 그로기(10 TPS 틱). 20틱 = 2초. 반격 창이다. */
    public static final int CHARGE_WALL_GROGGY_TICKS = 20;

    // ── ③ 유황 침 ────────────────────────────────────────────────────────────────────
    /** 유황 침 사거리(블록). */
    public static final double SPIT_RANGE = 8.0;
    /** 유황 침 발사 간격(10 TPS 틱). 40틱 = 4초. */
    public static final int SPIT_COOLDOWN_TICKS = 40;
    /** 유황 침 직격 피해. 라마 침(1)보다 무겁지만 돌진보다 가볍다. */
    public static final int SPIT_DAMAGE = 3;
    /** 착탄 후 장판이 피는 데 걸리는 시간(10 TPS 틱). 20틱 = 2초. */
    public static final int SPIT_FUSE_TICKS = 20;
    /** 점화 장판의 수평 반경(블록). "소형" 이라 1 이다. */
    public static final double SPIT_PATCH_RADIUS = 1.0;
    /** 점화 장판의 지속(10 TPS 틱). 40틱 = 4초. */
    public static final int SPIT_PATCH_TICKS = 40;

    // ── ④ 과열 주기 ──────────────────────────────────────────────────────────────────
    /**
     * 과열 주기(10 TPS 틱). 200틱 = 20초. 간헐천 주기({@code SulfurGeyserRules.PERIOD_TICKS}
     * 200 게임 틱 = 10초)와 <b>같은 숫자를 다른 단위로</b> 쓰는 것이 아니라, 의도적으로 그
     * 두 배 길이다 — 두 리듬이 서로 스치듯 어긋나 같은 패턴이 반복되지 않는다.
     */
    public static final int OVERHEAT_PERIOD_TICKS = 200;
    /** 과열 지속(10 TPS 틱). 50틱 = 5초. 이 동안 방어도가 {@link #ARMOR_SOFTENED} 다. */
    public static final int OVERHEAT_TICKS = 50;
    /** 과열 중 접촉한 대상에게 붙는 화염 지속(10 TPS 틱). */
    public static final int OVERHEAT_CONTACT_IGNITE_TICKS = 30;

    // ── 스폰 ─────────────────────────────────────────────────────────────────────────
    /**
     * 유황 동굴 <b>넷 중 하나</b>가 잠복자 하나를 갖는다. site 해시의 나머지 판정이라 난수를
     * 소비하지 않고 두 권위가 같은 자리를 고른다.
     */
    public static final int SITE_OCCUPANCY_DENOMINATOR = 4;
    /** site 점유 판정 해시 솔트. 다른 유황 계약의 솔트와 겹치지 않는 값이다. */
    public static final int SITE_SALT = 0x5f1a62;
    /** 한 site 의 개체 수. 엘리트라 언제나 1 이다. */
    public static final int PER_SITE_COUNT = 1;
    /**
     * 처치 뒤 재출현 주기(10 TPS 권위 틱). 90분 = 90 × 60 × 10 = 54,000 틱이다. 레이드
     * 쿨다운과 같은 <b>에포크</b> 계약이라 저장하는 것은 "마지막 처치 틱" 하나뿐이고
     * 잔여 시간은 파생이다.
     */
    public static final int RESPAWN_EPOCH_TICKS = 54_000;

    // ── 드랍 ─────────────────────────────────────────────────────────────────────────
    /** 유황 가루 최소 드랍. */
    public static final int SULFUR_MIN = 2;
    /** 유황 가루 굴림 폭(배타). {@code SULFUR_MIN + nextInt(SULFUR_ROLL)} → 2~4. */
    public static final int SULFUR_ROLL = 3;
    /** 약탈 1레벨당 유황 가루 추가 굴림 폭. 바닐라 약탈 규약과 같은 꼴이다. */
    public static final int SULFUR_LOOTING_PER_LEVEL = 1;
    /** 마그마 크림 드랍 확률. 약탈의 영향을 받지 않는 고정 확률이다. */
    public static final double MAGMA_CREAM_CHANCE = 0.40;
    /** 갑각 파편(장식 트로피 블록) 드랍 확률. */
    public static final double CARAPACE_SHARD_CHANCE = 0.01;

    /**
     * 이 유황 동굴 site 가 잠복자를 갖는가. 좌표 해시의 나머지 하나이므로 순수 함수이고
     * 난수를 소비하지 않는다 — 자연 스폰 확률에 어떤 영향도 주지 않는다.
     */
    public static boolean siteHostsLurker(int siteKey) {
        return Integer.remainderUnsigned(mix(siteKey ^ SITE_SALT), SITE_OCCUPANCY_DENOMINATOR) == 0;
    }

    /**
     * 마지막 처치 이후 재출현했는가. {@code lastKillTick} 이 음수면 아직 한 번도 죽지 않은
     * 것이라 언제나 살아 있다.
     */
    public static boolean respawned(long worldTick, long lastKillTick) {
        return lastKillTick < 0 || worldTick - lastKillTick >= RESPAWN_EPOCH_TICKS;
    }

    /** 과열 중인가. 개체가 태어난 뒤 흐른 틱만 보는 순수 함수라 양 권위가 같은 값을 낸다. */
    public static boolean overheated(long aliveTicks) {
        return Math.floorMod(aliveTicks, OVERHEAT_PERIOD_TICKS) < OVERHEAT_TICKS;
    }

    /** 지금 유효한 방어도. 과열 중이거나 물에 닿아 있으면 갑각 경화가 풀린다. */
    public static double effectiveArmor(long aliveTicks, boolean inWater) {
        return overheated(aliveTicks) || inWater ? ARMOR_SOFTENED : ARMOR;
    }

    /** 지금 유효한 이동 속도. 물에 닿아 있으면 −50% 다. */
    public static double effectiveSpeed(double base, boolean inWater) {
        return inWater ? base * WATER_SPEED_SCALE : base;
    }

    /** 돌진을 시작할 수 있는 거리인가. */
    public static boolean chargeDistanceOk(double distance) {
        return distance >= CHARGE_MIN_DISTANCE && distance <= CHARGE_MAX_DISTANCE;
    }

    /**
     * 구조물 계약과 같은 정수 혼합. 부동소수점이 없어 두 권위가 반드시 같은 값을 낸다
     * ({@code SulfurGeyserRules.phaseOffset} 과 같은 상수를 쓴다).
     */
    private static int mix(int value) {
        int hash = value * 0x9e3779b9;
        hash = Integer.rotateLeft(hash ^ 0x85ebca6b, 13);
        hash ^= hash >>> 16;
        return hash;
    }
}
