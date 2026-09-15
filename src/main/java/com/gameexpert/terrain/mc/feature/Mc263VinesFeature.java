package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Exact dormant Minecraft 26.3-snapshot-7 step-9 index-94 vines leaf. */
public final class Mc263VinesFeature {
    public static final int STEP = 9;
    public static final int GLOBAL_INDEX = 94;
    public static final int TRACE_MAGIC = 0x56494e33; // VIN3
    public static final int TRACE_VERSION = 1;
    public static final String TRACE_SCHEMA = "mc263-vines-trace-v1";
    public static final String INNER_SERVER_SHA1 = "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String VINES_PLACED_JSON_SHA256 =
            "915c9105e386e467c8a8aaf933e26f985fb87192c3bb20db034557c37dce2355";
    public static final String VINES_CONFIGURED_JSON_SHA256 =
            "a65e7eea42f3a7873f08751838cddb01ae732c2a25f720128e8ad2363b164fc6";
    public static final String VINES_FEATURE_CLASS_SHA256 =
            "06328eae67519583c41bc8bb0f4d803e8f4198a2e7d98865ea364d2e70a01410";
    public static final String VINE_BLOCK_CLASS_SHA256 =
            "88f966578a70d26401804a365800344abf2de90c1236c503eb14e6034f90d944";
    public static final String MULTIFACE_BLOCK_CLASS_SHA256 =
            "3fabdc32385ef4b66f74c8be4723875d9d3ac4295d9be11958725fa6ebb33387";
    public static final String DIRECTION_CLASS_SHA256 =
            "ca70b20df3288188ee9afa0972e4f3f06b452c15fa6ae112bb241592e90c71ff";
    public static final String BIOME_FILTER_CLASS_SHA256 =
            "9af5f023e371c8541e5a6086e5c01693735e39d10ee918adfea4761fc231e38a";
    public static final String BIOME_MANAGER_CLASS_SHA256 =
            "c913670c9e8a398a0ca92521c38cf4646cdec235dcd207ee89b2c4456962e876";

