-- Durable unpersisted furnace smelting XP remainder (MySQL 8.0.16+).
-- The pre-XP product stored no remainder, so a missing column is safely filled with zero
-- only after the rest of the WorldFurnace table is proven to be the current exact shape.

SET @furnace_xp_database = DATABASE();
SELECT CASE
    WHEN @furnace_xp_database IS NOT NULL
         AND CHAR_LENGTH(@furnace_xp_database) > 0
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @furnace_xp_quoted_database = CONCAT(
    '`', REPLACE(@furnace_xp_database, '`', '``'), '`');
SET @furnace_xp_table = CONCAT(
    @furnace_xp_quoted_database, '.`world_furnaces`');
SET @furnace_xp_lock_namespace = CONCAT(
    'gameexpert:v006:furnace-xp:', @furnace_xp_database);
SET @furnace_xp_lock_name = SHA2(@furnace_xp_lock_namespace, 256);
SET @furnace_xp_lock_timeout_seconds = 30;
SELECT CASE
    WHEN CHAR_LENGTH(@furnace_xp_lock_name) BETWEEN 1 AND 64
         AND @furnace_xp_lock_timeout_seconds BETWEEN 1 AND 60
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @furnace_xp_lock_result = GET_LOCK(@furnace_xp_lock_name, @furnace_xp_lock_timeout_seconds);
SELECT CASE
    WHEN @furnace_xp_lock_result = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @furnace_xp_mysql_version = @@version;
SET @furnace_xp_mysql_major = CAST(
    SUBSTRING_INDEX(@furnace_xp_mysql_version, '.', 1) AS UNSIGNED);
SET @furnace_xp_mysql_minor = CAST(
    SUBSTRING_INDEX(SUBSTRING_INDEX(@furnace_xp_mysql_version, '.', 2), '.', -1)
    AS UNSIGNED);
SET @furnace_xp_mysql_patch = CAST(
    SUBSTRING_INDEX(SUBSTRING_INDEX(@furnace_xp_mysql_version, '.', 3), '.', -1)
    AS UNSIGNED);
SELECT CASE
    WHEN @furnace_xp_mysql_version REGEXP '^[0-9]+[.][0-9]+[.][0-9]+'
         AND LOWER(@@version_comment) NOT LIKE '%mariadb%'
         AND (
             @furnace_xp_mysql_major > 8
             OR (@furnace_xp_mysql_major = 8
                 AND (
                     @furnace_xp_mysql_minor > 0
                     OR (@furnace_xp_mysql_minor = 0
                         AND @furnace_xp_mysql_patch >= 16)
                 ))
         )
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @furnace_xp_sql_mode = @@SESSION.sql_mode;
SELECT CASE
    WHEN FIND_IN_SET('STRICT_TRANS_TABLES', @furnace_xp_sql_mode) > 0
         OR FIND_IN_SET('STRICT_ALL_TABLES', @furnace_xp_sql_mode) > 0
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

-- A view or another object must not be mistaken for the JPA-owned table.
SELECT CASE WHEN NOT EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'world_furnaces'
      AND table_type <> 'BASE TABLE')
THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @furnace_xp_table_object_exists = EXISTS(
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'world_furnaces');
SET @furnace_xp_table_exists = EXISTS(
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'world_furnaces'
      AND table_type = 'BASE TABLE');

-- Engine 2.1.3 inherited the MySQL 8 default for Hibernate-owned tables.
-- Change only the table default; existing text columns and unique-key semantics stay intact.
SET @furnace_xp_previous_metadata_timeout = @@SESSION.lock_wait_timeout;
SET SESSION lock_wait_timeout = 30;
SET @furnace_xp_default_ddl = IF(
    @furnace_xp_lock_result = 1 AND EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
          AND table_type = 'BASE TABLE' AND engine = 'InnoDB'
          AND table_collation = 'utf8mb4_0900_ai_ci'
          AND COALESCE(create_options, '') = ''),
    CONCAT('ALTER TABLE ', @furnace_xp_table,
        ' DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci'),
    'SELECT 1');
PREPARE furnace_xp_default_statement FROM @furnace_xp_default_ddl;
EXECUTE furnace_xp_default_statement;
DEALLOCATE PREPARE furnace_xp_default_statement;
SET SESSION lock_wait_timeout = @furnace_xp_previous_metadata_timeout;

