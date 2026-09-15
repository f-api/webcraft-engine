-- Durable descriptor-free canonical LOOT production context.
-- Existing rows are never fabricated: a legacy table without this column must be empty.

SET @canonical_loot_context_database = DATABASE();
SELECT CASE
    WHEN @canonical_loot_context_database IS NOT NULL
         AND CHAR_LENGTH(@canonical_loot_context_database) > 0
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @canonical_loot_context_quoted_database = CONCAT(
    '`', REPLACE(@canonical_loot_context_database, '`', '``'), '`');
SET @canonical_loot_context_table = CONCAT(
    @canonical_loot_context_quoted_database, '.`world_canonical_loot_assignments`');

SET @canonical_loot_context_lock_namespace = CONCAT(
    'gameexpert:v004:canonical-loot-context:', @canonical_loot_context_database);
SET @canonical_loot_context_lock_name = SHA2(
    @canonical_loot_context_lock_namespace, 256);
SET @canonical_loot_context_lock_timeout_seconds = 30;
SELECT CASE
    WHEN CHAR_LENGTH(@canonical_loot_context_lock_name) BETWEEN 1 AND 64
         AND @canonical_loot_context_lock_timeout_seconds BETWEEN 1 AND 60
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @canonical_loot_context_lock_result = GET_LOCK(
    @canonical_loot_context_lock_name, @canonical_loot_context_lock_timeout_seconds);
SELECT CASE
    WHEN @canonical_loot_context_lock_result = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

-- The migration owns only this payload column. Existing table identity is checked at the
-- same engine/charset boundary used by the current MySQL schema contract.
SET @canonical_loot_context_table_object_exists = EXISTS(
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'world_canonical_loot_assignments');
SET @canonical_loot_context_table_exists = EXISTS(
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'world_canonical_loot_assignments'
      AND table_type = 'BASE TABLE');
SET @canonical_loot_context_table_contract_exact = IF(
    @canonical_loot_context_table_object_exists = 0,
    1,
    IF(
        @canonical_loot_context_table_exists = 1
        AND EXISTS(
            SELECT 1
            FROM information_schema.tables AS checked_tables
            JOIN information_schema.collations AS checked_collations
              ON checked_collations.collation_name = checked_tables.table_collation
            WHERE checked_tables.table_schema = DATABASE()
              AND checked_tables.table_name = 'world_canonical_loot_assignments'
              AND checked_tables.table_type = 'BASE TABLE'
              AND checked_tables.engine = 'InnoDB'
              AND checked_tables.table_collation = 'utf8mb4_unicode_ci'
              AND checked_collations.character_set_name = 'utf8mb4'
              AND COALESCE(checked_tables.create_options, '') = ''),
        1,
        0));
SELECT CASE
    WHEN @canonical_loot_context_table_contract_exact = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @canonical_loot_context_column_exists = EXISTS(
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'world_canonical_loot_assignments'
      AND column_name = 'production_context_payload');
SET @canonical_loot_context_existing_column_exact = IF(
    @canonical_loot_context_table_exists = 0
    OR @canonical_loot_context_column_exists = 0,
    1,
    IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'world_canonical_loot_assignments'
              AND column_name = 'production_context_payload'
              AND data_type = 'longblob'
              AND column_type = 'longblob'
              AND is_nullable = 'NO'
              AND column_default IS NULL
              AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND character_set_name IS NULL
              AND collation_name IS NULL),
        1,
        0));
SELECT CASE
    WHEN @canonical_loot_context_existing_column_exact = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

-- A missing column is admissible only for an empty table. The qualified identifier is built
-- from the escaped current schema above, so this check cannot read another database.
SET @canonical_loot_context_data_preflight_sql = IF(
    @canonical_loot_context_table_exists = 0
    OR @canonical_loot_context_column_exists = 1,
    'SELECT 1',
    CONCAT(
        'SELECT CASE WHEN NOT EXISTS (SELECT 1 FROM ',
        @canonical_loot_context_table,
        ') THEN 1 ELSE REGEXP_LIKE(''x'', ''['') END'));
PREPARE canonical_loot_context_data_preflight_statement
    FROM @canonical_loot_context_data_preflight_sql;
EXECUTE canonical_loot_context_data_preflight_statement;
DEALLOCATE PREPARE canonical_loot_context_data_preflight_statement;

SET @canonical_loot_context_ddl = IF(
    @canonical_loot_context_table_exists = 1
    AND @canonical_loot_context_column_exists = 0,
    CONCAT(
        'ALTER TABLE ', @canonical_loot_context_table,
        ' ADD COLUMN `production_context_payload` LONGBLOB NOT NULL'),
    'SELECT 1');
PREPARE canonical_loot_context_ddl_statement FROM @canonical_loot_context_ddl;
EXECUTE canonical_loot_context_ddl_statement;
DEALLOCATE PREPARE canonical_loot_context_ddl_statement;

-- Re-read the exact shape after the optional ADD so a concurrent or malformed change cannot
-- be accepted merely because the column name exists.
SET @canonical_loot_context_postcondition_exact = IF(
    @canonical_loot_context_table_exists = 0,
    1,
    IF(
        EXISTS(
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'world_canonical_loot_assignments'
              AND column_name = 'production_context_payload'
              AND data_type = 'longblob'
              AND column_type = 'longblob'
              AND is_nullable = 'NO'
              AND column_default IS NULL
              AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND character_set_name IS NULL
              AND collation_name IS NULL),
        1,
        0));
SELECT CASE
    WHEN @canonical_loot_context_postcondition_exact = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @canonical_loot_context_lock_release_result = RELEASE_LOCK(
    @canonical_loot_context_lock_name);
SELECT CASE
    WHEN @canonical_loot_context_lock_release_result = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;
