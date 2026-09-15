package com.gameexpert.engine.structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gameexpert.engine.BlockPos;
import com.gameexpert.engine.SurfaceDecorator;
import com.gameexpert.terrain.Blocks;

/**
 * Runtime-only ruin raster. It places into existing AIR or water and never digs,
 * flattens, replaces a surface block, or writes the frozen terrain generator.
 */
public final class RuinGenerator {
    public static final class Voxel {
        private final int x;
        private final int y;
        private final int z;
        private final int blockType;
        private final int blockState;

        private Voxel(int x, int y, int z, int blockType) {
            this(x, y, z, blockType, 0);
        }

        private Voxel(int x, int y, int z, int blockType, int blockState) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockType = blockType;
            this.blockState = blockState;
        }

        /** Narrow construction hook for per-kind overlay generator modules. */
        public static Voxel at(int x, int y, int z, int blockType) {
            return new Voxel(x, y, z, blockType);
        }

        /** Structure-only state hook for directional and connected building blocks. */
        public static Voxel at(int x, int y, int z, int blockType, int blockState) {
            return new Voxel(x, y, z, blockType, blockState);
        }

        /**
         * Coordinate view for assertions and small generators. Runtime planning hot paths use the
         * primitive accessors below so a large site does not retain a second BlockPos per voxel.
         */
        public BlockPos pos() { return new BlockPos(x, y, z); }
        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        public int blockType() { return blockType; }
        public int blockState() { return blockState; }
    }

    private static final int COLLAPSE_PURPOSE = 0x32;
    private static final int MATERIAL_PURPOSE = 0x33;
    private static final int AGING_PURPOSE = 0x34;

    public List<Voxel> plan(StructureSiteDescriptor site, SurfaceDecorator.BlockView world) {
        List<Voxel> plan = switch (site.kind()) {
            case SMALL_RUIN -> surfacePlan(site, world, smallFootprint(site));
            case MEDIUM_RUIN -> surfacePlan(site, world, mediumFootprint(site));
            case TOMB_RUIN -> surfacePlan(site, world, tombFootprint(site));
            case GENERAL_RUIN -> surfacePlan(site, world, generalFootprint(site));
            case FLOODED_RUIN -> floodedPlan(site, world);
            default -> StructureGeneratorCatalog.plan(site, world);
        };
        return switch (site.kind()) {
            case CAMPING_SITE, SMALL_RUIN, MEDIUM_RUIN, TOMB_RUIN, GENERAL_RUIN,
                    RUINED_TOWER, METEOR_CRATER, UNDERWATER_RUIN, FLOODED_RUIN ->
                    SurfaceStructureConnectivity.grounded(plan, world);
            default -> plan;
        };
    }

    private List<Voxel> surfacePlan(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world, List<Cell> footprint) {
        ensureRequiredCore(footprint);
        SurfaceHeightCache heights = new SurfaceHeightCache(world);
        // A player edit at the site identity must reject the site, not cause a retry offset to
        // materialize a different half-ruin beside it.
        if (!requiredCoreAvailable(site, world, heights, footprint)) return List.of();
        List<Voxel> best = List.of();
        int step = 2;
        int[][] offsets = {{0, 0}, {step, 0}, {-step, 0}, {0, step}, {0, -step},
                {step, step}, {-step, step}, {step, -step}, {-step, -step}};
        int bestSolidCount = -1;
        for (int[] offset : offsets) {
            List<Voxel> candidate = surfacePlanAt(
                    site, world, heights, footprint, offset[0], offset[1]);
            int solidCount = solidVoxelCount(candidate);
            if (solidCount > bestSolidCount
                    || (solidCount == bestSolidCount && candidate.size() > best.size())) {
                best = candidate;
                bestSolidCount = solidCount;
            }
        }
        int minimum = switch (site.kind()) {
            case SMALL_RUIN -> 24;
            case TOMB_RUIN -> 32;
            case MEDIUM_RUIN -> 48;
            case GENERAL_RUIN -> 40;
            default -> 24;
        };
        return bestSolidCount < minimum ? List.of() : best;
    }

    private int solidVoxelCount(List<Voxel> voxels) {
        int count = 0;
        for (Voxel voxel : voxels) if (voxel.blockType() != Blocks.AIR) count++;
        return count;
    }

    /** Every surface footprint needs one stable core even when its sparse cells miss the hash grid. */
    private void ensureRequiredCore(List<Cell> footprint) {
        for (Cell cell : footprint) if (cell.required) return;
        int coreIndex = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int index = 0; index < footprint.size(); index++) {
            Cell cell = footprint.get(index);
            int distance = Math.abs(cell.x) + Math.abs(cell.z);
            if (distance < bestDistance) {
                bestDistance = distance;
                coreIndex = index;
            }
        }
        if (coreIndex < 0) return;
        Cell core = footprint.get(coreIndex);
        footprint.set(coreIndex, new Cell(core.x, core.z, core.height, core.role, true));
    }

    private boolean requiredCoreAvailable(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world, SurfaceHeightCache heights,
            List<Cell> footprint) {
        for (Cell cell : footprint) {
            if (!cell.required) continue;
            int[] rotated = rotate(cell.x, cell.z, site.direction());
            int x = site.anchorX() + rotated[0];
            int z = site.anchorZ() + rotated[1];
            int ground = heights.groundY(x, z);
            if (ground < Blocks.MIN_Y || ground + cell.height > Blocks.MAX_Y) return false;
            for (int dy = 1; dy <= cell.height; dy++) {
                if (!StructureTerrainRules.isReplaceableByStructure(
                        world.getBlock(x, ground + dy, z))) return false;
            }
        }
        return true;
    }

    private List<Voxel> surfacePlanAt(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world, SurfaceHeightCache heights,
            List<Cell> footprint, int offsetX, int offsetZ) {
        int minGround = Integer.MAX_VALUE;
        int base = Integer.MIN_VALUE;
        for (Cell cell : footprint) {
            int[] rotated = rotate(cell.x, cell.z, site.direction());
            int x = site.anchorX() + offsetX + rotated[0];
            int z = site.anchorZ() + offsetZ + rotated[1];
            int ground = heights.groundY(x, z);
            if (ground < Blocks.MIN_Y || ground + cell.height > Blocks.MAX_Y) return List.of();
            minGround = Math.min(minGround, ground);
            base = Math.max(base, ground);
        }
        // A ruin may bridge a gentle one-block rise, but it must read as one building rather
        // than a set of independently warped columns.
        if (base - minGround > 2) return List.of();

        // Reject the candidate as a whole when terrain or an edit intersects it.  Silently
        // dropping just that column was the source of sparse, one-or-two-course "buildings".
        for (Cell cell : footprint) {
            int[] rotated = rotate(cell.x, cell.z, site.direction());
            int x = site.anchorX() + offsetX + rotated[0];
            int z = site.anchorZ() + offsetZ + rotated[1];
            int ground = heights.groundY(x, z);
            if (base + cell.height > Blocks.MAX_Y) return List.of();
            for (int y = ground + 1; y <= base + cell.height; y++) {
                if (!StructureTerrainRules.isReplaceableByStructure(
                        world.getBlock(x, y, z))) return List.of();
            }
        }

        Map<BlockPos, Voxel> plan = new LinkedHashMap<>();
        for (Cell cell : footprint) {
            int[] rotated = rotate(cell.x, cell.z, site.direction());
            int x = site.anchorX() + offsetX + rotated[0];
            int z = site.anchorZ() + offsetZ + rotated[1];
            int ground = heights.groundY(x, z);
            int support = world.getBlock(x, ground, z);
            for (int y = ground + 1; y <= base; y++) {
                int block = material(site, x, y, z, Role.SUPPORT, support);
                plan.putIfAbsent(new BlockPos(x, y, z), new Voxel(x, y, z, block));
            }
            for (int dy = cell.start; dy <= cell.height; dy++) {
                int y = base + dy;
                int block = cell.role == Role.CLEAR
                        ? Blocks.AIR : material(site, x, y, z, cell.role, support);
                BlockPos pos = new BlockPos(x, y, z);
                if (block == Blocks.AIR) {
                    plan.putIfAbsent(pos, new Voxel(x, y, z, block));
                } else {
                    Voxel existing = plan.get(pos);
                    if (existing == null || existing.blockType() == Blocks.AIR) {
                        plan.put(pos, new Voxel(x, y, z, block));
                    }
                }
            }
        }
        if (plan.isEmpty()) return List.of();
        addSurfaceLoot(site, world, plan, offsetX, offsetZ, base);
        return canonical(plan);
    }

    private List<Voxel> floodedPlan(StructureSiteDescriptor site, SurfaceDecorator.BlockView world) {
        int span = span(site, 10, 18, 30);
        int half = span / 2;
        int halfZ = Math.max(3, half - 1 - ((site.topologyLane() >>> 5) & 1));
        int family = (site.shapeLane() >>> 2) & 3;
        SubmergedFloorCache floors = new SubmergedFloorCache(world);
        Map<BlockPos, Voxel> plan = new LinkedHashMap<>();
        for (int z = -halfZ; z <= halfZ; z++) {
            for (int x = -half; x <= half; x++) {
                boolean perimeter = Math.abs(x) == half || Math.abs(z) == halfZ;
                boolean axis = family == 0 ? z == 0
                        : family == 1 ? x == 0
                        : family == 2 ? (z == 0 || x == -half / 2)
                        : (Math.abs(x) == Math.max(2, half / 2)
                                && Math.abs(z) <= Math.max(2, halfZ / 2));
                boolean paving = !perimeter && !axis
                        && ((x + z + site.topologyLane()) & 5) == 0;
                if (!perimeter && !axis && !paving) continue;
                if (!identityAnchor(x, z) && collapsed(site, x, z, perimeter)) continue;
                int[] rotated = rotate(x, z, site.direction());
                int wx = site.anchorX() + rotated[0], wz = site.anchorZ() + rotated[1];
                int y = floors.floorY(wx, wz);
                if (y < Blocks.MIN_Y || !isWater(world.getBlock(wx, y, wz))) {
                    if (identityAnchor(x, z)) return List.of();
                    continue;
                }
                int lane = site.voxelLane(wx, y, wz, MATERIAL_PURPOSE);
                int height = perimeter ? 2 + ((lane >>> 5) & 1)
                        : axis && ((lane >>> 7) & 3) == 0 ? 2 : 1;
                for (int dy = 0; dy < height; dy++) {
                    int wy = y + dy;
                    if (!isWater(world.getBlock(wx, wy, wz))) break;
                    int block = material(site, wx, wy, wz,
                            paving ? Role.FLOOR : Role.WALL,
                            world.getBlock(wx, wy - 1, wz));
                    plan.putIfAbsent(new BlockPos(wx, wy, wz), new Voxel(wx, wy, wz, block));
                }
            }
        }

        int pillarInset = Math.max(2, half - 2);
        int pillarZ = Math.max(2, halfZ - 2);
        int[][] pillars = {
                {-pillarInset, -pillarZ}, {pillarInset, -pillarZ},
                {pillarInset, pillarZ}, {-pillarInset, pillarZ}
        };
        for (int index = 0; index < pillars.length; index++) {
            int[] point = rotate(pillars[index][0], pillars[index][1], site.direction());
            int wx = site.anchorX() + point[0], wz = site.anchorZ() + point[1];
            int y = floors.floorY(wx, wz);
            int height = 2 + Math.floorMod(site.partLane0(index, AGING_PURPOSE), 3);
            for (int dy = 0; y >= Blocks.MIN_Y && dy < height; dy++) {
                if (!isWater(world.getBlock(wx, y + dy, wz))) break;
                int block = material(site, wx, y + dy, wz, Role.SUPPORT,
                        world.getBlock(wx, y - 1, wz));
                plan.putIfAbsent(new BlockPos(wx, y + dy, wz),
                        new Voxel(wx, y + dy, wz, block));
            }
        }

        // Erosion follows intact masonry instead of becoming an independent random cloud.
        List<Voxel> stone = new ArrayList<>(plan.values());
        for (int i = 0; i < stone.size(); i += 7 + ((site.agingLane() >>> 24) & 3)) {
            Voxel base = stone.get(i);
            int x = base.x + (((site.voxelLane(base.x, base.y, base.z, AGING_PURPOSE) >>> 4) & 1) == 0 ? -1 : 1);
            int z = base.z;
            int y = floors.floorY(x, z);
            if (y >= Blocks.MIN_Y && isWater(world.getBlock(x, y, z))) {
                plan.putIfAbsent(new BlockPos(x, y, z), new Voxel(x, y, z, Blocks.SEAGRASS));
            }
        }
        addFloodedLoot(site, world, floors, plan);
        if (plan.size() < 32) return List.of();
        return canonical(plan);
    }

    private List<Cell> smallFootprint(StructureSiteDescriptor site) {
        int span = span(site, 5, 8, 12), half = span / 2;
        int family = (site.shapeLane() >>> 2) & 3;
        List<Cell> cells = new ArrayList<>();
        if (family == 0) {
            ruinedRoom(cells, 0, 0, half, Math.max(2, half - 1),
                    5, site.direction(), site, true);
        } else if (family == 1) {
            brokenWall(cells, -half, 0, half, 0, 5, Role.WALL, site, true);
            brokenWall(cells, -half, 1, -half, half, 4, Role.WALL, site, true);
            floorPatch(cells, 0, Math.max(1, half / 2), half - 1,
                    Math.max(1, half / 2), 176, site);
        } else if (family == 2) {
            int court = Math.max(2, Math.min(half, 3));
            ruinedRoom(cells, 0, 0, court, court, 5,
                    site.direction(), site, true);
            for (int x : new int[] {-court, court}) {
                for (int z : new int[] {-court, court}) {
                    add(cells, x, z, 5 + ((x == z ? site.topologyLane()
                            : site.agingLane()) & 1), Role.SUPPORT, site, true);
                }
            }
            brokenWall(cells, 0, -half, 0, -court, 1, Role.FLOOR, site, true);
        } else {
            int depth = Math.max(2, half - 2);
            ruinedRoom(cells, 0, 0, half, depth, 5,
                    site.direction(), site, true);
            add(cells, 0, 0, 4, Role.SUPPORT, site, true);
        }
        return cells;
    }

    private List<Cell> mediumFootprint(StructureSiteDescriptor site) {
        int span = span(site, 12, 18, 26), half = span / 2;
        int depth = 3 + ((site.topologyLane() >>> 3) & 3);
        List<Cell> cells = new ArrayList<>();
        int primaryHalf = Math.max(4, half - 2);
        ruinedRoom(cells, 0, 0, primaryHalf, depth, 6 + site.sizeClass(),
                site.direction(), site, true);
        int wing = 3 + site.sizeClass() * 2;
        int side = ((site.topologyLane() >>> 26) & 1) == 0 ? 1 : -1;
        int annexCenter = side * (primaryHalf + wing / 2);
        ruinedRoom(cells, annexCenter, depth - 1, Math.max(2, wing / 2),
                Math.max(2, depth - 1), 5 + site.sizeClass(),
                side > 0 ? 3 : 1, site, true);
        int pillarZ = Math.max(2, depth - 1);
        add(cells, -primaryHalf + 2, pillarZ, 5, Role.SUPPORT, site, true);
        add(cells, primaryHalf - 2, pillarZ, 4 + site.sizeClass(),
                Role.SUPPORT, site, true);
        return cells;
    }

    private List<Cell> tombFootprint(StructureSiteDescriptor site) {
        int span = span(site, 7, 10, 16), half = span / 2;
        List<Cell> cells = new ArrayList<>();
        int depth = Math.max(2, half - 2);
        ruinedRoom(cells, 0, 0, half, depth, 5 + site.sizeClass(),
                site.direction(), site, true);
        int length = 2 + site.sizeClass();
        for (int offset = -length / 2; offset <= length / 2; offset++) {
            add(cells, offset, 0, 1, Role.SARCOPHAGUS, site, true);
        }
        for (int x : new int[] {-half + 1, half - 1}) {
            for (int z : new int[] {-depth + 1, depth - 1}) {
                add(cells, x, z, 4 + ((x + z) & 1), Role.SUPPORT, site, true);
            }
        }
        return cells;
    }

    private List<Cell> generalFootprint(StructureSiteDescriptor site) {
        int span = span(site, 8, 14, 24), half = span / 2;
        int family = (site.shapeLane() >>> 2) & 3;
        List<Cell> cells = new ArrayList<>();
        if (family == 0) {
            int roomHalf = Math.max(3, half - 2);
            ruinedRoom(cells, 0, 0, roomHalf, Math.max(3, half / 2),
                    6 + site.sizeClass(), site.direction(), site, true);
            int side = (site.topologyLane() & 1) == 0 ? 1 : -1;
            ruinedRoom(cells, side * (roomHalf + 2), 1, 2, 3, 5,
                    side > 0 ? 3 : 1, site, true);
        } else if (family == 1) {
            int tower = Math.max(3, half / 2);
            ruinedRoom(cells, 0, 0, tower, tower, 7 + site.sizeClass(),
                    site.direction(), site, false);
            brokenWall(cells, -tower, tower + 1, -half, half, 2,
                    Role.RUBBLE, site, false);
            brokenWall(cells, tower, tower + 1, half, half, 1,
                    Role.RUBBLE, site, false);
        } else if (family == 2) {
            int depth = 3 + site.sizeClass();
            ruinedRoom(cells, 0, 0, half, depth, 5 + site.sizeClass(),
                    site.direction(), site, true);
            for (int x = -half + 2; x <= half - 2; x += 4) {
                add(cells, x, -depth + 1, 5, Role.TIMBER, site, true);
                add(cells, x, depth - 1, 4 + ((x + half) & 1),
                        Role.TIMBER, site, true);
            }
        } else {
            int court = Math.max(3, half - 1);
            ruinedRoom(cells, 0, 0, court, court, 5 + site.sizeClass(),
                    site.direction(), site, true);
            for (int x : new int[] {-court, court}) {
                for (int z : new int[] {-court, court}) {
                    add(cells, x, z, 5 + Math.floorMod(
                            site.voxelLane(x, 0, z, AGING_PURPOSE), 3),
                            Role.SUPPORT, site, true);
                }
            }
            add(cells, 0, 0, 5 + site.sizeClass(), Role.SUPPORT, site, true);
        }
        return cells;
    }

    private void ruinedRoom(List<Cell> out, int centerX, int centerZ,
            int halfX, int halfZ, int wallHeight, int doorSide,
            StructureSiteDescriptor site, boolean preserveMore) {
        for (int x = -halfX; x <= halfX; x++) {
            if (!(doorSide == 0 && Math.abs(x) <= 1)) {
                add(out, centerX + x, centerZ - halfZ,
                        variedWallHeight(site, centerX + x, centerZ - halfZ,
                                wallHeight, Math.abs(x) == halfX),
                        Role.WALL, site, preserveMore);
            }
            if (!(doorSide == 2 && Math.abs(x) <= 1)) {
                add(out, centerX + x, centerZ + halfZ,
                        variedWallHeight(site, centerX + x, centerZ + halfZ,
                                wallHeight, Math.abs(x) == halfX),
                        Role.WALL, site, preserveMore);
            }
        }
        for (int z = -halfZ + 1; z < halfZ; z++) {
            if (!(doorSide == 3 && Math.abs(z) <= 1)) {
                add(out, centerX - halfX, centerZ + z,
                        variedWallHeight(site, centerX - halfX, centerZ + z,
                                wallHeight, false),
                        Role.WALL, site, preserveMore);
            }
            if (!(doorSide == 1 && Math.abs(z) <= 1)) {
                add(out, centerX + halfX, centerZ + z,
                        variedWallHeight(site, centerX + halfX, centerZ + z,
                                wallHeight, false),
                        Role.WALL, site, preserveMore);
            }
        }
        floorPatch(out, centerX, centerZ, Math.max(1, halfX - 1),
                Math.max(1, halfZ - 1), 198, site);
        roomClearance(out, centerX, centerZ, halfX, halfZ,
                Math.max(4, wallHeight), doorSide);
        entranceArch(out, centerX, centerZ, halfX, halfZ,
                Math.max(5, wallHeight), doorSide);
    }

    /**
     * Natural vegetation is applied before deferred structures. Explicit AIR cells make the
     * intended room and its threshold win over trees, leaves, and plants without touching the
     * frozen terrain generator or player edits.
     */
    private void roomClearance(List<Cell> out, int centerX, int centerZ,
            int halfX, int halfZ, int height, int doorSide) {
        for (int z = -halfZ + 1; z < halfZ; z++) {
            for (int x = -halfX + 1; x < halfX; x++) {
                out.add(new Cell(centerX + x, centerZ + z,
                        1, height, Role.CLEAR, false));
            }
        }
        for (int forward = 0; forward <= 1; forward++) {
            for (int cross = -1; cross <= 1; cross++) {
                int x = centerX + (doorSide == 0 || doorSide == 2
                        ? cross : doorSide == 1 ? halfX + forward : -halfX - forward);
                int z = centerZ + (doorSide == 1 || doorSide == 3
                        ? cross : doorSide == 2 ? halfZ + forward : -halfZ - forward);
                out.add(new Cell(x, z, 1, height, Role.CLEAR, false));
            }
        }
    }

    private void entranceArch(List<Cell> out, int centerX, int centerZ,
            int halfX, int halfZ, int y, int doorSide) {
        for (int cross = -1; cross <= 1; cross++) {
            int x = centerX + (doorSide == 0 || doorSide == 2
                    ? cross : doorSide == 1 ? halfX : -halfX);
            int z = centerZ + (doorSide == 1 || doorSide == 3
                    ? cross : doorSide == 2 ? halfZ : -halfZ);
            out.add(new Cell(x, z, y, y, Role.WALL, true));
        }
    }

    private int variedWallHeight(StructureSiteDescriptor site, int x, int z,
            int target, boolean corner) {
        int lane = site.voxelLane(site.anchorX() + x, target,
                site.anchorZ() + z, AGING_PURPOSE);
        int loss = (lane & 7) == 0 ? 2 : (lane & 3) == 0 ? 1 : 0;
        return Math.max(4, target + (corner ? 1 : 0) - loss);
    }

    private void floorPatch(List<Cell> out, int centerX, int centerZ,
            int halfX, int halfZ, int retention,
            StructureSiteDescriptor site) {
        for (int z = -halfZ; z <= halfZ; z++) {
            for (int x = -halfX; x <= halfX; x++) {
                int worldX = site.anchorX() + centerX + x;
                int worldZ = site.anchorZ() + centerZ + z;
                if ((site.voxelLane(worldX, 0, worldZ, MATERIAL_PURPOSE) & 255) > retention) {
                    continue;
                }
                add(out, centerX + x, centerZ + z, 1,
                        Role.FLOOR, site, true);
            }
        }
    }

    private void brokenWall(List<Cell> out, int x0, int z0, int x1, int z1,
            int height, Role role, StructureSiteDescriptor site, boolean preserveMore) {
        int length = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
        for (int i = 0; i <= length; i++) {
            int x = x0 + (x1 - x0) * i / Math.max(1, length);
            int z = z0 + (z1 - z0) * i / Math.max(1, length);
            add(out, x, z, height + ((i == 0 || i == length) ? 1 : 0),
                    role, site, preserveMore);
        }
    }

    private void add(List<Cell> out, int x, int z, int height, Role role,
            StructureSiteDescriptor site, boolean preserveMore) {
        boolean required = (x == 0 && z == 0) || role == Role.SARCOPHAGUS;
        if (!required && collapsed(site, x, z, preserveMore)) return;
        out.add(new Cell(x, z, height, role, required));
    }

    private boolean collapsed(StructureSiteDescriptor site, int x, int z) {
        return collapsed(site, x, z, false);
    }

    private boolean collapsed(StructureSiteDescriptor site, int x, int z,
            boolean preserveMore) {
        int age = (site.agingLane() >>> 16) & 255;
        int threshold = 20 + age / 3;
        if (preserveMore) threshold /= 2;
        return (site.voxelLane(site.anchorX() + x, 0, site.anchorZ() + z, COLLAPSE_PURPOSE) & 255) < threshold;
    }

    private int material(StructureSiteDescriptor site, int x, int y, int z, Role role,
            int support) {
        if (role == Role.SARCOPHAGUS) return Blocks.COBBLE_SLAB;
        int palette = (site.shapeLane() >>> 15) & 3;
        int age = site.agingLane() & 255;
        int moisture = (site.agingLane() >>> 8) & 255;
        int patch = site.voxelLane(x >> 1, y, z >> 1, AGING_PURPOSE) & 255;
        boolean dry = support == Blocks.SAND || support == Blocks.RED_SAND
                || support == Blocks.SANDSTONE || support == Blocks.RED_SANDSTONE;
        if (role == Role.FLOOR) {
            if (dry) return patch < 64 ? Blocks.SANDSTONE_SLAB : Blocks.SANDSTONE;
            return switch (palette) {
                case 0 -> patch < 96 ? Blocks.COBBLE_SLAB : Blocks.COBBLE;
                case 1 -> patch < 96 ? Blocks.STONE_BRICK_SLAB : Blocks.STONE_BRICK;
                case 2 -> patch < 96 ? Blocks.COBBLE_SLAB : Blocks.MOSSY_COBBLE;
                default -> patch < 80 ? Blocks.PLANK_SLAB : Blocks.PLANK;
            };
        }
        if (role == Role.TIMBER) {
            return (patch & 3) == 0 ? Blocks.LOG : Blocks.PLANK;
        }
        if (!dry && age + moisture > 250 && patch < (age + moisture) / 4) {
            return palette >= 2 ? Blocks.MOSSY_STONE_BRICK : Blocks.MOSSY_COBBLE;
        }
        int choice = site.voxelLane(x, y, z, MATERIAL_PURPOSE) & 3;
        if (role == Role.RUBBLE && choice == 0) return Blocks.GRAVEL;
        if (role == Role.RUBBLE && choice == 1) return Blocks.COBBLE_SLAB;
        if (role == Role.SUPPORT) {
            if (palette == 3 && choice == 0) return Blocks.LOG;
            if (choice == 1) return palette >= 2
                    ? Blocks.MOSSY_COBBLE_WALL : Blocks.STONE_BRICK_WALL;
        }
        return switch (palette) {
            case 0 -> choice == 0 ? Blocks.COBBLE : Blocks.STONE;
            case 1 -> choice <= 1 ? Blocks.STONE_BRICK : Blocks.COBBLE;
            case 2 -> choice == 0 ? Blocks.MOSSY_COBBLE : Blocks.COBBLE;
            default -> choice == 0 ? Blocks.PLANK : Blocks.STONE_BRICK;
        };
    }

    private void addSurfaceLoot(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world, Map<BlockPos, Voxel> plan,
            int offsetX, int offsetZ, int base) {
        if (!RuinLootProfile.hasLoot(site.seed(), site.anchorX(), site.anchorZ(), site.kind())) return;
        int[][] candidates = {{0, 1}, {1, 1}, {-1, 1}, {0, -1}, {2, 0}, {-2, 0}};
        for (int[] candidate : candidates) {
            int[] rotated = rotate(candidate[0], candidate[1], site.direction());
            int x = site.anchorX() + offsetX + rotated[0];
            int z = site.anchorZ() + offsetZ + rotated[1];
            int y = base + 2;
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos floor = new BlockPos(x, base + 1, z);
            if (plan.containsKey(floor) && world.getBlock(x, y, z) == Blocks.AIR
                    && (!plan.containsKey(pos) || plan.get(pos).blockType == Blocks.AIR)) {
                plan.put(pos, new Voxel(x, y, z, Blocks.CHEST));
            }
            // [CHEST-FAMILY] 형상군 술어를 쓰지 않는다 — 이 생성기가 바로 위에서 쓴
            // Blocks.CHEST 를 자기 계획에서 되읽는 **자기 일관성** 검사이고, 이 계획에는
            // 다른 갈래의 상자가 들어올 수 없다(구조물은 일반 상자만 놓는다).
            if (plan.containsKey(pos) && plan.get(pos).blockType == Blocks.CHEST) return;
        }
    }

    private void addFloodedLoot(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world, SubmergedFloorCache floors,
            Map<BlockPos, Voxel> plan) {
        if (!RuinLootProfile.hasLoot(site.seed(), site.anchorX(), site.anchorZ(), site.kind())) return;
        int[][] candidates = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] candidate : candidates) {
            int[] rotated = rotate(candidate[0], candidate[1], site.direction());
            int x = site.anchorX() + rotated[0], z = site.anchorZ() + rotated[1];
            int y = floors.floorY(x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (y >= Blocks.MIN_Y && isWater(world.getBlock(x, y, z)) && !plan.containsKey(pos)) {
                plan.put(pos, new Voxel(x, y, z, Blocks.CHEST));
            }
            // [CHEST-FAMILY] 형상군 술어를 쓰지 않는다 — 이 생성기가 바로 위에서 쓴
            // Blocks.CHEST 를 자기 계획에서 되읽는 **자기 일관성** 검사이고, 이 계획에는
            // 다른 갈래의 상자가 들어올 수 없다(구조물은 일반 상자만 놓는다).
            if (plan.containsKey(pos) && plan.get(pos).blockType == Blocks.CHEST) return;
        }
    }

    private int span(StructureSiteDescriptor site, int small, int medium, int large) {
        int[] centers = {small, medium, large};
        int jitter = (site.shapeLane() >>> 5) & 3;
        return Math.max(3, centers[site.sizeClass()] + jitter);
    }

    private int surfaceGroundY(int x, int z, SurfaceDecorator.BlockView world) {
        for (int y = Blocks.MAX_Y - 1; y >= Blocks.MIN_Y; y--) {
            int block = world.getBlock(x, y, z);
            if (block == Blocks.AIR || isPlant(block) || isTree(block)) continue;
            if (isWater(block)) return Blocks.MIN_Y - 1;
            return isSurfaceSupport(block) ? y : Blocks.MIN_Y - 1;
        }
        return Blocks.MIN_Y - 1;
    }

    private int submergedFloorY(int x, int z, SurfaceDecorator.BlockView world) {
        for (int y = Blocks.SEA_LEVEL; y >= Blocks.MIN_Y + 1; y--) {
            if (isWater(world.getBlock(x, y, z)) && isSolidSupport(world.getBlock(x, y - 1, z))) return y;
        }
        return Blocks.MIN_Y - 1;
    }

    private final class SurfaceHeightCache {
        private final SurfaceDecorator.BlockView world;
        private final Map<Long, Integer> values = new HashMap<>();

        private SurfaceHeightCache(SurfaceDecorator.BlockView world) {
            this.world = world;
        }

        private int groundY(int x, int z) {
            long key = columnKey(x, z);
            Integer cached = values.get(key);
            if (cached != null) return cached;
            int ground = surfaceGroundY(x, z, world);
            values.put(key, ground);
            return ground;
        }
    }

    private final class SubmergedFloorCache {
        private final SurfaceDecorator.BlockView world;
        private final Map<Long, Integer> values = new HashMap<>();

        private SubmergedFloorCache(SurfaceDecorator.BlockView world) {
            this.world = world;
        }

        private int floorY(int x, int z) {
            long key = columnKey(x, z);
            Integer cached = values.get(key);
            if (cached != null) return cached;
            int floor = submergedFloorY(x, z, world);
            values.put(key, floor);
            return floor;
        }
    }

    private long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private boolean isSurfaceSupport(int block) {
        return block == Blocks.GRASS || block == Blocks.DIRT || block == Blocks.SAND
                || block == Blocks.RED_SAND || block == Blocks.COARSE_DIRT
                || block == Blocks.PODZOL || block == Blocks.STONE
                || block == Blocks.COBBLE || block == Blocks.GRAVEL
                || block == Blocks.MOSS_BLOCK || block == Blocks.MOSSY_COBBLE
                || block == Blocks.STONE_BRICK || block == Blocks.MOSSY_STONE_BRICK
                || block == Blocks.SANDSTONE || block == Blocks.RED_SANDSTONE
                || block == Blocks.DEEPSLATE || block == Blocks.COBBLED_DEEPSLATE
                || block == Blocks.SNOW_BLOCK
                || block == Blocks.ICE
                || (block >= Blocks.TERRACOTTA && block <= Blocks.LIGHT_GRAY_TERRACOTTA);
    }

    private boolean isSolidSupport(int block) {
        return StructureTerrainRules.isStableGround(block);
    }

    private boolean isPlant(int block) {
        return StructureTerrainRules.isVegetation(block)
                && !StructureTerrainRules.isTreeBlock(block);
    }

    private boolean isTree(int block) {
        return StructureTerrainRules.isTreeBlock(block);
    }

    private boolean isWater(int block) {
        return block >= Blocks.WATER_SOURCE && block < Blocks.LAVA_SOURCE;
    }

    private boolean identityAnchor(int x, int z) {
        return (x == 0 && z == 0) || ((x + z) & 7) == 0;
    }

    private int[] rotate(int x, int z, int direction) {
        return switch (direction) {
            case 0 -> new int[] {x, z};
            case 1 -> new int[] {-z, x};
            case 2 -> new int[] {-x, -z};
            default -> new int[] {z, -x};
        };
    }

    private List<Voxel> canonical(Map<BlockPos, Voxel> plan) {
        List<Voxel> out = new ArrayList<>(plan.values());
        out.sort(Comparator.comparingInt(Voxel::x)
                .thenComparingInt(Voxel::y).thenComparingInt(Voxel::z)
                .thenComparingInt(Voxel::blockType));
        return out;
    }

    private enum Role { WALL, FLOOR, SUPPORT, TIMBER, RUBBLE, SARCOPHAGUS, CLEAR }

    private static final class Cell {
        private final int x;
        private final int z;
        private final int start;
        private final int height;
        private final Role role;
        private final boolean required;

        private Cell(int x, int z, int height, Role role, boolean required) {
            this(x, z, 1, height, role, required);
        }

        private Cell(int x, int z, int start, int height, Role role, boolean required) {
            this.x = x;
            this.z = z;
            this.start = start;
            this.height = height;
            this.role = role;
            this.required = required;
        }
    }
}
