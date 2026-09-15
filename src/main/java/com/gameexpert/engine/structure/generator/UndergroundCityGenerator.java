package com.gameexpert.engine.structure.generator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gameexpert.engine.BlockPos;
import com.gameexpert.engine.Fluids;
import com.gameexpert.engine.SurfaceDecorator;
import com.gameexpert.engine.structure.RuinGenerator;
import com.gameexpert.engine.structure.StructureOverlayGenerator;
import com.gameexpert.engine.structure.StructureSiteDescriptor;
import com.gameexpert.engine.structure.StructureTerrainRules;
import com.gameexpert.terrain.Blocks;

/**
 * A stateless, runtime-only ancient underground city plan. AIR is deliberately emitted before
 * the city fabric: the overlay application gate is responsible for preserving player diffs.
 * This generator never participates in frozen terrain generation.
 */
public final class UndergroundCityGenerator implements StructureOverlayGenerator {
    private static final int SCALE_SALT = 0x756351;
    private static final int LAYOUT_SALT = 0x756352;
    private static final int MATERIAL_SALT = 0x756353;
    private static final int WEAR_SALT = 0x756354;

    @Override
    public List<RuinGenerator.Voxel> plan(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world) {
        Scale scale = Scale.roll(site);
        int floor = -54 + Math.floorMod(site.adaptationLane(), 8);
        if (floor < Blocks.MIN_Y || floor + (scale.levels - 1) * scale.levelGap + 10 > Blocks.MAX_Y) {
            return List.of();
        }
        Planner planner = new Planner(site, scale, floor);
        planner.build();
        return planner.voxels.size() >= scale.minimumVoxels && planner.hasTerrainCover(world)
                ? List.copyOf(planner.voxels.values()) : List.of();
    }

    /** The unsigned percentile is intentionally exact: 40 / 40 / 18 / 2. */
    private enum Scale {
        SMALL(1, 3, 4, 8, 16, 42_000, 4_000),
        MEDIUM(2, 4, 6, 17, 16, 118_000, 18_000),
        LARGE(4, 5, 8, 29, 15, 300_000, 70_000),
        MEGA(8, 8, 11, 57, 14, 850_000, 260_000);

        final int levels, rows, columns, roomsPerLevel, levelGap, cap, minimumVoxels;
        Scale(int levels, int rows, int columns, int roomsPerLevel, int levelGap, int cap,
                int minimumVoxels) {
            this.levels = levels; this.rows = rows; this.columns = columns;
            this.roomsPerLevel = roomsPerLevel; this.levelGap = levelGap;
            this.cap = cap; this.minimumVoxels = minimumVoxels;
        }
        static Scale roll(StructureSiteDescriptor site) {
            int percentile = Integer.remainderUnsigned(site.partLane0(0, SCALE_SALT), 100);
            return percentile < 40 ? SMALL : percentile < 80 ? MEDIUM
                    : percentile < 98 ? LARGE : MEGA;
        }
    }

    private static final class Planner {
        private final StructureSiteDescriptor site;
        private final Scale scale;
        private final int floor;
        private final int spacing;
        /* District rasterization cannot consume the ladder spine's bounded reservation. */
        private final int spineReserve;
        private final Map<BlockPos, RuinGenerator.Voxel> voxels = new LinkedHashMap<>();
        private boolean districtPhase;

        Planner(StructureSiteDescriptor site, Scale scale, int floor) {
            this.site = site; this.scale = scale; this.floor = floor;
            this.spacing = 15 + Math.floorMod(part(1, 1), 5);
            this.spineReserve = (scale.levels - 1) * (scale.levelGap + 1) * 3;
        }

        void build() {
            districtPhase = true;
            for (int level = 0; level < scale.levels; level++) district(level);
            districtPhase = false;
            for (int level = 0; level + 1 < scale.levels; level++) stairwell(level);
        }

