package com.gameexpert.engine.mob;

/** Permanent flesh variants: no zombie infection, sunlight burning or underwater conversion. */
public final class Ghoul extends MeleeMob {
    public Ghoul(long id, double x, double y, double z, boolean juvenile) {
        super(id, juvenile ? MobType.BABY_GHOUL : MobType.GHOUL, x, y, z);
    }

    @Override protected double detectRange() { return 35.0; }
    @Override protected double attackRange() { return 1.5; }
    @Override protected int attackDamage() { return type == MobType.BABY_GHOUL ? 4 : 3; }
    @Override protected int attackCooldownTicks() { return commonAttackCooldownTicks(); }
    @Override protected boolean climbWalls() { return false; }
    private int windupTicks() { return type == MobType.BABY_GHOUL ? 4 : 2; }

    @Override protected TargetInterception interceptTarget(MobWorldView world, PlayerSnapshot target) {
        boolean anticipating = "melee".equals(actionKind()) && "anticipation".equals(actionPhase());
        if (attackCd > 0 || target == null || dist3d(target) > attackRange() || !canSeeTargetNow(world, target)) {
            if (anticipating) synchronizeVisualAction("none", "idle", 0);
            return TargetInterception.NONE;
        }
        faceToward(target.x(), target.z());
        state = MobState.ATTACK;
        if (!anticipating) {
            synchronizeVisualAction("melee", "anticipation", windupTicks());
            return TargetInterception.HANDLED;
        }
        int remaining = actionTicksRemaining() - 1;
        synchronizeVisualAction("melee", remaining > 0 ? "anticipation" : "active", Math.max(1, remaining));
        return remaining > 0 ? TargetInterception.HANDLED : TargetInterception.NONE;
    }

    @Override public void commitAcceptedMeleeAttack() {
        super.commitAcceptedMeleeAttack();
        // Telegraphing is part of the one-second cycle, not an extra attack delay.
        attackCd = commonAttackCooldownTicks() - windupTicks();
    }
}
