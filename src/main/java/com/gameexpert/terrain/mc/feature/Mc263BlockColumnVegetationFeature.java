package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263WorldgenRandomSource;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Exact dormant 26.3-snapshot-7 step-9 sugar-cane/cactus BlockColumn tranche. */
public final class Mc263BlockColumnVegetationFeature {
    public static final int STEP = 9;
    public static final int TRACE_MAGIC = 0x42434c33; // BCL3
    public static final int TRACE_VERSION = 1;
    public static final String TRACE_SCHEMA = "mc263-block-column-vegetation-trace-v1";
    public static final String INNER_SERVER_SHA1 = "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String SUGAR_CANE_JSON_SHA256 = "7b662547c226face1e8f845fab69918f3845f8f340c5b11f6d84ac6c91b2afc3";
    public static final String CACTUS_JSON_SHA256 = "42e196c7c4aef8433c083e2a55880eb42bcdb4328f5e89d593480485edd7bdb3";
    public static final String SUGAR_CANE_DESERT_PLACED_SHA256 = "e554345073145a9a4f73c3a3f62ae0942a649bcc0e382aa079679a6c1abcec1a";
    public static final String SUGAR_CANE_BADLANDS_PLACED_SHA256 = "9a43b0e4b236187fbd442883f5fe260b14d90762b5b861631804f4b85ee6d4b9";
    public static final String SUGAR_CANE_SWAMP_PLACED_SHA256 = "22752c16dbddeb5aa18e058fa4584d10f28526bc28f4300d12c82c2afdc6f73d";
    public static final String CACTUS_DESERT_PLACED_SHA256 = "8d9ed9deeea3f52aaba92c740da0a78d950dc2103acbca91d93d4b0fbc3edce0";
    public static final String CACTUS_DECORATED_PLACED_SHA256 = "b99f97e9728ac30ec74f7c6f95abc05f17074dc8a63dfb462076ae54d966a70f";
    public static final String SUGAR_CANE_PLACED_SHA256 = "174ae730a46d60295c8cb38e4fef685208e8ee3046501bd1b613f4c6ae5185ee";
    public static final String BLOCK_COLUMN_CLASS_SHA256 = "46d37e8cf9613d6115a28f90b38d17e935a529d00043b967d42ffd8b717bd4c6";
    public static final String BIASED_TO_BOTTOM_CLASS_SHA256 = "ee136c1cb63c923ecc89adb89463d5d312a4f4b8b4e8cd0044563011f9db2d0b";
    public static final String WEIGHTED_LIST_INT_CLASS_SHA256 = "f55c5c8c92e9aee16ec1e27072bb4ca6c401962b6183d03634a7bc9f4ed4c6ec";
    public static final String SUGAR_CANE_BLOCK_CLASS_SHA256 = "574bb2c8f79f19abefb59bb797992fe543cd835c7b202d0eb39629ee66ea93d2";
    public static final String CACTUS_BLOCK_CLASS_SHA256 = "b9b3c28164b51c3fb40067d2735f3ea59ae0fe17c90f900d002a2fdb2de3d0ea";

