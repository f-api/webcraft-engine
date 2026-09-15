package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.feature.Mc263StructureIndexReceipt;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Kind;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.PieceKind;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkReferences;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkStarts;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.PiecePayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Projection;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ReferenceSet;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Registry;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.StartEntry;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.StructureDefinition;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.TerrainAdjustment;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import com.gameexpert.terrain.mc.structure.Mc263StructureStartDecisionCoordinator.AttemptContext;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Exact dormant start/reference generator for pinned {@code minecraft:swamp_hut}. */
public final class Mc263SwampHutStartGenerator {
    public static final String STRUCTURE_SET = "minecraft:swamp_huts";
    public static final String STRUCTURE = "minecraft:swamp_hut";
    public static final String PIECE_TYPE = "minecraft:tesh";
    public static final int DECORATION_STEP = 4;
    public static final int STEP_INDEX = 38;
    public static final int REGISTRY_ORDINAL = 43;

    private Mc263SwampHutStartGenerator() { }

    /** Capability declarations are pure and are checked before either live world query. */
    public interface WorldAccess {
        boolean supportsWorldSurfaceWg();
        boolean supportsValidBiomeTest();
        int worldSurfaceWg(int blockX, int blockZ);
        boolean isValidBiome(int blockX, int blockY, int blockZ);
    }

    /** Immutable valid result; a failed biome gate publishes no planning or persisted facts. */
    public static final class GenerationResult {
        private final boolean valid;
        private final Mc263HardcodedStructureCarrier hardcodedCarrier;
        private final ChunkStarts startChunk;
        private final ChunkReferences references;

        private GenerationResult(boolean valid,
                Mc263HardcodedStructureCarrier hardcodedCarrier,
                ChunkStarts startChunk, ChunkReferences references) {
            if (valid != (hardcodedCarrier != null)
                    || valid != (startChunk != null) || valid != (references != null)) {
                throw new IllegalArgumentException("valid swamp-hut result/facts mismatch");
            }
            this.valid = valid;
            this.hardcodedCarrier = hardcodedCarrier;
            this.startChunk = startChunk;
            this.references = references;
        }

        public boolean valid() { return valid; }
        public Mc263HardcodedStructureCarrier hardcodedCarrier() { return hardcodedCarrier; }
        public ChunkStarts startChunk() { return startChunk; }
        public ChunkReferences references() { return references; }

        public static GenerationResult invalid() {
            return new GenerationResult(false, null, null, null);
        }
    }

    /**
     * Applies the pinned center {@code WORLD_SURFACE_WG} biome gate and creates the one official
     * swamp-hut piece plus its same-chunk reference. Geometry and initial mutable NBT come only
     * from the hardcoded carrier and {@link Mc263SwampHutProgram}.
     */
    public static GenerationResult generate(
            AttemptContext context, Registry registry, WorldAccess world) {
        Objects.requireNonNull(context, "swamp-hut attempt context");
        Objects.requireNonNull(registry, "swamp-hut structure registry");
        Objects.requireNonNull(world, "swamp-hut start world");
        validateAttempt(context);
        validateRegistry(registry);
        if (!world.supportsWorldSurfaceWg() || !world.supportsValidBiomeTest()) {
            throw new UnsupportedOperationException(
                    "complete swamp-hut start world capabilities are required");
        }

        int centerX = Math.addExact(Math.multiplyExact(context.chunkX(), 16), 8);
        int centerZ = Math.addExact(Math.multiplyExact(context.chunkZ(), 16), 8);
        int biomeY = world.worldSurfaceWg(centerX, centerZ);
        if (!world.isValidBiome(centerX, biomeY, centerZ)) return GenerationResult.invalid();

        Mc263HardcodedStructureCarrier hardcoded = Mc263HardcodedStructureCarrier.plan(
                Kind.SWAMP_HUT, context.worldSeed(), context.chunkX(), context.chunkZ());
        requireCanonicalCarrier(hardcoded);
        BoundingBox box = box(hardcoded);
        Piece piece = new Piece(PIECE_TYPE, box, false, Projection.NOT_APPLICABLE, 0, List.of(),
                new PiecePayload(Mc263SwampHutProgram.canonicalPieceNbt(hardcoded)));
        ValidStart start = new ValidStart(startKey(context.chunkX(), context.chunkZ()),
                context.chunkX(), context.chunkZ(), context.priorReferences(), box,
                List.of(piece));
        ChunkStarts starts = new ChunkStarts(context.chunkX(), context.chunkZ(),
                List.of(new StartEntry(STRUCTURE, start)));
        ChunkReferences references = new ChunkReferences(context.chunkX(), context.chunkZ(),
                List.of(new ReferenceSet(STRUCTURE, List.of(Mc263StructureCarrier.packChunk(
                        context.chunkX(), context.chunkZ())))));
        return new GenerationResult(true, hardcoded, starts, references);
    }

