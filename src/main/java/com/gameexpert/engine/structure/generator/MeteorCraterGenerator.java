package com.gameexpert.engine.structure.generator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
 * Additive impact site: an irregular low rim, radial ejecta and a fractured meteor core.
 * It suggests a depression through silhouette and material contrast without excavating terrain.
 */
public final class MeteorCraterGenerator implements StructureOverlayGenerator {
    private static final int RIM = 0x4d455452;
    private static final int EJECTA = 0x454a4354;

    @Override
    public List<RuinGenerator.Voxel> plan(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world) {
        Planner p = new Planner(site, world);
        int radiusX = 4 + site.sizeClass();
        int radiusZ = radiusX - ((site.shapeLane() >>> 5) & 1);
        int rimCells = 0;

        for (int z = -radiusX - 1; z <= radiusX + 1; z++) {
            for (int x = -radiusX - 1; x <= radiusX + 1; x++) {
                if (Math.abs(x) > StructureSiteDescriptor.Kind.METEOR_CRATER.maxReach()
                        || Math.abs(z) > StructureSiteDescriptor.Kind.METEOR_CRATER.maxReach()) continue;
                int metric = x * x * 1024 / (radiusX * radiusX)
                        + z * z * 1024 / (radiusZ * radiusZ);
                int lane = site.voxelLane(site.anchorX() + x, 0,
                        site.anchorZ() + z, RIM);
                int wobble = ((lane >>> 8) & 127) - 63;
                if (metric >= 790 + wobble && metric <= 1280 + wobble) {
                    int block = rimMaterial(lane);
                    if (p.addSurface(x, z, block)) {
                        rimCells++;
                        if (site.sizeClass() > 0 && (lane & 15) < 3) {
                            int ground = p.groundY(x, z);
                            p.put(x, ground + 2, z,
                                    (lane & 32) == 0 ? Blocks.GRAVEL : Blocks.OBSIDIAN);
                        }
                    }
                } else if (metric < 700 && (lane & 255) < 22 + site.sizeClass() * 10) {
                    p.addSurface(x, z, (lane & 4) == 0 ? Blocks.DEEPSLATE : Blocks.OBSIDIAN);
                }
            }
        }
        if (rimCells < radiusX * 4) return List.of();

        addEjecta(p, radiusX, site);
        addMeteorCore(p, site);
        return p.voxels();
    }

    private int rimMaterial(int lane) {
        return switch ((lane >>> 3) & 7) {
            case 0 -> Blocks.OBSIDIAN;
            case 1, 2 -> Blocks.DEEPSLATE;
            case 3 -> Blocks.COBBLE;
            default -> Blocks.GRAVEL;
        };
    }

    private void addEjecta(Planner p, int radius, StructureSiteDescriptor site) {
        int rays = 3 + site.sizeClass() + ((site.topologyLane() >>> 6) & 1);
        int start = Math.floorMod(site.partLane0(0, EJECTA), 8);
        int[][] directions = {
                {0, -1}, {1, -1}, {1, 0}, {1, 1},
                {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}
        };
        for (int ray = 0; ray < rays; ray++) {
            int[] direction = directions[(start + ray * 3) & 7];
            int length = Math.min(8, radius + 1 + Math.floorMod(
                    site.partLane1(ray, EJECTA), 3));
            for (int distance = radius; distance <= length; distance++) {
                int side = Math.floorMod(site.partLane0(ray * 17 + distance, EJECTA), 3) - 1;
                int x = direction[0] * distance
                        + (direction[0] == 0 ? side : 0);
                int z = direction[1] * distance
                        + (direction[1] == 0 ? side : 0);
                if (Math.abs(x) > 8 || Math.abs(z) > 8) continue;
                int lane = site.voxelLane(site.anchorX() + x, distance,
                        site.anchorZ() + z, EJECTA);
                p.addSurface(x, z, (lane & 7) == 0 ? Blocks.OBSIDIAN : Blocks.GRAVEL);
            }
        }
    }

