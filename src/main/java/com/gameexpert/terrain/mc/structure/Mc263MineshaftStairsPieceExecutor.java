package com.gameexpert.terrain.mc.structure;

import java.util.Objects;

/** Exact dormant executor for the pinned 26.3 {@code minecraft:msstairs} piece. */
public final class Mc263MineshaftStairsPieceExecutor {
    public static final String PIECE_TYPE = "minecraft:msstairs";
    public static final String CAVE_AIR = "minecraft:cave_air";

    private Mc263MineshaftStairsPieceExecutor() { }

    public enum Orientation {
        SOUTH(0), WEST(1), NORTH(2), EAST(3);

        private final int nbtId;
        Orientation(int nbtId) { this.nbtId = nbtId; }
        public int nbtId() { return nbtId; }
        public static Orientation fromNbtId(int id) {
            for (Orientation orientation : values()) if (orientation.nbtId == id) return orientation;
            throw new IllegalArgumentException("mineshaft-stairs orientation outside 0..3");
        }
    }

    public enum MineshaftType {
        NORMAL(0, "minecraft:oak_planks", "minecraft:oak_log", "minecraft:oak_fence"),
        MESA(1, "minecraft:dark_oak_planks", "minecraft:dark_oak_log",
                "minecraft:dark_oak_fence");

        private final int nbtId;
        private final String planks;
        private final String wood;
        private final String fence;

        MineshaftType(int nbtId, String planks, String wood, String fence) {
            this.nbtId = nbtId; this.planks = planks; this.wood = wood; this.fence = fence;
        }
        public int nbtId() { return nbtId; }
        public static MineshaftType fromNbtId(int id) {
            for (MineshaftType type : values()) if (type.nbtId == id) return type;
            throw new IllegalArgumentException("mineshaft type outside 0..1");
        }
    }

    public record BlockPos(int x, int y, int z) { }

