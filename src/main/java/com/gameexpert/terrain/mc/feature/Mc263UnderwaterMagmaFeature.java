package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.util.function.Predicate;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Exact, inactive Minecraft Java 26.3-snapshot-7 underwater-magma placed-feature leaf. */
public final class Mc263UnderwaterMagmaFeature {
    public static final int UNDERGROUND_ORES_STEP = 6;
    public static final int GLOBAL_INDEX = 26;
    public static final int UPDATE_FLAGS = 2;
    public static final int MIN_COUNT = 44;
    public static final int MAX_COUNT = 52;
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 256;
    public static final int FLOOR_SEARCH_RANGE = 5;
    public static final int PLACEMENT_RADIUS = 1;
    public static final String FEATURE = "minecraft:underwater_magma";
    public static final String WATER = "minecraft:water";
    public static final String MAGMA = "minecraft:magma_block";

    public static void preflight(Predicate<String> supportsState) {
        Mc263LakeFeature.requireOutputs(supportsState, MAGMA);
    }

    public static final String INNER_SERVER_SHA1 =
            "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String FEATURE_CLASS_SHA256 =
            "fa4e30a03e3be75e278c05e0ab7f717bb3bf2616f3b172c4a3b565ad835fbec8";
    public static final String CONFIGURED_JSON_SHA256 =
            "be17fa1ad689e2252f839649aaad5cae8f83d0f00bbf78ba33658ccc5a812110";
    public static final String PLACED_JSON_SHA256 =
            "4db28fc26370115712fc8176a3644d8b2962efd9b5c666e68ee22801483fcaf8";

