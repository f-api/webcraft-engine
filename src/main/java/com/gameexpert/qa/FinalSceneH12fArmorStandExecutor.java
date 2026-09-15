package com.gameexpert.qa;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.LongSupplier;

/**
 * Unregistered H12f boundary. It deliberately knows only how to validate and hand off a
 * product-owned complete batch; the product supplies every receipt and semantic fact.
 */
public final class FinalSceneH12fArmorStandExecutor {
    public static final String MODULE_SCHEMA = "game-expert.qa-final-scene-h12f-module/v1";
    public static final Duration REQUEST_DURATION = Duration.ofSeconds(60);
    public static final int MAX_CONCURRENT_WORKERS = 4;

    private static final Set<FailureCode> SEMANTIC_CODES =
            Set.of(FailureCode.ABSENT, FailureCode.STALE, FailureCode.DUPLICATE,
                    FailureCode.WRONG_KIND, FailureCode.WRONG_TARGET, FailureCode.WRONG_ORIGIN);
    private static final Comparator<Failure> FAILURE_ORDER = Comparator
            .comparing(Failure::roleOrdinal, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(Failure::candidateOrdinal,
                    Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparingInt(failure -> failure.code().ordinal());
    private static final Comparator<Candidate> CANDIDATE_ORDER = Comparator
            .comparingInt(Candidate::roleOrdinal)
            .thenComparingInt(Candidate::candidateOrdinal);
    private static final AtomicLong WORKER_SEQUENCE = new AtomicLong();
    private static final Semaphore WORKER_ADMISSION =
            new Semaphore(MAX_CONCURRENT_WORKERS, true);
    private static final Object SETTLEMENT_MONITOR = new Object();
    private static final ReferenceQueue<PreparedBatch> SETTLEMENT_QUEUE = new ReferenceQueue<>();
    private static final Map<SettlementKey, SettlementCycle> SETTLEMENT_REGISTRY = new HashMap<>();
    private static final Generation INITIAL_GENERATION = new Generation();
    private static final String WORKER_THREAD_PREFIX = "h12f-completion-worker-";
    private static final ThreadFactory WORKER_FACTORY = runnable -> {
        Thread worker = new Thread(runnable,
                WORKER_THREAD_PREFIX + WORKER_SEQUENCE.incrementAndGet());
        worker.setDaemon(true);
        return worker;
    };
    private static final Module DISABLED = new Module(null);

    private FinalSceneH12fArmorStandExecutor() { }

    public enum Status {
        ENABLED,
        ABSENT
    }

    public enum FailureCode {
        AUTHENTICATION_FAILURE,
        ABSENT,
        STALE,
        DUPLICATE,
        WRONG_KIND,
        WRONG_TARGET,
        WRONG_ORIGIN
    }

    public enum CleanupStatus {
        SUCCESS,
        REJECTED,
        CLASSIFIED_FAILURE
    }

    public enum OperationStatus {
        SUCCESS,
        REJECTED,
        CLASSIFIED_FAILURE
    }

    /** Requests are admitted only for the fixed H12f observation window. */
    public record Request(Duration duration) {
        public Request {
            Objects.requireNonNull(duration, "request duration");
            if (!REQUEST_DURATION.equals(duration)) {
                throw new IllegalArgumentException("H12f request duration must be exactly 60 seconds");
            }
        }
    }

    /**
     * An authenticated value whose contents never cross this shell boundary. The only receipt
     * issuer is package-private so an external caller cannot manufacture a receipt through the
     * public API or forge an authenticated success.
     */
    public static final class OpaqueReceipt<T> {
        private final T value;

        private OpaqueReceipt(T value) {
            this.value = Objects.requireNonNull(value, "opaque receipt value");
        }

        static <T> OpaqueReceipt<T> issue(T value) {
            return new OpaqueReceipt<>(value);
        }

        @Override
        public String toString() {
            return "OpaqueReceipt";
        }
    }

    /**
     * One generic candidate. Semantic codes are supplied by the product and are intentionally
     * limited to the fixed shell vocabulary.
     */
    public static final class Candidate {
        private final int roleOrdinal;
        private final int candidateOrdinal;
        private final OpaqueReceipt<?> receipt;
        private final List<FailureCode> semanticFailures;

        private Candidate(int roleOrdinal, int candidateOrdinal, OpaqueReceipt<?> receipt,
                Collection<FailureCode> semanticFailures) {
            requireOrdinal(roleOrdinal, "role ordinal");
            requireOrdinal(candidateOrdinal, "candidate ordinal");
            this.roleOrdinal = roleOrdinal;
            this.candidateOrdinal = candidateOrdinal;
            this.receipt = Objects.requireNonNull(receipt, "candidate receipt");
            Objects.requireNonNull(semanticFailures, "candidate semantic failures");
            LinkedHashSet<FailureCode> codes = new LinkedHashSet<>();
            for (FailureCode code : semanticFailures) {
                if (code == null || !SEMANTIC_CODES.contains(code)) {
                    throw new IllegalArgumentException("candidate semantic code is not supported");
                }
                codes.add(code);
            }
            this.semanticFailures = codes.stream()
                    .sorted(Comparator.comparingInt(Enum::ordinal)).toList();
        }

        public static Candidate of(int roleOrdinal, int candidateOrdinal,
                OpaqueReceipt<?> receipt) {
            return new Candidate(roleOrdinal, candidateOrdinal, receipt, List.of());
        }

        public static Candidate of(int roleOrdinal, int candidateOrdinal,
                OpaqueReceipt<?> receipt, FailureCode... semanticFailures) {
            Objects.requireNonNull(semanticFailures, "candidate semantic failures");
            return new Candidate(roleOrdinal, candidateOrdinal, receipt,
                    List.of(semanticFailures));
        }

        public static Candidate of(int roleOrdinal, int candidateOrdinal,
                OpaqueReceipt<?> receipt, Collection<FailureCode> semanticFailures) {
            return new Candidate(roleOrdinal, candidateOrdinal, receipt, semanticFailures);
        }

        public int roleOrdinal() { return roleOrdinal; }
        public int candidateOrdinal() { return candidateOrdinal; }
        public List<FailureCode> semanticFailures() { return semanticFailures; }

        private OpaqueReceipt<?> receipt() { return receipt; }

        @Override
        public String toString() {
            return "Candidate{roleOrdinal=" + roleOrdinal
                    + ", candidateOrdinal=" + candidateOrdinal + "}";
        }
    }

    /** Generic ordinal projection used by evidence; it contains no opaque receipt value. */
    public record Ordinal(int roleOrdinal, int candidateOrdinal) {
        public Ordinal {
            requireOrdinal(roleOrdinal, "role ordinal");
            requireOrdinal(candidateOrdinal, "candidate ordinal");
        }
    }

    public record Failure(Integer roleOrdinal, Integer candidateOrdinal, FailureCode code) {
        public Failure {
            Objects.requireNonNull(code, "failure code");
            if ((roleOrdinal == null) != (candidateOrdinal == null)) {
                throw new IllegalArgumentException("failure ordinals must be paired");
            }
            if (roleOrdinal == null && code != FailureCode.ABSENT) {
                throw new IllegalArgumentException("batch failure must be ABSENT");
            }
            if (roleOrdinal != null) {
                requireOrdinal(roleOrdinal, "role ordinal");
                requireOrdinal(candidateOrdinal, "candidate ordinal");
            }
        }
    }

    /** Deeply frozen evidence projection. Opaque values never appear in it. */
    public static final class Evidence {
        private final String schema;
        private final List<Ordinal> candidates;

        private Evidence(List<Ordinal> candidates) {
            this(MODULE_SCHEMA, candidates);
        }

        private Evidence(String schema, List<Ordinal> candidates) {
            if (!MODULE_SCHEMA.equals(Objects.requireNonNull(schema, "evidence schema"))) {
                throw new IllegalArgumentException("evidence schema is not owned by this module");
            }
            this.schema = schema;
            this.candidates = List.copyOf(Objects.requireNonNull(candidates, "evidence candidates"));
        }

        public String schema() { return schema; }
        public List<Ordinal> candidates() { return candidates; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Evidence that)) return false;
            return schema.equals(that.schema) && candidates.equals(that.candidates);
        }

        @Override
        public int hashCode() {
            return Objects.hash(schema, candidates);
        }

        @Override
        public String toString() {
            return "Evidence{schema='" + schema + "', candidates=" + candidates + "}";
        }
    }

