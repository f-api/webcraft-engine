package com.gameexpert.cluster;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.WebSocketSessionDecorator;

/** Generation-fenced physical sender; captured old-world chat recipients cannot reach a new world. */
final class EdgeSession extends WebSocketSessionDecorator {
    final ClusterRuntime.Edge edge;
    private final long generation;
    private final Map<String,Object> attributes;
    EdgeSession(ClusterRuntime.Edge edge, long generation, Map<String,Object> attributes) {
        super(edge.physical); this.edge=edge; this.generation=generation;
        this.attributes = new ConcurrentHashMap<>(attributes);
        this.attributes.put(ClusterIdentity.ROLE, "edge");
    }
    @Override public String getId() { return edge.wire + ":edge:" + generation; }
    @Override public Map<String,Object> getAttributes() { return attributes; }
    @Override public boolean isOpen() {
        return !edge.closed.get() && edge.current == this && super.isOpen();
    }
    @Override public void sendMessage(WebSocketMessage<?> message) throws IOException {
        synchronized (edge.transportLock) { if (isOpen()) super.sendMessage(message); }
    }
    @Override public void close(CloseStatus status) throws IOException {
        synchronized (edge.transportLock) { if (edge.current == this) super.close(status); }
    }
    @Override public void close() throws IOException { close(CloseStatus.NORMAL); }
}
