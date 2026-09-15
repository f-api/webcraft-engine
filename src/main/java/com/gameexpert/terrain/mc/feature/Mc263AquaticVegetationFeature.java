package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Exact dormant Minecraft 26.3-snapshot-7 step-nine waterlily/coral/sea-pickle leaves. */
public final class Mc263AquaticVegetationFeature {
    public static final int STEP = 9;
    public static final int TRACE_MAGIC = 0x41515633; // AQV3
    public static final int TRACE_VERSION = 1;
    public static final String TRACE_SCHEMA = "mc263-aquatic-vegetation-trace-v1";
    public static final String INNER_SERVER_SHA1 = "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String VEGETATION_FEATURES_CLASS_SHA256 = "aa17704f8fc6c698962d267d5dae8533f1bfce7a129be33cf6fd111c73f94e39";
    public static final String AQUATIC_FEATURES_CLASS_SHA256 = "e06fbf7e7241d304eb3aa1fd64f787afae16998d0195aeb25da6c0c5c538bf53";
    public static final String SIMPLE_BLOCK_CLASS_SHA256 = "10745682a08452d5e5f148cfd627fa1ec84bf669570da5cd284ee2119e810101";
    public static final String SIMPLE_RANDOM_SELECTOR_CLASS_SHA256 = "68ccaacb1b6202ab376067a0ed8b4c6287ad50ca5d014ce0100d963352650302";
    public static final String OVERLAY_CLASS_SHA256 = "891393697544a613b185dc2583a7f5f93c6b665564475ac2d1b8fbc8fce11b78";
    public static final String CORAL_TREE_CLASS_SHA256 = "2b684521f4f6c9ab29776ae7165dc03f2c2a3cc82b5f0c50fcc76c2e62b2bc3b";
    public static final String CORAL_CLAW_CLASS_SHA256 = "d3a4a43cd5b775daeaca94cf3fc2a2092d29f29188d3db6afdd4eb5e3310ef5b";
    public static final String CUBOID_PLACEMENT_CLASS_SHA256 = "7d20cfe28055e3729182ecb2581fd869602fb158bfc27e08a6bc0374df898943";
    public static final String HEIGHTMAP_PLACEMENT_CLASS_SHA256 = "ab73361bbb5b2ed3e5083b9518a6b10725629291f9fc17e7ba951d11f1df94e2";
    public static final String NOISE_COUNT_PLACEMENT_CLASS_SHA256 = "87e478a1120cba5f1f86db162059c24f4ec4c826b94b213d9542dfa9b5b4bba4";
    public static final String RANDOMIZED_INT_PROVIDER_CLASS_SHA256 = "34d8d7da1c609d0ccd76785371a31d3901684d26db1f4a3ce90ef5b574a85511";
    public static final String RANDOM_BLOCK_PROVIDER_CLASS_SHA256 = "5d20034a1e2bbae3665b77ffaa88a6279360c760ae91b94a5c4eb1d2fe9b5d59";
    public static final String ROTATED_PROVIDER_CLASS_SHA256 = "4edd685b750be5ce46e43afcfbdf8bd860e68fe8e933cb2459c3247ba9dafbad";
    public static final String LILY_PAD_CLASS_SHA256 = "a6ebeded1f642a253c28b0313d4cf707db85453c52386d58961b75faef21481d";
    public static final String SEA_PICKLE_CLASS_SHA256 = "f11fbde45e40c977b801a30cb5886aa95814a0f3ec3c041ab4e4bfa8846fb6dd";
    public static final String WATERLILY_FEATURE_JSON_SHA256 = "59baea8082137eb979a428f83752ad1971589ab1a7eb4f219aefa7d848d1bf93";
    public static final String PATCH_WATERLILY_JSON_SHA256 = "56570834eedefaf622034b007d76e1e94e3f7115e9b690845893eac84ad7c149";
    public static final String WARM_OCEAN_FEATURE_JSON_SHA256 = "724ea122ff43ddd1f3c44daf34722cfc37afe77a5e7f94e9c9eacf9d7a32c67f";
    public static final String WARM_OCEAN_PLACED_JSON_SHA256 = "09ed94181ca23e049f84a1eff86ca445ca23e726435b973bf487f4e2ca632004";
    public static final String SEA_PICKLE_FEATURE_JSON_SHA256 = "04e5885ad9bff1284e59e6c4ac941bdbcccbecccf7570b66ed8a393c100aebba";
    public static final String SEA_PICKLE_PLACED_JSON_SHA256 = "5cdb422a2c3caa563e61bbb2a73ab0ab1a343934f8d176ef8cf91bc77edebc19";
    public static final String CORAL_DECORATION_JSON_SHA256 = "222504a633a339bcfe5ac0fb2457ceb19ffd09790566c8fcb35ad53e54cf3835";
    public static final String TUBE_CORAL_BLOCK_JSON_SHA256 = "4573c92cdbb09614ee572efce34a005ccb3976f54bf6b184d0364d0d28bba8a5";
    public static final String BRAIN_CORAL_BLOCK_JSON_SHA256 = "f8f3f469297706d0a5e5a2b1e690be0b413b6b90983788bf64921b6429a385f0";
    public static final String BUBBLE_CORAL_BLOCK_JSON_SHA256 = "b859adcf181cd5010fa535068ffd802f541f5c4524eecac0d9a9f74248b7f9bd";
    public static final String FIRE_CORAL_BLOCK_JSON_SHA256 = "6e6b6f8de86e6f6a5b8f119f438b5b80b2f1811c9604b62d7a485ab674d4b128";
    public static final String HORN_CORAL_BLOCK_JSON_SHA256 = "da231f2a68e2de3eb8919a6a75ff871c87686d54295ddb3562dfba5156d6e151";
    public static final String CORALS_TAG_JSON_SHA256 = "421caec9d699d741d7fd69c003fb46e0d551eaa94c9af80ea1a9e75e8d89d029";
    public static final String CORAL_PLANTS_TAG_JSON_SHA256 = "f6646184b65ca1bc37deffc95cb56ff01a71374904052bca67985226e1ec85c2";
    public static final String WALL_CORALS_TAG_JSON_SHA256 = "e218e0a27b6e0b1ff685c3181a747b25eef3fdecf1d91bd22acc420eed16cf39";
    public static final int[] COVERED_INDICES = {68, 99, 101};

