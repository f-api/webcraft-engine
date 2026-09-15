package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.feature.Mc263StructureBatchBridge;
import com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState;
import com.gameexpert.terrain.mc.feature.Mc263FeatureWorldAdapter;
import com.gameexpert.terrain.mc.feature.Mc263SculkPatchFeature;
import com.gameexpert.terrain.mc.feature.Mc263WorldGenRegionRandom;
import com.gameexpert.terrain.mc.ore.Mc263WorldgenRandomSource;
import com.gameexpert.terrain.mc.structure.Mc263AncientCityProductionAdapter.PersistedDispatchPlan;
import com.gameexpert.terrain.mc.structure.Mc263AncientCityProductionAdapter.PersistedExecutionRequest;
import com.gameexpert.terrain.mc.structure.Mc263AncientCityProductionAdapter.PersistedFeaturePlacement;
import com.gameexpert.terrain.mc.structure.Mc263AncientCityProductionAdapter.PersistedTemplatePlacement;
import com.gameexpert.terrain.mc.structure.Mc263AncientCityPersistedRuntime.Capability;
import com.gameexpert.terrain.mc.structure.Mc263AncientCityPersistedRuntime.Replay;
import com.gameexpert.terrain.mc.structure.Mc263AncientCityPersistedRuntime.SessionIdentity;
import com.gameexpert.terrain.mc.structure.Mc263AncientCityPersistedRuntime.Transaction;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Cell;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.CellProcessor;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.CellProgram;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Clip;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.IgnorePolicy;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Mirror;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Piece;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.PlacementWorld;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.ProcessedCell;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Result;
import com.gameexpert.terrain.mc.structure.Mc263TemplatePlacementExecutor.Vec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Ancient City production environment: the thin settlement-facing adapter that runs the
 * family-neutral {@link Mc263TemplatePlacementExecutor} as the Ancient City template-cell engine
 * behind {@link Mc263AncientCityPersistedRuntime}.
 *
 * <p>Only Ancient-specific adaptation lives here. The vanilla {@code placeInWorld} loop, the
 * mirror-before-rotation transform, the clip filter, the block-entity clear write and the loot
 * {@code nextLong()} derivation stay in the shared executor; the per-cell processor chain stays in
 * {@link Mc263AncientCityProcessorExecutor}; the configured feature stays in
 * {@link Mc263AncientCitySculkExecutor}; and the atomic successor/replay semantics stay in the
 * runtime, which enters this class only through {@link Transaction}.</p>
 *
 * <h2>Cell program</h2>
 * <p>An {@link Mc263AncientCityGrammar.Template} is adapted command by command in stored block
 * order: {@link Mc263AncientCityGrammar.Run} expands to its {@code count} cells along {@code delta},
 * {@link Mc263AncientCityGrammar.Jigsaw} takes its template-local position from the connector its
 * {@code connectorOrdinal} names and carries the connector's final state to the
 * {@code minecraft:jigsaw_replacement} processor, and
 * {@link Mc263AncientCityGrammar.LootContainer}/{@link Mc263AncientCityGrammar.Bent} carry their
 * block-entity identity. The executor then clips, transforms and places them.</p>
 *
 * <h2>Landed lanes</h2>
 * <ul>
 *   <li>Template cells, their processor chain and their final writes are landed.</li>
 *   <li>LOOT rows are admitted only when the natural-domain
 *       {@link Mc263AncientCitySettlement.NaturalLootAuthority} holds a binding for exactly this
 *       piece ordinal, template, table and block-entity type; any other loot cell fails closed.</li>
 *   <li>Canonical block-entity NBT is synthesized from the authenticated template command grammar
 *       or the authenticated configured-feature block-entity grammar.</li>
 *   <li>The configured sculk feature and template loot share one official
 *       {@code WorldgenRandom<XoroshiroRandomSource>} view over the transaction-owned
 *       {@code worldgen_region_random} fork, preserving the E3I9 draw order and continuation.</li>
 * </ul>
 */