    /** Deeply frozen result; evidence is present only when every gate and cleanup succeeded. */
    public static final class Outcome {
        private final Evidence evidence;
        private final List<Failure> failures;
        private final CleanupStatus cleanup;
        private final OperationStatus operation;

        private Outcome(Evidence evidence, List<Failure> failures,
                CleanupStatus cleanup, OperationStatus operation) {
            this.failures = List.copyOf(Objects.requireNonNull(failures, "outcome failures"));
            this.cleanup = Objects.requireNonNull(cleanup, "outcome cleanup");
            this.operation = Objects.requireNonNull(operation, "outcome operation");
            if (evidence != null && (!this.failures.isEmpty()
                    || cleanup != CleanupStatus.SUCCESS
                    || operation != OperationStatus.SUCCESS)) {
                throw new IllegalArgumentException("failed outcome cannot expose evidence");
            }
            this.evidence = evidence;
        }

        public Evidence evidence() { return evidence; }
        public List<Failure> failures() { return failures; }
        public CleanupStatus cleanup() { return cleanup; }
        public OperationStatus operation() { return operation; }
        public boolean evidenceAccepted() { return evidence != null; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Outcome that)) return false;
            return Objects.equals(evidence, that.evidence)
                    && failures.equals(that.failures)
                    && cleanup == that.cleanup
                    && operation == that.operation;
        }

        @Override
        public int hashCode() {
            return Objects.hash(evidence, failures, cleanup, operation);
        }

        @Override
        public String toString() {
            return "Outcome{evidence=" + evidence + ", failures=" + failures
                    + ", cleanup=" + cleanup + ", operation=" + operation + "}";
        }
    }

    /** Product prepares a cleanup owner before the executor asks that owner to collect. */
    @FunctionalInterface
    public interface ProductOwnedCollectingApi {
        PreparedBatch prepare(Request request);
    }

    /** Compatibility name for the product source; the executor consumes it through preparation. */
    @FunctionalInterface
    public interface ProductOwnedCompleteBatchCollection extends ProductOwnedCollectingApi { }

    /** Product-owned collection result; its lifetime is owned by the prepared batch. */
    public interface CompleteBatch {
        List<Candidate> candidates();
    }

    /**
     * Product-owned lifetime that must remain live through collection and owns its cleanup.
     * Cleanup is invoked by the executor through an identity-global settle-once registry.
     */
    public interface PreparedBatch {
        CompleteBatch collect();
        CleanupStatus dispose();

        default CleanupStatus settle() {
            return dispose();
        }

        default CleanupStatus cleanup() {
            return dispose();
        }
    }

    @FunctionalInterface
    public interface ReceiptAuthenticator {
        boolean authenticate(OpaqueReceipt<?> receipt);
    }

    @FunctionalInterface
    public interface ProductExecutor {
        OperationStatus execute(Request request, List<Candidate> candidates);
    }

    @FunctionalInterface
    public interface EvidenceCallback {
        void onEvidence(Evidence evidence);
    }

    public record Binding(ProductOwnedCollectingApi batchCollection,
            ReceiptAuthenticator receiptAuthenticator, ProductExecutor productExecutor) {
        public Binding {
            Objects.requireNonNull(batchCollection, "product-owned complete batch preparation");
            Objects.requireNonNull(receiptAuthenticator, "receipt authenticator");
            Objects.requireNonNull(productExecutor, "product executor");
        }
    }

    public static Module bind(Binding binding) {
        return binding == null ? DISABLED : new Module(binding);
    }

    public static Module create(Binding binding) {
        return bind(binding);
    }

    public static final class Module {
        private final Executor executor;

        private Module(Binding binding) {
            this.executor = binding == null ? null : new Executor(binding);
        }

        public String schema() { return MODULE_SCHEMA; }
        public Status status() { return executor == null ? Status.ABSENT : Status.ENABLED; }
        public Executor executor() { return executor; }

        /** Explicitly replaces the receipt generation and permits a new one-use batch. */
        public void replaceReceiptGeneration() {
            if (executor != null) executor.replaceReceiptGeneration();
        }

        public Outcome execute(Request request, EvidenceCallback callback) {
            if (executor == null) return absentOutcome();
            return executor.execute(request, callback);
        }

        @Override
        public String toString() {
            return "FinalSceneH12fArmorStandModule{" + status() + "}";
        }
    }

    public static final class Executor {
        private final Binding binding;
        private final Object receiptState = new Object();
        private final Set<Ordinal> consumedCandidates = new HashSet<>();
        private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock(true);
        private Generation receiptGeneration = INITIAL_GENERATION;

        private Executor(Binding binding) {
            this.binding = Objects.requireNonNull(binding, "H12f binding");
        }

        /** Explicit replacement is the only operation that clears one-use candidate state. */
        public void replaceReceiptGeneration() {
            if (lifecycle.getReadHoldCount() > 0 || lifecycle.isWriteLockedByCurrentThread()) {
                throw new IllegalStateException("receipt generation replacement is reentrant");
            }
            lifecycle.writeLock().lock();
            try {
                synchronized (receiptState) {
                    receiptGeneration = new Generation();
                    consumedCandidates.clear();
                }
            } finally {
                lifecycle.writeLock().unlock();
            }
        }

        public Outcome execute(Request request, EvidenceCallback callback) {
            return execute(request, callback, System.nanoTime(), System::nanoTime);
        }

        /** Package-private monotonic start boundary used to test deadline expiry without waiting. */
        Outcome execute(Request request, EvidenceCallback callback, long startNanos) {
            return execute(request, callback, startNanos, System::nanoTime);
        }

        /** Package-private clock seam keeps final-deadline tests deterministic. */
        Outcome execute(Request request, EvidenceCallback callback, long startNanos,
                LongSupplier monotonicClock) {
            Objects.requireNonNull(request, "execution request");
            Objects.requireNonNull(monotonicClock, "monotonic clock");
            ExecutionControl control = new ExecutionControl(startNanos,
                    request.duration().toNanos(), monotonicClock);
            AtomicBoolean restoreInterrupt = new AtomicBoolean();

            AdmissionLease admission = admitWorker(control, restoreInterrupt);
            if (admission == null) {
                if (restoreInterrupt.get()) Thread.currentThread().interrupt();
                return classifiedOutcome();
            }

            ExecutorService workers = null;
            LifecycleTask task = null;
            Future<WorkerResult> future = null;
            WorkerResult workerResult = null;
            boolean cancellationRequested = false;
            boolean handedOff = false;
            try {
                workers = Executors.newSingleThreadExecutor(WORKER_FACTORY);
                task = new LifecycleTask(request, callback, control, admission);
                future = workers.submit(task);
                handedOff = true;
                long remaining = control.remainingNanos();
                if (remaining == 0) {
                    cancellationRequested = true;
                    requestCancellation(control, future, workers, task);
                } else {
                    try {
                        workerResult = future.get(remaining, TimeUnit.NANOSECONDS);
                    } catch (TimeoutException timeout) {
                        cancellationRequested = true;
                        requestCancellation(control, future, workers, task);
                    } catch (InterruptedException interrupted) {
                        restoreInterrupt.set(true);
                        cancellationRequested = true;
                        requestCancellation(control, future, workers, task);
                    } catch (ExecutionException failed) {
                        rethrowFatal(failed.getCause());
                        workerResult = WorkerResult.of(classifiedOutcome());
                    } catch (CancellationException failed) {
                        workerResult = WorkerResult.of(classifiedOutcome());
                    }
                }
            } catch (Throwable failure) {
                rethrowFatal(failure);
                workerResult = WorkerResult.of(classifiedOutcome());
                cancellationRequested = true;
                requestCancellation(control, future, workers, task);
            } finally {
                if (!handedOff) admission.release();
                if (workers != null) {
                    if (cancellationRequested) {
                        requestCancellation(control, future, workers, task);
                    } else {
                        workers.shutdown();
                    }
                }
                if (restoreInterrupt.get()) Thread.currentThread().interrupt();
            }

            Outcome outcome = workerResult == null
                    ? classifiedOutcome() : workerResult.outcome();
            if (cancellationRequested || !control.withinBudget()) {
                outcome = invalidateForCancellationOrDeadline(outcome);
            }
            return outcome;
        }

        private AdmissionLease admitWorker(ExecutionControl control,
                AtomicBoolean restoreInterrupt) {
            long remaining = control.remainingNanos();
            if (remaining == 0) return null;
            try {
                return WORKER_ADMISSION.tryAcquire(remaining, TimeUnit.NANOSECONDS)
                        ? new AdmissionLease() : null;
            } catch (InterruptedException interrupted) {
                control.cancel();
                restoreInterrupt.set(true);
                return null;
            }
        }

        private void requestCancellation(ExecutionControl control,
                Future<?> future, ExecutorService workers, LifecycleTask task) {
            control.cancel();
            if (future != null) future.cancel(true);
            if (workers != null) workers.shutdownNow();
            if (task != null) task.cancelBeforeStart();
        }

        private Outcome runProduct(Request request, EvidenceCallback callback,
                ExecutionControl control) {
            PreparedBatch prepared = null;
            SettlementLease settlementLease = null;
            CleanupStatus cleanup = CleanupStatus.SUCCESS;
            OperationStatus operation = OperationStatus.REJECTED;
            Evidence candidateEvidence = null;
            List<Failure> failures = List.of();
            Generation generation = currentGeneration();
            Generation acceptedGeneration = null;

            try {
                if (!control.withinBudget()) {
                    operation = OperationStatus.CLASSIFIED_FAILURE;
                } else {
                    prepared = Objects.requireNonNull(
                            binding.batchCollection().prepare(request),
                            "product-owned prepared batch");
                    settlementLease = acquireSettlement(prepared, generation);
                    if (!settlementLease.runnable()) {
                        operation = OperationStatus.CLASSIFIED_FAILURE;
                    } else if (!control.withinBudget()) {
                        operation = OperationStatus.CLASSIFIED_FAILURE;
                    } else {
                        CompleteBatch complete = Objects.requireNonNull(
                                prepared.collect(), "product-owned complete batch");
                        if (!control.withinBudget()) {
                            operation = OperationStatus.CLASSIFIED_FAILURE;
                        } else {
                            List<Candidate> ordered = orderedCandidates(complete.candidates());
                            if (ordered.isEmpty()) {
                                failures = List.of(new Failure(null, null, FailureCode.ABSENT));
                            } else {
                                ArrayList<Failure> authenticationFailures = new ArrayList<>();
                                ArrayList<Failure> semanticFailures = new ArrayList<>();
                                Map<Ordinal, Integer> ordinalCounts = countOrdinals(ordered);
                                boolean authenticationComplete = true;
                                for (Candidate candidate : ordered) {
                                    if (!control.withinBudget()) {
                                        authenticationComplete = false;
                                        break;
                                    }
                                    boolean authenticated = authenticated(candidate);
                                    if (!control.withinBudget()) {
                                        authenticationComplete = false;
                                        break;
                                    }
                                    if (!authenticated) {
                                        authenticationFailures.add(failure(candidate,
                                                FailureCode.AUTHENTICATION_FAILURE));
                                    }
                                    LinkedHashSet<FailureCode> candidateFailures =
                                            new LinkedHashSet<>(candidate.semanticFailures());
                                    Ordinal ordinal = ordinalOf(candidate);
                                    if (ordinalCounts.get(ordinal) > 1) {
                                        candidateFailures.add(FailureCode.DUPLICATE);
                                    }
                                    for (FailureCode code : candidateFailures) {
                                        semanticFailures.add(failure(candidate, code));
                                    }
                                }
                                if (!authenticationComplete || !control.withinBudget()) {
                                    operation = OperationStatus.CLASSIFIED_FAILURE;
                                } else {
                                    failures = combineFailures(authenticationFailures, semanticFailures);
                                    if (failures.isEmpty()) {
                                        Reservation reservation = reserve(ordered, generation);
                                        if (!reservation.stale().isEmpty()) {
                                            for (Candidate candidate : ordered) {
                                                if (reservation.stale().contains(ordinalOf(candidate))) {
                                                    semanticFailures.add(failure(candidate,
                                                            FailureCode.STALE));
                                                }
                                            }
                                            semanticFailures.sort(FAILURE_ORDER);
                                            failures = List.copyOf(semanticFailures);
                                        } else if (!control.withinBudget()) {
                                            operation = OperationStatus.CLASSIFIED_FAILURE;
                                        } else {
                                            acceptedGeneration = reservation.generation();
                                            List<Candidate> accepted = List.copyOf(ordered);
                                            try {
                                                operation = Objects.requireNonNull(
                                                        binding.productExecutor().execute(request,
                                                                accepted),
                                                        "product executor status");
                                            } catch (Throwable failure) {
                                                rethrowFatal(failure);
                                                operation = OperationStatus.CLASSIFIED_FAILURE;
                                            }
                                            if (!control.withinBudget()) {
                                                operation = OperationStatus.CLASSIFIED_FAILURE;
                                            } else if (operation == OperationStatus.SUCCESS
                                                    && publicationFence(acceptedGeneration, control)) {
                                                candidateEvidence = evidenceFor(accepted);
                                            } else if (operation == OperationStatus.SUCCESS) {
                                                operation = OperationStatus.CLASSIFIED_FAILURE;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Throwable failure) {
                rethrowFatal(failure);
                operation = OperationStatus.CLASSIFIED_FAILURE;
                candidateEvidence = null;
            } finally {
                if (settlementLease != null) {
                    cleanup = settlementLease.release(prepared, control).status();
                }
            }

            Evidence evidence = null;
            if (candidateEvidence != null) {
                if (cleanup != CleanupStatus.SUCCESS
                        || !publicationFence(acceptedGeneration, control)) {
                    operation = OperationStatus.CLASSIFIED_FAILURE;
                } else {
                    boolean callbackComplete = true;
                    if (callback != null) {
                        try {
                            callback.onEvidence(candidateEvidence);
                        } catch (Throwable failure) {
                            rethrowFatal(failure);
                            callbackComplete = false;
                            operation = OperationStatus.CLASSIFIED_FAILURE;
                        }
                    }
                    if (!callbackComplete
                            || !publicationFence(acceptedGeneration, control)
                            || operation != OperationStatus.SUCCESS) {
                        operation = OperationStatus.CLASSIFIED_FAILURE;
                    } else {
                        evidence = candidateEvidence;
                    }
                }
            }
            return new Outcome(evidence, failures, cleanup, operation);
        }

        private static SettlementLease acquireSettlement(PreparedBatch prepared,
                Generation generation) {
            drainSettlementQueue();
            synchronized (SETTLEMENT_MONITOR) {
                SettlementKey lookup = new SettlementKey(prepared, generation, null);
                SettlementCycle cycle = SETTLEMENT_REGISTRY.get(lookup);
                if (cycle == null) {
                    cycle = new SettlementCycle();
                    SETTLEMENT_REGISTRY.put(
                            new SettlementKey(prepared, generation, SETTLEMENT_QUEUE), cycle);
                }
                synchronized (cycle) {
                    if (cycle.disposing) {
                        return SettlementLease.blocked(cycle,
                                cycle.disposerId == Thread.currentThread().getId());
                    }
                    cycle.activeLeases++;
                    return SettlementLease.active(cycle);
                }
            }
        }

        private static void drainSettlementQueue() {
            synchronized (SETTLEMENT_MONITOR) {
                SettlementKey reference;
                while ((reference = (SettlementKey) SETTLEMENT_QUEUE.poll()) != null) {
                    SETTLEMENT_REGISTRY.remove(reference);
                }
            }
        }

        private static Settlement settleOutsideMonitors(PreparedBatch prepared,
                SettlementCycle cycle) {
            CleanupStatus status = CleanupStatus.CLASSIFIED_FAILURE;
            Throwable fatal = null;
            try {
                status = Objects.requireNonNull(prepared.dispose(), "cleanup status");
            } catch (Throwable failure) {
                if (isFatal(failure)) fatal = failure;
            }

            Settlement terminal = new Settlement(status);
            synchronized (cycle) {
                if (cycle.terminal == null) cycle.terminal = terminal;
                cycle.disposing = false;
                cycle.disposerId = 0;
                cycle.notifyAll();
                terminal = cycle.terminal;
            }
            if (fatal != null) rethrowFatal(fatal);
            return terminal;
        }

        private static Settlement awaitSettlement(SettlementCycle cycle,
                ExecutionControl control) {
            synchronized (cycle) {
                while (cycle.terminal == null) {
                    long remaining = control.remainingNanos();
                    if (remaining == 0) {
                        return new Settlement(CleanupStatus.CLASSIFIED_FAILURE);
                    }
                    try {
                        long millis = remaining / 1_000_000L;
                        int nanos = (int) (remaining % 1_000_000L);
                        cycle.wait(millis, nanos);
                    } catch (InterruptedException interrupted) {
                        control.cancel();
                        return new Settlement(CleanupStatus.CLASSIFIED_FAILURE);
                    }
                }
                return cycle.terminal;
            }
        }

        private static final class SettlementLease {
            private enum Mode { ACTIVE, BLOCKED, REENTRANT }

            private final SettlementCycle cycle;
            private final Mode mode;
            private final AtomicBoolean released = new AtomicBoolean();
            private volatile Settlement releaseResult;

            private SettlementLease(SettlementCycle cycle, Mode mode) {
                this.cycle = cycle;
                this.mode = mode;
            }

            private static SettlementLease active(SettlementCycle cycle) {
                return new SettlementLease(cycle, Mode.ACTIVE);
            }

            private static SettlementLease blocked(SettlementCycle cycle,
                    boolean sameOwnerReentry) {
                return new SettlementLease(cycle,
                        sameOwnerReentry ? Mode.REENTRANT : Mode.BLOCKED);
            }

            private boolean runnable() {
                return mode == Mode.ACTIVE;
            }

            private Settlement release(PreparedBatch prepared, ExecutionControl control) {
                if (mode != Mode.ACTIVE) {
                    return new Settlement(CleanupStatus.CLASSIFIED_FAILURE);
                }
                if (!released.compareAndSet(false, true)) {
                    Settlement result = releaseResult;
                    return result == null
                            ? new Settlement(CleanupStatus.CLASSIFIED_FAILURE) : result;
                }

                boolean dispose = false;
                Settlement terminal = null;
                synchronized (cycle) {
                    if (cycle.terminal != null) {
                        cycle.activeLeases--;
                        terminal = cycle.terminal;
                    } else {
                        cycle.activeLeases--;
                        if (cycle.activeLeases == 0 && !cycle.disposing) {
                            cycle.disposing = true;
                            cycle.disposerId = Thread.currentThread().getId();
                            dispose = true;
                        }
                    }
                }

                if (dispose) {
                    terminal = settleOutsideMonitors(prepared, cycle);
                } else if (terminal == null) {
                    terminal = awaitSettlement(cycle, control);
                }
                releaseResult = terminal;
                return terminal;
            }
        }

        private Reservation reserve(List<Candidate> candidates, Generation generation) {
            synchronized (receiptState) {
                LinkedHashSet<Ordinal> stale = new LinkedHashSet<>();
                for (Candidate candidate : candidates) {
                    Ordinal ordinal = ordinalOf(candidate);
                    if (consumedCandidates.contains(ordinal)) stale.add(ordinal);
                }
                if (stale.isEmpty()) {
                    for (Candidate candidate : candidates) {
                        consumedCandidates.add(ordinalOf(candidate));
                    }
                }
                return new Reservation(generation, Set.copyOf(stale));
            }
        }

        private Generation currentGeneration() {
            synchronized (receiptState) {
                return receiptGeneration;
            }
        }

        private boolean publicationFence(Generation generation, ExecutionControl control) {
            if (!control.withinBudget()) return false;
            synchronized (receiptState) {
                return !control.cancelled() && generation != null
                        && receiptGeneration == generation && control.withinBudget();
            }
        }

        private boolean authenticated(Candidate candidate) {
            try {
                return binding.receiptAuthenticator().authenticate(candidate.receipt());
            } catch (Throwable rejected) {
                rethrowFatal(rejected);
                return false;
            }
        }

        private static Evidence evidenceFor(List<Candidate> candidates) {
            return new Evidence(candidates.stream()
                    .map(candidate -> new Ordinal(candidate.roleOrdinal(),
                            candidate.candidateOrdinal()))
                    .toList());
        }

        private static Failure failure(Candidate candidate, FailureCode code) {
            return new Failure(candidate.roleOrdinal(), candidate.candidateOrdinal(), code);
        }

        private static Ordinal ordinalOf(Candidate candidate) {
            return new Ordinal(candidate.roleOrdinal(), candidate.candidateOrdinal());
        }

        private static List<Failure> combineFailures(List<Failure> authenticationFailures,
                List<Failure> semanticFailures) {
            authenticationFailures.sort(FAILURE_ORDER);
            semanticFailures.sort(FAILURE_ORDER);
            ArrayList<Failure> combined = new ArrayList<>(authenticationFailures.size()
                    + semanticFailures.size());
            combined.addAll(authenticationFailures);
            combined.addAll(semanticFailures);
            return List.copyOf(combined);
        }

        private static List<Candidate> orderedCandidates(List<Candidate> candidates) {
            ArrayList<Candidate> ordered = new ArrayList<>(
                    List.copyOf(Objects.requireNonNull(candidates, "complete batch candidates")));
            for (Candidate candidate : ordered) {
                Objects.requireNonNull(candidate, "complete batch candidate");
            }
            ordered.sort(CANDIDATE_ORDER);
            return List.copyOf(ordered);
        }

        private static Map<Ordinal, Integer> countOrdinals(List<Candidate> candidates) {
            HashMap<Ordinal, Integer> counts = new HashMap<>();
            for (Candidate candidate : candidates) {
                counts.merge(ordinalOf(candidate), 1, Integer::sum);
            }
            return counts;
        }

        private static Outcome invalidateForCancellationOrDeadline(Outcome outcome) {
            if (outcome.evidence() == null
                    && outcome.operation() == OperationStatus.CLASSIFIED_FAILURE) {
                return outcome;
            }
            return new Outcome(null, outcome.failures(), outcome.cleanup(),
                    OperationStatus.CLASSIFIED_FAILURE);
        }

        private final class LifecycleTask implements Callable<WorkerResult> {
            private final Request request;
            private final EvidenceCallback callback;
            private final ExecutionControl control;
            private final AdmissionLease admission;
            private final Object startMonitor = new Object();
            private boolean started;
            private boolean cancelledBeforeStart;

            private LifecycleTask(Request request, EvidenceCallback callback,
                    ExecutionControl control, AdmissionLease admission) {
                this.request = request;
                this.callback = callback;
                this.control = control;
                this.admission = admission;
            }

            @Override
            public WorkerResult call() {
                synchronized (startMonitor) {
                    if (cancelledBeforeStart) {
                        return WorkerResult.of(classifiedOutcome());
                    }
                    started = true;
                }
                try {
                    lifecycle.readLock().lockInterruptibly();
                    try {
                        return WorkerResult.of(runProduct(request, callback, control));
                    } finally {
                        lifecycle.readLock().unlock();
                    }
                } catch (InterruptedException interrupted) {
                    control.cancel();
                    return WorkerResult.of(classifiedOutcome());
                } finally {
                    admission.release();
                }
            }

            private void cancelBeforeStart() {
                boolean releaseNow = false;
                synchronized (startMonitor) {
                    if (!started && !cancelledBeforeStart) {
                        cancelledBeforeStart = true;
                        releaseNow = true;
                    }
                }
                if (releaseNow) admission.release();
            }
        }
    }

    private static final class ExecutionControl {
        private final long startNanos;
        private final long budgetNanos;
        private final LongSupplier monotonicClock;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private ExecutionControl(long startNanos, long budgetNanos,
                LongSupplier monotonicClock) {
            this.startNanos = startNanos;
            this.budgetNanos = budgetNanos;
            this.monotonicClock = monotonicClock;
        }

        private void cancel() {
            cancelled.set(true);
        }

        private boolean cancelled() {
            return cancelled.get();
        }

        private long remainingNanos() {
            if (cancelled.get()) return 0;
            long elapsed = monotonicClock.getAsLong() - startNanos;
            if (elapsed < 0 || elapsed >= budgetNanos) return 0;
            return budgetNanos - elapsed;
        }

        private boolean withinBudget() {
            return remainingNanos() > 0;
        }
    }

    private static final class AdmissionLease {
        private final AtomicBoolean released = new AtomicBoolean();

        private void release() {
            if (released.compareAndSet(false, true)) WORKER_ADMISSION.release();
        }
    }

    /** Identity-only epoch; it deliberately carries no owner, receipt, or failure value. */
    private static final class Generation { }

    private static final class SettlementCycle {
        private int activeLeases;
        private boolean disposing;
        private long disposerId;
        private Settlement terminal;
    }

    /** Weak identity key: its hash survives collection and its referent is never retained. */
    private static final class SettlementKey extends WeakReference<PreparedBatch> {
        private final Generation generation;
        private final int hash;

        private SettlementKey(PreparedBatch owner, Generation generation,
                ReferenceQueue<PreparedBatch> queue) {
            super(owner, queue);
            this.generation = Objects.requireNonNull(generation, "settlement generation");
            this.hash = 31 * System.identityHashCode(owner)
                    + System.identityHashCode(generation);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SettlementKey that)) return false;
            PreparedBatch owner = get();
            PreparedBatch otherOwner = that.get();
            return owner != null && owner == otherOwner && generation == that.generation;
        }
    }

    private record WorkerResult(Outcome outcome) {
        private static WorkerResult of(Outcome outcome) {
            return new WorkerResult(outcome);
        }
    }

    private record Reservation(Generation generation, Set<Ordinal> stale) { }

    private record Settlement(CleanupStatus status) { }

    private static boolean isFatal(Throwable failure) {
        return failure instanceof VirtualMachineError
                || failure instanceof ThreadDeath
                || failure instanceof LinkageError;
    }

    private static void rethrowFatal(Throwable failure) {
        if (failure instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
        if (failure instanceof ThreadDeath threadDeath) {
            throw threadDeath;
        }
        if (failure instanceof LinkageError linkageError) {
            throw linkageError;
        }
    }

    private static Outcome classifiedOutcome() {
        return new Outcome(null, List.of(), CleanupStatus.SUCCESS,
                OperationStatus.CLASSIFIED_FAILURE);
    }

    private static Outcome absentOutcome() {
        return new Outcome(null, List.of(new Failure(null, null, FailureCode.ABSENT)),
                CleanupStatus.SUCCESS, OperationStatus.REJECTED);
    }

    private static void requireOrdinal(int value, String label) {
        if (value < 0) throw new IllegalArgumentException(label + " must be non-negative");
    }
}
