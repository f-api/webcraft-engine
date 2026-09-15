package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import com.gameexpert.terrain.mc.surface.McFreezeTopLayer;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Exact dormant Minecraft 26.3-snapshot-7 step-10 freeze-top-layer leaf. */
public final class Mc263FreezeTopLayerFeature {
    public static final int STEP = 10;
    public static final int GLOBAL_INDEX = 0;
    public static final int TRACE_MAGIC = 0x46525a33; // FRZ3
    public static final int TRACE_VERSION = 1;
    public static final String TRACE_SCHEMA = "mc263-freeze-top-layer-trace-v1";
    public static final String INNER_SERVER_SHA1 =
            "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String CONFIGURED_JSON_SHA256 =
            "170e0e466d150a052fa5eecc2a26a8f8e5f33dbcb0a473e4f7cfc6f7b025cb9a";
    public static final String PLACED_JSON_SHA256 =
            "bfb26c624f8d4e7f514b49e034335637be7c5cccdbcc62955ffb8fb9a12cf8fc";
    public static final String SNOW_AND_FREEZE_CLASS_SHA256 =
            "465910a09dc362398abed83caeb882654cc881ea7d0466f9ff701bf6679dcecd";

    private static final Map<String, PhaseSpec> PHASES = Map.ofEntries(
            Map.entry("column", new PhaseSpec(1, 4)),
            Map.entry("height", new PhaseSpec(2, 4)),
            Map.entry("biome", new PhaseSpec(3, 6)),
            Map.entry("light", new PhaseSpec(4, 5)),
            Map.entry("read", new PhaseSpec(5, 7)),
            Map.entry("fluid", new PhaseSpec(6, 5)),
            Map.entry("freeze", new PhaseSpec(7, 5)),
            Map.entry("snow", new PhaseSpec(8, 5)),
            Map.entry("write", new PhaseSpec(9, 7)),
            Map.entry("result", new PhaseSpec(10, 6)));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public boolean enabled() { return false; }
        @Override public void record(String phase, long... values) {
            throw new AssertionError("disabled freeze trace emitted");
        }
    };

    private Mc263FreezeTopLayerFeature() { }

    public interface WorldAccess {
        int motionBlockingHeight(int x, int z);
        BiomeClimate biomeClimate(int x, int y, int z);
        int minGenerationY();
        int generationDepth();
        int seaLevel();
        int blockLight(int x, int y, int z);
        State blockState(int x, int y, int z);
        Fluid fluidState(int x, int y, int z);
        boolean supportsState(State state);
        boolean trySetBlockState(int x, int y, int z, State state, int flags);
    }

    public enum Fluid { NONE, WATER, LAVA }

    public record BiomeClimate(float baseTemperature, boolean frozenModifier,
                               boolean precipitation) { }

    /** Only the official live facts consumed by this feature are carried. */
    public record State(String block, Map<String, String> properties, boolean air,
                        boolean liquidBlock, boolean cannotSupportSnow,
                        boolean supportOverrideSnow, boolean collisionFaceFullUp,
                        int snowLayers, boolean hasSnowyProperty) {
        public State {
            block = key(block);
            properties = Collections.unmodifiableMap(new TreeMap<>(properties));
            if (snowLayers < 0 || snowLayers > 8) {
                throw new IllegalArgumentException("snow layers out of range");
            }
        }
        public static State simple(String block) {
            return new State(block, Map.of(), false, false, false, false,
                    true, 0, false);
        }
        public String canonical() {
            if (properties.isEmpty()) return block;
            return block + properties.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .reduce("[", (a, b) -> a.equals("[") ? a + b : a + "," + b) + "]";
        }
        public State snowy() {
            if (!hasSnowyProperty) return this;
            TreeMap<String, String> next = new TreeMap<>(properties);
            next.put("snowy", "true");
            return new State(block, next, air, liquidBlock, cannotSupportSnow,
                    supportOverrideSnow, collisionFaceFullUp, snowLayers, true);
        }
    }

    public interface TraceSink {
        default boolean enabled() { return true; }
        void record(String phase, long... values);
    }
    public record TraceEvent(String phase, long[] values) {
        public TraceEvent { values = values.clone(); }
        @Override public long[] values() { return values.clone(); }
    }
    public record Result(boolean placed, int columns, int attemptedWrites,
                         int retainedWrites, int iceWrites, int snowWrites,
                         int snowyWrites) { }

    public static Result placeWithFeatureRandom(WorldgenRandom random, int originX, int originY,
                                                int originZ, WorldAccess world) {
        return placeWithFeatureRandom(random, originX, originY, originZ, world, NO_TRACE);
    }

    public static Result placeWithFeatureRandom(WorldgenRandom random, int originX, int originY,
                                                int originZ, WorldAccess world,
                                                TraceSink trace) {
        if (random == null) throw new IllegalArgumentException("random is required");
        State ice = State.simple("minecraft:ice");
        State snow = new State("minecraft:snow", Map.of("layers", "1"), false, false,
                false, false, false, 1, false);
        preflight(world, ice, snow);
        int attempted = 0, retained = 0, iceWrites = 0, snowWrites = 0, snowyWrites = 0;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = originX + dx, z = originZ + dz;
                emit(trace, "column", dx, dz, x, z);
                int y = world.motionBlockingHeight(x, z);
                int belowY = y - 1;
                emit(trace, "height", x, z, y, belowY);
                BiomeClimate biome = world.biomeClimate(x, y, z);
                emit(trace, "biome", x, y, z,
                        Float.floatToRawIntBits(biome.baseTemperature),
                        biome.frozenModifier ? 1 : 0, biome.precipitation ? 1 : 0);

                if (shouldFreeze(world, x, belowY, z, biome, trace)) {
                    boolean kept = write(world, x, belowY, z, ice, trace);
                    attempted++; iceWrites++;
                    if (kept) retained++;
                }
                if (!shouldSnow(world, x, y, z, biome, trace)) continue;
                boolean keptSnow = write(world, x, y, z, snow, trace);
                attempted++; snowWrites++;
                if (keptSnow) retained++;
                State below = read(world, x, belowY, z, trace);
                if (!below.hasSnowyProperty) continue;
                State snowy = below.snowy();
                if (!world.supportsState(snowy)) {
                    throw new UnsupportedOperationException(
                            "unregistered exact snowy state: " + snowy.canonical());
                }
                boolean keptSnowy = write(world, x, belowY, z, snowy, trace);
                attempted++; snowyWrites++;
                if (keptSnowy) retained++;
            }
        }
        emit(trace, "result", 1, 256, attempted, retained, iceWrites,
                ((long) snowWrites << 32) | Integer.toUnsignedLong(snowyWrites));
        return new Result(true, 256, attempted, retained, iceWrites, snowWrites, snowyWrites);
    }

    private static boolean shouldFreeze(WorldAccess world, int x, int y, int z,
                                        BiomeClimate biome, TraceSink trace) {
        float temperature = McFreezeTopLayer.computeTemperature(x, y, z,
                biome.baseTemperature, biome.frozenModifier, world.seaLevel());
        if (temperature >= McFreezeTopLayer.FREEZE_TEMPERATURE || !inside(world, y)) {
            emit(trace, "freeze", x, y, z, Float.floatToRawIntBits(temperature), 0);
            return false;
        }
        int light = light(world, x, y, z, trace);
        if (light >= 10) {
            emit(trace, "freeze", x, y, z, Float.floatToRawIntBits(temperature), 0);
            return false;
        }
        State state = read(world, x, y, z, trace);
        Fluid fluid = fluid(world, x, y, z, trace);
        boolean freeze = fluid == Fluid.WATER && state.liquidBlock;
        emit(trace, "freeze", x, y, z, Float.floatToRawIntBits(temperature), freeze ? 1 : 0);
        return freeze;
    }

    private static boolean shouldSnow(WorldAccess world, int x, int y, int z,
                                      BiomeClimate biome, TraceSink trace) {
        float temperature = McFreezeTopLayer.computeTemperature(x, y, z,
                biome.baseTemperature, biome.frozenModifier, world.seaLevel());
        if (!biome.precipitation || temperature >= McFreezeTopLayer.FREEZE_TEMPERATURE
                || !inside(world, y)) {
            emit(trace, "snow", x, y, z, Float.floatToRawIntBits(temperature), 0);
            return false;
        }
        if (light(world, x, y, z, trace) >= 10) {
            emit(trace, "snow", x, y, z, Float.floatToRawIntBits(temperature), 0);
            return false;
        }
        State top = read(world, x, y, z, trace);
        if (!top.air && top.snowLayers == 0) {
            emit(trace, "snow", x, y, z, Float.floatToRawIntBits(temperature), 0);
            return false;
        }
        State below = read(world, x, y - 1, z, trace);
        boolean survives = !below.cannotSupportSnow
                && (below.supportOverrideSnow || below.collisionFaceFullUp
                    || below.snowLayers == 8);
        emit(trace, "snow", x, y, z, Float.floatToRawIntBits(temperature), survives ? 1 : 0);
        return survives;
    }

    private static void preflight(WorldAccess world, State ice, State snow) {
        if (!world.supportsState(ice) || !world.supportsState(snow)) {
            throw new UnsupportedOperationException("freeze-top-layer output state unavailable");
        }
    }
    private static boolean inside(WorldAccess world, int y) {
        return y >= world.minGenerationY()
                && y < world.minGenerationY() + world.generationDepth();
    }
    private static int light(WorldAccess world, int x, int y, int z, TraceSink trace) {
        int value = world.blockLight(x, y, z);
        if (value < 0 || value > 15) throw new IllegalArgumentException("block light out of range");
        emit(trace, "light", x, y, z, value, 0);
        return value;
    }
    private static State read(WorldAccess world, int x, int y, int z, TraceSink trace) {
        State state = world.blockState(x, y, z);
        emit(trace, "read", x, y, z, hash(state.canonical()), state.air ? 1 : 0,
                state.snowLayers, state.hasSnowyProperty ? 1 : 0);
        return state;
    }
    private static Fluid fluid(WorldAccess world, int x, int y, int z, TraceSink trace) {
        Fluid fluid = world.fluidState(x, y, z);
        emit(trace, "fluid", x, y, z, fluid.ordinal(), 0);
        return fluid;
    }
    private static boolean write(WorldAccess world, int x, int y, int z, State state,
                                 TraceSink trace) {
        boolean retained = world.trySetBlockState(x, y, z, state, 2);
        emit(trace, "write", x, y, z, hash(state.canonical()), 2,
                retained ? 1 : 0, state.properties.hashCode());
        return retained;
    }

    public static byte[] encodeTraceFixture(List<TraceEvent> events) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(TRACE_MAGIC); out.writeShort(TRACE_VERSION);
            byte[] schema = TRACE_SCHEMA.getBytes(StandardCharsets.UTF_8);
            out.writeShort(schema.length); out.write(schema); out.writeInt(events.size());
            for (TraceEvent event : events) {
                PhaseSpec phase = PHASES.get(event.phase);
                if (phase == null || event.values.length != phase.arity) {
                    throw new IllegalArgumentException("bad FRZ3 event");
                }
                out.writeByte(phase.id); out.writeByte(phase.arity);
                for (long value : event.values) out.writeLong(value);
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public static List<Integer> coveredIndices() { return List.of(GLOBAL_INDEX); }
    public static String featureKey() { return "minecraft:freeze_top_layer"; }

    /** Pure exact output-state closure check for step ten. */
    public static void preflight(WorldAccess world) {
        preflight(world,
                new State("minecraft:ice", Map.of(), false, false,
                        false, false, true, 0, false),
                new State("minecraft:snow", Map.of("layers", "1"), false, false,
                        false, false, false, 1, false));
    }

    private static String key(String value) {
        return value.indexOf(':') >= 0 ? value : "minecraft:" + value;
    }
    private static long hash(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xffL; hash *= 0x100000001b3L;
        }
        return hash;
    }
    private static void emit(TraceSink trace, String phase, long... values) {
        if (!trace.enabled()) return;
        trace.record(phase, values);
    }
    private record PhaseSpec(int id, int arity) { }
}
