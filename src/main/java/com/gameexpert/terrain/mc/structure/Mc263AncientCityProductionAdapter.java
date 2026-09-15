package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.feature.Mc263StructureBatchBridge;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Rotation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Dormant Ancient City production adapter for Minecraft 26.3-snapshot-7.
 *
 * <p>{@link Mc263AncientCitySettlement} owns the accepted compact grammar/tag preflight. This
 * adapter replaces its whole-start procedural callback with Ancient-specific ordered template and
 * configured-feature placement primitives. The runtime session is opened only after settlement
 * authority/capability preflight and persisted-start validation have completed, so malformed
 * authority or piece semantics cannot consume caller RNG, query world state, or mutate the fork.
 * This class does not register or publish {@code minecraft:ancient_city}.</p>
 */
public final class Mc263AncientCityProductionAdapter
        implements Mc263AncientCitySettlement.WorldAccess {
    private static final String STRUCTURE_KEY = Mc263AncientCitySettlement.STRUCTURE_KEY;
    private static final String FEATURE_KEY = Mc263AncientCitySettlement.FEATURE_KEY;
    private static final String PIECE_TYPE = "minecraft:jigsaw";
    private static final String SINGLE_ELEMENT_TYPE = "minecraft:single_pool_element";
    private static final String LIST_ELEMENT_TYPE = "minecraft:list_pool_element";
    private static final String FEATURE_ELEMENT_TYPE = "minecraft:feature_pool_element";
    private static final Set<String> LOOT_TABLES = Set.of("minecraft:chests/ancient_city",
            "minecraft:chests/ancient_city_ice_box");
    private static final Set<String> PROCESSORS = Set.of(
            "inline",
            "minecraft:ancient_city_start_degradation",
            "minecraft:ancient_city_generic_degradation",
            "minecraft:ancient_city_walls_degradation");
    private static final Set<String> POOLS = Set.of(
            "minecraft:ancient_city/city_center",
            "minecraft:ancient_city/structures",
            "minecraft:ancient_city/sculk",
            "minecraft:ancient_city/walls",
            "minecraft:ancient_city/walls/no_corners",
            "minecraft:ancient_city/city_center/walls",
            "minecraft:ancient_city/city/entrance");

    private final Runtime runtime;

    public Mc263AncientCityProductionAdapter(Runtime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "Ancient City production runtime");
    }

    public Mc263AncientCitySettlement.PreparedSettlement prepare(
            Mc263AncientCityProducer.Start start, Mc263AncientCitySettlement.Clip clip) {
        return Mc263AncientCitySettlement.prepare(start, clip, this);
    }

    /**
     * Rehydrates only execution-visible facts from an authenticated persisted Ancient City start.
     * No start generation, world query, RNG operation, or mutation is reachable from this method.
     */
    public PersistedDispatchPlan preflightPersisted(PersistedExecutionRequest request) {
        Objects.requireNonNull(request, "Ancient City persisted execution request");
        Mc263StructureCarrier.ValidStart start = request.start();
        Mc263AncientCityGrammar grammar = Mc263AncientCityProductionAuthority.load();
        validatePersistedProducerGraph(request.producerGraph(), start, grammar);
        require(start.startKey().equals(STRUCTURE_KEY + "@"
                        + start.originChunkX() + "," + start.originChunkZ()),
                "Ancient City persisted start identity drift");
        require(!start.orderedPieces().isEmpty(),
                "Ancient City persisted start has no pieces");
        require(start.adjustedBoundingBox().equals(rawBounds(start.orderedPieces())
                        .inflatedBy(Mc263AncientCityProducer.TERRAIN_PADDING)),
                "Ancient City persisted adjusted bbox drift");

        validateReferenceAndClip(request.references(), request.clip(), start);
        DecodedPair decoded = decodeRawPair(request.rawStart(), start, grammar);
        require(start.references() == decoded.predecessor().references()
                        || start.references() == decoded.successor().references(),
                "Ancient City persisted typed/raw reference mismatch");
        for (DecodedPiece piece : decoded.predecessor().pieces()) {
            validatePersistedPieceSemantics(piece, grammar);
        }

        ArrayList<PersistedPlacement> placements = new ArrayList<>();
        ArrayList<Integer> intersecting = new ArrayList<>();
        for (DecodedPiece piece : decoded.predecessor().pieces()) {
            if (!intersects(piece.boundingBox(), request.clip())) continue;
            intersecting.add(piece.ordinal());
            if (piece.element() instanceof DecodedSingle single) {
                placements.add(persistedTemplate(piece, single, request.clip()));
            } else if (piece.element() instanceof DecodedList list) {
                for (DecodedSingle child : list.children()) {
                    placements.add(persistedTemplate(piece, child, request.clip()));
                }
            } else if (piece.element() instanceof DecodedFeature feature) {
                placements.add(new PersistedFeaturePlacement(piece.ordinal(), feature.featureKey(),
                        piece.originX(), piece.originY(), piece.originZ(), piece.rotation(),
                        piece.boundingBox(), request.clip()));
            } else {
                throw new IllegalArgumentException("unknown Ancient City persisted pool element");
            }
        }
        PersistedDispatchPlan plan = new PersistedDispatchPlan(start, request.rawStart(),
                request.references(), request.clip(), placements, intersecting);
        preflightPersistedCapabilities(plan, grammar);
        return plan;
    }

    private static void validatePersistedProducerGraph(
            Mc263StructureCarrier.ProducerGraphPayload graph,
            Mc263StructureCarrier.ValidStart start, Mc263AncientCityGrammar grammar) {
        require(graph != null, "Ancient City persisted producer graph is absent");
        require(graph.structureId().equals(STRUCTURE_KEY)
                        && graph.startKey().equals(start.startKey())
                        && graph.originChunkX() == start.originChunkX()
                        && graph.originChunkZ() == start.originChunkZ(),
                "Ancient City persisted producer graph identity drift");
        require(graph.orderedEdges().size() == start.orderedPieces().size() - 1,
                "Ancient City persisted producer graph cardinality drift");

        List<String> authorityPools = grammar.registryPoolKeysInExecutionOrder();
        require(authorityPools.size() == 7
                        && Set.copyOf(authorityPools).size() == 7
                        && Set.copyOf(authorityPools).equals(POOLS),
                "Ancient City persisted seven-pool authority drift");
        Set<String> pools = Set.copyOf(authorityPools);
        for (int index = 0; index < graph.orderedEdges().size(); index++) {
            Mc263StructureCarrier.ProducerEdge edge = graph.orderedEdges().get(index);
            require(edge.targetPieceOrdinal() == index + 1
                            && edge.sourcePieceOrdinal() >= 0
                            && edge.sourcePieceOrdinal() < edge.targetPieceOrdinal()
                            && edge.targetPieceOrdinal() < start.orderedPieces().size(),
                    "Ancient City persisted accepted edge order drift");
            require(pools.contains(edge.selectedPool())
                            && pools.contains(edge.resolvedAlias())
                            && edge.selectedPool().equals(edge.resolvedAlias()),
                    "Ancient City persisted pool/alias escaped authenticated authority");
        }
    }

    /** Executes a preflighted persisted plan through the caller-owned transactional runtime. */
    public Mc263StructureBatchBridge.Batch executePersisted(PersistedExecutionRequest request) {
        PersistedDispatchPlan plan = preflightPersisted(request);
        PersistedSession session = Objects.requireNonNull(
                runtime.openPersistedSession(request, plan),
                "Ancient City persisted production session");
        boolean finished = false;
        try {
            for (PersistedPlacement placement : plan.placements()) {
                if (placement instanceof PersistedTemplatePlacement template) {
                    session.placeTemplate(template);
                } else if (placement instanceof PersistedFeaturePlacement feature) {
                    session.placeFeature(feature);
                }
            }
            Mc263StructureBatchBridge.Batch batch = Objects.requireNonNull(session.finish(),
                    "Ancient City persisted production batch");
            finished = true;
            return batch;
        } finally {
            if (!finished) session.abort();
        }
    }

    ProducerCarrierCheck preflightProducerCarrier(Mc263AncientCityProducer.Start start,
            Mc263AncientCitySettlement.Clip clip) {
        Objects.requireNonNull(start, "Ancient City producer start");
        Objects.requireNonNull(clip, "Ancient City producer clip");
        require(start.plan().structureKey().equals(STRUCTURE_KEY)
                        && start.plan().chunkX() == start.chunkX()
                        && start.plan().chunkZ() == start.chunkZ(),
                "Ancient City producer start identity drift");
        require(!start.plan().pieces().isEmpty()
                        && start.plan().pieces().size() == start.carrier().pieces().size(),
                "Ancient City producer carrier piece cardinality drift");
        require(start.aggregateBoundingBox().equals(producerBounds(start.plan().pieces())
                        .inflate(Mc263AncientCityProducer.TERRAIN_PADDING)),
                "Ancient City producer adjusted bbox drift");
        require(clip.minYInclusive() == Mc263AncientCitySettlement.MIN_CLIP_Y
                        && clip.maxYInclusive() == Mc263AncientCitySettlement.MAX_CLIP_Y
                        && start.aggregateBoundingBox().intersectsChunk(clip.chunkX(), clip.chunkZ()),
                "Ancient City producer clip escaped adjusted bbox");

        Mc263AncientCityGrammar grammar = Mc263AncientCityProductionAuthority.load();
        validateProducerGraph(start, grammar);
        byte[] predecessor = start.carrier().structureStart().bytes();
        byte[] successor = start.carrier().structureStart().mutableSuccessor();
        DecodedStart decodedPredecessor = decodeProducerStart(predecessor, start, grammar);
        DecodedStart decodedSuccessor = decodeProducerStart(successor, start, grammar);
        require(decodedPredecessor.references() == 0 && decodedSuccessor.references() == 1
                        && decodedPredecessor.referenceOffset() == decodedSuccessor.referenceOffset()
                        && decodedPredecessor.pieces().equals(decodedSuccessor.pieces())
                        && exactReferenceSuccessor(predecessor, successor,
                                decodedPredecessor.referenceOffset()),
                "Ancient City producer successor escaped exact references 0->1");

        ArrayList<Integer> intersecting = new ArrayList<>();
        for (DecodedPiece piece : decodedPredecessor.pieces()) {
            validatePersistedPieceSemantics(piece, grammar);
            if (intersects(piece.boundingBox(), clip)) intersecting.add(piece.ordinal());
        }
        String identity = producerPlanIdentity(start, clip, predecessor, successor);
        return new ProducerCarrierCheck(start.plan().pieces().size(), intersecting, identity);
    }

    @Override
    public Mc263AncientCitySettlement.ExecutionResult executeProcedurally(
            Mc263AncientCitySettlement.ExecutionRequest request) {
        DispatchPlan plan = preflightDispatch(request);
        Session session = Objects.requireNonNull(runtime.openSession(request, plan),
                "Ancient City production session");
        boolean finished = false;
        try {
            for (Placement placement : plan.placements()) {
                if (placement instanceof TemplatePlacement template) {
                    session.placeTemplate(template);
                } else if (placement instanceof FeaturePlacement feature) {
                    session.placeFeature(feature);
                }
            }
            Mc263AncientCitySettlement.ExecutionResult result = Objects.requireNonNull(
                    session.finish(), "Ancient City production execution result");
            finished = true;
            return result;
        } finally {
            if (!finished) session.abort();
        }
    }

    DispatchPlan preflightDispatch(Mc263AncientCitySettlement.ExecutionRequest request) {
        Objects.requireNonNull(request, "Ancient City execution request");
        Mc263AncientCityProducer.Start start = request.start();
        require(start.plan().structureKey().equals(Mc263AncientCityProducer.STRUCTURE_KEY)
                        && start.plan().chunkX() == start.chunkX()
                        && start.plan().chunkZ() == start.chunkZ(),
                "Ancient City persisted start identity drift");
        require(start.plan().pieces().size() == Mc263AncientCitySettlement.PIECE_COUNT
                        && start.carrier().pieces().size() == start.plan().pieces().size(),
                "Ancient City persisted piece cardinality drift");
        require(start.acceptedEdges().size() == start.plan().pieces().size() - 1,
                "Ancient City persisted connector graph cardinality drift");

        ArrayList<Placement> placements = new ArrayList<>();
        ArrayList<Integer> intersecting = new ArrayList<>();
        for (int ordinal = 0; ordinal < start.plan().pieces().size(); ordinal++) {
            Mc263AncientCityProducer.Piece piece = start.plan().pieces().get(ordinal);
            validatePiece(piece, ordinal);
            require(start.carrier().pieces().get(ordinal).length() > 0,
                    "Ancient City persisted piece payload is empty");
            if (!piece.boundingBox().intersectsChunk(request.clip().chunkX(), request.clip().chunkZ())) {
                continue;
            }
            intersecting.add(ordinal);
            switch (piece.kind()) {
                case TEMPLATE -> placements.add(template(piece, piece.elementKey(),
                        piece.processor(), request.clip()));
                case LIST -> {
                    for (Mc263AncientCityProducer.ListChild child : piece.children()) {
                        require(PROCESSORS.contains(child.processor()),
                                "unknown Ancient City list-child processor");
                        placements.add(template(piece, child.template(), child.processor(),
                                request.clip()));
                    }
                }
                case FEATURE -> placements.add(new FeaturePlacement(ordinal, piece.elementKey(),
                        piece.originX(), piece.originY(), piece.originZ(), piece.rotation(),
                        piece.boundingBox(), request.clip()));
            }
        }
        for (Mc263AncientCityProducer.Edge edge : start.acceptedEdges()) {
            require(edge.sourcePiece() >= 0 && edge.sourcePiece() < edge.targetPiece()
                            && edge.targetPiece() < start.plan().pieces().size(),
                    "Ancient City persisted connector edge order drift");
            require(POOLS.contains(edge.selectedPool())
                            && edge.selectedPool().equals(edge.resolvedAlias()),
                    "Ancient City persisted pool/alias decision drift");
        }
        validateSuccessor(start);
        return new DispatchPlan(start, request.clip(), placements, intersecting);
    }

    private static void validatePiece(Mc263AncientCityProducer.Piece piece, int ordinal) {
        require(piece.ordinal() == ordinal, "Ancient City persisted piece order drift");
        switch (piece.kind()) {
            case TEMPLATE -> {
                require(key(piece.elementKey()) && PROCESSORS.contains(piece.processor())
                                && piece.children().isEmpty(),
                        "unknown Ancient City template/processor semantic");
            }
            case LIST -> {
                require(piece.processor().isEmpty() && !piece.children().isEmpty(),
                        "Ancient City list piece semantic drift");
                for (Mc263AncientCityProducer.ListChild child : piece.children()) {
                    require(key(child.template()) && PROCESSORS.contains(child.processor()),
                            "unknown Ancient City list-child semantic");
                }
            }
            case FEATURE -> require(FEATURE_KEY.equals(piece.elementKey())
                            && piece.processor().isEmpty() && piece.children().isEmpty(),
                    "unknown Ancient City feature piece");
        }
    }

    private static TemplatePlacement template(Mc263AncientCityProducer.Piece piece,
            String template, String processor, Mc263AncientCitySettlement.Clip clip) {
        require(key(template) && PROCESSORS.contains(processor),
                "unknown Ancient City template placement semantic");
        return new TemplatePlacement(piece.ordinal(), template, processor,
                piece.originX(), piece.originY(), piece.originZ(), piece.rotation(),
                piece.boundingBox(), clip);
    }

    private static void validateSuccessor(Mc263AncientCityProducer.Start start) {
        byte[] predecessor = start.carrier().structureStart().bytes();
        byte[] successor = start.carrier().structureStart().mutableSuccessor();
        require(predecessor.length == successor.length && predecessor.length > 19,
                "Ancient City persisted successor length drift");
        int differences = 0;
        for (int index = 0; index < predecessor.length; index++) {
            if (predecessor[index] != successor[index]) differences++;
        }
        require(differences == 1 && predecessor[19] == 0 && successor[19] == 1,
                "Ancient City persisted successor escaped references-only 0->1");
    }

    private void preflightPersistedCapabilities(PersistedDispatchPlan plan,
            Mc263AncientCityGrammar grammar) {
        Mc263AncientCitySettlement.EvidenceIdentity evidence =
                Mc263AncientCitySettlement.evidenceIdentity();
        require(runtime.supportsPersistedStartSession(),
                "Ancient City persisted runtime/session capability absent");
        require(runtime.supportsVersion(evidence.versionId(), evidence.serverVersion(),
                        evidence.javaRuntimeVersion(), evidence.innerServerSha256(),
                        evidence.outerServerSha256()),
                "Ancient City persisted runtime/version preflight failed");
        for (Mc263AncientCitySettlement.SourcePin source : evidence.sources()) {
            require(runtime.supportsSource(source),
                    "Ancient City persisted source unavailable: " + source.label());
        }
        require(runtime.supportsExecutionCorpus(evidence.executionCorpusSha256())
                        && runtime.supportsStartGraphCarrier(evidence.startGraphCarrierSha256(),
                                evidence.startGraphJsonSha256())
                        && runtime.supportsFeatureCodecs(evidence.placedFeatureCodecSha256(),
                                evidence.configuredFeatureCodecSha256()),
                "Ancient City persisted authority closure unavailable");
        require(runtime.supportsFinalChunkSchema(Mc263AncientCitySettlement.FINAL_CHUNK_SCHEMA)
                        && runtime.supportsAtomicReplayableSettlementWithRng()
                        && runtime.supportsForeignDestinations()
                        && runtime.supportsCallerRngContinuation()
                        && runtime.supportsClip(plan.clip()),
                "Ancient City persisted settlement/clip capability absent");
        for (Mc263AncientCitySettlement.SidecarLane lane
                : Mc263AncientCitySettlement.SidecarLane.values()) {
            require(runtime.supportsSidecarLane(lane),
                    "Ancient City persisted sidecar lane unavailable: " + lane);
        }
        for (Mc263AncientCityGrammar.Template template : grammar.templatesInEncounterOrder()) {
            Mc263AncientCityProducer.TemplateStatus status = switch (template.status()) {
                case PRESENT -> Mc263AncientCityProducer.TemplateStatus.PRESENT;
                case EXPECTED_ABSENT -> Mc263AncientCityProducer.TemplateStatus.EXPECTED_ABSENT;
            };
            require(runtime.supportsTemplate(template.id(), status),
                    "Ancient City persisted template capability absent: " + template.id());
            for (String exactState : template.stateTable()) {
                require(runtime.supportsExactState(exactState),
                        "Ancient City persisted exact-state capability absent: " + exactState);
            }
            for (Mc263AncientCityGrammar.Command command : template.commands()) {
                if (command instanceof Mc263AncientCityGrammar.Jigsaw jigsaw) {
                    require(runtime.supportsExactState(jigsaw.finalState()),
                            "Ancient City persisted jigsaw final-state capability absent");
                } else if (command instanceof Mc263AncientCityGrammar.LootContainer loot) {
                    require(LOOT_TABLES.contains(loot.lootTable())
                                    && runtime.supportsLootTable(loot.lootTable())
                                    && runtime.supportsBlockEntity(loot.blockEntityType()),
                            "Ancient City persisted loot capability absent");
                } else if (command instanceof Mc263AncientCityGrammar.Bent bent) {
                    require(runtime.supportsBlockEntity(bent.payload().blockEntityType()),
                            "Ancient City persisted BENT capability absent");
                }
            }
        }
        for (Mc263AncientCityGrammar.ProcessorListSpec processor
                : grammar.processorListsInEncounterOrder()) {
            require(runtime.supportsProcessorList(processor.identity()),
                    "Ancient City persisted processor-list capability absent: "
                            + processor.identity());
        }
        for (Mc263AncientCityGrammar.ProcessorSpec processor
                : grammar.processorSemanticsInEncounterOrder()) {
            require(runtime.supportsProcessorSemantic(processor.identity(), processor.runtimeClass()),
                    "Ancient City persisted processor semantic capability absent: "
                            + processor.identity());
        }
        require(FEATURE_KEY.equals(grammar.configuredFeature().registryKey())
                        && runtime.supportsConfiguredFeature(FEATURE_KEY),
                "Ancient City persisted configured-feature capability absent");
        require(runtime.supportsCanonicalBlockEntityNbt("minecraft:chest",
                        Mc263AncientCitySettlement.E3I5_CHEST_NBT_TAGS_IN_ORDER),
                "Ancient City persisted canonical chest NBT capability absent");
        for (String exactState : grammar.statesInEncounterOrder()) {
            require(runtime.supportsExactState(exactState),
                    "Ancient City persisted exact-state closure absent: " + exactState);
        }
    }

    private static void validateReferenceAndClip(Mc263StructureCarrier.ChunkReferences references,
            Mc263AncientCitySettlement.Clip clip, Mc263StructureCarrier.ValidStart start) {
        require(references.chunkX() == clip.chunkX() && references.chunkZ() == clip.chunkZ(),
                "Ancient City persisted reference/clip source drift");
        require(clip.minYInclusive() == Mc263AncientCitySettlement.MIN_CLIP_Y
                        && clip.maxYInclusive() == Mc263AncientCitySettlement.MAX_CLIP_Y,
                "Ancient City persisted clip build-height drift");
        require(intersects(start.adjustedBoundingBox(), clip),
                "Ancient City persisted clip escaped adjusted bbox");
        long origin = Mc263StructureCarrier.packChunk(start.originChunkX(), start.originChunkZ());
        int ancientSets = 0;
        for (Mc263StructureCarrier.ReferenceSet set : references.orderedSets()) {
            if (!STRUCTURE_KEY.equals(set.structureId())) continue;
            ancientSets++;
            // Vanilla keeps one LongSet per structure holding EVERY referencing start origin;
            // ancient_city (spacing 24 / separation 8, ~15-chunk adjusted bbox) can seat two
            // starts referencing one chunk, so require membership, not singularity.
            require(new java.util.HashSet<>(set.orderedOrigins()).size()
                            == set.orderedOrigins().size(),
                    "Ancient City persisted reference long-set repeats an origin");
            require(set.orderedOrigins().contains(origin),
                    "Ancient City persisted reference long-set omits the start origin");
        }
        require(ancientSets == 1, "Ancient City persisted reference set is absent or duplicated");
        require(Math.max(Math.abs((long) start.originChunkX() - references.chunkX()),
                        Math.abs((long) start.originChunkZ() - references.chunkZ()))
                        <= Mc263StructureCarrier.REFERENCE_RADIUS,
                "Ancient City persisted reference exceeds radius 8");
    }

    private static DecodedPair decodeRawPair(Mc263StructureCarrier.RawStartPayload raw,
            Mc263StructureCarrier.ValidStart start, Mc263AncientCityGrammar grammar) {
        require(raw.structureId().equals(STRUCTURE_KEY)
                        && raw.startKey().equals(start.startKey())
                        && raw.originChunkX() == start.originChunkX()
                        && raw.originChunkZ() == start.originChunkZ(),
                "Ancient City persisted typed/raw identity mismatch");
        byte[] predecessor = raw.predecessorBinaryNbtCompound();
        byte[] successor = raw.successorBinaryNbtCompound();
        require(predecessor.length > 0 && predecessor.length == successor.length
                        && raw.predecessorSha256().equals(sha256(predecessor))
                        && raw.successorSha256().equals(sha256(successor)),
                "Ancient City persisted raw start authority drift");
        DecodedStart decodedPredecessor = decodeRawStart(predecessor, start, grammar);
        DecodedStart decodedSuccessor = decodeRawStart(successor, start, grammar);
        require(decodedPredecessor.references() == 0 && decodedSuccessor.references() == 1,
                "Ancient City persisted raw references are not exact 0->1");
        require(decodedPredecessor.referenceOffset() == decodedSuccessor.referenceOffset(),
                "Ancient City persisted reference offset drift");
        require(decodedPredecessor.pieces().equals(decodedSuccessor.pieces()),
                "Ancient City persisted successor piece drift");
        require(exactReferenceSuccessor(predecessor, successor,
                        decodedPredecessor.referenceOffset()),
                "Ancient City persisted successor changed outside exact references 0->1");
        return new DecodedPair(decodedPredecessor, decodedSuccessor);
    }

    private static DecodedStart decodeProducerStart(byte[] bytes,
            Mc263AncientCityProducer.Start start, Mc263AncientCityGrammar grammar) {
        PersistedNbtCursor input = new PersistedNbtCursor(bytes);
        input.expectByte(10, "Ancient City producer start root type");
        input.expectUtf("", "Ancient City producer start root name");
        input.expectTag(3, "references");
        int referenceOffset = input.position();
        int references = input.int32();
        input.expectTag(3, "ChunkZ");
        require(input.int32() == start.chunkZ(), "Ancient City producer raw ChunkZ drift");
        input.expectTag(8, "id");
        require(input.utf().equals(STRUCTURE_KEY), "Ancient City producer raw id drift");
        input.expectTag(9, "Children");
        input.expectByte(10, "Ancient City producer Children element type");
        int count = input.int32();
        require(count > 0 && count == start.plan().pieces().size()
                        && count == start.carrier().pieces().size(),
                "Ancient City producer raw Children cardinality drift");
        ArrayList<DecodedPiece> pieces = new ArrayList<>(count);
        for (int ordinal = 0; ordinal < count; ordinal++) {
            int bodyStart = input.position();
            DecodedPiece decoded = decodeRawPieceBody(input, ordinal, grammar);
            byte[] rawPiece = start.carrier().pieces().get(ordinal).bytes();
            require(start.carrier().pieces().get(ordinal).sha256().equals(sha256(rawPiece))
                            && input.rootBodyEquals(bodyStart, rawPiece),
                    "Ancient City producer piece raw identity drift at " + ordinal);
            validateProducerPiece(decoded, start.plan().pieces().get(ordinal));
            pieces.add(decoded);
        }
        input.expectTag(3, "ChunkX");
        require(input.int32() == start.chunkX(), "Ancient City producer raw ChunkX drift");
        input.expectByte(0, "Ancient City producer raw start end");
        input.requireFullyConsumed();
        return new DecodedStart(references, referenceOffset, pieces);
    }

    private static void validateProducerPiece(DecodedPiece decoded,
            Mc263AncientCityProducer.Piece typed) {
        require(typed.ordinal() == decoded.ordinal()
                        && typed.originX() == decoded.originX()
                        && typed.originY() == decoded.originY()
                        && typed.originZ() == decoded.originZ()
                        && typed.rotation() == decoded.rotation()
                        && producerBox(typed.boundingBox()).equals(decoded.boundingBox())
                        && typed.groundLevelDelta() == decoded.groundLevelDelta()
                        && carrierProjection(typed.projection().name().toLowerCase())
                                == elementProjection(decoded.element()),
                "Ancient City producer typed/raw piece drift at " + decoded.ordinal());
        ArrayList<Mc263StructureCarrier.Junction> expectedJunctions = new ArrayList<>();
        for (Mc263AncientCityProducer.Junction junction : typed.junctions()) {
            expectedJunctions.add(new Mc263StructureCarrier.Junction(junction.sourceX(),
                    junction.sourceGroundY(), junction.sourceZ(), junction.deltaY(),
                    carrierProjection(junction.destinationProjection().name().toLowerCase())));
        }
        require(expectedJunctions.equals(decoded.junctions()),
                "Ancient City producer typed/raw junction drift at " + decoded.ordinal());
        if (typed.kind() == Mc263AncientCityProducer.PieceKind.TEMPLATE) {
            require(decoded.element() instanceof DecodedSingle single
                            && typed.elementKey().equals(single.template())
                            && typed.processor().equals(single.processorList()),
                    "Ancient City producer template/processor raw drift at " + decoded.ordinal());
        } else if (typed.kind() == Mc263AncientCityProducer.PieceKind.FEATURE) {
            require(decoded.element() instanceof DecodedFeature feature
                            && typed.elementKey().equals(feature.featureKey()),
                    "Ancient City producer feature raw drift at " + decoded.ordinal());
        } else if (typed.kind() == Mc263AncientCityProducer.PieceKind.LIST) {
            require(decoded.element() instanceof DecodedList list
                            && typed.children().size() == list.children().size(),
                    "Ancient City producer list raw drift at " + decoded.ordinal());
            DecodedList list = (DecodedList) decoded.element();
            for (int index = 0; index < typed.children().size(); index++) {
                Mc263AncientCityProducer.ListChild left = typed.children().get(index);
                DecodedSingle right = list.children().get(index);
                require(left.template().equals(right.template())
                                && left.processor().equals(right.processorList()),
                        "Ancient City producer list child raw drift at "
                                + decoded.ordinal() + ":" + index);
            }
        } else {
            throw new IllegalArgumentException("unknown Ancient City producer piece kind");
        }
    }

    private static DecodedStart decodeRawStart(byte[] bytes,
            Mc263StructureCarrier.ValidStart start, Mc263AncientCityGrammar grammar) {
        PersistedNbtCursor input = new PersistedNbtCursor(bytes);
        input.expectByte(10, "Ancient City raw start root type");
        input.expectUtf("", "Ancient City raw start root name");
        input.expectTag(3, "references");
        int referenceOffset = input.position();
        int references = input.int32();
        input.expectTag(3, "ChunkZ");
        require(input.int32() == start.originChunkZ(),
                "Ancient City persisted raw ChunkZ drift");
        input.expectTag(8, "id");
        require(input.utf().equals(STRUCTURE_KEY), "Ancient City persisted raw id drift");
        input.expectTag(9, "Children");
        input.expectByte(10, "Ancient City Children element type");
        int count = input.int32();
        require(count > 0 && count == start.orderedPieces().size(),
                "Ancient City persisted raw Children cardinality drift");
        ArrayList<DecodedPiece> pieces = new ArrayList<>(count);
        for (int ordinal = 0; ordinal < count; ordinal++) {
            pieces.add(decodeRawPiece(input, start.orderedPieces().get(ordinal), ordinal, grammar));
        }
        input.expectTag(3, "ChunkX");
        require(input.int32() == start.originChunkX(),
                "Ancient City persisted raw ChunkX drift");
        input.expectByte(0, "Ancient City raw start end");
        input.requireFullyConsumed();
        return new DecodedStart(references, referenceOffset, pieces);
    }

    private static DecodedPiece decodeRawPiece(PersistedNbtCursor input,
            Mc263StructureCarrier.Piece typed, int ordinal, Mc263AncientCityGrammar grammar) {
        int bodyStart = input.position();
        require(PIECE_TYPE.equals(typed.pieceType()) && typed.poolElement(),
                "Ancient City persisted typed piece identity drift at " + ordinal);
        DecodedPiece decoded = decodeRawPieceBody(input, ordinal, grammar);
        require(decoded.boundingBox().equals(typed.boundingBox()),
                "Ancient City persisted BB fact drift at " + ordinal);
        require(decoded.groundLevelDelta() == typed.groundLevelDelta(),
                "Ancient City persisted ground-level delta drift at " + ordinal);
        require(decoded.junctions().equals(typed.junctions()),
                "Ancient City persisted junction drift at " + ordinal);
        require(input.rootBodyEquals(bodyStart, typed.persistedPayload().binaryNbtCompound()),
                "Ancient City persisted raw/typed piece bytes disagree at " + ordinal);
        require(elementProjection(decoded.element()) == typed.projection(),
                "Ancient City persisted projection drift at " + ordinal);
        return decoded;
    }

    private static DecodedPiece decodeRawPieceBody(PersistedNbtCursor input, int ordinal,
            Mc263AncientCityGrammar grammar) {
        input.expectTag(11, "BB");
        require(input.int32() == 6, "Ancient City persisted BB width drift at " + ordinal);
        Mc263StructureCarrier.BoundingBox box = new Mc263StructureCarrier.BoundingBox(
                input.int32(), input.int32(), input.int32(), input.int32(), input.int32(),
                input.int32());
        input.expectTag(3, "PosZ"); int originZ = input.int32();
        input.expectTag(3, "PosX"); int originX = input.int32();
        input.expectTag(10, "pool_element");
        DecodedElement element = decodePoolElement(input, grammar, ordinal);
        input.expectTag(3, "PosY"); int originY = input.int32();
        input.expectTag(8, "rotation"); Rotation rotation = decodeRotation(input.utf());
        input.expectTag(8, "id");
        require(input.utf().equals(PIECE_TYPE),
                "Ancient City persisted piece id drift at " + ordinal);
        input.expectTag(3, "GD");
        require(input.int32() == 0, "Ancient City persisted GD drift at " + ordinal);
        input.expectTag(3, "O");
        require(input.int32() == -1, "Ancient City persisted O drift at " + ordinal);
        input.expectTag(3, "ground_level_delta"); int groundLevelDelta = input.int32();
        input.expectTag(9, "junctions");
        input.expectByte(10, "Ancient City junction list element type");
        int junctionCount = input.int32();
        require(junctionCount >= 0,
                "Ancient City persisted junction cardinality drift at " + ordinal);
        ArrayList<Mc263StructureCarrier.Junction> junctions = new ArrayList<>(junctionCount);
        for (int index = 0; index < junctionCount; index++) {
            input.expectTag(3, "source_z"); int sourceZ = input.int32();
            input.expectTag(3, "source_x"); int sourceX = input.int32();
            input.expectTag(3, "delta_y"); int deltaY = input.int32();
            input.expectTag(3, "source_ground_y"); int sourceGroundY = input.int32();
            input.expectTag(8, "dest_proj");
            Mc263StructureCarrier.Projection destination = carrierProjection(input.utf());
            input.expectByte(0, "Ancient City junction compound end");
            junctions.add(new Mc263StructureCarrier.Junction(
                    sourceX, sourceGroundY, sourceZ, deltaY, destination));
        }
        input.expectByte(0, "Ancient City piece compound end");
        return new DecodedPiece(ordinal, element, originX, originY, originZ, rotation, box,
                groundLevelDelta, junctions);
    }

    private static DecodedElement decodePoolElement(PersistedNbtCursor input,
            Mc263AncientCityGrammar grammar, int ordinal) {
        int firstType = input.unsignedByte();
        String firstName = input.utf();
        DecodedElement result;
        if (firstType == 8 && firstName.equals("location")) {
            result = decodeSingleAfterLocation(input, grammar);
        } else if (firstType == 9 && firstName.equals("elements")) {
            input.expectByte(10, "Ancient City list child element type");
            int childCount = input.int32();
            require(childCount > 0 && childCount <= 64,
                    "Ancient City persisted list child count is out of bounds");
            ArrayList<DecodedSingle> children = new ArrayList<>(childCount);
            for (int index = 0; index < childCount; index++) {
                input.expectByte(8, "Ancient City list child location type");
                input.expectUtf("location", "Ancient City list child location name");
                children.add(decodeSingleAfterLocation(input, grammar));
                input.expectByte(0, "Ancient City list child compound end");
            }
            input.expectTag(8, "projection");
            Mc263StructureCarrier.Projection projection = carrierProjection(input.utf());
            for (DecodedSingle child : children) {
                require(child.projection() == projection,
                        "Ancient City persisted list child projection drift");
            }
            input.expectTag(8, "element_type");
            require(input.utf().equals(LIST_ELEMENT_TYPE),
                    "unknown Ancient City persisted list element type");
            result = new DecodedList(children, projection);
        } else if (firstType == 8 && firstName.equals("feature")) {
            String feature = input.utf();
            require(FEATURE_KEY.equals(feature)
                            && FEATURE_KEY.equals(grammar.configuredFeature().registryKey()),
                    "unknown Ancient City persisted configured feature");
            input.expectTag(8, "projection");
            Mc263StructureCarrier.Projection projection = carrierProjection(input.utf());
            input.expectTag(8, "element_type");
            require(input.utf().equals(FEATURE_ELEMENT_TYPE),
                    "unknown Ancient City persisted feature element type");
            result = new DecodedFeature(feature, projection);
        } else {
            throw new IllegalArgumentException(
                    "unknown Ancient City persisted pool element at " + ordinal);
        }
        input.expectByte(0, "Ancient City pool element compound end");
        return result;
    }

    private static DecodedSingle decodeSingleAfterLocation(PersistedNbtCursor input,
            Mc263AncientCityGrammar grammar) {
        String template = input.utf();
        require(key(template), "malformed Ancient City persisted template key");
        Mc263AncientCityGrammar.Template templateSpec = grammar.requireTemplate(template);
        require(templateSpec.status() == Mc263AncientCityGrammar.SourceStatus.PRESENT,
                "unknown or absent Ancient City persisted template: " + template);
        int processorType = input.unsignedByte();
        input.expectUtf("processors", "Ancient City processors field");
        String processor;
        if (processorType == 8) {
            processor = input.utf();
        } else if (processorType == 10) {
            input.expectTag(9, "processors");
            input.expectByte(0, "Ancient City inline processor list element type");
            require(input.int32() == 0, "Ancient City inline processor list is nonempty");
            input.expectByte(0, "Ancient City inline processor compound end");
            processor = "inline";
        } else {
            throw new IllegalArgumentException("unknown Ancient City persisted processor encoding");
        }
        requireProcessor(grammar, processor);
        input.expectTag(8, "projection");
        Mc263StructureCarrier.Projection projection = carrierProjection(input.utf());
        input.expectTag(8, "element_type");
        require(input.utf().equals(SINGLE_ELEMENT_TYPE),
                "unknown Ancient City persisted single element type");
        return new DecodedSingle(template, processor, projection);
    }

    private static void requireProcessor(Mc263AncientCityGrammar grammar, String processor) {
        for (Mc263AncientCityGrammar.ProcessorListSpec candidate
                : grammar.processorListsInEncounterOrder()) {
            if (candidate.identity().equals(processor)) return;
        }
        throw new IllegalArgumentException(
                "Ancient City persisted processor escaped production authority: " + processor);
    }

    private static void validatePersistedPieceSemantics(DecodedPiece piece,
            Mc263AncientCityGrammar grammar) {
        require(piece.ordinal() >= 0,
                "Ancient City persisted piece ordinal escaped encounter order");
        require(elementProjection(piece.element()) == Mc263StructureCarrier.Projection.RIGID,
                "Ancient City persisted piece projection drift at " + piece.ordinal());
        require(contains(piece.boundingBox(), piece.originX(), piece.originY(), piece.originZ()),
                "Ancient City persisted piece origin escaped bounds at " + piece.ordinal());
        require(authenticatedByAnyPool(piece.element(), grammar),
                "Ancient City persisted pool/element semantic drift at " + piece.ordinal());
        if (piece.ordinal() == 0) {
            require(authenticatedByPool(piece.element(), grammar.poolsInExecutionOrder().getFirst()),
                    "Ancient City persisted root escaped authenticated start pool");
        } else {
            require(!piece.junctions().isEmpty(),
                    "Ancient City persisted piece escaped connected graph at " + piece.ordinal());
        }
        require(piece.boundingBox().equals(expectedBoundingBox(piece, grammar)),
                "Ancient City persisted piece geometry drift at " + piece.ordinal());
    }

    private static Mc263StructureCarrier.Projection elementProjection(DecodedElement element) {
        if (element instanceof DecodedSingle single) return single.projection();
        if (element instanceof DecodedList list) return list.projection();
        if (element instanceof DecodedFeature feature) return feature.projection();
        throw new IllegalArgumentException("unknown Ancient City persisted element projection");
    }

    private static boolean authenticatedByAnyPool(DecodedElement element,
            Mc263AncientCityGrammar grammar) {
        require(grammar.registryPoolKeysInExecutionOrder().size()
                        == grammar.poolsInExecutionOrder().size(),
                "Ancient City persisted pool registry closure drift");
        for (int index = 0; index < grammar.poolsInExecutionOrder().size(); index++) {
            Mc263AncientCityGrammar.Pool pool = grammar.poolsInExecutionOrder().get(index);
            require(pool.key().equals(grammar.registryPoolKeysInExecutionOrder().get(index)),
                    "Ancient City persisted pool encounter-order drift");
            if (authenticatedByPool(element, pool)) return true;
        }
        return false;
    }

    private static boolean authenticatedByPool(DecodedElement decoded,
            Mc263AncientCityGrammar.Pool pool) {
        for (Mc263AncientCityGrammar.PoolElement element : pool.elementsInDeclaredOrder()) {
            if (decoded instanceof DecodedSingle single
                    && element instanceof Mc263AncientCityGrammar.SingleElement candidate
                    && single.template().equals(candidate.template())
                    && single.processorList().equals(candidate.processorList())) return true;
            if (decoded instanceof DecodedFeature feature
                    && element instanceof Mc263AncientCityGrammar.FeatureElement candidate
                    && feature.featureKey().equals(candidate.feature())) return true;
            if (decoded instanceof DecodedList list
                    && element instanceof Mc263AncientCityGrammar.ListElement candidate
                    && sameListElement(list, candidate)) return true;
        }
        return false;
    }

    private static boolean sameListElement(DecodedList decoded,
            Mc263AncientCityGrammar.ListElement candidate) {
        if (decoded.children().size() != candidate.childrenInDeclaredOrder().size()) return false;
        for (int index = 0; index < decoded.children().size(); index++) {
            DecodedSingle left = decoded.children().get(index);
            Mc263AncientCityGrammar.SingleChild right =
                    candidate.childrenInDeclaredOrder().get(index);
            if (!left.template().equals(right.template())
                    || !left.processorList().equals(right.processorList())) return false;
        }
        return true;
    }

    private static Mc263StructureCarrier.BoundingBox expectedBoundingBox(DecodedPiece piece,
            Mc263AncientCityGrammar grammar) {
        if (piece.element() instanceof DecodedFeature) {
            return new Mc263StructureCarrier.BoundingBox(piece.originX(), piece.originY(),
                    piece.originZ(), piece.originX(), piece.originY(), piece.originZ());
        }
        int sizeX = 0;
        int sizeY = 0;
        int sizeZ = 0;
        if (piece.element() instanceof DecodedSingle single) {
            Mc263AncientCityGrammar.Size size = presentSize(grammar, single.template());
            sizeX = size.x(); sizeY = size.y(); sizeZ = size.z();
        } else if (piece.element() instanceof DecodedList list) {
            for (DecodedSingle child : list.children()) {
                Mc263AncientCityGrammar.Size size = presentSize(grammar, child.template());
                sizeX = Math.max(sizeX, size.x());
                sizeY = Math.max(sizeY, size.y());
                sizeZ = Math.max(sizeZ, size.z());
            }
        } else {
            throw new IllegalArgumentException("unknown Ancient City persisted geometry element");
        }
        require(sizeX > 0 && sizeY > 0 && sizeZ > 0,
                "Ancient City persisted template geometry is empty");
        int x1 = piece.originX();
        int z1 = piece.originZ();
        int x2;
        int z2;
        switch (piece.rotation()) {
            case NONE -> {
                x2 = Math.addExact(x1, sizeX - 1);
                z2 = Math.addExact(z1, sizeZ - 1);
            }
            case CLOCKWISE_90 -> {
                x2 = Math.subtractExact(x1, sizeZ - 1);
                z2 = Math.addExact(z1, sizeX - 1);
            }
            case CLOCKWISE_180 -> {
                x2 = Math.subtractExact(x1, sizeX - 1);
                z2 = Math.subtractExact(z1, sizeZ - 1);
            }
            case COUNTERCLOCKWISE_90 -> {
                x2 = Math.addExact(x1, sizeZ - 1);
                z2 = Math.subtractExact(z1, sizeX - 1);
            }
            default -> throw new IllegalArgumentException(
                    "unknown Ancient City persisted rotation geometry");
        }
        int maxY = Math.addExact(piece.originY(), sizeY - 1);
        return new Mc263StructureCarrier.BoundingBox(Math.min(x1, x2), piece.originY(),
                Math.min(z1, z2), Math.max(x1, x2), maxY, Math.max(z1, z2));
    }

    private static Mc263AncientCityGrammar.Size presentSize(Mc263AncientCityGrammar grammar,
            String template) {
        Mc263AncientCityGrammar.Template value = grammar.requireTemplate(template);
        require(value.status() == Mc263AncientCityGrammar.SourceStatus.PRESENT
                        && value.size().isPresent(),
                "Ancient City persisted template is not a present authority asset: " + template);
        return value.size().orElseThrow();
    }

    private static boolean contains(Mc263StructureCarrier.BoundingBox box, int x, int y, int z) {
        return x >= box.minX() && x <= box.maxX() && y >= box.minY() && y <= box.maxY()
                && z >= box.minZ() && z <= box.maxZ();
    }

    private static Mc263AncientCityProducer.Box producerBounds(
            List<Mc263AncientCityProducer.Piece> pieces) {
        Mc263AncientCityProducer.Box bounds = pieces.getFirst().boundingBox();
        for (int index = 1; index < pieces.size(); index++) {
            bounds = bounds.union(pieces.get(index).boundingBox());
        }
        return bounds;
    }

    private static Mc263StructureCarrier.BoundingBox producerBox(
            Mc263AncientCityProducer.Box box) {
        return new Mc263StructureCarrier.BoundingBox(box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ());
    }

    private static void validateProducerGraph(Mc263AncientCityProducer.Start start,
            Mc263AncientCityGrammar grammar) {
        List<Mc263AncientCityProducer.Piece> pieces = start.plan().pieces();
        require(start.acceptedEdges().size() == pieces.size() - 1,
                "Ancient City producer graph cardinality drift");
        Set<String> pools = Set.copyOf(grammar.registryPoolKeysInExecutionOrder());
        boolean[] targets = new boolean[pieces.size()];
        for (int ordinal = 0; ordinal < pieces.size(); ordinal++) {
            require(pieces.get(ordinal).ordinal() == ordinal,
                    "Ancient City producer piece ordinal drift at " + ordinal);
        }
        for (Mc263AncientCityProducer.Edge edge : start.acceptedEdges()) {
            require(edge.sourcePiece() >= 0 && edge.sourcePiece() < edge.targetPiece()
                            && edge.targetPiece() < pieces.size() && !targets[edge.targetPiece()],
                    "Ancient City producer graph edge order drift");
            require(pools.contains(edge.selectedPool()) && pools.contains(edge.resolvedAlias())
                            && edge.selectedPool().equals(edge.resolvedAlias()),
                    "Ancient City producer pool/alias drift");
            targets[edge.targetPiece()] = true;
        }
        for (int ordinal = 1; ordinal < targets.length; ordinal++) {
            require(targets[ordinal],
                    "Ancient City producer graph lost piece " + ordinal);
        }
    }

    private static String producerPlanIdentity(Mc263AncientCityProducer.Start start,
            Mc263AncientCitySettlement.Clip clip, byte[] predecessor, byte[] successor) {
        StringBuilder text = new StringBuilder(1024);
        text.append(STRUCTURE_KEY).append('|').append(start.chunkX()).append('|')
                .append(start.chunkZ()).append('|').append(clip.chunkX()).append('|')
                .append(clip.chunkZ()).append('|').append(sha256(predecessor)).append('|')
                .append(sha256(successor)).append('\n');
        for (Mc263AncientCityProducer.BinaryNbt piece : start.carrier().pieces()) {
            text.append(piece.sha256()).append('\n');
        }
        for (Mc263AncientCityProducer.Edge edge : start.acceptedEdges()) {
            text.append(edge.sourcePiece()).append('>').append(edge.targetPiece()).append('|')
                    .append(edge.selectedPool()).append('|').append(edge.resolvedAlias()).append('\n');
        }
        return sha256(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static PersistedTemplatePlacement persistedTemplate(DecodedPiece piece,
            DecodedSingle single, Mc263AncientCitySettlement.Clip clip) {
        return new PersistedTemplatePlacement(piece.ordinal(), single.template(),
                single.processorList(), piece.originX(), piece.originY(), piece.originZ(),
                piece.rotation(), piece.boundingBox(), clip);
    }

    private static Mc263StructureCarrier.BoundingBox rawBounds(
            List<Mc263StructureCarrier.Piece> pieces) {
        require(!pieces.isEmpty(), "Ancient City persisted start has no pieces");
        Mc263StructureCarrier.BoundingBox box = pieces.getFirst().boundingBox();
        for (int index = 1; index < pieces.size(); index++) {
            box = box.encapsulating(pieces.get(index).boundingBox());
        }
        return box;
    }

    private static boolean intersects(Mc263StructureCarrier.BoundingBox box,
            Mc263AncientCitySettlement.Clip clip) {
        long minX = Math.multiplyExact((long) clip.chunkX(), 16L);
        long minZ = Math.multiplyExact((long) clip.chunkZ(), 16L);
        long maxX = Math.addExact(minX, 15L);
        long maxZ = Math.addExact(minZ, 15L);
        return box.maxX() >= minX && box.minX() <= maxX
                && box.maxY() >= clip.minYInclusive() && box.minY() <= clip.maxYInclusive()
                && box.maxZ() >= minZ && box.minZ() <= maxZ;
    }

    private static boolean exactReferenceSuccessor(byte[] predecessor, byte[] successor,
            int referenceOffset) {
        if (predecessor.length != successor.length || referenceOffset < 0
                || referenceOffset + Integer.BYTES > predecessor.length) return false;
        int differences = 0;
        for (int index = 0; index < predecessor.length; index++) {
            if (predecessor[index] == successor[index]) continue;
            if (index < referenceOffset || index >= referenceOffset + Integer.BYTES) return false;
            differences++;
        }
        return differences == 1;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static Mc263StructureCarrier.Projection carrierProjection(String value) {
        return switch (value) {
            case "rigid" -> Mc263StructureCarrier.Projection.RIGID;
            case "terrain_matching" -> Mc263StructureCarrier.Projection.TERRAIN_MATCHING;
            default -> throw new IllegalArgumentException(
                    "unknown Ancient City persisted projection: " + value);
        };
    }

    private static Rotation decodeRotation(String value) {
        try {
            return Rotation.valueOf(value);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(
                    "unknown Ancient City persisted rotation: " + value, failure);
        }
    }

    private static boolean key(String value) {
        return value != null && value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    @Override public boolean supportsVersion(String a,String b,String c,String d,String e){return runtime.supportsVersion(a,b,c,d,e);}
    @Override public boolean supportsSource(Mc263AncientCitySettlement.SourcePin v){return runtime.supportsSource(v);}
    @Override public boolean supportsExecutionCorpus(String v){return runtime.supportsExecutionCorpus(v);}
    @Override public boolean supportsStartGraphCarrier(String a,String b){return runtime.supportsStartGraphCarrier(a,b);}
    @Override public boolean supportsFeatureCodecs(String a,String b){return runtime.supportsFeatureCodecs(a,b);}
    @Override public boolean supportsFinalChunkSchema(int v){return runtime.supportsFinalChunkSchema(v);}
    @Override public boolean supportsAtomicReplayableSettlementWithRng(){return runtime.supportsAtomicReplayableSettlementWithRng();}
    @Override public boolean supportsForeignDestinations(){return runtime.supportsForeignDestinations();}
    @Override public boolean supportsCallerRngContinuation(){return runtime.supportsCallerRngContinuation();}
    @Override public boolean supportsClip(Mc263AncientCitySettlement.Clip v){return runtime.supportsClip(v);}
    @Override public boolean supportsSidecarLane(Mc263AncientCitySettlement.SidecarLane v){return runtime.supportsSidecarLane(v);}
    @Override public boolean supportsTemplate(String a,Mc263AncientCityProducer.TemplateStatus b){return runtime.supportsTemplate(a,b);}
    @Override public boolean supportsProcessorList(String v){return runtime.supportsProcessorList(v);}
    @Override public boolean supportsProcessorSemantic(String a,String b){return runtime.supportsProcessorSemantic(a,b);}
    @Override public boolean supportsConfiguredFeature(String v){return runtime.supportsConfiguredFeature(v);}
    @Override public boolean supportsExactState(String v){return runtime.supportsExactState(v);}
    @Override public boolean supportsLootTable(String v){return runtime.supportsLootTable(v);}
    @Override public boolean supportsBlockEntity(String v){return runtime.supportsBlockEntity(v);}
    @Override public boolean supportsCanonicalBlockEntityNbt(String a,List<Mc263AncientCitySettlement.NbtTagSpec> b){return runtime.supportsCanonicalBlockEntityNbt(a,b);}

    public interface Runtime extends Mc263AncientCitySettlement.WorldAccess {
        Session openSession(Mc263AncientCitySettlement.ExecutionRequest request, DispatchPlan plan);

        default boolean supportsPersistedStartSession() { return false; }

        default PersistedSession openPersistedSession(PersistedExecutionRequest request,
                PersistedDispatchPlan plan) {
            throw new UnsupportedOperationException(
                    "Ancient City persisted runtime is not available");
        }

        @Override
        default Mc263AncientCitySettlement.ExecutionResult executeProcedurally(
                Mc263AncientCitySettlement.ExecutionRequest request) {
            throw new UnsupportedOperationException(
                    "Ancient City runtime is callable only through the production adapter");
        }
    }

    public interface Session {
        void placeTemplate(TemplatePlacement placement);
        void placeFeature(FeaturePlacement placement);
        Mc263AncientCitySettlement.ExecutionResult finish();
        default void abort() { }
    }

    public interface PersistedSession {
        void placeTemplate(PersistedTemplatePlacement placement);
        void placeFeature(PersistedFeaturePlacement placement);
        Mc263StructureBatchBridge.Batch finish();
        void abort();
    }

    public sealed interface Placement permits TemplatePlacement, FeaturePlacement {
        int pieceOrdinal();
        Mc263AncientCitySettlement.Clip clip();
    }

    public record TemplatePlacement(int pieceOrdinal, String template, String processorList,
            int originX, int originY, int originZ, Rotation rotation,
            Mc263AncientCityProducer.Box pieceBoundingBox,
            Mc263AncientCitySettlement.Clip clip) implements Placement {
        public TemplatePlacement {
            require(pieceOrdinal >= 0 && key(template) && PROCESSORS.contains(processorList),
                    "unknown Ancient City template placement");
            Objects.requireNonNull(rotation); Objects.requireNonNull(pieceBoundingBox);
            Objects.requireNonNull(clip);
        }
    }

    public record FeaturePlacement(int pieceOrdinal, String featureKey,
            int originX, int originY, int originZ, Rotation rotation,
            Mc263AncientCityProducer.Box pieceBoundingBox,
            Mc263AncientCitySettlement.Clip clip) implements Placement {
        public FeaturePlacement {
            require(pieceOrdinal >= 0 && FEATURE_KEY.equals(featureKey),
                    "unknown Ancient City feature placement");
            Objects.requireNonNull(rotation); Objects.requireNonNull(pieceBoundingBox);
            Objects.requireNonNull(clip);
        }
    }

    record ProducerCarrierCheck(int pieceCount, List<Integer> intersectingPieceOrdinals,
            String planIdentity) {
        ProducerCarrierCheck {
            require(pieceCount > 0, "Ancient City producer check lost pieces");
            intersectingPieceOrdinals = List.copyOf(Objects.requireNonNull(
                    intersectingPieceOrdinals, "Ancient City producer check intersecting pieces"));
            require(planIdentity != null && planIdentity.matches("[0-9a-f]{64}"),
                    "Ancient City producer check identity malformed");
        }
    }

    public record PersistedExecutionRequest(Mc263StructureCarrier.ValidStart start,
            Mc263StructureCarrier.RawStartPayload rawStart,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263AncientCitySettlement.Clip clip,
            Mc263StructureCarrier.ProducerGraphPayload producerGraph) {
        public PersistedExecutionRequest {
            Objects.requireNonNull(start, "Ancient City persisted start");
            Objects.requireNonNull(rawStart, "Ancient City persisted raw start");
            Objects.requireNonNull(references, "Ancient City persisted references");
            Objects.requireNonNull(clip, "Ancient City persisted clip");
        }

    }

    public sealed interface PersistedPlacement
            permits PersistedTemplatePlacement, PersistedFeaturePlacement {
        int pieceOrdinal();
        Mc263AncientCitySettlement.Clip clip();
    }

    public record PersistedTemplatePlacement(int pieceOrdinal, String template,
            String processorList, int originX, int originY, int originZ, Rotation rotation,
            Mc263StructureCarrier.BoundingBox pieceBoundingBox,
            Mc263AncientCitySettlement.Clip clip) implements PersistedPlacement {
        public PersistedTemplatePlacement {
            require(pieceOrdinal >= 0 && key(template) && PROCESSORS.contains(processorList),
                    "unknown Ancient City persisted template placement");
            Objects.requireNonNull(rotation, "Ancient City persisted template rotation");
            Objects.requireNonNull(pieceBoundingBox,
                    "Ancient City persisted template bounding box");
            Objects.requireNonNull(clip, "Ancient City persisted template clip");
        }
    }

    public record PersistedFeaturePlacement(int pieceOrdinal, String featureKey,
            int originX, int originY, int originZ, Rotation rotation,
            Mc263StructureCarrier.BoundingBox pieceBoundingBox,
            Mc263AncientCitySettlement.Clip clip) implements PersistedPlacement {
        public PersistedFeaturePlacement {
            require(pieceOrdinal >= 0 && FEATURE_KEY.equals(featureKey),
                    "unknown Ancient City persisted feature placement");
            Objects.requireNonNull(rotation, "Ancient City persisted feature rotation");
            Objects.requireNonNull(pieceBoundingBox,
                    "Ancient City persisted feature bounding box");
            Objects.requireNonNull(clip, "Ancient City persisted feature clip");
        }
    }

    public record PersistedDispatchPlan(Mc263StructureCarrier.ValidStart start,
            Mc263StructureCarrier.RawStartPayload rawStart,
            Mc263StructureCarrier.ChunkReferences references,
            Mc263AncientCitySettlement.Clip clip, List<PersistedPlacement> placements,
            List<Integer> intersectingPieceOrdinals) {
        public PersistedDispatchPlan {
            Objects.requireNonNull(start, "Ancient City persisted plan start");
            Objects.requireNonNull(rawStart, "Ancient City persisted plan raw start");
            Objects.requireNonNull(references, "Ancient City persisted plan references");
            Objects.requireNonNull(clip, "Ancient City persisted plan clip");
            placements = List.copyOf(placements);
            intersectingPieceOrdinals = List.copyOf(intersectingPieceOrdinals);
        }
    }

    public record DispatchPlan(Mc263AncientCityProducer.Start start,
            Mc263AncientCitySettlement.Clip clip, List<Placement> placements,
            List<Integer> intersectingPieceOrdinals) {
        public DispatchPlan {
            Objects.requireNonNull(start); Objects.requireNonNull(clip);
            placements = List.copyOf(placements);
            intersectingPieceOrdinals = List.copyOf(intersectingPieceOrdinals);
        }
    }

    private sealed interface DecodedElement permits DecodedSingle, DecodedList, DecodedFeature {
        Mc263StructureCarrier.Projection projection();
    }

    private record DecodedSingle(String template, String processorList,
            Mc263StructureCarrier.Projection projection) implements DecodedElement { }

    private record DecodedList(List<DecodedSingle> children,
            Mc263StructureCarrier.Projection projection) implements DecodedElement {
        private DecodedList { children = List.copyOf(children); }
    }

    private record DecodedFeature(String featureKey,
            Mc263StructureCarrier.Projection projection) implements DecodedElement { }

    private record DecodedPiece(int ordinal, DecodedElement element, int originX, int originY,
            int originZ, Rotation rotation, Mc263StructureCarrier.BoundingBox boundingBox,
            int groundLevelDelta, List<Mc263StructureCarrier.Junction> junctions) {
        private DecodedPiece { junctions = List.copyOf(junctions); }
    }

    private record DecodedStart(int references, int referenceOffset, List<DecodedPiece> pieces) {
        private DecodedStart { pieces = List.copyOf(pieces); }
    }

    private record DecodedPair(DecodedStart predecessor, DecodedStart successor) { }

    private static final class PersistedNbtCursor {
        private final byte[] bytes;
        private int position;

        private PersistedNbtCursor(byte[] bytes) {
            this.bytes = Objects.requireNonNull(bytes, "Ancient City persisted raw NBT").clone();
        }

        private int position() { return position; }

        private void requireFullyConsumed() {
            require(position == bytes.length,
                    "Ancient City persisted raw NBT has trailing bytes");
        }

        private int unsignedByte() {
            require(position < bytes.length, "truncated Ancient City persisted raw NBT");
            return Byte.toUnsignedInt(bytes[position++]);
        }

        private int unsignedShort() {
            require(position + 2 <= bytes.length,
                    "truncated Ancient City persisted UTF length");
            int value = Byte.toUnsignedInt(bytes[position]) << 8
                    | Byte.toUnsignedInt(bytes[position + 1]);
            position += 2;
            return value;
        }

        private int int32() {
            require(position + 4 <= bytes.length,
                    "truncated Ancient City persisted integer");
            int value = Byte.toUnsignedInt(bytes[position]) << 24
                    | Byte.toUnsignedInt(bytes[position + 1]) << 16
                    | Byte.toUnsignedInt(bytes[position + 2]) << 8
                    | Byte.toUnsignedInt(bytes[position + 3]);
            position += 4;
            return value;
        }

        private String utf() {
            int length = unsignedShort();
            require(length <= bytes.length - position,
                    "truncated Ancient City persisted UTF payload");
            for (int index = 0; index < length; index++) {
                int value = Byte.toUnsignedInt(bytes[position + index]);
                require(value >= 1 && value <= 0x7f,
                        "non-ASCII Ancient City persisted production string");
            }
            String value = new String(bytes, position, length, StandardCharsets.US_ASCII);
            position += length;
            return value;
        }

        private void expectByte(int expected, String label) {
            require(unsignedByte() == expected, label + " drift");
        }

        private void expectUtf(String expected, String label) {
            require(utf().equals(expected), label + " drift");
        }

        private void expectTag(int type, String name) {
            expectByte(type, "Ancient City tag type for " + name);
            expectUtf(name, "Ancient City tag name for " + name);
        }

        private boolean rootBodyEquals(int bodyStart, byte[] rootPayload) {
            int bodyLength = position - bodyStart;
            return bodyStart >= 0 && bodyLength > 0 && rootPayload.length == bodyLength + 3
                    && rootPayload[0] == 10 && rootPayload[1] == 0 && rootPayload[2] == 0
                    && Arrays.equals(rootPayload, 3, rootPayload.length,
                            bytes, bodyStart, position);
        }
    }
}
