package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263WorldgenRandomSource;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Exact dormant inline default leaf of the pinned bamboo-vegetation selector. */
public final class Mc263BambooJungleGrassFeature {
    public static final String CONFIGURED_KEY = "minecraft:bamboo_jungle_grass";
    public static final int TRACE_MAGIC = 0x424a4733; // BJG3
    public static final int TRACE_VERSION = 1;
    public static final String TRACE_SCHEMA = "mc263-bamboo-jungle-grass-trace-v1";
    public static final String INNER_SERVER_SHA1 =
            "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String BAMBOO_VEGETATION_JSON_SHA256 =
            "6e8a7f2a5f7b99667aa7040316bebed4ca01a9a049af7114f4b1176d9059c9c9";
    public static final String GRASS_JUNGLE_JSON_SHA256 =
            "044b0d25b9b61e5c3fee0c2a4347c35f2538e00e1cbabc69db677baedaab60e1";
    public static final String FEATURE_PLACER_CLASS_SHA256 =
            "1db5dea1e80793d6d5f7e7a30148a7f7f689879eac1c1e15f56fdefd183b9db2";
    public static final String COUNT_PLACEMENT_CLASS_SHA256 =
            "a4c57ebf80bafcdfad951b54652b4fc5ed42275ce8d3c4522fe80e5df43afb18";
    public static final String OFFSET_PLACEMENT_CLASS_SHA256 =
            "5f0d8275962d66faad9786d1549805594ef5ced17bc47ceb14fb3702d930c361";
    public static final String TRAPEZOID_INT_CLASS_SHA256 =
            "423245d8437144cb8abf2b906b3919ee65525d137924ea01265a3540f99ee84c";
    public static final String SIMPLE_BLOCK_CLASS_SHA256 =
            "36c55680285bff4061feee31dd5be23524abc28b090796b150f88aa8ad0f1943";
    public static final String WEIGHTED_STATE_PROVIDER_CLASS_SHA256 =
            "2b28e7c8b9e0955f6af8717b535299cc30bfd6dc7c551aec1ad86cf9f3d2b564";
    public static final String WEIGHTED_LIST_CLASS_SHA256 =
            "1e41cb1e317c6c72e4ae9d9217f336c18e2f3e3fecd5d7bda66a4e48aa8ff9a1";
    public static final String WEIGHTED_LIST_FLAT_CLASS_SHA256 =
            "2b8e96c3807ea1a8058207854671f7427f8d059b56167fca47692cf0834fc1b2";
    public static final String BLOCK_PREDICATE_FILTER_CLASS_SHA256 =
            "35032cd69e2ee1a81dfdbf375beb16c4f1323f473cacac9c49a56f8133c2808b";
    public static final String ALL_OF_PREDICATE_CLASS_SHA256 =
            "e3887c2755561ddce93b667283aced8a20c9ef973153032c84e5a832525c9cdc";
    public static final String NOT_PREDICATE_CLASS_SHA256 =
            "4fb239339582ac24a87a74ec06f5f8957e59eef3769318bf3ef245e782349555";
    public static final String MATCHING_BLOCK_TAG_CLASS_SHA256 =
            "e06afedbcb5d48fc897dbb0f1bbe9e7f6021de6e0543a51d153e24beb2ebf6fb";
    public static final String MATCHING_BLOCKS_CLASS_SHA256 =
            "3acb3b23857e17920a0093ecae168c57d5f91a58037e51a4fa79d5cc6e5afea6";
    public static final String VEGETATION_BLOCK_CLASS_SHA256 =
            "3d18ac85ae68645d7a521653957be93d6caa541a3e7db8b686f4fcd0280d6356";

