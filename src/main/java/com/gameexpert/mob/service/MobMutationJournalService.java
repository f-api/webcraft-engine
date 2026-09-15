package com.gameexpert.mob.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.gameexpert.engine.PersistenceExecutor;
import com.gameexpert.engine.mob.MobMutationJournal;
import com.gameexpert.mob.entity.WorldMobMutation;
import com.gameexpert.mob.repository.WorldMobMutationRepository;

import lombok.RequiredArgsConstructor;

/**
 * 몹 변형 저널의 MySQL 저장소입니다. 월드별 최신 상태를 합치고, 제한된 크기의 한 transaction으로
 * 내보냅니다. executor 제출 거부와 DB 실패는 pending 상태를 지우지 않아 다음 tick/dispose가 재시도합니다.
 */
@Service
@RequiredArgsConstructor
public class MobMutationJournalService {

    private static final Logger log = LoggerFactory.getLogger(MobMutationJournalService.class);
    static final int MAX_BATCH_SIZE = 256;
    static final int MAX_PENDING_WRITES = 4_096;
    private static final long RETRY_DELAY_NANOS = TimeUnit.SECONDS.toNanos(1);

    private final WorldMobMutationRepository repository;
    private final PersistenceExecutor persistenceExecutor;
    private final TransactionTemplate transactionTemplate;

    /** 월드 런타임 하나가 쓰는 저널 저장소 뷰를 만듭니다. */
    public MobMutationJournal.Store storeFor(Long worldId) {
        return new WorldStore(worldId);
    }

    public List<MobMutationJournal.Entry> loadWorld(Long worldId) {
        List<MobMutationJournal.Entry> loaded = transactionTemplate.execute(status ->
                repository.findAllByWorldIdOrderBySequenceAsc(worldId).stream()
                        .map(WorldMobMutation::toEntry)
                        .toList());
        return loaded == null ? List.of() : loaded;
    }

    @Transactional
    public void deleteWorld(Long worldId) {
        repository.deleteAllByWorldId(worldId);
    }

    /** 한 entry key에 대해 DB가 도달해야 할 마지막 상태입니다. 삭제가 언제나 우선합니다. */
    private enum WriteKind {
        APPEND,
        APPLIED,
        SUPERSEDED,
        REMOVE
    }

    private record PendingWrite(
            long revision,
            MobMutationJournal.Entry entry,
            WriteKind kind,
            Runnable onDurable) { }

    @RequiredArgsConstructor
    private final class WorldStore implements MobMutationJournal.Store {

        private final Long worldId;
        private final Object pendingMonitor = new Object();
        private final Map<String, PendingWrite> pendingWrites = new LinkedHashMap<>();
        private long nextRevision;
        private long retryNotBeforeNanos;
        private boolean writeInFlight;

        @Override
        public boolean append(MobMutationJournal.Entry entry, Runnable onDurable) {
            synchronized (pendingMonitor) {
                if (!pendingWrites.containsKey(entry.key())
                        && pendingWrites.size() >= MAX_PENDING_WRITES) {
                    return false;
                }
                pendingWrites.put(entry.key(), new PendingWrite(
                        nextRevision++, entry, WriteKind.APPEND, onDurable));
            }
            submitPending();
            // executor 포화여도 제한·병합된 local 상태가 쓰기를 받았으므로 적용 전 durable callback을
            // 기다리며 다음 tick에 재제출한다. 기록을 버린 경우에만 false여야 한다.
            return true;
        }

        @Override
        public void markApplied(MobMutationJournal.Entry entry) {
            queueTerminal(entry, WriteKind.APPLIED);
        }

        @Override
        public void markSuperseded(MobMutationJournal.Entry entry) {
            queueTerminal(entry, WriteKind.SUPERSEDED);
        }

        @Override
        public void remove(MobMutationJournal.Entry entry) {
            queueTerminal(entry, WriteKind.REMOVE);
        }

        private void queueTerminal(MobMutationJournal.Entry entry, WriteKind requested) {
            synchronized (pendingMonitor) {
                PendingWrite current = pendingWrites.get(entry.key());
                WriteKind merged = merge(current == null ? null : current.kind(), requested);
                if (current != null && merged == current.kind()) return;
                pendingWrites.put(entry.key(), new PendingWrite(
                        nextRevision++, entry, merged, null));
            }
            submitPending();
        }

        private WriteKind merge(WriteKind current, WriteKind requested) {
            if (current == WriteKind.REMOVE || requested == WriteKind.REMOVE) {
                return WriteKind.REMOVE;
            }
            if (current == WriteKind.SUPERSEDED || requested == WriteKind.SUPERSEDED) {
                return WriteKind.SUPERSEDED;
            }
            return requested;
        }

