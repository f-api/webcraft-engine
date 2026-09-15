package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.feature.Mc263StructureIndexReceipt;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.LegacyRandom;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.Plan;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.TemplateCatalog;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.Type;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkReferences;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.ChunkStarts;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.Piece;
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

/**
 * Exact dormant start/reference generator for pinned {@code minecraft:ocean_ruin_cold} and
 * {@code minecraft:ocean_ruin_warm}.
 *
 * <p>The official start keeps every template piece at the fixed layout height; the seabed
 * projection belongs to piece placement, not to the persisted start graph.</p>
 */
public final class Mc263OceanRuinStartGenerator {
    public static final String STRUCTURE_SET = "minecraft:ocean_ruins";
    public static final String COLD_STRUCTURE = Mc263OceanRuinProgram.COLD_STRUCTURE;
    public static final String WARM_STRUCTURE = Mc263OceanRuinProgram.WARM_STRUCTURE;
    public static final String PIECE_TYPE = Mc263OceanRuinProgram.PIECE_TYPE;
    public static final int DECORATION_STEP = 4;
    public static final int COLD_STEP_INDEX = 25;
    public static final int WARM_STEP_INDEX = 26;
    public static final int COLD_REGISTRY_ORDINAL = 30;
    public static final int WARM_REGISTRY_ORDINAL = 31;
    /** Official structure-set member count; both members carry weight one. */
    public static final int SET_MEMBER_COUNT = 2;
    /** Official {@code WorldgenRandom} draws consumed by {@code setLargeFeatureSeed}. */
    public static final int SEEDING_DRAWS = 4;

    private Mc263OceanRuinStartGenerator() { }

    /** Pure declarations checked before either live start-world query. */
    public interface WorldAccess {
        boolean supportsOceanFloorWg();
        boolean supportsValidBiomeTest();
        int oceanFloorWg(int blockX, int blockZ);
        boolean isValidBiome(int blockX, int blockY, int blockZ);
    }

    public static final class GenerationResult {
        private final boolean valid;
        private final Plan plan;
        private final ChunkStarts startChunk;
        private final ChunkReferences references;
        private final int stubY;
        private final int worldgenDrawCount;

        private GenerationResult(boolean valid, Plan plan, ChunkStarts startChunk,
                ChunkReferences references, int stubY, int worldgenDrawCount) {
            if (valid != (plan != null) || valid != (startChunk != null)
                    || valid != (references != null)) {
                throw new IllegalArgumentException("valid ocean-ruin result/facts mismatch");
            }
            this.valid = valid;
            this.plan = plan;
            this.startChunk = startChunk;
            this.references = references;
            this.stubY = stubY;
            this.worldgenDrawCount = worldgenDrawCount;
        }

        public boolean valid() { return valid; }
        public Plan plan() { return plan; }
        public ChunkStarts startChunk() { return startChunk; }
        public ChunkReferences references() { return references; }

        /** Official {@code onTopOfChunkCenter} stub height; pieces stay at the layout height. */
        public int stubY() { return stubY; }

        /** Total official {@code WorldgenRandom} draw count for this start attempt. */
        public int worldgenDrawCount() {
            if (!valid) throw new IllegalStateException("invalid ocean-ruin attempt has no draws");
            return worldgenDrawCount;
        }

        public static GenerationResult invalid(int stubY) {
            return new GenerationResult(false, null, null, null, stubY, 0);
        }
    }

