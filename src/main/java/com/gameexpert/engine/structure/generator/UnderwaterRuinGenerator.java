package com.gameexpert.engine.structure.generator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gameexpert.engine.BlockPos;
import com.gameexpert.engine.SurfaceDecorator;
import com.gameexpert.engine.structure.RuinGenerator;
import com.gameexpert.engine.structure.StructureOverlayGenerator;
import com.gameexpert.engine.structure.StructureSiteDescriptor;
import com.gameexpert.engine.structure.StructureTerrainRules;
import com.gameexpert.terrain.Blocks;

/**
 * Stateless, site-hash assembled drowned complex. It deliberately replaces only existing
 * water: it neither excavates the sea bed nor persists a terrain change. Rooms are grown
 * from a hash-driven walk rather than selected from a finite set of templates.
 */
public final class UnderwaterRuinGenerator implements StructureOverlayGenerator {
    private static final int LAYOUT_PURPOSE = 0x75;
    private static final int WEAR_PURPOSE = 0x76;
    private static final int MATERIAL_PURPOSE = 0x77;
    private static final int DECOR_PURPOSE = 0x78;

    @Override
    public List<RuinGenerator.Voxel> plan(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world) {
        int floor = floorY(site.anchorX(), site.anchorZ(), world);
        if (floor < Blocks.MIN_Y || floor + 8 >= Blocks.MAX_Y) return List.of();

        Planner plan = new Planner(site, world, floor);
        SizeClass size = sizeClass(site);
        int roomCount = size.roomCount(site);
        int x = 0;
        int z = 0;
        int largestRoom = 0;
        int treasureX = 0;
        int treasureZ = 0;
        for (int room = 0; room < roomCount; room++) {
            int lane = lane(site, room, LAYOUT_PURPOSE);
            int previousX = x;
            int previousZ = z;
            if (room > 0) {
                int direction = (lane >>> 3) & 3;
                int stride = size.stride(4 + ((lane >>> 7) & 7));
                x += direction == 1 ? stride : direction == 3 ? -stride
                        : signed(lane, 11, stride / 2);
                z += direction == 2 ? stride : direction == 0 ? -stride
                        : signed(lane, 15, stride / 2);
            }
            int halfX = size.footprint(2 + ((lane >>> 19) & 3));
            int halfZ = size.footprint(2 + ((lane >>> 21) & 3));
            int height = size.height(3 + ((lane >>> 23) & 3));
            room(plan, site, room, x, z, halfX, halfZ, height);
            if (halfX * halfZ > largestRoom) {
                largestRoom = halfX * halfZ;
                treasureX = x;
                treasureZ = z;
            }
            if (room > 0) corridor(plan, site, room, previousX, previousZ, x, z);
        }
        treasureChamber(plan, site, treasureX, treasureZ);
        perimeterRelics(plan, site, roomCount, size);
        return !plan.valid || plan.size() < 28 ? List.of() : plan.voxels();
    }

    private void room(Planner p, StructureSiteDescriptor site, int room, int cx, int cz,
            int halfX, int halfZ, int height) {
        int lane = lane(site, room, LAYOUT_PURPOSE + 1);
        int doorSide = lane & 3;
        for (int z = -halfZ; z <= halfZ; z++) {
            for (int x = -halfX; x <= halfX; x++) {
                if (Math.abs(x) != halfX && Math.abs(z) != halfZ) continue;
                for (int dy = 1; dy <= height; dy++) {
                    if (doorway(x, z, dy, halfX, halfZ, doorSide)
                            || collapsed(site, room, cx + x, dy, cz + z)) continue;
                    p.add(cx + x, dy, cz + z, masonry(site, cx + x, p.y(dy), cz + z));
                }
            }
        }
        // Hash-selected partial roof leaves every chamber a different drowned silhouette.
        for (int z = -halfZ + 1; z < halfZ; z++) for (int x = -halfX + 1; x < halfX; x++) {
            if ((site.voxelLane(p.x(cx + x, cz + z), p.y(height + 1),
                    p.z(cx + x, cz + z), WEAR_PURPOSE) & 15)
                    < 6 + ((lane >>> 4) & 3)) {
                p.add(cx + x, height + 1, cz + z, Blocks.STONE_BRICK_SLAB);
            }
        }
        columns(p, site, room, cx, cz, halfX, halfZ, height, lane);
        floorScatter(p, site, room, cx, cz, halfX, halfZ, lane);
    }

