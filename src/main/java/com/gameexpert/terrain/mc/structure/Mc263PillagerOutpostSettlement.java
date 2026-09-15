package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.feature.Mc263FeatureBlockState;
import com.gameexpert.terrain.mc.feature.Mc263WorldGenRegionRandom;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Bounds;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.ElementType;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Projection;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureBoundary.Rotation;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.AcceptedEdge;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.ExecutionPlan;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.PiecePlacement;
import com.gameexpert.terrain.mc.structure.Mc263JigsawStructureExecutor.PlannerRngReceipt;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Dormant exact settlement boundary for Minecraft Java 26.3-snapshot-7 Pillager Outposts.
 *
 * <p>The settlement consumes the current procedural producer result and authenticated typed
 * grammar. Production callers may supply an externally authenticated start identity and exact
 * reference-successor receipt; the legacy four start-graph probes remain a compatibility authority
 * only. No authority coordinate or recorded operation stream is replayed. Legacy compatibility
 * keeps the historical evidence-hash preflight; the production-authorized path instead consumes
 * the source-pinned grammar plus authority reconstructed from persisted start NBT. All live queries
 * and mutations occur against an unpublished fork and the caller RNG is advanced
 * only by the final atomic publish.</p>
 */
public final class Mc263PillagerOutpostSettlement {
    public static final int TEMPLATE_WRITE_FLAGS = 18;
    public static final int BLOCK_ENTITY_CLEAR_FLAGS = 820;
    public static final int WATER_TICK_DELAY = 5;
    public static final int CLIP_MIN_Y = -63;
    public static final int CLIP_MAX_Y = 319;
    public static final String WATER_FLUID = "minecraft:water";
    public static final String HEIGHTMAP = "WORLD_SURFACE_WG";
    public static final String BARRIER_STATE = "minecraft:barrier[waterlogged=false]";
    public static final String OUTPOST_LOOT = "minecraft:chests/pillager_outpost";

    private static final String INLINE_PROCESSOR = "inline";
    private static final String ROT_PROCESSOR = "minecraft:outpost_rot";
    private static final double ROT_INTEGRITY = 0.05D;
    private static final long LEGACY_MULTIPLIER = 0x5DEECE66DL;
    private static final long LEGACY_ADDEND = 0xBL;
    private static final long LEGACY_MASK = (1L << 48) - 1L;

    /**
     * Frozen publication-fingerprint salt. These are the SHA-256 digests of the four accepted
     * Pillager Outpost verification corpora that the original fingerprint mixed in. The corpora
     * themselves are test-only oracles read by {@link Mc263PillagerOutpostLegacyProbeOracle}; the
     * digests stay here as opaque constants so the published fingerprint is bit-identical.
     */
    private static final List<String> FINGERPRINT_SALT = List.of(
            "7ffa7158f8d6031306600c69e45cdcccc42f66b2545729382b221e1ab07a73e2",
            "1e2b278bb21001c8a69c2b52cd5d106efb5e31955d1dd9eb121bbbbb4cd5b277",
            "4204ad6561a8d0023a6b09e2df345134b94913f0b98c7b517dcdd6f373949752",
            "91ff210f985cc69ec2f783a20979c1b77ee2aefc224520065ea497cef1ed88aa");

