package com.gameexpert.engine.mob;

import com.gameexpert.terrain.Blocks;

/**
 * 해피 가스트 하네스·좌석의 <b>상태 없는</b> 규칙. 개체 상태는 {@link HappyGhast} 가 들고,
 * 여기에는 수치와 판정만 둔다({@link CamelRules} 와 같은 분업).
 *
 * <p>근거 등급: <b>[B]</b> minecraft.wiki «Harness» · «Happy Ghast» — 하네스는 가죽 3 +
 * 유리 2 + 색 양털 1 로 만드는 16색 장비이고, <b>성체</b> 해피 가스트에게만 씌우며, 씌우면
 * 최대 <b>네 명</b>이 탄다. 조종석은 얼굴 위 앞자리이고 나머지 셋이 시계 방향으로 놓인다.
 *
 * <p>정적판 사본은 {@code client/src/backend/standalone/StandaloneHappyGhastRules.ts} 이고,
 * 두 사본의 상수·판정 동일성은 {@code StandaloneHappyGhastRules.test.ts} 가 이 파일 원문을
 * 읽어 강제한다.
 *
 * <h2>divergence(전부 의도한 것)</h2>
 * <ol>
 *   <li><b>좌석 부착점 수치는 [C] 다.</b> 바닐라 {@code HappyGhast#getPassengerAttachmentPoint}
 *       의 실수 오프셋을 이 저장소가 확인하지 못했으므로, [B] 가 못박은 <i>배치</i>(앞자리
 *       조종석 + 시계 방향 셋)만 지키고 거리는 몸통 폭 4 블록의 4분점인 {@value #SEAT_OFFSET}
 *       으로 둔다. 클라 모델({@code HappyGhastModel})이 같은 값을 읽어 좌석을 그린다.</li>
 *   <li><b>하네스를 벗기는 경로가 없다.</b> 벗기기 상호작용의 바닐라 원문을 확인하지 못했고,
 *       AGENTS §39d 는 "확인 못 한 것을 지어내는" 쪽이 아니라 "표에만 있고 동작하지 않는"
 *       쪽을 금지한다. 씌우기·탑승·영속은 전부 동작하며 벗기기만 다음 웨이브 몫이다.</li>
 * </ol>
 */
public final class HappyGhastRules {

    private HappyGhastRules() {
    }

    /**
     * 하네스가 여는 좌석 수. [B] «Happy Ghast» — "up to four players". 정본 상수는
     * {@link HappyGhast#SEAT_COUNT} 이고 이 이름은 좌석 표({@link MobMountRules#seatCount})가
     * 읽는 별칭이다(낙타의 {@code CamelRules.SEAT_COUNT} 와 같은 자리).
     */
    public static final int SEAT_COUNT = HappyGhast.SEAT_COUNT;

    /**
     * 좌석 부착점의 중심 거리(블록). [C] — 몸통 폭 {@code MobType.HAPPY_GHAST.width()} = 4 의
     * 4분점이라 네 좌석이 윗면 안에 들어온다. 앞/오른쪽/뒤/왼쪽 네 방향이 같은 거리를 쓴다.
     */
    public static final double SEAT_OFFSET = 1.0;

    /** 앞자리(조종석). 얼굴 위다. */
    public static final int SEAT_FRONT = MobMountRules.CONTROLLING_SEAT_INDEX;
    /** 조종석에서 시계 방향(위에서 내려다볼 때) 첫 번째 — 오른쪽. */
    public static final int SEAT_RIGHT = 1;
    /** 두 번째 — 뒤. */
    public static final int SEAT_BACK = 2;
    /** 세 번째 — 왼쪽. */
    public static final int SEAT_LEFT = 3;

    /**
     * 좌석의 탈것 로컬 <b>전후</b> 오프셋(블록, +가 앞). 앞자리 +, 뒷자리 −, 좌우는 0 이다.
     * 좌석 범위 검사는 호출자({@link MobMountRules#seatForwardOffset})가 먼저 한다.
     */
    public static double seatForwardOffset(int seatIndex) {
        if (seatIndex == SEAT_FRONT) return SEAT_OFFSET;
        if (seatIndex == SEAT_BACK) return -SEAT_OFFSET;
        return 0.0;
    }

    /**
     * 좌석의 탈것 로컬 <b>좌우</b> 오프셋(블록, +가 탈것 기준 오른쪽). 앞뒤 자리는 0 이다.
     * 시계 방향 배치가 이 두 함수의 부호로만 표현된다 — 앞(+F) → 오른쪽(+L) → 뒤(−F) →
     * 왼쪽(−L).
     */
    public static double seatLateralOffset(int seatIndex) {
        if (seatIndex == SEAT_RIGHT) return SEAT_OFFSET;
        if (seatIndex == SEAT_LEFT) return -SEAT_OFFSET;
        return 0.0;
    }

    /**
     * 탑승 중인 해피 가스트의 이동 속도(블록/초). 종 속성
     * ({@code MobType.HAPPY_GHAST.baseSpeed()} = 바닐라 {@code MOVEMENT_SPEED}/{@code
     * FLYING_SPEED} 0.05)를 말·낙타·돼지와 <b>같은 환산 계수</b>
     * ({@link FarmAnimalRules#MOVEMENT_SPEED_BLOCKS_PER_SECOND})로 옮긴 값이라 약 2.108 이다.
     *
     * <p>[C] — 바닐라가 탑승 시 비행 속도에 곱하는 배율을 확인하지 못했으므로 배율을 지어내지
     * 않고 속성 그대로 쓴다. 이 속도가 곧 좌표 업링크 leash({@link MobMountRules#RIDER_POS_RANGE}
     * 16 블록)가 견뎌야 할 상한이며, 말 14.23 블록/초보다 훨씬 느려 여유가 그대로 남는다.
     */
    public static double riddenSpeedBlocksPerSecond() {
        return MobType.HAPPY_GHAST.baseSpeed() * FarmAnimalRules.MOVEMENT_SPEED_BLOCKS_PER_SECOND;
    }

    /**
     * 탑승 방송에 실어 보내는 점프 강도. 해피 가스트는 <b>비행</b> 탈것이라 도약도 대시도
     * 없다 — 상승/하강은 조종석 클라의 비행 입력이지 임펄스가 아니다. 그래서 0 이다.
     */
    public static final double JUMP_STRENGTH = 0.0;

    /**
     * 이 아이템이 하네스인가. ID 정본은 {@link Blocks#isHarnessItem} 이고, 이 위임이 몹 쪽
     * 호출부의 단일 통로다.
     */
    public static boolean isHarnessItem(int itemId) {
        return Blocks.isHarnessItem(itemId);
    }

    /** 하네스 아이템 → MC {@code DyeColor} 네트워크 ID(0..15). 하네스가 아니면 -1. */
    public static int harnessColor(int itemId) {
        return Blocks.harnessDyeColor(itemId);
    }

    /** 저장·복원에서 받아들일 수 있는 하네스 색인가(0..15). */
    public static boolean validHarnessColor(int color) {
        return color >= 0 && color < Blocks.HARNESS_BY_DYE_COLOR.length;
    }
}