public final class Mc263AncientCityProductionEnvironment
        implements Mc263AncientCityPersistedRuntime.Environment {
    /** Runtime capability identities the persisted runtime probes under {@code "runtime"}. */
    private static final Set<String> RUNTIME_CAPABILITIES = Set.of("atomic-replay-rng",
            "foreign-destinations", "caller-rng-continuation", "final-schema-4");
    /** {@code SinglePoolElement} adds only {@code BlockIgnoreProcessor.STRUCTURE_BLOCK}. */
    private static final IgnorePolicy IGNORE_POLICY = IgnorePolicy.STRUCTURE_BLOCK;
    /** {@code LiquidSettings.APPLY_WATERLOGGING} is the pinned jigsaw placement default. */
    private static final boolean KEEP_LIQUIDS = true;
    private static final String RIGID = "rigid";
    /** The only block-entity identity the natural Ancient City loot domain admits. */
    private static final String LOOT_BLOCK_ENTITY = "minecraft:chest";
    /**
     * Block-entity surface of the post-placement {@code minecraft:sculk_patch_ancient_city}
     * configured feature, authenticated by
     * {@link Mc263AncientCityProductionAuthority#canonicalFeatureBlockEntityNbt}: the sculk lane
     * lands these three types, and the template command grammar carries none of them beyond the
     * empty sculk sensor.
     */
    private static final Set<String> FEATURE_BLOCK_ENTITY_TYPES = Set.of(
            "minecraft:sculk_catalyst", "minecraft:sculk_sensor", "minecraft:sculk_shrieker");

    /**
     * Rotation closure of the authenticated ANC263P1 template palette.
     *
     * <p>{@link Mc263AncientCityProcessorExecutor} preflights the state it actually processes,
     * which is {@code Mc263VillageSettlement.rotateState(paletteState, placementRotation)}. The
     * authority document stores only the unrotated palette, so the capability surface is that
     * palette closed under the four authenticated placement rotations — the same closure
     * {@code Mc263VillageSettlement.preflight} builds for the Village families.
     *
     * <p>Fail-closed: a rotated spelling is admitted only when the pinned production-closure
     * receipt ({@code exact-state-production-closure-v1.json}, carried by
     * {@link Mc263FeatureBlockState#isAuthenticatedProductionClosureExactState}) authenticates
     * it, so a rotation this project derives but the official closure never produced stays
     * outside the capability.</p>
     */
    private static final Set<String> ROTATED_EXACT_STATES = rotationClosure();

    private static Set<String> rotationClosure() {
        LinkedHashSet<String> closure = new LinkedHashSet<>();
        for (String state : Mc263AncientCityProductionAuthority.load().statesInEncounterOrder()) {
            for (Rotation rotation : Rotation.values()) {
                String rotated = Mc263VillageSettlement.rotateState(state, rotation);
                if (rotated.equals(state)) continue;
                if (Mc263FeatureBlockState.isAuthenticatedProductionClosureExactState(rotated)) {
                    closure.add(rotated);
                }
            }
        }
        return Set.copyOf(closure);
    }

    private final World world;
    private final Publisher publisher;
    private final Mc263AncientCityGrammar grammar;
    private final Map<String, Mc263AncientCityGrammar.Template> templates;
    private final Set<String> processorLists;
    private final Set<String> processorSemantics;
    private final Set<String> exactStates;
    private final Set<String> exactBlocks;
    private final Set<String> laneNames;
    private final Set<String> blockEntityTypes;
    private final List<Mc263AncientCitySettlement.LootBinding> lootBindings;
    private final Map<String, Replay> replays = new HashMap<>();
    private final Map<String, List<String>> transcripts = new HashMap<>();

    /**
     * World seam. Template placement needs the executor's transcribing world contract; the
     * {@code minecraft:protected_blocks} and {@code minecraft:block_rot} processors additionally
     * need the destination tag oracle the processor executor declares.
     */
    public interface World extends PlacementWorld {
        boolean supportsTag(String tagKey);
        boolean stateInTag(String exactState, String tagKey);

        default String biomeKey(int x, int y, int z) {
            throw new IllegalStateException("Ancient City feature biome query is unsupported");
        }

        default boolean sectionMayContainSculkGrowthInhibitor(
                int sectionX, int sectionY, int sectionZ) {
            throw new IllegalStateException(
                    "Ancient City sculk-growth inhibitor query is unsupported");
        }

        default boolean supportsPostprocessing() { return false; }
        default boolean supportsSculkPayloads() { return false; }
    }

    /** Host-owned atomic handoff for the prepared batch and accepted caller RNG successor. */
    @FunctionalInterface
    public interface Publisher {
        void publish(Mc263StructureBatchBridge.Batch batch,
                Mc263WorldGenRegionRandom acceptedRandom);
    }

    public Mc263AncientCityProductionEnvironment(World world) {
        this(world, (batch, acceptedRandom) -> { });
    }

    public Mc263AncientCityProductionEnvironment(World world, Publisher publisher) {
        this.world = Objects.requireNonNull(world, "Ancient City production world");
        this.publisher = Objects.requireNonNull(publisher, "Ancient City production publisher");
        this.grammar = Mc263AncientCityProductionAuthority.load();
        LinkedHashMap<String, Mc263AncientCityGrammar.Template> byId = new LinkedHashMap<>();
        for (Mc263AncientCityGrammar.Template template : grammar.templatesInEncounterOrder()) {
            require(byId.putIfAbsent(template.id(), template) == null,
                    "duplicate Ancient City template authority");
        }
        this.templates = Map.copyOf(byId);
        LinkedHashSet<String> lists = new LinkedHashSet<>();
        for (Mc263AncientCityGrammar.ProcessorListSpec list
                : grammar.processorListsInEncounterOrder()) {
            lists.add(list.identity());
        }
        this.processorLists = Set.copyOf(lists);
        LinkedHashSet<String> semantics = new LinkedHashSet<>();
        for (Mc263AncientCityGrammar.ProcessorSpec processor
                : grammar.processorSemanticsInEncounterOrder()) {
            semantics.add(processor.identity());
        }
        this.processorSemantics = Set.copyOf(semantics);
        this.exactStates = Set.copyOf(grammar.statesInEncounterOrder());
        LinkedHashSet<String> blocks = new LinkedHashSet<>();
        for (String state : this.exactStates) blocks.add(blockIdentity(state));
        this.exactBlocks = Set.copyOf(blocks);
        LinkedHashSet<String> lanes = new LinkedHashSet<>();
        for (Mc263AncientCitySettlement.SidecarLane lane
                : Mc263AncientCitySettlement.SidecarLane.values()) {
            lanes.add(lane.name());
        }
        this.laneNames = Set.copyOf(lanes);
        LinkedHashSet<String> entities = new LinkedHashSet<>();
        for (Mc263AncientCityGrammar.Template template : grammar.templatesInEncounterOrder()) {
            for (Mc263AncientCityGrammar.Command command : template.commands()) {
                if (command instanceof Mc263AncientCityGrammar.LootContainer container) {
                    entities.add(container.blockEntityType());
                } else if (command instanceof Mc263AncientCityGrammar.Bent bentCell) {
                    entities.add(bentCell.payload().blockEntityType());
                }
            }
        }
        this.blockEntityTypes = Set.copyOf(entities);
        this.lootBindings = Mc263AncientCitySettlement.NaturalLootAuthority.accepted()
                .bindingsInStoredPieceOrder();
    }

    // ── environment surface ──────────────────────────────────────────────────

    @Override
    public boolean supports(Capability capability) {
        Objects.requireNonNull(capability, "Ancient City capability");
        return switch (capability.kind()) {
            case "runtime" -> RUNTIME_CAPABILITIES.contains(capability.identity());
            case "lane" -> laneNames.contains(capability.identity());
            case "template" -> templates.containsKey(capability.identity());
            case "processor-list" -> processorLists.contains(capability.identity());
            case "processor" -> processorSemantics.contains(capability.identity());
            case "feature" -> grammar.configuredFeature().registryKey()
                    .equals(capability.identity());
            case "state" -> exactStates.contains(capability.identity())
                    || Mc263FeatureBlockState.isAuthenticatedProductionClosureExactState(
                            capability.identity());
            case "loot" -> lootTable(capability.identity());
            case "block-entity" -> blockEntityTypes.contains(capability.identity())
                    || FEATURE_BLOCK_ENTITY_TYPES.contains(capability.identity());
            case "canonical-nbt" -> blockEntityTypes.contains(capability.identity())
                    || FEATURE_BLOCK_ENTITY_TYPES.contains(capability.identity());
            default -> false;
        };
    }

    @Override
    public boolean supportsClip(Mc263AncientCitySettlement.Clip clip) {
        return clip != null && clip.minYInclusive() >= Mc263AncientCitySettlement.MIN_CLIP_Y
                && clip.maxYInclusive() <= Mc263AncientCitySettlement.MAX_CLIP_Y;
    }

    @Override
    public boolean acceptsCanonicalPayload(Mc263StructureBatchBridge.BentEvidence payload) {
        if (payload == null) return false;
        byte[] nbt = payload.canonicalNbt();
        return (blockEntityTypes.contains(payload.entityType())
                        || FEATURE_BLOCK_ENTITY_TYPES.contains(payload.entityType()))
                && nbt.length >= 4 && nbt[0] == 10 && nbt[nbt.length - 1] == 0;
    }

    @Override
    public Replay replay(String transactionKey) {
        return transactionKey == null ? null : replays.get(transactionKey);
    }

    @Override
    public Transaction open(SessionIdentity identity, PersistedExecutionRequest request,
            PersistedDispatchPlan plan, Mc263WorldGenRegionRandom randomFork) {
        Objects.requireNonNull(identity, "Ancient City session identity");
        Objects.requireNonNull(request, "Ancient City persisted request");
        Objects.requireNonNull(plan, "Ancient City persisted plan");
        Objects.requireNonNull(randomFork, "Ancient City transaction random fork");
        return new PlacementTransaction(identity, plan, randomFork);
    }

    /** Durable replays this environment installed, keyed by the runtime's transaction key. */
    public Map<String, Replay> installedReplays() {
        return Collections.unmodifiableMap(replays);
    }

    // ── transaction ──────────────────────────────────────────────────────────

    /**
     * Unpublished placement fork. Every staged write, sidecar row and transcript row lives here
     * until {@link #prepare()} materializes the batch and {@link #commit(Replay)} installs it;
     * {@link #abort()} discards the whole fork without touching the caller world.
     */
    private final class PlacementTransaction implements Transaction {
        private final SessionIdentity identity;
        private final PersistedDispatchPlan plan;
        private final Mc263WorldGenRegionRandom randomFork;
        private final Clip clip;
        private final long owner;
        private final StagingWorld staging;
        private final WorldgenRandomForkView placementRandom;
        private final ArrayList<String> transcript = new ArrayList<>();
        private final LinkedHashMap<Vec, String> finalCells = new LinkedHashMap<>();
        private final ArrayList<Mc263StructureBatchBridge.Loot> loot = new ArrayList<>();
        private final ArrayList<Mc263StructureBatchBridge.BentEvidence> bent = new ArrayList<>();
        private final ArrayList<Mc263StructureBatchBridge.FluidTick> fluidTicks =
                new ArrayList<>();
        private final ArrayList<Mc263StructureBatchBridge.PostprocessMark> postprocess =
                new ArrayList<>();
        private boolean open = true;

        private PlacementTransaction(SessionIdentity identity, PersistedDispatchPlan plan,
                Mc263WorldGenRegionRandom randomFork) {
            this.identity = identity;
            this.plan = plan;
            this.randomFork = randomFork;
            this.clip = Clip.chunk(plan.clip().chunkX(), plan.clip().chunkZ(),
                    plan.clip().minYInclusive(), plan.clip().maxYInclusive());
            this.owner = Mc263StructureCarrier.packChunk(
                    plan.start().originChunkX(), plan.start().originChunkZ());
            this.staging = new StagingWorld(world, fluidTicks);
            this.placementRandom = new WorldgenRandomForkView(randomFork);
        }

        @Override
        public void placeTemplate(PersistedTemplatePlacement placement,
                Mc263AncientCityGrammar.Template template,
                Mc263AncientCityGrammar.ProcessorListSpec processorList,
                List<Mc263AncientCityGrammar.ProcessorSpec> processors,
                Mc263WorldGenRegionRandom suppliedRandom) {
            require(open, "Ancient City placement transaction is not open");
            require(suppliedRandom == randomFork,
                    "Ancient City placement escaped the transaction random fork");
            Objects.requireNonNull(placement, "Ancient City template placement");
            Objects.requireNonNull(template, "Ancient City template program");
            Objects.requireNonNull(processorList, "Ancient City processor-list program");
            Objects.requireNonNull(processors, "Ancient City processor programs");

            TemplateProgram program = new TemplateProgram(template);
            ArrayList<String> semantics = new ArrayList<>();
            for (Mc263AncientCityGrammar.ProcessorSpec processor : processors) {
                semantics.add(processor.identity());
            }
            Piece piece = new Piece(placement.pieceOrdinal(), program,
                    new Vec(placement.originX(), placement.originY(), placement.originZ()),
                    executorRotation(placement.rotation()), Mirror.NONE, RIGID,
                    List.copyOf(semantics), IGNORE_POLICY, KEEP_LIQUIDS);
            int firstFluidTick = fluidTicks.size();
            Result result = Mc263TemplatePlacementExecutor.execute(List.of(piece), clip, staging,
                    new AncientCellProcessor(placement, semantics),
                    new AncientBlockEntitySynthesizer(),
                    placementRandom::nextLong);
            absorb(placement, result, firstFluidTick);
        }

        @Override
        public void placeFeature(PersistedFeaturePlacement placement,
                Mc263AncientCityGrammar.ConfiguredFeature feature,
                Mc263WorldGenRegionRandom suppliedRandom) {
            require(open, "Ancient City placement transaction is not open");
            require(suppliedRandom == randomFork,
                    "Ancient City feature escaped the transaction random fork");
            Objects.requireNonNull(placement, "Ancient City feature placement");
            Mc263AncientCitySculkExecutor.requireAuthenticatedFeature(
                    Objects.requireNonNull(feature, "Ancient City configured feature"));
            Mc263AncientCitySculkExecutor.Receipt receipt =
                    Mc263AncientCitySculkExecutor.execute(
                            new Mc263AncientCitySculkExecutor.Request(
                                    placement.originX(), placement.originY(), placement.originZ(),
                                    plan.clip().chunkX(), plan.clip().chunkZ(), placementRandom),
                            new StagingSculkEnvironment());
            absorbFeature(receipt);
        }

        private void absorbFeature(Mc263AncientCitySculkExecutor.Receipt receipt) {
            LinkedHashSet<Vec> featureBent = new LinkedHashSet<>();
            for (Mc263AncientCitySculkExecutor.Mutation mutation
                    : receipt.mutationsInOrder()) {
                Mc263AncientCitySculkExecutor.Position position = mutation.position();
                Vec target = new Vec(position.x(), position.y(), position.z());
                switch (mutation.kind()) {
                    case SET_BLOCK, SET_BLOCK_AND_UPDATE -> {
                        if (!mutation.applied()) continue;
                        String exact = Mc263FeatureWorldAdapter.sculkPatchExact(mutation.state());
                        finalCells.put(target, exact);
                        transcript.add(transcript.size() + "|feature-write|" + target + "|"
                                + exact + "|" + mutation.kind());
                        if (mutation.state().capability()
                                != Mc263SculkPatchFeature.Capability.NONE) {
                            featureBent.add(target);
                        }
                    }
                    case MARK_FOR_POSTPROCESSING -> {
                        if (!mutation.applied()) continue;
                        postprocess.add(new Mc263StructureBatchBridge.PostprocessMark(
                                position.x(), position.y(), position.z()));
                        transcript.add(transcript.size() + "|feature-postprocess|" + target);
                    }
                    default -> throw new IllegalStateException(
                            "unsupported Ancient City feature mutation: " + mutation.kind());
                }
            }
            for (Vec target : featureBent) {
                Mc263SculkPatchFeature.State state = stagingSculkState(target);
                String entityType = switch (state.capability()) {
                    case CATALYST_BLOCK_ENTITY -> "minecraft:sculk_catalyst";
                    case SENSOR_BLOCK_ENTITY -> "minecraft:sculk_sensor";
                    case SHRIEKER_BLOCK_ENTITY -> "minecraft:sculk_shrieker";
                    case NONE -> null;
                };
                if (entityType == null) continue;
                byte[] canonical = Mc263AncientCityProductionAuthority
                        .canonicalFeatureBlockEntityNbt(entityType,
                                new Mc263AncientCityGrammar.Pos(
                                        target.x(), target.y(), target.z()));
                bent.add(new Mc263StructureBatchBridge.BentEvidence(target.x(), target.y(),
                        target.z(), state.block(), entityType, canonical));
            }
        }

        /** Absorbs one executed piece into the fork, renumbering the clip-wide transcript. */
        private void absorb(PersistedTemplatePlacement placement, Result result,
                int firstFluidTick) {
            for (String row : result.operations()) {
                transcript.add(transcript.size() + "|" + row.substring(row.indexOf('|') + 1));
            }
            for (Mc263TemplatePlacementExecutor.FinalCell cell : result.finalCells()) {
                finalCells.put(cell.position(), cell.exactState());
            }
            for (Mc263TemplatePlacementExecutor.LootRow row : result.loot()) {
                require(admittedLoot(placement, row.table()),
                        "Ancient City loot row escaped the natural-domain authority at "
                                + row.position());
                loot.add(new Mc263StructureBatchBridge.Loot(row.position().x(),
                        row.position().y(), row.position().z(), row.table(), row.signedSeed()));
            }
            for (Mc263TemplatePlacementExecutor.BentRow row : result.bent()) {
                bent.add(new Mc263StructureBatchBridge.BentEvidence(row.position().x(),
                        row.position().y(), row.position().z(), blockIdentity(row.exactState()),
                        row.blockEntityType(), row.canonicalNbt()));
            }
            require(result.blockTicks().size() == fluidTicks.size() - firstFluidTick,
                    "Ancient City keepLiquids callback/result tick count diverged");
            for (int index = 0; index < result.blockTicks().size(); index++) {
                Mc263TemplatePlacementExecutor.TickRow row = result.blockTicks().get(index);
                Mc263StructureBatchBridge.FluidTick staged = fluidTicks.get(firstFluidTick + index);
                require(row.position().x() == staged.blockX()
                                && row.position().y() == staged.blockY()
                                && row.position().z() == staged.blockZ()
                                && row.key().equals(staged.fluidKey())
                                && row.delay() == staged.delay()
                                && row.priority() == staged.priority(),
                        "Ancient City keepLiquids callback/result tick identity diverged");
            }
        }

        @Override
        public Mc263StructureBatchBridge.Batch prepare() {
            require(open, "Ancient City placement transaction is not open");
            ArrayList<Mc263StructureBatchBridge.BlockWrite> blocks =
                    new ArrayList<>(finalCells.size());
            for (Map.Entry<Vec, String> cell : finalCells.entrySet()) {
                blocks.add(new Mc263StructureBatchBridge.BlockWrite(cell.getKey().x(),
                        cell.getKey().y(), cell.getKey().z(), cell.getValue(), owner));
            }
            return new Mc263StructureBatchBridge.Batch(blocks, List.copyOf(loot), List.of(),
                    List.copyOf(bent), List.of(), List.copyOf(fluidTicks),
                    List.copyOf(postprocess), List.of(), List.of(), List.of());
        }

        @Override
        public void commit(Replay replay) {
            require(open, "Ancient City placement transaction is not open");
            Objects.requireNonNull(replay, "Ancient City replay");
            require(replay.identity().equals(identity),
                    "Ancient City replay identity escaped its transaction");
            require(!replays.containsKey(replay.identity().transactionKey()),
                    "duplicate Ancient City durable replay key");
            publisher.publish(replay.batch(), randomFork);
            require(replays.putIfAbsent(replay.identity().transactionKey(), replay) == null,
                    "duplicate Ancient City durable replay key");
            transcripts.put(replay.identity().transactionKey(), List.copyOf(transcript));
            open = false;
        }

        @Override
        public void abort() {
            open = false;
            transcript.clear();
            finalCells.clear();
            loot.clear();
            bent.clear();
            fluidTicks.clear();
            postprocess.clear();
            staging.discard();
        }

        private Mc263SculkPatchFeature.State stagingSculkState(Vec position) {
            return Mc263FeatureWorldAdapter.sculkPatchState(
                    Mc263FeatureBlockState.fromExact(staging.getBlockState(position)));
        }

        private final class StagingSculkEnvironment
                implements Mc263AncientCitySculkExecutor.Environment {
            @Override public boolean supportsExactState(String exactState) {
                return Mc263FeatureBlockState.supportsExactState(exactState);
            }

            @Override public boolean supportsPostprocessing() {
                return world.supportsPostprocessing();
            }

            @Override public boolean supportsSculkPayloads() {
                return world.supportsSculkPayloads();
            }

            @Override public int minGenerationY() {
                return Mc263AncientCitySettlement.MIN_CLIP_Y - 1;
            }

            @Override public int generationDepth() {
                return Mc263AncientCitySettlement.MAX_CLIP_Y
                        - Mc263AncientCitySettlement.MIN_CLIP_Y + 2;
            }

            @Override public String biomeKey(int x, int y, int z) {
                return world.biomeKey(x, y, z);
            }

            @Override public Mc263SculkPatchFeature.State blockState(int x, int y, int z) {
                return stagingSculkState(new Vec(x, y, z));
            }

            @Override public Mc263SculkPatchFeature.FluidFact fluidState(int x, int y, int z) {
                Mc263SculkPatchFeature.State state = blockState(x, y, z);
                return new Mc263SculkPatchFeature.FluidFact(
                        state.fluid(), state.fluidSource());
            }

            @Override public boolean isCollisionShapeFullBlock(int x, int y, int z) {
                return Mc263FeatureBlockState.fromExact(staging.getBlockState(new Vec(x, y, z)))
                        .isCollisionShapeFullBlock();
            }

            @Override public boolean isFaceSturdy(int x, int y, int z,
                    Mc263SculkPatchFeature.Direction face) {
                return Mc263FeatureBlockState.fromExact(staging.getBlockState(new Vec(x, y, z)))
                        .isFaceSturdy(Mc263FeatureBlockState.OcclusionFace.valueOf(face.name()));
            }

            @Override public boolean canAttachTo(int x, int y, int z,
                    Mc263SculkPatchFeature.Direction face) {
                int nx = x + face.stepX(), ny = y + face.stepY(), nz = z + face.stepZ();
                return Mc263FeatureBlockState.fromExact(
                                staging.getBlockState(new Vec(nx, ny, nz)))
                        .isSupportOrCollisionFull(Mc263FeatureBlockState.OcclusionFace
                                .valueOf(face.opposite().name()));
            }

            @Override public boolean sectionMayContainGrowthInhibitor(
                    int sectionX, int sectionY, int sectionZ) {
                return world.sectionMayContainSculkGrowthInhibitor(
                        sectionX, sectionY, sectionZ);
            }

            @Override public boolean trySetBlockState(int x, int y, int z,
                    Mc263SculkPatchFeature.State state, int flags) {
                if (!canFeatureWrite(x, y, z)) return false;
                return staging.setBlock(new Vec(x, y, z),
                        Mc263FeatureWorldAdapter.sculkPatchExact(state), flags);
            }

            @Override public boolean tryMarkPosForPostProcessing(int x, int y, int z) {
                return canFeatureWrite(x, y, z);
            }

            private boolean canFeatureWrite(int x, int y, int z) {
                return y >= Mc263AncientCitySettlement.MIN_CLIP_Y
                        && y <= Mc263AncientCitySettlement.MAX_CLIP_Y
                        && Math.abs(Math.floorDiv(x, 16) - plan.clip().chunkX()) <= 1
                        && Math.abs(Math.floorDiv(z, 16) - plan.clip().chunkZ()) <= 1;
            }
        }

        private boolean admittedLoot(PersistedTemplatePlacement placement, String table) {
            Mc263AncientCityGrammar.Template template = templates.get(placement.template());
            if (template == null) return false;
            for (Mc263AncientCityGrammar.Command command : template.commands()) {
                if (command instanceof Mc263AncientCityGrammar.LootContainer container
                        && container.lootTable().equals(table)
                        && LOOT_BLOCK_ENTITY.equals(container.blockEntityType())) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Official {@code WorldgenRandom} bit-consumption view over the transaction-owned raw
     * Xoroshiro source. Each {@code next(bits)} consumes exactly one raw transition; notably,
     * {@code nextLong()} consumes two transitions, matching the pinned wrapper rather than the
     * delegate's one-transition convenience method.
     */
    private static final class WorldgenRandomForkView implements Mc263WorldgenRandomSource {
        private final Mc263WorldGenRegionRandom source;

        private WorldgenRandomForkView(Mc263WorldGenRegionRandom source) {
            this.source = Objects.requireNonNull(source, "Ancient City placement random source");
        }

        private int next(int bits) {
            return (int) (source.nextLong() >>> (64 - bits));
        }

        private long nextLong() {
            return ((long) next(32) << 32) + next(32);
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
            int mask = bound - 1;
            if ((bound & mask) == 0) return (int) ((bound * (long) next(31)) >> 31);
            int bits;
            int value;
            do {
                bits = next(31);
                value = bits % bound;
            } while (bits - value + mask < 0);
            return value;
        }

        @Override public boolean nextBoolean() { return next(1) != 0; }
        @Override public float nextFloat() { return next(24) * 0x1.0p-24F; }
    }

    /**
     * Clip-wide operation transcript of a committed transaction, in the pinned oracle's ordered
     * row form, so a caller can digest it in the whole-bbox evidence domain. Null when the
     * transaction key never committed.
     */
    public List<String> committedTranscript(String transactionKey) {
        return transactionKey == null ? null : transcripts.get(transactionKey);
    }

    // ── cell program ─────────────────────────────────────────────────────────

    /** One Ancient City template adapted to the executor's neutral cell program. */
    private static final class TemplateProgram implements CellProgram {
        private final Mc263AncientCityGrammar.Template template;
        private final List<AncientCell> cells;

        private TemplateProgram(Mc263AncientCityGrammar.Template template) {
            require(template.status() == Mc263AncientCityGrammar.SourceStatus.PRESENT,
                    "Ancient City template program is absent: " + template.id());
            this.template = template;
            ArrayList<AncientCell> expanded = new ArrayList<>(template.blockCount());
            for (Mc263AncientCityGrammar.Command command : template.commands()) {
                if (command instanceof Mc263AncientCityGrammar.Run run) {
                    for (int index = 0; index < run.count(); index++) {
                        expanded.add(new AncientCell(
                                run.start().x() + run.delta().x() * index,
                                run.start().y() + run.delta().y() * index,
                                run.start().z() + run.delta().z() * index,
                                state(template, run.state()), null, null, null, run));
                    }
                } else if (command instanceof Mc263AncientCityGrammar.Jigsaw jigsaw) {
                    Mc263AncientCityGrammar.Connector connector =
                            connector(template, jigsaw.connectorOrdinal());
                    expanded.add(new AncientCell(connector.position().x(),
                            connector.position().y(), connector.position().z(),
                            state(template, jigsaw.state()), null, null, jigsaw.finalState(),
                            jigsaw));
                } else if (command instanceof Mc263AncientCityGrammar.LootContainer container) {
                    expanded.add(new AncientCell(container.position().x(),
                            container.position().y(), container.position().z(),
                            state(template, container.state()), container.blockEntityType(),
                            container.lootTable(), null, container));
                } else if (command instanceof Mc263AncientCityGrammar.Bent bentCell) {
                    expanded.add(new AncientCell(bentCell.position().x(), bentCell.position().y(),
                            bentCell.position().z(), state(template, bentCell.state()),
                            bentCell.payload().blockEntityType(), null, null, bentCell));
                } else {
                    throw new IllegalStateException(
                            "unknown Ancient City template command: " + command.opcode());
                }
            }
            require(expanded.size() == template.blockCount(),
                    "Ancient City expanded cell cardinality drift: " + template.id());
            this.cells = List.copyOf(expanded);
        }

        @Override public String templateKey() { return template.id(); }
        @Override public List<AncientCell> cellsInPlacementOrder() { return cells; }

        private static String state(Mc263AncientCityGrammar.Template template, int index) {
            require(index >= 0 && index < template.stateTable().size(),
                    "Ancient City state-table index drift: " + template.id());
            return template.stateTable().get(index);
        }

        private static Mc263AncientCityGrammar.Connector connector(
                Mc263AncientCityGrammar.Template template, int ordinal) {
            require(ordinal >= 0 && ordinal < template.connectors().size(),
                    "Ancient City connector index drift: " + template.id());
            Mc263AncientCityGrammar.Connector connector = template.connectors().get(ordinal);
            require(connector.ordinal() == ordinal,
                    "Ancient City connector order drift: " + template.id());
            return connector;
        }
    }

    /** One expanded template cell; {@code jigsawFinalState} is null outside a JIGSAW cell. */
    private static final class AncientCell implements Cell {
        private final int localX, localY, localZ;
        private final String exactState, blockEntityType, lootTable, jigsawFinalState;
        private final Mc263AncientCityGrammar.Command command;

        private AncientCell(int localX, int localY, int localZ, String exactState,
                String blockEntityType, String lootTable, String jigsawFinalState,
                Mc263AncientCityGrammar.Command command) {
            this.localX = localX; this.localY = localY; this.localZ = localZ;
            this.exactState = Objects.requireNonNull(exactState, "Ancient City cell state");
            this.blockEntityType = blockEntityType;
            this.lootTable = lootTable;
            this.jigsawFinalState = jigsawFinalState;
            this.command = Objects.requireNonNull(command, "Ancient City cell command");
        }

        @Override public int localX() { return localX; }
        @Override public int localY() { return localY; }
        @Override public int localZ() { return localZ; }
        @Override public String exactState() { return exactState; }
        @Override public String blockEntityType() { return blockEntityType; }
        @Override public String lootTable() { return lootTable; }
        @Override public String jigsawFinalState() { return jigsawFinalState; }
        private Mc263AncientCityGrammar.Command command() { return command; }
    }

    // ── processor and block-entity lanes ─────────────────────────────────────

    /** The authenticated Ancient City processor chain, plugged in as the executor's leaf. */
    private final class AncientCellProcessor implements CellProcessor {
        private final PersistedTemplatePlacement placement;
        private final List<String> semantics;

        private AncientCellProcessor(PersistedTemplatePlacement placement,
                List<String> semantics) {
            this.placement = placement;
            this.semantics = semantics;
        }

        @Override
        public ProcessedCell process(Piece piece, Cell source, ProcessedCell input,
                PlacementWorld recording) {
            if (semantics.isEmpty()) return input;
            require(source instanceof AncientCell, "unknown Ancient City cell implementation");
            AncientCell cell = (AncientCell) source;
            Mc263AncientCityProcessorExecutor.Receipt receipt =
                    Mc263AncientCityProcessorExecutor.execute(
                            new Mc263AncientCityProcessorExecutor.Request(semantics,
                                    templateState(cell), input.inputNbt(), cell.jigsawFinalState(),
                                    cell.localX(), cell.localY(), cell.localZ(),
                                    placement.originX(), placement.originY(), placement.originZ(),
                                    placement.rotation()),
                            new ProcessorEnvironment(recording));
            Mc263VillageSettlement.Position position = receipt.worldPosition();
            require(position.x() == input.position().x() && position.y() == input.position().y()
                            && position.z() == input.position().z(),
                    "Ancient City processor/executor transform drift at " + input.position());
            if (receipt.removed()) return null;
            return new ProcessedCell(input.position(), receipt.outputExactState(),
                    receipt.outputNbt());
        }
    }

    /**
     * Template-local state the registered Ancient City processor list actually receives.
     *
     * <p>{@code semantics} is {@link Mc263AncientCityGrammar.ProcessorListSpec}'s <em>registered</em>
     * list — {@code minecraft:block_rot}/{@code minecraft:rule}/{@code minecraft:protected_blocks}
     * — never the two processors {@code SinglePoolElement.getSettings} installs ahead of it. The
     * pinned pool-element pipeline records exactly that split: every {@code E}/{@code C} row of
     * {@code src/main/resources/mc263/ancient-city-production-authority-v1.txt} spells its
     * placement chain {@code minecraft:block_ignore, minecraft:jigsaw_replacement, <registered
     * list>}, and {@code Mc263AncientCityGrammar.validatePlacementPipeline} pins that prefix.
     *
     * <p>{@link Mc263TemplatePlacementExecutor} owns that prefix for every family, so a
     * {@code minecraft:jigsaw} cell has already become its block entity's {@code final_state}
     * (default {@code minecraft:air}; {@code minecraft:structure_void} drops the cell outright)
     * before this chain runs — vanilla {@code JigsawReplacementProcessor}, which replaces the
     * marker before any element processor or the world write observes it. The Village and Trial
     * Chambers chains consume that pre-chain output directly as {@code input.exactState()}; this
     * lane re-derives the template-local state instead, so it must apply the same replacement or
     * the connector's {@code final_state} block is never written and the raw jigsaw spelling —
     * which is not a world-writable exact state — is preflighted as one.
     */
    private static String templateState(AncientCell cell) {
        String state = cell.exactState();
        if (!Mc263TemplatePlacementExecutor.JIGSAW_BLOCK.equals(blockKey(state))) return state;
        String finalState = cell.jigsawFinalState();
        return finalState == null ? Mc263TemplatePlacementExecutor.AIR_BLOCK : finalState;
    }

    /** Block identity of an exact state, as the shared executor spells it. */
    private static String blockKey(String exactState) {
        int open = exactState.indexOf('[');
        return open < 0 ? exactState : exactState.substring(0, open);
    }

    /** Processor world/tag oracle bound to the executor's transcribing world for one cell. */
    private final class ProcessorEnvironment
            implements Mc263AncientCityProcessorExecutor.Environment {
        private final PlacementWorld recording;

        private ProcessorEnvironment(PlacementWorld recording) {
            this.recording = recording;
        }

        @Override public boolean supportsProcessor(String semanticIdentity, String runtimeClass) {
            for (Mc263AncientCityGrammar.ProcessorSpec processor
                    : grammar.processorSemanticsInEncounterOrder()) {
                if (processor.identity().equals(semanticIdentity)) {
                    return processor.runtimeClass().equals(runtimeClass);
                }
            }
            return false;
        }

        /**
         * The processor codecs name both exact states and bare block identities (a
         * {@code minecraft:rule} input predicate carries the block, not the state), so the
         * capability surface the processor executor preflights is the authenticated closure of
         * both — and, because the executor preflights the <em>rotated</em> cell state, the
         * rotation closure of the authenticated palette as well (see
         * {@link Mc263AncientCityProductionEnvironment#ROTATED_EXACT_STATES}).
         */
        @Override public boolean supportsExactState(String exactState) {
            return exactStates.contains(exactState) || exactBlocks.contains(exactState)
                    || ROTATED_EXACT_STATES.contains(exactState);
        }

        @Override public boolean supportsTag(String tagKey) {
            return Mc263AncientCityProductionAuthority.supportsAuthenticatedTag(tagKey);
        }

        @Override public boolean supportsWorldStateQuery(Mc263VillageSettlement.Position position) {
            return true;
        }

        @Override public String blockStateAt(Mc263VillageSettlement.Position position) {
            return recording.getBlockState(new Vec(position.x(), position.y(), position.z()));
        }

        @Override public boolean stateInTag(String exactState, String tagKey) {
            return Mc263AncientCityProductionAuthority.stateInAuthenticatedTag(
                    exactState, tagKey);
        }
    }

    /** Canonical block-entity NBT authority derived from authenticated template commands. */
    private final class AncientBlockEntitySynthesizer
            implements Mc263TemplatePlacementExecutor.BlockEntitySynthesizer {
        @Override
        public byte[] canonicalNbt(Piece piece, Cell cell, Vec position, String exactState,
                String blockEntityType, String lootTable, long lootSeed) {
            require(cell instanceof AncientCell,
                    "Ancient City BENT cell escaped the authenticated template program");
            AncientCell ancient = (AncientCell) cell;
            Mc263AncientCityGrammar.Command command = ancient.command();
            require(command instanceof Mc263AncientCityGrammar.LootContainer
                            || command instanceof Mc263AncientCityGrammar.Bent,
                    "Ancient City non-BENT command requested canonical NBT");
            byte[] canonical = Mc263AncientCityGrammar.canonicalBlockEntityNbt(command,
                    new Mc263AncientCityGrammar.Pos(position.x(), position.y(), position.z()),
                    lootSeed);
            require(canonical.length >= 4 && canonical[0] == 10
                            && canonical[canonical.length - 1] == 0,
                    "Ancient City canonical BENT grammar emitted invalid binary NBT");
            return canonical;
        }
    }

    // ── staging world ────────────────────────────────────────────────────────

    /** Unpublished write buffer: reads fall through to the caller world, writes stay in the fork. */
    private static final class StagingWorld implements PlacementWorld {
        private final World delegate;
        private final List<Mc263StructureBatchBridge.FluidTick> fluidTicks;
        private final LinkedHashMap<Vec, String> staged = new LinkedHashMap<>();

        private StagingWorld(World delegate,
                List<Mc263StructureBatchBridge.FluidTick> fluidTicks) {
            this.delegate = delegate;
            this.fluidTicks = fluidTicks;
        }

        private void discard() {
            staged.clear();
        }

        @Override public int getHeight(String heightmap, int x, int z) {
            return delegate.getHeight(heightmap, x, z);
        }

        @Override public String getBlockState(Vec position) {
            String value = staged.get(position);
            return value != null ? value : delegate.getBlockState(position);
        }

        @Override public String getFluidKey(Vec position) {
            return staged.containsKey(position)
                    ? Mc263TemplatePlacementExecutor.EMPTY_FLUID : delegate.getFluidKey(position);
        }

        @Override public boolean isFluidSource(Vec position) {
            return !staged.containsKey(position) && delegate.isFluidSource(position);
        }

        @Override public double fluidHeight(Vec position) {
            return staged.containsKey(position) ? 0.0D : delegate.fluidHeight(position);
        }

        @Override public boolean setBlock(Vec position, String exactState, int flags) {
            staged.put(position, exactState);
            return true;
        }

        @Override public Mc263TemplatePlacementExecutor.BlockEntityHandle getBlockEntity(
                Vec position) {
            String exact = staged.get(position);
            if (exact != null) {
                String type = blockEntityType(blockIdentity(exact));
                if (type != null) {
                    return new Mc263TemplatePlacementExecutor.BlockEntityHandle() {
                        @Override public String blockEntityType() { return type; }
                        @Override public void loadCanonicalNbt(byte[] canonicalNbt) {
                            Objects.requireNonNull(canonicalNbt,
                                    "Ancient City staged canonical NBT");
                        }
                        @Override public void setChanged() { }
                    };
                }
            }
            return delegate.getBlockEntity(position);
        }

        @Override public void scheduleFluidTick(Vec position, String fluidKey, int delay) {
            Objects.requireNonNull(position, "Ancient City staged fluid-tick position");
            Objects.requireNonNull(fluidKey, "Ancient City staged fluid-tick type");
            require(delay >= 0, "Ancient City staged fluid-tick delay is negative");
            fluidTicks.add(new Mc263StructureBatchBridge.FluidTick(position.x(), position.y(),
                    position.z(), fluidKey, delay, 0, fluidTicks.size()));
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private boolean lootTable(String identity) {
        for (Mc263AncientCitySettlement.LootBinding binding : lootBindings) {
            if (binding.lootTable().equals(identity)) return true;
        }
        return false;
    }

    private static Mc263TemplatePlacementExecutor.Rotation executorRotation(Rotation rotation) {
        return Mc263TemplatePlacementExecutor.Rotation.valueOf(rotation.name());
    }

    private static String blockIdentity(String exactState) {
        int properties = exactState.indexOf('[');
        return properties < 0 ? exactState : exactState.substring(0, properties);
    }

    private static String blockEntityType(String block) {
        return switch (block) {
            case "minecraft:chest" -> "minecraft:chest";
            case "minecraft:comparator" -> "minecraft:comparator";
            case "minecraft:sculk_sensor" -> "minecraft:sculk_sensor";
            case "minecraft:sculk_catalyst" -> "minecraft:sculk_catalyst";
            case "minecraft:sculk_shrieker" -> "minecraft:sculk_shrieker";
            case "minecraft:furnace" -> "minecraft:furnace";
            case "minecraft:lectern" -> "minecraft:lectern";
            case "minecraft:campfire" -> "minecraft:campfire";
            case "minecraft:skeleton_skull" -> "minecraft:skull";
            default -> null;
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
