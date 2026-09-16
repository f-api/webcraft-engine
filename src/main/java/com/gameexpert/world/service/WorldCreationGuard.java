package com.gameexpert.world.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Every server locks the same row before counting and inserting root worlds. */
@Component
@RequiredArgsConstructor
public final class WorldCreationGuard implements SmartInitializingSingleton {
    private final JdbcTemplate jdbc;

    @Override
    public void afterSingletonsInstantiated() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS webcraft_world_creation_guard (id INTEGER NOT NULL PRIMARY KEY)");
        try {
            jdbc.update("INSERT INTO webcraft_world_creation_guard(id) VALUES (1)");
        } catch (DuplicateKeyException existing) {
            // Another startup already installed the singleton row.
        }
    }

    void lock() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("World creation requires a transaction");
        }
        Integer row = jdbc.queryForObject(
                "SELECT id FROM webcraft_world_creation_guard WHERE id=1 FOR UPDATE", Integer.class);
        if (row == null || row != 1) {
            throw new IllegalStateException("World creation guard is missing");
        }
        // The caller's transaction retains this database lock through commit or rollback.
    }
}
