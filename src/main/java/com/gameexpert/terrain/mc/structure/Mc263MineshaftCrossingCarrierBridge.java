package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263MineshaftCrossingPieceExecutor.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftCrossingPieceExecutor.Direction;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftCrossingPieceExecutor.ExecutionResult;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftCrossingPieceExecutor.MineshaftType;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftCrossingPieceExecutor.PieceFacts;
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

/** Dormant carrier bridge for exact pinned mineshaft-crossing pieces. */
public final class Mc263MineshaftCrossingCarrierBridge {
    public static final String NORMAL_STRUCTURE = "minecraft:mineshaft";
    public static final String MESA_STRUCTURE = "minecraft:mineshaft_mesa";
    private static final int EXACT_NBT_BYTES = 102;

    private Mc263MineshaftCrossingCarrierBridge() { }

    public record PieceResult(String startKey, int pieceIndex, ExecutionResult execution) { }

    public static List<PieceResult> execute(Mc263StructureCarrier carrier,
            ChunkReferences references, BoundingBox placementClip,
            Mc263MineshaftCrossingPieceExecutor.WorldAccess world) {
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(references, "references");
        Objects.requireNonNull(placementClip, "placementClip");
        Objects.requireNonNull(world, "world");
        ArrayList<SelectedPiece> selected = new ArrayList<>();
        for (ValidStart start : carrier.resolveStarts(references)) {
            MineshaftType expectedType = startType(start.startKey());
            if (expectedType == null) continue;
            for (int index = 0; index < start.orderedPieces().size(); index++) {
                Piece piece = start.orderedPieces().get(index);
                if (!Mc263MineshaftCrossingPieceExecutor.PIECE_TYPE.equals(
                        piece.pieceType())) continue;
                PieceFacts facts = decodeFacts(piece);
                if (facts.mineshaftType() != expectedType) {
                    throw new IllegalArgumentException(
                            "mineshaft crossing MST disagrees with start kind");
                }
                Mc263MineshaftCrossingPieceExecutor.preflight(facts, world);
                selected.add(new SelectedPiece(start.startKey(), index, facts));
            }
        }
        ArrayList<PieceResult> results = new ArrayList<>();
        for (SelectedPiece piece : selected) {
            if (!piece.facts.boundingBox().intersects(placementClip)) continue;
            results.add(new PieceResult(piece.startKey, piece.pieceIndex,
                    Mc263MineshaftCrossingPieceExecutor.execute(
                            piece.facts, placementClip, world)));
        }
        return List.copyOf(results);
    }

    static PieceFacts decodeFacts(Piece piece) {
        if (piece.poolElement()
                || piece.projection() != Mc263StructureCarrier.Projection.NOT_APPLICABLE
                || piece.groundLevelDelta() != 0 || !piece.junctions().isEmpty()) {
            throw new IllegalArgumentException(
                    "expected exact non-pool minecraft:mscrossing piece");
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
            requireTag(input, 1, "tf"); int twoFlooredByte = input.readUnsignedByte();
            require(twoFlooredByte == 0 || twoFlooredByte == 1);
            requireTag(input, 1, "D"); Direction direction = Direction.fromNbtId(
                    input.readUnsignedByte());
            requireTag(input, 8, "id");
            require(Mc263MineshaftCrossingPieceExecutor.PIECE_TYPE.equals(input.readUTF()));
            requireTag(input, 3, "GD"); int generationDepth = input.readInt();
            requireTag(input, 3, "O"); int orientation = input.readInt();
            requireTag(input, 3, "MST"); MineshaftType type = MineshaftType.fromNbtId(
                    input.readInt());
            require(input.readUnsignedByte() == 0 && input.available() == 0);
            Mc263StructureCarrier.BoundingBox box = piece.boundingBox();
            require(Arrays.equals(coordinates, new int[]{box.minX(), box.minY(), box.minZ(),
                    box.maxX(), box.maxY(), box.maxZ()}));
            require(Arrays.equals(payload, officialNbt(box, twoFlooredByte != 0,
                    direction, generationDepth, orientation, type)));
            return new PieceFacts(piece.pieceType(), new BoundingBox(box.minX(), box.minY(),
                    box.minZ(), box.maxX(), box.maxY(), box.maxZ()), generationDepth,
                    orientation, twoFlooredByte != 0, direction, type);
        } catch (IOException | IllegalStateException exception) {
            throw new IllegalArgumentException(
                    "noncanonical minecraft:mscrossing persisted NBT", exception);
        }
    }

    static byte[] officialNbt(Mc263StructureCarrier.BoundingBox box, boolean twoFloored,
            Direction direction, int generationDepth, int orientation, MineshaftType type) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(EXACT_NBT_BYTES);
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(10); output.writeShort(0);
                output.writeByte(11); output.writeUTF("BB"); output.writeInt(6);
                output.writeInt(box.minX()); output.writeInt(box.minY());
                output.writeInt(box.minZ()); output.writeInt(box.maxX());
                output.writeInt(box.maxY()); output.writeInt(box.maxZ());
                output.writeByte(1); output.writeUTF("tf");
                output.writeByte(twoFloored ? 1 : 0);
                output.writeByte(1); output.writeUTF("D"); output.writeByte(direction.nbtId());
                output.writeByte(8); output.writeUTF("id");
                output.writeUTF(Mc263MineshaftCrossingPieceExecutor.PIECE_TYPE);
                output.writeByte(3); output.writeUTF("GD"); output.writeInt(generationDepth);
                output.writeByte(3); output.writeUTF("O"); output.writeInt(orientation);
                output.writeByte(3); output.writeUTF("MST"); output.writeInt(type.nbtId());
                output.writeByte(0);
            }
            byte[] payload = bytes.toByteArray();
            if (payload.length != EXACT_NBT_BYTES) {
                throw new IllegalStateException("wrong mineshaft-crossing NBT size");
            }
            return payload;
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory mineshaft-crossing NBT failed", exception);
        }
    }

    private static MineshaftType startType(String startKey) {
        int separator = startKey.indexOf('@');
        if (separator < 0) return null;
        String structure = startKey.substring(0, separator);
        if (NORMAL_STRUCTURE.equals(structure)) return MineshaftType.NORMAL;
        if (MESA_STRUCTURE.equals(structure)) return MineshaftType.MESA;
        return null;
    }

    private static void requireTag(DataInputStream input, int type, String name)
            throws IOException {
        require(input.readUnsignedByte() == type && name.equals(input.readUTF()));
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalStateException("noncanonical mineshaft-crossing NBT");
    }

    private record SelectedPiece(String startKey, int pieceIndex, PieceFacts facts) { }
}