    private static final List<Direction> ATTACHMENT_ORDER = List.of(
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);
    private static final List<State> OUTPUT_STATES = ATTACHMENT_ORDER.stream()
            .map(Mc263VinesFeature::vineFacing)
            .toList();
    private static final Map<String, PhaseSpec> PHASES = Map.ofEntries(
            Map.entry("outer", new PhaseSpec(1, 2)),
            Map.entry("rng_int", new PhaseSpec(2, 3)),
            Map.entry("square", new PhaseSpec(3, 5)),
            Map.entry("height", new PhaseSpec(4, 5)),
            Map.entry("biome", new PhaseSpec(5, 5)),
            Map.entry("read", new PhaseSpec(6, 6)),
            Map.entry("attach", new PhaseSpec(7, 7)),
            Map.entry("write", new PhaseSpec(8, 7)),
            Map.entry("configured_result", new PhaseSpec(9, 7)),
            Map.entry("placed_result", new PhaseSpec(10, 7)));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public boolean enabled() { return false; }
        @Override public void record(String phase, long... values) {
            throw new AssertionError("disabled vines trace emitted");
        }
    };

    private Mc263VinesFeature() { }

    /** Minimal exact live boundary for isEmptyBlock and multiface attachment shape facts. */
    public interface WorldAccess {
        /** The pinned seeded BiomeManager zoom result at this exact block candidate. */
        String biomeKey(int x, int y, int z);
        State blockState(int x, int y, int z);
        boolean supportsState(State state);
        boolean trySetBlockState(int x, int y, int z, State state, int flags);
    }

    public enum Direction {
        DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1),
        WEST(-1, 0, 0), EAST(1, 0, 0);

        final int dx, dy, dz;
        Direction(int dx, int dy, int dz) { this.dx = dx; this.dy = dy; this.dz = dz; }
        Direction opposite() {
            return switch (this) {
                case DOWN -> UP; case UP -> DOWN; case NORTH -> SOUTH;
                case SOUTH -> NORTH; case WEST -> EAST; case EAST -> WEST;
            };
        }
    }

    /** Exact identity/state plus air and directional full-support-or-collision facts. */
    public record State(String block, Map<String, String> properties, boolean air,
                        int supportOrCollisionFullMask) {
        public State {
            block = key(block);
            properties = Collections.unmodifiableMap(new TreeMap<>(properties));
            if ((supportOrCollisionFullMask & ~63) != 0) {
                throw new IllegalArgumentException("support mask must fit six directions");
            }
        }
        public static State air(String block) { return new State(block, Map.of(), true, 0); }
        public static State block(String block, int supportMask) {
            return new State(block, Map.of(), false, supportMask);
        }
        public boolean supportOrCollisionFull(Direction face) {
            return (supportOrCollisionFullMask & (1 << face.ordinal())) != 0;
        }
        public String canonical() {
            if (properties.isEmpty()) return block;
            return block + properties.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .reduce("[", (a, b) -> a.equals("[") ? a + b : a + "," + b) + "]";
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

    public static Result placeWithFeatureRandom(WorldgenRandom random,
                                                int sourceX, int sourceY, int sourceZ,
                                                WorldAccess world) {
        return placeWithFeatureRandom(random, sourceX, sourceY, sourceZ, world, NO_TRACE);
    }

    public static Result placeWithFeatureRandom(WorldgenRandom random,
                                                int sourceX, int sourceY, int sourceZ,
                                                WorldAccess world, TraceSink trace) {
        preflight(world);
        emit(trace, "outer", GLOBAL_INDEX, 127);
        int successes = 0, writes = 0, retained = 0, configuredCalls = 0;
        for (int candidate = 0; candidate < 127; candidate++) {
            int x = sourceX + nextInt(random, 16, trace);
            int z = sourceZ + nextInt(random, 16, trace);
            emit(trace, "square", GLOBAL_INDEX, candidate, x, sourceY, z);
            int y = 64 + nextInt(random, 37, trace);
            emit(trace, "height", GLOBAL_INDEX, candidate, x, z, y);
            String biome = key(world.biomeKey(x, y, z));
            boolean member = biomeContains(biome);
            emit(trace, "biome", GLOBAL_INDEX, x, y, z, member ? 1 : 0);
            if (!member) continue;
            configuredCalls++;
            Configured configured = configured(x, y, z, world, trace);
            if (configured.placed) successes++;
            writes += configured.writes;
            retained += configured.retained;
        }
        emit(trace, "placed_result", GLOBAL_INDEX, 127, configuredCalls, successes,
                writes, retained, successes > 0 ? 1 : 0);
        return new Result(successes > 0, 127, configuredCalls, successes, writes, retained);
    }

    public static Result placeConfigured(WorldgenRandom random, int x, int y, int z,
                                         WorldAccess world) {
        return placeConfigured(random, x, y, z, world, NO_TRACE);
    }

    public static Result placeConfigured(WorldgenRandom random, int x, int y, int z,
                                         WorldAccess world,
                                         TraceSink trace) {
        if (random == null) throw new IllegalArgumentException("random is required");
        preflight(world);
        Configured configured = configured(x, y, z, world, trace);
        return new Result(configured.placed, 1, 1, configured.placed ? 1 : 0,
                configured.writes, configured.retained);
    }

    private static Configured configured(int x, int y, int z, WorldAccess world,
                                         TraceSink trace) {
        State origin = read(world, x, y, z, trace);
        if (!origin.air) {
            emit(trace, "configured_result", GLOBAL_INDEX, 0, x, y, z, 0, 0);
            return new Configured(false, 0, 0);
        }
        for (Direction direction : ATTACHMENT_ORDER) {
            int nx = x + direction.dx, ny = y + direction.dy, nz = z + direction.dz;
            State neighbour = read(world, nx, ny, nz, trace);
            Direction queriedFace = direction.opposite();
            boolean acceptable = neighbour.supportOrCollisionFull(queriedFace);
            emit(trace, "attach", GLOBAL_INDEX, direction.ordinal(), nx, ny, nz,
                    queriedFace.ordinal(), acceptable ? 1 : 0);
            if (!acceptable) continue;
            State output = vineFacing(direction);
            boolean kept = write(world, x, y, z, output, trace);
            emit(trace, "configured_result", GLOBAL_INDEX, 1, x, y, z, 1,
                    kept ? 1 : 0);
            return new Configured(true, 1, kept ? 1 : 0);
        }
        emit(trace, "configured_result", GLOBAL_INDEX, 0, x, y, z, 0, 0);
        return new Configured(false, 0, 0);
    }

    public static byte[] encodeTraceFixture(List<TraceEvent> events) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(TRACE_MAGIC);
            out.writeShort(TRACE_VERSION);
            byte[] schema = TRACE_SCHEMA.getBytes(StandardCharsets.UTF_8);
            out.writeShort(schema.length); out.write(schema); out.writeInt(events.size());
            for (TraceEvent event : events) {
                PhaseSpec phase = PHASES.get(event.phase);
                if (phase == null || event.values.length != phase.arity) {
                    throw new IllegalArgumentException("bad VIN3 event");
                }
                out.writeByte(phase.id); out.writeByte(phase.arity);
                for (long value : event.values) out.writeLong(value);
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public static List<Integer> coveredIndices() { return List.of(GLOBAL_INDEX); }
    public static String featureKey() { return "minecraft:vines"; }

    /** Pure exact capability/state closure check for the placed feature. */
    public static void preflightPlaced(WorldAccess world) { preflight(world); }

    private static void preflight(WorldAccess world) {
        for (State output : OUTPUT_STATES) {
            if (!world.supportsState(output)) {
                throw new UnsupportedOperationException("unregistered exact vine state: "
                        + output.canonical());
            }
        }
    }
    private static boolean biomeContains(String biome) {
        return Mc263FeatureIndexReceipt.biome(biome).featuresAtStep(STEP).stream()
                .anyMatch(ref -> ref.globalIndex() == GLOBAL_INDEX
                        && ref.featureKey().equals(featureKey()));
    }
    private static State vineFacing(Direction face) {
        Map<String, String> properties = new TreeMap<>();
        for (Direction direction : ATTACHMENT_ORDER) {
            properties.put(direction.name().toLowerCase(), Boolean.toString(direction == face));
        }
        return new State("minecraft:vine", properties, false, 0);
    }
    private static State read(WorldAccess world, int x, int y, int z, TraceSink trace) {
        State state = world.blockState(x, y, z);
        emit(trace, "read", x, y, z, stateId(state), state.air ? 1 : 0,
                state.supportOrCollisionFullMask);
        return state;
    }
    private static boolean write(WorldAccess world, int x, int y, int z, State state,
                                 TraceSink trace) {
        boolean retained = world.trySetBlockState(x, y, z, state, 2);
        emit(trace, "write", x, y, z, stateId(state), 2, retained ? 1 : 0,
                state.properties.hashCode());
        return retained;
    }
    private static int nextInt(WorldgenRandom random, int bound, TraceSink trace) {
        int value = random.nextInt(bound);
        emit(trace, "rng_int", bound, value, 0);
        return value;
    }
    private static long stateId(State state) {
        long hash = 0xcbf29ce484222325L;
        for (byte value : state.canonical().getBytes(StandardCharsets.UTF_8)) {
            hash = (hash ^ (value & 255)) * 0x100000001b3L;
        }
        return hash;
    }
    private static String key(String value) {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("invalid key");
        }
        return value;
    }
    private static void emit(TraceSink trace, String phase, long a, long b) {
        if (trace.enabled()) trace.record(phase, a, b);
    }
    private static void emit(TraceSink trace, String phase, long a, long b, long c) {
        if (trace.enabled()) trace.record(phase, a, b, c);
    }
    private static void emit(TraceSink trace, String phase, long a, long b, long c, long d,
                             long e) {
        if (trace.enabled()) trace.record(phase, a, b, c, d, e);
    }
    private static void emit(TraceSink trace, String phase, long a, long b, long c, long d,
                             long e, long f) {
        if (trace.enabled()) trace.record(phase, a, b, c, d, e, f);
    }
    private static void emit(TraceSink trace, String phase, long a, long b, long c, long d,
                             long e, long f, long g) {
        if (trace.enabled()) trace.record(phase, a, b, c, d, e, f, g);
    }

    private record PhaseSpec(int id, int arity) { }
    private record Configured(boolean placed, int writes, int retained) { }
}
