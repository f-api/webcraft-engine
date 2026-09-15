package com.gameexpert.ws.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.gameexpert.state.service.PlayerDimensionIdentity;
import com.gameexpert.world.dimension.DimensionEnvironment;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;

/** fluent 내부 데이터는 명시적으로 투영한다. Jackson의 JavaBean 추론에 의존하지 않는다. */
@Getter
public final class DimensionWelcome {
    @JsonUnwrapped private final WsMessages.Welcome welcome;
    private final long rootWorldId;
    private final String dimension;
    private final long travelRevision;
    private final Map<String, Object> environment;

    public DimensionWelcome(WsMessages.Welcome welcome, PlayerDimensionIdentity identity,
            DimensionEnvironment environment) {
        this.welcome = java.util.Objects.requireNonNull(welcome);
        this.rootWorldId = identity.rootWorldId();
        this.dimension = identity.dimension();
        this.travelRevision = identity.travelRevision();
        this.environment = project(environment);
    }

    public static Map<String, Object> project(DimensionEnvironment value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("custom", value.custom());
        result.put("useDayNight", value.useDayNight());
        result.put("sunMoon", value.sunMoon());
        result.put("stars", value.stars());
        result.put("day", profile(value.day()));
        result.put("night", profile(value.night()));
        return java.util.Collections.unmodifiableMap(result);
    }

    private static Map<String, Object> profile(DimensionEnvironment.Profile value) {
        return value == null ? null : Map.of(
                "skyColor", value.skyColor(), "fogColor", value.fogColor(),
                "fogDistance", value.fogDistance(), "ambientLight", value.ambientLight(),
                "lightPropagationFactor", value.lightPropagationFactor());
    }
}
