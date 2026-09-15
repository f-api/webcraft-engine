package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Exact dormant Minecraft 26.3-snapshot-7 step-9 seagrass family. */
public final class Mc263SeagrassFeature {
    /** The pinned namespaced-key grammar, compiled once: {@code String.matches} recompiles
     * this pattern on every call, and the key validator runs once per read state. */
    private static final Pattern RESOURCE_KEY =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public static final int STEP = 9;
    public static final int TRACE_MAGIC = 0x53454733; // SEG3
    public static final int TRACE_VERSION = 1;
    public static final String TRACE_SCHEMA = "mc263-seagrass-trace-v1";
    public static final String INNER_SERVER_SHA1 = "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String WEIGHTED_SELECTOR_CLASS_SHA256 = "acb5fdc954d66445c828096813e5cad8154e90faca645a6f672b06941337b490";
    public static final String TALL_SEAGRASS_CLASS_SHA256 = "e5b67bf3a074a68d1f4b658e41d3ef6d260e70fb48ff1b507730250a161f645a";
    public static final String SEAGRASS_CLASS_SHA256 = "74711c6f9a458373aefe88c5623902f53159961bf7836b80a23a815e3f59625a";
    public static final String AQUATIC_FEATURES_CLASS_SHA256 = "e06fbf7e7241d304eb3aa1fd64f787afae16998d0195aeb25da6c0c5c538bf53";
    public static final String AQUATIC_PLACEMENTS_CLASS_SHA256 = "bf03bd42d14659bab522a86c0c49e9b551bd18d66661a5af3ec8ab2f07e2a72f";
    public static final String WEIGHTED_LIST_CLASS_SHA256 = "76702ad9ac458345d9a24f86ce70384dd7668ebb95199d1e63a33c72c80128eb";
    public static final String SIMPLE_BLOCK_CLASS_SHA256 = "10745682a08452d5e5f148cfd627fa1ec84bf669570da5cd284ee2119e810101";
    public static final String DOUBLE_PLANT_CLASS_SHA256 = "a52bd483ba51e009812afe8d162403d497bfb8fc4c662e4ee2cfb2d0f0e826e7";
    public static final String SEAGRASS_MID_JSON_SHA256 = "39d62e3aa11de4f244446ec5883483e33c237abf27660da49dfd371aeb42cc8f";
    public static final String SEAGRASS_SLIGHTLY_LESS_SHORT_JSON_SHA256 = "77c5ddb195c7f15e28be66ec7d0533955b5da11ffb6528ffd21d500696a5637f";
    public static final String SEAGRASS_SHORT_JSON_SHA256 = "1d6717551ce76f550bf0adde5c5452139fe3728fb3e1b48c457b92bacc7b473f";
    public static final String SEAGRASS_TALL_JSON_SHA256 = "c61dcc6441399b95b383d034828d16f802b6b69b6ef443e50d1a0f1c986359ec";
    public static final String SEAGRASS_SWAMP_PLACED_SHA256 = "336f819258d32dcc7dd4b780f4aba231ca40d16cb2f845d9dc45781776509852";
    public static final String SEAGRASS_RIVER_PLACED_SHA256 = "d2ed44f533e1c3ba54a38884f5cf001167166f3334d56da245277c8cf62ff762";
    public static final String SEAGRASS_WARM_PLACED_SHA256 = "249c4d9f89d9aa74b9ddb0d96c24d8af4d486aead9614d42a1617053f43a7c81";
    public static final String SEAGRASS_DEEP_WARM_PLACED_SHA256 = "3824f6d463760586e1544b12c27761f11747558ff180d949bb09ed93f1bdc682";
    public static final String SEAGRASS_NORMAL_PLACED_SHA256 = "ee85edbd6c1f1f36d5a8ebb501f5721e4986565a73c23dc83922b71466511be0";
    public static final String SEAGRASS_DEEP_PLACED_SHA256 = "ac7f5fd7d738c0e6d64b2a034f07585b37b493a7a9c42efa6b4634373b847a93";
    public static final String SEAGRASS_COLD_PLACED_SHA256 = "f30b2c7740a10caf6c80dc177923c3151a3c988c51bced50213a96326ea8bd50";
    public static final String SEAGRASS_DEEP_COLD_PLACED_SHA256 = "ed790f6c2be17f8c8b957eca435d59ea0f0a76de47ecbfcebc9f7f8852badc04";
    public static final int[] COVERED_INDICES = {90, 93, 100, 102, 104, 105, 106, 107};

