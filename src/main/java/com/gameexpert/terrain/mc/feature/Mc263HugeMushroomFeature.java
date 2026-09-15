package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263WorldgenRandomSource;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Exact dormant 26.3 step-9 index-82 mushroom-island selector and huge mushrooms. */
public final class Mc263HugeMushroomFeature {
    public static final int STEP = 9, INDEX = 82;
    public static final int TRACE_MAGIC = 0x484d4733; // HMG3
    public static final int TRACE_VERSION = 1;
    public static final String TRACE_SCHEMA = "mc263-huge-mushroom-trace-v1";
    public static final String INNER_SERVER_SHA1 = "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String PLACED_JSON_SHA256 =
            "4ee01004485ee375bd06c327126b0b93b75527910ca7a63c0980753ba7e945ae";
    public static final String SELECTOR_JSON_SHA256 =
            "e62001d1ea553abfe8dbd1246e4ab2ee520f5d7ff5093e61eeef8609de917026";
    public static final String HUGE_BROWN_JSON_SHA256 =
            "39c765067f9a5db715dc217d9635c5d7fbc09aab0cce939afa9c7fac5f5536c6";
    public static final String HUGE_RED_JSON_SHA256 =
            "8088eb9c72b6881a0fbde8a5f79b7867bc47b799455142a5819ae3eb1559fd08";
    public static final String SELECTOR_CLASS_SHA256 =
            "b8982e208eb957d862dbe870b21fdbd12024ca2c7d181555a1c6dc5a8a71e7c6";
    public static final String ABSTRACT_HUGE_CLASS_SHA256 =
            "0694d6a1bfa89ea8671e69eceb02eb10f4dc6780f0956eba51b8026cce1350b0";
    public static final String HUGE_BROWN_CLASS_SHA256 =
            "dc1f30427462d35984009e9fbcf2b36d1c6177e2bab1ce6f06945ee1db263f50";
    public static final String HUGE_RED_CLASS_SHA256 =
            "f0c6051ee43ba2348cd2cfb5bc749d5db765f7f76cc885198ee8571ca6c3425c";
    public static final String HUGE_BROWN_KEY = "minecraft:huge_brown_mushroom";
    public static final String HUGE_RED_KEY = "minecraft:huge_red_mushroom";

