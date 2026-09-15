package com.gameexpert.engine.structure.generator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gameexpert.engine.BlockPos;
import com.gameexpert.engine.SurfaceDecorator;
import com.gameexpert.engine.structure.RuinGenerator;
import com.gameexpert.engine.structure.StructureAabb;
import com.gameexpert.engine.structure.StructureHash;
import com.gameexpert.engine.structure.StructureOverlayGenerator;
import com.gameexpert.engine.structure.StructureSiteDescriptor;
import com.gameexpert.engine.structure.StructureTerrainRules;
import com.gameexpert.terrain.Blocks;

/**
 * The large, runtime-only dungeon family.  The natural terrain generator is deliberately not
 * involved: AIR voxels excavate the rooms and the remaining voxels are a deterministic overlay.
 * Every choice is coordinate/site-hash based, so planning a chunk in a different order is safe.
 */
public final class UndergroundDungeonGenerator implements StructureOverlayGenerator {
    private static final int ROLL = 0x44554e47;
    private static final int PART = 0x44554e48;
    private static final int VOXEL = 0x44554e49;
    private static final int[] CAPS = {1800, 6500, 24000, 100000};
    private static final int[] LEVELS = {1, 2, 4, 7};
    private static final int[] ROOMS = {4, 8, 20, 45};
    private static final int[] RADII = {18, 30, 55, 100};

    @Override
    public List<RuinGenerator.Voxel> plan(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world) {
        int scale = scale(site);
        int topFloor = findTopFloor(site, world, LEVELS[scale]);
        if (topFloor < Blocks.MIN_Y + (LEVELS[scale] - 1) * 8 + 4) return List.of();
        Planner planner = new Planner(site, world, scale, topFloor);
        planner.build();
        return planner.voxels.size() < 32 || !planner.complete ? List.of() : List.copyOf(planner.voxels.values());
    }

    /**
     * A dungeon's rectangular maximum reach is intentionally broad, but its rooms and corridors
     * are sparse. Before reading terrain for a distant chunk, reject it when the hash-built
     * horizontal layout contains no possible write there. Terrain admission can only remove
     * writes, never add one outside this footprint.
     */
    @Override
    public boolean possibleHorizontalWriteIntersects(StructureSiteDescriptor site,
            StructureAabb chunk) {
        return new HorizontalFootprint(site, scale(site), chunk).intersects();
    }

    /** 0..3999 small, 4000..7999 medium, 8000..9799 large, 9800..9999 mega. */
    private int scale(StructureSiteDescriptor site) {
        int value = Integer.remainderUnsigned(StructureHash.mix32(site.siteKey() ^ ROLL), 10_000);
        return value < 4_000 ? 0 : value < 8_000 ? 1 : value < 9_800 ? 2 : 3;
    }

    private int findTopFloor(StructureSiteDescriptor site, SurfaceDecorator.BlockView world, int levels) {
        int depth = (levels - 1) * 8;
        for (int y = Math.min(Blocks.SEA_LEVEL - 4, Blocks.MAX_Y - 10); y - depth >= Blocks.MIN_Y + 4; y--) {
            if (solid(world.getBlock(site.anchorX(), y, site.anchorZ()))
                    && solid(world.getBlock(site.anchorX() + 3, y - depth, site.anchorZ() + 3))) return y;
        }
        return Blocks.MIN_Y - 1;
    }

    private boolean solid(int block) {
        return StructureTerrainRules.isStableGround(block);
    }

    /** Terrain-free, chunk-level superset of every local X/Z coordinate Planner may write. */
    private static final class HorizontalFootprint {
        private final StructureSiteDescriptor site;
        private final StructureAabb chunk;
        private final int scale;
        private final List<Room> rooms = new ArrayList<>();

        private HorizontalFootprint(StructureSiteDescriptor site, int scale, StructureAabb chunk) {
            this.site = site;
            this.scale = scale;
            this.chunk = chunk;
        }

