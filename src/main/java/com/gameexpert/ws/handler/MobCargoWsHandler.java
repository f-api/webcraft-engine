package com.gameexpert.ws.handler;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.gameexpert.engine.PlayerAction;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.mob.ChestedHorseRules;
import com.gameexpert.ws.WsMessageContext;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * [MOUNT] 열린 몹 화물 세션(상자를 단 당나귀·노새·라마)의 슬롯 이동·닫기를 월드 틱 큐에 넣는다.
 * 패널을 여는 요청은 몹 우클릭("mobInteract")을 그대로 재사용하므로 여기서 다루지 않는다.
 *
 * <p>여기서 보는 것은 <b>형태</b>뿐이다 — 세션 존재·사거리·개체별 칸 수·병합은 전부 틱 스레드가
 * 다시 판정한다(상자 {@code ChestWsHandler} 와 같은 신뢰 경계).
 */
@Component
@RequiredArgsConstructor
public class MobCargoWsHandler implements EngineMessageHandler {

    private final WorldEngineManager engineManager;

    @Override
    public String type() {
        return "moveMobCargoItem";
    }

    @Override
    public Set<String> supportedTypes() {
        return Set.of("moveMobCargoItem", "closeMobCargo", "openMountedMobInventory");
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        long mobId = WsFields.integer(message, "mobId");
        if ("openMountedMobInventory".equals(message.path("type").asString())) {
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.OpenMountedMobInventory(context.nickname(), mobId));
            return;
        }
        if ("closeMobCargo".equals(message.path("type").asString())) {
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.CloseMobCargo(context.nickname(), mobId));
            return;
        }
        String from = WsFields.text(message, "from");
        if (!"cargo".equals(from) && !"inventory".equals(from)) {
            throw new IllegalArgumentException("from은 cargo 또는 inventory여야 합니다.");
        }
        boolean fromCargo = "cargo".equals(from);
        int slot = WsFields.integer(message, "slot");
        // 화물 상한은 개체 스탯이 정하므로(라마 3~15칸) 여기서는 종 상한만 본다.
        int sourceSlots = fromCargo
                ? ChestedHorseRules.CHESTED_HORSE_CARGO_SLOTS : PlayerInventory.SLOTS;
        if (slot < 0 || slot >= sourceSlots) {
            throw new IllegalArgumentException("slot이 원본 보관함 범위를 벗어났습니다.");
        }
        engineManager.enqueue(context.worldId(),
                new PlayerAction.MoveMobCargoItem(context.nickname(), mobId, fromCargo, slot));
    }
}
