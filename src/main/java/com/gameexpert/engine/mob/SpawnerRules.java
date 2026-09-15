package com.gameexpert.engine.mob;

import com.gameexpert.terrain.Blocks;

/** Runtime spawner state shared by natural dungeons, generated structures and mob ticking. */
public final class SpawnerRules {
    public static final int ZOMBIE = 1;
    public static final int SPIDER = 2;
    public static final int SKELETON = 3;
    public static final int CAVE_SPIDER = 4;

    private SpawnerRules() {
    }

    public static int stateForEntityKey(String entityKey) {
        return switch (entityKey) {
            case "minecraft:zombie" -> ZOMBIE;
            case "minecraft:spider" -> SPIDER;
            case "minecraft:skeleton" -> SKELETON;
            case "minecraft:cave_spider" -> CAVE_SPIDER;
            default -> 0;
        };
    }

    /** Exact schema-4 visual carrier evidence accepted for each supported entity key. */
    public static int carrierForEntityKey(String entityKey) {
        return switch (entityKey) {
            case "minecraft:zombie" -> Blocks.SPAWNER_BASE;
            case "minecraft:spider", "minecraft:cave_spider" -> Blocks.SPAWNER_BASE + 1;
            case "minecraft:skeleton" -> Blocks.SPAWNER_BASE + 2;
            default -> 0;
        };
    }

    /** Converts the three visual carrier IDs into the authoritative state used at runtime. */
    public static int stateForCarrier(int blockId) {
        return switch (blockId) {
            case Blocks.SPAWNER_BASE -> ZOMBIE;
            case Blocks.SPAWNER_BASE + 1 -> SPIDER;
            case Blocks.SPAWNER_BASE + 2 -> SKELETON;
            default -> 0;
        };
    }

    /** Generated spawners must never reach runtime with an unspecified state. */
    public static int normalizeState(int blockId, int state) {
        if (state >= ZOMBIE && state <= CAVE_SPIDER) return state;
        return stateForCarrier(blockId);
    }

    public static MobType mobType(int state) {
        return switch (state) {
            case ZOMBIE -> MobType.ZOMBIE;
            case SPIDER -> MobType.SPIDER;
            case SKELETON -> MobType.SKELETON;
            case CAVE_SPIDER -> MobType.CAVE_SPIDER;
            default -> null;
        };
    }
}
