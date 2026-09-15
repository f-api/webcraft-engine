package com.gameexpert.ws.handler;

import org.springframework.stereotype.Component;

import com.gameexpert.engine.PlayerAction;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.ws.WsMessageContext;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/** 빈 지도 사용을 월드 틱 소유자에게 전달합니다. */
@Component
@RequiredArgsConstructor
public class MapWsHandler implements EngineMessageHandler {

    private final WorldEngineManager engineManager;

    @Override
    public String type() {
        return "mapUse";
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        engineManager.enqueue(context.worldId(), new PlayerAction.MapUse(
                context.nickname(), WsFields.hand(message)));
    }
}
