package com.gameexpert.ws.handler;

import org.springframework.stereotype.Component;

import com.gameexpert.engine.PlayerAction;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.ws.WsMessageContext;

import tools.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * 사망 상태에서의 리스폰 요청을 월드 틱 큐에 넣습니다.
 * 스폰 좌표 복귀, 체력 복원과 리스폰 방송은 서버 틱 루프가 처리합니다.
 */
@Component
@RequiredArgsConstructor
public class RespawnWsHandler implements EngineMessageHandler {

    private final WorldEngineManager engineManager;

    @Override
    public String type() {
        return "respawn";
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        engineManager.enqueue(context.worldId(), new PlayerAction.Respawn(context.nickname()));
    }
}
