-- One exact, crash-durable fractional furnace XP suffix per world.
-- `worlds` 는 Hibernate 가 만드는 테이블이라 빈 스키마 첫 기동에는 아직 없다.
-- 그래서 FK 절은 `worlds` 가 존재할 때만 붙이고, 없으면 다음 기동에서 ALTER 로 보강한다.
SET @furnace_carry_worlds_ready = EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'worlds'
      AND table_type = 'BASE TABLE');

SET @furnace_carry_create = CONCAT(
    'CREATE TABLE IF NOT EXISTS `world_furnace_xp_carry` (',
    '`world_id` BIGINT NOT NULL,',
    '`carry_milli` INT NOT NULL,',
    '`persistence_revision` BIGINT NOT NULL,',
    'PRIMARY KEY (`world_id`),',
    IF(@furnace_carry_worlds_ready,
        'CONSTRAINT `fk_world_furnace_xp_carry_world` FOREIGN KEY (`world_id`) REFERENCES `worlds` (`id`) ON DELETE CASCADE,',
        ''),
    'CONSTRAINT `ck_world_furnace_xp_carry_exact` CHECK (',
    '`world_id` > 0 AND `world_id` < 9223372036854775807',
    ' AND `carry_milli` >= 0 AND `carry_milli` < 1000',
    ' AND `persistence_revision` >= 0',
    ' AND `persistence_revision` < 9223372036854775807',
    ')',
    ') ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci');
PREPARE furnace_carry_create_statement FROM @furnace_carry_create;
EXECUTE furnace_carry_create_statement;
DEALLOCATE PREPARE furnace_carry_create_statement;

-- 이전 기동에서 FK 없이 만들어졌다면 `worlds` 가 생긴 뒤 한 번만 보강한다.
SET @furnace_carry_fk_missing = @furnace_carry_worlds_ready AND NOT EXISTS (
    SELECT 1 FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'world_furnace_xp_carry'
      AND constraint_name = 'fk_world_furnace_xp_carry_world');
SET @furnace_carry_fk_repair = IF(@furnace_carry_fk_missing,
    'ALTER TABLE `world_furnace_xp_carry` ADD CONSTRAINT `fk_world_furnace_xp_carry_world` FOREIGN KEY (`world_id`) REFERENCES `worlds` (`id`) ON DELETE CASCADE',
    'SELECT 1');
PREPARE furnace_carry_fk_repair_statement FROM @furnace_carry_fk_repair;
EXECUTE furnace_carry_fk_repair_statement;
DEALLOCATE PREPARE furnace_carry_fk_repair_statement;

-- Fail closed if a pre-existing object made IF NOT EXISTS a no-op or if DDL drifted.
SELECT CASE WHEN EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'world_furnace_xp_carry'
      AND table_type = 'BASE TABLE'
      AND engine = 'InnoDB'
      AND table_collation = 'utf8mb4_unicode_ci'
) THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SELECT CASE WHEN (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'world_furnace_xp_carry'
) = 3
AND EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'world_furnace_xp_carry'
      AND column_name = 'world_id' AND ordinal_position = 1
      AND column_type = 'bigint' AND is_nullable = 'NO'
)
AND EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'world_furnace_xp_carry'
      AND column_name = 'carry_milli' AND ordinal_position = 2
      AND column_type = 'int' AND is_nullable = 'NO'
)
AND EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'world_furnace_xp_carry'
      AND column_name = 'persistence_revision' AND ordinal_position = 3
      AND column_type = 'bigint' AND is_nullable = 'NO'
)
THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SELECT CASE WHEN (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'world_furnace_xp_carry'
) = 1
AND EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'world_furnace_xp_carry'
      AND index_name = 'PRIMARY' AND non_unique = 0
      AND seq_in_index = 1 AND column_name = 'world_id'
)
THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SELECT CASE WHEN (NOT @furnace_carry_worlds_ready OR EXISTS (
    SELECT 1 FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'world_furnace_xp_carry'
      AND constraint_name = 'fk_world_furnace_xp_carry_world'
      AND referenced_table_name = 'worlds'
      AND delete_rule = 'CASCADE' AND update_rule IN ('RESTRICT', 'NO ACTION')
))
AND EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'world_furnace_xp_carry'
      AND constraint_name = 'ck_world_furnace_xp_carry_exact'
      AND constraint_type = 'CHECK'
      AND enforced = 'YES'
)
THEN 1 ELSE REGEXP_LIKE('x', '[') END;
