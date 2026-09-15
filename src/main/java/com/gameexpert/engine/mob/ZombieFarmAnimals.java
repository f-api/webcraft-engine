package com.gameexpert.engine.mob;

/**
 * 좀비화된 가축·야생 소동물 여섯 종(stableId 76~81)의 근접 AI. 전부 {@link ZombieWolf} 와
 * <b>같은 골격</b>이고 갈리는 것은 종별 {@code *Rules} 상수뿐이라, 종마다 파일을 하나씩 두는
 * 대신 여기 중첩 클래스로 모아 둔다 — 여섯 개의 사본이 서로 어긋날 여지를 없애는 것이 목적이다.
 *
 * <p>공통 계약({@link UndeadAnimalRules}): 원본 동물의 AABB·눈높이를 그대로 물려받고,
 * 체력 +4 · 배회 속도 ×0.8 · 감지 35블록 · 방어도 2 로 갈린다. 벽을 타지 않고, 주간에 타지
 * 않으며, 길들이거나 번식시킬 수 없다. 추격 속도는 전부 플레이어 질주(0.56블록/틱)보다
 * <b>느리다</b> — 한 마리는 반드시 떨어뜨릴 수 있어야 한다는 것이 좀비 동물의 불변 계약이다.
 *
 * <p>좀비 염소만 {@link ChargingUndeadAnimal} 을 상속해 공통 돌진을 재사용한다.
 */
public final class ZombieFarmAnimals {

    private ZombieFarmAnimals() {
    }

    /** 좀비 소. 흔한 티어의 벽. 상수는 {@link ZombieCowRules}. */
    public static final class ZombieCow extends MeleeMob {
        public ZombieCow(long id, double x, double y, double z) {
            super(id, MobType.ZOMBIE_COW, x, y, z);
        }

        @Override protected double detectRange() { return ZombieCowRules.DETECT_RANGE; }
        @Override protected double attackRange() { return ZombieCowRules.ATTACK_RANGE; }
        @Override protected int attackDamage() { return ZombieCowRules.ATTACK_DAMAGE; }
        @Override protected int attackCooldownTicks() {
            return ZombieCowRules.ATTACK_COOLDOWN_TICKS;
        }
        @Override protected boolean climbWalls() { return false; }
        @Override protected double moveSpeed() {
            return ZombieCowRules.IDLE_BLOCKS_PER_TICK;
        }

        @Override
        protected double[] chaseMovement(MobWorldView world, PlayerSnapshot target,
                                         MobRandom rng) {
            return towardHoriz(target.x(), target.z(),
                    ZombieCowRules.PURSUIT_BLOCKS_PER_TICK);
        }
    }

    /** 좀비 돼지. {@link MobType#ZOMBIE_PIGMAN}(돼지 인간형)과는 다른 종이다. */
    public static final class ZombiePig extends MeleeMob {
        public ZombiePig(long id, double x, double y, double z) {
            super(id, MobType.ZOMBIE_PIG, x, y, z);
        }

        @Override protected double detectRange() { return ZombiePigRules.DETECT_RANGE; }
        @Override protected double attackRange() { return ZombiePigRules.ATTACK_RANGE; }
        @Override protected int attackDamage() { return ZombiePigRules.ATTACK_DAMAGE; }
        @Override protected int attackCooldownTicks() {
            return ZombiePigRules.ATTACK_COOLDOWN_TICKS;
        }
        @Override protected boolean climbWalls() { return false; }
        @Override protected double moveSpeed() {
            return ZombiePigRules.IDLE_BLOCKS_PER_TICK;
        }

        @Override
        protected double[] chaseMovement(MobWorldView world, PlayerSnapshot target,
                                         MobRandom rng) {
            return towardHoriz(target.x(), target.z(),
                    ZombiePigRules.PURSUIT_BLOCKS_PER_TICK);
        }
    }

    /**
     * 좀비 양. <b>전단할 수 없다</b> — 가위 경로는 {@link MobType#SHEEP} 만 통과시키고
     * 이 종은 그 문에 닿지 않는다. 양털은 살아서도 죽어서도 나오지 않는다.
     */
    public static final class ZombieSheep extends MeleeMob {
        public ZombieSheep(long id, double x, double y, double z) {
            super(id, MobType.ZOMBIE_SHEEP, x, y, z);
        }

