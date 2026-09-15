package com.gameexpert.engine.mob;

/**
 * 탑승(좌석) 계약의 <b>종 비의존</b> 정본. 돼지 전용으로 만들어 두었던 좌석 검증·leash·
 * 좌표 업링크 규칙을 여기로 올려, 말 계열·낙타 같은 뒤따르는 종이 같은 계약을 그대로 쓴다.
 *
 * <p>신뢰 모델은 보트와 같다(CONTRACT §1 클라 권위 이동 경계):
 * <ol>
 *   <li>권위는 좌석 원장(누가 어느 탈것의 몇 번 좌석에 앉았나)과 검증만 갖는다.</li>
 *   <li>탑승이 확정되면 <b>조종석(seat 0) 기수 클라</b>가 좌표 정본이 되어 업링크를 올린다.</li>
 *   <li>권위는 좌석 일치·유한성·월드 경계·기수 pose 근접(leash)만 본다. 물리는 굴리지 않는다.</li>
 *   <li>탑승 중 탈것의 권위 AI 이동은 멈춘다(좌표 정본이 둘이 되지 않게).</li>
 * </ol>
 *
 * <p>다인승은 처음부터 계약에 들어 있다. {@code seatIndex} 는 0 부터 {@link #seatCount}-1 이며
 * {@link #CONTROLLING_SEAT_INDEX} 만 조종·업링크 권한을 갖는다. 나머지 좌석은 순수 승객이라
 * 좌표를 올리지 않는다(바닐라 낙타 뒷좌석과 같은 규칙).
 *
 * <p>근거: Java 1.21.4 {@code Entity#startRiding}/{@code Entity#getPassengers} 의 승객 목록
 * 순서(첫 승객이 {@code getControllingPassenger} 후보)와 {@code EntityAttachments.createDefault}
 * 의 기본 부착점(탈것 높이 × 0.75). 정적판 사본은
 * {@code client/src/backend/standalone/StandaloneMobMountRules.ts} 이고 두 사본의 상수 동일성은
 * {@code StandaloneMobMountRules.test.ts} 가 이 파일 원문을 읽어 강제한다.
 */
public final class MobMountRules {

    private MobMountRules() {
    }

    /**
     * 기수 pose 와 탈것 좌표 사이에 허용하는 최대 간격(블록). 보트 운전자 범위
     * ({@code BoatSystem.DRIVER_POS_RANGE})와 <b>같은 값</b>이며, 기수 pose 자체가
     * {@code MovementLimits} 로 이미 속도 상한을 받으므로 이 leash 가 곧 탈것의 속도 상한이다.
     * 말 최고 탑승 속도 14.23블록/초(권위 틱당 1.423블록)에 대해서도 16블록은 지연 보정 몫으로만
     * 남는다.
     */
    public static final double RIDER_POS_RANGE = 16.0;

    /** 조종·좌표 업링크 권한을 갖는 좌석. 바닐라의 첫 승객({@code getControllingPassenger})이다. */
    public static final int CONTROLLING_SEAT_INDEX = 0;

    /** 바닐라 {@code EntityAttachments.createDefault}: 승객 부착점 = 탈것 높이 × 0.75. */
    public static final double PASSENGER_ATTACH_HEIGHT_FACTOR = 0.75;

    /**
     * 종별 좌석 수. 0 이면 탈 수 없다.
     *
     * <p>돼지·말·당나귀·노새·라마는 바닐라 1인승이다. 낙타는 바닐라
     * {@code Camel#canAddPassenger}({@code getPassengers().size() < 2})가 못박은 2인승으로,
     * 이 계약의 첫 다인승 소비자다.
     */
    public static int seatCount(MobType type) {
        return switch (type) {
            // 좀비 말은 바닐라 AbstractHorse 하위형이라 말과 같은 1인승이다. 조종 게이트도
            // 말과 글자 그대로 같다(안장 없이는 steerable 이 거짓).
            case PIG, HORSE, DONKEY, MULE, LLAMA, ZOMBIE_HORSE -> 1;
            case CAMEL -> CamelRules.SEAT_COUNT;
            // 해피 가스트는 하네스를 씌우면 넷이 탄다([B] «Happy Ghast»). 좌석 수 자체는
            // 하네스 유무와 무관한 종 상수이고, 하네스는 낙타의 안장과 같은 자리에서 —
            // steerable 게이트와 탑승 분기에서 — 본다.
            case HAPPY_GHAST -> HappyGhastRules.SEAT_COUNT;
            // [NAUTILUS-MOUNT] 길들인 성체 노틸러스는 안장을 얹으면 한 명이 탄다([B]).
            // 이 표의 첫 **수중** 종이고, 그 구분은 aquaticMount 한 술어가 소유한다.
            case NAUTILUS, ZOMBIE_NAUTILUS -> NautilusMountRules.SEAT_COUNT;
            default -> 0;
        };
    }

