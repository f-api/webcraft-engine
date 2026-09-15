package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.feature.Mc263CommonTreeFeature.Pos;
import com.gameexpert.terrain.mc.feature.Mc263CommonTreeFeature.Result;
import com.gameexpert.terrain.mc.feature.Mc263CommonTreeFeature.State;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Exact pinned 26.3-snapshot-7 mangrove and swamp tree leaf for Step 9 indices 50/51. */
public final class Mc263MangroveSwampTreeFeature {
    public static final int TRACE_MAGIC = 0x4d535433; // MST3
    public static final int TRACE_VERSION = 1;
    public static final String TRACE_SCHEMA = "mc263-mangrove-swamp-tree-trace-v1";
    public static final String INNER_SERVER_SHA1 = "2f1ef79f3cad10138ad18da45b265fe656624026";
    public static final String MANGROVE_ROOT_CLASS_SHA256 =
            "e436d3fc951afa06a1e4f9a4aed71863b5186ae546896106fe87a7f978fc221c";
    public static final String UPWARDS_BRANCHING_CLASS_SHA256 =
            "3f449a270a30d520caf10111c58c2ad27cad11b7646e40945ae47e25c9c764d4";
    public static final String RANDOM_SPREAD_CLASS_SHA256 =
            "f0b1ecc34d128b8adfd7a3bd25cc4a2ef218820df20f410f68d82f5125c4a492";

    private static final Map<String, Phase> PHASES = Map.ofEntries(
            Map.entry("preflight", new Phase(1, 3)),
            Map.entry("rng_int", new Phase(2, 3)),
            Map.entry("rng_float", new Phase(3, 2)),
            Map.entry("rng_bool", new Phase(4, 2)),
            Map.entry("heightmap", new Phase(5, 6)),
            Map.entry("predicate", new Phase(6, 6)),
            Map.entry("selector", new Phase(7, 4)),
            Map.entry("read", new Phase(8, 7)),
            Map.entry("write", new Phase(9, 8)),
            Map.entry("root", new Phase(10, 6)),
            Map.entry("trunk", new Phase(11, 6)),
            Map.entry("foliage", new Phase(12, 6)),
            Map.entry("decorator", new Phase(13, 6)),
            Map.entry("bee", new Phase(14, 6)),
            Map.entry("finish", new Phase(15, 6)),
            Map.entry("result", new Phase(16, 6)));
    private static final TraceSink NO_TRACE = new TraceSink() {
        @Override public boolean enabled() { return false; }
        @Override public void record(String phase, long... values) { throw new AssertionError(); }
    };

    private Mc263MangroveSwampTreeFeature() { }

    /** All live placement/tag/fluid queries needed by the leaf; adapters must not approximate tags. */
    public interface WorldAccess extends Mc263CommonTreeFeature.WorldAccess {
        int oceanFloor(Pos column);
        int worldSurface(Pos column);
        boolean biomeAllows(String placedKey, Pos pos);
        boolean mangroveRootsCanGrowThrough(State state);
        boolean mangroveLogsCanGrowThrough(State state);
        boolean muddyRootsIn(State state);
    }

    /** Strict transitive dependency for the existing exact {@code minecraft:swamp_oak} leaf. */
    public interface LeafExecutor {
        boolean supports(String configuredKey);
        void preflight(String configuredKey, WorldAccess world);
        Result place(String configuredKey, WorldgenRandom random, Pos origin, WorldAccess world,
                TraceSink trace);
    }

    public interface TraceSink {
        default boolean enabled() { return true; }
        void record(String phase, long... values);
    }

    public record TraceEvent(String phase, long[] values) {
        public TraceEvent { values = values.clone(); }
        @Override public long[] values() { return values.clone(); }
    }

    public static Set<Integer> placedIndices() { return Set.of(50, 51); }
    public static List<String> configuredKeys() {
        return List.of("minecraft:mangrove", "minecraft:mangrove_vegetation",
                "minecraft:tall_mangrove");
    }

