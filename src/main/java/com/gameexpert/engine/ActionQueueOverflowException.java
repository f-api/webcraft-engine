package com.gameexpert.engine;

/**
 * 액션 큐가 상한을 넘어 액션을 받지 않았음을 알리는 거부 신호입니다.
 *
 * <p>서버 결함이 아니라 클라이언트가 틱 소비 속도보다 빠르게 액션을 보낸 정상적인 방어 결과이므로,
 * WS 라우터는 이 예외를 500성 오류가 아닌 {@code QUEUE_FULL} 거부로 클라이언트에 돌려줍니다.
 */
public class ActionQueueOverflowException extends RuntimeException {

    public ActionQueueOverflowException(String message) {
        super(message);
    }
}
