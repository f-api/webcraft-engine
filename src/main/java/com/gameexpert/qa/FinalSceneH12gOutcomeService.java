package com.gameexpert.qa;

import com.gameexpert.ws.dto.WsMessages.FinalSceneH12gPrerequisiteEvidence;
import com.gameexpert.ws.dto.WsMessages.FinalSceneGeneratedPrerequisiteReceipt;
import com.gameexpert.ws.dto.WsMessages.FinalSceneNaturalEntityBinding;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Outcome-only authority boundary over one consumed, completed H12g Runtime ledger. */
@Service
public class FinalSceneH12gOutcomeService {
    public static final String MODULE_SCHEMA = "game-expert.qa-final-scene-h12g-module/v1";
    private static final String RECEIPT_SCHEMA = "game-expert.final-scene-prerequisites/v2";
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    private static final Pattern NICKNAME = Pattern.compile("[A-Za-z0-9_]{2,12}");
    private final CompletedLedgerProvider provider;

    @org.springframework.beans.factory.annotation.Autowired
    public FinalSceneH12gOutcomeService(ObjectProvider<CompletedLedgerProvider> providers) {
        this(providers.getIfUnique());
    }

    FinalSceneH12gOutcomeService(CompletedLedgerProvider provider) {
        this.provider = provider;
    }

    /** Exact current transport identity. Runtime must fence all fields before reserving a ledger. */
    public record ActiveBinding(long worldId, String world, String nickname,
            String connectionId) {
        public ActiveBinding {
            if (worldId <= 0L || world == null || world.isBlank() || world.length() > 30
                    || nickname == null || !NICKNAME.matcher(nickname).matches()
                    || connectionId == null || connectionId.isBlank()
                    || connectionId.length() > 256) {
                throw new IllegalArgumentException("invalid H12g active binding");
            }
        }
    }

    /** Durable LIVE candidate detached from Runtime; no capability or entity reference escapes. */
    public record DurableCandidate(int roleOrdinal, long candidateOrdinal, long entityId,
            String kind, String lifecycle, long revision) {
        public DurableCandidate {
            if (roleOrdinal < 0 || candidateOrdinal < 0L || candidateOrdinal > MAX_SAFE_INTEGER
                    || entityId <= 0L
                    || entityId > MAX_SAFE_INTEGER || candidateOrdinal != entityId
                    || !"CHEST_MINECART".equals(kind) || !"LIVE".equals(lifecycle)
                    || revision < 0L || revision > MAX_SAFE_INTEGER) {
                throw new IllegalArgumentException("invalid durable H12g candidate");
            }
        }
    }

    /**
     * Strict-v2 lifecycle ledger plus a same-snapshot durable candidate projection. Runtime creates
     * this only after visible/interact/open/mutate/close/unload-reload/reconnect have committed.
     */
    public record CompletedLedger(String connectionId,
            FinalSceneGeneratedPrerequisiteReceipt receipt,
            List<DurableCandidate> candidates) {
        public CompletedLedger {
            receipt = Objects.requireNonNull(receipt, "H12g prerequisite receipt");
            if (!"finalScenePrerequisiteReceipt".equals(receipt.getType())
                    || !RECEIPT_SCHEMA.equals(receipt.getSchema())
                    || !"content-v1".equals(receipt.getFixtureId())
                    || !receipt.getFixtureChecksum().matches("(?:0|[1-9][0-9]*)")
                    || !"spring".equals(receipt.getAuthority())
                    || !"H12g".equals(receipt.getScenario())
                    || receipt.getWorld() == null || receipt.getWorld().isBlank()
                    || receipt.getWorld().length() > 30
                    || receipt.getNickname() == null
                    || !NICKNAME.matcher(receipt.getNickname()).matches()
                    || connectionId == null || connectionId.isBlank()
                    || receipt.getActionNonce() == null
                    || !receipt.getActionNonce().matches("[A-Za-z0-9._:-]{8,128}")
                    || receipt.getRevision() <= 0L || !receipt.isRaidActive()
                    || receipt.getRaidRoleCount() < 5 || !receipt.isBossbarVisible()
                    || !receipt.isRewardsReady() || !receipt.isFlightReady()
                    || !receipt.isFishingReady() || !receipt.isConnectedShapesReady()) {
                throw new IllegalArgumentException("invalid completed H12g ledger header");
            }
            FinalSceneNaturalEntityBinding binding = Objects.requireNonNull(
                    receipt.getBinding(), "H12g natural binding");
            FinalSceneH12gPrerequisiteEvidence evidence = requireH12gEvidence(receipt);
            if (binding.getSchema() != 2 || binding.getWorldId() <= 0L
                    || !"LIVE".equals(binding.getLifecycle())
                    || binding.getTarget() == null
                    || !"CHEST_MINECART".equals(binding.getTarget().getKind())
                    || binding.getConnectionGeneration() <= 0L
                    || binding.getEvidenceGeneration() <= 0L
                    || !"CHEST_MINECART".equals(evidence.getKind())
                    || !evidence.isVisibleSpawn() || !evidence.isInteractionCommitted()
                    || evidence.getCargoTarget() == null
                    || evidence.getCargoTarget().getEntityId()
                            != binding.getTarget().getEntityId()
                    || evidence.getCargoRevision() < binding.getRevision()
                    || !evidence.isCargoOpened() || !evidence.isCargoMutationCommitted()
                    || !evidence.isCargoClosed() || !evidence.isUnloadReloadPersisted()
                    || !evidence.isReconnectPersisted()
                    || evidence.getPersistedSlots() == null
                    || evidence.getPersistedSlots().size() != 27
                    || evidence.getPersistedCursor() == null) {
                throw new IllegalArgumentException("invalid completed H12g lifecycle facts");
            }
            for (int index = 0; index < 27; index++) {
                if (evidence.getPersistedSlots().get(index) == null
                        || evidence.getPersistedSlots().get(index).getSlot() != index) {
                    throw new IllegalArgumentException("invalid persisted H12g cargo order");
                }
            }
            candidates = immutableCandidates(candidates);
            long targetId = binding.getTarget().getEntityId();
            long durableCargoRevision = evidence.getCargoRevision();
            if (candidates.stream().noneMatch(candidate -> candidate.entityId() == targetId
                    && candidate.revision() == durableCargoRevision)) {
                throw new IllegalArgumentException("H12g target is absent from durable candidates");
            }
        }

        boolean matches(ActiveBinding active) {
            return receipt.getBinding().getWorldId() == active.worldId()
                    && receipt.getWorld().equals(active.world())
                    && receipt.getNickname().equals(active.nickname())
                    && connectionId.equals(active.connectionId());
        }
    }

