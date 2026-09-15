package com.gameexpert.ws;

import org.springframework.web.socket.WebSocketSession;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/** 메시지 핸들러가 공통으로 쓰는 접속 정보입니다. */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public class WsMessageContext {

    private final Long worldId;
    private final String nickname;
    private final WebSocketSession session;
}
