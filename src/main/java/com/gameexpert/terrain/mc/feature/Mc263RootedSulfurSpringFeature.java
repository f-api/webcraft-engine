package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.McRandom;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.util.function.Predicate;

/**
 * Inactive 26.3-snapshot-7 rooted-sulfur-spring leaf with an explicit originality boundary.
 *
 * <p>The placed modifiers, {@code RootSystemFeature}, tuff-cover modifiers, weighted spring slot,
 * rotation and palette draws match the pinned server. Mojang's ten template voxel layouts are
 * deliberately neither stored nor reconstructed. After their official selection draws have been
 * consumed, a domain-separated site random drives a small procedural spring grammar without
 * advancing the official feature stream. Post-boundary root and hanging-root draw coordinates stay
 * in the official receipt; their predicates and writes observe the substituted grammar and are
 * therefore originality-owned. Consequently {@link #officialTemplateVoxelParity()} is permanently
 * false even though every official-owned event and random suffix remains testable.</p>
 */
public final class Mc263RootedSulfurSpringFeature {
    public static final int LAKES_STEP = 1;
    public static final int GLOBAL_INDEX = 2;
    public static final String ROOTED_SULFUR_SPRING = "minecraft:rooted_sulfur_spring";
    public static final String SULFUR_CAVES = "minecraft:sulfur_caves";
    public static final String SULFUR = "minecraft:sulfur";
    public static final String TUFF = "minecraft:tuff";
    public static final String WATER = "minecraft:water";
    public static final String AZALEA_ROOT_REPLACEABLE = "minecraft:azalea_root_replaceable";

    /** Pure exact output closure for the official spring and original surround. */
    public static void preflight(Predicate<String> supportsState) {
        Mc263LakeFeature.requireOutputs(supportsState, TUFF, SULFUR, WATER);
    }

    public static final String INNER_SERVER_SHA1 =
            "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String ROOT_SYSTEM_FEATURE_CLASS_SHA256 =
            "fdd06a1045afd399395f5aaf8cb8599be4339f9920144cea8760e741ce94fca4";
    public static final String TEMPLATE_FEATURE_CLASS_SHA256 =
            "cb697c9adb31935037fe35e4db997d4d9318a880b2268076fe08ba581106eb18";
    public static final String WEIGHTED_SELECTOR_CLASS_SHA256 =
            "acb5fdc954d66445c828096813e5cad8154e90faca645a6f672b06941337b490";
    public static final String SEQUENCE_FEATURE_CLASS_SHA256 =
            "815580c4c95545d9ef924ecbc9300f2cf27bb3996c9a30bf7086189f0cc48306";
    public static final String SIMPLE_BLOCK_FEATURE_CLASS_SHA256 =
            "10745682a08452d5e5f148cfd627fa1ec84bf669570da5cd284ee2119e810101";
    public static final String ENVIRONMENT_SCAN_PLACEMENT_CLASS_SHA256 =
            "ad4a0da23aef7d6142f408ba9943e3cfce53e9f0c23439176e950e43c168e5b2";
    public static final String FEATURE_PLACER_CLASS_SHA256 =
            "1d3e9c087bf63463503ba2fb55893053a3e0da6cd38fd3d6302afb02d9be437f";
    public static final String CONFIGURED_FEATURE_SHA256 =
            "ccb98a72eeaeff4b863aec8b9f528b952b7fc81112538d934cf54ea7554021f5";
    public static final String NESTED_SPRING_FEATURE_SHA256 =
            "1e55e3d8a07496c9998e5ee71599051fb163fb44d9da818713f5d7d64a1bed72";
    public static final String PLACED_FEATURE_SHA256 =
            "6b21dc439db8ccb2ea93034323041645a56548f970a57010b0c5084a951f5fa9";
    public static final String SULFUR_CAVES_BIOME_SHA256 =
            "84f2072d5c4b76c5be00152b0ef21e73e93c57315872e0f566dee24cf9f93c7a";
    public static final String AIR_TAG_SHA256 = Mc263LakeFeature.AIR_TAG_SHA256;
    public static final String AZALEA_ROOT_REPLACEABLE_TAG_SHA256 =
            "ea787c96911c9c031f6a08761a13df0e365f3ad32dd5550a6b467b1b0e6b823a";
    /** Sorted basename, NUL byte, and raw pinned NBT bytes for all ten templates. */
    public static final String TEMPLATE_BOUNDARY_AGGREGATE_SHA256 =
            "0488820b16290f2eb270f405e26f16dbb8db154f4d8b80c7bb70f7d6ee83909b";
    public static final int TEMPLATE_ENTRY_COUNT = 10;
    public static final int TEMPLATE_PALETTE_COUNT = 1;
    public static final int TEMPLATE_SIZE_Y = 11;
    public static final int TEMPLATE_ENTITY_COUNT = 0;
    public static final int TEMPLATE_BLOCK_NBT_COUNT = 0;
    public static final int TEMPLATE_MIN_BLOCK_COUNT = 1;

