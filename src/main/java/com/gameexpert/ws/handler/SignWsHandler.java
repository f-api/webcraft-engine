package com.gameexpert.ws.handler;

import com.gameexpert.engine.PlayerAction;
import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.sign.dto.SignBlockData;
import com.gameexpert.ws.WsMessageContext;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SignWsHandler implements EngineMessageHandler {
    private final WorldEngineManager engineManager;
    @Override public String type() { return "editSign"; }
    @Override public void handle(WsMessageContext context, JsonNode message) {
        int x = WsFields.integer(message, "x");
        int y = WsFields.integer(message, "y");
        int z = WsFields.integer(message, "z");
        JsonNode linesNode = message.path("lines");
        if (!linesNode.isArray() || linesNode.size() != SignBlockData.LINE_COUNT) {
            throw new IllegalArgumentException("sign requires four lines");
        }
        List<String> lines = new ArrayList<>(SignBlockData.LINE_COUNT);
        for (JsonNode line : linesNode) {
            if (!line.isString()) throw new IllegalArgumentException("sign line must be text");
            lines.add(line.asString());
        }
        // Constructor performs the exact length/control validation before the action reaches the tick.
        lines = new SignBlockData(x, y, z, lines).lines();
        engineManager.enqueue(context.worldId(),
                new PlayerAction.EditSign(context.nickname(), x, y, z, lines));
    }
}
