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
 * A stateless, air-only archaeological overlay.  The site hash selects every surviving
 * wall, chamber division, drift, column, cache, and weathering mark, so no shape is shared
 * by two sites even when their broad footprint happens to agree.
 */
public final class BuriedRuinGenerator implements StructureOverlayGenerator {
    private static final int SHELL_PURPOSE = 0x62;
    private static final int DETAIL_PURPOSE = 0x63;
    private static final int DRIFT_PURPOSE = 0x64;

    /** Site-key roll: SMALL 0-39, MEDIUM 40-79, LARGE 80-99. */
    private enum SizeClass {
        SMALL(2, 2, 1, 1),
        MEDIUM(4, 4, 2, 2),
        LARGE(6, 6, 3, 3);

        private final int halfExtentBase;
        private final int wallHeightBase;
        private final int buildingCount;
        private final int detailScale;

        SizeClass(int halfExtentBase, int wallHeightBase, int buildingCount, int detailScale) {
            this.halfExtentBase = halfExtentBase;
            this.wallHeightBase = wallHeightBase;
            this.buildingCount = buildingCount;
            this.detailScale = detailScale;
        }

        private static SizeClass roll(int siteKey) {
            int roll = Integer.remainderUnsigned(siteKey, 100);
            return roll < 40 ? SMALL : roll < 80 ? MEDIUM : LARGE;
        }
    }

    @Override
    public List<RuinGenerator.Voxel> plan(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world) {
        int floorY = findFloor(site.anchorX(), site.anchorZ(), world);
        if (floorY < Blocks.MIN_Y) return List.of();

        int shape = site.shapeLane();
        SizeClass size = SizeClass.roll(site.siteKey());
        int halfX = size.halfExtentBase + (shape & 1);
        int halfZ = size.halfExtentBase + ((shape >>> 1) & 1);
        int wallHeight = size.wallHeightBase + ((shape >>> 3) & 1);
        // A LARGE corner/pillar can be wall height + three blocks above the floor.
        if (floorY + wallHeight + 3 >= Blocks.MAX_Y) return List.of();
        Planner planner = new Planner(site, world, floorY);

        addPerimeter(planner, halfX, halfZ, wallHeight);
        addRoomRemnants(planner, halfX, halfZ, wallHeight, size.buildingCount);
        addProtrudingPillars(planner, halfX, halfZ, wallHeight, size.detailScale);
        addSandDrifts(planner, halfX, halfZ, size.detailScale);
        addCobwebsAndCaches(planner, halfX, halfZ, wallHeight, size.detailScale);
        addCollapseScatter(planner, halfX, halfZ, size.detailScale);
        addSatelliteRemnants(planner, halfX, halfZ, wallHeight, size.buildingCount);
        return planner.voxels.size() < 12 ? List.of() : List.copyOf(planner.voxels.values());
    }

    private void addPerimeter(Planner p, int halfX, int halfZ, int wallHeight) {
        for (int z = -halfZ; z <= halfZ; z++) for (int x = -halfX; x <= halfX; x++) {
            if (Math.abs(x) != halfX && Math.abs(z) != halfZ) continue;
            int lane = p.lane(x, 0, z, SHELL_PURPOSE);
            boolean corner = Math.abs(x) == halfX && Math.abs(z) == halfZ;
            // Door-sized losses, fractures, and complete collapsed segments are all local hash decisions.
            if (!corner && Integer.remainderUnsigned(lane, 11) < 3) continue;
            int rise = corner ? wallHeight + 1 : 1 + Integer.remainderUnsigned(lane >>> 5, wallHeight);
            for (int dy = 1; dy <= rise; dy++) {
                if (!corner && dy > 1 && Integer.remainderUnsigned(lane >>> (dy + 9), 13) == 0) continue;
                p.add(x, dy, z, p.material(x, dy, z));
            }
        }
    }