    /**
     * The two official {@code *_checked} placed features {@code minecraft:mangrove_vegetation}
     * selects between. Both are part of the configured closure this leaf preflights, so a caller's
     * feature capability must answer for them or step 9 fail-closes on every mangrove swamp chunk.
     */
    public static List<String> checkedKeys() {
        return List.of("minecraft:mangrove_checked", "minecraft:tall_mangrove_checked");
    }

    /** Validates the complete placed-index closure without RNG or live world access. */
    public static void preflightIndex(int index, WorldAccess world, LeafExecutor leaves) {
        if (index != 50 && index != 51) {
            throw new IllegalArgumentException("unsupported index " + index);
        }
        preflightIndexInternal(index, world, leaves);
    }

    /** Validates the complete configured-feature closure without RNG or live world access. */
    public static void preflightConfigured(String configuredKey, WorldAccess world) {
        preflightConfiguredInternal(normalize(configuredKey), world);
    }

    /** Validates the complete checked-feature closure without RNG or live world access. */
    public static void preflightChecked(String configuredKey, WorldAccess world) {
        String key = normalize(configuredKey);
        if (!key.equals("minecraft:mangrove") && !key.equals("minecraft:tall_mangrove")) {
            throw new IllegalArgumentException(key);
        }
        preflightCheckedInternal(key, world);
    }

    public static Result placeIndex(int index, WorldgenRandom random, Pos chunkOrigin,
            WorldAccess world, LeafExecutor leaves, TraceSink trace) {
        if (index != 50 && index != 51) throw new IllegalArgumentException("unsupported index " + index);
        preflightIndex(index, world, leaves);
        String placedKey = index == 50 ? "minecraft:trees_mangrove" : "minecraft:trees_swamp";
        emit(trace, "preflight", index, id(placedKey), 2);
        int count = index == 50 ? 25 : 2 + (nextInt(random, 10, 10, trace) >= 9 ? 1 : 0);
        Counts total = new Counts(); boolean any = false;
        for (int attempt = 0; attempt < count; attempt++) {
            int x = chunkOrigin.x() + nextInt(random, 16, 11, trace);
            int z = chunkOrigin.z() + nextInt(random, 16, 12, trace);
            Pos column = new Pos(x, chunkOrigin.y(), z);
            int ocean = world.oceanFloor(column); total.reads++;
            int surface = world.worldSurface(column); total.reads++;
            int maxWater = index == 50 ? 5 : 2;
            emit(trace, "heightmap", x, z, ocean, surface, maxWater, index);
            if (surface - ocean > maxWater) continue;
            int y = world.oceanFloor(column); total.reads++;
            emit(trace, "heightmap", x, z, y, y, -1, index);
            Pos origin = new Pos(x, y, z);
            boolean biome = world.biomeAllows(placedKey, origin); total.reads++;
            emit(trace, "predicate", id(placedKey), x, y, z, biome ? 1 : 0, 20);
            if (!biome) continue;
            Result result;
            if (index == 50) {
                result = placeVegetationLive(random, origin, world, trace);
            } else {
                State sapling = State.of("minecraft:oak_sapling");
                boolean survives = world.canSaplingSurvive(sapling, origin); total.reads++;
                emit(trace, "predicate", id(sapling.canonical()), x, y, z,
                        survives ? 1 : 0, 21);
                if (!survives) continue;
                result = leaves.place("minecraft:swamp_oak", random, origin, world, trace);
            }
            total.add(result); any |= result.placed();
        }
        return result(any, total, trace, placedKey);
    }

    public static Result placeConfigured(String configuredKey, WorldgenRandom random, Pos origin,
            WorldAccess world, TraceSink trace) {
        String key = normalize(configuredKey);
        preflightConfigured(key, world);
        emit(trace, "preflight", id(key), 0, 1);
        if (key.equals("minecraft:mangrove_vegetation"))
            return placeVegetationLive(random, origin, world, trace);
        return placeTree(key.equals("minecraft:tall_mangrove"), random, origin, world, trace);
    }