        private boolean intersects() {
            makeRooms();
            for (int i = 1; i < rooms.size(); i++) {
                Room a = rooms.get(i - 1), b = rooms.get(i);
                if (corridor(a, b, i)) return true;
                if (i > 2 && (part(i, 31) & 7) == 0 && corridor(a, rooms.get(i - 2), i + 91)) {
                    return true;
                }
            }
            for (int i = 1; i < rooms.size(); i++) {
                Room room = rooms.get(i);
                if (leg(room.x, room.z, 0, 0) || (room.level != 0 && square(room.x, room.z, 1))) {
                    return true;
                }
            }
            for (Room room : rooms) {
                // dress() can place a mossy pillar and then a chest two cells east of a room wall.
                if (rectangle(room.x - room.hx, room.x + room.hx + 2,
                        room.z - room.hz, room.z + room.hz)) return true;
            }
            return false;
        }

        private void makeRooms() {
            int levels = LEVELS[scale], total = ROOMS[scale];
            int perLevel = Math.max(1, total / levels);
            for (int level = 0; level < levels; level++) {
                int count = level == levels - 1 ? total - rooms.size() : perLevel;
                for (int i = 0; i < count; i++) {
                    int index = rooms.size(), lane = part(index, 7);
                    int radius = RADII[scale];
                    int x = signed(lane, radius), z = signed(lane >>> 8, radius);
                    if (index == 0) { x = 0; z = 0; }
                    int hx = 2 + Math.floorMod(lane >>> 16, 2 + scale * 2);
                    int hz = 2 + Math.floorMod(lane >>> 20, 2 + scale * 2);
                    int height = 3 + Math.floorMod(lane >>> 24, scale == 0 ? 2 : 4);
                    rooms.add(new Room(level, x, z, hx, hz, height,
                            index == 0 || (part(index, 13) & 15) < (scale + 2)));
                }
            }
        }

        private boolean corridor(Room a, Room b, int index) {
            if (a.level != b.level) {
                int x = a.x + signed(part(index, 37), Math.min(a.hx, b.hx));
                int z = a.z + signed(part(index, 41), Math.min(a.hz, b.hz));
                return leg(a.x, a.z, x, z) || leg(b.x, b.z, x, z) || square(x, z, 1);
            }
            int bend = (part(index, 23) & 1) == 0 ? b.x : a.x;
            return leg(a.x, a.z, bend, a.z) || leg(bend, a.z, bend, b.z)
                    || leg(bend, b.z, b.x, b.z);
        }

        private boolean leg(int x0, int z0, int x1, int z1) {
            int dx = Integer.compare(x1, x0);
            int dz = Integer.compare(z1, z0);
            int length = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
            // Planner.leg intentionally advances both signed axes for length steps. It can pass
            // beyond the nominal endpoint on the shorter axis, so the rejection geometry mirrors
            // its actual final coordinate rather than the requested x1/z1 pair.
            int endX = x0 + dx * length;
            int endZ = z0 + dz * length;
            if (dx != 0) {
                return rectangle(Math.min(x0, endX), Math.max(x0, endX),
                        Math.min(z0, endZ) - 2, Math.max(z0, endZ) + 2);
            }
            return rectangle(x0 - 2, x0 + 2, Math.min(z0, endZ), Math.max(z0, endZ));
        }

        private boolean square(int x, int z, int radius) {
            return rectangle(x - radius, x + radius, z - radius, z + radius);
        }

        private boolean rectangle(int minLocalX, int maxLocalX, int minLocalZ, int maxLocalZ) {
            int minX;
            int maxX;
            int minZ;
            int maxZ;
            switch (site.direction()) {
                case 1 -> {
                    minX = site.anchorX() - maxLocalZ;
                    maxX = site.anchorX() - minLocalZ;
                    minZ = site.anchorZ() + minLocalX;
                    maxZ = site.anchorZ() + maxLocalX;
                }
                case 2 -> {
                    minX = site.anchorX() - maxLocalX;
                    maxX = site.anchorX() - minLocalX;
                    minZ = site.anchorZ() - maxLocalZ;
                    maxZ = site.anchorZ() - minLocalZ;
                }
                case 3 -> {
                    minX = site.anchorX() + minLocalZ;
                    maxX = site.anchorX() + maxLocalZ;
                    minZ = site.anchorZ() - maxLocalX;
                    maxZ = site.anchorZ() - minLocalX;
                }
                default -> {
                    minX = site.anchorX() + minLocalX;
                    maxX = site.anchorX() + maxLocalX;
                    minZ = site.anchorZ() + minLocalZ;
                    maxZ = site.anchorZ() + maxLocalZ;
                }
            }
            return minX <= chunk.maxX() && maxX >= chunk.minX()
                    && minZ <= chunk.maxZ() && maxZ >= chunk.minZ();
        }

