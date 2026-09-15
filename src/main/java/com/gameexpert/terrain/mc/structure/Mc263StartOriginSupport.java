package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkStarts;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.PiecePayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Projection;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.StartEntry;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import com.gameexpert.terrain.mc.structure.Mc263StructureStartDecisionCoordinator.AttemptContext;

import java.util.List;
import java.util.Objects;

/**
 * Shared attempt-context validation and carrier projection for the producer-backed structure
 * start origins. It owns no generation: every fact comes from the already landed family producer.
 */
final class Mc263StartOriginSupport {
    private Mc263StartOriginSupport() { }

    /**
     * Validates one coordinator attempt against the family it is being routed to. The locate
     * position is only required to resolve to the attempt's own chunk because each pinned set
     * carries its own locate offset inside that chunk.
     */
    static void requireAttempt(AttemptContext context, String setKey, String structureKey,
            String label) {
        Objects.requireNonNull(context, label + " attempt context");
        if (!setKey.equals(context.setKey()) || !structureKey.equals(context.structureKey())) {
            throw new IllegalArgumentException(label + " attempt targets another structure");
        }
        if (context.attemptIndex() < 0 || context.weight() <= 0 || context.priorReferences() < 0) {
            throw new IllegalArgumentException(label + " attempt is not pinned 26.3");
        }
        Mc263StructureSetStartPlanner.BlockPos locate = Objects.requireNonNull(
                context.locatePos(), label + " attempt locate position");
        if (Math.floorDiv(locate.x(), 16) != context.chunkX()
                || Math.floorDiv(locate.z(), 16) != context.chunkZ()) {
            throw new IllegalArgumentException(label + " attempt locate position is not in chunk");
        }
    }

    static String startKey(String structureKey, int chunkX, int chunkZ) {
        return structureKey + "@" + chunkX + "," + chunkZ;
    }

    static BoundingBox box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    static Projection projection(String value) {
        return switch (value) {
            case "rigid", "RIGID" -> Projection.RIGID;
            case "terrain_matching", "TERRAIN_MATCHING" -> Projection.TERRAIN_MATCHING;
            default -> throw new IllegalArgumentException("unknown jigsaw projection: " + value);
        };
    }

    /** Builds one carrier piece whose facts must already agree with the producer's persisted NBT. */
    static Piece piece(String pieceType, BoundingBox boundingBox, boolean poolElement,
            Projection projection, int groundLevelDelta,
            List<Mc263StructureCarrier.Junction> junctions, byte[] persistedNbt) {
        return new Piece(pieceType, boundingBox, poolElement, projection, groundLevelDelta,
                junctions, new PiecePayload(persistedNbt));
    }

    static ChunkStarts chunkStarts(String structureKey, ValidStart start) {
        return new ChunkStarts(start.originChunkX(), start.originChunkZ(),
                List.of(new StartEntry(structureKey, start)));
    }
}
