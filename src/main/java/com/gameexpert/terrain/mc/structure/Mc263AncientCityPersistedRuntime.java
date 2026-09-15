package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.feature.Mc263StructureBatchBridge;
import com.gameexpert.terrain.mc.feature.Mc263WorldGenRegionRandom;
import com.gameexpert.terrain.mc.structure.Mc263AncientCityProductionAdapter.PersistedDispatchPlan;
import com.gameexpert.terrain.mc.structure.Mc263AncientCityProductionAdapter.PersistedExecutionRequest;
import com.gameexpert.terrain.mc.structure.Mc263AncientCityProductionAdapter.PersistedFeaturePlacement;
import com.gameexpert.terrain.mc.structure.Mc263AncientCityProductionAdapter.PersistedPlacement;
import com.gameexpert.terrain.mc.structure.Mc263AncientCityProductionAdapter.PersistedSession;
import com.gameexpert.terrain.mc.structure.Mc263AncientCityProductionAdapter.PersistedTemplatePlacement;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Production persisted-start runtime for the pinned Minecraft 26.3 Ancient City authority.
 *
 * <p>The adapter authenticates and decodes the persisted start before this runtime is entered.
 * This class binds those typed placements to one replay-aware environment transaction, isolates
 * the caller-owned {@link Mc263WorldGenRegionRandom} on its native transaction fork, and publishes
 * the RNG successor only after a complete schema-4 batch has been prepared and validated. The
 * environment owns durable replay storage and the unpublished placement fork; it never reparses
 * raw start bytes.</p>
 */
