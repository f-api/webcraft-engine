package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.util.function.Predicate;

/**
 * Exact, inactive Minecraft Java 26.3-snapshot-7 shared lava-lake configured feature and placed
 * wrappers.
 *
 * <p>The supplied world is the complete decoration region. Bounded writes and sidecars silently
 * ignore positions outside that region, matching the boolean/no-op boundary of the worldgen level.
 * No canonical generator calls this class until full-pipeline promotion is reviewed.</p>
 */
public final class Mc263LakeFeature {
    public static final int LAKES_STEP = 1;
    public static final String LAKE_LAVA_UNDERGROUND = "minecraft:lake_lava_underground";
    public static final String LAKE_LAVA_SURFACE = "minecraft:lake_lava_surface";
    public static final String LAKE_LAVA = "minecraft:lake_lava";

    public static final String AIR_TAG = "minecraft:air";
    public static final String FEATURES_CANNOT_REPLACE_TAG =
            "minecraft:features_cannot_replace";
    public static final String LAVA_POOL_STONE_CANNOT_REPLACE_TAG =
            "minecraft:lava_pool_stone_cannot_replace";

    public static final String INNER_SERVER_SHA1 =
            "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String LAKE_FEATURE_CLASS_SHA256 =
            "f17d2df5a3152e6dfb4ed18662c24c7950cb0a0617516088e89acb0d6d152a69";
    public static final String CONFIGURED_FEATURE_SHA256 =
            "1d5d9a62b2eea1261f6d81a6e2849187e0fec774f2151b1ff62a7a8f3c67db7a";
    public static final String SURFACE_PLACED_FEATURE_SHA256 =
            "2946f538beae9c78321d0e906309067131e248d432a83ef70356bf7ec3e8a62f";
    public static final String UNDERGROUND_PLACED_FEATURE_SHA256 =
            "45a01cbbbff19bd82c941c633fe6d30792c86b51c98491319f7810bc54345d69";
    public static final String FEATURES_CANNOT_REPLACE_TAG_SHA256 =
            "a117e03f857eacf14ff5862ba6f885bb02b476a844b683c2be61211202a9a10d";
    public static final String LAVA_POOL_STONE_CANNOT_REPLACE_TAG_SHA256 =
            "5b276c48e550fd769a8137a42f7dadf5e15625c336c7def6e07379c60a27bc92";
    public static final String AIR_TAG_SHA256 =
            "8291f3984418fa56cd1ca3f5132344de25cee407647b0f97d6008bc0f3d47d1b";

    private static final int WIDTH = 16;
    private static final int HEIGHT = 8;
    private static final int MASK_SIZE = WIDTH * WIDTH * HEIGHT;
    private static final String CAVE_AIR = "minecraft:cave_air";
    private static final String LAVA = "minecraft:lava";
    private static final String STONE = "minecraft:stone";
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override
        public void record(String phase, long... values) {
        }

