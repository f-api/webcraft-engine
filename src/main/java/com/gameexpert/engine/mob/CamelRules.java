package com.gameexpert.engine.mob;

import com.gameexpert.terrain.Blocks;

/**
 * 낙타의 <b>상태 없는</b> 바닐라 규칙. 근거는 Minecraft Java Edition 1.21.4 의
 * {@code net.minecraft.world.entity.animal.camel.Camel} 과 {@code EntityType.CAMEL} 이며,
 * 각 상수 옆에 원문 식별자를 적어 둔다. 정적판 사본은
 * {@code client/src/backend/standalone/StandaloneCamelRules.ts} 이고, 두 사본의 상수·판정
 * 동일성은 {@code StandaloneCamelRules.test.ts} 가 이 파일 원문을 읽어 강제한다.
 *
 * <p>낙타는 말 계열과 달리 <b>길들이기가 없다</b>({@code Camel} 은 {@code AbstractHorse} 를
 * 상속하지만 {@code isTamed()} 가 언제나 참이라 {@code RunAroundLikeCrazyGoal} 이 없다).
 * 대신 안장이 조종 조건이고, 좌석이 둘이며, 점프 키가 도약이 아니라 <b>전방 대시</b>다.
 *
 * <p>WebCraft divergence 는 파일 끝 주석에 모아 둔다.
 */
public final class CamelRules {

    private CamelRules() {
    }

    // ── 개체 상수({@code EntityType.CAMEL} / {@code Camel#createAttributes}) ──
    /** {@code EntityType.Builder.of(Camel::new, MobCategory.CREATURE).sized(1.7F, 2.375F)}. */
    public static final double WIDTH = 1.7;
    public static final double HEIGHT = 2.375;
    /** 같은 빌더의 {@code .eyeHeight(2.275F)}. 높이 × 0.85 가 아니라 명시값이다. */
    public static final double EYE_HEIGHT = 2.275;
    /** {@code Camel#createAttributes}: {@code Attributes.MAX_HEALTH, 32.0}. */
    public static final int MAX_HEALTH = 32;
    /** 같은 곳의 {@code Attributes.MOVEMENT_SPEED, 0.09F}. */
    public static final double MOVEMENT_SPEED = 0.09;
    /** 같은 곳의 {@code Attributes.JUMP_STRENGTH, 0.42}. 낙타는 이 값으로 <b>대시</b>한다. */
    public static final double JUMP_STRENGTH = 0.42;
    /** 같은 곳의 {@code Attributes.STEP_HEIGHT, 1.5} — 울타리·담을 그대로 넘는다. */
    public static final double STEP_HEIGHT = 1.5;

    // ── 좌석({@code Camel#getPassengerAttachmentPoint}) ────────────────
    /**
     * 바닐라 승객 정원. {@code Camel#canAddPassenger} 는
     * {@code this.getPassengers().size() < 2} 다.
     */
    public static final int SEAT_COUNT = 2;
    /**
     * {@code Camel#getPassengerAttachmentPoint} 의 로컬 전후 오프셋 {@code f}(블록, +가 앞):
     * 첫 승객(조종석) 0.5F, 승객이 둘일 때 뒷좌석 -0.7F.
     */
    public static final double SEAT_FORWARD_OFFSET_FRONT = 0.5;
    public static final double SEAT_FORWARD_OFFSET_REAR = -0.7;

    /** 좌석의 로컬 전후 오프셋. 좌석 범위 밖이면 호출자가 먼저 막는다. */
    public static double seatForwardOffset(int seatIndex) {
        return seatIndex == MobMountRules.CONTROLLING_SEAT_INDEX
                ? SEAT_FORWARD_OFFSET_FRONT : SEAT_FORWARD_OFFSET_REAR;
    }

