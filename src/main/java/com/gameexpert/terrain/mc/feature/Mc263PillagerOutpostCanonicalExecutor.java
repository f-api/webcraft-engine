package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.structure.Mc263PillagerOutpostEntityAuthority;
import com.gameexpert.terrain.mc.structure.Mc263PillagerOutpostProducer;
import com.gameexpert.terrain.mc.structure.Mc263PillagerOutpostSettlement;
import com.gameexpert.terrain.mc.structure.Mc263StructureBlockEntityNbtAuthority;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Production adapter for the accepted pinned Pillager Outpost persisted settlement. */
final class Mc263PillagerOutpostCanonicalExecutor implements
        Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutor {
    private static final String STRUCTURE = Mc263PillagerOutpostProducer.STRUCTURE_KEY;
    private static final int STEP = 4;
    private static final int INDEX = 27;
    private static final int REGISTRY_ORDINAL = 32;
    private static final long BIOME_MASK = 0x0c006900e26000L;

    static void register(
            Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry registry) {
        Objects.requireNonNull(registry, "canonical structure executor registry")
                .register(STRUCTURE, new Mc263PillagerOutpostCanonicalExecutor());
    }

    @Override
    public void preflight(
            Mc263CanonicalFeaturesProducerSkeleton.StructurePreflightContext context) {
        Objects.requireNonNull(context, "Pillager Outpost preflight context");
        requireCapabilities();
        requireSchedule(context.entry(), context.carrier().registry());
        requireSourceBinding(context.references(), context.clip());
        for (Mc263StructureCarrier.ValidStart start : referencedStarts(
                context.carrier(), context.references())) {
            Mc263StructureCarrier.RawStartPayload raw =
                    context.carrier().requireRawStartPayload(STRUCTURE, start);
            Mc263WorldGenRegionRandom random = random(context.worldGenRegionRandomState());
            try {
                Mc263PillagerOutpostSettlement.executePersisted(start, raw,
                        orderedReferences(context.references()), clip(start, context.clip()),
                        context.carrier(), Transaction.preflight(context.carrier(),
                                context.worldGenRegionRandomState()), random);
                throw new IllegalStateException("Outpost preflight unexpectedly published");
            } catch (PreflightComplete expected) {
                // executePersisted completed all pure capability/STR/reference/CAS validation.
            }
        }
    }

    @Override
    public void place(Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context) {
        Objects.requireNonNull(context, "Pillager Outpost placement context");
        requireCapabilities();
        Mc263FeatureDispatcher.StructurePlacementContext dispatcher = context.dispatcher();
        requireDispatcher(dispatcher, context.references(), context.clip());
        requireSchedule(Mc263StructureIndexReceipt.step(STEP).get(INDEX),
                context.carrier().registry());
        List<Mc263StructureCarrier.ValidStart> starts = referencedStarts(
                context.carrier(), context.references());
        for (String startKey : starts.stream()
                .map(Mc263StructureCarrier.ValidStart::startKey).toList()) {
            Mc263StructureCarrier predecessor = context.carrier();
            Mc263StructureCarrier.ValidStart start = referencedStarts(
                    predecessor, context.references()).stream()
                    .filter(value -> value.startKey().equals(startKey)).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "evolved Pillager Outpost carrier lost referenced start"));
            Mc263StructureCarrier.RawStartPayload raw =
                    predecessor.requireRawStartPayload(STRUCTURE, start);
            Mc263CanonicalFeaturesProducerSkeleton.WorldGenRegionRandomFork randomFork =
                    context.forkWorldGenRegionRandom();
            Transaction transaction = Transaction.live(context, predecessor, startKey, randomFork);
            Mc263PillagerOutpostSettlement.executePersisted(start, raw,
                    orderedReferences(context.references()), clip(start, context.clip()), predecessor,
                    transaction, randomFork.random());
        }
    }

    private static void requireCapabilities() {
        // This validates the adapter-owned schema lanes without touching live world state.
        if (!Mc263FeatureBlockState.supportsExactState("minecraft:air")
                || !Mc263FeatureBlockState.supportsExactState("minecraft:barrier")) {
            throw new UnsupportedOperationException(
                    "Pillager Outpost exact-state capability is absent");
        }
    }

    private static void requireSchedule(Mc263StructureIndexReceipt.Entry entry,
            Mc263StructureCarrier.Registry registry) {
        Mc263StructureCarrier.StructureDefinition definition = registry.require(STRUCTURE);
        Mc263StructureIndexReceipt.Entry expected =
                Mc263StructureIndexReceipt.entries().get(REGISTRY_ORDINAL);
        if (!expected.equals(entry) || entry.step() != STEP || entry.index() != INDEX
                || !entry.key().equals(STRUCTURE) || entry.biomeMask() != BIOME_MASK
                || definition.registryOrdinal() != REGISTRY_ORDINAL
                || !definition.structureId().equals(STRUCTURE)
                || definition.decorationStep() != STEP
                || definition.terrainAdjustment()
                        != Mc263StructureCarrier.TerrainAdjustment.BEARD_THIN) {
            throw new IllegalArgumentException(
                    "Pillager Outpost schedule/membership/ordinal mismatch");
        }
    }

    private static void requireDispatcher(
            Mc263FeatureDispatcher.StructurePlacementContext dispatcher,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        long expectedSeed = Mc263DecorationRandom.featureSeed(
                dispatcher.decorationSeed(), INDEX, STEP);
        if (dispatcher.step() != STEP || dispatcher.globalIndex() != INDEX
                || !dispatcher.structureKey().equals(STRUCTURE)
                || dispatcher.sourceChunkX() != references.chunkX()
                || dispatcher.sourceChunkZ() != references.chunkZ()
                || dispatcher.featureSeed() != expectedSeed) {
            throw new IllegalArgumentException(
                    "Pillager Outpost dispatcher/source/RNG mismatch");
        }
        requireSourceBinding(references, clip);
    }

    private static void requireSourceBinding(
            Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        if (!Mc263CanonicalFeaturesProducerSkeleton.SourceClip.chunk(
                references.chunkX(), references.chunkZ()).equals(clip)) {
            throw new IllegalArgumentException("Pillager Outpost source clip mismatch");
        }
    }

    /**
     * The Pillager Outpost starts this source chunk references, in reference-closure order.
     *
     * <p>{@code ChunkGenerator#applyBiomeDecoration} binds one feature seed per (chunk, structure,
     * decoration step) and then places every element of {@code
     * StructureManager#startsForStructure(SectionPos, Structure)} -- a {@code List} drained from
     * the chunk's whole {@code LongSet} of references -- through one shared {@code WorldgenRandom}.
     * Two outposts whose adjusted boxes both reach one chunk are therefore authentic, not a carrier
     * defect. Only a repeated start is rejected, because {@code ChunkStarts} holds at most one start
     * per (origin chunk, structure) and {@code ReferenceSet} forbids a duplicate origin, so a repeat
     * proves a corrupt closure rather than two outposts.</p>
     */
    private static List<Mc263StructureCarrier.ValidStart> referencedStarts(
            Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ChunkReferences references) {
        List<Mc263StructureCarrier.ValidStart> starts = carrier.resolveStarts(
                references, STRUCTURE);
        if (starts.stream().map(Mc263StructureCarrier.ValidStart::startKey)
                .distinct().count() != starts.size()) {
            throw new IllegalArgumentException(
                    "Pillager Outpost reference closure repeats a start");
        }
        return starts;
    }

    private static List<Long> orderedReferences(
            Mc263StructureCarrier.ChunkReferences references) {
        List<Mc263StructureCarrier.ReferenceSet> sets = references.orderedSets().stream()
                .filter(value -> value.structureId().equals(STRUCTURE)).toList();
        if (sets.size() != 1) {
            throw new IllegalArgumentException(
                    "Pillager Outpost reference-set cardinality mismatch");
        }
        return sets.getFirst().orderedOrigins();
    }

    private static Mc263PillagerOutpostSettlement.Clip clip(
            Mc263StructureCarrier.ValidStart start,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        int chunkX = Math.floorDiv(clip.minX(), Blocks.CHUNK_X);
        int chunkZ = Math.floorDiv(clip.minZ(), Blocks.CHUNK_Z);
        int minChunkX = Math.floorDiv(start.adjustedBoundingBox().minX(), Blocks.CHUNK_X);
        int minChunkZ = Math.floorDiv(start.adjustedBoundingBox().minZ(), Blocks.CHUNK_Z);
        int maxChunkX = Math.floorDiv(start.adjustedBoundingBox().maxX(), Blocks.CHUNK_X);
        int ordinal = Math.addExact(Math.multiplyExact(chunkZ - minChunkZ,
                maxChunkX - minChunkX + 1), chunkX - minChunkX);
        return new Mc263PillagerOutpostSettlement.Clip(
                chunkX, chunkZ, ordinal,
                Mc263PillagerOutpostSettlement.CLIP_MIN_Y,
                Mc263PillagerOutpostSettlement.CLIP_MAX_Y);
    }

    private static Mc263WorldGenRegionRandom random(Mc263WorldGenRegionRandom.State state) {
        return Mc263WorldGenRegionRandom.fromState(state.lo(), state.hi(), state.drawCount(),
                state.gaussianPresent(), state.gaussianBits());
    }

    private static final class Transaction implements
            Mc263PillagerOutpostSettlement.PersistedWorldTransaction {
        private final Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context;
        private final Mc263StructureCarrier predecessor;
        private final String startKey;
        private final Mc263CanonicalFeaturesProducerSkeleton.WorldGenRegionRandomFork randomFork;
        private final Mc263FeaturesRegion region;
        private final boolean isolated;
        private final Map<Mc263PillagerOutpostSettlement.Vec, String> overlay;
        private final Map<Mc263PillagerOutpostSettlement.Vec, MutableBlockEntity> blockEntities;
        private final List<Mc263FeaturesRegion.StructureFluidTick> fluidTicks;

        private Transaction(
                Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context,
                Mc263StructureCarrier predecessor, String startKey,
                Mc263CanonicalFeaturesProducerSkeleton.WorldGenRegionRandomFork randomFork,
                Mc263FeaturesRegion region, boolean isolated,
                Map<Mc263PillagerOutpostSettlement.Vec, String> overlay,
                Map<Mc263PillagerOutpostSettlement.Vec, MutableBlockEntity> blockEntities,
                List<Mc263FeaturesRegion.StructureFluidTick> fluidTicks) {
            this.context = context;
            this.predecessor = predecessor;
            this.startKey = startKey;
            this.randomFork = randomFork;
            this.region = region;
            this.isolated = isolated;
            this.overlay = overlay;
            this.blockEntities = blockEntities;
            this.fluidTicks = fluidTicks;
        }

        static Transaction preflight(Mc263StructureCarrier carrier,
                Mc263WorldGenRegionRandom.State state) {
            return new Transaction(null, carrier, null, null, null, false,
                    Map.of(), Map.of(), List.of());
        }

        static Transaction live(
                Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context,
                Mc263StructureCarrier predecessor, String startKey,
                Mc263CanonicalFeaturesProducerSkeleton.WorldGenRegionRandomFork randomFork) {
            return new Transaction(context, predecessor,
                    Objects.requireNonNull(startKey, "Pillager Outpost transaction start"),
                    randomFork, context.dispatcher().region(), false,
                    new LinkedHashMap<>(), new LinkedHashMap<>(), new ArrayList<>());
        }

        private String requireStartKey() {
            if (startKey == null) {
                throw new IllegalStateException("Pillager Outpost batch outside a live start");
            }
            return startKey;
        }

        @Override public boolean supportsAtomicForkPublishWithRandom() { return true; }
        @Override public boolean supportsHeightmap(String value) {
            return value.equals("WORLD_SURFACE_WG");
        }
        @Override public boolean supportsFluidStateQueries() { return true; }
        @Override public boolean supportsBlockStateQueries() { return true; }
        @Override public boolean supportsSetBlockAndUpdate() { return true; }
        @Override public boolean supportsWriteFlags(int flags) {
            return flags == Mc263PillagerOutpostSettlement.TEMPLATE_WRITE_FLAGS
                    || flags == Mc263PillagerOutpostSettlement.BLOCK_ENTITY_CLEAR_FLAGS;
        }
        @Override public boolean supportsExactState(String state) {
            return Mc263FeatureBlockState.supportsExactState(state);
        }
        @Override public boolean supportsBentPayloads() { return true; }
        @Override public boolean supportsLootPayloads() { return true; }
        @Override public boolean supportsStructureEntityPayloads() { return true; }
        @Override public boolean supportsEmptyUnauthenticatedLanes() { return true; }
        @Override public boolean supportsBlockEntity(String block, String entity) {
            return (block.equals("minecraft:chest") && entity.equals("minecraft:chest"))
                    || (block.equals("minecraft:white_wall_banner")
                            && entity.equals("minecraft:banner"));
        }
        @Override public boolean supportsLootTable(String table) {
            return table.equals("minecraft:chests/pillager_outpost");
        }
        @Override public boolean supportsStructureEntity(String entity) {
            return entity.equals("minecraft:allay") || entity.equals("minecraft:iron_golem");
        }
        @Override public boolean supportsFluidTick(String fluid, int delay) {
            return fluid.equals("minecraft:water")
                    && delay == Mc263PillagerOutpostSettlement.WATER_TICK_DELAY;
        }
        @Override public boolean supportsPersistedAtomicPublishWithWorldGenRegionRandom() {
            return true;
        }

        @Override
        public boolean acceptsPersistedPredecessor(Mc263StructureCarrier carrier,
                Mc263WorldGenRegionRandom.State state) {
            return java.security.MessageDigest.isEqual(
                    predecessor.receiptBytes(), carrier.receiptBytes())
                    && (context == null || context.worldGenRegionRandomState().equals(state));
        }

        @Override
        public Mc263PillagerOutpostSettlement.WorldTransaction fork() {
            if (context == null) {
                throw new PreflightComplete();
            }
            return new Transaction(context, predecessor, startKey, randomFork, region, true,
                    new LinkedHashMap<>(overlay), new LinkedHashMap<>(), new ArrayList<>());
        }

        private void requireIsolated() {
            if (!isolated) throw new AssertionError("live Outpost operation escaped fork");
        }

        @Override public int getHeight(String heightmap, int x, int z) {
            requireIsolated();
            return region.worldSurfaceWg(x, z);
        }
        @Override public Mc263PillagerOutpostSettlement.FluidState getFluidState(
                Mc263PillagerOutpostSettlement.Vec position) {
            requireIsolated();
            Mc263FeatureBlockState state = state(position);
            if (state.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_SOURCE) {
                return new Mc263PillagerOutpostSettlement.FluidState(
                        "minecraft:water", true, 1.0D);
            }
            if (state.fluidKind() == Mc263FeatureBlockState.FluidKind.WATER_FLOWING) {
                return new Mc263PillagerOutpostSettlement.FluidState(
                        "minecraft:water", false, 1.0D);
            }
            return Mc263PillagerOutpostSettlement.FluidState.emptyState();
        }
        @Override public String getBlockState(Mc263PillagerOutpostSettlement.Vec position) {
            requireIsolated();
            return state(position).exactState();
        }
        @Override public boolean setBlock(Mc263PillagerOutpostSettlement.Vec position,
                String exactState, int flags) {
            requireIsolated();
            overlay.put(position, exactState);
            updateBlockEntity(position, exactState);
            return true;
        }
        @Override public boolean setBlockAndUpdate(
                Mc263PillagerOutpostSettlement.Vec position, String exactState) {
            return setBlock(position, exactState, 3);
        }
        @Override public Mc263PillagerOutpostSettlement.BlockEntityAccess getBlockEntity(
                Mc263PillagerOutpostSettlement.Vec position) {
            requireIsolated();
            return blockEntities.get(position);
        }
        @Override public void scheduleFluidTick(Mc263PillagerOutpostSettlement.Vec position,
                String fluid, int delay) {
            requireIsolated();
            fluidTicks.add(new Mc263FeaturesRegion.StructureFluidTick(position.x(), position.y(),
                    position.z(), fluid, delay, 0, fluidTicks.size()));
        }
        @Override public Mc263PillagerOutpostSettlement.EntityPayload spawnStructureEntity(
                Mc263PillagerOutpostSettlement.EntityRequest request,
                Mc263PillagerOutpostSettlement.PlacementRandom random) {
            throw new AssertionError("legacy RNG reached persisted Outpost adapter");
        }
        @Override public Mc263PillagerOutpostSettlement.EntityPayload spawnStructureEntity(
                Mc263PillagerOutpostSettlement.EntityRequest request,
                Mc263WorldGenRegionRandom random) {
            requireIsolated();
            return Mc263PillagerOutpostEntityAuthority.spawnStructureEntity(request, random);
        }
        @Override public Mc263PillagerOutpostSettlement.PublishStatus publishAtomically(
                Mc263PillagerOutpostSettlement.WorldTransaction isolated,
                Mc263PillagerOutpostSettlement.Settlement settlement,
                Mc263PillagerOutpostSettlement.PlacementRandom caller,
                Mc263PillagerOutpostSettlement.PlacementRandom accepted) {
            throw new AssertionError("legacy Outpost publication reached persisted adapter");
        }

        @Override
        public Mc263PillagerOutpostSettlement.PublishStatus publishPersistedAtomically(
                Mc263PillagerOutpostSettlement.WorldTransaction isolatedWorld,
                Mc263PillagerOutpostSettlement.Settlement settlement,
                Mc263StructureCarrier carrierPredecessor,
                Mc263StructureCarrier carrierSuccessor,
                Mc263WorldGenRegionRandom callerRandom,
                Mc263WorldGenRegionRandom.State randomPredecessor,
                Mc263WorldGenRegionRandom acceptedRandom) {
            if (!(isolatedWorld instanceof Transaction staged) || !staged.isolated) {
                throw new IllegalStateException("invalid isolated Outpost transaction");
            }
            if (!acceptsPersistedPredecessor(carrierPredecessor, randomPredecessor)
                    || callerRandom != randomFork.random()) {
                throw new IllegalStateException("stale persisted Outpost STR/WGR predecessor");
            }
            if (!callerRandom.snapshot().equals(randomPredecessor)) {
                throw new IllegalStateException("Outpost caller WGR changed before publication");
            }
            // Install the settlement-owned accepted successor in the dispatch fork; the dispatch
            // transaction publishes STR, all schema lanes and WGR as one atomic unit.
            if (!callerRandom.commitIfExactPredecessor(randomPredecessor, acceptedRandom)) {
                throw new IllegalStateException("Outpost WGR candidate predecessor changed");
            }
            context.commitStructureBatchWithWorldGenRegionRandom(carrierPredecessor,
                    carrierSuccessor, batch(settlement, staged, context), randomFork);
            return Mc263PillagerOutpostSettlement.PublishStatus.COMMITTED;
        }

        private Mc263FeatureBlockState state(Mc263PillagerOutpostSettlement.Vec position) {
            String staged = overlay.get(position);
            return staged == null ? region.blockState(position.x(), position.y(), position.z())
                    : Mc263FeatureBlockState.fromExact(staged);
        }

        private void updateBlockEntity(Mc263PillagerOutpostSettlement.Vec position,
                String exactState) {
            String block = blockKey(exactState);
            if (block.equals("minecraft:chest")) {
                blockEntities.put(position,
                        new MutableBlockEntity(position, block, "minecraft:chest"));
            } else if (block.equals("minecraft:white_wall_banner")) {
                blockEntities.put(position,
                        new MutableBlockEntity(position, block, "minecraft:banner"));
            } else {
                blockEntities.remove(position);
            }
        }
    }

    private static final class PreflightComplete extends RuntimeException { }

    private static Mc263FeaturesRegion.StructureBatch batch(
            Mc263PillagerOutpostSettlement.Settlement settlement, Transaction staged,
            Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput placement) {
        // The owner is the start this transaction was executed from, never the closure's first
        // member: a chunk that references two outposts would otherwise publish the second start's
        // writes under the first start's owner token.
        long owner = Mc263StructureOwner.owner(STRUCTURE, staged.requireStartKey());
        List<Mc263FeaturesRegion.StructureBlockWrite> blocks = settlement.finalStates().stream()
                .map(value -> new Mc263FeaturesRegion.StructureBlockWrite(value.position().x(),
                        value.position().y(), value.position().z(), value.exactState(), owner))
                .toList();
        List<Mc263FeaturesRegion.StructureBentEvidence> bent = settlement.bent().stream()
                .map(value -> new Mc263FeaturesRegion.StructureBentEvidence(value.position().x(),
                        value.position().y(), value.position().z(), value.blockIdentity(),
                        value.entityType(), value.canonicalNbt())).toList();
        List<Mc263FeaturesRegion.StructureLoot> loot = settlement.loot().stream()
                .filter(value -> placement.clip().contains(value.position().x(),
                        value.position().y(), value.position().z()))
                .map(value -> new Mc263FeaturesRegion.StructureLoot(value.position().x(),
                        value.position().y(), value.position().z(), value.table(),
                        value.signedCallerSeed(), placement.productionContext(value.position().x(),
                                value.position().y(), value.position().z(), value.table())))
                .toList();
        int targetX = staged.region.targetChunkX();
        int targetZ = staged.region.targetChunkZ();
        List<Mc263FinalChunkSidecars.StructureEntity> entities = settlement.entities().stream()
                .filter(value -> Math.floorDiv((int) Math.floor(value.position().values()[0]), 16)
                                == targetX
                        && Math.floorDiv((int) Math.floor(value.position().values()[2]), 16)
                                == targetZ)
                .map(Mc263PillagerOutpostCanonicalExecutor::entity).toList();
        List<Mc263FeaturesRegion.StructureFluidTick> fluidTicks = staged.fluidTicks.stream()
                .filter(value -> Math.floorDiv(value.blockX(), 16) == targetX
                        && Math.floorDiv(value.blockZ(), 16) == targetZ).toList();
        return new Mc263FeaturesRegion.StructureBatch(blocks, loot, List.of(), bent, entities,
                fluidTicks, List.of(), List.of(), List.of(), List.of());
    }

    private static Mc263FinalChunkSidecars.StructureEntity entity(
            Mc263PillagerOutpostSettlement.EntityPayload value) {
        double[] position = value.position().values();
        float[] rotation = value.rotation().values();
        double[] motion = value.motion().values();
        return new Mc263FinalChunkSidecars.StructureEntity(value.entityKey(), value.spawnReason(),
                position[0], position[1], position[2], rotation[0], rotation[1],
                motion[0], motion[1], motion[2], value.canonicalPayload());
    }

    private static String blockKey(String state) {
        int property = state.indexOf('[');
        return property < 0 ? state : state.substring(0, property);
    }

    private static final class MutableBlockEntity implements
            Mc263PillagerOutpostSettlement.BlockEntityAccess {
        private final Mc263PillagerOutpostSettlement.Vec position;
        private final String blockIdentity;
        private final String entityType;
        private String semantic = "";
        private String lootTable = "";
        private long lootSeed;

        private MutableBlockEntity(Mc263PillagerOutpostSettlement.Vec position,
                String blockIdentity, String entityType) {
            this.position = position;
            this.blockIdentity = blockIdentity;
            this.entityType = entityType;
        }

        @Override public String blockIdentity() { return blockIdentity; }
        @Override public String entityType() { return entityType; }
        @Override public void loadOminousBanner() {
            semantic = "minecraft:ominous_banner";
            lootTable = "";
            lootSeed = 0L;
        }
        @Override public void setLootTable(String table, long seed) {
            semantic = "minecraft:loot_container";
            lootTable = table;
            lootSeed = seed;
        }
        @Override public Mc263PillagerOutpostSettlement.BlockEntitySnapshot snapshot() {
            byte[] nbt = Mc263StructureBlockEntityNbtAuthority.render(
                    position.x(), position.y(), position.z(), facts());
            return new Mc263PillagerOutpostSettlement.BlockEntitySnapshot(position,
                    blockIdentity, entityType, semantic, lootTable, lootSeed, nbt);
        }

        /**
         * The loaded semantic decides the canonical program: an ominous banner is the shared
         * {@code BannerBlockEntity} save, a deferred-loot chest the shared
         * {@code trySaveLootTable} container save. Nothing else can reach a saved snapshot.
         */
        private Mc263StructureBlockEntityNbtAuthority.Facts facts() {
            return switch (semantic) {
                case "minecraft:ominous_banner" ->
                        new Mc263StructureBlockEntityNbtAuthority.OminousBanner(entityType);
                case "minecraft:loot_container" ->
                        new Mc263StructureBlockEntityNbtAuthority.LootContainer(
                                entityType, lootTable, lootSeed);
                default -> throw new IllegalStateException(
                        "Pillager Outpost block entity was never loaded at " + position);
            };
        }
        @Override public void setChanged() { }
    }
}
