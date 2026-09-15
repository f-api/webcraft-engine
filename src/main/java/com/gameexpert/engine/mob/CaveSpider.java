package com.gameexpert.engine.mob;

/** 폐광 스포너에서만 나오는 작고 빠른 독거미. */
public final class CaveSpider extends Spider {
    public CaveSpider(long id, double x, double y, double z) {
        super(id, MobType.CAVE_SPIDER, x, y, z);
    }

    @Override protected double detectRange() { return 16.0; }
    @Override protected double attackRange() { return 1.2; }
    @Override protected int attackDamage() { return 2; }
    @Override protected int attackCooldownTicks() { return commonAttackCooldownTicks(); }
    @Override protected ProjectileEffect meleeEffect(MobWorldView world) {
        return MobEffectRules.caveSpiderPoison(world.difficulty());
    }
}
