package com.gameexpert.engine.structure.generator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
 * Additive surface altar with a stepped dais, approach and broken landmark pillars.
 * Scale, facing, palette, wear and pillar heights are derived from the site descriptor.
 */
public final class AltarGenerator implements StructureOverlayGenerator {
    private static final int WEAR = 0x414c5452;

    @Override
    public List<RuinGenerator.Voxel> plan(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world) {
        Planner p = new Planner(site, world);
        int radius = site.sizeClass() == 0 ? 3 : 4;
        int base = p.levelBase(radius, 6);
        if (base < Blocks.MIN_Y) return List.of();

        int[] palette = palette(p.centerSupport(), site.shapeLane());
        int approach = site.direction();
        int daisRadius = 2 + (site.sizeClass() == 2 ? 1 : 0);
        for (int z = -daisRadius; z <= daisRadius; z++) {
            for (int x = -daisRadius; x <= daisRadius; x++) {
                if (Math.abs(x) + Math.abs(z) > daisRadius * 2 - 1) continue;
                int wear = site.voxelLane(site.anchorX() + x, base,
                        site.anchorZ() + z, WEAR) & 255;
                if (wear < 18 + ((site.agingLane() >>> 12) & 31)) continue;
                p.foundation(x, z, base, ((x + z) & 3) == 0 ? palette[1] : palette[0]);
            }
        }

        for (int z = -1; z <= 1; z++) {
            for (int x = -1; x <= 1; x++) {
                p.put(x, base + 2, z, palette[(Math.abs(x) + Math.abs(z)) & 1]);
            }
        }

        int[][] corners = {{-radius, -radius}, {radius, -radius},
                {radius, radius}, {-radius, radius}};
        for (int index = 0; index < corners.length; index++) {
            int[] corner = corners[index];
            // Fill only below the pillar; foundation() also writes its first visible block.
            for (int y = p.groundY(corner[0], corner[1]) + 1; y <= base; y++) {
                p.put(corner[0], y, corner[1], Blocks.COBBLE);
            }
            int height = 2 + Math.floorMod(site.partLane0(index, WEAR), 3)
                    + (site.sizeClass() == 2 ? 1 : 0);
            if ((site.agingLane() & (1 << (index + 4))) != 0) height--;
            for (int dy = 1; dy <= height; dy++) {
                p.put(corner[0], base + dy, corner[1],
                        dy == height ? palette[1] : palette[0]);
            }
        }

        addBrokenRing(p, radius, base, approach, palette, site);
        addApproach(p, radius, base, approach, palette);
        addFocalMonument(p, base, palette, site);
        return p.voxels();
    }

    private void addBrokenRing(Planner p, int radius, int base, int approach,
            int[] palette, StructureSiteDescriptor site) {
        for (int offset = -radius + 1; offset < radius; offset++) {
            for (int side = 0; side < 4; side++) {
                if (side == approach && Math.abs(offset) <= 1) continue;
                int x = side == 1 ? radius : side == 3 ? -radius : offset;
                int z = side == 0 ? -radius : side == 2 ? radius : offset;
                int wear = site.voxelLane(site.anchorX() + x, base,
                        site.anchorZ() + z, WEAR ^ 0x52) & 255;
                if (wear < 58) continue;
                p.foundation(x, z, base,
                        wear < 108 ? Blocks.COBBLE_SLAB : palette[wear & 1]);
            }
        }
    }

    private void addApproach(Planner p, int radius, int base, int direction, int[] palette) {
        for (int distance = radius; distance >= 2; distance--) {
            int x = direction == 1 ? distance : direction == 3 ? -distance : 0;
            int z = direction == 0 ? -distance : direction == 2 ? distance : 0;
            p.foundation(x, z, base, distance == radius ? Blocks.COBBLE_SLAB : palette[0]);
            if (distance < radius) {
                int sideX = direction == 0 || direction == 2 ? 1 : 0;
                int sideZ = direction == 1 || direction == 3 ? 1 : 0;
                p.foundation(x + sideX, z + sideZ, base, palette[1]);
                p.foundation(x - sideX, z - sideZ, base, palette[1]);
            }
        }
    }

