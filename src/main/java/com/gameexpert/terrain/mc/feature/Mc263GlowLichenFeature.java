package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exact, dormant Minecraft Java 26.3-snapshot-7 step-9 glow-lichen leaf. */
public final class Mc263GlowLichenFeature {
    public static final int VEGETAL_DECORATION_STEP = 9;
    public static final int GLOBAL_INDEX = 0;
    public static final int COUNT_MIN = 104;
    public static final int COUNT_MAX = 157;
    public static final int HEIGHT_MIN = -64;
    public static final int HEIGHT_MAX = 256;
    public static final int SEARCH_RANGE = 20;
    public static final int DIRECT_FLAGS = 3;
    public static final int SPREAD_FLAGS = 2;
    public static final String FEATURE = "minecraft:glow_lichen";
    public static final String WATER = "minecraft:water";
    public static final String EMPTY_FLUID = "minecraft:empty";

    public static final String INNER_SERVER_SHA1 =
            "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String CONFIGURED_FEATURE_JSON_SHA256 =
            "f372bec74ef0980adf7fc6c0960f1710faf6a79e008b99b9c6d3f4947abf4481";
    public static final String PLACED_FEATURE_JSON_SHA256 =
            "732a6c9704a3482a5e0455c8f9315799a57dfdc7b6224abcbf44dec5c3c6e970";
    public static final String MULTIFACE_GROWTH_FEATURE_CLASS_SHA256 =
            "965f554f0aae8caa8d6326ac19c547173633fa590efc2234513320ef56c2a47d";
    public static final String MULTIFACE_SPREADER_CLASS_SHA256 =
            "6f79d7f730145e38465be60fb783c828409752b19e352bb1bdefeaa92e0b178b";
    public static final String MULTIFACE_SPREADABLE_BLOCK_CLASS_SHA256 =
            "343c11ceb9f4eb0e1b474868f959963ef76f4c011c7c9e6652b973addd6b0b9a";
    public static final String MULTIFACE_BLOCK_CLASS_SHA256 =
            "3fabdc32385ef4b66f74c8be4723875d9d3ac4295d9be11958725fa6ebb33387";
    public static final String GLOW_LICHEN_BLOCK_CLASS_SHA256 =
            "b76ae92e66ed54f12ced214b535f0da1974736d1fc29a6c5896c1e9c59e74c74";
    public static final String PLACED_FEATURE_CLASS_SHA256 =
            "61908c74cb40f03052036d9d75ef85deb9d669206de1ccd90fcf703f2df98546";
    public static final String COUNT_PLACEMENT_CLASS_SHA256 =
            "e2fa737411edc1547e7e9f29d108298fb87b6b03709a6b56fcfa9df1cd30d987";
    public static final String HEIGHT_RANGE_PLACEMENT_CLASS_SHA256 =
            "de43128c6a87e40dcad9fa34de64069b03e2de8a2ab23a2457d0dbc1d2436cba";
    public static final String IN_SQUARE_PLACEMENT_CLASS_SHA256 =
            "9faa0a4ef725a3d9f65e90ded8317f24b505bdf1c9163ac1a21e506ccdebdcf7";
    public static final String SURFACE_THRESHOLD_CLASS_SHA256 =
            "16dbd1417dc6f5fe28c9ed830e9d8e1dd9ff8c4c79b95455bd185fed7c2fb7d7";
    public static final String BIOME_FILTER_CLASS_SHA256 =
            "9af5f023e371c8541e5a6086e5c01693735e39d10ee918adfea4761fc231e38a";
    public static final String UNIFORM_INT_CLASS_SHA256 =
            "18ce04725246ddebb8d6e4334ca286f7a3685aafa14d03b99caf66fba71e8365";
    public static final String UNIFORM_HEIGHT_CLASS_SHA256 =
            "8f157810ef32e632ddf06276eb4874714079c9d8aec7bac11515321a4272570b";

