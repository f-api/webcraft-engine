-- Current-hash durable-tick schema. Existing rows are never defaulted: missing current identity
-- or publication data fails the current-hash CHECK below and stops startup.

CREATE TABLE IF NOT EXISTS final_carrier_scheduled_ticks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    world_id BIGINT NOT NULL,
    chunk_x INT NOT NULL,
    chunk_z INT NOT NULL,
    lane VARCHAR(8) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    type_key VARCHAR(160) NOT NULL,
    expected_block_id INT NOT NULL,
    due_tick BIGINT NOT NULL,
    priority INT NOT NULL,
    sub_tick_order BIGINT NOT NULL,
    durable_order BIGINT NOT NULL,
    source_fingerprint VARCHAR(64) NOT NULL,
    payload_fingerprint VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_final_carrier_scheduled_tick
        UNIQUE (world_id, lane, x, y, z, type_key),
    CONSTRAINT uk_final_carrier_scheduled_world_lane_order
        UNIQUE (world_id, lane, durable_order),
    KEY idx_final_carrier_scheduled_world_lane_order
        (world_id, lane, durable_order, id),
    KEY idx_final_carrier_scheduled_world_chunk_lane_order
        (world_id, chunk_x, chunk_z, lane, durable_order, id),
    KEY idx_final_carrier_scheduled_settlement_key
        (world_id, chunk_x, chunk_z, lane, x, y, z, type_key),
    CONSTRAINT ck_final_carrier_scheduled_tick_current_hash CHECK (
        world_id IS NOT NULL
        AND world_id > 0
        AND chunk_x IS NOT NULL
        AND chunk_z IS NOT NULL
        AND lane IS NOT NULL
        AND lane IN ('BLOCK', 'FLUID')
        AND x IS NOT NULL
        AND y IS NOT NULL
        AND y BETWEEN -64 AND 319
        AND z IS NOT NULL
        AND FLOOR(x / 16) = chunk_x
        AND FLOOR(z / 16) = chunk_z
        AND type_key IS NOT NULL
        AND OCTET_LENGTH(type_key) BETWEEN 1 AND 160
        AND REGEXP_LIKE(type_key, '^[a-z0-9_.-]+:[a-z0-9_./-]+$', 'c')
        AND expected_block_id IS NOT NULL
        AND ((lane = 'BLOCK' AND expected_block_id BETWEEN 0 AND 65535)
             OR (lane = 'FLUID' AND expected_block_id = -1))
        AND due_tick IS NOT NULL
        AND due_tick >= 0
        AND priority IS NOT NULL
        AND priority BETWEEN -3 AND 3
        AND sub_tick_order IS NOT NULL
        AND sub_tick_order >= 0
        AND durable_order IS NOT NULL
        AND durable_order > 0
        AND source_fingerprint IS NOT NULL
        AND REGEXP_LIKE(source_fingerprint, '^[0-9a-f]{64}$', 'c')
        AND payload_fingerprint IS NOT NULL
        AND REGEXP_LIKE(payload_fingerprint, '^[0-9a-f]{64}$', 'c')
    )
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS final_carrier_consumed_ticks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    world_id BIGINT NOT NULL,
    chunk_x INT NOT NULL,
    chunk_z INT NOT NULL,
    lane VARCHAR(8) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    type_key VARCHAR(160) NOT NULL,
    expected_block_id INT NOT NULL,
    due_tick BIGINT NOT NULL,
    priority INT NOT NULL,
    sub_tick_order BIGINT NOT NULL,
    durable_order BIGINT NOT NULL,
    source_fingerprint VARCHAR(64) NOT NULL,
    payload_fingerprint VARCHAR(64) NOT NULL,
    disposition VARCHAR(24) NOT NULL,
    publication_key VARCHAR(64) NOT NULL,
    publication_digest VARCHAR(64) NOT NULL,
    publication_body MEDIUMBLOB NOT NULL,
    publication_state VARCHAR(16) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_final_carrier_consumed_tick
        UNIQUE (world_id, lane, x, y, z, type_key),
    CONSTRAINT uk_final_carrier_consumed_tick_publication
        UNIQUE (publication_key),
    CONSTRAINT uk_final_carrier_consumed_world_lane_order
        UNIQUE (world_id, lane, durable_order),
    KEY idx_final_carrier_consumed_world_lane_state_order
        (world_id, lane, publication_state, durable_order, id),
    KEY idx_final_carrier_consumed_world_chunk_lane_state_order
        (world_id, chunk_x, chunk_z, lane, publication_state, durable_order, id),
    CONSTRAINT ck_final_carrier_consumed_tick_current_hash CHECK (
        world_id IS NOT NULL
        AND world_id > 0
        AND chunk_x IS NOT NULL
        AND chunk_z IS NOT NULL
        AND lane IS NOT NULL
        AND lane IN ('BLOCK', 'FLUID')
        AND x IS NOT NULL
        AND y IS NOT NULL
        AND y BETWEEN -64 AND 319
        AND z IS NOT NULL
        AND FLOOR(x / 16) = chunk_x
        AND FLOOR(z / 16) = chunk_z
        AND type_key IS NOT NULL
        AND OCTET_LENGTH(type_key) BETWEEN 1 AND 160
        AND REGEXP_LIKE(type_key, '^[a-z0-9_.-]+:[a-z0-9_./-]+$', 'c')
        AND expected_block_id IS NOT NULL
        AND ((lane = 'BLOCK' AND expected_block_id BETWEEN 0 AND 65535)
             OR (lane = 'FLUID' AND expected_block_id = -1))
        AND due_tick IS NOT NULL
        AND due_tick >= 0
        AND priority IS NOT NULL
        AND priority BETWEEN -3 AND 3
        AND sub_tick_order IS NOT NULL
        AND sub_tick_order >= 0
        AND durable_order IS NOT NULL
        AND durable_order > 0
        AND source_fingerprint IS NOT NULL
        AND REGEXP_LIKE(source_fingerprint, '^[0-9a-f]{64}$', 'c')
        AND payload_fingerprint IS NOT NULL
        AND REGEXP_LIKE(payload_fingerprint, '^[0-9a-f]{64}$', 'c')
        AND disposition IS NOT NULL
        AND disposition IN ('EXECUTE', 'LIVE_TYPE_NO_OP')
        AND publication_key IS NOT NULL
        AND OCTET_LENGTH(publication_key) = 64
        AND REGEXP_LIKE(publication_key, '^[0-9a-f]{64}$', 'c')
        AND publication_digest IS NOT NULL
        AND OCTET_LENGTH(publication_digest) = 64
        AND REGEXP_LIKE(publication_digest, '^[0-9a-f]{64}$', 'c')
        AND publication_body IS NOT NULL
        AND OCTET_LENGTH(publication_body) BETWEEN 1 AND 16777215
        AND publication_state IS NOT NULL
        AND publication_state IN ('UNACKNOWLEDGED', 'OUTCOME_UNKNOWN', 'ACKNOWLEDGED', 'REJECTED')
    )
) ENGINE=InnoDB;

