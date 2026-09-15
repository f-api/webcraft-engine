package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263MineshaftRoomPieceExecutor.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftRoomPieceExecutor.ExecutionResult;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftRoomPieceExecutor.MineshaftType;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftRoomPieceExecutor.PieceFacts;
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

/** Dormant carrier bridge for exact pinned mineshaft root-room pieces. */
public final class Mc263MineshaftRoomCarrierBridge {
    public static final String NORMAL_STRUCTURE = "minecraft:mineshaft";
    public static final String MESA_STRUCTURE = "minecraft:mineshaft_mesa";

    private Mc263MineshaftRoomCarrierBridge() { }

    public record PieceResult(String startKey, int pieceIndex, ExecutionResult execution) { }

    public static List<PieceResult> execute(Mc263StructureCarrier carrier,
            ChunkReferences references, BoundingBox placementClip,
            Mc263MineshaftRoomPieceExecutor.WorldAccess world) {
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(references, "references");
        Objects.requireNonNull(placementClip, "placementClip");
        Objects.requireNonNull(world, "world");
        ArrayList<SelectedPiece> selected = new ArrayList<>();
        for (ValidStart start : carrier.resolveStarts(references)) {
            MineshaftType expectedType = startType(start.startKey());
            if (expectedType == null) continue;
            int roomCount = 0;
            PieceFacts facts = null;
            for (int index = 0; index < start.orderedPieces().size(); index++) {
                Piece piece = start.orderedPieces().get(index);
                if (!Mc263MineshaftRoomPieceExecutor.PIECE_TYPE.equals(piece.pieceType())) continue;
                roomCount++;
                if (index != 0) {
                    throw new IllegalArgumentException("mineshaft room is not ordered root piece");
                }
                facts = decodeFacts(piece);
            }
            if (roomCount != 1 || facts == null) {
                throw new IllegalArgumentException("mineshaft start must contain one root room");
            }
            if (facts.mineshaftType() != expectedType) {
                throw new IllegalArgumentException("mineshaft room MST disagrees with start kind");
            }
            Mc263MineshaftRoomPieceExecutor.preflight(facts, world);
            selected.add(new SelectedPiece(start.startKey(), facts));
        }
        ArrayList<PieceResult> results = new ArrayList<>();
        for (SelectedPiece piece : selected) {
            if (!piece.facts.boundingBox().intersects(placementClip)) continue;
            results.add(new PieceResult(piece.startKey, 0,
                    Mc263MineshaftRoomPieceExecutor.execute(
                            piece.facts, placementClip, world)));
        }
        return List.copyOf(results);
    }

    static PieceFacts decodeFacts(Piece piece) {
        if (piece.poolElement()
                || piece.projection() != Mc263StructureCarrier.Projection.NOT_APPLICABLE
                || piece.groundLevelDelta() != 0 || !piece.junctions().isEmpty()) {
            throw new IllegalArgumentException("expected exact non-pool minecraft:msroom piece");
        }
        byte[] payload = piece.persistedPayload().binaryNbtCompound();
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            require(input.readUnsignedByte() == 10 && input.readUnsignedShort() == 0);
            requireTag(input, 11, "BB"); require(input.readInt() == 6);
            int[] coordinates = readBox(input);
            requireTag(input, 9, "Entrances"); require(input.readUnsignedByte() == 11);
            int entranceCount = input.readInt();
            require(entranceCount >= 0 && entranceCount <= input.available() / 28);
            ArrayList<BoundingBox> entrances = new ArrayList<>(entranceCount);
            for (int index = 0; index < entranceCount; index++) {
                require(input.readInt() == 6);
                int[] entrance = readBox(input);
                entrances.add(new BoundingBox(entrance[0], entrance[1], entrance[2],
                        entrance[3], entrance[4], entrance[5]));
            }
            requireTag(input, 8, "id");
            require(Mc263MineshaftRoomPieceExecutor.PIECE_TYPE.equals(input.readUTF()));
            requireTag(input, 3, "GD"); int generationDepth = input.readInt();
            requireTag(input, 3, "O"); int orientation = input.readInt();
            requireTag(input, 3, "MST"); MineshaftType type = MineshaftType.fromNbtId(input.readInt());
            require(input.readUnsignedByte() == 0 && input.available() == 0);
            Mc263StructureCarrier.BoundingBox box = piece.boundingBox();
            require(Arrays.equals(coordinates, new int[]{box.minX(), box.minY(), box.minZ(),
                    box.maxX(), box.maxY(), box.maxZ()}));
            require(Arrays.equals(payload, officialNbt(box, entrances,
                    generationDepth, orientation, type)));
            return new PieceFacts(piece.pieceType(), new BoundingBox(box.minX(), box.minY(),
                    box.minZ(), box.maxX(), box.maxY(), box.maxZ()), generationDepth,
                    orientation, type, entrances);
        } catch (IOException | IllegalStateException exception) {
            throw new IllegalArgumentException("noncanonical minecraft:msroom persisted NBT", exception);
        }
    }

    static byte[] officialNbt(Mc263StructureCarrier.BoundingBox box,
            List<BoundingBox> entrances, int generationDepth, int orientation,
            MineshaftType type) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(10); output.writeShort(0);
                output.writeByte(11); output.writeUTF("BB"); writeBox(output, box);
                output.writeByte(9); output.writeUTF("Entrances"); output.writeByte(11);
                output.writeInt(entrances.size());
                for (BoundingBox entrance : entrances) writeBox(output, entrance);
                output.writeByte(8); output.writeUTF("id");
                output.writeUTF(Mc263MineshaftRoomPieceExecutor.PIECE_TYPE);
                output.writeByte(3); output.writeUTF("GD"); output.writeInt(generationDepth);
                output.writeByte(3); output.writeUTF("O"); output.writeInt(orientation);
                output.writeByte(3); output.writeUTF("MST"); output.writeInt(type.nbtId());
                output.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory mineshaft-room NBT failed", exception);
        }
    }

    private static MineshaftType startType(String startKey) {
        if (startKey.startsWith(NORMAL_STRUCTURE + "@")) return MineshaftType.NORMAL;
        if (startKey.startsWith(MESA_STRUCTURE + "@")) return MineshaftType.MESA;
        return null;
    }

    private static int[] readBox(DataInputStream input) throws IOException {
        int[] box = new int[6];
        for (int index = 0; index < box.length; index++) box[index] = input.readInt();
        return box;
    }

    private static void writeBox(DataOutputStream output,
            Mc263StructureCarrier.BoundingBox box) throws IOException {
        output.writeInt(6);
        output.writeInt(box.minX()); output.writeInt(box.minY()); output.writeInt(box.minZ());
        output.writeInt(box.maxX()); output.writeInt(box.maxY()); output.writeInt(box.maxZ());
    }

    private static void writeBox(DataOutputStream output, BoundingBox box) throws IOException {
        output.writeInt(6);
        output.writeInt(box.minX()); output.writeInt(box.minY()); output.writeInt(box.minZ());
        output.writeInt(box.maxX()); output.writeInt(box.maxY()); output.writeInt(box.maxZ());
    }

    private static void requireTag(DataInputStream input, int type, String name) throws IOException {
        require(input.readUnsignedByte() == type && name.equals(input.readUTF()));
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalStateException("noncanonical mineshaft-room NBT");
    }

    private record SelectedPiece(String startKey, PieceFacts facts) { }
}