    private static final State SHORT = new State("minecraft:seagrass", Map.of(),
            "minecraft:water", true, false, true);
    private static final State TALL_LOWER = new State("minecraft:tall_seagrass",
            Map.of("half", "lower"), "minecraft:water", true, false, true);
    private static final State TALL_UPPER = new State("minecraft:tall_seagrass",
            Map.of("half", "upper"), "minecraft:water", true, false, true);
    private static final Map<Integer, Spec> SPECS = specs();
    private static final Map<String, PhaseSpec> PHASES = Map.ofEntries(
            Map.entry("outer", new PhaseSpec(1, 3)),
            Map.entry("rng_int", new PhaseSpec(2, 3)),
            Map.entry("square", new PhaseSpec(3, 5)),
            Map.entry("offset", new PhaseSpec(4, 8)),
            Map.entry("height", new PhaseSpec(5, 5)),
            Map.entry("read", new PhaseSpec(6, 6)),
            Map.entry("water", new PhaseSpec(7, 5)),
            Map.entry("biome", new PhaseSpec(8, 5)),
            Map.entry("selector", new PhaseSpec(9, 5)),
            Map.entry("survive", new PhaseSpec(10, 5)),
            Map.entry("write", new PhaseSpec(11, 7)),
            Map.entry("configured_result", new PhaseSpec(12, 7)),
            Map.entry("placed_result", new PhaseSpec(13, 7)));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public boolean enabled() { return false; }
        @Override public void record(String phase, long... values) {
            throw new AssertionError("disabled seagrass trace emitted");
        }
    };

    private Mc263SeagrassFeature() { }

    public enum Heightmap { OCEAN_FLOOR }

    public interface WorldAccess {
        int minGenerationY();
        int height(Heightmap heightmap, int x, int z);
        String biomeKey(int x, int y, int z);
        State blockState(int x, int y, int z);
        boolean canSurvive(State state, int x, int y, int z);
        boolean supportsState(State state);
        boolean trySetBlockState(int x, int y, int z, State state, int flags);
    }

    /** Exact block/fluid facts needed by matching-block and DoublePlant compatibility checks. */
    public record State(String block, Map<String, String> properties, String fluid,
                        boolean fluidSource, boolean airTag, boolean replaceable) {
        public State {
            block = key(block);
            fluid = key(fluid);
            properties = Collections.unmodifiableMap(new TreeMap<>(properties));
            if (fluidSource && fluid.equals("minecraft:empty")) {
                throw new IllegalArgumentException("empty fluid cannot be source");
            }
        }
        public static State block(String block) {
            return new State(block, Map.of(), "minecraft:empty", false, false, false);
        }
        public static State water(int level) {
            if (level < 0 || level > 15) throw new IllegalArgumentException("water level 0..15");
            return new State("minecraft:water", Map.of("level", Integer.toString(level)),
                    "minecraft:water", level == 0, false, true);
        }
        public static State air(String block) {
            return new State(block, Map.of(), "minecraft:empty", false, true, true);
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
        Totals total = new Totals();
        emit(trace, "outer", index, spec.count, spec.tallWeight);
        int baseX = sourceX + nextInt(random, 16, trace);
        int baseZ = sourceZ + nextInt(random, 16, trace);
        emit(trace, "square", index, 0, baseX, sourceY, baseZ);
        for (int candidate = 0; candidate < spec.count; candidate++) {
            int ox = triangle(random, 7, trace);
            int oy = triangle(random, 0, trace);
            int oz = triangle(random, 7, trace);
            int x = baseX + ox, z = baseZ + oz;
            emit(trace, "offset", index, candidate, ox, oy, oz, x, sourceY + oy, z);
            int y = world.height(Heightmap.OCEAN_FLOOR, x, z);
            emit(trace, "height", index, Heightmap.OCEAN_FLOOR.ordinal(), x, z, y);
            if (y <= world.minGenerationY()) continue;
            total.candidates++;
            State entry = read(world, x, y, z, trace);
            boolean water = entry.block.equals("minecraft:water");
            emit(trace, "water", index, x, y, z, water ? 1 : 0);
            if (!water) continue;
            String biome = world.biomeKey(x, y, z);
            boolean member = biomeContains(index, spec.key, biome);
            emit(trace, "biome", index, x, y, z, member ? 1 : 0);
            if (!member) continue;
            total.configuredCalls++;
            Configured configured = configured(spec, random, x, y, z, world, trace);
            if (configured.placed) total.successes++;
            total.writes += configured.writes;
            total.retained += configured.retained;
        }
        emit(trace, "placed_result", index, total.candidates, total.configuredCalls,
                total.successes, total.writes, total.retained, total.successes > 0 ? 1 : 0);
        return total.result();
    }

    public static Result placeConfigured(int index, WorldgenRandom random, int x, int y, int z,
                                         WorldAccess world) {
        return placeConfigured(index, random, x, y, z, world, NO_TRACE);
    }

    public static Result placeConfigured(int index, WorldgenRandom random, int x, int y, int z,
                                         WorldAccess world, TraceSink trace) {
        Spec spec = requireSpec(index);
        preflight(world);
        Configured configured = configured(spec, random, x, y, z, world, trace);
        return new Result(configured.placed, 1, 1, configured.placed ? 1 : 0,
                configured.writes, configured.retained);
    }

    private static Configured configured(Spec spec, WorldgenRandom random, int x, int y, int z,
                                         WorldAccess world, TraceSink trace) {
        int pick = nextInt(random, 100, trace);
        boolean tall = pick < spec.tallWeight;
        emit(trace, "selector", spec.index, pick, spec.tallWeight, tall ? 1 : 0,
                stateId(tall ? TALL_LOWER : SHORT));
        if (tall) {
            State upperPredicate = read(world, x, y + 1, z, trace);
            boolean upperWater = upperPredicate.block.equals("minecraft:water");
            emit(trace, "water", spec.index, x, y + 1, z, upperWater ? 1 : 0);
            if (!upperWater) {
                emit(trace, "configured_result", spec.index, 0, 1, 0, 0, 0, 1);
                return new Configured(false, 0, 0);
            }
            boolean survives = world.canSurvive(TALL_LOWER, x, y, z);
            emit(trace, "survive", spec.index, x, y, z, survives ? 1 : 0);
            if (!survives) {
                emit(trace, "configured_result", spec.index, 0, 1, 0, 0, 0, 1);
                return new Configured(false, 0, 0);
            }
            State upperLive = read(world, x, y + 1, z, trace);
            boolean compatible = upperLive.airTag
                    || TALL_LOWER.fluid.equals(upperLive.fluid) && upperLive.fluidSource
                    && upperLive.replaceable;
            if (!compatible) {
                emit(trace, "configured_result", spec.index, 0, 1, 0, 0, 0, 1);
                return new Configured(false, 0, 0);
            }
            int retained = 0;
            if (write(world, x, y, z, TALL_LOWER, trace)) retained++;
            if (write(world, x, y + 1, z, TALL_UPPER, trace)) retained++;
            emit(trace, "configured_result", spec.index, 1, 1, 2, retained, 1, 2);
            return new Configured(true, 2, retained);
        }
        boolean survives = world.canSurvive(SHORT, x, y, z);
        emit(trace, "survive", spec.index, x, y, z, survives ? 1 : 0);
        if (!survives) {
            emit(trace, "configured_result", spec.index, 0, 1, 0, 0, 0, 0);
            return new Configured(false, 0, 0);
        }
        boolean kept = write(world, x, y, z, SHORT, trace);
        emit(trace, "configured_result", spec.index, 1, 1, 1, kept ? 1 : 0, 0, 1);
        return new Configured(true, 1, kept ? 1 : 0);
    }

    private static boolean write(WorldAccess world, int x, int y, int z, State state,
                                 TraceSink trace) {
        boolean retained = world.trySetBlockState(x, y, z, state, 2);
        emit(trace, "write", x, y, z, stateId(state), 2, retained ? 1 : 0,
                state.properties.hashCode());
        return retained;
    }
    private static State read(WorldAccess world, int x, int y, int z, TraceSink trace) {
        State state = world.blockState(x, y, z);
        emit(trace, "read", x, y, z, stateId(state), state.fluidSource ? 1 : 0,
                state.replaceable ? 1 : 0);
        return state;
    }
    private static int triangle(WorldgenRandom random, int range, TraceSink trace) {
        return nextInt(random, range + 1, trace) - nextInt(random, range + 1, trace);
    }
    private static int nextInt(WorldgenRandom random, int bound, TraceSink trace) {
        int value = random.nextInt(bound);
        emit(trace, "rng_int", bound, value, 0);
        return value;
    }
    private static void preflight(WorldAccess world) {
        for (State state : new State[]{SHORT, TALL_LOWER, TALL_UPPER}) {
            if (!world.supportsState(state)) {
                throw new UnsupportedOperationException("unregistered exact seagrass state: "
                        + state.canonical());
            }
        }
    }
    private static boolean biomeContains(int index, String feature, String biome) {
        return Mc263FeatureIndexReceipt.biome(biome).featuresAtStep(STEP).stream()
                .anyMatch(r -> r.globalIndex() == index && r.featureKey().equals(feature));
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
                    throw new IllegalArgumentException("invalid SEG3 event " + event.phase);
                }
                out.writeByte(phase.id); out.writeByte(phase.arity);
                for (long value : event.values) out.writeLong(value);
            }
            out.flush(); return bytes.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }
    public static List<Integer> coveredIndices() { return SPECS.keySet().stream().sorted().toList(); }
    public static String featureKey(int index) { return requireSpec(index).key; }

    /** Pure exact capability/state closure check for one placed feature. */
    public static void preflight(int index, WorldAccess world) {
        requireSpec(index);
        preflight(world);
    }
    private static Spec requireSpec(int index) {
        Spec spec = SPECS.get(index);
        if (spec == null) throw new IllegalArgumentException("unsupported seagrass index " + index);
        return spec;
    }
    private static Map<Integer, Spec> specs() {
        Map<Integer, Spec> specs = new LinkedHashMap<>();
        specs.put(90, new Spec(90, "minecraft:seagrass_swamp", 64, 60));
        specs.put(93, new Spec(93, "minecraft:seagrass_river", 48, 40));
        specs.put(100, new Spec(100, "minecraft:seagrass_warm", 80, 30));
        specs.put(102, new Spec(102, "minecraft:seagrass_deep_warm", 80, 80));
        specs.put(104, new Spec(104, "minecraft:seagrass_normal", 48, 30));
        specs.put(105, new Spec(105, "minecraft:seagrass_deep", 48, 80));
        specs.put(106, new Spec(106, "minecraft:seagrass_cold", 32, 30));
        specs.put(107, new Spec(107, "minecraft:seagrass_deep_cold", 40, 80));
        return Map.copyOf(specs);
    }
    private static long stateId(State state) { return keyId(state.canonical()); }
    private static long keyId(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) hash = (hash ^ (b & 255)) * 0x100000001b3L;
        return hash;
    }
    private static String key(String value) {
        if (value == null || !RESOURCE_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid namespaced key: " + value);
        }
        return value;
    }
    private static void emit(TraceSink t, String p, long a, long b, long c) { if (t.enabled()) t.record(p, a, b, c); }
    private static void emit(TraceSink t, String p, long a, long b, long c, long d, long e) { if (t.enabled()) t.record(p, a, b, c, d, e); }
    private static void emit(TraceSink t, String p, long a, long b, long c, long d, long e, long f) { if (t.enabled()) t.record(p, a, b, c, d, e, f); }
    private static void emit(TraceSink t, String p, long a, long b, long c, long d, long e, long f, long g) { if (t.enabled()) t.record(p, a, b, c, d, e, f, g); }
    private static void emit(TraceSink t, String p, long a, long b, long c, long d, long e, long f, long g, long h) { if (t.enabled()) t.record(p, a, b, c, d, e, f, g, h); }

    private record Spec(int index, String key, int count, int tallWeight) { }
    private record PhaseSpec(int id, int arity) { }
    private record Configured(boolean placed, int writes, int retained) { }
    private static final class Totals {
        int candidates, configuredCalls, successes, writes, retained;
        Result result() { return new Result(successes > 0, candidates, configuredCalls,
                successes, writes, retained); }
    }
}