    private static final State SHORT_GRASS = State.output("minecraft:short_grass");
    private static final State FERN = State.output("minecraft:fern");
    private static final List<State> OUTPUT_STATES = List.of(SHORT_GRASS, FERN);
    private static final Map<String, Phase> PHASES = Map.of(
            "rng_int", new Phase(1, 4), "offset", new Phase(2, 7),
            "read", new Phase(3, 7), "filter", new Phase(4, 6),
            "provider", new Phase(5, 6), "survive", new Phase(6, 6),
            "write", new Phase(7, 7), "result", new Phase(8, 8));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public boolean enabled() { return false; }
        @Override public void record(String phase, long... values) {
            throw new AssertionError("disabled bamboo-jungle-grass trace emitted");
        }
    };

    private Mc263BambooJungleGrassFeature() { }

    /** Minimal live boundary for the exact inline placed/configured closure. */
    public interface WorldAccess {
        State blockState(int x, int y, int z);
        boolean supportsState(State state);
        boolean trySetBlockState(int x, int y, int z, State state, int flags);
    }

    /** Exact state identity plus only the two pinned tag facts consumed by this leaf. */
    public record State(String block, boolean airTag, boolean supportsVegetationTag) {
        public State {
            if (block == null || !block.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                throw new IllegalArgumentException("invalid block key");
            }
        }
        public static State output(String block) { return new State(block, false, false); }
    }

    public interface TraceSink {
        default boolean enabled() { return true; }
        void record(String phase, long... values);
    }
    public record TraceEvent(String phase, long[] values) {
        public TraceEvent { values = values.clone(); }
        @Override public long[] values() { return values.clone(); }
    }
    public record Result(boolean placed, int candidates, int predicatePasses,
                         int configuredCalls, int configuredSuccesses, int reads,
                         int attemptedWrites, int retainedWrites) { }

    /** Capability-only transitive preflight: no feature RNG, block query, write, or trace. */
    public static void preflightConfigured(WorldAccess world) {
        for (State state : OUTPUT_STATES) if (!world.supportsState(state)) {
            throw new UnsupportedOperationException(
                    "unregistered bamboo-jungle-grass state: " + state.block());
        }
    }

    public static Result placeConfigured(Mc263WorldgenRandomSource random, int x, int y, int z,
                                         WorldAccess world) {
        return placeConfigured(random, x, y, z, world, NO_TRACE);
    }

    public static Result placeConfigured(Mc263WorldgenRandomSource random, int x, int y, int z,
                                         WorldAccess world, TraceSink trace) {
        preflightConfigured(world);
        Counts counts = new Counts();
        for (int attempt = 0; attempt < 32; attempt++) {
            counts.candidates++;
            int px = x + triangle(random, 7, 0, attempt, trace);
            int py = y + triangle(random, 3, 1, attempt, trace);
            int pz = z + triangle(random, 7, 2, attempt, trace);
            emit(trace, "offset", attempt, x, y, z, px, py, pz);

            State target = read(world, px, py, pz, attempt, 0, trace, counts);
            if (!target.airTag()) {
                emit(trace, "filter", attempt, px, py, pz, 0, -1);
                continue;
            }
            State filterBelow = read(world, px, py - 1, pz, attempt, 1, trace, counts);
            boolean notPodzol = !filterBelow.block().equals("minecraft:podzol");
            emit(trace, "filter", attempt, px, py, pz, 1, notPodzol ? 1 : 0);
            if (!notPodzol) continue;
            counts.predicatePasses++;
            counts.configuredCalls++;

            int draw = nextInt(random, 4, 6, attempt, trace);
            State selected = draw < 3 ? SHORT_GRASS : FERN;
            emit(trace, "provider", attempt, px, py, pz, draw, stateId(selected));
            State survivalBelow = read(world, px, py - 1, pz, attempt, 2, trace, counts);
            boolean survives = survivalBelow.supportsVegetationTag();
            emit(trace, "survive", attempt, px, py, pz, stateId(selected), survives ? 1 : 0);
            if (!survives) continue;

            counts.configuredSuccesses++;
            boolean retained = world.trySetBlockState(px, py, pz, selected, 2);
            counts.attemptedWrites++;
            if (retained) counts.retainedWrites++;
            emit(trace, "write", attempt, px, py, pz, stateId(selected), 2,
                    retained ? 1 : 0);
        }
        boolean placed = counts.configuredSuccesses > 0;
        emit(trace, "result", placed ? 1 : 0, counts.candidates, counts.predicatePasses,
                counts.configuredCalls, counts.configuredSuccesses, counts.reads,
                counts.attemptedWrites, counts.retainedWrites);
        return new Result(placed, counts.candidates, counts.predicatePasses,
                counts.configuredCalls, counts.configuredSuccesses, counts.reads,
                counts.attemptedWrites, counts.retainedWrites);
    }

    public static byte[] encodeTraceFixture(List<TraceEvent> events) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(TRACE_MAGIC); out.writeShort(TRACE_VERSION);
            byte[] schema = TRACE_SCHEMA.getBytes(StandardCharsets.UTF_8);
            out.writeShort(schema.length); out.write(schema); out.writeInt(events.size());
            for (TraceEvent event : events) {
                Phase spec = PHASES.get(event.phase());
                if (spec == null || spec.arity() != event.values().length) {
                    throw new IllegalArgumentException("bad BJG3 event");
                }
                out.writeByte(spec.id()); out.writeByte(spec.arity());
                for (long value : event.values()) out.writeLong(value);
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static int triangle(Mc263WorldgenRandomSource random, int radius, int axis, int attempt,
                                TraceSink trace) {
        return nextInt(random, radius + 1, axis * 2, attempt, trace)
                - nextInt(random, radius + 1, axis * 2 + 1, attempt, trace);
    }

    private static int nextInt(Mc263WorldgenRandomSource random, int bound, int purpose, int attempt,
                               TraceSink trace) {
        int value = random.nextInt(bound);
        emit(trace, "rng_int", purpose, bound, value, attempt);
        return value;
    }

    private static State read(WorldAccess world, int x, int y, int z, int attempt, int kind,
                              TraceSink trace, Counts counts) {
        State state = world.blockState(x, y, z);
        counts.reads++;
        emit(trace, "read", attempt, kind, x, y, z, stateId(state),
                state.airTag() ? 1 : state.supportsVegetationTag() ? 2 : 0);
        return state;
    }

    private static long stateId(State state) {
        long hash = 0xcbf29ce484222325L;
        for (byte item : state.block().getBytes(StandardCharsets.UTF_8)) {
            hash = (hash ^ (item & 255)) * 0x100000001b3L;
        }
        return hash;
    }

    private static void emit(TraceSink trace, String phase, long... values) {
        if (trace.enabled()) trace.record(phase, values);
    }

    private record Phase(int id, int arity) { }
    private static final class Counts {
        int candidates, predicatePasses, configuredCalls, configuredSuccesses, reads;
        int attemptedWrites, retainedWrites;
    }
}
