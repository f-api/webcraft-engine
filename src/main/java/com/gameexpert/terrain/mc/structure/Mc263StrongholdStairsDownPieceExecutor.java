package com.gameexpert.terrain.mc.structure;

/** Public exact-type adapter for pinned stairs-down, straight-stairs, and source stairs. */
public final class Mc263StrongholdStairsDownPieceExecutor {
    public static final String PIECE_TYPE = "minecraft:shsd";
    public static final String STRAIGHT_PIECE_TYPE = "minecraft:shssd";
    private Mc263StrongholdStairsDownPieceExecutor() { }
    public static Mc263StrongholdStraightPieceExecutor.ExecutionResult execute(
            Mc263StrongholdStartPieceFactoryAdapter.PieceData piece,
            Mc263StrongholdStraightPieceExecutor.BoundingBox clip,
            Mc263StrongholdStraightPieceExecutor.RandomSource random,
            Mc263StrongholdStraightPieceExecutor.WorldAccess world) {
        if (piece == null || piece.type() != Mc263StrongholdGraphCarrier.PieceType.STAIRS_DOWN
                && piece.type() != Mc263StrongholdGraphCarrier.PieceType.STRAIGHT_STAIRS_DOWN
                && piece.type() != Mc263StrongholdGraphCarrier.PieceType.START) {
            throw new IllegalArgumentException("expected exact pinned stronghold stairs piece");
        }
        return Mc263StrongholdStraightPieceExecutor.execute(piece, clip, random, world);
    }
}
