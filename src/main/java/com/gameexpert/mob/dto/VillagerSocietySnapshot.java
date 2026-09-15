package com.gameexpert.mob.dto;

/**
 * 주민 한 명의 사회 상태(번식 willing 재고·구애·수면·골렘 기억) durable 스냅샷이다.
 * 침대 claim 은 별도 레인({@link VillagerBedClaimSnapshot})이 소유한다.
 */
public final class VillagerSocietySnapshot {
    private final long villagerId;
    private final int foodPoints;
    private final String foodInventory;
    private final long lastSleptTick;
    private final long golemMemoryTick;
    private final long mateId;
    private final long birthTick;
    private final long courtshipEndTick;

    public VillagerSocietySnapshot(long villagerId, int foodPoints, long lastSleptTick,
            long golemMemoryTick, long mateId, long birthTick, long courtshipEndTick, String foodInventory) {
        if (villagerId <= 0) throw new IllegalArgumentException("villagerId must be positive");
        if (foodPoints < 0) throw new IllegalArgumentException("foodPoints must not be negative");
        if (mateId < 0) throw new IllegalArgumentException("mateId must not be negative");
        this.villagerId = villagerId;
        int actualPoints = com.gameexpert.engine.mob.villager.VillagerFoodInventory.points(
                com.gameexpert.engine.mob.villager.VillagerFoodInventory.parse(foodInventory));
        if (foodPoints != actualPoints) throw new IllegalArgumentException("villager food point mismatch");
        this.foodInventory = foodInventory;
        this.foodPoints = actualPoints;
        this.lastSleptTick = lastSleptTick;
        this.golemMemoryTick = golemMemoryTick;
        this.mateId = mateId;
        this.birthTick = birthTick;
        this.courtshipEndTick = courtshipEndTick;
    }

    public long villagerId() { return villagerId; }

    public int foodPoints() { return foodPoints; }
    public String foodInventory() { return foodInventory; }

    /** 마지막으로 잔 MC gameTime. 없으면 {@link Long#MIN_VALUE}. */
    public long lastSleptTick() { return lastSleptTick; }

    /** GOLEM_DETECTED_RECENTLY 를 세운 MC gameTime. 없으면 {@link Long#MIN_VALUE}. */
    public long golemMemoryTick() { return golemMemoryTick; }

    /** 진행 중인 구애 상대. 없으면 0. */
    public long mateId() { return mateId; }

    public long birthTick() { return birthTick; }

    public long courtshipEndTick() { return courtshipEndTick; }
}