    public static final String TRACE_SCHEMA = "mc263-underwater-magma-trace-v1";
    public static final int TRACE_MAGIC = 0x554d5633; // UMV3
    public static final int TRACE_VERSION = 1;
    private static final Map<String, PhaseSpec> PHASES = Map.ofEntries(
            Map.entry("count", new PhaseSpec(1, 1)),
            Map.entry("candidate", new PhaseSpec(2, 4)),
            Map.entry("height", new PhaseSpec(3, 3)),
            Map.entry("biome", new PhaseSpec(4, 3)),
            Map.entry("column", new PhaseSpec(5, 6)),
            Map.entry("floor", new PhaseSpec(6, 3)),
            Map.entry("cell", new PhaseSpec(7, 7)),
            Map.entry("face", new PhaseSpec(8, 7)),
            Map.entry("write", new PhaseSpec(9, 6)),
            Map.entry("configured_result", new PhaseSpec(10, 4)),
            Map.entry("placed_result", new PhaseSpec(11, 6)));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public void record(String phase, long... values) {
            throw new AssertionError("disabled underwater-magma trace emitted");
        }
        @Override public boolean enabled() { return false; }
    };

    private Mc263UnderwaterMagmaFeature() {
    }

    public enum Face {
        UP, SOUTH, WEST, NORTH, EAST
    }

    public enum FaceOcclusion {
        FULL, NOT_FULL, UNKNOWN
    }

    /** One atomic live-state read and its face-occlusion result. */
    public record FaceFact(String state, FaceOcclusion occlusion) {
        public FaceFact {
            requireKey(state, "face block state");
            if (occlusion == null) throw new IllegalArgumentException("face occlusion is required");
        }
    }

    public interface WorldAccess {
        int minGenerationY();

        int generationDepth();

        int oceanFloorWgHeight(int x, int z);

        String biomeKey(int x, int y, int z);

        String blockState(int x, int y, int z);

        /** Exact state identity and face result derived from one live state query. */
        FaceFact faceFact(int x, int y, int z, Face coveredFace);

        /** Executes flag-2 setBlock; its result is not part of official success accounting. */
        boolean trySetBlockState(int x, int y, int z, String state, int flags);
    }

    @FunctionalInterface
    public interface TraceSink {
        void record(String phase, long... values);
        default boolean enabled() { return true; }
        static TraceSink disabled() { return NO_TRACE; }
        default void record(String phase, long a) {
            if (enabled()) record(phase, new long[]{a});
        }
        default void record(String phase, long a, long b, long c) {
            if (enabled()) record(phase, new long[]{a, b, c});
        }
        default void record(String phase, long a, long b, long c, long d) {
            if (enabled()) record(phase, new long[]{a, b, c, d});
        }
        default void record(String phase, long a, long b, long c, long d, long e, long f) {
            if (enabled()) record(phase, new long[]{a, b, c, d, e, f});
        }
        default void record(String phase, long a, long b, long c, long d, long e, long f,
                long g) {
            if (enabled()) record(phase, new long[]{a, b, c, d, e, f, g});
        }
    }

    public record TraceEvent(String phase, long[] values) {
        public TraceEvent {
            PhaseSpec spec = PHASES.get(phase);
            if (spec == null || values == null || values.length != spec.arity()) {
                throw new IllegalArgumentException("invalid underwater-magma trace: " + phase);
            }
            values = values.clone();
        }
        @Override public long[] values() { return values.clone(); }
    }

    public record BlockPos(int x, int y, int z) {
    }

    public record ConfiguredResult(boolean placed, int officialWriteCount,
                                   int retainedWriteCount) {
        public ConfiguredResult {
            if (officialWriteCount < 0 || retainedWriteCount < 0
                    || retainedWriteCount > officialWriteCount || placed != (officialWriteCount > 0)) {
                throw new IllegalArgumentException("invalid underwater-magma configured counts");
            }
        }
    }

    public record PlacementCounts(int generatedCount, int candidates,
                                  int configuredSuccesses, int officialWrites,
                                  int retainedWrites) {
        public PlacementCounts {
            if (generatedCount < MIN_COUNT || generatedCount > MAX_COUNT || candidates < 0
                    || candidates > generatedCount || configuredSuccesses < 0
                    || configuredSuccesses > candidates || officialWrites < 0
                    || retainedWrites < 0 || retainedWrites > officialWrites) {
                throw new IllegalArgumentException("invalid underwater-magma placement counts");
            }
        }
    }

    public record PlacementResult(long decorationSeed, long featureSeed, int globalIndex,
                                  PlacementCounts counts) {
    }

    public static PlacementResult place(long worldSeed, int sourceX, int sourceZ,
            WorldAccess world) {
        return place(worldSeed, sourceX, sourceZ, world, NO_TRACE);
    }

    public static PlacementResult place(long worldSeed, int sourceX, int sourceZ,
            WorldAccess world, TraceSink trace) {
        requireWorld(world);
        Mc263DecorationRandom.FeatureRandom seeded = Mc263DecorationRandom.forFeature(worldSeed,
                sourceX, sourceZ, UNDERGROUND_ORES_STEP, FEATURE);
        PlacementCounts counts = placeWithFeatureRandom(sourceX, sourceZ, world,
                seeded.random(), requireTrace(trace));
        return new PlacementResult(seeded.decorationSeed(), seeded.featureSeed(),
                seeded.globalIndex(), counts);
    }

    public static PlacementCounts placeWithFeatureRandom(int sourceX, int sourceZ,
            WorldAccess world, WorldgenRandom random, TraceSink trace) {
        requireWorld(world);
        if (random == null) throw new IllegalArgumentException("random is required");
        TraceSink events = requireTrace(trace);
        if (Mc263DecorationRandom.globalIndex(UNDERGROUND_ORES_STEP, FEATURE) != GLOBAL_INDEX
                || !Mc263FeatureIndexReceipt.step(UNDERGROUND_ORES_STEP)
                .featureAt(GLOBAL_INDEX).equals(FEATURE)) {
            throw new IllegalStateException("pinned underwater-magma feature index changed");
        }

        int count = MIN_COUNT + random.nextInt(MAX_COUNT - MIN_COUNT + 1);
        events.record("count", count);
        int candidates = 0;
        int successes = 0;
        int writes = 0;
        int retained = 0;
        for (int attempt = 0; attempt < count; attempt++) {
            int x = sourceX + random.nextInt(16);
            int z = sourceZ + random.nextInt(16);
            int y = MIN_Y + random.nextInt(MAX_Y - MIN_Y + 1);
            events.record("candidate", attempt, x, y, z);
            int oceanFloor = world.oceanFloorWgHeight(x, z);
            boolean heightAccepted = y <= oceanFloor - 2;
            events.record("height", attempt, oceanFloor, heightAccepted ? 1L : 0L);
            if (!heightAccepted) continue;
            candidates++;
            String biome = requireKey(world.biomeKey(x, y, z), "biome");
            int biomeId = biomeTraceId(biome);
            boolean biomeAccepted = biomeId >= 0 && biomeContains(biome);
            events.record("biome", attempt, biomeId, biomeAccepted ? 1L : 0L);
            if (!biomeAccepted) continue;
            ConfiguredResult result = placeConfigured(random, new BlockPos(x, y, z), world,
                    events, attempt);
            if (result.placed()) successes++;
            writes += result.officialWriteCount();
            retained += result.retainedWriteCount();
        }
        events.record("placed_result", GLOBAL_INDEX, count, candidates, successes, writes,
                retained);
        return new PlacementCounts(count, candidates, successes, writes, retained);
    }

    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world) {
        return placeConfigured(random, origin, world, NO_TRACE, 0);
    }

    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world, TraceSink trace) {
        return placeConfigured(random, origin, world, requireTrace(trace), 0);
    }

    private static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world, TraceSink trace, int attempt) {
        requireWorld(world);
        if (random == null || origin == null) {
            throw new IllegalArgumentException("random and origin are required");
        }
        int floorY = findFloor(world, origin, trace, attempt);
        if (floorY == Integer.MIN_VALUE) {
            trace.record("floor", attempt, 0, 0);
            trace.record("configured_result", attempt, 0, 0, 0);
            return new ConfiguredResult(false, 0, 0);
        }
        trace.record("floor", attempt, 1, floorY);
        int writes = 0;
        int retained = 0;
        for (int z = origin.z() - 1; z <= origin.z() + 1; z++) {
            for (int y = floorY - 1; y <= floorY + 1; y++) {
                for (int x = origin.x() - 1; x <= origin.x() + 1; x++) {
                    int bits = Float.floatToRawIntBits(random.nextFloat());
                    boolean probability = Float.intBitsToFloat(bits) < 0.5F;
                    boolean valid = probability && valid(world, x, y, z, trace, attempt);
                    trace.record("cell", attempt, x, y, z, bits, probability ? 1L : 0L,
                            valid ? 1L : 0L);
                    if (!valid) continue;
                    writes++;
                    boolean kept = world.trySetBlockState(x, y, z, MAGMA, UPDATE_FLAGS);
                    if (kept) retained++;
                    trace.record("write", attempt, x, y, z, UPDATE_FLAGS, kept ? 1L : 0L);
                }
            }
        }
        trace.record("configured_result", attempt, writes > 0 ? 1L : 0L, writes, retained);
        return new ConfiguredResult(writes > 0, writes, retained);
    }

    private static int findFloor(WorldAccess world, BlockPos origin, TraceSink trace,
            int attempt) {
        String originState = state(world, origin.x(), origin.y(), origin.z());
        boolean originWater = blockKey(originState).equals(WATER);
        trace.record("column", attempt, 0, 0, stateId(originState), originWater ? 1L : 0L,
                originWater ? 0L : 1L);
        if (!originWater) return Integer.MIN_VALUE;
        scanEdge(world, origin, 1, 1, trace, attempt); // official ceiling scan happens first
        return scanEdge(world, origin, -1, 2, trace, attempt);
    }

    private static int scanEdge(WorldAccess world, BlockPos origin, int step, int direction,
            TraceSink trace, int attempt) {
        int y = origin.y();
        for (int i = 1; i < FLOOR_SEARCH_RANGE; i++) {
            String current = state(world, origin.x(), y, origin.z());
            boolean water = blockKey(current).equals(WATER);
            trace.record("column", attempt, direction, y - origin.y(), stateId(current),
                    water ? 1L : 0L, water ? 0L : 1L);
            if (!water) break;
            y += step;
        }
        String edge = state(world, origin.x(), y, origin.z());
        boolean water = blockKey(edge).equals(WATER);
        trace.record("column", attempt, direction, y - origin.y(), stateId(edge),
                water ? 1L : 0L, water ? 0L : 1L);
        return water ? Integer.MIN_VALUE : y;
    }

    private static boolean valid(WorldAccess world, int x, int y, int z, TraceSink trace,
            int attempt) {
        String target = state(world, x, y, z);
        String targetKey = blockKey(target);
        if (targetKey.equals(WATER) || isAir(targetKey)) return false;
        if (!faceFull(world, x, y - 1, z, Face.UP, trace, attempt)) return false;
        if (!faceFull(world, x, y, z - 1, Face.SOUTH, trace, attempt)) return false;
        if (!faceFull(world, x + 1, y, z, Face.WEST, trace, attempt)) return false;
        if (!faceFull(world, x, y, z + 1, Face.NORTH, trace, attempt)) return false;
        return faceFull(world, x - 1, y, z, Face.EAST, trace, attempt);
    }

    private static boolean faceFull(WorldAccess world, int x, int y, int z, Face face,
            TraceSink trace, int attempt) {
        FaceFact fact = world.faceFact(x, y, z, face);
        boolean full = fact.occlusion() == FaceOcclusion.FULL;
        int stateId = fact.occlusion() == FaceOcclusion.UNKNOWN ? 6
                : blockKey(fact.state()).equals("minecraft:stone") && !full
                ? 5 : stateId(fact.state());
        trace.record("face", attempt, x, y, z, face.ordinal(), stateId, full ? 1L : 0L);
        return full;
    }

    public static byte[] encodeTraceFixture(List<TraceEvent> trace) {
        if (trace == null) throw new IllegalArgumentException("trace is required");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            byte[] schema = TRACE_SCHEMA.getBytes(StandardCharsets.UTF_8);
            out.writeInt(TRACE_MAGIC);
            out.writeShort(TRACE_VERSION);
            out.writeShort(schema.length);
            out.write(schema);
            out.writeInt(trace.size());
            for (TraceEvent event : trace) {
                if (event == null) throw new IllegalArgumentException("null trace event");
                PhaseSpec spec = PHASES.get(event.phase());
                long[] values = event.values();
                out.writeByte(spec.id());
                out.writeByte(values.length);
                for (long value : values) out.writeLong(value);
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static boolean biomeContains(String biome) {
        return Mc263FeatureIndexReceipt.biome(biome).featuresAtStep(UNDERGROUND_ORES_STEP)
                .stream().anyMatch(reference -> reference.featureKey().equals(FEATURE));
    }

    private static int biomeTraceId(String biome) {
        if (biome.equals("minecraft:plains")) return 0;
        int id = 1;
        for (Mc263FeatureIndexReceipt.BiomeFeatureData candidate
                : Mc263FeatureIndexReceipt.biomes()) {
            if (candidate.biomeKey().equals("minecraft:plains")) continue;
            if (candidate.biomeKey().equals(biome)) return id;
            id++;
        }
        return -1;
    }

    private static int stateId(String state) {
        return switch (blockKey(state)) {
            case WATER -> 0;
            case "minecraft:air" -> 1;
            case "minecraft:cave_air" -> 2;
            case "minecraft:void_air" -> 3;
            case "minecraft:stone" -> 4;
            default -> 6;
        };
    }

    private static boolean isAir(String key) {
        return key.equals("minecraft:air") || key.equals("minecraft:cave_air")
                || key.equals("minecraft:void_air");
    }

    private static String state(WorldAccess world, int x, int y, int z) {
        return requireKey(world.blockState(x, y, z), "block state");
    }

    private static String blockKey(String state) {
        String key = requireKey(state, "block state");
        int properties = key.indexOf('[');
        return properties < 0 ? key : key.substring(0, properties);
    }

    private static String requireKey(String key, String description) {
        if (key == null || !key.startsWith("minecraft:")
                || key.length() == "minecraft:".length()) {
            throw new IllegalArgumentException("exact minecraft namespaced " + description
                    + " is required: " + key);
        }
        return key;
    }

    private static TraceSink requireTrace(TraceSink trace) {
        if (trace == null) throw new IllegalArgumentException("trace is required");
        return trace;
    }

    private static void requireWorld(WorldAccess world) {
        if (world == null || world.minGenerationY() != MIN_Y || world.generationDepth() != 384) {
            throw new IllegalArgumentException("pinned Overworld world access is required");
        }
    }

    private record PhaseSpec(int id, int arity) {
    }
}