    private void addRoomRemnants(Planner p, int halfX, int halfZ, int wallHeight, int buildingCount) {
        int topology = p.site.topologyLane();
        int dividerAxis = (topology >>> 2) & 1;
        int divider = ((topology >>> 4) & 1) == 0 ? -1 : 1;
        int length = dividerAxis == 0 ? halfZ - 1 : halfX - 1;
        for (int offset = -length; offset <= length; offset++) {
            int x = dividerAxis == 0 ? divider : offset;
            int z = dividerAxis == 0 ? offset : divider;
            int lane = p.lane(x, 1, z, SHELL_PURPOSE + 1);
            if (Integer.remainderUnsigned(lane, 7) < 2) continue; // broken doorway / collapsed partition
            int rise = 1 + Integer.remainderUnsigned(lane >>> 4, Math.max(2, wallHeight));
            for (int dy = 1; dy <= rise; dy++) p.add(x, dy, z, p.material(x, dy, z));
        }

        // Larger sites retain additional independently hashed room divisions.
        for (int room = 1; room < buildingCount; room++) {
            int lane = p.site.partLane0(room, SHELL_PURPOSE + 1);
            int axis = (lane >>> 2) & 1;
            int span = axis == 0 ? halfZ - 1 : halfX - 1;
            int coordinate = 1 + Integer.remainderUnsigned(lane >>> 5, Math.max(1, span));
            if ((lane & 1) == 0) coordinate = -coordinate;
            for (int offset = -span; offset <= span; offset++) {
                int x = axis == 0 ? coordinate : offset;
                int z = axis == 0 ? offset : coordinate;
                int segment = p.lane(x, room + 1, z, SHELL_PURPOSE + room + 1);
                if (Integer.remainderUnsigned(segment, 7) < 2) continue;
                int rise = 1 + Integer.remainderUnsigned(segment >>> 4, Math.max(2, wallHeight));
                for (int dy = 1; dy <= rise; dy++) p.add(x, dy, z, p.material(x, dy, z));
            }
        }

        // Surviving roof lintels make the separated spaces read as partially exposed rooms.
        int lintelY = 2 + ((topology >>> 9) & 1);
        for (int x = -halfX + 1; x < halfX; x++) {
            int z = ((topology >>> 11) & 1) == 0 ? -halfZ : halfZ;
            if ((p.lane(x, lintelY, z, DETAIL_PURPOSE) & 3) == 0) p.add(x, lintelY, z,
                    p.material(x, lintelY, z));
        }
    }

    private void addProtrudingPillars(Planner p, int halfX, int halfZ, int wallHeight,
            int detailScale) {
        int count = 2 + detailScale * 2 + Integer.remainderUnsigned(p.site.agingLane() >>> 4, 4);
        for (int pillar = 0; pillar < count; pillar++) {
            int lane = p.site.partLane0(pillar, DETAIL_PURPOSE);
            int side = (lane >>> 2) & 3;
            int cross = Integer.remainderUnsigned(lane >>> 5,
                    (side == 0 || side == 2 ? halfX * 2 + 1 : halfZ * 2 + 1))
                    - (side == 0 || side == 2 ? halfX : halfZ);
            int x = side == 1 ? halfX : side == 3 ? -halfX : cross;
            int z = side == 0 ? -halfZ : side == 2 ? halfZ : cross;
            int rise = 2 + Integer.remainderUnsigned(lane >>> 12, wallHeight + 2);
            for (int dy = 1; dy <= rise; dy++) p.add(x, dy, z, p.material(x, dy, z));
            if ((lane & 3) == 0) p.add(x, rise + 1, z, Blocks.STONE_BRICK_SLAB);
        }
    }

