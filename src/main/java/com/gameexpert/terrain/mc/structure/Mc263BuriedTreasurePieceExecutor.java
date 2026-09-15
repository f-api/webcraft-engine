package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import java.util.Objects;

/**
 * Exact dormant executor for the pinned 26.3 {@code minecraft:btp} structure piece.
 *
 * <p>The persisted 74-byte NBT receipt is evidence for the typed facts supplied here; this leaf
 * does not parse or infer opaque carrier payloads. Nothing calls it from the canonical pipeline.
 * All fixed outputs and sidecar capabilities are preflighted before the heightmap, block world,
 * or supplied structure RNG is observed.</p>
 */
public final class Mc263BuriedTreasurePieceExecutor {
    public static final String PIECE_TYPE = "minecraft:btp";
    public static final String LOOT_TABLE = "minecraft:chests/buried_treasure";

    private static final String SAND = "minecraft:sand";
    private static final String CHEST = "minecraft:chest";

    private Mc263BuriedTreasurePieceExecutor() {
    }

    public record BoundingBox(int minX, int minY, int minZ,
                              int maxX, int maxY, int maxZ) {
        public BoundingBox {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted buried-treasure bounding box");
            }
        }

        public static BoundingBox point(int x, int y, int z) {
            return new BoundingBox(x, y, z, x, y, z);
        }

        boolean isPoint() {
            return minX == maxX && minY == maxY && minZ == maxZ;
        }

