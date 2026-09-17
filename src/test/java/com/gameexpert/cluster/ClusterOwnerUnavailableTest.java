package com.gameexpert.cluster;

import static com.gameexpert.ws.SessionAttributes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gameexpert.api.PresenceOperations;
import com.gameexpert.ws.ConnectionEndpoint;
import com.gameexpert.ws.DimensionTravelCoordinator;
import com.gameexpert.ws.GameTransport;
import com.gameexpert.ws.SessionCleanup;
import com.gameexpert.engine.Difficulty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

class ClusterOwnerUnavailableTest {
    private final WorldAuthority.Owner owner = new WorldAuthority.Owner(1L, "crashed-owner", 4L);
    private ClusterRuntime cluster;
    private StringRedisTemplate redis;
    private WebSocketSession physical;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepare() {
        redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(mock(ValueOperations.class));
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);
        WorldAuthority authority = mock(WorldAuthority.class);
        when(authority.resolve(1L)).thenReturn(owner);
        when(authority.namespace()).thenReturn("test");
        when(authority.retiredRoots()).thenReturn(java.util.Set.of());
        ObjectProvider<GameTransport> transport = mock(ObjectProvider.class);
        when(transport.getObject()).thenReturn(mock(GameTransport.class));
        cluster = new ClusterRuntime(authority, mock(AuthoritySessions.class), redis, new ObjectMapper(),
                mock(ObjectProvider.class), transport, mock(ObjectProvider.class), mock(ObjectProvider.class),
                mock(SessionCleanup.class), mock(PresenceOperations.class), List.of(), mock(ObjectProvider.class),
                mock(ObjectProvider.class), mock(ObjectProvider.class), mock(ApplicationContext.class));
        physical = mock(WebSocketSession.class);
        when(physical.getId()).thenReturn("physical-owner-unavailable");
        when(physical.isOpen()).thenReturn(true);
        Map<String, Object> attributes = new HashMap<>(Map.of(ATTR_WORLD_ID, 1L, ATTR_NICKNAME, "Alice",
                ATTR_PLAYER_ID, 7L, ATTR_WORLD_SEED, 42, ATTR_WORLD_DIFFICULTY, Difficulty.NORMAL));
        when(physical.getAttributes()).thenReturn(attributes);
    }

    @Test
    void crashedOwnerDetectedByHealthCheckIsReportedAsUnavailable() throws Exception {
        when(redis.hasKey(anyString())).thenReturn(false);
        Future<?> open = openInBackground();
        awaitEdge();
        ReflectionTestUtils.invokeMethod(cluster, "maintain");
        ExecutionException failure = assertThrows(ExecutionException.class, () -> open.get(5, TimeUnit.SECONDS));
        WorldAuthorityUnavailableException unavailable =
                assertInstanceOf(WorldAuthorityUnavailableException.class, failure.getCause());
        assertEquals("ENGINE_OWNER_LOST", unavailable.reason());
        assertEquals("crashed-owner", unavailable.owner());
        verify(physical).close(new CloseStatus(1012, "ENGINE_OWNER_LOST"));
    }

    @Test
    void ownerRejectingStaleGenerationIsReportedAsUnavailable() throws Exception {
        Future<?> open = openInBackground();
        ClusterRuntime.Edge edge = awaitEdge();
        ReflectionTestUtils.invokeMethod(cluster, "receivePacket", new ClusterRuntime.Packet("closed", "crashed-owner",
                edge.wire, 1L, 4L, 0L, 1L, "WORLD_AUTHORITY_UNAVAILABLE", false, 1012));
        ExecutionException failure = assertThrows(ExecutionException.class, () -> open.get(5, TimeUnit.SECONDS));
        assertEquals("WORLD_AUTHORITY_UNAVAILABLE",
                assertInstanceOf(WorldAuthorityUnavailableException.class, failure.getCause()).reason());
    }

    @Test
    void otherRemoteClosesKeepTheirOriginalFailure() throws Exception {
        Future<?> open = openInBackground();
        ClusterRuntime.Edge edge = awaitEdge();
        ReflectionTestUtils.invokeMethod(cluster, "receivePacket", new ClusterRuntime.Packet("closed", "crashed-owner",
                edge.wire, 1L, 4L, 0L, 1L, "", false, 1013));
        ExecutionException failure = assertThrows(ExecutionException.class, () -> open.get(5, TimeUnit.SECONDS));
        assertFalse(failure.getCause() instanceof WorldAuthorityUnavailableException);
    }

    @Test
    void endpointProxyClosesUnavailableConnectionWithoutRethrowing() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        Endpoint endpoint = new Endpoint(new WorldAuthorityUnavailableException(1L, "crashed-owner", "ENGINE_OWNER_LOST", null));
        Endpoint proxy = (Endpoint) new ClusterEndpointPostProcessor().postProcessAfterInitialization(endpoint, "handler");
        assertNotSame(endpoint, proxy);
        assertDoesNotThrow(() -> proxy.afterConnectionEstablished(session));
        verify(session).close(new CloseStatus(1012, "ENGINE_OWNER_LOST"));
    }

    @Test
    void endpointProxyKeepsOtherFailures() {
        Endpoint proxy = (Endpoint) new ClusterEndpointPostProcessor().postProcessAfterInitialization(
                new Endpoint(new IllegalStateException("student bug")), "handler");
        assertThrows(IllegalStateException.class, () -> proxy.afterConnectionEstablished(mock(WebSocketSession.class)));
    }

    private Future<?> openInBackground() {
        return Executors.newVirtualThreadPerTaskExecutor().submit(() -> cluster.openPhysical(physical));
    }

    @SuppressWarnings("unchecked")
    private ClusterRuntime.Edge awaitEdge() throws InterruptedException {
        Map<String, ClusterRuntime.Edge> edges = (Map<String, ClusterRuntime.Edge>)
                ReflectionTestUtils.getField(cluster, "physicalEdges");
        for (int i = 0; i < 200 && edges.isEmpty(); i++) Thread.sleep(10);
        assertFalse(edges.isEmpty(), "edge was not opened");
        return edges.values().iterator().next();
    }

    static class Endpoint extends TextWebSocketHandler implements ConnectionEndpoint {
        private final RuntimeException failure;
        Endpoint(RuntimeException failure) { this.failure = failure; }
        @Override public void afterConnectionEstablished(WebSocketSession session) { throw failure; }
        @Override public void attachDimensions(DimensionTravelCoordinator dimensions) { }
    }
}