        private boolean hasTerrainCover(SurfaceDecorator.BlockView world) {
            Map<BlockPos, Integer> tops = new LinkedHashMap<>();
            for (RuinGenerator.Voxel voxel : voxels.values()) {
                BlockPos column = new BlockPos(voxel.x(), 0, voxel.z());
                tops.merge(column, voxel.y(), Math::max);
            }
            // Include excavation: a high cavern can break through the seabed even when its
            // buildings fit below it. Reject the whole site rather than leave partial districts.
            for (Map.Entry<BlockPos, Integer> column : tops.entrySet()) {
                int top = column.getValue();
                int current = world.getBlock(column.getKey().x(), top, column.getKey().z());
                if (Fluids.isFluid(current) || Fluids.isSubmergedDecoration(current)) return false;
                boolean covered = false;
                for (int y = top + 1; y <= Blocks.MAX_Y; y++) {
                    int block = world.getBlock(column.getKey().x(), y, column.getKey().z());
                    // An ice cap above open water is not underground cover for the city.
                    if (Fluids.isFluid(block) || Fluids.isSubmergedDecoration(block)) return false;
                    if (StructureTerrainRules.isStableGround(block)) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) return false;
            }
            return true;
        }

        private void district(int level) {
            boolean[][] city = connectedDistrict(level);
            int base = floor + level * scale.levelGap;
            int middleX = center(scale.columns / 2, scale.columns);
            int middleZ = center(scale.rows / 2, scale.rows);
            carveCentralCavern(base, middleX, middleZ, level);
            for (int row = 0; row < scale.rows; row++) for (int col = 0; col < scale.columns; col++) {
                if (!city[row][col]) continue;
                int index = level * 257 + row * scale.columns + col;
                int lane = part(index, 3);
                int x = center(col, scale.columns), z = center(row, scale.rows);
                boolean plaza = row == scale.rows / 2 && col == scale.columns / 2;
                boolean vault = !plaza && (lane & 31) == 0;
                int hx = plaza ? 8 : 4 + ((lane >>> 4) & 1);
                int hz = plaza ? 8 : 4 + ((lane >>> 6) & 1);
                int height = plaza ? 8 : 6 + ((lane >>> 8) & 1);
                chamber(base, x, z, hx, hz, height, index, plaza, vault);
                if (col + 1 < scale.columns && city[row][col + 1])
                    street(base, x, z, center(col + 1, scale.columns), z, height, index);
                if (row + 1 < scale.rows && city[row + 1][col])
                    street(base, x, z, x, center(row + 1, scale.rows), height, index + 89);
            }
        }

        /**
         * Exposes the central district inside an irregular cavern.  Buildings and streets
         * overwrite this AIR afterward, leaving visible space around their exterior walls
         * instead of pressing every facade directly against host stone.
         */
        private void carveCentralCavern(int base, int cx, int cz, int level) {
            int radiusX = 13 + Math.floorMod(part(level, 67), 4);
            int radiusZ = 13 + Math.floorMod(part(level, 71), 4);
            int radiusY = 11 + Math.floorMod(part(level, 73), 3);
            int centerY = base + radiusY / 2;
            for (int z = cz - radiusZ; z <= cz + radiusZ; z++) {
                for (int x = cx - radiusX; x <= cx + radiusX; x++) {
                    for (int y = base + 1; y <= base + radiusY; y++) {
                        int dx = x - cx;
                        int dz = z - cz;
                        int dy = y - centerY;
                        int distance = dx * dx * 1024 / (radiusX * radiusX)
                                + dz * dz * 1024 / (radiusZ * radiusZ)
                                + dy * dy * 1024 / Math.max(1, radiusY * radiusY / 4);
                        int noise = site.voxelLane(site.anchorX() + x, y,
                                site.anchorZ() + z, LAYOUT_SALT + 79) & 255;
                        if (distance <= 900 + noise) put(x, y, z, Blocks.AIR);
                    }
                }
            }
        }

        /* A permanent cross gives every hash-random annex an attached ancient street network. */
        private boolean[][] connectedDistrict(int level) {
            boolean[][] city = new boolean[scale.rows][scale.columns];
            int middleRow = scale.rows / 2, middleColumn = scale.columns / 2;
            for (int col = 0; col < scale.columns; col++) city[middleRow][col] = true;
            for (int row = 0; row < scale.rows; row++) city[row][middleColumn] = true;
            int count = scale.rows + scale.columns - 1;
            int target = Math.min(scale.rows * scale.columns,
                    scale.roomsPerLevel + Math.floorMod(part(level, 7), 5));
            for (int attempt = 0; count < target && attempt < scale.rows * scale.columns * 12; attempt++) {
                int lane = part(level * 131 + attempt, 9);
                int row = Math.floorMod(lane, scale.rows);
                int col = Math.floorMod(lane >>> 9, scale.columns);
                if (!city[row][col] && adjacent(city, row, col)) { city[row][col] = true; count++; }
            }
            return city;
        }

