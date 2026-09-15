package com.gameexpert.engine.mob;

/** Raw-biome gate for Minecraft Java 26.3's real dappled forest. */
public final class DappledForestSpawnZone {

    private DappledForestSpawnZone() {}

    public static final int BIOME_ID = 187;

    public static boolean within(int biome) {
        return biome == BIOME_ID;
    }
}
