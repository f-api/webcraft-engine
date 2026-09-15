package com.gameexpert.ws.handler;

import com.gameexpert.engine.ChestInventory;
import com.gameexpert.engine.PlayerAction;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.ws.WsMessageContext;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** 열린 상자 세션의 전체 스택 이동/닫기를 틱 큐에 넣는다. */
@Component
@RequiredArgsConstructor
public class ChestWsHandler implements EngineMessageHandler {

    private final WorldEngineManager engineManager;

    @Override
    public String type() { return "moveChestItem"; }

    @Override
    public Set<String> supportedTypes() { return Set.of("moveChestItem", "closeChest"); }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        int x = WsFields.integer(message, "x");
        int y = WsFields.integer(message, "y");
        int z = WsFields.integer(message, "z");
        if ("closeChest".equals(message.path("type").asString())) {
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.CloseChest(context.nickname(), x, y, z));
            return;
        }
        String from = WsFields.text(message, "from");
        if (!"chest".equals(from) && !"inventory".equals(from)) {
            throw new IllegalArgumentException("from은 chest 또는 inventory여야 합니다.");
        }
        boolean fromChest = "chest".equals(from);
        int sourceSlot = WsFields.integer(message, "slot");
        int sourceSlots = fromChest ? ChestInventory.DOUBLE_SLOTS : PlayerInventory.SLOTS;
        if (sourceSlot < 0 || sourceSlot >= sourceSlots) {
            throw new IllegalArgumentException("slot이 원본 보관함 범위를 벗어났습니다.");
        }
        engineManager.enqueue(context.worldId(), new PlayerAction.MoveChestItem(
                context.nickname(), x, y, z, fromChest, sourceSlot));
    }
}
