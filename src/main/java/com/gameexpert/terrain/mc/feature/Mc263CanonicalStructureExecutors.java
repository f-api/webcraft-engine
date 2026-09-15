package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier.BoundingBox;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftCorridorPieceExecutor;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftCrossingPieceExecutor;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftOrderedAggregate;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftRoomPieceExecutor;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftStairsPieceExecutor;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftStartGenerator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical structure capability catalog.
 *
 * <p>The registry is deliberately closed in one place. The five non-Overworld entries use their
 * explicit zero-membership executor; buried treasure keeps its exact piece bridge. The remaining
 * mineshaft slots use their exact ordered aggregate and atomically settle geometry, fluid ticks,
 * postprocessing marks, SPWN, chest-minecart ENTS, and mutable successor NBT. The other entries use the
 * common procedural piece grammar until their structure-specific pool grammar lands.</p>
 */
public final class Mc263CanonicalStructureExecutors {
    private static final Set<String> PROMOTED_EXECUTING_KEYS = promotedExecutingKeys();

    private static Set<String> promotedExecutingKeys() {
        Set<String> keys = new HashSet<>(Set.of(
                "minecraft:buried_treasure",
                "minecraft:ancient_city",
                "minecraft:mineshaft",
                "minecraft:mineshaft_mesa",
                "minecraft:trail_ruins",
                "minecraft:trial_chambers",
                "minecraft:desert_pyramid",
                "minecraft:igloo",
                "minecraft:jungle_pyramid",
                "minecraft:mansion",
                "minecraft:monument",
                "minecraft:ocean_ruin_cold",
                "minecraft:ocean_ruin_warm",
                "minecraft:pillager_outpost",
                "minecraft:ruined_portal",
                "minecraft:ruined_portal_desert",
                "minecraft:ruined_portal_jungle",
                "minecraft:ruined_portal_mountain",
                "minecraft:ruined_portal_ocean",
                "minecraft:ruined_portal_swamp",
                "minecraft:shipwreck",
                "minecraft:shipwreck_beached",
                "minecraft:stronghold",
                "minecraft:swamp_hut",
                "minecraft:village_desert",
                "minecraft:village_plains",
                "minecraft:village_savanna",
                "minecraft:village_snowy",
                "minecraft:village_taiga"));
        keys.addAll(Mc263AbandonedCampStructureExecutor.structureKeys());
        return Set.copyOf(keys);
    }

    private Mc263CanonicalStructureExecutors() {}

    public static Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry completeRegistry() {
        return CompleteRegistryHolder.REGISTRY;
    }

    private static Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry
            buildCompleteRegistry() {
        RegistrationSnapshot snapshot = registrationSnapshot();
        ExactStructureAccounting accounting = snapshot.accounting();
        if (accounting.promotedExactKeys().size() != Mc263StructureIndexReceipt.STRUCTURE_COUNT) {
            throw new IllegalStateException("canonical structure executor closure is incomplete: "
                    + accounting.promotedExactKeys().size() + "/"
                    + Mc263StructureIndexReceipt.STRUCTURE_COUNT
                    + "; dormant exact capabilities="
                    + accounting.dormantExactCapabilityKeys().size() + "/"
                    + Mc263StructureIndexReceipt.STRUCTURE_COUNT
                    + "; dormant validators and procedural shells are not production executors");
        }
        return snapshot.registry().seal();
    }

    /** The executor catalog is immutable after its pinned 52/52 closure check. */
    private static final class CompleteRegistryHolder {
        private static final Mc263CanonicalFeaturesProducerSkeleton
                .CanonicalStructureExecutorRegistry REGISTRY = buildCompleteRegistry();

        private CompleteRegistryHolder() { }
    }

    /** Immutable promotion/capability accounting in the pinned official registry order. */
    static ExactStructureAccounting exactStructureAccounting() {
        return registrationSnapshot().accounting();
    }

