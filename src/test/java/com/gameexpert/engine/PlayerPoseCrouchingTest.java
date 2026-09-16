package com.gameexpert.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gameexpert.ws.dto.WsMessages.PlayerPose;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class PlayerPoseCrouchingTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void legacyConstructorExplicitlySerializesStandingState() {
        PlayerPose pose = new PlayerPose("Alice", 1, 64, 2, 30, 10,
                false, (short) 0, (short) 0);
        assertFalse(pose.isCrouching());
        assertWireCrouching(pose, false);
    }

    @Test
    void stationaryCrouchAndStandTransitionsProduceDirtyAuthoritativeSnapshots() {
        PlayerTickState player = new PlayerTickState(1L, "Alice", 1, 64, 2, 30, 10, 20);
        assertWireCrouching(player.pose(), false);
        player.consumePoseDirty();
        assertFalse(player.poseDirty());

        player.applyPose(1, 64, 2, 30, 10, true);
        assertTrue(player.crouching());
        assertTrue(player.poseDirty());
        PlayerPose crouched = player.pose();
        assertTrue(crouched.isCrouching());
        assertWireCrouching(crouched, true);
        player.consumePoseDirty();

        player.applyPose(1, 64, 2, 30, 10, false);
        assertFalse(player.crouching());
        assertTrue(player.poseDirty());
        PlayerPose standing = player.pose();
        assertFalse(standing.isCrouching());
        assertWireCrouching(standing, false);
        assertTrue(crouched.isCrouching(), "Previously captured snapshots must retain their stance");
        assertEquals(crouched.getX(), standing.getX());
        assertEquals(crouched.getY(), standing.getY());
        assertEquals(crouched.getZ(), standing.getZ());
    }

    private void assertWireCrouching(PlayerPose pose, boolean expected) {
        JsonNode serialized = mapper.readTree(mapper.writeValueAsString(pose));
        assertTrue(serialized.has("crouching"), "Standing state must explicitly clear remote crouching");
        assertTrue(serialized.get("crouching").isBoolean());
        assertEquals(expected, serialized.get("crouching").asBoolean());
    }
}