    /**
     * [NAUTILUS-MOUNT] <b>이동 매질 축.</b> 이 탈것은 물속에서 조종되는가.
     *
     * <p>지금까지 좌석 표는 지상 종(돼지·말 계열·낙타)과 공중 종(해피 가스트)만 담았고, 둘의
     * 차이는 종별 코드에 흩어져 있었다. 노틸러스가 <b>수중</b> 축의 첫 종이라 이 웨이브가
     * 술어를 하나 세운다 — 해피 가스트가 {@link #seatLateralOffset} 로 두 번째 좌석 축을 연
     * 것과 같은 꼴이고, 여기서도 <b>기존 종의 판정은 한 글자도 바뀌지 않는다</b>(전부 거짓).
     *
     * <p>신뢰 경계에서 이 축이 의미하는 것: 수중 탈것의 조종석 업링크는 <b>부력·낙하 판정을
     * 받지 않는</b> 좌표 정본이다(보트와 같다). 권위는 여기서도 좌석 일치·유한성·월드 경계·
     * leash 만 보므로 {@link #acceptableUplink} 는 매질과 무관하게 같은 함수를 쓴다 —
     * 이 술어는 <b>물리를 바꾸지 않고</b> 클라 조종 모드(SWIM)와 렌더를 가른다.
     */
    public static boolean aquaticMount(MobType type) {
        return type == MobType.NAUTILUS || type == MobType.ZOMBIE_NAUTILUS;
    }

    /** 이 종이 좌석을 갖는가. */
    public static boolean rideable(MobType type) {
        return seatCount(type) > 0;
    }

    /**
     * <b>조종 권한 표.</b> 좌석이 있다고 조종할 수 있는 것은 아니다 — 바닐라
     * {@code Llama#getControllingPassenger()} 는 언제나 null 이라 라마는 탈 수는 있어도 기수가
     * 방향·속도를 주지 못하고 라마 자신의 AI 가 계속 움직인다({@code AbstractHorse} 의 다른 종은
     * 첫 승객을 조종자로 돌려준다).
     *
     * <p>이 구분이 신뢰 경계에서 실제로 의미하는 것: 조종 불가 종은 <b>좌표 정본이 기수 클라로
     * 넘어가지 않는다</b>. 그래서 {@link #acceptableUplink} 를 호출하기 전에 이 표를 먼저 보고,
     * 조종 불가 종의 업링크는 좌석이 맞더라도 거절한다. 점프 차지도 같다.
     */
    public static boolean controllable(MobType type) {
        return rideable(type) && type != MobType.LLAMA;
    }

    /** 이 좌석이 <b>이 종에서</b> 조종·좌표 업링크 권한을 갖는가. 종 표와 좌석 번호를 함께 본다. */
    public static boolean controllingSeat(MobType type, int seatIndex) {
        return controllable(type) && controllingSeat(seatIndex) && validSeat(type, seatIndex);
    }

    /**
     * <b>안장까지 본 조종 판정.</b> 좌석이 있고 조종 가능한 종이어도, 안장이 없으면 기수의 이동
     * 입력은 탈것에 닿지 않는다 — 바닐라 {@code AbstractHorse#getControllingPassenger()} 는
     * <pre>
     *   if (this.isSaddled()) { Entity e = this.getFirstPassenger(); if (e instanceof Player p) return p; }
     *   return null;
     * </pre>
     * 라 안장이 없으면 조종자가 <b>없다</b>. 낙타는 {@code Camel extends AbstractHorse} 라 같은
     * 가드를 물려받고(길들이기는 없다), 돼지도 {@code Pig#getControllingPassenger} 가
     * {@code isSaddled()} 를 먼저 본다. 라마는 안장 슬롯 자체가 없어 {@link #controllable} 에서
     * 이미 걸린다.
     *
     * <p>탑승 자체는 이 게이트와 <b>다른 문</b>이다: 바닐라 {@code doPlayerRide} 는 길들이기·안장을
     * 묻지 않고 태우며, 미길들임 개체는 {@code RunAroundLikeCrazyGoal} 이 판정 실패마다 기수를
     * 떨어뜨린다({@code HorseRules.TAME_ATTEMPT_ROLL_BOUND}).
     */
    public static boolean steerable(MobType type, boolean saddled) {
        return controllable(type) && saddled;
    }

