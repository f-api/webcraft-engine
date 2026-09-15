package com.gameexpert.engine.mob;

import com.gameexpert.engine.PlayerInteractionRules;

/**
 * 크리킹(creaking, stableId 99)의 <b>상태 없는</b> 수치 정본.
 *
 * <p>근거 등급은 <b>[B]</b> 다 — 이 저장소가 고정한 원문 데이터 스냅샷
 * ({@code docs/research/mc-vanilla-1214/}, 태그 {@code 1.21.4-data-json})은 바이옴·지물·전리품
 * 만 담고 있어 몹 어트리뷰트가 들어 있지 않다. 아래 값은 전부
 * [B] minecraft.wiki «Creaking»(조회 2026-08-10)이며 발췌 핀은
 * {@code docs/research/mc-pale-garden-1214.md} §7 이다.
 *
 * <p><b>이 종의 정체성은 두 가지다.</b>
 * <ol>
 *   <li><b>자연 스폰이 0 이다.</b> 바닐라도 창백한 정원 바이옴의 {@code spawners} 에 크리킹이
 *       없다(핀 §1c) — <b>크리킹 하트 블록</b>이 밤마다 하나씩 소환한다. 그래서 이 종을
 *       등록해도 어떤 바이옴 스폰 표도 바뀌지 않고 기존 종의 상대 확률·난수 프리픽스가 한
 *       글자도 움직이지 않는다(황린 잠복자가 낸 선례와 같은 자리).</li>
 *   <li><b>본체가 무적이다.</b> 피해가 통하지 않고 자기 하트를 부숴야만 죽는다. 그래서 전투
 *       설계의 무게가 "몹을 어떻게 때리나" 가 아니라 "하트를 어떻게 찾나" 로 옮겨간다.</li>
 * </ol>
 *
 * <p><b>관측 판정과 신뢰 경계.</b> 크리킹은 플레이어가 보고 있는 동안 완전히 멈춘다(위핑
 * 앤젤 문법). 이 저장소의 이동·시선은 설계상 <b>클라이언트 권위</b>라
 * ({@code webcraft-server-trust-boundary}) 서버는 클라가 보고한 yaw/pitch 를 그대로 믿고
 * 시야 원뿔을 판정한다. <b>이것은 이 종이 새로 여는 구멍이 아니다</b> — 엔더맨의 응시
 * 적대화({@link Enderman#lookingAtFace})가 이미 정확히 같은 입력을 같은 방식으로 쓰고 있고,
 * {@link PlayerSnapshot} 이 그 목적으로 yaw/pitch 를 싣고 있다. 즉 크리킹의 divergence 는
 * <b>기존 신뢰 경계를 한 종 더 쓰는 것</b>이지 경계를 옮기는 것이 아니다. 악용 가능성은
 * 엔더맨과 같은 등급이다: 조작된 시선을 보내면 "보고 있지 않다" 고 속여 크리킹을 계속
 * 움직이게 만들 수 있는데, 그 결과는 <b>플레이어에게 불리하다</b>(멈춰 있어야 할 몹이
 * 다가온다). 반대 방향(계속 "보고 있다" 고 보내 영구 정지)은 노클립과 같은 등급의 구조적
 * 미탐지이며 문서화가 유일한 대응이다.
 *
 * <p>정적판 사본은 {@code StandaloneMobRules.ts} 의 {@code CREAKING_*} 상수이며 두 권위는
 * 손 사본이다(값이 갈리면 {@code StandaloneMobRules.species.test.ts} 가 잡는다).
 */
public final class CreakingRules {

    private CreakingRules() {
    }

    // ── 개체 치수 ────────────────────────────────────────────────────────────────────
    /** 히트박스 폭. [B] «Creaking» — 0.9 × 2.7. */
    public static final double WIDTH = 0.9;
    /** 히트박스 높이. 사람보다 한참 큰 마른 나무 실루엣이다. */
    public static final double HEIGHT = 2.7;
    /**
     * 눈높이. 원문 값을 확보하지 못해 지어내지 않고 {@code EntityDimensions} 기본값
     * {@code height × 0.85} 를 쓴다(황린 잠복자·유황 큐브·가디언과 같은 자리).
     */
    public static final double EYE_HEIGHT = HEIGHT * 0.85;

    // ── 전투 수치 ────────────────────────────────────────────────────────────────────
    /**
     * 최대 체력 1. [B] «Creaking» — 자연 소환된 개체는 어차피 무적이라 이 값이 실제로 닳는
     * 일이 없다. 그래도 1 을 그대로 싣는 이유는 하트를 부순 순간의 처치가 <b>보통 사망
     * 경로</b>를 타야 하기 때문이다(별도 "즉시 제거" 문법을 만들지 않는다).
     */
    public static final int MAX_HEALTH = 1;
    /** 방어도 0. 무적이 방어도가 아니라 {@link #INVULNERABLE} 로 표현된다. */
    public static final double ARMOR = 0.0;
    /** 방어구 관통 저항 0. */
    public static final double TOUGHNESS = 0.0;
    /**
     * 이동 속도(블록/틱). [B] «Creaking» movement speed 0.3 — 이 저장소의 {@code MobType}
     * 속도 칸이 쓰는 것과 같은 단위다(워든도 같은 0.3 이다).
     */
    public static final double MOVE_SPEED = 0.3;
    /**
     * 기본(=normal) 근접 피해 3. [B] «Creaking» — easy 2.5 / normal 3 / hard 4.5. 이 저장소의
     * 월드는 언제나 normal 이고 난이도 배율은 다른 모든 몹과 같은 자리에서 곱한다(황린
     * 잠복자가 낸 것과 같은 판단).
     */
    public static final int MELEE_DAMAGE = 3;
    /** 근접 사거리(블록). 다른 사람 크기 근접 몹과 같은 값이다. */
    public static final double ATTACK_RANGE = 2.0;
    /** 근접 공격 간격(10 TPS 권위 틱). 바닐라 공통 20 MC 틱을 접은 값이다. */
    public static final int ATTACK_COOLDOWN_TICKS = 10;
    /**
     * <b>모든 피해원에 면역인가.</b> [B] «Creaking» — "completely invulnerable to all sources
     * of damage (except for the void and /kill)". 이 저장소에는 공허도 {@code /kill} 도 없으므로
     * 실질적으로 <b>하트 파괴만이 유일한 처치 경로</b>다.
     */
    public static final boolean INVULNERABLE = true;
    /** 경험치 드랍. [B] «Creaking» — "do not drop any items or experience". */
    public static final int XP_DROP = 0;

