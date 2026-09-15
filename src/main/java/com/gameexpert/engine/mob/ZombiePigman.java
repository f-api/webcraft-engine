package com.gameexpert.engine.mob;

import com.gameexpert.engine.inventory.PlayerInventory;

/** 1.16 이전 좀비 피그맨 복고 몹. 피격 전에는 중립이고 피격한 플레이어에게만 분노한다. */
public final class ZombiePigman extends MeleeMob {
    public static final double GROUP_ANGER_RADIUS = 32.0;
    /** WebCraft's fixed midpoint mapping of the documented pre-1.16 20–40 second range. */
    public static final int ANGER_DURATION_AUTHORITY_TICKS = 300;
    private String angerTarget;
    private int angerTicks;
    private boolean angrySoundPending;

    public ZombiePigman(long id, double x, double y, double z) {
        super(id, MobType.ZOMBIE_PIGMAN, x, y, z);
    }

    @Override protected double detectRange() { return 35.0; }
    @Override protected double attackRange() { return 1.5; }
    /** 바닐라 좀비 피그맨의 공격 피해량은 5점이다. */
    @Override protected int attackDamage() { return 5; }
    /** [SPEAR-MOB] {@code Zombie.addBehaviourGoals} 의 {@code SpearUseGoal}(창을 쥐면 돌진한다). */
    @Override public boolean spearUseEligible() { return true; }
    /** [SPEAR-MOB] {@code ATTACK_DAMAGE} 속성 기본값(5). */
    @Override public double attackDamageAttributeBase() { return 5.0; }
    @Override protected int attackCooldownTicks() { return 10; }
    @Override protected boolean climbWalls() { return false; }
    @Override protected boolean hostile(MobWorldView world) { return isAngry(); }

    /**
     * [SPEAR][B] 자연 무장. 핀 §5 가 금 창을 드는 종으로 지목했으므로 굴림에 걸린 개체는
     * 금 검 대신 금 창을 든다. 굴림은 난수를 <b>한 칸도 소비하지 않는</b> 몹 id 해시라
     * ({@code SpearRules.spawnHeldSpear}) 기존 스폰 굴림 수열이 한 칸도 밀리지 않는다.
     * 이 종은 성체 피글린과 달리 주손이 상태기계 분기 조건이 아니라 그대로 바꿔도 안전하다.
     */
    @Override public short heldItem() { return naturalWeapon(); }

    @Override public int heldItemDurability() {
        return PlayerInventory.initialDurability(naturalWeapon());
    }

    private short naturalWeapon() {
        short spear = com.gameexpert.engine.SpearRules.spawnHeldSpear(type, id);
        return spear != PlayerInventory.EMPTY ? spear : PlayerInventory.GOLD_SWORD;
    }

    @Override
    public void onHurt(String attackerNickname, double attackerX, double attackerZ) {
        angerAt(attackerNickname);
    }

    /** CombatSystem이 같은 방 규모(32블록)의 동종에게 전달하는 단순 집단 분노 API. */
    public void angerAt(String attackerNickname) {
        if (attackerNickname == null) return;
        if (!isAngry() || !attackerNickname.equals(angerTarget)) angrySoundPending = true;
        angerTarget = attackerNickname;
        angerTicks = ANGER_DURATION_AUTHORITY_TICKS;
        forceTarget(attackerNickname);
    }

    public boolean isAngry() { return angerTarget != null && angerTicks > 0; }
    String angerTarget() { return angerTarget; }
    int angerTicksRemaining() { return angerTicks; }

    void restoreAnger(String target, int ticks) {
        if (target == null ? ticks != 0
                : target.isEmpty() || ticks <= 0 || ticks > ANGER_DURATION_AUTHORITY_TICKS) {
            throw new IllegalArgumentException("invalid persisted Zombie Pigman anger");
        }
        angerTarget = target;
        angerTicks = ticks;
        forceTarget(target);
    }

    @Override
    public java.util.List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (isDead()) return java.util.List.of();
        if (isAngry()) forceTarget(angerTarget);
        java.util.List<MobEvent> events = super.tick(world, rng);
        if (angrySoundPending) {
            angrySoundPending = false;
            java.util.ArrayList<MobEvent> withAlert = new java.util.ArrayList<>(events.size() + 1);
            withAlert.add(new MobEvent.Sound("angry"));
            withAlert.addAll(events);
            events = withAlert;
        }
        if (angerTicks > 0 && --angerTicks == 0) {
            angerTarget = null;
            clearTrackedTarget();
        }
        return events;
    }
}
