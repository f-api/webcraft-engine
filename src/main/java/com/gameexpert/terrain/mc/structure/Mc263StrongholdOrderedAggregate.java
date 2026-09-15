package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkReferences;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.PiecePayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Ordered execution boundary for generated or reloaded pinned stronghold starts. */
public final class Mc263StrongholdOrderedAggregate {
    public static final String STRUCTURE = "minecraft:stronghold";
    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long ADDEND = 0xBL;
    private static final long MASK = (1L << 48) - 1;

    private Mc263StrongholdOrderedAggregate() { }

    public record BoundingBox(int minX, int minY, int minZ,
                              int maxX, int maxY, int maxZ) {
        public BoundingBox {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted stronghold aggregate clip");
            }
        }
    }

    /** All projections must describe the same underlying ordered world. */
    public record Worlds(Mc263StrongholdStraightPieceExecutor.WorldAccess common,
                         Mc263StrongholdLeftTurnPieceExecutor.WorldAccess left,
                         Mc263StrongholdRightTurnPieceExecutor.WorldAccess right,
                         Mc263StrongholdFillerCorridorPieceExecutor.WorldAccess filler) {
        public Worlds {
            Objects.requireNonNull(common, "common stronghold world");
            Objects.requireNonNull(left, "left-turn stronghold world");
            Objects.requireNonNull(right, "right-turn stronghold world");
            Objects.requireNonNull(filler, "filler stronghold world");
        }
    }

    /** Exact legacy-random continuation shared by every piece in encounter order. */
    public static final class StructureRandom implements
            Mc263StrongholdStraightPieceExecutor.RandomSource,
            Mc263StrongholdLeftTurnPieceExecutor.RandomSource,
            Mc263StrongholdRightTurnPieceExecutor.RandomSource {
        private long state;
        private long calls;

        public StructureRandom(long externalSeed) {
            state = (externalSeed ^ MULTIPLIER) & MASK;
        }

        private StructureRandom(long state, long calls) {
            if ((state & ~MASK) != 0 || calls < 0) {
                throw new IllegalArgumentException("invalid stronghold placement RNG continuation");
            }
            this.state = state;
            this.calls = calls;
        }

        public static StructureRandom resume(RngContinuation continuation) {
            Objects.requireNonNull(continuation, "stronghold RNG continuation");
            return new StructureRandom(continuation.internalState(), continuation.rawCalls());
        }

        private int next(int bits) {
            state = (state * MULTIPLIER + ADDEND) & MASK;
            calls++;
            return (int) (state >>> (48 - bits));
        }

        @Override public float nextFloat() { return next(24) * 0x1.0p-24f; }

        @Override public long nextLong() {
            return ((long) next(32) << 32) + next(32);
        }

        public RngContinuation continuation() { return new RngContinuation(state, calls); }
    }

    public record RngContinuation(long internalState, long rawCalls) {
        public RngContinuation {
            if ((internalState & ~MASK) != 0 || rawCalls < 0) {
                throw new IllegalArgumentException("invalid stronghold placement RNG continuation");
            }
        }
    }

    public record PieceEvidence(int pieceIndex, String pieceType,
                                Object execution, byte[] replacementPayload) {
        public PieceEvidence {
            Objects.requireNonNull(pieceType, "stronghold evidence piece type");
            Objects.requireNonNull(execution, "stronghold piece execution");
            replacementPayload = replacementPayload == null ? null : replacementPayload.clone();
        }
        @Override public byte[] replacementPayload() {
            return replacementPayload == null ? null : replacementPayload.clone();
        }
    }

    public record StartEvidence(String startKey, int originChunkX, int originChunkZ,
                                int references, List<PieceEvidence> orderedPieces,
                                ValidStart successorStart) {
        public StartEvidence {
            Objects.requireNonNull(startKey, "stronghold evidence start key");
            orderedPieces = List.copyOf(orderedPieces);
        }
    }

    /** Strictly decodes and globally capability-preflights without RNG or world observation. */
    public static void preflight(Mc263StructureCarrier carrier, ChunkReferences references,
                                 Worlds worlds) {
        Selection selection = select(carrier, references);
        capabilityPreflight(selection, worlds);
    }

    public static List<StartEvidence> execute(Mc263StructureCarrier carrier,
            ChunkReferences references, BoundingBox placementClip, Worlds worlds,
            StructureRandom random) {
        Objects.requireNonNull(placementClip, "stronghold placement clip");
        Objects.requireNonNull(random, "stronghold placement random");
        Selection selection = select(carrier, references);
        capabilityPreflight(selection, worlds);

        Mc263StrongholdStraightPieceExecutor.BoundingBox commonClip = commonBox(placementClip);
        ArrayList<StartEvidence> result = new ArrayList<>();
        for (SelectedStart selectedStart : selection.starts()) {
            ArrayList<PieceEvidence> evidence = new ArrayList<>();
            ArrayList<Piece> successorPieces = new ArrayList<>(
                    selectedStart.start().orderedPieces());
            boolean changed = false;
            for (SelectedPiece selected : selectedStart.pieces()) {
                if (!intersects(selected.piece().boundingBox(), placementClip)) continue;
                Object execution = executePiece(selected, placementClip, commonClip, worlds, random);
                byte[] replacement = replacement(selected, execution);
                if (replacement != null) {
                    Piece old = selected.piece();
                    successorPieces.set(selected.index(), new Piece(old.pieceType(),
                            old.boundingBox(), false, Mc263StructureCarrier.Projection.NOT_APPLICABLE,
                            0, List.of(), new PiecePayload(replacement)));
                    changed = true;
                }
                evidence.add(new PieceEvidence(selected.index(), selected.piece().pieceType(),
                        execution, replacement));
            }
            ValidStart start = selectedStart.start();
            ValidStart successor = changed ? new ValidStart(start.startKey(),
                    start.originChunkX(), start.originChunkZ(), start.references(),
                    start.adjustedBoundingBox(), successorPieces) : null;
            result.add(new StartEvidence(start.startKey(), start.originChunkX(),
                    start.originChunkZ(), start.references(), evidence, successor));
        }
        return List.copyOf(result);
    }

    private static Selection select(Mc263StructureCarrier carrier, ChunkReferences references) {
        Objects.requireNonNull(carrier, "stronghold carrier");
        Objects.requireNonNull(references, "stronghold references");
        ArrayList<SelectedStart> starts = new ArrayList<>();
        for (ValidStart start : carrier.resolveStarts(references, STRUCTURE)) {
            if (!start.startKey().startsWith(STRUCTURE + "@")) {
                throw new IllegalArgumentException("malformed stronghold start key");
            }
            ArrayList<SelectedPiece> pieces = new ArrayList<>();
            int roots = 0;
            for (int index = 0; index < start.orderedPieces().size(); index++) {
                Piece piece = start.orderedPieces().get(index);
                Decoded decoded = decode(piece);
                if (decoded.type() == Mc263StrongholdGraphCarrier.PieceType.START) {
                    roots++;
                    if (index != 0) {
                        throw new IllegalArgumentException("stronghold start piece is not root");
                    }
                }
                pieces.add(new SelectedPiece(index, piece, decoded));
            }
            if (roots != 1) {
                throw new IllegalArgumentException("stronghold must contain one ordered start piece");
            }
            starts.add(new SelectedStart(start, List.copyOf(pieces)));
        }
        return new Selection(List.copyOf(starts));
    }

    private static Decoded decode(Piece piece) {
        return switch (piece.pieceType()) {
            case Mc263StrongholdLeftTurnPieceExecutor.PIECE_TYPE -> new Decoded(
                    Mc263StrongholdGraphCarrier.PieceType.LEFT_TURN,
                    Mc263StrongholdLeftTurnCarrierBridge.decodeFacts(piece));
            case Mc263StrongholdRightTurnPieceExecutor.PIECE_TYPE -> new Decoded(
                    Mc263StrongholdGraphCarrier.PieceType.RIGHT_TURN,
                    Mc263StrongholdRightTurnCarrierBridge.decodeFacts(piece));
            case Mc263StrongholdFillerCorridorPieceExecutor.PIECE_TYPE -> new Decoded(
                    Mc263StrongholdGraphCarrier.PieceType.FILLER_CORRIDOR,
                    Mc263StrongholdFillerCorridorCarrierBridge.decodeFacts(piece));
            default -> {
                Mc263StrongholdStartPieceFactoryAdapter.PieceData value =
                        Mc263StrongholdStartPieceCodec.decode(piece);
                yield new Decoded(value.type(), value);
            }
        };
    }

    private static void capabilityPreflight(Selection selection, Worlds worlds) {
        Objects.requireNonNull(worlds, "stronghold worlds");
        for (SelectedStart start : selection.starts()) {
            for (SelectedPiece piece : start.pieces()) {
                switch (piece.decoded().type()) {
                    case LEFT_TURN -> Mc263StrongholdLeftTurnPieceExecutor.preflight(
                            (Mc263StrongholdLeftTurnPieceExecutor.PieceFacts)
                                    piece.decoded().facts(), worlds.left());
                    case RIGHT_TURN -> Mc263StrongholdRightTurnPieceExecutor.preflight(
                            (Mc263StrongholdRightTurnPieceExecutor.PieceFacts)
                                    piece.decoded().facts(), worlds.right());
                    case FILLER_CORRIDOR -> Mc263StrongholdFillerCorridorPieceExecutor.preflight(
                            (Mc263StrongholdFillerCorridorPieceExecutor.PieceFacts)
                                    piece.decoded().facts(), worlds.filler());
                    default -> Mc263StrongholdStraightPieceExecutor.preflight(
                            (Mc263StrongholdStartPieceFactoryAdapter.PieceData)
                                    piece.decoded().facts(), worlds.common());
                }
            }
        }
    }

    private static Object executePiece(SelectedPiece selected, BoundingBox clip,
            Mc263StrongholdStraightPieceExecutor.BoundingBox commonClip, Worlds worlds,
            StructureRandom random) {
        Object facts = selected.decoded().facts();
        return switch (selected.decoded().type()) {
            case STRAIGHT -> Mc263StrongholdStraightPieceExecutor.execute(
                    (Mc263StrongholdStartPieceFactoryAdapter.PieceData) facts,
                    commonClip, random, worlds.common());
            case PRISON_HALL -> Mc263StrongholdPrisonHallPieceExecutor.execute(
                    (Mc263StrongholdStartPieceFactoryAdapter.PieceData) facts,
                    commonClip, random, worlds.common());
            case LEFT_TURN -> Mc263StrongholdLeftTurnPieceExecutor.execute(
                    (Mc263StrongholdLeftTurnPieceExecutor.PieceFacts) facts,
                    leftBox(clip), random, worlds.left());
            case RIGHT_TURN -> Mc263StrongholdRightTurnPieceExecutor.execute(
                    (Mc263StrongholdRightTurnPieceExecutor.PieceFacts) facts,
                    rightBox(clip), random, worlds.right());
            case ROOM_CROSSING -> Mc263StrongholdRoomCrossingPieceExecutor.execute(
                    (Mc263StrongholdStartPieceFactoryAdapter.PieceData) facts,
                    commonClip, random, worlds.common());
            case STRAIGHT_STAIRS_DOWN, STAIRS_DOWN, START ->
                    Mc263StrongholdStairsDownPieceExecutor.execute(
                            (Mc263StrongholdStartPieceFactoryAdapter.PieceData) facts,
                            commonClip, random, worlds.common());
            case FIVE_CROSSING -> Mc263StrongholdFiveCrossingPieceExecutor.execute(
                    (Mc263StrongholdStartPieceFactoryAdapter.PieceData) facts,
                    commonClip, random, worlds.common());
            case CHEST_CORRIDOR -> Mc263StrongholdChestCorridorPieceExecutor.execute(
                    (Mc263StrongholdStartPieceFactoryAdapter.PieceData) facts,
                    commonClip, random, worlds.common());
            case LIBRARY -> Mc263StrongholdLibraryPieceExecutor.execute(
                    (Mc263StrongholdStartPieceFactoryAdapter.PieceData) facts,
                    commonClip, random, worlds.common());
            case PORTAL_ROOM -> Mc263StrongholdPortalRoomPieceExecutor.execute(
                    (Mc263StrongholdStartPieceFactoryAdapter.PieceData) facts,
                    commonClip, random, worlds.common());
            case FILLER_CORRIDOR -> Mc263StrongholdFillerCorridorPieceExecutor.execute(
                    (Mc263StrongholdFillerCorridorPieceExecutor.PieceFacts) facts,
                    fillerBox(clip), worlds.filler());
        };
    }

    private static byte[] replacement(SelectedPiece selected, Object execution) {
        if (!(execution instanceof Mc263StrongholdStraightPieceExecutor.ExecutionResult value)) {
            return null;
        }
        Mc263StrongholdGraphCarrier.PieceType type = selected.decoded().type();
        if (type != Mc263StrongholdGraphCarrier.PieceType.CHEST_CORRIDOR
                && type != Mc263StrongholdGraphCarrier.PieceType.PORTAL_ROOM) return null;
        Mc263StrongholdStartPieceFactoryAdapter.PieceData facts =
                (Mc263StrongholdStartPieceFactoryAdapter.PieceData) selected.decoded().facts();
        if (facts.placed() == value.placedMutableFact()) return null;
        if (facts.placed() || !value.placedMutableFact()) {
            throw new IllegalArgumentException("stronghold mutable fact may only advance false-to-true");
        }
        return Mc263StrongholdStartPieceCodec.encode(withPlaced(facts, true));
    }

    private static Mc263StrongholdStartPieceFactoryAdapter.PieceData withPlaced(
            Mc263StrongholdStartPieceFactoryAdapter.PieceData value, boolean placed) {
        return new Mc263StrongholdStartPieceFactoryAdapter.PieceData(value.type(), value.box(),
                value.depth(), value.orientation(), value.door(), value.source(), value.first(),
                value.second(), value.third(), value.fourth(), placed, value.tall(),
                value.variant());
    }

    private static boolean intersects(Mc263StructureCarrier.BoundingBox a, BoundingBox b) {
        return a.maxX() >= b.minX() && a.minX() <= b.maxX()
                && a.maxY() >= b.minY() && a.minY() <= b.maxY()
                && a.maxZ() >= b.minZ() && a.minZ() <= b.maxZ();
    }

    private static Mc263StrongholdStraightPieceExecutor.BoundingBox commonBox(BoundingBox b) {
        return new Mc263StrongholdStraightPieceExecutor.BoundingBox(
                b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }
    private static Mc263StrongholdLeftTurnPieceExecutor.BoundingBox leftBox(BoundingBox b) {
        return new Mc263StrongholdLeftTurnPieceExecutor.BoundingBox(
                b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }
    private static Mc263StrongholdRightTurnPieceExecutor.BoundingBox rightBox(BoundingBox b) {
        return new Mc263StrongholdRightTurnPieceExecutor.BoundingBox(
                b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }
    private static Mc263StrongholdFillerCorridorPieceExecutor.BoundingBox fillerBox(BoundingBox b) {
        return new Mc263StrongholdFillerCorridorPieceExecutor.BoundingBox(
                b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }

    private record Decoded(Mc263StrongholdGraphCarrier.PieceType type, Object facts) { }
    private record SelectedPiece(int index, Piece piece, Decoded decoded) { }
    private record SelectedStart(ValidStart start, List<SelectedPiece> pieces) { }
    private record Selection(List<SelectedStart> starts) { }
}
