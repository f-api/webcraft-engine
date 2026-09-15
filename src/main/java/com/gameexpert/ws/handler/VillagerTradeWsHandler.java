package com.gameexpert.ws.handler;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.gameexpert.engine.PlayerAction;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.ws.WsMessageContext;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * [VILLAGER-TRADE] 열린 주민 거래 화면의 오퍼 확정·닫기를 월드 틱 큐에 넣는다.
 * 화면을 여는 요청은 몹 우클릭("mobInteract")을 그대로 재사용하므로 여기서 다루지 않는다.
 */
@Component
@RequiredArgsConstructor
public class VillagerTradeWsHandler implements EngineMessageHandler {

    private final WorldEngineManager engineManager;

    @Override
    public String type() {
        return "villagerTradeSelect";
    }

    @Override
    public Set<String> supportedTypes() {
        return Set.of("villagerTradeSelect", "villagerTradeClick", "villagerTradeDrag",
                "villagerTradeCollect",
                "dropVillagerTradeCursor", "closeVillagerTrade");
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        long mobId = WsFields.integer(message, "mobId");
        if ("closeVillagerTrade".equals(message.path("type").asString())) {
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.CloseVillagerTrade(context.nickname(), mobId));
            return;
        }
        String type = message.path("type").asString();
        if ("dropVillagerTradeCursor".equals(type)) {
            engineManager.enqueue(context.worldId(), new PlayerAction.DropVillagerTradeCursor(
                    context.nickname(), mobId, WsFields.booleanValue(message, "one")));
            return;
        }
        if ("villagerTradeCollect".equals(type)) {
            engineManager.enqueue(context.worldId(), new PlayerAction.CollectVillagerTrade(
                    context.nickname(), mobId, area(message), WsFields.integer(message, "slot")));
            return;
        }
        if ("villagerTradeDrag".equals(type)) {
            JsonNode targets = message.path("targets");
            if (!targets.isArray() || targets.isEmpty() || targets.size() > 38) {
                throw new IllegalArgumentException("targets는 1~38개의 거래 슬롯 배열이어야 합니다.");
            }
            PlayerAction.VillagerTradeArea[] areas =
                    new PlayerAction.VillagerTradeArea[targets.size()];
            int[] slots = new int[targets.size()];
            for (int index = 0; index < targets.size(); index++) {
                areas[index] = area(targets.get(index));
                if (areas[index] == PlayerAction.VillagerTradeArea.RESULT) {
                    throw new IllegalArgumentException("result 슬롯은 드래그 대상일 수 없습니다.");
                }
                slots[index] = WsFields.integer(targets.get(index), "slot");
            }
            engineManager.enqueue(context.worldId(), new PlayerAction.VillagerTradeDrag(
                    context.nickname(), mobId, areas, slots, button(message)));
            return;
        }
        if ("villagerTradeClick".equals(type)) {
            engineManager.enqueue(context.worldId(), new PlayerAction.VillagerTradeClick(
                    context.nickname(), mobId, area(message), WsFields.integer(message, "slot"),
                    button(message), WsFields.booleanValue(message, "shift")));
            return;
        }
        int offer = WsFields.integer(message, "offer");
        if (offer < 0) {
            throw new IllegalArgumentException("offer는 0 이상이어야 합니다.");
        }
        engineManager.enqueue(context.worldId(),
                new PlayerAction.VillagerTradeSelect(context.nickname(), mobId, offer));
    }

    private static PlayerAction.VillagerTradeArea area(JsonNode node) {
        return switch (WsFields.text(node, "area")) {
            case "inventory" -> PlayerAction.VillagerTradeArea.INVENTORY;
            case "payment" -> PlayerAction.VillagerTradeArea.PAYMENT;
            case "result" -> PlayerAction.VillagerTradeArea.RESULT;
            default -> throw new IllegalArgumentException("area는 inventory, payment 또는 result여야 합니다.");
        };
    }

    private static com.gameexpert.engine.inventory.PlayerInventory.CraftButton button(JsonNode node) {
        return switch (WsFields.text(node, "button")) {
            case "left" -> com.gameexpert.engine.inventory.PlayerInventory.CraftButton.LEFT;
            case "right" -> com.gameexpert.engine.inventory.PlayerInventory.CraftButton.RIGHT;
            default -> throw new IllegalArgumentException("button은 left 또는 right여야 합니다.");
        };
    }
}
