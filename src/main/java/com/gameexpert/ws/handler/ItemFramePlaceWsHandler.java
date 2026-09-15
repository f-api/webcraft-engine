package com.gameexpert.ws.handler;

import org.springframework.stereotype.Component;

import com.gameexpert.engine.PlayerAction;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.ws.WsMessageContext;

import tools.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * [EC-MOBS] {@code itemFramePlace{x,y,z,face,hand}}: 누른 블록 칸과 면(바닐라 Direction 3D 값 0..5)에 아이템 액자를
 * 건다({@code HangingEntityItem.useOn}). 리치·손 아이템·{@code survives()} 판정은 틱 루프가 한다.
 */
@Component
@RequiredArgsConstructor
public class ItemFramePlaceWsHandler implements EngineMessageHandler {

    private final WorldEngineManager engineManager;

    @Override
    public String type() {
        return "itemFramePlace";
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        int face = WsFields.integer(message, "face");
        if (face < 0 || face > 5) throw new IllegalArgumentException("face 는 0..5 여야 합니다.");
        engineManager.enqueue(context.worldId(), new PlayerAction.PlaceItemFrame(context.nickname(),
                WsFields.integer(message, "x"), WsFields.integer(message, "y"),
                WsFields.integer(message, "z"), face, WsFields.hand(message)));
    }
}
