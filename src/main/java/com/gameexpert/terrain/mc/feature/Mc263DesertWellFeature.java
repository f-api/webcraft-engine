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

/** Exact, inactive Minecraft Java 26.3-snapshot-7 desert-well placed-feature leaf. */
public final class Mc263DesertWellFeature {
    public static final int SURFACE_STRUCTURES_STEP = 4;
    public static final int DESERT_WELL_GLOBAL_INDEX = 2;
    public static final int RARITY_CHANCE = 1_000;
    public static final String DESERT_WELL = "minecraft:desert_well";
    public static final String DESERT = "minecraft:desert";
    public static final String DESERT_WELL_ARCHAEOLOGY =
            "minecraft:archaeology/desert_well";
    public static final String SAND = "minecraft:sand";
    public static final String SANDSTONE = "minecraft:sandstone";
    public static final String WATER = "minecraft:water[level=0]";
    public static final String SANDSTONE_SLAB =
            "minecraft:sandstone_slab[type=bottom,waterlogged=false]";
    public static final String SUSPICIOUS_SAND = "minecraft:suspicious_sand[dusted=0]";

    public static void preflight(Predicate<String> supportsState,
            boolean supportsSuspiciousSandPayload) {
        Mc263LakeFeature.requireOutputs(supportsState, SANDSTONE, WATER, SAND,
                SANDSTONE_SLAB, SUSPICIOUS_SAND);
        if (!supportsSuspiciousSandPayload) {
            throw new UnsupportedOperationException("suspicious-sand payload");
        }
    }

    public static final String INNER_SERVER_SHA1 =
            "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String DESERT_WELL_FEATURE_CLASS_SHA256 =
            "31cdb885d4613ceda1766d3cf6ee0fecd3425dec181ab304882e68064ed7aacd";
    public static final String CONFIGURED_FEATURE_JSON_SHA256 =
            "7c2a5ba917517a4a95994b2cca80e8cff27eafebdeb7a7bf31d0b5942f87a02f";
    public static final String PLACED_FEATURE_JSON_SHA256 =
            "d0a29deccf1a3b2b9f9a85063535e3ef8c5185356f452879bd4d264e1da5bcf1";
    public static final String SAND_TAG_JSON_SHA256 =
            "8b019e944ba404bfbfadd0389a94e0fe907819b109cbccf577f7bb80826cc8e8";
    public static final String ARCHAEOLOGY_LOOT_JSON_SHA256 =
            "7c24722ee66cf1aa282f9bdc0dd78ca898b3f53b17b572ae64355598be8bb2ee";

    public static final String TRACE_SCHEMA = "mc263-desert-well-trace-v1";
    public static final int TRACE_BINARY_MAGIC = 0x44575033; // DWP3
    public static final int TRACE_BINARY_VERSION = 1;

    public static final int STAGE_FOUNDATION = 0;
    public static final int STAGE_WATER = 1;
    public static final int STAGE_SAND = 2;
    public static final int STAGE_RIM = 3;
    public static final int STAGE_CARDINAL_SLABS = 4;
    public static final int STAGE_ROOF = 5;
    public static final int STAGE_PILLARS = 6;
    public static final int STAGE_SUSPICIOUS_SAND = 7;

    private static final int FLAG_WORLDGEN = 2;
    private static final int FLAG_UPDATE = 3;
    private static final int[][] HORIZONTAL = {
            {0, -1}, {1, 0}, {0, 1}, {-1, 0} // N, E, S, W
    };
    private static final int[][] ARCHAEOLOGY_POSITIONS = {
            {0, 0}, {1, 0}, {0, 1}, {-1, 0}, {0, -1} // C, E, S, W, N
    };
    private static final Map<String, PhaseSpec> TRACE_PHASES = Map.ofEntries(
            Map.entry("rarity", new PhaseSpec(1, 2)),
            Map.entry("heightmap_reject", new PhaseSpec(2, 3)),
            Map.entry("candidate", new PhaseSpec(3, 3)),
            Map.entry("biome", new PhaseSpec(4, 1)),
            Map.entry("descent", new PhaseSpec(5, 3)),
            Map.entry("support", new PhaseSpec(6, 5)),
            Map.entry("write", new PhaseSpec(7, 8)),
            Map.entry("sus_pick", new PhaseSpec(8, 5)),
            Map.entry("brushable", new PhaseSpec(9, 5)),
            Map.entry("loot", new PhaseSpec(10, 5)),
            Map.entry("configured_result", new PhaseSpec(11, 6)),
            Map.entry("placed_result", new PhaseSpec(12, 8)));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override
        public void record(String phase, long... values) {
            throw new AssertionError("disabled desert-well trace emitted");
        }

