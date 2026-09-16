package com.gameexpert.cluster;

import static com.gameexpert.ws.SessionAttributes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gameexpert.api.PresenceOperations;
import com.gameexpert.ws.GameTransport;
import com.gameexpert.ws.SessionCleanup;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

class ClusterCommandReplyTest {
    private ClusterRuntime cluster;
    private ClusterRuntime.Edge edge;
    private GameTransport transport;
    private StringRedisTemplate redis;
    private CompletableFuture<String> reply;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepare() {
        transport = mock(GameTransport.class);
        redis = mock(StringRedisTemplate.class);
        ObjectProvider<GameTransport> transportProvider = mock(ObjectProvider.class);
        when(transportProvider.getObject()).thenReturn(transport);
        cluster = new ClusterRuntime(mock(WorldAuthority.class), mock(AuthoritySessions.class), redis,
                new ObjectMapper(), mock(ObjectProvider.class), transportProvider,
                mock(ObjectProvider.class), mock(ObjectProvider.class), mock(SessionCleanup.class),
                mock(PresenceOperations.class), List.of(), mock(ObjectProvider.class),
                mock(ObjectProvider.class), mock(ObjectProvider.class), mock(ApplicationContext.class));
        WebSocketSession physical = mock(WebSocketSession.class);
        when(physical.getId()).thenReturn("physical-test");
        when(physical.isOpen()).thenReturn(true);
        Map<String, Object> attributes = Map.of(ATTR_WORLD_ID, 1L, ATTR_NICKNAME, "Alice");
        when(physical.getAttributes()).thenReturn(attributes);
        edge = cluster.new Edge(physical, new WorldAuthority.Owner(1L, "owner", 3L));
        edge.current = new EdgeSession(edge, 0, attributes);
        edge.ready = true;
        Map<String, ClusterRuntime.Edge> edges = (Map<String, ClusterRuntime.Edge>)
                ReflectionTestUtils.getField(cluster, "wireEdges");
        edges.put(edge.wire, edge);
        reply = new CompletableFuture<>();
        edge.queries.put(42L, reply);
    }

    @AfterEach
    void cleanup() throws Exception {
        edge.mailbox.close();
        Thread worker = (Thread) ReflectionTestUtils.getField(edge.mailbox, "worker");
        worker.join(2000);
    }

