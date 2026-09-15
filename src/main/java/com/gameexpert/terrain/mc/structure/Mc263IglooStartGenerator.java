package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Kind;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.PieceFact;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.PieceKind;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Template;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkStarts;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.PiecePayload;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Projection;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.StartEntry;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ValidStart;
import com.gameexpert.terrain.mc.structure.Mc263StructureStartDecisionCoordinator.AttemptContext;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Exact dormant start/reference generator for pinned {@code minecraft:igloo}. */
public final class Mc263IglooStartGenerator {
    public static final String STRUCTURE_SET = "minecraft:igloos";
    public static final String STRUCTURE = "minecraft:igloo";
    public static final String PIECE_TYPE = "minecraft:iglu";

    private Mc263IglooStartGenerator() { }

    /** Capability declarations are pure and must be checked before either live query. */
    public interface WorldAccess {
        boolean supportsWorldSurfaceWg();
        boolean supportsValidBiomeTest();
        int worldSurfaceWg(int blockX, int blockZ);
        boolean isValidBiome(int blockX, int blockY, int blockZ);
    }

    /** Immutable output; invalid biome results retain neither a start nor planning carrier. */
    public static final class GenerationResult {
        private final boolean valid;
        private final Mc263HardcodedStructureCarrier hardcodedCarrier;
        private final ChunkStarts startChunk;

        private GenerationResult(boolean valid,
                Mc263HardcodedStructureCarrier hardcodedCarrier, ChunkStarts startChunk) {
            if (valid != (hardcodedCarrier != null) || valid != (startChunk != null)) {
                throw new IllegalArgumentException("valid igloo result/start mismatch");
            }
            this.valid = valid;
            this.hardcodedCarrier = hardcodedCarrier;
            this.startChunk = startChunk;
        }

        public boolean valid() { return valid; }
        public Mc263HardcodedStructureCarrier hardcodedCarrier() { return hardcodedCarrier; }
        public ChunkStarts startChunk() { return startChunk; }

        public static GenerationResult invalid() {
            return new GenerationResult(false, null, null);
        }
    }

    /**
     * Mirrors the pinned on-top-of-chunk-center WORLD_SURFACE_WG biome gate, then projects the
     * repository-owned HCS263C1 template graph into one strict STR263C1 valid start. The structure
     * RNG is completely represented by the immutable hardcoded carrier; no caller RNG is used.
     */
    public static GenerationResult generate(AttemptContext context, WorldAccess world) {
        Objects.requireNonNull(context, "igloo attempt context");
        Objects.requireNonNull(world, "igloo start world");
        validateAttempt(context);
        if (!world.supportsWorldSurfaceWg() || !world.supportsValidBiomeTest()) {
            throw new UnsupportedOperationException(
                    "complete igloo start world capabilities are required");
        }

        int centerX = Math.addExact(Math.multiplyExact(context.chunkX(), 16), 8);
        int centerZ = Math.addExact(Math.multiplyExact(context.chunkZ(), 16), 8);
        int biomeY = world.worldSurfaceWg(centerX, centerZ);
        if (!world.isValidBiome(centerX, biomeY, centerZ)) return GenerationResult.invalid();

        Mc263HardcodedStructureCarrier hardcoded = Mc263HardcodedStructureCarrier.plan(
                Kind.IGLOO, context.worldSeed(), context.chunkX(), context.chunkZ());
        ArrayList<Piece> pieces = new ArrayList<>(hardcoded.orderedPieces().size());
        for (int index = 0; index < hardcoded.orderedPieces().size(); index++) {
            PieceFact fact = hardcoded.orderedPieces().get(index);
            pieces.add(piece(fact, canonicalPieceNbt(hardcoded, index, 0)));
        }
        BoundingBox aggregate = box(hardcoded.boundingBox(), 0);
        ValidStart start = new ValidStart(startKey(context.chunkX(), context.chunkZ()),
                context.chunkX(), context.chunkZ(), context.priorReferences(), aggregate, pieces);
        ChunkStarts starts = new ChunkStarts(context.chunkX(), context.chunkZ(),
                List.of(new StartEntry(STRUCTURE, start)));
        return new GenerationResult(true, hardcoded, starts);
    }

    /** Validates an unplaced persisted start against exact current-version generator output. */
    public static void validatePersisted(ValidStart start,
            Mc263HardcodedStructureCarrier hardcoded) {
        Objects.requireNonNull(start, "igloo persisted start");
        requireCanonicalCarrier(hardcoded);
        if (start.originChunkX() != hardcoded.chunkX()
                || start.originChunkZ() != hardcoded.chunkZ()
                || !start.startKey().equals(startKey(hardcoded.chunkX(), hardcoded.chunkZ()))
                || !start.adjustedBoundingBox().equals(box(hardcoded.boundingBox(), 0))
                || start.orderedPieces().size() != hardcoded.orderedPieces().size()) {
            throw new IllegalArgumentException("noncanonical igloo persisted start");
        }
        for (int index = 0; index < start.orderedPieces().size(); index++) {
            Piece expected = piece(hardcoded.orderedPieces().get(index),
                    canonicalPieceNbt(hardcoded, index, 0));
            if (!samePiece(start.orderedPieces().get(index), expected)) {
                throw new IllegalArgumentException(
                        "noncanonical igloo persisted piece at " + index);
            }
        }
    }

