package com.gameexpert.qa;

import com.gameexpert.qa.FinalSceneH12fArmorStandExecutor.Binding;
import com.gameexpert.qa.FinalSceneH12fArmorStandExecutor.Candidate;
import com.gameexpert.qa.FinalSceneH12fArmorStandExecutor.CleanupStatus;
import com.gameexpert.qa.FinalSceneH12fArmorStandExecutor.CompleteBatch;
import com.gameexpert.qa.FinalSceneH12fArmorStandExecutor.Module;
import com.gameexpert.qa.FinalSceneH12fArmorStandExecutor.OpaqueReceipt;
import com.gameexpert.qa.FinalSceneH12fArmorStandExecutor.OperationStatus;
import com.gameexpert.qa.FinalSceneH12fArmorStandExecutor.Outcome;
import com.gameexpert.qa.FinalSceneH12fArmorStandExecutor.PreparedBatch;
import com.gameexpert.qa.FinalSceneH12fArmorStandExecutor.Request;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Server-owned H12f adapter. Runtime capabilities and natural-target identity never leave it. */
@Service
public final class FinalSceneH12fOutcomeService {
    private static final String RECEIPT_SCHEMA = "game-expert.final-scene-prerequisites/v2";
    private static final Pattern NICKNAME = Pattern.compile("[A-Za-z0-9_]{2,12}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ACTION_NONCE = Pattern.compile("[A-Za-z0-9._:-]{8,128}");
    private static final Request REQUEST = new Request(Duration.ofSeconds(60));

    private final CompletedLedgerProvider provider;

    @Autowired
    public FinalSceneH12fOutcomeService(ObjectProvider<CompletedLedgerProvider> providers) {
        this(providers.getIfUnique());
    }

    FinalSceneH12fOutcomeService(CompletedLedgerProvider provider) {
        this.provider = provider;
    }

    /** Exact current transport identity; the runtime provider must fence against all four fields. */
    public record ActiveBinding(long worldId, String world, String nickname,
            String connectionId) {
        public ActiveBinding {
            if (worldId <= 0L || world == null || world.isBlank() || world.length() > 30
                    || nickname == null || !NICKNAME.matcher(nickname).matches()
                    || connectionId == null || connectionId.isBlank()
                    || connectionId.length() > 256) {
                throw new IllegalArgumentException("invalid H12f active binding");
            }
        }
    }

    /**
     * Authenticated completed strict-v2 ledger. It is authority-internal and is never serialized.
     * The provider constructs it only after durable semantic mutation, terminal commit, reload,
     * and reconnect have all been re-read from the exact natural Armor Stand aggregate.
     */
    public record CompletedLedger(String schema, String authority, String scenario,
            long worldId, String world, String nickname, String connectionId,
            long connectionGeneration, long evidenceGeneration, String actionNonce,
            long receiptRevision, String targetKind, long targetEntityId,
            String naturalAuthoritativeId, String stateProvenanceFingerprint,
            long targetRevision, long terminalRevision, boolean visibleSpawn,
            boolean interactionCommitted, boolean semanticMutationCommitted,
            boolean terminalCommitted, boolean unloadReloadPersisted,
            boolean reconnectPersisted, int roleOrdinal, int candidateOrdinal) {
        public CompletedLedger {
            if (!RECEIPT_SCHEMA.equals(schema) || !"spring".equals(authority)
                    || !"H12f".equals(scenario) || worldId <= 0L
                    || world == null || world.isBlank() || world.length() > 30
                    || nickname == null || !NICKNAME.matcher(nickname).matches()
                    || connectionId == null || connectionId.isBlank()
                    || connectionGeneration <= 0L || evidenceGeneration <= 0L
                    || actionNonce == null || !ACTION_NONCE.matcher(actionNonce).matches()
                    || receiptRevision <= 0L
                    || !"ARMOR_STAND".equals(targetKind) || targetEntityId <= 0L
                    || naturalAuthoritativeId == null || naturalAuthoritativeId.isBlank()
                    || naturalAuthoritativeId.length() > 512
                    || stateProvenanceFingerprint == null
                    || !SHA256.matcher(stateProvenanceFingerprint).matches()
                    || targetRevision < 0L || terminalRevision < targetRevision
                    || !visibleSpawn || !interactionCommitted || !semanticMutationCommitted
                    || !terminalCommitted || !unloadReloadPersisted || !reconnectPersisted
                    || roleOrdinal < 0 || candidateOrdinal < 0) {
                throw new IllegalArgumentException("invalid completed H12f ledger");
            }
        }

        boolean matches(ActiveBinding binding) {
            return worldId == binding.worldId() && world.equals(binding.world())
                    && nickname.equals(binding.nickname())
                    && connectionId.equals(binding.connectionId());
        }
    }

    /** One reservation of one completed ledger. Every method must be deterministic and thread-safe. */
    public interface PreparedLedger {
        CompletedLedger ledger();
        boolean authenticate();
        OperationStatus commit();
        CleanupStatus dispose();
    }

    /** Atomically consumes/reserves at most one completed ledger for the exact active binding. */
    @FunctionalInterface
    public interface CompletedLedgerProvider {
        Optional<PreparedLedger> consume(ActiveBinding binding);
    }

    public Outcome execute(ActiveBinding binding) {
        Objects.requireNonNull(binding, "H12f active binding");
        if (provider == null) return absentOutcome();
        Execution execution = new Execution();
        Module module = FinalSceneH12fArmorStandExecutor.bind(new Binding(
                request -> prepare(binding, execution),
                receipt -> authenticate(execution, receipt),
                (request, candidates) -> commit(execution, candidates)));
        return module.execute(REQUEST, ignored -> { });
    }

    private PreparedBatch prepare(ActiveBinding binding, Execution execution) {
        Optional<PreparedLedger> reserved = Objects.requireNonNull(provider.consume(binding),
                "H12f provider result");
        if (reserved.isEmpty()) return emptyPreparedBatch();
        PreparedLedger prepared = Objects.requireNonNull(reserved.get(), "H12f prepared ledger");
        CompletedLedger ledger = Objects.requireNonNull(prepared.ledger(), "H12f completed ledger");
        OpaqueReceipt<CompletedLedger> receipt = OpaqueReceipt.issue(ledger);
        Candidate candidate = Candidate.of(ledger.roleOrdinal(), ledger.candidateOrdinal(), receipt,
                ledger.matches(binding) ? List.of()
                        : List.of(FinalSceneH12fArmorStandExecutor.FailureCode.WRONG_ORIGIN));
        execution.prepared = prepared;
        execution.receipt = receipt;
        execution.candidate = candidate;
        return new PreparedBatch() {
            @Override public CompleteBatch collect() { return () -> List.of(candidate); }
            @Override public CleanupStatus dispose() {
                return Objects.requireNonNull(prepared.dispose(), "H12f cleanup status");
            }
        };
    }

    private boolean authenticate(Execution execution, OpaqueReceipt<?> receipt) {
        return receipt == execution.receipt && execution.prepared != null
                && execution.prepared.authenticate();
    }

    private OperationStatus commit(Execution execution, List<Candidate> candidates) {
        if (execution.prepared == null || execution.candidate == null
                || candidates.size() != 1 || candidates.getFirst() != execution.candidate) {
            return OperationStatus.REJECTED;
        }
        return Objects.requireNonNull(execution.prepared.commit(), "H12f operation status");
    }

    private static PreparedBatch emptyPreparedBatch() {
        return new PreparedBatch() {
            @Override public CompleteBatch collect() { return () -> List.of(); }
            @Override public CleanupStatus dispose() { return CleanupStatus.SUCCESS; }
        };
    }

    private static Outcome absentOutcome() {
        return FinalSceneH12fArmorStandExecutor.bind(null).execute(REQUEST, null);
    }

    private static final class Execution {
        private PreparedLedger prepared;
        private OpaqueReceipt<?> receipt;
        private Candidate candidate;
    }
}