        private int part(int index, int role) { return site.partLane0(index, PART + role); }
        private static int signed(int value, int radius) {
            return radius == 0 ? 0 : Math.floorMod(value, radius * 2 + 1) - radius;
        }
    }

    private static final class Room {
        final int level, x, z, hx, hz, height;
        final boolean spawner;
        Room(int level, int x, int z, int hx, int hz, int height, boolean spawner) {
            this.level = level; this.x = x; this.z = z; this.hx = hx; this.hz = hz;
            this.height = height; this.spawner = spawner;
        }
    }

    private static final class Planner {
        final StructureSiteDescriptor site;
        final SurfaceDecorator.BlockView world;
        final int scale, topFloor, cap;
        final Map<BlockPos, RuinGenerator.Voxel> voxels = new LinkedHashMap<>();
        final Set<BlockPos> corridorAir = new HashSet<>();
        final List<Room> rooms = new ArrayList<>();
        boolean complete = true;

        Planner(StructureSiteDescriptor site, SurfaceDecorator.BlockView world, int scale, int topFloor) {
            this.site = site; this.world = world; this.scale = scale; this.topFloor = topFloor;
            this.cap = CAPS[scale];
        }

        void build() {
            makeRooms();
            for (int i = 1; i < rooms.size(); i++) {
                Room a = rooms.get(i - 1), b = rooms.get(i);
                corridor(a, b, i);
                if (i > 2 && (part(i, 31) & 7) == 0) corridor(a, rooms.get(i - 2), i + 91);
            }
            // The sequential links provide branches; this deterministic trunk is the connectivity
            // invariant, so a room can never become an attractive but unreachable dead box.
            for (int i = 1; i < rooms.size(); i++) rootCorridor(rooms.get(i), i + 173);
            // Corridors are planned before chamber shells so their excavation cannot leave a
            // skeletal room. Rebuild every complete shell, then cut only deliberate entrances.
            for (Room r : rooms) room(r);
            for (int i = 1; i < rooms.size(); i++) {
                Room a = rooms.get(i - 1), b = rooms.get(i);
                doorway(a, b);
                doorway(b, a);
                if (i > 2 && (part(i, 31) & 7) == 0) {
                    Room branch = rooms.get(i - 2);
                    doorway(a, branch);
                    doorway(branch, a);
                }
                doorway(rooms.get(i), new Room(0, 0, 0, 0, 0, 0, false));
            }
            for (int i = 0; i < rooms.size(); i++) dress(rooms.get(i), i);
        }

        void rootCorridor(Room room, int index) {
            int rootX = 0, rootZ = 0;
            leg(room.x, room.z, rootX, rootZ, floor(room.level), index);
            if (room.level != 0) rootShaft(room.x, room.z, room.level, index);
        }

        void rootShaft(int x, int z, int level, int index) {
            int lo = floor(level), hi = floor(0);
            for (int y = lo + 1; y < hi + 4; y++) {
                for (int sx = -1; sx <= 1; sx++) for (int sz = -1; sz <= 1; sz++) carve(x + sx, y, z + sz);
                corridorAir.add(new BlockPos(worldX(x, z), y, worldZ(x, z)));
                carve(x - 1, y, z); finish(x - 1, y, z, Blocks.LADDER);
                build(x + 1, y, z, Blocks.MOSSY_COBBLE);
                build(x, y, z - 1, Blocks.MOSSY_COBBLE);
                build(x, y, z + 1, Blocks.MOSSY_COBBLE);
            }
            build(x - 1, lo + 1, z, Blocks.TORCH);
        }

