package com.gameexpert.engine.structure.generator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gameexpert.engine.BlockPos;
import com.gameexpert.engine.BuildingBlockRules;
import com.gameexpert.engine.SurfaceDecorator;
import com.gameexpert.engine.structure.RuinGenerator;
import com.gameexpert.engine.structure.StructureOverlayGenerator;
import com.gameexpert.engine.structure.StructureSiteDescriptor;
import com.gameexpert.engine.structure.StructureTerrainRules;
import com.gameexpert.terrain.Blocks;

/**
 * Stateless, hash-planned prison excavation.  AIR voxels are deliberate runtime-overlay cuts;
 * WorldRuntime's persistent-diff gate is responsible for letting player blocks win over this plan.
 */
public final class UndergroundPrisonGenerator implements StructureOverlayGenerator {
    private static final int LAYOUT = 0x70726973;
    private static final int WEAR = 0x70726977;
    private static final int LEVEL_GAP = 8;
    private static final int ROOM_HEIGHT = 5;

    @Override
    public List<RuinGenerator.Voxel> plan(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world) {
        Scale scale = Scale.roll(site);
        int topFloor = findStoneFloor(site, world, scale.levels);
        if (topFloor < Blocks.MIN_Y) return List.of();
        Entrance entrance = findEntrance(site, world, topFloor);
        Planner plan = new Planner(site, scale, topFloor, world, entrance);
        plan.build();
        return plan.valid() && plan.size() >= 450 ? plan.voxels() : List.of();
    }

    private static final class Entrance {
        private final int x, z, surface;
        private final boolean surfaceAccess;

        private Entrance(int x, int z, int surface, boolean surfaceAccess) {
            this.x = x;
            this.z = z;
            this.surface = surface;
            this.surfaceAccess = surfaceAccess;
        }
    }

    /**
     * A prison's scope is a site-hash decision, never a biome-derived size.  The literal
     * 40/40/20 split is intentionally kept here (rather than hidden in a bit mask), because
     * it is part of the content contract: a short cell block must be common, while a deep maze
     * remains memorable.  Every detail beneath a class is independently site-hashed.
     */
    private enum Scale {
        // rows x (rows or rows+1) yields 4..6, 18..24, and 48..60 chambers respectively.
        SMALL(2, 1, 11, 26_000),
        MEDIUM(3, 2, 13, 58_000),
        LARGE(4, 3, 15, 98_000);
        private final int rows;
        private final int levels;
        private final int spacing;
        private final int voxelCap;
        Scale(int rows, int levels, int spacing, int voxelCap) {
            this.rows = rows;
            this.levels = levels;
            this.spacing = spacing;
            this.voxelCap = voxelCap;
        }
        private static Scale roll(StructureSiteDescriptor site) {
            int roll = Math.floorMod(site.partLane0(0, LAYOUT), 100);
            return roll < 40 ? SMALL : roll < 80 ? MEDIUM : LARGE;
        }
    }

    private static final class Planner {
        private final StructureSiteDescriptor site;
        private final Scale scale;
        private final int topFloor;
        private final SurfaceDecorator.BlockView world;
        private final Entrance entrance;
        private final Map<BlockPos, RuinGenerator.Voxel> voxels = new LinkedHashMap<>();
        private boolean withinCap = true;

        private Planner(StructureSiteDescriptor site, Scale scale, int topFloor,
                SurfaceDecorator.BlockView world, Entrance entrance) {
            this.site = site;
            this.scale = scale;
            this.topFloor = topFloor;
            this.world = world;
            this.entrance = entrance;
        }

        private void build() {
            for (int level = 0; level < scale.levels; level++) buildLevel(level);
            for (int level = 0; level < scale.levels - 1; level++) verticalLink(level);
            entrance();
            wardenVault();
        }

