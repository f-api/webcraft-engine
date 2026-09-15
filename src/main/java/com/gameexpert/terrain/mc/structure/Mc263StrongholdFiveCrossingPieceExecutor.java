package com.gameexpert.terrain.mc.structure;

/** Public exact-type adapter for pinned {@code minecraft:sh5c}. */
public final class Mc263StrongholdFiveCrossingPieceExecutor {
    public static final String PIECE_TYPE = "minecraft:sh5c";
    private Mc263StrongholdFiveCrossingPieceExecutor() { }
    public static Mc263StrongholdStraightPieceExecutor.ExecutionResult execute(
            Mc263StrongholdStartPieceFactoryAdapter.PieceData piece,
            Mc263StrongholdStraightPieceExecutor.BoundingBox clip,
            Mc263StrongholdStraightPieceExecutor.RandomSource random,
            Mc263StrongholdStraightPieceExecutor.WorldAccess world) {
        if (piece == null || piece.type() != Mc263StrongholdGraphCarrier.PieceType.FIVE_CROSSING) {
            throw new IllegalArgumentException("expected exact minecraft:sh5c piece");
        }
        return Mc263StrongholdStraightPieceExecutor.execute(piece, clip, random, world);
    }
}
