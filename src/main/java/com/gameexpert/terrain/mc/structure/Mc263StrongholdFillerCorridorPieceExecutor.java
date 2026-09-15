package com.gameexpert.terrain.mc.structure;

import java.util.Objects;

/**
 * Exact dormant executor for the pinned 26.3 {@code minecraft:shfc} piece.
 *
 * <p>This is only the independently complete filler-corridor piece tranche. It does not make a
 * stronghold start complete and is not registered with the canonical structure dispatcher.</p>
 */
public final class Mc263StrongholdFillerCorridorPieceExecutor {
    public static final String PIECE_TYPE = "minecraft:shfc";
    public static final String STONE_BRICKS = "minecraft:stone_bricks";
    public static final String CAVE_AIR = "minecraft:cave_air";

    private Mc263StrongholdFillerCorridorPieceExecutor() { }

    public enum Orientation {
        SOUTH(0), WEST(1), NORTH(2), EAST(3);

        private final int nbtId;

        Orientation(int nbtId) { this.nbtId = nbtId; }

        public int nbtId() { return nbtId; }

        public static Orientation fromNbtId(int id) {
            for (Orientation orientation : values()) {
                if (orientation.nbtId == id) return orientation;
            }
            throw new IllegalArgumentException("stronghold filler orientation outside 0..3");
        }
    }

    public record BlockPos(int x, int y, int z) { }

    public record BoundingBox(int minX, int minY, int minZ,
                              int maxX, int maxY, int maxZ) {
        public BoundingBox {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted stronghold-filler bounding box");
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

    /** Typed facts already bound to the exact persisted piece NBT by the carrier bridge. */
    public record PieceFacts(String pieceType, BoundingBox boundingBox, int generationDepth,
                             Orientation orientation, int steps) {
        public PieceFacts {
            Objects.requireNonNull(pieceType, "pieceType");
            Objects.requireNonNull(boundingBox, "boundingBox");
            Objects.requireNonNull(orientation, "orientation");
        }
    }

    public interface WorldAccess {
        /** Pure capability declaration; it must not observe mutable world state. */
        boolean supportsExactState(String exactState);
        /** Pure capability declaration; it must not observe mutable world state. */
        boolean supportsPostWriteFluidStateQueries();
        /** Pure capability declaration; it must not observe mutable world state. */
        boolean supportsScheduledFluidTicks();
        /** The official return value is ignored. */
        boolean setBlock(BlockPos position, String exactState, int flags);
        /** Empty means that the exact live state after the attempted write has no fluid. */
        String postWriteFluidType(BlockPos position);
        void scheduleFluidTick(BlockPos position, String fluidType, int delay);
    }

    public record ExecutionResult(int writes) { }

    public static ExecutionResult execute(PieceFacts piece, BoundingBox placementClip,
            WorldAccess world) {
        Objects.requireNonNull(placementClip, "placementClip");
        Objects.requireNonNull(world, "world");
        preflight(piece, world);
        int writes = 0;
        for (int step = 0; step < piece.steps(); step++) {
            for (int x = 0; x <= 4; x++) {
                writes += placeBlock(piece, placementClip, world, x, 0, step, STONE_BRICKS);
            }
            for (int y = 1; y <= 3; y++) {
                writes += placeBlock(piece, placementClip, world, 0, y, step, STONE_BRICKS);
                writes += placeBlock(piece, placementClip, world, 1, y, step, CAVE_AIR);
                writes += placeBlock(piece, placementClip, world, 2, y, step, CAVE_AIR);
                writes += placeBlock(piece, placementClip, world, 3, y, step, CAVE_AIR);
                writes += placeBlock(piece, placementClip, world, 4, y, step, STONE_BRICKS);
            }
            for (int x = 0; x <= 4; x++) {
                writes += placeBlock(piece, placementClip, world, x, 4, step, STONE_BRICKS);
            }
        }
        return new ExecutionResult(writes);
    }

    static void preflight(PieceFacts piece, WorldAccess world) {
        Objects.requireNonNull(piece, "piece");
        if (!PIECE_TYPE.equals(piece.pieceType())) {
            throw new IllegalArgumentException("expected exact minecraft:shfc piece type");
        }
        if (piece.generationDepth() < 1 || piece.generationDepth() > 51) {
            throw new IllegalArgumentException("stronghold-filler generation depth outside 1..51");
        }
        if (piece.steps() < 2 || piece.steps() > 3) {
            throw new IllegalArgumentException("stronghold-filler steps outside 2..3");
        }
        BoundingBox box = piece.boundingBox();
        int xSpan = Math.subtractExact(box.maxX(), box.minX()) + 1;
        int ySpan = Math.subtractExact(box.maxY(), box.minY()) + 1;
        int zSpan = Math.subtractExact(box.maxZ(), box.minZ()) + 1;
        boolean zAxis = piece.orientation() == Orientation.NORTH
                || piece.orientation() == Orientation.SOUTH;
        if (ySpan != 5 || xSpan != (zAxis ? 5 : piece.steps())
                || zSpan != (zAxis ? piece.steps() : 5)) {
            throw new IllegalArgumentException(
                    "stronghold-filler dimensions disagree with orientation/steps");
        }
        require(world.supportsExactState(STONE_BRICKS), "stronghold stone-bricks state");
        require(world.supportsExactState(CAVE_AIR), "stronghold cave-air state");
        require(world.supportsPostWriteFluidStateQueries(), "post-write fluid-state queries");
        require(world.supportsScheduledFluidTicks(), "scheduled fluid ticks");
    }

    private static void require(boolean supported, String capability) {
        if (!supported) throw new UnsupportedOperationException(capability);
    }

    private static int placeBlock(PieceFacts piece, BoundingBox clip, WorldAccess world,
            int localX, int localY, int localZ, String exactState) {
        BlockPos position = worldPos(piece, localX, localY, localZ);
        if (!clip.contains(position)) return 0;
        world.setBlock(position, exactState, 2);
        String fluid = Objects.requireNonNull(world.postWriteFluidType(position),
                "post-write fluid type");
        if (!fluid.isEmpty()) world.scheduleFluidTick(position, fluid, 0);
        return 1;
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
}
