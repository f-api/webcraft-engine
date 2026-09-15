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
import java.util.Set;

/**
 * Exact, inactive Minecraft Java 26.3-snapshot-7 ice-patch placed-feature leaf.
 *
 * <p>The supplied world is a live FEATURES decoration-region view. Heightmap reads therefore see
 * earlier feature writes, while writes and post-processing marks may be rejected only at the real
 * decoration-region boundary. No canonical generator calls this class until full-pipeline
 * promotion is reviewed.</p>
 */
public final class Mc263IcePatchFeature {
    public static final int SURFACE_STRUCTURES_STEP = 4;
    public static final int ICE_PATCH_GLOBAL_INDEX = 1;
    public static final int CANDIDATE_COUNT = 2;
    public static final int UPDATE_FLAGS = 2;
    public static final int HALF_HEIGHT = 1;
    public static final String ICE_PATCH = "minecraft:ice_patch";
    public static final String PACKED_ICE = "minecraft:packed_ice";
    public static final String SNOW_BLOCK = "minecraft:snow_block";
    public static final String ICE_SPIKES = "minecraft:ice_spikes";

    public static void preflight(Predicate<String> supportsState) {
        Mc263LakeFeature.requireOutputs(supportsState, PACKED_ICE);
    }

    public static final String INNER_SERVER_SHA1 =
            "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String DISK_FEATURE_CLASS_SHA256 =
            "2ac1d35ac09709e8e84476fc4ff317ffb7622584ae59b5d4e5ee017519405e51";
    public static final String FEATURE_CLASS_SHA256 =
            "b140a544634d1bc33ccf2cacf00fe0b260652ec52f72ca243f259d3335d43665";
    public static final String CONFIGURED_FEATURE_JSON_SHA256 =
            "bf80cb774a99b01c686ee80f635b8007a5f633f7c60b074eb59e60573b67fc10";
    public static final String PLACED_FEATURE_JSON_SHA256 =
            "7d64e6ad5feac484d108487edbc8a04e752c96b0a3f80d2a9aad78076be9fc72";

    public static final String TRACE_SCHEMA = "mc263-ice-patch-trace-v1";
    public static final int TRACE_BINARY_MAGIC = 0x49435033; // ICP3
    public static final int TRACE_BINARY_VERSION = 1;

    private static final Set<String> TARGETS = Set.of(
            "minecraft:dirt",
            "minecraft:grass_block",
            "minecraft:podzol",
            "minecraft:coarse_dirt",
            "minecraft:mycelium",
            SNOW_BLOCK,
            "minecraft:ice");
    private static final Map<String, PhaseSpec> TRACE_PHASES = Map.ofEntries(
            Map.entry("count", new PhaseSpec(1, 1)),
            Map.entry("heightmap_reject", new PhaseSpec(2, 4)),
            Map.entry("candidate", new PhaseSpec(3, 4)),
            Map.entry("snow", new PhaseSpec(4, 2)),
            Map.entry("biome", new PhaseSpec(5, 2)),
            Map.entry("radius", new PhaseSpec(6, 1)),
            Map.entry("column", new PhaseSpec(7, 2)),
            Map.entry("cell", new PhaseSpec(8, 7)),
            Map.entry("post", new PhaseSpec(9, 4)),
            Map.entry("configured_result", new PhaseSpec(10, 5)),
            Map.entry("placed_result", new PhaseSpec(11, 7)));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override
        public void record(String phase, long... values) {
            throw new AssertionError("disabled ice-patch trace emitted");
        }

