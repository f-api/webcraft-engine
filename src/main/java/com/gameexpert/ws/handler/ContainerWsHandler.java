package com.gameexpert.ws.handler;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.gameexpert.engine.PlayerAction;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.engine.inventory.PlayerInventory.ContainerArea;
import com.gameexpert.engine.inventory.PlayerInventory.CraftButton;
import com.gameexpert.ws.WsMessageContext;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * [CONTAINER-CURSOR] 보관 컨테이너(좌표 상자·큰 상자·통, 몹 화물)의 바닐라 커서 클릭을 월드 틱
 * 큐에 넣는다. 기존 {@code moveChestItem}/{@code moveMobCargoItem}(전량 이송 한 줄)은 그대로
 * 남고, 이 핸들러는 그 위에 얹히는 append-only 계약이다.
 *
 * <p>여기서 보는 것은 <b>형태</b>뿐이다 — 세션 존재·사거리·칸 수·병합·수량은 전부 틱 스레드가
 * 다시 판정한다({@code ChestWsHandler} 와 같은 신뢰 경계).
 */
@Component
@RequiredArgsConstructor
public class ContainerWsHandler implements EngineMessageHandler {

    /** 드래그 한 번이 지나갈 수 있는 최대 칸 수(큰 상자 54 + 인벤토리 36). */
    private static final int MAX_DRAG_TARGETS = 90;

    private final WorldEngineManager engineManager;

    @Override
    public String type() {
        return "containerClick";
    }

    @Override
    public Set<String> supportedTypes() {
        return Set.of("containerClick", "containerDrag", "containerCollect",
                "dropContainerCursor", "closeMinecartCargo", "crafterSlotState");
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        String type = message.path("type").asString();
        if ("crafterSlotState".equals(type)) {
            int slot = WsFields.integer(message, "slot");
            if (slot < 0 || slot >= 9) {
                throw new IllegalArgumentException("crafter slot must be 0..8.");
            }
            engineManager.enqueue(context.worldId(), new PlayerAction.CrafterSlotState(
                    context.nickname(), WsFields.integer(message, "x"),
                    WsFields.integer(message, "y"), WsFields.integer(message, "z"), slot,
                    WsFields.booleanValue(message, "enabled")));
            return;
        }
        JsonNode targetNode = message.path("target");
        if ("closeMinecartCargo".equals(type)
                || "minecartCargo".equals(targetNode.path("kind").asString())) {
            generatedCargo(context, message, targetNode, type);
            return;
        }
        PlayerAction.ContainerRef target = target(targetNode);
        if ("dropContainerCursor".equals(type)) {
            engineManager.enqueue(context.worldId(), new PlayerAction.DropContainerCursor(
                    context.nickname(), target, WsFields.booleanValue(message, "one")));
            return;
        }
        if ("containerCollect".equals(type)) {
            engineManager.enqueue(context.worldId(), new PlayerAction.CollectContainer(
                    context.nickname(), target, area(message), WsFields.integer(message, "slot")));
            return;
        }
        CraftButton button = button(message);
        if ("containerDrag".equals(type)) {
            JsonNode targets = message.path("targets");
            if (!targets.isArray() || targets.isEmpty() || targets.size() > MAX_DRAG_TARGETS) {
                throw new IllegalArgumentException(
                        "targets는 1~" + MAX_DRAG_TARGETS + "개의 컨테이너 슬롯 배열이어야 합니다.");
            }
            ContainerArea[] areas = new ContainerArea[targets.size()];
            int[] slots = new int[targets.size()];
            for (int index = 0; index < targets.size(); index++) {
                areas[index] = area(targets.get(index));
                slots[index] = WsFields.integer(targets.get(index), "slot");
            }
            engineManager.enqueue(context.worldId(), new PlayerAction.ContainerDrag(
                    context.nickname(), target, areas, slots, button));
            return;
        }
        engineManager.enqueue(context.worldId(), new PlayerAction.ContainerClick(
                context.nickname(), target, area(message),
                WsFields.integer(message, "slot"), button,
                WsFields.booleanValue(message, "shift")));
    }