-- Table creation remains owned by Hibernate. A not-yet-created table is a valid no-op.
SET @furnace_xp_table_contract_exact = IF(
    @furnace_xp_table_object_exists = 0,
    1,
    IF(
        @furnace_xp_table_exists = 1
        AND EXISTS(
            SELECT 1
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name = 'world_furnaces'
              AND table_type = 'BASE TABLE'
              AND engine = 'InnoDB'
              AND table_collation = 'utf8mb4_unicode_ci'
              AND COALESCE(create_options, '') = ''),
        1,
        0));
SELECT CASE
    WHEN @furnace_xp_table_contract_exact = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

-- These two installation fields are compared in Java, never in a SQL key.
-- Preserve their UTF-8 values, type and nullability while repairing the known old collation.
SET @furnace_xp_previous_text_timeout = @@SESSION.lock_wait_timeout;
SET SESSION lock_wait_timeout = 30;
SET @furnace_xp_text_ddl = IF(
    @furnace_xp_lock_result = 1 AND @furnace_xp_table_contract_exact = 1
    AND EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
          AND column_name = 'generated_installation_id'
          AND column_type = 'varchar(255)' AND is_nullable = 'YES'
          AND column_default IS NULL AND extra = '' AND column_comment = ''
          AND COALESCE(generation_expression, '') = ''
          AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_0900_ai_ci')
    AND NOT EXISTS (SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
          AND column_name = 'generated_installation_id')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
          AND COALESCE(generation_expression, '') <> '')
    AND NOT EXISTS (SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
          AND expression IS NOT NULL)
    AND NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
          AND constraint_type NOT IN ('PRIMARY KEY', 'UNIQUE')),
    CONCAT('ALTER TABLE ', @furnace_xp_table,
        ' MODIFY COLUMN `generated_installation_id` VARCHAR(255)',
        ' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL'),
    'SELECT 1');
PREPARE furnace_xp_text_statement FROM @furnace_xp_text_ddl;
EXECUTE furnace_xp_text_statement;
DEALLOCATE PREPARE furnace_xp_text_statement;

SET @furnace_xp_text_ddl = IF(
    @furnace_xp_lock_result = 1 AND @furnace_xp_table_contract_exact = 1
    AND EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
          AND column_name = 'generated_installation_fingerprint'
          AND column_type = 'varchar(64)' AND is_nullable = 'YES'
          AND column_default IS NULL AND extra = '' AND column_comment = ''
          AND COALESCE(generation_expression, '') = ''
          AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_0900_ai_ci')
    AND NOT EXISTS (SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
          AND column_name = 'generated_installation_fingerprint')
    AND NOT EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
          AND COALESCE(generation_expression, '') <> '')
    AND NOT EXISTS (SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
          AND expression IS NOT NULL)
    AND NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
          AND constraint_type NOT IN ('PRIMARY KEY', 'UNIQUE')),
    CONCAT('ALTER TABLE ', @furnace_xp_table,
        ' MODIFY COLUMN `generated_installation_fingerprint` VARCHAR(64)',
        ' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL'),
    'SELECT 1');
PREPARE furnace_xp_text_statement FROM @furnace_xp_text_ddl;
EXECUTE furnace_xp_text_statement;
DEALLOCATE PREPARE furnace_xp_text_statement;
SET SESSION lock_wait_timeout = @furnace_xp_previous_text_timeout;

SET @furnace_xp_column_exists = EXISTS(
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'world_furnaces'
      AND column_name = 'xp_milli');

-- Full-stack slots are Hibernate-owned and may be absent before its first update.
-- Accept either the complete exact extension or no extension; reject partial/malformed shapes.
SET @furnace_stack_column_count = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
      AND column_name IN ('input_durability', 'input_enchantments', 'input_map_id', 'input_shulker_id', 'input_bucket_mob_data', 'input_item_component_data', 'fuel_durability', 'fuel_enchantments', 'fuel_map_id', 'fuel_shulker_id', 'fuel_bucket_mob_data', 'fuel_item_component_data', 'output_durability', 'output_enchantments', 'output_map_id', 'output_shulker_id', 'output_bucket_mob_data', 'output_item_component_data'));
