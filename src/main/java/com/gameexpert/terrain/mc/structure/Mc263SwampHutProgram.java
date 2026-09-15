package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Kind;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.PieceKind;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureExecutor.Command;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureExecutor.ExactStates;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureExecutor.FillColumnDown;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureExecutor.PieceProgram;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureExecutor.PlaceBlock;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureExecutor.Program;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureExecutor.Sidecar;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureExecutor.SidecarKind;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * Exact ordered procedural transcription of the pinned 26.3 swamp-hut piece.
 *
 * <p>The command stream follows {@code SwampHutPiece.postProcess} and the inherited
 * {@code StructurePiece.generateBox} Y/X/Z iteration order. It is repository-owned code, not a
 * copied structure template. Duplicate corner-stair writes and the four live foundation loops are
 * retained because they are observable parts of the official mutation order.</p>
 */
public final class Mc263SwampHutProgram {
    public static final String VERSION = Mc263HardcodedStructureExecutor.VERSION;
    public static final String SERVER_SHA1 = Mc263HardcodedStructureExecutor.SERVER_SHA1;
    public static final String STRUCTURE_ID = "minecraft:swamp_hut";
    public static final String PIECE_TYPE = "minecraft:tesh";
    public static final int COMMAND_COUNT = 156;

    /*
     * Canonical authority instruction carried by ENTS. Entity type, position, rotation and spawn
     * reason are type-tagged by Sidecar itself; these bytes require persistent mob state and the
     * pinned STRUCTURE finalize-spawn path at durable installation.
     */
    private static final byte[] ENTITY_PAYLOAD =
            "SHW263E1\u0001\u0001".getBytes(StandardCharsets.ISO_8859_1);
    private static final Program PROGRAM = build();

    private Mc263SwampHutProgram() { }

    public static Program program() { return PROGRAM; }

    public static byte[] canonicalEntityPayload() { return ENTITY_PAYLOAD.clone(); }

