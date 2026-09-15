package com.gameexpert.engine.mob;

import java.util.List;

/**
 * 눈 골렘(바닐라 실존 종, stableId 92). 수치 근거는 {@link MobType#SNOW_GOLEM} 의 인라인
 * 인용이다(MC Java 1.21.4 {@code EntityType.SNOW_GOLEM sized(0.7F, 1.9F)} ·
 * {@code createAttributes()} MAX_HEALTH 4 · MOVEMENT_SPEED 0.2).
 *
 * <p><b>제작 소환</b>: 눈 블록 {@value #SNOW_BLOCK_COUNT} 개를 세로로 쌓고 맨 위에 호박을
 * 올리면 선다 — 철 골렘 소환과 <b>같은 계약 자리</b>를 재사용한다(패턴 검사·블록 소거·개체
 * 생성이 한 경로다). 자연 스폰은 바닐라에도 없어 {@link MobCategory#MISC} 다.
 *
 * <p><b>눈덩이 투척</b>: {@value #THROW_RANGE} 블록 안의 적대 몹에게
 * {@value #THROW_COOLDOWN_TICKS} 틱마다 기존 눈덩이 투사체를 던진다(피해 0 · 넉백만).
 * 플레이어에게는 던지지 않으므로 {@code hostile} 은 언제나 false 다.
 *
 * <p><b>WebCraft divergence(등급 C)</b>: 바닐라는 뜨거운 바이옴에서 초당 피해를 받지만
 * WebCraft 지형에는 <b>온도 축이 없다</b>. 그래서 바이옴 온도 피해는 세우지 않는다 —
 * 축이 생기면 여기에 배선한다.
 */
public final class SnowGolem extends AnimalMob {

    /** 소환 패턴의 눈 블록 수(맨 위 호박 제외). */
    public static final int SNOW_BLOCK_COUNT = 2;
    /** 눈덩이 사거리(블록). */
    public static final double THROW_RANGE = 10.0;
    /** 눈덩이 쿨다운(틱). */
    public static final int THROW_COOLDOWN_TICKS = 20;
    /** 바닐라 SnowGolem.performRangedAttack 의 발사 속력. */
    public static final double THROW_SPEED = 1.6;
    /** 바닐라 SnowGolem.performRangedAttack 의 부정확도. */
    public static final double THROW_INACCURACY = 12.0;
    private static final double PROJECTILE_NOISE_SCALE = 0.0172275;

    private int throwCooldownTicks;
    /** [CONTAINER-MENUS] {@code SnowGolem.DATA_PUMPKIN_ID}: the carved pumpkin head is still on. */
    private boolean pumpkin = true;

    /**
     * {@code SnowGolem#readyForShearing}: alive and still wearing its pumpkin. Named apart from
     * {@link Mob#readyForShearing}, which stays the sheep gate.
     */
    public boolean readyForShears() {
        return pumpkin && !isDead() && !removed;
    }

    public boolean hasPumpkin() {
        return pumpkin;
    }

    /** {@code SnowGolem#shear}: {@code setPumpkin(false)}; the caller drops the shearing loot. */
    public void shear() {
        pumpkin = false;
    }

    void restorePumpkin(boolean value) {
        pumpkin = value;
    }

    @Override
    public int visualFlags() {
        return super.visualFlags() | (pumpkin ? 0 : Mob.VISUAL_SNOW_GOLEM_PUMPKINLESS);
    }

    public SnowGolem(long id, double x, double y, double z) {
        super(MobType.SNOW_GOLEM, id, x, y, z);
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        List<MobEvent> events = super.tick(world, rng);
        if (isDead()) return events;
        if (throwCooldownTicks > 0) throwCooldownTicks--;

        Mob target = preparedSocialTarget();
        if (target == null || target.isDead() || target.removed) return events;
        faceToward(target.x, target.z);
        double distanceSquared = MobRelationshipPolicy.distanceSquared(this, target);
        if (distanceSquared > THROW_RANGE * THROW_RANGE
                || !MobRelationshipPolicy.hasLineOfSight(world, this, target)) {
            state = MobState.CHASE;
            return events;
        }
        state = MobState.ATTACK;
        if (throwCooldownTicks > 0) return events;
        throwCooldownTicks = THROW_COOLDOWN_TICKS;

        double sx = x;
        double sy = y + eyeHeight();
        double sz = z;
        double dx = target.x - sx;
        double dz = target.z - sz;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double dy = target.y + target.eyeHeight() - 1.1 - sy + horizontal * 0.2;
        double[] velocity = shootVector(dx, dy, dz, rng);
        if (velocity == null) return events;
        return appendEvent(events, new MobEvent.ShootArrow(
                ProjectileSim.Kind.SNOWBALL, sx, sy, sz,
                velocity[0], velocity[1], velocity[2], 0));
    }

    /** Projectile.shoot: normalize, add triangular noise on each axis, normalize again, scale. */
    private static double[] shootVector(double dx, double dy, double dz, MobRandom rng) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 1.0e-12) return null;
        double deviation = PROJECTILE_NOISE_SCALE * THROW_INACCURACY;
        double vx = dx / length + triangle(rng, deviation);
        double vy = dy / length + triangle(rng, deviation);
        double vz = dz / length + triangle(rng, deviation);
        double noisyLength = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (noisyLength <= 1.0e-12) return null;
        return new double[] {
                vx / noisyLength * THROW_SPEED,
                vy / noisyLength * THROW_SPEED,
                vz / noisyLength * THROW_SPEED
        };
    }

    private static double triangle(MobRandom rng, double deviation) {
        return (rng.nextDouble() - rng.nextDouble()) * deviation;
    }
}