SET @furnace_stack_columns_exact = (
    @furnace_stack_column_count IN (0, 18)
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
           AND column_name IN ('input_durability', 'input_enchantments', 'input_map_id', 'input_shulker_id', 'input_bucket_mob_data', 'input_item_component_data', 'fuel_durability', 'fuel_enchantments', 'fuel_map_id', 'fuel_shulker_id', 'fuel_bucket_mob_data', 'fuel_item_component_data', 'output_durability', 'output_enchantments', 'output_map_id', 'output_shulker_id', 'output_bucket_mob_data', 'output_item_component_data')
           AND is_nullable = 'YES' AND column_default IS NULL AND extra = ''
           AND COALESCE(generation_expression, '') = ''
           AND ((column_name IN ('input_durability', 'fuel_durability', 'output_durability') AND data_type = 'int' AND column_type = 'int' AND numeric_precision = 10 AND numeric_scale = 0 AND character_set_name IS NULL AND collation_name IS NULL)
                OR (column_name IN ('input_enchantments', 'fuel_enchantments', 'output_enchantments') AND data_type = 'bigint' AND column_type = 'bigint' AND numeric_precision = 19 AND numeric_scale = 0 AND character_set_name IS NULL AND collation_name IS NULL)
                OR (column_name IN ('input_map_id', 'fuel_map_id', 'output_map_id') AND data_type = 'int' AND column_type = 'int' AND numeric_precision = 10 AND numeric_scale = 0 AND character_set_name IS NULL AND collation_name IS NULL)
                OR (column_name IN ('input_shulker_id', 'fuel_shulker_id', 'output_shulker_id') AND data_type = 'int' AND column_type = 'int' AND numeric_precision = 10 AND numeric_scale = 0 AND character_set_name IS NULL AND collation_name IS NULL)
                OR (column_name IN ('input_bucket_mob_data', 'fuel_bucket_mob_data', 'output_bucket_mob_data') AND data_type = 'varchar' AND column_type = 'varchar(512)' AND character_maximum_length = 512 AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci')
                OR (column_name IN ('input_item_component_data', 'fuel_item_component_data', 'output_item_component_data') AND data_type = 'longtext' AND column_type = 'longtext' AND character_maximum_length = 4294967295 AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci'))) = @furnace_stack_column_count);

-- All pre-existing columns must be the current WorldFurnace contract. Hibernate does not
-- guarantee physical column order, so identity is the exact member set and per-column shape.
SET @furnace_xp_base_columns_exact = IF(
    @furnace_xp_table_exists = 0,
    1,
    IF(
        (SELECT COUNT(*)
         FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = 'world_furnaces'
           AND column_name <> 'xp_milli'
           AND column_name NOT IN ('input_durability', 'input_enchantments', 'input_map_id', 'input_shulker_id', 'input_bucket_mob_data', 'input_item_component_data', 'fuel_durability', 'fuel_enchantments', 'fuel_map_id', 'fuel_shulker_id', 'fuel_bucket_mob_data', 'fuel_item_component_data', 'output_durability', 'output_enchantments', 'output_map_id', 'output_shulker_id', 'output_bucket_mob_data', 'output_item_component_data')) = 18
        AND @furnace_stack_columns_exact = 1
        AND EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
              AND column_name = 'id' AND data_type = 'bigint' AND column_type = 'bigint'
              AND is_nullable = 'NO' AND column_default IS NULL AND extra = 'auto_increment'
              AND COALESCE(generation_expression, '') = ''
              AND numeric_precision = 19 AND numeric_scale = 0
              AND character_set_name IS NULL AND collation_name IS NULL)
        AND EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
              AND column_name = 'world_id' AND data_type = 'bigint' AND column_type = 'bigint'
              AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND numeric_precision = 19 AND numeric_scale = 0
              AND character_set_name IS NULL AND collation_name IS NULL)
        AND EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
              AND column_name = 'pos_x' AND data_type = 'int' AND column_type = 'int'
              AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND numeric_precision = 10 AND numeric_scale = 0
              AND character_set_name IS NULL AND collation_name IS NULL)
        AND EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
              AND column_name = 'pos_y' AND data_type = 'int' AND column_type = 'int'
              AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND numeric_precision = 10 AND numeric_scale = 0
              AND character_set_name IS NULL AND collation_name IS NULL)
        AND EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
              AND column_name = 'pos_z' AND data_type = 'int' AND column_type = 'int'
              AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND numeric_precision = 10 AND numeric_scale = 0
              AND character_set_name IS NULL AND collation_name IS NULL)
        AND EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
              AND column_name = 'persistence_revision' AND data_type = 'bigint'
              AND column_type = 'bigint' AND is_nullable = 'NO' AND column_default = '0'
              AND extra = '' AND COALESCE(generation_expression, '') = ''
              AND numeric_precision = 19 AND numeric_scale = 0
              AND character_set_name IS NULL AND collation_name IS NULL)
        AND EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
              AND column_name = 'input_type' AND data_type = 'smallint'
              AND column_type = 'smallint' AND is_nullable = 'NO' AND column_default IS NULL
              AND extra = '' AND COALESCE(generation_expression, '') = ''
              AND numeric_precision = 5 AND numeric_scale = 0
              AND character_set_name IS NULL AND collation_name IS NULL)
        AND EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
              AND column_name = 'input_count' AND data_type = 'int' AND column_type = 'int'
              AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND numeric_precision = 10 AND numeric_scale = 0
              AND character_set_name IS NULL AND collation_name IS NULL)
        AND EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
              AND column_name = 'fuel_type' AND data_type = 'smallint'
              AND column_type = 'smallint' AND is_nullable = 'NO' AND column_default IS NULL
              AND extra = '' AND COALESCE(generation_expression, '') = ''
              AND numeric_precision = 5 AND numeric_scale = 0
              AND character_set_name IS NULL AND collation_name IS NULL)
        AND EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
              AND column_name = 'fuel_count' AND data_type = 'int' AND column_type = 'int'
              AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND numeric_precision = 10 AND numeric_scale = 0
              AND character_set_name IS NULL AND collation_name IS NULL)
        AND EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
              AND column_name = 'output_type' AND data_type = 'smallint'
              AND column_type = 'smallint' AND is_nullable = 'NO' AND column_default IS NULL
              AND extra = '' AND COALESCE(generation_expression, '') = ''
              AND numeric_precision = 5 AND numeric_scale = 0
              AND character_set_name IS NULL AND collation_name IS NULL)
        AND EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
              AND column_name = 'output_count' AND data_type = 'int' AND column_type = 'int'
              AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND numeric_precision = 10 AND numeric_scale = 0
              AND character_set_name IS NULL AND collation_name IS NULL)
        AND EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
              AND column_name = 'burn_ticks' AND data_type = 'int' AND column_type = 'int'
              AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND numeric_precision = 10 AND numeric_scale = 0
              AND character_set_name IS NULL AND collation_name IS NULL)
        AND EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
              AND column_name = 'burn_total_ticks' AND data_type = 'int'
              AND column_type = 'int' AND is_nullable = 'NO' AND column_default IS NULL
              AND extra = '' AND COALESCE(generation_expression, '') = ''
              AND numeric_precision = 10 AND numeric_scale = 0
              AND character_set_name IS NULL AND collation_name IS NULL)
        AND EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
              AND column_name = 'cook_ticks' AND data_type = 'int' AND column_type = 'int'
              AND is_nullable = 'NO' AND column_default IS NULL AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND numeric_precision = 10 AND numeric_scale = 0
              AND character_set_name IS NULL AND collation_name IS NULL)
        AND EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
              AND column_name = 'variant_code' AND data_type = 'int' AND column_type = 'int'
              AND is_nullable = 'NO' AND column_default = '0' AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND numeric_precision = 10 AND numeric_scale = 0
              AND character_set_name IS NULL AND collation_name IS NULL)
        AND EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
              AND column_name = 'generated_installation_id' AND data_type = 'varchar'
              AND column_type = 'varchar(255)' AND is_nullable = 'YES'
              AND column_default IS NULL AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND character_maximum_length = 255
              AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci')
        AND EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
              AND column_name = 'generated_installation_fingerprint' AND data_type = 'varchar'
              AND column_type = 'varchar(64)' AND is_nullable = 'YES'
              AND column_default IS NULL AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND character_maximum_length = 64
              AND character_set_name = 'utf8mb4' AND collation_name = 'utf8mb4_unicode_ci')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
              AND COALESCE(generation_expression, '') <> ''),
        1,
        0));