    public static Result placeChecked(String configuredKey, WorldgenRandom random, Pos origin,
            WorldAccess world, TraceSink trace) {
        String key = normalize(configuredKey);
        if (!key.equals("minecraft:mangrove") && !key.equals("minecraft:tall_mangrove"))
            throw new IllegalArgumentException(key);
        preflightChecked(key, world);
        emit(trace, "preflight", id(key), id(key + "_checked"), 3);
        return placeCheckedLive(key, random, origin, world, trace, false);
    }

    private static Result placeVegetationLive(WorldgenRandom random, Pos origin, WorldAccess world,
            TraceSink trace) {
        float pick = nextFloat(random, 30, trace);
        String key = pick < .85f ? "minecraft:tall_mangrove" : "minecraft:mangrove";
        emit(trace, "selector", id("minecraft:mangrove_vegetation"), id(key),
                Float.floatToRawIntBits(pick), 1);
        return placeCheckedLive(key, random, origin, world, trace, true);
    }

    private static Result placeCheckedLive(String key, WorldgenRandom random, Pos origin,
            WorldAccess world, TraceSink trace, boolean nested) {
        State propagule = propaguleSapling();
        boolean survives = world.canSaplingSurvive(propagule, origin);
        emit(trace, "predicate", id(propagule.canonical()), origin.x(), origin.y(), origin.z(),
                survives ? 1 : 0, nested ? 31 : 32);
        if (!survives) return new Result(false, 1, 0, 0, 0, 0);
        return placeTree(key.equals("minecraft:tall_mangrove"), random, origin, world, trace);
    }

    private static Result placeTree(boolean tall, WorldgenRandom random, Pos origin,
            WorldAccess world, TraceSink trace) {
        Counts counts = new Counts();
        int height = (tall ? 4 : 2) + nextInt(random, 2, 40, trace)
                + nextInt(random, tall ? 10 : 5, 41, trace);
        int trunkOffset = (tall ? 3 : 1) + nextInt(random, tall ? 5 : 3, 42, trace);
        Pos trunkOrigin = origin.offset(0, trunkOffset, 0);
        emit(trace, "trunk", id(tall ? "minecraft:tall_mangrove" : "minecraft:mangrove"),
                height, trunkOffset, trunkOrigin.y(), 0, 0);
        if (Math.min(origin.y(), trunkOrigin.y()) < world.minY() + 1
                || Math.max(origin.y(), trunkOrigin.y()) + height + 1 > world.maxY() + 1)
            return result(false, counts, trace, tall ? "minecraft:tall_mangrove" : "minecraft:mangrove");
        int clipped = maxFree(height, tall ? 3 : 2, trunkOrigin, world, trace, counts);
        if (clipped < height) return result(false, counts, trace,
                tall ? "minecraft:tall_mangrove" : "minecraft:mangrove");

        Set<Pos> roots = new HashSet<>(), logs = new HashSet<>(), foliage = new HashSet<>(),
                decorations = new HashSet<>();
        if (!placeRoots(origin, trunkOrigin, random, world, trace, counts, roots))
            return result(false, counts, trace, tall ? "minecraft:tall_mangrove" : "minecraft:mangrove");
        List<Attachment> attachments = placeTrunk(height, trunkOrigin, tall, random, world, trace,
                counts, logs);
        for (Attachment attachment : attachments)
            placeFoliage(attachment.pos, random, world, trace, counts, foliage);
        if (logs.isEmpty() && foliage.isEmpty()) return result(false, counts, trace,
                tall ? "minecraft:tall_mangrove" : "minecraft:mangrove");
        decorate(logs, foliage, random, world, trace, counts, decorations);
        world.finishTree(Collections.unmodifiableSet(new LinkedHashSet<>(logs)),
                Collections.unmodifiableSet(new LinkedHashSet<>(foliage)),
                Collections.unmodifiableSet(new LinkedHashSet<>(roots)),
                Collections.unmodifiableSet(new LinkedHashSet<>(decorations)));
        emit(trace, "finish", roots.size(), logs.size(), foliage.size(), decorations.size(),
                counts.reads, counts.writes);
        return result(true, counts, trace, tall ? "minecraft:tall_mangrove" : "minecraft:mangrove");
    }