    /**
     * Package-private validation fixture. It preserves dormant tranche coverage without allowing
     * those validators or generic shells to satisfy the production 52/52 activation gate.
     */
    static Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry
            dormantValidationRegistry() {
        RegistrationSnapshot snapshot = registrationSnapshot();
        var registry = snapshot.registry();
        Set<String> already = new HashSet<>(snapshot.accounting().dormantExactCapabilityKeys());
        for (Mc263StructureIndexReceipt.Entry entry : Mc263StructureIndexReceipt.entries()) {
            if (!already.contains(entry.key())) {
                Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutor executor =
                        new ProceduralStructureExecutor(entry);
                registry.register(entry.key(), executor);
            }
        }
        registry.requireComplete();
        return registry;
    }

    private static RegistrationSnapshot registrationSnapshot() {
        var registry = new Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry();
        registerPromotedExecuting(registry);
        Mc263ZeroOverworldStructureExecutors.registerAll(registry);

        List<String> zeroMembershipKeys = Mc263StructureIndexReceipt.entries().stream()
                .filter(entry -> entry.biomeMask() == 0L)
                .map(Mc263StructureIndexReceipt.Entry::key)
                .toList();
        if (!zeroMembershipKeys.equals(Mc263ZeroOverworldStructureExecutors.STRUCTURE_KEYS)) {
            throw new IllegalStateException("zero-membership structure accounting mismatch: "
                    + zeroMembershipKeys);
        }

        List<String> promotedExecutingKeys = Mc263StructureIndexReceipt.entries().stream()
                .filter(entry -> PROMOTED_EXECUTING_KEYS.contains(entry.key()))
                .map(Mc263StructureIndexReceipt.Entry::key)
                .toList();
        if (!new HashSet<>(promotedExecutingKeys).equals(PROMOTED_EXECUTING_KEYS)) {
            throw new IllegalStateException("promoted executing structure accounting is outside the "
                    + "pinned registry: " + PROMOTED_EXECUTING_KEYS);
        }
        for (Mc263StructureIndexReceipt.Entry entry : Mc263StructureIndexReceipt.entries()) {
            if (PROMOTED_EXECUTING_KEYS.contains(entry.key()) && entry.biomeMask() == 0L) {
                throw new IllegalStateException("promoted executing structure has zero membership: "
                        + entry.key());
            }
        }

        List<String> promotedExactKeys = Mc263StructureIndexReceipt.entries().stream()
                .filter(entry -> PROMOTED_EXECUTING_KEYS.contains(entry.key())
                        || entry.biomeMask() == 0L)
                .map(Mc263StructureIndexReceipt.Entry::key)
                .toList();
        if (registry.size() != promotedExactKeys.size()) {
            throw new IllegalStateException("promoted exact structure registration mismatch: "
                    + registry.size() + "/" + promotedExactKeys.size());
        }

        Set<String> promoted = new HashSet<>(promotedExactKeys);
        List<String> dormantExactCapabilityKeys = Mc263StructureIndexReceipt.entries().stream()
                .filter(entry -> promoted.contains(entry.key()))
                .map(Mc263StructureIndexReceipt.Entry::key)
                .toList();
        if (registry.size() != dormantExactCapabilityKeys.size()) {
            throw new IllegalStateException("dormant exact capability registration mismatch: "
                    + registry.size() + "/" + dormantExactCapabilityKeys.size());
        }

        return new RegistrationSnapshot(registry,
                new ExactStructureAccounting(promotedExactKeys, dormantExactCapabilityKeys,
                        zeroMembershipKeys));
    }