        private boolean adjacent(boolean[][] rooms, int row, int col) {
            return row > 0 && rooms[row - 1][col] || row + 1 < scale.rows && rooms[row + 1][col]
                    || col > 0 && rooms[row][col - 1] || col + 1 < scale.columns && rooms[row][col + 1];
        }

        private void chamber(int base, int cx, int cz, int hx, int hz, int height, int index,
                boolean plaza, boolean vault) {
            clear(base, cx - hx - 1, cx + hx + 1, cz - hz - 1, cz + hz + 1, 0, height + 2);
            for (int z = cz - hz; z <= cz + hz; z++) for (int x = cx - hx; x <= cx + hx; x++) {
                boolean edge = x == cx - hx || x == cx + hx || z == cz - hz || z == cz + hz;
                put(x, base, z, paving(x, base, z));
                if (edge) for (int y = 1; y <= height; y++) put(x, base + y, z, masonry(x, base + y, z));
                put(x, base + height + 1, z, masonry(x, base + height + 1, z));
            }
            if (plaza) plaza(base, cx, cz, hx, hz, height, index);
            else building(base, cx, cz, hx, hz, height, index, vault);
        }

        private void street(int base, int x0, int z0, int x1, int z1, int roomHeight, int salt) {
            int dx = Integer.compare(x1, x0), dz = Integer.compare(z1, z0);
            int length = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
            int height = Math.max(6, roomHeight);
            for (int i = 0; i <= length; i++) {
                int x = x0 + dx * i, z = z0 + dz * i;
                int sx = dz == 0 ? 0 : 1, sz = dx == 0 ? 0 : 1;
                clear(base, x - sx * 2, x + sx * 2, z - sz * 2, z + sz * 2, 0, height + 1);
                for (int side = -2; side <= 2; side++) {
                    put(x + sx * side, base, z + sz * side, paving(x + sx * side, base, z + sz * side));
                    put(x + sx * side, base + height + 1, z + sz * side, masonry(x, base + height + 1, z));
                }
                for (int side : new int[] {-3, 3}) for (int y = 1; y <= height; y++)
                    put(x + sx * side, base + y, z + sz * side, masonry(x, base + y, z));
                if (i % 9 == Math.floorMod(part(salt, 11), 9)) lamp(base, x + sx * 2, z + sz * 2, height);
            }
        }

        private void plaza(int base, int cx, int cz, int hx, int hz, int height, int index) {
            // Chasm void plus a deliberately retained bridge turn a central plaza into a landmark.
            for (int x = cx - 3; x <= cx + 3; x++) for (int z = cz - 5; z <= cz + 5; z++)
                if (Math.abs(z - cz) > 1) put(x, base, z, Blocks.AIR);
            for (int x = cx - 3; x <= cx + 3; x++) for (int z = cz - 1; z <= cz + 1; z++)
                put(x, base, z, Blocks.STONE_BRICK_SLAB);
            for (int x : new int[] {cx - hx + 2, cx + hx - 2}) for (int z : new int[] {cz - hz + 2, cz + hz - 2})
                pillar(base, x, z, height);
            int statueX = cx + ((part(index, 19) & 1) == 0 ? -5 : 5);
            statue(base, statueX, cz + ((part(index, 19) & 2) == 0 ? -4 : 4), height);
        }

        private void building(int base, int cx, int cz, int hx, int hz, int height, int index, boolean vault) {
            // Inner façades make the excavated room read as a multi-floor district building.
            int inset = 2;
            for (int x : new int[] {cx - hx + inset, cx + hx - inset})
                for (int z : new int[] {cz - hz + inset, cz + hz - inset}) pillar(base, x, z, height);
            int upper = 3 + Math.floorMod(part(index, 23), 2);
            for (int y = upper; y < height; y += 3) for (int x = cx - hx + 2; x <= cx + hx - 2; x++)
                if ((x + index) % 3 != 0) put(x, base + y, cz, Blocks.COBBLE_SLAB);
            int props = vault ? 5 : 1 + Math.floorMod(part(index, 29), 4);
            for (int prop = 0; prop < props; prop++) {
                int lane = part(index * 19 + prop, 31);
                int x = cx - hx + 2 + Math.floorMod(lane, Math.max(1, hx * 2 - 2));
                int z = cz - hz + 2 + Math.floorMod(lane >>> 8, Math.max(1, hz * 2 - 2));
                int kind = (lane >>> 17) & 7;
                if (vault && prop < 3) put(x, base + 1, z, Blocks.CHEST);
                else if (kind == 0) put(x, base + 1, z, Blocks.IRON_BARS);
                else if (kind == 1) put(x, base + 1, z, Blocks.BONE_BLOCK);
                else if (kind == 2) put(x, base + 1, z, Blocks.COBWEB);
                else lamp(base, x, z, height);
            }
        }

