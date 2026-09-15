-- Entity IDs are world-local. Correct only the known global authority index left behind
-- by Hibernate schema update; never rewrite rows, IDs, payloads or saved-world identities.
SET @structure_scope_database = DATABASE();
SELECT CASE WHEN @structure_scope_database IS NOT NULL
    AND CHAR_LENGTH(@structure_scope_database) > 0
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;
SET @structure_scope_table = CONCAT(
    '`', REPLACE(@structure_scope_database, '`', '``'), '`.`world_structure_entities`');
SET @structure_scope_lock_name = SHA2(CONCAT(
    'gameexpert:v010:structure-entity-scope:', @structure_scope_database), 256);
SET @structure_scope_lock_timeout_seconds = 30;
SET @structure_scope_lock_result = GET_LOCK(
    @structure_scope_lock_name, @structure_scope_lock_timeout_seconds);
SELECT CASE WHEN @structure_scope_lock_result = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

-- Bound metadata-lock acquisition as well as the cooperating initializer lock.
SET @structure_scope_previous_lock_wait_timeout = @@SESSION.lock_wait_timeout;
SET SESSION lock_wait_timeout = 30;
SET @structure_scope_table_object_exists = EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'world_structure_entities');
SET @structure_scope_table_exact = EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'world_structure_entities'
      AND table_type = 'BASE TABLE' AND engine = 'InnoDB');

-- Only the columns participating in this index are in scope. Hibernate owns the
-- remaining shape, including physical column ordering and string collations.
SET @structure_scope_columns_exact = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'world_structure_entities'
      AND column_name IN ('world_id', 'authoritative_entity_id')
      AND data_type = 'bigint' AND column_type = 'bigint'
      AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
      AND COALESCE(generation_expression, '') = '') = 2;
SET @structure_scope_index_rows = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'world_structure_entities'
      AND index_name = 'uk_structure_entity_authority_id');
SET @structure_scope_old_exact = @structure_scope_index_rows = 1 AND (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'world_structure_entities'
      AND index_name = 'uk_structure_entity_authority_id'
      AND non_unique = 0 AND seq_in_index = 1
      AND column_name = 'authoritative_entity_id'
      AND sub_part IS NULL AND index_type = 'BTREE'
      AND is_visible = 'YES' AND collation = 'A') = 1;
SET @structure_scope_current_exact = @structure_scope_index_rows = 2 AND (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'world_structure_entities'
      AND index_name = 'uk_structure_entity_authority_id'
      AND non_unique = 0 AND sub_part IS NULL AND index_type = 'BTREE'
      AND is_visible = 'YES' AND collation = 'A'
      AND ((seq_in_index = 1 AND column_name = 'world_id')
        OR (seq_in_index = 2 AND column_name = 'authoritative_entity_id'))) = 2;
SET @structure_scope_dependencies_safe = NOT EXISTS (
    SELECT 1 FROM information_schema.referential_constraints
    WHERE unique_constraint_schema = DATABASE()
      AND referenced_table_name = 'world_structure_entities'
      AND unique_constraint_name = 'uk_structure_entity_authority_id');
SET @structure_scope_contract_safe = @structure_scope_lock_result = 1
    AND (@structure_scope_table_object_exists = 0
      OR (@structure_scope_table_exact = 1 AND @structure_scope_columns_exact = 1
        AND (@structure_scope_current_exact = 1
          OR ((@structure_scope_old_exact = 1 OR @structure_scope_index_rows = 0)
            AND @structure_scope_dependencies_safe = 1))));
SELECT CASE WHEN @structure_scope_contract_safe = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

-- Reset the result before a guarded query so no prior pooled-session value can authorize
-- DDL. A missing index is added only after proving all existing pairs are unique.
SET @structure_scope_data_safe = 0;
SET @structure_scope_preflight_sql = IF(
    @structure_scope_contract_safe = 1 AND @structure_scope_table_exact = 1
      AND @structure_scope_current_exact = 0,
    CONCAT('SELECT NOT EXISTS (SELECT 1 FROM ', @structure_scope_table,
        ' GROUP BY `world_id`, `authoritative_entity_id` HAVING COUNT(*) > 1)',
        ' INTO @structure_scope_data_safe'),
    'SELECT 1 INTO @structure_scope_data_safe');
PREPARE structure_scope_preflight FROM @structure_scope_preflight_sql;
EXECUTE structure_scope_preflight;
DEALLOCATE PREPARE structure_scope_preflight;
SELECT CASE WHEN @structure_scope_contract_safe = 1 AND @structure_scope_data_safe = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

-- The old constraint is replaced in one atomic InnoDB ALTER. An unexpected shape never
-- reaches a DROP, even if a caller incorrectly continues after a failed assertion.
SET @structure_scope_ddl = IF(
    @structure_scope_contract_safe = 1 AND @structure_scope_data_safe = 1,
    IF(@structure_scope_table_object_exists = 0 OR @structure_scope_current_exact = 1,
        'SELECT 1',
        CONCAT('ALTER TABLE ', @structure_scope_table,
            IF(@structure_scope_old_exact = 1,
                ' DROP INDEX `uk_structure_entity_authority_id`,', ''),
            ' ADD UNIQUE INDEX `uk_structure_entity_authority_id` (`world_id`, `authoritative_entity_id`), ALGORITHM=INPLACE, LOCK=NONE')),
    'SELECT REGEXP_LIKE(''x'', ''['')');
PREPARE structure_scope_ddl_statement FROM @structure_scope_ddl;
EXECUTE structure_scope_ddl_statement;
DEALLOCATE PREPARE structure_scope_ddl_statement;

-- Re-read the final shape; a fresh schema remains untouched until Hibernate creates it.
SET @structure_scope_postcondition_exact = IF(@structure_scope_table_object_exists = 0,
    NOT EXISTS (SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'world_structure_entities'),
    (SELECT COUNT(*) FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'world_structure_entities'
          AND index_name = 'uk_structure_entity_authority_id') = 2
    AND (SELECT COUNT(*) FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'world_structure_entities'
          AND index_name = 'uk_structure_entity_authority_id'
          AND non_unique = 0 AND sub_part IS NULL AND index_type = 'BTREE'
          AND is_visible = 'YES' AND collation = 'A'
          AND ((seq_in_index = 1 AND column_name = 'world_id')
            OR (seq_in_index = 2 AND column_name = 'authoritative_entity_id'))) = 2);
SELECT CASE WHEN @structure_scope_contract_safe = 1
    AND @structure_scope_postcondition_exact = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;
SET SESSION lock_wait_timeout = @structure_scope_previous_lock_wait_timeout;
SET @structure_scope_release_result = RELEASE_LOCK(@structure_scope_lock_name);
SELECT CASE WHEN @structure_scope_release_result = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;
