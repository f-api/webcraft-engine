package com.gameexpert.engine.mob;

import java.util.List;

/** Heavy raid beast. Attack, shield stun and roar phases are durable MC-tick state. */
public final class Ravager extends RaidMeleeMob {
    /** 폭발 넉백 배율. 근접·화살 넉백은 MobKnockbackResistance(0.75)가 맡는다. */
    private static final double KNOCKBACK_RECEIVED = 0.25;
    public static final int ATTACK_DURATION_MC_TICKS = 10;
    public static final int STUN_DURATION_MC_TICKS = 40;
    public static final int ROAR_DURATION_MC_TICKS = 20;
    public static final int ROAR_TRIGGER_MC_TICKS = 10;
    public static final double ROAR_RADIUS = 4.0;
    public static final int ROAR_DAMAGE = 6;
    private static final int MC_TICKS_PER_AUTHORITY_TICK = 2;
    private static final double STRONG_KNOCKBACK_HORIZONTAL_MC = 4.0;
    private static final double STRONG_KNOCKBACK_VERTICAL_MC = 0.2;

    private int attackMcTicks;
    private int stunnedMcTicks;
    private int roarMcTicks;

    public Ravager(long id, double x, double y, double z) {
        super(id, MobType.RAVAGER, x, y, z, 12, 2.8);
    }

    @Override protected double detectRange() { return 32.0; }
    @Override protected int attackCooldownTicks() { return commonAttackCooldownTicks(); }

    public int attackMcTicks() { return attackMcTicks; }
    public int stunnedMcTicks() { return stunnedMcTicks; }
    public int roarMcTicks() { return roarMcTicks; }

    public void restoreActionTicks(int attackTicks, int stunnedTicks, int roarTicks) {
        if (attackTicks < 0 || attackTicks > ATTACK_DURATION_MC_TICKS
                || stunnedTicks < 0 || stunnedTicks > STUN_DURATION_MC_TICKS
                || roarTicks < 0 || roarTicks > ROAR_DURATION_MC_TICKS
                || (stunnedTicks > 0 && roarTicks > 0)) {
            throw new IllegalArgumentException("invalid persisted Ravager action state");
        }
        attackMcTicks = attackTicks;
        stunnedMcTicks = stunnedTicks;
        roarMcTicks = roarTicks;
        restoreActionVisual();
    }

    /** Returns true when the exact half-open 50% shield roll enters the 40-MC-tick stun. */
    public boolean onShieldBlocked(MobRandom rng) {
        if (stunnedMcTicks != 0 || roarMcTicks != 0 || rng.nextDouble() >= 0.5) return false;
        stunnedMcTicks = STUN_DURATION_MC_TICKS;
        markVisualAction("stunned", STUN_DURATION_MC_TICKS / MC_TICKS_PER_AUTHORITY_TICK);
        return true;
    }

    @Override
    public void commitAcceptedMeleeAttack() {
        attackCd = Math.max(1, (int) Math.ceil(
                attackCooldownTicks() * preparedRaidCooldownMultiplier()));
        attackMcTicks = ATTACK_DURATION_MC_TICKS;
        markVisualAction("melee", ATTACK_DURATION_MC_TICKS / MC_TICKS_PER_AUTHORITY_TICK);
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (isDead()) return List.of();
        boolean immobileThisTick = attackMcTicks > 0 || stunnedMcTicks > 0 || roarMcTicks > 0;
        MobEvent actionEvent = advanceActionState();
        if (immobileThisTick) {
            state = MobState.IDLE;
            MobPhysics.tickMove(this, world, 0.0, 0.0, 0.0, MoveMode.WALK);
            return actionEvent == null ? List.of() : List.of(actionEvent);
        }
        return super.tick(world, rng);
    }

    private MobEvent advanceActionState() {
        MobEvent event = null;
        for (int virtualTick = 0; virtualTick < MC_TICKS_PER_AUTHORITY_TICK; virtualTick++) {
            if (roarMcTicks > 0 && --roarMcTicks == ROAR_TRIGGER_MC_TICKS) {
                event = new MobEvent.RavagerRoar(x, y, z, ROAR_RADIUS, ROAR_DAMAGE);
                markVisualAction("roar", ROAR_TRIGGER_MC_TICKS / MC_TICKS_PER_AUTHORITY_TICK);
            }
            if (attackMcTicks > 0) attackMcTicks--;
            if (stunnedMcTicks > 0 && --stunnedMcTicks == 0) {
                roarMcTicks = ROAR_DURATION_MC_TICKS;
                markVisualAction("roar", ROAR_DURATION_MC_TICKS / MC_TICKS_PER_AUTHORITY_TICK);
                event = new MobEvent.Sound("roar");
            }
        }
        return event;
    }

    private void restoreActionVisual() {
        if (stunnedMcTicks > 0) {
            markVisualAction("stunned", Math.max(1,
                    (stunnedMcTicks + 1) / MC_TICKS_PER_AUTHORITY_TICK));
        } else if (roarMcTicks > 0) {
            markVisualAction("roar", Math.max(1,
                    (roarMcTicks + 1) / MC_TICKS_PER_AUTHORITY_TICK));
        } else if (attackMcTicks > 0) {
            markVisualAction("melee", Math.max(1,
                    (attackMcTicks + 1) / MC_TICKS_PER_AUTHORITY_TICK));
        }
    }

    /** Vanilla strong-knockback horizontal velocity converted to blocks/second for playerHurt. */
    public static double strongKnockbackBps(double delta, double dx, double dz) {
        double squared = Math.max(dx * dx + dz * dz, 0.001);
        return delta / squared * STRONG_KNOCKBACK_HORIZONTAL_MC * 20.0;
    }

    /** The same velocity converted to one 10 TPS authority displacement tick. */
    public static double strongKnockbackPerAuthorityTick(double delta, double dx, double dz) {
        return strongKnockbackBps(delta, dx, dz) / 10.0;
    }

    public static double strongKnockbackVerticalBps() {
        return STRONG_KNOCKBACK_VERTICAL_MC * 20.0;
    }

    public static double strongKnockbackVerticalPerAuthorityTick() {
        return strongKnockbackVerticalBps() / 10.0;
    }

    public static boolean withinRoarAabb(
            double sourceX, double sourceY, double sourceZ,
            double sourceWidth, double sourceHeight, double radius,
            double targetX, double targetY, double targetZ,
            double targetWidth, double targetHeight) {
        double sourceHalf = sourceWidth * 0.5 + radius;
        double targetHalf = targetWidth * 0.5;
        double sourceMinY = sourceY - radius;
        double sourceMaxY = sourceY + sourceHeight + radius;
        return targetX + targetHalf > sourceX - sourceHalf
                && targetX - targetHalf < sourceX + sourceHalf
                && targetY + targetHeight > sourceMinY
                && targetY < sourceMaxY
                && targetZ + targetHalf > sourceZ - sourceHalf
                && targetZ - targetHalf < sourceZ + sourceHalf;
    }

    @Override public void applyExplosionKnockback(double x, double y, double z) {
        super.applyExplosionKnockback(
                x * KNOCKBACK_RECEIVED, y * KNOCKBACK_RECEIVED, z * KNOCKBACK_RECEIVED);
    }
}
