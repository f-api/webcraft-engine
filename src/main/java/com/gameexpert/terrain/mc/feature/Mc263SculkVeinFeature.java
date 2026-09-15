package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.util.function.Predicate;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exact, inactive Minecraft Java 26.3-snapshot-7 sculk-vein placed-feature leaf. */
public final class Mc263SculkVeinFeature {
    public static final int UNDERGROUND_DECORATION_STEP = 7;
    public static final int SCULK_VEIN_GLOBAL_INDEX = 0;
    public static final int COUNT_MIN = 204;
    public static final int COUNT_MAX = 250;
    public static final int HEIGHT_MIN = -64;
    public static final int HEIGHT_MAX = 256;
    public static final int SEARCH_RANGE = 20;
    public static final int DIRECT_UPDATE_FLAGS = 3;
    public static final int SPREAD_UPDATE_FLAGS = 2;
    public static final String SCULK_VEIN_FEATURE = "minecraft:sculk_vein";
    public static final String SCULK_VEIN = "minecraft:sculk_vein";
    public static final String WATER = "minecraft:water";

    public static void preflight(Predicate<String> supportsState,
            boolean supportsPostprocessing) {
        for (int faces = 1; faces <= 63; faces++) {
            for (boolean waterlogged : new boolean[]{false, true}) {
                Mc263LakeFeature.requireOutputs(supportsState,
                        State.vein(faces, waterlogged).canonical());
            }
        }
        if (!supportsPostprocessing) {
            throw new UnsupportedOperationException("sculk-vein postprocessing");
        }
    }

    public static final String INNER_SERVER_SHA1 =
            "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String MULTIFACE_GROWTH_FEATURE_CLASS_SHA256 =
            "965f554f0aae8caa8d6326ac19c547173633fa590efc2234513320ef56c2a47d";
    public static final String MULTIFACE_BLOCK_CLASS_SHA256 =
            "3fabdc32385ef4b66f74c8be4723875d9d3ac4295d9be11958725fa6ebb33387";
    public static final String MULTIFACE_SPREADER_CLASS_SHA256 =
            "6f79d7f730145e38465be60fb783c828409752b19e352bb1bdefeaa92e0b178b";
    public static final String DEFAULT_SPREADER_CONFIG_CLASS_SHA256 =
            "2f0c1b0815028e4fa6d13b37f781429c04d58e328b7d5a76bcd0c8e7d12af7d2";
    public static final String SPREAD_CONFIG_CLASS_SHA256 =
            "4debd4096839f8ac0e1cc47da1cae4630a9775ae60657824e181a5fa76ecfe9b";
    public static final String SCULK_VEIN_BLOCK_CLASS_SHA256 =
            "9667dfbde838b8c5818f69a2b99a96a44419ac29668ad4f4186afe7730e6ec52";
    public static final String SCULK_VEIN_SPREADER_CONFIG_CLASS_SHA256 =
            "c18332a94f907a0872b187126864e66ccab0947ac3f1450a7f82cc521fcd6b25";
    public static final String CONFIGURED_FEATURE_JSON_SHA256 =
            "bf62141c9ff6e7a35bf8ae35836a0884431f6b2e9f96148f1fa2deb92f4f79d0";
    public static final String PLACED_FEATURE_JSON_SHA256 =
            "9589f1c59dcfd71280a4ed0745f556d73d907a186d5260e7855869142eb112df";
    public static final String DEEP_DARK_BIOME_JSON_SHA256 =
            "9f9bc413f4f62b291296431675e8a8b26d363d541eec0e909bae8ca86f4907cd";
    public static final String FIRE_BLOCK_TAG_JSON_SHA256 =
            "0074ed2ae89b03a2008a8220636c7544c868326e925b4bfae223891bd5727a97";

    public static final String TRACE_SCHEMA = "mc263-sculk-vein-trace-v1";
    public static final int TRACE_BINARY_MAGIC = 0x53435633; // SCV3
    public static final int TRACE_BINARY_VERSION = 1;