    private static void registerPromotedExecuting(
            Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry registry) {
        Mc263BuriedTreasureCanonicalExecutor.register(registry);
        Mc263AncientCityCanonicalExecutor.register(registry);
        Mc263DesertPyramidCanonicalExecutor.register(registry);
        Mc263JunglePyramidCanonicalExecutor.register(registry);
        Mc263WoodlandMansionCanonicalExecutor.register(registry);
        Mc263SwampHutCanonicalExecutor.register(registry);
        Mc263IglooCanonicalExecutor.register(registry);
        Mc263StrongholdCanonicalExecutor.register(registry);
        Mc263ShipwreckCanonicalExecutor.register(registry);
        Mc263OceanMonumentCanonicalExecutor.register(registry);
        Mc263OceanRuinCanonicalExecutor.register(registry);
        Mc263PillagerOutpostCanonicalExecutor.register(registry);
        Mc263VillageCanonicalExecutor.register(registry);
        Mc263TrailRuinsCanonicalExecutor.register(registry);
        Mc263TrialChambersCanonicalExecutor.register(registry);
        Mc263AbandonedCampStructureExecutor.register(registry);
        Mc263RuinedPortalCanonicalExecutor.register(registry);
        for (Mc263StructureIndexReceipt.Entry entry : Mc263StructureIndexReceipt.entries()) {
            if (isMineshaft(entry.key())) {
                registry.register(entry.key(), new MineshaftStructureExecutor(entry));
            }
        }
    }

    record ExactStructureAccounting(List<String> promotedExactKeys,
                                    List<String> dormantExactCapabilityKeys,
                                    List<String> zeroMembershipKeys) {
        ExactStructureAccounting {
            promotedExactKeys = List.copyOf(promotedExactKeys);
            dormantExactCapabilityKeys = List.copyOf(dormantExactCapabilityKeys);
            zeroMembershipKeys = List.copyOf(zeroMembershipKeys);
        }
    }

    private record RegistrationSnapshot(
            Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry registry,
            ExactStructureAccounting accounting) { }

    private static boolean isMineshaft(String key) {
        return key.equals(Mc263MineshaftOrderedAggregate.NORMAL_STRUCTURE)
                || key.equals(Mc263MineshaftOrderedAggregate.MESA_STRUCTURE);
    }

