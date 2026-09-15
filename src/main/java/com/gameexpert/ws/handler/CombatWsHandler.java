package com.gameexpert.ws.handler;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.gameexpert.engine.PlayerAction;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.ws.WsMessageContext;
import com.gameexpert.ws.dto.GeneratedEntityActionTarget;

import tools.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * 전투·핫바 입력을 월드 틱 큐에 넣습니다.
 * 사거리·쿨다운·무기 판정, 몹 피격·사망 방송과 선택 슬롯 추적은 서버 틱 루프가 처리합니다.
 */
@Component
@RequiredArgsConstructor
public class CombatWsHandler implements EngineMessageHandler {

    private static final java.util.regex.Pattern FINAL_SCENE_ACTION_ID =
            java.util.regex.Pattern.compile("[A-Za-z0-9._:-]{8,128}");

    private final WorldEngineManager engineManager;

    @Override
    public String type() {
        return "attack";
    }

    @Override
    public Set<String> supportedTypes() {
        return Set.of("attack", "spearStab", "selectSlot", "shieldBlock", "bowUse", "snowballThrow", "enderPearlThrow", "eggThrow",
                "potionThrow", "windChargeThrow", "enderEyeThrow",
                "equipArmor", "fishingRodUse", "fireworkUse", "glideImpact", "trophyItemUse",
                "entityAttack");
    }

    @Override
    public void handle(WsMessageContext context, JsonNode message) {
        if ("entityAttack".equals(message.path("type").asString())) {
            GeneratedEntityActionTarget.Attack parsed =
                    GeneratedEntityActionTarget.parseAttack(message);
            engineManager.enqueue(context.worldId(), new PlayerAction.GeneratedEntityAttack(
                    context.nickname(), parsed.target().kind(), parsed.target().entityId(),
                    parsed.sprinting()));
        } else if ("attack".equals(message.path("type").asString())) {
            long mobId = WsFields.longInteger(message, "mobId");
            boolean sprinting = WsFields.booleanValue(message, "sprinting");
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.Attack(context.nickname(), mobId, sprinting));
        } else if ("spearStab".equals(message.path("type").asString())) {
            engineManager.enqueue(context.worldId(), new PlayerAction.SpearStab(context.nickname()));
        } else if ("selectSlot".equals(message.path("type").asString())) {
            int slot = WsFields.integer(message, "slot");
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.SelectSlot(context.nickname(), slot));
        } else if ("shieldBlock".equals(message.path("type").asString())) {
            // 방패 홀드 입력은 이동 이력 검증 없이 그대로 틱 큐에 전달한다.
            engineManager.enqueue(context.worldId(), new PlayerAction.ShieldBlock(
                    context.nickname(), WsFields.booleanValue(message, "pressed"),
                    WsFields.hand(message)));
        } else if ("equipArmor".equals(message.path("type").asString())) {
            engineManager.enqueue(context.worldId(), new PlayerAction.EquipArmor(
                    context.nickname(), WsFields.hand(message)));
        } else if ("fishingRodUse".equals(message.path("type").asString())) {
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.FishingRodUse(context.nickname(), WsFields.hand(message)));
        } else if ("glideImpact".equals(message.path("type").asString())) {
            engineManager.enqueue(context.worldId(), new PlayerAction.GlideImpact(
                    context.nickname(), WsFields.finiteNumber(message, "lostSpeed"),
                    optionalFinalSceneActionId(message)));
        } else if ("trophyItemUse".equals(message.path("type").asString())) {
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.TrophyItemUse(context.nickname(), WsFields.hand(message)));
        } else if ("fireworkUse".equals(message.path("type").asString())) {
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.FireworkUse(context.nickname(), WsFields.hand(message),
                            optionalFinalSceneActionId(message)));
        } else if ("snowballThrow".equals(message.path("type").asString())) {
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.SnowballThrow(context.nickname(), WsFields.hand(message)));
        } else if ("enderPearlThrow".equals(message.path("type").asString())) {
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.EnderPearlThrow(context.nickname(), WsFields.hand(message)));
        } else if ("eggThrow".equals(message.path("type").asString())) {
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.EggThrow(context.nickname(), WsFields.hand(message)));
        } else if ("potionThrow".equals(message.path("type").asString())) {
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.PotionThrow(context.nickname(), WsFields.hand(message)));
        } else if ("windChargeThrow".equals(message.path("type").asString())) {
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.WindChargeThrow(context.nickname(), WsFields.hand(message)));
        } else if ("enderEyeThrow".equals(message.path("type").asString())) {
            engineManager.enqueue(context.worldId(),
                    new PlayerAction.EnderEyeThrow(context.nickname(), WsFields.hand(message)));
        } else {
            engineManager.enqueue(context.worldId(), new PlayerAction.BowUse(
                    context.nickname(), WsFields.booleanValue(message, "pressed"),
                    WsFields.hand(message),
                    message.has("cancel") && WsFields.booleanValue(message, "cancel")));
        }
    }

    private static String optionalFinalSceneActionId(JsonNode message) {
        JsonNode value = message.get("finalSceneActionId");
        if (value == null) return null;
        if (!value.isString() || !FINAL_SCENE_ACTION_ID.matcher(value.asString()).matches()) {
            throw new IllegalArgumentException("finalSceneActionId 형식이 올바르지 않습니다.");
        }
        return value.asString();
    }
}