    @Test
    void commandReplyDoesNotWaitForBlockedGameFrame() throws Exception {
        synchronized (edge.transportLock) {
            blockFrame();
            receive(packet("result", 42L, "position"));
            assertEquals("position", reply.get(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void remoteCloseFailsPendingQueryBeforeBlockedFrameCanDrain() throws Exception {
        synchronized (edge.transportLock) {
            blockFrame();
            receive(packet("closed", 0L, ""));
            receive(packet("result", 42L, "late position"));
            assertThrows(ExecutionException.class, () -> reply.get(1, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> edge.query("/pos"));
            verifyNoInteractions(redis);
        }
    }

    @Test
    void replyMustMatchConnectionAndAuthorityIncarnation() throws Exception {
        synchronized (edge.transportLock) {
            blockFrame();
            receive(new ClusterRuntime.Packet("result", "other", edge.wire, 1, 3, 42, 1, "wrong node", false, 0));
            receive(new ClusterRuntime.Packet("result", "owner", edge.wire, 2, 3, 42, 1, "wrong root", false, 0));
            receive(new ClusterRuntime.Packet("result", "owner", edge.wire, 1, 2, 42, 1, "old epoch", false, 0));
            receive(new ClusterRuntime.Packet("result", "owner", "other", 1, 3, 42, 1, "wrong wire", false, 0));
            receive(packet("result", 41L, "wrong sequence"));
            assertFalse(reply.isDone());
            receive(packet("result", 42L, "position"));
            assertEquals("position", reply.get(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void gameFramesKeepTheirArrivalOrder() throws Exception {
        receive(packet("frame", 1L, "{\"type\":\"time\",\"value\":1}"));
        receive(packet("frame", 2L, "{\"type\":\"time\",\"value\":2}"));
        CountDownLatch drained = new CountDownLatch(1);
        assertTrue(edge.mailbox.offer(drained::countDown));
        assertTrue(drained.await(2, TimeUnit.SECONDS));
        org.mockito.InOrder order = inOrder(transport);
        order.verify(transport).sendTo(eq(edge.current), argThat((tools.jackson.databind.JsonNode node) -> node.path("value").asInt() == 1));
        order.verify(transport).sendTo(eq(edge.current), argThat((tools.jackson.databind.JsonNode node) -> node.path("value").asInt() == 2));
    }

    @Test
    void actualQueryCompletesWhileCallerHoldsTransportLock() throws Exception {
        synchronized (edge.queryLock) { edge.queries.clear(); }
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    ClusterRuntime.Packet request = new ObjectMapper().readValue(
                            (String) invocation.getArgument(2), ClusterRuntime.Packet.class);
                    receive(packet("result", request.sequence(), "position"));
                    return 1L;
                });
        synchronized (edge.transportLock) {
            blockFrame();
            assertEquals("position", edge.query("/pos"));
        }
        synchronized (edge.queryLock) { assertTrue(edge.queries.isEmpty()); }
    }

    @Test
    void concurrentQueriesCorrelateOutOfOrderReplies() throws Exception {
        synchronized (edge.queryLock) { edge.queries.clear(); }
        BlockingQueue<ClusterRuntime.Packet> sent = new LinkedBlockingQueue<>();
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    sent.add(new ObjectMapper().readValue((String) invocation.getArgument(2), ClusterRuntime.Packet.class));
                    return 1L;
                });
        try (ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> calls = java.util.stream.IntStream.range(0, 32)
                    .mapToObj(i -> workers.submit(() -> edge.query("query-" + i))).toList();
            java.util.ArrayList<ClusterRuntime.Packet> requests = new java.util.ArrayList<>();
            for (int i = 0; i < 32; i++) {
                ClusterRuntime.Packet request = sent.poll(2, TimeUnit.SECONDS);
                assertNotNull(request);
                requests.add(request);
            }
            java.util.Collections.reverse(requests);
            requests.forEach(request -> receive(packet("result", request.sequence(), request.payload())));
            for (int i = 0; i < 32; i++) assertEquals("query-" + i, calls.get(i).get(1, TimeUnit.SECONDS));
        }
        synchronized (edge.queryLock) { assertTrue(edge.queries.isEmpty()); }
    }

    @Test
    void failedSendDoesNotLeakPendingQuery() {
        synchronized (edge.queryLock) { edge.queries.clear(); }
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("transport unavailable"));
        assertThrows(IllegalStateException.class, () -> edge.query("/pos"));
        synchronized (edge.queryLock) { assertTrue(edge.queries.isEmpty()); }
    }

    @Test
    void localTerminationReleasesWaitingQueryAndRejectsNewRequests() throws Exception {
        synchronized (edge.queryLock) { edge.queries.clear(); }
        CountDownLatch sent = new CountDownLatch(1);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), any(Object[].class)))
                .thenAnswer(invocation -> { sent.countDown(); return 1L; });
        try (ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> call = worker.submit(() -> edge.query("/pos"));
            assertTrue(sent.await(2, TimeUnit.SECONDS));
            edge.terminate(org.springframework.web.socket.CloseStatus.NORMAL);
            assertThrows(ExecutionException.class, () -> call.get(1, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> edge.query("/pos"));
        }
        synchronized (edge.queryLock) { assertTrue(edge.queries.isEmpty()); }
    }

    @Test
    void remoteCloseReleasesActualQueryWhileFrameIsBlocked() throws Exception {
        synchronized (edge.queryLock) { edge.queries.clear(); }
        CountDownLatch sent = new CountDownLatch(1);
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), any(Object[].class)))
                .thenAnswer(invocation -> { sent.countDown(); return 1L; });
        try (ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor()) {
            synchronized (edge.transportLock) {
                blockFrame();
                Future<String> call = worker.submit(() -> edge.query("/pos"));
                assertTrue(sent.await(2, TimeUnit.SECONDS));
                receive(packet("closed", 0L, ""));
                assertThrows(ExecutionException.class, () -> call.get(1, TimeUnit.SECONDS));
                assertThrows(IllegalStateException.class, () -> edge.query("/pos"));
                synchronized (edge.queryLock) { assertTrue(edge.queries.isEmpty()); }
            }
        }
    }

    private void blockFrame() throws Exception {
        receive(packet("frame", 1L, "{\"type\":\"time\"}"));
        Thread worker = (Thread) ReflectionTestUtils.getField(edge.mailbox, "worker");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (worker.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertEquals(Thread.State.BLOCKED, worker.getState(), "frame must be waiting on transportLock");
    }

    private ClusterRuntime.Packet packet(String kind, long sequence, String payload) {
        return new ClusterRuntime.Packet(kind, "owner", edge.wire, 1, 3, sequence, 1, payload, false, 1000);
    }

    private void receive(ClusterRuntime.Packet packet) {
        ReflectionTestUtils.invokeMethod(cluster, "receivePacket", packet);
    }
}
