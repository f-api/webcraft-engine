package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.LocatedProductionContext;
import com.gameexpert.terrain.mc.loot.Mc263ContainerLootResolver.LootProductionContext;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Canonical FEATURES orchestrator for the current 26.3 worldgen contract. */
public final class Mc263CanonicalFeaturesProducerSkeleton {
    private static final byte[] PROVENANCE_MAGIC =
            "CPS263P1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WORLDGEN_RANDOM_PROVENANCE_MAGIC =
            "WGR263P1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PRODUCTION_CONTEXT_PROVENANCE_MAGIC =
            "PCA263P1".getBytes(StandardCharsets.US_ASCII);

    private Mc263CanonicalFeaturesProducerSkeleton() {}

    /** Builds the complete final carrier after FEATURES, POST, and structure persistence close. */
    public static Mc263CanonicalGenerationProduct produce(UpstreamProduct product,
            Mc263PostCarversFeaturesRegionBuilder.HeightmapPredicates predicates,
            Mc263PostprocessResolver.ActivationContext activation,
            CanonicalStructureExecutorRegistry executors) {
        return produce(product, predicates, activation, executors, List.of(), List.of());
    }

    /**
     * Produces a canonical chunk while preserving exact structure sidecar payloads supplied by
     * the authority. The producer owns ordering and codec validation; it never synthesizes NBT or
     * entity presentation state.
     */
    public static Mc263CanonicalGenerationProduct produce(UpstreamProduct product,
            Mc263PostCarversFeaturesRegionBuilder.HeightmapPredicates predicates,
            Mc263PostprocessResolver.ActivationContext activation,
            CanonicalStructureExecutorRegistry executors,
            List<Mc263FinalChunkSidecars.BlockEntity> blockEntities,
            List<Mc263FinalChunkSidecars.StructureEntity> entities) {
        Objects.requireNonNull(activation, "POST activation context");
        product = Objects.requireNonNull(product, "canonical upstream product");
        blockEntities = List.copyOf(Objects.requireNonNull(
                blockEntities, "caller block entities"));
        entities = List.copyOf(Objects.requireNonNull(entities, "caller structure entities"));
        preflightCallerEntities(product, entities);
        DispatchRun run = run(product, predicates, executors);
        try {
            WorldGenRegionRandomFork postRandom = run.structures().forkWorldGenRegionRandom();
            Mc263PostprocessResolver.Result post = Mc263PostprocessResolver.resolve(
                    run.region(), activation, bound -> postRandom.random().nextInt(bound));
            run.structures().commitWorldGenRegionRandom(postRandom);
            // POST mutates the live region transactionally; the dispatcher snapshot is intentionally
            // pre-POST evidence and must never be assembled after resolution.
            Mc263FeaturesRegion.CenterSnapshot center = run.region().snapshotCenter();
            List<Mc263FinalChunkSidecars.BlockEntity> mergedBlockEntities = run.structures()
                    .centerBlockEntities(center.chunkX(), center.chunkZ(), blockEntities);
            List<Mc263FinalChunkSidecars.StructureEntity> mergedEntities = run.structures()
                    .centerEntities(center.chunkX(), center.chunkZ(), entities);
            Mc263FinalChunkSidecars.IntegratedBuildReceipt integratedBuildReceipt =
                    Mc263FinalChunkSidecars.currentIntegratedBuildReceipt();
            integratedBuildReceipt.verifyAgainstCurrentBuild();
            Mc263FinalChunkCodec.FinalChunk finalChunk = Mc263FinalChunkAssembler.assemble(
                    center, List.of(), mergedBlockEntities, mergedEntities,
                    integratedBuildReceipt.producerSourceSha256());
            byte[] finalCarrier = Mc263FinalChunkCodec.encode(finalChunk, integratedBuildReceipt);
            Mc263StructureCarrier currentCarrier = run.structures().currentCarrier();
            Mc263StructureCarrier successorCarrier = currentCarrier.strictlyDecoded();
            if (!successorCarrier.hasSameReceipt(currentCarrier)) {
                throw new IllegalStateException(
                        "canonical STR successor receipt changed during assembly");
            }
            byte[] structureReceipt = successorCarrier.receiptBytes();
            Mc263WorldGenRegionRandom.State successorRandomState =
                    run.structures().currentWorldGenRegionRandomState();
            byte[] provenanceReceipt = product.provenanceReceipt(successorCarrier,
                    successorRandomState);
            byte[] commitFingerprint = commitFingerprint(provenanceReceipt, run.dispatch(), post,
                    structureReceipt, finalCarrier);
            return new Mc263CanonicalGenerationProduct(finalChunk, successorCarrier,
                    provenanceReceipt, run.dispatch().binaryReceipt(),
                    List.of(sha256(provenanceReceipt),
                            sha256(run.dispatch().binaryReceipt()),
                            sha256(finalCarrier)), commitFingerprint,
                    new DispatchEvidence(product.sources().size(),
                            product.sources().size() * Mc263StructureIndexReceipt.STRUCTURE_COUNT,
                            run.dispatch().scheduleTrace().size(),
                            run.dispatch().scheduleTraceSha256()), post, integratedBuildReceipt);
        } finally {
            run.structures().close();
        }
    }

    static DispatchEvidence dispatch(UpstreamProduct product,
            Mc263PostCarversFeaturesRegionBuilder.HeightmapPredicates predicates,
            CanonicalStructureExecutorRegistry executors) {
        DispatchRun run = run(product, predicates, executors);
        try {
            return new DispatchEvidence(product.sources().size(),
                    product.sources().size() * Mc263StructureIndexReceipt.STRUCTURE_COUNT,
                    run.dispatch().scheduleTrace().size(), run.dispatch().scheduleTraceSha256());
        } finally {
            run.structures().close();
        }
    }

