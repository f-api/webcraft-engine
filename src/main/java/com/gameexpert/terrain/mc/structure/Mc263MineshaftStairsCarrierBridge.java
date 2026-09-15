package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263MineshaftStairsPieceExecutor.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftStairsPieceExecutor.ExecutionResult;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftStairsPieceExecutor.MineshaftType;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftStairsPieceExecutor.Orientation;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftStairsPieceExecutor.PieceFacts;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkReferences;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Dormant carrier bridge for exact pinned mineshaft-stairs pieces. */
public final class Mc263MineshaftStairsCarrierBridge {
    public static final String NORMAL_STRUCTURE = "minecraft:mineshaft";
    public static final String MESA_STRUCTURE = "minecraft:mineshaft_mesa";

    private Mc263MineshaftStairsCarrierBridge() { }

    public record PieceResult(String startKey, int pieceIndex, ExecutionResult execution) { }

    public static List<PieceResult> execute(Mc263StructureCarrier carrier,
            ChunkReferences references, BoundingBox placementClip,
            Mc263MineshaftStairsPieceExecutor.WorldAccess world) {
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(references, "references");
        Objects.requireNonNull(placementClip, "placementClip");
        Objects.requireNonNull(world, "world");
        ArrayList<SelectedPiece> selected = new ArrayList<>();
        for (ValidStart start : carrier.resolveStarts(references)) {
            boolean normal = start.startKey().startsWith(NORMAL_STRUCTURE + "@");
            boolean mesa = start.startKey().startsWith(MESA_STRUCTURE + "@");
            if (!normal && !mesa) continue;
            for (int index = 0; index < start.orderedPieces().size(); index++) {
                Piece piece = start.orderedPieces().get(index);
                if (!Mc263MineshaftStairsPieceExecutor.PIECE_TYPE.equals(piece.pieceType())) continue;
                PieceFacts facts = decodeFacts(piece);
                Mc263MineshaftStairsPieceExecutor.preflight(facts, world);
                selected.add(new SelectedPiece(start.startKey(), index, facts));
            }
        }
        ArrayList<PieceResult> results = new ArrayList<>();
        for (SelectedPiece piece : selected) {
            if (!piece.facts.boundingBox().intersects(placementClip)) continue;
            results.add(new PieceResult(piece.startKey, piece.pieceIndex,
                    Mc263MineshaftStairsPieceExecutor.execute(
                            piece.facts, placementClip, world)));
        }
        return List.copyOf(results);
    }

    static PieceFacts decodeFacts(Piece piece) {
        if (piece.poolElement()
                || piece.projection() != Mc263StructureCarrier.Projection.NOT_APPLICABLE
                || piece.groundLevelDelta() != 0 || !piece.junctions().isEmpty()) {
            throw new IllegalArgumentException("expected exact non-pool minecraft:msstairs piece");
        }
        byte[] payload = piece.persistedPayload().binaryNbtCompound();
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            require(input.readUnsignedByte() == 10 && input.readUnsignedShort() == 0);
            requireTag(input, 11, "BB"); require(input.readInt() == 6);
            int[] coordinates = new int[6];
            for (int index = 0; index < coordinates.length; index++) coordinates[index] = input.readInt();
            requireTag(input, 8, "id"); require(Mc263MineshaftStairsPieceExecutor.PIECE_TYPE.equals(input.readUTF()));
            requireTag(input, 3, "GD"); int generationDepth = input.readInt();
            requireTag(input, 3, "O"); Orientation orientation = Orientation.fromNbtId(input.readInt());
            requireTag(input, 3, "MST"); MineshaftType type = MineshaftType.fromNbtId(input.readInt());
            require(input.readUnsignedByte() == 0 && input.available() == 0);
            Mc263StructureCarrier.BoundingBox box = piece.boundingBox();
            require(coordinates[0] == box.minX() && coordinates[1] == box.minY()
                    && coordinates[2] == box.minZ() && coordinates[3] == box.maxX()
                    && coordinates[4] == box.maxY() && coordinates[5] == box.maxZ());
            require(java.util.Arrays.equals(payload,
                    officialNbt(box, generationDepth, orientation, type)));
            return new PieceFacts(piece.pieceType(), new BoundingBox(box.minX(), box.minY(), box.minZ(),
                    box.maxX(), box.maxY(), box.maxZ()), generationDepth, orientation, type);
        } catch (IOException | IllegalStateException exception) {
            throw new IllegalArgumentException("noncanonical minecraft:msstairs persisted NBT", exception);
        }
    }

    static byte[] officialNbt(Mc263StructureCarrier.BoundingBox box, int generationDepth,
            Orientation orientation, MineshaftType type) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(89);
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(10); output.writeShort(0);
                output.writeByte(11); output.writeUTF("BB"); output.writeInt(6);
                output.writeInt(box.minX()); output.writeInt(box.minY()); output.writeInt(box.minZ());
                output.writeInt(box.maxX()); output.writeInt(box.maxY()); output.writeInt(box.maxZ());
                output.writeByte(8); output.writeUTF("id");
                output.writeUTF(Mc263MineshaftStairsPieceExecutor.PIECE_TYPE);
                output.writeByte(3); output.writeUTF("GD"); output.writeInt(generationDepth);
                output.writeByte(3); output.writeUTF("O"); output.writeInt(orientation.nbtId());
                output.writeByte(3); output.writeUTF("MST"); output.writeInt(type.nbtId());
                output.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory mineshaft-stairs NBT failed", exception);
        }
    }

    private static void requireTag(DataInputStream input, int type, String name) throws IOException {
        require(input.readUnsignedByte() == type && name.equals(input.readUTF()));
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalStateException("noncanonical mineshaft-stairs NBT");
    }

    private record SelectedPiece(String startKey, int pieceIndex, PieceFacts facts) { }
}
