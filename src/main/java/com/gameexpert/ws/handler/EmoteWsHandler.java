package com.gameexpert.ws.handler;

import org.springframework.stereotype.Component;

import com.gameexpert.ws.GameTransport;
import com.gameexpert.ws.WsMessageContext;
import com.gameexpert.ws.dto.WsMessages.Emote;

import tools.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * 웨이브 이모트 처리기. 플레이어별 3초 쿨다운을 통과한 이모트만
 * 같은 월드에 브로드캐스트합니다(RemotePlayer 팔 흔들기 + 말풍선 연출).
 */
@Component
@RequiredArgsConstructor
public class EmoteWsHandler implements EngineMessageHandler {

    /** 이모트 쿨다운. 채팅과 같은 {@link SessionCooldown} 구현을 공유합니다. */
    public static final long COOLDOWN_MS = 3_000;

    private final SessionCooldown cooldown = new SessionCooldown(EmoteWsHandler.class, COOLDOWN_MS);

    private final GameTransport broadcaster;

    @Override
    public String type() {
        return "emote";
    }

    @Override
    public void handle(WsMessageContext context, JsonNode ignored) {
        // 이모트는 연출뿐이라 쿨다운에 걸리면 조용히 버립니다(채팅은 사용자가 원문을 잃으므로 통지).
        if (!cooldown.tryConsume(context.session())) return;
        broadcaster.broadcast(context.worldId(), new Emote(context.nickname()));
    }
}
