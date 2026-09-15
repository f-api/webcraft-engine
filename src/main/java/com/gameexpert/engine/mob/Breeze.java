package com.gameexpert.engine.mob;

import java.util.List;

/**
 * 브리즈(바닐라 실존 종, stableId 94). 수치 근거는 {@link MobType#BREEZE} 의 인라인 인용이다
 * (MC Java 1.21.4 {@code EntityType.BREEZE sized(0.6F, 1.77F)} · {@code createAttributes()}
 * MAX_HEALTH 30 · MOVEMENT_SPEED 0.63 · FOLLOW_RANGE 24).
 *
 * <p><b>시련 소환기 전용</b>: 바닐라에 자연 스폰 항목이 없다. WebCraft 의 시련 소환 풀은
 * 이 종을 {@code { name: "BREEZE", registered: false }} 로 이미 예약해 두었고, 이 웨이브가
 * 그 한 줄을 {@code registered: true} 로 바꾼다 — 풀의 <b>구성과 순서는 그대로</b>이므로
 * 다른 종의 상대 확률이 바뀌지 않는다.
 *
 * <p><b>바람 돌진</b>: {@value #WIND_CHARGE_RANGE} 블록 안의 표적에게
 * pinned 26.3 Shoot behavior의 15 MC-tick inhale, 4 MC-tick recovery, 10 MC-tick cooldown을
 * 10 TPS authority cadence로 보존한다. 직격 피해는 1이고 반경 3 trigger-only burst가 넉백을 낸다.
 */
public final class Breeze extends MeleeMob {

    /** wind charge 사거리(블록). 바닐라 FOLLOW_RANGE 24 안이다. */
    public static final double WIND_CHARGE_RANGE = 16.0;
    public static final int WIND_CHARGE_CHARGE_TICKS = 8;
    public static final int WIND_CHARGE_RECOVER_TICKS = 2;
    public static final int WIND_CHARGE_COOLDOWN_TICKS = 5;
    /** 0.7 blocks/MC-tick converted to the 10 TPS authority step. */
    public static final double WIND_CHARGE_SPEED = 1.4;
    /** wind charge 직격 피해. 바닐라도 1 이고 본체는 넉백이다. */
    public static final int WIND_CHARGE_DAMAGE = 1;

    public Breeze(long id, double x, double y, double z) {
        super(id, MobType.BREEZE, x, y, z);
    }

    private int shootPhaseTicks;
    private int recoverTicks;

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (isDead()) return List.of();
        if (attackCd > 0) attackCd--;
        PlayerSnapshot target = trackedTarget(world, WIND_CHARGE_RANGE, true);
        if (target == null || !canSeeTargetNow(world, target)) {
            shootPhaseTicks = 0;
            recoverTicks = 0;
            synchronizeVisualAction("none", "idle", 0);
            double[] mv = wander(rng, moveSpeed());
            state = mv[0] == 0 && mv[1] == 0 ? MobState.IDLE : MobState.WANDER;
            MobPhysics.tickMove(this, world, mv[0], 0, mv[1], MoveMode.WALK);
            return List.of();
        }
        faceToward(target.x(), target.z());
        state = MobState.ATTACK;
        if (recoverTicks > 0) {
            recoverTicks--;
            synchronizeVisualAction("ranged_release", "recovery", Math.max(1, recoverTicks));
            return List.of();
        }
        if (attackCd > 0) {
            synchronizeVisualAction("none", "idle", 0);
            return List.of();
        }
        if (shootPhaseTicks == 0) {
            shootPhaseTicks = WIND_CHARGE_CHARGE_TICKS;
            synchronizeVisualAction("ranged_charge", "anticipation", shootPhaseTicks);
            return List.of();
        }
        if (--shootPhaseTicks > 0) {
            synchronizeVisualAction("ranged_charge", "anticipation", shootPhaseTicks);
            return List.of();
        }
        double sx = x;
        // Breeze#getFiringYPosition: feet + half body height + 0.3.
        double sy = y + height() * 0.5 + 0.3;
        double sz = z;
        double dx = target.x() - sx;
        // LivingEntity#getY(0.3): target feet + 30% of the 1.8-block player body.
        double dy = target.y() + 0.54 - sy;
        double dz = target.z() - sz;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 1e-9) return List.of();
        double uncertainty = 5.0 - world.difficulty().id() * 4.0;
        double deviation = ProjectileSim.INACCURACY_NOISE_SCALE * uncertainty;
        dx = dx / length + triangle(rng, deviation);
        dy = dy / length + triangle(rng, deviation);
        dz = dz / length + triangle(rng, deviation);
        attackCd = WIND_CHARGE_COOLDOWN_TICKS;
        recoverTicks = WIND_CHARGE_RECOVER_TICKS;
        synchronizeVisualAction("ranged_release", "active", recoverTicks);
        return List.of(
                new MobEvent.ShootArrow(ProjectileSim.Kind.BREEZE_WIND_CHARGE,
                        sx, sy, sz, dx * WIND_CHARGE_SPEED,
                        dy * WIND_CHARGE_SPEED, dz * WIND_CHARGE_SPEED,
                        WIND_CHARGE_DAMAGE),
                new MobEvent.Sound("attack"));
    }

    @Override protected double detectRange() { return 24.0; }
    @Override protected double attackRange() { return 0.0; }
    @Override protected int attackDamage() { return WIND_CHARGE_DAMAGE; }
    @Override protected int attackCooldownTicks() { return WIND_CHARGE_COOLDOWN_TICKS; }
    @Override protected boolean climbWalls() { return false; }

    /** RandomSource#triangle(0, deviation), including its two-sample consumption order. */
    private static double triangle(MobRandom rng, double deviation) {
        return (rng.nextDouble() - rng.nextDouble()) * deviation;
    }
}
