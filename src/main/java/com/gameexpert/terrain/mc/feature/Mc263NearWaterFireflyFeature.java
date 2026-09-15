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
import java.util.regex.Pattern;

/** Exact dormant Minecraft 26.3-snapshot-7 step-9 near-water firefly-bush pair. */
public final class Mc263NearWaterFireflyFeature {
    /** The pinned namespaced-key grammar, compiled once: {@code String.matches} recompiles
     * this pattern on every call, and the key validator runs once per read state. */
    private static final Pattern RESOURCE_KEY =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public static final int STEP = 9;
    public static final int TRACE_MAGIC = 0x4e574633; // NWF3
    public static final int TRACE_VERSION = 1;
    public static final String TRACE_SCHEMA = "mc263-near-water-firefly-trace-v1";
    public static final String INNER_SERVER_SHA1 = "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String FIREFLY_CONFIGURED_JSON_SHA256 =
            "59c19189f252f3831b5147655ea96460dd3e5a18f43fc1dba33a9f228c8c88cd";
    public static final String NEAR_WATER_JSON_SHA256 =
            "cce58b0609a7ec8968a5dc57a3c26109613cd29ef7b69934645ad85136fcabef";
    public static final String NEAR_WATER_SWAMP_JSON_SHA256 =
            "0e8dead59fd64494df21f5fe4519dd9cb6f93821e15a20a838a1cb8138f7522e";
    public static final String SIMPLE_BLOCK_CLASS_SHA256 =
            "10745682a08452d5e5f148cfd627fa1ec84bf669570da5cd284ee2119e810101";
    public static final String SIMPLE_STATE_PROVIDER_CLASS_SHA256 =
            "72deffe747b0324a88691eeabce09e324a37dbc23a8b1136c0a7763d25c44e08";
    public static final String HEIGHTMAP_PLACEMENT_CLASS_SHA256 =
            "ab73361bbb5b2ed3e5083b9518a6b10725629291f9fc17e7ba951d11f1df94e2";
    public static final String BLOCK_PREDICATE_FILTER_CLASS_SHA256 =
            "290b3c0b1dc46ef806a811ee11e4b1fb4bbc4dac99c5cc07990acf65887b30ab";
    public static final String OFFSET_PLACEMENT_CLASS_SHA256 =
            "1414a9c5cd0e5df4a4c5ccf181be2ba05c1c57845c855a809a184217c383b1e5";
    public static final String TRAPEZOID_INT_CLASS_SHA256 =
            "2d1c9d123cab73f597ed319c4112e26af0d66e67725380b2a072ccbf1610b99e";
    public static final String MATCHING_BLOCK_TAG_CLASS_SHA256 =
            "a9650682afda1db3a78e87f8b469fc4cb09d8cfd226f29e16e4b11178076b7df";
    public static final String WOULD_SURVIVE_CLASS_SHA256 =
            "31e549cff6f9bdf41454ff41d9c7387b4993037d500e6a3efef74d9eb788b8d7";
    public static final String MATCHING_FLUIDS_CLASS_SHA256 =
            "aff99cba3b15228f2906d07bc2abb650be207452be26ce273ec07f3f24fa4dbc";
    public static final String ALL_OF_CLASS_SHA256 =
            "744be3dc5cfea99bcee497257dac0b9ebd4b05494a5481acb25f13a852a31af3";
    public static final String ANY_OF_CLASS_SHA256 =
            "3ebf62dc3089582fc57faeb5e1f2a4698fd3620168b965ecc5743ff7e55551b9";
    public static final String FIREFLY_BUSH_CLASS_SHA256 =
            "52a7707b0b3488b536272958883b71adffb1b338f421406f0f46c8bcb3c7e8eb";
    public static final String VEGETATION_BLOCK_CLASS_SHA256 =
            "e4d25f0f8e7e98815622eab7c8a57efb2b2a89b890c5d127bb409eceab6456f8";
    public static final String FEATURE_PLACER_CLASS_SHA256 =
            "1d3e9c087bf63463503ba2fb55893053a3e0da6cd38fd3d6302afb02d9be437f";
    public static final String COUNT_PLACEMENT_CLASS_SHA256 =
            "e2fa737411edc1547e7e9f29d108298fb87b6b03709a6b56fcfa9df1cd30d987";
    public static final String IN_SQUARE_PLACEMENT_CLASS_SHA256 =
            "9faa0a4ef725a3d9f65e90ded8317f24b505bdf1c9163ac1a21e506ccdebdcf7";
    public static final String BIOME_FILTER_CLASS_SHA256 =
            "9af5f023e371c8541e5a6086e5c01693735e39d10ee918adfea4761fc231e38a";
    public static final String STATE_TESTING_PREDICATE_CLASS_SHA256 =
            "8371bbcd4475fb59cf7fec1fc66df59bd6b75b4c5bf8fbd6651117836a0958a1";
    public static final String PLACED_FEATURE_CLASS_SHA256 =
            "61908c74cb40f03052036d9d75ef85deb9d669206de1ccd90fcf703f2df98546";

