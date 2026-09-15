package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.LootProductionContext;
import com.gameexpert.terrain.mc.structure.Mc263MineshaftCorridorPieceExecutor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Converts one fully postprocessed FEATURES center snapshot into the immutable live-chunk carrier.
 * This boundary deliberately has no POST resolver: callers must resolve every ProtoChunk
 * postprocessing mark before assembly.
 */
public final class Mc263FinalChunkAssembler {
    private Mc263FinalChunkAssembler() {}

    public static Mc263FinalChunkCodec.FinalChunk assemble(
            Mc263FeaturesRegion.CenterSnapshot snapshot) {
        return assemble(snapshot, List.of(), List.of(), List.of());
    }

    /** Source-facing overload receiving exact chest-minecart evidence in insertion order. */
    public static Mc263FinalChunkCodec.FinalChunk assemble(
            Mc263FeaturesRegion.CenterSnapshot snapshot,
            List<Mc263FinalChunkSidecars.ChestMinecart> chestMinecarts) {
        return assemble(snapshot, chestMinecarts, List.of(), List.of());
    }

    /**
     * Assembles all typed structure sidecars at the final-carrier boundary. Block entities are
     * position-ordered by the codec; structure entities retain caller encounter order. The
     * payloads are already canonical authority bytes and are never decoded or re-created here.
     * Every loot-bearing entity must match the indivisible entity/context carrier retained by the
     * region snapshot; assembly never derives authority facts from coordinates.
     */
    public static Mc263FinalChunkCodec.FinalChunk assemble(
            Mc263FeaturesRegion.CenterSnapshot snapshot,
            List<Mc263FinalChunkSidecars.ChestMinecart> chestMinecarts,
            List<Mc263FinalChunkSidecars.BlockEntity> blockEntities,
            List<Mc263FinalChunkSidecars.StructureEntity> entities) {
        return assemble(snapshot, chestMinecarts, blockEntities, entities,
                Mc263FinalChunkSidecars.currentIntegratedBuildReceipt()
                        .producerSourceSha256());
    }

