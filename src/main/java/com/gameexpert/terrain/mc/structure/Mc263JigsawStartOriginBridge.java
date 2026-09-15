package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.ExecutionPlan;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.PiecePlacement;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Junction;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Projection;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;

import java.util.ArrayList;
import java.util.List;

/**
 * Projects one already generated generic jigsaw execution plan and its producer-owned persisted
 * piece NBT into a carrier {@link ValidStart}. No generation, ordering or NBT is produced here.
 */
final class Mc263JigsawStartOriginBridge {
    static final String PIECE_TYPE = "minecraft:jigsaw";

    private Mc263JigsawStartOriginBridge() { }

    static ValidStart carry(String structureKey, ExecutionPlan plan, List<byte[]> persistedPieces,
            BoundingBox adjustedBoundingBox, int chunkX, int chunkZ, int references) {
        if (plan.pieces().size() != persistedPieces.size()) {
            throw new IllegalArgumentException(structureKey + " carrier piece count drift");
        }
        List<Piece> pieces = new ArrayList<>(plan.pieces().size());
        for (int ordinal = 0; ordinal < plan.pieces().size(); ordinal++) {
            PiecePlacement placement = plan.pieces().get(ordinal);
            pieces.add(Mc263StartOriginSupport.piece(PIECE_TYPE,
                    Mc263StartOriginSupport.box(placement.bounds().minX(),
                            placement.bounds().minY(), placement.bounds().minZ(),
                            placement.bounds().maxX(), placement.bounds().maxY(),
                            placement.bounds().maxZ()),
                    true, projection(placement.projection()), placement.groundLevelDelta(),
                    junctions(placement), persistedPieces.get(ordinal)));
        }
        return new ValidStart(Mc263StartOriginSupport.startKey(structureKey, chunkX, chunkZ),
                chunkX, chunkZ, references, adjustedBoundingBox, pieces);
    }

    private static List<Junction> junctions(PiecePlacement placement) {
        List<Junction> junctions = new ArrayList<>(placement.junctions().size());
        for (Mc263JigsawStructureExecutor.Junction junction : placement.junctions()) {
            junctions.add(new Junction(junction.sourceX(), junction.sourceGroundY(),
                    junction.sourceZ(), junction.deltaY(),
                    projection(junction.destinationProjection())));
        }
        return junctions;
    }

    private static Projection projection(
            Mc263JigsawStructureBoundary.Projection projection) {
        return switch (projection) {
            case RIGID -> Projection.RIGID;
            case TERRAIN_MATCHING -> Projection.TERRAIN_MATCHING;
        };
    }
}