        @Override
        public List<MobMutationJournal.Entry> loadAll() {
            return loadWorld(worldId);
        }

        @Override
        public void flushPending() {
            submitPending(false);
        }

        @Override
        public void flushPendingNow() {
            submitPending(true);
        }

        @Override
        public boolean hasPendingWrites() {
            synchronized (pendingMonitor) {
                return writeInFlight || !pendingWrites.isEmpty();
            }
        }

        private void submitPending() {
            submitPending(false);
        }

        private void submitPending(boolean force) {
            List<PendingWrite> batch;
            synchronized (pendingMonitor) {
                if (writeInFlight || pendingWrites.isEmpty()) return;
                if (!force && System.nanoTime() < retryNotBeforeNanos) return;
                batch = pendingWrites.values().stream().limit(MAX_BATCH_SIZE).toList();
                writeInFlight = true;
            }
            boolean accepted = false;
            try {
                accepted = persistenceExecutor.trySubmit(() -> writeBatch(batch));
            } catch (RuntimeException | Error failure) {
                log.warn("월드 {} 몹 변형 저널 배치 제출 실패 — pending {}건 유지",
                        worldId, batch.size(), failure);
            } finally {
                if (!accepted) {
                    synchronized (pendingMonitor) {
                        writeInFlight = false;
                        retryNotBeforeNanos = System.nanoTime() + RETRY_DELAY_NANOS;
                    }
                }
            }
        }

        private void writeBatch(List<PendingWrite> batch) {
            boolean success = false;
            try {
                transactionTemplate.executeWithoutResult(status -> persistBatch(worldId, batch));
                success = true;
            } catch (RuntimeException | Error failure) {
                log.warn("월드 {} 몹 변형 저널 배치 {}건 실패 — pending 유지",
                        worldId, batch.size(), failure);
            }

            List<Runnable> durableCallbacks = new ArrayList<>();
            boolean hasMore;
            synchronized (pendingMonitor) {
                if (success) {
                    for (PendingWrite completed : batch) {
                        PendingWrite current = pendingWrites.get(completed.entry().key());
                        if (current == null || current.revision() != completed.revision()) continue;
                        pendingWrites.remove(completed.entry().key());
                        if (completed.kind() == WriteKind.APPEND
                                && completed.onDurable() != null) {
                            durableCallbacks.add(completed.onDurable());
                        }
                    }
                }
                writeInFlight = false;
                retryNotBeforeNanos = success
                        ? 0L : System.nanoTime() + RETRY_DELAY_NANOS;
                hasMore = success && !pendingWrites.isEmpty();
            }
            for (Runnable callback : durableCallbacks) callback.run();
            // 한 작업만 in-flight로 유지하되, 성공한 뒤 남은 bounded batch는 즉시 이어서 보낸다.
            if (hasMore) submitPending();
        }
    }

    /** 같은 종류를 한 repository 쿼리로 처리해 transaction/round-trip 증폭을 제한합니다. */
    private void persistBatch(Long worldId, List<PendingWrite> batch) {
        List<MobMutationJournal.Entry> appends = new ArrayList<>();
        List<String> applied = new ArrayList<>();
        List<String> superseded = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        for (PendingWrite write : batch) {
            switch (write.kind()) {
                case APPEND -> appends.add(write.entry());
                case APPLIED -> applied.add(write.entry().key());
                case SUPERSEDED -> superseded.add(write.entry().key());
                case REMOVE -> removed.add(write.entry().key());
            }
        }
        if (!appends.isEmpty()) appendMissing(worldId, appends);
        if (!applied.isEmpty()) repository.markAppliedAll(worldId, applied);
        if (!superseded.isEmpty()) repository.markSupersededAll(worldId, superseded);
        if (!removed.isEmpty()) repository.deleteAllByWorldIdAndEntryKeyIn(worldId, removed);
    }

    private void appendMissing(Long worldId, List<MobMutationJournal.Entry> entries) {
        List<String> keys = entries.stream().map(MobMutationJournal.Entry::key).toList();
        Set<String> existing = new HashSet<>();
        for (WorldMobMutation row : repository.findAllByWorldIdAndEntryKeyIn(worldId, keys)) {
            existing.add(row.getEntryKey());
        }
        List<WorldMobMutation> missing = entries.stream()
                .filter(entry -> !existing.contains(entry.key()))
                .map(entry -> new WorldMobMutation(worldId, entry))
                .toList();
        if (!missing.isEmpty()) repository.saveAll(missing);
    }
}
