package com.gameexpert.engine.mob;

import com.gameexpert.engine.Fluids;

/** MC-228273: preparation yields to temptation, committed flight keeps its navigation until landing.
 * 26.3 timing/range mapped to WebCraft's 10 TPS and gravity 0.28; no persisted navigation cursor.
 */
final class GoatLongJump {
    private int phase; // 0 idle, 1 prepare, 2 air
    private int cooldown = -1, ticks, flightTicks;
    private double x, y, z, vx, vy, vz;
    private int cooldown(MobRandom rng) { return 600 + rng.nextInt(601); }
    private void reset(Mob mob, MobRandom rng) {
        phase = 0; cooldown = cooldown(rng); ticks = 0; vx = vy = vz = 0;
        mob.detourTicks = mob.blockedMoveTicks = 0;
    }
    boolean airborne() { return phase == 2; }

    boolean tick(Mob mob, MobWorldView world, MobRandom rng, boolean blocked, boolean ramIdle) {
        if (cooldown < 0) cooldown = cooldown(rng);
        if (phase == 2) {
            if ((mob.onGround && ticks > 0) || ticks >= 30
                    || Math.abs(mob.knockbackVx) + Math.abs(mob.knockbackVz) > 1e-6) {
                reset(mob, rng); return false;
            }
            mob.detourTicks = 0;
            MobPhysics.tickMove(mob, world, vx, 0, vz, MoveMode.WALK);
            ticks++;
            return true;
        }
        cooldown = Math.max(0, cooldown - 2);
        if (blocked || !ramIdle || !mob.onGround) {
            if (phase == 1) reset(mob, rng);
            return false;
        }
        if (phase == 1) {
            if (Math.sqrt(Math.pow(mob.x - x, 2) + Math.pow(mob.y - y, 2) + Math.pow(mob.z - z, 2)) > .1) {
                reset(mob, rng); return false;
            }
            ticks -= 2;
            if (ticks <= 0) {
                if (!clearArc(mob, world, x, y, z, vx, vy, vz, flightTicks)) {
                    reset(mob, rng); return false;
                }
                mob.vy = vy; mob.onGround = false;
                mob.detourTicks = mob.blockedMoveTicks = 0;
                phase = 2; ticks = 0;
                MobPhysics.tickMove(mob, world, vx, 0, vz, MoveMode.WALK);
                ticks++;
            } else MobPhysics.tickMove(mob, world, 0, 0, 0, MoveMode.WALK);
            return true;
        }
        if (cooldown > 0) return false;
        for (int attempt = 0; attempt < 32; attempt++) {
            int ox = rng.nextInt(11) - 5, oz = rng.nextInt(11) - 5;
            if (ox * ox + oz * oz < 4 || ox * ox + oz * oz > 25) continue;
            double tx = Math.floor(mob.x) + ox + .5, tz = Math.floor(mob.z) + oz + .5;
            double ty = Math.floor(mob.y) + rng.nextInt(11) - 5 + .001;
            int bx = (int) Math.floor(tx), by = (int) Math.floor(ty - .01), bz = (int) Math.floor(tz);
            short ground = world.getBlock(bx, by, bz);
            if (ground < 0 || world.waterAt(bx, by, bz) || Fluids.isLava(ground & 0xffff)) continue;
            if (!MobPhysics.blockCollision(world, tx - mob.width()/2, ty - .06, tz - mob.width()/2,
                    tx + mob.width()/2, ty, tz + mob.width()/2)) continue;
            for (int n = 6; n <= 16; n++) {
                double dx = (tx - mob.x)/n, dz = (tz - mob.z)/n;
                double dy = (ty - mob.y)/n + .28 * (n + 1)/2;
                if (dy <= .28 || Math.sqrt(dx*dx + dy*dy + dz*dz) > 3) continue;
                if (!clearArc(mob, world, mob.x, mob.y, mob.z, dx, dy, dz, n)) continue;
                phase = 1; ticks = 40; flightTicks = n;
                x = mob.x; y = mob.y; z = mob.z; vx = dx; vy = dy; vz = dz;
                return true;
            }
        }
        cooldown = 100;
        return false;
    }

    private static boolean clearArc(Mob mob, MobWorldView world,
            double x, double y, double z, double vx, double vy, double vz, int ticks) {
        double half = mob.width()/2, height = mob.height();
        for (int i = 0; i < ticks; i++) {
            vy -= .28;
            double nx = x + vx, ny = y + vy, nz = z + vz;
            if (MobPhysics.blockCollision(world, Math.min(x,nx)-half, y+.002, Math.min(z,nz)-half,
                    Math.max(x,nx)+half, y+height, Math.max(z,nz)+half)) return false;
            if (MobPhysics.blockCollision(world, nx-half, Math.min(y,ny)+.002, nz-half,
                    nx+half, Math.max(y,ny)+height, nz+half)) return false;
            int bx = (int)Math.floor(nx), by = (int)Math.floor(ny), bz = (int)Math.floor(nz);
            short block = world.getBlock(bx,by,bz);
            if (block < 0 || world.waterAt(bx,by,bz) || Fluids.isLava(block & 0xffff)) return false;
            x=nx; y=ny; z=nz;
        }
        return ticks > 0;
    }
}