    private static final int MIN_HEIGHT = -64;
    private static final int HEIGHT_SPAN = 321;
    private static final int OUTER_SCAN_STEPS = 12;
    private static final int ROOT_COLUMN_MAX_HEIGHT = 184;
    private static final int REQUIRED_VERTICAL_SPACE = 5;
    private static final int LEVEL_TEST_DISTANCE = 8;
    private static final int MAX_LEVEL_DEVIATION = 2;
    private static final int ROOT_RADIUS = 3;
    private static final int ROOT_PLACEMENT_ATTEMPTS = 20;
    private static final int HANGING_ROOT_RADIUS = 1;
    private static final int HANGING_ROOT_VERTICAL_SPAN = 1;
    private static final int HANGING_ROOT_ATTEMPTS = 1;
    private static final int ALLOWED_VERTICAL_WATER = 1;
    private static final long ORIGINALITY_DOMAIN = 0x7273733236330001L; // "rss263\0\1"
    private static final int[][] HORIZONTAL = {
            {0, 0, 1}, {-1, 0, 0}, {0, 0, -1}, {1, 0, 0}
    };
    private static final OfficialTraceSink NO_OFFICIAL_TRACE = new OfficialTraceSink() {
        @Override
        public void record(String phase, long... values) {
        }

        @Override
        public boolean enabled() {
            return false;
        }
    };
    private static final OriginalityTraceSink NO_ORIGINALITY_TRACE =
            new OriginalityTraceSink() {
                @Override
                public void record(String phase, long... values) {
                }

                @Override
                public boolean enabled() {
                    return false;
                }
            };

    private Mc263RootedSulfurSpringFeature() {
    }

    /** Live FEATURES-region view. False writes are bounded no-ops and never coordinate clipping. */
    public interface WorldAccess {
        int minGenerationY();

        int generationDepth();

        String biomeKey(int blockX, int blockY, int blockZ);

        String blockState(int blockX, int blockY, int blockZ);

        int worldSurface(int blockX, int blockZ);

        boolean isSolid(int blockX, int blockY, int blockZ);

        boolean isWater(int blockX, int blockY, int blockZ);

        boolean hasLavaFluid(int blockX, int blockY, int blockZ);

        boolean isFaceSturdyDown(int blockX, int blockY, int blockZ);

        boolean isInBlockTag(int blockX, int blockY, int blockZ, String tagKey);

        boolean trySetBlockState(int blockX, int blockY, int blockZ, String state);

        default boolean isOutsideBuildHeight(int blockY) {
            return blockY < minGenerationY()
                    || blockY >= minGenerationY() + generationDepth();
        }
    }

    @FunctionalInterface
    public interface OfficialTraceSink {
        void record(String phase, long... values);

        default boolean enabled() {
            return true;
        }
    }

    @FunctionalInterface
    public interface OriginalityTraceSink {
        void record(String phase, long... values);

        default boolean enabled() {
            return true;
        }
    }

    public record BlockPos(int x, int y, int z) {
    }

    public record PlacementResult(long decorationSeed, long featureSeed, int globalIndex,
                                  int candidates, int configuredSuccesses,
                                  int attemptedWrites, int acceptedWrites) {
        public PlacementResult {
            validateCounts(candidates, configuredSuccesses, attemptedWrites, acceptedWrites);
        }
    }

    public record PlacementCounts(int candidates, int configuredSuccesses,
                                  int attemptedWrites, int acceptedWrites) {
        public PlacementCounts {
            validateCounts(candidates, configuredSuccesses, attemptedWrites, acceptedWrites);
        }
    }

    public record ConfiguredResult(boolean returned,
                                   boolean springPlaced,
                                   boolean officialTemplateBoundaryReached,
                                   boolean officialTemplateVoxelParity,
                                   int profile, int templateSlot, int rotation, int palette,
                                   int tuffAttempts, int tuffSuccessfulPlacements,
                                   int proceduralAttemptedWrites,
                                   int substitutionRootAttemptedWrites,
                                   int substitutionHangingAttemptedWrites,
                                   int acceptedWrites) {
        public ConfiguredResult {
            if (profile < -1 || profile > 3 || templateSlot < -1 || rotation < -1
                    || palette < -1 || tuffAttempts < 0 || tuffSuccessfulPlacements < 0
                    || proceduralAttemptedWrites < 0 || substitutionRootAttemptedWrites < 0
                    || substitutionHangingAttemptedWrites < 0 || acceptedWrites < 0
                    || officialTemplateVoxelParity
                    || (officialTemplateBoundaryReached
                    && (profile < 0 || templateSlot < 0 || rotation < 0 || palette != 0))
                    || (!officialTemplateBoundaryReached
                    && (templateSlot != -1 || rotation != -1 || palette != -1))
                    || (springPlaced != officialTemplateBoundaryReached)
                    || (!returned && (springPlaced || tuffAttempts != 0))
                    || acceptedWrites > tuffSuccessfulPlacements + proceduralAttemptedWrites
                    + substitutionRootAttemptedWrites + substitutionHangingAttemptedWrites) {
                throw new IllegalArgumentException("invalid rooted-sulfur result");
            }
        }

        public int attemptedWrites() {
            return tuffSuccessfulPlacements + proceduralAttemptedWrites
                    + substitutionRootAttemptedWrites + substitutionHangingAttemptedWrites;
        }
    }