    // ── 앉기/일어서기({@code Camel} 자세 전이) ─────────────────────────
    /** {@code Camel.SITDOWN_DURATION_TICKS = 40} (MC 틱). */
    public static final int SIT_DOWN_MC_TICKS = 40;
    /** {@code Camel.STANDUP_DURATION_TICKS = 52} (MC 틱). */
    public static final int STAND_UP_MC_TICKS = 52;
    /**
     * {@code Camel.IDLE_MINIMAL_DURATION_TICKS = 80} (MC 틱). 낙타는 이 시간만큼 유휴가
     * 이어져야 앉기 판정을 한다({@code CamelAi} 의 유휴 활동).
     */
    public static final int IDLE_MINIMAL_MC_TICKS = 80;
    /**
     * {@code Camel.SITTING_HEIGHT_DIFFERENCE = 1.43F}. 앉은 자세의 AABB 높이는
     * {@code HEIGHT - 1.43 = 0.945} 이며, 이는 위키가 싣는 앉은 낙타 히트박스와 정확히 같다.
     */
    public static final double SITTING_HEIGHT_DIFFERENCE = 1.43;

    /** 앉은 자세의 AABB 높이. */
    public static double sittingHeight() { return HEIGHT - SITTING_HEIGHT_DIFFERENCE; }

    // ── 대시({@code Camel#executeRidersJump}) ─────────────────────────
    /**
     * {@code Camel.DASH_COOLDOWN_TICKS = 55} (MC 틱 = 2.75초). 위키의 "resets every
     * 2.75 seconds" 와 같고, 베드락 {@code minecraft:dash} 의 {@code cooldown_time: 2.75} 와도
     * 같다. 권위 틱은 10 TPS 라 {@link #DASH_COOLDOWN_AUTHORITY_TICKS} 로 환산해 쓴다.
     */
    public static final int DASH_COOLDOWN_MC_TICKS = 55;
    /** 위 값을 권위 틱(10 TPS)으로 올림 환산한 값. 20 MC틱 = 10 권위틱. */
    public static final int DASH_COOLDOWN_AUTHORITY_TICKS =
            (DASH_COOLDOWN_MC_TICKS + 1) / 2;
    /**
     * {@code Camel.DASH_HORIZONTAL_MOMENTUM = 22.2222F}. 대시 임펄스의 수평 계수다:
     * {@code lookAngle * (22.2222 * scale * jumpStrength * blockSpeedFactor)}.
     */
    public static final double DASH_HORIZONTAL_MOMENTUM = 22.2222;
    /**
     * {@code Camel.DASH_VERTICAL_MOMENTUM = 1.4285F}. 수직 성분은
     * {@code 1.4285 * scale * jumpStrength} 이며, 만충전에서 {@code 1.4285 × 0.42 = 0.5999…}
     * 로 베드락 {@code minecraft:dash} 의 {@code vertical_momentum: 0.6} 과 일치한다
     * (두 에디션이 같은 도약 높이를 내는 교차 증거).
     */
    public static final double DASH_VERTICAL_MOMENTUM = 1.4285;
    /**
     * {@code Camel.RUNNING_SPEED_BONUS = 0.1F}. 기수가 달리기 중이고 대시 쿨다운이 0 이면
     * 탑승 속도 속성에 더해진다({@code Camel#getRiddenSpeed}).
     *
     * <p>교차 증거: 위키의 낙타 탑승 속도 3.885블록/초(걷기)와 8.203블록/초(달리기)의 비
     * 2.112 는 속성 0.19/0.09 = 2.111 과 같다 — 즉 걷기는 속성 그대로이고 달리기만 +0.1 이다.
     */
    public static final double RUNNING_SPEED_BONUS = 0.1;

    /** 차지 게이지 상한. 말과 같은 {@code Mth.floor(jumpRidingScale * 100)} 공간이다. */
    public static final int MAX_DASH_CHARGE = HorseRules.MAX_JUMP_CHARGE;

