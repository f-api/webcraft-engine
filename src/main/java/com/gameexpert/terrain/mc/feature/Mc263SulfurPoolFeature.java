package com.gameexpert.terrain.mc.feature;

import static com.gameexpert.terrain.mc.feature.Mc263LakeFeature.emit;

import com.gameexpert.terrain.mc.feature.Mc263LakeFeature.BlockPos;
import com.gameexpert.terrain.mc.feature.Mc263LakeFeature.LakeConfiguration;
import com.gameexpert.terrain.mc.feature.Mc263LakeFeature.LakeKernelResult;
import com.gameexpert.terrain.mc.feature.Mc263LakeFeature.LakeWorldAccess;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.util.function.Predicate;

/**
 * Exact, inactive Minecraft Java 26.3-snapshot-7 sulfur-pool placed/configured feature leaf.
 *
 * <p>The supplied world is the complete FEATURES decoration-region view. It owns bounded flag-2
 * writes and every resulting tick, postprocessing, freeze, and potent-sulfur block-entity fact.
 * No canonical dispatcher calls this class until the complete feature carrier is reviewed.</p>
 */
public final class Mc263SulfurPoolFeature {
    public static final int LAKES_STEP = 1;
    public static final int GLOBAL_INDEX = 3;
    public static final int CANDIDATE_COUNT = 256;
    public static final String SULFUR_POOL = "minecraft:sulfur_pool";
    public static final String SULFUR_CAVES = "minecraft:sulfur_caves";
    public static final String SULFUR = "minecraft:sulfur";
    public static final String SULFUR_SPIKE = "minecraft:sulfur_spike";
    public static final String WATER = "minecraft:water";
    public static final String POTENT_SULFUR_WET =
            "minecraft:potent_sulfur[potent_sulfur_state=wet]";

    /** Pure exact output/sidecar closure for the sulfur-pool sequence. */
    public static void preflight(Predicate<String> supportsState, boolean supportsTicks,
            boolean supportsPostprocessing, boolean supportsPotentPayload) {
        Mc263LakeFeature.requireOutputs(supportsState, "minecraft:cave_air", WATER, SULFUR,
                "minecraft:ice", POTENT_SULFUR_WET);
        if (!supportsTicks) throw new UnsupportedOperationException("sulfur-pool ticks");
        if (!supportsPostprocessing) {
            throw new UnsupportedOperationException("sulfur-pool postprocessing");
        }
        if (!supportsPotentPayload) {
            throw new UnsupportedOperationException("potent-sulfur payload");
        }
    }

    public static final String INNER_SERVER_SHA1 =
            "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String LAKE_FEATURE_CLASS_SHA256 =
            "f17d2df5a3152e6dfb4ed18662c24c7950cb0a0617516088e89acb0d6d152a69";
    public static final String SEQUENCE_FEATURE_CLASS_SHA256 =
            "815580c4c95545d9ef924ecbc9300f2cf27bb3996c9a30bf7086189f0cc48306";
    public static final String SIMPLE_BLOCK_FEATURE_CLASS_SHA256 =
            "10745682a08452d5e5f148cfd627fa1ec84bf669570da5cd284ee2119e810101";
    public static final String ENVIRONMENT_SCAN_PLACEMENT_CLASS_SHA256 =
            "ad4a0da23aef7d6142f408ba9943e3cfce53e9f0c23439176e950e43c168e5b2";
    public static final String FEATURE_PLACER_CLASS_SHA256 =
            "1d3e9c087bf63463503ba2fb55893053a3e0da6cd38fd3d6302afb02d9be437f";
    public static final String CONFIGURED_FEATURE_SHA256 =
            "eb5a3774f3ede0043fd0f3ea3acaea486bfcd653c1c373402249b613a31dc9ab";
    public static final String PLACED_FEATURE_SHA256 =
            "af2df52ee609dc1e2ae72b5bd628aa50712c12579ffaff71e7c6e0aecf21a2a1";
    public static final String SULFUR_CAVES_BIOME_SHA256 =
            "84f2072d5c4b76c5be00152b0ef21e73e93c57315872e0f566dee24cf9f93c7a";
    public static final String AIR_TAG_SHA256 = Mc263LakeFeature.AIR_TAG_SHA256;
    public static final String FEATURES_CANNOT_REPLACE_TAG_SHA256 =
            Mc263LakeFeature.FEATURES_CANNOT_REPLACE_TAG_SHA256;
    public static final String LAVA_POOL_STONE_CANNOT_REPLACE_TAG_SHA256 =
            Mc263LakeFeature.LAVA_POOL_STONE_CANNOT_REPLACE_TAG_SHA256;

