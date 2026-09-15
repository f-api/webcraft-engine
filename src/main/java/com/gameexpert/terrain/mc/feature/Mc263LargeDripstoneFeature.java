package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.util.function.Predicate;
import java.util.Set;

/**
 * Exact, inactive Minecraft Java 26.3-snapshot-7 large-dripstone placed-feature leaf.
 *
 * <p>The supplied world is a live FEATURES decoration-region view. Direct writes may be rejected
 * only at that view's real write boundary. In particular, the stalagmite surface guard reads the
 * live {@code WORLD_SURFACE_WG} heightmap after earlier writes. No canonical generator calls this
 * class until the complete FEATURES pipeline is promoted.</p>
 */
public final class Mc263LargeDripstoneFeature {
    public static final int LOCAL_MODIFICATIONS_STEP = 2;
    public static final int LARGE_DRIPSTONE_GLOBAL_INDEX = 3;
    public static final String LARGE_DRIPSTONE = "minecraft:large_dripstone";
    public static final String DRIPSTONE_BLOCK = "minecraft:dripstone_block";
    public static final String DRIPSTONE_REPLACEABLE_BLOCKS =
            "minecraft:dripstone_replaceable_blocks";
    public static final String BASE_STONE_OVERWORLD = "minecraft:base_stone_overworld";

    /** Pure exact output closure for large-dripstone placement. */
    public static void preflight(Predicate<String> supportsState) {
        Mc263LakeFeature.requireOutputs(supportsState, DRIPSTONE_BLOCK);
    }

    public static final String INNER_SERVER_SHA1 =
            "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String LARGE_DRIPSTONE_FEATURE_CLASS_SHA256 =
            "83462687d6bef5c5dc917607eb53174760d9ceb1a9b15b910da2e687be9a2c02";
    public static final String SPELEOTHEM_UTILS_CLASS_SHA256 =
            "c5488d74ef7d6c38ba5b34284f8fdaaf094011c4ab7446d900aec8585baf338e";
    public static final String COLUMN_CLASS_SHA256 =
            "15bb2a159727298287efa28922b615e4a641ace5a4b6946fe803d7dfe0b19467";
    public static final String CONFIGURED_FEATURE_SHA256 =
            "d83e48c8767ab15388d5e445ec0d4a206a3c6bbe28a85f3b1aed8b32e9d059f6";
    public static final String PLACED_FEATURE_SHA256 =
            "03031835bf080076a39b4d2ccdf1677274fbb1269359f8dbd0cecb48b21b5fa1";
    public static final String DRIPSTONE_REPLACEABLE_BLOCKS_TAG_SHA256 =
            "69333efdca32f71c9dcee137cf6ffbec22607ee5619d381ea1c68f60efa49aea";
    public static final String BASE_STONE_OVERWORLD_TAG_SHA256 =
            "dbc51469f7d53ba1abd680a073c2cc25ba98985f0028404f1119e141dad0218f";

