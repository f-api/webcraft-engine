package com.gameexpert.engine.mob;

import java.util.List;

/**
 * 스켈레톤: 8~14블록 거리 유지(가까우면 후퇴, 멀면 접근, 중간이면 정지),
 * 20틱마다 사격. 화살 초기 속력 1.5블록/틱, 중력 보정 조준, 화살 피해 4. HP 20.
 * 실제 화살 시뮬레이션/피해는 {@link ProjectileSim} 가 담당(ShootArrow 이벤트 소비).
 */
public class Skeleton extends Mob {
    public static final double ARROW_SPEED = 1.5;
    /** 바닐라 normal 난이도 inaccuracy=6을 현재 1.5블록/틱 모델로 환산한 기준값. */
    public static final double ARROW_INACCURACY = 6.0;
    public static final double ARROW_GAUSSIAN_SCALE = 0.0075;
    public static final int SHOOT_INTERVAL = 20;
    public static final double DETECT_RANGE = 16.0;
    public static final double SHOOT_RANGE = 15.0;
    public static final int MIN_ARROW_DAMAGE = 3;
    public static final int MAX_ARROW_DAMAGE = 4;
    public static final double MIN_KEEP = 8.0;
    public static final double MAX_KEEP = 14.0;
    /** 거리 유지 구간의 좌우 이동 성분 배율(바닐라 RangedAttackGoal strafe 0.5). */
    public static final double STRAFE_FACTOR = 0.5;
    /** 방향 전환 판정 주기. MC 20틱을 10 TPS 서버 틱으로 환산한 값. */
    public static final int STRAFE_FLIP_INTERVAL = 10;
    /** 판정 시점의 방향 전환 확률. */
    public static final double STRAFE_FLIP_CHANCE = 0.3;

    private int shootTimer;
    private int meleeTimer;
    private int strafeTimer;
    private int strafeSign = 1;
    private int visibleTargetGameTicks;
    private String visibleTargetNickname;
    // 재수화 때 false. 영속 yaw만으로 표적 조준을 추론하지 않는다.
    private boolean targetAimProduced;
    private final double[] strafeBuf = new double[2];

    public Skeleton(long id, double x, double y, double z) {
        this(id, MobType.SKELETON, x, y, z);
    }

    protected Skeleton(long id, MobType type, double x, double y, double z) {
        super(id, type, x, y, z);
    }

    @Override
    public int visualFlags() {
        return targetAimProduced && !isDead() && !removed && heldItem() == 0 && hasTrackedTarget()
                ? VISUAL_SKELETON_AIMING : 0;
    }

    @Override
    void tickVisualAction() {
        // Runtime calls this before environmental/preempted paths, even if AI never runs.
        targetAimProduced = false;
        super.tickVisualAction();
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        List<MobEvent> events = List.of();
        targetAimProduced = false;
        if (isDead()) return events;
        if (shootTimer > 0) shootTimer--;
        if (meleeTimer > 0) meleeTimer--;

        double dx = 0, dz = 0;
        PlayerSnapshot target = trackedTarget(world, DETECT_RANGE, true);
        if (target != null) {
            // 조준 yaw의 정면은 (-sin(yaw), -cos(yaw)). 횡이동·후퇴 방향과 분리한다.
            yaw = Math.atan2(x - target.x(), z - target.z());
            targetAimProduced = heldItem() == 0;
            double hd = horizDist(target);
            boolean visible = canSeeTargetNow(world, target);
            if (!target.nickname().equals(visibleTargetNickname)) visibleTargetGameTicks = 0;
            visibleTargetNickname = target.nickname();
            visibleTargetGameTicks = visible && heldItem() == 0
                    ? Math.min(20, visibleTargetGameTicks + 2) : 0;
            if (heldItem() != 0) {
                if (dist3d(target) <= 1.5 && visible) {
                    state = MobState.ATTACK;
                    if (meleeTimer == 0) {
                        int damage = enchantedMeleeDamage(
                                com.gameexpert.engine.CombatRules.meleeDamage(heldItem()), null);
                        events = appendEvent(events, new MobEvent.AttackPlayer(
                                target.nickname(), contactDamage(world, damage), x, z));
                    }
                } else {
                    state = MobState.CHASE;
                    double[] mv = towardHoriz(target.x(), target.z(), type.baseSpeed());
                    dx = mv[0]; dz = mv[1];
                }
            } else if (visibleTargetGameTicks < 20 || dist3d(target) > SHOOT_RANGE) {
                // 활의 횡이동은 사거리 안에서 연속 20 game tick 시야를 확보한 뒤에만 한다.
                state = MobState.CHASE;
                double[] mv = towardHoriz(target.x(), target.z(), type.baseSpeed());
                dx = mv[0]; dz = mv[1];
            } else if (hd < MIN_KEEP) {
                state = MobState.FLEE;
                double[] mv = towardHoriz(target.x(), target.z(), type.baseSpeed());
                dx = -mv[0]; dz = -mv[1];          // 후퇴
            } else if (hd > MAX_KEEP) {
                state = MobState.CHASE;
                double[] mv = towardHoriz(target.x(), target.z(), type.baseSpeed());
                dx = mv[0]; dz = mv[1];
            } else {
                // 거리 유지 구간: 전진/후퇴 없이 표적 기준 좌우로만 이동한다.
                state = MobState.IDLE;
                double[] strafe = strafeAround(target, rng);
                dx = strafe[0]; dz = strafe[1];
            }
            if (heldItem() == 0 && dist3d(target) <= SHOOT_RANGE
                    && shootTimer <= 0 && visible) {
                MobEvent.ShootArrow shot = aimAt(target, world, rng);
                if (shot != null) events = appendEvent(events, shot);
                shootTimer = shootIntervalTicks(world);  // 발사 여부와 무관하게 주기 유지
            }
        } else {
            visibleTargetGameTicks = 0;
            visibleTargetNickname = null;
            double[] mv = wander(rng, type.baseSpeed());
            dx = mv[0]; dz = mv[1];
            state = (dx != 0 || dz != 0) ? MobState.WANDER : MobState.IDLE;
        }

        MobPhysics.tickMove(this, world, dx, 0.0, dz, MoveMode.WALK);
        return events;
    }

