package com.gameexpert.terrain.mc.structure;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Exact dormant post-process executor shared by the pinned hard-coded stronghold pieces. */
public final class Mc263StrongholdStraightPieceExecutor {
    public static final String STONE = "minecraft:stone_bricks";
    public static final String CRACKED = "minecraft:cracked_stone_bricks";
    public static final String MOSSY = "minecraft:mossy_stone_bricks";
    public static final String INFESTED = "minecraft:infested_stone_bricks";
    public static final String AIR = "minecraft:cave_air";

    private Mc263StrongholdStraightPieceExecutor() { }

    public static final class BlockPos {
        private final int x; private final int y; private final int z;
        public BlockPos(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        public int x() { return x; } public int y() { return y; } public int z() { return z; }
        @Override public boolean equals(Object other) {
            return other instanceof BlockPos p && x == p.x && y == p.y && z == p.z;
        }
        @Override public int hashCode() { return Objects.hash(x, y, z); }
    }

    public static final class BoundingBox {
        private final int minX, minY, minZ, maxX, maxY, maxZ;
        public BoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException(
                    "inverted stronghold placement clip");
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }
        public boolean contains(BlockPos p) { return p.x >= minX && p.x <= maxX
                && p.y >= minY && p.y <= maxY && p.z >= minZ && p.z <= maxZ; }
        public boolean intersects(Mc263StrongholdGraphCarrier.BoundingBox b) {
            return maxX >= b.minX() && minX <= b.maxX() && maxY >= b.minY()
                    && minY <= b.maxY() && maxZ >= b.minZ() && minZ <= b.maxZ();
        }
    }

    public interface RandomSource {
        float nextFloat();
        long nextLong();
    }

    /** Capability probes are pure and are exhausted before RNG, reads, or writes. */
    public interface WorldAccess {
        boolean supportsExactState(String exactState);
        boolean supportsLiveAirQueries();
        boolean supportsPostWriteFluidStateQueries();
        boolean supportsScheduledFluidTicks();
        boolean supportsPostprocessingMarks();
        boolean supportsLootChests();
        boolean supportsSpawnerBlockEntities();
        boolean isAir(BlockPos position);
        boolean setBlock(BlockPos position, String exactState, int flags);
        String postWriteFluidType(BlockPos position);
        void scheduleFluidTick(BlockPos position, String fluidType, int delay);
        void markForPostprocessing(BlockPos position);
        void createLootChest(BlockPos position, String lootTable, RandomSource random);
        void configureSpawner(BlockPos position, String entityType, RandomSource random);
    }

    public static final class ExecutionResult {
        private final int liveReads, randomFloats, writes, postprocessingMarks;
        private final boolean placedMutableFact;
        ExecutionResult(Counter c, boolean placed) {
            liveReads = c.reads; randomFloats = c.random; writes = c.writes;
            postprocessingMarks = c.post; placedMutableFact = placed;
        }
        public int liveReads() { return liveReads; }
        public int randomFloats() { return randomFloats; }
        public int writes() { return writes; }
        public int postprocessingMarks() { return postprocessingMarks; }
        public boolean placedMutableFact() { return placedMutableFact; }
    }

    public static ExecutionResult execute(Mc263StrongholdStartPieceFactoryAdapter.PieceData piece,
            BoundingBox clip, RandomSource random, WorldAccess world) {
        Objects.requireNonNull(clip, "clip"); Objects.requireNonNull(random, "random");
        Objects.requireNonNull(world, "world"); preflight(piece, world);
        Counter c = new Counter();
        boolean placed = piece.placed();
        switch (piece.type()) {
            case STRAIGHT -> straight(piece, clip, random, world, c);
            case PRISON_HALL -> prison(piece, clip, random, world, c);
            case ROOM_CROSSING -> room(piece, clip, random, world, c);
            case STRAIGHT_STAIRS_DOWN -> straightStairs(piece, clip, random, world, c);
            case STAIRS_DOWN, START -> stairs(piece, clip, random, world, c);
            case FIVE_CROSSING -> five(piece, clip, random, world, c);
            case CHEST_CORRIDOR -> placed = chest(piece, clip, random, world, c);
            case LIBRARY -> library(piece, clip, random, world, c);
            case PORTAL_ROOM -> placed = portal(piece, clip, random, world, c);
            default -> throw new IllegalArgumentException("executor does not own " + piece.type());
        }
        return new ExecutionResult(c, placed);
    }