    /**
     * Final assembly bound to the already-authenticated producer identity of this build.
     */
    public static Mc263FinalChunkCodec.FinalChunk assemble(
            Mc263FeaturesRegion.CenterSnapshot snapshot,
            List<Mc263FinalChunkSidecars.ChestMinecart> chestMinecarts,
            List<Mc263FinalChunkSidecars.BlockEntity> blockEntities,
            List<Mc263FinalChunkSidecars.StructureEntity> entities,
            String producerSourceSha256) {
        Objects.requireNonNull(snapshot, "center snapshot");
        Objects.requireNonNull(producerSourceSha256, "producer source identity");
        chestMinecarts = List.copyOf(chestMinecarts);
        blockEntities = List.copyOf(blockEntities);
        entities = List.copyOf(entities);

        // This check must precede every output collection/array copy. An unresolved ProtoChunk is
        // not a partially usable final chunk and must never escape through this boundary.
        for (List<Mc263FeaturesRegion.PostprocessMark> section
                : snapshot.postprocessMarksBySection()) {
            if (!section.isEmpty()) {
                throw new IllegalArgumentException(
                        "final chunk still contains unresolved POST marks");
            }
        }

        int[] positions = snapshot.borrowedExactStateOverridePositions();
        Mc263FeatureBlockState[] states = snapshot.borrowedExactStateOverrideStates();
        Map<Integer, Mc263FeatureBlockState> overrides = new LinkedHashMap<>(positions.length);
        for (int index = 0; index < positions.length; index++) {
            overrides.put(positions[index], states[index]);
        }

        List<Mc263FinalChunkSidecars.BlockTick> blockTicks = new ArrayList<>(
                snapshot.scheduledBlockTicks().size());
        for (Mc263FeaturesRegion.ScheduledBlockTick tick : snapshot.scheduledBlockTicks()) {
            blockTicks.add(new Mc263FinalChunkSidecars.BlockTick(
                    packed(tick.localX(), tick.blockY(), tick.localZ()), tick.blockId(),
                    tick.blockKey(), tick.delay(),
                    Mc263FinalChunkSidecars.TickPriority.fromValue(tick.priority()),
                    tick.subTickOrder()));
        }

        List<Mc263FinalChunkSidecars.FluidTick> fluidTicks = new ArrayList<>(
                snapshot.scheduledFluidTicks().size());
        for (Mc263FeaturesRegion.ScheduledFluidTick tick : snapshot.scheduledFluidTicks()) {
            fluidTicks.add(new Mc263FinalChunkSidecars.FluidTick(
                    packed(tick.localX(), tick.blockY(), tick.localZ()), tick.fluidKey(),
                    tick.delay(), Mc263FinalChunkSidecars.TickPriority.fromValue(tick.priority()),
                    tick.subTickOrder()));
        }

        List<Mc263FinalChunkSidecars.Loot> loot = snapshot.chestLoot().stream()
                .map(value -> new Mc263FinalChunkSidecars.Loot(
                        packed(value.localX(), value.blockY(), value.localZ()), value.facing(),
                        value.lootTable(), value.lootSeed()))
                .toList();
        List<Mc263FinalChunkSidecars.Spawner> spawners = snapshot.spawnerMobs().stream()
                .map(value -> new Mc263FinalChunkSidecars.Spawner(
                        packed(value.localX(), value.blockY(), value.localZ()),
                        value.entityType()))
                .toList();
        List<Mc263FinalChunkSidecars.Owner> owners = snapshot.ownedBlocks().stream()
                .map(value -> new Mc263FinalChunkSidecars.Owner(
                        packed(value.localX(), value.blockY(), value.localZ()), value.owner()))
                .toList();
        List<Mc263FinalChunkSidecars.Archaeology> archaeology = snapshot.archaeologyLoot().stream()
                .map(value -> new Mc263FinalChunkSidecars.Archaeology(
                        packed(value.localX(), value.blockY(), value.localZ()), value.lootTable(),
                        value.lootSeed()))
                .toList();
        List<Mc263FinalChunkSidecars.BeeNest> bees = snapshot.beehiveNests().stream()
                .filter(value -> !value.occupants().isEmpty())
                .map(value -> new Mc263FinalChunkSidecars.BeeNest(
                        packed(value.localX(), value.blockY(), value.localZ()),
                        value.occupants().stream()
                                .map(Mc263FeaturesRegion.BeehiveOccupant::ticksInHive).toList()))
                .toList();

        short[] blockIds = snapshot.blockIds();
        List<Mc263FinalChunkSidecars.BlockEntity> survivingBlockEntities =
                survivingBlockEntities(blockEntities, blockIds, overrides);
        List<Mc263FinalChunkSidecars.StructureEntity> finalEntities =
                java.util.stream.Stream.concat(
                        chestMinecarts.stream()
                                .map(Mc263FinalChunkSidecars.StructureEntity::fromChestMinecart),
                        entities.stream()).toList();

        Mc263FinalChunkSidecars sidecars = new Mc263FinalChunkSidecars(
                blockTicks, fluidTicks, loot, spawners, owners, archaeology, bees,
                survivingBlockEntities, finalEntities,
                containerLootDeclarations(snapshot, loot, finalEntities, producerSourceSha256));
        return new Mc263FinalChunkCodec.FinalChunk(snapshot.chunkX(), snapshot.chunkZ(),
                blockIds, overrides, snapshot.worldSurfaceWg(),
                snapshot.oceanFloorWg(), snapshot.motionBlocking(), sidecars);
    }

    /**
     * Applies vanilla's "a block overwrite removes the block entity" rule to the structure BENT
     * evidence carried outside the live region.
     *
     * <p>The live region already drops its own LOOT/SPWN/ARCH/BEES rows the moment a later write
     * replaces the cell ({@code Mc263FeaturesRegion.MutableChunk#acceptBlockEntityWrite}), which
     * mirrors {@code LevelChunk.setBlockState} removing the previous block entity whenever the new
     * state is not the same block-entity block. Structure BENT rows reach this boundary from the
     * placing executor's pre-POST evidence instead, so the same removal is applied here against
     * the final block at that position. A row whose block survived is kept byte-identical.</p>
     */
    private static List<Mc263FinalChunkSidecars.BlockEntity> survivingBlockEntities(
            List<Mc263FinalChunkSidecars.BlockEntity> blockEntities, short[] blockIds,
            Map<Integer, Mc263FeatureBlockState> overrides) {
        List<Mc263FinalChunkSidecars.BlockEntity> surviving =
                new ArrayList<>(blockEntities.size());
        for (Mc263FinalChunkSidecars.BlockEntity value : blockEntities) {
            int packed = value.packed();
            if (packed < 0 || packed >= blockIds.length) {
                throw new IllegalArgumentException(
                        "structure block entity outside chunk: " + packed);
            }
            Mc263FeatureBlockState state = overrides.get(packed);
            if (state == null) {
                state = Mc263FeatureBlockState.defaultForId(
                        Short.toUnsignedInt(blockIds[packed]));
            }
            if (state.blockKey().equals(value.blockIdentity())) surviving.add(value);
        }
        return List.copyOf(surviving);
    }

