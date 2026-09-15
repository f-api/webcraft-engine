package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.feature.Mc263StructureIndexReceipt;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.Kind;
import com.gameexpert.terrain.mc.structure.Mc263HardcodedStructureCarrier.PieceFact;
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
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Exact dormant start/reference generator for pinned {@code minecraft:monument}.
 *
 * <p>The official start is one procedural {@code MonumentBuilding} top piece whose room grid is
 * planned by the shared hardcoded carrier; the child grid is carried beside the persisted start
 * because the official {@code StructureStart} persists only the top piece.</p>
 */
public final class Mc263OceanMonumentStartGenerator {
    public static final String STRUCTURE_SET = "minecraft:ocean_monuments";
    public static final String STRUCTURE = "minecraft:monument";
    public static final String PIECE_TYPE = "minecraft:omb";
    public static final int DECORATION_STEP = 4;
    public static final int STEP_INDEX = 24;
    public static final int REGISTRY_ORDINAL = 29;
    /** Official {@code MonumentBuilding} west/north offset from the start chunk origin. */
    public static final int ORIGIN_OFFSET = 29;
    /** Official fixed {@code MonumentBuilding} bottom height. */
    public static final int FIXED_Y = 39;

    private Mc263OceanMonumentStartGenerator() { }

    /** Pure declarations checked before either live start-world query. */
    public interface WorldAccess {
        boolean supportsOceanFloorWg();
        boolean supportsValidBiomeTest();
        int oceanFloorWg(int blockX, int blockZ);
        boolean isValidBiome(int blockX, int blockY, int blockZ);
    }

    public static final class GenerationResult {
        private final boolean valid;
        private final Mc263HardcodedStructureCarrier hardcodedCarrier;
        private final ChunkStarts startChunk;
        private final ChunkReferences references;
        private final int stubY;

        private GenerationResult(boolean valid,
                Mc263HardcodedStructureCarrier hardcodedCarrier,
                ChunkStarts startChunk, ChunkReferences references, int stubY) {
            if (valid != (hardcodedCarrier != null)
                    || valid != (startChunk != null) || valid != (references != null)) {
                throw new IllegalArgumentException("valid monument result/facts mismatch");
            }
            this.valid = valid;
            this.hardcodedCarrier = hardcodedCarrier;
            this.startChunk = startChunk;
            this.references = references;
            this.stubY = stubY;
        }

        public boolean valid() { return valid; }
        public Mc263HardcodedStructureCarrier hardcodedCarrier() { return hardcodedCarrier; }
        public ChunkStarts startChunk() { return startChunk; }
        public ChunkReferences references() { return references; }

        /** Official {@code onTopOfChunkCenter} stub height; the top piece stays at y=39. */
        public int stubY() { return stubY; }

        /** Total official {@code WorldgenRandom} draw count for this start attempt. */
        public int worldgenDrawCount() {
            if (!valid) throw new IllegalStateException("invalid monument attempt has no draws");
            return Math.toIntExact(hardcodedCarrier.rngContinuation().bitDraws());
        }

        public static GenerationResult invalid(int stubY) {
            return new GenerationResult(false, null, null, null, stubY);
        }
    }

    public static GenerationResult generate(
            AttemptContext context, Registry registry, WorldAccess world) {
        Objects.requireNonNull(context, "monument attempt context");
        Objects.requireNonNull(registry, "monument structure registry");
        Objects.requireNonNull(world, "monument start world");
        validateAttempt(context);
        validateRegistry(registry);
        if (!world.supportsOceanFloorWg() || !world.supportsValidBiomeTest()) {
            throw new UnsupportedOperationException(
                    "complete monument start world capabilities are required");
        }

        int centerX = Math.addExact(Math.multiplyExact(context.chunkX(), 16), 8);
        int centerZ = Math.addExact(Math.multiplyExact(context.chunkZ(), 16), 8);
        int stubY = world.oceanFloorWg(centerX, centerZ);
        if (!world.isValidBiome(centerX, stubY, centerZ)) {
            return GenerationResult.invalid(stubY);
        }

        Mc263HardcodedStructureCarrier hardcoded = Mc263HardcodedStructureCarrier.plan(
                Kind.OCEAN_MONUMENT, context.worldSeed(), context.chunkX(), context.chunkZ());
        requireInitialCarrier(hardcoded);
        BoundingBox box = box(hardcoded.boundingBox());
        Piece piece = new Piece(PIECE_TYPE, box, false, Projection.NOT_APPLICABLE, 0,
                List.of(), new PiecePayload(hardcoded.canonicalMonumentBuildingNbt()));
        ValidStart start = new ValidStart(startKey(context.chunkX(), context.chunkZ()),
                context.chunkX(), context.chunkZ(), context.priorReferences(), box,
                List.of(piece));
        ChunkStarts starts = new ChunkStarts(context.chunkX(), context.chunkZ(),
                List.of(new StartEntry(STRUCTURE, start)));
        ChunkReferences references = new ChunkReferences(context.chunkX(), context.chunkZ(),
                List.of(new ReferenceSet(STRUCTURE, List.of(Mc263StructureCarrier.packChunk(
                        context.chunkX(), context.chunkZ())))));
        return new GenerationResult(true, hardcoded, starts, references, stubY);
    }

