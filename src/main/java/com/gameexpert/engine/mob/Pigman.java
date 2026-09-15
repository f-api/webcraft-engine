package com.gameexpert.engine.mob;

import java.util.List;

/** WebCraft living pig humanoid: a neutral structure sentinel with independent anger. */
public final class Pigman extends MeleeMob {
    public static final int ANGER_DURATION_AUTHORITY_TICKS = 300;
    public static final int MC_TICKS_PER_AUTHORITY_TICK = 2;
    public static final int ANGER_DURATION_MC_TICKS =
            ANGER_DURATION_AUTHORITY_TICKS * MC_TICKS_PER_AUTHORITY_TICK;
    public static final int MELEE_ATTACK_INTERVAL_AUTHORITY_TICKS = 10;

    private String angerTarget;
    private int angerMcTicks;

    public Pigman(long id, double x, double y, double z) {
        super(id, MobType.PIGMAN, x, y, z);
    }

    @Override protected double detectRange() { return 12.0; }
    @Override protected double attackRange() { return 2.5; }
    @Override protected int attackDamage() { return 6; }
    @Override protected int attackCooldownTicks() {
        return MELEE_ATTACK_INTERVAL_AUTHORITY_TICKS;
    }
    @Override protected boolean climbWalls() { return false; }
    @Override protected boolean hostile(MobWorldView world) { return isAngry(); }

    @Override
    public void onHurt(String attackerNickname, double attackerX, double attackerZ) {
        angerAt(attackerNickname);
    }

    void angerAt(String attackerNickname) {
        if (attackerNickname == null) return;
        angerTarget = attackerNickname;
        angerMcTicks = ANGER_DURATION_MC_TICKS;
        forceTarget(attackerNickname);
    }

    boolean isAngry() { return angerTarget != null && angerMcTicks > 0; }
    String angerTarget() { return angerTarget; }
    int angerMcTicksRemaining() { return angerMcTicks; }

    void restoreAnger(String target, int ticks) {
        if (target == null ? ticks != 0
                : target.isEmpty() || ticks <= 0 || ticks > ANGER_DURATION_MC_TICKS) {
            throw new IllegalArgumentException("invalid persisted Pigman anger");
        }
        angerTarget = target;
        angerMcTicks = ticks;
        forceTarget(target);
    }

    @Override
    protected PlayerSnapshot trackedTarget(MobWorldView world, double maxDist,
            boolean mayAcquire) {
        PlayerSnapshot target = super.trackedTarget(world, maxDist, mayAcquire);
        if (target == null || (angerTarget != null && angerTarget.equals(target.nickname()))) return target;
        forceTarget(angerTarget);
        return null;
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (isDead()) return List.of();
        if (isAngry()) forceTarget(angerTarget);
        else clearTrackedTarget();
        List<MobEvent> events = super.tick(world, rng);
        advanceAngerByAuthorityTick();
        if (!isAngry()) clearTrackedTarget();
        return events;
    }

    private void advanceAngerByAuthorityTick() {
        for (int logicalTick = 0;
                logicalTick < MC_TICKS_PER_AUTHORITY_TICK && angerMcTicks > 0;
                logicalTick++) {
            angerMcTicks--;
            if (angerMcTicks == 0) angerTarget = null;
        }
    }
}
