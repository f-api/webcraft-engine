-- Durable authenticated H12g final-scene terminal authority.
-- This script is intentionally MySQL-8.0.16+ only: CHECK constraints must be enforced.

SET @h12g_terminal_database = DATABASE();
SELECT CASE
    WHEN @h12g_terminal_database IS NOT NULL
         AND CHAR_LENGTH(@h12g_terminal_database) > 0
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @h12g_terminal_quoted_database = CONCAT(
    '`', REPLACE(@h12g_terminal_database, '`', '``'), '`');
SET @h12g_terminal_lock_namespace = CONCAT(
    'gameexpert:v003:h12g:', @h12g_terminal_database);
SET @h12g_terminal_lock_name = SHA2(@h12g_terminal_lock_namespace, 256);
SET @h12g_terminal_lock_timeout_seconds = 30;
SELECT CASE
    WHEN CHAR_LENGTH(@h12g_terminal_lock_name) BETWEEN 1 AND 64
         AND @h12g_terminal_lock_timeout_seconds BETWEEN 1 AND 60
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @h12g_terminal_lock_result = GET_LOCK(
    @h12g_terminal_lock_name, @h12g_terminal_lock_timeout_seconds);
SELECT CASE
    WHEN @h12g_terminal_lock_result = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @h12g_terminal_mysql_version = @@version;
SET @h12g_terminal_mysql_major = CAST(
    SUBSTRING_INDEX(@h12g_terminal_mysql_version, '.', 1) AS UNSIGNED);
SET @h12g_terminal_mysql_minor = CAST(
    SUBSTRING_INDEX(SUBSTRING_INDEX(@h12g_terminal_mysql_version, '.', 2), '.', -1)
    AS UNSIGNED);
SET @h12g_terminal_mysql_patch = CAST(
    SUBSTRING_INDEX(SUBSTRING_INDEX(@h12g_terminal_mysql_version, '.', 3), '.', -1)
    AS UNSIGNED);
SELECT CASE
    WHEN @h12g_terminal_mysql_version REGEXP '^[0-9]+[.][0-9]+[.][0-9]+'
         AND LOWER(@@version_comment) NOT LIKE '%mariadb%'
         AND (
             @h12g_terminal_mysql_major > 8
             OR (@h12g_terminal_mysql_major = 8
                 AND (
                     @h12g_terminal_mysql_minor > 0
                     OR (@h12g_terminal_mysql_minor = 0
                         AND @h12g_terminal_mysql_patch >= 16)
                 ))
         )
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @h12g_terminal_sql_mode = @@SESSION.sql_mode;
SELECT CASE
    WHEN FIND_IN_SET('STRICT_TRANS_TABLES', @h12g_terminal_sql_mode) > 0
         OR FIND_IN_SET('STRICT_ALL_TABLES', @h12g_terminal_sql_mode) > 0
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @h12g_terminal_assertion_failure = 'REGEXP_LIKE(''x'', ''['')';
SET @h12g_terminal_parent_table = CONCAT(
    @h12g_terminal_quoted_database, '.`final_scene_h12g_terminal_keys`');
SET @h12g_terminal_child_table = CONCAT(
    @h12g_terminal_quoted_database, '.`final_scene_h12g_terminals`');

SET @h12g_terminal_parent_check_semantics =
    'id=1andversion>=0andversion<9223372036854775807andkey_epoch>0'
    'andkey_epoch<9223372036854775807andlengthkey_identity=64'
    'andregexp_likekey_identity,''^[0-9a-f]{64}$'',''c''andlengthmac_key=32';
SET @h12g_terminal_child_check_semantics =
    'version>=0andversion<9223372036854775807andworld_id>0'
    'andworld_id<9223372036854775807andlengthsource_identity=64'
    'andregexp_likesource_identity,''^[0-9a-f]{64}$'',''c''andlengthterminal_keybetween1and256'
    'andlengthbinding_digest=64'
    'andregexp_likebinding_digest,''^[0-9a-f]{64}$'',''c''andkey_epoch>0'
    'andkey_epoch<9223372036854775807andlengthkey_identity=64'
    'andregexp_likekey_identity,''^[0-9a-f]{64}$'',''c''andlengthauthenticated_envelopebetween1and524288';

SET @h12g_terminal_parent_row_violation = CONCAT(
    'parent_rows.`id` IS NULL OR CAST(parent_rows.`id` AS CHAR) NOT REGEXP ''^-?[0-9]+$'' '
    'OR parent_rows.`id` < -9223372036854775808 '
    'OR parent_rows.`id` > 9223372036854775807 '
    'OR parent_rows.`id` <> 1 '
    'OR parent_rows.`version` IS NULL '
    'OR CAST(parent_rows.`version` AS CHAR) NOT REGEXP ''^[0-9]+$'' '
    'OR parent_rows.`version` < 0 OR parent_rows.`version` >= 9223372036854775807 '
    'OR parent_rows.`key_epoch` IS NULL '
    'OR CAST(parent_rows.`key_epoch` AS CHAR) NOT REGEXP ''^[0-9]+$'' '
    'OR parent_rows.`key_epoch` <= 0 '
    'OR parent_rows.`key_epoch` >= 9223372036854775807 '
    'OR parent_rows.`key_identity` IS NULL '
    'OR OCTET_LENGTH(parent_rows.`key_identity`) <> 64 '
    'OR NOT REGEXP_LIKE(parent_rows.`key_identity`, ''^[0-9a-f]{64}$'', ''c'') '
    'OR parent_rows.`mac_key` IS NULL OR OCTET_LENGTH(parent_rows.`mac_key`) <> 32');
SET @h12g_terminal_parent_data_safe = CONCAT(
    'NOT EXISTS (SELECT 1 FROM ', @h12g_terminal_parent_table,
    ' AS parent_rows WHERE ', @h12g_terminal_parent_row_violation, ')',
    ' AND NOT EXISTS (SELECT 1 FROM ', @h12g_terminal_parent_table,
    ' GROUP BY `id` HAVING COUNT(*) > 1)',
    ' AND NOT EXISTS (SELECT 1 FROM ', @h12g_terminal_parent_table,
    ' GROUP BY `key_epoch`, `key_identity` HAVING COUNT(*) > 1)');

SET @h12g_terminal_child_row_violation = CONCAT(
    'child_rows.`id` IS NULL OR CAST(child_rows.`id` AS CHAR) NOT REGEXP ''^[0-9]+$'' '
    'OR child_rows.`id` <= 0 OR child_rows.`id` >= 9223372036854775807 '
    'OR child_rows.`version` IS NULL '
    'OR CAST(child_rows.`version` AS CHAR) NOT REGEXP ''^[0-9]+$'' '
    'OR child_rows.`version` < 0 OR child_rows.`version` >= 9223372036854775807 '
    'OR child_rows.`world_id` IS NULL '
    'OR CAST(child_rows.`world_id` AS CHAR) NOT REGEXP ''^[0-9]+$'' '
    'OR child_rows.`world_id` <= 0 OR child_rows.`world_id` >= 9223372036854775807 '
    'OR child_rows.`source_identity` IS NULL '
    'OR OCTET_LENGTH(child_rows.`source_identity`) <> 64 '
    'OR NOT REGEXP_LIKE(child_rows.`source_identity`, ''^[0-9a-f]{64}$'', ''c'') '
    'OR child_rows.`terminal_key` IS NULL '
    'OR OCTET_LENGTH(child_rows.`terminal_key`) < 1 '
    'OR OCTET_LENGTH(child_rows.`terminal_key`) > 256 '
    'OR child_rows.`binding_digest` IS NULL '
    'OR OCTET_LENGTH(child_rows.`binding_digest`) <> 64 '
    'OR NOT REGEXP_LIKE(child_rows.`binding_digest`, ''^[0-9a-f]{64}$'', ''c'') '
    'OR child_rows.`key_epoch` IS NULL '
    'OR CAST(child_rows.`key_epoch` AS CHAR) NOT REGEXP ''^[0-9]+$'' '
    'OR child_rows.`key_epoch` <= 0 '
    'OR child_rows.`key_epoch` >= 9223372036854775807 '
    'OR child_rows.`key_identity` IS NULL '
    'OR OCTET_LENGTH(child_rows.`key_identity`) <> 64 '
    'OR NOT REGEXP_LIKE(child_rows.`key_identity`, ''^[0-9a-f]{64}$'', ''c'') '
    'OR child_rows.`authenticated_envelope` IS NULL '
    'OR OCTET_LENGTH(child_rows.`authenticated_envelope`) < 1 '
    'OR OCTET_LENGTH(child_rows.`authenticated_envelope`) > 524288');
SET @h12g_terminal_child_data_safe = CONCAT(
    'NOT EXISTS (SELECT 1 FROM ', @h12g_terminal_child_table,
    ' AS child_rows WHERE ', @h12g_terminal_child_row_violation, ')',
    ' AND NOT EXISTS (SELECT 1 FROM ', @h12g_terminal_child_table,
    ' GROUP BY `id` HAVING COUNT(*) > 1)',
    ' AND NOT EXISTS (SELECT 1 FROM ', @h12g_terminal_child_table,
    ' GROUP BY `binding_digest` HAVING COUNT(*) > 1)',
    ' AND NOT EXISTS (SELECT 1 FROM ', @h12g_terminal_child_table,
    ' AS child_rows LEFT JOIN ', @h12g_terminal_parent_table,
    ' AS parent_rows ON parent_rows.`key_epoch` = child_rows.`key_epoch` '
    'AND parent_rows.`key_identity` = child_rows.`key_identity` '
    'WHERE parent_rows.`id` IS NULL)');

