package com.gameexpert.cluster;

import static com.gameexpert.ws.SessionAttributes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gameexpert.api.PresenceOperations;
import com.gameexpert.api.SessionRegistry;
import com.gameexpert.state.service.PlayerDimensionIdentity;
import com.gameexpert.ws.*;
import com.gameexpert.ws.handler.EngineMessageHandler;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ClusterCommandTransitionTest {
    private ClusterRuntime cluster;
    private ClusterRuntime.Edge edge;
    private Object actor;
    private DimensionSession dimension;
    private ChatCommands commands;
    private final List<ClusterRuntime.Packet> sent = new CopyOnWriteArrayList<>();
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepare() throws Exception {
        WorldAuthority authority = mock(WorldAuthority.class);
        when(authority.accepts(any())).thenReturn(true);
        AuthoritySessions sessions = mock(AuthoritySessions.class);
        SessionRegistry local = mock(SessionRegistry.class);
        when(sessions.local()).thenReturn(local);
        commands = mock(ChatCommands.class);
        when(commands.resolve(anyLong(), anyString(), anyString())).thenReturn("position");
        ObjectProvider<ChatCommands> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(commands);
        StringRedisTemplate redis = mock(StringRedisTemplate.class, invocation -> {
            if (!invocation.getMethod().getName().equals("execute")) return RETURNS_DEFAULTS.answer(invocation);
            Object[] arguments = (Object[]) invocation.getRawArguments()[2];
            ClusterRuntime.Packet packet = json.readValue((String) arguments[0], ClusterRuntime.Packet.class);
            sent.add(packet);
            ReflectionTestUtils.invokeMethod(cluster, "receivePacket", packet);
            return 1L;
        });
        cluster = new ClusterRuntime(authority, sessions, redis, json, mock(ObjectProvider.class),
                mock(ObjectProvider.class), mock(ObjectProvider.class), provider, mock(SessionCleanup.class),
                mock(PresenceOperations.class), List.of(), mock(ObjectProvider.class), mock(ObjectProvider.class),
                mock(ObjectProvider.class), mock(ApplicationContext.class));
        WebSocketSession physical = mock(WebSocketSession.class);
        when(physical.getId()).thenReturn("transition-test");
        when(physical.isOpen()).thenReturn(true);
        Map<String, Object> attributes = Map.of(ATTR_WORLD_ID, 1L, ATTR_NICKNAME, "Alice");
        when(physical.getAttributes()).thenReturn(attributes);
        edge = cluster.new Edge(physical, new WorldAuthority.Owner(1, ClusterIdentity.NODE, 3));
        edge.current = new EdgeSession(edge, 0, attributes);
        edge.ready = true;
        ((Map<String, ClusterRuntime.Edge>) ReflectionTestUtils.getField(cluster, "wireEdges")).put(edge.wire, edge);
        when(local.get(1L, "Alice")).thenReturn(new SessionRegistry.Entry(edge.current));
        ClusterRuntime.Packet open = new ClusterRuntime.Packet("open", ClusterIdentity.NODE, edge.wire,
                1, 3, 0, 1, json.writeValueAsString(new ClusterRuntime.Identity("Alice", 1, 1, "NORMAL", 1)), false, 0);
        Class<?> actorClass = Arrays.stream(ClusterRuntime.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("Actor")).findFirst().orElseThrow();
        Constructor<?> constructor = actorClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        actor = constructor.newInstance(cluster, open, edge.owner);
        dimension = new DimensionSession(physical, new PlayerDimensionIdentity(1, 1, 0));
        dimension.welcomeComplete();
        ReflectionTestUtils.setField(actor, "session", dimension);
        ReflectionTestUtils.setField(actor, "joined", true);
        ((Map<String, Object>) ReflectionTestUtils.getField(cluster, "actors")).put(edge.wire, actor);
        ReflectionTestUtils.setField(cluster, "started", true);
        ReflectionTestUtils.setField(ClusterRuntime.class, "active", cluster);
    }

    @AfterEach
    void cleanup() throws Exception {
        ReflectionTestUtils.setField(ClusterRuntime.class, "active", null);
        closeMailbox(edge.mailbox);
        closeMailbox((SerialMailbox) ReflectionTestUtils.getField(actor, "mailbox"));
    }

    private void closeMailbox(SerialMailbox mailbox) throws Exception {
        mailbox.close();
        ((Thread) ReflectionTestUtils.getField(mailbox, "worker")).join(2000);
    }

    @Test
    void changedWorldRejectsCommandWithoutWaitingForTimeout() throws Exception {
        assertTrue(dimension.beginTransfer());
        dimension.transition(new PlayerDimensionIdentity(1, 2, "nether", 1), new org.springframework.web.socket.TextMessage("{}"));
        sendCommand();
        assertEquals(409, result().code());
        verifyNoInteractions(commands);
    }

    @Test
    void transferringSessionRejectsCommandEvenBeforeWorldChanges() throws Exception {
        assertTrue(dimension.beginTransfer());
        sendCommand();
        assertEquals(409, result().code());
        verifyNoInteractions(commands);
    }

    @Test
    void cancellationStopsLocalChatBeforeSaveAndRelay() {
        assertTrue(dimension.beginTransfer());
        AtomicInteger saves = new AtomicInteger();
        AtomicInteger relays = new AtomicInteger();
        EngineMessageHandler handler = (EngineMessageHandler) new ClusterHandlerPostProcessor()
                .postProcessAfterInitialization(new LocalChat(saves, relays), "chat");
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> {
            synchronized (edge.transportLock) {
                handler.handle(new WsMessageContext(1L, "Alice", edge.current), json.readTree("{\"type\":\"chat\"}"));
            }
        });
        assertEquals(0, saves.get());
        assertEquals(0, relays.get());
    }

    @Test
    void commandResolutionSerializesWithBeginningTransfer() throws Exception {
        CountDownLatch resolving = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        when(commands.resolve(anyLong(), anyString(), anyString())).thenAnswer(invocation -> {
            resolving.countDown();
            assertTrue(finish.await(2, TimeUnit.SECONDS));
            return "position";
        });
        CompletableFuture<Boolean> transferred = new CompletableFuture<>();
        Thread transfer = Thread.ofVirtual().unstarted(() -> transferred.complete(dimension.beginTransfer()));
        try {
            ReflectionTestUtils.invokeMethod(cluster, "receivePacket", command());
            assertTrue(resolving.await(2, TimeUnit.SECONDS));
            transfer.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (transfer.getState() != Thread.State.BLOCKED && !transferred.isDone() && System.nanoTime() < deadline) Thread.sleep(1);
            assertEquals(Thread.State.BLOCKED, transfer.getState());
        } finally {
            finish.countDown();
            transfer.join(2000);
        }
        assertTrue(transferred.get(1, TimeUnit.SECONDS));
    }

    public static final class LocalChat implements EngineMessageHandler {
        private final AtomicInteger saves;
        private final AtomicInteger relays;
        LocalChat(AtomicInteger saves, AtomicInteger relays) { this.saves = saves; this.relays = relays; }
        @Override public String type() { return "chat"; }
        @Override public void handle(WsMessageContext context, JsonNode message) {
            ClusterRuntime.resolveEdgeCommand(context.worldId(), context.nickname(), "/pos");
            saves.incrementAndGet();
            relays.incrementAndGet();
        }
    }

    private ClusterRuntime.Packet command() {
        return new ClusterRuntime.Packet("command", ClusterIdentity.NODE, edge.wire, 1, 3, 0, 1, "/pos", false, 0);
    }
    private void sendCommand() throws Exception {
        ReflectionTestUtils.invokeMethod(cluster, "receivePacket", command());
        CountDownLatch drained = new CountDownLatch(1);
        assertTrue(((SerialMailbox) ReflectionTestUtils.getField(actor, "mailbox")).offer(drained::countDown));
        assertTrue(drained.await(2, TimeUnit.SECONDS));
    }
    private ClusterRuntime.Packet result() {
        return sent.stream().filter(packet -> packet.kind().equals("result")).findFirst().orElseThrow();
    }
}
