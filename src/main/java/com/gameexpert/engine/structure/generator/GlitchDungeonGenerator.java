package com.gameexpert.engine.structure.generator;

import java.util.ArrayList;
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
 * Stateless runtime corruption. This deliberately has no architecture: a site is a noisy,
 * discontinuous cloud of bad material, hovering junk, and terrain that has been eaten away.
 */
public final class GlitchDungeonGenerator implements StructureOverlayGenerator {
    private static final int SCALE_PURPOSE = 0x6a17;
    private static final int FOOTPRINT_PURPOSE = 0x6a18;
    private static final int DENSITY_PURPOSE = 0x6a19;
    private static final int RAGGED_PURPOSE = 0x6a1a;
    private static final int MATERIAL_PURPOSE = 0x6a1b;
    private static final int DIMENSION_SCAR_PURPOSE = 0x6a1c;
    private static final int DIMENSION_SCAR_SITE_DENOMINATOR = 8;

    private static final int[] FILTH = {
            Blocks.DIRT, Blocks.GRAVEL, Blocks.CLAY, Blocks.MUD, Blocks.PODZOL,
            Blocks.MYCELIUM, Blocks.ROOTED_DIRT, Blocks.COBBLE, Blocks.MOSS_BLOCK,
            Blocks.MOSSY_COBBLE, Blocks.MOSSY_STONE_BRICK, Blocks.STONE_BRICK,
            Blocks.OBSIDIAN, Blocks.COAL_BLOCK, Blocks.BONE_BLOCK, Blocks.MANGROVE_ROOTS
    };
    private static final int[] DEBRIS = {
            Blocks.COBWEB, Blocks.BUSH, Blocks.MUSHROOM_BROWN, Blocks.MUSHROOM_RED,
            Blocks.TORCH, Blocks.LADDER, Blocks.VINE, Blocks.HANGING_ROOTS,
            Blocks.MOSS_CARPET, Blocks.SMALL_DRIPLEAF
    };

    @Override
    public List<RuinGenerator.Voxel> plan(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world) {
        int scaleLane = site.partLane0(0, SCALE_PURPOSE);
        int scaleRoll = Math.floorMod(scaleLane, 100);
        int floor = findSolidFloor(site.anchorX(), site.anchorZ(), world);
        if (floor < Blocks.MIN_Y + 8) return List.of();

        int halfExtent;
        int below;
        int above;
        if (scaleRoll < 40) { // 40%: a small, dense infection.
            halfExtent = 10 + Math.floorMod(site.partLane1(0, SCALE_PURPOSE), 6);
            below = 7 + Math.floorMod(site.partLane0(1, SCALE_PURPOSE), 4);
            above = 8 + Math.floorMod(site.partLane1(1, SCALE_PURPOSE), 5);
        } else if (scaleRoll < 80) { // 40%: a broader, uneven cloud.
            halfExtent = 18 + Math.floorMod(site.partLane1(0, SCALE_PURPOSE), 7);
            below = 8 + Math.floorMod(site.partLane0(1, SCALE_PURPOSE), 5);
            above = 11 + Math.floorMod(site.partLane1(1, SCALE_PURPOSE), 6);
        } else { // 20%: sprawling, but still bounded enough for a runtime overlay.
            halfExtent = 27 + Math.floorMod(site.partLane1(0, SCALE_PURPOSE), 9);
            below = 10 + Math.floorMod(site.partLane0(1, SCALE_PURPOSE), 5);
            above = 15 + Math.floorMod(site.partLane1(1, SCALE_PURPOSE), 8);
        }

        Plan plan = new Plan(site, world, floor);
        for (int dz = -halfExtent; dz <= halfExtent; dz++) {
            for (int dx = -halfExtent; dx <= halfExtent; dx++) {
                int edgeNoise = field(plan, dx, dz, FOOTPRINT_PURPOSE, 2);
                int horizontal = Math.max(Math.abs(dx), Math.abs(dz)) * 100 / halfExtent;
                // The edge is a shredded, noisy dropout rather than a readable perimeter.
                if (horizontal > 58 + edgeNoise / 24) continue;

                for (int y = floor - below; y <= floor + above; y++) {
                    int vertical = y >= floor ? (y - floor) * 100 / above
                            : (floor - y) * 100 / below;
                    if (Math.max(horizontal, vertical) > 58 + edgeNoise / 24) continue;

                    int clot = field(plan, dx, dz, DENSITY_PURPOSE, 2);
                    if (clot < 450) continue;
                    int fillThreshold = 70 + (clot - 450) / 2;
                    int placement = Math.floorMod(plan.lane(dx, y, dz, DENSITY_PURPOSE + 1), 1000);
                    if (placement >= fillThreshold) continue;

                    int existing = plan.block(dx, y, dz);
                    if (plan.solidBlock(existing)) {
                        int bite = field(plan, dx, dz, RAGGED_PURPOSE, 1);
                        int holeRoll = Math.floorMod(plan.lane(dx, y, dz, RAGGED_PURPOSE + 1), 100);
                        if (y <= floor + 3 && y >= floor - below && bite > 650 && holeRoll < 36) {
                            plan.carve(dx, y, dz);
                        } else {
                            plan.replace(dx, y, dz, filthyBlock(plan, dx, y, dz));
                        }
                    } else if (existing == Blocks.AIR) {
                        plan.set(dx, y, dz, airborneBlock(plan, dx, y, dz));
                    }
                }
            }
        }
        addDimensionScarVein(plan, halfExtent, below);
        return plan.voxels();
    }

