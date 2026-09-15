package com.gameexpert.world.dimension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.gameexpert.terrain.Blocks;
import org.springframework.stereotype.Component;

/** 키·포털 매핑 정본. 예약된 콘텐츠는 플러그인 설치 전에는 목적지가 되지 않는다. */
@Component
public class DimensionRegistry {
    public static final String OVERWORLD = "overworld";
    public static final String FLESH_NETHER = "flesh_nether";
    public static final String VOID_END = "void_end";
    public static final int PORTAL_DWELL_TICKS = 10;
    public static final int PORTAL_COOLDOWN_TICKS = 30;

    private final Map<String, DimensionDefinition> definitions = new LinkedHashMap<>();

    public DimensionRegistry() {
        register(new DimensionDefinition(OVERWORLD, true, 0, 0, null,
                DimensionEnvironment.legacyOverworld()));
        register(new DimensionDefinition(FLESH_NETHER, false, Blocks.NETHER_PORTAL,
                Blocks.NETHER_PORTAL, null, DimensionEnvironment.legacyOverworld()));
        register(new DimensionDefinition(VOID_END, false, Blocks.END_PORTAL,
                Blocks.END_PORTAL, null, DimensionEnvironment.legacyOverworld()));
    }

    public synchronized void register(DimensionDefinition definition) {
        java.util.Objects.requireNonNull(definition);
        if (definitions.containsKey(definition.key())) {
            throw new IllegalStateException("dimension key already registered: " + definition.key());
        }
        requireUniquePortal(definition);
        definitions.put(definition.key(), definition);
    }

    /** 예약 키의 최초 활성화만 허용한다. 실행 중 활성 차원의 계약을 바꾸지 않는다. */
    public synchronized void activate(DimensionDefinition definition) {
        java.util.Objects.requireNonNull(definition);
        DimensionDefinition prior = definitions.get(definition.key());
        if (prior == null || prior.enabled() || !definition.enabled()
                || prior.entryPortalBlock() != definition.entryPortalBlock()
                || prior.returnPortalBlock() != definition.returnPortalBlock()) {
            throw new IllegalStateException("dimension activation does not match its reservation");
        }
        requireUniquePortal(definition);
        definitions.put(definition.key(), definition);
    }

    private void requireUniquePortal(DimensionDefinition definition) {
        for (DimensionDefinition existing : definitions.values()) {
            if (!existing.key().equals(definition.key()) && definition.entryPortalBlock() != 0
                    && existing.entryPortalBlock() == definition.entryPortalBlock()) {
                throw new IllegalArgumentException("portal already belongs to " + existing.key());
            }
        }
    }

    public synchronized DimensionDefinition requireRegistered(String key) {
        DimensionDefinition result = definitions.get(key);
        if (result == null) throw new IllegalArgumentException("unregistered dimension: " + key);
        return result;
    }

    public synchronized DimensionDefinition requireEnabled(String key) {
        DimensionDefinition result = requireRegistered(key);
        if (!result.enabled()) throw new IllegalStateException("dimension is not enabled: " + key);
        return result;
    }

    public synchronized Map<String, DimensionDefinition> snapshot() {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    /** 목적지는 현재 권위 차원과 실제 접촉 블록으로만 결정한다. */
    public synchronized Optional<DimensionDefinition> destination(String currentKey, int portalBlock) {
        DimensionDefinition current = requireEnabled(currentKey);
        if (!OVERWORLD.equals(currentKey)) {
            return portalBlock == current.returnPortalBlock()
                    ? Optional.of(requireEnabled(OVERWORLD)) : Optional.empty();
        }
        return definitions.values().stream()
                .filter(d -> d.enabled() && d.entryPortalBlock() != 0
                        && d.entryPortalBlock() == portalBlock)
                .findFirst();
    }
}
