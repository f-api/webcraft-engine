package com.gameexpert.ws;

import com.gameexpert.api.SessionRegistry;
import org.springframework.web.socket.WebSocketSession;

public interface SessionCleanup {
    SessionRegistry.Entry remove(Long worldId, String nickname, WebSocketSession session);
}