    private void columns(Planner p, StructureSiteDescriptor site, int room, int cx, int cz,
            int halfX, int halfZ, int height, int lane) {
        int inset = 1 + ((lane >>> 8) & 1);
        int spacing = 2 + ((lane >>> 9) & 1);
        for (int z = -halfZ + inset; z <= halfZ - inset; z += spacing) {
            for (int x = -halfX + inset; x <= halfX - inset; x += spacing) {
                if ((lane(site, room * 131 + (x + 9) * 17 + z, DECOR_PURPOSE) & 3) != 0) continue;
                int tall = 1 + (lane(site, room * 211 + x * 19 + z, DECOR_PURPOSE) & 3);
                for (int dy = 1; dy <= Math.min(height, tall + 1); dy++) {
                    if (!collapsed(site, room + 41, cx + x, dy, cz + z)) {
                        p.add(cx + x, dy, cz + z, dy == tall + 1
                                ? Blocks.STONE_BRICK_SLAB
                                : wallMaterial(site, cx + x, p.y(dy), cz + z));
                    }
                }
            }
        }
    }

    private void floorScatter(Planner p, StructureSiteDescriptor site, int room, int cx, int cz,
            int halfX, int halfZ, int lane) {
        for (int z = -halfZ + 1; z < halfZ; z++) for (int x = -halfX + 1; x < halfX; x++) {
            int choice = lane(site, room * 521 + (x + 12) * 31 + z, DECOR_PURPOSE) & 31;
            if (choice == 0 || choice == 1) p.add(cx + x, 1, cz + z, Blocks.SAND);
            else if (choice == 2) p.add(cx + x, 1, cz + z, Blocks.GRAVEL);
            else if (choice == 3) p.add(cx + x, 1, cz + z, Blocks.CORAL);
            else if (choice == 4) {
                int kelp = 1 + ((lane >>> 12) & 3);
                for (int dy = 1; dy <= kelp; dy++) p.add(cx + x, dy, cz + z, Blocks.KELP);
            } else if (choice == 5) p.add(cx + x, 1, cz + z, Blocks.COBWEB);
        }
    }

    private void corridor(Planner p, StructureSiteDescriptor site, int room, int startX, int startZ,
            int endX, int endZ) {
        int lane = lane(site, room, LAYOUT_PURPOSE + 2);
        int length = Math.max(Math.abs(endX - startX), Math.abs(endZ - startZ));
        for (int step = 1; step < length; step++) {
            int x = startX + (endX - startX) * step / length;
            int z = startZ + (endZ - startZ) * step / length;
            if ((lane(site, room * 97 + step, WEAR_PURPOSE) & 7) == 0) continue;
            p.add(x, 1, z, masonry(site, p.x(x, z), p.y(1), p.z(x, z)));
            if ((step & 1) == 0) {
                for (int dy = 2; dy <= 3 + ((lane >>> 7) & 1); dy++) {
                    p.add(x, dy, z, wallMaterial(site, p.x(x, z), p.y(dy), p.z(x, z)));
                }
                p.add(x, 4, z, Blocks.STONE_BRICK_SLAB);
            }
        }
    }

    private void treasureChamber(Planner p, StructureSiteDescriptor site, int x, int z) {
        int lane = lane(site, 991, DECOR_PURPOSE);
        p.add(x, 1, z, Blocks.CHEST);
        p.add(x + 1, 1, z, (lane & 1) == 0 ? Blocks.COBBLE_SLAB : Blocks.MOSSY_COBBLE_WALL);
        p.add(x - 1, 1, z, (lane & 2) == 0 ? Blocks.STONE_BRICK_SLAB : Blocks.CORAL);
        if ((lane & 4) != 0) p.add(x, 2, z, Blocks.COBWEB);
    }

