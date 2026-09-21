package com.gameexpert.ws;

import static com.gameexpert.ws.SessionAttributes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.gameexpert.api.PresenceOperations;
import com.gameexpert.api.SessionRegistry;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.state.service.DimensionTravelPersistence;
import com.gameexpert.state.service.PlayerDimensionIdentity;
import com.gameexpert.world.dimension.DimensionProviders;
import com.gameexpert.world.dimension.DimensionRegistry;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

/** A player who closes the tab and comes straight back must not be refused as a duplicate. */
class DimensionRejoinGraceTest {
    private DimensionTravelCoordinator coordinator;

    @BeforeEach
    void prepare() {
        DimensionTravelPersistence travel = mock(DimensionTravelPersistence.class);
        when(travel.current(1L, 7L)).thenReturn(new PlayerDimensionIdentity(1, 1, 0));
        coordinator = new DimensionTravelCoordinator(travel, mock(DimensionRegistry.class),
                mock(DimensionProviders.class), mock(WorldEngineManager.class), mock(SessionRegistry.class),
                mock(GameTransport.class), mock(PresenceOperations.class), mock(WorldSessionLifecycle.class),
                new ObjectMapper());
    }

    @AfterEach
    void cleanup() { coordinator.shutdown(); }

    private static WebSocketSession physical(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(Map.of(ATTR_WORLD_ID, 1L, ATTR_PLAYER_ID, 7L, ATTR_NICKNAME, "Alice"));
        return session;
    }

    @Test
    void reconnectWaitsForTheClosedTabToLeave() throws Exception {
        WebSocketSession closedTab = physical("closed-tab");
        coordinator.open(closedTab);
        WebSocketSession reconnect = physical("reconnect");

        CompletableFuture<DimensionSession> opened = CompletableFuture.supplyAsync(() -> {
            try { return coordinator.open(reconnect); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        });
        Thread.sleep(300);
        assertFalse(opened.isDone(), "the reconnect must wait while the old tab is still leaving");

        coordinator.close(closedTab);

        DimensionSession session = opened.get(3, TimeUnit.SECONDS);
        assertNotNull(session);
        verify(reconnect, never()).close(any(CloseStatus.class));
    }

    @Test
    void secondLiveConnectionIsStillRefusedAfterTheGrace() throws Exception {
        coordinator.open(physical("live"));
        WebSocketSession duplicate = physical("duplicate");

        long started = System.nanoTime();
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> coordinator.open(duplicate));
        long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertEquals("root player already connected", refused.getMessage());
        verify(duplicate).close(new CloseStatus(4002));
        assertTrue(waitedMillis >= 4_900 && waitedMillis < 8_000, "refused after " + waitedMillis + "ms");
    }
}