    private static final String[] SPECIES = {"tube", "brain", "bubble", "fire", "horn"};
    private static final State LILY_PAD = state("minecraft:lily_pad");
    private static final State[] PICKLES = new State[4];
    private static final State[] CORAL_BLOCKS = new State[5];
    private static final State[] FLOOR_CORALS = new State[10];
    private static final State[][] WALL_CORALS = new State[4][5];
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private static final Map<String, PhaseSpec> PHASES = Map.ofEntries(
            Map.entry("outer", new PhaseSpec(1, 5)),
            Map.entry("rng_int", new PhaseSpec(2, 3)),
            Map.entry("rng_float", new PhaseSpec(3, 3)),
            Map.entry("noise", new PhaseSpec(4, 5)),
            Map.entry("square", new PhaseSpec(5, 5)),
            Map.entry("offset", new PhaseSpec(6, 8)),
            Map.entry("cuboid", new PhaseSpec(7, 8)),
            Map.entry("height", new PhaseSpec(8, 6)),
            Map.entry("read", new PhaseSpec(9, 7)),
            Map.entry("predicate", new PhaseSpec(10, 7)),
            Map.entry("biome", new PhaseSpec(11, 6)),
            Map.entry("selector", new PhaseSpec(12, 5)),
            Map.entry("shape", new PhaseSpec(13, 7)),
            Map.entry("shuffle", new PhaseSpec(14, 3)),
            Map.entry("survive", new PhaseSpec(15, 6)),
            Map.entry("write", new PhaseSpec(16, 8)),
            Map.entry("configured_result", new PhaseSpec(17, 8)),
            Map.entry("placed_result", new PhaseSpec(18, 8)));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public boolean enabled() { return false; }
        @Override public void record(String phase, long... values) {
            throw new AssertionError("disabled aquatic-vegetation trace emitted");
        }
    };

    static {
        for (int pickles = 1; pickles <= 4; pickles++) {
            PICKLES[pickles - 1] = new State("minecraft:sea_pickle",
                    Map.of("pickles", Integer.toString(pickles), "waterlogged", "true"),
                    "minecraft:water", true, false);
        }
        for (int species = 0; species < SPECIES.length; species++) {
            String key = SPECIES[species];
            CORAL_BLOCKS[species] = state("minecraft:" + key + "_coral_block");
            FLOOR_CORALS[species] = waterlogged("minecraft:" + key + "_coral");
            FLOOR_CORALS[species + 5] = waterlogged("minecraft:" + key + "_coral_fan");
            for (Direction direction : HORIZONTAL) {
                WALL_CORALS[direction.horizontalIndex][species] = new State(
                        "minecraft:" + key + "_coral_wall_fan",
                        Map.of("facing", direction.key, "waterlogged", "true"),
                        "minecraft:water", true, false);
            }
        }
    }

    private Mc263AquaticVegetationFeature() { }

    public enum Heightmap { WORLD_SURFACE_WG, OCEAN_FLOOR, OCEAN_FLOOR_WG }
    public enum Direction {
        NORTH(0, 0, -1, "north", 0), EAST(1, 0, 0, "east", 1),
        SOUTH(0, 0, 1, "south", 2), WEST(-1, 0, 0, "west", 3),
        UP(0, 1, 0, "up", -1), DOWN(0, -1, 0, "down", -1);
        final int dx, dy, dz, horizontalIndex;
        final String key;
        Direction(int dx, int dy, int dz, String key, int horizontalIndex) {
            this.dx = dx; this.dy = dy; this.dz = dz; this.key = key;
            this.horizontalIndex = horizontalIndex;
        }
        Direction clockwise() { return HORIZONTAL[(horizontalIndex + 1) & 3]; }
        Direction counterClockwise() { return HORIZONTAL[(horizontalIndex + 3) & 3]; }
        Direction opposite() {
            if (this == UP) return DOWN;
            if (this == DOWN) return UP;
            return HORIZONTAL[(horizontalIndex + 2) & 3];
        }
    }

    public interface WorldAccess {
        int minGenerationY();
        int height(Heightmap heightmap, int x, int z);
        String biomeKey(int x, int y, int z);
        /** Exact float result of {@code Biome.BIOME_INFO_NOISE.get(x / 400, z / 400)}. */
        float biomeInfoNoise(int sourceX, int sourceZ);
        State blockState(int x, int y, int z);
        boolean canSurvive(State state, int x, int y, int z);
        boolean supportsFeature(int globalIndex);
        boolean supportsState(State state);
        boolean trySetBlockState(int x, int y, int z, State state, int flags);
    }

    public record State(String block, Map<String, String> properties, String fluid,
                        boolean fluidSource, boolean airTag) {
        public State {
            block = key(block); fluid = key(fluid);
            properties = Collections.unmodifiableMap(new TreeMap<>(properties));
            if (fluidSource && !fluid.equals("minecraft:water")) {
                throw new IllegalArgumentException("only exact water source is supported");
            }
        }
        public static State block(String block) { return state(block); }
        public static State water(int level) {
            if (level < 0 || level > 15) throw new IllegalArgumentException("water level 0..15");
            return new State("minecraft:water", Map.of("level", Integer.toString(level)),
                    "minecraft:water", level == 0, false);
        }
        public static State air(String block) {
            return new State(block, Map.of(), "minecraft:empty", false, true);
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
    public record Result(boolean placed, int candidates, int configuredCalls,
                         int configuredSuccesses, int attemptedWrites, int retainedWrites) { }

    public static List<Integer> coveredIndices() { return List.of(68, 99, 101); }
    public static String featureKey(int index) {
        return switch (index) {
            case 68 -> "minecraft:patch_waterlily";
            case 99 -> "minecraft:warm_ocean_vegetation";
            case 101 -> "minecraft:sea_pickle";
            default -> throw new IllegalArgumentException("unsupported aquatic index " + index);
        };
    }

    /** Pure exact capability/state closure check for one placed feature. */
    public static void preflightPlaced(int index, WorldAccess world) {
        requireIndex(index);
        preflight(index, world);
    }

    public static Result placeWithFeatureRandom(int index, WorldgenRandom random,
            int sourceX, int sourceY, int sourceZ, WorldAccess world) {
        return placeWithFeatureRandom(index, random, sourceX, sourceY, sourceZ, world, NO_TRACE);
    }

    public static Result placeWithFeatureRandom(int index, WorldgenRandom random,
            int sourceX, int sourceY, int sourceZ, WorldAccess world, TraceSink trace) {
        requireIndex(index);
        preflight(index, world);
        Totals totals = new Totals();
        switch (index) {
            case 68 -> placeWaterlily(random, sourceX, sourceY, sourceZ, world, trace, totals);
            case 99 -> placeWarmOcean(random, sourceX, sourceY, sourceZ, world, trace, totals);
            case 101 -> placeSeaPickle(random, sourceX, sourceY, sourceZ, world, trace, totals);
            default -> throw new AssertionError(index);
        }
        emit(trace, "placed_result", index, totals.candidates, totals.calls, totals.successes,
                totals.writes, totals.retained, totals.successes > 0 ? 1 : 0, 0);
        return totals.result();
    }

    public static Result placeConfigured(int index, WorldgenRandom random, int x, int y, int z,
            WorldAccess world) {
        return placeConfigured(index, random, x, y, z, world, NO_TRACE);
    }

    public static Result placeConfigured(int index, WorldgenRandom random, int x, int y, int z,
            WorldAccess world, TraceSink trace) {
        requireIndex(index); preflight(index, world);
        Totals totals = new Totals(); totals.candidates = 1; totals.calls = 1;
        boolean success = switch (index) {
            case 68 -> simpleBlock(LILY_PAD, random, x, y, z, world, trace, totals, 680);
            case 99 -> warmConfigured(random, x, y, z, world, trace, totals);
            case 101 -> seaPickleConfigured(random, x, y, z, world, trace, totals, 1010);
            default -> throw new AssertionError(index);
        };
        if (success) totals.successes++;
        emit(trace, "configured_result", index, success ? 1 : 0, totals.candidates,
                totals.calls, totals.successes, totals.writes, totals.retained, 0);
        return totals.result();
    }

    private static void placeWaterlily(WorldgenRandom random, int sourceX, int sourceY,
            int sourceZ, WorldAccess world, TraceSink trace, Totals totals) {
        emit(trace, "outer", 68, 4, 10, 7, 3);
        for (int outer = 0; outer < 4; outer++) {
            int x = sourceX + nextInt(random, 16, 680, trace);
            int z = sourceZ + nextInt(random, 16, 681, trace);
            emit(trace, "square", 68, outer, x, sourceY, z);
            int y = world.height(Heightmap.WORLD_SURFACE_WG, x, z);
            emit(trace, "height", 68, Heightmap.WORLD_SURFACE_WG.ordinal(), x, z, y, outer);
            if (y <= world.minGenerationY()) continue;
            if (!biome(68, x, y, z, world, trace)) continue;
            for (int inner = 0; inner < 10; inner++) {
                int ox = triangle(random, 7, 682, trace);
                int oy = triangle(random, 3, 683, trace);
                int oz = triangle(random, 7, 684, trace);
                int px = x + ox, py = y + oy, pz = z + oz;
                emit(trace, "offset", 68, outer, inner, ox, oy, oz, px, packYz(py, pz));
                totals.candidates++;
                State current = read(world, px, py, pz, trace);
                boolean air = current.airTag;
                emit(trace, "predicate", 68, 1, px, py, pz, air ? 1 : 0, stateId(current));
                if (!air) continue;
                totals.calls++;
                if (simpleBlock(LILY_PAD, random, px, py, pz, world, trace, totals, 685)) {
                    totals.successes++;
                }
            }
        }
    }

    private static void placeSeaPickle(WorldgenRandom random, int sourceX, int sourceY,
            int sourceZ, WorldAccess world, TraceSink trace, Totals totals) {
        emit(trace, "outer", 101, 16, 20, 7, 0);
        if (!chance(random, 1.0F / 16.0F, 1010, trace)) return;
        int baseX = sourceX + nextInt(random, 16, 1011, trace);
        int baseZ = sourceZ + nextInt(random, 16, 1012, trace);
        emit(trace, "square", 101, 0, baseX, sourceY, baseZ);
        for (int candidate = 0; candidate < 20; candidate++) {
            int ox = triangle(random, 7, 1013, trace);
            int oy = triangle(random, 0, 1014, trace);
            int oz = triangle(random, 7, 1015, trace);
            int x = baseX + ox, z = baseZ + oz;
            emit(trace, "offset", 101, candidate, 0, ox, oy, oz, x, packYz(sourceY + oy, z));
            int y = world.height(Heightmap.OCEAN_FLOOR, x, z);
            emit(trace, "height", 101, Heightmap.OCEAN_FLOOR.ordinal(), x, z, y, candidate);
            if (y <= world.minGenerationY()) continue;
            totals.candidates++;
            State current = read(world, x, y, z, trace);
            boolean water = current.block.equals("minecraft:water");
            emit(trace, "predicate", 101, 2, x, y, z, water ? 1 : 0, stateId(current));
            if (!water || !biome(101, x, y, z, world, trace)) continue;
            totals.calls++;
            if (seaPickleConfigured(random, x, y, z, world, trace, totals, 1016)) {
                totals.successes++;
            }
        }
    }

    private static void placeWarmOcean(WorldgenRandom random, int sourceX, int sourceY,
            int sourceZ, WorldAccess world, TraceSink trace, Totals totals) {
        float noise = world.biomeInfoNoise(sourceX, sourceZ);
        int count = (int) Math.ceil((double) noise * 20.0D);
        emit(trace, "outer", 99, count, 400, 20, 0);
        emit(trace, "noise", 99, sourceX, sourceZ, Float.floatToRawIntBits(noise), count);
        for (int candidate = 0; candidate < count; candidate++) {
            int x = sourceX + nextInt(random, 16, 990, trace);
            int z = sourceZ + nextInt(random, 16, 991, trace);
            emit(trace, "square", 99, candidate, x, sourceY, z);
            int y = world.height(Heightmap.OCEAN_FLOOR_WG, x, z);
            emit(trace, "height", 99, Heightmap.OCEAN_FLOOR_WG.ordinal(), x, z, y, candidate);
            if (y <= world.minGenerationY()) continue;
            totals.candidates++;
            if (!biome(99, x, y, z, world, trace)) continue;
            totals.calls++;
            if (warmConfigured(random, x, y, z, world, trace, totals)) totals.successes++;
        }
    }

    private static boolean warmConfigured(WorldgenRandom random, int x, int y, int z,
            WorldAccess world, TraceSink trace, Totals totals) {
        int selected = nextInt(random, 15, 992, trace);
        int species = selected / 3, shape = selected % 3;
        emit(trace, "selector", 99, selected, species, shape, stateId(CORAL_BLOCKS[species]));
        return switch (shape) {
            case 0 -> coralTree(species, random, new Pos(x, y, z), world, trace, totals);
            case 1 -> coralClaw(species, random, new Pos(x, y, z), world, trace, totals);
            case 2 -> coralCuboid(species, random, new Pos(x, y, z), world, trace, totals);
            default -> throw new AssertionError(shape);
        };
    }

    private static boolean coralTree(int species, WorldgenRandom random, Pos origin,
            WorldAccess world, TraceSink trace, Totals totals) {
        Pos cursor = origin;
        int height = nextInt(random, 3, 993, trace) + 1;
        emit(trace, "shape", 99, 0, species, height, origin.x, origin.y, origin.z);
        for (int i = 0; i < height; i++) {
            if (!placeCoral(species, random, cursor, world, trace, totals)) return true;
            cursor = cursor.relative(Direction.UP);
        }
        Pos top = cursor;
        int branchCount = nextInt(random, 3, 994, trace) + 2;
        List<Direction> directions = shuffled(new ArrayList<>(List.of(HORIZONTAL)), random,
                trace, 9900);
        for (Direction direction : directions.subList(0, branchCount)) {
            cursor = top.relative(direction);
            int length = nextInt(random, 5, 995, trace) + 2;
            int consecutive = 0;
            for (int i = 0; i < length; i++) {
                if (!placeCoral(species, random, cursor, world, trace, totals)) break;
                consecutive++;
                cursor = cursor.relative(Direction.UP);
                if (i == 0 || consecutive >= 2 && chance(random, 0.25F, 996, trace)) {
                    cursor = cursor.relative(direction); consecutive = 0;
                }
            }
        }
        return true;
    }

    private static boolean coralClaw(int species, WorldgenRandom random, Pos origin,
            WorldAccess world, TraceSink trace, Totals totals) {
        emit(trace, "shape", 99, 1, species, 0, origin.x, origin.y, origin.z);
        if (!placeCoral(species, random, origin, world, trace, totals)) return false;
        Direction base = HORIZONTAL[nextInt(random, 4, 997, trace)];
        int count = nextInt(random, 2, 998, trace) + 2;
        List<Direction> arms = shuffled(new ArrayList<>(List.of(base, base.clockwise(),
                base.counterClockwise())), random, trace, 9901).subList(0, count);
        for (Direction arm : arms) {
            Pos cursor = origin.relative(arm);
            int first = nextInt(random, 2, 999, trace) + 1;
            final Direction movement;
            final int second;
            if (arm == base) {
                movement = base;
                second = nextInt(random, 3, 1000, trace) + 2;
            } else {
                cursor = cursor.relative(Direction.UP);
                movement = nextInt(random, 2, 1001, trace) == 0 ? arm : Direction.UP;
                second = nextInt(random, 3, 1002, trace) + 3;
            }
            for (int i = 0; i < first; i++) {
                if (!placeCoral(species, random, cursor, world, trace, totals)) break;
                cursor = cursor.relative(movement);
            }
            cursor = cursor.relative(movement.opposite()).relative(Direction.UP);
            for (int i = 0; i < second; i++) {
                cursor = cursor.relative(base);
                if (!placeCoral(species, random, cursor, world, trace, totals)) break;
                if (chance(random, 0.25F, 1003, trace)) cursor = cursor.relative(Direction.UP);
            }
        }
        return true;
    }

    private static boolean coralCuboid(int species, WorldgenRandom random, Pos origin,
            WorldAccess world, TraceSink trace, Totals totals) {
        int yOffset = nextInt(random, 3, 1004, trace) - 3;
        Pos base = new Pos(origin.x, origin.y + yOffset, origin.z);
        int ySize = nextInt(random, 3, 1005, trace) + 3;
        int xSize = nextInt(random, 3, 1006, trace) + 3;
        int zSize = nextInt(random, 3, 1007, trace) + 3;
        emit(trace, "shape", 99, 2, species, yOffset, xSize, ySize, zSize);
        int order = 0;
        boolean success = false;
        for (int dx = 0; dx <= xSize; dx++) {
            for (int dy = 0; dy <= ySize; dy++) {
                for (int dz = 0; dz <= zSize; dz++) {
                    boolean bx = dx == 0 || dx == xSize;
                    boolean by = dy == 0 || dy == ySize;
                    boolean bz = dz == 0 || dz == zSize;
                    int boundaryAxes = (bx ? 1 : 0) + (by ? 1 : 0) + (bz ? 1 : 0);
                    if (boundaryAxes != 1) continue;
                    Pos candidate = new Pos(base.x + dx, base.y + dy, base.z + dz);
                    emit(trace, "cuboid", 99, order++, dx, dy, dz, candidate.x,
                            packYz(candidate.y, candidate.z), 0);
                    if (!chance(random, 0.9F, 1008, trace)) continue;
                    success |= placeCoral(species, random, candidate, world, trace, totals);
                }
            }
        }
        return success;
    }

    private static boolean placeCoral(int species, WorldgenRandom random, Pos pos,
            WorldAccess world, TraceSink trace, Totals totals) {
        State current = read(world, pos.x, pos.y, pos.z, trace);
        boolean replaceable = current.block.equals("minecraft:water") || isCoral(current.block);
        emit(trace, "predicate", 99, 3, pos.x, pos.y, pos.z, replaceable ? 1 : 0,
                stateId(current));
        if (!replaceable) return false;
        State above = read(world, pos.x, pos.y + 1, pos.z, trace);
        boolean waterAbove = above.block.equals("minecraft:water");
        emit(trace, "predicate", 99, 4, pos.x, pos.y + 1, pos.z, waterAbove ? 1 : 0,
                stateId(above));
        if (!waterAbove) return false;
        boolean placed = simpleBlock(CORAL_BLOCKS[species], random, pos.x, pos.y, pos.z,
                world, trace, totals, 1009);
        boolean decorated = decorate(random, pos, world, trace, totals);
        return placed | decorated;
    }

    private static boolean decorate(WorldgenRandom random, Pos center, WorldAccess world,
            TraceSink trace, Totals totals) {
        Pos above = center.relative(Direction.UP);
        int top = nextInt(random, 80, 1017, trace);
        emit(trace, "selector", 99, top, 20, 3, 57);
        boolean result;
        if (top < 20) {
            State coral = FLOOR_CORALS[nextInt(random, 10, 1018, trace)];
            result = simpleBlock(coral, random, above.x, above.y, above.z, world, trace,
                    totals, 1019);
        } else if (top < 23) {
            result = seaPickleConfigured(random, above.x, above.y, above.z, world, trace,
                    totals, 1020);
        } else {
            result = true;
        }
        for (Direction direction : HORIZONTAL) {
            boolean wall = chance(random, 0.2F, 1021 + direction.horizontalIndex, trace);
            if (!wall) continue;
            Pos target = center.relative(direction);
            State current = read(world, target.x, target.y, target.z, trace);
            boolean water = current.block.equals("minecraft:water");
            emit(trace, "predicate", 99, 5 + direction.horizontalIndex, target.x, target.y,
                    target.z, water ? 1 : 0, stateId(current));
            if (!water) continue;
            State state = WALL_CORALS[direction.horizontalIndex][nextInt(random, 5,
                    1025 + direction.horizontalIndex, trace)];
            result |= simpleBlock(state, random, target.x, target.y, target.z, world, trace,
                    totals, 1029 + direction.horizontalIndex);
        }
        return result;
    }

    private static boolean seaPickleConfigured(WorldgenRandom random, int x, int y, int z,
            WorldAccess world, TraceSink trace, Totals totals, int kind) {
        int value = nextInt(random, 4, kind, trace) + 1;
        State state = PICKLES[value - 1];
        emit(trace, "selector", 101, value, 1, 4, stateId(state));
        return simpleBlock(state, random, x, y, z, world, trace, totals, kind + 1);
    }

    private static boolean simpleBlock(State state, WorldgenRandom random, int x, int y, int z,
            WorldAccess world, TraceSink trace, Totals totals, int kind) {
        boolean survives = world.canSurvive(state, x, y, z);
        emit(trace, "survive", kind, x, y, z, stateId(state), survives ? 1 : 0);
        if (!survives) return false;
        totals.writes++;
        boolean retained = world.trySetBlockState(x, y, z, state, 2);
        if (retained) totals.retained++;
        emit(trace, "write", kind, x, y, z, stateId(state), 2, retained ? 1 : 0,
                state.properties.hashCode());
        return true;
    }

    private static boolean biome(int index, int x, int y, int z, WorldAccess world,
            TraceSink trace) {
        String biome = world.biomeKey(x, y, z);
        boolean member = Mc263FeatureIndexReceipt.biome(biome).featuresAtStep(STEP).stream()
                .anyMatch(ref -> ref.globalIndex() == index
                        && ref.featureKey().equals(featureKey(index)));
        emit(trace, "biome", index, x, y, z, member ? 1 : 0, keyId(biome));
        return member;
    }

    private static State read(WorldAccess world, int x, int y, int z, TraceSink trace) {
        State state = world.blockState(x, y, z);
        emit(trace, "read", x, y, z, stateId(state), state.fluidSource ? 1 : 0,
                state.airTag ? 1 : 0, 0);
        return state;
    }

    private static int triangle(WorldgenRandom random, int range, int kind, TraceSink trace) {
        return nextInt(random, range + 1, kind, trace)
                - nextInt(random, range + 1, kind, trace);
    }
    private static int nextInt(WorldgenRandom random, int bound, int kind, TraceSink trace) {
        int value = random.nextInt(bound);
        emit(trace, "rng_int", bound, value, kind);
        return value;
    }
    private static boolean chance(WorldgenRandom random, float threshold, int kind,
            TraceSink trace) {
        float value = random.nextFloat();
        emit(trace, "rng_float", Float.floatToRawIntBits(value),
                Float.floatToRawIntBits(threshold), kind);
        return value < threshold;
    }
    private static List<Direction> shuffled(List<Direction> values, WorldgenRandom random,
            TraceSink trace, int kind) {
        for (int size = values.size(); size > 1; size--) {
            int target = nextInt(random, size, kind, trace);
            Direction old = values.set(size - 1, values.get(target));
            values.set(target, old);
        }
        long packed = 0;
        for (int i = 0; i < values.size(); i++) packed |= (long) values.get(i).ordinal() << i * 3;
        emit(trace, "shuffle", kind, values.size(), packed);
        return values;
    }

    private static void preflight(int index, WorldAccess world) {
        if (!world.supportsFeature(index)) {
            throw new UnsupportedOperationException(
                    "aquatic feature carrier does not support global index " + index);
        }
        List<State> required = new ArrayList<>();
        if (index == 68) required.add(LILY_PAD);
        if (index == 101) Collections.addAll(required, PICKLES);
        if (index == 99) {
            Collections.addAll(required, CORAL_BLOCKS);
            Collections.addAll(required, FLOOR_CORALS);
            Collections.addAll(required, PICKLES);
            for (State[] states : WALL_CORALS) Collections.addAll(required, states);
        }
        for (State state : required) {
            if (!world.supportsState(state)) {
                throw new UnsupportedOperationException("unregistered exact aquatic state: "
                        + state.canonical());
            }
        }
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
                if (phase == null || phase.arity != event.values.length) {
                    throw new IllegalArgumentException("invalid AQV3 event " + event.phase);
                }
                out.writeByte(phase.id); out.writeByte(phase.arity);
                for (long value : event.values) out.writeLong(value);
            }
            out.flush(); return bytes.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }

    private static void requireIndex(int index) { featureKey(index); }
    private static boolean isCoral(String block) {
        for (String species : SPECIES) {
            if (block.equals("minecraft:" + species + "_coral")
                    || block.equals("minecraft:" + species + "_coral_fan")) return true;
        }
        return false;
    }
    private static State state(String block) {
        return new State(block, Map.of(), "minecraft:empty", false, false);
    }
    private static State waterlogged(String block) {
        return new State(block, Map.of("waterlogged", "true"),
                "minecraft:water", true, false);
    }
    private static long packYz(int y, int z) { return (long) y << 32 | z & 0xffffffffL; }
    private static long stateId(State state) { return keyId(state.canonical()); }
    private static long keyId(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            hash = (hash ^ (b & 255)) * 0x100000001b3L;
        }
        return hash;
    }
    private static String key(String value) {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("invalid namespaced key: " + value);
        }
        return value;
    }
    // Fixed-arity guards are intentional: disabled production tracing must not allocate the
    // varargs payload arrays used by the receipt-only path.
    private static void emit(TraceSink t, String p, long a, long b, long c) {
        if (t.enabled()) t.record(p, a, b, c);
    }
    private static void emit(TraceSink t, String p, long a, long b, long c, long d, long e) {
        if (t.enabled()) t.record(p, a, b, c, d, e);
    }
    private static void emit(TraceSink t, String p, long a, long b, long c, long d, long e,
            long f) {
        if (t.enabled()) t.record(p, a, b, c, d, e, f);
    }
    private static void emit(TraceSink t, String p, long a, long b, long c, long d, long e,
            long f, long g) {
        if (t.enabled()) t.record(p, a, b, c, d, e, f, g);
    }
    private static void emit(TraceSink t, String p, long a, long b, long c, long d, long e,
            long f, long g, long h) {
        if (t.enabled()) t.record(p, a, b, c, d, e, f, g, h);
    }

    private record PhaseSpec(int id, int arity) { }
    private record Pos(int x, int y, int z) {
        Pos relative(Direction direction) {
            return new Pos(x + direction.dx, y + direction.dy, z + direction.dz);
        }
    }
    private static final class Totals {
        int candidates, calls, successes, writes, retained;
        Result result() {
            return new Result(successes > 0, candidates, calls, successes, writes, retained);
        }
    }
}
