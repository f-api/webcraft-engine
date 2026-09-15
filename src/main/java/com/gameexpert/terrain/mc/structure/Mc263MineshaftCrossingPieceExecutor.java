package com.gameexpert.terrain.mc.structure;

import java.util.Objects;

/** Exact dormant executor for the pinned 26.3 {@code minecraft:mscrossing} piece. */
public final class Mc263MineshaftCrossingPieceExecutor {
    public static final String PIECE_TYPE = "minecraft:mscrossing";
    public static final String CAVE_AIR = "minecraft:cave_air";

    private Mc263MineshaftCrossingPieceExecutor() { }

    public enum Direction {
        SOUTH(0), WEST(1), NORTH(2), EAST(3);

        private final int nbtId;

        Direction(int nbtId) { this.nbtId = nbtId; }

        public int nbtId() { return nbtId; }

        public static Direction fromNbtId(int id) {
            for (Direction direction : values()) if (direction.nbtId == id) return direction;
            throw new IllegalArgumentException("mineshaft-crossing direction outside 0..3");
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
            this.nbtId = nbtId;
            this.planks = planks;
            this.wood = wood;
            this.fence = fence;
        }

        public int nbtId() { return nbtId; }

        public String planks() { return planks; }

        public static MineshaftType fromNbtId(int id) {
            for (MineshaftType type : values()) if (type.nbtId == id) return type;
            throw new IllegalArgumentException("mineshaft-crossing type outside 0..1");
        }
    }

    public record BlockPos(int x, int y, int z) { }

