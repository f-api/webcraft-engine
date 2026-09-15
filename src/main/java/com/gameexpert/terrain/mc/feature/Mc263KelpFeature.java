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

/** Exact dormant Minecraft 26.3-snapshot-7 step-9 kelp family. */
public final class Mc263KelpFeature {
    /** The pinned namespaced-key grammar, compiled once: {@code String.matches} recompiles
     * this pattern on every call, and the key validator runs once per read state. */
    private static final Pattern RESOURCE_KEY =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public static final int STEP = 9;
    public static final int TRACE_MAGIC = 0x4b4c5033; // KLP3
    public static final int TRACE_VERSION = 1;
    public static final String TRACE_SCHEMA = "mc263-kelp-trace-v1";
    public static final String INNER_SERVER_SHA1 = "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String KELP_COLD_JSON_SHA256 =
            "ad6d61fb269cc2ccdc05ecfbc1089a6e85083ca834d4b787559790a56d3e771b";
    public static final String KELP_WARM_JSON_SHA256 =
            "419a4f5a38560ebec940d5b5c97a6f293381bbd917b7748cbbd3114626093320";
    public static final String KELP_CONFIGURED_JSON_SHA256 =
            "cd75e04c4f10a8b94d0c6aef487b1c7129afb3a1ffd5e04c7efac10a843a828b";
    public static final String CANNOT_SUPPORT_KELP_TAG_SHA256 =
            "c4aa173611f0f072f04e625e5d3d7a7b5e1cdb7f81a54103d7f89e4f4faa487b";
    public static final String BLOCK_COLUMN_CLASS_SHA256 =
            "46d37e8cf9613d6115a28f90b38d17e935a529d00043b967d42ffd8b717bd4c6";
    public static final String NOISE_BASED_COUNT_CLASS_SHA256 =
            "87e478a1120cba5f1f86db162059c24f4ec4c826b94b213d9542dfa9b5b4bba4";
    public static final String AQUATIC_FEATURES_CLASS_SHA256 =
            "e06fbf7e7241d304eb3aa1fd64f787afae16998d0195aeb25da6c0c5c538bf53";
    public static final String AQUATIC_PLACEMENTS_CLASS_SHA256 =
            "bf03bd42d14659bab522a86c0c49e9b551bd18d66661a5af3ec8ab2f07e2a72f";
    public static final int[] COVERED_INDICES = {103, 108};

