package com.gameexpert.terrain.mc.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Exact dormant evidence executor for pinned 26.3 {@code minecraft:mscorridor}. */
public final class Mc263MineshaftCorridorPieceExecutor {
    public static final String PIECE_TYPE = "minecraft:mscorridor";
    public static final String LOOT_TABLE = "minecraft:chests/abandoned_mineshaft";
    public static final String CAVE_SPIDER = "minecraft:cave_spider";
    public static final String CHEST_MINECART = "minecraft:chest_minecart";
    public static final String CHUNK_GENERATION = "CHUNK_GENERATION";
    private static final String AIR = "minecraft:air";
    private static final String CAVE_AIR = "minecraft:cave_air";
    private static final String COBWEB = "minecraft:cobweb";
    private static final String SPAWNER = "minecraft:spawner";
    private static final String CHAIN = "minecraft:chain";
    private static final long SILVER_RATIO = 0x6A09E667F3BCC909L;
    private static final long GOLDEN_RATIO = 0x9E3779B97F4A7C15L;

    private Mc263MineshaftCorridorPieceExecutor() { }

    public enum Direction {
        SOUTH(0), WEST(1), NORTH(2), EAST(3);
        private final int nbtId;
        Direction(int nbtId) { this.nbtId = nbtId; }
        public int nbtId() { return nbtId; }
        public static Direction fromNbtId(int id) {
            for (Direction value : values()) if (value.nbtId == id) return value;
            throw new IllegalArgumentException("mineshaft-corridor orientation outside 0..3");
        }
    }

    public enum Face {
        DOWN, UP, NORTH, SOUTH, WEST, EAST;
        Face opposite() {
            return switch (this) {
                case DOWN -> UP; case UP -> DOWN; case NORTH -> SOUTH;
                case SOUTH -> NORTH; case WEST -> EAST; case EAST -> WEST;
            };
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
        public String planks() { return planks; }
        public String wood() { return wood; }
        public String fence() { return fence; }
        public static MineshaftType fromNbtId(int id) {
            for (MineshaftType value : values()) if (value.nbtId == id) return value;
            throw new IllegalArgumentException("mineshaft-corridor type outside 0..1");
        }
    }

    public record BlockPos(int x, int y, int z) {
        BlockPos move(Face face) {
            return switch (face) {
                case DOWN -> new BlockPos(x, y - 1, z); case UP -> new BlockPos(x, y + 1, z);
                case NORTH -> new BlockPos(x, y, z - 1); case SOUTH -> new BlockPos(x, y, z + 1);
                case WEST -> new BlockPos(x - 1, y, z); case EAST -> new BlockPos(x + 1, y, z);
            };
        }
    }

    public record BoundingBox(int minX, int minY, int minZ,
                              int maxX, int maxY, int maxZ) {
        public BoundingBox {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted mineshaft-corridor bounding box");
            }
        }
        boolean contains(BlockPos p) {
            return p.x >= minX && p.x <= maxX && p.y >= minY && p.y <= maxY
                    && p.z >= minZ && p.z <= maxZ;
        }
        boolean intersects(BoundingBox b) {
            return maxX >= b.minX && minX <= b.maxX && maxY >= b.minY && minY <= b.maxY
                    && maxZ >= b.minZ && minZ <= b.maxZ;
        }
    }

    public record PieceFacts(String pieceType, BoundingBox boundingBox, int generationDepth,
                             Direction orientation, MineshaftType mineshaftType, int numSections,
                             boolean hasRails, boolean spiderCorridor,
                             boolean hasPlacedSpider) {
        public PieceFacts {
            Objects.requireNonNull(pieceType, "pieceType");
            Objects.requireNonNull(boundingBox, "boundingBox");
            Objects.requireNonNull(orientation, "orientation");
            Objects.requireNonNull(mineshaftType, "mineshaftType");
        }
    }

