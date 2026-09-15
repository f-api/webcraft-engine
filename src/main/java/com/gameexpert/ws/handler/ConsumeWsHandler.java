package com.gameexpert.ws.handler;

import org.springframework.stereotype.Component;

import com.gameexpert.engine.PlayerAction;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.ws.WsMessageContext;

import tools.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/** 식량 섭취 요청을 월드 틱 큐에 넣습니다. 판정·소비·회복·개인 갱신은 서버 틱 루프가 처리합니다. */
@Component
@RequiredArgsConstructor
public class ConsumeWsHandler implements EngineMessageHandler {

    private final WorldEngineManager engineManager;

    @Override
    public String type() {
        return "consume";
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        engineManager.enqueue(context.worldId(), new PlayerAction.Consume(
                context.nickname(), WsFields.hand(message), message.path("phase").asString("finish")));
    }
}