    private static int maxFree(int height, int limit, Pos origin, WorldAccess world, TraceSink trace,
            Counts counts) {
        for (int y = 0; y <= height + 1; y++) {
            int radius = y < limit ? 0 : 2;
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (!isFreeTreePosition(origin.offset(x, y, z), world, trace, counts)) return y - 2;
            }
        }
        return height;
    }

    private static boolean placeRoots(Pos origin, Pos trunkOrigin, WorldgenRandom random,
            WorldAccess world, TraceSink trace, Counts counts, Set<Pos> roots) {
        Pos column = origin;
        while (column.y() < trunkOrigin.y()) {
            if (!canPlaceRoot(column, world, trace, counts)) return false;
            column = column.offset(0, 1, 0);
        }
        List<Pos> ordered = new ArrayList<>();
        ordered.add(trunkOrigin.offset(0, -1, 0));
        for (Dir direction : Dir.H) {
            List<Pos> branch = new ArrayList<>();
            Pos side = trunkOrigin.offset(direction.dx, 0, direction.dz);
            if (!simulateRoots(side, direction, trunkOrigin, branch, 0, random, world, trace, counts))
                return false;
            ordered.addAll(branch);
            ordered.add(side);
        }
        for (Pos pos : ordered) placeRoot(pos, random, world, trace, counts, roots);
        return true;
    }

    private static boolean simulateRoots(Pos pos, Dir direction, Pos origin, List<Pos> out,
            int layer, WorldgenRandom random, WorldAccess world, TraceSink trace, Counts counts) {
        if (layer == 15 || out.size() > 15) return false;
        for (Pos candidate : potentialRoots(pos, direction, origin, random, trace)) {
            boolean can = canPlaceRoot(candidate, world, trace, counts);
            emit(trace, "root", candidate.x(), candidate.y(), candidate.z(), layer,
                    can ? 1 : 0, out.size());
            if (!can) continue;
            out.add(candidate);
            if (!simulateRoots(candidate, direction, origin, out, layer + 1, random, world, trace,
                    counts)) return false;
        }
        return true;
    }

    private static List<Pos> potentialRoots(Pos pos, Dir direction, Pos origin,
            WorldgenRandom random, TraceSink trace) {
        Pos below = pos.offset(0, -1, 0);
        Pos next = pos.offset(direction.dx, 0, direction.dz);
        int width = manhattan(pos, origin);
        if (width > 5 && width <= 8)
            return nextFloat(random, 50, trace) < .2f
                    ? List.of(below, next.offset(0, -1, 0)) : List.of(below);
        if (width > 8) return List.of(below);
        if (nextFloat(random, 51, trace) < .2f) return List.of(below);
        return nextBoolean(random, 52, trace) ? List.of(next) : List.of(below);
    }

    private static void placeRoot(Pos pos, WorldgenRandom random, WorldAccess world, TraceSink trace,
            Counts counts, Set<Pos> roots) {
        State live = read(world, pos, trace, counts);
        if (world.muddyRootsIn(live)) {
            write(world, pos, muddyRoots(), trace, counts, roots);
            return;
        }
        if (!canPlaceRoot(pos, world, trace, counts)) return;
        State fluidQuery = read(world, pos, trace, counts);
        write(world, pos, mangroveRoots(fluidQuery.waterSource()), trace,
                counts, roots);
        Pos above = pos.offset(0, 1, 0);
        if (nextFloat(random, 53, trace) < .5f && read(world, above, trace, counts).air())
            write(world, above, State.of("minecraft:moss_carpet"), trace, counts, roots);
    }

    private static List<Attachment> placeTrunk(int height, Pos origin, boolean tall,
            WorldgenRandom random, WorldAccess world, TraceSink trace, Counts counts, Set<Pos> logs) {
        List<Attachment> attachments = new ArrayList<>();
        Pos mutable = origin;
        for (int heightPos = 0; heightPos < height; heightPos++) {
            int y = origin.y() + heightPos;
            mutable = new Pos(origin.x(), y, origin.z());
            boolean placed = placeLog(mutable, world, trace, counts, logs);
            if (placed && heightPos < height - 1 && nextFloat(random, 60, trace) < .5f) {
                Dir direction = Dir.H[nextInt(random, 4, 61, trace)];
                int firstLength = nextInt(random, 2, 62, trace);
                int branchPos = Math.max(0, firstLength - nextInt(random, 2, 63, trace) - 1);
                int steps = 1 + nextInt(random, tall ? 6 : 4, 64, trace);
                placeBranch(height, mutable, y, direction, branchPos, steps, random, world, trace,
                        counts, logs, attachments);
            }
            if (heightPos == height - 1)
                attachments.add(new Attachment(new Pos(origin.x(), y + 1, origin.z())));
        }
        return attachments;
    }

    private static void placeBranch(int treeHeight, Pos logPos, int currentY, Dir direction,
            int branchPos, int steps, WorldgenRandom random, WorldAccess world, TraceSink trace,
            Counts counts, Set<Pos> logs, List<Attachment> attachments) {
        int alongHeight = currentY + branchPos, x = logPos.x(), z = logPos.z();
        for (int index = branchPos; index < treeHeight && steps > 0; index++, steps--) {
            if (index < 1) continue;
            int y = currentY + index;
            alongHeight = y; x += direction.dx; z += direction.dz;
            Pos pos = new Pos(x, y, z);
            if (placeLog(pos, world, trace, counts, logs)) alongHeight++;
            attachments.add(new Attachment(pos));
        }
        if (alongHeight - currentY > 1) {
            Pos foliage = new Pos(x, alongHeight, z);
            attachments.add(new Attachment(foliage));
            attachments.add(new Attachment(foliage.offset(0, -2, 0)));
        }
    }

    private static boolean placeLog(Pos pos, WorldAccess world, TraceSink trace, Counts counts,
            Set<Pos> logs) {
        boolean valid = validTrunkPosition(pos, world, trace, counts);
        emit(trace, "trunk", pos.x(), pos.y(), pos.z(), valid ? 1 : 0, 0, 0);
        if (!valid) return false;
        write(world, pos, mangroveLog(), trace, counts, logs);
        return true;
    }

    private static void placeFoliage(Pos origin, WorldgenRandom random, WorldAccess world,
            TraceSink trace, Counts counts, Set<Pos> foliage) {
        for (int i = 0; i < 70; i++) {
            int dx = nextInt(random, 3, 70, trace) - nextInt(random, 3, 71, trace);
            int dy = nextInt(random, 2, 72, trace) - nextInt(random, 2, 73, trace);
            int dz = nextInt(random, 3, 74, trace) - nextInt(random, 3, 75, trace);
            Pos pos = origin.offset(dx, dy, dz);
            State persistentQuery = read(world, pos, trace, counts);
            boolean valid = !persistentQuery.persistent()
                    && validTreePosition(pos, world, trace, counts);
            State fluidQuery = valid ? read(world, pos, trace, counts) : persistentQuery;
            emit(trace, "foliage", pos.x(), pos.y(), pos.z(), i, valid ? 1 : 0,
                    fluidQuery.waterSource() ? 1 : 0);
            if (valid) write(world, pos, leaves(fluidQuery.waterSource()), trace, counts, foliage);
        }
    }

    private static void decorate(Set<Pos> logs, Set<Pos> foliage, WorldgenRandom random,
            WorldAccess world, TraceSink trace, Counts counts, Set<Pos> out) {
        List<Pos> leaves = yOrdered(foliage), trunks = yOrdered(logs);
        emit(trace, "decorator", 1, leaves.size(), Float.floatToRawIntBits(.125f), 0, 0, 0);
        for (Pos leaf : leaves) for (Dir direction : List.of(Dir.W, Dir.E, Dir.N, Dir.S)) {
            if (nextFloat(random, 80, trace) >= .125f) continue;
            Pos pos = leaf.offset(direction.dx, 0, direction.dz);
            if (!read(world, pos, trace, counts).air()) continue;
            write(world, pos, vine(direction.opposite().key), trace, counts, out);
            pos = pos.offset(0, -1, 0);
            for (int remaining = 4; remaining > 0
                    && read(world, pos, trace, counts).air(); remaining--) {
                write(world, pos, vine(direction.opposite().key), trace, counts, out);
                pos = pos.offset(0, -1, 0);
            }
        }
        emit(trace, "decorator", 2, leaves.size(), Float.floatToRawIntBits(.14f), 1, 0, 2);
        List<Pos> shuffled = new ArrayList<>(leaves); shuffle(shuffled, random, trace);
        Set<Pos> blacklist = new HashSet<>();
        for (Pos leaf : shuffled) {
            Pos pos = leaf.offset(0, -1, 0);
            nextInt(random, 1, 90, trace); // Util.getRandom(singleton directions)
            if (blacklist.contains(pos) || nextFloat(random, 91, trace) >= .14f) continue;
            if (!read(world, pos, trace, counts).air()
                    || !read(world, pos.offset(0, -1, 0), trace, counts).air()) continue;
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                blacklist.add(pos.offset(x, 0, z));
            int age = nextInt(random, 5, 92, trace);
            write(world, pos, propagule(age), trace, counts, out);
        }
        emit(trace, "decorator", 3, trunks.size(), Float.floatToRawIntBits(.01f), 0, 0, 0);
        beehive(trunks, leaves, random, world, trace, counts, out);
    }

    private static void beehive(List<Pos> logs, List<Pos> leaves, WorldgenRandom random,
            WorldAccess world, TraceSink trace, Counts counts, Set<Pos> out) {
        if (logs.isEmpty() || nextFloat(random, 100, trace) >= .01f) return;
        int y = !leaves.isEmpty() ? Math.max(leaves.getFirst().y() - 1, logs.getFirst().y() + 1)
                : Math.min(logs.getFirst().y() + 1 + nextInt(random, 3, 101, trace),
                        logs.getLast().y());
        List<Pos> choices = new ArrayList<>();
        for (Pos log : logs) if (log.y() == y)
            for (Dir direction : List.of(Dir.E, Dir.S, Dir.W))
                choices.add(log.offset(direction.dx, 0, direction.dz));
        shuffle(choices, random, trace);
        for (Pos pos : choices) {
            if (!read(world, pos, trace, counts).air()
                    || !read(world, pos.offset(0, 0, 1), trace, counts).air()) continue;
            write(world, pos, State.of("minecraft:bee_nest").property("facing", "south")
                    .property("honey_level", "0"), trace, counts, out);
            int bees = 2 + nextInt(random, 2, 102, trace);
            for (int i = 0; i < bees; i++) {
                int ticks = nextInt(random, 599, 103, trace);
                world.storeBee(pos, ticks); counts.bees++;
                emit(trace, "bee", pos.x(), pos.y(), pos.z(), ticks, i, bees);
            }
            break;
        }
    }

    private static void preflightIndexInternal(int index, WorldAccess world, LeafExecutor leaves) {
        String key = index == 50 ? "minecraft:trees_mangrove" : "minecraft:trees_swamp";
        if (!world.supportsFeature(key)) throw new UnsupportedOperationException(key);
        if (index == 50) preflightConfiguredInternal("minecraft:mangrove_vegetation", world);
        else {
            if (!leaves.supports("minecraft:swamp_oak"))
                throw new UnsupportedOperationException("minecraft:swamp_oak");
            leaves.preflight("minecraft:swamp_oak", world);
            requireState(world, State.of("minecraft:oak_sapling"));
        }
    }

    private static void preflightConfiguredInternal(String key, WorldAccess world) {
        if (!configuredKeys().contains(key)) throw new IllegalArgumentException(key);
        Set<String> closure = new LinkedHashSet<>(); closure.add(key);
        if (key.equals("minecraft:mangrove_vegetation")) {
            closure.add("minecraft:tall_mangrove_checked");
            closure.add("minecraft:mangrove_checked");
            closure.add("minecraft:tall_mangrove"); closure.add("minecraft:mangrove");
        }
        for (String feature : closure)
            if (!world.supportsFeature(feature)) throw new UnsupportedOperationException(feature);
        if (!world.supportsTreeFinalization())
            throw new UnsupportedOperationException("tree finalization");
        if (!world.supportsBeeNestPayload())
            throw new UnsupportedOperationException("beehive payload");
        List<State> states = new ArrayList<>(List.of(
                mangroveLog(), State.of("minecraft:dirt"),
                State.of("minecraft:moss_carpet"),
                mangroveRoots(false), mangroveRoots(true), muddyRoots(),
                State.of("minecraft:bee_nest").property("facing", "south")
                        .property("honey_level", "0"), propaguleSapling()));
        for (int distance = 1; distance <= 7; distance++) {
            states.add(new State("minecraft:mangrove_leaves", Map.of(
                    "distance", Integer.toString(distance), "persistent", "false",
                    "waterlogged", "false"), false, true, false, true, false, false, false));
            states.add(new State("minecraft:mangrove_leaves", Map.of(
                    "distance", Integer.toString(distance), "persistent", "false",
                    "waterlogged", "true"), false, true, false, true, false, true, false));
        }
        for (Dir direction : Dir.H) states.add(vine(direction.key));
        for (int age = 0; age <= 4; age++) states.add(propagule(age));
        for (State state : states) requireState(world, state);
    }

    private static void preflightCheckedInternal(String key, WorldAccess world) {
        preflightConfiguredInternal(key, world);
        String checked = key + "_checked";
        if (!world.supportsFeature(checked)) throw new UnsupportedOperationException(checked);
        requireState(world, propaguleSapling());
    }

    private static void requireState(WorldAccess world, State state) {
        if (!world.supportsState(state)) throw new UnsupportedOperationException(state.canonical());
    }

    private static State read(WorldAccess world, Pos pos, TraceSink trace, Counts counts) {
        State state = world.state(pos); counts.reads++;
        emit(trace, "read", pos.x(), pos.y(), pos.z(), id(state.canonical()),
                state.air() ? 1 : 0, state.replaceableByTrees() ? 1 : 0,
                state.waterSource() ? 1 : 0);
        return state;
    }

    private static void write(WorldAccess world, Pos pos, State state, TraceSink trace,
            Counts counts, Set<Pos> positions) {
        boolean retained = world.set(pos, state, 19); counts.writes++;
        if (retained) counts.retained++;
        positions.add(pos);
        emit(trace, "write", pos.x(), pos.y(), pos.z(), id(state.canonical()), 19,
                retained ? 1 : 0, state.properties().hashCode(), id(state.block()));
    }

    private static boolean validTreePosition(Pos pos, WorldAccess world, TraceSink trace,
            Counts counts) {
        return free(read(world, pos, trace, counts));
    }
    private static boolean validTrunkPosition(Pos pos, WorldAccess world, TraceSink trace,
            Counts counts) {
        State base = read(world, pos, trace, counts);
        if (free(base)) return true;
        return world.mangroveLogsCanGrowThrough(read(world, pos, trace, counts));
    }
    private static boolean isFreeTreePosition(Pos pos, WorldAccess world, TraceSink trace,
            Counts counts) {
        if (validTrunkPosition(pos, world, trace, counts)) return true;
        return read(world, pos, trace, counts).log();
    }
    private static boolean canPlaceRoot(Pos pos, WorldAccess world, TraceSink trace, Counts counts) {
        State base = read(world, pos, trace, counts);
        if (free(base)) return true;
        return world.mangroveRootsCanGrowThrough(read(world, pos, trace, counts));
    }
    private static boolean free(State state) { return state.air() || state.replaceableByTrees(); }
    private static State mangroveLog() {
        return new State("minecraft:mangrove_log", Map.of("axis", "y"), false, false,
                true, false, false, false, true);
    }
    private static State muddyRoots() {
        return new State("minecraft:muddy_mangrove_roots", Map.of("axis", "y"), false,
                false, false, false, false, false, true);
    }
    private static State mangroveRoots(boolean water) {
        return new State("minecraft:mangrove_roots",
                Map.of("waterlogged", Boolean.toString(water)), false, false,
                false, false, false, water, false);
    }
    private static State leaves(boolean water) {
        return new State("minecraft:mangrove_leaves", Map.of("distance", "7",
                "persistent", "false", "waterlogged", Boolean.toString(water)), false, true,
                false, true, false, water, false);
    }
    private static State propagule(int age) {
        return State.of("minecraft:mangrove_propagule").property("age", Integer.toString(age))
                .property("hanging", "true").property("stage", "0")
                .property("waterlogged", "false");
    }
    private static State propaguleSapling() {
        return State.of("minecraft:mangrove_propagule").property("age", "0")
                .property("hanging", "false").property("stage", "0")
                .property("waterlogged", "false");
    }
    private static State vine(String face) {
        return State.of("minecraft:vine").property("east", Boolean.toString(face.equals("east")))
                .property("north", Boolean.toString(face.equals("north")))
                .property("south", Boolean.toString(face.equals("south"))).property("up", "false")
                .property("west", Boolean.toString(face.equals("west")));
    }

    private static List<Pos> yOrdered(Set<Pos> positions) {
        List<Pos> ordered = new ArrayList<>(positions);
        ordered.sort(Comparator.comparingInt(Pos::y));
        return ordered;
    }
    private static void shuffle(List<?> list, WorldgenRandom random, TraceSink trace) {
        for (int i = list.size(); i > 1; i--)
            java.util.Collections.swap(list, i - 1, nextInt(random, i, 110, trace));
    }
    private static int nextInt(WorldgenRandom random, int bound, int site, TraceSink trace) {
        int value = random.nextInt(bound); emit(trace, "rng_int", bound, value, site); return value;
    }
    private static float nextFloat(WorldgenRandom random, int site, TraceSink trace) {
        float value = random.nextFloat();
        emit(trace, "rng_float", Float.floatToRawIntBits(value), site); return value;
    }
    private static boolean nextBoolean(WorldgenRandom random, int site, TraceSink trace) {
        boolean value = random.nextBoolean(); emit(trace, "rng_bool", value ? 1 : 0, site);
        return value;
    }
    private static int manhattan(Pos a, Pos b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) + Math.abs(a.z() - b.z());
    }
    private static Result result(boolean placed, Counts counts, TraceSink trace, String key) {
        emit(trace, "result", id(key), placed ? 1 : 0, counts.reads, counts.writes,
                counts.retained, counts.bees);
        return new Result(placed, counts.reads, counts.writes, counts.retained, counts.bees, 0);
    }
    private static void emit(TraceSink trace, String phase, long... values) {
        if (trace.enabled()) trace.record(phase, values);
    }
    private static String normalize(String key) {
        return key.startsWith("minecraft:") ? key : "minecraft:" + key;
    }
    private static long id(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : value.getBytes(StandardCharsets.UTF_8))
            hash = (hash ^ (b & 255)) * 0x100000001b3L;
        return hash;
    }

    public static byte[] encodeTrace(List<TraceEvent> events) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(TRACE_MAGIC); out.writeShort(TRACE_VERSION);
            byte[] schema = TRACE_SCHEMA.getBytes(StandardCharsets.UTF_8);
            out.writeShort(schema.length); out.write(schema); out.writeInt(events.size());
            for (TraceEvent event : events) {
                Phase phase = PHASES.get(event.phase());
                if (phase == null || event.values().length != phase.arity)
                    throw new IllegalArgumentException(event.phase());
                out.writeByte(phase.id); out.writeByte(phase.arity);
                for (long value : event.values()) out.writeLong(value);
            }
            return bytes.toByteArray();
        } catch (IOException e) { throw new AssertionError(e); }
    }

    private record Attachment(Pos pos) { }
    private record Phase(int id, int arity) { }
    private enum Dir {
        N(0, -1, "north"), E(1, 0, "east"), S(0, 1, "south"), W(-1, 0, "west");
        static final Dir[] H = values();
        final int dx, dz; final String key;
        Dir(int dx, int dz, String key) { this.dx = dx; this.dz = dz; this.key = key; }
        Dir opposite() { return switch (this) { case N -> S; case E -> W; case S -> N; case W -> E; }; }
    }
    private static final class Counts {
        int reads, writes, retained, bees;
        void add(Result result) {
            reads += result.reads(); writes += result.writes(); retained += result.retained();
            bees += result.bees();
        }
    }
}
