package com.gameexpert.engine.mob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** love-mode 개체만 보유하는 공간 버킷. 짝 탐색은 전체 몹 목록을 순회하지 않는다. */
final class LoveModeSpatialBuckets {
    private final double cellSize;
    private final Map<Cell, List<Mob>> buckets = new HashMap<>();
    private final IdentityHashMap<Mob, Cell> cellsByMob = new IdentityHashMap<>();

    LoveModeSpatialBuckets(double cellSize) {
        if (cellSize <= 0.0) throw new IllegalArgumentException("cellSize must be positive");
        this.cellSize = cellSize;
    }

    void refresh(Mob mob) {
        Cell previous = cellsByMob.get(mob);
        if (!mob.isInLoveMode() || mob.isDead() || mob.removed) {
            if (previous != null) remove(mob, previous);
            return;
        }
        Cell current = cell(mob.x, mob.y, mob.z);
        if (current.equals(previous)) return;
        if (previous != null) remove(mob, previous);
        buckets.computeIfAbsent(current, ignored -> new ArrayList<>()).add(mob);
        cellsByMob.put(mob, current);
    }

    Mob nearestPartner(Mob source, double range) {
        if (!source.isInLoveMode() || range < 0.0) return null;
        int minX = cellCoordinate(source.x - range);
        int maxX = cellCoordinate(source.x + range);
        int minY = cellCoordinate(source.y - range);
        int maxY = cellCoordinate(source.y + range);
        int minZ = cellCoordinate(source.z - range);
        int maxZ = cellCoordinate(source.z + range);
        double rangeSquared = range * range;
        double bestDistance = Double.POSITIVE_INFINITY;
        Mob best = null;
        for (int cellX = minX; cellX <= maxX; cellX++) {
            for (int cellZ = minZ; cellZ <= maxZ; cellZ++) {
                for (int cellY = minY; cellY <= maxY; cellY++) {
                    List<Mob> candidates = buckets.get(new Cell(cellX, cellY, cellZ));
                    if (candidates == null) continue;
                    for (Mob candidate : candidates) {
                        if (candidate == source || !candidate.isInLoveMode()
                                || !source.type.canBreedWith(candidate.type)) continue;
                        double dx = candidate.x - source.x;
                        double dy = candidate.y - source.y;
                        double dz = candidate.z - source.z;
                        double distance = dx * dx + dy * dy + dz * dz;
                        if (distance <= rangeSquared && distance < bestDistance) {
                            bestDistance = distance;
                            best = candidate;
                        }
                    }
                }
            }
        }
        return best;
    }

    int indexedCount() { return cellsByMob.size(); }

    private void remove(Mob mob, Cell cell) {
        List<Mob> bucket = buckets.get(cell);
        if (bucket != null) {
            bucket.remove(mob);
            if (bucket.isEmpty()) buckets.remove(cell);
        }
        cellsByMob.remove(mob);
    }

    private Cell cell(double x, double y, double z) {
        return new Cell(cellCoordinate(x), cellCoordinate(y), cellCoordinate(z));
    }

    private int cellCoordinate(double coordinate) {
        return (int) Math.floor(coordinate / cellSize);
    }

    private static final class Cell {
        private final int x;
        private final int y;
        private final int z;

        private Cell(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Cell cell)) return false;
            return x == cell.x && y == cell.y && z == cell.z;
        }

        @Override
        public int hashCode() { return 31 * (31 * x + y) + z; }
    }
}