        boolean contains(BlockPos position) {
            return position.x >= minX && position.x <= maxX
                    && position.y >= minY && position.y <= maxY
                    && position.z >= minZ && position.z <= maxZ;
        }
    }

    /** Typed facts already validated against the persisted piece NBT by the structure carrier. */
    public record PieceFacts(String pieceType, BoundingBox boundingBox) {
        public PieceFacts {
            Objects.requireNonNull(pieceType, "pieceType");
            Objects.requireNonNull(boundingBox, "boundingBox");
        }
    }

    public record BlockPos(int x, int y, int z) {
        BlockPos relative(Direction direction) {
            return new BlockPos(x + direction.dx, y + direction.dy, z + direction.dz);
        }

        BlockPos below() {
            return new BlockPos(x, y - 1, z);
        }
    }

    /**
     * The boolean results of both write methods are intentionally ignored, matching the official
     * piece. Reads after a write must expose the live world result rather than the attempted state.
     */
    public interface WorldAccess {
        /** Pure capability declaration; it must not read mutable world state. */
        boolean supportsExactState(String exactState);

        /** Pure capability declaration for writing a state copied from a live read. */
        boolean supportsCopiedStateWrites();

        /** Pure capability declaration for the randomizable-container loot sidecar. */
        boolean supportsChestLoot();

        int minY();

        int oceanFloorWg(int blockX, int blockZ);

        String blockState(int blockX, int blockY, int blockZ);

        boolean isSolidRender(int blockX, int blockY, int blockZ);

        boolean setBlockAndUpdate(int blockX, int blockY, int blockZ, String exactState);

        boolean setBlock(int blockX, int blockY, int blockZ, String exactState, int flags);

        boolean hasChestBlockEntity(int blockX, int blockY, int blockZ);

        void setChestLoot(int blockX, int blockY, int blockZ, String lootTable, long lootSeed);
    }

    @FunctionalInterface
    public interface TraceSink {
        void record(String phase, long... values);

        static TraceSink disabled() {
            return (phase, values) -> { };
        }
    }

    public record ExecutionResult(boolean supportFound, boolean chestAttempted,
                                  boolean chestCreated, BoundingBox finalBoundingBox) {
    }

    /** Executes with the exact supplied structure RNG; this method never creates or reseeds one. */
    public static ExecutionResult execute(PieceFacts piece, BoundingBox placementClip,
            WorldAccess world, WorldgenRandom random) {
        return execute(piece, placementClip, world, random, TraceSink.disabled());
    }

    /** Executes with a stable numeric trace suitable for Java/Rust byte-identity probes. */
    public static ExecutionResult execute(PieceFacts piece, BoundingBox placementClip,
            WorldAccess world, WorldgenRandom random, TraceSink trace) {
        preflight(piece, placementClip, world, random, trace);

        BoundingBox originalBox = piece.boundingBox();
        int x = originalBox.minX();
        int z = originalBox.minZ();
        int y = world.oceanFloorWg(x, z);
        trace.record("height", x, z, y);
        while (true) {
            int minY = world.minY();
            if (y <= minY) {
                trace.record("min_y", y, minY);
                return new ExecutionResult(false, false, false, originalBox);
            }
            String current = requireState(world.blockState(x, y, z));
            String below = requireState(world.blockState(x, y - 1, z));
            boolean supported = isSupport(below);
            trace.record("scan", y, stateClass(current), supportClass(below), supported ? 1L : 0L);
            if (supported) {
                String fill = isAirOrLiquid(current) ? SAND : current;
                BlockPos center = new BlockPos(x, y, z);
                for (Direction direction : Direction.OFFICIAL_ORDER) {
                    BlockPos neighbor = center.relative(direction);
                    String neighborState = requireState(world.blockState(
                            neighbor.x(), neighbor.y(), neighbor.z()));
                    if (!isAirOrLiquid(neighborState)) {
                        trace.record("neighbor", direction.ordinal(), 0L);
                        continue;
                    }
                    BlockPos neighborBelow = neighbor.below();
                    String neighborBelowState = requireState(world.blockState(
                            neighborBelow.x(), neighborBelow.y(), neighborBelow.z()));
                    boolean unsupportedBelow = isAirOrLiquid(neighborBelowState);
                    String replacement;
                    long action;
                    if (unsupportedBelow && direction != Direction.UP) {
                        replacement = below;
                        action = 1L;
                    } else {
                        replacement = fill;
                        action = 2L;
                    }
                    boolean accepted = world.setBlockAndUpdate(
                            neighbor.x(), neighbor.y(), neighbor.z(), replacement);
                    trace.record("neighbor", direction.ordinal(), action, accepted ? 1L : 0L);
                }

                BoundingBox moved = BoundingBox.point(x, y, z);
                trace.record("box", x, y, z);
                boolean chestCreated = createChest(
                        placementClip, world, random, center, trace);
                return new ExecutionResult(true, placementClip.contains(center),
                        chestCreated, moved);
            }
            y--;
        }
    }

    private static boolean createChest(BoundingBox clip, WorldAccess world,
            WorldgenRandom random, BlockPos position, TraceSink trace) {
        if (!clip.contains(position)) {
            trace.record("chest_gate", 0L);
            return false;
        }
        String current = requireState(world.blockState(position.x(), position.y(), position.z()));
        if (isBlock(current, CHEST)) {
            trace.record("chest_gate", 1L);
            return false;
        }

        Direction facing = reorient(world, position, trace);
        String chest = chestState(facing);
        boolean accepted = world.setBlock(position.x(), position.y(), position.z(), chest, 2);
        trace.record("chest_write", facing.horizontalId, accepted ? 1L : 0L);
        boolean chestEntity = world.hasChestBlockEntity(
                position.x(), position.y(), position.z());
        trace.record("chest_entity", chestEntity ? 1L : 0L);
        if (chestEntity) {
            long lootSeed = random.nextLong();
            world.setChestLoot(position.x(), position.y(), position.z(), LOOT_TABLE, lootSeed);
            trace.record("loot", lootSeed);
        }
        return true;
    }

    private static Direction reorient(WorldAccess world, BlockPos position, TraceSink trace) {
        Direction onlySolid = null;
        for (Direction direction : Direction.HORIZONTAL_ORDER) {
            int x = position.x() + direction.dx;
            int z = position.z() + direction.dz;
            String state = requireState(world.blockState(x, position.y(), z));
            if (isBlock(state, CHEST)) {
                trace.record("reorient", direction.horizontalId, 2L);
                return Direction.NORTH;
            }
            boolean solid = world.isSolidRender(x, position.y(), z);
            trace.record("reorient", direction.horizontalId, solid ? 1L : 0L);
            if (!solid) continue;
            if (onlySolid == null) {
                onlySolid = direction;
            } else {
                onlySolid = null;
                break;
            }
        }
        if (onlySolid != null) return onlySolid.opposite();

        Direction facing = Direction.NORTH;
        if (solidRender(world, position, facing)) facing = facing.opposite();
        if (solidRender(world, position, facing)) facing = facing.clockwise();
        if (solidRender(world, position, facing)) facing = facing.opposite();
        return facing;
    }

    private static boolean solidRender(WorldAccess world, BlockPos position,
            Direction direction) {
        return world.isSolidRender(position.x() + direction.dx, position.y(),
                position.z() + direction.dz);
    }

    private static void preflight(PieceFacts piece, BoundingBox clip, WorldAccess world,
            WorldgenRandom random, TraceSink trace) {
        if (piece == null || clip == null || world == null || random == null || trace == null) {
            throw new IllegalArgumentException("piece, clip, world, random, and trace are required");
        }
        if (!PIECE_TYPE.equals(piece.pieceType())) {
            throw new IllegalArgumentException("expected exact minecraft:btp piece type");
        }
        if (!piece.boundingBox().isPoint()) {
            throw new IllegalArgumentException("buried-treasure piece bounding box must be a point");
        }
        if (!world.supportsCopiedStateWrites()) {
            throw new UnsupportedOperationException("buried-treasure copied-state writes");
        }
        if (!world.supportsExactState(SAND)) {
            throw new UnsupportedOperationException("buried-treasure state: " + SAND);
        }
        for (Direction facing : Direction.HORIZONTAL_ORDER) {
            String state = chestState(facing);
            if (!world.supportsExactState(state)) {
                throw new UnsupportedOperationException("buried-treasure state: " + state);
            }
        }
        if (!world.supportsChestLoot()) {
            throw new UnsupportedOperationException("buried-treasure chest loot sidecar");
        }
    }

    private static String chestState(Direction facing) {
        return CHEST + "[facing=" + facing.key + ",type=single,waterlogged=false]";
    }

    private static boolean isSupport(String state) {
        return supportClass(state) >= 0;
    }

    private static int supportClass(String state) {
        return switch (blockKey(state)) {
            case "minecraft:sandstone" -> 0;
            case "minecraft:stone" -> 1;
            case "minecraft:andesite" -> 2;
            case "minecraft:granite" -> 3;
            case "minecraft:diorite" -> 4;
            default -> -1;
        };
    }

    private static int stateClass(String state) {
        if (isAir(state)) return 0;
        if (isLiquid(state)) return 1;
        return 2;
    }

    private static boolean isAirOrLiquid(String state) {
        return isAir(state) || isLiquid(state);
    }

    private static boolean isAir(String state) {
        return switch (blockKey(state)) {
            case "minecraft:air", "minecraft:cave_air", "minecraft:void_air" -> true;
            default -> false;
        };
    }

    private static boolean isLiquid(String state) {
        return switch (blockKey(state)) {
            case "minecraft:water", "minecraft:lava" -> true;
            default -> false;
        };
    }

    private static boolean isBlock(String state, String key) {
        return blockKey(state).equals(key);
    }

    private static String requireState(String state) {
        if (state == null || state.isBlank()) {
            throw new IllegalStateException("live exact block state is required");
        }
        return state;
    }

    private static String blockKey(String state) {
        int properties = state.indexOf('[');
        return properties < 0 ? state : state.substring(0, properties);
    }

    private enum Direction {
        DOWN(0, -1, 0, null, -1),
        UP(0, 1, 0, null, -1),
        NORTH(0, 0, -1, "north", 0),
        SOUTH(0, 0, 1, "south", 2),
        WEST(-1, 0, 0, "west", 3),
        EAST(1, 0, 0, "east", 1);

        static final Direction[] OFFICIAL_ORDER = values();
        static final Direction[] HORIZONTAL_ORDER = {NORTH, EAST, SOUTH, WEST};

        final int dx;
        final int dy;
        final int dz;
        final String key;
        final int horizontalId;

        Direction(int dx, int dy, int dz, String key, int horizontalId) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.key = key;
            this.horizontalId = horizontalId;
        }

        Direction opposite() {
            return switch (this) {
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case WEST -> EAST;
                case EAST -> WEST;
                case DOWN -> UP;
                case UP -> DOWN;
            };
        }

        Direction clockwise() {
            return switch (this) {
                case NORTH -> EAST;
                case EAST -> SOUTH;
                case SOUTH -> WEST;
                case WEST -> NORTH;
                default -> throw new IllegalStateException("vertical clockwise direction");
            };
        }
    }
}
