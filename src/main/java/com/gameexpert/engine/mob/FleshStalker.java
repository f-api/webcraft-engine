package com.gameexpert.engine.mob;

/** 균열 포식자: 사용자 정의 근접 적대종. 좀비의 변환·햇빛·감염 동작은 상속하지 않는다. */
public final class FleshStalker extends MeleeMob {
    private final FleshPounce pounce = new FleshPounce();
    public FleshStalker(long id, double x, double y, double z) {
        super(id, MobType.FLESH_STALKER, x, y, z);
    }

    @Override protected double detectRange() { return 35.0; }
    @Override protected double attackRange() { return 1.5; }
    @Override protected int attackDamage() { return 5; }
    @Override protected int attackCooldownTicks() { return commonAttackCooldownTicks(); }
    @Override protected boolean climbWalls() { return false; }

    @Override protected TargetInterception interceptTarget(MobWorldView world, PlayerSnapshot target) {
        var step = pounce.advance(x, y, z, onGround, target,
                target != null && canSeeTargetNow(world, target), () -> clearPounce(world, target));
        if (!step.handled) {
            if ("pounce".equals(actionKind())) synchronizeVisualAction("none", "idle", 0);
            return TargetInterception.NONE;
        }
        state = MobState.ATTACK;
        if (step.moveX != 0 || step.moveZ != 0) faceToward(x + step.moveX, z + step.moveZ);
        else if (target != null) faceToward(target.x(), target.z());
        if (step.launch) vy = .84 + MobPhysics.GRAVITY;
        synchronizeVisualAction("pounce", pounce.phase(), Math.max(1, pounce.ticks()));
        java.util.List<MobEvent> events = step.attack
                ? java.util.List.of(new MobEvent.AttackPlayer(target.nickname(), contactDamage(world, 7), x, z))
                : java.util.List.of();
        return TargetInterception.handledMovement(events, step.moveX, step.moveZ);
    }

    private boolean clearPounce(MobWorldView world, PlayerSnapshot target) {
        if (target == null || !MobPhysics.groundBelow(world, target.x(), target.y(), target.z(), width() / 2)) return false;
        double length = Math.hypot(target.x() - x, target.z() - z);
        int steps = Math.max(1, (int) Math.ceil(length / .25));
        double half = width() / 2;
        for (int step = 1; step <= steps; step++) {
            double t = (double) step / steps;
            double px = x + (target.x() - x) * t, pz = z + (target.z() - z) * t;
            double py = y + (target.y() - y) * t + Math.sin(Math.PI * t) * 1.5;
            if (MobPhysics.blockCollision(world, px - half, py, pz - half,
                    px + half, py + height(), pz + half)) return false;
        }
        return true;
    }
}
