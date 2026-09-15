package com.gameexpert.engine.mob;

/**
 * 좀비곰(WebCraft 창작몹, stableId 73)의 <b>상태 없는</b> 자체 계약.
 *
 * <p>이 종은 바닐라 대응이 없다. 따라서 근거는 "Minecraft 원문"이 아니라 여기 적힌
 * <b>자체 계약</b>이며, 서술 정본은 {@code docs/MC-REFERENCE.md} 「WebCraft 창작몹」 절이다.
 * 정적판 사본은 {@code client/src/backend/standalone/StandaloneMobRules.ts} 의
 * {@code ZOMBIE_BEAR_*} 상수이고, 두 사본의 동일성은
 * {@code StandaloneMobRules.species.test.ts} 가 이 파일 원문을 읽어 강제한다.
 *
 * <p><b>정체성</b> — 갈색곰({@link BrownBear})이 부패한 개체다. 그래서 몸통 치수는
 * 갈색곰과 <b>글자 그대로 같고</b>({@code WIDTH}/{@code HEIGHT}), 갈라지는 것은 부패가
 * 설명할 수 있는 축뿐이다:
 * <ul>
 *   <li>더 질기다 — 최대 체력 30 → {@value #MAX_HEALTH}, 방어도 2(썩은 가죽·굳은 근막).</li>
 *   <li>더 느리다 — 배회 0.12 → {@value #IDLE_BLOCKS_PER_TICK},
 *       추격 0.52 → {@value #PURSUIT_BLOCKS_PER_TICK} 블록/틱. 살아 있는 갈색곰보다 느리므로
 *       플레이어 질주(0.56 블록/틱)로 <b>확실히</b> 떨어뜨릴 수 있다.</li>
 *   <li>더 아프고 더 굼뜨다 — 타격 6 → {@value #ATTACK_DAMAGE},
 *       쿨다운 12 → {@value #ATTACK_COOLDOWN_TICKS} 틱.</li>
 *   <li>더 끈질기다 — 감지 20 → {@value #DETECT_RANGE} 블록(좀비 계열과 같은 추적 반경).
 *       갈색곰은 영역 방어형이라 가까이서만 반응하지만, 좀비곰은 좀비처럼 멀리서 붙는다.</li>
 * </ul>
 *
 * <p><b>언데드 계약</b> — WebCraft 에는 "언데드" 피해 상호작용(즉시 회복/고통 반전)이
 * 존재하지 않으므로 편입할 판정 자체가 없다. 좀비곰의 언데드성은 (1) 드랍(썩은 살점),
 * (2) 외형(부패 얼룩·노출 갈비·탁한 눈), (3) 밤 한정 스폰으로만 표현한다.
 * <b>주간 소각은 하지 않는다</b> — {@code Mob#tickFireEnvironment} 의 {@code burnsInSun}
 * 명단에 넣지 않는다. 곰의 두꺼운 모피가 남아 있다는 설정이고, 게임적으로는 "밤에 만나
 * 낮까지 쫓기는" 조우가 성립해야 희소 조우가 사건이 되기 때문이다.
 *
 * <p><b>스폰 계약</b>은 {@link MobSpawner#tryZombieBearProwl}이 소유한다. 바이옴 가중치 표는
 * 건드리지 않는다 — 기존 종의 상대·절대 확률이 모두 불변이어야 하므로, 좀비곰은 바이옴
 * 표에 항목을 <b>더하지 않고</b> 순찰대({@code PILLAGER_PATROL_ROLL})와 같은 방식의
 * 독립 굴림으로만 나온다.
 */
public final class ZombieBearRules {

    private ZombieBearRules() {
    }

    // ── 개체 상수 ────────────────────────────────────────────────────
    /** 갈색곰과 같은 AABB 폭. 부패해도 골격은 그대로다. */
    public static final double WIDTH = 1.4;
    /** 갈색곰과 같은 AABB 높이. */
    public static final double HEIGHT = 1.5;
    /**
     * 눈높이. 갈색곰의 1.14 보다 낮다 — 목이 꺾여 머리가 처진 자세라
     * 시선 판정(가시성·발사체)이 그 자세를 따라야 한다.
     */
    public static final double EYE_HEIGHT = 1.02;
    /** 최대 체력. 갈색곰 30 + 부패로 굳은 근막 4. */
    public static final int MAX_HEALTH = 34;
    /** 방어도. 좀비 계열과 같은 2(썩은 가죽). */
    public static final double ARMOR = 2.0;
    /** 방어구 관통 저항. 좀비 계열과 같이 없다. */
    public static final double TOUGHNESS = 0.0;

    // ── 이동/전투 ────────────────────────────────────────────────────
    /** 표적이 없을 때의 배회 속도(블록/10TPS 틱). {@code MobType#baseSpeed} 가 이 값을 쓴다. */
    public static final double IDLE_BLOCKS_PER_TICK = 0.10;
    /**
     * 추격 속도(블록/10TPS 틱) = 4.6 블록/초. 플레이어 질주 5.6 블록/초보다 느리고
     * 살아 있는 갈색곰 5.2 블록/초보다도 느리다.
     */
    public static final double PURSUIT_BLOCKS_PER_TICK = 0.46;
    /** 추적 감지 반경(블록). 좀비 계열과 같다. */
    public static final double DETECT_RANGE = 35.0;
    /** 공격 사거리(블록). 갈색곰과 같은 앞발 사거리다. */
    public static final double ATTACK_RANGE = 2.2;
    /** 한 대 피해. 갈색곰 6 + 1. */
    public static final int ATTACK_DAMAGE = 7;
    /** 공격 간격(10TPS 틱). 갈색곰 12 보다 굼뜨다. */
    public static final int ATTACK_COOLDOWN_TICKS = 14;

    // ── 드랍 ─────────────────────────────────────────────────────────
    /** 썩은 살점 굴림 상한(배타). {@code nextInt(3)} → 0~2. 좀비 계열과 같은 형태다. */
    public static final int ROTTEN_FLESH_ROLL = 3;
    /** 부패해도 남는 모피 굴림 상한(배타). {@code nextInt(2)} → 0~1. */
    public static final int LEATHER_ROLL = 2;
    /**
     * 플레이어가 처치했을 때만 굴리는 창작 희귀 드랍(뼈 1). 곰의 골격이 드러난 종이라
     * 뼈가 나온다. {@code nextInt(8) == 0} → 12.5%.
     */
    public static final int BONE_RARE_ROLL = 8;
}