    public static void preflight(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            WorldAccess world) {
        Objects.requireNonNull(p, "piece"); Objects.requireNonNull(world, "world");
        int[] dimensions = dimensions(p.type(), p.tall());
        Mc263StrongholdGraphCarrier.BoundingBox b = p.box();
        int xs = Math.addExact(Math.subtractExact(b.maxX(), b.minX()), 1);
        int ys = Math.addExact(Math.subtractExact(b.maxY(), b.minY()), 1);
        int zs = Math.addExact(Math.subtractExact(b.maxZ(), b.minZ()), 1);
        boolean swapped = p.orientation() == Mc263StrongholdGraphCarrier.Orientation.WEST
                || p.orientation() == Mc263StrongholdGraphCarrier.Orientation.EAST;
        if (xs != (swapped ? dimensions[2] : dimensions[0]) || ys != dimensions[1]
                || zs != (swapped ? dimensions[0] : dimensions[2])) {
            throw new IllegalArgumentException("stronghold piece dimensions disagree with type");
        }
        // Resolve every local extreme, including the iron-door outside button, before side effects.
        worldPos(p, -1, 0, -1); worldPos(p, dimensions[0], dimensions[1], dimensions[2]);
        Set<String> states = requiredStates(p);
        for (String state : states) if (!world.supportsExactState(state)) {
            throw new UnsupportedOperationException("unsupported stronghold state " + state);
        }
        if (p.type() != Mc263StrongholdGraphCarrier.PieceType.PORTAL_ROOM) {
            require(world.supportsLiveAirQueries(), "live air queries");
        }
        require(world.supportsPostWriteFluidStateQueries(), "post-write fluid queries");
        require(world.supportsScheduledFluidTicks(), "scheduled fluid ticks");
        if (states.stream().anyMatch(Mc263StrongholdStraightPieceExecutor::shapeCheck)) {
            require(world.supportsPostprocessingMarks(), "postprocessing marks");
        }
        if (p.type() == Mc263StrongholdGraphCarrier.PieceType.CHEST_CORRIDOR
                || p.type() == Mc263StrongholdGraphCarrier.PieceType.LIBRARY
                || p.type() == Mc263StrongholdGraphCarrier.PieceType.ROOM_CROSSING
                && p.variant() == 2) require(world.supportsLootChests(), "loot chests");
        if (p.type() == Mc263StrongholdGraphCarrier.PieceType.PORTAL_ROOM && !p.placed()) {
            require(world.supportsSpawnerBlockEntities(), "spawner block entities");
        }
    }

    private static Set<String> requiredStates(
            Mc263StrongholdStartPieceFactoryAdapter.PieceData p) {
        LinkedHashSet<String> s = new LinkedHashSet<>();
        s.add(STONE); s.add(CRACKED); s.add(MOSSY); s.add(INFESTED); s.add(AIR);
        Mc263StrongholdStartPieceFactoryAdapter.Door actualDoor =
                p.type() == Mc263StrongholdGraphCarrier.PieceType.PORTAL_ROOM
                ? Mc263StrongholdStartPieceFactoryAdapter.Door.GRATES : p.door();
        addDoorStates(s, p, actualDoor);
        switch (p.type()) {
            case STRAIGHT -> {
                s.add(wallTorch(p, "east")); s.add(wallTorch(p, "west"));
            }
            case PRISON_HALL -> {
                s.add(bars(p, "north", "south")); s.add(bars(p, "north", "south", "east"));
                s.add(bars(p, "west", "east"));
                s.add(localDoorState(p, "iron_door", "west", "lower", "left"));
                s.add(localDoorState(p, "iron_door", "west", "upper", "left"));
            }
            case ROOM_CROSSING -> {
                if (p.variant() == 0) {
                    s.add(wallTorch(p, "west")); s.add(wallTorch(p, "east"));
                    s.add(wallTorch(p, "south")); s.add(wallTorch(p, "north"));
                    s.add("minecraft:smooth_stone_slab[type=bottom,waterlogged=false]");
                } else if (p.variant() == 1) s.add("minecraft:water[level=0]");
                else if (p.variant() == 2) {
                    s.add("minecraft:cobblestone"); s.add("minecraft:oak_planks");
                    s.add(wallTorch(p, "north"));
                    s.add(rotated(p, "minecraft:ladder[facing=west,waterlogged=false]"));
                }
            }
            case STRAIGHT_STAIRS_DOWN -> s.add(rotated(p,
                    "minecraft:cobblestone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"));
            case STAIRS_DOWN, START -> s.add(
                    "minecraft:smooth_stone_slab[type=bottom,waterlogged=false]");
            case FIVE_CROSSING -> {
                s.add("minecraft:smooth_stone_slab[type=bottom,waterlogged=false]");
                s.add("minecraft:smooth_stone_slab[type=double,waterlogged=false]");
                s.add(wallTorch(p, "south"));
            }
            case CHEST_CORRIDOR -> s.add(
                    "minecraft:stone_brick_slab[type=bottom,waterlogged=false]");
            case LIBRARY -> {
                s.add("minecraft:cobweb"); s.add("minecraft:oak_planks");
                s.add("minecraft:bookshelf"); s.add(wallTorch(p, "east"));
                s.add(wallTorch(p, "west"));
                if (p.tall()) {
                    s.add("minecraft:torch");
                    s.add(rotated(p, "minecraft:ladder[facing=south,waterlogged=false]"));
                    for (String[] connections : new String[][]{{"north","south"},
                            {"west","east"},{"north","east"},{"south","east"},
                            {"north","west"},{"south","west"},{"east"},{"west"},
                            {"north","south","west","east"},{"east","north"},
                            {"east","south"},{"west","north"},{"west","south"}})
                        s.add(fence(p, connections));
                }
            }
            case PORTAL_ROOM -> {
                s.add("minecraft:lava[level=0]"); s.add("minecraft:spawner");
                s.add("minecraft:end_portal"); s.add(bars(p, "north", "south"));
                s.add(bars(p, "west", "east"));
                s.add(rotated(p, "minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"));
                for (String d : new String[]{"north", "south", "east", "west"}) {
                    s.add(rotated(p, "minecraft:end_portal_frame[eye=false,facing=" + d + "]"));
                    s.add(rotated(p, "minecraft:end_portal_frame[eye=true,facing=" + d + "]"));
                }
            }
            default -> { }
        }
        return Set.copyOf(s);
    }

