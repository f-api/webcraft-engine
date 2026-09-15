package com.gameexpert.ws.handler;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.gameexpert.engine.PlayerAction;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.ws.WsMessageContext;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

/**
 * [CONTAINER-MENUS] Player input on placed armor stands and minecarts, in the ordered player
 * action queue (the world tick thread re-checks reach, hands and every rule):
 *  - placedEntityPlace{x,y,z,face,hand}: use the held armor stand / minecart on a block face
 *  - placedEntityInteract{id,hand,hitY}: right click (hitY = hit height above the entity's feet)
 *  - placedEntityAttack{id}: left click
 *  - placedEntityDismount{}: the rider sneaks out
 *  - placedEntityRideInput{x,z}: the rider's world-space move intent (length at most 1)
 *  - closeEntityCargo{entityId}: close a chest / hopper minecart menu
 */
@Component
@RequiredArgsConstructor
public class PlacedEntityWsHandler implements EngineMessageHandler {

    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    private final WorldEngineManager engineManager;

    @Override
    public String type() {
        return "placedEntityPlace";
    }

    @Override
    public Set<String> supportedTypes() {
        return Set.of("placedEntityPlace", "placedEntityInteract", "placedEntityAttack",
                "placedEntityDismount", "placedEntityRideInput", "closeEntityCargo");
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        String nickname = context.nickname();
        PlayerAction.PlacedEntityCommand command = switch (message.path("type").asString()) {
            case "placedEntityPlace" -> {
                int face = WsFields.integer(message, "face");
                if (face < 0 || face > 5) throw new IllegalArgumentException("face must be 0..5.");
                yield new PlayerAction.PlacedEntityCommand(nickname,
                        PlayerAction.PlacedEntityCommand.Op.PLACE, 0L,
                        WsFields.integer(message, "x"), WsFields.integer(message, "y"),
                        WsFields.integer(message, "z"), face, offhand(message), 0.0, 0.0, 0.0);
            }
            case "placedEntityInteract" -> {
                double hitY = WsFields.finiteNumber(message, "hitY");
                if (hitY < -1.0 || hitY > 3.0) throw new IllegalArgumentException("hitY out of range.");
                yield new PlayerAction.PlacedEntityCommand(nickname,
                        PlayerAction.PlacedEntityCommand.Op.INTERACT, id(message, "id"), 0, 0, 0, 0,
                        offhand(message), hitY, 0.0, 0.0);
            }
            case "placedEntityAttack" -> new PlayerAction.PlacedEntityCommand(nickname,
                    PlayerAction.PlacedEntityCommand.Op.ATTACK, id(message, "id"), 0, 0, 0, 0,
                    false, 0.0, 0.0, 0.0);
            case "placedEntityDismount" -> new PlayerAction.PlacedEntityCommand(nickname,
                    PlayerAction.PlacedEntityCommand.Op.DISMOUNT, 0L, 0, 0, 0, 0, false, 0.0, 0.0,
                    0.0);
            case "placedEntityRideInput" -> {
                double x = WsFields.finiteNumber(message, "x");
                double z = WsFields.finiteNumber(message, "z");
                if (x * x + z * z > 1.0002) {
                    throw new IllegalArgumentException("ride input must have length at most 1.");
                }
                yield new PlayerAction.PlacedEntityCommand(nickname,
                        PlayerAction.PlacedEntityCommand.Op.RIDE_INPUT, 0L, 0, 0, 0, 0, false, 0.0,
                        x, z);
            }
            case "closeEntityCargo" -> new PlayerAction.PlacedEntityCommand(nickname,
                    PlayerAction.PlacedEntityCommand.Op.CLOSE_CARGO, id(message, "entityId"), 0, 0,
                    0, 0, false, 0.0, 0.0, 0.0);
            default -> throw new IllegalArgumentException("알 수 없는 개체 메시지입니다.");
        };
        engineManager.enqueue(context.worldId(), command);
    }

    private static boolean offhand(JsonNode message) {
        return WsFields.hand(message) == PlayerAction.Hand.OFFHAND;
    }

    private static long id(JsonNode message, String field) {
        long id = WsFields.longInteger(message, field);
        if (id <= 0 || id > MAX_SAFE_INTEGER) {
            throw new IllegalArgumentException(field + " must be a positive safe integer.");
        }
        return id;
    }
}