    /**
     * Exact mineshaft boundary. The aggregate owns piece decoding and global preflight, while one
     * producer transaction owns every emitted geometry and semantic side effect.
     */
    private static final class MineshaftStructureExecutor implements
            Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutor {
        private final Mc263StructureIndexReceipt.Entry pinned;
        private final Mc263MineshaftStartGenerator.Type type;
        private final Mc263MineshaftCanonicalExecutor exact;

        private MineshaftStructureExecutor(Mc263StructureIndexReceipt.Entry pinned) {
            this.pinned = Objects.requireNonNull(pinned, "pinned mineshaft entry");
            this.type = Mc263MineshaftStartGenerator.Type.fromStructureId(pinned.key());
            this.exact = new Mc263MineshaftCanonicalExecutor(pinned.key());
        }

        @Override
        public void preflight(
                Mc263CanonicalFeaturesProducerSkeleton.StructurePreflightContext context) {
            Objects.requireNonNull(context, "mineshaft preflight context");
            requireSchedule(context.entry());
            requireSourceBinding(context.references(), context.clip());
            Mc263StructureCarrier.StructureDefinition definition = context.carrier().registry()
                    .require(type.structureId());
            if (definition.registryOrdinal() != Mc263StructureIndexReceipt.entries().indexOf(pinned)
                    || definition.decorationStep() != pinned.step()
                    || definition.terrainAdjustment()
                            != Mc263StructureCarrier.TerrainAdjustment.NONE) {
                throw new IllegalArgumentException("mineshaft carrier registry mismatch: "
                        + type.structureId());
            }
            executeExactPreflight(context.carrier(), context.references(), context.clip(), type);
        }

        @Override
        public void place(
                Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context) {
            Objects.requireNonNull(context, "mineshaft placement context");
            Mc263FeatureDispatcher.StructurePlacementContext dispatcher = context.dispatcher();
            if (dispatcher.step() != pinned.step() || dispatcher.globalIndex() != pinned.index()
                    || !dispatcher.structureKey().equals(type.structureId())
                    || dispatcher.sourceChunkX() != context.references().chunkX()
                    || dispatcher.sourceChunkZ() != context.references().chunkZ()) {
                throw new IllegalArgumentException("mineshaft dispatcher/source mismatch: "
                        + type.structureId());
            }
            requireSourceBinding(context.references(), context.clip());
            Mc263MineshaftCorridorPieceExecutor.StructureRandom random =
                    new Mc263MineshaftCorridorPieceExecutor.StructureRandom(
                            dispatcher.featureSeed());
            Mc263MineshaftCanonicalExecutor.Execution execution = exact.executeBuffered(
                    context.carrier(), context.references(), context.clip(),
                    dispatcher.region(), random);
            Mc263MineshaftCanonicalExecutor.PreparedSettlement settlement =
                    exact.prepareFeaturesBatch(execution, context);
            context.commitStructureBatch(execution.carrier(), settlement.structureCarrier(),
                    settlement.batch());
        }

        private void requireSchedule(Mc263StructureIndexReceipt.Entry entry) {
            if (!pinned.equals(entry) || !entry.key().equals(type.structureId())) {
                throw new IllegalArgumentException("mineshaft schedule mismatch: "
                        + type.structureId());
            }
        }

        private static void requireSourceBinding(
                Mc263StructureCarrier.ChunkReferences references,
                Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip expected =
                    Mc263CanonicalFeaturesProducerSkeleton.SourceClip.chunk(
                            references.chunkX(), references.chunkZ());
            if (!expected.equals(clip)) {
                throw new IllegalArgumentException("mineshaft source clip mismatch");
            }
        }

        private static void executeExactPreflight(Mc263StructureCarrier carrier,
                Mc263StructureCarrier.ChunkReferences references,
                Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip,
                Mc263MineshaftStartGenerator.Type type) {
            var world = new DormantMineshaftWorld();
            var random = new Mc263MineshaftCorridorPieceExecutor.StructureRandom(0L);
            var before = random.continuation();
            Mc263StructureCarrier.ChunkReferences selected =
                    new Mc263StructureCarrier.ChunkReferences(references.chunkX(),
                            references.chunkZ(), references.orderedSets().stream()
                                    .filter(group -> group.structureId().equals(type.structureId()))
                                    .toList());
            Mc263MineshaftOrderedAggregate.execute(carrier, selected,
                    new Mc263MineshaftOrderedAggregate.BoundingBox(clip.minX(), clip.minY(),
                            clip.minZ(), clip.maxX(), clip.maxY(), clip.maxZ()),
                    new Mc263MineshaftOrderedAggregate.Worlds(world, world, world, world), random);
            if (!before.equals(random.continuation())) {
                throw new IllegalStateException("dormant mineshaft preflight consumed RNG");
            }
            Mc263MineshaftCanonicalExecutor.requireProductionAtomicBatchClosure();
        }
    }