    private void perimeterRelics(Planner p, StructureSiteDescriptor site, int rooms, SizeClass size) {
        int count = size.relicCount(4 + bounded(site, rooms * 37, DECOR_PURPOSE, 9));
        for (int i = 0; i < count; i++) {
            int lane = lane(site, i * 53, DECOR_PURPOSE);
            int reach = size.relicReach();
            int x = Math.floorMod(lane >>> 2, reach * 2 + 1) - reach;
            int z = Math.floorMod(lane >>> 7, reach * 2 + 1) - reach;
            int height = size.relicHeight(1 + ((lane >>> 12) & 3));
            for (int dy = 1; dy <= height; dy++) {
                if ((lane(site, i * 79 + dy, WEAR_PURPOSE) & 7) != 0) {
                    p.add(x, dy, z, dy == height ? Blocks.STONE_BRICK_SLAB
                            : wallMaterial(site, p.x(x, z), p.y(dy), p.z(x, z)));
                }
            }
        }
    }

    private boolean doorway(int x, int z, int dy, int halfX, int halfZ, int side) {
        if (dy > 2) return false;
        return switch (side) {
            case 0 -> z == -halfZ && Math.abs(x) <= 1;
            case 1 -> x == halfX && Math.abs(z) <= 1;
            case 2 -> z == halfZ && Math.abs(x) <= 1;
            default -> x == -halfX && Math.abs(z) <= 1;
        };
    }

    private boolean collapsed(StructureSiteDescriptor site, int room, int x, int dy, int z) {
        int age = site.agingLane() & 127;
        int wear = site.voxelLane(site.anchorX() + x, dy, site.anchorZ() + z,
                WEAR_PURPOSE + room) & 255;
        return dy > 1 && wear < 18 + age / 2;
    }

    private int masonry(StructureSiteDescriptor site, int x, int y, int z) {
        int choice = site.voxelLane(x, y, z, MATERIAL_PURPOSE) & 15;
        if (choice == 0) return Blocks.MOSS_BLOCK;
        if (choice <= 3) return Blocks.MOSSY_STONE_BRICK;
        if (choice <= 7) return Blocks.MOSSY_COBBLE;
        if (choice == 8) return Blocks.COBBLE;
        if (choice == 9) return Blocks.CUT_SANDSTONE;
        if (choice == 10) return Blocks.CHISELED_SANDSTONE;
        return Blocks.STONE_BRICK;
    }

    private int wallMaterial(StructureSiteDescriptor site, int x, int y, int z) {
        int choice = site.voxelLane(x, y, z, MATERIAL_PURPOSE + 1) & 3;
        return choice == 0 ? Blocks.MOSSY_COBBLE_WALL : choice == 1 ? Blocks.COBBLE_WALL
                : Blocks.STONE_BRICK_WALL;
    }

    private int floorY(int x, int z, SurfaceDecorator.BlockView world) {
        for (int y = Blocks.MAX_Y - 2; y >= Blocks.MIN_Y; y--) {
            if (support(world.getBlock(x, y, z)) && water(world.getBlock(x, y + 1, z))) return y;
        }
        return Blocks.MIN_Y - 1;
    }

    private boolean support(int block) {
        return StructureTerrainRules.isStableGround(block);
    }

    private static boolean water(int block) {
        return block >= Blocks.WATER_SOURCE && block < Blocks.LAVA_SOURCE;
    }

    private int lane(StructureSiteDescriptor site, int index, int purpose) {
        return site.partLane0(index, purpose);
    }

    private int bounded(StructureSiteDescriptor site, int index, int purpose, int bound) {
        return Math.floorMod(lane(site, index, purpose), bound);
    }

    /** Site-key roll: SMALL 0..39, MEDIUM 40..79, LARGE 80..99. */
    private SizeClass sizeClass(StructureSiteDescriptor site) {
        int roll = Math.floorMod(site.siteKey(), 100);
        return roll < 40 ? SizeClass.SMALL : roll < 80 ? SizeClass.MEDIUM : SizeClass.LARGE;
    }

    private int signed(int lane, int shift, int magnitude) {
        return ((lane >>> shift) & 1) == 0 ? -magnitude : magnitude;
    }

    private static int[] rotate(int x, int z, int direction) {
        return switch (direction) {
            case 0 -> new int[] {x, z};
            case 1 -> new int[] {-z, x};
            case 2 -> new int[] {-x, -z};
            default -> new int[] {z, -x};
        };
    }

