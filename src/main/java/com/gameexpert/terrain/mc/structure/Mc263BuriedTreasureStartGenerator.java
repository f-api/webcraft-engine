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

/** Exact dormant start generator for pinned {@code minecraft:buried_treasure}. */
public final class Mc263BuriedTreasureStartGenerator {
    public static final String STRUCTURE_SET = "minecraft:buried_treasures";
    public static final String STRUCTURE = "minecraft:buried_treasure";
    public static final int PIECE_Y = 90;

    private Mc263BuriedTreasureStartGenerator() {}

    /** Capability declarations are pure; both are checked before either live query. */
    public interface WorldAccess {
        boolean supportsOceanFloorWg();
        boolean supportsValidBiomeTest();
        int oceanFloorWg(int blockX, int blockZ);
        boolean isValidBiome(int blockX, int blockY, int blockZ);
    }

    public record GenerationResult(boolean valid, ChunkStarts startChunk) {
        public GenerationResult {
            if (valid != (startChunk != null)) {
                throw new IllegalArgumentException("valid buried-treasure result/start mismatch");
            }
        }

        public static GenerationResult invalid() { return new GenerationResult(false, null); }
    }

    /**
     * Mirrors {@code onTopOfChunkCenter(OCEAN_FLOOR_WG)} and then creates the one official
     * {@code minecraft:btp} piece at local {@code (9,90,9)}. No RNG is created or consumed.
     */
    public static GenerationResult generate(AttemptContext context, WorldAccess world) {
        Objects.requireNonNull(context, "buried-treasure attempt context");
        Objects.requireNonNull(world, "buried-treasure start world");
        validateAttempt(context);
        if (!world.supportsOceanFloorWg() || !world.supportsValidBiomeTest()) {
            throw new UnsupportedOperationException(
                    "complete buried-treasure start world capabilities are required");
        }

        int chunkBlockX = Math.multiplyExact(context.chunkX(), 16);
        int chunkBlockZ = Math.multiplyExact(context.chunkZ(), 16);
        int centerX = Math.addExact(chunkBlockX, 8);
        int centerZ = Math.addExact(chunkBlockZ, 8);
        int biomeY = world.oceanFloorWg(centerX, centerZ);
        if (!world.isValidBiome(centerX, biomeY, centerZ)) return GenerationResult.invalid();

        int pieceX = Math.addExact(chunkBlockX, 9);
        int pieceZ = Math.addExact(chunkBlockZ, 9);
        BoundingBox box = new BoundingBox(pieceX, PIECE_Y, pieceZ,
                pieceX, PIECE_Y, pieceZ);
        Piece piece = new Piece(Mc263BuriedTreasurePieceExecutor.PIECE_TYPE, box, false,
                Projection.NOT_APPLICABLE, 0, List.of(),
                new PiecePayload(Mc263BuriedTreasureCarrierBridge.officialNbt(box)));
        ValidStart start = new ValidStart(STRUCTURE + "@" + context.chunkX() + ","
                + context.chunkZ(), context.chunkX(), context.chunkZ(),
                context.priorReferences(), box, List.of(piece));
        return new GenerationResult(true, new ChunkStarts(context.chunkX(), context.chunkZ(),
                List.of(new StartEntry(STRUCTURE, start))));
    }

    /** Validates one persisted valid start against the generator's exact current-version output. */
    public static void validatePersisted(ValidStart start) {
        Objects.requireNonNull(start, "buried-treasure persisted start");
        if (!start.startKey().equals(STRUCTURE + "@" + start.originChunkX() + ","
                + start.originChunkZ()) || start.orderedPieces().size() != 1) {
            throw new IllegalArgumentException("noncanonical buried-treasure persisted start");
        }
        int expectedX = Math.addExact(Math.multiplyExact(start.originChunkX(), 16), 9);
        int expectedZ = Math.addExact(Math.multiplyExact(start.originChunkZ(), 16), 9);
        BoundingBox expectedBox = new BoundingBox(expectedX, PIECE_Y, expectedZ,
                expectedX, PIECE_Y, expectedZ);
        Piece piece = start.orderedPieces().getFirst();
        if (!start.adjustedBoundingBox().equals(expectedBox)
                || !piece.boundingBox().equals(expectedBox)
                || !piece.pieceType().equals(Mc263BuriedTreasurePieceExecutor.PIECE_TYPE)
                || piece.poolElement() || piece.projection() != Projection.NOT_APPLICABLE
                || piece.groundLevelDelta() != 0 || !piece.junctions().isEmpty()
                || !java.util.Arrays.equals(piece.persistedPayload().binaryNbtCompound(),
                        Mc263BuriedTreasureCarrierBridge.officialNbt(expectedBox))) {
            throw new IllegalArgumentException("noncanonical buried-treasure persisted start");
        }
    }

    private static void validateAttempt(AttemptContext context) {
        int locateX = Math.addExact(Math.multiplyExact(context.chunkX(), 16), 9);
        int locateZ = Math.addExact(Math.multiplyExact(context.chunkZ(), 16), 9);
        if (!STRUCTURE_SET.equals(context.setKey()) || !STRUCTURE.equals(context.structureKey())
                || context.attemptIndex() != 0 || context.weight() != 1
                || context.priorReferences() < 0
                || context.locatePos().x() != locateX || context.locatePos().y() != 0
                || context.locatePos().z() != locateZ) {
            throw new IllegalArgumentException("buried-treasure attempt is not pinned 26.3");
        }
    }
}
