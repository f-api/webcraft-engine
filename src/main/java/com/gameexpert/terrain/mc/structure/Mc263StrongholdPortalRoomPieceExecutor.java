package com.gameexpert.terrain.mc.structure;

/** Public exact-type adapter for pinned {@code minecraft:shpr}. */
public final class Mc263StrongholdPortalRoomPieceExecutor {
    public static final String PIECE_TYPE = "minecraft:shpr";
    private Mc263StrongholdPortalRoomPieceExecutor() { }
    public static Mc263StrongholdStraightPieceExecutor.ExecutionResult execute(
            Mc263StrongholdStartPieceFactoryAdapter.PieceData piece,
            Mc263StrongholdStraightPieceExecutor.BoundingBox clip,
            Mc263StrongholdStraightPieceExecutor.RandomSource random,
            Mc263StrongholdStraightPieceExecutor.WorldAccess world) {
        if (piece == null || piece.type() != Mc263StrongholdGraphCarrier.PieceType.PORTAL_ROOM) {
            throw new IllegalArgumentException("expected exact minecraft:shpr piece");
        }
        return Mc263StrongholdStraightPieceExecutor.execute(piece, clip, random, world);
    }
}