    private static void straight(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            BoundingBox clip, RandomSource r, WorldAccess w, Counter c) {
        selectorBox(p, clip, r, w, c, 0, 0, 0, 4, 4, 6, true);
        door(p, clip, w, c, p.door(), 1, 1, 0); door(p, clip, w, c,
                Mc263StrongholdStartPieceFactoryAdapter.Door.OPENING, 1, 1, 6);
        maybe(p, clip, r, w, c, .1F, 1, 2, 1, wallTorch(p, "east"));
        maybe(p, clip, r, w, c, .1F, 3, 2, 1, wallTorch(p, "west"));
        maybe(p, clip, r, w, c, .1F, 1, 2, 5, wallTorch(p, "east"));
        maybe(p, clip, r, w, c, .1F, 3, 2, 5, wallTorch(p, "west"));
        if (p.first()) fixedBox(p, clip, w, c, 0, 1, 2, 0, 3, 4, AIR);
        if (p.second()) fixedBox(p, clip, w, c, 4, 1, 2, 4, 3, 4, AIR);
    }

    private static void prison(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            BoundingBox clip, RandomSource r, WorldAccess w, Counter c) {
        selectorBox(p, clip, r, w, c, 0, 0, 0, 8, 4, 10, true);
        door(p, clip, w, c, p.door(), 1, 1, 0);
        fixedBox(p, clip, w, c, 1, 1, 10, 3, 3, 10, AIR);
        selectorBox(p, clip, r, w, c, 4, 1, 1, 4, 3, 1, false);
        selectorBox(p, clip, r, w, c, 4, 1, 3, 4, 3, 3, false);
        selectorBox(p, clip, r, w, c, 4, 1, 7, 4, 3, 7, false);
        selectorBox(p, clip, r, w, c, 4, 1, 9, 4, 3, 9, false);
        for (int y = 1; y <= 3; y++) {
            place(p, clip, w, c, 4, y, 4, bars(p, "north", "south"), true);
            place(p, clip, w, c, 4, y, 5, bars(p, "north", "south", "east"), true);
            place(p, clip, w, c, 4, y, 6, bars(p, "north", "south"), true);
            for (int x = 5; x <= 7; x++) place(p, clip, w, c, x, y, 5,
                    bars(p, "west", "east"), true);
        }
        place(p, clip, w, c, 4, 3, 2, bars(p, "north", "south"), true);
        place(p, clip, w, c, 4, 3, 8, bars(p, "north", "south"), true);
        for (int z : new int[]{2, 8}) {
            place(p, clip, w, c, 4, 1, z,
                    localDoorState(p, "iron_door", "west", "lower", "left"), false);
            place(p, clip, w, c, 4, 2, z,
                    localDoorState(p, "iron_door", "west", "upper", "left"), false);
        }
    }

    private static void room(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            BoundingBox clip, RandomSource r, WorldAccess w, Counter c) {
        selectorBox(p, clip, r, w, c, 0, 0, 0, 10, 6, 10, true);
        door(p, clip, w, c, p.door(), 4, 1, 0);
        fixedBox(p, clip, w, c, 4, 1, 10, 6, 3, 10, AIR);
        fixedBox(p, clip, w, c, 0, 1, 4, 0, 3, 6, AIR);
        fixedBox(p, clip, w, c, 10, 1, 4, 10, 3, 6, AIR);
        if (p.variant() == 0) roomPillar(p, clip, w, c);
        if (p.variant() == 1) roomFountain(p, clip, w, c);
        if (p.variant() == 2) roomStore(p, clip, r, w, c);
    }

    private static void roomPillar(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            BoundingBox clip, WorldAccess w, Counter c) {
        for (int y = 1; y <= 3; y++) place(p, clip, w, c, 5, y, 5, STONE, false);
        place(p, clip, w, c, 4, 3, 5, wallTorch(p, "west"), false);
        place(p, clip, w, c, 6, 3, 5, wallTorch(p, "east"), false);
        place(p, clip, w, c, 5, 3, 4, wallTorch(p, "south"), false);
        place(p, clip, w, c, 5, 3, 6, wallTorch(p, "north"), false);
        for (int x = 4; x <= 6; x++) for (int z = 4; z <= 6; z++)
            if (x != 5 || z != 5) place(p, clip, w, c, x, 1, z,
                    "minecraft:smooth_stone_slab[type=bottom,waterlogged=false]", false);
    }

    private static void roomFountain(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            BoundingBox clip, WorldAccess w, Counter c) {
        for (int i = 0; i < 5; i++) {
            place(p, clip, w, c, 3, 1, 3 + i, STONE, false);
            place(p, clip, w, c, 7, 1, 3 + i, STONE, false);
            place(p, clip, w, c, 3 + i, 1, 3, STONE, false);
            place(p, clip, w, c, 3 + i, 1, 7, STONE, false);
        }
        for (int y = 1; y <= 3; y++) place(p, clip, w, c, 5, y, 5, STONE, false);
        place(p, clip, w, c, 5, 4, 5, "minecraft:water[level=0]", false);
    }

