package com.gameexpert.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Every warmed name must exist, or the warm-up quietly stops covering it. */
class EngineWarmupTest {
    @Test
    void everyTableNameResolvesAndLoads() throws Exception {
        for (String name : EngineWarmup.TABLES) {
            assertNotNull(Class.forName(name, false, EngineWarmupTest.class.getClassLoader()), name);
        }
        assertFalse(EngineWarmup.TABLES.isEmpty());
        EngineWarmup.loadTables();
    }
}
