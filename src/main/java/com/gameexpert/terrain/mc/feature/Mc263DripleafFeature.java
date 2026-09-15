package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Exact nested {@code minecraft:dripleaf} configured feature from 26.3-snapshot-7. */
public final class Mc263DripleafFeature {
    public static final String FEATURE = "minecraft:dripleaf";
    public static final int TRACE_MAGIC = 0x44525033; // DRP3
    public static final int TRACE_VERSION = 1;
    public static final String TRACE_SCHEMA = "mc263-dripleaf-trace-v1";
    public static final String CONFIGURED_JSON_SHA256 =
            "d01a7004d7a080c8fab748664627b644d3d263729418d772720202917b7aef82";
    public static final String SIMPLE_RANDOM_SELECTOR_CLASS_SHA256 =
            "68ccaacb1b6202ab376067a0ed8b4c6287ad50ca5d014ce0100d963352650302";
    public static final String SIMPLE_BLOCK_CLASS_SHA256 =
            "10745682a08452d5e5f148cfd627fa1ec84bf669570da5cd284ee2119e810101";
    public static final String BLOCK_COLUMN_CLASS_SHA256 =
            "46d37e8cf9613d6115a28f90b38d17e935a529d00043b967d42ffd8b717bd4c6";

    private static final String[] FACING = {"east", "west", "north", "south"};
    private static final Map<String, Phase> PHASES = Map.of(
            "preflight", new Phase(1, 1),
            "rng_int", new Phase(2, 3),
            "selector", new Phase(3, 2),
            "read", new Phase(4, 6),
            "predicate", new Phase(5, 6),
            "write", new Phase(6, 8),
            "truncate", new Phase(7, 5),
            "result", new Phase(8, 6));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public boolean enabled() { return false; }
        @Override public void record(String phase, long... values) { throw new AssertionError(); }
    };

    private Mc263DripleafFeature() { }

    public record Pos(int x, int y, int z) {
        public Pos above() { return new Pos(x, y + 1, z); }
    }

    public record State(String block, Map<String, String> properties, boolean air,
                        boolean water, boolean replaceable) {
        public State {
            block = key(block);
            properties = Collections.unmodifiableMap(new TreeMap<>(properties));
        }
        public static State of(String block) {
            return new State(block, Map.of(), false, false, false);
        }
        public State property(String name, String value) {
            Map<String, String> next = new TreeMap<>(properties);
            next.put(name, value);
            return new State(block, next, air, water, replaceable);
        }
        public String canonical() {
            if (properties.isEmpty()) return block;
            return block + properties.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .reduce("[", (a, b) -> a.equals("[") ? a + b : a + "," + b) + "]";
        }
    }

    public interface WorldAccess {
        State state(Pos pos);
        boolean canSurvive(State state, Pos pos);
        boolean supportsFeature(String configuredKey);
        boolean supportsState(State state);
        boolean set(Pos pos, State state, int flags);
    }

    public interface TraceSink {
        default boolean enabled() { return true; }
        void record(String phase, long... values);
    }

    public record TraceEvent(String phase, long[] values) {
        public TraceEvent { values = values.clone(); }
        @Override public long[] values() { return values.clone(); }
    }

    public record Result(boolean placed, int selected, int reads, int writes, int retained,
                         int truncated) { }

    public static Result place(WorldgenRandom random, Pos origin, WorldAccess world) {
        return place(random, origin, world, NO_TRACE);
    }

    public static Result place(WorldgenRandom random, Pos origin, WorldAccess world,
                               TraceSink trace) {
        preflight(random, origin, world);
        emit(trace, "preflight", id(FEATURE));
        int selected = nextInt(random, 5, 10, trace);
        emit(trace, "selector", selected, 5);
        Counts counts = selected == 0
                ? placeSmall(random, origin, world, trace)
                : placeBig(selected - 1, random, origin, world, trace);
        emit(trace, "result", counts.placed ? 1 : 0, selected, counts.reads, counts.writes,
                counts.retained, counts.truncated);
        return new Result(counts.placed, selected, counts.reads, counts.writes,
                counts.retained, counts.truncated);
    }

    private static Counts placeSmall(WorldgenRandom random, Pos origin, WorldAccess world,
                                     TraceSink trace) {
        Counts counts = new Counts();
        int facing = nextInt(random, 4, 20, trace);
        State lower = small(FACING[facing], "lower");
        boolean survives = world.canSurvive(lower, origin);
        emit(trace, "predicate", id(lower.canonical()), origin.x, origin.y, origin.z,
                survives ? 1 : 0, 20);
        if (!survives) return counts;
        State above = read(world, origin.above(), trace, counts);
        if (!above.air && (!sameFluid(lower, above) || !above.replaceable)) return counts;
        write(world, origin, lower, 2, trace, counts);
        write(world, origin.above(), small(FACING[facing], "upper"), 2, trace, counts);
        counts.placed = true;
        return counts;
    }

    private static Counts placeBig(int facing, WorldgenRandom random, Pos origin,
                                   WorldAccess world, TraceSink trace) {
        Counts counts = new Counts();
        int distribution = nextInt(random, 3, 30, trace);
        int stems = distribution < 2 ? nextInt(random, 5, 31, trace) : 0;
        int[] layers = {stems, 1};
        int total = stems + 1;
        Pos scan = origin.above();
        for (int i = 0; i < total; i++, scan = scan.above()) {
            State state = read(world, scan, trace, counts);
            if (state.air || state.water) continue;
            int removed = total - i;
            int fromStems = Math.min(layers[0], removed);
            layers[0] -= fromStems;
            layers[1] -= removed - fromStems;
            counts.truncated = removed;
            emit(trace, "truncate", total, i, removed, layers[0], layers[1]);
            break;
        }
        Pos cursor = origin;
        State stem = bigStem(FACING[facing]);
        State tip = bigTip(FACING[facing]);
        for (int i = 0; i < layers[0]; i++, cursor = cursor.above()) {
            write(world, cursor, stem, 2, trace, counts);
        }
        for (int i = 0; i < layers[1]; i++, cursor = cursor.above()) {
            write(world, cursor, tip, 2, trace, counts);
        }
        // BlockColumnFeature returns true for every non-zero sampled total, even after truncation.
        counts.placed = total != 0;
        return counts;
    }

    private static void preflight(WorldgenRandom random, Pos origin, WorldAccess world) {
        if (random == null || origin == null || world == null) {
            throw new IllegalArgumentException("random/origin/world required");
        }
        if (!world.supportsFeature(FEATURE)) throw new UnsupportedOperationException(FEATURE);
        List<State> required = new ArrayList<>();
        for (String facing : FACING) {
            required.add(small(facing, "lower"));
            required.add(small(facing, "upper"));
            required.add(bigStem(facing));
            required.add(bigTip(facing));
        }
        for (State state : required) {
            if (!world.supportsState(state)) throw new UnsupportedOperationException(state.canonical());
        }
    }

    private static State small(String facing, String half) {
        return State.of("minecraft:small_dripleaf").property("facing", facing)
                .property("half", half).property("waterlogged", "false");
    }
    private static State bigStem(String facing) {
        return State.of("minecraft:big_dripleaf_stem").property("facing", facing)
                .property("waterlogged", "false");
    }
    private static State bigTip(String facing) {
        return State.of("minecraft:big_dripleaf").property("facing", facing)
                .property("tilt", "none").property("waterlogged", "false");
    }
    private static boolean sameFluid(State a, State b) { return a.water == b.water; }
    private static State read(WorldAccess world, Pos pos, TraceSink trace, Counts counts) {
        State state = world.state(pos);
        if (state == null) throw new IllegalStateException("missing exact state fact");
        counts.reads++;
        emit(trace, "read", pos.x, pos.y, pos.z, id(state.canonical()), state.air ? 1 : 0,
                state.water ? 1 : 0);
        return state;
    }
    private static void write(WorldAccess world, Pos pos, State state, int flags,
                              TraceSink trace, Counts counts) {
        boolean retained = world.set(pos, state, flags);
        counts.writes++;
        if (retained) counts.retained++;
        emit(trace, "write", pos.x, pos.y, pos.z, id(state.canonical()), flags,
                retained ? 1 : 0, state.properties.hashCode(), id(state.block));
    }
    private static int nextInt(WorldgenRandom random, int bound, int site, TraceSink trace) {
        int value = random.nextInt(bound);
        emit(trace, "rng_int", bound, value, site);
        return value;
    }
    public static byte[] encodeTrace(List<TraceEvent> events) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(TRACE_MAGIC); out.writeShort(TRACE_VERSION);
            byte[] schema = TRACE_SCHEMA.getBytes(StandardCharsets.UTF_8);
            out.writeShort(schema.length); out.write(schema); out.writeInt(events.size());
            for (TraceEvent event : events) {
                Phase phase = PHASES.get(event.phase);
                if (phase == null || event.values.length != phase.arity) {
                    throw new IllegalArgumentException("bad trace event: " + event.phase);
                }
                out.writeByte(phase.id); out.writeByte(phase.arity);
                for (long value : event.values) out.writeLong(value);
            }
            out.flush(); return bytes.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }
    private static void emit(TraceSink trace, String phase, long... values) {
        if (trace != null && trace.enabled()) trace.record(phase, values);
    }
    private static String key(String value) {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("invalid key: " + value);
        }
        return value;
    }
    private static long id(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte one : value.getBytes(StandardCharsets.UTF_8)) {
            hash = (hash ^ (one & 255)) * 0x100000001b3L;
        }
        return hash;
    }
    private record Phase(int id, int arity) { }
    private static final class Counts {
        boolean placed;
        int reads;
        int writes;
        int retained;
        int truncated;
    }
}