        private void stairwell(int level) {
            int row = scale.rows / 2, col = scale.columns / 2;
            int x = center(col, scale.columns) + ((part(level, 43) & 1) == 0 ? -5 : 5);
            int z = center(row, scale.rows) + ((part(level, 43) & 2) == 0 ? -5 : 5);
            int base = floor + level * scale.levelGap;
            clear(base, x - 1, x + 1, z - 1, z + 1, 1, scale.levelGap + 1);
            for (int y = 1; y <= scale.levelGap + 1; y++) {
                put(x - 1, base + y, z - 1, darkMasonry(x - 1, base + y, z - 1));
                put(x + 1, base + y, z + 1, darkMasonry(x + 1, base + y, z + 1));
                put(x, base + y, z - 1, Blocks.LADDER);
            }
        }

        private void pillar(int base, int x, int z, int height) {
            for (int y = 1; y <= height; y++) put(x, base + y, z,
                    y == height ? Blocks.STONE_BRICK_SLAB : darkMasonry(x, base + y, z));
        }
        private void statue(int base, int x, int z, int height) {
            for (int y = 1; y <= Math.min(4, height - 1); y++) put(x, base + y, z, darkMasonry(x, base + y, z));
            put(x, base + Math.min(5, height), z, Blocks.CARVED_PUMPKIN);
            lamp(base, x + 1, z, height);
        }
        private void lamp(int base, int x, int z, int height) {
            put(x, base + 1, z, Blocks.STONE_BRICK_WALL);
            put(x, base + 2, z, Blocks.JACK_O_LANTERN);
            if (height > 6) put(x, base + 3, z, Blocks.IRON_BARS);
        }
        private void clear(int base, int x0, int x1, int z0, int z1, int dy0, int dy1) {
            for (int y = base + dy0; y <= base + dy1; y++) for (int z = z0; z <= z1; z++)
                for (int x = x0; x <= x1; x++) put(x, y, z, Blocks.AIR);
        }
        private void put(int localX, int y, int localZ, int block) {
            if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) return;
            int[] point = rotate(localX, localZ);
            int x = site.anchorX() + point[0], z = site.anchorZ() + point[1];
            BlockPos pos = new BlockPos(x, y, z);
            int limit = districtPhase ? scale.cap - spineReserve : scale.cap;
            if (voxels.size() >= limit && !voxels.containsKey(pos)) return;
            voxels.put(pos, RuinGenerator.Voxel.at(x, y, z, block));
        }
        private int[] rotate(int x, int z) {
            return switch (site.direction()) {
                case 1 -> new int[] {-z, x}; case 2 -> new int[] {-x, -z};
                case 3 -> new int[] {z, -x}; default -> new int[] {x, z};
            };
        }
        private int center(int value, int count) { return (value * 2 - count + 1) * spacing / 2; }
        private int part(int index, int role) { return site.partLane0(index, LAYOUT_SALT + role); }
        private int paving(int x, int y, int z) {
            int lane = site.voxelLane(site.anchorX() + x, y, site.anchorZ() + z, MATERIAL_SALT);
            return (lane & 31) == 0 ? Blocks.GRAVEL : (lane & 7) < 3 ? Blocks.MOSSY_COBBLE : Blocks.COBBLE;
        }
        private int masonry(int x, int y, int z) {
            int lane = site.voxelLane(site.anchorX() + x, y, site.anchorZ() + z, WEAR_SALT);
            return (lane & 63) == 0 ? Blocks.MOSS_BLOCK : (lane & 15) < 4 ? Blocks.MOSSY_STONE_BRICK
                    : (lane & 3) == 0 ? Blocks.COBBLE : Blocks.STONE_BRICK;
        }
        private int darkMasonry(int x, int y, int z) {
            return (site.voxelLane(site.anchorX() + x, y, site.anchorZ() + z, WEAR_SALT + 9) & 3) == 0
                    ? Blocks.OBSIDIAN : masonry(x, y, z);
        }
    }
}
