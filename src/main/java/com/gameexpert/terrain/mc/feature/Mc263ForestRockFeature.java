package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.util.function.Predicate;
import java.util.Set;

/**
 * Exact, inactive Minecraft Java 26.3-snapshot-7 forest-rock placed-feature leaf.
 *
 * <p>The supplied world is a live decoration-region view. In particular, its motion-blocking
 * height must observe earlier writes, and its write method may clip only at the decoration
 * region's real write boundary, never at the source-chunk boundary. No canonical generator calls
 * this class until full-pipeline promotion is reviewed.</p>
 */
public final class Mc263ForestRockFeature {
    public static final int LOCAL_MODIFICATIONS_STEP = 2;
    public static final int FOREST_ROCK_GLOBAL_INDEX = 4;
    public static final int CANDIDATE_COUNT = 2;
    public static final String FOREST_ROCK = "minecraft:forest_rock";
    public static final String MOSSY_COBBLESTONE = "minecraft:mossy_cobblestone";

    /** Pure exact output closure for forest-rock placement. */
    public static void preflight(Predicate<String> supportsState) {
        Mc263LakeFeature.requireOutputs(supportsState, MOSSY_COBBLESTONE);
    }

    private static final Set<String> FOREST_ROCK_CAN_PLACE_ON = Set.of(
            "minecraft:dirt",
            "minecraft:coarse_dirt",
            "minecraft:rooted_dirt",
            "minecraft:mud",
            "minecraft:muddy_mangrove_roots",
            "minecraft:moss_block",
            "minecraft:pale_moss_block",
            "minecraft:grass_block",
            "minecraft:podzol",
            "minecraft:mycelium",
            "minecraft:stone",
            "minecraft:granite",
            "minecraft:diorite",
            "minecraft:andesite",
            "minecraft:tuff",
            "minecraft:deepslate");
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override
        public void record(String phase, long... values) {
        }