    private static void roomStore(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            BoundingBox clip, RandomSource r, WorldAccess w, Counter c) {
        for (int z = 1; z <= 9; z++) {
            place(p, clip, w, c, 1, 3, z, "minecraft:cobblestone", false);
            place(p, clip, w, c, 9, 3, z, "minecraft:cobblestone", false);
        }
        for (int x = 1; x <= 9; x++) {
            place(p, clip, w, c, x, 3, 1, "minecraft:cobblestone", false);
            place(p, clip, w, c, x, 3, 9, "minecraft:cobblestone", false);
        }
        int[][] fixed = {{5,1,4},{5,1,6},{5,3,4},{5,3,6},{4,1,5},{6,1,5},{4,3,5},{6,3,5}};
        for (int[] a : fixed) place(p, clip, w, c, a[0], a[1], a[2], "minecraft:cobblestone", false);
        for (int y = 1; y <= 3; y++) for (int x : new int[]{4,6}) for (int z : new int[]{4,6})
            place(p, clip, w, c, x, y, z, "minecraft:cobblestone", false);
        place(p, clip, w, c, 5, 3, 5, wallTorch(p, "north"), false);
        for (int z = 2; z <= 8; z++) for (int x = 2; x <= 8; x++)
            if (x <= 3 || x >= 7 || z <= 3 || z >= 7)
                place(p, clip, w, c, x, 3, z, "minecraft:oak_planks", false);
        for (int y = 1; y <= 3; y++) place(p, clip, w, c, 9, y, 3,
                rotated(p, "minecraft:ladder[facing=west,waterlogged=false]"), false);
        chestAt(p, clip, r, w, c, 3, 4, 8, "minecraft:chests/stronghold_crossing");
    }

    private static void straightStairs(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            BoundingBox clip, RandomSource r, WorldAccess w, Counter c) {
        selectorBox(p, clip, r, w, c, 0, 0, 0, 4, 10, 7, true);
        door(p, clip, w, c, p.door(), 1, 7, 0);
        door(p, clip, w, c, Mc263StrongholdStartPieceFactoryAdapter.Door.OPENING, 1, 1, 7);
        String stair = rotated(p, "minecraft:cobblestone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]");
        for (int i = 0; i < 6; i++) for (int x = 1; x <= 3; x++) {
            place(p, clip, w, c, x, 6 - i, 1 + i, stair, false);
            if (i < 5) place(p, clip, w, c, x, 5 - i, 1 + i, STONE, false);
        }
    }

    private static void stairs(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            BoundingBox clip, RandomSource r, WorldAccess w, Counter c) {
        selectorBox(p, clip, r, w, c, 0, 0, 0, 4, 10, 4, true);
        door(p, clip, w, c, p.door(), 1, 7, 0);
        door(p, clip, w, c, Mc263StrongholdStartPieceFactoryAdapter.Door.OPENING, 1, 1, 4);
        int[][] stone = {{2,6,1},{1,5,1},{1,5,2},{1,4,3},{2,4,3},{3,3,3},{3,3,2},
                {3,2,1},{2,2,1},{1,1,1},{1,1,2}};
        int[][] slab = {{1,6,1},{1,5,3},{3,4,3},{3,3,1},{1,2,1},{1,1,3}};
        for (int[] a : stone) place(p, clip, w, c, a[0], a[1], a[2], STONE, false);
        for (int[] a : slab) place(p, clip, w, c, a[0], a[1], a[2],
                "minecraft:smooth_stone_slab[type=bottom,waterlogged=false]", false);
    }

    private static void five(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            BoundingBox clip, RandomSource r, WorldAccess w, Counter c) {
        selectorBox(p, clip, r, w, c, 0, 0, 0, 9, 8, 10, true);
        door(p, clip, w, c, p.door(), 4, 3, 0);
        if (p.first()) fixedBox(p, clip, w, c, 0, 3, 1, 0, 5, 3, AIR);
        if (p.third()) fixedBox(p, clip, w, c, 9, 3, 1, 9, 5, 3, AIR);
        if (p.second()) fixedBox(p, clip, w, c, 0, 5, 7, 0, 7, 9, AIR);
        if (p.fourth()) fixedBox(p, clip, w, c, 9, 5, 7, 9, 7, 9, AIR);
        fixedBox(p, clip, w, c, 5, 1, 10, 7, 3, 10, AIR);
        selectorBox(p, clip, r, w, c, 1, 2, 1, 8, 2, 6, false);
        selectorBox(p, clip, r, w, c, 4, 1, 5, 4, 4, 9, false);
        selectorBox(p, clip, r, w, c, 8, 1, 5, 8, 4, 9, false);
        selectorBox(p, clip, r, w, c, 1, 4, 7, 3, 4, 9, false);
        selectorBox(p, clip, r, w, c, 1, 3, 5, 3, 3, 6, false);
        fixedBox(p, clip, w, c, 1, 3, 4, 3, 3, 4,
                "minecraft:smooth_stone_slab[type=bottom,waterlogged=false]");
        fixedBox(p, clip, w, c, 1, 4, 6, 3, 4, 6,
                "minecraft:smooth_stone_slab[type=bottom,waterlogged=false]");
        selectorBox(p, clip, r, w, c, 5, 1, 7, 7, 1, 8, false);
        fixedBox(p, clip, w, c, 5, 1, 9, 7, 1, 9,
                "minecraft:smooth_stone_slab[type=bottom,waterlogged=false]");
        fixedBox(p, clip, w, c, 5, 2, 7, 7, 2, 7,
                "minecraft:smooth_stone_slab[type=bottom,waterlogged=false]");
        fixedBox(p, clip, w, c, 4, 5, 7, 4, 5, 9,
                "minecraft:smooth_stone_slab[type=bottom,waterlogged=false]");
        fixedBox(p, clip, w, c, 8, 5, 7, 8, 5, 9,
                "minecraft:smooth_stone_slab[type=bottom,waterlogged=false]");
        fixedBox(p, clip, w, c, 5, 5, 7, 7, 5, 9,
                "minecraft:smooth_stone_slab[type=double,waterlogged=false]");
        place(p, clip, w, c, 6, 5, 6, wallTorch(p, "south"), false);
    }

