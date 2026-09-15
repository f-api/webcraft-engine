package com.gameexpert.engine.mob;

import java.util.List;

/** WebCraft original ground companion: telegraphs a bounded bramble charge. */
public final class Briarback extends IllagerCompanionMob {
    public Briarback(long id, double x, double y, double z) {
        super(id, MobType.BRIARBACK, x, y, z);
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (isDead()) return List.of();
        if (companionCooldownTicks > 0) companionCooldownTicks--;
        if (companionAbilityPhase == AbilityPhase.RECOVERY) {
            if (--companionAbilityTicks <= 0) clearCompanionAbility();
        }
        PlayerSnapshot target = companionTargetNickname == null
                ? trackedTarget(world, 24.0, true)
                : lockedTarget(world, companionTargetNickname, 24.0, false);
        double dx = 0.0;
        double dz = 0.0;
        if (target != null) {
            double distance = Math.hypot(target.x() - x, target.z() - z);
            faceToward(target.x(), target.z());
            if (companionAbilityPhase == AbilityPhase.IDLE
                    && companionCooldownTicks == 0 && distance <= 16.0) {
                companionChargeDistance = 0.0;
                beginCompanionPhase(AbilityPhase.PRIMARY_TELEGRAPH,
                        IllagerCompanionPolicy.BRIARBACK_CHARGE_TELEGRAPH_TICKS,
                        target.nickname());
                state = MobState.ATTACK;
                MobPhysics.tickMove(this, world, 0.0, 0.0, 0.0, MoveMode.WALK);
                return List.of();
            }
            if (companionAbilityPhase == AbilityPhase.PRIMARY_TELEGRAPH) {
                state = MobState.ATTACK;
                if (--companionAbilityTicks <= 0) beginCompanionActive();
                synchronizeCompanionPresentation();
                MobPhysics.tickMove(this, world, 0.0, 0.0, 0.0, MoveMode.WALK);
                return List.of();
            }
            if (companionAbilityPhase == AbilityPhase.ACTIVE) {
                double[] movement = towardHoriz(target.x(), target.z(),
                        IllagerCompanionPolicy.BRIARBACK_SPEED);
                dx = movement[0];
                dz = movement[1];
                companionChargeDistance += Math.hypot(dx, dz);
                state = MobState.ATTACK;
                if (distance <= 1.35 && companionCooldownTicks == 0) {
                    return moveAndAppend(world, dx, dz,
                            new MobEvent.AttackPlayer(target.nickname(),
                                    contactDamage(world, IllagerCompanionPolicy.BRIARBACK_DAMAGE),
                                    x, z));
                }
                if (companionChargeDistance >= IllagerCompanionPolicy.BRIARBACK_MAX_CHARGE_BLOCKS) {
                    companionCooldownTicks = IllagerCompanionPolicy.BRIARBACK_COOLDOWN_TICKS;
                    beginCompanionPhase(AbilityPhase.RECOVERY,
                            IllagerCompanionPolicy.BRIARBACK_RECOVERY_TICKS, null);
                    dx = dz = 0.0;
                }
            } else {
                state = MobState.CHASE;
            }
        } else if (companionTargetNickname != null) {
            companionCooldownTicks = IllagerCompanionPolicy.BRIARBACK_COOLDOWN_TICKS;
            beginCompanionPhase(AbilityPhase.RECOVERY,
                    IllagerCompanionPolicy.BRIARBACK_RECOVERY_TICKS, null);
        } else {
            Mob handler = preparedHandler();
            if (handler != null && Math.hypot(handler.x - x, handler.z - z) > 3.0) {
                double[] movement = towardHoriz(handler.x, handler.z, 0.32);
                dx = movement[0];
                dz = movement[1];
                faceToward(handler.x, handler.z);
                state = MobState.WANDER;
            } else {
                double[] movement = wander(rng, 0.08);
                dx = movement[0];
                dz = movement[1];
                state = dx == 0.0 && dz == 0.0 ? MobState.IDLE : MobState.WANDER;
            }
        }
        synchronizeCompanionPresentation();
        MobPhysics.tickMove(this, world, dx, 0.0, dz, MoveMode.WALK);
        return List.of();
    }

    private List<MobEvent> moveAndAppend(MobWorldView world, double dx, double dz,
            MobEvent event) {
        MobPhysics.tickMove(this, world, dx, 0.0, dz, MoveMode.WALK);
        return List.of(event);
    }

    @Override
    public void commitAcceptedMeleeAttack() {
        companionCooldownTicks = IllagerCompanionPolicy.BRIARBACK_COOLDOWN_TICKS;
        beginCompanionPhase(AbilityPhase.RECOVERY,
                IllagerCompanionPolicy.BRIARBACK_RECOVERY_TICKS, null);
    }
}
