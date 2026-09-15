package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor;
import com.gameexpert.terrain.mc.structure.Mc263VillageEntityAuthority;
import com.gameexpert.terrain.mc.structure.Mc263VillagePileFeatureAuthority;
import com.gameexpert.terrain.mc.structure.Mc263VillageProducer;
import com.gameexpert.terrain.mc.structure.Mc263VillageProductionAdapters;
import com.gameexpert.terrain.mc.structure.Mc263VillageProductionTransaction;
import com.gameexpert.terrain.mc.structure.Mc263VillageSettlement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Production persisted-plan executor shared by all five pinned Village structures. */
final class Mc263VillageCanonicalExecutor implements
        Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutor {
    private static final int STEP = 4;
    private static final int FIRST_INDEX = 39;
    private static final int FIRST_ORDINAL = 44;
    private static final List<String> STRUCTURES = List.of(
            "minecraft:village_desert", "minecraft:village_plains",
            "minecraft:village_savanna", "minecraft:village_snowy",
            "minecraft:village_taiga");
    private static final Set<String> BLOCK_ENTITY_TYPES = Set.of(
            "minecraft:banner", "minecraft:barrel", "minecraft:bell",
            "minecraft:blast_furnace", "minecraft:brewing_stand", "minecraft:campfire",
            "minecraft:chest", "minecraft:furnace", "minecraft:lectern",
            "minecraft:sign", "minecraft:smoker");

    private final String structure;
    private final int index;
    private final int registryOrdinal;

    private Mc263VillageCanonicalExecutor(String structure, int index, int registryOrdinal) {
        this.structure = structure;
        this.index = index;
        this.registryOrdinal = registryOrdinal;
    }

    static void register(
            Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry registry) {
        Objects.requireNonNull(registry, "canonical structure executor registry");
        for (int ordinal = 0; ordinal < STRUCTURES.size(); ordinal++) {
            registry.register(STRUCTURES.get(ordinal), new Mc263VillageCanonicalExecutor(
                    STRUCTURES.get(ordinal), FIRST_INDEX + ordinal, FIRST_ORDINAL + ordinal));
        }
    }

    @Override
    public void preflight(
            Mc263CanonicalFeaturesProducerSkeleton.StructurePreflightContext context) {
        Objects.requireNonNull(context, "Village preflight context");
        requireSchedule(context.entry(), context.carrier().registry());
        requireSourceBinding(context.references(), context.clip());
        for (Mc263StructureCarrier.ValidStart start
                : referencedStarts(context.carrier(), context.references())) {
            Mc263StructureCarrier.RawStartPayload raw =
                    context.carrier().requireRawStartPayload(structure, start);
            Mc263WorldGenRegionRandom random = random(context.worldGenRegionRandomState());
            try {
                adapter().executePersisted(persisted(context.carrier(), context.references(), start,
                                raw, context.worldSeed(), context.clip()),
                        Transaction.preflight(structure, context.carrier()),
                        context.worldGenRegionRandomState(), random);
                throw new IllegalStateException("Village preflight unexpectedly published");
            } catch (PreflightComplete expected) {
                // Pure capabilities, persisted STR/STRGRF01 and successor validation completed.
            }
        }
    }

    @Override
    public void place(Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context) {
        Objects.requireNonNull(context, "Village placement context");
        requireDispatcher(context.dispatcher(), context.references(), context.clip());
        requireSchedule(Mc263StructureIndexReceipt.step(STEP).get(index),
                context.carrier().registry());
        for (String startKey : referencedStarts(context.carrier(), context.references()).stream()
                .map(Mc263StructureCarrier.ValidStart::startKey).toList()) {
            Mc263StructureCarrier predecessor = context.carrier();
            Mc263StructureCarrier.ValidStart start = referencedStarts(
                    predecessor, context.references()).stream()
                    .filter(value -> value.startKey().equals(startKey)).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "evolved Village carrier lost referenced start"));
            Mc263StructureCarrier.RawStartPayload raw =
                    predecessor.requireRawStartPayload(structure, start);
            Mc263CanonicalFeaturesProducerSkeleton.WorldGenRegionRandomFork randomFork =
                    context.forkWorldGenRegionRandom();
            Transaction transaction = Transaction.live(structure, context, predecessor, randomFork);
            adapter().executePersisted(persisted(predecessor, context.references(), start, raw,
                            context.dispatcher().worldSeed(), context.clip()), transaction,
                    randomFork.predecessor(), randomFork.random());
        }
    }

    private Mc263VillageProductionAdapters.Adapter adapter() {
        return Mc263VillageProductionAdapters.require(structure);
    }

    private void requireSchedule(Mc263StructureIndexReceipt.Entry entry,
            Mc263StructureCarrier.Registry registry) {
        Mc263StructureCarrier.StructureDefinition definition = registry.require(structure);
        Mc263StructureIndexReceipt.Entry expected =
                Mc263StructureIndexReceipt.entries().get(registryOrdinal);
        if (!expected.equals(entry) || entry.step() != STEP || entry.index() != index
                || !entry.key().equals(structure)
                || definition.registryOrdinal() != registryOrdinal
                || !definition.structureId().equals(structure)
                || definition.decorationStep() != STEP
                || definition.terrainAdjustment()
                        != Mc263StructureCarrier.TerrainAdjustment.BEARD_THIN) {
            throw new IllegalArgumentException("Village schedule/membership/ordinal mismatch");
        }
    }

    private void requireDispatcher(Mc263FeatureDispatcher.StructurePlacementContext dispatcher,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        long expectedSeed = Mc263DecorationRandom.featureSeed(
                dispatcher.decorationSeed(), index, STEP);
        if (dispatcher.step() != STEP || dispatcher.globalIndex() != index
                || !dispatcher.structureKey().equals(structure)
                || dispatcher.sourceChunkX() != references.chunkX()
                || dispatcher.sourceChunkZ() != references.chunkZ()
                || dispatcher.featureSeed() != expectedSeed) {
            throw new IllegalArgumentException("Village dispatcher/source/RNG mismatch");
        }
        requireSourceBinding(references, clip);
    }

    private static void requireSourceBinding(Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        if (!Mc263CanonicalFeaturesProducerSkeleton.SourceClip.chunk(
                references.chunkX(), references.chunkZ()).equals(clip)) {
            throw new IllegalArgumentException("Village source clip mismatch");
        }
    }

    /**
     * The village starts this source chunk references, in reference-closure order.
     *
     * <p>{@code ChunkGenerator#applyBiomeDecoration} binds one feature seed per (chunk, structure,
     * decoration step) and then places every element of {@code
     * StructureManager#startsForStructure(SectionPos, Structure)} -- a {@code List}, filled from
     * the chunk's whole {@code LongSet} of references -- through one shared {@code WorldgenRandom}.
     * Two starts of one village structure whose adjusted boxes both intersect a single chunk are
     * therefore authentic, not a carrier defect: village placement spacing 34 / separation 8 lets
     * two neighbouring region starts sit ten chunks apart, and a chunk column between their boxes
     * references both. Only a repeated start is rejected, because {@code ChunkStarts} holds at most
     * one start per (origin chunk, structure) and {@code ReferenceSet} forbids a duplicate origin,
     * so a repeat would prove a corrupt closure rather than two villages.</p>
     */
    private List<Mc263StructureCarrier.ValidStart> referencedStarts(
            Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ChunkReferences references) {
        List<Mc263StructureCarrier.ValidStart> starts = carrier.resolveStarts(references, structure);
        long distinct = starts.stream()
                .map(Mc263StructureCarrier.ValidStart::startKey).distinct().count();
        if (distinct != starts.size()) {
            throw new IllegalArgumentException("Village reference closure repeats a start");
        }
        return starts;
    }

    private Mc263VillageSettlement.PersistedRequest persisted(Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263StructureCarrier.ValidStart start,
            Mc263StructureCarrier.RawStartPayload raw, long worldSeed,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        int sourceX = Math.floorDiv(clip.minX(), Blocks.CHUNK_X);
        int sourceZ = Math.floorDiv(clip.minZ(), Blocks.CHUNK_Z);
        int minX = Math.floorDiv(start.adjustedBoundingBox().minX(), Blocks.CHUNK_X);
        int minZ = Math.floorDiv(start.adjustedBoundingBox().minZ(), Blocks.CHUNK_Z);
        int width = Math.floorDiv(start.adjustedBoundingBox().maxX(), Blocks.CHUNK_X) - minX + 1;
        int clipOrdinal = Math.addExact(Math.multiplyExact(sourceZ - minZ, width), sourceX - minX);
        Mc263VillageSettlement.Request request = new Mc263VillageSettlement.Request(structure,
                worldSeed, start.originChunkX(), start.originChunkZ(), sourceX, sourceZ, clipOrdinal);
        return new Mc263VillageSettlement.PersistedRequest(
                request, carrier, references, start, raw);
    }

    private static Mc263WorldGenRegionRandom random(Mc263WorldGenRegionRandom.State state) {
        return Mc263WorldGenRegionRandom.fromState(state.lo(), state.hi(), state.drawCount(),
                state.gaussianPresent(), state.gaussianBits());
    }

    private static final class Transaction extends Mc263VillageProductionTransaction.Host {
        private final String structure;
        private final Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context;
        private final Mc263StructureCarrier predecessor;
        private final Mc263CanonicalFeaturesProducerSkeleton.WorldGenRegionRandomFork randomFork;
        private final Mc263FeaturesRegion region;
        private final boolean isolated;
        private final Map<Mc263TemplatePlacementExecutor.Vec, String> overlay;
        private final Map<Mc263TemplatePlacementExecutor.Vec, MutableBlockEntity> blockEntities;
        private final List<Mc263VillageEntityAuthority.GeneratedEntity> generatedEntities;
        private final Mc263TemplatePlacementExecutor.PlacementWorld placementWorld;

        private Transaction(String structure,
                Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context,
                Mc263StructureCarrier predecessor,
                Mc263CanonicalFeaturesProducerSkeleton.WorldGenRegionRandomFork randomFork,
                Mc263FeaturesRegion region, boolean isolated,
                Map<Mc263TemplatePlacementExecutor.Vec, String> overlay,
                Map<Mc263TemplatePlacementExecutor.Vec, MutableBlockEntity> blockEntities,
                List<Mc263VillageEntityAuthority.GeneratedEntity> generatedEntities) {
            this.structure = structure;
            this.context = context;
            this.predecessor = predecessor;
            this.randomFork = randomFork;
            this.region = region;
            this.isolated = isolated;
            this.overlay = overlay;
            this.blockEntities = blockEntities;
            this.generatedEntities = generatedEntities;
            this.placementWorld = new PlacementWorld();
        }

        static Transaction preflight(String structure, Mc263StructureCarrier predecessor) {
            return new Transaction(structure, null, predecessor, null, null, false,
                    Map.of(), Map.of(), List.of());
        }

        static Transaction live(String structure,
                Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context,
                Mc263StructureCarrier predecessor,
                Mc263CanonicalFeaturesProducerSkeleton.WorldGenRegionRandomFork randomFork) {
            return new Transaction(structure, context, predecessor, randomFork,
                    context.dispatcher().region(), false, new LinkedHashMap<>(),
                    new LinkedHashMap<>(), new ArrayList<>());
        }

        @Override public boolean supportsAtomicForkPublishWithRandom() { return false; }
        @Override public boolean supportsPersistedWorldGenRegionRandomSettlement() { return true; }
        @Override public boolean supportsHeightmap(String value) {
            return value.equals("WORLD_SURFACE_WG");
        }
        @Override public boolean supportsBuildHeightBoundary() { return true; }
        @Override public boolean supportsTemplateExecution() { return true; }
        @Override public boolean supportsConfiguredFeatureExecution() { return true; }
        @Override public boolean supportsEntityExecution() { return true; }
        @Override public boolean supportsTypedSidecars() { return true; }
        @Override public boolean supportsRawBlockTickTranscript() { return true; }
        @Override public boolean supportsEffectiveBlockTickCarrier() { return true; }
        @Override public boolean supportsOwnerLane() { return true; }
        @Override public boolean supportsEmptyUnauthenticatedLanes() { return true; }
        @Override public boolean supportsWriteFlags(int flags) {
            return flags == Mc263TemplatePlacementExecutor.TEMPLATE_WRITE_FLAGS
                    || flags == Mc263TemplatePlacementExecutor.BLOCK_ENTITY_CLEAR_FLAGS
                    || flags == Mc263VillagePileFeatureAuthority.WRITE_FLAGS;
        }
        @Override public boolean supportsLevelChunkTicksAuthority(String sha256) {
            return Mc263VillageSettlement.LEVEL_CHUNK_TICKS_SHA256.equals(sha256);
        }
        @Override public boolean supportsBiomeAdmission(String key, String tag) { return false; }
        @Override public boolean supportsPool(String key) { return false; }
        @Override public boolean supportsTemplate(String key) { return true; }
        @Override public boolean supportsProcessorList(String key) { return true; }
        @Override public boolean supportsProcessorSemantic(String semantic) { return true; }
        @Override public boolean supportsConfiguredFeature(String key) { return true; }
        @Override public boolean supportsExactState(String state) {
            return Mc263FeatureBlockState.supportsExactState(state);
        }
        @Override public boolean supportsLootTable(String table) { return true; }
        @Override public boolean supportsBlockEntity(String type) {
            return BLOCK_ENTITY_TYPES.contains(type);
        }
        @Override public boolean supportsStructureEntity(String key) {
            return Mc263VillageEntityAuthority.AUTHENTICATED_ENTITY_TYPES.contains(key);
        }
        @Override public boolean supportsBlockTick(String key, int delay, int priority) {
            return delay >= 0;
        }
        @Override public boolean supportsRuleTag(String tag) { return true; }
        @Override public boolean supportsAuthenticatedConfiguredFeature(String key, String target) {
            return true;
        }
        @Override public boolean supportsPileFeatureQuery(
                Mc263VillagePileFeatureAuthority.Query query) { return true; }
        @Override public boolean supportsWritableChunkRadius(int radius) {
            return radius == Mc263VillagePileFeatureAuthority.WRITABLE_CHUNK_RADIUS;
        }
        @Override public int minBuildY() { return -64; }
        @Override public int maxBuildY() { return 320; }
        @Override public int baseHeight(String heightmap, int x, int z) {
            throw new AssertionError("persisted Village called planning height");
        }
        @Override public Mc263VillageProducer.BiomeSample actualBiome(String key, String tag,
                int x, int y, int z) {
            throw new AssertionError("persisted Village called planning biome");
        }
        @Override public boolean blockInRuleTag(String block, String tag) { return false; }

        @Override
        public Mc263VillageProductionAdapters.ProductionWorldTransaction fork() {
            if (context == null) throw new PreflightComplete();
            if (isolated) throw new IllegalStateException("nested Village production fork");
            return new Transaction(structure, context, predecessor, randomFork, region, true,
                    new LinkedHashMap<>(overlay), new LinkedHashMap<>(), new ArrayList<>());
        }

        @Override protected Mc263TemplatePlacementExecutor.PlacementWorld placementWorld() {
            requireIsolated();
            return placementWorld;
        }
        @Override protected Mc263TemplatePlacementExecutor.Clip placementClip() {
            requireIsolated();
            return Mc263TemplatePlacementExecutor.Clip.chunk(context.references().chunkX(),
                    context.references().chunkZ(), minBuildY(), maxBuildY() - 1);
        }
        @Override protected Mc263FeatureWorldAdapter featureWorld() {
            requireIsolated();
            return new Mc263FeatureWorldAdapter(context.dispatcher().worldSeed(), region);
        }
        @Override protected Mc263VillageSettlement.ExecutionResult drainFeatureEffects() {
            return Mc263VillageSettlement.ExecutionResult.empty();
        }
        @Override protected String structureKey() { return structure; }
        @Override protected void generatedStructureEntity(
                Mc263VillageEntityAuthority.GeneratedEntity generated) {
            generatedEntities.add(generated);
        }

        @Override public Mc263VillageSettlement.ExecutionResult executeTemplateCell(
                Mc263VillageSettlement.CellPlacement placement,
                Mc263VillageSettlement.PlacementRandom random) {
            throw new AssertionError("persisted Village used legacy template path");
        }
        @Override public Mc263VillageSettlement.ExecutionResult executeConfiguredFeature(
                Mc263VillageSettlement.FeaturePlacement placement,
                Mc263VillageSettlement.PlacementRandom random) {
            throw new AssertionError("persisted Village used legacy feature path");
        }
        @Override public Mc263VillageSettlement.ExecutionResult executeStructureEntity(
                Mc263VillageSettlement.EntityPlacement placement,
                Mc263VillageSettlement.PlacementRandom random) {
            throw new AssertionError("persisted Village used legacy entity path");
        }
        @Override public Mc263VillageSettlement.PublishStatus publishAtomically(
                Mc263VillageSettlement.WorldTransaction isolated,
                Mc263VillageSettlement.Settlement settlement,
                Mc263VillageSettlement.PlacementRandom caller,
                Mc263VillageSettlement.PlacementRandom accepted) {
            throw new AssertionError("persisted Village used legacy publisher");
        }

        @Override
        public Mc263VillageSettlement.PersistedPublishReceipt publishPersistedAtomically(
                Mc263VillageSettlement.WorldTransaction isolatedWorld,
                Mc263VillageSettlement.PersistedSettlement settlement,
                Mc263WorldGenRegionRandom.State exactPredecessor,
                Mc263WorldGenRegionRandom acceptedRandom) {
            if (!(isolatedWorld instanceof Transaction staged) || !staged.isolated
                    || staged.context != context || staged.predecessor != predecessor) {
                throw new IllegalStateException("invalid isolated Village transaction");
            }
            if (!context.worldGenRegionRandomState().equals(exactPredecessor)
                    || acceptedRandom != randomFork.random()) {
                throw new IllegalStateException("stale Village STR/WGR predecessor");
            }
            if (!acceptedRandom.snapshot().equals(settlement.randomSuccessor())) {
                throw new IllegalStateException("Village accepted WGR successor drift");
            }
            context.commitStructureBatchWithWorldGenRegionRandom(predecessor,
                    settlement.carrierSuccessor(), batch(settlement, staged, context), randomFork);
            return new Mc263VillageSettlement.PersistedPublishReceipt(
                    Mc263VillageSettlement.PublishStatus.COMMITTED,
                    settlement.carrierSuccessor().receiptSha256(),
                    settlement.rawPredecessorSha256(), settlement.rawSuccessorSha256(),
                    settlement.randomPredecessor(), settlement.randomSuccessor());
        }

        private void requireIsolated() {
            if (!isolated) throw new AssertionError("live Village operation escaped fork");
        }

        private Mc263FeatureBlockState state(Mc263TemplatePlacementExecutor.Vec position) {
            String staged = overlay.get(position);
            return staged == null ? region.blockState(position.x(), position.y(), position.z())
                    : Mc263FeatureBlockState.fromExact(staged);
        }

        private final class PlacementWorld implements
                Mc263TemplatePlacementExecutor.PlacementWorld {
            @Override public int getHeight(String heightmap, int x, int z) {
                return region.worldSurfaceWg(x, z);
            }
            @Override public String getBlockState(Mc263TemplatePlacementExecutor.Vec position) {
                return state(position).exactState();
            }
            @Override public String getFluidKey(Mc263TemplatePlacementExecutor.Vec position) {
                return switch (state(position).fluidKind()) {
                    case WATER_SOURCE, WATER_FLOWING -> "minecraft:water";
                    case LAVA_SOURCE, LAVA_FLOWING -> "minecraft:lava";
                    case NONE -> Mc263TemplatePlacementExecutor.EMPTY_FLUID;
                };
            }
            @Override public boolean isFluidSource(Mc263TemplatePlacementExecutor.Vec position) {
                return switch (state(position).fluidKind()) {
                    case WATER_SOURCE, LAVA_SOURCE -> true;
                    default -> false;
                };
            }
            @Override public double fluidHeight(Mc263TemplatePlacementExecutor.Vec position) {
                return state(position).fluidKind() == Mc263FeatureBlockState.FluidKind.NONE
                        ? 0.0D : 1.0D;
            }
            @Override public boolean setBlock(Mc263TemplatePlacementExecutor.Vec position,
                    String exactState, int flags) {
                String previous = state(position).exactState();
                overlay.put(position, exactState);
                String type = blockEntityType(exactState);
                if (type == null) {
                    blockEntities.remove(position);
                    return true;
                }
                // LevelChunk#setBlockState only drops the block entity when
                // BlockState#shouldChangedStateKeepBlockEntity is false, i.e. when the block
                // itself changed. A property-only rewrite -- the keepLiquids waterlogging pass
                // rewriting an already placed sign/campfire -- keeps the block entity and the NBT
                // placeInWorld already loaded into it, so setChanged() still has a loaded payload.
                MutableBlockEntity existing = blockEntities.get(position);
                if (existing != null && existing.blockEntityType().equals(type)
                        && blockKey(previous).equals(blockKey(exactState))) {
                    return true;
                }
                blockEntities.put(position, new MutableBlockEntity(type));
                return true;
            }
            @Override public Mc263TemplatePlacementExecutor.BlockEntityHandle getBlockEntity(
                    Mc263TemplatePlacementExecutor.Vec position) {
                return blockEntities.get(position);
            }
            @Override public void scheduleFluidTick(Mc263TemplatePlacementExecutor.Vec position,
                    String fluidKey, int delay) { }
        }
    }

    private static Mc263FeaturesRegion.StructureBatch batch(
            Mc263VillageSettlement.PersistedSettlement settlement, Transaction staged,
            Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput placement) {
        // A chunk may reference two starts of one village structure, so the batch owner is bound
        // to the start this settlement was produced from -- never to the closure's first start.
        Mc263StructureCarrier.ValidStart start = staged.predecessor.resolveStarts(
                staged.context.references(), staged.structure).stream()
                .filter(value -> value.originChunkX() == settlement.request().startChunkX()
                        && value.originChunkZ() == settlement.request().startChunkZ())
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Village settlement start left the reference closure"));
        long owner = Mc263StructureOwner.owner(staged.structure, start.startKey());
        List<Mc263FeaturesRegion.StructureBlockWrite> blocks = settlement.carrier().finalWrites()
                .stream().map(value -> new Mc263FeaturesRegion.StructureBlockWrite(
                        value.position().x(), value.position().y(), value.position().z(),
                        value.exactState(), owner)).toList();
        Map<Mc263VillageSettlement.Position, String> states = new LinkedHashMap<>();
        settlement.carrier().finalWrites().forEach(value ->
                states.put(value.position(), value.exactState()));
        List<Mc263FeaturesRegion.StructureBentEvidence> bent = settlement.carrier().bent().stream()
                .map(value -> new Mc263FeaturesRegion.StructureBentEvidence(value.position().x(),
                        value.position().y(), value.position().z(),
                        blockKey(Objects.requireNonNull(states.get(value.position()),
                                "Village BENT lost final block")), value.blockEntityType(),
                        value.canonicalNbt())).toList();
        List<Mc263FeaturesRegion.StructureLoot> loot = settlement.carrier().loot().stream()
                .filter(value -> placement.clip().contains(value.position().x(),
                        value.position().y(), value.position().z()))
                .map(value -> new Mc263FeaturesRegion.StructureLoot(value.position().x(),
                        value.position().y(), value.position().z(), value.lootTable(),
                        value.seed(), placement.productionContext(value.position().x(),
                                value.position().y(), value.position().z(), value.lootTable())))
                .toList();
        int targetX = staged.region.targetChunkX(), targetZ = staged.region.targetChunkZ();
        List<Mc263FinalChunkSidecars.StructureEntity> entities = staged.generatedEntities.stream()
                .filter(value -> Math.floorDiv((int) Math.floor(
                                Double.longBitsToDouble(value.positionBits()[0])), 16) == targetX
                        && Math.floorDiv((int) Math.floor(
                                Double.longBitsToDouble(value.positionBits()[2])), 16) == targetZ)
                .map(Mc263VillageCanonicalExecutor::entity).toList();
        Mc263VillageSettlement.Chunk targetChunk =
                new Mc263VillageSettlement.Chunk(targetX, targetZ);
        List<Mc263FeaturesRegion.StructureBlockTick> ticks = Mc263VillageSettlement
                .publicationTicks(settlement.carrier().effectiveBlockTicks(), targetChunk).stream()
                .map(value -> new Mc263FeaturesRegion.StructureBlockTick(value.position().x(),
                        value.position().y(), value.position().z(), value.blockKey(), value.delay(),
                        value.priority(), value.subTickOrder())).toList();
        // A template-restored fluid source schedules a LevelChunkTicks<Fluid> row, not a block
        // tick, so the FTIK lane publishes through the batch's fluid-tick slot.
        List<Mc263FeaturesRegion.StructureFluidTick> fluidTicks = Mc263VillageSettlement
                .publicationTicks(settlement.carrier().effectiveFluidTicks(), targetChunk).stream()
                .map(value -> new Mc263FeaturesRegion.StructureFluidTick(value.position().x(),
                        value.position().y(), value.position().z(), value.blockKey(), value.delay(),
                        value.priority(), value.subTickOrder())).toList();
        return new Mc263FeaturesRegion.StructureBatch(blocks, loot, List.of(), bent, entities,
                fluidTicks, List.of(), List.of(), ticks, List.of());
    }

    private static Mc263FinalChunkSidecars.StructureEntity entity(
            Mc263VillageEntityAuthority.GeneratedEntity value) {
        long[] position = value.positionBits();
        int[] rotation = value.rotationBits();
        long[] motion = value.motionBits();
        return new Mc263FinalChunkSidecars.StructureEntity(value.entityKey(), value.spawnReason(),
                Double.longBitsToDouble(position[0]), Double.longBitsToDouble(position[1]),
                Double.longBitsToDouble(position[2]), Float.intBitsToFloat(rotation[0]),
                Float.intBitsToFloat(rotation[1]), Double.longBitsToDouble(motion[0]),
                Double.longBitsToDouble(motion[1]), Double.longBitsToDouble(motion[2]),
                value.canonicalNbt());
    }

    private static String blockEntityType(String state) {
        return switch (blockKey(state)) {
            case "minecraft:barrel" -> "minecraft:barrel";
            case "minecraft:bell" -> "minecraft:bell";
            case "minecraft:blast_furnace" -> "minecraft:blast_furnace";
            case "minecraft:brewing_stand" -> "minecraft:brewing_stand";
            case "minecraft:campfire" -> "minecraft:campfire";
            case "minecraft:chest" -> "minecraft:chest";
            case "minecraft:furnace" -> "minecraft:furnace";
            case "minecraft:lectern" -> "minecraft:lectern";
            case "minecraft:smoker" -> "minecraft:smoker";
            default -> state.contains("banner") ? "minecraft:banner"
                    : state.contains("sign") ? "minecraft:sign" : null;
        };
    }

    private static String blockKey(String state) {
        int property = state.indexOf('[');
        return property < 0 ? state : state.substring(0, property);
    }

    private static final class MutableBlockEntity implements
            Mc263TemplatePlacementExecutor.BlockEntityHandle {
        private final String type;
        private byte[] nbt;
        private MutableBlockEntity(String type) { this.type = type; }
        @Override public String blockEntityType() { return type; }
        @Override public void loadCanonicalNbt(byte[] canonicalNbt) {
            nbt = Objects.requireNonNull(canonicalNbt).clone();
        }
        @Override public void setChanged() {
            if (nbt == null) throw new IllegalStateException("Village BENT changed before load");
        }
    }

    private static final class PreflightComplete extends RuntimeException { }
}
