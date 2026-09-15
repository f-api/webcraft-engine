package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkStarts;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Junction;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Projection;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import com.gameexpert.terrain.mc.structure.Mc263StructureStartDecisionCoordinator.AttemptContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Carrier {@code ValidStart} origin for pinned {@code minecraft:trail_ruins}.
 *
 * <p>The origin performs no generation of its own: it wires one coordinator
 * {@link AttemptContext} into the landed {@link Mc263TrailRuinsProducer} run and projects that
 * producer start — including the producer-owned official raw piece NBT emitted by
 * {@link Mc263TrailRuinsCarrier} — into one strict carrier start.</p>
 */
public final class Mc263TrailRuinsStartOrigin {
    public static final String STRUCTURE_SET = "minecraft:trail_ruins";
    public static final String STRUCTURE = "minecraft:trail_ruins";
    public static final String PIECE_TYPE = "minecraft:jigsaw";
    public static final String HEIGHTMAP = "WORLD_SURFACE_WG";
    /** Pinned {@code bury} terrain adaptation padding applied to the persisted start box. */
    public static final int TERRAIN_PADDING = 12;

    private Mc263TrailRuinsStartOrigin() { }

    /**
     * The single world fact the Trail Ruins producer consumes. The S3 planner must satisfy it from
     * its sampler-receipt-backed {@code WorldAccess}; nothing else is read for this family.
     */
    public interface WorldAccess {
        boolean supportsWorldSurfaceWg();
        int worldSurfaceWg(int blockX, int blockZ);
    }

    /** Immutable origin output; an out-of-world start retains neither carrier nor start chunk. */
    public static final class OriginResult {
        private final boolean valid;
        private final Mc263TrailRuinsProducer.Start producerStart;
        private final ChunkStarts startChunk;

        private OriginResult(boolean valid, Mc263TrailRuinsProducer.Start producerStart,
                ChunkStarts startChunk) {
            if (valid != (startChunk != null)) {
                throw new IllegalArgumentException("valid trail ruins result/start mismatch");
            }
            this.valid = valid;
            this.producerStart = producerStart;
            this.startChunk = startChunk;
        }

        public boolean valid() { return valid; }
        public Mc263TrailRuinsProducer.Start producerStart() { return producerStart; }
        public ChunkStarts startChunk() { return startChunk; }
    }

    /** Runs the pinned producer for one attempt and carries its accepted start. */
    public static OriginResult originate(AttemptContext context, WorldAccess world) {
        Mc263StartOriginSupport.requireAttempt(context, STRUCTURE_SET, STRUCTURE, "trail ruins");
        Objects.requireNonNull(world, "trail ruins start world");
        if (!world.supportsWorldSurfaceWg()) {
            throw new UnsupportedOperationException(
                    "complete trail ruins start world capabilities are required");
        }

        Mc263TrailRuinsProducer.Start start = Mc263TrailRuinsProducer.pinned().generate(
                context.worldSeed(), context.chunkX(), context.chunkZ(),
                new ProducerHeights(world));
        if (start.empty()) return new OriginResult(false, start, null);
        return new OriginResult(true, start,
                Mc263StartOriginSupport.chunkStarts(STRUCTURE, carry(start,
                        context.priorReferences())));
    }

    /** Projects one already generated producer start into the strict carrier start. */
    public static ValidStart carry(Mc263TrailRuinsProducer.Start start, int references) {
        Objects.requireNonNull(start, "trail ruins producer start");
        if (start.empty()) throw new IllegalArgumentException("empty trail ruins producer start");
        List<Piece> pieces = new ArrayList<>(start.pieces().size());
        for (Mc263TrailRuinsProducer.Piece piece : start.pieces()) {
            pieces.add(Mc263StartOriginSupport.piece(PIECE_TYPE, box(piece.box()), true,
                    Projection.RIGID, piece.groundLevelDelta(), junctions(piece),
                    Mc263TrailRuinsCarrier.rawPieceNbt(piece)));
        }
        return new ValidStart(
                Mc263StartOriginSupport.startKey(STRUCTURE, start.chunkX(), start.chunkZ()),
                start.chunkX(), start.chunkZ(), references,
                inflate(box(start.aggregateBox()), TERRAIN_PADDING), pieces);
    }

    /** Exact producer-owned persisted structure-start NBT for the carried start. */
    public static byte[] persistedStartNbt(Mc263TrailRuinsProducer.Start start) {
        return Mc263TrailRuinsCarrier.rawStructureStartNbt(start);
    }

    private static List<Junction> junctions(Mc263TrailRuinsProducer.Piece piece) {
        List<Junction> junctions = new ArrayList<>(piece.junctions().size());
        for (Mc263TrailRuinsProducer.Junction junction : piece.junctions()) {
            junctions.add(new Junction(junction.sourceX(), junction.sourceGroundY(),
                    junction.sourceZ(), junction.deltaY(),
                    Mc263StartOriginSupport.projection(junction.destinationProjection())));
        }
        return junctions;
    }

    private static BoundingBox inflate(BoundingBox source, int amount) {
        return Mc263StartOriginSupport.box(Math.subtractExact(source.minX(), amount),
                Math.subtractExact(source.minY(), amount),
                Math.subtractExact(source.minZ(), amount),
                Math.addExact(source.maxX(), amount), Math.addExact(source.maxY(), amount),
                Math.addExact(source.maxZ(), amount));
    }

    private static BoundingBox box(Mc263TrailRuinsProducer.Box source) {
        return Mc263StartOriginSupport.box(source.minX(), source.minY(), source.minZ(),
                source.maxX(), source.maxY(), source.maxZ());
    }

    private record ProducerHeights(WorldAccess world)
            implements Mc263TrailRuinsProducer.HeightAccess {
        @Override public boolean supportsWorldSurfaceHeight() {
            return world.supportsWorldSurfaceWg();
        }
        @Override public int worldSurfaceHeight(int blockX, int blockZ) {
            return world.worldSurfaceWg(blockX, blockZ);
        }
    }
}