    public static final int TRACE_MAGIC = 0x474c4933; // GLI3
    public static final int TRACE_VERSION = 1;
    public static final String TRACE_SCHEMA = "mc263-glow-lichen-trace-v1";

    private static final Set<String> SUPPORTS = Set.of(
            "minecraft:stone", "minecraft:andesite", "minecraft:diorite",
            "minecraft:granite", "minecraft:dripstone_block", "minecraft:calcite",
            "minecraft:tuff", "minecraft:deepslate", "minecraft:sulfur",
            "minecraft:cinnabar");
    private static final Direction[] VALID_DIRECTIONS = {
            Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final Direction[] ALL_DIRECTIONS = Direction.values();
    private static final Map<String, PhaseSpec> TRACE_PHASES = Map.ofEntries(
            Map.entry("count", new PhaseSpec(1, 2)),
            Map.entry("candidate", new PhaseSpec(2, 4)),
            Map.entry("threshold", new PhaseSpec(3, 6)),
            Map.entry("biome", new PhaseSpec(4, 3)),
            Map.entry("rng_int", new PhaseSpec(5, 3)),
            Map.entry("read", new PhaseSpec(6, 4)),
            Map.entry("origin", new PhaseSpec(7, 4)),
            Map.entry("shuffle", new PhaseSpec(8, 2)),
            Map.entry("search", new PhaseSpec(9, 6)),
            Map.entry("support", new PhaseSpec(10, 6)),
            Map.entry("attach", new PhaseSpec(11, 5)),
            Map.entry("placement", new PhaseSpec(12, 8)),
            Map.entry("chance", new PhaseSpec(13, 2)),
            Map.entry("spread", new PhaseSpec(14, 8)),
            Map.entry("post", new PhaseSpec(15, 5)),
            Map.entry("write", new PhaseSpec(16, 8)),
            Map.entry("configured_result", new PhaseSpec(17, 5)),
            Map.entry("placed_result", new PhaseSpec(18, 7)));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public void record(String phase, long... values) {
            throw new AssertionError("disabled glow-lichen trace emitted");
        }
        @Override public boolean enabled() { return false; }
    };

    private Mc263GlowLichenFeature() {
    }

    /** Minimal exact live FEATURES-region surface required by the later shared adapter. */
    public interface WorldAccess {
        String biomeKey(int blockX, int blockY, int blockZ);
        int oceanFloorWg(int blockX, int blockZ);
        State blockState(int blockX, int blockY, int blockZ);
        boolean canAttachTo(int blockX, int blockY, int blockZ, Direction face);
        boolean supportsState(State state);
        boolean trySetBlockState(int blockX, int blockY, int blockZ, State state, int flags);
        boolean tryMarkPosForPostProcessing(int blockX, int blockY, int blockZ);
    }

    /** Typed block/fluid facts used by the configured feature and default multiface spreader. */
    public record State(String block, int faces, String fluid, boolean fluidSource, boolean air) {
        public State {
            block = requireKey(block, "block");
            fluid = requireKey(fluid, "fluid");
            if (faces < 0 || faces > 63) throw new IllegalArgumentException("faces must be 0..63");
            if (!block.equals(FEATURE) && faces != 0) {
                throw new IllegalArgumentException("only glow lichen carries faces");
            }
            if (block.equals(FEATURE) && !fluid.equals(EMPTY_FLUID)
                    && !(fluid.equals(WATER) && fluidSource)) {
                throw new IllegalArgumentException("waterlogged glow lichen needs source water");
            }
        }

        public static State air(String airBlock) {
            return new State(airBlock, 0, EMPTY_FLUID, false, true);
        }

        public static State block(String block) {
            return new State(block, 0, EMPTY_FLUID, false, false);
        }

        /** Exact water-block LEVEL domain: level zero is the only source. */
        public static State water(int level) {
            if (level < 0 || level > 15) throw new IllegalArgumentException("water level 0..15");
            return new State(WATER, 0, WATER, level == 0, false);
        }

