package com.gameexpert.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * [제공코드] 틱 엔진 튜닝/QA 프로퍼티.
 *
 * <ul>
 *   <li>{@code game.time-scale} (기본 1): 틱당 월드 시간 증가 배수. QA는 낮밤을 빠르게 보려고 50 등으로 올립니다.</li>
 *   <li>{@code game.persistence.block-flush-ms} (기본 5000): 블록 diff 버퍼를 DB로 내보내는 주기.</li>
 *   <li>{@code game.qa-invulnerable} (기본 false): QA 서버에서만 환경 피해를 끕니다.</li>
 *   <li>{@code game.qa-godmode} (기본 false): QA 서버에서만 <b>모든</b> 플레이어 피해(몹 근접·
 *       투사체·폭발까지 포함)를 끕니다. {@code qa-invulnerable} 이 환경 피해만 껐던 것과 달리
 *       전투 피해도 건너뛰므로, 몹이 도는 월드에서 장시간 QA 를 돌 때만 켭니다.</li>
 *   <li>{@code game.qa-frozen-time} (기본 -1): QA 서버의 월드 시간을 지정 시각에 고정합니다.</li>
 *   <li>{@code game.ws.inbound-rate-per-second} (기본 120): 세션별 인바운드 WS 메시지 허용 속도.</li>
 *   <li>{@code game.ws.inbound-burst} (기본 240): 그 속도에 더해 순간적으로 허용하는 버스트 크기.</li>
 *   <li>{@code game.ws.allowed-origins} (기본 로컬 개발 origin): WS 핸드셰이크를 허용할 Origin 패턴 목록.</li>
 * </ul>
 */
@Component
public class EngineProperties {

    /** 다른 QA/일반 월드와 섞이지 않는 전체 콘텐츠 QA 월드 계약. */
    public static final String CONTENT_QA_WORLD_NAME = "webcraft-content-qa-v1-qa";
    public static final long CONTENT_QA_WORLD_SEED = 0x5743_5141L; // "WCQA", signed-int32 safe
    /** Version-bound dense owner-turn performance world; never selected outside QA seeding. */
    public static final String PERFORMANCE_QA_WORLD_NAME = "webcraft-perf-263-v1-qa";
    public static final long PERFORMANCE_QA_WORLD_SEED = 0x5743_5031L; // "WCP1", signed-int32 safe

    /**
     * WS Origin 허용 목록의 기본값.
     *
     * <p>같은 origin에서 온 요청(정적 클라이언트를 서버가 직접 서빙하는 정상 배포)은 스프링이 항상
     * 허용하므로, 기본값은 vite dev 서버처럼 포트가 다른 로컬 개발 origin만 추가로 엽니다.
     */
    public static final String DEFAULT_ALLOWED_ORIGINS =
            "http://localhost:[*],http://127.0.0.1:[*]";

    private final int timeScale;
    private final long blockFlushMs;
    private final boolean qaInvulnerable;
    private final boolean qaGodmode;
    private final int qaFrozenTime;
    private final boolean qaSeeding;
    private final int wsInboundRatePerSecond;
    private final int wsInboundBurst;
    private final List<String> wsAllowedOrigins;

    /** 틱 엔진만 쓰는 테스트용 축약 생성자. WS 방어 값은 운영 기본값을 그대로 씁니다. */
    public EngineProperties(int timeScale, long blockFlushMs, boolean qaInvulnerable, int qaFrozenTime) {
        this(timeScale, blockFlushMs, qaInvulnerable, false, qaFrozenTime, false, 120, 240,
                DEFAULT_ALLOWED_ORIGINS);
    }

    /** QA 무적 스위치를 쓰지 않는 호출부용 축약 생성자. {@code qa-godmode} 는 기본 off 다. */
    public EngineProperties(int timeScale, long blockFlushMs, boolean qaInvulnerable,
            int qaFrozenTime, boolean qaSeeding, int wsInboundRatePerSecond, int wsInboundBurst,
            String wsAllowedOrigins) {
        this(timeScale, blockFlushMs, qaInvulnerable, false, qaFrozenTime, qaSeeding,
                wsInboundRatePerSecond, wsInboundBurst, wsAllowedOrigins);
    }

    @Autowired
    public EngineProperties(
            @Value("${game.time-scale:1}") int timeScale,
            @Value("${game.persistence.block-flush-ms:5000}") long blockFlushMs,
            @Value("${game.qa-invulnerable:false}") boolean qaInvulnerable,
            @Value("${game.qa-godmode:false}") boolean qaGodmode,
            @Value("${game.qa-frozen-time:-1}") int qaFrozenTime,
            @Value("${game.qa-seeding:false}") boolean qaSeeding,
            @Value("${game.ws.inbound-rate-per-second:120}") int wsInboundRatePerSecond,
            @Value("${game.ws.inbound-burst:240}") int wsInboundBurst,
            @Value("${game.ws.allowed-origins:" + DEFAULT_ALLOWED_ORIGINS + "}") String wsAllowedOrigins) {
        this.timeScale = Math.max(1, timeScale);
        this.blockFlushMs = blockFlushMs;
        this.qaInvulnerable = qaInvulnerable;
        this.qaGodmode = qaGodmode;
        this.qaFrozenTime = qaFrozenTime;
        this.qaSeeding = qaSeeding;
        this.wsInboundRatePerSecond = Math.max(1, wsInboundRatePerSecond);
        this.wsInboundBurst = Math.max(this.wsInboundRatePerSecond, wsInboundBurst);
        this.wsAllowedOrigins = parseOrigins(wsAllowedOrigins);
    }

    /** 빈 항목을 제거한 Origin 패턴 목록. 전부 비면 same-origin만 허용됩니다. */
    private static List<String> parseOrigins(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    public int timeScale() {
        return timeScale;
    }

    public long blockFlushMs() {
        return blockFlushMs;
    }

    public boolean qaInvulnerable() {
        return qaInvulnerable;
    }

    /**
     * QA 전용 무적(전투 포함). 기본 false 이고, 프로덕션 경로는 이 값을 한 곳
     * ({@code PlayerTickState.damage})에서만 읽습니다.
     */
    public boolean qaGodmode() {
        return qaGodmode;
    }

    public int qaFrozenTime() {
        return qaFrozenTime;
    }

    public boolean qaSeeding() {
        return qaSeeding;
    }

    /** QA 시딩이 켜진 정확한 예약 이름에만 고정 시드를 사용합니다. */
    public boolean isContentQaWorld(String worldName) {
        return qaSeeding && CONTENT_QA_WORLD_NAME.equals(worldName);
    }

    /** QA seeding and the exact reserved performance-world name must both agree. */
    public boolean isPerformanceQaWorld(String worldName) {
        return qaSeeding && PERFORMANCE_QA_WORLD_NAME.equals(worldName);
    }

    public int wsInboundRatePerSecond() {
        return wsInboundRatePerSecond;
    }

    public int wsInboundBurst() {
        return wsInboundBurst;
    }

    public List<String> wsAllowedOrigins() {
        return wsAllowedOrigins;
    }
}
