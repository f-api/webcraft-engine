package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.structure.Mc263ShipwreckCarrier;
import com.gameexpert.terrain.mc.structure.Mc263ShipwreckCatalog;
import com.gameexpert.terrain.mc.structure.Mc263ShipwreckPieceProgram;
import com.gameexpert.terrain.mc.structure.Mc263ShipwreckStartGenerator;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Dormant production adapter for both exact pinned Shipwreck structure slots. */
final class Mc263ShipwreckCanonicalExecutor implements
        Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutor {
    private static final int STEP = Mc263ShipwreckStartGenerator.DECORATION_STEP;
    private static final long OCEAN_BIOME_MASK = 0x000000000003feL;
    private static final long BEACHED_BIOME_MASK = 0x00000001008000L;

    private final String key;
    private final int index;
    private final int ordinal;
    private final long biomeMask;

    private Mc263ShipwreckCanonicalExecutor(String key, int index, int ordinal, long biomeMask) {
        this.key = key;
        this.index = index;
        this.ordinal = ordinal;
        this.biomeMask = biomeMask;
    }

    static void register(
            Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry registry) {
        Objects.requireNonNull(registry, "canonical structure executor registry")
                .register(Mc263ShipwreckCarrier.OCEAN,
                        new Mc263ShipwreckCanonicalExecutor(Mc263ShipwreckCarrier.OCEAN,
                                Mc263ShipwreckStartGenerator.OCEAN_STEP_INDEX,
                                Mc263ShipwreckStartGenerator.OCEAN_REGISTRY_ORDINAL,
                                OCEAN_BIOME_MASK))
                .register(Mc263ShipwreckCarrier.BEACHED,
                        new Mc263ShipwreckCanonicalExecutor(Mc263ShipwreckCarrier.BEACHED,
                                Mc263ShipwreckStartGenerator.BEACHED_STEP_INDEX,
                                Mc263ShipwreckStartGenerator.BEACHED_REGISTRY_ORDINAL,
                                BEACHED_BIOME_MASK));
    }

    @Override
    public void preflight(
            Mc263CanonicalFeaturesProducerSkeleton.StructurePreflightContext context) {
        Objects.requireNonNull(context, "shipwreck preflight context");
        requireSchedule(context.entry(), context.carrier().registry());
        requireSourceBinding(context.references(), context.clip());
        requireAcceptedIdentity();
        validatePersistedStarts(context.worldSeed(), context.carrier());
        for (Mc263StructureCarrier.ValidStart start : context.carrier().resolveStarts(
                context.references(), key)) {
            loadPersisted(context.worldSeed(), start);
        }
    }

    @Override
    public void place(Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context) {
        Objects.requireNonNull(context, "shipwreck placement context");
        Mc263FeatureDispatcher.StructurePlacementContext dispatcher = context.dispatcher();
        requireDispatcher(dispatcher, context.references());
        requireSourceBinding(context.references(), context.clip());
        requireSchedule(Mc263StructureIndexReceipt.step(STEP).get(index),
                context.carrier().registry());

        List<String> startKeys = context.carrier().resolveStarts(context.references(), key).stream()
                .map(Mc263StructureCarrier.ValidStart::startKey).toList();
        for (String startKey : startKeys) {
            Mc263StructureCarrier predecessor = context.carrier();
            Mc263StructureCarrier.ChunkReferences references = context.references();
            Mc263StructureCarrier.ValidStart persisted = predecessor.resolveStarts(references, key)
                    .stream().filter(value -> value.startKey().equals(startKey)).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "evolved shipwreck carrier lost referenced start"));
            Mc263ShipwreckCarrier initial = loadPersisted(dispatcher.worldSeed(), persisted);
            if (initial.heightAdjusted()) {
                if (references.chunkX() == persisted.originChunkX()
                        && references.chunkZ() == persisted.originChunkZ()) {
                    continue;
                }
                CapturingWorld world = new CapturingWorld(dispatcher.region());
                Mc263ShipwreckPieceProgram.PlacementRandom candidate =
                        Mc263ShipwreckPieceProgram.PlacementRandom.forChunk(
                                dispatcher.worldSeed(), references.chunkX(), references.chunkZ(),
                                index);
                Mc263ShipwreckPieceProgram.AtomicSettlement settlement =
                        Mc263ShipwreckPieceProgram.replayAdjusted(initial, predecessor, references,
                                Mc263ShipwreckCatalog.PROCESSOR, clip(context.clip()), world,
                                candidate);
                if (world.settlement != settlement) {
                    throw new IllegalStateException(
                            "shipwreck replay settlement was not captured atomically");
                }
                Mc263FeaturesRegion.StructureBatch batch = batch(settlement, startKey, context);
                context.commitStructureBatch(predecessor, predecessor, batch);
                continueDispatcherRandom(dispatcher.random(), false, settlement.loot().size());
                continue;
            }
            long expectedSeed = Mc263DecorationRandom.featureSeed(dispatcher.decorationSeed(),
                    index, STEP);
            if (dispatcher.featureSeed() != expectedSeed) {
                throw new IllegalArgumentException("shipwreck featureSeed mismatch");
            }

            CapturingWorld world = new CapturingWorld(dispatcher.region());
            Mc263ShipwreckPieceProgram.PlacementRandom candidate =
                    Mc263ShipwreckPieceProgram.PlacementRandom.forChunk(
                            dispatcher.worldSeed(), references.chunkX(), references.chunkZ(), index);
            Mc263ShipwreckPieceProgram.AtomicSettlement settlement =
                    Mc263ShipwreckPieceProgram.settle(initial, predecessor, references,
                            Mc263ShipwreckCatalog.PROCESSOR, clip(context.clip()), world, candidate);
            if (world.settlement != settlement) {
                throw new IllegalStateException("shipwreck settlement was not captured atomically");
            }

            Mc263StructureCarrier successor = Mc263StructureCarrier.decode(
                    settlement.successorStr());
            Mc263FeaturesRegion.StructureBatch batch = batch(settlement, startKey, context);
            context.commitStructureBatch(predecessor, successor, batch);
            continueDispatcherRandom(dispatcher.random(), initial.beached(),
                    settlement.loot().size());
        }
    }

    private void requireSchedule(Mc263StructureIndexReceipt.Entry entry,
            Mc263StructureCarrier.Registry registry) {
        Mc263StructureCarrier.StructureDefinition definition = registry.require(key);
        if (entry.step() != STEP || entry.index() != index || !entry.key().equals(key)
                || entry.biomeMask() != biomeMask || definition.registryOrdinal() != ordinal
                || !definition.structureId().equals(key) || definition.decorationStep() != STEP
                || definition.terrainAdjustment()
                        != Mc263StructureCarrier.TerrainAdjustment.NONE
                || !Mc263StructureIndexReceipt.entries().get(ordinal).equals(entry)) {
            throw new IllegalArgumentException("shipwreck schedule/membership/ordinal mismatch: "
                    + key);
        }
    }

    private void requireDispatcher(Mc263FeatureDispatcher.StructurePlacementContext dispatcher,
            Mc263StructureCarrier.ChunkReferences references) {
        if (dispatcher.step() != STEP || dispatcher.globalIndex() != index
                || !dispatcher.structureKey().equals(key)
                || dispatcher.sourceChunkX() != references.chunkX()
                || dispatcher.sourceChunkZ() != references.chunkZ()) {
            throw new IllegalArgumentException("shipwreck dispatcher/source mismatch: " + key);
        }
    }

    private static void requireSourceBinding(Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        if (!Mc263CanonicalFeaturesProducerSkeleton.SourceClip.chunk(
                references.chunkX(), references.chunkZ()).equals(clip)) {
            throw new IllegalArgumentException("shipwreck source clip mismatch");
        }
    }

    private static void requireAcceptedIdentity() {
        if (!Mc263ShipwreckCatalog.ORACLE_SOURCE_SHA256.equals(
                    "51a84a23a13a5bd87c8d95c96b763625dcb589922d4c4feb7987b21a1991483a")
                || !Mc263ShipwreckCatalog.ORACLE_CONTRACT_SHA256.equals(
                    "5f4e6ad13a75b69357ac5b44cd6d9c18f0efc931ef0bdbf583fb68a19785f6eb")
                || !Mc263ShipwreckCatalog.ORACLE_SHA256.equals(
                    "5a439c9e2d3e96927e9a1cde0cc097fd51baef2456b66ef345f5c80726ea9f31")) {
            throw new IllegalStateException("shipwreck accepted shared fixture identity drift");
        }
    }

    private static void validatePersistedStarts(long worldSeed,
            Mc263StructureCarrier carrier) {
        for (Mc263StructureCarrier.ChunkStarts chunk : carrier.startChunks()) {
            for (Mc263StructureCarrier.StartEntry entry : chunk.orderedStarts()) {
                if (!(entry.structureId().equals(Mc263ShipwreckCarrier.OCEAN)
                        || entry.structureId().equals(Mc263ShipwreckCarrier.BEACHED))) continue;
                if (entry.body() instanceof Mc263StructureCarrier.ValidStart valid) {
                    loadPersisted(worldSeed, valid);
                }
            }
        }
    }

    private static Mc263ShipwreckCarrier loadPersisted(long worldSeed,
            Mc263StructureCarrier.ValidStart start) {
        if (start.references() < 0 || start.orderedPieces().size() != 1) {
            throw new IllegalArgumentException("noncanonical persisted shipwreck start");
        }
        Mc263StructureCarrier.Piece piece = start.orderedPieces().getFirst();
        if (!piece.pieceType().equals(Mc263ShipwreckCarrier.PIECE_TYPE)
                || piece.poolElement() || piece.projection()
                        != Mc263StructureCarrier.Projection.NOT_APPLICABLE
                || piece.groundLevelDelta() != 0 || !piece.junctions().isEmpty()) {
            throw new IllegalArgumentException("noncanonical persisted shipwreck piece shape");
        }
        String structureKey = start.startKey().substring(0, start.startKey().lastIndexOf('@'));
        Mc263ShipwreckStartGenerator.Result generated = Mc263ShipwreckStartGenerator.generate(
                structureKey, worldSeed, start.originChunkX(), start.originChunkZ(),
                start.references(), registry(), new StartDeclarations());
        if (generated == null) throw new IllegalArgumentException("shipwreck start biome rejected");
        Mc263ShipwreckCarrier expected = generated.carrier();
        Mc263ShipwreckCarrier decoded = Mc263ShipwreckCarrier.decodePieceNbt(structureKey,
                worldSeed, start.originChunkX(), start.originChunkZ(),
                expected.generationState48(), expected.generationContinuation(),
                piece.persistedPayload().binaryNbtCompound());
        Mc263StructureCarrier.BoundingBox box = piece.boundingBox();
        if (!start.adjustedBoundingBox().equals(box)
                || box.minX() != decoded.boundingBox().minX()
                || box.minY() != decoded.boundingBox().minY()
                || box.minZ() != decoded.boundingBox().minZ()
                || box.maxX() != decoded.boundingBox().maxX()
                || box.maxY() != decoded.boundingBox().maxY()
                || box.maxZ() != decoded.boundingBox().maxZ()
                || !start.startKey().equals(structureKey + "@" + start.originChunkX()
                        + "," + start.originChunkZ())) {
            throw new IllegalArgumentException("shipwreck persisted start binding mismatch");
        }
        return decoded;
    }

    private static Mc263StructureCarrier.Registry registry() {
        ArrayList<Mc263StructureCarrier.StructureDefinition> definitions = new ArrayList<>();
        for (int ordinal = 0; ordinal < Mc263StructureIndexReceipt.entries().size(); ordinal++) {
            Mc263StructureIndexReceipt.Entry entry = Mc263StructureIndexReceipt.entries().get(ordinal);
            definitions.add(new Mc263StructureCarrier.StructureDefinition(entry.key(), ordinal,
                    entry.step(), Mc263StructureCarrier.TerrainAdjustment.NONE));
        }
        return new Mc263StructureCarrier.Registry(definitions);
    }

    private static Mc263ShipwreckPieceProgram.Clip clip(
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip value) {
        return new Mc263ShipwreckPieceProgram.Clip(value.minX(), value.minY(), value.minZ(),
                value.maxX(), value.maxY(), value.maxZ());
    }

    private static Mc263FeaturesRegion.StructureBatch batch(
            Mc263ShipwreckPieceProgram.AtomicSettlement settlement, String startKey,
            Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput placement) {
        long owner = Mc263StructureOwner.owner(settlement.successor().structureKey(), startKey);
        List<Mc263FeaturesRegion.StructureBlockWrite> blocks = settlement.writes().stream()
                .map(value -> new Mc263FeaturesRegion.StructureBlockWrite(value.position().x(),
                        value.position().y(), value.position().z(),
                        canonicalShipwreckState(value.exactState()), owner))
                .toList();
        List<Mc263FeaturesRegion.StructureLoot> loot = settlement.loot().stream()
                .filter(value -> placement.clip().contains(value.position().x(),
                        value.position().y(), value.position().z()))
                .map(value -> new Mc263FeaturesRegion.StructureLoot(value.position().x(),
                        value.position().y(), value.position().z(), value.table(), value.seed(),
                        placement.productionContext(value.position().x(), value.position().y(),
                                value.position().z(), value.table())))
                .toList();
        List<Mc263FeaturesRegion.StructureBentEvidence> bent = settlement.blockEntities().stream()
                .map(value -> new Mc263FeaturesRegion.StructureBentEvidence(value.position().x(),
                        value.position().y(), value.position().z(), value.blockIdentity(),
                        value.entityType(), value.canonicalNbt()))
                .toList();
        return new Mc263FeaturesRegion.StructureBatch(blocks, loot, List.of(), bent);
    }

    private static String canonicalShipwreckState(String exactState) {
        return Mc263FeatureBlockState.canonicalExactState(exactState.replace(", ", ","));
    }

    private static void continueDispatcherRandom(Mc263DecorationRandom.WorldgenRandom random,
            boolean beached, int markerCount) {
        if (beached) random.nextInt(3);
        for (int i = 0; i < markerCount; i++) random.nextLong();
        for (int i = 0; i < markerCount; i++) random.nextLong();
    }

    private static final class StartDeclarations
            implements Mc263ShipwreckStartGenerator.WorldAccess {
        @Override public boolean supportsOceanFloorWg() { return true; }
        @Override public boolean supportsWorldSurfaceWg() { return true; }
        @Override public boolean supportsValidBiomeTest() { return true; }
        @Override public int baseHeight(Mc263ShipwreckStartGenerator.Heightmap ignored,
                int x, int z) { return 0; }
        @Override public boolean isValidBiome(int x, int y, int z) { return true; }
    }

    private static final class CapturingWorld implements Mc263ShipwreckPieceProgram.WorldAccess {
        private final Mc263FeaturesRegion region;
        private Mc263ShipwreckPieceProgram.AtomicSettlement settlement;
        private CapturingWorld(Mc263FeaturesRegion region) {
            this.region = Objects.requireNonNull(region, "FEATURES region");
        }
        @Override public boolean supportsAtomicSettlement() { return true; }
        @Override public boolean supportsExactState(String state) {
            try {
                canonicalShipwreckState(state);
                return true;
            } catch (IllegalArgumentException unsupported) {
                return false;
            }
        }
        @Override public boolean supportsLootTable(String table) {
            return table.equals("minecraft:chests/shipwreck_supply")
                    || table.equals("minecraft:chests/shipwreck_map")
                    || table.equals("minecraft:chests/shipwreck_treasure");
        }
        @Override public boolean supportsBlockEntity(String block, String entity) {
            return block.equals("minecraft:chest") && entity.equals("minecraft:chest");
        }
        @Override public int height(Mc263ShipwreckStartGenerator.Heightmap heightmap,
                int blockX, int blockZ) {
            return switch (heightmap) {
                case OCEAN_FLOOR_WG -> region.oceanFloorWg(blockX, blockZ);
                case WORLD_SURFACE_WG -> region.worldSurfaceWg(blockX, blockZ);
            };
        }
        @Override public void settle(Mc263ShipwreckPieceProgram.AtomicSettlement value) {
            if (settlement != null) throw new IllegalStateException("duplicate shipwreck settlement");
            settlement = Objects.requireNonNull(value, "shipwreck settlement");
        }
    }
}