-- A present XP column is accepted only with the exact persisted representation. A missing
-- column is the sole admissible repair because the old product's implicit remainder was zero.
SET @furnace_xp_existing_column_exact = IF(
    @furnace_xp_table_exists = 0 OR @furnace_xp_column_exists = 0,
    1,
    IF(EXISTS (SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
          AND column_name = 'xp_milli' AND data_type = 'int' AND column_type = 'int'
          AND is_nullable = 'NO' AND column_default = '0' AND extra = ''
          AND COALESCE(generation_expression, '') = ''
          AND numeric_precision = 10 AND numeric_scale = 0
          AND character_set_name IS NULL AND collation_name IS NULL),
        1,
        0));

SET @furnace_xp_indexes_exact = IF(
    @furnace_xp_table_exists = 0,
    1,
    IF(
        (SELECT COUNT(*) FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = 'world_furnaces') = 5
        AND (SELECT COUNT(*) FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
               AND index_name = 'PRIMARY' AND non_unique = 0 AND seq_in_index = 1
               AND column_name = 'id' AND sub_part IS NULL AND index_type = 'BTREE'
               AND is_visible = 'YES') = 1
        AND (SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
             FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
               AND index_name = 'uk_world_furnace_pos' AND non_unique = 0
               AND sub_part IS NULL AND index_type = 'BTREE' AND is_visible = 'YES') =
            'world_id,pos_x,pos_y,pos_z',
        1,
        0));