    public static GenerationResult generate(AttemptContext context, Registry registry,
            WorldAccess world, TemplateCatalog catalog) {
        Objects.requireNonNull(context, "ocean-ruin attempt context");
        Objects.requireNonNull(registry, "ocean-ruin structure registry");
        Objects.requireNonNull(world, "ocean-ruin start world");
        Objects.requireNonNull(catalog, "ocean-ruin template catalog");
        Type type = validateAttempt(context);
        validateRegistry(registry);
        if (!world.supportsOceanFloorWg() || !world.supportsValidBiomeTest()) {
            throw new UnsupportedOperationException(
                    "complete ocean-ruin start world capabilities are required");
        }

        int originX = Math.multiplyExact(context.chunkX(), 16);
        int originZ = Math.multiplyExact(context.chunkZ(), 16);
        int centerX = Math.addExact(originX, 8);
        int centerZ = Math.addExact(originZ, 8);
        int stubY = world.oceanFloorWg(centerX, centerZ);
        if (!world.isValidBiome(centerX, stubY, centerZ)) {
            return GenerationResult.invalid(stubY);
        }

        LegacyRandom random = new LegacyRandom(
                largeFeatureSeed(context.worldSeed(), context.chunkX(), context.chunkZ()));
        Rotation rotation = Rotation.values()[random.nextInt(Rotation.values().length)];
        Plan plan = Mc263OceanRuinProgram.plan(random, originX, originZ, rotation, type, catalog);
        if (!plan.structureKey().equals(context.structureKey())) {
            throw new IllegalStateException("ocean-ruin plan/structure-key drift");
        }
        ValidStart start = Mc263OceanRuinProgram.validStart(plan, context.priorReferences());
        requireStartShape(start, context);
        ChunkStarts starts = new ChunkStarts(context.chunkX(), context.chunkZ(),
                List.of(new StartEntry(context.structureKey(), start)));
        ChunkReferences references = new ChunkReferences(context.chunkX(), context.chunkZ(),
                List.of(new ReferenceSet(context.structureKey(), List.of(
                        Mc263StructureCarrier.packChunk(context.chunkX(), context.chunkZ())))));
        return new GenerationResult(true, plan, starts, references, stubY,
                Math.addExact(SEEDING_DRAWS, random.drawCount()));
    }

    /** Rejects every narrowed, reordered, rescheduled, or wrong-slot STR263C1 registry. */
    public static void validateRegistry(Registry registry) {
        Objects.requireNonNull(registry, "ocean-ruin structure registry");
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
        requireSlot(definitions, pinned, COLD_REGISTRY_ORDINAL, COLD_STEP_INDEX, COLD_STRUCTURE);
        requireSlot(definitions, pinned, WARM_REGISTRY_ORDINAL, WARM_STEP_INDEX, WARM_STRUCTURE);
    }

    public static void validatePersisted(ValidStart start, Plan plan, int references) {
        Objects.requireNonNull(start, "ocean-ruin persisted start");
        Objects.requireNonNull(plan, "ocean-ruin plan");
        ValidStart expected = Mc263OceanRuinProgram.validStart(plan, references);
        if (!start.startKey().equals(expected.startKey())
                || start.originChunkX() != expected.originChunkX()
                || start.originChunkZ() != expected.originChunkZ()
                || start.references() != expected.references()
                || !start.adjustedBoundingBox().equals(expected.adjustedBoundingBox())
                || start.orderedPieces().size() != expected.orderedPieces().size()) {
            throw new IllegalArgumentException("noncanonical ocean-ruin persisted start");
        }
        for (int ordinal = 0; ordinal < start.orderedPieces().size(); ordinal++) {
            Piece actual = start.orderedPieces().get(ordinal);
            Piece pinned = expected.orderedPieces().get(ordinal);
            if (!actual.pieceType().equals(pinned.pieceType())
                    || !actual.boundingBox().equals(pinned.boundingBox())
                    || actual.poolElement() || actual.projection() != Projection.NOT_APPLICABLE
                    || actual.groundLevelDelta() != 0 || !actual.junctions().isEmpty()
                    || !Arrays.equals(actual.persistedPayload().binaryNbtCompound(),
                            pinned.persistedPayload().binaryNbtCompound())) {
                throw new IllegalArgumentException(
                        "noncanonical ocean-ruin persisted piece at " + ordinal);
            }
        }
    }

