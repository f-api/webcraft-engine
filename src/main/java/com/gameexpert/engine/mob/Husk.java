package com.gameexpert.engine.mob;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.effect.StatusEffect;

/**
 * 허스크: 좀비와 같은 근접 추적을 하지만 햇빛에 타지 않는다.
 * 빈손 근접 타격이 실제 피해를 주면 허기 상태이상을 부여한다.
 */
public final class Husk extends MeleeMob {
    public Husk(long id, double x, double y, double z) {
        super(id, MobType.HUSK, x, y, z);
        // [SPEAR-MOB] Zombie.populateDefaultEquipmentSlots 는 난이도를 보므로 첫 틱에 굴린다(Mob.resolvePendingSpawnWeapon).
        markSpawnWeaponPending();
    }

    @Override protected double detectRange() { return 35.0; }
    @Override protected double attackRange() { return 1.5; }
    @Override protected int attackDamage() { return 3; }
    /** [SPEAR-MOB] {@code Zombie.addBehaviourGoals} 의 {@code SpearUseGoal}(창을 쥐면 돌진한다). */
    @Override public boolean spearUseEligible() { return true; }
    /** [SPEAR-MOB] {@code ATTACK_DAMAGE} 속성 기본값(3). */
    @Override public double attackDamageAttributeBase() { return 3.0; }
    @Override protected int attackCooldownTicks() { return 10; }
    @Override protected boolean climbWalls() { return false; }

    @Override
    protected ProjectileEffect meleeEffect(MobWorldView world) {
        if (heldItem() != PlayerInventory.EMPTY) return null;
        // 지역 난이도는 범위 밖이므로 전역 easy/normal/hard를 7/14/21초로 매핑한다.
        // 바닐라의 140 * floor(local effective difficulty) MC 틱 공식과는 구별한다.
        return new ProjectileEffect(StatusEffect.HUNGER, 0, 70 * world.difficulty().id());
    }

}
