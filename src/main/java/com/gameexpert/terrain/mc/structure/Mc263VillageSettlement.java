package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.LegacyRand;
import com.gameexpert.terrain.mc.feature.Mc263WorldGenRegionRandom;
import com.gameexpert.terrain.mc.ore.Mc263WorldgenRandomSource;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.ElementType;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Projection;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.PiecePlacement;
import com.gameexpert.terrain.mc.structure.Mc263VillageProducerGrammarAccess.ProducerSpec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Dormant Java settlement boundary for the five pinned Minecraft 26.3-snapshot-7 Village keys.
 *
 * <p>Template interiors are expanded only through {@link Mc263VillageHierarchicalGrammar}; the
 * legacy RUN/coordinate command corpus and finite accepted-start graph are not production inputs.
 * The compact {@link Mc263VillageProductionAuthority} authenticates the five-family pool topology,
 * processors, configured features and typed sidecar closure before the procedural
 * {@link Mc263VillageProducer} is allowed to consume RNG or query the world.</p>
 *
 * <p>Runtime processor/configured-feature primitives execute on an unpublished world fork. Their
 * typed effects are admitted through strict per-call validation and accumulated in official
 * piece/template/cell/entity/marker order. Raw block-tick encounters remain an independent receipt.
 * The carrier BTIK lane is derived with the authenticated {@code LevelChunkTicks} rule: within each
 * destination chunk, the first {@code type+position} encounter keeps its complete payload, later
 * duplicates are ignored, and accepted queue rows are packed by ascending {@code subTickOrder}.
 * No raw duplicate is published to the carrier.</p>
 */
public final class Mc263VillageSettlement {
    public static final int CLIP_MIN_Y = -63;
    public static final int CLIP_MAX_Y = 319;
    public static final int TEMPLATE_WRITE_FLAGS = 18;
    public static final int BLOCK_ENTITY_CLEAR_FLAGS = 820;
    public static final String HEIGHTMAP = "WORLD_SURFACE_WG";
    public static final String LEVEL_CHUNK_TICKS_SHA256 =
            "d822dce47f239123d8d7f0bacd246d82010b57d4bc03b2a4c827e4428010c";

    /**
     * Authenticated reference ceiling. In the pinned 26.3 inner jar
     * {@code net.minecraft.world.level.levelgen.structure.StructureStart} is {@code final},
     * {@code getMaxReferences()} returns {@code 1} and {@code canBeReferenced()} is
     * {@code references < getMaxReferences()}, so no start can ever hold more than one reference.
     * {@code isValid()} reads only the piece container, and {@code placeInChunk} never inspects
     * the counter, so placement is never gated by it.
     */
    public static final int MAX_START_REFERENCES = 1;

    private static final Set<String> BLOCK_TICK_TYPES = Set.of(
            "minecraft:oak_leaves", "minecraft:spruce_leaves", "minecraft:acacia_leaves",
            "minecraft:dirt_path", "minecraft:sand");
    /**
     * Authenticated fluid-tick lane. A template-restored fluid source runs
     * {@code LiquidBlock#onPlace -> Level.scheduleTick(pos, Fluid, fluid.getTickDelay(level))},
     * which is a {@code LevelChunkTicks<Fluid>} row at {@code TickPriority.NORMAL} (ordinal 0) and
     * never a block tick. The pinned jar gives {@code WaterFluid#getTickDelay() == 5} and
     * {@code LavaFluid#getTickDelay() == 30} outside the fast-lava dimension, the same 30/NORMAL
     * lava delay the {@code FLUID} rows of {@code postprocess-evidence-v1.txt} record.
     */
    private static final Map<String, Integer> FLUID_TICK_TYPES = fluidTickTypes();
    /**
     * Technical structure-marker blocks a template palette carries but {@code placeInWorld} never
     * writes: {@code minecraft:jigsaw} becomes its {@code final_state}, and
     * {@code minecraft:structure_block}/{@code minecraft:structure_void} cells are dropped.
     */
    private static final Set<String> TECHNICAL_MARKER_BLOCKS = Set.of(
            "minecraft:jigsaw", "minecraft:structure_block", "minecraft:structure_void");
    private static final Comparator<RawBlockTick> TICK_PACK_ORDER =
            Comparator.comparingLong(RawBlockTick::subTickOrder);
    private static final Comparator<Position> POSITION_ORDER = Comparator.comparingInt(Position::y)
            .thenComparingInt(Position::z).thenComparingInt(Position::x);

    private Mc263VillageSettlement() { }

    /**
     * Settles one exact scheduler-owned source chunk for one of the five accepted Village starts.
     * All capability methods are required to be pure. No caller-owned world/RNG state is changed
     * before the final atomic publication.
     */
    public static Settlement execute(Request request, WorldTransaction transaction,
            PlacementRandom callerRandom) {
        return executeInternal(request, transaction, callerRandom,
                (world, placement, random) -> world.executeTemplateCell(placement, random));
    }

    static Settlement executeWithTemplateAuthority(Request request, WorldTransaction transaction,
            PlacementRandom callerRandom, TemplateCellExecutor templateCellExecutor) {
        return executeInternal(request, transaction, callerRandom,
                Objects.requireNonNull(templateCellExecutor, "Village authoritative template executor"));
    }

    private static Settlement executeInternal(Request request, WorldTransaction transaction,
            PlacementRandom callerRandom, TemplateCellExecutor templateCellExecutor) {
        Objects.requireNonNull(request, "Village settlement request");
        Objects.requireNonNull(transaction, "Village settlement transaction");
        Objects.requireNonNull(callerRandom, "Village settlement caller RNG");

        Preflight preflight = preflight(request, transaction);
        PlacementRandom candidate = callerRandom.copy();
        long incomingLo = callerRandom.lo();
        long incomingHi = callerRandom.hi();
        int incomingCount = callerRandom.count();

        Mc263VillageProducer.Start start = regenerate(request, transaction);
        validateClip(start, request.sourceChunkX, request.sourceChunkZ, request.clipOrdinal);

        WorldTransaction isolated = Objects.requireNonNull(transaction.fork(),
                "isolated Village transaction");
        if (isolated == transaction) {
            throw new IllegalStateException("Village transaction fork is not isolated");
        }

        RuntimeExecutor runtime = new RuntimeExecutor() {
            @Override public ExecutionResult executeTemplateCell(CellPlacement placement) {
                return templateCellExecutor.execute(isolated, placement, candidate);
            }
            @Override public ExecutionResult executeConfiguredFeature(FeaturePlacement placement) {
                return isolated.executeConfiguredFeature(placement, candidate);
            }
            @Override public ExecutionResult executeStructureEntity(EntityPlacement placement) {
                return isolated.executeStructureEntity(placement, candidate);
            }
        };
        Accumulator accumulator = new Accumulator(request, isolated, runtime);
        List<PiecePlacement> pieces = start.executionPlan().pieces();
        for (int ordinal = 0; ordinal < pieces.size(); ordinal++) {
            PlacementPiece piece = new LegacyPlacementPiece(pieces.get(ordinal));
            if (!intersectsSourceChunk(piece, request.sourceChunkX, request.sourceChunkZ)) continue;
            if (piece.type() == ElementType.LEGACY_SINGLE) {
                executeTemplatePiece(ordinal, piece, accumulator, preflight);
            } else if (piece.type() == ElementType.FEATURE) {
                executeFeaturePiece(ordinal, piece, accumulator, preflight);
            } else {
                throw new IllegalArgumentException("unsupported Village piece type: " + piece.type());
            }
        }

        CarrierPayload carrier = accumulator.freezeCarrier(start.carrier().mutableSuccessorAfterOneReference(),
                candidate);
        RawTranscript raw = new RawTranscript(accumulator.rawBlockTicks(),
                accumulator.rawFluidTicks());
        String fingerprint = publicationFingerprint(request, start, incomingLo, incomingHi,
                incomingCount, raw.canonicalSha256(), carrier.canonicalSha256());
        Settlement settlement = new Settlement(request, start.carrier().structureStart().sha256(),
                raw, carrier, fingerprint);

        require(callerRandom.lo() == incomingLo && callerRandom.hi() == incomingHi
                        && callerRandom.count() == incomingCount,
                "Village caller RNG changed before atomic publication");
        PublishStatus status = Objects.requireNonNull(transaction.publishAtomically(
                isolated, settlement, callerRandom, candidate), "Village publish status");
        require(status == PublishStatus.COMMITTED || status == PublishStatus.REPLAYED,
                "unknown Village publish status");
        require(callerRandom.sameState(candidate),
                "Village atomic publisher did not install accepted RNG continuation");
        return settlement;
    }

    /**
     * Current-only canonical publication seam. The start graph and mutable-start bytes are consumed
     * from authenticated STR facts; no producer regeneration, planning query, Legacy48 seed, or RNG
     * reseed is permitted on this path. The supplied WorldGenRegion random is an unpublished fork
     * owned by the caller's canonical transaction and must begin at {@code randomPredecessor}.
     */
    public static PersistedSettlement executePersisted(PersistedRequest persisted,
            WorldTransaction transaction, Mc263WorldGenRegionRandom.State randomPredecessor,
            Mc263WorldGenRegionRandom randomFork) {
        return executePersistedInternal(persisted, transaction, randomPredecessor, randomFork,
                (world, placement, random) -> world.executeTemplateCell(placement, random),
                (world, placement, random) -> world.executeConfiguredFeature(placement, random));
    }

    static PersistedSettlement executePersistedWithAuthority(PersistedRequest persisted,
            WorldTransaction transaction, Mc263WorldGenRegionRandom.State randomPredecessor,
            Mc263WorldGenRegionRandom randomFork,
            PersistedTemplateCellExecutor templateCellExecutor,
            PersistedFeatureExecutor featureExecutor) {
        return executePersistedInternal(persisted, transaction, randomPredecessor, randomFork,
                Objects.requireNonNull(templateCellExecutor,
                        "Village persisted authoritative template executor"),
                Objects.requireNonNull(featureExecutor,
                        "Village persisted authoritative feature executor"));
    }

    private static PersistedSettlement executePersistedInternal(PersistedRequest persisted,
            WorldTransaction transaction, Mc263WorldGenRegionRandom.State randomPredecessor,
            Mc263WorldGenRegionRandom randomFork,
            PersistedTemplateCellExecutor templateCellExecutor,
            PersistedFeatureExecutor featureExecutor) {
        Objects.requireNonNull(persisted, "persisted Village settlement request");
        Objects.requireNonNull(transaction, "persisted Village settlement transaction");
        Objects.requireNonNull(randomPredecessor, "Village WorldGenRegion random predecessor");
        Objects.requireNonNull(randomFork, "Village WorldGenRegion random fork");

        Preflight preflight = preflightPersisted(persisted.request, transaction);
        PersistedPlan plan = decodePersistedPlan(persisted, preflight);
        require(randomFork.snapshot().equals(randomPredecessor),
                "Village WorldGenRegion random fork/predecessor mismatch");

        WorldTransaction isolated = Objects.requireNonNull(transaction.fork(),
                "isolated persisted Village transaction");
        if (isolated == transaction) {
            throw new IllegalStateException("persisted Village transaction fork is not isolated");
        }
        RuntimeExecutor runtime = new RuntimeExecutor() {
            @Override public ExecutionResult executeTemplateCell(CellPlacement placement) {
                return templateCellExecutor.execute(isolated, placement, randomFork);
            }
            @Override public ExecutionResult executeConfiguredFeature(FeaturePlacement placement) {
                return featureExecutor.execute(isolated, placement, randomFork);
            }
            @Override public ExecutionResult executeStructureEntity(EntityPlacement placement) {
                return isolated.executeStructureEntity(placement, randomFork);
            }
        };
        Accumulator accumulator = new Accumulator(persisted.request, isolated, runtime);
        for (int ordinal = 0; ordinal < plan.pieces.size(); ordinal++) {
            PlacementPiece piece = plan.pieces.get(ordinal);
            if (!intersectsSourceChunk(piece, persisted.request.sourceChunkX,
                    persisted.request.sourceChunkZ)) continue;
            if (piece.type() == ElementType.LEGACY_SINGLE) {
                executeTemplatePiece(ordinal, piece, accumulator, preflight);
            } else if (piece.type() == ElementType.FEATURE) {
                executeFeaturePiece(ordinal, piece, accumulator, preflight);
            } else {
                throw new IllegalArgumentException("unsupported persisted Village piece type: "
                        + piece.type());
            }
        }

        Mc263WorldGenRegionRandom.State randomSuccessor = randomFork.snapshot();
        PersistedCarrierPayload carrier = accumulator.freezePersistedCarrier(
                plan.rawSuccessor, randomSuccessor, randomFork.continuationNextLongI64());
        RawTranscript raw = new RawTranscript(accumulator.rawBlockTicks(),
                accumulator.rawFluidTicks());
        Mc263StructureCarrier successorCarrier = plan.successorCarrier;
        String fingerprint = persistedPublicationFingerprint(persisted, randomPredecessor,
                randomSuccessor, raw.canonicalSha256(), carrier.canonicalSha256,
                successorCarrier.receiptSha256());
        PersistedSettlement settlement = new PersistedSettlement(persisted.request,
                persisted.carrier, successorCarrier, plan.rawPredecessorSha256,
                plan.rawSuccessorSha256, plan.rawSuccessor, randomPredecessor,
                randomSuccessor, raw, carrier, fingerprint);

        require(randomFork.snapshot().equals(randomSuccessor),
                "Village WorldGenRegion random changed before atomic publication");
        PersistedPublishReceipt receipt = Objects.requireNonNull(
                transaction.publishPersistedAtomically(isolated, settlement, randomPredecessor,
                        randomFork), "persisted Village publish receipt");
        validatePersistedPublishReceipt(receipt, settlement);
        require(randomFork.snapshot().equals(randomSuccessor),
                "Village atomic publisher mutated accepted WorldGenRegion random fork");
        return settlement;
    }

    /**
     * Pure LevelChunkTicks settlement. Raw encounter order is preserved separately by callers.
     * Destination groups retain first destination encounter order; each group is then packed by
     * ascending subTickOrder exactly as LevelChunkTicks.pack sorts the accepted queue.
     */
    public static EffectiveBlockTicks settleBlockTicks(List<RawBlockTick> rawTicks) {
        return settleTicks(rawTicks, false);
    }

