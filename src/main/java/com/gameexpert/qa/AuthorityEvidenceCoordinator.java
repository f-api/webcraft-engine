package com.gameexpert.qa;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Spring-owned lifecycle boundary for keyed authority evidence sessions. */
@Component
public final class AuthorityEvidenceCoordinator {
    private static final int JOIN = 1;
    private static final int FIRST_CHUNK = 2;
    private static final int FIRST_MOVE = 3;
    private static final int SECOND_CHUNK = 4;
    private static final int SECOND_MOVE = 5;
    private static final int EVICTION = 6;
    private static final int LEFT = 7;
    private static final int REJOIN = 8;
    private static final int RECONNECT = 9;
    private static final int CAPTURE = 10;
    private static final int PROBE_CHUNK_X = -54;
    private static final int PROBE_CHUNK_Z = 222;

    private final AuthorityEvidenceJournal journal;
    private final AuthorityEvidenceService service;
    private final CloseObservationHook closeObservationHook;
    private final Map<BindingKey, BindingState> bindings = new HashMap<>();

    @Autowired
    public AuthorityEvidenceCoordinator(AuthorityEvidenceJournal journal,
            AuthorityEvidenceService service) {
        this(journal, service, null);
    }

    static AuthorityEvidenceCoordinator forCloseRaceTest(AuthorityEvidenceJournal journal,
            AuthorityEvidenceService service, CloseObservationHook closeObservationHook) {
        return new AuthorityEvidenceCoordinator(journal, service, closeObservationHook);
    }

    private AuthorityEvidenceCoordinator(AuthorityEvidenceJournal journal,
            AuthorityEvidenceService service, CloseObservationHook closeObservationHook) {
        this.journal = Objects.requireNonNull(journal, "authority evidence journal");
        this.service = Objects.requireNonNull(service, "authority evidence service");
        this.closeObservationHook = closeObservationHook;
    }

    public void arm(long worldId, String nickname, AuthorityEvidenceJournal.Window window) {
        BindingKey key = new BindingKey(worldId, nickname);
        synchronized (bindings) {
            if (binding(key) != null) reject("authority evidence binding is already active");
            AuthorityEvidenceJournal.ArmHandle handle = journal.arm(worldId, nickname, window);
            BindingState state = new BindingState(key, handle, window, 1L);
            publish(key, state);
        }
    }

    public SessionHandle sessionJoined(long worldId, String nickname,
            String rawConnectionIdentity) {
        return sessionJoined(armed(new BindingKey(worldId, nickname)), rawConnectionIdentity);
    }

    /** Product lifecycle adapter: absence is ordinary traffic; an observed binding stays strict. */
    public Optional<SessionHandle> sessionJoinedIfArmed(long worldId, String nickname,
            String rawConnectionIdentity) {
        BindingKey key = new BindingKey(worldId, nickname);
        BindingState state = recoverIfPresent(key);
        if (state == null || state.sealed) return Optional.empty();
        if (state.currentSession != null) {
            if (journal.activeConnectionMatches(state.journalHandle, rawConnectionIdentity)) {
                return Optional.of(state.currentSession);
            }
            reject("authority evidence session identity is stale");
        }
        return Optional.of(sessionJoined(state, rawConnectionIdentity));
    }

    private SessionHandle sessionJoined(BindingState state, String rawConnectionIdentity) {
        if (state.currentSession != null) reject("authority evidence session is already active");
        acquire(state, Pending.JOIN);
        try {
            requireArmed(state);
            if (state.currentSession != null) {
                reject("authority evidence session is already active");
            }
            requireNext(state, JOIN, REJOIN);
            AuthorityEvidenceJournal.LifecycleEvent event = journal.sessionJoined(
                    state.journalHandle, rawConnectionIdentity);
            SessionHandle handle = new SessionHandle(this, state.key, state.journalHandle,
                    event.connectionGeneration(), event.connectionIdentitySha256(), event.revision());
            afterCommit(() -> {
                state.currentSession = handle;
                state.nextEvent = eventSequence(event) + 1;
                state.expectedRevision = event.revision();
                state.expectedGeneration = event.connectionGeneration();
                state.expectedIdentitySha256 = event.connectionIdentitySha256();
            }, () -> { });
            return handle;
        } finally {
            release(state, Pending.JOIN);
        }
    }

