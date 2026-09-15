package com.gameexpert.engine.mob;

/**
 * 새끼 좀비. MC 값인 체력 20, AABB 0.3×0.975, 성체 속도의 1.5배를
 * {@link MobType}에서 고정하며 성장 타이머 자체를 두지 않아 영구히 새끼로 남는다.
 */
public final class BabyZombie extends MeleeMob {
    public BabyZombie(long id, double x, double y, double z) {
        super(id, MobType.BABY_ZOMBIE, x, y, z);
        // [SPEAR-MOB] Zombie.populateDefaultEquipmentSlots 는 난이도를 보므로 첫 틱에 굴린다(Mob.resolvePendingSpawnWeapon).
        markSpawnWeaponPending();
    }

    @Override protected double detectRange() { return 35.0; }
    @Override protected double attackRange() { return 1.5; }
    /** 새끼 공격력은 §25에서 미확인. WebCraft 매핑은 기존 좀비의 3점을 재사용한다. */
    @Override protected int attackDamage() { return 3; }
    /** [SPEAR-MOB] {@code Zombie.addBehaviourGoals} 의 {@code SpearUseGoal}(창을 쥐면 돌진한다). */
    @Override public boolean spearUseEligible() { return true; }
    /** [SPEAR-MOB] {@code ATTACK_DAMAGE} 속성 기본값(3). */
    @Override public double attackDamageAttributeBase() { return 3.0; }
    @Override protected int attackCooldownTicks() { return 10; }
    @Override protected boolean climbWalls() { return false; }


}
