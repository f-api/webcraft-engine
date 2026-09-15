package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkStarts;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import com.gameexpert.terrain.mc.structure.Mc263StructureStartDecisionCoordinator.AttemptContext;
import com.gameexpert.terrain.mc.structure.Mc263TrialChambersProducer.Publisher;
import com.gameexpert.terrain.mc.structure.Mc263TrialChambersProducer.Start;
import com.gameexpert.terrain.mc.structure.Mc263TrialChambersProducer.WorldAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Start origin adapter for the pinned {@code minecraft:trial_chambers} set: it wires one
 * coordinator {@link AttemptContext} into the landed {@link Mc263TrialChambersProducer} run and
 * carries the start that producer already encoded.
 */
public final class Mc263TrialChambersStartOrigin {
    public static final String STRUCTURE_SET = "minecraft:trial_chambers";
    public static final String STRUCTURE = Mc263TrialChambersProducer.STRUCTURE_KEY;

    private Mc263TrialChambersStartOrigin() { }

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
     * <p>The rigid Trial Chambers graph performs no terrain projection; the S3 planner must only
     * supply the build height boundary through {@code world}.</p>
     */
    public static OriginResult originate(AttemptContext context, WorldAccess world,
            Publisher publisher) {
        Mc263StartOriginSupport.requireAttempt(context, STRUCTURE_SET, STRUCTURE,
                "trial chambers");
        Start start = Mc263TrialChambersProducer.pinned().generate(context.worldSeed(),
                context.chunkX(), context.chunkZ(), world, publisher);
        return new OriginResult(start, Mc263StartOriginSupport.chunkStarts(STRUCTURE,
                carry(start, context.priorReferences())));
    }

    /** Projects one already generated producer start into the strict carrier start. */
    public static ValidStart carry(Start start, int references) {
        Objects.requireNonNull(start, "trial chambers producer start");
        List<byte[]> persisted = new ArrayList<>(start.carrier().pieces().size());
        for (Mc263TrialChambersProducer.BinaryNbt piece : start.carrier().pieces()) {
            persisted.add(piece.bytes());
        }
        return Mc263JigsawStartOriginBridge.carry(STRUCTURE, start.executionPlan(), persisted,
                Mc263StartOriginSupport.box(start.aggregateBoundingBox().minX(),
                        start.aggregateBoundingBox().minY(), start.aggregateBoundingBox().minZ(),
                        start.aggregateBoundingBox().maxX(), start.aggregateBoundingBox().maxY(),
                        start.aggregateBoundingBox().maxZ()),
                start.chunkX(), start.chunkZ(), references);
    }
}