        @Override
        public boolean enabled() {
            return false;
        }
    };

    private Mc263LakeFeature() {
    }

    /** Pure exact output/sidecar closure for both placed lava-lake wrappers. */
    public static void preflight(Predicate<String> supportsState, boolean supportsTicks,
            boolean supportsPostprocessing) {
        requireOutputs(supportsState, CAVE_AIR, LAVA, STONE, "minecraft:ice");
        if (!supportsTicks) throw new UnsupportedOperationException("lake block ticks");
        if (!supportsPostprocessing) {
            throw new UnsupportedOperationException("lake postprocessing");
        }
    }

    public static void requireOutputs(Predicate<String> supportsState, String... states) {
        if (supportsState == null) throw new IllegalArgumentException("state support is required");
        for (String state : states) {
            if (!supportsState.test(state)) throw new UnsupportedOperationException(state);
        }
    }

    /** Complete mutable view required by the configured feature and both placed wrappers. */
    public interface WorldAccess {
        int minGenerationY();

        int generationDepth();

        int worldSurfaceWg(int blockX, int blockZ);

        int oceanFloorWg(int blockX, int blockZ);

        String biomeKey(int blockX, int blockY, int blockZ);

        String blockState(int blockX, int blockY, int blockZ);

        boolean isSolid(int blockX, int blockY, int blockZ);

        boolean isLiquid(int blockX, int blockY, int blockZ);

        /** True only when the current state exactly equals the configured source-lava state. */
        boolean isExactSourceLava(int blockX, int blockY, int blockZ);

        boolean isInBlockTag(int blockX, int blockY, int blockZ, String tagKey);

        /**
         * Mirrors the bounded worldgen-level set operation. Outside-region or outside-height writes
         * are no-ops and return false; an implementation must never clip them into another cell.
         */
        boolean trySetBlockState(int blockX, int blockY, int blockZ, String state);

        /** Records a bounded block tick; invalid positions are silent no-ops. */
        void scheduleBlockTick(int blockX, int blockY, int blockZ, String block, int delay);

        /** Records a bounded postprocessing mark; invalid positions are silent no-ops. */
        void markForPostprocessing(int blockX, int blockY, int blockZ);

        default boolean isOutsideBuildHeight(int blockY) {
            return blockY < minGenerationY()
                    || blockY >= minGenerationY() + generationDepth();
        }
    }

    /** Shared, package-level contract for exact configured lakes with different fluids/barriers. */
    interface LakeWorldAccess {
        int minGenerationY();

        String blockState(int blockX, int blockY, int blockZ);

        boolean isSolid(int blockX, int blockY, int blockZ);

        boolean isLiquid(int blockX, int blockY, int blockZ);

        boolean isExactConfiguredFluid(int blockX, int blockY, int blockZ);

        boolean isInBlockTag(int blockX, int blockY, int blockZ, String tagKey);

        boolean trySetBlockState(int blockX, int blockY, int blockZ, String state);

        void scheduleBlockTick(int blockX, int blockY, int blockZ, String block, int delay);

        void markForPostprocessing(int blockX, int blockY, int blockZ);

        boolean shouldFreeze(int blockX, int blockY, int blockZ);
    }

    record LakeConfiguration(String fluidState, String barrierState,
                             String forbiddenShellBlock, boolean waterFluid) {
        LakeConfiguration {
            if (fluidState == null || fluidState.isBlank()
                    || barrierState == null || barrierState.isBlank()) {
                throw new IllegalArgumentException("lake states are required");
            }
        }
    }

    record LakeKernelResult(boolean placed, int airWrites, int fluidWrites,
                            int barrierWrites, int freezeWrites) {
        LakeKernelResult {
            if (airWrites < 0 || fluidWrites < 0 || barrierWrites < 0 || freezeWrites < 0
                    || (!placed && (airWrites != 0 || fluidWrites != 0
                    || barrierWrites != 0 || freezeWrites != 0))) {
                throw new IllegalArgumentException("invalid configured lake result");
            }
        }
    }

    /** Numeric event stream kept stable for direct Java/Rust parity probes. */
    @FunctionalInterface
    public interface TraceSink {
        void record(String phase, long... values);

        default boolean enabled() {
            return true;
        }
    }

    static void emit(TraceSink sink, String phase, long first) {
        if (sink.enabled()) sink.record(phase, first);
    }

    static void emit(TraceSink sink, String phase, long first, long second) {
        if (sink.enabled()) sink.record(phase, first, second);
    }

    static void emit(TraceSink sink, String phase, long first, long second, long third) {
        if (sink.enabled()) sink.record(phase, first, second, third);
    }

    static void emit(TraceSink sink, String phase, long first, long second, long third,
            long fourth) {
        if (sink.enabled()) sink.record(phase, first, second, third, fourth);
    }

    static void emit(TraceSink sink, String phase, long first, long second, long third,
            long fourth, long fifth) {
        if (sink.enabled()) sink.record(phase, first, second, third, fourth, fifth);
    }

    static void emit(TraceSink sink, String phase, long first, long second, long third,
            long fourth, long fifth, long sixth) {
        if (sink.enabled()) sink.record(phase, first, second, third, fourth, fifth, sixth);
    }

    static void emit(TraceSink sink, String phase, long first, long second, long third,
            long fourth, long fifth, long sixth, long seventh) {
        if (sink.enabled()) {
            sink.record(phase, first, second, third, fourth, fifth, sixth, seventh);
        }
    }

    public record BlockPos(int x, int y, int z) {
    }

    public record PlacementResult(long decorationSeed, long featureSeed, int globalIndex,
                                  int candidateCount, int placedLakeCount) {
        public PlacementResult {
            if (candidateCount < 0 || candidateCount > 1
                    || placedLakeCount < 0 || placedLakeCount > candidateCount) {
                throw new IllegalArgumentException("invalid lava-lake placement counts");
            }
        }
    }

    public record PlacementCounts(int candidates, int lakes) {
        public PlacementCounts {
            if (candidates < 0 || candidates > 1 || lakes < 0 || lakes > candidates) {
                throw new IllegalArgumentException("invalid lava-lake placement counts");
            }
        }
    }

    /** Runs one placed feature with its independently derived feature stream. */
    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            String placedFeatureKey, WorldAccess world) {
        return place(worldSeed, sourceBlockX, sourceBlockZ, placedFeatureKey, world, NO_TRACE);
    }

    /** Runs one placed feature with its independently derived feature stream. */
    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            String placedFeatureKey, WorldAccess world, TraceSink trace) {
        requireWorld(world);
        TraceSink events = requireTrace(trace);
        PlacedSpec spec = placedSpec(placedFeatureKey);
        Mc263DecorationRandom.FeatureRandom seeded = Mc263DecorationRandom.forFeature(
                worldSeed, sourceBlockX, sourceBlockZ, LAKES_STEP, spec.key());
        PlacementCounts counts = placeWithFeatureRandom(spec.key(), sourceBlockX, sourceBlockZ,
                world, seeded.random(), events);
        return new PlacementResult(seeded.decorationSeed(), seeded.featureSeed(),
                seeded.globalIndex(), counts.candidates(), counts.lakes());
    }

    /** Executes every placed modifier and the configured feature on one shared random stream. */
    public static PlacementCounts placeWithFeatureRandom(String placedFeatureKey,
            int sourceBlockX, int sourceBlockZ, WorldAccess world, WorldgenRandom random,
            TraceSink trace) {
        requireWorld(world);
        if (random == null) throw new IllegalArgumentException("random is required");
        TraceSink events = requireTrace(trace);
        PlacedSpec spec = placedSpec(placedFeatureKey);
        int globalIndex = Mc263DecorationRandom.globalIndex(LAKES_STEP, spec.key());
        if (globalIndex != spec.globalIndex()
                || !Mc263FeatureIndexReceipt.step(LAKES_STEP)
                .featureAt(globalIndex).equals(spec.key())) {
            throw new IllegalStateException("pinned lava-lake feature index changed");
        }

        boolean rarityAccepted = random.nextFloat() < 1.0f / (float) spec.rarity();
        emit(events, "rarity", spec.rarity(), rarityAccepted ? 1L : 0L);
        if (!rarityAccepted) return new PlacementCounts(0, 0);

        int x = sourceBlockX + random.nextInt(WIDTH);
        int z = sourceBlockZ + random.nextInt(WIDTH);
        int y;
        if (spec.surface()) {
            y = world.worldSurfaceWg(x, z);
            emit(events, "surface_height", x, y, z);
            if (y <= world.minGenerationY()) {
                emit(events, "heightmap_reject", x, y, z);
                return new PlacementCounts(1, 0);
            }
        } else {
            y = random.nextInt(320);
            emit(events, "uniform_height", x, y, z);
            int scannedY = scanDown(world, x, y, z, events);
            if (scannedY == Integer.MIN_VALUE) return new PlacementCounts(1, 0);
            y = scannedY;
            int threshold = world.oceanFloorWg(x, z) - 5;
            boolean thresholdAccepted = y <= threshold;
            emit(events, "surface_threshold", x, y, z, threshold,
                    thresholdAccepted ? 1L : 0L);
            if (!thresholdAccepted) return new PlacementCounts(1, 0);
        }

        boolean biomeAccepted = biomeContains(world.biomeKey(x, y, z), spec.key());
        emit(events, "biome", x, y, z, biomeAccepted ? 1L : 0L);
        if (!biomeAccepted) return new PlacementCounts(1, 0);

        boolean placed = placeConfigured(random, new BlockPos(x, y, z), world, events);
        emit(events, "placed_result", spec.globalIndex(), placed ? 1L : 0L);
        return new PlacementCounts(1, placed ? 1 : 0);
    }

    /** Executes the pinned {@code LakeFeature} after placement modifiers select an origin. */
    public static boolean placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world) {
        return placeConfigured(random, origin, world, NO_TRACE);
    }

    /** Executes the pinned {@code LakeFeature} after placement modifiers select an origin. */
    public static boolean placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world, TraceSink trace) {
        requireWorld(world);
        if (random == null || origin == null) {
            throw new IllegalArgumentException("random and origin are required");
        }
        LakeWorldAccess lakeWorld = new LakeWorldAccess() {
            @Override
            public int minGenerationY() {
                return world.minGenerationY();
            }

            @Override
            public String blockState(int blockX, int blockY, int blockZ) {
                return world.blockState(blockX, blockY, blockZ);
            }

            @Override
            public boolean isSolid(int blockX, int blockY, int blockZ) {
                return world.isSolid(blockX, blockY, blockZ);
            }

            @Override
            public boolean isLiquid(int blockX, int blockY, int blockZ) {
                return world.isLiquid(blockX, blockY, blockZ);
            }

            @Override
            public boolean isExactConfiguredFluid(int blockX, int blockY, int blockZ) {
                return world.isExactSourceLava(blockX, blockY, blockZ);
            }

            @Override
            public boolean isInBlockTag(int blockX, int blockY, int blockZ, String tagKey) {
                return world.isInBlockTag(blockX, blockY, blockZ, tagKey);
            }

            @Override
            public boolean trySetBlockState(int blockX, int blockY, int blockZ, String state) {
                return world.trySetBlockState(blockX, blockY, blockZ, state);
            }

            @Override
            public void scheduleBlockTick(int blockX, int blockY, int blockZ,
                    String block, int delay) {
                world.scheduleBlockTick(blockX, blockY, blockZ, block, delay);
            }

            @Override
            public void markForPostprocessing(int blockX, int blockY, int blockZ) {
                world.markForPostprocessing(blockX, blockY, blockZ);
            }

            @Override
            public boolean shouldFreeze(int blockX, int blockY, int blockZ) {
                return false;
            }
        };
        return placeConfiguredKernel(random, origin, lakeWorld,
                new LakeConfiguration(LAVA, STONE, null, false), requireTrace(trace)).placed();
    }

    static LakeKernelResult placeConfiguredKernel(WorldgenRandom random, BlockPos origin,
            LakeWorldAccess world, LakeConfiguration configuration, TraceSink events) {
        if (origin.y() <= world.minGenerationY() + 4) {
            emit(events, "origin_reject", origin.x(), origin.y(), origin.z());
            return new LakeKernelResult(false, 0, 0, 0, 0);
        }

        BlockPos base = new BlockPos(origin.x() - 8, origin.y() - 4, origin.z() - 8);
        boolean[] mask = new boolean[MASK_SIZE];
        int ellipsoidCount = random.nextInt(4) + 4;
        emit(events, "shape", base.x(), base.y(), base.z(), ellipsoidCount);
        for (int ellipsoid = 0; ellipsoid < ellipsoidCount; ellipsoid++) {
            double xRadius = random.nextDouble() * 6.0 + 3.0;
            double yRadius = random.nextDouble() * 4.0 + 2.0;
            double zRadius = random.nextDouble() * 6.0 + 3.0;
            double xCenter = random.nextDouble() * (16.0 - xRadius - 2.0)
                    + 1.0 + xRadius / 2.0;
            double yCenter = random.nextDouble() * (8.0 - yRadius - 4.0)
                    + 2.0 + yRadius / 2.0;
            double zCenter = random.nextDouble() * (16.0 - zRadius - 2.0)
                    + 1.0 + zRadius / 2.0;
            emit(events, "ellipsoid", ellipsoid,
                    Double.doubleToRawLongBits(xRadius), Double.doubleToRawLongBits(yRadius),
                    Double.doubleToRawLongBits(zRadius), Double.doubleToRawLongBits(xCenter),
                    Double.doubleToRawLongBits(yCenter), Double.doubleToRawLongBits(zCenter));

            for (int x = 1; x < 15; x++) {
                for (int z = 1; z < 15; z++) {
                    for (int y = 1; y < 7; y++) {
                        double xDistance = ((double) x - xCenter) / (xRadius / 2.0);
                        double yDistance = ((double) y - yCenter) / (yRadius / 2.0);
                        double zDistance = ((double) z - zCenter) / (zRadius / 2.0);
                        double distance = xDistance * xDistance + yDistance * yDistance
                                + zDistance * zDistance;
                        if (distance < 1.0) mask[index(x, y, z)] = true;
                    }
                }
            }
        }

        // SimpleStateProvider(lava) consumes no random values. Validate the complete shell first.
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < WIDTH; z++) {
                for (int y = 0; y < HEIGHT; y++) {
                    if (!isBoundary(mask, x, y, z)) continue;
                    int blockX = base.x() + x;
                    int blockY = base.y() + y;
                    int blockZ = base.z() + z;
                    if (y >= 4 && world.isLiquid(blockX, blockY, blockZ)) {
                        emit(events, "shell_reject", blockX, blockY, blockZ, 1L);
                        return new LakeKernelResult(false, 0, 0, 0, 0);
                    }
                    if (y < 4 && !world.isSolid(blockX, blockY, blockZ)
                            && !world.isExactConfiguredFluid(blockX, blockY, blockZ)) {
                        emit(events, "shell_reject", blockX, blockY, blockZ, 2L);
                        return new LakeKernelResult(false, 0, 0, 0, 0);
                    }
                    if (configuration.forbiddenShellBlock() != null
                            && blockKey(world.blockState(blockX, blockY, blockZ))
                            .equals(configuration.forbiddenShellBlock())) {
                        emit(events, "shell_reject", blockX, blockY, blockZ, 3L);
                        return new LakeKernelResult(false, 0, 0, 0, 0);
                    }
                }
            }
        }

        int airWrites = 0;
        int lavaWrites = 0;
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < WIDTH; z++) {
                for (int y = 0; y < HEIGHT; y++) {
                    if (!mask[index(x, y, z)]) continue;
                    int blockX = base.x() + x;
                    int blockY = base.y() + y;
                    int blockZ = base.z() + z;
                    if (world.isInBlockTag(blockX, blockY, blockZ,
                            FEATURES_CANNOT_REPLACE_TAG)) {
                        emit(events, "protected_skip", blockX, blockY, blockZ,
                                y >= 4 ? 0L : 1L);
                        continue;
                    }
                    boolean placeAir = y >= 4;
                    world.trySetBlockState(blockX, blockY, blockZ,
                            placeAir ? CAVE_AIR : configuration.fluidState());
                    emit(events, "fill", blockX, blockY, blockZ, placeAir ? 0L : 1L);
                    if (placeAir) {
                        airWrites++;
                        world.scheduleBlockTick(blockX, blockY, blockZ, CAVE_AIR, 0);
                        markAboveForPostProcessing(world, blockX, blockY, blockZ);
                    } else {
                        lavaWrites++;
                    }
                }
            }
        }

        // SimpleStateProvider(stone) consumes no random values. Upper boundary cells must consume
        // nextInt(2) before their state, solidity, or tag predicates are consulted.
        int barriers = 0;
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < WIDTH; z++) {
                for (int y = 0; y < HEIGHT; y++) {
                    if (!isBoundary(mask, x, y, z)) continue;
                    int blockX = base.x() + x;
                    int blockY = base.y() + y;
                    int blockZ = base.z() + z;
                    if (y >= 4) {
                        int upperGate = random.nextInt(2);
                        emit(events, "barrier_upper_gate", blockX, blockY, blockZ, upperGate);
                        if (upperGate == 0) continue;
                    }
                    if (!world.isSolid(blockX, blockY, blockZ)
                            || world.isInBlockTag(blockX, blockY, blockZ,
                            LAVA_POOL_STONE_CANNOT_REPLACE_TAG)) {
                        continue;
                    }
                    world.trySetBlockState(blockX, blockY, blockZ,
                            configuration.barrierState());
                    markAboveForPostProcessing(world, blockX, blockY, blockZ);
                    emit(events, "barrier", blockX, blockY, blockZ);
                    barriers++;
                }
            }
        }
        int freezeWrites = 0;
        if (configuration.waterFluid()) {
            for (int x = 0; x < WIDTH; x++) {
                for (int z = 0; z < WIDTH; z++) {
                    int blockX = base.x() + x;
                    int blockY = base.y() + 4;
                    int blockZ = base.z() + z;
                    if (!world.shouldFreeze(blockX, blockY, blockZ)
                            || world.isInBlockTag(blockX, blockY, blockZ,
                            FEATURES_CANNOT_REPLACE_TAG)) {
                        continue;
                    }
                    world.trySetBlockState(blockX, blockY, blockZ, "minecraft:ice");
                    emit(events, "freeze", blockX, blockY, blockZ);
                    freezeWrites++;
                }
            }
        }
        emit(events, "configured_result", airWrites, lavaWrites, barriers);
        return new LakeKernelResult(true, airWrites, lavaWrites, barriers, freezeWrites);
    }

    private static int scanDown(WorldAccess world, int x, int startY, int z, TraceSink trace) {
        int y = startY;
        for (int step = 0; step < 32; step++) {
            if (isUndergroundScanTarget(world, x, y, z)) {
                emit(trace, "environment_scan", x, startY, z, y, step, 1L);
                return y;
            }
            y--;
            if (world.isOutsideBuildHeight(y)) {
                emit(trace, "environment_scan", x, startY, z, y, step + 1L, 0L);
                return Integer.MIN_VALUE;
            }
        }
        boolean accepted = isUndergroundScanTarget(world, x, y, z);
        emit(trace, "environment_scan", x, startY, z, y, 32L, accepted ? 1L : 0L);
        return accepted ? y : Integer.MIN_VALUE;
    }

    private static boolean isUndergroundScanTarget(WorldAccess world, int x, int y, int z) {
        return !world.isInBlockTag(x, y, z, AIR_TAG)
                && !world.isOutsideBuildHeight(y - 5);
    }

    private static void markAboveForPostProcessing(LakeWorldAccess world, int x, int y, int z) {
        for (int offset = 1; offset <= 2; offset++) {
            int aboveY = y + offset;
            if (world.isInBlockTag(x, aboveY, z, AIR_TAG)) return;
            world.markForPostprocessing(x, aboveY, z);
        }
    }

    private static boolean isBoundary(boolean[] mask, int x, int y, int z) {
        return !mask[index(x, y, z)]
                && (x < 15 && mask[index(x + 1, y, z)]
                || x > 0 && mask[index(x - 1, y, z)]
                || z < 15 && mask[index(x, y, z + 1)]
                || z > 0 && mask[index(x, y, z - 1)]
                || y < 7 && mask[index(x, y + 1, z)]
                || y > 0 && mask[index(x, y - 1, z)]);
    }

    private static int index(int x, int y, int z) {
        return (x * WIDTH + z) * HEIGHT + y;
    }

    private static String blockKey(String state) {
        int properties = state.indexOf('[');
        return properties < 0 ? state : state.substring(0, properties);
    }

    private static boolean biomeContains(String biomeKey, String featureKey) {
        String biome = requireMinecraftKey(biomeKey, "biome key");
        return Mc263FeatureIndexReceipt.biome(biome).featuresAtStep(LAKES_STEP).stream()
                .anyMatch(reference -> reference.featureKey().equals(featureKey));
    }

    private static PlacedSpec placedSpec(String featureKey) {
        String key = requireMinecraftKey(featureKey, "placed feature key");
        return switch (key) {
            case LAKE_LAVA_UNDERGROUND -> new PlacedSpec(LAKE_LAVA_UNDERGROUND, 0, 9, false);
            case LAKE_LAVA_SURFACE -> new PlacedSpec(LAKE_LAVA_SURFACE, 1, 200, true);
            default -> throw new IllegalArgumentException("unsupported lava-lake feature: " + key);
        };
    }

    private static String requireMinecraftKey(String key, String description) {
        String canonical = Mc263FeatureBlockState.requireCanonicalResourceKey(key, description);
        if (!canonical.startsWith("minecraft:")) {
            throw new IllegalArgumentException("Minecraft namespaced " + description
                    + " is required: " + key);
        }
        return canonical;
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

    private record PlacedSpec(String key, int globalIndex, int rarity, boolean surface) {
    }
}