        @Override
        public boolean enabled() {
            return false;
        }
    };

    private Mc263DesertWellFeature() {
    }

    /** Complete live decoration-region view plus the brushable-block semantic sidecar. */
    public interface WorldAccess {
        int minGenerationY();

        int generationDepth();

        int motionBlockingHeight(int blockX, int blockZ);

        String biomeKey(int blockX, int blockY, int blockZ);

        String blockState(int blockX, int blockY, int blockZ);

        boolean trySetBlockState(int blockX, int blockY, int blockZ, String state, int flags);

        /** True only when the accepted suspicious-sand write produced a brushable block entity. */
        boolean hasBrushableBlockEntity(int blockX, int blockY, int blockZ);

        /** Assigns the unopened archaeology loot table with the exact position-derived seed. */
        void setArchaeologyLoot(int blockX, int blockY, int blockZ, String lootTable,
                long lootSeed);
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
    }

    public record TraceEvent(String phase, long[] values) {
        public TraceEvent {
            PhaseSpec spec = TRACE_PHASES.get(phase);
            if (spec == null || values == null || values.length != spec.arity()) {
                throw new IllegalArgumentException("invalid desert-well trace event: " + phase);
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
                                  boolean rarityAccepted, int candidateCount,
                                  int configuredSuccessCount, int attemptedWriteCount,
                                  int retainedWriteCount, int retainedSuspiciousSandCount,
                                  int archaeologyLootCount) {
        public PlacementResult {
            validatePlacement(rarityAccepted, candidateCount, configuredSuccessCount,
                    attemptedWriteCount, retainedWriteCount, retainedSuspiciousSandCount,
                    archaeologyLootCount);
        }
    }

    public record PlacementCounts(boolean rarityAccepted, int candidates,
                                  int configuredSuccesses, int attemptedWrites,
                                  int retainedWrites, int retainedSuspiciousSand,
                                  int archaeologyLoot) {
        public PlacementCounts {
            validatePlacement(rarityAccepted, candidates, configuredSuccesses, attemptedWrites,
                    retainedWrites, retainedSuspiciousSand, archaeologyLoot);
        }
    }

    public record ConfiguredResult(boolean placed, int attemptedWrites, int retainedWrites,
                                   int suspiciousSandAttempts, int retainedSuspiciousSand,
                                   int archaeologyLoot) {
        public ConfiguredResult {
            if (attemptedWrites < 0 || retainedWrites < 0 || retainedWrites > attemptedWrites
                    || suspiciousSandAttempts < 0
                    || retainedSuspiciousSand < 0
                    || retainedSuspiciousSand > suspiciousSandAttempts
                    || archaeologyLoot < 0 || archaeologyLoot > retainedSuspiciousSand
                    || (!placed && (attemptedWrites != 0 || suspiciousSandAttempts != 0))) {
                throw new IllegalArgumentException("invalid desert-well configured result");
            }
        }
    }

    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world) {
        return place(worldSeed, sourceBlockX, sourceBlockZ, world, NO_TRACE);
    }

    /** Standalone wrapper deriving the exact step-4/index-2 feature stream. */
    public static PlacementResult place(long worldSeed, int sourceBlockX, int sourceBlockZ,
            WorldAccess world, TraceSink trace) {
        requireWorld(world);
        Mc263DecorationRandom.FeatureRandom seeded = Mc263DecorationRandom.forFeature(worldSeed,
                sourceBlockX, sourceBlockZ, SURFACE_STRUCTURES_STEP, DESERT_WELL);
        PlacementCounts counts = placeWithFeatureRandom(sourceBlockX, sourceBlockZ, world,
                seeded.random(), trace);
        return new PlacementResult(seeded.decorationSeed(), seeded.featureSeed(),
                seeded.globalIndex(), counts.rarityAccepted(), counts.candidates(),
                counts.configuredSuccesses(), counts.attemptedWrites(), counts.retainedWrites(),
                counts.retainedSuspiciousSand(), counts.archaeologyLoot());
    }

    /** Dispatcher entrypoint using its already-derived shared {@code WorldgenRandom}. */
    public static PlacementCounts placeWithFeatureRandom(int sourceBlockX, int sourceBlockZ,
            WorldAccess world, WorldgenRandom random, TraceSink trace) {
        requireWorld(world);
        if (random == null) throw new IllegalArgumentException("random is required");
        TraceSink events = requireTrace(trace);
        int globalIndex = Mc263DecorationRandom.globalIndex(SURFACE_STRUCTURES_STEP, DESERT_WELL);
        if (globalIndex != DESERT_WELL_GLOBAL_INDEX
                || !Mc263FeatureIndexReceipt.step(SURFACE_STRUCTURES_STEP)
                .featureAt(globalIndex).equals(DESERT_WELL)) {
            throw new IllegalStateException("pinned desert-well feature index changed");
        }

        float raritySample = random.nextFloat();
        boolean rarityAccepted = raritySample < 1.0F / RARITY_CHANCE;
        emit(events, "rarity", Float.floatToRawIntBits(raritySample),
                rarityAccepted ? 1L : 0L);
        if (!rarityAccepted) {
            emit(events, "placed_result", globalIndex, 0, 0, 0, 0, 0, 0, 0);
            return new PlacementCounts(false, 0, 0, 0, 0, 0, 0);
        }

        int x = sourceBlockX + random.nextInt(16);
        int z = sourceBlockZ + random.nextInt(16);
        int y = world.motionBlockingHeight(x, z);
        if (y <= world.minGenerationY()) {
            emit(events, "heightmap_reject", x, y, z);
            emit(events, "placed_result", globalIndex, 1, 0, 0, 0, 0, 0, 0);
            return new PlacementCounts(true, 0, 0, 0, 0, 0, 0);
        }

        emit(events, "candidate", x, y, z);
        boolean biomeAccepted = biomeContains(world.biomeKey(x, y, z));
        emit(events, "biome", biomeAccepted ? 1L : 0L);
        if (!biomeAccepted) {
            emit(events, "placed_result", globalIndex, 1, 1, 0, 0, 0, 0, 0);
            return new PlacementCounts(true, 1, 0, 0, 0, 0, 0);
        }

        ConfiguredResult configured = placeConfigured(random, new BlockPos(x, y, z), world,
                events);
        emit(events, "placed_result", globalIndex, 1, 1, configured.placed() ? 1L : 0L,
                configured.attemptedWrites(), configured.retainedWrites(),
                configured.retainedSuspiciousSand(), configured.archaeologyLoot());
        return new PlacementCounts(true, 1, configured.placed() ? 1 : 0,
                configured.attemptedWrites(), configured.retainedWrites(),
                configured.retainedSuspiciousSand(), configured.archaeologyLoot());
    }

    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world) {
        return placeConfigured(random, origin, world, NO_TRACE);
    }

    /** Executes the exact configured {@code DesertWellFeature}. */
    public static ConfiguredResult placeConfigured(WorldgenRandom random, BlockPos origin,
            WorldAccess world, TraceSink trace) {
        requireWorld(world);
        if (random == null || origin == null) {
            throw new IllegalArgumentException("random and origin are required");
        }
        TraceSink events = requireTrace(trace);
        int x = origin.x();
        int y = origin.y() + 1;
        int z = origin.z();
        int startY = y;
        while (isAir(world.blockState(x, y, z)) && y > world.minGenerationY() + 2) y--;
        boolean sandAccepted = blockKey(world.blockState(x, y, z)).equals(SAND);
        emit(events, "descent", startY, y, sandAccepted ? 1L : 0L);
        if (!sandAccepted) return configuredFailure(events);

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean belowOneAir = isAir(world.blockState(x + dx, y - 1, z + dz));
                boolean belowTwoAir = belowOneAir
                        && isAir(world.blockState(x + dx, y - 2, z + dz));
                boolean accepted = !belowOneAir || !belowTwoAir;
                emit(events, "support", dx, dz, belowOneAir ? 1L : 0L,
                        belowTwoAir ? 1L : 0L, accepted ? 1L : 0L);
                if (!accepted) return configuredFailure(events);
            }
        }

        WriteCounts counts = new WriteCounts();
        for (int dy = -2; dy <= 0; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    write(world, x + dx, y + dy, z + dz, SANDSTONE, FLAG_WORLDGEN,
                            STAGE_FOUNDATION, counts, events);
                }
            }
        }
        write(world, x, y, z, WATER, FLAG_WORLDGEN, STAGE_WATER, counts, events);
        for (int[] direction : HORIZONTAL) {
            write(world, x + direction[0], y, z + direction[1], WATER, FLAG_WORLDGEN,
                    STAGE_WATER, counts, events);
        }

        write(world, x, y - 1, z, SAND, FLAG_WORLDGEN, STAGE_SAND, counts, events);
        for (int[] direction : HORIZONTAL) {
            write(world, x + direction[0], y - 1, z + direction[1], SAND, FLAG_WORLDGEN,
                    STAGE_SAND, counts, events);
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == -2 || dx == 2 || dz == -2 || dz == 2) {
                    write(world, x + dx, y + 1, z + dz, SANDSTONE, FLAG_WORLDGEN,
                            STAGE_RIM, counts, events);
                }
            }
        }
        write(world, x + 2, y + 1, z, SANDSTONE_SLAB, FLAG_WORLDGEN,
                STAGE_CARDINAL_SLABS, counts, events);
        write(world, x - 2, y + 1, z, SANDSTONE_SLAB, FLAG_WORLDGEN,
                STAGE_CARDINAL_SLABS, counts, events);
        write(world, x, y + 1, z + 2, SANDSTONE_SLAB, FLAG_WORLDGEN,
                STAGE_CARDINAL_SLABS, counts, events);
        write(world, x, y + 1, z - 2, SANDSTONE_SLAB, FLAG_WORLDGEN,
                STAGE_CARDINAL_SLABS, counts, events);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                write(world, x + dx, y + 4, z + dz,
                        dx == 0 && dz == 0 ? SANDSTONE : SANDSTONE_SLAB,
                        FLAG_WORLDGEN, STAGE_ROOF, counts, events);
            }
        }
        for (int dy = 1; dy <= 3; dy++) {
            write(world, x - 1, y + dy, z - 1, SANDSTONE, FLAG_WORLDGEN,
                    STAGE_PILLARS, counts, events);
            write(world, x - 1, y + dy, z + 1, SANDSTONE, FLAG_WORLDGEN,
                    STAGE_PILLARS, counts, events);
            write(world, x + 1, y + dy, z - 1, SANDSTONE, FLAG_WORLDGEN,
                    STAGE_PILLARS, counts, events);
            write(world, x + 1, y + dy, z + 1, SANDSTONE, FLAG_WORLDGEN,
                    STAGE_PILLARS, counts, events);
        }

        for (int archaeologyOrdinal = 0; archaeologyOrdinal < 2; archaeologyOrdinal++) {
            int choice = random.nextInt(ARCHAEOLOGY_POSITIONS.length);
            int[] delta = ARCHAEOLOGY_POSITIONS[choice];
            int susX = x + delta[0];
            int susY = y - (archaeologyOrdinal + 1);
            int susZ = z + delta[1];
            emit(events, "sus_pick", archaeologyOrdinal, choice, susX, susY, susZ);
            counts.suspiciousAttempts++;
            boolean retained = write(world, susX, susY, susZ, SUSPICIOUS_SAND, FLAG_UPDATE,
                    STAGE_SUSPICIOUS_SAND, counts, events);
            if (!retained) continue;
            counts.retainedSuspicious++;
            boolean capable = world.hasBrushableBlockEntity(susX, susY, susZ);
            emit(events, "brushable", archaeologyOrdinal, susX, susY, susZ,
                    capable ? 1L : 0L);
            if (!capable) continue;
            long lootSeed = blockPosAsLong(susX, susY, susZ);
            world.setArchaeologyLoot(susX, susY, susZ, DESERT_WELL_ARCHAEOLOGY, lootSeed);
            counts.loot++;
            emit(events, "loot", archaeologyOrdinal, susX, susY, susZ, lootSeed);
        }

        ConfiguredResult result = new ConfiguredResult(true, counts.attempted, counts.retained,
                counts.suspiciousAttempts, counts.retainedSuspicious, counts.loot);
        emit(events, "configured_result", 1, result.attemptedWrites(), result.retainedWrites(),
                result.suspiciousSandAttempts(), result.retainedSuspiciousSand(),
                result.archaeologyLoot());
        return result;
    }

    /** Stable big-endian Java/Rust trace fixture. */
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

    /** Exact current {@code BlockPos.asLong} bit layout. */
    public static long blockPosAsLong(int x, int y, int z) {
        return ((long) x & 0x3ffffffL) << 38
                | ((long) z & 0x3ffffffL) << 12
                | ((long) y & 0xfffL);
    }

    private static ConfiguredResult configuredFailure(TraceSink trace) {
        emit(trace, "configured_result", 0, 0, 0, 0, 0, 0);
        return new ConfiguredResult(false, 0, 0, 0, 0, 0);
    }

    private static boolean write(WorldAccess world, int x, int y, int z, String state, int flags,
            int stage, WriteCounts counts, TraceSink trace) {
        int ordinal = counts.attempted++;
        boolean retained = world.trySetBlockState(x, y, z, state, flags);
        if (retained) counts.retained++;
        emit(trace, "write", stage, ordinal, x, y, z, stateId(state), flags,
                retained ? 1L : 0L);
        return retained;
    }

    private static int stateId(String state) {
        if (state.equals(SANDSTONE)) return 0;
        if (state.equals(WATER)) return 1;
        if (state.equals(SAND)) return 2;
        if (state.equals(SANDSTONE_SLAB)) return 3;
        if (state.equals(SUSPICIOUS_SAND)) return 4;
        throw new IllegalArgumentException("unknown desert-well state: " + state);
    }

    private static boolean biomeContains(String biomeKey) {
        String biome = requireMinecraftKey(biomeKey, "biome");
        return biome.equals(DESERT) && Mc263FeatureIndexReceipt.biome(biome)
                .featuresAtStep(SURFACE_STRUCTURES_STEP).stream()
                .anyMatch(reference -> reference.featureKey().equals(DESERT_WELL));
    }

    private static boolean isAir(String state) {
        String block = blockKey(state);
        return block.equals("minecraft:air") || block.equals("minecraft:cave_air")
                || block.equals("minecraft:void_air");
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

    private static void validatePlacement(boolean rarityAccepted, int candidates, int successes,
            int attempted, int retained, int retainedSuspicious, int loot) {
        if (candidates < 0 || candidates > 1 || successes < 0 || successes > candidates
                || (!rarityAccepted && candidates != 0) || attempted < 0 || retained < 0
                || retained > attempted || retainedSuspicious < 0
                || retainedSuspicious > retained || loot < 0 || loot > retainedSuspicious) {
            throw new IllegalArgumentException("invalid desert-well placement counts");
        }
    }

    private static void emit(TraceSink trace, String phase, long value0) {
        if (trace.enabled()) trace.record(phase, new long[]{value0});
    }

    private static void emit(TraceSink trace, String phase, long value0, long value1) {
        if (trace.enabled()) trace.record(phase, new long[]{value0, value1});
    }

    private static void emit(TraceSink trace, String phase, long value0, long value1,
            long value2) {
        if (trace.enabled()) trace.record(phase, new long[]{value0, value1, value2});
    }

    private static void emit(TraceSink trace, String phase, long value0, long value1,
            long value2, long value3, long value4) {
        if (trace.enabled()) {
            trace.record(phase, new long[]{value0, value1, value2, value3, value4});
        }
    }

    private static void emit(TraceSink trace, String phase, long value0, long value1,
            long value2, long value3, long value4, long value5) {
        if (trace.enabled()) {
            trace.record(phase, new long[]{value0, value1, value2, value3, value4, value5});
        }
    }

    private static void emit(TraceSink trace, String phase, long value0, long value1,
            long value2, long value3, long value4, long value5, long value6, long value7) {
        if (trace.enabled()) {
            trace.record(phase, new long[]{value0, value1, value2, value3, value4, value5,
                    value6, value7});
        }
    }

    private record PhaseSpec(int id, int arity) {
    }

    private static final class WriteCounts {
        private int attempted;
        private int retained;
        private int suspiciousAttempts;
        private int retainedSuspicious;
        private int loot;
    }
}