        private void buildLevel(int level) {
            int floor = topFloor - level * LEVEL_GAP;
            int cols = scale.rows + ((part(level, 1) >>> 3) & 1);
            int rows = scale.rows;
            int startX = -((cols - 1) * scale.spacing) / 2;
            int startZ = -((rows - 1) * scale.spacing) / 2;
            for (int row = 0; row < rows; row++) for (int col = 0; col < cols; col++) {
                int index = level * 64 + row * 8 + col;
                int x = startX + col * scale.spacing;
                int z = startZ + row * scale.spacing;
                int lane = part(index, 2);
                int hx = 4 + ((lane >>> 4) & 1);
                int hz = 4 + ((lane >>> 6) & 1);
                room(floor, x, z, hx, hz, index);
                if (col + 1 < cols) corridor(floor, x, z, x + scale.spacing, z, index + 180);
                if (row + 1 < rows) corridor(floor, x, z, x, z + scale.spacing, index + 240);
                // Hash-selected cross links turn the larger classes into a maze, not a grid tour.
                if (col + 1 < cols && row + 1 < rows && (part(index, 3) & 3) == 0) {
                    corridor(floor, x, z, x + scale.spacing, z + scale.spacing, index + 300);
                }
            }
        }

        private void room(int floor, int cx, int cz, int hx, int hz, int index) {
            excavate(floor, cx - hx, cx + hx, cz - hz, cz + hz, ROOM_HEIGHT);
            for (int x = cx - hx; x <= cx + hx; x++) for (int z = cz - hz; z <= cz + hz; z++) {
                boolean edge = x == cx - hx || x == cx + hx || z == cz - hz || z == cz + hz;
                put(x, floor, z, masonry(x, floor, z));
                put(x, floor + ROOM_HEIGHT + 1, z, masonry(x, floor + ROOM_HEIGHT + 1, z));
                if (edge) for (int y = 1; y <= ROOM_HEIGHT; y++) put(x, floor + y, z,
                        masonry(x, floor + y, z));
            }
            // A prison room is normally cell-lined; only hash-selected guard/torture rooms are
            // left open.  Thus every generated complex includes many separately dressed cells.
            if ((part(index, 4) & 7) != 0) cellRow(floor, cx, cz, hx, hz, index);
            if ((part(index, 5) & 7) < 3) guardPost(floor, cx, cz, index);
            if ((part(index, 6) & 7) == 0) tortureProps(floor, cx, cz, index);
            pillars(floor, cx, cz, hx, hz, index);
        }

        private void corridor(int floor, int x0, int z0, int x1, int z1, int salt) {
            int dx = Integer.compare(x1, x0), dz = Integer.compare(z1, z0);
            int length = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
            for (int step = 0; step <= length; step++) {
                int x = x0 + dx * step, z = z0 + dz * step;
                excavate(floor, x - 1, x + 1, z - 1, z + 1, ROOM_HEIGHT);
                for (int sx = -1; sx <= 1; sx++) for (int sz = -1; sz <= 1; sz++) {
                    put(x + sx, floor, z + sz, masonry(x + sx, floor, z + sz));
                    put(x + sx, floor + ROOM_HEIGHT + 1, z + sz,
                            masonry(x + sx, floor + ROOM_HEIGHT + 1, z + sz));
                }
                if (step % 7 == Math.floorMod(part(salt, 7), 7)) {
                    put(x, floor + 1, z, Blocks.TORCH);
                }
            }
        }

        /** Every selected room has individually carved, barred cells with a door and prisoner kit. */
        private void cellRow(int floor, int cx, int cz, int hx, int hz, int index) {
            boolean northSouth = (part(index, 8) & 1) == 0;
            int cells = 2 + Math.floorMod(part(index, 9), Math.max(1, northSouth ? hx : hz));
            int span = northSouth ? 2 * hx - 1 : 2 * hz - 1;
            int step = Math.max(2, span / cells);
            for (int cell = 0; cell < cells; cell++) {
                int offset = -span / 2 + 1 + cell * step;
                int x0 = northSouth ? cx + offset : cx - hx + 1;
                int x1 = northSouth ? Math.min(cx + offset + step - 1, cx + hx - 1) : cx - hx + 3;
                int z0 = northSouth ? cz - hz + 1 : cz + offset;
                int z1 = northSouth ? cz - hz + 3 : Math.min(cz + offset + step - 1, cz + hz - 1);
                cell(floor, x0, x1, z0, z1, northSouth, index * 5 + cell);
            }
        }