    private void addFocalMonument(Planner p, int base, int[] palette,
            StructureSiteDescriptor site) {
        int style = (site.shapeLane() >>> 20) & 3;
        if (style == 0) {
            p.put(0, base + 3, 0, Blocks.OBSIDIAN);
            p.put(0, base + 4, 0, Blocks.OBSIDIAN);
            p.put(0, base + 5, 0, Blocks.TORCH);
        } else if (style == 1) {
            p.put(0, base + 3, 0, palette[0]);
            p.put(0, base + 4, 0, Blocks.STONE_BRICK_WALL);
            p.put(0, base + 5, 0, Blocks.CAMPFIRE,
                    BuildingBlockRules.CAMPFIRE_LIT);
        } else {
            p.put(0, base + 3, 0, palette[0]);
            p.put(0, base + 4, 0, palette[1]);
            p.put(0, base + 5, 0, style == 2 ? Blocks.TORCH : Blocks.MOSSY_COBBLE_WALL);
        }
    }

    private int[] palette(int support, int lane) {
        if (support == Blocks.SAND || support == Blocks.SANDSTONE) {
            return new int[] {Blocks.SANDSTONE, Blocks.CHISELED_SANDSTONE};
        }
        return (lane & 1) == 0
                ? new int[] {Blocks.STONE_BRICK, Blocks.MOSSY_STONE_BRICK}
                : new int[] {Blocks.COBBLE, Blocks.MOSSY_COBBLE};
    }

    private static final class Planner {
        private static final int REACH = StructureSiteDescriptor.Kind.ALTAR.maxReach();

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

        private int levelBase(int radius, int headroom) {
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (int z = -radius; z <= radius; z++) {
                for (int x = -radius; x <= radius; x++) {
                    int ground = groundY(x, z);
                    if (ground < Blocks.MIN_Y || !support(block(x, ground, z))) {
                        return Blocks.MIN_Y - 1;
                    }
                    min = Math.min(min, ground);
                    max = Math.max(max, ground);
                }
            }
            if (max - min > 1 || max + headroom > Blocks.MAX_Y) return Blocks.MIN_Y - 1;
            for (int z = -radius; z <= radius; z++) {
                for (int x = -radius; x <= radius; x++) {
                    int ground = groundY(x, z);
                    for (int y = ground + 1; y <= max + headroom; y++) {
                        if (!StructureTerrainRules.isReplaceableByStructure(
                                block(x, y, z))) return Blocks.MIN_Y - 1;
                    }
                }
            }
            return max;
        }

        private int centerSupport() {
            int ground = groundY(0, 0);
            return ground < Blocks.MIN_Y ? Blocks.AIR : block(0, ground, 0);
        }

        private void foundation(int x, int z, int base, int top) {
            int ground = groundY(x, z);
            if (ground < Blocks.MIN_Y || ground > base) return;
            for (int y = ground + 1; y <= base; y++) put(x, y, z, Blocks.COBBLE);
            put(x, base + 1, z, top);
        }

        private void put(int localX, int y, int localZ, int block) {
            put(localX, y, localZ, block, 0);
        }

        private void put(int localX, int y, int localZ, int block, int state) {
            if (Math.abs(localX) > REACH || Math.abs(localZ) > REACH
                    || y < Blocks.MIN_Y || y > Blocks.MAX_Y || block == Blocks.AIR) return;
            int worldX = site.anchorX() + rotatedX(localX, localZ, site.direction());
            int worldZ = site.anchorZ() + rotatedZ(localX, localZ, site.direction());
            BlockPos pos = new BlockPos(worldX, y, worldZ);
            if (plan.containsKey(pos) || !StructureTerrainRules.isReplaceableByStructure(
                    world.getBlock(worldX, y, worldZ))) return;
            plan.put(pos, RuinGenerator.Voxel.at(worldX, y, worldZ, block, state));
        }

        private int groundY(int localX, int localZ) {
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

        private int block(int localX, int y, int localZ) {
            return world.getBlock(
                    site.anchorX() + rotatedX(localX, localZ, site.direction()), y,
                    site.anchorZ() + rotatedZ(localX, localZ, site.direction()));
        }

        private List<RuinGenerator.Voxel> voxels() {
            if (plan.size() < 24) return List.of();
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
