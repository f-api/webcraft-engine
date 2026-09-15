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
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Exact dormant start/reference generator for pinned {@code minecraft:jungle_pyramid}. */
public final class Mc263JunglePyramidStartGenerator {
    public static final String STRUCTURE_SET = "minecraft:jungle_temples";
    public static final String STRUCTURE = Mc263JunglePyramidProgram.STRUCTURE_ID;
    public static final String PIECE_TYPE = Mc263JunglePyramidProgram.PIECE_TYPE;
    public static final int DECORATION_STEP = 4;
    public static final int STEP_INDEX = 22;
    public static final int REGISTRY_ORDINAL = 27;

    private Mc263JunglePyramidStartGenerator() { }

    /** Pure declarations checked before either live start-world query. */
    public interface WorldAccess {
        boolean supportsWorldSurfaceWg();
        boolean supportsSeaLevel();
        boolean supportsValidBiomeTest();
        int worldSurfaceWg(int blockX, int blockZ);
        int seaLevel();
        boolean isValidBiome(int blockX, int blockY, int blockZ);
    }

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
                throw new IllegalArgumentException(
                        "valid jungle-pyramid result/facts mismatch");
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

    /** Reloaded mutable carrier state and its exact persisted piece bounds. */
    public record LoadedStart(Mc263HardcodedStructureCarrier hardcodedCarrier,
            Mc263HardcodedStructureCarrier.BoundingBox adjustedBoundingBox) {
        public LoadedStart {
            Objects.requireNonNull(hardcodedCarrier, "jungle-pyramid loaded carrier");
            Objects.requireNonNull(adjustedBoundingBox, "jungle-pyramid loaded bounds");
        }
    }

