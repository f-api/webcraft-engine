package com.gameexpert.engine.hopper;

/**
 * {@code HopperBlockEntity.cooldownTime} and {@code tickedGameTime}. The constructor and a load
 * without {@code TransferCooldown} both start at {@link HopperRules#NO_COOLDOWN_TIME}.
 */
public final class HopperCooldown {
    private int cooldownTime = HopperRules.NO_COOLDOWN_TIME;
    private long tickedGameTime;

    public HopperCooldown() { }

    public HopperCooldown(int cooldownTime, long tickedGameTime) {
        this.cooldownTime = cooldownTime;
        this.tickedGameTime = tickedGameTime;
    }

    public int cooldownTime() { return cooldownTime; }
    public long tickedGameTime() { return tickedGameTime; }

    public void setCooldown(int value) { cooldownTime = value; }

    /** {@code isOnCooldown}: {@code cooldownTime > 0}. */
    public boolean isOnCooldown() { return cooldownTime > 0; }

    /** {@code isOnCustomCooldown}: {@code cooldownTime > MOVE_ITEM_SPEED}. */
    public boolean isOnCustomCooldown() { return cooldownTime > HopperRules.MOVE_ITEM_SPEED; }

    /** The first two statements of {@code pushItemsTick}. */
    void beginTick(long gameTime) {
        cooldownTime--;
        tickedGameTime = gameTime;
    }
}