    private static DispatchRun run(UpstreamProduct product,
            Mc263PostCarversFeaturesRegionBuilder.HeightmapPredicates predicates,
            CanonicalStructureExecutorRegistry executors) {
        Objects.requireNonNull(executors, "canonical structure executors").requireComplete();
        Objects.requireNonNull(product, "canonical upstream product");
        Objects.requireNonNull(predicates, "heightmap predicates");

        for (SourceInput source : product.sources()) {
            for (Mc263StructureIndexReceipt.Entry entry : Mc263StructureIndexReceipt.entries()) {
                executors.require(entry.key()).preflight(new StructurePreflightContext(
                        product.worldSeed(), product.worldGenRegionRandomState(), entry,
                        product.carrier(), source.references(), source.clip()));
            }
        }

        Mc263FeaturesRegion region = Mc263PostCarversFeaturesRegionBuilder.build(
                product.regionInput(), predicates);
        StructureDispatchState structures = new StructureDispatchState(product, region);
        Mc263FeatureDispatcher dispatcher = Mc263FeaturesStep10Stage.createDispatcher()
                .installPinnedStructureSchedule();
        for (Mc263StructureIndexReceipt.Entry entry : Mc263StructureIndexReceipt.entries()) {
            CanonicalStructureExecutor executor = executors.require(entry.key());
            dispatcher.registerStructure(entry.key(), context -> {
                SourceInput source = product.source(context.sourceChunkX(), context.sourceChunkZ());
                executor.place(new StructurePlacementInput(product.carrier(),
                        source.references(), source.clip(), context, structures,
                        product.productionContextAuthority()));
            });
        }
        try {
            Mc263FeatureDispatcher.DispatchResult internal = dispatcher.dispatchAuthenticated(
                    product.worldSeed(), region, product.productionContextAuthority()::locate);
            return new DispatchRun(region, internal, structures);
        } catch (RuntimeException | Error failure) {
            structures.close();
            throw failure;
        }
    }

    private static byte[] commitFingerprint(byte[] provenanceReceipt,
            Mc263FeatureDispatcher.DispatchResult dispatch, Mc263PostprocessResolver.Result post,
            byte[] structureReceipt, byte[] finalCarrier) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update("MC263-CANONICAL-PRODUCT-V1\0".getBytes(StandardCharsets.US_ASCII));
            digest.update(provenanceReceipt);
            digest.update(structureReceipt);
            digest.update(dispatch.binaryReceipt());
            digest.update(dispatch.scheduleTraceSha256().getBytes(StandardCharsets.US_ASCII));
            digest.update(Integer.toString(post.processedOccurrences()).getBytes(StandardCharsets.US_ASCII));
            digest.update(finalCarrier);
            return digest.digest();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void preflightCallerEntities(UpstreamProduct product,
            List<Mc263FinalChunkSidecars.StructureEntity> entities) {
        int chunkX = product.regionInput().target().chunkX();
        int chunkZ = product.regionInput().target().chunkZ();
        LinkedHashSet<Mc263FinalChunkSidecars.StructureEntity> records =
                new LinkedHashSet<>();
        for (Mc263FinalChunkSidecars.StructureEntity entity : entities) {
            requireEntityInChunk(entity, chunkX, chunkZ, "caller ENTS");
            if (!records.add(entity)) {
                throw new IllegalArgumentException("duplicate caller ENTS at "
                        + entityPosition(entity));
            }
        }
    }

    /** Non-encodable proof that the dormant schedule ran; no center snapshot or MCF carrier escapes. */
    record DispatchEvidence(int sourceCount, int structurePreflightCount,
                            int scheduleEventCount, String scheduleTraceSha256) {}

    private record DispatchRun(Mc263FeaturesRegion region,
                               Mc263FeatureDispatcher.DispatchResult dispatch,
                               StructureDispatchState structures) {}

    public static final class CanonicalStructureExecutorRegistry {
        private final Map<String, CanonicalStructureExecutor> executors = new LinkedHashMap<>();
        private boolean sealed;

        CanonicalStructureExecutorRegistry register(String structureKey,
                CanonicalStructureExecutor executor) {
            if (sealed) throw new IllegalStateException("canonical executor registry is sealed");
            Objects.requireNonNull(executor, "canonical structure executor");
            boolean represented = Mc263StructureIndexReceipt.entries().stream()
                    .anyMatch(entry -> entry.key().equals(structureKey));
            if (!represented) {
                throw new IllegalArgumentException(
                        "structure capability is outside the pinned 52: " + structureKey);
            }
            if (executors.putIfAbsent(structureKey, executor) != null) {
                throw new IllegalArgumentException("duplicate structure capability: " + structureKey);
            }
            return this;
        }

        CanonicalStructureExecutorRegistry seal() {
            requireComplete();
            sealed = true;
            return this;
        }

        int size() { return executors.size(); }

        private CanonicalStructureExecutor require(String key) {
            CanonicalStructureExecutor executor = executors.get(key);
            if (executor == null) throw new IllegalStateException("missing complete structure executor: " + key);
            return executor;
        }

        void requireComplete() {
            List<String> missing = Mc263StructureIndexReceipt.entries().stream()
                    .map(Mc263StructureIndexReceipt.Entry::key)
                    .filter(key -> !executors.containsKey(key)).toList();
            if (!missing.isEmpty() || executors.size() != Mc263StructureIndexReceipt.STRUCTURE_COUNT) {
                throw new IllegalStateException("canonical structure executor closure is incomplete: "
                        + executors.size() + "/" + Mc263StructureIndexReceipt.STRUCTURE_COUNT
                        + ", first missing=" + missing.getFirst());
            }
        }
    }

    interface CanonicalStructureExecutor {
        /** Pure capability/payload validation: no RNG, live query, copy, or write is permitted. */
        void preflight(StructurePreflightContext context);
        void place(StructurePlacementInput context);
    }

    @FunctionalInterface
    public interface ProductionContextLocator {
        LootProductionContext locate(Mc263FeaturesRegion region, int blockX, int blockY,
                int blockZ, String lootTable);
    }

    /** Authenticated identity plus the complete read-only authority used by structure placement. */
    public static final class ProductionContextAuthority {
        private static final int RECEIPT_BYTES = 32;
        private final byte[] receipt;
        private final ProductionContextLocator locator;
        private UpstreamProduct owner;
        private byte[] productBinding;
        private final ArrayList<byte[]> resultBindings = new ArrayList<>();

        private ProductionContextAuthority(byte[] receipt, ProductionContextLocator locator) {
            this.receipt = receipt;
            this.locator = locator;
        }

        public static ProductionContextAuthority authenticated(byte[] receipt,
                ProductionContextLocator locator) {
            receipt = Objects.requireNonNull(receipt, "production context authority receipt")
                    .clone();
            if (receipt.length != RECEIPT_BYTES) {
                throw new IllegalArgumentException(
                        "production context authority receipt must be SHA-256 sized");
            }
            return new ProductionContextAuthority(receipt,
                    Objects.requireNonNull(locator, "production context authority locator"));
        }

