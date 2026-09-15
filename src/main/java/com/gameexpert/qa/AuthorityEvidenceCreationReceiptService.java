package com.gameexpert.qa;

import com.gameexpert.config.EngineProperties;
import com.gameexpert.qa.persistence.AuthorityEvidenceCreationReceipt;
import com.gameexpert.qa.persistence.AuthorityEvidenceCreationReceiptRepository;
import com.gameexpert.api.persistence.WorldAccess;
import com.gameexpert.api.persistence.WorldStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** QA-only, one-use handoff of a committed world creation to authority evidence. */
@Component
public class AuthorityEvidenceCreationReceiptService {
    private static final int RECEIPT_BYTE_COUNT = 32;
    static final int MAX_COLLISION_ATTEMPTS = 8;
    private static final Pattern NICKNAME = Pattern.compile("[A-Za-z0-9_]{2,12}");
    private static final Pattern RECEIPT = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Base64.Encoder RECEIPT_ENCODER =
            Base64.getUrlEncoder().withoutPadding();

    private final EngineProperties properties;
    private final AuthorityEvidenceCoordinator coordinator;
    private final Consumer<byte[]> entropy;
    private final AuthorityEvidenceCreationReceiptRepository receipts;
    private final WorldStore worlds;
    private final EphemeralReceiptStore ephemeral;
    private final Object monitor = new Object();
    private final Map<Long, IssuedReceipt> issuedReceipts = new HashMap<>();

    public AuthorityEvidenceCreationReceiptService(EngineProperties properties,
            AuthorityEvidenceCoordinator coordinator,
            AuthorityEvidenceCreationReceiptRepository receipts) {
        this(properties, coordinator, new SecureRandom()::nextBytes, null, receipts);
    }

    /** Spring production constructor; world identity is locked before a bearer is issued. */
    @Autowired
    public AuthorityEvidenceCreationReceiptService(EngineProperties properties,
            AuthorityEvidenceCoordinator coordinator, WorldStore worlds,
            AuthorityEvidenceCreationReceiptRepository receipts) {
        this(properties, coordinator, new SecureRandom()::nextBytes, worlds, receipts);
    }

    /** Explicitly in-memory constructor retained for isolated unit tests only. */
    public AuthorityEvidenceCreationReceiptService(EngineProperties properties,
            AuthorityEvidenceCoordinator coordinator) {
        this(properties, coordinator, new SecureRandom()::nextBytes, null, null);
    }

    AuthorityEvidenceCreationReceiptService(EngineProperties properties,
            AuthorityEvidenceCoordinator coordinator, Consumer<byte[]> entropy) {
        this(properties, coordinator, entropy, null, null);
    }

    private AuthorityEvidenceCreationReceiptService(EngineProperties properties,
            AuthorityEvidenceCoordinator coordinator, Consumer<byte[]> entropy,
            WorldStore worlds, AuthorityEvidenceCreationReceiptRepository receipts) {
        this.properties = Objects.requireNonNull(properties, "engine properties");
        this.coordinator = Objects.requireNonNull(coordinator, "authority evidence coordinator");
        this.entropy = Objects.requireNonNull(entropy, "authority evidence entropy");
        this.receipts = receipts;
        this.worlds = worlds;
        this.ephemeral = receipts == null ? new EphemeralReceiptStore() : null;
    }

