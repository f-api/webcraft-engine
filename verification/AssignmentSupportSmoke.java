package com.gameexpert.ws;

import com.gameexpert.bootstrap.ChatHistoryIndexRequirement;
import com.gameexpert.state.service.PlayerDimensionIdentity;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

public class AssignmentSupportSmoke {
    private static final String LAST_PING = HeartbeatMonitor.class.getName() + ".lastPing";

    public static void main(String[] args) throws Exception {
        index(List.of("world_id", "created_at"), "YES", null, true);
        index(List.of(), "YES", null, false);
        index(List.of("world_id"), "YES", null, false);
        index(List.of("created_at", "world_id"), "YES", null, false);
        index(List.of("world_id", "created_at", "id"), "YES", null, false);
        index(List.of("world_id", "created_at"), "NO", null, false);
        index(List.of("world_id", "created_at"), "YES", 8, false);

        WebSocketSession session = session();
        long expired = System.nanoTime() + TimeUnit.SECONDS.toNanos(91);
        HeartbeatMonitor.received(session, "{\"type\":\"ping\"}");
        check(!session.getAttributes().containsKey(LAST_PING), "early ping cannot start timeout before welcome");
        HeartbeatMonitor.closeIfExpired(session, expired);
        check(session.isOpen(), "initial welcome pending must not expire");
        HeartbeatMonitor.welcomeCompleted(session);
        long first = (Long) session.getAttributes().get(LAST_PING);
        HeartbeatMonitor.received(session, "{broken");
        HeartbeatMonitor.received(session, "{\"type\":\"move\"}");
        check(first == (Long) session.getAttributes().get(LAST_PING), "only ping renews timestamp");
        HeartbeatMonitor.received(session, "{\"type\":\"ping\"}");
        long ping = (Long) session.getAttributes().get(LAST_PING);
        check(ping >= first, "ping received");
        HeartbeatMonitor.closeIfExpired(session, ping + TimeUnit.SECONDS.toNanos(89));
        check(session.isOpen(), "active heartbeat stays open");
        HeartbeatMonitor.closeIfExpired(session, ping + TimeUnit.SECONDS.toNanos(90));
        check(!session.isOpen(), "90-second timeout closes transport");
        check(((CloseStatus) session.getAttributes().get("closed")).getCode() == 4000, "timeout code");

        WebSocketSession physical = session();
        DimensionSession dimension = new DimensionSession(physical, new PlayerDimensionIdentity(1, 1, 0));
        HeartbeatMonitor.welcomeCompleted(dimension);
        HeartbeatMonitor.closeIfExpired(dimension, expired);
        check(physical.isOpen(), "unready dimension protected");
        dimension.welcomeComplete();
        check(dimension.beginTransfer(), "transfer starts");
        HeartbeatMonitor.closeIfExpired(dimension, expired);
        check(physical.isOpen(), "transfer protected");
        DimensionSession next = dimension.transition(new PlayerDimensionIdentity(1, 2, "nether", 1), new TextMessage("{}"));
        HeartbeatMonitor.closeIfExpired(next, expired);
        check(physical.isOpen(), "next dimension welcome protected");
        HeartbeatMonitor.awaitingWelcome(next);
        check(!next.getAttributes().containsKey(LAST_PING), "old dimension timestamp cleared");
        check(next.acknowledge(1), "dimension acknowledged");
        next.welcomeComplete();
        HeartbeatMonitor.welcomeCompleted(next);
        long resumed = (Long) next.getAttributes().get(LAST_PING);
        HeartbeatMonitor.closeIfExpired(next, resumed + TimeUnit.SECONDS.toNanos(89));
        check(physical.isOpen(), "fresh timeout after target welcome");
        HeartbeatMonitor.closeIfExpired(next, resumed + TimeUnit.SECONDS.toNanos(90));
        check(!physical.isOpen(), "target eventually expires");
        retirementUsesSessionLock();
        System.out.println("PASS: index shape/visibility/prefix, heartbeat timeout/ping, initial welcome and dimension transfer protection");
    }

    private static void retirementUsesSessionLock() throws Exception {
        DimensionSession source = new DimensionSession(session(), new PlayerDimensionIdentity(1, 1, 0));
        java.util.concurrent.atomic.AtomicBoolean present = new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicBoolean removed = new java.util.concurrent.atomic.AtomicBoolean(false);
        com.gameexpert.api.SessionRegistry registry = proxy(com.gameexpert.api.SessionRegistry.class,
                (object, method, arguments) -> {
                    if ("remove".equals(method.getName())) {
                        check(Thread.holdsLock(source), "registry retirement shares delayed join lock");
                        removed.set(true);
                        return new com.gameexpert.api.SessionRegistry.Entry(source);
                    }
                    if ("entries".equals(method.getName())) return List.of();
                    return null;
                });
        com.gameexpert.api.PresenceOperations presence = proxy(com.gameexpert.api.PresenceOperations.class,
                (object, method, arguments) -> {
                    if ("leave".equals(method.getName())) {
                        check(Thread.holdsLock(source), "presence retirement shares delayed join lock");
                        check(removed.get(), "registry removed before presence leave");
                        present.set(false);
                    }
                    return null;
                });
        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        DimensionTravelCoordinator coordinator = new DimensionTravelCoordinator(null, null, null, null,
                registry, new GameTransport(registry, mapper), presence, null, mapper);
        try {
            java.lang.reflect.Method retire = DimensionTravelCoordinator.class.getDeclaredMethod(
                    "retireSource", DimensionSession.class, String.class);
            retire.setAccessible(true);
            retire.invoke(coordinator, source, "player");
            check(!present.get(), "retirement clears presence after registered join");
        } finally {
            coordinator.shutdown();
        }
    }

    private static void index(List<String> columns, String visible, Object prefix, boolean accepted) throws Exception {
        AtomicInteger row = new AtomicInteger(-1);
        ResultSet result = proxy(ResultSet.class, (object, method, arguments) -> switch (method.getName()) {
            case "next" -> row.incrementAndGet() < columns.size();
            case "getString" -> "COLUMN_NAME".equals(arguments[0]) ? columns.get(row.get()) : visible;
            case "getObject" -> prefix;
            default -> null;
        });
        PreparedStatement statement = proxy(PreparedStatement.class, (object, method, arguments) ->
                "executeQuery".equals(method.getName()) ? result : null);
        Connection connection = proxy(Connection.class, (object, method, arguments) -> {
            if ("prepareStatement".equals(method.getName())) {
                String sql = (String) arguments[0];
                check(sql.contains("TABLE_SCHEMA = DATABASE()") && sql.contains("ORDER BY SEQ_IN_INDEX"), "schema and order scoped");
                return statement;
            }
            return null;
        });
        DataSource source = proxy(DataSource.class, (object, method, arguments) -> connection);
        try {
            new ChatHistoryIndexRequirement(source).afterPropertiesSet();
            check(accepted, "invalid index accepted: " + columns);
        } catch (IllegalStateException failure) {
            check(!accepted && failure.getMessage().startsWith("CHAT_HISTORY_INDEX_MISSING"), "valid index rejected");
        }
    }

    private static WebSocketSession session() {
        Map<String, Object> attributes = new ConcurrentHashMap<>();
        AtomicBoolean open = new AtomicBoolean(true);
        return proxy(WebSocketSession.class, (object, method, arguments) -> switch (method.getName()) {
            case "getId" -> "test-session";
            case "getAttributes" -> attributes;
            case "isOpen" -> open.get();
            case "close" -> { open.set(false); attributes.put("closed", arguments[0]); yield null; }
            default -> null;
        });
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
