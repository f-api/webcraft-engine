package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263WorldgenRandomSource;
import com.gameexpert.terrain.mc.McNormalNoise;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Exact dormant 26.3-snapshot-7 step-9 SimpleBlock/constant-or-weighted vegetation family. */
public final class Mc263SimpleVegetationFeature {
    /** The pinned namespaced-key grammar, compiled once: {@code String.matches} recompiles
     * this pattern on every call, and the key validator runs once per read state. */
    private static final Pattern RESOURCE_KEY =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public static final int STEP = 9;
    public static final int TRACE_MAGIC = 0x53564733; // SVG3
    public static final int TRACE_VERSION = 2;
    public static final String TRACE_SCHEMA = "mc263-simple-vegetation-trace-v2";
    public static final String INNER_SERVER_SHA1 = "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String SIMPLE_BLOCK_CLASS_SHA256 = "10745682a08452d5e5f148cfd627fa1ec84bf669570da5cd284ee2119e810101";
    public static final String WEIGHTED_PROVIDER_CLASS_SHA256 = "74541d854c0b3e830482c5203b6b1248fa84880e0ba61fda0f3b30637a1a45a6";
    public static final String SIMPLE_PROVIDER_CLASS_SHA256 = "72deffe747b0324a88691eeabce09e324a37dbc23a8b1136c0a7763d25c44e08";
    public static final String PLACED_FEATURE_CLASS_SHA256 = "61908c74cb40f03052036d9d75ef85deb9d669206de1ccd90fcf703f2df98546";
    public static final String FEATURE_PLACER_CLASS_SHA256 = "1d3e9c087bf63463503ba2fb55893053a3e0da6cd38fd3d6302afb02d9be437f";
    public static final String SOURCE_MANIFEST_SHA256 = "f21a928c8b963dc56d879e9039483a6affccea4134ec1ff087dafb609624d380";
    public static final String BLOCK_STATE_PROVIDER_CLASS_SHA256 = "de257f0261f6bac9e6f5715a27c9e7659408dea89935c83d381e05fbaafdf529";
    public static final String WEIGHTED_LIST_CLASS_SHA256 = "76702ad9ac458345d9a24f86ce70384dd7668ebb95199d1e63a33c72c80128eb";
    public static final String TRAPEZOID_INT_CLASS_SHA256 = "2d1c9d123cab73f597ed319c4112e26af0d66e67725380b2a072ccbf1610b99e";
    public static final String NOISE_THRESHOLD_COUNT_CLASS_SHA256 = "fda66ce7a357e1f5c61d6a3efed653beb5bfab5713fe9e4cd4fa0bb8c3f1d483";

    public static final int[] COVERED_INDICES = {6, 7, 11, 13, 14, 15, 18, 19, 23, 25, 29, 37, 38,
            39, 41, 43, 52, 54, 56, 57, 58, 60, 61, 62, 63, 64, 65, 66, 67, 69, 70, 71, 72,
            73, 74, 75, 76, 77, 80, 83, 84, 85, 88, 95, 96, 97, 98};

