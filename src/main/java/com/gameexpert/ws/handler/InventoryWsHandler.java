package com.gameexpert.ws.handler;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.gameexpert.engine.PlayerAction;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.engine.inventory.PlayerInventory.CraftArea;
import com.gameexpert.engine.inventory.PlayerInventory.CraftButton;
import com.gameexpert.engine.inventory.PlayerInventory.ContainerArea;
import com.gameexpert.engine.inventory.ArmorSlot;
import com.gameexpert.ws.WsMessageContext;

import tools.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * 제작 컨테이너·일반 슬롯 이동 입력을 월드 틱 큐에 넣습니다.
 */
@Component
@RequiredArgsConstructor
public class InventoryWsHandler implements EngineMessageHandler {

    private static final Logger dropAudit = LoggerFactory.getLogger("gameexpert.audit.drop");
    private final WorldEngineManager engineManager;

    @Override
    public String type() {
        return "openCrafting";
    }

    @Override
    public Set<String> supportedTypes() {
        return Set.of("openCrafting", "craftingClick", "craftingDrag",
                "placeCraftingRecipe", "selectStonecutterRecipe", "collectCrafting",
                "anvilRename", "selectLoomPattern", "beaconConfirm",
                "openLectern", "lecternPage", "closeLectern",
                "openBook", "editBook", "signBook", "closeBook",
                "dropCraftingCursor", "closeCrafting", "moveSlot", "moveArmor", "dropItem",
                "swapHands", "swapOffhand", "brewingClick", "brewingDrag", "brewingCollect",
                "dropBrewingCursor",
                "closeBrewing");

    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        String type = message.path("type").asString();
        if ("openCrafting".equals(type)) {
            String stationName = WsFields.text(message, "station");
            PlayerAction.CraftStation station;
            if ("inventory".equals(stationName)) {
                station = PlayerAction.CraftStation.INVENTORY;
            } else if ("table".equals(stationName)) {
                station = PlayerAction.CraftStation.TABLE;
            } else if ("brewing".equals(stationName)) {
                station = PlayerAction.CraftStation.BREWING;
            } else if ("stonecutter".equals(stationName)) {
                station = PlayerAction.CraftStation.STONECUTTER;
            } else if ("smithing".equals(stationName)) {
                station = PlayerAction.CraftStation.SMITHING;
            } else if ("anvil".equals(stationName)) {
                station = PlayerAction.CraftStation.ANVIL;
            } else if ("cartography".equals(stationName)) {
                station = PlayerAction.CraftStation.CARTOGRAPHY;
            } else if ("grindstone".equals(stationName)) {
                station = PlayerAction.CraftStation.GRINDSTONE;
            } else if ("loom".equals(stationName)) {
                station = PlayerAction.CraftStation.LOOM;
            } else if ("beacon".equals(stationName)) {
                station = PlayerAction.CraftStation.BEACON;
            } else {
                throw new IllegalArgumentException(
                        "지원하지 않는 제작 스테이션입니다.");
            }
            int x = WsFields.integer(message, "x");
            int y = WsFields.integer(message, "y");
            int z = WsFields.integer(message, "z");
            engineManager.enqueue(context.worldId(), new PlayerAction.OpenCrafting(
                    context.nickname(), station, x, y, z, WsFields.optionalRequestId(message)));
        } else if ("brewingCollect".equals(type)) {
            engineManager.enqueue(context.worldId(), new PlayerAction.CollectBrewing(
                    context.nickname(),
                    "inventory".equals(WsFields.text(message, "area"))
                            ? ContainerArea.INVENTORY : ContainerArea.CONTAINER,
                    "inventory".equals(WsFields.text(message, "area"))
                            ? WsFields.integer(message, "slot")
                            : brewingSlot(message, WsFields.text(message, "area")),
                    WsFields.integer(message, "x"),
                    WsFields.integer(message, "y"), WsFields.integer(message, "z")));
        } else if ("brewingDrag".equals(type)) {
            JsonNode targets = message.path("targets");
            if (!targets.isArray() || targets.isEmpty() || targets.size() > 41) {
                throw new IllegalArgumentException("targets must contain 1..41 brewing slots");
            }
            ContainerArea[] areas = new ContainerArea[targets.size()];
            int[] slots = new int[targets.size()];
            for (int index = 0; index < targets.size(); index++) {
                JsonNode target = targets.get(index);
                String targetArea = WsFields.text(target, "area");
                if ("inventory".equals(targetArea)) {
                    areas[index] = ContainerArea.INVENTORY;
                    slots[index] = WsFields.integer(target, "slot");
                } else {
                    areas[index] = ContainerArea.CONTAINER;
                    slots[index] = brewingSlot(target, targetArea);
                }
            }
            engineManager.enqueue(context.worldId(), new PlayerAction.BrewingDrag(
                    context.nickname(), areas, slots, craftButton(message),
                    WsFields.integer(message, "x"), WsFields.integer(message, "y"),
                    WsFields.integer(message, "z")));
        } else if ("brewingClick".equals(type)) {
            String areaName = WsFields.text(message, "area");
            ContainerArea area;
            int slot;
            if ("inventory".equals(areaName)) {
                area = ContainerArea.INVENTORY;
                slot = WsFields.integer(message, "slot");
            } else {
                area = ContainerArea.CONTAINER;
                slot = brewingSlot(message, areaName);
            }
            engineManager.enqueue(context.worldId(), new PlayerAction.BrewingClick(
                    context.nickname(), area, slot, craftButton(message),
                    WsFields.booleanValue(message, "shift"),
                    WsFields.integer(message, "x"), WsFields.integer(message, "y"),
                    WsFields.integer(message, "z")));
        } else if ("dropBrewingCursor".equals(type)) {
            engineManager.enqueue(context.worldId(), new PlayerAction.DropBrewingCursor(
                    context.nickname(), WsFields.booleanValue(message, "one"),
                    WsFields.integer(message, "x"), WsFields.integer(message, "y"),
                    WsFields.integer(message, "z")));
        } else if ("closeBrewing".equals(type)) {
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.CloseBrewing(context.nickname(),
                            WsFields.integer(message, "x"), WsFields.integer(message, "y"),
                            WsFields.integer(message, "z"), WsFields.optionalRequestId(message)));
        } else if ("craftingClick".equals(type)) {
            CraftArea area = switch (WsFields.text(message, "area")) {
                case "inventory" -> CraftArea.INVENTORY;
                case "grid" -> CraftArea.GRID;
                case "result" -> CraftArea.RESULT;
                case "armor" -> CraftArea.ARMOR;
                case "offhand" -> CraftArea.OFFHAND;
                default -> throw new IllegalArgumentException(
                        "area는 inventory, grid 또는 result여야 합니다.");
            };
            CraftButton button = switch (WsFields.text(message, "button")) {
                case "left" -> CraftButton.LEFT;
                case "right" -> CraftButton.RIGHT;
                default -> throw new IllegalArgumentException("button은 left 또는 right여야 합니다.");
            };
            engineManager.enqueue(context.worldId(), new PlayerAction.CraftingClick(
                    context.nickname(), area, WsFields.integer(message, "slot"),
                    button, WsFields.booleanValue(message, "shift")));
        } else if ("craftingDrag".equals(type)) {
            JsonNode targets = message.path("targets");
            if (!targets.isArray() || targets.isEmpty() || targets.size() > 45) {
                throw new IllegalArgumentException("targets는 1~45개의 제작 슬롯 배열이어야 합니다.");
            }
            CraftArea[] areas = new CraftArea[targets.size()];
            int[] slots = new int[targets.size()];
            for (int index = 0; index < targets.size(); index++) {
                JsonNode target = targets.get(index);
                areas[index] = switch (WsFields.text(target, "area")) {
                    case "inventory" -> CraftArea.INVENTORY;
                    case "grid" -> CraftArea.GRID;
                    case "armor" -> CraftArea.ARMOR;
                    case "offhand" -> CraftArea.OFFHAND;
                    default -> throw new IllegalArgumentException(
                            "드래그 대상 area는 inventory, grid, armor 또는 offhand여야 합니다.");
                };
                slots[index] = WsFields.integer(target, "slot");
            }
            CraftButton button = switch (WsFields.text(message, "button")) {
                case "left" -> CraftButton.LEFT;
                case "right" -> CraftButton.RIGHT;
                default -> throw new IllegalArgumentException("button은 left 또는 right여야 합니다.");
            };
            engineManager.enqueue(context.worldId(), new PlayerAction.CraftingDrag(
                    context.nickname(), areas, slots, button));
        } else if ("placeCraftingRecipe".equals(type)) {
            engineManager.enqueue(context.worldId(), new PlayerAction.PlaceCraftingRecipe(
                    context.nickname(), WsFields.text(message, "recipeId"),
                    WsFields.booleanValue(message, "maximum")));
        } else if ("selectStonecutterRecipe".equals(type)) {
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.SelectStonecutterRecipe(
                            context.nickname(), WsFields.text(message, "recipeId")));
        } else if ("anvilRename".equals(type)) {
            engineManager.enqueue(context.worldId(), new PlayerAction.AnvilRename(
                    context.nickname(), WsFields.text(message, "name")));
        } else if ("selectLoomPattern".equals(type)) {
            com.gameexpert.engine.inventory.LoomRules.Pattern pattern;
            try {
                pattern = com.gameexpert.engine.inventory.LoomRules.Pattern.valueOf(
                        WsFields.text(message, "pattern").toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("유효하지 않은 배너 무늬입니다.", invalid);
            }
            engineManager.enqueue(context.worldId(), new PlayerAction.SelectLoomPattern(
                    context.nickname(), pattern));
        } else if ("beaconConfirm".equals(type)) {
            // [BEACON] 효과 이름은 null(없음) 또는 신호기 효과 여섯 이름 중 하나다.
            engineManager.enqueue(context.worldId(), new PlayerAction.BeaconConfirm(
                    context.nickname(), beaconPower(message, "primary"),
                    beaconPower(message, "secondary")));
        } else if ("openLectern".equals(type)) {
            engineManager.enqueue(context.worldId(), new PlayerAction.OpenLectern(
                    context.nickname(), WsFields.integer(message, "x"),
                    WsFields.integer(message, "y"), WsFields.integer(message, "z"),
                    WsFields.optionalRequestId(message)));
        } else if ("lecternPage".equals(type)) {
            engineManager.enqueue(context.worldId(), new PlayerAction.LecternPage(
                    context.nickname(), WsFields.integer(message, "page")));
        } else if ("closeLectern".equals(type)) {
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.CloseLectern(context.nickname(), WsFields.optionalRequestId(message)));
        } else if ("openBook".equals(type)) {
            PlayerAction.Hand hand = switch (WsFields.text(message, "hand")) {
                case "main" -> PlayerAction.Hand.MAIN;
                case "offhand" -> PlayerAction.Hand.OFFHAND;
                default -> throw new IllegalArgumentException("hand must be main or offhand");
            };
            engineManager.enqueue(context.worldId(), new PlayerAction.OpenBook(
                    context.nickname(), hand, WsFields.optionalRequestId(message)));
        } else if ("editBook".equals(type)) {
            JsonNode pages = message.path("pages");
            if (!pages.isArray() || pages.isEmpty()
                    || pages.size() > com.gameexpert.engine.inventory.ItemComponentData.BookData.MAX_PAGES) {
                throw new IllegalArgumentException("book pages are required");
            }
            java.util.ArrayList<String> values = new java.util.ArrayList<>(pages.size());
            for (JsonNode page : pages) {
                if (!page.isTextual()) throw new IllegalArgumentException("book page must be text");
                values.add(page.asString());
            }
            engineManager.enqueue(context.worldId(), new PlayerAction.EditBook(
                    context.nickname(), java.util.List.copyOf(values)));
        } else if ("signBook".equals(type)) {
            engineManager.enqueue(context.worldId(), new PlayerAction.SignBook(
                    context.nickname(), WsFields.text(message, "title")));
        } else if ("closeBook".equals(type)) {
            engineManager.enqueue(context.worldId(), new PlayerAction.CloseBook(
                    context.nickname(), WsFields.optionalRequestId(message)));
        } else if ("collectCrafting".equals(type)) {
            CraftArea area = switch (WsFields.text(message, "area")) {
                case "inventory" -> CraftArea.INVENTORY;
                case "grid" -> CraftArea.GRID;
                case "armor" -> CraftArea.ARMOR;
                case "offhand" -> CraftArea.OFFHAND;
                default -> throw new IllegalArgumentException("collect area is invalid");
            };
            engineManager.enqueue(context.worldId(), new PlayerAction.CollectCrafting(
                    context.nickname(), area, WsFields.integer(message, "slot")));
        } else if ("dropCraftingCursor".equals(type)) {
            engineManager.enqueue(context.worldId(), new PlayerAction.DropCraftingCursor(
                    context.nickname(), WsFields.booleanValue(message, "one")));
        } else if ("closeCrafting".equals(type)) {
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.CloseCrafting(context.nickname(),
                            WsFields.longInteger(message, "sessionId"),
                            WsFields.optionalRequestId(message)));
        } else if ("swapHands".equals(type)) {
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.SwapHands(context.nickname()));
        } else if ("swapOffhand".equals(type)) {
            engineManager.enqueue(context.worldId(), new PlayerAction.SwapOffhand(
                    context.nickname(), WsFields.integer(message, "slot")));
        } else if ("moveSlot".equals(type)) {
            int from = WsFields.integer(message, "from");
            int to = WsFields.integer(message, "to");
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.MoveSlot(context.nickname(), from, to));
        } else if ("moveArmor".equals(type)) {
            ArmorSlot armorSlot = ArmorSlot.valueOf(
                    WsFields.text(message, "armorSlot").toUpperCase(java.util.Locale.ROOT));
            engineManager.enqueue(context.worldId(), new PlayerAction.MoveArmor(
                    context.nickname(), armorSlot, WsFields.integer(message, "inventorySlot")));
        } else {
            int slot = WsFields.integer(message, "slot");
            int count = WsFields.integer(message, "count");
            dropAudit.debug("phase=ingress world={} nickname={} slot={} count={}",
                    context.worldId(), context.nickname(), slot, count);
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.DropItem(context.nickname(), slot, count));
        }
    }

    private static int brewingSlot(JsonNode message, String areaName) {
        return switch (areaName) {
            case "fuel" -> 0;
            case "ingredient" -> 1;
            case "bottle" -> {
                int bottle = WsFields.integer(message, "slot");
                if (bottle < 0 || bottle > 2) {
                    throw new IllegalArgumentException("bottle slot must be 0..2");
                }
                yield bottle + 2;
            }
            default -> throw new IllegalArgumentException(
                    "area must be inventory, fuel, ingredient or bottle");
        };
    }

    /** [BEACON] 필수 키, 값은 null 또는 신호기 효과 이름. 모르는 이름은 거절한다. */
    private static com.gameexpert.engine.BeaconRules.Power beaconPower(JsonNode message, String field) {
        JsonNode value = message.get(field);
        if (value == null) throw new IllegalArgumentException(field + " is required");
        if (value.isNull()) return null;
        if (!value.isString()) throw new IllegalArgumentException(field + " must be a string or null");
        var power = com.gameexpert.engine.BeaconRules.Power.fromWireName(value.asString());
        if (power == null) throw new IllegalArgumentException("unknown beacon power");
        return power;
    }

    private static CraftButton craftButton(JsonNode message) {
        return switch (WsFields.text(message, "button")) {
            case "left" -> CraftButton.LEFT;
            case "right" -> CraftButton.RIGHT;
            default -> throw new IllegalArgumentException("button must be left or right");
        };
    }
}
