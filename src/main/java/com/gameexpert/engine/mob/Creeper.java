package com.gameexpert.engine.mob;

import java.util.List;

/**
 * 크리퍼: 접근 → 3블록 내 FUSE(15 서버틱=1.5초, 이동 정지) → Explode(폭발력 3).
 * FUSE 중 플레이어가 7블록 밖으로 벗어나면 점화 해제. HP 20. AABB 0.6×1.7.
 * 피해 반경(폭발력×2)과 노출도 피해, 저항 광선 파괴는 Explode 이벤트 소비자 몫이다.
 */
public final class Creeper extends Mob {
    public static final double DETECT_RANGE = 16.0;
    public static final double FUSE_START = 3.0;    // 점화 시작 거리
    public static final double FUSE_CANCEL = 7.0;   // 점화 해제 거리
    public static final int FUSE_TICKS = 15;
    public static final int EXPLOSION_POWER = 3;
    /** 공식의 중심·노출도 1 검산값. 실제 피해는 ExplosionRules가 매번 계산한다. */
    public static final int EXPLODE_MAX_DAMAGE = 43;
    /**
     * 차지드 크리퍼의 폭발력. 바닐라 {@code Creeper#explodeCreeper}:
     * {@code float f = isPowered() ? 2.0F : 1.0F; ... explosionRadius * f} → 3 × 2 = 6.
     */
    public static final int POWERED_EXPLOSION_POWER = EXPLOSION_POWER * 2;
    /** 위와 같은 공식의 중심·노출도 1 검산값: (1+1)/2 × 7 × (6×2) + 1 = 85. */
    public static final int POWERED_EXPLODE_MAX_DAMAGE = 85;
    /** 바닐라 AvoidEntityGoal(Cat): 탐색 반경 6, 수직 AABB 팽창 3. */
    public static final double CAT_AVOID_VERTICAL_RANGE = 3.0;
    /** 탐색 반경 6 안은 항상 sprint 경계(거리 제곱 49) 안이라 1.2 배로 달아난다. */
    public static final double CAT_AVOID_SPEED_MULTIPLIER = 1.2;

    /**
     * Immutable source capability for the existing {@link MobEvent.Explode} action. The owning
     * runtime includes these exact facts in its durable settlement and acknowledges the same
     * capability only after that settlement commits.
     */
    public record PendingExplosionSource(long sourceMobId, MobType sourceType,
            MobEvent.Explode action, boolean ignited, boolean powered, int fuseTicks) {
        public PendingExplosionSource {
            if (sourceMobId <= 0 || sourceType != MobType.CREEPER || action == null
                    || fuseTicks < 0) {
                throw new IllegalArgumentException("invalid creeper explosion source");
            }
        }
    }

    private int fuse = -1;   // -1: 미점화, >=0: 남은 점화 틱
    /** 바닐라 NBT {@code ignited}. 라이터로 수동 점화하면 대상/시야와 무관하게 끝까지 간다. */
    private boolean ignited;
    /** 바닐라 {@code DATA_IS_POWERED}. 낙뢰 타격으로만 켜진다. */
    private boolean powered;
    private PendingExplosionSource pendingExplosion;
    private boolean explosionRetryRequested;

    public Creeper(long id, double x, double y, double z) {
        super(id, MobType.CREEPER, x, y, z);
    }

    public boolean isFusing() { return fuse >= 0; }

    public boolean isIgnited() { return ignited; }

    /** 바닐라 {@code Creeper#ignite}. 이미 점화됐어도 진행 중인 fuse를 되감지 않는다. */
    public void ignite() {
        if (pendingExplosion == null) ignited = true;
    }

    /** 차지드 여부. 바닐라 {@code Creeper#isPowered}. */
    public boolean isPowered() { return powered; }

    /** 낙뢰 타격({@code Creeper#thunderHit})이 켜는 유일한 경로. */
    public void setPowered(boolean value) {
        if (pendingExplosion == null) this.powered = value;
    }

    @Override
    public int visualFlags() {
        return powered ? Mob.VISUAL_CREEPER_POWERED : 0;
    }

    /** 현재 상태의 폭발력(차지드면 2배). */
    public int explosionPower() {
        return powered ? POWERED_EXPLOSION_POWER : EXPLOSION_POWER;
    }

