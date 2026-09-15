package com.gameexpert.world;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import com.gameexpert.common.ServiceUnavailableException;

/** Keeps world entry closed until persisted generation profiles have been validated. */
@Component
public final class WorldBaselineReadiness {

    private final AtomicBoolean ready = new AtomicBoolean();

    public boolean isReady() {
        return ready.get();
    }

    public void requireReady() {
        if (!ready.get()) throw new ServiceUnavailableException("WORLD_BASELINE_INITIALIZING");
    }

    public void markReady() {
        ready.set(true);
    }
}
