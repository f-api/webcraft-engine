package com.gameexpert.ws.handler;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.gameexpert.engine.PlayerAction;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.engine.PlayerAction.EditKind;
import com.gameexpert.ws.WsMessageContext;

import tools.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * 블록 파괴·설치 요청을 월드 틱 큐에 넣습니다.
 * 리치·범위·가능 여부 검증, 오버레이 반영, blockUpdate 방송과 유체 연동은 서버 틱 루프가 처리합니다.
 */
@Component
@RequiredArgsConstructor
public class BlockWsHandler implements EngineMessageHandler {

    private final WorldEngineManager engineManager;

    @Override
    public String type() {
        return "blockPlace";
    }

    @Override
    public Set<String> supportedTypes() {
        return Set.of("blockPlace", "blockBreak", "mineHit");
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        int x = WsFields.integer(message, "x");
        int y = WsFields.integer(message, "y");
        int z = WsFields.integer(message, "z");

        String type = message.path("type").asString();
        if ("mineHit".equals(type)) {
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.MineHit(context.nickname(), x, y, z));
        } else if ("blockPlace".equals(type)) {
            short blockType = (short) WsFields.integer(message, "blockType");
            short state = (short) stateByte(message);
            engineManager.enqueue(context.worldId(), new PlayerAction.BlockEdit(
                    context.nickname(), EditKind.PLACE, x, y, z, blockType, state,
                    WsFields.hand(message), WsFields.optionalRequestId(message)));
        } else {
            // blockBreak는 AIR(0)로의 변경입니다.
            engineManager.enqueue(context.worldId(), new PlayerAction.BlockEdit(
                    context.nickname(), EditKind.BREAK, x, y, z, (short) 0, (short) 0));
        }
    }

    private int stateByte(JsonNode message) {
        int state = WsFields.integer(message, "state");
        if (state < 0 || state > 255) {
            throw new IllegalArgumentException("state는 0~255여야 합니다.");
        }
        return state;
    }
}
