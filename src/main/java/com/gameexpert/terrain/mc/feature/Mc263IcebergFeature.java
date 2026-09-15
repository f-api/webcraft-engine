package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.util.function.Predicate;

/**
 * Exact, inactive Minecraft Java 26.3-snapshot-7 iceberg configured feature and placed wrappers.
 *
 * <p>The configured feature deliberately replaces the supplied Y with sea level 63. The placed
 * biome filter, however, samples the candidate at the Overworld minimum generation Y (-64), as
 * produced by an in-square modifier without a height modifier. Writes are bounded only by the
 * supplied decoration-region view. No canonical generator calls this leaf yet.</p>
 */
public final class Mc263IcebergFeature {
    public static final int LOCAL_MODIFICATIONS_STEP = 2;
    public static final int ICEBERG_PACKED_GLOBAL_INDEX = 0;
    public static final int ICEBERG_BLUE_GLOBAL_INDEX = 1;
    public static final int SEA_LEVEL = 63;
    public static final String ICEBERG_PACKED = "minecraft:iceberg_packed";
    public static final String ICEBERG_BLUE = "minecraft:iceberg_blue";

    public static final String INNER_SERVER_SHA1 =
            "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String ICEBERG_FEATURE_CLASS_SHA256 =
            "3afe74960faca1e3230513c85235dea6ed8b45037ddb3f19e02b4073dcef9716";
    public static final String ICEBERG_FEATURE_DECOMPILED_SOURCE_SHA256 =
            "5ff7438c8d6926ba540977d5ca91bc8486e485a8bcd5d18e0ecca79a45508625";
    public static final String PACKED_CONFIGURED_FEATURE_SHA256 =
            "24d96cfd66e32ca3cc5e23f7bfe92fe9c95fd1bf234e7dd1d832ffb52a7d3614";
    public static final String BLUE_CONFIGURED_FEATURE_SHA256 =
            "a4c1397e27ccdaed525417bacaeb14824094195c45f3db77c57ed374d0729177";
    public static final String PACKED_PLACED_FEATURE_SHA256 =
            "e34a471087239a498862248da21bd1a77331c89ff7493b2b22dec87f85d3b93d";
    public static final String BLUE_PLACED_FEATURE_SHA256 =
            "700eb16a41919759f84efa6f6ffbf8a0ad28345d4e2bf8507a887c4fb27d22ab";

    private static final String AIR = "minecraft:air";
    private static final String WATER = "minecraft:water";
    private static final String ICE = "minecraft:ice";
    private static final String PACKED_ICE = "minecraft:packed_ice";
    private static final String BLUE_ICE = "minecraft:blue_ice";
    private static final String SNOW_BLOCK = "minecraft:snow_block";
    private static final String SNOW = "minecraft:snow";
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override
        public void record(String phase, long... values) {
        }

