package com.gameexpert.terrain.mc.structure;

/** Public exact-type adapter for pinned {@code minecraft:shli}. */
public final class Mc263StrongholdLibraryPieceExecutor {
    public static final String PIECE_TYPE = "minecraft:shli";
    private Mc263StrongholdLibraryPieceExecutor() { }
    public static Mc263StrongholdStraightPieceExecutor.ExecutionResult execute(
            Mc263StrongholdStartPieceFactoryAdapter.PieceData piece,
            Mc263StrongholdStraightPieceExecutor.BoundingBox clip,
            Mc263StrongholdStraightPieceExecutor.RandomSource random,
            Mc263StrongholdStraightPieceExecutor.WorldAccess world) {
        if (piece == null || piece.type() != Mc263StrongholdGraphCarrier.PieceType.LIBRARY) {
            throw new IllegalArgumentException("expected exact minecraft:shli piece");
        }
        return Mc263StrongholdStraightPieceExecutor.execute(piece, clip, random, world);
    }
}
