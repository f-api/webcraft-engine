package com.gameexpert.engine.mob;

/** 늘지대의 낮은 체력 스켈레톤. */
public final class Bogged extends Skeleton {
    /** [CONTAINER-MENUS] {@code Bogged.DATA_SHEARED}: the head mushrooms were sheared off. */
    private boolean sheared;

    public Bogged(long id, double x, double y, double z) {
        super(id, MobType.BOGGED, x, y, z);
    }

    /**
     * {@code Bogged#readyForShearing}: alive and not yet sheared. Named apart from
     * {@link Mob#readyForShearing}, which stays the sheep gate.
     */
    public boolean readyForShears() {
        return !sheared && !isDead() && !removed;
    }

    public boolean sheared() {
        return sheared;
    }

    /** {@code Bogged#shear}: marks the head bare; the caller drops the shearing loot. */
    public void shear() {
        sheared = true;
    }

    void restoreSheared(boolean value) {
        sheared = value;
    }

    @Override
    public int visualFlags() {
        return super.visualFlags() | (sheared ? Mob.VISUAL_BOGGED_SHEARED : 0);
    }

    @Override
    protected ProjectileEffect arrowEffect(MobWorldView world) {
        return MobEffectRules.boggedArrow(world.difficulty());
    }
}