    public record BoundingBox(int minX, int minY, int minZ,
                              int maxX, int maxY, int maxZ) {
        public BoundingBox {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted mineshaft-stairs bounding box");
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
                             Orientation orientation, MineshaftType mineshaftType) {
        public PieceFacts {
            Objects.requireNonNull(pieceType, "pieceType");
            Objects.requireNonNull(boundingBox, "boundingBox");
            Objects.requireNonNull(orientation, "orientation");
            Objects.requireNonNull(mineshaftType, "mineshaftType");
        }
    }

    public interface WorldAccess {
        boolean supportsExactState(String exactState);
        boolean supportsMineshaftBlockingBiomeTag();
        boolean supportsLiquidStateQueries();
        boolean supportsMineshaftProtectedStateQueries();
        boolean isMineshaftBlockingBiome(BlockPos position);
        boolean isLiquid(BlockPos position);
        String blockState(BlockPos position);
        boolean setBlock(BlockPos position, String exactState, int flags);
    }

    public record ExecutionResult(boolean invalidLocation, int writes) { }

    public static ExecutionResult execute(PieceFacts piece, BoundingBox placementClip,
            WorldAccess world) {
        Objects.requireNonNull(placementClip, "placementClip");
        Objects.requireNonNull(world, "world");
        preflight(piece, world);
        if (isInInvalidLocation(piece.boundingBox(), placementClip, world)) {
            return new ExecutionResult(true, 0);
        }
        int writes = 0;
        writes += generateBox(piece, placementClip, world, 0, 5, 0, 2, 7, 1);
        writes += generateBox(piece, placementClip, world, 0, 0, 7, 2, 2, 8);
        for (int index = 0; index < 5; index++) {
            writes += generateBox(piece, placementClip, world, 0,
                    5 - index - (index < 4 ? 1 : 0), 2 + index,
                    2, 7 - index, 2 + index);
        }
        return new ExecutionResult(false, writes);
    }

    static void preflight(PieceFacts piece, WorldAccess world) {
        Objects.requireNonNull(piece, "piece");
        if (!PIECE_TYPE.equals(piece.pieceType())) {
            throw new IllegalArgumentException("expected exact minecraft:msstairs piece type");
        }
        if (piece.generationDepth() < 0 || piece.generationDepth() > 9) {
            throw new IllegalArgumentException("mineshaft-stairs generation depth outside 0..9");
        }
        BoundingBox box = piece.boundingBox();
        int xSpan = box.maxX() - box.minX() + 1;
        int ySpan = box.maxY() - box.minY() + 1;
        int zSpan = box.maxZ() - box.minZ() + 1;
        boolean zAxis = piece.orientation() == Orientation.NORTH
                || piece.orientation() == Orientation.SOUTH;
        if (ySpan != 8 || xSpan != (zAxis ? 3 : 9) || zSpan != (zAxis ? 9 : 3)) {
            throw new IllegalArgumentException("mineshaft-stairs dimensions disagree with orientation");
        }
        if (!world.supportsExactState(CAVE_AIR)) {
            throw new UnsupportedOperationException("mineshaft-stairs cave-air state");
        }
        if (!world.supportsMineshaftBlockingBiomeTag()) {
            throw new UnsupportedOperationException("mineshaft-blocking biome tag");
        }
        if (!world.supportsLiquidStateQueries()) {
            throw new UnsupportedOperationException("mineshaft liquid-state queries");
        }
        if (!world.supportsMineshaftProtectedStateQueries()) {
            throw new UnsupportedOperationException("mineshaft protected-state queries");
        }
    }

    private static boolean isInInvalidLocation(BoundingBox box, BoundingBox clip,
            WorldAccess world) {
        int x0 = Math.max(box.minX() - 1, clip.minX());
        int y0 = Math.max(box.minY() - 1, clip.minY());
        int z0 = Math.max(box.minZ() - 1, clip.minZ());
        int x1 = Math.min(box.maxX() + 1, clip.maxX());
        int y1 = Math.min(box.maxY() + 1, clip.maxY());
        int z1 = Math.min(box.maxZ() + 1, clip.maxZ());
        BlockPos center = new BlockPos((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2);
        if (world.isMineshaftBlockingBiome(center)) return true;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            if (world.isLiquid(new BlockPos(x, y0, z))
                    || world.isLiquid(new BlockPos(x, y1, z))) return true;
        }
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) {
            if (world.isLiquid(new BlockPos(x, y, z0))
                    || world.isLiquid(new BlockPos(x, y, z1))) return true;
        }
        for (int z = z0; z <= z1; z++) for (int y = y0; y <= y1; y++) {
            if (world.isLiquid(new BlockPos(x0, y, z))
                    || world.isLiquid(new BlockPos(x1, y, z))) return true;
        }
        return false;
    }

    private static int generateBox(PieceFacts piece, BoundingBox clip, WorldAccess world,
            int x0, int y0, int z0, int x1, int y1, int z1) {
        int writes = 0;
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                BlockPos position = worldPos(piece, x, y, z);
                if (!clip.contains(position)) continue;
                String state = Objects.requireNonNull(world.blockState(position), "block state");
                if (state.isEmpty()) throw new IllegalArgumentException("empty block state");
                if (isProtected(piece.mineshaftType(), state)) continue;
                world.setBlock(position, CAVE_AIR, 2);
                writes++;
            }
        }
        return writes;
    }

    static BlockPos worldPos(PieceFacts piece, int x, int y, int z) {
        BoundingBox box = piece.boundingBox();
        int worldX = switch (piece.orientation()) {
            case NORTH, SOUTH -> box.minX() + x;
            case WEST -> box.maxX() - z;
            case EAST -> box.minX() + z;
        };
        int worldZ = switch (piece.orientation()) {
            case NORTH -> box.maxZ() - z;
            case SOUTH -> box.minZ() + z;
            case WEST, EAST -> box.minZ() + x;
        };
        return new BlockPos(worldX, box.minY() + y, worldZ);
    }

    private static boolean isProtected(MineshaftType type, String state) {
        int property = state.indexOf('[');
        String key = property < 0 ? state : state.substring(0, property);
        return key.equals(type.planks) || key.equals(type.wood) || key.equals(type.fence)
                || key.equals("minecraft:chain");
    }
}
