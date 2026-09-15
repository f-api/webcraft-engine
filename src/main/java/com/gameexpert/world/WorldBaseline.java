package com.gameexpert.world;

import com.gameexpert.terrain.mc.McTerrainDataPin;

/** Single build/world-generation identity shared by every newly created world. */
public final class WorldBaseline {

    public static final String ID = "minecraft-java-26.3-snapshot-7+wv5009+dp115+rp95";
    public static final String INPUT_FINGERPRINT_SHA256 = McTerrainDataPin.INPUT_FINGERPRINT_SHA256;
    /** sha256-path-length-bytes-v1 over every canonical Java terrain source file. */
    public static final String GENERATOR_SOURCE_SHA256 =
            "7f461999fb81a5c3de449f1c170e4532ebb11f56518ddb616432e610c7fef5e9";
    /** Number of terrain source files {@link #GENERATOR_SOURCE_SHA256} was reviewed over. */
    public static final int GENERATOR_SOURCE_FILE_COUNT = 320;
    public static final int WORLD_VERSION = McTerrainDataPin.WORLD_VERSION;
    public static final int DATA_PACK_MAJOR = McTerrainDataPin.DATA_PACK_MAJOR;
    public static final int RESOURCE_PACK_MAJOR = McTerrainDataPin.RESOURCE_PACK_MAJOR;
    public static final int PROTOCOL_VERSION = McTerrainDataPin.PROTOCOL_VERSION;

    private WorldBaseline() { }
}
