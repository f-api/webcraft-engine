-- Durable coordinate-owned enchanting-table inventory aggregate (MySQL 8.0.16+).

SET @enchanting_database = DATABASE();
SELECT CASE WHEN @enchanting_database IS NOT NULL AND CHAR_LENGTH(@enchanting_database) > 0
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;
SET @enchanting_quoted_database = CONCAT('`', REPLACE(@enchanting_database, '`', '``'), '`');
SET @enchanting_parent = CONCAT(@enchanting_quoted_database, '.`world_enchanting_tables`');
SET @enchanting_items = CONCAT(@enchanting_quoted_database, '.`world_enchanting_table_items`');
SET @enchanting_lock_name = SHA2(CONCAT('gameexpert:v005:enchanting:', @enchanting_database), 256);
SET @enchanting_lock_timeout_seconds = 30;
SELECT CASE WHEN CHAR_LENGTH(@enchanting_lock_name) BETWEEN 1 AND 64
        AND @enchanting_lock_timeout_seconds BETWEEN 1 AND 60
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;
SET @enchanting_lock_result = GET_LOCK(@enchanting_lock_name, @enchanting_lock_timeout_seconds);
SELECT CASE WHEN @enchanting_lock_result = 1 THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @enchanting_mysql_version = @@version;
SET @enchanting_mysql_major = CAST(SUBSTRING_INDEX(@enchanting_mysql_version, '.', 1) AS UNSIGNED);
SET @enchanting_mysql_minor = CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(@enchanting_mysql_version, '.', 2), '.', -1) AS UNSIGNED);
SET @enchanting_mysql_patch = CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(@enchanting_mysql_version, '.', 3), '.', -1) AS UNSIGNED);
SELECT CASE WHEN @enchanting_mysql_version REGEXP '^[0-9]+[.][0-9]+[.][0-9]+'
        AND LOWER(@@version_comment) NOT LIKE '%mariadb%'
        AND (@enchanting_mysql_major > 8 OR (@enchanting_mysql_major = 8
            AND (@enchanting_mysql_minor > 0 OR (@enchanting_mysql_minor = 0
                AND @enchanting_mysql_patch >= 16))))
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;
SET @enchanting_sql_mode = @@SESSION.sql_mode;
SELECT CASE WHEN FIND_IN_SET('STRICT_TRANS_TABLES', @enchanting_sql_mode) > 0
        OR FIND_IN_SET('STRICT_ALL_TABLES', @enchanting_sql_mode) > 0
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

-- Reject colliding views and other non-table objects before any DDL.
SELECT CASE WHEN NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN ('world_enchanting_tables', 'world_enchanting_table_items')
      AND table_type <> 'BASE TABLE')
THEN 1 ELSE REGEXP_LIKE('x', '[') END;

-- Reject pre-existing generated columns: the aggregate stores every value explicitly, and a
-- generated column would silently rewrite persisted enchanting state on read.
SELECT CASE WHEN NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name IN ('world_enchanting_tables', 'world_enchanting_table_items')
      AND extra LIKE '%GENERATED%')
THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @enchanting_parent_create = CONCAT(
    'CREATE TABLE IF NOT EXISTS ', @enchanting_parent, ' (',
    '`id` BIGINT NOT NULL AUTO_INCREMENT,',
    '`world_id` BIGINT NOT NULL,',
    '`pos_x` INT NOT NULL,',
    '`pos_y` INT NOT NULL,',
    '`pos_z` INT NOT NULL,',
    '`persistence_revision` BIGINT NOT NULL DEFAULT 0,',
    'PRIMARY KEY (`id`),',
    'CONSTRAINT `uk_world_enchanting_table_pos` UNIQUE (`world_id`,`pos_x`,`pos_y`,`pos_z`),',
    'CONSTRAINT `ck_world_enchanting_table_identity` CHECK (`world_id` > 0 AND `persistence_revision` >= 0)',
    ') ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci');
