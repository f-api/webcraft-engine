package com.gameexpert.engine.mob;

/**
 * 스켈레톤 말(바닐라 실존 종, stableId 89). 수치 근거는 {@link MobType#SKELETON_HORSE} 의
 * 인라인 인용이다(MC Java 1.21.4 {@code EntityType.SKELETON_HORSE sized(1.3964844F, 1.6F)} ·
 * {@code createAttributes()} MAX_HEALTH 15 · MOVEMENT_SPEED 0.2).
 *
 * <p><b>뇌우 트랩 승격</b>(바닐라 {@code SkeletonHorse#setTrap}): 낙뢰가 떨어진 자리에
 * {@value #TRAP_CHANCE_PERCENT}% 확률로 트랩 개체가 솟고, 플레이어가
 * {@value #TRAP_TRIGGER_RANGE} 블록 안에 들어오면 트랩이 발동한다. 트랩은
 * {@value #TRAP_DESPAWN_TICKS} MC 틱(약 15분) 안에 발동하지 않으면 사라진다.
 *
 * <p><b>WebCraft divergence(등급 C)</b>: 바닐라 트랩은 스켈레톤 기수 4기를 함께 소환하지만
 * 이 웨이브에는 <b>기수 계약이 없다</b>(탑승 seatIndex 일반화가 해피 가스트 하네스 트랙과
 * 함께 다음 단계다). 그래서 이 트랙은 기수 수를 {@value #TRAP_RIDER_COUNT} 로 <b>축소</b>해
 * 말 옆에 걸어 세우고, 승격 자체는 낙뢰 파이프라인 훅에서만 성립시킨다. 축소의 이유가
 * 이 divergence 다.
 *
 * <p><b>안장 없이 탄다</b> — 바닐라 {@code AbstractHorse#isSaddleable} 이 스켈레톤 말에서
 * true 로 남고 안장 슬롯을 요구하지 않는다. 길들인 뒤의 탑승 배선은 다음 단계다.
 */
public final class SkeletonHorse extends AnimalMob {
    private final com.gameexpert.engine.ChestInventory equipment =
            new com.gameexpert.engine.ChestInventory(2);

    /** 낙뢰 자리에서 트랩 개체가 솟을 확률(%). */
    public static final int TRAP_CHANCE_PERCENT = 10;
    /** 트랩 발동 반경(블록). */
    public static final double TRAP_TRIGGER_RANGE = 10.0;
    /** 발동하지 않은 트랩이 사라지기까지의 MC 틱(약 15분). */
    public static final int TRAP_DESPAWN_TICKS = 18_000;
    /**
     * 이 웨이브가 세우는 기수 수. 바닐라는 4기지만 기수 계약이 없어 도보 스켈레톤
     * {@value} 기로 축소한다(divergence — 클래스 주석 참조).
     */
    public static final int TRAP_RIDER_COUNT = 1;

    private boolean trapActive;
    private int trapAgeMcTicks;

    public SkeletonHorse(long id, double x, double y, double z) {
        super(MobType.SKELETON_HORSE, id, x, y, z);
    }

    public boolean trapActive() { return trapActive; }
    com.gameexpert.engine.ChestInventory equipment() { return equipment; }
    public int trapAgeMcTicks() { return trapAgeMcTicks; }

    public void armTrap() {
        trapActive = true;
        trapAgeMcTicks = 0;
        setPersistenceRequired(true);
    }

    /** WebCraft authority runs at 10 TPS, while the persisted timer is the vanilla 20 TPS value. */
    public boolean advanceTrapAge() {
        if (!trapActive) return false;
        trapAgeMcTicks = Math.min(TRAP_DESPAWN_TICKS, trapAgeMcTicks + 2);
        return trapAgeMcTicks >= TRAP_DESPAWN_TICKS;
    }

    public boolean triggerTrap() {
        if (!trapActive) return false;
        trapActive = false;
        trapAgeMcTicks = 0;
        return true;
    }

    public void restoreTrapState(boolean active, int ageMcTicks) {
        if (ageMcTicks < 0 || ageMcTicks >= TRAP_DESPAWN_TICKS || !active && ageMcTicks != 0) {
            throw new IllegalArgumentException("invalid skeleton horse trap state");
        }
        trapActive = active;
        trapAgeMcTicks = ageMcTicks;
    }
}