SET @furnace_xp_constraints_exact = IF(
    @furnace_xp_table_exists = 0,
    1,
    IF(
        (SELECT COUNT(*) FROM information_schema.table_constraints
         WHERE constraint_schema = DATABASE() AND table_name = 'world_furnaces') = 2
        AND EXISTS (SELECT 1 FROM information_schema.table_constraints
            WHERE constraint_schema = DATABASE() AND table_name = 'world_furnaces'
              AND constraint_name = 'PRIMARY' AND constraint_type = 'PRIMARY KEY')
        AND EXISTS (SELECT 1 FROM information_schema.table_constraints
            WHERE constraint_schema = DATABASE() AND table_name = 'world_furnaces'
              AND constraint_name = 'uk_world_furnace_pos' AND constraint_type = 'UNIQUE'),
        1,
        0));

-- Every assertion above is evaluated before this optional ALTER. No legacy row is rewritten.
SELECT CASE
    WHEN @furnace_xp_table_contract_exact = 1
         AND @furnace_xp_base_columns_exact = 1
         AND @furnace_xp_existing_column_exact = 1
         AND @furnace_xp_indexes_exact = 1
         AND @furnace_xp_constraints_exact = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @furnace_xp_ddl = IF(
    @furnace_xp_table_exists = 1
    AND @furnace_xp_column_exists = 0,
    CONCAT(
        'ALTER TABLE ', @furnace_xp_table,
        ' ADD COLUMN `xp_milli` INT NOT NULL DEFAULT 0 AFTER `persistence_revision`'),
    'SELECT 1');
PREPARE furnace_xp_ddl_statement FROM @furnace_xp_ddl;
EXECUTE furnace_xp_ddl_statement;
DEALLOCATE PREPARE furnace_xp_ddl_statement;

-- Re-read the added column so a concurrent or malformed result is never accepted by name only.
SET @furnace_xp_post_table_exists = EXISTS(
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'world_furnaces'
      AND table_type = 'BASE TABLE');
SET @furnace_xp_postcondition_exact = IF(
    @furnace_xp_post_table_exists = 0,
    1,
    IF(
        (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'world_furnaces') = 19 + @furnace_stack_column_count
        AND @furnace_stack_columns_exact = 1
        AND EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
              AND column_name = 'xp_milli' AND data_type = 'int' AND column_type = 'int'
              AND is_nullable = 'NO' AND column_default = '0' AND extra = ''
              AND COALESCE(generation_expression, '') = ''
              AND numeric_precision = 10 AND numeric_scale = 0
              AND character_set_name IS NULL AND collation_name IS NULL)
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'world_furnaces'
              AND COALESCE(generation_expression, '') <> ''),
        1,
        0));
SELECT CASE
    WHEN @furnace_xp_postcondition_exact = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @furnace_xp_release_result = RELEASE_LOCK(@furnace_xp_lock_name);
SELECT CASE
    WHEN @furnace_xp_release_result = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;