    private static final String KEY = "minecraft:mushroom_island_vegetation";
    private static final State BROWN_CAP = mushroom("minecraft:brown_mushroom_block", true);
    private static final State RED_CAP = mushroom("minecraft:red_mushroom_block", true);
    private static final State STEM = mushroom("minecraft:mushroom_stem", false);
    private static final List<State> BROWN_OUTPUT_STATES = brownOutputStates();
    private static final List<State> RED_OUTPUT_STATES = redOutputStates();
    private static final List<State> OUTPUT_STATES = outputStates();
    private static final Map<String, PhaseSpec> PHASES = Map.ofEntries(
            Map.entry("rng_bool", new PhaseSpec(1, 2)),
            Map.entry("rng_int", new PhaseSpec(2, 3)),
            Map.entry("square", new PhaseSpec(3, 5)),
            Map.entry("height", new PhaseSpec(4, 4)),
            Map.entry("biome", new PhaseSpec(5, 5)),
            Map.entry("read", new PhaseSpec(6, 7)),
            Map.entry("validate", new PhaseSpec(7, 6)),
            Map.entry("provider", new PhaseSpec(8, 5)),
            Map.entry("write", new PhaseSpec(9, 7)),
            Map.entry("result", new PhaseSpec(10, 7)));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public boolean enabled() { return false; }
        @Override public void record(String phase, long... values) {
            throw new AssertionError("disabled huge-mushroom trace emitted");
        }
    };

    private Mc263HugeMushroomFeature() { }

    public interface WorldAccess {
        int minGenerationY();
        int maxGenerationYExclusive();
        int motionBlockingHeight(int x, int z);
        String biomeKey(int x, int y, int z);
        State blockState(int x, int y, int z);
        boolean supportsFeature(int globalIndex);
        boolean supportsState(State state);
        boolean trySetBlockState(int x, int y, int z, State state, int flags);
    }

    public record State(String block, Map<String, String> properties, boolean airTag,
                        boolean leavesTag, boolean replaceableByMushroomsTag,
                        boolean hugeMushroomSubstrateTag) {
        public State {
            block = key(block);
            properties = Collections.unmodifiableMap(new TreeMap<>(properties));
        }
        public static State air() {
            return new State("minecraft:air", Map.of(), true, false, false, false);
        }
        public static State block(String block) {
            return new State(block, Map.of(), false, false, false, false);
        }
        public State property(String name, boolean value) {
            Map<String, String> next = new TreeMap<>(properties);
            next.put(name, Boolean.toString(value));
            return new State(block, next, airTag, leavesTag, replaceableByMushroomsTag,
                    hugeMushroomSubstrateTag);
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
    public record Result(boolean placed, boolean red, int height, int reads,
                         int attemptedWrites, int retainedWrites) { }

    public static Result placeWithFeatureRandom(Mc263WorldgenRandomSource random, int sourceX, int sourceY,
                                                int sourceZ, WorldAccess world) {
        return placeWithFeatureRandom(random, sourceX, sourceY, sourceZ, world, NO_TRACE);
    }
    public static Result placeWithFeatureRandom(Mc263WorldgenRandomSource random, int sourceX, int sourceY,
                                                int sourceZ, WorldAccess world, TraceSink trace) {
        preflight(world);
        int x = sourceX + nextInt(random, 16, trace);
        int z = sourceZ + nextInt(random, 16, trace);
        emit(trace, "square", INDEX, x, sourceY, z, 0);
        int y = world.motionBlockingHeight(x, z);
        emit(trace, "height", INDEX, x, z, y);
        if (y <= world.minGenerationY()) return result(trace, false, false, 0, new Counts());
        boolean member = Mc263FeatureIndexReceipt.biome(world.biomeKey(x, y, z))
                .featuresAtStep(STEP).stream().anyMatch(ref -> ref.globalIndex() == INDEX
                        && ref.featureKey().equals(KEY));
        emit(trace, "biome", INDEX, x, y, z, member ? 1 : 0);
        return member ? placeConfigured(random, x, y, z, world, trace)
                : result(trace, false, false, 0, new Counts());
    }

    public static Result placeConfigured(Mc263WorldgenRandomSource random, int x, int y, int z,
                                         WorldAccess world) {
        return placeConfigured(random, x, y, z, world, NO_TRACE);
    }
    public static Result placeConfigured(Mc263WorldgenRandomSource random, int x, int y, int z,
                                         WorldAccess world, TraceSink trace) {
        preflight(world);
        boolean red = random.nextBoolean();
        emit(trace, "rng_bool", red ? 1 : 0, 2);
        return placeSelected(red, random, x, y, z, world, trace);
    }

    /** Pure capability preflight for an already-selected configured huge-mushroom leaf. */
    public static void preflightConfigured(String configuredKey, WorldAccess world) {
        preflightStates(configuredStates(configuredKey), world);
    }

    /** Places an already-selected configured leaf without consuming the selector boolean. */
    public static Result placeConfigured(String configuredKey, Mc263WorldgenRandomSource random,
                                         int x, int y, int z, WorldAccess world) {
        return placeConfigured(configuredKey, random, x, y, z, world, NO_TRACE);
    }

    /** Places an already-selected configured leaf without consuming the selector boolean. */
    public static Result placeConfigured(String configuredKey, Mc263WorldgenRandomSource random,
                                         int x, int y, int z, WorldAccess world,
                                         TraceSink trace) {
        preflightConfigured(configuredKey, world);
        boolean red = configuredKey.equals(HUGE_RED_KEY);
        return placeSelected(red, random, x, y, z, world, trace);
    }

    private static Result placeSelected(boolean red, Mc263WorldgenRandomSource random,
                                        int x, int y, int z, WorldAccess world,
                                        TraceSink trace) {
        int height = nextInt(random, 3, trace) + 4;
        if (nextInt(random, 12, trace) == 0) height *= 2;
        Counts counts = new Counts();
        if (y < world.minGenerationY() + 1
                || y + height + 1 > world.maxGenerationYExclusive()) {
            emit(trace, "validate", red ? 1 : 0, x, y, z, height, 0);
            return result(trace, false, red, height, counts);
        }
        State below = read(world, x, y - 1, z, counts, trace);
        if (!below.hugeMushroomSubstrateTag) {
            emit(trace, "validate", red ? 1 : 0, x, y, z, height, 0);
            return result(trace, false, red, height, counts);
        }
        for (int dy = 0; dy <= height; dy++) {
            int radius = red ? 0 : dy <= 3 ? 0 : 3;
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                State state = read(world, x + dx, y + dy, z + dz, counts, trace);
                if (!state.airTag && !state.leavesTag) {
                    emit(trace, "validate", red ? 1 : 0, x, y, z, height, 0);
                    return result(trace, false, red, height, counts);
                }
            }
        }
        emit(trace, "validate", red ? 1 : 0, x, y, z, height, 1);
        if (red) makeRedCap(world, x, y, z, height, counts, trace);
        else makeBrownCap(world, x, y, z, height, counts, trace);
        for (int dy = 0; dy < height; dy++) {
            placeBlock(world, x, y + dy, z, STEM, counts, trace);
        }
        return result(trace, true, red, height, counts);
    }

    private static void makeBrownCap(WorldAccess world, int x, int y, int z, int height,
                                     Counts counts, TraceSink trace) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            boolean minX = dx == -3, maxX = dx == 3, minZ = dz == -3, maxZ = dz == 3;
            boolean xEdge = minX || maxX, zEdge = minZ || maxZ;
            if (xEdge && zEdge) continue;
            State state = BROWN_CAP.property("west", minX || zEdge && dx == -2)
                    .property("east", maxX || zEdge && dx == 2)
                    .property("north", minZ || xEdge && dz == -2)
                    .property("south", maxZ || xEdge && dz == 2);
            placeBlock(world, x + dx, y + height, z + dz, state, counts, trace);
        }
    }

    private static void makeRedCap(WorldAccess world, int x, int y, int z, int height,
                                   Counts counts, TraceSink trace) {
        for (int dy = height - 3; dy <= height; dy++) {
            int radius = dy < height ? 2 : 1;
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                boolean xEdge = dx == -radius || dx == radius;
                boolean zEdge = dz == -radius || dz == radius;
                if (dy < height && xEdge == zEdge) continue;
                State state = RED_CAP.property("up", dy >= height - 1)
                        .property("west", dx < 0).property("east", dx > 0)
                        .property("north", dz < 0).property("south", dz > 0);
                placeBlock(world, x + dx, y + dy, z + dz, state, counts, trace);
            }
        }
    }

    private static void placeBlock(WorldAccess world, int x, int y, int z, State state,
                                   Counts counts, TraceSink trace) {
        emit(trace, "provider", stateId(state), x, y, z, 0);
        State current = read(world, x, y, z, counts, trace);
        if (!current.airTag && !current.replaceableByMushroomsTag) return;
        boolean retained = world.trySetBlockState(x, y, z, state, 3);
        counts.writes++;
        if (retained) counts.retained++;
        emit(trace, "write", x, y, z, stateId(state), 3, retained ? 1 : 0,
                state.properties.hashCode());
    }

    private static State read(WorldAccess world, int x, int y, int z, Counts counts,
                              TraceSink trace) {
        State state = world.blockState(x, y, z);
        counts.reads++;
        emit(trace, "read", x, y, z, stateId(state), state.airTag ? 1 : 0,
                state.leavesTag ? 1 : 0, state.replaceableByMushroomsTag ? 1 : 0);
        return state;
    }
    private static Result result(TraceSink trace, boolean placed, boolean red, int height,
                                 Counts counts) {
        emit(trace, "result", INDEX, placed ? 1 : 0, red ? 1 : 0, height, counts.reads,
                counts.writes, counts.retained);
        return new Result(placed, red, height, counts.reads, counts.writes, counts.retained);
    }
    /** Pure exact transitive capability/state closure check for the placed selector. */
    public static void preflight(WorldAccess world) {
        if (!world.supportsFeature(INDEX)) {
            throw new UnsupportedOperationException("unsupported exact huge-mushroom selector");
        }
        preflightStates(OUTPUT_STATES, world);
    }

    private static void preflightStates(List<State> states, WorldAccess world) {
        for (State state : states) if (!world.supportsState(state)) {
            throw new UnsupportedOperationException("unregistered huge-mushroom state: "
                    + state.canonical());
        }
    }

    private static List<State> configuredStates(String configuredKey) {
        return switch (configuredKey) {
            case HUGE_BROWN_KEY -> BROWN_OUTPUT_STATES;
            case HUGE_RED_KEY -> RED_OUTPUT_STATES;
            default -> throw new IllegalArgumentException(
                    "unsupported huge-mushroom configured key " + configuredKey);
        };
    }

    public static byte[] encodeTraceFixture(List<TraceEvent> events) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(TRACE_MAGIC); out.writeShort(TRACE_VERSION);
            byte[] schema = TRACE_SCHEMA.getBytes(StandardCharsets.UTF_8);
            out.writeShort(schema.length); out.write(schema); out.writeInt(events.size());
            for (TraceEvent event : events) {
                PhaseSpec spec = PHASES.get(event.phase);
                if (spec == null || spec.arity != event.values.length) {
                    throw new IllegalArgumentException("bad HMG3 event");
                }
                out.writeByte(spec.id); out.writeByte(spec.arity);
                for (long value : event.values) out.writeLong(value);
            }
            out.flush(); return bytes.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }

    private static List<State> outputStates() {
        Set<State> states = new LinkedHashSet<>();
        states.addAll(BROWN_OUTPUT_STATES);
        states.addAll(RED_OUTPUT_STATES);
        return List.copyOf(states);
    }
    private static List<State> brownOutputStates() {
        Set<State> states = new LinkedHashSet<>();
        states.add(STEM);
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            boolean minX = dx == -3, maxX = dx == 3, minZ = dz == -3, maxZ = dz == 3;
            boolean xEdge = minX || maxX, zEdge = minZ || maxZ;
            if (!(xEdge && zEdge)) states.add(BROWN_CAP
                    .property("west", minX || zEdge && dx == -2)
                    .property("east", maxX || zEdge && dx == 2)
                    .property("north", minZ || xEdge && dz == -2)
                    .property("south", maxZ || xEdge && dz == 2));
        }
        return List.copyOf(states);
    }
    private static List<State> redOutputStates() {
        Set<State> states = new LinkedHashSet<>();
        states.add(STEM);
        int height = 3;
        for (int dy = 0; dy <= height; dy++) {
            int radius = dy < height ? 2 : 1;
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                boolean xEdge = dx == -radius || dx == radius;
                boolean zEdge = dz == -radius || dz == radius;
                if (dy < height && xEdge == zEdge) continue;
                states.add(RED_CAP.property("up", dy >= height - 1)
                        .property("west", dx < 0).property("east", dx > 0)
                        .property("north", dz < 0).property("south", dz > 0));
            }
        }
        return List.copyOf(states);
    }
    private static State mushroom(String block, boolean up) {
        return State.block(block).property("down", false).property("east", true)
                .property("north", true).property("south", true).property("up", up)
                .property("west", true);
    }
    private static int nextInt(Mc263WorldgenRandomSource random, int bound, TraceSink trace) {
        int value = random.nextInt(bound);
        emit(trace, "rng_int", bound, value, 0);
        return value;
    }
    private static long stateId(State state) { return keyId(state.canonical()); }
    private static long keyId(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte item : value.getBytes(StandardCharsets.UTF_8))
            hash = (hash ^ (item & 255)) * 0x100000001b3L;
        return hash;
    }
    private static String key(String value) {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("invalid key");
        return value;
    }
    private static void emit(TraceSink t,String p,long a,long b){if(t.enabled())t.record(p,a,b);}
    private static void emit(TraceSink t,String p,long a,long b,long c){if(t.enabled())t.record(p,a,b,c);}
    private static void emit(TraceSink t,String p,long a,long b,long c,long d){if(t.enabled())t.record(p,a,b,c,d);}
    private static void emit(TraceSink t,String p,long a,long b,long c,long d,long e){if(t.enabled())t.record(p,a,b,c,d,e);}
    private static void emit(TraceSink t,String p,long a,long b,long c,long d,long e,long f){if(t.enabled())t.record(p,a,b,c,d,e,f);}
    private static void emit(TraceSink t,String p,long a,long b,long c,long d,long e,long f,long g){if(t.enabled())t.record(p,a,b,c,d,e,f,g);}
    private record PhaseSpec(int id,int arity) { }
    private static final class Counts { int reads,writes,retained; }
}