        void makeRooms() {
            int levels = LEVELS[scale], total = ROOMS[scale];
            int perLevel = Math.max(1, total / levels);
            for (int level = 0; level < levels; level++) {
                int count = level == levels - 1 ? total - rooms.size() : perLevel;
                for (int i = 0; i < count; i++) {
                    int index = rooms.size(), lane = part(index, 7);
                    int radius = RADII[scale];
                    int x = signed(lane, radius), z = signed(lane >>> 8, radius);
                    if (index == 0) { x = 0; z = 0; }
                    int hx = 2 + Math.floorMod(lane >>> 16, 2 + scale * 2);
                    int hz = 2 + Math.floorMod(lane >>> 20, 2 + scale * 2);
                    int h = 3 + Math.floorMod(lane >>> 24, scale == 0 ? 2 : 4);
                    rooms.add(new Room(level, x, z, hx, hz, h,
                            index == 0 || (part(index, 13) & 15) < (scale + 2)));
                }
            }
        }

        void room(Room r) {
            int floor = floor(r.level);
            for (int z = r.z - r.hz; z <= r.z + r.hz; z++) for (int x = r.x - r.hx; x <= r.x + r.hx; x++) {
                boolean edge = x == r.x - r.hx || x == r.x + r.hx || z == r.z - r.hz || z == r.z + r.hz;
                if (!edge) for (int y = 1; y <= r.height; y++) carve(x, floor + y, z);
                else for (int y = 1; y <= r.height; y++) build(x, floor + y, z, masonry(x, floor + y, z));
                carve(x, floor, z); finish(x, floor, z, floorMaterial(x, z));
            }
            for (int x = r.x - r.hx; x <= r.x + r.hx; x++) for (int z = r.z - r.hz; z <= r.z + r.hz; z++)
                { carve(x, floor + r.height + 1, z); finish(x, floor + r.height + 1, z, masonry(x, floor + r.height + 1, z)); }
        }

        void doorway(Room room, Room toward) {
            int dx = Integer.compare(toward.x, room.x), dz = Integer.compare(toward.z, room.z);
            if (dx == 0 && dz == 0) { dx = room.x == 0 ? 1 : -Integer.compare(room.x, 0); dz = room.z == 0 ? 1 : -Integer.compare(room.z, 0); }
            int f = floor(room.level);
            if (dx != 0) openDoor(room.x + dx * room.hx, room.z, f, dx, dz);
            if (dz != 0) openDoor(room.x, room.z + dz * room.hz, f, dx, dz);
        }

        void openDoor(int x, int z, int floor, int dx, int dz) {
            carve(x, floor + 1, z); carve(x, floor + 2, z);
            if (dx != 0) { build(x, floor + 1, z - 1, masonry(x, floor + 1, z - 1)); build(x, floor + 1, z + 1, masonry(x, floor + 1, z + 1)); }
            else { build(x - 1, floor + 1, z, masonry(x - 1, floor + 1, z)); build(x + 1, floor + 1, z, masonry(x + 1, floor + 1, z)); }
        }

        void corridor(Room a, Room b, int index) {
            if (a.level != b.level) {
                int x = a.x + signed(part(index, 37), Math.min(a.hx, b.hx));
                int z = a.z + signed(part(index, 41), Math.min(a.hz, b.hz));
                leg(a.x, a.z, x, z, floor(a.level), index);
                leg(b.x, b.z, x, z, floor(b.level), index + 1);
                vertical(a, b, index, x, z);
                return;
            }
            int floor = floor(a.level), bend = (part(index, 23) & 1) == 0 ? b.x : a.x;
            leg(a.x, a.z, bend, a.z, floor, index); leg(bend, a.z, bend, b.z, floor, index + 1); leg(bend, b.z, b.x, b.z, floor, index + 2);
            if ((part(index, 29) & 3) != 0) gate(bend, a.z, floor, index);
        }

