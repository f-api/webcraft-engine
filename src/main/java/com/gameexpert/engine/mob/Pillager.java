package com.gameexpert.engine.mob;

import java.util.List;

/** 약탈자: 기존 서버 화살 경로를 재사용하는 체력 24 원거리 적대 거주자. */
public class Pillager extends Mob {
    public static final int NORMAL_ARROW_DAMAGE = 4;
    // 사거리·발사 주기·속도·거리 유지는 정확값 미확인이라 검증된 스켈레톤 경로의 WebCraft 매핑을 쓴다.
    private int shootTimer;

    public Pillager(long id, double x, double y, double z) {
        this(id, MobType.PILLAGER, x, y, z);
    }

    protected Pillager(long id, MobType type, double x, double y, double z) {
        super(id, type, x, y, z);
        if (type == MobType.PILLAGER) setNativeEquipment("crossbow");
    }

    protected double targetRange() { return Skeleton.DETECT_RANGE; }

    /**
     * 주문 시전 등으로 이번 틱 활 공격을 눌러야 하면 false. 바닐라 goal 우선순위(주문 &lt; 활)를
     * 단일 tick 함수로 옮긴 훅이다.
     */
    protected boolean mayShoot() { return true; }

    /**
     * 발사가 확정된 화살에 실을 상태이상. 기본 약탈자는 없다.
     *
     * @param targetKey 표적 식별자(플레이어는 닉네임, 몹은 {@code "#" + mobId}). 표적별 1회 규칙에 쓴다.
     */
    protected ProjectileEffect arrowEffect(MobWorldView world, String targetKey) { return null; }

    /** 사격 사거리. 기본은 검증된 스켈레톤 매핑이고 종별 무기 사거리가 이것을 대체한다. */
    protected double shootRange() { return Skeleton.SHOOT_RANGE; }

    /** 이보다 가까우면 후퇴한다. */
    protected double minKeepDistance() { return Skeleton.MIN_KEEP; }

    /** 이보다 멀면 접근한다. */
    protected double maxKeepDistance() { return Skeleton.MAX_KEEP; }

    /** 후퇴 구간의 이동 속도 배율. */
    protected double retreatSpeedMultiplier() { return 1.0; }

    /** 사격 시도 뒤 강제되는 최소 간격. 종별 장전 상태기계가 주기를 소유하면 0을 돌려준다. */
    protected int shootIntervalTicks() {
        // [RAID-OMEN] Pillager#applyRaidBuffs: 레이드 쇠뇌의 빠른 장전은 장전 시간(25 − 5·n MC 틱)에 비례해 주기를 줄인다.
        int quickCharge = type == MobType.PILLAGER ? raidBuffLevel() : 0;
        if (quickCharge <= 0) return Skeleton.SHOOT_INTERVAL;
        return Math.max(1, Math.round(Skeleton.SHOOT_INTERVAL
                * com.gameexpert.engine.enchant.EnchantmentRules.crossbowChargeMcTicks(quickCharge)
                / (float) com.gameexpert.engine.enchant.EnchantmentRules.crossbowChargeMcTicks(0)));
    }

    /** 발사 벡터에 난이도 산포를 실을지. 기본 약탈자는 기존 정밀 석궁 매핑을 유지한다. */
    protected boolean spreadArrow() { return false; }

    /**
     * [SPEAR-MOB] 교전 이동을 종이 직접 정하면 수평 변위 {dx, dz} 를, 아니면 null(거리 유지 이동). 창을 든 피글린의
     * SpearApproach · SpearAttack · SpearRetreat 가 이 자리다.
     */
    protected double[] engagedMovement(MobWorldView world, MobRandom rng, double targetX, double targetY,
            double targetZ, double targetEyeY) {
        return null;
    }

    /** 표적과 교전 중인 틱마다 1회. 종별 장전 상태기계가 여기서 전진한다. */
    protected void onEngagedTick(MobRandom rng) {}

    /** 표적이 없어 배회로 떨어진 틱마다 1회. 장전 상태를 되돌리는 자리다. */
    protected void onDisengaged() {}

    /** 화살이 실제로 생성된 직후 1회. */
    protected void onArrowReleased() {}