    private static final State FIREFLY_BUSH = new State("minecraft:firefly_bush", Map.of(),
            false, false);
    private static final int[][] WATER_OFFSETS = {{1, -1, 0}, {-1, -1, 0},
            {0, -1, 1}, {0, -1, -1}};
    private static final Map<String, PhaseSpec> PHASES = Map.ofEntries(
            Map.entry("outer", new PhaseSpec(1, 3)),
            Map.entry("rng_int", new PhaseSpec(2, 3)),
            Map.entry("square", new PhaseSpec(3, 5)),
            Map.entry("height", new PhaseSpec(4, 5)),
            Map.entry("biome", new PhaseSpec(5, 5)),
            Map.entry("read", new PhaseSpec(6, 6)),
            Map.entry("survive", new PhaseSpec(7, 5)),
            Map.entry("fluid", new PhaseSpec(8, 5)),
            Map.entry("near_water", new PhaseSpec(9, 6)),
            Map.entry("offset", new PhaseSpec(10, 8)),
            Map.entry("provider", new PhaseSpec(11, 5)),
            Map.entry("write", new PhaseSpec(12, 7)),
            Map.entry("configured_result", new PhaseSpec(13, 7)),
            Map.entry("placed_result", new PhaseSpec(14, 7)));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public boolean enabled() { return false; }
        @Override public void record(String phase, long... values) {
            throw new AssertionError("disabled near-water firefly trace emitted");
        }
    };

    private Mc263NearWaterFireflyFeature() { }

    public enum Heightmap { MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES }

    /** Minimal exact live boundary for the two filters and SimpleBlockFeature. */
    public interface WorldAccess {
        int minGenerationY();
        int height(Heightmap heightmap, int x, int z);
        /** Pinned seeded BiomeManager zoom result at the exact pre-offset anchor. */
        String biomeKey(int x, int y, int z);
        State blockState(int x, int y, int z);
        String fluidState(int x, int y, int z);
        boolean canSurvive(State state, int x, int y, int z);
        boolean supportsState(State state);
        boolean trySetBlockState(int x, int y, int z, State state, int flags);
    }

    /** Exact block identity plus only the AIR-tag and replaceability facts used here. */
    public record State(String block, Map<String, String> properties,
                        boolean airTag, boolean replaceable) {
        public State {
            block = key(block);
            properties = Collections.unmodifiableMap(new TreeMap<>(properties));
        }
        public static State air(String block) { return new State(block, Map.of(), true, true); }
        public static State block(String block) { return new State(block, Map.of(), false, false); }
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
    public record Result(boolean placed, int anchors, int configuredCalls,
                         int configuredSuccesses, int attemptedWrites, int retainedWrites) { }

    public static Result placeWithFeatureRandom(int index, WorldgenRandom random,
                                                int sourceX, int sourceY, int sourceZ,
                                                WorldAccess world) {
        return placeWithFeatureRandom(index, random, sourceX, sourceY, sourceZ, world, NO_TRACE);
    }

    public static Result placeWithFeatureRandom(int index, WorldgenRandom random,
                                                int sourceX, int sourceY, int sourceZ,
                                                WorldAccess world, TraceSink trace) {
        Spec spec = requireSpec(index);
        preflight(world);
        emit(trace, "outer", index, spec.outerCount, 20);
        Totals totals = new Totals();
        for (int outer = 0; outer < spec.outerCount; outer++) {
            int x = sourceX + nextInt(random, 16, trace);
            int z = sourceZ + nextInt(random, 16, trace);
            emit(trace, "square", index, outer, x, sourceY, z);
            int y = world.height(spec.heightmap, x, z);
            emit(trace, "height", index, spec.heightmap.ordinal(), x, z, y);
            if (y <= world.minGenerationY()) continue;
            String biome = key(world.biomeKey(x, y, z));
            boolean member = biomeContains(index, spec.key, biome);
            emit(trace, "biome", index, x, y, z, member ? 1 : 0);
            if (!member || !nearWater(index, x, y, z, world, trace)) continue;
            totals.anchors++;
            for (int inner = 0; inner < 20; inner++) {
                int ox = triangle(random, 4, trace);
                int oy = triangle(random, 3, trace);
                int oz = triangle(random, 4, trace);
                int px = x + ox, py = y + oy, pz = z + oz;
                emit(trace, "offset", index, inner, ox, oy, oz, px, py, pz);
                State entry = read(world, px, py, pz, trace);
                if (!entry.airTag) continue;
                totals.configuredCalls++;
                Configured configured = configured(index, px, py, pz, world, trace);
                if (configured.placed) totals.successes++;
                totals.writes += configured.writes;
                totals.retained += configured.retained;
            }
        }
        emit(trace, "placed_result", index, totals.anchors, totals.configuredCalls,
                totals.successes, totals.writes, totals.retained,
                totals.successes > 0 ? 1 : 0);
        return totals.result();
    }

    public static Result placeConfigured(int index, WorldgenRandom random, int x, int y, int z,
                                         WorldAccess world) {
        return placeConfigured(index, random, x, y, z, world, NO_TRACE);
    }

    public static Result placeConfigured(int index, WorldgenRandom random, int x, int y, int z,
                                         WorldAccess world, TraceSink trace) {
        requireSpec(index);
        if (random == null) throw new IllegalArgumentException("random is required");
        preflight(world);
        Configured configured = configured(index, x, y, z, world, trace);
        return new Result(configured.placed, 1, 1, configured.placed ? 1 : 0,
                configured.writes, configured.retained);
    }

    private static boolean nearWater(int index, int x, int y, int z, WorldAccess world,
                                     TraceSink trace) {
        State entry = read(world, x, y, z, trace);
        if (!entry.airTag) {
            emit(trace, "near_water", index, x, y, z, -1, 0);
            return false;
        }
        boolean survives = world.canSurvive(FIREFLY_BUSH, x, y, z);
        emit(trace, "survive", index, x, y, z, survives ? 1 : 0);
        if (!survives) {
            emit(trace, "near_water", index, x, y, z, -1, 0);
            return false;
        }
        for (int direction = 0; direction < WATER_OFFSETS.length; direction++) {
            int[] offset = WATER_OFFSETS[direction];
            int fx = x + offset[0], fy = y + offset[1], fz = z + offset[2];
            String fluid = key(world.fluidState(fx, fy, fz));
            emit(trace, "fluid", fx, fy, fz, keyId(fluid), direction);
            boolean water = fluid.equals("minecraft:water")
                    || fluid.equals("minecraft:flowing_water");
            if (water) {
                emit(trace, "near_water", index, x, y, z, direction, 1);
                return true;
            }
        }
        emit(trace, "near_water", index, x, y, z, -1, 0);
        return false;
    }

    private static Configured configured(int index, int x, int y, int z, WorldAccess world,
                                         TraceSink trace) {
        emit(trace, "provider", index, stateId(FIREFLY_BUSH), x, y, z);
        boolean survives = world.canSurvive(FIREFLY_BUSH, x, y, z);
        emit(trace, "survive", index, x, y, z, survives ? 1 : 0);
        if (!survives) {
            emit(trace, "configured_result", index, 0, x, y, z, 0, 0);
            return new Configured(false, 0, 0);
        }
        boolean retained = write(world, x, y, z, FIREFLY_BUSH, trace);
        emit(trace, "configured_result", index, 1, x, y, z, 1, retained ? 1 : 0);
        return new Configured(true, 1, retained ? 1 : 0);
    }

    public static byte[] encodeTraceFixture(List<TraceEvent> events) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(TRACE_MAGIC); out.writeShort(TRACE_VERSION);
            byte[] schema = TRACE_SCHEMA.getBytes(StandardCharsets.UTF_8);
            out.writeShort(schema.length); out.write(schema); out.writeInt(events.size());
            for (TraceEvent event : events) {
                PhaseSpec phase = PHASES.get(event.phase);
                if (phase == null || event.values.length != phase.arity) {
                    throw new IllegalArgumentException("bad NWF3 event");
                }
                out.writeByte(phase.id); out.writeByte(phase.arity);
                for (long value : event.values) out.writeLong(value);
            }
            out.flush(); return bytes.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }

    public static List<Integer> coveredIndices() { return List.of(89, 92); }
    public static String featureKey(int index) { return requireSpec(index).key; }

    /** Pure exact capability/state closure check for one placed feature. */
    public static void preflight(int index, WorldAccess world) {
        requireSpec(index);
        preflight(world);
    }

    private static void preflight(WorldAccess world) {
        if (!world.supportsState(FIREFLY_BUSH)) {
            throw new UnsupportedOperationException("unregistered exact firefly state: "
                    + FIREFLY_BUSH.canonical());
        }
    }
    private static Spec requireSpec(int index) {
        return switch (index) {
            case 89 -> new Spec(89, "minecraft:patch_firefly_bush_near_water_swamp", 3,
                    Heightmap.MOTION_BLOCKING);
            case 92 -> new Spec(92, "minecraft:patch_firefly_bush_near_water", 2,
                    Heightmap.MOTION_BLOCKING_NO_LEAVES);
            default -> throw new IllegalArgumentException("unsupported near-water index " + index);
        };
    }
    private static boolean biomeContains(int index, String featureKey, String biome) {
        return Mc263FeatureIndexReceipt.biome(biome).featuresAtStep(STEP).stream()
                .anyMatch(ref -> ref.globalIndex() == index && ref.featureKey().equals(featureKey));
    }
    private static State read(WorldAccess world, int x, int y, int z, TraceSink trace) {
        State state = world.blockState(x, y, z);
        emit(trace, "read", x, y, z, stateId(state), state.airTag ? 1 : 0,
                state.replaceable ? 1 : 0);
        return state;
    }
    private static boolean write(WorldAccess world, int x, int y, int z, State state,
                                 TraceSink trace) {
        boolean retained = world.trySetBlockState(x, y, z, state, 2);
        emit(trace, "write", x, y, z, stateId(state), 2, retained ? 1 : 0,
                state.properties.hashCode());
        return retained;
    }
    private static int triangle(WorldgenRandom random, int range, TraceSink trace) {
        return nextInt(random, range + 1, trace) - nextInt(random, range + 1, trace);
    }
    private static int nextInt(WorldgenRandom random, int bound, TraceSink trace) {
        int value = random.nextInt(bound);
        emit(trace, "rng_int", bound, value, 0);
        return value;
    }
    private static long stateId(State state) { return keyId(state.canonical()); }
    private static long keyId(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
            hash = (hash ^ (item & 255)) * 0x100000001b3L;
        }
        return hash;
    }
    private static String key(String value) {
        if (value == null || !RESOURCE_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid key");
        }
        return value;
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
    private static void emit(TraceSink trace, String phase, long a, long b, long c, long d,
                             long e, long f, long g, long h) {
        if (trace.enabled()) trace.record(phase, a, b, c, d, e, f, g, h);
    }

    private record Spec(int index, String key, int outerCount, Heightmap heightmap) { }
    private record PhaseSpec(int id, int arity) { }
    private record Configured(boolean placed, int writes, int retained) { }
    private static final class Totals {
        int anchors, configuredCalls, successes, writes, retained;
        Result result() {
            return new Result(successes > 0, anchors, configuredCalls, successes,
                    writes, retained);
        }
    }
}
