package com.gameexpert.cluster;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Bounded per-connection ordering. Overflow is terminal, never silent message eviction. */
final class SerialMailbox implements AutoCloseable {

    /**
     * 닫을 때 진행 중인 작업에 주는 시간. 이 안에 끝나면 끊지 않는다.
     *
     * 접속 처리는 이 스레드에서 데이터베이스 트랜잭션을 돌린다. 스레드를 바로 인터럽트하면
     * JDBC 소켓이 그 자리에서 닫혀("Closed by interrupt") 커넥션과 트랜잭션이 함께 망가지고,
     * 다른 접속까지 "연결이 끊겼다"는 실패로 번진다.
     */
    static final Duration CLOSE_GRACE = Duration.ofSeconds(20);

    private static final Runnable WAKE = () -> { };

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
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        tasks.clear();
        if (Thread.currentThread() == worker) {
            return;
        }
        // 대기 중이면 깨워서 스스로 끝내게 하고, 일하는 중이면 끝날 때까지 기다린다.
        tasks.offer(WAKE);
        Thread.ofVirtual().name(worker.getName() + "-stop").start(() -> {
            try {
                if (!worker.join(CLOSE_GRACE)) {
                    worker.interrupt();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                worker.interrupt();
            }
        });
    }

    /** 시험용: 이 편지함의 작업 스레드가 아직 살아 있는가. */
    boolean running() { return worker.isAlive(); }
}
