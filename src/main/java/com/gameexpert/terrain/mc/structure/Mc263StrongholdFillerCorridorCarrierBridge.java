package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263StrongholdFillerCorridorPieceExecutor.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263StrongholdFillerCorridorPieceExecutor.ExecutionResult;
import com.gameexpert.terrain.mc.structure.Mc263StrongholdFillerCorridorPieceExecutor.Orientation;
import com.gameexpert.terrain.mc.structure.Mc263StrongholdFillerCorridorPieceExecutor.PieceFacts;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkReferences;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Dormant carrier bridge for exact pinned stronghold filler-corridor pieces only. */
public final class Mc263StrongholdFillerCorridorCarrierBridge {
    public static final String STRUCTURE = "minecraft:stronghold";
    private static final String ENTRY_DOOR = "OPENING";
    private static final int EXACT_NBT_BYTES = 108;

    private Mc263StrongholdFillerCorridorCarrierBridge() { }

    public record PieceResult(String startKey, int pieceIndex, ExecutionResult execution) { }

    public static List<PieceResult> execute(Mc263StructureCarrier carrier,
            ChunkReferences references, BoundingBox placementClip,
            Mc263StrongholdFillerCorridorPieceExecutor.WorldAccess world) {
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(references, "references");
        Objects.requireNonNull(placementClip, "placementClip");
        Objects.requireNonNull(world, "world");

        // Decode and transitively preflight every selected piece before the first world mutation.
        ArrayList<SelectedPiece> selected = new ArrayList<>();
        for (ValidStart start : carrier.resolveStarts(references)) {
            if (!start.startKey().startsWith(STRUCTURE + "@")) continue;
            for (int index = 0; index < start.orderedPieces().size(); index++) {
                Piece piece = start.orderedPieces().get(index);
                if (!Mc263StrongholdFillerCorridorPieceExecutor.PIECE_TYPE.equals(
                        piece.pieceType())) continue;
                PieceFacts facts = decodeFacts(piece);
                Mc263StrongholdFillerCorridorPieceExecutor.preflight(facts, world);
                selected.add(new SelectedPiece(start.startKey(), index, facts));
            }
        }

        ArrayList<PieceResult> results = new ArrayList<>();
        for (SelectedPiece selectedPiece : selected) {
            if (!selectedPiece.facts().boundingBox().intersects(placementClip)) continue;
            results.add(new PieceResult(selectedPiece.startKey(), selectedPiece.pieceIndex(),
                    Mc263StrongholdFillerCorridorPieceExecutor.execute(
                            selectedPiece.facts(), placementClip, world)));
        }
        return List.copyOf(results);
    }

    static PieceFacts decodeFacts(Piece piece) {
        if (piece.poolElement()
                || piece.projection() != Mc263StructureCarrier.Projection.NOT_APPLICABLE
                || piece.groundLevelDelta() != 0 || !piece.junctions().isEmpty()) {
            throw new IllegalArgumentException(
                    "expected exact non-pool minecraft:shfc piece");
        }
        byte[] payload = piece.persistedPayload().binaryNbtCompound();
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            require(payload.length == EXACT_NBT_BYTES);
            require(input.readUnsignedByte() == 10 && input.readUnsignedShort() == 0);
            requireTag(input, 11, "BB"); require(input.readInt() == 6);
            int[] coordinates = new int[6];
            for (int index = 0; index < coordinates.length; index++) {
                coordinates[index] = input.readInt();
            }
            requireTag(input, 3, "Steps"); int steps = input.readInt();
            requireTag(input, 8, "EntryDoor"); require(ENTRY_DOOR.equals(input.readUTF()));
            requireTag(input, 8, "id");
            require(Mc263StrongholdFillerCorridorPieceExecutor.PIECE_TYPE.equals(input.readUTF()));
            requireTag(input, 3, "GD"); int generationDepth = input.readInt();
            requireTag(input, 3, "O"); Orientation orientation = Orientation.fromNbtId(
                    input.readInt());
            require(input.readUnsignedByte() == 0 && input.available() == 0);

            Mc263StructureCarrier.BoundingBox box = piece.boundingBox();
            require(Arrays.equals(coordinates, new int[]{box.minX(), box.minY(), box.minZ(),
                    box.maxX(), box.maxY(), box.maxZ()}));
            require(Arrays.equals(payload, officialNbt(
                    box, steps, generationDepth, orientation)));
            return new PieceFacts(piece.pieceType(), new BoundingBox(
                    box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()),
                    generationDepth, orientation, steps);
        } catch (IOException | IllegalStateException exception) {
            throw new IllegalArgumentException(
                    "noncanonical minecraft:shfc persisted NBT", exception);
        }
    }

    static byte[] officialNbt(Mc263StructureCarrier.BoundingBox box, int steps,
            int generationDepth, Orientation orientation) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(EXACT_NBT_BYTES);
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(10); output.writeShort(0);
                output.writeByte(11); output.writeUTF("BB"); output.writeInt(6);
                output.writeInt(box.minX()); output.writeInt(box.minY());
                output.writeInt(box.minZ()); output.writeInt(box.maxX());
                output.writeInt(box.maxY()); output.writeInt(box.maxZ());
                output.writeByte(3); output.writeUTF("Steps"); output.writeInt(steps);
                output.writeByte(8); output.writeUTF("EntryDoor"); output.writeUTF(ENTRY_DOOR);
                output.writeByte(8); output.writeUTF("id");
                output.writeUTF(Mc263StrongholdFillerCorridorPieceExecutor.PIECE_TYPE);
                output.writeByte(3); output.writeUTF("GD"); output.writeInt(generationDepth);
                output.writeByte(3); output.writeUTF("O"); output.writeInt(orientation.nbtId());
                output.writeByte(0);
            }
            byte[] payload = bytes.toByteArray();
            if (payload.length != EXACT_NBT_BYTES) {
                throw new IllegalStateException("wrong stronghold-filler NBT size");
            }
            return payload;
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory stronghold-filler NBT failed", exception);
        }
    }

    private static void requireTag(DataInputStream input, int type, String name)
            throws IOException {
        require(input.readUnsignedByte() == type && name.equals(input.readUTF()));
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalStateException("noncanonical stronghold-filler NBT");
    }

    private record SelectedPiece(String startKey, int pieceIndex, PieceFacts facts) { }
}
