package com.gameexpert.engine.mob;

import java.util.List;

/**
 * 수동 동물 공통 AI: Panic → Breed → Tempt → FollowParent → Wander 순서로
 * 이동 목표를 평가하며 플레이어를 공격하지 않는다.
 * 피격 시 기존 결정적 도주 상태를 유지하되, 출처가 고정된 종은 종별 PanicGoal 속도를 쓴다.
 * 실제 random escape target/navigation 종료 계약은 현재 이동 API가 제공하지 않는다.
 * cow/pig/sheep/chicken 이 파라미터(HP·AABB, {@link MobType})만 바꿔 이 클래스를 공유한다.
 *
 * <p>물리·충돌·1블록 등반은 적대 몹과 동일한 {@link MobPhysics#tickMove}를 쓴다(벽 등반 없음).
 */
public class AnimalMob extends Mob {

    /** Java의 BreedGoal 이동 배율 1.0: 종 기본 속도로 짝에게 접근한다. */
    protected boolean hasBreedTarget;
    private double breedTargetX, breedTargetZ;

    /** 기존 단순 도주의 지속 시간. 경로 목표 기반 PanicGoal로 교체할 때 제거한다. */
    static final int FLEE_TICKS = 60;
    protected int fleeTimer;      // 남은 도주 틱(>0 이면 FLEE)
    private double fleeDx, fleeDz; // 도주 방향(단위 속도, 블록/틱)
    private final String variant;
    private boolean hasTemptTarget;
    private double temptTargetX, temptTargetZ;
    private boolean temptationActive;
    private int temptationCooldown;
    private boolean hasParentTarget;
    private double parentTargetX, parentTargetZ;

    public AnimalMob(MobType type, long id, double x, double y, double z) {
        this(type, id, x, y, z,
                type.deterministicVariant(0, id, x, z));
    }

    AnimalMob(MobType type, long id, double x, double y, double z, String variant) {
        super(id, type, x, y, z);
        if (!type.acceptsVariant(variant)) {
            throw new IllegalArgumentException("Invalid variant for " + type + ": " + variant);
        }
        this.variant = variant;
        this.state = MobState.IDLE;
    }

    @Override
    public String variant() {
        return variant;
    }

    @Override
    public void onHurt(String attackerNickname, double attackerX, double attackerZ) {
        clearLoveMode();
        // 가해자 반대 방향(수평). 겹쳐 있으면 배회 방향 유지.
        double ax = x - attackerX, az = z - attackerZ;
        double d = Math.sqrt(ax * ax + az * az);
        double speed = type.baseSpeed() * AnimalGoalRules.panicSpeedMultiplier(type);
        if (d < 1e-9) {
            // 정확히 겹친 드문 경우: 현재 yaw 반대편으로 밀어낸다.
            fleeDx = Math.cos(yaw) * speed;
            fleeDz = Math.sin(yaw) * speed;
        } else {
            fleeDx = ax / d * speed;
            fleeDz = az / d * speed;
            yaw = Math.atan2(fleeDz, fleeDx);
        }
        fleeTimer = FLEE_TICKS;
        state = MobState.FLEE;
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (isDead()) return List.of();

        double dx, dz;
        if (temptationCooldown > 0) temptationCooldown--;
        if (!hasTemptTarget && temptationActive) {
            temptationActive = false;
            temptationCooldown = AnimalGoalRules.TEMPT_COOLDOWN_TICKS;
        }
        double[] scheduled = fleeTimer > 0 || (hasBreedTarget && isInLoveMode())
                || hasTemptTarget || hasParentTarget
                ? null : scheduledWalk(world);
        if (fleeTimer > 0) {
            fleeTimer--;
            state = MobState.FLEE;
            dx = fleeDx; dz = fleeDz;
        } else if (hasBreedTarget && isInLoveMode()) {
            if (temptationActive) {
                temptationActive = false;
                temptationCooldown = AnimalGoalRules.TEMPT_COOLDOWN_TICKS;
            }
            double[] mv = towardHoriz(breedTargetX, breedTargetZ, type.baseSpeed());
            dx = mv[0]; dz = mv[1];
            yaw = Math.atan2(dz, dx);
            state = MobState.WANDER;
        } else if (hasTemptTarget && temptationCooldown == 0) {
            double speed = type.baseSpeed() * AnimalGoalRules.temptSpeedMultiplier(type);
            double[] mv = towardHoriz(temptTargetX, temptTargetZ, speed);
            dx = mv[0]; dz = mv[1];
            yaw = Math.atan2(dz, dx);
            state = MobState.WANDER;
            temptationActive = true;
        } else if (hasParentTarget) {
            double[] mv = towardHoriz(parentTargetX, parentTargetZ,
                    type.baseSpeed() * AnimalGoalRules.followParentSpeedMultiplier(type));
            dx = mv[0]; dz = mv[1];
            yaw = Math.atan2(dz, dx);
            state = MobState.WANDER;
        } else if (scheduled != null) {
            dx = scheduled[0]; dz = scheduled[1];
            if (dx != 0.0 || dz != 0.0) yaw = Math.atan2(dz, dx);
            state = (dx != 0 || dz != 0) ? MobState.WANDER : MobState.IDLE;
        } else if (!mayWander(world)) {
            dx = 0.0;
            dz = 0.0;
            state = MobState.IDLE;
        } else {
            double[] mv = wander(rng, idleMoveSpeed(world));
            dx = mv[0]; dz = mv[1];
            state = (dx != 0 || dz != 0) ? MobState.WANDER : MobState.IDLE;
        }

        // 비행 종(해피 가스트·앵무새)은 movementMedium() 만 "fly" 로 갈아도 물리가 따라와야 한다.
        // 정적판은 profile.movementMedium === "fly" 하나로 비행 경로를 고르므로 같은 축을 쓴다.
        MobPhysics.tickMove(this, world, dx, 0.0, dz,
                "fly".equals(movementMedium()) ? MoveMode.FLY : MoveMode.WALK);
        hasBreedTarget = false;
        hasTemptTarget = false;
        hasParentTarget = false;
        return List.of();
    }

    @Override
    void seekBreedingPartner(Mob partner) {
        hasBreedTarget = true;
        breedTargetX = partner.x;
        breedTargetZ = partner.z;
    }

    void seekTemptingPlayer(PlayerSnapshot player) {
        if (player == null || temptationCooldown > 0) return;
        hasTemptTarget = true;
        temptTargetX = player.x();
        temptTargetZ = player.z();
    }

    void followParent(Mob parent) {
        if (parent == null || !isBaby() || parent.isBaby() || parent.type != type) return;
        hasParentTarget = true;
        parentTargetX = parent.x;
        parentTargetZ = parent.z;
    }

    protected final boolean priorityAnimalMovement() {
        return fleeTimer > 0 || isInLoveMode() || hasTemptTarget || hasParentTarget;
    }

    protected final void clearAnimalMovementTargets() {
        hasBreedTarget = hasTemptTarget = hasParentTarget = false;
    }

    /** Species schedules may suspend idle roaming without bypassing panic or breeding goals. */
    protected boolean mayWander(MobWorldView world) { return true; }

    /**
     * A schedule-owned walk this tick, or {@code null} to fall through to idle roaming. Panic and
     * breeding still win, exactly as the vanilla goal/behaviour priorities order them.
     */
    protected double[] scheduledWalk(MobWorldView world) { return null; }

    protected double idleMoveSpeed(MobWorldView world) { return type.baseSpeed(); }
}