SET @h12g_terminal_parent_create_ddl = CONCAT(
    'CREATE TABLE IF NOT EXISTS ', @h12g_terminal_parent_table, ' (',
    '`id` BIGINT NOT NULL,',
    '`version` BIGINT NOT NULL,',
    '`key_epoch` BIGINT NOT NULL,',
    '`key_identity` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,',
    '`mac_key` LONGBLOB NOT NULL,',
    'PRIMARY KEY (`id`),',
    'CONSTRAINT `uk_h12g_terminal_key_epoch_identity` UNIQUE (`key_epoch`, `key_identity`),',
    'CONSTRAINT `ck_h12g_terminal_key_singleton` CHECK (',
    'id = 1 AND version >= 0 AND version < 9223372036854775807 ',
    'AND key_epoch > 0 AND key_epoch < 9223372036854775807 ',
    'AND OCTET_LENGTH(key_identity) = 64 ',
    'AND REGEXP_LIKE(key_identity, ''^[0-9a-f]{64}$'', ''c'') ',
    'AND OCTET_LENGTH(mac_key) = 32)',
    ') ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci');
PREPARE h12g_terminal_create_keys FROM @h12g_terminal_parent_create_ddl;
EXECUTE h12g_terminal_create_keys;
DEALLOCATE PREPARE h12g_terminal_create_keys;

SELECT CASE WHEN
    (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name = 'final_scene_h12g_terminal_keys'
       AND table_type = 'BASE TABLE') = 1
    AND (SELECT COALESCE(create_options, '') FROM information_schema.tables
         WHERE table_schema = DATABASE()
           AND table_name = 'final_scene_h12g_terminal_keys') = ''
THEN 1 ELSE REGEXP_LIKE('x', '[') END;
SELECT CASE WHEN
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'final_scene_h12g_terminal_keys') = 5
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'final_scene_h12g_terminal_keys'
           AND column_name NOT IN ('id', 'version', 'key_epoch', 'key_identity', 'mac_key')) = 0
THEN 1 ELSE REGEXP_LIKE('x', '[') END;
SELECT CASE WHEN NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'final_scene_h12g_terminal_keys'
      AND index_name NOT IN ('PRIMARY', 'uk_h12g_terminal_key_epoch_identity'))
THEN 1 ELSE REGEXP_LIKE('x', '[') END;
SELECT CASE WHEN NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'final_scene_h12g_terminal_keys'
      AND (
          constraint_name NOT IN (
              'PRIMARY', 'uk_h12g_terminal_key_epoch_identity',
              'ck_h12g_terminal_key_singleton')
          OR (constraint_name = 'PRIMARY' AND constraint_type <> 'PRIMARY KEY')
          OR (constraint_name = 'uk_h12g_terminal_key_epoch_identity'
              AND constraint_type <> 'UNIQUE')
          OR (constraint_name = 'ck_h12g_terminal_key_singleton'
              AND constraint_type <> 'CHECK')
      ))
THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @h12g_terminal_parent_preflight_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'final_scene_h12g_terminal_keys'
       AND column_name IN ('id', 'version', 'key_epoch', 'key_identity', 'mac_key')) = 5,
    CONCAT('SELECT CASE WHEN ', @h12g_terminal_parent_data_safe,
           ' THEN 1 ELSE ', @h12g_terminal_assertion_failure, ' END'),
    CONCAT('SELECT CASE WHEN NOT EXISTS (SELECT 1 FROM ', @h12g_terminal_parent_table,
           ') THEN 1 ELSE ', @h12g_terminal_assertion_failure, ' END'));

PREPARE h12g_terminal_parent_id_preflight FROM @h12g_terminal_parent_preflight_sql;
EXECUTE h12g_terminal_parent_id_preflight;
DEALLOCATE PREPARE h12g_terminal_parent_id_preflight;
SET @h12g_terminal_parent_id_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminal_keys'
              AND column_name = 'id' AND data_type = 'bigint' AND column_type = 'bigint'
              AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND ordinal_position = 1 AND character_set_name IS NULL
              AND collation_name IS NULL AND numeric_precision = 19 AND numeric_scale = 0),
    'SELECT 1',
    IF(EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminal_keys'
                 AND column_name = 'id'),
       CONCAT('ALTER TABLE ', @h12g_terminal_parent_table,
              ' MODIFY COLUMN `id` BIGINT NOT NULL FIRST'),
       CONCAT('ALTER TABLE ', @h12g_terminal_parent_table,
              ' ADD COLUMN `id` BIGINT NOT NULL FIRST')));
PREPARE h12g_terminal_parent_id_ddl_statement FROM @h12g_terminal_parent_id_ddl;
EXECUTE h12g_terminal_parent_id_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_parent_id_ddl_statement;

PREPARE h12g_terminal_parent_version_preflight FROM @h12g_terminal_parent_preflight_sql;
EXECUTE h12g_terminal_parent_version_preflight;
DEALLOCATE PREPARE h12g_terminal_parent_version_preflight;
SET @h12g_terminal_parent_version_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminal_keys'
              AND column_name = 'version' AND data_type = 'bigint' AND column_type = 'bigint'
              AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND ordinal_position = 2 AND character_set_name IS NULL
              AND collation_name IS NULL AND numeric_precision = 19 AND numeric_scale = 0),
    'SELECT 1',
    IF(EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminal_keys'
                 AND column_name = 'version'),
       CONCAT('ALTER TABLE ', @h12g_terminal_parent_table,
              ' MODIFY COLUMN `version` BIGINT NOT NULL AFTER `id`'),
       CONCAT('ALTER TABLE ', @h12g_terminal_parent_table,
              ' ADD COLUMN `version` BIGINT NOT NULL AFTER `id`')));
PREPARE h12g_terminal_parent_version_ddl_statement FROM @h12g_terminal_parent_version_ddl;
EXECUTE h12g_terminal_parent_version_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_parent_version_ddl_statement;

PREPARE h12g_terminal_parent_epoch_preflight FROM @h12g_terminal_parent_preflight_sql;
EXECUTE h12g_terminal_parent_epoch_preflight;
DEALLOCATE PREPARE h12g_terminal_parent_epoch_preflight;
SET @h12g_terminal_parent_epoch_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminal_keys'
              AND column_name = 'key_epoch' AND data_type = 'bigint' AND column_type = 'bigint'
              AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND ordinal_position = 3 AND character_set_name IS NULL
              AND collation_name IS NULL AND numeric_precision = 19 AND numeric_scale = 0),
    'SELECT 1',
    IF(EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminal_keys'
                 AND column_name = 'key_epoch'),
       CONCAT('ALTER TABLE ', @h12g_terminal_parent_table,
              ' MODIFY COLUMN `key_epoch` BIGINT NOT NULL AFTER `version`'),
       CONCAT('ALTER TABLE ', @h12g_terminal_parent_table,
              ' ADD COLUMN `key_epoch` BIGINT NOT NULL AFTER `version`')));
PREPARE h12g_terminal_parent_epoch_ddl_statement FROM @h12g_terminal_parent_epoch_ddl;
EXECUTE h12g_terminal_parent_epoch_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_parent_epoch_ddl_statement;

PREPARE h12g_terminal_parent_identity_preflight FROM @h12g_terminal_parent_preflight_sql;
EXECUTE h12g_terminal_parent_identity_preflight;
DEALLOCATE PREPARE h12g_terminal_parent_identity_preflight;
SET @h12g_terminal_parent_identity_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminal_keys'
              AND column_name = 'key_identity' AND data_type = 'varchar'
              AND column_type = 'varchar(64)' AND character_maximum_length = 64
              AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci'
              AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND ordinal_position = 4),
    'SELECT 1',
    IF(EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminal_keys'
                 AND column_name = 'key_identity'),
       CONCAT('ALTER TABLE ', @h12g_terminal_parent_table,
              ' MODIFY COLUMN `key_identity` VARCHAR(64) CHARACTER SET utf8mb4 '
              'COLLATE utf8mb4_unicode_ci NOT NULL AFTER `key_epoch`'),
       CONCAT('ALTER TABLE ', @h12g_terminal_parent_table,
              ' ADD COLUMN `key_identity` VARCHAR(64) CHARACTER SET utf8mb4 '
              'COLLATE utf8mb4_unicode_ci NOT NULL AFTER `key_epoch`')));
PREPARE h12g_terminal_parent_identity_ddl_statement FROM @h12g_terminal_parent_identity_ddl;
EXECUTE h12g_terminal_parent_identity_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_parent_identity_ddl_statement;

PREPARE h12g_terminal_parent_mac_preflight FROM @h12g_terminal_parent_preflight_sql;
EXECUTE h12g_terminal_parent_mac_preflight;
DEALLOCATE PREPARE h12g_terminal_parent_mac_preflight;
SET @h12g_terminal_parent_mac_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminal_keys'
              AND column_name = 'mac_key' AND data_type = 'longblob'
              AND column_type = 'longblob' AND is_nullable = 'NO'
              AND column_default IS NULL AND extra = '' AND COALESCE(generation_expression, '') = '' AND ordinal_position = 5
              AND character_set_name IS NULL AND collation_name IS NULL),
    'SELECT 1',
    IF(EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminal_keys'
                 AND column_name = 'mac_key'),
       CONCAT('ALTER TABLE ', @h12g_terminal_parent_table,
              ' MODIFY COLUMN `mac_key` LONGBLOB NOT NULL AFTER `key_identity`'),
       CONCAT('ALTER TABLE ', @h12g_terminal_parent_table,
              ' ADD COLUMN `mac_key` LONGBLOB NOT NULL AFTER `key_identity`')));
PREPARE h12g_terminal_parent_mac_ddl_statement FROM @h12g_terminal_parent_mac_ddl;
EXECUTE h12g_terminal_parent_mac_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_parent_mac_ddl_statement;

