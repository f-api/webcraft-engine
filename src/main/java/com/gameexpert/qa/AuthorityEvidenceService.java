package com.gameexpert.qa;

import com.gameexpert.engine.persistence.finalcarrier.FinalCarrierConsumedTick;
import com.gameexpert.engine.persistence.finalcarrier.FinalCarrierConsumedTickRepository;
import com.gameexpert.engine.persistence.finalcarrier.FinalCarrierLaneMutation;
import com.gameexpert.engine.persistence.finalcarrier.FinalCarrierLaneMutationRepository;
import com.gameexpert.engine.persistence.finalcarrier.FinalCarrierScheduledTick;
import com.gameexpert.engine.persistence.finalcarrier.FinalCarrierScheduledTickRepository;
import com.gameexpert.engine.persistence.finalcarrier.loot.CanonicalLootAssignmentPlan;
import com.gameexpert.engine.persistence.finalcarrier.loot.WorldCanonicalLootAssignment;
import com.gameexpert.engine.persistence.finalcarrier.loot.WorldCanonicalLootAssignmentRepository;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.StructureEntityAggregate;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldStructureEntity;
import com.gameexpert.engine.persistence.finalcarrier.structureentity.WorldStructureEntityRepository;
import com.gameexpert.engine.persistence.tick.FinalCarrierTickScheduler;
import com.gameexpert.terrain.Blocks;
import com.gameexpert.authority.versioned.NeutralFinalChunk;
import com.gameexpert.authority.versioned.ProducerAuthorities;
import com.gameexpert.terrain.mc.structure.Mc263StructureCarrier;
import com.gameexpert.terrain.persistence.CanonicalWorldgenStore;
import com.gameexpert.api.persistence.WorldStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Read-only, QA-gated projection of durable runtime parity evidence. */
@Service
public class AuthorityEvidenceService {
    public static final String ABI = "SCOR2631";
    public static final int SCHEMA = 1;
    public static final int SCHEMA_V2 = 2;
    public static final int ENTITY_SCHEMA_V3 = 3;

    private static final String GENERATION_ONE_FIXTURE_DIGEST =
            "6905fd04fae55137744a8a298ae8e22afbb71824918078791a6bbf09e33701e8";
    private static final String GENERATION_TWO_FIXTURE_DIGEST =
            "f0d5839457e36bd945b88ac0c5d716d8c49718cad0b5cca68b574903d2528112";
    private static final byte[] CANONICAL_CHEST_MINECART_PAYLOAD =
            {0x43, 0x4d, 0x45, 0x32, 0x36, 0x33, 0x45, 0x31};
    private static final FinalCarrierTickScheduler.AtomicPersistence CANDIDATE_PERSISTENCE =
            new FinalCarrierTickScheduler.AtomicPersistence() {
                @Override
                public FinalCarrierTickScheduler.Admission admitAndAcknowledge(
                        FinalCarrierTickScheduler.CarrierReceipt receipt,
                        List<FinalCarrierTickScheduler.ScheduledTick> candidates,
                        long admissionBaseMcTick, int capacity) {
                    throw new AssertionError("evidence candidate scheduler must not persist");
                }

                @Override
                public List<FinalCarrierTickScheduler.ScheduledTick> loadWorld(long worldId) {
                    throw new AssertionError("evidence candidate scheduler must not load");
                }

                @Override
                public List<FinalCarrierTickScheduler.ScheduledTick> loadChunk(
                        long worldId, int chunkX, int chunkZ) {
                    throw new AssertionError("evidence candidate scheduler must not load");
                }

                @Override
                public FinalCarrierTickScheduler.Settlement settleAtomically(
                        FinalCarrierTickScheduler.ScheduledTick tick,
                        FinalCarrierTickScheduler.DueDisposition disposition,
                        FinalCarrierTickScheduler.TickMutation mutation) {
                    throw new AssertionError("evidence candidate scheduler must not settle");
                }
            };
    private static final Comparator<FinalCarrierTickScheduler.ScheduledTick> TICK_ORDER =
            Comparator.comparing(FinalCarrierTickScheduler.ScheduledTick::lane)
                    .thenComparingLong(FinalCarrierTickScheduler.ScheduledTick::dueTick)
                    .thenComparingInt(tick -> tick.priority().value())
                    .thenComparingLong(FinalCarrierTickScheduler.ScheduledTick::subTickOrder)
                    .thenComparingLong(FinalCarrierTickScheduler.ScheduledTick::durableOrder)
                    .thenComparingInt(tick -> tick.receipt().chunkX())
                    .thenComparingInt(tick -> tick.receipt().chunkZ())
                    .thenComparingInt(FinalCarrierTickScheduler.ScheduledTick::x)
                    .thenComparingInt(FinalCarrierTickScheduler.ScheduledTick::y)
                    .thenComparingInt(FinalCarrierTickScheduler.ScheduledTick::z)
                    .thenComparing(FinalCarrierTickScheduler.ScheduledTick::typeKey);

    private final WorldStore worlds;
    private final CanonicalWorldgenStore canonicalWorldgen;
    private final WorldCanonicalLootAssignmentRepository lootAssignments;
    private final FinalCarrierScheduledTickRepository scheduledTicks;
    private final FinalCarrierConsumedTickRepository consumedTicks;
    private final FinalCarrierLaneMutationRepository laneMutations;
    private final WorldStructureEntityRepository structureEntities;

    public AuthorityEvidenceService(WorldStore worlds, CanonicalWorldgenStore canonicalWorldgen,
            WorldCanonicalLootAssignmentRepository lootAssignments,
            FinalCarrierScheduledTickRepository scheduledTicks,
            FinalCarrierConsumedTickRepository consumedTicks,
            FinalCarrierLaneMutationRepository laneMutations,
            WorldStructureEntityRepository structureEntities) {
        this.worlds = Objects.requireNonNull(worlds, "world repository");
        this.canonicalWorldgen = Objects.requireNonNull(canonicalWorldgen, "canonical worldgen store");
        this.lootAssignments = Objects.requireNonNull(lootAssignments, "loot assignments");
        this.scheduledTicks = Objects.requireNonNull(scheduledTicks, "scheduled ticks");
        this.consumedTicks = Objects.requireNonNull(consumedTicks, "consumed ticks");
        this.laneMutations = Objects.requireNonNull(laneMutations, "final-carrier mutations");
        this.structureEntities = Objects.requireNonNull(
                structureEntities, "structure-entity receipts");
    }

