package com.gameexpert.cluster;

import com.gameexpert.api.SessionRegistry;
import com.gameexpert.ws.GameConnectionRuntime;
import com.gameexpert.ws.HeartbeatMonitor;
import com.gameexpert.ws.InboundRateLimiter;
import com.gameexpert.ws.SessionAttributes;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real physical receive and heartbeat close must complete without opposite monitor ordering. */
class HeartbeatTransportLockRegressionTest {
    @Test void concurrentExpiredHeartbeatAndReceiveCompleteNormally() throws Exception {
        concurrentReceive("{}", true);
    }

    @Test void pingWhileHeartbeatWaitsPreventsStaleTimeoutClosure() throws Exception {
        concurrentReceive("{\"type\":\"ping\"}", false);
    }

    private void concurrentReceive(String payload, boolean expectClose) throws Exception {
        Fixture fixture = new Fixture();
        Object previous = activeField().get(null);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch receiveOwnsTransport = new CountDownLatch(1);
        CountDownLatch releaseReceive = new CountDownLatch(1);
        CountDownLatch heartbeatStarted = new CountDownLatch(1);
        Thread receive = daemon("regression-physical-receive", failure,
                () -> fixture.game.receive(fixture.session, new TextMessage(payload), (context, body) -> {}));
        Thread heartbeat = daemon("regression-heartbeat-expiration", failure, () -> {
            heartbeatStarted.countDown(); expire(fixture.session);
        });
        try {
            activeField().set(null, fixture.cluster);
            when(fixture.limiter.check(fixture.session)).thenAnswer(ignored -> {
                assertTrue(Thread.holdsLock(fixture.edge.transportLock));
                receiveOwnsTransport.countDown();
                assertTrue(releaseReceive.await(3, TimeUnit.SECONDS));
                return InboundRateLimiter.Decision.ACCEPT;
            });
            receive.start();
            assertTrue(receiveOwnsTransport.await(3, TimeUnit.SECONDS));
            heartbeat.start();
            assertTrue(heartbeatStarted.await(3, TimeUnit.SECONDS));
            ThreadMXBean threads = ManagementFactory.getThreadMXBean();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            ThreadInfo waiting = null;
            while (System.nanoTime() < deadline) {
                waiting = threads.getThreadInfo(new long[]{heartbeat.threadId()}, true, true)[0];
                if (waiting != null && waiting.getThreadState() == Thread.State.BLOCKED) break;
                if (failure.get() != null) throw new AssertionError(failure.get());
                Thread.sleep(10);
            }
            assertNotNull(waiting);
            assertEquals(Thread.State.BLOCKED, waiting.getThreadState());
            assertEquals(System.identityHashCode(fixture.edge.transportLock),
                    waiting.getLockInfo().getIdentityHashCode());
            assertFalse(Arrays.stream(waiting.getLockedMonitors()).anyMatch(monitor ->
                    monitor.getIdentityHashCode() == System.identityHashCode(fixture.session)),
                    "Heartbeat must not own session while waiting for transport");
            releaseReceive.countDown();
            receive.join(3000); heartbeat.join(3000);
            assertFalse(receive.isAlive()); assertFalse(heartbeat.isAlive());
            assertNull(failure.get());
            if (expectClose) verify(fixture.physical).close(any(CloseStatus.class));
            else verify(fixture.physical, never()).close(any(CloseStatus.class));
        } finally {
            releaseReceive.countDown();
            activeField().set(null, previous);
            fixture.edge.mailbox.close();
        }
    }

    @Test void edgeLockRemainsAvailableAfterClusterStops() throws Exception {
        Fixture fixture = new Fixture();
        Object previous = activeField().get(null);
        try {
            activeField().set(null, null);
            assertSame(fixture.edge.transportLock, ClusterRuntime.sessionTransportLock(fixture.session));
            expire(fixture.session);
            verify(fixture.physical).close(any(CloseStatus.class));
        } finally {
            activeField().set(null, previous); fixture.edge.mailbox.close();
        }
    }

    @Test void dimensionWelcomeAndTransferBarriersStillPreventExpiration() throws Exception {
        WebSocketSession physical = mock(WebSocketSession.class);
        when(physical.isOpen()).thenReturn(true);
        when(physical.getAttributes()).thenReturn(new java.util.HashMap<>());
        com.gameexpert.ws.DimensionSession dimension = new com.gameexpert.ws.DimensionSession(
                physical, new com.gameexpert.state.service.PlayerDimensionIdentity(1L, 1L, 0L));
        dimension.getAttributes().put(HeartbeatMonitor.class.getName() + ".lastPing",
                System.nanoTime() - TimeUnit.SECONDS.toNanos(91));
        expire(dimension);
        verify(physical, never()).close(any(CloseStatus.class));
        dimension.welcomeComplete();
        assertTrue(dimension.beginTransfer());
        expire(dimension);
        verify(physical, never()).close(any(CloseStatus.class));
        dimension.abortTransfer();
        expire(dimension);
        verify(physical).close(any(CloseStatus.class));
    }
    private static Field activeField() throws Exception {
        Field field = ClusterRuntime.class.getDeclaredField("active");
        field.setAccessible(true); return field;
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = ClusterRuntime.class.getDeclaredField(name);
        field.setAccessible(true); field.set(target, value);
    }
    private static void expire(WebSocketSession session) throws Exception {
        Method method = HeartbeatMonitor.class.getDeclaredMethod("closeIfExpired", WebSocketSession.class, long.class);
        method.setAccessible(true); method.invoke(null, session, System.nanoTime());
    }
    private interface CheckedTask { void run() throws Exception; }
    private static Thread daemon(String name, AtomicReference<Throwable> failure, CheckedTask task) {
        Thread thread = new Thread(() -> {
            try { task.run(); } catch (Throwable problem) { failure.compareAndSet(null, problem); }
        }, name);
        thread.setDaemon(true); return thread;
    }
    private static final class Fixture {
        final ClusterRuntime cluster = mock(ClusterRuntime.class, CALLS_REAL_METHODS);
        final WebSocketSession physical = mock(WebSocketSession.class);
        final InboundRateLimiter limiter = mock(InboundRateLimiter.class);
        final SessionRegistry registry = mock(SessionRegistry.class);
        final ClusterRuntime.Edge edge;
        final EdgeSession session;
        final GameConnectionRuntime game;
        Fixture() throws Exception {
            set(cluster, "started", true);
            set(cluster, "stopped", new AtomicBoolean());
            when(physical.isOpen()).thenReturn(true);
            edge = cluster.new Edge(physical, mock(WorldAuthority.Owner.class));
            session = new EdgeSession(edge, 0L, Map.of(
                    SessionAttributes.ATTR_WORLD_ID, 1L,
                    SessionAttributes.ATTR_NICKNAME, "Audit"));
            edge.current = session; edge.ready = true;
            session.getAttributes().put(HeartbeatMonitor.class.getName() + ".lastPing",
                    System.nanoTime() - TimeUnit.SECONDS.toNanos(91));
            when(registry.get(1L, "Audit")).thenReturn(new SessionRegistry.Entry(session));
            when(limiter.check(session)).thenReturn(InboundRateLimiter.Decision.ACCEPT);
            game = new GameConnectionRuntime(registry, new com.gameexpert.capacity.PlayerCapacity(
                    new org.springframework.mock.env.MockEnvironment()), null, null, null, null, limiter);
        }
    }
}
