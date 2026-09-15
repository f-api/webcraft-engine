package com.gameexpert.world.dimension.flesh;

import com.gameexpert.engine.BlockPos;
import com.gameexpert.engine.Fluids;
import com.gameexpert.terrain.Blocks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Bounded resident scanning; no chunk generation, offline catch-up or per-tick colony sweep. */
public final class FleshColonyEcology {
    public static final int SCAN_COLUMNS = 256;
    private static final int[][] SIDES = {{0,-1},{1,0},{0,1},{-1,0}};
    private FleshColonyEcology() { }
    public static boolean tissueSupport(int type) {
        return type == Blocks.FLESH_BLOCK || type == Blocks.FLESH_HEART || type == Blocks.HEART_CORE
                || type >= Blocks.FLESH_ARTERY && type <= Blocks.FLESH_NECROSIS;
    }
    @lombok.Getter @lombok.experimental.Accessors(fluent = true)
    @lombok.RequiredArgsConstructor
    public static final class Scan {
        private final List<BlockPos> growth, cocoons;
        private final BlockPos newCocoon;
    }
    public static Scan scan(FleshColonyLayout.Layout layout, int round,
            Fluids.BlockLookup lookup, Predicate<BlockPos> edited) {
        List<BlockPos> growth = new ArrayList<>(), cocoons = new ArrayList<>();
        BlockPos newCocoon = null;
        int maxY = Math.max(74, layout.bounds().maxY() + 1);
        for (int i = 0; i < SCAN_COLUMNS; i++) {
            int column = ((round * SCAN_COLUMNS + i) * 73) & 65535;
            int x = layout.cellX() * 256 + (column & 255), z = layout.cellZ() * 256 + (column >>> 8);
            long dx = (long) x - layout.centerX(), dz = (long) z - layout.centerZ();
            if (dx * dx + dz * dz > (long) layout.radius() * layout.radius()
                    || FleshColonyLayout.isPortalSafetyColumn(x, z)) continue;
            if (lookup.get(x, 63, z) != Blocks.ABYSS_STONE) continue;
            int top = 63, topType = Blocks.ABYSS_STONE;
            for (int y = 64; y <= maxY; y++) {
                int type = lookup.get(x, y, z);
                if (type < 0) break;
                if ((type == Blocks.FLESH_COCOON || type == Blocks.FLESH_LARGE_COCOON) && !edited.test(new BlockPos(x, y, z))
                        && tissueSupport(lookup.get(x, y - 1, z))) cocoons.add(new BlockPos(x, y, z));
                if (type != Blocks.AIR) { top = y; topType = type; }
            }
            if (newCocoon == null && tissueSupport(topType)
                    && FleshColonyLayout.hash(layout.seed(), x, z, 0x558e0000) % 64 == 0
                    && lookup.get(x, top + 1, z) == Blocks.AIR && lookup.get(x, top + 2, z) == Blocks.AIR
                    && !edited.test(new BlockPos(x, top, z)) && !edited.test(new BlockPos(x, top + 1, z))) {
                newCocoon = new BlockPos(x, top + 1, z);
            }
            if (!growth.isEmpty()) continue;
            long roll = FleshColonyLayout.hash(layout.seed(), x, z, 0x336c0000) % 100;
            int height = roll < 70 ? 1 : roll < 90 ? 2 : roll < 98 ? 3 : 4;
            boolean clear = true;
            for (int y = 64; y < 64 + height; y++) {
                if (lookup.get(x, y, z) != Blocks.AIR || edited.test(new BlockPos(x, y, z))) { clear = false; break; }
            }
            boolean attached = false;
            for (int[] side : SIDES) {
                attached |= tissueSupport(lookup.get(x + side[0], 64, z + side[1]));
            }
            if (clear && attached) for (int y = 64; y < 64 + height; y++) growth.add(new BlockPos(x, y, z));
        }
        return new Scan(List.copyOf(growth), List.copyOf(cocoons), newCocoon);
    }
}
