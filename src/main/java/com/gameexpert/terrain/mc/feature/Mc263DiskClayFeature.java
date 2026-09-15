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

/**
 * Exact, inactive Minecraft Java 26.3-snapshot-7 disk-clay placed-feature leaf.
 *
 * <p>The supplied world is a live FEATURES decoration-region view. Reads therefore observe earlier
 * feature writes, while writes and post-processing marks may be rejected only at the real region
 * boundary. No canonical generator calls this class until full-pipeline promotion is reviewed.</p>
 */
public final class Mc263DiskClayFeature {
    public static final int UNDERGROUND_ORES_STEP = 6;
    public static final int DISK_CLAY_GLOBAL_INDEX = 31;
    public static final int UPDATE_FLAGS = 2;
    public static final int HALF_HEIGHT = 1;
    public static final String DISK_CLAY = "minecraft:disk_clay";
    public static final String CLAY = "minecraft:clay";
    public static final String DIRT = "minecraft:dirt";
    public static final String WATER = "minecraft:water";

    public static void preflight(Predicate<String> supportsState) {
        Mc263LakeFeature.requireOutputs(supportsState, CLAY);
    }

    public static final String INNER_SERVER_SHA1 =
            "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String DISK_FEATURE_CLASS_SHA256 =
            "2ac1d35ac09709e8e84476fc4ff317ffb7622584ae59b5d4e5ee017519405e51";
    public static final String FEATURE_CLASS_SHA256 =
            "b140a544634d1bc33ccf2cacf00fe0b260652ec52f72ca243f259d3335d43665";
    public static final String CONFIGURED_FEATURE_JSON_SHA256 =
            "6b7b1e156d8a2dcceea60151274d21509c3a7c2af46f2c1f9218849beeddc5c5";
    public static final String PLACED_FEATURE_JSON_SHA256 =
            "0c8225f6f56afb4a0682737f78d624deb80e5357ec6d4aee2fe2cc6b96bf26e3";

    public static final String TRACE_SCHEMA = "mc263-disk-clay-trace-v1";
    public static final int TRACE_BINARY_MAGIC = 0x44434c33; // DCL3
    public static final int TRACE_BINARY_VERSION = 1;

    private static final Map<String, PhaseSpec> TRACE_PHASES = Map.ofEntries(
            Map.entry("candidate", new PhaseSpec(1, 3)),
            Map.entry("fluid", new PhaseSpec(2, 2)),
            Map.entry("biome", new PhaseSpec(3, 2)),
            Map.entry("radius", new PhaseSpec(4, 1)),
            Map.entry("column", new PhaseSpec(5, 2)),
            Map.entry("cell", new PhaseSpec(6, 7)),
            Map.entry("post", new PhaseSpec(7, 4)),
            Map.entry("configured_result", new PhaseSpec(8, 5)),
            Map.entry("placed_result", new PhaseSpec(9, 7)));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override
        public void record(String phase, long... values) {
            throw new AssertionError("disabled disk-clay trace emitted");
        }