    /**
     * Pure {@code LevelChunkTicks} settlement for the FTIK lane. Vanilla stores block and fluid
     * ticks in two independent {@code LevelChunkTicks} instances with the identical pack rule, so
     * only the row validator differs.
     */
    public static EffectiveBlockTicks settleFluidTicks(List<RawBlockTick> rawTicks) {
        return settleTicks(rawTicks, true);
    }

    /**
     * Projects one packed destination queue onto its chunk-local publication order. The
     * settlement carrier keeps the authenticated global subTickOrder for receipts; a
     * LevelChunkTicks publication starts its own encounter index at zero for each chunk.
     */
    public static List<RawBlockTick> publicationTicks(
            EffectiveBlockTicks effective, Chunk destinationChunk) {
        Objects.requireNonNull(effective, "Village effective tick carrier");
        Objects.requireNonNull(destinationChunk, "Village publication tick destination");
        for (PackedTickChunk packed : effective.destinationChunks()) {
            if (!packed.destinationChunk().equals(destinationChunk)) continue;
            ArrayList<RawBlockTick> published = new ArrayList<>(packed.ticks().size());
            for (int index = 0; index < packed.ticks().size(); index++) {
                RawBlockTick tick = packed.ticks().get(index);
                published.add(new RawBlockTick(tick.ordinal(), tick.destinationChunk(),
                        tick.position(), tick.blockKey(), tick.delay(), tick.priority(), index));
            }
            return List.copyOf(published);
        }
        return List.of();
    }

    private static EffectiveBlockTicks settleTicks(List<RawBlockTick> rawTicks, boolean fluid) {
        String lane = fluid ? "FTIK" : "BTIK";
        Objects.requireNonNull(rawTicks, "Village raw " + lane + " transcript");
        LinkedHashMap<TickIdentity, RawBlockTick> first = new LinkedHashMap<>();
        for (int ordinal = 0; ordinal < rawTicks.size(); ordinal++) {
            RawBlockTick tick = Objects.requireNonNull(rawTicks.get(ordinal),
                    "Village raw " + lane + " row " + ordinal);
            if (fluid) validateRawFluidTick(tick, ordinal);
            else validateRawBlockTick(tick, ordinal);
            first.putIfAbsent(new TickIdentity(tick.blockKey(), tick.position()), tick);
        }

        LinkedHashMap<Chunk, ArrayList<RawBlockTick>> byDestination = new LinkedHashMap<>();
        for (RawBlockTick tick : first.values()) {
            byDestination.computeIfAbsent(tick.destinationChunk(), ignored -> new ArrayList<>()).add(tick);
        }
        ArrayList<PackedTickChunk> packed = new ArrayList<>(byDestination.size());
        int accepted = 0;
        HashSet<TickIdentity> published = new HashSet<>();
        for (Map.Entry<Chunk, ArrayList<RawBlockTick>> entry : byDestination.entrySet()) {
            ArrayList<RawBlockTick> queue = entry.getValue();
            queue.sort(TICK_PACK_ORDER); // TimSort is stable for equal subTickOrder.
            for (RawBlockTick tick : queue) {
                require(published.add(new TickIdentity(tick.blockKey(), tick.position())),
                        "duplicate Village effective " + lane + " identity");
                accepted++;
            }
            packed.add(new PackedTickChunk(entry.getKey(), List.copyOf(queue)));
        }
        require(accepted == first.size(), "Village effective " + lane + " cardinality drift");
        return new EffectiveBlockTicks(List.copyOf(packed), rawTicks.size(), accepted,
                rawTicks.size() - accepted);
    }

    private static Preflight preflight(Request request, WorldTransaction transaction) {
        return preflight(request, transaction, true);
    }

    private static Preflight preflightPersisted(Request request, WorldTransaction transaction) {
        require(transaction.supportsPersistedWorldGenRegionRandomSettlement(),
                "persisted Village WorldGenRegion random publication unavailable");
        return preflight(request, transaction, false);
    }

    private static Preflight preflight(Request request, WorldTransaction transaction, boolean planningCapabilities) {
        ProducerSpec spec = Mc263VillageProducerGrammarAccess.require(request.structureKey);
        Mc263VillageProductionAuthority.Corpus authority = Mc263VillageProductionAuthority.pinned();
        Mc263VillageHierarchicalGrammar.Corpus hierarchy = authority.hierarchy();

        require(planningCapabilities ? transaction.supportsAtomicForkPublishWithRandom()
                        : transaction.supportsPersistedWorldGenRegionRandomSettlement(),
                "atomic Village world/sidecar/start/RNG publish required");
        require((!planningCapabilities || transaction.supportsHeightmap(HEIGHTMAP))
                        && transaction.supportsBuildHeightBoundary()
                        && transaction.minBuildY() == -64 && transaction.maxBuildY() == 320,
                "Village build/heightmap capability closure unavailable");
        require(transaction.supportsTemplateExecution()
                        && transaction.supportsConfiguredFeatureExecution()
                        && transaction.supportsEntityExecution()
                        && transaction.supportsTypedSidecars()
                        && transaction.supportsRawBlockTickTranscript()
                        && transaction.supportsEffectiveBlockTickCarrier()
                        && transaction.supportsOwnerLane()
                        && transaction.supportsEmptyUnauthenticatedLanes(),
                "Village settlement capability closure unavailable");
        require(transaction.supportsWriteFlags(TEMPLATE_WRITE_FLAGS)
                        && transaction.supportsWriteFlags(BLOCK_ENTITY_CLEAR_FLAGS),
                "Village write-flag closure unavailable");
        require(transaction.supportsLevelChunkTicksAuthority(LEVEL_CHUNK_TICKS_SHA256),
                "Village LevelChunkTicks authority capability unavailable");
        require(!planningCapabilities
                        || transaction.supportsBiomeAdmission(request.structureKey, spec.biomeTag()),
                "Village biome-admission capability unavailable");

        for (String pool : spec.poolCapabilities()) {
            authority.requirePool(pool);
            require(!planningCapabilities || transaction.supportsPool(pool),
                    "Village pool capability absent: " + pool);
        }
        authority.requireProcessorList("inline");
        require(transaction.supportsProcessorList("inline"),
                "Village processor-list capability absent: inline");
        for (String processor : spec.processorCapabilities()) {
            authority.requireProcessorList(processor);
            require(transaction.supportsProcessorList(processor),
                    "Village processor-list capability absent: " + processor);
        }
        for (String semantic : authority.processorSemanticsInOrder()) {
            require(transaction.supportsProcessorSemantic(semantic),
                    "Village processor semantic capability absent: " + semantic);
        }
        for (String feature : spec.featureCapabilities()) {
            authority.requireFeature(feature);
            require(transaction.supportsConfiguredFeature(feature),
                    "Village configured-feature capability absent: " + feature);
        }
        for (Mc263VillageProductionAuthority.LootIdentity loot : authority.sidecars().loot()) {
            require(transaction.supportsLootTable(loot.lootTable()),
                    "Village loot-table capability absent: " + loot.lootTable());
        }
        for (Mc263VillageProductionAuthority.BentIdentity bent : authority.sidecars().bent()) {
            require(transaction.supportsBlockEntity(bent.blockEntityType()),
                    "Village block-entity capability absent: " + bent.blockEntityType());
        }
        for (Mc263VillageProductionAuthority.EntityIdentity entity : authority.sidecars().entities()) {
            require(transaction.supportsStructureEntity(entity.entityType()),
                    "Village structure-entity capability absent: " + entity.entityType());
        }
        for (String type : BLOCK_TICK_TYPES) {
            require(transaction.supportsBlockTick(type, expectedDelay(type), 0),
                    "Village block-tick capability absent: " + type);
        }

        LinkedHashSet<String> exactStates = new LinkedHashSet<>();
        LinkedHashSet<String> writableStates = new LinkedHashSet<>();
        LinkedHashSet<String> allowedTemplates = new LinkedHashSet<>();
        for (String templateKey : spec.templateCapabilities()) {
            Mc263VillageHierarchicalGrammar.Template template = hierarchy.requireTemplate(templateKey);
            Mc263VillageProductionAuthority.TemplateAuthority promoted =
                    authority.requireTemplate(templateKey);
            require(template.blockCount() == promoted.blockCount()
                            && template.size().x() == promoted.sizeX()
                            && template.size().y() == promoted.sizeY()
                            && template.size().z() == promoted.sizeZ(),
                    "Village hierarchical production-authority drift: " + templateKey);
            require(transaction.supportsTemplate(templateKey),
                    "Village template capability absent: " + templateKey);
            allowedTemplates.add(templateKey);
            for (Mc263VillageHierarchicalGrammar.Binding binding : template.bindings()) {
                if ("inline".equals(binding.processorList())
                        || spec.processorCapabilities().contains(binding.processorList())) {
                    require(transaction.supportsProcessorList(binding.processorList()),
                            "Village bound processor-list capability absent: "
                                    + binding.processorList());
                }
            }
            for (String state : template.stateTable()) {
                for (Rotation rotation : Rotation.values()) {
                    String rotated = rotateState(state, rotation);
                    exactStates.add(rotated);
                    if (!TECHNICAL_MARKER_BLOCKS.contains(stateBlockKey(rotated))) {
                        writableStates.add(rotated);
                    }
                }
            }
            // StructureTemplate.placeInWorld never writes a technical marker cell: the
            // JigsawReplacementProcessor SinglePoolElement.getSettings installs ahead of every
            // element/projection processor substitutes a minecraft:jigsaw cell for its block
            // entity's final_state (minecraft:structure_void drops the cell outright), and
            // BlockIgnoreProcessor.STRUCTURE_BLOCK drops minecraft:structure_block. The runtime
            // representability demand is therefore the substituted final_state, taken value for
            // value from the authenticated connector semantics, not the marker palette entry.
            for (Mc263VillageHierarchicalGrammar.Cell cell : template.expand()) {
                if (cell.marker() == null) continue;
                String finalState = Mc263VillageProductionTransaction.connectorFinalState(
                        cell.marker().json());
                if (finalState == null) continue;
                for (Rotation rotation : Rotation.values()) {
                    String rotated = rotateState(finalState, rotation);
                    if (!TECHNICAL_MARKER_BLOCKS.contains(stateBlockKey(rotated))) {
                        writableStates.add(rotated);
                    }
                }
            }
        }
        exactStates.addAll(authority.exactStatesInOrder());
        writableStates.addAll(authority.exactStatesInOrder());
        for (String state : writableStates) {
            require(transaction.supportsExactState(state),
                    "Village runtime exact-state capability absent: " + state);
        }
        return new Preflight(spec, hierarchy, authority, Set.copyOf(allowedTemplates),
                Set.copyOf(exactStates));
    }

    private static Mc263VillageProducer.Start regenerate(Request request,
            WorldTransaction transaction) {
        LegacyRand generationRandom = new LegacyRand(0L);
        CapturingPublisher publisher = new CapturingPublisher(request.structureKey);
        Mc263VillageProducer.Attempt attempt = Mc263VillageProducer.pinned().attempt(
                request.structureKey, request.worldSeed, request.startChunkX, request.startChunkZ,
                generationRandom, new ProducerWorld(transaction), new ProducerBiome(transaction), publisher);
        require(attempt instanceof Mc263VillageProducer.Accepted,
                "Village request did not produce a lawful start");
        Mc263VillageProducer.Start start = ((Mc263VillageProducer.Accepted) attempt).start();
        require(publisher.start == start, "Village producer publication capture drift");
        require(Mc263VillageProducer.CARRIER_FORMAT.equals(start.carrier().format()),
                "Village carrier format drift");
        return start;
    }

    private interface PlacementPiece {
        ElementType type();
        String elementKey();
        int originX(); int originY(); int originZ();
        Rotation rotation();
        Projection projection();
        String processor();
        Mc263JigsawStructureBoundary.Bounds bounds();
    }

    private static final class LegacyPlacementPiece implements PlacementPiece {
        private final PiecePlacement value;
        private LegacyPlacementPiece(PiecePlacement value) {
            this.value = Objects.requireNonNull(value);
        }
        @Override public ElementType type() { return value.type(); }
        @Override public String elementKey() { return value.elementKey(); }
        @Override public int originX() { return value.originX(); }
        @Override public int originY() { return value.originY(); }
        @Override public int originZ() { return value.originZ(); }
        @Override public Rotation rotation() { return value.rotation(); }
        @Override public Projection projection() { return value.projection(); }
        @Override public String processor() { return value.processor(); }
        @Override public Mc263JigsawStructureBoundary.Bounds bounds() { return value.bounds(); }
    }

    private record PersistedPlacementPiece(ElementType type, String elementKey,
            int originX, int originY, int originZ, Rotation rotation, Projection projection,
            String processor, Mc263JigsawStructureBoundary.Bounds bounds) implements PlacementPiece {
        private PersistedPlacementPiece {
            Objects.requireNonNull(type); Objects.requireNonNull(elementKey);
            Objects.requireNonNull(rotation); Objects.requireNonNull(projection);
            Objects.requireNonNull(processor); Objects.requireNonNull(bounds);
        }
    }