-- The scheduled table predates durable_order. Add it nullable only long enough for the
-- fail-closed current-hash check; no legacy value is synthesized.
SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'final_carrier_scheduled_ticks'
          AND column_name = 'durable_order'
    ),
    'SELECT 1',
    'ALTER TABLE final_carrier_scheduled_ticks ADD COLUMN durable_order BIGINT NULL'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;

-- Consumed rows from the previous schema have no current identity or publication proof. Each
-- missing member is added without a default so the current-hash check below quarantines them.
SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'final_carrier_consumed_ticks'
          AND column_name = 'chunk_x'
    ), 'SELECT 1',
    'ALTER TABLE final_carrier_consumed_ticks ADD COLUMN chunk_x INT NULL'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;

SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'final_carrier_consumed_ticks'
          AND column_name = 'chunk_z'
    ), 'SELECT 1',
    'ALTER TABLE final_carrier_consumed_ticks ADD COLUMN chunk_z INT NULL'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;

SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'final_carrier_consumed_ticks'
          AND column_name = 'expected_block_id'
    ), 'SELECT 1',
    'ALTER TABLE final_carrier_consumed_ticks ADD COLUMN expected_block_id INT NULL'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;

SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'final_carrier_consumed_ticks'
          AND column_name = 'due_tick'
    ), 'SELECT 1',
    'ALTER TABLE final_carrier_consumed_ticks ADD COLUMN due_tick BIGINT NULL'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;

SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'final_carrier_consumed_ticks'
          AND column_name = 'priority'
    ), 'SELECT 1',
    'ALTER TABLE final_carrier_consumed_ticks ADD COLUMN priority INT NULL'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;

SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'final_carrier_consumed_ticks'
          AND column_name = 'sub_tick_order'
    ), 'SELECT 1',
    'ALTER TABLE final_carrier_consumed_ticks ADD COLUMN sub_tick_order BIGINT NULL'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;

SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'final_carrier_consumed_ticks'
          AND column_name = 'durable_order'
    ), 'SELECT 1',
    'ALTER TABLE final_carrier_consumed_ticks ADD COLUMN durable_order BIGINT NULL'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;

SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'final_carrier_consumed_ticks'
          AND column_name = 'source_fingerprint'
    ), 'SELECT 1',
    'ALTER TABLE final_carrier_consumed_ticks ADD COLUMN source_fingerprint VARCHAR(64) NULL'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;

SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'final_carrier_consumed_ticks'
          AND column_name = 'payload_fingerprint'
    ), 'SELECT 1',
    'ALTER TABLE final_carrier_consumed_ticks ADD COLUMN payload_fingerprint VARCHAR(64) NULL'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;

SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'final_carrier_consumed_ticks'
          AND column_name = 'publication_key'
    ), 'SELECT 1',
    'ALTER TABLE final_carrier_consumed_ticks ADD COLUMN publication_key VARCHAR(64) NULL'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;

SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'final_carrier_consumed_ticks'
          AND column_name = 'publication_digest'
    ), 'SELECT 1',
    'ALTER TABLE final_carrier_consumed_ticks ADD COLUMN publication_digest VARCHAR(64) NULL'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;

SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'final_carrier_consumed_ticks'
          AND column_name = 'publication_body'
    ), 'SELECT 1',
    'ALTER TABLE final_carrier_consumed_ticks ADD COLUMN publication_body MEDIUMBLOB NULL'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;

SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'final_carrier_consumed_ticks'
          AND column_name = 'publication_state'
    ), 'SELECT 1',
    'ALTER TABLE final_carrier_consumed_ticks ADD COLUMN publication_state VARCHAR(16) NULL'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;

-- Preserve pre-current-hash data without letting rows that lack reconstructable publication
-- evidence block startup. Scheduled rows retain their original insertion order as durable order;
-- consumed rows cannot be safely reconstructed and are moved intact to a quarantine table.
UPDATE final_carrier_scheduled_ticks
SET durable_order = id
WHERE durable_order IS NULL;

CREATE TABLE IF NOT EXISTS final_carrier_consumed_ticks_legacy_quarantine
LIKE final_carrier_consumed_ticks;

INSERT IGNORE INTO final_carrier_consumed_ticks_legacy_quarantine
SELECT * FROM final_carrier_consumed_ticks
WHERE chunk_x IS NULL OR chunk_z IS NULL OR expected_block_id IS NULL
   OR due_tick IS NULL OR priority IS NULL OR sub_tick_order IS NULL
   OR durable_order IS NULL OR source_fingerprint IS NULL OR payload_fingerprint IS NULL
   OR publication_key IS NULL OR publication_digest IS NULL OR publication_body IS NULL
   OR publication_state IS NULL;

