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
 * A deterministic, air-only underground vault.  Every choice is derived from the
 * site descriptor; the generator keeps no state and never excavates the host world.
 */
public final class SealedChamberGenerator implements StructureOverlayGenerator {
    private static final int PURPOSE = 0x73;
    private static final int DETAIL = 0x74;
    private static final int WEAR = 0x75;

    @Override
    public List<RuinGenerator.Voxel> plan(StructureSiteDescriptor site,
            SurfaceDecorator.BlockView world) {
        int halfX = 3 + unsigned(site.shapeLane() >>> 3, 5);
        int halfZ = 3 + unsigned(site.topologyLane() >>> 7, 5);
        int height = 5 + unsigned(site.adaptationLane() >>> 4, 5);
        int floor = findFloor(site.anchorX(), site.anchorZ(), halfX, halfZ, height, world);
        if (floor < Blocks.MIN_Y || floor + height + 1 >= Blocks.MAX_Y) return List.of();

        Planner p = new Planner(site, world, floor, halfX, halfZ, height);
        if (!p.reserveFootprint()) return List.of();
        p.excavate();
        p.vault();
        p.pillars();
        p.reliefs();
        p.trapMotif();
        p.altarAndTreasure();
        p.alcoves();
        p.torchesAndWear();
        return p.valid ? List.copyOf(p.voxels.values()) : List.of();
    }

    private int findFloor(int x, int z, int hx, int hz, int height,
            SurfaceDecorator.BlockView world) {
        int top = Math.min(Blocks.SEA_LEVEL - 12, Blocks.MAX_Y - height - 2);
        for (int y = top; y >= Blocks.MIN_Y; y--) {
            boolean valid = true;
            for (int dz = -hz; dz <= hz && valid; dz++) {
                for (int dx = -hx; dx <= hx; dx++) {
                    if (!solid(world.getBlock(x + dx, y, z + dz))) { valid = false; break; }
                    for (int dy = 1; dy <= height + 1; dy++) {
                        if (!solid(world.getBlock(x + dx, y + dy, z + dz))) {
                            valid = false; break;
                        }
                    }
                    if (!valid) break;
                }
            }
            if (valid) return y;
        }
        return Blocks.MIN_Y - 1;
    }

    private static int unsigned(int value, int bound) {
        return (value & 0x7fffffff) % bound;
    }

    private static boolean solid(int block) {
        return StructureTerrainRules.isStableGround(block);
    }

    private static final class Planner {
        private final StructureSiteDescriptor site;
        private final SurfaceDecorator.BlockView world;
        private final int floor, hx, hz, height;
        private final Map<BlockPos, RuinGenerator.Voxel> voxels = new LinkedHashMap<>();
        private boolean valid = true;

        private Planner(StructureSiteDescriptor site, SurfaceDecorator.BlockView world,
                int floor, int hx, int hz, int height) {
            this.site = site; this.world = world;
            this.floor = floor; this.hx = hx; this.hz = hz; this.height = height;
        }

        private boolean reserveFootprint() {
            for (int z = -hz; z <= hz; z++) for (int x = -hx; x <= hx; x++) {
                if (!solid(world.getBlock(wx(x, z), floor, wz(x, z)))) { valid = false; return false; }
                for (int y = 1; y <= height + 1; y++) {
                    if (!solid(world.getBlock(wx(x, z), floor + y, wz(x, z)))) {
                        valid = false; return false;
                    }
                }
            }
            return true;
        }

        private void excavate() {
            for (int z = -hz; z <= hz; z++) for (int x = -hx; x <= hx; x++) {
                for (int y = 1; y <= height + 1; y++) put(x, y, z, Blocks.AIR);
            }
        }

