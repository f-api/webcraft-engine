package com.gameexpert.engine.mob;

/**
 * 행상인(바닐라 실존 종, stableId 87). 수치 근거는 {@link MobType#WANDERING_TRADER} 의
 * 인라인 인용이다(MC Java 1.21.4 {@code EntityType.WANDERING_TRADER sized(0.6F, 1.95F)} ·
 * {@code createAttributes()} MAX_HEALTH 20 · MOVEMENT_SPEED 0.5).
 *
 * <p><b>주기 출현 계약</b>(바닐라 1.14 {@code WanderingTraderSpawner}):
 * {@code tickDelay} 가 {@value #SPAWN_DELAY_TICKS} MC 틱마다 한 번 굴림을 열고, 굴림 확률은
 * {@value #SPAWN_CHANCE_MIN_PERCENT}% 에서 시작해 실패할 때마다
 * {@value #SPAWN_CHANCE_STEP_PERCENT}% 씩 올라 {@value #SPAWN_CHANCE_MAX_PERCENT}% 에서
 * 멈춘다. 성공하면 확률은 다시 최소로 돌아간다. 나온 개체는 {@value #DESPAWN_TICKS} MC 틱
 * (60분) 뒤 소멸한다.
 *
 * <p>거래는 독립 행상인 풀에서 5/2/2개를 뽑고 주민과 같은 거래 화면·영속 원장을 쓴다.
 * 스폰 경로는 {@code MobSpawner#tryWanderingTraderArrival} 의 <b>독립 굴림</b>이라
 * 바이옴 가중치 표를 읽지도 쓰지도 않는다 — 기존 종의 확률이 한 톨도 바뀌지 않는다.
 */
public final class WanderingTrader extends AnimalMob implements TimedDespawn {

    /** 바닐라 {@code WanderingTraderSpawner} 의 굴림 주기 24000 MC 틱(하루). */
    public static final int SPAWN_DELAY_TICKS = 24_000;
    /** 굴림 확률 계단의 시작값(%). */
    public static final int SPAWN_CHANCE_MIN_PERCENT = 25;
    /** 실패할 때마다 오르는 폭(%). */
    public static final int SPAWN_CHANCE_STEP_PERCENT = 25;
    /** 계단의 상한(%). */
    public static final int SPAWN_CHANCE_MAX_PERCENT = 75;
    /** 체류 시간 48000 MC 틱(60분) 뒤 소멸한다. */
    public static final int DESPAWN_TICKS = 48_000;
    /** 함께 나오는 트레이더 라마 마릿수(바닐라 2기 리드 동행). */
    public static final int LLAMA_ESCORT_COUNT = 2;

    /**
     * 남은 체류 시간(MC 틱). 바닐라 {@code WanderingTrader#despawnDelay} 와 같은 자리이며
     * {@code WanderingTraderSpawner#spawn} 이 개체를 세울 때 48,000 으로 채운다.
     */
    private int despawnDelay = DESPAWN_TICKS;

    public WanderingTrader(long id, double x, double y, double z) {
        super(MobType.WANDERING_TRADER, id, x, y, z);
    }

    @Override
    public int remainingDespawnMcTicks() {
        return despawnDelay;
    }

    /** 되살린 개체의 남은 체류 시간 복원(영속 왕복). */
    public void setRemainingDespawnMcTicks(int mcTicks) {
        despawnDelay = Math.max(0, mcTicks);
    }

    @Override
    public boolean expireDespawnDelay(int mcTicks) {
        // 바닐라 `WanderingTrader#tick`: `if (!this.level().isClientSide) this.maybeDespawn();`
        // 이름표/길들임으로 영속이 걸린 개체는 시간이 흐르지 않는다(바닐라도 discard 하지 않는다).
        if (shouldPersist()) return false;
        despawnDelay = Math.max(0, despawnDelay - mcTicks);
        return despawnDelay == 0;
    }
}