    /** Capability methods are pure and are all checked before any RNG or mutable-world access. */
    public interface WorldAccess {
        boolean supportsExactState(String exactState);
        boolean supportsMineshaftBlockingBiomeTag();
        boolean supportsLiquidStateQueries();
        boolean supportsMineshaftProtectedStateQueries();
        boolean supportsAirStateQueries();
        boolean supportsOceanFloorWgHeight();
        boolean supportsSturdyFaceQueries();
        boolean supportsSolidRenderQueries();
        boolean supportsReplaceableByStructuresQueries();
        boolean supportsCenterSupportQueries();
        boolean supportsFallingBlockQueries();
        boolean supportsPostWriteFluidStateQueries();
        boolean supportsScheduledFluidTicks();
        boolean supportsPostProcessingMarks();
        boolean supportsSpawnerBlockEntityQueries();
        boolean supportsChestMinecartCreation();
        int minY();
        int maxY();
        boolean isMineshaftBlockingBiome(BlockPos position);
        boolean isLiquid(BlockPos position);
        String blockState(BlockPos position);
        int oceanFloorWgHeight(int blockX, int blockZ);
        boolean isFaceSturdy(BlockPos position, String exactState, Face face);
        boolean isSolidRender(BlockPos position, String exactState);
        boolean isReplaceableByStructures(BlockPos position, String exactState);
        boolean canSupportCenter(BlockPos position, String exactState, Face face);
        boolean isFallingBlock(String exactState);
        boolean setBlock(BlockPos position, String exactState, int flags);
        String postWriteFluidType(BlockPos position);
        void scheduleFluidTick(BlockPos position, String fluidType, int delay);
        void markForPostProcessing(BlockPos position);
        boolean hasSpawnerBlockEntity(BlockPos position);
    }

