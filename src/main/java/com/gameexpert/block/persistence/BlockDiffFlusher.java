package com.gameexpert.block.persistence;


import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.gameexpert.engine.BlockPos;
import com.gameexpert.engine.PersistenceExecutor;

import jakarta.annotation.PreDestroy;

import lombok.RequiredArgsConstructor;

/**
 * [제공코드] {@link BlockDiffBuffer}를 주기적으로 DB에 배치 upsert합니다.
 *
 * 주기({@code game.persistence.block-flush-ms}, 기본 5초)마다 + 월드 비활성화 시 + 종료 시 원자 스왑 후
 * {@code INSERT ... ON DUPLICATE KEY UPDATE} 배치로 씁니다. Connector/J는 이를 multi-values 한 문장으로
 * 재작성하고, MySQL은 실제 값이 같은 행을 affected row로 세거나 timestamp만 다시 쓰지 않습니다.
 * 실패하면 버퍼로 되돌려 다음 주기에 재시도합니다.
 * 실제 쓰기는 항상 {@link PersistenceExecutor}(단일 스레드)에서 수행합니다. 신규 플레이어 입장 시에는
 * welcome 직전에 해당 월드를 <b>즉시(블로킹) flush</b>해 새 런타임의 DB 복원 누락을 막습니다.
 */
@Component
@DependsOn({"entityManagerFactory", "persistenceExecutor"})
@RequiredArgsConstructor
public class BlockDiffFlusher {

    private static final Logger log = LoggerFactory.getLogger(BlockDiffFlusher.class);

    private static final String UPSERT_SQL = """
            INSERT INTO world_block_diffs (world_id, x, y, z, chunk_x, chunk_z, block_type, block_state,
                mob_mutation_key, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            AS new
            ON DUPLICATE KEY UPDATE
                updated_at = IF(
                    world_block_diffs.block_type <> new.block_type
                        OR world_block_diffs.block_state <> new.block_state
                        OR NOT (world_block_diffs.mob_mutation_key <=> new.mob_mutation_key),
                    new.updated_at, world_block_diffs.updated_at),
                block_type = new.block_type, block_state = new.block_state,
                mob_mutation_key = new.mob_mutation_key
            """;

    /**
     * H2(MODE=MySQL) 테스트 데이터베이스는 MySQL 8.0.19+ row-alias(`AS new`) 문법을 지원하지 않아
     * 의미가 동일한 VALUES() 변형을 사용합니다. H2는 MySQL 전용 {@code IF()}/{@code <=>} 도
     * ON DUPLICATE KEY UPDATE 절에서 파싱하지 못하므로 CASE WHEN + IS DISTINCT FROM으로 씁니다.
     * 프로덕션 MySQL은 항상 row-alias 문장을 씁니다.
     */
    private static final String UPSERT_SQL_H2 = """
            INSERT INTO world_block_diffs (world_id, x, y, z, chunk_x, chunk_z, block_type, block_state,
                mob_mutation_key, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                updated_at = CASE WHEN world_block_diffs.block_type <> VALUES(block_type)
                        OR world_block_diffs.block_state <> VALUES(block_state)
                        OR world_block_diffs.mob_mutation_key
                            IS DISTINCT FROM VALUES(mob_mutation_key)
                    THEN VALUES(updated_at) ELSE world_block_diffs.updated_at END,
                block_type = VALUES(block_type), block_state = VALUES(block_state),
                mob_mutation_key = VALUES(mob_mutation_key)
            """;

    private volatile String upsertSql;