    private static void executeTemplatePiece(int pieceOrdinal, PlacementPiece piece,
            Accumulator accumulator, Preflight preflight) {
        require(preflight.allowedTemplates.contains(piece.elementKey()),
                "unbound Village template piece: " + piece.elementKey());
        Mc263VillageHierarchicalGrammar.Template template =
                preflight.hierarchy.requireTemplate(piece.elementKey());
        String processor = piece.processor().isEmpty() ? "inline" : piece.processor();
        preflight.authority.requireProcessorList(processor);
        List<String> placementProcessors = preflight.authority.placementProcessors(
                piece.elementKey(), projectionName(piece.projection()), processor);
        require(template.accepts(projectionName(piece.projection()), processor,
                        placementProcessors),
                "Village hierarchical processor/projection binding drift: " + piece.elementKey());

        for (Mc263VillageHierarchicalGrammar.Cell cell : template.expand()) {
            Position base = transformBlock(cell.position().x(), cell.position().y(), cell.position().z(),
                    piece.rotation(), piece.originX(), piece.originY(), piece.originZ());
            if (!insideSourceChunk(base.x, base.z, accumulator.request.sourceChunkX,
                    accumulator.request.sourceChunkZ)) continue;
            String rotated = rotateState(cell.state(), piece.rotation());
            require(preflight.exactStates.contains(rotated),
                    "unpreflighted Village exact state: " + rotated);
            CellPlacement placement = new CellPlacement(pieceOrdinal, piece.elementKey(),
                    cell.ordinal(), cell.stateIndex(), cell.pass(), cell.position().x(), cell.position().y(),
                    cell.position().z(), base, rotated, piece.rotation().name(),
                    projectionName(piece.projection()), processor, placementProcessors,
                    cell.marker() == null ? "" : cell.marker().op(),
                    cell.marker() == null ? "" : cell.marker().json());
            ExecutionResult result = Objects.requireNonNull(
                    accumulator.executeTemplateCell(placement),
                    "Village template-cell execution result");
            accumulator.accept(result, Source.template(pieceOrdinal, piece.elementKey(), cell.ordinal()),
                    false);
        }

        List<Mc263VillageHierarchicalGrammar.EntityPlacement> entities = template.entities();
        for (Mc263VillageHierarchicalGrammar.EntityPlacement entity : entities) {
            Position block = transformBlock(entity.blockPosition().x(), entity.blockPosition().y(),
                    entity.blockPosition().z(), piece.rotation(), piece.originX(), piece.originY(),
                    piece.originZ());
            if (!insideSourceChunk(block.x, block.z, accumulator.request.sourceChunkX,
                    accumulator.request.sourceChunkZ)) continue;
            DoublePosition position = transformEntity(entity.position().x(), entity.position().y(),
                    entity.position().z(), piece.rotation(), piece.originX(), piece.originY(),
                    piece.originZ());
            EntityPlacement placement = new EntityPlacement(pieceOrdinal, piece.elementKey(),
                    entity.ordinal(), entity.semantic().json(), position, block, piece.rotation().name());
            ExecutionResult result = Objects.requireNonNull(
                    accumulator.executeStructureEntity(placement),
                    "Village structure-entity execution result");
            accumulator.accept(result, Source.entity(pieceOrdinal, piece.elementKey(), entity.ordinal()),
                    false);
        }
    }

    private static void executeFeaturePiece(int pieceOrdinal, PlacementPiece piece,
            Accumulator accumulator, Preflight preflight) {
        require(preflight.spec.featureCapabilities().contains(piece.elementKey()),
                "unbound Village configured-feature piece: " + piece.elementKey());
        Mc263VillageProductionAuthority.Feature feature =
                preflight.authority.requireFeature(piece.elementKey());
        Position origin = new Position(piece.originX(), piece.originY(), piece.originZ());
        FeaturePlacement placement = new FeaturePlacement(pieceOrdinal, piece.elementKey(), origin,
                piece.rotation().name(), projectionName(piece.projection()),
                feature.configuredTarget());
        ExecutionResult result = Objects.requireNonNull(
                accumulator.executeConfiguredFeature(placement),
                "Village configured-feature execution result");
        accumulator.accept(result, Source.feature(pieceOrdinal, piece.elementKey()), true);
    }

    private static String normalizedProcessor(String processor) {
        return processor == null || processor.isEmpty() ? "inline" : processor;
    }

    private static void validateClip(Mc263VillageProducer.Start start, int chunkX, int chunkZ,
            int ordinal) {
        Mc263VillageProducer.Box box = start.aggregateBoundingBox();
        int minChunkX = Math.floorDiv(box.minX(), 16);
        int maxChunkX = Math.floorDiv(box.maxX(), 16);
        int minChunkZ = Math.floorDiv(box.minZ(), 16);
        int maxChunkZ = Math.floorDiv(box.maxZ(), 16);
        require(chunkX >= minChunkX && chunkX <= maxChunkX
                        && chunkZ >= minChunkZ && chunkZ <= maxChunkZ,
                "Village source clip does not intersect accepted aggregate bounds");
        int expected = Math.addExact(Math.multiplyExact(chunkZ - minChunkZ,
                maxChunkX - minChunkX + 1), chunkX - minChunkX);
        require(ordinal == expected, "Village source clip ordinal drift");
    }

    private static PersistedPlan decodePersistedPlan(PersistedRequest persisted, Preflight preflight) {
        Request request = persisted.request;
        Mc263StructureCarrier.ValidStart start = persisted.start;
        require(start.startKey().equals(request.structureKey + "@" + request.startChunkX + ","
                        + request.startChunkZ)
                        && start.originChunkX() == request.startChunkX
                        && start.originChunkZ() == request.startChunkZ,
                "persisted Village start/request identity mismatch");
        // Vanilla ChunkGenerator#createReferences runs in STRUCTURE_REFERENCES, before the
        // FEATURES step that calls StructureStart#placeInChunk, and placement never inspects the
        // counter (only isValid()/createTag do). A referenced start therefore still places, and
        // StructureStart#getMaxReferences()==1 with canBeReferenced()==references<max caps the
        // authenticated range at 0..1.
        require(start.references() >= 0 && start.references() <= MAX_START_REFERENCES,
                "persisted Village start references outside the authenticated 0.."
                        + MAX_START_REFERENCES + " range: " + start.references());
        Mc263StructureCarrier.StructureDefinition definition =
                persisted.carrier.registry().require(request.structureKey);
        require(definition.terrainAdjustment() == Mc263StructureCarrier.TerrainAdjustment.BEARD_THIN,
                "persisted Village terrain-adjustment drift");

        Mc263StructureCarrier.ChunkReferences storedReferences = persisted.carrier
                .referenceChunk(request.sourceChunkX, request.sourceChunkZ)
                .orElseThrow(() -> new IllegalArgumentException(
                        "persisted Village source references are absent"));
        require(storedReferences.equals(persisted.references),
                "persisted Village source references disagree with carrier");
        // ChunkGenerator#applyBiomeDecoration places every start of
        // StructureManager#startsForStructure(SectionPos, Structure) -- a List built from the whole
        // reference LongSet -- so a source chunk between two neighbouring village boxes authentically
        // resolves two starts. The persisted request is bound to exactly one of them; a closure that
        // repeats a start is still rejected, because ChunkStarts holds one start per (origin chunk,
        // structure) and ReferenceSet forbids a duplicate origin.
        List<Mc263StructureCarrier.ValidStart> resolved = persisted.carrier.resolveStarts(
                storedReferences, request.structureKey);
        long resolvedMatches = resolved.stream().filter(start::equals).count();
        long resolvedDistinct = resolved.stream()
                .map(Mc263StructureCarrier.ValidStart::startKey).distinct().count();
        require(resolvedMatches == 1 && resolvedDistinct == resolved.size(),
                "persisted Village source references do not resolve the exact start");
        validatePersistedClip(start, request.sourceChunkX, request.sourceChunkZ,
                request.clipOrdinal);

        Mc263StructureCarrier.RawStartPayload bound = persisted.carrier.requireRawStartPayload(
                request.structureKey, start);
        require(rawStartEquals(bound, persisted.rawStart),
                "persisted Village raw-start facts disagree with carrier");
        byte[] predecessor = bound.predecessorBinaryNbtCompound();
        byte[] successor = bound.successorBinaryNbtCompound();
        DecodedStart decodedPredecessor = decodeRawStart(predecessor, request, start, preflight);
        DecodedStart decodedSuccessor = decodeRawStart(successor, request, start, preflight);
        // STRRAW01 is the producer's own pinned pair: the zero-reference start it encoded and the
        // exact one-reference successor. It is never re-encoded, so the pair stays 0/1 whatever the
        // runtime counter reached; the typed start binds to whichever blob carries its own count.
        require(decodedPredecessor.references == 0,
                "persisted Village raw predecessor is not the zero-reference producer receipt");
        require(decodedSuccessor.references == MAX_START_REFERENCES,
                "persisted Village raw successor is not the one-reference producer receipt");
        require(start.references() == (start.references() == 0
                        ? decodedPredecessor.references : decodedSuccessor.references),
                "persisted Village typed/raw reference binding drift");
        require(decodedPredecessor.referenceOffset == decodedSuccessor.referenceOffset,
                "persisted Village mutable reference offset drift");
        require(onlyReferenceCountDiffers(predecessor, successor,
                        decodedPredecessor.referenceOffset),
                "persisted Village successor mutates data other than references");

        Mc263StructureCarrier successorCarrier = persistedSuccessorCarrier(persisted, successor,
                bound.successorSha256());
        return new PersistedPlan(decodedPredecessor.pieces, predecessor, successor,
                bound.predecessorSha256(), bound.successorSha256(), successorCarrier);
    }

    private static void validatePersistedClip(Mc263StructureCarrier.ValidStart start,
            int chunkX, int chunkZ, int ordinal) {
        Mc263StructureCarrier.BoundingBox box = start.adjustedBoundingBox();
        int minChunkX = Math.floorDiv(box.minX(), 16), maxChunkX = Math.floorDiv(box.maxX(), 16);
        int minChunkZ = Math.floorDiv(box.minZ(), 16), maxChunkZ = Math.floorDiv(box.maxZ(), 16);
        require(chunkX >= minChunkX && chunkX <= maxChunkX
                        && chunkZ >= minChunkZ && chunkZ <= maxChunkZ,
                "persisted Village source clip escaped adjusted bounding box");
        int expected = Math.addExact(Math.multiplyExact(chunkZ - minChunkZ,
                maxChunkX - minChunkX + 1), chunkX - minChunkX);
        require(ordinal == expected, "persisted Village source clip ordinal drift");
    }

    private static boolean rawStartEquals(Mc263StructureCarrier.RawStartPayload left,
            Mc263StructureCarrier.RawStartPayload right) {
        return left.structureId().equals(right.structureId())
                && left.startKey().equals(right.startKey())
                && left.originChunkX() == right.originChunkX()
                && left.originChunkZ() == right.originChunkZ()
                && left.predecessorSha256().equals(right.predecessorSha256())
                && left.successorSha256().equals(right.successorSha256())
                && Arrays.equals(left.predecessorBinaryNbtCompound(),
                        right.predecessorBinaryNbtCompound())
                && Arrays.equals(left.successorBinaryNbtCompound(),
                        right.successorBinaryNbtCompound());
    }

    private static boolean onlyReferenceCountDiffers(byte[] predecessor, byte[] successor,
            int referenceOffset) {
        if (predecessor.length != successor.length || referenceOffset < 0
                || referenceOffset + Integer.BYTES > predecessor.length) return false;
        for (int index = 0; index < predecessor.length; index++) {
            if (index >= referenceOffset && index < referenceOffset + Integer.BYTES) continue;
            if (predecessor[index] != successor[index]) return false;
        }
        return true;
    }

    private static Mc263StructureCarrier persistedSuccessorCarrier(PersistedRequest persisted,
            byte[] rawSuccessor, String expectedRawSuccessorSha256) {
        Request request = persisted.request;
        if (persisted.start.references() >= MAX_START_REFERENCES) {
            // canBeReferenced() is false at the authenticated ceiling, so vanilla adds nothing:
            // every further source chunk of the same start commits an identity STR successor.
            return persisted.carrier;
        }
        ArrayList<Mc263StructureCarrier.ChunkStarts> chunks = new ArrayList<>();
        Mc263StructureCarrier.ValidStart advanced = null;
        int replacements = 0;
        for (Mc263StructureCarrier.ChunkStarts chunk : persisted.carrier.startChunks()) {
            ArrayList<Mc263StructureCarrier.StartEntry> starts = new ArrayList<>();
            for (Mc263StructureCarrier.StartEntry entry : chunk.orderedStarts()) {
                if (entry.structureId().equals(request.structureKey)
                        && entry.body() instanceof Mc263StructureCarrier.ValidStart candidate
                        && candidate.equals(persisted.start)) {
                    advanced = new Mc263StructureCarrier.ValidStart(candidate.startKey(),
                            candidate.originChunkX(), candidate.originChunkZ(),
                            Math.addExact(candidate.references(), 1), candidate.adjustedBoundingBox(),
                            candidate.orderedPieces());
                    starts.add(new Mc263StructureCarrier.StartEntry(request.structureKey, advanced));
                    replacements++;
                } else {
                    starts.add(entry);
                }
            }
            chunks.add(chunk.withStarts(starts));
        }
        require(replacements == 1 && advanced != null,
                "persisted Village carrier lost exact mutable start");
        Mc263StructureCarrier candidate = new Mc263StructureCarrier(persisted.carrier.registry(),
                chunks, persisted.carrier.referenceChunks(), persisted.carrier.rawStartPayloads(),
                persisted.carrier.producerGraphPayloads());
        return candidate.replaceRawStartSuccessor(request.structureKey, advanced,
                expectedRawSuccessorSha256, rawSuccessor);
    }

    private static DecodedStart decodeRawStart(byte[] bytes, Request request,
            Mc263StructureCarrier.ValidStart typed, Preflight preflight) {
        VillageNbtCursor input = new VillageNbtCursor(bytes);
        input.expectByte(10, "Village raw start root type");
        input.expectUtf("", "Village raw start root name");
        input.expectTag(3, "references");
        int referenceOffset = input.position();
        int references = input.int32();
        input.expectTag(3, "ChunkZ");
        require(input.int32() == request.startChunkZ, "persisted Village raw ChunkZ drift");
        input.expectTag(8, "id");
        require(input.utf().equals(request.structureKey), "persisted Village raw id drift");
        input.expectTag(9, "Children");
        input.expectByte(10, "Village Children element type");
        int count = input.int32();
        require(count == typed.orderedPieces().size() && count > 0,
                "persisted Village raw Children cardinality drift");
        ArrayList<PlacementPiece> pieces = new ArrayList<>(count);
        for (int ordinal = 0; ordinal < count; ordinal++) {
            pieces.add(decodeRawPiece(input, typed.orderedPieces().get(ordinal), ordinal, preflight));
        }
        input.expectTag(3, "ChunkX");
        require(input.int32() == request.startChunkX, "persisted Village raw ChunkX drift");
        input.expectByte(0, "Village raw start end");
        require(input.exhausted(), "persisted Village raw start trailing bytes");
        return new DecodedStart(references, referenceOffset, List.copyOf(pieces));
    }

