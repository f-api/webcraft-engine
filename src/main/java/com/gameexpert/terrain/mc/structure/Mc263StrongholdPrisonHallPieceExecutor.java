package com.gameexpert.terrain.mc.structure;

/** Public exact-type adapter for pinned {@code minecraft:shph}. */
public final class Mc263StrongholdPrisonHallPieceExecutor {
    public static final String PIECE_TYPE = "minecraft:shph";
    private Mc263StrongholdPrisonHallPieceExecutor() { }
    public static Mc263StrongholdStraightPieceExecutor.ExecutionResult execute(
            Mc263StrongholdStartPieceFactoryAdapter.PieceData piece,
            Mc263StrongholdStraightPieceExecutor.BoundingBox clip,
            Mc263StrongholdStraightPieceExecutor.RandomSource random,
            Mc263StrongholdStraightPieceExecutor.WorldAccess world) {
        require(piece, Mc263StrongholdGraphCarrier.PieceType.PRISON_HALL);
        return Mc263StrongholdStraightPieceExecutor.execute(piece, clip, random, world);
    }
    private static void require(Mc263StrongholdStartPieceFactoryAdapter.PieceData piece,
            Mc263StrongholdGraphCarrier.PieceType type) {
        if (piece == null || piece.type() != type) throw new IllegalArgumentException(
                "expected exact " + type.id() + " piece");
    }
}
