package com.gameexpert.ws.handler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.json.JsonMapper;

public class MoveFieldsSmoke {
    public static void main(String[] args) throws Exception {
        JsonMapper mapper = JsonMapper.builder().build();
        check(WsFields.optionalFinalSceneActionId(mapper.readTree("{}")) == null, "absent action ID");
        check("scene-1".equals(WsFields.optionalFinalSceneActionId(
                mapper.readTree("{\"finalSceneActionId\":\"scene-1\"}"))), "string action ID");
        check("".equals(WsFields.optionalFinalSceneActionId(
                mapper.readTree("{\"finalSceneActionId\":\"\"}"))), "empty string preserves existing behavior");
        for (String value : java.util.List.of("null", "1", "true", "[]", "{}")) {
            try {
                WsFields.optionalFinalSceneActionId(mapper.readTree("{\"finalSceneActionId\":" + value + "}"));
                throw new AssertionError("non-string action ID accepted: " + value);
            } catch (IllegalArgumentException expected) {
                check(expected.getMessage().contains("finalSceneActionId"), "field error");
            }
        }
        check(MoveFieldsSmoke.class.getResource("MoveWsHandler.class") == null, "handler must be student-owned");
        try (InputStream input = MoveFieldsSmoke.class.getResourceAsStream("/META-INF/webcraft-components.txt")) {
            check(input != null, "component index exists");
            String index = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            check(!index.contains("com.gameexpert.ws.handler.MoveWsHandler"), "no bundled move component");
        }
        System.out.println("PASS: move action ID compatibility and student-owned handler extraction");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