    private static final List<String> ALLAY_ATTRIBUTES = List.of(
            "minecraft:follow_range", "minecraft:movement_speed");
    private static final List<String> GOLEM_ATTRIBUTES = List.of(
            "minecraft:armor", "minecraft:armor_toughness", "minecraft:attack_knockback",
            "minecraft:follow_range", "minecraft:knockback_resistance",
            "minecraft:max_health", "minecraft:movement_speed");
    private static final List<String> E3L_RELEASED_EXACT_STATES = List.of(
            "minecraft:white_wall_banner[facing=north]",
            "minecraft:white_wall_banner[facing=west]",
            "minecraft:white_wall_banner[facing=east]",
            "minecraft:white_wall_banner[facing=south]");
    private static final List<Direction> LIQUID_NEIGHBORS = List.of(
            Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    private static final Comparator<Vec> POSITION_ORDER = Comparator.comparingInt(Vec::y)
            .thenComparingInt(Vec::z).thenComparingInt(Vec::x);

    private Mc263PillagerOutpostSettlement() { }

    /**
     * Settles a procedurally produced start after an external authority has authenticated its full
     * production identity. This path contains no accepted-coordinate lookup.
     */
    public static Settlement execute(Mc263PillagerOutpostProducer.Start start, Clip clip,
            StartAuthority startAuthority, WorldTransaction transaction, PlacementRandom random) {
        return executeInternal(start, clip, Objects.requireNonNull(startAuthority,
                "Pillager Outpost start authority"), transaction, random);
    }

    /**
     * Settles one persisted Outpost clip without regenerating the jigsaw start or touching Legacy48.
     * The supplied STR start/raw payload/source order and WGR source are the complete live authority.
     */
    public static Settlement executePersisted(Mc263StructureCarrier.ValidStart persistedStart,
            Mc263StructureCarrier.RawStartPayload rawStart, List<Long> orderedSourceReferences,
            Clip clip, Mc263StructureCarrier structureCarrier,
            PersistedWorldTransaction transaction, Mc263WorldGenRegionRandom worldGenRegionRandom) {
        Objects.requireNonNull(persistedStart, "persisted Pillager Outpost start");
        Objects.requireNonNull(rawStart, "persisted Pillager Outpost raw start");
        orderedSourceReferences = List.copyOf(
                Objects.requireNonNull(orderedSourceReferences, "ordered Outpost source references"));
        Objects.requireNonNull(clip, "persisted Pillager Outpost clip");
        Objects.requireNonNull(structureCarrier, "persisted Pillager Outpost structure carrier");
        Objects.requireNonNull(transaction, "persisted Pillager Outpost transaction");
        Objects.requireNonNull(worldGenRegionRandom, "persisted Pillager Outpost WGR RNG");

        Mc263WorldGenRegionRandom.State randomPredecessor = worldGenRegionRandom.snapshot();
        PersistedPreflight prepared = preflightPersisted(persistedStart, rawStart,
                orderedSourceReferences, clip, structureCarrier, transaction, randomPredecessor);
        String fingerprint = persistedPublicationFingerprint(persistedStart, rawStart,
                orderedSourceReferences, clip, randomPredecessor);

        WorldTransaction isolated = Objects.requireNonNull(
                transaction.fork(), "isolated persisted Pillager Outpost transaction");
        if (isolated == transaction || !(isolated instanceof PersistedWorldTransaction)) {
            throw new IllegalStateException(
                    "persisted Pillager Outpost transaction fork is not isolated/capable");
        }
        Mc263WorldGenRegionRandom candidate = worldGenRegionRandom.forkForTransaction();
        SettlementRandom placementRandom = new WorldGenRegionSettlementRandom(candidate);
        Trace trace = new Trace(isolated);
        LinkedHashMap<Vec, String> finalStates = new LinkedHashMap<>();
        LinkedHashMap<Vec, LoadedBlockEntity> finalBlockEntities = new LinkedHashMap<>();
        ArrayList<EntityPayload> entities = new ArrayList<>();
        Set<EntityIdentity> entityIdentities = new LinkedHashSet<>();
        int[] blockEntityEncounter = {0};
        int[] entityEncounter = {0};

        for (PreparedPiece piece : prepared.plan.pieces) {
            placePiece(piece, prepared.plan, clip, trace, placementRandom, finalStates,
                    finalBlockEntities, blockEntityEncounter, entities, entityIdentities,
                    entityEncounter);
        }
        Settlement settlement = settlement(clip, trace.operations(), finalStates,
                finalBlockEntities, entities, prepared.rawSuccessor, placementRandom, fingerprint);
        require(worldGenRegionRandom.snapshot().equals(randomPredecessor),
                "persisted Pillager Outpost WGR changed before atomic publication");
        Objects.requireNonNull(transaction.publishPersistedAtomically(isolated, settlement,
                structureCarrier, prepared.carrierSuccessor, worldGenRegionRandom,
                randomPredecessor, candidate), "persisted Pillager Outpost publish status");
        return settlement;
    }

    private static Settlement executeInternal(Mc263PillagerOutpostProducer.Start start, Clip clip,
            StartAuthority startAuthority, WorldTransaction transaction, PlacementRandom random) {
        Objects.requireNonNull(start, "Pillager Outpost start");
        Objects.requireNonNull(clip, "Pillager Outpost clip");
        Objects.requireNonNull(transaction, "Pillager Outpost transaction");
        Objects.requireNonNull(random, "Pillager Outpost caller RNG");

        Plan plan = preflight(start, clip, startAuthority, transaction);
        long callerLo = random.lo();
        long callerHi = random.hi();
        int callerCount = random.count();
        String fingerprint = publicationFingerprint(start, clip, callerLo, callerHi, callerCount);
        PlacementRandom candidate = random.copy();
        SettlementRandom placementRandom = new LegacySettlementRandom(candidate);
        WorldTransaction isolated = Objects.requireNonNull(
                transaction.fork(), "isolated Pillager Outpost transaction");
        if (isolated == transaction) {
            throw new IllegalStateException("Pillager Outpost transaction fork is not isolated");
        }

        Trace trace = new Trace(isolated);
        LinkedHashMap<Vec, String> finalStates = new LinkedHashMap<>();
        LinkedHashMap<Vec, LoadedBlockEntity> finalBlockEntities = new LinkedHashMap<>();
        ArrayList<EntityPayload> entities = new ArrayList<>();
        Set<EntityIdentity> entityIdentities = new LinkedHashSet<>();
        int[] blockEntityEncounter = {0};
        int[] entityEncounter = {0};

        for (PreparedPiece piece : plan.pieces) {
            placePiece(piece, plan, clip, trace, placementRandom, finalStates, finalBlockEntities,
                    blockEntityEncounter, entities, entityIdentities, entityEncounter);
        }

        PersistedStart successor = successor(start.carrier().structureStart(), plan.startAuthority);
        Settlement settlement = settlement(clip, trace.operations(), finalStates,
                finalBlockEntities, entities, successor, placementRandom, fingerprint);
        require(random.lo() == callerLo && random.hi() == callerHi && random.count() == callerCount,
                "Pillager Outpost caller RNG changed before atomic publication");
        Objects.requireNonNull(transaction.publishAtomically(
                isolated, settlement, random, candidate), "Pillager Outpost publish status");
        return settlement;
    }

    private static Plan preflight(Mc263PillagerOutpostProducer.Start start, Clip clip,
            StartAuthority startAuthority, WorldTransaction transaction) {
        Mc263PillagerOutpostGrammar.Corpus grammar = Mc263PillagerOutpostGrammar.pinned();
        authenticateStart(start, startAuthority);
        StartAuthority acceptedAuthority = startAuthority;
        validateProcessorClosure(grammar.evidence());
        validateReleasedExactStateClosure();
        validateClip(start, clip);
        preflightCapabilities(transaction);

        ArrayList<PreparedPiece> pieces = new ArrayList<>();
        LinkedHashSet<String> exactStates = new LinkedHashSet<>();
        LinkedHashSet<BlockEntitySpec> blockEntities = new LinkedHashSet<>();
        LinkedHashSet<String> entityTypes = new LinkedHashSet<>();
        Mc263PillagerOutpostGrammar.Evidence accepted = grammar.evidence();

        List<PiecePlacement> placements = start.executionPlan().pieces();
        for (int ordinal = 0; ordinal < placements.size(); ordinal++) {
            PiecePlacement placement = placements.get(ordinal);
            if (!intersects(placement.bounds(), clip)) continue;
            ArrayList<PreparedTemplate> templates = new ArrayList<>();
            if (placement.type() == ElementType.LEGACY_SINGLE) {
                templates.add(prepareTemplate(accepted, placement.elementKey(), placement.rotation(),
                        processorForTemplate(accepted, placement.elementKey()), exactStates,
                        blockEntities, entityTypes));
            } else if (placement.type() == ElementType.LIST) {
                if (placement.components().isEmpty()) {
                    throw new IllegalArgumentException("empty Pillager Outpost list element");
                }
                for (String component : placement.components()) {
                    templates.add(prepareTemplate(accepted, component, placement.rotation(),
                            processorForTemplate(accepted, component), exactStates,
                            blockEntities, entityTypes));
                }
            } else {
                throw new IllegalArgumentException(
                        "unsupported Pillager Outpost piece type: " + placement.type());
            }
            pieces.add(new PreparedPiece(ordinal, placement.originX(), placement.originY(),
                    placement.originZ(), placement.rotation(), placement.projection(),
                    List.copyOf(templates)));
        }

        String barrier = exact(BARRIER_STATE);
        exactStates.add(barrier);
        for (String state : exactStates) {
            if (!transaction.supportsExactState(state)) {
                throw new UnsupportedOperationException(
                        "unsupported Pillager Outpost exact state: " + state);
            }
        }
        for (BlockEntitySpec spec : blockEntities) {
            if (!transaction.supportsBlockEntity(spec.blockIdentity, spec.entityType)) {
                throw new UnsupportedOperationException(
                        "unsupported Pillager Outpost block entity: " + spec);
            }
            if (spec.lootTable != null && !transaction.supportsLootTable(spec.lootTable)) {
                throw new UnsupportedOperationException(
                        "unsupported Pillager Outpost loot table: " + spec.lootTable);
            }
        }
        for (String entityType : entityTypes) {
            if (!transaction.supportsStructureEntity(entityType)) {
                throw new UnsupportedOperationException(
                        "unsupported Pillager Outpost structure entity: " + entityType);
            }
        }
        return new Plan(List.copyOf(pieces), Set.copyOf(exactStates), acceptedAuthority);
    }

    private static PersistedPreflight preflightPersisted(
            Mc263StructureCarrier.ValidStart persistedStart,
            Mc263StructureCarrier.RawStartPayload rawStart, List<Long> orderedSourceReferences,
            Clip clip, Mc263StructureCarrier structureCarrier,
            PersistedWorldTransaction transaction,
            Mc263WorldGenRegionRandom.State randomPredecessor) {
        String structure = Mc263PillagerOutpostProducer.STRUCTURE_KEY;
        require(persistedStart.startKey().equals(structure + "@"
                        + persistedStart.originChunkX() + "," + persistedStart.originChunkZ()),
                "noncanonical persisted Pillager Outpost start key");
        require(persistedStart.references() == 0 || persistedStart.references() == 1,
                "persisted Pillager Outpost reference count drift");
        require(!persistedStart.orderedPieces().isEmpty(),
                "persisted Pillager Outpost has no pieces");
        require(rawStart.structureId().equals(structure)
                        && rawStart.startKey().equals(persistedStart.startKey())
                        && rawStart.originChunkX() == persistedStart.originChunkX()
                        && rawStart.originChunkZ() == persistedStart.originChunkZ(),
                "persisted Pillager Outpost raw-start identity mismatch");

        byte[] predecessor = rawStart.predecessorBinaryNbtCompound();
        byte[] root = persistedStart.orderedPieces().getFirst()
                .persistedPayload().binaryNbtCompound();
        Mc263PillagerOutpostPersistedAuthority.Reloaded reloaded =
                Mc263PillagerOutpostPersistedAuthority.reload(root, predecessor);
        StartAuthority authority = reloaded.startAuthority();
        require(reloaded.chunkX() == persistedStart.originChunkX()
                        && reloaded.chunkZ() == persistedStart.originChunkZ(),
                "persisted Pillager Outpost authority origin mismatch");
        require(rawStart.predecessorSha256().equals(authority.predecessorSha256())
                        && rawStart.successorSha256().equals(authority.successorSha256())
                        && predecessor.length == authority.predecessorLength(),
                "persisted Pillager Outpost raw-start authority mismatch");
        PersistedStart rawSuccessor = persistedRawSuccessor(rawStart, authority);
        PersistedAuthorityPlan persistedPlan = decodePersistedAuthority(
                reloaded.authorityPayload());
        require(persistedPlan.worldSeed == reloaded.worldSeed()
                        && persistedPlan.chunkX == persistedStart.originChunkX()
                        && persistedPlan.chunkZ == persistedStart.originChunkZ(),
                "persisted Pillager Outpost decoded authority mismatch");
        require(persistedPlan.pieces.size() == persistedStart.orderedPieces().size(),
                "persisted Pillager Outpost piece cardinality mismatch");
        require(sameBox(persistedStart.adjustedBoundingBox(), persistedPlan.aggregate),
                "persisted Pillager Outpost aggregate bounding-box mismatch");

        authenticateCarrierBinding(structureCarrier, persistedStart, rawStart,
                orderedSourceReferences, clip);
        validateClip(persistedPlan.aggregate, clip);

        Mc263PillagerOutpostGrammar.Corpus grammar = Mc263PillagerOutpostGrammar.pinned();
        Mc263PillagerOutpostGrammar.Evidence evidence = grammar.evidence();
        validateProcessorClosure(evidence);
        validateReleasedExactStateClosure();
        preflightCapabilities(transaction);
        if (!transaction.supportsPersistedAtomicPublishWithWorldGenRegionRandom()) {
            throw new UnsupportedOperationException(
                    "atomic Pillager Outpost STR/WGR publication required");
        }

        ArrayList<PreparedPiece> pieces = new ArrayList<>();
        LinkedHashSet<String> exactStates = new LinkedHashSet<>();
        LinkedHashSet<BlockEntitySpec> blockEntities = new LinkedHashSet<>();
        LinkedHashSet<String> entityTypes = new LinkedHashSet<>();
        for (int ordinal = 0; ordinal < persistedPlan.pieces.size(); ordinal++) {
            PersistedPieceFact fact = persistedPlan.pieces.get(ordinal);
            Mc263StructureCarrier.Piece carrierPiece =
                    persistedStart.orderedPieces().get(ordinal);
            authenticatePersistedPiece(carrierPiece, fact);
            for (PersistedTemplateFact template : fact.templates) {
                String expectedProcessor = processorForTemplate(evidence, template.templateKey);
                require(expectedProcessor.equals(template.processor),
                        "persisted Pillager Outpost processor authority mismatch");
            }
            if (!intersects(fact.bounds, clip)) continue;

            ArrayList<PreparedTemplate> templates = new ArrayList<>();
            for (PersistedTemplateFact template : fact.templates) {
                templates.add(prepareTemplate(evidence, template.templateKey, fact.rotation,
                        template.processor, exactStates, blockEntities, entityTypes));
            }
            pieces.add(new PreparedPiece(ordinal, fact.originX, fact.originY, fact.originZ,
                    fact.rotation, fact.projection, List.copyOf(templates)));
        }

        String barrier = exact(BARRIER_STATE);
        exactStates.add(barrier);
        requirePreparedCapabilities(transaction, exactStates, blockEntities, entityTypes);
        Mc263StructureCarrier carrierSuccessor =
                persistedReferenceSuccessor(structureCarrier, persistedStart);
        if (!transaction.acceptsPersistedPredecessor(structureCarrier, randomPredecessor)) {
            throw new IllegalStateException("stale persisted Pillager Outpost STR/WGR predecessor");
        }
        return new PersistedPreflight(
                new Plan(List.copyOf(pieces), Set.copyOf(exactStates), authority),
                rawSuccessor, carrierSuccessor);
    }

    private static void authenticateCarrierBinding(Mc263StructureCarrier structureCarrier,
            Mc263StructureCarrier.ValidStart persistedStart,
            Mc263StructureCarrier.RawStartPayload rawStart, List<Long> orderedSourceReferences,
            Clip clip) {
        String structure = Mc263PillagerOutpostProducer.STRUCTURE_KEY;
        structureCarrier.registry().require(structure);

        int matchingStarts = 0;
        for (Mc263StructureCarrier.ChunkStarts chunk : structureCarrier.startChunks()) {
            for (Mc263StructureCarrier.StartEntry entry : chunk.orderedStarts()) {
                if (entry.structureId().equals(structure)
                        && entry.body() instanceof Mc263StructureCarrier.ValidStart valid
                        && valid.startKey().equals(persistedStart.startKey())) {
                    require(valid.equals(persistedStart),
                            "persisted Pillager Outpost start/carrier mismatch");
                    matchingStarts++;
                }
            }
        }
        require(matchingStarts == 1,
                "persisted Pillager Outpost start must occur exactly once in STR");

        Mc263StructureCarrier.RawStartPayload carrierRaw =
                structureCarrier.requireRawStartPayload(structure, persistedStart);
        require(carrierRaw.predecessorSha256().equals(rawStart.predecessorSha256())
                        && carrierRaw.successorSha256().equals(rawStart.successorSha256())
                        && Arrays.equals(carrierRaw.predecessorBinaryNbtCompound(),
                                rawStart.predecessorBinaryNbtCompound())
                        && Arrays.equals(carrierRaw.successorBinaryNbtCompound(),
                                rawStart.successorBinaryNbtCompound()),
                "persisted Pillager Outpost raw-start/carrier mismatch");

        Mc263StructureCarrier.ChunkReferences references =
                structureCarrier.referenceChunk(clip.chunkX, clip.chunkZ)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "persisted Pillager Outpost clip has no STR reference map"));
        List<Mc263StructureCarrier.ReferenceSet> sets = references.orderedSets().stream()
                .filter(value -> value.structureId().equals(structure)).toList();
        require(sets.size() == 1,
                "persisted Pillager Outpost clip must have one ordered reference set");
        require(sets.getFirst().orderedOrigins().equals(orderedSourceReferences),
                "persisted Pillager Outpost source-reference order mismatch");
        long origin = Mc263StructureCarrier.packChunk(
                persistedStart.originChunkX(), persistedStart.originChunkZ());
        require(orderedSourceReferences.stream().filter(value -> value == origin).count() == 1,
                "persisted Pillager Outpost origin reference missing/duplicated");
        long resolved = structureCarrier.resolveStarts(references, structure).stream()
                .filter(value -> value.startKey().equals(persistedStart.startKey())).count();
        require(resolved == 1, "persisted Pillager Outpost reference did not resolve exact start");
    }

    private static void authenticatePersistedPiece(Mc263StructureCarrier.Piece carrierPiece,
            PersistedPieceFact fact) {
        require(carrierPiece.pieceType().equals("minecraft:jigsaw") && carrierPiece.poolElement(),
                "persisted Pillager Outpost piece type drift");
        require(sameBox(carrierPiece.boundingBox(), fact.bounds)
                        && carrierPiece.groundLevelDelta() == fact.groundLevelDelta
                        && projection(carrierPiece.projection()) == fact.projection,
                "persisted Pillager Outpost piece geometry/projection drift");
        require(carrierPiece.junctions().size() == fact.junctions.size(),
                "persisted Pillager Outpost junction cardinality drift");
        for (int index = 0; index < fact.junctions.size(); index++) {
            Mc263StructureCarrier.Junction carrier = carrierPiece.junctions().get(index);
            PersistedJunctionFact expected = fact.junctions.get(index);
            require(carrier.sourceX() == expected.sourceX
                            && carrier.sourceGroundY() == expected.sourceGroundY
                            && carrier.sourceZ() == expected.sourceZ
                            && carrier.deltaY() == expected.deltaY
                            && projection(carrier.destinationProjection())
                                    == expected.destinationProjection,
                    "persisted Pillager Outpost junction order/identity drift");
        }

        PersistedPieceNbt nbt = decodePersistedPieceNbt(
                carrierPiece.persistedPayload().binaryNbtCompound());
        require(nbt.originX == fact.originX && nbt.originY == fact.originY
                        && nbt.originZ == fact.originZ && nbt.rotation == fact.rotation
                        && nbt.projection == fact.projection
                        && nbt.groundLevelDelta == fact.groundLevelDelta
                        && sameBox(nbt.bounds, fact.bounds)
                        && nbt.type == fact.type
                        && nbt.templates.equals(fact.templates)
                        && nbt.junctions.equals(fact.junctions),
                "persisted Pillager Outpost jigsaw NBT authority mismatch");
    }

    private static void requirePreparedCapabilities(WorldTransaction transaction,
            Set<String> exactStates, Set<BlockEntitySpec> blockEntities, Set<String> entityTypes) {
        for (String state : exactStates) {
            if (!transaction.supportsExactState(state)) {
                throw new UnsupportedOperationException(
                        "unsupported Pillager Outpost exact state: " + state);
            }
        }
        for (BlockEntitySpec spec : blockEntities) {
            if (!transaction.supportsBlockEntity(spec.blockIdentity, spec.entityType)) {
                throw new UnsupportedOperationException(
                        "unsupported Pillager Outpost block entity: " + spec);
            }
            if (spec.lootTable != null && !transaction.supportsLootTable(spec.lootTable)) {
                throw new UnsupportedOperationException(
                        "unsupported Pillager Outpost loot table: " + spec.lootTable);
            }
        }
        for (String entityType : entityTypes) {
            if (!transaction.supportsStructureEntity(entityType)) {
                throw new UnsupportedOperationException(
                        "unsupported Pillager Outpost structure entity: " + entityType);
            }
        }
    }

    private static PersistedStart persistedRawSuccessor(
            Mc263StructureCarrier.RawStartPayload rawStart, StartAuthority authority) {
        byte[] before = rawStart.predecessorBinaryNbtCompound();
        byte[] after = rawStart.successorBinaryNbtCompound();
        require(before.length == authority.predecessorLength()
                        && sha256(before).equals(authority.predecessorSha256()),
                "persisted Pillager Outpost predecessor hash/length mismatch");
        require(after.length == before.length && sha256(after).equals(authority.successorSha256()),
                "persisted Pillager Outpost successor hash/length mismatch");
        byte[] expected = before.clone();
        require(expected.length > 19 && expected[19] == 0,
                "persisted Pillager Outpost predecessor references drift");
        expected[19] = 1;
        require(Arrays.equals(expected, after),
                "persisted Pillager Outpost successor is not reference-only 0->1");
        return new PersistedStart(after, authority.successorSha256(), 19, 0, 1);
    }

    private static Mc263StructureCarrier persistedReferenceSuccessor(
            Mc263StructureCarrier predecessor, Mc263StructureCarrier.ValidStart target) {
        ArrayList<Mc263StructureCarrier.ChunkStarts> chunks = new ArrayList<>();
        int replacements = 0;
        for (Mc263StructureCarrier.ChunkStarts chunk : predecessor.startChunks()) {
            ArrayList<Mc263StructureCarrier.StartEntry> starts = new ArrayList<>();
            for (Mc263StructureCarrier.StartEntry entry : chunk.orderedStarts()) {
                if (entry.structureId().equals(Mc263PillagerOutpostProducer.STRUCTURE_KEY)
                        && entry.body() instanceof Mc263StructureCarrier.ValidStart valid
                        && valid.startKey().equals(target.startKey())) {
                    require(valid.equals(target),
                            "persisted Pillager Outpost successor target drift");
                    starts.add(new Mc263StructureCarrier.StartEntry(entry.structureId(),
                            new Mc263StructureCarrier.ValidStart(valid.startKey(),
                                    valid.originChunkX(), valid.originChunkZ(), 1,
                                    valid.adjustedBoundingBox(), valid.orderedPieces())));
                    replacements++;
                } else {
                    starts.add(entry);
                }
            }
            chunks.add(chunk.withStarts(starts));
        }
        require(replacements == 1,
                "persisted Pillager Outpost successor target cardinality drift");
        Mc263StructureCarrier successor = new Mc263StructureCarrier(predecessor.registry(),
                chunks, predecessor.referenceChunks(), predecessor.rawStartPayloads(),
                predecessor.producerGraphPayloads());
        return successor.strictlyDecoded();
    }

    private static String persistedPublicationFingerprint(
            Mc263StructureCarrier.ValidStart persistedStart,
            Mc263StructureCarrier.RawStartPayload rawStart, List<Long> orderedSourceReferences,
            Clip clip, Mc263WorldGenRegionRandom.State randomPredecessor) {
        StringBuilder value = new StringBuilder("OUT263PW1|")
                .append(persistedStart.startKey()).append('|')
                .append(rawStart.predecessorSha256()).append('|')
                .append(rawStart.successorSha256()).append('|')
                .append(clip.chunkX).append(',').append(clip.chunkZ).append(',')
                .append(clip.ordinal).append(',').append(clip.minY).append(',')
                .append(clip.maxY).append('|')
                .append(randomPredecessor.receiptSha256()).append('|');
        for (long source : orderedSourceReferences) {
            value.append(Long.toUnsignedString(source)).append(',');
        }
        value.append('|');
        for (Mc263StructureCarrier.Piece piece : persistedStart.orderedPieces()) {
            value.append(sha256(piece.persistedPayload().binaryNbtCompound())).append(';');
        }
        return sha256(value.toString().getBytes(StandardCharsets.US_ASCII));
    }

    private static Projection projection(Mc263StructureCarrier.Projection projection) {
        return switch (projection) {
            case RIGID -> Projection.RIGID;
            case TERRAIN_MATCHING -> Projection.TERRAIN_MATCHING;
            case NOT_APPLICABLE -> throw new IllegalArgumentException(
                    "non-jigsaw persisted Pillager Outpost projection");
        };
    }

    private static boolean sameBox(Mc263StructureCarrier.BoundingBox left, Bounds right) {
        return left.minX() == right.minX() && left.minY() == right.minY()
                && left.minZ() == right.minZ() && left.maxX() == right.maxX()
                && left.maxY() == right.maxY() && left.maxZ() == right.maxZ();
    }

    private static boolean sameBox(Bounds left, Bounds right) {
        return left.minX() == right.minX() && left.minY() == right.minY()
                && left.minZ() == right.minZ() && left.maxX() == right.maxX()
                && left.maxY() == right.maxY() && left.maxZ() == right.maxZ();
    }

    private static PersistedAuthorityPlan decodePersistedAuthority(byte[] payload) {
        PersistedCursor in = new PersistedCursor(payload, "Pillager Outpost persisted authority");
        require(in.fixedAscii(8).equals(Mc263PillagerOutpostPersistedAuthority.FORMAT)
                        && in.i32() == Mc263PillagerOutpostPersistedAuthority.SCHEMA,
                "Pillager Outpost persisted authority header drift");
        for (int i = 0; i < 5; i++) in.string();
        in.i32(); in.i32();
        in.string();
        require(in.string().equals(Mc263PillagerOutpostProducer.CARRIER_FORMAT),
                "Pillager Outpost persisted carrier format drift");

        long worldSeed = in.i64();
        int chunkX = in.i32(), chunkZ = in.i32();
        Bounds aggregate = persistedBounds(in);
        in.i32(); in.i32(); in.i32();
        in.i32(); in.i32(); in.i32();
        Rotation.valueOf(in.string());

        int pieceCount = in.count(128, "piece");
        require(pieceCount > 0, "Pillager Outpost persisted authority has no pieces");
        ArrayList<PersistedPieceFact> pieces = new ArrayList<>(pieceCount);
        for (int ordinal = 0; ordinal < pieceCount; ordinal++) {
            require(in.i32() == ordinal, "Pillager Outpost persisted piece order drift");
            int depth = in.i32();
            require(depth >= 0 && depth <= 32, "Pillager Outpost persisted piece depth drift");
            ElementType type = ElementType.valueOf(in.string());
            require(type == ElementType.LEGACY_SINGLE || type == ElementType.LIST,
                    "unsupported persisted Pillager Outpost piece type");
            String elementKey = in.string();
            int componentCount = in.count(8, "component");
            ArrayList<String> components = new ArrayList<>(componentCount);
            for (int i = 0; i < componentCount; i++) components.add(in.string());
            String declaredProcessor = in.string();
            int originX = in.i32(), originY = in.i32(), originZ = in.i32();
            int groundLevelDelta = in.i32();
            Rotation rotation = Rotation.valueOf(in.string());
            Projection projection = Projection.valueOf(in.string());
            Bounds bounds = persistedBounds(in);

            int templateCount = in.count(8, "template");
            ArrayList<PersistedTemplateFact> templates = new ArrayList<>(templateCount);
            for (int i = 0; i < templateCount; i++) {
                templates.add(new PersistedTemplateFact(in.string(), in.string()));
            }
            if (type == ElementType.LEGACY_SINGLE) {
                require(components.isEmpty() && templates.size() == 1
                                && templates.getFirst().templateKey.equals(elementKey),
                        "persisted Pillager Outpost single-element identity drift");
            } else {
                require(!components.isEmpty() && templates.size() == components.size()
                                && templates.stream().map(PersistedTemplateFact::templateKey)
                                        .toList().equals(components),
                        "persisted Pillager Outpost list-element identity drift");
            }

            int junctionCount = in.count(128, "junction");
            ArrayList<PersistedJunctionFact> junctions = new ArrayList<>(junctionCount);
            for (int i = 0; i < junctionCount; i++) {
                junctions.add(new PersistedJunctionFact(in.i32(), in.i32(), in.i32(), in.i32(),
                        Projection.valueOf(in.string())));
            }
            pieces.add(new PersistedPieceFact(type, elementKey, components, declaredProcessor,
                    originX, originY, originZ, groundLevelDelta, rotation, projection, bounds,
                    templates, junctions));
        }

        int edgeCount = in.count(127, "edge");
        for (int i = 0; i < edgeCount; i++) {
            in.i32(); in.i32();
            skipPersistedConnector(in); skipPersistedConnector(in);
            in.string(); in.string();
        }
        int queryCount = in.count(4096, "projection query");
        for (int i = 0; i < queryCount; i++) {
            in.i32(); in.i32(); in.i32(); in.i32(); in.string();
        }
        in.i64(); in.i32();
        int drawCount = in.count(100_000, "planner draw");
        for (int i = 0; i < drawCount; i++) {
            in.i32();
            int operation = in.u8();
            if (operation == 0) in.i64();
            else if (operation == 1) { in.i32(); in.i32(); in.i64(); }
            else throw new IllegalArgumentException(
                    "unknown persisted Pillager Outpost planner RNG operation");
        }
        int continuation = in.count(64, "planner continuation");
        for (int i = 0; i < continuation; i++) in.i64();
        require(continuation == 8, "persisted Pillager Outpost planner continuation drift");
        int predecessorLength = in.i32();
        require(predecessorLength > 0, "persisted Pillager Outpost predecessor length drift");
        in.skip(32 * 3);
        require(in.remaining() == 32, "persisted Pillager Outpost authority trailer drift");
        in.skip(32);
        in.end();
        return new PersistedAuthorityPlan(worldSeed, chunkX, chunkZ, aggregate, pieces);
    }

    private static Bounds persistedBounds(PersistedCursor in) {
        return new Bounds(in.i32(), in.i32(), in.i32(), in.i32(), in.i32(), in.i32());
    }

    private static void skipPersistedConnector(PersistedCursor in) {
        in.i32(); in.i32(); in.i32(); in.i32();
        for (int i = 0; i < 6; i++) in.string();
        in.i32(); in.i32();
    }

    private static PersistedPieceNbt decodePersistedPieceNbt(byte[] bytes) {
        PersistedNbtCursor in = new PersistedNbtCursor(bytes);
        require(in.u8() == 10, "persisted Pillager Outpost piece root must be compound");
        in.utf();
        Integer originX = null, originY = null, originZ = null, groundLevelDelta = null;
        Bounds bounds = null;
        Rotation rotation = null;
        PersistedPoolNbt pool = null;
        List<PersistedJunctionFact> junctions = null;
        String id = null;
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        while (true) {
            int type = in.u8();
            if (type == 0) break;
            require(type <= 12, "unknown persisted Pillager Outpost NBT tag");
            String name = in.utf();
            require(seen.add(name), "duplicate persisted Pillager Outpost NBT field: " + name);
            switch (name) {
                case "BB" -> {
                    require(type == 11, "persisted Pillager Outpost BB type drift");
                    int[] box = in.intArray();
                    require(box.length == 6, "persisted Pillager Outpost BB length drift");
                    bounds = new Bounds(box[0], box[1], box[2], box[3], box[4], box[5]);
                }
                case "PosX" -> {
                    require(type == 3, "persisted Pillager Outpost PosX type drift");
                    originX = in.i32();
                }
                case "PosY" -> {
                    require(type == 3, "persisted Pillager Outpost PosY type drift");
                    originY = in.i32();
                }
                case "PosZ" -> {
                    require(type == 3, "persisted Pillager Outpost PosZ type drift");
                    originZ = in.i32();
                }
                case "ground_level_delta" -> {
                    require(type == 3, "persisted Pillager Outpost ground delta type drift");
                    groundLevelDelta = in.i32();
                }
                case "rotation" -> {
                    require(type == 8, "persisted Pillager Outpost rotation type drift");
                    rotation = Rotation.valueOf(in.utf());
                }
                case "id" -> {
                    require(type == 8, "persisted Pillager Outpost id type drift");
                    id = in.utf();
                }
                case "pool_element" -> {
                    require(type == 10, "persisted Pillager Outpost pool element type drift");
                    pool = readPersistedPoolCompound(in);
                }
                case "junctions" -> {
                    require(type == 9, "persisted Pillager Outpost junction list type drift");
                    junctions = readPersistedJunctions(in);
                }
                default -> in.skipPayload(type, 1);
            }
        }
        in.end();
        require("minecraft:jigsaw".equals(id), "persisted Pillager Outpost piece id drift");
        require(originX != null && originY != null && originZ != null && groundLevelDelta != null
                        && bounds != null && rotation != null && pool != null && junctions != null,
                "persisted Pillager Outpost jigsaw NBT is incomplete");
        return new PersistedPieceNbt(originX, originY, originZ, bounds, pool.type, rotation,
                pool.projection, groundLevelDelta, pool.templates, junctions);
    }

    private static PersistedPoolNbt readPersistedPoolCompound(PersistedNbtCursor in) {
        String elementType = null, projectionName = null, location = null, processor = null;
        List<PersistedPoolNbt> elements = null;
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        while (true) {
            int type = in.u8();
            if (type == 0) break;
            require(type <= 12, "unknown persisted Pillager Outpost pool NBT tag");
            String name = in.utf();
            require(seen.add(name), "duplicate persisted Pillager Outpost pool field: " + name);
            switch (name) {
                case "element_type" -> {
                    require(type == 8, "persisted Pillager Outpost element_type drift");
                    elementType = in.utf();
                }
                case "projection" -> {
                    require(type == 8, "persisted Pillager Outpost projection type drift");
                    projectionName = in.utf();
                }
                case "location" -> {
                    require(type == 8, "persisted Pillager Outpost location type drift");
                    location = in.utf();
                }
                case "processors" -> processor = readPersistedProcessor(in, type);
                case "elements" -> {
                    require(type == 9, "persisted Pillager Outpost list elements type drift");
                    int childType = in.u8();
                    int count = in.i32();
                    require(childType == 10 && count > 0 && count <= 8,
                            "persisted Pillager Outpost list element cardinality drift");
                    ArrayList<PersistedPoolNbt> children = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) children.add(readPersistedPoolCompound(in));
                    elements = List.copyOf(children);
                }
                default -> in.skipPayload(type, 2);
            }
        }
        require(elementType != null && projectionName != null,
                "persisted Pillager Outpost pool element is incomplete");
        Projection projection = persistedProjection(projectionName);
        if ("minecraft:legacy_single_pool_element".equals(elementType)) {
            require(location != null && processor != null && elements == null,
                    "persisted Pillager Outpost single pool element drift");
            return new PersistedPoolNbt(ElementType.LEGACY_SINGLE, projection,
                    List.of(new PersistedTemplateFact(location, processor)));
        }
        if ("minecraft:list_pool_element".equals(elementType)) {
            require(location == null && processor == null && elements != null,
                    "persisted Pillager Outpost list pool element drift");
            ArrayList<PersistedTemplateFact> templates = new ArrayList<>();
            for (PersistedPoolNbt child : elements) {
                require(child.type == ElementType.LEGACY_SINGLE && child.projection == projection
                                && child.templates.size() == 1,
                        "persisted Pillager Outpost list child identity drift");
                templates.add(child.templates.getFirst());
            }
            return new PersistedPoolNbt(ElementType.LIST, projection, templates);
        }
        throw new IllegalArgumentException(
                "unknown persisted Pillager Outpost pool element type: " + elementType);
    }

    private static String readPersistedProcessor(PersistedNbtCursor in, int type) {
        if (type == 8) return in.utf();
        require(type == 10, "persisted Pillager Outpost processor tag type drift");
        boolean processors = false;
        while (true) {
            int child = in.u8();
            if (child == 0) break;
            require(child <= 12, "unknown persisted Pillager Outpost processor NBT tag");
            String name = in.utf();
            if ("processors".equals(name)) {
                require(!processors && child == 9,
                        "persisted Pillager Outpost inline processor list drift");
                processors = true;
                int elementType = in.u8();
                int count = in.i32();
                require(elementType >= 0 && elementType <= 12 && count == 0,
                        "persisted Pillager Outpost inline processor must be empty");
            } else {
                in.skipPayload(child, 3);
            }
        }
        require(processors, "persisted Pillager Outpost inline processor is incomplete");
        return INLINE_PROCESSOR;
    }

    private static List<PersistedJunctionFact> readPersistedJunctions(PersistedNbtCursor in) {
        int elementType = in.u8();
        int count = in.i32();
        require((count == 0 && elementType == 0) || (count > 0 && elementType == 10),
                "persisted Pillager Outpost junction list element type drift");
        require(count >= 0 && count <= 128, "persisted Pillager Outpost junction count drift");
        ArrayList<PersistedJunctionFact> junctions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Integer sourceX = null, sourceGroundY = null, sourceZ = null, deltaY = null;
            Projection destination = null;
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            while (true) {
                int type = in.u8();
                if (type == 0) break;
                require(type <= 12, "unknown persisted Pillager Outpost junction NBT tag");
                String name = in.utf();
                require(seen.add(name), "duplicate persisted Pillager Outpost junction field");
                switch (name) {
                    case "source_x" -> {
                        require(type == 3, "persisted Pillager Outpost source_x type drift");
                        sourceX = in.i32();
                    }
                    case "source_ground_y" -> {
                        require(type == 3, "persisted Pillager Outpost source_ground_y type drift");
                        sourceGroundY = in.i32();
                    }
                    case "source_z" -> {
                        require(type == 3, "persisted Pillager Outpost source_z type drift");
                        sourceZ = in.i32();
                    }
                    case "delta_y" -> {
                        require(type == 3, "persisted Pillager Outpost delta_y type drift");
                        deltaY = in.i32();
                    }
                    case "dest_proj" -> {
                        require(type == 8, "persisted Pillager Outpost dest_proj type drift");
                        destination = persistedProjection(in.utf());
                    }
                    default -> in.skipPayload(type, 3);
                }
            }
            require(sourceX != null && sourceGroundY != null && sourceZ != null
                            && deltaY != null && destination != null,
                    "persisted Pillager Outpost junction is incomplete");
            junctions.add(new PersistedJunctionFact(
                    sourceX, sourceGroundY, sourceZ, deltaY, destination));
        }
        return List.copyOf(junctions);
    }

    private static Projection persistedProjection(String value) {
        return switch (value) {
            case "rigid" -> Projection.RIGID;
            case "terrain_matching" -> Projection.TERRAIN_MATCHING;
            default -> throw new IllegalArgumentException(
                    "unknown persisted Pillager Outpost projection: " + value);
        };
    }

    private static void preflightCapabilities(WorldTransaction transaction) {
        if (!transaction.supportsAtomicForkPublishWithRandom()) {
            throw new UnsupportedOperationException(
                    "atomic Pillager Outpost world/sidecar/RNG publish required");
        }
        if (!transaction.supportsHeightmap(HEIGHTMAP)
                || !transaction.supportsFluidStateQueries()
                || !transaction.supportsBlockStateQueries()) {
            throw new UnsupportedOperationException("Pillager Outpost query closure unavailable");
        }
        if (!transaction.supportsWriteFlags(TEMPLATE_WRITE_FLAGS)
                || !transaction.supportsWriteFlags(BLOCK_ENTITY_CLEAR_FLAGS)
                || !transaction.supportsSetBlockAndUpdate()) {
            throw new UnsupportedOperationException("Pillager Outpost mutation closure unavailable");
        }
        if (!transaction.supportsBentPayloads() || !transaction.supportsLootPayloads()
                || !transaction.supportsStructureEntityPayloads()) {
            throw new UnsupportedOperationException("Pillager Outpost typed sidecars unavailable");
        }
        if (!transaction.supportsFluidTick(WATER_FLUID, WATER_TICK_DELAY)) {
            throw new UnsupportedOperationException("Pillager Outpost Water delay-5 tick unavailable");
        }
        if (!transaction.supportsEmptyUnauthenticatedLanes()) {
            throw new UnsupportedOperationException(
                    "Pillager Outpost unauthenticated sidecar lanes must stay empty");
        }
    }

    private static void authenticateStart(Mc263PillagerOutpostProducer.Start start,
            StartAuthority authority) {
        Objects.requireNonNull(authority, "Pillager Outpost start authority");
        ExecutionPlan plan = start.executionPlan();
        require(plan.present(), "Pillager Outpost accepted plan absent");
        require(Mc263PillagerOutpostProducer.STRUCTURE_KEY.equals(plan.structureKey()),
                "Pillager Outpost plan key drift");
        require(plan.chunkX() == start.chunkX() && plan.chunkZ() == start.chunkZ(),
                "Pillager Outpost plan chunk drift");
        require(!plan.pieces().isEmpty(), "Pillager Outpost accepted plan has no pieces");
        for (int ordinal = 0; ordinal < plan.pieces().size(); ordinal++) {
            PiecePlacement piece = plan.pieces().get(ordinal);
            require(piece.type() == ElementType.LEGACY_SINGLE || piece.type() == ElementType.LIST,
                    "unsupported Pillager Outpost piece type at " + ordinal);
            require(piece.type() != ElementType.LIST || !piece.components().isEmpty(),
                    "empty Pillager Outpost list element at " + ordinal);
        }
        for (int ordinal = 0; ordinal < start.projectionQueries().size(); ordinal++) {
            Mc263PillagerOutpostProducer.ProjectionQuery query = start.projectionQueries().get(ordinal);
            require(query.ordinal() == ordinal && HEIGHTMAP.equals(query.heightmap()),
                    "Pillager Outpost projection query drift at " + ordinal);
        }
        PlannerRngReceipt rng = start.generationRng();
        require(rng.state48() >= 0 && rng.state48() <= LEGACY_MASK
                        && rng.worldgenCount() >= 0 && rng.continuationNextLongI64().size() == 8,
                "Pillager Outpost generation RNG receipt drift");
        require(Mc263PillagerOutpostProducer.CARRIER_FORMAT.equals(start.carrier().format()),
                "Pillager Outpost carrier format drift");
        require(start.carrier().pieces().size() == plan.pieces().size(),
                "Pillager Outpost persisted piece cardinality drift");
        Mc263PillagerOutpostPersistedAuthority.authenticate(start);
        StartAuthority persisted = authorityFromPersisted(
                start.carrier().pieces().getFirst().bytes(),
                start.carrier().structureStart().bytes());
        require(persisted.equals(authority),
                "Pillager Outpost persisted settlement authority drift");
        require(startIdentitySha256(start).equals(authority.startIdentitySha256()),
                "Pillager Outpost accepted start identity drift");
        Mc263PillagerOutpostProducer.BinaryNbt predecessor = start.carrier().structureStart();
        require(predecessor.length() == authority.predecessorLength()
                        && predecessor.sha256().equals(authority.predecessorSha256()),
                "Pillager Outpost predecessor authority drift");
        validateReferenceTransition(predecessor, authority);
    }

    private static PreparedTemplate prepareTemplate(Mc263PillagerOutpostGrammar.Evidence evidence,
            String templateKey, Rotation rotation, String processor,
            Set<String> exactStates, Set<BlockEntitySpec> blockEntities, Set<String> entityTypes) {
        Mc263PillagerOutpostGrammar.Template template = evidence.requireTemplate(templateKey);
        if (!INLINE_PROCESSOR.equals(processor) && !ROT_PROCESSOR.equals(processor)) {
            throw new IllegalArgumentException("unknown Pillager Outpost processor: " + processor);
        }
        ArrayList<PreparedCell> cells = new ArrayList<>();
        ArrayList<PreparedCell> markers = new ArrayList<>();
        for (Mc263PillagerOutpostGrammar.Command command : template.commands()) {
            int count = command instanceof Mc263PillagerOutpostGrammar.Run run ? run.count() : 1;
            for (int occurrence = 0; occurrence < count; occurrence++) {
                Mc263PillagerOutpostGrammar.Vec3i local = commandPosition(template, command, occurrence);
                String source = command instanceof Mc263PillagerOutpostGrammar.Jigsaw jigsaw
                        ? jigsaw.finalState() : state(template, command.state());
                if (!(command instanceof Mc263PillagerOutpostGrammar.Jigsaw)
                        && ("minecraft:air".equals(blockKey(source))
                                || "minecraft:structure_block".equals(blockKey(source)))) {
                    continue;
                }
                if ("minecraft:structure_void".equals(blockKey(source))) continue;
                String rotated = rotateState(source, rotation);
                exactStates.add(rotated);
                String wet = authenticatedWetSuccessor(rotated);
                if (wet != null) exactStates.add(wet);
                BlockEntitySpec blockEntity = blockEntitySpec(command, rotated);
                if (blockEntity != null) blockEntities.add(blockEntity);
                PreparedCell prepared = new PreparedCell(local.x(), local.y(), local.z(), rotated, wet,
                        blockEntity);
                if (command instanceof Mc263PillagerOutpostGrammar.Data) markers.add(prepared);
                else cells.add(prepared);
            }
        }
        ArrayList<PreparedEntity> entities = new ArrayList<>();
        for (Mc263PillagerOutpostGrammar.EntitySidecar entity : template.entities()) {
            String id = entity.entityType().id();
            if (!id.equals("minecraft:allay") && !id.equals("minecraft:iron_golem")) {
                throw new IllegalArgumentException("unknown Pillager Outpost entity: " + id);
            }
            entityTypes.add(id);
            entities.add(new PreparedEntity(id, entity.position().x(), entity.position().y(),
                    entity.position().z(), entity.blockPosition().x(), entity.blockPosition().y(),
                    entity.blockPosition().z()));
        }
        return new PreparedTemplate(templateKey, processor, List.copyOf(cells),
                List.copyOf(markers), List.copyOf(entities));
    }

    private static Mc263PillagerOutpostGrammar.Vec3i commandPosition(
            Mc263PillagerOutpostGrammar.Template template,
            Mc263PillagerOutpostGrammar.Command command, int occurrence) {
        if (command instanceof Mc263PillagerOutpostGrammar.Run run) {
            return new Mc263PillagerOutpostGrammar.Vec3i(
                    Math.addExact(run.start().x(), Math.multiplyExact(run.delta().x(), occurrence)),
                    Math.addExact(run.start().y(), Math.multiplyExact(run.delta().y(), occurrence)),
                    Math.addExact(run.start().z(), Math.multiplyExact(run.delta().z(), occurrence)));
        }
        if (command instanceof Mc263PillagerOutpostGrammar.Jigsaw jigsaw) {
            if (jigsaw.connectorOrdinal() < 0 || jigsaw.connectorOrdinal() >= template.connectors().size()) {
                throw new IllegalArgumentException("Pillager Outpost jigsaw connector ordinal drift");
            }
            return template.connectors().get(jigsaw.connectorOrdinal()).position();
        }
        if (command instanceof Mc263PillagerOutpostGrammar.Data data) return data.position();
        if (command instanceof Mc263PillagerOutpostGrammar.LootContainer loot) return loot.position();
        throw new IllegalArgumentException("unknown Pillager Outpost command");
    }

    private static String state(Mc263PillagerOutpostGrammar.Template template, int index) {
        if (index < 0 || index >= template.stateTable().size()) {
            throw new IllegalArgumentException("Pillager Outpost state-table index drift");
        }
        return template.stateTable().get(index);
    }

    private static BlockEntitySpec blockEntitySpec(Mc263PillagerOutpostGrammar.Command command,
            String exactState) {
        if (command instanceof Mc263PillagerOutpostGrammar.Data data) {
            require(data.semantic() == Mc263PillagerOutpostGrammar.DataSemantic.OMINOUS_BANNER,
                    "unknown Pillager Outpost DATA semantic");
            require("minecraft:banner".equals(data.blockEntityType())
                            && "minecraft:white_wall_banner".equals(blockKey(exactState)),
                    "Pillager Outpost ominous-banner block/entity drift");
            return new BlockEntitySpec("minecraft:white_wall_banner", "minecraft:banner", null,
                    BlockEntityKind.OMINOUS_BANNER);
        }
        if (command instanceof Mc263PillagerOutpostGrammar.LootContainer loot) {
            require(OUTPOST_LOOT.equals(loot.lootTable())
                            && "minecraft:chest".equals(loot.blockEntityType())
                            && "minecraft:chest".equals(blockKey(exactState)),
                    "Pillager Outpost loot-container drift");
            return new BlockEntitySpec("minecraft:chest", "minecraft:chest", OUTPOST_LOOT,
                    BlockEntityKind.LOOT_CONTAINER);
        }
        return null;
    }

    private static String processorForTemplate(Mc263PillagerOutpostGrammar.Evidence evidence,
            String templateKey) {
        String found = null;
        for (Mc263PillagerOutpostGrammar.Pool pool : evidence.poolsInExecutionOrder()) {
            for (Mc263PillagerOutpostGrammar.PoolElement element : pool.elementsInDeclaredOrder()) {
                if (element instanceof Mc263PillagerOutpostGrammar.SingleElement single
                        && single.template().equals(templateKey)) {
                    found = uniqueProcessor(found, single.processorList(), templateKey);
                } else if (element instanceof Mc263PillagerOutpostGrammar.ListElement list) {
                    for (Mc263PillagerOutpostGrammar.SingleChild child : list.childrenInDeclaredOrder()) {
                        if (child.template().equals(templateKey)) {
                            found = uniqueProcessor(found, child.processorList(), templateKey);
                        }
                    }
                }
            }
        }
        if (found == null) {
            throw new IllegalArgumentException("Pillager Outpost template absent from pools: " + templateKey);
        }
        return found;
    }

    private static String uniqueProcessor(String previous, String current, String template) {
        if (!INLINE_PROCESSOR.equals(current) && !ROT_PROCESSOR.equals(current)) {
            throw new IllegalArgumentException("unknown Pillager Outpost processor for " + template);
        }
        if (previous != null && !previous.equals(current)) {
            throw new IllegalArgumentException("ambiguous Pillager Outpost processor for " + template);
        }
        return current;
    }

    private static void validateProcessorClosure(Mc263PillagerOutpostGrammar.Evidence evidence) {
        require(evidence.processorListsInEncounterOrder().size() == 2,
                "Pillager Outpost processor-list closure drift");
        Mc263PillagerOutpostGrammar.ProcessorList inline =
                evidence.processorListsInEncounterOrder().get(0);
        require(INLINE_PROCESSOR.equals(inline.identity()) && !inline.registered()
                        && inline.orderedCodec().isEmpty(),
                "Pillager Outpost inline processor closure drift");
        Mc263PillagerOutpostGrammar.ProcessorList rot =
                evidence.processorListsInEncounterOrder().get(1);
        require(ROT_PROCESSOR.equals(rot.identity()) && rot.registered()
                        && rot.orderedCodec().size() == 1
                        && rot.orderedCodec().get(0) instanceof Mc263PillagerOutpostGrammar.BlockRotConfig config
                        && Double.compare(config.integrity(), ROT_INTEGRITY) == 0,
                "Pillager Outpost outpost_rot codec drift");
        boolean jigsaw = false, ignore = false, blockRot = false, gravity = false;
        for (Mc263PillagerOutpostGrammar.ProcessorSemantic semantic
                : evidence.processorSemanticsInEncounterOrder()) {
            if (semantic instanceof Mc263PillagerOutpostGrammar.JigsawReplacementSemantic) jigsaw = true;
            else if (semantic instanceof Mc263PillagerOutpostGrammar.BlockIgnoreSemantic value) {
                require(value.blocks().equals(List.of("minecraft:air", "minecraft:structure_block")),
                        "Pillager Outpost ignore-block closure drift");
                ignore = true;
            } else if (semantic instanceof Mc263PillagerOutpostGrammar.BlockRotSemantic value) {
                require(Double.compare(value.integrity(), ROT_INTEGRITY) == 0,
                        "Pillager Outpost block-rot integrity drift");
                Mc263PillagerOutpostGrammar.BlockRotRng rng = value.rng();
                require(rng.xMultiplierI32() == 3_129_871
                                && rng.zMultiplierI64() == 116_129_781L
                                && rng.squareMultiplierI64() == 42_317_861L
                                && rng.linearMultiplierI64() == 11L
                                && rng.arithmeticRightShift() == 16,
                        "Pillager Outpost block-rot RNG contract drift");
                blockRot = true;
            } else if (semantic instanceof Mc263PillagerOutpostGrammar.GravitySemantic value) {
                require(value.offset() == -1, "Pillager Outpost gravity offset drift");
                gravity = true;
            } else {
                throw new IllegalArgumentException("unknown Pillager Outpost processor semantic");
            }
        }
        require(jigsaw && ignore && blockRot && gravity,
                "Pillager Outpost processor semantic closure incomplete");
    }

    private static void validateReleasedExactStateClosure() {
        for (String state : E3L_RELEASED_EXACT_STATES) {
            require(Mc263FeatureBlockState.supportsExactState(state),
                    "Pillager Outpost E3L released exact-state drift: " + state);
        }
    }

    private static void validateClip(Mc263PillagerOutpostProducer.Start start, Clip clip) {
        require(clip.minX == Math.multiplyExact(clip.chunkX, 16)
                        && clip.maxX == Math.addExact(clip.minX, 15)
                        && clip.minZ == Math.multiplyExact(clip.chunkZ, 16)
                        && clip.maxZ == Math.addExact(clip.minZ, 15),
                "Pillager Outpost clip must be one exact destination chunk");
        require(clip.minY == CLIP_MIN_Y && clip.maxY == CLIP_MAX_Y,
                "Pillager Outpost destination clip Y boundary drift");
        Mc263PillagerOutpostProducer.Box box = start.aggregateBoundingBox();
        int minChunkX = Math.floorDiv(box.minX(), 16);
        int maxChunkX = Math.floorDiv(box.maxX(), 16);
        int minChunkZ = Math.floorDiv(box.minZ(), 16);
        int maxChunkZ = Math.floorDiv(box.maxZ(), 16);
        require(clip.chunkX >= minChunkX && clip.chunkX <= maxChunkX
                        && clip.chunkZ >= minChunkZ && clip.chunkZ <= maxChunkZ,
                "Pillager Outpost clip does not intersect accepted aggregate bounds");
        int expectedOrdinal = (clip.chunkZ - minChunkZ) * (maxChunkX - minChunkX + 1)
                + clip.chunkX - minChunkX;
        require(clip.ordinal == expectedOrdinal,
                "Pillager Outpost destination clip order drift");
    }

    private static void validateClip(Bounds box, Clip clip) {
        require(clip.minX == Math.multiplyExact(clip.chunkX, 16)
                        && clip.maxX == Math.addExact(clip.minX, 15)
                        && clip.minZ == Math.multiplyExact(clip.chunkZ, 16)
                        && clip.maxZ == Math.addExact(clip.minZ, 15),
                "Pillager Outpost clip must be one exact destination chunk");
        require(clip.minY == CLIP_MIN_Y && clip.maxY == CLIP_MAX_Y,
                "Pillager Outpost destination clip Y boundary drift");
        int minChunkX = Math.floorDiv(box.minX(), 16);
        int maxChunkX = Math.floorDiv(box.maxX(), 16);
        int minChunkZ = Math.floorDiv(box.minZ(), 16);
        int maxChunkZ = Math.floorDiv(box.maxZ(), 16);
        require(clip.chunkX >= minChunkX && clip.chunkX <= maxChunkX
                        && clip.chunkZ >= minChunkZ && clip.chunkZ <= maxChunkZ,
                "Pillager Outpost clip does not intersect persisted aggregate bounds");
        int expectedOrdinal = (clip.chunkZ - minChunkZ) * (maxChunkX - minChunkX + 1)
                + clip.chunkX - minChunkX;
        require(clip.ordinal == expectedOrdinal,
                "Pillager Outpost destination clip order drift");
    }

    private static void placePiece(PreparedPiece piece, Plan plan, Clip clip, Trace trace,
            SettlementRandom random, Map<Vec, String> finalStates,
            Map<Vec, LoadedBlockEntity> finalBlockEntities, int[] blockEntityEncounter,
            List<EntityPayload> entities, Set<EntityIdentity> entityIdentities, int[] entityEncounter) {
        for (PreparedTemplate template : piece.templates) {
            ArrayList<Vec> pendingLiquids = new ArrayList<>();
            ArrayList<LoadedBlockEntity> placedEntities = new ArrayList<>();
            for (PreparedCell cell : template.cells) {
                Vec transformed = transformBlock(cell.localX, cell.localY, cell.localZ,
                        piece.rotation, piece.originX, piece.originY, piece.originZ);
                if (ROT_PROCESSOR.equals(template.processor) && !keepByBlockRot(transformed)) continue;
                // StructureTemplate.placeInWorld drops a cell whose transformed column lies outside
                // the destination clip before the projection processor chain runs, so the
                // GravityProcessor height query of a terrain_matching element is never issued for a
                // column outside the executing source chunk. Feature plates are 16x4x32, so an
                // out-of-clip column can lie two chunks beyond the executing source chunk, which no
                // FEATURES WorldGenRegion can read.
                if (!clip.containsColumn(transformed.x, transformed.z)) continue;
                Vec target = transformed;
                if (piece.projection == Projection.TERRAIN_MATCHING) {
                    int height = trace.height(HEIGHTMAP, transformed.x, transformed.z);
                    target = new Vec(transformed.x, Math.addExact(Math.addExact(height, -1), cell.localY),
                            transformed.z);
                }
                if (!clip.contains(target)) continue;
                placeCell(cell, target, plan, trace, random, finalStates, finalBlockEntities,
                        pendingLiquids, placedEntities, blockEntityEncounter);
            }
            restorePendingLiquids(pendingLiquids, plan, trace, finalStates, finalBlockEntities);
            for (LoadedBlockEntity loaded : placedEntities) {
                BlockEntityAccess handle = requireBlockEntity(trace.blockEntity(loaded.position),
                        loaded.spec, loaded.position);
                handle.setChanged();
                trace.changed(loaded.position, loaded.spec.entityType);
            }
            placeEntities(piece, template, clip, trace, random, entities, entityIdentities, entityEncounter);
            placeMarkers(piece, template, plan, clip, trace, random, finalStates,
                    finalBlockEntities, blockEntityEncounter);
        }
    }

    private static void placeMarkers(PreparedPiece piece, PreparedTemplate template, Plan plan,
            Clip clip, Trace trace, SettlementRandom random, Map<Vec, String> finalStates,
            Map<Vec, LoadedBlockEntity> finalBlockEntities, int[] blockEntityEncounter) {
        ArrayList<Vec> pendingLiquids = new ArrayList<>();
        ArrayList<LoadedBlockEntity> placedEntities = new ArrayList<>();
        for (PreparedCell marker : template.markers) {
            Vec transformed = transformBlock(marker.localX, marker.localY, marker.localZ,
                    piece.rotation, piece.originX, piece.originY, piece.originZ);
            if (!clip.containsColumn(transformed.x, transformed.z)) continue;
            Vec target = transformed;
            if (piece.projection == Projection.TERRAIN_MATCHING) {
                int height = trace.height(HEIGHTMAP, transformed.x, transformed.z);
                target = new Vec(transformed.x,
                        Math.addExact(Math.addExact(height, -1), marker.localY), transformed.z);
            }
            if (!clip.contains(target)) continue;
            placeCell(marker, target, plan, trace, random, finalStates, finalBlockEntities,
                    pendingLiquids, placedEntities, blockEntityEncounter);
        }
        restorePendingLiquids(pendingLiquids, plan, trace, finalStates, finalBlockEntities);
        for (LoadedBlockEntity loaded : placedEntities) {
            BlockEntityAccess handle = requireBlockEntity(trace.blockEntity(loaded.position),
                    loaded.spec, loaded.position);
            handle.setChanged();
            trace.changed(loaded.position, loaded.spec.entityType);
        }
    }

    private static void placeCell(PreparedCell cell, Vec target, Plan plan, Trace trace,
            SettlementRandom random, Map<Vec, String> finalStates,
            Map<Vec, LoadedBlockEntity> finalBlockEntities, List<Vec> pendingLiquids,
            List<LoadedBlockEntity> placedEntities, int[] blockEntityEncounter) {
        FluidState retained = trace.fluid(target);
        if (cell.blockEntity != null) {
            boolean cleared = trace.setBlock(target, exact(BARRIER_STATE), BLOCK_ENTITY_CLEAR_FLAGS);
            if (cleared) {
                finalStates.put(target, exact(BARRIER_STATE));
                finalBlockEntities.remove(target);
            }
        }
        boolean placed = trace.setBlock(target, cell.state, TEMPLATE_WRITE_FLAGS);
        if (!placed) return;
        finalStates.put(target, cell.state);
        if (cell.blockEntity == null) finalBlockEntities.remove(target);

        if (cell.blockEntity != null) {
            BlockEntityAccess handle = requireBlockEntity(trace.blockEntity(target),
                    cell.blockEntity, target);
            long lootSeed = 0L;
            if (cell.blockEntity.kind == BlockEntityKind.OMINOUS_BANNER) {
                handle.loadOminousBanner();
            } else {
                lootSeed = random.nextLong();
                trace.randomNextLong(target, lootSeed);
                handle.setLootTable(OUTPOST_LOOT, lootSeed);
            }
            BlockEntitySnapshot snapshot = Objects.requireNonNull(handle.snapshot(),
                    "Pillager Outpost block entity snapshot");
            validateBlockEntitySnapshot(snapshot, cell.blockEntity, target, lootSeed);
            LoadedBlockEntity loaded = new LoadedBlockEntity(target, cell.state, cell.blockEntity,
                    lootSeed, snapshot.canonicalNbt(), blockEntityEncounter[0]++);
            finalBlockEntities.put(target, loaded);
            placedEntities.add(loaded);
        }

        if (!retained.empty()) {
            if (retained.source()) {
                if (WATER_FLUID.equals(retained.fluidKey()) && hasDryWaterloggedProperty(cell.state)) {
                    require(cell.wetState != null && plan.exactStates.contains(cell.wetState),
                            "unauthenticated Pillager Outpost wet successor");
                    placeWater(target, cell.wetState, trace, finalStates, finalBlockEntities);
                }
            } else {
                pendingLiquids.add(target);
            }
        }
    }

    private static void restorePendingLiquids(List<Vec> pending, Plan plan, Trace trace,
            Map<Vec, String> finalStates, Map<Vec, LoadedBlockEntity> finalBlockEntities) {
        boolean changed = true;
        while (changed && !pending.isEmpty()) {
            changed = false;
            Iterator<Vec> iterator = pending.iterator();
            while (iterator.hasNext()) {
                Vec position = iterator.next();
                FluidState best = trace.fluid(position);
                for (Direction direction : LIQUID_NEIGHBORS) {
                    if (best.source()) break;
                    FluidState candidate = trace.fluid(offset(position, direction));
                    if (candidate.height() > best.height()) best = candidate;
                }
                if (!best.source()) continue;
                String current = exact(trace.blockState(position));
                require(trace.supportsExactState(current),
                        "unsupported live Pillager Outpost exact state: " + current);
                if (!WATER_FLUID.equals(best.fluidKey()) || !hasDryWaterloggedProperty(current)) continue;
                String wet = authenticatedWetSuccessor(current);
                require(wet != null && plan.exactStates.contains(wet),
                        "unauthenticated Pillager Outpost pending wet successor");
                placeWater(position, wet, trace, finalStates, finalBlockEntities);
                iterator.remove();
                changed = true;
            }
        }
    }

    private static void placeWater(Vec position, String wetState, Trace trace,
            Map<Vec, String> finalStates, Map<Vec, LoadedBlockEntity> finalBlockEntities) {
        if (trace.setBlockAndUpdate(position, wetState)) {
            finalStates.put(position, wetState);
            LoadedBlockEntity loaded = finalBlockEntities.get(position);
            if (loaded != null && !loaded.spec.blockIdentity.equals(blockKey(wetState))) {
                finalBlockEntities.remove(position);
            }
        }
        trace.fluidTick(position, WATER_FLUID, WATER_TICK_DELAY);
    }

    private static void placeEntities(PreparedPiece piece, PreparedTemplate template, Clip clip,
            Trace trace, SettlementRandom random, List<EntityPayload> result,
            Set<EntityIdentity> identities, int[] encounter) {
        for (PreparedEntity entity : template.entities) {
                DoubleVec transformed = transformEntity(entity.x, entity.y, entity.z,
                        piece.rotation, piece.originX, piece.originY, piece.originZ);
                Vec transformedBlock = transformBlock(entity.blockX, entity.blockY, entity.blockZ,
                        piece.rotation, piece.originX, piece.originY, piece.originZ);
                if (!clip.contains(transformedBlock)) continue;
                EntityRequest request = new EntityRequest(entity.entityKey, transformed,
                        transformedBlock, piece.rotation);
                SettlementRandom expectedAfter = random.copy();
                expectedAfter.nextFloat();
                EntityPayload payload = Objects.requireNonNull(trace.entity(request, random),
                        "Pillager Outpost structure entity result");
                require(random.sameState(expectedAfter),
                        "Pillager Outpost structure entity RNG consumption drift");
                validateEntity(payload, request, encounter[0]);
                EntityIdentity identity = new EntityIdentity(payload.entityKey(),
                        payload.position().rawBitsU64(), payload.canonicalPayloadSha256());
                if (!identities.add(identity)) {
                    throw new IllegalStateException("duplicate Pillager Outpost structure entity");
                }
            result.add(payload.withEncounterOrdinal(encounter[0]++));
        }
    }

    private static void validateEntity(EntityPayload value, EntityRequest request, int ordinal) {
        String expectedKey = request.entityKey();
        require(value.encounterOrdinal() == ordinal || value.encounterOrdinal() == -1,
                "Pillager Outpost entity encounter drift");
        require(expectedKey.equals(value.entityKey())
                        && "minecraft:structure".equals(value.spawnReason())
                        && "STRUCTURE".equals(value.runtimeSpawnReason()),
                "Pillager Outpost entity identity/spawn drift");
        require(value.position().size() == 3 && value.motion().size() == 3
                        && value.rotation().size() == 2,
                "Pillager Outpost entity raw-vector cardinality drift");
        value.position().validate(); value.motion().validate(); value.rotation().validate();
        long[] positionBits = value.position().rawBitsU64();
        require(positionBits[0] == Double.doubleToRawLongBits(request.templatePosition().x())
                        && positionBits[1] == Double.doubleToRawLongBits(request.templatePosition().y())
                        && positionBits[2] == Double.doubleToRawLongBits(request.templatePosition().z()),
                "Pillager Outpost entity transformed position drift");
        byte[] canonical = value.canonicalPayload();
        require(value.runtimeUuidExcluded() && canonical.length > 0
                        && sha256(canonical).equals(value.canonicalPayloadSha256())
                        && containsLongSequence(canonical, positionBits)
                        && containsLongSequence(canonical, value.motion().rawBitsU64())
                        && containsIntSequence(canonical, value.rotation().rawBitsU32()),
                "Pillager Outpost entity canonical-payload/vector drift");
        if ("minecraft:allay".equals(expectedKey)) {
            require(value.canonicalPayload().length == 729
                            && value.canonicalAttributeIds().equals(ALLAY_ATTRIBUTES),
                    "Pillager Outpost allay complete ENTS drift");
        } else if ("minecraft:iron_golem".equals(expectedKey)) {
            require(value.canonicalPayload().length == 903
                            && value.canonicalAttributeIds().equals(GOLEM_ATTRIBUTES),
                    "Pillager Outpost iron-golem complete ENTS drift");
        } else {
            throw new IllegalArgumentException("unknown Pillager Outpost entity key");
        }
    }

    /**
     * Holds a placed block entity's snapshot to the canonical save-with-full-metadata payload.
     *
     * <p>The payload a chunk publishes is binary NBT, never a descriptive rendering: it must be a
     * compound root ({@code 0x0a}) with an END-terminated body, and it must equal byte for byte the
     * program {@link Mc263StructureBlockEntityNbtAuthority} renders for this position, semantic and
     * loot seed. A length check alone accepted a debug string and let it reach
     * {@code Mc263FinalChunkSidecars}, which rejects any non-compound payload.</p>
     */
    private static void validateBlockEntitySnapshot(BlockEntitySnapshot snapshot,
            BlockEntitySpec expected, Vec position, long lootSeed) {
        byte[] canonicalNbt = snapshot.canonicalNbt();
        require(snapshot.position().equals(position)
                        && snapshot.blockIdentity().equals(expected.blockIdentity)
                        && snapshot.entityType().equals(expected.entityType)
                        && canonicalNbt.length >= 3
                        && canonicalNbt[0] == (byte) 10
                        && canonicalNbt[1] == 0 && canonicalNbt[2] == 0
                        && canonicalNbt[canonicalNbt.length - 1] == 0
                        && sha256(canonicalNbt).equals(snapshot.canonicalNbtSha256()),
                "Pillager Outpost BENT snapshot drift at " + position);
        Mc263StructureBlockEntityNbtAuthority.Facts facts;
        if (expected.kind == BlockEntityKind.OMINOUS_BANNER) {
            require(snapshot.semantic().equals("minecraft:ominous_banner")
                            && snapshot.lootTable().isEmpty() && snapshot.lootSeed() == 0L,
                    "Pillager Outpost ominous-banner payload drift");
            facts = new Mc263StructureBlockEntityNbtAuthority.OminousBanner(expected.entityType);
        } else {
            require(snapshot.semantic().equals("minecraft:loot_container")
                            && snapshot.lootTable().equals(OUTPOST_LOOT)
                            && snapshot.lootSeed() == lootSeed,
                    "Pillager Outpost loot-container payload drift");
            facts = new Mc263StructureBlockEntityNbtAuthority.LootContainer(
                    expected.entityType, OUTPOST_LOOT, lootSeed);
        }
        require(Arrays.equals(canonicalNbt, Mc263StructureBlockEntityNbtAuthority.render(
                        position.x(), position.y(), position.z(), facts)),
                "Pillager Outpost BENT canonical NBT authority drift at " + position);
    }

    private static BlockEntityAccess requireBlockEntity(BlockEntityAccess handle,
            BlockEntitySpec expected, Vec position) {
        if (handle == null || !handle.blockIdentity().equals(expected.blockIdentity)
                || !handle.entityType().equals(expected.entityType)) {
            throw new IllegalStateException("Pillager Outpost block-entity lifecycle mismatch at "
                    + position);
        }
        return handle;
    }

    private static Settlement settlement(Clip clip, List<Operation> operations,
            Map<Vec, String> finalStates, Map<Vec, LoadedBlockEntity> finalBlockEntities,
            List<EntityPayload> entities, PersistedStart successor, SettlementRandom candidate,
            String fingerprint) {
        ArrayList<Map.Entry<Vec, String>> states = new ArrayList<>(finalStates.entrySet());
        states.sort(Map.Entry.comparingByKey(POSITION_ORDER));
        ArrayList<FinalState> frozenStates = new ArrayList<>();
        for (Map.Entry<Vec, String> state : states) {
            frozenStates.add(new FinalState(state.getKey(), state.getValue()));
        }
        ArrayList<LoadedBlockEntity> loaded = new ArrayList<>(finalBlockEntities.values());
        loaded.sort(Comparator.comparingInt(value -> value.encounterOrder));
        ArrayList<BentPayload> bent = new ArrayList<>();
        ArrayList<LootPayload> loot = new ArrayList<>();
        for (LoadedBlockEntity value : loaded) {
            String state = finalStates.get(value.position);
            require(state != null && blockKey(state).equals(value.spec.blockIdentity),
                    "Pillager Outpost final BENT/state mismatch");
            int bentOrdinal = bent.size();
            bent.add(new BentPayload(bentOrdinal, value.position, state,
                    value.spec.blockIdentity, value.spec.entityType, value.canonicalNbt,
                    value.encounterOrder));
            if (value.spec.lootTable != null) {
                loot.add(new LootPayload(loot.size(), value.position, value.spec.lootTable,
                        value.lootSeed, bentOrdinal));
            }
        }
        return new Settlement(clip, operations, frozenStates, bent, loot, entities, successor,
                candidate.count(), candidate.continuationNextLongI64(), fingerprint);
    }

    private static String publicationFingerprint(Mc263PillagerOutpostProducer.Start start, Clip clip,
            long callerLo, long callerHi, int callerCount) {
        StringBuilder value = new StringBuilder(Mc263PillagerOutpostProducer.STRUCTURE_KEY)
                .append('|').append(start.worldSeed()).append('|').append(start.chunkX()).append('|')
                .append(start.chunkZ()).append('|').append(clip.chunkX()).append('|').append(clip.chunkZ())
                .append('|').append(clip.ordinal()).append('|').append(start.carrier().structureStart().sha256())
                .append('|').append(Long.toUnsignedString(callerLo)).append('|')
                .append(Long.toUnsignedString(callerHi)).append('|').append(callerCount);
        for (String digest : FINGERPRINT_SALT) value.append('|').append(digest);
        return sha256(value.toString().getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] canonicalPayload(Settlement value) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            writeString(out, value.fingerprint);
            writeClip(out, value.clip);
            out.writeInt(value.operations.size());
            for (Operation operation : value.operations) {
                out.writeInt(operation.ordinal()); out.writeInt(operation.kind().ordinal());
                writeVec(out, operation.position()); writeString(out, operation.detail());
            }
            out.writeInt(value.finalStates.size());
            for (FinalState state : value.finalStates) {
                writeVec(out, state.position()); writeString(out, state.exactState());
            }
            out.writeInt(value.bent.size());
            for (BentPayload bent : value.bent) {
                out.writeInt(bent.ordinal()); writeVec(out, bent.position());
                writeString(out, bent.exactState()); writeString(out, bent.blockIdentity());
                writeString(out, bent.entityType()); writeBytes(out, bent.canonicalNbt());
                out.writeInt(bent.loadEncounterOrder());
            }
            out.writeInt(value.loot.size());
            for (LootPayload loot : value.loot) {
                out.writeInt(loot.ordinal()); writeVec(out, loot.position()); writeString(out, loot.table());
                out.writeLong(loot.signedCallerSeed()); out.writeInt(loot.bentOrdinal());
            }
            out.writeInt(value.entities.size());
            for (EntityPayload entity : value.entities) {
                out.writeInt(entity.encounterOrdinal()); writeString(out, entity.entityKey());
                writeString(out, entity.spawnReason()); writeString(out, entity.runtimeSpawnReason());
                writeLongs(out, entity.position().rawBitsU64()); writeInts(out, entity.rotation().rawBitsU32());
                writeLongs(out, entity.motion().rawBitsU64()); writeBytes(out, entity.canonicalPayload());
                out.writeInt(entity.canonicalAttributeIds().size());
                for (String attribute : entity.canonicalAttributeIds()) writeString(out, attribute);
                out.writeBoolean(entity.runtimeUuidExcluded());
            }
            writeBytes(out, value.successor.bytes()); out.writeInt(value.successor.changedByteOffset());
            out.writeInt(value.successor.predecessorReferences()); out.writeInt(value.successor.successorReferences());
            out.writeInt(value.placementRandomCount); out.writeInt(value.placementContinuation.size());
            for (long continuation : value.placementContinuation) out.writeLong(continuation);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    /** Canonical cross-language identity of one complete procedural start. */
    public static String startIdentitySha256(Mc263PillagerOutpostProducer.Start start) {
        return Mc263PillagerOutpostPersistedAuthority.startIdentitySha256(
                Objects.requireNonNull(start, "Pillager Outpost start"));
    }

    /** Reconstructs E3M15 authority from the persisted Outpost root-piece NBT and predecessor. */
    public static StartAuthority authorityFromPersisted(byte[] persistedRootPieceNbt,
            byte[] predecessor) {
        return Mc263PillagerOutpostPersistedAuthority.reload(
                persistedRootPieceNbt, predecessor).startAuthority();
    }

    /** STR263C1 reload seam; no global carrier field or codec widening is required. */
    public static StartAuthority authorityFromPersisted(Mc263StructureCarrier.ValidStart persisted,
            byte[] predecessor) {
        Objects.requireNonNull(persisted, "persisted Pillager Outpost start");
        require(persisted.startKey().startsWith(Mc263PillagerOutpostProducer.STRUCTURE_KEY + "@"),
                "persisted start is not a Pillager Outpost");
        require(!persisted.orderedPieces().isEmpty(), "persisted Pillager Outpost has no pieces");
        return authorityFromPersisted(persisted.orderedPieces().getFirst()
                .persistedPayload().binaryNbtCompound(), predecessor);
    }

    private static void writeClip(DataOutputStream out, Clip clip) throws IOException {
        out.writeInt(clip.chunkX()); out.writeInt(clip.chunkZ()); out.writeInt(clip.ordinal());
        out.writeInt(clip.minX()); out.writeInt(clip.minY()); out.writeInt(clip.minZ());
        out.writeInt(clip.maxX()); out.writeInt(clip.maxY()); out.writeInt(clip.maxZ());
    }

    private static void writeVec(DataOutputStream out, Vec value) throws IOException {
        out.writeInt(value.x()); out.writeInt(value.y()); out.writeInt(value.z());
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value).getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length); out.write(bytes);
    }

    private static void writeBytes(DataOutputStream out, byte[] value) throws IOException {
        out.writeInt(value.length); out.write(value);
    }

    private static void writeLongs(DataOutputStream out, long[] value) throws IOException {
        out.writeInt(value.length); for (long item : value) out.writeLong(item);
    }

    private static void writeInts(DataOutputStream out, int[] value) throws IOException {
        out.writeInt(value.length); for (int item : value) out.writeInt(item);
    }

    private static PersistedStart successor(Mc263PillagerOutpostProducer.BinaryNbt predecessor,
            StartAuthority authority) {
        validateReferenceTransition(predecessor, authority);
        byte[] before = predecessor.bytes();
        byte[] after = before.clone();
        after[19] = 1;
        require(sha256(after).equals(authority.successorSha256()),
                "Pillager Outpost successor digest drift");
        return new PersistedStart(after, authority.successorSha256(), 19, 0, 1);
    }

    private static void validateReferenceTransition(Mc263PillagerOutpostProducer.BinaryNbt predecessor,
            StartAuthority authority) {
        require(predecessor.length() == authority.predecessorLength()
                        && predecessor.sha256().equals(authority.predecessorSha256()),
                "Pillager Outpost predecessor authority drift");
        byte[] before = predecessor.bytes();
        require(before.length > 19 && before[16] == 0 && before[17] == 0
                        && before[18] == 0 && before[19] == 0,
                "Pillager Outpost predecessor references is not exact zero");
        byte[] after = before.clone();
        after[19] = 1;
        require(sha256(after).equals(authority.successorSha256()),
                "Pillager Outpost successor authority drift");
        int changed = 0;
        for (int i = 0; i < before.length; i++) if (before[i] != after[i]) changed++;
        require(changed == 1, "Pillager Outpost successor changed outside references");
    }

    private static boolean keepByBlockRot(Vec position) {
        long seed = ((long) (position.x * 3_129_871)) ^ ((long) position.z * 116_129_781L)
                ^ position.y;
        seed = seed * seed * 42_317_861L + seed * 11L;
        seed >>= 16;
        long state = (seed ^ LEGACY_MULTIPLIER) & LEGACY_MASK;
        state = (state * LEGACY_MULTIPLIER + LEGACY_ADDEND) & LEGACY_MASK;
        float value = (int) (state >>> 24) * 0x1.0p-24F;
        return value <= (float) ROT_INTEGRITY;
    }

    /** Exact integer structure-template transform with mirror NONE and pivot ZERO. */
    public static Vec transformBlock(int x, int y, int z, Rotation rotation,
            int originX, int originY, int originZ) {
        Objects.requireNonNull(rotation, "Pillager Outpost rotation");
        int rx, rz;
        switch (rotation) {
            case NONE -> { rx = x; rz = z; }
            case CLOCKWISE_90 -> { rx = -z; rz = x; }
            case CLOCKWISE_180 -> { rx = -x; rz = -z; }
            case COUNTERCLOCKWISE_90 -> { rx = z; rz = -x; }
            default -> throw new IllegalArgumentException("unknown Pillager Outpost rotation");
        }
        return new Vec(Math.addExact(originX, rx), Math.addExact(originY, y),
                Math.addExact(originZ, rz));
    }

    /** Exact Vec3 structure-template transform with mirror NONE and pivot ZERO. */
    public static DoubleVec transformEntity(double x, double y, double z, Rotation rotation,
            int originX, int originY, int originZ) {
        Objects.requireNonNull(rotation, "Pillager Outpost entity rotation");
        double rx, rz;
        switch (rotation) {
            case NONE -> { rx = x; rz = z; }
            case COUNTERCLOCKWISE_90 -> { rx = z; rz = 1.0D - x; }
            case CLOCKWISE_90 -> { rx = 1.0D - z; rz = x; }
            case CLOCKWISE_180 -> { rx = 1.0D - x; rz = 1.0D - z; }
            default -> throw new IllegalArgumentException("unknown Pillager Outpost rotation");
        }
        return new DoubleVec(originX + rx, originY + y, originZ + rz);
    }

    /** Rotates exact block-state directional properties and re-admits through the exact registry. */
    public static String rotateState(String state, Rotation rotation) {
        Objects.requireNonNull(state, "Pillager Outpost exact state");
        Objects.requireNonNull(rotation, "Pillager Outpost rotation");
        int open = state.indexOf('[');
        if (open < 0) return exact(state);
        require(state.endsWith("]") && open > 0, "malformed Pillager Outpost exact state");
        String block = state.substring(0, open).trim();
        TreeMap<String, String> properties = new TreeMap<>();
        String body = state.substring(open + 1, state.length() - 1);
        if (!body.isBlank()) {
            for (String raw : body.split(",")) {
                String item = raw.trim();
                int equals = item.indexOf('=');
                require(equals > 0 && equals < item.length() - 1,
                        "malformed Pillager Outpost state property");
                String key = item.substring(0, equals).trim();
                String value = item.substring(equals + 1).trim();
                if ("facing".equals(key)) value = rotateHorizontal(value, rotation);
                if ("axis".equals(key) && quarterTurn(rotation)) {
                    if ("x".equals(value)) value = "z";
                    else if ("z".equals(value)) value = "x";
                }
                if (isHorizontal(key)) key = rotateHorizontal(key, rotation);
                require(properties.put(key, value) == null,
                        "duplicate Pillager Outpost state property after rotation");
            }
        }
        StringBuilder result = new StringBuilder(block).append('[');
        boolean first = true;
        for (Map.Entry<String, String> property : properties.entrySet()) {
            if (!first) result.append(',');
            result.append(property.getKey()).append('=').append(property.getValue());
            first = false;
        }
        return exact(result.append(']').toString());
    }

    private static String rotateHorizontal(String value, Rotation rotation) {
        if (!isHorizontal(value)) return value;
        List<String> values = List.of("north", "east", "south", "west");
        int turns = switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
        return values.get((values.indexOf(value) + turns) & 3);
    }

    private static boolean quarterTurn(Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
    }

    private static boolean isHorizontal(String value) {
        return "north".equals(value) || "east".equals(value)
                || "south".equals(value) || "west".equals(value);
    }

    private static String authenticatedWetSuccessor(String state) {
        if (!hasDryWaterloggedProperty(state)) return null;
        return exact(state.replace("waterlogged=false", "waterlogged=true"));
    }

    private static boolean hasDryWaterloggedProperty(String state) {
        return state.contains("waterlogged=false");
    }

    private static String exact(String state) {
        Objects.requireNonNull(state, "Pillager Outpost exact state");
        String canonical = state.trim();
        int open = canonical.indexOf('[');
        String key = open < 0 ? canonical : canonical.substring(0, open);
        require(key.startsWith("minecraft:") && key.length() > "minecraft:".length(),
                "invalid Pillager Outpost exact-state key: " + state);
        for (int i = "minecraft:".length(); i < key.length(); i++) {
            char c = key.charAt(i);
            require((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_'
                            || c == '/' || c == '.' || c == '-',
                    "invalid Pillager Outpost exact-state key: " + state);
        }
        if (open < 0) return key;
        require(canonical.endsWith("]") && canonical.indexOf('[', open + 1) < 0,
                "malformed Pillager Outpost exact state: " + state);
        TreeMap<String, String> properties = new TreeMap<>();
        String body = canonical.substring(open + 1, canonical.length() - 1);
        require(!body.isBlank(), "empty Pillager Outpost state properties: " + state);
        for (String raw : body.split(",")) {
            String item = raw.trim();
            int equals = item.indexOf('=');
            require(equals > 0 && equals < item.length() - 1 && item.indexOf('=', equals + 1) < 0,
                    "malformed Pillager Outpost state property: " + item);
            String name = item.substring(0, equals).trim();
            String value = item.substring(equals + 1).trim();
            require(!name.isEmpty() && !value.isEmpty() && properties.put(name, value) == null,
                    "duplicate/empty Pillager Outpost state property: " + item);
        }
        StringBuilder result = new StringBuilder(key).append('[');
        boolean first = true;
        for (Map.Entry<String, String> property : properties.entrySet()) {
            if (!first) result.append(',');
            result.append(property.getKey()).append('=').append(property.getValue());
            first = false;
        }
        return result.append(']').toString();
    }

    private static String blockKey(String state) {
        int open = state.indexOf('[');
        return open < 0 ? state.trim() : state.substring(0, open).trim();
    }

    private static Vec offset(Vec value, Direction direction) {
        return new Vec(Math.addExact(value.x, direction.dx), Math.addExact(value.y, direction.dy),
                Math.addExact(value.z, direction.dz));
    }

    private static boolean intersects(Bounds box, Clip clip) {
        return box.maxX() >= clip.minX && box.minX() <= clip.maxX
                && box.maxY() >= clip.minY && box.minY() <= clip.maxY
                && box.maxZ() >= clip.minZ && box.minZ() <= clip.maxZ;
    }

    private static boolean containsLongSequence(byte[] haystack, long[] values) {
        byte[] needle = new byte[values.length * Long.BYTES];
        int offset = 0;
        for (long value : values) {
            for (int shift = 56; shift >= 0; shift -= 8) needle[offset++] = (byte) (value >>> shift);
        }
        return containsBytes(haystack, needle);
    }

    private static boolean containsIntSequence(byte[] haystack, int[] values) {
        byte[] needle = new byte[values.length * Integer.BYTES];
        int offset = 0;
        for (int value : values) {
            for (int shift = 24; shift >= 0; shift -= 8) needle[offset++] = (byte) (value >>> shift);
        }
        return containsBytes(haystack, needle);
    }

    private static boolean containsBytes(byte[] haystack, byte[] needle) {
        if (needle.length == 0 || needle.length > haystack.length) return false;
        outer: for (int start = 0; start <= haystack.length - needle.length; start++) {
            for (int index = 0; index < needle.length; index++) {
                if (haystack[start + index] != needle[index]) continue outer;
            }
            return true;
        }
        return false;
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


    /** Pure resource preflight seam; implementations must not query or mutate world state. */
    /** External, source-bound admission receipt for one exact procedural start. */
    public record StartAuthority(String startIdentitySha256, String predecessorSha256,
            int predecessorLength, String successorSha256) {
        public StartAuthority {
            requireSha(startIdentitySha256, "start identity");
            requireSha(predecessorSha256, "predecessor");
            requireSha(successorSha256, "successor");
            if (predecessorLength <= 19) {
                throw new IllegalArgumentException("invalid Pillager Outpost predecessor length");
            }
        }

        private static void requireSha(String value, String label) {
            if (value == null || value.length() != 64) {
                throw new IllegalArgumentException("invalid Pillager Outpost " + label + " sha256");
            }
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                    throw new IllegalArgumentException("invalid Pillager Outpost " + label + " sha256");
                }
            }
        }
    }

    /** Inclusive one-chunk destination clip plus its z-major/x-minor aggregate ordinal. */
    public static final class Clip {
        private final int chunkX, chunkZ, ordinal, minX, minY, minZ, maxX, maxY, maxZ;

        public Clip(int chunkX, int chunkZ, int ordinal, int minY, int maxY) {
            if (ordinal < 0 || minY > maxY) throw new IllegalArgumentException("invalid Pillager Outpost clip");
            this.chunkX = chunkX; this.chunkZ = chunkZ; this.ordinal = ordinal;
            this.minX = Math.multiplyExact(chunkX, 16); this.minZ = Math.multiplyExact(chunkZ, 16);
            this.maxX = Math.addExact(minX, 15); this.maxZ = Math.addExact(minZ, 15);
            this.minY = minY; this.maxY = maxY;
        }

        public int chunkX() { return chunkX; } public int chunkZ() { return chunkZ; }
        public int ordinal() { return ordinal; } public int minX() { return minX; }
        public int minY() { return minY; } public int minZ() { return minZ; }
        public int maxX() { return maxX; } public int maxY() { return maxY; }
        public int maxZ() { return maxZ; }
        public boolean contains(Vec value) {
            return value.x >= minX && value.x <= maxX && value.y >= minY && value.y <= maxY
                    && value.z >= minZ && value.z <= maxZ;
        }
        /**
         * Horizontal half of {@link #contains}. Vanilla drops a cell whose transformed column lies
         * outside {@code StructurePlaceSettings.getBoundingBox()} before the projection processor
         * chain runs, so such a cell never reaches {@code GravityProcessor} and never issues a
         * {@code WorldGenLevel.getHeight} query. A {@code terrain_matching} projection only shifts
         * Y, so the column test is exact: no cell outside this column can ever satisfy
         * {@link #contains}.
         */
        public boolean containsColumn(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    public static final class Vec {
        private final int x, y, z;
        public Vec(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        public int x() { return x; } public int y() { return y; } public int z() { return z; }
        @Override public boolean equals(Object other) {
            return other instanceof Vec value && x == value.x && y == value.y && z == value.z;
        }
        @Override public int hashCode() { return Objects.hash(x, y, z); }
        @Override public String toString() { return "Vec[x=" + x + ", y=" + y + ", z=" + z + "]"; }
    }
    public static final class DoubleVec {
        private final double x, y, z;
        public DoubleVec(double x, double y, double z) {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("non-finite Pillager Outpost entity position");
            }
            this.x = x; this.y = y; this.z = z;
        }
        public double x() { return x; } public double y() { return y; } public double z() { return z; }
    }

    public static final class FluidState {
        private final String fluidKey; private final boolean source; private final double height;
        public FluidState(String fluidKey, boolean source, double height) {
            this.fluidKey = Objects.requireNonNull(fluidKey); this.source = source; this.height = height;
            if (!Double.isFinite(height) || height < 0.0D
                    || (fluidKey.isEmpty() && (source || height != 0.0D))) {
                throw new IllegalArgumentException("invalid Pillager Outpost fluid state");
            }
        }
        public String fluidKey() { return fluidKey; } public boolean source() { return source; }
        public double height() { return height; }
        public static FluidState emptyState() { return new FluidState("", false, 0.0D); }
        public boolean empty() { return fluidKey.isEmpty(); }
    }

    public enum OperationKind {
        HEIGHT_QUERY, FLUID_QUERY, BLOCK_QUERY, SET_BLOCK, SET_BLOCK_AND_UPDATE,
        BLOCK_ENTITY_QUERY, BLOCK_ENTITY_CHANGED, RANDOM_NEXT_LONG, FLUID_TICK, STRUCTURE_ENTITY
    }

    public static final class Operation {
        private final int ordinal; private final OperationKind kind; private final Vec position; private final String detail;
        public Operation(int ordinal, OperationKind kind, Vec position, String detail) {
            this.ordinal = ordinal; this.kind = Objects.requireNonNull(kind); this.position = position;
            this.detail = Objects.requireNonNull(detail);
        }
        public int ordinal() { return ordinal; } public OperationKind kind() { return kind; }
        public Vec position() { return position; } public String detail() { return detail; }
    }

    public interface BlockEntityAccess {
        String blockIdentity();
        String entityType();
        void loadOminousBanner();
        void setLootTable(String table, long signedSeed);
        BlockEntitySnapshot snapshot();
        void setChanged();
    }

    public static final class BlockEntitySnapshot {
        private final Vec position;
        private final String blockIdentity, entityType, semantic, lootTable;
        private final long lootSeed;
        private final byte[] canonicalNbt;
        private final String canonicalNbtSha256;

        public BlockEntitySnapshot(Vec position, String blockIdentity, String entityType,
                String semantic, String lootTable, long lootSeed, byte[] canonicalNbt) {
            this.position = Objects.requireNonNull(position); this.blockIdentity = Objects.requireNonNull(blockIdentity);
            this.entityType = Objects.requireNonNull(entityType); this.semantic = Objects.requireNonNull(semantic);
            this.lootTable = Objects.requireNonNull(lootTable); this.lootSeed = lootSeed;
            this.canonicalNbt = Objects.requireNonNull(canonicalNbt).clone();
            this.canonicalNbtSha256 = sha256(this.canonicalNbt);
        }
        public Vec position() { return position; } public String blockIdentity() { return blockIdentity; }
        public String entityType() { return entityType; } public String semantic() { return semantic; }
        public String lootTable() { return lootTable; } public long lootSeed() { return lootSeed; }
        public byte[] canonicalNbt() { return canonicalNbt.clone(); }
        public String canonicalNbtSha256() { return canonicalNbtSha256; }
    }

    public static final class EntityRequest {
        private final String entityKey; private final DoubleVec templatePosition;
        private final Vec templateBlockPosition; private final Rotation rotation;
        public EntityRequest(String entityKey, DoubleVec templatePosition,
                Vec templateBlockPosition, Rotation rotation) {
            this.entityKey = Objects.requireNonNull(entityKey);
            this.templatePosition = Objects.requireNonNull(templatePosition);
            this.templateBlockPosition = Objects.requireNonNull(templateBlockPosition);
            this.rotation = Objects.requireNonNull(rotation);
        }
        public String entityKey() { return entityKey; } public DoubleVec templatePosition() { return templatePosition; }
        public Vec templateBlockPosition() { return templateBlockPosition; } public Rotation rotation() { return rotation; }
    }

    public static final class RawDoubles {
        private final double[] values;
        private final long[] rawBitsU64;
        private final List<String> rawBitsHex;
        public RawDoubles(double[] values, long[] rawBitsU64, List<String> rawBitsHex) {
            this.values = Objects.requireNonNull(values).clone();
            this.rawBitsU64 = Objects.requireNonNull(rawBitsU64).clone();
            this.rawBitsHex = List.copyOf(rawBitsHex);
        }
        public int size() { return values.length; }
        public double[] values() { return values.clone(); }
        public long[] rawBitsU64() { return rawBitsU64.clone(); }
        public List<String> rawBitsHex() { return rawBitsHex; }
        void validate() {
            require(values.length == rawBitsU64.length && values.length == rawBitsHex.size(),
                    "Pillager Outpost raw-double cardinality drift");
            for (int i = 0; i < values.length; i++) {
                require(Double.isFinite(values[i])
                                && Double.doubleToRawLongBits(values[i]) == rawBitsU64[i]
                                && String.format("%016x", rawBitsU64[i]).equals(rawBitsHex.get(i)),
                        "Pillager Outpost raw-double bits drift");
            }
        }
    }

    public static final class RawFloats {
        private final float[] values;
        private final int[] rawBitsU32;
        private final List<String> rawBitsHex;
        public RawFloats(float[] values, int[] rawBitsU32, List<String> rawBitsHex) {
            this.values = Objects.requireNonNull(values).clone();
            this.rawBitsU32 = Objects.requireNonNull(rawBitsU32).clone();
            this.rawBitsHex = List.copyOf(rawBitsHex);
        }
        public int size() { return values.length; }
        public float[] values() { return values.clone(); }
        public int[] rawBitsU32() { return rawBitsU32.clone(); }
        public List<String> rawBitsHex() { return rawBitsHex; }
        void validate() {
            require(values.length == rawBitsU32.length && values.length == rawBitsHex.size(),
                    "Pillager Outpost raw-float cardinality drift");
            for (int i = 0; i < values.length; i++) {
                require(Float.isFinite(values[i])
                                && Float.floatToRawIntBits(values[i]) == rawBitsU32[i]
                                && String.format("%08x", rawBitsU32[i]).equals(rawBitsHex.get(i)),
                        "Pillager Outpost raw-float bits drift");
            }
        }
    }

    public static final class EntityPayload {
        private final int encounterOrdinal;
        private final String entityKey, spawnReason, runtimeSpawnReason;
        private final RawDoubles position, motion;
        private final RawFloats rotation;
        private final byte[] canonicalPayload;
        private final String canonicalPayloadSha256;
        private final List<String> canonicalAttributeIds;
        private final boolean runtimeUuidExcluded;

        public EntityPayload(int encounterOrdinal, String entityKey, String spawnReason,
                String runtimeSpawnReason, RawDoubles position, RawFloats rotation,
                RawDoubles motion, byte[] canonicalPayload, List<String> canonicalAttributeIds,
                boolean runtimeUuidExcluded) {
            this.encounterOrdinal = encounterOrdinal; this.entityKey = Objects.requireNonNull(entityKey);
            this.spawnReason = Objects.requireNonNull(spawnReason);
            this.runtimeSpawnReason = Objects.requireNonNull(runtimeSpawnReason);
            this.position = Objects.requireNonNull(position); this.rotation = Objects.requireNonNull(rotation);
            this.motion = Objects.requireNonNull(motion);
            this.canonicalPayload = Objects.requireNonNull(canonicalPayload).clone();
            this.canonicalPayloadSha256 = sha256(this.canonicalPayload);
            this.canonicalAttributeIds = List.copyOf(canonicalAttributeIds);
            this.runtimeUuidExcluded = runtimeUuidExcluded;
        }
        EntityPayload withEncounterOrdinal(int ordinal) {
            return new EntityPayload(ordinal, entityKey, spawnReason, runtimeSpawnReason,
                    position, rotation, motion, canonicalPayload, canonicalAttributeIds, runtimeUuidExcluded);
        }
        public int encounterOrdinal() { return encounterOrdinal; }
        public String entityKey() { return entityKey; } public String spawnReason() { return spawnReason; }
        public String runtimeSpawnReason() { return runtimeSpawnReason; }
        public RawDoubles position() { return position; } public RawFloats rotation() { return rotation; }
        public RawDoubles motion() { return motion; } public byte[] canonicalPayload() { return canonicalPayload.clone(); }
        public String canonicalPayloadSha256() { return canonicalPayloadSha256; }
        public List<String> canonicalAttributeIds() { return canonicalAttributeIds; }
        public boolean runtimeUuidExcluded() { return runtimeUuidExcluded; }
    }

    /** Caller/world seam. All capability methods must be pure. fork() is unpublished. */
    public enum PublishStatus { COMMITTED, REPLAYED }

    public interface WorldTransaction {
        boolean supportsAtomicForkPublishWithRandom();
        boolean supportsHeightmap(String heightmap);
        boolean supportsFluidStateQueries();
        boolean supportsBlockStateQueries();
        boolean supportsSetBlockAndUpdate();
        boolean supportsWriteFlags(int flags);
        boolean supportsExactState(String exactState);
        boolean supportsBentPayloads();
        boolean supportsLootPayloads();
        boolean supportsStructureEntityPayloads();
        boolean supportsEmptyUnauthenticatedLanes();
        boolean supportsBlockEntity(String blockIdentity, String entityType);
        boolean supportsLootTable(String lootTable);
        boolean supportsStructureEntity(String entityKey);
        boolean supportsFluidTick(String fluidKey, int delay);
        WorldTransaction fork();
        int getHeight(String heightmap, int x, int z);
        FluidState getFluidState(Vec position);
        String getBlockState(Vec position);
        boolean setBlock(Vec position, String exactState, int flags);
        boolean setBlockAndUpdate(Vec position, String exactState);
        BlockEntityAccess getBlockEntity(Vec position);
        void scheduleFluidTick(Vec position, String fluidKey, int delay);
        /** Executes official entity construction/finalization using this exact caller-owned RNG. */
        EntityPayload spawnStructureEntity(EntityRequest request, PlacementRandom random);
        /**
         * Atomically commits fork + all sidecars/successor + caller RNG replacement, or nothing.
         * For this destination clip, an existing byte-identical fingerprint+canonical payload must
         * return REPLAYED without republishing any world/sidecar/start lane. Any existing conflicting
         * fingerprint or payload must throw before changing world state or caller RNG. Both COMMITTED
         * and REPLAYED replace callerRandom with acceptedRandom so the caller continuation is exact.
         */
        PublishStatus publishAtomically(WorldTransaction isolated, Settlement settlement,
                PlacementRandom callerRandom, PlacementRandom acceptedRandom);
    }

    /** Atomic persisted-start boundary for the caller-owned WorldGenRegion Xoroshiro source. */
    public interface PersistedWorldTransaction extends WorldTransaction {
        /** Pure capability declaration; it must not query or mutate the live world. */
        boolean supportsPersistedAtomicPublishWithWorldGenRegionRandom();
        /** Pure optimistic-CAS preflight over the exact durable STR/WGR predecessor. */
        boolean acceptsPersistedPredecessor(Mc263StructureCarrier structureCarrier,
                Mc263WorldGenRegionRandom.State worldGenRegionRandomPredecessor);
        /** Executes official entity construction/finalization against the exact WGR source. */
        EntityPayload spawnStructureEntity(EntityRequest request,
                Mc263WorldGenRegionRandom worldGenRegionRandom);
        /**
         * Atomically publishes the isolated world, sidecars, STR successor and WGR successor.
         * Implementations must CAS both predecessors again at publication. Exact fingerprint/payload,
         * carrier and WGR replay returns REPLAYED without republishing world/start side effects;
         * any conflict or stale predecessor fails without changing caller-visible state.
         */
        PublishStatus publishPersistedAtomically(WorldTransaction isolated, Settlement settlement,
                Mc263StructureCarrier structureCarrierPredecessor,
                Mc263StructureCarrier structureCarrierSuccessor,
                Mc263WorldGenRegionRandom callerWorldGenRegionRandom,
                Mc263WorldGenRegionRandom.State worldGenRegionRandomPredecessor,
                Mc263WorldGenRegionRandom acceptedWorldGenRegionRandom);
    }

    public static final class FinalState {
        private final Vec position; private final String exactState;
        public FinalState(Vec position, String exactState) {
            this.position = Objects.requireNonNull(position); this.exactState = Objects.requireNonNull(exactState);
        }
        public Vec position() { return position; } public String exactState() { return exactState; }
    }

    public static final class BentPayload {
        private final int ordinal, loadEncounterOrder;
        private final Vec position;
        private final String exactState, blockIdentity, entityType;
        private final byte[] canonicalNbt;
        private final String canonicalNbtSha256;
        BentPayload(int ordinal, Vec position, String exactState, String blockIdentity,
                String entityType, byte[] canonicalNbt, int loadEncounterOrder) {
            this.ordinal = ordinal; this.position = position; this.exactState = exactState;
            this.blockIdentity = blockIdentity; this.entityType = entityType;
            this.canonicalNbt = canonicalNbt.clone(); this.canonicalNbtSha256 = sha256(this.canonicalNbt);
            this.loadEncounterOrder = loadEncounterOrder;
        }
        public int ordinal() { return ordinal; } public Vec position() { return position; }
        public String exactState() { return exactState; } public String blockIdentity() { return blockIdentity; }
        public String entityType() { return entityType; } public byte[] canonicalNbt() { return canonicalNbt.clone(); }
        public String canonicalNbtSha256() { return canonicalNbtSha256; }
        public int loadEncounterOrder() { return loadEncounterOrder; }
    }

    public static final class LootPayload {
        private final int ordinal, bentOrdinal; private final Vec position; private final String table;
        private final long signedCallerSeed;
        public LootPayload(int ordinal, Vec position, String table, long signedCallerSeed, int bentOrdinal) {
            this.ordinal = ordinal; this.position = Objects.requireNonNull(position);
            this.table = Objects.requireNonNull(table); this.signedCallerSeed = signedCallerSeed;
            this.bentOrdinal = bentOrdinal;
        }
        public int ordinal() { return ordinal; } public Vec position() { return position; }
        public String table() { return table; } public long signedCallerSeed() { return signedCallerSeed; }
        public int bentOrdinal() { return bentOrdinal; }
    }

    public static final class PersistedStart {
        private final byte[] bytes;
        private final String sha256;
        private final int changedByteOffset, predecessorReferences, successorReferences;
        PersistedStart(byte[] bytes, String sha256, int changedByteOffset,
                int predecessorReferences, int successorReferences) {
            this.bytes = bytes.clone(); this.sha256 = sha256; this.changedByteOffset = changedByteOffset;
            this.predecessorReferences = predecessorReferences; this.successorReferences = successorReferences;
        }
        public byte[] bytes() { return bytes.clone(); } public String sha256() { return sha256; }
        public int changedByteOffset() { return changedByteOffset; }
        public int predecessorReferences() { return predecessorReferences; }
        public int successorReferences() { return successorReferences; }
    }

    public static final class Settlement {
        private final Clip clip;
        private final List<Operation> operations;
        private final List<FinalState> finalStates;
        private final List<BentPayload> bent;
        private final List<LootPayload> loot;
        private final List<EntityPayload> entities;
        private final PersistedStart successor;
        private final int placementRandomCount;
        private final List<Long> placementContinuation;
        private final String fingerprint;
        private final byte[] canonicalPayload;
        private final String canonicalPayloadSha256;
        Settlement(Clip clip, List<Operation> operations, List<FinalState> finalStates,
                List<BentPayload> bent, List<LootPayload> loot, List<EntityPayload> entities,
                PersistedStart successor, int placementRandomCount, List<Long> placementContinuation,
                String fingerprint) {
            this.clip = clip; this.operations = List.copyOf(operations); this.finalStates = List.copyOf(finalStates);
            this.bent = List.copyOf(bent); this.loot = List.copyOf(loot); this.entities = List.copyOf(entities);
            this.successor = successor; this.placementRandomCount = placementRandomCount;
            this.placementContinuation = List.copyOf(placementContinuation);
            this.fingerprint = Objects.requireNonNull(fingerprint, "Pillager Outpost fingerprint");
            this.canonicalPayload = Mc263PillagerOutpostSettlement.canonicalPayload(this);
            this.canonicalPayloadSha256 = sha256(this.canonicalPayload);
        }
        public Clip clip() { return clip; } public List<Operation> operations() { return operations; }
        public List<FinalState> finalStates() { return finalStates; } public List<BentPayload> bent() { return bent; }
        public List<LootPayload> loot() { return loot; } public List<EntityPayload> entities() { return entities; }
        public PersistedStart successor() { return successor; }
        public int placementRandomCount() { return placementRandomCount; }
        public List<Long> placementContinuationNextLongI64() { return placementContinuation; }
        public String fingerprint() { return fingerprint; }
        public byte[] canonicalPayload() { return canonicalPayload.clone(); }
        public String canonicalPayloadSha256() { return canonicalPayloadSha256; }
    }

    /** Caller-owned exact Xoroshiro128++ worldgen-region random state. No seeding/reset factory exists. */
    public static final class PlacementRandom {
        private Xoroshiro source;
        private int count;
        private PlacementRandom(Xoroshiro source, int count) { this.source = source; this.count = count; }
        public static PlacementRandom fromState(long lo, long hi, int count) {
            if (count < 0) throw new IllegalArgumentException("negative Pillager Outpost RNG count");
            return new PlacementRandom(new Xoroshiro(lo, hi), count);
        }
        public long nextLong() { return ((long) next(32) << 32) + next(32); }
        public float nextFloat() { return next(24) * 0x1.0p-24F; }
        private int next(int bits) { count++; return (int) (source.nextLong() >>> (64 - bits)); }
        public PlacementRandom copy() { return new PlacementRandom(source.copy(), count); }
        public void replaceWith(PlacementRandom value) {
            Objects.requireNonNull(value); source = value.source.copy(); count = value.count;
        }
        public long lo() { return source.lo; } public long hi() { return source.hi; }
        public int count() { return count; }
        private boolean sameState(PlacementRandom value) {
            return lo() == value.lo() && hi() == value.hi() && count == value.count;
        }
        public List<Long> continuationNextLongI64() {
            Xoroshiro copy = source.copy(); ArrayList<Long> result = new ArrayList<>(8);
            for (int i = 0; i < 8; i++) result.add(copy.nextLong());
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
            long first = lo, second = hi, value = Long.rotateLeft(first + second, 17) + first;
            second ^= first; lo = Long.rotateLeft(first, 49) ^ second ^ second << 21;
            hi = Long.rotateLeft(second, 28); return value;
        }
        Xoroshiro copy() { return new Xoroshiro(lo, hi); }
    }

    private interface SettlementRandom {
        long nextLong();
        float nextFloat();
        SettlementRandom copy();
        boolean sameState(SettlementRandom value);
        int count();
        List<Long> continuationNextLongI64();
    }

    private static final class LegacySettlementRandom implements SettlementRandom {
        private final PlacementRandom random;
        LegacySettlementRandom(PlacementRandom random) {
            this.random = Objects.requireNonNull(random);
        }
        @Override public long nextLong() { return random.nextLong(); }
        @Override public float nextFloat() { return random.nextFloat(); }
        @Override public SettlementRandom copy() { return new LegacySettlementRandom(random.copy()); }
        @Override public boolean sameState(SettlementRandom value) {
            return value instanceof LegacySettlementRandom other && random.sameState(other.random);
        }
        @Override public int count() { return random.count(); }
        @Override public List<Long> continuationNextLongI64() {
            return random.continuationNextLongI64();
        }
    }

    private static final class WorldGenRegionSettlementRandom implements SettlementRandom {
        private final Mc263WorldGenRegionRandom random;
        WorldGenRegionSettlementRandom(Mc263WorldGenRegionRandom random) {
            this.random = Objects.requireNonNull(random);
        }
        @Override public long nextLong() { return random.nextLong(); }
        @Override public float nextFloat() { return random.nextFloat(); }
        @Override public SettlementRandom copy() {
            return new WorldGenRegionSettlementRandom(random.copy());
        }
        @Override public boolean sameState(SettlementRandom value) {
            return value instanceof WorldGenRegionSettlementRandom other
                    && random.snapshot().equals(other.random.snapshot());
        }
        @Override public int count() { return random.snapshot().drawCount(); }
        @Override public List<Long> continuationNextLongI64() {
            return random.continuationNextLongI64();
        }
    }

    private static final class Trace {
        private final WorldTransaction world;
        private final ArrayList<Operation> operations = new ArrayList<>();
        Trace(WorldTransaction world) { this.world = world; }
        List<Operation> operations() { return List.copyOf(operations); }
        private void add(OperationKind kind, Vec pos, String detail) {
            operations.add(new Operation(operations.size(), kind, pos, detail));
        }
        int height(String map, int x, int z) {
            int result = world.getHeight(map, x, z); add(OperationKind.HEIGHT_QUERY,
                    new Vec(x, 0, z), map + "->" + result); return result;
        }
        FluidState fluid(Vec pos) {
            FluidState result = Objects.requireNonNull(world.getFluidState(pos));
            add(OperationKind.FLUID_QUERY, pos, result.fluidKey() + ":" + result.source()
                    + ":" + Long.toHexString(Double.doubleToRawLongBits(result.height()))); return result;
        }
        String blockState(Vec pos) {
            String result = Objects.requireNonNull(world.getBlockState(pos));
            add(OperationKind.BLOCK_QUERY, pos, result); return result;
        }
        boolean supportsExactState(String state) { return world.supportsExactState(state); }
        boolean setBlock(Vec pos, String state, int flags) {
            boolean result = world.setBlock(pos, state, flags);
            add(OperationKind.SET_BLOCK, pos, state + "|" + flags + "->" + result); return result;
        }
        boolean setBlockAndUpdate(Vec pos, String state) {
            boolean result = world.setBlockAndUpdate(pos, state);
            add(OperationKind.SET_BLOCK_AND_UPDATE, pos, state + "->" + result); return result;
        }
        BlockEntityAccess blockEntity(Vec pos) {
            BlockEntityAccess result = world.getBlockEntity(pos);
            add(OperationKind.BLOCK_ENTITY_QUERY, pos,
                    result == null ? "null" : result.blockIdentity() + "/" + result.entityType());
            return result;
        }
        void changed(Vec pos, String type) { add(OperationKind.BLOCK_ENTITY_CHANGED, pos, type); }
        void randomNextLong(Vec pos, long value) {
            add(OperationKind.RANDOM_NEXT_LONG, pos, Long.toString(value));
        }
        void fluidTick(Vec pos, String key, int delay) {
            world.scheduleFluidTick(pos, key, delay); add(OperationKind.FLUID_TICK, pos, key + ":" + delay);
        }
        EntityPayload entity(EntityRequest request, SettlementRandom random) {
            EntityPayload result;
            if (random instanceof LegacySettlementRandom legacy) {
                result = Mc263PillagerOutpostEntityAuthority.spawnStructureEntity(
                        request, legacy.random);
            } else if (random instanceof WorldGenRegionSettlementRandom persisted
                    && world instanceof PersistedWorldTransaction) {
                result = Mc263PillagerOutpostEntityAuthority.spawnStructureEntity(
                        request, persisted.random);
            } else {
                throw new IllegalStateException("Pillager Outpost RNG/transaction kind mismatch");
            }
            add(OperationKind.STRUCTURE_ENTITY, request.templateBlockPosition(), request.entityKey());
            return result;
        }
    }

    private enum BlockEntityKind { OMINOUS_BANNER, LOOT_CONTAINER }
    private static final class BlockEntitySpec {
        private final String blockIdentity, entityType, lootTable; private final BlockEntityKind kind;
        BlockEntitySpec(String blockIdentity, String entityType, String lootTable, BlockEntityKind kind) {
            this.blockIdentity = Objects.requireNonNull(blockIdentity); this.entityType = Objects.requireNonNull(entityType);
            this.lootTable = lootTable; this.kind = Objects.requireNonNull(kind);
        }
        @Override public boolean equals(Object other) {
            return other instanceof BlockEntitySpec value && blockIdentity.equals(value.blockIdentity)
                    && entityType.equals(value.entityType) && Objects.equals(lootTable, value.lootTable)
                    && kind == value.kind;
        }
        @Override public int hashCode() { return Objects.hash(blockIdentity, entityType, lootTable, kind); }
        @Override public String toString() { return blockIdentity + "/" + entityType + "/" + kind; }
    }
    private static final class PreparedCell {
        private final int localX, localY, localZ; private final String state, wetState;
        private final BlockEntitySpec blockEntity;
        PreparedCell(int localX, int localY, int localZ, String state, String wetState, BlockEntitySpec blockEntity) {
            this.localX = localX; this.localY = localY; this.localZ = localZ;
            this.state = Objects.requireNonNull(state); this.wetState = wetState; this.blockEntity = blockEntity;
        }
    }
    private static final class PreparedEntity {
        private final String entityKey; private final double x, y, z; private final int blockX, blockY, blockZ;
        PreparedEntity(String entityKey, double x, double y, double z, int blockX, int blockY, int blockZ) {
            this.entityKey = Objects.requireNonNull(entityKey); this.x = x; this.y = y; this.z = z;
            this.blockX = blockX; this.blockY = blockY; this.blockZ = blockZ;
        }
    }
    private static final class PreparedTemplate {
        private final String templateKey, processor; private final List<PreparedCell> cells, markers;
        private final List<PreparedEntity> entities;
        PreparedTemplate(String templateKey, String processor, List<PreparedCell> cells,
                List<PreparedCell> markers, List<PreparedEntity> entities) {
            this.templateKey = Objects.requireNonNull(templateKey); this.processor = Objects.requireNonNull(processor);
            this.cells = List.copyOf(cells); this.markers = List.copyOf(markers); this.entities = List.copyOf(entities);
        }
    }

    private static final class PersistedNbtCursor {
        private static final int MAX_DEPTH = 512;
        private final byte[] bytes;
        private int at;
        PersistedNbtCursor(byte[] bytes) {
            this.bytes = Objects.requireNonNull(bytes, "persisted Pillager Outpost piece NBT").clone();
        }
        int remaining() { return bytes.length - at; }
        int u8() {
            require(at < bytes.length, "truncated persisted Pillager Outpost piece NBT");
            return Byte.toUnsignedInt(bytes[at++]);
        }
        int i32() { return (u8() << 24) | (u8() << 16) | (u8() << 8) | u8(); }
        int u16() { return (u8() << 8) | u8(); }
        String utf() {
            int length = u16();
            require(length <= remaining(), "truncated persisted Pillager Outpost NBT string");
            byte[] raw = Arrays.copyOfRange(bytes, at, at + length);
            at += length;
            for (byte value : raw) {
                require(Byte.toUnsignedInt(value) > 0 && Byte.toUnsignedInt(value) <= 0x7f,
                        "non-ASCII persisted Pillager Outpost NBT string");
            }
            return new String(raw, StandardCharsets.US_ASCII);
        }
        int[] intArray() {
            int length = i32();
            require(length >= 0 && length <= remaining() / Integer.BYTES,
                    "invalid persisted Pillager Outpost NBT int-array length");
            int[] value = new int[length];
            for (int index = 0; index < length; index++) value[index] = i32();
            return value;
        }
        void skipPayload(int type, int depth) {
            require(depth <= MAX_DEPTH, "persisted Pillager Outpost NBT depth drift");
            switch (type) {
                case 1 -> skip(1);
                case 2 -> skip(2);
                case 3, 5 -> skip(4);
                case 4, 6 -> skip(8);
                case 7 -> {
                    int length = i32();
                    require(length >= 0, "negative persisted Pillager Outpost NBT byte array");
                    skip(length);
                }
                case 8 -> skip(u16());
                case 9 -> {
                    int element = u8();
                    int length = i32();
                    require(element >= 0 && element <= 12 && length >= 0,
                            "invalid persisted Pillager Outpost NBT list");
                    require(element != 0 || length == 0, "nonempty persisted TAG_End list");
                    for (int index = 0; index < length; index++) skipPayload(element, depth + 1);
                }
                case 10 -> {
                    while (true) {
                        int child = u8();
                        if (child == 0) break;
                        require(child <= 12, "unknown persisted Pillager Outpost NBT type");
                        utf();
                        skipPayload(child, depth + 1);
                    }
                }
                case 11 -> {
                    int length = i32();
                    require(length >= 0, "negative persisted Pillager Outpost NBT int array");
                    skip(Math.multiplyExact(length, Integer.BYTES));
                }
                case 12 -> {
                    int length = i32();
                    require(length >= 0, "negative persisted Pillager Outpost NBT long array");
                    skip(Math.multiplyExact(length, Long.BYTES));
                }
                default -> throw new IllegalArgumentException(
                        "unknown persisted Pillager Outpost NBT payload type");
            }
        }
        void skip(int count) {
            require(count >= 0 && count <= remaining(), "truncated persisted Pillager Outpost piece NBT");
            at += count;
        }
        void end() {
            require(at == bytes.length, "persisted Pillager Outpost piece NBT trailing bytes");
        }
    }

    private static final class PersistedCursor {
        private final byte[] bytes;
        private final String label;
        private int at;
        PersistedCursor(byte[] bytes, String label) {
            this.bytes = Objects.requireNonNull(bytes).clone();
            this.label = Objects.requireNonNull(label);
        }
        int remaining() { return bytes.length - at; }
        int u8() {
            require(at < bytes.length, "truncated " + label);
            return Byte.toUnsignedInt(bytes[at++]);
        }
        int i32() { return (u8() << 24) | (u8() << 16) | (u8() << 8) | u8(); }
        long i64() {
            return ((long) u8() << 56) | ((long) u8() << 48) | ((long) u8() << 40)
                    | ((long) u8() << 32) | ((long) u8() << 24) | ((long) u8() << 16)
                    | ((long) u8() << 8) | u8();
        }
        String fixedAscii(int length) {
            require(length >= 0 && length <= remaining(), "truncated " + label);
            byte[] value = Arrays.copyOfRange(bytes, at, at + length);
            at += length;
            return new String(value, StandardCharsets.US_ASCII);
        }
        String string() {
            int length = i32();
            require(length >= 0 && length <= remaining(), "invalid " + label + " string length");
            byte[] value = Arrays.copyOfRange(bytes, at, at + length);
            at += length;
            return new String(value, StandardCharsets.UTF_8);
        }
        int count(int max, String what) {
            int value = i32();
            require(value >= 0 && value <= max, "invalid " + label + " " + what + " count");
            return value;
        }
        void skip(int count) {
            require(count >= 0 && count <= remaining(), "truncated " + label);
            at += count;
        }
        void end() { require(at == bytes.length, label + " trailing bytes"); }
    }

    private record PersistedPreflight(Plan plan, PersistedStart rawSuccessor,
            Mc263StructureCarrier carrierSuccessor) { }

    private record PersistedAuthorityPlan(long worldSeed, int chunkX, int chunkZ, Bounds aggregate,
            List<PersistedPieceFact> pieces) {
        PersistedAuthorityPlan {
            Objects.requireNonNull(aggregate);
            pieces = List.copyOf(pieces);
        }
    }

    private record PersistedPieceFact(ElementType type, String elementKey,
            List<String> components, String declaredProcessor, int originX, int originY,
            int originZ, int groundLevelDelta, Rotation rotation, Projection projection,
            Bounds bounds, List<PersistedTemplateFact> templates,
            List<PersistedJunctionFact> junctions) {
        PersistedPieceFact {
            Objects.requireNonNull(type); Objects.requireNonNull(elementKey);
            components = List.copyOf(components); Objects.requireNonNull(declaredProcessor);
            Objects.requireNonNull(rotation); Objects.requireNonNull(projection);
            Objects.requireNonNull(bounds); templates = List.copyOf(templates);
            junctions = List.copyOf(junctions);
        }
    }

    private record PersistedTemplateFact(String templateKey, String processor) {
        PersistedTemplateFact {
            Objects.requireNonNull(templateKey); Objects.requireNonNull(processor);
        }
    }

    private record PersistedJunctionFact(int sourceX, int sourceGroundY, int sourceZ, int deltaY,
            Projection destinationProjection) {
        PersistedJunctionFact { Objects.requireNonNull(destinationProjection); }
    }

    private record PersistedPieceNbt(int originX, int originY, int originZ, Bounds bounds,
            ElementType type, Rotation rotation, Projection projection, int groundLevelDelta,
            List<PersistedTemplateFact> templates, List<PersistedJunctionFact> junctions) {
        PersistedPieceNbt {
            Objects.requireNonNull(bounds); Objects.requireNonNull(type);
            Objects.requireNonNull(rotation); Objects.requireNonNull(projection);
            templates = List.copyOf(templates); junctions = List.copyOf(junctions);
        }
    }

    private record PersistedPoolNbt(ElementType type, Projection projection,
            List<PersistedTemplateFact> templates) {
        PersistedPoolNbt {
            Objects.requireNonNull(type); Objects.requireNonNull(projection);
            templates = List.copyOf(templates);
        }
    }

    private static final class PreparedPiece {
        private final int ordinal, originX, originY, originZ; private final Rotation rotation;
        private final Projection projection; private final List<PreparedTemplate> templates;
        PreparedPiece(int ordinal, int originX, int originY, int originZ, Rotation rotation,
                Projection projection, List<PreparedTemplate> templates) {
            this.ordinal = ordinal; this.originX = originX; this.originY = originY; this.originZ = originZ;
            this.rotation = Objects.requireNonNull(rotation); this.projection = Objects.requireNonNull(projection);
            this.templates = List.copyOf(templates);
        }
    }
    private static final class Plan {
        private final List<PreparedPiece> pieces; private final Set<String> exactStates;
        private final StartAuthority startAuthority;
        Plan(List<PreparedPiece> pieces, Set<String> exactStates, StartAuthority startAuthority) {
            this.pieces = List.copyOf(pieces); this.exactStates = Set.copyOf(exactStates);
            this.startAuthority = Objects.requireNonNull(startAuthority);
        }
    }

    private static final class EntityIdentity {
        private final String key; private final long[] positionBits; private final String payloadSha;
        EntityIdentity(String key, long[] positionBits, String payloadSha) {
            this.key = Objects.requireNonNull(key); this.positionBits = positionBits.clone();
            this.payloadSha = Objects.requireNonNull(payloadSha);
        }
        @Override public boolean equals(Object other) {
            return other instanceof EntityIdentity value && key.equals(value.key)
                    && Arrays.equals(positionBits, value.positionBits) && payloadSha.equals(value.payloadSha);
        }
        @Override public int hashCode() {
            return 31 * (31 * key.hashCode() + Arrays.hashCode(positionBits)) + payloadSha.hashCode();
        }
    }
    private static final class LoadedBlockEntity {
        private final Vec position; private final String state; private final BlockEntitySpec spec;
        private final long lootSeed; private final byte[] canonicalNbt; private final int encounterOrder;
        LoadedBlockEntity(Vec position, String state, BlockEntitySpec spec, long lootSeed,
                byte[] canonicalNbt, int encounterOrder) {
            this.position = position; this.state = state; this.spec = spec; this.lootSeed = lootSeed;
            this.canonicalNbt = canonicalNbt.clone(); this.encounterOrder = encounterOrder;
        }
    }
    private enum Direction {
        UP(0,1,0), NORTH(0,0,-1), EAST(1,0,0), SOUTH(0,0,1), WEST(-1,0,0);
        private final int dx,dy,dz; Direction(int dx,int dy,int dz){this.dx=dx;this.dy=dy;this.dz=dz;}
    }
}
