package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Exact nested {@code minecraft:azalea_tree} configured feature from 26.3-snapshot-7. */
public final class Mc263AzaleaTreeFeature {
    public static final String FEATURE = "minecraft:azalea_tree";
    public static final int TRACE_MAGIC = 0x415a5433; // AZT3
    public static final int TRACE_VERSION = 1;
    public static final String TRACE_SCHEMA = "mc263-azalea-tree-trace-v1";
    public static final String CONFIGURED_JSON_SHA256 =
            "0754ff667607c190233c47d35b2aec9d1b5fd416f69e73fd277d4f2c30bbc23f";
    public static final String TREE_CLASS_SHA256 =
            "bbba55eac5b4991411cf3a59388499f928e70497a750e437c3e09acdcae0c1ce";
    public static final String BENDING_TRUNK_CLASS_SHA256 =
            "e6201f02334f65c530cb3cccbf31b7d4a5c4536497012e2578b76a2e8fe84148";
    public static final String RANDOM_SPREAD_FOLIAGE_CLASS_SHA256 =
            "f0b1ecc34d128b8adfd7a3bd25cc4a2ef218820df20f410f68d82f5125c4a492";

    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final State ROOTED_DIRT = State.of("minecraft:rooted_dirt");
    private static final State OAK_LOG = State.of("minecraft:oak_log").property("axis", "y");
    private static final Map<String, Phase> PHASES = Map.ofEntries(
            Map.entry("preflight", new Phase(1, 1)),
            Map.entry("rng_int", new Phase(2, 3)),
            Map.entry("height", new Phase(3, 5)),
            Map.entry("read", new Phase(4, 8)),
            Map.entry("fluid", new Phase(5, 4)),
            Map.entry("write", new Phase(6, 8)),
            Map.entry("direction", new Phase(7, 2)),
            Map.entry("attachment", new Phase(8, 4)),
            Map.entry("foliage", new Phase(9, 7)),
            Map.entry("finish", new Phase(10, 6)),
            Map.entry("result", new Phase(11, 6)));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public boolean enabled() { return false; }
        @Override public void record(String phase, long... values) { throw new AssertionError(); }
    };

    private Mc263AzaleaTreeFeature() { }

    public enum Direction {
        NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);
        final int dx;
        final int dz;
        Direction(int dx, int dz) { this.dx = dx; this.dz = dz; }
    }

    public record Pos(int x, int y, int z) {
        public Pos offset(int dx, int dy, int dz) { return new Pos(x + dx, y + dy, z + dz); }
    }

    /** Predicate facts are official tag/property queries, never block-name approximations. */
    public record State(String block, Map<String, String> properties, boolean air,
                        boolean replaceableByTrees, boolean log, boolean persistent,
                        boolean waterSource) {
        public State {
            block = key(block);
            properties = Collections.unmodifiableMap(new TreeMap<>(properties));
        }
        public static State of(String block) {
            return new State(block, Map.of(), false, false, false, false, false);
        }
        public static State airState() {
            return new State("minecraft:air", Map.of(), true, true, false, false, false);
        }
        public State property(String name, String value) {
            Map<String, String> next = new TreeMap<>(properties);
            next.put(name, value);
            return new State(block, next, air, replaceableByTrees, log, persistent, waterSource);
        }
        public String canonical() {
            if (properties.isEmpty()) return block;
            return block + properties.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .reduce("[", (a, b) -> a.equals("[") ? a + b : a + "," + b) + "]";
        }
    }

    public interface WorldAccess {
        int minY();
        int maxY();
        State state(Pos pos);
        boolean waterSource(Pos pos);
        boolean supportsFeature(String configuredKey);
        boolean supportsState(State state);
        boolean supportsTreeFinalization();
        boolean set(Pos pos, State state, int flags);
        void finishTree(Set<Pos> logs, Set<Pos> leaves, Set<Pos> roots,
                        Set<Pos> decorations);
    }

    public interface TraceSink {
        default boolean enabled() { return true; }
        void record(String phase, long... values);
    }

    public record TraceEvent(String phase, long[] values) {
        public TraceEvent { values = values.clone(); }
        @Override public long[] values() { return values.clone(); }
    }

    public record Result(boolean placed, int reads, int writes, int retained,
                         int logPositions, int leafPositions) { }

    public static Result place(WorldgenRandom random, Pos origin, WorldAccess world) {
        return place(random, origin, world, NO_TRACE);
    }

    public static Result place(WorldgenRandom random, Pos origin, WorldAccess world,
                               TraceSink trace) {
        preflight(random, origin, world);
        emit(trace, "preflight", id(FEATURE));
        Counts counts = new Counts();
        int height = 4 + nextInt(random, 3, 10, trace) + nextInt(random, 1, 11, trace);
        int foliageHeight = 2;
        int radius = 3;
        emit(trace, "height", height, foliageHeight, radius, origin.y, 0);
        int minY = origin.y;
        int maxY = origin.y + height + 1;
        if (minY < world.minY() + 1 || maxY > world.maxY() + 1) {
            return result(false, counts, trace);
        }
        int clipped = maxFreeHeight(world, origin, height, trace, counts);
        if (clipped < height) return result(false, counts, trace);

        Set<Pos> logs = new LinkedHashSet<>();
        Set<Pos> leaves = new LinkedHashSet<>();
        // SimpleStateProvider.getOptionalState is non-random and the setter records before setBlock.
        write(world, origin.offset(0, -1, 0), ROOTED_DIRT, 19, trace, counts, logs);

        Direction direction = HORIZONTAL[nextInt(random, 4, 20, trace)];
        emit(trace, "direction", direction.ordinal(), 4);
        int logHeight = clipped - 1;
        Pos cursor = origin;
        List<Pos> attachments = new java.util.ArrayList<>();
        for (int i = 0; i <= logHeight; i++) {
            if (i + 1 >= logHeight + nextInt(random, 2, 21, trace)) {
                cursor = cursor.offset(direction.dx, 0, direction.dz);
            }
            if (validTreePos(read(world, cursor, trace, counts))) {
                if (validTreePos(read(world, cursor, trace, counts))) {
                    write(world, cursor, OAK_LOG, 19, trace, counts, logs);
                }
            }
            if (i >= 3) {
                attachments.add(cursor);
                emit(trace, "attachment", cursor.x, cursor.y, cursor.z, 0);
            }
            cursor = cursor.offset(0, 1, 0);
        }
        int bendLength = 1 + nextInt(random, 2, 22, trace);
        for (int i = 0; i <= bendLength; i++) {
            if (validTreePos(read(world, cursor, trace, counts))) {
                if (validTreePos(read(world, cursor, trace, counts))) {
                    write(world, cursor, OAK_LOG, 19, trace, counts, logs);
                }
            }
            attachments.add(cursor);
            emit(trace, "attachment", cursor.x, cursor.y, cursor.z, 0);
            cursor = cursor.offset(direction.dx, 0, direction.dz);
        }

        for (Pos attachment : attachments) {
            for (int attempt = 0; attempt < 50; attempt++) {
                int dx = nextInt(random, radius, 30, trace) - nextInt(random, radius, 31, trace);
                int dy = nextInt(random, foliageHeight, 32, trace)
                        - nextInt(random, foliageHeight, 33, trace);
                int dz = nextInt(random, radius, 34, trace) - nextInt(random, radius, 35, trace);
                Pos pos = attachment.offset(dx, dy, dz);
                State persistentCheck = read(world, pos, trace, counts);
                if (persistentCheck.persistent) {
                    emit(trace, "foliage", pos.x, pos.y, pos.z, attempt, 0, 0, 0);
                    continue;
                }
                State replaceableCheck = read(world, pos, trace, counts);
                if (!validTreePos(replaceableCheck)) {
                    emit(trace, "foliage", pos.x, pos.y, pos.z, attempt, 0, 0, 1);
                    continue;
                }
                int provider = nextInt(random, 4, 36, trace);
                boolean waterlogged = waterSource(world, pos, trace, counts);
                State leaf = leaf(provider < 3 ? "minecraft:azalea_leaves"
                        : "minecraft:flowering_azalea_leaves", waterlogged);
                write(world, pos, leaf, 19, trace, counts, leaves);
                emit(trace, "foliage", pos.x, pos.y, pos.z, attempt, provider,
                        waterlogged ? 1 : 0, 2);
            }
        }
        boolean placed = !logs.isEmpty() || !leaves.isEmpty();
        if (placed) {
            world.finishTree(Collections.unmodifiableSet(new LinkedHashSet<>(logs)),
                    Collections.unmodifiableSet(new LinkedHashSet<>(leaves)), Set.of(), Set.of());
            emit(trace, "finish", logs.size(), leaves.size(), 0, 0, counts.writes,
                    counts.retained);
        }
        return result(placed, counts, trace, logs.size(), leaves.size());
    }

    private static int maxFreeHeight(WorldAccess world, Pos origin, int height,
                                     TraceSink trace, Counts counts) {
        for (int y = 0; y <= height + 1; y++) {
            int radius = y < 1 ? 0 : 1;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Pos pos = origin.offset(dx, y, dz);
                    State first = read(world, pos, trace, counts);
                    boolean free = validTreePos(first);
                    if (!free) free = read(world, pos, trace, counts).log;
                    if (free && !read(world, pos, trace, counts).block.equals("minecraft:vine")) {
                        continue;
                    }
                    return y - 2;
                }
            }
        }
        return height;
    }

    private static void preflight(WorldgenRandom random, Pos origin, WorldAccess world) {
        if (random == null || origin == null || world == null) {
            throw new IllegalArgumentException("random/origin/world required");
        }
        if (!world.supportsFeature(FEATURE)) throw new UnsupportedOperationException(FEATURE);
        if (!world.supportsTreeFinalization()) {
            throw new UnsupportedOperationException("tree finalization");
        }
        List<State> required = new java.util.ArrayList<>(List.of(ROOTED_DIRT, OAK_LOG));
        for (String block : List.of("minecraft:azalea_leaves",
                "minecraft:flowering_azalea_leaves")) {
            for (int distance = 1; distance <= 7; distance++) {
                required.add(leaf(block, distance, false));
                required.add(leaf(block, distance, true));
            }
        }
        for (State state : required) {
            if (!world.supportsState(state)) throw new UnsupportedOperationException(state.canonical());
        }
    }

    private static boolean validTreePos(State state) {
        return state.air || state.replaceableByTrees;
    }
    private static State leaf(String block, boolean waterlogged) {
        return leaf(block, 7, waterlogged);
    }
    private static State leaf(String block, int distance, boolean waterlogged) {
        return State.of(block).property("distance", Integer.toString(distance))
                .property("persistent", "false")
                .property("waterlogged", Boolean.toString(waterlogged));
    }
    private static State read(WorldAccess world, Pos pos, TraceSink trace, Counts counts) {
        State state = world.state(pos);
        if (state == null) throw new IllegalStateException("missing exact state fact");
        counts.reads++;
        emit(trace, "read", pos.x, pos.y, pos.z, id(state.canonical()), state.air ? 1 : 0,
                state.replaceableByTrees ? 1 : 0, state.log ? 1 : 0,
                state.persistent ? 1 : 0);
        return state;
    }
    private static boolean waterSource(WorldAccess world, Pos pos, TraceSink trace, Counts counts) {
        boolean result = world.waterSource(pos);
        counts.reads++;
        emit(trace, "fluid", pos.x, pos.y, pos.z, result ? 1 : 0);
        return result;
    }
    private static void write(WorldAccess world, Pos pos, State state, int flags,
                              TraceSink trace, Counts counts, Set<Pos> positions) {
        positions.add(pos);
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
    private static Result result(boolean placed, Counts counts, TraceSink trace) {
        return result(placed, counts, trace, 0, 0);
    }
    private static Result result(boolean placed, Counts counts, TraceSink trace,
                                 int logs, int leaves) {
        emit(trace, "result", placed ? 1 : 0, counts.reads, counts.writes, counts.retained,
                logs, leaves);
        return new Result(placed, counts.reads, counts.writes, counts.retained, logs, leaves);
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
    private static final class Counts { int reads; int writes; int retained; }
}
