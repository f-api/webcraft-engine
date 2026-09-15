package com.gameexpert.terrain.mc.structure;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Exact dormant executor for the pinned 26.3 {@code minecraft:shlt} piece only. */
public final class Mc263StrongholdLeftTurnPieceExecutor {
    public static final String PIECE_TYPE = "minecraft:shlt";
    public static final String STONE_BRICKS = "minecraft:stone_bricks";
    public static final String CRACKED_STONE_BRICKS = "minecraft:cracked_stone_bricks";
    public static final String MOSSY_STONE_BRICKS = "minecraft:mossy_stone_bricks";
    public static final String INFESTED_STONE_BRICKS = "minecraft:infested_stone_bricks";
    public static final String CAVE_AIR = "minecraft:cave_air";

    private Mc263StrongholdLeftTurnPieceExecutor() { }

    public enum Orientation {
        SOUTH(0), WEST(1), NORTH(2), EAST(3);
        private final int nbtId;
        Orientation(int nbtId) { this.nbtId = nbtId; }
        public int nbtId() { return nbtId; }
        public static Orientation fromNbtId(int id) {
            for (Orientation value : values()) if (value.nbtId == id) return value;
            throw new IllegalArgumentException("stronghold-left-turn orientation outside 0..3");
        }
    }

    public enum EntryDoor {
        OPENING, WOOD_DOOR, GRATES, IRON_DOOR;
        static EntryDoor decode(String value) {
            try { return valueOf(value); }
            catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("unknown stronghold entry door", exception);
            }
        }
    }

    public record BlockPos(int x, int y, int z) { }

    public record BoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public BoundingBox {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted stronghold-left-turn bounding box");
            }
        }
        boolean contains(BlockPos position) {
            return position.x >= minX && position.x <= maxX
                    && position.y >= minY && position.y <= maxY
                    && position.z >= minZ && position.z <= maxZ;
        }
        boolean intersects(BoundingBox other) {
            return maxX >= other.minX && minX <= other.maxX
                    && maxY >= other.minY && minY <= other.maxY
                    && maxZ >= other.minZ && minZ <= other.maxZ;
        }
    }

    public record PieceFacts(String pieceType, BoundingBox boundingBox, int generationDepth,
                             Orientation orientation, EntryDoor entryDoor) {
        public PieceFacts {
            Objects.requireNonNull(pieceType, "pieceType");
            Objects.requireNonNull(boundingBox, "boundingBox");
            Objects.requireNonNull(orientation, "orientation");
            Objects.requireNonNull(entryDoor, "entryDoor");
        }
    }

    public interface RandomSource { float nextFloat(); }

    public interface WorldAccess {
        boolean supportsExactState(String exactState);
        boolean supportsLiveAirQueries();
        boolean supportsPostWriteFluidStateQueries();
        boolean supportsScheduledFluidTicks();
        boolean supportsPostprocessingMarks();
        boolean isAir(BlockPos position);
        boolean setBlock(BlockPos position, String exactState, int flags);
        String postWriteFluidType(BlockPos position);
        void scheduleFluidTick(BlockPos position, String fluidType, int delay);
        void markForPostprocessing(BlockPos position);
    }

    public record ExecutionResult(int liveReads, int randomDraws, int writes,
                                  int postprocessingMarks) { }

    public static ExecutionResult execute(PieceFacts piece, BoundingBox placementClip,
            RandomSource random, WorldAccess world) {
        Objects.requireNonNull(placementClip, "placementClip");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(world, "world");
        preflight(piece, world);
        Counter counter = new Counter();

        // StructurePiece.generateBox order is y, x, z. Outside-clip cells read as air without a
        // world observation; live air skips the selector and therefore consumes no RNG.
        for (int y = 0; y <= 4; y++) {
            for (int x = 0; x <= 4; x++) {
                for (int z = 0; z <= 4; z++) {
                    BlockPos position = worldPos(piece, x, y, z);
                    if (!placementClip.contains(position)) continue;
                    counter.liveReads++;
                    if (world.isAir(position)) continue;
                    boolean edge = y == 0 || y == 4 || x == 0 || x == 4 || z == 0 || z == 4;
                    String state = CAVE_AIR;
                    if (edge) {
                        counter.randomDraws++;
                        float selection = random.nextFloat();
                        state = selection < 0.2F ? CRACKED_STONE_BRICKS
                                : selection < 0.5F ? MOSSY_STONE_BRICKS
                                : selection < 0.55F ? INFESTED_STONE_BRICKS : STONE_BRICKS;
                    }
                    place(piece, placementClip, world, counter, x, y, z, state, false);
                }
            }
        }
        generateDoor(piece, placementClip, world, counter);
        int sideX = piece.orientation() == Orientation.NORTH
                || piece.orientation() == Orientation.EAST ? 0 : 4;
        for (int y = 1; y <= 3; y++) {
            for (int z = 1; z <= 3; z++) {
                place(piece, placementClip, world, counter, sideX, y, z, CAVE_AIR, false);
            }
        }
        return new ExecutionResult(counter.liveReads, counter.randomDraws, counter.writes,
                counter.postprocessingMarks);
    }

    static void preflight(PieceFacts piece, WorldAccess world) {
        Objects.requireNonNull(piece, "piece");
        if (!PIECE_TYPE.equals(piece.pieceType())) {
            throw new IllegalArgumentException("expected exact minecraft:shlt piece type");
        }
        if (piece.generationDepth() < 1 || piece.generationDepth() > 51) {
            throw new IllegalArgumentException("stronghold-left-turn depth outside 1..51");
        }
        BoundingBox box = piece.boundingBox();
        int xSpan = Math.addExact(Math.subtractExact(box.maxX(), box.minX()), 1);
        int ySpan = Math.addExact(Math.subtractExact(box.maxY(), box.minY()), 1);
        int zSpan = Math.addExact(Math.subtractExact(box.maxZ(), box.minZ()), 1);
        if (xSpan != 5 || ySpan != 5 || zSpan != 5) {
            throw new IllegalArgumentException("stronghold-left-turn dimensions must be 5x5x5");
        }
        // The second iron-door button is deliberately one local block outside the piece box.
        // Resolve it before any selected carrier piece may mutate the world.
        if (piece.entryDoor() == EntryDoor.IRON_DOOR) worldPos(piece, 3, 2, -1);
        for (String state : requiredStates(piece)) {
            if (!world.supportsExactState(state)) {
                throw new UnsupportedOperationException("stronghold-left-turn state " + state);
            }
        }
        require(world.supportsLiveAirQueries(), "live air queries");
        require(world.supportsPostWriteFluidStateQueries(), "post-write fluid queries");
        require(world.supportsScheduledFluidTicks(), "scheduled fluid ticks");
        if (piece.entryDoor() == EntryDoor.GRATES) {
            require(world.supportsPostprocessingMarks(), "POST marks");
        }
    }

    static Set<String> requiredStates(PieceFacts piece) {
        LinkedHashSet<String> states = new LinkedHashSet<>(Set.of(CRACKED_STONE_BRICKS,
                MOSSY_STONE_BRICKS, INFESTED_STONE_BRICKS, STONE_BRICKS, CAVE_AIR));
        switch (piece.entryDoor()) {
            case OPENING -> { }
            case WOOD_DOOR -> {
                states.add(door("oak_door", piece.orientation(), "lower"));
                states.add(door("oak_door", piece.orientation(), "upper"));
            }
            case GRATES -> {
                states.add(bars(piece.orientation(), "west"));
                states.add(bars(piece.orientation(), "east", "west"));
                states.add(bars(piece.orientation(), "east"));
            }
            case IRON_DOOR -> {
                states.add(door("iron_door", piece.orientation(), "lower"));
                states.add(door("iron_door", piece.orientation(), "upper"));
                states.add(button(piece.orientation(), "north"));
                states.add(button(piece.orientation(), "south"));
            }
        }
        return Set.copyOf(states);
    }

    private static void require(boolean supported, String capability) {
        if (!supported) throw new UnsupportedOperationException(capability);
    }

    private static void generateDoor(PieceFacts piece, BoundingBox clip, WorldAccess world,
            Counter counter) {
        switch (piece.entryDoor()) {
            case OPENING -> box3(piece, clip, world, counter, CAVE_AIR);
            case WOOD_DOOR -> {
                stoneFrame(piece, clip, world, counter);
                place(piece, clip, world, counter, 2, 1, 0,
                        door("oak_door", piece.orientation(), "lower"), false);
                place(piece, clip, world, counter, 2, 2, 0,
                        door("oak_door", piece.orientation(), "upper"), false);
            }
            case GRATES -> {
                place(piece, clip, world, counter, 2, 1, 0, CAVE_AIR, false);
                place(piece, clip, world, counter, 2, 2, 0, CAVE_AIR, false);
                for (int y = 1; y <= 2; y++) {
                    place(piece, clip, world, counter, 1, y, 0,
                            bars(piece.orientation(), "west"), true);
                }
                for (int x = 1; x <= 3; x++) {
                    place(piece, clip, world, counter, x, 3, 0,
                            bars(piece.orientation(), "east", "west"), true);
                }
                for (int y = 2; y >= 1; y--) {
                    place(piece, clip, world, counter, 3, y, 0,
                            bars(piece.orientation(), "east"), true);
                }
            }
            case IRON_DOOR -> {
                stoneFrame(piece, clip, world, counter);
                place(piece, clip, world, counter, 2, 1, 0,
                        door("iron_door", piece.orientation(), "lower"), false);
                place(piece, clip, world, counter, 2, 2, 0,
                        door("iron_door", piece.orientation(), "upper"), false);
                place(piece, clip, world, counter, 3, 2, 1,
                        button(piece.orientation(), "north"), false);
                place(piece, clip, world, counter, 3, 2, -1,
                        button(piece.orientation(), "south"), false);
            }
        }
    }

    private static void box3(PieceFacts piece, BoundingBox clip, WorldAccess world,
            Counter counter, String state) {
        for (int y = 1; y <= 3; y++) for (int x = 1; x <= 3; x++) {
            place(piece, clip, world, counter, x, y, 0, state, false);
        }
    }

    private static void stoneFrame(PieceFacts piece, BoundingBox clip, WorldAccess world,
            Counter counter) {
        int[][] cells = {{1, 1}, {1, 2}, {1, 3}, {2, 3}, {3, 3}, {3, 2}, {3, 1}};
        for (int[] cell : cells) {
            place(piece, clip, world, counter, cell[0], cell[1], 0, STONE_BRICKS, false);
        }
    }

    private static void place(PieceFacts piece, BoundingBox clip, WorldAccess world,
            Counter counter, int x, int y, int z, String state, boolean post) {
        BlockPos position = worldPos(piece, x, y, z);
        if (!clip.contains(position)) return;
        world.setBlock(position, state, 2);
        counter.writes++;
        String fluid = Objects.requireNonNull(world.postWriteFluidType(position),
                "post-write fluid type");
        if (!fluid.isEmpty()) world.scheduleFluidTick(position, fluid, 0);
        if (post) {
            world.markForPostprocessing(position);
            counter.postprocessingMarks++;
        }
    }

    static BlockPos worldPos(PieceFacts piece, int x, int y, int z) {
        BoundingBox box = piece.boundingBox();
        int worldX = switch (piece.orientation()) {
            case NORTH, SOUTH -> Math.addExact(box.minX(), x);
            case WEST -> Math.subtractExact(box.maxX(), z);
            case EAST -> Math.addExact(box.minX(), z);
        };
        int worldZ = switch (piece.orientation()) {
            case NORTH -> Math.subtractExact(box.maxZ(), z);
            case SOUTH -> Math.addExact(box.minZ(), z);
            case WEST, EAST -> Math.addExact(box.minZ(), x);
        };
        return new BlockPos(worldX, Math.addExact(box.minY(), y), worldZ);
    }

    private static String door(String block, Orientation orientation, String half) {
        String hinge = orientation == Orientation.SOUTH || orientation == Orientation.WEST
                ? "right" : "left";
        return "minecraft:" + block + "[facing=" + direction(orientation, "north")
                + ",half=" + half + ",hinge=" + hinge + ",open=false,powered=false]";
    }

    private static String button(Orientation orientation, String facing) {
        return "minecraft:stone_button[face=wall,facing=" + direction(orientation, facing)
                + ",powered=false]";
    }

    private static String bars(Orientation orientation, String... connections) {
        Set<String> rotated = new java.util.HashSet<>();
        for (String connection : connections) rotated.add(direction(orientation, connection));
        return "minecraft:iron_bars[east=" + rotated.contains("east")
                + ",north=" + rotated.contains("north")
                + ",south=" + rotated.contains("south") + ",waterlogged=false,west="
                + rotated.contains("west") + "]";
    }

    private static String direction(Orientation orientation, String direction) {
        int base = switch (direction) {
            case "south" -> 0; case "west" -> 1; case "north" -> 2; case "east" -> 3;
            default -> throw new IllegalArgumentException("non-horizontal direction");
        };
        int northToOrientation = switch (orientation) {
            case NORTH -> 0; case EAST -> 1; case SOUTH -> 2; case WEST -> 3;
        };
        int rotated = Math.floorMod(base + northToOrientation, 4);
        return switch (rotated) { case 0 -> "south"; case 1 -> "west";
            case 2 -> "north"; default -> "east"; };
    }

    private static final class Counter {
        int liveReads; int randomDraws; int writes; int postprocessingMarks;
    }
}
