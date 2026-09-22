package com.gameexpert.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.gameexpert.authority.versioned.NeutralFinalChunk;
import com.gameexpert.authority.versioned.ProducerAuthorities;
import com.gameexpert.terrain.TerrainAccessor;
import com.gameexpert.world.WorldGenerationProfiles;
import java.io.InputStream;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/** Lanes without LOOT or entity rows are projected locally; the producer must agree with that projection. */
class LocalLaneProjectionTest {
    @Test
    void localProjectionEqualsTheProducerSubset() throws Exception {
        byte[] raw;
        try (InputStream input = getClass().getResourceAsStream("/fixtures/spawner-generic-skeleton.bin")) {
            raw = input.readAllBytes();
        }
        NeutralFinalChunk carrier = ProducerAuthorities.verify(WorldGenerationProfiles.CURRENT, 9, -4, raw);
        Method project = WorldRuntime.class.getDeclaredMethod("projectFinalCarrierSidecars",
                NeutralFinalChunk.class, TerrainAccessor.FinalLiveCarrierLane.class);
        project.setAccessible(true);
        int checked = 0;
        for (TerrainAccessor.FinalLiveCarrierLane lane : TerrainAccessor.FinalLiveCarrierLane.values()) {
            if (lane == TerrainAccessor.FinalLiveCarrierLane.LOOT
                    || lane == TerrainAccessor.FinalLiveCarrierLane.ENTITIES) continue;
            NeutralFinalChunk.Sidecars local = (NeutralFinalChunk.Sidecars) project.invoke(null, carrier, lane);
            NeutralFinalChunk.Sidecars remote = carrier.withSidecars(local).sidecars();
            assertEquals(remote, local, "lane " + lane);
            checked++;
        }
        assertTrue(checked >= 7);
        assertFalse(carrier.sidecars().spawners().isEmpty(), "fixture must carry a spawner row");
    }
}
