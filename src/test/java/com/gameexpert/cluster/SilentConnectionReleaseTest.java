package com.gameexpert.cluster;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gameexpert.api.PresenceOperations;
import com.gameexpert.ws.GameConnectionRuntime;
import com.gameexpert.ws.SessionCleanup;
import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

/** A reconnecting player releases a claim held by a connection whose edge server went quiet. */
class SilentConnectionReleaseTest {
    private final ObjectMapper json = new ObjectMapper();
    private ClusterRuntime cluster;
    private Object actor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepare() throws Exception {
        WorldAuthority authority = mock(WorldAuthority.class);
        when(authority.accepts(any())).thenReturn(true);
        ObjectProvider<GameConnectionRuntime> game = mock(ObjectProvider.class);
        when(game.getObject()).thenReturn(mock(GameConnectionRuntime.class));
        cluster = new ClusterRuntime(authority, mock(AuthoritySessions.class), mock(StringRedisTemplate.class), json,
                game, mock(ObjectProvider.class), mock(ObjectProvider.class), mock(ObjectProvider.class),
                mock(SessionCleanup.class), mock(PresenceOperations.class), List.of(), mock(ObjectProvider.class),
                mock(ObjectProvider.class), mock(ObjectProvider.class), mock(ApplicationContext.class));
        ClusterRuntime.Packet open = new ClusterRuntime.Packet("open", "edge-node", "silent-wire", 1, 3, 0, 1,
                json.writeValueAsString(new ClusterRuntime.Identity("Alice", 1, 1, "NORMAL", 1)), false, 0);
        Class<?> actorClass = Arrays.stream(ClusterRuntime.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("Actor")).findFirst().orElseThrow();
        Constructor<?> constructor = actorClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        actor = constructor.newInstance(cluster, open, new WorldAuthority.Owner(1, ClusterIdentity.NODE, 3));
        ((Map<String, Object>) ReflectionTestUtils.getField(cluster, "actors")).put("silent-wire", actor);
        ReflectionTestUtils.setField(cluster, "started", true);
        ReflectionTestUtils.setField(ClusterRuntime.class, "active", cluster);
    }

    @AfterEach
    void cleanup() throws Exception {
        ReflectionTestUtils.setField(ClusterRuntime.class, "active", null);
        SerialMailbox mailbox = (SerialMailbox) ReflectionTestUtils.getField(actor, "mailbox");
        mailbox.close();
        ((Thread) ReflectionTestUtils.getField(mailbox, "worker")).join(2000);
    }

    private boolean live() { return ((AtomicBoolean) ReflectionTestUtils.getField(actor, "live")).get(); }

    @Test
    void connectionThatStillSendsKeepalivesIsKept() {
        ReflectionTestUtils.setField(actor, "lastSeen", System.nanoTime());
        ClusterRuntime.releaseSilentConnection("silent-wire");
        assertTrue(live());
    }

    @Test
    void connectionWhoseEdgeStoppedSendingIsReleased() {
        ReflectionTestUtils.setField(actor, "lastSeen", System.nanoTime() - Duration.ofSeconds(4).toNanos());
        ClusterRuntime.releaseSilentConnection("silent-wire");
        assertFalse(live());
    }

    @Test
    void unknownConnectionIsIgnored() {
        assertDoesNotThrow(() -> ClusterRuntime.releaseSilentConnection("no-such-wire"));
        assertTrue(live());
    }
}
