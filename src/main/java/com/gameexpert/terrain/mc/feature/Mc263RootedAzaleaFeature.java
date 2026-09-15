package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Exact dormant 26.3 step-9 index-34 RootSystem configured/placed feature. */
public final class Mc263RootedAzaleaFeature {
    public static final int STEP = 9;
    public static final int GLOBAL_INDEX = 34;
    public static final String FEATURE = "minecraft:rooted_azalea_tree";
    public static final int TRACE_MAGIC = 0x52415a33; // RAZ3
    public static final int TRACE_VERSION = 1;
    public static final String TRACE_SCHEMA = "mc263-rooted-azalea-trace-v1";
    public static final String ROOT_SYSTEM_CLASS_SHA256 =
            "fdd06a1045afd399395f5aaf8cb8599be4339f9920144cea8760e741ce94fca4";
    public static final String PLACED_JSON_SHA256 =
            "f81ffb1a1c1cefcebd6942f2e647de53399f956452a09c1192077cc98b58f719";

    private static final State ROOTED_DIRT = new State("minecraft:rooted_dirt", false, false,
            false, false, true);
    private static final State HANGING_ROOTS = new State("minecraft:hanging_roots", false, false,
            false, false, false);
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public boolean enabled() { return false; }
        @Override public void record(String phase, long... values) {
            throw new AssertionError("disabled rooted-azalea trace emitted");
        }
    };

    private Mc263RootedAzaleaFeature() { }

    public enum Heightmap { WORLD_SURFACE }

    /** Tree placement remains one exact nested capability and consumes this same random stream. */
    public interface WorldAccess {
        int minGenerationY();
        int height(Heightmap type, int x, int z);
        String biomeKey(int x, int y, int z);
        State blockState(int x, int y, int z);
        boolean allowedTreePosition(int x, int y, int z);
        boolean rootReplaceable(int x, int y, int z);
        boolean canSurviveHangingRoots(int x, int y, int z);
        boolean supportsRootedAzaleaTree();
        boolean supportsState(State state);
        boolean trySetBlockState(int x, int y, int z, State state, int flags);
        boolean placeAzaleaTree(WorldgenRandom random, int x, int y, int z);
    }

    /** Exact state identity plus primitive facts queried directly by RootSystemFeature. */
    public record State(String block, boolean air, boolean solid, boolean water,
                        boolean lava, boolean sturdyDown) {
        public State { key(block); }
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
                         int configuredSuccesses, int trees, int attemptedWrites,
                         int retainedWrites) { }

    public static Result placeWithFeatureRandom(WorldgenRandom random, int sourceX, int sourceY,
                                                int sourceZ, WorldAccess world) {
        return placeWithFeatureRandom(random, sourceX, sourceY, sourceZ, world, NO_TRACE);
    }

    public static Result placeWithFeatureRandom(WorldgenRandom random, int sourceX, int sourceY,
                                                int sourceZ, WorldAccess world, TraceSink trace) {
        if (random == null) throw new IllegalArgumentException("random is required");
        preflight(world);
        int count = 1 + drawInt(random, 2, trace);
        int calls = 0, successes = 0, trees = 0, writes = 0, retained = 0;
        emit(trace, "outer", GLOBAL_INDEX, count);
        for (int attempt = 0; attempt < count; attempt++) {
            int x = sourceX + drawInt(random, 16, trace);
            int z = sourceZ + drawInt(random, 16, trace);
            int y = world.minGenerationY()
                    + drawInt(random, 256 - world.minGenerationY() + 1, trace);
            Integer ceiling = scanUp(world, x, y, z, trace);
            if (ceiling == null) continue;
            y = ceiling - 1;
            boolean biome = biomeContains(world.biomeKey(x, y, z));
            emit(trace, "biome", x, y, z, biome ? 1 : 0);
            if (!biome) continue;
            calls++;
            Counts one = configured(random, world, x, y, z, trace);
            if (one.success) successes++;
            if (one.tree) trees++;
            writes += one.writes; retained += one.retained;
        }
        emit(trace, "result", count, calls, successes, trees, writes, retained);
        return new Result(successes > 0, count, calls, successes, trees, writes, retained);
    }

    public static Result placeConfigured(WorldgenRandom random, int x, int y, int z,
                                         WorldAccess world, TraceSink trace) {
        if (random == null) throw new IllegalArgumentException("random is required");
        preflight(world);
        Counts one = configured(random, world, x, y, z, trace);
        return new Result(one.success, 1, 1, one.success ? 1 : 0, one.tree ? 1 : 0,
                one.writes, one.retained);
    }

    private static Counts configured(WorldgenRandom random, WorldAccess world, int x, int y, int z,
                                     TraceSink trace) {
        if (!read(world, x, y, z, trace).air) return new Counts(false, false, 0, 0);
        int topY = y;
        boolean tree = false;
        for (int rise = 0; rise < 100; rise++) {
            topY++;
            if (world.height(Heightmap.WORLD_SURFACE, x, z) < topY) break;
            if (!world.allowedTreePosition(x, topY, z) || !spaceForTree(world, x, topY, z, trace)) {
                continue;
            }
            State below = read(world, x, topY - 1, z, trace);
            if (below.lava || !below.solid) break;
            tree = world.placeAzaleaTree(random, x, topY, z);
            emit(trace, "tree", x, topY, z, tree ? 1 : 0);
            if (tree) break;
        }
        int writes = 0, retained = 0;
        if (tree) {
            for (int py = y; py < topY - 1; py++) {
                for (int attempt = 0; attempt < 20; attempt++) {
                    int px = x + drawInt(random, 3, trace) - drawInt(random, 3, trace);
                    int pz = z + drawInt(random, 3, trace) - drawInt(random, 3, trace);
                    if (!world.rootReplaceable(px, py, pz)) continue;
                    writes++;
                    if (write(world, px, py, pz, ROOTED_DIRT, trace)) retained++;
                }
            }
            for (int attempt = 0; attempt < 20; attempt++) {
                int px = x + drawInt(random, 3, trace) - drawInt(random, 3, trace);
                int py = y + drawInt(random, 2, trace) - drawInt(random, 2, trace);
                int pz = z + drawInt(random, 3, trace) - drawInt(random, 3, trace);
                if (!read(world, px, py, pz, trace).air
                        || !world.canSurviveHangingRoots(px, py, pz)
                        || !read(world, px, py + 1, pz, trace).sturdyDown) continue;
                writes++;
                if (write(world, px, py, pz, HANGING_ROOTS, trace)) retained++;
            }
        }
        // Vanilla RootSystemFeature returns true whenever the configured origin was air.
        return new Counts(true, tree, writes, retained);
    }

    private static boolean spaceForTree(WorldAccess world, int x, int y, int z, TraceSink trace) {
        for (int above = 1; above <= 3; above++) {
            State state = read(world, x, y + above, z, trace);
            if (state.air) continue;
            if (above + 1 <= 2 && state.water) continue;
            return false;
        }
        return true;
    }

    private static Integer scanUp(WorldAccess world, int x, int y, int z, TraceSink trace) {
        if (!read(world, x, y, z, trace).air) return null;
        int py = y;
        for (int step = 0; step < 12; step++) {
            State state = read(world, x, py, z, trace);
            if (state.solid) return py;
            py++;
            if (py > 319) return null;
            if (!read(world, x, py, z, trace).air) break;
        }
        return read(world, x, py, z, trace).solid ? py : null;
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
        if (!world.supportsRootedAzaleaTree()) throw new IllegalStateException("missing azalea-tree capability");
        if (!world.supportsState(ROOTED_DIRT) || !world.supportsState(HANGING_ROOTS)) {
            throw new IllegalStateException("missing rooted-azalea exact states");
        }
    }
    private static State read(WorldAccess world, int x, int y, int z, TraceSink trace) {
        State state = world.blockState(x, y, z);
        if (state == null) throw new IllegalStateException("missing exact state fact");
        emit(trace, "read", x, y, z, state.block.hashCode(), state.air ? 1 : 0);
        return state;
    }
    private static boolean write(WorldAccess world, int x, int y, int z, State state,
                                 TraceSink trace) {
        boolean retained = world.trySetBlockState(x, y, z, state, 2);
        emit(trace, "write", x, y, z, state.block.hashCode(), retained ? 1 : 0);
        return retained;
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
    private record Counts(boolean success, boolean tree, int writes, int retained) { }
}