        @Override
        public boolean enabled() {
            return false;
        }
    };

    private Mc263DiskClayFeature() {
    }

    /** Complete live view used by placed modifiers and the configured disk. */
    public interface WorldAccess extends Mc263DiskFeatureKernel.WorldAccess {
        int minGenerationY();

        int generationDepth();

        /** Exact live {@code Heightmap.Types.OCEAN_FLOOR_WG} height for this column. */
        int oceanFloorWgHeight(int blockX, int blockZ);

        /** Exact live registry fluid type ({@code water}, {@code flowing_water}, and so on). */
        String fluidState(int blockX, int blockY, int blockZ);

        /** Exact three-dimensional biome holder at this block position. */
        String biomeKey(int blockX, int blockY, int blockZ);

        String blockState(int blockX, int blockY, int blockZ);

        /** Performs the official flag-2 disk write, returning whether it was retained. */
        boolean trySetBlockState(int blockX, int blockY, int blockZ, String state, int flags);

        /** Performs one ordered chunk post-processing mark, returning whether it was retained. */
        boolean tryMarkPosForPostProcessing(int blockX, int blockY, int blockZ);
    }

    /** Numeric event stream kept stable for direct Java/Rust parity probes. */
    @FunctionalInterface
    public interface TraceSink {
        void record(String phase, long... values);

        default boolean enabled() {
            return true;
        }

        static TraceSink disabled() {
            return NO_TRACE;
        }

        default void record(String phase, long value0) {
            if (enabled()) record(phase, new long[]{value0});
        }

        default void record(String phase, long value0, long value1) {
            if (enabled()) record(phase, new long[]{value0, value1});
        }

        default void record(String phase, long value0, long value1, long value2) {
            if (enabled()) record(phase, new long[]{value0, value1, value2});
        }

        default void record(String phase, long value0, long value1, long value2,
                long value3) {
            if (enabled()) record(phase, new long[]{value0, value1, value2, value3});
        }

        default void record(String phase, long value0, long value1, long value2,
                long value3, long value4) {
            if (enabled()) {
                record(phase, new long[]{value0, value1, value2, value3, value4});
            }
        }

        default void record(String phase, long value0, long value1, long value2,
                long value3, long value4, long value5, long value6) {
            if (enabled()) {
                record(phase, new long[]{value0, value1, value2, value3, value4, value5,
                        value6});
            }
        }
    }

    public record TraceEvent(String phase, long[] values) {
        public TraceEvent {
            PhaseSpec spec = TRACE_PHASES.get(phase);
            if (spec == null || values == null || values.length != spec.arity()) {
                throw new IllegalArgumentException("invalid disk-clay trace event: " + phase);
            }
            values = values.clone();
        }

        @Override
        public long[] values() {
            return values.clone();
        }
    }

    public record BlockPos(int x, int y, int z) {
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
            if (!placed && (attemptedWrites != 0 || attemptedPostProcessMarks != 0)) {
                throw new IllegalArgumentException("unplaced disk cannot have attempts");
            }
        }
    }

    /** Runs the independently seeded step-6/index-31 placed feature. */
    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world) {
        return place(worldSeed, sourceBlockX, sourceBlockZ, world, NO_TRACE);
    }

    /** Runs the independently seeded step-6/index-31 placed feature. */
    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world, TraceSink trace) {
        requireWorld(world);
        TraceSink events = requireTrace(trace);
        Mc263DecorationRandom.FeatureRandom seeded = Mc263DecorationRandom.forFeature(worldSeed,
                sourceBlockX, sourceBlockZ, UNDERGROUND_ORES_STEP, DISK_CLAY);
        PlacementCounts counts = placeWithFeatureRandom(sourceBlockX, sourceBlockZ, world,
                seeded.random(), events);
        return new PlacementResult(seeded.decorationSeed(), seeded.featureSeed(),
                seeded.globalIndex(), counts.candidates(), counts.configuredSuccesses(),
                counts.attemptedWrites(), counts.retainedWrites(),
                counts.attemptedPostProcessMarks(), counts.retainedPostProcessMarks());
    }

    /** Executes in-square, OCEAN_FLOOR_WG, fluid predicate, biome, and disk stages. */
    public static PlacementCounts placeWithFeatureRandom(int sourceBlockX, int sourceBlockZ,
            WorldAccess world, WorldgenRandom random, TraceSink trace) {
        requireWorld(world);
        if (random == null) throw new IllegalArgumentException("random is required");
        TraceSink events = requireTrace(trace);
        int globalIndex = Mc263DecorationRandom.globalIndex(UNDERGROUND_ORES_STEP, DISK_CLAY);
        if (globalIndex != DISK_CLAY_GLOBAL_INDEX
                || !Mc263FeatureIndexReceipt.step(UNDERGROUND_ORES_STEP)
                .featureAt(globalIndex).equals(DISK_CLAY)) {
            throw new IllegalStateException("pinned disk-clay feature index changed");
        }

        int x = sourceBlockX + random.nextInt(16);
        int z = sourceBlockZ + random.nextInt(16);
        int y = world.oceanFloorWgHeight(x, z);
        if (y <= world.minGenerationY()) {
            events.record("placed_result", DISK_CLAY_GLOBAL_INDEX, 0, 0, 0, 0, 0, 0);
            return new PlacementCounts(0, 0, 0, 0, 0, 0);
        }
        BlockPos origin = new BlockPos(x, y, z);
        events.record("candidate", x, y, z);

        String fluidState = world.fluidState(x, y, z);
        int fluidId = fluidId(fluidState);
        boolean fluidAccepted = fluidId == 0;
        events.record("fluid", fluidId, fluidAccepted ? 1L : 0L);
        if (!fluidAccepted) {
            events.record("placed_result", DISK_CLAY_GLOBAL_INDEX, 1, 0, 0, 0, 0, 0);
            return new PlacementCounts(1, 0, 0, 0, 0, 0);
        }

        String biome = requireMinecraftKey(world.biomeKey(x, y, z), "biome");
        int biomeId = biomeTraceId(biome);
        boolean biomeAccepted = biomeId >= 0 && biomeContains(biome);
        events.record("biome", biomeId, biomeAccepted ? 1L : 0L);
        if (!biomeAccepted) {
            events.record("placed_result", DISK_CLAY_GLOBAL_INDEX, 1, 0, 0, 0, 0, 0);
            return new PlacementCounts(1, 0, 0, 0, 0, 0);
        }

        ConfiguredResult configured = placeConfigured(random, origin, world, events);
        events.record("placed_result", DISK_CLAY_GLOBAL_INDEX, 1,
                configured.placed() ? 1L : 0L, configured.attemptedWrites(),
                configured.retainedWrites(), configured.attemptedPostProcessMarks(),
                configured.retainedPostProcessMarks());
        return new PlacementCounts(1, configured.placed() ? 1 : 0,
                configured.attemptedWrites(), configured.retainedWrites(),
                configured.attemptedPostProcessMarks(),
                configured.retainedPostProcessMarks());
    }

    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world) {
        return placeConfigured(random, origin, world, NO_TRACE);
    }

    /** Executes the configured {@code DiskFeature} after modifiers select an origin. */
    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world, TraceSink trace) {
        requireWorld(world);
        if (random == null || origin == null) {
            throw new IllegalArgumentException("random and origin are required");
        }
        TraceSink events = requireTrace(trace);
        Mc263DiskFeatureKernel.Result kernel = Mc263DiskFeatureKernel.place(random,
                origin.x(), origin.y(), origin.z(), 2, 3, HALF_HEIGHT, UPDATE_FLAGS, world,
                state -> {
                    int target = targetId(blockKey(state));
                    return new Mc263DiskFeatureKernel.Match(target, target < 2);
                }, (ignored, x, y, z) -> CLAY, kernelEvents(events));
        ConfiguredResult result = new ConfiguredResult(kernel.placed(), kernel.attemptedWrites(),
                kernel.retainedWrites(), kernel.attemptedPostProcessMarks(),
                kernel.retainedPostProcessMarks());
        events.record("configured_result", kernel.placed() ? 1L : 0L, result.attemptedWrites(),
                result.retainedWrites(), result.attemptedPostProcessMarks(),
                result.retainedPostProcessMarks());
        return result;
    }

    /** Stable big-endian trace fixture shared by the Java and Rust dormant leaves. */
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

    private static Mc263DiskFeatureKernel.Events kernelEvents(TraceSink trace) {
        if (!trace.enabled()) return Mc263DiskFeatureKernel.Events.disabled();
        return new Mc263DiskFeatureKernel.Events() {
            @Override public boolean enabled() { return true; }
            @Override public void radius(int radius) { trace.record("radius", radius); }
            @Override public void column(int x, int z) { trace.record("column", x, z); }
            @Override public void cell(int x, int y, int z, int target, boolean accepted,
                    int flags, boolean retained) {
                trace.record("cell", x, y, z, target, accepted ? 1L : 0L, flags,
                        retained ? 1L : 0L);
            }
            @Override public void post(int x, int y, int z, boolean retained) {
                trace.record("post", x, y, z, retained ? 1L : 0L);
            }
        };
    }

    private static boolean biomeContains(String biomeKey) {
        String biome = requireMinecraftKey(biomeKey, "biome");
        return Mc263FeatureIndexReceipt.biome(biome)
                .featuresAtStep(UNDERGROUND_ORES_STEP).stream()
                .anyMatch(reference -> reference.featureKey().equals(DISK_CLAY));
    }

    /** Plains-first parity ID, then the pinned receipt order with plains omitted. */
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

    private static int targetId(String block) {
        return switch (block) {
            case DIRT -> 0;
            case CLAY -> 1;
            default -> 2;
        };
    }

    private static int fluidId(String state) {
        return switch (requireMinecraftKey(state, "fluid type")) {
            case WATER -> 0;
            case "minecraft:flowing_water" -> 1;
            default -> 2;
        };
    }

    private static void requireWorld(WorldAccess world) {
        if (world == null || world.minGenerationY() != -64 || world.generationDepth() != 384) {
            throw new IllegalArgumentException(
                    "pinned Overworld access with min Y -64 and depth 384 is required");
        }
    }

    private static TraceSink requireTrace(TraceSink trace) {
        if (trace == null) throw new IllegalArgumentException("trace sink is required");
        return trace;
    }

    private static String blockKey(String state) {
        String key = requireMinecraftKey(state, "block or fluid state");
        int properties = key.indexOf('[');
        return properties < 0 ? key : key.substring(0, properties);
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
            throw new IllegalArgumentException("invalid disk-clay placement counts");
        }
        validateWriteAndMarkCounts(attemptedWrites, retainedWrites, attemptedMarks,
                retainedMarks);
    }

    private static void validateWriteAndMarkCounts(int attemptedWrites, int retainedWrites,
            int attemptedMarks, int retainedMarks) {
        if (attemptedWrites < 0 || retainedWrites < 0 || retainedWrites > attemptedWrites
                || attemptedMarks < 0 || retainedMarks < 0 || retainedMarks > attemptedMarks) {
            throw new IllegalArgumentException("invalid disk-clay write/mark counts");
        }
    }

    private record PhaseSpec(int id, int arity) {
    }

    private static final class MutableCounts {
        private int attemptedWrites;
        private int retainedWrites;
        private int attemptedMarks;
        private int retainedMarks;
    }
}
