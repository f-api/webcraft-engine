package com.gameexpert.ws.handler;

import org.springframework.stereotype.Component;

import com.gameexpert.ws.WorldSessionLifecycle;
import com.gameexpert.ws.WsMessageContext;

import tools.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/** 월드 목록으로 돌아가기 전에 서버의 플레이어 상태와 닉네임 점유를 확정 해제합니다. */
@Component
@RequiredArgsConstructor
public class LeaveWorldWsHandler implements EngineMessageHandler {

    private final WorldSessionLifecycle lifecycle;

    @Override
    public String type() {
        return "leaveWorld";
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        lifecycle.releaseGracefully(context.session());
    }
}