        @Override protected double detectRange() { return ZombieSheepRules.DETECT_RANGE; }
        @Override protected double attackRange() { return ZombieSheepRules.ATTACK_RANGE; }
        @Override protected int attackDamage() { return ZombieSheepRules.ATTACK_DAMAGE; }
        @Override protected int attackCooldownTicks() {
            return ZombieSheepRules.ATTACK_COOLDOWN_TICKS;
        }
        @Override protected boolean climbWalls() { return false; }
        @Override protected double moveSpeed() {
            return ZombieSheepRules.IDLE_BLOCKS_PER_TICK;
        }

        @Override
        protected double[] chaseMovement(MobWorldView world, PlayerSnapshot target,
                                         MobRandom rng) {
            return towardHoriz(target.x(), target.z(),
                    ZombieSheepRules.PURSUIT_BLOCKS_PER_TICK);
        }
    }

    /** 좀비 염소. 살아 있는 염소의 들이받기가 부패해서도 남았다 — 공통 돌진을 재사용한다. */
    public static final class ZombieGoat extends ChargingUndeadAnimal {
        public ZombieGoat(long id, double x, double y, double z) {
            super(id, MobType.ZOMBIE_GOAT, x, y, z);
        }

        @Override protected double detectRange() { return ZombieGoatRules.DETECT_RANGE; }
        @Override protected double attackRange() { return ZombieGoatRules.ATTACK_RANGE; }
        @Override protected int attackDamage() { return ZombieGoatRules.ATTACK_DAMAGE; }
        @Override protected int attackCooldownTicks() {
            return ZombieGoatRules.ATTACK_COOLDOWN_TICKS;
        }
        @Override protected boolean climbWalls() { return false; }
        @Override protected double moveSpeed() {
            return ZombieGoatRules.IDLE_BLOCKS_PER_TICK;
        }
        @Override protected double pursuitSpeed() {
            return ZombieGoatRules.PURSUIT_BLOCKS_PER_TICK;
        }
    }

    /** 좀비 여우. 혼자서 가장 끈질기게 붙는 종이지만 그래도 질주보다는 느리다. */
    public static final class ZombieFox extends MeleeMob {
        public ZombieFox(long id, double x, double y, double z) {
            super(id, MobType.ZOMBIE_FOX, x, y, z);
        }

        @Override protected double detectRange() { return ZombieFoxRules.DETECT_RANGE; }
        @Override protected double attackRange() { return ZombieFoxRules.ATTACK_RANGE; }
        @Override protected int attackDamage() { return ZombieFoxRules.ATTACK_DAMAGE; }
        @Override protected int attackCooldownTicks() {
            return ZombieFoxRules.ATTACK_COOLDOWN_TICKS;
        }
        @Override protected boolean climbWalls() { return false; }
        @Override protected double moveSpeed() {
            return ZombieFoxRules.IDLE_BLOCKS_PER_TICK;
        }

        @Override
        protected double[] chaseMovement(MobWorldView world, PlayerSnapshot target,
                                         MobRandom rng) {
            return towardHoriz(target.x(), target.z(),
                    ZombieFoxRules.PURSUIT_BLOCKS_PER_TICK);
        }
    }

    /**
     * 좀비 닭. 날개가 썩어 <b>활공하지 않고</b>({@code MoveMode.WALK} 만 쓴다) 알을 품지도
     * 않는다 — 살아 있는 닭의 주기적 산란({@link Chicken}) 경로에 닿지 않는다. 상한 달걀은
     * 사망 드랍으로만 나온다({@code MobSystem.animalDrops} 의 {@code ZOMBIE_CHICKEN} 절).
     */
    public static final class ZombieChicken extends MeleeMob {
        public ZombieChicken(long id, double x, double y, double z) {
            super(id, MobType.ZOMBIE_CHICKEN, x, y, z);
        }

        @Override protected double detectRange() { return ZombieChickenRules.DETECT_RANGE; }
        @Override protected double attackRange() { return ZombieChickenRules.ATTACK_RANGE; }
        @Override protected int attackDamage() { return ZombieChickenRules.ATTACK_DAMAGE; }
        @Override protected int attackCooldownTicks() {
            return ZombieChickenRules.ATTACK_COOLDOWN_TICKS;
        }
        @Override protected boolean climbWalls() { return false; }
        @Override protected double moveSpeed() {
            return ZombieChickenRules.IDLE_BLOCKS_PER_TICK;
        }

        @Override
        protected double[] chaseMovement(MobWorldView world, PlayerSnapshot target,
                                         MobRandom rng) {
            return towardHoriz(target.x(), target.z(),
                    ZombieChickenRules.PURSUIT_BLOCKS_PER_TICK);
        }
    }
}
