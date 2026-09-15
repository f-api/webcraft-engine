package com.gameexpert.frog.persistence.dto;

/** Hydrated absolute-tick cooldown for one Rafflesia colony. */
public final class FrogColonyCooldown {
    private final int rafflesiaX;
    private final int rafflesiaY;
    private final int rafflesiaZ;
    private final long nextReadyMcTick;

    public FrogColonyCooldown(int rafflesiaX, int rafflesiaY, int rafflesiaZ,
            long nextReadyMcTick) {
        this.rafflesiaX = rafflesiaX;
        this.rafflesiaY = rafflesiaY;
        this.rafflesiaZ = rafflesiaZ;
        this.nextReadyMcTick = nextReadyMcTick;
    }

    public int getRafflesiaX() { return rafflesiaX; }
    public int getRafflesiaY() { return rafflesiaY; }
    public int getRafflesiaZ() { return rafflesiaZ; }
    public long getNextReadyMcTick() { return nextReadyMcTick; }

    public boolean isReady(long currentMcTick) {
        return currentMcTick >= nextReadyMcTick;
    }
}
