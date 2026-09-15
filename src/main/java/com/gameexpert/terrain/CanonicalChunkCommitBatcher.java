package com.gameexpert.terrain;

import com.gameexpert.terrain.persistence.CanonicalWorldgenStore;
import com.gameexpert.terrain.persistence.CanonicalWorldgenStore.ChunkCommit;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/** CPU slots never own a transaction while waiting for these bounded durable publications. */
final class CanonicalChunkCommitBatcher {
    static final int MAX_BATCH = 8;
    private final CanonicalWorldgenStore store;
    private final ConcurrentLinkedQueue<Pending> pending = new ConcurrentLinkedQueue<>();
    private final Semaphore capacity = new Semaphore(32);
    private final AtomicBoolean draining = new AtomicBoolean();
    private final ThreadPoolExecutor executor;

    CanonicalChunkCommitBatcher(CanonicalWorldgenStore store, long worldId) {
        this.store = Objects.requireNonNull(store, "canonical worldgen store");
        executor = new ThreadPoolExecutor(0, 1, 1, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), task -> {
                    Thread thread = new Thread(task, "canonical-chunk-settlement-" + worldId);
                    thread.setDaemon(true);
                    thread.setPriority(Thread.NORM_PRIORITY - 1);
                    return thread;
                });
    }

    void commit(ChunkCommit commit) {
        Pending request = new Pending(Objects.requireNonNull(commit, "canonical chunk commit"));
        capacity.acquireUninterruptibly();
        pending.add(request);
        schedule();
        try {
            request.done.join();
        } catch (CompletionException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw failure;
        }
    }

    int queuedRequests() { return pending.size(); }

    private void schedule() {
        if (draining.compareAndSet(false, true)) executor.execute(this::drain);
    }

    private void drain() {
        try {
            while (!pending.isEmpty()) {
                // Coalesce arrivals before, never inside, the store's transaction.
                LockSupport.parkNanos(2_000_000L);
                ArrayList<Pending> batch = new ArrayList<>(MAX_BATCH);
                for (Pending request; batch.size() < MAX_BATCH
                        && (request = pending.poll()) != null;) batch.add(request);
                if (batch.isEmpty()) continue;
                try {
                    store.commitBatch(batch.stream().map(request -> request.commit).toList());
                    for (Pending request : batch) request.done.complete(null);
                } catch (Throwable failure) {
                    for (Pending request : batch) request.done.completeExceptionally(failure);
                } finally {
                    capacity.release(batch.size());
                }
            }
        } finally {
            draining.set(false);
            if (!pending.isEmpty()) schedule();
        }
    }

    private static final class Pending {
        private final ChunkCommit commit;
        private final CompletableFuture<Void> done = new CompletableFuture<>();

        private Pending(ChunkCommit commit) { this.commit = commit; }
    }
}
