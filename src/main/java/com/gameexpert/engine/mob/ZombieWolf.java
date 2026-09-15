package com.gameexpert.engine.mob;

/**
 * WebCraft 창작몹: 부패한 늑대. 상수·근거는 {@link ZombieWolfRules} 가 소유한다.
 *
 * <p>{@link ZombieBear} 와 같은 근접 AI 골격을 쓰되 좀비 보정을 받는다 — 좀비 계열과 같은
 * 35블록 추적 반경으로 멀리서 붙고, 추격 속도는 플레이어 질주보다 느려 <b>한 마리</b>는 떨어뜨릴
 * 수 있다. 위협은 개체가 아니라 무리에서 나온다({@code MobSpawner#tryZombieWolfPack} 이 2~3마리를
 * 함께 붙인다). 주간 소각은 하지 않는다({@code Mob#tickFireEnvironment} 의 {@code burnsInSun}
 * 명단 밖).
 */
public final class ZombieWolf extends MeleeMob {

    public ZombieWolf(long id, double x, double y, double z) {
        super(id, MobType.ZOMBIE_WOLF, x, y, z);
    }

    @Override protected double detectRange() { return ZombieWolfRules.DETECT_RANGE; }
    @Override protected double attackRange() { return ZombieWolfRules.ATTACK_RANGE; }
    @Override protected int attackDamage() { return ZombieWolfRules.ATTACK_DAMAGE; }
    @Override protected int attackCooldownTicks() { return ZombieWolfRules.ATTACK_COOLDOWN_TICKS; }
    @Override protected boolean climbWalls() { return false; }
    @Override protected double moveSpeed() { return ZombieWolfRules.IDLE_BLOCKS_PER_TICK; }

    @Override
    protected double[] chaseMovement(MobWorldView world, PlayerSnapshot target, MobRandom rng) {
        return towardHoriz(target.x(), target.z(), ZombieWolfRules.PURSUIT_BLOCKS_PER_TICK);
    }
}