    private static final int COUNT_MIN = 10;
    private static final int COUNT_MAX = 48;
    private static final int HEIGHT_MIN = -64;
    private static final int HEIGHT_MAX = 256;
    private static final int COLUMN_SEARCH_RANGE = 30;
    private static final int RADIUS_MIN = 3;
    private static final int RADIUS_MAX = 16;
    private static final float MAX_RADIUS_HEIGHT_RATIO = 0.33F;
    private static final int MIN_RADIUS_FOR_WIND = 4;
    private static final float MIN_BLUNTNESS_FOR_WIND = 0.6F;
    private static final double SIN_SCALE = 10_430.378350470453;
    private static final float[] MTH_SIN = makeMthSinTable();
    private static final Set<String> BASE_STONE_BLOCKS = Set.of(
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

    private Mc263LargeDripstoneFeature() {
    }

    /** Complete live view used by the placed modifiers and configured feature. */
    public interface WorldAccess {
        int minGenerationY();

        int generationDepth();

        String biomeKey(int blockX, int blockY, int blockZ);

        String blockState(int blockX, int blockY, int blockZ);

        /** Exact live {@code Heightmap.Types.WORLD_SURFACE_WG} height for this column. */
        int worldSurfaceWg(int blockX, int blockZ);

        /**
         * Performs one direct flag-2 worldgen write. Returns false and does nothing only when the
         * position lies outside this decoration view's actual write boundary.
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
                long value4, long value5, long value6, long value7, long value8) {
            if (enabled()) {
                record(phase, new long[]{value0, value1, value2, value3, value4, value5, value6,
                        value7, value8});
            }
        }
    }

    public record BlockPos(int x, int y, int z) {
    }

    public record PlacementResult(long decorationSeed, long featureSeed, int globalIndex,
                                  int candidateCount, int configuredSuccessCount,
                                  int attemptedBlockCount, int writtenBlockCount) {
        public PlacementResult {
            validateCounts(candidateCount, configuredSuccessCount,
                    attemptedBlockCount, writtenBlockCount);
        }
    }

    public record PlacementCounts(int candidates, int configuredSuccesses,
                                  int attemptedBlocks, int writtenBlocks) {
        public PlacementCounts {
            validateCounts(candidates, configuredSuccesses, attemptedBlocks, writtenBlocks);
        }
    }

    public record ConfiguredResult(boolean placed, int attemptedBlocks, int writtenBlocks) {
        public ConfiguredResult {
            if (attemptedBlocks < 0 || writtenBlocks < 0 || writtenBlocks > attemptedBlocks
                    || (!placed && (attemptedBlocks != 0 || writtenBlocks != 0))) {
                throw new IllegalArgumentException("invalid large-dripstone configured result");
            }
        }
    }

    /** Runs the independently seeded step-2/index-3 placed feature. */
    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world) {
        return place(worldSeed, sourceBlockX, sourceBlockZ, world, NO_TRACE);
    }

    /** Runs the independently seeded step-2/index-3 placed feature. */
    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world, TraceSink trace) {
        requireWorld(world);
        TraceSink events = requireTrace(trace);
        Mc263DecorationRandom.FeatureRandom seeded = Mc263DecorationRandom.forFeature(worldSeed,
                sourceBlockX, sourceBlockZ, LOCAL_MODIFICATIONS_STEP, LARGE_DRIPSTONE);
        PlacementCounts counts = placeWithFeatureRandom(sourceBlockX, sourceBlockZ, world,
                seeded.random(), events);
        return new PlacementResult(seeded.decorationSeed(), seeded.featureSeed(),
                seeded.globalIndex(), counts.candidates(), counts.configuredSuccesses(),
                counts.attemptedBlocks(), counts.writtenBlocks());
    }

    /** Executes the count, in-square, height-range, biome, and configured stages on one stream. */
    public static PlacementCounts placeWithFeatureRandom(int sourceBlockX, int sourceBlockZ,
            WorldAccess world, WorldgenRandom random, TraceSink trace) {
        requireWorld(world);
        if (random == null) throw new IllegalArgumentException("random is required");
        TraceSink events = requireTrace(trace);
        int globalIndex = Mc263DecorationRandom.globalIndex(
                LOCAL_MODIFICATIONS_STEP, LARGE_DRIPSTONE);
        if (globalIndex != LARGE_DRIPSTONE_GLOBAL_INDEX
                || !Mc263FeatureIndexReceipt.step(LOCAL_MODIFICATIONS_STEP)
                .featureAt(globalIndex).equals(LARGE_DRIPSTONE)) {
            throw new IllegalStateException("pinned large-dripstone feature index changed");
        }

        int count = random.nextInt(COUNT_MAX - COUNT_MIN + 1) + COUNT_MIN;
        events.record("count", COUNT_MAX, count);
        int successes = 0;
        int attemptedBlocks = 0;
        int writtenBlocks = 0;
        for (int attempt = 0; attempt < count; attempt++) {
            int x = sourceBlockX + random.nextInt(16);
            int z = sourceBlockZ + random.nextInt(16);
            int y = random.nextInt(HEIGHT_MAX - HEIGHT_MIN + 1) + HEIGHT_MIN;
            events.record("candidate", attempt, x, y, z);
            boolean biomeAccepted = biomeContains(world.biomeKey(x, y, z));
            events.record("biome", attempt, biomeAccepted ? 1L : 0L);
            if (!biomeAccepted) continue;

            ConfiguredResult configured = placeConfigured(random, new BlockPos(x, y, z), world,
                    events);
            if (configured.placed()) successes++;
            attemptedBlocks += configured.attemptedBlocks();
            writtenBlocks += configured.writtenBlocks();
        }
        events.record("placed_result", LARGE_DRIPSTONE_GLOBAL_INDEX, count, successes,
                writtenBlocks);
        return new PlacementCounts(count, successes, attemptedBlocks, writtenBlocks);
    }

