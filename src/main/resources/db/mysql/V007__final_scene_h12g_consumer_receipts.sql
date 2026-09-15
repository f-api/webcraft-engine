-- Durable idempotent H12g product/publication consumer receipts.
-- The phase is part of the key so a product and its later publication cannot alias.
CREATE TABLE IF NOT EXISTS `final_scene_h12g_consumer_receipts` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `version` BIGINT NOT NULL,
    `world_id` BIGINT NOT NULL,
    `source_identity` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `phase` VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `idempotency_key` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `operation_id` VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `authorization` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `request_fingerprint` LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `result` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_h12g_consumer_receipt_identity`
        UNIQUE (`world_id`, `source_identity`, `phase`, `idempotency_key`),
    CONSTRAINT `ck_h12g_consumer_receipt_identity` CHECK (
        `version` >= 0 AND `version` < 9223372036854775807
        AND `world_id` > 0 AND `world_id` < 9223372036854775807
        AND OCTET_LENGTH(`source_identity`) = 64
        AND REGEXP_LIKE(`source_identity`, '^[0-9a-f]{64}$', 'c')
        AND `phase` IN ('PRODUCT', 'PUBLICATION')
        AND OCTET_LENGTH(`idempotency_key`) = 64
        AND REGEXP_LIKE(`idempotency_key`, '^[0-9a-f]{64}$', 'c')
        AND OCTET_LENGTH(`operation_id`) BETWEEN 1 AND 512
        AND OCTET_LENGTH(`authorization`) BETWEEN 1 AND 524288
        AND OCTET_LENGTH(`request_fingerprint`) BETWEEN 1 AND 262144
        AND (
            (`phase` = 'PRODUCT' AND `result` IN
                ('SUCCESS', 'RETRY', 'REJECTED', 'CLASSIFIED_FAILURE'))
            OR (`phase` = 'PUBLICATION' AND `result` IN ('ACKNOWLEDGED', 'RETRY'))
        )
    )
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
