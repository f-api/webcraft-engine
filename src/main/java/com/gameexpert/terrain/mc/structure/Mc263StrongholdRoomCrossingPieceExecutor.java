package com.gameexpert.terrain.mc.structure;

/** Public exact-type adapter for pinned {@code minecraft:shrc}. */
public final class Mc263StrongholdRoomCrossingPieceExecutor {
    public static final String PIECE_TYPE = "minecraft:shrc";
    private Mc263StrongholdRoomCrossingPieceExecutor() { }
    public static Mc263StrongholdStraightPieceExecutor.ExecutionResult execute(
            Mc263StrongholdStartPieceFactoryAdapter.PieceData piece,
            Mc263StrongholdStraightPieceExecutor.BoundingBox clip,
            Mc263StrongholdStraightPieceExecutor.RandomSource random,
            Mc263StrongholdStraightPieceExecutor.WorldAccess world) {
        if (piece == null || piece.type() != Mc263StrongholdGraphCarrier.PieceType.ROOM_CROSSING) {
            throw new IllegalArgumentException("expected exact minecraft:shrc piece");
        }
        return Mc263StrongholdStraightPieceExecutor.execute(piece, clip, random, world);
    }
}
