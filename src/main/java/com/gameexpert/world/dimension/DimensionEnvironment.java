package com.gameexpert.world.dimension;

import lombok.Getter;
import lombok.experimental.Accessors;

/** 차원 전용 표현 데이터. legacy는 기존 일반 월드의 계산과 기본값을 그대로 사용한다. */
@Getter
@Accessors(fluent = true)
public final class DimensionEnvironment {
    private final boolean custom;
    private final boolean useDayNight;
    private final boolean sunMoon;
    private final boolean stars;
    private final Profile day;
    private final Profile night;

    private DimensionEnvironment(boolean custom, boolean useDayNight, boolean sunMoon,
            boolean stars, Profile day, Profile night) {
        this.custom = custom;
        this.useDayNight = useDayNight;
        this.sunMoon = sunMoon;
        this.stars = stars;
        this.day = day;
        this.night = night;
    }

    public static DimensionEnvironment legacyOverworld() {
        return new DimensionEnvironment(false, true, true, true, null, null);
    }

    public static DimensionEnvironment custom(boolean useDayNight, boolean sunMoon,
            boolean stars, Profile day, Profile night) {
        return new DimensionEnvironment(true, useDayNight, sunMoon, stars,
                java.util.Objects.requireNonNull(day), java.util.Objects.requireNonNull(night));
    }

    @Getter
    @Accessors(fluent = true)
    public static final class Profile {
        private final int skyColor;
        private final int fogColor;
        private final double fogDistance;
        private final double ambientLight;
        private final double lightPropagationFactor;

        public Profile(int skyColor, int fogColor, double fogDistance,
                double ambientLight, double lightPropagationFactor) {
            if ((skyColor & ~0xffffff) != 0 || (fogColor & ~0xffffff) != 0
                    || !Double.isFinite(fogDistance) || fogDistance <= 0
                    || !Double.isFinite(ambientLight) || ambientLight < 0 || ambientLight > 1
                    || !Double.isFinite(lightPropagationFactor)
                    || lightPropagationFactor <= 0 || lightPropagationFactor > 1) {
                throw new IllegalArgumentException("invalid dimension environment profile");
            }
            this.skyColor = skyColor;
            this.fogColor = fogColor;
            this.fogDistance = fogDistance;
            this.ambientLight = ambientLight;
            this.lightPropagationFactor = lightPropagationFactor;
        }
    }
}