    /** Exact pinned binary-NBT value for the mutable swamp-hut piece successor. */
    public static byte[] canonicalPieceNbt(Mc263HardcodedStructureCarrier carrier) {
        if (carrier.kind() != Kind.SWAMP_HUT
                || carrier.orderedPieces().size() != 1
                || carrier.orderedPieces().getFirst().kind() != PieceKind.SWAMP_HUT) {
            throw new IllegalArgumentException("noncanonical swamp-hut carrier shape");
        }
        var successor = carrier.successor();
        int height = successor.heightPosition();
        int yDelta = height < 0 ? 0
                : Math.subtractExact(height, carrier.boundingBox().minY());
        var box = carrier.boundingBox();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(160);
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(10); output.writeShort(0);
                output.writeByte(11); output.writeUTF("BB"); output.writeInt(6);
                output.writeInt(box.minX()); output.writeInt(Math.addExact(box.minY(), yDelta));
                output.writeInt(box.minZ()); output.writeInt(box.maxX());
                output.writeInt(Math.addExact(box.maxY(), yDelta)); output.writeInt(box.maxZ());
                // CompoundTag's pinned HashMap encounter order after createTag/additional data.
                output.writeByte(1); output.writeUTF("Cat");
                output.writeByte(successor.flag("Cat") ? 1 : 0);
                output.writeByte(3); output.writeUTF("Height"); output.writeInt(7);
                output.writeByte(8); output.writeUTF("id"); output.writeUTF(PIECE_TYPE);
                output.writeByte(1); output.writeUTF("Witch");
                output.writeByte(successor.flag("Witch") ? 1 : 0);
                output.writeByte(3); output.writeUTF("GD"); output.writeInt(0);
                output.writeByte(3); output.writeUTF("Width"); output.writeInt(7);
                output.writeByte(3); output.writeUTF("HPos"); output.writeInt(height);
                output.writeByte(3); output.writeUTF("Depth"); output.writeInt(9);
                output.writeByte(3); output.writeUTF("O");
                output.writeInt(orientationNbtId(carrier.rotation()));
                output.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory swamp-hut NBT failed", exception);
        }
    }

    private static int orientationNbtId(Rotation rotation) {
        return switch (rotation) {
            case SOUTH -> 0;
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> throw new IllegalArgumentException(
                    "swamp hut requires cardinal orientation");
        };
    }

    private static Program build() {
        ArrayList<Command> commands = new ArrayList<>(COMMAND_COUNT);
        ExactStates planks = ExactStates.fixed("minecraft:spruce_planks");
        ExactStates log = ExactStates.fixed("minecraft:oak_log[axis=y]");
        ExactStates fence = ExactStates.fixed(
                "minecraft:oak_fence[east=false,north=false,south=false,waterlogged=false,west=false]");

        box(commands, 1, 1, 1, 5, 1, 7, planks);
        box(commands, 1, 4, 2, 5, 4, 7, planks);
        box(commands, 2, 1, 0, 4, 1, 0, planks);
        box(commands, 2, 2, 2, 3, 3, 2, planks);
        box(commands, 1, 2, 3, 1, 3, 6, planks);
        box(commands, 5, 2, 3, 5, 3, 6, planks);
        box(commands, 2, 2, 7, 4, 3, 7, planks);
        box(commands, 1, 0, 2, 1, 3, 2, log);
        box(commands, 5, 0, 2, 5, 3, 2, log);
        box(commands, 1, 0, 7, 1, 3, 7, log);
        box(commands, 5, 0, 7, 5, 3, 7, log);

        place(commands, 2, 3, 2, fence);
        place(commands, 3, 3, 7, fence);
        place(commands, 1, 3, 4, "minecraft:air");
        place(commands, 5, 3, 4, "minecraft:air");
        place(commands, 5, 3, 5, "minecraft:air");
        place(commands, 1, 3, 5, "minecraft:potted_red_mushroom");
        place(commands, 3, 2, 6, "minecraft:crafting_table");
        place(commands, 4, 2, 6, "minecraft:cauldron");
        place(commands, 1, 2, 1, fence);
        place(commands, 5, 2, 1, fence);

        ExactStates north = stair("north", "straight");
        ExactStates east = stair("east", "straight");
        ExactStates west = stair("west", "straight");
        ExactStates south = stair("south", "straight");
        box(commands, 0, 4, 1, 6, 4, 1, north);
        box(commands, 0, 4, 2, 0, 4, 7, east);
        box(commands, 6, 4, 2, 6, 4, 7, west);
        box(commands, 0, 4, 8, 6, 4, 8, south);
        place(commands, 0, 4, 1, stair("north", "outer_right"));
        place(commands, 6, 4, 1, stair("north", "outer_left"));
        place(commands, 0, 4, 8, stair("south", "outer_left"));
        place(commands, 6, 4, 8, stair("south", "outer_right"));

        for (int z = 2; z <= 7; z += 5) {
            for (int x = 1; x <= 5; x += 4) {
                commands.add(new FillColumnDown(x, -1, z, log));
            }
        }
        commands.add(new Sidecar(2, 2, 5, SidecarKind.ENTITY, "minecraft:witch",
                "Witch", true, ENTITY_PAYLOAD));
        commands.add(new Sidecar(2, 2, 5, SidecarKind.ENTITY, "minecraft:cat",
                "Cat", true, ENTITY_PAYLOAD));

        if (commands.size() != COMMAND_COUNT) {
            throw new IllegalStateException("incomplete swamp-hut command stream: "
                    + commands.size() + "/" + COMMAND_COUNT);
        }
        return new Program(SERVER_SHA1, Kind.SWAMP_HUT,
                List.of(new PieceProgram(PieceKind.SWAMP_HUT, null, commands)));
    }

    private static void box(ArrayList<Command> commands, int x0, int y0, int z0,
            int x1, int y1, int z1, ExactStates state) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    commands.add(new PlaceBlock(x, y, z, state));
                }
            }
        }
    }

    private static void place(ArrayList<Command> commands,
            int x, int y, int z, String state) {
        place(commands, x, y, z, ExactStates.fixed(state));
    }

    private static void place(ArrayList<Command> commands,
            int x, int y, int z, ExactStates state) {
        commands.add(new PlaceBlock(x, y, z, state));
    }

    /** Applies the exact StructurePiece mirror-then-rotation transform to stair facing and shape. */
    private static ExactStates stair(String sourceFacing, String sourceShape) {
        EnumMap<Rotation, String> states = new EnumMap<>(Rotation.class);
        for (Rotation orientation : List.of(
                Rotation.NORTH, Rotation.EAST, Rotation.SOUTH, Rotation.WEST)) {
            String facing = rotateFacing(sourceFacing, orientation);
            String shape = mirrorShape(sourceShape, orientation);
            states.put(orientation, "minecraft:spruce_stairs[facing=" + facing
                    + ",half=bottom,shape=" + shape + ",waterlogged=false]");
        }
        return ExactStates.of(states);
    }

    private static String rotateFacing(String facing, Rotation orientation) {
        String mirrored = (orientation == Rotation.SOUTH || orientation == Rotation.WEST)
                && (facing.equals("north") || facing.equals("south"))
                ? opposite(facing) : facing;
        return orientation == Rotation.WEST || orientation == Rotation.EAST
                ? clockwise(mirrored) : mirrored;
    }

    private static String mirrorShape(String shape, Rotation orientation) {
        if (orientation != Rotation.SOUTH && orientation != Rotation.WEST) return shape;
        return switch (shape) {
            case "outer_left" -> "outer_right";
            case "outer_right" -> "outer_left";
            default -> shape;
        };
    }

    private static String clockwise(String facing) {
        return switch (facing) {
            case "north" -> "east";
            case "east" -> "south";
            case "south" -> "west";
            case "west" -> "north";
            default -> throw new IllegalArgumentException("non-horizontal facing: " + facing);
        };
    }

    private static String opposite(String facing) {
        return switch (facing) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            case "west" -> "east";
            default -> throw new IllegalArgumentException("non-horizontal facing: " + facing);
        };
    }
}