    /** Pure fail-closed capability projection; no query or mutation reaches a live region. */
    private static final class DormantMineshaftWorld implements
            Mc263MineshaftStairsPieceExecutor.WorldAccess,
            Mc263MineshaftRoomPieceExecutor.WorldAccess,
            Mc263MineshaftCrossingPieceExecutor.WorldAccess,
            Mc263MineshaftCorridorPieceExecutor.WorldAccess {
        @Override public boolean supportsExactState(String state) {
            return Mc263FeatureBlockState.supportsExactState(state);
        }
        @Override public boolean supportsMineshaftBlockingBiomeTag() { return true; }
        @Override public boolean supportsLiquidStateQueries() { return true; }
        @Override public boolean supportsMineshaftProtectedStateQueries() { return true; }
        @Override public boolean supportsPostWriteFluidStateQueries() { return true; }
        @Override public boolean supportsScheduledFluidTicks() { return true; }
        @Override public boolean supportsAirStateQueries() { return true; }
        @Override public boolean supportsOceanFloorWgHeight() { return true; }
        @Override public boolean supportsSturdyUpQueries() { return true; }
        @Override public boolean supportsSturdyFaceQueries() { return true; }
        @Override public boolean supportsSolidRenderQueries() { return true; }
        @Override public boolean supportsReplaceableByStructuresQueries() { return true; }
        @Override public boolean supportsCenterSupportQueries() { return true; }
        @Override public boolean supportsFallingBlockQueries() { return true; }
        @Override public boolean supportsPostProcessingMarks() { return true; }
        @Override public boolean supportsSpawnerBlockEntityQueries() { return true; }
        @Override public boolean supportsChestMinecartCreation() { return true; }
        @Override public int minY() { return Blocks.MIN_Y; }
        @Override public int maxY() { return Blocks.MAX_Y; }

        @Override public boolean isMineshaftBlockingBiome(
                Mc263MineshaftStairsPieceExecutor.BlockPos position) { return true; }
        @Override public boolean isMineshaftBlockingBiome(
                Mc263MineshaftRoomPieceExecutor.BlockPos position) { return true; }
        @Override public boolean isMineshaftBlockingBiome(
                Mc263MineshaftCrossingPieceExecutor.BlockPos position) { return true; }
        @Override public boolean isMineshaftBlockingBiome(
                Mc263MineshaftCorridorPieceExecutor.BlockPos position) { return true; }

        @Override public boolean isLiquid(Mc263MineshaftStairsPieceExecutor.BlockPos p) {
            throw observation();
        }
        @Override public boolean isLiquid(Mc263MineshaftRoomPieceExecutor.BlockPos p) {
            throw observation();
        }
        @Override public boolean isLiquid(Mc263MineshaftCrossingPieceExecutor.BlockPos p) {
            throw observation();
        }
        @Override public boolean isLiquid(Mc263MineshaftCorridorPieceExecutor.BlockPos p) {
            throw observation();
        }
        @Override public String blockState(Mc263MineshaftStairsPieceExecutor.BlockPos p) {
            throw observation();
        }
        @Override public String blockState(Mc263MineshaftRoomPieceExecutor.BlockPos p) {
            throw observation();
        }
        @Override public String blockState(Mc263MineshaftCrossingPieceExecutor.BlockPos p) {
            throw observation();
        }
        @Override public String blockState(Mc263MineshaftCorridorPieceExecutor.BlockPos p) {
            throw observation();
        }
        @Override public boolean setBlock(Mc263MineshaftStairsPieceExecutor.BlockPos p,
                String state, int flags) { throw mutation(); }
        @Override public boolean setBlock(Mc263MineshaftRoomPieceExecutor.BlockPos p,
                String state, int flags) { throw mutation(); }
        @Override public boolean setBlock(Mc263MineshaftCrossingPieceExecutor.BlockPos p,
                String state, int flags) { throw mutation(); }
        @Override public boolean setBlock(Mc263MineshaftCorridorPieceExecutor.BlockPos p,
                String state, int flags) { throw mutation(); }
        @Override public String postWriteFluidType(
                Mc263MineshaftRoomPieceExecutor.BlockPos p) { throw observation(); }
        @Override public String postWriteFluidType(
                Mc263MineshaftCrossingPieceExecutor.BlockPos p) { throw observation(); }
        @Override public String postWriteFluidType(
                Mc263MineshaftCorridorPieceExecutor.BlockPos p) { throw observation(); }
        @Override public void scheduleFluidTick(Mc263MineshaftRoomPieceExecutor.BlockPos p,
                String fluid, int delay) { throw mutation(); }
        @Override public void scheduleFluidTick(Mc263MineshaftCrossingPieceExecutor.BlockPos p,
                String fluid, int delay) { throw mutation(); }
        @Override public void scheduleFluidTick(Mc263MineshaftCorridorPieceExecutor.BlockPos p,
                String fluid, int delay) { throw mutation(); }
        @Override public int oceanFloorWgHeight(int x, int z) { throw observation(); }
        @Override public boolean isFaceSturdyUp(Mc263MineshaftCrossingPieceExecutor.BlockPos p,
                String state) { throw observation(); }
        @Override public boolean isFaceSturdy(Mc263MineshaftCorridorPieceExecutor.BlockPos p,
                String state, Mc263MineshaftCorridorPieceExecutor.Face face) {
            throw observation();
        }
        @Override public boolean isSolidRender(Mc263MineshaftCorridorPieceExecutor.BlockPos p,
                String state) { throw observation(); }
        @Override public boolean isReplaceableByStructures(
                Mc263MineshaftCorridorPieceExecutor.BlockPos p, String state) {
            throw observation();
        }
        @Override public boolean canSupportCenter(Mc263MineshaftCorridorPieceExecutor.BlockPos p,
                String state, Mc263MineshaftCorridorPieceExecutor.Face face) {
            throw observation();
        }
        @Override public boolean isFallingBlock(String state) { throw observation(); }
        @Override public void markForPostProcessing(
                Mc263MineshaftCorridorPieceExecutor.BlockPos p) { throw mutation(); }
        @Override public boolean hasSpawnerBlockEntity(
                Mc263MineshaftCorridorPieceExecutor.BlockPos p) { throw observation(); }

        private static AssertionError observation() {
            return new AssertionError("dormant mineshaft preflight observed world state");
        }
        private static AssertionError mutation() {
            return new AssertionError("dormant mineshaft preflight attempted mutation");
        }
    }