    /**
     * 근접 무기를 든 파생 종(금 검 피글린)이 이번 틱 표적을 때리면 공격 이벤트를 돌려준다.
     * 기본 약탈자는 원거리 전용이라 항상 {@code null} 이다.
     */
    protected MobEvent meleeAttack(MobWorldView world, PlayerSnapshot target) { return null; }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        List<MobEvent> events = List.of();
        if (isDead()) return events;
        if (shootTimer > 0) shootTimer--;
        double dx = 0, dz = 0;
        PlayerSnapshot target = trackedTarget(world, targetRange(), true);
        if (target != null) {
            onEngagedTick(rng);
            faceToward(target.x(), target.z());
            double distance = horizDist(target);
            double[] engaged = engagedMovement(world, rng, target.x(), target.y(), target.z(),
                    target.y() + (target.crouching() ? com.gameexpert.engine.PlayerInteractionRules.CROUCHING_EYE_HEIGHT
                            : com.gameexpert.engine.PlayerInteractionRules.STANDING_EYE_HEIGHT));
            if (engaged != null) {
                dx = engaged[0];
                dz = engaged[1];
            } else if (distance < minKeepDistance()) {
                state = MobState.FLEE;
                double[] move = towardHoriz(target.x(), target.z(),
                        type.baseSpeed() * retreatSpeedMultiplier());
                dx = -move[0]; dz = -move[1];
            } else if (distance > maxKeepDistance()) {
                state = MobState.CHASE;
                double[] move = towardHoriz(target.x(), target.z(), type.baseSpeed());
                dx = move[0]; dz = move[1];
            } else {
                state = MobState.IDLE;
            }
            MobEvent melee = meleeAttack(world, target);
            if (melee != null) {
                events = appendEvent(events, melee);
                state = MobState.ATTACK;
            }
            if (dist3d(target) <= shootRange() && shootTimer == 0 && mayShoot()
                    && canSeeTargetNow(world, target)) {
                MobEvent.ShootArrow shot = aimAt(world, rng, target);
                if (shot != null) {
                    events = appendEvent(events, shot);
                    state = MobState.ATTACK;
                    onArrowReleased();
                }
                shootTimer = shootIntervalTicks();
            }
        } else if (!hasPlayerTarget()
                && MobRelationshipPolicy.mayDirectlyTarget(this, preparedSocialTarget())) {
            Mob socialTarget = preparedSocialTarget();
            if (MobRelationshipPolicy.inTargetRange(this, socialTarget)) {
                onEngagedTick(rng);
                faceToward(socialTarget.x, socialTarget.z);
                double distance = Math.hypot(socialTarget.x - x, socialTarget.z - z);
                double[] engaged = engagedMovement(world, rng, socialTarget.x, socialTarget.y, socialTarget.z,
                        socialTarget.y + socialTarget.eyeHeight());
                if (engaged != null) {
                    dx = engaged[0];
                    dz = engaged[1];
                } else if (distance < minKeepDistance()) {
                    state = MobState.FLEE;
                    double[] move = towardHoriz(socialTarget.x, socialTarget.z,
                            type.baseSpeed() * retreatSpeedMultiplier());
                    dx = -move[0]; dz = -move[1];
                } else if (distance > maxKeepDistance()) {
                    state = MobState.CHASE;
                    double[] move = towardHoriz(socialTarget.x, socialTarget.z, type.baseSpeed());
                    dx = move[0]; dz = move[1];
                } else {
                    state = MobState.IDLE;
                }
                if (Math.sqrt(MobRelationshipPolicy.distanceSquared(this, socialTarget))
                        <= shootRange() && shootTimer == 0 && mayShoot()
                        && MobRelationshipPolicy.hasLineOfSight(world, this, socialTarget)) {
                    MobEvent.ShootArrow shot = aimAt(world, rng, socialTarget);
                    if (shot != null) {
                        events = appendEvent(events, shot);
                        state = MobState.ATTACK;
                        onArrowReleased();
                    }
                    shootTimer = shootIntervalTicks();
                }
            } else {
                onDisengaged();
                double[] move = wander(rng, type.baseSpeed());
                dx = move[0]; dz = move[1];
                state = (dx != 0 || dz != 0) ? MobState.WANDER : MobState.IDLE;
            }
        } else {
            onDisengaged();
            double[] move = wander(rng, type.baseSpeed());
            dx = move[0]; dz = move[1];
            state = (dx != 0 || dz != 0) ? MobState.WANDER : MobState.IDLE;
        }
        MobPhysics.tickMove(this, world, dx, 0.0, dz, MoveMode.WALK);
        return events;
    }

    private MobEvent.ShootArrow aimAt(MobWorldView world, MobRandom rng, PlayerSnapshot target) {
        return aimAt(world, rng, target.nickname(), target.x(), target.y() + 0.9, target.z());
    }

    private MobEvent.ShootArrow aimAt(MobWorldView world, MobRandom rng, Mob target) {
        return aimAt(world, rng, "#" + target.id,
                target.x, target.y + target.height() * 0.5, target.z);
    }

    private MobEvent.ShootArrow aimAt(MobWorldView world, MobRandom rng, String targetKey,
            double targetX, double targetY, double targetZ) {
        double sx = x, sy = y + eyeHeight(), sz = z;
        double[] velocity = Ballistics.solve(sx, sy, sz, targetX, targetY, targetZ,
                Skeleton.ARROW_SPEED, ProjectileSim.GRAVITY);
        if (velocity == null) return null;
        if (spreadArrow()) {
            velocity = Skeleton.gaussianSpread(
                    velocity, world.difficulty().skeletonArrowInaccuracy(), rng);
        }
        // 탄도 해가 있어 화살이 실제로 생기는 경우에만 표적별 1회 규칙을 소모한다.
        return new MobEvent.ShootArrow(ProjectileSim.Kind.ARROW, sx, sy, sz,
                velocity[0], velocity[1], velocity[2], NORMAL_ARROW_DAMAGE,
                arrowEffect(world, targetKey));
    }
}
