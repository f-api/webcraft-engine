package com.gameexpert.engine.mob;

import java.util.List;
import com.gameexpert.engine.ChestInventory;
import com.gameexpert.engine.inventory.PlayerInventory;

/**
 * 낙타. 바닐라 {@code Camel} 의 <b>상태</b>만 얹고 수치·판정은 전부 {@link CamelRules} 에 있다.
 *
 * <p>말 계열과 갈리는 지점은 셋이다:
 * <ol>
 *   <li>길들이기가 없다(바닐라 {@code Camel} 은 {@code isTamed()} 가 언제나 참).
 *       안장만 있으면 누구나 조종한다.</li>
 *   <li>좌석이 둘이다. 조종석(0)만 좌표 업링크를 올리고 뒷좌석(1)은 순수 승객이다
 *       ({@link MobMountRules} 다인승 계약의 첫 소비자).</li>
 *   <li>점프 키가 도약이 아니라 <b>전방 대시</b>이며 쿨다운 게이지를 갖는다.</li>
 * </ol>
 */
final class Camel extends AnimalMob {

    /** 좌석 원장. 인덱스가 곧 {@code seatIndex} 다. */
    private final String[] seatRiders = new String[CamelRules.SEAT_COUNT];
    private final ChestInventory equipment = new ChestInventory(2);
    /** 앉은 자세인가. 바닐라 {@code Camel#isCamelSitting} 이다. */
    private boolean sitting;
    /** 유휴가 이어진 MC 틱 수. {@link CamelRules#IDLE_MINIMAL_MC_TICKS} 에서 앉는다. */
    private int idleMcTicks;
    /** 남은 대시 쿨다운(MC 틱). 0 이어야 대시할 수 있다. */
    private int dashCooldownMcTicks;

    Camel(long id, double x, double y, double z) {
        super(MobType.CAMEL, id, x, y, z, null);
    }

    // ── 좌석 ──────────────────────────────────────────────────────────
    String seatRiderNickname(int seatIndex) {
        return MobMountRules.validSeat(MobType.CAMEL, seatIndex) ? seatRiders[seatIndex] : null;
    }

    /**
     * 앉아 있어도 태울 수 있다(바닐라). 태우면 즉시 일어선다.
     *
     * <p><b>탑승은 안장을 묻지 않는다.</b> 바닐라 {@code AbstractHorse#mobInteract} 는 안장이
     * 없으면 그대로 {@code doPlayerRide} 로 떨어지고, {@code isSaddled()} 를 보는 것은
     * {@code getControllingPassenger()} 뿐이다. {@code Camel extends AbstractHorse} 라 낙타도
     * 같은 문을 쓰며(길들이기만 없다), 안장 게이트는 {@link MobMountRules#steerable} 이 지킨다 —
     * {@link #applyRiderPosition}·{@link #tryDash} 가 그 게이트다. 말·당나귀와 글자 그대로 같은
     * 계약이다.
     */
    boolean mount(String nickname, int seatIndex) {
        if (isDead() || removed || isBaby()) return false;
        if (!MobMountRules.validSeat(MobType.CAMEL, seatIndex)) return false;
        if (nickname == null || nickname.isEmpty()) return false;
        if (seatRiders[seatIndex] != null) return false;
        for (String seated : seatRiders) if (nickname.equals(seated)) return false;
        seatRiders[seatIndex] = nickname;
        standUp();
        setPersistenceRequired(true);
        return true;
    }

    boolean dismount(String nickname, int seatIndex) {
        if (!MobMountRules.validSeat(MobType.CAMEL, seatIndex)) return false;
        if (seatRiders[seatIndex] == null || !seatRiders[seatIndex].equals(nickname)) return false;
        seatRiders[seatIndex] = null;
        setPersistenceRequired(true);
        return true;
    }

    void clearCamelSeat(int seatIndex) {
        if (!MobMountRules.validSeat(MobType.CAMEL, seatIndex)) return;
        seatRiders[seatIndex] = null;
    }

    boolean occupied() {
        for (String seated : seatRiders) if (seated != null) return true;
        return false;
    }

    /**
     * 조종석 기수 좌표 업링크. 말·돼지와 같은 계약이며 <b>안장</b>도 같은 조건이다 —
     * {@code Camel extends AbstractHorse} 라 {@code getControllingPassenger} 의
     * {@code isSaddled()} 가드를 그대로 물려받는다(낙타는 길들이기만 없다).
     */
    boolean applyRiderPosition(String nickname, double x, double y, double z, double yaw) {
        if (!MobMountRules.steerable(MobType.CAMEL, saddled())) return false;
        String pilot = seatRiders[MobMountRules.CONTROLLING_SEAT_INDEX];
        if (pilot == null || !pilot.equals(nickname)) return false;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        vy = 0;
        horizontalVx = 0;
        horizontalVz = 0;
        knockbackVx = 0;
        knockbackVz = 0;
        return true;
    }

