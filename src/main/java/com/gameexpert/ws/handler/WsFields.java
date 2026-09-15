package com.gameexpert.ws.handler;

import com.gameexpert.engine.PlayerAction;

import tools.jackson.databind.JsonNode;

/** WebSocket field readers shared by all inbound handlers. */
final class WsFields {

    private WsFields() {
    }

    static boolean booleanValue(JsonNode message, String field) {
        JsonNode value = message.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException(field + "는 불리언이어야 합니다.");
        }
        return value.booleanValue();
    }

    static int integer(JsonNode message, String field) {
        JsonNode value = message.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException(field + "는 32비트 정수여야 합니다.");
        }
        return value.intValue();
    }

    static long longInteger(JsonNode message, String field) {
        JsonNode value = message.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new IllegalArgumentException(field + "는 64비트 정수여야 합니다.");
        }
        return value.longValue();
    }

    static double finiteNumber(JsonNode message, String field) {
        JsonNode value = message.get(field);
        if (value == null || !value.isNumber() || !Double.isFinite(value.doubleValue())) {
            throw new IllegalArgumentException(field + "는 유한한 숫자여야 합니다.");
        }
        return value.doubleValue();
    }

    static Long optionalRequestId(JsonNode message) {
        if (!message.has("requestId")) return null;
        long value = longInteger(message, "requestId");
        if (value <= 0 || value > 9_007_199_254_740_991L) {
            throw new IllegalArgumentException("requestId must be a positive safe integer");
        }
        return value;
    }

    static float finiteFloat(JsonNode message, String field) {
        float value = (float) finiteNumber(message, field);
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(field + "는 유한한 실수 범위여야 합니다.");
        }
        return value;
    }

    static String text(JsonNode message, String field) {
        JsonNode value = message.get(field);
        if (value == null || !value.isString()) {
            throw new IllegalArgumentException(field + "는 문자열이어야 합니다.");
        }
        return value.asString();
    }

    static String optionalFinalSceneActionId(JsonNode message) {
        JsonNode value = message.get("finalSceneActionId");
        if (value == null) {
            return null;
        }
        if (!value.isString()) {
            throw new IllegalArgumentException("finalSceneActionId 형식이 올바르지 않습니다.");
        }
        return value.asString();
    }

    static PlayerAction.Hand hand(JsonNode message) {
        return switch (text(message, "hand")) {
            case "main" -> PlayerAction.Hand.MAIN;
            case "offhand" -> PlayerAction.Hand.OFFHAND;
            default -> throw new IllegalArgumentException("hand는 main 또는 offhand여야 합니다.");
        };
    }
}