    /**
     * 차지 → 배율. 바닐라는 낙타도 {@code AbstractHorse#handleStartJump} 경로를 그대로 쓰므로
     * 말과 같은 {@code onPlayerJump} 식이다. 한 곳에만 두려고 {@link HorseRules#jumpScale} 에
     * 위임한다.
     */
    public static double dashScale(int charge) { return HorseRules.jumpScale(charge); }

    /** 대시 수평 초기 속도(블록/MC 틱). {@code getBlockSpeedFactor()} 는 1.0 을 가정한다. */
    public static double dashHorizontalPerMcTick(int charge) {
        return DASH_HORIZONTAL_MOMENTUM * dashScale(charge) * JUMP_STRENGTH;
    }

    /** 대시 수직 초기 속도(블록/MC 틱). */
    public static double dashVerticalPerMcTick(int charge) {
        return DASH_VERTICAL_MOMENTUM * dashScale(charge) * JUMP_STRENGTH;
    }

    /** 클라 물리는 초 단위라 20 TPS 로 환산해 쓴다(블록/초). */
    public static double dashHorizontalBlocksPerSecond(int charge) {
        return dashHorizontalPerMcTick(charge) * 20.0;
    }

    public static double dashVerticalBlocksPerSecond(int charge) {
        return dashVerticalPerMcTick(charge) * 20.0;
    }

    /** 탑승 중 낙타의 수평 속도(블록/초). {@code Camel#getRiddenSpeed} 다. */
    public static double riddenSpeedBlocksPerSecond(boolean sprinting, int dashCooldownTicks) {
        double attribute = MOVEMENT_SPEED
                + (sprinting && dashCooldownTicks == 0 ? RUNNING_SPEED_BONUS : 0.0);
        return attribute * FarmAnimalRules.MOVEMENT_SPEED_BLOCKS_PER_SECOND;
    }

    // ── 먹이({@code Camel#isFood}) ────────────────────────────────────
    /**
     * {@code Camel#isFood(ItemStack)} 은 {@code stack.is(Items.CACTUS)} 다. WebCraft 의
     * 선인장 블록 아이템 ID 가 그대로 먹이다.
     */
    public static boolean isBreedingFood(short itemType) {
        return itemType == (short) Blocks.CACTUS;
    }

    // ── WebCraft divergence ────────────────────────────────────────────
    // 1. 바닐라 낙타는 앉은 상태에서도 탑승할 수 있고, 태우면 일어선다. WebCraft 도 같지만
    //    일어서는 애니메이션 시간(STAND_UP_MC_TICKS)은 클라 표현이고 권위는 자세 플래그만
    //    즉시 뒤집는다 — 권위가 물리를 굴리지 않기 때문이다(좌석 계약 §2).
    // 2. 바닐라 대시는 실제 delta movement 임펄스지만 WebCraft 권위는 좌표 정본이 아니라
    //    검증자다. 그래서 대시는 "쿨다운이 0 이고 안장이 있고 조종석 기수인가"만 보고,
    //    임펄스 자체는 조종석 클라가 위 식으로 만든다(말 점프와 같은 신뢰 모델).
    // 3. 앉기 AI 는 바닐라 brain(CamelAi) 이 아니라 WebCraft 의 유휴 카운터로 굴린다. 발화
    //    조건(IDLE_MINIMAL_MC_TICKS 만큼 유휴)과 전이 시간만 바닐라 상수를 쓴다.
    // 4. 속성 → 블록/초 환산은 말·돼지와 <b>같은 한 곳</b>
    //    ({@link FarmAnimalRules#MOVEMENT_SPEED_BLOCKS_PER_SECOND} = 42.16)에서 온다. 위키가
    //    싣는 낙타 실측(3.885 / 8.203)은 계수 43.17 에 해당해 1보다 작은 % 차이가 나지만,
    //    두 계수를 동시에 두면 종마다 속도 공간이 갈라지므로 하나만 쓴다.
}