    /** The product never claims Mojang-template voxel identity. */
    public static boolean officialTemplateVoxelParity() {
        return false;
    }

    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world) {
        return place(worldSeed, sourceBlockX, sourceBlockZ, world,
                NO_OFFICIAL_TRACE, NO_ORIGINALITY_TRACE);
    }

    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world, OfficialTraceSink officialTrace,
            OriginalityTraceSink originalityTrace) {
        requireWorld(world);
        Mc263DecorationRandom.FeatureRandom seeded = Mc263DecorationRandom.forFeature(
                worldSeed, sourceBlockX, sourceBlockZ, LAKES_STEP, ROOTED_SULFUR_SPRING);
        PlacementCounts counts = placeWithFeatureRandom(worldSeed, sourceBlockX, sourceBlockZ,
                world, seeded.random(), officialTrace, originalityTrace);
        return new PlacementResult(seeded.decorationSeed(), seeded.featureSeed(),
                seeded.globalIndex(), counts.candidates(), counts.configuredSuccesses(),
                counts.attemptedWrites(), counts.acceptedWrites());
    }

    public static PlacementCounts placeWithFeatureRandom(long worldSeed,
            int sourceBlockX, int sourceBlockZ, WorldAccess world, WorldgenRandom random,
            OfficialTraceSink officialTrace, OriginalityTraceSink originalityTrace) {
        requireWorld(world);
        if (random == null) throw new IllegalArgumentException("random is required");
        OfficialTraceSink official = requireOfficialTrace(officialTrace);
        OriginalityTraceSink original = requireOriginalityTrace(originalityTrace);
        verifyIndex();

        int count = random.nextInt(2) + 1;
        if (official.enabled()) official.record("count", 1L, 2L, count);
        int configuredCalls = 0;
        int configuredReturns = 0;
        int springPlacements = 0;
        int attemptedWrites = 0;
        int acceptedWrites = 0;
        for (int attempt = 0; attempt < count; attempt++) {
            int x = sourceBlockX + random.nextInt(16);
            int z = sourceBlockZ + random.nextInt(16);
            int y = random.nextInt(HEIGHT_SPAN) + MIN_HEIGHT;
            if (official.enabled()) official.record("candidate", attempt, x, y, z);

            int scannedY = scanOuterUp(world, x, y, z, attempt, official);
            if (scannedY == Integer.MIN_VALUE) continue;
            int originY = scannedY - 1;
            boolean biome = biomeContains(world.biomeKey(x, originY, z));
            if (official.enabled()) {
                official.record("biome", attempt, x, originY, z, biome ? 1L : 0L);
            }
            if (!biome) continue;

            configuredCalls++;
            ConfiguredResult result = placeConfigured(worldSeed,
                    new BlockPos(x, originY, z), world, random, official, original);
            // The outer environment scan guarantees an air origin; keep this explicit for probes.
            if (result.returned()) configuredReturns++;
            if (result.springPlaced()) springPlacements++;
            attemptedWrites += result.attemptedWrites();
            acceptedWrites += result.acceptedWrites();
        }
        if (official.enabled()) {
            official.record("placed_result", GLOBAL_INDEX, count, configuredCalls,
                    configuredReturns, springPlacements);
        }
        return new PlacementCounts(count, configuredReturns, attemptedWrites, acceptedWrites);
    }

    public static ConfiguredResult placeConfigured(long worldSeed, BlockPos origin,
            WorldAccess world, WorldgenRandom random) {
        return placeConfigured(worldSeed, origin, world, random,
                NO_OFFICIAL_TRACE, NO_ORIGINALITY_TRACE);
    }

    public static ConfiguredResult placeConfigured(long worldSeed, BlockPos origin,
            WorldAccess world, WorldgenRandom random, OfficialTraceSink officialTrace,
            OriginalityTraceSink originalityTrace) {
        requireWorld(world);
        if (origin == null || random == null) {
            throw new IllegalArgumentException("origin and random are required");
        }
        OfficialTraceSink official = requireOfficialTrace(officialTrace);
        OriginalityTraceSink original = requireOriginalityTrace(originalityTrace);

        boolean originAir = isAir(world.blockState(origin.x(), origin.y(), origin.z()));
        if (official.enabled()) {
            official.record("root_origin", origin.x(), origin.y(), origin.z(),
                    originAir ? 1L : 0L);
        }
        if (!originAir) return emptyResult();

        for (int level = 0; level < ROOT_COLUMN_MAX_HEIGHT; level++) {
            int treeY = origin.y() + level + 1;
            int surface = world.worldSurface(origin.x(), origin.z());
            boolean withinSurface = surface >= treeY;
            if (!withinSurface) {
                if (official.enabled()) {
                    official.record("root_column_probe", level, origin.x(), treeY, origin.z(),
                            surface, 0L, -1L);
                }
                return unsuccessfulRootSystem(official);
            }
            boolean allowed = world.isInBlockTag(
                    origin.x(), treeY, origin.z(), Mc263LakeFeature.AIR_TAG);
            if (official.enabled()) {
                official.record("root_column_probe", level, origin.x(), treeY, origin.z(),
                        surface, 1L, allowed ? 1L : 0L);
            }

            if (!allowed) continue;
            if (!spaceForSpring(level, world, origin.x(), treeY, origin.z(), official)) continue;

            boolean lavaFoundation = world.hasLavaFluid(origin.x(), treeY - 1, origin.z());
            boolean solidFoundation = !lavaFoundation
                    && world.isSolid(origin.x(), treeY - 1, origin.z());
            boolean foundationAccepted = !lavaFoundation && solidFoundation;
            if (official.enabled()) {
                official.record("foundation", level, origin.x(), treeY - 1, origin.z(),
                        lavaFoundation ? 1L : 0L, lavaFoundation ? -1L
                                : solidFoundation ? 1L : 0L,
                        foundationAccepted ? 1L : 0L);
            }
            if (!foundationAccepted) return unsuccessfulRootSystem(official);

            NestedResult nested = placeNestedSpring(worldSeed,
                    new BlockPos(origin.x(), treeY, origin.z()), world, random,
                    official, original);
            if (!nested.templateBoundaryReached) continue;

            WriteCounts roots = placeRootColumn(origin, origin.y() + level,
                    world, random, official, original);
            WriteCounts hanging = placeHangingRoots(origin, world, random, official, original);
            int accepted = nested.acceptedWrites + roots.accepted + hanging.accepted;
            if (official.enabled()) {
                official.record("configured_result", 1L, 1L, nested.profile,
                        nested.tuffAttempts, nested.tuffSuccesses);
            }
            if (original.enabled()) {
                original.record("substitution_result", nested.proceduralAttempts,
                        roots.attempted, hanging.attempted, accepted);
            }
            return new ConfiguredResult(true, true, true, false, nested.profile,
                    nested.templateSlot, nested.rotation, nested.palette,
                    nested.tuffAttempts, nested.tuffSuccesses, nested.proceduralAttempts,
                    roots.attempted, hanging.attempted, accepted);
        }
        return unsuccessfulRootSystem(official);
    }

    private static NestedResult placeNestedSpring(long worldSeed, BlockPos treeOrigin,
            WorldAccess world, WorldgenRandom random, OfficialTraceSink official,
            OriginalityTraceSink original) {
        int weightedDraw = random.nextInt(315);
        int profile = weightedDraw < 200 ? 0
                : weightedDraw < 290 ? 1 : weightedDraw < 310 ? 2 : 3;
        int tuffAttempts = switch (profile) {
            case 0 -> 64;
            case 1 -> 80;
            case 2 -> 96;
            default -> 128;
        };
        int horizontalRadius = 7 + profile;
        int templateCount = 4 - profile;
        if (official.enabled()) official.record("spring_weight", weightedDraw, profile);

        int tuffSuccesses = 0;
        int acceptedWrites = 0;
        for (int attempt = 0; attempt < tuffAttempts; attempt++) {
            int dx = triangle(random, horizontalRadius);
            int dy = triangle(random, 3);
            int dz = triangle(random, horizontalRadius);
            int x = treeOrigin.x() + dx;
            int y = treeOrigin.y() + dy;
            int z = treeOrigin.z() + dz;
            if (official.enabled()) official.record("tuff_candidate", attempt, x, y, z);
            int solidY = scanDownForSolid(world, x, y, z, attempt, official);
            if (solidY == Integer.MIN_VALUE || !world.isSolid(x, solidY, z)) continue;
            boolean accepted = world.trySetBlockState(x, solidY, z, TUFF);
            tuffSuccesses++;
            if (accepted) acceptedWrites++;
            if (official.enabled()) {
                official.record("tuff_write", attempt, x, solidY, z, accepted ? 1L : 0L);
            }
        }

        // SequenceFeature short-circuits here, so no template-owned draws occur on this path.
        if (tuffSuccesses == 0) {
            return new NestedResult(false, profile, -1, -1, -1,
                    tuffAttempts, 0, 0, acceptedWrites);
        }

        int templateDraw = random.nextInt(templateCount);
        int templateSlot = templateDraw;
        int rotation = random.nextInt(4);
        int palette = random.nextInt(1);
        if (official.enabled()) {
            official.record("template_slot", profile, templateDraw, templateSlot, templateCount);
            official.record("template_rotation", rotation);
            official.record("template_palette", palette);
        }
        int templateSize = templateSize(profile, templateSlot);
        int templateX = treeOrigin.x() + centeredTemplateOffsetX(templateSize, rotation);
        int templateY = treeOrigin.y() - 7;
        int templateZ = treeOrigin.z() + centeredTemplateOffsetZ(templateSize, rotation);
        if (official.enabled()) {
            official.record("template_boundary", profile, templateSlot, rotation,
                    templateX, templateY, templateZ);
            official.record("post_template_unverified", profile, templateSlot, rotation);
        }

        long grammarSeed = originalitySeed(worldSeed, treeOrigin, profile, templateSlot, rotation);
        WriteCounts grammar = placeOriginalGrammar(grammarSeed, treeOrigin, profile,
                templateSlot, rotation, world, original);
        return new NestedResult(true, profile, templateSlot, rotation, palette,
                tuffAttempts, tuffSuccesses, grammar.attempted,
                acceptedWrites + grammar.accepted);
    }

    private static boolean spaceForSpring(int level, WorldAccess world, int x, int y, int z,
            OfficialTraceSink trace) {
        for (int vertical = 1; vertical <= REQUIRED_VERTICAL_SPACE; vertical++) {
            String state = world.blockState(x, y + vertical, z);
            boolean accepted = isAir(state)
                    || (vertical + 1 <= ALLOWED_VERTICAL_WATER
                    && world.isWater(x, y + vertical, z));
            if (trace.enabled()) {
                trace.record("space_vertical", level, vertical, x, y + vertical, z,
                        accepted ? 1L : 0L);
            }
            if (!accepted) return false;
        }
        for (int direction = 0; direction < HORIZONTAL.length; direction++) {
            int[] delta = HORIZONTAL[direction];
            int probeX = x + delta[0] * LEVEL_TEST_DISTANCE;
            int probeZ = z + delta[2] * LEVEL_TEST_DISTANCE;
            boolean belowAir = isAir(world.blockState(
                    probeX, y - MAX_LEVEL_DEVIATION, probeZ));
            boolean aboveAir = isAir(world.blockState(
                    probeX, y + MAX_LEVEL_DEVIATION, probeZ));
            boolean accepted = !belowAir && aboveAir;
            if (trace.enabled()) {
                trace.record("level_probe", level, direction, probeX, y, probeZ,
                        belowAir ? 1L : 0L, aboveAir ? 1L : 0L, accepted ? 1L : 0L);
            }
            if (!accepted) return false;
        }
        return true;
    }

    private static WriteCounts placeRootColumn(BlockPos rootOrigin, int lastRootY,
            WorldAccess world, WorldgenRandom random, OfficialTraceSink official,
            OriginalityTraceSink original) {
        WriteCounts counts = new WriteCounts();
        for (int y = rootOrigin.y(); y < lastRootY; y++) {
            for (int attempt = 0; attempt < ROOT_PLACEMENT_ATTEMPTS; attempt++) {
                int dx = random.nextInt(ROOT_RADIUS) - random.nextInt(ROOT_RADIUS);
                int dz = random.nextInt(ROOT_RADIUS) - random.nextInt(ROOT_RADIUS);
                int x = rootOrigin.x() + dx;
                int z = rootOrigin.z() + dz;
                if (official.enabled()) {
                    official.record("roots_draw", y - rootOrigin.y(), attempt, x, y, z);
                }
                boolean replaceable = world.isInBlockTag(x, y, z, AZALEA_ROOT_REPLACEABLE);
                if (original.enabled()) {
                    original.record("roots_candidate", y - rootOrigin.y(), attempt, x, y, z,
                            replaceable ? 1L : 0L);
                }
                if (!replaceable) continue;
                counts.attempted++;
                boolean accepted = world.trySetBlockState(x, y, z, SULFUR);
                if (accepted) counts.accepted++;
                if (original.enabled()) {
                    original.record("roots_write", y - rootOrigin.y(), attempt, x, y, z,
                            accepted ? 1L : 0L);
                }
            }
        }
        return counts;
    }

    private static WriteCounts placeHangingRoots(BlockPos origin, WorldAccess world,
            WorldgenRandom random, OfficialTraceSink official, OriginalityTraceSink original) {
        WriteCounts counts = new WriteCounts();
        for (int attempt = 0; attempt < HANGING_ROOT_ATTEMPTS; attempt++) {
            int x = origin.x() + random.nextInt(HANGING_ROOT_RADIUS)
                    - random.nextInt(HANGING_ROOT_RADIUS);
            int y = origin.y() + random.nextInt(HANGING_ROOT_VERTICAL_SPAN)
                    - random.nextInt(HANGING_ROOT_VERTICAL_SPAN);
            int z = origin.z() + random.nextInt(HANGING_ROOT_RADIUS)
                    - random.nextInt(HANGING_ROOT_RADIUS);
            if (official.enabled()) {
                official.record("hanging_draw", attempt, x, y, z);
            }
            boolean empty = isAir(world.blockState(x, y, z));
            boolean sturdyAbove = empty && world.isFaceSturdyDown(x, y + 1, z);
            boolean survives = empty && sturdyAbove;
            if (original.enabled()) {
                original.record("roots_candidate", -1L, attempt, x, y, z,
                        survives ? 1L : 0L);
            }
            if (!survives) continue;
            counts.attempted++;
            boolean accepted = world.trySetBlockState(x, y, z, SULFUR);
            if (accepted) counts.accepted++;
            if (original.enabled()) {
                original.record("roots_write", -1L, attempt, x, y, z, accepted ? 1L : 0L);
            }
        }
        return counts;
    }

    /** Original, bounded basin/channel/spire grammar; no official template cell is consulted. */
    private static WriteCounts placeOriginalGrammar(long seed, BlockPos treeOrigin, int profile,
            int slot, int rotation, WorldAccess world, OriginalityTraceSink trace) {
        OriginalRandom random = new OriginalRandom(seed);
        int basinRadius = 2 + profile + random.nextInt(2);
        int depth = 1 + random.nextInt(2);
        int channelCount = 2 + random.nextInt(2 + profile);
        int spireCount = 1 + profile + random.nextInt(3);
        int baseY = treeOrigin.y() - 7;
        if (trace.enabled()) {
            trace.record("grammar_seed", seed);
            trace.record("grammar_profile", profile, slot, basinRadius,
                    depth, channelCount, spireCount);
        }

        WriteCounts counts = new WriteCounts();
        int basinIndex = 0;
        for (int dz = -basinRadius; dz <= basinRadius; dz++) {
            for (int dx = -basinRadius; dx <= basinRadius; dx++) {
                int distance = dx * dx + dz * dz;
                int jitter = random.nextInt(3) - 1;
                if (distance > basinRadius * basinRadius + jitter) continue;
                int x = treeOrigin.x() + dx;
                int z = treeOrigin.z() + dz;
                int index = basinIndex++;
                boolean water = distance <= (basinRadius - 1) * (basinRadius - 1);
                int foundationY = water ? baseY - depth : baseY - 1;
                writeOriginal(world, x, foundationY, z, TUFF, 0,
                        trace, "basin", counts, index, 0L, false);
                if (water) {
                    for (int fill = 0; fill < depth; fill++) {
                        writeOriginal(world, x, baseY - depth + 1 + fill, z, WATER, 2,
                                trace, "basin", counts, index, 0L, false);
                    }
                } else {
                    writeOriginal(world, x, baseY, z, SULFUR, 1,
                            trace, "basin", counts, index, 0L, false);
                }
            }
        }
        for (int channel = 0; channel < channelCount; channel++) {
            int direction = (rotation + channel + random.nextInt(2)) & 3;
            int length = basinRadius + 1 + random.nextInt(3 + profile);
            int[] delta = HORIZONTAL[direction];
            for (int step = basinRadius + 1; step <= length; step++) {
                writeOriginal(world, treeOrigin.x() + delta[0] * step, baseY,
                        treeOrigin.z() + delta[2] * step, TUFF, 0,
                        trace, "channel", counts, channel, step, true);
            }
        }
        for (int spire = 0; spire < spireCount; spire++) {
            int direction = random.nextInt(4);
            int lateral = random.nextInt(3) - 1;
            int radial = basinRadius + random.nextInt(2);
            int[] delta = HORIZONTAL[direction];
            int[] side = HORIZONTAL[(direction + 1) & 3];
            int x = treeOrigin.x() + delta[0] * radial + side[0] * lateral;
            int z = treeOrigin.z() + delta[2] * radial + side[2] * lateral;
            int height = 1 + random.nextInt(2 + profile);
            for (int dy = 0; dy < height; dy++) {
                writeOriginal(world, x, baseY + dy, z, SULFUR, 1,
                        trace, "spire", counts, spire, dy, true);
            }
        }
        if (trace.enabled()) {
            trace.record("originality_result", counts.attempted, counts.accepted);
        }
        return counts;
    }

    private static void writeOriginal(WorldAccess world, int x, int y, int z, String state,
            int stateId, OriginalityTraceSink trace, String phase, WriteCounts counts,
            long firstContext, long secondContext, boolean hasSecondContext) {
        counts.attempted++;
        boolean accepted = world.trySetBlockState(x, y, z, state);
        if (accepted) counts.accepted++;
        if (!trace.enabled()) return;
        if (hasSecondContext) {
            trace.record(phase, firstContext, secondContext, x, y, z, stateId,
                    accepted ? 1L : 0L);
        } else {
            trace.record(phase, firstContext, x, y, z, stateId, accepted ? 1L : 0L);
        }
    }

    private static int scanOuterUp(WorldAccess world, int x, int y, int z, int attempt,
            OfficialTraceSink trace) {
        if (!world.isInBlockTag(x, y, z, Mc263LakeFeature.AIR_TAG)) {
            if (trace.enabled()) trace.record("outer_scan", attempt, y, y, 0L, 0L);
            return Integer.MIN_VALUE;
        }
        int currentY = y;
        int moves = 0;
        for (int step = 0; step < OUTER_SCAN_STEPS; step++) {
            if (world.isSolid(x, currentY, z)) {
                if (trace.enabled()) {
                    trace.record("outer_scan", attempt, y, currentY, step, 1L);
                }
                return currentY;
            }
            currentY++;
            moves = step + 1;
            if (world.isOutsideBuildHeight(currentY)) {
                if (trace.enabled()) {
                    trace.record("outer_scan", attempt, y, currentY, step + 1L, 0L);
                }
                return Integer.MIN_VALUE;
            }
            if (!world.isInBlockTag(x, currentY, z, Mc263LakeFeature.AIR_TAG)) break;
        }
        boolean solid = world.isSolid(x, currentY, z);
        if (trace.enabled()) {
            trace.record("outer_scan", attempt, y, currentY, moves, solid ? 1L : 0L);
        }
        return solid ? currentY : Integer.MIN_VALUE;
    }

    private static int scanDownForSolid(WorldAccess world, int x, int y, int z, int attempt,
            OfficialTraceSink trace) {
        int currentY = y;
        for (int step = 0; step < 4; step++) {
            if (world.isSolid(x, currentY, z)) {
                if (trace.enabled()) {
                    trace.record("tuff_scan", attempt, x, y, z, currentY, step, 1L);
                }
                return currentY;
            }
            currentY--;
            if (world.isOutsideBuildHeight(currentY)) {
                if (trace.enabled()) {
                    trace.record("tuff_scan", attempt, x, y, z, currentY, step + 1L, 0L);
                }
                return Integer.MIN_VALUE;
            }
        }
        boolean solid = world.isSolid(x, currentY, z);
        if (trace.enabled()) {
            trace.record("tuff_scan", attempt, x, y, z, currentY, 4L, solid ? 1L : 0L);
        }
        return solid ? currentY : Integer.MIN_VALUE;
    }

    private static int triangle(WorldgenRandom random, int radius) {
        return random.nextInt(radius + 1) - random.nextInt(radius + 1);
    }

    private static long originalitySeed(long worldSeed, BlockPos origin,
            int profile, int slot, int rotation) {
        long value = worldSeed ^ ORIGINALITY_DOMAIN;
        value ^= McRandom.mixStafford13((long) origin.x() ^ 0x9E3779B97F4A7C15L);
        value ^= Long.rotateLeft(McRandom.mixStafford13(
                (long) origin.y() ^ 0xC2B2AE3D27D4EB4FL), 21);
        value ^= Long.rotateLeft(McRandom.mixStafford13(
                (long) origin.z() ^ 0x165667B19E3779F9L), 42);
        value ^= (long) profile * 0xD6E8FEB86659FD93L;
        value ^= (long) slot * 0xA5A3564E27F8862FL;
        value ^= (long) rotation * 0x9E6C63D0676A9A99L;
        return McRandom.mixStafford13(value);
    }

    static int centeredTemplateOffsetX(int packedSize, int rotation) {
        int halfX = (packedSize >>> 16) / 2;
        int halfZ = (packedSize & 0xffff) / 2;
        return switch (rotation) {
            case 1 -> halfZ;
            case 2 -> halfX;
            case 3 -> -halfZ;
            default -> -halfX;
        };
    }

    static int centeredTemplateOffsetZ(int packedSize, int rotation) {
        int halfX = (packedSize >>> 16) / 2;
        int halfZ = (packedSize & 0xffff) / 2;
        return switch (rotation) {
            case 1 -> -halfX;
            case 2 -> halfZ;
            case 3 -> halfX;
            default -> -halfZ;
        };
    }

    /** Only non-copyrightable size metadata needed by TemplateFeature's official centering rule. */
    static int templateSize(int profile, int slot) {
        return switch (profile) {
            case 0 -> switch (slot) {
                case 0 -> 8 << 16 | 9;
                case 1 -> 9 << 16 | 8;
                case 2, 3 -> 9 << 16 | 9;
                default -> throw new IllegalArgumentException("invalid small template slot");
            };
            case 1 -> switch (slot) {
                case 0 -> 11 << 16 | 11;
                case 1 -> 12 << 16 | 11;
                case 2 -> 12 << 16 | 12;
                default -> throw new IllegalArgumentException("invalid medium template slot");
            };
            case 2 -> switch (slot) {
                case 0 -> 13 << 16 | 12;
                case 1 -> 12 << 16 | 13;
                default -> throw new IllegalArgumentException("invalid large template slot");
            };
            case 3 -> {
                if (slot != 0) throw new IllegalArgumentException("invalid XL template slot");
                yield 16 << 16 | 16;
            }
            default -> throw new IllegalArgumentException("invalid template profile");
        };
    }

    private static ConfiguredResult emptyResult() {
        return new ConfiguredResult(false, false, false, false, -1, -1, -1, -1,
                0, 0, 0, 0, 0, 0);
    }

    private static ConfiguredResult unsuccessfulRootSystem(OfficialTraceSink trace) {
        if (trace.enabled()) {
            trace.record("configured_result", 1L, 0L, -1L, 0L, 0L);
        }
        return new ConfiguredResult(true, false, false, false, -1, -1, -1, -1,
                0, 0, 0, 0, 0, 0);
    }

    private static boolean biomeContains(String biomeKey) {
        if (biomeKey == null || !biomeKey.startsWith("minecraft:")
                || biomeKey.length() == "minecraft:".length()) {
            throw new IllegalArgumentException(
                    "exact Minecraft namespaced biome key is required: " + biomeKey);
        }
        return Mc263FeatureIndexReceipt.biome(biomeKey).featuresAtStep(LAKES_STEP).stream()
                .anyMatch(reference -> reference.featureKey().equals(ROOTED_SULFUR_SPRING));
    }

    private static boolean isAir(String state) {
        return Mc263FeatureBlockState.fromExact(state).isAir();
    }

    private static void verifyIndex() {
        int index = Mc263DecorationRandom.globalIndex(LAKES_STEP, ROOTED_SULFUR_SPRING);
        if (index != GLOBAL_INDEX
                || !Mc263FeatureIndexReceipt.step(LAKES_STEP).featureAt(index)
                .equals(ROOTED_SULFUR_SPRING)) {
            throw new IllegalStateException("pinned rooted-sulfur feature index changed");
        }
    }

    private static void requireWorld(WorldAccess world) {
        if (world == null || world.minGenerationY() != -64 || world.generationDepth() != 384) {
            throw new IllegalArgumentException(
                    "pinned Overworld access with min Y -64 and depth 384 is required");
        }
    }

    private static OfficialTraceSink requireOfficialTrace(OfficialTraceSink trace) {
        if (trace == null) throw new IllegalArgumentException("official trace sink is required");
        return trace;
    }

    private static OriginalityTraceSink requireOriginalityTrace(OriginalityTraceSink trace) {
        if (trace == null) throw new IllegalArgumentException("originality trace sink is required");
        return trace;
    }

    private static void validateCounts(int candidates, int successes,
            int attemptedWrites, int acceptedWrites) {
        if (candidates < 0 || candidates > 2 || successes < 0 || successes > candidates
                || attemptedWrites < 0 || acceptedWrites < 0
                || acceptedWrites > attemptedWrites) {
            throw new IllegalArgumentException("invalid rooted-sulfur placement counts");
        }
    }

    private static final class WriteCounts {
        private int attempted;
        private int accepted;
    }

    /** Domain-local SplitMix stream; no draw ever feeds back into the official worldgen stream. */
    private static final class OriginalRandom {
        private long state;

        private OriginalRandom(long seed) {
            state = seed;
        }

        private long nextLong() {
            state += 0x9E3779B97F4A7C15L;
            return McRandom.mixStafford13(state);
        }

        private int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
            return (int) Long.remainderUnsigned(nextLong(), bound);
        }
    }

    private record NestedResult(boolean templateBoundaryReached, int profile, int templateSlot,
                                int rotation, int palette, int tuffAttempts, int tuffSuccesses,
                                int proceduralAttempts, int acceptedWrites) {
    }
}