    /** Language-neutral fixture used to bind the Spring and browser schema tests. */
    public static Map<String, Object> schemaFixture() {
        return map("schema", SCHEMA, "abi", ABI, "seed", 0,
                "coordinates", map("minChunkX", -54, "minChunkZ", 222,
                        "maxChunkX", -54, "maxChunkZ", 222),
                "structures", List.of(), "bent", domain(List.of()),
                "ents", entityDomain(List.of()), "ticks", domain(List.of()), "loot", domain(List.of()),
                "reconnect", map("schema", SCHEMA, "canonicalChunks", List.of()),
                "eviction", map("schema", SCHEMA, "canonicalChunks", List.of()));
    }

    /** Language-neutral root-schema-2 fixture using the shared durable ENTS domain. */
    public static Map<String, Object> schemaV2Fixture() {
        return map("schema", SCHEMA_V2, "abi", ABI, "revision", 11L, "seed", 0,
                "nickname", "AuthorityQA",
                "coordinates", map("minChunkX", -54, "minChunkZ", 222,
                        "maxChunkX", -54, "maxChunkZ", 222),
                "lifecycle", map("schema", SCHEMA, "revision", 11L, "events", List.of(
                        lifecycleEvent("1", 2L, "session-joined", 1, GENERATION_ONE_FIXTURE_DIGEST, null, null),
                        lifecycleEvent("2", 3L, "chunk-activated", 1, GENERATION_ONE_FIXTURE_DIGEST, -54, 222),
                        lifecycleEvent("3", 4L, "player-moved", 1, GENERATION_ONE_FIXTURE_DIGEST, -54, 222),
                        lifecycleEvent("4", 5L, "chunk-activated", 1, GENERATION_ONE_FIXTURE_DIGEST, 1, 186),
                        lifecycleEvent("5", 6L, "player-moved", 1, GENERATION_ONE_FIXTURE_DIGEST, 1, 186),
                        lifecycleEvent("6", 7L, "chunk-evicted", 1, GENERATION_ONE_FIXTURE_DIGEST, -54, 222),
                        lifecycleEvent("7", 8L, "session-left", 1, GENERATION_ONE_FIXTURE_DIGEST, null, null),
                        lifecycleEvent("8", 9L, "session-joined", 2, GENERATION_TWO_FIXTURE_DIGEST, null, null),
                        lifecycleEvent("9", 10L, "chunk-activated", 2, GENERATION_TWO_FIXTURE_DIGEST, -54, 222),
                        lifecycleEvent("10", 11L, "capture", 2, GENERATION_TWO_FIXTURE_DIGEST, null, null))),
                "structures", List.of(), "bent", domain(List.of()),
                "ents", entityDomain(List.of()), "ticks", domain(List.of()), "loot", domain(List.of()),
                "reconnect", map("schema", SCHEMA_V2, "revision", 10L, "sourceSequence", "9",
                        "canonicalChunks", List.of()),
                "eviction", map("schema", SCHEMA_V2, "revision", 7L, "sourceSequence", "6",
                        "canonicalChunks", List.of()));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> capture(long worldId, int minChunkX, int minChunkZ,
            int maxChunkX, int maxChunkZ) {
        validateWindow(minChunkX, minChunkZ, maxChunkX, maxChunkZ);
        var world = worlds.findById(worldId).orElseThrow();
        ArrayList<CanonicalWorldgenStore.CanonicalChunkSnapshot> chunks = readCanonicalChunks(
                worldId, minChunkX, minChunkZ, maxChunkX, maxChunkZ);
        ArrayList<Map<String, Object>> structures = new ArrayList<>();
        ArrayList<StartFact> starts = new ArrayList<>();
        LinkedHashMap<String, Mc263StructureCarrier.ChunkStarts> startChunks =
                new LinkedHashMap<>();
        LinkedHashMap<String, Mc263StructureCarrier.ChunkReferences> referenceChunks =
                new LinkedHashMap<>();
        for (var snapshot : chunks) {
            Mc263StructureCarrier carrier = Mc263StructureCarrier.decode(
                    snapshot.commit().structureCarrier());
            for (var startChunk : carrier.startChunks()) {
                String key = startChunk.chunkX() + ":" + startChunk.chunkZ();
                var previous = startChunks.putIfAbsent(key, startChunk);
                if (previous != null && !previous.equals(startChunk)) {
                    throw malformedSnapshot("conflicting overlapping structure-start chunk");
                }
            }
            for (var referenceChunk : carrier.referenceChunks()) {
                String key = referenceChunk.chunkX() + ":" + referenceChunk.chunkZ();
                var previous = referenceChunks.putIfAbsent(key, referenceChunk);
                if (previous != null && !previous.equals(referenceChunk)) {
                    throw malformedSnapshot("conflicting overlapping structure-reference chunk");
                }
            }
        }
        LinkedHashMap<String, Map<String, Object>> structureChunks = new LinkedHashMap<>();
        for (var keyed : startChunks.entrySet()) {
            var startChunk = keyed.getValue();
            ArrayList<Map<String, Object>> entries = new ArrayList<>();
            for (var entry : startChunk.orderedStarts()) {
                if (!(entry.body() instanceof Mc263StructureCarrier.ValidStart valid)) {
                    entries.add(map("structureId", entry.structureId(),
                            "valid", false, "startKey", null,
                            "originChunkX", null, "originChunkZ", null,
                            "references", null, "boundingBox", null, "pieceCount", 0));
                    continue;
                }
                var box = valid.adjustedBoundingBox();
                starts.add(new StartFact(entry.structureId(), box));
                entries.add(map(
                        "structureId", entry.structureId(), "valid", true,
                        "startKey", valid.startKey(),
                        "originChunkX", valid.originChunkX(), "originChunkZ", valid.originChunkZ(),
                        "references", valid.references(),
                        "boundingBox", List.of(box.minX(), box.minY(), box.minZ(),
                                box.maxX(), box.maxY(), box.maxZ()),
                        "pieceCount", valid.orderedPieces().size()));
            }
            structureChunks.put(keyed.getKey(), map(
                    "chunkX", startChunk.chunkX(), "chunkZ", startChunk.chunkZ(),
                    "starts", entries, "references", List.of()));
        }
        for (var keyed : referenceChunks.entrySet()) {
            var referenceChunk = keyed.getValue();
            ArrayList<Map<String, Object>> references = new ArrayList<>();
            for (var set : referenceChunk.orderedSets()) references.add(map(
                    "structureId", set.structureId(),
                    "origins", set.orderedOrigins().stream().map(String::valueOf).toList()));
            Map<String, Object> existing = structureChunks.get(keyed.getKey());
            structureChunks.put(keyed.getKey(), map(
                    "chunkX", referenceChunk.chunkX(), "chunkZ", referenceChunk.chunkZ(),
                    "starts", existing == null ? List.of() : existing.get("starts"),
                    "references", references));
        }
        structures.addAll(structureChunks.values());

        ArrayList<Map<String, Object>> bent = new ArrayList<>();
        ArrayList<Map<String, Object>> entityChunks = new ArrayList<>();
        for (var snapshot : chunks) {
            var chunk = decodedChunk(snapshot);
            int chunkX = chunk.chunkX(); int chunkZ = chunk.chunkZ();
            for (var entry : chunk.sidecars().blockEntities()) {
                int[] position = position(chunkX, chunkZ, entry.packed());
                bent.add(map("chunkX", chunkX, "chunkZ", chunkZ, "packed", entry.packed(),
                        "x", position[0], "y", position[1], "z", position[2],
                        "blockIdentity", entry.blockIdentity(), "entityType", entry.entityType(),
                        "canonicalNbtSha256", sha256(entry.canonicalNbt())));
            }
            entityChunks.add(projectEntityChunk(worldId, snapshot, chunk, starts));
        }

        var ticks = projectTicks(worldId, chunks);
        var loot = projectLoot(worldId, world.getSeed(), chunks,
                minChunkX, minChunkZ, maxChunkX, maxChunkZ);
        var durableChunks = chunks.stream().map(snapshot -> map(
                "chunkX", snapshot.commit().chunkX(), "chunkZ", snapshot.commit().chunkZ(),
                "finalCarrierSha256", sha256(snapshot.commit().finalCarrier()),
                "structureCarrierSha256", sha256(snapshot.commit().structureCarrier()),
                "laneClaimMask", snapshot.laneClaimMask(), "laneAckMask", snapshot.laneAckMask(),
                "laneRejectedMask", snapshot.laneRejectedMask())).toList();

        return map("schema", SCHEMA, "abi", ABI, "seed", Math.toIntExact(world.getSeed()),
                "coordinates", map("minChunkX", minChunkX, "minChunkZ", minChunkZ,
                        "maxChunkX", maxChunkX, "maxChunkZ", maxChunkZ),
                "structures", structures,
                "bent", domain(bent), "ents", entityDomain(entityChunks), "ticks", domain(ticks),
                "loot", domain(loot),
                "reconnect", map("schema", SCHEMA, "canonicalChunks", durableChunks),
                "eviction", map("schema", SCHEMA, "canonicalChunks", durableChunks));
    }

    /** Reads only durable canonical rows and projects their byte identities and lane state. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<AuthorityEvidenceJournal.CanonicalChunk> projectCanonicalChunks(
            long worldId, AuthorityEvidenceJournal.Window window) {
        if (worldId <= 0) throw new IllegalArgumentException("world ID must be positive");
        Objects.requireNonNull(window, "authority evidence window");
        ArrayList<AuthorityEvidenceJournal.CanonicalChunk> projected = new ArrayList<>();
        for (var snapshot : readCanonicalChunks(worldId, window.minChunkX(), window.minChunkZ(),
                window.maxChunkX(), window.maxChunkZ())) {
            var commit = snapshot.commit();
            projected.add(new AuthorityEvidenceJournal.CanonicalChunk(
                    commit.chunkX(), commit.chunkZ(), sha256(commit.finalCarrier()),
                    sha256(commit.structureCarrier()), snapshot.laneClaimMask(),
                    snapshot.laneAckMask(), snapshot.laneRejectedMask()));
        }
        return List.copyOf(projected);
    }

    private ArrayList<CanonicalWorldgenStore.CanonicalChunkSnapshot> readCanonicalChunks(
            long worldId, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        ArrayList<CanonicalWorldgenStore.CanonicalChunkSnapshot> chunks = new ArrayList<>();
        Set<CanonicalWorldgenStore.CanonicalChunkSnapshot> seenSnapshots =
                Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> seenCoordinates = new HashSet<>();
        for (long chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (long chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                var snapshot = canonicalWorldgen.find(worldId, (int) chunkX, (int) chunkZ);
                if (snapshot == null) continue;
                if (!seenSnapshots.add(snapshot)) {
                    throw malformedSnapshot("duplicate canonical chunk snapshot");
                }
                var commit = snapshot.commit();
                if (commit == null || commit.worldId() != worldId
                        || commit.chunkX() != (int) chunkX
                        || commit.chunkZ() != (int) chunkZ) {
                    throw malformedSnapshot("foreign canonical chunk snapshot");
                }
                String coordinate = chunkX + ":" + chunkZ;
                if (!seenCoordinates.add(coordinate)) {
                    throw malformedSnapshot("duplicate canonical chunk coordinate");
                }
                chunks.add(snapshot);
            }
        }
        return chunks;
    }

    private static NeutralFinalChunk decodedChunk(
            CanonicalWorldgenStore.CanonicalChunkSnapshot snapshot) {
        var commit = Objects.requireNonNull(snapshot, "canonical chunk snapshot").commit();
        if (commit == null) throw malformedSnapshot("canonical chunk has no commit");
        var decoded = commit.semanticFinalChunk();
        if (decoded == null || decoded.chunkX() != commit.chunkX()
                || decoded.chunkZ() != commit.chunkZ()) {
            throw malformedSnapshot("decoded final carrier coordinates differ from its commit");
        }
        return decoded;
    }

    private static Pageable boundedRowLimit(long candidateCount) {
        if (candidateCount < 0L) {
            throw new IllegalStateException("negative authenticated sidecar count");
        }
        final long limit;
        try {
            limit = Math.addExact(candidateCount, 1L);
        } catch (ArithmeticException overflow) {
            throw new IllegalStateException("authenticated sidecar count exceeds the pageable bound",
                    overflow);
        }
        if (limit > Integer.MAX_VALUE) {
            throw new IllegalStateException("authenticated sidecar count exceeds the pageable bound");
        }
        return PageRequest.of(0, Math.toIntExact(limit));
    }

    private List<Map<String, Object>> projectTicks(long worldId,
            List<CanonicalWorldgenStore.CanonicalChunkSnapshot> chunks) {
        var incompleteScheduled = scheduledTicks.findIncompleteLegacyRowsByWorldId(
                worldId, PageRequest.of(0, 1));
        var incompleteConsumed = consumedTicks.findIncompleteLegacyRowsByWorldId(
                worldId, PageRequest.of(0, 1));
        if (incompleteScheduled == null || incompleteConsumed == null) {
            throw malformedTicks("legacy-row preflight query returned null");
        }
        if (!incompleteScheduled.isEmpty() || !incompleteConsumed.isEmpty()) {
            throw malformedTicks("legacy scheduled or consumed tick row is incomplete");
        }
        ArrayList<TickEvidence> projected = new ArrayList<>();
        var profile = worlds.findById(worldId).orElseThrow().generationProfile();
        FinalCarrierTickScheduler candidateScheduler = new FinalCarrierTickScheduler(
                worldId, CANDIDATE_PERSISTENCE, blockId -> ProducerAuthorities.defaultState(profile, blockId));
        for (var snapshot : chunks) {
            NeutralFinalChunk source = decodedChunk(snapshot);
            projected.addAll(readTickLane(worldId, candidateScheduler, source,
                    FinalCarrierTickScheduler.Lane.BLOCK));
            projected.addAll(readTickLane(worldId, candidateScheduler, source,
                    FinalCarrierTickScheduler.Lane.FLUID));
        }
        Set<String> durableOrders = new HashSet<>();
        for (TickEvidence evidence : projected) {
            FinalCarrierTickScheduler.ScheduledTick tick = evidence.tick();
            if (!durableOrders.add(tick.lane().name() + ":" + tick.durableOrder())) {
                throw malformedTicks("duplicate durable order in a lane");
            }
        }
        projected.sort(Comparator.comparing(TickEvidence::tick, TICK_ORDER));
        return projected.stream().map(AuthorityEvidenceService::projectTick).toList();
    }

    private List<TickEvidence> readTickLane(long worldId,
            FinalCarrierTickScheduler candidateScheduler,
            NeutralFinalChunk source, FinalCarrierTickScheduler.Lane lane) {
        String sourceFingerprint = finalCarrierSourceFingerprint(source);
        FinalCarrierTickScheduler.CarrierReceipt expected = lane == FinalCarrierTickScheduler.Lane.BLOCK
                ? FinalCarrierTickScheduler.blockReceipt(worldId,
                        source.chunkX(), source.chunkZ(),
                        sourceFingerprint, source.sidecars().blockTicks())
                : FinalCarrierTickScheduler.fluidReceipt(worldId,
                        source.chunkX(), source.chunkZ(),
                        sourceFingerprint, source.sidecars().fluidTicks());
        FinalCarrierTickScheduler.PreparedAdmission prepared;
        try {
            prepared = lane == FinalCarrierTickScheduler.Lane.BLOCK
                    ? candidateScheduler.prepareBlockLane(expected, 0L, true,
                            source.sidecars().blockTicks())
                    : candidateScheduler.prepareFluidLane(expected, 0L, true,
                            source.sidecars().fluidTicks());
        } catch (RuntimeException malformed) {
            throw malformedTicks("decoded " + lane + " sidecar is not a supported candidate set",
                    malformed);
        }
        List<FinalCarrierTickScheduler.ScheduledTick> candidates = prepared.candidates();
        Pageable limit = boundedRowLimit(candidates.size());
        List<FinalCarrierScheduledTick> rows = scheduledTicks
                .findAllByWorldIdAndChunkXAndChunkZAndLaneOrderByDurableOrderAscIdAsc(
                        expected.worldId(), source.chunkX(), source.chunkZ(), lane.name(), limit);
        List<FinalCarrierConsumedTick> consumed = consumedTicks
                .findAllByWorldIdAndChunkXAndChunkZAndLaneAndPublicationStateInOrderByDurableOrderAscIdAsc(
                        expected.worldId(), source.chunkX(), source.chunkZ(), lane.name(),
                        Set.of(FinalCarrierConsumedTick.PublicationState.values()), limit);
        if (rows == null || consumed == null) {
            throw malformedTicks("durable " + lane + " row query returned null");
        }
        if (rows.size() > candidates.size() || consumed.size() > candidates.size()
                || (long) rows.size() + consumed.size() > candidates.size()) {
            throw malformedTicks("canonical " + lane + " lane returned an overflow sentinel");
        }
        Map<FinalCarrierTickScheduler.TickKey, FinalCarrierTickScheduler.ScheduledTick> expectedByKey =
                new HashMap<>();
        for (FinalCarrierTickScheduler.ScheduledTick candidate : candidates) {
            if (expectedByKey.put(candidate.key(), candidate) != null) {
                throw malformedTicks("decoded " + lane + " sidecar contains duplicate first-winner keys");
            }
        }
        ArrayList<TickEvidence> projected = new ArrayList<>(candidates.size());
        Set<FinalCarrierTickScheduler.TickKey> seenKeys = new HashSet<>();
        long previousDurableOrder = 0L;
        for (FinalCarrierScheduledTick row : rows) {
            FinalCarrierTickScheduler.ScheduledTick tick;
            try {
                tick = row.toScheduledTick();
            } catch (RuntimeException malformed) {
                throw malformedTicks("durable " + lane + " row cannot reconstruct its receipt", malformed);
            }
            if (tick == null || !expected.equals(tick.receipt())) {
                throw malformedTicks("durable " + lane
                        + " row source or payload identity differs from its carrier");
            }
            FinalCarrierTickScheduler.ScheduledTick candidate = expectedByKey.get(tick.key());
            if (candidate == null || !sameTickCandidateIdentity(tick, candidate)) {
                throw malformedTicks("durable " + lane
                        + " row is not an authenticated first-winner candidate");
            }
            if (tick.durableOrder() <= previousDurableOrder) {
                throw malformedTicks("durable " + lane + " rows are not in strict durable order");
            }
            if (!seenKeys.add(tick.key())) {
                throw malformedTicks("durable " + lane + " rows contain a duplicate key");
            }
            previousDurableOrder = tick.durableOrder();
            projected.add(new TickEvidence(tick, "SCHEDULED", null));
        }
        previousDurableOrder = 0L;
        for (FinalCarrierConsumedTick row : consumed) {
            if (row == null || row.getState() == null || !row.hasCompletePublication()) {
                throw malformedTicks("consumed " + lane + " row is incomplete");
            }
            FinalCarrierTickScheduler.ScheduledTick tick;
            FinalCarrierTickScheduler.DueDisposition disposition;
            try {
                tick = row.toScheduledTick();
                disposition = row.parsedDisposition();
            } catch (RuntimeException malformed) {
                throw malformedTicks("consumed " + lane
                        + " row cannot reconstruct its candidate", malformed);
            }
            FinalCarrierTickScheduler.ScheduledTick candidate = expectedByKey.get(tick.key());
            if (candidate == null || !sameTickCandidateIdentity(tick, candidate)) {
                throw malformedTicks("consumed " + lane
                        + " row is not an authenticated first-winner candidate");
            }
            if (tick.durableOrder() <= previousDurableOrder) {
                throw malformedTicks("consumed " + lane + " rows are not in strict durable order");
            }
            if (!seenKeys.add(tick.key())) {
                throw malformedTicks("scheduled and consumed " + lane
                        + " rows contain a duplicate key");
            }
            previousDurableOrder = tick.durableOrder();
            projected.add(new TickEvidence(tick, "CONSUMED", disposition.name()));
        }
        if (seenKeys.size() != candidates.size()) {
            throw malformedTicks("scheduled and consumed " + lane
                    + " rows do not completely cover authenticated candidates");
        }
        return List.copyOf(projected);
    }

    private static boolean sameTickCandidateIdentity(
            FinalCarrierTickScheduler.ScheduledTick durable,
            FinalCarrierTickScheduler.ScheduledTick candidate) {
        return durable.receipt().equals(candidate.receipt())
                && durable.key().equals(candidate.key())
                && durable.expectedBlockId() == candidate.expectedBlockId()
                && durable.priority() == candidate.priority()
                && durable.subTickOrder() == candidate.subTickOrder();
    }

    private List<Map<String, Object>> projectLoot(long worldId, long worldSeed,
            List<CanonicalWorldgenStore.CanonicalChunkSnapshot> chunks,
            int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        ArrayList<WorldCanonicalLootAssignment> expected = new ArrayList<>();
        Set<LootCell> expectedCells = new HashSet<>();
        long candidateCount = 0L;
        for (var snapshot : chunks) {
            var source = decodedChunk(snapshot);
            List<NeutralFinalChunk.Loot> candidates = source.sidecars().loot();
            try {
                candidateCount = Math.addExact(candidateCount, candidates.size());
            } catch (ArithmeticException overflow) {
                throw malformedLoot("authenticated LOOT sidecar count overflow", overflow);
            }
            int bit = CanonicalWorldgenStore.Lane.LOOT.mask();
            boolean claimed = (snapshot.laneClaimMask() & bit) != 0;
            boolean acknowledged = (snapshot.laneAckMask() & bit) != 0;
            boolean rejected = (snapshot.laneRejectedMask() & bit) != 0;
            if (acknowledged && rejected || (acknowledged || rejected) && !claimed
                    || candidates.isEmpty() == claimed) {
                throw malformedLoot("carrier and LOOT lane masks disagree");
            }
            Pageable historyLimit = PageRequest.of(0, candidates.isEmpty() ? 1 : 2);
            List<FinalCarrierLaneMutation> mutations = laneMutations
                    .findAllByWorldIdAndChunkXAndChunkZAndLaneOrderById(
                            worldId, source.chunkX(), source.chunkZ(),
                            CanonicalWorldgenStore.Lane.LOOT.name(), historyLimit);
            if (mutations == null) {
                throw malformedLoot("durable LOOT mutation query returned null");
            }
            if (candidates.isEmpty()) {
                if (!mutations.isEmpty()) {
                    throw malformedLoot("empty carrier owns durable LOOT mutation residue");
                }
                continue;
            }
            if (!acknowledged || rejected || mutations.size() != 1) {
                throw malformedLoot("LOOT carrier lacks one complete installed mutation");
            }
            FinalCarrierLaneMutation mutation = mutations.getFirst();
            if (mutation == null
                    || mutation.getActivationStatus()
                            != FinalCarrierLaneMutation.ActivationStatus.INSTALLED
                    || !matchingLootMutation(mutation, worldId, source)) {
                throw malformedLoot("installed LOOT mutation differs from its carrier");
            }
            final CanonicalLootAssignmentPlan plan;
            try {
                NeutralFinalChunk typed = source.verifyCarrier(mutation.getTypedPayload());
                plan = CanonicalLootAssignmentPlan.prepare(
                        worldSeed, mutation, source, typed.sidecars());
            } catch (RuntimeException invalid) {
                throw malformedLoot("installed LOOT mutation cannot reconstruct its plan", invalid);
            }
            if (plan.assignments().size() != candidates.size()) {
                throw malformedLoot("installed LOOT plan is incomplete");
            }
            for (WorldCanonicalLootAssignment assignment : plan.assignments()) {
                LootCell cell = new LootCell(assignment.getChunkX(), assignment.getChunkZ(),
                        assignment.getPacked());
                if (!expectedCells.add(cell)) {
                    throw malformedLoot("decoded LOOT sidecar contains a duplicate packed position");
                }
                expected.add(assignment);
            }
        }
        expected.sort(Comparator.comparingInt(WorldCanonicalLootAssignment::getChunkX)
                .thenComparingInt(WorldCanonicalLootAssignment::getChunkZ)
                .thenComparingInt(WorldCanonicalLootAssignment::getPacked));

        List<WorldCanonicalLootAssignment> rows = lootAssignments
                .findAllByWorldIdAndChunkXBetweenAndChunkZBetweenOrderByChunkXAscChunkZAscPackedAscIdAsc(
                        worldId, minChunkX, maxChunkX, minChunkZ, maxChunkZ,
                        boundedRowLimit(candidateCount));
        if (rows == null) throw malformedLoot("durable LOOT row query returned null");
        if ((long) rows.size() != candidateCount || rows.size() != expected.size()) {
            throw malformedLoot("canonical LOOT rows do not completely cover the installed plan");
        }

        Set<LootCell> seen = new HashSet<>();
        ArrayList<Map<String, Object>> projected = new ArrayList<>(rows.size());
        WorldCanonicalLootAssignment previous = null;
        for (int index = 0; index < rows.size(); index++) {
            WorldCanonicalLootAssignment row = rows.get(index);
            if (row == null || row.getId() == null || row.getStatus() == null) {
                throw malformedLoot("durable LOOT row is incomplete");
            }
            LootCell cell = new LootCell(row.getChunkX(), row.getChunkZ(), row.getPacked());
            if (!seen.add(cell)) {
                throw malformedLoot("durable LOOT row is foreign or duplicated");
            }
            if (previous != null && compareLootOrder(previous, row) >= 0) {
                throw malformedLoot("durable LOOT rows are not in strict spatial order");
            }
            previous = row;
            WorldCanonicalLootAssignment candidate = expected.get(index);
            if (!row.sameDefinition(candidate)) {
                throw malformedLoot("durable LOOT row differs from its installed plan");
            }
            WorldCanonicalLootAssignment.Status status = row.getStatus();
            String resultFingerprint = row.getResultFingerprint();
            if (status == WorldCanonicalLootAssignment.Status.RESOLVED) {
                try {
                    row.requireStoredResolution();
                } catch (RuntimeException invalid) {
                    throw malformedLoot("resolved LOOT row has no complete stored result", invalid);
                }
                if (!isSha256(resultFingerprint)) {
                    throw malformedLoot("resolved LOOT row has no valid result digest");
                }
            } else if (resultFingerprint != null || row.getResolvedPayload() != null
                    || row.getContinuationKind() != null
                    || row.getContinuationFirst() != null
                    || row.getContinuationSecond() != null) {
                throw malformedLoot("non-resolved LOOT row owns terminal result state");
            }
            projected.add(map("chunkX", cell.chunkX, "chunkZ", cell.chunkZ,
                    "packed", cell.packed, "x", row.getPosX(), "y", row.getPosY(),
                    "z", row.getPosZ(), "table", row.getTableKey(),
                    "rawSeed", Long.toString(row.getRawSeed()), "state", status.name(),
                    "resultSha256", status == WorldCanonicalLootAssignment.Status.RESOLVED
                            ? resultFingerprint : null));
        }
        return List.copyOf(projected);
    }

    private static boolean matchingLootMutation(FinalCarrierLaneMutation mutation, long worldId,
            NeutralFinalChunk source) {
        try {
            String sourceFingerprint = finalCarrierSourceFingerprint(source);
            if (mutation.getWorldId() != worldId || mutation.getChunkX() != source.chunkX()
                    || mutation.getChunkZ() != source.chunkZ()
                    || !CanonicalWorldgenStore.Lane.LOOT.name().equals(mutation.getLane())
                    || !sourceFingerprint.equals(mutation.getSourceFingerprint())) return false;
            byte[] typedBytes = mutation.getTypedPayload();
            if (!mutation.getPayloadFingerprint().equals(sha256(typedBytes))) return false;
            String expectedIdentity = worldId + ":" + source.chunkX() + ":" + source.chunkZ()
                    + ":" + CanonicalWorldgenStore.Lane.LOOT.name() + ":" + sourceFingerprint
                    + ":" + mutation.getPayloadFingerprint();
            if (!expectedIdentity.equals(mutation.getInstallationIdentity())) return false;
            NeutralFinalChunk typed = source.verifyCarrier(typedBytes);
            NeutralFinalChunk.Sidecars sidecars = typed.sidecars();
            return typed.chunkX() == source.chunkX() && typed.chunkZ() == source.chunkZ()
                    && finalCarrierSourceFingerprint(typed).equals(sourceFingerprint)
                    && sidecars.blockTicks().isEmpty() && sidecars.fluidTicks().isEmpty()
                    && sidecars.loot().equals(source.sidecars().loot())
                    && sidecars.spawners().isEmpty() && sidecars.owners().isEmpty()
                    && sidecars.archaeology().isEmpty() && sidecars.bees().isEmpty()
                    && sidecars.blockEntities().isEmpty() && sidecars.entities().isEmpty();
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static int compareLootOrder(WorldCanonicalLootAssignment left,
            WorldCanonicalLootAssignment right) {
        int compared = Integer.compare(left.getChunkX(), right.getChunkX());
        if (compared != 0) return compared;
        compared = Integer.compare(left.getChunkZ(), right.getChunkZ());
        if (compared != 0) return compared;
        compared = Integer.compare(left.getPacked(), right.getPacked());
        if (compared != 0) return compared;
        return Long.compare(left.getId(), right.getId());
    }

    private static Map<String, Object> projectTick(TickEvidence evidence) {
        FinalCarrierTickScheduler.ScheduledTick tick = evidence.tick();
        return map("chunkX", tick.receipt().chunkX(), "chunkZ", tick.receipt().chunkZ(),
                "x", tick.x(), "y", tick.y(), "z", tick.z(),
                "lane", tick.lane().name(), "key", tick.typeKey(),
                "dueTick", Long.toString(tick.dueTick()), "priority", tick.priority().value(),
                "subTickOrder", Long.toString(tick.subTickOrder()),
                "state", evidence.state(), "disposition", evidence.disposition());
    }

    private static IllegalStateException malformedTicks(String message) {
        return new IllegalStateException("malformed durable BLOCK/FLUID evidence: " + message);
    }

    private static IllegalStateException malformedTicks(String message, Throwable cause) {
        return new IllegalStateException("malformed durable BLOCK/FLUID evidence: " + message, cause);
    }

    private static IllegalStateException malformedLoot(String message) {
        return new IllegalStateException("malformed durable LOOT evidence: " + message);
    }

    private static IllegalStateException malformedLoot(String message, Throwable cause) {
        return new IllegalStateException("malformed durable LOOT evidence: " + message, cause);
    }

    private static IllegalStateException malformedSnapshot(String message) {
        return new IllegalStateException("malformed canonical chunk snapshot: " + message);
    }

    private static Map<String, Object> domain(List<?> entries) {
        return map("schema", SCHEMA, "entries", entries);
    }

    private static Map<String, Object> entityDomain(List<?> chunks) {
        return map("schema", ENTITY_SCHEMA_V3, "chunks", chunks);
    }

    private Map<String, Object> projectEntityChunk(long worldId,
            CanonicalWorldgenStore.CanonicalChunkSnapshot snapshot,
            NeutralFinalChunk chunk, List<StartFact> starts) {
        List<NeutralFinalChunk.StructureEntity> sourceEntries = chunk.sidecars().entities();
        int chunkX = chunk.chunkX();
        int chunkZ = chunk.chunkZ();
        int bit = CanonicalWorldgenStore.Lane.ENTITIES.mask();
        boolean claimed = (snapshot.laneClaimMask() & bit) != 0;
        boolean acknowledged = (snapshot.laneAckMask() & bit) != 0;
        boolean rejected = (snapshot.laneRejectedMask() & bit) != 0;
        if (acknowledged && rejected || (acknowledged || rejected) && !claimed
                || sourceEntries.isEmpty() == claimed) {
            throw malformedEntities("carrier and ENTS lane masks disagree");
        }

        Pageable mutationHistoryLimit = PageRequest.of(0, sourceEntries.isEmpty() ? 1 : 2);
        Pageable entityLimit = boundedRowLimit(sourceEntries.size());
        List<FinalCarrierLaneMutation> chunkMutations = laneMutations
                .findAllByWorldIdAndChunkXAndChunkZAndLaneOrderById(
                        worldId, chunkX, chunkZ, CanonicalWorldgenStore.Lane.ENTITIES.name(),
                        mutationHistoryLimit);
        List<WorldStructureEntity> durableRows = structureEntities
                .findEvidenceByWorldIdAndChunkOrderByEncounterOrdinal(
                        worldId, chunkX, chunkZ, entityLimit);
        if (chunkMutations == null || durableRows == null) {
            throw malformedEntities("durable ENTS evidence query returned null");
        }
        if (sourceEntries.isEmpty()) {
            if (!chunkMutations.isEmpty() || !durableRows.isEmpty()) {
                throw malformedEntities("empty carrier owns durable ENTS residue");
            }
            return entityChunk(chunkX, chunkZ, false, false, false, "EMPTY", List.of());
        }
        String sourceFingerprint = finalCarrierSourceFingerprint(chunk);
        if (chunkMutations.size() != 1) {
            throw malformedEntities("current ENTS mutation history or residue is present");
        }
        FinalCarrierLaneMutation mutation = chunkMutations.getFirst();
        if (mutation == null
                || !sourceFingerprint.equals(mutation.getSourceFingerprint())
                || !matchingEntityMutation(mutation, worldId, chunk, sourceFingerprint)) {
            throw malformedEntities("current ENTS mutation is missing or does not match its carrier");
        }
        if (durableRows.stream().anyMatch(row -> !mutation.getInstallationIdentity()
                .equals(row.getLaneInstallationIdentity()))) {
            throw malformedEntities("chunk owns non-current ENTS installation residue");
        }
        String outcome;
        List<StructureEntityAggregate.PlannedEntity> planned;
        if (acknowledged) {
            if (mutation.getActivationStatus() != FinalCarrierLaneMutation.ActivationStatus.INSTALLED
                    || durableRows.size() != sourceEntries.size()) {
                throw malformedEntities("acknowledged ENTS lane lacks one complete installed aggregate");
            }
            List<Long> ids = durableRows.stream()
                    .map(WorldStructureEntity::getAuthoritativeEntityId).toList();
            StructureEntityAggregate.Installation installation = new StructureEntityAggregate.Installation(
                    mutation.getInstallationIdentity(), sourceEntries, ids);
            planned = installation.plannedEntities();
            for (int index = 0; index < planned.size(); index++) {
                WorldStructureEntity durable = durableRows.get(index);
                if (!durable.matches(worldId, chunkX, chunkZ, mutation.getInstallationIdentity(),
                        installation.sourceFingerprint(), planned.get(index))) {
                    throw malformedEntities("durable ENTS row differs at encounter ordinal " + index);
                }
            }
            outcome = "DURABLY_ACTIVATED";
        } else {
            FinalCarrierLaneMutation.ActivationStatus expected = rejected
                    ? FinalCarrierLaneMutation.ActivationStatus.REJECTED
                    : FinalCarrierLaneMutation.ActivationStatus.PENDING;
            if (mutation.getActivationStatus() != expected || !durableRows.isEmpty()) {
                throw malformedEntities("non-acknowledged ENTS state owns conflicting durable rows");
            }
            planned = new StructureEntityAggregate.Installation(
                    mutation.getInstallationIdentity(), sourceEntries).plannedEntities();
            outcome = rejected ? "TERMINALLY_REJECTED" : "CLAIMED_PENDING";
        }

        ArrayList<Map<String, Object>> entries = new ArrayList<>(sourceEntries.size());
        boolean chestMinecartPrefixOpen = true;
        for (int index = 0; index < sourceEntries.size(); index++) {
            NeutralFinalChunk.StructureEntity source = sourceEntries.get(index);
            boolean canonicalChestMinecart = canonicalChestMinecartPrefixEntry(
                    chestMinecartPrefixOpen, source);
            if (!canonicalChestMinecart) chestMinecartPrefixOpen = false;
            String canonicalRow = planned.get(index).rowFingerprint();
            List<String> owners = starts.stream().filter(start -> start.contains(
                            source.x(), source.y(), source.z()))
                    .map(StartFact::structureId).distinct().sorted().toList();
            String disposition = "LIVE";
            String durableRowFingerprint = null;
            if (acknowledged) {
                WorldStructureEntity durable = durableRows.get(index);
                switch (durable.bindingStatus()) {
                    case LIVE -> durableRowFingerprint = durable.getRowFingerprint();
                    case DEAD -> disposition = "OVERRIDDEN";
                }
            }
            entries.add(map(
                    "encounterOrdinal", index,
                    "disposition", disposition,
                    "packed", entityPacked(source),
                    "structures", owners,
                    "kind", canonicalChestMinecart ? "CHEST_MINECART" : "ENTITY",
                    "entityKey", source.entityKey(),
                    "spawnReason", source.spawnReason(),
                    "x", source.x(), "y", source.y(), "z", source.z(),
                    "yaw", (double) source.yaw(), "pitch", (double) source.pitch(),
                    "velocityX", source.velocityX(), "velocityY", source.velocityY(),
                    "velocityZ", source.velocityZ(),
                    "lootTable", source.lootTable().isEmpty() ? null : source.lootTable(),
                    "lootSeed", Long.toString(source.lootSeed()),
                    "canonicalPayloadSha256", sha256(source.canonicalPayload()),
                    "canonicalRowSha256", canonicalRow,
                    "durableRowSha256", durableRowFingerprint));
        }
        return entityChunk(chunkX, chunkZ, claimed, acknowledged, rejected, outcome,
                List.copyOf(entries));
    }

    /** Mirrors the standalone carrier decoder's ordered legacy chest-minecart prefix exactly. */
    private static boolean canonicalChestMinecartPrefixEntry(boolean prefixOpen,
            NeutralFinalChunk.StructureEntity source) {
        return prefixOpen && source.entityKey().equals("minecraft:chest_minecart")
                && source.spawnReason().equals("minecraft:chunk_generation")
                && !source.lootTable().isEmpty()
                && Float.floatToRawIntBits(source.yaw()) == 0
                && Float.floatToRawIntBits(source.pitch()) == 0
                && Double.doubleToRawLongBits(source.velocityX()) == 0L
                && Double.doubleToRawLongBits(source.velocityY()) == 0L
                && Double.doubleToRawLongBits(source.velocityZ()) == 0L
                && Arrays.equals(source.canonicalPayload(), CANONICAL_CHEST_MINECART_PAYLOAD)
                && exactHalfCell(source.x()) && exactHalfCell(source.y())
                && exactHalfCell(source.z());
    }

    private static boolean exactHalfCell(double value) {
        return Double.doubleToRawLongBits(value)
                == Double.doubleToRawLongBits(Math.floor(value) + 0.5d);
    }

    private static Map<String, Object> entityChunk(int chunkX, int chunkZ, boolean claimed,
            boolean acknowledged, boolean rejected, String outcome, List<?> entries) {
        return map("chunkX", chunkX, "chunkZ", chunkZ,
                "laneClaimed", claimed, "laneAcknowledged", acknowledged,
                "laneRejected", rejected, "activationOutcome", outcome, "entries", entries);
    }

    private static boolean matchingEntityMutation(FinalCarrierLaneMutation mutation, long worldId,
            NeutralFinalChunk source, String sourceFingerprint) {
        if (mutation.getWorldId() != worldId || mutation.getChunkX() != source.chunkX()
                || mutation.getChunkZ() != source.chunkZ()
                || !CanonicalWorldgenStore.Lane.ENTITIES.name().equals(mutation.getLane())
                || !sourceFingerprint.equals(mutation.getSourceFingerprint())) return false;
        byte[] typedBytes = mutation.getTypedPayload();
        if (!mutation.getPayloadFingerprint().equals(sha256(typedBytes))) return false;
        String expectedIdentity = worldId + ":" + source.chunkX() + ":" + source.chunkZ()
                + ":" + CanonicalWorldgenStore.Lane.ENTITIES.name() + ":" + sourceFingerprint
                + ":" + mutation.getPayloadFingerprint();
        if (!expectedIdentity.equals(mutation.getInstallationIdentity())) return false;
        NeutralFinalChunk typed;
        try {
            typed = source.verifyCarrier(typedBytes);
        } catch (RuntimeException malformed) {
            return false;
        }
        NeutralFinalChunk.Sidecars sidecars = typed.sidecars();
        return typed.chunkX() == source.chunkX() && typed.chunkZ() == source.chunkZ()
                && finalCarrierSourceFingerprint(typed).equals(sourceFingerprint)
                && sidecars.blockTicks().isEmpty() && sidecars.fluidTicks().isEmpty()
                && sidecars.loot().isEmpty() && sidecars.spawners().isEmpty()
                && sidecars.owners().isEmpty() && sidecars.archaeology().isEmpty()
                && sidecars.bees().isEmpty() && sidecars.blockEntities().isEmpty()
                && sidecars.entities().equals(source.sidecars().entities());
    }

    private static int entityPacked(NeutralFinalChunk.StructureEntity entry) {
        int x = checkedFloorToInt(entry.x(), "entity X");
        int y = checkedFloorToInt(entry.y(), "entity Y");
        int z = checkedFloorToInt(entry.z(), "entity Z");
        if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) {
            throw malformedEntities("entity Y is outside the canonical block index");
        }
        return Blocks.blockIndex(Math.floorMod(x, Blocks.CHUNK_X), y,
                Math.floorMod(z, Blocks.CHUNK_Z));
    }