    private static PlacementPiece decodeRawPiece(VillageNbtCursor input,
            Mc263StructureCarrier.Piece typed, int ordinal, Preflight preflight) {
        int bodyStart = input.position();
        require(typed.pieceType().equals("minecraft:jigsaw") && typed.poolElement(),
                "persisted Village typed piece identity drift at " + ordinal);
        input.expectTag(11, "BB");
        require(input.int32() == 6, "persisted Village BB width drift at " + ordinal);
        Mc263StructureCarrier.BoundingBox box = new Mc263StructureCarrier.BoundingBox(
                input.int32(), input.int32(), input.int32(), input.int32(), input.int32(), input.int32());
        require(box.equals(typed.boundingBox()), "persisted Village BB fact drift at " + ordinal);
        input.expectTag(3, "PosZ"); int originZ = input.int32();
        input.expectTag(3, "PosX"); int originX = input.int32();
        input.expectTag(10, "pool_element");
        input.expectByte(8, "Village pool element first field type");
        String identityField = input.utf();
        require(identityField.equals("location") || identityField.equals("feature"),
                "persisted Village unknown pool element identity field");
        String elementKey = input.utf();
        ElementType type;
        String processor = "";
        if (identityField.equals("location")) {
            type = ElementType.LEGACY_SINGLE;
            int processorType = input.u8();
            require(processorType == 8 || processorType == 10,
                    "persisted Village processor encoding drift at " + ordinal);
            input.expectUtf("processors", "Village processors field");
            if (processorType == 8) {
                processor = input.utf();
                require(!processor.isEmpty(), "persisted Village empty processor registry key");
            } else {
                input.expectTag(9, "processors");
                input.expectByte(0, "Village inline processor list element type");
                require(input.int32() == 0, "persisted Village inline processor list is nonempty");
                input.expectByte(0, "Village inline processor compound end");
            }
        } else {
            type = ElementType.FEATURE;
        }
        input.expectTag(8, "projection");
        Projection projection = decodePlacementProjection(input.utf());
        input.expectTag(8, "element_type");
        String elementType = input.utf();
        require(elementType.equals(type == ElementType.LEGACY_SINGLE
                        ? "minecraft:legacy_single_pool_element" : "minecraft:feature_pool_element"),
                "persisted Village pool element type drift at " + ordinal);
        input.expectByte(0, "Village pool element end");
        input.expectTag(3, "PosY"); int originY = input.int32();
        input.expectTag(8, "rotation"); Rotation rotation = decodeRotation(input.utf());
        input.expectTag(8, "id");
        require(input.utf().equals("minecraft:jigsaw"),
                "persisted Village piece id drift at " + ordinal);
        input.expectTag(3, "GD");
        require(input.int32() == 0, "persisted Village GD drift at " + ordinal);
        input.expectTag(3, "O");
        require(input.int32() == -1, "persisted Village O drift at " + ordinal);
        input.expectTag(3, "ground_level_delta"); int groundLevelDelta = input.int32();
        require(groundLevelDelta == typed.groundLevelDelta(),
                "persisted Village ground-level delta drift at " + ordinal);
        input.expectTag(9, "junctions");
        input.expectByte(10, "Village junction list element type");
        int junctionCount = input.int32();
        require(junctionCount == typed.junctions().size(),
                "persisted Village junction cardinality drift at " + ordinal);
        for (int index = 0; index < junctionCount; index++) {
            Mc263StructureCarrier.Junction expected = typed.junctions().get(index);
            input.expectTag(3, "source_z"); int sourceZ = input.int32();
            input.expectTag(3, "source_x"); int sourceX = input.int32();
            input.expectTag(3, "delta_y"); int deltaY = input.int32();
            input.expectTag(3, "source_ground_y"); int sourceGroundY = input.int32();
            input.expectTag(8, "dest_proj"); Projection destination = decodePlacementProjection(input.utf());
            input.expectByte(0, "Village junction compound end");
            require(sourceX == expected.sourceX() && sourceZ == expected.sourceZ()
                            && sourceGroundY == expected.sourceGroundY() && deltaY == expected.deltaY()
                            && carrierProjection(destination) == expected.destinationProjection(),
                    "persisted Village junction fact drift at " + ordinal + ":" + index);
        }
        input.expectByte(0, "Village piece compound end");
        require(input.rootBodyEquals(bodyStart, typed.persistedPayload().binaryNbtCompound()),
                "persisted Village raw piece bytes disagree with typed payload at " + ordinal);
        require(carrierProjection(projection) == typed.projection(),
                "persisted Village projection fact drift at " + ordinal);

        String normalized = normalizedProcessor(processor);
        if (type == ElementType.LEGACY_SINGLE) {
            require(preflight.allowedTemplates.contains(elementKey),
                    "persisted Village unknown template: " + elementKey);
            preflight.authority.requireProcessorList(normalized);
            Mc263VillageHierarchicalGrammar.Template template = preflight.hierarchy.requireTemplate(elementKey);
            require(template.accepts(projectionName(projection), normalized,
                            preflight.authority.placementProcessors(elementKey,
                                    projectionName(projection), normalized)),
                    "persisted Village template processor/projection binding drift: " + elementKey);
        } else {
            require(preflight.spec.featureCapabilities().contains(elementKey),
                    "persisted Village unknown configured feature: " + elementKey);
            preflight.authority.requireFeature(elementKey);
        }
        return new PersistedPlacementPiece(type, elementKey, originX, originY, originZ, rotation,
                projection, processor, new Mc263JigsawStructureBoundary.Bounds(box.minX(), box.minY(),
                        box.minZ(), box.maxX(), box.maxY(), box.maxZ()));
    }

    private static Projection decodePlacementProjection(String value) {
        return switch (value) {
            case "rigid" -> Projection.RIGID;
            case "terrain_matching" -> Projection.TERRAIN_MATCHING;
            default -> throw new IllegalArgumentException("unknown persisted Village projection: " + value);
        };
    }

    private static Mc263StructureCarrier.Projection carrierProjection(Projection value) {
        return value == Projection.RIGID ? Mc263StructureCarrier.Projection.RIGID
                : Mc263StructureCarrier.Projection.TERRAIN_MATCHING;
    }

