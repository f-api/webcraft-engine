package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Kind;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.PieceKind;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.SuccessorPayload;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dormant exact procedural transcription of the pinned 26.3 jungle-pyramid piece.
 *
 * <p>The ordered operation stream expands the hardcoded piece's source loops. It contains no
 * template asset and is not registered in the production terrain pipeline. Selector draws,
 * tripwire/redstone writes and conditional container helpers retain source order.</p>
 */
public final class Mc263JunglePyramidProgram {
    public static final String VERSION = Mc263HardcodedStructureCarrier.VERSION;
    public static final String SERVER_SHA1 = Mc263HardcodedStructureCarrier.SERVER_SHA1;
    public static final String STRUCTURE_ID = "minecraft:jungle_pyramid";
    public static final String PIECE_TYPE = "minecraft:tejp";
    public static final String CHEST_LOOT = "minecraft:chests/jungle_temple";
    public static final String DISPENSER_LOOT = "minecraft:chests/jungle_temple_dispenser";
    private static final int MOSS_CUTOFF = 6_710_887;
    private static final List<Operation> OPERATIONS = buildOperations();

    private Mc263JunglePyramidProgram() { }

    public enum OperationKind { MOSS_STONE, PLACE, CONTAINER }

    /** One source-order operation in piece-local coordinates. */
    public static final class Operation {
        private final OperationKind kind;
        private final int x;
        private final int y;
        private final int z;
        private final State state;
        private final String containerBlock;
        private final String lootTable;
        private final String successorFlag;

        private Operation(OperationKind kind, int x, int y, int z, State state,
                String containerBlock, String lootTable, String successorFlag) {
            this.kind = kind; this.x = x; this.y = y; this.z = z; this.state = state;
            this.containerBlock = containerBlock; this.lootTable = lootTable;
            this.successorFlag = successorFlag;
        }

        public OperationKind kind() { return kind; }
        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        public String successorFlag() { return successorFlag == null ? "" : successorFlag; }
        public String lootTable() { return lootTable == null ? "" : lootTable; }
        public String exactState(Rotation rotation) {
            return state == null ? "" : state.exact(Objects.requireNonNull(rotation, "rotation"));
        }
    }

    public record BlockPos(int x, int y, int z) {
        BlockPos relative(Direction direction) {
            return new BlockPos(Math.addExact(x, direction.dx), y,
                    Math.addExact(z, direction.dz));
        }
    }

