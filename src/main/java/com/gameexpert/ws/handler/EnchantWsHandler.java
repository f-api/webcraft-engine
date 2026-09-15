package com.gameexpert.ws.handler;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.gameexpert.engine.EnchantingInventory;
import com.gameexpert.engine.PlayerAction;
import com.gameexpert.engine.PlayerAction.EnchantArea;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.inventory.PlayerInventory.CraftButton;
import com.gameexpert.ws.WsMessageContext;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/** [SURV-X] 열린 좌표 인챈트 테이블의 슬롯 클릭·제안 선택·커서 드랍·닫기를 월드 틱 큐에 넣습니다. */
@Component
@RequiredArgsConstructor
public class EnchantWsHandler implements EngineMessageHandler {

    private final WorldEngineManager engineManager;

    @Override
    public String type() {
        return "enchantClick";
    }

    @Override
    public Set<String> supportedTypes() {
        return Set.of(
                "enchantClick", "enchantDrag", "enchantCollect", "selectEnchantOffer",
                "dropEnchantCursor", "closeEnchanting");
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        int x = WsFields.integer(message, "x");
        int y = WsFields.integer(message, "y");
        int z = WsFields.integer(message, "z");
        String type = message.path("type").asString();
        if ("closeEnchanting".equals(type)) {
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.CloseEnchanting(context.nickname(), x, y, z));
            return;
        }
        if ("selectEnchantOffer".equals(type)) {
            engineManager.enqueue(context.worldId(), new PlayerAction.SelectEnchantOffer(
                    context.nickname(), x, y, z, WsFields.integer(message, "offer")));
            return;
        }
        if ("dropEnchantCursor".equals(type)) {
            engineManager.enqueue(context.worldId(), new PlayerAction.DropEnchantCursor(
                    context.nickname(), x, y, z, WsFields.booleanValue(message, "one")));
            return;
        }
        if ("enchantCollect".equals(type)) {
            engineManager.enqueue(context.worldId(), new PlayerAction.CollectEnchant(
                    context.nickname(), x, y, z, area(message),
                    WsFields.integer(message, "slot")));
            return;
        }
        if ("enchantDrag".equals(type)) {
            JsonNode targets = message.path("targets");
            if (!targets.isArray() || targets.isEmpty() || targets.size() > 38) {
                throw new IllegalArgumentException("targets는 1~38개의 인챈트 슬롯 배열이어야 합니다.");
            }
            EnchantArea[] areas = new EnchantArea[targets.size()];
            int[] slots = new int[targets.size()];
            for (int index = 0; index < targets.size(); index++) {
                JsonNode target = targets.get(index);
                areas[index] = area(target);
                slots[index] = WsFields.integer(target, "slot");
            }
            engineManager.enqueue(context.worldId(), new PlayerAction.EnchantDrag(
                    context.nickname(), x, y, z, areas, slots, button(message)));
            return;
        }
        EnchantArea area = area(message);
        int slot = WsFields.integer(message, "slot");
        int slots = area == EnchantArea.ENCHANT
                ? EnchantingInventory.SLOTS : PlayerInventory.SLOTS;
        if (slot < 0 || slot >= slots) {
            throw new IllegalArgumentException("slot이 인챈트 메뉴 범위를 벗어났습니다.");
        }
        engineManager.enqueue(context.worldId(), new PlayerAction.EnchantClick(
                context.nickname(), x, y, z, area, slot, button(message),
                WsFields.booleanValue(message, "shift")));
    }

    private static EnchantArea area(JsonNode message) {
        return switch (WsFields.text(message, "area")) {
            case "inventory" -> EnchantArea.INVENTORY;
            case "enchant" -> EnchantArea.ENCHANT;
            default -> throw new IllegalArgumentException(
                    "area는 inventory 또는 enchant여야 합니다.");
        };
    }

    private static CraftButton button(JsonNode message) {
        return switch (WsFields.text(message, "button")) {
            case "left" -> CraftButton.LEFT;
            case "right" -> CraftButton.RIGHT;
            default -> throw new IllegalArgumentException(
                    "button은 left 또는 right여야 합니다.");
        };
    }
}