    private static final int MIN_HEIGHT = -64;
    private static final int HEIGHT_SPAN = 321;
    private static final int OUTER_SCAN_STEPS = 32;
    private static final int POTENT_SCAN_STEPS = 4;
    private static final Mc263LakeFeature.TraceSink NO_TRACE = new Mc263LakeFeature.TraceSink() {
        @Override
        public void record(String phase, long... values) {
        }

        @Override
        public boolean enabled() {
            return false;
        }
    };
    private static final LakeConfiguration LAKE_CONFIGURATION =
            new LakeConfiguration(WATER, SULFUR, SULFUR_SPIKE, true);

    private Mc263SulfurPoolFeature() {
    }

    /** Complete isolated live view required by all modifiers and both configured subfeatures. */
    public interface WorldAccess {
        int minGenerationY();

        int generationDepth();

        String biomeKey(int blockX, int blockY, int blockZ);

        String blockState(int blockX, int blockY, int blockZ);

        boolean isSolid(int blockX, int blockY, int blockZ);

        boolean isLiquid(int blockX, int blockY, int blockZ);

        /** True only for the exact configured source-water block state. */
        boolean isExactSourceWater(int blockX, int blockY, int blockZ);

        /** True when the live fluid state matches the configured {@code water} fluid holder. */
        boolean hasWaterFluid(int blockX, int blockY, int blockZ);

        boolean isInBlockTag(int blockX, int blockY, int blockZ, String tagKey);

        /** Exact live biome freeze predicate with neighbor checking disabled. */
        boolean shouldFreeze(int blockX, int blockY, int blockZ);

        /** Diagnostic live fact after the potent-sulfur flag-2 set attempt. */
        boolean hasPotentSulfurBlockEntity(int blockX, int blockY, int blockZ);

        /** Bounded flag-2 write; false is an official silent failure, never coordinate clipping. */
        boolean trySetBlockState(int blockX, int blockY, int blockZ, String state);

        /** Bounded sidecar operation; invalid positions are silent no-ops. */
        void scheduleBlockTick(int blockX, int blockY, int blockZ, String block, int delay);

        /** Bounded sidecar operation; invalid positions are silent no-ops. */
        void markForPostprocessing(int blockX, int blockY, int blockZ);

        default boolean isOutsideBuildHeight(int blockY) {
            return blockY < minGenerationY()
                    || blockY >= minGenerationY() + generationDepth();
        }
    }

    public record PlacementResult(long decorationSeed, long featureSeed, int globalIndex,
                                  int candidates, int lakePlacements, int sequenceSuccesses,
                                  int potentWriteAttempts, int potentAcceptedWrites,
                                  int potentBlockEntities) {
        public PlacementResult {
            validateCounts(candidates, lakePlacements, sequenceSuccesses, potentWriteAttempts,
                    potentAcceptedWrites, potentBlockEntities);
        }
    }

    public record PlacementCounts(int candidates, int lakePlacements, int sequenceSuccesses,
                                  int potentWriteAttempts, int potentAcceptedWrites,
                                  int potentBlockEntities) {
        public PlacementCounts {
            validateCounts(candidates, lakePlacements, sequenceSuccesses, potentWriteAttempts,
                    potentAcceptedWrites, potentBlockEntities);
        }
    }