    private static final Set<String> DIRECT_SUPPORTS = Set.of(
            "minecraft:stone", "minecraft:andesite", "minecraft:diorite",
            "minecraft:granite", "minecraft:dripstone_block", "minecraft:calcite",
            "minecraft:tuff", "minecraft:deepslate");
    private static final Set<String> SPREAD_FORBIDDEN_SUPPORTS = Set.of(
            "minecraft:sculk", "minecraft:sculk_catalyst", "minecraft:moving_piston");
    private static final Direction[] VALID_DIRECTIONS = {
            Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST,
            Direction.SOUTH, Direction.WEST
    };
    private static final Direction[] ENUM_DIRECTIONS = Direction.values();
    private static final Map<String, PhaseSpec> TRACE_PHASES = Map.ofEntries(
            Map.entry("count", new PhaseSpec(1, 2)),
            Map.entry("candidate", new PhaseSpec(2, 4)),
            Map.entry("biome", new PhaseSpec(3, 3)),
            Map.entry("origin", new PhaseSpec(4, 4)),
            Map.entry("shuffle", new PhaseSpec(5, 2)),
            Map.entry("search", new PhaseSpec(6, 6)),
            Map.entry("support", new PhaseSpec(7, 6)),
            Map.entry("placement", new PhaseSpec(8, 8)),
            Map.entry("spread", new PhaseSpec(9, 8)),
            Map.entry("post", new PhaseSpec(10, 5)),
            Map.entry("write", new PhaseSpec(11, 8)),
            Map.entry("configured_result", new PhaseSpec(12, 5)),
            Map.entry("placed_result", new PhaseSpec(13, 7)));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public void record(String phase, long... values) {
            throw new AssertionError("disabled sculk-vein trace emitted");
        }
        @Override public boolean enabled() { return false; }
    };

    private Mc263SculkVeinFeature() {
    }

    /** Complete live FEATURES-region view used by the feature and spreader. */
    public interface WorldAccess {
        int minGenerationY();

        int generationDepth();

        String biomeKey(int blockX, int blockY, int blockZ);

        State blockState(int blockX, int blockY, int blockZ);

        /** Exact multiface attachment test for a face pointing from placement toward neighbour. */
        boolean canAttachTo(int blockX, int blockY, int blockZ, Direction face);

        /** Exact sturdy-face query on the supplied block position and face. */
        boolean isFaceSturdy(int blockX, int blockY, int blockZ, Direction face);

        /** Performs the exact bounded state write and reports whether it was retained. */
        boolean trySetBlockState(int blockX, int blockY, int blockZ, State state, int flags);

        /** Performs one ordered chunk post-processing mark and reports retention. */
        boolean tryMarkPosForPostProcessing(int blockX, int blockY, int blockZ);
    }

    /** Typed block/fluid facts needed by the official multiface predicates. */
    public record State(String block, int faces, boolean waterlogged, String fluid,
                        boolean fluidSource, boolean replaceable, boolean fire) {
        public State {
            block = requireMinecraftKey(block, "block");
            fluid = requireMinecraftKey(fluid, "fluid");
            if (faces < 0 || faces > 0x3f) {
                throw new IllegalArgumentException("sculk-vein face mask must be in 0..63");
            }
            if (!block.equals(SCULK_VEIN) && (faces != 0 || waterlogged)) {
                throw new IllegalArgumentException("only sculk vein carries faces/waterlogged");
            }
            if (waterlogged && (!fluid.equals(WATER) || !fluidSource)) {
                throw new IllegalArgumentException("waterlogged vein requires source water");
            }
        }

        public static State block(String block) {
            return new State(block, 0, false, "minecraft:empty", false, false, false);
        }

        public static State air(String block) {
            return new State(block, 0, false, "minecraft:empty", false, true, false);
        }

        public static State water(boolean source) {
            return new State(WATER, 0, false,
                    source ? WATER : "minecraft:flowing_water", source, true, false);
        }

        /** Exact 26.3 water-block LEVEL domain; only level zero is a source. */
        public static State water(int level) {
            if (level < 0 || level > 15) {
                throw new IllegalArgumentException("water level must be in 0..15");
            }
            return water(level == 0);
        }

        public static State vein(int faces, boolean waterlogged) {
            return new State(SCULK_VEIN, faces, waterlogged,
                    waterlogged ? WATER : "minecraft:empty", waterlogged, false, false);
        }

        public boolean hasFace(Direction face) {
            return (faces & face.bit()) != 0;
        }

        public String canonical() {
            if (!block.equals(SCULK_VEIN)) return block;
            return SCULK_VEIN + "[down=" + hasFace(Direction.DOWN)
                    + ",east=" + hasFace(Direction.EAST)
                    + ",north=" + hasFace(Direction.NORTH)
                    + ",south=" + hasFace(Direction.SOUTH)
                    + ",up=" + hasFace(Direction.UP)
                    + ",waterlogged=" + waterlogged
                    + ",west=" + hasFace(Direction.WEST) + "]";
        }
    }

    public enum Direction {
        DOWN(0, -1, 0, 1),
        UP(0, 1, 0, 0),
        NORTH(0, 0, -1, 3),
        SOUTH(0, 0, 1, 2),
        WEST(-1, 0, 0, 5),
        EAST(1, 0, 0, 4);

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

        public Direction opposite() { return ENUM_DIRECTIONS[opposite]; }
        public int bit() { return 1 << ordinal(); }
        private int axis() { return dy != 0 ? 1 : dx != 0 ? 0 : 2; }
    }

    @FunctionalInterface
    public interface TraceSink {
        void record(String phase, long... values);
        default boolean enabled() { return true; }
        static TraceSink disabled() { return NO_TRACE; }
        default void record(String p, long a) {
            if (enabled()) record(p, new long[]{a});
        }
        default void record(String p, long a, long b) {
            if (enabled()) record(p, new long[]{a, b});
        }
        default void record(String p, long a, long b, long c) {
            if (enabled()) record(p, new long[]{a, b, c});
        }
        default void record(String p, long a, long b, long c, long d) {
            if (enabled()) record(p, new long[]{a, b, c, d});
        }
        default void record(String p, long a, long b, long c, long d, long e) {
            if (enabled()) record(p, new long[]{a, b, c, d, e});
        }
        default void record(String p, long a, long b, long c, long d, long e, long f) {
            if (enabled()) record(p, new long[]{a, b, c, d, e, f});
        }
        default void record(String p, long a, long b, long c, long d, long e, long f,
                long g) {
            if (enabled()) record(p, new long[]{a, b, c, d, e, f, g});
        }
        default void record(String p, long a, long b, long c, long d, long e, long f,
                long g, long h) {
            if (enabled()) record(p, new long[]{a, b, c, d, e, f, g, h});
        }
    }

    public record TraceEvent(String phase, long[] values) {
        public TraceEvent {
            PhaseSpec spec = TRACE_PHASES.get(phase);
            if (spec == null || values == null || values.length != spec.arity()) {
                throw new IllegalArgumentException("invalid sculk-vein trace event: " + phase);
            }
            values = values.clone();
        }
        @Override public long[] values() { return values.clone(); }
    }

    public record BlockPos(int x, int y, int z) {
        BlockPos relative(Direction direction) {
            return new BlockPos(x + direction.dx, y + direction.dy, z + direction.dz);
        }
        int manhattan(BlockPos other) {
            return Math.abs(x - other.x) + Math.abs(y - other.y) + Math.abs(z - other.z);
        }
    }

    public record PlacementResult(long decorationSeed, long featureSeed, int globalIndex,
                                  int candidateCount, int configuredSuccessCount,
                                  int attemptedWriteCount, int retainedWriteCount,
                                  int attemptedPostProcessMarkCount,
                                  int retainedPostProcessMarkCount) {
        public PlacementResult {
            validateCounts(candidateCount, configuredSuccessCount, attemptedWriteCount,
                    retainedWriteCount, attemptedPostProcessMarkCount,
                    retainedPostProcessMarkCount);
        }
    }

    public record PlacementCounts(int candidates, int configuredSuccesses,
                                  int attemptedWrites, int retainedWrites,
                                  int attemptedPostProcessMarks,
                                  int retainedPostProcessMarks) {
        public PlacementCounts {
            validateCounts(candidates, configuredSuccesses, attemptedWrites, retainedWrites,
                    attemptedPostProcessMarks, retainedPostProcessMarks);
        }
    }

    public record ConfiguredResult(boolean placed, int attemptedWrites, int retainedWrites,
                                   int attemptedPostProcessMarks,
                                   int retainedPostProcessMarks) {
        public ConfiguredResult {
            validateWriteAndMarkCounts(attemptedWrites, retainedWrites,
                    attemptedPostProcessMarks, retainedPostProcessMarks);
        }
    }

    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world) {
        return place(worldSeed, sourceBlockX, sourceBlockZ, world, NO_TRACE);
    }

    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world, TraceSink trace) {
        requireWorld(world);
        TraceSink events = requireTrace(trace);
        Mc263DecorationRandom.FeatureRandom seeded = Mc263DecorationRandom.forFeature(worldSeed,
                sourceBlockX, sourceBlockZ, UNDERGROUND_DECORATION_STEP, SCULK_VEIN_FEATURE);
        PlacementCounts counts = placeWithFeatureRandom(sourceBlockX, sourceBlockZ, world,
                seeded.random(), events);
        return new PlacementResult(seeded.decorationSeed(), seeded.featureSeed(),
                seeded.globalIndex(), counts.candidates(), counts.configuredSuccesses(),
                counts.attemptedWrites(), counts.retainedWrites(),
                counts.attemptedPostProcessMarks(), counts.retainedPostProcessMarks());
    }

    /** Executes uniform count, X/Z/Y placement, biome, then configured feature on one stream. */
    public static PlacementCounts placeWithFeatureRandom(int sourceBlockX, int sourceBlockZ,
            WorldAccess world, WorldgenRandom random, TraceSink trace) {
        requireWorld(world);
        if (random == null) throw new IllegalArgumentException("random is required");
        TraceSink events = requireTrace(trace);
        int globalIndex = Mc263DecorationRandom.globalIndex(
                UNDERGROUND_DECORATION_STEP, SCULK_VEIN_FEATURE);
        if (globalIndex != SCULK_VEIN_GLOBAL_INDEX
                || !Mc263FeatureIndexReceipt.step(UNDERGROUND_DECORATION_STEP)
                .featureAt(globalIndex).equals(SCULK_VEIN_FEATURE)) {
            throw new IllegalStateException("pinned sculk-vein feature index changed");
        }

        int count = random.nextInt(COUNT_MAX - COUNT_MIN + 1) + COUNT_MIN;
        events.record("count", COUNT_MAX, count);
        MutableCounts totals = new MutableCounts();
        totals.candidates = count;
        for (int attempt = 0; attempt < count; attempt++) {
            int x = sourceBlockX + random.nextInt(16);
            int z = sourceBlockZ + random.nextInt(16);
            int y = random.nextInt(HEIGHT_MAX - HEIGHT_MIN + 1) + HEIGHT_MIN;
            events.record("candidate", attempt, x, y, z);
            String biome = requireMinecraftKey(world.biomeKey(x, y, z), "biome");
            int biomeId = biomeTraceId(biome);
            boolean accepted = biomeId >= 0 && biomeContains(biome);
            events.record("biome", attempt, biomeId, accepted ? 1L : 0L);
            if (!accepted) continue;
            ConfiguredResult configured = placeConfigured(random, new BlockPos(x, y, z), world,
                    events);
            if (configured.placed()) totals.successes++;
            totals.attemptedWrites += configured.attemptedWrites();
            totals.retainedWrites += configured.retainedWrites();
            totals.attemptedMarks += configured.attemptedPostProcessMarks();
            totals.retainedMarks += configured.retainedPostProcessMarks();
        }
        events.record("placed_result", SCULK_VEIN_GLOBAL_INDEX, totals.candidates,
                totals.successes, totals.attemptedWrites, totals.retainedWrites,
                totals.attemptedMarks, totals.retainedMarks);
        return totals.freeze();
    }

    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world) {
        return placeConfigured(random, origin, world, NO_TRACE);
    }

    /** Executes exact {@code MultifaceGrowthFeature} and one sculk-vein spread attempt. */
    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world, TraceSink trace) {
        requireWorld(world);
        if (random == null || origin == null) {
            throw new IllegalArgumentException("random and origin are required");
        }
        TraceSink events = requireTrace(trace);
        State firstOriginState = stateAt(world, origin);
        events.record("origin", origin.x(), origin.y(), origin.z(), stateId(firstOriginState));
        if (!isAir(firstOriginState) && !firstOriginState.block().equals(WATER)) {
            return configuredResult(false, new WriteCounts(), events);
        }

        List<Direction> searchDirections = shuffled(VALID_DIRECTIONS, random);
        events.record("shuffle", 0, packDirections(searchDirections));
        WriteCounts counts = new WriteCounts();
        if (placeGrowthIfPossible(world, origin, stateAt(world, origin), random,
                searchDirections, counts, events)) {
            return configuredResult(true, counts, events);
        }

        for (Direction searchDirection : searchDirections) {
            List<Direction> placementDirections = shuffledExceptOpposite(random,
                    searchDirection.opposite());
            events.record("shuffle", 1, packDirections(placementDirections));
            for (int iteration = 0; iteration < SEARCH_RANGE; iteration++) {
                // Pinned 26.3 resets from origin on every iteration; it never advances farther.
                BlockPos searchPos = origin.relative(searchDirection);
                State state = stateAt(world, searchPos);
                events.record("search", searchDirection.ordinal(), iteration, searchPos.x(),
                        searchPos.y(), searchPos.z(), stateId(state));
                if (!isAir(state) && !state.block().equals(WATER)
                        && !state.block().equals(SCULK_VEIN)) {
                    break;
                }
                if (placeGrowthIfPossible(world, searchPos, state, random,
                        placementDirections, counts, events)) {
                    return configuredResult(true, counts, events);
                }
            }
        }
        return configuredResult(false, counts, events);
    }

    public static byte[] encodeTraceFixture(List<TraceEvent> trace) {
        if (trace == null) throw new IllegalArgumentException("trace is required");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            byte[] schema = TRACE_SCHEMA.getBytes(StandardCharsets.UTF_8);
            output.writeInt(TRACE_BINARY_MAGIC);
            output.writeShort(TRACE_BINARY_VERSION);
            output.writeShort(schema.length);
            output.write(schema);
            output.writeInt(trace.size());
            for (TraceEvent event : trace) {
                if (event == null) throw new IllegalArgumentException("null trace event");
                PhaseSpec spec = TRACE_PHASES.get(event.phase());
                long[] values = event.values();
                output.writeByte(spec.id());
                output.writeByte(values.length);
                for (long value : values) output.writeLong(value);
            }
            output.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static boolean placeGrowthIfPossible(WorldAccess world, BlockPos pos, State oldState,
            WorldgenRandom random, List<Direction> placementDirections, WriteCounts counts,
            TraceSink trace) {
        for (Direction placementDirection : placementDirections) {
            BlockPos supportPos = pos.relative(placementDirection);
            State support = stateAt(world, supportPos);
            boolean supportedBlock = DIRECT_SUPPORTS.contains(support.block());
            trace.record("support", pos.x(), pos.y(), pos.z(), placementDirection.ordinal(),
                    stateId(support), supportedBlock ? 1L : 0L);
            if (!supportedBlock) continue;
            State newState = stateForPlacement(oldState, world, pos, placementDirection);
            if (newState == null) return false;
            trace.record("placement", pos.x(), pos.y(), pos.z(),
                    placementDirection.ordinal(), oldState.faces(), oldState.waterlogged() ? 1L : 0L,
                    newState.faces(), newState.waterlogged() ? 1L : 0L);

            counts.attemptedWrites++;
            boolean retained = world.trySetBlockState(pos.x(), pos.y(), pos.z(), newState,
                    DIRECT_UPDATE_FLAGS);
            if (retained) counts.retainedWrites++;
            trace.record("write", 0, pos.x(), pos.y(), pos.z(), DIRECT_UPDATE_FLAGS,
                    newState.faces(), newState.waterlogged() ? 1L : 0L, retained ? 1L : 0L);

            counts.attemptedMarks++;
            boolean markRetained = world.tryMarkPosForPostProcessing(pos.x(), pos.y(), pos.z());
            if (markRetained) counts.retainedMarks++;
            trace.record("post", 0, pos.x(), pos.y(), pos.z(), markRetained ? 1L : 0L);

            if (random.nextFloat() < 1.0F) {
                spreadFromFaceTowardRandomDirection(newState, world, pos, placementDirection,
                        random, counts, trace);
            }
            return true;
        }
        return false;
    }

    private static boolean spreadFromFaceTowardRandomDirection(State sourceState,
            WorldAccess world, BlockPos sourcePos, Direction fromFace, WorldgenRandom random,
            WriteCounts counts, TraceSink trace) {
        List<Direction> spreadDirections = shuffled(ENUM_DIRECTIONS, random);
        trace.record("shuffle", 2, packDirections(spreadDirections));
        for (Direction spreadDirection : spreadDirections) {
            SpreadPos candidate = getSpreadFromFaceTowardDirection(sourceState, world, sourcePos,
                    fromFace, spreadDirection, trace);
            if (candidate == null) continue;
            State oldState = stateAt(world, candidate.pos());
            State spreadState = stateForPlacement(oldState, world, candidate.pos(),
                    candidate.face());
            if (spreadState == null) continue;

            counts.attemptedMarks++;
            boolean markRetained = world.tryMarkPosForPostProcessing(candidate.pos().x(),
                    candidate.pos().y(), candidate.pos().z());
            if (markRetained) counts.retainedMarks++;
            trace.record("post", 1, candidate.pos().x(), candidate.pos().y(),
                    candidate.pos().z(), markRetained ? 1L : 0L);

            counts.attemptedWrites++;
            boolean retained = world.trySetBlockState(candidate.pos().x(), candidate.pos().y(),
                    candidate.pos().z(), spreadState, SPREAD_UPDATE_FLAGS);
            if (retained) counts.retainedWrites++;
            trace.record("write", 1, candidate.pos().x(), candidate.pos().y(),
                    candidate.pos().z(), SPREAD_UPDATE_FLAGS, spreadState.faces(),
                    spreadState.waterlogged() ? 1L : 0L, retained ? 1L : 0L);
            if (retained) return true;
        }
        return false;
    }

    private static SpreadPos getSpreadFromFaceTowardDirection(State sourceState,
            WorldAccess world, BlockPos sourcePos, Direction fromFace, Direction spreadDirection,
            TraceSink trace) {
        if (spreadDirection.axis() == fromFace.axis()
                || sourceState.block().equals(SCULK_VEIN)
                && (!sourceState.hasFace(fromFace) || sourceState.hasFace(spreadDirection))) {
            trace.record("spread", fromFace.ordinal(), spreadDirection.ordinal(), -1,
                    sourcePos.x(), sourcePos.y(), sourcePos.z(), -1, 0);
            return null;
        }
        for (int type = 0; type < 3; type++) {
            SpreadPos candidate = switch (type) {
                case 0 -> new SpreadPos(sourcePos, spreadDirection);
                case 1 -> new SpreadPos(sourcePos.relative(spreadDirection), fromFace);
                case 2 -> new SpreadPos(sourcePos.relative(spreadDirection).relative(fromFace),
                        spreadDirection.opposite());
                default -> throw new AssertionError(type);
            };
            boolean accepted = canSpreadInto(world, sourcePos, candidate);
            trace.record("spread", fromFace.ordinal(), spreadDirection.ordinal(), type,
                    candidate.pos().x(), candidate.pos().y(), candidate.pos().z(),
                    candidate.face().ordinal(), accepted ? 1L : 0L);
            if (accepted) return candidate;
        }
        return null;
    }

    private static boolean canSpreadInto(WorldAccess world, BlockPos sourcePos,
            SpreadPos spreadPos) {
        State existing = stateAt(world, spreadPos.pos());
        State against = stateAt(world, spreadPos.pos().relative(spreadPos.face()));
        if (SPREAD_FORBIDDEN_SUPPORTS.contains(against.block())) return false;
        if (sourcePos.manhattan(spreadPos.pos()) == 2) {
            BlockPos obstruction = sourcePos.relative(spreadPos.face().opposite());
            if (world.isFaceSturdy(obstruction.x(), obstruction.y(), obstruction.z(),
                    spreadPos.face())) {
                return false;
            }
        }
        if (!existing.fluid().equals("minecraft:empty")
                && !existing.fluid().equals(WATER)
                && !existing.fluid().equals("minecraft:flowing_water")) {
            return false;
        }
        if (existing.fire()) return false;
        boolean replaceable = existing.replaceable() || isAir(existing)
                || existing.block().equals(SCULK_VEIN)
                || existing.block().equals(WATER) && existing.fluidSource();
        return replaceable && stateForPlacement(existing, world, spreadPos.pos(),
                spreadPos.face()) != null;
    }

    private static State stateForPlacement(State oldState, WorldAccess world, BlockPos pos,
            Direction placementDirection) {
        if (oldState.block().equals(SCULK_VEIN) && oldState.hasFace(placementDirection)) {
            return null;
        }
        if (!world.canAttachTo(pos.x(), pos.y(), pos.z(), placementDirection)) return null;
        State base = oldState.block().equals(SCULK_VEIN)
                ? oldState
                : State.vein(0, oldState.fluidSource() && oldState.fluid().equals(WATER));
        return State.vein(base.faces() | placementDirection.bit(), base.waterlogged());
    }

    private static ConfiguredResult configuredResult(boolean placed, WriteCounts counts,
            TraceSink trace) {
        trace.record("configured_result", placed ? 1L : 0L, counts.attemptedWrites,
                counts.retainedWrites, counts.attemptedMarks, counts.retainedMarks);
        return new ConfiguredResult(placed, counts.attemptedWrites, counts.retainedWrites,
                counts.attemptedMarks, counts.retainedMarks);
    }

    private static List<Direction> shuffled(Direction[] source, WorldgenRandom random) {
        List<Direction> result = new ArrayList<>(List.of(source));
        shuffle(result, random);
        return result;
    }

    private static List<Direction> shuffledExceptOpposite(WorldgenRandom random,
            Direction excluded) {
        List<Direction> result = new ArrayList<>(5);
        for (Direction direction : VALID_DIRECTIONS) {
            if (direction != excluded) result.add(direction);
        }
        shuffle(result, random);
        return result;
    }

    private static void shuffle(List<Direction> directions, WorldgenRandom random) {
        for (int i = directions.size(); i > 1; i--) {
            int swapTo = random.nextInt(i);
            Direction previous = directions.set(i - 1, directions.get(swapTo));
            directions.set(swapTo, previous);
        }
    }

    private static long packDirections(List<Direction> directions) {
        long packed = 0;
        for (int i = 0; i < directions.size(); i++) {
            packed |= (long) directions.get(i).ordinal() << (i * 3);
        }
        return packed;
    }

    private static boolean biomeContains(String biomeKey) {
        String biome = requireMinecraftKey(biomeKey, "biome");
        return biome.equals("minecraft:deep_dark")
                && Mc263FeatureIndexReceipt.biome(biome)
                .featuresAtStep(UNDERGROUND_DECORATION_STEP).stream()
                .anyMatch(reference -> reference.featureKey().equals(SCULK_VEIN_FEATURE)
                        && reference.globalIndex() == SCULK_VEIN_GLOBAL_INDEX);
    }

    private static int biomeTraceId(String biomeKey) {
        String biome = requireMinecraftKey(biomeKey, "biome");
        if (biome.equals("minecraft:plains")) return 0;
        int traceId = 1;
        for (Mc263FeatureIndexReceipt.BiomeFeatureData candidate
                : Mc263FeatureIndexReceipt.biomes()) {
            if (candidate.biomeKey().equals("minecraft:plains")) continue;
            if (candidate.biomeKey().equals(biome)) return traceId;
            traceId++;
        }
        return -1;
    }

    private static int stateId(State state) {
        return switch (state.block()) {
            case "minecraft:air" -> 0;
            case "minecraft:cave_air" -> 1;
            case "minecraft:void_air" -> 2;
            case WATER -> state.fluidSource() ? 3 : 4;
            case SCULK_VEIN -> 5;
            case "minecraft:stone" -> 6;
            case "minecraft:andesite" -> 7;
            case "minecraft:diorite" -> 8;
            case "minecraft:granite" -> 9;
            case "minecraft:dripstone_block" -> 10;
            case "minecraft:calcite" -> 11;
            case "minecraft:tuff" -> 12;
            case "minecraft:deepslate" -> 13;
            case "minecraft:sculk" -> 14;
            case "minecraft:sculk_catalyst" -> 15;
            case "minecraft:moving_piston" -> 16;
            case "minecraft:fire", "minecraft:soul_fire" -> 17;
            case "minecraft:lava" -> 18;
            default -> state.replaceable() ? 19 : 20;
        };
    }

    private static boolean isAir(State state) {
        return state.block().equals("minecraft:air")
                || state.block().equals("minecraft:cave_air")
                || state.block().equals("minecraft:void_air");
    }

    private static State stateAt(WorldAccess world, BlockPos pos) {
        State state = world.blockState(pos.x(), pos.y(), pos.z());
        if (state == null) throw new IllegalArgumentException("null live block state");
        return state;
    }

    private static void requireWorld(WorldAccess world) {
        if (world == null || world.minGenerationY() != HEIGHT_MIN
                || world.generationDepth() != 384) {
            throw new IllegalArgumentException(
                    "pinned Overworld access with min Y -64 and depth 384 is required");
        }
    }

    private static TraceSink requireTrace(TraceSink trace) {
        if (trace == null) throw new IllegalArgumentException("trace sink is required");
        return trace;
    }

    private static String requireMinecraftKey(String key, String description) {
        if (key == null || !key.startsWith("minecraft:")
                || key.length() == "minecraft:".length()) {
            throw new IllegalArgumentException("exact minecraft namespaced " + description
                    + " is required: " + key);
        }
        return key;
    }

    private static void validateCounts(int candidates, int successes, int attemptedWrites,
            int retainedWrites, int attemptedMarks, int retainedMarks) {
        if (candidates < 0 || successes < 0 || successes > candidates) {
            throw new IllegalArgumentException("invalid sculk-vein placement counts");
        }
        validateWriteAndMarkCounts(attemptedWrites, retainedWrites, attemptedMarks,
                retainedMarks);
    }

    private static void validateWriteAndMarkCounts(int attemptedWrites, int retainedWrites,
            int attemptedMarks, int retainedMarks) {
        if (attemptedWrites < 0 || retainedWrites < 0 || retainedWrites > attemptedWrites
                || attemptedMarks < 0 || retainedMarks < 0 || retainedMarks > attemptedMarks) {
            throw new IllegalArgumentException("invalid sculk-vein write/mark counts");
        }
    }

    private record SpreadPos(BlockPos pos, Direction face) {
    }

    private record PhaseSpec(int id, int arity) {
    }

    private static final class WriteCounts {
        private int attemptedWrites;
        private int retainedWrites;
        private int attemptedMarks;
        private int retainedMarks;
    }

    private static final class MutableCounts {
        private int candidates;
        private int successes;
        private int attemptedWrites;
        private int retainedWrites;
        private int attemptedMarks;
        private int retainedMarks;

        private PlacementCounts freeze() {
            return new PlacementCounts(candidates, successes, attemptedWrites, retainedWrites,
                    attemptedMarks, retainedMarks);
        }
    }
}