        private void vault() {
            for (int z = -hz; z <= hz; z++) for (int x = -hx; x <= hx; x++) {
                boolean edge = Math.abs(x) == hx || Math.abs(z) == hz;
                if (edge) for (int y = 1; y <= height; y++) {
                    boolean corner = Math.abs(x) == hx && Math.abs(z) == hz;
                    put(x, y, z, corner || y == height ? capMaterial(x, y, z) : wallMaterial(x, y, z));
                }
                if (Math.abs(x) == hx || Math.abs(z) == hz || yOnRim(x, z)) {
                    put(x, height + 1, z, capMaterial(x, height + 1, z));
                }
            }
            for (int z = -hz + 1; z < hz; z++) for (int x = -hx + 1; x < hx; x++) {
                if ((x + z & 1) == 0 && (unsigned(lane(x, height + 1, z), 9) == 0)) {
                    put(x, height + 1, z, Blocks.MOSSY_STONE_BRICK);
                }
            }
        }

        private boolean yOnRim(int x, int z) {
            return Math.abs(x) == hx - 1 || Math.abs(z) == hz - 1;
        }

        private void pillars() {
            int inset = 1 + unsigned(site.shapeLane() >>> 19, 2);
            for (int z : new int[] {-hz + inset, hz - inset}) for (int x : new int[] {-hx + inset, hx - inset}) {
                int top = height - unsigned(lane(x, 2, z), Math.max(1, height / 3));
                for (int y = 1; y <= top; y++) put(x, y, z, y == 1 || y == top ? capMaterial(x, y, z) : wallMaterial(x, y, z));
                if (unsigned(lane(x, height, z), 3) != 0) put(x, top + 1, z, Blocks.STONE_BRICK_SLAB);
            }
        }

        private void reliefs() {
            int motif = unsigned(site.topologyLane() >>> 13, 3);
            for (int side = 0; side < 4; side++) {
                int count = 1 + unsigned(site.partLane0(side, DETAIL), 1 + motif);
                for (int i = 0; i < count; i++) {
                    int lane = site.partLane1(side * 7 + i, DETAIL);
                    int y = 2 + unsigned(lane >>> 5, Math.max(1, height - 2));
                    int inset = 1 + unsigned(lane >>> 12, Math.max(1, (side < 2 ? hz : hx) - 1));
                    int x = side == 1 ? hx : side == 3 ? -hx : (lane & 1) == 0 ? -inset : inset;
                    int z = side == 0 ? -hz : side == 2 ? hz : (lane & 1) == 0 ? -inset : inset;
                    put(x, y, z, (lane & 2) == 0 ? Blocks.CHISELED_SANDSTONE : Blocks.STONE_BRICK);
                }
            }
        }

        private void trapMotif() {
            int arms = 2 + unsigned(site.topologyLane() >>> 21, 3);
            int radius = 1 + unsigned(site.shapeLane() >>> 25, Math.max(1, Math.min(hx, hz) - 1));
            for (int i = 0; i < arms; i++) {
                int lane = site.partLane0(i, PURPOSE);
                int x = (i % 2 == 0 ? radius : -radius);
                int z = (i < 2 ? 0 : ((lane & 1) == 0 ? radius : -radius));
                if ((lane & 4) != 0) { int t = x; x = z; z = t; }
                put(x, 1, z, Blocks.STONE_BRICK_SLAB);
                put(x, 1, z == 0 ? z + 1 : z, (lane & 8) == 0 ? Blocks.REDSTONE_ORE : Blocks.IRON_BLOCK);
            }
            put(0, 1, 0, Blocks.STONE_BRICK_SLAB);
        }

        private void altarAndTreasure() {
            int altar = (site.shapeLane() & 1) == 0 ? Blocks.STONE_BRICK : Blocks.CUT_SANDSTONE;
            put(0, 1, 0, altar);
            put(0, 2, 0, (site.topologyLane() & 2) == 0 ? Blocks.GOLD_BLOCK : Blocks.CHEST);
            if ((site.difficultyLane() & 3) != 0) put(0, 3, 0, Blocks.TORCH);
            int ring = 1 + unsigned(site.adaptationLane() >>> 22, 2);
            for (int i = 0; i < 4 + ring; i++) {
                int lane = site.partLane0(i, DETAIL + 1);
                int x = (i % 2 == 0 ? ring : -ring), z = (i < 2 ? 0 : (i % 4 == 2 ? ring : -ring));
                if ((lane & 1) != 0) { int t = x; x = z; z = t; }
                put(x, 1, z, (lane & 2) == 0 ? Blocks.STONE_BRICK_SLAB : Blocks.COBBLE);
            }
        }

