package com.gameexpert.engine.mob;

/**
 * 파치드(stableId 91)의 <b>상태 없는</b> 계약.
 *
 * <p><b>이 종은 바닐라 실존 종이다</b> — Java Edition 1.21.11 "Mounts of Mayhem" 의
 * {@code parched}. 리서치 발췌·원문 인용은 {@code docs/research/mc-parched-1-21-11.md} 가
 * 소유하고 정적판 사본은 {@code StandaloneMobRules.ts} 의 {@code PARCHED_*} 상수다.
 *
 * <p><b>[PARCHED-FAMILY] 계열 정정.</b> 이 클래스의 첫 판은 파치드를 <b>허스크 모델</b>로
 * 세웠다 — HP 20 · 방어도 2 · 속도 0.23 · 근접 3 의 좀비 계열 근접몹. 그 판단의 근거는
 * "사막 언데드 인간형이니 같은 자리의 허스크 값을 물려받자"였고, 무장은 위키 서술을 잘못 읽어
 * <b>철 창</b>으로 적었다. <b>둘 다 틀렸다.</b> 위키 라이브 재확인 결과 파치드는
 * <b>스켈레톤 변종</b>이며 창을 들지 않는다("Parched can no longer pick up spears",
 * "Parched do not pick up spears under any circumstances"). 그래서 이 트랙은 수치·계열·AI를
 * 전부 스켈레톤 쪽으로 옮긴다:
 *
 * <table>
 *   <caption>정정 대조</caption>
 *   <tr><th>항목</th><th>옛 값(허스크 모델, 틀림)</th><th>원문(등급 B)</th></tr>
 *   <tr><td>계열</td><td>좀비/허스크 근접</td><td><b>스켈레톤 변종</b>(활)</td></tr>
 *   <tr><td>체력</td><td>20</td><td><b>16</b></td></tr>
 *   <tr><td>방어도</td><td>2.0</td><td><b>0</b></td></tr>
 *   <tr><td>속도</td><td>0.23</td><td><b>0.25</b></td></tr>
 *   <tr><td>히트박스</td><td>0.6 × 1.95</td><td><b>0.6 × 1.99</b></td></tr>
 *   <tr><td>원거리</td><td>없음</td><td><b>나약함 화살 30초</b></td></tr>
 *   <tr><td>드랍</td><td>썩은 살점 0~2</td><td><b>뼈 0~2 · 화살 0~2</b></td></tr>
 * </table>
 *
 * <p><b>근거 등급</b>은 여전히 <b>B</b> 다 — 1.21.11 은 이 저장소가 고정한 1.21.4 데이터
 * 스냅샷보다 뒤라 {@code EntityType}/{@code createAttributes()} 원문을 인용할 수 없고, 위키
 * 인포박스 표기(Health 16 · Armor 0 · Height 1.99 · Width 0.6 · Speed 0.25)를 옮긴다.
 * 다만 <b>지어낸 값은 하나도 없다</b> — 옛 판이 "원문을 못 구해 허스크를 물려받은" 것과 달리
 * 이번에는 여섯 수치 전부 인포박스에 실제로 적힌 숫자다.
 *
 * <p><b>WebCraft divergence(등급 C)</b>
 * <ol>
 *   <li><b>나약함 화살 아이템이 없다.</b> 원문 드랍표는 뼈 0~2 · 화살 0~2 에 더해
 *       <b>나약함 화살 0~1</b>(플레이어/길들인 늑대 처치 한정)을 준다. 이 저장소에는 팁 화살이
 *       <b>아이템으로 등록되어 있지 않다</b> — 스트레이(감속 화살)·보그드(독 화살)도 바닐라
 *       드랍을 같은 이유로 옮기지 못했고 뼈·화살만 떨어뜨린다. 파치드도 <b>그 선례를 그대로
 *       따른다</b>. 팁 화살 아이템 사슬이 서면 세 종을 한 번에 채운다.
 *       <b>화살이 실어 나르는 나약함 효과 자체는 정상 동작한다</b> — 발사 경로는
 *       {@link ProjectileEffect} 를 그대로 태우므로 손실이 없다.</li>
 *   <li><b>기수 승격이 없다.</b> 낙타 husk 자연 스폰의 10% 를 기수로 올리는 계약은 마운트
 *       {@code seatIndex} 일반화를 요구한다. 파치드는 26.3 사막 MONSTER 표의 가중치·무리
 *       크기로 자연 스폰하지만 여전히 <b>도보 단독 개체</b>로 finalize 된다.</li>
 *   <li><b>주간 소각 없음.</b> 원문도 파치드를 햇빛에 태우지 않는다(스켈레톤·스트레이·보그드와
 *       갈리는 지점이라 {@code Mob#tickFireEnvironment} 의 {@code burnsInSun} 명단에
 *       <b>넣지 않는다</b>). 사막 낮이 이 종을 지우지 않는다는 뜻이다.</li>
 * </ol>
 */
public final class ParchedRules {

    private ParchedRules() {
    }

    /** 히트박스 폭. 위키 인포박스 "Width: 0.6 blocks". */
    public static final double WIDTH = 0.6;
    /** 히트박스 높이. 위키 인포박스 "Height: 1.99 blocks" — 스켈레톤 계열과 같다. */
    public static final double HEIGHT = 1.99;
    /** 눈높이. 스켈레톤 계열 인간형의 1.74 를 그대로 쓴다({@code Mob#eyeHeight} 의 같은 줄). */
    public static final double EYE_HEIGHT = 1.74;
    /** 최대 체력. 위키 인포박스 "16HP ❤ × 8" — 보그드와 같고 스켈레톤(20)보다 낮다. */
    public static final int MAX_HEALTH = 16;
    /** 방어도. 위키 인포박스 "Armor: 0" — 좀비 계열의 2.0 을 물려받던 옛 판의 정정분이다. */
    public static final double ARMOR = 0.0;
    /** 방어구 관통 저항. */
    public static final double TOUGHNESS = 0.0;
    /** 이동 속도. 위키 인포박스 "Speed: 0.25" — 스켈레톤 계열과 같다. */
    public static final double MOVEMENT_SPEED = 0.25;

    /**
     * 나약함 화살 지속(normal 기준 10 TPS 서버 틱). 원문은 "Weakness for 30 seconds" 이고
     * 30초 = 300 서버 틱이다. 난이도 분기는 {@link com.gameexpert.engine.Difficulty}
     * 가 소유하며 스트레이·보그드와 <b>같은 규약</b>(normal 이 바닐라 값, easy 절반, hard 2배)을
     * 쓴다.
     */
    public static final int WEAKNESS_TICKS = 300;

    /**
     * 사격 주기(10 TPS 서버 틱). 원문 "every 3.5 seconds on Easy and Normal" = 3.5초 →
     * 35 서버 틱. 스켈레톤(20 = 2초)보다 <b>느린 연사</b>가 이 종의 특징이다.
     */
    public static final int SHOOT_INTERVAL_TICKS = 35;
    /** hard 사격 주기. 원문 "every 2.5 seconds on Hard" = 25 서버 틱. */
    public static final int SHOOT_INTERVAL_TICKS_HARD = 25;

    /** 뼈 굴림 상한(배타). {@code nextInt(3)} → 0~2, 스켈레톤 계열 전리품 표와 같다. */
    public static final int BONE_ROLL = 3;
    /** 화살 굴림 상한(배타). {@code nextInt(3)} → 0~2, 스켈레톤 계열 전리품 표와 같다. */
    public static final int ARROW_ROLL = 3;
}