    private enum SizeClass {
        SMALL {
            @Override int roomCount(StructureSiteDescriptor site) {
                return 2 + Math.floorMod(site.partLane0(0, LAYOUT_PURPOSE), 3);
            }
            @Override int footprint(int base) { return Math.max(1, base - 1); }
            @Override int height(int base) { return Math.max(2, base - 1); }
            @Override int stride(int base) { return Math.max(3, base - 2); }
            @Override int relicCount(int base) { return Math.max(2, base - 2); }
            @Override int relicReach() { return 11; }
            @Override int relicHeight(int base) { return Math.max(1, base - 1); }
        },
        MEDIUM {
            @Override int roomCount(StructureSiteDescriptor site) {
                return 3 + Math.floorMod(site.partLane0(0, LAYOUT_PURPOSE), 5);
            }
            @Override int footprint(int base) { return base; }
            @Override int height(int base) { return base; }
            @Override int stride(int base) { return base; }
            @Override int relicCount(int base) { return base; }
            @Override int relicReach() { return 15; }
            @Override int relicHeight(int base) { return base; }
        },
        LARGE {
            @Override int roomCount(StructureSiteDescriptor site) {
                return 6 + Math.floorMod(site.partLane0(0, LAYOUT_PURPOSE), 6);
            }
            @Override int footprint(int base) { return base + 2; }
            @Override int height(int base) { return base + 2; }
            @Override int stride(int base) { return base + 3; }
            @Override int relicCount(int base) { return base + 4; }
            @Override int relicReach() { return 23; }
            @Override int relicHeight(int base) { return base + 2; }
        };

        abstract int roomCount(StructureSiteDescriptor site);
        abstract int footprint(int base);
        abstract int height(int base);
        abstract int stride(int base);
        abstract int relicCount(int base);
        abstract int relicReach();
        abstract int relicHeight(int base);
    }

    private static final class Planner {
        private final StructureSiteDescriptor site;
        private final SurfaceDecorator.BlockView world;
        private final int floor;
        private final Map<BlockPos, RuinGenerator.Voxel> voxels = new LinkedHashMap<>();
        private final Map<BlockPos, Integer> seabed = new LinkedHashMap<>();
        private boolean valid = true;

        private Planner(StructureSiteDescriptor site, SurfaceDecorator.BlockView world, int floor) {
            this.site = site;
            this.world = world;
            this.floor = floor;
        }

        private int x(int localX, int localZ) {
            return site.anchorX() + rotate(localX, localZ, site.direction())[0];
        }
        private int z(int localX, int localZ) {
            return site.anchorZ() + rotate(localX, localZ, site.direction())[1];
        }
        private int y(int dy) { return floor + dy; }

        private void add(int localX, int dy, int localZ, int block) {
            if (!valid) return;
            int[] point = rotate(localX, localZ, site.direction());
            int x = site.anchorX() + point[0];
            int y = floor + dy;
            int z = site.anchorZ() + point[1];
            int ground = seabed.computeIfAbsent(new BlockPos(x, floor, z), ignored -> ground(x, z));
            if (ground < Blocks.MIN_Y) { valid = false; return; }
            if (y < Blocks.MIN_Y || y >= Blocks.MAX_Y || !water(world.getBlock(x, y, z))) return;
            // Bridge shallow seabed steps only below real floor pieces; roofs stay hollow.
            if (dy == 1) for (int supportY = ground + 1; supportY <= floor; supportY++) {
                BlockPos support = new BlockPos(x, supportY, z);
                voxels.putIfAbsent(support, RuinGenerator.Voxel.at(x, supportY, z, Blocks.COBBLE));
            }
            BlockPos pos = new BlockPos(x, y, z);
            voxels.putIfAbsent(pos, RuinGenerator.Voxel.at(x, y, z, block));
        }

        private int ground(int x, int z) {
            if (!water(world.getBlock(x, floor + 1, z))) return Blocks.MIN_Y - 1;
            for (int y = floor; y >= Math.max(Blocks.MIN_Y, floor - 2); y--) {
                int block = world.getBlock(x, y, z);
                if (StructureTerrainRules.isStableGround(block)) return y;
                if (!water(block)) break;
            }
            return Blocks.MIN_Y - 1;
        }

        private int size() { return voxels.size(); }
        private List<RuinGenerator.Voxel> voxels() { return List.copyOf(voxels.values()); }
    }
}