SET @h12g_terminal_parent_shape_exact = (
    SELECT CASE WHEN
        EXISTS (SELECT 1 FROM information_schema.tables AS checked_tables
                JOIN information_schema.collations AS checked_collations
                  ON checked_collations.collation_name = checked_tables.table_collation
                WHERE checked_tables.table_schema = DATABASE()
                  AND checked_tables.table_name = 'final_scene_h12g_terminal_keys'
                  AND checked_tables.table_type = 'BASE TABLE'
                  AND checked_tables.engine = 'InnoDB'
                  AND checked_tables.table_collation = 'utf8mb4_unicode_ci'
                  AND checked_collations.character_set_name = 'utf8mb4'
                  AND COALESCE(checked_tables.create_options, '') = '')
        AND (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'final_scene_h12g_terminal_keys') = 5
        AND (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'final_scene_h12g_terminal_keys'
               AND (
                   (column_name = 'id' AND data_type = 'bigint' AND column_type = 'bigint'
                    AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
                    AND COALESCE(generation_expression, '') = ''
                    AND ordinal_position = 1 AND character_set_name IS NULL
                    AND collation_name IS NULL AND numeric_precision = 19 AND numeric_scale = 0)
                   OR (column_name = 'version' AND data_type = 'bigint' AND column_type = 'bigint'
                    AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
                    AND COALESCE(generation_expression, '') = ''
                    AND ordinal_position = 2 AND character_set_name IS NULL
                    AND collation_name IS NULL AND numeric_precision = 19 AND numeric_scale = 0)
                   OR (column_name = 'key_epoch' AND data_type = 'bigint' AND column_type = 'bigint'
                    AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
                    AND COALESCE(generation_expression, '') = ''
                    AND ordinal_position = 3 AND character_set_name IS NULL
                    AND collation_name IS NULL AND numeric_precision = 19 AND numeric_scale = 0)
                   OR (column_name = 'key_identity' AND data_type = 'varchar'
                    AND column_type = 'varchar(64)' AND character_maximum_length = 64
                    AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci'
                    AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
                    AND COALESCE(generation_expression, '') = ''
                    AND ordinal_position = 4)
                   OR (column_name = 'mac_key' AND data_type = 'longblob'
                    AND column_type = 'longblob' AND is_nullable = 'NO'
                    AND column_default IS NULL AND extra = '' AND COALESCE(generation_expression, '') = '' AND ordinal_position = 5
                    AND character_set_name IS NULL AND collation_name IS NULL)
               )) = 5
    THEN 1 ELSE 0 END);
PREPARE h12g_terminal_parent_shape_preflight FROM @h12g_terminal_parent_preflight_sql;
EXECUTE h12g_terminal_parent_shape_preflight;
DEALLOCATE PREPARE h12g_terminal_parent_shape_preflight;
SET @h12g_terminal_parent_shape_ddl = IF(
    @h12g_terminal_parent_shape_exact = 1,
    'SELECT 1',
    CONCAT('ALTER TABLE ', @h12g_terminal_parent_table,
           ' ENGINE=InnoDB, DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci, '
           'MODIFY COLUMN `id` BIGINT NOT NULL FIRST, '
           'MODIFY COLUMN `version` BIGINT NOT NULL AFTER `id`, '
           'MODIFY COLUMN `key_epoch` BIGINT NOT NULL AFTER `version`, '
           'MODIFY COLUMN `key_identity` VARCHAR(64) CHARACTER SET utf8mb4 '
           'COLLATE utf8mb4_unicode_ci NOT NULL AFTER `key_epoch`, '
           'MODIFY COLUMN `mac_key` LONGBLOB NOT NULL AFTER `key_identity`'));
PREPARE h12g_terminal_parent_shape_ddl_statement FROM @h12g_terminal_parent_shape_ddl;
EXECUTE h12g_terminal_parent_shape_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_parent_shape_ddl_statement;

SET @h12g_terminal_parent_shape_exact = (
    SELECT CASE WHEN
        EXISTS (SELECT 1 FROM information_schema.tables AS checked_tables
                JOIN information_schema.collations AS checked_collations
                  ON checked_collations.collation_name = checked_tables.table_collation
                WHERE checked_tables.table_schema = DATABASE()
                  AND checked_tables.table_name = 'final_scene_h12g_terminal_keys'
                  AND checked_tables.table_type = 'BASE TABLE'
                  AND checked_tables.engine = 'InnoDB'
                  AND checked_tables.table_collation = 'utf8mb4_unicode_ci'
                  AND checked_collations.character_set_name = 'utf8mb4'
                  AND COALESCE(checked_tables.create_options, '') = '')
        AND (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'final_scene_h12g_terminal_keys') = 5
        AND (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'final_scene_h12g_terminal_keys'
               AND ((column_name = 'id' AND data_type = 'bigint' AND column_type = 'bigint'
                     AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
                     AND COALESCE(generation_expression, '') = ''
                     AND ordinal_position = 1 AND character_set_name IS NULL
                     AND collation_name IS NULL AND numeric_precision = 19 AND numeric_scale = 0)
                OR (column_name = 'version' AND data_type = 'bigint' AND column_type = 'bigint'
                     AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
                     AND COALESCE(generation_expression, '') = ''
                     AND ordinal_position = 2 AND character_set_name IS NULL
                     AND collation_name IS NULL AND numeric_precision = 19 AND numeric_scale = 0)
                OR (column_name = 'key_epoch' AND data_type = 'bigint' AND column_type = 'bigint'
                     AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
                     AND COALESCE(generation_expression, '') = ''
                     AND ordinal_position = 3 AND character_set_name IS NULL
                     AND collation_name IS NULL AND numeric_precision = 19 AND numeric_scale = 0)
                OR (column_name = 'key_identity' AND data_type = 'varchar'
                     AND column_type = 'varchar(64)' AND character_maximum_length = 64
                     AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci'
                     AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
                     AND COALESCE(generation_expression, '') = ''
                     AND ordinal_position = 4)
                OR (column_name = 'mac_key' AND data_type = 'longblob'
                     AND column_type = 'longblob' AND is_nullable = 'NO'
                     AND column_default IS NULL AND extra = '' AND COALESCE(generation_expression, '') = '' AND ordinal_position = 5
                     AND character_set_name IS NULL AND collation_name IS NULL))) = 5
    THEN 1 ELSE 0 END);
SELECT CASE WHEN @h12g_terminal_parent_shape_exact = 1
THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @h12g_terminal_parent_primary_exact = (
    SELECT CASE WHEN COUNT(*) = 1 AND SUM(CASE WHEN
        non_unique = 0 AND seq_in_index = 1 AND column_name = 'id'
        AND sub_part IS NULL AND index_type = 'BTREE'
        AND is_visible = 'YES' AND collation = 'A'
        THEN 1 ELSE 0 END) = 1 THEN 1 ELSE 0 END
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'final_scene_h12g_terminal_keys'
      AND index_name = 'PRIMARY');
SET @h12g_terminal_parent_unique_exact = (
    SELECT CASE WHEN COUNT(*) = 2 AND SUM(CASE WHEN
        non_unique = 0 AND index_type = 'BTREE' AND is_visible = 'YES'
        AND sub_part IS NULL AND collation = 'A'
        AND ((seq_in_index = 1 AND column_name = 'key_epoch')
             OR (seq_in_index = 2 AND column_name = 'key_identity'))
        THEN 1 ELSE 0 END) = 2 THEN 1 ELSE 0 END
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'final_scene_h12g_terminal_keys'
      AND index_name = 'uk_h12g_terminal_key_epoch_identity');
SELECT CASE WHEN
    (NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'final_scene_h12g_terminal_keys'
                  AND index_name = 'PRIMARY') OR @h12g_terminal_parent_primary_exact = 1)
    AND (NOT EXISTS (SELECT 1 FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 'final_scene_h12g_terminal_keys'
                      AND index_name = 'uk_h12g_terminal_key_epoch_identity')
         OR @h12g_terminal_parent_unique_exact = 1)
THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @h12g_terminal_parent_check_present = (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'final_scene_h12g_terminal_keys'
      AND constraint_name = 'ck_h12g_terminal_key_singleton'
      AND constraint_type = 'CHECK');
SET @h12g_terminal_parent_check_exact = (
    SELECT CASE WHEN COUNT(*) = 1 AND SUM(CASE WHEN
        tc.enforced = 'YES'
        AND REGEXP_REPLACE(
            LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(cc.check_clause, CHAR(92), ''), '`', ''), '(', ''), ')', ''), ' ', ''), '_latin1', ''), '_utf8mb4', '')),
            '[[:space:]]+', '') = @h12g_terminal_parent_check_semantics
        THEN 1 ELSE 0 END) = 1 THEN 1 ELSE 0 END
    FROM information_schema.table_constraints AS tc
    JOIN information_schema.check_constraints AS cc
      ON cc.constraint_schema = tc.constraint_schema
     AND cc.constraint_name = tc.constraint_name
    WHERE tc.constraint_schema = DATABASE()
      AND tc.table_name = 'final_scene_h12g_terminal_keys'
      AND tc.constraint_name = 'ck_h12g_terminal_key_singleton'
      AND tc.constraint_type = 'CHECK');
SELECT CASE WHEN @h12g_terminal_parent_check_present = 0
                 OR @h12g_terminal_parent_check_exact = 1
THEN 1 ELSE REGEXP_LIKE('x', '[') END;

PREPARE h12g_terminal_parent_primary_preflight FROM @h12g_terminal_parent_preflight_sql;
EXECUTE h12g_terminal_parent_primary_preflight;
DEALLOCATE PREPARE h12g_terminal_parent_primary_preflight;
SET @h12g_terminal_parent_primary_ddl = IF(
    @h12g_terminal_parent_primary_exact = 1,
    'SELECT 1',
    CONCAT('ALTER TABLE ', @h12g_terminal_parent_table,
           ' ADD PRIMARY KEY (`id`)'));
PREPARE h12g_terminal_parent_primary_ddl_statement FROM @h12g_terminal_parent_primary_ddl;
EXECUTE h12g_terminal_parent_primary_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_parent_primary_ddl_statement;

PREPARE h12g_terminal_parent_unique_preflight FROM @h12g_terminal_parent_preflight_sql;
EXECUTE h12g_terminal_parent_unique_preflight;
DEALLOCATE PREPARE h12g_terminal_parent_unique_preflight;
SET @h12g_terminal_parent_unique_ddl = IF(
    @h12g_terminal_parent_unique_exact = 1,
    'SELECT 1',
    CONCAT('ALTER TABLE ', @h12g_terminal_parent_table,
           ' ADD CONSTRAINT `uk_h12g_terminal_key_epoch_identity` '
           'UNIQUE (`key_epoch`, `key_identity`)'));