    private static int checkedFloorToInt(double value, String label) {
        double floor = Math.floor(value);
        if (floor < Integer.MIN_VALUE || floor > Integer.MAX_VALUE) {
            throw malformedEntities(label + " exceeds the block-coordinate range");
        }
        return (int) floor;
    }

    /** Stable producer identity excludes every delivery-local final-carrier sidecar. */
    private static String finalCarrierSourceFingerprint(NeutralFinalChunk carrier) {
        return sha256(carrier.withSidecars(NeutralFinalChunk.Sidecars.EMPTY).encodedCarrier());
    }

    private static IllegalStateException malformedEntities(String message) {
        return new IllegalStateException("malformed durable ENTS evidence: " + message);
    }

    private static Map<String, Object> lifecycleEvent(String sequence, long revision, String kind,
            int generation, String digest, Integer chunkX, Integer chunkZ) {
        return map("schema", SCHEMA, "sequence", sequence, "revision", revision, "kind", kind,
                "connectionGeneration", generation, "connectionIdentitySha256", digest,
                "chunkX", chunkX, "chunkZ", chunkZ);
    }

    private static void validateWindow(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        if (minChunkX > maxChunkX || minChunkZ > maxChunkZ
                || (long) maxChunkX - minChunkX > 16L || (long) maxChunkZ - minChunkZ > 16L) {
            throw new IllegalArgumentException("authority evidence window must be at most 17x17");
        }
        checkedBlockCoordinate(minChunkX, 0);
        checkedBlockCoordinate(maxChunkX, Blocks.CHUNK_X - 1);
        checkedBlockCoordinate(minChunkZ, 0);
        checkedBlockCoordinate(maxChunkZ, Blocks.CHUNK_Z - 1);
    }

