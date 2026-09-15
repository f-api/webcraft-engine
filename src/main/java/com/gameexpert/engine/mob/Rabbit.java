package com.gameexpert.engine.mob;

import java.util.List;

import com.gameexpert.terrain.Blocks;

/** 토끼의 점프 이동과 성숙한 당근 한 단계 갉아먹기를 서버에서 처리한다. */
public final class Rabbit extends AnimalMob {
    private static final int CROP_CHECK_TICKS = 200;
    private static final int MATURE_CARROT_STATE = 7;
    private int cropCheckTicks = CROP_CHECK_TICKS;
    private int cropProbe;
    private int hopCooldown;

    Rabbit(long id, double x, double y, double z, String variant) {
        super(MobType.RABBIT, id, x, y, z, variant);
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        List<MobEvent> events = super.tick(world, rng);
        if (hopCooldown > 0) hopCooldown--;
        if (onGround && hopCooldown == 0
                && Math.abs(horizontalVx) + Math.abs(horizontalVz) > 1e-4) {
            vy = state == MobState.FLEE ? 0.50 : 0.42;
            hopCooldown = state == MobState.FLEE ? 3 : 5;
        }

        if (--cropCheckTicks > 0 || isBaby() || isInLoveMode()) return events;
        cropCheckTicks = CROP_CHECK_TICKS;
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y + 0.01);
        int bz = (int) Math.floor(z);
        int direction = cropProbe++ & 3;
        if (direction == 0) bx++;
        else if (direction == 1) bx--;
        else if (direction == 2) bz++;
        else bz--;
        if (world.getBlock(bx, by, bz) == Blocks.CARROT_CROP
                && world.blockState(bx, by, bz, Blocks.CARROT_CROP) == MATURE_CARROT_STATE) {
            events = appendEvent(events, new MobEvent.ChangeBlockState(
                    bx, by, bz, Blocks.CARROT_CROP, MATURE_CARROT_STATE,
                    MATURE_CARROT_STATE - 1));
        }
        return events;
    }
}