    public static GenerationResult generate(
            AttemptContext context, Registry registry, WorldAccess world) {
        Objects.requireNonNull(context, "jungle-pyramid attempt context");
        Objects.requireNonNull(registry, "jungle-pyramid structure registry");
        Objects.requireNonNull(world, "jungle-pyramid start world");
        validateAttempt(context);
        validateRegistry(registry);
        if (!world.supportsWorldSurfaceWg() || !world.supportsSeaLevel()
                || !world.supportsValidBiomeTest()) {
            throw new UnsupportedOperationException(
                    "complete jungle-pyramid start world capabilities are required");
        }

        int blockX = Math.multiplyExact(context.chunkX(), 16);
        int blockZ = Math.multiplyExact(context.chunkZ(), 16);
        int farX = Math.addExact(blockX, 12);
        int farZ = Math.addExact(blockZ, 15);
        int lowest = world.worldSurfaceWg(blockX, blockZ);
        lowest = Math.min(lowest, world.worldSurfaceWg(blockX, farZ));
        lowest = Math.min(lowest, world.worldSurfaceWg(farX, blockZ));
        lowest = Math.min(lowest, world.worldSurfaceWg(farX, farZ));
        if (lowest < world.seaLevel()) return GenerationResult.invalid();

        int centerX = Math.addExact(blockX, 8);
        int centerZ = Math.addExact(blockZ, 8);
        int biomeY = world.worldSurfaceWg(centerX, centerZ);
        if (!world.isValidBiome(centerX, biomeY, centerZ)) {
            return GenerationResult.invalid();
        }

        Mc263HardcodedStructureCarrier hardcoded = Mc263HardcodedStructureCarrier.plan(
                Kind.JUNGLE_PYRAMID, context.worldSeed(), context.chunkX(), context.chunkZ());
        requireInitialCarrier(hardcoded);
        BoundingBox box = box(hardcoded.boundingBox());
        Piece piece = new Piece(PIECE_TYPE, box, false, Projection.NOT_APPLICABLE, 0,
                List.of(), new PiecePayload(
                        Mc263JunglePyramidProgram.canonicalPieceNbt(hardcoded)));
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

    /** Rejects every narrowed, reordered, rescheduled, or wrong-slot STR263C1 registry. */
    public static void validateRegistry(Registry registry) {
        Objects.requireNonNull(registry, "jungle-pyramid structure registry");
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
        StructureDefinition jungle = definitions.get(REGISTRY_ORDINAL);
        if (!jungle.structureId().equals(STRUCTURE)
                || jungle.decorationStep() != DECORATION_STEP
                || jungle.terrainAdjustment() != TerrainAdjustment.NONE
                || !Mc263StructureIndexReceipt.step(DECORATION_STEP).get(STEP_INDEX)
                        .equals(pinned.get(REGISTRY_ORDINAL))) {
            throw new IllegalArgumentException(
                    "jungle-pyramid registry slot is not pinned 26.3");
        }
    }

    public static void validatePersisted(ValidStart start,
            Mc263HardcodedStructureCarrier hardcoded) {
        Objects.requireNonNull(start, "jungle-pyramid persisted start");
        requireInitialCarrier(hardcoded);
        BoundingBox expected = box(hardcoded.boundingBox());
        if (!start.startKey().equals(startKey(hardcoded.chunkX(), hardcoded.chunkZ()))
                || start.originChunkX() != hardcoded.chunkX()
                || start.originChunkZ() != hardcoded.chunkZ()
                || !start.adjustedBoundingBox().equals(expected)
                || start.orderedPieces().size() != 1) {
            throw new IllegalArgumentException("noncanonical jungle-pyramid persisted start");
        }
        Piece piece = start.orderedPieces().getFirst();
        if (!piece.pieceType().equals(PIECE_TYPE)
                || !piece.boundingBox().equals(expected) || piece.poolElement()
                || piece.projection() != Projection.NOT_APPLICABLE
                || piece.groundLevelDelta() != 0 || !piece.junctions().isEmpty()
                || !Arrays.equals(piece.persistedPayload().binaryNbtCompound(),
                        Mc263JunglePyramidProgram.canonicalPieceNbt(hardcoded))) {
            throw new IllegalArgumentException("noncanonical jungle-pyramid persisted piece");
        }
    }

    /**
     * Strictly reloads one persisted TeJP piece without repeating structure-set admission.
     * Immutable geometry and rotation are replanned from seed/origin before mutable NBT is applied.
     */
    public static LoadedStart loadPersisted(long worldSeed, ValidStart start) {
        Objects.requireNonNull(start, "jungle-pyramid persisted start");
        if (!start.startKey().equals(startKey(start.originChunkX(), start.originChunkZ()))
                || start.orderedPieces().size() != 1) {
            throw new IllegalArgumentException("noncanonical jungle-pyramid persisted start");
        }
        Piece piece = start.orderedPieces().getFirst();
        if (!piece.pieceType().equals(PIECE_TYPE) || piece.poolElement()
                || piece.projection() != Projection.NOT_APPLICABLE
                || piece.groundLevelDelta() != 0 || !piece.junctions().isEmpty()
                || !start.adjustedBoundingBox().equals(piece.boundingBox())) {
            throw new IllegalArgumentException("noncanonical jungle-pyramid persisted piece");
        }

        Mc263HardcodedStructureCarrier replanned = Mc263HardcodedStructureCarrier.plan(
                Kind.JUNGLE_PYRAMID, worldSeed, start.originChunkX(), start.originChunkZ());
        requireInitialCarrier(replanned);
        byte[] payload = piece.persistedPayload().binaryNbtCompound();
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            require(input.readUnsignedByte() == 10 && input.readUnsignedShort() == 0);
            requireTag(input, 11, "BB");
            require(input.readInt() == 6);
            int[] coordinates = new int[6];
            for (int index = 0; index < coordinates.length; index++) {
                coordinates[index] = input.readInt();
            }
            boolean trap2 = readBooleanTag(input, "placedTrap2");
            boolean mainChest = readBooleanTag(input, "placedMainChest");
            requireIntTag(input, "Height", 10);
            requireTag(input, 8, "id");
            require(PIECE_TYPE.equals(input.readUTF()));
            boolean trap1 = readBooleanTag(input, "placedTrap1");
            requireIntTag(input, "GD", 0);
            requireIntTag(input, "Width", 12);
            requireTag(input, 3, "HPos");
            int heightPosition = input.readInt();
            boolean hiddenChest = readBooleanTag(input, "placedHiddenChest");
            requireIntTag(input, "Depth", 15);
            requireIntTag(input, "O", orientationNbtId(replanned.rotation()));
            require(input.readUnsignedByte() == 0 && input.available() == 0);

            BoundingBox persistedBox = piece.boundingBox();
            require(Arrays.equals(coordinates, coordinates(persistedBox)));
            require(heightPosition >= -1);
            require(heightPosition >= 0 || !(trap2 || mainChest || trap1 || hiddenChest));
            var successor = replanned.successor().withHeightPosition(heightPosition)
                    .withFlag("placedMainChest", mainChest)
                    .withFlag("placedHiddenChest", hiddenChest)
                    .withFlag("placedTrap1", trap1)
                    .withFlag("placedTrap2", trap2);
            Mc263HardcodedStructureCarrier loaded = replanned.withSuccessor(successor);
            require(Arrays.equals(payload, Mc263JunglePyramidProgram.canonicalPieceNbt(loaded)));
            return new LoadedStart(loaded, hardcodedBox(persistedBox));
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException(
                    "noncanonical minecraft:tejp persisted NBT", exception);
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
            throw new IllegalArgumentException("jungle-pyramid attempt is not pinned 26.3");
        }
    }

