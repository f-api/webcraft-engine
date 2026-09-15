package com.gameexpert.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import com.gameexpert.config.EngineProperties;

import lombok.RequiredArgsConstructor;

/**
 * 세션별 인바운드 WS 메시지 레이트리밋(토큰 버킷).
 *
 * <p>인증이 닉네임뿐이라 접속 자체는 값싸므로, 단일 세션이 초당 수만 건을 밀어 넣어 파싱·핸들러·
 * 액션 큐를 통해 힙과 틱 스레드를 고갈시키는 것을 막는 1차 방어선입니다. 라우팅 이전에 검사하므로
 * JSON 파싱 비용조차 발생하지 않습니다.
 *
 * <p>버킷은 {@link WebSocketSession#getAttributes()}에 보관해 세션 수명과 함께 사라집니다.
 * 별도 맵을 두면 연결이 끊긴 세션의 항목이 남아 그 자체가 메모리 누수 표면이 됩니다.
 *
 * <p>거부 통지도 남용되면 아웃바운드 폭주가 되므로 세션당
 * {@value #NOTICE_INTERVAL_MS}ms에 한 번만 보냅니다.
 */
@Component
@RequiredArgsConstructor
public class InboundRateLimiter {

    /** 같은 세션에 거부 통지를 다시 보내기까지의 최소 간격. */
    public static final long NOTICE_INTERVAL_MS = 1_000;

    private static final String BUCKET_ATTRIBUTE = InboundRateLimiter.class.getName() + ".bucket";

    private final EngineProperties properties;

    /**
     * 이 메시지를 처리해도 되는지 판단합니다.
     *
     * @return 통과면 {@link Decision#ACCEPT}, 예산 초과면 거부(통지 여부에 따라 두 값 중 하나)
     */
    public Decision check(WebSocketSession session) {
        return check(session, System.currentTimeMillis());
    }

    /** 테스트가 시간을 주입하는 진입점입니다. */
    Decision check(WebSocketSession session, long nowMs) {
        var attributes = session.getAttributes();
        Bucket bucket;
        synchronized (attributes) {
            Object stored = attributes.get(BUCKET_ATTRIBUTE);
            if (stored instanceof Bucket existing) {
                bucket = existing;
            } else {
                bucket = new Bucket(properties.wsInboundBurst(), nowMs);
                attributes.put(BUCKET_ATTRIBUTE, bucket);
            }
        }
        return bucket.consume(nowMs, properties.wsInboundRatePerSecond(),
                properties.wsInboundBurst());
    }

    public enum Decision {
        /** 예산 안이라 정상 처리합니다. */
        ACCEPT,
        /** 예산을 넘겨 버립니다. 통지 간격 안이므로 조용히 버립니다. */
        REJECT_SILENT,
        /** 예산을 넘겨 버리고, 이번에는 클라이언트에 거부를 알립니다. */
        REJECT_NOTIFY;

        public boolean accepted() {
            return this == ACCEPT;
        }
    }

    /** 세션 하나의 토큰 버킷. 같은 세션의 메시지는 직렬 처리가 보장되지 않으므로 동기화합니다. */
    private static final class Bucket {

        private double tokens;
        private long lastRefillMs;
        private long lastNoticeMs;

        private Bucket(int initialTokens, long nowMs) {
            this.tokens = initialTokens;
            this.lastRefillMs = nowMs;
            // 첫 거부는 곧바로 통지할 수 있어야 하므로 통지 간격만큼 과거로 둡니다.
            this.lastNoticeMs = nowMs - NOTICE_INTERVAL_MS;
        }

        private synchronized Decision consume(long nowMs, int ratePerSecond, int burst) {
            long elapsed = Math.max(0, nowMs - lastRefillMs);
            lastRefillMs = nowMs;
            tokens = Math.min(burst, tokens + elapsed * ratePerSecond / 1000.0);
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return Decision.ACCEPT;
            }
            if (nowMs - lastNoticeMs >= NOTICE_INTERVAL_MS) {
                lastNoticeMs = nowMs;
                return Decision.REJECT_NOTIFY;
            }
            return Decision.REJECT_SILENT;
        }
    }
}