    private static boolean chest(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            BoundingBox clip, RandomSource r, WorldAccess w, Counter c) {
        selectorBox(p, clip, r, w, c, 0, 0, 0, 4, 4, 6, true);
        door(p, clip, w, c, p.door(), 1, 1, 0);
        door(p, clip, w, c, Mc263StrongholdStartPieceFactoryAdapter.Door.OPENING, 1, 1, 6);
        fixedBox(p, clip, w, c, 3, 1, 2, 3, 1, 4, STONE);
        int[][] slabs = {{3,1,1},{3,1,5},{3,2,2},{3,2,4},{2,1,2},{2,1,3},{2,1,4}};
        for (int[] a : slabs) place(p, clip, w, c, a[0], a[1], a[2],
                "minecraft:stone_brick_slab[type=bottom,waterlogged=false]", false);
        if (!p.placed() && clip.contains(worldPos(p, 3, 2, 3))) {
            chestAt(p, clip, r, w, c, 3, 2, 3, "minecraft:chests/stronghold_corridor");
            return true;
        }
        return p.placed();
    }

    private static void library(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            BoundingBox clip, RandomSource r, WorldAccess w, Counter c) {
        int height = p.tall() ? 11 : 6;
        selectorBox(p, clip, r, w, c, 0, 0, 0, 13, height - 1, 14, true);
        door(p, clip, w, c, p.door(), 4, 1, 0);
        maybeBox(p, clip, r, w, c, .07F, 2, 1, 1, 11, 4, 13, "minecraft:cobweb");
        for (int z = 1; z <= 13; z++) {
            boolean post = (z - 1) % 4 == 0;
            String state = post ? "minecraft:oak_planks" : "minecraft:bookshelf";
            fixedBox(p, clip, w, c, 1, 1, z, 1, 4, z, state);
            fixedBox(p, clip, w, c, 12, 1, z, 12, 4, z, state);
            if (post) {
                place(p, clip, w, c, 2, 3, z, wallTorch(p, "east"), false);
                place(p, clip, w, c, 11, 3, z, wallTorch(p, "west"), false);
            }
            if (p.tall()) {
                fixedBox(p, clip, w, c, 1, 6, z, 1, 9, z, state);
                fixedBox(p, clip, w, c, 12, 6, z, 12, 9, z, state);
            }
        }
        for (int z = 3; z < 12; z += 2) for (int x : new int[]{3,6,9})
            fixedBox(p, clip, w, c, x, 1, z, x + 1, 3, z, "minecraft:bookshelf");
        // The tall balcony's fixed placement order is intentionally kept in a dedicated helper.
        if (p.tall()) tallLibrary(p, clip, w, c);
        chestAt(p, clip, r, w, c, 3, 3, 5, "minecraft:chests/stronghold_library");
        if (p.tall()) {
            place(p, clip, w, c, 12, 9, 1, AIR, false);
            chestAt(p, clip, r, w, c, 12, 8, 1, "minecraft:chests/stronghold_library");
        }
    }