    /** 이 좌석이 <b>이 개체에서</b> 조종·좌표 업링크 권한을 갖는가(종 표 + 좌석 번호 + 안장). */
    public static boolean controllingSeat(MobType type, int seatIndex, boolean saddled) {
        return saddled && controllingSeat(type, seatIndex);
    }

    /** 좌석 번호가 이 종의 좌석 범위 안인가. */
    public static boolean validSeat(MobType type, int seatIndex) {
        return seatIndex >= 0 && seatIndex < seatCount(type);
    }

    /** 이 좌석이 조종·좌표 업링크 권한을 갖는가. */
    public static boolean controllingSeat(int seatIndex) {
        return seatIndex == CONTROLLING_SEAT_INDEX;
    }

    /** 승객이 앉는 높이(탈것 발밑 기준 블록). */
    public static double passengerAttachY(MobType type) {
        return type.height() * PASSENGER_ATTACH_HEIGHT_FACTOR;
    }

    /**
     * 좌석의 탈것 로컬 전후 오프셋(블록, +가 앞). 1인승 종은 언제나 0 이다. 다인승 종은
     * 자기 트랙이 바닐라 {@code getPassengerAttachmentPoint} 수치를 근거와 함께 여기에 더한다.
     */
    public static double seatForwardOffset(MobType type, int seatIndex) {
        if (!validSeat(type, seatIndex)) {
            throw new IllegalArgumentException("invalid seat " + seatIndex + " for " + type);
        }
        return switch (type) {
            case CAMEL -> CamelRules.seatForwardOffset(seatIndex);
            case HAPPY_GHAST -> HappyGhastRules.seatForwardOffset(seatIndex);
            default -> 0.0;
        };
    }

    /**
     * 좌석의 탈것 로컬 <b>좌우</b> 오프셋(블록, +가 탈것 기준 오른쪽). 좌석이 전후로만 늘어선
     * 종(돼지·말 계열·낙타)은 언제나 0 이고, 좌석을 <b>평면에 두 축으로</b> 배치하는 종만
     * 값을 갖는다 — 지금은 해피 가스트뿐이다(앞·오른쪽·뒤·왼쪽 네 방향).
     *
     * <p>{@link #seatForwardOffset} 과 짝이며 <b>같은 좌석 범위 검사</b>를 쓴다. 축을 하나만
     * 가진 API 로는 네 방향 배치를 표현할 수 없어 이 웨이브가 두 번째 축을 연다.
     */
    public static double seatLateralOffset(MobType type, int seatIndex) {
        if (!validSeat(type, seatIndex)) {
            throw new IllegalArgumentException("invalid seat " + seatIndex + " for " + type);
        }
        return type == MobType.HAPPY_GHAST ? HappyGhastRules.seatLateralOffset(seatIndex) : 0.0;
    }

    /**
     * 업링크 좌표가 기수 pose 근처인가. 두 권위가 같은 판정을 내리도록 한 곳에만 둔다.
     * 돼지 {@code FarmAnimalRules.withinPigRiderRange} 도 이 판정을 그대로 위임한다.
     */
    public static boolean withinRiderRange(double riderX, double riderY, double riderZ,
            double x, double y, double z) {
        double dx = x - riderX;
        double dy = y - riderY;
        double dz = z - riderZ;
        return dx * dx + dy * dy + dz * dz <= RIDER_POS_RANGE * RIDER_POS_RANGE;
    }

    /**
     * 업링크 좌표가 권위가 받아들일 수 있는 값인가(유한성 + 월드 경계 + leash). 좌석 일치는
     * 좌석 원장을 가진 호출자가 먼저 본다.
     */
    public static boolean acceptableUplink(double riderX, double riderY, double riderZ,
            double x, double y, double z, double yaw) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Double.isFinite(yaw)
                && withinRiderRange(riderX, riderY, riderZ, x, y, z);
    }
}