PREPARE h12g_terminal_parent_unique_ddl_statement FROM @h12g_terminal_parent_unique_ddl;
EXECUTE h12g_terminal_parent_unique_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_parent_unique_ddl_statement;

PREPARE h12g_terminal_parent_check_preflight FROM @h12g_terminal_parent_preflight_sql;
EXECUTE h12g_terminal_parent_check_preflight;
DEALLOCATE PREPARE h12g_terminal_parent_check_preflight;
SET @h12g_terminal_parent_check_ddl = IF(
    @h12g_terminal_parent_check_exact = 1,
    'SELECT 1',
    CONCAT('ALTER TABLE ', @h12g_terminal_parent_table,
           ' ADD CONSTRAINT `ck_h12g_terminal_key_singleton` CHECK (',
           'id = 1 AND version >= 0 AND version < 9223372036854775807 ',
           'AND key_epoch > 0 AND key_epoch < 9223372036854775807 ',
           'AND OCTET_LENGTH(key_identity) = 64 ',
           'AND REGEXP_LIKE(key_identity, ''^[0-9a-f]{64}$'', ''c'') ',
           'AND OCTET_LENGTH(mac_key) = 32)'));
PREPARE h12g_terminal_parent_check_ddl_statement FROM @h12g_terminal_parent_check_ddl;
EXECUTE h12g_terminal_parent_check_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_parent_check_ddl_statement;

SET @h12g_terminal_parent_primary_exact = (
    SELECT CASE WHEN COUNT(*) = 1 AND SUM(CASE WHEN
        non_unique = 0 AND seq_in_index = 1 AND column_name = 'id'
        AND sub_part IS NULL AND index_type = 'BTREE'
        AND is_visible = 'YES' AND collation = 'A'
        THEN 1 ELSE 0 END) = 1 THEN 1 ELSE 0 END
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'final_scene_h12g_terminal_keys'
      AND index_name = 'PRIMARY');
SET @h12g_terminal_parent_unique_exact = (
    SELECT CASE WHEN COUNT(*) = 2 AND SUM(CASE WHEN
        non_unique = 0 AND index_type = 'BTREE' AND is_visible = 'YES'
        AND sub_part IS NULL AND collation = 'A'
        AND ((seq_in_index = 1 AND column_name = 'key_epoch')
             OR (seq_in_index = 2 AND column_name = 'key_identity'))
        THEN 1 ELSE 0 END) = 2 THEN 1 ELSE 0 END
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'final_scene_h12g_terminal_keys'
      AND index_name = 'uk_h12g_terminal_key_epoch_identity');
SET @h12g_terminal_parent_check_exact = (
    SELECT CASE WHEN COUNT(*) = 1 AND SUM(CASE WHEN
        tc.enforced = 'YES'
        AND REGEXP_REPLACE(
            LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(cc.check_clause, CHAR(92), ''), '`', ''), '(', ''), ')', ''), ' ', ''), '_latin1', ''), '_utf8mb4', '')),
            '[[:space:]]+', '') = @h12g_terminal_parent_check_semantics
        THEN 1 ELSE 0 END) = 1 THEN 1 ELSE 0 END
    FROM information_schema.table_constraints AS tc
    JOIN information_schema.check_constraints AS cc
      ON cc.constraint_schema = tc.constraint_schema
     AND cc.constraint_name = tc.constraint_name
    WHERE tc.constraint_schema = DATABASE()
      AND tc.table_name = 'final_scene_h12g_terminal_keys'
      AND tc.constraint_name = 'ck_h12g_terminal_key_singleton'
      AND tc.constraint_type = 'CHECK');
SET @h12g_terminal_parent_constraints_exact = (
    SELECT CASE WHEN COUNT(*) = 3 AND SUM(CASE WHEN
        (constraint_name = 'PRIMARY' AND constraint_type = 'PRIMARY KEY')
        OR (constraint_name = 'uk_h12g_terminal_key_epoch_identity'
            AND constraint_type = 'UNIQUE')
        OR (constraint_name = 'ck_h12g_terminal_key_singleton'
            AND constraint_type = 'CHECK' AND enforced = 'YES')
        THEN 1 ELSE 0 END) = 3 THEN 1 ELSE 0 END
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'final_scene_h12g_terminal_keys');
SELECT CASE WHEN @h12g_terminal_parent_shape_exact = 1
                 AND @h12g_terminal_parent_primary_exact = 1
                 AND @h12g_terminal_parent_unique_exact = 1
                 AND @h12g_terminal_parent_check_exact = 1
                 AND @h12g_terminal_parent_constraints_exact = 1
THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @h12g_terminal_child_create_ddl = CONCAT(
    'CREATE TABLE IF NOT EXISTS ', @h12g_terminal_child_table, ' (',
    '`id` BIGINT NOT NULL AUTO_INCREMENT,',
    '`version` BIGINT NOT NULL,',
    '`world_id` BIGINT NOT NULL,',
    '`source_identity` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,',
    '`terminal_key` VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,',
    '`binding_digest` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,',
    '`key_epoch` BIGINT NOT NULL,',
    '`key_identity` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,',
    '`authenticated_envelope` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,',
    'PRIMARY KEY (`id`),',
    'CONSTRAINT `uk_h12g_terminal_binding_digest` UNIQUE (`binding_digest`),',
    'KEY `idx_h12g_terminal_world` (`world_id`),',
    'KEY `idx_h12g_terminal_key_epoch_identity` (`key_epoch`, `key_identity`),',
    'CONSTRAINT `ck_h12g_terminal_current_state` CHECK (',
    'version >= 0 AND version < 9223372036854775807 ',
    'AND world_id > 0 AND world_id < 9223372036854775807 ',
    'AND OCTET_LENGTH(source_identity) = 64 ',
    'AND REGEXP_LIKE(source_identity, ''^[0-9a-f]{64}$'', ''c'') ',
    'AND OCTET_LENGTH(terminal_key) BETWEEN 1 AND 256 ',
    'AND OCTET_LENGTH(binding_digest) = 64 ',
    'AND REGEXP_LIKE(binding_digest, ''^[0-9a-f]{64}$'', ''c'') ',
    'AND key_epoch > 0 AND key_epoch < 9223372036854775807 ',
    'AND OCTET_LENGTH(key_identity) = 64 ',
    'AND REGEXP_LIKE(key_identity, ''^[0-9a-f]{64}$'', ''c'') ',
    'AND OCTET_LENGTH(authenticated_envelope) BETWEEN 1 AND 524288)',
    ',CONSTRAINT `fk_h12g_terminal_key` FOREIGN KEY (`key_epoch`, `key_identity`) ',
    'REFERENCES ', @h12g_terminal_parent_table,
    ' (`key_epoch`, `key_identity`) ON UPDATE RESTRICT ON DELETE RESTRICT',
    ') ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci');
PREPARE h12g_terminal_create_terminals FROM @h12g_terminal_child_create_ddl;
EXECUTE h12g_terminal_create_terminals;
DEALLOCATE PREPARE h12g_terminal_create_terminals;

SELECT CASE WHEN
    (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name = 'final_scene_h12g_terminals'
       AND table_type = 'BASE TABLE') = 1
    AND (SELECT COALESCE(create_options, '') FROM information_schema.tables
         WHERE table_schema = DATABASE()
           AND table_name = 'final_scene_h12g_terminals') = ''
THEN 1 ELSE REGEXP_LIKE('x', '[') END;
SELECT CASE WHEN
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'final_scene_h12g_terminals') = 9
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'final_scene_h12g_terminals'
           AND column_name NOT IN (
               'id', 'version', 'world_id', 'source_identity', 'terminal_key',
               'binding_digest', 'key_epoch', 'key_identity', 'authenticated_envelope')) = 0
THEN 1 ELSE REGEXP_LIKE('x', '[') END;
SELECT CASE WHEN NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'final_scene_h12g_terminals'
      AND index_name NOT IN (
          'PRIMARY', 'uk_h12g_terminal_binding_digest',
          'idx_h12g_terminal_world', 'idx_h12g_terminal_key_epoch_identity'))
THEN 1 ELSE REGEXP_LIKE('x', '[') END;
SELECT CASE WHEN NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'final_scene_h12g_terminals'
      AND (
          constraint_name NOT IN (
              'PRIMARY', 'uk_h12g_terminal_binding_digest',
              'ck_h12g_terminal_current_state', 'fk_h12g_terminal_key')
          OR (constraint_name = 'PRIMARY' AND constraint_type <> 'PRIMARY KEY')
          OR (constraint_name = 'uk_h12g_terminal_binding_digest'
              AND constraint_type <> 'UNIQUE')
          OR (constraint_name = 'ck_h12g_terminal_current_state'
              AND constraint_type <> 'CHECK')
          OR (constraint_name = 'fk_h12g_terminal_key'
              AND constraint_type <> 'FOREIGN KEY')
      ))
THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @h12g_terminal_child_fk_present = (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'final_scene_h12g_terminals'
      AND constraint_name = 'fk_h12g_terminal_key'
      AND constraint_type = 'FOREIGN KEY');
SET @h12g_terminal_child_fk_key_columns_exact = (
    SELECT CASE WHEN COUNT(*) = 2 AND SUM(CASE WHEN
        table_schema = DATABASE() AND referenced_table_schema = DATABASE()
        AND referenced_table_name = 'final_scene_h12g_terminal_keys'
        AND ((ordinal_position = 1 AND column_name = 'key_epoch'
              AND referenced_column_name = 'key_epoch'
              AND position_in_unique_constraint = 1)
             OR (ordinal_position = 2 AND column_name = 'key_identity'
                 AND referenced_column_name = 'key_identity'
                 AND position_in_unique_constraint = 2))
        THEN 1 ELSE 0 END) = 2 THEN 1 ELSE 0 END
    FROM information_schema.key_column_usage
    WHERE constraint_schema = DATABASE()
      AND table_schema = DATABASE()
      AND table_name = 'final_scene_h12g_terminals'
      AND constraint_name = 'fk_h12g_terminal_key');