    public void chunkActivated(SessionHandle session, int chunkX, int chunkZ) {
        chunkActivated(session, null, chunkX, chunkZ);
    }

    /** Identity-bearing callback path; the legacy overload is retained for old runtime adapters. */
    public void chunkActivated(SessionHandle session, String rawConnectionIdentity,
            int chunkX, int chunkZ) {
        BindingState state = beginSession(session);
        try {
            requireConnection(state, session, rawConnectionIdentity);
            requireNext(state, FIRST_CHUNK, SECOND_CHUNK, RECONNECT);
            if (state.nextEvent == RECONNECT) {
                requireProbeCoordinates(chunkX, chunkZ);
                var projection = service.projectCanonicalChunks(
                        state.key.worldId, state.window);
                requireSession(state, session);
                requireNext(state, RECONNECT);
                AuthorityEvidenceJournal.LifecycleEvent event = journal.reconnectedChunkActivated(
                        state.journalHandle, chunkX, chunkZ, projection);
                advanceAfterCommit(state, event);
            } else {
                AuthorityEvidenceJournal.LifecycleEvent event = journal.chunkActivated(
                        state.journalHandle, chunkX, chunkZ);
                advanceAfterCommit(state, event);
            }
        } finally {
            release(state, Pending.EVENT);
        }
    }

    public void playerMoved(SessionHandle session, int chunkX, int chunkZ) {
        playerMoved(session, null, chunkX, chunkZ);
    }

    /** Identity-bearing callback path; the legacy overload is retained for old runtime adapters. */
    public void playerMoved(SessionHandle session, String rawConnectionIdentity,
            int chunkX, int chunkZ) {
        BindingState state = beginSession(session);
        try {
            requireConnection(state, session, rawConnectionIdentity);
            requireNext(state, FIRST_MOVE, SECOND_MOVE);
            AuthorityEvidenceJournal.LifecycleEvent event = journal.playerMoved(
                    state.journalHandle, chunkX, chunkZ);
            advanceAfterCommit(state, event);
        } finally {
            release(state, Pending.EVENT);
        }
    }

    public void capacityChunkEvicted(SessionHandle session, int chunkX, int chunkZ) {
        capacityChunkEvicted(session, null, chunkX, chunkZ);
    }

    /** Identity-bearing callback path; the legacy overload is retained for old runtime adapters. */
    public void capacityChunkEvicted(SessionHandle session, String rawConnectionIdentity,
            int chunkX, int chunkZ) {
        BindingState state = beginSession(session);
        try {
            requireConnection(state, session, rawConnectionIdentity);
            requireNext(state, EVICTION);
            requireProbeCoordinates(chunkX, chunkZ);
            var projection = service.projectCanonicalChunks(
                    state.key.worldId, state.window);
            requireSession(state, session);
            requireNext(state, EVICTION);
            AuthorityEvidenceJournal.LifecycleEvent event = journal.chunkEvicted(
                    state.journalHandle, chunkX, chunkZ, projection);
            advanceAfterCommit(state, event);
        } finally {
            release(state, Pending.EVENT);
        }
    }

    public void sessionLeft(SessionHandle session) {
        sessionLeft(session, null);
    }

