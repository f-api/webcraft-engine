package com.gameexpert.engine.mob;

import java.util.List;

/** A usually neutral wild boar with occasional territorial aggression. */
public final class Boar extends MeleeMob {
    public static final int AGGRESSION_INTERVAL = 100;
    public static final int ANGER_DURATION = 200;
    private int aggressionCooldown = AGGRESSION_INTERVAL;
    private int angerTicks;
    private String angerTarget;

    public Boar(long id, double x, double y, double z) {
        super(id, MobType.BOAR, x, y, z);
    }

    @Override protected double detectRange() { return 16.0; }
    @Override protected double attackRange() { return 1.6; }
    @Override protected int attackDamage() { return 4; }
    @Override protected int attackCooldownTicks() { return 15; }
    @Override protected boolean climbWalls() { return false; }
    @Override protected double moveSpeed() { return 0.14; }
    @Override protected boolean hostile(MobWorldView world) { return angerTicks > 0; }
    @Override protected boolean canTargetPlayer(PlayerSnapshot player) {
        return angerTicks > 0 && player.nickname().equals(angerTarget);
    }
    @Override protected double[] chaseMovement(MobWorldView world, PlayerSnapshot target, MobRandom rng) {
        return towardHoriz(target.x(), target.z(), 0.40);
    }

    @Override public void onHurt(String nickname, double attackerX, double attackerZ) {
        if (nickname == null || nickname.isEmpty()) return;
        angerTarget = nickname;
        angerTicks = ANGER_DURATION;
        forceTarget(nickname);
    }

    public int angerTicks() { return angerTicks; }

    @Override public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (isDead()) return List.of();
        if (angerTicks == 0 && --aggressionCooldown <= 0) {
            aggressionCooldown = AGGRESSION_INTERVAL;
            PlayerSnapshot candidate = null;
            double nearest = 36.0;
            for (PlayerSnapshot player : world.players()) {
                if (!player.alive()) continue;
                double distance = sq(player.x() - x) + sq(player.y() - y) + sq(player.z() - z);
                if (distance <= nearest && canSee(world, player)) {
                    nearest = distance;
                    candidate = player;
                }
            }
            if (candidate != null && rng.nextInt(10) == 0) {
                onHurt(candidate.nickname(), candidate.x(), candidate.z());
            }
        }
        List<MobEvent> events = super.tick(world, rng);
        if (angerTicks > 0 && --angerTicks == 0) {
            angerTarget = null;
            clearTrackedTarget();
            aggressionCooldown = AGGRESSION_INTERVAL;
        }
        return events;
    }
}