    /**
     * Rare Overworld divergence: a glitch site can carry one short, hash-walked Nether-gold scar.
     * The site gate is independent of structure admission and every step/branch comes from the
     * existing site hash, so this adds no fixed template and cannot perturb the base corruption RNG.
     */
    private void addDimensionScarVein(Plan plan, int halfExtent, int below) {
        if (Math.floorMod(plan.site.partLane0(0, DIMENSION_SCAR_PURPOSE),
                DIMENSION_SCAR_SITE_DENOMINATOR) != 0) return;
        int span = Math.max(1, halfExtent * 2 - 7);
        int dx = Math.floorMod(plan.site.partLane1(0, DIMENSION_SCAR_PURPOSE), span)
                - halfExtent + 4;
        int dz = Math.floorMod(plan.site.partLane0(1, DIMENSION_SCAR_PURPOSE), span)
                - halfExtent + 4;
        int y = plan.floor - 2
                - Math.floorMod(plan.site.partLane1(1, DIMENSION_SCAR_PURPOSE), Math.max(1, below - 3));
        int steps = 4 + Math.floorMod(plan.site.partLane0(2, DIMENSION_SCAR_PURPOSE), 5);
        for (int step = 0; step < steps; step++) {
            plan.replace(dx, y, dz, Blocks.NETHER_GOLD_ORE);
            int blob = Math.floorMod(plan.lane(dx, y, dz, DIMENSION_SCAR_PURPOSE + 1), 4);
            if (blob == 0) plan.replace(dx + 1, y, dz, Blocks.NETHER_GOLD_ORE);
            if (blob == 1) plan.replace(dx, y + 1, dz, Blocks.NETHER_GOLD_ORE);
            if (blob == 2) plan.replace(dx, y, dz + 1, Blocks.NETHER_GOLD_ORE);
            int direction = Math.floorMod(
                    plan.lane(dx, y, dz, DIMENSION_SCAR_PURPOSE + 2 + step), 6);
            switch (direction) {
                case 0 -> dx++;
                case 1 -> dx--;
                case 2 -> dz++;
                case 3 -> dz--;
                case 4 -> y++;
                default -> y--;
            }
        }
    }

