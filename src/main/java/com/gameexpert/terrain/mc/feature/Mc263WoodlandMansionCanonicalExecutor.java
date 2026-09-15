package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionProductionAdapter;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionProductionTransaction;
import com.gameexpert.terrain.mc.structure.Mc263WoodlandMansionSettlement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Canonical persisted-start host for the accepted Woodland Mansion transaction. */
final class Mc263WoodlandMansionCanonicalExecutor implements
        Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutor {
    private static final String STRUCTURE = Mc263WoodlandMansionSettlement.STRUCTURE_KEY;
    private static final int STEP = 4;
    private static final int INDEX = 23;
    /** Decoration setup consumed two wrapper nextLong calls before setFeatureSeed retained count. */
    private static final int PLACEMENT_RANDOM_PREDECESSOR_COUNT = 4;

    static void register(
            Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry registry) {
        Objects.requireNonNull(registry, "canonical structure executor registry")
                .register(STRUCTURE, new Mc263WoodlandMansionCanonicalExecutor());
    }

    @Override
    public void preflight(
            Mc263CanonicalFeaturesProducerSkeleton.StructurePreflightContext context) {
        Objects.requireNonNull(context, "Woodland Mansion preflight context");
        requireSchedule(context.entry(), context.carrier().registry());
        requireSource(context.references(), context.clip());
        Mc263WoodlandMansionProductionAdapter adapter =
                new Mc263WoodlandMansionProductionAdapter();
        for (Mc263StructureCarrier.ValidStart start : referencedStarts(
                context.carrier(), context.references())) {
            adapter.preflightPersisted(context.carrier(), start,
                    request(context.worldSeed(), start, context.references()));
        }
    }

    @Override
    public void place(Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context) {
        Objects.requireNonNull(context, "Woodland Mansion placement context");
        requireDispatcher(context.dispatcher(), context.references(), context.clip());
        requireSchedule(Mc263StructureIndexReceipt.step(STEP).get(INDEX),
                context.carrier().registry());
        List<String> startKeys = referencedStarts(context.carrier(), context.references()).stream()
                .map(Mc263StructureCarrier.ValidStart::startKey).toList();
        if (startKeys.isEmpty()) return;

        // One WorldgenRandom is bound per (chunk, structure, decoration step) and every start of
        // that structure is placed through it in closure order, so the placement stream is seeded
        // once and every later start continues that exact state instead of reseeding.
        Mc263WoodlandMansionSettlement.PlacementRandom placementRandom =
                Mc263WoodlandMansionSettlement.PlacementRandom.fromSeed(
                        context.dispatcher().featureSeed(),
                        PLACEMENT_RANDOM_PREDECESSOR_COUNT);
        for (String startKey : startKeys) {
            Mc263StructureCarrier predecessor = context.carrier();
            Mc263StructureCarrier.ValidStart start = referencedStarts(
                    predecessor, context.references()).stream()
                    .filter(value -> value.startKey().equals(startKey)).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "evolved Woodland Mansion carrier lost referenced start"));
            Mc263CanonicalFeaturesProducerSkeleton.WorldGenRegionRandomFork randomFork =
                    context.forkWorldGenRegionRandom();
            Mc263WorldGenRegionRandom.State worldRandomPredecessor = randomFork.predecessor();
            Mc263WoodlandMansionSettlement.ServerRandomState serverState =
                    new Mc263WoodlandMansionSettlement.ServerRandomState(
                            worldRandomPredecessor.lo(), worldRandomPredecessor.hi(),
                            worldRandomPredecessor.gaussianPresent(),
                            worldRandomPredecessor.gaussianBits());
            Mc263WoodlandMansionSettlement.ServerLevelRandom serverRandom =
                    Mc263WoodlandMansionSettlement.ServerLevelRandom.fromAuthenticatedState(
                            serverState, randomFork.random().continuationNextLongI64());

            MansionWorld world = new MansionWorld(context.dispatcher().region());
            Mc263WoodlandMansionProductionTransaction transaction =
                    Mc263WoodlandMansionProductionTransaction.root(world,
                            new Mc263WoodlandMansionProductionTransaction.Journal());
            Mc263WoodlandMansionProductionAdapter.Execution execution =
                    new Mc263WoodlandMansionProductionAdapter().executePersisted(
                            predecessor, start,
                            request(context.dispatcher().worldSeed(), start, context.references()),
                            transaction, placementRandom, serverRandom);

            Mc263WoodlandMansionSettlement.ServerRandomContinuation continuation =
                    execution.result().serverRandom();
            int acceptedDrawCount;
            try {
                acceptedDrawCount = Math.addExact(worldRandomPredecessor.drawCount(),
                        continuation.drawCount());
            } catch (ArithmeticException error) {
                throw new IllegalStateException(
                        "Mansion WorldGenRegion RNG draw count overflow", error);
            }
            Mc263WorldGenRegionRandom accepted = Mc263WorldGenRegionRandom.fromState(
                    continuation.lo(), continuation.hi(), acceptedDrawCount,
                    continuation.gaussianCached(), continuation.gaussianValueRawBits());
            if (!accepted.continuationNextLongI64().equals(
                    continuation.continuationNextLongI64())) {
                throw new IllegalStateException("Mansion WGR continuation projection drift");
            }
            if (!randomFork.random().commitIfExactPredecessor(
                    worldRandomPredecessor, accepted)) {
                throw new IllegalStateException("Mansion WGR candidate predecessor changed");
            }
            context.commitStructureBatchWithWorldGenRegionRandom(predecessor,
                    execution.successorCarrier(), batch(execution.result(),
                            context.dispatcher().region(), context), randomFork);
            placementRandom = Mc263WoodlandMansionSettlement.PlacementRandom.fromState(
                    execution.result().random().lo(), execution.result().random().hi(),
                    execution.result().random().count());
        }
    }

    private static Mc263WoodlandMansionSettlement.Request request(long worldSeed,
            Mc263StructureCarrier.ValidStart start,
            Mc263StructureCarrier.ChunkReferences references) {
        return new Mc263WoodlandMansionSettlement.Request(STRUCTURE, worldSeed,
                start.originChunkX(), start.originChunkZ(),
                references.chunkX(), references.chunkZ());
    }

    /**
     * The Woodland Mansion starts this source chunk references, in reference-closure order.
     *
     * <p>{@code ChunkGenerator#applyBiomeDecoration} binds one feature seed per (chunk, structure,
     * decoration step) and then places every element of {@code
     * StructureManager#startsForStructure(SectionPos, Structure)} -- a {@code List} drained from
     * the chunk's whole {@code LongSet} of references -- through one shared {@code WorldgenRandom}.
     * Two mansions whose adjusted boxes both reach one chunk are therefore authentic, not a carrier
     * defect. Only a repeated start is rejected, because {@code ChunkStarts} holds at most one start
     * per (origin chunk, structure) and {@code ReferenceSet} forbids a duplicate origin, so a repeat
     * proves a corrupt closure rather than two mansions.</p>
     */
    private static List<Mc263StructureCarrier.ValidStart> referencedStarts(
            Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ChunkReferences references) {
        List<Mc263StructureCarrier.ValidStart> starts = carrier.resolveStarts(
                references, STRUCTURE);
        if (starts.stream().map(Mc263StructureCarrier.ValidStart::startKey)
                .distinct().count() != starts.size()) {
            throw new IllegalArgumentException(
                    "Woodland Mansion reference closure repeats a start");
        }
        return starts;
    }

    private static void requireSchedule(Mc263StructureIndexReceipt.Entry entry,
            Mc263StructureCarrier.Registry registry) {
        Mc263StructureIndexReceipt.Entry expected = Mc263StructureIndexReceipt.step(STEP).get(INDEX);
        Mc263StructureCarrier.StructureDefinition definition = registry.require(STRUCTURE);
        int ordinal = Mc263StructureIndexReceipt.entries().indexOf(expected);
        if (!expected.equals(entry) || !STRUCTURE.equals(entry.key())
                || definition.registryOrdinal() != ordinal
                || definition.decorationStep() != STEP
                || definition.terrainAdjustment()
                        != Mc263StructureCarrier.TerrainAdjustment.NONE) {
            throw new IllegalArgumentException("Woodland Mansion schedule/carrier mismatch");
        }
    }

    private static void requireDispatcher(
            Mc263FeatureDispatcher.StructurePlacementContext dispatcher,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        long expectedSeed = Mc263DecorationRandom.featureSeed(
                dispatcher.decorationSeed(), INDEX, STEP);
        if (dispatcher.step() != STEP || dispatcher.globalIndex() != INDEX
                || !STRUCTURE.equals(dispatcher.structureKey())
                || dispatcher.sourceChunkX() != references.chunkX()
                || dispatcher.sourceChunkZ() != references.chunkZ()
                || dispatcher.featureSeed() != expectedSeed) {
            throw new IllegalArgumentException("Woodland Mansion dispatcher/source/RNG mismatch");
        }
        requireSource(references, clip);
    }

    private static void requireSource(Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        if (!Mc263CanonicalFeaturesProducerSkeleton.SourceClip.chunk(
                references.chunkX(), references.chunkZ()).equals(clip)) {
            throw new IllegalArgumentException("Woodland Mansion source clip mismatch");
        }
    }

    private static Mc263FeaturesRegion.StructureBatch batch(
            Mc263WoodlandMansionSettlement.Settlement settlement,
            Mc263FeaturesRegion region,
            Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput placement) {
        List<Mc263FeaturesRegion.StructureBlockWrite> blocks = settlement.finalStates().stream()
                .map(value -> new Mc263FeaturesRegion.StructureBlockWrite(
                        value.position().x(), value.position().y(), value.position().z(),
                        value.exactState(), settlement.ownerId())).toList();
        List<Mc263FeaturesRegion.StructureLoot> loot = settlement.loot().stream()
                .filter(value -> placement.clip().contains(value.position().x(),
                        value.position().y(), value.position().z()))
                .map(value -> new Mc263FeaturesRegion.StructureLoot(
                        value.position().x(), value.position().y(), value.position().z(),
                        value.table(), value.seed(), placement.productionContext(
                                value.position().x(), value.position().y(), value.position().z(),
                                value.table()))).toList();
        List<Mc263FeaturesRegion.StructureBentEvidence> bent = settlement.bent().stream()
                .map(value -> new Mc263FeaturesRegion.StructureBentEvidence(
                        value.position().x(), value.position().y(), value.position().z(),
                        blockKey(value.exactState()), value.blockEntityType(),
                        value.canonicalNbt().binary())).toList();
        int targetX = region.targetChunkX();
        int targetZ = region.targetChunkZ();
        List<Mc263FinalChunkSidecars.StructureEntity> entities = settlement.entities().stream()
                .filter(value -> Math.floorDiv(value.markerPosition().x(), 16) == targetX
                        && Math.floorDiv(value.markerPosition().z(), 16) == targetZ)
                .map(Mc263WoodlandMansionCanonicalExecutor::entity).toList();
        // LevelChunkTicks numbers a scheduled tick by its index in the surviving (deduplicated)
        // queue, so the batch carries the effective encounter index, not the raw staging counter
        // that also counted duplicate-unchanged schedules. Both tick lanes are therefore renumbered
        // 0..n-1 batch-wide in encounter order at the publication boundary, exactly as the accepted
        // Village lane does in Mc263VillageSettlement#publicationTicks.
        ArrayList<Mc263FeaturesRegion.StructureFluidTick> fluidTicks = new ArrayList<>();
        for (Mc263WoodlandMansionSettlement.FluidTick value : settlement.fluidTicks()) {
            if (!value.inserted()) continue;
            fluidTicks.add(new Mc263FeaturesRegion.StructureFluidTick(
                    value.position().x(), value.position().y(), value.position().z(),
                    value.fluidKey(), value.delay(), value.priority(), fluidTicks.size()));
        }
        ArrayList<Mc263FeaturesRegion.StructureBlockTick> blockTicks = new ArrayList<>();
        for (Mc263WoodlandMansionSettlement.BlockTick value
                : Mc263WoodlandMansionSettlement.settleBlockTicksForPublication(
                        settlement.blockTicks())) {
            blockTicks.add(new Mc263FeaturesRegion.StructureBlockTick(
                    value.position().x(), value.position().y(), value.position().z(),
                    value.blockKey(), value.delay(), value.priority(), blockTicks.size()));
        }
        return new Mc263FeaturesRegion.StructureBatch(blocks, loot, List.of(), bent, entities,
                List.copyOf(fluidTicks), List.of(), List.of(), List.copyOf(blockTicks), List.of());
    }

    private static Mc263FinalChunkSidecars.StructureEntity entity(
            Mc263WoodlandMansionSettlement.EntityPayload value) {
        Mc263WoodlandMansionSettlement.Position position = value.markerPosition();
        return new Mc263FinalChunkSidecars.StructureEntity(value.entityKey(), value.spawnReason(),
                position.x() + 0.5D, position.y(), position.z() + 0.5D,
                0.0F, 0.0F, 0.0D, 0.0D, 0.0D, value.canonicalNbt().binary());
    }

    private static String blockKey(String exactState) {
        int properties = exactState.indexOf('[');
        return properties < 0 ? exactState : exactState.substring(0, properties);
    }

    /** Region-backed read view with transaction-local writes only. */
    private static final class MansionWorld
            implements Mc263WoodlandMansionProductionTransaction.World {
        private final Mc263FeaturesRegion region;
        private final Map<Mc263TemplatePlacementExecutor.Vec, String> states;

        private MansionWorld(Mc263FeaturesRegion region) {
            this(region, new LinkedHashMap<>());
        }

        private MansionWorld(Mc263FeaturesRegion region,
                Map<Mc263TemplatePlacementExecutor.Vec, String> states) {
            this.region = Objects.requireNonNull(region, "Mansion FEATURES region");
            this.states = new LinkedHashMap<>(states);
        }

        @Override public Mc263WoodlandMansionProductionTransaction.World forkIsolated() {
            return new MansionWorld(region, states);
        }

        @Override public void commitFrom(
                Mc263WoodlandMansionProductionTransaction.World isolated) {
            states.clear();
            states.putAll(((MansionWorld) isolated).states);
        }

        @Override public int getHeight(String heightmap, int x, int z) {
            if (!"WORLD_SURFACE_WG".equals(heightmap)) {
                throw new IllegalArgumentException("unknown Mansion heightmap: " + heightmap);
            }
            return region.worldSurfaceWg(x, z);
        }

        @Override public String getBlockState(Mc263TemplatePlacementExecutor.Vec position) {
            String staged = states.get(position);
            return staged != null ? staged
                    : region.blockState(position.x(), position.y(), position.z()).exactState();
        }

        @Override public String getFluidKey(Mc263TemplatePlacementExecutor.Vec position) {
            Mc263FeatureBlockState state = Mc263FeatureBlockState.fromExact(
                    getBlockState(position));
            return state.fluidKind() == Mc263FeatureBlockState.FluidKind.NONE
                    ? Mc263TemplatePlacementExecutor.EMPTY_FLUID : "minecraft:water";
        }

        @Override public boolean isFluidSource(Mc263TemplatePlacementExecutor.Vec position) {
            return Mc263FeatureBlockState.fromExact(getBlockState(position)).fluidKind()
                    == Mc263FeatureBlockState.FluidKind.WATER_SOURCE;
        }

        @Override public double fluidHeight(Mc263TemplatePlacementExecutor.Vec position) {
            return Mc263FeatureBlockState.fromExact(getBlockState(position)).fluidKind()
                    == Mc263FeatureBlockState.FluidKind.NONE ? 0.0D : 1.0D;
        }

        @Override public boolean setBlock(Mc263TemplatePlacementExecutor.Vec position,
                String exactState, int flags) {
            states.put(position, exactState);
            return true;
        }

        @Override public Mc263TemplatePlacementExecutor.BlockEntityHandle getBlockEntity(
                Mc263TemplatePlacementExecutor.Vec position) {
            String entityType = switch (blockKey(getBlockState(position))) {
                case "minecraft:chest" -> "minecraft:chest";
                case "minecraft:trapped_chest" -> "minecraft:trapped_chest";
                case "minecraft:spawner" -> "minecraft:mob_spawner";
                default -> blockKey(getBlockState(position)).endsWith("_banner")
                        ? "minecraft:banner" : null;
            };
            if (entityType == null) return null;
            return new Mc263TemplatePlacementExecutor.BlockEntityHandle() {
                @Override public String blockEntityType() { return entityType; }
                @Override public void loadCanonicalNbt(byte[] canonicalNbt) { }
                @Override public void setChanged() { }
            };
        }

        @Override public void scheduleFluidTick(Mc263TemplatePlacementExecutor.Vec position,
                String fluidKey, int delay) { }
    }
}
