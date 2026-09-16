package com.gameexpert.ws;

import com.gameexpert.engine.WorldEngineManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatCommands {
    private final WorldEngineManager engineManager;

    public String resolve(Long worldId, String nickname, String content) {
        String remote = com.gameexpert.cluster.ClusterRuntime.resolveEdgeCommand(worldId, nickname, content);
        if (remote != null) return remote;
        if (content.strip().equals("/pos")) {
            var pose = engineManager.playerPose(worldId, nickname);
            if (pose != null) {
                return String.format("📍 %.1f, %.1f, %.1f", pose.getX(), pose.getY(), pose.getZ());
            }
        }
        return content;
    }
}