        public static State lichen(int faces, boolean waterlogged) {
            return new State(FEATURE, faces, waterlogged ? WATER : EMPTY_FLUID,
                    waterlogged, false);
        }

        public boolean hasFace(Direction face) { return (faces & face.bit()) != 0; }
        public boolean waterlogged() { return block.equals(FEATURE) && fluidSource; }

        public String canonical() {
            if (!block.equals(FEATURE)) return block.equals(WATER) && !fluidSource
                    ? WATER + "[level=1]" : block;
            return FEATURE + "[down=" + hasFace(Direction.DOWN)
                    + ",east=" + hasFace(Direction.EAST)
                    + ",north=" + hasFace(Direction.NORTH)
                    + ",south=" + hasFace(Direction.SOUTH)
                    + ",up=" + hasFace(Direction.UP)
                    + ",waterlogged=" + waterlogged()
                    + ",west=" + hasFace(Direction.WEST) + "]";
        }
    }

    public enum Direction {
        DOWN(0, -1, 0, 1), UP(0, 1, 0, 0), NORTH(0, 0, -1, 3),
        SOUTH(0, 0, 1, 2), WEST(-1, 0, 0, 5), EAST(1, 0, 0, 4);

        private final int dx;
        private final int dy;
        private final int dz;
        private final int opposite;

        Direction(int dx, int dy, int dz, int opposite) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.opposite = opposite;
        }

