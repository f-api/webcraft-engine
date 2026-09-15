package com.gameexpert.common;

import lombok.Getter;

/**
 * 현재 상태와 충돌해 요청을 처리할 수 없을 때 던지는 예외입니다. (→ HTTP 409)
 *
 * 예: 이미 존재하는 닉네임(DUPLICATE_NICKNAME), 접속자가 있는 월드 삭제(WORLD_IN_USE).
 */
@Getter
public class ConflictException extends RuntimeException {

    private final String error;

    public ConflictException(String error) {
        super(error);
        this.error = error;
    }
}