    public void sessionLeft(SessionHandle session, String rawConnectionIdentity) {
        BindingState state = beginSession(session);
        try {
            requireConnection(state, session, rawConnectionIdentity);
            requireNext(state, LEFT);
            if (rawConnectionIdentity != null
                    && !journal.activeConnectionMatches(state.journalHandle, rawConnectionIdentity)) {
                reject("authority evidence session identity is stale");
            }
            AuthorityEvidenceJournal.LifecycleEvent event = journal.sessionLeft(
                    state.journalHandle);
            afterCommit(() -> {
                state.currentSession = null;
                state.nextEvent = eventSequence(event) + 1;
                state.expectedRevision = event.revision();
            }, () -> { });
        } finally {
            release(state, Pending.EVENT);
        }
    }

    public CaptureLease acquireCaptureLease(long worldId, String nickname,
            AuthorityEvidenceJournal.Window expectedWindow) {
        return acquireCaptureLease(worldId, nickname, expectedWindow, null);
    }

    /** Capture variant for callers that carry a previously committed revision. */
    public CaptureLease acquireCaptureLease(long worldId, String nickname,
            AuthorityEvidenceJournal.Window expectedWindow, long expectedRevision) {
        if (expectedRevision <= 0) reject("authority evidence revision is missing");
        return acquireCaptureLease(worldId, nickname, expectedWindow, Long.valueOf(expectedRevision));
    }

    private CaptureLease acquireCaptureLease(long worldId, String nickname,
            AuthorityEvidenceJournal.Window expectedWindow, Long expectedRevision) {
        BindingState state = workingBinding(armed(new BindingKey(worldId, nickname)));
        requireWindow(state, expectedWindow);
        acquire(state, Pending.ACQUIRE_LEASE);
        try {
            requireArmed(state);
            requireWindow(state, expectedWindow);
            requireNext(state, CAPTURE);
            if (expectedRevision != null
                    && journal.currentRevision(state.journalHandle) != expectedRevision) {
                reject("authority evidence revision is stale or replayed");
            }
            if (state.currentLease != null) {
                reject("authority evidence capture lease is already active");
            }
            AuthorityEvidenceJournal.CaptureLease journalLease =
                    journal.acquireCaptureLease(state.journalHandle);
            requireArmed(state);
            CaptureLease lease = new CaptureLease(this, state, journalLease);
            state.currentLease = lease;
            afterCommit(() -> { }, () -> {
                if (state.currentLease == lease) state.currentLease = null;
            });
            return lease;
        } finally {
            release(state, Pending.ACQUIRE_LEASE);
        }
    }

    /** Recovered runtime callback route for a named player after Spring recreation. */
    public Optional<SessionHandle> sessionForCallbacks(long worldId, String nickname) {
        BindingState state = recoverIfPresent(new BindingKey(worldId, nickname));
        if (state == null || state.sealed || state.currentSession == null) return Optional.empty();
        return Optional.of(state.currentSession);
    }

    /** Returns a sealed payload for an idempotent capture retry after the HTTP response was lost. */
    public Optional<AuthorityEvidenceJournal.Snapshot> recoverSealedCapture(long worldId,
            String nickname, AuthorityEvidenceJournal.Window expectedWindow) {
        Optional<AuthorityEvidenceJournal.Recovery> recovered = journal.recover(worldId, nickname);
        if (recovered.isEmpty() || !recovered.get().sealed()) return Optional.empty();
        if (!recovered.get().binding().window().equals(expectedWindow)) {
            reject("authority evidence window does not match the sealed binding");
        }
        return Optional.ofNullable(recovered.get().sealedSnapshot());
    }

    /** Recovered runtime callback route for the one world binding at an exact next event. */
    public Optional<SessionHandle> sessionForCallback(long worldId, int expectedNextEvent) {
        Optional<AuthorityEvidenceJournal.Recovery> recovered = journal.recover(worldId);
        if (recovered.isEmpty() || recovered.get().sealed()
                || recovered.get().nextEvent() != expectedNextEvent
                || !recovered.get().activeSession()) {
            return Optional.empty();
        }
        BindingState state = restore(recovered.get());
        synchronized (bindings) {
            BindingState existing = binding(state.key);
            if (existing != null && existing.journalHandle == state.journalHandle
                    && existing.expectedRevision == state.expectedRevision) {
                state = existing;
            } else {
                publish(state.key, state);
            }
        }
        return state.currentSession == null ? Optional.empty()
                : Optional.of(state.currentSession);
    }