    private static void tallLibrary(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            BoundingBox clip, WorldAccess w, Counter c) {
        fixedBox(p, clip, w, c, 1, 5, 1, 3, 5, 13, "minecraft:oak_planks");
        fixedBox(p, clip, w, c, 10, 5, 1, 12, 5, 13, "minecraft:oak_planks");
        fixedBox(p, clip, w, c, 4, 5, 1, 9, 5, 2, "minecraft:oak_planks");
        fixedBox(p, clip, w, c, 4, 5, 12, 9, 5, 13, "minecraft:oak_planks");
        for (int[] a : new int[][]{{9,5,11},{8,5,11},{9,5,10}})
            place(p, clip, w, c, a[0], a[1], a[2], "minecraft:oak_planks", false);
        String ns = fence(p, "north", "south"), we = fence(p, "west", "east");
        fixedBox(p, clip, w, c, 3, 6, 3, 3, 6, 11, ns);
        fixedBox(p, clip, w, c, 10, 6, 3, 10, 6, 9, ns);
        fixedBox(p, clip, w, c, 4, 6, 2, 9, 6, 2, we);
        fixedBox(p, clip, w, c, 4, 6, 12, 7, 6, 12, we);
        place(p, clip, w, c, 3, 6, 2, fence(p, "north", "east"), true);
        place(p, clip, w, c, 3, 6, 12, fence(p, "south", "east"), true);
        place(p, clip, w, c, 10, 6, 2, fence(p, "north", "west"), true);
        for (int i = 0; i <= 2; i++) {
            place(p, clip, w, c, 8 + i, 6, 12 - i, fence(p, "south", "west"), true);
            if (i < 2) place(p, clip, w, c, 8 + i, 6, 11 - i,
                    fence(p, "north", "east"), true);
        }
        for (int y = 1; y <= 7; y++) place(p, clip, w, c, 10, y, 13,
                rotated(p, "minecraft:ladder[facing=south,waterlogged=false]"), false);
        String e = fence(p, "east"), west = fence(p, "west"), all = fence(p,
                "north", "south", "west", "east");
        for (int[] a : new int[][]{{6,9,7},{6,8,7},{5,7,7}})
            place(p, clip, w, c, a[0], a[1], a[2], e, true);
        for (int[] a : new int[][]{{7,9,7},{7,8,7},{8,7,7}})
            place(p, clip, w, c, a[0], a[1], a[2], west, true);
        place(p, clip, w, c, 6, 7, 7, all, true); place(p, clip, w, c, 7, 7, 7, all, true);
        place(p, clip, w, c, 6, 7, 6, fence(p, "east", "north"), true);
        place(p, clip, w, c, 6, 7, 8, fence(p, "east", "south"), true);
        place(p, clip, w, c, 7, 7, 6, fence(p, "west", "north"), true);
        place(p, clip, w, c, 7, 7, 8, fence(p, "west", "south"), true);
        for (int[] a : new int[][]{{5,8,7},{8,8,7},{6,8,6},{6,8,8},{7,8,6},{7,8,8}})
            place(p, clip, w, c, a[0], a[1], a[2], "minecraft:torch", false);
    }

    private static boolean portal(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            BoundingBox clip, RandomSource r, WorldAccess w, Counter c) {
        selectorBox(p, clip, r, w, c, 0, 0, 0, 10, 7, 15, false);
        door(p, clip, w, c, Mc263StrongholdStartPieceFactoryAdapter.Door.GRATES, 4, 1, 0);
        selectorBox(p, clip, r, w, c, 1, 6, 1, 1, 6, 14, false);
        selectorBox(p, clip, r, w, c, 9, 6, 1, 9, 6, 14, false);
        selectorBox(p, clip, r, w, c, 2, 6, 1, 8, 6, 2, false);
        selectorBox(p, clip, r, w, c, 2, 6, 14, 8, 6, 14, false);
        selectorBox(p, clip, r, w, c, 1, 1, 1, 2, 1, 4, false);
        selectorBox(p, clip, r, w, c, 8, 1, 1, 9, 1, 4, false);
        fixedBox(p, clip, w, c, 1, 1, 1, 1, 1, 3, "minecraft:lava[level=0]");
        fixedBox(p, clip, w, c, 9, 1, 1, 9, 1, 3, "minecraft:lava[level=0]");
        selectorBox(p, clip, r, w, c, 3, 1, 8, 7, 1, 12, false);
        fixedBox(p, clip, w, c, 4, 1, 9, 6, 1, 11, "minecraft:lava[level=0]");
        for (int z = 3; z < 14; z += 2) for (int x : new int[]{0,10})
            fixedBox(p, clip, w, c, x, 3, z, x, 4, z, bars(p, "north", "south"));
        for (int x = 2; x < 9; x += 2)
            fixedBox(p, clip, w, c, x, 3, 15, x, 4, 15, bars(p, "west", "east"));
        selectorBox(p, clip, r, w, c, 4, 1, 5, 6, 1, 7, false);
        selectorBox(p, clip, r, w, c, 4, 2, 6, 6, 2, 7, false);
        selectorBox(p, clip, r, w, c, 4, 3, 7, 6, 3, 7, false);
        String stair = rotated(p, "minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
        for (int x = 4; x <= 6; x++) for (int i = 0; i < 3; i++)
            place(p, clip, w, c, x, 1 + i, 4 + i, stair, false);
        boolean all = true; boolean[] eyes = new boolean[12];
        for (int i = 0; i < 12; i++) { c.random++; eyes[i] = r.nextFloat() > .9F; all &= eyes[i]; }
        int[][] frames = {{4,3,8,0},{5,3,8,0},{6,3,8,0},{4,3,12,1},{5,3,12,1},{6,3,12,1},
                {3,3,9,2},{3,3,10,2},{3,3,11,2},{7,3,9,3},{7,3,10,3},{7,3,11,3}};
        String[] dirs = {"north","south","east","west"};
        for (int i = 0; i < frames.length; i++) {
            int[] a = frames[i]; place(p, clip, w, c, a[0], a[1], a[2], rotated(p,
                    "minecraft:end_portal_frame[eye=" + eyes[i] + ",facing=" + dirs[a[3]] + "]"), false);
        }
        if (all) fixedBox(p, clip, w, c, 4, 3, 9, 6, 3, 11, "minecraft:end_portal");
        BlockPos spawner = worldPos(p, 5, 3, 6);
        if (!p.placed() && clip.contains(spawner)) {
            w.setBlock(spawner, "minecraft:spawner", 2);
            c.writes++;
            w.configureSpawner(spawner, "minecraft:silverfish", r);
            return true;
        }
        return p.placed();
    }

    private static void selectorBox(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            BoundingBox clip, RandomSource r, WorldAccess w, Counter c, int x1, int y1, int z1,
            int x2, int y2, int z2, boolean skipAir) {
        for (int y = y1; y <= y2; y++) for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                BlockPos pos = worldPos(p, x, y, z); if (!clip.contains(pos)) continue;
                if (skipAir) { c.reads++; if (w.isAir(pos)) continue; }
                boolean edge = y == y1 || y == y2 || x == x1 || x == x2 || z == z1 || z == z2;
                String state = AIR;
                if (edge) { c.random++; float f = r.nextFloat(); state = f < .2F ? CRACKED
                        : f < .5F ? MOSSY : f < .55F ? INFESTED : STONE; }
                place(p, clip, w, c, x, y, z, state, false);
            }
    }