    private static FinalSceneH12gPrerequisiteEvidence requireH12gEvidence(
            FinalSceneGeneratedPrerequisiteReceipt receipt) {
        Objects.requireNonNull(receipt, "H12g prerequisite receipt");
        if (!(receipt.getEvidence() instanceof FinalSceneH12gPrerequisiteEvidence evidence)) {
            throw new IllegalArgumentException("invalid completed H12g receipt evidence");
        }
        return evidence;
    }

    /** Reservation is single-use; authenticate must re-read the same durable candidate snapshot. */
    public interface PreparedLedger {
        CompletedLedger ledger();
        boolean authenticate();
        boolean commit();
        String dispose();
    }

    /** Atomically reserves at most one completed ledger for the exact active connection. */
    @FunctionalInterface
    public interface CompletedLedgerProvider {
        Optional<PreparedLedger> consume(ActiveBinding binding);
    }

    public Outcome execute(ActiveBinding binding) {
        Objects.requireNonNull(binding, "H12g active binding");
        if (provider == null) return rejected("ABSENT", "SUCCESS");
        Optional<PreparedLedger> reservation = Objects.requireNonNull(provider.consume(binding),
                "H12g provider result");
        if (reservation.isEmpty()) return rejected("ABSENT", "SUCCESS");
        PreparedLedger prepared = Objects.requireNonNull(reservation.get(),
                "H12g prepared ledger");
        try {
            CompletedLedger ledger = Objects.requireNonNull(prepared.ledger(),
                    "H12g completed ledger");
            if (!ledger.matches(binding)) return rejected("WRONG_ORIGIN", "SUCCESS");
            if (!prepared.authenticate()) return rejected("AUTHENTICATION_FAILURE", "SUCCESS");
            if (!prepared.commit()) return rejected("STALE", "SUCCESS");
            List<Ordinal> candidates = ledger.candidates().stream()
                    .map(candidate -> new Ordinal(candidate.roleOrdinal(),
                            candidate.candidateOrdinal()))
                    .toList();
            return new Outcome(new Evidence(MODULE_SCHEMA, candidates), List.of(),
                    "SUCCESS", "SUCCESS", true);
        } finally {
            String cleanup = requireCleanup(prepared.dispose());
            if (!"SUCCESS".equals(cleanup)) {
                throw new IllegalStateException("H12g completed ledger cleanup failed");
            }
        }
    }

    private static List<DurableCandidate> immutableCandidates(List<DurableCandidate> input) {
        if (input == null || input.isEmpty() || input.size() > 4_096) {
            throw new IllegalArgumentException("H12g durable candidates are unavailable");
        }
        List<DurableCandidate> copy = input.stream()
                .map(candidate -> Objects.requireNonNull(candidate, "H12g durable candidate"))
                .sorted(Comparator.comparingInt(DurableCandidate::roleOrdinal)
                        .thenComparingLong(DurableCandidate::candidateOrdinal))
                .toList();
        Set<String> unique = new HashSet<>();
        for (DurableCandidate candidate : copy) {
            if (!unique.add(candidate.roleOrdinal() + ":" + candidate.candidateOrdinal())) {
                throw new IllegalArgumentException("duplicate H12g durable candidate");
            }
        }
        return List.copyOf(copy);
    }

    private static Outcome rejected(String code, String cleanup) {
        return new Outcome(null, List.of(new Failure(null, null, code)), cleanup,
                "REJECTED", false);
    }

    private static String requireCleanup(String cleanup) {
        if (!"SUCCESS".equals(cleanup) && !"REJECTED".equals(cleanup)
                && !"CLASSIFIED_FAILURE".equals(cleanup)) {
            throw new IllegalStateException("invalid H12g cleanup result");
        }
        return cleanup;
    }

    public record Ordinal(int roleOrdinal, long candidateOrdinal) { }
    public record Evidence(String schema, List<Ordinal> candidates) {
        public Evidence { candidates = List.copyOf(candidates); }
    }
    public record Failure(Integer roleOrdinal, Integer candidateOrdinal, String code) { }
    public record Outcome(Evidence evidence, List<Failure> failures, String cleanup,
            String operation, boolean evidenceAccepted) {
        public Outcome { failures = List.copyOf(failures); }
    }
}
