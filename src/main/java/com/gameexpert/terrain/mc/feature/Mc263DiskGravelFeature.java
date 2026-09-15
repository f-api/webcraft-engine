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

/** Exact, inactive Minecraft Java 26.3-snapshot-7 disk-gravel placed-feature leaf. */
public final class Mc263DiskGravelFeature {
    public static final int UNDERGROUND_ORES_STEP = 6;
    public static final int DISK_GRAVEL_GLOBAL_INDEX = 32;
    public static final int UPDATE_FLAGS = 2;
    public static final int HALF_HEIGHT = 2;
    public static final String DISK_GRAVEL = "minecraft:disk_gravel";
    public static final String GRAVEL = "minecraft:gravel";
    public static final String DIRT = "minecraft:dirt";
    public static final String GRASS_BLOCK = "minecraft:grass_block";
    public static final String WATER = "minecraft:water";

    public static void preflight(Predicate<String> supportsState) {
        Mc263LakeFeature.requireOutputs(supportsState, GRAVEL);
    }

    public static final String INNER_SERVER_SHA1 =
            "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String DISK_FEATURE_CLASS_SHA256 =
            "2ac1d35ac09709e8e84476fc4ff317ffb7622584ae59b5d4e5ee017519405e51";
    public static final String FEATURE_CLASS_SHA256 =
            "b140a544634d1bc33ccf2cacf00fe0b260652ec52f72ca243f259d3335d43665";
    public static final String CONFIGURED_FEATURE_JSON_SHA256 =
            "ea09661406d6156ca58a91bc6a46b5e6715470d9ae79cf40fe7d0cec88736f2c";
    public static final String PLACED_FEATURE_JSON_SHA256 =
            "ab8f6bd6cbcb5f47a9e167afae6a72c739ef64edba6f50b8d3a372c22d93a6e3";

