package com.gameexpert.ws;

import com.gameexpert.api.SessionRegistry;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public final class HeartbeatMonitor {
    private static final Logger log = LoggerFactory.getLogger(HeartbeatMonitor.class);
    private static final String LAST_PING = HeartbeatMonitor.class.getName() + ".lastPing";
    private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(90);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final SessionRegistry registry;

    public HeartbeatMonitor(SessionRegistry registry) {
        this.registry = registry;
    }

    public static void awaitingWelcome(WebSocketSession session) {
        session.getAttributes().remove(LAST_PING);
    }

    public static void welcomeCompleted(WebSocketSession session) {
        session.getAttributes().put(LAST_PING, System.nanoTime());
    }

    public static void received(WebSocketSession session, String payload) {
        try {
            JsonNode message = JSON.readTree(payload);
            if (message != null && "ping".equals(message.path("type").asString(""))) {
                session.getAttributes().computeIfPresent(LAST_PING, (key, previous) -> System.nanoTime());
            }
        } catch (RuntimeException invalidJson) {
            // 메시지 형식 처리는 기존 라우터에 맡깁니다.
        }
    }

    @Scheduled(fixedDelay = 5_000)
    public void closeExpiredConnections() {
        long now = System.nanoTime();
        SessionRegistry local = registry instanceof com.gameexpert.cluster.AuthoritySessions owned ? owned.local() : registry;
        local.worldIds().stream()
                .flatMap(worldId -> local.entries(worldId).stream())
                .map(SessionRegistry.Entry::session)
                .forEach(session -> closeIfExpired(session, now));
    }

    static void closeIfExpired(WebSocketSession session, long now) {
        // Physical input takes the transport lock before the session monitor. Timeout closure
        // must use the same order and recheck the timestamp after a waiting ping can complete.
        synchronized (com.gameexpert.cluster.ClusterRuntime.sessionTransportLock(session)) {
            closeUnderTransportLock(session, now);
        }
    }

    private static void closeUnderTransportLock(WebSocketSession session, long now) {
        synchronized (session) {
            if (!session.isOpen()
                    || session instanceof DimensionSession dimension && !dimension.inputReady()) {
                return;
            }
            Object lastPing = session.getAttributes().get(LAST_PING);
            if (!(lastPing instanceof Long receivedAt) || now - receivedAt < TIMEOUT_NANOS) {
                return;
            }
            try {
                // close의 기존 연결 종료 콜백이 세션과 Redis 접속 정보를 정리합니다.
                session.close(new CloseStatus(4000, "HEARTBEAT_TIMEOUT"));
            } catch (IOException | RuntimeException failure) {
                log.warn("Heartbeat 연결 종료 실패: session={}", session.getId(), failure);
            }
        }
    }
}
