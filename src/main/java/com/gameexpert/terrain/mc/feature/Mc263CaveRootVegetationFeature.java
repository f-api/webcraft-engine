package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Exact dormant 26.3 step-9 vegetation-patch, cave-vine, clay, and spore tranche. */
public final class Mc263CaveRootVegetationFeature {
    public static final int STEP = 9;
    public static final int TRACE_MAGIC = 0x43525633; // CRV3
    public static final int TRACE_VERSION = 1;
    public static final String TRACE_SCHEMA = "mc263-cave-root-vegetation-trace-v1";
    public static final int[] COVERED_INDICES = {17, 30, 31, 32, 33, 35};

    public static final String VEGETATION_PATCH_CLASS_SHA256 =
            "17848f86dfea14bee1df61214a3c76d569d38786fb9ea5263e0b9b558c981f37";
    public static final String WATERLOGGED_VEGETATION_PATCH_CLASS_SHA256 =
            "70b2824632cf4bb2457c1e7573e52c55ac8c3dfd53fb0f5bdb2d2c7d1b2be4cf";
    public static final String BLOCK_COLUMN_CLASS_SHA256 =
            "46d37e8cf9613d6115a28f90b38d17e935a529d00043b967d42ffd8b717bd4c6";
    public static final String SIMPLE_BLOCK_CLASS_SHA256 =
            "10745682a08452d5e5f148cfd627fa1ec84bf669570da5cd284ee2119e810101";
    public static final String RANDOM_BOOLEAN_SELECTOR_CLASS_SHA256 =
            "b8982e208eb957d862dbe870b21fdbd12024ca2c7d181555a1c6dc5a8a71e7c6";
    public static final String ENVIRONMENT_SCAN_CLASS_SHA256 =
            "ad4a0da23aef7d6142f408ba9943e3cfce53e9f0c23439176e950e43c168e5b2";
    public static final String PALE_MOSS_PLACED_JSON_SHA256 =
            "a5bbbe0a1bf1c1601bbb917b49839ccb83b74754025a8849078efe3d153fb524";
    public static final String CEILING_VEGETATION_PLACED_JSON_SHA256 =
            "ee469037e9b311013fc9297acbad43f3e75d5a55718ca197623203bd0fc45e09";
    public static final String CAVE_VINES_PLACED_JSON_SHA256 =
            "722409f8ed212962a74be2408449550f188a19d2c31a756e677a71041e3e5c7c";
    public static final String LUSH_CLAY_PLACED_JSON_SHA256 =
            "3ab941ff28b42e30fcd3d7b7994ad943326c6f46661ffc98a4183d5ee29cceee";
    public static final String LUSH_VEGETATION_PLACED_JSON_SHA256 =
            "ac75a94a568d04f30ea81d4ddc6ca353d9235c9e6bb84180c63f811ba7abb25e";
    public static final String SPORE_BLOSSOM_PLACED_JSON_SHA256 =
            "16aa6808e2d424fc1a67e4852e4336d25756a6a20c84c4738233025bcb75228d";

