package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.util.function.Predicate;

/**
 * Exact, inactive Minecraft Java 26.3-snapshot-7 blue-ice placed-feature leaf.
 *
 * <p>The implementation is bound to the pinned inner server class SHA-256
 * {@code 818e19f13413bff8257ea75a73daa19362dd77f76ba9942c555afa354984721f},
 * configured-feature JSON SHA-256
 * {@code 465000d6fd3290115f5f38ee5e968fa41bdf36c04a7fa70c69be1a55c2e1a60e},
 * and placed-feature JSON SHA-256
 * {@code 7260aebdc3b50a49a0fd0ca1b8f623c492673f5206f26e86e21efe9c7d35a1ff}.
 * No canonical generator calls this class until full-pipeline promotion is reviewed.</p>
 */
public final class Mc263BlueIceFeature {
    public static final int SURFACE_STRUCTURES_STEP = 4;
    public static final int BLUE_ICE_GLOBAL_INDEX = 3;
    public static final String BLUE_ICE_FEATURE = "minecraft:blue_ice";
    public static final String BLUE_ICE = "minecraft:blue_ice";
    public static final String PACKED_ICE = "minecraft:packed_ice";
    public static final String ICE = "minecraft:ice";
    public static final String WATER = "minecraft:water";
    public static final String AIR = "minecraft:air";
    public static final String CAVE_AIR = "minecraft:cave_air";
    public static final String VOID_AIR = "minecraft:void_air";

    public static void preflight(Predicate<String> supportsState) {
        Mc263LakeFeature.requireOutputs(supportsState, BLUE_ICE);
    }
    public static final int MIN_Y = 30;
    public static final int MAX_Y = 61;
    public static final int MAX_COUNT = 19;
    public static final int GROWTH_ATTEMPTS = 200;

    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override
        public void record(String phase, long... values) {
            throw new AssertionError("disabled blue-ice trace emitted");
        }

