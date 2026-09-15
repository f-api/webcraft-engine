package com.gameexpert.engine.persistence.finalcarrier;

/** Typed terminal failure for a malformed or internally inconsistent durable row. */
public final class FinalCarrierDurableStateException extends IllegalStateException {
    public FinalCarrierDurableStateException(String message) {
        super(message);
    }

    public FinalCarrierDurableStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
