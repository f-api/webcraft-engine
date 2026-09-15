package com.gameexpert.ws;

import org.springframework.web.socket.WebSocketSession;

public interface ConnectionEndpoint {
    void afterConnectionEstablished(WebSocketSession session) throws Exception;
    void attachDimensions(DimensionTravelCoordinator dimensions);
}
