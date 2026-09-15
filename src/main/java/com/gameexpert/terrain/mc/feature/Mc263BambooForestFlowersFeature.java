package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263WorldgenRandomSource;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Exact dormant 26.3-snapshot-7 step-nine bamboo and forest-flower selector leaves. */
public final class Mc263BambooForestFlowersFeature {
    public static final int STEP = 9;
    public static final int TRACE_MAGIC = 0x42464633; // BFF3
    public static final int TRACE_VERSION = 1;
    public static final String TRACE_SCHEMA = "mc263-bamboo-forest-flowers-trace-v1";
    public static final String INNER_SERVER_SHA1 =
            "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String BAMBOO_CLASS_SHA256 =
            "fc81187289a5f4026fd823c04a1ea482fb81d1a6f5084d69e5a21b16a8089d1d";
    public static final String SIMPLE_BLOCK_CLASS_SHA256 =
            "10745682a08452d5e5f148cfd627fa1ec84bf669570da5cd284ee2119e810101";
    public static final String SIMPLE_RANDOM_SELECTOR_CLASS_SHA256 =
            "68ccaacb1b6202ab376067a0ed8b4c6287ad50ca5d014ce0100d963352650302";
    public static final String DOUBLE_PLANT_CLASS_SHA256 =
            "a52bd483ba51e009812afe8d162403d497bfb8fc4c662e4ee2cfb2d0f0e826e7";
    public static final String NOISE_COUNT_CLASS_SHA256 =
            "87e478a1120cba5f1f86db162059c24f4ec4c826b94b213d9542dfa9b5b4bba4";
    public static final String RARITY_FILTER_CLASS_SHA256 =
            "816b014fad3a867c2fe487ccb417dc2e347dc8bcba773b4413fc843985dbecc6";
    public static final String COUNT_PLACEMENT_CLASS_SHA256 =
            "e2fa737411edc1547e7e9f29d108298fb87b6b03709a6b56fcfa9df1cd30d987";
    public static final String HEIGHTMAP_PLACEMENT_CLASS_SHA256 =
            "ab73361bbb5b2ed3e5083b9518a6b10725629291f9fc17e7ba951d11f1df94e2";

    public static final String BAMBOO_SOME_PODZOL_JSON_SHA256 =
            "d1e9d4fd970e4ccbcdfcc2f5f39814bf112265c3e5c297704b01dbc3ae7c18c5";
    public static final String BAMBOO_NO_PODZOL_JSON_SHA256 =
            "eacf8bdb95481a545edfd9f1cf5f96a06a1dc8202120d3e0f2bf4012609090e7";
    public static final String FOREST_FLOWERS_JSON_SHA256 =
            "85827cf46bebe803e84e759b5b4dd5c2c246300773d29b62a5ba4cb3256bc957";
    public static final String BAMBOO_PLACED_JSON_SHA256 =
            "63df754f012c9e766914f25107ee1303552260913ed7410d51a321fc84c88e1e";
    public static final String BAMBOO_LIGHT_PLACED_JSON_SHA256 =
            "0f6601f47be6a2933450aeb28908ad9c72e0052b7d60fccf36d702011a082b99";
    public static final String FLOWER_FOREST_FLOWERS_PLACED_JSON_SHA256 =
            "bbc2c9e34dfe1eb41cfd7154e1e57a263efc7172e784d44bb841d1bcd00ceb98";
    public static final String FOREST_FLOWERS_PLACED_JSON_SHA256 =
            "f1cb30462d959bd314dbb03cfdb3e186a52f3a2f306c57f637cd7ece5ddf89fc";

    public static final int[] COVERED_INDICES = {2, 9, 21, 24};