        @Override
        public boolean enabled() {
            return false;
        }
    };
    private static final int[][] DIRECTIONS = {
            {0, -1, 0},
            {0, 1, 0},
            {0, 0, -1},
            {0, 0, 1},
            {-1, 0, 0},
            {1, 0, 0}
    };

    private Mc263BlueIceFeature() {
    }

    /** Complete live decoration-region view used by placement and configured-feature stages. */
    public interface WorldAccess {
        int minGenerationY();

        int generationDepth();

        int seaLevel();

        String biomeKey(int blockX, int blockY, int blockZ);

        String blockState(int blockX, int blockY, int blockZ);

        /**
         * Performs a direct feature write. False means the real decoration-region boundary clipped
         * the write; callers still preserve the official random stream and success result.
         */
        boolean trySetBlockState(int blockX, int blockY, int blockZ, String state);
    }

    /** Numeric event stream kept stable for direct Java/Rust parity probes. */
    @FunctionalInterface
    public interface TraceSink {
        void record(String phase, long... values);

        default boolean enabled() {
            return true;
        }

        static TraceSink disabled() {
            return NO_TRACE;
        }
    }

    public record BlockPos(int x, int y, int z) {
    }

    public record PlacementResult(long decorationSeed, long featureSeed, int globalIndex,
                                  int candidateCount, int configuredSuccessCount,
                                  int acceptedWriteCount) {
        public PlacementResult {
            if (candidateCount < 0 || configuredSuccessCount < 0
                    || configuredSuccessCount > candidateCount || acceptedWriteCount < 0) {
                throw new IllegalArgumentException("invalid blue-ice placement counts");
            }
        }
    }

    public record PlacementCounts(int candidates, int configuredSuccesses,
                                  int acceptedWrites) {
        public PlacementCounts {
            if (candidates < 0 || configuredSuccesses < 0 || configuredSuccesses > candidates
                    || acceptedWrites < 0) {
                throw new IllegalArgumentException("invalid blue-ice placement counts");
            }
        }
    }

    public record ConfiguredResult(boolean placed, int acceptedWrites) {
        public ConfiguredResult {
            if (acceptedWrites < 0 || (!placed && acceptedWrites != 0)) {
                throw new IllegalArgumentException("invalid configured blue-ice result");
            }
        }
    }

    /** Runs the placed feature with its independently derived step-4/index-3 stream. */
    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world) {
        return place(worldSeed, sourceBlockX, sourceBlockZ, world, NO_TRACE);
    }

    /** Runs the placed feature with its independently derived step-4/index-3 stream. */
    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world, TraceSink trace) {
        requireWorld(world);
        TraceSink events = requireTrace(trace);
        Mc263DecorationRandom.FeatureRandom seeded = Mc263DecorationRandom.forFeature(worldSeed,
                sourceBlockX, sourceBlockZ, SURFACE_STRUCTURES_STEP, BLUE_ICE_FEATURE);
        PlacementCounts counts = placeWithFeatureRandom(sourceBlockX, sourceBlockZ, world,
                seeded.random(), events);
        return new PlacementResult(seeded.decorationSeed(), seeded.featureSeed(),
                seeded.globalIndex(), counts.candidates(), counts.configuredSuccesses(),
                counts.acceptedWrites());
    }

    /** Executes uniform count, in-square, uniform height, biome, and configured-feature stages. */
    public static PlacementCounts placeWithFeatureRandom(int sourceBlockX, int sourceBlockZ,
            WorldAccess world, WorldgenRandom random, TraceSink trace) {
        requireWorld(world);
        if (random == null) throw new IllegalArgumentException("random is required");
        TraceSink events = requireTrace(trace);
        int globalIndex = Mc263DecorationRandom.globalIndex(SURFACE_STRUCTURES_STEP,
                BLUE_ICE_FEATURE);
        if (globalIndex != BLUE_ICE_GLOBAL_INDEX
                || !Mc263FeatureIndexReceipt.step(SURFACE_STRUCTURES_STEP)
                .featureAt(globalIndex).equals(BLUE_ICE_FEATURE)) {
            throw new IllegalStateException("pinned blue-ice feature index changed");
        }

        int count = random.nextInt(MAX_COUNT + 1);
        emit(events, "count", MAX_COUNT, count);
        int successes = 0;
        int writes = 0;
        for (int attempt = 0; attempt < count; attempt++) {
            int x = sourceBlockX + random.nextInt(16);
            int z = sourceBlockZ + random.nextInt(16);
            int y = MIN_Y + random.nextInt(MAX_Y - MIN_Y + 1);
            emit(events, "candidate", attempt, x, y, z);
            boolean biomeAccepted = biomeContains(world.biomeKey(x, y, z));
            emit(events, "biome", attempt, biomeAccepted ? 1L : 0L);
            if (!biomeAccepted) continue;

            ConfiguredResult configured = placeConfigured(random, new BlockPos(x, y, z), world,
                    events);
            if (configured.placed()) successes++;
            writes += configured.acceptedWrites();
        }
        emit(events, "placed_result", BLUE_ICE_GLOBAL_INDEX, count, successes, writes);
        return new PlacementCounts(count, successes, writes);
    }

    /** Executes the configured {@code BlueIceFeature} after modifiers select an origin. */
    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world, TraceSink trace) {
        requireWorld(world);
        if (random == null || origin == null) {
            throw new IllegalArgumentException("random and origin are required");
        }
        TraceSink events = requireTrace(trace);
        boolean seaAccepted = origin.y() <= world.seaLevel() - 1;
        emit(events, "sea_level", origin.y(), world.seaLevel(), seaAccepted ? 1L : 0L);
        if (!seaAccepted) return new ConfiguredResult(false, 0);

        boolean originWater = isBlock(world.blockState(origin.x(), origin.y(), origin.z()), WATER);
        boolean belowWater = false;
        if (!originWater) {
            belowWater = isBlock(world.blockState(origin.x(), origin.y() - 1, origin.z()), WATER);
        }
        boolean waterAccepted = originWater || belowWater;
        emit(events, "water", originWater ? 1L : 0L, belowWater ? 1L : 0L,
                waterAccepted ? 1L : 0L);
        if (!waterAccepted) return new ConfiguredResult(false, 0);

        boolean packedSeed = false;
        // Direction.DOWN is intentionally excluded from the initial packed-ice seed search.
        for (int direction = 1; direction < DIRECTIONS.length; direction++) {
            int[] delta = DIRECTIONS[direction];
            if (isBlock(world.blockState(origin.x() + delta[0], origin.y() + delta[1],
                    origin.z() + delta[2]), PACKED_ICE)) {
                packedSeed = true;
                break;
            }
        }
        emit(events, "packed_seed", packedSeed ? 1L : 0L);
        if (!packedSeed) return new ConfiguredResult(false, 0);

        int acceptedWrites = world.trySetBlockState(origin.x(), origin.y(), origin.z(), BLUE_ICE)
                ? 1 : 0;
        emit(events, "origin_write", origin.x(), origin.y(), origin.z(), acceptedWrites);
        for (int attempt = 0; attempt < GROWTH_ATTEMPTS; attempt++) {
            int dy = random.nextInt(5) - random.nextInt(6);
            int radius = 3;
            if (dy < 2) radius += dy / 2;
            if (radius < 1) {
                emit(events, "attempt", attempt, dy, radius, 0, 0,
                        origin.x(), origin.y() + dy, origin.z());
                continue;
            }

            int dx = random.nextInt(radius) - random.nextInt(radius);
            int dz = random.nextInt(radius) - random.nextInt(radius);
            int x = origin.x() + dx;
            int y = origin.y() + dy;
            int z = origin.z() + dz;
            emit(events, "attempt", attempt, dy, radius, dx, dz, x, y, z);
            String target = blockKey(world.blockState(x, y, z));
            boolean targetAccepted = isAir(target) || target.equals(WATER)
                    || target.equals(PACKED_ICE) || target.equals(ICE);
            emit(events, "target", attempt, targetId(target), targetAccepted ? 1L : 0L);
            if (!targetAccepted) continue;

            boolean adjacentBlue = false;
            for (int[] delta : DIRECTIONS) {
                if (isBlock(world.blockState(x + delta[0], y + delta[1], z + delta[2]),
                        BLUE_ICE)) {
                    adjacentBlue = true;
                    break;
                }
            }
            boolean written = adjacentBlue && world.trySetBlockState(x, y, z, BLUE_ICE);
            if (written) acceptedWrites++;
            emit(events, "grow", attempt, adjacentBlue ? 1L : 0L, written ? 1L : 0L);
        }
        return new ConfiguredResult(true, acceptedWrites);
    }

    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world) {
        return placeConfigured(random, origin, world, NO_TRACE);
    }

    private static boolean biomeContains(String biomeKey) {
        String biome = requireMinecraftKey(biomeKey, "biome");
        // BiomeFilter is fail-closed for an otherwise valid biome outside this pinned leaf's two
        // holders. The receipt assertion in placeWithFeatureRandom separately pins the index.
        return biome.equals("minecraft:frozen_ocean")
                || biome.equals("minecraft:deep_frozen_ocean");
    }

    private static int targetId(String block) {
        if (isAir(block)) return 0;
        if (block.equals(WATER)) return 1;
        if (block.equals(PACKED_ICE)) return 2;
        if (block.equals(ICE)) return 3;
        return 4;
    }

    private static boolean isBlock(String state, String block) {
        return blockKey(state).equals(block);
    }

    private static boolean isAir(String block) {
        return block.equals(AIR) || block.equals(CAVE_AIR) || block.equals(VOID_AIR);
    }

    private static String blockKey(String state) {
        String key = requireMinecraftKey(state, "block state");
        int properties = key.indexOf('[');
        return properties < 0 ? key : key.substring(0, properties);
    }

    private static void requireWorld(WorldAccess world) {
        if (world == null || world.minGenerationY() != -64 || world.generationDepth() != 384
                || world.seaLevel() != 63) {
            throw new IllegalArgumentException(
                    "pinned Overworld access with min Y -64, depth 384, and sea level 63 is required");
        }
    }

    private static TraceSink requireTrace(TraceSink trace) {
        if (trace == null) throw new IllegalArgumentException("trace sink is required");
        return trace;
    }

    private static void emit(TraceSink trace, String phase, long value0) {
        if (trace.enabled()) trace.record(phase, new long[]{value0});
    }

    private static void emit(TraceSink trace, String phase, long value0, long value1) {
        if (trace.enabled()) trace.record(phase, new long[]{value0, value1});
    }

    private static void emit(TraceSink trace, String phase, long value0, long value1,
            long value2) {
        if (trace.enabled()) trace.record(phase, new long[]{value0, value1, value2});
    }

    private static void emit(TraceSink trace, String phase, long value0, long value1,
            long value2, long value3) {
        if (trace.enabled()) trace.record(phase, new long[]{value0, value1, value2, value3});
    }

    private static void emit(TraceSink trace, String phase, long value0, long value1,
            long value2, long value3, long value4, long value5, long value6, long value7) {
        if (trace.enabled()) {
            trace.record(phase, new long[]{value0, value1, value2, value3, value4, value5,
                    value6, value7});
        }
    }

    private static String requireMinecraftKey(String key, String description) {
        if (key == null || !key.startsWith("minecraft:")
                || key.length() == "minecraft:".length()) {
            throw new IllegalArgumentException("exact minecraft namespaced " + description
                    + " is required: " + key);
        }
        return key;
    }
}
