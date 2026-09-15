-- 설치된 보트를 담을 `world_boats` 테이블. 없을 때만 만들고, 이미 있으면 한 줄도 건드리지 않는다.
--
-- 기존 저장 월드에는 보트 행이 하나도 없다(그때는 보트가 인메모리 일시 엔티티였다). 그래서
-- 마이그레이션할 데이터가 없고, 빈 테이블로 시작하는 것이 정확한 시작 상태다. 옛 월드는 보트가
-- 없는 채로 열리고, 그 월드에서 새로 설치한 보트부터 여기에 새겨진다.
--
-- Hibernate(`ddl-auto=update`)도 같은 엔티티로 이 테이블을 만들 수 있지만, 그때 컬럼 순서는
-- 알파벳순이 된다. 이 스크립트가 Hibernate 보다 먼저 돌아 계약된 컬럼 순서와 유니크 키를
-- 확정하고, 뒤이어 도는 Hibernate 는 이미 맞는 모양을 보고 아무것도 바꾸지 않는다.

CREATE TABLE IF NOT EXISTS `world_boats` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `world_id` BIGINT NOT NULL,
    `boat_id` BIGINT NOT NULL,
    `pos_x` DOUBLE NOT NULL,
    `pos_y` DOUBLE NOT NULL,
    `pos_z` DOUBLE NOT NULL,
    `yaw` DOUBLE NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_world_boat_id` (`world_id`, `boat_id`)
) ENGINE=InnoDB;

-- 이름만으로 믿지 않고 다시 읽어 모양까지 확인한다. 계약과 다르면 조용히 고치지 않고 기동을 막는다.
SET @world_boats_table_exact = EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'world_boats'
      AND table_type = 'BASE TABLE'
      AND engine = 'InnoDB');
SELECT CASE WHEN @world_boats_table_exact = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

SET @world_boats_columns_exact = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'world_boats'
      AND column_name IN ('world_id', 'boat_id', 'pos_x', 'pos_y', 'pos_z', 'yaw')
      AND is_nullable = 'NO'
      AND column_default IS NULL
      AND extra = ''
      AND COALESCE(generation_expression, '') = '') = 6;
SELECT CASE WHEN @world_boats_columns_exact = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;

-- (world_id, boat_id) 는 보트의 정체성이다. 유니크가 아니면 같은 보트가 두 행이 되어
-- 재입장 스냅샷에 중복 보트가 나타난다.
SET @world_boats_identity_unique = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'world_boats'
      AND index_name = 'uk_world_boat_id'
      AND non_unique = 0) = 2;
SELECT CASE WHEN @world_boats_identity_unique = 1
    THEN 1 ELSE REGEXP_LIKE('x', '[') END;
