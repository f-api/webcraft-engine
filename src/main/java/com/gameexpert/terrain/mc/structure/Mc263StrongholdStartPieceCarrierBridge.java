package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkReferences;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Fail-closed carrier bridge for every hard-coded stronghold piece in this batch. */
public final class Mc263StrongholdStartPieceCarrierBridge {
    private static final String STRUCTURE = "minecraft:stronghold@";

    private Mc263StrongholdStartPieceCarrierBridge() { }

    public static final class ReplacementPayload {
        private final byte[] binaryNbtCompound;
        public ReplacementPayload(byte[] binaryNbtCompound) {
            this.binaryNbtCompound = Objects.requireNonNull(binaryNbtCompound,
                    "binaryNbtCompound").clone();
        }
        public byte[] binaryNbtCompound() { return binaryNbtCompound.clone(); }
    }

    public static final class PieceResult {
        private final String startKey;
        private final int pieceIndex;
        private final Mc263StrongholdStraightPieceExecutor.ExecutionResult execution;
        private final ReplacementPayload replacementPayload;
        PieceResult(String startKey, int pieceIndex,
                Mc263StrongholdStraightPieceExecutor.ExecutionResult execution,
                ReplacementPayload replacementPayload) {
            this.startKey = startKey; this.pieceIndex = pieceIndex;
            this.execution = execution; this.replacementPayload = replacementPayload;
        }
        public String startKey() { return startKey; }
        public int pieceIndex() { return pieceIndex; }
        public Mc263StrongholdStraightPieceExecutor.ExecutionResult execution() { return execution; }
        public ReplacementPayload replacementPayload() { return replacementPayload; }
    }

    public static List<PieceResult> execute(Mc263StructureCarrier carrier,
            ChunkReferences references, Mc263StrongholdStraightPieceExecutor.BoundingBox clip,
            Mc263StrongholdStraightPieceExecutor.RandomSource random,
            Mc263StrongholdStraightPieceExecutor.WorldAccess world) {
        Objects.requireNonNull(carrier, "carrier"); Objects.requireNonNull(references, "references");
        Objects.requireNonNull(clip, "clip"); Objects.requireNonNull(random, "random");
        Objects.requireNonNull(world, "world");
        ArrayList<Selected> selected = new ArrayList<>();
        for (ValidStart start : carrier.resolveStarts(references)) {
            if (!start.startKey().startsWith(STRUCTURE)) continue;
            for (int index = 0; index < start.orderedPieces().size(); index++) {
                Piece piece = start.orderedPieces().get(index);
                if (!Mc263StrongholdStartPieceCodec.ownedPieceTypes().contains(piece.pieceType())) {
                    continue;
                }
                Mc263StrongholdStartPieceFactoryAdapter.PieceData data =
                        Mc263StrongholdStartPieceCodec.decode(piece);
                Mc263StrongholdStraightPieceExecutor.preflight(data, world);
                selected.add(new Selected(start.startKey(), index, data));
            }
        }
        ArrayList<PieceResult> result = new ArrayList<>();
        for (Selected value : selected) {
            if (!clip.intersects(value.data.box())) continue;
            Mc263StrongholdStraightPieceExecutor.ExecutionResult execution =
                    Mc263StrongholdStraightPieceExecutor.execute(value.data, clip, random, world);
            ReplacementPayload replacement = null;
            if (execution.placedMutableFact() != value.data.placed()) {
                Mc263StrongholdStartPieceFactoryAdapter.PieceData changed = withPlaced(
                        value.data, execution.placedMutableFact());
                replacement = new ReplacementPayload(Mc263StrongholdStartPieceCodec.encode(changed));
            }
            result.add(new PieceResult(value.startKey, value.index, execution, replacement));
        }
        return List.copyOf(result);
    }

    private static Mc263StrongholdStartPieceFactoryAdapter.PieceData withPlaced(
            Mc263StrongholdStartPieceFactoryAdapter.PieceData d, boolean placed) {
        return new Mc263StrongholdStartPieceFactoryAdapter.PieceData(d.type(), d.box(), d.depth(),
                d.orientation(), d.door(), d.source(), d.first(), d.second(), d.third(),
                d.fourth(), placed, d.tall(), d.variant());
    }

    private static final class Selected {
        final String startKey; final int index;
        final Mc263StrongholdStartPieceFactoryAdapter.PieceData data;
        Selected(String startKey, int index,
                Mc263StrongholdStartPieceFactoryAdapter.PieceData data) {
            this.startKey = startKey; this.index = index; this.data = data;
        }
    }
}
