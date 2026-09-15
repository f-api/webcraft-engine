package com.gameexpert.engine.mob;

import java.util.List;

/** Rare territorial bear with a telegraphed, committed rush and a punishable recovery. */
public final class GrizzlyBear extends MeleeMob {
    private int phase;
    private int remaining;
    private int chargeCooldown = 30;
    private double chargeX, chargeZ;
    private boolean chargeHit;

    public GrizzlyBear(long id, double x, double y, double z) {
        super(id, MobType.GRIZZLY_BEAR, x, y, z);
    }
    @Override protected double detectRange() { return 24; }
    @Override protected double attackRange() { return 2.6; }
    @Override protected int attackDamage() { return 9; }
    @Override protected int attackCooldownTicks() { return 18; }
    @Override protected boolean climbWalls() { return false; }
    @Override protected double moveSpeed() { return .10; }
    @Override public int visualFlags() { return phase | (hp <= 30 ? 8 : 0); }
    @Override protected double[] chaseMovement(MobWorldView world, PlayerSnapshot target, MobRandom rng) {
        return towardHoriz(target.x(), target.z(), .46);
    }

    private TargetInterception recover() {
        phase = 4;
        remaining = 11; // This blocked/finished tick is the first recovery tick.
        state = MobState.IDLE;
        return TargetInterception.HANDLED;
    }

    @Override protected TargetInterception interceptTarget(MobWorldView world, PlayerSnapshot target) {
        if (phase == 4) {
            state = MobState.IDLE;
            if (--remaining <= 0) {
                phase = 0;
                chargeCooldown = hp <= 30 ? 70 : 100;
            }
            return TargetInterception.HANDLED;
        }
        if (phase == 1) {
            state = MobState.ATTACK;
            if (remaining-- > 0) return TargetInterception.HANDLED;
            phase = 2;
            remaining = 8;
        }
        if (phase == 2) {
            if (remaining == 0) return recover();
            // Sweep the full collision hull; never auto-step or tunnel during a committed rush.
            for (int step = 1; step <= 5; step++) {
                double nextX = x + chargeX * step / 5.0;
                double nextZ = z + chargeZ * step / 5.0;
                if (MobPhysics.blockCollision(world, nextX - .95, y + .001, nextZ - .95,
                        nextX + .95, y + 2, nextZ + .95)) return recover();
            }
            remaining--;
            state = MobState.CHASE;
            List<MobEvent> events = List.of();
            if (!chargeHit && target != null && dist3d(target) <= attackRange()
                    && canSeeTargetNow(world, target)) {
                // One opportunity per rush, including rejected hits: no repeated contact spam.
                chargeHit = true;
                events = List.of(new MobEvent.AttackPlayer(target.nickname(),
                        contactDamage(world, 14), x, z, null, meleeEffect(world)));
            }
            return TargetInterception.handledMovement(events, chargeX, chargeZ);
        }
        if (chargeCooldown > 0) chargeCooldown--;
        if (chargeCooldown == 0 && target != null && canSeeTargetNow(world, target)) {
            double distance = dist3d(target);
            double horizontalDistance = Math.hypot(target.x() - x, target.z() - z);
            if (distance >= 5 && distance <= 12 && horizontalDistance > 1e-6) {
                double[] heading = towardHoriz(target.x(), target.z(), 1.0);
                chargeX = heading[0]; chargeZ = heading[1];
                faceToward(target.x(), target.z());
                phase = 1;
                remaining = 9;
                chargeHit = false;
                state = MobState.ATTACK;
                return TargetInterception.HANDLED;
            }
        }
        return TargetInterception.NONE;
    }
}
