package com.gameexpert.terrain.mc.structure;

import java.util.List;
import java.util.Objects;

/** Exact dormant executor for the pinned 26.3 {@code minecraft:msroom} piece. */
public final class Mc263MineshaftRoomPieceExecutor {
    public static final String PIECE_TYPE = "minecraft:msroom";
    public static final String CAVE_AIR = "minecraft:cave_air";

    private Mc263MineshaftRoomPieceExecutor() { }

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

        public static MineshaftType fromNbtId(int id) {
            for (MineshaftType type : values()) if (type.nbtId == id) return type;
            throw new IllegalArgumentException("mineshaft-room type outside 0..1");
        }
    }

    public record BlockPos(int x, int y, int z) { }

    public record BoundingBox(int minX, int minY, int minZ,
                              int maxX, int maxY, int maxZ) {
        public BoundingBox {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted mineshaft-room bounding box");
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
                             int orientation, MineshaftType mineshaftType,
                             List<BoundingBox> orderedEntrances) {
        public PieceFacts {
            Objects.requireNonNull(pieceType, "pieceType");
            Objects.requireNonNull(boundingBox, "boundingBox");
            Objects.requireNonNull(mineshaftType, "mineshaftType");
            orderedEntrances = List.copyOf(orderedEntrances);
            for (BoundingBox entrance : orderedEntrances) {
                Objects.requireNonNull(entrance, "entrance");
            }
        }
    }

    public interface WorldAccess {
        boolean supportsExactState(String exactState);
        boolean supportsMineshaftBlockingBiomeTag();
        boolean supportsLiquidStateQueries();
        boolean supportsMineshaftProtectedStateQueries();
        boolean supportsPostWriteFluidStateQueries();
        boolean supportsScheduledFluidTicks();
        boolean isMineshaftBlockingBiome(BlockPos position);
        boolean isLiquid(BlockPos position);
        String blockState(BlockPos position);
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
        BoundingBox room = piece.boundingBox();
        int writes = generateBox(piece, placementClip, world,
                room.minX(), room.minY() + 1, room.minZ(),
                room.maxX(), Math.min(room.minY() + 3, room.maxY()), room.maxZ());
        for (BoundingBox entrance : piece.orderedEntrances()) {
            writes += generateBox(piece, placementClip, world,
                    entrance.minX(), entrance.maxY() - 2, entrance.minZ(),
                    entrance.maxX(), entrance.maxY(), entrance.maxZ());
        }
        writes += generateUpperHalfSphere(piece, placementClip, world,
                room.minX(), room.minY() + 4, room.minZ(),
                room.maxX(), room.maxY(), room.maxZ());
        return new ExecutionResult(false, writes);
    }

    static void preflight(PieceFacts piece, WorldAccess world) {
        Objects.requireNonNull(piece, "piece");
        if (!PIECE_TYPE.equals(piece.pieceType())) {
            throw new IllegalArgumentException("expected exact minecraft:msroom piece type");
        }
        if (piece.generationDepth() != 0 || piece.orientation() != -1) {
            throw new IllegalArgumentException("mineshaft room is not the exact unoriented root");
        }
        BoundingBox box = piece.boundingBox();
        int xSpan = box.maxX() - box.minX() + 1;
        int ySpan = box.maxY() - box.minY() + 1;
        int zSpan = box.maxZ() - box.minZ() + 1;
        if (xSpan < 8 || xSpan > 13 || ySpan < 5 || ySpan > 10
                || zSpan < 8 || zSpan > 13) {
            throw new IllegalArgumentException("mineshaft-room dimensions outside constructor range");
        }
        require(world.supportsExactState(CAVE_AIR), "mineshaft-room cave-air state");
        require(world.supportsMineshaftBlockingBiomeTag(), "mineshaft-blocking biome tag");
        require(world.supportsLiquidStateQueries(), "mineshaft liquid-state queries");
        require(world.supportsMineshaftProtectedStateQueries(),
                "mineshaft protected-state queries");
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
            int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int writes = 0;
        for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                writes += placeCaveAir(piece, clip, world, new BlockPos(x, y, z));
            }
        }
        return writes;
    }

    private static int generateUpperHalfSphere(PieceFacts piece, BoundingBox clip,
            WorldAccess world, int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ) {
        float xSpan = maxX - minX + 1;
        float ySpan = maxY - minY + 1;
        float zSpan = maxZ - minZ + 1;
        float centerX = minX + xSpan / 2.0F;
        float centerZ = minZ + zSpan / 2.0F;
        int writes = 0;
        for (int y = minY; y <= maxY; y++) {
            float dy = (float) (y - minY) / ySpan;
            for (int x = minX; x <= maxX; x++) {
                float dx = ((float) x - centerX) / (xSpan * 0.5F);
                for (int z = minZ; z <= maxZ; z++) {
                    float dz = ((float) z - centerZ) / (zSpan * 0.5F);
                    if (dx * dx + dy * dy + dz * dz <= 1.05F) {
                        writes += placeCaveAir(piece, clip, world, new BlockPos(x, y, z));
                    }
                }
            }
        }
        return writes;
    }

    private static int placeCaveAir(PieceFacts piece, BoundingBox clip, WorldAccess world,
            BlockPos position) {
        if (!clip.contains(position)) return 0;
        String state = Objects.requireNonNull(world.blockState(position), "block state");
        if (state.isEmpty()) throw new IllegalArgumentException("empty block state");
        if (isProtected(piece.mineshaftType(), state)) return 0;
        world.setBlock(position, CAVE_AIR, 2);
        String fluid = Objects.requireNonNull(world.postWriteFluidType(position),
                "post-write fluid type");
        if (!fluid.isEmpty()) world.scheduleFluidTick(position, fluid, 0);
        return 1;
    }

    private static boolean isProtected(MineshaftType type, String state) {
        int property = state.indexOf('[');
        String key = property < 0 ? state : state.substring(0, property);
        return key.equals(type.planks) || key.equals(type.wood) || key.equals(type.fence)
                || key.equals("minecraft:chain");
    }
}