    private static final State BODY = new State("minecraft:kelp_plant", Map.of(),
            "minecraft:water", true, false);
    private static final List<State> TIPS = List.of(20, 21, 22, 23).stream()
            .map(age -> new State("minecraft:kelp", Map.of("age", Integer.toString(age)),
                    "minecraft:water", true, false))
            .toList();
    private static final Map<String, PhaseSpec> PHASES = Map.ofEntries(
            Map.entry("noise", new PhaseSpec(1, 5)),
            Map.entry("rng_int", new PhaseSpec(2, 3)),
            Map.entry("square", new PhaseSpec(3, 5)),
            Map.entry("height", new PhaseSpec(4, 5)),
            Map.entry("read", new PhaseSpec(5, 6)),
            Map.entry("predicate", new PhaseSpec(6, 6)),
            Map.entry("biome", new PhaseSpec(7, 5)),
            Map.entry("layers", new PhaseSpec(8, 5)),
            Map.entry("scan", new PhaseSpec(9, 6)),
            Map.entry("provider", new PhaseSpec(10, 5)),
            Map.entry("write", new PhaseSpec(11, 7)),
            Map.entry("configured_result", new PhaseSpec(12, 7)),
            Map.entry("placed_result", new PhaseSpec(13, 7)));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public boolean enabled() { return false; }
        @Override public void record(String phase, long... values) {
            throw new AssertionError("disabled kelp trace emitted");
        }
    };

    private Mc263KelpFeature() { }

    public enum Heightmap { OCEAN_FLOOR }

    /** Minimal live boundary needed by noise placement, predicates, and BlockColumnFeature. */
    public interface WorldAccess {
        int minGenerationY();
        double flowerNoise(int sourceX, int sourceZ);
        int height(Heightmap heightmap, int x, int z);
        String biomeKey(int x, int y, int z);
        State blockState(int x, int y, int z);
        boolean supportsState(State state);
        boolean trySetBlockState(int x, int y, int z, State state, int flags);
    }

    /** Exact identity, state, fluid, and cannot-support-kelp tag facts. */
    public record State(String block, Map<String, String> properties, String fluid,
                        boolean fluidSource, boolean cannotSupportKelp) {
        public State {
            block = key(block);
            fluid = key(fluid);
            properties = Collections.unmodifiableMap(new TreeMap<>(properties));
            if (fluidSource && fluid.equals("minecraft:empty")) {
                throw new IllegalArgumentException("empty fluid cannot be source");
            }
        }
        public static State block(String block, boolean cannotSupportKelp) {
            return new State(block, Map.of(), "minecraft:empty", false, cannotSupportKelp);
        }
        public static State water(int level) {
            if (level < 0 || level > 15) throw new IllegalArgumentException("water level 0..15");
            return new State("minecraft:water", Map.of("level", Integer.toString(level)),
                    "minecraft:water", level == 0, false);
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
        double noise = (double) (float) world.flowerNoise(sourceX, sourceZ);
        int count = (int) Math.ceil(noise * spec.noiseToCountRatio);
        emit(trace, "noise", index, sourceX, sourceZ,
                Double.doubleToRawLongBits(noise), count);
        Totals totals = new Totals();
        for (int candidate = 0; candidate < count; candidate++) {
            int x = sourceX + nextInt(random, 16, trace);
            int z = sourceZ + nextInt(random, 16, trace);
            emit(trace, "square", index, candidate, x, sourceY, z);
            int y = world.height(Heightmap.OCEAN_FLOOR, x, z);
            emit(trace, "height", index, Heightmap.OCEAN_FLOOR.ordinal(), x, z, y);
            if (y <= world.minGenerationY()) continue;
            totals.candidates++;
            State entry = read(world, x, y, z, trace);
            boolean entryWater = entry.block.equals("minecraft:water");
            emit(trace, "predicate", 0, x, y, z, entryWater ? 1 : 0, index);
            if (!entryWater) continue;
            State upper = read(world, x, y + 1, z, trace);
            boolean upperWater = upper.block.equals("minecraft:water");
            emit(trace, "predicate", 1, x, y + 1, z, upperWater ? 1 : 0, index);
            if (!upperWater) continue;
            State tagLive = read(world, x, y, z, trace);
            boolean supported = !tagLive.cannotSupportKelp;
            emit(trace, "predicate", 2, x, y, z, supported ? 1 : 0, index);
            if (!supported) continue;
            String biome = key(world.biomeKey(x, y, z));
            boolean member = biomeContains(index, spec.key, biome);
            emit(trace, "biome", index, x, y, z, member ? 1 : 0);
            if (!member) continue;
            totals.configuredCalls++;
            Configured configured = configured(index, random, x, y, z, world, trace);
            if (configured.placed) totals.successes++;
            totals.writes += configured.writes;
            totals.retained += configured.retained;
        }
        emit(trace, "placed_result", index, totals.candidates, totals.configuredCalls,
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
        preflight(world);
        Configured configured = configured(index, random, x, y, z, world, trace);
        return new Result(configured.placed, 1, 1, configured.placed ? 1 : 0,
                configured.writes, configured.retained);
    }

    private static Configured configured(int index, WorldgenRandom random, int x, int y, int z,
                                         WorldAccess world, TraceSink trace) {
        int bodyHeight = nextInt(random, 10, trace);
        int tipHeight = 1;
        int totalHeight = bodyHeight + tipHeight;
        emit(trace, "layers", index, bodyHeight, tipHeight, totalHeight, 1);
        for (int step = 0; step < totalHeight; step++) {
            int scanY = y + 1 + step;
            State scan = read(world, x, scanY, z, trace);
            boolean allowed = scan.block.equals("minecraft:water");
            if (allowed) {
                State above = read(world, x, scanY + 1, z, trace);
                allowed = above.block.equals("minecraft:water");
            }
            emit(trace, "scan", index, step, x, scanY, z, allowed ? 1 : 0);
            if (!allowed) {
                int remove = totalHeight - step;
                int fromBody = Math.min(bodyHeight, remove);
                bodyHeight -= fromBody;
                tipHeight -= Math.min(tipHeight, remove - fromBody);
                break;
            }
        }
        int writes = 0, retained = 0;
        for (int offset = 0; offset < bodyHeight; offset++) {
            if (write(world, x, y + offset, z, BODY, trace)) retained++;
            writes++;
        }
        if (tipHeight != 0) {
            int age = 20 + nextInt(random, 4, trace);
            State tip = TIPS.get(age - 20);
            emit(trace, "provider", index, x, y + bodyHeight, z, stateId(tip));
            if (write(world, x, y + bodyHeight, z, tip, trace)) retained++;
            writes++;
        }
        emit(trace, "configured_result", index, 1, bodyHeight, tipHeight,
                writes, retained, totalHeight);
        return new Configured(true, writes, retained);
    }

    public static byte[] encodeTraceFixture(List<TraceEvent> events) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(TRACE_MAGIC);
            out.writeShort(TRACE_VERSION);
            byte[] schema = TRACE_SCHEMA.getBytes(StandardCharsets.UTF_8);
            out.writeShort(schema.length);
            out.write(schema);
            out.writeInt(events.size());
            for (TraceEvent event : events) {
                PhaseSpec phase = PHASES.get(event.phase);
                if (phase == null || event.values.length != phase.arity) {
                    throw new IllegalArgumentException("bad KLP3 event");
                }
                out.writeByte(phase.id);
                out.writeByte(phase.arity);
                for (long value : event.values) out.writeLong(value);
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public static List<Integer> coveredIndices() { return List.of(103, 108); }
    public static String featureKey(int index) { return requireSpec(index).key; }

    /** Pure exact capability/state closure check for one placed feature. */
    public static void preflight(int index, WorldAccess world) {
        requireSpec(index);
        preflight(world);
    }

    private static void preflight(WorldAccess world) {
        if (!world.supportsState(BODY)) {
            throw new UnsupportedOperationException("unregistered exact kelp state: "
                    + BODY.canonical());
        }
        for (State tip : TIPS) {
            if (!world.supportsState(tip)) {
                throw new UnsupportedOperationException("unregistered exact kelp state: "
                        + tip.canonical());
            }
        }
    }
    private static boolean biomeContains(int index, String featureKey, String biome) {
        return Mc263FeatureIndexReceipt.biome(biome).featuresAtStep(STEP).stream()
                .anyMatch(ref -> ref.globalIndex() == index
                        && ref.featureKey().equals(featureKey));
    }
    private static Spec requireSpec(int index) {
        return switch (index) {
            case 103 -> new Spec(103, "minecraft:kelp_warm", 80);
            case 108 -> new Spec(108, "minecraft:kelp_cold", 120);
            default -> throw new IllegalArgumentException("unsupported kelp index " + index);
        };
    }
    private static State read(WorldAccess world, int x, int y, int z, TraceSink trace) {
        State state = world.blockState(x, y, z);
        emit(trace, "read", x, y, z, stateId(state), state.fluidSource ? 1 : 0,
                state.cannotSupportKelp ? 1 : 0);
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

    private record Spec(int index, String key, int noiseToCountRatio) { }
    private record PhaseSpec(int id, int arity) { }
    private record Configured(boolean placed, int writes, int retained) { }
    private static final class Totals {
        int candidates, configuredCalls, successes, writes, retained;
        Result result() {
            return new Result(successes > 0, candidates, configuredCalls, successes,
                    writes, retained);
        }
    }
}
