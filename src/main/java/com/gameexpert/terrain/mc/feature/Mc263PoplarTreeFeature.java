package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263WorldgenRandomSource;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Exact dormant poplar TreeFeature/FallenTreeFeature kernel for 26.3-snapshot-7. */
public final class Mc263PoplarTreeFeature {
    private static final String FALLEN_KEY = "minecraft:fallen_poplar_tree";
    public static final int TRACE_MAGIC = 0x50544633; // PTF3
    public static final int TRACE_VERSION = 1;
    public static final String TRACE_SCHEMA = "mc263-poplar-tree-trace-v1";
    public static final String INNER_SERVER_SHA1 = "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String POPLAR_TRUNK_CLASS_SHA256 =
            "48b650e31a3137f1ece0fd0fc627d3eff4fb5d5c110ba79d5b24e0ab40eed809";
    public static final String POPLAR_FOLIAGE_CLASS_SHA256 =
            "3a032d6e01d8eafc9e78eddbde880717302e083c8dc2b3e7add83a8c02910313";
    public static final String SHELF_DECORATOR_CLASS_SHA256 =
            "acdcc537970d02a4d3f509380a0c3b80f84e7564057ba1e6933ebaae1f706d6a";
    public static final String FALLEN_TREE_CLASS_SHA256 =
            "00558d33ec9dbcca91217cfa22e72a5bc835cc4d86742e0b42d4f943758ae16f";
    public static final String ATTACHED_LOG_DECORATOR_CLASS_SHA256 =
            "b61ebe8058cab03c239ad71a4a2d0df284df7156dd2bd98d16c7e78554160058";
    public static final String PLACE_ON_GROUND_CLASS_SHA256 =
            "b6be44e50905f1b977bcba93646b14ce166989e9eeebfac66cbc460c0a3fc546";
    public static final Map<String, String> CONFIGURED_JSON_SHA256 = Map.ofEntries(
            Map.entry("minecraft:red_poplar",
                    "0197aa9e82513c0f432f2a7b81ab052f1d4b419040a3bc0e87b53e9a71bf453b"),
            Map.entry("minecraft:orange_poplar",
                    "01b79539faa6aa2b48ef2cd1c945c930c526de4ccb9fcea0058ad998a6843cc9"),
            Map.entry("minecraft:yellow_poplar",
                    "c24ccf851ffd252c0a610ff65635165fdecb52be0b711c1139b9fbd7ae5fd2a4"),
            Map.entry("minecraft:red_poplar_leaf_litter",
                    "c5fc0663109c2023315b2e17b7f919f7ffad158eb59fcd17bb3d651d37126a18"),
            Map.entry("minecraft:orange_poplar_leaf_litter",
                    "d816b1a9daf23c216cfcb8cbfab9df89e0031b42a5cc3b7f69fe68b75681cd6c"),
            Map.entry("minecraft:yellow_poplar_leaf_litter",
                    "267066c4b97d7957e72d28ffc2fbfda6cd1c866d565e5f5be21147b61d74b184"),
            Map.entry(FALLEN_KEY,
                    "1356a643145ac7ceb1763156042ac7f5ca4a8c008c9a07038b51a707e0532bd5"));