        void leg(int x0, int z0, int x1, int z1, int floor, int index) {
            int dx = Integer.compare(x1, x0), dz = Integer.compare(z1, z0), n = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
            for (int i = 0; i <= n; i++) { int x = x0 + dx * i, z = z0 + dz * i;
                passage(x, z, floor, dx != 0, index, i);
            }
        }

        /** Carves a real 3-wide, two-block-high passage, including its envelope. */
        void passage(int x, int z, int floor, boolean horizontal, int index, int step) {
            int acrossX = horizontal ? 0 : 1, acrossZ = horizontal ? 1 : 0;
            for (int across = -1; across <= 1; across++) {
                int px = x + acrossX * across, pz = z + acrossZ * across;
                carve(px, floor + 1, pz); carve(px, floor + 2, pz);
                corridorAir.add(new BlockPos(worldX(px, pz), floor + 1, worldZ(px, pz)));
                corridorAir.add(new BlockPos(worldX(px, pz), floor + 2, worldZ(px, pz)));
                carve(px, floor, pz); finish(px, floor, pz, floorMaterial(px, pz));
                build(px, floor + 3, pz, masonry(px, floor + 3, pz));
            }
            for (int side = -2; side <= 2; side += 4) {
                int wx = x + acrossX * side, wz = z + acrossZ * side;
                for (int y = floor + 1; y <= floor + 2; y++) build(wx, y, wz, masonry(wx, y, wz));
            }
            // Decorations stay off the central walking lane. They are hash-driven and sparse.
            int decoration = part(index * 37 + step, 59);
            if ((decoration & 15) == 0) build(x, floor + 1, z, Blocks.TORCH);
            if ((decoration & 31) == 1) {
                int side = (decoration & 1) == 0 ? -1 : 1;
                build(x + acrossX * side, floor + 1, z + acrossZ * side, Blocks.COBWEB);
            }
            if ((decoration & 63) == 2) {
                int side = (decoration & 1) == 0 ? -1 : 1;
                build(x + acrossX * side, floor + 1, z + acrossZ * side, Blocks.GRAVEL);
            }
        }

        void vertical(Room a, Room b, int index, int x, int z) {
            int lo = Math.min(floor(a.level), floor(b.level)), hi = Math.max(floor(a.level), floor(b.level));
            for (int y = lo + 1; y < hi + 4; y++) {
                for (int sx = -1; sx <= 1; sx++) for (int sz = -1; sz <= 1; sz++) carve(x + sx, y, z + sz);
                corridorAir.add(new BlockPos(worldX(x, z), y, worldZ(x, z)));
                carve(x - 1, y, z); finish(x - 1, y, z, Blocks.LADDER);
                build(x + 1, y, z, Blocks.MOSSY_COBBLE);
                build(x, y, z - 1, Blocks.MOSSY_COBBLE);
                build(x, y, z + 1, Blocks.MOSSY_COBBLE);
            }
            build(x - 1, lo + 1, z, Blocks.TORCH);
        }

        void gate(int x, int z, int floor, int index) {
            for (int y = floor + 1; y <= floor + 3; y++) { build(x - 1, y, z, Blocks.IRON_BARS); build(x + 1, y, z, Blocks.IRON_BARS); }
            if ((part(index, 43) & 1) == 0) build(x, floor + 3, z, Blocks.IRON_BARS);
        }