    /** Exact immutable source facts currently waiting for the owning settlement lane. */
    public PendingExplosionSource pendingExplosionSource() { return pendingExplosion; }

    /**
     * Acknowledges the exact staged source. Rejection changes no live source state and requests
     * one identical retry emission; commit retires the mob once. A stale or repeated acknowledgement
     * is rejected without changing the entity.
     */
    public boolean acknowledgeExplosionSettlement(PendingExplosionSource source,
            boolean committed) {
        if (pendingExplosion == null || source != pendingExplosion
                || source.sourceMobId() != id || source.sourceType() != type
                || isDead() || removed) return false;
        if (!committed) {
            explosionRetryRequested = true;
            return true;
        }
        pendingExplosion = null;
        explosionRetryRequested = false;
        kill();
        removed = true;
        return true;
    }

    /** Convenience form for the owner after looking the live mob up by the emitted mob ID. */
    public boolean acknowledgeExplosionSettlement(boolean committed) {
        return acknowledgeExplosionSettlement(pendingExplosion, committed);
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        List<MobEvent> events = List.of();
        if (isDead() || removed) return events;
        if (pendingExplosion != null) {
            if (!explosionRetryRequested) return events;
            explosionRetryRequested = false;
            return List.of(pendingExplosion.action());
        }

        PlayerSnapshot target = trackedTarget(world, DETECT_RANGE, true);
        double dx = 0, dz = 0;

        if (fuse >= 0) {
            // 점화 중: 이동 정지, 해제 조건 확인.
            state = MobState.FUSE;
            if (!ignited && (target == null || dist3d(target) > FUSE_CANCEL
                    || !canSeeTargetNow(world, target))) {
                fuse = -1;                          // 점화 해제
                state = MobState.IDLE;
            } else {
                fuse--;
                if (fuse == 0) {
                    MobEvent.Explode action = new MobEvent.Explode(x, y, z,
                            explosionPower(),
                            powered ? POWERED_EXPLODE_MAX_DAMAGE : EXPLODE_MAX_DAMAGE);
                    pendingExplosion = new PendingExplosionSource(
                            id, type, action, ignited, powered, fuse);
                    explosionRetryRequested = false;
                    setPersistenceRequired(true);
                    return List.of(action);
                }
            }
        } else if (ignited) {
            fuse = FUSE_TICKS;
            state = MobState.FUSE;
            events = appendEvent(events, new MobEvent.Sound("attack"));
        } else {
            double[] cat = world.nearestCat(
                    x, y, z, Cat.CREEPER_AVOID_RANGE, CAT_AVOID_VERTICAL_RANGE);
            if (cat != null) {
                state = MobState.FLEE;
                double awayX = x - cat[0], awayZ = z - cat[2];
                if (awayX * awayX + awayZ * awayZ > 1e-12) {
                    faceToward(x + awayX, z + awayZ);
                    double[] mv = towardHoriz(x + awayX, z + awayZ,
                            type.baseSpeed() * CAT_AVOID_SPEED_MULTIPLIER);
                    dx = mv[0]; dz = mv[1];
                }
            } else if (target != null) {
                faceToward(target.x(), target.z());
                if (dist3d(target) <= FUSE_START && canSeeTargetNow(world, target)) {
                    fuse = FUSE_TICKS;                  // 점화 시작(이번 틱부터 정지)
                    state = MobState.FUSE;
                    // 클라이언트는 creeper의 권위 attack 의미를 entity.creeper.primed로 매핑한다.
                    // FUSE 진입 경계에서만 한 번 내고, 점화 유지 틱에는 반복하지 않는다.
                    events = appendEvent(events, new MobEvent.Sound("attack"));
                } else {
                    state = MobState.CHASE;
                    double[] mv = towardHoriz(target.x(), target.z(), type.baseSpeed());
                    dx = mv[0]; dz = mv[1];
                }
            } else {
                double[] mv = wander(rng, type.baseSpeed());
                dx = mv[0]; dz = mv[1];
                state = (dx != 0 || dz != 0) ? MobState.WANDER : MobState.IDLE;
            }
        }

        MobPhysics.tickMove(this, world, dx, 0.0, dz, MoveMode.WALK);
        return events;
    }
}
