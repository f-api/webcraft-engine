package com.gameexpert.engine;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceExecutorShutdownRegressionTest {
    @Test
    void timeoutCancelsOnlyUnstartedFutureAndRunningCommitMayFinish() throws Exception {
        exerciseShutdown(false);
    }

    @Test
    void interruptedShutdownAlsoCancelsOnlyUnstartedFuture() throws Exception {
        exerciseShutdown(true);
    }

    private void exerciseShutdown(boolean interruptShutdown) throws Exception {
        PersistenceExecutor executor = new PersistenceExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finishCommit = new CountDownLatch(1);
        AtomicBoolean committed = new AtomicBoolean();
        AtomicBoolean queuedRan = new AtomicBoolean();
        AtomicBoolean shutdownInterruptPreserved = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Future<?> running = executor.submitFuture(() -> {
            started.countDown();
            boolean interrupted = false;
            boolean complete = false;
            while (!complete) {
                try { finishCommit.await(); complete = true; }
                catch (InterruptedException signal) { interrupted = true; }
            }
            committed.set(true);
            if (interrupted) Thread.currentThread().interrupt();
        });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        Future<?> queued = executor.submitFuture(() -> queuedRan.set(true));
        Thread shutdown = new Thread(() -> {
            try {
                if (interruptShutdown) Thread.currentThread().interrupt();
                executor.shutdown();
                shutdownInterruptPreserved.set(Thread.currentThread().isInterrupted());
            } catch (Throwable problem) { failure.set(problem); }
        }, "persistence-shutdown-regression");
        shutdown.setDaemon(true);
        try {
            shutdown.start();
            shutdown.join(7000);
            assertFalse(shutdown.isAlive());
            assertNull(failure.get());
            assertEquals(interruptShutdown, shutdownInterruptPreserved.get());
            assertTrue(queued.isCancelled(), "Removed queued Future must release its waiter");
            assertThrows(CancellationException.class, () -> queued.get(100, TimeUnit.MILLISECONDS));
            assertFalse(queuedRan.get());
            assertFalse(running.isCancelled(), "A running database commit must retain its outcome");
            assertFalse(running.isDone());
            finishCommit.countDown();
            running.get(2, TimeUnit.SECONDS);
            assertTrue(committed.get());
        } finally {
            finishCommit.countDown();
            shutdown.join(2000);
            executor.shutdown();
        }
    }
}
