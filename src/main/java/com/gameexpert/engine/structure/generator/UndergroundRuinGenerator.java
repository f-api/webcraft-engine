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
import com.gameexpert.engine.structure.StructureOverlayGenerator;
import com.gameexpert.engine.structure.StructureSiteDescriptor;
import com.gameexpert.terrain.Blocks;

/** A stateless, hash-shaped underground ruin.  AIR is the only excavation operation. */
public final class UndergroundRuinGenerator implements StructureOverlayGenerator {
    private static final int LAYOUT = 0x7a31;
    private static final int MATERIAL = 0x7a32;
    private static final int WEAR = 0x7a33;

    private enum Scale {
        SMALL(3, 5, 1, 4, 3, 1, 2_000),
        MEDIUM(6, 10, 1, 7, 5, 2, 8_000),
        LARGE(10, 16, 2, 11, 8, 3, 20_000);

        final int minRooms, maxRooms, minLevels, maxRadius, maxHalfRoom, levels, cap;

        Scale(int minRooms, int maxRooms, int minLevels, int maxRadius, int maxHalfRoom,
                int levels, int cap) {
            this.minRooms = minRooms;
            this.maxRooms = maxRooms;
            this.minLevels = minLevels;
            this.maxRadius = maxRadius;
            this.maxHalfRoom = maxHalfRoom;
            this.levels = levels;
            this.cap = cap;
        }
    }

    @Override
    public List<RuinGenerator.Voxel> plan(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world) {
        Scale scale = scale(site);
        int floor = findFloor(site, world, scale);
        if (floor < Blocks.MIN_Y || floor + scale.levels * 8 + 4 >= Blocks.MAX_Y) return List.of();
        Planner planner = new Planner(site, world, floor, scale);
        planner.build();
        return !planner.complete || planner.voxels.size() < 24
                ? List.of() : List.copyOf(planner.voxels.values());
    }

    private Scale scale(StructureSiteDescriptor site) {
        int roll = Math.floorMod(site.siteKey(), 100);
        return roll < 40 ? Scale.SMALL : roll < 80 ? Scale.MEDIUM : Scale.LARGE;
    }

    private int findFloor(StructureSiteDescriptor site, SurfaceDecorator.BlockView world,
            Scale scale) {
        int top = Math.min(Blocks.SEA_LEVEL - 6, Blocks.MAX_Y - scale.levels * 8 - 5);
        for (int y = top; y >= Blocks.MIN_Y + 3; y--) {
            if (world.getBlock(site.anchorX(), y, site.anchorZ()) != Blocks.STONE) continue;
            boolean roof = true;
            for (int dy = 1; dy <= scale.levels * 7 + 3 && roof; dy++) {
                roof = world.getBlock(site.anchorX(), y + dy, site.anchorZ()) == Blocks.STONE;
            }
            if (roof) return y;
        }
        return Blocks.MIN_Y - 1;
    }

    private static final class Room {
        final int x, z, level, hx, hz, height;

        Room(int x, int z, int level, int hx, int hz, int height) {
            this.x = x; this.z = z; this.level = level; this.hx = hx; this.hz = hz;
            this.height = height;
        }
    }

    private static final class Planner {
        final StructureSiteDescriptor site;
        final SurfaceDecorator.BlockView world;
        final int floor;
        final Scale scale;
        final Map<BlockPos, RuinGenerator.Voxel> voxels = new LinkedHashMap<>();
        final Set<BlockPos> carved = new HashSet<>();
        final List<Room> rooms = new ArrayList<>();
        boolean complete = true;

        Planner(StructureSiteDescriptor site, SurfaceDecorator.BlockView world, int floor,
                Scale scale) {
            this.site = site; this.world = world; this.floor = floor; this.scale = scale;
        }

        void build() {
            makeRooms();
            if (!rooms.isEmpty()) carveNaturalGrotto(rooms.get(0));
            for (Room room : rooms) carveRoom(room);
            for (int i = 1; i < rooms.size(); i++) corridor(rooms.get(i - 1), rooms.get(i), i);
            for (Room room : rooms) masonry(room);
            for (int i = 0; i < rooms.size(); i++) dress(rooms.get(i), i);
            if (scale.levels > 1) verticalLink();
        }

        /**
         * The primary ruin must read as masonry exposed inside a cave, not as a wall
         * raster pasted directly into stone.  This irregular ellipsoid leaves several
         * blocks of navigable air around the first room before its surviving walls are built.
         */
        void carveNaturalGrotto(Room room) {
            int base = room.level * 7;
            int radiusX = room.hx + 4;
            int radiusZ = room.hz + 4;
            int radiusY = Math.max(4, room.height + 2);
            int centerY = base + 1 + radiusY / 2;
            for (int z = room.z - radiusZ; z <= room.z + radiusZ; z++) {
                for (int x = room.x - radiusX; x <= room.x + radiusX; x++) {
                    for (int y = base + 1; y <= base + radiusY; y++) {
                        int dx = x - room.x;
                        int dz = z - room.z;
                        int dy = y - centerY;
                        int distance = dx * dx * 1024 / (radiusX * radiusX)
                                + dz * dz * 1024 / (radiusZ * radiusZ)
                                + dy * dy * 1024 / Math.max(1, radiusY * radiusY / 4);
                        int noise = site.voxelLane(site.anchorX() + x, floor + y,
                                site.anchorZ() + z, LAYOUT + 71) & 255;
                        if (distance <= 900 + noise) carve(x, y, z);
                    }
                }
            }
        }