SET @h12g_terminal_child_fk_actions_exact = (
    SELECT CASE WHEN COUNT(*) = 1 AND SUM(CASE WHEN
        unique_constraint_schema = DATABASE()
        AND unique_constraint_name = 'uk_h12g_terminal_key_epoch_identity'
        AND referenced_table_name = 'final_scene_h12g_terminal_keys'
        AND match_option = 'NONE'
        AND update_rule = 'RESTRICT' AND delete_rule = 'RESTRICT'
        THEN 1 ELSE 0 END) = 1 THEN 1 ELSE 0 END
    FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'final_scene_h12g_terminals'
      AND constraint_name = 'fk_h12g_terminal_key');
SET @h12g_terminal_child_fk_exact = IF(
    @h12g_terminal_child_fk_key_columns_exact = 1
    AND @h12g_terminal_child_fk_actions_exact = 1, 1, 0);
SELECT CASE WHEN @h12g_terminal_child_fk_present = 0
                 OR @h12g_terminal_child_fk_exact = 1
THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @h12g_terminal_child_check_present = (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'final_scene_h12g_terminals'
      AND constraint_name = 'ck_h12g_terminal_current_state'
      AND constraint_type = 'CHECK');
SET @h12g_terminal_child_check_exact = (
    SELECT CASE WHEN COUNT(*) = 1 AND SUM(CASE WHEN
        tc.enforced = 'YES'
        AND REGEXP_REPLACE(
            LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(cc.check_clause, CHAR(92), ''), '`', ''), '(', ''), ')', ''), ' ', ''), '_latin1', ''), '_utf8mb4', '')),
            '[[:space:]]+', '') = @h12g_terminal_child_check_semantics
        THEN 1 ELSE 0 END) = 1 THEN 1 ELSE 0 END
    FROM information_schema.table_constraints AS tc
    JOIN information_schema.check_constraints AS cc
      ON cc.constraint_schema = tc.constraint_schema
     AND cc.constraint_name = tc.constraint_name
    WHERE tc.constraint_schema = DATABASE()
      AND tc.table_name = 'final_scene_h12g_terminals'
      AND tc.constraint_name = 'ck_h12g_terminal_current_state'
      AND tc.constraint_type = 'CHECK');
SELECT CASE WHEN @h12g_terminal_child_check_present = 0
                 OR @h12g_terminal_child_check_exact = 1
THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @h12g_terminal_child_preflight_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'final_scene_h12g_terminals'
       AND column_name IN (
           'id', 'version', 'world_id', 'source_identity', 'terminal_key',
           'binding_digest', 'key_epoch', 'key_identity', 'authenticated_envelope')) = 9,
    CONCAT('SELECT CASE WHEN ', @h12g_terminal_child_data_safe,
           ' THEN 1 ELSE ', @h12g_terminal_assertion_failure, ' END'),
    CONCAT('SELECT CASE WHEN NOT EXISTS (SELECT 1 FROM ', @h12g_terminal_child_table,
           ') THEN 1 ELSE ', @h12g_terminal_assertion_failure, ' END'));

PREPARE h12g_terminal_child_id_preflight FROM @h12g_terminal_child_preflight_sql;
EXECUTE h12g_terminal_child_id_preflight;
DEALLOCATE PREPARE h12g_terminal_child_id_preflight;
SET @h12g_terminal_child_id_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
              AND column_name = 'id' AND data_type = 'bigint' AND column_type = 'bigint'
              AND is_nullable = 'NO' AND column_default IS NULL AND extra = 'auto_increment'
              AND COALESCE(generation_expression, '') = ''
              AND ordinal_position = 1 AND character_set_name IS NULL
              AND collation_name IS NULL AND numeric_precision = 19 AND numeric_scale = 0),
    'SELECT 1',
    IF(EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
                 AND column_name = 'id'),
       CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
              ' MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT FIRST'),
       CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
              ' ADD COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT FIRST')));
PREPARE h12g_terminal_child_id_ddl_statement FROM @h12g_terminal_child_id_ddl;
EXECUTE h12g_terminal_child_id_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_child_id_ddl_statement;

PREPARE h12g_terminal_child_version_preflight FROM @h12g_terminal_child_preflight_sql;
EXECUTE h12g_terminal_child_version_preflight;
DEALLOCATE PREPARE h12g_terminal_child_version_preflight;
SET @h12g_terminal_child_version_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
              AND column_name = 'version' AND data_type = 'bigint' AND column_type = 'bigint'
              AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND ordinal_position = 2 AND character_set_name IS NULL
              AND collation_name IS NULL AND numeric_precision = 19 AND numeric_scale = 0),
    'SELECT 1',
    IF(EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
                 AND column_name = 'version'),
       CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
              ' MODIFY COLUMN `version` BIGINT NOT NULL AFTER `id`'),
       CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
              ' ADD COLUMN `version` BIGINT NOT NULL AFTER `id`')));
PREPARE h12g_terminal_child_version_ddl_statement FROM @h12g_terminal_child_version_ddl;
EXECUTE h12g_terminal_child_version_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_child_version_ddl_statement;

PREPARE h12g_terminal_child_world_preflight FROM @h12g_terminal_child_preflight_sql;
EXECUTE h12g_terminal_child_world_preflight;
DEALLOCATE PREPARE h12g_terminal_child_world_preflight;
SET @h12g_terminal_child_world_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
              AND column_name = 'world_id' AND data_type = 'bigint' AND column_type = 'bigint'
              AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND ordinal_position = 3 AND character_set_name IS NULL
              AND collation_name IS NULL AND numeric_precision = 19 AND numeric_scale = 0),
    'SELECT 1',
    IF(EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
                 AND column_name = 'world_id'),
       CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
              ' MODIFY COLUMN `world_id` BIGINT NOT NULL AFTER `version`'),
       CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
              ' ADD COLUMN `world_id` BIGINT NOT NULL AFTER `version`')));
PREPARE h12g_terminal_child_world_ddl_statement FROM @h12g_terminal_child_world_ddl;
EXECUTE h12g_terminal_child_world_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_child_world_ddl_statement;

PREPARE h12g_terminal_child_source_preflight FROM @h12g_terminal_child_preflight_sql;
EXECUTE h12g_terminal_child_source_preflight;
DEALLOCATE PREPARE h12g_terminal_child_source_preflight;
SET @h12g_terminal_child_source_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
              AND column_name = 'source_identity' AND data_type = 'varchar'
              AND column_type = 'varchar(64)' AND character_maximum_length = 64
              AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci'
              AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND ordinal_position = 4),
    'SELECT 1',
    IF(EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
                 AND column_name = 'source_identity'),
       CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
              ' MODIFY COLUMN `source_identity` VARCHAR(64) CHARACTER SET utf8mb4 '
              'COLLATE utf8mb4_unicode_ci NOT NULL AFTER `world_id`'),
       CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
              ' ADD COLUMN `source_identity` VARCHAR(64) CHARACTER SET utf8mb4 '
              'COLLATE utf8mb4_unicode_ci NOT NULL AFTER `world_id`')));
PREPARE h12g_terminal_child_source_ddl_statement FROM @h12g_terminal_child_source_ddl;
EXECUTE h12g_terminal_child_source_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_child_source_ddl_statement;

PREPARE h12g_terminal_child_terminal_key_preflight FROM @h12g_terminal_child_preflight_sql;
EXECUTE h12g_terminal_child_terminal_key_preflight;
DEALLOCATE PREPARE h12g_terminal_child_terminal_key_preflight;
SET @h12g_terminal_child_terminal_key_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
              AND column_name = 'terminal_key' AND data_type = 'varchar'
              AND column_type = 'varchar(256)' AND character_maximum_length = 256
              AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci'
              AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND ordinal_position = 5),
    'SELECT 1',
    IF(EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
                 AND column_name = 'terminal_key'),
       CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
              ' MODIFY COLUMN `terminal_key` VARCHAR(256) CHARACTER SET utf8mb4 '
              'COLLATE utf8mb4_unicode_ci NOT NULL AFTER `source_identity`'),
       CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
              ' ADD COLUMN `terminal_key` VARCHAR(256) CHARACTER SET utf8mb4 '
              'COLLATE utf8mb4_unicode_ci NOT NULL AFTER `source_identity`')));
PREPARE h12g_terminal_child_terminal_key_ddl_statement FROM @h12g_terminal_child_terminal_key_ddl;
EXECUTE h12g_terminal_child_terminal_key_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_child_terminal_key_ddl_statement;

PREPARE h12g_terminal_child_binding_preflight FROM @h12g_terminal_child_preflight_sql;
EXECUTE h12g_terminal_child_binding_preflight;
DEALLOCATE PREPARE h12g_terminal_child_binding_preflight;
SET @h12g_terminal_child_binding_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
              AND column_name = 'binding_digest' AND data_type = 'varchar'
              AND column_type = 'varchar(64)' AND character_maximum_length = 64
              AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci'
              AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND ordinal_position = 6),
    'SELECT 1',
    IF(EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
                 AND column_name = 'binding_digest'),
       CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
              ' MODIFY COLUMN `binding_digest` VARCHAR(64) CHARACTER SET utf8mb4 '
              'COLLATE utf8mb4_unicode_ci NOT NULL AFTER `terminal_key`'),
       CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
              ' ADD COLUMN `binding_digest` VARCHAR(64) CHARACTER SET utf8mb4 '
              'COLLATE utf8mb4_unicode_ci NOT NULL AFTER `terminal_key`')));
PREPARE h12g_terminal_child_binding_ddl_statement FROM @h12g_terminal_child_binding_ddl;
EXECUTE h12g_terminal_child_binding_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_child_binding_ddl_statement;

PREPARE h12g_terminal_child_epoch_preflight FROM @h12g_terminal_child_preflight_sql;
EXECUTE h12g_terminal_child_epoch_preflight;
DEALLOCATE PREPARE h12g_terminal_child_epoch_preflight;
SET @h12g_terminal_child_epoch_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
              AND column_name = 'key_epoch' AND data_type = 'bigint' AND column_type = 'bigint'
              AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND ordinal_position = 7 AND character_set_name IS NULL
              AND collation_name IS NULL AND numeric_precision = 19 AND numeric_scale = 0),
    'SELECT 1',
    IF(EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
                 AND column_name = 'key_epoch'),
       CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
              ' MODIFY COLUMN `key_epoch` BIGINT NOT NULL AFTER `binding_digest`'),
       CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
              ' ADD COLUMN `key_epoch` BIGINT NOT NULL AFTER `binding_digest`')));
