package com.gameexpert.qa;

import com.gameexpert.qa.persistence.AuthorityEvidenceCreationReceipt;
import com.gameexpert.qa.persistence.AuthorityEvidenceCreationReceiptRepository;
import com.gameexpert.terrain.persistence.CanonicalWorldgenStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Append-only schema-2 authority evidence boundary with durable Spring recovery. */
@Service
public class AuthorityEvidenceJournal {
    public static final String ABI = "SCOR2631";
    public static final int SCHEMA = 1;
    public static final int SCHEMA_V2 = 2;

    private static final int MAX_WINDOW_DISTANCE = 16;
    private static final int MAX_PROJECTION_ROWS = (MAX_WINDOW_DISTANCE + 1) * (MAX_WINDOW_DISTANCE + 1);
    private static final int MAX_STATE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_STRING_BYTES = 16 * 1024;
    private static final int STATE_MAGIC = 0x41455632;
    private static final int STATE_FORMAT = 2;
    private static final Pattern NICKNAME = Pattern.compile("[A-Za-z0-9_]{2,12}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final int ALL_LANES_MASK = allLanesMask();

    private final Object monitor = new Object();
    private final Map<ArmHandle, State> states = new IdentityHashMap<>();
    private final AuthorityEvidenceCreationReceiptRepository receipts;

    /** Explicitly in-memory constructor retained for isolated unit tests only. */
    public AuthorityEvidenceJournal() {
        this.receipts = null;
    }

    @Autowired
    public AuthorityEvidenceJournal(AuthorityEvidenceCreationReceiptRepository receipts) {
        this.receipts = Objects.requireNonNull(receipts, "authority evidence receipts");
    }

    /** Arms one immutable world/window binding after the creation receipt has been consumed. */
    @Transactional
    public ArmHandle arm(long worldId, String nickname, Window window) {
        requireWorldId(worldId);
        requireNickname(nickname);
        Objects.requireNonNull(window, "authority evidence window");
        ArmBinding binding = new ArmBinding(worldId, nickname, window);
        synchronized (monitor) {
            State state;
            if (receipts == null) {
                state = new State(binding);
                state.revision = 1;
            } else {
                AuthorityEvidenceCreationReceipt receipt = lockedReceipt(worldId);
                if (receipt.state() != AuthorityEvidenceCreationReceipt.State.CONSUMED) {
                    reject("authority evidence creation receipt is not consumed");
                }
                if (receipt.worldId() == null || receipt.worldId() != worldId
                        || !nickname.equals(receipt.nickname())) {
                    reject("authority evidence binding does not match its receipt");
                }
                if (receipt.evidenceState() != null || receipt.evidenceRevision() != 0) {
                    reject("authority evidence binding is already durable");
                }
                state = new State(binding);
                persist(state, receipt, 1);
            }
            ArmHandle handle = new ArmHandle();
            publish(handle, state);
            return handle;
        }
    }

    /** Rehydrates an active or sealed binding from the committed receipt row. */
    @Transactional
    public Optional<Recovery> recover(long worldId) {
        requireWorldId(worldId);
        synchronized (monitor) {
            AuthorityEvidenceCreationReceipt receipt = receipts == null ? null
                    : receipts.findByWorldIdForUpdate(worldId).orElse(null);
            if (receipt != null && receipt.state() != AuthorityEvidenceCreationReceipt.State.CONSUMED
                    && (receipt.evidenceState() != null || receipt.evidenceRevision() != 0)) {
                reject("authority evidence receipt is not consumed");
            }
            for (Map.Entry<ArmHandle, State> entry : states.entrySet()) {
                if (entry.getValue().binding.worldId == worldId) {
                    if (receipts != null && receipt == null) {
                        reject("authority evidence receipt is missing");
                    }
                    if (receipt != null) {
                        try {
                            verifyDurableIdentityAndImage(receipt, entry.getValue());
                        } catch (IllegalStateException stale) {
                            State rehydrated = readDurableState(receipt, null);
                            ArmHandle handle = new ArmHandle();
                            publish(handle, rehydrated);
                            return Optional.of(recovery(handle, rehydrated));
                        }
                    }
                    return Optional.of(recovery(entry.getKey(), entry.getValue()));
                }
            }
            if (receipts == null) return Optional.empty();
            if (receipt == null) return Optional.empty();
            if (receipt.state() != AuthorityEvidenceCreationReceipt.State.CONSUMED) {
                if (receipt.evidenceState() == null && receipt.evidenceRevision() == 0) {
                    return Optional.empty();
                }
                reject("authority evidence receipt is not consumed");
            }
            State state = readDurableState(receipt, null);
            ArmHandle handle = new ArmHandle();
            publish(handle, state);
            return Optional.of(recovery(handle, state));
        }
    }

    @Transactional
    public Optional<Recovery> recover(long worldId, String nickname) {
        requireWorldId(worldId);
        requireNickname(nickname);
        Optional<Recovery> recovered = recover(worldId);
        if (recovered.isEmpty() || !recovered.get().binding.nickname().equals(nickname)) {
            return Optional.empty();
        }
        return recovered;
    }

    @Transactional
    public LifecycleEvent sessionJoined(ArmHandle handle, String rawConnectionIdentity) {
        String digest = digestConnectionIdentity(rawConnectionIdentity);
        synchronized (monitor) {
            State state = workingState(handle);
            if (!state.events.isEmpty()) {
                DurableRecord durable = mutableState(handle, 7);
                if (digest.equals(state.generationOneDigest)) {
                    reject("connection generation must use a distinct identity");
                }
                state.generationTwoDigest = digest;
                return appendAndPersist(state, durable, "session-joined", 2, digest, null, null);
            }
            DurableRecord durable = mutableState(handle, 0);
            return appendAndPersist(state, durable, "session-joined", 1, digest, null, null);
        }
    }

    @Transactional
    public LifecycleEvent chunkActivated(ArmHandle handle, int chunkX, int chunkZ) {
        synchronized (monitor) {
            State state = workingState(handle);
            DurableRecord durable = mutableState(handle, state.events.size());
            int expectedGeneration = state.events.size() < 7 ? 1 : 2;
            if (state.events.size() == 1 || state.events.size() == 8) {
                requireCoordinates(chunkX, chunkZ, -54, 222);
            } else if (state.events.size() == 3) {
                requireCoordinates(chunkX, chunkZ, 1, 186);
            } else {
                reject("chunk-activated is out of order");
            }
            return appendAndPersist(state, durable, "chunk-activated", expectedGeneration,
                    generationDigest(state, expectedGeneration), chunkX, chunkZ);
        }
    }

    @Transactional
    public LifecycleEvent playerMoved(ArmHandle handle, int chunkX, int chunkZ) {
        synchronized (monitor) {
            State state = workingState(handle);
            DurableRecord durable = mutableState(handle, state.events.size());
            int expectedGeneration = state.events.size() < 7 ? 1 : 2;
            if (state.events.size() == 2) {
                requireCoordinates(chunkX, chunkZ, -54, 222);
            } else if (state.events.size() == 4) {
                requireCoordinates(chunkX, chunkZ, 1, 186);
            } else {
                reject("player-moved is out of order");
            }
            return appendAndPersist(state, durable, "player-moved", expectedGeneration,
                    generationDigest(state, expectedGeneration), chunkX, chunkZ);
        }
    }

    @Transactional
    public LifecycleEvent chunkEvicted(ArmHandle handle, int chunkX, int chunkZ,
            List<CanonicalChunk> canonicalRows) {
        synchronized (monitor) {
            State state = workingState(handle);
            DurableRecord durable = mutableState(handle, 5);
            requireCoordinates(chunkX, chunkZ, -54, 222);
            long revision = nextRevision(state, durable);
            Projection eviction = projection(revision, "6", state.binding.window, canonicalRows);
            LifecycleEvent event = append(state, "chunk-evicted", 1, state.generationOneDigest,
                    chunkX, chunkZ, revision);
            Projection previousEviction = state.eviction;
            state.eviction = eviction;
            try {
                persist(state, durable.receipt, revision);
            } catch (RuntimeException | Error failure) {
                state.events.remove(state.events.size() - 1);
                state.eviction = previousEviction;
                state.revision = revision - 1;
                throw failure;
            }
            return event;
        }
    }

    @Transactional
    public LifecycleEvent sessionLeft(ArmHandle handle) {
        synchronized (monitor) {
            State state = workingState(handle);
            DurableRecord durable = mutableState(handle, 6);
            return appendAndPersist(state, durable, "session-left", 1,
                    state.generationOneDigest, null, null);
        }
    }

    @Transactional
    public LifecycleEvent reconnectedChunkActivated(ArmHandle handle, int chunkX, int chunkZ,
            List<CanonicalChunk> canonicalRows) {
        synchronized (monitor) {
            State state = workingState(handle);
            DurableRecord durable = mutableState(handle, 8);
            requireCoordinates(chunkX, chunkZ, -54, 222);
            if (state.eviction == null) reject("eviction projection is missing");
            long revision = nextRevision(state, durable);
            Projection reconnect = projection(revision, "9", state.binding.window, canonicalRows);
            requireReconnectRelation(state.eviction, reconnect);
            LifecycleEvent event = append(state, "chunk-activated", 2, state.generationTwoDigest,
                    chunkX, chunkZ, revision);
            Projection previousReconnect = state.reconnect;
            state.reconnect = reconnect;
            try {
                persist(state, durable.receipt, revision);
            } catch (RuntimeException | Error failure) {
                state.events.remove(state.events.size() - 1);
                state.reconnect = previousReconnect;
                state.revision = revision - 1;
                throw failure;
            }
            return event;
        }
    }

    @Transactional
    public CaptureLease acquireCaptureLease(ArmHandle handle) {
        synchronized (monitor) {
            State state = workingState(handle);
            if (state.events.size() != 9 || state.reconnect == null || state.sealed) {
                reject("capture lease is not available");
            }
            if (state.activeLease != null) reject("capture lease is already active");
            durable(state);
            CaptureLease lease = new CaptureLease(this, handle, state);
            state.activeLease = lease;
            publish(handle, state);
            return lease;
        }
    }

    /** Current committed revision for an exact handle; stale durable images fail closed. */
    public long currentRevision(ArmHandle handle) {
        synchronized (monitor) {
            State state = state(handle);
            durable(state);
            return state.revision;
        }
    }

    /** Exact identity check used by the callback adapter after process recreation. */
    public boolean activeConnectionMatches(ArmHandle handle, String rawConnectionIdentity) {
        String digest = digestConnectionIdentity(rawConnectionIdentity);
        synchronized (monitor) {
            State state = state(handle);
            durable(state);
            String active = activeConnectionDigest(state);
            return active != null && active.equals(digest);
        }
    }

    public boolean firstConnectionMatches(ArmHandle handle, String rawConnectionIdentity) {
        String digest = digestConnectionIdentity(rawConnectionIdentity);
        synchronized (monitor) {
            State state = state(handle);
            durable(state);
            return state.generationOneDigest != null && state.generationOneDigest.equals(digest);
        }
    }

    /** Internal digest fence used by the runtime adapter without retaining raw connection data. */
    String activeConnectionIdentitySha256(ArmHandle handle) {
        synchronized (monitor) {
            State state = state(handle);
            durable(state);
            return activeConnectionDigest(state);
        }
    }

    private Snapshot seal(CaptureLease lease, CapturePayload payload) {
        synchronized (monitor) {
            if (lease == null || lease.closed) reject("capture lease is not active");
            State state = workingState(lease.handle);
            if (state.activeLease != lease || state.sealed || state.events.size() != 9
                    || state.reconnect == null) {
                reject("capture lease is not active");
            }
            if (receipts != null && payload == null) {
                reject("complete durable capture payload is required");
            }
            if (payload != null) requireCapturePayload(state, payload);
            DurableRecord durable = durable(state);
            long revision = nextRevision(state, durable);
            LifecycleEvent capture = append(state, "capture", 2, state.generationTwoDigest,
                    null, null, revision);
            state.sealed = true;
            state.capturePayload = payload;
            try {
                persist(state, durable.receipt, revision);
            } catch (RuntimeException | Error failure) {
                state.events.remove(state.events.size() - 1);
                state.sealed = false;
                state.capturePayload = null;
                state.revision = revision - 1;
                throw failure;
            }
            Snapshot snapshot = new FinalSnapshot(state);
            state.activeLease = null;
            publish(lease.handle, state);
            if (transactionState(false) == null) {
                lease.closed = true;
            } else {
                transactionState(true).rollbackActions.add(() -> lease.closed = false);
                lease.closed = true;
            }
            return snapshot;
        }
    }

    private DurableRecord mutableState(ArmHandle handle, int expectedSequence) {
        State state = workingState(handle);
        if (state.sealed) reject("authority evidence handle is sealed");
        if (state.activeLease != null) reject("authority evidence handle has an active lease");
        if (state.events.size() != expectedSequence) reject("authority evidence event is out of order");
        return durable(state);
    }

    private DurableRecord durable(State state) {
        if (receipts == null) return new DurableRecord(null);
        AuthorityEvidenceCreationReceipt receipt = lockedReceipt(state.binding.worldId);
        verifyDurableIdentityAndImage(receipt, state);
        return new DurableRecord(receipt);
    }

    private static void verifyDurableIdentityAndImage(AuthorityEvidenceCreationReceipt receipt,
            State state) {
        if (receipt.state() != AuthorityEvidenceCreationReceipt.State.CONSUMED) {
            reject("authority evidence binding receipt is not consumed");
        }
        if (receipt.worldId() == null || receipt.worldId() != state.binding.worldId
                || !state.binding.nickname.equals(receipt.nickname())) {
            reject("authority evidence durable binding does not match its receipt");
        }
        byte[] encoded = receipt.evidenceState();
        if (encoded == null || receipt.evidenceRevision() != state.revision
                || !Arrays.equals(encoded, encode(state))) {
            reject("authority evidence revision is stale or replayed");
        }
    }

    private static void requireCapturePayload(State state, CapturePayload payload) {
        if (payload.durableJson.length == 0 || payload.durableJson.length > MAX_STATE_BYTES) {
            reject("authority evidence capture payload is missing or too large");
        }
        if (state.eviction == null || state.reconnect == null
                || payload.sourceRevision != state.reconnect.revision
                || !payload.evictionRows.equals(state.eviction.canonicalChunks)
                || !payload.reconnectRows.equals(state.reconnect.canonicalChunks)) {
            reject("authority evidence capture source projection is stale");
        }
        String expected = captureSourceFingerprint(state.eviction, state.reconnect);
        if (!expected.equals(payload.sourceFingerprint)) {
            reject("authority evidence capture source fingerprint is stale");
        }
    }

    private static String captureSourceFingerprint(Projection eviction, Projection reconnect) {
        return projectionFingerprint(eviction.canonicalChunks)
                + projectionFingerprint(reconnect.canonicalChunks);
    }

    private AuthorityEvidenceCreationReceipt lockedReceipt(long worldId) {
        return receipts.findByWorldIdForUpdate(worldId).orElseThrow(
                () -> new IllegalStateException("authority evidence receipt is missing"));
    }

    private void persist(State state, AuthorityEvidenceCreationReceipt receipt, long revision) {
        if (revision <= state.revision || revision == Long.MAX_VALUE) {
            reject("authority evidence revision is not the exact successor");
        }
        state.revision = revision;
        if (receipt != null) {
            receipt.replaceEvidenceState(encode(state), revision);
            receipts.saveAndFlush(receipt);
        }
    }

    private LifecycleEvent appendAndPersist(State state, DurableRecord durable, String kind,
            int generation, String digest, Integer chunkX, Integer chunkZ) {
        long revision = nextRevision(state, durable);
        LifecycleEvent event = append(state, kind, generation, digest, chunkX, chunkZ, revision);
        try {
            persist(state, durable.receipt, revision);
        } catch (RuntimeException | Error failure) {
            state.events.remove(state.events.size() - 1);
            if (generation == 1 && state.events.isEmpty()) state.generationOneDigest = null;
            if (generation == 2 && state.events.size() < 8) state.generationTwoDigest = null;
            state.revision = revision - 1;
            throw failure;
        }
        return event;
    }

    private static long nextRevision(State state, DurableRecord durable) {
        long current = durable.receipt == null ? state.revision : durable.receipt.evidenceRevision();
        if (current != state.revision || current <= 0 || current == Long.MAX_VALUE) {
            reject("authority evidence revision is stale or missing");
        }
        return current + 1;
    }

    private State state(ArmHandle handle) {
        if (handle == null) reject("authority evidence handle is invalid");
        TransactionState transaction = transactionState(false);
        State state = transaction == null ? states.get(handle)
                : transaction.staged.getOrDefault(handle, states.get(handle));
        if (state == null) reject("authority evidence handle is foreign");
        return state;
    }

    private State workingState(ArmHandle handle) {
        if (handle == null) reject("authority evidence handle is invalid");
        TransactionState transaction = transactionState(false);
        State published = states.get(handle);
        if (transaction != null && transaction.staged.containsKey(handle)) {
            published = transaction.staged.get(handle);
        }
        if (published == null) reject("authority evidence handle is foreign");
        if (receipts == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return state(handle);
        }
        transaction = transactionState(true);
        State staged = transaction.staged.get(handle);
        if (staged == null) {
            staged = copyState(published);
            transaction.staged.put(handle, staged);
        }
        return staged;
    }

    private void publish(ArmHandle handle, State state) {
        if (receipts == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            states.put(handle, state);
            return;
        }
        transactionState(true).staged.put(handle, state);
    }

    private TransactionState transactionState(boolean create) {
        if (receipts == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return null;
        }
        if (TransactionSynchronizationManager.hasResource(this)) {
            return (TransactionState) TransactionSynchronizationManager.getResource(this);
        }
        if (!create) return null;
        TransactionState transaction = new TransactionState();
        TransactionSynchronizationManager.bindResource(this, transaction);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                synchronized (monitor) {
                    states.putAll(transaction.staged);
                }
                for (Runnable action : transaction.commitActions) action.run();
            }

            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    for (Runnable action : transaction.rollbackActions) action.run();
                }
                if (TransactionSynchronizationManager.hasResource(AuthorityEvidenceJournal.this)) {
                    TransactionSynchronizationManager.unbindResource(AuthorityEvidenceJournal.this);
                }
            }
        });
        return transaction;
    }

    private static State copyState(State source) {
        State copy = new State(source.binding);
        copy.events.addAll(source.events);
        copy.revision = source.revision;
        copy.generationOneDigest = source.generationOneDigest;
        copy.generationTwoDigest = source.generationTwoDigest;
        copy.reconnect = source.reconnect;
        copy.eviction = source.eviction;
        copy.capturePayload = source.capturePayload == null ? null : source.capturePayload.copy();
        copy.sealed = source.sealed;
        copy.activeLease = source.activeLease;
        return copy;
    }

    private static LifecycleEvent append(State state, String kind, int generation,
            String digest, Integer chunkX, Integer chunkZ, long revision) {
        int sequence = state.events.size() + 1;
        if (sequence == 10 && (!"capture".equals(kind) || generation != 2
                || chunkX != null || chunkZ != null)) {
            reject("capture event is invalid");
        }
        LifecycleEvent event = new LifecycleEvent(Integer.toString(sequence), kind, generation,
                digest, chunkX, chunkZ, revision);
        state.events.add(event);
        if (generation == 1 && state.generationOneDigest == null) {
            state.generationOneDigest = digest;
        }
        return event;
    }

    private static String generationDigest(State state, int generation) {
        String digest = generation == 1 ? state.generationOneDigest : state.generationTwoDigest;
        if (digest == null) reject("connection identity is missing");
        return digest;
    }

    private static String projectionFingerprint(List<CanonicalChunk> rows) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (CanonicalChunk row : rows) {
                digest.update(Integer.toString(row.chunkX).getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) ':');
                digest.update(Integer.toString(row.chunkZ).getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) ':');
                digest.update(row.finalCarrierSha256.getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) ':');
                digest.update(row.structureCarrierSha256.getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) ':');
                digest.update(Integer.toString(row.laneClaimMask).getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) ':');
                digest.update(Integer.toString(row.laneAckMask).getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) ':');
                digest.update(Integer.toString(row.laneRejectedMask).getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) ';');
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String activeConnectionDigest(State state) {
        int size = state.events.size();
        if (size >= 1 && size <= 6) return state.generationOneDigest;
        if (size >= 8 && size <= 9) return state.generationTwoDigest;
        return null;
    }

    private static Projection projection(long revision, String sourceSequence, Window window,
            List<CanonicalChunk> suppliedRows) {
        Objects.requireNonNull(suppliedRows, "canonical projection rows");
        ArrayList<CanonicalChunk> rows = new ArrayList<>(suppliedRows);
        if (rows.size() > MAX_PROJECTION_ROWS) reject("canonical projection has too many rows");
        CanonicalChunk previous = null;
        Map<Long, CanonicalChunk> unique = new HashMap<>();
        for (CanonicalChunk row : rows) {
            if (row == null) reject("canonical projection contains a null row");
            if (!window.contains(row.chunkX, row.chunkZ)) {
                reject("canonical projection row is outside the armed window");
            }
            if (previous != null && (row.chunkX < previous.chunkX
                    || (row.chunkX == previous.chunkX && row.chunkZ <= previous.chunkZ))) {
                reject("canonical projection rows are not strict x-major/z-minor order");
            }
            long key = coordinateKey(row.chunkX, row.chunkZ);
            if (unique.put(key, row) != null) reject("canonical projection has duplicate coordinates");
            previous = row;
        }
        return new Projection(revision, sourceSequence, rows);
    }

    private static void requireReconnectRelation(Projection eviction, Projection reconnect) {
        Map<Long, CanonicalChunk> reconnectRows = new HashMap<>();
        for (CanonicalChunk row : reconnect.canonicalChunks) {
            reconnectRows.put(coordinateKey(row.chunkX, row.chunkZ), row);
        }
        for (CanonicalChunk evicted : eviction.canonicalChunks) {
            CanonicalChunk current = reconnectRows.get(coordinateKey(evicted.chunkX, evicted.chunkZ));
            if (current == null || !evicted.finalCarrierSha256.equals(current.finalCarrierSha256)
                    || !evicted.structureCarrierSha256.equals(current.structureCarrierSha256)
                    || !isSubset(evicted.laneClaimMask, current.laneClaimMask)
                    || !isSubset(evicted.laneAckMask, current.laneAckMask)
                    || !isSubset(evicted.laneRejectedMask, current.laneRejectedMask)) {
                reject("reconnect projection regresses eviction state");
            }
        }
    }

    private static void requireCoordinates(int actualX, int actualZ, int expectedX, int expectedZ) {
        if (actualX != expectedX || actualZ != expectedZ) reject("probe coordinate is invalid");
    }

    private static void requireWorldId(long worldId) {
        if (worldId <= 0) throw new IllegalArgumentException("world ID must be positive");
    }

    private static void requireNickname(String nickname) {
        if (nickname == null || !NICKNAME.matcher(nickname).matches()) {
            throw new IllegalArgumentException("nickname must match ASCII [A-Za-z0-9_]{2,12}");
        }
    }

    private static String digestConnectionIdentity(String rawConnectionIdentity) {
        if (rawConnectionIdentity == null || rawConnectionIdentity.isEmpty()) {
            throw new IllegalArgumentException("connection identity must not be empty");
        }
        for (int index = 0; index < rawConnectionIdentity.length(); index++) {
            char value = rawConnectionIdentity.charAt(index);
            if (Character.isHighSurrogate(value)) {
                if (index + 1 >= rawConnectionIdentity.length()
                        || !Character.isLowSurrogate(rawConnectionIdentity.charAt(index + 1))) {
                    throw new IllegalArgumentException("connection identity has malformed UTF-16");
                }
                index++;
            } else if (Character.isLowSurrogate(value)) {
                throw new IllegalArgumentException("connection identity has malformed UTF-16");
            }
        }
        byte[] utf8 = rawConnectionIdentity.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > 4096) throw new IllegalArgumentException("connection identity is too long");
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(utf8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static String connectionIdentitySha256(String rawConnectionIdentity) {
        return digestConnectionIdentity(rawConnectionIdentity);
    }

    private static int allLanesMask() {
        int mask = 0;
        for (CanonicalWorldgenStore.Lane lane : CanonicalWorldgenStore.Lane.values()) mask |= lane.mask();
        return mask;
    }

    private static boolean isSubset(int subset, int superset) {
        return (subset & superset) == subset;
    }

    private static long coordinateKey(int chunkX, int chunkZ) {
        return ((long) chunkX << Integer.SIZE) ^ (chunkZ & 0xffffffffL);
    }

    private static void reject(String message) {
        throw new IllegalStateException(message);
    }

    private static byte[] encode(State state) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(STATE_MAGIC);
            output.writeInt(STATE_FORMAT);
            output.writeLong(state.revision);
            output.writeLong(state.binding.worldId);
            writeString(output, state.binding.nickname);
            output.writeInt(state.binding.window.minChunkX);
            output.writeInt(state.binding.window.minChunkZ);
            output.writeInt(state.binding.window.maxChunkX);
            output.writeInt(state.binding.window.maxChunkZ);
            output.writeBoolean(state.sealed);
            writeNullableString(output, state.generationOneDigest);
            writeNullableString(output, state.generationTwoDigest);
            if (state.events.size() > 10) reject("authority evidence has too many events");
            output.writeInt(state.events.size());
            for (LifecycleEvent event : state.events) {
                output.writeLong(event.revision);
                writeString(output, event.sequence);
                writeString(output, event.kind);
                output.writeInt(event.connectionGeneration);
                writeString(output, event.connectionIdentitySha256);
                output.writeBoolean(event.chunkX != null);
                if (event.chunkX != null) output.writeInt(event.chunkX);
                output.writeBoolean(event.chunkZ != null);
                if (event.chunkZ != null) output.writeInt(event.chunkZ);
            }
            writeProjection(output, state.eviction);
            writeProjection(output, state.reconnect);
            output.writeBoolean(state.capturePayload != null);
            if (state.capturePayload != null) {
                byte[] payload = state.capturePayload.durableJson;
                if (payload.length == 0 || payload.length > MAX_STATE_BYTES) {
                    reject("authority evidence capture payload is missing or too large");
                }
                output.writeInt(payload.length);
                output.write(payload);
                output.writeLong(state.capturePayload.sourceRevision);
                writeString(output, state.capturePayload.sourceFingerprint);
            }
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > MAX_STATE_BYTES) reject("authority evidence durable state is too large");
            return encoded;
        } catch (IOException impossible) {
            throw new IllegalStateException("authority evidence durable state could not be encoded",
                    impossible);
        }
    }

    private static State readDurableState(AuthorityEvidenceCreationReceipt receipt,
            ArmBinding expectedBinding) {
        byte[] encoded = receipt.evidenceState();
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_STATE_BYTES) {
            reject("authority evidence durable state is missing");
        }
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
            int magic = input.readInt();
            int format = input.readInt();
            if (magic != STATE_MAGIC || (format != 1 && format != STATE_FORMAT)) {
                reject("authority evidence durable state format is unsupported");
            }
            long revision = input.readLong();
            long worldId = input.readLong();
            String nickname = readString(input, false);
            Window window = new Window(input.readInt(), input.readInt(), input.readInt(), input.readInt());
            ArmBinding binding = new ArmBinding(worldId, nickname, window);
            if (receipt.worldId() == null || receipt.worldId() != worldId
                    || !receipt.nickname().equals(nickname)) {
                reject("authority evidence durable binding does not match its receipt");
            }
            if (expectedBinding != null && !expectedBinding.equals(binding)) {
                reject("authority evidence durable binding does not match");
            }
            State state = new State(binding);
            state.revision = revision;
            state.sealed = input.readBoolean();
            state.generationOneDigest = readString(input, true);
            state.generationTwoDigest = readString(input, true);
            int eventCount = input.readInt();
            if (eventCount < 0 || eventCount > 10) reject("authority evidence event count is invalid");
            for (int index = 0; index < eventCount; index++) {
                long eventRevision = input.readLong();
                String sequence = readString(input, false);
                String kind = readString(input, false);
                int generation = input.readInt();
                String digest = readString(input, false);
                Integer chunkX = input.readBoolean() ? input.readInt() : null;
                Integer chunkZ = input.readBoolean() ? input.readInt() : null;
                state.events.add(new LifecycleEvent(sequence, kind, generation, digest,
                        chunkX, chunkZ, eventRevision));
            }
            state.eviction = readProjection(input);
            state.reconnect = readProjection(input);
            if (format >= 2) {
                if (input.readBoolean()) {
                    int payloadLength = input.readInt();
                    if (payloadLength <= 0 || payloadLength > MAX_STATE_BYTES) {
                        reject("authority evidence capture payload length is invalid");
                    }
                    byte[] payload = input.readNBytes(payloadLength);
                    if (payload.length != payloadLength) throw new EOFException();
                    long sourceRevision = input.readLong();
                    String sourceFingerprint = readString(input, false);
                    state.capturePayload = new CapturePayload(payload,
                            state.eviction == null ? List.of() : state.eviction.canonicalChunks,
                            state.reconnect == null ? List.of() : state.reconnect.canonicalChunks,
                            sourceRevision, sourceFingerprint);
                }
            }
            if (input.read() != -1) reject("authority evidence durable state has trailing bytes");
            if (receipt.evidenceRevision() != state.revision) {
                reject("authority evidence revision is missing or replayed");
            }
            validateDurableState(state);
            return state;
        } catch (EOFException | CharacterCodingException malformed) {
            throw new IllegalStateException("authority evidence durable state is truncated", malformed);
        } catch (IOException malformed) {
            throw new IllegalStateException("authority evidence durable state is malformed", malformed);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalStateException("authority evidence durable state is malformed", malformed);
        }
    }

    private static void validateDurableState(State state) {
        if (state.revision <= 0 || state.revision == Long.MAX_VALUE) {
            reject("authority evidence durable revision is invalid");
        }
        int size = state.events.size();
        if (state.revision != size + 1 || (state.sealed != (size == 10))) {
            reject("authority evidence durable revision is not exact");
        }
        String generationOne = null;
        String generationTwo = null;
        for (int index = 0; index < size; index++) {
            LifecycleEvent event = state.events.get(index);
            int sequence = index + 1;
            if (!Integer.toString(sequence).equals(event.sequence)
                    || event.revision != sequence + 1L
                    || !DIGEST.matcher(event.connectionIdentitySha256).matches()) {
                reject("authority evidence lifecycle revision is missing or replayed");
            }
            int expectedGeneration = sequence <= 7 ? 1 : 2;
            if (event.connectionGeneration != expectedGeneration) {
                reject("authority evidence lifecycle generation is invalid");
            }
            switch (sequence) {
                case 1 -> requireEvent(event, "session-joined", 1, null, null);
                case 2 -> requireEvent(event, "chunk-activated", 1, -54, 222);
                case 3 -> requireEvent(event, "player-moved", 1, -54, 222);
                case 4 -> requireEvent(event, "chunk-activated", 1, 1, 186);
                case 5 -> requireEvent(event, "player-moved", 1, 1, 186);
                case 6 -> requireEvent(event, "chunk-evicted", 1, -54, 222);
                case 7 -> requireEvent(event, "session-left", 1, null, null);
                case 8 -> requireEvent(event, "session-joined", 2, null, null);
                case 9 -> requireEvent(event, "chunk-activated", 2, -54, 222);
                case 10 -> requireEvent(event, "capture", 2, null, null);
                default -> reject("authority evidence lifecycle sequence is invalid");
            }
            if (event.connectionGeneration == 1) {
                if (generationOne == null) generationOne = event.connectionIdentitySha256;
                if (!generationOne.equals(event.connectionIdentitySha256)) {
                    reject("authority evidence generation-one identity changed");
                }
            } else {
                if (generationTwo == null) generationTwo = event.connectionIdentitySha256;
                if (!generationTwo.equals(event.connectionIdentitySha256)) {
                    reject("authority evidence generation-two identity changed");
                }
            }
        }
        if (!Objects.equals(generationOne, state.generationOneDigest)
                || !Objects.equals(generationTwo, state.generationTwoDigest)) {
            reject("authority evidence lifecycle identity is inconsistent");
        }
        if (generationOne != null && generationTwo != null && generationOne.equals(generationTwo)) {
            reject("authority evidence connection generations are not distinct");
        }
        if ((size < 6) != (state.eviction == null)
                || (size < 9) != (state.reconnect == null)) {
            reject("authority evidence projection presence is inconsistent");
        }
        if (state.eviction != null) {
            validateProjection(state.eviction, state.binding.window, 7L, "6",
                    "authority evidence eviction");
        }
        if (state.reconnect != null) {
            validateProjection(state.reconnect, state.binding.window, 10L, "9",
                    "authority evidence reconnect");
            if (state.eviction != null) requireReconnectRelation(state.eviction, state.reconnect);
        }
        if (state.sealed) {
            if (state.capturePayload == null) {
                reject("sealed authority evidence is missing its complete capture payload");
            }
            requireCapturePayload(state, state.capturePayload);
        } else if (state.capturePayload != null) {
            reject("unsealed authority evidence owns a capture payload");
        }
    }

    private static void validateProjection(Projection projection, Window window,
            long expectedRevision, String expectedSourceSequence, String label) {
        if (projection.schema != SCHEMA_V2 || projection.revision != expectedRevision
                || !expectedSourceSequence.equals(projection.sourceSequence)) {
            reject(label + " revision is invalid");
        }
        CanonicalChunk previous = null;
        Map<Long, CanonicalChunk> unique = new HashMap<>();
        for (CanonicalChunk row : projection.canonicalChunks) {
            if (row == null || !window.contains(row.chunkX, row.chunkZ)) {
                reject(label + " row is outside the armed window");
            }
            if (previous != null && (row.chunkX < previous.chunkX
                    || (row.chunkX == previous.chunkX && row.chunkZ <= previous.chunkZ))) {
                reject(label + " rows are not strict x-major/z-minor order");
            }
            if (unique.put(coordinateKey(row.chunkX, row.chunkZ), row) != null) {
                reject(label + " has duplicate coordinates");
            }
            previous = row;
        }
    }

    private static void requireEvent(LifecycleEvent event, String kind, int generation,
            Integer chunkX, Integer chunkZ) {
        if (!kind.equals(event.kind) || event.connectionGeneration != generation
                || !Objects.equals(event.chunkX, chunkX) || !Objects.equals(event.chunkZ, chunkZ)) {
            reject("authority evidence lifecycle event is invalid");
        }
    }

    private static void writeProjection(DataOutputStream output, Projection projection)
            throws IOException {
        output.writeBoolean(projection != null);
        if (projection == null) return;
        output.writeLong(projection.revision);
        writeString(output, projection.sourceSequence);
        output.writeInt(projection.canonicalChunks.size());
        for (CanonicalChunk row : projection.canonicalChunks) {
            output.writeInt(row.chunkX);
            output.writeInt(row.chunkZ);
            writeString(output, row.finalCarrierSha256);
            writeString(output, row.structureCarrierSha256);
            output.writeInt(row.laneClaimMask);
            output.writeInt(row.laneAckMask);
            output.writeInt(row.laneRejectedMask);
        }
    }

    private static Projection readProjection(DataInputStream input)
            throws IOException, CharacterCodingException {
        if (!input.readBoolean()) return null;
        long revision = input.readLong();
        String sourceSequence = readString(input, false);
        int rowCount = input.readInt();
        if (rowCount < 0 || rowCount > MAX_PROJECTION_ROWS) {
            reject("authority evidence projection row count is invalid");
        }
        ArrayList<CanonicalChunk> rows = new ArrayList<>(rowCount);
        for (int index = 0; index < rowCount; index++) {
            rows.add(new CanonicalChunk(input.readInt(), input.readInt(),
                    readString(input, false), readString(input, false), input.readInt(),
                    input.readInt(), input.readInt()));
        }
        return new Projection(revision, sourceSequence, rows);
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        if (value == null) throw new IllegalArgumentException("durable state string is null");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) throw new IllegalArgumentException("durable state string is too long");
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static void writeNullableString(DataOutputStream output, String value) throws IOException {
        if (value == null) output.writeInt(-1);
        else writeString(output, value);
    }

    private static String readString(DataInputStream input, boolean nullable)
            throws IOException, CharacterCodingException {
        int length = input.readInt();
        if (length == -1 && nullable) return null;
        if (length < 0 || length > MAX_STRING_BYTES) {
            reject("authority evidence durable string length is invalid");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new EOFException();
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static Recovery recovery(ArmHandle handle, State state) {
        return new Recovery(handle, state.binding, state.events.size() + 1, state.revision,
                state.sealed, activeConnectionDigest(state), state.sealed
                        ? new FinalSnapshot(state) : null);
    }

    public static final class Recovery {
        private final ArmHandle handle;
        private final ArmBinding binding;
        private final int nextEvent;
        private final long revision;
        private final boolean sealed;
        private final String activeConnectionIdentitySha256;
        private final Snapshot sealedSnapshot;

        private Recovery(ArmHandle handle, ArmBinding binding, int nextEvent, long revision,
                boolean sealed, String activeConnectionIdentitySha256, Snapshot sealedSnapshot) {
            this.handle = handle;
            this.binding = binding;
            this.nextEvent = nextEvent;
            this.revision = revision;
            this.sealed = sealed;
            this.activeConnectionIdentitySha256 = activeConnectionIdentitySha256;
            this.sealedSnapshot = sealedSnapshot;
        }

        public ArmHandle handle() { return handle; }
        public ArmBinding binding() { return binding; }
        public int nextEvent() { return nextEvent; }
        public long revision() { return revision; }
        public boolean sealed() { return sealed; }
        public boolean activeSession() { return activeConnectionIdentitySha256 != null; }
        public int activeConnectionGeneration() { return nextEvent >= 9 ? 2 : 1; }
        public String activeConnectionIdentitySha256() { return activeConnectionIdentitySha256; }
        public Snapshot sealedSnapshot() { return sealedSnapshot; }
        public CapturePayload capturePayload() {
            return sealedSnapshot == null ? null : sealedSnapshot.capturePayload();
        }

        public boolean activeConnectionMatches(String rawConnectionIdentity) {
            return activeConnectionIdentitySha256 != null
                    && activeConnectionIdentitySha256.equals(digestConnectionIdentity(rawConnectionIdentity));
        }
    }

    public static final class Window {
        private final int minChunkX;
        private final int minChunkZ;
        private final int maxChunkX;
        private final int maxChunkZ;

        public Window(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
            if (minChunkX > maxChunkX || minChunkZ > maxChunkZ
                    || (long) maxChunkX - minChunkX > MAX_WINDOW_DISTANCE
                    || (long) maxChunkZ - minChunkZ > MAX_WINDOW_DISTANCE) {
                throw new IllegalArgumentException("authority evidence window must be at most 17x17");
            }
            this.minChunkX = minChunkX;
            this.minChunkZ = minChunkZ;
            this.maxChunkX = maxChunkX;
            this.maxChunkZ = maxChunkZ;
        }

        public int minChunkX() { return minChunkX; }
        public int minChunkZ() { return minChunkZ; }
        public int maxChunkX() { return maxChunkX; }
        public int maxChunkZ() { return maxChunkZ; }
        private boolean contains(int chunkX, int chunkZ) {
            return chunkX >= minChunkX && chunkX <= maxChunkX
                    && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Window value)) return false;
            return minChunkX == value.minChunkX && minChunkZ == value.minChunkZ
                    && maxChunkX == value.maxChunkX && maxChunkZ == value.maxChunkZ;
        }

        @Override public int hashCode() { return Objects.hash(minChunkX, minChunkZ, maxChunkX, maxChunkZ); }
    }

    public static final class ArmHandle {
        private ArmHandle() { }
        @Override public String toString() { return "ArmHandle{opaque}"; }
    }

    public static final class ArmBinding {
        private final long worldId;
        private final String nickname;
        private final Window window;

        private ArmBinding(long worldId, String nickname, Window window) {
            this.worldId = worldId;
            this.nickname = nickname;
            this.window = window;
        }

        public long worldId() { return worldId; }
        public String nickname() { return nickname; }
        public Window window() { return window; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ArmBinding value)) return false;
            return worldId == value.worldId && nickname.equals(value.nickname)
                    && window.equals(value.window);
        }

        @Override public int hashCode() { return Objects.hash(worldId, nickname, window); }
    }

    public static final class LifecycleEvent {
        private final int schema = SCHEMA;
        private final String sequence;
        private final String kind;
        private final int connectionGeneration;
        private final String connectionIdentitySha256;
        private final Integer chunkX;
        private final Integer chunkZ;
        private final long revision;

        private LifecycleEvent(String sequence, String kind, int connectionGeneration,
                String connectionIdentitySha256, Integer chunkX, Integer chunkZ, long revision) {
            this.sequence = sequence;
            this.kind = kind;
            this.connectionGeneration = connectionGeneration;
            this.connectionIdentitySha256 = connectionIdentitySha256;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.revision = revision;
        }

        public int schema() { return schema; }
        public String sequence() { return sequence; }
        public String kind() { return kind; }
        public int connectionGeneration() { return connectionGeneration; }
        public String connectionIdentitySha256() { return connectionIdentitySha256; }
        public Integer chunkX() { return chunkX; }
        public Integer chunkZ() { return chunkZ; }
        public long revision() { return revision; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof LifecycleEvent value)) return false;
            return schema == value.schema && connectionGeneration == value.connectionGeneration
                    && revision == value.revision && sequence.equals(value.sequence)
                    && kind.equals(value.kind) && connectionIdentitySha256.equals(value.connectionIdentitySha256)
                    && Objects.equals(chunkX, value.chunkX) && Objects.equals(chunkZ, value.chunkZ);
        }

        @Override public int hashCode() {
            return Objects.hash(schema, sequence, kind, connectionGeneration,
                    connectionIdentitySha256, chunkX, chunkZ, revision);
        }
    }

    public static final class CanonicalChunk {
        private final int chunkX;
        private final int chunkZ;
        private final String finalCarrierSha256;
        private final String structureCarrierSha256;
        private final int laneClaimMask;
        private final int laneAckMask;
        private final int laneRejectedMask;

        public CanonicalChunk(int chunkX, int chunkZ, String finalCarrierSha256,
                String structureCarrierSha256, int laneClaimMask, int laneAckMask,
                int laneRejectedMask) {
            requireDigest(finalCarrierSha256, "final carrier digest");
            requireDigest(structureCarrierSha256, "structure carrier digest");
            requireMask(laneClaimMask, "claim");
            requireMask(laneAckMask, "ack");
            requireMask(laneRejectedMask, "rejected");
            if (!isSubset(laneAckMask, laneClaimMask)
                    || !isSubset(laneRejectedMask, laneClaimMask)
                    || (laneAckMask & laneRejectedMask) != 0) {
                throw new IllegalArgumentException("canonical lane masks are not disjoint subsets");
            }
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.finalCarrierSha256 = finalCarrierSha256;
            this.structureCarrierSha256 = structureCarrierSha256;
            this.laneClaimMask = laneClaimMask;
            this.laneAckMask = laneAckMask;
            this.laneRejectedMask = laneRejectedMask;
        }

        public int chunkX() { return chunkX; }
        public int chunkZ() { return chunkZ; }
        public String finalCarrierSha256() { return finalCarrierSha256; }
        public String structureCarrierSha256() { return structureCarrierSha256; }
        public int laneClaimMask() { return laneClaimMask; }
        public int laneAckMask() { return laneAckMask; }
        public int laneRejectedMask() { return laneRejectedMask; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CanonicalChunk value)) return false;
            return chunkX == value.chunkX && chunkZ == value.chunkZ
                    && laneClaimMask == value.laneClaimMask && laneAckMask == value.laneAckMask
                    && laneRejectedMask == value.laneRejectedMask
                    && finalCarrierSha256.equals(value.finalCarrierSha256)
                    && structureCarrierSha256.equals(value.structureCarrierSha256);
        }

        @Override public int hashCode() {
            return Objects.hash(chunkX, chunkZ, finalCarrierSha256, structureCarrierSha256,
                    laneClaimMask, laneAckMask, laneRejectedMask);
        }

        private static void requireDigest(String digest, String label) {
            if (digest == null || !DIGEST.matcher(digest).matches()) {
                throw new IllegalArgumentException(label + " must be lower-case SHA-256 hex");
            }
        }

        private static void requireMask(int mask, String label) {
            if (mask < 0 || (mask & ~ALL_LANES_MASK) != 0) {
                throw new IllegalArgumentException(label + " lane mask is outside the canonical lanes");
            }
        }
    }

    public static final class Projection {
        private final int schema = SCHEMA_V2;
        private final long revision;
        private final String sourceSequence;
        private final List<CanonicalChunk> canonicalChunks;
        private final String sourceFingerprint;

        private Projection(long revision, String sourceSequence, List<CanonicalChunk> canonicalChunks) {
            this.revision = revision;
            this.sourceSequence = sourceSequence;
            this.canonicalChunks = List.copyOf(canonicalChunks);
            this.sourceFingerprint = projectionFingerprint(this.canonicalChunks);
        }

        public int schema() { return schema; }
        public long revision() { return revision; }
        public String sourceSequence() { return sourceSequence; }
        public List<CanonicalChunk> canonicalChunks() { return canonicalChunks; }
        public String sourceFingerprint() { return sourceFingerprint; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Projection value)) return false;
            return schema == value.schema && revision == value.revision
                    && sourceSequence.equals(value.sourceSequence)
                    && canonicalChunks.equals(value.canonicalChunks);
        }

        @Override public int hashCode() { return Objects.hash(schema, revision, sourceSequence, canonicalChunks); }
    }

    public sealed interface Snapshot permits FinalSnapshot {
        int schema();
        String abi();
        long revision();
        ArmBinding binding();
        List<LifecycleEvent> events();
        Projection reconnect();
        Projection eviction();
        CapturePayload capturePayload();
    }

    public static final class FinalSnapshot implements Snapshot {
        private final ArmBinding binding;
        private final long revision;
        private final List<LifecycleEvent> events;
        private final Projection reconnect;
        private final Projection eviction;
        private final CapturePayload capturePayload;

        private FinalSnapshot(State state) {
            this.binding = state.binding;
            this.revision = state.revision;
            this.events = List.copyOf(state.events);
            this.reconnect = state.reconnect;
            this.eviction = state.eviction;
            this.capturePayload = state.capturePayload == null ? null : state.capturePayload.copy();
        }

        @Override public int schema() { return SCHEMA_V2; }
        @Override public String abi() { return ABI; }
        @Override public long revision() { return revision; }
        @Override public ArmBinding binding() { return binding; }
        @Override public List<LifecycleEvent> events() { return events; }
        @Override public Projection reconnect() { return reconnect; }
        @Override public Projection eviction() { return eviction; }
        @Override public CapturePayload capturePayload() { return capturePayload; }

        @Override public String toString() {
            return "FinalSnapshot{worldId=" + binding.worldId + ", nickname="
                    + binding.nickname + ", events=" + events.size() + ", revision=" + revision + "}";
        }
    }

    public static final class CaptureLease implements AutoCloseable {
        private final AuthorityEvidenceJournal journal;
        private final ArmHandle handle;
        private final State state;
        private boolean closed;

        private CaptureLease(AuthorityEvidenceJournal journal, ArmHandle handle, State state) {
            this.journal = journal;
            this.handle = handle;
            this.state = state;
        }

        public Snapshot seal() {
            return journal.seal(this, null);
        }

        /** Seals a durable lease with the already verified, detached response payload. */
        public Snapshot seal(CapturePayload payload) {
            return journal.seal(this, payload);
        }

        /** Canonical rows captured by the locked eviction projection for this lease. */
        public List<CanonicalChunk> evictionRows() {
            synchronized (journal.monitor) {
                return state.eviction == null ? List.of() : state.eviction.canonicalChunks;
            }
        }

        /** Canonical rows captured by the locked reconnect projection for this lease. */
        public List<CanonicalChunk> reconnectRows() {
            synchronized (journal.monitor) {
                return state.reconnect == null ? List.of() : state.reconnect.canonicalChunks;
            }
        }

        @Override public void close() {
            synchronized (journal.monitor) {
                if (closed) return;
                if (state.activeLease == this && !state.sealed) state.activeLease = null;
                closed = true;
            }
        }
    }

    private static final class State {
        private final ArmBinding binding;
        private final List<LifecycleEvent> events = new ArrayList<>();
        private long revision;
        private String generationOneDigest;
        private String generationTwoDigest;
        private Projection reconnect;
        private Projection eviction;
        private CapturePayload capturePayload;
        private CaptureLease activeLease;
        private boolean sealed;

        private State(ArmBinding binding) {
            this.binding = binding;
        }
    }

    /** Complete response material persisted with a sealed journal image. */
    public static final class CapturePayload {
        private final byte[] durableJson;
        private final List<CanonicalChunk> evictionRows;
        private final List<CanonicalChunk> reconnectRows;
        private final long sourceRevision;
        private final String sourceFingerprint;

        public CapturePayload(byte[] durableJson, List<CanonicalChunk> evictionRows,
                List<CanonicalChunk> reconnectRows) {
            this(durableJson, evictionRows, reconnectRows,
                    10L,
                    captureSourceFingerprint(new Projection(7L, "6", evictionRows),
                            new Projection(10L, "9", reconnectRows)));
        }

        private CapturePayload(byte[] durableJson, List<CanonicalChunk> evictionRows,
                List<CanonicalChunk> reconnectRows, long sourceRevision, String sourceFingerprint) {
            if (durableJson == null || durableJson.length == 0) {
                throw new IllegalArgumentException("authority evidence capture payload is empty");
            }
            if (sourceRevision <= 0 || sourceRevision == Long.MAX_VALUE) {
                throw new IllegalArgumentException("authority evidence source revision is invalid");
            }
            this.durableJson = durableJson.clone();
            this.evictionRows = List.copyOf(Objects.requireNonNull(
                    evictionRows, "authority evidence eviction rows"));
            this.reconnectRows = List.copyOf(Objects.requireNonNull(
                    reconnectRows, "authority evidence reconnect rows"));
            if (sourceFingerprint == null || (!DIGEST.matcher(sourceFingerprint).matches()
                    && !sourceFingerprint.matches("[0-9a-f]{128}"))) {
                throw new IllegalArgumentException("authority evidence source fingerprint is invalid");
            }
            this.sourceRevision = sourceRevision;
            this.sourceFingerprint = sourceFingerprint;
        }

        public byte[] durableJson() { return durableJson.clone(); }
        public List<CanonicalChunk> evictionRows() { return evictionRows; }
        public List<CanonicalChunk> reconnectRows() { return reconnectRows; }
        public long sourceRevision() { return sourceRevision; }
        public String sourceFingerprint() { return sourceFingerprint; }

        private CapturePayload copy() {
            return new CapturePayload(durableJson, evictionRows, reconnectRows,
                    sourceRevision, sourceFingerprint);
        }
    }

    private static final class DurableRecord {
        private final AuthorityEvidenceCreationReceipt receipt;

        private DurableRecord(AuthorityEvidenceCreationReceipt receipt) {
            this.receipt = receipt;
        }
    }

    private static final class TransactionState {
        private final IdentityHashMap<ArmHandle, State> staged = new IdentityHashMap<>();
        private final List<Runnable> commitActions = new ArrayList<>();
        private final List<Runnable> rollbackActions = new ArrayList<>();
    }
}