    private static List<Mc263FinalChunkSidecars.ContainerLootDeclaration>
            containerLootDeclarations(Mc263FeaturesRegion.CenterSnapshot snapshot,
                    List<Mc263FinalChunkSidecars.Loot> loot,
                    List<Mc263FinalChunkSidecars.StructureEntity> entities,
                    String producerSourceSha256) {
        List<Mc263FinalChunkSidecars.ContainerLootDeclaration> declarations =
                new ArrayList<>();
        LootProductionContext activeIdentity = null;
        List<Mc263FinalChunkSidecars.Loot> orderedLoot = loot.stream()
                .sorted(Comparator.comparingInt(Mc263FinalChunkSidecars.Loot::packed)).toList();
        for (int sectionOrdinal = 0; sectionOrdinal < orderedLoot.size(); sectionOrdinal++) {
            Mc263FinalChunkSidecars.Loot row = orderedLoot.get(sectionOrdinal);
            int localX = row.packed() & 15;
            int localZ = (row.packed() >>> 4) & 15;
            int blockY = (row.packed() >>> 8) + Blocks.MIN_Y;
            Mc263FeatureBlockState state = snapshot.blockState(localX, blockY, localZ);
            LootProductionContext context = snapshot.chestLoot().stream()
                    .filter(value -> value.localX() == localX && value.blockY() == blockY
                            && value.localZ() == localZ)
                    .findFirst().orElseThrow(() -> new IllegalArgumentException(
                            "final LOOT declaration lacks source context"))
                    .productionContext();
            int blockX = Math.addExact(Math.multiplyExact(snapshot.chunkX(), Blocks.CHUNK_X),
                    localX);
            int blockZ = Math.addExact(Math.multiplyExact(snapshot.chunkZ(), Blocks.CHUNK_Z),
                    localZ);
            activeIdentity = requireExactProductionContext(snapshot, context, row.table(),
                    blockX, blockY, blockZ, activeIdentity);
            declarations.add(Mc263FinalChunkSidecars.ContainerLootDeclaration.forLoot(
                    declarations.size(), sectionOrdinal, row, snapshot.chunkX(),
                    snapshot.chunkZ(), containerSize(state.blockKey()), context,
                    producerSourceSha256));
        }
        LinkedHashMap<Mc263FinalChunkSidecars.StructureEntity, LootProductionContext> entityContexts =
                new LinkedHashMap<>();
        for (Mc263FeaturesRegion.AuthenticatedStructureEntity value
                : snapshot.authenticatedStructureEntities()) {
            if (entityContexts.putIfAbsent(value.entity(), value.productionContext()) != null) {
                throw new IllegalArgumentException(
                        "duplicate authenticated structure entity context");
            }
        }
        for (int sectionOrdinal = 0; sectionOrdinal < entities.size(); sectionOrdinal++) {
            Mc263FinalChunkSidecars.StructureEntity row = entities.get(sectionOrdinal);
            if (row.lootTable().isEmpty()) continue;
            int blockX = entityBlockCoordinate(row.x(), "structure entity X");
            int blockY = entityBlockCoordinate(row.y(), "structure entity Y");
            int blockZ = entityBlockCoordinate(row.z(), "structure entity Z");
            LootProductionContext context = entityContexts.remove(row);
            if (context == null) {
                throw new IllegalArgumentException(
                        "loot-bearing structure entity lacks its authenticated context carrier");
            }
            activeIdentity = requireExactProductionContext(snapshot, context, row.lootTable(),
                    blockX, blockY, blockZ, activeIdentity);
            declarations.add(Mc263FinalChunkSidecars.ContainerLootDeclaration.forEntity(
                    declarations.size(), sectionOrdinal, row, snapshot.chunkX(),
                    snapshot.chunkZ(), entityContainerSize(row.entityKey()), context,
                    producerSourceSha256));
        }
        if (!entityContexts.isEmpty()) {
            throw new IllegalArgumentException(
                    "authenticated structure entity context was detached from final ENTS");
        }
        return List.copyOf(declarations);
    }

