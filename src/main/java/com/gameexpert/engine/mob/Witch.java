package com.gameexpert.engine.mob;

import java.util.List;

import com.gameexpert.engine.effect.StatusEffect;

/** 마녀: 거리를 유지하며 주기적으로 물약을 투척한다. 자신은 마법 피해에 85% 저항한다. */
public final class Witch extends Mob {
    private static final double DETECT_RANGE = 16.0;
    private static final double ATTACK_RANGE = 10.0;
    /** 투척 주기(10 TPS 서버 틱). */
    private static final int THROW_INTERVAL = 30;
    private int attackTimer;

    public Witch(long id, double x, double y, double z) {
        super(id, MobType.WITCH, x, y, z);
    }

    @Override
    protected double magicResistance() {
        return MobEffectRules.WITCH_MAGIC_RESISTANCE;
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        List<MobEvent> events = List.of();
        if (isDead()) return events;
        if (attackTimer > 0) attackTimer--;
        double dx = 0;
        double dz = 0;
        PlayerSnapshot target = trackedTarget(world, DETECT_RANGE, true);
        Mob socialTarget = target == null && !hasPlayerTarget()
                && MobRelationshipPolicy.mayDirectlyTarget(this, preparedSocialTarget())
                ? preparedSocialTarget() : null;
        if (target == null && socialTarget == null) {
            double[] movement = wander(rng, type.baseSpeed());
            dx = movement[0];
            dz = movement[1];
            state = dx == 0 && dz == 0 ? MobState.IDLE : MobState.WANDER;
        } else if (target != null) {
            faceToward(target.x(), target.z());
            double distance = dist3d(target);
            if (distance <= ATTACK_RANGE && canSeeTargetNow(world, target)) {
                state = MobState.ATTACK;
                if (attackTimer == 0) {
                    MobEvent.ShootArrow thrown = throwPotionAt(target, distance, rng);
                    if (thrown != null) events = appendEvent(events, thrown);
                    attackTimer = THROW_INTERVAL;   // 궤도 해가 없어도 주기는 유지한다
                }
            } else {
                state = MobState.CHASE;
                double[] movement = towardHoriz(target.x(), target.z(), type.baseSpeed());
                dx = movement[0];
                dz = movement[1];
            }
        } else {
            faceToward(socialTarget.x, socialTarget.z);
            double distance = Math.sqrt(MobRelationshipPolicy.distanceSquared(this, socialTarget));
            if (distance <= ATTACK_RANGE && attackTimer == 0
                    && MobRelationshipPolicy.hasLineOfSight(world, this, socialTarget)) {
                state = MobState.ATTACK;
                events = appendEvent(events, MobEvent.AttackMob.direct(socialTarget.id, 6));
                attackTimer = THROW_INTERVAL;
            } else {
                state = MobState.CHASE;
                double[] movement = towardHoriz(
                        socialTarget.x, socialTarget.z, type.baseSpeed());
                dx = movement[0];
                dz = movement[1];
            }
        }
        MobPhysics.tickMove(this, world, dx, 0, dz, MoveMode.WALK);
        return events;
    }

    /**
     * 바닐라 조건으로 물약을 고르고 표적 발밑을 향해 포물선으로 던진다.
     * 착탄 판정과 광역 효과는 {@link ProjectileSim} 과 이벤트 소비자가 담당한다.
     */
    private MobEvent.ShootArrow throwPotionAt(PlayerSnapshot target, double distance, MobRandom rng) {
        ProjectileEffect potion = MobEffectRules.selectWitchPotion(distance, target.health(),
                target.hasEffect(StatusEffect.SLOWNESS),
                target.hasEffect(StatusEffect.POISON),
                target.hasEffect(StatusEffect.WEAKNESS),
                rng.nextDouble());
        double sx = x;
        double sy = y + eyeHeight();
        double sz = z;
        // 바닐라와 같이 발밑을 노려 착탄점이 표적 주변 지면이 되게 한다.
        double[] v = Ballistics.solve(sx, sy, sz, target.x(), target.y(), target.z(),
                ProjectileSim.POTION_POWER, ProjectileSim.GRAVITY);
        if (v == null) return null;
        return new MobEvent.ShootArrow(ProjectileSim.Kind.SPLASH_POTION,
                sx, sy, sz, v[0], v[1], v[2], 0, potion);
    }
}
