SET @canonical_payload_ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'canonical_worldgen_chunks'
          AND column_name IN ('final_carrier', 'structure_carrier', 'mutable_piece_successor')
          AND data_type <> 'longblob'
    ),
    'ALTER TABLE canonical_worldgen_chunks MODIFY final_carrier LONGBLOB NOT NULL, MODIFY structure_carrier LONGBLOB NOT NULL, MODIFY mutable_piece_successor LONGBLOB NULL',
    'SELECT 1'
);
PREPARE canonical_payload_statement FROM @canonical_payload_ddl;
EXECUTE canonical_payload_statement;
DEALLOCATE PREPARE canonical_payload_statement;

SET @canonical_payload_ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'final_carrier_lane_mutations'
          AND column_name = 'typed_payload'
          AND data_type <> 'longblob'
    ),
    'ALTER TABLE final_carrier_lane_mutations MODIFY typed_payload LONGBLOB NOT NULL',
    'SELECT 1'
);
PREPARE canonical_payload_statement FROM @canonical_payload_ddl;
EXECUTE canonical_payload_statement;
DEALLOCATE PREPARE canonical_payload_statement;

SET @canonical_payload_ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'world_canonical_bee_installations'
          AND column_name = 'canonical_receipt'
          AND data_type <> 'longblob'
    ),
    'ALTER TABLE world_canonical_bee_installations MODIFY canonical_receipt LONGBLOB NOT NULL',
    'SELECT 1'
);
PREPARE canonical_payload_statement FROM @canonical_payload_ddl;
EXECUTE canonical_payload_statement;
DEALLOCATE PREPARE canonical_payload_statement;

SET @canonical_payload_ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'world_canonical_bee_installations'
          AND column_name = 'occupant_payload'
          AND data_type <> 'longblob'
    ),
    'ALTER TABLE world_canonical_bee_installations MODIFY occupant_payload LONGBLOB NOT NULL',
    'SELECT 1'
);
PREPARE canonical_payload_statement FROM @canonical_payload_ddl;
EXECUTE canonical_payload_statement;
DEALLOCATE PREPARE canonical_payload_statement;

SET @canonical_payload_ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'world_archaeology_brushables'
          AND column_name = 'canonical_receipt_bytes'
          AND data_type <> 'longblob'
    ),
    'ALTER TABLE world_archaeology_brushables MODIFY canonical_receipt_bytes LONGBLOB NOT NULL',
    'SELECT 1'
);
PREPARE canonical_payload_statement FROM @canonical_payload_ddl;
EXECUTE canonical_payload_statement;
DEALLOCATE PREPARE canonical_payload_statement;

SET @canonical_payload_ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'world_structure_entities'
          AND column_name = 'canonical_payload'
          AND data_type <> 'longblob'
    ),
    'ALTER TABLE world_structure_entities MODIFY canonical_payload LONGBLOB NOT NULL',
    'SELECT 1'
);
PREPARE canonical_payload_statement FROM @canonical_payload_ddl;
EXECUTE canonical_payload_statement;
DEALLOCATE PREPARE canonical_payload_statement;

SET @canonical_payload_ddl = IF(
    EXISTS(
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'world_canonical_loot_assignments'
          AND column_name = 'resolved_payload'
          AND data_type <> 'longblob'
    ),
    'ALTER TABLE world_canonical_loot_assignments MODIFY resolved_payload LONGBLOB NULL',
    'SELECT 1'
);
PREPARE canonical_payload_statement FROM @canonical_payload_ddl;
EXECUTE canonical_payload_statement;
DEALLOCATE PREPARE canonical_payload_statement;