    // ── 안장 ──────────────────────────────────────────────────────────
    boolean saddled() { return equipment.itemType(0) == PlayerInventory.SADDLE; }
    ChestInventory equipment() { return equipment; }

    /** 바닐라: 길들이기 없이 안장만 얹으면 조종할 수 있다. 새끼는 안장을 받지 않는다. */
    boolean saddle() {
        return saddle(new PlayerInventory.StackSnapshot(
                PlayerInventory.SADDLE, 1, 0, 0, 0, 0, null, null));
    }

    boolean saddle(PlayerInventory.StackSnapshot saddle) {
        if (isDead() || removed || saddled() || isBaby()
                || saddle == null || saddle.count() != 1
                || saddle.itemType() != PlayerInventory.SADDLE) return false;
        return putHorseMenuEquipment(equipment, 0, saddle);
    }

    // ── 앉기/일어서기 ─────────────────────────────────────────────────
    boolean sitting() { return sitting; }
    int idleMcTicks() { return idleMcTicks; }

    void sitDown() {
        if (sitting || occupied()) return;
        sitting = true;
        idleMcTicks = 0;
        setPersistenceRequired(true);
    }

    void standUp() {
        if (!sitting) return;
        sitting = false;
        idleMcTicks = 0;
        setPersistenceRequired(true);
    }

    /** 앉은 자세의 AABB 높이. 서 있으면 종 프로필 높이 그대로다. */
    double poseHeight() {
        return (sitting ? CamelRules.sittingHeight() : CamelRules.HEIGHT)
                * (isBaby() ? height() / CamelRules.HEIGHT : 1.0);
    }

    // ── 대시 ──────────────────────────────────────────────────────────
    int dashCooldownMcTicks() { return dashCooldownMcTicks; }

    /**
     * 대시 검증. 권위는 임펄스를 굴리지 않고 "안장 + 조종석 기수 + 쿨다운 0 + 차지 범위"만
     * 본다(말 점프와 같은 신뢰 모델). 받아들이면 쿨다운이 그 자리에서 채워진다.
     */
    boolean tryDash(String nickname, int charge) {
        if (!saddled() || isDead() || removed) return false;
        if (charge < 0 || charge > CamelRules.MAX_DASH_CHARGE) return false;
        if (dashCooldownMcTicks > 0) return false;
        String pilot = seatRiders[MobMountRules.CONTROLLING_SEAT_INDEX];
        if (pilot == null || !pilot.equals(nickname)) return false;
        dashCooldownMcTicks = CamelRules.DASH_COOLDOWN_MC_TICKS;
        standUp();
        return true;
    }

    @Override public int visualFlags() {
        int flags = saddled() ? Mob.VISUAL_CAMEL_SADDLED : 0;
        if (sitting) flags |= Mob.VISUAL_CAMEL_SITTING;
        return flags;
    }

    void restoreState(boolean restoredSaddled, boolean restoredSitting) {
        equipment.clearForRestore();
        if (restoredSaddled) equipment.restoreSlot(0, PlayerInventory.SADDLE, 1, null);
        sitting = restoredSitting;
        idleMcTicks = 0;
        dashCooldownMcTicks = 0;
        // 재시작·언로드 뒤에는 기수 세션이 없으므로 좌석은 비운 채로 복원한다(말·돼지와 같다).
        for (int seat = 0; seat < seatRiders.length; seat++) seatRiders[seat] = null;
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        // 탑승 중에는 조종석 클라가 좌표 정본이라 서버 AI 이동을 돌리지 않는다(말과 같다).
        if (occupied()) {
            state = MobState.IDLE;
            idleMcTicks = 0;
            tickDashCooldown();
            return List.of();
        }
        List<MobEvent> events = super.tick(world, rng);
        tickDashCooldown();
        // 권위 틱(10 TPS)은 MC 틱 2개다. 앉기 판정은 유휴가 이어질 때만 누적한다.
        if (state == MobState.IDLE && !sitting) {
            idleMcTicks += 2;
            if (idleMcTicks >= CamelRules.IDLE_MINIMAL_MC_TICKS) sitDown();
        } else if (state != MobState.IDLE) {
            idleMcTicks = 0;
            standUp();
        }
        return events;
    }

    private void tickDashCooldown() {
        if (dashCooldownMcTicks <= 0) return;
        dashCooldownMcTicks = Math.max(0, dashCooldownMcTicks - 2);
    }
}