        static ProductionContextAuthority absent() {
            return new ProductionContextAuthority(new byte[0], null);
        }

        private synchronized void bind(UpstreamProduct candidateOwner, byte[] candidate) {
            Objects.requireNonNull(candidateOwner, "production context upstream product");
            candidate = Objects.requireNonNull(candidate,
                    "production context product binding").clone();
            if (owner == null) {
                owner = candidateOwner;
                productBinding = candidate;
            } else if (owner != candidateOwner
                    || !java.security.MessageDigest.isEqual(productBinding, candidate)) {
                throw new IllegalArgumentException(
                        "production context authority belongs to another upstream product");
            }
        }

        private synchronized byte[] receipt() {
            if (locator != null && productBinding == null) {
                throw new IllegalStateException(
                        "production context authority is not bound to an upstream product");
            }
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes);
                out.write("PCA263-AUTHORITY-V2\0".getBytes(StandardCharsets.US_ASCII));
                out.writeInt(receipt.length); out.write(receipt);
                out.writeInt(productBinding == null ? 0 : productBinding.length);
                if (productBinding != null) out.write(productBinding);
                out.writeInt(resultBindings.size());
                for (byte[] result : resultBindings) {
                    out.writeInt(result.length); out.write(result);
                }
                out.flush();
                return java.security.MessageDigest.getInstance("SHA-256")
                        .digest(bytes.toByteArray());
            } catch (IOException | java.security.NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("production context receipt failed", impossible);
            }
        }

        synchronized LootProductionContext locate(Mc263FeaturesRegion region, int blockX,
                int blockY,
                int blockZ, String lootTable) {
            if (locator == null) {
                throw new IllegalStateException(
                        "authenticated production context authority is absent");
            }
            Objects.requireNonNull(region, "production context live region");
            if (blockY < Blocks.MIN_Y || blockY > Blocks.MAX_Y || lootTable == null
                    || !lootTable.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                throw new IllegalArgumentException("invalid production context request");
            }
            LootProductionContext context = Objects.requireNonNull(
                    locator.locate(region, blockX, blockY, blockZ, lootTable),
                    "authenticated production context");
            if (!context.biomeKey().equals(region.biomeKey(blockX, blockY, blockZ))) {
                throw new IllegalArgumentException(
                        "production context authority returned a mixed live biome");
            }
            if (!(context instanceof LocatedProductionContext located)) {
                throw new IllegalArgumentException(
                        "production authority returned legacy fixture context");
            }
            if (Mc263FeaturesRegion.isMapSensitiveCampLootTable(lootTable)
                    && located.maps().isEmpty()) {
                throw new IllegalArgumentException(
                        "map-sensitive Camp production context lacks located-map targets");
            }
            resultBindings.add(resultBinding(blockX, blockY, blockZ, lootTable, context));
            return context;
        }

        private static byte[] resultBinding(int blockX, int blockY, int blockZ,
                String lootTable, LootProductionContext context) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes);
                out.write("PCA263-RESULT-V2\0".getBytes(StandardCharsets.US_ASCII));
                out.writeInt(blockX); out.writeInt(blockY); out.writeInt(blockZ);
                out.writeUTF(lootTable); out.writeUTF(context.biomeKey());
                out.writeUTF(context.worldIdentity());
                out.write(HexFormat.of().parseHex(context.sourceIdentity()));
                out.writeUTF(context.tableIdentity());
                out.writeInt(context.originX()); out.writeInt(context.originY());
                out.writeInt(context.originZ());
                out.write(HexFormat.of().parseHex(context.catalogReceipt()));
                LocatedProductionContext located = (LocatedProductionContext) context;
                List<Map.Entry<String,
                        com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority.LocatedMapTarget>> maps =
                        located.maps().entrySet().stream()
                                .sorted(Map.Entry.comparingByKey()).toList();
                out.writeInt(maps.size());
                for (var entry : maps) {
                    out.writeUTF(entry.getKey());
                    out.writeBoolean(entry.getValue()
                            instanceof com.gameexpert.terrain.mc.loot.Mc263LocatedMapAuthority.Found);
                    out.write(HexFormat.of().parseHex(entry.getValue().targetReceipt()));
                }
                out.flush();
                return java.security.MessageDigest.getInstance("SHA-256")
                        .digest(bytes.toByteArray());
            } catch (IOException | java.security.NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("production context result binding failed",
                        impossible);
            }
        }
    }

    record StructurePreflightContext(long worldSeed,
                                     Mc263WorldGenRegionRandom.State worldGenRegionRandomState,
                                     Mc263StructureIndexReceipt.Entry entry,
                                     Mc263StructureCarrier carrier,
                                     Mc263StructureCarrier.ChunkReferences references,
                                     SourceClip clip) {
        StructurePreflightContext {
            Objects.requireNonNull(worldGenRegionRandomState,
                    "WorldGenRegion random preflight state");
        }
    }

    record StructurePlacementInput(Mc263StructureCarrier initialCarrier,
                                   Mc263StructureCarrier.ChunkReferences initialReferences,
                                   SourceClip clip,
                                   Mc263FeatureDispatcher.StructurePlacementContext dispatcher,
                                   StructureDispatchState transaction,
                                   ProductionContextAuthority productionContextAuthority) {
        StructurePlacementInput {
            Objects.requireNonNull(initialCarrier, "initial structure carrier");
            Objects.requireNonNull(initialReferences, "initial structure references");
            Objects.requireNonNull(clip, "structure source clip");
            Objects.requireNonNull(dispatcher, "structure dispatcher context");
            if (transaction == null) {
                if (productionContextAuthority != null) {
                    throw new IllegalArgumentException(
                            "production context authority is outside producer transaction");
                }
            } else {
                transaction.requireBinding(initialCarrier, initialReferences, clip, dispatcher,
                        productionContextAuthority);
            }
        }

        StructurePlacementInput(Mc263StructureCarrier carrier,
                Mc263StructureCarrier.ChunkReferences references, SourceClip clip,
                Mc263FeatureDispatcher.StructurePlacementContext dispatcher) {
            this(carrier, references, clip, dispatcher, null, null);
        }

        @Override
        public Mc263FeatureDispatcher.StructurePlacementContext dispatcher() {
            if (transaction != null) transaction.requireActive();
            return dispatcher;
        }

        Mc263StructureCarrier carrier() {
            return transaction == null ? initialCarrier : transaction.currentCarrier();
        }

        Mc263StructureCarrier.ChunkReferences references() {
            if (transaction == null) return initialReferences;
            return transaction.currentCarrier()
                    .referenceChunk(initialReferences.chunkX(), initialReferences.chunkZ())
                    .orElseThrow(() -> new IllegalStateException(
                            "evolved structure carrier lost source references"));
        }

        Mc263WorldGenRegionRandom.State worldGenRegionRandomState() {
            requireTransaction();
            return transaction.currentWorldGenRegionRandomState();
        }

        WorldGenRegionRandomFork forkWorldGenRegionRandom() {
            requireTransaction();
            return transaction.forkWorldGenRegionRandom();
        }

        LootProductionContext productionContext(int blockX, int blockY, int blockZ,
                String lootTable) {
            requireTransaction();
            return transaction.productionContext(this, blockX, blockY, blockZ, lootTable);
        }

        void commitStructureBatch(Mc263StructureCarrier predecessor,
                Mc263StructureCarrier successor, Mc263FeaturesRegion.StructureBatch batch) {
            requireTransaction();
            transaction.commit(predecessor, successor, batch);
        }

        void commitStructureBatchWithWorldGenRegionRandom(Mc263StructureCarrier predecessor,
                Mc263StructureCarrier successor, Mc263FeaturesRegion.StructureBatch batch,
                WorldGenRegionRandomFork randomFork) {
            requireTransaction();
            transaction.commit(predecessor, successor, batch, randomFork);
        }

        private void requireTransaction() {
            if (transaction == null) {
                throw new IllegalStateException(
                        "structure placement is outside producer transaction");
            }
        }
    }

    /** One placement-local mutable candidate bound to its exact shared RNG predecessor. */
    static final class WorldGenRegionRandomFork {
        private final StructureDispatchState owner;
        private final Mc263WorldGenRegionRandom.State predecessor;
        private final Mc263WorldGenRegionRandom random;
        private boolean committed;

        private WorldGenRegionRandomFork(StructureDispatchState owner,
                Mc263WorldGenRegionRandom.State predecessor,
                Mc263WorldGenRegionRandom random) {
            this.owner = Objects.requireNonNull(owner, "WorldGenRegion RNG fork owner");
            this.predecessor = Objects.requireNonNull(predecessor,
                    "WorldGenRegion RNG predecessor");
            this.random = Objects.requireNonNull(random, "WorldGenRegion RNG candidate");
        }

        Mc263WorldGenRegionRandom.State predecessor() {
            owner.requireFork(this);
            return predecessor;
        }
        Mc263WorldGenRegionRandom random() {
            owner.requireFork(this);
            return random;
        }
    }

    /** Producer-owned mutable STR/RNG ledger; no executor may persist an intermediate successor. */
    static final class StructureDispatchState {
        private final UpstreamProduct upstream;
        private Mc263StructureCarrier currentCarrier;
        private final Mc263FeaturesRegion region;
        private final Mc263WorldGenRegionRandom worldGenRegionRandom;
        private final Mc263StructureCarrier.SuccessorTransaction successorTransaction;
        private boolean active = true;
        private final LinkedHashMap<Mc263FeaturesRegion.DestinationPosition,
                Mc263FinalChunkSidecars.BlockEntity> blockEntities = new LinkedHashMap<>();
        private final LinkedHashMap<Mc263FeaturesRegion.DestinationPosition,
                List<Mc263FinalChunkSidecars.StructureEntity>> entities = new LinkedHashMap<>();
        private final ArrayList<Mc263FinalChunkSidecars.StructureEntity> orderedEntities =
                new ArrayList<>();
        private final LinkedHashSet<Mc263FinalChunkSidecars.StructureEntity> entityRecords =
                new LinkedHashSet<>();

        private StructureDispatchState(UpstreamProduct upstream, Mc263FeaturesRegion region) {
            this.upstream = Objects.requireNonNull(upstream, "canonical upstream product");
            currentCarrier = upstream.carrier();
            this.region = Objects.requireNonNull(region, "FEATURES region");
            worldGenRegionRandom = randomFromState(upstream.worldGenRegionRandomState());
            successorTransaction = Mc263StructureCarrier.beginSuccessorTransaction(
                    currentCarrier, this, currentCarrier, upstream.productionContextAuthority());
        }

        synchronized Mc263StructureCarrier currentCarrier() {
            requireActive();
            return currentCarrier;
        }

        synchronized Mc263WorldGenRegionRandom.State currentWorldGenRegionRandomState() {
            requireActive();
            return worldGenRegionRandom.snapshot();
        }

        synchronized WorldGenRegionRandomFork forkWorldGenRegionRandom() {
            requireActive();
            Mc263WorldGenRegionRandom.State predecessor = worldGenRegionRandom.snapshot();
            return new WorldGenRegionRandomFork(this, predecessor,
                    worldGenRegionRandom.forkForTransaction());
        }

        synchronized void commitWorldGenRegionRandom(WorldGenRegionRandomFork randomFork) {
            requireActive();
            Objects.requireNonNull(randomFork, "WorldGenRegion random fork");
            requireFork(randomFork);
            if (randomFork.committed) {
                throw new IllegalArgumentException("WorldGenRegion random fork already committed");
            }
            Mc263WorldGenRegionRandom accepted = randomFromState(randomFork.random.snapshot());
            if (!worldGenRegionRandom.commitIfExactPredecessor(
                    randomFork.predecessor, accepted)) {
                throw new IllegalArgumentException("stale WorldGenRegion random predecessor");
            }
            randomFork.committed = true;
        }

        private synchronized void commit(Mc263StructureCarrier predecessor,
                Mc263StructureCarrier successor, Mc263FeaturesRegion.StructureBatch batch) {
            requireActive();
            commitPrepared(predecessor, successor, batch, null);
        }

        private synchronized void commit(Mc263StructureCarrier predecessor,
                Mc263StructureCarrier successor, Mc263FeaturesRegion.StructureBatch batch,
                WorldGenRegionRandomFork randomFork) {
            requireActive();
            commitPrepared(predecessor, successor, batch,
                    Objects.requireNonNull(randomFork, "WorldGenRegion random fork"));
        }

        private void commitPrepared(Mc263StructureCarrier predecessor,
                Mc263StructureCarrier successor, Mc263FeaturesRegion.StructureBatch batch,
                WorldGenRegionRandomFork randomFork) {
            Mc263WorldGenRegionRandom acceptedRandom = null;
            if (randomFork != null) {
                requireFork(randomFork);
                if (randomFork.committed) {
                    throw new IllegalArgumentException("WorldGenRegion random fork already committed");
                }
                if (!worldGenRegionRandom.snapshot().equals(randomFork.predecessor)) {
                    throw new IllegalArgumentException("stale WorldGenRegion random predecessor");
                }
                acceptedRandom = randomFromState(randomFork.random.snapshot());
            }
            LinkedHashMap<Mc263FeaturesRegion.DestinationPosition,
                    Mc263FinalChunkSidecars.BlockEntity> prepared = prepareBlockEntities(batch);
            LinkedHashMap<Mc263FeaturesRegion.DestinationPosition,
                    List<Mc263FinalChunkSidecars.StructureEntity>> preparedEntities =
                    prepareEntities(batch);
            Mc263StructureCarrier decoded = validateSuccessor(predecessor, successor);
            List<?> orderedMutations = orderedMutations(batch);
            Mc263StructureCarrier.Successor issued = successorTransaction.issueSuccessor(
                    predecessor, decoded, orderedMutations);
            try {
                Mc263FeaturesRegion.StructureBatchSettlement settlement =
                        region.settleStructureBatch(batch);
                if (!settlement.blockEntities().equals(prepared)) {
                    throw new IllegalStateException(
                            "structure BENT preflight/settlement mismatch");
                }
                if (!settlement.entities().equals(preparedEntities)) {
                    throw new IllegalStateException(
                            "structure ENTS preflight/settlement mismatch");
                }
                Mc263StructureCarrier committed = successorTransaction.commit(issued, this,
                        orderedMutations, decoded);
                currentCarrier = committed;
                blockEntities.putAll(prepared);
                preparedEntities.forEach((destination, values) -> entities
                        .computeIfAbsent(destination, ignored -> new ArrayList<>()).addAll(values));
                orderedEntities.addAll(batch.entities());
                for (List<Mc263FinalChunkSidecars.StructureEntity> values :
                        preparedEntities.values()) {
                    entityRecords.addAll(values);
                }
                if (randomFork != null) {
                    if (!worldGenRegionRandom.commitIfExactPredecessor(
                            randomFork.predecessor, acceptedRandom)) {
                        throw new IllegalStateException(
                                "WorldGenRegion random changed during atomic structure commit");
                    }
                    randomFork.committed = true;
                }
            } catch (RuntimeException | Error failure) {
                // An issued capability is single-use. Closing the owning transaction also makes a
                // failed staged settlement unable to publish or replay a successor later.
                close();
                throw failure;
            }
        }

        /** Preserves the complete immutable StructureBatch field and encounter order. */
        private static List<?> orderedMutations(Mc263FeaturesRegion.StructureBatch batch) {
            ArrayList<Object> mutations = new ArrayList<>();
            mutations.addAll(batch.blocks());
            mutations.addAll(batch.loot());
            mutations.addAll(batch.archaeology());
            mutations.addAll(batch.blockEntities());
            mutations.addAll(batch.entities());
            mutations.addAll(batch.fluidTicks());
            mutations.addAll(batch.postprocessMarks());
            mutations.addAll(batch.spawners());
            mutations.addAll(batch.blockTicks());
            mutations.addAll(batch.bees());
            return List.copyOf(mutations);
        }

        private synchronized void requireBinding(Mc263StructureCarrier initialCarrier,
                Mc263StructureCarrier.ChunkReferences initialReferences, SourceClip clip,
                Mc263FeatureDispatcher.StructurePlacementContext dispatcher,
                ProductionContextAuthority authority) {
            requireActive();
            if (upstream.carrier() != initialCarrier
                    || authority == null || authority != upstream.productionContextAuthority()
                    || !SourceClip.chunk(initialReferences.chunkX(), initialReferences.chunkZ())
                            .equals(clip)
                    || upstream.source(initialReferences.chunkX(), initialReferences.chunkZ())
                            .references() != initialReferences
                    || dispatcher.region() != region
                    || dispatcher.sourceChunkX() != initialReferences.chunkX()
                    || dispatcher.sourceChunkZ() != initialReferences.chunkZ()) {
                throw new IllegalArgumentException(
                        "mixed production context/STR/WGR placement transaction");
            }
        }

        private synchronized LootProductionContext productionContext(StructurePlacementInput input,
                int blockX, int blockY, int blockZ, String lootTable) {
            requireBinding(input.initialCarrier(), input.initialReferences(), input.clip(),
                    input.dispatcher(), input.productionContextAuthority());
            if (!input.clip().contains(blockX, blockY, blockZ)) {
                throw new IllegalArgumentException(
                        "production-context-clip-escape structure="
                                + String.valueOf(input.dispatcher().structureKey())
                                        .replaceAll("[^A-Za-z0-9_.:/-]", "_")
                                        .substring(0, Math.min(96, String.valueOf(
                                                input.dispatcher().structureKey())
                                                .replaceAll("[^A-Za-z0-9_.:/-]", "_").length()))
                                + " sourceChunk=" + input.dispatcher().sourceChunkX() + ","
                                + input.dispatcher().sourceChunkZ()
                                + " block=" + blockX + "," + blockY + "," + blockZ
                                + " lootTable=" + String.valueOf(lootTable)
                                        .replaceAll("[^A-Za-z0-9_.:/-]", "_")
                                        .substring(0, Math.min(128, String.valueOf(lootTable)
                                                .replaceAll("[^A-Za-z0-9_.:/-]", "_").length()))
                                + " clip=" + input.clip().minX() + "," + input.clip().minY()
                                + "," + input.clip().minZ() + ":" + input.clip().maxX() + ","
                                + input.clip().maxY() + "," + input.clip().maxZ());
            }
            return upstream.productionContextAuthority().locate(
                    region, blockX, blockY, blockZ, lootTable);
        }

        private synchronized void close() {
            active = false;
            successorTransaction.close();
        }

        private void requireActive() {
            if (!active) {
                throw new IllegalStateException("stale structure placement transaction");
            }
        }

        private synchronized void requireFork(WorldGenRegionRandomFork fork) {
            requireActive();
            if (fork.owner != this) {
                throw new IllegalArgumentException(
                        "foreign WorldGenRegion random fork transaction");
            }
        }

        private Mc263StructureCarrier validateSuccessor(Mc263StructureCarrier predecessor,
                Mc263StructureCarrier successor) {
            Objects.requireNonNull(predecessor, "structure settlement predecessor");
            Objects.requireNonNull(successor, "structure settlement successor");
            if (currentCarrier != predecessor) {
                throw new IllegalArgumentException("stale structure settlement predecessor: "
                        + "STR successor predecessor object is stale or foreign");
            }
            // AGENTS rule 10l: a settlement hands back the very registry and reference list it was
            // given, so the common case is one reference comparison, not a walk of forty
            // definitions and a window of reference chunks per structure commit. The walks stay
            // for the case they exist for — a successor that really did rebuild them.
            if (currentCarrier.registry() != successor.registry()
                    && !currentCarrier.registry().definitions().equals(
                            successor.registry().definitions())) {
                throw new IllegalArgumentException("structure settlement changed pinned registry");
            }
            if (currentCarrier.referenceChunks() != successor.referenceChunks()
                    && !currentCarrier.referenceChunks().equals(successor.referenceChunks())) {
                throw new IllegalArgumentException("structure settlement changed reference maps");
            }
            // Force strict canonical encoding before publishing the successor to later sources.
            return successor.strictlyDecoded();
        }

        private LinkedHashMap<Mc263FeaturesRegion.DestinationPosition,
                Mc263FinalChunkSidecars.BlockEntity> prepareBlockEntities(
                        Mc263FeaturesRegion.StructureBatch batch) {
            Objects.requireNonNull(batch, "structure batch");
            LinkedHashMap<Mc263FeaturesRegion.DestinationPosition,
                    Mc263FinalChunkSidecars.BlockEntity> prepared = new LinkedHashMap<>();
            for (Mc263FeaturesRegion.StructureBentEvidence value : batch.blockEntities()) {
                int chunkX = Math.floorDiv(value.blockX(), Blocks.CHUNK_X);
                int chunkZ = Math.floorDiv(value.blockZ(), Blocks.CHUNK_Z);
                int packed = Blocks.blockIndex(Math.floorMod(value.blockX(), Blocks.CHUNK_X),
                        value.blockY(), Math.floorMod(value.blockZ(), Blocks.CHUNK_Z));
                var destination = new Mc263FeaturesRegion.DestinationPosition(
                        chunkX, chunkZ, packed);
                var entity = new Mc263FinalChunkSidecars.BlockEntity(packed,
                        value.blockIdentity(), value.entityType(), value.canonicalNbt());
                Mc263FinalChunkSidecars.BlockEntity existing = prepared.putIfAbsent(
                        destination, entity);
                if (existing != null && !existing.equals(entity)) {
                    throw new IllegalArgumentException("conflicting structure BENT contribution");
                }
                Mc263FinalChunkSidecars.BlockEntity committed = blockEntities.get(destination);
                if (committed != null && !committed.equals(entity)) {
                    throw new IllegalArgumentException("structure BENT conflicts across settlements");
                }
            }
            return prepared;
        }

        private LinkedHashMap<Mc263FeaturesRegion.DestinationPosition,
                List<Mc263FinalChunkSidecars.StructureEntity>> prepareEntities(
                        Mc263FeaturesRegion.StructureBatch batch) {
            LinkedHashMap<Mc263FeaturesRegion.DestinationPosition,
                    List<Mc263FinalChunkSidecars.StructureEntity>> prepared =
                    new LinkedHashMap<>();
            LinkedHashSet<Mc263FinalChunkSidecars.StructureEntity> batchRecords =
                    new LinkedHashSet<>();
            for (Mc263FinalChunkSidecars.StructureEntity entity : batch.entities()) {
                Mc263FeaturesRegion.DestinationPosition destination = entityDestination(entity);
                if (!batchRecords.add(entity)) {
                    throw new IllegalArgumentException("duplicate structure ENTS at "
                            + entityPosition(entity));
                }
                if (entityRecords.contains(entity)) {
                    throw new IllegalArgumentException(
                            "duplicate structure ENTS across settlements at "
                                    + entityPosition(entity));
                }
                prepared.computeIfAbsent(destination, ignored -> new ArrayList<>()).add(entity);
            }
            return prepared;
        }

        private List<Mc263FinalChunkSidecars.BlockEntity> centerBlockEntities(
                int chunkX, int chunkZ,
                List<Mc263FinalChunkSidecars.BlockEntity> callerBlockEntities) {
            java.util.TreeMap<Integer, Mc263FinalChunkSidecars.BlockEntity> merged =
                    new java.util.TreeMap<>();
            for (var entry : blockEntities.entrySet()) {
                if (entry.getKey().chunkX() == chunkX && entry.getKey().chunkZ() == chunkZ) {
                    merged.put(entry.getKey().packedPosition(), entry.getValue());
                }
            }
            for (Mc263FinalChunkSidecars.BlockEntity entity :
                    List.copyOf(callerBlockEntities)) {
                Mc263FinalChunkSidecars.BlockEntity existing = merged.putIfAbsent(
                        entity.packed(), entity);
                if (existing != null && !existing.equals(entity)) {
                    throw new IllegalArgumentException(
                            "caller BENT conflicts with generated structure BENT");
                }
            }
            return List.copyOf(merged.values());
        }

        private List<Mc263FinalChunkSidecars.StructureEntity> centerEntities(
                int chunkX, int chunkZ,
                List<Mc263FinalChunkSidecars.StructureEntity> callerEntities) {
            ArrayList<Mc263FinalChunkSidecars.StructureEntity> merged = new ArrayList<>();
            LinkedHashSet<Mc263FinalChunkSidecars.StructureEntity> records =
                    new LinkedHashSet<>();
            for (Mc263FinalChunkSidecars.StructureEntity entity : orderedEntities) {
                Mc263FeaturesRegion.DestinationPosition destination = entityDestination(entity);
                if (destination.chunkX() != chunkX || destination.chunkZ() != chunkZ) continue;
                merged.add(entity);
                records.add(entity);
            }
            for (Mc263FinalChunkSidecars.StructureEntity entity : callerEntities) {
                requireEntityInChunk(entity, chunkX, chunkZ, "caller ENTS");
                if (!records.add(entity)) {
                    throw new IllegalArgumentException(
                            "caller ENTS duplicates generated structure ENTS");
                }
                merged.add(entity);
            }
            return List.copyOf(merged);
        }
    }

    private static Mc263FeaturesRegion.DestinationPosition entityDestination(
            Mc263FinalChunkSidecars.StructureEntity entity) {
        int blockX = entityBlockCoordinate(entity.x(), "structure entity X");
        int blockY = entityBlockCoordinate(entity.y(), "structure entity Y");
        int blockZ = entityBlockCoordinate(entity.z(), "structure entity Z");
        if (blockY < Blocks.MIN_Y || blockY > Blocks.MAX_Y) {
            throw new IllegalArgumentException("structure ENTS Y outside generation range: "
                    + entity.y());
        }
        return new Mc263FeaturesRegion.DestinationPosition(
                Math.floorDiv(blockX, Blocks.CHUNK_X), Math.floorDiv(blockZ, Blocks.CHUNK_Z),
                Blocks.blockIndex(Math.floorMod(blockX, Blocks.CHUNK_X), blockY,
                        Math.floorMod(blockZ, Blocks.CHUNK_Z)));
    }

    private static void requireEntityInChunk(Mc263FinalChunkSidecars.StructureEntity entity,
            int chunkX, int chunkZ, String lane) {
        Mc263FeaturesRegion.DestinationPosition destination = entityDestination(entity);
        if (destination.chunkX() != chunkX || destination.chunkZ() != chunkZ) {
            throw new IllegalArgumentException(lane + " escaped final chunk at "
                    + entityPosition(entity));
        }
    }

    private static int entityBlockCoordinate(double coordinate, String name) {
        double floor = Math.floor(coordinate);
        if (floor < Integer.MIN_VALUE || floor > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " lies outside integer world coordinates");
        }
        return (int) floor;
    }

    private static String entityPosition(Mc263FinalChunkSidecars.StructureEntity entity) {
        return entity.x() + "," + entity.y() + "," + entity.z();
    }

    record SourceClip(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean contains(int blockX, int blockY, int blockZ) {
            return blockX >= minX && blockX <= maxX
                    && blockY >= minY && blockY <= maxY
                    && blockZ >= minZ && blockZ <= maxZ;
        }

        static SourceClip chunk(int chunkX, int chunkZ) {
            int minX = Math.multiplyExact(chunkX, Blocks.CHUNK_X);
            int minZ = Math.multiplyExact(chunkZ, Blocks.CHUNK_Z);
            return new SourceClip(minX, Blocks.MIN_Y, minZ,
                    Math.addExact(minX, Blocks.CHUNK_X - 1), Blocks.MAX_Y,
                    Math.addExact(minZ, Blocks.CHUNK_Z - 1));
        }
    }

    record SourceInput(int chunkX, int chunkZ,
                       Mc263StructureCarrier.ChunkReferences references, SourceClip clip) {}

    public static final class UpstreamProduct {
        private final Mc263PostCarversFeaturesRegionBuilder.RegionInput regionInput;
        private final Mc263StructureCarrier carrier;
        private final Mc263WorldGenRegionRandom.State worldGenRegionRandomState;
        private final ProductionContextAuthority productionContextAuthority;
        private final List<SourceInput> sources;
        private final Map<Long, SourceInput> sourceByChunk;
        /** Built on first use: production itself only needs the successor receipt. */
        private volatile byte[] provenanceReceipt;

        private UpstreamProduct(Mc263PostCarversFeaturesRegionBuilder.RegionInput regionInput,
                Mc263StructureCarrier carrier,
                Mc263WorldGenRegionRandom.State worldGenRegionRandomState,
                ProductionContextAuthority productionContextAuthority) {
            this.regionInput = Objects.requireNonNull(regionInput, "post-CARVERS region input");
            this.carrier = Objects.requireNonNull(carrier, "STR263C1 carrier");
            this.worldGenRegionRandomState = Objects.requireNonNull(worldGenRegionRandomState,
                    "WorldGenRegion random predecessor state");
            this.productionContextAuthority = Objects.requireNonNull(productionContextAuthority,
                    "production context authority");
            validateRegistry(carrier.registry());
            this.productionContextAuthority.bind(this, upstreamProductBinding(
                    regionInput, carrier, worldGenRegionRandomState));
            ArrayList<SourceInput> ordered = new ArrayList<>(Mc263FeaturesRegion.ACTIVE_SOURCE_COUNT);
            LinkedHashMap<Long, SourceInput> indexed = new LinkedHashMap<>();
            int targetX = regionInput.target().chunkX();
            int targetZ = regionInput.target().chunkZ();
            for (int chunkX = targetX - 1; chunkX <= targetX + 1; chunkX++) {
                for (int chunkZ = targetZ - 1; chunkZ <= targetZ + 1; chunkZ++) {
                    int sourceChunkX = chunkX;
                    int sourceChunkZ = chunkZ;
                    Mc263StructureCarrier.ChunkReferences references = carrier
                            .referenceChunk(sourceChunkX, sourceChunkZ)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "missing explicit structure references for source "
                                            + sourceChunkX + "," + sourceChunkZ));
                    SourceInput source = new SourceInput(sourceChunkX, sourceChunkZ, references,
                            SourceClip.chunk(sourceChunkX, sourceChunkZ));
                    ordered.add(source);
                    indexed.put(Mc263StructureCarrier.packChunk(chunkX, chunkZ), source);
                }
            }
            sources = List.copyOf(ordered);
            sourceByChunk = Map.copyOf(indexed);
        }

        public static UpstreamProduct bind(
                Mc263PostCarversFeaturesRegionBuilder.RegionInput regionInput,
                Mc263StructureCarrier carrier,
                Mc263WorldGenRegionRandom.State worldGenRegionRandomState) {
            return new UpstreamProduct(regionInput, carrier, worldGenRegionRandomState,
                    ProductionContextAuthority.absent());
        }

        public static UpstreamProduct bind(
                Mc263PostCarversFeaturesRegionBuilder.RegionInput regionInput,
                Mc263StructureCarrier carrier,
                Mc263WorldGenRegionRandom.State worldGenRegionRandomState,
                ProductionContextAuthority productionContextAuthority) {
            return new UpstreamProduct(regionInput, carrier, worldGenRegionRandomState,
                    productionContextAuthority);
        }

        long worldSeed() { return regionInput.target().worldSeed(); }
        Mc263PostCarversFeaturesRegionBuilder.RegionInput regionInput() { return regionInput; }
        Mc263StructureCarrier carrier() { return carrier; }
        Mc263WorldGenRegionRandom.State worldGenRegionRandomState() {
            return worldGenRegionRandomState;
        }
        ProductionContextAuthority productionContextAuthority() {
            return productionContextAuthority;
        }
        List<SourceInput> sources() { return sources; }
        byte[] provenanceReceipt() {
            byte[] receipt = provenanceReceipt;
            if (receipt == null) {
                receipt = provenance(regionInput, carrier, worldGenRegionRandomState,
                        worldGenRegionRandomState, productionContextAuthority.receipt());
                provenanceReceipt = receipt;
            }
            return receipt.clone();
        }
        byte[] provenanceReceipt(Mc263StructureCarrier successorCarrier,
                Mc263WorldGenRegionRandom.State successorState) {
            Mc263StructureCarrier canonicalSuccessor = Objects.requireNonNull(
                    successorCarrier, "canonical STR successor").strictlyDecoded();
            if (!canonicalSuccessor.hasSameReceipt(successorCarrier)) {
                throw new IllegalArgumentException("canonical STR successor receipt changed");
            }
            return provenance(regionInput, canonicalSuccessor, worldGenRegionRandomState,
                    Objects.requireNonNull(successorState,
                            "WorldGenRegion random successor state"),
                    productionContextAuthority.receipt());
        }

        SourceInput source(int chunkX, int chunkZ) {
            SourceInput source = sourceByChunk.get(Mc263StructureCarrier.packChunk(chunkX, chunkZ));
            if (source == null) throw new IllegalStateException("dispatcher escaped nine source references");
            return source;
        }
    }

    private static void validateRegistry(Mc263StructureCarrier.Registry registry) {
        List<Mc263StructureCarrier.StructureDefinition> definitions = registry.definitions();
        List<Mc263StructureIndexReceipt.Entry> entries = Mc263StructureIndexReceipt.entries();
        if (definitions.size() != entries.size()) {
            throw new IllegalArgumentException("STR263C1 registry is not the pinned 52-entry registry");
        }
        for (int index = 0; index < entries.size(); index++) {
            Mc263StructureCarrier.StructureDefinition definition = definitions.get(index);
            Mc263StructureIndexReceipt.Entry entry = entries.get(index);
            if (definition.registryOrdinal() != index
                    || !definition.structureId().equals(entry.key())
                    || definition.decorationStep() != entry.step()) {
                throw new IllegalArgumentException("STR263C1 registry/schedule mismatch at " + index);
            }
        }
    }

    private static byte[] provenance(
            Mc263PostCarversFeaturesRegionBuilder.RegionInput regionInput,
            Mc263StructureCarrier carrier,
            Mc263WorldGenRegionRandom.State randomPredecessor,
            Mc263WorldGenRegionRandom.State randomSuccessor,
            byte[] productionContextAuthorityReceipt) {
        byte[] postCarvers = Mc263PostCarversFeaturesRegionBuilder.receiptView(regionInput);
        byte[] structures = carrier.receiptBytes();
        byte[] predecessorReceipt = randomPredecessor.receipt();
        byte[] successorReceipt = randomSuccessor.receipt();
        // Same bytes as the former nested stream build, written once into an exact-size array.
        int sourceLength = Math.addExact(Math.addExact(postCarvers.length,
                PRODUCTION_CONTEXT_PROVENANCE_MAGIC.length + Integer.BYTES
                        + productionContextAuthorityReceipt.length
                        + WORLDGEN_RANDOM_PROVENANCE_MAGIC.length + Integer.BYTES * 2),
                predecessorReceipt.length + successorReceipt.length);
        java.nio.ByteBuffer output = java.nio.ByteBuffer.allocate(Math.addExact(
                PROVENANCE_MAGIC.length + Integer.BYTES * 2 + sourceLength, structures.length));
        output.put(PROVENANCE_MAGIC);
        output.putInt(sourceLength);
        output.put(postCarvers);
        output.put(PRODUCTION_CONTEXT_PROVENANCE_MAGIC);
        output.putInt(productionContextAuthorityReceipt.length);
        output.put(productionContextAuthorityReceipt);
        output.put(WORLDGEN_RANDOM_PROVENANCE_MAGIC);
        output.putInt(predecessorReceipt.length); output.put(predecessorReceipt);
        output.putInt(successorReceipt.length); output.put(successorReceipt);
        output.putInt(structures.length); output.put(structures);
        if (output.hasRemaining()) throw new IllegalStateException("canonical provenance length drift");
        return output.array();
    }

    private static byte[] upstreamProductBinding(
            Mc263PostCarversFeaturesRegionBuilder.RegionInput regionInput,
            Mc263StructureCarrier carrier,
            Mc263WorldGenRegionRandom.State worldGenRegionRandomState) {
        try {
            // Hashes the same stream the former byte-array build produced, without materializing it.
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            DataOutputStream out = new DataOutputStream(new java.security.DigestOutputStream(
                    java.io.OutputStream.nullOutputStream(), digest));
            out.write("PCA263-UPSTREAM-V1\0".getBytes(StandardCharsets.US_ASCII));
            byte[] postCarvers = Mc263PostCarversFeaturesRegionBuilder.receiptView(regionInput);
            byte[] structures = carrier.receiptBytes();
            byte[] random = worldGenRegionRandomState.receipt();
            out.writeInt(postCarvers.length); out.write(postCarvers);
            out.writeInt(structures.length); out.write(structures);
            out.writeInt(random.length); out.write(random);
            out.flush();
            return digest.digest();
        } catch (IOException | java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("production context product binding failed",
                    impossible);
        }
    }

    private static Mc263WorldGenRegionRandom randomFromState(
            Mc263WorldGenRegionRandom.State state) {
        return Mc263WorldGenRegionRandom.fromState(state.lo(), state.hi(), state.drawCount(),
                state.gaussianPresent(), state.gaussianBits());
    }
}