        @Override
        public boolean enabled() {
            return false;
        }
    };

    private Mc263IcePatchFeature() {
    }

    /** Complete live view used by the placed modifiers and configured disk feature. */
    public interface WorldAccess extends Mc263DiskFeatureKernel.WorldAccess {
        int minGenerationY();

        int generationDepth();

        /** Exact live {@code Heightmap.Types.MOTION_BLOCKING} height for this column. */
        int motionBlockingHeight(int blockX, int blockZ);

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
                throw new IllegalArgumentException("invalid ice-patch trace event: " + phase);
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

    /** Runs the independently seeded step-4/index-1 placed feature. */
    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world) {
        return place(worldSeed, sourceBlockX, sourceBlockZ, world, NO_TRACE);
    }

    /** Runs the independently seeded step-4/index-1 placed feature. */
    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world, TraceSink trace) {
        requireWorld(world);
        TraceSink events = requireTrace(trace);
        Mc263DecorationRandom.FeatureRandom seeded = Mc263DecorationRandom.forFeature(worldSeed,
                sourceBlockX, sourceBlockZ, SURFACE_STRUCTURES_STEP, ICE_PATCH);
        PlacementCounts counts = placeWithFeatureRandom(sourceBlockX, sourceBlockZ, world,
                seeded.random(), events);
        return new PlacementResult(seeded.decorationSeed(), seeded.featureSeed(),
                seeded.globalIndex(), counts.candidates(), counts.configuredSuccesses(),
                counts.attemptedWrites(), counts.retainedWrites(),
                counts.attemptedPostProcessMarks(), counts.retainedPostProcessMarks());
    }

    /** Executes count, in-square, live heightmap, offset, predicate, biome, and disk stages. */
    public static PlacementCounts placeWithFeatureRandom(int sourceBlockX, int sourceBlockZ,
            WorldAccess world, WorldgenRandom random, TraceSink trace) {
        requireWorld(world);
        if (random == null) throw new IllegalArgumentException("random is required");
        TraceSink events = requireTrace(trace);
        int globalIndex = Mc263DecorationRandom.globalIndex(SURFACE_STRUCTURES_STEP, ICE_PATCH);
        if (globalIndex != ICE_PATCH_GLOBAL_INDEX
                || !Mc263FeatureIndexReceipt.step(SURFACE_STRUCTURES_STEP)
                .featureAt(globalIndex).equals(ICE_PATCH)) {
            throw new IllegalStateException("pinned ice-patch feature index changed");
        }

        // CountPlacement.of(ConstantInt.of(2)) samples without consuming random bits.
        events.record("count", CANDIDATE_COUNT);
        int candidates = 0;
        int successes = 0;
        MutableCounts totals = new MutableCounts();
        for (int attempt = 0; attempt < CANDIDATE_COUNT; attempt++) {
            int x = sourceBlockX + random.nextInt(16);
            int z = sourceBlockZ + random.nextInt(16);
            int height = world.motionBlockingHeight(x, z);
            if (height <= world.minGenerationY()) {
                events.record("heightmap_reject", attempt, x, height, z);
                continue;
            }

            candidates++;
            BlockPos origin = new BlockPos(x, height - 1, z);
            events.record("candidate", attempt, origin.x(), origin.y(), origin.z());
            boolean snowAccepted = blockKey(world.blockState(origin.x(), origin.y(), origin.z()))
                    .equals(SNOW_BLOCK);
            events.record("snow", attempt, snowAccepted ? 1L : 0L);
            if (!snowAccepted) continue;

            boolean biomeAccepted = biomeContains(
                    world.biomeKey(origin.x(), origin.y(), origin.z()));
            events.record("biome", attempt, biomeAccepted ? 1L : 0L);
            if (!biomeAccepted) continue;

            ConfiguredResult configured = placeConfigured(random, origin, world, events);
            if (configured.placed()) successes++;
            totals.add(configured);
        }
        events.record("placed_result", ICE_PATCH_GLOBAL_INDEX, candidates, successes,
                totals.attemptedWrites, totals.retainedWrites, totals.attemptedMarks,
                totals.retainedMarks);
        return new PlacementCounts(candidates, successes, totals.attemptedWrites,
                totals.retainedWrites, totals.attemptedMarks, totals.retainedMarks);
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
                    String block = blockKey(state);
                    return new Mc263DiskFeatureKernel.Match(targetId(block),
                            TARGETS.contains(block));
                }, (ignored, x, y, z) -> PACKED_ICE, kernelEvents(events));
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
        if (!biome.equals(ICE_SPIKES)) return false;
        return Mc263FeatureIndexReceipt.biome(biome)
                .featuresAtStep(SURFACE_STRUCTURES_STEP).stream()
                .anyMatch(reference -> reference.featureKey().equals(ICE_PATCH));
    }

    private static int targetId(String block) {
        return switch (block) {
            case "minecraft:dirt" -> 0;
            case "minecraft:grass_block" -> 1;
            case "minecraft:podzol" -> 2;
            case "minecraft:coarse_dirt" -> 3;
            case "minecraft:mycelium" -> 4;
            case SNOW_BLOCK -> 5;
            case "minecraft:ice" -> 6;
            default -> 7;
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
        String key = requireMinecraftKey(state, "block state");
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
            throw new IllegalArgumentException("invalid ice-patch placement counts");
        }
        validateWriteAndMarkCounts(attemptedWrites, retainedWrites, attemptedMarks,
                retainedMarks);
    }

    private static void validateWriteAndMarkCounts(int attemptedWrites, int retainedWrites,
            int attemptedMarks, int retainedMarks) {
        if (attemptedWrites < 0 || retainedWrites < 0 || retainedWrites > attemptedWrites
                || attemptedMarks < 0 || retainedMarks < 0 || retainedMarks > attemptedMarks) {
            throw new IllegalArgumentException("invalid ice-patch write/mark counts");
        }
    }

    private record PhaseSpec(int id, int arity) {
    }

    private static final class MutableCounts {
        private int attemptedWrites;
        private int retainedWrites;
        private int attemptedMarks;
        private int retainedMarks;

        private void add(ConfiguredResult result) {
            attemptedWrites += result.attemptedWrites();
            retainedWrites += result.retainedWrites();
            attemptedMarks += result.attemptedPostProcessMarks();
            retainedMarks += result.retainedPostProcessMarks();
        }
    }
}
