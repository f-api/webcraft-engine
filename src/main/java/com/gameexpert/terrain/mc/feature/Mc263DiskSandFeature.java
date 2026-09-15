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

/** Exact, inactive Minecraft Java 26.3-snapshot-7 disk-sand placed-feature leaf. */
public final class Mc263DiskSandFeature {
    public static final int UNDERGROUND_ORES_STEP = 6;
    public static final int DISK_SAND_GLOBAL_INDEX = 30;
    public static final int CANDIDATE_COUNT = 3;
    public static final int UPDATE_FLAGS = 2;
    public static final int HALF_HEIGHT = 2;
    public static final String DISK_SAND = "minecraft:disk_sand";
    public static final String DIRT = "minecraft:dirt";
    public static final String GRASS_BLOCK = "minecraft:grass_block";
    public static final String SAND = "minecraft:sand";
    public static final String SANDSTONE = "minecraft:sandstone";
    public static final String WATER = "minecraft:water";

    public static void preflight(Predicate<String> supportsState) {
        Mc263LakeFeature.requireOutputs(supportsState, SAND, SANDSTONE);
    }

    public static final String INNER_SERVER_SHA1 =
            "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String DISK_FEATURE_CLASS_SHA256 =
            "2ac1d35ac09709e8e84476fc4ff317ffb7622584ae59b5d4e5ee017519405e51";
    public static final String RULE_PROVIDER_CLASS_SHA256 =
            "54fd879c1249ee9e210baeb4e0bd1ed4108f979ab4621299ca99d6055498b00a";
    public static final String CONFIGURED_FEATURE_JSON_SHA256 =
            "46b51e04b348547b90ad6dd7e4e38a1a9197f2267b4ee54475216346ad5efedf";
    public static final String PLACED_FEATURE_JSON_SHA256 =
            "17b172392fb99850e4918ac9774b99d44c36185893feeb1aa0b7ed54a16b1acf";

    public static final String TRACE_SCHEMA = "mc263-disk-sand-trace-v1";
    public static final int TRACE_BINARY_MAGIC = 0x44534e33; // DSN3
    public static final int TRACE_BINARY_VERSION = 1;

    private static final Map<String, PhaseSpec> TRACE_PHASES = Map.ofEntries(
            Map.entry("candidate", new PhaseSpec(1, 4)),
            Map.entry("fluid", new PhaseSpec(2, 3)),
            Map.entry("biome", new PhaseSpec(3, 3)),
            Map.entry("radius", new PhaseSpec(4, 2)),
            Map.entry("column", new PhaseSpec(5, 3)),
            Map.entry("cell", new PhaseSpec(6, 8)),
            Map.entry("provider", new PhaseSpec(7, 6)),
            Map.entry("post", new PhaseSpec(8, 5)),
            Map.entry("configured_result", new PhaseSpec(9, 6)),
            Map.entry("placed_result", new PhaseSpec(10, 7)));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override
        public void record(String phase, long... values) {
            throw new AssertionError("disabled disk-sand trace emitted");
        }