    /** Rejects every narrowed, reordered, rescheduled, or wrong-slot STR263C1 registry. */
    public static void validateRegistry(Registry registry) {
        Objects.requireNonNull(registry, "monument structure registry");
        List<Mc263StructureIndexReceipt.Entry> pinned = Mc263StructureIndexReceipt.entries();
        List<StructureDefinition> definitions = registry.definitions();
        if (pinned.size() != 52 || definitions.size() != 52) {
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
        StructureDefinition monument = definitions.get(REGISTRY_ORDINAL);
        if (!monument.structureId().equals(STRUCTURE)
                || monument.decorationStep() != DECORATION_STEP
                || monument.terrainAdjustment() != TerrainAdjustment.NONE
                || !Mc263StructureIndexReceipt.step(DECORATION_STEP).get(STEP_INDEX)
                        .equals(pinned.get(REGISTRY_ORDINAL))) {
            throw new IllegalArgumentException("monument registry slot is not pinned 26.3");
        }
    }

    public static void validatePersisted(ValidStart start,
            Mc263HardcodedStructureCarrier hardcoded) {
        Objects.requireNonNull(start, "monument persisted start");
        requireInitialCarrier(hardcoded);
        BoundingBox expected = box(hardcoded.boundingBox());
        if (!start.startKey().equals(startKey(hardcoded.chunkX(), hardcoded.chunkZ()))
                || start.originChunkX() != hardcoded.chunkX()
                || start.originChunkZ() != hardcoded.chunkZ()
                || !start.adjustedBoundingBox().equals(expected)
                || start.orderedPieces().size() != 1) {
            throw new IllegalArgumentException("noncanonical monument persisted start");
        }
        Piece piece = start.orderedPieces().getFirst();
        if (!piece.pieceType().equals(PIECE_TYPE)
                || !piece.boundingBox().equals(expected) || piece.poolElement()
                || piece.projection() != Projection.NOT_APPLICABLE
                || piece.groundLevelDelta() != 0 || !piece.junctions().isEmpty()
                || !Arrays.equals(piece.persistedPayload().binaryNbtCompound(),
                        hardcoded.canonicalMonumentBuildingNbt())) {
            throw new IllegalArgumentException("noncanonical monument persisted piece");
        }
    }

    /**
     * Strictly reloads one persisted {@code minecraft:omb} start; the whole procedural room grid
     * and its child pieces are replanned from the immutable seed/origin identity, because the
     * official structure start persists only the top piece and carries no mutable successor field.
     */
    public static Mc263HardcodedStructureCarrier loadPersisted(long worldSeed, ValidStart start) {
        Objects.requireNonNull(start, "monument persisted start");
        Mc263HardcodedStructureCarrier replanned = Mc263HardcodedStructureCarrier.plan(
                Kind.OCEAN_MONUMENT, worldSeed, start.originChunkX(), start.originChunkZ());
        validatePersisted(start, replanned);
        return replanned;
    }

    /** Ordered child grid NBT in official source order; index 0 is the first child piece. */
    public static List<byte[]> orderedChildPieceNbt(Mc263HardcodedStructureCarrier hardcoded) {
        requireInitialCarrier(hardcoded);
        List<PieceFact> pieces = hardcoded.orderedPieces();
        ArrayList<byte[]> result = new ArrayList<>(pieces.size() - 1);
        for (int index = 1; index < pieces.size(); index++) {
            result.add(childPieceNbt(pieces.get(index), hardcoded.rotation()));
        }
        return List.copyOf(result);
    }

    /** Exact persisted child compound in the pinned {@code BB, id, GD, O} encounter order. */
    public static byte[] canonicalChildPieceNbt(
            Mc263HardcodedStructureCarrier hardcoded, int childOrdinal) {
        requireInitialCarrier(hardcoded);
        if (childOrdinal < 0 || childOrdinal >= hardcoded.orderedPieces().size() - 1) {
            throw new IllegalArgumentException("monument child ordinal outside grid");
        }
        return childPieceNbt(hardcoded.orderedPieces().get(childOrdinal + 1),
                hardcoded.rotation());
    }

    /** Exact persisted {@code StructureStart} compound carrying the single top piece. */
    public static byte[] persistedStartNbt(
            Mc263HardcodedStructureCarrier hardcoded, int references) {
        requireInitialCarrier(hardcoded);
        if (references < 0) throw new IllegalArgumentException("negative monument references");
        byte[] top = hardcoded.canonicalMonumentBuildingNbt();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(10); out.writeShort(0);
                tagInt(out, "references", references);
                tagInt(out, "ChunkZ", hardcoded.chunkZ());
                tagString(out, "id", STRUCTURE);
                out.writeByte(9); utf(out, "Children"); out.writeByte(10); out.writeInt(1);
                out.write(top, 3, top.length - 3);
                tagInt(out, "ChunkX", hardcoded.chunkX());
                out.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory monument start NBT failed", impossible);
        }
    }

