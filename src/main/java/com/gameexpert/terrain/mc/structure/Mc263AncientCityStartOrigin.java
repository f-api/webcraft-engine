package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263AncientCityProducer.Publisher;
import com.gameexpert.terrain.mc.structure.Mc263AncientCityProducer.Start;
import com.gameexpert.terrain.mc.structure.Mc263AncientCityProducer.WorldAccess;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkStarts;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Junction;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Projection;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ProducerEdge;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ProducerGraphPayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.RawStartPayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import com.gameexpert.terrain.mc.structure.Mc263StructureStartDecisionCoordinator.AttemptContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Start origin adapter for the pinned {@code minecraft:ancient_cities} set: it wires one
 * coordinator {@link AttemptContext} into the landed {@link Mc263AncientCityProducer} run and
 * carries the start that producer already encoded.
 */
public final class Mc263AncientCityStartOrigin {
    public static final String STRUCTURE_SET = Mc263AncientCityProducer.STRUCTURE_SET_KEY;
    public static final String STRUCTURE = Mc263AncientCityProducer.STRUCTURE_KEY;

    private Mc263AncientCityStartOrigin() { }

    /** Immutable adapter output; the producer publishes before this projection. */
    public static final class OriginResult {
        private final Start producerStart;
        private final ChunkStarts startChunk;

        private OriginResult(Start producerStart, ChunkStarts startChunk) {
            this.producerStart = producerStart;
            this.startChunk = startChunk;
        }

        public boolean valid() { return startChunk != null; }
        public Start producerStart() { return producerStart; }
        public ChunkStarts startChunk() { return startChunk; }
    }

    /**
     * Runs the pinned producer for one attempt.
     *
     * <p>The rigid Ancient City graph performs no terrain projection; the S3 planner must supply
     * the build height boundary and the one deep-dark biome sample at the root stub through
     * {@code world}, because {@link AttemptContext} carries neither fact.</p>
     */
    public static OriginResult originate(AttemptContext context, WorldAccess world,
            Publisher publisher) {
        Mc263StartOriginSupport.requireAttempt(context, STRUCTURE_SET, STRUCTURE, "ancient city");
        Start start = Mc263AncientCityProducer.pinned().generate(context.worldSeed(),
                context.chunkX(), context.chunkZ(), world, publisher);
        return new OriginResult(start, Mc263StartOriginSupport.chunkStarts(STRUCTURE,
                carry(start, context.priorReferences())));
    }

    /** Projects one already generated producer start into the strict carrier start. */
    public static ValidStart carry(Start start, int references) {
        Objects.requireNonNull(start, "ancient city producer start");
        List<Mc263AncientCityProducer.Piece> planned = start.plan().pieces();
        List<Mc263AncientCityProducer.BinaryNbt> persisted = start.carrier().pieces();
        if (planned.size() != persisted.size()) {
            throw new IllegalArgumentException("ancient city carrier piece count drift");
        }
        List<Piece> pieces = new ArrayList<>(planned.size());
        for (int ordinal = 0; ordinal < planned.size(); ordinal++) {
            Mc263AncientCityProducer.Piece piece = planned.get(ordinal);
            pieces.add(Mc263StartOriginSupport.piece(Mc263JigsawStartOriginBridge.PIECE_TYPE,
                    Mc263StartOriginSupport.box(piece.boundingBox().minX(),
                            piece.boundingBox().minY(), piece.boundingBox().minZ(),
                            piece.boundingBox().maxX(), piece.boundingBox().maxY(),
                            piece.boundingBox().maxZ()),
                    true, projection(piece.projection()), piece.groundLevelDelta(),
                    junctions(piece), persisted.get(ordinal).bytes()));
        }
        return new ValidStart(
                Mc263StartOriginSupport.startKey(STRUCTURE, start.chunkX(), start.chunkZ()),
                start.chunkX(), start.chunkZ(), references,
                Mc263StartOriginSupport.box(start.aggregateBoundingBox().minX(),
                        start.aggregateBoundingBox().minY(), start.aggregateBoundingBox().minZ(),
                        start.aggregateBoundingBox().maxX(), start.aggregateBoundingBox().maxY(),
                        start.aggregateBoundingBox().maxZ()),
                pieces);
    }

    /** Exact producer-authenticated predecessor and mutable one-reference successor. */
    public static RawStartPayload rawStartPayload(Start start, ValidStart carried) {
        requireIdentity(start, carried);
        return new RawStartPayload(STRUCTURE, carried.startKey(), carried.originChunkX(),
                carried.originChunkZ(), start.carrier().structureStart().bytes(),
                start.carrier().structureStart().mutableSuccessor());
    }

    /** Exact accepted jigsaw edge order carried by the authenticated start graph. */
    public static ProducerGraphPayload producerGraphPayload(Start start, ValidStart carried) {
        requireIdentity(start, carried);
        List<ProducerEdge> edges = start.acceptedEdges().stream()
                .map(edge -> new ProducerEdge(edge.sourcePiece(), edge.targetPiece(),
                        edge.selectedPool(), edge.resolvedAlias()))
                .toList();
        return new ProducerGraphPayload(STRUCTURE, carried.startKey(), carried.originChunkX(),
                carried.originChunkZ(), edges);
    }

    private static void requireIdentity(Start start, ValidStart carried) {
        Objects.requireNonNull(start, "ancient city producer start");
        Objects.requireNonNull(carried, "ancient city carried start");
        if (carried.originChunkX() != start.chunkX()
                || carried.originChunkZ() != start.chunkZ()) {
            throw new IllegalArgumentException("Ancient City sidecar identity drift");
        }
    }

    private static List<Junction> junctions(Mc263AncientCityProducer.Piece piece) {
        List<Junction> junctions = new ArrayList<>(piece.junctions().size());
        for (Mc263AncientCityProducer.Junction junction : piece.junctions()) {
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
