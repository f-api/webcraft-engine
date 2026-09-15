package com.gameexpert.engine.mob;

/**
 * WebCraft 창작몹: 부패한 갈색곰. 상수·근거는 {@link ZombieBearRules} 가 소유한다.
 *
 * <p>{@link BrownBear} 와 같은 근접 AI 골격을 쓰되 좀비 보정을 받는다 — 좀비 계열과 같은
 * 35블록 추적 반경으로 멀리서 붙고, 추격 속도는 살아 있는 곰보다 느려 질주로 떨어뜨릴 수 있다.
 * 주간 소각은 하지 않는다({@code Mob#tickFireEnvironment} 의 {@code burnsInSun} 명단 밖).
 */
public final class ZombieBear extends MeleeMob {

    public ZombieBear(long id, double x, double y, double z) {
        super(id, MobType.ZOMBIE_BEAR, x, y, z);
    }

    @Override protected double detectRange() { return ZombieBearRules.DETECT_RANGE; }
    @Override protected double attackRange() { return ZombieBearRules.ATTACK_RANGE; }
    @Override protected int attackDamage() { return ZombieBearRules.ATTACK_DAMAGE; }
    @Override protected int attackCooldownTicks() { return ZombieBearRules.ATTACK_COOLDOWN_TICKS; }
    @Override protected boolean climbWalls() { return false; }
    @Override protected double moveSpeed() { return ZombieBearRules.IDLE_BLOCKS_PER_TICK; }

    @Override
    protected double[] chaseMovement(MobWorldView world, PlayerSnapshot target, MobRandom rng) {
        return towardHoriz(target.x(), target.z(), ZombieBearRules.PURSUIT_BLOCKS_PER_TICK);
    }
}
