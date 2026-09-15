package com.gameexpert.world.dimension;

import lombok.Getter;
import lombok.experimental.Accessors;

/** 콘텐츠 플러그인이 공급하는 불변 차원 설명. 좌표는 해당 차원 내부 좌표다. */
@Getter
@Accessors(fluent = true)
public final class DimensionDefinition {
    private final String key;
    private final boolean enabled;
    private final int entryPortalBlock;
    private final int returnPortalBlock;
    private final Arrival arrival;
    private final DimensionEnvironment environment;

    public DimensionDefinition(String key, boolean enabled, int entryPortalBlock,
            int returnPortalBlock, Arrival arrival, DimensionEnvironment environment) {
        if (key == null || !key.matches("[a-z][a-z0-9_]{0,31}")
                || entryPortalBlock < 0 || entryPortalBlock > 65535
                || returnPortalBlock < 0 || returnPortalBlock > 65535) {
            throw new IllegalArgumentException("invalid dimension definition");
        }
        if (DimensionRegistry.OVERWORLD.equals(key)) {
            if (!enabled || entryPortalBlock != 0 || returnPortalBlock != 0 || arrival != null
                    || environment == null || environment.custom()) {
                throw new IllegalArgumentException("overworld retains its existing provider and environment");
            }
        } else if (enabled && (entryPortalBlock == 0 || returnPortalBlock == 0 || arrival == null)) {
            throw new IllegalArgumentException("enabled dimension requires portal and arrival policy");
        }
        this.key = key;
        this.enabled = enabled;
        this.entryPortalBlock = entryPortalBlock;
        this.returnPortalBlock = returnPortalBlock;
        this.arrival = arrival;
        this.environment = java.util.Objects.requireNonNull(environment);
    }

    @Getter
    @Accessors(fluent = true)
    public static final class Arrival {
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;

        public Arrival(double x, double y, double z, float yaw, float pitch) {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                throw new IllegalArgumentException("finite dimension arrival required");
            }
            this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
        }
        public double[] pose() { return new double[] {x, y, z, yaw, pitch}; }
    }
}
