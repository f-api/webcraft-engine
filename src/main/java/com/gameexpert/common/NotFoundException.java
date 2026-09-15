package com.gameexpert.common;

import lombok.Getter;

/**
 * 요청한 자원을 찾지 못했을 때 던지는 예외입니다. (→ HTTP 404)
 *
 * error 코드(예: "WORLD_NOT_FOUND")를 함께 담아, 응답의 error 필드로 그대로 전달됩니다.
 */
@Getter
public class NotFoundException extends RuntimeException {

    private final String error;

    public NotFoundException(String error) {
        super(error);
        this.error = error;
    }
}
