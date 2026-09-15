package com.gameexpert.engine.mob;

import java.util.List;

/**
 * 해피 가스트(바닐라 실존 종, stableId 95). 수치 근거는 {@link MobType#HAPPY_GHAST} 의
 * 인라인 인용이다(MC Java 1.21.6 {@code EntityType.HAPPY_GHAST sized(4.0F, 4.0F)} ·
 * {@code createAttributes()} MAX_HEALTH 20 · FLYING_SPEED 0.05 · MOVEMENT_SPEED 0.05).
 *
 * <p><b>온순하다</b> — 바닐라에도 공격 goal 이 없다. 그래서 {@link AnimalMob} 의 배회 골격을
 * 그대로 쓰고 {@code movementMedium} 만 {@code "fly"} 로 갈린다.
 *
 * <p><b>가스틀링은 이 종의 새끼 형태다</b> — 별도 종이 아니다. [B] minecraft.wiki «Ghastling»:
 * 가스틀링과 해피 가스트는 같은 엔티티 id {@code happy_ghast} 이고, 가스틀링은
 * {@value #GROWTH_TICKS} MC 틱(20분) 뒤 성체가 된다. 그래서 새 {@code MobType} 을 열지 않고
 * 피글린 새끼가 이미 쓰는 {@code babyForm}/{@code ageTicksRemaining} 인프라를 그대로 쓴다
 * ({@link Piglin} 의 성장 경계와 <b>글자 그대로 같은</b> {@link Mob#growOutOfBabyForm()} 호출).
 * 세우는 지점은 {@code sculk.GhastlingRevival} 하나뿐이다 — 말린 가스트 수화 사슬
 * ({@code sculk.DriedGhastHydrationSystem})이 네 구간을 다 채우면 부른다.
 *
 * <p><b>비행 탑승 4인승</b>(바닐라 1.21.6 하네스 규약): 하네스를 씌우면 좌석
 * {@value #SEAT_COUNT} 개가 열린다. 하네스는 낙타의 <b>안장과 같은 자리</b>에 있다 — 탑승
 * 게이트가 아니라 <b>조종 게이트</b>이고({@link MobMountRules#steerable}), 하네스가 없으면
 * 애초에 아무도 태우지 않는다는 점만 낙타와 다르다([B] «Happy Ghast»: 하네스 없는 해피
 * 가스트는 탈 수 없다). 수치·배치 규칙은 {@link HappyGhastRules} 가 소유한다.
 */
public final class HappyGhast extends AnimalMob {

    /**
     * 가스틀링 → 해피 가스트 성장에 걸리는 MC 틱(20분).
     * [B] minecraft.wiki «Ghastling» — "20 minutes (24000 game ticks) to grow up".
     */
    public static final int GROWTH_TICKS = 24_000;

    /**
     * 같은 성장 시간의 권위 틱(10 TPS). 20분은 권위 틱 12000 이고, 이는 이 저장소가 이미
     * 모든 새끼에게 쓰는 {@link MobRuntime#BABY_GROWTH_TICKS}({@code 20 * 60 * 10}) 와
     * <b>같은 값</b>이다 — 바닐라의 24000 MC 틱과 이 저장소의 12000 권위 틱이 둘 다 20분이라
     * 가스틀링만 별도 타이머를 갖지 않는다.
     */
    public static final int GROWTH_AUTHORITY_TICKS = GROWTH_TICKS / 2;

    /** 하네스가 여는 좌석 수. */
    public static final int SEAT_COUNT = 4;

    /** 좌석 원장. 인덱스가 곧 {@code seatIndex} 이고 0 이 조종석(얼굴 위 앞자리)이다. */
    private final String[] seatRiders = new String[SEAT_COUNT];
    /** 하네스를 쓰고 있는가. 바닐라 {@code HappyGhast} 의 장비 슬롯에 해당하는 영속 상태다. */
    private boolean harnessed;
    /** 쓰고 있는 하네스의 MC {@code DyeColor} 네트워크 ID(0..15). 없으면 -1. */
    private int harnessColor = -1;

    public HappyGhast(long id, double x, double y, double z) {
        super(MobType.HAPPY_GHAST, id, x, y, z);
    }

    @Override public String movementMedium() { return "fly"; }

    // ── 하네스 ────────────────────────────────────────────────────────
    boolean harnessed() { return harnessed; }

    /** 하네스 색(0..15). 쓰고 있지 않으면 -1 이다(양의 색 계약과 같은 어휘). */
    int harnessColor() { return harnessed ? harnessColor : -1; }

    /**
     * 하네스 장착. <b>성체만</b> 받는다 — [B] «Happy Ghast» 는 가스틀링(새끼 형태)에게 하네스를
     * 씌울 수 없다고 못박는다. 안장과 같은 계약으로 이미 쓰고 있으면 거절한다(덮어쓰지 않는다).
     */
    boolean harness(int color) {
        if (isDead() || removed || harnessed || isBaby()) return false;
        if (!HappyGhastRules.validHarnessColor(color)) return false;
        harnessed = true;
        harnessColor = color;
        setPersistenceRequired(true);
        return true;
    }