    /**
     * Strictly reloads one persisted ocean-ruin start by replanning the immutable template graph
     * from the seed/origin identity; the family carries no mutable successor field.
     */
    public static Plan loadPersisted(long worldSeed, ValidStart start, TemplateCatalog catalog) {
        Objects.requireNonNull(start, "ocean-ruin persisted start");
        Objects.requireNonNull(catalog, "ocean-ruin template catalog");
        String structureKey = start.startKey().substring(0, start.startKey().indexOf('@'));
        Type type = requireType(structureKey);
        int originX = Math.multiplyExact(start.originChunkX(), 16);
        int originZ = Math.multiplyExact(start.originChunkZ(), 16);
        LegacyRandom random = new LegacyRandom(
                largeFeatureSeed(worldSeed, start.originChunkX(), start.originChunkZ()));
        Rotation rotation = Rotation.values()[random.nextInt(Rotation.values().length)];
        Plan replanned = Mc263OceanRuinProgram.plan(random, originX, originZ, rotation, type,
                catalog);
        validatePersisted(start, replanned, start.references());
        return replanned;
    }

    /** Official {@code WorldgenRandom.setLargeFeatureSeed} mixed layout seed. */
    public static long largeFeatureSeed(long worldSeed, int chunkX, int chunkZ) {
        LegacyRandom seedRandom = new LegacyRandom(worldSeed);
        long xScale = seedRandom.nextLong();
        long zScale = seedRandom.nextLong();
        return (long) chunkX * xScale ^ (long) chunkZ * zScale ^ worldSeed;
    }

    private static void requireStartShape(ValidStart start, AttemptContext context) {
        String expectedKey = context.structureKey() + "@" + context.chunkX() + ","
                + context.chunkZ();
        if (!start.startKey().equals(expectedKey)
                || start.originChunkX() != context.chunkX()
                || start.originChunkZ() != context.chunkZ()
                || start.references() != context.priorReferences()) {
            throw new IllegalStateException("ocean-ruin start identity drift");
        }
        BoundingBox aggregate = start.adjustedBoundingBox();
        for (Piece piece : start.orderedPieces()) {
            if (!piece.pieceType().equals(PIECE_TYPE) || piece.poolElement()
                    || piece.projection() != Projection.NOT_APPLICABLE
                    || piece.groundLevelDelta() != 0 || !piece.junctions().isEmpty()
                    || piece.boundingBox().minX() < aggregate.minX()
                    || piece.boundingBox().minY() < aggregate.minY()
                    || piece.boundingBox().minZ() < aggregate.minZ()
                    || piece.boundingBox().maxX() > aggregate.maxX()
                    || piece.boundingBox().maxY() > aggregate.maxY()
                    || piece.boundingBox().maxZ() > aggregate.maxZ()) {
                throw new IllegalStateException("ocean-ruin start piece shape drift");
            }
        }
    }

    private static void requireSlot(List<StructureDefinition> definitions,
            List<Mc263StructureIndexReceipt.Entry> pinned, int ordinal, int stepIndex,
            String structureId) {
        StructureDefinition definition = definitions.get(ordinal);
        if (!definition.structureId().equals(structureId)
                || definition.decorationStep() != DECORATION_STEP
                || definition.terrainAdjustment() != TerrainAdjustment.NONE
                || !Mc263StructureIndexReceipt.step(DECORATION_STEP).get(stepIndex)
                        .equals(pinned.get(ordinal))) {
            throw new IllegalArgumentException(
                    "ocean-ruin registry slot is not pinned 26.3: " + structureId);
        }
    }

    private static Type validateAttempt(AttemptContext context) {
        int locateX = Math.multiplyExact(context.chunkX(), 16);
        int locateZ = Math.multiplyExact(context.chunkZ(), 16);
        if (!STRUCTURE_SET.equals(context.setKey())
                || context.attemptIndex() < 0 || context.attemptIndex() >= SET_MEMBER_COUNT
                || context.weight() != 1 || context.priorReferences() < 0
                || context.locatePos().x() != locateX || context.locatePos().y() != 0
                || context.locatePos().z() != locateZ) {
            throw new IllegalArgumentException("ocean-ruin attempt is not pinned 26.3");
        }
        return requireType(context.structureKey());
    }

    private static Type requireType(String structureKey) {
        if (COLD_STRUCTURE.equals(structureKey)) return Type.COLD;
        if (WARM_STRUCTURE.equals(structureKey)) return Type.WARM;
        throw new IllegalArgumentException("structure is not an ocean-ruin member");
    }
}
