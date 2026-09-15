package com.gameexpert.world.service;

import java.util.function.LongSupplier;

import com.gameexpert.common.InvalidRequestException;
import com.gameexpert.config.EngineProperties;

/**
 * 월드 생성 시드 결정 규칙 한 곳.
 *
 * <p>운영 서버에서 시드는 여전히 서버만 정합니다. QA 격리 서버({@code game.qa-seeding=true},
 * 디버그 권위 증거 엔드포인트와 같은 게이트)에서만 생성 요청이 시드를 직접 지정할 수 있어
 * 권위 비교(예: seed 0 의 눈 마을 (-54,222)·타이가 마을 (1,186))를 서버판과 정적판에서 같은
 * 좌표로 실행할 수 있습니다.</p>
 *
 * <p>게이트가 꺼진 서버는 그 필드를 아예 모르는 서버와 구분되지 않아야 하므로, 폐기된 {@code seed}
 * 필드와 같은 {@code 400 INVALID_REQUEST_BODY} 로 거절합니다.</p>
 */
public final class ExplicitWorldSeed {

    private ExplicitWorldSeed() {
    }

    /**
     * QA 전용 명시 시드를 받아들일 수 있는지 검사합니다(부수효과 전에 먼저 부르기 위한 진입점).
     * 명시 시드가 없으면 아무것도 하지 않습니다.
     */
    public static void requireAcceptable(EngineProperties properties, Long debugSeed) {
        if (debugSeed == null) return;
        if (properties == null || !properties.qaSeeding()) {
            throw new InvalidRequestException("INVALID_REQUEST_BODY");
        }
        if (debugSeed < Integer.MIN_VALUE || debugSeed > Integer.MAX_VALUE) {
            // 클라이언트 JS 비트연산 호환을 위해 저장 시드는 항상 signed int32 범위입니다.
            throw new InvalidRequestException("VALIDATION_FAILED");
        }
    }

    /**
     * 저장할 시드를 고릅니다.
     *
     * @param properties  엔진 설정(널이면 QA 게이트가 꺼진 것으로 봅니다)
     * @param worldName   월드 이름(예약된 QA 이름 판정용)
     * @param debugSeed   요청이 실어 온 QA 전용 명시 시드. 없으면 널
     * @param randomSeed  게이트/예약 이름에 해당하지 않을 때 쓰는 서버 난수 시드
     */
    public static long resolve(EngineProperties properties, String worldName, Long debugSeed,
            LongSupplier randomSeed) {
        if (debugSeed != null) {
            requireAcceptable(properties, debugSeed);
            return debugSeed;
        }
        if (properties != null && properties.isContentQaWorld(worldName)) {
            return EngineProperties.CONTENT_QA_WORLD_SEED;
        }
        if (properties != null && properties.isPerformanceQaWorld(worldName)) {
            return EngineProperties.PERFORMANCE_QA_WORLD_SEED;
        }
        return randomSeed.getAsLong();
    }
}
