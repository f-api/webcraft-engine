package com.gameexpert.engine.mob;

/** 눈 바이옴의 유리자. */
public final class Stray extends Skeleton {
    public Stray(long id, double x, double y, double z) {
        super(id, MobType.STRAY, x, y, z);
    }

    @Override
    protected ProjectileEffect arrowEffect(MobWorldView world) {
        return MobEffectRules.strayArrow(world.difficulty());
    }
}