    /** Exact WorldgenRandom/Xoroshiro stream with an immutable non-consuming continuation. */
    public static final class StructureRandom {
        private long lo;
        private long hi;
        private long rawDraws;
        public StructureRandom(long seed) {
            long first = seed ^ SILVER_RATIO;
            setState(mix(first), mix(first + GOLDEN_RATIO), 0);
        }
        public StructureRandom(RngContinuation continuation) {
            Objects.requireNonNull(continuation, "continuation");
            setState(continuation.seedLo(), continuation.seedHi(), continuation.rawDraws());
        }
        private void setState(long newLo, long newHi, long draws) {
            if ((newLo | newHi) == 0) { lo = GOLDEN_RATIO; hi = SILVER_RATIO; }
            else { lo = newLo; hi = newHi; }
            rawDraws = draws;
        }
        private static long mix(long value) {
            value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
            value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
            return value ^ value >>> 31;
        }
        private long raw() {
            long s0 = lo, s1 = hi;
            long result = Long.rotateLeft(s0 + s1, 17) + s0;
            s1 ^= s0;
            lo = Long.rotateLeft(s0, 49) ^ s1 ^ s1 << 21;
            hi = Long.rotateLeft(s1, 28);
            rawDraws++;
            return result;
        }
        private int next(int bits) { return (int) (raw() >>> (64 - bits)); }
        int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
            if ((bound & -bound) == bound) return (int) (bound * (long) next(31) >> 31);
            int bits, value;
            do { bits = next(31); value = bits % bound; }
            while (bits - value + bound - 1 < 0);
            return value;
        }
        boolean nextBoolean() { return next(1) != 0; }
        float nextFloat() { return next(24) * 0x1.0p-24f; }
        long nextLong() { return ((long) next(32) << 32) + next(32); }
        public RngContinuation continuation() { return new RngContinuation(lo, hi, rawDraws); }
    }

    public record RngContinuation(long seedLo, long seedHi, long rawDraws) { }
    public record EntityPosition(double x, double y, double z) { }
    public record SpawnerEffect(BlockPos anchor, String entityType) { }
    public record ChestMinecartEffect(BlockPos anchor, EntityPosition position, String entityType,
                                      String lootTable, long lootSeed, String spawnReason) { }
    public record ExecutionResult(boolean invalidLocation, int writes,
                                  boolean finalHasPlacedSpider,
                                  List<SpawnerEffect> spawnerEffects,
                                  List<ChestMinecartEffect> chestMinecartEffects,
                                  RngContinuation rngContinuation) {
        public ExecutionResult {
            spawnerEffects = List.copyOf(spawnerEffects);
            chestMinecartEffects = List.copyOf(chestMinecartEffects);
        }
    }

    public static ExecutionResult execute(PieceFacts piece, BoundingBox clip, WorldAccess world,
            StructureRandom random) {
        Objects.requireNonNull(clip, "clip"); Objects.requireNonNull(world, "world");
        Objects.requireNonNull(random, "random");
        preflight(piece, world);
        var spawners = new ArrayList<SpawnerEffect>();
        var chests = new ArrayList<ChestMinecartEffect>();
        Counter writes = new Counter();
        if (invalid(piece.boundingBox(), clip, world)) {
            return result(true, writes, piece.hasPlacedSpider(), spawners, chests, random);
        }
        int length = piece.numSections() * 5 - 1;
        generateBox(piece, clip, world, 0, 0, 0, 2, 1, length, CAVE_AIR, writes);
        generateMaybeBox(piece, clip, world, random, .8f, 0, 2, 0, 2, 2, length,
                CAVE_AIR, false, writes);
        if (piece.spiderCorridor()) {
            generateMaybeBox(piece, clip, world, random, .6f, 0, 0, 0, 2, 1, length,
                    COBWEB, true, writes);
        }
        boolean hps = piece.hasPlacedSpider();
        for (int section = 0; section < piece.numSections(); section++) {
            int z = 2 + section * 5;
            placeSupport(piece, clip, world, random, z, writes);
            maybeCobweb(piece, clip, world, random, .1f, 0, 2, z - 1, writes);
            maybeCobweb(piece, clip, world, random, .1f, 2, 2, z - 1, writes);
            maybeCobweb(piece, clip, world, random, .1f, 0, 2, z + 1, writes);
            maybeCobweb(piece, clip, world, random, .1f, 2, 2, z + 1, writes);
            maybeCobweb(piece, clip, world, random, .05f, 0, 2, z - 2, writes);
            maybeCobweb(piece, clip, world, random, .05f, 2, 2, z - 2, writes);
            maybeCobweb(piece, clip, world, random, .05f, 0, 2, z + 2, writes);
            maybeCobweb(piece, clip, world, random, .05f, 2, 2, z + 2, writes);
            if (random.nextInt(100) == 0) createChest(piece, clip, world, random,
                    2, 0, z - 1, writes, chests);
            if (random.nextInt(100) == 0) createChest(piece, clip, world, random,
                    0, 0, z + 1, writes, chests);
            if (piece.spiderCorridor() && !hps) {
                int newZ = z - 1 + random.nextInt(3);
                BlockPos pos = worldPos(piece, 1, 0, newZ);
                if (clip.contains(pos) && isInterior(piece, clip, world, 1, 0, newZ)) {
                    hps = true;
                    world.setBlock(pos, SPAWNER, 2);
                    writes.value++;
                    if (world.hasSpawnerBlockEntity(pos)) {
                        spawners.add(new SpawnerEffect(pos, CAVE_SPIDER));
                    }
                }
            }
        }
        for (int x = 0; x <= 2; x++) for (int z = 0; z <= length; z++) {
            setPlanks(piece, clip, world, x, -1, z, writes);
        }
        doubleSupport(piece, clip, world, 0, -1, 2, writes);
        if (piece.numSections() > 1) doubleSupport(piece, clip, world, 0, -1,
                length - 2, writes);
        if (piece.hasRails()) for (int z = 0; z <= length; z++) {
            BlockPos floorPos = worldPos(piece, 1, -1, z);
            String floor = getBlock(piece, clip, world, 1, -1, z);
            if (isAir(floor) || !world.isSolidRender(floorPos, floor)) continue;
            float probability = isInterior(piece, clip, world, 1, 0, z) ? .7f : .9f;
            if (random.nextFloat() < probability) {
                place(piece, clip, world, 1, 0, z, railState(piece, true), writes);
            }
        }
        return result(false, writes, hps, spawners, chests, random);
    }

    static void preflight(PieceFacts p, WorldAccess w) {
        Objects.requireNonNull(p, "piece");
        if (!PIECE_TYPE.equals(p.pieceType()) || p.generationDepth() < 1
                || p.generationDepth() > 9 || p.numSections() < 1 || p.numSections() > 4) {
            throw new IllegalArgumentException("noncanonical mineshaft corridor identity");
        }
        BoundingBox b = p.boundingBox(); boolean z = p.orientation() == Direction.NORTH
                || p.orientation() == Direction.SOUTH;
        int expected = p.numSections() * 5;
        if (b.maxY() - b.minY() + 1 != 3 || b.maxX() - b.minX() + 1 != (z ? 3 : expected)
                || b.maxZ() - b.minZ() + 1 != (z ? expected : 3)
                || p.hasRails() && p.spiderCorridor() || p.hasPlacedSpider() && !p.spiderCorridor()) {
            throw new IllegalArgumentException("noncanonical mineshaft corridor dimensions/flags");
        }
        for (String state : outputStates(p)) require(w.supportsExactState(state), state);
        require(w.supportsMineshaftBlockingBiomeTag(), "blocking tag");
        require(w.supportsLiquidStateQueries(), "liquid queries");
        require(w.supportsMineshaftProtectedStateQueries(), "protected queries");
        require(w.supportsAirStateQueries(), "air queries");
        require(w.supportsOceanFloorWgHeight(), "height queries");
        require(w.supportsSturdyFaceQueries(), "sturdy queries");
        require(w.supportsSolidRenderQueries(), "solid-render queries");
        require(w.supportsReplaceableByStructuresQueries(), "replaceable queries");
        require(w.supportsCenterSupportQueries(), "center-support queries");
        require(w.supportsFallingBlockQueries(), "falling-block queries");
        require(w.supportsPostWriteFluidStateQueries(), "post-write fluid queries");
        require(w.supportsScheduledFluidTicks(), "fluid ticks");
        require(w.supportsPostProcessingMarks(), "post-processing marks");
        if (p.spiderCorridor() && !p.hasPlacedSpider()) {
            require(w.supportsSpawnerBlockEntityQueries(), "spawner block entities");
        }
        require(w.supportsChestMinecartCreation(), "chest-minecart creation");
    }

    private static List<String> outputStates(PieceFacts p) {
        ArrayList<String> out = new ArrayList<>(List.of(CAVE_AIR, p.mineshaftType().planks(),
                p.mineshaftType().wood(), COBWEB, CHAIN, fenceState(p, true),
                fenceState(p, false), defaultFenceState(p), torchState(p, Face.SOUTH), torchState(p, Face.NORTH),
                railState(p, true), railState(p, false)));
        if (p.spiderCorridor() && !p.hasPlacedSpider()) out.add(SPAWNER);
        return out;
    }

    private static void require(boolean value, String name) {
        if (!value) throw new UnsupportedOperationException("unsupported " + name);
    }

    private static ExecutionResult result(boolean invalid, Counter writes, boolean hps,
            List<SpawnerEffect> spawners, List<ChestMinecartEffect> chests,
            StructureRandom random) {
        return new ExecutionResult(invalid, writes.value, hps, spawners, chests,
                random.continuation());
    }

    private static boolean invalid(BoundingBox b, BoundingBox c, WorldAccess w) {
        int x0 = Math.max(b.minX - 1, c.minX), y0 = Math.max(b.minY - 1, c.minY);
        int z0 = Math.max(b.minZ - 1, c.minZ), x1 = Math.min(b.maxX + 1, c.maxX);
        int y1 = Math.min(b.maxY + 1, c.maxY), z1 = Math.min(b.maxZ + 1, c.maxZ);
        if (w.isMineshaftBlockingBiome(new BlockPos((x0 + x1) / 2, (y0 + y1) / 2,
                (z0 + z1) / 2))) return true;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            if (w.isLiquid(new BlockPos(x, y0, z)) || w.isLiquid(new BlockPos(x, y1, z))) return true;
        }
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) {
            if (w.isLiquid(new BlockPos(x, y, z0)) || w.isLiquid(new BlockPos(x, y, z1))) return true;
        }
        for (int z = z0; z <= z1; z++) for (int y = y0; y <= y1; y++) {
            if (w.isLiquid(new BlockPos(x0, y, z)) || w.isLiquid(new BlockPos(x1, y, z))) return true;
        }
        return false;
    }

    private static void generateBox(PieceFacts p, BoundingBox c, WorldAccess w,
            int x0, int y0, int z0, int x1, int y1, int z1, String state, Counter writes) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) place(p, c, w, x, y, z, state, writes);
        }
    }

    private static void generateMaybeBox(PieceFacts p, BoundingBox c, WorldAccess w,
            StructureRandom r, float probability, int x0, int y0, int z0, int x1, int y1,
            int z1, String state, boolean inside, Counter writes) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            if (r.nextFloat() > probability || inside && !isInterior(p, c, w, x, y, z)) continue;
            place(p, c, w, x, y, z, state, writes);
        }
    }

    private static void placeSupport(PieceFacts p, BoundingBox c, WorldAccess w,
            StructureRandom r, int z, Counter writes) {
        for (int x = 0; x <= 2; x++) if (isAir(getBlock(p, c, w, x, 3, z))) return;
        generateBox(p, c, w, 0, 0, z, 0, 1, z, fenceState(p, true), writes);
        generateBox(p, c, w, 2, 0, z, 2, 1, z, fenceState(p, false), writes);
        if (r.nextInt(4) == 0) {
            place(p, c, w, 0, 2, z, p.mineshaftType().planks(), writes);
            place(p, c, w, 2, 2, z, p.mineshaftType().planks(), writes);
        } else {
            generateBox(p, c, w, 0, 2, z, 2, 2, z, p.mineshaftType().planks(), writes);
            if (r.nextFloat() < .05f) place(p, c, w, 1, 2, z - 1,
                    torchState(p, Face.SOUTH), writes);
            if (r.nextFloat() < .05f) place(p, c, w, 1, 2, z + 1,
                    torchState(p, Face.NORTH), writes);
        }
    }

    private static void maybeCobweb(PieceFacts p, BoundingBox c, WorldAccess w,
            StructureRandom r, float probability, int x, int y, int z, Counter writes) {
        if (!isInterior(p, c, w, x, y, z) || r.nextFloat() >= probability
                || !hasSturdyNeighbours(p, c, w, x, y, z)) return;
        place(p, c, w, x, y, z, COBWEB, writes);
    }

    private static boolean hasSturdyNeighbours(PieceFacts p, BoundingBox c, WorldAccess w,
            int x, int y, int z) {
        BlockPos origin = worldPos(p, x, y, z); int count = 0;
        for (Face face : Face.values()) {
            BlockPos neighbor = origin.move(face);
            if (c.contains(neighbor)) {
                String state = state(w, neighbor);
                if (w.isFaceSturdy(neighbor, state, face.opposite()) && ++count >= 2) return true;
            }
        }
        return false;
    }

    private static void createChest(PieceFacts p, BoundingBox c, WorldAccess w,
            StructureRandom r, int x, int y, int z, Counter writes,
            List<ChestMinecartEffect> chests) {
        BlockPos pos = worldPos(p, x, y, z);
        if (!c.contains(pos) || !isAir(state(w, pos)) || isAir(state(w, pos.move(Face.DOWN)))) return;
        String rail = railState(p, r.nextBoolean());
        place(p, c, w, x, y, z, rail, writes);
        long seed = r.nextLong();
        chests.add(new ChestMinecartEffect(pos,
                new EntityPosition(pos.x + .5, pos.y + .5, pos.z + .5), CHEST_MINECART,
                LOOT_TABLE, seed, CHUNK_GENERATION));
    }

    private static void setPlanks(PieceFacts p, BoundingBox c, WorldAccess w,
            int x, int y, int z, Counter writes) {
        if (!isInterior(p, c, w, x, y, z)) return;
        BlockPos pos = worldPos(p, x, y, z); String current = state(w, pos);
        if (!w.isFaceSturdy(pos, current, Face.UP)) { w.setBlock(pos, p.mineshaftType().planks(), 2); writes.value++; }
    }

    private static void doubleSupport(PieceFacts p, BoundingBox c, WorldAccess w,
            int x, int y, int z, Counter writes) {
        if (sameBlock(getBlock(p, c, w, x, y, z), p.mineshaftType().planks())) {
            pillarOrChain(p, c, w, x, y, z, writes);
        }
        if (sameBlock(getBlock(p, c, w, x + 2, y, z), p.mineshaftType().planks())) {
            pillarOrChain(p, c, w, x + 2, y, z, writes);
        }
    }

    private static void pillarOrChain(PieceFacts p, BoundingBox c, WorldAccess w,
            int x, int y, int z, Counter writes) {
        BlockPos origin = worldPos(p, x, y, z); if (!c.contains(origin)) return;
        int worldY = origin.y; boolean below = true, above = true;
        for (int distance = 1; below || above; distance++) {
            if (below) {
                BlockPos pos = new BlockPos(origin.x, worldY - distance, origin.z);
                String state = state(w, pos);
                boolean empty = w.isReplaceableByStructures(pos, state) && !sameBlock(state, "minecraft:lava");
                if (!empty && w.isFaceSturdy(pos, state, Face.UP)) {
                    for (int py = worldY - distance + 1; py < worldY; py++) {
                        w.setBlock(new BlockPos(origin.x, py, origin.z), p.mineshaftType().wood(), 2); writes.value++;
                    }
                    return;
                }
                below = distance <= 20 && empty && pos.y > w.minY() + 1;
            }
            if (above) {
                BlockPos pos = new BlockPos(origin.x, worldY + distance, origin.z);
                String state = state(w, pos);
                boolean empty = w.isReplaceableByStructures(pos, state);
                if (!empty && w.canSupportCenter(pos, state, Face.DOWN) && !w.isFallingBlock(state)) {
                    w.setBlock(new BlockPos(origin.x, worldY + 1, origin.z),
                            defaultFenceState(p), 2); writes.value++;
                    for (int py = worldY + 2; py < worldY + distance; py++) {
                        w.setBlock(new BlockPos(origin.x, py, origin.z), CHAIN, 2); writes.value++;
                    }
                    return;
                }
                above = distance <= 50 && empty && pos.y < w.maxY();
            }
        }
    }

    private static boolean isInterior(PieceFacts p, BoundingBox c, WorldAccess w,
            int x, int y, int z) {
        BlockPos pos = worldPos(p, x, y + 1, z);
        return c.contains(pos) && pos.y < w.oceanFloorWgHeight(pos.x, pos.z);
    }

    private static String getBlock(PieceFacts p, BoundingBox c, WorldAccess w,
            int x, int y, int z) {
        BlockPos pos = worldPos(p, x, y, z);
        return c.contains(pos) ? state(w, pos) : AIR;
    }

    private static void place(PieceFacts p, BoundingBox c, WorldAccess w,
            int x, int y, int z, String output, Counter writes) {
        BlockPos pos = worldPos(p, x, y, z); if (!c.contains(pos)) return;
        if (isProtected(p.mineshaftType(), state(w, pos))) return;
        w.setBlock(pos, output, 2); writes.value++;
        String fluid = Objects.requireNonNull(w.postWriteFluidType(pos), "post-write fluid");
        if (!fluid.isEmpty()) w.scheduleFluidTick(pos, fluid, 0);
        if (isShapeCheck(output)) w.markForPostProcessing(pos);
    }

    static BlockPos worldPos(PieceFacts p, int x, int y, int z) {
        BoundingBox b = p.boundingBox();
        int wx = switch (p.orientation()) {
            case NORTH, SOUTH -> b.minX + x; case WEST -> b.maxX - z; case EAST -> b.minX + z;
        };
        int wz = switch (p.orientation()) {
            case NORTH -> b.maxZ - z; case SOUTH -> b.minZ + z;
            case WEST, EAST -> b.minZ + x;
        };
        return new BlockPos(wx, b.minY + y, wz);
    }

    private static String state(WorldAccess w, BlockPos p) {
        String state = Objects.requireNonNull(w.blockState(p), "block state");
        if (state.isEmpty()) throw new IllegalArgumentException("empty block state");
        return state;
    }
    private static boolean sameBlock(String state, String block) {
        int property = state.indexOf('['); return (property < 0 ? state : state.substring(0, property)).equals(block);
    }
    private static boolean isAir(String state) {
        return sameBlock(state, AIR) || sameBlock(state, CAVE_AIR) || sameBlock(state, "minecraft:void_air");
    }
    private static boolean isProtected(MineshaftType type, String state) {
        return sameBlock(state, type.planks()) || sameBlock(state, type.wood())
                || sameBlock(state, type.fence()) || sameBlock(state, CHAIN);
    }
    private static boolean isShapeCheck(String state) {
        String block = state.indexOf('[') < 0 ? state : state.substring(0, state.indexOf('['));
        return block.equals("minecraft:oak_fence") || block.equals("minecraft:dark_oak_fence")
                || block.equals("minecraft:wall_torch");
    }

    private static Face transform(PieceFacts p, Face local) {
        Face mirrored = switch (p.orientation()) {
            case SOUTH, WEST -> local == Face.NORTH ? Face.SOUTH : local == Face.SOUTH ? Face.NORTH : local;
            default -> local;
        };
        if (p.orientation() != Direction.WEST && p.orientation() != Direction.EAST) return mirrored;
        return switch (mirrored) {
            case NORTH -> Face.EAST; case EAST -> Face.SOUTH; case SOUTH -> Face.WEST;
            case WEST -> Face.NORTH; default -> mirrored;
        };
    }
    private static String fenceState(PieceFacts p, boolean left) {
        Face connected = transform(p, left ? Face.WEST : Face.EAST);
        return p.mineshaftType().fence() + "[east=" + (connected == Face.EAST)
                + ",north=" + (connected == Face.NORTH) + ",south="
                + (connected == Face.SOUTH) + ",waterlogged=false,west="
                + (connected == Face.WEST) + "]";
    }
    private static String defaultFenceState(PieceFacts p) {
        return p.mineshaftType().fence()
                + "[east=false,north=false,south=false,waterlogged=false,west=false]";
    }
    private static String torchState(PieceFacts p, Face facing) {
        return "minecraft:wall_torch[facing=" + transform(p, facing).name().toLowerCase() + "]";
    }
    private static String railState(PieceFacts p, boolean northSouth) {
        Face axis = transform(p, northSouth ? Face.NORTH : Face.EAST);
        String shape = axis == Face.NORTH || axis == Face.SOUTH ? "north_south" : "east_west";
        return "minecraft:rail[shape=" + shape + ",waterlogged=false]";
    }
    private static final class Counter { int value; }
}
