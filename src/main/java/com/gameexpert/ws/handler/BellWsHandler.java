package com.gameexpert.ws.handler;

import org.springframework.stereotype.Component;

import com.gameexpert.engine.PlayerAction;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.ws.WsMessageContext;

import tools.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * [BLOCK-SHAPES] 종 치기 요청을 월드 틱 큐에 넣습니다. 리치·isProperHit 판정과 bellRing 방송은
 * 서버 틱 루프({@code WorldTickLoop.applyBellRing})가 처리합니다.
 */
@Component
@RequiredArgsConstructor
public class BellWsHandler implements EngineMessageHandler {

    private final WorldEngineManager engineManager;

    @Override
    public String type() {
        return "bellRing";
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        engineManager.enqueue(context.worldId(), new PlayerAction.BellRing(context.nickname(),
                WsFields.integer(message, "x"), WsFields.integer(message, "y"),
                WsFields.integer(message, "z"), WsFields.integer(message, "face"),
                WsFields.finiteNumber(message, "hitY")));
    }
}
