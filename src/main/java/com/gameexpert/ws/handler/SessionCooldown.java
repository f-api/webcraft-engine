package com.gameexpert.ws.handler;

import org.springframework.web.socket.WebSocketSession;

/**
 * 세션 하나에 매달리는 입력 쿨다운.
 *
 * <p>이모트에만 쿨다운이 있고 채팅에는 없어 정책이 갈렸던 것을 하나의 구현으로 모읍니다.
 * 상태를 {@link WebSocketSession#getAttributes()}에 두므로 세션이 끝나면 함께 사라지고,
 * 재접속하면 새 세션의 새 쿨다운으로 시작합니다(닉네임 기준으로 남지 않음).
 *
 * <p>쿨다운은 남용 방어이지 정확한 스로틀이 아니므로, 창을 넘긴 뒤 첫 요청 시각을 기준으로
 * 다음 창을 엽니다.
 */
public final class SessionCooldown {

    private final String attribute;
    private final long windowMs;

    /**
     * @param owner    쿨다운을 소유한 핸들러(속성 키 이름으로만 사용)
     * @param windowMs 두 요청 사이의 최소 간격
     */
    public SessionCooldown(Class<?> owner, long windowMs) {
        this.attribute = owner.getName() + ".cooldownAt";
        this.windowMs = windowMs;
    }

    public long windowMs() {
        return windowMs;
    }

    /** 쿨다운을 통과하면 {@code true}를 돌려주고 다음 창을 엽니다. */
    public boolean tryConsume(WebSocketSession session) {
        return tryConsume(session, System.currentTimeMillis());
    }

    /** 테스트가 시간을 주입하는 진입점입니다. */
    public boolean tryConsume(WebSocketSession session, long nowMs) {
        // 세션이 없는 경로(내부 호출·테스트)는 쿨다운 대상이 아닙니다.
        if (session == null) return true;
        var attributes = session.getAttributes();
        synchronized (attributes) {
            Object stored = attributes.get(attribute);
            if (stored instanceof Long last && nowMs - last < windowMs) return false;
            attributes.put(attribute, nowMs);
            return true;
        }
    }
}