    private static final State PODZOL = State.of("minecraft:podzol")
            .property("snowy", "false");
    private static final State BAMBOO_DEFAULT = bamboo(0, "none", 0);
    private static final State BAMBOO_TRUNK = bamboo(1, "none", 0);
    private static final State BAMBOO_FINAL_LARGE = bamboo(1, "large", 1);
    private static final State BAMBOO_TOP_LARGE = bamboo(1, "large", 0);
    private static final State BAMBOO_TOP_SMALL = bamboo(1, "small", 0);
    private static final State[] FLOWERS = {
            State.of("minecraft:lilac"), State.of("minecraft:rose_bush"),
            State.of("minecraft:peony"), State.of("minecraft:lily_of_the_valley")};

    private static final Map<String, PhaseSpec> PHASES = Map.ofEntries(
            Map.entry("outer", new PhaseSpec(1, 5)),
            Map.entry("noise", new PhaseSpec(2, 4)),
            Map.entry("rng_int", new PhaseSpec(3, 3)),
            Map.entry("rng_float", new PhaseSpec(4, 3)),
            Map.entry("square", new PhaseSpec(5, 5)),
            Map.entry("height", new PhaseSpec(6, 6)),
            Map.entry("count", new PhaseSpec(7, 6)),
            Map.entry("biome", new PhaseSpec(8, 6)),
            Map.entry("offset", new PhaseSpec(9, 8)),
            Map.entry("read", new PhaseSpec(10, 7)),
            Map.entry("predicate", new PhaseSpec(11, 7)),
            Map.entry("selector", new PhaseSpec(12, 5)),
            Map.entry("survive", new PhaseSpec(13, 6)),
            Map.entry("write", new PhaseSpec(14, 8)),
            Map.entry("configured_result", new PhaseSpec(15, 8)),
            Map.entry("placed_result", new PhaseSpec(16, 8)));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public boolean enabled() { return false; }
        @Override public void record(String phase, long... values) {
            throw new AssertionError("disabled bamboo/forest-flower trace emitted");
        }
    };

    private Mc263BambooForestFlowersFeature() { }

    public enum Heightmap { WORLD_SURFACE_WG, MOTION_BLOCKING, WORLD_SURFACE }

    /** Minimal live boundary required by these four exact placed-feature leaves. */
    public interface WorldAccess {
        int minGenerationY();
        int height(Heightmap heightmap, int x, int z);
        String biomeKey(int x, int y, int z);
        /** Exact {@code Biome.BIOME_INFO_NOISE.get(x / 80.0, z / 80.0)} result. */
        double bambooCountNoise(int sourceX, int sourceZ);
        State blockState(int x, int y, int z);
        boolean canSurvive(State state, int x, int y, int z);
        boolean beneathBambooPodzolReplaceable(State state);
        boolean supportsFeature(int globalIndex);
        boolean supportsState(State state);
        boolean trySetBlockState(int x, int y, int z, State state, int flags);
    }

    /** Exact namespaced state and the only live facts consumed by this leaf family. */
    public record State(String block, Map<String, String> properties, String fluid,
                        boolean airTag, boolean replaceable) {
        public State {
            block = key(block);
            fluid = key(fluid);
            properties = Collections.unmodifiableMap(new TreeMap<>(properties));
        }
        public static State of(String block) {
            return new State(block, Map.of(), "minecraft:empty", false, false);
        }
        public static State of(String block, boolean airTag, boolean replaceable) {
            return new State(block, Map.of(), "minecraft:empty", airTag, replaceable);
        }
        public State property(String name, String value) {
            Map<String, String> next = new TreeMap<>(properties);
            next.put(name, value);
            return new State(block, next, fluid, airTag, replaceable);
        }
        public State half(String half) { return property("half", half); }
        public String canonical() {
            if (properties.isEmpty()) return block;
            return block + properties.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .reduce("[", (left, right) -> left.equals("[")
                            ? left + right : left + "," + right) + "]";
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

    public static List<Integer> coveredIndices() { return List.of(2, 9, 21, 24); }

    public static String featureKey(int index) {
        return switch (index) {
            case 2 -> "minecraft:bamboo";
            case 9 -> "minecraft:bamboo_light";
            case 21 -> "minecraft:flower_forest_flowers";
            case 24 -> "minecraft:forest_flowers";
            default -> throw new IllegalArgumentException("unsupported index " + index);
        };
    }

    /** Pure exact capability/state closure check for one placed feature. */
    public static void preflightPlaced(int index, WorldAccess world) {
        requireIndex(index);
        preflight(index, world);
    }

    public static Result placeWithFeatureRandom(int index, Mc263WorldgenRandomSource random,
            int sourceX, int sourceY, int sourceZ, WorldAccess world) {
        return placeWithFeatureRandom(index, random, sourceX, sourceY, sourceZ, world, NO_TRACE);
    }

    public static Result placeWithFeatureRandom(int index, Mc263WorldgenRandomSource random,
            int sourceX, int sourceY, int sourceZ, WorldAccess world, TraceSink trace) {
        requireIndex(index);
        preflight(index, world);
        Totals totals = new Totals();
        if (index == 2) {
            double noise = world.bambooCountNoise(sourceX, sourceZ);
            int count = (int) Math.ceil((noise + 0.3D) * 160.0D);
            emit(trace, "noise", index, Double.doubleToRawLongBits(noise), sourceX, sourceZ);
            emit(trace, "outer", index, count, 160, 80, Double.doubleToRawLongBits(0.3D));
            for (int outer = 0; outer < count; outer++) {
                placeBambooCandidate(index, random, sourceX, sourceY, sourceZ, outer,
                        Heightmap.WORLD_SURFACE_WG, world, trace, totals);
            }
        } else if (index == 9) {
            emit(trace, "outer", index, 1, 4, 0, 0);
            if (chance(random, 0.25F, 900, trace)) {
                placeBambooCandidate(index, random, sourceX, sourceY, sourceZ, 0,
                        Heightmap.MOTION_BLOCKING, world, trace, totals);
            }
        } else {
            placeForestFlowers(index, random, sourceX, sourceY, sourceZ, world, trace, totals);
        }
        emit(trace, "placed_result", index, totals.candidates, totals.calls, totals.successes,
                totals.writes, totals.retained, totals.successes > 0 ? 1 : 0, 0);
        return totals.result();
    }

    public static Result placeConfigured(int index, Mc263WorldgenRandomSource random,
            int x, int y, int z, WorldAccess world) {
        return placeConfigured(index, random, x, y, z, world, NO_TRACE);
    }

    public static Result placeConfigured(int index, Mc263WorldgenRandomSource random,
            int x, int y, int z, WorldAccess world, TraceSink trace) {
        requireIndex(index);
        preflight(index, world);
        Totals totals = new Totals();
        if (index == 2 || index == 9) {
            totals.candidates = 1;
            totals.calls = 1;
            if (bambooConfigured(index, random, x, y, z, world, trace, totals)) {
                totals.successes++;
            }
        } else {
            forestFlowersConfigured(index, random, x, y, z, world, trace, totals);
        }
        emit(trace, "configured_result", index, totals.successes > 0 ? 1 : 0, totals.candidates,
                totals.calls, totals.successes, totals.writes, totals.retained, 0);
        return totals.result();
    }

    private static void placeBambooCandidate(int index, Mc263WorldgenRandomSource random,
            int sourceX, int sourceY, int sourceZ, int outer, Heightmap heightmap,
            WorldAccess world, TraceSink trace, Totals totals) {
        int x = sourceX + nextInt(random, 16, index * 100 + 1, trace);
        int z = sourceZ + nextInt(random, 16, index * 100 + 2, trace);
        emit(trace, "square", index, outer, x, sourceY, z);
        int y = world.height(heightmap, x, z);
        emit(trace, "height", index, heightmap.ordinal(), x, z, y, outer);
        if (y <= world.minGenerationY() || !biome(index, x, y, z, world, trace)) return;
        totals.candidates++;
        totals.calls++;
        if (bambooConfigured(index, random, x, y, z, world, trace, totals)) totals.successes++;
    }

    private static void placeForestFlowers(int index, Mc263WorldgenRandomSource random,
            int sourceX, int sourceY, int sourceZ, WorldAccess world, TraceSink trace,
            Totals totals) {
        emit(trace, "outer", index, 1, 7, index == 21 ? -1 : -3,
                index == 21 ? 3 : 1);
        if (!chance(random, 1.0F / 7.0F, index * 100, trace)) return;
        int x = sourceX + nextInt(random, 16, index * 100 + 1, trace);
        int z = sourceZ + nextInt(random, 16, index * 100 + 2, trace);
        emit(trace, "square", index, 0, x, sourceY, z);
        int y = world.height(Heightmap.MOTION_BLOCKING, x, z);
        emit(trace, "height", index, Heightmap.MOTION_BLOCKING.ordinal(), x, z, y, 0);
        if (y <= world.minGenerationY()) return;
        int minimum = index == 21 ? -1 : -3;
        int maximum = index == 21 ? 3 : 1;
        int sampled = minimum + nextInt(random, maximum - minimum + 1,
                index * 100 + 3, trace);
        int count = Math.max(0, Math.min(index == 21 ? 3 : 1, sampled));
        emit(trace, "count", index, sampled, count, minimum, maximum, 0);
        for (int counted = 0; counted < count; counted++) {
            if (!biome(index, x, y, z, world, trace)) continue;
            forestFlowersConfigured(index, random, x, y, z, world, trace, totals);
        }
    }

    private static boolean forestFlowersConfigured(int index, Mc263WorldgenRandomSource random,
            int x, int y, int z, WorldAccess world, TraceSink trace, Totals totals) {
        int selected = nextInt(random, FLOWERS.length, index * 100 + 4, trace);
        emit(trace, "selector", index, selected, FLOWERS.length, x, packYz(y, z));
        boolean placed = false;
        for (int inner = 0; inner < 96; inner++) {
            int ox = triangle(random, 7, index * 100 + 5, trace);
            int oy = triangle(random, 3, index * 100 + 6, trace);
            int oz = triangle(random, 7, index * 100 + 7, trace);
            int px = x + ox, py = y + oy, pz = z + oz;
            emit(trace, "offset", index, 0, inner, ox, oy, oz, px, packYz(py, pz));
            totals.candidates++;
            State current = read(world, px, py, pz, trace);
            emit(trace, "predicate", index, 1, px, py, pz,
                    current.airTag ? 1 : 0, stateId(current));
            if (!current.airTag) continue;
            totals.calls++;
            if (simpleBlock(index, selected, px, py, pz, world, trace, totals)) {
                totals.successes++;
                placed = true;
            }
        }
        return placed;
    }

    private static boolean simpleBlock(int index, int selected, int x, int y, int z,
            WorldAccess world, TraceSink trace, Totals totals) {
        State flower = FLOWERS[selected];
        boolean survives = world.canSurvive(flower, x, y, z);
        emit(trace, "survive", index, x, y, z, stateId(flower), survives ? 1 : 0);
        if (!survives) return false;
        if (selected < 3) {
            State above = read(world, x, y + 1, z, trace);
            if (!above.airTag
                    && (!flower.fluid.equals(above.fluid) || !above.replaceable)) return false;
            totals.retained += write(world, x, y, z, flower.half("lower"), 2, trace) ? 1 : 0;
            totals.writes++;
            totals.retained += write(world, x, y + 1, z, flower.half("upper"), 2, trace) ? 1 : 0;
            totals.writes++;
        } else {
            totals.retained += write(world, x, y, z, flower, 2, trace) ? 1 : 0;
            totals.writes++;
        }
        return true;
    }

    private static boolean bambooConfigured(int index, Mc263WorldgenRandomSource random,
            int x, int y, int z, WorldAccess world, TraceSink trace, Totals totals) {
        State origin = read(world, x, y, z, trace);
        boolean empty = origin.airTag;
        emit(trace, "predicate", index, 2, x, y, z, empty ? 1 : 0, stateId(origin));
        if (!empty) return false;
        boolean survives = world.canSurvive(BAMBOO_DEFAULT, x, y, z);
        emit(trace, "survive", index, x, y, z, stateId(BAMBOO_DEFAULT), survives ? 1 : 0);
        if (survives) {
            int height = nextInt(random, 12, index * 100 + 10, trace) + 5;
            float probability = index == 2 ? 0.2F : 0.0F;
            if (chance(random, probability, index * 100 + 11, trace)) {
                int radius = nextInt(random, 4, index * 100 + 12, trace) + 1;
                for (int xx = x - radius; xx <= x + radius; xx++) {
                    for (int zz = z - radius; zz <= z + radius; zz++) {
                        int dx = xx - x, dz = zz - z;
                        if (dx * dx + dz * dz > radius * radius) continue;
                        int yy = world.height(Heightmap.WORLD_SURFACE, xx, zz) - 1;
                        emit(trace, "height", index, Heightmap.WORLD_SURFACE.ordinal(),
                                xx, zz, yy + 1, radius);
                        State ground = read(world, xx, yy, zz, trace);
                        boolean matches = world.beneathBambooPodzolReplaceable(ground);
                        emit(trace, "predicate", index, 3, xx, yy, zz,
                                matches ? 1 : 0, stateId(ground));
                        if (!matches) continue;
                        totals.retained += write(world, xx, yy, zz, PODZOL, 2, trace) ? 1 : 0;
                        totals.writes++;
                    }
                }
            }
            int placed = 0;
            while (placed < height) {
                State live = read(world, x, y + placed, z, trace);
                if (!live.airTag) break;
                totals.retained += write(world, x, y + placed, z, BAMBOO_TRUNK, 2, trace)
                        ? 1 : 0;
                totals.writes++;
                placed++;
            }
            if (placed >= 3) {
                totals.retained += write(world, x, y + placed, z, BAMBOO_FINAL_LARGE, 2, trace)
                        ? 1 : 0;
                totals.writes++;
                totals.retained += write(world, x, y + placed - 1, z, BAMBOO_TOP_LARGE, 2, trace)
                        ? 1 : 0;
                totals.writes++;
                totals.retained += write(world, x, y + placed - 2, z, BAMBOO_TOP_SMALL, 2, trace)
                        ? 1 : 0;
                totals.writes++;
            }
        }
        // BambooFeature increments its result for an empty origin even when survival fails.
        return true;
    }

    private static boolean biome(int index, int x, int y, int z, WorldAccess world,
            TraceSink trace) {
        String biome = key(world.biomeKey(x, y, z));
        boolean member = switch (index) {
            case 2 -> biome.equals("minecraft:bamboo_jungle");
            case 9 -> biome.equals("minecraft:jungle");
            case 21 -> biome.equals("minecraft:flower_forest");
            case 24 -> biome.equals("minecraft:forest")
                    || biome.equals("minecraft:birch_forest")
                    || biome.equals("minecraft:dark_forest")
                    || biome.equals("minecraft:old_growth_birch_forest");
            default -> throw new AssertionError(index);
        };
        emit(trace, "biome", index, x, y, z, member ? 1 : 0, keyId(biome));
        return member;
    }

    private static void preflight(int index, WorldAccess world) {
        if (!world.supportsFeature(index)) {
            throw new UnsupportedOperationException("unsupported exact feature global index "
                    + index + ": " + featureKey(index));
        }
        State[] required = index == 2 || index == 9
                ? new State[]{BAMBOO_DEFAULT, BAMBOO_TRUNK, BAMBOO_FINAL_LARGE,
                        BAMBOO_TOP_LARGE, BAMBOO_TOP_SMALL, PODZOL}
                : flowerStates();
        for (State state : required) {
            if (!world.supportsState(state)) {
                throw new UnsupportedOperationException("unregistered exact state: "
                        + state.canonical());
            }
        }
    }

    private static State[] flowerStates() {
        return new State[]{FLOWERS[0], FLOWERS[0].half("lower"), FLOWERS[0].half("upper"),
                FLOWERS[1], FLOWERS[1].half("lower"), FLOWERS[1].half("upper"),
                FLOWERS[2], FLOWERS[2].half("lower"), FLOWERS[2].half("upper"), FLOWERS[3]};
    }

    public static byte[] encodeTraceFixture(List<TraceEvent> events) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(TRACE_MAGIC);
            output.writeShort(TRACE_VERSION);
            byte[] schema = TRACE_SCHEMA.getBytes(StandardCharsets.UTF_8);
            output.writeShort(schema.length);
            output.write(schema);
            output.writeInt(events.size());
            for (TraceEvent event : events) {
                PhaseSpec phase = PHASES.get(event.phase);
                if (phase == null || event.values.length != phase.arity) {
                    throw new IllegalArgumentException("invalid trace event: " + event.phase);
                }
                output.writeByte(phase.id);
                output.writeByte(phase.arity);
                for (long value : event.values) output.writeLong(value);
            }
            output.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static State read(WorldAccess world, int x, int y, int z, TraceSink trace) {
        State state = world.blockState(x, y, z);
        emit(trace, "read", x, y, z, stateId(state), state.airTag ? 1 : 0,
                state.replaceable ? 1 : 0, keyId(state.fluid));
        return state;
    }

    private static boolean write(WorldAccess world, int x, int y, int z, State state,
            int flags, TraceSink trace) {
        boolean retained = world.trySetBlockState(x, y, z, state, flags);
        emit(trace, "write", x, y, z, stateId(state), flags, retained ? 1 : 0,
                state.properties.hashCode(), keyId(state.block));
        return retained;
    }

    private static int nextInt(Mc263WorldgenRandomSource random, int bound, int site, TraceSink trace) {
        int value = random.nextInt(bound);
        emit(trace, "rng_int", bound, value, site);
        return value;
    }

    private static boolean chance(Mc263WorldgenRandomSource random, float probability, int site,
            TraceSink trace) {
        float value = random.nextFloat();
        emit(trace, "rng_float", Float.floatToRawIntBits(value),
                Float.floatToRawIntBits(probability), site);
        return value < probability;
    }

    private static int triangle(Mc263WorldgenRandomSource random, int range, int site, TraceSink trace) {
        return nextInt(random, range + 1, site, trace)
                - nextInt(random, range + 1, site, trace);
    }

    private static State bamboo(int age, String leaves, int stage) {
        return State.of("minecraft:bamboo").property("age", Integer.toString(age))
                .property("leaves", leaves).property("stage", Integer.toString(stage));
    }

    private static void requireIndex(int index) {
        if (index != 2 && index != 9 && index != 21 && index != 24) {
            throw new IllegalArgumentException("unsupported index " + index);
        }
    }

    private static String key(String value) {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("invalid namespaced key: " + value);
        }
        return value;
    }

    private static long stateId(State state) { return keyId(state.canonical()); }
    private static long keyId(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte element : value.getBytes(StandardCharsets.UTF_8)) {
            hash = (hash ^ (element & 255)) * 0x100000001b3L;
        }
        return hash;
    }
    private static long packYz(int y, int z) { return y ^ ((long) z << 32); }

    private static void emit(TraceSink sink, String phase, long... values) {
        if (sink.enabled()) sink.record(phase, values);
    }

    private record PhaseSpec(int id, int arity) { }
    private static final class Totals {
        int candidates;
        int calls;
        int successes;
        int writes;
        int retained;
        Result result() {
            return new Result(successes > 0, candidates, calls, successes, writes, retained);
        }
    }
}