PREPARE h12g_terminal_child_epoch_ddl_statement FROM @h12g_terminal_child_epoch_ddl;
EXECUTE h12g_terminal_child_epoch_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_child_epoch_ddl_statement;

PREPARE h12g_terminal_child_identity_preflight FROM @h12g_terminal_child_preflight_sql;
EXECUTE h12g_terminal_child_identity_preflight;
DEALLOCATE PREPARE h12g_terminal_child_identity_preflight;
SET @h12g_terminal_child_identity_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
              AND column_name = 'key_identity' AND data_type = 'varchar'
              AND column_type = 'varchar(64)' AND character_maximum_length = 64
              AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci'
              AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND ordinal_position = 8),
    'SELECT 1',
    IF(EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
                 AND column_name = 'key_identity'),
       CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
              ' MODIFY COLUMN `key_identity` VARCHAR(64) CHARACTER SET utf8mb4 '
              'COLLATE utf8mb4_unicode_ci NOT NULL AFTER `key_epoch`'),
       CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
              ' ADD COLUMN `key_identity` VARCHAR(64) CHARACTER SET utf8mb4 '
              'COLLATE utf8mb4_unicode_ci NOT NULL AFTER `key_epoch`')));
PREPARE h12g_terminal_child_identity_ddl_statement FROM @h12g_terminal_child_identity_ddl;
EXECUTE h12g_terminal_child_identity_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_child_identity_ddl_statement;

PREPARE h12g_terminal_child_envelope_preflight FROM @h12g_terminal_child_preflight_sql;
EXECUTE h12g_terminal_child_envelope_preflight;
DEALLOCATE PREPARE h12g_terminal_child_envelope_preflight;
SET @h12g_terminal_child_envelope_ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
              AND column_name = 'authenticated_envelope' AND data_type = 'longtext'
              AND column_type = 'longtext' AND character_set_name = 'utf8mb4'
              AND collation_name = 'utf8mb4_unicode_ci' AND is_nullable = 'NO'
              AND column_default IS NULL AND extra = '' AND COALESCE(generation_expression, '') = '' AND ordinal_position = 9),
    'SELECT 1',
    IF(EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
                 AND column_name = 'authenticated_envelope'),
       CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
              ' MODIFY COLUMN `authenticated_envelope` LONGTEXT CHARACTER SET utf8mb4 '
              'COLLATE utf8mb4_unicode_ci NOT NULL AFTER `key_identity`'),
       CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
              ' ADD COLUMN `authenticated_envelope` LONGTEXT CHARACTER SET utf8mb4 '
              'COLLATE utf8mb4_unicode_ci NOT NULL AFTER `key_identity`')));
PREPARE h12g_terminal_child_envelope_ddl_statement FROM @h12g_terminal_child_envelope_ddl;
EXECUTE h12g_terminal_child_envelope_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_child_envelope_ddl_statement;

SET @h12g_terminal_child_shape_exact = (
    SELECT CASE WHEN
        EXISTS (SELECT 1 FROM information_schema.tables AS checked_tables
                JOIN information_schema.collations AS checked_collations
                  ON checked_collations.collation_name = checked_tables.table_collation
                WHERE checked_tables.table_schema = DATABASE()
                  AND checked_tables.table_name = 'final_scene_h12g_terminals'
                  AND checked_tables.table_type = 'BASE TABLE'
                  AND checked_tables.engine = 'InnoDB'
                  AND checked_tables.table_collation = 'utf8mb4_unicode_ci'
                  AND checked_collations.character_set_name = 'utf8mb4'
                  AND COALESCE(checked_tables.create_options, '') = '')
        AND (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'final_scene_h12g_terminals') = 9
        AND (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'final_scene_h12g_terminals'
               AND (
                   (column_name = 'id' AND data_type = 'bigint' AND column_type = 'bigint'
                    AND is_nullable = 'NO' AND column_default IS NULL AND extra = 'auto_increment'
                    AND COALESCE(generation_expression, '') = ''
                    AND ordinal_position = 1 AND character_set_name IS NULL
                    AND collation_name IS NULL AND numeric_precision = 19 AND numeric_scale = 0)
                   OR (column_name IN ('version', 'world_id', 'key_epoch')
                    AND data_type = 'bigint' AND column_type = 'bigint'
                    AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
                    AND COALESCE(generation_expression, '') = ''
                    AND ordinal_position = CASE column_name
                        WHEN 'version' THEN 2 WHEN 'world_id' THEN 3 ELSE 7 END
                    AND character_set_name IS NULL AND collation_name IS NULL
                    AND numeric_precision = 19 AND numeric_scale = 0)
                   OR (column_name = 'source_identity' AND data_type = 'varchar'
                    AND column_type = 'varchar(64)' AND character_maximum_length = 64
                    AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci'
                    AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
                    AND COALESCE(generation_expression, '') = ''
                    AND ordinal_position = 4)
                   OR (column_name = 'terminal_key' AND data_type = 'varchar'
                    AND column_type = 'varchar(256)' AND character_maximum_length = 256
                    AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci'
                    AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
                    AND COALESCE(generation_expression, '') = ''
                    AND ordinal_position = 5)
                   OR (column_name = 'binding_digest' AND data_type = 'varchar'
                    AND column_type = 'varchar(64)' AND character_maximum_length = 64
                    AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci'
                    AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
                    AND COALESCE(generation_expression, '') = ''
                    AND ordinal_position = 6)
                   OR (column_name = 'key_identity' AND data_type = 'varchar'
                    AND column_type = 'varchar(64)' AND character_maximum_length = 64
                    AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci'
                    AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
                    AND COALESCE(generation_expression, '') = ''
                    AND ordinal_position = 8)
                   OR (column_name = 'authenticated_envelope' AND data_type = 'longtext'
                    AND column_type = 'longtext' AND character_set_name = 'utf8mb4'
                    AND collation_name = 'utf8mb4_unicode_ci' AND is_nullable = 'NO'
                    AND column_default IS NULL AND extra = '' AND COALESCE(generation_expression, '') = '' AND ordinal_position = 9)
               )) = 9
    THEN 1 ELSE 0 END);
PREPARE h12g_terminal_child_shape_preflight FROM @h12g_terminal_child_preflight_sql;
EXECUTE h12g_terminal_child_shape_preflight;
DEALLOCATE PREPARE h12g_terminal_child_shape_preflight;
SET @h12g_terminal_child_shape_ddl = IF(
    @h12g_terminal_child_shape_exact = 1,
    'SELECT 1',
    CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
           ' ENGINE=InnoDB, DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci, '
           'MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT FIRST, '
           'MODIFY COLUMN `version` BIGINT NOT NULL AFTER `id`, '
           'MODIFY COLUMN `world_id` BIGINT NOT NULL AFTER `version`, '
           'MODIFY COLUMN `source_identity` VARCHAR(64) CHARACTER SET utf8mb4 '
           'COLLATE utf8mb4_unicode_ci NOT NULL AFTER `world_id`, '
           'MODIFY COLUMN `terminal_key` VARCHAR(256) CHARACTER SET utf8mb4 '
           'COLLATE utf8mb4_unicode_ci NOT NULL AFTER `source_identity`, '
           'MODIFY COLUMN `binding_digest` VARCHAR(64) CHARACTER SET utf8mb4 '
           'COLLATE utf8mb4_unicode_ci NOT NULL AFTER `terminal_key`, '
           'MODIFY COLUMN `key_epoch` BIGINT NOT NULL AFTER `binding_digest`, '
           'MODIFY COLUMN `key_identity` VARCHAR(64) CHARACTER SET utf8mb4 '
           'COLLATE utf8mb4_unicode_ci NOT NULL AFTER `key_epoch`, '
           'MODIFY COLUMN `authenticated_envelope` LONGTEXT CHARACTER SET utf8mb4 '
           'COLLATE utf8mb4_unicode_ci NOT NULL AFTER `key_identity`'));
PREPARE h12g_terminal_child_shape_ddl_statement FROM @h12g_terminal_child_shape_ddl;
EXECUTE h12g_terminal_child_shape_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_child_shape_ddl_statement;