    @Override
    public void commitAcceptedMeleeAttack() {
        meleeTimer = 10;
        super.commitAcceptedMeleeAttack();
    }

    /**
     * 거리 유지 구간의 좌우 이동 변위. 표적 방향의 좌수직으로 {@link #STRAFE_FACTOR} 만큼 움직이고,
     * {@link #STRAFE_FLIP_INTERVAL} 틱마다 {@link #STRAFE_FLIP_CHANCE} 확률로 방향을 뒤집는다.
     */
    private double[] strafeAround(PlayerSnapshot target, MobRandom rng) {
        if (++strafeTimer >= STRAFE_FLIP_INTERVAL) {
            strafeTimer = 0;
            if (rng.nextDouble() < STRAFE_FLIP_CHANCE) strafeSign = -strafeSign;
        }
        double ax = target.x() - x;
        double az = target.z() - z;
        double d = Math.sqrt(ax * ax + az * az);
        if (d < 1e-9) {
            strafeBuf[0] = 0;
            strafeBuf[1] = 0;
            return strafeBuf;
        }
        double speed = type.baseSpeed() * STRAFE_FACTOR * effectSpeedMultiplier() * strafeSign;
        strafeBuf[0] = -az / d * speed;
        strafeBuf[1] = ax / d * speed;
        return strafeBuf;
    }

    /**
     * 이 스켈레톤이 쏘는 화살이 싣는 상태이상. 스트레이·보그드·파치드만 재정의한다
     * (난이도별 지속).
     */
    protected ProjectileEffect arrowEffect(MobWorldView world) {
        return null;
    }

    /**
     * 사격 주기(10 TPS 서버 틱). 스켈레톤·스트레이·보그드는 난이도와 무관한
     * {@link #SHOOT_INTERVAL} 이고, <b>파치드만</b> 난이도별로 갈리는 느린 연사를 쓴다
     * (원문 "every 3.5 seconds on Easy and Normal and every 2.5 seconds on Hard").
     * 기본 구현이 상수를 그대로 돌려주므로 기존 세 종의 발사 타이밍은 한 틱도 바뀌지 않는다.
     */
    protected int shootIntervalTicks(MobWorldView world) {
        return SHOOT_INTERVAL;
    }

    /** 표적 몸통(발+height*0.5)을 겨냥한 화살 발사 이벤트. 사거리 밖이면 null. */
    private MobEvent.ShootArrow aimAt(PlayerSnapshot p, MobWorldView world, MobRandom rng) {
        double sx = x, sy = y + eyeHeight(), sz = z;
        double tx = p.x(), ty = p.y() + 0.9, tz = p.z();     // 몸통(플레이어 높이 1.8의 절반 근사)
        double[] v = Ballistics.solve(sx, sy, sz, tx, ty, tz, ARROW_SPEED, ProjectileSim.GRAVITY);
        if (v == null) return null;
        v = gaussianSpread(v, world.difficulty().skeletonArrowInaccuracy(), rng);
        int damage = dist3d(p) < MIN_KEEP ? MIN_ARROW_DAMAGE : MAX_ARROW_DAMAGE;
        // [MOB-EQUIP] 고유 활의 힘: 플레이어 활(CombatRules.arrowDamage)과 같은 0.5·n + 0.5 가산 후 반올림.
        damage = com.gameexpert.engine.CombatRules.mobArrowDamage(damage, heldWeaponEnchantments());
        return new MobEvent.ShootArrow(ProjectileSim.Kind.ARROW,
                sx, sy, sz, v[0], v[1], v[2], damage, arrowEffect(world));
    }

    /** 기존 호출부 호환: normal 난이도 산포. */
    static double[] gaussianSpread(double[] velocity, MobRandom rng) {
        return gaussianSpread(velocity, ARROW_INACCURACY, rng);
    }

    /**
     * 조준 벡터에 정규분포 오차를 더한 뒤 발사 속도를 다시 1.5로 맞춘다.
     * inaccuracy 는 난이도가 정한다(easy 10 / normal 6 / hard 2).
     */
    static double[] gaussianSpread(double[] velocity, double inaccuracy, MobRandom rng) {
        double speed = Math.sqrt(velocity[0] * velocity[0]
                + velocity[1] * velocity[1] + velocity[2] * velocity[2]);
        if (speed <= 1e-12) return velocity;
        double noise = inaccuracy * ARROW_GAUSSIAN_SCALE;
        double x = velocity[0] / speed + nextGaussian(rng) * noise;
        double y = velocity[1] / speed + nextGaussian(rng) * noise;
        double z = velocity[2] / speed + nextGaussian(rng) * noise;
        double length = Math.sqrt(x * x + y * y + z * z);
        return new double[] { x * ARROW_SPEED / length,
                y * ARROW_SPEED / length, z * ARROW_SPEED / length };
    }

    /** 정규분포 표본(Box–Muller). 정적판 `normalRandom` 과 소비 순서까지 같은 사본이다. */
    static double nextGaussian(MobRandom rng) {
        double u1 = Math.max(Double.MIN_VALUE, rng.nextDouble());
        double u2 = rng.nextDouble();
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(Math.PI * 2.0 * u2);
    }
}
