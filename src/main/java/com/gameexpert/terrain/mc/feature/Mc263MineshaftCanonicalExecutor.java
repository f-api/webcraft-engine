package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.structure.Mc263MineshaftCorridorPieceExecutor;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftOrderedAggregate;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftStartGenerator;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.LootProductionContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dormant live execution boundary for the pinned normal and mesa mineshaft structures.
 *
 * <p>Direct {@link #settle} requires a final authenticated carrier. FEATURES instead emits one
 * provisional semantic batch and leaves final LDEC to post-dispatch assembly.
 */
public final class Mc263MineshaftCanonicalExecutor {
    private final Mc263MineshaftStartGenerator.Type type;
    private final Mc263StructureIndexReceipt.Entry pinned;

    public Mc263MineshaftCanonicalExecutor(String structureId) {
        type = Mc263MineshaftStartGenerator.Type.fromStructureId(structureId);
        pinned = Mc263StructureIndexReceipt.entries().stream()
                .filter(entry -> entry.key().equals(structureId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "mineshaft is absent from pinned structure receipt: " + structureId));
    }

    public Mc263MineshaftStartGenerator.GeneratedStart generateStart(long worldSeed,
            int chunkX, int chunkZ, int references,
            Mc263MineshaftStartGenerator.SurfaceHeight surfaceHeight) {
        return Mc263MineshaftStartGenerator.generate(worldSeed, chunkX, chunkZ, type,
                references, surfaceHeight);
    }

    public Execution execute(Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip,
            Mc263FeaturesRegion region,
            Mc263MineshaftCorridorPieceExecutor.StructureRandom random) {
        Objects.requireNonNull(carrier, "structure carrier");
        Objects.requireNonNull(references, "structure references");
        Objects.requireNonNull(clip, "source clip");
        Objects.requireNonNull(region, "FEATURES region");
        Objects.requireNonNull(random, "structure random");
        requireBinding(carrier, references, clip);

        Mc263StructureCarrier.ChunkReferences selected =
                new Mc263StructureCarrier.ChunkReferences(references.chunkX(),
                        references.chunkZ(), references.orderedSets().stream()
                                .filter(group -> group.structureId().equals(type.structureId()))
                                .toList());
        Mc263MineshaftFeatureWorldAdapter world =
                Mc263MineshaftFeatureWorldAdapter.buffered(region);
        List<Mc263MineshaftOrderedAggregate.StartEvidence> evidence =
                Mc263MineshaftOrderedAggregate.execute(carrier, selected,
                        new Mc263MineshaftOrderedAggregate.BoundingBox(
                                clip.minX(), clip.minY(), clip.minZ(),
                                clip.maxX(), clip.maxY(), clip.maxZ()),
                        world.worlds(), random);
        List<Mc263FeaturesRegion.StructureBlockWrite> blocks =
                ownedBlocks(world.stagedBlocks(), evidence);
        for (Mc263FeaturesRegion.StructureBlockWrite write : blocks) {
            if (!region.trySetOwnedBlockState(write.blockX(), write.blockY(), write.blockZ(),
                    write.exactState(), write.owner())) {
                throw new IllegalStateException("mineshaft write escaped active source view");
            }
        }
        for (Mc263FeaturesRegion.StructureFluidTick tick : world.stagedFluidTicks()) {
            region.scheduleFluidTick(tick.blockX(), tick.blockY(), tick.blockZ(),
                    tick.fluidKey(), tick.delay());
        }
        for (Mc263FeaturesRegion.StructurePostprocessMark mark : world.stagedPostprocessMarks()) {
            if (!region.markForPostprocessing(mark.blockX(), mark.blockY(), mark.blockZ())) {
                throw new IllegalStateException(
                        "mineshaft postprocessing mark escaped source view");
            }
        }
        return new Execution(carrier, selected, evidence, random.continuation());
    }

    Execution executeBuffered(Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip,
            Mc263FeaturesRegion region,
            Mc263MineshaftCorridorPieceExecutor.StructureRandom random) {
        Objects.requireNonNull(region, "FEATURES region");
        requireBinding(carrier, references, clip);
        Mc263StructureCarrier.ChunkReferences selected =
                new Mc263StructureCarrier.ChunkReferences(references.chunkX(),
                        references.chunkZ(), references.orderedSets().stream()
                                .filter(group -> group.structureId().equals(type.structureId()))
                                .toList());
        Mc263MineshaftFeatureWorldAdapter world =
                Mc263MineshaftFeatureWorldAdapter.buffered(region);
        List<Mc263MineshaftOrderedAggregate.StartEvidence> evidence =
                Mc263MineshaftOrderedAggregate.execute(carrier, selected,
                        new Mc263MineshaftOrderedAggregate.BoundingBox(
                                clip.minX(), clip.minY(), clip.minZ(),
                                clip.maxX(), clip.maxY(), clip.maxZ()),
                        world.worlds(), random);
        return new Execution(carrier, selected, evidence, random.continuation(),
                ownedBlocks(world.stagedBlocks(), evidence), world.stagedFluidTicks(),
                world.stagedPostprocessMarks());
    }

    private List<Mc263FeaturesRegion.StructureBlockWrite> ownedBlocks(
            List<Mc263FeaturesRegion.StructureBlockWrite> blocks,
            List<Mc263MineshaftOrderedAggregate.StartEvidence> evidence) {
        java.util.ArrayList<Mc263FeaturesRegion.StructureBlockWrite> owned =
                new java.util.ArrayList<>(blocks.size());
        int index = 0;
        for (Mc263MineshaftOrderedAggregate.StartEvidence start : evidence) {
            long owner = Mc263StructureOwner.owner(type.structureId(), start.startKey());
            int startWrites = start.orderedPieces().stream()
                    .mapToInt(piece -> writes(piece.execution())).sum();
            for (int end = Math.addExact(index, startWrites); index < end; index++) {
                Mc263FeaturesRegion.StructureBlockWrite value = blocks.get(index);
                owned.add(new Mc263FeaturesRegion.StructureBlockWrite(value.blockX(),
                        value.blockY(), value.blockZ(), value.exactState(), owner));
            }
        }
        if (index != blocks.size()) {
            throw new IllegalStateException("mineshaft write/evidence count mismatch");
        }
        return List.copyOf(owned);
    }

    private static int writes(Mc263MineshaftOrderedAggregate.ExecutionEvidence evidence) {
        return switch (evidence) {
            case Mc263MineshaftOrderedAggregate.RoomEvidence value ->
                    value.execution().writes();
            case Mc263MineshaftOrderedAggregate.CorridorEvidence value ->
                    value.execution().writes();
            case Mc263MineshaftOrderedAggregate.CrossingEvidence value ->
                    value.execution().writes();
            case Mc263MineshaftOrderedAggregate.StairsEvidence value ->
                    value.execution().writes();
        };
    }

    /**
     * Provisional FEATURES settlement.  It deliberately carries semantic batch evidence only:
     * final LOOT/LDEC belongs to the post-dispatch final assembler.
     */
    PreparedSettlement prepareFeaturesBatch(Execution execution,
            Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput placement) {
        Objects.requireNonNull(placement, "active mineshaft placement");
        return prepareFeaturesBatch(execution, placement.clip()::contains,
                placement::productionContext);
    }

    PreparedSettlement prepareFeaturesBatch(Execution execution,
            ProductionContextResolver productionContexts) {
        Objects.requireNonNull(execution, "mineshaft execution");
        return prepareFeaturesBatch(execution,
                Mc263CanonicalFeaturesProducerSkeleton.SourceClip.chunk(
                        execution.references().chunkX(), execution.references().chunkZ())::contains,
                productionContexts);
    }

    PreparedSettlement prepareFeaturesBatch(Execution execution,
            CoordinatePredicate activeSource,
            ProductionContextResolver productionContexts) {
        Objects.requireNonNull(execution, "mineshaft execution");
        Objects.requireNonNull(activeSource, "active mineshaft source clip");
        Objects.requireNonNull(productionContexts, "mineshaft production contexts");
        Mc263MineshaftSettlement.ProvisionalSettlement settlement =
                Mc263MineshaftSettlement.prepareFeaturesSettlement(execution.carrier(),
                        execution.references(), evidenceWithSurvivingSpawners(execution));
        int chunkX = execution.references().chunkX();
        int chunkZ = execution.references().chunkZ();
        List<Mc263FeaturesRegion.StructureSpawner> spawners = settlement.spawners().stream()
                .map(value -> new Mc263FeaturesRegion.StructureSpawner(
                        chunkX * Blocks.CHUNK_X + Math.floorMod(value.packed(), 16),
                        Blocks.MIN_Y + Math.floorDiv(value.packed(), 256),
                        chunkZ * Blocks.CHUNK_Z
                                + Math.floorMod(Math.floorDiv(value.packed(), 16), 16),
                        value.entityType()))
                .toList();
        java.util.ArrayList<Mc263FinalChunkSidecars.StructureEntity> entities =
                new java.util.ArrayList<>();
        java.util.ArrayList<Mc263FeaturesRegion.AuthenticatedStructureEntity>
                authenticatedEntities = new java.util.ArrayList<>();
        for (Mc263MineshaftOrderedAggregate.StartEvidence start : execution.evidence()) {
            for (Mc263MineshaftOrderedAggregate.PieceEvidence piece : start.orderedPieces()) {
                if (!(piece.execution()
                        instanceof Mc263MineshaftOrderedAggregate.CorridorEvidence corridor)) {
                    continue;
                }
                for (Mc263MineshaftCorridorPieceExecutor.ChestMinecartEffect effect
                        : corridor.execution().chestMinecartEffects()) {
                    if (!activeSource.contains(effect.anchor().x(), effect.anchor().y(),
                            effect.anchor().z())) {
                        continue;
                    }
                    Mc263FinalChunkSidecars.StructureEntity entity =
                            Mc263FinalChunkSidecars.StructureEntity.fromChestMinecart(
                                    Mc263FinalChunkAssembler.chestMinecart(effect));
                    entities.add(entity);
                    authenticatedEntities.add(
                            new Mc263FeaturesRegion.AuthenticatedStructureEntity(entity,
                                    productionContexts.locate(effect.anchor().x(),
                                            effect.anchor().y(), effect.anchor().z(),
                                            effect.lootTable())));
                }
            }
        }
        Mc263FeaturesRegion.StructureBatch batch = new Mc263FeaturesRegion.StructureBatch(
                execution.blocks(), List.of(), List.of(), List.of(),
                entities, authenticatedEntities, execution.fluidTicks(),
                execution.postprocessMarks(), spawners, List.of(), List.of());
        return new PreparedSettlement(settlement.structureCarrier(), batch);
    }

    /**
     * A later intersecting piece may replace a successfully placed cave-spider spawner. Preserve
     * the corridor's HPS successor while omitting only a SPWN sidecar whose final staged block is
     * no longer a spawner.
     */
    private static List<Mc263MineshaftOrderedAggregate.StartEvidence>
            evidenceWithSurvivingSpawners(Execution aggregate) {
        Map<Integer, Mc263FeatureBlockState> finalStates = new LinkedHashMap<>();
        for (Mc263FeaturesRegion.StructureBlockWrite write : aggregate.blocks()) {
            int packed = Blocks.blockIndex(Math.floorMod(write.blockX(), Blocks.CHUNK_X),
                    write.blockY(), Math.floorMod(write.blockZ(), Blocks.CHUNK_Z));
            finalStates.put(packed, Mc263FeatureBlockState.fromExact(write.exactState()));
        }
        java.util.ArrayList<Mc263MineshaftOrderedAggregate.StartEvidence> starts =
                new java.util.ArrayList<>(aggregate.evidence().size());
        for (Mc263MineshaftOrderedAggregate.StartEvidence start : aggregate.evidence()) {
            java.util.ArrayList<Mc263MineshaftOrderedAggregate.PieceEvidence> pieces =
                    new java.util.ArrayList<>(start.orderedPieces().size());
            for (Mc263MineshaftOrderedAggregate.PieceEvidence piece : start.orderedPieces()) {
                if (!(piece.execution()
                        instanceof Mc263MineshaftOrderedAggregate.CorridorEvidence corridor)) {
                    pieces.add(piece);
                    continue;
                }
                var value = corridor.execution();
                List<Mc263MineshaftCorridorPieceExecutor.SpawnerEffect> surviving =
                        value.spawnerEffects().stream().filter(effect -> {
                            int packed = Blocks.blockIndex(
                                    Math.floorMod(effect.anchor().x(), Blocks.CHUNK_X),
                                    effect.anchor().y(),
                                    Math.floorMod(effect.anchor().z(), Blocks.CHUNK_Z));
                            Mc263FeatureBlockState state = finalStates.get(packed);
                            return state != null && state.capability()
                                    == Mc263FeatureBlockState.Capability.SPAWNER;
                        }).toList();
                var filtered = new Mc263MineshaftCorridorPieceExecutor.ExecutionResult(
                        value.invalidLocation(), value.writes(), value.finalHasPlacedSpider(),
                        surviving, value.chestMinecartEffects(), value.rngContinuation());
                pieces.add(new Mc263MineshaftOrderedAggregate.PieceEvidence(
                        piece.pieceIndex(), piece.kind(),
                        new Mc263MineshaftOrderedAggregate.CorridorEvidence(filtered,
                                corridor.replacementRequired(), corridor.replacementNbtBytes(),
                                corridor.replacementNbtSha256())));
            }
            starts.add(new Mc263MineshaftOrderedAggregate.StartEvidence(
                    start.startKey(), start.originChunkX(), start.originChunkZ(),
                    start.references(), pieces, start.successorStart()));
        }
        return List.copyOf(starts);
    }


    public Mc263MineshaftSettlement.Settlement settle(Execution execution,
            Mc263FinalChunkCodec.FinalChunk finalChunk) {
        Objects.requireNonNull(execution, "mineshaft execution");
        Objects.requireNonNull(finalChunk, "final chunk");
        return Mc263MineshaftSettlement.settle(execution.carrier(), execution.references(),
                execution.evidence(), finalChunk);
    }

    public String structureId() { return type.structureId(); }

    public Mc263StructureIndexReceipt.Entry pinnedEntry() { return pinned; }

    /**
     * Exact production promotion remains closed until the producer-owned atomic structure batch
     * can carry every side effect emitted by the four pinned mineshaft piece executors.
     * Chest-minecart ENTS is already representable and is intentionally not a blocker.
     */
    static List<String> productionAtomicBatchBlockers() { return List.of(); }

    static void requireProductionAtomicBatchClosure() { }

    private void requireBinding(Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        Mc263CanonicalFeaturesProducerSkeleton.SourceClip expected =
                Mc263CanonicalFeaturesProducerSkeleton.SourceClip.chunk(
                        references.chunkX(), references.chunkZ());
        if (!expected.equals(clip)) {
            throw new IllegalArgumentException("mineshaft source clip mismatch");
        }
        Mc263StructureCarrier.StructureDefinition definition =
                carrier.registry().require(type.structureId());
        int ordinal = Mc263StructureIndexReceipt.entries().indexOf(pinned);
        if (definition.registryOrdinal() != ordinal
                || definition.decorationStep() != pinned.step()
                || definition.terrainAdjustment()
                        != Mc263StructureCarrier.TerrainAdjustment.NONE) {
            throw new IllegalArgumentException(
                    "mineshaft carrier registry mismatch: " + type.structureId());
        }
    }

    public static final class Execution {
        private final Mc263StructureCarrier carrier;
        private final Mc263StructureCarrier.ChunkReferences references;
        private final List<Mc263MineshaftOrderedAggregate.StartEvidence> evidence;
        private final Mc263MineshaftCorridorPieceExecutor.RngContinuation rngContinuation;
        private final List<Mc263FeaturesRegion.StructureBlockWrite> blocks;
        private final List<Mc263FeaturesRegion.StructureFluidTick> fluidTicks;
        private final List<Mc263FeaturesRegion.StructurePostprocessMark> postprocessMarks;

        private Execution(Mc263StructureCarrier carrier,
                Mc263StructureCarrier.ChunkReferences references,
                List<Mc263MineshaftOrderedAggregate.StartEvidence> evidence,
                Mc263MineshaftCorridorPieceExecutor.RngContinuation rngContinuation) {
            this(carrier, references, evidence, rngContinuation, List.of(), List.of(),
                    List.of());
        }

        private Execution(Mc263StructureCarrier carrier,
                Mc263StructureCarrier.ChunkReferences references,
                List<Mc263MineshaftOrderedAggregate.StartEvidence> evidence,
                Mc263MineshaftCorridorPieceExecutor.RngContinuation rngContinuation,
                List<Mc263FeaturesRegion.StructureBlockWrite> blocks,
                List<Mc263FeaturesRegion.StructureFluidTick> fluidTicks,
                List<Mc263FeaturesRegion.StructurePostprocessMark> postprocessMarks) {
            this.carrier = carrier;
            this.references = references;
            this.evidence = List.copyOf(evidence);
            this.rngContinuation = rngContinuation;
            this.blocks = List.copyOf(blocks);
            this.fluidTicks = List.copyOf(fluidTicks);
            this.postprocessMarks = List.copyOf(postprocessMarks);
        }

        public Mc263StructureCarrier carrier() { return carrier; }
        public Mc263StructureCarrier.ChunkReferences references() { return references; }
        public List<Mc263MineshaftOrderedAggregate.StartEvidence> evidence() { return evidence; }
        public Mc263MineshaftCorridorPieceExecutor.RngContinuation rngContinuation() {
            return rngContinuation;
        }
        List<Mc263FeaturesRegion.StructureBlockWrite> blocks() { return blocks; }
        List<Mc263FeaturesRegion.StructureFluidTick> fluidTicks() { return fluidTicks; }
        List<Mc263FeaturesRegion.StructurePostprocessMark> postprocessMarks() {
            return postprocessMarks;
        }
    }

    record PreparedSettlement(Mc263StructureCarrier structureCarrier,
                              Mc263FeaturesRegion.StructureBatch batch) { }

    @FunctionalInterface
    interface ProductionContextResolver {
        LootProductionContext locate(int blockX, int blockY, int blockZ, String lootTable);
    }

    @FunctionalInterface
    interface CoordinatePredicate {
        boolean contains(int blockX, int blockY, int blockZ);
    }
}