    public static final int[] COVERED_INDICES = {78, 79, 81, 86, 87, 91};
    private static final State SUGAR_CANE = State.of("minecraft:sugar_cane").property("age", "0");
    private static final State CACTUS = State.of("minecraft:cactus").property("age", "0");
    private static final State CACTUS_FLOWER = State.of("minecraft:cactus_flower");
    private static final Map<Integer, Spec> SPECS = specs();
    private static final Map<String, PhaseSpec> PHASES = Map.ofEntries(
            Map.entry("outer", new PhaseSpec(1, 4)),
            Map.entry("rng_float", new PhaseSpec(2, 2)),
            Map.entry("rng_int", new PhaseSpec(3, 3)),
            Map.entry("square", new PhaseSpec(4, 5)),
            Map.entry("height", new PhaseSpec(5, 5)),
            Map.entry("biome", new PhaseSpec(6, 5)),
            Map.entry("offset", new PhaseSpec(7, 8)),
            Map.entry("read", new PhaseSpec(8, 5)),
            Map.entry("survive", new PhaseSpec(9, 5)),
            Map.entry("fluid", new PhaseSpec(10, 5)),
            Map.entry("predicate", new PhaseSpec(11, 5)),
            Map.entry("layer_height", new PhaseSpec(12, 5)),
            Map.entry("scan", new PhaseSpec(13, 6)),
            Map.entry("truncate", new PhaseSpec(14, 5)),
            Map.entry("provider", new PhaseSpec(15, 5)),
            Map.entry("write", new PhaseSpec(16, 7)),
            Map.entry("configured_result", new PhaseSpec(17, 7)),
            Map.entry("placed_result", new PhaseSpec(18, 7)));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public boolean enabled() { return false; }
        @Override public void record(String phase, long... values) {
            throw new AssertionError("disabled BlockColumn trace emitted");
        }
    };

    private Mc263BlockColumnVegetationFeature() { }

    public enum Heightmap { MOTION_BLOCKING }

    /** Minimal live FEATURES boundary; survival remains one exact semantic carrier callback. */
    public interface WorldAccess {
        int minGenerationY();
        int height(Heightmap heightmap, int x, int z);
        String biomeKey(int x, int y, int z);
        State blockState(int x, int y, int z);
        String fluidState(int x, int y, int z);
        boolean canSurvive(State state, int x, int y, int z);
        boolean supportsState(State state);
        boolean trySetBlockState(int x, int y, int z, State state, int flags);
    }

    public record State(String block, Map<String, String> properties, boolean airTag) {
        public State {
            block = key(block);
            properties = Collections.unmodifiableMap(new TreeMap<>(properties));
        }
        public static State of(String block) { return new State(block, Map.of(), false); }
        public static State air(String block) { return new State(block, Map.of(), true); }
        public State property(String name, String value) {
            Map<String, String> next = new TreeMap<>(properties);
            next.put(name, value);
            return new State(block, next, airTag);
        }
        public String canonical() {
            if (properties.isEmpty()) return block;
            return block + properties.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
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

    public static Result placeWithFeatureRandom(int index, Mc263WorldgenRandomSource random,
                                                int sourceX, int sourceY, int sourceZ,
                                                WorldAccess world) {
        return placeWithFeatureRandom(index, random, sourceX, sourceY, sourceZ, world, NO_TRACE);
    }

    public static Result placeWithFeatureRandom(int index, Mc263WorldgenRandomSource random,
                                                int sourceX, int sourceY, int sourceZ,
                                                WorldAccess world, TraceSink trace) {
        Spec spec = requireSpec(index);
        preflight(spec, world);
        Totals total = new Totals();
        emit(trace, "outer", index, 1, spec.rarity, spec.count);
        if (spec.rarity > 1 && !rarity(random, spec.rarity, trace)) {
            emit(trace, "placed_result", index, 0, 0, 0, 0, 0, 0);
            return total.result();
        }
        int baseX = sourceX + nextInt(random, 16, trace);
        int baseZ = sourceZ + nextInt(random, 16, trace);
        emit(trace, "square", index, 0, baseX, sourceY, baseZ);
        int baseY = world.height(Heightmap.MOTION_BLOCKING, baseX, baseZ);
        emit(trace, "height", index, Heightmap.MOTION_BLOCKING.ordinal(), baseX, baseZ, baseY);
        if (baseY <= world.minGenerationY()) {
            emit(trace, "placed_result", index, 0, 0, 0, 0, 0, 0);
            return total.result();
        }
        String biome = world.biomeKey(baseX, baseY, baseZ);
        boolean member = biomeContains(index, spec.key, biome);
        emit(trace, "biome", index, baseX, baseY, baseZ, member ? 1 : 0);
        if (!member) {
            emit(trace, "placed_result", index, 0, 0, 0, 0, 0, 0);
            return total.result();
        }
        for (int candidate = 0; candidate < spec.count; candidate++) {
            int ox = triangle(random, spec.xzRange, trace);
            int oy = triangle(random, spec.yRange, trace);
            int oz = triangle(random, spec.xzRange, trace);
            int x = baseX + ox, y = baseY + oy, z = baseZ + oz;
            emit(trace, "offset", index, candidate, ox, oy, oz, x, y, z);
            total.candidates++;
            if (!placedPredicate(spec.kind, world, x, y, z, trace)) continue;
            total.configuredCalls++;
            Configured configured = placeConfigured0(index, spec, random, x, y, z, world, trace);
            if (configured.placed) total.successes++;
            total.writes += configured.writes;
            total.retained += configured.retained;
        }
        emit(trace, "placed_result", index, total.candidates, total.configuredCalls,
                total.successes, total.writes, total.retained, total.successes > 0 ? 1 : 0);
        return total.result();
    }

    public static Result placeConfigured(int index, Mc263WorldgenRandomSource random, int x, int y, int z,
                                         WorldAccess world) {
        return placeConfigured(index, random, x, y, z, world, NO_TRACE);
    }

    public static Result placeConfigured(int index, Mc263WorldgenRandomSource random, int x, int y, int z,
                                         WorldAccess world, TraceSink trace) {
        Spec spec = requireSpec(index);
        preflight(spec, world);
        Configured configured = placeConfigured0(index, spec, random, x, y, z, world, trace);
        return new Result(configured.placed, 1, 1, configured.placed ? 1 : 0,
                configured.writes, configured.retained);
    }

    private static Configured placeConfigured0(int index, Spec spec, Mc263WorldgenRandomSource random,
                                               int x, int y, int z, WorldAccess world,
                                               TraceSink trace) {
        int[] heights;
        if (spec.kind == Kind.SUGAR_CANE) {
            int cane = biased(random, 2, 4, trace);
            heights = new int[]{cane};
            emit(trace, "layer_height", index, 0, cane, cane, stateId(SUGAR_CANE));
        } else {
            int cactus = biased(random, 1, 3, trace);
            int flowerPick = nextInt(random, 4, trace);
            int flower = flowerPick < 3 ? 0 : 1;
            heights = new int[]{cactus, flower};
            emit(trace, "layer_height", index, 0, cactus, cactus, stateId(CACTUS));
            emit(trace, "layer_height", index, 1, flower, cactus + flower, stateId(CACTUS_FLOWER));
        }
        int sampledTotal = sum(heights);
        if (sampledTotal == 0) {
            emit(trace, "configured_result", index, 0, 0, 0, 0, 0, heights.length);
            return new Configured(false, 0, 0);
        }
        int allowed = sampledTotal;
        for (int step = 0; step < sampledTotal; step++) {
            State state = read(world, x, y + step + 1, z, trace);
            emit(trace, "scan", index, step, x, y + step + 1, z, state.airTag ? 1 : 0);
            if (!state.airTag) {
                allowed = step;
                truncateFromTip(heights, sampledTotal - allowed);
                emit(trace, "truncate", index, sampledTotal, allowed, heights[0],
                        heights.length == 1 ? 0 : heights[1]);
                break;
            }
        }
        int writes = 0, retained = 0, py = y;
        State[] providers = spec.kind == Kind.SUGAR_CANE
                ? new State[]{SUGAR_CANE} : new State[]{CACTUS, CACTUS_FLOWER};
        for (int layer = 0; layer < heights.length; layer++) {
            for (int n = 0; n < heights[layer]; n++) {
                State state = providers[layer];
                emit(trace, "provider", index, layer, x, py, stateId(state));
                boolean kept = world.trySetBlockState(x, py, z, state, 2);
                emit(trace, "write", x, py, z, stateId(state), 2, kept ? 1 : 0,
                        state.properties.hashCode());
                writes++;
                if (kept) retained++;
                py++;
            }
        }
        emit(trace, "configured_result", index, 1, sampledTotal, sum(heights), writes,
                retained, heights.length);
        return new Configured(true, writes, retained);
    }

    private static boolean placedPredicate(Kind kind, WorldAccess world, int x, int y, int z,
                                           TraceSink trace) {
        State state = read(world, x, y, z, trace);
        if (!state.airTag) {
            emit(trace, "predicate", kind.ordinal(), x, y, z, 0);
            return false;
        }
        State placed = kind == Kind.SUGAR_CANE ? SUGAR_CANE : CACTUS;
        boolean survives = world.canSurvive(placed, x, y, z);
        emit(trace, "survive", kind.ordinal(), x, y, z, survives ? 1 : 0);
        if (!survives) {
            emit(trace, "predicate", kind.ordinal(), x, y, z, 0);
            return false;
        }
        if (kind == Kind.SUGAR_CANE) {
            int[][] offsets = {{1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1}};
            boolean found = false;
            for (int direction = 0; direction < offsets.length; direction++) {
                String fluid = key(world.fluidState(x + offsets[direction][0],
                        y + offsets[direction][1], z + offsets[direction][2]));
                boolean supports = fluid.equals("minecraft:water")
                        || fluid.equals("minecraft:flowing_water");
                emit(trace, "fluid", direction, x + offsets[direction][0],
                        y + offsets[direction][1], z + offsets[direction][2], supports ? 1 : 0);
                if (supports) { found = true; break; }
            }
            if (!found) {
                emit(trace, "predicate", kind.ordinal(), x, y, z, 0);
                return false;
            }
        }
        emit(trace, "predicate", kind.ordinal(), x, y, z, 1);
        return true;
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
                if (phase == null || phase.arity != event.values.length) {
                    throw new IllegalArgumentException("invalid BlockColumn trace event " + event.phase);
                }
                out.writeByte(phase.id); out.writeByte(phase.arity);
                for (long value : event.values) out.writeLong(value);
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }

    public static String featureKey(int index) { return requireSpec(index).key; }
    public static List<Integer> coveredIndices() { return SPECS.keySet().stream().sorted().toList(); }

    /** Pure exact capability/state closure check for one placed feature. */
    public static void preflight(int index, WorldAccess world) {
        preflight(requireSpec(index), world);
    }

    private static int biased(Mc263WorldgenRandomSource random, int min, int max, TraceSink trace) {
        int first = nextInt(random, max - min + 1, trace);
        return min + nextInt(random, first + 1, trace);
    }
    private static int triangle(Mc263WorldgenRandomSource random, int range, TraceSink trace) {
        return nextInt(random, range + 1, trace) - nextInt(random, range + 1, trace);
    }
    private static boolean rarity(Mc263WorldgenRandomSource random, int chance, TraceSink trace) {
        float value = random.nextFloat();
        emit(trace, "rng_float", Float.floatToRawIntBits(value), chance);
        return value < 1.0f / (float) chance;
    }
    private static int nextInt(Mc263WorldgenRandomSource random, int bound, TraceSink trace) {
        int value = random.nextInt(bound);
        emit(trace, "rng_int", bound, value, 0);
        return value;
    }
    private static State read(WorldAccess world, int x, int y, int z, TraceSink trace) {
        State state = world.blockState(x, y, z);
        emit(trace, "read", x, y, z, stateId(state), state.airTag ? 1 : 0);
        return state;
    }
    private static void truncateFromTip(int[] heights, int remove) {
        for (int layer = heights.length - 1; layer >= 0 && remove > 0; layer--) {
            int taken = Math.min(heights[layer], remove);
            heights[layer] -= taken;
            remove -= taken;
        }
    }
    private static int sum(int[] values) { int total = 0; for (int value : values) total += value; return total; }
    private static void preflight(Spec spec, WorldAccess world) {
        for (State state : spec.kind == Kind.SUGAR_CANE
                ? new State[]{SUGAR_CANE} : new State[]{CACTUS, CACTUS_FLOWER}) {
            if (!world.supportsState(state)) {
                throw new UnsupportedOperationException("unregistered exact BlockColumn state: "
                        + state.canonical());
            }
        }
    }
    private static boolean biomeContains(int index, String feature, String biome) {
        return Mc263FeatureIndexReceipt.biome(biome).featuresAtStep(STEP).stream()
                .anyMatch(r -> r.globalIndex() == index && r.featureKey().equals(feature));
    }
    private static long stateId(State state) { return keyId(state.canonical()); }
    private static long keyId(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) hash = (hash ^ (b & 255)) * 0x100000001b3L;
        return hash;
    }
    private static String key(String value) {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("invalid namespaced key: " + value);
        }
        return value;
    }
    private static Spec requireSpec(int index) {
        Spec spec = SPECS.get(index);
        if (spec == null) throw new IllegalArgumentException("unsupported BlockColumn index " + index);
        return spec;
    }
    private static Map<Integer, Spec> specs() {
        Map<Integer, Spec> specs = new LinkedHashMap<>();
        specs.put(78, new Spec(78, "minecraft:patch_sugar_cane_desert", Kind.SUGAR_CANE, 1, 20, 4, 0));
        specs.put(79, new Spec(79, "minecraft:patch_sugar_cane_badlands", Kind.SUGAR_CANE, 5, 20, 4, 0));
        specs.put(81, new Spec(81, "minecraft:patch_sugar_cane_swamp", Kind.SUGAR_CANE, 3, 20, 4, 0));
        specs.put(86, new Spec(86, "minecraft:patch_cactus_desert", Kind.CACTUS, 6, 10, 7, 3));
        specs.put(87, new Spec(87, "minecraft:patch_cactus_decorated", Kind.CACTUS, 13, 10, 7, 3));
        specs.put(91, new Spec(91, "minecraft:patch_sugar_cane", Kind.SUGAR_CANE, 6, 20, 4, 0));
        return Map.copyOf(specs);
    }

    private static void emit(TraceSink t, String p, long a, long b) { if (t.enabled()) t.record(p, a, b); }
    private static void emit(TraceSink t, String p, long a, long b, long c) { if (t.enabled()) t.record(p, a, b, c); }
    private static void emit(TraceSink t, String p, long a, long b, long c, long d) { if (t.enabled()) t.record(p, a, b, c, d); }
    private static void emit(TraceSink t, String p, long a, long b, long c, long d, long e) { if (t.enabled()) t.record(p, a, b, c, d, e); }
    private static void emit(TraceSink t, String p, long a, long b, long c, long d, long e, long f) { if (t.enabled()) t.record(p, a, b, c, d, e, f); }
    private static void emit(TraceSink t, String p, long a, long b, long c, long d, long e, long f, long g) { if (t.enabled()) t.record(p, a, b, c, d, e, f, g); }
    private static void emit(TraceSink t, String p, long a, long b, long c, long d, long e, long f, long g, long h) { if (t.enabled()) t.record(p, a, b, c, d, e, f, g, h); }

    private enum Kind { SUGAR_CANE, CACTUS }
    private record Spec(int index, String key, Kind kind, int rarity, int count,
                        int xzRange, int yRange) { }
    private record PhaseSpec(int id, int arity) { }
    private record Configured(boolean placed, int writes, int retained) { }
    private static final class Totals {
        int candidates, configuredCalls, successes, writes, retained;
        Result result() { return new Result(successes > 0, candidates, configuredCalls,
                successes, writes, retained); }
    }
}
