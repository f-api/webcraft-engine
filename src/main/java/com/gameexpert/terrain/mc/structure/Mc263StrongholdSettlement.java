package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkReferences;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkStarts;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.PiecePayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.StartEntry;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Atomic immutable-successor settlement for exact ordered stronghold evidence. */
public final class Mc263StrongholdSettlement {
    private Mc263StrongholdSettlement() { }

    public record Settlement(Mc263StructureCarrier structureCarrier) {
        public Settlement { Objects.requireNonNull(structureCarrier, "stronghold successor"); }
    }

    public static Settlement settle(Mc263StructureCarrier carrier, ChunkReferences references,
            List<Mc263StrongholdOrderedAggregate.StartEvidence> evidence) {
        Objects.requireNonNull(carrier, "stronghold carrier");
        Objects.requireNonNull(references, "stronghold references");
        Objects.requireNonNull(evidence, "stronghold evidence");

        ArrayList<ValidStart> resolvedOrder = new ArrayList<>();
        Map<String, ValidStart> resolved = new HashMap<>();
        for (ValidStart start : carrier.resolveStarts(references,
                Mc263StrongholdOrderedAggregate.STRUCTURE)) {
            if (resolved.put(start.startKey(), start) != null) {
                throw new IllegalArgumentException("duplicate resolved stronghold start");
            }
            resolvedOrder.add(start);
        }
        if (evidence.size() != resolvedOrder.size()) {
            throw new IllegalArgumentException("stronghold start evidence set mismatch");
        }

        Map<String, ValidStart> successors = new HashMap<>();
        Set<String> seenStarts = new HashSet<>();
        int startOrder = 0;
        for (Mc263StrongholdOrderedAggregate.StartEvidence startEvidence : List.copyOf(evidence)) {
            ValidStart start = resolved.get(startEvidence.startKey());
            if (start == null || !seenStarts.add(startEvidence.startKey())
                    || start != resolvedOrder.get(startOrder++)
                    || start.originChunkX() != startEvidence.originChunkX()
                    || start.originChunkZ() != startEvidence.originChunkZ()
                    || start.references() != startEvidence.references()) {
                throw new IllegalArgumentException("stronghold start evidence/carrier mismatch");
            }
            ArrayList<Piece> expectedPieces = new ArrayList<>(start.orderedPieces());
            int previous = -1;
            for (Mc263StrongholdOrderedAggregate.PieceEvidence pieceEvidence
                    : startEvidence.orderedPieces()) {
                int index = pieceEvidence.pieceIndex();
                if (index <= previous || index >= expectedPieces.size()) {
                    throw new IllegalArgumentException("stronghold evidence piece index outside start");
                }
                previous = index;
                Piece original = start.orderedPieces().get(index);
                if (!original.pieceType().equals(pieceEvidence.pieceType())) {
                    throw new IllegalArgumentException("stronghold evidence piece type mismatch");
                }
                validateExecutionKind(original, pieceEvidence.execution());
                byte[] replacement = pieceEvidence.replacementPayload();
                if (replacement != null) {
                    validateReplacement(original, pieceEvidence.execution(), replacement);
                    expectedPieces.set(index, replaced(original, replacement));
                } else {
                    validateNoReplacement(original, pieceEvidence.execution());
                }
            }
            ValidStart expected = new ValidStart(start.startKey(), start.originChunkX(),
                    start.originChunkZ(), start.references(), start.adjustedBoundingBox(),
                    expectedPieces);
            ValidStart supplied = startEvidence.successorStart();
            if (supplied == null) {
                if (!sameStart(start, expected)) {
                    throw new IllegalArgumentException("missing stronghold successor start");
                }
            } else if (!sameStart(supplied, expected)) {
                throw new IllegalArgumentException("stronghold successor disagrees with evidence");
            }
            successors.put(start.startKey(), supplied == null ? start : expected);
        }

        Mc263StructureCarrier successor = rebuild(carrier, successors);
        successor = successor.strictlyDecoded();
        return new Settlement(successor);
    }

    private static void validateExecutionKind(Piece piece, Object execution) {
        boolean common = Mc263StrongholdStartPieceCodec.ownedPieceTypes()
                .contains(piece.pieceType());
        boolean valid = common
                ? execution instanceof Mc263StrongholdStraightPieceExecutor.ExecutionResult
                : piece.pieceType().equals(Mc263StrongholdLeftTurnPieceExecutor.PIECE_TYPE)
                        ? execution instanceof Mc263StrongholdLeftTurnPieceExecutor.ExecutionResult
                        : piece.pieceType().equals(Mc263StrongholdRightTurnPieceExecutor.PIECE_TYPE)
                                ? execution instanceof Mc263StrongholdRightTurnPieceExecutor.ExecutionResult
                                : piece.pieceType().equals(
                                        Mc263StrongholdFillerCorridorPieceExecutor.PIECE_TYPE)
                                        && execution instanceof
                                        Mc263StrongholdFillerCorridorPieceExecutor.ExecutionResult;
        if (!valid) throw new IllegalArgumentException("stronghold execution evidence kind mismatch");
    }

