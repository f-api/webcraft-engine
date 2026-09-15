package com.gameexpert.ws.handler;

import org.springframework.stereotype.Component;

import com.gameexpert.engine.PlayerAction;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.ws.WsMessageContext;

import tools.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * 이동 메시지를 검사하고 월드 틱 큐에 넣습니다.
 * 실제 위치 반영과 낙하 추적은 서버 틱 루프가 처리합니다.
 */
@Component
@RequiredArgsConstructor
public class MoveWsHandler implements EngineMessageHandler {

    private final WorldEngineManager engineManager;

    @Override
    public String type() {
        return "move";
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        // 숫자가 빠졌거나 NaN/무한대이면 게임 좌표로 쓸 수 없으므로 받지 않습니다.
        double x = WsFields.finiteNumber(message, "x");
        double y = WsFields.finiteNumber(message, "y");
        double z = WsFields.finiteNumber(message, "z");
        float yaw = WsFields.finiteFloat(message, "yaw");
        float pitch = WsFields.finiteFloat(message, "pitch");
        boolean crouching = WsFields.booleanValue(message, "crouching");
        boolean gliding = WsFields.booleanValue(message, "gliding");
        JsonNode actionIdNode = message.get("finalSceneActionId");
        String finalSceneActionId = null;
        if (actionIdNode != null) {
            if (!actionIdNode.isString()) {
                throw new IllegalArgumentException("finalSceneActionId 형식이 올바르지 않습니다.");
            }
            finalSceneActionId = actionIdNode.asString();
        }

        engineManager.enqueue(context.worldId(),
                new PlayerAction.Move(context.nickname(), x, y, z, yaw, pitch, crouching, gliding,
                        finalSceneActionId));
    }
}