    /** Rejects every narrowed or reordered registry before start/reference facts are produced. */
    public static void validateRegistry(Registry registry) {
        Objects.requireNonNull(registry, "swamp-hut structure registry");
        List<Mc263StructureIndexReceipt.Entry> pinned = Mc263StructureIndexReceipt.entries();
        List<StructureDefinition> definitions = registry.definitions();
        if (definitions.size() != pinned.size()) {
            throw new IllegalArgumentException("STR263C1 registry is not the pinned 52 entries");
        }
        for (int ordinal = 0; ordinal < pinned.size(); ordinal++) {
            Mc263StructureIndexReceipt.Entry entry = pinned.get(ordinal);
            StructureDefinition definition = definitions.get(ordinal);
            if (definition.registryOrdinal() != ordinal
                    || !definition.structureId().equals(entry.key())
                    || definition.decorationStep() != entry.step()) {
                throw new IllegalArgumentException(
                        "STR263C1 registry/schedule mismatch at " + ordinal);
            }
        }
        StructureDefinition swamp = definitions.get(REGISTRY_ORDINAL);
        if (!swamp.structureId().equals(STRUCTURE)
                || swamp.decorationStep() != DECORATION_STEP
                || swamp.terrainAdjustment() != TerrainAdjustment.NONE
                || !Mc263StructureIndexReceipt.step(DECORATION_STEP).get(STEP_INDEX)
                        .equals(pinned.get(REGISTRY_ORDINAL))) {
            throw new IllegalArgumentException("swamp-hut registry slot is not pinned 26.3");
        }
    }

    /** Validates one unplaced persisted start against the exact initial carrier facts. */
    public static void validatePersisted(ValidStart start,
            Mc263HardcodedStructureCarrier hardcoded) {
        Objects.requireNonNull(start, "swamp-hut persisted start");
        requireCanonicalCarrier(hardcoded);
        BoundingBox expectedBox = box(hardcoded);
        if (!start.startKey().equals(startKey(hardcoded.chunkX(), hardcoded.chunkZ()))
                || start.originChunkX() != hardcoded.chunkX()
                || start.originChunkZ() != hardcoded.chunkZ()
                || !start.adjustedBoundingBox().equals(expectedBox)
                || start.orderedPieces().size() != 1) {
            throw new IllegalArgumentException("noncanonical swamp-hut persisted start");
        }
        Piece piece = start.orderedPieces().getFirst();
        if (!piece.pieceType().equals(PIECE_TYPE)
                || !piece.boundingBox().equals(expectedBox) || piece.poolElement()
                || piece.projection() != Projection.NOT_APPLICABLE
                || piece.groundLevelDelta() != 0 || !piece.junctions().isEmpty()
                || !Arrays.equals(piece.persistedPayload().binaryNbtCompound(),
                        Mc263SwampHutProgram.canonicalPieceNbt(hardcoded))) {
            throw new IllegalArgumentException("noncanonical swamp-hut persisted piece");
        }
    }

    private static void validateAttempt(AttemptContext context) {
        int locateX = Math.multiplyExact(context.chunkX(), 16);
        int locateZ = Math.multiplyExact(context.chunkZ(), 16);
        if (!STRUCTURE_SET.equals(context.setKey()) || !STRUCTURE.equals(context.structureKey())
                || context.attemptIndex() != 0 || context.weight() != 1
                || context.priorReferences() < 0
                || context.locatePos().x() != locateX || context.locatePos().y() != 0
                || context.locatePos().z() != locateZ) {
            throw new IllegalArgumentException("swamp-hut attempt is not pinned 26.3");
        }
    }

    private static void requireCanonicalCarrier(Mc263HardcodedStructureCarrier hardcoded) {
        Objects.requireNonNull(hardcoded, "swamp-hut hardcoded carrier");
        if (hardcoded.kind() != Kind.SWAMP_HUT
                || hardcoded.orderedPieces().size() != 1
                || hardcoded.orderedPieces().getFirst().kind() != PieceKind.SWAMP_HUT
                || hardcoded.orderedPieces().getFirst().template() != null
                || !isCardinal(hardcoded.rotation())) {
            throw new IllegalArgumentException("noncanonical swamp-hut hardcoded carrier shape");
        }
        Mc263HardcodedStructureCarrier canonical = Mc263HardcodedStructureCarrier.plan(
                Kind.SWAMP_HUT, hardcoded.worldSeed(), hardcoded.chunkX(), hardcoded.chunkZ());
        if (!Arrays.equals(hardcoded.encodeCanonical(), canonical.encodeCanonical())) {
            throw new IllegalArgumentException("noncanonical initial swamp-hut carrier facts");
        }
    }

    private static boolean isCardinal(Rotation rotation) {
        return rotation == Rotation.NORTH || rotation == Rotation.EAST
                || rotation == Rotation.SOUTH || rotation == Rotation.WEST;
    }

    private static BoundingBox box(Mc263HardcodedStructureCarrier hardcoded) {
        var source = hardcoded.boundingBox();
        return new BoundingBox(source.minX(), source.minY(), source.minZ(),
                source.maxX(), source.maxY(), source.maxZ());
    }

    private static String startKey(int chunkX, int chunkZ) {
        return STRUCTURE + "@" + chunkX + "," + chunkZ;
    }
}
