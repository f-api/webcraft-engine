package com.gameexpert.engine;

import com.gameexpert.ws.dto.WsMessages.PlayerRelocated;

/** Publishes a confirmed positional discontinuity without changing passenger or player state. */
final class PlayerRelocation {
    private PlayerRelocation() { }
    static void publish(WorldRuntime world, PlayerTickState player) {
        var message = new PlayerRelocated(player.nickname(), player.x(), player.y(), player.z());
        for (var observer : world.players().values()) {
            if (observer == player) continue;
            var session = world.session(observer.nickname());
            if (session != null) world.ctx().broadcaster().enqueueSendToFromTick(world.worldId(), session, message);
        }
    }
}
