package com.gameexpert.engine;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * [제공코드] 모든 DB 쓰기(플레이어 상태 저장·블록 diff 배치)를 직렬화하는 단일 스레드 executor.
 *
 * 틱 스레드는 스냅샷만 캡처해 여기로 넘기고, 실제 JDBC 쓰기는 이 스레드 하나에서만 일어납니다
 * (틱 지연·DB 경합 분리). 스레딩 불변식: <b>DB 쓰기는 이 executor 전용</b>.
 *
 * 비동기 제출({@link #submit}, {@link #trySubmit})은 큐 깊이 상한을 가집니다. DB가 느려져 큐가 상한을 넘으면 스냅샷이
 * 힙에 무한히 쌓이는 대신 제출이 <b>명시적으로 거부되고 로그가 남으며 {@code false}가 반환</b>됩니다.
 * 호출자는 반환값으로 dirty 상태를 되돌릴 수 있습니다. 완료 장벽이 필요한 {@link #submitFuture}
 * (입장 직전 flush·종료 flush)는 상한과 무관하게 항상 큐에 들어갑니다.
 */
@Component
public class PersistenceExecutor {

    private static final Logger log = LoggerFactory.getLogger(PersistenceExecutor.class);

    /** 비동기 제출이 거부되기 시작하는 대기 작업 수. */
    static final int DEFAULT_MAX_QUEUE_DEPTH = 10_000;

    private final int maxQueueDepth;
    private final ThreadPoolExecutor executor;
    private final AtomicBoolean saturated = new AtomicBoolean();
    private final AtomicLong rejectedTasks = new AtomicLong();

    public PersistenceExecutor() {
        this(DEFAULT_MAX_QUEUE_DEPTH);
    }

    PersistenceExecutor(int maxQueueDepth) {
        this.maxQueueDepth = maxQueueDepth;
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable, "persistence-writer");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /**
     * 반환값을 처리할 상태가 없는 최선 비동기 작업에만 사용합니다.
     * dirty나 pending 상태를 비우는 호출자는 반드시 {@link #trySubmit}을 사용해야 합니다.
     */
    @Deprecated(forRemoval = false)
    public void submit(Runnable task) {
        trySubmit(task);
    }

    /**
     * 비동기 쓰기 작업을 제출하고 큐에 들어갔는지 알려줍니다.
     *
     * @return 큐에 들어간 경우 {@code true}. {@code false}면 작업은 실행되지 않으므로 호출자는
     *         dirty 상태·pending 표시를 반드시 되돌려야 합니다.
     */
    public boolean trySubmit(Runnable task) {
        if (executor.isShutdown()) {
            log.warn("영속 executor 종료 후 제출된 작업을 실행하지 않습니다 (누적 거부 {}건)",
                    rejectedTasks.incrementAndGet());
            return false;
        }
        int depth = queueDepth();
        if (depth >= maxQueueDepth) {
            long rejected = rejectedTasks.incrementAndGet();
            if (saturated.compareAndSet(false, true)) {
                log.error("영속 executor 큐 포화 — 대기 {}건이 상한 {}건에 도달해 제출을 거부합니다 (누적 거부 {}건)",
                        depth, maxQueueDepth, rejected);
            }
            return false;
        }
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException | Error exception) {
                    log.error("비동기 영속화 작업 실패", exception);
                    throw exception;
                }
            });
        } catch (RejectedExecutionException exception) {
            log.warn("영속 executor가 작업을 거부했습니다 — 실행되지 않습니다 (누적 거부 {}건)",
                    rejectedTasks.incrementAndGet(), exception);
            return false;
        }
        if (saturated.compareAndSet(true, false)) {
            log.info("영속 executor 큐 회복 — 대기 {}건, 누적 거부 {}건", queueDepth(), rejectedTasks.get());
        }
        return true;
    }

    public Future<?> submitFuture(Runnable task) {
        return executor.submit(task);
    }

    /** 아직 실행되지 않은 대기 작업 수(백프레셔 계측용). */
    public int queueDepth() {
        return executor.getQueue().size();
    }

    /** 실행되지 못하고 거부된 누적 작업 수. */
    public long rejectedTaskCount() {
        return rejectedTasks.get();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                List<Runnable> dropped = executor.shutdownNow();
                cancelUnstartedFutures(dropped);
                if (!dropped.isEmpty()) {
                    log.error("영속 executor 종료 대기 초과 — 실행되지 못한 작업 {}건을 버립니다", dropped.size());
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            List<Runnable> dropped = executor.shutdownNow();
            cancelUnstartedFutures(dropped);
            if (!dropped.isEmpty()) {
                log.error("영속 executor 종료 대기 중단 — 실행되지 못한 작업 {}건을 버립니다", dropped.size());
            }
        }
    }

    /** Removed queue entries never ran; unblock their waiters without cancelling a running commit. */
    private static void cancelUnstartedFutures(List<Runnable> dropped) {
        dropped.stream().filter(task -> task instanceof Future<?>)
                .map(task -> (Future<?>) task)
                .forEach(task -> task.cancel(false));
    }
}
