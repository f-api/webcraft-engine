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

/** Exact, inactive Minecraft Java 26.3-snapshot-7 spring placed-feature family. */
public final class Mc263SpringFeature {
    public static final int FLUID_SPRINGS_STEP = 8;
    public static final int UPDATE_FLAGS = 2;
    public static final int FLUID_TICK_DELAY = 0;
    public static final int MIN_Y = -64;
    public static final int WATER_MAX_Y = 192;
    public static final int BIASED_MAX_Y = 311;
    public static final int BIASED_INNER = 8;
    public static final String WATER_STATE = "minecraft:water";
    public static final String LAVA_STATE = "minecraft:lava";
    public static final String WATER_FLUID = "minecraft:water";
    public static final String LAVA_FLUID = "minecraft:lava";

    public static void preflight(Source source, Predicate<String> supportsState,
            boolean supportsTicks) {
        Mc263LakeFeature.requireOutputs(supportsState, source.blockState());
        if (!supportsTicks) throw new UnsupportedOperationException("spring fluid ticks");
    }

    public static final String INNER_SERVER_SHA1 =
            "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String FEATURE_CLASS_SHA256 =
            "0b14cb7e1b2dcd7a837a545739cdbf5825e1d952cf53a6442f647608bacf9bba";
    public static final String WATER_CONFIGURED_JSON_SHA256 =
            "566e6926daf118b1728b9c92dc471eb6ee7b2778bdf7816d2266feec89117493";
    public static final String LAVA_CONFIGURED_JSON_SHA256 =
            "9df90209c4999362dd4b8a6d1a4a32e89b7a93f1ec7cfc52397951ec355bf01a";
    public static final String FROZEN_CONFIGURED_JSON_SHA256 =
            "96368b06bd6eb3d57e131b6fdadd48a261f01cef9fd43bb7ac8e9ed47b333c22";
    public static final String WATER_PLACED_JSON_SHA256 =
            "a469d9576738d670ed1a60a7ad71bf1019cc858ac4ee9c4be89189270c5304a3";
    public static final String LAVA_PLACED_JSON_SHA256 =
            "5a70f5f9c9da20c291f65dab635e5ee51aa3a81798f6fe80b94f702640c40a5b";
    public static final String FROZEN_PLACED_JSON_SHA256 =
            "f0da6b8df798aab8ca2b37f224dbfd55d82c1070078b06294ee7dd20b9917a15";