    /** Exact repository-owned binary-NBT value for one pinned igloo template piece. */
    public static byte[] canonicalPieceNbt(Mc263HardcodedStructureCarrier hardcoded,
            int pieceIndex) {
        return canonicalPieceNbt(hardcoded, pieceIndex, 0);
    }

    /** Same exact piece value after its official per-piece terrain-alignment Y translation. */
    public static byte[] canonicalPieceNbt(Mc263HardcodedStructureCarrier hardcoded,
            int pieceIndex, int yDelta) {
        requireCanonicalCarrier(hardcoded);
        if (pieceIndex < 0 || pieceIndex >= hardcoded.orderedPieces().size()) {
            throw new IllegalArgumentException("igloo piece index outside carrier");
        }
        PieceFact fact = hardcoded.orderedPieces().get(pieceIndex);
        var source = fact.boundingBox();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(192);
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(10); output.writeShort(0);
                intArray(output, "BB", source.minX(), Math.addExact(source.minY(), yDelta),
                        source.minZ(), source.maxX(), Math.addExact(source.maxY(), yDelta),
                        source.maxZ());
                integer(output, "GD", 0);
                string(output, "id", PIECE_TYPE);
                integer(output, "O", -1);
                integer(output, "TPX", fact.templateX());
                integer(output, "TPY", Math.addExact(fact.templateY(), yDelta));
                integer(output, "TPZ", fact.templateZ());
                string(output, "Template", templateKey(fact.template()));
                string(output, "Rot", rotationKey(hardcoded.rotation()));
                output.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("in-memory igloo piece NBT failed", exception);
        }
    }

    private static Piece piece(PieceFact fact, byte[] payload) {
        return new Piece(PIECE_TYPE, box(fact.boundingBox(), 0), false,
                Projection.NOT_APPLICABLE, 0, List.of(), new PiecePayload(payload));
    }

    private static boolean samePiece(Piece actual, Piece expected) {
        return actual.pieceType().equals(expected.pieceType())
                && actual.boundingBox().equals(expected.boundingBox())
                && actual.poolElement() == expected.poolElement()
                && actual.projection() == expected.projection()
                && actual.groundLevelDelta() == expected.groundLevelDelta()
                && actual.junctions().equals(expected.junctions())
                && Arrays.equals(actual.persistedPayload().binaryNbtCompound(),
                        expected.persistedPayload().binaryNbtCompound());
    }

    private static BoundingBox box(Mc263HardcodedStructureCarrier.BoundingBox source,
            int yDelta) {
        return new BoundingBox(source.minX(), Math.addExact(source.minY(), yDelta),
                source.minZ(), source.maxX(), Math.addExact(source.maxY(), yDelta),
                source.maxZ());
    }

    private static void requireCanonicalCarrier(Mc263HardcodedStructureCarrier hardcoded) {
        Objects.requireNonNull(hardcoded, "igloo hardcoded carrier");
        if (hardcoded.kind() != Kind.IGLOO || hardcoded.orderedPieces().isEmpty()
                || hardcoded.orderedPieces().getLast().template() != Template.IGLOO_TOP) {
            throw new IllegalArgumentException("noncanonical igloo hardcoded carrier shape");
        }
        for (PieceFact fact : hardcoded.orderedPieces()) {
            if (fact.kind() != PieceKind.IGLOO_TEMPLATE || fact.template() == null) {
                throw new IllegalArgumentException("noncanonical igloo hardcoded piece shape");
            }
        }
        Mc263HardcodedStructureCarrier canonical = Mc263HardcodedStructureCarrier.plan(
                Kind.IGLOO, hardcoded.worldSeed(), hardcoded.chunkX(), hardcoded.chunkZ());
        if (!Arrays.equals(hardcoded.encodeCanonical(), canonical.encodeCanonical())) {
            throw new IllegalArgumentException("noncanonical igloo hardcoded carrier facts");
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
            throw new IllegalArgumentException("igloo attempt is not pinned 26.3");
        }
    }

    private static String startKey(int chunkX, int chunkZ) {
        return STRUCTURE + "@" + chunkX + "," + chunkZ;
    }

    private static String templateKey(Template template) {
        return switch (template) {
            case IGLOO_TOP -> "minecraft:igloo/top";
            case IGLOO_MIDDLE -> "minecraft:igloo/middle";
            case IGLOO_BOTTOM -> "minecraft:igloo/bottom";
        };
    }

    private static String rotationKey(Rotation rotation) {
        return switch (rotation) {
            case NONE -> "NONE";
            case CLOCKWISE_90 -> "CLOCKWISE_90";
            case CLOCKWISE_180 -> "CLOCKWISE_180";
            case COUNTERCLOCKWISE_90 -> "COUNTERCLOCKWISE_90";
            default -> throw new IllegalArgumentException("igloo requires quarter rotation");
        };
    }

    private static void intArray(DataOutputStream output, String key, int... values)
            throws IOException {
        output.writeByte(11); output.writeUTF(key); output.writeInt(values.length);
        for (int value : values) output.writeInt(value);
    }

    private static void integer(DataOutputStream output, String key, int value)
            throws IOException {
        output.writeByte(3); output.writeUTF(key); output.writeInt(value);
    }

    private static void string(DataOutputStream output, String key, String value)
            throws IOException {
        output.writeByte(8); output.writeUTF(key); output.writeUTF(value);
    }
}
