package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom;
import com.gameexpert.terrain.mc.structure.Mc263BuriedTreasurePieceExecutor.ExecutionResult;
import com.gameexpert.terrain.mc.structure.Mc263BuriedTreasurePieceExecutor.PieceFacts;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkReferences;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Dormant carrier-to-piece bridge for the exact pinned {@code minecraft:buried_treasure} codec.
 *
 * <p>This deliberately does not register a structure or a decoration step. Every referenced valid
 * start, persisted payload, and world capability is checked before the supplied structure RNG or
 * live world is observed.</p>
 */
public final class Mc263BuriedTreasureCarrierBridge {
    public static final String STRUCTURE = "minecraft:buried_treasure";

    private Mc263BuriedTreasureCarrierBridge() {
    }

    public record PieceResult(String startKey, ExecutionResult execution) {
        public PieceResult {
            Objects.requireNonNull(startKey, "startKey");
            Objects.requireNonNull(execution, "execution");
        }
    }

    public static List<PieceResult> execute(Mc263StructureCarrier carrier,
            ChunkReferences references, Mc263BuriedTreasurePieceExecutor.BoundingBox placementClip,
            Mc263BuriedTreasurePieceExecutor.WorldAccess world, WorldgenRandom random) {
        return execute(carrier, references, placementClip, world, random,
                Mc263BuriedTreasurePieceExecutor.TraceSink.disabled());
    }

    public static List<PieceResult> execute(Mc263StructureCarrier carrier,
            ChunkReferences references, Mc263BuriedTreasurePieceExecutor.BoundingBox placementClip,
            Mc263BuriedTreasurePieceExecutor.WorldAccess world, WorldgenRandom random,
            Mc263BuriedTreasurePieceExecutor.TraceSink trace) {
        if (carrier == null || references == null || placementClip == null || world == null
                || random == null || trace == null) {
            throw new IllegalArgumentException(
                    "carrier, references, clip, world, random, and trace are required");
        }

        List<ValidStart> starts = carrier.resolveStarts(references, STRUCTURE);
        ArrayList<SelectedPiece> selected = new ArrayList<>(starts.size());
        for (ValidStart start : starts) {
            if (start.orderedPieces().size() != 1) {
                throw new IllegalArgumentException(
                        "buried-treasure start must contain exactly one piece");
            }
            Piece piece = start.orderedPieces().get(0);
            preflightPiece(piece);
            selected.add(new SelectedPiece(start.startKey(), piece));
        }
        preflightCapabilities(world);

        ArrayList<PieceResult> results = new ArrayList<>(selected.size());
        for (int index = 0; index < selected.size(); index++) {
            SelectedPiece selectedPiece = selected.get(index);
            trace.record("carrier_piece", index,
                    selectedPiece.piece().boundingBox().minX(),
                    selectedPiece.piece().boundingBox().minY(),
                    selectedPiece.piece().boundingBox().minZ());
            ExecutionResult result = Mc263BuriedTreasurePieceExecutor.execute(
                    facts(selectedPiece.piece()), placementClip, world, random, trace);
            results.add(new PieceResult(selectedPiece.startKey(), result));
        }
        return List.copyOf(results);
    }

    private static void preflightPiece(Piece piece) {
        if (!Mc263BuriedTreasurePieceExecutor.PIECE_TYPE.equals(piece.pieceType())
                || piece.poolElement()
                || piece.projection() != Mc263StructureCarrier.Projection.NOT_APPLICABLE
                || piece.groundLevelDelta() != 0
                || !piece.junctions().isEmpty()) {
            throw new IllegalArgumentException("expected exact minecraft:btp piece");
        }
        Mc263StructureCarrier.BoundingBox box = piece.boundingBox();
        if (box.minX() != box.maxX() || box.minY() != box.maxY() || box.minZ() != box.maxZ()) {
            throw new IllegalArgumentException("buried-treasure piece bounding box must be a point");
        }
        if (!Arrays.equals(piece.persistedPayload().binaryNbtCompound(), officialNbt(box))) {
            throw new IllegalArgumentException("noncanonical minecraft:btp persisted NBT");
        }
    }

    private static void preflightCapabilities(Mc263BuriedTreasurePieceExecutor.WorldAccess world) {
        if (!world.supportsCopiedStateWrites()) {
            throw new UnsupportedOperationException("buried-treasure copied-state writes");
        }
        if (!world.supportsExactState("minecraft:sand")) {
            throw new UnsupportedOperationException("buried-treasure state: minecraft:sand");
        }
        for (String facing : List.of("north", "east", "south", "west")) {
            String state = "minecraft:chest[facing=" + facing
                    + ",type=single,waterlogged=false]";
            if (!world.supportsExactState(state)) {
                throw new UnsupportedOperationException("buried-treasure state: " + state);
            }
        }
        if (!world.supportsChestLoot()) {
            throw new UnsupportedOperationException("buried-treasure chest loot sidecar");
        }
    }

    private static PieceFacts facts(Piece piece) {
        Mc263StructureCarrier.BoundingBox box = piece.boundingBox();
        return new PieceFacts(piece.pieceType(),
                new Mc263BuriedTreasurePieceExecutor.BoundingBox(
                        box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()));
    }

    static byte[] officialNbt(Mc263StructureCarrier.BoundingBox box) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(74);
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(10); output.writeShort(0);
                output.writeByte(11); writeNbtName(output, "BB"); output.writeInt(6);
                output.writeInt(box.minX()); output.writeInt(box.minY()); output.writeInt(box.minZ());
                output.writeInt(box.maxX()); output.writeInt(box.maxY()); output.writeInt(box.maxZ());
                output.writeByte(8); writeNbtName(output, "id");
                byte[] id = Mc263BuriedTreasurePieceExecutor.PIECE_TYPE
                        .getBytes(StandardCharsets.UTF_8);
                output.writeShort(id.length); output.write(id);
                output.writeByte(3); writeNbtName(output, "GD"); output.writeInt(0);
                output.writeByte(3); writeNbtName(output, "O"); output.writeInt(-1);
                output.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory buried-treasure NBT failed", exception);
        }
    }

    private static void writeNbtName(DataOutputStream output, String name) throws IOException {
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        output.writeShort(bytes.length); output.write(bytes);
    }

    private record SelectedPiece(String startKey, Piece piece) {
    }
}