    private static final Map<String, Spec> SPECS = specs();
    private static final Map<String, Phase> PHASES = Map.ofEntries(
            Map.entry("preflight", new Phase(1, 2)),
            Map.entry("rng_int", new Phase(2, 3)),
            Map.entry("rng_float", new Phase(3, 3)),
            Map.entry("rng_bool", new Phase(4, 2)),
            Map.entry("read", new Phase(5, 9)),
            Map.entry("predicate", new Phase(6, 6)),
            Map.entry("write", new Phase(7, 8)),
            Map.entry("shape", new Phase(8, 8)),
            Map.entry("decorator", new Phase(9, 6)),
            Map.entry("finish", new Phase(10, 6)),
            Map.entry("result", new Phase(11, 6)));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public boolean enabled() { return false; }
        @Override public void record(String phase, long... values) { throw new AssertionError(); }
    };

    private Mc263PoplarTreeFeature() { }

    public record Pos(int x, int y, int z) {
        public Pos offset(int dx, int dy, int dz) { return new Pos(x + dx, y + dy, z + dz); }
        @Override public int hashCode() { return (y + z * 31) * 31 + x; }
    }

    public record State(String block, Map<String, String> properties, boolean air,
            boolean replaceableByTrees, boolean replaceable, boolean log, boolean leaves,
            boolean persistent,
            boolean waterSource, boolean solidRender) {
        public State {
            block = key(block);
            properties = Collections.unmodifiableMap(new TreeMap<>(properties));
        }
        public static State of(String block) {
            return new State(block, Map.of(), false, false, false, false, false, false,
                    false, true);
        }
        public State property(String name, String value) {
            Map<String, String> next = new TreeMap<>(properties);
            next.put(name, value);
            return new State(block, next, air, replaceableByTrees, replaceable, log, leaves,
                    persistent, waterSource, solidRender);
        }
        public String canonical() {
            if (properties.isEmpty()) return block;
            return block + properties.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .reduce("[", (left, right) -> left.equals("[") ? left + right
                            : left + "," + right) + "]";
        }
    }

    public interface WorldAccess {
        int minY();
        int maxY();
        State state(Pos pos);
        boolean cannotReplaceBelowTreeTrunk(State state);
        boolean canSaplingSurvive(State sapling, Pos pos);
        boolean supportsFeature(String configuredKey);
        boolean supportsState(State state);
        boolean supportsTreeFinalization();
        boolean supportsPostProcessing();
        boolean set(Pos pos, State state, int flags);
        boolean setAndUpdate(Pos pos, State state);
        void markAboveForPostProcessing(Pos pos);
        boolean sturdyUp(Pos below, Pos queriedFrom);
        int motionBlockingNoLeaves(Pos pos);
        boolean waterSource(Pos pos);
        void finishTree(Set<Pos> logs, Set<Pos> leaves, Set<Pos> roots,
                Set<Pos> decorations);
    }

    public interface TraceSink {
        default boolean enabled() { return true; }
        void record(String phase, long... values);
    }

    public record TraceEvent(String phase, long[] values) {
        public TraceEvent { values = values.clone(); }
        @Override public long[] values() { return values.clone(); }
    }

    public record Result(boolean placed, int reads, int writes, int retained,
            int decorations, int postprocess) { }

    public static List<String> configuredKeys() {
        return SPECS.keySet().stream().sorted().toList();
    }

    public static String fallenKey() { return FALLEN_KEY; }

    /** Validates the complete configured-tree capability closure without live world access. */
    public static void preflightConfigured(String configuredKey, WorldAccess world) {
        preflightTree(requireSpec(configuredKey), null, world);
    }

    /** Validates configured-tree and checked-sapling capabilities without live world access. */
    public static void preflightChecked(String configuredKey, State sapling, WorldAccess world) {
        preflightTree(requireSpec(configuredKey), sapling, world);
    }

    /** Validates the complete fallen-tree capability closure without live world access. */
    public static void preflightFallen(WorldAccess world) {
        preflightFallenInternal(world);
    }

    public static Result placeChecked(String configuredKey, State sapling, Mc263WorldgenRandomSource random,
            Pos origin, WorldAccess world, TraceSink trace) {
        Spec spec = requireSpec(configuredKey);
        preflightChecked(configuredKey, sapling, world);
        emit(trace, "preflight", id(spec.key), 1);
        boolean survives = world.canSaplingSurvive(sapling, origin);
        emit(trace, "predicate", id(spec.key), origin.x, origin.y, origin.z,
                survives ? 1 : 0, 1);
        if (!survives) return new Result(false, 0, 0, 0, 0, 0);
        return placeTree(spec, random, origin, world, trace);
    }

    public static Result place(String configuredKey, Mc263WorldgenRandomSource random, Pos origin,
            WorldAccess world) {
        return place(configuredKey, random, origin, world, NO_TRACE);
    }

    public static Result place(String configuredKey, Mc263WorldgenRandomSource random, Pos origin,
            WorldAccess world, TraceSink trace) {
        Spec spec = requireSpec(configuredKey);
        preflightConfigured(configuredKey, world);
        emit(trace, "preflight", id(spec.key), 0);
        return placeTree(spec, random, origin, world, trace);
    }

    public static Result placeFallen(Mc263WorldgenRandomSource random, Pos origin, WorldAccess world,
            TraceSink trace) {
        preflightFallen(world);
        emit(trace, "preflight", id(FALLEN_KEY), 0);
        Counts counts = new Counts();
        Set<Pos> stump = new HashSet<>();
        writeUpdate(world, origin, log("y"), trace, counts, stump);

        Direction direction = Direction.HORIZONTAL[nextInt(random, 4, 700, trace)];
        int length = 4 + nextInt(random, 4, 701, trace) - 2;
        int distance = 2 + nextInt(random, 2, 702, trace);
        Pos start = origin.offset(direction.dx * distance, 1, direction.dz * distance);
        for (int attempt = 0; attempt < 6; attempt++) {
            State current = read(world, start, trace, counts);
            boolean mayPlace = valid(current)
                    && world.sturdyUp(start.offset(0, -1, 0), start);
            emit(trace, "predicate", id(FALLEN_KEY), start.x, start.y, start.z,
                    mayPlace ? 1 : 0, 2);
            if (mayPlace) break;
            start = start.offset(0, -1, 0);
        }

        Pos cursor = start;
        int gap = 0;
        boolean canPlace = true;
        for (int index = 0; index < length; index++) {
            State current = read(world, cursor, trace, counts);
            if (!valid(current)) {
                canPlace = false;
                break;
            }
            if (!world.sturdyUp(cursor.offset(0, -1, 0), cursor)) {
                gap++;
                if (gap > 2) {
                    canPlace = false;
                    break;
                }
            } else {
                gap = 0;
            }
            cursor = cursor.offset(direction.dx, 0, direction.dz);
        }

        Set<Pos> fallen = new HashSet<>();
        if (canPlace) {
            cursor = start;
            for (int index = 0; index < length; index++) {
                writeUpdate(world, cursor, log(direction.axis), trace, counts, fallen);
                cursor = cursor.offset(direction.dx, 0, direction.dz);
            }
            List<Pos> logs = ordered(fallen);
            attachedBrownMushrooms(logs, random, world, trace, counts);
            shelfMushrooms(logs, true, .8f, random, world, trace, counts, null);
        }
        return result(true, counts, trace, FALLEN_KEY);
    }

    private static Result placeTree(Spec spec, Mc263WorldgenRandomSource random, Pos origin,
            WorldAccess world, TraceSink trace) {
        Counts counts = new Counts();
        int height = 7 + nextInt(random, 5, 100, trace)
                + nextInt(random, 1, 101, trace);
        int foliageHeight = 5 + nextInt(random, 2, 102, trace);
        int radiusPick = nextInt(random, 12, 103, trace);
        int foliageRadius = radiusPick < 5 ? 5 : radiusPick < 10 ? 6 : radiusPick == 10 ? 7 : 8;
        emit(trace, "shape", id(spec.key), height, foliageHeight, foliageRadius,
                origin.x, origin.y, origin.z, 0);
        if (origin.y < world.minY() + 1 || origin.y + height + 1 > world.maxY() + 1) {
            return result(false, counts, trace, spec.key);
        }
        int clippedHeight = maxFreeHeight(height, origin, world, trace, counts);
        if (clippedHeight < height) return result(false, counts, trace, spec.key);

        Set<Pos> logs = new HashSet<>();
        Set<Pos> leaves = new HashSet<>();
        Set<Pos> decorations = new HashSet<>();
        Pos below = origin.offset(0, -1, 0);
        State belowState = read(world, below, trace, counts);
        if (!world.cannotReplaceBelowTreeTrunk(belowState)) {
            write(world, below, State.of("minecraft:dirt"), 19, trace, counts, logs);
        }

        int attachmentY = clippedHeight - 4;
        for (int y = 0; y < clippedHeight; y++) {
            placeLogIfValid(origin.offset(0, y, 0), "y", world, trace, counts, logs);
            List<Direction> branches = shuffledHorizontalFromAllSix(random, trace);
            if (attachmentY - 1 == y) {
                int branchAmount = 1 + nextInt(random, 4, 120, trace);
                for (int branch = 0; branch < branchAmount; branch++) {
                    Direction direction = branches.get(branch);
                    placeLogIfValid(origin.offset(direction.dx, y, direction.dz),
                            direction.axis, world, trace, counts, logs);
                }
            }
        }

        Pos attachment = origin.offset(0, attachmentY, 0);
        createFoliage(spec, attachment, foliageHeight, foliageRadius, random, world,
                trace, counts, leaves);
        if (logs.isEmpty() && leaves.isEmpty()) return result(false, counts, trace, spec.key);

        List<Pos> orderedLogs = ordered(logs);
        if (spec.leafLitter) {
            placeOnGround(orderedLogs, 96, 4, 2, 3, random, world, trace,
                    counts, decorations);
            placeOnGround(orderedLogs, 150, 2, 1, 4, random, world, trace,
                    counts, decorations);
        }
        shelfMushrooms(orderedLogs, false, .4f, random, world, trace, counts,
                decorations);
        // Preserve the source HashSet walk; Set.copyOf applies a per-JVM encounter-order salt.
        world.finishTree(Collections.unmodifiableSet(new LinkedHashSet<>(logs)),
                Collections.unmodifiableSet(new LinkedHashSet<>(leaves)), Set.of(),
                Collections.unmodifiableSet(new LinkedHashSet<>(decorations)));
        emit(trace, "finish", logs.size(), leaves.size(), decorations.size(),
                counts.writes, counts.retained, 0);
        return result(true, counts, trace, spec.key);
    }

    private static int maxFreeHeight(int height, Pos origin, WorldAccess world,
            TraceSink trace, Counts counts) {
        for (int y = 0; y <= height + 1; y++) {
            int radius = y < 1 ? 0 : 2;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    Pos pos = origin.offset(x, y, z);
                    State state = read(world, pos, trace, counts);
                    if (!valid(state) && !read(world, pos, trace, counts).log) return y - 2;
                }
            }
        }
        return height;
    }

    private static void createFoliage(Spec spec, Pos attachment, int foliageHeight,
            int sampledRadius, Mc263WorldgenRandomSource random, WorldAccess world, TraceSink trace,
            Counts counts, Set<Pos> leaves) {
        int radius = sampledRadius - 1;
        boolean diagonal = nextBoolean(random, 200, trace);
        placeLeavesRow(spec, attachment, radius - 2, foliageHeight - 1, foliageHeight,
                diagonal, random, world, trace, counts, leaves);
        placeLeavesRow(spec, attachment, radius - 1, foliageHeight - 2, foliageHeight,
                diagonal, random, world, trace, counts, leaves);
        placeLeavesRow(spec, attachment, radius - 1, foliageHeight - 3, foliageHeight,
                diagonal, random, world, trace, counts, leaves);
        for (int y = foliageHeight - 4; y >= 1; y--) {
            placeLeavesRow(spec, attachment, radius, y, foliageHeight, diagonal,
                    random, world, trace, counts, leaves);
        }
        replaceLeavesWithLog(spec, attachment, radius, foliageHeight - 4, foliageHeight,
                diagonal, random, world, trace, counts, leaves);
        placeLeavesRow(spec, attachment, radius - 1, 0, foliageHeight, diagonal,
                random, world, trace, counts, leaves);
        placeLeavesRow(spec, attachment, clamp(radius - 2, 1, 2), -1, foliageHeight,
                diagonal, random, world, trace, counts, leaves);
    }

    private static void placeLeavesRow(Spec spec, Pos center, int radius, int rowY,
            int foliageHeight, boolean diagonal, Mc263WorldgenRandomSource random, WorldAccess world,
            TraceSink trace, Counts counts, Set<Pos> leaves) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                boolean skip = shouldSkip(random, x, rowY, z, radius, foliageHeight,
                        diagonal, trace);
                emit(trace, "shape", center.x + x, center.y + rowY, center.z + z,
                        radius, rowY, foliageHeight, diagonal ? 1 : 0, skip ? 1 : 0);
                if (skip) continue;
                Pos pos = center.offset(x, rowY, z);
                State persistentState = read(world, pos, trace, counts);
                if (!persistentState.persistent && valid(read(world, pos, trace, counts))) {
                    boolean waterlogged = world.waterSource(pos);
                    emit(trace, "predicate", id(spec.key), pos.x, pos.y, pos.z,
                            waterlogged ? 1 : 0, 3);
                    write(world, pos, leaf(spec.leaf, 7, waterlogged), 19,
                            trace, counts, leaves);
                }
            }
        }
    }

    private static boolean shouldSkip(Mc263WorldgenRandomSource random, int x, int rowY, int z,
            int radius, int foliageHeight, boolean diagonal, TraceSink trace) {
        boolean partial = rowY == foliageHeight - 1 || rowY == foliageHeight - 2;
        boolean corner = diagonal ? leftTopOrRightLower(x, z) : leftLowerOrRightTop(x, z);
        int cut = corner ? radius - 1 : partial ? radius + 1 : radius;
        int absX = Math.abs(x);
        int absZ = Math.abs(z);
        if (partial && (absX == radius || absZ == radius)) return true;
        boolean hole = nextFloatInclusive(random, .15f, 210, trace);
        return absX + absZ > radius * 2 - cut - (hole ? 1 : 0);
    }

    private static void replaceLeavesWithLog(Spec spec, Pos center, int radius, int rowY,
            int foliageHeight, boolean diagonal, Mc263WorldgenRandomSource random, WorldAccess world,
            TraceSink trace, Counts counts, Set<Pos> foliageSet) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int absX = Math.abs(x);
                int absZ = Math.abs(z);
                boolean partial = rowY == foliageHeight - 1 || rowY == foliageHeight - 2;
                boolean corner = diagonal ? leftTopOrRightLower(x, z)
                        : leftLowerOrRightTop(x, z);
                int cut = corner ? radius - 1 : partial ? radius + 1 : radius;
                if (absX + absZ > radius * 2 - cut - 2) continue;
                if (!((absZ == 0 && radius - absX >= 4)
                        || (absX == 0 && radius - absZ >= 4))) continue;
                Pos pos = center.offset(x, rowY, z);
                State current = read(world, pos, trace, counts);
                if (current.canonical().equals(leaf(spec.leaf, 7, false).canonical())) {
                    write(world, pos, log(absZ == 0 ? "x" : "z"), 19,
                            trace, counts, foliageSet);
                }
            }
        }
    }

    private static void placeOnGround(List<Pos> logs, int tries, int radius, int height,
            int maxAmount, Mc263WorldgenRandomSource random, WorldAccess world, TraceSink trace,
            Counts counts, Set<Pos> decorations) {
        if (logs.isEmpty()) return;
        int minY = logs.getFirst().y;
        int minX = logs.getFirst().x;
        int maxX = minX;
        int minZ = logs.getFirst().z;
        int maxZ = minZ;
        for (Pos pos : logs) {
            if (pos.y != minY) continue;
            minX = Math.min(minX, pos.x);
            maxX = Math.max(maxX, pos.x);
            minZ = Math.min(minZ, pos.z);
            maxZ = Math.max(maxZ, pos.z);
        }
        emit(trace, "decorator", 1, tries, radius, height, maxAmount, logs.size());
        for (int attempt = 0; attempt < tries; attempt++) {
            int x = minX - radius + nextInt(random, maxX - minX + radius * 2 + 1,
                    300, trace);
            int y = minY - height + nextInt(random, height * 2 + 1, 301, trace);
            int z = minZ - radius + nextInt(random, maxZ - minZ + radius * 2 + 1,
                    302, trace);
            Pos base = new Pos(x, y, z);
            Pos above = base.offset(0, 1, 0);
            State aboveState = read(world, above, trace, counts);
            if (!(aboveState.air || aboveState.block.equals("minecraft:vine"))) continue;
            State baseState = read(world, base, trace, counts);
            if (!baseState.solidRender) continue;
            if (world.motionBlockingNoLeaves(base) > above.y) continue;
            int pick = nextInt(random, maxAmount * 4, 303, trace);
            int amount = 1 + pick / 4;
            Direction facing = Direction.HORIZONTAL[pick % 4];
            write(world, above, State.of("minecraft:leaf_litter")
                    .property("facing", facing.key)
                    .property("segment_amount", Integer.toString(amount)), 19,
                    trace, counts, decorations);
        }
    }

    private static void shelfMushrooms(List<Pos> logs, boolean fallen, float probability,
            Mc263WorldgenRandomSource random, WorldAccess world, TraceSink trace, Counts counts,
            Set<Pos> decorations) {
        emit(trace, "decorator", 2, logs.size(), fallen ? 1 : 0,
                Float.floatToRawIntBits(probability), 0, 0);
        if (!nextFloat(random, probability, 400, trace) || logs.isEmpty()) return;
        if (fallen) {
            Direction[] directions = logs.getFirst().x != logs.getLast().x
                    ? new Direction[]{Direction.N, Direction.S}
                    : new Direction[]{Direction.E, Direction.W};
            for (Pos log : logs) {
                for (Direction direction : directions) {
                    if (!nextFloatInclusive(random, .25f, 401, trace)) continue;
                    Pos mushroom = log.offset(direction.dx, 0, direction.dz);
                    if (!replaceableWithoutWater(mushroom, world, trace, counts)) continue;
                    if (hasAdjacentShelf(mushroom, world, trace, counts)
                            || hasAdjacentShelf(log, world, trace, counts)) continue;
                    placeShelf(mushroom, direction, random, world, trace, counts,
                            decorations);
                }
            }
            return;
        }
        Direction first = Direction.HORIZONTAL[nextInt(random, 4, 402, trace)];
        Direction[] directions = {first, first.clockwise()};
        int baseY = logs.getFirst().y;
        for (Pos log : logs) {
            if (log.y - baseY < 1 || log.y - baseY > 4) continue;
            for (Direction direction : directions) {
                if (!nextFloatInclusive(random, .25f, 403, trace)) continue;
                Pos mushroom = log.offset(direction.dx, 0, direction.dz);
                if (!replaceableWithoutWater(mushroom, world, trace, counts)) continue;
                if (read(world, mushroom.offset(0, -1, 0), trace, counts).block
                        .equals("minecraft:shelf_mushroom")) continue;
                placeShelf(mushroom, direction, random, world, trace, counts,
                        decorations);
                break;
            }
        }
    }

    private static void attachedBrownMushrooms(List<Pos> logs, Mc263WorldgenRandomSource random,
            WorldAccess world, TraceSink trace, Counts counts) {
        emit(trace, "decorator", 3, logs.size(), 1, Float.floatToRawIntBits(.1f), 0, 0);
        List<Pos> shuffled = new ArrayList<>(logs);
        shuffle(shuffled, random, 500, trace);
        for (Pos log : shuffled) {
            nextInt(random, 1, 501, trace);
            Pos above = log.offset(0, 1, 0);
            if (nextFloatInclusive(random, .1f, 502, trace)
                    && read(world, above, trace, counts).air) {
                writeDecoration(world, above, State.of("minecraft:brown_mushroom"),
                        trace, counts, null);
            }
        }
    }

    private static boolean replaceableWithoutWater(Pos pos, WorldAccess world, TraceSink trace,
            Counts counts) {
        State current = read(world, pos, trace, counts);
        if (!(current.air || current.replaceable)) return false;
        if (read(world, pos, trace, counts).block.equals("minecraft:water")) return false;
        for (Direction direction : Direction.WATER_NEIGHBOR_ORDER) {
            if (read(world, pos.offset(direction.dx, 0, direction.dz), trace, counts).block
                    .equals("minecraft:water")) return false;
        }
        return true;
    }

    private static boolean hasAdjacentShelf(Pos pos, WorldAccess world, TraceSink trace,
            Counts counts) {
        for (Direction direction : Direction.HORIZONTAL) {
            if (read(world, pos.offset(direction.dx, 0, direction.dz), trace, counts).block
                    .equals("minecraft:shelf_mushroom")) return true;
        }
        return false;
    }

    private static void placeShelf(Pos pos, Direction facing, Mc263WorldgenRandomSource random,
            WorldAccess world, TraceSink trace, Counts counts, Set<Pos> decorations) {
        State shelf = State.of("minecraft:shelf_mushroom")
                .property("age", Integer.toString(nextInt(random, 2, 410, trace)))
                .property("facing", facing.key);
        writeDecoration(world, pos, shelf, trace, counts, decorations);
    }

    private static List<Direction> shuffledHorizontalFromAllSix(Mc263WorldgenRandomSource random,
            TraceSink trace) {
        List<Direction> directions = new ArrayList<>(List.of(Direction.ALL_SIX));
        shuffle(directions, random, 600, trace);
        directions.removeIf(direction -> direction.axis.equals("y"));
        return directions;
    }

    private static void placeLogIfValid(Pos pos, String axis, WorldAccess world, TraceSink trace,
            Counts counts, Set<Pos> logs) {
        if (valid(read(world, pos, trace, counts))) {
            write(world, pos, log(axis), 19, trace, counts, logs);
        }
    }

    private static void preflightTree(Spec spec, State sapling, WorldAccess world) {
        Objects.requireNonNull(world, "world");
        if (!world.supportsFeature(spec.key)) throw new UnsupportedOperationException(spec.key);
        if (!world.supportsTreeFinalization()) {
            throw new UnsupportedOperationException("tree finalization");
        }
        List<State> required = new ArrayList<>(List.of(log("x"), log("y"), log("z"),
                State.of("minecraft:dirt")));
        for (int distance = 1; distance <= 7; distance++) {
            required.add(leaf(spec.leaf, distance, false));
            required.add(leaf(spec.leaf, distance, true));
        }
        for (Direction direction : Direction.HORIZONTAL) {
            for (int age = 0; age < 2; age++) {
                required.add(State.of("minecraft:shelf_mushroom")
                        .property("age", Integer.toString(age))
                        .property("facing", direction.key));
            }
        }
        if (spec.leafLitter) {
            for (Direction direction : Direction.HORIZONTAL) {
                for (int amount = 1; amount <= 4; amount++) {
                    required.add(State.of("minecraft:leaf_litter")
                            .property("facing", direction.key)
                            .property("segment_amount", Integer.toString(amount)));
                }
            }
        }
        if (sapling != null) required.add(sapling);
        requireStates(required, world);
    }

    private static void preflightFallenInternal(WorldAccess world) {
        Objects.requireNonNull(world, "world");
        if (!world.supportsFeature(FALLEN_KEY)) {
            throw new UnsupportedOperationException(FALLEN_KEY);
        }
        if (!world.supportsPostProcessing()) {
            throw new UnsupportedOperationException("postprocessing");
        }
        List<State> required = new ArrayList<>(List.of(log("x"), log("y"), log("z"),
                State.of("minecraft:brown_mushroom")));
        for (Direction direction : Direction.HORIZONTAL) {
            for (int age = 0; age < 2; age++) {
                required.add(State.of("minecraft:shelf_mushroom")
                        .property("age", Integer.toString(age))
                        .property("facing", direction.key));
            }
        }
        requireStates(required, world);
    }

    private static void requireStates(List<State> states, WorldAccess world) {
        for (State state : states) {
            if (!world.supportsState(state)) {
                throw new UnsupportedOperationException(state.canonical());
            }
        }
    }

    private static Spec requireSpec(String configuredKey) {
        Spec spec = SPECS.get(normalize(configuredKey));
        if (spec == null) throw new IllegalArgumentException(configuredKey);
        return spec;
    }

    private static Map<String, Spec> specs() {
        Map<String, Spec> specs = new LinkedHashMap<>();
        add(specs, "red_poplar", "red_poplar_leaves", false);
        add(specs, "orange_poplar", "orange_poplar_leaves", false);
        add(specs, "yellow_poplar", "yellow_poplar_leaves", false);
        add(specs, "red_poplar_leaf_litter", "red_poplar_leaves", true);
        add(specs, "orange_poplar_leaf_litter", "orange_poplar_leaves", true);
        add(specs, "yellow_poplar_leaf_litter", "yellow_poplar_leaves", true);
        return Collections.unmodifiableMap(specs);
    }

    private static void add(Map<String, Spec> specs, String name, String leaf, boolean litter) {
        String configuredKey = "minecraft:" + name;
        specs.put(configuredKey, new Spec(configuredKey, "minecraft:" + leaf, litter));
    }

    private static State read(WorldAccess world, Pos pos, TraceSink trace, Counts counts) {
        State state = world.state(pos);
        counts.reads++;
        emit(trace, "read", pos.x, pos.y, pos.z, id(state.canonical()), state.air ? 1 : 0,
                state.replaceableByTrees ? 1 : 0, state.replaceable ? 1 : 0,
                state.waterSource ? 1 : 0, state.leaves ? 1 : 0);
        return state;
    }

    private static void write(WorldAccess world, Pos pos, State state, int flags,
            TraceSink trace, Counts counts, Set<Pos> positions) {
        positions.add(pos);
        boolean retained = world.set(pos, state, flags);
        counts.writes++;
        if (retained) counts.retained++;
        emit(trace, "write", pos.x, pos.y, pos.z, id(state.canonical()), flags,
                retained ? 1 : 0, state.properties.hashCode(), id(state.block));
    }

    private static void writeDecoration(WorldAccess world, Pos pos, State state,
            TraceSink trace, Counts counts, Set<Pos> positions) {
        if (positions != null) positions.add(pos);
        boolean retained = world.set(pos, state, 19);
        counts.writes++;
        counts.decorations++;
        if (retained) counts.retained++;
        emit(trace, "write", pos.x, pos.y, pos.z, id(state.canonical()), 19,
                retained ? 1 : 0, state.properties.hashCode(), id(state.block));
    }

    private static void writeUpdate(WorldAccess world, Pos pos, State state, TraceSink trace,
            Counts counts, Set<Pos> positions) {
        positions.add(pos);
        boolean retained = world.setAndUpdate(pos, state);
        counts.writes++;
        if (retained) counts.retained++;
        world.markAboveForPostProcessing(pos);
        counts.postprocess++;
        emit(trace, "write", pos.x, pos.y, pos.z, id(state.canonical()), 3,
                retained ? 1 : 0, state.properties.hashCode(), id(state.block));
    }

    private static State log(String axis) {
        return new State("minecraft:poplar_log", Map.of("axis", axis), false, false,
                false, true, false, false, false, true);
    }

    private static State leaf(String block, int distance, boolean waterlogged) {
        return new State(block, Map.of("distance", Integer.toString(distance),
                "persistent", "false", "waterlogged", Boolean.toString(waterlogged)),
                false, true, false, false, true, false, waterlogged, false);
    }

    private static boolean valid(State state) {
        return state.air || state.replaceableByTrees;
    }

    private static List<Pos> ordered(Set<Pos> positions) {
        List<Pos> ordered = new ArrayList<>(positions);
        // TreeFeature.Context#getLogs preserves the HashSet encounter order for equal Y.
        // List.sort is stable, so the pinned JVM HashMap bucket order remains observable by
        // decorators that consume RNG once per log.
        ordered.sort(Comparator.comparingInt(Pos::y));
        return ordered;
    }

    private static void shuffle(List<?> values, Mc263WorldgenRandomSource random, int site,
            TraceSink trace) {
        for (int bound = values.size(); bound > 1; bound--) {
            int selected = nextInt(random, bound, site, trace);
            Collections.swap(values, bound - 1, selected);
        }
    }

    private static int nextInt(Mc263WorldgenRandomSource random, int bound, int site, TraceSink trace) {
        int value = random.nextInt(bound);
        emit(trace, "rng_int", bound, value, site);
        return value;
    }

    private static boolean nextBoolean(Mc263WorldgenRandomSource random, int site, TraceSink trace) {
        boolean value = random.nextBoolean();
        emit(trace, "rng_bool", value ? 1 : 0, site);
        return value;
    }

    private static boolean nextFloat(Mc263WorldgenRandomSource random, float chance, int site,
            TraceSink trace) {
        float value = random.nextFloat();
        emit(trace, "rng_float", Float.floatToRawIntBits(value),
                Float.floatToRawIntBits(chance), site);
        return value < chance;
    }

    private static boolean nextFloatInclusive(Mc263WorldgenRandomSource random, float chance, int site,
            TraceSink trace) {
        float value = random.nextFloat();
        emit(trace, "rng_float", Float.floatToRawIntBits(value),
                Float.floatToRawIntBits(chance), site);
        return value <= chance;
    }

    private static boolean leftLowerOrRightTop(int x, int z) {
        return (x > 0 && z < 0) || (z > 0 && x < 0);
    }

    private static boolean leftTopOrRightLower(int x, int z) {
        return (x > 0 && z > 0) || (z < 0 && x < 0);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static Result result(boolean placed, Counts counts, TraceSink trace, String key) {
        emit(trace, "result", id(key), placed ? 1 : 0, counts.reads, counts.writes,
                counts.retained, counts.decorations);
        return new Result(placed, counts.reads, counts.writes, counts.retained,
                counts.decorations, counts.postprocess);
    }

    public static byte[] encodeTrace(List<TraceEvent> events) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(TRACE_MAGIC);
            output.writeShort(TRACE_VERSION);
            byte[] schema = TRACE_SCHEMA.getBytes(StandardCharsets.UTF_8);
            output.writeShort(schema.length);
            output.write(schema);
            output.writeInt(events.size());
            for (TraceEvent event : events) {
                Phase phase = PHASES.get(event.phase);
                if (phase == null || event.values.length != phase.arity) {
                    throw new IllegalArgumentException(event.phase);
                }
                output.writeByte(phase.id);
                output.writeByte(phase.arity);
                for (long value : event.values) output.writeLong(value);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static String normalize(String value) {
        return value.startsWith("minecraft:") ? value : "minecraft:" + value;
    }

    private static String key(String value) {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException(value);
        }
        return value;
    }

    private static long id(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
            hash = (hash ^ (item & 255)) * 0x100000001b3L;
        }
        return hash;
    }

    private static void emit(TraceSink trace, String phase, long... values) {
        Objects.requireNonNull(trace, "trace");
        if (trace.enabled()) trace.record(phase, values);
    }

    private record Spec(String key, String leaf, boolean leafLitter) { }
    private record Phase(int id, int arity) { }
    private static final class Counts {
        int reads;
        int writes;
        int retained;
        int decorations;
        int postprocess;
    }

    private enum Direction {
        DOWN(0, 0, "down", "y"), UP(0, 0, "up", "y"),
        N(0, -1, "north", "z"), E(1, 0, "east", "x"),
        S(0, 1, "south", "z"), W(-1, 0, "west", "x");

        static final Direction[] ALL_SIX = {DOWN, UP, N, S, W, E};
        static final Direction[] HORIZONTAL = {N, E, S, W};
        static final Direction[] WATER_NEIGHBOR_ORDER = {E, W, N, S};
        final int dx;
        final int dz;
        final String key;
        final String axis;

        Direction(int dx, int dz, String key, String axis) {
            this.dx = dx;
            this.dz = dz;
            this.key = key;
            this.axis = axis;
        }

        Direction clockwise() {
            return switch (this) {
                case N -> E;
                case E -> S;
                case S -> W;
                case W -> N;
                default -> throw new IllegalStateException(key);
            };
        }
    }
}
