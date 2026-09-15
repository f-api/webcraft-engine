package com.gameexpert.engine.mob;

/**
 * 뿔·엄니 <b>돌진</b>을 쓰는 좀비 동물의 공통 근접 AI. 계약과 상수는
 * {@link UndeadAnimalRules} 의 {@code CHARGE_*} 가 소유하고 여기에는 그 계약의 상태 기계만 있다
 * — 좀비 염소·무덤 사슴·부패 멧돼지가 <b>같은</b> 구현을 공유한다(종별 사본 금지).
 *
 * <p>돌진은 <b>난수를 하나도 소비하지 않는</b> 결정적 판정이다. 그래서 이 계열을 추가해도
 * 결정 트레이스의 난수 소비 수열이 흔들리지 않고, 정적판 사본
 * ({@code StandaloneMobRuntime} 의 {@code tickUndeadCharge})과 틱 단위로 같은 값을 낸다.
 *
 * <p>상태는 둘뿐이다: 남은 돌진 틱({@code chargeTicks})과 남은 쿨다운({@code chargeCooldown}).
 * 둘 다 <b>영속하지 않는</b> 런타임 상태다 — 재접속하면 쿨다운 0 · 돌진 없음에서 다시 시작한다
 * (좀비 피그맨 분노와 달리 지속 시간이 짧아 저장할 가치가 없다).
 */
abstract class ChargingUndeadAnimal extends MeleeMob {

    /** 남은 돌진 틱. 0 보다 크면 돌진 중이다. */
    private int chargeTicks;
    /** 남은 돌진 쿨다운 틱. */
    private int chargeCooldown;

    ChargingUndeadAnimal(long id, MobType type, double x, double y, double z) {
        super(id, type, x, y, z);
    }

    /** 표적을 향한 평상시 추격 속도(블록/10TPS 틱). */
    protected abstract double pursuitSpeed();

    /** 이 개체가 지금 돌진을 더 쓸 수 있는가. 사슴만 횟수 제한으로 이걸 조인다. */
    protected boolean mayCharge() {
        return true;
    }

    /** 돌진이 실제로 시작된 순간의 훅. 사슴이 남은 횟수를 깎는 데 쓴다. */
    protected void onChargeStarted() {
    }

    /** 돌진 중인가. 시각 상태·피해 보정·정적판 대조가 읽는다. */
    final boolean charging() {
        return chargeTicks > 0;
    }

    final int chargeCooldownTicks() {
        return chargeCooldown;
    }

    /**
     * 돌진 상태를 한 틱 진행한다. {@link #chaseMovement} 가 매 추격 틱에 정확히 한 번 부른다 —
     * 공격 사거리 안에 있는 틱에는 부르지 않으므로 붙어 있는 동안에는 쿨다운이 흐르지 않는다.
     */
    private void advanceCharge(double distance) {
        if (chargeTicks > 0) {
            if (--chargeTicks == 0) chargeCooldown = UndeadAnimalRules.CHARGE_COOLDOWN_TICKS;
            return;
        }
        if (chargeCooldown > 0) {
            chargeCooldown--;
            return;
        }
        if (mayCharge() && UndeadAnimalRules.chargeReady(distance, chargeCooldown)) {
            chargeTicks = UndeadAnimalRules.CHARGE_TICKS;
            onChargeStarted();
        }
    }

    /** 돌진 중에는 추격 속도가 배가 된다. */
    @Override
    protected double[] chaseMovement(MobWorldView world, PlayerSnapshot target, MobRandom rng) {
        advanceCharge(dist3d(target));
        double speed = charging()
                ? pursuitSpeed() * UndeadAnimalRules.CHARGE_SPEED_SCALE
                : pursuitSpeed();
        return towardHoriz(target.x(), target.z(), speed);
    }

    /** 돌진 중에 닿은 타격만 더 아프다. */
    @Override
    protected int attackDamage(MobRandom rng) {
        return charging()
                ? attackDamage() + UndeadAnimalRules.CHARGE_DAMAGE_BONUS
                : attackDamage();
    }

    /** 표적을 잃으면 돌진 상태를 되돌린다(다음 조우가 깨끗한 상태에서 시작한다). */
    final void resetCharge() {
        chargeTicks = 0;
        chargeCooldown = 0;
    }
}