    private static final State PALE_MOSS = State.of("minecraft:pale_moss_block");
    private static final State MOSS = State.of("minecraft:moss_block");
    private static final State CLAY = State.of("minecraft:clay");
    private static final State WATER = new State("minecraft:water", Map.of(),
            false, false, false, false, false, true, 0);
    private static final State SPORE = State.of("minecraft:spore_blossom");
    private static final State[] PALE_VEGETATION = weighted(
            State.of("minecraft:pale_moss_carpet")
                    .property("bottom", "true")
                    .property("east", "none")
                    .property("north", "none")
                    .property("south", "none")
                    .property("west", "none"), 25,
            State.of("minecraft:short_grass"), 25,
            State.of("minecraft:tall_grass").property("half", "lower"), 10);
    private static final State[] MOSS_VEGETATION = weighted(
            State.of("minecraft:flowering_azalea"), 4,
            State.of("minecraft:azalea"), 7,
            State.of("minecraft:moss_carpet"), 25,
            State.of("minecraft:short_grass"), 50,
            State.of("minecraft:tall_grass").property("half", "lower"), 10);
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public boolean enabled() { return false; }
        @Override public void record(String phase, long... values) {
            throw new AssertionError("disabled cave/root trace emitted");
        }
    };

    private Mc263CaveRootVegetationFeature() { }

    public enum Heightmap { MOTION_BLOCKING_NO_LEAVES }
    public enum NestedFeature { DRIPLEAF }
    private enum Surface { FLOOR(0, -1), CEILING(0, 1);
        final int outwardY, inwardY;
        Surface(int outwardY, int inwardY) { this.outwardY = outwardY; this.inwardY = inwardY; }
    }

    /** Minimal exact carrier. Boolean facts are the official predicates, not inferred aliases. */
    public interface WorldAccess {
        int minGenerationY();
        int height(Heightmap type, int x, int z);
        String biomeKey(int x, int y, int z);
        State blockState(int x, int y, int z);
        boolean canSurvive(State state, int x, int y, int z);
        boolean supportsFeature(int globalIndex);
        boolean supportsNestedFeature(NestedFeature feature);
        boolean supportsState(State state);
        boolean trySetBlockState(int x, int y, int z, State state, int flags);
        boolean placeNestedFeature(NestedFeature feature, WorldgenRandom random,
                                   int x, int y, int z);
    }

    /** Exact identity plus the only predicate facts consumed by these configured features. */
    public record State(String block, Map<String, String> properties, boolean air,
                        boolean solid, boolean replaceable, boolean mossReplaceable,
                        boolean lushGroundReplaceable, boolean waterSource, int sturdyMask) {
        public State {
            block = key(block);
            properties = Collections.unmodifiableMap(new TreeMap<>(properties));
            if ((sturdyMask & ~63) != 0) throw new IllegalArgumentException("bad sturdy mask");
        }
        public static State of(String block) {
            return new State(block, Map.of(), false, false, false, false, false, false, 63);
        }
        public static State airState() {
            return new State("minecraft:air", Map.of(), true, false, true,
                    false, false, false, 0);
        }
        public State property(String name, String value) {
            Map<String, String> next = new TreeMap<>(properties);
            next.put(name, value);
            return new State(block, next, air, solid, replaceable, mossReplaceable,
                    lushGroundReplaceable, waterSource, sturdyMask);
        }
        public boolean sturdy(Direction face) { return (sturdyMask & 1 << face.ordinal()) != 0; }
        public String canonical() {
            if (properties.isEmpty()) return block;
            return block + properties.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                    .reduce("[", (a, b) -> a.equals("[") ? a + b : a + "," + b) + "]";
        }
    }

    public enum Direction { DOWN, UP, NORTH, SOUTH, WEST, EAST }
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

    public static Result placeWithFeatureRandom(int index, WorldgenRandom random,
                                                int sourceX, int sourceY, int sourceZ,
                                                WorldAccess world) {
        return placeWithFeatureRandom(index, random, sourceX, sourceY, sourceZ, world, NO_TRACE);
    }

    public static Result placeWithFeatureRandom(int index, WorldgenRandom random,
                                                int sourceX, int sourceY, int sourceZ,
                                                WorldAccess world, TraceSink trace) {
        Spec spec = require(index);
        if (random == null) throw new IllegalArgumentException("random is required");
        preflight(spec, world);
        Totals total = new Totals();
        emit(trace, "outer", index, spec.count);
        for (int attempt = 0; attempt < spec.count; attempt++) {
            int x = sourceX + drawInt(random, 16, trace);
            int z = sourceZ + drawInt(random, 16, trace);
            int y;
            if (index == 17) {
                y = world.height(Heightmap.MOTION_BLOCKING_NO_LEAVES, x, z);
                emit(trace, "heightmap", index, x, z, y);
            } else {
                y = world.minGenerationY()
                        + drawInt(random, 256 - world.minGenerationY() + 1, trace);
                emit(trace, "height", index, x, z, y);
                Integer scanned = scan(world, x, y, z, spec.scanUp, spec.sturdyTarget, trace);
                if (scanned == null) continue;
                y = scanned + (spec.scanUp ? -1 : 1);
                emit(trace, "offset", index, x, y, z);
            }
            total.candidates++;
            boolean biome = biomeContains(index, world.biomeKey(x, y, z));
            emit(trace, "biome", index, x, y, z, biome ? 1 : 0);
            if (!biome) continue;
            total.configuredCalls++;
            Counts placed = configured(spec, random, world, x, y, z, trace);
            if (placed.success) total.successes++;
            total.writes += placed.writes;
            total.retained += placed.retained;
        }
        emit(trace, "result", index, total.candidates, total.configuredCalls, total.successes,
                total.writes, total.retained);
        return total.result();
    }

    public static Result placeConfigured(int index, WorldgenRandom random, int x, int y, int z,
                                         WorldAccess world, TraceSink trace) {
        Spec spec = require(index);
        if (random == null) throw new IllegalArgumentException("random is required");
        preflight(spec, world);
        Counts placed = configured(spec, random, world, x, y, z, trace);
        return new Result(placed.success, 1, 1, placed.success ? 1 : 0,
                placed.writes, placed.retained);
    }

    private static Counts configured(Spec spec, WorldgenRandom random, WorldAccess world,
                                     int x, int y, int z, TraceSink trace) {
        if (spec.index == 31) return caveVine(random, world, x, y, z, false, trace);
        if (spec.index == 35) return simple(world, SPORE, x, y, z, trace);
        if (spec.index == 32) {
            boolean normal = drawBoolean(random, trace);
            return vegetationPatch(spec, random, world, x, y, z, !normal, trace);
        }
        return vegetationPatch(spec, random, world, x, y, z, false, trace);
    }

    private static Counts vegetationPatch(Spec base, WorldgenRandom random, WorldAccess world,
                                          int x, int y, int z, boolean waterlogged,
                                          TraceSink trace) {
        Spec spec = waterlogged ? base.waterlogged() : base;
        int rx = sampleRadius(spec, random, trace) + 1;
        int rz = sampleRadius(spec, random, trace) + 1;
        Set<Pos> surface = new HashSet<>();
        Counts counts = new Counts();
        for (int dx = -rx; dx <= rx; dx++) {
            boolean xEdge = dx == -rx || dx == rx;
            for (int dz = -rz; dz <= rz; dz++) {
                boolean zEdge = dz == -rz || dz == rz;
                boolean corner = xEdge && zEdge;
                boolean edge = (xEdge || zEdge) && !corner;
                if (corner || edge && (spec.edgeChance == 0.0f
                        || drawFloat(random, trace) > spec.edgeChance)) continue;
                int py = findSurface(world, x + dx, y, z + dz, spec.surface, trace);
                State air = read(world, x + dx, py, z + dz, trace);
                int gy = py + spec.surface.inwardY;
                State ground = read(world, x + dx, gy, z + dz, trace);
                Direction face = spec.surface == Surface.FLOOR ? Direction.UP : Direction.DOWN;
                if (!air.air || !ground.sturdy(face)) continue;
                int depth = sampleDepth(spec, random, trace);
                if (spec.extraBottomChance > 0 && drawFloat(random, trace) < spec.extraBottomChance) {
                    depth++;
                }
                boolean groundPlaced = true;
                int cy = gy;
                for (int d = 0; d < depth; d++, cy += spec.surface.inwardY) {
                    State old = read(world, x + dx, cy, z + dz, trace);
                    if (old.block.equals(spec.ground.block)) continue;
                    if (!replaceable(spec, old)) {
                        groundPlaced = d != 0;
                        break;
                    }
                    counts.writes++;
                    if (write(world, x + dx, cy, z + dz, spec.ground, trace)) counts.retained++;
                }
                if (groundPlaced) surface.add(new Pos(x + dx, gy, z + dz));
            }
        }
        if (waterlogged) {
            Set<Pos> enclosed = new HashSet<>();
            for (Pos pos : surface) if (!exposed(world, pos, trace)) enclosed.add(pos);
            for (Pos pos : enclosed) {
                counts.writes++;
                if (write(world, pos.x, pos.y, pos.z, WATER, trace)) counts.retained++;
            }
            surface = enclosed;
        }
        for (Pos pos : surface) {
            if (!(spec.vegetationChance > 0)
                    || !(drawFloat(random, trace) < spec.vegetationChance)) continue;
            Counts vegetation = vegetation(spec, random, world, pos, waterlogged, trace);
            counts.add(vegetation);
        }
        counts.success = !surface.isEmpty();
        return counts;
    }

    private static Counts vegetation(Spec spec, WorldgenRandom random, WorldAccess world, Pos ground,
                                     boolean waterlogged, TraceSink trace) {
        int y = ground.y - spec.surface.inwardY;
        if (spec.index == 30) return caveVine(random, world, ground.x, y, ground.z, true, trace);
        if (spec.index == 32) {
            int targetY = waterlogged ? y - 1 : y;
            boolean placed = world.placeNestedFeature(NestedFeature.DRIPLEAF, random,
                    ground.x, targetY, ground.z);
            emit(trace, "nested", spec.index, ground.x, targetY, ground.z, placed ? 1 : 0);
            return new Counts(placed, 0, 0);
        }
        State[] provider = spec.index == 17 ? PALE_VEGETATION : MOSS_VEGETATION;
        State selected = provider[drawInt(random, provider.length, trace)];
        if (!world.canSurvive(selected, ground.x, y, ground.z)) return new Counts(false, 0, 0);
        if (selected.block.equals("minecraft:tall_grass")) {
            State above = read(world, ground.x, y + 1, ground.z, trace);
            if (!above.air && !above.replaceable) return new Counts(false, 0, 0);
            boolean a = write(world, ground.x, y, ground.z,
                    selected.property("half", "lower"), trace);
            boolean b = write(world, ground.x, y + 1, ground.z,
                    selected.property("half", "upper"), trace);
            return new Counts(true, 2, (a ? 1 : 0) + (b ? 1 : 0));
        }
        return new Counts(true, 1, write(world, ground.x, y, ground.z, selected, trace) ? 1 : 0);
    }

    private static Counts caveVine(WorldgenRandom random, WorldAccess world, int x, int y, int z,
                                   boolean shortColumn, TraceSink trace) {
        int pick = drawInt(random, shortColumn ? 6 : 15, trace);
        int body;
        if (shortColumn) body = pick < 5 ? drawInt(random, 4, trace) : 1 + drawInt(random, 7, trace);
        else if (pick < 2) body = drawInt(random, 20, trace);
        else if (pick < 5) body = drawInt(random, 3, trace);
        else body = drawInt(random, 7, trace);
        int[] heights = {body, 1};
        int total = body + 1;
        int permitted = total;
        for (int step = 0; step < total; step++) {
            State next = read(world, x, y - step - 1, z, trace);
            if (!next.air) { permitted = step; break; }
        }
        int remove = total - permitted;
        int fromBody = Math.min(body, remove);
        heights[0] -= fromBody;
        heights[1] -= remove - fromBody;
        int writes = 0, retained = 0, py = y;
        for (int n = 0; n < heights[0]; n++, py--) {
            boolean berries = drawInt(random, 5, trace) == 4;
            State state = State.of("minecraft:cave_vines_plant")
                    .property("berries", Boolean.toString(berries));
            writes++; if (write(world, x, py, z, state, trace)) retained++;
        }
        if (heights[1] != 0) {
            boolean berries = drawInt(random, 5, trace) == 4;
            int age = 23 + drawInt(random, 3, trace);
            State head = State.of("minecraft:cave_vines")
                    .property("age", Integer.toString(age))
                    .property("berries", Boolean.toString(berries));
            writes++; if (write(world, x, py, z, head, trace)) retained++;
        }
        return new Counts(total != 0, writes, retained);
    }

    private static Counts simple(WorldAccess world, State state, int x, int y, int z,
                                 TraceSink trace) {
        if (!world.canSurvive(state, x, y, z)) return new Counts(false, 0, 0);
        return new Counts(true, 1, write(world, x, y, z, state, trace) ? 1 : 0);
    }

    private static Integer scan(WorldAccess world, int x, int y, int z, boolean up,
                                boolean sturdyTarget, TraceSink trace) {
        State initial = read(world, x, y, z, trace);
        if (!initial.air) return null;
        int py = y;
        for (int step = 0; step < 12; step++) {
            State state = read(world, x, py, z, trace);
            boolean target = sturdyTarget
                    ? state.sturdy(up ? Direction.DOWN : Direction.UP) : state.solid;
            if (target) return py;
            py += up ? 1 : -1;
            if (py < world.minGenerationY() || py > 319) return null;
            if (!read(world, x, py, z, trace).air) break;
        }
        State last = read(world, x, py, z, trace);
        return (sturdyTarget ? last.sturdy(up ? Direction.DOWN : Direction.UP) : last.solid)
                ? py : null;
    }

    private static int findSurface(WorldAccess world, int x, int y, int z, Surface surface,
                                   TraceSink trace) {
        int py = y, moved = 0;
        while (read(world, x, py, z, trace).air && moved++ < 5) py += surface.inwardY;
        moved = 0;
        while (!read(world, x, py, z, trace).air && moved++ < 5) py -= surface.inwardY;
        return py;
    }

    private static boolean exposed(WorldAccess world, Pos pos, TraceSink trace) {
        return !read(world, pos.x, pos.y, pos.z - 1, trace).sturdy(Direction.SOUTH)
                || !read(world, pos.x + 1, pos.y, pos.z, trace).sturdy(Direction.WEST)
                || !read(world, pos.x, pos.y, pos.z + 1, trace).sturdy(Direction.NORTH)
                || !read(world, pos.x - 1, pos.y, pos.z, trace).sturdy(Direction.EAST)
                || !read(world, pos.x, pos.y - 1, pos.z, trace).sturdy(Direction.UP);
    }

    private static boolean replaceable(Spec spec, State state) {
        return spec.index == 32 ? state.lushGroundReplaceable : state.mossReplaceable;
    }

    private static int sampleRadius(Spec spec, WorldgenRandom random, TraceSink trace) {
        return spec.radiusMin == spec.radiusMax ? spec.radiusMin
                : spec.radiusMin + drawInt(random, spec.radiusMax - spec.radiusMin + 1, trace);
    }
    private static int sampleDepth(Spec spec, WorldgenRandom random, TraceSink trace) {
        return spec.depthMin == spec.depthMax ? spec.depthMin
                : spec.depthMin + drawInt(random, spec.depthMax - spec.depthMin + 1, trace);
    }

    public static byte[] encodeTraceFixture(List<TraceEvent> events) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(TRACE_MAGIC); out.writeShort(TRACE_VERSION);
            byte[] schema = TRACE_SCHEMA.getBytes(StandardCharsets.UTF_8);
            out.writeShort(schema.length); out.write(schema); out.writeInt(events.size());
            for (TraceEvent event : events) {
                byte[] phase = event.phase.getBytes(StandardCharsets.UTF_8);
                out.writeByte(phase.length); out.write(phase);
                out.writeByte(event.values.length);
                for (long value : event.values) out.writeLong(value);
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }

    public static List<Integer> coveredIndices() { return List.of(17, 30, 31, 32, 33, 35); }

    /** Pure exact capability/state closure check for one placed feature. */
    public static void preflight(int index, WorldAccess world) {
        preflight(require(index), world);
    }

    private static Spec require(int index) {
        return switch (index) {
            case 17 -> new Spec(17, "minecraft:pale_moss_patch", 1, false, false,
                    Surface.FLOOR, PALE_MOSS, 1, 1, 0, 5, .3f, 2, 4, .75f);
            case 30 -> new Spec(30, "minecraft:lush_caves_ceiling_vegetation", 125, true, false,
                    Surface.CEILING, MOSS, 1, 2, 0, 5, .08f, 4, 7, .3f);
            case 31 -> new Spec(31, "minecraft:cave_vines", 188, true, true,
                    Surface.CEILING, MOSS, 1, 1, 0, 1, 0, 1, 1, 0);
            case 32 -> new Spec(32, "minecraft:lush_caves_clay", 62, false, false,
                    Surface.FLOOR, CLAY, 3, 3, .8f, 2, .05f, 4, 7, .7f);
            case 33 -> new Spec(33, "minecraft:lush_caves_vegetation", 125, false, false,
                    Surface.FLOOR, MOSS, 1, 1, 0, 5, .8f, 4, 7, .3f);
            case 35 -> new Spec(35, "minecraft:spore_blossom", 25, true, false,
                    Surface.CEILING, MOSS, 1, 1, 0, 1, 0, 1, 1, 0);
            default -> throw new IllegalArgumentException("unsupported step-9 cave/root index: " + index);
        };
    }

    private static void preflight(Spec spec, WorldAccess world) {
        if (world == null) throw new IllegalArgumentException("world is required");
        if (!world.supportsFeature(spec.index)) throw new IllegalStateException("missing feature capability " + spec.index);
        List<State> states = new ArrayList<>(List.of(spec.ground));
        if (spec.index == 17) Collections.addAll(states, PALE_VEGETATION);
        if (spec.index == 30 || spec.index == 31) {
            for (boolean berries : new boolean[]{false, true}) {
                states.add(State.of("minecraft:cave_vines_plant")
                        .property("berries", Boolean.toString(berries)));
                for (int age = 23; age <= 25; age++) {
                    states.add(State.of("minecraft:cave_vines").property("age", Integer.toString(age))
                            .property("berries", Boolean.toString(berries)));
                }
            }
        }
        if (spec.index == 32) {
            states.add(WATER);
            if (!world.supportsNestedFeature(NestedFeature.DRIPLEAF)) {
                throw new IllegalStateException("missing exact dripleaf capability");
            }
        }
        if (spec.index == 33) Collections.addAll(states, MOSS_VEGETATION);
        if (spec.index == 35) states.add(SPORE);
        if (spec.index == 17 || spec.index == 33) {
            states.add(State.of("minecraft:tall_grass").property("half", "upper"));
        }
        for (State state : states) if (!world.supportsState(state)) {
            throw new IllegalStateException("missing exact state: " + state.canonical());
        }
    }

    private static boolean biomeContains(int index, String biome) {
        String key = key(biome);
        return Mc263FeatureIndexReceipt.biome(key).featuresAtStep(STEP).stream()
                .anyMatch(ref -> ref.globalIndex() == index);
    }
    private static State read(WorldAccess world, int x, int y, int z, TraceSink trace) {
        State state = world.blockState(x, y, z);
        if (state == null) throw new IllegalStateException("missing exact state fact");
        emit(trace, "read", x, y, z, state.canonical().hashCode());
        return state;
    }
    private static boolean write(WorldAccess world, int x, int y, int z, State state,
                                 TraceSink trace) {
        if (!world.supportsState(state)) throw new IllegalStateException("missing state " + state.canonical());
        boolean kept = world.trySetBlockState(x, y, z, state, 2);
        emit(trace, "write", x, y, z, state.canonical().hashCode(), kept ? 1 : 0);
        return kept;
    }
    private static int drawInt(WorldgenRandom random, int bound, TraceSink trace) {
        int value = random.nextInt(bound); emit(trace, "rng_int", bound, value); return value;
    }
    private static float drawFloat(WorldgenRandom random, TraceSink trace) {
        float value = random.nextFloat(); emit(trace, "rng_float", Float.floatToRawIntBits(value)); return value;
    }
    private static boolean drawBoolean(WorldgenRandom random, TraceSink trace) {
        boolean value = random.nextBoolean(); emit(trace, "rng_bool", value ? 1 : 0); return value;
    }
    private static void emit(TraceSink trace, String phase, long... values) {
        if (trace != null && trace.enabled()) trace.record(phase, values);
    }
    private static String key(String value) {
        if (value == null || !value.matches("minecraft:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("invalid key: " + value);
        return value;
    }
    private static State[] weighted(Object... entries) {
        List<State> out = new ArrayList<>();
        for (int i = 0; i < entries.length; i += 2) {
            State state = (State) entries[i]; int weight = (Integer) entries[i + 1];
            for (int n = 0; n < weight; n++) out.add(state);
        }
        return out.toArray(State[]::new);
    }

    private record Pos(int x, int y, int z) {
        @Override public int hashCode() { return (y + z * 31) * 31 + x; }
    }
    private record Spec(int index, String key, int count, boolean scanUp, boolean sturdyTarget,
                        Surface surface, State ground, int depthMin, int depthMax,
                        float extraBottomChance, int verticalRange, float vegetationChance,
                        int radiusMin, int radiusMax, float edgeChance) {
        Spec waterlogged() { return new Spec(index, key, count, scanUp, sturdyTarget, surface,
                ground, 3, 3, .8f, 5, .1f, 4, 7, .7f); }
    }
    private static final class Counts {
        boolean success; int writes; int retained;
        Counts() { }
        Counts(boolean success, int writes, int retained) {
            this.success = success; this.writes = writes; this.retained = retained;
        }
        void add(Counts other) { writes += other.writes; retained += other.retained; }
    }
    private static final class Totals {
        int candidates, configuredCalls, successes, writes, retained;
        Result result() { return new Result(successes > 0, candidates, configuredCalls,
                successes, writes, retained); }
    }
}