DELETE current_row
FROM final_carrier_consumed_ticks current_row
JOIN final_carrier_consumed_ticks_legacy_quarantine quarantined ON quarantined.id = current_row.id
WHERE current_row.chunk_x IS NULL OR current_row.chunk_z IS NULL
   OR current_row.expected_block_id IS NULL OR current_row.due_tick IS NULL
   OR current_row.priority IS NULL OR current_row.sub_tick_order IS NULL
   OR current_row.durable_order IS NULL OR current_row.source_fingerprint IS NULL
   OR current_row.payload_fingerprint IS NULL OR current_row.publication_key IS NULL
   OR current_row.publication_digest IS NULL OR current_row.publication_body IS NULL
   OR current_row.publication_state IS NULL;

SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'final_carrier_scheduled_ticks'
          AND constraint_name = 'ck_final_carrier_scheduled_tick_current_hash'
    ), 'SELECT 1',
    'ALTER TABLE final_carrier_scheduled_ticks ADD CONSTRAINT ck_final_carrier_scheduled_tick_current_hash CHECK (world_id IS NOT NULL AND world_id > 0 AND chunk_x IS NOT NULL AND chunk_z IS NOT NULL AND lane IS NOT NULL AND lane IN (''BLOCK'', ''FLUID'') AND x IS NOT NULL AND y IS NOT NULL AND y BETWEEN -64 AND 319 AND z IS NOT NULL AND FLOOR(x / 16) = chunk_x AND FLOOR(z / 16) = chunk_z AND type_key IS NOT NULL AND OCTET_LENGTH(type_key) BETWEEN 1 AND 160 AND REGEXP_LIKE(type_key, ''^[a-z0-9_.-]+:[a-z0-9_./-]+$'', ''c'') AND expected_block_id IS NOT NULL AND ((lane = ''BLOCK'' AND expected_block_id BETWEEN 0 AND 65535) OR (lane = ''FLUID'' AND expected_block_id = -1)) AND due_tick IS NOT NULL AND due_tick >= 0 AND priority IS NOT NULL AND priority BETWEEN -3 AND 3 AND sub_tick_order IS NOT NULL AND sub_tick_order >= 0 AND durable_order IS NOT NULL AND durable_order > 0 AND source_fingerprint IS NOT NULL AND REGEXP_LIKE(source_fingerprint, ''^[0-9a-f]{64}$'', ''c'') AND payload_fingerprint IS NOT NULL AND REGEXP_LIKE(payload_fingerprint, ''^[0-9a-f]{64}$'', ''c''))'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;

SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'final_carrier_consumed_ticks'
          AND constraint_name = 'ck_final_carrier_consumed_tick_current_hash'
    ), 'SELECT 1',
    'ALTER TABLE final_carrier_consumed_ticks ADD CONSTRAINT ck_final_carrier_consumed_tick_current_hash CHECK (world_id IS NOT NULL AND world_id > 0 AND chunk_x IS NOT NULL AND chunk_z IS NOT NULL AND lane IS NOT NULL AND lane IN (''BLOCK'', ''FLUID'') AND x IS NOT NULL AND y IS NOT NULL AND y BETWEEN -64 AND 319 AND z IS NOT NULL AND FLOOR(x / 16) = chunk_x AND FLOOR(z / 16) = chunk_z AND type_key IS NOT NULL AND OCTET_LENGTH(type_key) BETWEEN 1 AND 160 AND REGEXP_LIKE(type_key, ''^[a-z0-9_.-]+:[a-z0-9_./-]+$'', ''c'') AND expected_block_id IS NOT NULL AND ((lane = ''BLOCK'' AND expected_block_id BETWEEN 0 AND 65535) OR (lane = ''FLUID'' AND expected_block_id = -1)) AND due_tick IS NOT NULL AND due_tick >= 0 AND priority IS NOT NULL AND priority BETWEEN -3 AND 3 AND sub_tick_order IS NOT NULL AND sub_tick_order >= 0 AND durable_order IS NOT NULL AND durable_order > 0 AND source_fingerprint IS NOT NULL AND REGEXP_LIKE(source_fingerprint, ''^[0-9a-f]{64}$'', ''c'') AND payload_fingerprint IS NOT NULL AND REGEXP_LIKE(payload_fingerprint, ''^[0-9a-f]{64}$'', ''c'') AND disposition IS NOT NULL AND disposition IN (''EXECUTE'', ''LIVE_TYPE_NO_OP'') AND publication_key IS NOT NULL AND OCTET_LENGTH(publication_key) = 64 AND REGEXP_LIKE(publication_key, ''^[0-9a-f]{64}$'', ''c'') AND publication_digest IS NOT NULL AND OCTET_LENGTH(publication_digest) = 64 AND REGEXP_LIKE(publication_digest, ''^[0-9a-f]{64}$'', ''c'') AND publication_body IS NOT NULL AND OCTET_LENGTH(publication_body) BETWEEN 1 AND 16777215 AND publication_state IS NOT NULL AND publication_state IN (''UNACKNOWLEDGED'', ''OUTCOME_UNKNOWN'', ''ACKNOWLEDGED'', ''REJECTED''))'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;