public final class Mc263AncientCityPersistedRuntime
        implements Mc263AncientCityProductionAdapter.Runtime {
    private static final String FEATURE_KEY = "minecraft:sculk_patch_ancient_city";
    private static final String CAP_ATOMIC = "atomic-replay-rng";
    private static final String CAP_FOREIGN = "foreign-destinations";
    private static final String CAP_CALLER_RNG = "caller-rng-continuation";
    private static final String CAP_FINAL_SCHEMA = "final-schema-4";
    /**
     * Block-entity surface of the post-placement {@code minecraft:sculk_patch_ancient_city}
     * configured feature. 26.3 lands Ancient City sculk through that feature, not through the
     * template palette, so its BENT identities are authenticated by
     * {@code Mc263AncientCityProductionAuthority#canonicalFeatureBlockEntityNbt}, which admits
     * exactly these three types, rather than by the template command grammar.
     */
    private static final Set<String> FEATURE_BLOCK_ENTITY_TYPES = Set.of(
            "minecraft:sculk_catalyst", "minecraft:sculk_sensor", "minecraft:sculk_shrieker");

    private final Environment environment;
    private final Mc263WorldGenRegionRandom callerRandom;
    private final Mc263AncientCityGrammar grammar;
    private final Map<String, Mc263AncientCityGrammar.Template> templates;
    private final Map<String, Mc263AncientCityGrammar.ProcessorListSpec> processorLists;
    private final Map<String, Mc263AncientCityGrammar.ProcessorSpec> processors;
    private final Set<String> exactStates;
    private final Set<String> exactBlocks;
    private final Set<String> lootTables;
    private final Set<String> blockEntityTypes;

    public Mc263AncientCityPersistedRuntime(Environment environment,
            Mc263WorldGenRegionRandom callerRandom) {
        this.environment = Objects.requireNonNull(environment, "Ancient City environment");
        this.callerRandom = Objects.requireNonNull(
                callerRandom, "Ancient City caller-owned WorldGenRegionRandom");
        grammar = Mc263AncientCityProductionAuthority.load();
        templates = indexTemplates(grammar.templatesInEncounterOrder());
        processorLists = indexProcessorLists(grammar.processorListsInEncounterOrder());
        processors = indexProcessors(grammar.processorSemanticsInEncounterOrder());
        exactStates = Set.copyOf(grammar.statesInEncounterOrder());
        exactBlocks = exactStates.stream().map(Mc263AncientCityPersistedRuntime::blockIdentity)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        lootTables = collectLootTables(grammar.templatesInEncounterOrder());
        blockEntityTypes = collectBlockEntityTypes(grammar.templatesInEncounterOrder());
        validateAuthorityPrograms();
    }

    @Override
    public Mc263AncientCityProductionAdapter.Session openSession(
            Mc263AncientCitySettlement.ExecutionRequest request,
            Mc263AncientCityProductionAdapter.DispatchPlan plan) {
        throw new UnsupportedOperationException(
                "Ancient City persisted runtime does not execute generated starts");
    }

    @Override public boolean supportsPersistedStartSession() { return true; }

    @Override
    public PersistedSession openPersistedSession(PersistedExecutionRequest request,
            PersistedDispatchPlan plan) {
        validatePlan(request, plan);
        SessionIdentity identity = identity(plan);
        Replay replay = environment.replay(identity.transactionKey());
        if (replay != null) {
            require(replay.identity().equals(identity),
                    "Ancient City persisted replay fingerprint conflict");
            validateBatch(plan, replay.batch());
            return new RuntimeSession(plan, identity, replay, null, null, null);
        }
        Mc263WorldGenRegionRandom.State predecessor = callerRandom.snapshot();
        Mc263WorldGenRegionRandom fork = callerRandom.forkForTransaction();
        Transaction transaction = Objects.requireNonNull(
                environment.open(identity, request, plan, fork),
                "Ancient City persisted environment transaction");
        return new RuntimeSession(plan, identity, null, transaction, predecessor, fork);
    }

    @Override
    public boolean supportsVersion(String versionId, String serverVersion,
            String javaRuntimeVersion, String innerServerSha256, String outerServerSha256) {
        Mc263AncientCitySettlement.EvidenceIdentity evidence =
                Mc263AncientCitySettlement.evidenceIdentity();
        return evidence.versionId().equals(versionId)
                && evidence.serverVersion().equals(serverVersion)
                && evidence.javaRuntimeVersion().equals(javaRuntimeVersion)
                && evidence.innerServerSha256().equals(innerServerSha256)
                && evidence.outerServerSha256().equals(outerServerSha256);
    }

    @Override
    public boolean supportsSource(Mc263AncientCitySettlement.SourcePin source) {
        return Mc263AncientCitySettlement.evidenceIdentity().sources().contains(source);
    }

    @Override public boolean supportsExecutionCorpus(String sha256) {
        return Mc263AncientCitySettlement.evidenceIdentity().executionCorpusSha256().equals(sha256);
    }

    @Override public boolean supportsStartGraphCarrier(String carrierSha256, String jsonSha256) {
        Mc263AncientCitySettlement.EvidenceIdentity evidence =
                Mc263AncientCitySettlement.evidenceIdentity();
        return evidence.startGraphCarrierSha256().equals(carrierSha256)
                && evidence.startGraphJsonSha256().equals(jsonSha256);
    }

    @Override public boolean supportsFeatureCodecs(
            String placedFeatureCodecSha256, String configuredFeatureCodecSha256) {
        Mc263AncientCitySettlement.EvidenceIdentity evidence =
                Mc263AncientCitySettlement.evidenceIdentity();
        return evidence.placedFeatureCodecSha256().equals(placedFeatureCodecSha256)
                && evidence.configuredFeatureCodecSha256().equals(configuredFeatureCodecSha256);
    }

    @Override public boolean supportsFinalChunkSchema(int schema) {
        return schema == Mc263AncientCitySettlement.FINAL_CHUNK_SCHEMA
                && environment.supports(new Capability("runtime", CAP_FINAL_SCHEMA));
    }
    @Override public boolean supportsAtomicReplayableSettlementWithRng() {
        return environment.supports(new Capability("runtime", CAP_ATOMIC));
    }
    @Override public boolean supportsForeignDestinations() {
        return environment.supports(new Capability("runtime", CAP_FOREIGN));
    }
    @Override public boolean supportsCallerRngContinuation() {
        return environment.supports(new Capability("runtime", CAP_CALLER_RNG));
    }
    @Override public boolean supportsClip(Mc263AncientCitySettlement.Clip clip) {
        return environment.supportsClip(clip);
    }
    @Override public boolean supportsSidecarLane(Mc263AncientCitySettlement.SidecarLane lane) {
        return lane != null && environment.supports(new Capability("lane", lane.name()));
    }

    @Override
    public boolean supportsTemplate(String template,
            Mc263AncientCityProducer.TemplateStatus status) {
        Mc263AncientCityGrammar.Template value = templates.get(template);
        return value != null && status == producerStatus(value.status())
                && environment.supports(new Capability("template", template));
    }

    @Override public boolean supportsProcessorList(String processorIdentity) {
        return processorLists.containsKey(processorIdentity)
                && environment.supports(new Capability("processor-list", processorIdentity));
    }

    @Override public boolean supportsProcessorSemantic(String semanticIdentity,
            String runtimeClass) {
        Mc263AncientCityGrammar.ProcessorSpec value = processors.get(semanticIdentity);
        return value != null && value.runtimeClass().equals(runtimeClass)
                && environment.supports(new Capability("processor", semanticIdentity));
    }

    @Override public boolean supportsConfiguredFeature(String featureKey) {
        return FEATURE_KEY.equals(featureKey)
                && grammar.configuredFeature().registryKey().equals(featureKey)
                && environment.supports(new Capability("feature", featureKey));
    }

    @Override public boolean supportsExactState(String exactState) {
        return exactStates.contains(exactState)
                && environment.supports(new Capability("state", exactState));
    }

    @Override public boolean supportsLootTable(String lootTable) {
        return lootTables.contains(lootTable)
                && environment.supports(new Capability("loot", lootTable));
    }

    @Override public boolean supportsBlockEntity(String blockEntityType) {
        return blockEntityTypes.contains(blockEntityType)
                && environment.supports(new Capability("block-entity", blockEntityType));
    }

    @Override public boolean supportsCanonicalBlockEntityNbt(String blockEntityType,
            List<Mc263AncientCitySettlement.NbtTagSpec> tagsInCanonicalOrder) {
        return "minecraft:chest".equals(blockEntityType)
                && Mc263AncientCitySettlement.E3I5_CHEST_NBT_TAGS_IN_ORDER
                        .equals(tagsInCanonicalOrder)
                && environment.supports(new Capability("canonical-nbt", blockEntityType));
    }

    private final class RuntimeSession implements PersistedSession {
        private final PersistedDispatchPlan plan;
        private final SessionIdentity identity;
        private final Replay replay;
        private final Transaction transaction;
        private final Mc263WorldGenRegionRandom.State predecessor;
        private final Mc263WorldGenRegionRandom fork;
        private int nextPlacement;
        private State state = State.OPEN;

        private RuntimeSession(PersistedDispatchPlan plan, SessionIdentity identity, Replay replay,
                Transaction transaction, Mc263WorldGenRegionRandom.State predecessor,
                Mc263WorldGenRegionRandom fork) {
            this.plan = plan;
            this.identity = identity;
            this.replay = replay;
            this.transaction = transaction;
            this.predecessor = predecessor;
            this.fork = fork;
        }

        @Override public void placeTemplate(PersistedTemplatePlacement placement) {
            requireNext(placement);
            Mc263AncientCityGrammar.Template template = templates.get(placement.template());
            require(template != null
                            && template.status() == Mc263AncientCityGrammar.SourceStatus.PRESENT,
                    "unknown Ancient City persisted template program");
            Mc263AncientCityGrammar.ProcessorListSpec list =
                    processorLists.get(placement.processorList());
            require(list != null, "unknown Ancient City persisted processor-list program");
            ArrayList<Mc263AncientCityGrammar.ProcessorSpec> semantics = new ArrayList<>();
            for (String semantic : list.processorSemanticIdentitiesInOrder()) {
                Mc263AncientCityGrammar.ProcessorSpec processor = processors.get(semantic);
                require(processor != null,
                        "unknown Ancient City persisted processor semantic program");
                semantics.add(processor);
            }
            if (replay == null) {
                transaction.placeTemplate(
                        placement, template, list, List.copyOf(semantics), fork);
            }
            nextPlacement++;
        }

        @Override public void placeFeature(PersistedFeaturePlacement placement) {
            requireNext(placement);
            require(FEATURE_KEY.equals(placement.featureKey())
                            && FEATURE_KEY.equals(grammar.configuredFeature().registryKey()),
                    "unknown Ancient City persisted configured-feature program");
            Mc263AncientCitySculkExecutor.requireAuthenticatedFeature(grammar.configuredFeature());
            if (replay == null) {
                transaction.placeFeature(placement, grammar.configuredFeature(), fork);
            }
            nextPlacement++;
        }

        @Override public Mc263StructureBatchBridge.Batch finish() {
            require(state == State.OPEN, "Ancient City persisted session is not open");
            require(nextPlacement == plan.placements().size(),
                    "Ancient City persisted placement sequence ended early");
            if (replay != null) {
                state = State.FINISHED;
                return replay.batch();
            }
            Mc263StructureBatchBridge.Batch batch = Objects.requireNonNull(
                    transaction.prepare(), "Ancient City persisted prepared batch");
            validateBatch(plan, batch);
            Mc263WorldGenRegionRandom.State successor = fork.snapshot();
            require(callerRandom.commitIfExactPredecessor(predecessor, fork),
                    "Ancient City caller RNG predecessor changed concurrently");
            try {
                transaction.commit(new Replay(identity, batch));
                state = State.FINISHED;
                return batch;
            } catch (RuntimeException | Error failure) {
                if (!callerRandom.rollbackIfExactSuccessor(predecessor, successor)) {
                    failure.addSuppressed(new IllegalStateException(
                            "Ancient City caller RNG rollback lost exact-successor race"));
                }
                throw failure;
            }
        }

        @Override public void abort() {
            if (state != State.OPEN) return;
            state = State.ABORTED;
            if (transaction != null) transaction.abort();
        }

        private void requireNext(PersistedPlacement placement) {
            require(state == State.OPEN, "Ancient City persisted session is not open");
            require(nextPlacement < plan.placements().size()
                            && plan.placements().get(nextPlacement).equals(placement),
                    "Ancient City persisted placement order/identity drift");
        }
    }

    private void validatePlan(PersistedExecutionRequest request, PersistedDispatchPlan plan) {
        Objects.requireNonNull(request, "Ancient City persisted request");
        Objects.requireNonNull(plan, "Ancient City persisted plan");
        require(request.start().equals(plan.start()) && request.rawStart().equals(plan.rawStart())
                        && request.references().equals(plan.references())
                        && request.clip().equals(plan.clip()),
                "Ancient City persisted request/plan identity drift");
        int previousPiece = -1;
        for (int ordinal : plan.intersectingPieceOrdinals()) {
            require(ordinal > previousPiece,
                    "Ancient City persisted intersecting-piece order drift");
            previousPiece = ordinal;
        }
        int placementPiece = -1;
        for (PersistedPlacement placement : plan.placements()) {
            require(plan.intersectingPieceOrdinals().contains(placement.pieceOrdinal())
                            && placement.pieceOrdinal() >= placementPiece
                            && placement.clip().equals(plan.clip()),
                    "Ancient City persisted placement escaped parsed plan");
            placementPiece = placement.pieceOrdinal();
            if (placement instanceof PersistedTemplatePlacement template) {
                Mc263AncientCityGrammar.Template program = templates.get(template.template());
                require(program != null
                                && program.status() == Mc263AncientCityGrammar.SourceStatus.PRESENT
                                && processorLists.containsKey(template.processorList()),
                        "unknown Ancient City persisted template/processor program");
            } else if (placement instanceof PersistedFeaturePlacement feature) {
                require(FEATURE_KEY.equals(feature.featureKey()),
                        "unknown Ancient City persisted feature program");
            } else {
                throw new IllegalArgumentException(
                        "unknown Ancient City persisted placement payload");
            }
        }
    }

    private void validateBatch(PersistedDispatchPlan plan, Mc263StructureBatchBridge.Batch batch) {
        long owner = Mc263StructureCarrier.packChunk(
                plan.start().originChunkX(), plan.start().originChunkZ());
        HashSet<BlockPos> positions = new HashSet<>();
        for (Mc263StructureBatchBridge.BlockWrite block : batch.blocks()) {
            require(environment.supports(new Capability("state", block.exactState()))
                            && block.owner() == owner,
                    "unknown Ancient City persisted block state/owner: "
                            + block.exactState() + " owner=" + block.owner()
                            + " expected=" + owner);
            require(positions.add(new BlockPos(block.blockX(), block.blockY(), block.blockZ())),
                    "duplicate Ancient City persisted final block");
        }
        for (Mc263StructureBatchBridge.Loot loot : batch.loot()) {
            require(lootTables.contains(loot.lootTable()),
                    "unknown Ancient City persisted loot payload");
        }
        for (Mc263StructureBatchBridge.BentEvidence bent : batch.blockEntities()) {
            byte[] nbt = bent.canonicalNbt();
            boolean featureBent = FEATURE_BLOCK_ENTITY_TYPES.contains(bent.entityType())
                    && FEATURE_BLOCK_ENTITY_TYPES.contains(bent.blockIdentity());
            require((featureBent
                            || (exactBlocks.contains(bent.blockIdentity())
                                    && blockEntityTypes.contains(bent.entityType())))
                            && nbt.length >= 4 && nbt[0] == 10 && nbt[nbt.length - 1] == 0
                            && environment.acceptsCanonicalPayload(bent),
                    "unknown Ancient City persisted block-entity payload");
        }
        for (int index = 0; index < batch.fluidTicks().size(); index++) {
            Mc263StructureBatchBridge.FluidTick tick = batch.fluidTicks().get(index);
            require("minecraft:water".equals(tick.fluidKey()) && tick.delay() >= 0
                            && tick.priority() == 0 && tick.subTickOrder() == index,
                    "unknown Ancient City persisted fluid-tick payload");
        }
        // The Ancient City configured feature is the one lane that marks positions for
        // postprocessing (SculkPatchFeature -> WorldGenLevel#markPosForPostProcessing). The marks
        // are carried through to the canonical POST authorities exactly as the other structure
        // lanes carry them, so they are validated here instead of being rejected: every mark must
        // name a block this batch owns. Repeated marks on one position are deliberately kept in
        // insertion order, because ProtoChunk stores them in a per-section list, not a set.
        for (Mc263StructureBatchBridge.PostprocessMark mark : batch.postprocessMarks()) {
            BlockPos at = new BlockPos(mark.blockX(), mark.blockY(), mark.blockZ());
            require(positions.contains(at),
                    "Ancient City postprocess mark escaped the persisted batch writes at " + at);
        }
        require(batch.archaeology().isEmpty() && batch.entities().isEmpty()
                        && batch.spawners().isEmpty() && batch.blockTicks().isEmpty()
                        && batch.bees().isEmpty(),
                "unknown Ancient City persisted non-authority sidecar payload");
    }

    private void validateAuthorityPrograms() {
        require(FEATURE_KEY.equals(grammar.configuredFeature().registryKey()),
                "unknown Ancient City configured-feature authority");
        for (Mc263AncientCityGrammar.Template template : templates.values()) {
            for (Mc263AncientCityGrammar.Command command : template.commands()) {
                if (command instanceof Mc263AncientCityGrammar.Bent bent) {
                    Mc263AncientCityGrammar.BentPayload payload = bent.payload();
                    require(payload instanceof Mc263AncientCityGrammar.ComparatorOutput
                                    || payload instanceof Mc263AncientCityGrammar.EmptySculkSensor
                                    || payload instanceof Mc263AncientCityGrammar.EmptyLectern
                                    || payload instanceof Mc263AncientCityGrammar.EmptySkeletonSkull
                                    || payload instanceof Mc263AncientCityGrammar.EmptyCampfire
                                    || payload instanceof Mc263AncientCityGrammar.FixedChestContents
                                    || payload instanceof Mc263AncientCityGrammar.FurnacePayload,
                            "unknown Ancient City block-entity authority payload");
                }
            }
        }
    }

    private SessionIdentity identity(PersistedDispatchPlan plan) {
        String transactionKey = plan.start().startKey() + "->" + plan.clip().chunkX()
                + "," + plan.clip().chunkZ();
        return new SessionIdentity(transactionKey, fingerprint(plan));
    }

    private static String fingerprint(PersistedDispatchPlan plan) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                writeString(out, "ANC263PR1");
                writeString(out, plan.start().startKey());
                out.writeInt(plan.start().originChunkX());
                out.writeInt(plan.start().originChunkZ());
                out.writeInt(plan.start().references());
                writeBytes(out, plan.rawStart().predecessorBinaryNbtCompound());
                writeBytes(out, plan.rawStart().successorBinaryNbtCompound());
                out.writeInt(plan.clip().chunkX()); out.writeInt(plan.clip().chunkZ());
                out.writeInt(plan.clip().minYInclusive());
                out.writeInt(plan.clip().maxYInclusive());
                out.writeInt(plan.placements().size());
                for (PersistedPlacement placement : plan.placements()) {
                    out.writeInt(placement.pieceOrdinal());
                    if (placement instanceof PersistedTemplatePlacement template) {
                        out.writeByte(1); writeString(out, template.template());
                        writeString(out, template.processorList());
                        writePlacement(out, template.originX(), template.originY(),
                                template.originZ(), template.rotation().name(),
                                template.pieceBoundingBox());
                    } else if (placement instanceof PersistedFeaturePlacement feature) {
                        out.writeByte(2); writeString(out, feature.featureKey());
                        writePlacement(out, feature.originX(), feature.originY(),
                                feature.originZ(), feature.rotation().name(),
                                feature.pieceBoundingBox());
                    }
                }
            }
            return sha256(bytes.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException(
                    "failed to fingerprint Ancient City persisted plan", impossible);
        }
    }

    private static void writePlacement(DataOutputStream out, int x, int y, int z,
            String rotation, Mc263StructureCarrier.BoundingBox box) throws IOException {
        out.writeInt(x); out.writeInt(y); out.writeInt(z); writeString(out, rotation);
        out.writeInt(box.minX()); out.writeInt(box.minY()); out.writeInt(box.minZ());
        out.writeInt(box.maxX()); out.writeInt(box.maxY()); out.writeInt(box.maxZ());
    }

    private static Map<String, Mc263AncientCityGrammar.Template> indexTemplates(
            List<Mc263AncientCityGrammar.Template> values) {
        LinkedHashMap<String, Mc263AncientCityGrammar.Template> result = new LinkedHashMap<>();
        for (Mc263AncientCityGrammar.Template value : values) {
            require(result.putIfAbsent(value.id(), value) == null,
                    "duplicate Ancient City template authority");
        }
        return Map.copyOf(result);
    }

    private static Map<String, Mc263AncientCityGrammar.ProcessorListSpec> indexProcessorLists(
            List<Mc263AncientCityGrammar.ProcessorListSpec> values) {
        HashMap<String, Mc263AncientCityGrammar.ProcessorListSpec> result = new HashMap<>();
        for (Mc263AncientCityGrammar.ProcessorListSpec value : values) {
            require(result.putIfAbsent(value.identity(), value) == null,
                    "duplicate Ancient City processor-list authority");
        }
        return Map.copyOf(result);
    }

    private static Map<String, Mc263AncientCityGrammar.ProcessorSpec> indexProcessors(
            List<Mc263AncientCityGrammar.ProcessorSpec> values) {
        HashMap<String, Mc263AncientCityGrammar.ProcessorSpec> result = new HashMap<>();
        for (Mc263AncientCityGrammar.ProcessorSpec value : values) {
            require(result.putIfAbsent(value.identity(), value) == null,
                    "duplicate Ancient City processor authority");
        }
        return Map.copyOf(result);
    }

    private static Set<String> collectLootTables(
            List<Mc263AncientCityGrammar.Template> templates) {
        HashSet<String> result = new HashSet<>();
        for (Mc263AncientCityGrammar.Template template : templates) {
            for (Mc263AncientCityGrammar.Command command : template.commands()) {
                if (command instanceof Mc263AncientCityGrammar.LootContainer loot) {
                    result.add(loot.lootTable());
                }
            }
        }
        return Set.copyOf(result);
    }

    private static Set<String> collectBlockEntityTypes(
            List<Mc263AncientCityGrammar.Template> templates) {
        HashSet<String> result = new HashSet<>();
        for (Mc263AncientCityGrammar.Template template : templates) {
            for (Mc263AncientCityGrammar.Command command : template.commands()) {
                if (command instanceof Mc263AncientCityGrammar.LootContainer loot) {
                    result.add(loot.blockEntityType());
                } else if (command instanceof Mc263AncientCityGrammar.Bent bent) {
                    result.add(bent.payload().blockEntityType());
                }
            }
        }
        return Set.copyOf(result);
    }

    private static Mc263AncientCityProducer.TemplateStatus producerStatus(
            Mc263AncientCityGrammar.SourceStatus status) {
        return status == Mc263AncientCityGrammar.SourceStatus.PRESENT
                ? Mc263AncientCityProducer.TemplateStatus.PRESENT
                : Mc263AncientCityProducer.TemplateStatus.EXPECTED_ABSENT;
    }

    private static String blockIdentity(String exactState) {
        int properties = exactState.indexOf('[');
        return properties < 0 ? exactState : exactState.substring(0, properties);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length); out.write(bytes);
    }
    private static void writeBytes(DataOutputStream out, byte[] value) throws IOException {
        out.writeInt(value.length); out.write(value);
    }
    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
    private static boolean hash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private enum State { OPEN, FINISHED, ABORTED }
    private record BlockPos(int x, int y, int z) { }

    public record Capability(String kind, String identity) {
        public Capability {
            require(kind != null && !kind.isBlank() && identity != null && !identity.isBlank(),
                    "Ancient City runtime capability is malformed");
        }
    }

    public record SessionIdentity(String transactionKey, String fingerprintSha256) {
        public SessionIdentity {
            require(transactionKey != null && !transactionKey.isBlank()
                            && hash(fingerprintSha256),
                    "Ancient City persisted session identity is malformed");
        }
    }

    public record Replay(SessionIdentity identity, Mc263StructureBatchBridge.Batch batch) {
        public Replay {
            Objects.requireNonNull(identity, "Ancient City persisted replay identity");
            Objects.requireNonNull(batch, "Ancient City persisted replay batch");
        }
    }

    /** Durable replay lookup plus one isolated new transaction. Null means no prior replay. */
    public interface Environment {
        boolean supports(Capability capability);
        boolean supportsClip(Mc263AncientCitySettlement.Clip clip);
        boolean acceptsCanonicalPayload(Mc263StructureBatchBridge.BentEvidence payload);
        Replay replay(String transactionKey);
        Transaction open(SessionIdentity identity, PersistedExecutionRequest request,
                PersistedDispatchPlan plan, Mc263WorldGenRegionRandom randomFork);
    }

    /**
     * Unpublished environment fork. Placement methods consume only {@code randomFork}; prepare is
     * pure with respect to durable state, commit durably installs replay, and abort discards every
     * staged query, write, sidecar and RNG effect.
     */
    public interface Transaction {
        void placeTemplate(PersistedTemplatePlacement placement,
                Mc263AncientCityGrammar.Template template,
                Mc263AncientCityGrammar.ProcessorListSpec processorList,
                List<Mc263AncientCityGrammar.ProcessorSpec> processors,
                Mc263WorldGenRegionRandom randomFork);
        void placeFeature(PersistedFeaturePlacement placement,
                Mc263AncientCityGrammar.ConfiguredFeature feature,
                Mc263WorldGenRegionRandom randomFork);
        Mc263StructureBatchBridge.Batch prepare();
        void commit(Replay replay);
        void abort();
    }
}
