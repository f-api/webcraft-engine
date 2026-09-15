package com.gameexpert.api;

public interface PresenceOperations {
    long onlineCount(Long worldId);
    void join(Long worldId, String connectionId);
    void leave(Long worldId, String connectionId);
    void heartbeat(Long worldId, String connectionId);
}