    private static Rotation decodeRotation(String value) {
        try {
            return Rotation.valueOf(value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("unknown persisted Village rotation: " + value, failure);
        }
    }

    private record DecodedStart(int references, int referenceOffset, List<PlacementPiece> pieces) {
        private DecodedStart {
            pieces = List.copyOf(pieces);
        }
    }

    private record PersistedPlan(List<PlacementPiece> pieces, byte[] rawPredecessor,
            byte[] rawSuccessor, String rawPredecessorSha256, String rawSuccessorSha256,
            Mc263StructureCarrier successorCarrier) {
        private PersistedPlan {
            pieces = List.copyOf(pieces);
            rawPredecessor = rawPredecessor.clone(); rawSuccessor = rawSuccessor.clone();
            Objects.requireNonNull(rawPredecessorSha256); Objects.requireNonNull(rawSuccessorSha256);
            Objects.requireNonNull(successorCarrier);
        }
    }

    private static final class VillageNbtCursor {
        private final byte[] bytes;
        private int position;
        private VillageNbtCursor(byte[] bytes) {
            this.bytes = Objects.requireNonNull(bytes, "persisted Village raw NBT").clone();
        }
        private int position() { return position; }
        private boolean exhausted() { return position == bytes.length; }
        private int u8() {
            require(position < bytes.length, "truncated persisted Village raw NBT");
            return bytes[position++] & 0xff;
        }
        private int u16() {
            require(position + 2 <= bytes.length, "truncated persisted Village UTF length");
            int value = (bytes[position] & 0xff) << 8 | bytes[position + 1] & 0xff;
            position += 2;
            return value;
        }
        private int int32() {
            require(position + 4 <= bytes.length, "truncated persisted Village int");
            int value = (bytes[position] & 0xff) << 24 | (bytes[position + 1] & 0xff) << 16
                    | (bytes[position + 2] & 0xff) << 8 | bytes[position + 3] & 0xff;
            position += 4;
            return value;
        }
        private String utf() {
            int length = u16();
            require(position + length <= bytes.length, "truncated persisted Village UTF payload");
            for (int index = 0; index < length; index++) {
                require((bytes[position + index] & 0x80) == 0,
                        "non-ASCII persisted Village production string");
            }
            String value = new String(bytes, position, length, StandardCharsets.US_ASCII);
            position += length;
            return value;
        }
        private void expectByte(int expected, String label) {
            require(u8() == expected, label + " drift");
        }
        private void expectUtf(String expected, String label) {
            require(utf().equals(expected), label + " drift");
        }
        private void expectTag(int type, String name) {
            expectByte(type, "Village tag type for " + name);
            expectUtf(name, "Village tag name for " + name);
        }
        private boolean rootBodyEquals(int bodyStart, byte[] rootPayload) {
            int bodyLength = position - bodyStart;
            if (bodyStart < 0 || bodyLength < 1 || rootPayload.length != bodyLength + 3
                    || rootPayload[0] != 10 || rootPayload[1] != 0 || rootPayload[2] != 0) {
                return false;
            }
            for (int index = 0; index < bodyLength; index++) {
                if (bytes[bodyStart + index] != rootPayload[index + 3]) return false;
            }
            return true;
        }
    }

    private static boolean intersectsSourceChunk(PlacementPiece piece, int chunkX, int chunkZ) {
        int minX = Math.multiplyExact(chunkX, 16), minZ = Math.multiplyExact(chunkZ, 16);
        int maxX = Math.addExact(minX, 15), maxZ = Math.addExact(minZ, 15);
        return piece.bounds().maxX() >= minX && piece.bounds().minX() <= maxX
                && piece.bounds().maxZ() >= minZ && piece.bounds().minZ() <= maxZ
                && piece.bounds().maxY() >= CLIP_MIN_Y && piece.bounds().minY() <= CLIP_MAX_Y;
    }

    private static boolean insideSourceChunk(int x, int z, int chunkX, int chunkZ) {
        return Math.floorDiv(x, 16) == chunkX && Math.floorDiv(z, 16) == chunkZ;
    }

    private static void validateRawBlockTick(RawBlockTick tick, int ordinal) {
        require(tick.ordinal == ordinal, "Village raw BTIK encounter ordinal drift");
        require(!fluidTickType(tick.blockKey),
                "Village fluid tick escaped the FTIK lane: " + tick.blockKey);
        require(BLOCK_TICK_TYPES.contains(tick.blockKey),
                "unknown Village raw BTIK type: " + tick.blockKey);
        require(tick.delay == expectedDelay(tick.blockKey) && tick.priority == 0,
                "Village raw BTIK delay/priority drift: " + tick.blockKey);
        require(tick.destinationChunk.equals(Chunk.from(tick.position)),
                "Village raw BTIK destination/position drift");
    }

    /** Fail-closed FTIK row check; only the authenticated water/lava delays are admitted. */
    private static void validateRawFluidTick(RawBlockTick tick, int ordinal) {
        require(tick.ordinal == ordinal, "Village raw FTIK encounter ordinal drift");
        Integer delay = FLUID_TICK_TYPES.get(tick.blockKey);
        require(delay != null, "unknown Village raw FTIK type: " + tick.blockKey);
        require(tick.delay == delay && tick.priority == 0,
                "Village raw FTIK delay/priority drift: " + tick.blockKey);
        require(tick.destinationChunk.equals(Chunk.from(tick.position)),
                "Village raw FTIK destination/position drift");
    }

    /** Block key of an exact state, i.e. the identifier ahead of its property list. */
    private static String stateBlockKey(String exactState) {
        int open = exactState.indexOf('[');
        return open < 0 ? exactState : exactState.substring(0, open);
    }

    private static int expectedDelay(String blockKey) {
        return "minecraft:sand".equals(blockKey) ? 2 : 1;
    }

    private static Map<String, Integer> fluidTickTypes() {
        LinkedHashMap<String, Integer> types = new LinkedHashMap<>();
        types.put("minecraft:water", 5);
        types.put("minecraft:lava", 30);
        return java.util.Collections.unmodifiableMap(types);
    }

    /** True when a raw scheduleTick encounter belongs to the FTIK lane rather than BTIK. */
    private static boolean fluidTickType(String key) {
        return FLUID_TICK_TYPES.containsKey(key);
    }

    /** Exact StructureTemplate block transform for mirror NONE and pivot ZERO. */
    public static Position transformBlock(int x, int y, int z, Rotation rotation,
            int originX, int originY, int originZ) {
        Objects.requireNonNull(rotation, "Village rotation");
        int rx, rz;
        switch (rotation) {
            case NONE -> { rx = x; rz = z; }
            case CLOCKWISE_90 -> { rx = -z; rz = x; }
            case CLOCKWISE_180 -> { rx = -x; rz = -z; }
            case COUNTERCLOCKWISE_90 -> { rx = z; rz = -x; }
            default -> throw new IllegalArgumentException("unknown Village rotation");
        }
        return new Position(Math.addExact(originX, rx), Math.addExact(originY, y),
                Math.addExact(originZ, rz));
    }

    /** Exact StructureTemplate Vec3 transform for mirror NONE and pivot ZERO. */
    public static DoublePosition transformEntity(double x, double y, double z, Rotation rotation,
            int originX, int originY, int originZ) {
        Objects.requireNonNull(rotation, "Village entity rotation");
        double rx, rz;
        switch (rotation) {
            case NONE -> { rx = x; rz = z; }
            case CLOCKWISE_90 -> { rx = 1.0D - z; rz = x; }
            case CLOCKWISE_180 -> { rx = 1.0D - x; rz = 1.0D - z; }
            case COUNTERCLOCKWISE_90 -> { rx = z; rz = 1.0D - x; }
            default -> throw new IllegalArgumentException("unknown Village entity rotation");
        }
        return new DoublePosition(originX + rx, originY + y, originZ + rz);
    }

    /** Rotates the complete directional property surface present in the accepted Village palettes. */
    public static String rotateState(String state, Rotation rotation) {
        Objects.requireNonNull(state, "Village exact state");
        Objects.requireNonNull(rotation, "Village rotation");
        String canonical = canonicalState(state);
        int open = canonical.indexOf('[');
        if (open < 0 || rotation == Rotation.NONE) return canonical;
        TreeMap<String, String> properties = parseProperties(canonical);
        TreeMap<String, String> rotated = new TreeMap<>();
        for (Map.Entry<String, String> property : properties.entrySet()) {
            String key = property.getKey();
            String value = property.getValue();
            if ("facing".equals(key)) value = rotateHorizontal(value, rotation);
            else if ("axis".equals(key) && quarterTurn(rotation)) {
                if ("x".equals(value)) value = "z";
                else if ("z".equals(value)) value = "x";
            } else if ("orientation".equals(key)) {
                value = rotateOrientation(value, rotation);
            }
            if (isHorizontal(key)) key = rotateHorizontal(key, rotation);
            require(rotated.put(key, value) == null,
                    "duplicate Village state property after rotation");
        }
        String block = canonical.substring(0, open);
        StringBuilder result = new StringBuilder(block).append('[');
        boolean first = true;
        for (Map.Entry<String, String> property : rotated.entrySet()) {
            if (!first) result.append(',');
            result.append(property.getKey()).append('=').append(property.getValue());
            first = false;
        }
        return result.append(']').toString();
    }

    private static String canonicalState(String state) {
        String value = state.trim();
        int open = value.indexOf('[');
        String block = open < 0 ? value : value.substring(0, open);
        require(block.matches("minecraft:[a-z0-9_./-]+"), "invalid Village state key: " + state);
        if (open < 0) return block;
        require(value.endsWith("]") && value.indexOf('[', open + 1) < 0,
                "malformed Village exact state: " + state);
        TreeMap<String, String> properties = parseProperties(value);
        require(!properties.isEmpty(), "empty Village state properties: " + state);
        StringBuilder result = new StringBuilder(block).append('[');
        boolean first = true;
        for (Map.Entry<String, String> property : properties.entrySet()) {
            if (!first) result.append(',');
            result.append(property.getKey()).append('=').append(property.getValue());
            first = false;
        }
        return result.append(']').toString();
    }

    private static TreeMap<String, String> parseProperties(String state) {
        int open = state.indexOf('[');
        TreeMap<String, String> properties = new TreeMap<>();
        if (open < 0) return properties;
        String body = state.substring(open + 1, state.length() - 1);
        for (String raw : body.split(",")) {
            int equals = raw.indexOf('=');
            require(equals > 0 && equals < raw.length() - 1 && raw.indexOf('=', equals + 1) < 0,
                    "malformed Village state property: " + raw);
            String key = raw.substring(0, equals).trim();
            String value = raw.substring(equals + 1).trim();
            require(!key.isEmpty() && !value.isEmpty() && properties.put(key, value) == null,
                    "duplicate/empty Village state property: " + raw);
        }
        return properties;
    }

    private static String rotateOrientation(String value, Rotation rotation) {
        String[] parts = value.split("_", -1);
        for (int index = 0; index < parts.length; index++) {
            parts[index] = rotateHorizontal(parts[index], rotation);
        }
        return String.join("_", parts);
    }

    private static String rotateHorizontal(String value, Rotation rotation) {
        if (!isHorizontal(value)) return value;
        List<String> order = List.of("north", "east", "south", "west");
        int turns = switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
        return order.get((order.indexOf(value) + turns) & 3);
    }

    private static boolean quarterTurn(Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
    }

    private static boolean isHorizontal(String value) {
        return "north".equals(value) || "east".equals(value)
                || "south".equals(value) || "west".equals(value);
    }

    private static String projectionName(Projection projection) {
        return projection == Projection.RIGID ? "rigid" : "terrain_matching";
    }

    private static String publicationFingerprint(Request request, Mc263VillageProducer.Start start,
            long incomingLo, long incomingHi, int incomingCount, String rawSha256,
            String payloadSha256) {
        String text = request.structureKey + '|' + request.worldSeed + '|' + request.startChunkX + '|'
                + request.startChunkZ + '|' + request.sourceChunkX + '|' + request.sourceChunkZ + '|'
                + request.clipOrdinal + '|' + start.carrier().structureStart().sha256() + '|'
                + Long.toUnsignedString(incomingLo) + '|' + Long.toUnsignedString(incomingHi) + '|'
                + incomingCount + '|' + rawSha256 + '|' + payloadSha256 + '|'
                + LEVEL_CHUNK_TICKS_SHA256 + '|'
                + Mc263VillageHierarchicalGrammar.RESOURCE_SHA256;
        return sha256(text.getBytes(StandardCharsets.US_ASCII));
    }

    private static String persistedPublicationFingerprint(PersistedRequest persisted,
            Mc263WorldGenRegionRandom.State predecessor,
            Mc263WorldGenRegionRandom.State successor, String rawTranscriptSha256,
            String payloadSha256, String carrierSuccessorSha256) {
        Request request = persisted.request;
        Mc263StructureCarrier.RawStartPayload raw = persisted.rawStart;
        String text = "VILLAGE-PERSISTED-SETTLEMENT-1|" + request.structureKey + '|'
                + request.worldSeed + '|' + request.startChunkX + '|' + request.startChunkZ + '|'
                + request.sourceChunkX + '|' + request.sourceChunkZ + '|' + request.clipOrdinal + '|'
                + persisted.carrier.receiptSha256() + '|' + carrierSuccessorSha256 + '|'
                + raw.predecessorSha256() + '|' + raw.successorSha256() + '|'
                + randomStateFingerprint(predecessor) + '|' + randomStateFingerprint(successor) + '|'
                + rawTranscriptSha256 + '|' + payloadSha256 + '|' + LEVEL_CHUNK_TICKS_SHA256 + '|'
                + Mc263VillageHierarchicalGrammar.RESOURCE_SHA256;
        return sha256(text.getBytes(StandardCharsets.US_ASCII));
    }

    private static String randomStateFingerprint(Mc263WorldGenRegionRandom.State state) {
        return Long.toUnsignedString(state.lo()) + ',' + Long.toUnsignedString(state.hi()) + ','
                + state.drawCount() + ',' + state.gaussianPresent() + ','
                + Long.toUnsignedString(state.gaussianBits());
    }

    private static void validatePersistedPublishReceipt(PersistedPublishReceipt receipt,
            PersistedSettlement settlement) {
        require(receipt.status == PublishStatus.COMMITTED || receipt.status == PublishStatus.REPLAYED,
                "unknown persisted Village publish status");
        require(receipt.carrierSuccessorSha256.equals(settlement.carrierSuccessor.receiptSha256()),
                "persisted Village published carrier successor drift");
        require(receipt.rawPredecessorSha256.equals(settlement.rawPredecessorSha256)
                        && receipt.rawSuccessorSha256.equals(settlement.rawSuccessorSha256),
                "persisted Village published raw-start CAS drift");
        require(receipt.randomPredecessor.equals(settlement.randomPredecessor)
                        && receipt.randomSuccessor.equals(settlement.randomSuccessor),
                "persisted Village published WorldGenRegion random drift");
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    public static final class Request {
        private final String structureKey;
        private final long worldSeed;
        private final int startChunkX, startChunkZ, sourceChunkX, sourceChunkZ, clipOrdinal;

        public Request(String structureKey, long worldSeed, int startChunkX, int startChunkZ,
                int sourceChunkX, int sourceChunkZ, int clipOrdinal) {
            this.structureKey = Objects.requireNonNull(structureKey, "Village structure key");
            if (clipOrdinal < 0) throw new IllegalArgumentException("negative Village clip ordinal");
            this.worldSeed = worldSeed; this.startChunkX = startChunkX; this.startChunkZ = startChunkZ;
            this.sourceChunkX = sourceChunkX; this.sourceChunkZ = sourceChunkZ;
            this.clipOrdinal = clipOrdinal;
        }
        public String structureKey() { return structureKey; } public long worldSeed() { return worldSeed; }
        public int startChunkX() { return startChunkX; } public int startChunkZ() { return startChunkZ; }
        public int sourceChunkX() { return sourceChunkX; } public int sourceChunkZ() { return sourceChunkZ; }
        public int clipOrdinal() { return clipOrdinal; }
    }

    public static final class Position {
        private final int x, y, z;
        public Position(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        public int x() { return x; } public int y() { return y; } public int z() { return z; }
        @Override public boolean equals(Object other) {
            return other instanceof Position value && x == value.x && y == value.y && z == value.z;
        }
        @Override public int hashCode() { return Objects.hash(x, y, z); }
        @Override public String toString() { return "[" + x + "," + y + "," + z + "]"; }
    }

    public static final class DoublePosition {
        private final double x, y, z;
        public DoublePosition(double x, double y, double z) {
            require(Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z),
                    "non-finite Village entity position");
            this.x = x; this.y = y; this.z = z;
        }
        public double x() { return x; } public double y() { return y; } public double z() { return z; }
    }

    public static final class Chunk {
        private final int x, z;
        public Chunk(int x, int z) { this.x = x; this.z = z; }
        public int x() { return x; } public int z() { return z; }
        public static Chunk from(Position position) {
            return new Chunk(Math.floorDiv(position.x, 16), Math.floorDiv(position.z, 16));
        }
        @Override public boolean equals(Object other) {
            return other instanceof Chunk value && x == value.x && z == value.z;
        }
        @Override public int hashCode() { return Objects.hash(x, z); }
        @Override public String toString() { return "[" + x + "," + z + "]"; }
    }

    public enum EffectKind {
        QUERY, WRITE, BLOCK_ENTITY, LOOT, ENTITY, BLOCK_TICK, POSTPROCESS
    }

    public static final class Operation {
        private final int ordinal;
        private final EffectKind kind;
        private final Position position;
        private final String detail;
        public Operation(int ordinal, EffectKind kind, Position position, String detail) {
            if (ordinal < 0) throw new IllegalArgumentException("negative Village operation ordinal");
            this.ordinal = ordinal; this.kind = Objects.requireNonNull(kind);
            this.position = position; this.detail = Objects.requireNonNull(detail);
        }
        public int ordinal() { return ordinal; } public EffectKind kind() { return kind; }
        public Position position() { return position; } public String detail() { return detail; }
    }

    public static final class Write {
        private final Position position;
        private final String exactState;
        private final int flags;
        private final boolean successful;
        public Write(Position position, String exactState, int flags, boolean successful) {
            this.position = Objects.requireNonNull(position); this.exactState = canonicalState(exactState);
            this.flags = flags; this.successful = successful;
        }
        public Position position() { return position; } public String exactState() { return exactState; }
        public int flags() { return flags; } public boolean successful() { return successful; }
    }

    public static final class BentPayload {
        private final Position position;
        private final String blockEntityType;
        private final byte[] canonicalNbt;
        public BentPayload(Position position, String blockEntityType, byte[] canonicalNbt) {
            this.position = Objects.requireNonNull(position); this.blockEntityType = Objects.requireNonNull(blockEntityType);
            this.canonicalNbt = Objects.requireNonNull(canonicalNbt).clone();
            require(this.canonicalNbt.length > 0, "empty Village BENT payload");
        }
        public Position position() { return position; } public String blockEntityType() { return blockEntityType; }
        public byte[] canonicalNbt() { return canonicalNbt.clone(); }
        public String canonicalNbtSha256() { return sha256(canonicalNbt); }
    }

    public static final class LootPayload {
        private final Position position;
        private final String lootTable;
        private final long seed;
        public LootPayload(Position position, String lootTable, long seed) {
            this.position = Objects.requireNonNull(position); this.lootTable = Objects.requireNonNull(lootTable);
            this.seed = seed;
        }
        public Position position() { return position; } public String lootTable() { return lootTable; }
        public long seed() { return seed; }
    }

    public static final class EntityPayload {
        private final String entityKey;
        private final DoublePosition position;
        private final byte[] canonicalNbt;
        public EntityPayload(String entityKey, DoublePosition position, byte[] canonicalNbt) {
            this.entityKey = Objects.requireNonNull(entityKey); this.position = Objects.requireNonNull(position);
            this.canonicalNbt = Objects.requireNonNull(canonicalNbt).clone();
            require(this.canonicalNbt.length > 0, "empty Village ENTS payload");
        }
        public String entityKey() { return entityKey; } public DoublePosition position() { return position; }
        public byte[] canonicalNbt() { return canonicalNbt.clone(); }
        public String canonicalNbtSha256() { return sha256(canonicalNbt); }
    }

    public static final class RawBlockTick {
        private final int ordinal;
        private final Chunk destinationChunk;
        private final Position position;
        private final String blockKey;
        private final int delay, priority;
        private final long subTickOrder;
        public RawBlockTick(int ordinal, Chunk destinationChunk, Position position, String blockKey,
                int delay, int priority, long subTickOrder) {
            if (ordinal < 0) throw new IllegalArgumentException("negative Village raw BTIK ordinal");
            this.ordinal = ordinal; this.destinationChunk = Objects.requireNonNull(destinationChunk);
            this.position = Objects.requireNonNull(position); this.blockKey = Objects.requireNonNull(blockKey);
            this.delay = delay; this.priority = priority; this.subTickOrder = subTickOrder;
        }
        public int ordinal() { return ordinal; } public Chunk destinationChunk() { return destinationChunk; }
        public Position position() { return position; } public String blockKey() { return blockKey; }
        public int delay() { return delay; } public int priority() { return priority; }
        public long subTickOrder() { return subTickOrder; }
    }

    public static final class PackedTickChunk {
        private final Chunk destinationChunk;
        private final List<RawBlockTick> ticks;
        private PackedTickChunk(Chunk destinationChunk, List<RawBlockTick> ticks) {
            this.destinationChunk = destinationChunk; this.ticks = List.copyOf(ticks);
        }
        public Chunk destinationChunk() { return destinationChunk; }
        public List<RawBlockTick> ticks() { return ticks; }
    }

    public static final class EffectiveBlockTicks {
        private final List<PackedTickChunk> destinationChunks;
        private final int rawCount, acceptedCount, duplicateCount;
        private EffectiveBlockTicks(List<PackedTickChunk> destinationChunks, int rawCount,
                int acceptedCount, int duplicateCount) {
            this.destinationChunks = List.copyOf(destinationChunks); this.rawCount = rawCount;
            this.acceptedCount = acceptedCount; this.duplicateCount = duplicateCount;
        }
        public List<PackedTickChunk> destinationChunks() { return destinationChunks; }
        public int rawCount() { return rawCount; } public int acceptedCount() { return acceptedCount; }
        public int duplicateCount() { return duplicateCount; }
        public List<RawBlockTick> flattened() {
            ArrayList<RawBlockTick> result = new ArrayList<>();
            for (PackedTickChunk chunk : destinationChunks) result.addAll(chunk.ticks);
            return List.copyOf(result);
        }
    }

    public static final class RawTranscript {
        private final List<RawBlockTick> blockTicks;
        private final List<RawBlockTick> fluidTicks;
        private final String canonicalSha256;
        private RawTranscript(List<RawBlockTick> blockTicks, List<RawBlockTick> fluidTicks) {
            this.blockTicks = List.copyOf(blockTicks);
            this.fluidTicks = List.copyOf(fluidTicks);
            StringBuilder encoded = new StringBuilder();
            for (RawBlockTick tick : this.blockTicks) {
                encoded.append(tick.ordinal).append('|').append(tick.destinationChunk).append('|')
                        .append(tick.position).append('|').append(tick.blockKey).append('|')
                        .append(tick.delay).append('|').append(tick.priority).append('|')
                        .append(tick.subTickOrder).append('\n');
            }
            // Empty FTIK keeps the pinned BTIK-only preimage byte-identical.
            for (RawBlockTick tick : this.fluidTicks) {
                encoded.append("F|").append(tick.ordinal).append('|').append(tick.destinationChunk)
                        .append('|').append(tick.position).append('|').append(tick.blockKey)
                        .append('|').append(tick.delay).append('|').append(tick.priority)
                        .append('|').append(tick.subTickOrder).append('\n');
            }
            this.canonicalSha256 = sha256(encoded.toString().getBytes(StandardCharsets.UTF_8));
        }
        public List<RawBlockTick> blockTicks() { return blockTicks; }
        public List<RawBlockTick> fluidTicks() { return fluidTicks; }
        public String canonicalSha256() { return canonicalSha256; }
    }

    public static final class CellPlacement {
        private final int pieceOrdinal, cellOrdinal, stateIndex, pass, localX, localY, localZ;
        private final String templateKey, exactState, rotation, projection, processorList;
        private final List<String> processors;
        private final Position transformedPosition;
        private final String markerOp, markerSemanticJson;
        private final String blockEntityType, lootTable;
        private CellPlacement(int pieceOrdinal, String templateKey, int cellOrdinal, int stateIndex,
                int pass, int localX, int localY, int localZ, Position transformedPosition, String exactState,
                String rotation, String projection, String processorList, List<String> processors,
                String markerOp, String markerSemanticJson) {
            this.pieceOrdinal = pieceOrdinal; this.templateKey = templateKey; this.cellOrdinal = cellOrdinal;
            this.stateIndex = stateIndex; this.pass = pass; this.localX = localX; this.localY = localY; this.localZ = localZ;
            this.transformedPosition = transformedPosition; this.exactState = exactState;
            this.rotation = rotation; this.projection = projection; this.processorList = processorList;
            this.processors = List.copyOf(processors); this.markerOp = markerOp;
            this.markerSemanticJson = markerSemanticJson;
            // Additive block-entity lane facts, read out of the persisted cell program's own DATA
            // marker semantics ({"kind":"EMPTY_BLOCK_ENTITY"|"LOOT_CONTAINER","blockEntityType":...,
            // "lootTable":...}); a CONNECTOR marker and a plain cell carry neither.
            this.blockEntityType = Mc263VillageProductionTransaction.dataBlockEntityType(
                    markerOp, markerSemanticJson);
            this.lootTable = Mc263VillageProductionTransaction.dataLootTable(
                    markerOp, markerSemanticJson);
        }
        public int pieceOrdinal() { return pieceOrdinal; } public String templateKey() { return templateKey; }
        public int cellOrdinal() { return cellOrdinal; } public int stateIndex() { return stateIndex; }
        public int pass() { return pass; }
        public int localX() { return localX; } public int localY() { return localY; } public int localZ() { return localZ; }
        public Position transformedPosition() { return transformedPosition; }
        public String exactState() { return exactState; } public String rotation() { return rotation; }
        public String projection() { return projection; } public String processorList() { return processorList; }
        public List<String> processors() { return processors; } public String markerOp() { return markerOp; }
        public String markerSemanticJson() { return markerSemanticJson; }
        /** Block-entity type this cell's DATA marker creates, or {@code null}. */
        public String blockEntityType() { return blockEntityType; }
        /** Loot table this cell's DATA marker seeds, or {@code null} when it is not a container. */
        public String lootTable() { return lootTable; }
    }

    public static final class FeaturePlacement {
        private final int pieceOrdinal;
        private final String featureKey, rotation, projection, configuredTarget;
        private final Position origin;
        private FeaturePlacement(int pieceOrdinal, String featureKey, Position origin, String rotation,
                String projection, String configuredTarget) {
            this.pieceOrdinal = pieceOrdinal; this.featureKey = featureKey; this.origin = origin;
            this.rotation = rotation; this.projection = projection; this.configuredTarget = configuredTarget;
        }
        public int pieceOrdinal() { return pieceOrdinal; } public String featureKey() { return featureKey; }
        public Position origin() { return origin; } public String rotation() { return rotation; }
        public String projection() { return projection; } public String configuredTarget() { return configuredTarget; }
    }

    public static final class EntityPlacement {
        private final int pieceOrdinal, entityOrdinal;
        private final String templateKey, semanticJson, rotation;
        private final DoublePosition position;
        private final Position blockPosition;
        private EntityPlacement(int pieceOrdinal, String templateKey, int entityOrdinal,
                String semanticJson, DoublePosition position, Position blockPosition, String rotation) {
            this.pieceOrdinal = pieceOrdinal; this.templateKey = templateKey; this.entityOrdinal = entityOrdinal;
            this.semanticJson = semanticJson; this.position = position; this.blockPosition = blockPosition;
            this.rotation = rotation;
        }
        public int pieceOrdinal() { return pieceOrdinal; } public String templateKey() { return templateKey; }
        public int entityOrdinal() { return entityOrdinal; } public String semanticJson() { return semanticJson; }
        public DoublePosition position() { return position; } public Position blockPosition() { return blockPosition; }
        public String rotation() { return rotation; }
    }

    /** One primitive invocation result. Lists are encounter ordered and must be complete. */
    public static final class ExecutionResult {
        private final List<Operation> operations;
        private final List<Write> writes;
        private final List<BentPayload> bent;
        private final List<LootPayload> loot;
        private final List<EntityPayload> entities;
        private final List<RawBlockTick> rawBlockTicks;
        public ExecutionResult(List<Operation> operations, List<Write> writes,
                List<BentPayload> bent, List<LootPayload> loot, List<EntityPayload> entities,
                List<RawBlockTick> rawBlockTicks) {
            this.operations = List.copyOf(operations); this.writes = List.copyOf(writes);
            this.bent = List.copyOf(bent); this.loot = List.copyOf(loot);
            this.entities = List.copyOf(entities); this.rawBlockTicks = List.copyOf(rawBlockTicks);
        }
        public static ExecutionResult empty() {
            return new ExecutionResult(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        public List<Operation> operations() { return operations; } public List<Write> writes() { return writes; }
        public List<BentPayload> bent() { return bent; } public List<LootPayload> loot() { return loot; }
        public List<EntityPayload> entities() { return entities; }
        public List<RawBlockTick> rawBlockTicks() { return rawBlockTicks; }
    }

    public static final class Owner {
        private final Position position;
        private final int sourcePieceOrdinal;
        private final String sourceIdentity;
        private Owner(Position position, int sourcePieceOrdinal, String sourceIdentity) {
            this.position = position; this.sourcePieceOrdinal = sourcePieceOrdinal;
            this.sourceIdentity = sourceIdentity;
        }
        public Position position() { return position; } public int sourcePieceOrdinal() { return sourcePieceOrdinal; }
        public String sourceIdentity() { return sourceIdentity; }
    }

    public static final class CarrierPayload {
        private final List<Operation> operations;
        private final List<Write> finalWrites;
        private final List<BentPayload> bent;
        private final List<LootPayload> loot;
        private final List<EntityPayload> entities;
        private final List<Owner> owners;
        private final EffectiveBlockTicks effectiveBlockTicks;
        private final EffectiveBlockTicks effectiveFluidTicks;
        private final Mc263VillageProducer.BinaryNbt mutableSuccessor;
        private final int placementRandomCount;
        private final List<Long> placementContinuation;
        private final String canonicalSha256;
        private CarrierPayload(List<Operation> operations, List<Write> finalWrites,
                List<BentPayload> bent, List<LootPayload> loot, List<EntityPayload> entities,
                List<Owner> owners, EffectiveBlockTicks effectiveBlockTicks,
                EffectiveBlockTicks effectiveFluidTicks,
                Mc263VillageProducer.BinaryNbt mutableSuccessor, int placementRandomCount,
                List<Long> placementContinuation) {
            this.operations = List.copyOf(operations); this.finalWrites = List.copyOf(finalWrites);
            this.bent = List.copyOf(bent); this.loot = List.copyOf(loot); this.entities = List.copyOf(entities);
            this.owners = List.copyOf(owners); this.effectiveBlockTicks = effectiveBlockTicks;
            this.effectiveFluidTicks = Objects.requireNonNull(effectiveFluidTicks,
                    "Village effective FTIK lane");
            this.mutableSuccessor = mutableSuccessor; this.placementRandomCount = placementRandomCount;
            this.placementContinuation = List.copyOf(placementContinuation);
            this.canonicalSha256 = hashCarrier(this);
        }
        public List<Operation> operations() { return operations; } public List<Write> finalWrites() { return finalWrites; }
        public List<BentPayload> bent() { return bent; } public List<LootPayload> loot() { return loot; }
        public List<EntityPayload> entities() { return entities; } public List<Owner> owners() { return owners; }
        public EffectiveBlockTicks effectiveBlockTicks() { return effectiveBlockTicks; }
        public EffectiveBlockTicks effectiveFluidTicks() { return effectiveFluidTicks; }
        public Mc263VillageProducer.BinaryNbt mutableSuccessor() { return mutableSuccessor; }
        public int placementRandomCount() { return placementRandomCount; }
        public List<Long> placementContinuationNextLongI64() { return placementContinuation; }
        public String canonicalSha256() { return canonicalSha256; }
    }

    public static final class Settlement {
        private final Request request;
        private final String predecessorSha256;
        private final RawTranscript rawTranscript;
        private final CarrierPayload carrier;
        private final String fingerprint;
        private Settlement(Request request, String predecessorSha256, RawTranscript rawTranscript,
                CarrierPayload carrier, String fingerprint) {
            this.request = request; this.predecessorSha256 = predecessorSha256;
            this.rawTranscript = rawTranscript; this.carrier = carrier; this.fingerprint = fingerprint;
        }
        public Request request() { return request; } public String predecessorSha256() { return predecessorSha256; }
        public RawTranscript rawTranscript() { return rawTranscript; } public CarrierPayload carrier() { return carrier; }
        public String fingerprint() { return fingerprint; }
    }

    public static final class PersistedRequest {
        private final Request request;
        private final Mc263StructureCarrier carrier;
        private final Mc263StructureCarrier.ChunkReferences references;
        private final Mc263StructureCarrier.ValidStart start;
        private final Mc263StructureCarrier.RawStartPayload rawStart;
        public PersistedRequest(Request request, Mc263StructureCarrier carrier,
                Mc263StructureCarrier.ChunkReferences references,
                Mc263StructureCarrier.ValidStart start,
                Mc263StructureCarrier.RawStartPayload rawStart) {
            this.request = Objects.requireNonNull(request, "persisted Village request");
            this.carrier = Objects.requireNonNull(carrier, "persisted Village carrier");
            this.references = Objects.requireNonNull(references, "persisted Village references");
            this.start = Objects.requireNonNull(start, "persisted Village start");
            this.rawStart = Objects.requireNonNull(rawStart, "persisted Village raw start");
        }
        public Request request() { return request; }
        public Mc263StructureCarrier carrier() { return carrier; }
        public Mc263StructureCarrier.ChunkReferences references() { return references; }
        public Mc263StructureCarrier.ValidStart start() { return start; }
        public Mc263StructureCarrier.RawStartPayload rawStart() { return rawStart; }
    }

    public static final class PersistedCarrierPayload {
        private final List<Operation> operations;
        private final List<Write> finalWrites;
        private final List<BentPayload> bent;
        private final List<LootPayload> loot;
        private final List<EntityPayload> entities;
        private final List<Owner> owners;
        private final EffectiveBlockTicks effectiveBlockTicks;
        private final EffectiveBlockTicks effectiveFluidTicks;
        private final byte[] mutableRawSuccessor;
        private final Mc263WorldGenRegionRandom.State worldGenRegionRandomSuccessor;
        private final List<Long> worldGenRegionContinuation;
        private final String canonicalSha256;
        private PersistedCarrierPayload(List<Operation> operations, List<Write> finalWrites,
                List<BentPayload> bent, List<LootPayload> loot, List<EntityPayload> entities,
                List<Owner> owners, EffectiveBlockTicks effectiveBlockTicks,
                EffectiveBlockTicks effectiveFluidTicks, byte[] mutableRawSuccessor,
                Mc263WorldGenRegionRandom.State worldGenRegionRandomSuccessor,
                List<Long> worldGenRegionContinuation) {
            this.operations = List.copyOf(operations); this.finalWrites = List.copyOf(finalWrites);
            this.bent = List.copyOf(bent); this.loot = List.copyOf(loot);
            this.entities = List.copyOf(entities); this.owners = List.copyOf(owners);
            this.effectiveBlockTicks = Objects.requireNonNull(effectiveBlockTicks);
            this.effectiveFluidTicks = Objects.requireNonNull(effectiveFluidTicks,
                    "Village persisted effective FTIK lane");
            this.mutableRawSuccessor = Objects.requireNonNull(mutableRawSuccessor).clone();
            this.worldGenRegionRandomSuccessor = Objects.requireNonNull(worldGenRegionRandomSuccessor);
            this.worldGenRegionContinuation = List.copyOf(worldGenRegionContinuation);
            this.canonicalSha256 = hashPersistedCarrier(this);
        }
        public List<Operation> operations() { return operations; }
        public List<Write> finalWrites() { return finalWrites; }
        public List<BentPayload> bent() { return bent; }
        public List<LootPayload> loot() { return loot; }
        public List<EntityPayload> entities() { return entities; }
        public List<Owner> owners() { return owners; }
        public EffectiveBlockTicks effectiveBlockTicks() { return effectiveBlockTicks; }
        public EffectiveBlockTicks effectiveFluidTicks() { return effectiveFluidTicks; }
        public byte[] mutableRawSuccessor() { return mutableRawSuccessor.clone(); }
        public String mutableRawSuccessorSha256() { return sha256(mutableRawSuccessor); }
        public Mc263WorldGenRegionRandom.State worldGenRegionRandomSuccessor() {
            return worldGenRegionRandomSuccessor;
        }
        public List<Long> worldGenRegionContinuationNextLongI64() {
            return worldGenRegionContinuation;
        }
        /**
         * Lanes this settlement authenticated as empty. FTIK leaves the list as soon as the
         * template lane restores a fluid source, exactly as the official Village probes recorded
         * {@code FTIK: 0} while the lane itself remains authenticated.
         */
        public List<String> authenticatedEmptyLanes() {
            ArrayList<String> lanes = new ArrayList<>(4);
            if (effectiveFluidTicks.acceptedCount() == 0) lanes.add("FTIK");
            lanes.add("SPWN"); lanes.add("ARCH"); lanes.add("BEES");
            return List.copyOf(lanes);
        }
        public String canonicalSha256() { return canonicalSha256; }
    }

    public static final class PersistedSettlement {
        private final Request request;
        private final Mc263StructureCarrier carrierPredecessor, carrierSuccessor;
        private final String rawPredecessorSha256, rawSuccessorSha256;
        private final byte[] rawSuccessor;
        private final Mc263WorldGenRegionRandom.State randomPredecessor, randomSuccessor;
        private final RawTranscript rawTranscript;
        private final PersistedCarrierPayload carrier;
        private final String fingerprint;
        private PersistedSettlement(Request request, Mc263StructureCarrier carrierPredecessor,
                Mc263StructureCarrier carrierSuccessor, String rawPredecessorSha256,
                String rawSuccessorSha256, byte[] rawSuccessor,
                Mc263WorldGenRegionRandom.State randomPredecessor,
                Mc263WorldGenRegionRandom.State randomSuccessor, RawTranscript rawTranscript,
                PersistedCarrierPayload carrier, String fingerprint) {
            this.request = request; this.carrierPredecessor = carrierPredecessor;
            this.carrierSuccessor = carrierSuccessor; this.rawPredecessorSha256 = rawPredecessorSha256;
            this.rawSuccessorSha256 = rawSuccessorSha256; this.rawSuccessor = rawSuccessor.clone();
            this.randomPredecessor = randomPredecessor; this.randomSuccessor = randomSuccessor;
            this.rawTranscript = rawTranscript; this.carrier = carrier; this.fingerprint = fingerprint;
        }
        public Request request() { return request; }
        public Mc263StructureCarrier carrierPredecessor() { return carrierPredecessor; }
        public Mc263StructureCarrier carrierSuccessor() { return carrierSuccessor; }
        public String rawPredecessorSha256() { return rawPredecessorSha256; }
        public String rawSuccessorSha256() { return rawSuccessorSha256; }
        public byte[] rawSuccessorBinaryNbtCompound() { return rawSuccessor.clone(); }
        public Mc263WorldGenRegionRandom.State randomPredecessor() { return randomPredecessor; }
        public Mc263WorldGenRegionRandom.State randomSuccessor() { return randomSuccessor; }
        public RawTranscript rawTranscript() { return rawTranscript; }
        public PersistedCarrierPayload carrier() { return carrier; }
        public String fingerprint() { return fingerprint; }
    }

    public static final class PersistedPublishReceipt {
        private final PublishStatus status;
        private final String carrierSuccessorSha256, rawPredecessorSha256, rawSuccessorSha256;
        private final Mc263WorldGenRegionRandom.State randomPredecessor, randomSuccessor;
        public PersistedPublishReceipt(PublishStatus status, String carrierSuccessorSha256,
                String rawPredecessorSha256, String rawSuccessorSha256,
                Mc263WorldGenRegionRandom.State randomPredecessor,
                Mc263WorldGenRegionRandom.State randomSuccessor) {
            this.status = Objects.requireNonNull(status, "persisted Village publish status");
            this.carrierSuccessorSha256 = Objects.requireNonNull(carrierSuccessorSha256);
            this.rawPredecessorSha256 = Objects.requireNonNull(rawPredecessorSha256);
            this.rawSuccessorSha256 = Objects.requireNonNull(rawSuccessorSha256);
            this.randomPredecessor = Objects.requireNonNull(randomPredecessor);
            this.randomSuccessor = Objects.requireNonNull(randomSuccessor);
        }
        public PublishStatus status() { return status; }
        public String carrierSuccessorSha256() { return carrierSuccessorSha256; }
        public String rawPredecessorSha256() { return rawPredecessorSha256; }
        public String rawSuccessorSha256() { return rawSuccessorSha256; }
        public Mc263WorldGenRegionRandom.State randomPredecessor() { return randomPredecessor; }
        public Mc263WorldGenRegionRandom.State randomSuccessor() { return randomSuccessor; }
    }

    public enum PublishStatus { COMMITTED, REPLAYED }

    /**
     * Product/runtime boundary. Every supports* method must be pure. fork() must return unpublished
     * state. execute* may mutate only that fork and must return a complete ordered typed receipt.
     * publishAtomically must commit fork + carrier lanes + references successor + RNG together, return
     * REPLAYED without any lane duplication for an exact fingerprint/payload replay, and throw with
     * no effects for a conflicting prior publication. Both successful statuses replace callerRandom
     * with acceptedRandom.
     */
    @FunctionalInterface
    interface TemplateCellExecutor {
        ExecutionResult execute(WorldTransaction world, CellPlacement placement, PlacementRandom random);
    }

    @FunctionalInterface
    interface PersistedTemplateCellExecutor {
        ExecutionResult execute(WorldTransaction world, CellPlacement placement,
                Mc263WorldGenRegionRandom random);
    }

    @FunctionalInterface
    interface PersistedFeatureExecutor {
        ExecutionResult execute(WorldTransaction world, FeaturePlacement placement,
                Mc263WorldGenRegionRandom random);
    }

    private interface RuntimeExecutor {
        ExecutionResult executeTemplateCell(CellPlacement placement);
        ExecutionResult executeConfiguredFeature(FeaturePlacement placement);
        ExecutionResult executeStructureEntity(EntityPlacement placement);
    }

    public interface WorldTransaction {
        boolean supportsAtomicForkPublishWithRandom();
        default boolean supportsPersistedWorldGenRegionRandomSettlement() {
            return false;
        }
        boolean supportsHeightmap(String heightmap); boolean supportsBuildHeightBoundary();
        boolean supportsTemplateExecution(); boolean supportsConfiguredFeatureExecution();
        boolean supportsEntityExecution(); boolean supportsTypedSidecars();
        boolean supportsRawBlockTickTranscript(); boolean supportsEffectiveBlockTickCarrier();
        boolean supportsOwnerLane(); boolean supportsEmptyUnauthenticatedLanes();
        boolean supportsWriteFlags(int flags); boolean supportsLevelChunkTicksAuthority(String sha256);
        boolean supportsBiomeAdmission(String structureKey, String biomeTag);
        boolean supportsPool(String poolKey); boolean supportsTemplate(String templateKey);
        boolean supportsProcessorList(String processorKey); boolean supportsProcessorSemantic(String semanticId);
        boolean supportsConfiguredFeature(String featureKey); boolean supportsExactState(String exactState);
        boolean supportsLootTable(String lootTable); boolean supportsBlockEntity(String blockEntityType);
        boolean supportsStructureEntity(String entityKey); boolean supportsBlockTick(String blockKey, int delay, int priority);
        /**
         * FTIK capability. Vanilla schedules a fluid tick through the same
         * {@code ScheduledTickAccess.scheduleTick} seam as a block tick, so a host that admits
         * block ticks admits fluid ticks unless it narrows this explicitly.
         */
        default boolean supportsFluidTick(String fluidKey, int delay, int priority) {
            return supportsBlockTick(fluidKey, delay, priority);
        }
        int minBuildY(); int maxBuildY(); int baseHeight(String heightmap, int x, int z);
        Mc263VillageProducer.BiomeSample actualBiome(String structureKey, String biomeTag,
                int blockX, int blockY, int blockZ);
        WorldTransaction fork();
        ExecutionResult executeTemplateCell(CellPlacement placement, PlacementRandom random);
        ExecutionResult executeConfiguredFeature(FeaturePlacement placement, PlacementRandom random);
        ExecutionResult executeStructureEntity(EntityPlacement placement, PlacementRandom random);
        PublishStatus publishAtomically(WorldTransaction isolated, Settlement settlement,
                PlacementRandom callerRandom, PlacementRandom acceptedRandom);
        default ExecutionResult executeTemplateCell(CellPlacement placement,
                Mc263WorldGenRegionRandom random) {
            throw new UnsupportedOperationException("persisted Village template execution unavailable");
        }
        default ExecutionResult executeConfiguredFeature(FeaturePlacement placement,
                Mc263WorldGenRegionRandom random) {
            throw new UnsupportedOperationException("persisted Village feature execution unavailable");
        }
        default ExecutionResult executeStructureEntity(EntityPlacement placement,
                Mc263WorldGenRegionRandom random) {
            throw new UnsupportedOperationException("persisted Village entity execution unavailable");
        }
        default PersistedPublishReceipt publishPersistedAtomically(WorldTransaction isolated,
                PersistedSettlement settlement, Mc263WorldGenRegionRandom.State exactPredecessor,
                Mc263WorldGenRegionRandom acceptedRandom) {
            throw new UnsupportedOperationException("persisted Village publication unavailable");
        }
    }

    /** Caller-owned exact Xoroshiro128++ worldgen-region random state. */
    public static final class PlacementRandom implements Mc263WorldgenRandomSource {
        private Xoroshiro source;
        private int count;
        private PlacementRandom(Xoroshiro source, int count) { this.source = source; this.count = count; }
        public static PlacementRandom fromState(long lo, long hi, int count) {
            if (count < 0) throw new IllegalArgumentException("negative Village RNG count");
            return new PlacementRandom(new Xoroshiro(lo, hi), count);
        }
        public long nextLong() { return ((long) next(32) << 32) + next(32); }
        public float nextFloat() { return next(24) * 0x1.0p-24F; }
        @Override public boolean nextBoolean() { return next(1) != 0; }
        public int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("Village RNG bound must be positive");
            int mask = bound - 1;
            if ((bound & mask) == 0) return (int) ((bound * (long) next(31)) >> 31);
            int bits, value;
            do {
                bits = next(31);
                value = bits % bound;
            } while (bits - value + mask < 0);
            return value;
        }
        private int next(int bits) { count++; return (int) (source.nextLong() >>> (64 - bits)); }
        public PlacementRandom copy() { return new PlacementRandom(source.copy(), count); }
        public void replaceWith(PlacementRandom value) {
            Objects.requireNonNull(value); source = value.source.copy(); count = value.count;
        }
        public long lo() { return source.lo; } public long hi() { return source.hi; } public int count() { return count; }
        public boolean sameState(PlacementRandom value) {
            return value != null && lo() == value.lo() && hi() == value.hi() && count == value.count;
        }
        public List<Long> continuationNextLongI64() {
            Xoroshiro copy = source.copy(); ArrayList<Long> result = new ArrayList<>(8);
            for (int index = 0; index < 8; index++) result.add(copy.nextLong());
            return List.copyOf(result);
        }
    }

    private static final class Xoroshiro {
        private static final long SILVER = 0x6A09E667F3BCC909L;
        private static final long GOLDEN = 0x9E3779B97F4A7C15L;
        private long lo, hi;
        Xoroshiro(long lo, long hi) {
            if ((lo | hi) == 0L) { this.lo = GOLDEN; this.hi = SILVER; }
            else { this.lo = lo; this.hi = hi; }
        }
        long nextLong() {
            long first = lo, second = hi, result = Long.rotateLeft(first + second, 17) + first;
            second ^= first; lo = Long.rotateLeft(first, 49) ^ second ^ second << 21;
            hi = Long.rotateLeft(second, 28); return result;
        }
        Xoroshiro copy() { return new Xoroshiro(lo, hi); }
    }

    private static final class Accumulator {
        private final Request request;
        private final WorldTransaction world;
        private final RuntimeExecutor runtime;
        private final ArrayList<Operation> operations = new ArrayList<>();
        private final LinkedHashMap<Position, Write> finalWrites = new LinkedHashMap<>();
        private final LinkedHashMap<Position, Owner> owners = new LinkedHashMap<>();
        private final LinkedHashMap<Position, BentPayload> bent = new LinkedHashMap<>();
        private final LinkedHashMap<Position, LootPayload> loot = new LinkedHashMap<>();
        private final ArrayList<EntityPayload> entities = new ArrayList<>();
        private final HashSet<String> entityIdentities = new HashSet<>();
        private final ArrayList<RawBlockTick> rawBlockTicks = new ArrayList<>();
        private final ArrayList<RawBlockTick> rawFluidTicks = new ArrayList<>();
        private Accumulator(Request request, WorldTransaction world, RuntimeExecutor runtime) {
            this.request = request; this.world = world; this.runtime = runtime;
        }

        private ExecutionResult executeTemplateCell(CellPlacement placement) {
            return runtime.executeTemplateCell(placement);
        }
        private ExecutionResult executeConfiguredFeature(FeaturePlacement placement) {
            return runtime.executeConfiguredFeature(placement);
        }
        private ExecutionResult executeStructureEntity(EntityPlacement placement) {
            return runtime.executeStructureEntity(placement);
        }

        private void accept(ExecutionResult result, Source source, boolean feature) {
            for (int index = 0; index < result.operations.size(); index++) {
                Operation value = Objects.requireNonNull(result.operations.get(index), "Village operation");
                require(value.ordinal == index, "Village primitive operation ordinal drift");
                if (value.position != null) validateDestination(value.position, feature);
                operations.add(new Operation(operations.size(), value.kind, value.position,
                        source.label + '|' + value.detail));
            }
            for (Write value : result.writes) {
                Objects.requireNonNull(value, "Village write"); validateDestination(value.position, feature);
                require(world.supportsExactState(value.exactState),
                        "Village runtime emitted unsupported exact state: " + value.exactState);
                require(value.flags == TEMPLATE_WRITE_FLAGS || value.flags == BLOCK_ENTITY_CLEAR_FLAGS
                                || feature,
                        "Village template write flags drift: " + value.flags);
                if (!value.successful) continue;
                Write previous = finalWrites.put(value.position, value);
                // WorldGenRegion#setBlock removes the block entity an earlier piece left at this
                // position once a later piece writes a different block over it (a savanna street
                // laying wheat across a tannery chest). The chest's RandomizableContainer loot table
                // lives in that block entity, so vanilla keeps neither BENT nor LOOT there. A
                // property-only rewrite of the same block keeps its block entity.
                if (previous != null && !stateBlockKey(previous.exactState)
                        .equals(stateBlockKey(value.exactState))) {
                    bent.remove(value.position);
                    loot.remove(value.position);
                }
                owners.put(value.position, new Owner(value.position, source.pieceOrdinal, source.label));
            }
            for (BentPayload value : result.bent) {
                validateDestination(value.position, feature);
                require(world.supportsBlockEntity(value.blockEntityType),
                        "Village runtime emitted unsupported block entity: " + value.blockEntityType);
                // StructureTemplate#placeInWorld clears the old block entity and writes a barrier
                // before placing a block-entity cell, so a later piece's block entity replaces an
                // earlier one at the same position together with that one's loot table.
                if (bent.remove(value.position) != null) loot.remove(value.position);
                bent.put(value.position, value);
            }
            for (LootPayload value : result.loot) {
                validateDestination(value.position, feature);
                require(world.supportsLootTable(value.lootTable),
                        "Village runtime emitted unsupported loot table: " + value.lootTable);
                require(loot.put(value.position, value) == null,
                        "duplicate Village LOOT destination in one settlement");
            }
            for (EntityPayload value : result.entities) {
                require(world.supportsStructureEntity(value.entityKey),
                        "Village runtime emitted unsupported entity: " + value.entityKey);
                String identity = value.entityKey + '|' + Double.doubleToRawLongBits(value.position.x)
                        + '|' + Double.doubleToRawLongBits(value.position.y) + '|'
                        + Double.doubleToRawLongBits(value.position.z) + '|' + value.canonicalNbtSha256();
                require(entityIdentities.add(identity), "duplicate Village ENTS identity");
                entities.add(value);
            }
            for (RawBlockTick value : result.rawBlockTicks) {
                if (fluidTickType(value.blockKey)) {
                    RawBlockTick reordinal = new RawBlockTick(rawFluidTicks.size(),
                            value.destinationChunk, value.position, value.blockKey, value.delay,
                            value.priority, value.subTickOrder);
                    validateRawFluidTick(reordinal, rawFluidTicks.size());
                    require(world.supportsFluidTick(reordinal.blockKey, reordinal.delay,
                                    reordinal.priority),
                            "Village runtime emitted unsupported fluid tick: " + reordinal.blockKey);
                    validateDestination(reordinal.position, true);
                    rawFluidTicks.add(reordinal);
                    continue;
                }
                RawBlockTick reordinal = new RawBlockTick(rawBlockTicks.size(), value.destinationChunk,
                        value.position, value.blockKey, value.delay, value.priority, value.subTickOrder);
                validateRawBlockTick(reordinal, rawBlockTicks.size());
                require(world.supportsBlockTick(reordinal.blockKey, reordinal.delay, reordinal.priority),
                        "Village runtime emitted unsupported block tick: " + reordinal.blockKey);
                validateDestination(reordinal.position, true);
                rawBlockTicks.add(reordinal);
            }
        }

        private void validateDestination(Position position, boolean feature) {
            Chunk destination = Chunk.from(position);
            if (!feature) {
                require(destination.x == request.sourceChunkX && destination.z == request.sourceChunkZ,
                        "Village template effect escaped source clip: " + position);
            } else {
                require(Math.abs(destination.x - request.sourceChunkX) <= 1
                                && Math.abs(destination.z - request.sourceChunkZ) <= 1,
                        "Village configured-feature effect escaped writable radius: " + position);
            }
            require(position.y >= -64 && position.y < 320,
                    "Village effect escaped build height: " + position);
        }

        private List<RawBlockTick> rawBlockTicks() { return List.copyOf(rawBlockTicks); }

        private List<RawBlockTick> rawFluidTicks() { return List.copyOf(rawFluidTicks); }

        private CarrierPayload freezeCarrier(Mc263VillageProducer.BinaryNbt successor,
                PlacementRandom candidate) {
            EffectiveBlockTicks effective = settleBlockTicks(rawBlockTicks);
            EffectiveBlockTicks effectiveFluid = settleFluidTicks(rawFluidTicks);
            ArrayList<Write> writes = new ArrayList<>(finalWrites.values());
            writes.sort(Comparator.comparing(Write::position, POSITION_ORDER));
            ArrayList<Owner> ownerRows = new ArrayList<>(writes.size());
            for (Write write : writes) {
                Owner owner = owners.get(write.position);
                require(owner != null, "Village final write lost owner");
                ownerRows.add(owner);
            }
            require(writes.size() == ownerRows.size(), "Village final write/owner lane drift");
            for (BentPayload value : bent.values()) require(finalWrites.containsKey(value.position),
                    "Village final BENT lacks final state at " + value.position);
            for (LootPayload value : loot.values()) require(bent.containsKey(value.position),
                    "Village LOOT row lacks final BENT at " + value.position);
            return new CarrierPayload(operations, writes, new ArrayList<>(bent.values()),
                    new ArrayList<>(loot.values()), entities, ownerRows, effective, effectiveFluid,
                    successor, candidate.count(), candidate.continuationNextLongI64());
        }

        private PersistedCarrierPayload freezePersistedCarrier(byte[] successor,
                Mc263WorldGenRegionRandom.State randomSuccessor, List<Long> continuation) {
            EffectiveBlockTicks effective = settleBlockTicks(rawBlockTicks);
            EffectiveBlockTicks effectiveFluid = settleFluidTicks(rawFluidTicks);
            ArrayList<Write> writes = new ArrayList<>(finalWrites.values());
            writes.sort(Comparator.comparing(Write::position, POSITION_ORDER));
            ArrayList<Owner> ownerRows = new ArrayList<>(writes.size());
            for (Write write : writes) {
                Owner owner = owners.get(write.position);
                require(owner != null, "Village persisted final write lost owner");
                ownerRows.add(owner);
            }
            require(writes.size() == ownerRows.size(),
                    "Village persisted final write/owner lane drift");
            for (BentPayload value : bent.values()) require(finalWrites.containsKey(value.position),
                    "Village persisted final BENT lacks final state at " + value.position);
            for (LootPayload value : loot.values()) require(bent.containsKey(value.position),
                    "Village persisted LOOT row lacks final BENT at " + value.position);
            return new PersistedCarrierPayload(operations, writes, new ArrayList<>(bent.values()),
                    new ArrayList<>(loot.values()), entities, ownerRows, effective, effectiveFluid,
                    successor, randomSuccessor, continuation);
        }
    }

    private static String hashCarrier(CarrierPayload value) {
        StringBuilder out = new StringBuilder();
        out.append(value.mutableSuccessor.sha256()).append('|').append(value.placementRandomCount);
        for (Operation operation : value.operations) out.append("\nO|").append(operation.ordinal)
                .append('|').append(operation.kind).append('|').append(operation.position)
                .append('|').append(operation.detail);
        for (Write write : value.finalWrites) out.append("\nW|").append(write.position)
                .append('|').append(write.exactState).append('|').append(write.flags);
        for (BentPayload bent : value.bent) out.append("\nB|").append(bent.position)
                .append('|').append(bent.blockEntityType).append('|').append(bent.canonicalNbtSha256());
        for (LootPayload loot : value.loot) out.append("\nL|").append(loot.position)
                .append('|').append(loot.lootTable).append('|').append(loot.seed);
        for (EntityPayload entity : value.entities) out.append("\nE|").append(entity.entityKey)
                .append('|').append(entity.canonicalNbtSha256());
        for (Owner owner : value.owners) out.append("\nN|").append(owner.position)
                .append('|').append(owner.sourcePieceOrdinal).append('|').append(owner.sourceIdentity);
        for (PackedTickChunk chunk : value.effectiveBlockTicks.destinationChunks) {
            out.append("\nC|").append(chunk.destinationChunk);
            for (RawBlockTick tick : chunk.ticks) out.append("\nT|").append(tick.blockKey)
                    .append('|').append(tick.position).append('|').append(tick.delay).append('|')
                    .append(tick.priority).append('|').append(tick.subTickOrder);
        }
        for (PackedTickChunk chunk : value.effectiveFluidTicks.destinationChunks) {
            out.append("\nCF|").append(chunk.destinationChunk);
            for (RawBlockTick tick : chunk.ticks) out.append("\nF|").append(tick.blockKey)
                    .append('|').append(tick.position).append('|').append(tick.delay).append('|')
                    .append(tick.priority).append('|').append(tick.subTickOrder);
        }
        for (long continuation : value.placementContinuation) out.append("\nR|").append(continuation);
        return sha256(out.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String hashPersistedCarrier(PersistedCarrierPayload value) {
        StringBuilder out = new StringBuilder("VILLAGE-PERSISTED-CARRIER-1|");
        out.append(value.mutableRawSuccessorSha256()).append('|')
                .append(randomStateFingerprint(value.worldGenRegionRandomSuccessor))
                .append("|FTIK").append(value.effectiveFluidTicks.acceptedCount())
                .append("|SPWN0|ARCH0|BEES0");
        for (Operation operation : value.operations) out.append("\nO|").append(operation.ordinal)
                .append('|').append(operation.kind).append('|').append(operation.position)
                .append('|').append(operation.detail);
        for (Write write : value.finalWrites) out.append("\nW|").append(write.position)
                .append('|').append(write.exactState).append('|').append(write.flags);
        for (BentPayload bent : value.bent) out.append("\nB|").append(bent.position)
                .append('|').append(bent.blockEntityType).append('|').append(bent.canonicalNbtSha256());
        for (LootPayload loot : value.loot) out.append("\nL|").append(loot.position)
                .append('|').append(loot.lootTable).append('|').append(loot.seed);
        for (EntityPayload entity : value.entities) out.append("\nE|").append(entity.entityKey)
                .append('|').append(entity.canonicalNbtSha256());
        for (Owner owner : value.owners) out.append("\nN|").append(owner.position)
                .append('|').append(owner.sourcePieceOrdinal).append('|').append(owner.sourceIdentity);
        for (PackedTickChunk chunk : value.effectiveBlockTicks.destinationChunks) {
            out.append("\nC|").append(chunk.destinationChunk);
            for (RawBlockTick tick : chunk.ticks) out.append("\nT|").append(tick.blockKey)
                    .append('|').append(tick.position).append('|').append(tick.delay).append('|')
                    .append(tick.priority).append('|').append(tick.subTickOrder);
        }
        for (PackedTickChunk chunk : value.effectiveFluidTicks.destinationChunks) {
            out.append("\nCF|").append(chunk.destinationChunk);
            for (RawBlockTick tick : chunk.ticks) out.append("\nF|").append(tick.blockKey)
                    .append('|').append(tick.position).append('|').append(tick.delay).append('|')
                    .append(tick.priority).append('|').append(tick.subTickOrder);
        }
        for (long continuation : value.worldGenRegionContinuation) out.append("\nR|").append(continuation);
        return sha256(out.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static final class TickIdentity {
        private final String type; private final Position position;
        TickIdentity(String type, Position position) { this.type = type; this.position = position; }
        @Override public boolean equals(Object other) {
            return other instanceof TickIdentity value && type.equals(value.type) && position.equals(value.position);
        }
        @Override public int hashCode() { return 31 * type.hashCode() + position.hashCode(); }
    }

    private static final class Source {
        private final int pieceOrdinal; private final String label;
        private Source(int pieceOrdinal, String label) { this.pieceOrdinal = pieceOrdinal; this.label = label; }
        static Source template(int piece, String template, int cell) {
            return new Source(piece, "template:" + piece + ':' + template + ':' + cell);
        }
        static Source entity(int piece, String template, int entity) {
            return new Source(piece, "entity:" + piece + ':' + template + ':' + entity);
        }
        static Source feature(int piece, String feature) {
            return new Source(piece, "feature:" + piece + ':' + feature);
        }
    }

    private static final class Preflight {
        private final ProducerSpec spec;
        private final Mc263VillageHierarchicalGrammar.Corpus hierarchy;
        private final Mc263VillageProductionAuthority.Corpus authority;
        private final Set<String> allowedTemplates, exactStates;
        private Preflight(ProducerSpec spec, Mc263VillageHierarchicalGrammar.Corpus hierarchy,
                Mc263VillageProductionAuthority.Corpus authority,
                Set<String> allowedTemplates, Set<String> exactStates) {
            this.spec = spec; this.hierarchy = hierarchy; this.authority = authority;
            this.allowedTemplates = allowedTemplates; this.exactStates = exactStates;
        }
    }

    private static final class ProducerWorld implements Mc263VillageProducer.WorldAccess {
        private final WorldTransaction world;
        ProducerWorld(WorldTransaction world) { this.world = world; }
        @Override public boolean supportsHeightmap(String heightmap) { return world.supportsHeightmap(heightmap); }
        @Override public boolean supportsBuildHeightBoundary() { return world.supportsBuildHeightBoundary(); }
        @Override public boolean supportsPool(String poolKey) { return world.supportsPool(poolKey); }
        @Override public boolean supportsTemplate(String templateKey) { return world.supportsTemplate(templateKey); }
        @Override public boolean supportsProcessorList(String processorKey) { return world.supportsProcessorList(processorKey); }
        @Override public boolean supportsConfiguredFeature(String featureKey) { return world.supportsConfiguredFeature(featureKey); }
        @Override public int minBuildY() { return world.minBuildY(); }
        @Override public int maxBuildY() { return world.maxBuildY(); }
        @Override public int baseHeight(String heightmap, int blockX, int blockZ) {
            return world.baseHeight(heightmap, blockX, blockZ);
        }
    }

    private static final class ProducerBiome implements Mc263VillageProducer.BiomeAdmission {
        private final WorldTransaction world;
        ProducerBiome(WorldTransaction world) { this.world = world; }
        @Override public boolean supports(String structureKey, String biomeTag) {
            return world.supportsBiomeAdmission(structureKey, biomeTag);
        }
        @Override public Mc263VillageProducer.BiomeSample actualBiome(String structureKey,
                String biomeTag, int blockX, int blockY, int blockZ) {
            return world.actualBiome(structureKey, biomeTag, blockX, blockY, blockZ);
        }
    }

    private static final class CapturingPublisher implements Mc263VillageProducer.Publisher {
        private final String structureKey;
        private Mc263VillageProducer.Start start;
        CapturingPublisher(String structureKey) { this.structureKey = structureKey; }
        @Override public boolean supports(String structureKey, String carrierFormat) {
            return this.structureKey.equals(structureKey)
                    && Mc263VillageProducer.CARRIER_FORMAT.equals(carrierFormat);
        }
        @Override public void publishAtomically(Mc263VillageProducer.Start start) {
            require(this.start == null, "Village producer published start twice"); this.start = start;
        }
    }
}
