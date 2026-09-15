package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Kind;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.PieceFact;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.PieceKind;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.SuccessorPayload;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Repository-owned ordered transcription of the pinned 26.3 desert-pyramid piece.
 *
 * <p>The program expands the Java source loops instead of embedding a structure template. It is
 * dormant: no production registry references this class. The caller must provide the two random
 * domains used by the source separately: the structure RNG and the mutable level RNG. Positional
 * archaeology RNG is derived here from the pinned world seed and exact block coordinates.</p>
 */
public final class Mc263DesertPyramidProgram {
    public static final String VERSION = "26.3-snapshot-7";
    public static final String SERVER_SHA1 = "06157fedd67ff4dd6e0e6fa4a9dd0af296f0dd61";
    public static final String STRUCTURE = "minecraft:desert_pyramid";
    public static final String PIECE_TYPE = "minecraft:tedp";
    public static final String CHEST_LOOT = "minecraft:chests/desert_pyramid";
    public static final String ARCHAEOLOGY_LOOT = "minecraft:archaeology/desert_pyramid";

    private static final String AIR = "minecraft:air";
    private static final String SAND = "minecraft:sand";
    private static final String SANDSTONE = "minecraft:sandstone";
    private static final String CUT = "minecraft:cut_sandstone";
    private static final String CHISELED = "minecraft:chiseled_sandstone";
    private static final String ORANGE = "minecraft:orange_terracotta";
    private static final String BLUE = "minecraft:blue_terracotta";
    private static final String SLAB = "minecraft:sandstone_slab[type=bottom,waterlogged=false]";
    private static final String PRESSURE = "minecraft:stone_pressure_plate[powered=false]";
    private static final String TNT = "minecraft:tnt[unstable=false]";
    private static final String SUSPICIOUS = "minecraft:suspicious_sand[dusted=0]";
    private static final List<Operation> OPERATIONS = buildOperations();
    private static final Set<String> REQUIRED_STATES = requiredStates();

    private Mc263DesertPyramidProgram() { }

    public enum OperationKind {
        PLACE, PLACE_IF_NON_AIR, FILL_COLUMN_DOWN, LEVEL_BOOLEAN, VARIANT_PLACE,
        COLLAPSED_ROOF, POTENTIAL_SAND, CHEST
    }

    /** One expanded source-order action, in piece-local coordinates. */
    public static final class Operation {
        private final OperationKind kind;
        private final int x;
        private final int y;
        private final int z;
        private final String state;
        private final String alternateState;
        private final Direction facing;
        private final String successorFlag;

        private Operation(OperationKind kind, int x, int y, int z, String state,
                String alternateState, Direction facing, String successorFlag) {
            this.kind = Objects.requireNonNull(kind, "operation kind");
            this.x = x;
            this.y = y;
            this.z = z;
            this.state = state;
            this.alternateState = alternateState;
            this.facing = facing;
            this.successorFlag = successorFlag;
        }

        public OperationKind kind() { return kind; }
        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        public String state() { return state; }
        public String alternateState() { return alternateState; }
        public String facing() { return facing == null ? "" : facing.key; }
        public String successorFlag() { return successorFlag == null ? "" : successorFlag; }
    }

    public static final class BlockPos {
        private final int x;
        private final int y;
        private final int z;

        public BlockPos(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        private BlockPos below() { return new BlockPos(x, Math.subtractExact(y, 1), z); }
        private BlockPos relative(Direction direction) {
            return new BlockPos(Math.addExact(x, direction.dx), y,
                    Math.addExact(z, direction.dz));
        }
        @Override public boolean equals(Object other) {
            return other instanceof BlockPos value
                    && x == value.x && y == value.y && z == value.z;
        }
        @Override public int hashCode() { return Objects.hash(x, y, z); }
    }

    public static final class Clip {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        public Clip(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted desert-pyramid clip");
            }
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        }

        public boolean contains(BlockPos pos) {
            return pos.x >= minX && pos.x <= maxX && pos.y >= minY && pos.y <= maxY
                    && pos.z >= minZ && pos.z <= maxZ;
        }

        public int minX() { return minX; }
        public int minY() { return minY; }
        public int minZ() { return minZ; }
        public int maxX() { return maxX; }
        public int maxY() { return maxY; }
        public int maxZ() { return maxZ; }
    }

    /**
     * Exact world boundary. Queries and capability methods must be pure. The single settlement
     * call must durably publish the ordered blocks, sidecars, adjusted box and carrier successor
     * atomically and idempotently for {@link AtomicSettlement#identity()}.
     */
    public interface WorldAccess {
        boolean supportsExactState(String exactState);
        boolean supportsMotionBlockingNoLeavesHeight();
        boolean supportsReplaceableByStructuresQuery();
        boolean supportsSolidRenderQuery();
        boolean supportsLevelRandom();
        boolean supportsLootSidecar(String table);
        boolean supportsArchaeologySidecar(String table);
        boolean supportsAtomicSettlement();
        int minY();
        int maxY();
        int motionBlockingNoLeaves(int x, int z);
        String blockState(BlockPos pos);
        boolean isReplaceableByStructures(BlockPos pos, String exactState);
        boolean isSolidRender(BlockPos pos, String exactState);
        boolean nextLevelBoolean();
        float nextLevelFloat();
        void settle(AtomicSettlement settlement);
    }

