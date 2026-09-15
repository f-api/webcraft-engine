package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263StrongholdRightTurnPieceExecutor.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263StrongholdRightTurnPieceExecutor.EntryDoor;
import com.gameexpert.terrain.mc.structure.Mc263StrongholdRightTurnPieceExecutor.ExecutionResult;
import com.gameexpert.terrain.mc.structure.Mc263StrongholdRightTurnPieceExecutor.Orientation;
import com.gameexpert.terrain.mc.structure.Mc263StrongholdRightTurnPieceExecutor.PieceFacts;
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

/** Non-encodable dormant carrier bridge for exact pinned stronghold right-turn pieces. */
public final class Mc263StrongholdRightTurnCarrierBridge {
    public static final String STRUCTURE = "minecraft:stronghold";

    private Mc263StrongholdRightTurnCarrierBridge() { }

    public record PieceResult(String startKey, int pieceIndex, ExecutionResult execution) { }

    public static List<PieceResult> execute(Mc263StructureCarrier carrier,
            ChunkReferences references, BoundingBox placementClip,
            Mc263StrongholdRightTurnPieceExecutor.RandomSource random,
            Mc263StrongholdRightTurnPieceExecutor.WorldAccess world) {
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(references, "references");
        Objects.requireNonNull(placementClip, "placementClip");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(world, "world");
        ArrayList<SelectedPiece> selected = new ArrayList<>();
        for (ValidStart start : carrier.resolveStarts(references)) {
            if (!start.startKey().startsWith(STRUCTURE + "@")) continue;
            for (int index = 0; index < start.orderedPieces().size(); index++) {
                Piece piece = start.orderedPieces().get(index);
                if (!Mc263StrongholdRightTurnPieceExecutor.PIECE_TYPE.equals(piece.pieceType())) {
                    continue;
                }
                PieceFacts facts = decodeFacts(piece);
                Mc263StrongholdRightTurnPieceExecutor.preflight(facts, world);
                selected.add(new SelectedPiece(start.startKey(), index, facts));
            }
        }
        ArrayList<PieceResult> results = new ArrayList<>();
        for (SelectedPiece piece : selected) {
            if (!piece.facts().boundingBox().intersects(placementClip)) continue;
            results.add(new PieceResult(piece.startKey(), piece.index(),
                    Mc263StrongholdRightTurnPieceExecutor.execute(
                            piece.facts(), placementClip, random, world)));
        }
        return List.copyOf(results);
    }

    static PieceFacts decodeFacts(Piece piece) {
        if (piece.poolElement()
                || piece.projection() != Mc263StructureCarrier.Projection.NOT_APPLICABLE
                || piece.groundLevelDelta() != 0 || !piece.junctions().isEmpty()) {
            throw new IllegalArgumentException("expected exact non-pool minecraft:shrt piece");
        }
        byte[] payload = piece.persistedPayload().binaryNbtCompound();
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            require(input.readUnsignedByte() == 10 && input.readUnsignedShort() == 0);
            requireTag(input, 11, "BB"); require(input.readInt() == 6);
            int[] coordinates = new int[6];
            for (int index = 0; index < 6; index++) coordinates[index] = input.readInt();
            requireTag(input, 8, "EntryDoor"); EntryDoor door = EntryDoor.decode(input.readUTF());
            requireTag(input, 8, "id");
            require(Mc263StrongholdRightTurnPieceExecutor.PIECE_TYPE.equals(input.readUTF()));
            requireTag(input, 3, "GD"); int depth = input.readInt();
            requireTag(input, 3, "O"); Orientation orientation = Orientation.fromNbtId(
                    input.readInt());
            require(input.readUnsignedByte() == 0 && input.available() == 0);
            Mc263StructureCarrier.BoundingBox box = piece.boundingBox();
            require(Arrays.equals(coordinates, new int[]{box.minX(), box.minY(), box.minZ(),
                    box.maxX(), box.maxY(), box.maxZ()}));
            require(Arrays.equals(payload, officialNbt(box, door, depth, orientation)));
            return new PieceFacts(piece.pieceType(), new BoundingBox(box.minX(), box.minY(),
                    box.minZ(), box.maxX(), box.maxY(), box.maxZ()), depth, orientation, door);
        } catch (IOException | IllegalStateException exception) {
            throw new IllegalArgumentException("noncanonical minecraft:shrt persisted NBT",
                    exception);
        }
    }

    static byte[] officialNbt(Mc263StructureCarrier.BoundingBox box, EntryDoor door,
            int depth, Orientation orientation) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(100);
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(10); output.writeShort(0);
                output.writeByte(11); output.writeUTF("BB"); output.writeInt(6);
                output.writeInt(box.minX()); output.writeInt(box.minY());
                output.writeInt(box.minZ()); output.writeInt(box.maxX());
                output.writeInt(box.maxY()); output.writeInt(box.maxZ());
                output.writeByte(8); output.writeUTF("EntryDoor"); output.writeUTF(door.name());
                output.writeByte(8); output.writeUTF("id");
                output.writeUTF(Mc263StrongholdRightTurnPieceExecutor.PIECE_TYPE);
                output.writeByte(3); output.writeUTF("GD"); output.writeInt(depth);
                output.writeByte(3); output.writeUTF("O"); output.writeInt(orientation.nbtId());
                output.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory stronghold-right-turn NBT failed", exception);
        }
    }

    private static void requireTag(DataInputStream input, int type, String name)
            throws IOException {
        require(input.readUnsignedByte() == type && name.equals(input.readUTF()));
    }
    private static void require(boolean condition) {
        if (!condition) throw new IllegalStateException("noncanonical stronghold-right-turn NBT");
    }
    private record SelectedPiece(String startKey, int index, PieceFacts facts) { }
}
