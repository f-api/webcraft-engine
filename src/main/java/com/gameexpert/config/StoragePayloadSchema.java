package com.gameexpert.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/** Expands existing saves after ORM schema initialization, under the shared startup lock. */
@Component
@DependsOn("entityManagerFactory")
@RequiredArgsConstructor
public final class StoragePayloadSchema implements SmartInitializingSingleton {
    private final DataSource dataSource;

    @Override
    public void afterSingletonsInstantiated() {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL")) {
                return;
            }
            for (String table : List.of("player_inventory_items", "world_chest_items", "shulker_contents_items",
                    "world_dispenser_items", "world_generated_structure_entity_cargo",
                    "world_placed_entity_items", "world_enchanting_table_items")) {
                expand(connection, table, "bucket_mob_data");
                expand(connection, table, "item_component_data");
            }
            expand(connection, "world_trial_sites", "ledger_payload");
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot expand saved item and Trial payload storage", failure);
        }
    }

    private static void expand(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT data_type, is_nullable FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """)) {
            query.setString(1, table);
            query.setString(2, column);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Missing payload column: " + table + "." + column);
                }
                if ("longtext".equals(result.getString(1))) {
                    return;
                }
                String nullable = "YES".equals(result.getString(2)) ? "NULL" : "NOT NULL";
                try (Statement alter = connection.createStatement()) {
                    alter.execute("ALTER TABLE `" + table + "` MODIFY `" + column + "` LONGTEXT " + nullable);
                }
            }
        }
    }
}
