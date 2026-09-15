package com.gameexpert.ws.handler;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.ws.WsMessageContext;

import tools.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * 보트 입력을 월드 틱 큐(BoatSystem)에 넣습니다. 좌석 배정·위치 중계·수명은 서버 틱 루프(클라 권위 모델)가 처리합니다.
 *  - placeBoat{x,y,z,yaw}: 보트 아이템 사용 → 물/지면 위 스폰(배치자 자동 탑승)
 *  - boardBoat{boatId}   : 빈 보트 탑승 요청
 *  - leaveBoat{}         : 하차 요청
 *  - boatPos{boatId,x,y,z,yaw}: 운전자 위치 업링크(서버는 신뢰·중계)
 *  - breakBoat{boatId}   : 파괴 요청(보트 아이템 1개 드랍)
 */
@Component
@RequiredArgsConstructor
public class BoatWsHandler implements EngineMessageHandler {

    private final WorldEngineManager engineManager;

    @Override
    public String type() {
        return "placeBoat";
    }

    @Override
    public Set<String> supportedTypes() {
        return Set.of("placeBoat", "boardBoat", "leaveBoat", "boatPos", "breakBoat");
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        Long worldId = context.worldId();
        String nickname = context.nickname();
        switch (message.path("type").asString()) {
            case "placeBoat" -> engineManager.placeBoat(worldId, nickname,
                    WsFields.finiteNumber(message, "x"), WsFields.finiteNumber(message, "y"),
                    WsFields.finiteNumber(message, "z"), WsFields.finiteNumber(message, "yaw"),
                    WsFields.hand(message));
            case "boardBoat" -> engineManager.boardBoat(
                    worldId, nickname, WsFields.longInteger(message, "boatId"));
            case "leaveBoat" -> engineManager.leaveBoat(worldId, nickname);
            case "boatPos" -> engineManager.boatPos(
                    worldId, nickname, WsFields.longInteger(message, "boatId"),
                    WsFields.finiteNumber(message, "x"), WsFields.finiteNumber(message, "y"),
                    WsFields.finiteNumber(message, "z"), WsFields.finiteNumber(message, "yaw"));
            case "breakBoat" -> engineManager.breakBoat(
                    worldId, nickname, WsFields.longInteger(message, "boatId"));
            default -> throw new IllegalArgumentException("알 수 없는 보트 메시지입니다.");
        }
    }
}