    private static LootProductionContext requireExactProductionContext(
            Mc263FeaturesRegion.CenterSnapshot snapshot, LootProductionContext context,
            String table, int blockX, int blockY, int blockZ,
            LootProductionContext expectedIdentity) {
        Objects.requireNonNull(context, "production loot context");
        context.requireAuthenticatedContext();
        if (context.sourceIdentity() == null
                || !context.sourceIdentity().matches("[0-9a-f]{64}")
                || context.sourceIdentity().equals("0".repeat(64))) {
            throw new IllegalArgumentException(
                    "final loot source identity must be a nonzero SHA-256 receipt");
        }
        if (!table.equals(context.tableIdentity())) {
            throw new IllegalArgumentException("final loot table identity mismatch");
        }
        if (context.originX() != blockX || context.originY() != blockY
                || context.originZ() != blockZ) {
            throw new IllegalArgumentException("final loot origin mismatch");
        }
        if (Math.floorDiv(blockX, Blocks.CHUNK_X) != snapshot.chunkX()
                || Math.floorDiv(blockZ, Blocks.CHUNK_Z) != snapshot.chunkZ()) {
            throw new IllegalArgumentException("final loot context escaped its causal chunk");
        }
        int localX = Math.floorMod(blockX, Blocks.CHUNK_X);
        int localZ = Math.floorMod(blockZ, Blocks.CHUNK_Z);
        if (!context.biomeKey().equals(snapshot.biomeKey(localX, blockY, localZ))) {
            throw new IllegalArgumentException("final loot declaration biome drift");
        }
        if (expectedIdentity != null
                && (!expectedIdentity.worldIdentity().equals(context.worldIdentity())
                || !expectedIdentity.sourceIdentity().equals(context.sourceIdentity()))) {
            throw new IllegalArgumentException(
                    "final loot context differs from the active world/source identity");
        }
        return expectedIdentity == null ? context : expectedIdentity;
    }

    private static int entityBlockCoordinate(double coordinate, String name) {
        double floor = Math.floor(coordinate);
        if (floor < Integer.MIN_VALUE || floor > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " lies outside integer world coordinates");
        }
        return (int) floor;
    }

    static int containerSize(String blockKey) {
        if (blockKey.endsWith("chest") || blockKey.endsWith("shulker_box")
                || blockKey.equals("minecraft:barrel")) return 27;
        if (blockKey.equals("minecraft:dispenser") || blockKey.equals("minecraft:dropper")) {
            return 9;
        }
        if (blockKey.equals("minecraft:decorated_pot")) return 1;
        if (blockKey.equals("minecraft:hopper")) return 5;
        throw new IllegalArgumentException(
                "randomizable-container size is not authenticated: " + blockKey);
    }

    static int entityContainerSize(String entityKey) {
        if (entityKey.equals("minecraft:chest_minecart")) return 27;
        if (entityKey.equals("minecraft:hopper_minecart")) return 5;
        throw new IllegalArgumentException(
                "loot-bearing entity container size is not authenticated: " + entityKey);
    }

    /** Converts only the exact released corridor chest-minecart semantic evidence. */
    public static Mc263FinalChunkSidecars.ChestMinecart chestMinecart(
            Mc263MineshaftCorridorPieceExecutor.ChestMinecartEffect effect) {
        Objects.requireNonNull(effect, "chest minecart effect");
        var anchor = effect.anchor(); var position = effect.position();
        if (!Mc263MineshaftCorridorPieceExecutor.CHEST_MINECART.equals(effect.entityType())
                || !Mc263MineshaftCorridorPieceExecutor.LOOT_TABLE.equals(effect.lootTable())
                || !Mc263MineshaftCorridorPieceExecutor.CHUNK_GENERATION.equals(
                        effect.spawnReason())
                || position.x() != anchor.x() + .5d || position.y() != anchor.y() + .5d
                || position.z() != anchor.z() + .5d) {
            throw new IllegalArgumentException("noncanonical mineshaft chest-minecart evidence");
        }
        return new Mc263FinalChunkSidecars.ChestMinecart(position.x(), position.y(),
                position.z(), effect.lootTable(), effect.lootSeed());
    }

    private static int packed(int localX, int blockY, int localZ) {
        return Blocks.blockIndex(localX, blockY, localZ);
    }
}