    public record BlockMutation(BlockPos position, String exactState, int flags) {
        public BlockMutation {
            Objects.requireNonNull(position, "block position");
            requireState(exactState);
            if (flags != 2) throw new IllegalArgumentException("noncanonical block flags");
        }
    }

    public record LootMutation(BlockPos position, String table, long seed) {
        public LootMutation {
            Objects.requireNonNull(position, "loot position");
            if (!CHEST_LOOT.equals(table)) {
                throw new IllegalArgumentException("noncanonical desert-pyramid loot table");
            }
        }
    }

    public record ArchaeologyMutation(BlockPos position, String table, long seed) {
        public ArchaeologyMutation {
            Objects.requireNonNull(position, "archaeology position");
            if (!ARCHAEOLOGY_LOOT.equals(table)) {
                throw new IllegalArgumentException(
                        "noncanonical desert-pyramid archaeology table");
            }
        }
    }

    /** Complete value handed to the durable exact-once settlement boundary. */
    public record AtomicSettlement(String identity, BoundingBox adjustedBoundingBox,
            Mc263HardcodedStructureCarrier successor, List<BlockMutation> blocks,
            List<LootMutation> loot, List<ArchaeologyMutation> archaeology) {
        public AtomicSettlement {
            Objects.requireNonNull(identity, "settlement identity");
            Objects.requireNonNull(adjustedBoundingBox, "adjusted bounding box");
            Objects.requireNonNull(successor, "successor carrier");
            blocks = List.copyOf(blocks);
            loot = List.copyOf(loot);
            archaeology = List.copyOf(archaeology);
        }
    }

    public static final class ExecutionResult {
        private final Mc263HardcodedStructureCarrier successor;
        private final BoundingBox adjustedBoundingBox;
        private final int writes;
        private final int lootSidecars;
        private final int archaeologySidecars;
        private final List<BlockMutation> blocks;
        private final List<LootMutation> loot;
        private final List<ArchaeologyMutation> archaeology;

        private ExecutionResult(Mc263HardcodedStructureCarrier successor,
                BoundingBox adjustedBoundingBox, int writes, int lootSidecars,
                int archaeologySidecars, List<BlockMutation> blocks,
                List<LootMutation> loot, List<ArchaeologyMutation> archaeology) {
            this.successor = successor;
            this.adjustedBoundingBox = adjustedBoundingBox;
            this.writes = writes;
            this.lootSidecars = lootSidecars;
            this.archaeologySidecars = archaeologySidecars;
            this.blocks = List.copyOf(blocks);
            this.loot = List.copyOf(loot);
            this.archaeology = List.copyOf(archaeology);
        }

        public Mc263HardcodedStructureCarrier successor() { return successor; }
        public BoundingBox adjustedBoundingBox() { return adjustedBoundingBox; }
        public int writes() { return writes; }
        public int lootSidecars() { return lootSidecars; }
        public int archaeologySidecars() { return archaeologySidecars; }
        public List<BlockMutation> blocks() { return blocks; }
        public List<LootMutation> loot() { return loot; }
        public List<ArchaeologyMutation> archaeology() { return archaeology; }
    }

    public static List<Operation> operations() { return OPERATIONS; }
    public static Set<String> requiredExactStates() { return REQUIRED_STATES; }

    /** Executes one official per-chunk postProcess/afterPlace call without reseeding its RNGs. */
    public static ExecutionResult execute(Mc263HardcodedStructureCarrier carrier, Clip clip,
            WorldAccess world, WorldgenRandom structureRandom, long worldSeed) {
        return execute(carrier, null, clip, world, structureRandom, worldSeed);
    }