    private void addSandDrifts(Planner p, int halfX, int halfZ, int detailScale) {
        int drifts = 2 + detailScale * 2 + Integer.remainderUnsigned(p.site.adaptationLane(), 5);
        for (int drift = 0; drift < drifts; drift++) {
            int lane = p.site.partLane1(drift, DRIFT_PURPOSE);
            int x = Integer.remainderUnsigned(lane, halfX * 2 + 1) - halfX;
            int z = Integer.remainderUnsigned(lane >>> 5, halfZ * 2 + 1) - halfZ;
            int block = (lane & 15) == 0 ? Blocks.RED_SAND : (lane & 3) == 0 ? Blocks.DIRT : Blocks.SAND;
            p.add(x, 1, z, block);
            // A handful of dune crests are two blocks high, but only where the cave remains air.
            if ((lane & 31) == 0) p.add(x, 2, z, block);
        }
    }

    private void addCobwebsAndCaches(Planner p, int halfX, int halfZ, int wallHeight,
            int detailScale) {
        int details = 2 + detailScale * 3 + Integer.remainderUnsigned(p.site.agingLane() >>> 10, 6);
        for (int detail = 0; detail < details; detail++) {
            int lane = p.site.partLane1(detail + 19, DETAIL_PURPOSE);
            int x = Integer.remainderUnsigned(lane, halfX * 2 + 1) - halfX;
            int z = Integer.remainderUnsigned(lane >>> 5, halfZ * 2 + 1) - halfZ;
            if ((lane & 7) < 5) p.add(x, 2 + Integer.remainderUnsigned(lane >>> 10,
                    Math.max(1, wallHeight)), z, Blocks.COBWEB);
        }
        int cacheLane = p.site.partLane0(71, DETAIL_PURPOSE);
        int cacheX = ((cacheLane & 1) == 0 ? -1 : 1) * Math.max(1, halfX - 1);
        int cacheZ = ((cacheLane & 2) == 0 ? -1 : 1) * Math.max(1, halfZ - 1);
        p.add(cacheX, 1, cacheZ, Blocks.CHEST);
        // The cache is visibly buried by a drift where air permits it; no terrain is ever replaced.
        if ((cacheLane & 4) == 0) p.add(cacheX, 2, cacheZ, Blocks.SAND);
        if ((cacheLane & 8) == 0) p.add(cacheX + Integer.signum(-cacheX), 1, cacheZ,
                Blocks.COBBLE_SLAB);
    }

    private void addCollapseScatter(Planner p, int halfX, int halfZ, int detailScale) {
        int count = 3 + detailScale * 3 + Integer.remainderUnsigned(p.site.difficultyLane(), 6);
        for (int part = 0; part < count; part++) {
            int lane = p.site.partLane0(part + 37, DRIFT_PURPOSE);
            int x = Integer.remainderUnsigned(lane, halfX * 2 + 1) - halfX;
            int z = Integer.remainderUnsigned(lane >>> 6, halfZ * 2 + 1) - halfZ;
            int block = (lane & 7) == 0 ? Blocks.CHISELED_SANDSTONE
                    : (lane & 3) == 0 ? Blocks.CUT_SANDSTONE : p.material(x, 1, z);
            p.add(x, 1, z, block);
        }
    }