        void makeRooms() {
            int count = scale.minRooms + Math.floorMod(part(0, 1), scale.maxRooms - scale.minRooms + 1);
            for (int i = 0; i < count; i++) {
                int level = scale.levels == 1 ? 0 : Math.floorMod(part(i, 2), scale.levels);
                int hx = 2 + Math.floorMod(part(i, 3), scale.maxHalfRoom - 1);
                int hz = 2 + Math.floorMod(part(i, 4), scale.maxHalfRoom - 1);
                int x = i == 0 ? 0 : signed(part(i, 5), scale.maxRadius - 2);
                int z = i == 0 ? 0 : signed(part(i, 6), scale.maxRadius - 2);
                int attempts = 0;
                while (overlaps(x, z, level, hx, hz) && attempts++ < 8) {
                    x = signed(part(i + attempts, 7), scale.maxRadius - 2);
                    z = signed(part(i + attempts, 8), scale.maxRadius - 2);
                }
                rooms.add(new Room(x, z, level, hx, hz, 3 + Math.floorMod(part(i, 9), 3)));
            }
        }

        boolean overlaps(int x, int z, int level, int hx, int hz) {
            for (Room room : rooms) {
                if (room.level == level && Math.abs(x - room.x) < hx + room.hx + 2
                        && Math.abs(z - room.z) < hz + room.hz + 2) return true;
            }
            return false;
        }

        void carveRoom(Room room) {
            int base = room.level * 7;
            for (int y = 1; y <= room.height + 1; y++) {
                for (int z = room.z - room.hz - 1; z <= room.z + room.hz + 1; z++) {
                    for (int x = room.x - room.hx - 1; x <= room.x + room.hx + 1; x++) {
                        carve(x, base + y, z);
                    }
                }
            }
        }

        void corridor(Room from, Room to, int index) {
            int base = Math.min(from.level, to.level) * 7;
            int bend = (part(index, 10) & 1) == 0 ? to.x : from.x;
            leg(from.x, from.z, bend, from.z, base, index);
            leg(bend, from.z, bend, to.z, base, index + 17);
            leg(bend, to.z, to.x, to.z, base, index + 29);
        }

        void leg(int x0, int z0, int x1, int z1, int base, int part) {
            int dx = Integer.compare(x1, x0), dz = Integer.compare(z1, z0);
            int length = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
            for (int i = 0; i <= length; i++) {
                int x = x0 + dx * i, z = z0 + dz * i;
                for (int side = -1; side <= 1; side++) {
                    int sx = x + (dz == 0 ? 0 : side), sz = z + (dx == 0 ? 0 : side);
                    for (int y = 1; y <= 4; y++) carve(sx, base + y, sz);
                }
            }
        }

        void masonry(Room room) {
            int base = room.level * 7;
            for (int z = room.z - room.hz - 1; z <= room.z + room.hz + 1; z++) {
                for (int x = room.x - room.hx - 1; x <= room.x + room.hx + 1; x++) {
                    boolean edge = Math.abs(x - room.x) == room.hx + 1
                            || Math.abs(z - room.z) == room.hz + 1;
                    if (!edge) continue;
                    for (int y = 1; y <= room.height + 1; y++) {
                        if ((Math.abs(x - room.x) + Math.abs(z - room.z)) % 7 == 0
                                && y <= room.height - 1) continue;
                        build(x, base + y, z, masonry(x, base + y, z));
                    }
                }
            }
            // Broken arch fragments on both random walls; the doorway remains walkable.
            int side = (part(room.level + room.x + room.z, 11) & 1) == 0 ? room.hx + 1 : -room.hx - 1;
            for (int z = room.z - 1; z <= room.z + 1; z++) {
                build(room.x + side, base + room.height, z, masonry(room.x + side, base + room.height, z));
            }
            build(room.x - room.hx - 1, base + room.height + 1, room.z, Blocks.STONE_BRICK_SLAB);
            build(room.x + room.hx + 1, base + room.height + 1, room.z, Blocks.STONE_BRICK_SLAB);
        }