        private void alcoves() {
            int count = 2 + unsigned(site.topologyLane() >>> 26, 5);
            for (int i = 0; i < count; i++) {
                int lane = site.partLane0(i, PURPOSE + 1);
                int side = (lane >>> 2) & 3;
                int along = 1 + unsigned(lane >>> 9, Math.max(1, (side < 2 ? hz : hx) - 1));
                int x = side == 1 ? hx - 1 : side == 3 ? -hx + 1 : ((lane & 1) == 0 ? -along : along);
                int z = side == 0 ? -hz + 1 : side == 2 ? hz - 1 : ((lane & 1) == 0 ? -along : along);
                put(x, 2, z, Blocks.IRON_BARS);
                if ((lane & 16) != 0) put(x, 3, z, Blocks.CHEST);
            }
        }

        private void torchesAndWear() {
            for (int side = 0; side < 4; side++) {
                int lane = site.partLane1(side, PURPOSE + 2);
                int x = side == 1 ? hx - 1 : side == 3 ? -hx + 1 : 0;
                int z = side == 0 ? -hz + 1 : side == 2 ? hz - 1 : 0;
                // Rotate the supporting wall with the chamber, using the engine's wall direction.
                if ((lane & 1) != 0) put(x, 2 + (lane & 2), z,
                        Blocks.WALL_TORCH_N + ((side + site.direction()) & 3));
                int cobwebs = unsigned(site.agingLane() >>> (side * 3), 3);
                if (cobwebs > 0) put(x, height - 1, z, Blocks.COBWEB);
            }
            for (int z = -hz + 1; z < hz; z++) for (int x = -hx + 1; x < hx; x++) {
                int lane = lane(x, 1, z);
                if (unsigned(lane, 17) == 0) put(x, 1, z, (lane & 2) == 0 ? Blocks.MOSS_BLOCK : Blocks.MOSS_CARPET);
            }
        }

        private int wallMaterial(int x, int y, int z) {
            int lane = lane(x, y, z);
            if (unsigned(lane >>> 2, 13) == 0) return Blocks.MOSSY_STONE_BRICK;
            if (unsigned(lane >>> 8, 11) == 0) return Blocks.MOSSY_COBBLE;
            return (lane & 1) == 0 ? Blocks.STONE_BRICK : Blocks.COBBLE;
        }

        private int capMaterial(int x, int y, int z) {
            int lane = lane(x, y, z);
            return (lane & 7) == 0 ? Blocks.MOSSY_STONE_BRICK
                    : ((lane & 3) == 0 ? Blocks.CUT_SANDSTONE : Blocks.STONE_BRICK);
        }

        private int lane(int x, int y, int z) {
            return site.voxelLane(wx(x, z), floor + y, wz(x, z), WEAR);
        }

        private int wx(int x, int z) {
            return site.anchorX() + rotateX(x, z, site.direction());
        }

        private int wz(int x, int z) {
            return site.anchorZ() + rotateZ(x, z, site.direction());
        }

        private int rotateX(int x, int z, int direction) {
            return switch (direction & 3) { case 1 -> -z; case 2 -> -x; case 3 -> z; default -> x; };
        }

        private int rotateZ(int x, int z, int direction) {
            return switch (direction & 3) { case 1 -> x; case 2 -> -z; case 3 -> -x; default -> z; };
        }

        private void put(int x, int y, int z, int block) {
            if (!valid || y < 1 || y > height + 1) return;
            int worldX = wx(x, z), worldY = floor + y, worldZ = wz(x, z);
            BlockPos pos = new BlockPos(worldX, worldY, worldZ);
            voxels.put(pos, RuinGenerator.Voxel.at(worldX, worldY, worldZ, block));
        }
    }
}
