package com.gameexpert.cluster;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.*;

/** An engine transport endpoint, deliberately never inserted in the student's session registry. */
final class TunnelSession implements WebSocketSession {
    @FunctionalInterface interface Sender { void send(WebSocketMessage<?> message) throws IOException; }
    private final String id;
    private final Map<String,Object> attributes;
    private final BooleanSupplier open;
    private final Sender sender;
    private final Consumer<CloseStatus> closer;
    private int textLimit = 16 * 1024 * 1024;
    private int binaryLimit = 16 * 1024 * 1024;
    TunnelSession(String id, Map<String,Object> attributes, BooleanSupplier open, Sender sender, Consumer<CloseStatus> closer) {
        this.id=id; this.attributes=attributes; this.open=open; this.sender=sender; this.closer=closer;
    }
    @Override public String getId() { return id; }
    @Override public URI getUri() { return URI.create("ws://engine-authority/" + id); }
    @Override public HttpHeaders getHandshakeHeaders() { return HttpHeaders.EMPTY; }
    @Override public Map<String,Object> getAttributes() { return attributes; }
    @Override public Principal getPrincipal() { return null; }
    @Override public InetSocketAddress getLocalAddress() { return null; }
    @Override public InetSocketAddress getRemoteAddress() { return null; }
    @Override public String getAcceptedProtocol() { return null; }
    @Override public void setTextMessageSizeLimit(int value) { textLimit=value; }
    @Override public int getTextMessageSizeLimit() { return textLimit; }
    @Override public void setBinaryMessageSizeLimit(int value) { binaryLimit=value; }
    @Override public int getBinaryMessageSizeLimit() { return binaryLimit; }
    @Override public List<WebSocketExtension> getExtensions() { return List.of(); }
    @Override public synchronized void sendMessage(WebSocketMessage<?> message) throws IOException {
        if (!isOpen()) throw new IOException("World authority transport is closed");
        sender.send(message);
    }
    @Override public boolean isOpen() { return open.getAsBoolean(); }
    @Override public void close() { close(CloseStatus.NORMAL); }
    @Override public void close(CloseStatus status) { closer.accept(status); }
}