    /** A local average of absolute-coordinate voxel hashes creates clots without a lattice. */
    private int field(Plan plan, int dx, int dz, int purpose, int reach) {
        int total = 0;
        int samples = 0;
        for (int oz = -reach; oz <= reach; oz++) {
            for (int ox = -reach; ox <= reach; ox++) {
                total += Math.floorMod(plan.lane(dx + ox, plan.floor, dz + oz, purpose), 1000);
                samples++;
            }
        }
        return total / samples;
    }

    private int filthyBlock(Plan plan, int dx, int y, int dz) {
        int roll = Math.floorMod(plan.lane(dx, y, dz, MATERIAL_PURPOSE), 512);
        // A rare source of lava is enough to make the corruption hazardous without lighting it up.
        if (roll == 0) return Blocks.LAVA_SOURCE;
        return FILTH[Math.floorMod(plan.lane(dx, y, dz, MATERIAL_PURPOSE + 1), FILTH.length)];
    }

    private int airborneBlock(Plan plan, int dx, int y, int dz) {
        int roll = Math.floorMod(plan.lane(dx, y, dz, MATERIAL_PURPOSE + 2), 128);
        if (roll == 0) return Blocks.LAVA_SOURCE;
        if (roll < 45) {
            return DEBRIS[Math.floorMod(plan.lane(dx, y, dz, MATERIAL_PURPOSE + 3), DEBRIS.length)];
        }
        return FILTH[Math.floorMod(plan.lane(dx, y, dz, MATERIAL_PURPOSE + 4), FILTH.length)];
    }

    private int findSolidFloor(int x, int z, SurfaceDecorator.BlockView world) {
        for (int y = Blocks.SEA_LEVEL - 8; y >= Blocks.MIN_Y + 8; y--) {
            if (solid(world.getBlock(x, y, z))) return y;
        }
        return Blocks.MIN_Y - 1;
    }

    private boolean solid(int block) {
        return block != Blocks.BEDROCK && StructureTerrainRules.isStableGround(block);
    }

    private static final class Plan {
        private final StructureSiteDescriptor site;
        private final SurfaceDecorator.BlockView world;
        private final int floor;
        private final Map<BlockPos, RuinGenerator.Voxel> cells = new LinkedHashMap<>();

        private Plan(StructureSiteDescriptor site, SurfaceDecorator.BlockView world, int floor) {
            this.site = site;
            this.world = world;
            this.floor = floor;
        }

        private int lane(int dx, int y, int dz, int purpose) {
            return site.voxelLane(site.anchorX() + dx, y, site.anchorZ() + dz, purpose);
        }

        private void carve(int dx, int y, int dz) {
            int existing = block(dx, y, dz);
            if (existing != Blocks.BEDROCK && solidBlock(existing)) set(dx, y, dz, Blocks.AIR);
        }

        private void replace(int dx, int y, int dz, int block) {
            if (solidBlock(block(dx, y, dz))) set(dx, y, dz, block);
        }

        private int block(int dx, int y, int dz) {
            if (y < Blocks.MIN_Y || y >= Blocks.MAX_Y) return Blocks.BEDROCK;
            RuinGenerator.Voxel changed = cells.get(new BlockPos(site.anchorX() + dx, y, site.anchorZ() + dz));
            return changed == null ? world.getBlock(site.anchorX() + dx, y, site.anchorZ() + dz)
                    : changed.blockType();
        }

        private void set(int dx, int y, int dz, int block) {
            if (y < Blocks.MIN_Y || y >= Blocks.MAX_Y) return;
            int x = site.anchorX() + dx;
            int z = site.anchorZ() + dz;
            if (block(dx, y, dz) == block) return;
            cells.put(new BlockPos(x, y, z), RuinGenerator.Voxel.at(x, y, z, block));
        }

        private boolean solidBlock(int block) {
            return block != Blocks.BEDROCK && StructureTerrainRules.isStableGround(block);
        }

        private List<RuinGenerator.Voxel> voxels() {
            return cells.isEmpty() ? List.of() : List.copyOf(new ArrayList<>(cells.values()));
        }
    }
}
