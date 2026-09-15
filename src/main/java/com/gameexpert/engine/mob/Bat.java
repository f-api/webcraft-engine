package com.gameexpert.engine.mob;

import java.util.List;

import com.gameexpert.engine.BuildingBlockRules;
import com.gameexpert.terrain.Blocks;

/** 동굴 천장에 매달렸다가 플레이어가 가까워지면 날아다니는 비공격 박쥐. */
public final class Bat extends Mob {
    private static final double WAKE_RANGE = 4.0;
    private int targetX;
    private int targetY;
    private int targetZ;
    private boolean hasFlightTarget;
    private boolean resting = true;

    Bat(long id, double x, double y, double z) {
        super(id, MobType.BAT, x, y, z);
    }

    @Override
    public String movementMedium() {
        return resting ? "land" : "fly";
    }

    @Override
    public int visualFlags() {
        return resting ? Mob.VISUAL_BAT_RESTING : 0;
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (isDead()) return List.of();
        for (int gameTick = 0; gameTick < 2; gameTick++) tickGameTick(world, rng);
        return List.of();
    }

    private void tickGameTick(MobWorldView world, MobRandom rng) {
        int bx = (int) Math.floor(x);
        int ceilingY = (int) Math.floor(y) + 1;
        int bz = (int) Math.floor(z);
        short ceilingBlock = world.getBlock(bx, ceilingY, bz);
        int ceilingId = ceilingBlock & 0xffff;
        boolean ceiling = ceilingBlock >= 0 && BuildingBlockRules.isFullCollisionShape(
                ceilingId, world.blockState(bx, ceilingY, bz, ceilingId));

        if (resting) {
            if (!ceiling) {
                resting = false;
                return;
            } else {
                if (rng.nextInt(200) == 0) yaw = Math.toRadians(rng.nextInt(360));
                if (nearestAlive(world, WAKE_RANGE) != null) {
                    resting = false;
                    return;
                } else {
                    state = MobState.IDLE;
                    vy = 0;
                    return;
                }
            }
        }

        boolean validTarget = hasFlightTarget && targetY > Blocks.MIN_Y
                && world.getBlock(targetX, targetY, targetZ) == Blocks.AIR;
        if (!validTarget || rng.nextInt(30) == 0
                || distanceSquaredToTarget() < 4.0) {
            targetX = (int) Math.floor(x) + rng.nextInt(7) - rng.nextInt(7);
            targetY = (int) Math.floor(y) + rng.nextInt(6) - 2;
            targetZ = (int) Math.floor(z) + rng.nextInt(7) - rng.nextInt(7);
            hasFlightTarget = true;
        }

        double dx = targetX + 0.5 - x;
        double dy = targetY + 0.1 - y;
        double dz = targetZ + 0.5 - z;
        double flightDx = horizontalVx + (Math.signum(dx) * 0.5 - horizontalVx) * 0.1;
        double flightDz = horizontalVz + (Math.signum(dz) * 0.5 - horizontalVz) * 0.1;
        double desiredVy = vy + (Math.signum(dy) * 0.7 - vy) * 0.1;
        double flightDy = desiredVy - vy * MobPhysics.FLY_VERTICAL_DRAG
                + MobPhysics.GRAVITY * MobPhysics.FLY_GRAVITY_SCALE;
        yaw = Math.atan2(flightDz, flightDx);
        state = MobState.WANDER;
        MobPhysics.tickMove(this, world, flightDx, flightDy, flightDz, MoveMode.FLY);
        if (rng.nextInt(100) == 0 && ceiling) {
            resting = true;
            state = MobState.IDLE;
            vy = 0;
        }
    }

    private double distanceSquaredToTarget() {
        double dx = targetX + 0.5 - x;
        double dy = targetY + 0.1 - y;
        double dz = targetZ + 0.5 - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