    /** Executes the configured {@code LargeDripstoneFeature} after modifiers choose an origin. */
    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world) {
        return placeConfigured(random, origin, world, NO_TRACE);
    }

    /** Executes the configured {@code LargeDripstoneFeature} after modifiers choose an origin. */
    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world, TraceSink trace) {
        requireWorld(world);
        if (random == null || origin == null) {
            throw new IllegalArgumentException("random and origin are required");
        }
        TraceSink events = requireTrace(trace);
        boolean originAccepted = isEmptyOrWater(blockAt(world, origin));
        events.record("origin", originAccepted ? 1L : 0L);
        if (!originAccepted) {
            events.record("configured_result", 0L, 0L, 0L);
            return new ConfiguredResult(false, 0, 0);
        }

        ColumnRange range = scanColumn(world, origin);
        if (range == null) {
            events.record("column", 0L, 0L, 0L, 0L);
            events.record("configured_result", 0L, 0L, 0L);
            return new ConfiguredResult(false, 0, 0);
        }
        int caveHeight = range.height();
        events.record("column", 1L, range.floor(), range.ceiling(), caveHeight);
        if (caveHeight < 4) {
            events.record("configured_result", 0L, 0L, 0L);
            return new ConfiguredResult(false, 0, 0);
        }

        int radiusBasedOnHeight = (int) ((float) caveHeight * MAX_RADIUS_HEIGHT_RATIO);
        int maxRadius = clamp(radiusBasedOnHeight, RADIUS_MIN, RADIUS_MAX);
        int radius = random.nextInt(maxRadius - RADIUS_MIN + 1) + RADIUS_MIN;
        events.record("radius", caveHeight, radiusBasedOnHeight, maxRadius, radius);

        Dripstone stalactite = makeDripstone(
                new BlockPos(origin.x(), range.ceiling() - 1, origin.z()), false,
                radius, 0.3F, 0.9F, random, events);
        Dripstone stalagmite = makeDripstone(
                new BlockPos(origin.x(), range.floor() + 1, origin.z()), true,
                radius, 0.4F, 1.0F, random, events);

        Wind wind;
        if (stalactite.suitableForWind() && stalagmite.suitableForWind()) {
            float speed = randomBetween(random, 0.0F, 0.3F);
            float direction = randomBetween(random, 0.0F, (float) Math.PI);
            wind = new Wind(origin.y(), speed, direction, 16 - radius);
            events.record("wind", 1L, Float.floatToRawIntBits(speed),
                    Float.floatToRawIntBits(direction), 16L - radius);
        } else {
            wind = Wind.NO_WIND;
            events.record("wind", 0L, 0L, 0L, 0L);
        }

        boolean stalactiteEmbedded = stalactite.embed(world, wind);
        events.record("embed", 0L, stalactiteEmbedded ? 1L : 0L,
                stalactite.root.y(), stalactite.radius);
        boolean stalagmiteEmbedded = stalagmite.embed(world, wind);
        events.record("embed", 1L, stalagmiteEmbedded ? 1L : 0L,
                stalagmite.root.y(), stalagmite.radius);

        WriteCounts counts = new WriteCounts();
        if (stalactiteEmbedded) stalactite.placeBlocks(world, random, wind, events, counts);
        if (stalagmiteEmbedded) stalagmite.placeBlocks(world, random, wind, events, counts);
        events.record("configured_result", 1L, counts.attempted, counts.written);
        return new ConfiguredResult(true, counts.attempted, counts.written);
    }

    /** Exact floating-point formula in the pinned {@code SpeleothemUtils}. */
    public static double speleothemHeight(double distanceFromCenter, double radius,
            double scale, double bluntness) {
        double distance = Math.max(distanceFromCenter, bluntness);
        double normalized = distance / radius * 0.384;
        double part1 = 0.75 * Math.pow(normalized, 1.3333333333333333);
        double part2 = Math.pow(normalized, 0.6666666666666666);
        double part3 = 0.3333333333333333 * Math.log(normalized);
        double relativeHeight = scale * (part1 - part2 - part3);
        return Math.max(relativeHeight, 0.0) / 0.384 * radius;
    }

    private static Dripstone makeDripstone(BlockPos root, boolean pointingUp, int radius,
            float bluntnessMin, float bluntnessMax, WorldgenRandom random, TraceSink trace) {
        float bluntness = randomBetween(random, bluntnessMin, bluntnessMax);
        float scale = randomBetween(random, 0.4F, 2.0F);
        trace.record("shape", pointingUp ? 1L : 0L, root.y(), radius,
                Float.floatToRawIntBits(bluntness), Float.floatToRawIntBits(scale));
        return new Dripstone(root, pointingUp, radius, (double) bluntness, (double) scale);
    }

    private static ColumnRange scanColumn(WorldAccess world, BlockPos origin) {
        // Column.scan checks the origin again even though LargeDripstoneFeature already did so.
        if (!isEmptyOrWater(blockAt(world, origin))) return null;
        Integer ceiling = scanDirection(world, origin, 1);
        Integer floor = scanDirection(world, origin, -1);
        return ceiling == null || floor == null ? null : new ColumnRange(floor, ceiling);
    }

    private static Integer scanDirection(WorldAccess world, BlockPos origin, int direction) {
        int y = origin.y();
        for (int step = 1; step < COLUMN_SEARCH_RANGE
                && isEmptyOrWater(blockAt(world, origin.x(), y, origin.z())); step++) {
            y += direction;
        }
        return isValidColumnEdge(blockAt(world, origin.x(), y, origin.z())) ? y : null;
    }

    private static boolean isCircleMostlyEmbedded(WorldAccess world, BlockPos center, int radius) {
        if (isEmptyOrWaterOrLava(blockAt(world, center))) return false;
        float increment = 6.0F / (float) radius;
        for (float angle = 0.0F; angle < (float) Math.PI * 2.0F; angle += increment) {
            int dx = (int) (mthCos((double) angle) * (float) radius);
            int dz = (int) (mthSin((double) angle) * (float) radius);
            if (isEmptyOrWaterOrLava(blockAt(world,
                    center.x() + dx, center.y(), center.z() + dz))) {
                return false;
            }
        }
        return true;
    }

    private static float randomBetween(WorldgenRandom random, float min, float maxExclusive) {
        return random.nextFloat() * (maxExclusive - min) + min;
    }

    private static boolean biomeContains(String biomeKey) {
        String biome = requireMinecraftKey(biomeKey, "biome");
        if (!biome.equals("minecraft:dripstone_caves")) return false;
        return Mc263FeatureIndexReceipt.biome(biome)
                .featuresAtStep(LOCAL_MODIFICATIONS_STEP).stream()
                .anyMatch(reference -> reference.featureKey().equals(LARGE_DRIPSTONE)
                        && reference.globalIndex() == LARGE_DRIPSTONE_GLOBAL_INDEX);
    }

    private static boolean isValidColumnEdge(String block) {
        return block.equals(DRIPSTONE_BLOCK) || BASE_STONE_BLOCKS.contains(block)
                || block.equals("minecraft:lava");
    }

    private static boolean isBaseStone(String block) {
        return BASE_STONE_BLOCKS.contains(block);
    }

    private static boolean isEmptyOrWater(String block) {
        return isAir(block) || block.equals("minecraft:water");
    }

    private static boolean isEmptyOrWaterOrLava(String block) {
        return isEmptyOrWater(block) || block.equals("minecraft:lava");
    }

    private static boolean isAir(String block) {
        return block.equals("minecraft:air") || block.equals("minecraft:cave_air")
                || block.equals("minecraft:void_air");
    }

    private static String blockAt(WorldAccess world, BlockPos pos) {
        return blockAt(world, pos.x(), pos.y(), pos.z());
    }

    private static String blockAt(WorldAccess world, int x, int y, int z) {
        if (y < world.minGenerationY()
                || y >= world.minGenerationY() + world.generationDepth()) {
            return "minecraft:void_air";
        }
        String state = requireMinecraftKey(world.blockState(x, y, z), "block state");
        int properties = state.indexOf('[');
        return properties < 0 ? state : state.substring(0, properties);
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private static float[] makeMthSinTable() {
        float[] table = new float[65_536];
        for (int index = 0; index < table.length; index++) {
            table[index] = (float) Math.sin((double) index / SIN_SCALE);
        }
        return table;
    }

    private static float mthSin(double angle) {
        return MTH_SIN[(int) ((long) (angle * SIN_SCALE) & 0xffffL)];
    }

    private static float mthCos(double angle) {
        return MTH_SIN[(int) ((long) (angle * SIN_SCALE + 16_384.0) & 0xffffL)];
    }

    private static void validateCounts(int candidates, int successes, int attempted, int written) {
        if (candidates < 0 || successes < 0 || successes > candidates || attempted < 0
                || written < 0 || written > attempted) {
            throw new IllegalArgumentException("invalid large-dripstone placement counts");
        }
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

    private static String requireMinecraftKey(String key, String description) {
        if (key == null || !key.startsWith("minecraft:")
                || key.length() == "minecraft:".length()) {
            throw new IllegalArgumentException("exact minecraft namespaced " + description
                    + " is required: " + key);
        }
        return key;
    }

    private record ColumnRange(int floor, int ceiling) {
        private ColumnRange {
            if (ceiling < floor) throw new IllegalArgumentException("negative column range");
        }

        private int height() {
            return ceiling - floor - 1;
        }
    }

    private static final class WriteCounts {
        private int attempted;
        private int written;
    }

    private static final class Dripstone {
        private BlockPos root;
        private final boolean pointingUp;
        private int radius;
        private final double bluntness;
        private final double scale;

        private Dripstone(BlockPos root, boolean pointingUp, int radius,
                double bluntness, double scale) {
            this.root = root;
            this.pointingUp = pointingUp;
            this.radius = radius;
            this.bluntness = bluntness;
            this.scale = scale;
        }

        private boolean suitableForWind() {
            return radius >= MIN_RADIUS_FOR_WIND
                    && bluntness >= (double) MIN_BLUNTNESS_FOR_WIND;
        }

        private int heightAtRadius(float checkRadius) {
            return (int) speleothemHeight((double) checkRadius, (double) radius,
                    scale, bluntness);
        }

        private boolean embed(WorldAccess world, Wind wind) {
            while (radius > 1) {
                BlockPos candidateRoot = root;
                int maxTries = Math.min(10, heightAtRadius(0.0F));
                for (int attempt = 0; attempt < maxTries; attempt++) {
                    if (blockAt(world, candidateRoot).equals("minecraft:lava")) return false;
                    if (isCircleMostlyEmbedded(world, wind.offset(candidateRoot), radius)) {
                        root = candidateRoot;
                        return true;
                    }
                    candidateRoot = new BlockPos(candidateRoot.x(),
                            candidateRoot.y() + (pointingUp ? -1 : 1), candidateRoot.z());
                }
                radius /= 2;
            }
            return false;
        }

        private void placeBlocks(WorldAccess world, WorldgenRandom random, Wind wind,
                TraceSink trace, WriteCounts counts) {
            for (int dx = -radius; dx <= radius; dx++) {
                columnLoop:
                for (int dz = -radius; dz <= radius; dz++) {
                    float currentRadius = (float) Math.sqrt((float) (dx * dx + dz * dz));
                    if (currentRadius > (float) radius) continue;
                    int heightBefore = heightAtRadius(currentRadius);
                    if (heightBefore <= 0) continue;

                    float gate = random.nextFloat();
                    boolean shortened = (double) gate < 0.2;
                    float factor = 0.0F;
                    int height = heightBefore;
                    if (shortened) {
                        factor = randomBetween(random, 0.8F, 1.0F);
                        height = (int) ((float) height * factor);
                    }
                    trace.record("column_shape", pointingUp ? 1L : 0L, dx, dz,
                            Float.floatToRawIntBits(currentRadius), heightBefore,
                            Float.floatToRawIntBits(gate), shortened ? 1L : 0L,
                            Float.floatToRawIntBits(factor), height);

                    BlockPos pos = new BlockPos(root.x() + dx, root.y(), root.z() + dz);
                    boolean hasBeenOutOfStone = false;
                    int maxY = pointingUp
                            ? world.worldSurfaceWg(pos.x(), pos.z()) : Integer.MAX_VALUE;
                    for (int index = 0; index < height && pos.y() < maxY; index++) {
                        BlockPos adjusted = wind.offset(pos);
                        if (isEmptyOrWaterOrLava(blockAt(world, adjusted))) {
                            hasBeenOutOfStone = true;
                            counts.attempted++;
                            boolean written = world.trySetBlockState(adjusted.x(), adjusted.y(),
                                    adjusted.z(), DRIPSTONE_BLOCK);
                            if (written) counts.written++;
                            trace.record("write", pointingUp ? 1L : 0L, adjusted.x(),
                                    adjusted.y(), adjusted.z(), written ? 1L : 0L);
                        } else if (hasBeenOutOfStone
                                && isBaseStone(blockAt(world, adjusted))) {
                            continue columnLoop;
                        }
                        pos = new BlockPos(pos.x(), pos.y() + (pointingUp ? 1 : -1), pos.z());
                    }
                }
            }
        }
    }

    private static final class Wind {
        private static final Wind NO_WIND = new Wind();

        private final int originY;
        private final Double speedX;
        private final double speedZ;
        private final int maxOffset;

        private Wind(int originY, float speed, float direction, int maxOffset) {
            this.originY = originY;
            this.maxOffset = maxOffset;
            this.speedX = (double) (mthCos((double) direction) * speed);
            this.speedZ = (double) (mthSin((double) direction) * speed);
        }

        private Wind() {
            originY = 0;
            speedX = null;
            speedZ = 0.0;
            maxOffset = 0;
        }

        private BlockPos offset(BlockPos pos) {
            if (speedX == null) return pos;
            int dy = originY - pos.y();
            int dx = clamp((int) Math.floor(speedX * (double) dy), -maxOffset, maxOffset);
            int dz = clamp((int) Math.floor(speedZ * (double) dy), -maxOffset, maxOffset);
            return new BlockPos(pos.x() + dx, pos.y(), pos.z() + dz);
        }
    }
}