        @Override
        public boolean enabled() {
            return false;
        }
    };

    private Mc263IcebergFeature() {
    }

    /** Pure exact output closure for both iceberg configured features. */
    public static void preflight(Predicate<String> supportsState) {
        Mc263LakeFeature.requireOutputs(supportsState, AIR, WATER, ICE, PACKED_ICE, BLUE_ICE,
                SNOW_BLOCK, SNOW);
    }

    /** Complete live decoration-region view used by the placed and configured feature. */
    public interface WorldAccess {
        int minGenerationY();

        int generationDepth();

        String biomeKey(int blockX, int blockY, int blockZ);

        String blockState(int blockX, int blockY, int blockZ);

        /** Direct bounded worldgen write; false means the real region boundary clipped it. */
        boolean trySetBlockState(int blockX, int blockY, int blockZ, String state);
    }

    /** Numeric event stream shared with the Rust leaf. */
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

        default void record(String phase, long value0, long value1, long value2, long value3,
                long value4) {
            if (enabled()) record(phase, new long[]{value0, value1, value2, value3, value4});
        }

        default void record(String phase, long value0, long value1, long value2, long value3,
                long value4, long value5, long value6, long value7, long value8, long value9) {
            if (enabled()) {
                record(phase, new long[]{value0, value1, value2, value3, value4, value5, value6,
                        value7, value8, value9});
            }
        }
    }

    public record BlockPos(int x, int y, int z) {
    }

    public record PlacementResult(long decorationSeed, long featureSeed, int globalIndex,
                                  int candidateCount, int placedIcebergCount,
                                  int attemptedWrites, int writtenBlocks) {
        public PlacementResult {
            if (globalIndex < 0 || candidateCount < 0 || candidateCount > 1
                    || placedIcebergCount < 0 || placedIcebergCount > candidateCount
                    || attemptedWrites < 0 || writtenBlocks < 0
                    || writtenBlocks > attemptedWrites) {
                throw new IllegalArgumentException("invalid iceberg placement result");
            }
        }
    }

    public record PlacementCounts(int candidates, int icebergs, int attemptedWrites,
                                  int writtenBlocks) {
        public PlacementCounts {
            if (candidates < 0 || candidates > 1 || icebergs < 0 || icebergs > candidates
                    || attemptedWrites < 0 || writtenBlocks < 0
                    || writtenBlocks > attemptedWrites) {
                throw new IllegalArgumentException("invalid iceberg placement counts");
            }
        }
    }

    /** Vanilla lower bound of both height draws: nextInt(15) + 3 and nextInt(6) + 6. */
    private static final int MIN_OVER_WATER_HEIGHT = 3;
    /** Vanilla caps min(overWaterHeight + nextInt(11), 18) and min(... , 11). */
    private static final int MAX_UNDER_WATER_HEIGHT = 18;
    private static final int MAX_WIDTH = 11;
    /** MIN_OVER_WATER_HEIGHT + 0 - (5 - 1): the smallest width vanilla can emit. */
    private static final int MIN_WIDTH = MIN_OVER_WATER_HEIGHT - 4;

    public record ConfiguredResult(boolean snowOnTop, boolean ellipse, boolean cutOut,
                                   int overWaterHeight, int underWaterHeight, int width,
                                   int attemptedWrites, int writtenBlocks) {
        public ConfiguredResult {
            // Vanilla IcebergFeature bounds, not clamped shapes: the official leaf computes
            // width = min(overWaterHeight + nextInt(7) - nextInt(5), 11) with no lower clamp, so a
            // legitimate short iceberg (overWaterHeight 3, +0, -4) yields width -1 and places
            // nothing. Rejecting negative width rejected an authentic vanilla result.
            if (overWaterHeight < MIN_OVER_WATER_HEIGHT
                    || underWaterHeight < MIN_OVER_WATER_HEIGHT
                    || underWaterHeight > MAX_UNDER_WATER_HEIGHT
                    || width < MIN_WIDTH || width > MAX_WIDTH
                    || attemptedWrites < 0 || writtenBlocks < 0
                    || writtenBlocks > attemptedWrites) {
                throw new IllegalArgumentException("invalid configured iceberg result");
            }
        }
    }

    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            String placedFeatureKey, WorldAccess world) {
        return place(worldSeed, sourceBlockX, sourceBlockZ, placedFeatureKey, world, NO_TRACE);
    }

    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            String placedFeatureKey, WorldAccess world, TraceSink trace) {
        requireWorld(world);
        Spec spec = spec(placedFeatureKey);
        Mc263DecorationRandom.FeatureRandom seeded = Mc263DecorationRandom.forFeature(worldSeed,
                sourceBlockX, sourceBlockZ, LOCAL_MODIFICATIONS_STEP, spec.key());
        PlacementCounts counts = placeWithFeatureRandom(spec.key(), sourceBlockX, sourceBlockZ,
                world, seeded.random(), trace);
        return new PlacementResult(seeded.decorationSeed(), seeded.featureSeed(),
                seeded.globalIndex(), counts.candidates(), counts.icebergs(),
                counts.attemptedWrites(), counts.writtenBlocks());
    }

    /** Runs rarity, in-square and biome modifiers, then the configured feature on one stream. */
    public static PlacementCounts placeWithFeatureRandom(String placedFeatureKey,
            int sourceBlockX, int sourceBlockZ, WorldAccess world, WorldgenRandom random,
            TraceSink trace) {
        requireWorld(world);
        if (random == null) throw new IllegalArgumentException("random is required");
        TraceSink events = requireTrace(trace);
        Spec spec = spec(placedFeatureKey);
        int index = Mc263DecorationRandom.globalIndex(LOCAL_MODIFICATIONS_STEP, spec.key());
        if (index != spec.globalIndex()
                || !Mc263FeatureIndexReceipt.step(LOCAL_MODIFICATIONS_STEP)
                .featureAt(index).equals(spec.key())) {
            throw new IllegalStateException("pinned iceberg feature index changed");
        }

        boolean accepted = random.nextFloat() < 1.0f / (float) spec.rarity();
        events.record("rarity", spec.rarity(), accepted ? 1L : 0L);
        if (!accepted) return new PlacementCounts(0, 0, 0, 0);
        int x = sourceBlockX + random.nextInt(16);
        int z = sourceBlockZ + random.nextInt(16);
        int y = world.minGenerationY();
        events.record("candidate", x, y, z);
        boolean biomeAccepted = biomeContains(world.biomeKey(x, y, z), spec.key());
        events.record("biome", biomeAccepted ? 1L : 0L);
        if (!biomeAccepted) return new PlacementCounts(1, 0, 0, 0);

        ConfiguredResult configured = placeConfigured(random, new BlockPos(x, y, z),
                spec.mainState(), world, events);
        events.record("placed_result", index, configured.attemptedWrites(),
                configured.writtenBlocks());
        return new PlacementCounts(1, 1, configured.attemptedWrites(),
                configured.writtenBlocks());
    }

    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            String mainState, WorldAccess world) {
        return placeConfigured(random, origin, mainState, world, NO_TRACE);
    }

    /** Exact pinned {@code IcebergFeature.place}; the official leaf always returns true. */
    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos suppliedOrigin,
            String mainState, WorldAccess world, TraceSink trace) {
        requireWorld(world);
        if (random == null || suppliedOrigin == null) {
            throw new IllegalArgumentException("random and origin are required");
        }
        String main = blockKey(mainState);
        if (!main.equals(PACKED_ICE) && !main.equals(BLUE_ICE)) {
            throw new IllegalArgumentException("iceberg state must be packed_ice or blue_ice");
        }
        TraceSink events = requireTrace(trace);
        BlockPos origin = new BlockPos(suppliedOrigin.x(), SEA_LEVEL, suppliedOrigin.z());
        boolean snowOnTop = random.nextDouble() > 0.7;
        double shapeAngle = random.nextDouble() * 2.0 * Math.PI;
        int shapeEllipseA = 11 - random.nextInt(5);
        int shapeEllipseC = 3 + random.nextInt(3);
        boolean ellipse = random.nextDouble() > 0.7;
        int overWaterHeight = ellipse ? random.nextInt(6) + 6 : random.nextInt(15) + 3;
        boolean tall = false;
        if (!ellipse && random.nextDouble() > 0.9) {
            overWaterHeight += random.nextInt(19) + 7;
            tall = true;
        }
        int underWaterHeight = Math.min(overWaterHeight + random.nextInt(11), 18);
        int width = Math.min(overWaterHeight + random.nextInt(7) - random.nextInt(5), 11);
        int a = ellipse ? shapeEllipseA : 11;
        events.record("shape", snowOnTop ? 1L : 0L, Double.doubleToRawLongBits(shapeAngle),
                shapeEllipseA, shapeEllipseC, ellipse ? 1L : 0L, tall ? 1L : 0L,
                overWaterHeight, underWaterHeight, width, a);

        WriteCounter writes = new WriteCounter();
        for (int xo = -a; xo < a; xo++) {
            for (int zo = -a; zo < a; zo++) {
                for (int yOff = 0; yOff < overWaterHeight; yOff++) {
                    int radius = ellipse
                            ? heightDependentRadiusEllipse(yOff, overWaterHeight, width)
                            : heightDependentRadiusRound(random, yOff, overWaterHeight, width);
                    if (!ellipse && xo >= radius) continue;
                    generateIcebergBlock(world, random, origin, overWaterHeight, xo, yOff, zo,
                            radius, a, ellipse, shapeEllipseC, shapeAngle, snowOnTop, main,
                            writes, events);
                }
            }
        }
        smooth(world, origin, width, overWaterHeight, ellipse, shapeEllipseA, writes, events);
        for (int xo = -a; xo < a; xo++) {
            for (int zo = -a; zo < a; zo++) {
                for (int yOff = -1; yOff > -underWaterHeight; yOff--) {
                    int newA = ellipse
                            ? ceil((float) a * (1.0f - (float) Math.pow(yOff, 2.0)
                            / ((float) underWaterHeight * 8.0f)))
                            : a;
                    int radius = heightDependentRadiusSteep(random, -yOff, underWaterHeight,
                            width);
                    if (xo >= radius) continue;
                    generateIcebergBlock(world, random, origin, underWaterHeight, xo, yOff, zo,
                            radius, newA, ellipse, shapeEllipseC, shapeAngle, snowOnTop, main,
                            writes, events);
                }
            }
        }
        boolean cutOut = ellipse ? random.nextDouble() > 0.1 : random.nextDouble() > 0.7;
        events.record("cutout", cutOut ? 1L : 0L);
        if (cutOut) {
            generateCutOut(random, world, width, overWaterHeight, origin, ellipse, shapeEllipseA,
                    shapeAngle, shapeEllipseC, writes, events);
        }
        events.record("configured_result", writes.attempted, writes.written);
        return new ConfiguredResult(snowOnTop, ellipse, cutOut, overWaterHeight,
                underWaterHeight, width, writes.attempted, writes.written);
    }

    private static void generateCutOut(WorldgenRandom random, WorldAccess world, int width,
            int height, BlockPos origin, boolean ellipse, int ellipseA, double shapeAngle,
            int ellipseC, WriteCounter writes, TraceSink trace) {
        int signX = random.nextBoolean() ? -1 : 1;
        int signZ = random.nextBoolean() ? -1 : 1;
        int xOff = random.nextInt(Math.max(width / 2 - 2, 1));
        if (random.nextBoolean()) {
            xOff = width / 2 + 1 - random.nextInt(Math.max(width - width / 2 - 1, 1));
        }
        int zOff = random.nextInt(Math.max(width / 2 - 2, 1));
        if (random.nextBoolean()) {
            zOff = width / 2 + 1 - random.nextInt(Math.max(width - width / 2 - 1, 1));
        }
        if (ellipse) xOff = zOff = random.nextInt(Math.max(ellipseA - 5, 1));
        BlockPos localOrigin = new BlockPos(signX * xOff, 0, signZ * zOff);
        double angle = ellipse ? shapeAngle + Math.PI / 2.0 : random.nextDouble() * 2.0 * Math.PI;
        trace.record("cutout_shape", localOrigin.x(), localOrigin.z(),
                Double.doubleToRawLongBits(angle));
        for (int yOff = 0; yOff < height - 3; yOff++) {
            int radius = heightDependentRadiusRound(random, yOff, height, width);
            carve(radius, yOff, origin, world, false, angle, localOrigin, ellipseA, ellipseC,
                    writes, trace);
        }
        // nextInt(5) is intentionally re-evaluated for every loop-condition check.
        for (int yOff = -1; yOff > -height + random.nextInt(5); yOff--) {
            int radius = heightDependentRadiusSteep(random, -yOff, height, width);
            carve(radius, yOff, origin, world, true, angle, localOrigin, ellipseA, ellipseC,
                    writes, trace);
        }
    }

    private static void carve(int radius, int yOff, BlockPos origin, WorldAccess world,
            boolean underWater, double angle, BlockPos localOrigin, int ellipseA, int ellipseC,
            WriteCounter writes, TraceSink trace) {
        int a = radius + 1 + ellipseA / 3;
        int c = Math.min(radius - 3, 3) + ellipseC / 2 - 1;
        for (int xo = -a; xo < a; xo++) {
            for (int zo = -a; zo < a; zo++) {
                if (signedDistanceEllipse(xo, zo, localOrigin, a, c, angle) >= 0.0) continue;
                BlockPos pos = offset(origin, xo, yOff, zo);
                String state = blockKey(world.blockState(pos.x(), pos.y(), pos.z()));
                if (!isIcebergState(state) && !state.equals(SNOW_BLOCK)) continue;
                set(world, pos, underWater ? WATER : AIR, writes, trace);
                if (!underWater) removeFloatingSnowLayer(world, pos, writes, trace);
            }
        }
    }

    private static void removeFloatingSnowLayer(WorldAccess world, BlockPos pos,
            WriteCounter writes, TraceSink trace) {
        BlockPos above = offset(pos, 0, 1, 0);
        if (blockKey(world.blockState(above.x(), above.y(), above.z())).equals(SNOW)) {
            set(world, above, AIR, writes, trace);
        }
    }

    private static void generateIcebergBlock(WorldAccess world, WorldgenRandom random,
            BlockPos origin, int height, int xo, int yOff, int zo, int radius, int a,
            boolean ellipse, int ellipseC, double angle, boolean snowOnTop, String mainState,
            WriteCounter writes, TraceSink trace) {
        double signedDistance = ellipse
                ? signedDistanceEllipse(xo, zo, new BlockPos(0, 0, 0), a,
                getEllipseC(yOff, height, ellipseC), angle)
                : signedDistanceCircle(xo, zo, radius, random);
        if (signedDistance >= 0.0) return;
        double compare = ellipse ? -0.5 : -6 - random.nextInt(3);
        if (signedDistance > compare && random.nextDouble() > 0.9) return;
        setIcebergBlock(offset(origin, xo, yOff, zo), world, random, height - yOff, height,
                ellipse, snowOnTop, mainState, writes, trace);
    }

    private static void setIcebergBlock(BlockPos pos, WorldAccess world, WorldgenRandom random,
            int heightDifference, int height, boolean ellipse, boolean snowOnTop, String mainState,
            WriteCounter writes, TraceSink trace) {
        String state = blockKey(world.blockState(pos.x(), pos.y(), pos.z()));
        if (!isAir(state) && !state.equals(SNOW_BLOCK) && !state.equals(ICE)
                && !state.equals(WATER)) return;
        boolean randomness = !ellipse || random.nextDouble() > 0.05;
        int divisor = ellipse ? 3 : 2;
        if (snowOnTop && !state.equals(WATER)
                && (double) heightDifference <= random.nextInt(Math.max(1, height / divisor))
                + (double) height * 0.6 && randomness) {
            set(world, pos, SNOW_BLOCK, writes, trace);
        } else {
            set(world, pos, mainState, writes, trace);
        }
    }

    private static void smooth(WorldAccess world, BlockPos origin, int width, int height,
            boolean ellipse, int ellipseA, WriteCounter writes, TraceSink trace) {
        int a = ellipse ? ellipseA : width / 2;
        for (int x = -a; x <= a; x++) {
            for (int z = -a; z <= a; z++) {
                for (int y = 0; y <= height; y++) {
                    BlockPos pos = offset(origin, x, y, z);
                    String state = blockKey(world.blockState(pos.x(), pos.y(), pos.z()));
                    if (!isIcebergState(state) && !state.equals(SNOW)) continue;
                    BlockPos below = offset(pos, 0, -1, 0);
                    if (isAir(blockKey(world.blockState(below.x(), below.y(), below.z())))) {
                        set(world, pos, AIR, writes, trace);
                        set(world, offset(pos, 0, 1, 0), AIR, writes, trace);
                        continue;
                    }
                    if (!isIcebergState(state)) continue;
                    int exposed = 0;
                    if (!isIcebergState(blockKey(world.blockState(pos.x() - 1, pos.y(), pos.z())))) exposed++;
                    if (!isIcebergState(blockKey(world.blockState(pos.x() + 1, pos.y(), pos.z())))) exposed++;
                    if (!isIcebergState(blockKey(world.blockState(pos.x(), pos.y(), pos.z() - 1)))) exposed++;
                    if (!isIcebergState(blockKey(world.blockState(pos.x(), pos.y(), pos.z() + 1)))) exposed++;
                    if (exposed >= 3) set(world, pos, AIR, writes, trace);
                }
            }
        }
    }

    private static int getEllipseC(int yOff, int height, int ellipseC) {
        int c = ellipseC;
        if (yOff > 0 && height - yOff <= 3) c -= 4 - (height - yOff);
        return c;
    }

    private static double signedDistanceCircle(int x, int z, int radius,
            WorldgenRandom random) {
        float sample = random.nextFloat();
        float clamped = Math.max(0.2f, Math.min(0.8f, sample));
        float offset = 10.0f * clamped / (float) radius;
        return (double) offset + Math.pow(x, 2.0) + Math.pow(z, 2.0)
                - Math.pow(radius, 2.0);
    }

    private static double signedDistanceEllipse(int x, int z, BlockPos localOrigin, int a,
            int c, double angle) {
        return Math.pow(((double) (x - localOrigin.x()) * Math.cos(angle)
                - (double) (z - localOrigin.z()) * Math.sin(angle)) / (double) a, 2.0)
                + Math.pow(((double) (x - localOrigin.x()) * Math.sin(angle)
                + (double) (z - localOrigin.z()) * Math.cos(angle)) / (double) c, 2.0) - 1.0;
    }

    private static int heightDependentRadiusRound(WorldgenRandom random, int yOff, int height,
            int width) {
        float k = 3.5f - random.nextFloat();
        float scale = (1.0f - (float) Math.pow(yOff, 2.0) / ((float) height * k)) * width;
        if (height > 15 + random.nextInt(5)) {
            int adjustedY = yOff < 3 + random.nextInt(6) ? yOff / 2 : yOff;
            scale = (1.0f - (float) adjustedY / ((float) height * k * 0.4f)) * width;
        }
        return ceil(scale / 2.0f);
    }

    private static int heightDependentRadiusEllipse(int yOff, int height, int width) {
        float scale = (1.0f - (float) Math.pow(yOff, 2.0) / (float) height) * width;
        return ceil(scale / 2.0f);
    }

    private static int heightDependentRadiusSteep(WorldgenRandom random, int yOff, int height,
            int width) {
        float k = 1.0f + random.nextFloat() / 2.0f;
        float scale = (1.0f - (float) yOff / ((float) height * k)) * width;
        return ceil(scale / 2.0f);
    }

    private static int ceil(float value) {
        return (int) Math.ceil(value);
    }

    private static boolean isIcebergState(String state) {
        return state.equals(PACKED_ICE) || state.equals(SNOW_BLOCK) || state.equals(BLUE_ICE);
    }

    private static boolean isAir(String state) {
        return state.equals(AIR) || state.equals("minecraft:cave_air")
                || state.equals("minecraft:void_air");
    }

    private static void set(WorldAccess world, BlockPos pos, String state, WriteCounter writes,
            TraceSink trace) {
        writes.attempted++;
        boolean written = world.trySetBlockState(pos.x(), pos.y(), pos.z(), state);
        if (written) writes.written++;
        trace.record("write", pos.x(), pos.y(), pos.z(), stateCode(state), written ? 1L : 0L);
    }

    private static int stateCode(String state) {
        return switch (state) {
            case AIR -> 0;
            case WATER -> 1;
            case PACKED_ICE -> 2;
            case BLUE_ICE -> 3;
            case SNOW_BLOCK -> 4;
            default -> throw new IllegalArgumentException("untraced iceberg state: " + state);
        };
    }

    private static BlockPos offset(BlockPos pos, int x, int y, int z) {
        return new BlockPos(pos.x() + x, pos.y() + y, pos.z() + z);
    }

    private static boolean biomeContains(String biomeKey, String featureKey) {
        return Mc263FeatureIndexReceipt.biome(requireMinecraftKey(biomeKey))
                .featuresAtStep(LOCAL_MODIFICATIONS_STEP).stream()
                .anyMatch(reference -> reference.featureKey().equals(featureKey));
    }

    private static Spec spec(String key) {
        String feature = requireMinecraftKey(key);
        return switch (feature) {
            case ICEBERG_PACKED -> new Spec(feature, ICEBERG_PACKED_GLOBAL_INDEX, 16, PACKED_ICE);
            case ICEBERG_BLUE -> new Spec(feature, ICEBERG_BLUE_GLOBAL_INDEX, 200, BLUE_ICE);
            default -> throw new IllegalArgumentException("unsupported iceberg placed feature: " + key);
        };
    }

    private static String blockKey(String state) {
        if (state == null) throw new IllegalArgumentException("block state is required");
        int properties = state.indexOf('[');
        return requireMinecraftKey(properties < 0 ? state : state.substring(0, properties));
    }

    private static String requireMinecraftKey(String key) {
        if (key == null || !key.startsWith("minecraft:") || key.length() <= 10) {
            throw new IllegalArgumentException("pinned Minecraft key is required: " + key);
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

    private record Spec(String key, int globalIndex, int rarity, String mainState) {
    }

    private static final class WriteCounter {
        private int attempted;
        private int written;
    }
}