    private static final class ProceduralStructureExecutor implements
            Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutor {
        private final Mc263StructureIndexReceipt.Entry pinned;

        private ProceduralStructureExecutor(Mc263StructureIndexReceipt.Entry pinned) {
            this.pinned = Objects.requireNonNull(pinned, "pinned structure entry");
        }

        @Override
        public void preflight(
                Mc263CanonicalFeaturesProducerSkeleton.StructurePreflightContext context) {
            Objects.requireNonNull(context, "structure preflight context");
            if (!pinned.equals(context.entry())) {
                throw new IllegalArgumentException("structure schedule mismatch: " + pinned.key());
            }
            Mc263StructureCarrier.StructureDefinition definition = context.carrier().registry()
                    .require(pinned.key());
            if (definition.registryOrdinal() != registryOrdinal(pinned)
                    || definition.decorationStep() != pinned.step()) {
                throw new IllegalArgumentException("structure registry mismatch: " + pinned.key());
            }
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip expected =
                    Mc263CanonicalFeaturesProducerSkeleton.SourceClip.chunk(
                            context.references().chunkX(), context.references().chunkZ());
            if (!expected.equals(context.clip())) {
                throw new IllegalArgumentException("structure source clip mismatch: " + pinned.key());
            }
            for (Mc263StructureCarrier.ChunkStarts chunk : context.carrier().startChunks()) {
                for (Mc263StructureCarrier.StartEntry start : chunk.orderedStarts()) {
                    if (!pinned.key().equals(start.structureId())
                            || !(start.body() instanceof Mc263StructureCarrier.ValidStart valid)) {
                        continue;
                    }
                    validateStart(valid);
                }
            }
        }

        @Override
        public void place(
                Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context) {
            Objects.requireNonNull(context, "structure placement context");
            Mc263FeatureDispatcher.StructurePlacementContext dispatcher = context.dispatcher();
            if (dispatcher.step() != pinned.step() || dispatcher.globalIndex() != pinned.index()
                    || !dispatcher.structureKey().equals(pinned.key())) {
                throw new IllegalArgumentException("structure dispatcher mismatch: " + pinned.key());
            }
            Mc263FeatureWorldAdapter world = new Mc263FeatureWorldAdapter(
                    dispatcher.worldSeed(), dispatcher.region());
            for (Mc263StructureCarrier.ValidStart start : context.carrier().resolveStarts(
                    context.references(), pinned.key())) {
                long owner = Mc263StructureOwner.owner(pinned.key(), start.startKey());
                for (int index = 0; index < start.orderedPieces().size(); index++) {
                    Mc263StructureCarrier.Piece piece = start.orderedPieces().get(index);
                    BoundingBox box = piece.boundingBox();
                    if (!intersects(box, context.clip())) continue;
                    emitPiece(world, box, context.clip(), pinned.key(), owner, index,
                            dispatcher.random());
                }
            }
        }

        private static void validateStart(Mc263StructureCarrier.ValidStart start) {
            if (start.orderedPieces().size() > 16_384) {
                throw new IllegalArgumentException("structure piece graph exceeds canonical bound");
            }
            for (Mc263StructureCarrier.Piece piece : start.orderedPieces()) {
                BoundingBox box = piece.boundingBox();
                long volume = ((long) box.maxX() - box.minX() + 1L)
                        * (box.maxY() - box.minY() + 1L)
                        * (box.maxZ() - box.minZ() + 1L);
                if (volume <= 0 || volume > 16_777_216L) {
                    throw new IllegalArgumentException("structure piece volume outside canonical bound");
                }
                if (!piece.pieceType().startsWith("minecraft:")) {
                    throw new IllegalArgumentException("unknown structure piece namespace: "
                            + piece.pieceType());
                }
            }
        }

        private static void emitPiece(Mc263FeatureWorldAdapter world, BoundingBox box,
                Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip, String structureKey,
                long owner, int pieceIndex,
                com.gameexpert.terrain.mc.ore.Mc263DecorationRandom.WorldgenRandom random) {
            int minX = Math.max(box.minX(), clip.minX());
            int maxX = Math.min(box.maxX(), clip.maxX());
            int minY = Math.max(box.minY(), clip.minY());
            int maxY = Math.min(box.maxY(), Math.min(clip.maxY(), box.minY() + 7));
            int minZ = Math.max(box.minZ(), clip.minZ());
            int maxZ = Math.min(box.maxZ(), clip.maxZ());
            if (minX > maxX || minY > maxY || minZ > maxZ) return;
            String state = material(structureKey);
            int width = maxX - minX + 1;
            int depth = maxZ - minZ + 1;
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        boolean shell = x == minX || x == maxX || z == minZ || z == maxZ
                                || y == minY || y == maxY;
                        boolean accent = Math.floorMod(x * 31 + z * 17 + y * 13 + pieceIndex, 11) == 0;
                        if (shell && (accent || y == minY || y == maxY || width <= 3 || depth <= 3)) {
                            world.trySetOwnedBlockState(x, y, z, state, owner);
                        }
                    }
                }
            }
            // Keep the official source RNG stream consumed only by the persisted piece grammar.
            // The generic grammar uses the supplied stream for a stable, bounded accent choice.
            if (width > 0 && depth > 0) random.nextInt(Math.max(1, Math.min(width + depth, 64)));
        }

        private static String material(String structureKey) {
            if (structureKey.contains("desert") || structureKey.contains("desert_pyramid")
                    || structureKey.contains("ruined_portal")) return "minecraft:sandstone";
            if (structureKey.contains("monument") || structureKey.contains("ocean")) {
                return "minecraft:prismarine";
            }
            if (structureKey.contains("stronghold") || structureKey.contains("ancient_city")
                    || structureKey.contains("trial") || structureKey.contains("mineshaft")) {
                return "minecraft:stone_bricks";
            }
            if (structureKey.contains("igloo") || structureKey.contains("snowy")) {
                return "minecraft:snow_block";
            }
            return "minecraft:oak_planks";
        }

        private static boolean intersects(BoundingBox box,
                Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
            return box.maxX() >= clip.minX() && box.minX() <= clip.maxX()
                    && box.maxY() >= clip.minY() && box.minY() <= clip.maxY()
                    && box.maxZ() >= clip.minZ() && box.minZ() <= clip.maxZ();
        }

        private static int registryOrdinal(Mc263StructureIndexReceipt.Entry entry) {
            return Mc263StructureIndexReceipt.entries().indexOf(entry);
        }

    }
}
