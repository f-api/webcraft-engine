package com.gameexpert.ws;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.gameexpert.state.service.PlayerDimensionIdentity;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.WebSocketSessionDecorator;

/** 같은 물리 socket의 공간별 송신 lease. 이미 포착된 recipient도 retire 이후 보낼 수 없다. */
public final class DimensionSession extends WebSocketSessionDecorator {
    static final class Transport {
        private DimensionSession active;
        private final WebSocketSession physical;
        Transport(WebSocketSession physical) { this.physical = physical; }
    }
    private final Transport transport;
    private final PlayerDimensionIdentity identity;
    private final Map<String, Object> attributes;
    private volatile boolean ready;
    private boolean retired;
    private volatile boolean transferring;
    private boolean acknowledged;

    public DimensionSession(WebSocketSession physical, PlayerDimensionIdentity identity) {
        this(new Transport(physical), identity, physical.getAttributes(), false);
        acknowledged = true; // Initial reconnect needs no transition ACK, but still waits for welcome.
        synchronized (transport) { transport.active = this; }
    }

    private DimensionSession(Transport transport, PlayerDimensionIdentity identity,
            Map<String, Object> source, boolean ready) {
        super(transport.physical);
        this.transport = transport;
        this.identity = java.util.Objects.requireNonNull(identity);
        this.attributes = new ConcurrentHashMap<>(source);
        this.attributes.put(SessionAttributes.ATTR_WORLD_ID, identity.runtimeWorldId());
        this.ready = ready;
    }

    public PlayerDimensionIdentity identity() { return identity; }
    @Override public String getId() { return super.getId() + ":dimension:" + identity.travelRevision(); }
    @Override public Map<String, Object> getAttributes() { return attributes; }
    @Override public boolean isOpen() {
        synchronized (transport) { return transport.active == this && !retired && super.isOpen(); }
    }
    @Override public void sendMessage(WebSocketMessage<?> message) throws IOException {
        synchronized (transport) {
            if (transport.active == this && !retired && super.isOpen()) super.sendMessage(message);
        }
    }
    @Override public void close(CloseStatus status) throws IOException {
        synchronized (transport) {
            if (transport.active == this) super.close(status);
        }
    }
    @Override public void close() throws IOException { close(CloseStatus.NORMAL); }

    public boolean inputReady() { return ready && !transferring && isOpen(); }
    public synchronized boolean beginTransfer() {
        if (!inputReady()) return false;
        transferring = true;
        return true;
    }
    public synchronized void abortTransfer() { transferring = false; }

    /** DB 커밋 이후 retire와 transition 송신을 같은 물리 lock 안에서 순서대로 실행한다. */
    public DimensionSession transition(PlayerDimensionIdentity target, TextMessage envelope) throws IOException {
        synchronized (transport) {
            if (transport.active != this || !transferring
                    || target.rootWorldId() != identity.rootWorldId()
                    || target.travelRevision() != identity.travelRevision() + 1) {
                throw new IllegalStateException("stale dimension transport transition");
            }
            DimensionSession next = new DimensionSession(transport, target, attributes, false);
            transport.active = next;
            super.sendMessage(envelope);
            return next;
        }
    }

    public synchronized boolean acknowledge(long revision) {
        if (!isOpen() || ready || acknowledged || revision != identity.travelRevision()) return false;
        acknowledged = true;
        return true;
    }

    public synchronized void welcomeComplete() {
        if (!isOpen() || !acknowledged && identity.travelRevision() > 0 && !ready) {
            throw new IllegalStateException("dimension welcome without matching acknowledgment");
        }
        ready = true;
    }

    public void retire() { synchronized (transport) { retired = true; } }

    public DimensionSession current() { synchronized (transport) { return transport.active; } }
}
