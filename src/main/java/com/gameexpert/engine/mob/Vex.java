package com.gameexpert.engine.mob;

import java.util.List;

import com.gameexpert.engine.inventory.PlayerInventory;

/** Summoned no-clip flying attacker with a telegraphed charge and bounded lifetime. */
public final class Vex extends Mob {
    private static final int CHARGE_ANTICIPATION_TICKS = 4;
    private static final int ATTACK_COOLDOWN_TICKS = 20;
    private static final int STARVATION_INTERVAL_AUTHORITY_TICKS = 10;
    private long summonerMobId;
    private int limitedLifeTicks;
    private int attackCooldown;

    public Vex(long id, double x, double y, double z) {
        super(id, MobType.VEX, x, y, z);
    }

    void bindSummoner(long summonerMobId, int limitedLifeTicks) {
        if (summonerMobId <= 0 || limitedLifeTicks <= 0) {
            throw new IllegalArgumentException("invalid Vex ownership");
        }
        this.summonerMobId = summonerMobId;
        this.limitedLifeTicks = limitedLifeTicks;
    }

    long summonerMobId() { return summonerMobId; }
    int limitedLifeTicks() { return limitedLifeTicks; }

    @Override public String movementMedium() { return "fly"; }
    @Override public short heldItem() { return PlayerInventory.IRON_SWORD; }
    @Override public int heldItemDurability() {
        return PlayerInventory.initialDurability(PlayerInventory.IRON_SWORD);
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        List<MobEvent> events = List.of();
        if (isDead()) return events;
        if (attackCooldown > 0) attackCooldown--;
        if (limitedLifeTicks > 0 && --limitedLifeTicks == 0) {
            limitedLifeTicks = STARVATION_INTERVAL_AUTHORITY_TICKS;
            damage(1.0);
            events = appendEvent(events, new MobEvent.EnvironmentDamage(isDead()));
            if (isDead()) return events;
        }

        PlayerSnapshot target = nearestAlive(world, 32.0);
        Mob socialTarget = target == null
                && MobRelationshipPolicy.mayDirectlyTarget(this, preparedSocialTarget())
                ? preparedSocialTarget() : null;
        double dx;
        double dy;
        double dz;
        if (target == null && socialTarget == null) {
            double[] movement = wander(rng, type.baseSpeed());
            dx = movement[0];
            dy = (rng.nextDouble() - 0.5) * 0.12;
            dz = movement[1];
            state = dx == 0.0 && dz == 0.0 ? MobState.IDLE : MobState.WANDER;
        } else {
            double tx = target != null ? target.x() : socialTarget.x;
            double ty = target != null ? target.y() + 0.9
                    : socialTarget.y + socialTarget.height() * 0.5;
            double tz = target != null ? target.z() : socialTarget.z;
            dx = tx - x;
            dy = ty - y;
            dz = tz - z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            faceToward(tx, tz);
            if ("none".equals(actionKind()) && attackCooldown == 0 && distance <= 12.0) {
                beginSemanticAction("charge", CHARGE_ANTICIPATION_TICKS);
            }
            boolean charging = "charge".equals(actionKind())
                    && "active".equals(actionPhase());
            double speed = charging ? 0.55 : type.baseSpeed();
            if (distance > 1e-9) {
                dx = dx / distance * speed;
                dy = dy / distance * speed;
                dz = dz / distance * speed;
            } else {
                dx = dy = dz = 0.0;
            }
            state = charging ? MobState.ATTACK : MobState.CHASE;
            if (charging && distance <= 1.2 && attackCooldown == 0) {
                events = target != null
                        ? appendEvent(events, new MobEvent.AttackPlayer(target.nickname(),
                                contactDamage(world, 9), x, z))
                        : appendEvent(events, MobEvent.AttackMob.direct(socialTarget.id, 9));
            }
        }
        MobPhysics.tickMove(this, world, dx, dy, dz, MoveMode.FLY_NOCLIP);
        return events;
    }

    @Override
    public void commitAcceptedMeleeAttack() {
        attackCooldown = ATTACK_COOLDOWN_TICKS;
        cancelSemanticAction(12);
    }
}
