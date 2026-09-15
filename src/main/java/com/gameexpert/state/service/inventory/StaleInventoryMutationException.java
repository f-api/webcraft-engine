package com.gameexpert.state.service.inventory;

/** 이미 더 최신 스냅샷이 저장된 aggregate에 대한 지연 쓰기. */
public final class StaleInventoryMutationException extends RuntimeException {
    public StaleInventoryMutationException(String aggregate) {
        super("stale inventory mutation for " + aggregate);
    }
}
