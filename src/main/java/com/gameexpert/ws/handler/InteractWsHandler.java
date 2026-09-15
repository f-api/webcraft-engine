package com.gameexpert.ws.handler;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.gameexpert.engine.PlayerAction;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.ws.WsMessageContext;
import com.gameexpert.ws.dto.GeneratedEntityActionTarget;

import tools.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * 블록·몹 상호작용 요청을 월드 틱 큐에 넣습니다.
 * 리치·대상 검증과 문 ID 29의 open state 토글·오버레이 반영·전원 blockUpdate·영속은 서버 틱 루프가 처리합니다.
 */
@Component
@RequiredArgsConstructor
public class InteractWsHandler implements EngineMessageHandler {

    private final WorldEngineManager engineManager;

    @Override
    public String type() {
        return "interact";
    }

    @Override
    public Set<String> supportedTypes() {
        return Set.of("interact", "mobInteract", "shelfInteract", "entityInteract");
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        if ("entityInteract".equals(message.path("type").asString())) {
            GeneratedEntityActionTarget.Interact parsed =
                    GeneratedEntityActionTarget.parseInteract(message);
            engineManager.enqueue(context.worldId(), new PlayerAction.GeneratedEntityInteract(
                    context.nickname(), parsed.target().kind(), parsed.target().entityId(),
                    switch (parsed.hand()) {
                        case MAIN -> PlayerAction.Hand.MAIN;
                        case OFFHAND -> PlayerAction.Hand.OFFHAND;
                    }, WsFields.optionalRequestId(message)));
            return;
        }
        if ("mobInteract".equals(message.path("type").asString())) {
            Long interactionId = null;
            if (message.has("interactionId")) {
                interactionId = WsFields.longInteger(message, "interactionId");
                if (interactionId <= 0 || interactionId > 9_007_199_254_740_991L) {
                    throw new IllegalArgumentException("interactionId must be a positive safe integer");
                }
            }
            engineManager.enqueue(context.worldId(), new PlayerAction.MobInteract(
                    context.nickname(), WsFields.longInteger(message, "mobId"),
                    WsFields.hand(message), interactionId));
            return;
        }
        if ("shelfInteract".equals(message.path("type").asString())) {
            engineManager.enqueue(context.worldId(), new PlayerAction.ShelfInteract(
                    context.nickname(), WsFields.integer(message, "x"),
                    WsFields.integer(message, "y"), WsFields.integer(message, "z"),
                    WsFields.integer(message, "slot")));
            return;
        }
        int x = WsFields.integer(message, "x");
        int y = WsFields.integer(message, "y");
        int z = WsFields.integer(message, "z");
        engineManager.enqueue(context.worldId(),
                new PlayerAction.Interact(context.nickname(), x, y, z, WsFields.hand(message),
                        WsFields.optionalRequestId(message)));
    }
}