SET @h12g_terminal_child_shape_exact = (
    SELECT CASE WHEN
        EXISTS (SELECT 1 FROM information_schema.tables AS checked_tables
                JOIN information_schema.collations AS checked_collations
                  ON checked_collations.collation_name = checked_tables.table_collation
                WHERE checked_tables.table_schema = DATABASE()
                  AND checked_tables.table_name = 'final_scene_h12g_terminals'
                  AND checked_tables.table_type = 'BASE TABLE'
                  AND checked_tables.engine = 'InnoDB'
                  AND checked_tables.table_collation = 'utf8mb4_unicode_ci'
                  AND checked_collations.character_set_name = 'utf8mb4'
                  AND COALESCE(checked_tables.create_options, '') = '')
        AND (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'final_scene_h12g_terminals') = 9
        AND (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'final_scene_h12g_terminals'
               AND ((column_name = 'id' AND data_type = 'bigint' AND column_type = 'bigint'
                     AND is_nullable = 'NO' AND column_default IS NULL AND extra = 'auto_increment'
                     AND COALESCE(generation_expression, '') = ''
                     AND ordinal_position = 1 AND character_set_name IS NULL
                     AND collation_name IS NULL AND numeric_precision = 19 AND numeric_scale = 0)
                OR (column_name = 'version' AND data_type = 'bigint' AND column_type = 'bigint'
                     AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
                     AND COALESCE(generation_expression, '') = ''
                     AND ordinal_position = 2 AND character_set_name IS NULL
                     AND collation_name IS NULL AND numeric_precision = 19 AND numeric_scale = 0)
                OR (column_name = 'world_id' AND data_type = 'bigint' AND column_type = 'bigint'
                     AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
                     AND COALESCE(generation_expression, '') = ''
                     AND ordinal_position = 3 AND character_set_name IS NULL
                     AND collation_name IS NULL AND numeric_precision = 19 AND numeric_scale = 0)
                OR (column_name IN ('source_identity', 'binding_digest', 'key_identity')
                     AND data_type = 'varchar' AND column_type = 'varchar(64)'
                     AND character_maximum_length = 64 AND character_set_name = 'utf8mb4'
                     AND collation_name = 'utf8mb4_unicode_ci' AND is_nullable = 'NO'
                     AND column_default IS NULL AND extra = '' AND COALESCE(generation_expression, '') = ''
                     AND ordinal_position = CASE column_name
                         WHEN 'source_identity' THEN 4
                         WHEN 'binding_digest' THEN 6
                         ELSE 8 END)
                OR (column_name = 'terminal_key' AND data_type = 'varchar'
                     AND column_type = 'varchar(256)' AND character_maximum_length = 256
                     AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci'
                     AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
                     AND COALESCE(generation_expression, '') = ''
                     AND ordinal_position = 5)
                OR (column_name = 'key_epoch' AND data_type = 'bigint' AND column_type = 'bigint'
                     AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
                     AND COALESCE(generation_expression, '') = ''
                     AND ordinal_position = 7 AND character_set_name IS NULL
                     AND collation_name IS NULL AND numeric_precision = 19 AND numeric_scale = 0)
                OR (column_name = 'authenticated_envelope' AND data_type = 'longtext'
                     AND column_type = 'longtext' AND character_set_name = 'utf8mb4'
                     AND collation_name = 'utf8mb4_unicode_ci' AND is_nullable = 'NO'
                     AND column_default IS NULL AND extra = '' AND COALESCE(generation_expression, '') = '' AND ordinal_position = 9))) = 9
    THEN 1 ELSE 0 END);
SELECT CASE WHEN @h12g_terminal_child_shape_exact = 1
THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @h12g_terminal_child_primary_exact = (
    SELECT CASE WHEN COUNT(*) = 1 AND SUM(CASE WHEN
        non_unique = 0 AND seq_in_index = 1 AND column_name = 'id'
        AND sub_part IS NULL AND index_type = 'BTREE'
        AND is_visible = 'YES' AND collation = 'A'
        THEN 1 ELSE 0 END) = 1 THEN 1 ELSE 0 END
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
      AND index_name = 'PRIMARY');
SET @h12g_terminal_child_unique_exact = (
    SELECT CASE WHEN COUNT(*) = 1 AND SUM(CASE WHEN
        non_unique = 0 AND seq_in_index = 1 AND column_name = 'binding_digest'
        AND sub_part IS NULL AND index_type = 'BTREE'
        AND is_visible = 'YES' AND collation = 'A'
        THEN 1 ELSE 0 END) = 1 THEN 1 ELSE 0 END
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
      AND index_name = 'uk_h12g_terminal_binding_digest');
SET @h12g_terminal_child_world_index_exact = (
    SELECT CASE WHEN COUNT(*) = 1 AND SUM(CASE WHEN
        non_unique = 1 AND seq_in_index = 1 AND column_name = 'world_id'
        AND sub_part IS NULL AND index_type = 'BTREE'
        AND is_visible = 'YES' AND collation = 'A'
        THEN 1 ELSE 0 END) = 1 THEN 1 ELSE 0 END
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
      AND index_name = 'idx_h12g_terminal_world');
SET @h12g_terminal_child_key_index_exact = (
    SELECT CASE WHEN COUNT(*) = 2 AND SUM(CASE WHEN
        non_unique = 1 AND index_type = 'BTREE' AND is_visible = 'YES'
        AND sub_part IS NULL AND collation = 'A'
        AND ((seq_in_index = 1 AND column_name = 'key_epoch')
             OR (seq_in_index = 2 AND column_name = 'key_identity'))
        THEN 1 ELSE 0 END) = 2 THEN 1 ELSE 0 END
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
      AND index_name = 'idx_h12g_terminal_key_epoch_identity');
SELECT CASE WHEN
    (NOT EXISTS (SELECT 1 FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
                  AND index_name = 'PRIMARY') OR @h12g_terminal_child_primary_exact = 1)
    AND (NOT EXISTS (SELECT 1 FROM information_schema.statistics
                    WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
                      AND index_name = 'uk_h12g_terminal_binding_digest')
         OR @h12g_terminal_child_unique_exact = 1)
    AND (NOT EXISTS (SELECT 1 FROM information_schema.statistics
                    WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
                      AND index_name = 'idx_h12g_terminal_world')
         OR @h12g_terminal_child_world_index_exact = 1)
    AND (NOT EXISTS (SELECT 1 FROM information_schema.statistics
                    WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
                      AND index_name = 'idx_h12g_terminal_key_epoch_identity')
         OR @h12g_terminal_child_key_index_exact = 1)
THEN 1 ELSE REGEXP_LIKE('x', '[') END;

PREPARE h12g_terminal_child_primary_preflight FROM @h12g_terminal_child_preflight_sql;
EXECUTE h12g_terminal_child_primary_preflight;
DEALLOCATE PREPARE h12g_terminal_child_primary_preflight;
SET @h12g_terminal_child_primary_ddl = IF(
    @h12g_terminal_child_primary_exact = 1,
    'SELECT 1',
    CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
           ' ADD PRIMARY KEY (`id`)'));
PREPARE h12g_terminal_child_primary_ddl_statement FROM @h12g_terminal_child_primary_ddl;
EXECUTE h12g_terminal_child_primary_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_child_primary_ddl_statement;

PREPARE h12g_terminal_child_unique_preflight FROM @h12g_terminal_child_preflight_sql;
EXECUTE h12g_terminal_child_unique_preflight;
DEALLOCATE PREPARE h12g_terminal_child_unique_preflight;
SET @h12g_terminal_child_unique_ddl = IF(
    @h12g_terminal_child_unique_exact = 1,
    'SELECT 1',
    CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
           ' ADD CONSTRAINT `uk_h12g_terminal_binding_digest` '
           'UNIQUE (`binding_digest`)'));
PREPARE h12g_terminal_child_unique_ddl_statement FROM @h12g_terminal_child_unique_ddl;
EXECUTE h12g_terminal_child_unique_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_child_unique_ddl_statement;

PREPARE h12g_terminal_child_world_index_preflight FROM @h12g_terminal_child_preflight_sql;
EXECUTE h12g_terminal_child_world_index_preflight;
DEALLOCATE PREPARE h12g_terminal_child_world_index_preflight;
SET @h12g_terminal_child_world_index_ddl = IF(
    @h12g_terminal_child_world_index_exact = 1,
    'SELECT 1',
    CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
           ' ADD INDEX `idx_h12g_terminal_world` (`world_id`)'));
PREPARE h12g_terminal_child_world_index_ddl_statement FROM @h12g_terminal_child_world_index_ddl;
EXECUTE h12g_terminal_child_world_index_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_child_world_index_ddl_statement;

PREPARE h12g_terminal_child_key_index_preflight FROM @h12g_terminal_child_preflight_sql;
EXECUTE h12g_terminal_child_key_index_preflight;
DEALLOCATE PREPARE h12g_terminal_child_key_index_preflight;
SET @h12g_terminal_child_key_index_ddl = IF(
    @h12g_terminal_child_key_index_exact = 1,
    'SELECT 1',
    CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
           ' ADD INDEX `idx_h12g_terminal_key_epoch_identity` '
           '(`key_epoch`, `key_identity`)'));
PREPARE h12g_terminal_child_key_index_ddl_statement FROM @h12g_terminal_child_key_index_ddl;
EXECUTE h12g_terminal_child_key_index_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_child_key_index_ddl_statement;

PREPARE h12g_terminal_child_check_preflight FROM @h12g_terminal_child_preflight_sql;
EXECUTE h12g_terminal_child_check_preflight;
DEALLOCATE PREPARE h12g_terminal_child_check_preflight;
SET @h12g_terminal_child_check_ddl = IF(
    @h12g_terminal_child_check_exact = 1,
    'SELECT 1',
    CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
           ' ADD CONSTRAINT `ck_h12g_terminal_current_state` CHECK (',
           'version >= 0 AND version < 9223372036854775807 ',
           'AND world_id > 0 AND world_id < 9223372036854775807 ',
           'AND OCTET_LENGTH(source_identity) = 64 ',
           'AND REGEXP_LIKE(source_identity, ''^[0-9a-f]{64}$'', ''c'') ',
           'AND OCTET_LENGTH(terminal_key) BETWEEN 1 AND 256 ',
           'AND OCTET_LENGTH(binding_digest) = 64 ',
           'AND REGEXP_LIKE(binding_digest, ''^[0-9a-f]{64}$'', ''c'') ',
           'AND key_epoch > 0 AND key_epoch < 9223372036854775807 ',
           'AND OCTET_LENGTH(key_identity) = 64 ',
           'AND REGEXP_LIKE(key_identity, ''^[0-9a-f]{64}$'', ''c'') ',
           'AND OCTET_LENGTH(authenticated_envelope) BETWEEN 1 AND 524288)'));
PREPARE h12g_terminal_child_check_ddl_statement FROM @h12g_terminal_child_check_ddl;
EXECUTE h12g_terminal_child_check_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_child_check_ddl_statement;