        public Direction opposite() { return ALL_DIRECTIONS[opposite]; }
        public int bit() { return 1 << ordinal(); }
        private int axis() { return dy != 0 ? 1 : dx != 0 ? 0 : 2; }
    }

    public record BlockPos(int x, int y, int z) {
        public BlockPos relative(Direction direction) {
            return new BlockPos(x + direction.dx, y + direction.dy, z + direction.dz);
        }
    }

    public record PlacementResult(long decorationSeed, long featureSeed, int globalIndex,
                                  PlacementCounts counts) { }
    public record PlacementCounts(int candidates, int configuredSuccesses,
                                  int attemptedWrites, int retainedWrites,
                                  int attemptedMarks, int retainedMarks) { }
    public record ConfiguredResult(boolean placed, int attemptedWrites, int retainedWrites,
                                   int attemptedMarks, int retainedMarks) { }

    public record TraceEvent(String phase, long[] values) {
        public TraceEvent {
            PhaseSpec spec = TRACE_PHASES.get(phase);
            if (spec == null || values == null || values.length != spec.arity) {
                throw new IllegalArgumentException("invalid glow-lichen trace event: " + phase);
            }
            values = values.clone();
        }
        @Override public long[] values() { return values.clone(); }
    }

    @FunctionalInterface
    public interface TraceSink {
        void record(String phase, long... values);
        default boolean enabled() { return true; }
        static TraceSink disabled() { return NO_TRACE; }
    }

    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world) {
        Mc263DecorationRandom.FeatureRandom seeded = Mc263DecorationRandom.forFeature(worldSeed,
                sourceBlockX, sourceBlockZ, VEGETAL_DECORATION_STEP, FEATURE);
        PlacementCounts counts = placeWithFeatureRandom(sourceBlockX, sourceBlockZ, world,
                seeded.random(), NO_TRACE);
        return new PlacementResult(seeded.decorationSeed(), seeded.featureSeed(),
                seeded.globalIndex(), counts);
    }

    /** Exact modifier chain: count, height, in-square X/Z, threshold, biome. */
    public static PlacementCounts placeWithFeatureRandom(int sourceBlockX, int sourceBlockZ,
            WorldAccess world, WorldgenRandom random, TraceSink trace) {
        requireInputs(world, random, trace);
        if (Mc263DecorationRandom.globalIndex(VEGETAL_DECORATION_STEP, FEATURE) != GLOBAL_INDEX
                || Mc263FeatureIndexReceipt.biomes().size() != 56) {
            throw new IllegalStateException("pinned step-9 glow-lichen receipt changed");
        }
        int count = drawInt(random, COUNT_MAX - COUNT_MIN + 1, 100, trace) + COUNT_MIN;
        traceRecord(trace, "count", COUNT_MAX, count);
        Counts totals = new Counts();
        totals.candidates = count;
        for (int attempt = 0; attempt < count; attempt++) {
            int y = drawInt(random, HEIGHT_MAX - HEIGHT_MIN + 1, 101, trace) + HEIGHT_MIN;
            int x = drawInt(random, 16, 102, trace) + sourceBlockX;
            int z = drawInt(random, 16, 103, trace) + sourceBlockZ;
            traceRecord(trace, "candidate", attempt, x, y, z);
            int oceanFloor = world.oceanFloorWg(x, z);
            boolean threshold = y <= oceanFloor - 13;
            traceRecord(trace, "threshold", attempt, x, y, z, oceanFloor, threshold ? 1 : 0);
            if (!threshold) continue;
            String biome = requireKey(world.biomeKey(x, y, z), "biome");
            boolean accepted = biomeContains(biome);
            traceRecord(trace, "biome", attempt, biomeId(biome), accepted ? 1 : 0);
            if (!accepted) continue;
            ConfiguredResult configured = placeConfigured(random, new BlockPos(x, y, z),
                    world, trace);
            if (configured.placed) totals.successes++;
            totals.attemptedWrites += configured.attemptedWrites;
            totals.retainedWrites += configured.retainedWrites;
            totals.attemptedMarks += configured.attemptedMarks;
            totals.retainedMarks += configured.retainedMarks;
        }
        traceRecord(trace, "placed_result", GLOBAL_INDEX, totals.candidates, totals.successes,
                totals.attemptedWrites, totals.retainedWrites, totals.attemptedMarks,
                totals.retainedMarks);
        return totals.freeze();
    }

    /** Pure exact output-state closure check for the placed feature. */
    public static void preflight(WorldAccess world) {
        if (world == null) throw new IllegalArgumentException("world is required");
        for (int faces = 1; faces <= 63; faces++) {
            for (boolean waterlogged : new boolean[]{false, true}) {
                State state = State.lichen(faces, waterlogged);
                if (!world.supportsState(state)) {
                    throw new UnsupportedOperationException(
                            "unregistered exact glow-lichen state: " + state.canonical());
                }
            }
        }
    }

    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world) {
        return placeConfigured(random, origin, world, NO_TRACE);
    }

    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world, TraceSink trace) {
        requireInputs(world, random, trace);
        if (origin == null) throw new IllegalArgumentException("origin is required");
        State initial = read(world, origin, trace);
        traceRecord(trace, "origin", origin.x, origin.y, origin.z, stateId(initial));
        if (!initial.air && !initial.block.equals(WATER)) {
            return configured(false, new Counts(), trace);
        }
        List<Direction> searchDirections = shuffled(VALID_DIRECTIONS, random, trace, 200);
        Counts counts = new Counts();
        if (placeGrowth(world, origin, read(world, origin, trace), random, searchDirections,
                counts, trace)) return configured(true, counts, trace);

        for (Direction searchDirection : searchDirections) {
            List<Direction> faces = shuffledExcept(searchDirection.opposite(), random, trace, 201);
            for (int iteration = 0; iteration < SEARCH_RANGE; iteration++) {
                // Exact pinned behavior: reset from origin every iteration, never advance farther.
                BlockPos cursor = origin.relative(searchDirection);
                State state = read(world, cursor, trace);
                traceRecord(trace, "search", searchDirection.ordinal(), iteration, cursor.x,
                        cursor.y, cursor.z, stateId(state));
                if (!state.air && !state.block.equals(WATER) && !state.block.equals(FEATURE)) break;
                if (placeGrowth(world, cursor, state, random, faces, counts, trace)) {
                    return configured(true, counts, trace);
                }
            }
        }
        return configured(false, counts, trace);
    }

    private static boolean placeGrowth(WorldAccess world, BlockPos pos, State old,
            WorldgenRandom random, List<Direction> faces, Counts counts, TraceSink trace) {
        for (Direction face : faces) {
            BlockPos supportPos = pos.relative(face);
            State support = read(world, supportPos, trace);
            boolean configuredSupport = SUPPORTS.contains(support.block);
            traceRecord(trace, "support", pos.x, pos.y, pos.z, face.ordinal(),
                    stateId(support), configuredSupport ? 1 : 0);
            if (!configuredSupport) continue;
            State placed = stateForPlacement(old, world, pos, face, trace);
            // MultifaceGrowthFeature returns immediately when a configured support yields null.
            if (placed == null) return false;
            traceRecord(trace, "placement", pos.x, pos.y, pos.z, face.ordinal(), old.faces,
                    old.fluidSource ? 1 : 0, placed.faces, placed.waterlogged() ? 1 : 0);
            write(world, pos, placed, DIRECT_FLAGS, counts, trace, 0);
            mark(world, pos, counts, trace, 0);
            float sample = random.nextFloat();
            boolean spread = sample < 0.5F;
            traceRecord(trace, "chance", Float.floatToRawIntBits(sample), spread ? 1 : 0);
            if (spread) spreadRandom(placed, world, pos, face, random, counts, trace);
            return true;
        }
        return false;
    }

    static State configuredSupportPlacementForTest(State old, BlockPos pos, Direction face,
            WorldAccess world) {
        State support = world.blockState(pos.relative(face).x, pos.relative(face).y,
                pos.relative(face).z);
        if (!SUPPORTS.contains(support.block)) return null;
        return stateForPlacement(old, world, pos, face, NO_TRACE);
    }

    private static void spreadRandom(State source, WorldAccess world, BlockPos sourcePos,
            Direction fromFace, WorldgenRandom random, Counts counts, TraceSink trace) {
        List<Direction> directions = shuffled(ALL_DIRECTIONS, random, trace, 202);
        for (Direction direction : directions) {
            SpreadPos candidate = spreadPosition(source, world, sourcePos, fromFace, direction,
                    trace);
            if (candidate == null) continue;
            State old = read(world, candidate.pos, trace);
            State placed = stateForPlacement(old, world, candidate.pos, candidate.face, trace);
            if (placed == null) continue;
            mark(world, candidate.pos, counts, trace, 1);
            if (write(world, candidate.pos, placed, SPREAD_FLAGS, counts, trace, 1)) return;
        }
    }

    private static SpreadPos spreadPosition(State source, WorldAccess world, BlockPos sourcePos,
            Direction fromFace, Direction direction, TraceSink trace) {
        if (direction.axis() == fromFace.axis()
                || !source.hasFace(fromFace) || source.hasFace(direction)) {
            traceRecord(trace, "spread", fromFace.ordinal(), direction.ordinal(), -1,
                    sourcePos.x, sourcePos.y, sourcePos.z, -1, 0);
            return null;
        }
        for (int type = 0; type < 3; type++) {
            SpreadPos candidate = switch (type) {
                case 0 -> new SpreadPos(sourcePos, direction);
                case 1 -> new SpreadPos(sourcePos.relative(direction), fromFace);
                case 2 -> new SpreadPos(sourcePos.relative(direction).relative(fromFace),
                        direction.opposite());
                default -> throw new AssertionError(type);
            };
            boolean accepted = canSpreadInto(world, candidate, trace);
            traceRecord(trace, "spread", fromFace.ordinal(), direction.ordinal(), type,
                    candidate.pos.x, candidate.pos.y, candidate.pos.z,
                    candidate.face.ordinal(), accepted ? 1 : 0);
            if (accepted) return candidate;
        }
        return null;
    }

    private static boolean canSpreadInto(WorldAccess world, SpreadPos spread, TraceSink trace) {
        State existing = read(world, spread.pos, trace);
        boolean replaceable = existing.air || existing.block.equals(FEATURE)
                || existing.block.equals(WATER) && existing.fluidSource;
        return replaceable && validPlacement(existing, world, spread.pos, spread.face, trace);
    }

    private static State stateForPlacement(State old, WorldAccess world, BlockPos pos,
            Direction face, TraceSink trace) {
        if (!validPlacement(old, world, pos, face, trace)) return null;
        State base = old.block.equals(FEATURE) ? old
                : State.lichen(face.bit(), old.block.equals(WATER) && old.fluidSource);
        return State.lichen(base.faces | face.bit(), base.waterlogged());
    }

    private static boolean validPlacement(State old, WorldAccess world, BlockPos pos,
            Direction face, TraceSink trace) {
        if (old.block.equals(FEATURE) && old.hasFace(face)) return false;
        boolean attach = world.canAttachTo(pos.x, pos.y, pos.z, face);
        traceRecord(trace, "attach", pos.x, pos.y, pos.z, face.ordinal(), attach ? 1 : 0);
        return attach;
    }

    private static boolean write(WorldAccess world, BlockPos pos, State state, int flags,
            Counts counts, TraceSink trace, int kind) {
        counts.attemptedWrites++;
        boolean retained = world.trySetBlockState(pos.x, pos.y, pos.z, state, flags);
        if (retained) counts.retainedWrites++;
        traceRecord(trace, "write", kind, pos.x, pos.y, pos.z, flags, state.faces,
                state.waterlogged() ? 1 : 0, retained ? 1 : 0);
        return retained;
    }

    private static void mark(WorldAccess world, BlockPos pos, Counts counts, TraceSink trace,
            int kind) {
        counts.attemptedMarks++;
        boolean retained = world.tryMarkPosForPostProcessing(pos.x, pos.y, pos.z);
        if (retained) counts.retainedMarks++;
        traceRecord(trace, "post", kind, pos.x, pos.y, pos.z, retained ? 1 : 0);
    }

    private static ConfiguredResult configured(boolean placed, Counts counts, TraceSink trace) {
        traceRecord(trace, "configured_result", placed ? 1 : 0, counts.attemptedWrites,
                counts.retainedWrites, counts.attemptedMarks, counts.retainedMarks);
        return new ConfiguredResult(placed, counts.attemptedWrites, counts.retainedWrites,
                counts.attemptedMarks, counts.retainedMarks);
    }

    private static State read(WorldAccess world, BlockPos pos, TraceSink trace) {
        State state = world.blockState(pos.x, pos.y, pos.z);
        if (state == null) throw new IllegalStateException("null block state at " + pos);
        traceRecord(trace, "read", pos.x, pos.y, pos.z, stateId(state));
        return state;
    }

    private static List<Direction> shuffled(Direction[] source, WorldgenRandom random,
            TraceSink trace, int kind) {
        List<Direction> result = new ArrayList<>(List.of(source));
        shuffle(result, random);
        traceRecord(trace, "shuffle", kind, pack(result));
        return result;
    }

    private static List<Direction> shuffledExcept(Direction excluded, WorldgenRandom random,
            TraceSink trace, int kind) {
        List<Direction> result = new ArrayList<>(4);
        for (Direction direction : VALID_DIRECTIONS) if (direction != excluded) result.add(direction);
        shuffle(result, random);
        traceRecord(trace, "shuffle", kind, pack(result));
        return result;
    }

    private static void shuffle(List<Direction> directions, WorldgenRandom random) {
        for (int i = directions.size(); i > 1; i--) {
            int target = random.nextInt(i);
            Direction old = directions.set(i - 1, directions.get(target));
            directions.set(target, old);
        }
    }

    private static long pack(List<Direction> directions) {
        long packed = 0;
        for (int i = 0; i < directions.size(); i++) {
            packed |= (long) directions.get(i).ordinal() << i * 3;
        }
        return packed;
    }

    private static int drawInt(WorldgenRandom random, int bound, int kind, TraceSink trace) {
        int value = random.nextInt(bound);
        traceRecord(trace, "rng_int", kind, bound, value);
        return value;
    }

    private static boolean biomeContains(String biome) {
        return Mc263FeatureIndexReceipt.biome(biome).featuresAtStep(VEGETAL_DECORATION_STEP)
                .stream().anyMatch(ref -> ref.globalIndex() == GLOBAL_INDEX
                        && ref.featureKey().equals(FEATURE));
    }

    private static int biomeId(String biome) {
        int id = 0;
        for (Mc263FeatureIndexReceipt.BiomeFeatureData candidate
                : Mc263FeatureIndexReceipt.biomes()) {
            if (candidate.biomeKey().equals(biome)) return id;
            id++;
        }
        throw new IllegalArgumentException("unknown pinned biome: " + biome);
    }

    private static int stateId(State state) {
        int hash = state.block.hashCode();
        hash = 31 * hash + state.faces;
        hash = 31 * hash + state.fluid.hashCode();
        hash = 31 * hash + Boolean.hashCode(state.fluidSource);
        return 31 * hash + Boolean.hashCode(state.air);
    }

    public static byte[] encodeTraceFixture(List<TraceEvent> events) {
        if (events == null) throw new IllegalArgumentException("events are required");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            byte[] schema = TRACE_SCHEMA.getBytes(StandardCharsets.UTF_8);
            out.writeInt(TRACE_MAGIC);
            out.writeShort(TRACE_VERSION);
            out.writeShort(schema.length);
            out.write(schema);
            out.writeInt(events.size());
            for (TraceEvent event : events) {
                PhaseSpec spec = TRACE_PHASES.get(event.phase);
                out.writeByte(spec.id);
                out.writeByte(spec.arity);
                for (long value : event.values) out.writeLong(value);
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void requireInputs(WorldAccess world, WorldgenRandom random, TraceSink trace) {
        if (world == null || random == null || trace == null) {
            throw new IllegalArgumentException("world, random, and trace are required");
        }
    }

    private static String requireKey(String key, String kind) {
        if (key == null || !key.startsWith("minecraft:")
                || key.length() == "minecraft:".length()) {
            throw new IllegalArgumentException(kind + " must be an exact minecraft key");
        }
        return key;
    }

    private static void traceRecord(TraceSink trace, String phase, long a, long b) {
        if (trace.enabled()) trace.record(phase, new long[]{a, b});
    }
    private static void traceRecord(TraceSink trace, String phase, long a, long b, long c) {
        if (trace.enabled()) trace.record(phase, new long[]{a, b, c});
    }
    private static void traceRecord(TraceSink trace, String phase,
            long a, long b, long c, long d) {
        if (trace.enabled()) trace.record(phase, new long[]{a, b, c, d});
    }
    private static void traceRecord(TraceSink trace, String phase,
            long a, long b, long c, long d, long e) {
        if (trace.enabled()) trace.record(phase, new long[]{a, b, c, d, e});
    }
    private static void traceRecord(TraceSink trace, String phase,
            long a, long b, long c, long d, long e, long f) {
        if (trace.enabled()) trace.record(phase, new long[]{a, b, c, d, e, f});
    }
    private static void traceRecord(TraceSink trace, String phase,
            long a, long b, long c, long d, long e, long f, long g) {
        if (trace.enabled()) trace.record(phase, new long[]{a, b, c, d, e, f, g});
    }
    private static void traceRecord(TraceSink trace, String phase,
            long a, long b, long c, long d, long e, long f, long g, long h) {
        if (trace.enabled()) trace.record(phase, new long[]{a, b, c, d, e, f, g, h});
    }

    private record SpreadPos(BlockPos pos, Direction face) { }
    private record PhaseSpec(int id, int arity) { }
    private static final class Counts {
        int candidates;
        int successes;
        int attemptedWrites;
        int retainedWrites;
        int attemptedMarks;
        int retainedMarks;
        PlacementCounts freeze() {
            return new PlacementCounts(candidates, successes, attemptedWrites, retainedWrites,
                    attemptedMarks, retainedMarks);
        }
    }
}
