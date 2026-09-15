package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.util.function.Predicate;
import java.util.List;
import java.util.Set;

/**
 * Exact, inactive Minecraft Java 26.3-snapshot-7 ice-spike placed-feature leaf.
 *
 * <p>The implementation is bound to the pinned remapped inner-server class SHA-256
 * {@code 979ee3f7efaa505282a859abe179885b65cd8560ae0c436391249ae91b2527c2},
 * configured-feature JSON SHA-256
 * {@code 0953d48e48904f3901339d6cba4d182030e245ebf4187f90d8cc3f0f2962e8da},
 * placed-feature JSON SHA-256
 * {@code 471bc18434b9d9b8b1b0a5858ef8909f6372b6730d19f2d1d5f7f93470d910fd},
 * and expanded block-tag JSON SHA-256
 * {@code 5da056adf11b03c033541fc4cbb69ce878bfa31c48d54a6e88656f6b7284e841}.
 * The supplied world is a live decoration-region view, and writes may be clipped only at that
 * view's real boundary. No canonical generator calls this class until full-pipeline promotion is
 * reviewed.</p>
 */
public final class Mc263IceSpikeFeature {
    public static final int SURFACE_STRUCTURES_STEP = 4;
    public static final int ICE_SPIKE_GLOBAL_INDEX = 0;
    public static final int CANDIDATE_COUNT = 3;
    public static final String ICE_SPIKE = "minecraft:ice_spike";
    public static final String PACKED_ICE = "minecraft:packed_ice";
    public static final String SNOW_BLOCK = "minecraft:snow_block";

    public static void preflight(Predicate<String> supportsState) {
        Mc263LakeFeature.requireOutputs(supportsState, PACKED_ICE);
    }

    public static final String CLASS_SHA256 =
            "979ee3f7efaa505282a859abe179885b65cd8560ae0c436391249ae91b2527c2";
    public static final String CONFIGURED_FEATURE_SHA256 =
            "0953d48e48904f3901339d6cba4d182030e245ebf4187f90d8cc3f0f2962e8da";
    public static final String PLACED_FEATURE_SHA256 =
            "471bc18434b9d9b8b1b0a5858ef8909f6372b6730d19f2d1d5f7f93470d910fd";
    public static final String REPLACEABLE_TAG_SHA256 =
            "5da056adf11b03c033541fc4cbb69ce878bfa31c48d54a6e88656f6b7284e841";

    private static final List<String> ICE_SPIKE_REPLACEABLE_ORDER = List.of(
            "minecraft:coarse_dirt",
            "minecraft:dirt",
            "minecraft:grass_block",
            "minecraft:ice",
            "minecraft:mud",
            "minecraft:muddy_mangrove_roots",
            "minecraft:moss_block",
            "minecraft:mycelium",
            "minecraft:pale_moss_block",
            "minecraft:podzol",
            "minecraft:rooted_dirt",
            SNOW_BLOCK);
    private static final Set<String> ICE_SPIKE_REPLACEABLE =
            Set.copyOf(ICE_SPIKE_REPLACEABLE_ORDER);
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override
        public void record(String phase, long... values) {
        }