    /**
     * Medium and large sites grow into multiple scattered, collapsed annexes rather than a
     * uniformly enlarged box. Each annex is an independently hashed fragment of a building.
     */
    private void addSatelliteRemnants(Planner p, int halfX, int halfZ, int wallHeight,
            int buildingCount) {
        for (int building = 1; building < buildingCount; building++) {
            int lane = p.site.partLane1(building, SHELL_PURPOSE + 9);
            int side = (lane >>> 2) & 3;
            int halfWidth = 1 + Integer.remainderUnsigned(lane >>> 5, 2);
            int halfDepth = 1 + Integer.remainderUnsigned(lane >>> 8, 2);
            int offset = 2 + Integer.remainderUnsigned(lane >>> 11, 3);
            int centerX = side == 1 ? halfX + offset : side == 3 ? -halfX - offset
                    : Integer.remainderUnsigned(lane >>> 15, halfX * 2 + 1) - halfX;
            int centerZ = side == 0 ? -halfZ - offset : side == 2 ? halfZ + offset
                    : Integer.remainderUnsigned(lane >>> 19, halfZ * 2 + 1) - halfZ;
            int annexHeight = Math.max(2, wallHeight - 1
                    + Integer.remainderUnsigned(lane >>> 23, 2));
            for (int z = -halfDepth; z <= halfDepth; z++) for (int x = -halfWidth; x <= halfWidth; x++) {
                if (Math.abs(x) != halfWidth && Math.abs(z) != halfDepth) continue;
                int segment = p.lane(centerX + x, building, centerZ + z, SHELL_PURPOSE + 10);
                if (Integer.remainderUnsigned(segment, 9) < 3) continue;
                int rise = 1 + Integer.remainderUnsigned(segment >>> 5, annexHeight);
                for (int dy = 1; dy <= rise; dy++) p.add(centerX + x, dy, centerZ + z,
                        p.material(centerX + x, dy, centerZ + z));
            }
            p.add(centerX, 1, centerZ, (lane & 3) == 0 ? Blocks.CHISELED_SANDSTONE
                    : Blocks.CUT_SANDSTONE);
        }
    }

    private int findFloor(int x, int z, SurfaceDecorator.BlockView world) {
        for (int y = Math.min(Blocks.SEA_LEVEL - 8, Blocks.MAX_Y - 5); y >= Blocks.MIN_Y; y--) {
            if (isSupport(world.getBlock(x, y, z)) && world.getBlock(x, y + 1, z) == Blocks.AIR
                    && world.getBlock(x, y + 2, z) == Blocks.AIR && world.getBlock(x, y + 3, z) == Blocks.AIR) {
                return y;
            }
        }
        return Blocks.MIN_Y - 1;
    }

    private boolean isSupport(int block) {
        return StructureTerrainRules.isStableGround(block);
    }

    private static int[] rotate(int x, int z, int direction) {
        return switch (direction) {
            case 0 -> new int[] {x, z}; case 1 -> new int[] {-z, x};
            case 2 -> new int[] {-x, -z}; default -> new int[] {z, -x};
        };
    }

    private static final class Planner {
        private final StructureSiteDescriptor site;
        private final SurfaceDecorator.BlockView world;
        private final int floorY;
        private final Map<BlockPos, RuinGenerator.Voxel> voxels = new LinkedHashMap<>();

        private Planner(StructureSiteDescriptor site, SurfaceDecorator.BlockView world, int floorY) {
            this.site = site; this.world = world; this.floorY = floorY;
        }

        private int lane(int localX, int dy, int localZ, int purpose) {
            int[] r = rotate(localX, localZ, site.direction());
            return site.voxelLane(site.anchorX() + r[0], floorY + dy, site.anchorZ() + r[1], purpose);
        }

        private int material(int localX, int dy, int localZ) {
            int lane = lane(localX, dy, localZ, SHELL_PURPOSE);
            int wear = 12 + Integer.remainderUnsigned(site.agingLane() >>> 16, 28);
            int roll = Integer.remainderUnsigned(lane, 100);
            if (roll < wear / 3) return Blocks.MOSSY_STONE_BRICK;
            if (roll < wear) return Blocks.MOSSY_COBBLE;
            if (roll < wear + 16) return Blocks.COBBLE; // broken/cracked brick read
            return Blocks.STONE_BRICK;
        }

        private void add(int localX, int dy, int localZ, int block) {
            int[] r = rotate(localX, localZ, site.direction());
            int x = site.anchorX() + r[0], y = floorY + dy, z = site.anchorZ() + r[1];
            if (y < Blocks.MIN_Y || y >= Blocks.MAX_Y || world.getBlock(x, y, z) != Blocks.AIR) return;
            BlockPos pos = new BlockPos(x, y, z);
            voxels.putIfAbsent(pos, RuinGenerator.Voxel.at(x, y, z, block));
        }
    }
}