    /** Executes a reload whose official piece BB was persisted after its first HPos alignment. */
    public static ExecutionResult execute(Mc263HardcodedStructureCarrier carrier,
            BoundingBox persistedAdjustedBox, Clip clip, WorldAccess world,
            WorldgenRandom structureRandom, long worldSeed) {
        Objects.requireNonNull(carrier, "desert-pyramid carrier");
        Objects.requireNonNull(clip, "desert-pyramid clip");
        Objects.requireNonNull(world, "desert-pyramid world");
        Objects.requireNonNull(structureRandom, "desert-pyramid structure RNG");
        PieceFact piece = preflight(carrier, persistedAdjustedBox, world);

        int height = carrier.successor().heightPosition();
        BoundingBox adjustedBox = persistedAdjustedBox;
        if (height < 0) {
            int offset = -structureRandom.nextInt(3);
            height = Math.addExact(world.maxY(), 1);
            BoundingBox box = carrier.boundingBox();
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    height = Math.min(height, world.motionBlockingNoLeaves(x, z));
                }
            }
            adjustedBox = shiftY(piece.boundingBox(), Math.addExact(
                    Math.subtractExact(height, piece.boundingBox().minY()), offset));
        }
        int yDelta = Math.subtractExact(adjustedBox.minY(), piece.boundingBox().minY());
        SuccessorPayload successor = carrier.successor().withHeightPosition(height);
        StagedWorld staged = new StagedWorld(world);
        boolean variant = false;
        ArrayList<BlockPos> potentialSand = new ArrayList<>();
        BlockPos collapsedRoof = null;
        int writes = 0;
        int loot = 0;

        for (Operation operation : OPERATIONS) {
            BlockPos pos = worldPos(piece, carrier.rotation(), operation.x, operation.y,
                    operation.z, yDelta);
            switch (operation.kind) {
                case LEVEL_BOOLEAN -> variant = staged.nextLevelBoolean();
                case PLACE -> writes += place(staged, clip, pos,
                        rotateState(operation.state, operation.facing, carrier.rotation()));
                case PLACE_IF_NON_AIR -> {
                    if (clip.contains(pos)
                            && !blockKey(staged.blockState(pos)).equals("minecraft:air")) {
                        staged.setBlock(pos, operation.state);
                        writes++;
                    }
                }
                case VARIANT_PLACE -> writes += place(staged, clip, pos,
                        variant ? operation.state : operation.alternateState);
                case COLLAPSED_ROOF -> writes += place(staged, clip, pos,
                        staged.nextLevelFloat() < 0.33F ? SANDSTONE : SAND);
                case FILL_COLUMN_DOWN -> {
                    if (!clip.contains(pos)) break;
                    BlockPos cursor = pos;
                    while (cursor.y() > world.minY() + 1) {
                        String existing = requireState(staged.blockState(cursor));
                        if (!staged.isReplaceableByStructures(cursor, existing)) break;
                        staged.setBlock(cursor, operation.state);
                        writes++;
                        cursor = cursor.below();
                    }
                }
                case POTENTIAL_SAND -> potentialSand.add(pos);
                case CHEST -> {
                    if (successor.flag(operation.successorFlag) || !clip.contains(pos)) break;
                    boolean completed = !blockKey(staged.blockState(pos)).equals("minecraft:chest");
                    if (completed) {
                        Direction facing = reorientChest(staged, pos);
                        String state = "minecraft:chest[facing=" + facing.key
                                + ",type=single,waterlogged=false]";
                        staged.setBlock(pos, state);
                        writes++;
                        staged.setLoot(pos, structureRandom.nextLong());
                        loot++;
                    }
                    successor = successor.withFlag(operation.successorFlag, completed);
                }
            }
        }

        BlockPos roofOrigin = worldPos(piece, carrier.rotation(), 14, 0, 11, yDelta);
        LegacyRandom roofRandom = LegacyRandom.positional(worldSeed, roofOrigin);
        int roofX = 14 + roofRandom.nextInt(5);
        int roofZ = 11 + roofRandom.nextInt(5);
        collapsedRoof = worldPos(piece, carrier.rotation(), roofX, 0, roofZ, yDelta);

        int archaeology = placeArchaeology(staged, clip, adjustedBox, worldSeed,
                potentialSand, collapsedRoof);
        writes += staged.archaeologyWrites();
        Mc263HardcodedStructureCarrier successorCarrier = carrier.withSuccessor(successor);
        AtomicSettlement settlement = new AtomicSettlement(settlementIdentity(carrier, clip),
                adjustedBox, successorCarrier, staged.blocks(), staged.loot(),
                staged.archaeology());
        world.settle(settlement);
        return new ExecutionResult(successorCarrier, adjustedBox, writes, loot, archaeology,
                staged.blocks(), staged.loot(), staged.archaeology());
    }

    private static PieceFact preflight(Mc263HardcodedStructureCarrier carrier,
            BoundingBox persistedAdjustedBox, WorldAccess world) {
        if (carrier.kind() != Kind.DESERT_PYRAMID || carrier.orderedPieces().size() != 1
                || carrier.orderedPieces().getFirst().kind() != PieceKind.DESERT_PYRAMID) {
            throw new IllegalArgumentException("not an exact desert-pyramid carrier");
        }
        validateAdjustedBox(carrier, persistedAdjustedBox);
        carrier.preflight(carrier.requiredCapabilities(), carrier.requiredExactStates());
        for (String state : REQUIRED_STATES) {
            if (!world.supportsExactState(state)) {
                throw new UnsupportedOperationException("desert-pyramid exact state: " + state);
            }
        }
        require(carrier.successor().heightPosition() >= 0
                        || world.supportsMotionBlockingNoLeavesHeight(),
                "MOTION_BLOCKING_NO_LEAVES height");
        require(world.supportsReplaceableByStructuresQuery(),
                "replaceable-by-structures query");
        require(world.supportsSolidRenderQuery(), "solid-render query");
        require(world.supportsLevelRandom(), "level RNG");
        require(world.supportsLootSidecar(CHEST_LOOT), "desert-pyramid loot sidecar");
        require(world.supportsArchaeologySidecar(ARCHAEOLOGY_LOOT),
                "desert-pyramid archaeology sidecar");
        require(world.supportsAtomicSettlement(),
                "atomic desert-pyramid successor/sidecar settlement");
        return carrier.orderedPieces().getFirst();
    }

    private static int placeArchaeology(StagedWorld world, Clip clip,
            BoundingBox adjustedBox, long worldSeed,
            List<BlockPos> potentialSand, BlockPos collapsedRoof) {
        int sidecars = 0;
        if (clip.contains(collapsedRoof)) {
            world.setArchaeologyBlock(collapsedRoof, SUSPICIOUS);
            world.setArchaeologyLoot(collapsedRoof, packedBlockPos(collapsedRoof));
            sidecars++;
        }
        ArrayList<BlockPos> unique = new ArrayList<>(new LinkedHashSet<>(potentialSand));
        unique.sort(Comparator.comparingInt(BlockPos::y).thenComparingInt(BlockPos::z)
                .thenComparingInt(BlockPos::x));
        BoundingBox box = adjustedBox;
        BlockPos center = new BlockPos(box.minX() + (box.maxX() - box.minX() + 1) / 2,
                box.minY() + (box.maxY() - box.minY() + 1) / 2,
                box.minZ() + (box.maxZ() - box.minZ() + 1) / 2);
        LegacyRandom random = LegacyRandom.positional(worldSeed, center);
        for (int size = unique.size(); size > 1; size--) {
            Collections.swap(unique, size - 1, random.nextInt(size));
        }
        int suspicious = Math.min(unique.size(), random.nextInt(5, 8));
        for (BlockPos pos : unique) {
            if (suspicious > 0) {
                suspicious--;
                if (!clip.contains(pos)) continue;
                world.setArchaeologyBlock(pos, SUSPICIOUS);
                world.setArchaeologyLoot(pos, packedBlockPos(pos));
                sidecars++;
            } else if (clip.contains(pos)) {
                world.setArchaeologyBlock(pos, SAND);
            }
        }
        return sidecars;
    }

    private static long packedBlockPos(BlockPos pos) {
        return ((long) pos.x() & 0x3FFFFFFL) << 38
                | ((long) pos.z() & 0x3FFFFFFL) << 12 | (long) pos.y() & 0xFFFL;
    }

    private static int place(StagedWorld world, Clip clip, BlockPos pos, String state) {
        if (!clip.contains(pos)) return 0;
        world.setBlock(pos, state);
        return 1;
    }

    private static Direction reorientChest(StagedWorld world, BlockPos pos) {
        Direction solid = null;
        for (Direction direction : Direction.horizontal()) {
            String neighbor = blockKey(world.blockState(pos.relative(direction)));
            if (neighbor.equals("minecraft:chest")) return Direction.NORTH;
            if (!world.isSolidRender(pos.relative(direction))) continue;
            if (solid != null) { solid = null; break; }
            solid = direction;
        }
        if (solid != null) return solid.opposite();
        Direction direction = Direction.NORTH;
        if (world.isSolidRender(pos.relative(direction))) direction = direction.opposite();
        if (world.isSolidRender(pos.relative(direction))) direction = direction.clockwise();
        if (world.isSolidRender(pos.relative(direction))) direction = direction.opposite();
        return direction;
    }

    private static void validateAdjustedBox(Mc263HardcodedStructureCarrier carrier,
            BoundingBox adjustedBox) {
        int height = carrier.successor().heightPosition();
        if ((height >= 0) != (adjustedBox != null)) {
            throw new UnsupportedOperationException(height >= 0
                    ? "persisted desert-pyramid adjusted bounding box is unavailable"
                    : "unexpected adjusted bounding box before HPos alignment");
        }
        if (adjustedBox == null) return;
        BoundingBox source = carrier.orderedPieces().getFirst().boundingBox();
        if (adjustedBox.minX() != source.minX() || adjustedBox.maxX() != source.maxX()
                || adjustedBox.minZ() != source.minZ() || adjustedBox.maxZ() != source.maxZ()
                || adjustedBox.maxY() - adjustedBox.minY() != source.maxY() - source.minY()
                || adjustedBox.minY() < height - 2 || adjustedBox.minY() > height) {
            throw new IllegalArgumentException("persisted desert-pyramid adjusted box mismatch");
        }
    }

    private static BoundingBox shiftY(BoundingBox box, int delta) {
        return new BoundingBox(box.minX(), Math.addExact(box.minY(), delta), box.minZ(),
                box.maxX(), Math.addExact(box.maxY(), delta), box.maxZ());
    }

    /** Exact pinned binary-NBT value for the mutable {@code minecraft:tedp} piece. */
    public static byte[] canonicalPieceNbt(Mc263HardcodedStructureCarrier carrier,
            BoundingBox persistedBox) {
        Objects.requireNonNull(carrier, "desert-pyramid carrier");
        Objects.requireNonNull(persistedBox, "desert-pyramid persisted box");
        if (carrier.kind() != Kind.DESERT_PYRAMID
                || carrier.orderedPieces().size() != 1
                || carrier.orderedPieces().getFirst().kind() != PieceKind.DESERT_PYRAMID
                || carrier.orderedPieces().getFirst().template() != null) {
            throw new IllegalArgumentException("noncanonical desert-pyramid carrier shape");
        }
        validatePersistedPieceBox(carrier, persistedBox);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(200);
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(10); output.writeShort(0);
                output.writeByte(11); output.writeUTF("BB"); output.writeInt(6);
                output.writeInt(persistedBox.minX()); output.writeInt(persistedBox.minY());
                output.writeInt(persistedBox.minZ()); output.writeInt(persistedBox.maxX());
                output.writeInt(persistedBox.maxY()); output.writeInt(persistedBox.maxZ());
                writeBooleanTag(output, "hasPlacedChest0",
                        carrier.successor().flag("hasPlacedChest0"));
                writeBooleanTag(output, "hasPlacedChest1",
                        carrier.successor().flag("hasPlacedChest1"));
                writeIntTag(output, "Height", 15);
                output.writeByte(8); output.writeUTF("id"); output.writeUTF(PIECE_TYPE);
                writeIntTag(output, "GD", 0);
                writeIntTag(output, "Width", 21);
                writeIntTag(output, "HPos", carrier.successor().heightPosition());
                writeIntTag(output, "Depth", 21);
                writeBooleanTag(output, "hasPlacedChest2",
                        carrier.successor().flag("hasPlacedChest2"));
                writeIntTag(output, "O", orientationNbtId(carrier.rotation()));
                writeBooleanTag(output, "hasPlacedChest3",
                        carrier.successor().flag("hasPlacedChest3"));
                output.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory desert-pyramid NBT failed", exception);
        }
    }

    public static byte[] canonicalInitialPieceNbt(Mc263HardcodedStructureCarrier carrier) {
        if (carrier.successor().heightPosition() != -1) {
            throw new IllegalArgumentException("expected initial desert-pyramid carrier");
        }
        return canonicalPieceNbt(carrier, carrier.boundingBox());
    }

    private static void validatePersistedPieceBox(Mc263HardcodedStructureCarrier carrier,
            BoundingBox persistedBox) {
        BoundingBox initial = carrier.boundingBox();
        int height = carrier.successor().heightPosition();
        if (persistedBox.minX() != initial.minX() || persistedBox.maxX() != initial.maxX()
                || persistedBox.minZ() != initial.minZ() || persistedBox.maxZ() != initial.maxZ()
                || persistedBox.maxY() - persistedBox.minY() != 14
                || (height < 0 && (height != -1 || !persistedBox.equals(initial)))
                || (height >= 0 && (persistedBox.minY() < height - 2
                        || persistedBox.minY() > height))) {
            throw new IllegalArgumentException("noncanonical desert-pyramid persisted box");
        }
    }

    private static int orientationNbtId(Rotation rotation) {
        return switch (rotation) {
            case SOUTH -> 0;
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> throw new IllegalArgumentException(
                    "desert pyramid requires cardinal rotation");
        };
    }

    private static void writeBooleanTag(DataOutputStream output, String key, boolean value)
            throws IOException {
        output.writeByte(1); output.writeUTF(key); output.writeByte(value ? 1 : 0);
    }

    private static void writeIntTag(DataOutputStream output, String key, int value)
            throws IOException {
        output.writeByte(3); output.writeUTF(key); output.writeInt(value);
    }

    private static String settlementIdentity(Mc263HardcodedStructureCarrier carrier, Clip clip) {
        return STRUCTURE + "@" + carrier.worldSeed() + "/" + carrier.chunkX() + ","
                + carrier.chunkZ() + "/"
                + clip.minX() + "," + clip.minY() + "," + clip.minZ() + ":"
                + clip.maxX() + "," + clip.maxY() + "," + clip.maxZ();
    }

    private static final class StagedWorld {
        private final WorldAccess source;
        private final java.util.LinkedHashMap<BlockPos, String> pending =
                new java.util.LinkedHashMap<>();
        private final ArrayList<BlockMutation> blocks = new ArrayList<>();
        private final ArrayList<LootMutation> loot = new ArrayList<>();
        private final ArrayList<ArchaeologyMutation> archaeology = new ArrayList<>();
        private int archaeologyWrites;

        private StagedWorld(WorldAccess source) { this.source = source; }
        private String blockState(BlockPos pos) {
            String state = pending.get(pos);
            return state == null ? source.blockState(pos) : state;
        }
        private boolean isReplaceableByStructures(BlockPos pos, String state) {
            return source.isReplaceableByStructures(pos, state);
        }
        private boolean isSolidRender(BlockPos pos) {
            String state = blockState(pos);
            return source.isSolidRender(pos, state);
        }
        private boolean nextLevelBoolean() { return source.nextLevelBoolean(); }
        private float nextLevelFloat() { return source.nextLevelFloat(); }
        private void setBlock(BlockPos pos, String state) {
            pending.put(pos, state);
            blocks.add(new BlockMutation(pos, state, 2));
        }
        private void setLoot(BlockPos pos, long seed) {
            loot.add(new LootMutation(pos, CHEST_LOOT, seed));
        }
        private void setArchaeologyLoot(BlockPos pos, long seed) {
            archaeology.add(new ArchaeologyMutation(pos, ARCHAEOLOGY_LOOT, seed));
        }
        private void setArchaeologyBlock(BlockPos pos, String state) {
            setBlock(pos, state);
            archaeologyWrites++;
        }
        private int archaeologyWrites() { return archaeologyWrites; }
        private List<BlockMutation> blocks() { return List.copyOf(blocks); }
        private List<LootMutation> loot() { return List.copyOf(loot); }
        private List<ArchaeologyMutation> archaeology() { return List.copyOf(archaeology); }
    }

    private static String rotateState(String state, Direction facing, Rotation rotation) {
        if (facing == null) return state;
        Direction transformed = transformFacing(facing, rotation);
        return state.replace("{facing}", transformed.key);
    }

    private static Direction transformFacing(Direction facing, Rotation rotation) {
        Direction mirrored = (rotation == Rotation.SOUTH || rotation == Rotation.WEST)
                && (facing == Direction.NORTH || facing == Direction.SOUTH)
                ? facing.opposite() : facing;
        return switch (rotation) {
            case NORTH, SOUTH -> mirrored;
            case WEST, EAST -> mirrored.clockwise();
            default -> throw new IllegalArgumentException("desert pyramid requires cardinal rotation");
        };
    }

    private static BlockPos worldPos(PieceFact piece, Rotation rotation,
            int x, int y, int z, int yDelta) {
        BoundingBox box = piece.boundingBox();
        int worldX;
        int worldZ;
        switch (rotation) {
            case NORTH -> { worldX = box.minX() + x; worldZ = box.maxZ() - z; }
            case SOUTH -> { worldX = box.minX() + x; worldZ = box.minZ() + z; }
            case WEST -> { worldX = box.maxX() - z; worldZ = box.minZ() + x; }
            case EAST -> { worldX = box.minX() + z; worldZ = box.minZ() + x; }
            default -> throw new IllegalArgumentException("desert pyramid requires cardinal rotation");
        }
        return new BlockPos(worldX, Math.addExact(box.minY() + y, yDelta), worldZ);
    }

    private static List<Operation> buildOperations() {
        Builder b = new Builder();
        b.box(0, -4, 0, 20, 0, 20, SANDSTONE, SANDSTONE);
        for (int pos = 1; pos <= 9; pos++) {
            b.box(pos, pos, pos, 20 - pos, pos, 20 - pos, SANDSTONE, SANDSTONE);
            b.box(pos + 1, pos, pos + 1, 19 - pos, pos, 19 - pos, AIR, AIR);
        }
        for (int x = 0; x < 21; x++) for (int z = 0; z < 21; z++) b.fill(x, -5, z);
        b.box(0, 0, 0, 4, 9, 4, SANDSTONE, AIR); b.box(1, 10, 1, 3, 10, 3, SANDSTONE, SANDSTONE);
        b.stair(2,10,0,Direction.NORTH); b.stair(2,10,4,Direction.SOUTH); b.stair(0,10,2,Direction.EAST); b.stair(4,10,2,Direction.WEST);
        b.box(16,0,0,20,9,4,SANDSTONE,AIR); b.box(17,10,1,19,10,3,SANDSTONE,SANDSTONE);
        b.stair(18,10,0,Direction.NORTH); b.stair(18,10,4,Direction.SOUTH); b.stair(16,10,2,Direction.EAST); b.stair(20,10,2,Direction.WEST);
        b.box(8,0,0,12,4,4,SANDSTONE,AIR); b.box(9,1,0,11,3,4,AIR,AIR);
        for (int[] p : new int[][]{{9,1,1},{9,2,1},{9,3,1},{10,3,1},{11,3,1},{11,2,1},{11,1,1}}) b.place(p[0],p[1],p[2],CUT);
        b.box(4,1,1,8,3,3,SANDSTONE,AIR); b.box(4,1,2,8,2,2,AIR,AIR); b.box(12,1,1,16,3,3,SANDSTONE,AIR); b.box(12,1,2,16,2,2,AIR,AIR);
        b.box(5,4,5,15,4,15,SANDSTONE,SANDSTONE); b.box(9,4,9,11,4,11,AIR,AIR);
        b.box(8,1,8,8,3,8,CUT,CUT); b.box(12,1,8,12,3,8,CUT,CUT); b.box(8,1,12,8,3,12,CUT,CUT); b.box(12,1,12,12,3,12,CUT,CUT);
        b.box(1,1,5,4,4,11,SANDSTONE,SANDSTONE); b.box(16,1,5,19,4,11,SANDSTONE,SANDSTONE);
        b.box(6,7,9,6,7,11,SANDSTONE,SANDSTONE); b.box(14,7,9,14,7,11,SANDSTONE,SANDSTONE); b.box(5,5,9,5,7,11,CUT,CUT); b.box(15,5,9,15,7,11,CUT,CUT);
        for (int[] p : new int[][]{{5,5,10},{5,6,10},{6,6,10},{15,5,10},{15,6,10},{14,6,10}}) b.place(p[0],p[1],p[2],AIR);
        b.box(2,4,4,2,6,4,AIR,AIR); b.box(18,4,4,18,6,4,AIR,AIR);
        b.stair(2,4,5,Direction.NORTH); b.stair(2,3,4,Direction.NORTH); b.stair(18,4,5,Direction.NORTH); b.stair(18,3,4,Direction.NORTH);
        b.box(1,1,3,2,2,3,SANDSTONE,SANDSTONE); b.box(18,1,3,19,2,3,SANDSTONE,SANDSTONE);
        b.place(1,1,2,SANDSTONE); b.place(19,1,2,SANDSTONE); b.place(1,2,2,SLAB); b.place(19,2,2,SLAB); b.stair(2,1,2,Direction.WEST); b.stair(18,1,2,Direction.EAST);
        b.box(4,3,5,4,3,17,SANDSTONE,SANDSTONE); b.box(16,3,5,16,3,17,SANDSTONE,SANDSTONE); b.box(3,1,5,4,2,16,AIR,AIR); b.box(15,1,5,16,2,16,AIR,AIR);
        for (int z=5; z<=17; z+=2) { b.place(4,1,z,CUT); b.place(4,2,z,CHISELED); b.place(16,1,z,CUT); b.place(16,2,z,CHISELED); }
        for (int[] p : new int[][]{{10,0,7},{10,0,8},{9,0,9},{11,0,9},{8,0,10},{12,0,10},{7,0,10},{13,0,10},{9,0,11},{11,0,11},{10,0,12},{10,0,13}}) b.place(p[0],p[1],p[2],ORANGE);
        b.place(10,0,10,BLUE);
        for (int x : new int[]{0,20}) b.wallGlyph(x, false);
        for (int x : new int[]{2,18}) b.wallGlyph(x, true);
        b.box(8,4,0,12,6,0,CUT,CUT); b.place(8,6,0,AIR); b.place(12,6,0,AIR); b.place(9,5,0,ORANGE); b.place(10,5,0,CHISELED); b.place(11,5,0,ORANGE);
        b.box(8,-14,8,12,-11,12,CUT,CUT); b.box(8,-10,8,12,-10,12,CHISELED,CHISELED); b.box(8,-9,8,12,-9,12,CUT,CUT); b.box(8,-8,8,12,-1,12,SANDSTONE,SANDSTONE); b.box(9,-11,9,11,-1,11,AIR,AIR);
        b.place(10,-11,10,PRESSURE); b.box(9,-13,9,11,-13,11,TNT,AIR);
        for (int[] p : new int[][]{{8,-11,10},{8,-10,10},{12,-11,10},{12,-10,10},{10,-11,8},{10,-10,8},{10,-11,12},{10,-10,12}}) b.place(p[0],p[1],p[2],AIR);
        for (int[] p : new int[][]{{7,-10,10},{13,-10,10},{10,-10,7},{10,-10,13}}) b.place(p[0],p[1],p[2],CHISELED);
        for (int[] p : new int[][]{{7,-11,10},{13,-11,10},{10,-11,7},{10,-11,13}}) b.place(p[0],p[1],p[2],CUT);
        b.chest(10,-11,12,"hasPlacedChest0"); b.chest(8,-11,10,"hasPlacedChest1"); b.chest(10,-11,8,"hasPlacedChest2"); b.chest(12,-11,10,"hasPlacedChest3");
        b.stair(13,-1,17,Direction.WEST); b.stair(14,-2,17,Direction.WEST); b.stair(15,-3,17,Direction.WEST);
        b.levelBoolean();
        for (int x=12; x<=16; x++) b.place(x,0,17,SAND);
        b.place(14,-1,17,SAND); b.variant(15,-1,17,SAND,SANDSTONE); b.variant(16,-1,17,SANDSTONE,SAND); b.place(15,-2,17,SAND); b.place(16,-2,17,SANDSTONE); b.place(16,-3,17,SAND);
        b.boxSkipAir(13,-3,10,13,-3,15,CUT,CUT); b.boxSkipAir(19,-3,10,19,-3,15,CUT,CUT); b.boxSkipAir(13,-3,10,19,-3,11,CUT,CUT); b.boxSkipAir(13,-3,16,19,-3,16,CUT,CUT);
        b.boxSkipAir(13,-2,10,13,-2,15,CHISELED,CHISELED); b.boxSkipAir(19,-2,10,19,-2,15,CHISELED,CHISELED); b.boxSkipAir(13,-2,10,19,-2,11,CHISELED,CHISELED); b.boxSkipAir(13,-2,16,19,-2,16,CHISELED,CHISELED);
        b.boxSkipAir(13,-1,10,13,-1,15,CUT,CUT); b.boxSkipAir(19,-1,10,19,-1,15,CUT,CUT); b.boxSkipAir(13,-1,10,19,-1,11,CUT,CUT); b.boxSkipAir(13,-1,16,19,-1,16,CUT,CUT);
        b.sandBox(14,-3,11,18,-1,15);
        for (int x=14; x<=18; x++) for (int z=11; z<=15; z++) b.roof(x,0,z);
        b.place(16,-4,13,BLUE);
        for (int[] p : new int[][]{{17,-4,12},{17,-4,14},{15,-4,12},{15,-4,14},{18,-4,13},{14,-4,13},{16,-4,15},{16,-4,11},{19,-4,13},{13,-4,13},{16,-4,16},{16,-4,10}}) b.place(p[0],p[1],p[2],ORANGE);
        b.potential(19,-3,13); b.potential(19,-2,13); b.place(20,-3,13,CUT); b.place(20,-2,13,CHISELED);
        b.potential(13,-3,13); b.potential(13,-2,13); b.place(12,-3,13,CUT); b.place(12,-2,13,CHISELED);
        b.potential(16,-3,16); b.potential(16,-2,16); b.potential(16,-3,10); b.potential(16,-2,10); b.place(16,-3,9,CUT); b.place(16,-2,9,CHISELED);
        return List.copyOf(b.operations);
    }

    private static Set<String> requiredStates() {
        LinkedHashSet<String> states = new LinkedHashSet<>();
        for (Operation operation : OPERATIONS) {
            if (operation.state != null) states.add(operation.state.replace("{facing}", "north"));
            if (operation.alternateState != null) states.add(operation.alternateState);
            if (operation.facing != null) {
                for (Direction direction : Direction.horizontal()) {
                    states.add("minecraft:sandstone_stairs[facing=" + direction.key
                            + ",half=bottom,shape=straight,waterlogged=false]");
                }
            }
        }
        states.add(SUSPICIOUS);
        for (Direction direction : Direction.horizontal()) {
            states.add("minecraft:chest[facing=" + direction.key
                    + ",type=single,waterlogged=false]");
        }
        return Set.copyOf(states);
    }

    private static void require(boolean value, String capability) {
        if (!value) throw new UnsupportedOperationException(capability);
    }

    private static String requireState(String state) {
        Objects.requireNonNull(state, "exact state");
        if (!state.startsWith("minecraft:")) {
            throw new IllegalArgumentException("noncanonical exact state: " + state);
        }
        return state;
    }

    private static String blockKey(String state) {
        int properties = requireState(state).indexOf('[');
        return properties < 0 ? state : state.substring(0, properties);
    }

    private enum Direction {
        SOUTH("south",0,1), WEST("west",-1,0), NORTH("north",0,-1), EAST("east",1,0);
        private final String key; private final int dx; private final int dz;
        Direction(String key, int dx, int dz) { this.key=key; this.dx=dx; this.dz=dz; }
        private static Direction[] horizontal() { return values(); }
        private Direction opposite() { return fromVector(-dx,-dz); }
        private Direction clockwise() { return fromVector(-dz,dx); }
        private static Direction fromVector(int dx, int dz) {
            for (Direction value : values()) if (value.dx==dx && value.dz==dz) return value;
            throw new IllegalArgumentException("non-cardinal direction");
        }
    }

    private static final class Builder {
        private final ArrayList<Operation> operations = new ArrayList<>();
        private void place(int x,int y,int z,String state) { operations.add(new Operation(OperationKind.PLACE,x,y,z,state,null,null,null)); }
        private void stair(int x,int y,int z,Direction facing) { operations.add(new Operation(OperationKind.PLACE,x,y,z,"minecraft:sandstone_stairs[facing={facing},half=bottom,shape=straight,waterlogged=false]",null,facing,null)); }
        private void fill(int x,int y,int z) { operations.add(new Operation(OperationKind.FILL_COLUMN_DOWN,x,y,z,SANDSTONE,null,null,null)); }
        private void levelBoolean() { operations.add(new Operation(OperationKind.LEVEL_BOOLEAN,0,0,0,null,null,null,null)); }
        private void variant(int x,int y,int z,String whenTrue,String whenFalse) { operations.add(new Operation(OperationKind.VARIANT_PLACE,x,y,z,whenTrue,whenFalse,null,null)); }
        private void roof(int x,int y,int z) { operations.add(new Operation(OperationKind.COLLAPSED_ROOF,x,y,z,SANDSTONE,SAND,null,null)); }
        private void potential(int x,int y,int z) { operations.add(new Operation(OperationKind.POTENTIAL_SAND,x,y,z,null,null,null,null)); }
        private void chest(int x,int y,int z,String flag) { operations.add(new Operation(OperationKind.CHEST,x,y,z,null,null,null,flag)); }
        private void sandBox(int x0,int y0,int z0,int x1,int y1,int z1) { for(int y=y0;y<=y1;y++) for(int x=x0;x<=x1;x++) for(int z=z0;z<=z1;z++) potential(x,y,z); }
        private void box(int x0,int y0,int z0,int x1,int y1,int z1,String edge,String fill) { for(int y=y0;y<=y1;y++) for(int x=x0;x<=x1;x++) for(int z=z0;z<=z1;z++) place(x,y,z,(y==y0||y==y1||x==x0||x==x1||z==z0||z==z1)?edge:fill); }
        private void boxSkipAir(int x0,int y0,int z0,int x1,int y1,int z1,String edge,String fill) { for(int y=y0;y<=y1;y++) for(int x=x0;x<=x1;x++) for(int z=z0;z<=z1;z++) operations.add(new Operation(OperationKind.PLACE_IF_NON_AIR,x,y,z,(y==y0||y==y1||x==x0||x==x1||z==z0||z==z1)?edge:fill,null,null,null)); }
        private void wallGlyph(int coordinate, boolean front) { int[][] pattern={{-1,2,0},{0,2,1},{1,2,0},{-1,3,0},{0,3,1},{1,3,0},{-1,4,1},{0,4,2},{1,4,1},{-1,5,0},{0,5,1},{1,5,0},{-1,6,1},{0,6,2},{1,6,1},{-1,7,1},{0,7,1},{1,7,1},{-1,8,0},{0,8,0},{1,8,0}}; for(int[] p:pattern){String s=p[2]==0?CUT:p[2]==1?ORANGE:CHISELED;if(front)place(coordinate+p[0],p[1],0,s);else place(coordinate,p[1],p[0]+2,s);} }
    }

    /** Exact 48-bit source used by createThreadLocalInstance(seed).forkPositional(). */
    private static final class LegacyRandom {
        private static final long MASK=(1L<<48)-1; private long seed;
        private LegacyRandom(long seed) { this.seed=(seed^25214903917L)&MASK; }
        private static LegacyRandom positional(long worldSeed, BlockPos pos) { LegacyRandom root=new LegacyRandom(worldSeed); long salt=root.nextLong(); long mixed=((long)(pos.x*3129871)^((long)pos.z*116129781L)^(long)pos.y); mixed=mixed*mixed*42317861L+mixed*11L; return new LegacyRandom((mixed>>16)^salt); }
        private int next(int bits){seed=(seed*25214903917L+11L)&MASK;return (int)(seed>>>(48-bits));}
        private long nextLong(){return ((long)next(32)<<32)+(long)next(32);}
        private int nextInt(int bound){if(bound<=0)throw new IllegalArgumentException("bound");if((bound&-bound)==bound)return(int)((bound*(long)next(31))>>31);int bits,value;do{bits=next(31);value=bits%bound;}while(bits-value+(bound-1)<0);return value;}
        private int nextInt(int origin,int bound){return origin+nextInt(bound-origin);}
    }
}
