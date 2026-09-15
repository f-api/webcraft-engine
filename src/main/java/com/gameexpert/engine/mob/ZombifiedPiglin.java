package com.gameexpert.engine.mob;

import java.util.List;

import com.gameexpert.engine.inventory.PlayerInventory;

/**
 * Modern Java 1.21.4 zombified piglin. It is intentionally separate from the
 * legacy ZombiePigman: modern anger has one explicit target and expires after
 * the pinned 600 MC ticks, with no group propagation in this transition.
 */
public final class ZombifiedPiglin extends MeleeMob {
    public static final int ANGER_DURATION_MC_TICKS = 600;
    public static final int MELEE_ATTACK_INTERVAL_MC_TICKS = 20;
    public static final int MC_TICKS_PER_SERVER_TICK = 2;
    public static final int MELEE_ATTACK_INTERVAL_SERVER_TICKS =
            MELEE_ATTACK_INTERVAL_MC_TICKS / MC_TICKS_PER_SERVER_TICK;

    private String angerTarget;
    private int angerMcTicks;

    public ZombifiedPiglin(long id, double x, double y, double z) {
        super(id, MobType.ZOMBIFIED_PIGLIN, x, y, z);
    }

    @Override protected double detectRange() { return 8.0; }
    @Override protected double attackRange() { return 1.5; }
    @Override protected int attackDamage() { return 5; }
    /** [SPEAR-MOB] {@code Zombie.addBehaviourGoals} 의 {@code SpearUseGoal}(창을 쥐면 돌진한다). */
    @Override public boolean spearUseEligible() { return true; }
    /** [SPEAR-MOB] {@code ATTACK_DAMAGE} 속성 기본값(5). */
    @Override public double attackDamageAttributeBase() { return 5.0; }
    @Override protected int attackCooldownTicks() {
        return MELEE_ATTACK_INTERVAL_SERVER_TICKS;
    }
    @Override protected boolean climbWalls() { return false; }
    @Override protected double moveSpeed() {
        return type.baseSpeed() + (isAngry() ? 0.05 : 0.0);
    }
    @Override protected boolean hostile(MobWorldView world) { return isAngry(); }

    /**
     * Natural modern zombified piglins visibly carry a golden sword.
     *
     * <p>[SPEAR][B] 핀 §5 가 금 창을 드는 종으로 지목했으므로 굴림에 걸린 개체는 금 검 대신
     * 금 창을 든다. 굴림은 난수를 <b>한 칸도 소비하지 않는</b> 몹 id 해시라
     * ({@code SpearRules.spawnHeldSpear}) 기존 스폰 굴림 수열이 한 칸도 밀리지 않는다.
     * 이 종은 성체 피글린과 달리 주손이 상태기계 분기 조건이 아니라 그대로 바꿔도 안전하다.
     */
    @Override public short heldItem() { return naturalWeapon(); }

    /** Keep the existing equipment carrier internally consistent for this visible tool. */
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

    /** Establishes the modern explicit attacker target without group propagation. */
    void angerAt(String attackerNickname) {
        if (attackerNickname == null) return;
        angerTarget = attackerNickname;
        angerMcTicks = ANGER_DURATION_MC_TICKS;
        forceTarget(attackerNickname);
    }

    /** Package-visible test seam for the first anger transition. */
    boolean isAngry() { return angerTarget != null && angerMcTicks > 0; }

    /** Package-visible test seam; no persistence contract is added here. */
    String angerTarget() { return angerTarget; }

    /** Package-visible timer seam proving the ordered virtual-MC-tick cursor. */
    int angerMcTicksRemaining() { return angerMcTicks; }

    void restoreAnger(String target, int ticks) {
        if (target == null ? ticks != 0
                : target.isEmpty() || ticks <= 0 || ticks > ANGER_DURATION_MC_TICKS) {
            throw new IllegalArgumentException("invalid persisted Zombified Piglin anger");
        }
        angerTarget = target;
        angerMcTicks = ticks;
        forceTarget(target);
    }

    @Override
    public List<MobEvent> tick(MobWorldView world, MobRandom rng) {
        if (isDead()) return List.of();
        advanceAngerByServerTick();
        if (isAngry()) forceTarget(angerTarget);
        return super.tick(world, rng);
    }

    /** The 10 TPS authority advances two ordered Minecraft ticks per server tick. */
    private void advanceAngerByServerTick() {
        for (int virtualMcTick = 0;
                virtualMcTick < MC_TICKS_PER_SERVER_TICK && angerMcTicks > 0;
                virtualMcTick++) {
            angerMcTicks--;
            if (angerMcTicks == 0) {
                angerTarget = null;
                forceTarget(null);
            }
        }
    }
}
