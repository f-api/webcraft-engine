package com.gameexpert.ws.handler;

import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.ws.WsMessageContext;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Semantic actions for the persistent 26.3 cushion decoration entity. */
@Component
@RequiredArgsConstructor
public class CushionWsHandler implements EngineMessageHandler {
    private final WorldEngineManager engineManager;

    @Override
    public String type() {
        return "placeCushion";
    }

    @Override
    public Set<String> supportedTypes() {
        return Set.of("placeCushion", "sitCushion", "leaveCushion", "breakCushion");
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        Long worldId = context.worldId();
        String nickname = context.nickname();
        switch (message.path("type").asString()) {
            case "placeCushion" -> {
                if (!"up".equals(WsFields.text(message, "face"))) {
                    throw new IllegalArgumentException("쿠션은 윗면에만 설치할 수 있습니다.");
                }
                engineManager.placeCushion(worldId, nickname,
                        WsFields.finiteNumber(message, "x"),
                        WsFields.finiteNumber(message, "y"),
                        WsFields.finiteNumber(message, "z"), WsFields.hand(message));
            }
            case "sitCushion" -> engineManager.sitCushion(worldId, nickname,
                    WsFields.longInteger(message, "cushionId"));
            case "leaveCushion" -> engineManager.leaveCushion(worldId, nickname);
            case "breakCushion" -> engineManager.breakCushion(worldId, nickname,
                    WsFields.longInteger(message, "cushionId"));
            default -> throw new IllegalArgumentException("알 수 없는 쿠션 메시지입니다.");
        }
    }
}