    /** 기존 원자적 전량 이송과 정확한 세션 닫기만 연결한다. 커서 조작으로 대체하지 않는다. */
    private void generatedCargo(WsMessageContext context, JsonNode message,
            JsonNode target, String type) {
        if (!target.isObject() || target.size() != 4
                || !"minecartCargo".equals(WsFields.text(target, "kind"))
                || WsFields.integer(target, "schema") != 1) {
            throw new IllegalArgumentException("정확한 schema 1 minecartCargo 대상이 필요합니다.");
        }
        long entityId = WsFields.longInteger(target, "entityId");
        long sessionId = WsFields.longInteger(target, "sessionId");
        if (entityId <= 0 || entityId > 9_007_199_254_740_991L
                || sessionId <= 0 || sessionId > 9_007_199_254_740_991L) {
            throw new IllegalArgumentException("화물 개체와 세션은 양의 안전 정수여야 합니다.");
        }
        if ("closeMinecartCargo".equals(type)) {
            engineManager.enqueue(context.worldId(), new PlayerAction.CloseGeneratedEntityCargo(
                    context.nickname(), entityId, sessionId));
            return;
        }
        if (!"containerClick".equals(type) || button(message) != CraftButton.LEFT
                || !WsFields.booleanValue(message, "shift")) {
            throw new IllegalArgumentException("광산 수레 전량 이송은 Shift 왼쪽 클릭이어야 합니다.");
        }
        ContainerArea area = area(message);
        int slot = WsFields.integer(message, "slot");
        if (slot < 0 || slot >= (area == ContainerArea.CONTAINER ? 27 : 36)) {
            throw new IllegalArgumentException("화물 이송 슬롯이 범위를 벗어났습니다.");
        }
        engineManager.enqueue(context.worldId(), new PlayerAction.GeneratedEntityCargoTransfer(
                context.nickname(), entityId, sessionId, area == ContainerArea.CONTAINER, slot));
    }

    private static PlayerAction.ContainerRef target(JsonNode node) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("target은 컨테이너 대상 객체여야 합니다.");
        }
        return switch (WsFields.text(node, "kind")) {
            case "chest" -> PlayerAction.ContainerRef.chest(
                    WsFields.integer(node, "x"),
                    WsFields.integer(node, "y"),
                    WsFields.integer(node, "z"));
            case "mobCargo" -> PlayerAction.ContainerRef.mobCargo(
                    WsFields.longInteger(node, "mobId"));
            // [CONTAINER-MENUS] A placed chest or hopper minecart's cargo menu.
            case "entityCargo" -> {
                long entityId = WsFields.longInteger(node, "entityId");
                if (entityId <= 0 || entityId > 9_007_199_254_740_991L) {
                    throw new IllegalArgumentException("entityId must be a positive safe integer.");
                }
                yield PlayerAction.ContainerRef.entityCargo(entityId);
            }
            default -> throw new IllegalArgumentException(
                    "target.kind는 chest, mobCargo 또는 entityCargo여야 합니다.");
        };
    }

    private static ContainerArea area(JsonNode node) {
        return switch (WsFields.text(node, "area")) {
            case "inventory" -> ContainerArea.INVENTORY;
            case "container" -> ContainerArea.CONTAINER;
            default -> throw new IllegalArgumentException(
                    "area는 inventory 또는 container여야 합니다.");
        };
    }

    private static CraftButton button(JsonNode message) {
        return switch (WsFields.text(message, "button")) {
            case "left" -> CraftButton.LEFT;
            case "right" -> CraftButton.RIGHT;
            default -> throw new IllegalArgumentException("button은 left 또는 right여야 합니다.");
        };
    }
}
