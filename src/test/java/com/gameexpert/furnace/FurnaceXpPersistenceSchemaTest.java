package com.gameexpert.furnace;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class FurnaceXpPersistenceSchemaTest {
    private static final Path SCHEMA = Path.of(
            "src/main/resources/db/mysql/V006__world_furnace_xp.sql");
    private static final Pattern PREPARE = Pattern.compile(
            "\\bPREPARE\\s+([A-Za-z0-9_]+)\\s+FROM\\b");

    @Test
    void permitsOnlyTheZeroDefaultAddToTheExactWorldFurnaceShape() throws IOException {
        String sql = normalized();

        assertThat(sql).contains(
                "`world_furnaces`",
                "ADD COLUMN `xp_milli` INT NOT NULL DEFAULT 0 AFTER `persistence_revision`",
                "@furnace_xp_table_exists = 1",
                "@furnace_xp_column_exists = 0",
                "@furnace_xp_base_columns_exact",
                "@furnace_xp_existing_column_exact",
                "COUNT(*)",
                "AND column_name <> 'xp_milli'",
                "AND column_name NOT IN",
                "@furnace_stack_column_count IN (0, 18)",
                "@furnace_stack_columns_exact = 1",
                "column_name = 'id' AND data_type = 'bigint' AND column_type = 'bigint'",
                "COALESCE(generation_expression, '') = ''",
                "column_default = '0'");
        assertThat(occurrences(sql, "ADD COLUMN `xp_milli`")).isOne();
        assertThat(sql).contains("@furnace_stack_collation_changes", "column_type = 'varchar(512)'", "column_type = 'longtext'", "collation_name = 'utf8mb4_0900_ai_ci'");
        assertThat(sql).doesNotContain(
                "CREATE TABLE", "DROP TABLE", "TRUNCATE", "DELETE FROM", "INSERT INTO",
                "UPDATE ");
    }

    @Test
    void acceptsAnExactColumnAndRejectsShapeOrColumnDriftBeforeAlter() throws IOException {
        String sql = normalized();
        int assertion = sql.indexOf("AND @furnace_xp_constraints_exact = 1");
        int alter = sql.indexOf("ADD COLUMN `xp_milli`");

        assertThat(assertion).isGreaterThanOrEqualTo(0);
        assertThat(alter).isGreaterThan(assertion);
        assertThat(sql).contains(
                "@furnace_xp_table_object_exists = 0",
                "@furnace_xp_table_exists = 1",
                "engine = 'InnoDB'",
                "table_collation = 'utf8mb4_unicode_ci'",
                "COALESCE(create_options, '') = ''",
                ") = 18",
                ") = 19",
                "data_type = 'int' AND column_type = 'int'",
                "is_nullable = 'NO' AND column_default = '0' AND extra = ''",
                "@furnace_xp_indexes_exact",
                "@furnace_xp_constraints_exact",
                "REGEXP_LIKE('x', '[')");
        assertThat(occurrences(sql, "@furnace_xp_existing_column_exact = 1")).isOne();
        assertThat(occurrences(sql, "@furnace_xp_existing_column_exact = 0")).isZero();
    }

    @Test
    void balancesTheBoundedLockAndEveryPreparedStatement() throws IOException {
        String sql = normalized();

        assertThat(sql).contains(
                "SET @furnace_xp_lock_timeout_seconds = 30",
                "@furnace_xp_lock_timeout_seconds BETWEEN 1 AND 60",
                "GET_LOCK(@furnace_xp_lock_name, @furnace_xp_lock_timeout_seconds)",
                "REPLACE(@furnace_xp_database, '`', '``')",
                "RELEASE_LOCK(@furnace_xp_lock_name)");
        assertThat(sql.indexOf("GET_LOCK(")).isLessThan(sql.indexOf("information_schema."));
        assertThat(sql.indexOf("RELEASE_LOCK(")).isGreaterThan(sql.lastIndexOf("information_schema."));
        assertThat(occurrences(sql, "GET_LOCK(")).isOne();
        assertThat(occurrences(sql, "RELEASE_LOCK(")).isOne();

        Matcher matcher = PREPARE.matcher(sql);
        int count = 0;
        while (matcher.find()) {
            count++;
            String name = matcher.group(1);
            int prepare = matcher.start();
            int execute = sql.indexOf("EXECUTE " + name + ";", prepare);
            int deallocate = sql.indexOf("DEALLOCATE PREPARE " + name + ";", execute);
            assertThat(execute).isGreaterThan(prepare);
            assertThat(deallocate).isGreaterThan(execute);
        }
        assertThat(count).isPositive();
        assertThat(occurrences(sql, "EXECUTE ")).isEqualTo(count);
        assertThat(occurrences(sql, "DEALLOCATE PREPARE ")).isEqualTo(count);
    }

    private static String normalized() throws IOException {
        return Files.readString(SCHEMA).replaceAll("\\s+", " ").trim();
    }

    private static int occurrences(String source, String target) {
        int count = 0;
        for (int at = 0; (at = source.indexOf(target, at)) >= 0; at += target.length()) count++;
        return count;
    }
}