        @Override
        public boolean enabled() {
            return false;
        }
    };

    private Mc263ForestRockFeature() {
    }

    /** Complete live view used by the placement modifiers and configured feature. */
    public interface WorldAccess {
        int minGenerationY();

        int generationDepth();

        /** Exact live {@code Heightmap.Types.MOTION_BLOCKING} height for this column. */
        int motionBlockingHeight(int blockX, int blockZ);

        String biomeKey(int blockX, int blockY, int blockZ);

        String blockState(int blockX, int blockY, int blockZ);

        /**
         * Performs the configured feature's direct write. Returns false and does nothing only when
         * the position is outside this decoration view's actual write radius.
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

        default void record(String phase, long value0) {
            if (enabled()) record(phase, new long[]{value0});
        }

        default void record(String phase, long value0, long value1) {
            if (enabled()) record(phase, new long[]{value0, value1});
        }

        default void record(String phase, long value0, long value1, long value2) {
            if (enabled()) record(phase, new long[]{value0, value1, value2});
        }

        default void record(String phase, long value0, long value1, long value2, long value3) {
            if (enabled()) record(phase, new long[]{value0, value1, value2, value3});
        }

        default void record(String phase, long value0, long value1, long value2, long value3,
                long value4) {
            if (enabled()) record(phase, new long[]{value0, value1, value2, value3, value4});
        }

        default void record(String phase, long value0, long value1, long value2, long value3,
                long value4, long value5, long value6) {
            if (enabled()) {
                record(phase, new long[]{value0, value1, value2, value3, value4, value5, value6});
            }
        }

        default void record(String phase, long value0, long value1, long value2, long value3,
                long value4, long value5, long value6, long value7) {
            if (enabled()) {
                record(phase, new long[]{value0, value1, value2, value3, value4, value5, value6,
                        value7});
            }
        }
    }

    public record BlockPos(int x, int y, int z) {
    }

    public record PlacementResult(long decorationSeed, long featureSeed, int globalIndex,
                                  int candidateCount, int placedRockCount,
                                  int attemptedBlockCount, int writtenBlockCount) {
        public PlacementResult {
            if (candidateCount < 0 || placedRockCount < 0 || placedRockCount > candidateCount
                    || attemptedBlockCount < 0 || writtenBlockCount < 0
                    || writtenBlockCount > attemptedBlockCount) {
                throw new IllegalArgumentException("invalid forest-rock placement counts");
            }
        }
    }

    public record PlacementCounts(int candidates, int rocks, int attemptedBlocks,
                                  int writtenBlocks) {
        public PlacementCounts {
            if (candidates < 0 || rocks < 0 || rocks > candidates || attemptedBlocks < 0
                    || writtenBlocks < 0 || writtenBlocks > attemptedBlocks) {
                throw new IllegalArgumentException("invalid forest-rock placement counts");
            }
        }
    }

    public record ConfiguredResult(boolean placed, int attemptedBlocks, int writtenBlocks) {
        public ConfiguredResult {
            if (attemptedBlocks < 0 || writtenBlocks < 0 || writtenBlocks > attemptedBlocks
                    || (!placed && (attemptedBlocks != 0 || writtenBlocks != 0))) {
                throw new IllegalArgumentException("invalid configured forest-rock result");
            }
        }
    }

    /** Runs the placed feature with its independently derived step-2/index-4 stream. */
    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world) {
        return place(worldSeed, sourceBlockX, sourceBlockZ, world, NO_TRACE);
    }

    /** Runs the placed feature with its independently derived step-2/index-4 stream. */
    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world, TraceSink trace) {
        requireWorld(world);
        TraceSink events = requireTrace(trace);
        Mc263DecorationRandom.FeatureRandom seeded = Mc263DecorationRandom.forFeature(worldSeed,
                sourceBlockX, sourceBlockZ, LOCAL_MODIFICATIONS_STEP, FOREST_ROCK);
        PlacementCounts counts = placeWithFeatureRandom(sourceBlockX, sourceBlockZ, world,
                seeded.random(), events);
        return new PlacementResult(seeded.decorationSeed(), seeded.featureSeed(),
                seeded.globalIndex(), counts.candidates(), counts.rocks(),
                counts.attemptedBlocks(), counts.writtenBlocks());
    }

    /**
     * Executes constant count, in-square, live heightmap, biome, and configured-feature stages on
     * one shared stream. This overload exists for stream-composition and parity probes.
     */
    public static PlacementCounts placeWithFeatureRandom(int sourceBlockX, int sourceBlockZ,
            WorldAccess world, WorldgenRandom random, TraceSink trace) {
        requireWorld(world);
        if (random == null) throw new IllegalArgumentException("random is required");
        TraceSink events = requireTrace(trace);
        int globalIndex = Mc263DecorationRandom.globalIndex(LOCAL_MODIFICATIONS_STEP, FOREST_ROCK);
        if (globalIndex != FOREST_ROCK_GLOBAL_INDEX
                || !Mc263FeatureIndexReceipt.step(LOCAL_MODIFICATIONS_STEP)
                .featureAt(globalIndex).equals(FOREST_ROCK)) {
            throw new IllegalStateException("pinned forest-rock feature index changed");
        }

        // CountPlacement.of(ConstantInt.of(2)) samples without consuming random bits.
        events.record("count", CANDIDATE_COUNT);
        int candidates = 0;
        int rocks = 0;
        int attemptedBlocks = 0;
        int writtenBlocks = 0;
        for (int attempt = 0; attempt < CANDIDATE_COUNT; attempt++) {
            int x = sourceBlockX + random.nextInt(16);
            int z = sourceBlockZ + random.nextInt(16);
            int y = world.motionBlockingHeight(x, z);
            if (y <= world.minGenerationY()) {
                events.record("heightmap_reject", attempt, x, y, z);
                continue;
            }
            candidates++;
            events.record("candidate", attempt, x, y, z);
            boolean biomeAccepted = biomeContains(world.biomeKey(x, y, z));
            events.record("biome", attempt, biomeAccepted ? 1L : 0L);
            if (!biomeAccepted) continue;

            ConfiguredResult configured = placeConfigured(random, new BlockPos(x, y, z), world,
                    events);
            if (configured.placed()) rocks++;
            attemptedBlocks += configured.attemptedBlocks();
            writtenBlocks += configured.writtenBlocks();
        }
        events.record("placed_result", FOREST_ROCK_GLOBAL_INDEX, candidates, rocks,
                writtenBlocks);
        return new PlacementCounts(candidates, rocks, attemptedBlocks, writtenBlocks);
    }

    /** Executes the configured {@code BlockBlobFeature} after modifiers select an origin. */
    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world, TraceSink trace) {
        requireWorld(world);
        if (random == null || origin == null) {
            throw new IllegalArgumentException("random and origin are required");
        }
        TraceSink events = requireTrace(trace);
        int x = origin.x();
        int y = origin.y();
        int z = origin.z();
        int startY = y;
        while (y > world.minGenerationY() + 3 && !canPlaceOn(world.blockState(x, y - 1, z))) {
            y--;
        }
        boolean descentAccepted = y > world.minGenerationY() + 3;
        events.record("descent", startY, y, descentAccepted ? 1L : 0L);
        if (!descentAccepted) {
            return new ConfiguredResult(false, 0, 0);
        }

        int attemptedBlocks = 0;
        int writtenBlocks = 0;
        for (int round = 0; round < 3; round++) {
            int xRadius = random.nextInt(2);
            int yRadius = random.nextInt(2);
            int zRadius = random.nextInt(2);
            float radius = (float) (xRadius + yRadius + zRadius) * 0.333f + 0.5f;
            float radiusSquared = radius * radius;
            events.record("blob", round, x, y, z, xRadius, yRadius, zRadius,
                    Float.floatToRawIntBits(radius));

            // BlockPos.betweenClosed order is X fastest, then Y, then Z.
            for (int dz = -zRadius; dz <= zRadius; dz++) {
                for (int dy = -yRadius; dy <= yRadius; dy++) {
                    for (int dx = -xRadius; dx <= xRadius; dx++) {
                        int distanceSquared = dx * dx + dy * dy + dz * dz;
                        if ((double) distanceSquared > (double) radiusSquared) continue;
                        int blockX = x + dx;
                        int blockY = y + dy;
                        int blockZ = z + dz;
                        attemptedBlocks++;
                        boolean written = world.trySetBlockState(blockX, blockY, blockZ,
                                MOSSY_COBBLESTONE);
                        if (written) writtenBlocks++;
                        events.record("write", round, blockX, blockY, blockZ,
                                written ? 1L : 0L);
                    }
                }
            }

            int dx = -1 + random.nextInt(2);
            int dy = -random.nextInt(2);
            int dz = -1 + random.nextInt(2);
            x += dx;
            y += dy;
            z += dz;
            events.record("shift", round, dx, dy, dz, x, y, z);
        }
        return new ConfiguredResult(true, attemptedBlocks, writtenBlocks);
    }

    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world) {
        return placeConfigured(random, origin, world, NO_TRACE);
    }

    private static boolean biomeContains(String biomeKey) {
        String biome = requireMinecraftKey(biomeKey, "biome");
        return Mc263FeatureIndexReceipt.biome(biome)
                .featuresAtStep(LOCAL_MODIFICATIONS_STEP).stream()
                .anyMatch(reference -> reference.featureKey().equals(FOREST_ROCK));
    }

    private static boolean canPlaceOn(String state) {
        return FOREST_ROCK_CAN_PLACE_ON.contains(blockKey(state));
    }

    private static void requireWorld(WorldAccess world) {
        if (world == null || world.minGenerationY() != -64 || world.generationDepth() != 384) {
            throw new IllegalArgumentException(
                    "pinned Overworld access with min Y -64 and depth 384 is required");
        }
    }

    private static TraceSink requireTrace(TraceSink trace) {
        if (trace == null) throw new IllegalArgumentException("trace sink is required");
        return trace;
    }

    private static String blockKey(String state) {
        String key = requireMinecraftKey(state, "block state");
        int properties = key.indexOf('[');
        return properties < 0 ? key : key.substring(0, properties);
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