        void dress(Room room, int index) {
            int base = room.level * 7;
            for (int i = 0; i < 2 + Math.floorMod(part(index, 12), 4); i++) {
                int lane = part(index * 13 + i, 13);
                int x = room.x + signed(lane, Math.max(1, room.hx - 1));
                int z = room.z + signed(lane >>> 7, Math.max(1, room.hz - 1));
                int choice = Math.floorMod(lane >>> 14, 7);
                if (choice == 0) build(x, base + 1, z, Blocks.GRAVEL);
                else if (choice == 1) build(x, base + 1, z, Blocks.COBBLE_SLAB);
                else if (choice == 2) build(x, base + 2, z, Blocks.COBWEB);
                else if (choice == 3) build(x, base + 2, z, Blocks.VINE);
                else if (choice == 4) build(x, base + room.height, z, Blocks.GLOW_LICHEN);
                else if (choice == 5) build(x, base + 1, z, Blocks.CHEST);
                else build(x, base + 1, z, Blocks.MOSS_CARPET);
            }
            if ((part(index, 14) & 3) == 0) {
                build(room.x, base + 1, room.z, Blocks.CHEST); // buried loot marker
                build(room.x, base + 2, room.z, Blocks.GRAVEL);
            }
            // Four irregular columns make large halls read as supported chambers.
            if ((part(index, 15) & 1) == 0) {
                for (int dx : new int[] {-room.hx, room.hx}) for (int dz : new int[] {-room.hz, room.hz}) {
                    for (int y = 1; y <= room.height; y++) build(room.x + dx, base + y, room.z + dz,
                            masonry(room.x + dx, base + y, room.z + dz));
                }
            }
        }

        void verticalLink() {
            Room top = rooms.get(0), lower = rooms.get(rooms.size() - 1);
            int x = top.x, z = top.z;
            for (int i = 0; i < scale.levels * 6; i++) {
                int y = floor + i;
                carve(x, y - floor, z);
                if ((i & 1) == 0) build(x, y - floor, z, Blocks.STONE_BRICK_STAIRS);
            }
            // A second broken run is intentionally offset by the site hash.
            if ((part(lower.level, 16) & 1) == 0) {
                for (int i = 0; i < 4; i++) build(x + 1, i + 1, z, Blocks.COBBLE_STAIRS);
            }
        }

        void carve(int lx, int dy, int lz) {
            int[] p = rotate(lx, lz);
            int x = site.anchorX() + p[0], y = floor + dy, z = site.anchorZ() + p[1];
            if (y <= Blocks.MIN_Y || y >= Blocks.MAX_Y || dy < 1) return;
            int current = world.getBlock(x, y, z);
            if (current == Blocks.AIR) { carved.add(new BlockPos(x, y, z)); return; }
            if (current != Blocks.STONE || current == Blocks.BEDROCK
                    || current == Blocks.WATER_SOURCE || current == Blocks.LAVA_SOURCE) return;
            BlockPos pos = new BlockPos(x, y, z);
            if (!carved.contains(pos) && voxels.size() >= scale.cap) {
                complete = false;
                return;
            }
            if (carved.add(pos)) voxels.putIfAbsent(pos, RuinGenerator.Voxel.at(x, y, z, Blocks.AIR));
        }

        void build(int lx, int dy, int lz, int block) {
            int[] p = rotate(lx, lz);
            int x = site.anchorX() + p[0], y = floor + dy, z = site.anchorZ() + p[1];
            if (y <= Blocks.MIN_Y || y >= Blocks.MAX_Y) return;
            BlockPos pos = new BlockPos(x, y, z);
            if (voxels.containsKey(pos) && !carved.contains(pos)) return;
            if (!carved.contains(pos) && world.getBlock(x, y, z) != Blocks.AIR) return;
            if (!voxels.containsKey(pos) && voxels.size() >= scale.cap) {
                complete = false;
                return;
            }
            if (carved.contains(pos)) {
                voxels.put(pos, RuinGenerator.Voxel.at(x, y, z, block));
            } else {
                voxels.putIfAbsent(pos, RuinGenerator.Voxel.at(x, y, z, block));
            }
        }

        int masonry(int x, int y, int z) {
            int value = site.voxelLane(x, y, z, MATERIAL);
            int wear = site.voxelLane(x, y, z, WEAR);
            if ((wear & 31) == 0) return Blocks.MOSSY_STONE_BRICK;
            if ((wear & 63) == 1) return Blocks.MOSSY_COBBLE;
            return (value & 7) < 2 ? Blocks.COBBLE : Blocks.STONE_BRICK;
        }

        int part(int index, int role) { return site.partLane0(index, LAYOUT + role); }
        int signed(int value, int radius) {
            return radius <= 0 ? 0 : Math.floorMod(value, radius * 2 + 1) - radius;
        }
        int[] rotate(int x, int z) {
            return switch (site.direction()) {
                case 0 -> new int[] {x, z}; case 1 -> new int[] {-z, x};
                case 2 -> new int[] {-x, -z}; default -> new int[] {z, -x};
            };
        }
    }
}
