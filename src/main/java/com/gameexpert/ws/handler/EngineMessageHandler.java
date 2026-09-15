package com.gameexpert.ws.handler;

import java.util.Set;

import com.gameexpert.ws.WsMessageContext;

import tools.jackson.databind.JsonNode;

/** type별 메시지 처리기의 공통 규격입니다. */
public interface EngineMessageHandler {
    String type();

    default Set<String> supportedTypes() {
        return Set.of(type());
    }

    void handle(WsMessageContext context, JsonNode message);
}