    private static void validateReplacement(Piece original, Object execution, byte[] replacement) {
        if (!original.pieceType().equals(Mc263StrongholdChestCorridorPieceExecutor.PIECE_TYPE)
                && !original.pieceType().equals(
                        Mc263StrongholdPortalRoomPieceExecutor.PIECE_TYPE)) {
            throw new IllegalArgumentException("immutable stronghold piece replacement");
        }
        var before = Mc263StrongholdStartPieceCodec.decode(original);
        var result = (Mc263StrongholdStraightPieceExecutor.ExecutionResult) execution;
        Piece candidate = replaced(original, replacement);
        var after = Mc263StrongholdStartPieceCodec.decode(candidate);
        if (before.placed() || !after.placed() || !result.placedMutableFact()
                || !sameExceptPlaced(before, after)) {
            throw new IllegalArgumentException("noncanonical stronghold mutable replacement");
        }
        byte[] canonical = Mc263StrongholdStartPieceCodec.encode(after);
        if (!Arrays.equals(canonical, replacement)) {
            throw new IllegalArgumentException("noncanonical stronghold replacement payload");
        }
    }

    private static void validateNoReplacement(Piece original, Object execution) {
        if ((!original.pieceType().equals(Mc263StrongholdChestCorridorPieceExecutor.PIECE_TYPE)
                && !original.pieceType().equals(Mc263StrongholdPortalRoomPieceExecutor.PIECE_TYPE))
                || !(execution instanceof Mc263StrongholdStraightPieceExecutor.ExecutionResult r)) {
            return;
        }
        if (Mc263StrongholdStartPieceCodec.decode(original).placed()
                != r.placedMutableFact()) {
            throw new IllegalArgumentException("stronghold mutable result lacks replacement");
        }
    }

    private static boolean sameExceptPlaced(
            Mc263StrongholdStartPieceFactoryAdapter.PieceData a,
            Mc263StrongholdStartPieceFactoryAdapter.PieceData b) {
        return a.type() == b.type() && a.box().equals(b.box()) && a.depth() == b.depth()
                && a.orientation() == b.orientation() && a.door() == b.door()
                && a.source() == b.source() && a.first() == b.first()
                && a.second() == b.second() && a.third() == b.third()
                && a.fourth() == b.fourth() && a.tall() == b.tall()
                && a.variant() == b.variant();
    }

    private static Piece replaced(Piece piece, byte[] payload) {
        return new Piece(piece.pieceType(), piece.boundingBox(), false,
                Mc263StructureCarrier.Projection.NOT_APPLICABLE, 0, List.of(),
                new PiecePayload(payload));
    }

    private static boolean sameStart(ValidStart a, ValidStart b) {
        if (!a.startKey().equals(b.startKey()) || a.originChunkX() != b.originChunkX()
                || a.originChunkZ() != b.originChunkZ() || a.references() != b.references()
                || !a.adjustedBoundingBox().equals(b.adjustedBoundingBox())
                || a.orderedPieces().size() != b.orderedPieces().size()) return false;
        for (int index = 0; index < a.orderedPieces().size(); index++) {
            Piece left = a.orderedPieces().get(index);
            Piece right = b.orderedPieces().get(index);
            if (!left.pieceType().equals(right.pieceType())
                    || !left.boundingBox().equals(right.boundingBox())
                    || left.poolElement() != right.poolElement()
                    || left.projection() != right.projection()
                    || left.groundLevelDelta() != right.groundLevelDelta()
                    || !left.junctions().equals(right.junctions())
                    || !Arrays.equals(left.persistedPayload().binaryNbtCompound(),
                            right.persistedPayload().binaryNbtCompound())) return false;
        }
        return true;
    }

    private static Mc263StructureCarrier rebuild(Mc263StructureCarrier carrier,
            Map<String, ValidStart> replacements) {
        ArrayList<ChunkStarts> chunks = new ArrayList<>();
        for (ChunkStarts chunk : carrier.startChunks()) {
            ArrayList<StartEntry> starts = new ArrayList<>();
            for (StartEntry entry : chunk.orderedStarts()) {
                if (entry.body() instanceof ValidStart start
                        && replacements.containsKey(start.startKey())) {
                    starts.add(new StartEntry(entry.structureId(),
                            replacements.get(start.startKey())));
                } else {
                    starts.add(entry);
                }
            }
            chunks.add(chunk.withStarts(starts));
        }
        return new Mc263StructureCarrier(carrier.registry(), chunks, carrier.referenceChunks(),
                carrier.rawStartPayloads(), carrier.producerGraphPayloads());
    }
}