    CallbackPhase callbackPhase(SessionHandle session) {
        BindingState state = requireSessionHandle(session);
        return callbackPhase(state.nextEvent);
    }

    String callbackNickname(SessionHandle session) {
        return requireSessionHandle(session).key.nickname;
    }

    boolean activeConnectionMatches(SessionHandle session, String rawConnectionIdentity) {
        BindingState state = requireSessionHandle(session);
        return journal.activeConnectionMatches(state.journalHandle, rawConnectionIdentity);
    }

    boolean firstConnectionMatches(long worldId, String nickname, String rawConnectionIdentity) {
        BindingState state = recoverIfPresent(new BindingKey(worldId, nickname));
        return state != null && journal.firstConnectionMatches(state.journalHandle, rawConnectionIdentity);
    }

    private static CallbackPhase callbackPhase(int nextEvent) {
        return switch (nextEvent) {
            case FIRST_CHUNK -> CallbackPhase.FIRST_CHUNK;
            case FIRST_MOVE -> CallbackPhase.FIRST_MOVE;
            case SECOND_CHUNK -> CallbackPhase.SECOND_CHUNK;
            case SECOND_MOVE -> CallbackPhase.SECOND_MOVE;
            case EVICTION -> CallbackPhase.EVICTION;
            case LEFT -> CallbackPhase.LEAVE;
            case RECONNECT -> CallbackPhase.RECONNECTED_CHUNK;
            default -> null;
        };
    }

    private BindingState beginSession(SessionHandle session) {
        BindingState state = requireSessionHandle(session);
        acquire(state, Pending.EVENT);
        try {
            requireSession(state, session);
            return state;
        } catch (RuntimeException failure) {
            release(state, Pending.EVENT);
            throw failure;
        }
    }

    private BindingState binding(BindingKey key) {
        TransactionState transaction = transactionState(false);
        if (transaction != null && transaction.staged.containsKey(key)) {
            return transaction.staged.get(key);
        }
        synchronized (bindings) {
            return bindings.get(key);
        }
    }

    /** Returns a transaction-local binding copy before any lease state is mutated. */
    private BindingState workingBinding(BindingState published) {
        if (published == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return published;
        }
        TransactionState transaction = transactionState(true);
        BindingState staged = transaction.staged.get(published.key);
        if (staged != null) return staged;
        staged = copyBinding(published);
        transaction.staged.put(published.key, staged);
        return staged;
    }

