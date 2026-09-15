package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263MineshaftCorridorPieceExecutor.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftCorridorPieceExecutor.Direction;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftCorridorPieceExecutor.ExecutionResult;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftCorridorPieceExecutor.MineshaftType;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftCorridorPieceExecutor.PieceFacts;
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

/** Dormant immutable carrier evidence bridge for exact pinned mineshaft corridors. */
public final class Mc263MineshaftCorridorCarrierBridge {
    public static final String NORMAL_STRUCTURE = "minecraft:mineshaft";
    public static final String MESA_STRUCTURE = "minecraft:mineshaft_mesa";
    private static final int EXACT_NBT_BYTES = 120;

    private Mc263MineshaftCorridorCarrierBridge() { }

    public static final class ReplacementPayload {
        private final byte[] bytes;
        private ReplacementPayload(byte[] bytes) { this.bytes = bytes.clone(); }
        public byte[] binaryNbtCompound() { return bytes.clone(); }
        @Override public boolean equals(Object other) {
            return other instanceof ReplacementPayload payload && Arrays.equals(bytes, payload.bytes);
        }
        @Override public int hashCode() { return Arrays.hashCode(bytes); }
    }

    public record PieceEvidence(String startKey, int pieceIndex, ExecutionResult execution,
                                ReplacementPayload replacementPayload) { }

    public static List<PieceEvidence> execute(Mc263StructureCarrier carrier,
            ChunkReferences references, BoundingBox placementClip,
            Mc263MineshaftCorridorPieceExecutor.WorldAccess world,
            Mc263MineshaftCorridorPieceExecutor.StructureRandom random) {
        Objects.requireNonNull(carrier, "carrier"); Objects.requireNonNull(references, "references");
        Objects.requireNonNull(placementClip, "placementClip"); Objects.requireNonNull(world, "world");
        Objects.requireNonNull(random, "random");
        ArrayList<Selected> selected = new ArrayList<>();
        for (ValidStart start : carrier.resolveStarts(references)) {
            MineshaftType expected = startType(start.startKey()); if (expected == null) continue;
            for (int index = 0; index < start.orderedPieces().size(); index++) {
                Piece piece = start.orderedPieces().get(index);
                if (!Mc263MineshaftCorridorPieceExecutor.PIECE_TYPE.equals(piece.pieceType())) continue;
                PieceFacts facts = decodeFacts(piece);
                if (facts.mineshaftType() != expected) {
                    throw new IllegalArgumentException("mineshaft corridor MST disagrees with start kind");
                }
                Mc263MineshaftCorridorPieceExecutor.preflight(facts, world);
                selected.add(new Selected(start.startKey(), index, facts));
            }
        }
        ArrayList<PieceEvidence> evidence = new ArrayList<>();
        for (Selected value : selected) {
            if (!value.facts.boundingBox().intersects(placementClip)) continue;
            ExecutionResult result = Mc263MineshaftCorridorPieceExecutor.execute(
                    value.facts, placementClip, world, random);
            ReplacementPayload replacement = result.finalHasPlacedSpider()
                    != value.facts.hasPlacedSpider()
                    ? new ReplacementPayload(officialNbt(value.facts, result.finalHasPlacedSpider()))
                    : null;
            evidence.add(new PieceEvidence(value.startKey, value.pieceIndex, result, replacement));
        }
        return List.copyOf(evidence);
    }

