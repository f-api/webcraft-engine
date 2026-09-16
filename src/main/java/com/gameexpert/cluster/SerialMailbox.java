package com.gameexpert.cluster;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Bounded per-connection ordering. Overflow is terminal, never silent message eviction. */
final class SerialMailbox implements AutoCloseable {
    private final ArrayBlockingQueue<Runnable> tasks;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread worker;
    SerialMailbox(String name, int capacity, Consumer<Throwable> failed) {
        tasks = new ArrayBlockingQueue<>(capacity);
        worker = Thread.ofVirtual().name(name).start(() -> {
            try {
                while (!closed.get()) tasks.take().run();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Throwable failure) {
                failed.accept(failure);
            } finally { closed.set(true); tasks.clear(); }
        });
    }
    boolean offer(Runnable task) { return !closed.get() && tasks.offer(task); }
    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            tasks.clear();
            if (Thread.currentThread() != worker) worker.interrupt();
        }
    }
}
