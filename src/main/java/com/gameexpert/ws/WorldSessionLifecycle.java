package com.gameexpert.ws;

import com.gameexpert.api.SessionRegistry;
import static com.gameexpert.ws.SessionAttributes.ATTR_NICKNAME;
import static com.gameexpert.ws.SessionAttributes.ATTR_WORLD_ID;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import com.gameexpert.engine.WorldEngineManager;
import com.gameexpert.ws.dto.WsMessages.PlayerLeave;
import com.gameexpert.ws.dto.WsMessages.WorldLeaveReady;

import lombok.RequiredArgsConstructor;

/** 플레이어 상태 확정, 닉네임 점유 해제와 접속자 방송을 하나의 퇴장 순서로 유지합니다. */
@Component
@RequiredArgsConstructor
public class WorldSessionLifecycle {

    private static final Logger log = LoggerFactory.getLogger(WorldSessionLifecycle.class);

    private final SessionRegistry registry;
    private final GameTransport broadcaster;
    private final SessionCleanup cleanup;
    private final WorldEngineManager engineManager;

    /** 전송이 이미 닫힌 일반 연결 종료 경로입니다. */
    public boolean release(WebSocketSession session) {
        return release(session, false);
    }

    /**
     * 메뉴의 명시적 퇴장 경로입니다. 상태와 닉네임 점유를 먼저 해제한 다음 완료 메시지를 보내므로,
     * 클라이언트가 이를 받은 직후 같은 닉네임으로 재입장해도 4002와 경합하지 않습니다.
     */
    public boolean releaseGracefully(WebSocketSession session) {
        return release(session, true);
    }

    private boolean release(WebSocketSession session, boolean graceful) {
        Long worldId = (Long) session.getAttributes().get(ATTR_WORLD_ID);
        String nickname = (String) session.getAttributes().get(ATTR_NICKNAME);
        if (worldId == null || nickname == null) {
            broadcaster.forget(session);
            return false;
        }

        SessionRegistry.Entry current = registry.get(worldId, nickname);
        if (current == null || current.session() != session) {
            broadcaster.forget(session);
            return false;
        }
        if (!current.beginRelease()) return false;

        // 닫힌 전송에는 더 보내지 않습니다. 명시적 퇴장은 완료 응답을 보내야 하므로 전송 계층만
        // 잠시 유지하되, registry에서 제거된 뒤에는 월드 broadcast 대상에서 이미 제외됩니다.
        if (!graceful) broadcaster.forget(session);

        SessionRegistry.Entry removed;
        try {
            engineManager.onPlayerLeave(worldId, nickname, current.connectionId());
        } catch (RuntimeException exception) {
            log.error("퇴장 상태 확정 또는 저장 제출 실패: world={}, nickname={}",
                    worldId, nickname, exception);
        } finally {
            removed = cleanup.remove(worldId, nickname, session);
        }
        if (removed == null) {
            broadcaster.forget(session);
            return false;
        }

        broadcaster.broadcast(worldId, new PlayerLeave(nickname));

        if (graceful) {
            broadcaster.sendTo(session, new WorldLeaveReady());
            broadcaster.forget(session);
            try {
                session.close(CloseStatus.NORMAL);
            } catch (IOException | RuntimeException exception) {
                log.debug("정리 완료 세션 종료 실패: session={}", session.getId(), exception);
            }
        }
        return true;
    }
}
