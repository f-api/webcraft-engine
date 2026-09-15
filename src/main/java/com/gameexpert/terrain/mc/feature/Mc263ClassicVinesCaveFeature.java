package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Exact dormant 26.3 step-9 index-36 classic cave VinesFeature. */
public final class Mc263ClassicVinesCaveFeature {
    public static final int STEP = 9;
    public static final int GLOBAL_INDEX = 36;
    public static final String FEATURE = "minecraft:classic_vines_cave_feature";
    public static final int TRACE_MAGIC = 0x43564933; // CVI3
    public static final int TRACE_VERSION = 1;
    public static final String TRACE_SCHEMA = "mc263-classic-vines-cave-trace-v1";
    public static final String VINES_FEATURE_CLASS_SHA256 =
            "06328eae67519583c41bc8bb0f4d803e8f4198a2e7d98865ea364d2e70a01410";
    public static final String PLACED_JSON_SHA256 =
            "c0f420d80cd8af8849eeee9ee45a1e7f1443ea431577e815fc9abdd5c2f091a9";
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public boolean enabled() { return false; }
        @Override public void record(String phase, long... values) {
            throw new AssertionError("disabled classic-vines trace emitted");
        }
    };

    private Mc263ClassicVinesCaveFeature() { }

    public enum Direction {
        DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1),
        WEST(-1, 0, 0), EAST(1, 0, 0);
        final int dx, dy, dz;
        Direction(int dx, int dy, int dz) { this.dx = dx; this.dy = dy; this.dz = dz; }
        Direction opposite() { return switch (this) {
            case DOWN -> UP; case UP -> DOWN; case NORTH -> SOUTH; case SOUTH -> NORTH;
            case WEST -> EAST; case EAST -> WEST;
        }; }
    }
    private static final Direction[] ORDER = Direction.values();

    public interface WorldAccess {
        int minGenerationY();
        String biomeKey(int x, int y, int z);
        State blockState(int x, int y, int z);
        boolean supportsClassicVines();
        boolean supportsState(State state);
        boolean trySetBlockState(int x, int y, int z, State state, int flags);
    }
    public record State(String block, Map<String, String> properties, boolean air,
                        int acceptableNeighbourMask) {
        public State { key(block); properties = Map.copyOf(properties); }
        public static State airState() {
            return new State("minecraft:air", Map.of(), true, 0);
        }
        public boolean acceptable(Direction face) {
            return (acceptableNeighbourMask & 1 << face.ordinal()) != 0;
        }
    }
    public interface TraceSink {
        default boolean enabled() { return true; }
        void record(String phase, long... values);
    }
    public record TraceEvent(String phase, long[] values) {
        public TraceEvent { values = values.clone(); }
        @Override public long[] values() { return values.clone(); }
    }
    public record Result(boolean placed, int candidates, int configuredCalls,
                         int configuredSuccesses, int attemptedWrites, int retainedWrites) { }

    public static Result placeWithFeatureRandom(WorldgenRandom random, int sourceX, int sourceY,
                                                int sourceZ, WorldAccess world) {
        return placeWithFeatureRandom(random, sourceX, sourceY, sourceZ, world, NO_TRACE);
    }
    public static Result placeWithFeatureRandom(WorldgenRandom random, int sourceX, int sourceY,
                                                int sourceZ, WorldAccess world, TraceSink trace) {
        if (random == null) throw new IllegalArgumentException("random is required");
        preflight(world);
        int calls = 0, successes = 0, writes = 0, retained = 0;
        for (int attempt = 0; attempt < 256; attempt++) {
            int x = sourceX + drawInt(random, 16, trace);
            int z = sourceZ + drawInt(random, 16, trace);
            int y = world.minGenerationY()
                    + drawInt(random, 256 - world.minGenerationY() + 1, trace);
            boolean biome = biomeContains(world.biomeKey(x, y, z));
            emit(trace, "biome", x, y, z, biome ? 1 : 0);
            if (!biome) continue;
            calls++;
            Counts configured = configured(world, x, y, z, trace);
            if (configured.success) successes++;
            writes += configured.writes; retained += configured.retained;
        }
        emit(trace, "result", 256, calls, successes, writes, retained);
        return new Result(successes > 0, 256, calls, successes, writes, retained);
    }
    public static Result placeConfigured(WorldgenRandom random, int x, int y, int z,
                                         WorldAccess world, TraceSink trace) {
        if (random == null) throw new IllegalArgumentException("random is required");
        preflight(world);
        Counts configured = configured(world, x, y, z, trace);
        return new Result(configured.success, 1, 1, configured.success ? 1 : 0,
                configured.writes, configured.retained);
    }

    private static Counts configured(WorldAccess world, int x, int y, int z, TraceSink trace) {
        if (!read(world, x, y, z, trace).air) return new Counts(false, 0, 0);
        for (Direction direction : ORDER) {
            if (direction == Direction.DOWN) continue;
            State neighbour = read(world, x + direction.dx, y + direction.dy,
                    z + direction.dz, trace);
            if (!neighbour.acceptable(direction.opposite())) continue;
            State vine = new State("minecraft:vine",
                    Map.of(direction.name().toLowerCase(), "true"), false, 0);
            if (!world.supportsState(vine)) throw new IllegalStateException("missing vine state");
            boolean retained = world.trySetBlockState(x, y, z, vine, 2);
            emit(trace, "write", x, y, z, direction.ordinal(), retained ? 1 : 0);
            return new Counts(true, 1, retained ? 1 : 0);
        }
        return new Counts(false, 0, 0);
    }

    public static byte[] encodeTraceFixture(List<TraceEvent> events) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(TRACE_MAGIC); out.writeShort(TRACE_VERSION);
            byte[] schema = TRACE_SCHEMA.getBytes(StandardCharsets.UTF_8);
            out.writeShort(schema.length); out.write(schema); out.writeInt(events.size());
            for (TraceEvent event : events) {
                byte[] phase = event.phase.getBytes(StandardCharsets.UTF_8);
                out.writeByte(phase.length); out.write(phase); out.writeByte(event.values.length);
                for (long value : event.values) out.writeLong(value);
            }
            out.flush(); return bytes.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }

    /** Pure exact capability/state closure check for the placed feature. */
    public static void preflight(WorldAccess world) {
        if (world == null) throw new IllegalArgumentException("world is required");
        if (!world.supportsClassicVines()) throw new IllegalStateException("missing classic-vines capability");
        for (Direction direction : ORDER) {
            if (direction == Direction.DOWN) continue;
            State state = new State("minecraft:vine",
                    Map.of(direction.name().toLowerCase(), "true"), false, 0);
            if (!world.supportsState(state)) {
                throw new IllegalStateException("missing vine state: " + direction);
            }
        }
    }
    private static State read(WorldAccess world, int x, int y, int z, TraceSink trace) {
        State state = world.blockState(x, y, z);
        if (state == null) throw new IllegalStateException("missing exact state fact");
        emit(trace, "read", x, y, z, state.block.hashCode(), state.air ? 1 : 0);
        return state;
    }
    private static int drawInt(WorldgenRandom random, int bound, TraceSink trace) {
        int value = random.nextInt(bound); emit(trace, "rng_int", bound, value); return value;
    }
    private static boolean biomeContains(String biome) {
        return Mc263FeatureIndexReceipt.biome(key(biome)).featuresAtStep(STEP).stream()
                .anyMatch(ref -> ref.globalIndex() == GLOBAL_INDEX && ref.featureKey().equals(FEATURE));
    }
    private static void emit(TraceSink trace, String phase, long... values) {
        if (trace != null && trace.enabled()) trace.record(phase, values);
    }
    private static String key(String key) {
        if (key == null || !key.matches("minecraft:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("invalid key: " + key);
        return key;
    }
    private record Counts(boolean success, int writes, int retained) { }
}
