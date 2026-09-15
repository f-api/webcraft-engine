package com.gameexpert.api;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.socket.WebSocketSession;

public interface SessionRegistry {
    public static final class Entry {
        private final WebSocketSession session;
        private final AtomicBoolean releaseStarted = new AtomicBoolean();

        public Entry(WebSocketSession session) {
            this.session = session;
        }

        public WebSocketSession session() {
            return session;
        }

        public String connectionId() {
            return session.getId();
        }

        public boolean beginRelease() {
            return releaseStarted.compareAndSet(false, true);
        }
    }

    Entry register(Long worldId, String nickname, WebSocketSession session);
    Entry remove(Long worldId, String nickname, WebSocketSession session);
    Entry get(Long worldId, String nickname);
    Collection<Entry> entries(Long worldId);
    Set<Long> worldIds();
}