    public static PieceFacts decodeFacts(Piece piece) {
        if (piece.poolElement() || piece.projection() != Mc263StructureCarrier.Projection.NOT_APPLICABLE
                || piece.groundLevelDelta() != 0 || !piece.junctions().isEmpty()) {
            throw new IllegalArgumentException("expected exact non-pool minecraft:mscorridor piece");
        }
        byte[] payload = piece.persistedPayload().binaryNbtCompound();
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            require(payload.length == EXACT_NBT_BYTES);
            require(input.readUnsignedByte() == 10 && input.readUnsignedShort() == 0);
            requireTag(input, 11, "BB"); require(input.readInt() == 6);
            int[] coordinates = new int[6]; for (int i = 0; i < 6; i++) coordinates[i] = input.readInt();
            requireTag(input, 1, "sc"); int sc = input.readUnsignedByte(); require(sc <= 1);
            requireTag(input, 3, "Num"); int num = input.readInt();
            requireTag(input, 1, "hr"); int hr = input.readUnsignedByte(); require(hr <= 1);
            requireTag(input, 1, "hps"); int hps = input.readUnsignedByte(); require(hps <= 1);
            requireTag(input, 8, "id");
            require(Mc263MineshaftCorridorPieceExecutor.PIECE_TYPE.equals(input.readUTF()));
            requireTag(input, 3, "GD"); int depth = input.readInt();
            requireTag(input, 3, "O"); Direction orientation = Direction.fromNbtId(input.readInt());
            requireTag(input, 3, "MST"); MineshaftType type = MineshaftType.fromNbtId(input.readInt());
            require(input.readUnsignedByte() == 0 && input.available() == 0);
            Mc263StructureCarrier.BoundingBox b = piece.boundingBox();
            require(Arrays.equals(coordinates, new int[]{b.minX(), b.minY(), b.minZ(),
                    b.maxX(), b.maxY(), b.maxZ()}));
            PieceFacts facts = new PieceFacts(piece.pieceType(), new BoundingBox(b.minX(), b.minY(),
                    b.minZ(), b.maxX(), b.maxY(), b.maxZ()), depth, orientation, type, num,
                    hr != 0, sc != 0, hps != 0);
            require(Arrays.equals(payload, officialNbt(facts, facts.hasPlacedSpider())));
            return facts;
        } catch (IOException | IllegalStateException exception) {
            throw new IllegalArgumentException("noncanonical minecraft:mscorridor persisted NBT", exception);
        }
    }

    public static byte[] officialNbt(PieceFacts facts, boolean hasPlacedSpider) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(EXACT_NBT_BYTES);
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                BoundingBox b = facts.boundingBox();
                out.writeByte(10); out.writeShort(0);
                out.writeByte(11); out.writeUTF("BB"); out.writeInt(6);
                out.writeInt(b.minX()); out.writeInt(b.minY()); out.writeInt(b.minZ());
                out.writeInt(b.maxX()); out.writeInt(b.maxY()); out.writeInt(b.maxZ());
                out.writeByte(1); out.writeUTF("sc"); out.writeByte(facts.spiderCorridor() ? 1 : 0);
                out.writeByte(3); out.writeUTF("Num"); out.writeInt(facts.numSections());
                out.writeByte(1); out.writeUTF("hr"); out.writeByte(facts.hasRails() ? 1 : 0);
                out.writeByte(1); out.writeUTF("hps"); out.writeByte(hasPlacedSpider ? 1 : 0);
                out.writeByte(8); out.writeUTF("id");
                out.writeUTF(Mc263MineshaftCorridorPieceExecutor.PIECE_TYPE);
                out.writeByte(3); out.writeUTF("GD"); out.writeInt(facts.generationDepth());
                out.writeByte(3); out.writeUTF("O"); out.writeInt(facts.orientation().nbtId());
                out.writeByte(3); out.writeUTF("MST"); out.writeInt(facts.mineshaftType().nbtId());
                out.writeByte(0);
            }
            byte[] payload = bytes.toByteArray(); require(payload.length == EXACT_NBT_BYTES); return payload;
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory mineshaft-corridor NBT failed", exception);
        }
    }

    private static MineshaftType startType(String key) {
        int separator = key.indexOf('@'); if (separator < 0) return null;
        return switch (key.substring(0, separator)) {
            case NORMAL_STRUCTURE -> MineshaftType.NORMAL;
            case MESA_STRUCTURE -> MineshaftType.MESA;
            default -> null;
        };
    }
    private static void requireTag(DataInputStream input, int type, String name) throws IOException {
        require(input.readUnsignedByte() == type && name.equals(input.readUTF()));
    }
    private static void require(boolean condition) {
        if (!condition) throw new IllegalStateException("noncanonical mineshaft-corridor NBT");
    }
    private record Selected(String startKey, int pieceIndex, PieceFacts facts) { }
}