    private static void requireInitialCarrier(Mc263HardcodedStructureCarrier hardcoded) {
        Objects.requireNonNull(hardcoded, "jungle-pyramid hardcoded carrier");
        if (hardcoded.kind() != Kind.JUNGLE_PYRAMID
                || hardcoded.orderedPieces().size() != 1
                || hardcoded.orderedPieces().getFirst().kind() != PieceKind.JUNGLE_PYRAMID
                || hardcoded.orderedPieces().getFirst().template() != null
                || hardcoded.successor().heightPosition() != -1
                || hardcoded.successor().orderedKeys().stream()
                        .anyMatch(hardcoded.successor()::flag)
                || !isCardinal(hardcoded.rotation())) {
            throw new IllegalArgumentException(
                    "noncanonical initial jungle-pyramid carrier shape");
        }
        Mc263HardcodedStructureCarrier canonical = Mc263HardcodedStructureCarrier.plan(
                Kind.JUNGLE_PYRAMID, hardcoded.worldSeed(), hardcoded.chunkX(),
                hardcoded.chunkZ());
        if (!Arrays.equals(hardcoded.encodeCanonical(), canonical.encodeCanonical())) {
            throw new IllegalArgumentException(
                    "noncanonical initial jungle-pyramid carrier facts");
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
                    "jungle pyramid requires cardinal rotation");
        };
    }

    private static int[] coordinates(BoundingBox box) {
        return new int[]{box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ()};
    }

    private static Mc263HardcodedStructureCarrier.BoundingBox hardcodedBox(BoundingBox box) {
        return new Mc263HardcodedStructureCarrier.BoundingBox(box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ());
    }

    private static boolean readBooleanTag(DataInputStream input, String name) throws IOException {
        requireTag(input, 1, name);
        int value = input.readUnsignedByte();
        require(value == 0 || value == 1);
        return value != 0;
    }

    private static void requireIntTag(DataInputStream input, String name, int value)
            throws IOException {
        requireTag(input, 3, name);
        require(input.readInt() == value);
    }

    private static void requireTag(DataInputStream input, int type, String name)
            throws IOException {
        require(input.readUnsignedByte() == type && name.equals(input.readUTF()));
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("noncanonical persisted NBT fact");
    }

    private static BoundingBox box(Mc263HardcodedStructureCarrier.BoundingBox source) {
        return new BoundingBox(source.minX(), source.minY(), source.minZ(),
                source.maxX(), source.maxY(), source.maxZ());
    }

    private static String startKey(int chunkX, int chunkZ) {
        return STRUCTURE + "@" + chunkX + "," + chunkZ;
    }
}
