package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.structure.Mc263BuriedTreasureCarrierBridge;
import com.gameexpert.terrain.mc.structure.Mc263BuriedTreasurePieceExecutor;
import com.gameexpert.terrain.mc.structure.Mc263BuriedTreasureStartGenerator;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import java.util.List;
import java.util.Objects;

/** Complete dormant canonical adapter for the exact buried-treasure start and piece closure. */
final class Mc263BuriedTreasureCanonicalExecutor implements
        Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutor {
    private static final int STEP = 3;
    private static final int INDEX = 0;
    private static final long BIOME_MASK = 0x00000001008000L;
    private static final List<String> REQUIRED_STATES = List.of(
            "minecraft:sand",
            "minecraft:chest[facing=north,type=single,waterlogged=false]",
            "minecraft:chest[facing=east,type=single,waterlogged=false]",
            "minecraft:chest[facing=south,type=single,waterlogged=false]",
            "minecraft:chest[facing=west,type=single,waterlogged=false]");

    static void register(
            Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry registry) {
        Objects.requireNonNull(registry, "canonical structure executor registry")
                .register(Mc263BuriedTreasureStartGenerator.STRUCTURE,
                        new Mc263BuriedTreasureCanonicalExecutor());
    }

    @Override
    public void preflight(
            Mc263CanonicalFeaturesProducerSkeleton.StructurePreflightContext context) {
        Objects.requireNonNull(context, "buried-treasure preflight context");
        requireSchedule(context.entry());
        requireSourceBinding(context.references(), context.clip());
        requireRegistry(context.carrier().registry());
        for (String state : REQUIRED_STATES) {
            if (!Mc263FeatureBlockState.supportsExactState(state)) {
                throw new UnsupportedOperationException("buried-treasure state: " + state);
            }
        }
        validatePersistedStarts(context.carrier());
        for (Mc263StructureCarrier.ValidStart start : context.carrier().resolveStarts(
                context.references(), Mc263BuriedTreasureStartGenerator.STRUCTURE)) {
            Mc263BuriedTreasureStartGenerator.validatePersisted(start);
        }
    }

    @Override
    public void place(Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context) {
        Objects.requireNonNull(context, "buried-treasure placement context");
        Mc263FeatureDispatcher.StructurePlacementContext dispatcher = context.dispatcher();
        if (dispatcher.step() != STEP || dispatcher.globalIndex() != INDEX
                || !dispatcher.structureKey().equals(Mc263BuriedTreasureStartGenerator.STRUCTURE)
                || dispatcher.sourceChunkX() != context.references().chunkX()
                || dispatcher.sourceChunkZ() != context.references().chunkZ()) {
            throw new IllegalArgumentException("buried-treasure dispatcher/source mismatch");
        }
        requireSourceBinding(context.references(), context.clip());
        List<Mc263StructureCarrier.ValidStart> starts = context.carrier().resolveStarts(
                context.references(), Mc263BuriedTreasureStartGenerator.STRUCTURE);
        for (Mc263StructureCarrier.ValidStart start : starts) {
            Mc263BuriedTreasureStartGenerator.validatePersisted(start);
        }
        var clip = new Mc263BuriedTreasurePieceExecutor.BoundingBox(
                context.clip().minX(), context.clip().minY(), context.clip().minZ(),
                context.clip().maxX(), context.clip().maxY(), context.clip().maxZ());
        for (Mc263StructureCarrier.ValidStart start : starts) {
            var selectedReferences = new Mc263StructureCarrier.ChunkReferences(
                    context.references().chunkX(), context.references().chunkZ(),
                    List.of(new Mc263StructureCarrier.ReferenceSet(
                            Mc263BuriedTreasureStartGenerator.STRUCTURE,
                            List.of(Mc263StructureCarrier.packChunk(
                                    start.originChunkX(), start.originChunkZ())))));
            RegionWorld world = new RegionWorld(dispatcher.region(), start.startKey(), context);
            Mc263BuriedTreasureCarrierBridge.execute(context.carrier(), selectedReferences,
                    clip, world, dispatcher.random());
        }
    }

    private static void requireSchedule(Mc263StructureIndexReceipt.Entry entry) {
        if (entry.step() != STEP || entry.index() != INDEX
                || !entry.key().equals(Mc263BuriedTreasureStartGenerator.STRUCTURE)
                || entry.biomeMask() != BIOME_MASK) {
            throw new IllegalArgumentException("buried-treasure schedule mismatch");
        }
    }

    private static void requireRegistry(Mc263StructureCarrier.Registry registry) {
        Mc263StructureCarrier.StructureDefinition definition = registry.require(
                Mc263BuriedTreasureStartGenerator.STRUCTURE);
        if (definition.registryOrdinal() != 0 || definition.decorationStep() != STEP
                || definition.terrainAdjustment() != Mc263StructureCarrier.TerrainAdjustment.NONE) {
            throw new IllegalArgumentException("buried-treasure carrier registry mismatch");
        }
    }

    private static void requireSourceBinding(Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        Mc263CanonicalFeaturesProducerSkeleton.SourceClip expected =
                Mc263CanonicalFeaturesProducerSkeleton.SourceClip.chunk(
                        references.chunkX(), references.chunkZ());
        if (!expected.equals(clip)) {
            throw new IllegalArgumentException("buried-treasure source clip mismatch");
        }
    }

    private static void validatePersistedStarts(Mc263StructureCarrier carrier) {
        for (Mc263StructureCarrier.ChunkStarts chunk : carrier.startChunks()) {
            for (Mc263StructureCarrier.StartEntry entry : chunk.orderedStarts()) {
                if (!entry.structureId().equals(Mc263BuriedTreasureStartGenerator.STRUCTURE)
                        || !(entry.body() instanceof Mc263StructureCarrier.ValidStart start)) {
                    continue;
                }
                Mc263BuriedTreasureStartGenerator.validatePersisted(start);
            }
        }
    }

    private static final class RegionWorld implements Mc263BuriedTreasurePieceExecutor.WorldAccess {
        private final Mc263FeaturesRegion region;
        private final long owner;
        private final Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput placement;

        private RegionWorld(Mc263FeaturesRegion region, String startKey,
                Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput placement) {
            this.region = region;
            this.owner = Mc263StructureOwner.owner(
                    Mc263BuriedTreasureStartGenerator.STRUCTURE, startKey);
            this.placement = Objects.requireNonNull(placement,
                    "buried-treasure placement context");
        }

        @Override public boolean supportsExactState(String state) {
            return Mc263FeatureBlockState.supportsExactState(state);
        }
        @Override public boolean supportsCopiedStateWrites() { return true; }
        @Override public boolean supportsChestLoot() { return true; }
        @Override public int minY() { return region.minGenerationY(); }
        @Override public int oceanFloorWg(int x, int z) { return region.oceanFloorWg(x, z); }
        @Override public String blockState(int x, int y, int z) {
            return region.blockState(x, y, z).exactState();
        }
        @Override public boolean isSolidRender(int x, int y, int z) {
            return region.blockState(x, y, z).isSolidRender();
        }
        @Override public boolean setBlockAndUpdate(int x, int y, int z, String state) {
            return region.trySetOwnedBlockState(x, y, z, state, owner);
        }
        @Override public boolean setBlock(int x, int y, int z, String state, int flags) {
            if (flags != 2) {
                throw new IllegalArgumentException("unsupported buried-treasure flags: " + flags);
            }
            return region.trySetOwnedBlockState(x, y, z, state, owner);
        }
        @Override public boolean hasChestBlockEntity(int x, int y, int z) {
            return region.hasRandomizableContainer(x, y, z);
        }
        @Override public void setChestLoot(int x, int y, int z, String table, long seed) {
            if (!placement.clip().contains(x, y, z)) {
                return;
            }
            if (!region.setChestLoot(x, y, z, table, seed,
                    placement.productionContext(x, y, z, table))) {
                throw new IllegalStateException(
                        "buried-treasure chest capability disappeared before loot write");
            }
        }
    }
}
