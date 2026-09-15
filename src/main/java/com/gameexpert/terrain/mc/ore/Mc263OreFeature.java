package com.gameexpert.terrain.mc.ore;

import com.gameexpert.terrain.mc.feature.Mc263FeatureIndexReceipt;
import com.gameexpert.terrain.mc.feature.Mc263LakeFeature;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.util.BitSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * Executable placed-ore leaf for Minecraft Java 26.3-snapshot-7.
 *
 * <p>This deliberately has no dependency on the canonical chunk generator. The supplied world
 * must expose the complete decoration region: clipping reads or writes to the target 16x16 chunk
 * is not part of the official algorithm and can change later exposure-test draws.</p>
 */
public final class Mc263OreFeature {
    /** Exact flags passed by the pinned {@code OreFeature} block write. */
    public static final int SET_BLOCK_FLAGS = 2;
    private static final float PI = (float) Math.PI;
    private static final double SIN_SCALE = 10_430.378350470453;
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override
        public void record(String phase, long... values) {
            throw new AssertionError("disabled ore trace emitted");
        }

        @Override
        public boolean enabled() {
            return false;
        }
    };

    private Mc263OreFeature() {
    }

    /** Pure exact replacement-state closure for one placed ore wrapper. */
    public static void preflight(Mc263OreCatalog.PlacedFeature placed,
            Predicate<String> supportsState) {
        if (placed == null) throw new IllegalArgumentException("placed feature is required");
        for (Mc263OreCatalog.OreTarget target : Mc263OreCatalog
                .configuredOre(placed.configuredKey()).orderedTargets()) {
            Mc263LakeFeature.requireOutputs(supportsState, target.replacementState());
        }
    }

    public interface WorldAccess {
        int minGenerationY();

        int generationDepth();

        int oceanFloorWg(int blockX, int blockZ);

        String biomeKey(int blockX, int blockY, int blockZ);

        String blockState(int blockX, int blockY, int blockZ);

        /**
         * The property-free block key at this position.
         *
         * <p>Every read in the {@code OreFeature} inner loop — the target test and the six
         * air-exposure probes — consumes only this key, never the property spelling. A world
         * that already holds a resolved state hands its precomputed key over instead of making
         * this leaf scan the exact-state string for {@code '['} once per probed cell. The default
         * derives it exactly as before, so an implementation that does not override this is
         * unchanged.</p>
         */
        default String blockKey(int blockX, int blockY, int blockZ) {
            return Mc263OreCatalog.blockKeyUnchecked(blockState(blockX, blockY, blockZ));
        }

        /** Mirrors {@code WorldGenLevel.ensureCanWrite}; this must not be target-chunk clipping. */
        boolean ensureCanWrite(int blockX, int blockY, int blockZ);

        boolean trySetBlockState(int blockX, int blockY, int blockZ, String state, int flags);

        default boolean isOutsideBuildHeight(int blockY) {
            return blockY < minGenerationY()
                    || blockY >= minGenerationY() + generationDepth();
        }

        default boolean isAir(int blockX, int blockY, int blockZ) {
            String block = blockKey(blockX, blockY, blockZ);
            return block.equals("minecraft:air") || block.equals("minecraft:cave_air")
                    || block.equals("minecraft:void_air");
        }
    }

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
                                  int candidateCount, int placedBlockCount) {
        public boolean placedAny() {
            return placedBlockCount > 0;
        }
    }

    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            int step, String placedFeatureKey, WorldAccess world) {
        return place(worldSeed, sourceBlockX, sourceBlockZ, step, placedFeatureKey, world,
                NO_TRACE);
    }

    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            int step, String placedFeatureKey, WorldAccess world, TraceSink trace) {
        Mc263OreCatalog.PlacedFeature placed = Mc263OreCatalog.placedFeature(placedFeatureKey);
        Mc263DecorationRandom.FeatureRandom seeded = Mc263DecorationRandom.forFeature(
                worldSeed, sourceBlockX, sourceBlockZ, step, placed.key());
        PlacementCounts counts = placeWithFeatureRandom(placed, step, sourceBlockX, sourceBlockZ,
                world, seeded.random(), requireTrace(trace));
        return new PlacementResult(seeded.decorationSeed(), seeded.featureSeed(),
                seeded.globalIndex(), counts.candidates(), counts.blocks());
    }

    /**
     * Executes all four placement modifiers and the configured feature on one shared stream.
     * This overload is exposed for exact stream composition and parity probes.
     */
    public static PlacementCounts placeWithFeatureRandom(Mc263OreCatalog.PlacedFeature placed,
            int step, int sourceBlockX, int sourceBlockZ, WorldAccess world, WorldgenRandom random,
            TraceSink trace) {
        requireWorld(world);
        if (placed == null || random == null) {
            throw new IllegalArgumentException("placed feature and random are required");
        }
        TraceSink events = requireTrace(trace);
        String namespacedKey = "minecraft:" + placed.key();
        int globalIndex = Mc263DecorationRandom.globalIndex(step, namespacedKey);
        if (!Mc263FeatureIndexReceipt.step(step).featureAt(globalIndex).equals(namespacedKey)) {
            throw new IllegalStateException("feature-index receipt changed during placement");
        }

        int count;
        if (placed.frequencyKind() == Mc263OreCatalog.FrequencyKind.RARITY_FILTER) {
            boolean accepted = random.nextFloat() < 1.0f / placed.frequencyMin();
            emit(events, "rarity", placed.frequencyMin(), accepted ? 1L : 0L);
            count = accepted ? 1 : 0;
        } else {
            count = placed.sampleCount((min, max) -> nextIntInclusive(random, min, max));
            emit(events, "count", count);
        }

        Mc263OreCatalog.ConfiguredOre configured =
                Mc263OreCatalog.configuredOre(placed.configuredKey());
        int placedBlocks = 0;
        for (int attempt = 0; attempt < count; attempt++) {
            int x = sourceBlockX + random.nextInt(16);
            int z = sourceBlockZ + random.nextInt(16);
            int y = placed.sampleHeight((min, max) -> nextIntInclusive(random, min, max),
                    world.minGenerationY(), world.generationDepth());
            emit(events, "candidate", attempt, x, y, z);
            if (!biomeContains(world.biomeKey(x, y, z), step, namespacedKey)) {
                emit(events, "biome_reject", attempt, x, y, z);
                continue;
            }
            placedBlocks += placeConfigured(configured, random, new BlockPos(x, y, z), world,
                    events);
        }
        return new PlacementCounts(count, placedBlocks);
    }

    /** Executes the official {@code OreFeature} after placement modifiers have selected an origin. */
    public static int placeConfigured(Mc263OreCatalog.ConfiguredOre configured, WorldgenRandom random,
            BlockPos origin, WorldAccess world, TraceSink trace) {
        requireWorld(world);
        if (configured == null || random == null || origin == null) {
            throw new IllegalArgumentException("configured feature, random, and origin are required");
        }
        TraceSink events = requireTrace(trace);
        int size = configured.size();
        float direction = random.nextFloat() * PI;
        float spreadXz = (float) size / 8.0f;
        int maxRadius = (int) Math.ceil(((float) size / 16.0f * 2.0f + 1.0f) / 2.0f);
        double x0 = (double) origin.x() + Math.sin(direction) * (double) spreadXz;
        double x1 = (double) origin.x() - Math.sin(direction) * (double) spreadXz;
        double z0 = (double) origin.z() + Math.cos(direction) * (double) spreadXz;
        double z1 = (double) origin.z() - Math.cos(direction) * (double) spreadXz;
        double y0 = origin.y() + random.nextInt(3) - 2;
        double y1 = origin.y() + random.nextInt(3) - 2;
        int xStart = origin.x() - (int) Math.ceil(spreadXz) - maxRadius;
        int yStart = origin.y() - 2 - maxRadius;
        int zStart = origin.z() - (int) Math.ceil(spreadXz) - maxRadius;
        int sizeXz = 2 * ((int) Math.ceil(spreadXz) + maxRadius);
        int sizeY = 2 * (2 + maxRadius);
        emit(events, "shape", Float.floatToRawIntBits(direction),
                Double.doubleToRawLongBits(y0), Double.doubleToRawLongBits(y1),
                xStart, yStart, zStart, sizeXz, sizeY);

        for (int xProbe = xStart; xProbe <= xStart + sizeXz; xProbe++) {
            for (int zProbe = zStart; zProbe <= zStart + sizeXz; zProbe++) {
                if (yStart > world.oceanFloorWg(xProbe, zProbe)) {
                    continue;
                }
                return doPlace(configured, random, world, events, x0, x1, z0, z1, y0, y1,
                        xStart, yStart, zStart, sizeXz, sizeY);
            }
        }
        emit(events, "ocean_floor_reject", xStart, yStart, zStart, sizeXz, sizeY);
        return 0;
    }

    public record PlacementCounts(int candidates, int blocks) {
        public PlacementCounts {
            if (candidates < 0 || blocks < 0) {
                throw new IllegalArgumentException("placement counts cannot be negative");
            }
        }
    }

    private static int doPlace(Mc263OreCatalog.ConfiguredOre configured, WorldgenRandom random,
            WorldAccess world, TraceSink trace, double x0, double x1, double z0, double z1,
            double y0, double y1, int xStart, int yStart, int zStart, int sizeXz, int sizeY) {
        int size = configured.size();
        BitSet tested = new BitSet(sizeXz * sizeY * sizeXz);
        double[] data = new double[size * 4];
        for (int i = 0; i < size; i++) {
            float step = (float) i / (float) size;
            double x = lerp((double) step, x0, x1);
            double y = lerp((double) step, y0, y1);
            double z = lerp((double) step, z0, z1);
            double scale = random.nextDouble() * (double) size / 16.0;
            double radius = ((double) (minecraftSin(PI * step) + 1.0f) * scale + 1.0) / 2.0;
            int offset = i * 4;
            data[offset] = x;
            data[offset + 1] = y;
            data[offset + 2] = z;
            data[offset + 3] = radius;
            emit(trace, "sphere", i, Double.doubleToRawLongBits(x),
                    Double.doubleToRawLongBits(y), Double.doubleToRawLongBits(z),
                    Double.doubleToRawLongBits(radius));
        }

        for (int first = 0; first < size - 1; first++) {
            int firstOffset = first * 4;
            if (data[firstOffset + 3] <= 0.0) {
                continue;
            }
            for (int second = first + 1; second < size; second++) {
                int secondOffset = second * 4;
                if (data[secondOffset + 3] <= 0.0) {
                    continue;
                }
                double radiusDelta = data[firstOffset + 3] - data[secondOffset + 3];
                double xDelta = data[firstOffset] - data[secondOffset];
                double yDelta = data[firstOffset + 1] - data[secondOffset + 1];
                double zDelta = data[firstOffset + 2] - data[secondOffset + 2];
                if (radiusDelta * radiusDelta <= xDelta * xDelta + yDelta * yDelta
                        + zDelta * zDelta) {
                    continue;
                }
                if (radiusDelta > 0.0) {
                    data[secondOffset + 3] = -1.0;
                } else {
                    data[firstOffset + 3] = -1.0;
                }
            }
        }

        int placed = 0;
        List<Mc263OreCatalog.OreTarget> ordered = configured.orderedTargets();
        if (ordered.size() > Integer.SIZE) {
            throw new IllegalStateException("ordered ore targets exceed one match word: "
                    + ordered.size());
        }
        // The ordered targets are read once per probed cell; copying them into an array once per
        // placement keeps that read monomorphic instead of an interface call into whichever
        // immutable list implementation the catalog handed back.
        Mc263OreCatalog.OreTarget[] targets =
                ordered.toArray(new Mc263OreCatalog.OreTarget[0]);
        for (int sphere = 0; sphere < size; sphere++) {
            int offset = sphere * 4;
            double radius = data[offset + 3];
            if (radius < 0.0) {
                continue;
            }
            double centerX = data[offset];
            double centerY = data[offset + 1];
            double centerZ = data[offset + 2];
            int xMin = Math.max((int) Math.floor(centerX - radius), xStart);
            int yMin = Math.max((int) Math.floor(centerY - radius), yStart);
            int zMin = Math.max((int) Math.floor(centerZ - radius), zStart);
            int xMax = Math.max((int) Math.floor(centerX + radius), xMin);
            int yMax = Math.max((int) Math.floor(centerY + radius), yMin);
            int zMax = Math.max((int) Math.floor(centerZ + radius), zMin);
            for (int x = xMin; x <= xMax; x++) {
                double xDistance = ((double) x + 0.5 - centerX) / radius;
                if (!(xDistance * xDistance < 1.0)) {
                    continue;
                }
                for (int y = yMin; y <= yMax; y++) {
                    double yDistance = ((double) y + 0.5 - centerY) / radius;
                    if (!(xDistance * xDistance + yDistance * yDistance < 1.0)) {
                        continue;
                    }
                    // AGENTS 10l: the target test is a pure function of the block key and this
                    // row's Y, and an ore body runs through long spans of one block key, so the
                    // ordered targets are tested once per distinct key in the row instead of once
                    // per cell. The word records every matching target, not just the first, so a
                    // cell whose first match is rejected for air exposure still falls through to
                    // the next target exactly as before; nothing that draws from the shared RNG
                    // stream is cached, so the stream is untouched. Y is the loop variable, so the
                    // cache cannot outlive the row it was decided in.
                    String cachedKey = null;
                    int cachedMatches = 0;
                    blockLoop:
                    for (int z = zMin; z <= zMax; z++) {
                        double zDistance = ((double) z + 0.5 - centerZ) / radius;
                        if (!(xDistance * xDistance + yDistance * yDistance
                                + zDistance * zDistance < 1.0) || world.isOutsideBuildHeight(y)) {
                            continue;
                        }
                        int bitIndex = x - xStart + (y - yStart) * sizeXz
                                + (z - zStart) * sizeXz * sizeY;
                        if (tested.get(bitIndex)) {
                            continue;
                        }
                        tested.set(bitIndex);
                        if (!world.ensureCanWrite(x, y, z)) {
                            continue;
                        }
                        String blockKey = world.blockKey(x, y, z);
                        if (blockKey != cachedKey) {
                            Mc263OreCatalog.requireBlockKey(blockKey);
                            int matches = 0;
                            for (int index = 0; index < targets.length; index++) {
                                if (targets[index].matchesCheckedBlockKey(blockKey, y)) {
                                    matches |= 1 << index;
                                }
                            }
                            cachedKey = blockKey;
                            cachedMatches = matches;
                        }
                        for (int targetIndex = 0; targetIndex < targets.length; targetIndex++) {
                            if ((cachedMatches & (1 << targetIndex)) == 0) {
                                continue;
                            }
                            Mc263OreCatalog.OreTarget target = targets[targetIndex];
                            boolean skipAirCheck = shouldSkipAirCheck(
                                    random, configured.discardChanceOnAirExposure());
                            boolean adjacentAir = !skipAirCheck && isAdjacentToAir(world, x, y, z);
                            emit(trace, "target", x, y, z, targetIndex,
                                    skipAirCheck ? 1L : 0L, adjacentAir ? 1L : 0L);
                            if (adjacentAir) {
                                continue;
                            }
                            // OreFeature deliberately ignores WorldGenLevel#setBlock's boolean:
                            // an eligible attempted replacement advances its placed accounting
                            // even when the bounded WorldGenRegion rejects the actual write.
                            world.trySetBlockState(x, y, z, target.replacementState(),
                                    SET_BLOCK_FLAGS);
                            emit(trace, "write", x, y, z, targetIndex);
                            placed++;
                            continue blockLoop;
                        }
                    }
                }
            }
        }
        return placed;
    }

    static boolean shouldSkipAirCheck(WorldgenRandom random, float discardChance) {
        if (discardChance <= 0.0f) {
            return true;
        }
        if (discardChance >= 1.0f) {
            return false;
        }
        return random.nextFloat() >= discardChance;
    }

    private static boolean isAdjacentToAir(WorldAccess world, int x, int y, int z) {
        return world.isAir(x, y - 1, z) || world.isAir(x, y + 1, z)
                || world.isAir(x, y, z - 1) || world.isAir(x, y, z + 1)
                || world.isAir(x - 1, y, z) || world.isAir(x + 1, y, z);
    }

    private static boolean biomeContains(String biomeKey, int step, String featureKey) {
        if (biomeKey == null || biomeKey.isBlank()) {
            throw new IllegalArgumentException("biome key is required");
        }
        String key = biomeKey.startsWith("minecraft:") ? biomeKey : "minecraft:" + biomeKey;
        List<Mc263FeatureIndexReceipt.FeatureReference> references =
                Mc263FeatureIndexReceipt.biome(key).featuresAtStep(step);
        for (int index = 0; index < references.size(); index++) {
            if (references.get(index).featureKey().equals(featureKey)) return true;
        }
        return false;
    }

    private static int nextIntInclusive(WorldgenRandom random, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("empty inclusive random range");
        }
        long width = (long) max - min + 1L;
        if (width > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("inclusive random range is too wide");
        }
        return min + random.nextInt((int) width);
    }

    private static float minecraftSin(double value) {
        int index = (int) ((long) (value * SIN_SCALE) & 0xffffL);
        return (float) Math.sin((double) index / SIN_SCALE);
    }

    private static double lerp(double factor, double first, double second) {
        return first + factor * (second - first);
    }

    private static void emit(TraceSink trace, String phase, long value0) {
        if (trace.enabled()) trace.record(phase, new long[]{value0});
    }

    private static void emit(TraceSink trace, String phase, long value0, long value1) {
        if (trace.enabled()) trace.record(phase, new long[]{value0, value1});
    }

    private static void emit(TraceSink trace, String phase, long value0, long value1,
            long value2, long value3) {
        if (trace.enabled()) trace.record(phase, new long[]{value0, value1, value2, value3});
    }

    private static void emit(TraceSink trace, String phase, long value0, long value1,
            long value2, long value3, long value4) {
        if (trace.enabled()) {
            trace.record(phase, new long[]{value0, value1, value2, value3, value4});
        }
    }

    private static void emit(TraceSink trace, String phase, long value0, long value1,
            long value2, long value3, long value4, long value5) {
        if (trace.enabled()) {
            trace.record(phase, new long[]{value0, value1, value2, value3, value4, value5});
        }
    }

    private static void emit(TraceSink trace, String phase, long value0, long value1,
            long value2, long value3, long value4, long value5, long value6, long value7) {
        if (trace.enabled()) {
            trace.record(phase, new long[]{value0, value1, value2, value3, value4, value5,
                    value6, value7});
        }
    }

    private static void requireWorld(WorldAccess world) {
        if (world == null || world.generationDepth() <= 0) {
            throw new IllegalArgumentException("positive-height world access is required");
        }
    }

    private static TraceSink requireTrace(TraceSink trace) {
        if (trace == null) {
            throw new IllegalArgumentException("trace sink is required");
        }
        return trace;
    }
}