        void dress(Room r, int index) {
            int f = floor(r.level), lane = part(index, 47);
            if (r.spawner) { dressBuild(r.x, f + 2, r.z, Blocks.SPAWNER_BASE + Math.floorMod(lane, 3)); dressBuild(r.x - r.hx + 1, f + 1, r.z - r.hz + 1, Blocks.CHEST); }
            if ((lane & 3) == 0) dressBuild(r.x + r.hx - 1, f + 1, r.z + r.hz - 1, Blocks.CHEST);
            int props = 2 + Math.floorMod(lane >>> 8, 5);
            for (int p = 0; p < props; p++) { int q = part(index * 11 + p, 53); int x = r.x + signed(q, Math.max(1, r.hx - 1)), z = r.z + signed(q >>> 8, Math.max(1, r.hz - 1));
                // This block set has no chain ID; paired iron bars are the registered chain analogue.
                int block = switch (q & 7) { case 0 -> Blocks.COBWEB; case 1 -> Blocks.GRAVEL; case 2 -> Blocks.COBBLE_SLAB; case 3, 4 -> Blocks.IRON_BARS; default -> Blocks.TORCH; };
                // Standing torches need the room floor immediately below them.
                int rise = block == Blocks.TORCH ? 1 : 1 + ((q >>> 5) & 1);
                dressBuild(x, f + rise, z, block); }
            if ((lane & 31) == 1) { int x = r.x + (r.hx + 1); for (int y = f + 1; y <= f + 3; y++) dressBuild(x, y, r.z, Blocks.MOSSY_COBBLE); dressBuild(x + 1, f + 1, r.z, Blocks.CHEST); }
        }

        void dressBuild(int x, int y, int z, int block) {
            BlockPos pos = new BlockPos(worldX(x, z), y, worldZ(x, z));
            if (!corridorAir.contains(pos)) build(x, y, z, block);
        }

        int floor(int level) { return topFloor - level * 8; }
        int signed(int value, int radius) { return radius == 0 ? 0 : Math.floorMod(value, radius * 2 + 1) - radius; }
        int part(int index, int role) { return site.partLane0(index, PART + role); }
        int lane(int x, int y, int z, int role) { return site.voxelLane(site.anchorX() + x, y, site.anchorZ() + z, VOXEL + role); }
        int worldX(int x, int z) { return switch (site.direction()) { case 1 -> site.anchorX() - z; case 2 -> site.anchorX() - x; case 3 -> site.anchorX() + z; default -> site.anchorX() + x; }; }
        int worldZ(int x, int z) { return switch (site.direction()) { case 1 -> site.anchorZ() + x; case 2 -> site.anchorZ() - z; case 3 -> site.anchorZ() - x; default -> site.anchorZ() + z; }; }
        void carve(int x, int y, int z) { if (y < Blocks.MIN_Y || y >= Blocks.MAX_Y || voxels.size() >= cap) { complete = false; return; } int wx = worldX(x, z), wz = worldZ(x, z), current = world.getBlock(wx, y, wz); if (!com.gameexpert.engine.Fluids.isFluid(current)) put(wx, y, wz, Blocks.AIR, true); }
        void build(int x, int y, int z, int block) {
            if (y < Blocks.MIN_Y || y >= Blocks.MAX_Y || voxels.size() >= cap) { complete = false; return; }
            int wx = worldX(x, z), wz = worldZ(x, z);
            if (world.getBlock(wx, y, wz) == Blocks.AIR) put(wx, y, wz, block, false);
        }
        void finish(int x, int y, int z, int block) {
            int wx = worldX(x, z), wz = worldZ(x, z);
            BlockPos pos = new BlockPos(wx, y, wz);
            if (voxels.get(pos) != null && voxels.get(pos).blockType() == Blocks.AIR)
                put(wx, y, wz, block, true);
        }
        void put(int x, int y, int z, int block, boolean excavation) { BlockPos pos = new BlockPos(x, y, z); if (excavation) voxels.put(pos, RuinGenerator.Voxel.at(x, y, z, block)); else voxels.putIfAbsent(pos, RuinGenerator.Voxel.at(x, y, z, block)); }
        int floorMaterial(int x, int z) { int v = lane(x, 0, z, 1); return (v & 15) < 3 ? Blocks.MOSSY_COBBLE : (v & 1) == 0 ? Blocks.COBBLE : Blocks.STONE_BRICK; }
        int masonry(int x, int y, int z) { int v = lane(x, y, z, 2), w = lane(x, y, z, 3); if ((w & 31) == 0) return Blocks.MOSSY_STONE_BRICK; return (v & 7) < 3 ? Blocks.MOSSY_COBBLE : (v & 1) == 0 ? Blocks.STONE_BRICK : Blocks.COBBLE; }
    }
}