    private String upsertSql() {
        String sql = upsertSql;
        if (sql == null) {
            Boolean h2;
            try {
                h2 = jdbcTemplate.execute((java.sql.Connection connection) ->
                        "H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName()));
            } catch (RuntimeException probeFailure) {
                // 테스트 대역처럼 실제 커넥션이 없는 템플릿은 프로덕션 MySQL 문장을 그대로 쓴다.
                h2 = Boolean.FALSE;
            }
            sql = Boolean.TRUE.equals(h2) ? UPSERT_SQL_H2 : UPSERT_SQL;
            upsertSql = sql;
        }
        return sql;
    }

    private final BlockDiffBuffer buffer;
    private final JdbcTemplate jdbcTemplate;
    private final PersistenceExecutor persistenceExecutor;
    private final Set<Long> pendingWorlds = ConcurrentHashMap.newKeySet();
    /**
     * 활성 WorldRuntime이 블록 diff와 PrimedTnt delta를 한 트랜잭션으로 저장하는 월드입니다.
     * 이 월드는 독립 스케줄 flusher가 drain하면 두 durable 사실의 체크포인트 경계가 갈라집니다.
     */
    private final Set<Long> combinedCheckpointWorlds = ConcurrentHashMap.newKeySet();
    /** 주기 flush: 버퍼가 있는 모든 월드를 persistence executor에서 쓰도록 제출합니다. */
    @Scheduled(fixedRateString = "${game.persistence.block-flush-ms:5000}")
    public void flushDue() {
        for (Long worldId : buffer.worldIds()) {
            submitWorldOnce(worldId);
        }
    }

    /** 월드 비활성화 시 남은 버퍼를 비동기로 내보냅니다. */
    public void flushWorldAsync(Long worldId) {
        submitWorldOnce(worldId);
    }

    private void submitWorldOnce(Long worldId) {
        if (combinedCheckpointWorlds.contains(worldId)) return;
        if (!pendingWorlds.add(worldId)) return;
        boolean accepted = false;
        try {
            // 제출이 거부되면 작업이 실행되지 않아 pendingWorlds가 영구히 남고 이후 flush가 모두
            // 건너뛰므로, 실행되지 못한 경우에는 반드시 pending 표시를 되돌립니다.
            accepted = persistenceExecutor.trySubmit(() -> {
                try {
                    writeWorld(worldId);
                } finally {
                    pendingWorlds.remove(worldId);
                }
            });
        } finally {
            if (!accepted) {
                pendingWorlds.remove(worldId);
            }
        }
    }

    /**
     * 신규 입장 welcome 직전 호출: persistence executor에서 쓰고 완료를 기다립니다.
     * pending 비동기 작업이 있어도 별도 제출합니다. 큐 중복 제거보다 welcome이 최신 diff를 읽는
     * 완료 장벽이 우선이며, 앞 작업이 먼저 drain했다면 이 작업은 빈 버퍼를 확인하고 끝납니다.
     */
    public void flushWorldBlocking(Long worldId) {
        try {
            persistenceExecutor.submitFuture(() -> writeWorld(worldId)).get();
        } catch (ExecutionException exception) {
            throw new IllegalStateException("월드 " + worldId + " 즉시 flush 실패", exception.getCause());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("월드 " + worldId + " 즉시 flush 대기 중단", exception);
        }
    }

    /** 활성 런타임이 블록+TNT 결합 체크포인트의 단독 drain 권한을 획득합니다. */
    public void claimCombinedCheckpoint(Long worldId) {
        combinedCheckpointWorlds.add(worldId);
    }

    /** 최종 결합 체크포인트가 끝난 뒤 독립 flusher에 월드를 돌려줍니다. */
    public void releaseCombinedCheckpoint(Long worldId) {
        combinedCheckpointWorlds.remove(worldId);
    }

    boolean isCombinedCheckpointClaimed(Long worldId) {
        return combinedCheckpointWorlds.contains(worldId);
    }

    // persistence executor 스레드에서만 호출됩니다.
    private void writeWorld(Long worldId) {
        Map<BlockPos, BlockDiffBuffer.Change> drained = buffer.drain(worldId);
        if (drained.isEmpty()) {
            return;
        }
        try {
            writeDetached(worldId, drained);
        } catch (RuntimeException exception) {
            buffer.restore(worldId, drained); // 다음 주기에 재시도
            log.warn("월드 {} 블록 diff 배치 {}건 실패 — 버퍼로 복원", worldId, drained.size(), exception);
            throw exception;
        }
    }

    /**
     * 이미 owner thread에서 떼어낸 배치를 현재 Spring 트랜잭션의 JDBC 연결로 씁니다.
     * 복원 책임은 호출자에게 있으며 PrimedTnt 결합 체크포인트가 이 경계를 사용합니다.
     */
    public void writeDetached(Long worldId, Map<BlockPos, BlockDiffBuffer.Change> drained) {
        if (drained.isEmpty()) return;
        List<Map.Entry<BlockPos, BlockDiffBuffer.Change>> rows = new ArrayList<>(drained.entrySet());
        // uq_world_xyz 순서로 잠금을 획득하도록 정렬합니다.
        rows.sort(Comparator.comparingInt((Map.Entry<BlockPos, BlockDiffBuffer.Change> row) -> row.getKey().x())
                .thenComparingInt(row -> row.getKey().y())
                .thenComparingInt(row -> row.getKey().z()));
        // 한 배치의 모든 행이 동일 시각을 공유(행마다 now() 재생성 제거 — 오히려 자연스러움).
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.batchUpdate(upsertSql(), new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    BlockPos pos = rows.get(i).getKey();
                    ps.setLong(1, worldId);
                    ps.setInt(2, pos.x());
                    ps.setInt(3, pos.y());
                    ps.setInt(4, pos.z());
                    ps.setInt(5, Math.floorDiv(pos.x(), 16));
                    ps.setInt(6, Math.floorDiv(pos.z(), 16));
                    BlockDiffBuffer.Change change = rows.get(i).getValue();
                    ps.setShort(7, change.getBlockType());
                    ps.setShort(8, change.getState());
                    ps.setString(9, change.getMobMutationKey());
                    ps.setTimestamp(10, now);
                }

                @Override
                public int getBatchSize() {
                    return rows.size();
                }
            });
    }

    @PreDestroy
    public void flushAllOnShutdown() {
        /*
         * @DependsOn은 파괴 시 역순으로 적용되어 이 훅이 persistence executor와
         * entityManagerFactory보다 먼저 실행됩니다. 직접 JDBC를 호출하지 않고 단일 쓰기
         * 큐의 마지막 작업으로 넣어, 앞서 제출된 플레이어 저장·diff flush가 끝난 뒤
         * 복원된 버퍼까지 모두 쓰고 완료를 기다립니다.
         */
        boolean interrupted = false;
        var finalFlush = persistenceExecutor.submitFuture(() -> {
            List<Long> worldIds = buffer.worldIds().stream().sorted().toList();
            IllegalStateException aggregateFailure = null;
            for (Long worldId : worldIds) {
                try {
                    writeWorld(worldId);
                } catch (RuntimeException exception) {
                    if (aggregateFailure == null) {
                        aggregateFailure = new IllegalStateException("종료 시 일부 월드의 블록 diff flush 실패");
                    }
                    aggregateFailure.addSuppressed(new IllegalStateException(
                            "월드 " + worldId + " 종료 flush 실패", exception));
                }
            }
            if (aggregateFailure != null) {
                throw aggregateFailure;
            }
        });
        RuntimeException shutdownFailure = null;
        while (true) {
            try {
                finalFlush.get();
                break;
            } catch (InterruptedException exception) {
                interrupted = true;
            } catch (ExecutionException exception) {
                log.warn("종료 시 블록 diff flush 실패", exception.getCause());
                shutdownFailure = new IllegalStateException("종료 시 블록 diff flush 실패", exception.getCause());
                break;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        if (shutdownFailure != null) {
            throw shutdownFailure;
        }
    }
}
