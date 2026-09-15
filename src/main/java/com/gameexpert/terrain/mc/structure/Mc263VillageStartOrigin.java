package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.LegacyRand;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkStarts;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.RawStartPayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import com.gameexpert.terrain.mc.structure.Mc263StructureStartDecisionCoordinator.AttemptContext;
import com.gameexpert.terrain.mc.structure.Mc263VillageProducer.Accepted;
import com.gameexpert.terrain.mc.structure.Mc263VillageProducer.Attempt;
import com.gameexpert.terrain.mc.structure.Mc263VillageProducer.BiomeAdmission;
import com.gameexpert.terrain.mc.structure.Mc263VillageProducer.Publisher;
import com.gameexpert.terrain.mc.structure.Mc263VillageProducer.Start;
import com.gameexpert.terrain.mc.structure.Mc263VillageProducer.WorldAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Start origin adapter for the pinned {@code minecraft:villages} set.
 *
 * <p>It wires one coordinator {@link AttemptContext} into the landed
 * {@link Mc263VillageProducer} run and carries the start that producer already encoded. The
 * caller RNG is created here because vanilla's {@code setLargeFeatureSeed} resets that object at
 * root initialization, so its incoming state cannot be observed.</p>
 */
public final class Mc263VillageStartOrigin {
    public static final String STRUCTURE_SET = "minecraft:villages";
    public static final Set<String> MEMBERS = Set.of("minecraft:village_plains",
            "minecraft:village_desert", "minecraft:village_savanna", "minecraft:village_snowy",
            "minecraft:village_taiga");

    private Mc263VillageStartOrigin() { }

    /** Immutable adapter output; a biome-rejected attempt retains the producer root receipt only. */
    public static final class OriginResult {
        private final Attempt attempt;
        private final ChunkStarts startChunk;

        private OriginResult(Attempt attempt, ChunkStarts startChunk) {
            this.attempt = attempt;
            this.startChunk = startChunk;
        }

        public boolean valid() { return startChunk != null; }
        public Attempt attempt() { return attempt; }
        public ChunkStarts startChunk() { return startChunk; }
    }

    /**
     * Runs one pinned family attempt.
     *
     * <p>The S3 planner must supply {@code world} (WORLD_SURFACE_WG base heights and the build
     * height boundary) and {@code biomes} (the actual biome at the root stub) from its
     * sampler-receipt-backed surface; {@link AttemptContext} carries neither fact.</p>
     */
    public static OriginResult originate(AttemptContext context, WorldAccess world,
            BiomeAdmission biomes, Publisher publisher) {
        Objects.requireNonNull(context, "village attempt context");
        if (!MEMBERS.contains(context.structureKey())) {
            throw new IllegalArgumentException("village attempt targets another structure");
        }
        Mc263StartOriginSupport.requireAttempt(context, STRUCTURE_SET, context.structureKey(),
                "village");
        Attempt attempt = Mc263VillageProducer.pinned().attempt(context.structureKey(),
                context.worldSeed(), context.chunkX(), context.chunkZ(),
                new LegacyRand(context.worldSeed()), world, biomes, publisher);
        if (!(attempt instanceof Accepted accepted)) return new OriginResult(attempt, null);
        ValidStart start = carry(accepted.start(), context.priorReferences());
        return new OriginResult(attempt,
                Mc263StartOriginSupport.chunkStarts(context.structureKey(), start));
    }

    /** Projects one already generated producer start into the strict carrier start. */
    public static ValidStart carry(Start start, int references) {
        Objects.requireNonNull(start, "village producer start");
        List<byte[]> persisted = new ArrayList<>(start.carrier().pieces().size());
        for (Mc263VillageProducer.BinaryNbt piece : start.carrier().pieces()) {
            persisted.add(piece.bytes());
        }
        return Mc263JigsawStartOriginBridge.carry(start.structureKey(), start.executionPlan(),
                persisted, Mc263StartOriginSupport.box(start.aggregateBoundingBox().minX(),
                        start.aggregateBoundingBox().minY(), start.aggregateBoundingBox().minZ(),
                        start.aggregateBoundingBox().maxX(), start.aggregateBoundingBox().maxY(),
                        start.aggregateBoundingBox().maxZ()),
                start.chunkX(), start.chunkZ(), references);
    }

    /**
     * The producer-authenticated STRRAW01 predecessor and its exact one-reference successor.
     *
     * <p>Both blobs are the {@link Mc263VillageProducer.Carrier} bytes this very start already
     * encoded; nothing is re-encoded or synthesized here.</p>
     */
    public static RawStartPayload rawStartPayload(Start start, ValidStart carried) {
        Objects.requireNonNull(start, "village producer start");
        Objects.requireNonNull(carried, "village carried start");
        if (carried.originChunkX() != start.chunkX() || carried.originChunkZ() != start.chunkZ()
                || !carried.startKey().equals(Mc263StartOriginSupport.startKey(
                        start.structureKey(), start.chunkX(), start.chunkZ()))) {
            throw new IllegalArgumentException("Village raw start identity drift");
        }
        return new RawStartPayload(start.structureKey(), carried.startKey(),
                carried.originChunkX(), carried.originChunkZ(),
                start.carrier().structureStart().bytes(),
                start.carrier().mutableSuccessorAfterOneReference().bytes());
    }
}
