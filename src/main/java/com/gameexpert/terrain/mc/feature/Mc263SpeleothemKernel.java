package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Shared exact 26.3-snapshot-7 kernel for configured speleothem clusters and spikes. */
public final class Mc263SpeleothemKernel {
    public static final int SET_BLOCK_FLAGS = 2;
    public static final int TRACE_MAGIC = 0x53505433; // SPT3
    public static final int TRACE_VERSION = 1;
    public static final String TRACE_SCHEMA = "mc263-speleothem-trace-v1";

    private static final Set<String> BASE_STONE = Set.of("minecraft:stone",
            "minecraft:granite", "minecraft:diorite", "minecraft:andesite",
            "minecraft:tuff", "minecraft:deepslate");
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final Direction[] ENUM_DIRECTIONS = Direction.values();
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public void record(String phase, long... values) {
            throw new AssertionError("disabled speleothem trace emitted");
        }
        @Override public boolean enabled() { return false; }
    };

    private Mc263SpeleothemKernel() {
    }

    public static void preflight(Kind kind, Predicate<String> supportsState) {
        Mc263LakeFeature.requireOutputs(supportsState, kind.base);
        for (String thickness : List.of("tip_merge", "tip", "frustum", "middle", "base")) {
            for (String vertical : List.of("up", "down")) {
                for (boolean waterlogged : new boolean[]{false, true}) {
                    Mc263LakeFeature.requireOutputs(supportsState,
                            kind.pointed + "[thickness=" + thickness + ",vertical_direction="
                                    + vertical + ",waterlogged=" + waterlogged + "]");
                }
            }
        }
    }

    public enum Kind {
        SULFUR("minecraft:sulfur", "minecraft:sulfur_spike",
                "minecraft:sulfur_caves", 1, 4, true),
        DRIPSTONE("minecraft:dripstone_block", "minecraft:pointed_dripstone",
                "minecraft:dripstone_caves", 3, 6, false);

        public final String base;
        public final String pointed;
        public final String biome;
        final int minHeight;
        final int maxHeight;
        final boolean sulfur;

        Kind(String base, String pointed, String biome, int minHeight, int maxHeight,
                boolean sulfur) {
            this.base = base;
            this.pointed = pointed;
            this.biome = biome;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
            this.sulfur = sulfur;
        }
    }

    public enum Direction {
        DOWN(0, -1, 0, 1, "down"), UP(0, 1, 0, 0, "up"),
        NORTH(0, 0, -1, 3, "north"), SOUTH(0, 0, 1, 2, "south"),
        WEST(-1, 0, 0, 5, "west"), EAST(1, 0, 0, 4, "east");

        final int dx;
        final int dy;
        final int dz;
        final int opposite;
        final String key;

        Direction(int dx, int dy, int dz, int opposite, String key) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.opposite = opposite;
            this.key = key;
        }

        Direction opposite() { return ENUM_DIRECTIONS[opposite]; }
    }

    public record BlockPos(int x, int y, int z) {
        public BlockPos offset(int dx, int dy, int dz) {
            return new BlockPos(x + dx, y + dy, z + dz);
        }
        BlockPos relative(Direction direction) {
            return offset(direction.dx, direction.dy, direction.dz);
        }
        BlockPos atY(int newY) { return new BlockPos(x, newY, z); }
    }

    /** Atomic state/fluid facts returned by one official block-state query. */
    public record State(String exactState, boolean air, boolean solid, boolean waterFluid) {
        public State {
            if (exactState == null || !exactState.startsWith("minecraft:")) {
                throw new IllegalArgumentException("exact Minecraft state is required");
            }
        }
        public String block() {
            int properties = exactState.indexOf('[');
            return properties < 0 ? exactState : exactState.substring(0, properties);
        }
        public boolean exactWaterBlock() { return block().equals("minecraft:water"); }
        public boolean exactLavaBlock() { return block().equals("minecraft:lava"); }
        public boolean emptyOrWater() { return air || exactWaterBlock(); }
    }

    public interface WorldAccess {
        int minGenerationY();
        int generationDepth();
        String biomeKey(int x, int y, int z);
        State blockState(int x, int y, int z);
        boolean trySetBlockState(int x, int y, int z, String exactState, int flags);
    }

    @FunctionalInterface
    public interface TraceSink {
        void record(String phase, long... values);
        default boolean enabled() { return true; }
        static TraceSink disabled() { return NO_TRACE; }
    }

    public record TraceEvent(String phase, long[] values) {
        public TraceEvent {
            if (phase == null || phase.isEmpty() || values == null) {
                throw new IllegalArgumentException("valid trace event required");
            }
            values = values.clone();
        }
        @Override public long[] values() { return values.clone(); }
    }

    public record ConfiguredResult(boolean placed, int attemptedWrites, int retainedWrites) {
        public ConfiguredResult {
            if (attemptedWrites < 0 || retainedWrites < 0 || retainedWrites > attemptedWrites) {
                throw new IllegalArgumentException("invalid speleothem write counts");
            }
        }
    }

    public static ConfiguredResult placeCluster(Kind kind, WorldgenRandom random,
            BlockPos origin, WorldAccess world) {
        return placeCluster(kind, random, origin, world, NO_TRACE);
    }

    public static ConfiguredResult placeCluster(Kind kind, WorldgenRandom random,
            BlockPos origin, WorldAccess world, TraceSink trace) {
        require(kind, random, origin, world, trace);
        Counts counts = new Counts();
        State first = state(world, origin);
        if (trace.enabled()) trace.record("cluster_origin", first.emptyOrWater() ? 1 : 0);
        if (!first.emptyOrWater()) return new ConfiguredResult(false, 0, 0);

        int height = nextInclusive(random, kind.minHeight, kind.maxHeight);
        float wetness = kind.sulfur ? 0.0F
                : clamp(0.1F + (float) random.nextGaussian() * 0.3F, 0.1F, 0.9F);
        float density = 0.3F + random.nextFloat() * 0.4F;
        int xRadius = nextInclusive(random, 2, 8);
        int zRadius = nextInclusive(random, 2, 8);
        if (trace.enabled()) trace.record("cluster_shape", height,
                Float.floatToRawIntBits(wetness), Float.floatToRawIntBits(density),
                xRadius, zRadius);
        for (int dx = -xRadius; dx <= xRadius; dx++) {
            for (int dz = -zRadius; dz <= zRadius; dz++) {
                int edge = Math.min(xRadius - Math.abs(dx), zRadius - Math.abs(dz));
                double chance = clampedMapFloat(edge, 0.0F, 3.0F, 0.1F, 1.0F);
                placeClusterColumn(kind, random, origin.offset(dx, 0, dz), dx, dz,
                        wetness, chance, height, density, world, trace, counts);
            }
        }
        if (trace.enabled()) {
            trace.record("configured_result", 1, counts.attempted, counts.retained);
        }
        return new ConfiguredResult(true, counts.attempted, counts.retained);
    }

    public static ConfiguredResult placeSingle(Kind kind, WorldgenRandom random,
            BlockPos origin, WorldAccess world) {
        return placeSingle(kind, random, origin, world, NO_TRACE);
    }

    public static ConfiguredResult placeSingle(Kind kind, WorldgenRandom random,
            BlockPos origin, WorldAccess world, TraceSink trace) {
        require(kind, random, origin, world, trace);
        boolean above = isBase(kind, state(world, origin.relative(Direction.UP)));
        boolean below = isBase(kind, state(world, origin.relative(Direction.DOWN)));
        Direction direction;
        if (above && below) direction = random.nextBoolean() ? Direction.DOWN : Direction.UP;
        else if (above) direction = Direction.DOWN;
        else if (below) direction = Direction.UP;
        else {
            if (trace.enabled()) trace.record("single_direction", 0, 0, 0);
            return new ConfiguredResult(false, 0, 0);
        }
        if (trace.enabled()) {
            trace.record("single_direction", 1, direction.ordinal(), above ? 1 : 0,
                    below ? 1 : 0);
        }
        Counts counts = new Counts();
        BlockPos root = origin.relative(direction.opposite());
        placeBase(kind, world, root, trace, counts);
        for (Direction horizontal : HORIZONTAL) {
            if (random.nextFloat() > 0.7F) continue;
            BlockPos radius1 = root.relative(horizontal);
            placeBase(kind, world, radius1, trace, counts);
            if (random.nextFloat() > 0.5F) continue;
            BlockPos radius2 = radius1.relative(randomDirection(random));
            placeBase(kind, world, radius2, trace, counts);
            if (random.nextFloat() > 0.5F) continue;
            placeBase(kind, world, radius2.relative(randomDirection(random)), trace, counts);
        }
        int length = random.nextFloat() < 0.2F
                && state(world, origin.relative(direction)).emptyOrWater() ? 2 : 1;
        grow(kind, world, origin, direction, length, false, trace, counts);
        if (trace.enabled()) {
            trace.record("configured_result", 1, counts.attempted, counts.retained);
        }
        return new ConfiguredResult(true, counts.attempted, counts.retained);
    }

    /** Exact environment-scan modifier followed by its fixed offset. */
    public static BlockPos scanForSingle(WorldAccess world, BlockPos origin,
            Direction search, TraceSink trace) {
        BlockPos pos = origin;
        if (!scanAllowed(world, pos)) return null;
        for (int step = 0; step < 12; step++) {
            // EnvironmentScan performs a distinct target predicate query after the allowed
            // predicate query; do not reuse the prior state snapshot.
            if (state(world, pos).solid()) {
                BlockPos result = pos.relative(search.opposite());
                if (trace.enabled()) {
                    trace.record("scan_result", 1, search.ordinal(), result.x(), result.y(),
                            result.z());
                }
                return result;
            }
            pos = pos.relative(search);
            if (outside(world, pos.y())) return null;
            if (!scanAllowed(world, pos)) break;
        }
        if (state(world, pos).solid()) {
            BlockPos result = pos.relative(search.opposite());
            if (trace.enabled()) {
                trace.record("scan_result", 1, search.ordinal(), result.x(), result.y(),
                        result.z());
            }
            return result;
        }
        if (trace.enabled()) {
            trace.record("scan_result", 0, search.ordinal(), pos.x(), pos.y(), pos.z());
        }
        return null;
    }

    private static void placeClusterColumn(Kind kind, WorldgenRandom random, BlockPos pos,
            int dx, int dz, float wetness, double chance, int clusterHeight, float density,
            WorldAccess world, TraceSink trace, Counts counts) {
        Column column = scanColumn(world, pos, 12);
        if (column == null || column.floor == null && column.ceiling == null) return;
        Integer ceiling = column.ceiling;
        Integer floor = column.floor;
        if (random.nextFloat() < wetness && floor != null
                && canPlacePool(kind, world, pos.atY(floor))) {
            write(world, pos.atY(floor), "minecraft:water", trace, counts);
            floor--;
        }
        int stalactite = 0;
        if (random.nextDouble() < chance && ceiling != null
                && !state(world, pos.atY(ceiling)).exactLavaBlock()) {
            int thickness = nextInclusive(random, 2, 4);
            replaceBaseLine(kind, world, pos.atY(ceiling), thickness, Direction.UP,
                    trace, counts);
            int max = floor == null ? clusterHeight : Math.min(clusterHeight, ceiling - floor);
            stalactite = sampleHeight(random, dx, dz, density, max);
        }
        int stalagmite = 0;
        if (random.nextDouble() < chance && floor != null
                && !state(world, pos.atY(floor)).exactLavaBlock()) {
            int thickness = nextInclusive(random, 2, 4);
            replaceBaseLine(kind, world, pos.atY(floor), thickness, Direction.DOWN,
                    trace, counts);
            stalagmite = ceiling == null ? sampleHeight(random, dx, dz, density, clusterHeight)
                    : Math.max(0, stalactite + nextInclusive(random, -1, 1));
        }
        int actualDown = stalactite;
        int actualUp = stalagmite;
        if (ceiling != null && floor != null && ceiling - stalactite <= floor + stalagmite) {
            int low = Math.max(ceiling - stalactite, floor + 1);
            int high = Math.min(floor + stalagmite, ceiling - 1);
            int bottom = nextInclusive(random, low, high + 1);
            actualDown = ceiling - bottom;
            actualUp = bottom - 1 - floor;
        }
        boolean merge = random.nextBoolean() && actualDown > 0 && actualUp > 0
                && ceiling != null && floor != null
                && actualDown + actualUp == ceiling - floor - 1;
        if (ceiling != null) grow(kind, world, pos.atY(ceiling - 1), Direction.DOWN,
                actualDown, merge, trace, counts);
        if (floor != null) grow(kind, world, pos.atY(floor + 1), Direction.UP,
                actualUp, merge, trace, counts);
    }

    private static Column scanColumn(WorldAccess world, BlockPos origin, int range) {
        if (!state(world, origin).emptyOrWater()) return null;
        Integer ceiling = scanEdge(world, origin, range, Direction.UP);
        Integer floor = scanEdge(world, origin, range, Direction.DOWN);
        return new Column(floor, ceiling);
    }

    private static Integer scanEdge(WorldAccess world, BlockPos origin, int range,
            Direction direction) {
        BlockPos pos = origin;
        for (int i = 1; i < range && state(world, pos).emptyOrWater(); i++) {
            pos = pos.relative(direction);
        }
        return !state(world, pos).emptyOrWater() ? pos.y() : null;
    }

    private static boolean canPlacePool(Kind kind, WorldAccess world, BlockPos floor) {
        State floorState = state(world, floor);
        if (floorState.exactWaterBlock() || floorState.block().equals(kind.base)
                || floorState.block().equals(kind.pointed)) return false;
        if (state(world, floor.relative(Direction.UP)).waterFluid()) return false;
        for (Direction direction : HORIZONTAL) {
            if (!adjacentToWater(state(world, floor.relative(direction)))) return false;
        }
        return adjacentToWater(state(world, floor.relative(Direction.DOWN)));
    }

    private static boolean adjacentToWater(State state) {
        return BASE_STONE.contains(state.block()) || state.waterFluid();
    }

    private static void replaceBaseLine(Kind kind, WorldAccess world, BlockPos first,
            int count, Direction direction, TraceSink trace, Counts counts) {
        BlockPos pos = first;
        for (int i = 0; i < count; i++) {
            if (!placeBase(kind, world, pos, trace, counts)) return;
            pos = pos.relative(direction);
        }
    }

    private static boolean placeBase(Kind kind, WorldAccess world, BlockPos pos,
            TraceSink trace, Counts counts) {
        if (!replaceable(kind, state(world, pos).block())) return false;
        write(world, pos, kind.base, trace, counts);
        return true;
    }

    private static void grow(Kind kind, WorldAccess world, BlockPos start, Direction direction,
            int length, boolean merge, TraceSink trace, Counts counts) {
        if (!isBase(kind, state(world, start.relative(direction.opposite())))) return;
        BlockPos pos = start;
        if (length >= 3) {
            writePointed(kind, world, pos, direction, "base", trace, counts);
            pos = pos.relative(direction);
            for (int i = 0; i < length - 3; i++) {
                writePointed(kind, world, pos, direction, "middle", trace, counts);
                pos = pos.relative(direction);
            }
        }
        if (length >= 2) {
            writePointed(kind, world, pos, direction, "frustum", trace, counts);
            pos = pos.relative(direction);
        }
        if (length >= 1) {
            writePointed(kind, world, pos, direction, merge ? "tip_merge" : "tip",
                    trace, counts);
        }
    }

    private static void writePointed(Kind kind, WorldAccess world, BlockPos pos,
            Direction direction, String thickness, TraceSink trace, Counts counts) {
        boolean waterlogged = state(world, pos).waterFluid();
        String exact = kind.pointed + "[thickness=" + thickness + ",vertical_direction="
                + direction.key + ",waterlogged=" + waterlogged + "]";
        write(world, pos, exact, trace, counts);
    }

    private static void write(WorldAccess world, BlockPos pos, String exact, TraceSink trace,
            Counts counts) {
        counts.attempted++;
        boolean retained = world.trySetBlockState(pos.x(), pos.y(), pos.z(), exact,
                SET_BLOCK_FLAGS);
        if (retained) counts.retained++;
        if (trace.enabled()) {
            trace.record("write", pos.x(), pos.y(), pos.z(), stateCode(exact),
                    retained ? 1 : 0);
        }
    }

    private static int sampleHeight(WorldgenRandom random, int dx, int dz, float density,
            int maxHeight) {
        if (random.nextFloat() > density) return 0;
        int distance = Math.abs(dx) + Math.abs(dz);
        float mean = (float) clampedMap(distance, 0.0, 8.0, maxHeight / 2.0, 0.0);
        float sampled = clamp(mean + (float) random.nextGaussian() * 3.0F, 0.0F, maxHeight);
        return (int) sampled;
    }

    private static boolean isBase(Kind kind, State state) {
        return state.block().equals(kind.base) || replaceable(kind, state.block());
    }

    private static boolean replaceable(Kind kind, String block) {
        return kind.sulfur ? block.equals("minecraft:sulfur") || block.equals("minecraft:cinnabar")
                : BASE_STONE.contains(block);
    }

    private static boolean scanAllowed(WorldAccess world, BlockPos pos) {
        // AnyOfPredicate invokes two independent StateTestingPredicate queries. Preserve its
        // short-circuit and do not coalesce the non-air water query into one world read.
        if (state(world, pos).air()) return true;
        return state(world, pos).exactWaterBlock();
    }

    private static Direction randomDirection(WorldgenRandom random) {
        return ENUM_DIRECTIONS[random.nextInt(ENUM_DIRECTIONS.length)];
    }

    private static State state(WorldAccess world, BlockPos pos) {
        if (outside(world, pos.y())) return new State("minecraft:void_air", true, false, false);
        State state = world.blockState(pos.x(), pos.y(), pos.z());
        if (state == null) throw new IllegalArgumentException("null world state");
        return state;
    }

    private static boolean outside(WorldAccess world, int y) {
        return y < world.minGenerationY()
                || y >= world.minGenerationY() + world.generationDepth();
    }

    private static int nextInclusive(WorldgenRandom random, int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }

    private static float clamp(float value, float min, float max) {
        return Math.min(Math.max(value, min), max);
    }

    private static double clampedMap(double value, double fromMin, double fromMax,
            double toMin, double toMax) {
        double factor = (value - fromMin) / (fromMax - fromMin);
        if (factor < 0.0) return toMin;
        if (factor > 1.0) return toMax;
        return toMin + factor * (toMax - toMin);
    }

    private static float clampedMapFloat(float value, float fromMin, float fromMax,
            float toMin, float toMax) {
        float factor = (value - fromMin) / (fromMax - fromMin);
        if (factor < 0.0F) return toMin;
        if (factor > 1.0F) return toMax;
        return toMin + factor * (toMax - toMin);
    }

    /** Stable language-neutral code used only by the parity trace. */
    private static long stateCode(String exact) {
        if (exact.equals("minecraft:water")) return 1;
        if (exact.equals("minecraft:sulfur")) return 2;
        if (exact.equals("minecraft:dripstone_block")) return 3;
        boolean sulfur = exact.startsWith("minecraft:sulfur_spike[");
        boolean dripstone = exact.startsWith("minecraft:pointed_dripstone[");
        if (!sulfur && !dripstone) return 0;
        int thickness = exact.contains("thickness=tip_merge") ? 1
                : exact.contains("thickness=tip,") ? 0
                : exact.contains("thickness=frustum") ? 2
                : exact.contains("thickness=middle") ? 3 : 4;
        int direction = exact.contains("vertical_direction=up") ? 1 : 0;
        int waterlogged = exact.contains("waterlogged=true") ? 1 : 0;
        return 16L + (dripstone ? 32L : 0L) + thickness * 4L + direction * 2L
                + waterlogged;
    }

    private static void require(Kind kind, WorldgenRandom random, BlockPos origin,
            WorldAccess world, TraceSink trace) {
        if (kind == null || random == null || origin == null || world == null || trace == null
                || world.minGenerationY() != -64 || world.generationDepth() != 384) {
            throw new IllegalArgumentException("pinned speleothem inputs required");
        }
    }

    public static byte[] encodeTrace(List<TraceEvent> events) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(TRACE_MAGIC);
            out.writeInt(TRACE_VERSION);
            out.writeInt(events.size());
            for (TraceEvent event : events) {
                byte[] phase = event.phase().getBytes(StandardCharsets.UTF_8);
                out.writeInt(phase.length);
                out.write(phase);
                out.writeInt(event.values().length);
                for (long value : event.values()) out.writeLong(value);
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static TraceSink collecting(List<TraceEvent> events) {
        if (events == null) throw new IllegalArgumentException("events required");
        return (phase, values) -> events.add(new TraceEvent(phase, values));
    }

    private record Column(Integer floor, Integer ceiling) {
    }

    private static final class Counts {
        int attempted;
        int retained;
    }
}
