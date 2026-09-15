package com.gameexpert.ws.handler;

import org.springframework.stereotype.Component;

import com.gameexpert.engine.PlayerAction;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.ws.WsMessageContext;

import tools.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * [DRAGON] {@code endCrystalPlace{x,y,z,hand}}: 누른 블록 칸 위에 엔드 수정을 세운다({@code EndCrystalItem.useOn}).
 * 리치·손 아이템·흑요석/기반암·빈 칸·개체 판정과 드래곤 부활 시도는 틱 루프가 한다.
 */
@Component
@RequiredArgsConstructor
public class EndCrystalPlaceWsHandler implements EngineMessageHandler {

    private final WorldEngineManager engineManager;

    @Override
    public String type() {
        return "endCrystalPlace";
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        engineManager.enqueue(context.worldId(), new PlayerAction.PlaceEndCrystal(context.nickname(),
                WsFields.integer(message, "x"), WsFields.integer(message, "y"),
                WsFields.integer(message, "z"), WsFields.hand(message)));
    }
}