    public record Clip(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Clip {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted jungle-pyramid clip");
            }
        }
        public boolean contains(BlockPos pos) {
            return pos.x >= minX && pos.x <= maxX && pos.y >= minY && pos.y <= maxY
                    && pos.z >= minZ && pos.z <= maxZ;
        }
    }

    public record BlockMutation(BlockPos position, String exactState) { }
    public record LootMutation(BlockPos position, String table, long seed) { }
    public record AtomicSettlement(String identity, List<BlockMutation> blocks,
            List<LootMutation> loot) {
        public AtomicSettlement {
            Objects.requireNonNull(identity, "identity");
            blocks = List.copyOf(blocks);
            loot = List.copyOf(loot);
        }
    }

    /** Mutable-world boundary required by the exact dormant executor. */
    public interface WorldAccess {
        boolean supportsExactState(String exactState);
        boolean supportsMotionBlockingNoLeavesHeight();
        boolean supportsSolidRenderQuery();
        boolean supportsLootSidecar(String table);
        boolean supportsAtomicSettlement();
        int motionBlockingNoLeaves(int x, int z);
        String blockState(BlockPos position);
        boolean isSolidRender(BlockPos position, String exactState);
        void settle(AtomicSettlement settlement);
    }

    public record ExecutionResult(Mc263HardcodedStructureCarrier successor,
            BoundingBox adjustedBoundingBox, int writes, int lootSidecars) { }

    public static List<Operation> operations() { return OPERATIONS; }

    /** Executes without reseeding the supplied structure RNG. */
    public static ExecutionResult execute(Mc263HardcodedStructureCarrier carrier, Clip clip,
            WorldAccess world, WorldgenRandom random) {
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(clip, "clip");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(random, "random");
        preflight(carrier, world);

        SuccessorPayload successor = carrier.successor();
        int height = successor.heightPosition();
        if (height < 0) {
            long total = 0;
            int count = 0;
            BoundingBox box = carrier.boundingBox();
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    if (!clip.contains(new BlockPos(x, 64, z))) continue;
                    total += world.motionBlockingNoLeaves(x, z);
                    count++;
                }
            }
            if (count == 0) {
                return new ExecutionResult(carrier, carrier.boundingBox(), 0, 0);
            }
            height = Math.toIntExact(total / count);
            successor = successor.withHeightPosition(height);
        }

        int yDelta = Math.subtractExact(height, carrier.boundingBox().minY());
        BoundingBox adjusted = shifted(carrier.boundingBox(), yDelta);
        ArrayList<BlockMutation> blocks = new ArrayList<>();
        ArrayList<LootMutation> loot = new ArrayList<>();
        HashMap<BlockPos, String> overlay = new HashMap<>();
        for (Operation operation : OPERATIONS) {
            BlockPos position = worldPos(carrier, operation.x, operation.y, operation.z, yDelta);
            if (operation.kind == OperationKind.MOSS_STONE) {
                String state = random.next(24) < MOSS_CUTOFF
                        ? "minecraft:cobblestone" : "minecraft:mossy_cobblestone";
                if (clip.contains(position)) put(blocks, overlay, position, state);
            } else if (operation.kind == OperationKind.PLACE) {
                if (clip.contains(position)) {
                    put(blocks, overlay, position, operation.state.exact(carrier.rotation()));
                }
            } else if (!successor.flag(operation.successorFlag) && clip.contains(position)) {
                String existing = stateAt(world, overlay, position);
                if (!blockKey(existing).equals(operation.containerBlock)) {
                    String state;
                    if (operation.containerBlock.equals("minecraft:chest")) {
                        Direction facing = chestFacing(world, overlay, position);
                        state = "minecraft:chest[facing=" + facing.key
                                + ",type=single,waterlogged=false]";
                    } else {
                        state = operation.state.exact(carrier.rotation());
                    }
                    put(blocks, overlay, position, state);
                    loot.add(new LootMutation(position, operation.lootTable, random.nextLong()));
                    successor = successor.withFlag(operation.successorFlag, true);
                }
            }
        }

        Mc263HardcodedStructureCarrier resultCarrier = carrier.withSuccessor(successor);
        String identity = STRUCTURE_ID + ":" + carrier.worldSeed() + ":"
                + carrier.chunkX() + ":" + carrier.chunkZ() + ":" + height;
        world.settle(new AtomicSettlement(identity, blocks, loot));
        return new ExecutionResult(resultCarrier, adjusted, blocks.size(), loot.size());
    }

    /** Exact pinned binary-NBT value for the mutable jungle-pyramid piece successor. */
    public static byte[] canonicalPieceNbt(Mc263HardcodedStructureCarrier carrier) {
        requireCarrier(carrier);
        SuccessorPayload successor = carrier.successor();
        int height = successor.heightPosition();
        int delta = height < 0 ? 0 : Math.subtractExact(height, carrier.boundingBox().minY());
        BoundingBox box = shifted(carrier.boundingBox(), delta);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(224);
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(10); output.writeShort(0);
                output.writeByte(11); output.writeUTF("BB"); output.writeInt(6);
                output.writeInt(box.minX()); output.writeInt(box.minY()); output.writeInt(box.minZ());
                output.writeInt(box.maxX()); output.writeInt(box.maxY()); output.writeInt(box.maxZ());
                // Pinned CompoundTag HashMap encounter order.
                bool(output, "placedTrap2", successor.flag("placedTrap2"));
                bool(output, "placedMainChest", successor.flag("placedMainChest"));
                integer(output, "Height", 10);
                string(output, "id", PIECE_TYPE);
                bool(output, "placedTrap1", successor.flag("placedTrap1"));
                integer(output, "GD", 0);
                integer(output, "Width", 12);
                integer(output, "HPos", height);
                bool(output, "placedHiddenChest", successor.flag("placedHiddenChest"));
                integer(output, "Depth", 15);
                integer(output, "O", orientationNbtId(carrier.rotation()));
                output.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory jungle-pyramid NBT failed", exception);
        }
    }

    private static void preflight(Mc263HardcodedStructureCarrier carrier, WorldAccess world) {
        requireCarrier(carrier);
        carrier.preflight(carrier.requiredCapabilities(), carrier.requiredExactStates());
        LinkedHashSet<String> states = new LinkedHashSet<>();
        states.add("minecraft:cobblestone");
        states.add("minecraft:mossy_cobblestone");
        for (Operation operation : OPERATIONS) {
            if (operation.state != null) states.add(operation.state.exact(carrier.rotation()));
        }
        for (Direction facing : Direction.HORIZONTAL) {
            states.add("minecraft:chest[facing=" + facing.key
                    + ",type=single,waterlogged=false]");
        }
        for (String state : states) {
            if (!world.supportsExactState(state)) {
                throw new UnsupportedOperationException(
                        "unsupported jungle-pyramid exact state: " + state);
            }
        }
        require(carrier.successor().heightPosition() >= 0
                        || world.supportsMotionBlockingNoLeavesHeight(),
                "MOTION_BLOCKING_NO_LEAVES height");
        require(world.supportsSolidRenderQuery(), "solid-render query");
        require(world.supportsLootSidecar(CHEST_LOOT), "jungle-pyramid chest loot");
        require(world.supportsLootSidecar(DISPENSER_LOOT), "jungle-pyramid dispenser loot");
        require(world.supportsAtomicSettlement(), "atomic jungle-pyramid settlement");
    }

    private static void requireCarrier(Mc263HardcodedStructureCarrier carrier) {
        if (carrier.kind() != Kind.JUNGLE_PYRAMID
                || carrier.orderedPieces().size() != 1
                || carrier.orderedPieces().getFirst().kind() != PieceKind.JUNGLE_PYRAMID) {
            throw new IllegalArgumentException("noncanonical jungle-pyramid carrier shape");
        }
        if (!carrier.successor().orderedKeys().equals(List.of(
                "placedMainChest", "placedHiddenChest", "placedTrap1", "placedTrap2"))) {
            throw new IllegalArgumentException("noncanonical jungle-pyramid successor");
        }
    }

    private static List<Operation> buildOperations() {
        ArrayList<Operation> out = new ArrayList<>(2_600);
        mossBox(out, 0, -4, 0, 11, 0, 14);
        mossBox(out, 2, 1, 2, 9, 2, 2);
        mossBox(out, 2, 1, 12, 9, 2, 12);
        mossBox(out, 2, 1, 3, 2, 2, 11);
        mossBox(out, 9, 1, 3, 9, 2, 11);
        mossBox(out, 1, 3, 1, 10, 6, 1);
        mossBox(out, 1, 3, 13, 10, 6, 13);
        mossBox(out, 1, 3, 2, 1, 6, 12);
        mossBox(out, 10, 3, 2, 10, 6, 12);
        mossBox(out, 2, 3, 2, 9, 3, 12);
        mossBox(out, 2, 6, 2, 9, 6, 12);
        mossBox(out, 3, 7, 3, 8, 7, 11);
        mossBox(out, 4, 8, 4, 7, 8, 10);
        box(out, 3, 1, 3, 8, 2, 11, fixed("minecraft:air"));
        box(out, 4, 3, 6, 7, 3, 9, fixed("minecraft:air"));
        box(out, 2, 4, 2, 9, 5, 12, fixed("minecraft:air"));
        box(out, 4, 6, 5, 7, 6, 9, fixed("minecraft:air"));
        box(out, 5, 7, 6, 6, 7, 8, fixed("minecraft:air"));
        box(out, 5, 1, 2, 6, 2, 2, fixed("minecraft:air"));
        box(out, 5, 2, 12, 6, 2, 12, fixed("minecraft:air"));
        box(out, 5, 5, 1, 6, 5, 1, fixed("minecraft:air"));
        box(out, 5, 5, 13, 6, 5, 13, fixed("minecraft:air"));
        place(out, 1, 5, 5, fixed("minecraft:air")); place(out, 10, 5, 5, fixed("minecraft:air"));
        place(out, 1, 5, 9, fixed("minecraft:air")); place(out, 10, 5, 9, fixed("minecraft:air"));
        for (int z = 0; z <= 14; z += 14) {
            mossBox(out, 2, 4, z, 2, 5, z); mossBox(out, 4, 4, z, 4, 5, z);
            mossBox(out, 7, 4, z, 7, 5, z); mossBox(out, 9, 4, z, 9, 5, z);
        }
        mossBox(out, 5, 6, 0, 6, 6, 0);
        for (int x = 0; x <= 11; x += 11) {
            for (int z = 2; z <= 12; z += 2) mossBox(out, x, 4, z, x, 5, z);
            mossBox(out, x, 6, 5, x, 6, 5); mossBox(out, x, 6, 9, x, 6, 9);
        }
        mossBox(out, 2, 7, 2, 2, 9, 2); mossBox(out, 9, 7, 2, 9, 9, 2);
        mossBox(out, 2, 7, 12, 2, 9, 12); mossBox(out, 9, 7, 12, 9, 9, 12);
        mossBox(out, 4, 9, 4, 4, 9, 4); mossBox(out, 7, 9, 4, 7, 9, 4);
        mossBox(out, 4, 9, 10, 4, 9, 10); mossBox(out, 7, 9, 10, 7, 9, 10);
        mossBox(out, 5, 9, 7, 6, 9, 7);
        State northStairs = directional("minecraft:cobblestone_stairs", Direction.NORTH,
                "half=bottom,shape=straight,waterlogged=false");
        State southStairs = directional("minecraft:cobblestone_stairs", Direction.SOUTH,
                "half=bottom,shape=straight,waterlogged=false");
        State eastStairs = directional("minecraft:cobblestone_stairs", Direction.EAST,
                "half=bottom,shape=straight,waterlogged=false");
        State westStairs = directional("minecraft:cobblestone_stairs", Direction.WEST,
                "half=bottom,shape=straight,waterlogged=false");
        place(out, 5, 9, 6, northStairs); place(out, 6, 9, 6, northStairs);
        place(out, 5, 9, 8, southStairs); place(out, 6, 9, 8, southStairs);
        for (int x = 4; x <= 7; x++) place(out, x, 0, 0, northStairs);
        for (int x : List.of(4, 7)) {
            place(out, x, 1, 8, northStairs); place(out, x, 2, 9, northStairs);
            place(out, x, 3, 10, northStairs);
        }
        mossBox(out, 4, 1, 9, 4, 1, 9); mossBox(out, 7, 1, 9, 7, 1, 9);
        mossBox(out, 4, 1, 10, 7, 2, 10); mossBox(out, 5, 4, 5, 6, 4, 5);
        place(out, 4, 4, 5, eastStairs); place(out, 7, 4, 5, westStairs);
        for (int i = 0; i < 4; i++) {
            place(out, 5, -i, 6 + i, southStairs); place(out, 6, -i, 6 + i, southStairs);
            box(out, 5, -i, 7 + i, 6, -i, 9 + i, fixed("minecraft:air"));
        }
        box(out, 1, -3, 12, 10, -1, 13, fixed("minecraft:air"));
        box(out, 1, -3, 1, 3, -1, 13, fixed("minecraft:air"));
        box(out, 1, -3, 1, 9, -1, 5, fixed("minecraft:air"));
        for (int z = 1; z <= 13; z += 2) mossBox(out, 1, -3, z, 1, -2, z);
        for (int z = 2; z <= 12; z += 2) mossBox(out, 1, -1, z, 3, -1, z);
        mossBox(out, 2, -2, 1, 5, -2, 1); mossBox(out, 7, -2, 1, 9, -2, 1);
        mossBox(out, 6, -3, 1, 6, -3, 1); mossBox(out, 6, -1, 1, 6, -1, 1);
        place(out, 1, -3, 8, directional("minecraft:tripwire_hook", Direction.EAST,
                "attached=true,facing={facing},powered=false"));
        place(out, 4, -3, 8, directional("minecraft:tripwire_hook", Direction.WEST,
                "attached=true,facing={facing},powered=false"));
        State tripEW = sides("minecraft:tripwire", Set.of(Direction.EAST, Direction.WEST),
                "attached=true,disarmed=false,{sides},powered=false");
        place(out, 2, -3, 8, tripEW); place(out, 3, -3, 8, tripEW);
        State redNS = sides("minecraft:redstone_wire", Set.of(Direction.NORTH, Direction.SOUTH),
                "{sides},power=0");
        for (int z = 7; z >= 2; z--) place(out, 5, -3, z, redNS);
        place(out, 5, -3, 1, sides("minecraft:redstone_wire",
                Set.of(Direction.NORTH, Direction.WEST), "{sides},power=0"));
        place(out, 4, -3, 1, sides("minecraft:redstone_wire",
                Set.of(Direction.EAST, Direction.WEST), "{sides},power=0"));
        place(out, 3, -3, 1, fixed("minecraft:mossy_cobblestone"));
        container(out, 3, -2, 1, "minecraft:dispenser",
                directional("minecraft:dispenser", Direction.NORTH, "triggered=false"),
                DISPENSER_LOOT, "placedTrap1");
        place(out, 3, -2, 2, sides("minecraft:vine", Set.of(Direction.SOUTH), "{vine}"));
        place(out, 7, -3, 1, directional("minecraft:tripwire_hook", Direction.NORTH,
                "attached=true,facing={facing},powered=false"));
        place(out, 7, -3, 5, directional("minecraft:tripwire_hook", Direction.SOUTH,
                "attached=true,facing={facing},powered=false"));
        State tripNS = sides("minecraft:tripwire", Set.of(Direction.NORTH, Direction.SOUTH),
                "attached=true,disarmed=false,{sides},powered=false");
        place(out, 7, -3, 2, tripNS); place(out, 7, -3, 3, tripNS); place(out, 7, -3, 4, tripNS);
        place(out, 8, -3, 6, sides("minecraft:redstone_wire",
                Set.of(Direction.EAST, Direction.WEST), "{sides},power=0"));
        place(out, 9, -3, 6, sides("minecraft:redstone_wire",
                Set.of(Direction.SOUTH, Direction.WEST), "{sides},power=0"));
        place(out, 9, -3, 5, redstone(Map.of(Direction.NORTH, "side", Direction.SOUTH, "up")));
        place(out, 9, -3, 4, fixed("minecraft:mossy_cobblestone"));
        place(out, 9, -2, 4, redNS);
        container(out, 9, -2, 3, "minecraft:dispenser",
                directional("minecraft:dispenser", Direction.WEST, "triggered=false"),
                DISPENSER_LOOT, "placedTrap2");
        State vineEast = sides("minecraft:vine", Set.of(Direction.EAST), "{vine}");
        place(out, 8, -1, 3, vineEast); place(out, 8, -2, 3, vineEast);
        container(out, 8, -3, 3, "minecraft:chest", null, CHEST_LOOT, "placedMainChest");
        for (int[] p : List.of(new int[]{9,-3,2}, new int[]{8,-3,1}, new int[]{4,-3,5},
                new int[]{5,-2,5}, new int[]{5,-1,5}, new int[]{6,-3,5},
                new int[]{7,-2,5}, new int[]{7,-1,5}, new int[]{8,-3,5})) {
            place(out, p[0], p[1], p[2], fixed("minecraft:mossy_cobblestone"));
        }
        mossBox(out, 9, -1, 1, 9, -1, 5);
        box(out, 8, -3, 8, 10, -1, 10, fixed("minecraft:air"));
        place(out, 8, -2, 11, fixed("minecraft:chiseled_stone_bricks"));
        place(out, 9, -2, 11, fixed("minecraft:chiseled_stone_bricks"));
        place(out, 10, -2, 11, fixed("minecraft:chiseled_stone_bricks"));
        State lever = directional("minecraft:lever", Direction.NORTH,
                "face=wall,facing={facing},powered=false");
        place(out, 8, -2, 12, lever); place(out, 9, -2, 12, lever); place(out, 10, -2, 12, lever);
        mossBox(out, 8, -3, 8, 8, -3, 10); mossBox(out, 10, -3, 8, 10, -3, 10);
        place(out, 10, -2, 9, fixed("minecraft:mossy_cobblestone"));
        place(out, 8, -2, 9, redNS); place(out, 8, -2, 10, redNS);
        place(out, 10, -1, 9, redstone(Map.of(Direction.NORTH, "side",
                Direction.SOUTH, "side", Direction.EAST, "side", Direction.WEST, "side")));
        place(out, 9, -2, 8, directional("minecraft:sticky_piston", Direction.UP,
                "extended=false,facing={facing}"));
        place(out, 10, -2, 8, directional("minecraft:sticky_piston", Direction.WEST,
                "extended=false,facing={facing}"));
        place(out, 10, -1, 8, directional("minecraft:sticky_piston", Direction.WEST,
                "extended=false,facing={facing}"));
        place(out, 10, -2, 10, directional("minecraft:repeater", Direction.NORTH,
                "delay=1,facing={facing},locked=false,powered=false"));
        container(out, 9, -3, 10, "minecraft:chest", null, CHEST_LOOT, "placedHiddenChest");
        return List.copyOf(out);
    }

    private static void mossBox(ArrayList<Operation> out, int x0, int y0, int z0,
            int x1, int y1, int z1) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                out.add(new Operation(OperationKind.MOSS_STONE, x, y, z,
                        null, null, null, null));
    }

    private static void box(ArrayList<Operation> out, int x0, int y0, int z0,
            int x1, int y1, int z1, State state) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) place(out, x, y, z, state);
    }

    private static void place(ArrayList<Operation> out, int x, int y, int z, State state) {
        out.add(new Operation(OperationKind.PLACE, x, y, z, state, null, null, null));
    }

    private static void container(ArrayList<Operation> out, int x, int y, int z,
            String block, State state, String loot, String flag) {
        out.add(new Operation(OperationKind.CONTAINER, x, y, z, state, block, loot, flag));
    }

    private interface State { String exact(Rotation rotation); }
    private static State fixed(String state) { return ignored -> state; }
    private static State directional(String block, Direction facing, String remaining) {
        return rotation -> {
            String suffix = remaining.replace("{facing}", transform(facing, rotation).key);
            if (!remaining.contains("{facing}")) suffix = "facing="
                    + transform(facing, rotation).key + "," + suffix;
            return block + "[" + suffix + "]";
        };
    }
    private static State sides(String block, Set<Direction> connected, String format) {
        Map<Direction, String> values = new HashMap<>();
        for (Direction direction : Direction.HORIZONTAL) {
            values.put(direction, connected.contains(direction) ? "side" : "none");
        }
        if (block.equals("minecraft:tripwire") || block.equals("minecraft:vine")) {
            for (Direction direction : Direction.HORIZONTAL) {
                values.put(direction, connected.contains(direction) ? "true" : "false");
            }
        }
        return rotation -> stateWithSides(block, transformSides(values, rotation), format);
    }
    private static State redstone(Map<Direction, String> values) {
        return rotation -> stateWithSides("minecraft:redstone_wire",
                transformSides(values, rotation), "{sides},power=0");
    }
    private static String stateWithSides(String block, Map<Direction, String> values,
            String format) {
        if (block.equals("minecraft:vine")) {
            return block + "[east=" + values.getOrDefault(Direction.EAST, "false")
                    + ",north=" + values.getOrDefault(Direction.NORTH, "false")
                    + ",south=" + values.getOrDefault(Direction.SOUTH, "false")
                    + ",up=false,west=" + values.getOrDefault(Direction.WEST, "false") + "]";
        }
        if (block.equals("minecraft:tripwire")) {
            return block + "[attached=true,disarmed=false,east="
                    + values.getOrDefault(Direction.EAST, "false") + ",north="
                    + values.getOrDefault(Direction.NORTH, "false")
                    + ",powered=false,south="
                    + values.getOrDefault(Direction.SOUTH, "false") + ",west="
                    + values.getOrDefault(Direction.WEST, "false") + "]";
        }
        if (block.equals("minecraft:redstone_wire")) {
            return block + "[east=" + values.getOrDefault(Direction.EAST, "none")
                    + ",north=" + values.getOrDefault(Direction.NORTH, "none")
                    + ",power=0,south=" + values.getOrDefault(Direction.SOUTH, "none")
                    + ",west=" + values.getOrDefault(Direction.WEST, "none") + "]";
        }
        String sides = "east=" + values.getOrDefault(Direction.EAST, "none")
                + ",north=" + values.getOrDefault(Direction.NORTH, "none")
                + ",south=" + values.getOrDefault(Direction.SOUTH, "none")
                + ",west=" + values.getOrDefault(Direction.WEST, "none");
        return block + "[" + format.replace("{sides}", sides) + "]";
    }
    private static Map<Direction, String> transformSides(Map<Direction, String> source,
            Rotation rotation) {
        HashMap<Direction, String> result = new HashMap<>();
        for (Map.Entry<Direction, String> entry : source.entrySet()) {
            result.put(transform(entry.getKey(), rotation), entry.getValue());
        }
        return result;
    }

    private enum Direction {
        NORTH("north", 0, -1), EAST("east", 1, 0), SOUTH("south", 0, 1),
        WEST("west", -1, 0), UP("up", 0, 0);
        private static final List<Direction> HORIZONTAL = List.of(NORTH, EAST, SOUTH, WEST);
        private final String key; private final int dx; private final int dz;
        Direction(String key, int dx, int dz) { this.key = key; this.dx = dx; this.dz = dz; }
    }

    private static Direction transform(Direction direction, Rotation rotation) {
        if (direction == Direction.UP) return direction;
        Direction mirrored = (rotation == Rotation.SOUTH || rotation == Rotation.WEST)
                && (direction == Direction.NORTH || direction == Direction.SOUTH)
                ? opposite(direction) : direction;
        return rotation == Rotation.WEST || rotation == Rotation.EAST
                ? clockwise(mirrored) : mirrored;
    }
    private static Direction clockwise(Direction direction) {
        return switch (direction) {
            case NORTH -> Direction.EAST; case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST; case WEST -> Direction.NORTH;
            default -> direction;
        };
    }
    private static Direction opposite(Direction direction) {
        return switch (direction) {
            case NORTH -> Direction.SOUTH; case SOUTH -> Direction.NORTH;
            case EAST -> Direction.WEST; case WEST -> Direction.EAST;
            default -> direction;
        };
    }

    private static Direction chestFacing(WorldAccess world, Map<BlockPos, String> overlay,
            BlockPos position) {
        Direction solid = null;
        for (Direction direction : Direction.HORIZONTAL) {
            BlockPos neighbor = position.relative(direction);
            String state = stateAt(world, overlay, neighbor);
            if (blockKey(state).equals("minecraft:chest")) return Direction.NORTH;
            if (!world.isSolidRender(neighbor, state)) continue;
            if (solid == null) solid = direction; else { solid = null; break; }
        }
        if (solid != null) return opposite(solid);
        Direction facing = Direction.NORTH;
        if (solid(world, overlay, position.relative(facing))) facing = opposite(facing);
        if (solid(world, overlay, position.relative(facing))) facing = clockwise(facing);
        if (solid(world, overlay, position.relative(facing))) facing = opposite(facing);
        return facing;
    }
    private static boolean solid(WorldAccess world, Map<BlockPos, String> overlay, BlockPos pos) {
        String state = stateAt(world, overlay, pos);
        return world.isSolidRender(pos, state);
    }
    private static String stateAt(WorldAccess world, Map<BlockPos, String> overlay, BlockPos pos) {
        String state = overlay.get(pos);
        return state == null ? requireState(world.blockState(pos)) : state;
    }
    private static void put(List<BlockMutation> blocks, Map<BlockPos, String> overlay,
            BlockPos position, String state) {
        blocks.add(new BlockMutation(position, state)); overlay.put(position, state);
    }

    private static BlockPos worldPos(Mc263HardcodedStructureCarrier carrier,
            int x, int y, int z, int yDelta) {
        BoundingBox box = carrier.boundingBox();
        int worldX; int worldZ;
        switch (carrier.rotation()) {
            case NORTH -> { worldX = box.minX() + x; worldZ = box.maxZ() - z; }
            case SOUTH -> { worldX = box.minX() + x; worldZ = box.minZ() + z; }
            case WEST -> { worldX = box.maxX() - z; worldZ = box.minZ() + x; }
            case EAST -> { worldX = box.minX() + z; worldZ = box.minZ() + x; }
            default -> throw new IllegalArgumentException("jungle pyramid requires cardinal rotation");
        }
        return new BlockPos(worldX, Math.addExact(box.minY() + y, yDelta), worldZ);
    }
    private static BoundingBox shifted(BoundingBox box, int delta) {
        return new BoundingBox(box.minX(), Math.addExact(box.minY(), delta), box.minZ(),
                box.maxX(), Math.addExact(box.maxY(), delta), box.maxZ());
    }
    private static BoundingBox boxAtHeight(Mc263HardcodedStructureCarrier carrier, int height) {
        return shifted(carrier.boundingBox(), height - carrier.boundingBox().minY());
    }
    private static String blockKey(String state) {
        int property = state.indexOf('['); return property < 0 ? state : state.substring(0, property);
    }
    private static String requireState(String state) {
        Objects.requireNonNull(state, "exact state");
        if (!state.startsWith("minecraft:")) throw new IllegalArgumentException("noncanonical state");
        return state;
    }
    private static void require(boolean value, String capability) {
        if (!value) throw new UnsupportedOperationException(capability);
    }
    private static int orientationNbtId(Rotation rotation) {
        return switch (rotation) {
            case SOUTH -> 0; case WEST -> 1; case NORTH -> 2; case EAST -> 3;
            default -> throw new IllegalArgumentException("jungle pyramid requires cardinal orientation");
        };
    }
    private static void bool(DataOutputStream out, String name, boolean value) throws IOException {
        out.writeByte(1); out.writeUTF(name); out.writeByte(value ? 1 : 0);
    }
    private static void integer(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(3); out.writeUTF(name); out.writeInt(value);
    }
    private static void string(DataOutputStream out, String name, String value) throws IOException {
        out.writeByte(8); out.writeUTF(name); out.writeUTF(value);
    }
}