        private void cell(int floor, int x0, int x1, int z0, int z1, boolean northSouth, int salt) {
            excavate(floor, x0, x1, z0, z1, ROOM_HEIGHT);
            int front = northSouth ? z1 : x1;
            int door = northSouth ? x0 + Math.floorMod(part(salt, 10), Math.max(1, x1 - x0 + 1))
                    : z0 + Math.floorMod(part(salt, 10), Math.max(1, z1 - z0 + 1));
            for (int a = northSouth ? x0 : z0; a <= (northSouth ? x1 : z1); a++) {
                for (int y = 1; y <= 4; y++) {
                    int x = northSouth ? a : front, z = northSouth ? front : a;
                    put(x, floor + y, z, a == door && y <= 2 ? Blocks.DOOR_CLOSED : Blocks.IRON_BARS);
                }
            }
            int bedX = x0, bedZ = z0;
            if ((part(salt, 11) & 1) == 0) {
                int facing = northSouth ? 2 : 1;
                put(bedX, floor + 1, bedZ, Blocks.BED, facing);
                put(bedX + (northSouth ? 0 : 1), floor + 1,
                        bedZ + (northSouth ? 1 : 0), Blocks.BED,
                        facing | BuildingBlockRules.BED_HEAD);
            } else {
                put(northSouth ? x0 : x0 + 1, floor + 1,
                        northSouth ? z0 + 1 : z0, Blocks.HAY_BLOCK);
            }
            int chainX = northSouth ? x1 : x0 + 2, chainZ = northSouth ? z0 + 1 : z1;
            for (int y = 2; y <= 4; y++) put(chainX, floor + y, chainZ, Blocks.IRON_BARS);
            if ((part(salt, 12) & 1) == 0) put(chainX, floor + 3, chainZ, Blocks.COBWEB);
        }

        private void pillars(int floor, int cx, int cz, int hx, int hz, int index) {
            if ((part(index, 13) & 3) == 0) return;
            for (int x : new int[] {cx - hx + 1, cx + hx - 1}) for (int z : new int[] {cz - hz + 1, cz + hz - 1}) {
                for (int y = 1; y <= ROOM_HEIGHT; y++) put(x, floor + y, z, masonry(x, floor + y, z));
            }
        }

        private void guardPost(int floor, int x, int z, int salt) {
            put(x, floor + 1, z, Blocks.STONE_BRICK_WALL);
            put(x, floor + 2, z, Blocks.TORCH);
            put(x + ((part(salt, 14) & 1) == 0 ? 1 : -1), floor + 1, z, Blocks.CHEST);
        }

        private void tortureProps(int floor, int x, int z, int salt) {
            int dx = (part(salt, 15) & 1) == 0 ? 2 : -2;
            put(x + dx, floor + 1, z, Blocks.PLANK);
            put(x + dx, floor + 2, z, Blocks.WOOD_FENCE);
            put(x + dx - 1, floor + 2, z, Blocks.IRON_BARS);
            put(x + dx + 1, floor + 2, z, Blocks.IRON_BARS);
            put(x + dx, floor + 3, z, Blocks.COBWEB);
        }

        private void verticalLink(int level) {
            int floor = topFloor - level * LEVEL_GAP;
            int x = -scale.spacing / 2 + Math.floorMod(part(level, 16), scale.spacing);
            int z = -scale.spacing / 2 + Math.floorMod(part(level, 17), scale.spacing);
            for (int y = floor - LEVEL_GAP + 1; y <= floor + ROOM_HEIGHT; y++) {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    put(x + dx, y, z + dz, Blocks.AIR);
                }
                put(x - 1, y, z, Blocks.LADDER);
            }
        }

        private void entrance() {
            excavate(topFloor, -3, 3, -6, 2, ROOM_HEIGHT);
            put(0, topFloor + 1, -6, Blocks.DOOR_CLOSED);
            put(0, topFloor + 2, -6, Blocks.DOOR_CLOSED);
            corridor(topFloor, 0, -7, entrance.x, -7, 0x51);
            corridor(topFloor, entrance.x, -7, entrance.x, entrance.z, 0x52);
            if (entrance.surfaceAccess) {
                accessShaft();
                surfaceEntry();
            } else {
                entryGrotto();
            }
        }