    private void publish(BindingKey key, BindingState state) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            synchronized (bindings) {
                bindings.put(key, state);
            }
            return;
        }
        transactionState(true).staged.put(key, state);
    }

    private TransactionState transactionState(boolean create) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return null;
        if (TransactionSynchronizationManager.hasResource(this)) {
            return (TransactionState) TransactionSynchronizationManager.getResource(this);
        }
        if (!create) return null;
        TransactionState transaction = new TransactionState();
        TransactionSynchronizationManager.bindResource(this, transaction);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                synchronized (bindings) {
                    bindings.putAll(transaction.staged);
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (TransactionSynchronizationManager.hasResource(
                        AuthorityEvidenceCoordinator.this)) {
                    TransactionSynchronizationManager.unbindResource(
                            AuthorityEvidenceCoordinator.this);
                }
            }
        });
        return transaction;
    }

    private BindingState requireSessionHandle(SessionHandle session) {
        if (session == null || session.owner != this) {
            reject("authority evidence session handle is invalid");
        }
        BindingState state = binding(session.key);
        if (state == null || state.journalHandle != session.journalHandle) {
            reject("authority evidence session handle is stale");
        }
        requireSession(state, session);
        return state;
    }

    private void requireSession(BindingState state, SessionHandle session) {
        requireArmed(state);
        if (state.currentSession != session || !state.key.equals(session.key)
                || state.journalHandle != session.journalHandle
                || state.expectedGeneration != session.connectionGeneration
                || !state.expectedIdentitySha256.equals(session.connectionIdentitySha256)) {
            reject("authority evidence session handle is stale");
        }
        String activeIdentity = journal.activeConnectionIdentitySha256(state.journalHandle);
        if (!session.connectionIdentitySha256.equals(activeIdentity)) {
            reject("authority evidence session identity is stale");
        }
        long actualRevision = journal.currentRevision(state.journalHandle);
        if (actualRevision != state.expectedRevision) {
            reject("authority evidence session revision is stale or replayed");
        }
    }

    private void requireConnection(BindingState state, SessionHandle session,
            String rawConnectionIdentity) {
        if (rawConnectionIdentity == null) return;
        if (!session.connectionIdentitySha256.equals(
                AuthorityEvidenceJournal.connectionIdentitySha256(rawConnectionIdentity))) {
            reject("authority evidence session identity is stale");
        }
        if (!journal.activeConnectionMatches(state.journalHandle, rawConnectionIdentity)) {
            reject("authority evidence session identity is stale");
        }
    }

    private static void advanceAfterCommit(BindingState state,
            AuthorityEvidenceJournal.LifecycleEvent event) {
        afterCommit(() -> {
            state.nextEvent = eventSequence(event) + 1;
            state.expectedRevision = event.revision();
        }, () -> { });
    }

    private static int eventSequence(AuthorityEvidenceJournal.LifecycleEvent event) {
        try {
            return Integer.parseInt(event.sequence());
        } catch (RuntimeException failure) {
            reject("authority evidence lifecycle sequence is invalid");
            return -1;
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

    private void requireArmed(BindingState state) {
        synchronized (bindings) {
            if (state.sealed || binding(state.key) != state) {
                reject("authority evidence binding is stale");
            }
        }
    }

    private BindingState armed(BindingKey key) {
        BindingState state = recoverIfPresent(key);
        if (state == null) reject("authority evidence binding is not armed");
        return state;
    }

    private BindingState recoverIfPresent(BindingKey key) {
        synchronized (bindings) {
            BindingState current = binding(key);
            Optional<AuthorityEvidenceJournal.Recovery> recovered =
                    journal.recover(key.worldId, key.nickname);
            if (recovered.isEmpty()) return current;
            if (current != null && current.journalHandle == recovered.get().handle()
                    && current.expectedRevision == recovered.get().revision()
                    && current.sealed == recovered.get().sealed()) {
                return current;
            }
            BindingState state = restore(recovered.get());
            publish(key, state);
            return state;
        }
    }

    private BindingState recover(BindingKey key) {
        Optional<AuthorityEvidenceJournal.Recovery> recovered =
                journal.recover(key.worldId, key.nickname);
        if (recovered.isEmpty()) return null;
        BindingState state = restore(recovered.get());
        publish(key, state);
        return state;
    }

    private BindingState restore(AuthorityEvidenceJournal.Recovery recovered) {
        AuthorityEvidenceJournal.ArmBinding binding = recovered.binding();
        BindingKey key = new BindingKey(binding.worldId(), binding.nickname());
        BindingState state = new BindingState(key, recovered.handle(), binding.window(),
                recovered.revision());
        state.nextEvent = recovered.nextEvent();
        state.sealed = recovered.sealed();
        if (recovered.activeSession() && !state.sealed) {
            state.currentSession = new SessionHandle(this, key, recovered.handle(),
                    recovered.activeConnectionGeneration(),
                    recovered.activeConnectionIdentitySha256(), recovered.revision());
            state.expectedGeneration = recovered.activeConnectionGeneration();
            state.expectedIdentitySha256 = recovered.activeConnectionIdentitySha256();
        }
        return state;
    }

    private void removeExact(BindingState state) {
        synchronized (bindings) {
            if (bindings.get(state.key) == state) bindings.remove(state.key);
        }
    }

    private static void acquire(BindingState state, Pending operation) {
        if (!state.pending.compareAndSet(null, operation)) {
            reject("authority evidence binding has a pending operation");
        }
    }

    private static void release(BindingState state, Pending operation) {
        state.pending.compareAndSet(operation, null);
    }

    private static void requireNext(BindingState state, int... expected) {
        for (int value : expected) {
            if (state.nextEvent == value) return;
        }
        reject("authority evidence event is out of order");
    }

    private static void requireProbeCoordinates(int chunkX, int chunkZ) {
        if (chunkX != PROBE_CHUNK_X || chunkZ != PROBE_CHUNK_Z) {
            reject("probe coordinate is invalid");
        }
    }

    private static void requireWindow(BindingState state,
            AuthorityEvidenceJournal.Window expectedWindow) {
        if (expectedWindow == null || !state.window.equals(expectedWindow)) {
            reject("authority evidence window does not match the armed binding");
        }
    }

    private static void reject(String message) {
        throw new IllegalStateException(message);
    }

    public static final class SessionHandle {
        private final AuthorityEvidenceCoordinator owner;
        private final BindingKey key;
        private final AuthorityEvidenceJournal.ArmHandle journalHandle;
        private final int connectionGeneration;
        private final String connectionIdentitySha256;
        private final long joinedRevision;

        private SessionHandle(AuthorityEvidenceCoordinator owner, BindingKey key,
                AuthorityEvidenceJournal.ArmHandle journalHandle, int connectionGeneration,
                String connectionIdentitySha256, long joinedRevision) {
            this.owner = owner;
            this.key = key;
            this.journalHandle = journalHandle;
            this.connectionGeneration = connectionGeneration;
            this.connectionIdentitySha256 = connectionIdentitySha256;
            this.joinedRevision = joinedRevision;
        }

        public int connectionGeneration() {
            return connectionGeneration;
        }

        public long joinedRevision() {
            return joinedRevision;
        }

        @Override
        public String toString() {
            return "SessionHandle";
        }
    }

    public static final class CaptureLease implements AutoCloseable {
        private final AuthorityEvidenceCoordinator owner;
        private final BindingState binding;
        private final AuthorityEvidenceJournal.CaptureLease journalLease;
        private final AtomicReference<LeaseStatus> status =
                new AtomicReference<>(LeaseStatus.ACTIVE);

        private CaptureLease(AuthorityEvidenceCoordinator owner, BindingState binding,
                AuthorityEvidenceJournal.CaptureLease journalLease) {
            this.owner = owner;
            this.binding = binding;
            this.journalLease = journalLease;
        }

        public AuthorityEvidenceJournal.Snapshot seal() {
            return seal(null);
        }

        public AuthorityEvidenceJournal.Snapshot seal(
                AuthorityEvidenceJournal.CapturePayload payload) {
            if (status.get() != LeaseStatus.ACTIVE) {
                reject("authority evidence capture lease is not active");
            }
            owner.requireArmed(binding);
            if (!binding.pending.compareAndSet(null, Pending.SEAL_LEASE)) {
                reject("authority evidence binding has a pending operation");
            }
            try {
                if (status.get() != LeaseStatus.ACTIVE) {
                    reject("authority evidence capture lease is not active");
                }
                owner.requireArmed(binding);
                if (binding.currentLease != this) {
                    reject("authority evidence capture lease is not active");
                }
                AuthorityEvidenceJournal.Snapshot snapshot = journalLease.seal(payload);
                status.set(LeaseStatus.SEALED);
                afterCommit(() -> {
                    binding.currentLease = null;
                    binding.sealed = true;
                    owner.removeExact(binding);
                }, () -> {
                    status.set(LeaseStatus.ACTIVE);
                });
                return snapshot;
            } finally {
                binding.pending.compareAndSet(Pending.SEAL_LEASE, null);
            }
        }

        public java.util.List<AuthorityEvidenceJournal.CanonicalChunk> evictionRows() {
            return journalLease.evictionRows();
        }

        public java.util.List<AuthorityEvidenceJournal.CanonicalChunk> reconnectRows() {
            return journalLease.reconnectRows();
        }

        @Override
        public void close() {
            if (status.get() != LeaseStatus.ACTIVE) return;
            if (owner.closeObservationHook != null) {
                owner.closeObservationHook.afterActiveObservation();
            }
            if (!binding.pending.compareAndSet(null, Pending.CLOSE_LEASE)) {
                if (status.get() != LeaseStatus.ACTIVE) return;
                reject("authority evidence binding has a pending operation");
            }
            try {
                if (status.get() != LeaseStatus.ACTIVE) return;
                owner.requireArmed(binding);
                if (binding.currentLease != this) {
                    reject("authority evidence capture lease is not active");
                }
                journalLease.close();
                binding.currentLease = null;
                status.set(LeaseStatus.CLOSED);
            } finally {
                binding.pending.compareAndSet(Pending.CLOSE_LEASE, null);
            }
        }

        @Override
        public String toString() {
            return "CaptureLease";
        }
    }

    @FunctionalInterface
    interface CloseObservationHook {
        void afterActiveObservation();
    }

    private enum Pending {
        JOIN,
        EVENT,
        ACQUIRE_LEASE,
        SEAL_LEASE,
        CLOSE_LEASE
    }

    private enum LeaseStatus {
        ACTIVE,
        CLOSED,
        SEALED
    }

    enum CallbackPhase {
        FIRST_CHUNK,
        FIRST_MOVE,
        SECOND_CHUNK,
        SECOND_MOVE,
        EVICTION,
        LEAVE,
        RECONNECTED_CHUNK
    }

    private static final class BindingKey {
        private final long worldId;
        private final String nickname;

        private BindingKey(long worldId, String nickname) {
            this.worldId = worldId;
            this.nickname = nickname;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BindingKey value)) return false;
            return worldId == value.worldId && Objects.equals(nickname, value.nickname);
        }

        @Override
        public int hashCode() {
            return Objects.hash(worldId, nickname);
        }
    }

    private static final class BindingState {
        private final BindingKey key;
        private final AuthorityEvidenceJournal.ArmHandle journalHandle;
        private final AuthorityEvidenceJournal.Window window;
        private final AtomicReference<Pending> pending = new AtomicReference<>();
        private volatile int nextEvent = JOIN;
        private volatile long expectedRevision;
        private volatile int expectedGeneration;
        private volatile String expectedIdentitySha256;
        private volatile SessionHandle currentSession;
        private volatile CaptureLease currentLease;
        private volatile boolean sealed;

        private BindingState(BindingKey key, AuthorityEvidenceJournal.ArmHandle journalHandle,
                AuthorityEvidenceJournal.Window window, long expectedRevision) {
            this.key = key;
            this.journalHandle = journalHandle;
            this.window = window;
            this.expectedRevision = expectedRevision;
        }
    }

    private static BindingState copyBinding(BindingState source) {
        BindingState copy = new BindingState(source.key, source.journalHandle,
                source.window, source.expectedRevision);
        copy.nextEvent = source.nextEvent;
        copy.expectedGeneration = source.expectedGeneration;
        copy.expectedIdentitySha256 = source.expectedIdentitySha256;
        copy.currentSession = source.currentSession;
        copy.currentLease = source.currentLease;
        copy.sealed = source.sealed;
        return copy;
    }

    private static final class TransactionState {
        private final Map<BindingKey, BindingState> staged = new HashMap<>();
    }
}