    AuthorityEvidenceCreationReceiptService(EngineProperties properties,
            AuthorityEvidenceCoordinator coordinator, SecureRandom secureRandom) {
        this(properties, coordinator,
                Objects.requireNonNull(secureRandom, "secure random")::nextBytes, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String issueAfterCommittedCreation(long worldId, String nickname) {
        requireEnabled();
        requireWorldAndNickname(worldId, nickname);
        if (receipts == null && (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive())) {
            throw new IllegalStateException("authority evidence receipt requires a committed creation");
        }

        if (receipts != null) return issueDurably(worldId, nickname);

        String receipt = ephemeral.issue(worldId, nickname, entropy);
        synchronized (monitor) {
            issuedReceipts.put(worldId, new IssuedReceipt(nickname, receipt,
                    digestReceipt(receipt)));
        }
        return receipt;
    }

    /** Returns the same bearer after a response-delivery failure, never minting a second digest. */
    @Transactional
    public String recoverAfterResponseLoss(long worldId, String nickname) {
        requireEnabled();
        requireWorldAndNickname(worldId, nickname);
        synchronized (monitor) {
            IssuedReceipt issued = issuedReceipts.get(worldId);
            if (issued == null || !issued.nickname.equals(nickname)) {
                throw new IllegalStateException(
                        "authority evidence receipt bearer recovery is unavailable");
            }
            if (receipts != null) {
                requireCommittedWorld(worldId, nickname);
                AuthorityEvidenceCreationReceipt stored = lockedReceipt(worldId);
                if (!issued.digest.equals(stored.receiptDigest())) {
                    throw new IllegalStateException(
                            "authority evidence receipt identity changed");
                }
            }
            return issued.bearer;
        }
    }

    /** Descriptive alias retained for callers that name the operation as receipt recovery. */
    public String recoverIssuedReceipt(long worldId, String nickname) {
        return recoverAfterResponseLoss(worldId, nickname);
    }

    @Transactional
    public void consumeAndArm(long worldId, String nickname,
            AuthorityEvidenceJournal.Window window, String creationReceipt) {
        requireEnabled();
        requireWorldAndNickname(worldId, nickname);
        Objects.requireNonNull(window, "authority evidence window");
        if (creationReceipt == null || !RECEIPT.matcher(creationReceipt).matches()) {
            throw new IllegalArgumentException("authority evidence receipt is malformed");
        }

        if (receipts != null) {
            requireCommittedWorld(worldId, nickname);
            consumeDurably(worldId, nickname, window, creationReceipt);
            return;
        }

        ephemeral.consume(worldId, nickname, window, creationReceipt, coordinator);
    }

    private String issueDurably(long worldId, String nickname) {
        synchronized (monitor) {
            requireCommittedWorld(worldId, nickname);
            if (receipts.findByWorldIdForUpdate(worldId).isPresent()) {
                throw new IllegalStateException("authority evidence receipt already issued for world");
            }
            for (int attempt = 0; attempt < MAX_COLLISION_ATTEMPTS; attempt++) {
                byte[] bytes = new byte[RECEIPT_BYTE_COUNT];
                entropy.accept(bytes);
                String receipt = RECEIPT_ENCODER.encodeToString(bytes);
                String digest = digestReceipt(receipt);
                if (receipts.findByReceiptDigest(digest).isPresent()) continue;
                try {
                    receipts.saveAndFlush(new AuthorityEvidenceCreationReceipt(
                            worldId, nickname, digest));
                    IssuedReceipt issued = new IssuedReceipt(nickname, receipt, digest);
                    afterCommit(() -> {
                        synchronized (monitor) {
                            issuedReceipts.put(worldId, issued);
                        }
                    }, () -> {
                        synchronized (monitor) {
                            if (issuedReceipts.get(worldId) == issued) issuedReceipts.remove(worldId);
                        }
                    });
                    return receipt;
                } catch (RuntimeException failure) {
                    if (receipts.findByWorldIdForUpdate(worldId).isPresent()) {
                        throw new IllegalStateException(
                                "authority evidence receipt already issued for world", failure);
                    }
                    if (receipts.findByReceiptDigest(digest).isEmpty()) throw failure;
                }
            }
            throw new IllegalStateException("authority evidence receipt collision limit reached");
        }
    }

    private void consumeDurably(long worldId, String nickname,
            AuthorityEvidenceJournal.Window window, String creationReceipt) {
        AuthorityEvidenceCreationReceipt receipt = lockedReceipt(worldId);
        receipt.requirePendingBinding(worldId, nickname, digestReceipt(creationReceipt));
        receipt.consume();
        coordinator.arm(worldId, nickname, window);
    }

    private AuthorityEvidenceCreationReceipt lockedReceipt(long worldId) {
        return receipts.findByWorldIdForUpdate(worldId).orElseThrow(() ->
                new IllegalStateException("authority evidence receipt binding is invalid"));
    }

    private void requireCommittedWorld(long worldId, String nickname) {
        if (worlds == null) return;
        WorldAccess world = worlds.findByIdForShare(worldId).orElseThrow(() ->
                new IllegalStateException("authority evidence world identity is invalid"));
        world.generationProfile();
        if (!world.isOwnedBy(nickname)) {
            throw new IllegalStateException("authority evidence world identity is invalid");
        }
    }

    private static void afterCommit(Runnable commit, Runnable rollback) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            commit.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                commit.run();
            }

            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) rollback.run();
            }
        });
    }

    private static String digestReceipt(String receipt) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(receipt.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void requireEnabled() {
        if (!properties.qaSeeding()) {
            throw new IllegalStateException("authority evidence creation receipts are disabled");
        }
    }

    private static void requireWorldAndNickname(long worldId, String nickname) {
        if (worldId <= 0) {
            throw new IllegalArgumentException("world ID must be positive");
        }
        if (nickname == null || !NICKNAME.matcher(nickname).matches()) {
            throw new IllegalArgumentException("nickname must match ASCII [A-Za-z0-9_]{2,12}");
        }
    }

    /** Test-only fallback; the Spring constructor always selects the repository-backed branch. */
    private static final class EphemeralReceiptStore {
        private final Object monitor = new Object();
        private final Set<Long> issuedWorldIds = new HashSet<>();
        private final Map<String, PendingReceipt> pendingReceipts = new HashMap<>();
        private final Map<String, PendingReceipt> inFlightReceipts = new HashMap<>();

        private String issue(long worldId, String nickname, Consumer<byte[]> entropy) {
            synchronized (monitor) {
                if (!issuedWorldIds.add(worldId)) {
                    throw new IllegalStateException(
                            "authority evidence receipt already issued for world");
                }
                try {
                    for (int attempt = 0; attempt < MAX_COLLISION_ATTEMPTS; attempt++) {
                        byte[] bytes = new byte[RECEIPT_BYTE_COUNT];
                        entropy.accept(bytes);
                        String receipt = RECEIPT_ENCODER.encodeToString(bytes);
                        if (!pendingReceipts.containsKey(receipt)
                                && !inFlightReceipts.containsKey(receipt)) {
                            pendingReceipts.put(receipt, new PendingReceipt(worldId, nickname));
                            return receipt;
                        }
                    }
                    throw new IllegalStateException(
                            "authority evidence receipt collision limit reached");
                } catch (RuntimeException | Error failure) {
                    issuedWorldIds.remove(worldId);
                    throw failure;
                }
            }
        }

        private void consume(long worldId, String nickname, AuthorityEvidenceJournal.Window window,
                String creationReceipt, AuthorityEvidenceCoordinator coordinator) {
            PendingReceipt pending;
            synchronized (monitor) {
                pending = pendingReceipts.get(creationReceipt);
                if (pending == null || pending.worldId() != worldId
                        || !pending.nickname().equals(nickname)) {
                    throw new IllegalStateException(
                            "authority evidence receipt binding is invalid");
                }
                pendingReceipts.remove(creationReceipt);
                inFlightReceipts.put(creationReceipt, pending);
            }
            try {
                coordinator.arm(worldId, nickname, window);
            } catch (RuntimeException | Error failure) {
                synchronized (monitor) {
                    if (inFlightReceipts.remove(creationReceipt) == pending) {
                        pendingReceipts.put(creationReceipt, pending);
                    }
                }
                throw failure;
            }
            synchronized (monitor) {
                inFlightReceipts.remove(creationReceipt);
            }
        }
    }

    private static final class PendingReceipt {
        private final long worldId;
        private final String nickname;

        private PendingReceipt(long worldId, String nickname) {
            this.worldId = worldId;
            this.nickname = nickname;
        }

        private long worldId() { return worldId; }
        private String nickname() { return nickname; }
    }

    private static final class IssuedReceipt {
        private final String nickname;
        private final String bearer;
        private final String digest;

        private IssuedReceipt(String nickname, String bearer, String digest) {
            this.nickname = nickname;
            this.bearer = bearer;
            this.digest = digest;
        }
    }
}
