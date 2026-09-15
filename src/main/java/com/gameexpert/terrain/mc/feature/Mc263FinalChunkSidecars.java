package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.LocatedMap;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.LocatedProductionContext;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.LootProductionContext;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.ProductionContext;
import com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority.LocatedMapTarget;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable live sidecars retained by the final 26.3 chunk carrier. */
public record Mc263FinalChunkSidecars(
        List<BlockTick> blockTicks, List<FluidTick> fluidTicks, List<Loot> loot,
        List<Spawner> spawners, List<Owner> owners, List<Archaeology> archaeology,
        List<BeeNest> bees, List<BlockEntity> blockEntities, List<StructureEntity> entities,
        List<ContainerLootDeclaration> containerLootDeclarations) {
    private static final int MAX_EXTERNAL_RESOURCE_BYTES = 64 * 1024 * 1024;
    private static final String PRODUCTION_CONTEXT_CATALOG_RESOURCE =
            "mc263/production-context-catalog-v3.json";
    private static final List<String> CONTAINER_LOOT_PRODUCER_CLASSES = List.of(
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkSidecars.class",
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkSidecars$BlockTick.class",
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkSidecars$FluidTick.class",
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkSidecars$ContainerLootDeclaration.class",
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkSidecars$ContainerLootSourceSection.class",
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkSidecars$ContainerLootSourceRow.class",
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkSidecars$Loot.class",
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkSidecars$Spawner.class",
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkSidecars$Owner.class",
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkSidecars$Archaeology.class",
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkSidecars$BeeNest.class",
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkSidecars$BlockEntity.class",
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkSidecars$SourceRowWriter.class",
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkSidecars$StructureEntity.class",
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkSidecars$ChestMinecart.class",
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkSidecars$TickPriority.class",
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkSidecars$IntegratedBuildReceipt.class",
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkAssembler.class",
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkCodec.class",
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkCodec$FinalChunk.class",
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkCodec$KeyTable.class",
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkCodec$Writer.class",
            "com/gameexpert/terrain/mc/feature/Mc263FinalChunkCodec$Reader.class",
            "com/gameexpert/terrain/mc/feature/Mc263CanonicalGenerationProduct.class",
            "com/gameexpert/terrain/mc/feature/Mc263CanonicalGenerationProduct$OriginReceipt.class",
            "com/gameexpert/terrain/mc/feature/Mc263CanonicalGenerationProduct$TargetIdentity.class",
            "com/gameexpert/terrain/mc/feature/Mc263CanonicalGenerationProduct$ProvenanceTarget.class",
            "com/gameexpert/terrain/Mc263FeaturesRegionBridge.class",
            "com/gameexpert/terrain/Mc263FeaturesRegionBridge$InputBuilderContext.class",
            "com/gameexpert/terrain/Mc263FeaturesRegionBridge$InputBuilderContext$1.class",
            "com/gameexpert/terrain/Mc263FeaturesRegionBridge$InputBuilderContext$TrackedFuture.class",
            "com/gameexpert/terrain/Mc263FeaturesRegionBridge$InputBuilderTelemetry.class",
            "com/gameexpert/terrain/Mc263FeaturesRegionBridge$RegionMemo.class",
            "com/gameexpert/terrain/Mc263FeaturesRegionBridge$RegionMemo$1.class",
            "com/gameexpert/terrain/Mc263FeaturesRegionBridge$RegionMemo$Key.class",
            "com/gameexpert/terrain/Mc263FeaturesRegionBridge$PendingRegionInput.class",
            "com/gameexpert/terrain/mc/feature/Mc263WorldGenRegionRandom.class",
            "com/gameexpert/terrain/mc/feature/Mc263WorldGenRegionRandom$State.class",
            "com/gameexpert/terrain/mc/feature/Mc263WorldGenRegionRandom$Core.class");
    private static final List<String> CONTAINER_LOOT_EXPORTER_CLASSES = List.of(
            "com/gameexpert/terrain/mc/GoldenRawCarrierExporterMain.class",
            "com/gameexpert/terrain/mc/GoldenRawCarrierExporterMain$Coordinate.class",
            "com/gameexpert/terrain/mc/GoldenRawCarrierExporterMain$Product.class",
            "com/gameexpert/terrain/mc/GoldenRawCarrierExporterMain$Producer.class",
            "com/gameexpert/terrain/mc/GoldenRawCarrierExporterMain$CanonicalProducer.class");
    /**
     * The one authoritative registry for behavior outside the direct producer/exporter closure.
     * Every semantic receipt key is bound to the complete compiled-resource closure that carries
     * that behavior. Receipt creation and verification both read these exact resources; no caller
     * supplies or blesses an external value.
     */
    private static final Map<String, List<String>> CONTAINER_LOOT_EXTERNAL_RESOURCE_CLOSURES =
            Map.ofEntries(
                    Map.entry("producer:Mc263FeatureBlockState-and-exact-state-codec",
                            classResources(
                                    "com/gameexpert/terrain/mc/feature/Mc263FeatureBlockState",
                                    "Capability", "CatalogEntry", "FluidKind", "OcclusionFace")),
                    Map.entry("producer:Mc263FeaturesRegion-container-facings-and-capabilities",
                            classResources(
                                    "com/gameexpert/terrain/mc/feature/Mc263FeaturesRegion",
                                    "ArchaeologyLoot", "BeehiveNest", "BeehiveOccupant",
                                    "BlockEntityCapabilityReceipt", "CarversChunk",
                                    "CarversChunk$Heightmaps", "CenterSnapshot", "ChestLoot",
                                    "DestinationPosition", "HeightmapKind", "LightSnapshot",
                                    "MutableChunk", "OwnedBlock", "PostprocessMark",
                                    "PreparedStructureArchaeology", "PreparedStructureBee",
                                    "PreparedStructureBlock", "PreparedStructureBlockTick",
                                    "PreparedStructureFluidTick", "PreparedStructureLoot",
                                    "PreparedStructureSpawner", "ScheduledBlockTick",
                                    "ScheduledFluidTick", "SourceChunk", "SpawnerMob",
                                    "StateOverride", "StructureArchaeology", "StructureBatch",
                                    "StructureBatchSettlement", "StructureBee",
                                    "StructureBentEvidence", "StructureBlockTick",
                                    "StructureBlockWrite", "StructureFluidTick", "StructureLoot",
                                    "StructurePostprocessMark", "StructureSpawner",
                                    "StructureTickIdentity", "TickKey")),
                    Map.entry("producer:Mc263FeaturesRegion-feature-receipts",
                            classResources(
                                    "com/gameexpert/terrain/mc/feature/Mc263FeaturesRegion",
                                    "ArchaeologyLoot", "BeehiveNest", "BeehiveOccupant",
                                    "BlockEntityCapabilityReceipt", "CarversChunk",
                                    "CarversChunk$Heightmaps", "CenterSnapshot", "ChestLoot",
                                    "DestinationPosition", "HeightmapKind", "LightSnapshot",
                                    "MutableChunk", "OwnedBlock", "PostprocessMark",
                                    "PreparedStructureArchaeology", "PreparedStructureBee",
                                    "PreparedStructureBlock", "PreparedStructureBlockTick",
                                    "PreparedStructureFluidTick", "PreparedStructureLoot",
                                    "PreparedStructureSpawner", "ScheduledBlockTick",
                                    "ScheduledFluidTick", "SourceChunk", "SpawnerMob",
                                    "StateOverride", "StructureArchaeology", "StructureBatch",
                                    "StructureBatchSettlement", "StructureBee",
                                    "StructureBentEvidence", "StructureBlockTick",
                                    "StructureBlockWrite", "StructureFluidTick", "StructureLoot",
                                    "StructurePostprocessMark", "StructureSpawner",
                                    "StructureTickIdentity", "TickKey")),
                    Map.entry(
                            "producer:Mc263CanonicalFeaturesProducerSkeleton-feature-receipts",
                            classResources(
                                    "com/gameexpert/terrain/mc/feature/"
                                            + "Mc263CanonicalFeaturesProducerSkeleton",
                                    "CanonicalStructureExecutor",
                                    "CanonicalStructureExecutorRegistry", "DispatchEvidence",
                                    "DispatchRun", "ProductionContextAuthority",
                                    "ProductionContextLocator", "SourceClip", "SourceInput",
                                    "StructureDispatchState", "StructurePlacementInput",
                                    "StructurePreflightContext", "UpstreamProduct",
                                    "WorldGenRegionRandomFork")),
                    Map.entry("producer:Blocks-and-McTerrainDataPin", List.of(
                            "com/gameexpert/terrain/Blocks.class",
                            "com/gameexpert/terrain/Blocks$SupportKind.class",
                            "com/gameexpert/terrain/Blocks$SupportMetadata.class",
                            "com/gameexpert/terrain/mc/McTerrainDataPin.class")),
                    Map.entry("producer:Mc263ContainerLootResolver-production-context",
                            mergeResources(
                                    classResources(
                                            "com/gameexpert/terrain/mc/loot/"
                                                    + "Mc263ContainerLootResolver",
                                            "AuthenticatedContext", "Candidate", "ComponentValue",
                                            "Condition", "Condition$1", "Condition$2",
                                            "Condition$3", "Condition$4", "Condition$5",
                                            "Condition$6", "Condition$7", "Condition$8",
                                            "Condition$9", "Condition$10", "Continuation",
                                            "Continuation$Kind", "Damage", "Enchantment",
                                            "EnchantmentValue", "Enchantments", "Entry", "Function",
                                            "FunctionKind", "Instrument", "IntRange", "LegacyRandom",
                                            "LocatedMap", "LootStack",
                                            "LocatedProductionContext", "LootProductionContext",
                                            "MapDecoration", "MapDecorations", "MapId",
                                            "MutableStack", "OminousBottleAmplifier", "Pool",
                                            "PotionContents", "PendingMapId",
                                            "ProductionContext", "RandomSource", "ReceiptWriter",
                                            "Resolution", "StewEffect", "StoredEnchantments",
                                            "SuspiciousStewEffect", "SuspiciousStewEffects", "Table",
                                            "TranslatableItemName", "ItemName", "XoroshiroRandom",
                                            "XoroshiroState"),
                                    classResources(
                                            "com/gameexpert/terrain/mc/loot/"
                                                    + "Mc263ProductionContextCatalog",
                                            "ContextKey", "ImmutableProvider", "ParsedContext",
                                            "PinnedIdentity", "Provider", "ReceiptWriter",
                                            "RuntimeProvider", "TargetKey"),
                                    List.of(
                                            "com/gameexpert/terrain/mc/loot/"
                                                    + "Mc263ProductionContextCatalogPins.class",
                                            PRODUCTION_CONTEXT_CATALOG_RESOURCE),
                                    classResources(
                                            "com/gameexpert/terrain/mc/loot/"
                                                    + "Mc263LocatedMapAuthority",
                                            "Binding", "BiomePreviewRenderer", "CacheKey",
                                            "CacheStats", "Destination", "Found",
                                            "LocatedMapTarget", "NotFound", "PreviewRenderer",
                                            "ReceiptWriter"))),
                    Map.entry("exporter:Mc263FeaturesRegionBridge-input-builder",
                            classResources("com/gameexpert/terrain/Mc263FeaturesRegionBridge",
                                    "InputBuilderContext", "InputBuilderContext$1",
                                    "InputBuilderContext$TrackedFuture", "InputBuilderTelemetry",
                                    "PendingRegionInput", "RegionMemo", "RegionMemo$1",
                                    "RegionMemo$Key")),
                    Map.entry("exporter:Mc263StructureCarrier-and-origin", List.of(
                            "com/gameexpert/terrain/mc/structure/Mc263StructureCarrier.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$BoundingBox.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$ChunkReferences.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$ChunkStarts.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$FragmentWriter.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$InvalidStart.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$Junction.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$NbtCursor.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$NbtMetadata.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$PayloadKey.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$Piece.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$PiecePayload.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$ProducerEdge.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$ProducerGraphPayload.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$Projection.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$RawStartPayload.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$ReceiptCursor.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$ReferenceSet.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$Registry.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$StartBody.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$StartEntry.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$StartNbtMetadata.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$StructureDefinition.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$Successor.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$SuccessorCapability.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$SuccessorReceipt.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$SuccessorTransaction.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$TerrainAdjustment.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrier$ValidStart.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrierOrigin.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrierOrigin$Assembly.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrierOrigin$RegionMemo.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrierOrigin$RegionMemo$1.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrierOrigin$RegionMemo$CachedStarts.class",
                            "com/gameexpert/terrain/mc/structure/"
                                    + "Mc263StructureCarrierOrigin$WorldContext.class")),
                    Map.entry(
                            "exporter:Mc263BaseHeightSampler-and-postprocess-activation",
                            List.of(
                                    "com/gameexpert/terrain/Mc263BaseHeightSampler.class",
                                    "com/gameexpert/terrain/"
                                            + "CanonicalPostprocessActivationContext.class",
                                    "com/gameexpert/terrain/"
                                            + "CanonicalPostprocessActivationContext$1.class")));
    /** Authenticated identity of the exact compiled producer/source boundary. */
    public static final String CONTAINER_LOOT_PRODUCER_SOURCE_SHA256 = HexFormat.of().formatHex(
            sha256(containerLootProducerReceiptPreimage()));

    public static final Mc263FinalChunkSidecars EMPTY = new Mc263FinalChunkSidecars(
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of());

    /** Existing non-declaration construction remains available for leaf fixtures only. */
    public Mc263FinalChunkSidecars(List<BlockTick> blockTicks, List<FluidTick> fluidTicks,
            List<Loot> loot, List<Spawner> spawners, List<Owner> owners,
            List<Archaeology> archaeology, List<BeeNest> bees,
            List<BlockEntity> blockEntities, List<StructureEntity> entities) {
        this(blockTicks, fluidTicks, loot, spawners, owners, archaeology, bees, blockEntities,
                entities, List.of());
    }

    /** Convenience constructor for feature producers without entity sidecars. */
    public Mc263FinalChunkSidecars(List<BlockTick> blockTicks, List<FluidTick> fluidTicks,
            List<Loot> loot, List<Spawner> spawners, List<Owner> owners,
            List<Archaeology> archaeology, List<BeeNest> bees) {
        this(blockTicks, fluidTicks, loot, spawners, owners, archaeology, bees, List.of(),
                List.of(), List.of());
    }

    /** Source-facing constructor retained for the exact mineshaft chest-minecart effect. */
    public Mc263FinalChunkSidecars(List<BlockTick> blockTicks, List<FluidTick> fluidTicks,
            List<Loot> loot, List<Spawner> spawners, List<Owner> owners,
            List<Archaeology> archaeology, List<BeeNest> bees,
            List<ChestMinecart> chestMinecarts) {
        this(blockTicks, fluidTicks, loot, spawners, owners, archaeology, bees, List.of(),
                chestMinecarts.stream().map(StructureEntity::fromChestMinecart).toList(),
                List.of());
    }

    public Mc263FinalChunkSidecars {
        blockTicks = List.copyOf(blockTicks); fluidTicks = List.copyOf(fluidTicks);
        loot = List.copyOf(loot); spawners = List.copyOf(spawners);
        owners = List.copyOf(owners); archaeology = List.copyOf(archaeology);
        bees = List.copyOf(bees); blockEntities = List.copyOf(blockEntities);
        entities = List.copyOf(entities);
        containerLootDeclarations = List.copyOf(containerLootDeclarations);
    }

    /** Compatibility-free semantic view for callers that only handle chest minecarts. */
    public List<ChestMinecart> chestMinecarts() {
        return entities.stream().filter(StructureEntity::isChunkGenerationChestMinecart)
                .map(StructureEntity::toChestMinecart).toList();
    }

    /** Official {@code TickPriority} wire values, ordered from earliest to latest. */
    public enum TickPriority {
        EXTREMELY_HIGH(-3), VERY_HIGH(-2), HIGH(-1), NORMAL(0), LOW(1), VERY_LOW(2),
        EXTREMELY_LOW(3);

        private final int value;

        TickPriority(int value) { this.value = value; }
        public int value() { return value; }
        public static TickPriority fromValue(int value) {
            for (TickPriority priority : values()) if (priority.value == value) return priority;
            throw new IllegalArgumentException("tick priority outside -3..3: " + value);
        }
    }

    public record BlockTick(int packed, int blockId, String key, int delay,
                            TickPriority priority, long subTickOrder) {
        public BlockTick {
            position(packed); unsigned16(blockId, "block tick ID"); validateKey(key);
            nonnegative(delay, "block tick delay"); Objects.requireNonNull(priority);
        }
    }
    public record FluidTick(int packed, String key, int delay, TickPriority priority,
                            long subTickOrder) {
        public FluidTick {
            position(packed); validateKey(key); nonnegative(delay, "fluid tick delay");
            Objects.requireNonNull(priority);
        }
    }
    /**
     * Randomizable-container loot assignment. {@code facing} is the container state's own
     * {@code facing} property over vanilla's full six-direction set (barrel, dispenser and hopper
     * are legally vertical); see {@link Mc263FeaturesRegion#CONTAINER_FACINGS}.
     */
    /** Closed source-row family accepted by the canonical declaration validation/rebind API. */
    public sealed interface ContainerLootSourceRow permits Loot, StructureEntity {}

    public record Loot(int packed, String facing, String table, long seed)
            implements ContainerLootSourceRow {
        public Loot { position(packed); if (!Mc263FeaturesRegion.CONTAINER_FACINGS.contains(facing)) throw new IllegalArgumentException("invalid facing: " + facing); validateKey(table); }
    }

    /** The one combined declaration order shared by final LOOT and loot-bearing ENTS rows. */
    public enum ContainerLootSourceSection { LOOT, ENTS }

    /**
     * Complete source facts required to execute one unopened final-carrier loot declaration.
     * The declaration binding covers the referenced final row, all context values and producer
     * identity, so a declaration cannot be moved between carriers or section ordinals.
     */
    public record ContainerLootDeclaration(
            int ordinal, ContainerLootSourceSection sourceSection, int sourceSectionOrdinal,
            int containerSize, LootProductionContext productionContext,
            String producerSourceSha256, String sourceDeclarationSha256) {
        public ContainerLootDeclaration {
            if (ordinal < 0) throw new IllegalArgumentException("negative loot declaration ordinal");
            Objects.requireNonNull(sourceSection, "loot declaration source section");
            if (sourceSectionOrdinal < 0) {
                throw new IllegalArgumentException("negative loot source section ordinal");
            }
            if (containerSize < 1 || containerSize > 256) {
                throw new IllegalArgumentException("container size outside 1..256");
            }
            productionContext = canonicalContext(productionContext);
            requireSha256(producerSourceSha256, "producer source identity");
            requireSha256(sourceDeclarationSha256, "loot source declaration identity");
        }

        static ContainerLootDeclaration forLoot(int ordinal, int sectionOrdinal, Loot row,
                int chunkX, int chunkZ, int containerSize, LootProductionContext context) {
            return forLoot(ordinal, sectionOrdinal, row, chunkX, chunkZ, containerSize, context,
                    currentIntegratedBuildReceipt().producerSourceSha256());
        }

        static ContainerLootDeclaration forLoot(int ordinal, int sectionOrdinal, Loot row,
                int chunkX, int chunkZ, int containerSize, LootProductionContext context,
                String producerSourceSha256) {
            String binding = sourceDeclarationBinding(ordinal, sectionOrdinal, chunkX, chunkZ,
                    row, containerSize, context, producerSourceSha256);
            return new ContainerLootDeclaration(ordinal, ContainerLootSourceSection.LOOT,
                    sectionOrdinal, containerSize, context,
                    producerSourceSha256, binding);
        }

        static ContainerLootDeclaration forEntity(int ordinal, int sectionOrdinal,
                StructureEntity row, int chunkX, int chunkZ, int containerSize,
                LootProductionContext context) {
            return forEntity(ordinal, sectionOrdinal, row, chunkX, chunkZ, containerSize,
                    context, currentIntegratedBuildReceipt().producerSourceSha256());
        }

        static ContainerLootDeclaration forEntity(int ordinal, int sectionOrdinal,
                StructureEntity row, int chunkX, int chunkZ, int containerSize,
                LootProductionContext context, String producerSourceSha256) {
            String binding = sourceDeclarationBinding(ordinal, sectionOrdinal, chunkX, chunkZ,
                    row, containerSize, context, producerSourceSha256);
            return new ContainerLootDeclaration(ordinal, ContainerLootSourceSection.ENTS,
                    sectionOrdinal, containerSize, context,
                    producerSourceSha256, binding);
        }
    }

    /**
     * Validates an existing declaration against its exact source row and, when requested,
     * rebinds only its combined/source-section ordinals.  Consumers that filter or reorder final
     * carrier rows use this one authority instead of reproducing the source-row digest grammar.
     */
    public static ContainerLootDeclaration validateAndRebindContainerLootDeclaration(
            ContainerLootDeclaration declaration, int ordinal, int sourceSectionOrdinal,
            int chunkX, int chunkZ, ContainerLootSourceRow sourceRow) {
        return validateAndRebindContainerLootDeclaration(declaration, ordinal,
                sourceSectionOrdinal, chunkX, chunkZ, sourceRow,
                currentIntegratedBuildReceipt());
    }

    /** Validates and rebinds against an authenticated integrated build receipt. */
    public static ContainerLootDeclaration validateAndRebindContainerLootDeclaration(
            ContainerLootDeclaration declaration, int ordinal, int sourceSectionOrdinal,
            int chunkX, int chunkZ, ContainerLootSourceRow sourceRow,
            IntegratedBuildReceipt integratedBuildReceipt) {
        Objects.requireNonNull(declaration, "container loot declaration");
        Objects.requireNonNull(sourceRow, "container loot source row");
        Objects.requireNonNull(integratedBuildReceipt, "integrated build receipt")
                .verifyAgainstCurrentBuild();
        canonicalContext(declaration.productionContext());
        if (!integratedBuildReceipt.producerSourceSha256().equals(
                declaration.producerSourceSha256())) {
            throw new IllegalArgumentException("container loot producer source identity mismatch");
        }
        String existingBinding;
        String reboundBinding;
        if (declaration.sourceSection() == ContainerLootSourceSection.LOOT
                && sourceRow instanceof Loot row) {
            existingBinding = sourceDeclarationBinding(declaration.ordinal(),
                    declaration.sourceSectionOrdinal(), chunkX, chunkZ, row,
                    declaration.containerSize(), declaration.productionContext(),
                    declaration.producerSourceSha256());
            reboundBinding = sourceDeclarationBinding(ordinal, sourceSectionOrdinal, chunkX,
                    chunkZ, row, declaration.containerSize(), declaration.productionContext(),
                    declaration.producerSourceSha256());
        } else if (declaration.sourceSection() == ContainerLootSourceSection.ENTS
                && sourceRow instanceof StructureEntity row) {
            existingBinding = sourceDeclarationBinding(declaration.ordinal(),
                    declaration.sourceSectionOrdinal(), chunkX, chunkZ, row,
                    declaration.containerSize(), declaration.productionContext(),
                    declaration.producerSourceSha256());
            reboundBinding = sourceDeclarationBinding(ordinal, sourceSectionOrdinal, chunkX,
                    chunkZ, row, declaration.containerSize(), declaration.productionContext(),
                    declaration.producerSourceSha256());
        } else {
            throw new IllegalArgumentException("container loot declaration/source row mismatch");
        }
        if (!existingBinding.equals(declaration.sourceDeclarationSha256())) {
            throw new IllegalArgumentException("container loot source-row binding mismatch");
        }
        if (declaration.ordinal() == ordinal
                && declaration.sourceSectionOrdinal() == sourceSectionOrdinal) {
            return declaration;
        }
        return new ContainerLootDeclaration(ordinal, declaration.sourceSection(),
                sourceSectionOrdinal, declaration.containerSize(),
                declaration.productionContext(), declaration.producerSourceSha256(),
                reboundBinding);
    }
    public record Spawner(int packed, String entityType) {
        public Spawner { position(packed); validateKey(entityType); }
    }
    public record Owner(int packed, long owner) { public Owner { position(packed); } }
    public record Archaeology(int packed, String table, long seed) {
        public Archaeology { position(packed); validateKey(table); }
    }
    public record BeeNest(int packed, List<Integer> ticksInHive) {
        public BeeNest {
            position(packed); ticksInHive = List.copyOf(ticksInHive);
            if (ticksInHive.size() > 0xffff) {
                throw new IllegalArgumentException("bee occupant count exceeds unsigned-16: "
                        + ticksInHive.size());
            }
            for (int ticks : ticksInHive) if (ticks < 0 || ticks > 598) throw new IllegalArgumentException("bee ticks outside 0..598: " + ticks);
        }
    }
    /** Exact block-entity payload retained by the final carrier. */
    public static final class BlockEntity {
        private final int packed;
        private final String blockIdentity;
        private final String entityType;
        private final byte[] canonicalNbt;

        public BlockEntity(int packed, String blockIdentity, String entityType,
                byte[] canonicalNbt) {
            position(packed);
            validateKey(blockIdentity); validateKey(entityType);
            this.packed = packed;
            this.blockIdentity = blockIdentity;
            this.entityType = entityType;
            this.canonicalNbt = Mc263FinalChunkSidecars.canonicalNbt(canonicalNbt);
        }

        public int packed() { return packed; }
        public String blockIdentity() { return blockIdentity; }
        public String entityType() { return entityType; }
        public byte[] canonicalNbt() { return canonicalNbt.clone(); }

        @Override public boolean equals(Object other) {
            return other instanceof BlockEntity value && packed == value.packed
                    && blockIdentity.equals(value.blockIdentity)
                    && entityType.equals(value.entityType)
                    && Arrays.equals(canonicalNbt, value.canonicalNbt);
        }
        @Override public int hashCode() {
            int result = Objects.hash(packed, blockIdentity, entityType);
            return 31 * result + Arrays.hashCode(canonicalNbt);
        }
    }

    /** Type-tagged structure entity with encounter-order payload semantics. */
    public static final class StructureEntity implements ContainerLootSourceRow {
        private final String entityKey;
        private final String spawnReason;
        private final double x, y, z;
        private final float yaw, pitch;
        private final double velocityX, velocityY, velocityZ;
        private final String lootTable;
        private final long lootSeed;
        private final byte[] canonicalPayload;

        public StructureEntity(String entityKey, String spawnReason, double x, double y, double z,
                float yaw, float pitch, double velocityX, double velocityY, double velocityZ,
                byte[] canonicalPayload) {
            this(entityKey, spawnReason, x, y, z, yaw, pitch, velocityX, velocityY, velocityZ,
                    "", 0L, canonicalPayload);
        }

        public StructureEntity(String entityKey, String spawnReason, double x, double y, double z,
                float yaw, float pitch, double velocityX, double velocityY, double velocityZ,
                String lootTable, long lootSeed, byte[] canonicalPayload) {
            validateKey(entityKey); validateKey(spawnReason);
            finite(x, "entity X"); finite(y, "entity Y"); finite(z, "entity Z");
            finite(yaw, "entity yaw"); finite(pitch, "entity pitch");
            finite(velocityX, "entity velocity X"); finite(velocityY, "entity velocity Y");
            finite(velocityZ, "entity velocity Z");
            if (!lootTable.isEmpty()) validateKey(lootTable);
            this.entityKey = entityKey; this.spawnReason = spawnReason;
            this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
            this.velocityX = velocityX; this.velocityY = velocityY; this.velocityZ = velocityZ;
            this.lootTable = Objects.requireNonNull(lootTable, "loot table");
            this.lootSeed = lootSeed;
            this.canonicalPayload = payload(canonicalPayload);
        }

        static StructureEntity fromChestMinecart(ChestMinecart value) {
            return new StructureEntity("minecraft:chest_minecart", "minecraft:chunk_generation",
                    value.x(), value.y(), value.z(), 0f, 0f, 0d, 0d, 0d,
                    value.lootTable(), value.lootSeed(), "CME263E1".getBytes(StandardCharsets.US_ASCII));
        }

        private boolean isChunkGenerationChestMinecart() {
            return entityKey.equals("minecraft:chest_minecart")
                    && spawnReason.equals("minecraft:chunk_generation")
                    && !lootTable.isEmpty();
        }

        private ChestMinecart toChestMinecart() {
            return new ChestMinecart(x, y, z, lootTable, lootSeed);
        }

        public String entityKey() { return entityKey; }
        public String spawnReason() { return spawnReason; }
        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        public float yaw() { return yaw; }
        public float pitch() { return pitch; }
        public double velocityX() { return velocityX; }
        public double velocityY() { return velocityY; }
        public double velocityZ() { return velocityZ; }
        public String lootTable() { return lootTable; }
        public long lootSeed() { return lootSeed; }
        public byte[] canonicalPayload() { return canonicalPayload.clone(); }

        @Override public boolean equals(Object other) {
            return other instanceof StructureEntity value
                    && entityKey.equals(value.entityKey) && spawnReason.equals(value.spawnReason)
                    && Double.doubleToRawLongBits(x) == Double.doubleToRawLongBits(value.x)
                    && Double.doubleToRawLongBits(y) == Double.doubleToRawLongBits(value.y)
                    && Double.doubleToRawLongBits(z) == Double.doubleToRawLongBits(value.z)
                    && Float.floatToRawIntBits(yaw) == Float.floatToRawIntBits(value.yaw)
                    && Float.floatToRawIntBits(pitch) == Float.floatToRawIntBits(value.pitch)
                    && Double.doubleToRawLongBits(velocityX) == Double.doubleToRawLongBits(value.velocityX)
                    && Double.doubleToRawLongBits(velocityY) == Double.doubleToRawLongBits(value.velocityY)
                    && Double.doubleToRawLongBits(velocityZ) == Double.doubleToRawLongBits(value.velocityZ)
                    && lootTable.equals(value.lootTable) && lootSeed == value.lootSeed
                    && Arrays.equals(canonicalPayload, value.canonicalPayload);
        }
        @Override public int hashCode() {
            int result = Objects.hash(entityKey, spawnReason, x, y, z, yaw, pitch,
                    velocityX, velocityY, velocityZ, lootTable, lootSeed);
            return 31 * result + Arrays.hashCode(canonicalPayload);
        }
    }

    public record ChestMinecart(double x, double y, double z, String lootTable, long lootSeed) {
        public ChestMinecart {
            halfCell(x, "chest minecart X"); halfCell(y, "chest minecart Y");
            halfCell(z, "chest minecart Z"); validateKey(lootTable);
            int blockY = (int) Math.floor(y);
            if (blockY < com.gameexpert.terrain.Blocks.MIN_Y
                    || blockY > com.gameexpert.terrain.Blocks.MAX_Y) {
                throw new IllegalArgumentException("chest minecart Y outside generation range: " + y);
            }
        }
    }

    private static void position(int packed) {
        if (packed < 0 || packed >= com.gameexpert.terrain.Blocks.CHUNK_BLOCKS)
            throw new IllegalArgumentException("packed position outside chunk: " + packed);
    }
    private static void unsigned16(int value, String name) { if (value < 0 || value > 0xffff) throw new IllegalArgumentException(name + " outside u16: " + value); }
    private static void nonnegative(int value, String name) { if (value < 0) throw new IllegalArgumentException(name + " is negative"); }
    private static void validateKey(String value) { Mc263FeatureBlockState.requireCanonicalResourceKey(Objects.requireNonNull(value), "sidecar key"); }
    private static byte[] canonicalNbt(byte[] value) {
        byte[] bytes = payload(value);
        if (bytes.length < 3 || Byte.toUnsignedInt(bytes[0]) != 10) {
            throw new IllegalArgumentException("block entity payload must be a compound NBT value");
        }
        return bytes;
    }
    private static byte[] payload(byte[] value) {
        Objects.requireNonNull(value, "canonical payload");
        if (value.length > 1_048_576) throw new IllegalArgumentException("canonical payload too large");
        return value.clone();
    }
    private static void finite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " is not finite");
    }
    private static void finite(float value, String name) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException(name + " is not finite");
    }
    private static void halfCell(double value, String name) {
        if (!Double.isFinite(value) || value != Math.floor(value) + .5d) {
            throw new IllegalArgumentException(name + " must be a finite block-center coordinate: " + value);
        }
    }

    static String sourceDeclarationBinding(int ordinal, int sectionOrdinal, int chunkX, int chunkZ,
            Loot row, int containerSize, LootProductionContext context,
            String producerSourceSha256) {
        Objects.requireNonNull(row, "LOOT source row");
        return sourceDeclarationBinding(ordinal, ContainerLootSourceSection.LOOT, sectionOrdinal,
                chunkX, chunkZ, containerSize, context, producerSourceSha256, out -> {
                    out.writeInt(row.packed());
                    writeString(out, row.facing());
                    writeString(out, row.table());
                    out.writeLong(row.seed());
                });
    }

    static String sourceDeclarationBinding(int ordinal, int sectionOrdinal, int chunkX, int chunkZ,
            StructureEntity row, int containerSize, LootProductionContext context,
            String producerSourceSha256) {
        Objects.requireNonNull(row, "ENTS source row");
        return sourceDeclarationBinding(ordinal, ContainerLootSourceSection.ENTS, sectionOrdinal,
                chunkX, chunkZ, containerSize, context, producerSourceSha256, out -> {
                    writeString(out, row.entityKey());
                    writeString(out, row.spawnReason());
                    out.writeLong(Double.doubleToRawLongBits(row.x()));
                    out.writeLong(Double.doubleToRawLongBits(row.y()));
                    out.writeLong(Double.doubleToRawLongBits(row.z()));
                    out.writeInt(Float.floatToRawIntBits(row.yaw()));
                    out.writeInt(Float.floatToRawIntBits(row.pitch()));
                    out.writeLong(Double.doubleToRawLongBits(row.velocityX()));
                    out.writeLong(Double.doubleToRawLongBits(row.velocityY()));
                    out.writeLong(Double.doubleToRawLongBits(row.velocityZ()));
                    writeString(out, row.lootTable());
                    out.writeLong(row.lootSeed());
                    byte[] entityPayload = row.canonicalPayload();
                    out.writeInt(entityPayload.length);
                    out.write(entityPayload);
                });
    }

    private static String sourceDeclarationBinding(int ordinal,
            ContainerLootSourceSection section, int sectionOrdinal, int chunkX, int chunkZ,
            int containerSize, LootProductionContext context, String producerSourceSha256,
            SourceRowWriter rowWriter) {
        if (ordinal < 0 || sectionOrdinal < 0) {
            throw new IllegalArgumentException("negative loot declaration ordinal");
        }
        context = canonicalContext(context);
        requireSha256(producerSourceSha256, "producer source identity");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.write("MC263-CONTAINER-LOOT-SOURCE-ROW-V3\0".getBytes(StandardCharsets.US_ASCII));
            out.writeByte(section.ordinal());
            out.writeInt(ordinal);
            out.writeInt(sectionOrdinal);
            out.writeInt(chunkX); out.writeInt(chunkZ);
            rowWriter.write(out);
            out.writeShort(containerSize);
            writeProductionContext(out, context);
            out.write(HexFormat.of().parseHex(producerSourceSha256));
            out.flush();
            return HexFormat.of().formatHex(sha256(bytes.toByteArray()));
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Producer identity over the exact compiled classes that implement the source/wire boundary. */
    static byte[] containerLootProducerReceiptPreimage() {
        return classReceiptPreimage("MC263-CONTAINER-LOOT-PRODUCER-CLASSES-V1\0", null,
                CONTAINER_LOOT_PRODUCER_CLASSES);
    }

    public static String containerLootExporterSourceSha256() {
        return currentIntegratedBuildReceipt().exporterSourceSha256();
    }

    private static byte[] classReceiptPreimage(String domain, byte[] predecessor,
            List<String> classResources) {
        return classReceiptPreimage(domain, predecessor, classResources,
                Mc263FinalChunkSidecars.class.getClassLoader(), Mc263FinalChunkSidecars.class);
    }

    /**
     * Test-only seam for proving that a classloader cannot hide duplicate or foreign resources.
     * Production receipt verification always uses the defining loader of this class.
     */
    public static byte[] classReceiptPreimageForTesting(ClassLoader loader, Class<?> anchor,
            List<String> classResources) {
        return classReceiptPreimage("MC263-CLASS-RECEIPT-RESOURCE-TEST-V1\0", null,
                classResources, loader, anchor);
    }

    private static byte[] classReceiptPreimage(String domain, byte[] predecessor,
            List<String> classResources, ClassLoader loader, Class<?> anchor) {
        Objects.requireNonNull(classResources, "class receipt resources");
        if (classResources.isEmpty() || classResources.size() > 0xffff) {
            throw new IllegalStateException("class receipt resource closure has invalid size");
        }
        Set<String> uniqueResources = new LinkedHashSet<>();
        for (String resource : classResources) {
            if (resource == null || !uniqueResources.add(resource)) {
                throw new IllegalStateException("class receipt resource closure is duplicated");
            }
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.write(domain.getBytes(StandardCharsets.US_ASCII));
            if (predecessor == null) out.writeByte(0);
            else { out.writeByte(1); out.write(predecessor); }
            out.writeShort(classResources.size());
            for (String resource : classResources) {
                writeString(out, resource);
                byte[] resourceBytes = readExactClosureResource(loader, anchor, resource);
                out.writeInt(resourceBytes.length);
                out.write(resourceBytes);
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("cannot authenticate producer class receipt", failure);
        }
    }

    private static byte[] readExactClassResource(ClassLoader loader, Class<?> anchor,
            String resource) {
        Objects.requireNonNull(loader, "class receipt loader");
        Objects.requireNonNull(anchor, "class receipt anchor");
        if (anchor.getClassLoader() != loader) {
            throw new IllegalStateException("class receipt anchor/classloader mismatch");
        }
        if (resource == null || !resource.endsWith(".class") || resource.startsWith("/")) {
            throw new IllegalStateException("invalid class receipt resource: " + resource);
        }
        String binaryName = resource.substring(0, resource.length() - ".class".length())
                .replace('/', '.');
        Class<?> owner;
        try {
            owner = Class.forName(binaryName, false, loader);
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException("missing authenticated class: " + binaryName,
                    failure);
        }
        if (owner.getClassLoader() != loader) {
            throw new IllegalStateException("class receipt classloader mismatch: " + resource);
        }
        CodeSource codeSource = owner.getProtectionDomain().getCodeSource();
        URL location = codeSource == null ? null : codeSource.getLocation();
        List<URL> matches;
        try {
            matches = Collections.list(loader.getResources(resource));
        } catch (IOException failure) {
            throw new IllegalStateException("cannot enumerate class receipt resource: " + resource,
                    failure);
        }
        if (matches.size() != 1) {
            throw new IllegalStateException("class receipt resource is not unique: " + resource
                    + " (matches=" + matches.size() + ")");
        }
        if (location == null) {
            throw new IllegalStateException("class receipt code-source is absent: " + resource);
        }
        URL resolved = matches.getFirst();
        if (!resourceBelongsToCodeSource(resolved, resource, location)) {
            throw new IllegalStateException("class receipt resource origin mismatch: " + resource);
        }
        try (InputStream input = resolved.openStream()) {
            byte[] classBytes = input.readAllBytes();
            if (classBytes.length == 0) {
                throw new IllegalStateException("empty authenticated class resource: " + resource);
            }
            return classBytes;
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read authenticated class resource: " + resource,
                    failure);
        }
    }

    private static byte[] readExactClosureResource(ClassLoader loader, Class<?> anchor,
            String resource) {
        if (resource != null && resource.endsWith(".class")) {
            return readExactClassResource(loader, anchor, resource);
        }
        if (!PRODUCTION_CONTEXT_CATALOG_RESOURCE.equals(resource)) {
            throw new IllegalStateException("invalid external receipt resource: " + resource);
        }
        Objects.requireNonNull(loader, "external receipt loader");
        Objects.requireNonNull(anchor, "external receipt anchor");
        if (anchor.getClassLoader() != loader) {
            throw new IllegalStateException("external receipt anchor/classloader mismatch");
        }
        CodeSource codeSource = anchor.getProtectionDomain().getCodeSource();
        URL location = codeSource == null ? null : codeSource.getLocation();
        List<URL> matches;
        try {
            matches = Collections.list(loader.getResources(resource));
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "cannot enumerate external receipt resource: " + resource, failure);
        }
        if (matches.size() > 1 || location == null) {
            throw new IllegalStateException("external receipt resource is not unique: " + resource
                    + " (matches=" + matches.size() + ")");
        }
        // 클래스로더 열거는 exploded 클래스패스가 다시 쓰이는 순간(gradle processResources 등)에
        // 0건을 돌려줄 수 있다. 그때는 코드 소스에서 후보 URL을 직접 만들어 쓰되, 중복(2건 이상)은
        // 위에서 그대로 거부하고 후보 URL도 아래 출처 검증을 반드시 통과해야 한다.
        URL resolved = matches.isEmpty()
                ? codeSourceResourceUrl(location, resource) : matches.getFirst();
        if (resolved == null) {
            throw new IllegalStateException("external receipt resource is not unique: " + resource
                    + " (matches=0)");
        }
        if (!resourceBelongsToCodeSource(resolved, resource, location)) {
            throw new IllegalStateException("external receipt resource origin mismatch: "
                    + resource);
        }
        try (InputStream input = resolved.openStream()) {
            byte[] resourceBytes = input.readNBytes(MAX_EXTERNAL_RESOURCE_BYTES + 1);
            if (resourceBytes.length == 0 || resourceBytes.length > MAX_EXTERNAL_RESOURCE_BYTES) {
                throw new IllegalStateException("external receipt resource size is invalid: "
                        + resource);
            }
            return resourceBytes;
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read external receipt resource: " + resource,
                    failure);
        }
    }

    /**
     * 코드 소스에서 영수증 자원의 후보 URL을 결정적으로 만든다. 실제로 존재하는 파일/엔트리만
     * 돌려주며, 허용 여부는 언제나 {@link #resourceBelongsToCodeSource}가 최종 판정한다.
     * exploded(gradle) 배치와 boot jar 배치를 모두 다룬다.
     */
    private static URL codeSourceResourceUrl(URL codeSource, String name) {
        try {
            if (codeSource == null || name == null || name.isEmpty() || name.startsWith("/")
                    || !"file".equalsIgnoreCase(codeSource.getProtocol())) {
                return null;
            }
            Path base = Path.of(codeSource.toURI()).toAbsolutePath().normalize();
            if (Files.isDirectory(base)) {
                Path candidate = PRODUCTION_CONTEXT_CATALOG_RESOURCE.equals(name)
                        ? explodedSiblingResource(base, name)
                        : base.resolve(name).normalize();
                if (candidate == null || !Files.isRegularFile(candidate)) return null;
                return candidate.toUri().toURL();
            }
            if (!Files.isRegularFile(base)) return null;
            URL jarUrl = URI.create("jar:" + base.toUri() + "!/" + name).toURL();
            try (InputStream probe = jarUrl.openStream()) {
                if (probe == null) return null;
            }
            return jarUrl;
        } catch (Exception failure) {
            return null;
        }
    }

    /** gradle은 클래스와 리소스를 형제 디렉터리로 내보낸다: build/classes/java/main 옆의 리소스 경로. */
    private static Path explodedSiblingResource(Path classRoot, String name) {
        Path javaRoot = classRoot.getParent();
        Path classesRoot = javaRoot == null ? null : javaRoot.getParent();
        Path buildRoot = classesRoot == null ? null : classesRoot.getParent();
        if (buildRoot == null) return null;
        return buildRoot.resolve("resources").resolve("main").resolve(name).normalize();
    }

    private static boolean resourceBelongsToCodeSource(URL resource, String name,
            URL codeSource) {
        try {
            if ("file".equalsIgnoreCase(resource.getProtocol())
                    && "file".equalsIgnoreCase(codeSource.getProtocol())) {
                Path base = Path.of(codeSource.toURI()).toAbsolutePath().normalize();
                Path actual = Path.of(resource.toURI()).toAbsolutePath().normalize();
                if (PRODUCTION_CONTEXT_CATALOG_RESOURCE.equals(name)) {
                    return isAuthenticatedExplodedCatalog(base, actual);
                }
                return actual.equals(base.resolve(name).normalize());
            }
            if ("jar".equalsIgnoreCase(resource.getProtocol())) {
                if (!(resource.openConnection() instanceof JarURLConnection jar)) return false;
                return sameUrl(jar.getJarFileURL(), codeSource)
                        && name.equals(jar.getEntryName());
            }
            return false;
        } catch (Exception failure) {
            return false;
        }
    }

    /**
     * Gradle publishes classes and resources to sibling directories. This is deliberately not a
     * general classpath rule: only the catalog may use this split layout, and both paths must be
     * the canonical, non-symlinked children of one build root.
     */
    private static boolean isAuthenticatedExplodedCatalog(Path classRoot, Path resource) {
        try {
            Path normalizedClassRoot = classRoot.toAbsolutePath().normalize();
            Path normalizedResource = resource.toAbsolutePath().normalize();
            Path realClassRoot = normalizedClassRoot.toRealPath();
            Path realResource = normalizedResource.toRealPath();
            if (!normalizedClassRoot.equals(realClassRoot) || !normalizedResource.equals(realResource)
                    || !Files.isDirectory(realClassRoot) || !Files.isRegularFile(realResource)) {
                return false;
            }
            Path javaRoot = realClassRoot.getParent();
            Path classesRoot = javaRoot == null ? null : javaRoot.getParent();
            Path buildRoot = classesRoot == null ? null : classesRoot.getParent();
            if (javaRoot == null || classesRoot == null || buildRoot == null
                    || !"main".equals(realClassRoot.getFileName().toString())
                    || !"java".equals(javaRoot.getFileName().toString())
                    || !"classes".equals(classesRoot.getFileName().toString())
                    || !"build".equals(buildRoot.getFileName().toString())) {
                return false;
            }
            Path expected = buildRoot.resolve("resources").resolve("main")
                    .resolve(PRODUCTION_CONTEXT_CATALOG_RESOURCE).toRealPath();
            return normalizedResource.equals(expected) && realResource.equals(expected);
        } catch (Exception failure) {
            return false;
        }
    }

    private static boolean sameUrl(URL first, URL second) {
        try {
            return first.toURI().normalize().equals(second.toURI().normalize());
        } catch (Exception failure) {
            return first.toExternalForm().equals(second.toExternalForm());
        }
    }

    private static final Object PRODUCTION_RECEIPT_LOCK = new Object();
    /**
     * 프로덕션 영수증은 이미 로드된 이 JVM의 바이트에 대한 값이라 실행 중에 변하지 않는다.
     * 청크 승인마다 4.5MB 카탈로그와 수백 개 클래스 파일을 다시 해시하면 틱 예산을 잡아먹고,
     * 빌드 산출물이 다시 쓰이는 순간마다 실패에 노출되므로 JVM당 한 번만 계산한다.
     */
    private static volatile IntegratedBuildReceipt productionReceipt;

    /** Creates the integrated receipt from the exact resources loaded by the current build. */
    public static IntegratedBuildReceipt currentIntegratedBuildReceipt() {
        IntegratedBuildReceipt cached = productionReceipt;
        if (cached != null) return cached;
        synchronized (PRODUCTION_RECEIPT_LOCK) {
            IntegratedBuildReceipt existing = productionReceipt;
            if (existing != null) return existing;
            IntegratedBuildReceipt computed = currentIntegratedBuildReceipt(
                    Mc263FinalChunkSidecars.class.getClassLoader(),
                    Mc263FinalChunkSidecars.class);
            productionReceipt = computed;
            return computed;
        }
    }

    private static IntegratedBuildReceipt currentIntegratedBuildReceipt(ClassLoader loader,
            Class<?> anchor) {
        LinkedHashMap<String, String> classes = new LinkedHashMap<>();
        for (String resource : allClassReceiptResources()) {
            classes.put(resource, HexFormat.of().formatHex(sha256(readExactClassResource(
                    loader, anchor, resource))));
        }
        LinkedHashMap<String, String> external = currentExternalReceipts(loader, anchor);
        String producer = HexFormat.of().formatHex(sha256(classReceiptPreimage(
                "MC263-CONTAINER-LOOT-PRODUCER-CLASSES-V1\0", null,
                CONTAINER_LOOT_PRODUCER_CLASSES, loader, anchor)));
        String exporter = HexFormat.of().formatHex(sha256(classReceiptPreimage(
                "MC263-CONTAINER-LOOT-EXPORTER-CLASSES-V1\0",
                HexFormat.of().parseHex(producer), CONTAINER_LOOT_EXPORTER_CLASSES,
                loader, anchor)));
        return IntegratedBuildReceipt.fromIntegratedBuild(producer, exporter, classes, external,
                integratedReceiptSeal(producer, exporter, classes, external));
    }

    /** Existing focused-test entry point retained as a source- and binary-compatible alias. */
    public static IntegratedBuildReceipt currentIntegratedBuildReceiptForTesting() {
        return currentIntegratedBuildReceipt();
    }

    /** Test-only seam for a separately defined, authenticated exploded build. */
    static IntegratedBuildReceipt currentIntegratedBuildReceiptForTesting(ClassLoader loader,
            Class<?> anchor) {
        return currentIntegratedBuildReceipt(loader, anchor);
    }

    /** Test-only seam for proving that changed compiled external bytes cannot be self-sealed. */
    public static Map<String, String> currentExternalReceiptsForTesting(ClassLoader loader,
            Class<?> anchor) {
        return Collections.unmodifiableMap(currentExternalReceipts(loader, anchor));
    }

    private static LinkedHashMap<String, String> currentExternalReceipts(ClassLoader loader,
            Class<?> anchor) {
        LinkedHashMap<String, String> receipts = new LinkedHashMap<>();
        CONTAINER_LOOT_EXTERNAL_RESOURCE_CLOSURES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> receipts.put(entry.getKey(), HexFormat.of().formatHex(sha256(
                        classReceiptPreimage(
                                "MC263-INTEGRATED-EXTERNAL-RESOURCE-CLOSURE-V1\0"
                                        + entry.getKey() + "\0",
                                null, entry.getValue(), loader, anchor)))));
        return receipts;
    }

    private static List<String> classResources(String outer, String... nested) {
        Objects.requireNonNull(outer, "compiled resource outer class");
        if (outer.isEmpty() || outer.endsWith(".class") || outer.startsWith("/")) {
            throw new IllegalStateException("invalid compiled resource outer class: " + outer);
        }
        java.util.ArrayList<String> resources = new java.util.ArrayList<>(nested.length + 1);
        resources.add(outer + ".class");
        for (String suffix : nested) {
            if (suffix == null || suffix.isEmpty() || suffix.indexOf('/') >= 0
                    || suffix.endsWith(".class")) {
                throw new IllegalStateException("invalid compiled nested resource: " + suffix);
            }
            resources.add(outer + "$" + suffix + ".class");
        }
        return List.copyOf(resources);
    }

    @SafeVarargs
    private static List<String> mergeResources(List<String>... closures) {
        java.util.ArrayList<String> resources = new java.util.ArrayList<>();
        for (List<String> closure : closures) {
            resources.addAll(Objects.requireNonNull(closure, "compiled resource closure"));
        }
        if (resources.size() != new LinkedHashSet<>(resources).size()) {
            throw new IllegalStateException("compiled resource closures overlap");
        }
        return List.copyOf(resources);
    }

    private static List<String> allClassReceiptResources() {
        LinkedHashSet<String> resources = new LinkedHashSet<>(CONTAINER_LOOT_PRODUCER_CLASSES);
        resources.addAll(CONTAINER_LOOT_EXPORTER_CLASSES);
        return List.copyOf(resources);
    }

    private static String integratedReceiptSeal(String producer, String exporter,
            Map<String, String> classes, Map<String, String> external) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.write("MC263-INTEGRATED-CLASS-RECEIPT-SEAL-V1\0"
                    .getBytes(StandardCharsets.US_ASCII));
            writeString(out, producer); writeString(out, exporter);
            writeSortedMap(out, classes); writeSortedMap(out, external);
            out.flush();
            return HexFormat.of().formatHex(sha256(bytes.toByteArray()));
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void writeSortedMap(DataOutputStream out, Map<String, String> values)
            throws IOException {
        values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            try {
                writeString(out, entry.getKey()); writeString(out, entry.getValue());
            } catch (IOException impossible) {
                throw new IllegalStateException(impossible);
            }
        });
    }

    /** External integrated-build identity for the producer/exporter closure. */
    public static final class IntegratedBuildReceipt {
        private static final String FILE_MAGIC = "MC263-INTEGRATED-CLASS-RECEIPT-V1";
        private static final int MAX_RECEIPT_BYTES = 1_048_576;
        private static final Set<OpenOption> READ_OPTIONS = Set.of(
                StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        private final String producerSourceSha256;
        private final String exporterSourceSha256;
        private final Map<String, String> classReceipts;
        private final Map<String, String> externalReceipts;
        private final String seal;
        private volatile boolean verified;

        private IntegratedBuildReceipt(String producerSourceSha256, String exporterSourceSha256,
                Map<String, String> classReceipts, Map<String, String> externalReceipts,
                String seal) {
            requireSha256(producerSourceSha256, "integrated producer receipt");
            requireSha256(exporterSourceSha256, "integrated exporter receipt");
            requireSha256(seal, "integrated receipt seal");
            this.producerSourceSha256 = producerSourceSha256;
            this.exporterSourceSha256 = exporterSourceSha256;
            this.classReceipts = immutableReceiptMap(classReceipts, "class receipts");
            this.externalReceipts = immutableReceiptMap(externalReceipts, "external receipts");
            this.seal = seal;
        }

        public static IntegratedBuildReceipt fromIntegratedBuild(String producerSourceSha256,
                String exporterSourceSha256, Map<String, String> classReceipts,
                Map<String, String> externalReceipts, String seal) {
            return new IntegratedBuildReceipt(producerSourceSha256, exporterSourceSha256,
                    classReceipts, externalReceipts, seal);
        }

        public static IntegratedBuildReceipt read(Path receipt) throws IOException {
            return read(receipt, null);
        }

        /** Test-only seam for deterministically replacing the pathname after descriptor open. */
        static IntegratedBuildReceipt readForTesting(Path receipt, Runnable afterDescriptorOpen)
                throws IOException {
            Objects.requireNonNull(afterDescriptorOpen, "after-descriptor-open hook");
            return read(receipt, afterDescriptorOpen);
        }

        private static IntegratedBuildReceipt read(Path receipt, Runnable afterDescriptorOpen)
                throws IOException {
            Objects.requireNonNull(receipt, "integrated build receipt");
            if (!receipt.isAbsolute()) {
                throw new IllegalArgumentException(
                        "integrated build receipt must be an absolute regular file");
            }
            BasicFileAttributes before = readAttributes(receipt);
            requireRegularReceiptFile(before, "integrated build receipt");
            int expectedBytes = boundedReceiptSize(before.size());
            try (FileChannel channel = FileChannel.open(receipt, READ_OPTIONS)) {
                BasicFileAttributes opened = readAttributes(receipt);
                requireRegularReceiptFile(opened, "integrated build receipt");
                requireStableIdentity(before, opened, expectedBytes,
                        "integrated build receipt changed while opening");
                if (channel.size() != expectedBytes) {
                    throw new IOException("integrated build receipt size changed while opening");
                }
                if (afterDescriptorOpen != null) afterDescriptorOpen.run();

                int descriptorBytes = boundedReceiptSize(channel.size());
                if (descriptorBytes != expectedBytes) {
                    throw new IOException("integrated build receipt size changed while opening");
                }
                byte[] payload = new byte[descriptorBytes];
                ByteBuffer buffer = ByteBuffer.wrap(payload);
                while (buffer.hasRemaining()) {
                    int count = channel.read(buffer);
                    if (count < 0) {
                        throw new IOException("integrated build receipt ended before its size");
                    }
                    if (count == 0) {
                        throw new IOException("integrated build receipt made no read progress");
                    }
                }
                if (channel.size() != expectedBytes) {
                    throw new IOException("integrated build receipt size changed while reading");
                }
                if (channel.read(ByteBuffer.allocate(1)) != -1) {
                    throw new IOException("integrated build receipt has trailing bytes");
                }
                BasicFileAttributes after = readAttributes(receipt);
                requireRegularReceiptFile(after, "integrated build receipt");
                requireStableIdentity(before, after, expectedBytes,
                        "integrated build receipt was replaced while reading");
                return parseCanonicalBytes(payload);
            }
        }

        /** Parses and authenticates one already-read canonical receipt byte sequence. */
        public static IntegratedBuildReceipt parseCanonicalBytes(byte[] payload) {
            Objects.requireNonNull(payload, "integrated build receipt bytes");
            if (payload.length == 0 || payload.length > MAX_RECEIPT_BYTES) {
                throw new IllegalArgumentException("integrated build receipt is oversized");
            }
            for (byte value : payload) {
                int unsigned = value & 0xff;
                if (unsigned != '\n' && (unsigned < 0x20 || unsigned > 0x7e)) {
                    throw new IllegalArgumentException(
                            "integrated build receipt is not strict printable ASCII");
                }
            }
            if (payload[payload.length - 1] != '\n') {
                throw new IllegalArgumentException(
                        "integrated build receipt must end with one line feed");
            }

            String text = new String(payload, StandardCharsets.US_ASCII);
            String body = text.substring(0, text.length() - 1);
            List<String> lines = new java.util.ArrayList<>();
            int start = 0;
            for (int end = 0; end <= body.length(); end++) {
                if (end == body.length() || body.charAt(end) == '\n') {
                    lines.add(body.substring(start, end));
                    start = end + 1;
                }
            }
            if (lines.size() < 4 || !FILE_MAGIC.equals(lines.getFirst())) {
                throw new IllegalArgumentException("integrated build receipt magic or shape mismatch");
            }

            String producer = requiredField(lines.get(1), "producer");
            String exporter = requiredField(lines.get(2), "exporter");
            Map<String, String> classes = new LinkedHashMap<>();
            Map<String, String> external = new LinkedHashMap<>();
            String previousClass = null;
            String previousExternal = null;
            boolean externalSection = false;
            for (int index = 3; index < lines.size() - 1; index++) {
                String line = lines.get(index);
                int equals = line.indexOf('=');
                if (equals <= 0 || equals != line.lastIndexOf('=')) {
                    throw new IllegalArgumentException("malformed integrated build receipt line");
                }
                String key = line.substring(0, equals);
                String value = line.substring(equals + 1);
                if ("class".equals(key)) {
                    if (externalSection) {
                        throw new IllegalArgumentException(
                                "integrated build receipt class entries follow external entries");
                    }
                    String entryKey = putReceipt(classes, value, "class");
                    if (previousClass != null && previousClass.compareTo(entryKey) >= 0) {
                        throw new IllegalArgumentException(
                                "integrated build receipt class entries are not ordered");
                    }
                    previousClass = entryKey;
                } else if ("external".equals(key)) {
                    externalSection = true;
                    String entryKey = putReceipt(external, value, "external");
                    if (previousExternal != null && previousExternal.compareTo(entryKey) >= 0) {
                        throw new IllegalArgumentException(
                                "integrated build receipt external entries are not ordered");
                    }
                    previousExternal = entryKey;
                } else {
                    throw new IllegalArgumentException(
                            "integrated build receipt has an out-of-order field: " + key);
                }
            }
            String seal = requiredField(lines.getLast(), "seal");
            IntegratedBuildReceipt parsed = fromIntegratedBuild(
                    producer, exporter, classes, external, seal);
            String expectedSeal = integratedReceiptSeal(producer, exporter, classes, external);
            if (!MessageDigest.isEqual(expectedSeal.getBytes(StandardCharsets.US_ASCII),
                    seal.getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("integrated build receipt seal mismatch");
            }
            if (!MessageDigest.isEqual(payload, parsed.canonicalBytes())) {
                throw new IllegalArgumentException(
                        "integrated build receipt is not canonically encoded");
            }
            return parsed;
        }

        private static String requiredField(String line, String expectedKey) {
            int equals = line.indexOf('=');
            if (equals != expectedKey.length() || !line.startsWith(expectedKey)
                    || equals != line.lastIndexOf('=')) {
                throw new IllegalArgumentException(
                        "integrated build receipt expected " + expectedKey + " field");
            }
            String value = line.substring(equals + 1);
            requireSha256(value, "integrated " + expectedKey + " receipt");
            return value;
        }

        private static BasicFileAttributes readAttributes(Path receipt) throws IOException {
            return Files.readAttributes(receipt, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
        }

        private static void requireRegularReceiptFile(BasicFileAttributes attributes, String label) {
            if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
                throw new IllegalArgumentException(label + " must be a nofollow regular file");
            }
            if (attributes.fileKey() == null) {
                throw new IllegalArgumentException(label + " has no stable file identity");
            }
        }

        private static int boundedReceiptSize(long size) {
            if (size <= 0 || size > MAX_RECEIPT_BYTES) {
                throw new IllegalArgumentException("integrated build receipt is oversized");
            }
            try {
                return Math.toIntExact(size);
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException(
                        "integrated build receipt size overflows an in-memory buffer", overflow);
            }
        }

        private static void requireStableIdentity(BasicFileAttributes expected,
                BasicFileAttributes actual, long expectedSize, String message) throws IOException {
            if (expected.fileKey() == null || actual.fileKey() == null
                    || !expected.fileKey().equals(actual.fileKey())
                    || expectedSize != actual.size()
                    || !actual.isRegularFile() || actual.isSymbolicLink()) {
                throw new IOException(message);
            }
        }

        public String producerSourceSha256() { return producerSourceSha256; }
        public String exporterSourceSha256() { return exporterSourceSha256; }
        public Map<String, String> classReceipts() { return classReceipts; }
        public Map<String, String> externalReceipts() { return externalReceipts; }
        public String seal() { return seal; }

        /** Canonical parser-compatible encoding: fixed fields, sorted closures, then the seal. */
        public byte[] canonicalBytes() {
            StringBuilder text = new StringBuilder(FILE_MAGIC).append('\n')
                    .append("producer=").append(producerSourceSha256).append('\n')
                    .append("exporter=").append(exporterSourceSha256).append('\n');
            classReceipts.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> text.append("class=").append(entry.getKey()).append(',')
                            .append(entry.getValue()).append('\n'));
            externalReceipts.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> text.append("external=").append(entry.getKey()).append(',')
                            .append(entry.getValue()).append('\n'));
            text.append("seal=").append(seal).append('\n');
            return text.toString().getBytes(StandardCharsets.US_ASCII);
        }

        public void verifyAgainstCurrentBuild() {
            verifyAgainstCurrentBuild(Mc263FinalChunkSidecars.class.getClassLoader(),
                    Mc263FinalChunkSidecars.class);
        }

        /** Test-only seam for verifying a separately defined, authenticated exploded build. */
        void verifyAgainstCurrentBuildForTesting(ClassLoader loader, Class<?> anchor) {
            verifyAgainstCurrentBuild(loader, anchor);
        }

        private void verifyAgainstCurrentBuild(ClassLoader loader, Class<?> anchor) {
            if (verified) return;
            synchronized (this) {
                if (verified) return;
                requireExactKeys(classReceipts, Set.copyOf(allClassReceiptResources()),
                        "class receipt closure");
                requireExactKeys(externalReceipts,
                        CONTAINER_LOOT_EXTERNAL_RESOURCE_CLOSURES.keySet(),
                        "external receipt closure");
                for (String resource : allClassReceiptResources()) {
                    String actual = HexFormat.of().formatHex(sha256(readExactClassResource(
                            loader, anchor, resource)));
                    if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                            classReceipts.get(resource).getBytes(StandardCharsets.US_ASCII))) {
                        throw new IllegalStateException("integrated class receipt mismatch: "
                                + resource);
                    }
                }
                Map<String, String> actualExternal = currentExternalReceipts(
                        loader, anchor);
                for (String key : CONTAINER_LOOT_EXTERNAL_RESOURCE_CLOSURES.keySet().stream()
                        .sorted().toList()) {
                    if (!MessageDigest.isEqual(
                            actualExternal.get(key).getBytes(StandardCharsets.US_ASCII),
                            externalReceipts.get(key).getBytes(StandardCharsets.US_ASCII))) {
                        throw new IllegalStateException(
                                "integrated external receipt mismatch: " + key);
                    }
                }
                String actualProducer = HexFormat.of().formatHex(
                        sha256(classReceiptPreimage("MC263-CONTAINER-LOOT-PRODUCER-CLASSES-V1\0",
                                null, CONTAINER_LOOT_PRODUCER_CLASSES, loader, anchor)));
                if (!actualProducer.equals(producerSourceSha256)) {
                    throw new IllegalStateException("integrated producer receipt mismatch");
                }
                String actualExporter = HexFormat.of().formatHex(sha256(classReceiptPreimage(
                        "MC263-CONTAINER-LOOT-EXPORTER-CLASSES-V1\0",
                        HexFormat.of().parseHex(actualProducer), CONTAINER_LOOT_EXPORTER_CLASSES,
                        loader, anchor)));
                if (!actualExporter.equals(exporterSourceSha256)) {
                    throw new IllegalStateException("integrated exporter receipt mismatch");
                }
                String actualSeal = integratedReceiptSeal(producerSourceSha256,
                        exporterSourceSha256, classReceipts, externalReceipts);
                if (!actualSeal.equals(seal)) {
                    throw new IllegalStateException("integrated build receipt seal mismatch");
                }
                verified = true;
            }
        }

        private static Map<String, String> immutableReceiptMap(Map<String, String> values,
                String label) {
            Objects.requireNonNull(values, label);
            LinkedHashMap<String, String> copy = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                requireReceiptKey(entry.getKey(), label);
                requireSha256(entry.getValue(), label + " value");
                if (copy.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                    throw new IllegalArgumentException(label + " has a duplicate key");
                }
            }
            return Map.copyOf(copy);
        }

        private static void requireExactKeys(Map<String, String> values, Set<String> expected,
                String label) {
            if (!values.keySet().equals(expected)) {
                throw new IllegalStateException(label + " is incomplete or has extra entries");
            }
        }

        private static String putReceipt(Map<String, String> target, String encoded, String kind) {
            int comma = encoded.indexOf(',');
            if (comma <= 0 || comma != encoded.lastIndexOf(',')) {
                throw new IllegalArgumentException("malformed " + kind + " receipt entry");
            }
            String key = encoded.substring(0, comma);
            String value = encoded.substring(comma + 1);
            requireReceiptKey(key, kind + " receipts");
            requireSha256(value, kind + " receipt value");
            if (target.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("duplicate " + kind + " receipt entry: " + key);
            }
            return key;
        }

        private static void requireReceiptKey(String key, String label) {
            if (key == null || key.isEmpty() || key.indexOf(',') >= 0 || key.indexOf('=') >= 0
                    || key.indexOf('\n') >= 0 || key.indexOf('\r') >= 0
                    || key.indexOf('\0') >= 0 || key.length() > 0xffff
                    || !StandardCharsets.US_ASCII.newEncoder().canEncode(key)) {
                throw new IllegalArgumentException(label + " has a noncanonical key");
            }
        }
    }

    private static LootProductionContext canonicalContext(LootProductionContext value) {
        Objects.requireNonNull(value, "production loot context");
        value.requireAuthenticatedContext();
        if (value instanceof LocatedProductionContext located) {
            LinkedHashMap<String, LocatedMapTarget> ordered = new LinkedHashMap<>();
            located.maps().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        entry.getValue().requireAuthenticated();
                        ordered.put(entry.getKey(), entry.getValue());
                    });
            if (ordered.size() > 64) {
                throw new IllegalArgumentException("production loot context exceeds 64 maps");
            }
            return LocatedProductionContext.authenticated(located.biomeKey(), ordered,
                    located.worldIdentity(), located.sourceIdentity(), located.tableIdentity(),
                    located.originX(), located.originY(), located.originZ(),
                    located.catalogReceipt());
        }
        ProductionContext legacy = (ProductionContext) value;
        LinkedHashMap<String, LocatedMap> ordered = new LinkedHashMap<>();
        legacy.maps().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            entry.getValue().requireAuthenticated();
            ordered.put(entry.getKey(), entry.getValue());
        });
        if (ordered.size() > 64) {
            throw new IllegalArgumentException("production loot context exceeds 64 maps");
        }
        return ProductionContext.authenticated(value.biomeKey(), ordered,
                value.worldIdentity(), value.sourceIdentity(), value.tableIdentity(),
                value.originX(), value.originY(), value.originZ(), value.catalogReceipt());
    }

    private static void writeProductionContext(DataOutputStream out, LootProductionContext context)
            throws IOException {
        if (context instanceof LocatedProductionContext located) {
            out.write("MC263-LOCATED-CONTEXT-V2\0".getBytes(StandardCharsets.US_ASCII));
            writeLocatedProductionContext(out, located);
            return;
        }
        ProductionContext legacy = (ProductionContext) context;
        writeString(out, legacy.biomeKey());
        writeString(out, legacy.worldIdentity());
        writeString(out, legacy.sourceIdentity());
        writeString(out, legacy.tableIdentity());
        out.writeInt(legacy.originX()); out.writeInt(legacy.originY());
        out.writeInt(legacy.originZ());
        out.write(HexFormat.of().parseHex(legacy.catalogReceipt()));
        List<Map.Entry<String, LocatedMap>> maps = legacy.maps().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder())).toList();
        out.writeShort(maps.size());
        for (Map.Entry<String, LocatedMap> entry : maps) {
            LocatedMap map = entry.getValue();
            writeString(out, entry.getKey());
            writeString(out, map.destination());
            writeString(out, map.worldIdentity());
            writeString(out, map.sourceIdentity());
            writeString(out, map.tableIdentity());
            out.writeInt(map.mapId()); out.writeInt(map.centerX()); out.writeInt(map.centerZ());
            out.writeInt(map.originX()); out.writeInt(map.originY()); out.writeInt(map.originZ());
            out.writeInt(map.scale());
            out.write(HexFormat.of().parseHex(map.resolverCatalogReceipt()));
        }
    }

    private static void writeLocatedProductionContext(DataOutputStream out,
            LocatedProductionContext context) throws IOException {
        writeString(out, context.biomeKey()); writeString(out, context.worldIdentity());
        writeString(out, context.sourceIdentity()); writeString(out, context.tableIdentity());
        out.writeInt(context.originX()); out.writeInt(context.originY());
        out.writeInt(context.originZ());
        out.write(HexFormat.of().parseHex(context.catalogReceipt()));
        List<Map.Entry<String, LocatedMapTarget>> maps = context.maps().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList();
        out.writeShort(maps.size());
        for (var entry : maps) {
            var target = entry.getValue();
            var binding = target.binding();
            writeString(out, entry.getKey());
            out.writeByte(target instanceof com.gameexpert.terrain.mc.loot
                    .Mc263LocatedMapAuthority.Found ? 1 : 0);
            writeString(out, binding.destinationTag()); writeString(out, binding.structureSet());
            out.writeShort(binding.acceptedMembers().size());
            for (String member : binding.acceptedMembers()) writeString(out, member);
            out.writeInt(binding.scale()); out.writeInt(binding.searchRadius());
            out.writeBoolean(binding.skipExistingChunks());
            out.write(HexFormat.of().parseHex(binding.locatorSourceReceipt()));
            String referenceSnapshotReceipt = binding.referenceSnapshotReceipt();
            requireNonzeroSha256(referenceSnapshotReceipt,
                    "located-map reference snapshot receipt");
            out.write(HexFormat.of().parseHex(referenceSnapshotReceipt));
            if (target instanceof com.gameexpert.terrain.mc.loot
                    .Mc263LocatedMapAuthority.Found found) {
                out.writeInt(found.targetX()); out.writeInt(found.targetZ());
                out.writeInt(found.savedCenterX()); out.writeInt(found.savedCenterZ());
                out.write(HexFormat.of().parseHex(found.previewSha256()));
            }
            out.write(HexFormat.of().parseHex(target.targetReceipt()));
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        value = Objects.requireNonNull(value, "declaration string");
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (codePoint > 0x7f) {
                throw new IllegalArgumentException(
                        "declaration string contains a non-ASCII code point");
            }
            offset += Character.charCount(codePoint);
        }
        if (value.length() > 0xffff) {
            throw new IllegalArgumentException("declaration string exceeds u16 bytes");
        }
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        out.writeShort(bytes.length); out.write(bytes);
    }

    private static void requireSha256(String value, String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be canonical SHA-256");
        }
    }

    private static void requireNonzeroSha256(String value, String name) {
        requireSha256(value, name);
        if (value.equals("0".repeat(64))) {
            throw new IllegalArgumentException(name + " must be nonzero");
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    @FunctionalInterface
    private interface SourceRowWriter {
        void write(DataOutputStream output) throws IOException;
    }
}
