package com.gameexpert.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/** The per-ID tables must answer exactly what the rules they cache answer. */
class BlockPredicateTableTest {
    @Test
    void tablesMatchTheRules() throws Exception {
        Method slab = BuildingBlockRules.class.getDeclaredMethod("isSlabById", int.class);
        Method solid = Fluids.class.getDeclaredMethod("isSolidById", int.class);
        slab.setAccessible(true);
        solid.setAccessible(true);
        int slabs = 0;
        for (int id = -2; id < 5000; id++) {
            assertEquals(slab.invoke(null, id), BuildingBlockRules.isSlab(id), "slab " + id);
            assertEquals(solid.invoke(null, id), Fluids.isSolid(id), "solid " + id);
            if (BuildingBlockRules.isSlab(id)) slabs++;
        }
        assertTrue(slabs > 30);
    }
}
