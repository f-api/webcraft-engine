package com.gameexpert.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.gameexpert.terrain.Blocks;
import org.junit.jupiter.api.Test;

/** Skipping the state lookup is only allowed where every state produces the same collision boxes. */
class CollisionStateFreeTest {
    @Test
    void stateFreeOnlyWhereBoxesNeverChange() {
        assertTrue(BuildingBlockRules.collisionIgnoresState(Blocks.AIR));
        assertTrue(BuildingBlockRules.collisionIgnoresState(Blocks.STONE));
        assertFalse(BuildingBlockRules.collisionIgnoresState(Blocks.OAK_SLAB));
        int dependent = 0;
        for (int id = 0; id < 4096; id++) {
            if (!Blocks.isWorldBlockId(id) || BuildingBlockRules.collisionIgnoresState(id)) continue;
            dependent++;
        }
        assertTrue(dependent > 20, "stairs, slabs, doors and the like depend on state: " + dependent);
    }
}
