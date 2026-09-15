package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkStarts;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ProducerEdge;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ProducerGraphPayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.RawStartPayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Projection;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import com.gameexpert.terrain.mc.structure.Mc263StructureStartDecisionCoordinator.AttemptContext;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionProducer.Result;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionProducer.Start;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionProducer.LocatedWorldAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Start origin adapter for the pinned {@code minecraft:woodland_mansions} set: it wires one
 * coordinator {@link AttemptContext} into the landed {@link Mc263WoodlandMansionProducer} run and
 * carries the start that producer already encoded. Mansion pieces are template pieces, so they
 * carry no jigsaw pool facts.
 */
public final class Mc263WoodlandMansionStartOrigin {
    public static final String STRUCTURE_SET = "minecraft:woodland_mansions";
    public static final String STRUCTURE = Mc263WoodlandMansionProducer.STRUCTURE_KEY;
    public static final String PIECE_TYPE = "minecraft:wmp";

    private Mc263WoodlandMansionStartOrigin() { }

    /** Immutable adapter output; a below-minimum generation Y retains the rejected result only. */
    public static final class OriginResult {
        private final Result producerResult;
        private final ChunkStarts startChunk;

        private OriginResult(Result producerResult, ChunkStarts startChunk) {
            this.producerResult = producerResult;
            this.startChunk = startChunk;
        }

        public boolean valid() { return startChunk != null; }
        public Result producerResult() { return producerResult; }
        public ChunkStarts startChunk() { return startChunk; }
    }

    /**
     * Runs the pinned producer for one attempt.
     *
     * <p>The S3 planner must supply {@code world} with the four WORLD_SURFACE_WG corner base
     * heights the mansion reads; {@link AttemptContext} carries no world fact.</p>
     */
    public static OriginResult originate(AttemptContext context, LocatedWorldAccess world) {
        Mc263StartOriginSupport.requireAttempt(context, STRUCTURE_SET, STRUCTURE,
                "woodland mansion");
        Result result = Mc263WoodlandMansionProducer.generateLocated(STRUCTURE, context.worldSeed(),
                context.chunkX(), context.chunkZ(), world);
        if (!(result instanceof Start start)) return new OriginResult(result, null);
        return new OriginResult(result, Mc263StartOriginSupport.chunkStarts(STRUCTURE,
                carry(start, context.priorReferences())));
    }

    /** Projects one already generated producer start into the strict carrier start. */
    public static ValidStart carry(Start start, int references) {
        Objects.requireNonNull(start, "woodland mansion producer start");
        List<Mc263WoodlandMansionProducer.Piece> planned = start.pieces();
        List<Mc263WoodlandMansionProducer.BinaryNbt> persisted =
                start.successor().carrier().pieces();
        if (planned.size() != persisted.size()) {
            throw new IllegalArgumentException("woodland mansion carrier piece count drift");
        }
        List<Piece> pieces = new ArrayList<>(planned.size());
        for (int ordinal = 0; ordinal < planned.size(); ordinal++) {
            Mc263WoodlandMansionGrammar.Box box = planned.get(ordinal).boundingBox();
            pieces.add(Mc263StartOriginSupport.piece(PIECE_TYPE,
                    Mc263StartOriginSupport.box(box.minX(), box.minY(), box.minZ(),
                            box.maxX(), box.maxY(), box.maxZ()),
                    false, Projection.NOT_APPLICABLE, 0, List.of(),
                    persisted.get(ordinal).bytes()));
        }
        Mc263WoodlandMansionGrammar.Box aggregate = start.aggregateBoundingBox();
        return new ValidStart(Mc263StartOriginSupport.startKey(STRUCTURE,
                start.request().chunkX(), start.request().chunkZ()),
                start.request().chunkX(), start.request().chunkZ(), references,
                Mc263StartOriginSupport.box(aggregate.minX(), aggregate.minY(), aggregate.minZ(),
                        aggregate.maxX(), aggregate.maxY(), aggregate.maxZ()),
                pieces);
    }

    /**
     * The producer-authenticated STRRAW01 predecessor and its exact one-reference successor.
     *
     * <p>Both blobs are the {@link Mc263WoodlandMansionProducer.PersistedCarrier} bytes this very
     * start already encoded, which is exactly what
     * {@code Mc263WoodlandMansionProductionAdapter} re-derives and compares byte-for-byte.</p>
     */
    public static RawStartPayload rawStartPayload(Start start, ValidStart carried) {
        requireIdentity(start, carried);
        return new RawStartPayload(STRUCTURE, carried.startKey(), carried.originChunkX(),
                carried.originChunkZ(), start.successor().carrier().structureStart().bytes(),
                start.successor().carrier().mutableSuccessorAfterOneReference().bytes());
    }

    /**
     * The producer's own encounter graph: one edge per non-root piece, in placement order, whose
     * source is the root the Mansion grammar expands every piece from and whose selected pool and
     * resolved alias are that piece's own template key. This is the exact fact
     * {@code Mc263WoodlandMansionProductionAdapter#preflightPersisted} validates.
     */
    public static ProducerGraphPayload producerGraphPayload(Start start, ValidStart carried) {
        requireIdentity(start, carried);
        List<ProducerEdge> edges = new ArrayList<>(start.pieces().size() - 1);
        for (int ordinal = 1; ordinal < start.pieces().size(); ordinal++) {
            String template = start.pieces().get(ordinal).templateKey();
            edges.add(new ProducerEdge(0, ordinal, template, template));
        }
        return new ProducerGraphPayload(STRUCTURE, carried.startKey(), carried.originChunkX(),
                carried.originChunkZ(), edges);
    }

    private static void requireIdentity(Start start, ValidStart carried) {
        Objects.requireNonNull(start, "woodland mansion producer start");
        Objects.requireNonNull(carried, "woodland mansion carried start");
        if (carried.originChunkX() != start.request().chunkX()
                || carried.originChunkZ() != start.request().chunkZ()) {
            throw new IllegalArgumentException("Woodland Mansion sidecar identity drift");
        }
    }
}
