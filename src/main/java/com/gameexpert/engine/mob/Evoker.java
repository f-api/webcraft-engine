package com.gameexpert.engine.mob;

import java.util.List;

import com.gameexpert.engine.mob.illager.EvokerFangPattern;

/** Raid spellcaster with authority-owned cast phases, fangs, and bounded Vex summons. */
public final class Evoker extends Mob {
    private static final int CAST_ANTICIPATION_TICKS = 10;
    private static final int CAST_ACTIVE_TICKS = 8;
    private static final int CAST_INTERRUPTION_RECOVERY_TICKS = 10;
    private static final int FANG_COOLDOWN_TICKS = 100;
    private static final int SUMMON_COOLDOWN_TICKS = 170;

    private int spellCooldown;
    private double spellTargetX;
    private double spellTargetY;
    private double spellTargetZ;
    private EvokerFangPattern.Fang[] fangs = new EvokerFangPattern.Fang[0];

    public Evoker(long id, double x, double y, double z) {
        super(id, MobType.EVOKER, x, y, z);
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        List<MobEvent> events = List.of();
        if (isDead()) return events;
        if (spellCooldown > 0) spellCooldown--;
        PlayerSnapshot target = trackedTarget(world, 12.0, true);
        Mob socialTarget = target == null && !hasPlayerTarget()
                && MobRelationshipPolicy.mayDirectlyTarget(this, preparedSocialTarget())
                ? preparedSocialTarget() : null;
        if (target == null && socialTarget == null && "anticipation".equals(actionPhase())) {
            cancelSemanticAction(CAST_INTERRUPTION_RECOVERY_TICKS);
            fangs = new EvokerFangPattern.Fang[0];
        }
        if ((target != null || socialTarget != null)
                && "none".equals(actionKind()) && spellCooldown == 0) {
            double targetX = target != null ? target.x() : socialTarget.x;
            double targetY = target != null ? target.y() : socialTarget.y;
            double targetZ = target != null ? target.z() : socialTarget.z;
            spellTargetX = targetX;
            spellTargetY = targetY;
            spellTargetZ = targetZ;
            boolean summon = ((id + actionSequence()) & 3L) == 0L;
            if (summon) {
                fangs = new EvokerFangPattern.Fang[0];
                beginSemanticAction("summon_vex", CAST_ANTICIPATION_TICKS);
                spellCooldown = SUMMON_COOLDOWN_TICKS;
            } else {
                fangs = EvokerFangPattern.generate(x, z, targetX, targetZ);
                beginSemanticAction("cast_fangs", CAST_ANTICIPATION_TICKS);
                spellCooldown = FANG_COOLDOWN_TICKS;
            }
        }

        if ("summon_vex".equals(actionKind()) && "active".equals(actionPhase())
                && actionTicksRemaining() == CAST_ACTIVE_TICKS) {
            events = appendEvent(events, new MobEvent.SummonVex(
                    x + ((id & 1L) == 0L ? 1.0 : -1.0), y + 1.0,
                    z + ((id & 2L) == 0L ? 1.0 : -1.0)));
        }
        if ("cast_fangs".equals(actionKind()) && "active".equals(actionPhase())) {
            int elapsedAuthorityTicks = CAST_ACTIVE_TICKS - actionTicksRemaining();
            if (target != null) {
                for (PlayerSnapshot player : world.players()) {
                    if (!player.alive() || !fangHits(player, elapsedAuthorityTicks)) continue;
                    events = appendEvent(events, new MobEvent.AttackPlayer(player.nickname(),
                            contactDamage(world, 6), x, z));
                }
            } else if (socialTarget != null && fangHits(socialTarget, elapsedAuthorityTicks)) {
                events = appendEvent(events, MobEvent.AttackMob.direct(socialTarget.id, 6));
            }
        }

        double dx = 0.0;
        double dz = 0.0;
        if (target != null || socialTarget != null) {
            double targetX = target != null ? target.x() : socialTarget.x;
            double targetZ = target != null ? target.z() : socialTarget.z;
            faceToward(targetX, targetZ);
            double distance = Math.hypot(targetX - x, targetZ - z);
            if (distance < 8.0) {
                double[] away = towardHoriz(targetX, targetZ, type.baseSpeed());
                dx = -away[0];
                dz = -away[1];
                state = MobState.FLEE;
            } else if (distance > 12.0) {
                double[] toward = towardHoriz(targetX, targetZ, type.baseSpeed());
                dx = toward[0];
                dz = toward[1];
                state = MobState.CHASE;
            } else {
                state = "none".equals(actionKind()) ? MobState.IDLE : MobState.ATTACK;
            }
        } else {
            double[] movement = wander(rng, type.baseSpeed());
            dx = movement[0];
            dz = movement[1];
            state = dx == 0.0 && dz == 0.0 ? MobState.IDLE : MobState.WANDER;
        }
        MobPhysics.tickMove(this, world, dx, 0.0, dz, MoveMode.WALK);
        return events;
    }

    private boolean fangHits(PlayerSnapshot player, int elapsedAuthorityTicks) {
        return fangHits(player.x(), player.y(), player.z(), elapsedAuthorityTicks);
    }

    private boolean fangHits(Mob mob, int elapsedAuthorityTicks) {
        return fangHits(mob.x, mob.y, mob.z, elapsedAuthorityTicks);
    }

    private boolean fangHits(double targetX, double targetY, double targetZ,
            int elapsedAuthorityTicks) {
        for (EvokerFangPattern.Fang fang : fangs) {
            if (Math.floorDiv(fang.getWarmup(), 2) != elapsedAuthorityTicks) continue;
            double dx = targetX - fang.getX();
            double dz = targetZ - fang.getZ();
            if (dx * dx + dz * dz <= 0.9 * 0.9
                    && Math.abs(targetY - spellTargetY) <= 2.0) return true;
        }
        return false;
    }

    double spellTargetX() { return spellTargetX; }
    double spellTargetZ() { return spellTargetZ; }
}
