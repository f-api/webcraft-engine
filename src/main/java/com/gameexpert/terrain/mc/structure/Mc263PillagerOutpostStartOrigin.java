package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263PillagerOutpostProducer.Publisher;
import com.gameexpert.terrain.mc.structure.Mc263PillagerOutpostProducer.Start;
import com.gameexpert.terrain.mc.structure.Mc263PillagerOutpostProducer.WorldAccess;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkStarts;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.RawStartPayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import com.gameexpert.terrain.mc.structure.Mc263StructureStartDecisionCoordinator.AttemptContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Start origin adapter for the pinned {@code minecraft:pillager_outposts} set: it wires one
 * coordinator {@link AttemptContext} into the landed {@link Mc263PillagerOutpostProducer} run and
 * carries the start that producer already encoded.
 */
public final class Mc263PillagerOutpostStartOrigin {
    public static final String STRUCTURE_SET = "minecraft:pillager_outposts";
    public static final String STRUCTURE = Mc263PillagerOutpostProducer.STRUCTURE_KEY;

    private Mc263PillagerOutpostStartOrigin() { }

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
     * <p>The S3 planner must supply {@code world} with WORLD_SURFACE_WG base heights and the build
     * height boundary; {@link AttemptContext} carries neither fact.</p>
     */
    public static OriginResult originate(AttemptContext context, WorldAccess world,
            Publisher publisher) {
        Mc263StartOriginSupport.requireAttempt(context, STRUCTURE_SET, STRUCTURE,
                "pillager outpost");
        Start start = Mc263PillagerOutpostProducer.pinned().generate(context.worldSeed(),
                context.chunkX(), context.chunkZ(), world, publisher);
        return new OriginResult(start, Mc263StartOriginSupport.chunkStarts(STRUCTURE,
                carry(start, context.priorReferences())));
    }

    /** Projects one already generated producer start into the strict carrier start. */
    public static ValidStart carry(Start start, int references) {
        Objects.requireNonNull(start, "pillager outpost producer start");
        List<Mc263PillagerOutpostProducer.BinaryNbt> encoded = start.carrier().pieces();
        List<byte[]> persisted = new ArrayList<>(encoded.size());
        for (Mc263PillagerOutpostProducer.BinaryNbt piece : encoded) {
            // The root piece keeps the appended OUT263A1 restart authority the persisted
            // settlement reloads on every FEATURES visit. Official parity is on the stripped
            // form, which Mc263PillagerOutpostPersistedAuthority.strippedRootPiece recovers
            // byte-for-byte and the start-graph adapter test pins against the official probe.
            persisted.add(piece.bytes());
        }
        return Mc263JigsawStartOriginBridge.carry(STRUCTURE, start.executionPlan(), persisted,
                Mc263StartOriginSupport.box(start.aggregateBoundingBox().minX(),
                        start.aggregateBoundingBox().minY(), start.aggregateBoundingBox().minZ(),
                        start.aggregateBoundingBox().maxX(), start.aggregateBoundingBox().maxY(),
                        start.aggregateBoundingBox().maxZ()),
                start.chunkX(), start.chunkZ(), references);
    }

    /** Preserves the producer-authenticated raw start and its exact one-reference successor. */
    public static RawStartPayload rawStartPayload(Start start, ValidStart carried) {
        Objects.requireNonNull(start, "pillager outpost producer start");
        Objects.requireNonNull(carried, "pillager outpost carried start");
        if (carried.originChunkX() != start.chunkX()
                || carried.originChunkZ() != start.chunkZ()) {
            throw new IllegalArgumentException("Pillager Outpost raw start identity drift");
        }
        byte[] predecessor = start.carrier().structureStart().bytes();
        byte[] successor = Mc263PillagerOutpostPersistedAuthority.referenceSuccessor(predecessor);
        return new RawStartPayload(STRUCTURE, carried.startKey(), carried.originChunkX(),
                carried.originChunkZ(), predecessor, successor);
    }
}
