package com.gameexpert.ws.handler;

import org.springframework.stereotype.Component;

import com.gameexpert.ws.GameTransport;
import com.gameexpert.ws.WsMessageContext;
import com.gameexpert.ws.dto.WsMessages.PlayerSwing;

import tools.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * [SWING] 손 휘두르기(바닐라 {@code ServerboundSwingPacket} → {@code ClientboundAnimatePacket} SWING_MAIN_HAND ·
 * SWING_OFF_HAND). 게임 상태를 바꾸지 않는 연출이라 틱 큐를 거치지 않고, 세션당 {@link #MIN_INTERVAL_MS} 간격을 넘긴
 * 요청만 같은 월드의 다른 접속자에게 퍼뜨린다(보낸 본인 제외 — 자기 1인칭·F5 스윙은 클라가 이미 그렸다).
 */
@Component
@RequiredArgsConstructor
public class SwingWsHandler implements EngineMessageHandler {

    /** 한 세션의 스윙 방송 최소 간격. 정적판 {@code SWING_BROADCAST_INTERVAL_MS} 와 같다. */
    public static final long MIN_INTERVAL_MS = 50;

    private final SessionCooldown cooldown = new SessionCooldown(SwingWsHandler.class, MIN_INTERVAL_MS);

    private final GameTransport broadcaster;

    @Override
    public String type() {
        return "swing";
    }

    @Override
    public void handle(WsMessageContext context, JsonNode payload) {
        JsonNode handNode = payload == null ? null : payload.get("hand");
        String hand = handNode == null || !handNode.isString() ? null : handNode.asString();
        if (!"main".equals(hand) && !"offhand".equals(hand)) return;
        if (!cooldown.tryConsume(context.session())) return;
        broadcaster.broadcastExcept(context.worldId(), context.session(), new PlayerSwing(context.nickname(), hand));
    }
}
