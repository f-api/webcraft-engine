package com.gameexpert.engine.mob;

/** Broad, low guardian: telegraphed charge only, never passive contact damage. */
public final class BoneProcession extends MeleeMob {
    private final FleshCharge charge = new FleshCharge();
    public BoneProcession(long id, double x, double y, double z) { super(id, MobType.BONE_PROCESSION, x, y, z); }
    @Override protected double detectRange() { return 35; }
    @Override protected double attackRange() { return 0; }
    @Override protected int attackDamage() { return 0; }
    @Override protected int attackCooldownTicks() { return 50; }
    @Override protected boolean climbWalls() { return false; }
    @Override protected double adjustIncomingDamage(double amount) { return amount * charge.damageMultiplier(); }
    @Override protected TargetInterception interceptTarget(MobWorldView world, PlayerSnapshot target) {
        var step = charge.advance(x,y,z,onGround,target,target != null && canSeeTargetNow(world,target),
                (dx,dz) -> clearStep(world,dx,dz));
        if (!step.handled) {
            if ("charge".equals(actionKind())) synchronizeVisualAction("none","idle",0);
            return target != null && Math.hypot(target.x()-x,target.z()-z) < 2
                    ? TargetInterception.handledMovement(java.util.List.of(),0,0) : TargetInterception.NONE;
        }
        state = MobState.ATTACK;
        if (step.moveX != 0 || step.moveZ != 0) faceToward(x+step.moveX,z+step.moveZ);
        else if (target != null) faceToward(target.x(),target.z());
        synchronizeVisualAction("charge",charge.phase(),Math.max(1,charge.ticks()));
        java.util.List<MobEvent> events = step.attack && target != null
                ? java.util.List.of(new MobEvent.AttackPlayer(target.nickname(),contactDamage(world,12),x,z)) : java.util.List.of();
        return TargetInterception.handledMovement(events,step.moveX,step.moveZ);
    }
    private boolean clearStep(MobWorldView world,double dx,double dz) {
        double half=width()/2;
        for(int i=1;i<=4;i++) {
            double px=x+dx*i/4,pz=z+dz*i/4;
            if(MobPhysics.blockCollision(world,px-half,y,pz-half,px+half,y+height(),pz+half)
                    || !MobPhysics.groundBelow(world,px,y,pz,half)) return false;
        }
        return true;
    }
}
