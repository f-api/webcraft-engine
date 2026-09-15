package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.Mc263BaseHeightSampler;
import com.gameexpert.terrain.mc.biome.McBiomeRegistry;
import com.gameexpert.terrain.mc.ore.Mc263DecorationRandom;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalProducer;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalProductionExecutor;
import com.gameexpert.terrain.mc.structure.Mc263RuinedPortalProgram;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import com.gameexpert.terrain.mc.structure.Mc263StructureWorldAccess;
import com.gameexpert.terrain.mc.surface.McFreezeTopLayer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Atomic canonical FEATURES adapters for the six pinned Overworld ruined-portal slots. */
final class Mc263RuinedPortalCanonicalExecutor implements
        Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutor {
    private static final int STEP = Mc263RuinedPortalProducer.GENERATION_STEP;
    private static final List<Slot> SLOTS = List.of(
            new Slot("minecraft:ruined_portal", 28, 33, 0x703f817f6ac001L),
            new Slot("minecraft:ruined_portal_desert", 29, 34, 0x00000800000000L),
            new Slot("minecraft:ruined_portal_jungle", 30, 35, 0x00c00200000000L),
            new Slot("minecraft:ruined_portal_mountain", 31, 36, 0x0f007480952400L),
            new Slot("minecraft:ruined_portal_ocean", 33, 38, 0x000000000003feL),
            new Slot("minecraft:ruined_portal_swamp", 34, 39, 0x00000000001800L));
    private static final Map<String, McBiomeRegistry.Biome> BIOMES = loadBiomes();

    private final Slot slot;
    private final Capabilities capabilities;

    private Mc263RuinedPortalCanonicalExecutor(Slot slot, Capabilities capabilities) {
        this.slot = Objects.requireNonNull(slot, "ruined-portal slot");
        this.capabilities = Objects.requireNonNull(capabilities, "ruined-portal capabilities");
    }

    /** Additive registration hook for the later global-registry wiring change. */
    static void register(
            Mc263CanonicalFeaturesProducerSkeleton.CanonicalStructureExecutorRegistry registry) {
        Objects.requireNonNull(registry, "canonical structure executor registry");
        for (Slot slot : SLOTS) {
            registry.register(slot.key, new Mc263RuinedPortalCanonicalExecutor(
                    slot, Capabilities.production()));
        }
    }

    static Mc263RuinedPortalCanonicalExecutor fixture(String key,
            boolean exactStates, boolean semanticLanes, boolean atomicSettlement) {
        Slot slot = SLOTS.stream().filter(value -> value.key.equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown Overworld ruined-portal key: " + key));
        return new Mc263RuinedPortalCanonicalExecutor(slot,
                new Capabilities(exactStates, semanticLanes, atomicSettlement, false));
    }

    static Mc263RuinedPortalCanonicalExecutor productionFixture(String key) {
        Slot slot = SLOTS.stream().filter(value -> value.key.equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown Overworld ruined-portal key: " + key));
        return new Mc263RuinedPortalCanonicalExecutor(slot, Capabilities.production());
    }

    @Override
    public void preflight(
            Mc263CanonicalFeaturesProducerSkeleton.StructurePreflightContext context) {
        Objects.requireNonNull(context, "ruined-portal preflight context");
        // This ordering is contractual: rejection cannot resolve a reference, query terrain,
        // instantiate a placement random, copy a region, or stage a write.
        requireCapabilities();
        requireAcceptedProducer();
        requireSchedule(context.entry(), context.carrier().registry());
        requireSourceBinding(context.references(), context.clip());
        validatePersistedStarts(context.carrier());
    }

    @Override
    public void place(Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput context) {
        Objects.requireNonNull(context, "ruined-portal placement context");
        requireCapabilities();
        requireAcceptedProducer();
        requireDispatcher(context.dispatcher(), context.references());
        requireSchedule(Mc263StructureIndexReceipt.step(STEP).get(slot.index),
                context.carrier().registry());
        requireSourceBinding(context.references(), context.clip());

        long expectedSeed = Mc263DecorationRandom.featureSeed(
                context.dispatcher().decorationSeed(), slot.index, STEP);
        if (context.dispatcher().featureSeed() != expectedSeed) {
            throw new IllegalArgumentException("ruined-portal featureSeed mismatch: " + slot.key);
        }

        List<Mc263StructureCarrier.ValidStart> referenced = context.carrier()
                .resolveStarts(context.references(), slot.key);
        ArrayList<PreparedStart> prepared = new ArrayList<>();
        for (Mc263StructureCarrier.ValidStart start : referenced) {
            validatePersistedStart(start);
            Mc263StructureCarrier.BoundingBox box = start.adjustedBoundingBox();
            if (box.maxX() < context.clip().minX() || box.minX() > context.clip().maxX()
                    || box.maxY() < context.clip().minY()
                    || box.minY() > context.clip().maxY()
                    || box.maxZ() < context.clip().minZ()
                    || box.minZ() > context.clip().maxZ()) {
                continue;
            }
            int persistedCenterX = box.minX() + (box.maxX() - box.minX() + 1) / 2;
            int persistedCenterY = box.minY() + (box.maxY() - box.minY() + 1) / 2;
            int persistedCenterZ = box.minZ() + (box.maxZ() - box.minZ() + 1) / 2;
            if (!clip(context.clip()).contains(new Mc263RuinedPortalProgram.BlockPos(
                    persistedCenterX, persistedCenterY, persistedCenterZ))) continue;
            int successorReferences = Math.addExact(start.references(), 1);
            Mc263RuinedPortalProgram.Plan plan = Mc263RuinedPortalProgram.plan(slot.key,
                    context.dispatcher().worldSeed(), start.originChunkX(), start.originChunkZ(),
                    new RegionTerrain(context.dispatcher().region()));
            try {
                requirePlanBinding(start, plan);
            } catch (IllegalArgumentException mutableRegionMismatch) {
                // The persisted start was selected before FEATURES from ChunkGenerator's base
                // column. A replay region can already contain carvers/features, so its opacity
                // lane is not the start producer's authority. Re-derive from that exact base lane
                // and retain the same full key/box/piece byte comparison below.
                plan = Mc263RuinedPortalProgram.plan(slot.key,
                        context.dispatcher().worldSeed(), start.originChunkX(),
                        start.originChunkZ(), new OriginTerrain(
                                context.dispatcher().worldSeed()));
                requirePlanBinding(start, plan);
            }
            prepared.add(new PreparedStart(start, plan, successorReferences));
        }
        if (prepared.isEmpty()) return;

        CapturingWorld world = new CapturingWorld(context.dispatcher().region(),
                capabilities.catalogBacked, context);
        Mc263RuinedPortalProductionExecutor.PlacementRandom candidate =
                Mc263RuinedPortalProductionExecutor.PlacementRandom.forChunk(
                        context.dispatcher().worldSeed(), context.references().chunkX(),
                        context.references().chunkZ(), slot.index, STEP);
        int initialDrawCount = candidate.count();
        ArrayList<Mc263StructureCarrier.ValidStart> replacements =
                new ArrayList<>(prepared.size());
        for (PreparedStart start : prepared) {
            Mc263RuinedPortalProductionExecutor.AtomicSettlement settlement =
                    Mc263RuinedPortalProductionExecutor.execute(start.plan,
                            context.references().chunkX(), context.references().chunkZ(), slot.index,
                            clip(context.clip()), world, candidate);
            world.requireCaptured(settlement);
            replacements.add(validatedSuccessorStart(
                    context.carrier().registry(), start, settlement));
            world.owner(start.persisted.startKey(), settlement);
        }

        Mc263StructureCarrier predecessor = context.carrier();
        Mc263StructureCarrier successor = canonicalSuccessor(predecessor, replacements);
        context.commitStructureBatch(predecessor, successor, world.batch());
        int consumed = Math.subtractExact(candidate.count(), initialDrawCount);
        for (int draw = 0; draw < consumed; draw++) context.dispatcher().random().next(32);
    }

    private void requireCapabilities() {
        if (!capabilities.atomicSettlement) {
            throw new UnsupportedOperationException(
                    "atomic ruined-portal settlement capability required");
        }
        if (!capabilities.semanticLanes) {
            throw new UnsupportedOperationException(
                    "ruined-portal LOOT/BENT/FTIK capabilities required");
        }
        if (!capabilities.exactStates) {
            throw new UnsupportedOperationException(
                    "ruined-portal exact-state capability required");
        }
        if (capabilities.catalogBacked
                && !Mc263FeatureBlockState.supportsExactState("minecraft:netherrack")) {
            throw new UnsupportedOperationException(
                    "unsupported ruined-portal state: minecraft:netherrack");
        }
    }

    private static void requireAcceptedProducer() {
        if (!Mc263RuinedPortalProducer.ORACLE_SOURCE_SHA256.equals(
                    "38d943d1168d02d3d00561c8f4d3bafd53fdce99952b6d0513cab99064453508")
                || !Mc263RuinedPortalProducer.ORACLE_CONTRACT_SHA256.equals(
                    "d8d6f328a5f24c8ae444d808d278545bd7064f748b4ff7a094e9eed910602e5a")
                || !Mc263RuinedPortalProducer.ORACLE_SHA256.equals(
                    "b6d052ce51d8b55e5dbcf0afb344c27bfafcee6860093e261aff6beccbb52e10")) {
            throw new IllegalStateException("accepted ruined-portal producer identity drift");
        }
    }

    private void requireSchedule(Mc263StructureIndexReceipt.Entry entry,
            Mc263StructureCarrier.Registry registry) {
        Mc263StructureCarrier.StructureDefinition definition = registry.require(slot.key);
        if (entry.step() != STEP || entry.index() != slot.index || !entry.key().equals(slot.key)
                || entry.biomeMask() != slot.biomeMask
                || !Mc263StructureIndexReceipt.entries().get(slot.ordinal).equals(entry)
                || definition.registryOrdinal() != slot.ordinal
                || !definition.structureId().equals(slot.key)
                || definition.decorationStep() != STEP
                || definition.terrainAdjustment()
                        != Mc263StructureCarrier.TerrainAdjustment.NONE) {
            throw new IllegalArgumentException(
                    "ruined-portal schedule/membership/ordinal mismatch: " + slot.key);
        }
    }

    private void requireDispatcher(Mc263FeatureDispatcher.StructurePlacementContext dispatcher,
            Mc263StructureCarrier.ChunkReferences references) {
        if (dispatcher.step() != STEP || dispatcher.globalIndex() != slot.index
                || !dispatcher.structureKey().equals(slot.key)
                || dispatcher.sourceChunkX() != references.chunkX()
                || dispatcher.sourceChunkZ() != references.chunkZ()) {
            throw new IllegalArgumentException(
                    "ruined-portal dispatcher/source mismatch: " + slot.key);
        }
    }

    private static void requireSourceBinding(Mc263StructureCarrier.ChunkReferences references,
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip clip) {
        if (!Mc263CanonicalFeaturesProducerSkeleton.SourceClip.chunk(
                references.chunkX(), references.chunkZ()).equals(clip)) {
            throw new IllegalArgumentException("ruined-portal source clip mismatch");
        }
    }

    private void validatePersistedStarts(Mc263StructureCarrier carrier) {
        for (Mc263StructureCarrier.ChunkStarts chunk : carrier.startChunks()) {
            for (Mc263StructureCarrier.StartEntry entry : chunk.orderedStarts()) {
                if (!entry.structureId().equals(slot.key)
                        || !(entry.body() instanceof Mc263StructureCarrier.ValidStart start)) {
                    continue;
                }
                validatePersistedStart(start);
            }
        }
    }

    private void validatePersistedStart(Mc263StructureCarrier.ValidStart start) {
        if (start.references() < 0 || start.orderedPieces().size() != 1
                || !start.startKey().equals(slot.key + "@" + start.originChunkX()
                        + "," + start.originChunkZ())) {
            throw new IllegalArgumentException("noncanonical persisted ruined-portal start");
        }
        Mc263StructureCarrier.Piece piece = start.orderedPieces().getFirst();
        byte[] payload = piece.persistedPayload().binaryNbtCompound();
        if (!piece.pieceType().equals(Mc263RuinedPortalProgram.PIECE_TYPE)
                || piece.poolElement()
                || piece.projection() != Mc263StructureCarrier.Projection.NOT_APPLICABLE
                || piece.groundLevelDelta() != 0 || !piece.junctions().isEmpty()
                || !piece.boundingBox().equals(start.adjustedBoundingBox())
                || payload.length < 4 || payload[0] != 10 || payload[1] != 0
                || payload[2] != 0 || payload[payload.length - 1] != 0) {
            throw new IllegalArgumentException("noncanonical persisted ruined-portal piece");
        }
    }

    private static void requirePlanBinding(Mc263StructureCarrier.ValidStart start,
            Mc263RuinedPortalProgram.Plan plan) {
        Mc263StructureCarrier.Piece piece = start.orderedPieces().getFirst();
        Mc263RuinedPortalProgram.BoundingBox box = plan.boundingBox();
        Mc263StructureCarrier.BoundingBox persisted = start.adjustedBoundingBox();
        String expectedKey = plan.structureKey() + "@" + plan.chunkX() + "," + plan.chunkZ();
        byte[] persistedPiece = piece.persistedPayload().binaryNbtCompound();
        byte[] replayedPiece = plan.pieceNbt();
        if (!start.startKey().equals(expectedKey)
                || persisted.minX() != box.minX() || persisted.minY() != box.minY()
                || persisted.minZ() != box.minZ() || persisted.maxX() != box.maxX()
                || persisted.maxY() != box.maxY() || persisted.maxZ() != box.maxZ()
                || !Arrays.equals(persistedPiece, replayedPiece)) {
            throw new IllegalArgumentException("ruined-portal persisted start replay mismatch: "
                    + "key=" + start.startKey() + "/" + expectedKey
                    + ", box=" + persisted + "/" + box
                    + ", piece=" + sha256(persistedPiece) + "/" + sha256(replayedPiece));
        }
    }

    private static Mc263StructureCarrier.ValidStart validatedSuccessorStart(
            Mc263StructureCarrier.Registry registry, PreparedStart prepared,
            Mc263RuinedPortalProductionExecutor.AtomicSettlement settlement) {
        Mc263RuinedPortalProgram.Plan successor = Objects.requireNonNull(
                settlement.successor(), "ruined-portal producer successor");
        if (!Arrays.equals(successor.startNbt(), prepared.plan.startNbt())
                || !Arrays.equals(successor.pieceNbt(), prepared.plan.pieceNbt())) {
            throw new IllegalArgumentException(
                    "ruined-portal producer successor byte mismatch");
        }
        requirePlanBinding(prepared.persisted, successor);
        Mc263StructureCarrier local = successor.structureCarrier(registry, prepared.successorReferences).strictlyDecoded();
        if (local.startChunks().size() != 1
                || local.startChunks().getFirst().orderedStarts().size() != 1
                || !(local.startChunks().getFirst().orderedStarts().getFirst().body()
                        instanceof Mc263StructureCarrier.ValidStart replacement)
                || !local.startChunks().getFirst().orderedStarts().getFirst().structureId()
                        .equals(successor.structureKey())
                || !replacement.startKey().equals(prepared.persisted.startKey())
                || replacement.originChunkX() != prepared.persisted.originChunkX()
                || replacement.originChunkZ() != prepared.persisted.originChunkZ()
                || replacement.references() != prepared.successorReferences
                || !replacement.adjustedBoundingBox()
                        .equals(prepared.persisted.adjustedBoundingBox())
                || !replacement.orderedPieces().equals(prepared.persisted.orderedPieces())) {
            throw new IllegalArgumentException(
                    "ruined-portal producer successor changed persisted start identity");
        }
        return replacement;
    }

    static Mc263StructureCarrier canonicalSuccessor(Mc263StructureCarrier predecessor,
            List<Mc263StructureCarrier.ValidStart> replacements) {
        Objects.requireNonNull(predecessor, "ruined-portal STR predecessor");
        LinkedHashMap<String, Mc263StructureCarrier.ValidStart> targets = new LinkedHashMap<>();
        for (Mc263StructureCarrier.ValidStart replacement
                : List.copyOf(Objects.requireNonNull(replacements,
                        "ruined-portal STR replacements"))) {
            Objects.requireNonNull(replacement, "ruined-portal STR replacement");
            if (targets.putIfAbsent(replacement.startKey(), replacement) != null) {
                throw new IllegalArgumentException("duplicate Ruined Portal STR replacement");
            }
        }

        HashSet<String> replaced = new HashSet<>();
        ArrayList<Mc263StructureCarrier.ChunkStarts> chunks = new ArrayList<>();
        for (Mc263StructureCarrier.ChunkStarts chunk : predecessor.startChunks()) {
            ArrayList<Mc263StructureCarrier.StartEntry> entries = new ArrayList<>();
            for (Mc263StructureCarrier.StartEntry entry : chunk.orderedStarts()) {
                Mc263StructureCarrier.ValidStart replacement =
                        entry.body() instanceof Mc263StructureCarrier.ValidStart valid
                                ? targets.get(valid.startKey()) : null;
                if (replacement == null) {
                    entries.add(entry);
                    continue;
                }
                Mc263StructureCarrier.ValidStart prior =
                        (Mc263StructureCarrier.ValidStart) entry.body();
                if (!replaced.add(prior.startKey())) {
                    throw new IllegalArgumentException("duplicate Ruined Portal STR predecessor");
                }
                int expectedReferences = Math.addExact(prior.references(), 1);
                if (replacement.references() != expectedReferences
                        || replacement.originChunkX() != prior.originChunkX()
                        || replacement.originChunkZ() != prior.originChunkZ()
                        || !replacement.adjustedBoundingBox().equals(prior.adjustedBoundingBox())
                        || !replacement.orderedPieces().equals(prior.orderedPieces())) {
                    throw new IllegalArgumentException(
                            "Ruined Portal STR replacement changed persisted start identity");
                }
                entries.add(new Mc263StructureCarrier.StartEntry(
                        entry.structureId(), replacement));
            }
            chunks.add(chunk.withStarts(entries));
        }
        if (replaced.size() != targets.size()) {
            throw new IllegalArgumentException("missing Ruined Portal STR predecessor");
        }
        Mc263StructureCarrier successor = new Mc263StructureCarrier(predecessor.registry(), chunks,
                predecessor.referenceChunks(), predecessor.rawStartPayloads(),
                predecessor.producerGraphPayloads());
        return successor.strictlyDecoded();
    }

    private static Mc263RuinedPortalProductionExecutor.Clip clip(
            Mc263CanonicalFeaturesProducerSkeleton.SourceClip value) {
        return new Mc263RuinedPortalProductionExecutor.Clip(value.minX(), value.minY(),
                value.minZ(), value.maxX(), value.maxY(), value.maxZ());
    }

    private static Map<String, McBiomeRegistry.Biome> loadBiomes() {
        LinkedHashMap<String, McBiomeRegistry.Biome> result = new LinkedHashMap<>();
        for (int id : McBiomeRegistry.ids()) {
            McBiomeRegistry.Biome biome = McBiomeRegistry.get(id);
            result.put(biome.name(), biome);
        }
        return Map.copyOf(result);
    }

    private static final class Slot {
        private final String key;
        private final int index;
        private final int ordinal;
        private final long biomeMask;

        private Slot(String key, int index, int ordinal, long biomeMask) {
            this.key = key;
            this.index = index;
            this.ordinal = ordinal;
            this.biomeMask = biomeMask;
        }
    }

    private static final class Capabilities {
        private final boolean exactStates;
        private final boolean semanticLanes;
        private final boolean atomicSettlement;
        private final boolean catalogBacked;

        private Capabilities(boolean exactStates, boolean semanticLanes,
                boolean atomicSettlement, boolean catalogBacked) {
            this.exactStates = exactStates;
            this.semanticLanes = semanticLanes;
            this.atomicSettlement = atomicSettlement;
            this.catalogBacked = catalogBacked;
        }

        private static Capabilities production() {
            return new Capabilities(true, true, true, true);
        }
    }

    private static final class PreparedStart {
        private final Mc263StructureCarrier.ValidStart persisted;
        private final Mc263RuinedPortalProgram.Plan plan;
        private final int successorReferences;

        private PreparedStart(Mc263StructureCarrier.ValidStart persisted,
                Mc263RuinedPortalProgram.Plan plan, int successorReferences) {
            this.persisted = persisted;
            this.plan = plan;
            this.successorReferences = successorReferences;
        }
    }

    private static final class RegionTerrain implements Mc263RuinedPortalProgram.Terrain {
        private final Mc263FeaturesRegion region;

        private RegionTerrain(Mc263FeaturesRegion region) {
            this.region = Objects.requireNonNull(region, "ruined-portal terrain region");
        }

        @Override public int minY() { return Blocks.MIN_Y; }
        @Override public int seaLevel() { return Blocks.SEA_LEVEL; }
        @Override public int baseHeight(int x, int z,
                Mc263RuinedPortalProgram.Heightmap heightmap) {
            return heightmap == Mc263RuinedPortalProgram.Heightmap.WORLD_SURFACE_WG
                    ? region.worldSurfaceWg(x, z) : region.oceanFloorWg(x, z);
        }
        @Override public boolean opaqueInBaseColumn(int x, int y, int z,
                Mc263RuinedPortalProgram.Heightmap heightmap) {
            return region.blockState(x, y, z).isSolidRender();
        }
        @Override public boolean coldEnoughToSnow(Mc263RuinedPortalProgram.BlockPos position,
                int seaLevel) {
            McBiomeRegistry.Biome biome = BIOMES.get(
                    region.biomeKey(position.x(), position.y(), position.z()));
            if (biome == null || !biome.precipitation()) return false;
            return McFreezeTopLayer.computeTemperature(position.x(), position.y(), position.z(),
                    biome.temperature(), biome.frozenModifier(), seaLevel) < 0.15F;
        }
    }

    private static final class OriginTerrain implements Mc263RuinedPortalProgram.Terrain {
        private final Mc263BaseHeightSampler base;
        private final Mc263StructureWorldAccess world;

        private OriginTerrain(long worldSeed) {
            base = Mc263BaseHeightSampler.overworld(worldSeed);
            world = Mc263StructureWorldAccess.overworld(worldSeed).bindBaseHeightSampler(base);
        }

        @Override public int minY() { return Blocks.MIN_Y; }
        @Override public int seaLevel() { return Blocks.SEA_LEVEL; }
        @Override public int baseHeight(int x, int z,
                Mc263RuinedPortalProgram.Heightmap heightmap) {
            return heightmap == Mc263RuinedPortalProgram.Heightmap.WORLD_SURFACE_WG
                    ? base.worldSurfaceWg(x, z) : base.oceanFloorWg(x, z);
        }
        @Override public boolean opaqueInBaseColumn(int x, int y, int z,
                Mc263RuinedPortalProgram.Heightmap heightmap) {
            return base.opaqueInBaseColumn(x, y, z,
                    heightmap == Mc263RuinedPortalProgram.Heightmap.OCEAN_FLOOR_WG);
        }
        @Override public boolean coldEnoughToSnow(Mc263RuinedPortalProgram.BlockPos position,
                int seaLevel) {
            return world.coldEnoughToSnow(position.x(), position.y(), position.z(), seaLevel);
        }
    }

    private static final class CapturingWorld
            implements Mc263RuinedPortalProductionExecutor.WorldAccess {
        private final Mc263FeaturesRegion region;
        private final boolean catalogBacked;
        private final Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput placement;
        private final LinkedHashMap<Mc263RuinedPortalProgram.BlockPos, String> overlay =
                new LinkedHashMap<>();
        private final ArrayList<OwnedSettlement> settlements = new ArrayList<>();
        private Mc263RuinedPortalProductionExecutor.AtomicSettlement captured;

        private CapturingWorld(Mc263FeaturesRegion region, boolean catalogBacked,
                Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput placement) {
            this.region = Objects.requireNonNull(region, "ruined-portal FEATURES region");
            this.catalogBacked = catalogBacked;
            this.placement = Objects.requireNonNull(placement,
                    "ruined-portal placement context");
        }

        @Override public boolean supportsAtomicSettlement() { return true; }
        @Override public boolean supportsExactState(String state) {
            return !catalogBacked || Mc263FeatureBlockState.supportsExactState(state);
        }
        @Override public boolean supportsLootTable(String table) {
            return table.equals(Mc263RuinedPortalProgram.LOOT_TABLE);
        }
        @Override public boolean supportsBlockEntity(String block, String entity) {
            return block.equals("minecraft:chest") && entity.equals("minecraft:chest");
        }
        @Override public String blockState(Mc263RuinedPortalProgram.BlockPos position) {
            return overlay.getOrDefault(position,
                    region.blockState(position.x(), position.y(), position.z()).exactState());
        }
        @Override public String fluidState(Mc263RuinedPortalProgram.BlockPos position) {
            return Mc263FeatureBlockState.fromExact(blockState(position)).fluidTypeKey();
        }
        @Override public boolean canFeatureReplace(Mc263RuinedPortalProgram.BlockPos position) {
            return !Mc263FeatureBlockState.fromExact(blockState(position)).featuresCannotReplace();
        }
        @Override public boolean isFaceFull(Mc263RuinedPortalProgram.BlockPos position,
                Mc263RuinedPortalProductionExecutor.Direction face) {
            Mc263FeatureBlockState.OcclusionFace mapped = switch (face) {
                case NORTH -> Mc263FeatureBlockState.OcclusionFace.NORTH;
                case EAST -> Mc263FeatureBlockState.OcclusionFace.EAST;
                case SOUTH -> Mc263FeatureBlockState.OcclusionFace.SOUTH;
                case WEST -> Mc263FeatureBlockState.OcclusionFace.WEST;
            };
            return Mc263FeatureBlockState.fromExact(blockState(position))
                    .isFaceOcclusionFull(mapped);
        }
        @Override public int height(Mc263RuinedPortalProgram.Heightmap heightmap,
                int x, int z) {
            int base = heightmap == Mc263RuinedPortalProgram.Heightmap.WORLD_SURFACE_WG
                    ? region.worldSurfaceWg(x, z) : region.oceanFloorWg(x, z);
            int top = base - 1;
            for (Map.Entry<Mc263RuinedPortalProgram.BlockPos, String> value : overlay.entrySet()) {
                if (value.getKey().x() == x && value.getKey().z() == z
                        && heightMatches(value.getValue(), heightmap)) {
                    top = Math.max(top, value.getKey().y());
                }
            }
            return top + 1;
        }
        @Override public void settle(
                Mc263RuinedPortalProductionExecutor.AtomicSettlement settlement) {
            if (captured != null) {
                throw new IllegalStateException("unclaimed ruined-portal settlement");
            }
            captured = Objects.requireNonNull(settlement, "ruined-portal settlement");
        }

        private void requireCaptured(
                Mc263RuinedPortalProductionExecutor.AtomicSettlement settlement) {
            if (captured != settlement) {
                throw new IllegalStateException("ruined-portal settlement was not captured");
            }
        }

        private void owner(String startKey,
                Mc263RuinedPortalProductionExecutor.AtomicSettlement settlement) {
            requireCaptured(settlement);
            OfficialQueryReceipt officialQueries = officialQueryReceipt(settlement);
            for (Mc263RuinedPortalProductionExecutor.BlockWrite write : settlement.writes()) {
                overlay.put(write.position(), write.exactState());
            }
            settlements.add(new OwnedSettlement(startKey, settlement, officialQueries));
            captured = null;
        }

        private Mc263FeaturesRegion.StructureBatch batch() {
            return structureBatch(settlements, placement);
        }

        private static boolean heightMatches(String state,
                Mc263RuinedPortalProgram.Heightmap heightmap) {
            Mc263FeatureBlockState value = Mc263FeatureBlockState.fromExact(state);
            return !value.isAir() && (heightmap == Mc263RuinedPortalProgram.Heightmap.WORLD_SURFACE_WG
                    || value.fluidKind() == Mc263FeatureBlockState.FluidKind.NONE);
        }
    }

    static record OfficialQueryReceipt(int count, String sha256) {
        OfficialQueryReceipt {
            if (count < 0) throw new IllegalArgumentException("negative ruined-portal query count");
            Objects.requireNonNull(sha256, "ruined-portal query receipt SHA-256");
            if (sha256.length() != 64) {
                throw new IllegalArgumentException("malformed ruined-portal query receipt SHA-256");
            }
        }
    }

    static OfficialQueryReceipt officialQueryReceipt(
            Mc263RuinedPortalProductionExecutor.AtomicSettlement settlement) {
        Objects.requireNonNull(settlement, "ruined-portal settlement");
        List<Mc263RuinedPortalProductionExecutor.OfficialQuery> rows =
                List.copyOf(settlement.officialQueries());
        if (rows.size() != settlement.officialQueryCount()) {
            throw new IllegalArgumentException("ruined-portal official query count mismatch");
        }
        String sha256 = canonicalOfficialQuerySha256(rows);
        if (!sha256.equals(settlement.officialQuerySha256())) {
            throw new IllegalArgumentException("ruined-portal official query order/receipt mismatch");
        }
        return new OfficialQueryReceipt(rows.size(), sha256);
    }

    static Mc263FeaturesRegion.StructureBatch publicationFixture(String startKey,
            Mc263RuinedPortalProductionExecutor.AtomicSettlement settlement,
            Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput placement) {
        return structureBatch(List.of(new OwnedSettlement(
                Objects.requireNonNull(startKey, "ruined-portal start key"),
                Objects.requireNonNull(settlement, "ruined-portal settlement"),
                officialQueryReceipt(settlement))), placement);
    }

    private static Mc263FeaturesRegion.StructureBatch structureBatch(
            List<OwnedSettlement> settlements,
            Mc263CanonicalFeaturesProducerSkeleton.StructurePlacementInput placement) {
        ArrayList<Mc263FeaturesRegion.StructureBlockWrite> blocks = new ArrayList<>();
        ArrayList<Mc263FeaturesRegion.StructureLoot> loot = new ArrayList<>();
        ArrayList<Mc263FeaturesRegion.StructureBentEvidence> bent = new ArrayList<>();
        ArrayList<Mc263FeaturesRegion.StructureFluidTick> ticks = new ArrayList<>();
        long batchFluidOrder = 0L;
        for (OwnedSettlement owned : settlements) {
            if (!officialQueryReceipt(owned.settlement).equals(owned.officialQueries)) {
                throw new IllegalStateException("ruined-portal official query receipt changed after capture");
            }
            long owner = Mc263StructureOwner.owner(
                    owned.settlement.successor().structureKey(), owned.startKey);
            for (Mc263RuinedPortalProductionExecutor.BlockWrite value
                    : owned.settlement.writes()) {
                blocks.add(new Mc263FeaturesRegion.StructureBlockWrite(value.position().x(),
                        value.position().y(), value.position().z(),
                        Mc263FeatureBlockState.canonicalExactState(value.exactState()), owner));
            }
            for (Mc263RuinedPortalProductionExecutor.LootWrite value
                    : owned.settlement.loot()) {
                if (!placement.clip().contains(value.position().x(), value.position().y(),
                        value.position().z())) continue;
                loot.add(new Mc263FeaturesRegion.StructureLoot(value.position().x(),
                        value.position().y(), value.position().z(), value.table(), value.seed(),
                        placement.productionContext(value.position().x(), value.position().y(),
                                value.position().z(), value.table())));
            }
            for (Mc263RuinedPortalProductionExecutor.BlockEntityWrite value
                    : owned.settlement.blockEntities()) {
                bent.add(new Mc263FeaturesRegion.StructureBentEvidence(value.position().x(),
                        value.position().y(), value.position().z(), value.blockIdentity(),
                        value.entityType(), value.canonicalNbt()));
            }
            for (Mc263RuinedPortalProductionExecutor.FluidTickWrite value
                    : owned.settlement.officialFluidTicks()) {
                ticks.add(new Mc263FeaturesRegion.StructureFluidTick(value.position().x(),
                        value.position().y(), value.position().z(), value.key(), value.delay(),
                        value.priority(), batchFluidOrder++));
            }
            // The template processor runs before netherrack spread, but LOOT/BENT semantics
            // require the committed final cell to remain the exact transformed chest state.
            // Reassert that already-produced state at the tail of this one atomic batch.
            for (Mc263RuinedPortalProductionExecutor.LootWrite value
                    : owned.settlement.loot()) {
                Mc263RuinedPortalProductionExecutor.BlockWrite chest = owned.settlement
                        .writes().stream()
                        .filter(write -> write.position().equals(value.position())
                                && write.exactState().startsWith("minecraft:chest["))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "ruined-portal LOOT lacks transformed chest write"));
                blocks.add(new Mc263FeaturesRegion.StructureBlockWrite(value.position().x(),
                        value.position().y(), value.position().z(),
                        Mc263FeatureBlockState.canonicalExactState(chest.exactState()), owner));
            }
        }
        return new Mc263FeaturesRegion.StructureBatch(blocks, loot, List.of(), bent,
                List.of(), ticks, List.of(), List.of());
    }

    private static String canonicalOfficialQuerySha256(
            List<Mc263RuinedPortalProductionExecutor.OfficialQuery> rows) {
        StringBuilder json = new StringBuilder(rows.size() * 72 + 2).append('[');
        for (int index = 0; index < rows.size(); index++) {
            if (index != 0) json.append(',');
            Mc263RuinedPortalProductionExecutor.OfficialQuery row =
                    Objects.requireNonNull(rows.get(index), "ruined-portal official query row");
            Mc263RuinedPortalProgram.BlockPos position =
                    Objects.requireNonNull(row.position(), "ruined-portal official query position");
            json.append("{\"operation\":\"").append(row.operation())
                    .append("\",\"position\":[").append(position.x()).append(',')
                    .append(position.y()).append(',').append(position.z()).append("]}");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.append(']').toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final class OwnedSettlement {
        private final String startKey;
        private final Mc263RuinedPortalProductionExecutor.AtomicSettlement settlement;
        private final OfficialQueryReceipt officialQueries;

        private OwnedSettlement(String startKey,
                Mc263RuinedPortalProductionExecutor.AtomicSettlement settlement,
                OfficialQueryReceipt officialQueries) {
            this.startKey = startKey;
            this.settlement = settlement;
            this.officialQueries = officialQueries;
        }
    }
}
