package com.gameexpert.engine.mob;

import com.gameexpert.engine.effect.StatusEffect;

/** 자연 스폰 좀비 주민. 현재 제품에서는 일반 좀비와 같은 전투 AI를 쓴다. */
public final class ZombieVillager extends MeleeMob {
    /** 0 이면 전환 중이 아니다. 그 밖에는 남은 20 TPS 게임 틱 수다. */
    private int conversionMcTicks;
    /** 카운트다운을 시작시킨 플레이어 닉네임. 전환 중이 아니면 null 이다. */
    private String conversionStarter;

    public ZombieVillager(long id, double x, double y, double z) {
        super(id, MobType.ZOMBIE_VILLAGER, x, y, z);
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
    @Override protected int attackCooldownTicks() { return commonAttackCooldownTicks(); }
    @Override protected boolean climbWalls() { return false; }

    int conversionMcTicks() { return conversionMcTicks; }
    String conversionStarter() { return conversionStarter; }
    boolean converting() { return conversionMcTicks > 0; }

    /**
     * 바닐라 {@code setConverting}. 약함을 들고 있을 때만 성립하며 성공하면 약함을 제거하고
     * 카운트다운을 건다. 이미 전환 중이면 다시 걸지 않는다.
     */
    boolean beginConversion(String starter, MobRandom rng) {
        if (conversionMcTicks > 0 || !statusEffects().has(StatusEffect.WEAKNESS)) return false;
        conversionMcTicks = ZombieVillagerCureRules.rollConversionMcTicks(rng);
        conversionStarter = starter;
        statusEffects().remove(StatusEffect.WEAKNESS);
        return true;
    }

    /**
     * 권위 틱 1회(= 게임 틱 {@link ZombieVillagerCureRules#MC_TICKS_PER_AUTHORITY_TICK}회)를
     * 진행한다. 카운트다운이 0 이하가 되면 true 를 돌려주고 그 즉시 Villager 로 전환된다.
     */
    boolean advanceConversion(MobWorldView world, MobRandom rng) {
        if (conversionMcTicks <= 0) return false;
        for (int step = 0; step < ZombieVillagerCureRules.MC_TICKS_PER_AUTHORITY_TICK; step++) {
            conversionMcTicks -= ZombieVillagerCureRules.conversionRate(rng, world, x, y, z);
            if (conversionMcTicks <= 0) {
                conversionMcTicks = 0;
                return true;
            }
        }
        return false;
    }

    void restoreConversion(int restoredMcTicks, String restoredStarter) {
        if (!ZombieVillagerCureRules.conversionMcTicksValid(restoredMcTicks)) {
            throw new IllegalArgumentException("invalid persisted Zombie Villager conversion");
        }
        if (restoredMcTicks == 0 && restoredStarter != null) {
            throw new IllegalArgumentException("idle Zombie Villager has a conversion starter");
        }
        conversionMcTicks = restoredMcTicks;
        conversionStarter = restoredStarter;
    }

    @Override
    public int visualFlags() {
        return (conversionMcTicks > 0 ? Mob.VISUAL_ZOMBIE_VILLAGER_CONVERTING : 0)
                | villagerAppearanceFlags();
    }

}
