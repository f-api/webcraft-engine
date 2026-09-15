package com.gameexpert.engine.mob;

import java.util.List;

/**
 * 돼지의 안장 장착·탑승·당근 낚싯대 부스트. AI 는 {@link AnimalMob} 그대로이고
 * 여기에는 바닐라 {@code Pig} + {@code ItemBasedSteering} 의 상태만 얹는다.
 *
 * <p>좌표 권위는 보트와 같은 계약을 쓴다: 탑승 중에는 <b>기수 클라이언트</b>가 위치를 올리고
 * 서버는 도달 거리·탑승자 일치만 검증한다(CONTRACT §1 클라 권위 이동 경계와 같은 근거).
 * 그래서 탑승 중 돼지는 서버 AI 이동을 멈춘다.
 */
public final class Pig extends AnimalMob {
    private boolean saddled;
    private String riderNickname;
    private int boostMcTicks;
    private int boostTotalMcTicks;

    /** [FARM-VARIANT] {@code variant} 는 1.21.5 기후 변종(temperate/warm/cold). */
    Pig(long id, double x, double y, double z, String variant) {
        super(MobType.PIG, id, x, y, z, variant);
    }

    public boolean saddled() { return saddled; }

    public String riderNickname() { return riderNickname; }

    public int boostMcTicks() { return boostMcTicks; }

    public int boostTotalMcTicks() { return boostTotalMcTicks; }

    /** 바닐라: 새끼가 아니고 아직 안장이 없을 때만 안장 한 개를 소비한다. */
    public boolean saddle() {
        if (isDead() || removed || saddled || isBaby()) return false;
        saddled = true;
        setPersistenceRequired(true);
        return true;
    }

    /** 바닐라 {@code Pig.mobInteract}: 안장이 있고 이미 탄 기수가 없어야 탑승한다. */
    public boolean mount(String nickname) {
        if (isDead() || removed || !saddled || isBaby()
                || riderNickname != null || nickname == null || nickname.isEmpty()) return false;
        riderNickname = nickname;
        setPersistenceRequired(true);
        return true;
    }

    /** 하차. 실제로 타고 있던 기수일 때만 참을 돌려준다. */
    public boolean dismount(String nickname) {
        if (riderNickname == null || !riderNickname.equals(nickname)) return false;
        riderNickname = null;
        boostMcTicks = 0;
        boostTotalMcTicks = 0;
        setPersistenceRequired(true);
        return true;
    }

    /**
     * 기수 클라가 올린 좌표를 반영한다(보트 운전자 업링크와 같은 계약). 실제 기수이고
     * <b>안장이 있을 때만</b> 참을 돌려주며(바닐라 {@code Pig#getControllingPassenger} 도
     * {@code isSaddled()} 를 먼저 본다), 호출자가 월드 경계·기수 pose 근접을 먼저 검증한다.
     */
    public boolean applyRiderPosition(String nickname, double x, double y, double z, double yaw) {
        if (!MobMountRules.steerable(MobType.PIG, saddled)) return false;
        if (riderNickname == null || !riderNickname.equals(nickname)) return false;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        // 좌표 정본이 기수 클라라 서버가 들고 있던 잔여 속도는 의미가 없다(Mob.lockToVehicle 과 같다).
        vy = 0;
        horizontalVx = 0;
        horizontalVz = 0;
        knockbackVx = 0;
        knockbackVz = 0;
        return true;
    }

    /** 기수가 사라졌을 때(사망·퇴장) 좌석만 비운다. */
    public void clearRider() {
        riderNickname = null;
        boostMcTicks = 0;
        boostTotalMcTicks = 0;
    }

    /**
     * 바닐라 {@code ItemBasedSteering.boost}: 이미 부스트 중이면 실패한다.
     * 성공하면 당근 낚싯대가 7 만큼 닳는다(호출자 책임).
     */
    public boolean boost(String nickname, MobRandom rng) {
        if (riderNickname == null || !riderNickname.equals(nickname) || boostMcTicks > 0) {
            return false;
        }
        boostTotalMcTicks = FarmAnimalRules.nextPigBoostMcTicks(rng);
        boostMcTicks = 1;
        setPersistenceRequired(true);
        return true;
    }

    /** 현재 부스트 배율(1.0~2.15). 클라 조종 속도와 같은 값을 서버도 계산해 둔다. */
    public double boostFactor() {
        return FarmAnimalRules.pigBoostFactor(boostMcTicks, boostTotalMcTicks);
    }

    void restorePigState(boolean restoredSaddled, String restoredRider,
                         int restoredBoostMcTicks, int restoredBoostTotalMcTicks) {
        if (restoredBoostMcTicks < 0 || restoredBoostTotalMcTicks < 0
                || restoredBoostMcTicks > restoredBoostTotalMcTicks
                || restoredBoostTotalMcTicks > FarmAnimalRules.PIG_BOOST_MIN_MC_TICKS
                        + FarmAnimalRules.PIG_BOOST_ROLL_BOUND
                || !restoredSaddled && (restoredRider != null
                        || restoredBoostMcTicks != 0 || restoredBoostTotalMcTicks != 0)) {
            throw new IllegalArgumentException("invalid persisted Pig saddle/boost state");
        }
        saddled = restoredSaddled;
        // 재시작·언로드 뒤에는 기수 세션이 없으므로 좌석은 비운 채로 복원한다.
        riderNickname = null;
        boostMcTicks = restoredBoostMcTicks;
        boostTotalMcTicks = restoredBoostTotalMcTicks;
    }

    @Override public int visualFlags() {
        int flags = saddled ? Mob.VISUAL_PIG_SADDLED : 0;
        if (boostMcTicks > 0) flags |= Mob.VISUAL_PIG_BOOSTING;
        return flags;
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        // 탑승 중에는 기수 클라이언트가 좌표 정본이라 서버 AI 이동을 돌리지 않는다.
        List<MobEvent> events = riderNickname == null
                ? super.tick(world, rng)
                : List.of();
        if (riderNickname != null) state = MobState.IDLE;
        // 바닐라 tickBoost 는 20 TPS 라 권위 틱마다 두 칸 전진한다.
        for (int step = 0; step < 2 && boostMcTicks > 0; step++) {
            boostMcTicks++;
            if (boostMcTicks > boostTotalMcTicks) {
                boostMcTicks = 0;
                boostTotalMcTicks = 0;
            }
        }
        return events;
    }
}