SET @h12g_terminal_child_shape_exact = (
    SELECT CASE WHEN
        EXISTS (SELECT 1 FROM information_schema.tables AS checked_tables
                JOIN information_schema.collations AS checked_collations
                  ON checked_collations.collation_name = checked_tables.table_collation
                WHERE checked_tables.table_schema = DATABASE()
                  AND checked_tables.table_name = 'final_scene_h12g_terminals'
                  AND checked_tables.table_type = 'BASE TABLE'
                  AND checked_tables.engine = 'InnoDB'
                  AND checked_tables.table_collation = 'utf8mb4_unicode_ci'
                  AND checked_collations.character_set_name = 'utf8mb4'
                  AND COALESCE(checked_tables.create_options, '') = '')
        AND (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'final_scene_h12g_terminals') = 9
        AND (SELECT COUNT(*) FROM information_schema.columns
             WHERE table_schema = DATABASE()
               AND table_name = 'final_scene_h12g_terminals'
               AND (
                   (column_name = 'id' AND data_type = 'bigint' AND column_type = 'bigint'
                    AND is_nullable = 'NO' AND column_default IS NULL AND extra = 'auto_increment'
                    AND COALESCE(generation_expression, '') = ''
                    AND ordinal_position = 1 AND character_set_name IS NULL
                    AND collation_name IS NULL AND numeric_precision = 19 AND numeric_scale = 0)
                   OR (column_name IN ('version', 'world_id', 'key_epoch')
                    AND data_type = 'bigint' AND column_type = 'bigint'
                    AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
                    AND COALESCE(generation_expression, '') = ''
                    AND ordinal_position = CASE column_name
                        WHEN 'version' THEN 2 WHEN 'world_id' THEN 3 ELSE 7 END
                    AND character_set_name IS NULL AND collation_name IS NULL
                    AND numeric_precision = 19 AND numeric_scale = 0)
                   OR (column_name = 'source_identity' AND data_type = 'varchar'
                    AND column_type = 'varchar(64)' AND character_maximum_length = 64
                    AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci'
                    AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
                    AND COALESCE(generation_expression, '') = ''
                    AND ordinal_position = 4)
                   OR (column_name = 'terminal_key' AND data_type = 'varchar'
                    AND column_type = 'varchar(256)' AND character_maximum_length = 256
                    AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci'
                    AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
                    AND COALESCE(generation_expression, '') = ''
                    AND ordinal_position = 5)
                   OR (column_name = 'binding_digest' AND data_type = 'varchar'
                    AND column_type = 'varchar(64)' AND character_maximum_length = 64
                    AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci'
                    AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
                    AND COALESCE(generation_expression, '') = ''
                    AND ordinal_position = 6)
                   OR (column_name = 'key_identity' AND data_type = 'varchar'
                    AND column_type = 'varchar(64)' AND character_maximum_length = 64
                    AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci'
                    AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
                    AND COALESCE(generation_expression, '') = ''
                    AND ordinal_position = 8)
                   OR (column_name = 'authenticated_envelope' AND data_type = 'longtext'
                    AND column_type = 'longtext' AND character_set_name = 'utf8mb4'
                    AND collation_name = 'utf8mb4_unicode_ci' AND is_nullable = 'NO'
                    AND column_default IS NULL AND extra = '' AND COALESCE(generation_expression, '') = '' AND ordinal_position = 9)
               )) = 9
    THEN 1 ELSE 0 END);
SET @h12g_terminal_child_primary_exact = (
    SELECT CASE WHEN COUNT(*) = 1 AND SUM(CASE WHEN non_unique = 0
        AND seq_in_index = 1 AND column_name = 'id' AND sub_part IS NULL
        AND index_type = 'BTREE' AND is_visible = 'YES' AND collation = 'A'
        THEN 1 ELSE 0 END) = 1 THEN 1 ELSE 0 END
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
      AND index_name = 'PRIMARY');
SET @h12g_terminal_child_unique_exact = (
    SELECT CASE WHEN COUNT(*) = 1 AND SUM(CASE WHEN non_unique = 0
        AND seq_in_index = 1 AND column_name = 'binding_digest' AND sub_part IS NULL
        AND index_type = 'BTREE' AND is_visible = 'YES' AND collation = 'A'
        THEN 1 ELSE 0 END) = 1 THEN 1 ELSE 0 END
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
      AND index_name = 'uk_h12g_terminal_binding_digest');
SET @h12g_terminal_child_world_index_exact = (
    SELECT CASE WHEN COUNT(*) = 1 AND SUM(CASE WHEN non_unique = 1
        AND seq_in_index = 1 AND column_name = 'world_id' AND sub_part IS NULL
        AND index_type = 'BTREE' AND is_visible = 'YES' AND collation = 'A'
        THEN 1 ELSE 0 END) = 1 THEN 1 ELSE 0 END
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
      AND index_name = 'idx_h12g_terminal_world');
SET @h12g_terminal_child_key_index_exact = (
    SELECT CASE WHEN COUNT(*) = 2 AND SUM(CASE WHEN non_unique = 1
        AND index_type = 'BTREE' AND is_visible = 'YES' AND sub_part IS NULL
        AND collation = 'A'
        AND ((seq_in_index = 1 AND column_name = 'key_epoch')
             OR (seq_in_index = 2 AND column_name = 'key_identity'))
        THEN 1 ELSE 0 END) = 2 THEN 1 ELSE 0 END
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
      AND index_name = 'idx_h12g_terminal_key_epoch_identity');
SET @h12g_terminal_child_check_exact = (
    SELECT CASE WHEN COUNT(*) = 1 AND SUM(CASE WHEN tc.enforced = 'YES'
        AND REGEXP_REPLACE(
            LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(cc.check_clause, CHAR(92), ''), '`', ''), '(', ''), ')', ''), ' ', ''), '_latin1', ''), '_utf8mb4', '')),
            '[[:space:]]+', '') = @h12g_terminal_child_check_semantics
        THEN 1 ELSE 0 END) = 1 THEN 1 ELSE 0 END
    FROM information_schema.table_constraints AS tc
    JOIN information_schema.check_constraints AS cc
      ON cc.constraint_schema = tc.constraint_schema
     AND cc.constraint_name = tc.constraint_name
    WHERE tc.constraint_schema = DATABASE()
      AND tc.table_name = 'final_scene_h12g_terminals'
      AND tc.constraint_name = 'ck_h12g_terminal_current_state'
      AND tc.constraint_type = 'CHECK');

PREPARE h12g_terminal_child_fk_preflight FROM @h12g_terminal_child_preflight_sql;
EXECUTE h12g_terminal_child_fk_preflight;
DEALLOCATE PREPARE h12g_terminal_child_fk_preflight;
SET @h12g_terminal_child_fk_ddl = IF(
    @h12g_terminal_child_fk_exact = 1,
    'SELECT 1',
    CONCAT('ALTER TABLE ', @h12g_terminal_child_table,
           ' ADD CONSTRAINT `fk_h12g_terminal_key` FOREIGN KEY '
           '(`key_epoch`, `key_identity`) REFERENCES ',
           @h12g_terminal_parent_table,
           ' (`key_epoch`, `key_identity`) ON UPDATE RESTRICT ON DELETE RESTRICT'));
PREPARE h12g_terminal_child_fk_ddl_statement FROM @h12g_terminal_child_fk_ddl;
EXECUTE h12g_terminal_child_fk_ddl_statement;
DEALLOCATE PREPARE h12g_terminal_child_fk_ddl_statement;

SET @h12g_terminal_child_fk_key_columns_exact = (
    SELECT CASE WHEN COUNT(*) = 2 AND SUM(CASE WHEN
        table_schema = DATABASE() AND referenced_table_schema = DATABASE()
        AND referenced_table_name = 'final_scene_h12g_terminal_keys'
        AND ((ordinal_position = 1 AND column_name = 'key_epoch'
              AND referenced_column_name = 'key_epoch'
              AND position_in_unique_constraint = 1)
             OR (ordinal_position = 2 AND column_name = 'key_identity'
                 AND referenced_column_name = 'key_identity'
                 AND position_in_unique_constraint = 2))
        THEN 1 ELSE 0 END) = 2 THEN 1 ELSE 0 END
    FROM information_schema.key_column_usage
    WHERE constraint_schema = DATABASE() AND table_schema = DATABASE()
      AND table_name = 'final_scene_h12g_terminals'
      AND constraint_name = 'fk_h12g_terminal_key');
SET @h12g_terminal_child_fk_actions_exact = (
    SELECT CASE WHEN COUNT(*) = 1 AND SUM(CASE WHEN
        unique_constraint_schema = DATABASE()
        AND unique_constraint_name = 'uk_h12g_terminal_key_epoch_identity'
        AND referenced_table_name = 'final_scene_h12g_terminal_keys'
        AND match_option = 'NONE' AND update_rule = 'RESTRICT'
        AND delete_rule = 'RESTRICT'
        THEN 1 ELSE 0 END) = 1 THEN 1 ELSE 0 END
    FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'final_scene_h12g_terminals'
      AND constraint_name = 'fk_h12g_terminal_key');
SET @h12g_terminal_child_fk_exact = IF(
    @h12g_terminal_child_fk_key_columns_exact = 1
    AND @h12g_terminal_child_fk_actions_exact = 1, 1, 0);
SET @h12g_terminal_child_constraints_exact = (
    SELECT CASE WHEN COUNT(*) = 4 AND SUM(CASE WHEN
        (constraint_name = 'PRIMARY' AND constraint_type = 'PRIMARY KEY')
        OR (constraint_name = 'uk_h12g_terminal_binding_digest' AND constraint_type = 'UNIQUE')
        OR (constraint_name = 'ck_h12g_terminal_current_state'
            AND constraint_type = 'CHECK' AND enforced = 'YES')
        OR (constraint_name = 'fk_h12g_terminal_key' AND constraint_type = 'FOREIGN KEY')
        THEN 1 ELSE 0 END) = 4 THEN 1 ELSE 0 END
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'final_scene_h12g_terminals');
SELECT CASE WHEN @h12g_terminal_child_shape_exact = 1
                 AND @h12g_terminal_child_primary_exact = 1
                 AND @h12g_terminal_child_unique_exact = 1
                 AND @h12g_terminal_child_world_index_exact = 1
                 AND @h12g_terminal_child_key_index_exact = 1
                 AND @h12g_terminal_child_check_exact = 1
                 AND @h12g_terminal_child_fk_exact = 1
                 AND @h12g_terminal_child_constraints_exact = 1
THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @h12g_terminal_lock_release_result = RELEASE_LOCK(@h12g_terminal_lock_name);
SELECT CASE
    WHEN @h12g_terminal_lock_release_result = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;