        @Override
        public boolean enabled() {
            return false;
        }
    };

    private Mc263DiskSandFeature() {
    }

    /** Live FEATURES decoration-region view used by the placed modifiers and disk body. */
    public interface WorldAccess extends Mc263DiskFeatureKernel.WorldAccess {
        int minGenerationY();

        int generationDepth();

        int oceanFloorWgHeight(int blockX, int blockZ);

        /** Exact live registry fluid type, independent of the visible block-state properties. */
        String fluidState(int blockX, int blockY, int blockZ);

        String biomeKey(int blockX, int blockY, int blockZ);
    }

    @FunctionalInterface
    public interface TraceSink {
        void record(String phase, long... values);

        default boolean enabled() {
            return true;
        }

        static TraceSink disabled() {
            return NO_TRACE;
        }

        default void record(String phase, long a, long b) {
            if (enabled()) record(phase, new long[]{a, b});
        }

        default void record(String phase, long a, long b, long c) {
            if (enabled()) record(phase, new long[]{a, b, c});
        }

        default void record(String phase, long a, long b, long c, long d) {
            if (enabled()) record(phase, new long[]{a, b, c, d});
        }

        default void record(String phase, long a, long b, long c, long d, long e) {
            if (enabled()) record(phase, new long[]{a, b, c, d, e});
        }

        default void record(String phase, long a, long b, long c, long d, long e, long f) {
            if (enabled()) record(phase, new long[]{a, b, c, d, e, f});
        }

        default void record(String phase, long a, long b, long c, long d, long e, long f,
                long g) {
            if (enabled()) record(phase, new long[]{a, b, c, d, e, f, g});
        }

        default void record(String phase, long a, long b, long c, long d, long e, long f,
                long g, long h) {
            if (enabled()) record(phase, new long[]{a, b, c, d, e, f, g, h});
        }
    }

    public record TraceEvent(String phase, long[] values) {
        public TraceEvent {
            PhaseSpec spec = TRACE_PHASES.get(phase);
            if (spec == null || values == null || values.length != spec.arity()) {
                throw new IllegalArgumentException("invalid disk-sand trace event: " + phase);
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

    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world) {
        return place(worldSeed, sourceBlockX, sourceBlockZ, world, NO_TRACE);
    }

    /** Runs the independently seeded step-6/index-30 placed feature. */
    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world, TraceSink trace) {
        requireWorld(world);
        TraceSink events = requireTrace(trace);
        Mc263DecorationRandom.FeatureRandom seeded = Mc263DecorationRandom.forFeature(worldSeed,
                sourceBlockX, sourceBlockZ, UNDERGROUND_ORES_STEP, DISK_SAND);
        PlacementCounts counts = placeWithFeatureRandom(sourceBlockX, sourceBlockZ, world,
                seeded.random(), events);
        return new PlacementResult(seeded.decorationSeed(), seeded.featureSeed(),
                seeded.globalIndex(), counts.candidates(), counts.configuredSuccesses(),
                counts.attemptedWrites(), counts.retainedWrites(),
                counts.attemptedPostProcessMarks(), counts.retainedPostProcessMarks());
    }

    /** Executes constant-count, in-square, heightmap, source-water, biome, then disk stages. */
    public static PlacementCounts placeWithFeatureRandom(int sourceBlockX, int sourceBlockZ,
            WorldAccess world, WorldgenRandom random, TraceSink trace) {
        requireWorld(world);
        if (random == null) throw new IllegalArgumentException("random is required");
        TraceSink events = requireTrace(trace);
        int globalIndex = Mc263DecorationRandom.globalIndex(UNDERGROUND_ORES_STEP, DISK_SAND);
        if (globalIndex != DISK_SAND_GLOBAL_INDEX
                || !Mc263FeatureIndexReceipt.step(UNDERGROUND_ORES_STEP)
                .featureAt(globalIndex).equals(DISK_SAND)) {
            throw new IllegalStateException("pinned disk-sand feature index changed");
        }

        MutableCounts counts = new MutableCounts();
        for (int attempt = 0; attempt < CANDIDATE_COUNT; attempt++) {
            int x = sourceBlockX + random.nextInt(16);
            int z = sourceBlockZ + random.nextInt(16);
            int y = world.oceanFloorWgHeight(x, z);
            if (y <= world.minGenerationY()) continue;
            counts.candidates++;
            events.record("candidate", attempt, x, y, z);

            int fluid = fluidId(world.fluidState(x, y, z));
            boolean fluidAccepted = fluid == 0;
            events.record("fluid", attempt, fluid, fluidAccepted ? 1L : 0L);
            if (!fluidAccepted) continue;

            String biome = requireMinecraftKey(world.biomeKey(x, y, z), "biome");
            int biomeId = biomeTraceId(biome);
            boolean biomeAccepted = biomeId >= 0 && biomeContains(biome);
            events.record("biome", attempt, biomeId, biomeAccepted ? 1L : 0L);
            if (!biomeAccepted) continue;

            ConfiguredResult configured = placeConfigured(random, new BlockPos(x, y, z), world,
                    events, attempt);
            if (configured.placed()) counts.configuredSuccesses++;
            counts.attemptedWrites += configured.attemptedWrites();
            counts.retainedWrites += configured.retainedWrites();
            counts.attemptedMarks += configured.attemptedPostProcessMarks();
            counts.retainedMarks += configured.retainedPostProcessMarks();
        }
        events.record("placed_result", DISK_SAND_GLOBAL_INDEX, counts.candidates,
                counts.configuredSuccesses, counts.attemptedWrites, counts.retainedWrites,
                counts.attemptedMarks, counts.retainedMarks);
        return counts.freeze();
    }

    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world) {
        return placeConfigured(random, origin, world, NO_TRACE, 0);
    }

    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world, TraceSink trace) {
        return placeConfigured(random, origin, world, trace, 0);
    }

    private static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world, TraceSink trace, int attempt) {
        requireWorld(world);
        if (random == null || origin == null) {
            throw new IllegalArgumentException("random and origin are required");
        }
        TraceSink events = requireTrace(trace);
        Mc263DiskFeatureKernel.Result kernel = Mc263DiskFeatureKernel.place(random,
                origin.x(), origin.y(), origin.z(), 2, 6, HALF_HEIGHT, UPDATE_FLAGS, world,
                state -> {
                    int target = targetId(blockKey(state));
                    return new Mc263DiskFeatureKernel.Match(target, target < 2);
                }, (ignored, x, y, z) -> {
                    int below = providerBelowId(blockKey(world.blockState(x, y - 1, z)));
                    int result = below == 0 ? 0 : 1;
                    events.record("provider", attempt, x, y, z, below, result);
                    return result == 0 ? SANDSTONE : SAND;
                }, kernelEvents(events, attempt));
        ConfiguredResult result = new ConfiguredResult(kernel.placed(), kernel.attemptedWrites(),
                kernel.retainedWrites(), kernel.attemptedPostProcessMarks(),
                kernel.retainedPostProcessMarks());
        events.record("configured_result", attempt, kernel.placed() ? 1L : 0L,
                result.attemptedWrites(), result.retainedWrites(),
                result.attemptedPostProcessMarks(), result.retainedPostProcessMarks());
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

    private static Mc263DiskFeatureKernel.Events kernelEvents(TraceSink trace, int attempt) {
        if (!trace.enabled()) return Mc263DiskFeatureKernel.Events.disabled();
        return new Mc263DiskFeatureKernel.Events() {
            @Override public boolean enabled() { return true; }
            @Override public void radius(int radius) {
                trace.record("radius", attempt, radius);
            }
            @Override public void column(int x, int z) {
                trace.record("column", attempt, x, z);
            }
            @Override public void cell(int x, int y, int z, int target, boolean accepted,
                    int flags, boolean retained) {
                trace.record("cell", attempt, x, y, z, target, accepted ? 1L : 0L, flags,
                        retained ? 1L : 0L);
            }
            @Override public void post(int x, int y, int z, boolean retained) {
                trace.record("post", attempt, x, y, z, retained ? 1L : 0L);
            }
        };
    }

    private static boolean biomeContains(String biomeKey) {
        return Mc263FeatureIndexReceipt.biome(requireMinecraftKey(biomeKey, "biome"))
                .featuresAtStep(UNDERGROUND_ORES_STEP).stream()
                .anyMatch(reference -> reference.featureKey().equals(DISK_SAND));
    }

    /** Plains-first parity ID, then pinned receipt order with plains omitted. */
    private static int biomeTraceId(String biomeKey) {
        String biome = requireMinecraftKey(biomeKey, "biome");
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

    private static int targetId(String block) {
        return switch (block) {
            case DIRT -> 0;
            case GRASS_BLOCK -> 1;
            default -> 2;
        };
    }

    private static int providerBelowId(String block) {
        return switch (block) {
            case "minecraft:air" -> 0;
            case "minecraft:cave_air" -> 1;
            case "minecraft:void_air" -> 2;
            default -> 3;
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
        if (candidates < 0 || candidates > CANDIDATE_COUNT || successes < 0
                || successes > candidates) {
            throw new IllegalArgumentException("invalid disk-sand placement counts");
        }
        validateWriteAndMarkCounts(attemptedWrites, retainedWrites, attemptedMarks,
                retainedMarks);
    }

    private static void validateWriteAndMarkCounts(int attemptedWrites, int retainedWrites,
            int attemptedMarks, int retainedMarks) {
        if (attemptedWrites < 0 || retainedWrites < 0 || retainedWrites > attemptedWrites
                || attemptedMarks < 0 || retainedMarks < 0 || retainedMarks > attemptedMarks) {
            throw new IllegalArgumentException("invalid disk-sand write/mark counts");
        }
    }

    private record PhaseSpec(int id, int arity) {
    }

    private static final class MutableCounts {
        private int candidates;
        private int configuredSuccesses;
        private int attemptedWrites;
        private int retainedWrites;
        private int attemptedMarks;
        private int retainedMarks;

        private PlacementCounts freeze() {
            return new PlacementCounts(candidates, configuredSuccesses, attemptedWrites,
                    retainedWrites, attemptedMarks, retainedMarks);
        }
    }
}