    /** Official child piece identifier for one planned room piece. */
    public static String childPieceId(PieceKind kind) {
        return switch (kind) {
            case MONUMENT_ENTRY -> "minecraft:omentry";
            case MONUMENT_CORE -> "minecraft:omcr";
            case MONUMENT_DOUBLE_X -> "minecraft:omdxr";
            case MONUMENT_DOUBLE_XY -> "minecraft:omdxyr";
            case MONUMENT_DOUBLE_Y -> "minecraft:omdyr";
            case MONUMENT_DOUBLE_YZ -> "minecraft:omdyzr";
            case MONUMENT_DOUBLE_Z -> "minecraft:omdzr";
            case MONUMENT_SIMPLE -> "minecraft:omsimple";
            case MONUMENT_SIMPLE_TOP -> "minecraft:omsimplet";
            case MONUMENT_WING -> "minecraft:omwr";
            case MONUMENT_PENTHOUSE -> "minecraft:ompenthouse";
            default -> throw new IllegalArgumentException("non-child monument piece kind");
        };
    }

    private static byte[] childPieceNbt(PieceFact piece, Rotation rotation) {
        Mc263HardcodedStructureCarrier.BoundingBox box = piece.boundingBox();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(10); out.writeShort(0);
                out.writeByte(11); utf(out, "BB"); out.writeInt(6);
                out.writeInt(box.minX()); out.writeInt(box.minY()); out.writeInt(box.minZ());
                out.writeInt(box.maxX()); out.writeInt(box.maxY()); out.writeInt(box.maxZ());
                tagString(out, "id", childPieceId(piece.kind()));
                tagInt(out, "GD", 1);
                tagInt(out, "O", orientationNbtId(rotation));
                out.writeByte(0);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory monument child NBT failed", impossible);
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
            throw new IllegalArgumentException("monument attempt is not pinned 26.3");
        }
    }

    private static void requireInitialCarrier(Mc263HardcodedStructureCarrier hardcoded) {
        Objects.requireNonNull(hardcoded, "monument hardcoded carrier");
        List<PieceFact> pieces = hardcoded.orderedPieces();
        if (hardcoded.kind() != Kind.OCEAN_MONUMENT || pieces.size() < 2
                || pieces.getFirst().kind() != PieceKind.MONUMENT_BUILDING
                || !hardcoded.successor().orderedKeys().isEmpty()
                || !isCardinal(hardcoded.rotation())
                || hardcoded.boundingBox().minY() != FIXED_Y) {
            throw new IllegalArgumentException("noncanonical initial monument carrier shape");
        }
        for (int index = 1; index < pieces.size(); index++) {
            childPieceId(pieces.get(index).kind());
        }
        Mc263HardcodedStructureCarrier canonical = Mc263HardcodedStructureCarrier.plan(
                Kind.OCEAN_MONUMENT, hardcoded.worldSeed(), hardcoded.chunkX(),
                hardcoded.chunkZ());
        if (!Arrays.equals(hardcoded.encodeCanonical(), canonical.encodeCanonical())) {
            throw new IllegalArgumentException("noncanonical initial monument carrier facts");
        }
    }

    private static boolean isCardinal(Rotation rotation) {
        return rotation == Rotation.NORTH || rotation == Rotation.EAST
                || rotation == Rotation.SOUTH || rotation == Rotation.WEST;
    }

    private static int orientationNbtId(Rotation rotation) {
        return switch (rotation) {
            case SOUTH -> 0;
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> throw new IllegalArgumentException(
                    "monument requires cardinal orientation");
        };
    }

    private static void tagInt(DataOutputStream out, String name, int value) throws IOException {
        out.writeByte(3); utf(out, name); out.writeInt(value);
    }

    private static void tagString(DataOutputStream out, String name, String value)
            throws IOException {
        out.writeByte(8); utf(out, name); utf(out, value);
    }

    private static void utf(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 0xffff) throw new IllegalArgumentException("NBT string too long");
        out.writeShort(bytes.length); out.write(bytes);
    }

    private static BoundingBox box(Mc263HardcodedStructureCarrier.BoundingBox source) {
        return new BoundingBox(source.minX(), source.minY(), source.minZ(),
                source.maxX(), source.maxY(), source.maxZ());
    }

    private static String startKey(int chunkX, int chunkZ) {
        return STRUCTURE + "@" + chunkX + "," + chunkZ;
    }
}
