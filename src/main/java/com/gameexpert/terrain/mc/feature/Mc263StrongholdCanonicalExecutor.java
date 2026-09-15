package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.structure.Mc263StrongholdFeatureWorldAdapter;
import com.gameexpert.terrain.mc.structure.Mc263StrongholdFillerCorridorPieceExecutor;
import com.gameexpert.terrain.mc.structure.Mc263StrongholdLeftTurnPieceExecutor;
import com.gameexpert.terrain.mc.structure.Mc263StrongholdOrderedAggregate;
import com.gameexpert.terrain.mc.structure.Mc263StrongholdRightTurnPieceExecutor;
import com.gameexpert.terrain.mc.structure.Mc263StrongholdSettlement;
import com.gameexpert.terrain.mc.structure.Mc263StrongholdStraightPieceExecutor;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import java.util.List;
import java.util.Objects;

/** Atomic production executor for the pinned ordered stronghold piece aggregate. */
public final class Mc263StrongholdCanonicalExecutor implements
        Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutor {
    private static final String STRUCTURE = Mc263StrongholdOrderedAggregate.STRUCTURE;
    private final Mc263StructureIndexReceipt.Entry pinned;

    private Mc263StrongholdCanonicalExecutor() {
        pinned = Mc263StructureIndexReceipt.entries().stream()
                .filter(entry -> entry.key().equals(STRUCTURE))
                .findFirst().orElseThrow();
    }

    static void register(
            Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry registry) {
        registry.register(STRUCTURE, new Mc263StrongholdCanonicalExecutor());
    }

    @Override public void preflight(
            Mc263CanonicalFeaturesProducerSkeleton.StructurePreflightContext context) {
        Objects.requireNonNull(context, "stronghold preflight context");
        requireSchedule(context.entry());
        requireBinding(context.carrier(), context.references(), context.clip());
        Mc263StrongholdOrderedAggregate.preflight(context.carrier(), context.references(),
                new CapabilityWorld().worlds());
        requireProductionAtomicBatchClosure();
    }

    @Override public void place(
            Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context) {
        Objects.requireNonNull(context, "stronghold placement context");
        Mc263FeatureDispatcher.StructurePlacementContext dispatcher = context.dispatcher();
        if (dispatcher.step() != pinned.step() || dispatcher.globalIndex() != pinned.index()
                || !dispatcher.structureKey().equals(STRUCTURE)
                || dispatcher.sourceChunkX() != context.references().chunkX()
                || dispatcher.sourceChunkZ() != context.references().chunkZ()) {
            throw new IllegalArgumentException("stronghold dispatcher/source mismatch");
        }
        Mc263StructureCarrier predecessor = context.carrier();
        requireBinding(predecessor, context.references(), context.clip());
        Mc263StrongholdFeatureWorldAdapter world =
                Mc263StrongholdFeatureWorldAdapter.buffered(dispatcher.region());
        Mc263StrongholdOrderedAggregate.StructureRandom random =
                new Mc263StrongholdOrderedAggregate.StructureRandom(dispatcher.featureSeed());
        List<Mc263StrongholdOrderedAggregate.StartEvidence> evidence =
                Mc263StrongholdOrderedAggregate.execute(predecessor, context.references(),
                        new Mc263StrongholdOrderedAggregate.BoundingBox(context.clip().minX(),
                                context.clip().minY(), context.clip().minZ(),
                                context.clip().maxX(), context.clip().maxY(),
                                context.clip().maxZ()), world.worlds(), random);
        Mc263StructureCarrier successor = Mc263StrongholdSettlement.settle(predecessor,
                context.references(), evidence).structureCarrier();
        Mc263FeaturesRegion.StructureBatch batch = new Mc263FeaturesRegion.StructureBatch(
                ownedBlocks(world.stagedBlocks(), evidence),
                world.stagedLoot().stream()
                        .filter(value -> context.clip().contains(value.blockX(), value.blockY(),
                                value.blockZ()))
                        .map(value -> new Mc263FeaturesRegion.StructureLoot(
                        value.blockX(), value.blockY(), value.blockZ(), value.lootTable(),
                        value.lootSeed(), context.productionContext(value.blockX(), value.blockY(),
                                value.blockZ(), value.lootTable()))).toList(),
                List.of(), List.of(), List.of(),
                fluidTicks(world.stagedFluidTicks()),
                world.stagedPostprocessMarks().stream().map(value ->
                        new Mc263FeaturesRegion.StructurePostprocessMark(value.blockX(),
                                value.blockY(), value.blockZ())).toList(),
                world.stagedSpawners().stream().map(value ->
                        new Mc263FeaturesRegion.StructureSpawner(value.blockX(), value.blockY(),
                                value.blockZ(), value.entityType())).toList());
        context.commitStructureBatch(predecessor, successor, batch);
    }

    private static List<Mc263FeaturesRegion.StructureFluidTick> fluidTicks(
            List<Mc263StrongholdFeatureWorldAdapter.FluidTick> values) {
        java.util.ArrayList<Mc263FeaturesRegion.StructureFluidTick> ticks =
                new java.util.ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            Mc263StrongholdFeatureWorldAdapter.FluidTick value = values.get(index);
            ticks.add(new Mc263FeaturesRegion.StructureFluidTick(value.blockX(), value.blockY(),
                    value.blockZ(), value.fluidKey(), value.delay(), value.priority(), index));
        }
        return List.copyOf(ticks);
    }

    private static List<Mc263FeaturesRegion.StructureBlockWrite> ownedBlocks(
            List<Mc263StrongholdFeatureWorldAdapter.BlockWrite> blocks,
            List<Mc263StrongholdOrderedAggregate.StartEvidence> evidence) {
        java.util.ArrayList<Mc263FeaturesRegion.StructureBlockWrite> owned =
                new java.util.ArrayList<>(blocks.size());
        int index = 0;
        for (Mc263StrongholdOrderedAggregate.StartEvidence start : evidence) {
            long owner = Mc263StructureOwner.owner(STRUCTURE, start.startKey());
            int startWrites = start.orderedPieces().stream()
                    .mapToInt(piece -> writes(piece.execution())).sum();
            for (int end = Math.addExact(index, startWrites); index < end; index++) {
                Mc263StrongholdFeatureWorldAdapter.BlockWrite value = blocks.get(index);
                owned.add(new Mc263FeaturesRegion.StructureBlockWrite(value.blockX(),
                        value.blockY(), value.blockZ(), value.exactState(), owner));
            }
        }
        if (index != blocks.size()) {
            throw new IllegalStateException("stronghold write/evidence count mismatch: evidence="
                    + index + " staged=" + blocks.size());
        }
        return List.copyOf(owned);
    }

    private static int writes(Object execution) {
        if (execution instanceof Mc263StrongholdStraightPieceExecutor.ExecutionResult value) {
            return value.writes();
        }
        if (execution instanceof Mc263StrongholdLeftTurnPieceExecutor.ExecutionResult value) {
            return value.writes();
        }
        if (execution instanceof Mc263StrongholdRightTurnPieceExecutor.ExecutionResult value) {
            return value.writes();
        }
        if (execution instanceof Mc263StrongholdFillerCorridorPieceExecutor.ExecutionResult value) {
            return value.writes();
        }
        throw new IllegalArgumentException("unknown stronghold execution evidence");
    }

    static List<String> productionAtomicBatchBlockers() { return List.of(); }
    static void requireProductionAtomicBatchClosure() { }

    private void requireSchedule(Mc263StructureIndexReceipt.Entry entry) {
        if (!pinned.equals(entry) || !STRUCTURE.equals(entry.key())) {
            throw new IllegalArgumentException("stronghold schedule mismatch");
        }
    }

    private void requireBinding(Mc263StructureCarrier carrier,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        Mc263CanonicalFeaturesProducerSkeleton.SourceClip expected =
                Mc263CanonicalFeaturesProducerSkeleton.SourceClip.chunk(
                        references.chunkX(), references.chunkZ());
        if (!expected.equals(clip)) {
            throw new IllegalArgumentException("stronghold source clip mismatch");
        }
        Mc263StructureCarrier.StructureDefinition definition = carrier.registry()
                .require(STRUCTURE);
        if (definition.registryOrdinal()
                    != Mc263StructureIndexReceipt.entries().indexOf(pinned)
                || definition.decorationStep() != pinned.step()
                || definition.terrainAdjustment()
                    != Mc263StructureCarrier.TerrainAdjustment.BURY) {
            throw new IllegalArgumentException("stronghold carrier registry mismatch");
        }
    }

    /** Capability-only projection: any observation or mutation is a preflight defect. */
    private static final class CapabilityWorld implements
            Mc263StrongholdStraightPieceExecutor.WorldAccess,
            Mc263StrongholdLeftTurnPieceExecutor.WorldAccess,
            Mc263StrongholdRightTurnPieceExecutor.WorldAccess,
            Mc263StrongholdFillerCorridorPieceExecutor.WorldAccess {
        Mc263StrongholdOrderedAggregate.Worlds worlds() {
            return new Mc263StrongholdOrderedAggregate.Worlds(this, this, this, this);
        }
        @Override public boolean supportsExactState(String state) {
            return Mc263FeatureBlockState.supportsExactState(state);
        }
        @Override public boolean supportsLiveAirQueries() { return true; }
        @Override public boolean supportsPostWriteFluidStateQueries() { return true; }
        @Override public boolean supportsScheduledFluidTicks() { return true; }
        @Override public boolean supportsPostprocessingMarks() { return true; }
        @Override public boolean supportsLootChests() { return true; }
        @Override public boolean supportsSpawnerBlockEntities() { return true; }
        @Override public boolean isAir(Mc263StrongholdStraightPieceExecutor.BlockPos p) {
            throw observation();
        }
        @Override public boolean isAir(Mc263StrongholdLeftTurnPieceExecutor.BlockPos p) {
            throw observation();
        }
        @Override public boolean isAir(Mc263StrongholdRightTurnPieceExecutor.BlockPos p) {
            throw observation();
        }
        @Override public boolean setBlock(Mc263StrongholdStraightPieceExecutor.BlockPos p,
                String state, int flags) { throw mutation(); }
        @Override public boolean setBlock(Mc263StrongholdLeftTurnPieceExecutor.BlockPos p,
                String state, int flags) { throw mutation(); }
        @Override public boolean setBlock(Mc263StrongholdRightTurnPieceExecutor.BlockPos p,
                String state, int flags) { throw mutation(); }
        @Override public boolean setBlock(Mc263StrongholdFillerCorridorPieceExecutor.BlockPos p,
                String state, int flags) { throw mutation(); }
        @Override public String postWriteFluidType(
                Mc263StrongholdStraightPieceExecutor.BlockPos p) { throw observation(); }
        @Override public String postWriteFluidType(
                Mc263StrongholdLeftTurnPieceExecutor.BlockPos p) { throw observation(); }
        @Override public String postWriteFluidType(
                Mc263StrongholdRightTurnPieceExecutor.BlockPos p) { throw observation(); }
        @Override public String postWriteFluidType(
                Mc263StrongholdFillerCorridorPieceExecutor.BlockPos p) { throw observation(); }
        @Override public void scheduleFluidTick(
                Mc263StrongholdStraightPieceExecutor.BlockPos p, String fluid, int delay) {
            throw mutation();
        }
        @Override public void scheduleFluidTick(
                Mc263StrongholdLeftTurnPieceExecutor.BlockPos p, String fluid, int delay) {
            throw mutation();
        }
        @Override public void scheduleFluidTick(
                Mc263StrongholdRightTurnPieceExecutor.BlockPos p, String fluid, int delay) {
            throw mutation();
        }
        @Override public void scheduleFluidTick(
                Mc263StrongholdFillerCorridorPieceExecutor.BlockPos p,
                String fluid, int delay) { throw mutation(); }
        @Override public void markForPostprocessing(
                Mc263StrongholdStraightPieceExecutor.BlockPos p) { throw mutation(); }
        @Override public void markForPostprocessing(
                Mc263StrongholdLeftTurnPieceExecutor.BlockPos p) { throw mutation(); }
        @Override public void markForPostprocessing(
                Mc263StrongholdRightTurnPieceExecutor.BlockPos p) { throw mutation(); }
        @Override public void createLootChest(
                Mc263StrongholdStraightPieceExecutor.BlockPos p, String table,
                Mc263StrongholdStraightPieceExecutor.RandomSource random) { throw mutation(); }
        @Override public void configureSpawner(
                Mc263StrongholdStraightPieceExecutor.BlockPos p, String entity,
                Mc263StrongholdStraightPieceExecutor.RandomSource random) { throw mutation(); }
        private static AssertionError observation() {
            return new AssertionError("stronghold preflight observed world state");
        }
        private static AssertionError mutation() {
            return new AssertionError("stronghold preflight attempted mutation");
        }
    }
}