        @Override
        public boolean enabled() {
            return false;
        }
    };

    private Mc263IceSpikeFeature() {
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
         * Performs the configured feature's direct write. False means only that the position lies
         * beyond this decoration view's actual write boundary.
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
                long value4, long value5) {
            if (enabled()) {
                record(phase, new long[]{value0, value1, value2, value3, value4, value5});
            }
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
                                  int candidateCount, int configuredSuccessCount,
                                  int attemptedWriteCount, int acceptedWriteCount) {
        public PlacementResult {
            requireCounts(candidateCount, configuredSuccessCount, attemptedWriteCount,
                    acceptedWriteCount);
        }
    }

    public record PlacementCounts(int candidates, int configuredSuccesses,
                                  int attemptedWrites, int acceptedWrites) {
        public PlacementCounts {
            requireCounts(candidates, configuredSuccesses, attemptedWrites, acceptedWrites);
        }
    }

    public record ConfiguredResult(boolean placed, int attemptedWrites, int acceptedWrites) {
        public ConfiguredResult {
            if (attemptedWrites < 0 || acceptedWrites < 0 || acceptedWrites > attemptedWrites
                    || (!placed && (attemptedWrites != 0 || acceptedWrites != 0))) {
                throw new IllegalArgumentException("invalid ice-spike configured counts");
            }
        }
    }

    /** Runs the placed feature with its independently derived step-4/index-0 stream. */
    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world) {
        return place(worldSeed, sourceBlockX, sourceBlockZ, world, NO_TRACE);
    }

    /** Runs the placed feature with its independently derived step-4/index-0 stream. */
    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world, TraceSink trace) {
        requireWorld(world);
        TraceSink events = requireTrace(trace);
        Mc263DecorationRandom.FeatureRandom seeded = Mc263DecorationRandom.forFeature(worldSeed,
                sourceBlockX, sourceBlockZ, SURFACE_STRUCTURES_STEP, ICE_SPIKE);
        PlacementCounts counts = placeWithFeatureRandom(sourceBlockX, sourceBlockZ, world,
                seeded.random(), events);
        return new PlacementResult(seeded.decorationSeed(), seeded.featureSeed(),
                seeded.globalIndex(), counts.candidates(), counts.configuredSuccesses(),
                counts.attemptedWrites(), counts.acceptedWrites());
    }

    /** Executes constant count, in-square, live heightmap, biome, and configured stages. */
    public static PlacementCounts placeWithFeatureRandom(int sourceBlockX, int sourceBlockZ,
            WorldAccess world, WorldgenRandom random, TraceSink trace) {
        requireWorld(world);
        if (random == null) throw new IllegalArgumentException("random is required");
        TraceSink events = requireTrace(trace);
        int globalIndex = Mc263DecorationRandom.globalIndex(SURFACE_STRUCTURES_STEP, ICE_SPIKE);
        if (globalIndex != ICE_SPIKE_GLOBAL_INDEX
                || !Mc263FeatureIndexReceipt.step(SURFACE_STRUCTURES_STEP)
                .featureAt(globalIndex).equals(ICE_SPIKE)) {
            throw new IllegalStateException("pinned ice-spike feature index changed");
        }

        // CountPlacement.of(ConstantInt.of(3)) samples without consuming random bits.
        events.record("count", CANDIDATE_COUNT);
        int candidates = 0;
        int successes = 0;
        int attemptedWrites = 0;
        int acceptedWrites = 0;
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
            if (configured.placed()) successes++;
            attemptedWrites += configured.attemptedWrites();
            acceptedWrites += configured.acceptedWrites();
        }
        events.record("placed_result", ICE_SPIKE_GLOBAL_INDEX, candidates, successes,
                attemptedWrites, acceptedWrites);
        return new PlacementCounts(candidates, successes, attemptedWrites, acceptedWrites);
    }

    /** Executes the configured {@code SpikeFeature} after modifiers select an origin. */
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
        String support = world.blockState(x, y, z);
        while (isAir(support) && y > world.minGenerationY() + 2) {
            y--;
            support = world.blockState(x, y, z);
        }
        boolean supportAccepted = blockKey(support).equals(SNOW_BLOCK);
        events.record("descent", startY, y, supportAccepted ? 1L : 0L);
        if (!supportAccepted) return new ConfiguredResult(false, 0, 0);

        int baseLift = random.nextInt(4);
        y += baseLift;
        int height = random.nextInt(4) + 7;
        int width = height / 4 + random.nextInt(2);
        int tallGate = -1;
        int tallLift = 0;
        if (width > 1) {
            tallGate = random.nextInt(60);
            if (tallGate == 0) {
                tallLift = 10 + random.nextInt(30);
                y += tallLift;
            }
        }
        events.record("shape", x, y, z, baseLift, height, width, tallGate, tallLift);

        MutableCounts counts = new MutableCounts();
        for (int yOff = 0; yOff < height; yOff++) {
            float scale = (1.0f - (float) yOff / (float) height) * (float) width;
            int newWidth = (int) Math.ceil(scale);
            events.record("layer", yOff, Float.floatToRawIntBits(scale), newWidth);
            for (int xo = -newWidth; xo <= newWidth; xo++) {
                float dx = (float) Math.abs(xo) - 0.25f;
                for (int zo = -newWidth; zo <= newWidth; zo++) {
                    float dz = (float) Math.abs(zo) - 0.25f;
                    if ((xo != 0 || zo != 0) && dx * dx + dz * dz > scale * scale) continue;
                    boolean boundary = xo == -newWidth || xo == newWidth
                            || zo == -newWidth || zo == newWidth;
                    if (boundary) {
                        float gate = random.nextFloat();
                        boolean skipped = gate > 0.75f;
                        events.record("boundary", yOff, xo, zo,
                                Float.floatToRawIntBits(gate), skipped ? 1L : 0L);
                        if (skipped) continue;
                    }

                    attemptReplaceableWrite(world, events, "positive", x + xo, y + yOff,
                            z + zo, counts);
                    if (yOff != 0 && newWidth > 1) {
                        attemptReplaceableWrite(world, events, "negative", x + xo, y - yOff,
                                z + zo, counts);
                    }
                }
            }
        }

        int pillarWidth = Math.max(0, Math.min(1, width - 1));
        for (int xo = -pillarWidth; xo <= pillarWidth; xo++) {
            for (int zo = -pillarWidth; zo <= pillarWidth; zo++) {
                int cursorY = y - 1;
                int runLength = 50;
                if (Math.abs(xo) == 1 && Math.abs(zo) == 1) {
                    runLength = random.nextInt(5);
                }
                events.record("root_start", xo, zo, cursorY, runLength);
                while (cursorY > 50) {
                    String state = blockKey(world.blockState(x + xo, cursorY, z + zo));
                    boolean eligible = isAirKey(state) || ICE_SPIKE_REPLACEABLE.contains(state)
                            || state.equals(PACKED_ICE);
                    if (!eligible) {
                        events.record("root_stop", xo, zo, cursorY, blockId(state));
                        break;
                    }
                    counts.attempted++;
                    boolean written = world.trySetBlockState(x + xo, cursorY, z + zo, PACKED_ICE);
                    if (written) counts.accepted++;
                    events.record("root", xo, zo, cursorY, blockId(state), written ? 1L : 0L,
                            runLength);
                    cursorY--;
                    runLength--;
                    if (runLength <= 0) {
                        int gap = random.nextInt(5) + 1;
                        cursorY -= gap;
                        runLength = random.nextInt(5);
                        events.record("root_gap", xo, zo, cursorY, gap, runLength);
                    }
                }
            }
        }
        events.record("configured_result", counts.attempted, counts.accepted);
        return new ConfiguredResult(true, counts.attempted, counts.accepted);
    }

    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world) {
        return placeConfigured(random, origin, world, NO_TRACE);
    }

    private static void attemptReplaceableWrite(WorldAccess world, TraceSink events, String phase,
            int x, int y, int z, MutableCounts counts) {
        String state = blockKey(world.blockState(x, y, z));
        boolean eligible = isAirKey(state) || ICE_SPIKE_REPLACEABLE.contains(state);
        boolean written = false;
        if (eligible) {
            counts.attempted++;
            written = world.trySetBlockState(x, y, z, PACKED_ICE);
            if (written) counts.accepted++;
        }
        events.record(phase, x, y, z, blockId(state), eligible ? 1L : 0L,
                written ? 1L : 0L);
    }

    private static boolean biomeContains(String biomeKey) {
        String biome = requireMinecraftKey(biomeKey, "biome");
        return Mc263FeatureIndexReceipt.biome(biome)
                .featuresAtStep(SURFACE_STRUCTURES_STEP).stream()
                .anyMatch(reference -> reference.featureKey().equals(ICE_SPIKE));
    }

    private static int blockId(String block) {
        if (isAirKey(block)) return 0;
        if (block.equals(PACKED_ICE)) return 1;
        int index = ICE_SPIKE_REPLACEABLE_ORDER.indexOf(block);
        if (index >= 0) return index + 2;
        return 14;
    }

    private static boolean isAir(String state) {
        return isAirKey(blockKey(state));
    }

    private static boolean isAirKey(String block) {
        return block.equals("minecraft:air") || block.equals("minecraft:cave_air")
                || block.equals("minecraft:void_air");
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

    private static void requireCounts(int candidates, int successes, int attempted, int accepted) {
        if (candidates < 0 || successes < 0 || successes > candidates || attempted < 0
                || accepted < 0 || accepted > attempted) {
            throw new IllegalArgumentException("invalid ice-spike placement counts");
        }
    }

    private static final class MutableCounts {
        private int attempted;
        private int accepted;
    }
}
