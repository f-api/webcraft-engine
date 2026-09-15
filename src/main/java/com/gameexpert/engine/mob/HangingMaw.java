package com.gameexpert.engine.mob;

import java.util.List;
import com.gameexpert.engine.effect.StatusEffect;

/** Fixed organic sentry. The support face, not target yaw, owns its body orientation. */
public final class HangingMaw extends Mob {
    private final FleshMawAttack attack = new FleshMawAttack();
    private int face = 0;

    public HangingMaw(long id, double x, double y, double z) {
        super(id, MobType.HANGING_MAW, Math.floor(x) + .5, Math.floor(y), Math.floor(z) + .5);
    }

    public int attachFace() { return face; }
    public void restoreAttachment(int face, int state) {
        if (face < 0 || face > 5 || face == 1 || state != 0)
            throw new IllegalStateException("invalid persisted hanging maw attachment");
        this.face = face;
    }
    @Override public boolean immovable() { return true; }
    @Override public boolean hasStepSound() { return false; }
    @Override public double eyeHeight() { return .65; }
    @Override public int visualFlags() { return face; }
    @Override public double[] authorityAabb() {
        double hx = ItemFrameRules.stepX(face) == 0 ? .8 : .7;
        double hy = ItemFrameRules.stepY(face) == 0 ? .8 : .7;
        double hz = ItemFrameRules.stepZ(face) == 0 ? .8 : .7;
        return new double[]{x-hx,y+.65-hy,z-hz,x+hx,y+.65+hy,z+hz};
    }

    @Override public List<MobEvent> tick(MobWorldView world, MobRandom random) {
        if (isDead()) return List.of();
        horizontalVx = horizontalVz = knockbackVx = knockbackVz = vy = 0;
        int sx = (int)Math.floor(x) - ItemFrameRules.stepX(face);
        int sy = (int)Math.floor(y) - ItemFrameRules.stepY(face);
        int sz = (int)Math.floor(z) - ItemFrameRules.stepZ(face);
        short support = world.getBlock(sx, sy, sz);
        if (support < 0 || !world.isChunkActive(sx >> 4, sz >> 4)) return List.of();
        if (!world.isSolid(support)) { kill(); return List.of(); }
        PlayerSnapshot target = trackedTarget(world, 18, true);
        double mx = x + ItemFrameRules.stepX(face) * .78;
        double my = y + .65 + ItemFrameRules.stepY(face) * .78;
        double mz = z + ItemFrameRules.stepZ(face) * .78;
        double dx = target == null ? 0 : target.x() - mx;
        double dy = target == null ? 0 : target.y() + 1.2 - my;
        double dz = target == null ? 0 : target.z() - mz;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean visible = target != null && world.hasLineOfSight(mx, my, mz,
                target.x(), target.y() + 1.2, target.z());
        boolean shoot = attack.advance(target == null ? null : target.nickname(), length, visible);
        state = "idle".equals(attack.phase()) ? MobState.IDLE : MobState.ATTACK;
        synchronizeVisualAction(shoot ? "ranged_release" : "ranged_charge", attack.phase(), attack.ticks());
        if (!shoot || length < 1e-6) return List.of();
        return List.of(new MobEvent.ShootArrow(ProjectileSim.Kind.FLESH_SPIT, mx, my, mz,
                dx / length * 1.2, dy / length * 1.2, dz / length * 1.2, 5,
                new ProjectileEffect(StatusEffect.SLOWNESS, 0, 20)));
    }
}