    private static void fixedBox(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            BoundingBox clip, WorldAccess w, Counter c, int x1, int y1, int z1,
            int x2, int y2, int z2, String state) {
        boolean post = state.startsWith("minecraft:iron_bars[")
                || state.startsWith("minecraft:oak_fence[");
        for (int y = y1; y <= y2; y++) for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) place(p, clip, w, c, x, y, z, state, post);
    }

    private static void maybeBox(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            BoundingBox clip, RandomSource r, WorldAccess w, Counter c, float chance,
            int x1, int y1, int z1, int x2, int y2, int z2, String state) {
        for (int y = y1; y <= y2; y++) for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                c.random++; if (r.nextFloat() <= chance) place(p, clip, w, c, x, y, z, state, false);
            }
    }

    private static void maybe(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            BoundingBox clip, RandomSource r, WorldAccess w, Counter c, float chance,
            int x, int y, int z, String state) {
        c.random++; if (r.nextFloat() < chance) place(p, clip, w, c, x, y, z, state, false);
    }

    private static void door(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            BoundingBox clip, WorldAccess w, Counter c,
            Mc263StrongholdStartPieceFactoryAdapter.Door door, int x, int y, int z) {
        switch (door) {
            case OPENING -> fixedBox(p, clip, w, c, x, y, z, x + 2, y + 2, z, AIR);
            case WOOD_DOOR, IRON_DOOR -> {
                int[][] frame = {{0,0},{0,1},{0,2},{1,2},{2,2},{2,1},{2,0}};
                for (int[] a : frame) place(p, clip, w, c, x + a[0], y + a[1], z, STONE, false);
                String block = door == Mc263StrongholdStartPieceFactoryAdapter.Door.WOOD_DOOR
                        ? "oak_door" : "iron_door";
                place(p, clip, w, c, x + 1, y, z, doorState(p, block, "lower"), false);
                place(p, clip, w, c, x + 1, y + 1, z, doorState(p, block, "upper"), false);
                if (door == Mc263StrongholdStartPieceFactoryAdapter.Door.IRON_DOOR) {
                    place(p, clip, w, c, x + 2, y + 1, z + 1, button(p, "north"), false);
                    place(p, clip, w, c, x + 2, y + 1, z - 1, button(p, "south"), false);
                }
            }
            case GRATES -> {
                place(p, clip, w, c, x + 1, y, z, AIR, false);
                place(p, clip, w, c, x + 1, y + 1, z, AIR, false);
                place(p, clip, w, c, x, y, z, bars(p, "west"), true);
                place(p, clip, w, c, x, y + 1, z, bars(p, "west"), true);
                for (int dx = 0; dx <= 2; dx++) place(p, clip, w, c, x + dx, y + 2, z,
                        bars(p, "east", "west"), true);
                place(p, clip, w, c, x + 2, y + 1, z, bars(p, "east"), true);
                place(p, clip, w, c, x + 2, y, z, bars(p, "east"), true);
            }
        }
    }

    private static void chestAt(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            BoundingBox clip, RandomSource r, WorldAccess w, Counter c,
            int x, int y, int z, String table) {
        BlockPos pos = worldPos(p, x, y, z);
        if (clip.contains(pos)) {
            w.createLootChest(pos, table, r);
            c.writes++;
        }
    }

    private static void place(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            BoundingBox clip, WorldAccess w, Counter c, int x, int y, int z,
            String state, boolean post) {
        BlockPos pos = worldPos(p, x, y, z); if (!clip.contains(pos)) return;
        w.setBlock(pos, state, 2); c.writes++;
        String fluid = Objects.requireNonNull(w.postWriteFluidType(pos), "post-write fluid");
        if (!fluid.isEmpty()) w.scheduleFluidTick(pos, fluid, 0);
        if (post || shapeCheck(state)) { w.markForPostprocessing(pos); c.post++; }
    }

    private static boolean shapeCheck(String state) {
        return state.startsWith("minecraft:torch")
                || state.startsWith("minecraft:wall_torch[")
                || state.startsWith("minecraft:oak_fence[")
                || state.startsWith("minecraft:ladder[")
                || state.startsWith("minecraft:iron_bars[");
    }

    public static BlockPos worldPos(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            int x, int y, int z) {
        Mc263StrongholdGraphCarrier.BoundingBox b = p.box();
        int wx = switch (p.orientation()) {
            case NORTH, SOUTH -> Math.addExact(b.minX(), x);
            case WEST -> Math.subtractExact(b.maxX(), z);
            case EAST -> Math.addExact(b.minX(), z);
        };
        int wz = switch (p.orientation()) {
            case NORTH -> Math.subtractExact(b.maxZ(), z);
            case SOUTH -> Math.addExact(b.minZ(), z);
            case WEST, EAST -> Math.addExact(b.minZ(), x);
        };
        return new BlockPos(wx, Math.addExact(b.minY(), y), wz);
    }

    private static String wallTorch(Mc263StrongholdStartPieceFactoryAdapter.PieceData p, String d) {
        return rotated(p, "minecraft:wall_torch[facing=" + d + "]");
    }
    private static String doorState(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            String block, String half) {
        return localDoorState(p, block, "north", half, "left");
    }
    private static String localDoorState(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            String block, String localFacing, String half, String baseHinge) {
        boolean mirrored = p.orientation() == Mc263StrongholdGraphCarrier.Orientation.SOUTH
                || p.orientation() == Mc263StrongholdGraphCarrier.Orientation.WEST;
        String hinge = mirrored ? ("left".equals(baseHinge) ? "right" : "left") : baseHinge;
        return "minecraft:" + block + "[facing=" + direction(p, localFacing) + ",half=" + half
                + ",hinge=" + hinge + ",open=false,powered=false]";
    }
    private static String button(Mc263StrongholdStartPieceFactoryAdapter.PieceData p, String d) {
        return "minecraft:stone_button[face=wall,facing=" + direction(p, d) + ",powered=false]";
    }
    private static String bars(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            String... connections) { return connections(p, "iron_bars", connections); }
    private static String fence(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            String... connections) { return connections(p, "oak_fence", connections); }
    private static String connections(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            String block, String... connections) {
        Set<String> rotated = new java.util.HashSet<>();
        for (String connection : connections) rotated.add(direction(p, connection));
        return "minecraft:" + block + "[east=" + rotated.contains("east") + ",north="
                + rotated.contains("north") + ",south=" + rotated.contains("south")
                + ",waterlogged=false,west=" + rotated.contains("west") + "]";
    }
    private static String rotated(Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            String state) {
        int at = state.indexOf("facing="); if (at < 0) return state;
        int start = at + 7; int end = state.indexOf(',', start);
        if (end < 0) end = state.indexOf(']', start);
        String old = state.substring(start, end);
        return state.substring(0, start) + direction(p, old) + state.substring(end);
    }
    private static String direction(Mc263StrongholdStartPieceFactoryAdapter.PieceData p, String d) {
        int base = switch (d) { case "south" -> 0; case "west" -> 1;
            case "north" -> 2; case "east" -> 3;
            default -> throw new IllegalArgumentException("non-horizontal direction"); };
        int turn = switch (p.orientation()) { case NORTH -> 0; case EAST -> 1;
            case SOUTH -> 2; case WEST -> 3; };
        return switch (Math.floorMod(base + turn, 4)) { case 0 -> "south"; case 1 -> "west";
            case 2 -> "north"; default -> "east"; };
    }
    private static void addDoorStates(Set<String> states,
            Mc263StrongholdStartPieceFactoryAdapter.PieceData p,
            Mc263StrongholdStartPieceFactoryAdapter.Door door) {
        switch (door) {
            case OPENING -> { }
            case WOOD_DOOR -> {
                states.add(doorState(p, "oak_door", "lower"));
                states.add(doorState(p, "oak_door", "upper"));
            }
            case IRON_DOOR -> {
                states.add(doorState(p, "iron_door", "lower"));
                states.add(doorState(p, "iron_door", "upper"));
                states.add(button(p, "north")); states.add(button(p, "south"));
            }
            case GRATES -> {
                states.add(bars(p, "west")); states.add(bars(p, "east"));
                states.add(bars(p, "west", "east"));
            }
        }
    }
    private static int[] dimensions(Mc263StrongholdGraphCarrier.PieceType type, boolean tall) {
        return switch (type) {
            case STRAIGHT, CHEST_CORRIDOR -> new int[]{5,5,7};
            case PRISON_HALL -> new int[]{9,5,11}; case ROOM_CROSSING -> new int[]{11,7,11};
            case STRAIGHT_STAIRS_DOWN -> new int[]{5,11,8};
            case STAIRS_DOWN, START -> new int[]{5,11,5}; case FIVE_CROSSING -> new int[]{10,9,11};
            case LIBRARY -> new int[]{14,tall ? 11 : 6,15}; case PORTAL_ROOM -> new int[]{11,8,16};
            default -> throw new IllegalArgumentException("unowned stronghold executor type");
        };
    }
    private static void require(boolean value, String capability) {
        if (!value) throw new UnsupportedOperationException(capability);
    }
    private static final class Counter { int reads, random, writes, post; }
}
