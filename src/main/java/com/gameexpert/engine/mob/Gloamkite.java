package com.gameexpert.engine.mob;

import java.util.List;

/** WebCraft original flying companion: marks a target, then commits to a readable dive. */
public final class Gloamkite extends IllagerCompanionMob {
    public Gloamkite(long id, double x, double y, double z) {
        super(id, MobType.GLOAMKITE, x, y, z);
    }

    @Override public String movementMedium() { return "fly"; }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (isDead()) return List.of();
        if (companionCooldownTicks > 0) companionCooldownTicks--;
        if (companionAbilityPhase == AbilityPhase.RECOVERY) {
            if (--companionAbilityTicks <= 0) clearCompanionAbility();
        }
        PlayerSnapshot target = companionTargetNickname == null
                ? trackedTarget(world, 24.0, true)
                : lockedTarget(world, companionTargetNickname, 24.0, true);
        if (target == null && companionTargetNickname != null) {
            companionCooldownTicks = IllagerCompanionPolicy.GLOAMKITE_COOLDOWN_TICKS;
            beginCompanionPhase(AbilityPhase.RECOVERY,
                    IllagerCompanionPolicy.GLOAMKITE_RECOVERY_TICKS, null);
        }
        if (target != null && companionAbilityPhase == AbilityPhase.IDLE
                && companionCooldownTicks == 0) {
            beginCompanionPhase(AbilityPhase.PRIMARY_TELEGRAPH,
                    IllagerCompanionPolicy.GLOAMKITE_MARK_TELEGRAPH_TICKS,
                    target.nickname());
            state = MobState.ATTACK;
            MobPhysics.tickMove(this, world, 0.0, 0.0, 0.0, MoveMode.FLY);
            return List.of();
        }
        if (target != null && companionAbilityPhase == AbilityPhase.PRIMARY_TELEGRAPH) {
            if (--companionAbilityTicks <= 0) {
                companionMarkTicks = IllagerCompanionPolicy.GLOAMKITE_MARK_DURATION_TICKS;
                beginCompanionPhase(AbilityPhase.SECONDARY_TELEGRAPH,
                        IllagerCompanionPolicy.GLOAMKITE_DIVE_TELEGRAPH_TICKS,
                        companionTargetNickname);
            }
            synchronizeCompanionPresentation();
            faceToward(target.x(), target.z());
            state = MobState.ATTACK;
            MobPhysics.tickMove(this, world, 0.0, 0.0, 0.0, MoveMode.FLY);
            return List.of();
        }
        if (target != null && companionAbilityPhase == AbilityPhase.SECONDARY_TELEGRAPH) {
            companionMarkTicks--;
            if (companionMarkTicks <= 0) {
                companionCooldownTicks = IllagerCompanionPolicy.GLOAMKITE_COOLDOWN_TICKS;
                beginCompanionPhase(AbilityPhase.RECOVERY,
                        IllagerCompanionPolicy.GLOAMKITE_RECOVERY_TICKS, null);
            } else if (--companionAbilityTicks <= 0) {
                beginCompanionActive();
            }
            synchronizeCompanionPresentation();
            faceToward(target.x(), target.z());
            state = MobState.ATTACK;
            MobPhysics.tickMove(this, world, 0.0, 0.0, 0.0, MoveMode.FLY);
            return List.of();
        }

        double dx;
        double dy;
        double dz;
        if (target != null && companionAbilityPhase == AbilityPhase.ACTIVE) {
            if (--companionMarkTicks <= 0) {
                companionCooldownTicks = IllagerCompanionPolicy.GLOAMKITE_COOLDOWN_TICKS;
                beginCompanionPhase(AbilityPhase.RECOVERY,
                        IllagerCompanionPolicy.GLOAMKITE_RECOVERY_TICKS, null);
                synchronizeCompanionPresentation();
                MobPhysics.tickMove(this, world, 0.0, 0.0, 0.0, MoveMode.FLY);
                return List.of();
            }
            double tx = target.x();
            double ty = target.y() + 0.8;
            double tz = target.z();
            double distance = Math.sqrt((tx - x) * (tx - x)
                    + (ty - y) * (ty - y) + (tz - z) * (tz - z));
            faceToward(tx, tz);
            if (distance > 1e-9) {
                dx = (tx - x) / distance * IllagerCompanionPolicy.GLOAMKITE_DIVE_SPEED;
                dy = (ty - y) / distance * IllagerCompanionPolicy.GLOAMKITE_DIVE_SPEED;
                dz = (tz - z) / distance * IllagerCompanionPolicy.GLOAMKITE_DIVE_SPEED;
            } else {
                dx = dy = dz = 0.0;
            }
            state = MobState.ATTACK;
            MobPhysics.tickMove(this, world, dx, dy, dz, MoveMode.FLY);
            if (distance <= 1.1) {
                return List.of(new MobEvent.AttackPlayer(target.nickname(),
                        contactDamage(world, IllagerCompanionPolicy.GLOAMKITE_DAMAGE), x, z));
            }
            return List.of();
        }

        synchronizeCompanionPresentation();
        Mob handler = preparedHandler();
        if (handler != null && Math.sqrt((handler.x - x) * (handler.x - x)
                + (handler.y + 1.8 - y) * (handler.y + 1.8 - y)
                + (handler.z - z) * (handler.z - z)) > 4.0) {
            double tx = handler.x;
            double ty = handler.y + 1.8;
            double tz = handler.z;
            double distance = Math.sqrt((tx - x) * (tx - x)
                    + (ty - y) * (ty - y) + (tz - z) * (tz - z));
            dx = (tx - x) / distance * 0.34;
            dy = (ty - y) / distance * 0.34;
            dz = (tz - z) / distance * 0.34;
            faceToward(tx, tz);
            state = MobState.WANDER;
        } else {
            double[] movement = wander(rng, 0.12);
            dx = movement[0];
            dy = (rng.nextDouble() - 0.5) * 0.08;
            dz = movement[1];
            state = MobState.WANDER;
        }
        MobPhysics.tickMove(this, world, dx, dy, dz, MoveMode.FLY);
        return List.of();
    }

    @Override
    public void commitAcceptedMeleeAttack() {
        companionCooldownTicks = IllagerCompanionPolicy.GLOAMKITE_COOLDOWN_TICKS;
        beginCompanionPhase(AbilityPhase.RECOVERY,
                IllagerCompanionPolicy.GLOAMKITE_RECOVERY_TICKS, null);
    }

}
