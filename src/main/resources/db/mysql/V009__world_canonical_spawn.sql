-- 월드의 정본 스폰 좌표를 `worlds` 행에 새기기 위한 nullable 컬럼 세 개.
--
-- 스폰 탐색(WorldSpawn.find)은 시드만 입력으로 받는 순수 함수라 언제 계산해도 같은 값이 나온다.
-- 그런데 마른 땅이 원점에서 멀면 그 탐색이 청크를 동기 생산하며, 저장 월드에 콜드로 접속할 때마다
-- 그 비용을 다시 낸다. 첫 접속이 구한 좌표를 여기에 새기면 이후 접속은 읽기만 한다.
--
-- `worlds` 는 Hibernate 가 만드는 테이블이라 빈 스키마 첫 기동에는 아직 없다. 그때는 아무것도
-- 하지 않고, 뒤이어 도는 Hibernate 가 엔티티 정의대로 세 컬럼을 함께 만든다. 그래서 이 스크립트는
-- "이미 있는 테이블에 빠진 컬럼만 더한다"만 한다. 기존 행은 한 줄도 다시 쓰지 않는다(전부 NULL 로
-- 시작해, 그 월드의 첫 접속이 채운다).

SET @world_spawn_table_object_exists = EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'worlds');
SET @world_spawn_table_exists = EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'worlds'
      AND table_type = 'BASE TABLE');

-- 뷰나 다른 객체를 JPA 소유 테이블로 오인해서는 안 된다.
SELECT CASE
    WHEN @world_spawn_table_object_exists = 0 OR @world_spawn_table_exists = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

-- 이미 있는 컬럼은 정확히 계약된 모양(nullable INT, 기본값 없음, 생성 컬럼 아님)이어야 한다.
-- 하나라도 다르면 조용히 고치지 않고 기동을 막는다.
SET @world_spawn_existing_columns_exact = IF(
    @world_spawn_table_exists = 0,
    1,
    IF((SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'worlds'
          AND column_name IN ('spawn_x', 'spawn_y', 'spawn_z')
          AND data_type = 'int'
          AND column_type = 'int'
          AND is_nullable = 'YES'
          AND column_default IS NULL
          AND extra = ''
          AND COALESCE(generation_expression, '') = ''
          AND numeric_precision = 10
          AND numeric_scale = 0
          AND character_set_name IS NULL
          AND collation_name IS NULL)
       = (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name = 'worlds'
            AND column_name IN ('spawn_x', 'spawn_y', 'spawn_z')),
       1,
       0));
SELECT CASE
    WHEN @world_spawn_existing_columns_exact = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @world_spawn_x_missing = @world_spawn_table_exists = 1 AND NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'worlds' AND column_name = 'spawn_x');
SET @world_spawn_y_missing = @world_spawn_table_exists = 1 AND NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'worlds' AND column_name = 'spawn_y');
SET @world_spawn_z_missing = @world_spawn_table_exists = 1 AND NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'worlds' AND column_name = 'spawn_z');

SET @world_spawn_ddl = IF(
    @world_spawn_x_missing OR @world_spawn_y_missing OR @world_spawn_z_missing,
    CONCAT('ALTER TABLE `worlds`',
        TRIM(TRAILING ',' FROM CONCAT(
            IF(@world_spawn_x_missing, ' ADD COLUMN `spawn_x` INT NULL,', ''),
            IF(@world_spawn_y_missing, ' ADD COLUMN `spawn_y` INT NULL,', ''),
            IF(@world_spawn_z_missing, ' ADD COLUMN `spawn_z` INT NULL,', '')))),
    'SELECT 1');
PREPARE world_spawn_ddl_statement FROM @world_spawn_ddl;
EXECUTE world_spawn_ddl_statement;
DEALLOCATE PREPARE world_spawn_ddl_statement;

-- 더한 컬럼을 이름만으로 믿지 않고 다시 읽어 모양까지 확인한다.
SET @world_spawn_postcondition_exact = IF(
    @world_spawn_table_exists = 0,
    1,
    IF((SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'worlds'
          AND column_name IN ('spawn_x', 'spawn_y', 'spawn_z')
          AND data_type = 'int'
          AND column_type = 'int'
          AND is_nullable = 'YES'
          AND column_default IS NULL
          AND extra = ''
          AND COALESCE(generation_expression, '') = ''
          AND numeric_precision = 10
          AND numeric_scale = 0
          AND character_set_name IS NULL
          AND collation_name IS NULL) = 3,
       1,
       0));
SELECT CASE
    WHEN @world_spawn_postcondition_exact = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;