    private static int[] position(int chunkX, int chunkZ, int packed) {
        int horizontal = packed % 256;
        return new int[]{checkedBlockCoordinate(chunkX, horizontal % Blocks.CHUNK_X),
                Math.addExact(packed / (Blocks.CHUNK_X * Blocks.CHUNK_Z), Blocks.MIN_Y),
                checkedBlockCoordinate(chunkZ, horizontal / Blocks.CHUNK_X)};
    }

    private static int checkedBlockCoordinate(int chunk, int local) {
        try {
            return Math.toIntExact(Math.addExact(
                    Math.multiplyExact((long) chunk, Blocks.CHUNK_X), local));
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(
                    "authority evidence chunk coordinate exceeds the block-coordinate range",
                    overflow);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static Map<String, Object> map(Object... fields) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < fields.length; index += 2) {
            result.put((String) fields[index], fields[index + 1]);
        }
        return result;
    }

    private static final class StartFact {
        private final String structureId;
        private final Mc263StructureCarrier.BoundingBox box;
        private StartFact(String structureId, Mc263StructureCarrier.BoundingBox box) {
            this.structureId = structureId; this.box = box;
        }
        private String structureId() { return structureId; }
        private boolean contains(double x, double y, double z) {
            return x >= box.minX() && x <= box.maxX() && y >= box.minY() && y <= box.maxY()
                    && z >= box.minZ() && z <= box.maxZ();
        }
    }

    private static final class LootCell {
        private final int chunkX;
        private final int chunkZ;
        private final int packed;

        private LootCell(int chunkX, int chunkZ, int packed) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.packed = packed;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof LootCell value)) return false;
            return chunkX == value.chunkX && chunkZ == value.chunkZ && packed == value.packed;
        }

        @Override
        public int hashCode() {
            return Objects.hash(chunkX, chunkZ, packed);
        }
    }

    private static final class TickEvidence {
        private final FinalCarrierTickScheduler.ScheduledTick tick;
        private final String state;
        private final String disposition;

        private TickEvidence(FinalCarrierTickScheduler.ScheduledTick tick,
                String state, String disposition) {
            this.tick = tick;
            this.state = state;
            this.disposition = disposition;
        }

        private FinalCarrierTickScheduler.ScheduledTick tick() { return tick; }
        private String state() { return state; }
        private String disposition() { return disposition; }
    }
}