    private static final Map<Integer, Spec> SPECS = specs();
    private static final Map<String, PhaseSpec> PHASES = Map.ofEntries(
            Map.entry("outer", new PhaseSpec(1, 4)), Map.entry("rng_float", new PhaseSpec(2, 2)),
            Map.entry("rng_int", new PhaseSpec(3, 3)), Map.entry("square", new PhaseSpec(4, 5)),
            Map.entry("height", new PhaseSpec(5, 5)), Map.entry("biome", new PhaseSpec(6, 5)),
            Map.entry("offset", new PhaseSpec(7, 7)), Map.entry("read", new PhaseSpec(8, 5)),
            Map.entry("predicate", new PhaseSpec(9, 5)), Map.entry("provider", new PhaseSpec(10, 5)),
            Map.entry("survive", new PhaseSpec(11, 5)), Map.entry("write", new PhaseSpec(12, 7)),
            Map.entry("schedule", new PhaseSpec(13, 5)), Map.entry("configured_result", new PhaseSpec(14, 5)),
            Map.entry("placed_result", new PhaseSpec(15, 7)),
            Map.entry("fluid", new PhaseSpec(16, 4)),
            Map.entry("provider_noise", new PhaseSpec(17, 5)));
    private static final McNormalNoise FLOWER_FOREST_NOISE =
            McNormalNoise.legacyParity(2345L, 0, new double[]{1.0D});
    private static final McNormalNoise MEADOW_FAST_NOISE =
            McNormalNoise.legacyParity(2345L, -3, new double[]{1.0D});
    private static final McNormalNoise MEADOW_SLOW_NOISE =
            McNormalNoise.legacyParity(2345L, -10, new double[]{1.0D});
    private static final McNormalNoise PLAINS_NOISE =
            McNormalNoise.legacyParity(2345L, 0, new double[]{1.0D});
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public boolean enabled() { return false; }
        @Override public void record(String phase, long... values) {
            throw new AssertionError("disabled simple-vegetation trace emitted");
        }
    };

    private Mc263SimpleVegetationFeature() { }

    public enum Heightmap { WORLD_SURFACE_WG, MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES }

    /** Minimal semantic world boundary; the later adapter owns exact block survival rules. */
    public interface WorldAccess {
        int minGenerationY();
        int height(Heightmap heightmap, int x, int z);
        String biomeKey(int x, int y, int z);
        double flowerNoise(int x, int z);
        State blockState(int x, int y, int z);
        String fluidState(int x, int y, int z);
        boolean canSurvive(State state, int x, int y, int z);
        boolean supportsFeature(int globalIndex);
        boolean supportsState(State state);
        boolean trySetBlockState(int x, int y, int z, State state, int flags);
        void scheduleTick(int x, int y, int z, String block, int delay);
    }

    /** Canonical state plus only the live predicate facts consumed by this family. */
    public record State(String block, Map<String, String> properties, String fluid,
                        boolean airTag, boolean replaceable) {
        public State {
            block = key(block);
            fluid = key(fluid);
            properties = Collections.unmodifiableMap(new TreeMap<>(properties));
        }
        public static State of(String block) {
            return new State(block, Map.of(), "minecraft:empty", false, false);
        }
        public static State air() {
            return new State("minecraft:air", Map.of(), "minecraft:empty", true, true);
        }
        public State property(String name, String value) {
            Map<String, String> next = new TreeMap<>(properties);
            next.put(name, value);
            return new State(block, next, fluid, airTag, replaceable);
        }
        public String canonical() {
            if (properties.isEmpty()) return block;
            return block + properties.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                    .reduce("[", (a, b) -> a.equals("[") ? a + b : a + "," + b) + "]";
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
    public record Result(boolean placed, int attempts, int successes,
                         int attemptedWrites, int retainedWrites, int scheduledTicks) { }

    public static Result placeWithFeatureRandom(int index, Mc263WorldgenRandomSource random,
                                                int sourceX, int sourceY, int sourceZ,
                                                WorldAccess world) {
        return placeWithFeatureRandom(index, random, sourceX, sourceY, sourceZ, world, NO_TRACE);
    }

    public static Result placeWithFeatureRandom(int index, Mc263WorldgenRandomSource random,
                                                int sourceX, int sourceY, int sourceZ,
                                                WorldAccess world, TraceSink trace) {
        Spec spec = requireSpec(index);
        requireSupported(spec, world);
        Counters out = new Counters();
        int outer = spec.noiseCount ? (world.flowerNoise(sourceX, sourceZ) < -0.8
                ? spec.noiseBelow : spec.noiseAbove)
                : spec.outerCount;
        emit(trace, "outer", index, outer, spec.rarity, spec.noiseCount ? 1 : 0);
        for (int i = 0; i < outer; i++) {
            if (spec.rarity > 1 && !rarity(random, spec.rarity, trace)) continue;
            int x = sourceX + nextInt(random, 16, trace);
            int z = sourceZ + nextInt(random, 16, trace);
            emit(trace, "square", index, i, x, sourceY, z);
            int y = world.height(spec.heightmap, x, z);
            emit(trace, "height", index, spec.heightmap.ordinal(), x, z, y);
            if (y <= world.minGenerationY()) continue;
            String biome = world.biomeKey(x, y, z);
            boolean member = biomeContains(index, spec.key, biome);
            emit(trace, "biome", index, x, y, z, member ? 1 : 0);
            if (!member) continue;
            for (int inner = 0; inner < spec.innerCount; inner++) {
                int ox = spec.hasOffset ? triangle(random, spec.xzRange, trace) : 0;
                int oy = spec.hasOffset ? triangle(random, spec.yRange, trace) : 0;
                int oz = spec.hasOffset ? triangle(random, spec.xzRange, trace) : 0;
                int px = x + ox, py = y + oy, pz = z + oz;
                emit(trace, "offset", index, inner, ox, oy, oz, px, py ^ ((long) pz << 32));
                if (spec.predicate != Predicate.NONE
                        && !predicate(spec.predicate, world, px, py, pz, trace)) continue;
                Result one = placeConfigured(index, random, px, py, pz, world, trace);
                out.add(one);
            }
        }
        emit(trace, "placed_result", index, out.attempts, out.successes, out.writes,
                out.retained, out.ticks, out.successes > 0 ? 1 : 0);
        return out.result();
    }

    public static Result placeConfigured(int index, Mc263WorldgenRandomSource random, int x, int y, int z,
                                         WorldAccess world) {
        return placeConfigured(index, random, x, y, z, world, NO_TRACE);
    }

    public static Result placeConfigured(int index, Mc263WorldgenRandomSource random, int x, int y, int z,
                                         WorldAccess world, TraceSink trace) {
        Spec spec = requireSpec(index);
        requireSupported(spec, world);
        State selected = spec.provider.select(random, x, y, z, trace);
        emit(trace, "provider", index, stateId(selected), x, y, z);
        boolean survives = world.canSurvive(selected, x, y, z);
        emit(trace, "survive", index, x, y, z, survives ? 1 : 0);
        if (!survives) {
            emit(trace, "configured_result", index, 0, 0, 0, 0);
            return new Result(false, 1, 0, 0, 0, 0);
        }
        int writes = 0, retained = 0, ticks = 0;
        if (isDoublePlant(selected.block)) {
            State upperOld = read(world, x, y + 1, z, trace);
            boolean replaceable = upperOld.airTag
                    || selected.fluid.equals(upperOld.fluid) && upperOld.replaceable;
            if (!replaceable) {
                emit(trace, "configured_result", index, 0, 0, 0, 0);
                return new Result(false, 1, 0, 0, 0, 0);
            }
            State lower = selected.property("half", "lower");
            State upper = selected.property("half", "upper");
            retained += write(world, x, y, z, lower, 2, trace) ? 1 : 0; writes++;
            retained += write(world, x, y + 1, z, upper, 2, trace) ? 1 : 0; writes++;
        } else {
            retained += write(world, x, y, z, selected, 2, trace) ? 1 : 0; writes++;
        }
        if (spec.scheduleTick) {
            State live = read(world, x, y, z, trace);
            world.scheduleTick(x, y, z, live.block, 1);
            emit(trace, "schedule", index, x, y, z, stateId(live));
            ticks++;
        }
        emit(trace, "configured_result", index, 1, writes, retained, ticks);
        return new Result(true, 1, 1, writes, retained, ticks);
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
                if (phase == null || event.values.length != phase.arity) throw new IllegalArgumentException("bad event");
                out.writeByte(phase.id); out.writeByte(phase.arity);
                for (long value : event.values) out.writeLong(value);
            }
            out.flush(); return bytes.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }

    public static String featureKey(int index) { return requireSpec(index).key; }
    public static List<Integer> coveredIndices() { return SPECS.keySet().stream().sorted().toList(); }

    /** Pure exact capability/state closure check for one placed feature. */
    public static void preflight(int index, WorldAccess world) {
        requireSupported(requireSpec(index), world);
    }

    private static boolean predicate(Predicate predicate, WorldAccess world, int x, int y, int z,
                                     TraceSink trace) {
        State here = read(world, x, y, z, trace);
        boolean ok = switch (predicate) {
            case AIR -> here.airTag;
            case AIR_NOT_PODZOL -> here.airTag && !read(world, x, y - 1, z, trace).block.equals("minecraft:podzol");
            case AIR_GRASS -> here.airTag && read(world, x, y - 1, z, trace).block.equals("minecraft:grass_block");
            case REPLACEABLE_EMPTY_GRASS -> here.replaceable && fluid(world, x, y, z, trace).equals("minecraft:empty")
                    && read(world, x, y - 1, z, trace).block.equals("minecraft:grass_block");
            case NONE -> true;
        };
        emit(trace, "predicate", predicate.ordinal(), x, y, z, ok ? 1 : 0);
        return ok;
    }

    private static State read(WorldAccess world, int x, int y, int z, TraceSink trace) {
        State state = world.blockState(x, y, z);
        emit(trace, "read", x, y, z, stateId(state), state.airTag ? 1 : 0);
        return state;
    }
    private static String fluid(WorldAccess world, int x, int y, int z, TraceSink trace) {
        String fluid = key(world.fluidState(x, y, z));
        emit(trace, "fluid", x, y, z, keyId(fluid));
        return fluid;
    }
    private static boolean write(WorldAccess world, int x, int y, int z, State state, int flags, TraceSink trace) {
        boolean retained = world.trySetBlockState(x, y, z, state, flags);
        emit(trace, "write", x, y, z, stateId(state), flags, retained ? 1 : 0, state.properties.hashCode());
        return retained;
    }
    private static boolean rarity(Mc263WorldgenRandomSource random, int chance, TraceSink trace) {
        float value = random.nextFloat(); emit(trace, "rng_float", Float.floatToRawIntBits(value), chance);
        return value < 1.0f / (float) chance;
    }
    private static int nextInt(Mc263WorldgenRandomSource random, int bound, TraceSink trace) {
        int value = random.nextInt(bound); emit(trace, "rng_int", bound, value, 0); return value;
    }
    private static int triangle(Mc263WorldgenRandomSource random, int range, TraceSink trace) {
        return nextInt(random, range + 1, trace) - nextInt(random, range + 1, trace);
    }
    private static boolean biomeContains(int index, String key, String biome) {
        return Mc263FeatureIndexReceipt.biome(biome).featuresAtStep(STEP).stream()
                .anyMatch(r -> r.globalIndex() == index && r.featureKey().equals(key));
    }
    private static boolean isDoublePlant(String block) {
        return block.equals("minecraft:tall_grass") || block.equals("minecraft:large_fern")
                || block.equals("minecraft:sunflower");
    }
    private static long stateId(State state) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : state.canonical().getBytes(StandardCharsets.UTF_8)) hash = (hash ^ (b & 255)) * 0x100000001b3L;
        return hash;
    }
    private static long keyId(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) hash = (hash ^ (b & 255)) * 0x100000001b3L;
        return hash;
    }
    private static String key(String value) {
        if (value == null || !RESOURCE_KEY.matcher(value).matches()) throw new IllegalArgumentException("invalid key");
        return value;
    }
    private static Spec requireSpec(int index) {
        Spec spec = SPECS.get(index); if (spec == null) throw new IllegalArgumentException("unsupported index " + index); return spec;
    }
    private static void requireSupported(Spec spec, WorldAccess world) {
        if (!world.supportsFeature(spec.index)) {
            throw new UnsupportedOperationException("unsupported exact vegetation feature: " + spec.key);
        }
        for (State state : spec.provider.states) {
            if (!world.supportsState(state)) {
                throw new UnsupportedOperationException("unregistered exact vegetation state: "
                        + state.canonical());
            }
        }
    }

    private static Map<Integer, Spec> specs() {
        Map<Integer, Spec> m = new LinkedHashMap<>();
        add(m,6,"brown_mushroom_dappled_forest","brown_mushroom",1,2,96,7,3,Heightmap.MOTION_BLOCKING,Predicate.AIR,false);
        add(m,7,"patch_red_shrub","red_shrub",1,0,8,7,3,Heightmap.WORLD_SURFACE_WG,Predicate.AIR,false);
        add(m,11,"patch_tall_grass","tall_grass",1,5,96,7,3,Heightmap.MOTION_BLOCKING,Predicate.AIR,false);
        add(m,13,"flower_warm","flower_default",1,16,64,7,3,Heightmap.MOTION_BLOCKING,Predicate.AIR,false);
        add(m,14,"patch_grass_jungle","grass_jungle",25,0,32,7,3,Heightmap.WORLD_SURFACE_WG,Predicate.AIR_NOT_PODZOL,false);
        add(m,15,"patch_grass_savanna","grass",20,0,32,7,3,Heightmap.WORLD_SURFACE_WG,Predicate.AIR,false);
        add(m,18,"pale_garden_flowers","pale_forest_flower",1,8,96,7,3,Heightmap.MOTION_BLOCKING_NO_LEAVES,Predicate.AIR,true);
        addNoOffset(m,19,"flower_pale_garden","flower_pale_garden",32,Heightmap.MOTION_BLOCKING,true);
        add(m,23,"flower_flower_forest","flower_flower_forest",3,2,96,6,2,Heightmap.MOTION_BLOCKING,Predicate.AIR,false);
        add(m,25,"wildflowers_birch_forest","wildflower",3,2,64,6,2,Heightmap.MOTION_BLOCKING,Predicate.AIR,false);
        noiseCustom(m,29,"patch_tall_grass_2","tall_grass",0,7,32,96,7,3,
                Heightmap.MOTION_BLOCKING,Predicate.AIR);
        add(m,37,"patch_sunflower","sunflower",1,3,96,7,3,Heightmap.MOTION_BLOCKING,Predicate.AIR,false);
        noise(m,38,"patch_grass_meadow","grass",16,7,3,Heightmap.WORLD_SURFACE_WG,Predicate.AIR);
        add(m,39,"flower_meadow","flower_meadow",1,0,96,6,2,Heightmap.MOTION_BLOCKING,Predicate.AIR,false);
        noise(m,41,"wildflowers_meadow","wildflower",8,6,2,Heightmap.MOTION_BLOCKING,Predicate.AIR);
        add(m,43,"patch_large_fern","large_fern",1,5,96,7,3,Heightmap.MOTION_BLOCKING,Predicate.AIR,false);
        add(m,52,"flower_swamp","flower_swamp",1,32,64,6,2,Heightmap.MOTION_BLOCKING,Predicate.AIR,false);
        add(m,54,"patch_bush","bush",1,4,24,5,3,Heightmap.MOTION_BLOCKING,Predicate.AIR,false);
        noiseCustom(m,56,"flower_plains","flower_plain",15,4,32,64,6,2,
                Heightmap.MOTION_BLOCKING,Predicate.AIR);
        noise(m,57,"patch_grass_plain","grass",32,7,3,Heightmap.WORLD_SURFACE_WG,Predicate.AIR);
        noise(m,58,"flower_cherry","flower_cherry",96,6,2,Heightmap.MOTION_BLOCKING,Predicate.AIR);
        add(m,60,"flower_default","flower_default",1,32,64,7,3,Heightmap.MOTION_BLOCKING,Predicate.AIR,false);
        add(m,61,"patch_grass_taiga","taiga_grass",7,0,32,7,3,Heightmap.WORLD_SURFACE_WG,Predicate.AIR,false);
        add(m,62,"patch_grass_forest","grass",2,0,32,7,3,Heightmap.WORLD_SURFACE_WG,Predicate.AIR,false);
        add(m,63,"patch_grass_taiga_2","taiga_grass",1,0,32,7,3,Heightmap.WORLD_SURFACE_WG,Predicate.AIR,false);
        add(m,64,"patch_grass_normal","grass",5,0,32,7,3,Heightmap.WORLD_SURFACE_WG,Predicate.AIR,false);
        add(m,65,"patch_dead_bush","dead_bush",1,0,4,7,3,Heightmap.WORLD_SURFACE_WG,Predicate.AIR,false);
        add(m,66,"brown_mushroom_old_growth","brown_mushroom",3,4,96,7,3,Heightmap.MOTION_BLOCKING,Predicate.AIR,false);
        add(m,67,"red_mushroom_old_growth","red_mushroom",1,171,96,7,3,Heightmap.MOTION_BLOCKING,Predicate.AIR,false);
        add(m,69,"brown_mushroom_swamp","brown_mushroom",2,0,96,7,3,Heightmap.MOTION_BLOCKING,Predicate.AIR,false);
        add(m,70,"red_mushroom_swamp","red_mushroom",1,64,96,7,3,Heightmap.MOTION_BLOCKING,Predicate.AIR,false);
        add(m,71,"patch_grass_badlands","grass",1,0,32,7,3,Heightmap.WORLD_SURFACE_WG,Predicate.AIR,false);
        add(m,72,"patch_dry_grass_desert","dry_grass",1,3,64,7,3,Heightmap.MOTION_BLOCKING,Predicate.AIR,false);
        add(m,73,"patch_dead_bush_2","dead_bush",2,0,4,7,3,Heightmap.WORLD_SURFACE_WG,Predicate.AIR,false);
        add(m,74,"patch_dry_grass_badlands","dry_grass",1,6,64,7,3,Heightmap.MOTION_BLOCKING,Predicate.AIR,false);
        add(m,75,"patch_dead_bush_badlands","dead_bush",20,0,4,7,3,Heightmap.WORLD_SURFACE_WG,Predicate.AIR,false);
        add(m,76,"brown_mushroom_normal","brown_mushroom",1,256,96,7,3,Heightmap.MOTION_BLOCKING,Predicate.AIR,false);
        add(m,77,"red_mushroom_normal","red_mushroom",1,512,96,7,3,Heightmap.MOTION_BLOCKING,Predicate.AIR,false);
        add(m,80,"patch_leaf_litter","leaf_litter",2,0,32,7,3,Heightmap.WORLD_SURFACE_WG,Predicate.AIR_GRASS,false);
        add(m,83,"brown_mushroom_taiga","brown_mushroom",1,4,96,7,3,Heightmap.MOTION_BLOCKING,Predicate.AIR,false);
        add(m,84,"red_mushroom_taiga","red_mushroom",1,256,96,7,3,Heightmap.MOTION_BLOCKING,Predicate.AIR,false);
        add(m,85,"patch_pumpkin","pumpkin",1,300,96,7,3,Heightmap.MOTION_BLOCKING,Predicate.AIR_GRASS,false);
        add(m,88,"patch_firefly_bush_swamp","firefly_bush",1,8,20,4,3,Heightmap.MOTION_BLOCKING,Predicate.AIR,false);
        add(m,95,"patch_melon_sparse","melon",1,64,64,7,3,Heightmap.MOTION_BLOCKING,Predicate.REPLACEABLE_EMPTY_GRASS,false);
        add(m,96,"patch_melon","melon",1,6,64,7,3,Heightmap.MOTION_BLOCKING,Predicate.REPLACEABLE_EMPTY_GRASS,false);
        add(m,97,"patch_berry_common","berry_bush",1,32,96,7,3,Heightmap.WORLD_SURFACE_WG,Predicate.AIR_GRASS,false);
        add(m,98,"patch_berry_rare","berry_bush",1,384,96,7,3,Heightmap.WORLD_SURFACE_WG,Predicate.AIR_GRASS,false);
        return Map.copyOf(m);
    }
    private static void noise(Map<Integer,Spec> m,int i,String k,String p,int inner,int xz,int y,Heightmap h,Predicate f) {
        noiseCustom(m,i,k,p,5,10,0,inner,xz,y,h,f);
    }
    private static void noiseCustom(Map<Integer,Spec> m,int i,String k,String p,int below,
            int above,int rarity,int inner,int xz,int y,Heightmap h,Predicate f) {
        add(m,i,k,p,1,rarity,inner,xz,y,h,f,false); Spec s=m.get(i);
        m.put(i,new Spec(s.index,s.key,s.provider,1,rarity,inner,xz,y,h,f,false,true,true,
                below,above));
    }
    private static void add(Map<Integer,Spec> m,int i,String k,String p,int outer,int rarity,int inner,int xz,int y,Heightmap h,Predicate f,boolean tick) {
        m.put(i,new Spec(i,"minecraft:"+k,provider(p),outer,rarity,inner,xz,y,h,f,tick,false,true,0,0));
    }
    private static void addNoOffset(Map<Integer,Spec> m,int i,String k,String p,int rarity,Heightmap h,boolean tick) {
        m.put(i,new Spec(i,"minecraft:"+k,provider(p),1,rarity,1,0,0,h,Predicate.NONE,tick,false,false,0,0));
    }
    private static Provider provider(String name) {
        return switch (name) {
            case "tall_grass","large_fern","sunflower" -> Provider.one(
                    State.of("minecraft:" + name).property("half", "lower"));
            case "red_shrub","grass","flower_swamp","bush","dead_bush",
                    "brown_mushroom","red_mushroom","pumpkin","firefly_bush","melon" -> Provider.one(State.of("minecraft:" + switch(name) {
                        case "grass" -> "short_grass";
                        case "flower_swamp" -> "blue_orchid";
                        default -> name;
                    }));
            case "pale_forest_flower","flower_pale_garden" -> Provider.one(State.of("minecraft:closed_eyeblossom"));
            case "berry_bush" -> Provider.one(State.of("minecraft:sweet_berry_bush").property("age","3"));
            case "flower_default" -> new Provider(List.of(State.of("minecraft:poppy"),State.of("minecraft:dandelion")),new int[]{2,1});
            case "flower_flower_forest" -> Provider.noise(ProviderKind.FLOWER_FOREST, List.of(
                    "dandelion","poppy","allium","azure_bluet","red_tulip","orange_tulip",
                    "white_tulip","pink_tulip","oxeye_daisy","cornflower","lily_of_the_valley"));
            case "flower_meadow" -> Provider.noise(ProviderKind.MEADOW, List.of(
                    "tall_grass","allium","poppy","azure_bluet","dandelion","cornflower",
                    "oxeye_daisy","short_grass"));
            case "flower_plain" -> Provider.noise(ProviderKind.PLAINS, List.of(
                    "dandelion","orange_tulip","red_tulip","pink_tulip","white_tulip",
                    "poppy","azure_bluet","oxeye_daisy","cornflower"));
            case "grass_jungle" -> new Provider(List.of(State.of("minecraft:short_grass"),State.of("minecraft:fern")),new int[]{3,1});
            case "taiga_grass" -> new Provider(List.of(State.of("minecraft:short_grass"),State.of("minecraft:fern")),new int[]{1,4});
            case "dry_grass" -> new Provider(List.of(State.of("minecraft:short_dry_grass"),State.of("minecraft:tall_dry_grass")),new int[]{1,1});
            case "flower_cherry" -> petals("minecraft:pink_petals",4);
            case "wildflower" -> petals("minecraft:wildflowers",4);
            case "leaf_litter" -> petals("minecraft:leaf_litter",3);
            default -> throw new IllegalArgumentException(name);
        };
    }
    private static Provider petals(String block, int max) {
        List<State> states=new ArrayList<>();
        for(int amount=1;amount<=max;amount++) for(String face:List.of("north","east","south","west"))
            states.add(State.of(block).property("facing",face).property(block.endsWith("leaf_litter")?"segment_amount":"flower_amount",Integer.toString(amount)));
        return new Provider(states,states.stream().mapToInt(x->1).toArray());
    }
    private static void emit(TraceSink t,String p,long a,long b) { if(t.enabled()) t.record(p,a,b); }
    private static void emit(TraceSink t,String p,long a,long b,long c) { if(t.enabled()) t.record(p,a,b,c); }
    private static void emit(TraceSink t,String p,long a,long b,long c,long d) { if(t.enabled()) t.record(p,a,b,c,d); }
    private static void emit(TraceSink t,String p,long a,long b,long c,long d,long e) { if(t.enabled()) t.record(p,a,b,c,d,e); }
    private static void emit(TraceSink t,String p,long a,long b,long c,long d,long e,long f,long g) { if(t.enabled()) t.record(p,a,b,c,d,e,f,g); }

    private enum Predicate { NONE, AIR, AIR_NOT_PODZOL, AIR_GRASS, REPLACEABLE_EMPTY_GRASS }
    private enum ProviderKind { FIXED, FLOWER_FOREST, MEADOW, PLAINS }
    private record Spec(int index,String key,Provider provider,int outerCount,int rarity,int innerCount,int xzRange,int yRange,Heightmap heightmap,Predicate predicate,boolean scheduleTick,boolean noiseCount,boolean hasOffset,int noiseBelow,int noiseAbove) { }
    private record PhaseSpec(int id,int arity) { }
    private record Provider(List<State> states,int[] weights,ProviderKind kind) {
        Provider(List<State> states,int[] weights) { this(states,weights,ProviderKind.FIXED); }
        Provider { states=List.copyOf(states); weights=weights.clone(); }
        static Provider one(State s) { return new Provider(List.of(s),new int[]{1}); }
        static Provider noise(ProviderKind kind,List<String> blocks) {
            List<State> states=blocks.stream().map(block -> State.of("minecraft:"+block))
                    .map(state -> state.block.equals("minecraft:tall_grass")
                            ? state.property("half","lower") : state).toList();
            return new Provider(states,new int[states.size()],kind);
        }
        State select(Mc263WorldgenRandomSource r,int x,int y,int z,TraceSink t) {
            if(kind==ProviderKind.FLOWER_FOREST) return noiseState(states,FLOWER_FOREST_NOISE,
                    0.020833334F,x,y,z,kind,t);
            if(kind==ProviderKind.MEADOW) {
                float slow=sampleSlow(MEADOW_SLOW_NOISE,1.0F,x,y,z,kind,t);
                int variety=(int)Math.max(1.0D,Math.min(4.0D,1.0D+(slow+1.0D)*1.5D));
                List<State> possible=new ArrayList<>(variety);
                for(int i=0;i<variety;i++) possible.add(noiseStateSlow(states,
                        MEADOW_SLOW_NOISE,1.0F,x+i*54545,y,z+i*34234,kind,t));
                return noiseState(possible,MEADOW_FAST_NOISE,1.0F,x,y,z,kind,t);
            }
            if(kind==ProviderKind.PLAINS) {
                float noise=sample(PLAINS_NOISE,0.005F,x,y,z,kind,t);
                if(noise < -0.8F) return states.get(1+nextInt(r,4,t));
                float value=r.nextFloat();
                emit(t,"rng_float",Float.floatToRawIntBits(value),3);
                if(value < 0.33333334F) {
                    return states.get(5+nextInt(r,4,t));
                }
                return states.getFirst();
            }
            if(states.size()==1)return states.getFirst(); int total=0;for(int w:weights)total+=w;
            int pick=nextInt(r,total,t); for(int i=0;i<weights.length;i++)if((pick-=weights[i])<0)return states.get(i);
            throw new AssertionError();
        }
        private static State noiseState(List<State> states,McNormalNoise noise,float scale,
                int x,int y,int z,ProviderKind kind,TraceSink trace) {
            float value=sample(noise,scale,x,y,z,kind,trace);
            float placement=Math.max(0.0F,Math.min(0.9999F,(1.0F+value)/2.0F));
            return states.get((int)(placement*states.size()));
        }
        private static State noiseStateSlow(List<State> states,McNormalNoise noise,float scale,
                int x,int y,int z,ProviderKind kind,TraceSink trace) {
            float value=sampleSlow(noise,scale,x,y,z,kind,trace);
            float placement=Math.max(0.0F,Math.min(0.9999F,(1.0F+value)/2.0F));
            return states.get((int)(placement*states.size()));
        }
        private static float sample(McNormalNoise noise,float scale,int x,int y,int z,
                ProviderKind kind,TraceSink trace) {
            float value=(float)noise.getValue((double)x*(double)scale,
                    (double)y*(double)scale,(double)z*(double)scale);
            emit(trace,"provider_noise",kind.ordinal(),x,y,z,Float.floatToRawIntBits(value));
            return value;
        }
        private static float sampleSlow(McNormalNoise noise,float scale,int x,int y,int z,
                ProviderKind kind,TraceSink trace) {
            float value=(float)noise.getValue((double)((float)x*scale),
                    (double)((float)y*scale),(double)((float)z*scale));
            emit(trace,"provider_noise",kind.ordinal(),x,y,z,Float.floatToRawIntBits(value));
            return value;
        }
    }
    private static final class Counters {
        int attempts,successes,writes,retained,ticks;
        void add(Result r){attempts+=r.attempts;successes+=r.successes;writes+=r.attemptedWrites;retained+=r.retainedWrites;ticks+=r.scheduledTicks;}
        Result result(){return new Result(successes>0,attempts,successes,writes,retained,ticks);}
    }
}
