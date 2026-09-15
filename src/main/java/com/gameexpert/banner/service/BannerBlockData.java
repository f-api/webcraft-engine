package com.gameexpert.banner.service;

import com.gameexpert.engine.inventory.ItemComponentData;
import java.util.List;

/** Immutable authority snapshot of one placed banner's block-entity payload. */
public final class BannerBlockData {
    private final int x;
    private final int y;
    private final int z;
    private final List<ItemComponentData.BannerLayer> patterns;

    public BannerBlockData(int x, int y, int z, List<ItemComponentData.BannerLayer> patterns) {
        if (patterns == null || patterns.size() > ItemComponentData.MAX_BANNER_LAYERS
                || patterns.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("invalid placed banner patterns");
        }
        this.x = x;
        this.y = y;
        this.z = z;
        this.patterns = List.copyOf(patterns);
    }

    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public List<ItemComponentData.BannerLayer> patterns() { return patterns; }
}