    public static final String TRACE_SCHEMA = "mc263-disk-gravel-trace-v1";
    public static final int TRACE_BINARY_MAGIC = 0x44475633; // DGV3
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
        @Override public void record(String phase, long... values) {
            throw new AssertionError("disabled disk-gravel trace emitted");
        }
        @Override public boolean enabled() { return false; }
    };

    private Mc263DiskGravelFeature() { }

    public interface WorldAccess extends Mc263DiskFeatureKernel.WorldAccess {
        int minGenerationY();

        int generationDepth();

        int oceanFloorWgHeight(int blockX, int blockZ);

        String fluidState(int blockX, int blockY, int blockZ);

        String biomeKey(int blockX, int blockY, int blockZ);
    }

    @FunctionalInterface
    public interface TraceSink {
        void record(String phase, long... values);

        default boolean enabled() { return true; }

        static TraceSink disabled() { return NO_TRACE; }

        default void record(String phase, long value0) {
            if (enabled()) record(phase, new long[]{value0});
        }
        default void record(String phase, long value0, long value1) {
            if (enabled()) record(phase, new long[]{value0, value1});
        }
        default void record(String phase, long value0, long value1, long value2) {
            if (enabled()) record(phase, new long[]{value0, value1, value2});
        }
        default void record(String phase, long value0, long value1, long value2, long value3) {
            if (enabled()) record(phase, new long[]{value0, value1, value2, value3});
        }
        default void record(String phase, long value0, long value1, long value2, long value3,
                long value4) {
            if (enabled()) record(phase, new long[]{value0, value1, value2, value3, value4});
        }
        default void record(String phase, long value0, long value1, long value2, long value3,
                long value4, long value5, long value6) {
            if (enabled()) record(phase, new long[]{value0, value1, value2, value3, value4,
                    value5, value6});
        }
    }

    public record TraceEvent(String phase, long[] values) {
        public TraceEvent {
            PhaseSpec spec = TRACE_PHASES.get(phase);
            if (spec == null || values == null || values.length != spec.arity()) {
                throw new IllegalArgumentException("invalid disk-gravel trace event: " + phase);
            }
            values = values.clone();
        }
        @Override public long[] values() { return values.clone(); }
    }

    public record BlockPos(int x, int y, int z) { }

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

    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world) {
        return place(worldSeed, sourceBlockX, sourceBlockZ, world, NO_TRACE);
    }

    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world, TraceSink trace) {
        requireWorld(world);
        TraceSink events = requireTrace(trace);
        Mc263DecorationRandom.FeatureRandom seeded = Mc263DecorationRandom.forFeature(worldSeed,
                sourceBlockX, sourceBlockZ, UNDERGROUND_ORES_STEP, DISK_GRAVEL);
        PlacementCounts counts = placeWithFeatureRandom(sourceBlockX, sourceBlockZ, world,
                seeded.random(), events);
        return new PlacementResult(seeded.decorationSeed(), seeded.featureSeed(),
                seeded.globalIndex(), counts.candidates(), counts.configuredSuccesses(),
                counts.attemptedWrites(), counts.retainedWrites(),
                counts.attemptedPostProcessMarks(), counts.retainedPostProcessMarks());
    }

    /** Executes in-square, OCEAN_FLOOR_WG, source-water predicate, biome, and disk stages. */
    public static PlacementCounts placeWithFeatureRandom(int sourceBlockX, int sourceBlockZ,
            WorldAccess world, WorldgenRandom random, TraceSink trace) {
        requireWorld(world);
        if (random == null) throw new IllegalArgumentException("random is required");
        TraceSink events = requireTrace(trace);
        int globalIndex = Mc263DecorationRandom.globalIndex(UNDERGROUND_ORES_STEP, DISK_GRAVEL);
        if (globalIndex != DISK_GRAVEL_GLOBAL_INDEX
                || !Mc263FeatureIndexReceipt.step(UNDERGROUND_ORES_STEP)
                .featureAt(globalIndex).equals(DISK_GRAVEL)) {
            throw new IllegalStateException("pinned disk-gravel feature index changed");
        }

        int x = sourceBlockX + random.nextInt(16);
        int z = sourceBlockZ + random.nextInt(16);
        int y = world.oceanFloorWgHeight(x, z);
        if (y <= world.minGenerationY()) {
            events.record("placed_result", DISK_GRAVEL_GLOBAL_INDEX, 0, 0, 0, 0, 0, 0);
            return new PlacementCounts(0, 0, 0, 0, 0, 0);
        }
        BlockPos origin = new BlockPos(x, y, z);
        events.record("candidate", x, y, z);

        int fluidId = fluidId(world.fluidState(x, y, z));
        boolean fluidAccepted = fluidId == 0;
        events.record("fluid", fluidId, fluidAccepted ? 1L : 0L);
        if (!fluidAccepted) {
            events.record("placed_result", DISK_GRAVEL_GLOBAL_INDEX, 1, 0, 0, 0, 0, 0);
            return new PlacementCounts(1, 0, 0, 0, 0, 0);
        }

        String biome = requireMinecraftKey(world.biomeKey(x, y, z), "biome");
        int biomeId = biomeTraceId(biome);
        boolean biomeAccepted = biomeId >= 0 && biomeContains(biome);
        events.record("biome", biomeId, biomeAccepted ? 1L : 0L);
        if (!biomeAccepted) {
            events.record("placed_result", DISK_GRAVEL_GLOBAL_INDEX, 1, 0, 0, 0, 0, 0);
            return new PlacementCounts(1, 0, 0, 0, 0, 0);
        }

        ConfiguredResult configured = placeConfigured(random, origin, world, events);
        events.record("placed_result", DISK_GRAVEL_GLOBAL_INDEX, 1,
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

    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world, TraceSink trace) {
        requireWorld(world);
        if (random == null || origin == null) {
            throw new IllegalArgumentException("random and origin are required");
        }
        TraceSink events = requireTrace(trace);
        Mc263DiskFeatureKernel.Result kernel = Mc263DiskFeatureKernel.place(random,
                origin.x(), origin.y(), origin.z(), 2, 5, HALF_HEIGHT, UPDATE_FLAGS, world,
                state -> {
                    int target = targetId(blockKey(state));
                    return new Mc263DiskFeatureKernel.Match(target, target < 2);
                }, (ignored, x, y, z) -> GRAVEL, kernelEvents(events));
        ConfiguredResult result = new ConfiguredResult(kernel.placed(), kernel.attemptedWrites(),
                kernel.retainedWrites(), kernel.attemptedPostProcessMarks(),
                kernel.retainedPostProcessMarks());
        events.record("configured_result", kernel.placed() ? 1L : 0L,
                result.attemptedWrites(), result.retainedWrites(),
                result.attemptedPostProcessMarks(), result.retainedPostProcessMarks());
        return result;
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
        return Mc263FeatureIndexReceipt.biome(biome).featuresAtStep(UNDERGROUND_ORES_STEP)
                .stream().anyMatch(reference -> reference.featureKey().equals(DISK_GRAVEL));
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

    private static int targetId(String block) {
        return switch (block) {
            case DIRT -> 0;
            case GRASS_BLOCK -> 1;
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
            throw new IllegalArgumentException("invalid disk-gravel placement counts");
        }
        validateWriteAndMarkCounts(attemptedWrites, retainedWrites, attemptedMarks,
                retainedMarks);
    }

    private static void validateWriteAndMarkCounts(int attemptedWrites, int retainedWrites,
            int attemptedMarks, int retainedMarks) {
        if (attemptedWrites < 0 || retainedWrites < 0 || retainedWrites > attemptedWrites
                || attemptedMarks < 0 || retainedMarks < 0 || retainedMarks > attemptedMarks) {
            throw new IllegalArgumentException("invalid disk-gravel write/mark counts");
        }
    }

    private record PhaseSpec(int id, int arity) { }
}