    public static final String TRACE_SCHEMA = "mc263-spring-trace-v1";
    public static final int TRACE_MAGIC = 0x53505233; // SPR3
    public static final int TRACE_VERSION = 1;
    private static final Map<String, PhaseSpec> PHASES = Map.of(
            "candidate", new PhaseSpec(1, 5),
            "biome", new PhaseSpec(2, 3),
            "valid_probe", new PhaseSpec(3, 8),
            "empty_probe", new PhaseSpec(4, 7),
            "write", new PhaseSpec(5, 8),
            "tick", new PhaseSpec(6, 7),
            "configured_result", new PhaseSpec(7, 6),
            "placed_result", new PhaseSpec(8, 7));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public void record(String phase, long... values) {
            throw new AssertionError("disabled spring trace emitted");
        }
        @Override public boolean enabled() { return false; }
    };

    private Mc263SpringFeature() {
    }

    public enum Source {
        WATER("minecraft:spring_water", 0, 25, WATER_STATE, WATER_FLUID),
        LAVA("minecraft:spring_lava", 1, 20, LAVA_STATE, LAVA_FLUID),
        FROZEN("minecraft:spring_lava_frozen", 2, 20, LAVA_STATE, LAVA_FLUID);

        private final String featureKey;
        private final int globalIndex;
        private final int count;
        private final String blockState;
        private final String fluid;

        Source(String featureKey, int globalIndex, int count, String blockState, String fluid) {
            this.featureKey = featureKey;
            this.globalIndex = globalIndex;
            this.count = count;
            this.blockState = blockState;
            this.fluid = fluid;
        }

        public String featureKey() { return featureKey; }
        public int globalIndex() { return globalIndex; }
        public int count() { return count; }
        public String blockState() { return blockState; }
        public String fluid() { return fluid; }
    }

    /** Every read is a fresh FEATURES-region query; no state may be cached by the leaf. */
    public interface WorldAccess {
        String biomeKey(int blockX, int blockY, int blockZ);

        String blockState(int blockX, int blockY, int blockZ);

        boolean isEmptyBlock(int blockX, int blockY, int blockZ);

        /** Executes flag-2 setBlock. The return value is retention, not official success. */
        boolean trySetBlockState(int blockX, int blockY, int blockZ, String state, int flags);

        void scheduleFluidTick(int blockX, int blockY, int blockZ, String fluid, int delay);
    }

    @FunctionalInterface
    public interface TraceSink {
        void record(String phase, long... values);
        default boolean enabled() { return true; }
        static TraceSink disabled() { return NO_TRACE; }
        default void record(String phase, long a, long b, long c) {
            if (enabled()) record(phase, new long[]{a, b, c});
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
            PhaseSpec spec = PHASES.get(phase);
            if (spec == null || values == null || values.length != spec.arity()) {
                throw new IllegalArgumentException("invalid spring trace event: " + phase);
            }
            values = values.clone();
        }
        @Override public long[] values() { return values.clone(); }
    }

    public record BlockPos(int x, int y, int z) {
        public BlockPos offset(int dx, int dy, int dz) {
            return new BlockPos(x + dx, y + dy, z + dz);
        }
    }

    public record ConfiguredResult(boolean placed, int rockCount, int holeCount,
                                   int officialWriteCount, int retainedWriteCount,
                                   int scheduledTicks) {
        public ConfiguredResult {
            if (rockCount < 0 || holeCount < 0 || officialWriteCount < 0
                    || retainedWriteCount < 0 || retainedWriteCount > officialWriteCount
                    || scheduledTicks < 0 || scheduledTicks != officialWriteCount
                    || placed != (officialWriteCount > 0)) {
                throw new IllegalArgumentException("invalid spring configured result");
            }
        }
    }

    public record PlacementCounts(int generatedCount, int biomeCandidates,
                                  int configuredSuccesses, int officialWrites,
                                  int retainedWrites, int scheduledTicks) {
        public PlacementCounts {
            if (generatedCount < 0 || biomeCandidates < 0 || biomeCandidates > generatedCount
                    || configuredSuccesses < 0 || configuredSuccesses > biomeCandidates
                    || officialWrites < 0 || retainedWrites < 0
                    || retainedWrites > officialWrites || scheduledTicks != officialWrites) {
                throw new IllegalArgumentException("invalid spring placement counts");
            }
        }
    }

    public record PlacementResult(long decorationSeed, long featureSeed, int globalIndex,
                                  PlacementCounts counts) {
    }

    public static PlacementResult place(Source source, long worldSeed, int sourceBlockX,
            int sourceBlockZ, WorldAccess world) {
        return place(source, worldSeed, sourceBlockX, sourceBlockZ, world, NO_TRACE);
    }

    public static PlacementResult place(Source source, long worldSeed, int sourceBlockX,
            int sourceBlockZ, WorldAccess world, TraceSink trace) {
        requireSource(source);
        requireWorld(world);
        Mc263DecorationRandom.FeatureRandom seeded = Mc263DecorationRandom.forFeature(worldSeed,
                sourceBlockX, sourceBlockZ, FLUID_SPRINGS_STEP, source.featureKey());
        PlacementCounts counts = placeWithFeatureRandom(source, sourceBlockX, sourceBlockZ,
                world, seeded.random(), requireTrace(trace));
        return new PlacementResult(seeded.decorationSeed(), seeded.featureSeed(),
                seeded.globalIndex(), counts);
    }

    /** Supplied-stream entrypoint used by the shared decoration-stage random lifecycle. */
    public static PlacementCounts placeWithFeatureRandom(Source source, int sourceBlockX,
            int sourceBlockZ, WorldAccess world, WorldgenRandom random, TraceSink trace) {
        requireSource(source);
        requireWorld(world);
        if (random == null) throw new IllegalArgumentException("random is required");
        TraceSink events = requireTrace(trace);
        verifyIndex(source);

        int candidates = 0;
        int successes = 0;
        int writes = 0;
        int retained = 0;
        int ticks = 0;
        for (int attempt = 0; attempt < source.count(); attempt++) {
            int x = sourceBlockX + random.nextInt(16);
            int z = sourceBlockZ + random.nextInt(16);
            int y = source == Source.WATER ? MIN_Y + random.nextInt(WATER_MAX_Y - MIN_Y + 1)
                    : veryBiasedToBottom(random);
            events.record("candidate", source.ordinal(), attempt, x, y, z);

            String biome = requireKey(world.biomeKey(x, y, z), "biome");
            boolean accepted = biomeContains(source, biome);
            events.record("biome", source.ordinal(), attempt, accepted ? 1L : 0L);
            if (!accepted) continue;
            candidates++;
            ConfiguredResult result = placeConfigured(source, new BlockPos(x, y, z), world,
                    events, attempt);
            if (result.placed()) successes++;
            writes += result.officialWriteCount();
            retained += result.retainedWriteCount();
            ticks += result.scheduledTicks();
        }
        events.record("placed_result", source.ordinal(), source.count(), candidates, successes,
                writes, retained, ticks);
        return new PlacementCounts(source.count(), candidates, successes, writes, retained, ticks);
    }

    public static ConfiguredResult placeConfigured(Source source, BlockPos origin,
            WorldAccess world) {
        return placeConfigured(source, origin, world, NO_TRACE, 0);
    }

    public static ConfiguredResult placeConfigured(Source source, BlockPos origin,
            WorldAccess world, TraceSink trace) {
        return placeConfigured(source, origin, world, requireTrace(trace), 0);
    }

    private static ConfiguredResult placeConfigured(Source source, BlockPos origin,
            WorldAccess world, TraceSink trace, int attempt) {
        requireSource(source);
        requireWorld(world);
        if (origin == null) throw new IllegalArgumentException("origin is required");

        if (!validProbe(source, world, origin.offset(0, 1, 0), 0, attempt, trace)) {
            return configuredResult(source, attempt, 0, 0, false, false, trace);
        }
        if (!validProbe(source, world, origin.offset(0, -1, 0), 1, attempt, trace)) {
            return configuredResult(source, attempt, 0, 0, false, false, trace);
        }
        String originState = state(world, origin);
        boolean originAccepted = isAir(originState) || validBlock(source, originState);
        trace.record("valid_probe", source.ordinal(), attempt, 2, origin.x(), origin.y(),
                origin.z(), stateId(originState), originAccepted ? 1L : 0L);
        if (!originAccepted) {
            return configuredResult(source, attempt, 0, 0, false, false, trace);
        }

        BlockPos[] neighbors = {
                origin.offset(-1, 0, 0), origin.offset(1, 0, 0),
                origin.offset(0, 0, -1), origin.offset(0, 0, 1),
                origin.offset(0, -1, 0)
        };
        int rocks = 0;
        for (int i = 0; i < neighbors.length; i++) {
            if (validProbe(source, world, neighbors[i], i + 3, attempt, trace)) rocks++;
        }
        int holes = 0;
        for (int i = 0; i < neighbors.length; i++) {
            BlockPos pos = neighbors[i];
            boolean empty = world.isEmptyBlock(pos.x(), pos.y(), pos.z());
            trace.record("empty_probe", source.ordinal(), attempt, i + 3, pos.x(), pos.y(),
                    pos.z(), empty ? 1L : 0L);
            if (empty) holes++;
        }

        if (rocks != 4 || holes != 1) {
            return configuredResult(source, attempt, rocks, holes, false, false, trace);
        }
        boolean kept = world.trySetBlockState(origin.x(), origin.y(), origin.z(),
                source.blockState(), UPDATE_FLAGS);
        trace.record("write", source.ordinal(), attempt, origin.x(), origin.y(), origin.z(),
                stateId(source.blockState()), UPDATE_FLAGS, kept ? 1L : 0L);
        world.scheduleFluidTick(origin.x(), origin.y(), origin.z(), source.fluid(),
                FLUID_TICK_DELAY);
        trace.record("tick", source.ordinal(), attempt, origin.x(), origin.y(), origin.z(),
                source == Source.WATER ? 0L : 1L, FLUID_TICK_DELAY);
        return configuredResult(source, attempt, rocks, holes, true, kept, trace);
    }

    private static ConfiguredResult configuredResult(Source source, int attempt, int rocks,
            int holes, boolean placed, boolean retained, TraceSink trace) {
        trace.record("configured_result", source.ordinal(), attempt, rocks, holes,
                placed ? 1L : 0L, retained ? 1L : 0L);
        return new ConfiguredResult(placed, rocks, holes, placed ? 1 : 0,
                retained ? 1 : 0, placed ? 1 : 0);
    }

    private static boolean validProbe(Source source, WorldAccess world, BlockPos pos, int probe,
            int attempt, TraceSink trace) {
        String blockState = state(world, pos);
        boolean valid = validBlock(source, blockState);
        trace.record("valid_probe", source.ordinal(), attempt, probe, pos.x(), pos.y(), pos.z(),
                stateId(blockState), valid ? 1L : 0L);
        return valid;
    }

    private static int veryBiasedToBottom(WorldgenRandom random) {
        int upper = nextIntInclusive(random, MIN_Y + BIASED_INNER, BIASED_MAX_Y);
        int middle = nextIntInclusive(random, MIN_Y, upper - 1);
        return nextIntInclusive(random, MIN_Y, middle - 1 + BIASED_INNER);
    }

    private static int nextIntInclusive(WorldgenRandom random, int minimum, int maximum) {
        return minimum + random.nextInt(maximum - minimum + 1);
    }

    private static boolean validBlock(Source source, String exactState) {
        String block = blockKey(exactState);
        if (source == Source.FROZEN) {
            return block.equals("minecraft:snow_block")
                    || block.equals("minecraft:powder_snow")
                    || block.equals("minecraft:packed_ice");
        }
        return block.equals("minecraft:stone") || block.equals("minecraft:granite")
                || block.equals("minecraft:diorite") || block.equals("minecraft:andesite")
                || block.equals("minecraft:deepslate") || block.equals("minecraft:tuff")
                || block.equals("minecraft:calcite") || block.equals("minecraft:dirt")
                || source == Source.WATER && (block.equals("minecraft:snow_block")
                || block.equals("minecraft:powder_snow")
                || block.equals("minecraft:packed_ice"));
    }

    private static boolean biomeContains(Source source, String biome) {
        for (Mc263FeatureIndexReceipt.BiomeFeatureData candidate
                : Mc263FeatureIndexReceipt.biomes()) {
            if (!candidate.biomeKey().equals(biome)) continue;
            return candidate.featuresAtStep(FLUID_SPRINGS_STEP).stream()
                    .anyMatch(reference -> reference.featureKey().equals(source.featureKey())
                            && reference.globalIndex() == source.globalIndex());
        }
        return false;
    }

    private static String state(WorldAccess world, BlockPos pos) {
        return requireKey(world.blockState(pos.x(), pos.y(), pos.z()), "block state");
    }

    private static boolean isAir(String state) {
        String block = blockKey(state);
        return block.equals("minecraft:air") || block.equals("minecraft:cave_air")
                || block.equals("minecraft:void_air");
    }

    private static String blockKey(String state) {
        String exact = requireKey(state, "block state");
        int properties = exact.indexOf('[');
        return properties < 0 ? exact : exact.substring(0, properties);
    }

    private static int stateId(String state) {
        if (state.equals(WATER_STATE)) return 14;
        if (state.equals(LAVA_STATE)) return 15;
        return switch (blockKey(state)) {
            case "minecraft:air" -> 0;
            case "minecraft:cave_air" -> 1;
            case "minecraft:void_air" -> 2;
            case "minecraft:stone" -> 3;
            case "minecraft:granite" -> 4;
            case "minecraft:diorite" -> 5;
            case "minecraft:andesite" -> 6;
            case "minecraft:deepslate" -> 7;
            case "minecraft:tuff" -> 8;
            case "minecraft:calcite" -> 9;
            case "minecraft:dirt" -> 10;
            case "minecraft:snow_block" -> 11;
            case "minecraft:powder_snow" -> 12;
            case "minecraft:packed_ice" -> 13;
            default -> 16;
        };
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

    private static void verifyIndex(Source source) {
        if (Mc263DecorationRandom.globalIndex(FLUID_SPRINGS_STEP, source.featureKey())
                != source.globalIndex()
                || !Mc263FeatureIndexReceipt.step(FLUID_SPRINGS_STEP)
                .featureAt(source.globalIndex()).equals(source.featureKey())) {
            throw new IllegalStateException("pinned spring feature index changed: " + source);
        }
    }

    private static String requireKey(String key, String description) {
        if (key == null || !key.startsWith("minecraft:")
                || key.length() == "minecraft:".length()) {
            throw new IllegalArgumentException("exact minecraft namespaced " + description
                    + " is required: " + key);
        }
        return key;
    }

    private static Source requireSource(Source source) {
        if (source == null) throw new IllegalArgumentException("spring source is required");
        return source;
    }

    private static WorldAccess requireWorld(WorldAccess world) {
        if (world == null) throw new IllegalArgumentException("world is required");
        return world;
    }

    private static TraceSink requireTrace(TraceSink trace) {
        if (trace == null) throw new IllegalArgumentException("trace is required");
        return trace;
    }

    private record PhaseSpec(int id, int arity) {
    }
}
