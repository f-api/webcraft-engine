package com.gameexpert.map.dto;

import java.util.Arrays;

import lombok.Getter;

/** 월드 저장 지도 한 장의 권위 값입니다. */
@Getter
public final class WorldMapData {

    public static final int SIZE = 128;
    public static final int COLOR_COUNT = SIZE * SIZE;

    private final Long worldId;
    private final int mapId;
    private final int centerX;
    private final int centerZ;
    private final int scale;
    private final boolean locked;
    private final long revision;
    private final byte[] colors;
    private final TargetMarker targetMarker;

    /** Located-map target; absent on ordinary and previously saved maps. */
    public record TargetMarker(String type, int x, int z) {
        public TargetMarker {
            if (type == null || type.isBlank() || type.length() > 128)
                throw new IllegalArgumentException("map target type is required");
        }
    }

    public WorldMapData(Long worldId, int mapId, int centerX, int centerZ,
            int scale, boolean locked, byte[] colors, long revision) {
        this(worldId, mapId, centerX, centerZ, scale, locked, colors, revision, null);
    }

    public WorldMapData(Long worldId, int mapId, int centerX, int centerZ,
            int scale, boolean locked, byte[] colors, long revision, TargetMarker targetMarker) {
        if (worldId == null || worldId <= 0) {
            throw new IllegalArgumentException("worldId must be positive");
        }
        if (mapId <= 0) {
            throw new IllegalArgumentException("mapId must be positive");
        }
        if (scale < 0 || scale > 4) {
            throw new IllegalArgumentException("map scale must be 0..4");
        }
        if (!isCanonicalCenter(centerX, scale) || !isCanonicalCenter(centerZ, scale)) {
            throw new IllegalArgumentException("map center must align to its scale grid");
        }
        if (colors == null || colors.length != COLOR_COUNT) {
            throw new IllegalArgumentException("map colors must contain exactly " + COLOR_COUNT + " bytes");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("map revision must be non-negative");
        }
        this.worldId = worldId;
        this.mapId = mapId;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.scale = scale;
        this.locked = locked;
        this.colors = Arrays.copyOf(colors, colors.length);
        this.revision = revision;
        this.targetMarker = targetMarker;
    }

    /** 저장/와이어 호출부가 내부 버퍼를 바꾸지 못하도록 항상 복사합니다. */
    public byte[] getColors() {
        return Arrays.copyOf(colors, colors.length);
    }

    /**
     * Cartography preserves the source center while a newly located exploration map uses the
     * vanilla scale grid ({@code floor((target + 64) / span) * span + span / 2 - 64}).
     */
    private static boolean isCanonicalCenter(int center, int scale) {
        if (Math.floorMod(center, SIZE) == 0) {
            return true;
        }
        long span = (long) SIZE << scale;
        return Math.floorMod((long) center + 64L, span) == span / 2L;
    }
}
