package com.gameexpert.common;

import lombok.Getter;

/**
 * 요청자가 그 자원에 대해 권한이 없을 때 던지는 예외입니다. (→ HTTP 403)
 *
 * 예: 다른 사람이 만든 월드를 지우려는 요청(NOT_WORLD_OWNER).
 */
@Getter
public class ForbiddenException extends RuntimeException {

    private final String error;

    public ForbiddenException(String error) {
        super(error);
        this.error = error;
    }
}