    public record ConfiguredResult(boolean placed, boolean lakePlaced,
                                   int airWrites, int waterWrites, int barrierWrites,
                                   int freezeWrites, boolean potentTargetFound,
                                   boolean potentSurvived, boolean potentWriteAttempted,
                                   boolean potentWriteAccepted,
                                   boolean potentBlockEntityPresent) {
        public ConfiguredResult {
            if (airWrites < 0 || waterWrites < 0 || barrierWrites < 0 || freezeWrites < 0
                    || (!lakePlaced && (placed || airWrites != 0 || waterWrites != 0
                    || barrierWrites != 0 || freezeWrites != 0 || potentTargetFound
                    || potentSurvived || potentWriteAttempted || potentWriteAccepted
                    || potentBlockEntityPresent))
                    || (potentSurvived && !potentTargetFound)
                    || (potentWriteAttempted != potentSurvived)
                    || (placed != potentWriteAttempted)
                    || (potentWriteAccepted && !potentWriteAttempted)
                    || (potentBlockEntityPresent && !potentWriteAttempted)) {
                throw new IllegalArgumentException("invalid sulfur-pool configured result");
            }
        }
    }

    /** Runs the independently seeded step-1/index-3 placed feature. */
    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world) {
        return place(worldSeed, sourceBlockX, sourceBlockZ, world, NO_TRACE);
    }

    /** Runs the independently seeded step-1/index-3 placed feature. */
    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world, Mc263LakeFeature.TraceSink trace) {
        requireWorld(world);
        Mc263LakeFeature.TraceSink events = requireTrace(trace);
        Mc263DecorationRandom.FeatureRandom seeded = Mc263DecorationRandom.forFeature(
                worldSeed, sourceBlockX, sourceBlockZ, LAKES_STEP, SULFUR_POOL);
        PlacementCounts counts = placeWithFeatureRandom(sourceBlockX, sourceBlockZ, world,
                seeded.random(), events);
        return new PlacementResult(seeded.decorationSeed(), seeded.featureSeed(),
                seeded.globalIndex(), counts.candidates(), counts.lakePlacements(),
                counts.sequenceSuccesses(), counts.potentWriteAttempts(),
                counts.potentAcceptedWrites(), counts.potentBlockEntities());
    }

    /** Executes all 256 modifier candidates and configured sequences on one shared stream. */
    public static PlacementCounts placeWithFeatureRandom(int sourceBlockX, int sourceBlockZ,
            WorldAccess world, WorldgenRandom random, Mc263LakeFeature.TraceSink trace) {
        requireWorld(world);
        if (random == null) throw new IllegalArgumentException("random is required");
        Mc263LakeFeature.TraceSink events = requireTrace(trace);
        verifyIndex();

        emit(events, "count", CANDIDATE_COUNT);
        int lakePlacements = 0;
        int sequenceSuccesses = 0;
        int potentWriteAttempts = 0;
        int potentAcceptedWrites = 0;
        int potentBlockEntities = 0;
        for (int attempt = 0; attempt < CANDIDATE_COUNT; attempt++) {
            int x = sourceBlockX + random.nextInt(16);
            int z = sourceBlockZ + random.nextInt(16);
            int y = random.nextInt(HEIGHT_SPAN) + MIN_HEIGHT;
            emit(events, "candidate", attempt, x, y, z);

            boolean solid = world.isSolid(x, y, z);
            emit(events, "solid_filter", attempt, solid ? 1L : 0L);
            if (!solid) continue;

            int airY = scanUpForAir(world, x, y, z, attempt, events);
            if (airY == Integer.MIN_VALUE) continue;
            int originY = airY - 1;
            boolean sulfur = blockKey(world.blockState(x, originY, z)).equals(SULFUR);
            emit(events, "sulfur_filter", attempt, x, originY, z, sulfur ? 1L : 0L);
            if (!sulfur) continue;

            boolean biome = biomeContains(world.biomeKey(x, originY, z));
            emit(events, "biome", attempt, x, originY, z, biome ? 1L : 0L);
            if (!biome) continue;

            ConfiguredResult configured = placeConfigured(random,
                    new BlockPos(x, originY, z), world, events);
            if (configured.lakePlaced()) lakePlacements++;
            if (configured.placed()) sequenceSuccesses++;
            if (configured.potentWriteAttempted()) potentWriteAttempts++;
            if (configured.potentWriteAccepted()) potentAcceptedWrites++;
            if (configured.potentBlockEntityPresent()) potentBlockEntities++;
        }
        emit(events, "placed_result", GLOBAL_INDEX, CANDIDATE_COUNT, lakePlacements,
                sequenceSuccesses, potentWriteAttempts, potentAcceptedWrites,
                potentBlockEntities);
        return new PlacementCounts(CANDIDATE_COUNT, lakePlacements, sequenceSuccesses,
                potentWriteAttempts, potentAcceptedWrites, potentBlockEntities);
    }

    /** Executes the configured lake then placed wet-potent-sulfur sequence. */
    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world) {
        return placeConfigured(random, origin, world, NO_TRACE);
    }

    /** Executes the configured lake then placed wet-potent-sulfur sequence. */
    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world, Mc263LakeFeature.TraceSink trace) {
        requireWorld(world);
        if (random == null || origin == null) {
            throw new IllegalArgumentException("random and origin are required");
        }
        Mc263LakeFeature.TraceSink events = requireTrace(trace);
        LakeKernelResult lake = Mc263LakeFeature.placeConfiguredKernel(random, origin,
                lakeWorld(world), LAKE_CONFIGURATION, events);
        if (!lake.placed()) {
            emit(events, "sequence_result", 0L, 0L, 0L, 0L);
            return new ConfiguredResult(false, false, 0, 0, 0, 0,
                    false, false, false, false, false);
        }

        int potentY = scanDownForPotentTarget(world, origin, events);
        if (potentY == Integer.MIN_VALUE) {
            emit(events, "sequence_result", 1L, 0L, 0L, 0L);
            return configuredResult(false, lake, false, false, false, false, false);
        }

        // PotentSulfurBlock inherits BlockBehaviour.canSurvive, which is unconditionally true.
        emit(events, "potent_survive", origin.x(), potentY, origin.z(), 1L);

        boolean accepted = world.trySetBlockState(
                origin.x(), potentY, origin.z(), POTENT_SULFUR_WET);
        boolean blockEntity = world.hasPotentSulfurBlockEntity(origin.x(), potentY, origin.z());
        emit(events, "potent_write", origin.x(), potentY, origin.z(),
                accepted ? 1L : 0L, blockEntity ? 1L : 0L);
        emit(events, "sequence_result", 1L, 1L, accepted ? 1L : 0L,
                blockEntity ? 1L : 0L);
        return configuredResult(true, lake, true, true, true, accepted, blockEntity);
    }

    private static LakeWorldAccess lakeWorld(WorldAccess world) {
        return new LakeWorldAccess() {
            @Override
            public int minGenerationY() {
                return world.minGenerationY();
            }

            @Override
            public String blockState(int x, int y, int z) {
                return world.blockState(x, y, z);
            }

            @Override
            public boolean isSolid(int x, int y, int z) {
                return world.isSolid(x, y, z);
            }

            @Override
            public boolean isLiquid(int x, int y, int z) {
                return world.isLiquid(x, y, z);
            }

            @Override
            public boolean isExactConfiguredFluid(int x, int y, int z) {
                return world.isExactSourceWater(x, y, z);
            }

            @Override
            public boolean isInBlockTag(int x, int y, int z, String tagKey) {
                return world.isInBlockTag(x, y, z, tagKey);
            }

            @Override
            public boolean trySetBlockState(int x, int y, int z, String state) {
                return world.trySetBlockState(x, y, z, state);
            }

            @Override
            public void scheduleBlockTick(int x, int y, int z, String block, int delay) {
                world.scheduleBlockTick(x, y, z, block, delay);
            }

            @Override
            public void markForPostprocessing(int x, int y, int z) {
                world.markForPostprocessing(x, y, z);
            }

            @Override
            public boolean shouldFreeze(int x, int y, int z) {
                return world.shouldFreeze(x, y, z);
            }
        };
    }

    private static int scanUpForAir(WorldAccess world, int x, int startY, int z, int attempt,
            Mc263LakeFeature.TraceSink trace) {
        int y = startY;
        for (int step = 0; step < OUTER_SCAN_STEPS; step++) {
            if (world.isInBlockTag(x, y, z, Mc263LakeFeature.AIR_TAG)) {
                emit(trace, "outer_scan", attempt, x, startY, z, y, step, 1L);
                return y;
            }
            y++;
            if (world.isOutsideBuildHeight(y)) {
                emit(trace, "outer_scan", attempt, x, startY, z, y, step + 1L, 0L);
                return Integer.MIN_VALUE;
            }
        }
        boolean accepted = world.isInBlockTag(x, y, z, Mc263LakeFeature.AIR_TAG);
        emit(trace, "outer_scan", attempt, x, startY, z, y, OUTER_SCAN_STEPS,
                accepted ? 1L : 0L);
        return accepted ? y : Integer.MIN_VALUE;
    }

    private static int scanDownForPotentTarget(WorldAccess world, BlockPos origin,
            Mc263LakeFeature.TraceSink trace) {
        int y = origin.y();
        for (int step = 0; step < POTENT_SCAN_STEPS; step++) {
            if (isPotentTarget(world, origin.x(), y, origin.z())) {
                emit(trace, "potent_scan", origin.x(), origin.y(), origin.z(), y, step, 1L);
                return y;
            }
            y--;
            if (world.isOutsideBuildHeight(y)) {
                emit(trace, "potent_scan", origin.x(), origin.y(), origin.z(), y,
                        step + 1L, 0L);
                return Integer.MIN_VALUE;
            }
        }
        boolean accepted = isPotentTarget(world, origin.x(), y, origin.z());
        emit(trace, "potent_scan", origin.x(), origin.y(), origin.z(), y,
                POTENT_SCAN_STEPS, accepted ? 1L : 0L);
        return accepted ? y : Integer.MIN_VALUE;
    }

    private static boolean isPotentTarget(WorldAccess world, int x, int y, int z) {
        return world.isSolid(x, y, z) && world.hasWaterFluid(x, y + 1, z);
    }

    private static ConfiguredResult configuredResult(boolean placed, LakeKernelResult lake,
            boolean target, boolean survived, boolean attempted, boolean accepted,
            boolean blockEntity) {
        return new ConfiguredResult(placed, true, lake.airWrites(), lake.fluidWrites(),
                lake.barrierWrites(), lake.freezeWrites(), target, survived, attempted,
                accepted, blockEntity);
    }

    private static boolean biomeContains(String biomeKey) {
        String key = Mc263FeatureBlockState.requireCanonicalResourceKey(biomeKey, "biome key");
        if (!key.startsWith("minecraft:")) {
            throw new IllegalArgumentException("Minecraft namespaced biome key is required: "
                    + biomeKey);
        }
        return Mc263FeatureIndexReceipt.biome(key).featuresAtStep(LAKES_STEP).stream()
                .anyMatch(reference -> reference.featureKey().equals(SULFUR_POOL));
    }

    private static void verifyIndex() {
        int index = Mc263DecorationRandom.globalIndex(LAKES_STEP, SULFUR_POOL);
        if (index != GLOBAL_INDEX
                || !Mc263FeatureIndexReceipt.step(LAKES_STEP).featureAt(index)
                .equals(SULFUR_POOL)) {
            throw new IllegalStateException("pinned sulfur-pool feature index changed");
        }
    }

    private static String blockKey(String state) {
        int properties = state.indexOf('[');
        return properties < 0 ? state : state.substring(0, properties);
    }

    private static void requireWorld(WorldAccess world) {
        if (world == null || world.minGenerationY() != -64 || world.generationDepth() != 384) {
            throw new IllegalArgumentException(
                    "pinned Overworld access with min Y -64 and depth 384 is required");
        }
    }

    private static Mc263LakeFeature.TraceSink requireTrace(Mc263LakeFeature.TraceSink trace) {
        if (trace == null) throw new IllegalArgumentException("trace sink is required");
        return trace;
    }

    private static void validateCounts(int candidates, int lakePlacements,
            int sequenceSuccesses, int potentWriteAttempts, int potentAcceptedWrites,
            int potentBlockEntities) {
        if (candidates < 0 || candidates > CANDIDATE_COUNT
                || lakePlacements < 0 || lakePlacements > candidates
                || sequenceSuccesses < 0 || sequenceSuccesses > lakePlacements
                || potentWriteAttempts != sequenceSuccesses
                || potentAcceptedWrites < 0 || potentAcceptedWrites > potentWriteAttempts
                || potentBlockEntities < 0 || potentBlockEntities > potentWriteAttempts) {
            throw new IllegalArgumentException("invalid sulfur-pool placement counts");
        }
    }
}
