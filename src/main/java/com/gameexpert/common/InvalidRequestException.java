package com.gameexpert.common;

import lombok.Getter;

/**
 * 요청 본문이 이 서버에서 받아들일 수 없는 형태일 때 던지는 예외입니다. (→ HTTP 400)
 *
 * <p>Jackson 이 파싱 단계에서 거절하는 {@code INVALID_REQUEST_BODY} 와 같은 오류 바디를
 * 컨트롤러/서비스 계층에서도 낼 수 있게 합니다. 예: QA 게이트가 꺼진 서버에 QA 전용 필드가
 * 실려 온 경우 — 그 필드를 모르는 서버와 구분되지 않아야 하므로 같은 코드로 거절합니다.
 */
@Getter
public class InvalidRequestException extends RuntimeException {

    private final String error;

    public InvalidRequestException(String error) {
        super(error);
        this.error = error;
    }
}
