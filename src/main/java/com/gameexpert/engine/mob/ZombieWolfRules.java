package com.gameexpert.engine.mob;

/**
 * 좀비 늑대(WebCraft 창작몹, stableId 75)의 <b>상태 없는</b> 자체 계약.
 *
 * <p>이 종은 바닐라 대응이 <b>없다</b>({@code EntityType} 에 {@code zombie_wolf} 는 없다).
 * 따라서 근거는 "Minecraft 원문"이 아니라 여기 적힌 <b>자체 계약</b>이며, 서술 정본은
 * {@code docs/MC-REFERENCE.md} 「WebCraft 좀비 동물」 절이다. 정적판 사본은
 * {@code client/src/backend/standalone/StandaloneMobRules.ts} 의 {@code ZOMBIE_WOLF_*} 상수이고,
 * 두 사본의 동일성은 {@code StandaloneMobRules.species.test.ts} 가 이 파일 원문을 읽어 강제한다.
 *
 * <p><b>정체성</b> — 늑대({@code MobType.WOLF})가 부패한 개체다. 좀비곰이 갈색곰에 대해 그런
 * 것처럼 몸통 치수는 늑대와 <b>글자 그대로 같고</b>({@code WIDTH}/{@code HEIGHT}), 갈라지는
 * 것은 부패가 설명할 수 있는 축뿐이다:
 * <ul>
 *   <li>더 질기다 — 야생 늑대 8 → {@value #MAX_HEALTH}, 방어도 2(썩은 가죽·굳은 근막).
 *       좀비곰과 같은 방어도이며 이유도 같다.</li>
 *   <li>더 끈질기다 — 감지 16 → {@value #DETECT_RANGE} 블록(좀비 계열과 같은 추적 반경).
 *       살아 있는 늑대는 건드려야 반응하지만 좀비 늑대는 좀비처럼 멀리서 붙는다.</li>
 *   <li>더 굼뜨다 — 배회 0.3 → {@value #IDLE_BLOCKS_PER_TICK},
 *       추격 0.3 → {@value #PURSUIT_BLOCKS_PER_TICK} 블록/틱 = 5.0블록/초. 플레이어 질주
 *       5.6블록/초보다 <b>느리다</b> — 무리에 둘러싸이기 전이라면 질주로 확실히 떨어뜨릴 수 있다.
 *       "위험한 것은 개체가 아니라 무리"라는 것이 이 종의 설계다.</li>
 *   <li>물어뜯는 리듬은 늑대 그대로다 — 타격 {@value #ATTACK_DAMAGE},
 *       쿨다운 {@value #ATTACK_COOLDOWN_TICKS} 틱. 곰처럼 굼뜨지 않다.</li>
 * </ul>
 *
 * <p><b>길들일 수 없다.</b> 살아 있는 늑대의 뼈 길들이기({@code MobSystem#tameWolf})는 종을
 * {@code WOLF} 로 못박고 있고 이 종은 그 문에 닿지 않는다. 부패한 개체에 주인·앉기·목줄 색을
 * 붙일 근거가 없고, 게임적으로도 "밤 숲의 무리"라는 위협이 길들이기로 무력화되면 안 된다.
 *
 * <p><b>언데드 계약</b> — 좀비곰과 같다. (1) 드랍(썩은 살점·썩은 가죽), (2) 외형(부패 얼룩·
 * 노출 갈비·탁한 눈), (3) 밤 한정 스폰으로만 언데드성을 표현하고 <b>주간 소각은 하지 않는다</b>
 * ({@code Mob#tickFireEnvironment} 의 {@code burnsInSun} 명단 밖).
 * {@link RottenLeatherDropRules#rottenLeatherDrop} · {@link UndeadNeutralityRules#isUndead} 두
 * 술어의 대상이며, 플레이어가 썩은 가죽 풀세트를 입으면 이 종의 <b>선공이 억제</b>된다
 * (적대 종이라 여기서는 실제로 판정이 갈린다).
 *
 * <p><b>스폰 계약</b>은 {@link MobSpawner#tryZombieWolfPack} 이 소유한다. 좀비곰과 같은
 * <b>독립 굴림</b>이라 바이옴 가중치 표를 읽지도 쓰지도 않는다 — 기존 종의 상대·절대 확률이
 * 모두 불변이다.
 */
public final class ZombieWolfRules {

    private ZombieWolfRules() {
    }

    // ── 개체 상수 ────────────────────────────────────────────────────
    /** 늑대와 같은 AABB 폭. 부패해도 골격은 그대로다. */
    public static final double WIDTH = 0.6;
    /** 늑대와 같은 AABB 높이. */
    public static final double HEIGHT = 0.85;
    /**
     * 눈높이. 늑대의 0.68 보다 낮다 — 좀비곰과 같은 이유로 머리가 처진 자세라
     * 시선 판정(가시성·발사체)이 그 자세를 따라야 한다.
     */
    public static final double EYE_HEIGHT = 0.6;
    /** 최대 체력. 야생 늑대 8 + 부패로 굳은 근막 4. */
    public static final int MAX_HEALTH = 12;
    /** 방어도. 좀비 계열과 같은 2(썩은 가죽). */
    public static final double ARMOR = 2.0;
    /** 방어구 관통 저항. 좀비 계열과 같이 없다. */
    public static final double TOUGHNESS = 0.0;

    // ── 이동/전투 ────────────────────────────────────────────────────
    /** 표적이 없을 때의 배회 속도(블록/10TPS 틱). {@code MobType#baseSpeed} 가 이 값을 쓴다. */
    public static final double IDLE_BLOCKS_PER_TICK = 0.24;
    /**
     * 추격 속도(블록/10TPS 틱) = 5.0블록/초. 플레이어 질주 5.6블록/초보다 느려 <b>한 마리</b>는
     * 반드시 떼어낼 수 있다. 좀비곰(4.6)보다는 빠르다 — 곰은 밀어붙이는 벽이고 늑대는 무리다.
     */
    public static final double PURSUIT_BLOCKS_PER_TICK = 0.50;
    /** 추적 감지 반경(블록). 좀비 계열과 같다 — 늑대(16, 피격 반응형)보다 끈질기다. */
    public static final double DETECT_RANGE = 35.0;
    /** 공격 사거리(블록). 늑대와 같은 물어뜯기 사거리다. */
    public static final double ATTACK_RANGE = 1.6;
    /** 한 대 피해. 살아 있는 늑대와 같다 — 위협은 무리 수에서 나온다. */
    public static final int ATTACK_DAMAGE = 4;
    /** 공격 간격(10TPS 틱). 늑대의 물어뜯는 리듬 그대로이며 좀비곰(14)보다 빠르다. */
    public static final int ATTACK_COOLDOWN_TICKS = 10;

    // ── 드랍 ─────────────────────────────────────────────────────────
    /** 썩은 살점 굴림 상한(배타). {@code nextInt(3)} → 0~2. 좀비 계열과 같은 형태다. */
    public static final int ROTTEN_FLESH_ROLL = 3;
    // 썩은 가죽은 이 종의 표가 아니라 {@code RottenLeatherDropRules} 공통 pool 이 붙인다
    // (썩은 가죽 트랙). 이 종은 그 술어의 대상이라는 사실만 계약으로 갖는다.
    /**
     * 플레이어가 처치했을 때만 굴리는 창작 희귀 드랍(뼈 1). 갈비가 드러난 개과 짐승이라
     * 뼈가 나온다. {@code nextInt(8) == 0} → 12.5%. 좀비곰과 같은 굴림이다.
     */
    public static final int BONE_RARE_ROLL = 8;
}
