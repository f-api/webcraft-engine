package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkReferences;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Ordered execution boundary for generated or reloaded pinned mineshaft starts. */
public final class Mc263MineshaftOrderedAggregate {
    public static final String NORMAL_STRUCTURE = "minecraft:mineshaft";
    public static final String MESA_STRUCTURE = "minecraft:mineshaft_mesa";

    private Mc263MineshaftOrderedAggregate() { }

    public record BoundingBox(int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ) {
        public BoundingBox {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted mineshaft aggregate clip");
            }
        }
    }

    /** Type-specific authority projections over the same underlying world. */
    public record Worlds(
            Mc263MineshaftStairsPieceExecutor.WorldAccess stairs,
            Mc263MineshaftRoomPieceExecutor.WorldAccess room,
            Mc263MineshaftCrossingPieceExecutor.WorldAccess crossing,
            Mc263MineshaftCorridorPieceExecutor.WorldAccess corridor) {
        public Worlds {
            Objects.requireNonNull(stairs, "stairs world");
            Objects.requireNonNull(room, "room world");
            Objects.requireNonNull(crossing, "crossing world");
            Objects.requireNonNull(corridor, "corridor world");
        }
    }

    public enum PieceKind { ROOM, CORRIDOR, CROSSING, STAIRS }

    public sealed interface ExecutionEvidence permits RoomEvidence, CorridorEvidence,
            CrossingEvidence, StairsEvidence { }

    public record RoomEvidence(Mc263MineshaftRoomPieceExecutor.ExecutionResult execution)
            implements ExecutionEvidence { }

    /** The digest proves the exact immutable successor without exposing an encodable payload. */
    public record CorridorEvidence(
            Mc263MineshaftCorridorPieceExecutor.ExecutionResult execution,
            boolean replacementRequired, int replacementNbtBytes,
            String replacementNbtSha256) implements ExecutionEvidence {
        public CorridorEvidence {
            if (!replacementRequired
                    && (replacementNbtBytes != 0 || replacementNbtSha256 != null)) {
                throw new IllegalArgumentException("unexpected corridor replacement evidence");
            }
            if (replacementRequired
                    && (replacementNbtBytes <= 0 || replacementNbtSha256 == null)) {
                throw new IllegalArgumentException("missing corridor replacement evidence");
            }
        }
    }

    public record CrossingEvidence(Mc263MineshaftCrossingPieceExecutor.ExecutionResult execution)
            implements ExecutionEvidence { }

    public record StairsEvidence(Mc263MineshaftStairsPieceExecutor.ExecutionResult execution)
            implements ExecutionEvidence { }

    public record PieceEvidence(int pieceIndex, PieceKind kind, ExecutionEvidence execution) { }

    public record StartEvidence(String startKey, int originChunkX, int originChunkZ,
            int references, List<PieceEvidence> orderedPieces, ValidStart successorStart) {
        public StartEvidence { orderedPieces = List.copyOf(orderedPieces); }
        public StartEvidence(String startKey, int originChunkX, int originChunkZ,
                int references, List<PieceEvidence> orderedPieces) {
            this(startKey, originChunkX, originChunkZ, references, orderedPieces, null);
        }
    }

    public static List<StartEvidence> execute(Mc263StructureCarrier carrier,
            ChunkReferences references, BoundingBox placementClip, Worlds worlds,
            Mc263MineshaftCorridorPieceExecutor.StructureRandom random) {
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(references, "references");
        Objects.requireNonNull(placementClip, "placementClip");
        Objects.requireNonNull(worlds, "worlds");
        Objects.requireNonNull(random, "random");

        // Decode and validate every referenced mineshaft start before any capability or world call.
        ArrayList<SelectedStart> starts = new ArrayList<>();
        for (ValidStart start : carrier.resolveStarts(references)) {
            MineKind expected = MineKind.fromStart(start.startKey());
            if (expected == null) continue;
            ArrayList<SelectedPiece> pieces = new ArrayList<>();
            int rooms = 0;
            for (int index = 0; index < start.orderedPieces().size(); index++) {
                Piece piece = start.orderedPieces().get(index);
                SelectedPiece selected = switch (piece.pieceType()) {
                    case Mc263MineshaftRoomPieceExecutor.PIECE_TYPE -> {
                        rooms++;
                        if (index != 0) fail("mineshaft room is not ordered root piece");
                        var facts = Mc263MineshaftRoomCarrierBridge.decodeFacts(piece);
                        requireType(expected, facts.mineshaftType().nbtId(), "room");
                        yield new Room(index, facts);
                    }
                    case Mc263MineshaftCorridorPieceExecutor.PIECE_TYPE -> {
                        var facts = Mc263MineshaftCorridorCarrierBridge.decodeFacts(piece);
                        requireType(expected, facts.mineshaftType().nbtId(), "corridor");
                        yield new Corridor(index, facts, piece);
                    }
                    case Mc263MineshaftCrossingPieceExecutor.PIECE_TYPE -> {
                        var facts = Mc263MineshaftCrossingCarrierBridge.decodeFacts(piece);
                        requireType(expected, facts.mineshaftType().nbtId(), "crossing");
                        yield new Crossing(index, facts);
                    }
                    case Mc263MineshaftStairsPieceExecutor.PIECE_TYPE -> {
                        var facts = Mc263MineshaftStairsCarrierBridge.decodeFacts(piece);
                        requireType(expected, facts.mineshaftType().nbtId(), "stairs");
                        yield new Stairs(index, facts);
                    }
                    default -> throw new IllegalArgumentException(
                            "unknown pinned mineshaft piece type: " + piece.pieceType());
                };
                pieces.add(selected);
            }
            if (rooms != 1) fail("mineshaft start must contain one ordered root room");
            starts.add(new SelectedStart(start, List.copyOf(pieces)));
        }

        // Capability preflight is global: no selected piece may observe RNG/world before this ends.
        for (SelectedStart start : starts) for (SelectedPiece piece : start.pieces) {
            switch (piece) {
                case Room value -> Mc263MineshaftRoomPieceExecutor.preflight(
                        value.facts, worlds.room());
                case Corridor value -> Mc263MineshaftCorridorPieceExecutor.preflight(
                        value.facts, worlds.corridor());
                case Crossing value -> Mc263MineshaftCrossingPieceExecutor.preflight(
                        value.facts, worlds.crossing());
                case Stairs value -> Mc263MineshaftStairsPieceExecutor.preflight(
                        value.facts, worlds.stairs());
            }
        }

        ArrayList<StartEvidence> evidence = new ArrayList<>();
        for (SelectedStart selectedStart : starts) {
            ArrayList<PieceEvidence> pieceEvidence = new ArrayList<>();
            ArrayList<Piece> successorPieces = new ArrayList<>(selectedStart.start.orderedPieces());
            boolean changed = false;
            for (SelectedPiece piece : selectedStart.pieces) {
                ExecutionEvidence execution = executePiece(piece, placementClip, worlds, random);
                if (execution != null) {
                    pieceEvidence.add(new PieceEvidence(piece.index(), piece.kind(), execution));
                    if (piece instanceof Corridor corridor
                            && execution instanceof CorridorEvidence corridorEvidence
                            && corridorEvidence.replacementRequired()) {
                        Piece persisted = corridor.persistedPiece();
                        successorPieces.set(piece.index(), new Piece(persisted.pieceType(),
                                persisted.boundingBox(), false,
                                Mc263StructureCarrier.Projection.NOT_APPLICABLE, 0, List.of(),
                                new Mc263StructureCarrier.PiecePayload(
                                        Mc263MineshaftCorridorCarrierBridge.officialNbt(
                                                corridor.facts(), corridorEvidence.execution()
                                                        .finalHasPlacedSpider()))));
                        changed = true;
                    }
                }
            }
            ValidStart start = selectedStart.start;
            ValidStart successor = changed ? new ValidStart(start.startKey(), start.originChunkX(),
                    start.originChunkZ(), start.references(), start.adjustedBoundingBox(),
                    successorPieces) : null;
            evidence.add(new StartEvidence(start.startKey(), start.originChunkX(),
                    start.originChunkZ(), start.references(), pieceEvidence, successor));
        }
        return List.copyOf(evidence);
    }

    private static ExecutionEvidence executePiece(SelectedPiece piece, BoundingBox clip,
            Worlds worlds, Mc263MineshaftCorridorPieceExecutor.StructureRandom random) {
        return switch (piece) {
            case Room value -> {
                var box = roomBox(clip);
                if (!value.facts.boundingBox().intersects(box)) yield null;
                yield new RoomEvidence(Mc263MineshaftRoomPieceExecutor.execute(
                        value.facts, box, worlds.room()));
            }
            case Corridor value -> {
                var box = corridorBox(clip);
                if (!value.facts.boundingBox().intersects(box)) yield null;
                var result = Mc263MineshaftCorridorPieceExecutor.execute(
                        value.facts, box, worlds.corridor(), random);
                boolean replacement = result.finalHasPlacedSpider()
                        != value.facts.hasPlacedSpider();
                byte[] payload = replacement
                        ? Mc263MineshaftCorridorCarrierBridge.officialNbt(
                                value.facts, result.finalHasPlacedSpider()) : null;
                yield new CorridorEvidence(result, replacement,
                        payload == null ? 0 : payload.length,
                        payload == null ? null : sha256(payload));
            }
            case Crossing value -> {
                var box = crossingBox(clip);
                if (!value.facts.boundingBox().intersects(box)) yield null;
                yield new CrossingEvidence(Mc263MineshaftCrossingPieceExecutor.execute(
                        value.facts, box, worlds.crossing()));
            }
            case Stairs value -> {
                var box = stairsBox(clip);
                if (!value.facts.boundingBox().intersects(box)) yield null;
                yield new StairsEvidence(Mc263MineshaftStairsPieceExecutor.execute(
                        value.facts, box, worlds.stairs()));
            }
        };
    }

    private enum MineKind {
        NORMAL(0), MESA(1);
        private final int nbtId;
        MineKind(int nbtId) { this.nbtId = nbtId; }
        static MineKind fromStart(String key) {
            int separator = key.indexOf('@');
            if (separator < 0) return null;
            return switch (key.substring(0, separator)) {
                case NORMAL_STRUCTURE -> NORMAL;
                case MESA_STRUCTURE -> MESA;
                default -> null;
            };
        }
    }

    private static void requireType(MineKind expected, int actual, String piece) {
        if (actual != expected.nbtId) {
            throw new IllegalArgumentException("mineshaft " + piece + " MST disagrees with start kind");
        }
    }

    private static void fail(String message) { throw new IllegalArgumentException(message); }

    private static Mc263MineshaftRoomPieceExecutor.BoundingBox roomBox(BoundingBox b) {
        return new Mc263MineshaftRoomPieceExecutor.BoundingBox(
                b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }
    private static Mc263MineshaftCorridorPieceExecutor.BoundingBox corridorBox(BoundingBox b) {
        return new Mc263MineshaftCorridorPieceExecutor.BoundingBox(
                b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }
    private static Mc263MineshaftCrossingPieceExecutor.BoundingBox crossingBox(BoundingBox b) {
        return new Mc263MineshaftCrossingPieceExecutor.BoundingBox(
                b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }
    private static Mc263MineshaftStairsPieceExecutor.BoundingBox stairsBox(BoundingBox b) {
        return new Mc263MineshaftStairsPieceExecutor.BoundingBox(
                b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private sealed interface SelectedPiece permits Room, Corridor, Crossing, Stairs {
        int index();
        PieceKind kind();
    }
    private record Room(int index, Mc263MineshaftRoomPieceExecutor.PieceFacts facts)
            implements SelectedPiece { @Override public PieceKind kind() { return PieceKind.ROOM; } }
    private record Corridor(int index, Mc263MineshaftCorridorPieceExecutor.PieceFacts facts,
            Piece persistedPiece)
            implements SelectedPiece { @Override public PieceKind kind() { return PieceKind.CORRIDOR; } }
    private record Crossing(int index, Mc263MineshaftCrossingPieceExecutor.PieceFacts facts)
            implements SelectedPiece { @Override public PieceKind kind() { return PieceKind.CROSSING; } }
    private record Stairs(int index, Mc263MineshaftStairsPieceExecutor.PieceFacts facts)
            implements SelectedPiece { @Override public PieceKind kind() { return PieceKind.STAIRS; } }
    private record SelectedStart(ValidStart start, List<SelectedPiece> pieces) { }
}
