package com.gameexpert.world.dimension;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 실제 공급자 capability와 descriptor를 함께 검사한다. 예약만으로는 세계를 열 수 없다. */
@Component
public class DimensionProviders {
    private final DimensionRegistry registry;
    private final Map<String, DimensionChunkProvider> providers = new HashMap<>();

    public DimensionProviders(DimensionRegistry registry) {
        this.registry = java.util.Objects.requireNonNull(registry);
    }

    public synchronized void register(String key, DimensionChunkProvider provider) {
        registry.requireRegistered(key);
        if (DimensionRegistry.OVERWORLD.equals(key)) {
            throw new IllegalArgumentException("overworld retains its canonical source");
        }
        if (providers.putIfAbsent(key, java.util.Objects.requireNonNull(provider)) != null) {
            throw new IllegalStateException("dimension provider already installed: " + key);
        }
    }

    public synchronized void install(DimensionDefinition definition, DimensionChunkProvider provider) {
        register(definition.key(), provider);
        try {
            registry.activate(definition);
        } catch (RuntimeException | Error failure) {
            providers.remove(definition.key(), provider);
            throw failure;
        }
    }

    public synchronized boolean available(String key) {
        DimensionDefinition definition = registry.requireRegistered(key);
        return definition.enabled() && !DimensionRegistry.OVERWORLD.equals(key)
                && providers.containsKey(key);
    }

    public synchronized DimensionChunkProvider require(String key) {
        if (!available(key)) throw new IllegalStateException("dimension provider unavailable: " + key);
        return providers.get(key);
    }
}