-- Once all rows satisfy the current-hash proof, make every current field physically non-null.
SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'final_carrier_scheduled_ticks'
          AND ((column_name = 'world_id' AND (data_type <> 'bigint' OR is_nullable <> 'NO'))
            OR (column_name IN ('chunk_x', 'chunk_z', 'x', 'y', 'z', 'expected_block_id', 'priority')
                AND (data_type <> 'int' OR is_nullable <> 'NO'))
            OR (column_name IN ('lane') AND (data_type <> 'varchar' OR character_maximum_length <> 8 OR is_nullable <> 'NO'))
            OR (column_name IN ('type_key') AND (data_type <> 'varchar' OR character_maximum_length <> 160 OR is_nullable <> 'NO'))
            OR (column_name IN ('due_tick', 'sub_tick_order', 'durable_order') AND (data_type <> 'bigint' OR is_nullable <> 'NO'))
            OR (column_name IN ('source_fingerprint', 'payload_fingerprint') AND (data_type <> 'varchar' OR character_maximum_length <> 64 OR is_nullable <> 'NO')))
    ),
    'ALTER TABLE final_carrier_scheduled_ticks MODIFY world_id BIGINT NOT NULL, MODIFY chunk_x INT NOT NULL, MODIFY chunk_z INT NOT NULL, MODIFY lane VARCHAR(8) NOT NULL, MODIFY x INT NOT NULL, MODIFY y INT NOT NULL, MODIFY z INT NOT NULL, MODIFY type_key VARCHAR(160) NOT NULL, MODIFY expected_block_id INT NOT NULL, MODIFY due_tick BIGINT NOT NULL, MODIFY priority INT NOT NULL, MODIFY sub_tick_order BIGINT NOT NULL, MODIFY durable_order BIGINT NOT NULL, MODIFY source_fingerprint VARCHAR(64) NOT NULL, MODIFY payload_fingerprint VARCHAR(64) NOT NULL',
    'SELECT 1'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;

SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'final_carrier_consumed_ticks'
          AND ((column_name = 'world_id' AND (data_type <> 'bigint' OR is_nullable <> 'NO'))
            OR (column_name IN ('chunk_x', 'chunk_z', 'x', 'y', 'z', 'expected_block_id', 'priority')
                AND (data_type <> 'int' OR is_nullable <> 'NO'))
            OR (column_name IN ('lane') AND (data_type <> 'varchar' OR character_maximum_length <> 8 OR is_nullable <> 'NO'))
            OR (column_name IN ('type_key') AND (data_type <> 'varchar' OR character_maximum_length <> 160 OR is_nullable <> 'NO'))
            OR (column_name IN ('due_tick', 'sub_tick_order', 'durable_order') AND (data_type <> 'bigint' OR is_nullable <> 'NO'))
            OR (column_name IN ('source_fingerprint', 'payload_fingerprint', 'publication_key', 'publication_digest') AND (data_type <> 'varchar' OR character_maximum_length <> 64 OR is_nullable <> 'NO'))
            OR (column_name = 'disposition' AND (data_type <> 'varchar' OR character_maximum_length <> 24 OR is_nullable <> 'NO'))
            OR (column_name = 'publication_body' AND (data_type <> 'mediumblob' OR is_nullable <> 'NO'))
            OR (column_name = 'publication_state' AND (data_type <> 'varchar' OR character_maximum_length <> 16 OR is_nullable <> 'NO')))
    ),
    'ALTER TABLE final_carrier_consumed_ticks MODIFY world_id BIGINT NOT NULL, MODIFY chunk_x INT NOT NULL, MODIFY chunk_z INT NOT NULL, MODIFY lane VARCHAR(8) NOT NULL, MODIFY x INT NOT NULL, MODIFY y INT NOT NULL, MODIFY z INT NOT NULL, MODIFY type_key VARCHAR(160) NOT NULL, MODIFY expected_block_id INT NOT NULL, MODIFY due_tick BIGINT NOT NULL, MODIFY priority INT NOT NULL, MODIFY sub_tick_order BIGINT NOT NULL, MODIFY durable_order BIGINT NOT NULL, MODIFY source_fingerprint VARCHAR(64) NOT NULL, MODIFY payload_fingerprint VARCHAR(64) NOT NULL, MODIFY disposition VARCHAR(24) NOT NULL, MODIFY publication_key VARCHAR(64) NOT NULL, MODIFY publication_digest VARCHAR(64) NOT NULL, MODIFY publication_body MEDIUMBLOB NOT NULL, MODIFY publication_state VARCHAR(16) NOT NULL',
    'SELECT 1'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;

SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'final_carrier_scheduled_ticks'
          AND index_name = 'uk_final_carrier_scheduled_world_lane_order'
    ), 'SELECT 1',
    'ALTER TABLE final_carrier_scheduled_ticks ADD CONSTRAINT uk_final_carrier_scheduled_world_lane_order UNIQUE (world_id, lane, durable_order)'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;

SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'final_carrier_consumed_ticks'
          AND index_name = 'uk_final_carrier_consumed_world_lane_order'
    ), 'SELECT 1',
    'ALTER TABLE final_carrier_consumed_ticks ADD CONSTRAINT uk_final_carrier_consumed_world_lane_order UNIQUE (world_id, lane, durable_order)'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;

SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'final_carrier_consumed_ticks'
          AND index_name = 'uk_final_carrier_consumed_tick_publication'
    ), 'SELECT 1',
    'ALTER TABLE final_carrier_consumed_ticks ADD CONSTRAINT uk_final_carrier_consumed_tick_publication UNIQUE (publication_key)'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;

SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'final_carrier_consumed_ticks'
          AND index_name = 'idx_final_carrier_consumed_world_lane_state_order'
    ), 'SELECT 1',
    'ALTER TABLE final_carrier_consumed_ticks ADD INDEX idx_final_carrier_consumed_world_lane_state_order (world_id, lane, publication_state, durable_order, id)'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;

SET @final_carrier_tick_schema_ddl = IF(
    EXISTS(
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'final_carrier_consumed_ticks'
          AND index_name = 'idx_final_carrier_consumed_world_chunk_lane_state_order'
    ), 'SELECT 1',
    'ALTER TABLE final_carrier_consumed_ticks ADD INDEX idx_final_carrier_consumed_world_chunk_lane_state_order (world_id, chunk_x, chunk_z, lane, publication_state, durable_order, id)'
);
PREPARE final_carrier_tick_schema_statement FROM @final_carrier_tick_schema_ddl;
EXECUTE final_carrier_tick_schema_statement;
DEALLOCATE PREPARE final_carrier_tick_schema_statement;