    private void addMeteorCore(Planner p, StructureSiteDescriptor site) {
        int centerX = Math.floorMod(site.partLane0(11, RIM), 3) - 1;
        int centerZ = Math.floorMod(site.partLane1(11, RIM), 3) - 1;
        int base = p.groundY(centerX, centerZ);
        if (base < Blocks.MIN_Y) return;
        int height = 2 + site.sizeClass();
        int[][] baseShape = {
                {0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1},
                {-1, -1}, {1, 1}
        };
        for (int index = 0; index < baseShape.length; index++) {
            int lane = site.partLane0(index + 30, RIM);
            if (index > 4 && (lane & 1) == 0) continue;
            int x = centerX + baseShape[index][0];
            int z = centerZ + baseShape[index][1];
            int ground = p.groundY(x, z);
            if (ground < Blocks.MIN_Y || Math.abs(ground - base) > 1) continue;
            p.put(x, ground + 1, z, (lane & 3) == 0 ? Blocks.DEEPSLATE : Blocks.OBSIDIAN);
        }
        for (int dy = 1; dy <= height; dy++) {
            p.put(centerX, base + dy, centerZ,
                    dy == height ? Blocks.DEEPSLATE : Blocks.OBSIDIAN);
            if (dy < height && ((site.topologyLane() >>> dy) & 1) != 0) {
                p.put(centerX + (dy % 2 == 0 ? 1 : -1), base + dy, centerZ,
                        Blocks.DEEPSLATE);
            }
        }
    }

    private static final class Planner {
        private static final int REACH = StructureSiteDescriptor.Kind.METEOR_CRATER.maxReach();

        private final StructureSiteDescriptor site;
        private final SurfaceDecorator.BlockView world;
        private final int width = REACH * 2 + 1;
        private final int[] surface = new int[width * width];
        private final Map<BlockPos, RuinGenerator.Voxel> plan = new LinkedHashMap<>();

        private Planner(StructureSiteDescriptor site, SurfaceDecorator.BlockView world) {
            this.site = site;
            this.world = world;
            Arrays.fill(surface, Integer.MIN_VALUE);
        }

        private boolean addSurface(int localX, int localZ, int block) {
            int ground = groundY(localX, localZ);
            if (ground < Blocks.MIN_Y) return false;
            int before = plan.size();
            put(localX, ground + 1, localZ, block);
            return plan.size() != before;
        }

        private int groundY(int localX, int localZ) {
            if (Math.abs(localX) > REACH || Math.abs(localZ) > REACH) {
                return Blocks.MIN_Y - 1;
            }
            int index = (localZ + REACH) * width + localX + REACH;
            int cached = surface[index];
            if (cached != Integer.MIN_VALUE) return cached;
            int worldX = site.anchorX() + rotatedX(localX, localZ, site.direction());
            int worldZ = site.anchorZ() + rotatedZ(localX, localZ, site.direction());
            for (int y = Blocks.MAX_Y - 1; y >= Blocks.MIN_Y; y--) {
                int block = world.getBlock(worldX, y, worldZ);
                if (block == Blocks.AIR || vegetation(block)) continue;
                surface[index] = support(block) ? y : Blocks.MIN_Y - 1;
                return surface[index];
            }
            surface[index] = Blocks.MIN_Y - 1;
            return surface[index];
        }

        private void put(int localX, int y, int localZ, int block) {
            if (Math.abs(localX) > REACH || Math.abs(localZ) > REACH
                    || y < Blocks.MIN_Y || y > Blocks.MAX_Y || block == Blocks.AIR) return;
            int worldX = site.anchorX() + rotatedX(localX, localZ, site.direction());
            int worldZ = site.anchorZ() + rotatedZ(localX, localZ, site.direction());
            BlockPos pos = new BlockPos(worldX, y, worldZ);
            if (plan.containsKey(pos) || !StructureTerrainRules.isReplaceableByStructure(
                    world.getBlock(worldX, y, worldZ))) return;
            plan.put(pos, RuinGenerator.Voxel.at(worldX, y, worldZ, block));
        }

        private List<RuinGenerator.Voxel> voxels() {
            if (plan.size() < 32) return List.of();
            List<RuinGenerator.Voxel> out = new ArrayList<>(plan.values());
            out.sort(Comparator.comparingInt(RuinGenerator.Voxel::x)
                    .thenComparingInt(RuinGenerator.Voxel::y)
                    .thenComparingInt(RuinGenerator.Voxel::z)
                    .thenComparingInt(RuinGenerator.Voxel::blockType));
            return List.copyOf(out);
        }
    }

    private static boolean support(int block) {
        return StructureTerrainRules.isStableGround(block);
    }

    private static boolean vegetation(int block) {
        return StructureTerrainRules.isVegetation(block);
    }

    private static int rotatedX(int x, int z, int direction) {
        return switch (direction) {
            case 1 -> -z;
            case 2 -> -x;
            case 3 -> z;
            default -> x;
        };
    }

    private static int rotatedZ(int x, int z, int direction) {
        return switch (direction) {
            case 1 -> x;
            case 2 -> -z;
            case 3 -> -x;
            default -> z;
        };
    }
}
