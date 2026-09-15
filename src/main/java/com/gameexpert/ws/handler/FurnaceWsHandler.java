package com.gameexpert.ws.handler;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.gameexpert.engine.FurnaceInventory;
import com.gameexpert.engine.PlayerAction;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.engine.inventory.PlayerInventory.CraftButton;
import com.gameexpert.engine.inventory.PlayerInventory.FurnaceArea;
import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.ws.WsMessageContext;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/** 열린 좌표 화로의 슬롯 클릭·레시피 북·커서 드랍·닫기를 월드 틱 큐에 넣습니다. */
@Component
@RequiredArgsConstructor
public class FurnaceWsHandler implements EngineMessageHandler {

    private final WorldEngineManager engineManager;

    @Override
    public String type() {
        return "furnaceClick";
    }

    @Override
    public Set<String> supportedTypes() {
        return Set.of(
                "furnaceClick", "furnaceDrag", "furnaceCollect", "placeFurnaceRecipe",
                "dropFurnaceCursor", "closeFurnace");
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        int x = WsFields.integer(message, "x");
        int y = WsFields.integer(message, "y");
        int z = WsFields.integer(message, "z");
        String type = message.path("type").asString();
        if ("closeFurnace".equals(type)) {
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.CloseFurnace(context.nickname(), x, y, z));
            return;
        }
        if ("placeFurnaceRecipe".equals(type)) {
            engineManager.enqueue(context.worldId(), new PlayerAction.PlaceFurnaceRecipe(
                    context.nickname(), x, y, z,
                    WsFields.text(message, "recipeId"),
                    WsFields.booleanValue(message, "maximum")));
            return;
        }
        if ("dropFurnaceCursor".equals(type)) {
            engineManager.enqueue(context.worldId(), new PlayerAction.DropFurnaceCursor(
                    context.nickname(), x, y, z,
                    WsFields.booleanValue(message, "one")));
            return;
        }
        if ("furnaceCollect".equals(type)) {
            engineManager.enqueue(context.worldId(), new PlayerAction.CollectFurnace(
                    context.nickname(), x, y, z, area(message),
                    WsFields.integer(message, "slot")));
            return;
        }
        if ("furnaceDrag".equals(type)) {
            JsonNode targets = message.path("targets");
            if (!targets.isArray() || targets.isEmpty() || targets.size() > 39) {
                throw new IllegalArgumentException("targets는 1~39개의 화로 슬롯 배열이어야 합니다.");
            }
            FurnaceArea[] areas = new FurnaceArea[targets.size()];
            int[] slots = new int[targets.size()];
            for (int index = 0; index < targets.size(); index++) {
                JsonNode target = targets.get(index);
                areas[index] = area(target);
                slots[index] = WsFields.integer(target, "slot");
            }
            engineManager.enqueue(context.worldId(), new PlayerAction.FurnaceDrag(
                    context.nickname(), x, y, z, areas, slots, button(message)));
            return;
        }
        FurnaceArea area = area(message);
        int slot = WsFields.integer(message, "slot");
        int slots = area == FurnaceArea.FURNACE
                ? FurnaceInventory.SLOTS : PlayerInventory.SLOTS;
        if (slot < 0 || slot >= slots) {
            throw new IllegalArgumentException("slot이 화로 메뉴 범위를 벗어났습니다.");
        }
        engineManager.enqueue(context.worldId(), new PlayerAction.FurnaceClick(
                context.nickname(), x, y, z, area, slot, button(message),
                WsFields.booleanValue(message, "shift")));
    }

    private static FurnaceArea area(JsonNode message) {
        return switch (WsFields.text(message, "area")) {
            case "inventory" -> FurnaceArea.INVENTORY;
            case "furnace" -> FurnaceArea.FURNACE;
            default -> throw new IllegalArgumentException(
                    "area는 inventory 또는 furnace여야 합니다.");
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
