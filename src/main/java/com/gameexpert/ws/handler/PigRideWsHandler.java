package com.gameexpert.ws.handler;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.gameexpert.engine.PlayerAction;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.ws.WsMessageContext;

import tools.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * [MOUNT] 탑승 조종 입력을 월드 틱 큐에 넣습니다. 탑승 요청은 몹 우클릭
 * ({@code mobInteract})을 그대로 재사용하므로 여기서 다루지 않습니다.
 *  - pigPos{mobId,x,y,z,yaw}: 기수 좌표 업링크(보트 {@code boatPos} 와 같은 계약)
 *  - leavePig{}             : 자발적 하차 요청
 *
 * <p>좌석 일치·월드 경계·기수 pose 근접 검증은 전부 틱 스레드({@code MobSystem})가 합니다.
 */
@Component
@RequiredArgsConstructor
public class PigRideWsHandler implements EngineMessageHandler {

    private final WorldEngineManager engineManager;

    @Override
    public String type() {
        return "pigPos";
    }

    @Override
    public Set<String> supportedTypes() {
        return Set.of("pigPos", "leavePig", "mobRiderPos", "mobDismount", "mobJump");
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        Long worldId = context.worldId();
        String nickname = context.nickname();
        switch (message.path("type").asString()) {
            case "pigPos" -> engineManager.enqueue(worldId, new PlayerAction.PigPos(
                    nickname, WsFields.longInteger(message, "mobId"),
                    WsFields.finiteNumber(message, "x"), WsFields.finiteNumber(message, "y"),
                    WsFields.finiteNumber(message, "z"), WsFields.finiteNumber(message, "yaw")));
            case "leavePig" -> engineManager.enqueue(
                    worldId, new PlayerAction.LeavePig(nickname));
            // [MOUNT] 종 비의존 좌석 계약. 돼지 메시지는 하위호환으로 남고 새 종은 이 셋을 쓴다.
            case "mobRiderPos" -> engineManager.enqueue(worldId, new PlayerAction.MobRiderPos(
                    nickname, WsFields.longInteger(message, "mobId"),
                    (int) WsFields.longInteger(message, "seatIndex"),
                    WsFields.finiteNumber(message, "x"), WsFields.finiteNumber(message, "y"),
                    WsFields.finiteNumber(message, "z"), WsFields.finiteNumber(message, "yaw")));
            case "mobDismount" -> engineManager.enqueue(
                    worldId, new PlayerAction.MobDismount(nickname));
            case "mobJump" -> engineManager.enqueue(worldId, new PlayerAction.MobJump(
                    nickname, WsFields.longInteger(message, "mobId"),
                    (int) WsFields.longInteger(message, "charge")));
            default -> throw new IllegalArgumentException("알 수 없는 탑승 조종 메시지입니다.");
        }
    }
}