    // ── 좌석 ──────────────────────────────────────────────────────────
    String seatRiderNickname(int seatIndex) {
        return MobMountRules.validSeat(MobType.HAPPY_GHAST, seatIndex)
                ? seatRiders[seatIndex] : null;
    }

    /**
     * 좌석 착석. 낙타와 갈리는 지점은 하나뿐이다 — <b>하네스가 없으면 태우지 않는다</b>.
     * 바닐라 말·낙타는 안장 없이도 {@code doPlayerRide} 로 떨어지지만, 하네스 없는 해피
     * 가스트에는 앉을 자리 자체가 없다([B] «Happy Ghast»). 새끼(가스틀링)는 하네스를 받을 수
     * 없으므로 이 가드에 이미 걸린다.
     */
    boolean mount(String nickname, int seatIndex) {
        if (isDead() || removed || isBaby() || !harnessed) return false;
        if (!MobMountRules.validSeat(MobType.HAPPY_GHAST, seatIndex)) return false;
        if (nickname == null || nickname.isEmpty()) return false;
        if (seatRiders[seatIndex] != null) return false;
        for (String seated : seatRiders) if (nickname.equals(seated)) return false;
        seatRiders[seatIndex] = nickname;
        setPersistenceRequired(true);
        return true;
    }

    boolean dismount(String nickname, int seatIndex) {
        if (!MobMountRules.validSeat(MobType.HAPPY_GHAST, seatIndex)) return false;
        if (seatRiders[seatIndex] == null || !seatRiders[seatIndex].equals(nickname)) return false;
        seatRiders[seatIndex] = null;
        setPersistenceRequired(true);
        return true;
    }

    void clearGhastSeat(int seatIndex) {
        if (!MobMountRules.validSeat(MobType.HAPPY_GHAST, seatIndex)) return;
        seatRiders[seatIndex] = null;
    }

    boolean occupied() {
        for (String seated : seatRiders) if (seated != null) return true;
        return false;
    }

    /**
     * 조종석 기수 좌표 업링크. 낙타·말과 같은 계약이며 <b>하네스</b>가 그 종의 안장 자리다
     * ({@link MobMountRules#steerable}).
     */
    boolean applyRiderPosition(String nickname, double x, double y, double z, double yaw) {
        if (!MobMountRules.steerable(MobType.HAPPY_GHAST, harnessed)) return false;
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

    @Override public int visualFlags() {
        int flags = 0;
        if (harnessed) {
            flags |= Mob.VISUAL_HAPPY_GHAST_HARNESSED;
            if (harnessColor >= 0) {
                flags |= harnessColor << Mob.VISUAL_HAPPY_GHAST_HARNESS_COLOR_SHIFT
                        & Mob.VISUAL_HAPPY_GHAST_HARNESS_COLOR_MASK;
            }
        }
        for (String rider : seatRiders) {
            if (rider != null) {
                flags |= Mob.VISUAL_HAPPY_GHAST_RIDDEN;
                break;
            }
        }
        return flags;
    }

    /**
     * 저장된 하네스 상태를 복원한다. 좌석은 비운 채로 복원한다 — 재시작·언로드 뒤에는 기수
     * 세션이 없다(말·돼지·낙타와 같은 계약).
     */
    void restoreState(boolean restoredHarnessed, int restoredColor) {
        harnessed = restoredHarnessed;
        harnessColor = restoredHarnessed && HappyGhastRules.validHarnessColor(restoredColor)
                ? restoredColor : -1;
        // 색 없이 하네스만 참인 손상 행은 하네스 자체를 버린다(없는 색을 지어내지 않는다).
        if (harnessed && harnessColor < 0) harnessed = false;
        for (int seat = 0; seat < seatRiders.length; seat++) seatRiders[seat] = null;
    }

    /**
     * 성장 경계. {@link AnimalMob} 의 배회 골격이 이미 성장 타이머를 깎으므로, 여기서는 타이머를
     * 다 쓴 새끼 형태를 성체로 <b>정확히 한 번</b> 확정한다(피글린과 같은 자리·같은 호출).
     * {@code babyForm} 이 영속 필드라 재접속·언로드 복구가 이 경계를 다시 지나게 하지 않는다.
     */
    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        // 탑승 중에는 조종석 클라가 좌표 정본이라 서버 AI 이동을 돌리지 않는다(낙타·말과 같다).
        // 좌표 정본이 둘이 되면 기수와 탈것이 서로를 끌어당긴다. 새 난수를 소비하지 않도록
        // super.tick 을 건너뛰는 것도 낙타와 같은 자리·같은 이유다.
        if (occupied()) {
            state = MobState.IDLE;
            return List.of();
        }
        List<MobEvent> events = super.tick(world, rng);
        growOutOfBabyForm();
        return events;
    }
}
