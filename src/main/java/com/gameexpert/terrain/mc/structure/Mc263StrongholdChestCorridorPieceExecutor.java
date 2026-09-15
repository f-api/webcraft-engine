package com.gameexpert.terrain.mc.structure;

/** Public exact-type adapter for pinned {@code minecraft:shcc}. */
public final class Mc263StrongholdChestCorridorPieceExecutor {
    public static final String PIECE_TYPE = "minecraft:shcc";
    private Mc263StrongholdChestCorridorPieceExecutor() { }
    public static Mc263StrongholdStraightPieceExecutor.ExecutionResult execute(
            Mc263StrongholdStartPieceFactoryAdapter.PieceData piece,
            Mc263StrongholdStraightPieceExecutor.BoundingBox clip,
            Mc263StrongholdStraightPieceExecutor.RandomSource random,
            Mc263StrongholdStraightPieceExecutor.WorldAccess world) {
        if (piece == null || piece.type() != Mc263StrongholdGraphCarrier.PieceType.CHEST_CORRIDOR) {
            throw new IllegalArgumentException("expected exact minecraft:shcc piece");
        }
        return Mc263StrongholdStraightPieceExecutor.execute(piece, clip, random, world);
    }
}