    public record BoundingBox(int minX, int minY, int minZ,
                              int maxX, int maxY, int maxZ) {
        public BoundingBox {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted mineshaft-crossing bounding box");
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
                             int orientation, boolean twoFloored, Direction direction,
                             MineshaftType mineshaftType) {
        public PieceFacts {
            Objects.requireNonNull(pieceType, "pieceType");
            Objects.requireNonNull(boundingBox, "boundingBox");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(mineshaftType, "mineshaftType");
        }
    }

    public interface WorldAccess {
        boolean supportsExactState(String exactState);
        boolean supportsMineshaftBlockingBiomeTag();
        boolean supportsLiquidStateQueries();
        boolean supportsMineshaftProtectedStateQueries();
        boolean supportsAirStateQueries();
        boolean supportsOceanFloorWgHeight();
        boolean supportsSturdyUpQueries();
        boolean supportsPostWriteFluidStateQueries();
        boolean supportsScheduledFluidTicks();
        boolean isMineshaftBlockingBiome(BlockPos position);
        boolean isLiquid(BlockPos position);
        String blockState(BlockPos position);
        int oceanFloorWgHeight(int blockX, int blockZ);
        boolean isFaceSturdyUp(BlockPos position, String exactState);
        boolean setBlock(BlockPos position, String exactState, int flags);
        /** Empty means the exact post-write fluid state is empty. */
        String postWriteFluidType(BlockPos position);
        void scheduleFluidTick(BlockPos position, String fluidType, int delay);
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
        BoundingBox box = piece.boundingBox();
        int writes = 0;
        if (piece.twoFloored()) {
            writes += generateBox(piece, placementClip, world,
                    box.minX() + 1, box.minY(), box.minZ(),
                    box.maxX() - 1, box.minY() + 2, box.maxZ(), CAVE_AIR);
            writes += generateBox(piece, placementClip, world,
                    box.minX(), box.minY(), box.minZ() + 1,
                    box.maxX(), box.minY() + 2, box.maxZ() - 1, CAVE_AIR);
            writes += generateBox(piece, placementClip, world,
                    box.minX() + 1, box.maxY() - 2, box.minZ(),
                    box.maxX() - 1, box.maxY(), box.maxZ(), CAVE_AIR);
            writes += generateBox(piece, placementClip, world,
                    box.minX(), box.maxY() - 2, box.minZ() + 1,
                    box.maxX(), box.maxY(), box.maxZ() - 1, CAVE_AIR);
            writes += generateBox(piece, placementClip, world,
                    box.minX() + 1, box.minY() + 3, box.minZ() + 1,
                    box.maxX() - 1, box.minY() + 3, box.maxZ() - 1, CAVE_AIR);
        } else {
            writes += generateBox(piece, placementClip, world,
                    box.minX() + 1, box.minY(), box.minZ(),
                    box.maxX() - 1, box.maxY(), box.maxZ(), CAVE_AIR);
            writes += generateBox(piece, placementClip, world,
                    box.minX(), box.minY(), box.minZ() + 1,
                    box.maxX(), box.maxY(), box.maxZ() - 1, CAVE_AIR);
        }
        writes += placeSupportPillar(piece, placementClip, world,
                box.minX() + 1, box.minZ() + 1);
        writes += placeSupportPillar(piece, placementClip, world,
                box.minX() + 1, box.maxZ() - 1);
        writes += placeSupportPillar(piece, placementClip, world,
                box.maxX() - 1, box.minZ() + 1);
        writes += placeSupportPillar(piece, placementClip, world,
                box.maxX() - 1, box.maxZ() - 1);
        int floorY = box.minY() - 1;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                writes += setPlanksBlock(piece, placementClip, world, x, floorY, z);
            }
        }
        return new ExecutionResult(false, writes);
    }

    static void preflight(PieceFacts piece, WorldAccess world) {
        Objects.requireNonNull(piece, "piece");
        if (!PIECE_TYPE.equals(piece.pieceType())) {
            throw new IllegalArgumentException("expected exact minecraft:mscrossing piece type");
        }
        if (piece.generationDepth() < 1 || piece.generationDepth() > 9
                || piece.orientation() != -1) {
            throw new IllegalArgumentException("mineshaft crossing depth/orientation is not exact");
        }
        BoundingBox box = piece.boundingBox();
        int xSpan = box.maxX() - box.minX() + 1;
        int ySpan = box.maxY() - box.minY() + 1;
        int zSpan = box.maxZ() - box.minZ() + 1;
        if (xSpan != 5 || zSpan != 5 || ySpan != (piece.twoFloored() ? 7 : 3)) {
            throw new IllegalArgumentException("mineshaft-crossing dimensions disagree with tf");
        }
        require(world.supportsExactState(CAVE_AIR), "mineshaft-crossing cave-air state");
        require(world.supportsExactState(piece.mineshaftType().planks()),
                "mineshaft-crossing planks state");
        require(world.supportsMineshaftBlockingBiomeTag(), "mineshaft-blocking biome tag");
        require(world.supportsLiquidStateQueries(), "mineshaft liquid-state queries");
        require(world.supportsMineshaftProtectedStateQueries(),
                "mineshaft protected-state queries");
        require(world.supportsAirStateQueries(), "mineshaft air-state queries");
        require(world.supportsOceanFloorWgHeight(), "OCEAN_FLOOR_WG height");
        require(world.supportsSturdyUpQueries(), "sturdy-UP queries");
        require(world.supportsPostWriteFluidStateQueries(), "post-write fluid-state queries");
        require(world.supportsScheduledFluidTicks(), "scheduled fluid ticks");
    }

    private static void require(boolean supported, String capability) {
        if (!supported) throw new UnsupportedOperationException(capability);
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
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String state) {
        int writes = 0;
        for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                writes += placeBlock(piece, clip, world, new BlockPos(x, y, z), state);
            }
        }
        return writes;
    }

    private static int placeSupportPillar(PieceFacts piece, BoundingBox clip,
            WorldAccess world, int x, int z) {
        BoundingBox box = piece.boundingBox();
        String top = getBlock(clip, world, new BlockPos(x, box.maxY() + 1, z));
        if (isAir(top)) return 0;
        return generateBox(piece, clip, world, x, box.minY(), z, x, box.maxY(), z,
                piece.mineshaftType().planks());
    }

    private static String getBlock(BoundingBox clip, WorldAccess world, BlockPos position) {
        if (!clip.contains(position)) return "minecraft:air";
        return requireState(world.blockState(position));
    }

    private static int setPlanksBlock(PieceFacts piece, BoundingBox clip, WorldAccess world,
            int x, int y, int z) {
        BlockPos interior = new BlockPos(x, y + 1, z);
        if (!clip.contains(interior)
                || interior.y() >= world.oceanFloorWgHeight(interior.x(), interior.z())) return 0;
        BlockPos floor = new BlockPos(x, y, z);
        String existing = requireState(world.blockState(floor));
        if (world.isFaceSturdyUp(floor, existing)) return 0;
        world.setBlock(floor, piece.mineshaftType().planks(), 2);
        return 1;
    }

    private static int placeBlock(PieceFacts piece, BoundingBox clip, WorldAccess world,
            BlockPos position, String state) {
        if (!clip.contains(position)) return 0;
        String existing = requireState(world.blockState(position));
        if (isProtected(piece.mineshaftType(), existing)) return 0;
        world.setBlock(position, state, 2);
        String fluid = Objects.requireNonNull(world.postWriteFluidType(position),
                "post-write fluid type");
        if (!fluid.isEmpty()) world.scheduleFluidTick(position, fluid, 0);
        return 1;
    }

    private static String requireState(String state) {
        Objects.requireNonNull(state, "block state");
        if (state.isEmpty()) throw new IllegalArgumentException("empty block state");
        return state;
    }

    private static boolean isAir(String state) {
        int property = state.indexOf('[');
        String key = property < 0 ? state : state.substring(0, property);
        return key.equals("minecraft:air") || key.equals("minecraft:cave_air")
                || key.equals("minecraft:void_air");
    }

    private static boolean isProtected(MineshaftType type, String state) {
        int property = state.indexOf('[');
        String key = property < 0 ? state : state.substring(0, property);
        return key.equals(type.planks) || key.equals(type.wood) || key.equals(type.fence)
                || key.equals("minecraft:chain");
    }
}