        /** A prison gets a deliberate ladder shaft and a small grounded surface ruin. */
        private void accessShaft() {
            for (int y = topFloor + 1; y <= entrance.surface + 2; y++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        put(entrance.x + dx, y, entrance.z + dz, Blocks.AIR);
                    }
                }
                put(entrance.x - 1, y, entrance.z, Blocks.LADDER);
                put(entrance.x + 1, y, entrance.z,
                        masonry(entrance.x + 1, y, entrance.z));
                put(entrance.x, y, entrance.z - 1,
                        masonry(entrance.x, y, entrance.z - 1));
                put(entrance.x, y, entrance.z + 1,
                        masonry(entrance.x, y, entrance.z + 1));
            }
        }

        private void surfaceEntry() {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    if (Math.abs(dx) != 2 && Math.abs(dz) != 2) continue;
                    if (dz == -2 && Math.abs(dx) <= 1) continue;
                    int localX = entrance.x + dx;
                    int localZ = entrance.z + dz;
                    int ground = surfaceY(site.anchorX() + localX,
                            site.anchorZ() + localZ, world);
                    for (int y = ground + 1; y <= entrance.surface + 1; y++) {
                        put(localX, y, localZ, masonry(localX, y, localZ));
                    }
                    int lane = part((dx + 2) * 7 + dz + 2, 0x53);
                    // Keep both entrance torch pedestals intact after the wall's wear pass.
                    if ((lane & 3) != 0 || Math.abs(dx) == 2 && dz == 0) {
                        put(localX, entrance.surface + 2, localZ,
                                masonry(localX, entrance.surface + 2, localZ));
                    }
                }
            }
            put(entrance.x - 2, entrance.surface + 3, entrance.z, Blocks.TORCH);
            put(entrance.x + 2, entrance.surface + 3, entrance.z, Blocks.TORCH);
        }

        /** Wet or obstructed terrain keeps its roof; the prison opens into a dry cave hall. */
        private void entryGrotto() {
            int radiusX = 6;
            int radiusZ = 7;
            int radiusY = 7;
            int centerY = topFloor + 4;
            for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                for (int dx = -radiusX; dx <= radiusX; dx++) {
                    for (int y = topFloor + 1; y <= topFloor + radiusY; y++) {
                        int dy = y - centerY;
                        int distance = dx * dx * 1024 / (radiusX * radiusX)
                                + dz * dz * 1024 / (radiusZ * radiusZ)
                                + dy * dy * 1024 / 16;
                        int noise = site.voxelLane(site.anchorX() + entrance.x + dx, y,
                                site.anchorZ() + entrance.z + dz, LAYOUT + 0x54) & 255;
                        if (distance <= 900 + noise) {
                            put(entrance.x + dx, y, entrance.z + dz, Blocks.AIR);
                        }
                    }
                }
            }
            for (int dx = -2; dx <= 2; dx++) {
                put(entrance.x + dx, topFloor + 1, entrance.z - 2,
                        masonry(entrance.x + dx, topFloor + 1, entrance.z - 2));
            }
            put(entrance.x - 2, topFloor + 2, entrance.z - 2, Blocks.TORCH);
            put(entrance.x + 2, topFloor + 2, entrance.z - 2, Blocks.TORCH);
        }

        private void wardenVault() {
            int floor = topFloor - (scale.levels - 1) * LEVEL_GAP;
            int x = scale.rows * scale.spacing / 2 - 2;
            excavate(floor, x - 4, x + 4, -4, 4, ROOM_HEIGHT);
            for (int z = -4; z <= 4; z++) for (int y = 1; y <= ROOM_HEIGHT; y++) {
                put(x - 4, floor + y, z, Blocks.IRON_BARS);
                put(x + 4, floor + y, z, Blocks.IRON_BARS);
            }
            put(x, floor + 1, 0, Blocks.CHEST);
            put(x + 2, floor + 1, 0, Blocks.CHEST);
            put(x + 1, floor + 1, -2, Blocks.IRON_BLOCK);
            put(x + 1, floor + 2, -2, Blocks.TORCH);
        }

        private void excavate(int floor, int x0, int x1, int z0, int z1, int height) {
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
                for (int y = 1; y <= height; y++) put(x, floor + y, z, Blocks.AIR);
            }
        }

        private int masonry(int x, int y, int z) {
            int wear = site.voxelLane(site.anchorX() + x, y, site.anchorZ() + z, WEAR);
            if ((wear & 31) == 0) return Blocks.MOSSY_STONE_BRICK;
            if ((wear & 63) == 1) return Blocks.MOSSY_COBBLE;
            if ((wear & 127) == 2) return Blocks.COBBLE;
            return Blocks.STONE_BRICK;
        }

        private int part(int index, int lane) { return site.partLane0(index, LAYOUT + lane); }
        private void put(int x, int y, int z, int block) {
            put(x, y, z, block, 0);
        }
        private void put(int x, int y, int z, int block, int state) {
            if (y < Blocks.MIN_Y || y > Blocks.MAX_Y) {
                withinCap = false;
                return;
            }
            int worldX = site.anchorX() + x;
            int worldZ = site.anchorZ() + z;
            BlockPos pos = new BlockPos(worldX, y, worldZ);
            // Repeated passes deliberately overwrite AIR with masonry/detail.  They do not
            // consume the cap, otherwise a dense maze could fail simply because it was dressed.
            if (!voxels.containsKey(pos) && voxels.size() >= scale.voxelCap) {
                withinCap = false;
                return;
            }
            voxels.put(pos, RuinGenerator.Voxel.at(worldX, y, worldZ, block, state));
        }
        private boolean valid() { return withinCap; }
        private int size() { return voxels.size(); }
        private List<RuinGenerator.Voxel> voxels() { return List.copyOf(voxels.values()); }
    }

    private int findStoneFloor(StructureSiteDescriptor site, SurfaceDecorator.BlockView world, int levels) {
        int depth = (levels - 1) * LEVEL_GAP + ROOM_HEIGHT + 2;
        for (int y = Math.min(Blocks.SEA_LEVEL - 25, Blocks.MAX_Y - ROOM_HEIGHT - 2);
                y - depth >= Blocks.MIN_Y + 2; y--) {
            if (stone(world.getBlock(site.anchorX(), y, site.anchorZ()))
                    && stone(world.getBlock(site.anchorX() + 2, y - depth, site.anchorZ() + 2))
                    && stone(world.getBlock(site.anchorX() - 2, y - depth, site.anchorZ() - 2))) return y;
        }
        return Blocks.MIN_Y - 1;
    }

    private Entrance findEntrance(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world, int topFloor) {
        for (int attempt = 0; attempt < 64; attempt++) {
            int lane = site.partLane1(attempt, LAYOUT + 0x50);
            int localX = Math.floorMod(lane, 17) - 8;
            int localZ = -8 - Math.floorMod(lane >>> 9, 7);
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            boolean valid = true;
            for (int dz = -2; dz <= 2 && valid; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    int worldX = site.anchorX() + localX + dx;
                    int worldZ = site.anchorZ() + localZ + dz;
                    int surface = surfaceY(worldX, worldZ, world);
                    if (surface <= topFloor + ROOM_HEIGHT + 2
                            || !surfaceSupport(world.getBlock(worldX, surface, worldZ))) {
                        valid = false;
                        break;
                    }
                    min = Math.min(min, surface);
                    max = Math.max(max, surface);
                }
            }
            if (!valid || max - min > 2 || max + 3 > Blocks.MAX_Y) continue;
            for (int dz = -2; dz <= 2 && valid; dz++) {
                for (int dx = -2; dx <= 2 && valid; dx++) {
                    int worldX = site.anchorX() + localX + dx;
                    int worldZ = site.anchorZ() + localZ + dz;
                    int surface = surfaceY(worldX, worldZ, world);
                    for (int y = surface + 1; y <= max + 3; y++) {
                        if (world.getBlock(worldX, y, worldZ) != Blocks.AIR) {
                            valid = false;
                            break;
                        }
                    }
                }
            }
            if (valid) return new Entrance(localX, localZ, max, true);
        }
        return new Entrance(0, -12, topFloor + ROOM_HEIGHT + 4, false);
    }

    private static int surfaceY(int x, int z, SurfaceDecorator.BlockView world) {
        for (int y = Blocks.MAX_Y; y >= Blocks.MIN_Y; y--) {
            if (world.getBlock(x, y, z) != Blocks.AIR) return y;
        }
        return Blocks.MIN_Y - 1;
    }

    private boolean surfaceSupport(int block) {
        return StructureTerrainRules.isStableGround(block);
    }

    private boolean stone(int block) {
        return StructureTerrainRules.isStableGround(block);
    }
}