    // ── 감지 · 관측 정지 ─────────────────────────────────────────────────────────────
    /**
     * 감지 반경(블록). [B] «Creaking» — "within approximately 12 blocks enters a player's
     * field of vision, it becomes alerted".
     */
    public static final double DETECT_RANGE = 12.0;
    /**
     * 관측 원뿔의 <b>반각</b>(도). [B] «Creaking» — "within 60° of the creaking". 위키가 말하는
     * 60° 는 시선 벡터와 크리킹 방향 사이의 각이므로 반각으로 읽는다.
     */
    public static final double OBSERVE_CONE_DEGREES = 60.0;
    /**
     * 그 반각의 코사인. 시선 단위벡터와 크리킹 방향 단위벡터의 내적이 이 값 이상이면 원뿔
     * 안이다. 상수로 굳혀 두 권위가 삼각함수 구현 차이로 갈리지 않게 한다(cos 60° = 0.5).
     */
    public static final double OBSERVE_CONE_COS = 0.5;

    // ── 하트 결속 ────────────────────────────────────────────────────────────────────
    /**
     * 하트와의 최대 유클리드 거리(블록). [B] «Creaking» — "32-block Euclidean radius
     * surrounding" its heart; 넘어가면 죽는다.
     */
    public static final double TETHER_RADIUS = 32.0;
    /**
     * 소환 지연(10 TPS 권위 틱). [B] «Creaking Heart» — "waits 22 ticks total before
     * spawning". 바닐라 22 MC 틱을 이 저장소 규약(10 TPS 환산 = ÷2)으로 접어 <b>11</b> 이다.
     */
    public static final int SPAWN_DELAY_TICKS = 11;
    /**
     * 소환 후보 상자의 수평 반경(블록). [B] «Creaking Heart» — "33×17×33 box centered at that
     * creaking heart" → 수평 (33−1)/2 = 16.
     */
    public static final int SPAWN_BOX_HORIZONTAL_RADIUS = 16;
    /** 소환 후보 상자의 수직 반경(블록). (17−1)/2 = 8. */
    public static final int SPAWN_BOX_VERTICAL_RADIUS = 8;
    /** 한 하트가 동시에 거느리는 크리킹 수. [B] — "One creaking per heart". */
    public static final int PER_HEART_COUNT = 1;

    /**
     * 플레이어가 지금 이 크리킹을 <b>보고 있는가</b>. 순수 함수라 두 권위가 같은 값을 낸다.
     *
     * <p>입력은 플레이어 <b>눈</b>에서 크리킹 <b>눈</b>으로 가는 벡터이고, 판정은 시선
     * 단위벡터와의 내적 하나다. 시선 정보가 없는 스냅샷(yaw/pitch 가 NaN)은 <b>보고 있지
     * 않다</b>로 본다 — 엔더맨의 응시 판정이 같은 입력에 같은 규약을 쓴다(NaN 이면 건너뛴다).
     *
     * <p>가림(line of sight)은 여기서 보지 않는다. 블록 질의가 필요해 순수 함수로 남길 수
     * 없고, 호출부가 이미 갖고 있는 {@code MobWorldView#hasLineOfSight} 를 쓰는 것이
     * 엔더맨과 같은 분업이기 때문이다.
     */
    public static boolean withinObservationCone(
            float yaw, float pitch, double dx, double dy, double dz) {
        if (Float.isNaN(yaw) || Float.isNaN(pitch)) return false;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        if (distanceSq <= 0.0) return true;
        double cosPitch = Math.cos(pitch);
        // yaw 0 = -Z, pitch 양수 = 위. PlayerSnapshot 주석이 못 박은 규약이며 엔더맨의
        // lookingAtFace 가 쓰는 것과 <b>같은 세 줄</b>이다.
        double vx = -Math.sin(yaw) * cosPitch;
        double vy = Math.sin(pitch);
        double vz = -Math.cos(yaw) * cosPitch;
        double along = dx * vx + dy * vy + dz * vz;
        if (along <= 0.0) return false;
        return along * along >= OBSERVE_CONE_COS * OBSERVE_CONE_COS * distanceSq;
    }

    /**
     * 이 플레이어의 눈높이. 웅크림까지 반영하는 정본을 되물어 사본을 만들지 않는다 —
     * 엔더맨 응시 판정이 같은 함수를 쓴다.
     */
    public static double playerEyeY(PlayerSnapshot player) {
        return player.y() + PlayerInteractionRules.eyeHeight(player.crouching());
    }

    /** 하트에서 이만큼 멀어지면 죽는가. 제곱 비교라 제곱근을 뽑지 않는다. */
    public static boolean beyondTether(
            double x, double y, double z, double heartX, double heartY, double heartZ) {
        double dx = x - heartX;
        double dy = y - heartY;
        double dz = z - heartZ;
        return dx * dx + dy * dy + dz * dz > TETHER_RADIUS * TETHER_RADIUS;
    }
}