PREPARE enchanting_parent_create_statement FROM @enchanting_parent_create;
EXECUTE enchanting_parent_create_statement;
DEALLOCATE PREPARE enchanting_parent_create_statement;

-- Existing tables are accepted only when their complete shape is already exact. No repair ALTER is safe.
SELECT CASE WHEN
    (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()
      AND table_name = 'world_enchanting_tables' AND table_type = 'BASE TABLE'
      AND engine = 'InnoDB' AND table_collation = 'utf8mb4_unicode_ci'
      AND COALESCE(create_options, '') = '') = 1
    AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'world_enchanting_tables') = 6
    AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'world_enchanting_tables' AND column_name = 'id' AND ordinal_position = 1
      AND data_type = 'bigint' AND column_type = 'bigint' AND is_nullable = 'NO'
      AND column_default IS NULL AND extra = 'auto_increment' AND numeric_precision = 19 AND numeric_scale = 0)
    AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'world_enchanting_tables' AND column_name = 'world_id' AND ordinal_position = 2
      AND data_type = 'bigint' AND column_type = 'bigint' AND is_nullable = 'NO'
      AND column_default IS NULL AND extra = '' AND numeric_precision = 19 AND numeric_scale = 0)
    AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'world_enchanting_tables' AND column_name = 'pos_x' AND ordinal_position = 3
      AND data_type = 'int' AND column_type = 'int' AND is_nullable = 'NO' AND column_default IS NULL AND extra = '')
    AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'world_enchanting_tables' AND column_name = 'pos_y' AND ordinal_position = 4
      AND data_type = 'int' AND column_type = 'int' AND is_nullable = 'NO' AND column_default IS NULL AND extra = '')
    AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'world_enchanting_tables' AND column_name = 'pos_z' AND ordinal_position = 5
      AND data_type = 'int' AND column_type = 'int' AND is_nullable = 'NO' AND column_default IS NULL AND extra = '')
    AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'world_enchanting_tables' AND column_name = 'persistence_revision' AND ordinal_position = 6
      AND data_type = 'bigint' AND column_type = 'bigint' AND is_nullable = 'NO'
      AND column_default = '0' AND extra = '' AND numeric_precision = 19 AND numeric_scale = 0)
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'world_enchanting_tables'
      AND COALESCE(generation_expression, '') <> '')
THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SELECT CASE WHEN
    (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
      AND table_name = 'world_enchanting_tables') = 5
    AND (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
      AND table_name = 'world_enchanting_tables' AND index_name = 'PRIMARY' AND non_unique = 0
      AND seq_in_index = 1 AND column_name = 'id' AND sub_part IS NULL AND index_type = 'BTREE' AND is_visible = 'YES') = 1
    AND (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
      FROM information_schema.statistics WHERE table_schema = DATABASE()
      AND table_name = 'world_enchanting_tables' AND index_name = 'uk_world_enchanting_table_pos'
      AND non_unique = 0 AND sub_part IS NULL AND index_type = 'BTREE' AND is_visible = 'YES') = 'world_id,pos_x,pos_y,pos_z'
THEN 1 ELSE REGEXP_LIKE('x', '[') END;
SELECT CASE WHEN
    (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema = DATABASE()
      AND table_name = 'world_enchanting_tables') = 3
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE()
      AND table_name = 'world_enchanting_tables' AND constraint_name = 'PRIMARY' AND constraint_type = 'PRIMARY KEY')
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE()
      AND table_name = 'world_enchanting_tables' AND constraint_name = 'uk_world_enchanting_table_pos' AND constraint_type = 'UNIQUE')
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE()
      AND table_name = 'world_enchanting_tables' AND constraint_name = 'ck_world_enchanting_table_identity'
      AND constraint_type = 'CHECK' AND enforced = 'YES')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema = DATABASE()
      AND constraint_name = 'ck_world_enchanting_table_identity'
      AND REGEXP_REPLACE(LOWER(check_clause), '[[:space:]`()]+', '') =
          'world_id>0andpersistence_revision>=0')
THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @enchanting_items_create = CONCAT(
    'CREATE TABLE IF NOT EXISTS ', @enchanting_items, ' (',
    '`table_id` BIGINT NOT NULL,',
    '`slot` INT NOT NULL,',
    '`item_type` SMALLINT NOT NULL,',
    '`item_count` INT NOT NULL,',
    '`durability` INT NULL,',
    '`enchantments` BIGINT NULL,',
    '`map_id` INT NULL,',
    '`shulker_id` INT NULL,',
    '`bucket_mob_data` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,',
    '`item_component_data` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,',
    'CONSTRAINT `uk_world_enchanting_table_item_slot` UNIQUE (`table_id`,`slot`),',
    'CONSTRAINT `fk_world_enchanting_item_table` FOREIGN KEY (`table_id`) REFERENCES ',
    @enchanting_parent, ' (`id`) ON UPDATE RESTRICT ON DELETE CASCADE,',
    'CONSTRAINT `ck_world_enchanting_item_value` CHECK (`slot` BETWEEN 0 AND 1 AND `item_type` <> 0 AND `item_count` > 0)',
    ') ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci');
PREPARE enchanting_items_create_statement FROM @enchanting_items_create;
EXECUTE enchanting_items_create_statement;
DEALLOCATE PREPARE enchanting_items_create_statement;

SET @enchanting_bucket_expand = IF(
    EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'world_enchanting_table_items'
        AND column_name = 'bucket_mob_data' AND ordinal_position = 9
        AND data_type = 'varchar' AND column_type = 'varchar(512)'
        AND character_maximum_length = 512 AND is_nullable = 'YES'
        AND column_default IS NULL AND extra = ''
        AND COALESCE(generation_expression, '') = ''
        AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci'),
    CONCAT('ALTER TABLE ', @enchanting_items,
      ' MODIFY COLUMN `bucket_mob_data` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL'),
    'SELECT 1');
PREPARE enchanting_bucket_expand_statement FROM @enchanting_bucket_expand;
EXECUTE enchanting_bucket_expand_statement;
DEALLOCATE PREPARE enchanting_bucket_expand_statement;

SELECT CASE WHEN
    (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()
      AND table_name = 'world_enchanting_table_items' AND table_type = 'BASE TABLE'
      AND engine = 'InnoDB' AND table_collation = 'utf8mb4_unicode_ci'
      AND COALESCE(create_options, '') = '') = 1
    AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'world_enchanting_table_items') = 10
    AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'world_enchanting_table_items'
      AND column_name = 'table_id' AND ordinal_position = 1 AND data_type = 'bigint' AND column_type = 'bigint' AND is_nullable = 'NO' AND column_default IS NULL AND extra = '')
    AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'world_enchanting_table_items'
      AND column_name = 'slot' AND ordinal_position = 2 AND data_type = 'int' AND column_type = 'int' AND is_nullable = 'NO' AND column_default IS NULL AND extra = '')
    AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'world_enchanting_table_items'
      AND column_name = 'item_type' AND ordinal_position = 3 AND data_type = 'smallint' AND column_type = 'smallint' AND is_nullable = 'NO' AND column_default IS NULL AND extra = '')
    AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'world_enchanting_table_items'
      AND column_name = 'item_count' AND ordinal_position = 4 AND data_type = 'int' AND column_type = 'int' AND is_nullable = 'NO' AND column_default IS NULL AND extra = '')
    AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'world_enchanting_table_items'
      AND column_name = 'durability' AND ordinal_position = 5 AND data_type = 'int' AND column_type = 'int' AND is_nullable = 'YES' AND column_default IS NULL AND extra = '')
    AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'world_enchanting_table_items'
      AND column_name = 'enchantments' AND ordinal_position = 6 AND data_type = 'bigint' AND column_type = 'bigint' AND is_nullable = 'YES' AND column_default IS NULL AND extra = '')
    AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'world_enchanting_table_items'
      AND column_name = 'map_id' AND ordinal_position = 7 AND data_type = 'int' AND column_type = 'int' AND is_nullable = 'YES' AND column_default IS NULL AND extra = '')
    AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'world_enchanting_table_items'
      AND column_name = 'shulker_id' AND ordinal_position = 8 AND data_type = 'int' AND column_type = 'int' AND is_nullable = 'YES' AND column_default IS NULL AND extra = '')
    AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'world_enchanting_table_items'
      AND column_name = 'bucket_mob_data' AND ordinal_position = 9 AND data_type = 'longtext' AND column_type = 'longtext' AND is_nullable = 'YES'
      AND column_default IS NULL AND extra = '' AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci')
    AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'world_enchanting_table_items'
      AND column_name = 'item_component_data' AND ordinal_position = 10 AND data_type = 'longtext' AND column_type = 'longtext' AND is_nullable = 'YES'
      AND column_default IS NULL AND extra = '' AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
      AND table_name = 'world_enchanting_table_items'
      AND COALESCE(generation_expression, '') <> '')
THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SELECT CASE WHEN
    (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
      AND table_name = 'world_enchanting_table_items') = 2
    AND (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
      FROM information_schema.statistics WHERE table_schema = DATABASE()
      AND table_name = 'world_enchanting_table_items' AND index_name = 'uk_world_enchanting_table_item_slot'
      AND non_unique = 0 AND sub_part IS NULL AND index_type = 'BTREE' AND is_visible = 'YES') = 'table_id,slot'
THEN 1 ELSE REGEXP_LIKE('x', '[') END;
SELECT CASE WHEN
    (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema = DATABASE()
      AND table_name = 'world_enchanting_table_items') = 3
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE()
      AND table_name = 'world_enchanting_table_items' AND constraint_name = 'uk_world_enchanting_table_item_slot' AND constraint_type = 'UNIQUE')
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE()
      AND table_name = 'world_enchanting_table_items' AND constraint_name = 'fk_world_enchanting_item_table' AND constraint_type = 'FOREIGN KEY')
    AND EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema = DATABASE()
      AND table_name = 'world_enchanting_table_items' AND constraint_name = 'ck_world_enchanting_item_value' AND constraint_type = 'CHECK' AND enforced = 'YES')
    AND EXISTS (SELECT 1 FROM information_schema.check_constraints WHERE constraint_schema = DATABASE()
      AND constraint_name = 'ck_world_enchanting_item_value'
      AND REGEXP_REPLACE(LOWER(check_clause), '[[:space:]`()]+', '') =
          'slotbetween0and1anditem_type<>0anditem_count>0')
    AND EXISTS (SELECT 1 FROM information_schema.key_column_usage WHERE constraint_schema = DATABASE()
      AND table_name = 'world_enchanting_table_items' AND constraint_name = 'fk_world_enchanting_item_table'
      AND column_name = 'table_id' AND ordinal_position = 1 AND position_in_unique_constraint = 1
      AND referenced_table_schema = DATABASE() AND referenced_table_name = 'world_enchanting_tables' AND referenced_column_name = 'id')
    AND EXISTS (SELECT 1 FROM information_schema.referential_constraints WHERE constraint_schema = DATABASE()
      AND table_name = 'world_enchanting_table_items' AND constraint_name = 'fk_world_enchanting_item_table'
      AND unique_constraint_schema = DATABASE() AND referenced_table_name = 'world_enchanting_tables'
      AND unique_constraint_name = 'PRIMARY' AND match_option = 'NONE' AND update_rule = 'RESTRICT' AND delete_rule = 'CASCADE')
THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @enchanting_release_result = RELEASE_LOCK(@enchanting_lock_name);
SELECT CASE WHEN @enchanting_release_result = 1 THEN 1 ELSE REGEXP_LIKE('x', '[') END;
