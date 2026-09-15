package com.gameexpert.engine;

import com.gameexpert.world.dimension.flesh.FleshColonyLayout;
import java.util.function.Predicate;

/** Layout-only lookup: no chunk access, generation, or block queries. */
public final class FleshDetector {
    private FleshDetector() { }
    public static BlockPos nearest(int seed, double x, double y, double z, Predicate<BlockPos> retired) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
        BlockPos nearest = null;
        double best = 512.0 * 512.0;
        int minX = (int)Math.floor((x - 512) / 256), maxX = (int)Math.floor((x + 512) / 256);
        int minZ = (int)Math.floor((z - 512) / 256), maxZ = (int)Math.floor((z + 512) / 256);
        for (int cx = minX; cx <= maxX; cx++) for (int cz = minZ; cz <= maxZ; cz++) {
            var point = FleshColonyLayout.forCell(seed, cx, cz).core();
            var core = new BlockPos(point.x(), point.y(), point.z());
            if (retired.test(core)) continue;
            double dx = core.x() + .5 - x, dy = core.y() + .5 - y, dz = core.z() + .5 - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < best || nearest == null && distance == best) { best = distance; nearest = core; }
        }
        return nearest;
    }
}
