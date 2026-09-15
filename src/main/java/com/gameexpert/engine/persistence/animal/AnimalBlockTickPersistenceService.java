package com.gameexpert.engine.persistence.animal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.gameexpert.engine.PersistenceExecutor;
import com.gameexpert.engine.AnimalDependencyBlockRules;
import com.gameexpert.block.entity.WorldBlockDiff;
import com.gameexpert.block.repository.WorldBlockDiffRepository;
import com.gameexpert.terrain.Blocks;

import lombok.RequiredArgsConstructor;

/**
 * 동물 블록의 지연된 변화를 월드 시계의 절대 tick으로 저장합니다.
 * DB 실패나 executor 거부 시 pending 항목을 남겨 다음 tick과 종료 flush가 재시도합니다.
 */
@Service
@RequiredArgsConstructor
public class AnimalBlockTickPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(AnimalBlockTickPersistenceService.class);
    private static final long RETRY_DELAY_NANOS = TimeUnit.SECONDS.toNanos(1);
    static final int MAX_PENDING_WRITES = 4_096;
    static final int MAX_BATCH_SIZE = 256;

    private final WorldAnimalBlockTickRepository repository;
    private final PersistenceExecutor persistenceExecutor;
    private final TransactionTemplate transactionTemplate;
    private final WorldBlockDiffRepository blockDiffRepository;
    private final com.gameexpert.falling.service.RuntimeFallingPersistence runtimeFallingPersistence;

    /** 런타임과 DB가 공통으로 사용하는 예약 스냅샷입니다. */
    public record ScheduledTick(int x, int y, int z, Kind kind, long dueTick) { }

    /** 현재 등록된 동물 블록 tick 종류입니다. */
    public enum Kind {
        TURTLE_EGG_HATCH,
        FROGSPAWN_HATCH,
        SNIFFER_EGG_HATCH,
        /**
         * [CONTAINER-MENUS] Redstone block ticks share this durable absolute-tick table (vanilla
         * saves scheduled block ticks with the chunk): the dispenser/dropper {@code tick}, the
         * crafter {@code tick} and the crafter's {@code crafting_ticks_remaining} countdown.
         */
        DISPENSER_TICK,
        CRAFTER_TICK,
        CRAFTER_CRAFTING,
        EYEBLOSSOM_EXPECT_OPEN,
        EYEBLOSSOM_EXPECT_CLOSED
    }

    public WorldStore storeFor(Long worldId) {
        if (worldId == null) throw new IllegalArgumentException("worldId must not be null");
        return new WorldStore(worldId);
    }

    /** 월드 활성화 시 절대 due tick 순서로 런타임을 복구합니다. */
    public List<ScheduledTick> loadWorld(Long worldId) {
        List<ScheduledTick> loaded = transactionTemplate.execute(status ->
                repository.findAllByWorldIdOrderByDueTickAsc(worldId).stream()
                        .map(AnimalBlockTickPersistenceService::toSnapshot)
                        .toList());
        return loaded == null ? List.of() : loaded;
    }

    /** 월드 삭제 transaction에서 호출할 cleanup API입니다. */
    @Transactional
    public void deleteWorld(Long worldId) {
        repository.deleteAllByWorldId(worldId);
    }

    private static ScheduledTick toSnapshot(WorldAnimalBlockTick row) {
        return new ScheduledTick(row.getX(), row.getY(), row.getZ(),
                Kind.valueOf(row.getKind()), row.getDueTick());
    }

    private enum WriteKind { UPSERT, DELETE }

    private record TickKey(int x, int y, int z, Kind kind) { }

    private record PendingWrite(long revision, TickKey key, long dueTick, WriteKind kind) { }

    /** 월드 런타임 하나가 소유하는 bounded, coalescing 저장 뷰입니다. */
    public final class WorldStore {

        private final Long worldId;
        private final Object monitor = new Object();
        private final Map<TickKey, PendingWrite> pending = new LinkedHashMap<>();
        private long nextRevision;
        private long retryNotBeforeNanos;
        private boolean writeInFlight;
        private boolean disposalFlush;

        private WorldStore(Long worldId) {
            this.worldId = worldId;
        }

        public com.gameexpert.falling.service.RuntimeFallingPersistence runtimeFallingPersistence() {
            return runtimeFallingPersistence;
        }

        public List<ScheduledTick> loadAll() {
            return loadWorld(worldId);
        }

        /** Repairs a crash after source-block persistence but before the schedule row was written. */
        public List<ScheduledTick> repairAndLoad(int worldSeed, long nowMcTick) {
            List<ScheduledTick> repaired = transactionTemplate.execute(status -> {
                Map<TickKey, WorldAnimalBlockTick> rows = new LinkedHashMap<>();
                for (WorldAnimalBlockTick row : repository.findAllByWorldIdOrderByDueTickAsc(worldId)) {
                    rows.put(new TickKey(row.getX(), row.getY(), row.getZ(),
                            Kind.valueOf(row.getKind())), row);
                }
                repairMissingSourceRows(rows,
                        blockDiffRepository.findByWorldIdAndBlockType(worldId, (short) Blocks.FROGSPAWN),
                        Kind.FROGSPAWN_HATCH, worldSeed, nowMcTick);
                repairMissingSourceRows(rows,
                        blockDiffRepository.findByWorldIdAndBlockType(worldId, (short) Blocks.SNIFFER_EGG),
                        Kind.SNIFFER_EGG_HATCH, worldSeed, nowMcTick);
                repository.flush();
                return repository.findAllByWorldIdOrderByDueTickAsc(worldId).stream()
                        .map(AnimalBlockTickPersistenceService::toSnapshot).toList();
            });
            return repaired == null ? List.of() : repaired;
        }

        private void repairMissingSourceRows(Map<TickKey, WorldAnimalBlockTick> rows,
                List<WorldBlockDiff> sources, Kind kind, int worldSeed, long nowMcTick) {
            for (WorldBlockDiff source : sources) {
                TickKey key = new TickKey(source.getX(), source.getY(), source.getZ(), kind);
                if (rows.containsKey(key)) continue;
                long due = nowMcTick + (kind == Kind.FROGSPAWN_HATCH
                        ? AnimalDependencyBlockRules.deterministicFrogspawnHatchDelay(
                                worldSeed, source.getX(), source.getY(), source.getZ(), nowMcTick)
                        : AnimalDependencyBlockRules.snifferEggNextDelay(false));
                WorldAnimalBlockTick row = new WorldAnimalBlockTick(worldId,
                        source.getX(), source.getY(), source.getZ(), kind.name(), due);
                repository.save(row);
                rows.put(key, row);
            }
        }

        /** 배치와 재배치는 같은 위치·종류의 due tick을 최신 값으로 합칩합니다. */
        public boolean upsert(int x, int y, int z, Kind kind, long absoluteDueTick) {
            if (kind == null) throw new IllegalArgumentException("kind must not be null");
            TickKey key = new TickKey(x, y, z, kind);
            synchronized (monitor) {
                if (!pending.containsKey(key) && pending.size() >= MAX_PENDING_WRITES) return false;
                pending.put(key, new PendingWrite(
                        nextRevision++, key, absoluteDueTick, WriteKind.UPSERT));
            }
            submitPending(false);
            return true;
        }

        /** 블록 해치 변화가 월드에 commit된 뒤에만 호출합니다. */
        public void deleteAfterCommittedHatch(int x, int y, int z, Kind kind) {
            if (kind == null) throw new IllegalArgumentException("kind must not be null");
            TickKey key = new TickKey(x, y, z, kind);
            synchronized (monitor) {
                pending.put(key, new PendingWrite(nextRevision++, key, 0L, WriteKind.DELETE));
            }
            submitPending(false);
        }

        public void flushPending() {
            submitPending(false);
        }

        public void flushPendingNow() {
            submitPending(true);
        }

        public boolean hasPendingWrites() {
            synchronized (monitor) {
                return writeInFlight || !pending.isEmpty();
            }
        }

        /**
         * executor의 앞선 작업을 기다린 뒤 pending을 동기적으로 모두 내보냅니다.
         * {@code false}면 pending을 유지하므로 종료 절차가 같은 store로 재시도할 수 있습니다.
         */
        public boolean flushForDisposal() {
            synchronized (monitor) {
                disposalFlush = true;
            }
            try {
                persistenceExecutor.submitFuture(this::drainForDisposal).get();
                return true;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            } catch (ExecutionException | RuntimeException failure) {
                log.warn("월드 {} 동물 블록 tick 종료 flush 실패 — pending 유지",
                        worldId, failure);
                return false;
            } finally {
                synchronized (monitor) {
                    disposalFlush = false;
                }
            }
        }

        private void submitPending(boolean force) {
            List<PendingWrite> batch;
            synchronized (monitor) {
                if (disposalFlush || writeInFlight || pending.isEmpty()) return;
                if (!force && System.nanoTime() < retryNotBeforeNanos) return;
                batch = pending.values().stream().limit(MAX_BATCH_SIZE).toList();
                writeInFlight = true;
            }
            boolean accepted = false;
            try {
                accepted = persistenceExecutor.trySubmit(() -> writeAsyncBatch(batch));
            } catch (RuntimeException | Error failure) {
                log.warn("월드 {} 동물 블록 tick 제출 실패 — pending {}건 유지",
                        worldId, batch.size(), failure);
            } finally {
                if (!accepted) {
                    synchronized (monitor) {
                        writeInFlight = false;
                        retryNotBeforeNanos = System.nanoTime() + RETRY_DELAY_NANOS;
                    }
                }
            }
        }

        private void writeAsyncBatch(List<PendingWrite> batch) {
            boolean success = persist(batch);
            boolean hasMore;
            synchronized (monitor) {
                if (success) acknowledge(batch);
                writeInFlight = false;
                retryNotBeforeNanos = success ? 0L : System.nanoTime() + RETRY_DELAY_NANOS;
                hasMore = success && !pending.isEmpty() && !disposalFlush;
            }
            if (hasMore) submitPending(false);
        }

        private void drainForDisposal() {
            while (true) {
                List<PendingWrite> batch;
                synchronized (monitor) {
                    // 이 작업은 단일 persistence executor에서 앞선 async 작업 뒤에 실행됩니다.
                    writeInFlight = false;
                    if (pending.isEmpty()) return;
                    batch = pending.values().stream().limit(MAX_BATCH_SIZE).toList();
                    writeInFlight = true;
                }
                boolean success = persist(batch);
                synchronized (monitor) {
                    if (success) acknowledge(batch);
                    writeInFlight = false;
                    if (!success) {
                        retryNotBeforeNanos = System.nanoTime() + RETRY_DELAY_NANOS;
                        throw new IllegalStateException("animal block tick disposal flush failed");
                    }
                    retryNotBeforeNanos = 0L;
                }
            }
        }

        private boolean persist(List<PendingWrite> batch) {
            try {
                transactionTemplate.executeWithoutResult(status -> persistBatch(worldId, batch));
                return true;
            } catch (RuntimeException | Error failure) {
                log.warn("월드 {} 동물 블록 tick 배치 {}건 실패 — pending 유지",
                        worldId, batch.size(), failure);
                return false;
            }
        }

        private void acknowledge(List<PendingWrite> batch) {
            for (PendingWrite completed : batch) {
                PendingWrite current = pending.get(completed.key());
                if (current != null && current.revision() == completed.revision()) {
                    pending.remove(completed.key());
                }
            }
        }
    }

    private void persistBatch(Long worldId, List<PendingWrite> batch) {
        for (PendingWrite write : batch) {
            TickKey key = write.key();
            if (write.kind() == WriteKind.DELETE) {
                repository.deleteOne(worldId, key.x(), key.y(), key.z(), key.kind().name());
                continue;
            }
            WorldAnimalBlockTick row = repository
                    .findByWorldIdAndXAndYAndZAndKind(
                            worldId, key.x(), key.y(), key.z(), key.kind().name())
                    .orElseGet(() -> new WorldAnimalBlockTick(
                            worldId, key.x(), key.y(), key.z(), key.kind().name(), write.dueTick()));
            row.reschedule(write.dueTick());
            repository.save(row);
        }
        repository.flush();
    }
}
